package com.example.wearconnect.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wearconnect.repository.SensorDataRepository
import com.example.wearconnect.service.PhoneConnectionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun SensorsDiagnosticScreen(
    connectionManager: PhoneConnectionManager
) {
    val coroutineScope = rememberCoroutineScope()
    val reading by SensorDataRepository.currentReading.collectAsState()
    val isStreaming by SensorDataRepository.isStreaming.collectAsState()
    val totalPackets by SensorDataRepository.totalPackets.collectAsState()
    val sampleRateHz by SensorDataRepository.sampleRateHz.collectAsState()
    val connectedWatch by SensorDataRepository.connectedWatch.collectAsState()

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    // Animación de latido para el corazón
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (reading.bpm > 0) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Encabezado de Diagnóstico
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isStreaming) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isStreaming) "📡 Transmisión Activa" else "⏸️ Transmisión en Espera",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dispositivo: ${connectedWatch?.displayName ?: "No detectado"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStreaming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        onClick = {
                            coroutineScope.launch {
                                connectionManager.toggleSensorStream(!isStreaming)
                            }
                        }
                    ) {
                        Text(if (isStreaming) "Pausar" else "Reanudar")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Frecuencia", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = String.format(Locale.US, "%.1f Hz", sampleRateHz),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("Muestras recibidas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = "$totalPackets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("Última actualización", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = if (reading.timestamp > 0) timeFormat.format(Date(reading.timestamp)) else "--",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Medidor de Frecuencia Cardíaca
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFEBEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Corazón",
                        tint = Color.Red,
                        modifier = Modifier
                            .size(36.dp)
                            .scale(heartScale)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Sensor Cardíaco (PPG)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = if (reading.bpm > 0) "${reading.bpm} BPM" else "Esperando lectura...",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (reading.bpm > 0) Color(0xFFC2185B) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Acelerómetro (X, Y, Z)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Acelerómetro (m/s²)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Detecta la orientación y movimientos en los 3 ejes espaciales",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(14.dp))

                SensorAxisBar(axisName = "Eje X (Lateral)", value = reading.ax, color = Color(0xFF1976D2), maxRange = 20f)
                Spacer(modifier = Modifier.height(10.dp))
                SensorAxisBar(axisName = "Eje Y (Longitudinal)", value = reading.ay, color = Color(0xFF388E3C), maxRange = 20f)
                Spacer(modifier = Modifier.height(10.dp))
                SensorAxisBar(axisName = "Eje Z (Vertical / Gravedad)", value = reading.az, color = Color(0xFFF57C00), maxRange = 20f)
            }
        }

        // Giroscopio (X, Y, Z)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Giroscopio (rad/s)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Detecta la velocidad angular y rotación de la muñeca",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(14.dp))

                SensorAxisBar(axisName = "Rotación X", value = reading.gx, color = Color(0xFF7B1FA2), maxRange = 10f)
                Spacer(modifier = Modifier.height(10.dp))
                SensorAxisBar(axisName = "Rotación Y", value = reading.gy, color = Color(0xFF0097A7), maxRange = 10f)
                Spacer(modifier = Modifier.height(10.dp))
                SensorAxisBar(axisName = "Rotación Z", value = reading.gz, color = Color(0xFFD32F2F), maxRange = 10f)
            }
        }
    }
}

@Composable
fun SensorAxisBar(
    axisName: String,
    value: Float,
    color: Color,
    maxRange: Float
) {
    val fraction = (value / maxRange).coerceIn(-1f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(axisName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                text = String.format(Locale.US, "%+6.2f", value),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Barra con centro en 0
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Línea central
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.outline)
            )

            // Indicador de valor
            val barWidthFraction = abs(fraction) * 0.5f
            val alignment = if (fraction >= 0) Alignment.CenterStart else Alignment.CenterEnd

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f + if (fraction >= 0) barWidthFraction else 0f)
                    .padding(start = if (fraction < 0) ((0.5f - barWidthFraction) * 300).dp else 0.dp)
            ) {
                if (fraction >= 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(barWidthFraction / 0.5f)
                            .align(Alignment.CenterEnd)
                            .background(color)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(barWidthFraction / 0.5f)
                            .align(Alignment.CenterStart)
                            .background(color)
                    )
                }
            }
        }
    }
}

