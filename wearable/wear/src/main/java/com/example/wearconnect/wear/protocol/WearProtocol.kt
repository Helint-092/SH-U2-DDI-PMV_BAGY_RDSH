package com.example.wearconnect.wear.protocol

object WearProtocol {
    // Capacidades registradas en wear.xml
    const val CAPABILITY_WATCH = "smartwatch_app"
    const val CAPABILITY_PHONE = "mobile_app"

    // Rutas de mensajes rápidos (MessageClient)
    const val MSG_PING = "/ping"
    const val MSG_PONG = "/pong"
    const val MSG_GESTURE_ACTION = "/gesture/action"
    const val MSG_CONTROL_STREAM = "/sensors/control"

    // Rutas de datos (DataClient)
    const val DATA_SENSORS = "/sensors/realtime"
    const val DATA_HEART_RATE = "/heart_rate"

    // Claves en DataMap
    const val KEY_TIMESTAMP = "timestamp"
    const val KEY_ACCEL_X = "ax"
    const val KEY_ACCEL_Y = "ay"
    const val KEY_ACCEL_Z = "az"
    const val KEY_GYRO_X = "gx"
    const val KEY_GYRO_Y = "gy"
    const val KEY_GYRO_Z = "gz"
    const val KEY_HEART_RATE = "bpm"
    const val KEY_GESTURE = "gesture"
    const val KEY_STATUS = "status"

    // Tipos de gestos detectados
    const val GESTURE_SHAKE = "SHAKE"
    const val GESTURE_TWIST = "WRIST_TWIST"
    const val GESTURE_LIFT = "LIFT_HAND"
}

