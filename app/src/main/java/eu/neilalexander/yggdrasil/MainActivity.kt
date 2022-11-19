package eu.neilalexander.yggdrasil

import android.app.Activity
import android.content.*
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.widget.Switch
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import eu.neilalexander.yggdrasil.PacketTunnelProvider.Companion.STATE_INTENT
import mobile.Mobile
import org.json.JSONArray


class MainActivity : AppCompatActivity() {
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
            val preferences = PreferenceManager.getDefaultSharedPreferences(this.baseContext)
            preferences.edit(commit = true) { putBoolean(PREF_KEY_ENABLED, isChecked) }
        }

        val enableYggdrasilPanel = findViewById<TableRow>(R.id.enableYggdrasilPanel)
        enableYggdrasilPanel.setOnClickListener {
            enabledSwitch.toggle()
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

        ipAddressLabel.setOnLongClickListener {
            val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("ip", ipAddressLabel.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(applicationContext,R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            true
        }

        subnetLabel.setOnLongClickListener {
            val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("subnet", subnetLabel.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(applicationContext,R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver, IntentFilter(STATE_INTENT)
        )
        val preferences = PreferenceManager.getDefaultSharedPreferences(this.baseContext)
        enabledSwitch.isChecked = preferences.getBoolean(PREF_KEY_ENABLED, false)
        val serverString = preferences.getString(KEY_DNS_SERVERS, "")
        if (serverString!!.isNotEmpty()) {
            val servers = serverString.split(",")
            dnsLabel.text = when (servers.size) {
                0 -> getString(R.string.dns_no_servers)
                1 -> getString(R.string.dns_one_server)
                else -> getString(R.string.dns_many_servers, servers.size)
            }
        } else {
            dnsLabel.text = getString(R.string.dns_no_servers)
        }
        (application as GlobalApplication).subscribe()
    }

    override fun onPause() {
        super.onPause()
        (application as GlobalApplication).unsubscribe()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.getStringExtra("type")) {
                "state" -> {
                    enabledLabel.text = if (intent.getBooleanExtra("started", false)) {
                        var count = 0
                        if (intent.hasExtra("dht")) {
                            val dht = intent.getStringExtra("dht")
                            if (dht != null && dht != "null") {
                                val dhtState = JSONArray(dht)
                                count = dhtState.length()
                            }
                        }
                        if (count == 0) {
                            enabledLabel.setTextColor(Color.RED)
                            getString(R.string.main_no_connectivity)
                        } else {
                            enabledLabel.setTextColor(Color.GREEN)
                            getString(R.string.main_enabled)
                        }
                    } else {
                        enabledLabel.setTextColor(Color.GRAY)
                        getString(R.string.main_disabled)
                    }
                    ipAddressLabel.text = intent.getStringExtra("ip") ?: "N/A"
                    subnetLabel.text = intent.getStringExtra("subnet") ?: "N/A"
                    coordinatesLabel.text = intent.getStringExtra("coords") ?: "[]"
                    if (intent.hasExtra("peers")) {
                        val peerState = JSONArray(intent.getStringExtra("peers") ?: "[]")
                        peersLabel.text = when (val count = peerState.length()) {
                            0 -> getString(R.string.main_no_peers)
                            1 -> getString(R.string.main_one_peer)
                            else -> getString(R.string.main_many_peers, count)
                        }
                    }
                }
            }
        }
    }
}
