package com.example.wearconnect

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.wearconnect.data.WatchDevice
import com.example.wearconnect.repository.SensorDataRepository
import com.example.wearconnect.service.PhoneConnectionManager
import com.example.wearconnect.ui.ConnectionSettingsScreen
import com.example.wearconnect.ui.HomeScreen
import com.example.wearconnect.ui.SensorsDiagnosticScreen
import kotlinx.coroutines.launch

enum class Screen(val title: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Default.Home),
    SENSORS("Sensores", Icons.Default.Sensors),
    SETTINGS("Conexión", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private lateinit var connectionManager: PhoneConnectionManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permisos procesados
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionManager = PhoneConnectionManager(this)

        checkPermissions()

        lifecycleScope.launch {
            connectionManager.refreshConnectedDevices()
        }

        setContent {
            MaterialTheme {
                MainAppLayout(connectionManager)
            }
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppLayout(connectionManager: PhoneConnectionManager) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var devices by remember { mutableStateOf<List<WatchDevice>>(emptyList()) }
    val connectedWatch by SensorDataRepository.connectedWatch.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        devices = connectionManager.refreshConnectedDevices()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (currentScreen) {
                                Screen.HOME -> "WearConnect"
                                Screen.SENSORS -> "Monitor de Sensores"
                                Screen.SETTINGS -> "Configuración & Conexión"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (connectedWatch != null) "Conectado a ${connectedWatch?.displayName}" else "Sin reloj conectado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.values().forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        label = { Text(screen.title) },
                        icon = { Icon(screen.icon, contentDescription = screen.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    connectedWatch = connectedWatch,
                    onNavigateToSensors = { currentScreen = Screen.SENSORS },
                    onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                    onTestPing = {
                        coroutineScope.launch {
                            connectionManager.sendPing()
                        }
                    }
                )

                Screen.SENSORS -> SensorsDiagnosticScreen(
                    connectionManager = connectionManager
                )

                Screen.SETTINGS -> ConnectionSettingsScreen(
                    connectionManager = connectionManager,
                    devices = devices,
                    onRefreshDevices = {
                        devices = connectionManager.refreshConnectedDevices()
                    }
                )
            }
        }
    }
}
