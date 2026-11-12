package com.example.wearconnect.wear.service

import android.content.Intent
import android.util.Log
import com.example.wearconnect.wear.protocol.WearProtocol
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearListenerService : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val dataStr = String(messageEvent.data, Charsets.UTF_8)
        Log.d("WearListenerService", "Mensaje recibido del teléfono: $path ($dataStr)")

        when (path) {
            WearProtocol.MSG_PING -> {
                scope.launch {
                    try {
                        val messageClient = Wearable.getMessageClient(this@WearListenerService)
                        messageClient.sendMessage(
                            messageEvent.sourceNodeId,
                            WearProtocol.MSG_PONG,
                            messageEvent.data
                        ).await()
                        Log.d("WearListenerService", "Pong enviado al teléfono")
                    } catch (e: Exception) {
                        Log.e("WearListenerService", "Error respondiendo Ping", e)
                    }
                }
            }

            WearProtocol.MSG_CONTROL_STREAM -> {
                // Notificar a la actividad o gestor de sensores
                val intent = Intent("com.example.wearconnect.SENSOR_CONTROL").apply {
                    putExtra("command", dataStr)
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            }
        }
    }
}

