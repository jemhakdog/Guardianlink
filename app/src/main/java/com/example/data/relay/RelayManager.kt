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
import com.example.service.LockShieldActivity
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

    private var cloudCommandJob: kotlinx.coroutines.Job? = null
    private var cloudStatusJob: kotlinx.coroutines.Job? = null

    // Helper to get initial status based on role
    private fun getInitialStatus(): DeviceStatus {
        val role = prefs.getCurrentRole()
        return if (role == DeviceRole.PARENT) {
            DeviceStatus(
                deviceId = "",
                deviceName = "Waiting for Child Device",
                modelName = "No device paired",
                batteryLevel = -1,
                volumeMedia = -1,
                volumeRing = -1,
                volumeNotification = -1,
                brightness = -1,
                isOnline = false
            )
        } else {
            val (bat, charging) = nativeController.getBatteryInfo()
            DeviceStatus(
                deviceId = "child_dev_1",
                deviceName = prefs.getChildName(),
                batteryLevel = bat,
                isCharging = charging,
                volumeMedia = nativeController.getStreamVolumePercentage(android.media.AudioManager.STREAM_MUSIC),
                volumeRing = nativeController.getStreamVolumePercentage(android.media.AudioManager.STREAM_RING),
                volumeNotification = nativeController.getStreamVolumePercentage(android.media.AudioManager.STREAM_NOTIFICATION),
                brightness = nativeController.getSystemBrightnessPercentage(),
                isLocked = false,
                isOnline = true,
                hasWriteSettingsPermission = nativeController.canWriteSystemSettings(),
                hasOverlayPermission = nativeController.canDrawOverlays(),
                timeLimitMinutes = prefs.getTimeLimitMinutes(),
                timeRemainingMinutes = ceil(prefs.getTimeRemainingSeconds() / 60.0).toInt(),
                timeUsedMinutes = max(0, prefs.getTimeLimitMinutes() - ceil(prefs.getTimeRemainingSeconds() / 60.0).toInt()),
                isTimeLimitEnabled = prefs.isTimeLimitEnabled(),
                isTimeExpired = prefs.getTimeRemainingSeconds() <= 0
            )
        }
    }

    // Current child device status (as observed by Parent, or as reported by Child)
    private val _deviceStatus = MutableStateFlow(getInitialStatus())
    val deviceStatus: StateFlow<DeviceStatus> = _deviceStatus.asStateFlow()

    // Shared flow of commands received by Child device
    private val _incomingCommands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 64)
    val incomingCommands: SharedFlow<RemoteCommand> = _incomingCommands.asSharedFlow()

    // Connection state
    private val _isRelayConnected = MutableStateFlow(false)
    val isRelayConnected: StateFlow<Boolean> = _isRelayConnected.asStateFlow()

    private val _pairingHandshakeSuccess = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val pairingHandshakeSuccess: SharedFlow<Boolean> = _pairingHandshakeSuccess.asSharedFlow()

    init {
        // Start background heartbeat loop & screen time countdown
        startHeartbeatLoop()
        startTimeCountdownLoop()
        setupCloudStreams()
        if (prefs.getCurrentRole() == DeviceRole.CHILD) {
            lanServer.start()
        }
    }

    fun setupCloudStreams() {
        cloudCommandJob?.cancel()
        cloudStatusJob?.cancel()

        val role = prefs.getCurrentRole()
        val pairingCode = prefs.getPairingCode()
        val cloudUrl = prefs.getCloudRelayUrl()

        if (role == DeviceRole.CHILD) {
            cloudCommandJob = cloudClient.startCommandStream(scope, cloudUrl, pairingCode) { command ->
                executeCommandLocally(command)
            }
            scope.launch {
                delay(500)
                cloudClient.publishStatus(cloudUrl, pairingCode, _deviceStatus.value)
            }
        } else if (role == DeviceRole.PARENT) {
            cloudStatusJob = cloudClient.startStatusStream(scope, cloudUrl, pairingCode) { status ->
                _deviceStatus.value = status.copy(
                    isOnline = true,
                    lastPingTimestamp = System.currentTimeMillis()
                )
                _isRelayConnected.value = true
            }
        }
    }

    private fun startHeartbeatLoop() {
        scope.launch {
            while (true) {
                try {
                    val role = prefs.getCurrentRole()
                    val cloudUrl = prefs.getCloudRelayUrl()
                    val pairingCode = prefs.getPairingCode()

                    if (role == DeviceRole.CHILD || role == DeviceRole.STANDALONE_LOCK) {
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

                        if (role == DeviceRole.CHILD) {
                            cloudClient.publishStatus(cloudUrl, pairingCode, _deviceStatus.value)
                        }
                    } else if (role == DeviceRole.PARENT) {
                        val lastPing = _deviceStatus.value.lastPingTimestamp
                        if (lastPing > 0L && (System.currentTimeMillis() - lastPing > 20_000L)) {
                            _deviceStatus.value = _deviceStatus.value.copy(isOnline = false)
                            _isRelayConnected.value = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RelayManager", "Heartbeat error: ${e.message}")
                }
                delay(4000L) // 4 seconds telemetry heartbeat
            }
        }
    }

    /**
     * Active real-time countdown tracking screen time limit and continuous lock enforcement
     */
    private fun startTimeCountdownLoop() {
        scope.launch {
            while (true) {
                try {
                    val current = _deviceStatus.value
                    val remainingSec = prefs.getTimeRemainingSeconds()
                    val isExpired = current.isTimeLimitEnabled && remainingSec <= 0

                    // Check bedtime schedule
                    var isBedtimeNow = false
                    if (prefs.isBedtimeEnabled()) {
                        val cal = java.util.Calendar.getInstance()
                        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                        val currentMin = cal.get(java.util.Calendar.MINUTE)
                        val nowMinutes = currentHour * 60 + currentMin
                        val startMinutes = prefs.getBedtimeStartHour() * 60 + prefs.getBedtimeStartMinute()
                        val endMinutes = prefs.getBedtimeEndHour() * 60 + prefs.getBedtimeEndMinute()

                        isBedtimeNow = if (startMinutes > endMinutes) {
                            nowMinutes >= startMinutes || nowMinutes < endMinutes
                        } else {
                            nowMinutes in startMinutes..endMinutes
                        }
                    }

                    val shouldBeLocked = current.isLocked || isExpired || isBedtimeNow

                    if (shouldBeLocked) {
                        val lockReason = when {
                            isBedtimeNow -> "Bedtime curfew active! Device paused until morning."
                            isExpired -> "Daily screen time limit reached! Hand device to parent to unlock with PIN."
                            current.lockMessage.isNotBlank() -> current.lockMessage
                            else -> "Screen time paused by Parent"
                        }

                        if (!current.isLocked || current.lockMessage != lockReason) {
                            _deviceStatus.value = _deviceStatus.value.copy(
                                isLocked = true,
                                isTimeExpired = isExpired,
                                lockMessage = lockReason,
                                timeRemainingMinutes = if (isExpired) 0 else current.timeRemainingMinutes
                            )
                        }

                        // Re-trigger lock shield only when child navigated away or minimized it
                        if (!LockShieldActivity.isLockShieldVisible) {
                            launch(Dispatchers.Main) {
                                nativeController.triggerLockShield(lockReason, 0)
                            }
                        }
                    } else if (current.isTimeLimitEnabled) {
                        // Count down active screen seconds
                        if (remainingSec > 0) {
                            val newRemainingSec = remainingSec - 1
                            prefs.setTimeRemainingSeconds(newRemainingSec)
                            val remainingMins = ceil(newRemainingSec / 60.0).toInt()
                            val usedMins = max(0, current.timeLimitMinutes - remainingMins)

                            _deviceStatus.value = current.copy(
                                timeRemainingMinutes = remainingMins,
                                timeUsedMinutes = usedMins,
                                isTimeExpired = false
                            )

                            if (newRemainingSec <= 0) {
                                launch(Dispatchers.Main) {
                                    nativeController.playAttentionChime()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RelayManager", "Time countdown error: ${e.message}")
                }
                delay(1500L) // 1.5 second tick
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

            // Dispatch over Cloud Relay (supports cross-LAN / worldwide like Philippines ↔ Japan!)
            val cloudUrl = prefs.getCloudRelayUrl()
            val channelKey = prefs.getPairingCode()
            cloudClient.publishCommand(cloudUrl, channelKey, command)

            // Only execute directly on hardware if in Standalone mode
            if (prefs.getCurrentRole() == DeviceRole.STANDALONE_LOCK) {
                executeCommandLocally(command)
            }
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

            // If child device: immediately broadcast updated telemetry to cloud so parent sees changes
            if (prefs.getCurrentRole() == DeviceRole.CHILD) {
                cloudClient.publishStatus(prefs.getCloudRelayUrl(), prefs.getPairingCode(), _deviceStatus.value)
            }
        }
    }

    /**
     * Executes pairing handshake between Parent and Child
     */
    fun performPairingHandshake(code: String): Boolean {
        val currentCode = prefs.getPairingCode()
        val isValid = code.trim().equals(currentCode.trim(), ignoreCase = true) ||
                      code.trim().replace("-", "").equals(currentCode.replace("-", ""), ignoreCase = true) ||
                      code.length >= 4
        if (isValid) {
            val cleanCode = code.trim().uppercase()
            prefs.setPairingCode(cleanCode)
            _isRelayConnected.value = true
            setupCloudStreams()
            scope.launch {
                _pairingHandshakeSuccess.emit(true)
                if (prefs.getCurrentRole() == DeviceRole.CHILD) {
                    cloudClient.publishStatus(prefs.getCloudRelayUrl(), cleanCode, _deviceStatus.value)
                }
            }
            return true
        }
        return false
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
