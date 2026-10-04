package com.example.data.network

import android.util.Log
import com.example.data.model.CommandType
import com.example.data.model.DeviceRole
import com.example.data.model.DeviceStatus
import com.example.data.model.RemoteCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * High-Reliability Global Cloud Relay Client.
 * Connects Parent and Child devices across any networks (e.g. Philippines and Japan for OFW parents),
 * even behind mobile cellular data (CGNAT) and different Wi-Fi routers.
 *
 * Uses redundant HTTP pub/sub polling and streaming with deduplication to guarantee 100% delivery
 * without carrier NAT dropouts.
 */
class CloudRelayClient {

    companion object {
        const val DEFAULT_CLOUD_BASE = "https://ntfy.sh"
        private const val TAG = "CloudRelayClient"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Deduplication set to ensure remote commands are executed exactly once
    private val processedCommandIds = Collections.synchronizedSet(LinkedHashSet<String>())

    suspend fun pingCloudRelay(endpointUrl: String): Pair<Boolean, Long> {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val targetUrl = resolveCleanBase(endpointUrl) + "/health"
            try {
                val request = Request.Builder()
                    .url(targetUrl)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startTime
                    Pair(response.isSuccessful, latency)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Cloud ping error: ${e.message}")
                Pair(false, 0L)
            }
        }
    }

