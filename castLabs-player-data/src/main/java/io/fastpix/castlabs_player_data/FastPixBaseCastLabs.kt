package io.fastpix.castlabs_player_data

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.View
import com.castlabs.android.player.DisplayInfo
import com.castlabs.android.player.PlayerController
import com.castlabs.android.player.exceptions.CastlabsPlayerException
import com.castlabs.android.player.models.VideoTrackQuality
import com.google.android.exoplayer2.ExoPlayer
import io.fastpix.castlabs_player_data.src.info.CastLabsLibraryInfo
import io.fastpix.castlabs_player_data.src.model.CustomerData
import io.fastpix.castlabs_player_data.src.model.PlayerEvents
import io.fastpix.castlabs_player_data.src.utils.Utils
import io.fastpix.castlabs_player_data.src.utils.Utils.validTransitions
import io.fastpix.data.FastPixDataSDK
import io.fastpix.data.domain.SDKConfiguration
import io.fastpix.data.domain.enums.PlayerEventType
import io.fastpix.data.domain.listeners.PlayerListener
import io.fastpix.data.domain.model.BandwidthModel
import io.fastpix.data.domain.model.ErrorModel
import kotlin.math.ceil

class FastPixBaseCastLabs(
    private val context: Context,
    private val playerView: View,
    private val playerController: PlayerController,
    private val enableLogging: Boolean = false,
    private val customerData: CustomerData,
) : PlayerListener, com.castlabs.android.player.PlayerListener {

    private val TAG = "FastPixBaseCastLabs"
    private lateinit var fastPixDataSDK: FastPixDataSDK
    private var videoSourceWidth: Int? = null
    private var videoSourceHeight: Int? = null
    private var errorCode: String? = null
    private var errorMessage: String? = null
    private var isSeeking = false

    // State machine for valid event transitions
    private var currentEventState: PlayerEvents? = null

    init {
        initializeFastPixSDK()
        playerController.addPlayerListener(this)
        // setUpListener()
    }

    private var lastStablePositionUs: Long = -1
    private var seekStartPositionUs: Long = -1

    private fun setUpListener() {
        playerController.addPlayerListener(object : com.castlabs.android.player.PlayerListener {
            override fun onFatalErrorOccurred(error: CastlabsPlayerException) {
                errorMessage = error.cause.toString()
                errorCode = error.type.toString()
                dispatchErrorEvent()
            }

            override fun onError(error: CastlabsPlayerException) {}

            override fun onStateChanged(state: PlayerController.State) {
                when (state) {
                    PlayerController.State.Playing -> dispatchPlayingEvent()
                    PlayerController.State.Idle -> dispatchPauseEvent()
                    PlayerController.State.Preparing -> {
                        dispatchViewBegin()
                        dispatchPlayerReadyEvent()
                        dispatchPlayEvent()
                    }

                    PlayerController.State.Buffering -> dispatchBufferingEvent()
                    PlayerController.State.Pausing -> dispatchPauseEvent()
                    PlayerController.State.Finished -> dispatchEndedEvent()
                }
            }

            override fun onSeekTo(p0: Long) {
                dispatchSeekingEvent()
            }

            override fun onSeekCompleted() {
                if (!isSeeking) {
                    isSeeking = true
                    dispatchPauseEvent()
                    dispatchSeekingEvent()
                }
            }

            override fun onVideoSizeChanged(width: Int, height: Int, p2: Float) {
                videoSourceWidth = width
                videoSourceHeight = height
                dispatchVariantChangeEvent()
            }

            override fun onSeekRangeChanged(p0: Long, p1: Long) {
                Log.e(TAG, "onSeekRangeChanged: ")
            }

            override fun onPlaybackPositionChanged(position: Long) {
                if (!isSeeking) {
                    lastStablePositionUs = position
                    return
                }

                isSeeking = false
                dispatchSeekedEvent(position)
            }

            override fun onDisplayChanged(
                p0: DisplayInfo?,
                p1: Boolean,
            ) {
            }

            override fun onDurationChanged(p0: Long) {
            }

            override fun onSpeedChanged(p0: Float) {
            }

            override fun onPlayerModelChanged() {
            }

            override fun onVideoKeyStatusChanged(p0: List<VideoTrackQuality?>) {
            }

            override fun onFullyBuffered() {
                dispatchBufferedEvent()
            }
        })
    }

    private fun dispatchViewBegin() {
        if (enableLogging) {
            Log.d(TAG, "Dispatching ViewBegin event")
        }
        fastPixDataSDK.dispatchEvent(PlayerEventType.viewBegin)
    }

    private fun dispatchPlayerReadyEvent() {
        if (enableLogging) {
            Log.d(TAG, "Dispatching Play Ready event")
        }
        fastPixDataSDK.dispatchEvent(PlayerEventType.playerReady)
    }

    private fun dispatchPlayEvent() {
        if (transitionToEvent(PlayerEvents.PLAY)) {
            if (enableLogging) {
                Log.d(TAG, "Dispatching Play event")
            }
            fastPixDataSDK.dispatchEvent(PlayerEventType.play)
        }
    }

    private fun dispatchPlayingEvent() {
        if (transitionToEvent(PlayerEvents.PLAYING)) {
            if (enableLogging) {
                Log.d(TAG, "Dispatching Playing event")
            }
            fastPixDataSDK.dispatchEvent(PlayerEventType.playing)
        }
    }

    private fun dispatchPauseEvent(currentPosition: Int? = null) {
        if (transitionToEvent(PlayerEvents.PAUSE)) {
            if (enableLogging) {
                Log.d(TAG, "Dispatching Pause event")
            }
            // Note: There's no Pause event type in PlayerEventType enum
            // For now, we'll just track the state transition without dispatching
            // You may need to add a Pause event type to PlayerEventType enum
            if (currentPosition != null) {
                fastPixDataSDK.dispatchEvent(PlayerEventType.pause, currentPosition)
            } else {
                fastPixDataSDK.dispatchEvent(PlayerEventType.pause)
            }
        }
    }

    private fun dispatchSeekingEvent(currentPosition: Int? = null) {
        if (transitionToEvent(PlayerEvents.SEEKING)) {
            if (enableLogging) {
                Log.d(TAG, "Dispatching Seeking event")
            }
            // Temporarily set currentPosition to seeking start position for the seeking event
            fastPixDataSDK.dispatchEvent(PlayerEventType.seeking, currentPosition)
        }
    }

    private fun dispatchSeekedEvent(position: Long) {
        if (transitionToEvent(PlayerEvents.SEEKED)) {
            if (enableLogging) {
                Log.d(TAG, "Dispatching Seeked event")
            }
            fastPixDataSDK.dispatchEvent(PlayerEventType.seeked, position.toInt())
        }
    }

    private fun dispatchBufferingEvent() {
        if (transitionToEvent(PlayerEvents.BUFFERING)) {
            if (enableLogging) {
                Log.d(TAG, "Dispatching Buffering event")
            }
            fastPixDataSDK.dispatchEvent(PlayerEventType.buffering)
        }
    }

    private fun dispatchBufferedEvent() {
        if (transitionToEvent(PlayerEvents.BUFFERED)) {
            if (enableLogging) {
                Log.d(TAG, "Dispatching Buffered event")
            }
            fastPixDataSDK.dispatchEvent(PlayerEventType.buffered)
        }
    }

    private fun dispatchEndedEvent(currentPosition: Int? = null) {
        if (transitionToEvent(PlayerEvents.ENDED)) {
            if (enableLogging) {
                Log.d(TAG, "Dispatching Ended event")
            }
            if (currentPosition != null) {
                fastPixDataSDK.dispatchEvent(PlayerEventType.ended, currentPosition)
            } else {
                fastPixDataSDK.dispatchEvent(PlayerEventType.ended)
            }
        }
    }

    private fun dispatchVariantChangeEvent() {
        if (transitionToEvent(PlayerEvents.VARIANT_CHANGED)) {
            if (enableLogging) {
                Log.d(TAG, "Dispatching VariantChange event")
            }
            fastPixDataSDK.dispatchEvent(PlayerEventType.variantChanged)
        }
    }

    private fun dispatchErrorEvent() {
        if (transitionToEvent(PlayerEvents.ERROR)) {
            if (enableLogging) {
                Log.d(TAG, "Dispatching Error event: $errorMessage")
            }
            fastPixDataSDK.dispatchEvent(PlayerEventType.error)
        }
    }

    /**
     * Validates if the transition from current state to new state is valid
     */
    private fun isValidTransition(newEvent: PlayerEvents): Boolean {
        val allowedTransitions = validTransitions[currentEventState] ?: emptySet()
        return newEvent in allowedTransitions
    }

    /**
     * Safely transitions to a new event state if valid
     */
    private fun transitionToEvent(newEvent: PlayerEvents): Boolean {
        if (isValidTransition(newEvent)) {
            if (newEvent != PlayerEvents.VARIANT_CHANGED) {
                currentEventState = newEvent
            }
            return true
        } else {
            return false
        }
    }

    private fun initializeFastPixSDK() {
        fastPixDataSDK = FastPixDataSDK()
        val sdkConfiguration = SDKConfiguration(
            playerData = customerData.playerDetails,
            workspaceId = customerData.workspaceId,
            beaconUrl = customerData.beaconUrl,
            videoData = customerData.videoDetails,
            playerListener = this,
            enableLogging = enableLogging,
            customData = customerData.customDataDetails,
        )
        fastPixDataSDK.initialize(sdkConfiguration, context)
    }

    override fun playerHeight(): Int? {
        val density = context.resources.displayMetrics.density
        val rawHeight = playerView.height
        val height = ceil(rawHeight / density)
        return height.toInt()
    }

    override fun playerWidth(): Int? {
        val density = context.resources.displayMetrics.density
        val rawWidth = playerView.width
        val width = ceil(rawWidth / density)
        return width.toInt()
    }

    private fun getPlayer(): ExoPlayer? {
        return playerController.player
    }

    override fun videoSourceWidth(): Int? {
        return videoSourceWidth
    }

    override fun videoSourceHeight(): Int? {
        return videoSourceHeight
    }

    override fun playHeadTime(): Int? {
        return getPlayer()?.currentPosition?.toInt() ?: 0
    }

    override fun mimeType(): String? {
        return Utils.getMimeTypeFromUrl(playerController.playerConfig?.contentUrl)
    }

    override fun sourceFps(): String? {
        return null
    }

    override fun sourceAdvertisedBitrate(): String? {
        return null
    }

    override fun sourceAdvertiseFrameRate(): String? {
        return null
    }

    override fun sourceDuration(): Int? {
        return getPlayer()?.duration?.toInt()
    }

    override fun isPause(): Boolean? {
        return getPlayer()?.isPlaying == false
    }

    override fun isAutoPlay(): Boolean? {
        return playerController.isPlayWhenReady
    }

    override fun preLoad(): Boolean? {
        return false
    }

    override fun isBuffering(): Boolean? {
        return currentEventState == PlayerEvents.BUFFERING
    }

    override fun playerCodec(): String? {
        return null
    }

    override fun sourceHostName(): String? {
        return null
    }

    override fun isLive(): Boolean? {
        return playerController.isLive
    }

    override fun sourceUrl(): String? {
        return playerController.playerConfig?.contentUrl
    }

    override fun isFullScreen(): Boolean? {
        val orientation = context.resources.configuration.orientation
        return orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    override fun getBandWidthData(): BandwidthModel {
        return BandwidthModel()
    }

    override fun getPlayerError(): ErrorModel {
        return ErrorModel(errorCode, errorMessage)
    }

    override fun getVideoCodec(): String? {
        return null
    }

    override fun getSoftwareName(): String? {
        return CastLabsLibraryInfo.SDK_NAME
    }

    override fun getSoftwareVersion(): String? {
        return CastLabsLibraryInfo.SDK_VERSION
    }

    fun release() {
        fastPixDataSDK.release()
        errorMessage = null
        errorCode = null
        playerController.removePlayerListener(this)
        playerController.release()
        playerController.player?.release()
        videoSourceWidth = null
        videoSourceHeight = null
        currentEventState = null
    }

    override fun onFatalErrorOccurred(error: CastlabsPlayerException) {
        errorMessage = error.cause.toString()
        errorCode = error.type.toString()
        dispatchErrorEvent()
    }

    override fun onError(p0: CastlabsPlayerException) {}

    override fun onStateChanged(state: PlayerController.State) {
        when (state) {
            PlayerController.State.Playing -> dispatchPlayingEvent()
            PlayerController.State.Idle -> dispatchPauseEvent()
            PlayerController.State.Preparing -> {
                dispatchViewBegin()
                dispatchPlayerReadyEvent()
                dispatchPlayEvent()
            }

            PlayerController.State.Buffering -> dispatchBufferingEvent()
            PlayerController.State.Pausing -> dispatchPauseEvent()
            PlayerController.State.Finished -> dispatchEndedEvent()
        }
    }

    override fun onSeekTo(p0: Long) {
        dispatchSeekingEvent()
    }

    override fun onSeekCompleted() {
        if (!isSeeking) {
            isSeeking = true
            dispatchPauseEvent()
            dispatchSeekingEvent()
        }
    }

    override fun onVideoSizeChanged(width: Int, height: Int, p2: Float) {
        videoSourceWidth = width
        videoSourceHeight = height
        dispatchVariantChangeEvent()
    }

    override fun onSeekRangeChanged(p0: Long, p1: Long) {}

    override fun onPlaybackPositionChanged(position: Long) {
        if (!isSeeking) {
            lastStablePositionUs = position
            return
        }

        isSeeking = false
        dispatchSeekedEvent(position)
    }

    override fun onDisplayChanged(
        p0: DisplayInfo?,
        p1: Boolean,
    ) {
    }

    override fun onDurationChanged(p0: Long) {
    }

    override fun onSpeedChanged(p0: Float) {
    }

    override fun onPlayerModelChanged() {
    }

    override fun onVideoKeyStatusChanged(p0: List<VideoTrackQuality?>) {
    }

    override fun onFullyBuffered() {
        dispatchBufferedEvent()
    }
}
