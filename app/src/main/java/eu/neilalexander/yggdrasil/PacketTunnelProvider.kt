package eu.neilalexander.yggdrasil

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Handler
import android.os.Message
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import mobile.Yggdrasil
import org.json.JSONArray
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread


class PacketTunnelProvider: VpnService() {
    companion object {
        const val RECEIVER_INTENT = "eu.neilalexander.yggdrasil.PacketTunnelProvider.MESSAGE"

        const val ACTION_START = "eu.neilalexander.yggdrasil.PacketTunnelProvider.START"
        const val ACTION_STOP = "eu.neilalexander.yggdrasil.PacketTunnelProvider.STOP"
    }

    private var yggdrasil = Yggdrasil()
    private var started = AtomicBoolean()

    private lateinit var config: ConfigurationProxy

    private var readerThread: Thread? = null
    private var writerThread: Thread? = null
    private var updateThread: Thread? = null

    private var parcel: ParcelFileDescriptor? = null
    private var readerStream: FileInputStream? = null
    private var writerStream: FileOutputStream? = null

    override fun onCreate() {
        super.onCreate()
        config = ConfigurationProxy(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        stop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            return START_NOT_STICKY
        }
        return when (intent.action ?: ACTION_STOP) {
            ACTION_START -> {
                start(); START_STICKY
            }
            ACTION_STOP -> {
                stop(); START_NOT_STICKY
            }
            else -> {
                stop(); START_NOT_STICKY
            }
        }
    }

    private fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        Log.d("PacketTunnelProvider", config.getJSON().toString())
        yggdrasil.startJSON(config.getJSONByteArray())

        val address = yggdrasil.addressString
        var builder = Builder()
            .addAddress(address, 7)
            .addRoute("200::", 7)
            .allowBypass()
            .allowFamily(OsConstants.AF_INET)
            .setBlocking(true)
            .setMtu(yggdrasil.mtu.toInt())
            .setSession("Yggdrasil")

        parcel = builder.establish()
        val parcel = parcel
        if (parcel == null || !parcel.fileDescriptor.valid()) {
            stop()
            return
        }

        readerStream = FileInputStream(parcel.fileDescriptor)
        writerStream = FileOutputStream(parcel.fileDescriptor)

        readerThread = thread {
            reader()
        }
        writerThread = thread {
            writer()
        }
        updateThread = thread {
            updater()
        }

        val intent = Intent(RECEIVER_INTENT)
        intent.putExtra("type", "state")
        intent.putExtra("started", true)
        intent.putExtra("ip", yggdrasil.addressString)
        intent.putExtra("subnet", yggdrasil.subnetString)
        intent.putExtra("coords", yggdrasil.coordsString)
        intent.putExtra("peers", JSONArray(yggdrasil.peersJSON).length())
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun stop() {
        if (!started.compareAndSet(true, false)) {
            return
        }

        yggdrasil.stop()

        readerStream?.let {
            it.close()
            readerStream = null
        }
        writerStream?.let {
            it.close()
            writerStream = null
        }
        parcel?.let {
            it.close()
            parcel = null
        }

        readerThread?.let {
            it.interrupt()
            readerThread = null
        }
        writerThread?.let {
            it.interrupt()
            writerThread = null
        }
        updateThread?.let {
            it.interrupt()
            updateThread = null
        }

        val intent = Intent(RECEIVER_INTENT)
        intent.putExtra("type", "state")
        intent.putExtra("started", false)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

        stopSelf()
    }

    private fun updater() {
        updates@ while (started.get()) {
            val intent = Intent(RECEIVER_INTENT)
            intent.putExtra("type", "state")
            intent.putExtra("started", true)
            intent.putExtra("ip", yggdrasil.addressString)
            intent.putExtra("subnet", yggdrasil.subnetString)
            intent.putExtra("coords", yggdrasil.coordsString)
            intent.putExtra("peers", yggdrasil.peersJSON)
            intent.putExtra("dht", yggdrasil.dhtjson)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            if (Thread.currentThread().isInterrupted) {
                break@updates
            }
            try {
                Thread.sleep(2000)
            } catch (e: java.lang.InterruptedException) {
                return
            }
        }
    }

    private fun writer() {
        writes@ while (started.get()) {
            val writerStream = writerStream
            val writerThread = writerThread
            if (writerThread == null || writerStream == null) {
                break@writes
            }
            if (Thread.currentThread().isInterrupted || !writerStream.fd.valid()) {
                break@writes
            }
            try {
                val b = yggdrasil.recv()
                writerStream.write(b)
            } catch (e: Exception) {
                break@writes
            }
        }
        writerStream?.let {
            it.close()
            writerStream = null
        }
    }

    private fun reader() {
        var b = ByteArray(65535)
        reads@ while (started.get()) {
            val readerStream = readerStream
            val readerThread = readerThread
            if (readerThread == null || readerStream == null) {
                break@reads
            }
            if (Thread.currentThread().isInterrupted ||!readerStream.fd.valid()) {
                break@reads
            }
            try {
                val n = readerStream.read(b)
                yggdrasil.send(b.sliceArray(0..n))
            } catch (e: Exception) {
                break@reads
            }
        }
        readerStream?.let {
            it.close()
            readerStream = null
        }
    }
}