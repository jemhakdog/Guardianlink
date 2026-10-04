package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CommandLogEntity
import com.example.ui.components.BrightnessControlSection
import com.example.ui.components.DeviceStatusHeader
import com.example.ui.components.QuickActionsRow
import com.example.ui.components.ScreenLockSection
import com.example.ui.components.ScreenTimeLimitCard
import com.example.ui.components.VolumeControlSection
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.viewmodel.GuardianViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    viewModel: GuardianViewModel,
    onNavigatePairing: () -> Unit,
    onNavigateSchedules: () -> Unit,
    onNavigateSandbox: () -> Unit,
    onNavigateNetworkHub: () -> Unit,
    onSwitchRole: () -> Unit
) {
    val deviceStatus by viewModel.deviceStatus.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    val commandLogs by viewModel.commandLogs.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "GuardianLink",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Text(
                            "Parent Controller",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateNetworkHub,
                        modifier = Modifier.testTag("network_hub_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lan,
                            contentDescription = "Connection Hub"
                        )
                    }

                    IconButton(
                        onClick = onNavigatePairing,
                        modifier = Modifier.testTag("pairing_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Pairing Code"
                        )
                    }

                    IconButton(
                        onClick = onNavigateSandbox,
                        modifier = Modifier.testTag("sandbox_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Test Sandbox"
                        )
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Connection Hub (LAN / Online)") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateNetworkHub()
                                },
                                leadingIcon = { Icon(Icons.Default.Lan, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Pairing & QR Code") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigatePairing()
                                },
                                leadingIcon = { Icon(Icons.Default.QrCode, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Bedtime Schedules") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateSchedules()
                                },
                                leadingIcon = { Icon(Icons.Default.Bedtime, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Interactive Sandbox") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateSandbox()
                                },
                                leadingIcon = { Icon(Icons.Default.Tune, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Switch Role (Child/Parent)") },
                                onClick = {
                                    menuExpanded = false
                                    onSwitchRole()
                                },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Device Status Header Card
            item {
                DeviceStatusHeader(
                    status = deviceStatus,
                    pairingCode = pairingCode,
                    onPairingClick = onNavigatePairing
                )
            }

            // 2. Daily Screen Time Limit Card
            item {
                ScreenTimeLimitCard(
                    status = deviceStatus,
                    onAddExtraTime = { mins -> viewModel.addExtraTime(mins) },
                    onSetTimeLimit = { mins -> viewModel.setTimeLimit(mins) },
                    onResetTime = { viewModel.resetScreenTime() },
                    onEndTimeAndLock = { viewModel.endTimeAndLock() }
                )
            }

            // 3. Instant Screen Lock / Pause Card
            item {
                ScreenLockSection(
                    isLocked = deviceStatus.isLocked,
                    onLockClick = { minutes, msg ->
                        viewModel.lockScreen(minutes, msg)
                    },
                    onUnlockClick = {
                        viewModel.unlockScreen()
                    }
                )
            }

            // 3. Remote Volume Control Card
            item {
                VolumeControlSection(
                    mediaVol = deviceStatus.volumeMedia,
                    ringVol = deviceStatus.volumeRing,
                    notifVol = deviceStatus.volumeNotification,
                    onVolumeChange = { commandType, value ->
                        viewModel.setStreamVolume(commandType, value)
                    }
                )
            }

            // 4. Remote Brightness Control Card
            item {
                BrightnessControlSection(
                    brightness = deviceStatus.brightness,
                    onBrightnessChange = { newBrightness ->
                        viewModel.setBrightness(newBrightness)
                    }
                )
            }

            // 5. Quick Actions Row
            item {
                QuickActionsRow(
                    onSendChime = {
                        viewModel.sendAttentionChime("Parent requested attention")
                    },
                    onBedtimeRule = onNavigateSchedules
                )
            }

            // 6. Recent Activity Log Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Live Activity Stream",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    if (commandLogs.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearLogs() }) {
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (commandLogs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = "No recent actions yet. Slide volume or trigger a lock to see live updates.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(commandLogs.take(10)) { log ->
                    ActivityLogItem(log = log)
                }
            }
        }
    }
}

@Composable
fun ActivityLogItem(log: CommandLogEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(EmeraldSuccess.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.description,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                val timeStr = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(log.timestamp))
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
