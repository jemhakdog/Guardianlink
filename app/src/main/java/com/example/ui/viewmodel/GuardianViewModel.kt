package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.GuardianApplication
import com.example.data.local.CommandLogEntity
import com.example.data.local.ScheduleEntity
import com.example.data.model.CommandType
import com.example.data.model.DeviceRole
import com.example.data.model.DeviceStatus
import com.example.data.model.RemoteCommand
import com.example.data.repository.GuardianRepository
import com.example.service.ChildGuardianService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GuardianViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GuardianApplication
    private val relayManager = app.relayManager
    private val prefs = app.preferencesManager
    private val repository = GuardianRepository(app.database)

    // Current Role
    private val _currentRole = MutableStateFlow<DeviceRole?>(prefs.getCurrentRole())
    val currentRole: StateFlow<DeviceRole?> = _currentRole.asStateFlow()

    // Pairing Code
    private val _pairingCode = MutableStateFlow(prefs.getPairingCode())
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    // Connection Mode & Network settings
    private val _connectionMode = MutableStateFlow(prefs.getConnectionMode())
    val connectionMode: StateFlow<String> = _connectionMode.asStateFlow()

    private val _targetDeviceIp = MutableStateFlow(prefs.getTargetDeviceIp())
    val targetDeviceIp: StateFlow<String> = _targetDeviceIp.asStateFlow()

    fun getLocalIpAddress(): String = com.example.data.network.NetworkUtils.getLocalIpAddress()
    fun getNetworkTypeLabel(): String = com.example.data.network.NetworkUtils.getNetworkTypeLabel(getApplication())

    fun updateConnectionMode(mode: String) {
        prefs.setConnectionMode(mode)
        _connectionMode.value = mode
        postUiEvent("Connection mode set to $mode")
    }

    fun updateTargetDeviceIp(ip: String) {
        prefs.setTargetDeviceIp(ip.trim())
        _targetDeviceIp.value = ip.trim()
        postUiEvent("Child target IP updated: ${ip.trim()}")
    }

    suspend fun pingConnectionTest(): Pair<String, Long> {
        return relayManager.testConnectionLatency()
    }

    // Child Device Status observed in realtime
    val deviceStatus: StateFlow<DeviceStatus> = relayManager.deviceStatus

    // Recent command logs from Room
    val commandLogs: StateFlow<List<CommandLogEntity>> = repository.recentLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Schedules from Room
    val schedules: StateFlow<List<ScheduleEntity>> = repository.allSchedules
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // UI Toast / SnackBar event stream
    private val _uiEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    init {
        // If configured as Child, start the foreground service
        if (prefs.getCurrentRole() == DeviceRole.CHILD) {
            ChildGuardianService.start(getApplication())
        }
    }

    fun selectRole(role: DeviceRole) {
        prefs.setRole(role)
        _currentRole.value = role

        if (role == DeviceRole.CHILD) {
            ChildGuardianService.start(getApplication())
            postUiEvent("Child protection mode activated")
        } else {
            ChildGuardianService.stop(getApplication())
            postUiEvent("Parent control center activated")
        }
    }

    fun resetRole() {
        prefs.clearRole()
        _currentRole.value = null
        ChildGuardianService.stop(getApplication())
    }

    fun setMasterVolume(percentage: Int) {
        val command = RemoteCommand(
            pairingCode = _pairingCode.value,
            commandType = CommandType.SET_VOLUME_ALL,
            valueInt = percentage,
            valueString = "Master volume $percentage%"
        )
        relayManager.sendCommand(command)
        postUiEvent("Master volume set to $percentage%")
    }

    fun setStreamVolume(commandType: CommandType, percentage: Int) {
        val channelName = when (commandType) {
            CommandType.SET_VOLUME_MEDIA -> "Media"
            CommandType.SET_VOLUME_RING -> "Ring"
            CommandType.SET_VOLUME_NOTIFICATION -> "Notification"
            else -> "Volume"
        }
        val command = RemoteCommand(
            pairingCode = _pairingCode.value,
            commandType = commandType,
            valueInt = percentage,
            valueString = "$channelName volume $percentage%"
        )
        relayManager.sendCommand(command)
        postUiEvent("$channelName volume set to $percentage%")
    }

    fun setBrightness(percentage: Int) {
        val command = RemoteCommand(
            pairingCode = _pairingCode.value,
            commandType = CommandType.SET_BRIGHTNESS,
            valueInt = percentage,
            valueString = "Brightness $percentage%"
        )
        relayManager.sendCommand(command)
        postUiEvent("Brightness set to $percentage%")
    }

    fun lockScreen(minutes: Int = 0, message: String = "") {
        val effectiveMsg = message.ifBlank { "Screen time paused by Parent" }
        val command = RemoteCommand(
            pairingCode = _pairingCode.value,
            commandType = CommandType.LOCK_SCREEN,
            valueInt = minutes,
            valueString = effectiveMsg
        )
        relayManager.sendCommand(command)
        postUiEvent(if (minutes > 0) "Screen paused for $minutes min" else "Screen paused immediately")
    }

    fun unlockScreen() {
        val command = RemoteCommand(
            pairingCode = _pairingCode.value,
            commandType = CommandType.UNLOCK_SCREEN,
            valueInt = 0,
            valueString = "Screen unlocked"
        )
        relayManager.sendCommand(command)
        postUiEvent("Screen unlocked")
    }

    fun setTimeLimit(minutes: Int) {
        val command = RemoteCommand(
            pairingCode = _pairingCode.value,
            commandType = CommandType.SET_TIME_LIMIT,
            valueInt = minutes,
            valueString = "Daily limit $minutes min"
        )
        relayManager.sendCommand(command)
        postUiEvent("Daily time limit set to $minutes min")
    }

    fun addExtraTime(minutes: Int) {
        val command = RemoteCommand(
            pairingCode = _pairingCode.value,
            commandType = CommandType.ADD_EXTRA_TIME,
            valueInt = minutes,
            valueString = "+$minutes min"
        )
        relayManager.sendCommand(command)
        postUiEvent("Added +$minutes min to child screen time")
    }

    fun resetScreenTime() {
        val command = RemoteCommand(
            pairingCode = _pairingCode.value,
            commandType = CommandType.RESET_TIME,
            valueInt = 0,
            valueString = "Reset time"
        )
        relayManager.sendCommand(command)
        postUiEvent("Screen time reset to full limit")
    }

    fun endTimeAndLock() {
        val command = RemoteCommand(
            pairingCode = _pairingCode.value,
            commandType = CommandType.END_TIME_AND_LOCK,
            valueInt = 0,
            valueString = "End time & lock"
        )
        relayManager.sendCommand(command)
        postUiEvent("Screen time ended. Child device locked.")
    }

    fun requestMoreTime(minutes: Int, reason: String = "") {
        val command = RemoteCommand(
            pairingCode = _pairingCode.value,
            commandType = CommandType.REQUEST_MORE_TIME,
            valueInt = minutes,
            valueString = reason,
            senderRole = DeviceRole.CHILD
        )
        relayManager.sendCommand(command)
        postUiEvent("Sent request for +$minutes min to Guardian")
    }

    fun sendAttentionChime(message: String = "Attention requested by Parent") {
        val command = RemoteCommand(
            pairingCode = _pairingCode.value,
            commandType = CommandType.SEND_ALERT,
            valueInt = 0,
            valueString = message
        )
        relayManager.sendCommand(command)
        postUiEvent("Attention alert sent to child device")
    }

    fun pairWithCode(code: String): Boolean {
        val success = relayManager.performPairingHandshake(code)
        if (success) {
            _pairingCode.value = code.trim().uppercase()
            prefs.setPairingCode(code.trim().uppercase())
            postUiEvent("Device paired successfully!")
        } else {
            postUiEvent("Pairing failed. Check 6-digit code.")
        }
        return success
    }

    fun refreshPairingCode() {
        val randomDigits = (1000 + (Math.random() * 9000).toInt())
        val newCode = "GL-$randomDigits"
        prefs.setPairingCode(newCode)
        _pairingCode.value = newCode
        postUiEvent("New pairing code generated: $newCode")
    }

    fun updateChildName(name: String) {
        prefs.setChildName(name)
        viewModelScope.launch {
            repository.saveDevice(
                com.example.data.local.DeviceEntity(
                    deviceId = deviceStatus.value.deviceId,
                    deviceName = name,
                    role = DeviceRole.CHILD.name,
                    pairingCode = _pairingCode.value,
                    isConnected = true,
                    batteryLevel = deviceStatus.value.batteryLevel,
                    isCharging = deviceStatus.value.isCharging,
                    volumeMedia = deviceStatus.value.volumeMedia,
                    volumeRing = deviceStatus.value.volumeRing,
                    volumeNotification = deviceStatus.value.volumeNotification,
                    brightness = deviceStatus.value.brightness,
                    isLocked = deviceStatus.value.isLocked,
                    lockMessage = deviceStatus.value.lockMessage,
                    lastSeenTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun addSchedule(title: String, startH: Int, startM: Int, endH: Int, endM: Int, actionType: String) {
        viewModelScope.launch {
            repository.addSchedule(
                ScheduleEntity(
                    title = title,
                    startHour = startH,
                    startMinute = startM,
                    endHour = endH,
                    endMinute = endM,
                    isEnabled = true,
                    actionType = actionType
                )
            )
            postUiEvent("Schedule '$title' added")
        }
    }

    fun toggleSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            repository.updateSchedule(schedule.copy(isEnabled = !schedule.isEnabled))
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            repository.deleteSchedule(id)
            postUiEvent("Schedule removed")
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
            postUiEvent("Activity logs cleared")
        }
    }

    fun openWriteSettings() {
        relayManager.nativeController.openWriteSettingsPermissionScreen()
    }

    fun openOverlaySettings() {
        relayManager.nativeController.openOverlayPermissionScreen()
    }

    fun openNotificationPolicySettings() {
        relayManager.nativeController.openNotificationPolicyPermissionScreen()
    }

    private fun postUiEvent(message: String) {
        viewModelScope.launch {
            _uiEvents.emit(message)
        }
    }
}
