package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.CommandLogEntity
import com.example.data.local.DeviceEntity
import com.example.data.local.ScheduleEntity
import kotlinx.coroutines.flow.Flow

class GuardianRepository(private val database: AppDatabase) {

    val allDevices: Flow<List<DeviceEntity>> = database.deviceDao().getAllDevices()
    val recentLogs: Flow<List<CommandLogEntity>> = database.commandLogDao().getRecentLogs()
    val allSchedules: Flow<List<ScheduleEntity>> = database.scheduleDao().getAllSchedules()

    suspend fun saveDevice(device: DeviceEntity) {
        database.deviceDao().insertOrUpdateDevice(device)
    }

    suspend fun removeDevice(deviceId: String) {
        database.deviceDao().deleteDevice(deviceId)
    }

    suspend fun logCommand(
        commandType: String,
        description: String,
        valueInt: Int = 0,
        valueString: String = "",
        isSuccess: Boolean = true
    ) {
        database.commandLogDao().insertLog(
            CommandLogEntity(
                commandType = commandType,
                description = description,
                valueInt = valueInt,
                valueString = valueString,
                isSuccess = isSuccess
            )
        )
    }

    suspend fun clearAllLogs() {
        database.commandLogDao().clearLogs()
    }

    suspend fun addSchedule(schedule: ScheduleEntity) {
        database.scheduleDao().insertSchedule(schedule)
    }

    suspend fun updateSchedule(schedule: ScheduleEntity) {
        database.scheduleDao().updateSchedule(schedule)
    }

    suspend fun deleteSchedule(id: Long) {
        database.scheduleDao().deleteSchedule(id)
    }
}
