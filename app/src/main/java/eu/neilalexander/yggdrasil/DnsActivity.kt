package eu.neilalexander.yggdrasil

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.preference.PreferenceManager
import com.google.android.material.textfield.TextInputEditText

const val KEY_DNS_SERVERS = "dns_servers"
const val KEY_ENABLE_CHROME_FIX = "enable_chrome_fix"

class DnsActivity : AppCompatActivity() {
    private lateinit var config: ConfigurationProxy
    private lateinit var inflater: LayoutInflater

    private lateinit var serversTableLayout: TableLayout
    private lateinit var serversTableLabel: TextView
    private lateinit var serversTableHint: TextView
    private lateinit var addServerButton: ImageButton
    private lateinit var enableChromeFix: Switch

    private lateinit var servers: MutableList<String>
    private lateinit var preferences: SharedPreferences

    private lateinit var defaultDnsServers: HashMap<String, Pair<String, String>>

    @SuppressLint("ApplySharedPref")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dns)

        config = ConfigurationProxy(applicationContext)
        inflater = LayoutInflater.from(this)

        val descriptionRevertron = getString(R.string.dns_server_info_revertron)
        // Here we can add some other DNS servers in a future
        defaultDnsServers = hashMapOf(
            "302:7991::53" to Pair(getString(R.string.location_amsterdam), descriptionRevertron),
            "302:db60::53" to Pair(getString(R.string.location_prague), descriptionRevertron),
            "300:6223::53" to Pair(getString(R.string.location_bratislava), descriptionRevertron),
            "301:1088::53" to Pair(getString(R.string.location_buffalo), descriptionRevertron),
        )

        serversTableLayout = findViewById(R.id.configuredDnsTableLayout)
        serversTableLabel = findViewById(R.id.configuredDnsLabel)
        serversTableHint = findViewById(R.id.configuredDnsHint)
        enableChromeFix = findViewById(R.id.enableChromeFix)

        addServerButton = findViewById(R.id.addServerButton)
        addServerButton.setOnClickListener {
            val view = inflater.inflate(R.layout.dialog_add_dns_server, null)
            val input = view.findViewById<TextInputEditText>(R.id.addDnsInput)
            val builder: AlertDialog.Builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.Theme_MaterialComponents_DayNight_Dialog))
            builder.setTitle(getString(R.string.dns_add_server_dialog_title))
            builder.setView(view)
            builder.setPositiveButton(getString(R.string.add)) { dialog, _ ->
                val server = input.text.toString()
                if (!servers.contains(server)) {
                    servers.add(server)
                    preferences.edit().apply {
                        putString(KEY_DNS_SERVERS, servers.joinToString(","))
                        commit()
                    }
                    updateConfiguredServers()
                } else {
                    Toast.makeText(this, R.string.dns_already_added_server, Toast.LENGTH_SHORT).show()
                }
            }
            builder.setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.cancel()
            }
            builder.show()
        }

        enableChromeFix.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().apply {
                putBoolean(KEY_ENABLE_CHROME_FIX, isChecked)
                commit()
            }
        }

        val enableChromeFixPanel = findViewById<TableRow>(R.id.enableChromeFixPanel)
        enableChromeFixPanel.setOnClickListener {
            enableChromeFix.toggle()
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(this.baseContext)
        val serverString = preferences.getString(KEY_DNS_SERVERS, "")
        servers = if (serverString!!.isNotEmpty()) {
            serverString.split(",").toMutableList()
        } else {
            mutableListOf()
        }
        updateUsableServers()
    }

    override fun onResume() {
        super.onResume()
        updateConfiguredServers()
        enableChromeFix.isChecked = preferences.getBoolean(KEY_ENABLE_CHROME_FIX, false)
    }

    @SuppressLint("ApplySharedPref")
    private fun updateConfiguredServers() {
        when (servers.size) {
            0 -> {
                serversTableLayout.visibility = View.GONE
                serversTableLabel.text = getString(R.string.dns_no_configured_servers)
                serversTableHint.text = getText(R.string.dns_configured_servers_hint_empty)
            }
            else -> {
                serversTableLayout.visibility = View.VISIBLE
                serversTableLabel.text = getString(R.string.dns_configured_servers)
                serversTableHint.text = getText(R.string.dns_configured_servers_hint)

                serversTableLayout.removeAllViewsInLayout()
                for (i in servers.indices) {
                    val server = servers[i]
                    val view = inflater.inflate(R.layout.peers_configured, null)
                    view.findViewById<TextView>(R.id.addressValue).text = server
                    view.findViewById<ImageButton>(R.id.deletePeerButton).tag = i

                    view.findViewById<ImageButton>(R.id.deletePeerButton).setOnClickListener { button ->
                        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                        builder.setTitle(getString(R.string.dns_remove_title, server))
                        builder.setPositiveButton(getString(R.string.remove)) { dialog, _ ->
                            servers.removeAt(button.tag as Int)
                            preferences.edit().apply {
                                this.putString(KEY_DNS_SERVERS, servers.joinToString(","))
                                this.commit()
                            }
                            dialog.dismiss()
                            updateConfiguredServers()
                        }
                        builder.setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                            dialog.cancel()
                        }
                        builder.show()
                    }
                    serversTableLayout.addView(view)
                }
            }
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun updateUsableServers() {
        val usableTableLayout: TableLayout = findViewById(R.id.usableDnsTableLayout)

        defaultDnsServers.forEach {
            val server = it.key
            val infoPair = it.value
            val view = inflater.inflate(R.layout.dns_server_usable, null)
            view.findViewById<TextView>(R.id.serverValue).text = server
            val addButton = view.findViewById<ImageButton>(R.id.addButton)
            addButton.tag = server

            addButton.setOnClickListener { button ->
                val serverString = button.tag as String
                if (!servers.contains(serverString)) {
                    servers.add(serverString)
                    preferences.edit().apply {
                        this.putString(KEY_DNS_SERVERS, servers.joinToString(","))
                        this.commit()
                    }
                    updateConfiguredServers()
                } else {
                    Toast.makeText(this, R.string.dns_already_added_server, Toast.LENGTH_SHORT).show()
                }
            }
            view.setOnLongClickListener {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                builder.setTitle(getString(R.string.dns_server_info_dialog_title))
                builder.setMessage("${infoPair.first}\n\n${infoPair.second}")
                builder.setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                    dialog.dismiss()
                }
                builder.show()
                true
            }

            usableTableLayout.addView(view)
        }
    }
}