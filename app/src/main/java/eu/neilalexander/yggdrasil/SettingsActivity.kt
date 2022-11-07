package eu.neilalexander.yggdrasil

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.*
import androidx.core.widget.doOnTextChanged
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {
    private lateinit var config: ConfigurationProxy
    private lateinit var inflater: LayoutInflater

    private lateinit var deviceNameEntry: EditText
    private lateinit var publicKeyLabel: TextView
    private lateinit var resetConfigurationRow: TableRow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        config = ConfigurationProxy(applicationContext)
        inflater = LayoutInflater.from(this)

        deviceNameEntry = findViewById(R.id.deviceNameEntry)
        publicKeyLabel = findViewById(R.id.publicKeyLabel)
        resetConfigurationRow = findViewById(R.id.resetConfigurationRow)

        deviceNameEntry.doOnTextChanged { text, _, _, _ ->
            config.updateJSON { cfg ->
                val nodeinfo = cfg.optJSONObject("NodeInfo")
                if (nodeinfo == null) {
                    cfg.put("NodeInfo", JSONObject("{}"))
                }
                cfg.getJSONObject("NodeInfo").put("name", text)
            }
        }

        resetConfigurationRow.setOnClickListener {
            val view = inflater.inflate(R.layout.dialog_resetconfig, null)
            val builder: AlertDialog.Builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.Theme_MaterialComponents_DayNight_Dialog))
            builder.setTitle(getString(R.string.settings_warning_title))
            builder.setView(view)
            builder.setPositiveButton(getString(R.string.settings_reset)) { dialog, _ ->
                config.resetJSON()
                updateView()
                dialog.dismiss()
            }
            builder.setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.cancel()
            }
            builder.show()
        }

        publicKeyLabel.setOnLongClickListener {
            val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("public key", publicKeyLabel.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(applicationContext,R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            true
        }

        updateView()
    }

    private fun updateView() {
        val nodeinfo = config.getJSON().optJSONObject("NodeInfo")
        if (nodeinfo != null) {
            deviceNameEntry.setText(nodeinfo.getString("name"), TextView.BufferType.EDITABLE)
        } else {
            deviceNameEntry.setText("", TextView.BufferType.EDITABLE)
        }

        publicKeyLabel.text = config.getJSON().getString("PublicKey")
    }
}
