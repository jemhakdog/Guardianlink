package com.example.data.model

enum class DeviceRole {
    PARENT,
    CHILD
}

enum class CommandType {
    SET_VOLUME_MEDIA,
    SET_VOLUME_RING,
    SET_VOLUME_NOTIFICATION,
    SET_VOLUME_ALL,
    SET_BRIGHTNESS,
    LOCK_SCREEN,
    UNLOCK_SCREEN,
    HEARTBEAT_PING,
    SEND_ALERT,
    UPDATE_RESTRICTION,
    SET_TIME_LIMIT,
    ADD_EXTRA_TIME,
    RESET_TIME,
    END_TIME_AND_LOCK,
    REQUEST_MORE_TIME
}

data class RemoteCommand(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pairingCode: String,
    val commandType: CommandType,
    val valueInt: Int = 0,
    val valueString: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val senderRole: DeviceRole = DeviceRole.PARENT
)

data class DeviceStatus(
    val deviceId: String = "child_dev_1",
    val deviceName: String = "Child Device",
    val modelName: String = "Android Device",
    val batteryLevel: Int = 84,
    val isCharging: Boolean = false,
    val volumeMedia: Int = 45, // percentage 0-100
    val volumeRing: Int = 60,
    val volumeNotification: Int = 50,
    val brightness: Int = 65, // percentage 0-100
    val isLocked: Boolean = false,
    val lockMessage: String = "Screen time paused by Parent",
    val lockRemainingMinutes: Int = 0,
    val isOnline: Boolean = true,
    val lastPingTimestamp: Long = System.currentTimeMillis(),
    val hasAudioPermission: Boolean = true,
    val hasWriteSettingsPermission: Boolean = false,
    val hasOverlayPermission: Boolean = true,

    // Time Limit Governance
    val timeLimitMinutes: Int = 60,
    val timeRemainingMinutes: Int = 45,
    val timeUsedMinutes: Int = 15,
    val isTimeLimitEnabled: Boolean = true,
    val isTimeExpired: Boolean = false,
    val pendingTimeRequestMinutes: Int = 0,
    val pendingTimeRequestReason: String = ""
)
