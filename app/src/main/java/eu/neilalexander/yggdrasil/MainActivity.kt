package eu.neilalexander.yggdrasil

import android.app.Activity
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import mobile.Mobile


class MainActivity : AppCompatActivity() {
    private var state = PacketTunnelState

    private lateinit var enabledSwitch: Switch
    private lateinit var enabledLabel: TextView
    private lateinit var ipAddressLabel: TextView
    private lateinit var subnetLabel: TextView
    private lateinit var coordinatesLabel: TextView
    private lateinit var peersLabel: TextView
    private lateinit var peersRow: TableRow
    private lateinit var dnsLabel: TextView
    private lateinit var dnsRow: TableRow
    private lateinit var settingsRow: TableRow

    private fun start() {
        val intent = Intent(this, PacketTunnelProvider::class.java)
        intent.action = PacketTunnelProvider.ACTION_START
        startService(intent)
    }

    private var startVpnActivity = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
           start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.versionValue).text = Mobile.getVersion()

        enabledSwitch = findViewById(R.id.enableYggdrasil)
        enabledLabel = findViewById(R.id.yggdrasilStatusLabel)
        ipAddressLabel = findViewById(R.id.ipAddressValue)
        subnetLabel = findViewById(R.id.subnetValue)
        coordinatesLabel = findViewById(R.id.coordinatesValue)
        peersLabel = findViewById(R.id.peersValue)
        peersRow = findViewById(R.id.peersTableRow)
        dnsLabel = findViewById(R.id.dnsValue)
        dnsRow = findViewById(R.id.dnsTableRow)
        settingsRow = findViewById(R.id.settingsTableRow)

        enabledLabel.setTextColor(Color.GRAY)

        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            when (isChecked) {
                true -> {
                    val vpnIntent = VpnService.prepare(this)
                    if (vpnIntent != null) {
                        startVpnActivity.launch(vpnIntent)
                    } else {
                        start()
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

        dnsRow.isClickable = true
        dnsRow.setOnClickListener {
            val intent = Intent(this, DnsActivity::class.java)
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
        val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this.baseContext)
        val serverString = preferences.getString(KEY_DNS_SERVERS, "")
        if (serverString!!.isNotEmpty()) {
            val servers = serverString.split(",")
            dnsLabel.text = when (servers.size) {
                0 -> "No servers"
                1 -> "1 server"
                else -> "${servers.size} servers"
            }
        } else {
            dnsLabel.text = "No servers"
        }
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.getStringExtra("type")) {
                "state" -> {
                    enabledLabel.text = if (intent.getBooleanExtra("started", false)) {
                        enabledSwitch.isChecked = true
                        if (state.dhtCount() == 0) {
                            enabledLabel.setTextColor(Color.RED)
                            "No connectivity"
                        } else {
                            enabledLabel.setTextColor(Color.GREEN)
                            "Enabled"
                        }
                    } else {
                        enabledSwitch.isChecked = false
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
}