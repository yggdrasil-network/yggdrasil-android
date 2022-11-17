package eu.neilalexander.yggdrasil

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class TileServiceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Just starting MainActivity
        val intent = Intent(this, MainActivity::class.java)
        startService(intent)
        finish()
    }
}