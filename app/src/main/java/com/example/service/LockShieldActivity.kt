package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PreferencesManager
import com.example.data.model.CommandType
import com.example.data.model.RemoteCommand
import com.example.data.relay.RelayManager
import com.example.ui.theme.GuardianTheme
import kotlinx.coroutines.delay

class LockShieldActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MESSAGE = "extra_lock_message"
        const val EXTRA_MINUTES = "extra_lock_minutes"
        const val ACTION_DISMISS_LOCK = "com.example.action.DISMISS_LOCK"
    }

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_DISMISS_LOCK) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val filter = IntentFilter(ACTION_DISMISS_LOCK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dismissReceiver, filter)
        }

        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Screen time limit reached or paused by Parent"
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        val prefs = PreferencesManager(this)
        val relayManager = RelayManager.getInstance(this)

        setContent {
            GuardianTheme(darkTheme = true) {
                LockShieldScreen(
                    message = message,
                    initialMinutes = minutes,
                    emergencyPhone = prefs.isEmergencyContactConfigured(),
                    parentPin = prefs.getParentPin(),
                    onRequestMoreTime = { requestedMins, reason ->
                        relayManager.sendCommand(
                            RemoteCommand(
                                pairingCode = prefs.getPairingCode(),
                                commandType = CommandType.REQUEST_MORE_TIME,
                                valueInt = requestedMins,
                                valueString = reason,
                                senderRole = com.example.data.model.DeviceRole.CHILD
                            )
                        )
                    },
                    onUnlockSuccess = { finish() },
                    onCallEmergency = { phone ->
                        try {
                            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            startActivity(callIntent)
                        } catch (_: Exception) {}
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(dismissReceiver)
        } catch (_: Exception) {}
    }
}

@Composable
fun LockShieldScreen(
    message: String,
    initialMinutes: Int,
    emergencyPhone: String,
    parentPin: String,
    onRequestMoreTime: (Int, String) -> Unit,
    onUnlockSuccess: () -> Unit,
    onCallEmergency: (String) -> Unit
) {
    // Intercept back button to prevent escaping the lock
    BackHandler(enabled = true) {}

    var remainingSeconds by remember { mutableIntStateOf(initialMinutes * 60) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    var showRequestDialog by remember { mutableStateOf(false) }
    var requestedMinutes by remember { mutableIntStateOf(15) }
    var requestSentMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(remainingSeconds) {
        if (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
            if (remainingSeconds == 0) {
                onUnlockSuccess()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B0F19),
                        Color(0xFF1E1B4B),
                        Color(0xFF0F172A)
                    )
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Shield Lock Icon
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(Color(0xFF38BDF8).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(Color(0xFF0284C7), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Shield",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Screen Time Expired",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = message,
                fontSize = 15.sp,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Notice Box explaining that other apps are locked & only Guardian can unlock
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HourglassBottom,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (initialMinutes > 0 && remainingSeconds > 0) {
                                val mins = remainingSeconds / 60
                                val secs = remainingSeconds % 60
                                String.format("%02d:%02d until unlock", mins, secs)
                            } else {
                                "Time Limit Reached • Apps Locked"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "All applications are temporarily locked. Only GuardianLink is accessible. Only your guardian can unlock the apps by adding more time.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Row 1: Request More Time & Emergency Call
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showRequestDialog = !showRequestDialog },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("request_more_time_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ask For Time", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                Button(
                    onClick = { onCallEmergency(emergencyPhone) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("emergency_call_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Call Guardian",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call Guardian", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Row 2: Guardian PIN Unlock
            OutlinedButton(
                onClick = { showPinDialog = !showPinDialog },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("guardian_pin_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0))
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardian In-Person PIN Unlock", fontSize = 13.sp)
            }

            // Request More Time Dialog Form
            AnimatedVisibility(visible = showRequestDialog) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Request Extra Screen Time",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(15 to "+15m", 30 to "+30m", 60 to "+1h").forEach { (mins, label) ->
                                FilledTonalButton(
                                    onClick = { requestedMinutes = mins },
                                    colors = if (requestedMinutes == mins) {
                                        ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black)
                                    } else {
                                        ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF334155), contentColor = Color.White)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(label, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                onRequestMoreTime(requestedMinutes, "Child requested +$requestedMinutes min")
                                requestSentMessage = "Request for +$requestedMinutes min sent to Guardian!"
                                showRequestDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Send Request to Parent")
                        }
                    }
                }
            }

            if (requestSentMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = requestSentMessage ?: "",
                    color = Color(0xFF4ADE80),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // PIN Unlock Form
            AnimatedVisibility(visible = showPinDialog) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Enter Guardian PIN (Default: 1234)",
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                if (it.length <= 6) {
                                    pinInput = it
                                    pinError = false
                                }
                            },
                            singleLine = true,
                            placeholder = { Text("PIN") },
                            modifier = Modifier.width(120.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF64748B)
                            )
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = {
                                if (pinInput == parentPin) {
                                    onUnlockSuccess()
                                } else {
                                    pinError = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Unlock")
                        }
                    }

                    if (pinError) {
                        Text(
                            text = "Incorrect PIN. Default is 1234.",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
