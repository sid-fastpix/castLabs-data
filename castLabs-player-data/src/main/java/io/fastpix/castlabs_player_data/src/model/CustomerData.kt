package io.fastpix.castlabs_player_data.src.model

import com.castlabs.sdk.BuildConfig
import io.fastpix.data.domain.model.CustomDataDetails
import io.fastpix.data.domain.model.PlayerDataDetails
import io.fastpix.data.domain.model.VideoDataDetails

data class CustomerData(
    var beaconUrl: String? = null,
    var workspaceId: String,
    var videoDetails: VideoDataDetails? = null,
    var playerDetails: PlayerDataDetails = PlayerDataDetails(
        BuildConfig.LIBRARY_PACKAGE_NAME,
        BuildConfig.VERSION_NAME
    ),
    var customDataDetails: CustomDataDetails? = null
)