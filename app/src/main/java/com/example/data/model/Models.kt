package com.example.data.model

enum class DeviceRole {
    PARENT,
    CHILD,
    STANDALONE_LOCK
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
    val deviceId: String = "",
    val deviceName: String = "Waiting for Child Device",
    val modelName: String = "No device paired",
    val batteryLevel: Int = -1, // -1 means no telemetry yet
    val isCharging: Boolean = false,
    val volumeMedia: Int = -1, // -1 means unmeasured
    val volumeRing: Int = -1,
    val volumeNotification: Int = -1,
    val brightness: Int = -1, // -1 means unmeasured
    val isLocked: Boolean = false,
    val lockMessage: String = "Screen time paused by Parent",
    val lockRemainingMinutes: Int = 0,
    val isOnline: Boolean = false,
    val lastPingTimestamp: Long = 0L,
    val hasAudioPermission: Boolean = true,
    val hasWriteSettingsPermission: Boolean = false,
    val hasOverlayPermission: Boolean = true,

    // Time Limit Governance
    val timeLimitMinutes: Int = 120,
    val timeRemainingMinutes: Int = 0,
    val timeUsedMinutes: Int = 0,
    val isTimeLimitEnabled: Boolean = true,
    val isTimeExpired: Boolean = false,
    val pendingTimeRequestMinutes: Int = 0,
    val pendingTimeRequestReason: String = ""
) {
    val hasRealTelemetry: Boolean get() = isOnline && batteryLevel >= 0
}
