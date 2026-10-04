package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CommandType
import com.example.data.model.DeviceStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralLock
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DeviceStatusHeader(
    status: DeviceStatus,
    pairingCode: String,
    onPairingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_status_header"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Device",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = status.deviceName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(EmeraldSuccess, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Online • Code: $pairingCode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Battery Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onPairingClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (status.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            contentDescription = "Battery",
                            tint = if (status.batteryLevel < 20) CoralLock else EmeraldSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${status.batteryLevel}%",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Status Telemetry Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryPill(
                    title = "Screen",
                    value = if (status.isLocked) "Paused" else "Active",
                    isAlert = status.isLocked,
                    modifier = Modifier.weight(1f)
                )
                TelemetryPill(
                    title = "Volume",
                    value = "${status.volumeMedia}%",
                    isAlert = false,
                    modifier = Modifier.weight(1f)
                )
                TelemetryPill(
                    title = "Brightness",
                    value = "${status.brightness}%",
                    isAlert = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TelemetryPill(
    title: String,
    value: String,
    isAlert: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isAlert) CoralLock.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isAlert) CoralLock else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
fun ScreenLockSection(
    isLocked: Boolean,
    onLockClick: (minutes: Int, msg: String) -> Unit,
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMinutes by remember { mutableIntStateOf(0) } // 0 = indefinite/until unlocked
    var customMessage by remember { mutableStateOf("") }
    var showCustomMsgField by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("screen_lock_section"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) CoralLock.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isLocked) androidx.compose.foundation.BorderStroke(1.5.dp, CoralLock) else null
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (isLocked) CoralLock else ElectricBlue,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Instant Screen Pause",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isLocked) "Screen locked • Apps paused safely" else "Target apps preserved in background",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!isLocked) {
                // Duration presets
                Text(
                    text = "Pause Duration",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to "Manual",
                        15 to "15m",
                        30 to "30m",
                        60 to "1h"
                    ).forEach { (mins, label) ->
                        FilterChip(
                            selected = selectedMinutes == mins,
                            onClick = { selectedMinutes = mins },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedVisibility(visible = showCustomMsgField) {
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = { customMessage = it },
                        label = { Text("Display message on child screen") },
                        placeholder = { Text("e.g. Dinner is ready! Finish up.") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showCustomMsgField = !showCustomMsgField }
                    ) {
                        Text(if (showCustomMsgField) "Hide message note" else "+ Add lock note")
                    }

                    Button(
                        onClick = {
                            onLockClick(selectedMinutes, customMessage)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralLock),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("lock_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pause Screen Now")
                    }
                }
            } else {
                // Currently Locked View
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Child device is locked",
                            fontWeight = FontWeight.SemiBold,
                            color = CoralLock
                        )
                        Text(
                            text = "Open apps remain untouched in memory.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onUnlockClick,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("unlock_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unlock Device")
                    }
                }
            }
        }
    }
}

