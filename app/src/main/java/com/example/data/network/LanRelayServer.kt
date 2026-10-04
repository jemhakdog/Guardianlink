package com.example.data.network

import android.util.Log
import com.example.data.model.CommandType
import com.example.data.model.DeviceStatus
import com.example.data.model.RemoteCommand
import com.example.data.relay.RelayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class LanRelayServer(private val relayManager: RelayManager) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    var isRunning: Boolean = false
        private set

    fun start(port: Int = NetworkUtils.DEFAULT_PORT) {
        if (isRunning) return
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                Log.d("LanRelayServer", "LAN Server started on port $port")

                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleClient(clientSocket)
                    } catch (e: Exception) {
                        if (!isRunning) break
                        Log.e("LanRelayServer", "Error accepting client: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("LanRelayServer", "Failed to bind LAN server to port $port: ${e.message}")
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {}
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val out: OutputStream = socket.getOutputStream()

                val requestLine = reader.readLine() ?: return@launch
                val tokens = requestLine.split(" ")
                val method = if (tokens.isNotEmpty()) tokens[0] else "GET"
                val path = if (tokens.size > 1) tokens[1] else "/"

                var contentLength = 0
                var line: String? = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    if (line.lowercase().startsWith("content-length:")) {
                        contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                    }
                    line = reader.readLine()
                }

                val body = if (contentLength > 0) {
                    val buffer = CharArray(contentLength)
                    reader.read(buffer, 0, contentLength)
                    String(buffer)
                } else ""

                val (statusCode, responseBody) = when {
                    path.startsWith("/ping") -> {
                        Pair(200, JSONObject().put("status", "ok").put("role", "CHILD").toString())
                    }
                    path.startsWith("/status") -> {
                        val st = relayManager.deviceStatus.value
                        val json = JSONObject().apply {
                            put("deviceId", st.deviceId)
                            put("deviceName", st.deviceName)
                            put("batteryLevel", st.batteryLevel)
                            put("volumeMedia", st.volumeMedia)
                            put("brightness", st.brightness)
                            put("isLocked", st.isLocked)
                            put("timeRemainingMinutes", st.timeRemainingMinutes)
                            put("timeLimitMinutes", st.timeLimitMinutes)
                            put("isTimeExpired", st.isTimeExpired)
                        }
                        Pair(200, json.toString())
                    }
                    path.startsWith("/command") && method.equals("POST", ignoreCase = true) -> {
                        val result = processCommandBody(body)
                        Pair(200, JSONObject().put("success", result).toString())
                    }
                    else -> {
                        Pair(404, JSONObject().put("error", "Not found").toString())
                    }
                }

                val response = "HTTP/1.1 $statusCode OK\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Content-Length: ${responseBody.toByteArray().size}\r\n" +
                        "\r\n" +
                        responseBody

                out.write(response.toByteArray())
                out.flush()
                socket.close()
            } catch (e: Exception) {
                Log.e("LanRelayServer", "Error handling LAN client: ${e.message}")
            }
        }
    }

    private fun processCommandBody(body: String): Boolean {
        return try {
            val json = JSONObject(body)
            val cmdStr = json.optString("commandType")
            val cmdType = CommandType.valueOf(cmdStr)
            val valueInt = json.optInt("valueInt", 0)
            val valueString = json.optString("valueString", "")
            val pairingCode = json.optString("pairingCode", "")

            val command = RemoteCommand(
                pairingCode = pairingCode,
                commandType = cmdType,
                valueInt = valueInt,
                valueString = valueString
            )
            relayManager.executeCommandLocally(command)
            true
        } catch (e: Exception) {
            Log.e("LanRelayServer", "Failed parsing command body: ${e.message}")
            false
        }
    }
}
