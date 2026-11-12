package com.example.wearconnect.wear.service

import android.content.Context
import android.util.Log
import com.example.wearconnect.wear.protocol.WearProtocol
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearConnectionManager(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(appContext)
    private val messageClient: MessageClient = Wearable.getMessageClient(appContext)
    private val dataClient: DataClient = Wearable.getDataClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)

    private val _isPhoneConnected = MutableStateFlow(false)
    val isPhoneConnected: StateFlow<Boolean> = _isPhoneConnected.asStateFlow()

    init {
        checkPhoneConnection()
        listenCapabilityChanges()
    }

    private fun checkPhoneConnection() {
        scope.launch {
            try {
                val capabilityInfo = capabilityClient
                    .getCapability(WearProtocol.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                    .await()
                _isPhoneConnected.value = capabilityInfo.nodes.isNotEmpty()
            } catch (e: Exception) {
                Log.e("WearConnectionMgr", "Error verificando capacidad del teléfono", e)
                _isPhoneConnected.value = false
            }
        }
    }

    private fun listenCapabilityChanges() {
        capabilityClient.addListener({ capabilityInfo ->
            _isPhoneConnected.value = capabilityInfo.nodes.isNotEmpty()
        }, WearProtocol.CAPABILITY_PHONE)
    }

    suspend fun getPhoneNodes(): Set<Node> {
        return try {
            val capability = capabilityClient
                .getCapability(WearProtocol.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await()
            capability.nodes.ifEmpty {
                nodeClient.connectedNodes.await().toSet()
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    suspend fun sendGestureCommand(gestureType: String): Boolean {
        val nodes = getPhoneNodes()
        if (nodes.isEmpty()) {
            Log.w("WearConnectionMgr", "No hay teléfono conectado para recibir el comando de gesto")
            return false
        }

        val payload = gestureType.toByteArray(Charsets.UTF_8)
        var success = false
        nodes.forEach { node ->
            try {
                messageClient.sendMessage(node.id, WearProtocol.MSG_GESTURE_ACTION, payload).await()
                success = true
                Log.d("WearConnectionMgr", "Gesto enviado: $gestureType hacia ${node.displayName}")
            } catch (e: Exception) {
                Log.e("WearConnectionMgr", "Error enviando gesto a ${node.id}", e)
            }
        }
        return success
    }

    suspend fun sendSensorsData(
        ax: Float, ay: Float, az: Float,
        gx: Float, gy: Float, gz: Float,
        bpm: Int
    ) {
        val request = PutDataMapRequest.create(WearProtocol.DATA_SENSORS).apply {
            dataMap.putFloat(WearProtocol.KEY_ACCEL_X, ax)
            dataMap.putFloat(WearProtocol.KEY_ACCEL_Y, ay)
            dataMap.putFloat(WearProtocol.KEY_ACCEL_Z, az)
            dataMap.putFloat(WearProtocol.KEY_GYRO_X, gx)
            dataMap.putFloat(WearProtocol.KEY_GYRO_Y, gy)
            dataMap.putFloat(WearProtocol.KEY_GYRO_Z, gz)
            dataMap.putInt(WearProtocol.KEY_HEART_RATE, bpm)
            dataMap.putLong(WearProtocol.KEY_TIMESTAMP, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        try {
            dataClient.putDataItem(request).await()
        } catch (e: Exception) {
            Log.e("WearConnectionMgr", "Error transmitiendo datos de sensores", e)
        }
    }

    suspend fun sendHeartRate(bpm: Int) {
        val request = PutDataMapRequest.create(WearProtocol.DATA_HEART_RATE).apply {
            dataMap.putInt(WearProtocol.KEY_HEART_RATE, bpm)
            dataMap.putLong(WearProtocol.KEY_TIMESTAMP, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        try {
            dataClient.putDataItem(request).await()
        } catch (e: Exception) {
            Log.e("WearConnectionMgr", "Error enviando ritmo cardíaco", e)
        }
    }

    suspend fun sendPong(originalPayload: ByteArray, targetNodeId: String) {
        try {
            messageClient.sendMessage(targetNodeId, WearProtocol.MSG_PONG, originalPayload).await()
        } catch (e: Exception) {
            Log.e("WearConnectionMgr", "Error enviando Pong", e)
        }
    }
}