@Composable
fun VolumeControlSection(
    mediaVol: Int,
    ringVol: Int,
    notifVol: Int,
    onVolumeChange: (CommandType, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedChannels by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("volume_control_section"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(ElectricBlue.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (mediaVol == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Volume",
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Remote Volume Control",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Media: $mediaVol% • Ring: $ringVol% • Notif: $notifVol%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Media Volume Slider
            Text(
                text = "Media & Games ($mediaVol%)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Slider(
                value = mediaVol.toFloat(),
                onValueChange = { onVolumeChange(CommandType.SET_VOLUME_MEDIA, it.roundToInt()) },
                valueRange = 0f..100f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricBlue,
                    activeTrackColor = ElectricBlue
                ),
                modifier = Modifier.testTag("volume_media_slider")
            )

            // Preset Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VolumePresetChip("Mute All", 0) { onVolumeChange(CommandType.SET_VOLUME_ALL, 0) }
                VolumePresetChip("Quiet 20%", 20) { onVolumeChange(CommandType.SET_VOLUME_ALL, 20) }
                VolumePresetChip("Normal 50%", 50) { onVolumeChange(CommandType.SET_VOLUME_ALL, 50) }
                VolumePresetChip("Max 100%", 100) { onVolumeChange(CommandType.SET_VOLUME_ALL, 100) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { expandedChannels = !expandedChannels },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (expandedChannels) "Hide Ring & Notif" else "More Channels (Ring/Notif)")
            }

            AnimatedVisibility(visible = expandedChannels) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Ringtone Volume ($ringVol%)",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        value = ringVol.toFloat(),
                        onValueChange = { onVolumeChange(CommandType.SET_VOLUME_RING, it.roundToInt()) },
                        valueRange = 0f..100f,
                        steps = 19
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Notifications Volume ($notifVol%)",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        value = notifVol.toFloat(),
                        onValueChange = { onVolumeChange(CommandType.SET_VOLUME_NOTIFICATION, it.roundToInt()) },
                        valueRange = 0f..100f,
                        steps = 19
                    )
                }
            }
        }
    }
}

@Composable
fun VolumePresetChip(label: String, value: Int, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(label, fontSize = 11.sp)
    }
}

@Composable
fun BrightnessControlSection(
    brightness: Int,
    onBrightnessChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("brightness_control_section"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(AmberWarning.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = "Brightness",
                            tint = AmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Remote Brightness Control",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Level: $brightness% • Protect eyesight & bedtime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Slider(
                value = brightness.toFloat(),
                onValueChange = { onBrightnessChange(it.roundToInt()) },
                valueRange = 5f..100f,
                steps = 18,
                colors = SliderDefaults.colors(
                    thumbColor = AmberWarning,
                    activeTrackColor = AmberWarning
                ),
                modifier = Modifier.testTag("brightness_slider")
            )

            // Brightness Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VolumePresetChip("Bedtime 15%", 15) { onBrightnessChange(15) }
                VolumePresetChip("Cozy 35%", 35) { onBrightnessChange(35) }
                VolumePresetChip("Daylight 70%", 70) { onBrightnessChange(70) }
                VolumePresetChip("Max 100%", 100) { onBrightnessChange(100) }
            }
        }
    }
}

