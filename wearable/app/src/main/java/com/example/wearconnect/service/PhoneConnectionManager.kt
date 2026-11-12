package com.example.wearconnect.service

import android.content.Context
import android.util.Log
import com.example.wearconnect.data.WatchDevice
import com.example.wearconnect.protocol.WearProtocol
import com.example.wearconnect.repository.SensorDataRepository
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class PhoneConnectionManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val capabilityClient = Wearable.getCapabilityClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)

    private var pingStartTime: Long = 0L

    suspend fun refreshConnectedDevices(): List<WatchDevice> = withContext(Dispatchers.IO) {
        try {
            val capabilityInfo = capabilityClient
                .getCapability(WearProtocol.CAPABILITY_WATCH, CapabilityClient.FILTER_ALL)
                .await()

            val connectedNodes: List<Node> = nodeClient.connectedNodes.await()
            val watchNodes = capabilityInfo.nodes.ifEmpty { connectedNodes.toSet() }

            val devices = watchNodes.map { node ->
                WatchDevice(
                    id = node.id,
                    displayName = node.displayName,
                    isNearby = node.isNearby
                )
            }

            val primaryDevice = devices.firstOrNull()
            SensorDataRepository.updateConnectedWatch(primaryDevice)
            devices
        } catch (e: Exception) {
            Log.e("PhoneConnectionMgr", "Error al buscar smartwatches", e)
            SensorDataRepository.updateConnectedWatch(null)
            emptyList()
        }
    }

    suspend fun sendPing(): Long? = withContext(Dispatchers.IO) {
        try {
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) return@withContext null

            pingStartTime = System.currentTimeMillis()
            val payload = pingStartTime.toString().toByteArray(Charsets.UTF_8)

            for (node in nodes) {
                messageClient.sendMessage(node.id, WearProtocol.MSG_PING, payload).await()
            }
            // Retorna timestamp de envío
            pingStartTime
        } catch (e: Exception) {
            Log.e("PhoneConnectionMgr", "Error al enviar ping", e)
            null
        }
    }

    fun onPongReceived(timestamp: Long) {
        if (pingStartTime > 0) {
            val rtt = System.currentTimeMillis() - pingStartTime
            SensorDataRepository.setPingLatency(rtt)
        }
    }

    suspend fun toggleSensorStream(start: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) return@withContext false

            val command = if (start) "START" else "STOP"
            val payload = command.toByteArray(Charsets.UTF_8)

            for (node in nodes) {
                messageClient.sendMessage(node.id, WearProtocol.MSG_CONTROL_STREAM, payload).await()
            }
            SensorDataRepository.setStreaming(start)
            true
        } catch (e: Exception) {
            Log.e("PhoneConnectionMgr", "Error al enviar comando de stream", e)
            false
        }
    }
}

