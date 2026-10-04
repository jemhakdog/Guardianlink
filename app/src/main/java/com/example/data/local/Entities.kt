package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val role: String, // PARENT or CHILD
    val pairingCode: String,
    val isConnected: Boolean,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val volumeMedia: Int,
    val volumeRing: Int,
    val volumeNotification: Int,
    val brightness: Int,
    val isLocked: Boolean,
    val lockMessage: String,
    val lastSeenTimestamp: Long,
    val timeLimitMinutes: Int = 60,
    val timeRemainingMinutes: Int = 45,
    val isTimeLimitEnabled: Boolean = true
)

@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandType: String,
    val description: String,
    val valueInt: Int,
    val valueString: String,
    val isSuccess: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val isEnabled: Boolean,
    val actionType: String // "LOCK" or "BEDTIME_DIM"
)