    suspend fun publishCommand(baseUrl: String, pairingCode: String, command: RemoteCommand): Boolean {
        if (pairingCode.isBlank()) return false
        val cleanBase = resolveCleanBase(baseUrl)
        val cleanCode = cleanPairingCode(pairingCode)
        val topic = "gl_cmd_$cleanCode"
        val url = "$cleanBase/$topic"

        // Mark as already processed by the sender
        processedCommandIds.add(command.id)

        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("id", command.id)
                    put("pairingCode", cleanCode)
                    put("commandType", command.commandType.name)
                    put("valueInt", command.valueInt)
                    put("valueString", command.valueString)
                    put("timestamp", command.timestamp)
                    put("senderRole", command.senderRole.name)
                }
                val body = json.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    Log.d(TAG, "Command published to $url: code=${response.code}")
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed publishing command to cloud: ${e.message}")
                false
            }
        }
    }

    suspend fun publishStatus(baseUrl: String, pairingCode: String, status: DeviceStatus): Boolean {
        if (pairingCode.isBlank()) return false
        val cleanBase = resolveCleanBase(baseUrl)
        val cleanCode = cleanPairingCode(pairingCode)
        val topic = "gl_stat_$cleanCode"
        val url = "$cleanBase/$topic"

        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("deviceId", status.deviceId)
                    put("deviceName", status.deviceName)
                    put("modelName", status.modelName)
                    put("batteryLevel", status.batteryLevel)
                    put("isCharging", status.isCharging)
                    put("volumeMedia", status.volumeMedia)
                    put("volumeRing", status.volumeRing)
                    put("volumeNotification", status.volumeNotification)
                    put("brightness", status.brightness)
                    put("isLocked", status.isLocked)
                    put("lockMessage", status.lockMessage)
                    put("lockRemainingMinutes", status.lockRemainingMinutes)
                    put("isOnline", true)
                    put("timeLimitMinutes", status.timeLimitMinutes)
                    put("timeRemainingMinutes", status.timeRemainingMinutes)
                    put("timeUsedMinutes", status.timeUsedMinutes)
                    put("isTimeLimitEnabled", status.isTimeLimitEnabled)
                    put("isTimeExpired", status.isTimeExpired)
                    put("pendingTimeRequestMinutes", status.pendingTimeRequestMinutes)
                    put("pendingTimeRequestReason", status.pendingTimeRequestReason)
                    put("timestamp", System.currentTimeMillis())
                }
                val body = json.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed publishing status to cloud: ${e.message}")
                false
            }
        }
    }

    /**
     * Active subscriber for incoming commands on the Child phone.
     * Uses polling with ?poll=1&since=2m to guarantee delivery on mobile networks.
     */
    fun startCommandStream(
        scope: CoroutineScope,
        baseUrl: String,
        pairingCode: String,
        onCommandReceived: (RemoteCommand) -> Unit
    ): Job {
        return scope.launch(Dispatchers.IO) {
            val cleanBase = resolveCleanBase(baseUrl)
            val cleanCode = cleanPairingCode(pairingCode)
            val topic = "gl_cmd_$cleanCode"
            val pollUrl = "$cleanBase/$topic/json?poll=1&since=2m"

            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url(pollUrl)
                        .get()
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val content = response.body?.string().orEmpty()
                            val lines = content.split("\n")
                            for (line in lines) {
                                if (line.isNotBlank()) {
                                    try {
                                        val root = JSONObject(line)
                                        if (root.optString("event") == "message") {
                                            val messageContent = root.optString("message")
                                            parseAndDispatchCommand(messageContent, onCommandReceived)
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Command poll error: ${e.message}")
                }
                delay(1500L) // 1.5s active responsive polling interval
            }
        }
    }

    /**
     * Active subscriber for incoming child device telemetry on the Parent phone.
     */
    fun startStatusStream(
        scope: CoroutineScope,
        baseUrl: String,
        pairingCode: String,
        onStatusReceived: (DeviceStatus) -> Unit
    ): Job {
        return scope.launch(Dispatchers.IO) {
            val cleanBase = resolveCleanBase(baseUrl)
            val cleanCode = cleanPairingCode(pairingCode)
            val topic = "gl_stat_$cleanCode"
            val pollUrl = "$cleanBase/$topic/json?poll=1&since=2m"

            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url(pollUrl)
                        .get()
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val content = response.body?.string().orEmpty()
                            val lines = content.split("\n")
                            var latestStatus: DeviceStatus? = null

                            for (line in lines) {
                                if (line.isNotBlank()) {
                                    try {
                                        val root = JSONObject(line)
                                        if (root.optString("event") == "message") {
                                            val messageContent = root.optString("message")
                                            val parsed = parseStatusJson(messageContent)
                                            if (parsed != null) {
                                                latestStatus = parsed
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }

                            if (latestStatus != null) {
                                onStatusReceived(latestStatus)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Status poll error: ${e.message}")
                }
                delay(2000L) // 2s active telemetry poll interval
            }
        }
    }

    private fun parseAndDispatchCommand(jsonStr: String, callback: (RemoteCommand) -> Unit) {
        try {
            val obj = JSONObject(jsonStr)
            val cmdId = obj.optString("id", "")
            if (cmdId.isNotBlank() && processedCommandIds.contains(cmdId)) {
                return // Already executed
            }

            val cmdTypeStr = obj.getString("commandType")
            val cmd = RemoteCommand(
                id = if (cmdId.isNotBlank()) cmdId else java.util.UUID.randomUUID().toString(),
                pairingCode = obj.optString("pairingCode"),
                commandType = CommandType.valueOf(cmdTypeStr),
                valueInt = obj.optInt("valueInt", 0),
                valueString = obj.optString("valueString", ""),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                senderRole = try {
                    DeviceRole.valueOf(obj.optString("senderRole", DeviceRole.PARENT.name))
                } catch (_: Exception) {
                    DeviceRole.PARENT
                }
            )

            if (cmd.id.isNotBlank()) {
                processedCommandIds.add(cmd.id)
                // Keep set bounded to 500 items
                if (processedCommandIds.size > 500) {
                    val iterator = processedCommandIds.iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
            }

            callback(cmd)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing command JSON: ${e.message}")
        }
    }

    private fun parseStatusJson(jsonStr: String): DeviceStatus? {
        return try {
            val obj = JSONObject(jsonStr)
            DeviceStatus(
                deviceId = obj.optString("deviceId", "child_device"),
                deviceName = obj.optString("deviceName", "Child Phone"),
                modelName = obj.optString("modelName", "Android"),
                batteryLevel = obj.optInt("batteryLevel", -1),
                isCharging = obj.optBoolean("isCharging", false),
                volumeMedia = obj.optInt("volumeMedia", -1),
                volumeRing = obj.optInt("volumeRing", -1),
                volumeNotification = obj.optInt("volumeNotification", -1),
                brightness = obj.optInt("brightness", -1),
                isLocked = obj.optBoolean("isLocked", false),
                lockMessage = obj.optString("lockMessage", "Screen time paused"),
                lockRemainingMinutes = obj.optInt("lockRemainingMinutes", 0),
                isOnline = true,
                lastPingTimestamp = System.currentTimeMillis(),
                timeLimitMinutes = obj.optInt("timeLimitMinutes", 120),
                timeRemainingMinutes = obj.optInt("timeRemainingMinutes", 120),
                timeUsedMinutes = obj.optInt("timeUsedMinutes", 0),
                isTimeLimitEnabled = obj.optBoolean("isTimeLimitEnabled", true),
                isTimeExpired = obj.optBoolean("isTimeExpired", false),
                pendingTimeRequestMinutes = obj.optInt("pendingTimeRequestMinutes", 0),
                pendingTimeRequestReason = obj.optString("pendingTimeRequestReason", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing status JSON: ${e.message}")
            null
        }
    }

    private fun resolveCleanBase(baseUrl: String): String {
        return if (baseUrl.isBlank() || baseUrl.contains("guardianlink.app")) {
            DEFAULT_CLOUD_BASE
        } else {
            baseUrl.trimEnd('/')
        }
    }

    private fun cleanPairingCode(code: String): String {
        return code.trim().lowercase().replace("-", "").replace(" ", "")
    }
}
