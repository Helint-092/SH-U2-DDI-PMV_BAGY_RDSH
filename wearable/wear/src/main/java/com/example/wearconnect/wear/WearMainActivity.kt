package com.example.wearconnect.wear

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.*
import com.example.wearconnect.wear.protocol.WearProtocol
import com.example.wearconnect.wear.sensor.WearSensorManager
import com.example.wearconnect.wear.service.WearConnectionManager

class WearMainActivity : ComponentActivity() {

    private lateinit var connectionManager: WearConnectionManager
    private lateinit var sensorManager: WearSensorManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            sensorManager.startStreaming()
        }
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val cmd = intent?.getStringExtra("command") ?: ""
            if (cmd == "START") {
                sensorManager.startStreaming()
            } else if (cmd == "STOP") {
                sensorManager.stopStreaming()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionManager = WearConnectionManager(this)
        sensorManager = WearSensorManager(this, connectionManager)

        requestSensorPermissions()

        val filter = IntentFilter("com.example.wearconnect.SENSOR_CONTROL")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(controlReceiver, filter)
        }

        setContent {
            WearAppUI(
                connectionManager = connectionManager,
                sensorManager = sensorManager
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(controlReceiver)
        sensorManager.stopStreaming()
    }

    private fun requestSensorPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        } else {
            sensorManager.startStreaming()
        }
    }
}

@Composable
fun WearAppUI(
    connectionManager: WearConnectionManager,
    sensorManager: WearSensorManager
) {
    val isConnected by connectionManager.isPhoneConnected.collectAsState()
    val isStreaming by sensorManager.isStreaming.collectAsState()
    val heartRate by sensorManager.currentHeartRate.collectAsState()
    val lastGesture by sensorManager.lastDetectedGesture.collectAsState()

    MaterialTheme {
        Scaffold(
            timeText = { TimeText() }
        ) {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text(
                        text = "WearConnect",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center
                    )
                }

                // Estado de Conexión
                item {
                    Text(
                        text = if (isConnected) "✅ Teléfono conectado" else "❌ Sin conexión móvil",
                        fontSize = 11.sp,
                        color = if (isConnected) Color(0xFF81C784) else Color(0xFFE57373),
                        textAlign = TextAlign.Center
                    )
                }

                // Frecuencia Cardíaca
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        onClick = {},
                        label = {
                            Text(
                                text = "❤️ $heartRate BPM",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = Color(0xFF2C1B1B),
                            contentColor = Color(0xFFFF5252)
                        )
                    )
                }

                // Último Gesto Detectado
                if (lastGesture != null) {
                    item {
                        Text(
                            text = "⚡ Gesto: $lastGesture",
                            fontSize = 11.sp,
                            color = Color(0xFFFFD54F),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Botón Iniciar / Detener Transmisión
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isStreaming) MaterialTheme.colors.error else MaterialTheme.colors.primary
                        ),
                        onClick = {
                            if (isStreaming) {
                                sensorManager.stopStreaming()
                            } else {
                                sensorManager.startStreaming()
                            }
                        }
                    ) {
                        Text(
                            text = if (isStreaming) "Pausar Sensores" else "Iniciar Sensores",
                            fontSize = 11.sp
                        )
                    }
                }

                // Botones de Simulación Manual de Gestos (ideales para pruebas o emuladores)
                item {
                    Text(
                        text = "Probar Acciones en Móvil:",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }

                item {
                    CompactChip(
                        onClick = {
                            sensorManager.triggerManualGesture(WearProtocol.GESTURE_SHAKE)
                        },
                        label = { Text("Sacudir (Shake)", fontSize = 10.sp) },
                        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF333333))
                    )
                }

                item {
                    CompactChip(
                        onClick = {
                            sensorManager.triggerManualGesture(WearProtocol.GESTURE_TWIST)
                        },
                        label = { Text("Giro Muñeca (Twist)", fontSize = 10.sp) },
                        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF333333))
                    )
                }
            }
        }
    }
}

