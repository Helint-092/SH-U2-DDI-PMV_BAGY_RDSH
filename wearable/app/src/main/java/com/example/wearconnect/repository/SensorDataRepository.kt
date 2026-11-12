package com.example.wearconnect.repository

import com.example.wearconnect.data.GestureConfig
import com.example.wearconnect.data.GestureEvent
import com.example.wearconnect.data.PhoneAction
import com.example.wearconnect.data.SensorReading
import com.example.wearconnect.data.WatchDevice
import com.example.wearconnect.protocol.WearProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SensorDataRepository {

    private val _currentReading = MutableStateFlow(SensorReading())
    val currentReading: StateFlow<SensorReading> = _currentReading.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _connectedWatch = MutableStateFlow<WatchDevice?>(null)
    val connectedWatch: StateFlow<WatchDevice?> = _connectedWatch.asStateFlow()

    private val _gestureEvents = MutableStateFlow<List<GestureEvent>>(emptyList())
    val gestureEvents: StateFlow<List<GestureEvent>> = _gestureEvents.asStateFlow()

    private val _gestureConfig = MutableStateFlow(GestureConfig())
    val gestureConfig: StateFlow<GestureConfig> = _gestureConfig.asStateFlow()

    private val _totalPackets = MutableStateFlow(0L)
    val totalPackets: StateFlow<Long> = _totalPackets.asStateFlow()

    private val _sampleRateHz = MutableStateFlow(0f)
    val sampleRateHz: StateFlow<Float> = _sampleRateHz.asStateFlow()

    private val _lastPingLatencyMs = MutableStateFlow<Long?>(null)
    val lastPingLatencyMs: StateFlow<Long?> = _lastPingLatencyMs.asStateFlow()

    private var lastSampleTimestamp = 0L
    private val intervals = ArrayList<Long>()

    fun updateConnectedWatch(device: WatchDevice?) {
        _connectedWatch.value = device
    }

    fun setStreaming(active: Boolean) {
        _isStreaming.value = active
    }

    fun setPingLatency(ms: Long) {
        _lastPingLatencyMs.value = ms
    }

    fun updateGestureConfig(config: GestureConfig) {
        _gestureConfig.value = config
    }

    fun onSensorReadingReceived(reading: SensorReading) {
        _currentReading.value = reading
        _totalPackets.value += 1
        _isStreaming.value = true

        val now = System.currentTimeMillis()
        if (lastSampleTimestamp > 0) {
            val delta = now - lastSampleTimestamp
            if (delta in 5..3000) {
                intervals.add(delta)
                if (intervals.size > 20) intervals.removeAt(0)
                val avgInterval = intervals.average()
                if (avgInterval > 0) {
                    _sampleRateHz.value = (1000f / avgInterval).toFloat()
                }
            }
        }
        lastSampleTimestamp = now
    }

    fun onHeartRateReceived(bpm: Int) {
        _currentReading.value = _currentReading.value.copy(
            bpm = bpm,
            timestamp = System.currentTimeMillis()
        )
        _totalPackets.value += 1
    }

    fun recordGesture(gestureType: String, actionResult: String) {
        val event = GestureEvent(
            gestureType = gestureType,
            actionExecuted = actionResult,
            timestamp = System.currentTimeMillis()
        )
        _gestureEvents.value = (listOf(event) + _gestureEvents.value).take(30)
    }

    fun getActionForGesture(gestureType: String): PhoneAction {
        return when (gestureType) {
            WearProtocol.GESTURE_SHAKE -> _gestureConfig.value.shakeAction
            WearProtocol.GESTURE_TWIST -> _gestureConfig.value.twistAction
            WearProtocol.GESTURE_LIFT -> _gestureConfig.value.liftAction
            else -> PhoneAction.HAPTIC_VIBRATE
        }
    }

    fun clearHistory() {
        _gestureEvents.value = emptyList()
        _totalPackets.value = 0L
    }
}

