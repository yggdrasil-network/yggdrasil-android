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
const val DEFAULT_DNS_SERVERS = "302:7991::53,302:db60::53,300:6223::53"

class DnsActivity : AppCompatActivity() {
    private lateinit var config: ConfigurationProxy
    private lateinit var inflater: LayoutInflater

    private lateinit var serversTableLayout: TableLayout
    private lateinit var serversTableLabel: TextView
    private lateinit var addServerButton: ImageButton
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

        addServerButton = findViewById(R.id.addServerButton)
        addServerButton.setOnClickListener {
            val view = inflater.inflate(R.layout.dialog_add_dns_server, null)
            val input = view.findViewById<TextInputEditText>(R.id.addDnsInput)
            val builder: AlertDialog.Builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.Theme_MaterialComponents_DayNight_Dialog))
            builder.setTitle("Add DNS server")
            builder.setView(view)
            builder.setPositiveButton("Add") { dialog, _ ->
                servers.add(input.text.toString())
                preferences.edit().apply {
                    this.putString(KEY_DNS_SERVERS, servers.joinToString(","))
                    this.commit()
                }
                dialog.dismiss()
                updateConfiguredServers()
            }
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            builder.show()
        }

        preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this.baseContext)
        val serverString = preferences.getString(KEY_DNS_SERVERS, DEFAULT_DNS_SERVERS)
        if (serverString!!.isNotEmpty()) {
            servers = serverString.split(",").toMutableList()
        } else {
            servers = mutableListOf()
        }
    }

    override fun onResume() {
        super.onResume()
        updateConfiguredServers()
    }

    @SuppressLint("ApplySharedPref")
    private fun updateConfiguredServers() {
        when (servers.size) {
            0 -> {
                serversTableLayout.visibility = View.GONE
                serversTableLabel.text = "No servers configured"
            }
            else -> {
                serversTableLayout.visibility = View.VISIBLE
                serversTableLabel.text = "Configured servers"

                serversTableLayout.removeAllViewsInLayout()
                for (i in servers.indices) {
                    val peer = servers[i]
                    val view = inflater.inflate(R.layout.peers_configured, null)
                    view.findViewById<TextView>(R.id.addressValue).text = peer
                    view.findViewById<ImageButton>(R.id.deletePeerButton).tag = i

                    view.findViewById<ImageButton>(R.id.deletePeerButton).setOnClickListener { button ->
                        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                        builder.setTitle("Remove ${peer}?")
                        builder.setPositiveButton("Remove") { dialog, _ ->
                            servers.removeAt(button.tag as Int)
                            preferences.edit().apply {
                                this.putString(KEY_DNS_SERVERS, servers.joinToString(","))
                                this.commit()
                            }
                            dialog.dismiss()
                            updateConfiguredServers()
                        }
                        builder.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.cancel()
                        }
                        builder.show()
                    }
                    serversTableLayout.addView(view)
                }
            }
        }
    }
}