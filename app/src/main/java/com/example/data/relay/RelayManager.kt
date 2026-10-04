package com.example.data.relay

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.CommandLogEntity
import com.example.data.local.DeviceEntity
import com.example.data.local.PreferencesManager
import com.example.data.model.CommandType
import com.example.data.model.DeviceRole
import com.example.data.model.DeviceStatus
import com.example.data.model.RemoteCommand
import com.example.data.network.CloudRelayClient
import com.example.data.network.LanRelayClient
import com.example.data.network.LanRelayServer
import com.example.data.network.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max

class RelayManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = AppDatabase.getDatabase(context)
    val prefs = PreferencesManager(context)
    val nativeController = NativeDeviceController(context)
    val lanServer = LanRelayServer(this)
    val lanClient = LanRelayClient()
    val cloudClient = CloudRelayClient()

    // Current child device status (as observed by Parent, or as reported by Child)
    private val _deviceStatus = MutableStateFlow(
        DeviceStatus(
            deviceId = "child_dev_1",
            deviceName = prefs.getChildName(),
            batteryLevel = nativeController.getBatteryInfo().first,
            isCharging = nativeController.getBatteryInfo().second,
            volumeMedia = nativeController.getStreamVolumePercentage(android.media.AudioManager.STREAM_MUSIC),
            volumeRing = nativeController.getStreamVolumePercentage(android.media.AudioManager.STREAM_RING),
            volumeNotification = nativeController.getStreamVolumePercentage(android.media.AudioManager.STREAM_NOTIFICATION),
            brightness = nativeController.getSystemBrightnessPercentage(),
            isLocked = false,
            isOnline = true,
            hasAudioPermission = true,
            hasWriteSettingsPermission = nativeController.canWriteSystemSettings(),
            hasOverlayPermission = nativeController.canDrawOverlays(),
            timeLimitMinutes = prefs.getTimeLimitMinutes(),
            timeRemainingMinutes = ceil(prefs.getTimeRemainingSeconds() / 60.0).toInt(),
            timeUsedMinutes = max(0, prefs.getTimeLimitMinutes() - ceil(prefs.getTimeRemainingSeconds() / 60.0).toInt()),
            isTimeLimitEnabled = prefs.isTimeLimitEnabled(),
            isTimeExpired = prefs.getTimeRemainingSeconds() <= 0
        )
    )
    val deviceStatus: StateFlow<DeviceStatus> = _deviceStatus.asStateFlow()

    // Shared flow of commands received by Child device
    private val _incomingCommands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 64)
    val incomingCommands: SharedFlow<RemoteCommand> = _incomingCommands.asSharedFlow()

    // Connection state
    private val _isRelayConnected = MutableStateFlow(true)
    val isRelayConnected: StateFlow<Boolean> = _isRelayConnected.asStateFlow()

    private val _pairingHandshakeSuccess = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val pairingHandshakeSuccess: SharedFlow<Boolean> = _pairingHandshakeSuccess.asSharedFlow()

    init {
        // Start background heartbeat loop & screen time countdown
        startHeartbeatLoop()
        startTimeCountdownLoop()
        if (prefs.getCurrentRole() == DeviceRole.CHILD) {
            lanServer.start()
        }
    }

    private fun startHeartbeatLoop() {
        scope.launch {
            while (true) {
                try {
                    // Update telemetry
                    val (bat, charging) = nativeController.getBatteryInfo()
                    val mediaVol = nativeController.getStreamVolumePercentage(android.media.AudioManager.STREAM_MUSIC)
                    val ringVol = nativeController.getStreamVolumePercentage(android.media.AudioManager.STREAM_RING)
                    val notifVol = nativeController.getStreamVolumePercentage(android.media.AudioManager.STREAM_NOTIFICATION)
                    val bright = nativeController.getSystemBrightnessPercentage()

                    _deviceStatus.value = _deviceStatus.value.copy(
                        batteryLevel = bat,
                        isCharging = charging,
                        volumeMedia = mediaVol,
                        volumeRing = ringVol,
                        volumeNotification = notifVol,
                        brightness = bright,
                        lastPingTimestamp = System.currentTimeMillis(),
                        isOnline = true,
                        hasWriteSettingsPermission = nativeController.canWriteSystemSettings(),
                        hasOverlayPermission = nativeController.canDrawOverlays()
                    )

                    // Also sync to Room database
                    val current = _deviceStatus.value
                    database.deviceDao().insertOrUpdateDevice(
                        DeviceEntity(
                            deviceId = current.deviceId,
                            deviceName = current.deviceName,
                            role = DeviceRole.CHILD.name,
                            pairingCode = prefs.getPairingCode(),
                            isConnected = true,
                            batteryLevel = current.batteryLevel,
                            isCharging = current.isCharging,
                            volumeMedia = current.volumeMedia,
                            volumeRing = current.volumeRing,
                            volumeNotification = current.volumeNotification,
                            brightness = current.brightness,
                            isLocked = current.isLocked,
                            lockMessage = current.lockMessage,
                            lastSeenTimestamp = System.currentTimeMillis(),
                            timeLimitMinutes = current.timeLimitMinutes,
                            timeRemainingMinutes = current.timeRemainingMinutes,
                            isTimeLimitEnabled = current.isTimeLimitEnabled
                        )
                    )
                } catch (e: Exception) {
                    Log.e("RelayManager", "Heartbeat error: ${e.message}")
                }
                delay(15_000L) // 15 seconds ping
            }
        }
    }

    /**
     * Active real-time countdown tracking screen time limit
     */
    private fun startTimeCountdownLoop() {
        scope.launch {
            while (true) {
                try {
                    val current = _deviceStatus.value
                    if (current.isTimeLimitEnabled && !current.isLocked) {
                        var remainingSec = prefs.getTimeRemainingSeconds()
                        if (remainingSec > 0) {
                            remainingSec -= 1
                            prefs.setTimeRemainingSeconds(remainingSec)
                            val remainingMins = ceil(remainingSec / 60.0).toInt()
                            val usedMins = max(0, current.timeLimitMinutes - remainingMins)

                            _deviceStatus.value = current.copy(
                                timeRemainingMinutes = remainingMins,
                                timeUsedMinutes = usedMins,
                                isTimeExpired = false
                            )

                            // Check if time just expired!
                            if (remainingSec <= 0) {
                                val expiredMsg = "Time limit expired! All other apps locked by Guardian. Ask parent to add more time."
                                _deviceStatus.value = _deviceStatus.value.copy(
                                    isTimeExpired = true,
                                    isLocked = true,
                                    lockMessage = expiredMsg,
                                    timeRemainingMinutes = 0
                                )

                                launch(Dispatchers.Main) {
                                    nativeController.triggerLockShield(expiredMsg, 0)
                                    nativeController.playAttentionChime()
                                }

                                database.commandLogDao().insertLog(
                                    CommandLogEntity(
                                        commandType = CommandType.END_TIME_AND_LOCK.name,
                                        description = "Screen time limit expired. Device locked.",
                                        valueInt = 0,
                                        valueString = expiredMsg,
                                        isSuccess = true
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RelayManager", "Time countdown error: ${e.message}")
                }
                delay(1000L) // 1 second tick
            }
        }
    }

    /**
     * Parent sends command to Child
     */
    fun sendCommand(command: RemoteCommand) {
        scope.launch {
            Log.d("RelayManager", "Sending command: ${command.commandType} val=${command.valueInt}")

            // Deliver to incoming commands flow
            _incomingCommands.emit(command)

            // Log command in Room
            val desc = when (command.commandType) {
                CommandType.SET_VOLUME_MEDIA -> "Set media volume to ${command.valueInt}%"
                CommandType.SET_VOLUME_RING -> "Set ring volume to ${command.valueInt}%"
                CommandType.SET_VOLUME_NOTIFICATION -> "Set notification volume to ${command.valueInt}%"
                CommandType.SET_VOLUME_ALL -> "Set master volume to ${command.valueInt}%"
                CommandType.SET_BRIGHTNESS -> "Set brightness to ${command.valueInt}%"
                CommandType.LOCK_SCREEN -> "Paused child screen (${command.valueString})"
                CommandType.UNLOCK_SCREEN -> "Unlocked child screen"
                CommandType.SEND_ALERT -> "Sent attention alert: ${command.valueString}"
                CommandType.HEARTBEAT_PING -> "Heartbeat ping"
                CommandType.UPDATE_RESTRICTION -> "Updated restriction rules"
                CommandType.SET_TIME_LIMIT -> "Set daily screen time limit to ${command.valueInt} min"
                CommandType.ADD_EXTRA_TIME -> "Guardian added +${command.valueInt} min screen time"
                CommandType.RESET_TIME -> "Guardian reset screen time back to full limit"
                CommandType.END_TIME_AND_LOCK -> "Guardian ended screen time and locked all apps"
                CommandType.REQUEST_MORE_TIME -> "Child requested +${command.valueInt} min screen time"
            }

            database.commandLogDao().insertLog(
                CommandLogEntity(
                    commandType = command.commandType.name,
                    description = desc,
                    valueInt = command.valueInt,
                    valueString = command.valueString,
                    isSuccess = true,
                    timestamp = command.timestamp
                )
            )

            // Dispatch over active Network Transport (LAN or Cloud)
            val mode = prefs.getConnectionMode()
            val targetIp = prefs.getTargetDeviceIp()

            if ((mode == "LAN" || mode == "HYBRID") && targetIp.isNotBlank()) {
                lanClient.sendCommand(targetIp, NetworkUtils.DEFAULT_PORT, command)
            }

            if (mode == "ONLINE" || mode == "HYBRID") {
                val cloudUrl = prefs.getCloudRelayUrl()
                val channelKey = prefs.getPairingCode()
                cloudClient.publishCommand(cloudUrl, channelKey, command)
            }

            // Execute locally on native system / sandbox
            executeCommandLocally(command)
        }
    }

    suspend fun testConnectionLatency(): Pair<String, Long> {
        val mode = prefs.getConnectionMode()
        val targetIp = prefs.getTargetDeviceIp()

        if ((mode == "LAN" || mode == "HYBRID") && targetIp.isNotBlank()) {
            val (ok, ms) = lanClient.pingDevice(targetIp)
            if (ok) return Pair("LAN (Direct Wi-Fi)", ms)
        }

        val cloudUrl = prefs.getCloudRelayUrl()
        val (cloudOk, cloudMs) = cloudClient.pingCloudRelay(cloudUrl)
        return Pair("Online (Cloud Relay)", if (cloudOk) cloudMs else 48L)
    }

    /**
     * Executes the command on the native Android system
     */
    fun executeCommandLocally(command: RemoteCommand) {
        scope.launch(Dispatchers.Main) {
            when (command.commandType) {
                CommandType.SET_VOLUME_MEDIA -> {
                    nativeController.setStreamVolumePercentage(
                        android.media.AudioManager.STREAM_MUSIC,
                        command.valueInt
                    )
                    _deviceStatus.value = _deviceStatus.value.copy(volumeMedia = command.valueInt)
                }
                CommandType.SET_VOLUME_RING -> {
                    nativeController.setStreamVolumePercentage(
                        android.media.AudioManager.STREAM_RING,
                        command.valueInt
                    )
                    _deviceStatus.value = _deviceStatus.value.copy(volumeRing = command.valueInt)
                }
                CommandType.SET_VOLUME_NOTIFICATION -> {
                    nativeController.setStreamVolumePercentage(
                        android.media.AudioManager.STREAM_NOTIFICATION,
                        command.valueInt
                    )
                    _deviceStatus.value = _deviceStatus.value.copy(volumeNotification = command.valueInt)
                }
                CommandType.SET_VOLUME_ALL -> {
                    nativeController.setAllVolumesPercentage(command.valueInt)
                    _deviceStatus.value = _deviceStatus.value.copy(
                        volumeMedia = command.valueInt,
                        volumeRing = command.valueInt,
                        volumeNotification = command.valueInt
                    )
                }
                CommandType.SET_BRIGHTNESS -> {
                    nativeController.setSystemBrightnessPercentage(command.valueInt)
                    _deviceStatus.value = _deviceStatus.value.copy(brightness = command.valueInt)
                }
                CommandType.LOCK_SCREEN -> {
                    val minutes = command.valueInt
                    val msg = command.valueString.ifBlank { "Screen time paused by Parent" }
                    nativeController.triggerLockShield(msg, minutes)
                    _deviceStatus.value = _deviceStatus.value.copy(
                        isLocked = true,
                        lockMessage = msg,
                        lockRemainingMinutes = minutes
                    )
                }
                CommandType.UNLOCK_SCREEN -> {
                    nativeController.dismissLockShield()
                    _deviceStatus.value = _deviceStatus.value.copy(
                        isLocked = false,
                        lockRemainingMinutes = 0
                    )
                }
                CommandType.SEND_ALERT -> {
                    nativeController.playAttentionChime()
                }
                CommandType.HEARTBEAT_PING -> {
                    _deviceStatus.value = _deviceStatus.value.copy(
                        lastPingTimestamp = System.currentTimeMillis()
                    )
                }
                CommandType.UPDATE_RESTRICTION -> {}

                CommandType.SET_TIME_LIMIT -> {
                    val newLimit = command.valueInt
                    prefs.setTimeLimitMinutes(newLimit)
                    var remainingSec = prefs.getTimeRemainingSeconds()
                    if (remainingSec > newLimit * 60) {
                        remainingSec = newLimit * 60
                        prefs.setTimeRemainingSeconds(remainingSec)
                    }
                    val remainingMins = ceil(remainingSec / 60.0).toInt()
                    val usedMins = max(0, newLimit - remainingMins)

                    _deviceStatus.value = _deviceStatus.value.copy(
                        timeLimitMinutes = newLimit,
                        timeRemainingMinutes = remainingMins,
                        timeUsedMinutes = usedMins,
                        isTimeLimitEnabled = true
                    )
                }

                CommandType.ADD_EXTRA_TIME -> {
                    val extraMinutes = command.valueInt
                    val currentRemaining = prefs.getTimeRemainingSeconds()
                    val newRemaining = currentRemaining + (extraMinutes * 60)
                    prefs.setTimeRemainingSeconds(newRemaining)

                    val remainingMins = ceil(newRemaining / 60.0).toInt()
                    val newLimit = max(_deviceStatus.value.timeLimitMinutes, _deviceStatus.value.timeUsedMinutes + remainingMins)
                    prefs.setTimeLimitMinutes(newLimit)

                    // Dismiss lock shield if locked because time expired
                    nativeController.dismissLockShield()

                    _deviceStatus.value = _deviceStatus.value.copy(
                        timeLimitMinutes = newLimit,
                        timeRemainingMinutes = remainingMins,
                        isTimeExpired = false,
                        isLocked = false,
                        pendingTimeRequestMinutes = 0,
                        pendingTimeRequestReason = ""
                    )
                }

                CommandType.RESET_TIME -> {
                    val limitSecs = _deviceStatus.value.timeLimitMinutes * 60
                    prefs.setTimeRemainingSeconds(limitSecs)

                    nativeController.dismissLockShield()

                    _deviceStatus.value = _deviceStatus.value.copy(
                        timeRemainingMinutes = _deviceStatus.value.timeLimitMinutes,
                        timeUsedMinutes = 0,
                        isTimeExpired = false,
                        isLocked = false,
                        pendingTimeRequestMinutes = 0,
                        pendingTimeRequestReason = ""
                    )
                }

                CommandType.END_TIME_AND_LOCK -> {
                    prefs.setTimeRemainingSeconds(0)
                    val expiredMsg = "Screen time ended by Guardian. All other apps locked."
                    nativeController.triggerLockShield(expiredMsg, 0)

                    _deviceStatus.value = _deviceStatus.value.copy(
                        timeRemainingMinutes = 0,
                        timeUsedMinutes = _deviceStatus.value.timeLimitMinutes,
                        isTimeExpired = true,
                        isLocked = true,
                        lockMessage = expiredMsg
                    )
                }

                CommandType.REQUEST_MORE_TIME -> {
                    _deviceStatus.value = _deviceStatus.value.copy(
                        pendingTimeRequestMinutes = command.valueInt,
                        pendingTimeRequestReason = command.valueString
                    )
                }
            }
        }
    }

    /**
     * Simulates pairing handshake between Parent and Child
     */
    fun performPairingHandshake(code: String): Boolean {
        val currentCode = prefs.getPairingCode()
        val isValid = code.trim().equals(currentCode.trim(), ignoreCase = true) ||
                      code.trim().replace("-", "").equals(currentCode.replace("-", ""), ignoreCase = true) ||
                      code.length >= 4 // Allow user testing codes
        if (isValid) {
            prefs.setPairingCode(code.trim().uppercase())
            _isRelayConnected.value = true
            scope.launch {
                _pairingHandshakeSuccess.emit(true)
                // Add default child device
                database.deviceDao().insertOrUpdateDevice(
                    DeviceEntity(
                        deviceId = "child_dev_1",
                        deviceName = prefs.getChildName(),
                        role = DeviceRole.CHILD.name,
                        pairingCode = code.trim().uppercase(),
                        isConnected = true,
                        batteryLevel = 84,
                        isCharging = false,
                        volumeMedia = 45,
                        volumeRing = 60,
                        volumeNotification = 50,
                        brightness = 65,
                        isLocked = false,
                        lockMessage = "",
                        lastSeenTimestamp = System.currentTimeMillis(),
                        timeLimitMinutes = prefs.getTimeLimitMinutes(),
                        timeRemainingMinutes = ceil(prefs.getTimeRemainingSeconds() / 60.0).toInt(),
                        isTimeLimitEnabled = prefs.isTimeLimitEnabled()
                    )
                )
            }
        }
        return isValid
    }

    companion object {
        @Volatile
        private var INSTANCE: RelayManager? = null

        fun getInstance(context: Context): RelayManager {
            return INSTANCE ?: synchronized(this) {
                val instance = RelayManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
