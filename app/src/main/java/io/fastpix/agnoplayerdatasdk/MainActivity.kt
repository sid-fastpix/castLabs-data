package io.fastpix.agnoplayerdatasdk

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.castlabs.android.player.DisplayInfo
import com.castlabs.android.player.PlayerConfig
import com.castlabs.android.player.PlayerController
import com.castlabs.android.player.PlayerListener
import com.castlabs.android.player.exceptions.CastlabsPlayerException
import com.castlabs.android.player.models.VideoTrackQuality
import io.fastpix.agnoplayerdatasdk.databinding.ActivityMainBinding
import io.fastpix.castlabs_player_data.FastPixBaseCastLabs
import io.fastpix.castlabs_player_data.src.model.CustomerData
import io.fastpix.data.domain.model.PlayerDataDetails
import io.fastpix.data.domain.model.VideoDataDetails
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var playerController: PlayerController
    private var isFullscreen = false
    private var isUserSeeking = false
    private lateinit var fastPixDataSDK: FastPixBaseCastLabs
    private var seekBarUpdateRunnable: Runnable? = null
    private val seekBarUpdateHandler = Handler(Looper.getMainLooper())
    private var videoModel: DummyData? = null
    private var currentVideoIndex: Int = -1
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("video_model", DummyData::class.java)
        } else {
            intent.getParcelableExtra("video_model")
        }
        // Find current video index in the list
        currentVideoIndex =
            dummyData.indexOfFirst { it.id == videoModel?.id && it.url == videoModel?.url }
        if (currentVideoIndex == -1 && videoModel != null) {
            // Fallback: try to find by id only
            currentVideoIndex = dummyData.indexOfFirst { it.id == videoModel?.id }
        }

        initializePlayer()
        initializeFastPixExo()
        setupControls()
    }

    private fun startSeekBarUpdates() {
        stopSeekBarUpdates()
        seekBarUpdateRunnable = object : Runnable {
            override fun run() {
                updateSeekBar()
                seekBarUpdateHandler.postDelayed(this, 100) // Update every 100ms
            }
        }
        seekBarUpdateRunnable?.let { seekBarUpdateHandler.post(it) }
    }

    private fun stopSeekBarUpdates() {
        seekBarUpdateRunnable?.let { seekBarUpdateHandler.removeCallbacks(it) }
        seekBarUpdateRunnable = null
    }

    private fun initializeFastPixExo() {
        val videoDataDetails = VideoDataDetails(
            videoId = UUID.randomUUID().toString(),
            videoTitle = videoModel?.id.orEmpty(),
            videoCDN = "cloudflare"
        )
        val customerData = CustomerData(
            workspaceId = "1109888358169935873",
            videoDetails = videoDataDetails,
        )
        fastPixDataSDK = FastPixBaseCastLabs(
            this,
            playerView = binding.castLabsPlayerView,
            playerController = binding.castLabsPlayerView.playerController,
            enableLogging = true,
            customerData = customerData
        )
    }

    private fun initializePlayer() {
        playerController = binding.castLabsPlayerView.playerController

        val playerConfig =
            PlayerConfig.Builder(videoModel!!.url)
                .userID(UUID.randomUUID().toString())
                .get()

        playerController.open(playerConfig)
        // Set up player event listeners
        setupPlayerListeners()
    }

    private fun setupPlayerListeners() {
        playerController.addPlayerListener(object : PlayerListener {
            override fun onFatalErrorOccurred(p0: CastlabsPlayerException) {
            }

            override fun onError(p0: CastlabsPlayerException) {
            }

            override fun onStateChanged(p0: PlayerController.State) {
                when (p0) {
                    PlayerController.State.Playing -> updatePlayPauseButton(true)
                    PlayerController.State.Idle -> updatePlayPauseButton(false)
                    PlayerController.State.Preparing -> {
                        startSeekBarUpdates()
                    }

                    PlayerController.State.Buffering -> binding.loadingIndicator.isVisible = false
                    PlayerController.State.Pausing -> {}
                    PlayerController.State.Finished -> {}
                }
            }

            override fun onSeekTo(p0: Long) {
                binding.loadingIndicator.isVisible = true
            }

            override fun onSeekCompleted() {
                binding.loadingIndicator.isVisible = false
                updateSeekBar()
            }

            override fun onVideoSizeChanged(p0: Int, p1: Int, p2: Float) {

            }

            override fun onSeekRangeChanged(p0: Long, p1: Long) {

            }

            override fun onPlaybackPositionChanged(p0: Long) {

            }

            override fun onDisplayChanged(
                p0: DisplayInfo?,
                p1: Boolean
            ) {

            }

            override fun onDurationChanged(p0: Long) {
                updateDuration()
                val duration = playerController.duration / 1000
                binding.durationText.text = formatTime(duration)
            }

            override fun onSpeedChanged(p0: Float) {

            }

            override fun onPlayerModelChanged() {

            }

            override fun onVideoKeyStatusChanged(p0: List<VideoTrackQuality?>) {

            }

            override fun onFullyBuffered() {
                binding.loadingIndicator.isVisible = false
            }

        })
    }

    private fun setupControls() {
        // Make ImageViews clickable and focusable for better UX
        binding.forwardSeek.isClickable = true
        binding.forwardSeek.isFocusable = true
        binding.backwardSeek.isClickable = true
        binding.backwardSeek.isFocusable = true
        binding.previousEpisode.isClickable = true
        binding.previousEpisode.isFocusable = true
        binding.nextEpisode.isClickable = true
        binding.nextEpisode.isFocusable = true

        // Play/Pause Button
        binding.playPauseButton.setOnClickListener {
            togglePlayPause()
        }

        // Seek Bar
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && seekBar != null) {
                    val durationMs = playerController.duration / 1000.0
                    val seekMs = ((progress / 1000.0) * durationMs).toLong()
                    binding.currentTimeText.text = formatTime(seekMs)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: return
                val durationMs = playerController.duration / 1000.0

                val seekMs = (((progress / 1000.0) * durationMs).toLong()) * 1000
                playerController.position = seekMs
            }
        })

        // Forward Seek (10 seconds) - matches XML id: forward_seek
        binding.forwardSeek.setOnClickListener {
            val newPosition = playerController.position + 10000000
            playerController.position = newPosition
        }

        // Backward Seek (10 seconds) - matches XML id: backward_seek
        binding.backwardSeek.setOnClickListener {
            val newPosition = playerController.position - 10000000
            playerController.position = newPosition
        }

        // Previous Episode - matches XML id: previous_episode
        binding.previousEpisode.setOnClickListener {
            navigateToPreviousEpisode()
        }

        // Next Episode - matches XML id: next_episode
        binding.nextEpisode.setOnClickListener {
            navigateToNextEpisode()
        }

        // Fullscreen Button - matches XML id: fullscreenButton
        binding.fullscreenButton.setOnClickListener {
            toggleFullscreen()
        }

        // Hide/show controls when tapping on player view - matches XML id: bitmovin_player_view
        binding.castLabsPlayerView.setOnClickListener {
            toggleControlsVisibility()
        }
    }

    private fun navigateToPreviousEpisode() {
        if (currentVideoIndex > 0) {
            switchToEpisode(currentVideoIndex - 1)
        }
    }

    private fun navigateToNextEpisode() {
        if (currentVideoIndex >= 0 && currentVideoIndex < dummyData.size - 1) {
            switchToEpisode(currentVideoIndex + 1)
        }
    }

    private fun switchToEpisode(newIndex: Int) {
        if (newIndex < 0 || newIndex >= dummyData.size) {
            Log.e(TAG, "Invalid episode index: $newIndex")
            return
        }

        // Release existing players
        try {
            binding.castLabsPlayerView.playerController.release()
            binding.castLabsPlayerView.playerController.player?.release()
            // Release FastPix
            fastPixDataSDK.release()
            videoModel = dummyData[newIndex]
            currentVideoIndex = newIndex
            initializePlayer()
            // Reset UI
            resetPlayerUI()

            // Reinitialize players with new video
            initializeFastPixExo()
            updateEpisodeButtons()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing players: ${e.message}")
        }
    }

    private fun updateEpisodeButtons() {
        // Enable/disable buttons based on current position
        binding.nextEpisode.isEnabled =
            currentVideoIndex >= 0 && currentVideoIndex < dummyData.size - 1
        binding.previousEpisode.isEnabled = currentVideoIndex > 0

        // Optionally change alpha to show disabled state
        binding.nextEpisode.alpha = if (binding.nextEpisode.isEnabled) 1.0f else 0.5f
        binding.previousEpisode.alpha = if (binding.previousEpisode.isEnabled) 1.0f else 0.5f
    }

    private fun resetPlayerUI() {
        binding.seekBar.progress = 0
        binding.seekBar.max = 0
        binding.seekBar.secondaryProgress = 0
        binding.currentTimeText.text = formatTime(0)
        binding.durationText.text = formatTime(0)
        showLoader()
    }

    private fun showLoader() {
        binding.loadingIndicator.visibility = View.VISIBLE
    }

    private fun hideLoader() {
        binding.loadingIndicator.visibility = View.GONE
    }

    private fun togglePlayPause() {
        if (playerController.isPlaying) {
            playerController.pause()
            updatePlayPauseButton(false)
        } else {
            playerController.play()
            updatePlayPauseButton(true)
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        if (isPlaying) {
            binding.playPauseButton.setImageResource(R.drawable.ic_pause)
        } else {
            binding.playPauseButton.setImageResource(R.drawable.ic_play_arrow)
        }
    }

    private fun updateSeekBar() {
        val currentMs = playerController.player?.currentPosition ?: return
        val duration = playerController.duration

        // currentPosition is in milliseconds, formatTime expects milliseconds
        binding.currentTimeText.text = formatTime(currentMs)

        if (duration > 0 && binding.seekBar.max > 0) {
            val durationMs = duration / 1000.0   // micro → milli

            // -------- Primary progress --------
            val playedFraction = currentMs / durationMs
            binding.seekBar.progress = (playedFraction * 1000).toInt()

            // -------- Secondary (buffered) progress --------
            val bufferedUs = playerController.bufferedPosition
            if (bufferedUs > 0) {
                val bufferedMs = bufferedUs / 1000.0
                val bufferedFraction = bufferedMs / durationMs

                binding.seekBar.secondaryProgress =
                    (bufferedFraction * 1000)
                        .toInt()
                        .coerceAtMost(binding.seekBar.max)
            }
        }
    }

    private fun updateDuration() {
        val durationUs = playerController.duration
        binding.seekBar.max = 1_000
        binding.durationText.text = formatTime(durationUs / 1_000) // ms
    }

    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1_000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen

        if (isFullscreen) {
            // Enter fullscreen
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            supportActionBar?.hide()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

            // Update fullscreen button icon
            binding.fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit)
        } else {
            // Exit fullscreen
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            supportActionBar?.show()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

            // Update fullscreen button icon
            binding.fullscreenButton.setImageResource(R.drawable.ic_fullscreen)
        }
    }

    private fun toggleControlsVisibility() {
        if (binding.controlsContainer.visibility == View.VISIBLE) {
            binding.controlsContainer.visibility = View.GONE
        } else {
            binding.controlsContainer.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (playerController.isPlaying) {
            playerController.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSeekBarUpdates()
        fastPixDataSDK.release()
    }
}