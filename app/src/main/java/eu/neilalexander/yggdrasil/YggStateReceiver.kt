package eu.neilalexander.yggdrasil

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

const val STATE_ENABLED = "enabled"
const val STATE_DISABLED = "disabled"
const val STATE_CONNECTED = "connected"
const val STATE_RECONNECTING = "reconnecting"

class YggStateReceiver(var receiver: StateReceiver): BroadcastReceiver() {

    companion object {
        const val YGG_STATE_INTENT = "eu.neilalexander.yggdrasil.YggStateReceiver.STATE"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        val state = when (intent?.getStringExtra("state")) {
            STATE_ENABLED -> State.Enabled
            STATE_DISABLED -> State.Disabled
            STATE_CONNECTED -> State.Connected
            STATE_RECONNECTING -> State.Reconnecting
            else -> State.Unknown
        }
        receiver.onStateChange(state)
    }

    fun register(context: Context) {
        LocalBroadcastManager.getInstance(context).registerReceiver(
            this, IntentFilter(YGG_STATE_INTENT)
        )
    }

    fun unregister(context: Context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(this)
    }

    interface StateReceiver {
        fun onStateChange(state: State)
    }
}

/**
 * A class-supporter with an Yggdrasil state
 */
enum class State {
    Unknown, Disabled, Enabled, Connected, Reconnecting;
}