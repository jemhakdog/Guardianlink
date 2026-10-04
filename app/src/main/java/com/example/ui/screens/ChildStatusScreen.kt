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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralLock
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.viewmodel.GuardianViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildStatusScreen(
    viewModel: GuardianViewModel,
    onNavigatePairing: () -> Unit,
    onNavigateSandbox: () -> Unit,
    onNavigateNetworkHub: () -> Unit,
    onSwitchRole: () -> Unit
) {
    val deviceStatus by viewModel.deviceStatus.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GuardianLink", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("Child Device (Target)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateNetworkHub,
                        modifier = Modifier.testTag("child_network_hub_button")
                    ) {
                        Icon(Icons.Default.Lan, contentDescription = "Connection Hub")
                    }

                    IconButton(
                        onClick = onNavigatePairing,
                        modifier = Modifier.testTag("child_pair_button")
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = "Pairing")
                    }

                    IconButton(
                        onClick = onNavigateSandbox,
                        modifier = Modifier.testTag("child_sandbox_button")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Sandbox")
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
                                text = { Text("Pairing Token & Scanner") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigatePairing()
                                },
                                leadingIcon = { Icon(Icons.Default.QrCode, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Test Sandbox (Split View)") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateSandbox()
                                },
                                leadingIcon = { Icon(Icons.Default.Tune, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Switch to Parent Controller") },
                                onClick = {
                                    menuExpanded = false
                                    onSwitchRole()
                                },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            // Protection Active Hero Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(CyanAccent, ElectricBlue)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Protected",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Device Protection Active",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )

                        Text(
                            text = "Governed remotely by Parent • Token: $pairingCode",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = EmeraldSuccess.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(EmeraldSuccess, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Service Connected",
                                        color = EmeraldSuccess,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Screen Time Allowance Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (deviceStatus.isTimeExpired) CoralLock.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                    ),
                    border = if (deviceStatus.isTimeExpired) androidx.compose.foundation.BorderStroke(1.5.dp, CoralLock) else null
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.HourglassBottom,
                                    contentDescription = null,
                                    tint = if (deviceStatus.isTimeExpired) CoralLock else ElectricBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Daily Screen Time",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }

                            Text(
                                text = if (deviceStatus.isTimeExpired) "Time's Up!" else "${deviceStatus.timeRemainingMinutes}m left",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = if (deviceStatus.isTimeExpired) CoralLock else ElectricBlue
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val frac = (deviceStatus.timeRemainingMinutes.toFloat() / deviceStatus.timeLimitMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(frac)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (deviceStatus.isTimeExpired) CoralLock else ElectricBlue)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (deviceStatus.isTimeExpired) {
                                "All other apps are locked. GuardianLink remains open. Only your guardian can unlock the apps by adding time."
                            } else {
                                "When your time limit runs out, other apps are locked. You can ask your guardian for more time."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.requestMoreTime(15, "Need 15 min more") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Ask +15m", fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                onClick = { viewModel.requestMoreTime(30, "Need 30 min more") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Ask +30m", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Current Telemetry
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Current Governed Settings",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        SettingStatusRow(
                            label = "Media Volume",
                            value = "${deviceStatus.volumeMedia}%",
                            icon = Icons.Default.VolumeUp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingStatusRow(
                            label = "Screen Brightness",
                            value = "${deviceStatus.brightness}%",
                            icon = Icons.Default.SettingsBrightness
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingStatusRow(
                            label = "Screen State",
                            value = if (deviceStatus.isLocked) "Paused" else "Active (Normal)",
                            icon = Icons.Default.Security,
                            isAlert = deviceStatus.isLocked
                        )
                    }
                }
            }

            // Permissions Rationale Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Required System Permissions",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "These system privileges allow the parent to regulate screen and volume remotely.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Audio Permission
                        PermissionItem(
                            title = "Modify Audio Settings",
                            description = "Required to sync media and ring volumes remotely.",
                            isGranted = deviceStatus.hasAudioPermission,
                            onGrantClick = null
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Write Settings Permission
                        PermissionItem(
                            title = "Write System Settings",
                            description = "Required to set screen brightness and eye protection remotely.",
                            isGranted = deviceStatus.hasWriteSettingsPermission,
                            onGrantClick = { viewModel.openWriteSettings() }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Overlay Permission
                        PermissionItem(
                            title = "Display Over Other Apps",
                            description = "Required to pause active screen without killing apps in memory.",
                            isGranted = deviceStatus.hasOverlayPermission,
                            onGrantClick = { viewModel.openOverlaySettings() }
                        )
                    }
                }
            }

            // Emergency & Lock test
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Safety & Emergency Access",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Child devices always retain access to emergency dialers and the parent phone number.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    viewModel.lockScreen(1, "Test Lock from Child Settings")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CoralLock),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Preview Lock Shield")
                            }

                            OutlinedButton(
                                onClick = onNavigatePairing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Pair New Code")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingStatusRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isAlert: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isAlert) CoralLock else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (isAlert) CoralLock else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isGranted) EmeraldSuccess.copy(alpha = 0.08f) else AmberWarning.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (isGranted) EmeraldSuccess else AmberWarning,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (!isGranted && onGrantClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onGrantClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Enable", fontSize = 12.sp)
            }
        }
    }
}
