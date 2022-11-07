package eu.neilalexander.yggdrasil

import android.app.Application

class GlobalApplication: Application() {
    private lateinit var config: ConfigurationProxy
    var updaterConnections: Int = 0

    override fun onCreate() {
        super.onCreate()
        config = ConfigurationProxy(applicationContext)
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
}