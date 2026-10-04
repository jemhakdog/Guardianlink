package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DeviceRole
import com.example.ui.screens.ChildStatusScreen
import com.example.ui.screens.ConnectionHubScreen
import com.example.ui.screens.DemoSandboxScreen
import com.example.ui.screens.OnDeviceParentLockScreen
import com.example.ui.screens.PairingScreen
import com.example.ui.screens.ParentDashboardScreen
import com.example.ui.screens.RoleSelectionScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.theme.GuardianTheme
import com.example.ui.viewmodel.GuardianViewModel

enum class NavigationDestination {
    MAIN,
    PAIRING,
    SCHEDULES,
    SANDBOX,
    NETWORK_HUB
}

class MainActivity : ComponentActivity() {

    private val viewModel: GuardianViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GuardianTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GuardianApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun GuardianApp(viewModel: GuardianViewModel) {
    val context = LocalContext.current
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    var currentDestination by remember { mutableStateOf(NavigationDestination.MAIN) }

    // Request notification permission on Android 13+ so countdown & alerts function
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && (currentRole == DeviceRole.CHILD || currentRole == DeviceRole.STANDALONE_LOCK)) {
            com.example.service.ChildGuardianService.start(context)
        }
    }

    LaunchedEffect(currentRole) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    AnimatedContent(
        targetState = Pair(currentRole, currentDestination),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_transition"
    ) { (role, dest) ->
        when {
            dest == NavigationDestination.PAIRING -> {
                PairingScreen(
                    viewModel = viewModel,
                    onBack = { currentDestination = NavigationDestination.MAIN }
                )
            }
            dest == NavigationDestination.SCHEDULES -> {
                ScheduleScreen(
                    viewModel = viewModel,
                    onBack = { currentDestination = NavigationDestination.MAIN }
                )
            }
            dest == NavigationDestination.SANDBOX -> {
                DemoSandboxScreen(
                    viewModel = viewModel,
                    onBack = { currentDestination = NavigationDestination.MAIN }
                )
            }
            dest == NavigationDestination.NETWORK_HUB -> {
                ConnectionHubScreen(
                    viewModel = viewModel,
                    onBack = { currentDestination = NavigationDestination.MAIN }
                )
            }
            role == null -> {
                RoleSelectionScreen(
                    onSelectRole = { selectedRole ->
                        viewModel.selectRole(selectedRole)
                    },
                    onLaunchSandbox = {
                        currentDestination = NavigationDestination.SANDBOX
                    }
                )
            }
            role == DeviceRole.PARENT -> {
                ParentDashboardScreen(
                    viewModel = viewModel,
                    onNavigatePairing = { currentDestination = NavigationDestination.PAIRING },
                    onNavigateSchedules = { currentDestination = NavigationDestination.SCHEDULES },
                    onNavigateSandbox = { currentDestination = NavigationDestination.SANDBOX },
                    onNavigateNetworkHub = { currentDestination = NavigationDestination.NETWORK_HUB },
                    onSwitchRole = { viewModel.resetRole() }
                )
            }
            role == DeviceRole.CHILD -> {
                ChildStatusScreen(
                    viewModel = viewModel,
                    onNavigatePairing = { currentDestination = NavigationDestination.PAIRING },
                    onNavigateSandbox = { currentDestination = NavigationDestination.SANDBOX },
                    onNavigateNetworkHub = { currentDestination = NavigationDestination.NETWORK_HUB },
                    onSwitchRole = { viewModel.resetRole() }
                )
            }
            role == DeviceRole.STANDALONE_LOCK -> {
                OnDeviceParentLockScreen(
                    viewModel = viewModel,
                    onSwitchRole = { viewModel.resetRole() }
                )
            }
        }
    }
}
