package eu.neilalexander.yggdrasil

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import eu.neilalexander.yggdrasil.PacketTunnelProvider.Companion.STATE_INTENT
import mobile.Mobile
import org.json.JSONArray

const val APP_WEB_URL = "https://github.com/yggdrasil-network/yggdrasil-android"

class MainActivity : AppCompatActivity() {
    private lateinit var enabledSwitch: Switch
    private lateinit var enabledLabel: TextView
    private lateinit var ipAddressLabel: TextView
    private lateinit var subnetLabel: TextView
    private lateinit var peersLabel: TextView
    private lateinit var peersRow: LinearLayoutCompat
    private lateinit var dnsLabel: TextView
    private lateinit var dnsRow: LinearLayoutCompat
    private lateinit var settingsRow: LinearLayoutCompat
    private lateinit var versionRow: LinearLayoutCompat

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
        peersLabel = findViewById(R.id.peersValue)
        peersRow = findViewById(R.id.peersTableRow)
        dnsLabel = findViewById(R.id.dnsValue)
        dnsRow = findViewById(R.id.dnsTableRow)
        settingsRow = findViewById(R.id.settingsTableRow)
        versionRow = findViewById(R.id.versionTableRow)

        enabledLabel.setTextColor(Color.GRAY)

        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            when (isChecked) {
                true -> {
                    val vpnIntent = VpnService.prepare(this)
                    if (vpnIntent != null) {
                        startVpnActivity.launch(vpnIntent)
                    } else {
                        start()
                        enabledSwitch.isEnabled = false
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

        val enableYggdrasilPanel = findViewById<LinearLayoutCompat>(R.id.enableYggdrasilPanel)
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

        versionRow.isClickable = true
        versionRow.setOnClickListener {
            openUrlInBrowser(APP_WEB_URL)
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
                    val peerState = JSONArray(intent.getStringExtra("peers") ?: "[]")
                    var count = 0
                    for (i in 0..<peerState.length()) {
                        val peer = peerState.getJSONObject(i)
                        if (peer.getString("IP").isNotEmpty()) {
                            count += 1
                        }
                    }
                    enabledLabel.text = if (intent.getBooleanExtra("started", false)) {
                        showPeersNoteIfNeeded(peerState.length())
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
                    if (intent.hasExtra("peers")) {
                        peersLabel.text = when (count) {
                            0 -> getString(R.string.main_no_peers)
                            1 -> getString(R.string.main_one_peer)
                            else -> getString(R.string.main_many_peers, count)
                        }
                    }
                    if (!enabledSwitch.isEnabled) {
                        enabledSwitch.isEnabled = true
                    }
                }
            }
        }
    }

    private fun showPeersNoteIfNeeded(peerCount: Int) {
        if (peerCount > 0) return
        val preferences = PreferenceManager.getDefaultSharedPreferences(this@MainActivity.baseContext)
        if (!preferences.getBoolean(PREF_KEY_PEERS_NOTE, false)) {
            this@MainActivity.runOnUiThread {
                val builder: AlertDialog.Builder =
                    AlertDialog.Builder(ContextThemeWrapper(this@MainActivity, R.style.YggdrasilDialogs))
                builder.setTitle(getString(R.string.main_add_some_peers_title))
                builder.setMessage(getString(R.string.main_add_some_peers_message))
                builder.setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                    dialog.dismiss()
                }
                builder.show()
            }
            // Mark this note as shown
            preferences.edit().apply {
                putBoolean(PREF_KEY_PEERS_NOTE, true)
                commit()
            }
        }
    }

    fun openUrlInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Handle the exception if no browser is found
            Toast.makeText(this, getText(R.string.no_browser_found_toast), Toast.LENGTH_SHORT).show()
        }
    }
}
