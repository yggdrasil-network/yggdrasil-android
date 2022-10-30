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
import com.google.android.material.textfield.TextInputEditText

const val KEY_DNS_SERVERS = "dns_servers"
const val KEY_ENABLE_CHROME_FIX = "enable_chrome_fix"
const val DEFAULT_DNS_SERVERS = "302:7991::53,302:db60::53,300:6223::53,301:1088::53"

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

    @SuppressLint("ApplySharedPref")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dns)

        config = ConfigurationProxy(applicationContext)
        inflater = LayoutInflater.from(this)

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
                servers.add(input.text.toString())
                preferences.edit().apply {
                    putString(KEY_DNS_SERVERS, servers.joinToString(","))
                    commit()
                }
                dialog.dismiss()
                updateConfiguredServers()
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

        preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this.baseContext)
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
                        builder.setTitle("Remove ${server}?")
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
        val defaultServers = DEFAULT_DNS_SERVERS.split(",")

        defaultServers.forEach {
            val server = it
            val view = inflater.inflate(R.layout.dns_server_usable, null)
            view.findViewById<TextView>(R.id.serverValue).text = server
            val addButton = view.findViewById<ImageButton>(R.id.addButton)
            addButton.tag = server

            addButton.setOnClickListener { button ->
                servers.add(button.tag as String)
                preferences.edit().apply {
                    this.putString(KEY_DNS_SERVERS, servers.joinToString(","))
                    this.commit()
                }
                updateConfiguredServers()
            }
            view.setOnLongClickListener {
                val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                builder.setTitle(getString(R.string.dns_server_info_dialog_title))
                builder.setMessage(getText(R.string.dns_server_info_revertron))
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