package eu.neilalexander.yggdrasil

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Layout
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray

class PeersActivity : AppCompatActivity() {
    private var state = PacketTunnelState
    private lateinit var config: ConfigurationProxy
    private lateinit var inflater: LayoutInflater

    private lateinit var connectedTableLayout: TableLayout
    private lateinit var connectedTableLabel: TextView
    private lateinit var configuredTableLayout: TableLayout
    private lateinit var configuredTableLabel: TextView
    private lateinit var multicastSwitch: Switch
    private lateinit var addPeerButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_peers)

        config = ConfigurationProxy(applicationContext)
        inflater = LayoutInflater.from(this)

        connectedTableLayout = findViewById(R.id.connectedPeersTableLayout)
        connectedTableLabel = findViewById(R.id.connectedPeersLabel)

        configuredTableLayout = findViewById(R.id.configuredPeersTableLayout)
        configuredTableLabel = findViewById(R.id.configuredPeersLabel)

        multicastSwitch = findViewById(R.id.enableMulticastSwitch)
        multicastSwitch.setOnCheckedChangeListener { button, _ ->
            when (button.isChecked) {
                true -> {
                    config.updateJSON { json ->
                        json.put("MulticastInterfaces", JSONArray("[\"lo\", \".*\"]"))
                    }
                }
                false -> {
                    config.updateJSON { json ->
                        json.put("MulticastInterfaces", JSONArray("[\"lo\"]"))
                    }
                }
            }
        }
        var multicastInterfaceFound = false
        val multicastInterfaces = config.getJSON().getJSONArray("MulticastInterfaces")
        (0 until multicastInterfaces.length()).forEach {
            if (multicastInterfaces[it] == ".*") {
                multicastInterfaceFound = true
            }
        }
        multicastSwitch.isChecked = multicastInterfaceFound

        addPeerButton = findViewById(R.id.addPeerButton)
        addPeerButton.setOnClickListener {
            var view = inflater.inflate(R.layout.dialog_addpeer, null)
            var input = view.findViewById<TextInputEditText>(R.id.addPeerInput)
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle("Add Configured Peer")
            builder.setView(view)
            builder.setPositiveButton("Add") { dialog, _ ->
                config.updateJSON { json ->
                    json.getJSONArray("Peers").put(input.text)
                }
                dialog.dismiss()
                updateConfiguredPeers()
            }
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            builder.show()
        }
    }

    override fun onResume() {
        super.onResume()

        updateConfiguredPeers()
        updateConnectedPeers()
    }

    private fun updateConfiguredPeers() {
        val peers = config.getJSON().getJSONArray("Peers")

        when (peers.length()) {
            0 -> {
                configuredTableLayout.visibility = View.GONE
                configuredTableLabel.text = "No peers currently configured"
            }
            else -> {
                configuredTableLayout.visibility = View.VISIBLE
                configuredTableLabel.text = "Configured Peers"

                configuredTableLayout.removeAllViewsInLayout()
                for (i in 0 until peers.length()) {
                    val peer = peers[i].toString()
                    var view = inflater.inflate(R.layout.peers_configured, null)
                    view.findViewById<TextView>(R.id.addressValue).text = peer
                    view.findViewById<ImageButton>(R.id.deletePeerButton).tag = i

                    view.findViewById<ImageButton>(R.id.deletePeerButton).setOnClickListener { button ->
                        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                        builder.setTitle("Remove ${peer}?")
                        builder.setPositiveButton("Remove") { dialog, _ ->
                            config.updateJSON { json ->
                                json.getJSONArray("Peers").remove(button.tag as Int)
                            }
                            dialog.dismiss()
                            updateConfiguredPeers()
                        }
                        builder.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.cancel()
                        }
                        builder.show()
                    }
                    configuredTableLayout.addView(view)
                }
            }
        }
    }

    private fun updateConnectedPeers() {
        val peers = state.peersState ?: JSONArray("[]")

        when (peers.length()) {
            0 -> {
                connectedTableLayout.visibility = View.GONE
                connectedTableLabel.text = "No peers currently connected"
            }
            else -> {
                connectedTableLayout.visibility = View.VISIBLE
                connectedTableLabel.text = "Connected Peers"

                connectedTableLayout.removeAllViewsInLayout()
                for (i in 0 until peers.length()) {
                    val peer = peers.getJSONObject(i)
                    var view = inflater.inflate(R.layout.peers_connected, null)
                    val ip = peer.getString("IP")
                    view.findViewById<TextView>(R.id.addressLabel).text = ip
                    view.findViewById<TextView>(R.id.detailsLabel).text = peer.getString("Remote")
                    connectedTableLayout.addView(view)
                }
            }
        }
    }
}