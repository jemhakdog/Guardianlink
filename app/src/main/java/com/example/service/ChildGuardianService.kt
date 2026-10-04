package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.relay.RelayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChildGuardianService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var relayManager: RelayManager

    companion object {
        const val CHANNEL_ID = "guardian_link_service_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            try {
                val intent = Intent(context, ChildGuardianService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("ChildGuardianService", "Cannot start foreground service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, ChildGuardianService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                android.util.Log.e("ChildGuardianService", "Cannot stop foreground service: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        relayManager = RelayManager.getInstance(this)
        createNotificationChannel()

        val role = relayManager.prefs.getCurrentRole()
        val notifText = if (role == com.example.data.model.DeviceRole.STANDALONE_LOCK) {
            "On-Device Screen Limit: ${relayManager.prefs.getTimeRemainingSeconds() / 60}m remaining today"
        } else {
            "GuardianLink active. Connected to parent controller."
        }

        val notification = createNotification(notifText)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("ChildGuardianService", "startForeground failed gracefully: ${e.message}")
        }

        // Listen for incoming commands
        serviceScope.launch {
            relayManager.incomingCommands.collectLatest { command ->
                relayManager.executeCommandLocally(command)
                updateNotification(
                    if (relayManager.deviceStatus.value.isLocked) {
                        "Device is currently paused by Parent"
                    } else {
                        "GuardianLink active. Device governed remotely."
                    }
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GuardianLink Child Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps remote governance connection active"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GuardianLink Protection Active")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(contentText))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
