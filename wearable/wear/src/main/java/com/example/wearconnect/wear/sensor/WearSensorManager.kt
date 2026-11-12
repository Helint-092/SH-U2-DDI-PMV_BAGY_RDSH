package com.example.wearconnect.wear.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.wearconnect.wear.protocol.WearProtocol
import com.example.wearconnect.wear.service.WearConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class WearSensorManager(
    context: Context,
    private val connectionManager: WearConnectionManager
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val heartRateSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentHeartRate = MutableStateFlow(72)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    private val _lastDetectedGesture = MutableStateFlow<String?>(null)
    val lastDetectedGesture: StateFlow<String?> = _lastDetectedGesture.asStateFlow()

    // Últimas lecturas
    private var lastAx = 0f
    private var lastAy = 0f
    private var lastAz = 9.8f
    private var lastGx = 0f
    private var lastGy = 0f
    private var lastGz = 0f

    // Cooldown de gestos (para no disparar en bucle)
    private var lastShakeTimestamp = 0L
    private var lastTwistTimestamp = 0L
    private var lastLiftTimestamp = 0L

    // Throttle de transmisión de datos hacia el teléfono (cada 100ms = 10Hz)
    private var lastTransmissionTime = 0L

    fun startStreaming() {
        if (_isStreaming.value) return

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        _isStreaming.value = true
        Log.d("WearSensorManager", "Transmisión de sensores iniciada")
    }

    fun stopStreaming() {
        if (!_isStreaming.value) return
        sensorManager.unregisterListener(this)
        _isStreaming.value = false
        Log.d("WearSensorManager", "Transmisión de sensores detenida")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAx = event.values[0]
                lastAy = event.values[1]
                lastAz = event.values[2]

                // Análisis y detección de gestos
                evaluateAccelerometerGestures(lastAx, lastAy, lastAz)
            }

            Sensor.TYPE_GYROSCOPE -> {
                lastGx = event.values[0]
                lastGy = event.values[1]
                lastGz = event.values[2]

                // Detección de giro de muñeca
                evaluateGyroscopeGestures(lastGx, lastGy, lastGz)
            }

            Sensor.TYPE_HEART_RATE -> {
                val bpm = event.values[0].toInt()
                if (bpm > 0) {
                    _currentHeartRate.value = bpm
                    scope.launch {
                        connectionManager.sendHeartRate(bpm)
                    }
                }
            }
        }

        // Transmisión con throttle al teléfono
        val now = System.currentTimeMillis()
        if (now - lastTransmissionTime >= 100) {
            lastTransmissionTime = now
            scope.launch {
                connectionManager.sendSensorsData(
                    lastAx, lastAy, lastAz,
                    lastGx, lastGy, lastGz,
                    _currentHeartRate.value
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun evaluateAccelerometerGestures(ax: Float, ay: Float, az: Float) {
        val now = System.currentTimeMillis()
        val magnitude = sqrt((ax * ax + ay * ay + az * az).toDouble())

        // 1. Detección de Sacudida (Shake): Aceleración neta brusca > 18 m/s²
        if (magnitude > 18.0 && (now - lastShakeTimestamp) > 1500) {
            lastShakeTimestamp = now
            _lastDetectedGesture.value = WearProtocol.GESTURE_SHAKE
            Log.i("WearSensorManager", "¡Gesto detectado: SACUDIDA (Shake)!")
            scope.launch {
                connectionManager.sendGestureCommand(WearProtocol.GESTURE_SHAKE)
            }
        }

        // 2. Detección de Levantamiento de mano (Lift Hand): Elevación vertical notable
        if (ay > 14.0 && az in -3.0f..5.0f && (now - lastLiftTimestamp) > 2000) {
            lastLiftTimestamp = now
            _lastDetectedGesture.value = WearProtocol.GESTURE_LIFT
            Log.i("WearSensorManager", "¡Gesto detectado: LEVANTAR MANO (Lift)!")
            scope.launch {
                connectionManager.sendGestureCommand(WearProtocol.GESTURE_LIFT)
            }
        }
    }

    private fun evaluateGyroscopeGestures(gx: Float, gy: Float, gz: Float) {
        val now = System.currentTimeMillis()
        val rotationSpeed = sqrt((gx * gx + gy * gy + gz * gz).toDouble())

        // 3. Detección de Giro de muñeca (Wrist Twist): Rotación angular rápida > 4.5 rad/s
        if (rotationSpeed > 4.5 && (now - lastTwistTimestamp) > 1500) {
            lastTwistTimestamp = now
            _lastDetectedGesture.value = WearProtocol.GESTURE_TWIST
            Log.i("WearSensorManager", "¡Gesto detectado: GIRO DE MUÑECA (Wrist Twist)!")
            scope.launch {
                connectionManager.sendGestureCommand(WearProtocol.GESTURE_TWIST)
            }
        }
    }

    fun triggerManualGesture(gestureType: String) {
        _lastDetectedGesture.value = gestureType
        scope.launch {
            connectionManager.sendGestureCommand(gestureType)
        }
    }
}

