package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.DeviceRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)

    private val _currentRoleFlow = MutableStateFlow(getCurrentRole())
    val currentRoleFlow: StateFlow<DeviceRole?> = _currentRoleFlow.asStateFlow()

    fun getCurrentRole(): DeviceRole? {
        val roleStr = prefs.getString("user_role", null) ?: return null
        return try {
            DeviceRole.valueOf(roleStr)
        } catch (e: Exception) {
            null
        }
    }

    fun setRole(role: DeviceRole) {
        prefs.edit().putString("user_role", role.name).apply()
        _currentRoleFlow.value = role
    }

    fun clearRole() {
        prefs.edit().remove("user_role").apply()
        _currentRoleFlow.value = null
    }

    fun getPairingCode(): String {
        var code = prefs.getString("pairing_code", null)
        if (code == null) {
            // Generate a memorable 6-character code e.g. "GL-7492"
            val randomDigits = (1000 + (Math.random() * 9000).toInt())
            code = "GL-$randomDigits"
            prefs.edit().putString("pairing_code", code).apply()
        }
        return code
    }

    fun setPairingCode(code: String) {
        prefs.edit().putString("pairing_code", code).apply()
    }

    fun getChildName(): String {
        return prefs.getString("child_name", "Leo's Phone") ?: "Leo's Phone"
    }

    fun setChildName(name: String) {
        prefs.edit().putString("child_name", name).apply()
    }

    fun getParentPin(): String {
        return prefs.getString("parent_pin", "1234") ?: "1234"
    }

    fun setParentPin(pin: String) {
        prefs.edit().putString("parent_pin", pin).apply()
    }

    fun isEmergencyContactConfigured(): String {
        return prefs.getString("emergency_contact", "+1 (555) 019-2834") ?: "+1 (555) 019-2834"
    }

    fun setEmergencyContact(phone: String) {
        prefs.edit().putString("emergency_contact", phone).apply()
    }

    fun getTimeLimitMinutes(): Int {
        return prefs.getInt("time_limit_mins", 120) // Default 2 hours limit
    }

    fun setTimeLimitMinutes(mins: Int) {
        prefs.edit().putInt("time_limit_mins", mins).apply()
    }

    fun getTimeRemainingSeconds(): Int {
        return prefs.getInt("time_remaining_sec", 120 * 60)
    }

    fun setTimeRemainingSeconds(secs: Int) {
        prefs.edit().putInt("time_remaining_sec", secs).apply()
    }

    fun isTimeLimitEnabled(): Boolean {
        return prefs.getBoolean("time_limit_enabled", true)
    }

    fun setTimeLimitEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("time_limit_enabled", enabled).apply()
    }

    fun isBedtimeEnabled(): Boolean = prefs.getBoolean("bedtime_enabled", false)
    fun setBedtimeEnabled(enabled: Boolean) = prefs.edit().putBoolean("bedtime_enabled", enabled).apply()

    fun getBedtimeStartHour(): Int = prefs.getInt("bedtime_start_hour", 20)
    fun setBedtimeStartHour(h: Int) = prefs.edit().putInt("bedtime_start_hour", h).apply()

    fun getBedtimeStartMinute(): Int = prefs.getInt("bedtime_start_min", 30)
    fun setBedtimeStartMinute(m: Int) = prefs.edit().putInt("bedtime_start_min", m).apply()

    fun getBedtimeEndHour(): Int = prefs.getInt("bedtime_end_hour", 7)
    fun setBedtimeEndHour(h: Int) = prefs.edit().putInt("bedtime_end_hour", h).apply()

    fun getBedtimeEndMinute(): Int = prefs.getInt("bedtime_end_min", 0)
    fun setBedtimeEndMinute(m: Int) = prefs.edit().putInt("bedtime_end_min", m).apply()

    fun getConnectionMode(): String {
        return prefs.getString("connection_mode", "HYBRID") ?: "HYBRID"
    }

    fun setConnectionMode(mode: String) {
        prefs.edit().putString("connection_mode", mode).apply()
    }

    fun getTargetDeviceIp(): String {
        return prefs.getString("target_device_ip", "") ?: ""
    }

    fun setTargetDeviceIp(ip: String) {
        prefs.edit().putString("target_device_ip", ip).apply()
    }

    fun getCloudRelayUrl(): String {
        val stored = prefs.getString("cloud_relay_url", "https://ntfy.sh") ?: "https://ntfy.sh"
        return if (stored.contains("guardianlink.app")) "https://ntfy.sh" else stored
    }

    fun setCloudRelayUrl(url: String) {
        prefs.edit().putString("cloud_relay_url", url).apply()
    }
}
