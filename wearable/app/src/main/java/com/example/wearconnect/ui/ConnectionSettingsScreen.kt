package com.example.wearconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wearconnect.data.GestureConfig
import com.example.wearconnect.data.PhoneAction
import com.example.wearconnect.data.WatchDevice
import com.example.wearconnect.protocol.WearProtocol
import com.example.wearconnect.repository.SensorDataRepository
import com.example.wearconnect.service.ActionExecutor
import com.example.wearconnect.service.PhoneConnectionManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsScreen(
    connectionManager: PhoneConnectionManager,
    devices: List<WatchDevice>,
    onRefreshDevices: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val actionExecutor = remember { ActionExecutor(context) }

    val connectedWatch by SensorDataRepository.connectedWatch.collectAsState()
    val pingLatency by SensorDataRepository.lastPingLatencyMs.collectAsState()
    val isStreaming by SensorDataRepository.isStreaming.collectAsState()
    val gestureConfig by SensorDataRepository.gestureConfig.collectAsState()

    var isScanning by remember { mutableStateOf(false) }
    var isPinging by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tarjeta de Dispositivo y Escaneo
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Conexión con Smartwatch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            isScanning = true
                            coroutineScope.launch {
                                onRefreshDevices()
                                isScanning = false
                            }
                        }
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Buscar reloj")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (devices.isEmpty()) {
                    Text(
                        text = "No se encontraron smartwatches emparejados. Asegúrate de tener Bluetooth encendido y la app del reloj instalada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    devices.forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Watch,
                                contentDescription = null,
                                tint = if (device.isNearby) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "ID: ${device.id.take(12)}... | ${if (device.isNearby) "Cercano" else "Remoto"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text(if (device.isNearby) "Activo" else "Offline", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Fila de acciones de conexión: Ping y Control de Stream
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isPinging = true
                            coroutineScope.launch {
                                connectionManager.sendPing()
                                isPinging = false
                            }
                        }
                    ) {
                        if (isPinging) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (pingLatency != null) "${pingLatency}ms" else "Ping")
                        }
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStreaming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        onClick = {
                            coroutineScope.launch {
                                connectionManager.toggleSensorStream(!isStreaming)
                            }
                        }
                    ) {
                        Icon(
                            if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isStreaming) "Pausar" else "Transmitir")
                    }
                }
            }
        }

        // Configuración de Acciones para Movimientos
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Acciones por Movimiento del Reloj",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Define qué acción ejecutará este teléfono cuando realices cada gesto con el reloj:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Gesto 1: Sacudida (Shake)
                GestureActionSelector(
                    gestureName = "Sacudir reloj (Shake)",
                    gestureDescription = "Movimiento enérgico hacia los lados",
                    selectedAction = gestureConfig.shakeAction,
                    onActionSelected = { newAction ->
                        SensorDataRepository.updateGestureConfig(
                            gestureConfig.copy(shakeAction = newAction)
                        )
                    },
                    onTestExecute = {
                        coroutineScope.launch {
                            val res = actionExecutor.executeAction(gestureConfig.shakeAction, WearProtocol.GESTURE_SHAKE)
                            SensorDataRepository.recordGesture(WearProtocol.GESTURE_SHAKE, "[Test] $res")
                        }
                    }
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Gesto 2: Giro de muñeca (Wrist Twist)
                GestureActionSelector(
                    gestureName = "Giro de muñeca (Twist)",
                    gestureDescription = "Rotar la muñeca rápidamente",
                    selectedAction = gestureConfig.twistAction,
                    onActionSelected = { newAction ->
                        SensorDataRepository.updateGestureConfig(
                            gestureConfig.copy(twistAction = newAction)
                        )
                    },
                    onTestExecute = {
                        coroutineScope.launch {
                            val res = actionExecutor.executeAction(gestureConfig.twistAction, WearProtocol.GESTURE_TWIST)
                            SensorDataRepository.recordGesture(WearProtocol.GESTURE_TWIST, "[Test] $res")
                        }
                    }
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Gesto 3: Levantar mano (Lift Hand)
                GestureActionSelector(
                    gestureName = "Levantar mano (Lift)",
                    gestureDescription = "Elevar el brazo hacia arriba",
                    selectedAction = gestureConfig.liftAction,
                    onActionSelected = { newAction ->
                        SensorDataRepository.updateGestureConfig(
                            gestureConfig.copy(liftAction = newAction)
                        )
                    },
                    onTestExecute = {
                        coroutineScope.launch {
                            val res = actionExecutor.executeAction(gestureConfig.liftAction, WearProtocol.GESTURE_LIFT)
                            SensorDataRepository.recordGesture(WearProtocol.GESTURE_LIFT, "[Test] $res")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun GestureActionSelector(
    gestureName: String,
    gestureDescription: String,
    selectedAction: PhoneAction,
    onActionSelected: (PhoneAction) -> Unit,
    onTestExecute: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = gestureName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(text = gestureDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onTestExecute) {
                Icon(Icons.Default.PlayCircleOutline, contentDescription = "Probar acción", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = selectedAction.title)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                PhoneAction.values().forEach { action ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(action.title, fontWeight = FontWeight.Bold)
                                Text(action.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        },
                        onClick = {
                            onActionSelected(action)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

