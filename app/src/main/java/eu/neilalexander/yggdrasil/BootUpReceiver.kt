package eu.neilalexander.yggdrasil

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.preference.PreferenceManager

class BootUpReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BootUpReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.w(TAG, "Wrong action: ${intent.action}")
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!preferences.getBoolean(PREF_KEY_ENABLED, false)) {
            Log.i(TAG, "Yggdrasil disabled, not starting service")
            return
        }
        Log.i(TAG, "Yggdrasil enabled, starting service")
        val serviceIntent = Intent(context, PacketTunnelProvider::class.java)
        serviceIntent.action = PacketTunnelProvider.ACTION_START

        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            Log.i(TAG, "Need to ask for VPN permission")
            val notification = createPermissionMissingNotification(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(444, notification)
        } else {
            context.startService(serviceIntent)
        }
    }
}