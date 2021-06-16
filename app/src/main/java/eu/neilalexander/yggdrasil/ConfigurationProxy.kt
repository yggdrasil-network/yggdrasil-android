package eu.neilalexander.yggdrasil

import android.content.Context
import mobile.Mobile
import org.json.JSONObject
import java.io.File

object ConfigurationProxy {
    private lateinit var json: JSONObject
    private lateinit var file: File

    operator fun invoke(applicationContext: Context): ConfigurationProxy {
        file = File(applicationContext.filesDir, "yggdrasil.conf")
        if (!file.exists()) {
            val conf = Mobile.generateConfigJSON()
            if (file.createNewFile()) {
                file.writeBytes(conf)
            }
        }
        fix()
        return this
    }

    fun updateJSON(fn: (JSONObject) -> Unit) {
        json = JSONObject(file.readText(Charsets.UTF_8))
        fn(json)
        val str = json.toString()
        file.writeText(str, Charsets.UTF_8)
    }

    private fun fix() {
        updateJSON { json ->
            json.put("AdminListen", "none")
            json.put("IfName", "none")
            json.put("IfMTU", 65535)
        }
    }

    fun getJSON(): JSONObject {
        fix()
        return json
    }

    fun getJSONByteArray(): ByteArray {
        return json.toString().toByteArray(Charsets.UTF_8)
    }
}