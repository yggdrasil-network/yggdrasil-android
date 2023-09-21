package eu.neilalexander.yggdrasil

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

const val PREF_KEY_ENABLED = "enabled"
const val BLE_ENABLED = "ble"
const val CODED_PHY_ENABLED = "codedPhy"
const val MAIN_CHANNEL_ID = "Yggdrasil Service"

class GlobalApplication: Application(), YggStateReceiver.StateReceiver {
    private lateinit var config: ConfigurationProxy
    private var currentState: State = State.Disabled
    var updaterConnections: Int = 0

    override fun onCreate() {
        super.onCreate()
        config = ConfigurationProxy(applicationContext)
        val callback = NetworkStateCallback(this)
        callback.register()
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

            if (state != State.Disabled) {
                val notification = createServiceNotification(this, state)
                val notificationManager: NotificationManager =
                    this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
            }

            currentState = state
        }
    }
}

fun createServiceNotification(context: Context, state: State): Notification {
    createNotificationChannels(context)

    val intent = Intent(context, MainActivity::class.java).apply {
        this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    val text = when (state) {
        State.Disabled -> context.getText(R.string.tile_disabled)
        State.Enabled -> context.getText(R.string.tile_enabled)
        State.Connected -> context.getText(R.string.tile_connected)
        else -> context.getText(R.string.tile_disabled)
    }

    return NotificationCompat.Builder(context, MAIN_CHANNEL_ID)
        .setShowWhen(false)
        .setContentTitle(text)
        .setSmallIcon(R.drawable.ic_tile_icon)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()
}

fun createPermissionMissingNotification(context: Context): Notification {
    createNotificationChannels(context)
    val intent = Intent(context, MainActivity::class.java).apply {
        this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

    return NotificationCompat.Builder(context, MAIN_CHANNEL_ID)
        .setShowWhen(false)
        .setContentTitle(context.getText(R.string.app_name))
        .setContentText(context.getText(R.string.permission_notification_text))
        .setSmallIcon(R.drawable.ic_tile_icon)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
}

private fun createNotificationChannels(context: Context) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = context.getString(R.string.channel_name)
        val descriptionText = context.getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_MIN
        val channel = NotificationChannel(MAIN_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
