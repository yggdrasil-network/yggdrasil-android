package eu.neilalexander.yggdrasil

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONArray

object PacketTunnelState: BroadcastReceiver() {
    var dhtState: JSONArray? = null
        private set

    var peersState: JSONArray? = null
        private set

    const val RECEIVER_INTENT = "eu.neilalexander.yggdrasil.PacketTunnelState.MESSAGE"

    fun peerCount(): Int {
        if (peersState == null) {
            return 0
        }
        return peersState!!.length()
    }

    fun dhtCount(): Int {
        if (dhtState == null) {
            return 0
        }
        return dhtState!!.length()
    }

    override fun onReceive(context: Context?, intent: Intent) {
        when (intent.getStringExtra("type")) {
            "state" -> {
                var dht = intent.getStringExtra("dht")
                var peers = intent.getStringExtra("peers")

                if (dht == null || dht == "null") {
                    dht = "[]"
                }
                if (peers == null || peers == "null") {
                    peers = "[]"
                }

                peersState = JSONArray(peers)
                dhtState = JSONArray(dht)

                intent.action = RECEIVER_INTENT
                LocalBroadcastManager.getInstance(context!!).sendBroadcast(intent)
            }
        }
    }
}