package com.example.wearconnect.service

import android.util.Log
import com.example.wearconnect.data.SensorReading
import com.example.wearconnect.data.WatchDevice
import com.example.wearconnect.protocol.WearProtocol
import com.example.wearconnect.repository.SensorDataRepository
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PhoneWearListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var actionExecutor: ActionExecutor

    override fun onCreate() {
        super.onCreate()
        actionExecutor = ActionExecutor(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val dataStr = String(messageEvent.data, Charsets.UTF_8)
        Log.d("PhoneWearListener", "Mensaje recibido: $path payload=$dataStr")

        when (path) {
            WearProtocol.MSG_PING -> {
                // Responder con Pong
                serviceScope.launch {
                    try {
                        val messageClient = Wearable.getMessageClient(this@PhoneWearListenerService)
                        messageClient.sendMessage(
                            messageEvent.sourceNodeId,
                            WearProtocol.MSG_PONG,
                            messageEvent.data
                        ).await()
                    } catch (e: Exception) {
                        Log.e("PhoneWearListener", "Error respondiendo pong", e)
                    }
                }
            }

            WearProtocol.MSG_PONG -> {
                val timestamp = dataStr.toLongOrNull() ?: 0L
                PhoneConnectionManager(this).onPongReceived(timestamp)
            }

            WearProtocol.MSG_GESTURE_ACTION -> {
                val gestureType = dataStr.ifEmpty { WearProtocol.GESTURE_SHAKE }
                val configuredAction = SensorDataRepository.getActionForGesture(gestureType)

                serviceScope.launch {
                    val result = actionExecutor.executeAction(configuredAction, gestureType)
                    SensorDataRepository.recordGesture(gestureType, "${configuredAction.title}: $result")
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                val path = uri.path ?: ""

                when (path) {
                    WearProtocol.DATA_SENSORS -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val reading = SensorReading(
                            ax = dataMap.getFloat(WearProtocol.KEY_ACCEL_X, 0f),
                            ay = dataMap.getFloat(WearProtocol.KEY_ACCEL_Y, 0f),
                            az = dataMap.getFloat(WearProtocol.KEY_ACCEL_Z, 0f),
                            gx = dataMap.getFloat(WearProtocol.KEY_GYRO_X, 0f),
                            gy = dataMap.getFloat(WearProtocol.KEY_GYRO_Y, 0f),
                            gz = dataMap.getFloat(WearProtocol.KEY_GYRO_Z, 0f),
                            bpm = dataMap.getInt(WearProtocol.KEY_HEART_RATE, 0),
                            timestamp = dataMap.getLong(WearProtocol.KEY_TIMESTAMP, System.currentTimeMillis())
                        )
                        SensorDataRepository.onSensorReadingReceived(reading)
                    }

                    WearProtocol.DATA_HEART_RATE -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val bpm = dataMap.getInt(WearProtocol.KEY_HEART_RATE, 0)
                        SensorDataRepository.onHeartRateReceived(bpm)
                    }
                }
            }
        }
    }

    override fun onPeerConnected(peer: Node) {
        SensorDataRepository.updateConnectedWatch(
            WatchDevice(
                id = peer.id,
                displayName = peer.displayName,
                isNearby = peer.isNearby
            )
        )
    }

    override fun onPeerDisconnected(peer: Node) {
        if (SensorDataRepository.connectedWatch.value?.id == peer.id) {
            SensorDataRepository.updateConnectedWatch(null)
            SensorDataRepository.setStreaming(false)
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name == WearProtocol.CAPABILITY_WATCH) {
            val node = capabilityInfo.nodes.firstOrNull()
            if (node != null) {
                SensorDataRepository.updateConnectedWatch(
                    WatchDevice(
                        id = node.id,
                        displayName = node.displayName,
                        isNearby = node.isNearby
                    )
                )
            } else {
                SensorDataRepository.updateConnectedWatch(null)
            }
        }
    }
}

