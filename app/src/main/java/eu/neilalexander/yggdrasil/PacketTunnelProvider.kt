package eu.neilalexander.yggdrasil

import android.content.Intent
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import eu.neilalexander.yggdrasil.YggStateReceiver.Companion.YGG_STATE_INTENT
import mobile.Yggdrasil
import org.json.JSONArray
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet6Address
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread


private const val TAG = "PacketTunnelProvider"
const val SERVICE_NOTIFICATION_ID = 1000

open class PacketTunnelProvider: VpnService() {
    companion object {
        const val STATE_INTENT = "eu.neilalexander.yggdrasil.PacketTunnelProvider.STATE_MESSAGE"

        const val ACTION_START = "eu.neilalexander.yggdrasil.PacketTunnelProvider.START"
        const val ACTION_STOP = "eu.neilalexander.yggdrasil.PacketTunnelProvider.STOP"
        const val ACTION_TOGGLE = "eu.neilalexander.yggdrasil.PacketTunnelProvider.TOGGLE"
        const val ACTION_CONNECT = "eu.neilalexander.yggdrasil.PacketTunnelProvider.CONNECT"
    }

    private var yggdrasil = Yggdrasil()
    private var started = AtomicBoolean()

    private lateinit var config: ConfigurationProxy
    private var customDnsPort: Int = 0

    private var readerThread: Thread? = null
    private var writerThread: Thread? = null
    private var updateThread: Thread? = null

