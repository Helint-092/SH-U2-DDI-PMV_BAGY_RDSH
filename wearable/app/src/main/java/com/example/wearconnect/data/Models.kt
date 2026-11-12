package com.example.wearconnect.data

data class SensorReading(
    val ax: Float = 0f,
    val ay: Float = 0f,
    val az: Float = 0f,
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 0f,
    val bpm: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class GestureEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val gestureType: String,
    val actionExecuted: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class WatchDevice(
    val id: String,
    val displayName: String,
    val isNearby: Boolean
)

enum class PhoneAction(val title: String, val description: String) {
    TOGGLE_FLASHLIGHT("Linterna", "Enciende o apaga la linterna del teléfono"),
    HAPTIC_VIBRATE("Vibración Háptica", "Emite un patrón de vibración fuerte"),
    PLAY_ALERT_SOUND("Sonido de Alerta", "Reproduce un tono de alerta del sistema"),
    NOTIFICATION_ALERT("Notificación", "Muestra una notificación en la pantalla"),
    DO_NOTHING("Sin acción", "No ejecutar ninguna acción")
}

data class GestureConfig(
    val shakeAction: PhoneAction = PhoneAction.TOGGLE_FLASHLIGHT,
    val twistAction: PhoneAction = PhoneAction.HAPTIC_VIBRATE,
    val liftAction: PhoneAction = PhoneAction.PLAY_ALERT_SOUND
)