@Composable
fun QuickActionsRow(
    onSendChime: () -> Unit,
    onBedtimeRule: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ElevatedCard(
            onClick = onSendChime,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CyanAccent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Chime",
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Sound Alert", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Play child chime", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        ElevatedCard(
            onClick = onBedtimeRule,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF8B5CF6).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Bedtime",
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Schedules", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Bedtime locks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Beautiful simulated QR Code vector generator
 */
@Composable
fun QrCodeView(
    data: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 160
) {
    val hash = data.hashCode()
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((sizeDp - 24).dp)) {
            val cols = 15
            val cellSize = size.width / cols

            // Draw corners finder patterns
            fun drawFinder(cx: Int, cy: Int) {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(cx * cellSize, cy * cellSize),
                    size = Size(4 * cellSize, 4 * cellSize)
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset((cx + 1) * cellSize, (cy + 1) * cellSize),
                    size = Size(2 * cellSize, 2 * cellSize)
                )
                drawRect(
                    color = Color.Black,
                    topLeft = Offset((cx + 1.3f) * cellSize, (cy + 1.3f) * cellSize),
                    size = Size(1.4f * cellSize, 1.4f * cellSize)
                )
            }

            drawFinder(0, 0)
            drawFinder(cols - 4, 0)
            drawFinder(0, cols - 4)

            // Draw pseudo data cells based on data hash
            for (r in 0 until cols) {
                for (c in 0 until cols) {
                    // skip corners
                    val inTL = r < 5 && c < 5
                    val inTR = r < 5 && c >= cols - 5
                    val inBL = r >= cols - 5 && c < 5
                    if (!inTL && !inTR && !inBL) {
                        val bit = ((hash xor (r * 31 + c * 17)) and (1 shl ((r + c) % 8))) != 0
                        if (bit) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(c * cellSize, r * cellSize),
                                size = Size(cellSize, cellSize)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenTimeLimitCard(
    status: DeviceStatus,
    onAddExtraTime: (Int) -> Unit,
    onSetTimeLimit: (Int) -> Unit,
    onResetTime: () -> Unit,
    onEndTimeAndLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    var customMinutesInput by remember { mutableStateOf("") }

    val remaining = status.timeRemainingMinutes
    val total = status.timeLimitMinutes.coerceAtLeast(1)
    val used = status.timeUsedMinutes
    val progressFraction = (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    val progressColor = when {
        status.isTimeExpired || remaining == 0 -> CoralLock
        remaining <= 15 -> AmberWarning
        else -> ElectricBlue
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("screen_time_limit_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (status.isTimeExpired || (status.isLocked && remaining == 0)) {
                CoralLock.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (status.isTimeExpired) androidx.compose.foundation.BorderStroke(1.5.dp, CoralLock) else null
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (status.isTimeExpired) CoralLock else ElectricBlue,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Screen Time",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Daily Screen Time Limit",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (status.isTimeExpired) "Time expired • Apps locked" else "Governed active device countdown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (status.isTimeExpired) CoralLock.copy(alpha = 0.15f) else EmeraldSuccess.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (status.isTimeExpired) "Locked" else "Active",
                        color = if (status.isTimeExpired) CoralLock else EmeraldSuccess,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time Remaining & Progress Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = if (status.isTimeExpired || remaining == 0) "0m remaining" else "${remaining}m remaining",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = progressColor
                    )
                    Text(
                        text = "${used}m used of ${total}m daily limit",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (status.isTimeExpired) {
                    Text(
                        text = "Apps Inaccessible",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = CoralLock
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(progressColor)
                )
            }

            // Pending Child Time Request Banner (if any)
            if (status.pendingTimeRequestMinutes > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Child requested +${status.pendingTimeRequestMinutes} min time",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (status.pendingTimeRequestReason.isNotBlank()) {
                                Text(
                                    text = "\"${status.pendingTimeRequestReason}\"",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = { onAddExtraTime(status.pendingTimeRequestMinutes) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Grant +${status.pendingTimeRequestMinutes}m", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Actions: Add More Time
            Text(
                text = "Add Screen Time (Unlocks Apps)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15 to "+15m", 30 to "+30m", 60 to "+1h").forEach { (mins, label) ->
                    FilledTonalButton(
                        onClick = { onAddExtraTime(mins) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("add_time_${mins}_button")
                    ) {
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                OutlinedButton(
                    onClick = { showCustomDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text("+Custom", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Adjust Daily Limit Row
            Text(
                text = "Set Daily Target Limit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(30 to "30m", 60 to "1h", 90 to "1.5h", 120 to "2h", 180 to "3h").forEach { (mins, label) ->
                    FilterChip(
                        selected = status.timeLimitMinutes == mins,
                        onClick = { onSetTimeLimit(mins) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // End Time / Lock & Reset Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Reset time to full limit
                OutlinedButton(
                    onClick = onResetTime,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("reset_time_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Reset Daily Time", fontSize = 12.sp)
                }

                // End time now & lock immediately
                Button(
                    onClick = onEndTimeAndLock,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("end_time_lock_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralLock),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("End & Lock Now", fontSize = 12.sp)
                }
            }
        }
    }

    if (showCustomDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Add Custom Screen Time") },
            text = {
                Column {
                    Text("Enter extra minutes to grant to the child device:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customMinutesInput,
                        onValueChange = { customMinutesInput = it.filter { char -> char.isDigit() } },
                        label = { Text("Minutes (e.g. 45)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = customMinutesInput.toIntOrNull() ?: 15
                        if (mins > 0) {
                            onAddExtraTime(mins)
                        }
                        showCustomDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Add Minutes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("Cancel") }
            }
        )
    }
}
