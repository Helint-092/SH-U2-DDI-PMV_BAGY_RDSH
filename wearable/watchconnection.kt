package com.example.wearconnect

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "WatchConnection"

object WearPaths {
    const val CAPABILITY = "smartwatch_app"
    const val MSG_PING = "/ping"
    const val MSG_COMMAND = "/command"
    const val DATA_HEART_RATE = "/heart_rate"

    const val KEY_BPM = "bpm"
    const val KEY_TIMESTAMP = "timestamp"
}

class WatchConnectionManager(context: Context) {

    private val appContext = context.applicationContext
    private val messageClient: MessageClient = Wearable.getMessageClient(appContext)
    private val dataClient: DataClient = Wearable.getDataClient(appContext)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)

    suspend fun getConnectedNodes(): List<Node> =
        nodeClient.connectedNodes.await()

    suspend fun getNodesWithApp(): Set<Node> =
        capabilityClient
            .getCapability(WearPaths.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes

    fun connectionState(): Flow<Boolean> = callbackFlow {
        val listener = CapabilityClient.OnCapabilityChangedListener { info ->
            trySend(info.nodes.any { it.isNearby })
        }

        capabilityClient.addListener(listener, WearPaths.CAPABILITY)

        trySend(runCatching { getNodesWithApp().isNotEmpty() }.getOrDefault(false))

        awaitClose {
            capabilityClient.removeListener(listener)
        }
    }

    suspend fun sendMessage(
        path: String,
        payload: ByteArray = ByteArray(0)
    ): Boolean {
        val nodes = getNodesWithApp()

        if (nodes.isEmpty()) {
            Log.w(TAG, "No hay dispositivos conectados con la app")
            return false
        }

        nodes.forEach { node ->
            messageClient.sendMessage(node.id, path, payload).await()
        }

        return true
    }

    suspend fun sendText(path: String, text: String) =
        sendMessage(path, text.toByteArray(Charsets.UTF_8))

    fun incomingMessages(): Flow<MessageEvent> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener {
            trySend(it)
        }

        messageClient.addListener(listener)

        awaitClose {
            messageClient.removeListener(listener)
        }
    }

    suspend fun putData(
        path: String,
        fill: (com.google.android.gms.wearable.DataMap) -> Unit
    ) {
        val request = PutDataMapRequest.create(path).apply {
            fill(dataMap)
            dataMap.putLong(
                WearPaths.KEY_TIMESTAMP,
                System.currentTimeMillis()
            )
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request).await()
    }

    suspend fun sendHeartRate(bpm: Int) =
        putData(WearPaths.DATA_HEART_RATE) {
            it.putInt(WearPaths.KEY_BPM, bpm)
        }

    fun dataChanges(): Flow<DataEvent> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { events ->
            events.map { it.freeze() }.forEach {
                trySend(it)
            }
        }

        dataClient.addListener(listener)

        awaitClose {
            dataClient.removeListener(listener)
        }
    }
}

class WearListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearPaths.MSG_PING -> {
                Log.d(TAG, "Ping recibido de ${event.sourceNodeId}")
            }

            WearPaths.MSG_COMMAND -> {
                val command = String(event.data, Charsets.UTF_8)
                Log.d(TAG, "Comando recibido: $command")
            }

            else -> super.onMessageReceived(event)
        }
    }

    override fun onDataChanged(
        events: com.google.android.gms.wearable.DataEventBuffer
    ) {
        events.forEach { event ->
            if (
                event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WearPaths.DATA_HEART_RATE
            ) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val bpm = map.getInt(WearPaths.KEY_BPM)

                Log.d(TAG, "Frecuencia cardíaca recibida: $bpm bpm")
            }
        }
    }
}