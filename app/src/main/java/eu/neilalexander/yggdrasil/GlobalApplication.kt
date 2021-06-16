package eu.neilalexander.yggdrasil

import android.app.Application
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class GlobalApplication: Application() {
    private var state = PacketTunnelState
    private lateinit var config: ConfigurationProxy

    override fun onCreate() {
        super.onCreate()
        config = ConfigurationProxy(applicationContext)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            state, IntentFilter(PacketTunnelProvider.RECEIVER_INTENT)
        )
    }

    override fun onTerminate() {
        super.onTerminate()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(state)
    }
}