    private var parcel: ParcelFileDescriptor? = null
    private var readerStream: FileInputStream? = null
    private var writerStream: FileOutputStream? = null
    private var multicastLock: WifiManager.MulticastLock? = null

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
            Log.d(TAG, "Intent is null")
            return START_NOT_STICKY
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(this.baseContext)
        val enabled = preferences.getBoolean(PREF_KEY_ENABLED, false)
        return when (intent.action ?: ACTION_STOP) {
            ACTION_STOP -> {
                Log.d(TAG, "Stopping...")
                stop(); START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                Log.d(TAG, "Connecting...")
                if (started.get()) {
                    connect()
                } else {
                    start()
                }
                START_STICKY
            }
            ACTION_TOGGLE -> {
                Log.d(TAG, "Toggling...")
                if (started.get()) {
                    stop(); START_NOT_STICKY
                } else {
                    start(); START_STICKY
                }
            }
            else -> {
                if (!enabled) {
                    Log.d(TAG, "Service is disabled")
                    return START_NOT_STICKY
                }
                Log.d(TAG, "Starting...")
                start(); START_STICKY
            }
        }
    }

    private fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        val notification = createServiceNotification(this, State.Enabled)
        startForeground(SERVICE_NOTIFICATION_ID, notification)

        // Acquire multicast lock
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("Yggdrasil").apply {
            setReferenceCounted(false)
            acquire()
        }

        Log.d(TAG, config.getJSON().toString())
        yggdrasil.startJSON(config.getJSONByteArray())

        val address = yggdrasil.addressString
        var hasCustomDns = false
        
        val preferences = PreferenceManager.getDefaultSharedPreferences(this.baseContext)
        val serverString = preferences.getString(KEY_DNS_SERVERS, "")
        
        // First, check if we have custom DNS servers to determine bypass behavior
        if (serverString!!.isNotEmpty()) {
            val servers = serverString.split(",")
            servers.forEach { server ->
                val trimmedServer = server.trim()
                if (trimmedServer.startsWith("127.0.0.1:")) {
                    hasCustomDns = true
                    // Extract port number from 127.0.0.1:5353 format
                    val portString = trimmedServer.substring(10) // Remove "127.0.0.1:"
                    customDnsPort = portString.toIntOrNull() ?: 5353
                    Log.i(TAG, "Found custom IPv4 DNS server: $trimmedServer, port: $customDnsPort")
                }
            }
        }
        
        val builder = Builder()
            .addAddress(address, 7)
            .addRoute("200::", 7)
            // We do this to trick the DNS-resolver into thinking that we have "regular" IPv6,
            // and therefore we need to resolve AAAA DNS-records.
            // See: https://android.googlesource.com/platform/bionic/+/refs/heads/master/libc/dns/net/getaddrinfo.c#1935
            // and: https://android.googlesource.com/platform/bionic/+/refs/heads/master/libc/dns/net/getaddrinfo.c#365
            // If we don't do this the DNS-resolver just doesn't do DNS-requests with record type AAAA,
            // and we can't use DNS with Yggdrasil addresses.
            .addRoute("2000::", 128)
            .allowFamily(OsConstants.AF_INET)
        
        // Only allow bypass if no custom DNS servers
        if (!hasCustomDns) {
            builder.allowBypass()
            Log.d(TAG, "Allowing VPN bypass - no custom DNS")
        } else {
            Log.i(TAG, "Not allowing VPN bypass - forcing DNS through VPN")
            // Add route only for our dummy DNS server
            builder.addRoute("198.18.0.1", 32)              // Private IPv4 DNS (dummy)
        }
        
        builder
            .setBlocking(true)
            .setMtu(yggdrasil.mtu.toInt())
            .setSession("Yggdrasil")
        // On Android API 29+ apps can opt-in/out to using metered networks.
        // If we don't set metered status of VPN it is considered as metered.
        // If we set it to false, then it will inherit this status from underlying network.
        // See: https://developer.android.com/reference/android/net/VpnService.Builder#setMetered(boolean)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        // Now add the actual DNS servers
        if (serverString.isNotEmpty()) {
            val servers = serverString.split(",")
            if (servers.isNotEmpty()) {
                servers.forEach { server ->
                    val trimmedServer = server.trim()
                    if (trimmedServer.startsWith("127.0.0.1:")) {
                        // Add only private DNS as dummy server to intercept
                        builder.addDnsServer("198.18.0.1")        // Private IPv4 DNS (dummy)
                        Log.i(TAG, "Added dummy DNS server 198.18.0.1 to intercept for 127.0.0.1:$customDnsPort")
                    } else {
                        Log.i(TAG, "Using standard DNS server $trimmedServer")
                        builder.addDnsServer(trimmedServer)
                    }
                }
            }
        }
        if (preferences.getBoolean(KEY_ENABLE_CHROME_FIX, false)) {
            builder.addRoute("2001:4860:4860::8888", 128)
        }

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

        var intent = Intent(YGG_STATE_INTENT)
        intent.putExtra("state", STATE_ENABLED)
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

        var intent = Intent(STATE_INTENT)
        intent.putExtra("type", "state")
        intent.putExtra("started", false)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

        intent = Intent(YGG_STATE_INTENT)
        intent.putExtra("state", STATE_DISABLED)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

        stopForeground(true)
        stopSelf()
        multicastLock?.release()
    }

    private fun connect() {
        if (!started.get()) {
            return
        }
        yggdrasil.retryPeersNow()
    }

    private fun updater() {
        try {
            Thread.sleep(500)
        } catch (_: InterruptedException) {
            return
        }
        var lastStateUpdate = System.currentTimeMillis()
        updates@ while (started.get()) {
            val treeJSON = yggdrasil.treeJSON
            if ((application as  GlobalApplication).needUiUpdates()) {
                val intent = Intent(STATE_INTENT)
                intent.putExtra("type", "state")
                intent.putExtra("started", true)
                intent.putExtra("ip", yggdrasil.addressString)
                intent.putExtra("subnet", yggdrasil.subnetString)
                intent.putExtra("pubkey", yggdrasil.publicKeyString)
                intent.putExtra("peers", yggdrasil.peersJSON)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
            val curTime = System.currentTimeMillis()
            if (lastStateUpdate + 10000 < curTime) {
                val intent = Intent(YGG_STATE_INTENT)
                var state = STATE_ENABLED
                if (yggdrasil.routingEntries > 0) {
                    state = STATE_CONNECTED
                }
                if (treeJSON != null && treeJSON != "null") {
                    val treeState = JSONArray(treeJSON)
                    val count = treeState.length()
                    if (count > 1)
                        state = STATE_CONNECTED
                }
                intent.putExtra("state", state)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                lastStateUpdate = curTime
            }

            if (Thread.currentThread().isInterrupted) {
                break@updates
            }
            if (sleep()) return
        }
    }

    private fun sleep(): Boolean {
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            return true
        }
        return false
    }

    private fun writer() {
        val buf = ByteArray(65535)
        writes@ while (started.get()) {
            val writerStream = writerStream
            val writerThread = writerThread
            if (writerThread == null || writerStream == null) {
                Log.i(TAG, "Write thread or stream is null")
                break@writes
            }
            if (Thread.currentThread().isInterrupted || !writerStream.fd.valid()) {
                Log.i(TAG, "Write thread interrupted or file descriptor is invalid")
                break@writes
            }
            try {
                val len = yggdrasil.recvBuffer(buf)
                if (len > 0) {
                    writerStream.write(buf, 0, len.toInt())
                }
            } catch (e: Exception) {
                Log.i(TAG, "Error in write: $e")
                if (e.toString().contains("ENOBUFS")) {
                    //TODO Check this by some error code
                    //More info about this: https://github.com/AdguardTeam/AdguardForAndroid/issues/724
                    continue
                }
                break@writes
            }
        }
        writerStream?.let {
            it.close()
            writerStream = null
        }
    }

    private fun reader() {
        val b = ByteArray(65535)
        reads@ while (started.get()) {
            val readerStream = readerStream
            val readerThread = readerThread
            if (readerThread == null || readerStream == null) {
                Log.i(TAG, "Read thread or stream is null")
                break@reads
            }
            if (Thread.currentThread().isInterrupted ||!readerStream.fd.valid()) {
                Log.i(TAG, "Read thread interrupted or file descriptor is invalid")
                break@reads
            }
            try {
                val n = readerStream.read(b)
                
                if (n > 0) {
                    if (n > 20) {
                        val version = (b[0].toInt() and 0xF0) shr 4
                        
                        if (version == 4 && n >= 20) {
                            val protocol = b[9].toInt() and 0xFF
                            val srcIP = String.format("%d.%d.%d.%d", 
                                b[12].toInt() and 0xFF, b[13].toInt() and 0xFF, 
                                b[14].toInt() and 0xFF, b[15].toInt() and 0xFF)
                            val dstIP = String.format("%d.%d.%d.%d", 
                                b[16].toInt() and 0xFF, b[17].toInt() and 0xFF, 
                                b[18].toInt() and 0xFF, b[19].toInt() and 0xFF)
                            
                            if (protocol == 17 && n >= 28) { // UDP
                                val ipHeaderLength = (b[0].toInt() and 0x0F) * 4
                                val srcPort = ((b[ipHeaderLength].toInt() and 0xFF) shl 8) or 
                                             (b[ipHeaderLength + 1].toInt() and 0xFF)
                                val destPort = ((b[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or 
                                              (b[ipHeaderLength + 3].toInt() and 0xFF)
                                
                                if (destPort == 53 && customDnsPort > 0) {
                                    // Forward DNS query to custom server and inject response
                                    forwardDnsQuery(b, n, ipHeaderLength + 8, true, srcIP, srcPort)
                                    continue@reads // Skip normal processing
                                }
                            }
                        }
                    }
                }
                
                yggdrasil.sendBuffer(b, n.toLong())
            } catch (e: Exception) {
                Log.i(TAG, "Error in sendBuffer: $e")
                break@reads
            }
        }
        readerStream?.let {
            it.close()
            readerStream = null
        }
    }
    
    private fun forwardDnsQuery(packet: ByteArray, packetLength: Int, dnsPayloadOffset: Int, isIPv4: Boolean, srcIP: String, srcPort: Int) {
        try {
            // Extract DNS payload
            val dnsPayloadLength = packetLength - dnsPayloadOffset
            val dnsPayload = ByteArray(dnsPayloadLength)
            System.arraycopy(packet, dnsPayloadOffset, dnsPayload, 0, dnsPayloadLength)
            
            
            // Forward to custom DNS server at 127.0.0.1:customDnsPort
            thread {
                var socket: DatagramSocket? = null
                try {
                    
                    // Create socket with no specific binding - let system choose any available port on any interface
                    socket = DatagramSocket()
                    socket.soTimeout = 1000 // 1 second timeout
                    
                    
                    val address = InetAddress.getByName("127.0.0.1")
                    val outPacket = DatagramPacket(dnsPayload, dnsPayload.size, address, customDnsPort)
                    
                    socket.send(outPacket)
                    
                    // Wait for response
                    val responseBuffer = ByteArray(1024)
                    val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                    
                    socket.receive(responsePacket)
                    
                    val responseData = ByteArray(responsePacket.length)
                    System.arraycopy(responseBuffer, 0, responseData, 0, responsePacket.length)
                    
                    
                    // Inject response back into VPN tunnel (IPv4 only)
                    injectDnsResponse(responseData, true, srcIP, srcPort)
                    
                } catch (e: java.net.SocketTimeoutException) {
                    Log.e(TAG, "Timeout waiting for DNS response from 127.0.0.1:$customDnsPort")
                } catch (e: Exception) {
                    Log.e(TAG, "Error forwarding DNS query: $e")
                } finally {
                    socket?.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in forwardDnsQuery: $e")
        }
    }
    
    private fun injectDnsResponse(dnsResponse: ByteArray, isIPv4: Boolean, originalSrcIP: String, originalSrcPort: Int) {
        try {
            val writerStream = writerStream ?: return
            
            
            // Create IPv4 UDP packet with DNS response from 198.18.0.1:53 back to client
            val responsePacket = createIPv4UdpPacket(dnsResponse, "198.18.0.1", 53, originalSrcIP, originalSrcPort)
            if (responsePacket.isNotEmpty()) {
                writerStream.write(responsePacket)
            } else {
                Log.e(TAG, "Failed to create response packet")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting DNS response: $e")
        }
    }
    
    private fun createIPv4UdpPacket(payload: ByteArray, srcIP: String, srcPort: Int, dstIP: String, dstPort: Int): ByteArray {
        val totalLength = 20 + 8 + payload.size // IP header + UDP header + payload
        val packet = ByteArray(totalLength)
        
        // IPv4 Header (20 bytes)
        packet[0] = 0x45 // Version 4, Header length 5 (20 bytes)
        packet[1] = 0x00 // Type of Service
        packet[2] = (totalLength shr 8).toByte() // Total length high byte
        packet[3] = (totalLength and 0xFF).toByte() // Total length low byte
        packet[4] = 0x00 // Identification high byte
        packet[5] = 0x00 // Identification low byte
        packet[6] = 0x40 // Flags: Don't fragment
        packet[7] = 0x00 // Fragment offset
        packet[8] = 64 // TTL
        packet[9] = 17 // Protocol: UDP
        packet[10] = 0x00 // Header checksum (will calculate)
        packet[11] = 0x00 // Header checksum
        
        // Source IP
        val srcBytes = srcIP.split(".").map { it.toInt().toByte() }
        packet[12] = srcBytes[0]
        packet[13] = srcBytes[1]
        packet[14] = srcBytes[2]
        packet[15] = srcBytes[3]
        
        // Destination IP
        val dstBytes = dstIP.split(".").map { it.toInt().toByte() }
        packet[16] = dstBytes[0]
        packet[17] = dstBytes[1]
        packet[18] = dstBytes[2]
        packet[19] = dstBytes[3]
        
        // Calculate IP header checksum
        val checksum = calculateIPv4Checksum(packet, 0, 20)
        packet[10] = (checksum shr 8).toByte()
        packet[11] = (checksum and 0xFF).toByte()
        
        // UDP Header (8 bytes)
        val udpLength = 8 + payload.size
        packet[20] = (srcPort shr 8).toByte() // Source port high byte
        packet[21] = (srcPort and 0xFF).toByte() // Source port low byte
        packet[22] = (dstPort shr 8).toByte() // Dest port high byte
        packet[23] = (dstPort and 0xFF).toByte() // Dest port low byte
        packet[24] = (udpLength shr 8).toByte() // UDP length high byte
        packet[25] = (udpLength and 0xFF).toByte() // UDP length low byte
        packet[26] = 0x00 // UDP checksum (optional for IPv4)
        packet[27] = 0x00 // UDP checksum
        
        // Copy payload
        System.arraycopy(payload, 0, packet, 28, payload.size)
        
        return packet
    }
    
    
    private fun calculateIPv4Checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        
        // Sum all 16-bit words
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) + (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        
        // Add odd byte if present
        if (i < offset + length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        
        // Add carry bits
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        
        return (sum.inv() and 0xFFFF).toInt()
    }
    
}
