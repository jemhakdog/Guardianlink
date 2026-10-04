package com.example.data.relay

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.service.ChildGuardianService
import com.example.service.LockShieldActivity
import kotlin.math.roundToInt

class NativeDeviceController(private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)

    fun getBatteryInfo(): Pair<Int, Boolean> {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, filter)
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 80
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val batteryPct = if (scale > 0 && level >= 0) (level * 100 / scale.toFloat()).toInt() else 80
        return Pair(batteryPct, isCharging)
    }

    fun getStreamVolumePercentage(streamType: Int): Int {
        return try {
            val current = audioManager.getStreamVolume(streamType)
            val max = audioManager.getStreamMaxVolume(streamType)
            if (max > 0) ((current.toFloat() / max) * 100).roundToInt() else 0
        } catch (e: Exception) {
            50
        }
    }

    fun setStreamVolumePercentage(streamType: Int, percentage: Int) {
        try {
            val max = audioManager.getStreamMaxVolume(streamType)
            val clamped = percentage.coerceIn(0, 100)
            val target = ((clamped / 100f) * max).roundToInt().coerceIn(0, max)
            audioManager.setStreamVolume(streamType, target, 0)
        } catch (e: Exception) {
            Log.e("NativeDeviceController", "Error setting stream volume: ${e.message}")
        }
    }

    fun setAllVolumesPercentage(percentage: Int) {
        setStreamVolumePercentage(AudioManager.STREAM_MUSIC, percentage)
        setStreamVolumePercentage(AudioManager.STREAM_RING, percentage)
        setStreamVolumePercentage(AudioManager.STREAM_NOTIFICATION, percentage)
    }

    fun canWriteSystemSettings(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun openWriteSettingsPermissionScreen() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun openOverlayPermissionScreen() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun canAccessNotificationPolicy(): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.isNotificationPolicyAccessGranted
    }

    fun openNotificationPolicyPermissionScreen() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun getSystemBrightnessPercentage(): Int {
        return try {
            val brightnessVal = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            ((brightnessVal / 255f) * 100).roundToInt()
        } catch (e: Exception) {
            50
        }
    }

    fun setSystemBrightnessPercentage(percentage: Int): Boolean {
        val clamped = percentage.coerceIn(5, 100)
        val value255 = ((clamped / 100f) * 255).roundToInt().coerceIn(5, 255)
        return if (canWriteSystemSettings()) {
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    value255
                )
                true
            } catch (e: Exception) {
                Log.e("NativeDeviceController", "Failed writing brightness: ${e.message}")
                false
            }
        } else {
            false
        }
    }

    fun triggerLockShield(message: String, remainingMinutes: Int = 0) {
        val intent = Intent(context, LockShieldActivity::class.java).apply {
            putExtra(LockShieldActivity.EXTRA_MESSAGE, message)
            putExtra(LockShieldActivity.EXTRA_MINUTES, remainingMinutes)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }

        // Post High-Priority Full Screen Intent to ensure Android displays over background apps
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val pendingIntent = PendingIntent.getActivity(
                context,
                9999,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val fullScreenNotif = NotificationCompat.Builder(context, ChildGuardianService.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("GuardianLink Screen Lock")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(true)
                .build()

            notificationManager.notify(9999, fullScreenNotif)
        } catch (e: Exception) {
            Log.e("NativeDeviceController", "Full screen notification failed: ${e.message}")
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("NativeDeviceController", "Direct activity start failed: ${e.message}")
        }
    }

    fun dismissLockShield() {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(9999)
        } catch (_: Exception) {}

        val intent = Intent(LockShieldActivity.ACTION_DISMISS_LOCK).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    fun playAttentionChime() {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.play()

            // Also short haptic buzz
            vibrate(500)
        } catch (e: Exception) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
            } catch (_: Exception) {}
        }
    }

    fun vibrate(durationMs: Long = 400) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.e("NativeDeviceController", "Vibration failed: ${e.message}")
        }
    }
}
