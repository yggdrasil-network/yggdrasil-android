package eu.neilalexander.yggdrasil

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi

private const val TAG = "TileService"

@RequiresApi(Build.VERSION_CODES.N)
class YggTileService: TileService(), YggStateReceiver.StateReceiver {

    private lateinit var receiver: YggStateReceiver

    override fun onCreate() {
        super.onCreate()
        receiver = YggStateReceiver(this)
    }

    /**
     * We need to override the method onBind to avoid crashes that were detected on Android 8
     *
     * The possible reason of crashes is described here:
     * https://github.com/aosp-mirror/platform_frameworks_base/commit/ee68fd889c2dfcd895b8e73fc39d7b97826dc3d8
     */
    override fun onBind(intent: Intent?): IBinder? {
        return try {
            super.onBind(intent)
        } catch (th: Throwable) {
            null
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState((application as GlobalApplication).getCurrentState())
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        updateTileState((application as GlobalApplication).getCurrentState())
    }

    override fun onStartListening() {
        super.onStartListening()
        receiver.register(this)
        updateTileState((application as GlobalApplication).getCurrentState())
    }

    override fun onStopListening() {
        super.onStopListening()
        receiver.unregister(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        receiver.unregister(this)
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, PacketTunnelProvider::class.java)
        intent.action = PacketTunnelProvider.ACTION_TOGGLE
        startService(intent)
    }

    private fun updateTileState(state: State) {
        val tile = qsTile ?: return
        val oldState = tile.state
        tile.state = when (state) {
            State.Unknown -> Tile.STATE_UNAVAILABLE
            State.Disabled -> Tile.STATE_INACTIVE
            State.Enabled -> Tile.STATE_ACTIVE
            State.Connected -> Tile.STATE_ACTIVE
            State.Reconnecting -> Tile.STATE_ACTIVE
        }
        var changed = oldState != tile.state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val oldText = tile.subtitle
            tile.subtitle = when (state) {
                State.Enabled -> getText(R.string.tile_enabled)
                State.Connected -> getText(R.string.tile_connected)
                else -> getText(R.string.tile_disabled)
            }
            changed = changed || (oldText != tile.subtitle)
        }

        // Update tile if changed state
        if (changed) {
            Log.i(TAG, "Updating tile, old state: $oldState, new state: ${tile.state}")
            /*
              Force set the icon in the tile, because there is a problem on icon tint in the Android Oreo.
              Issue: https://github.com/AdguardTeam/AdguardForAndroid/issues/1996
             */
            tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_tile_icon)
            tile.updateTile()
        }
    }

    override fun onStateChange(state: State) {
        updateTileState(state)
    }
}