package io.fastpix.agnoplayerdatasdk

import android.app.Application
import com.castlabs.android.PlayerSDK

class CastLabApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PlayerSDK.init(applicationContext)
    }
}
