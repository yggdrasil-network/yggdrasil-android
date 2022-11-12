package eu.neilalexander.yggdrasil

import android.content.Context
import android.content.Intent
import android.net.*
import android.util.Log


private const val TAG = "Network"

class NetworkStateCallback(val context: Context) : ConnectivityManager.NetworkCallback() {

    init {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.registerNetworkCallback(request, this)
    }

    override fun onAvailable(network: Network) {
        super.onAvailable(network)
        Log.d(TAG, "onAvailable")

        Thread {
            // The message often arrives before the connection is fully established
            Thread.sleep(1000)
            val intent = Intent(context, PacketTunnelProvider::class.java)
            intent.action = PacketTunnelProvider.ACTION_CONNECT
            context.startService(intent)
        }.start()
    }

    override fun onLost(network: Network) {
        super.onLost(network)
        Log.d(TAG, "onLost")
    }
}