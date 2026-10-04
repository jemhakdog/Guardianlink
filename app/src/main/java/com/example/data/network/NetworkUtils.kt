package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    const val DEFAULT_PORT = 8888

    /**
     * Resolves the device's actual IPv4 address on the local Wi-Fi or hotspot network.
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue

                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        if (host != null && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    /**
     * Checks if device is currently connected to a Wi-Fi network.
     */
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Gets a human-readable network type label (Wi-Fi, Cellular, Localhost).
     */
    fun getNetworkTypeLabel(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "Offline"
        val activeNetwork = cm.activeNetwork ?: return "Localhost / Offline"
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "Disconnected"

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi (LAN Enabled)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular (Mobile Data)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Local Network"
        }
    }

    /**
     * Resolves the device's public Internet WAN IP address using global IP echo services.
     * This provides the real global internet address for cross-network and OFW connections.
     */
    suspend fun getPublicIpAddress(): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val echoEndpoints = listOf(
                "https://api.ipify.org",
                "https://icanhazip.com",
                "https://ifconfig.me/ip"
            )
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            for (endpoint in echoEndpoints) {
                try {
                    val req = okhttp3.Request.Builder()
                        .url(endpoint)
                        .header("User-Agent", "GuardianLink/1.0")
                        .build()
                    client.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val ip = response.body?.string()?.trim()
                            if (!ip.isNullOrBlank() && ip.length <= 45 && !ip.contains("<html", ignoreCase = true)) {
                                return@withContext ip
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            null
        }
    }

    /**
     * Generates a global cloud relay channel name from the pairing code.
     */
    fun getCloudRelayChannel(pairingCode: String): String {
        val clean = pairingCode.trim().lowercase().replace("-", "").replace(" ", "")
        return "gl_$clean"
    }

    /**
     * Generates a shareable invite deep link for one-tap pairing without typing any IP address.
     */
    fun getInviteLink(pairingCode: String): String {
        return "guardianlink://pair?code=${pairingCode.trim().uppercase()}"
    }
}
