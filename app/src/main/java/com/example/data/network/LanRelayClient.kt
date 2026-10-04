package com.example.data.network

import android.util.Log
import com.example.data.model.RemoteCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LanRelayClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun pingDevice(ip: String, port: Int = NetworkUtils.DEFAULT_PORT): Pair<Boolean, Long> {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url("http://$ip:$port/ping")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startTime
                    Pair(response.isSuccessful, latency)
                }
            } catch (e: Exception) {
                Log.d("LanRelayClient", "Ping to $ip:$port failed: ${e.message}")
                Pair(false, -1L)
            }
        }
    }

    suspend fun sendCommand(ip: String, port: Int = NetworkUtils.DEFAULT_PORT, command: RemoteCommand): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("id", command.id)
                    put("pairingCode", command.pairingCode)
                    put("commandType", command.commandType.name)
                    put("valueInt", command.valueInt)
                    put("valueString", command.valueString)
                    put("timestamp", command.timestamp)
                }

                val body = json.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("http://$ip:$port/command")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e("LanRelayClient", "Failed sending command to $ip:$port: ${e.message}")
                false
            }
        }
    }
}
