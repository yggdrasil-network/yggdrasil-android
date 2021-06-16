package eu.neilalexander.yggdrasil

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.widget.Switch
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import mobile.Mobile
import org.json.JSONArray


class MainActivity : AppCompatActivity() {
    private var state = PacketTunnelState

    private lateinit var enabledSwitch: Switch
    private lateinit var enabledLabel: TextView
    private lateinit var ipAddressLabel: TextView
    private lateinit var subnetLabel: TextView
    private lateinit var coordinatesLabel: TextView
    private lateinit var peersLabel: TextView
    private lateinit var peersRow: TableRow
    private lateinit var settingsRow: TableRow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.versionValue).text = Mobile.getVersion()

        enabledSwitch = findViewById(R.id.enableMulticastSwitch)
        enabledLabel = findViewById(R.id.yggdrasilStatusLabel)
        ipAddressLabel = findViewById(R.id.ipAddressValue)
        subnetLabel = findViewById(R.id.subnetValue)
        coordinatesLabel = findViewById(R.id.coordinatesValue)
        peersLabel = findViewById(R.id.peersValue)
        peersRow = findViewById(R.id.peersTableRow)
        settingsRow = findViewById(R.id.settingsTableRow)

        enabledLabel.setTextColor(Color.GRAY)

        VpnService.prepare(this)

        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            when (isChecked) {
                true -> {
                    val vpnintent = VpnService.prepare(this)
                    if (vpnintent != null) {
                        startActivityForResult(vpnintent, 0)
                    } else {
                        onActivityResult(0, RESULT_OK, vpnintent)
                    }
                }
                false -> {
                    val intent = Intent(this, PacketTunnelProvider::class.java)
                    intent.action = PacketTunnelProvider.ACTION_STOP
                    startService(intent)
                }
            }
        }

        peersRow.isClickable = true
        peersRow.setOnClickListener {
            val intent = Intent(this, PeersActivity::class.java)
            startActivity(intent)
        }

        settingsRow.isClickable = true
        settingsRow.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver, IntentFilter(PacketTunnelState.RECEIVER_INTENT)
        )
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.getStringExtra("type")) {
                "state" -> {
                    enabledLabel.text = if (intent.getBooleanExtra("started", false)) {
                        if (state.dhtCount() == 0) {
                            enabledLabel.setTextColor(Color.RED)
                            "No connectivity"
                        } else {
                            enabledLabel.setTextColor(Color.GREEN)
                            "Enabled"
                        }
                    } else {
                        enabledLabel.setTextColor(Color.GRAY)
                        "Not enabled"
                    }
                    ipAddressLabel.text = intent.getStringExtra("ip") ?: "N/A"
                    subnetLabel.text = intent.getStringExtra("subnet") ?: "N/A"
                    coordinatesLabel.text = intent.getStringExtra("coords") ?: "[]"
                    peersLabel.text = when (val count = state.peerCount()) {
                        0 -> "No peers"
                        1 -> "1 peer"
                        else -> "$count peers"
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            RESULT_OK -> {
                val intent = Intent(this, PacketTunnelProvider::class.java)
                intent.action = PacketTunnelProvider.ACTION_START
                startService(intent)
            }
        }
    }
}