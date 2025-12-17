package io.fastpix.bitmovin_data

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import io.fastpix.agnoplayerdatasdk.MainActivity
import io.fastpix.agnoplayerdatasdk.VideoAdapter
import io.fastpix.agnoplayerdatasdk.databinding.ActivityVideoListScreenBinding
import io.fastpix.agnoplayerdatasdk.dummyData

class VideoListScreen : AppCompatActivity() {
    private lateinit var binding: ActivityVideoListScreenBinding
    private val videoAdapter by lazy {
        VideoAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoListScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        videoAdapter.passDataToAdapter(dummyData)
        binding.recyclerView.adapter = videoAdapter

        videoAdapter.onVideoClick = { video ->
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("video_model", video)
            startActivity(intent)
        }
    }
}
