package app.lifesaver.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import app.lifesaver.blocklist.Blocklist
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

class BlockerVpnService : VpnService() {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        if (!promoteToForeground()) {
            VpnStatus.set(VpnPhase.Error)
            stopSelf()
            return START_NOT_STICKY
        }
        startLoop()
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        running.set(false)
        closeTun()
        super.onDestroy()
    }

    private fun startLoop() {
        if (worker?.isAlive == true) {
            return
        }
        running.set(true)
        VpnStatus.set(VpnPhase.Starting)
        worker = Thread(::runLoop, "vpn-io").also { it.start() }
    }

    private fun runLoop() {
        val established = if (loadBlocklist()) establishTun() else null
        if (established == null) {
            VpnStatus.set(VpnPhase.Error)
            running.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        tun = established
        VpnStatus.set(VpnPhase.On)
        val input = FileInputStream(established.fileDescriptor)
        val output = FileOutputStream(established.fileDescriptor)
        val buffer = ByteArray(MAX_PACKET)
        try {
            while (running.get()) {
                val size = input.read(buffer)
                val response = if (size > 0) handlePacket(buffer, size) else null
                if (response != null) {
                    output.write(response)
                }
            }
        } catch (e: IOException) {
            if (running.get()) {
                Log.w(TAG, "vpn read ended: ${e.javaClass.simpleName}")
            }
        } finally {
            running.set(false)
            closeTun()
            VpnStatus.set(VpnPhase.Off)
        }
    }

    /** Fails closed: without a list the VPN does not come up and the UI shows an error. */
    private fun loadBlocklist(): Boolean =
        try {
            Blocklist.ensureLoaded(this)
            true
        } catch (e: IOException) {
            Log.e(TAG, "blocklist load failed: ${e.javaClass.simpleName}")
            false
        }

    private fun handlePacket(
        buffer: ByteArray,
        size: Int,
    ): ByteArray? {
        val query = PacketCodec.dnsQuery(buffer, size) ?: return null
        val payload =
            if (Blocklist.current.blocksAny(query.names)) {
                Log.d(TAG, "sinkhole")
                DnsMessages.nxDomain(query.payload)
            } else {
                Log.d(TAG, "forward")
                forward(query.payload)
            } ?: return null
        return PacketCodec.dnsResponsePacket(buffer, size, payload)
    }

    private fun forward(payload: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                if (!protect(socket)) {
                    Log.d(TAG, "protect failed")
                    return null
                }
                socket.soTimeout = FORWARD_TIMEOUT_MS
                val upstream = upstreamDns()
                socket.send(DatagramPacket(payload, payload.size, upstream, DNS_PORT))
                val buf = ByteArray(MAX_DNS)
                val response = DatagramPacket(buf, buf.size)
                socket.receive(response)
                buf.copyOf(response.length)
            }
        } catch (e: IOException) {
            Log.d(TAG, "forward failed: ${e.javaClass.simpleName}")
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun upstreamDns(): InetAddress {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val candidates = ArrayList<InetAddress>()
        for (network in connectivity.allNetworks) {
            val caps = connectivity.getNetworkCapabilities(network)
            if (
                caps != null &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) {
                connectivity.getLinkProperties(network)?.dnsServers?.let { candidates.addAll(it) }
            }
        }
        val usable = candidates.filterNot { isOurResolver(it) }
        return usable.firstOrNull { it is Inet4Address }
            ?: usable.firstOrNull()
            ?: InetAddress.getByName(FALLBACK_DNS)
    }

    private fun establishTun(): ParcelFileDescriptor? =
        try {
            val builder =
                Builder()
                    .setSession("LifeSaver")
                    .setMtu(MTU)
                    .addAddress(VPN_IPV4, IPV4_PREFIX)
                    .addDnsServer(DNS_IPV4)
                    .addRoute(DNS_IPV4, IPV4_PREFIX)
                    .addAddress(VPN_IPV6, IPV6_PREFIX)
                    .addDnsServer(DNS_IPV6)
                    .addRoute(DNS_IPV6, IPV6_PREFIX)
                    .setBlocking(true)
                    .setMetered(false)
            // Known public resolvers are routed into the TUN and dropped (see KnownResolvers).
            KnownResolvers.ipv4.forEach { builder.addRoute(it, IPV4_PREFIX) }
            KnownResolvers.ipv6.forEach { builder.addRoute(it, IPV6_PREFIX) }
            builder.establish()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "establish rejected: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "establish rejected: ${e.javaClass.simpleName}")
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "establish rejected: ${e.javaClass.simpleName}")
            null
        }

    private fun promoteToForeground(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(CHANNEL_ID, "Protection", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending =
            PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            Notification
                .Builder(this, CHANNEL_ID)
                .setContentTitle("LifeSaver")
                .setContentText("Protection is on")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "foreground rejected: ${e.javaClass.simpleName}")
            false
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "foreground rejected: ${e.javaClass.simpleName}")
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "foreground rejected: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun stopVpn() {
        running.set(false)
        closeTun()
        stopForeground(STOP_FOREGROUND_REMOVE)
        VpnStatus.set(VpnPhase.Off)
        stopSelf()
    }

    private fun closeTun() {
        try {
            tun?.close()
        } catch (e: IOException) {
            Log.d(TAG, "close tun: ${e.javaClass.simpleName}")
        }
        tun = null
    }

    private fun isOurResolver(address: InetAddress): Boolean {
        val bytes = address.address
        return bytes.contentEquals(ourIpv4.address) || bytes.contentEquals(ourIpv6.address)
    }

    companion object {
        const val ACTION_STOP = "app.lifesaver.vpn.STOP"
        private const val TAG = "BlockerVpn"
        private const val CHANNEL_ID = "protection"
        private const val NOTIFICATION_ID = 1
        private const val DNS_PORT = 53
        private const val MTU = 1500
        private const val MAX_PACKET = 32767
        private const val MAX_DNS = 4096
        private const val FORWARD_TIMEOUT_MS = 3000
        private const val IPV4_PREFIX = 32
        private const val IPV6_PREFIX = 128
        private const val VPN_IPV4 = "10.0.0.2"
        private const val DNS_IPV4 = "10.0.0.1"
        private const val VPN_IPV6 = "fd00:1::2"
        private const val DNS_IPV6 = "fd00:1::1"
        private const val FALLBACK_DNS = "8.8.8.8"

        private val ourIpv4: InetAddress = InetAddress.getByName(DNS_IPV4)
        private val ourIpv6: InetAddress = InetAddress.getByName(DNS_IPV6)
    }
}
