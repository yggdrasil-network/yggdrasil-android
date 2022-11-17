package eu.neilalexander.yggdrasil

import android.app.Application
import android.content.ComponentName
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

class GlobalApplication: Application(), YggStateReceiver.StateReceiver {
    private lateinit var config: ConfigurationProxy
    private var currentState: State = State.Disabled
    var updaterConnections: Int = 0

    override fun onCreate() {
        super.onCreate()
        config = ConfigurationProxy(applicationContext)
        val callback = NetworkStateCallback(this)
        val receiver = YggStateReceiver(this)
        receiver.register(this)
    }

    fun subscribe() {
        updaterConnections++
    }

    fun unsubscribe() {
        if (updaterConnections > 0) {
            updaterConnections--
        }
    }

    fun needUiUpdates(): Boolean {
        return updaterConnections > 0
    }

    fun getCurrentState(): State {
        return currentState
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onStateChange(state: State) {
        if (state != currentState) {
            val componentName = ComponentName(this, YggTileService::class.java)
            TileService.requestListeningState(this, componentName)
            currentState = state
        }
    }
}