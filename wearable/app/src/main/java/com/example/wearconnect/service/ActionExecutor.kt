package com.example.wearconnect.service

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.RingtoneManager
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import com.example.wearconnect.data.PhoneAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ActionExecutor(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var isTorchOn = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    suspend fun executeAction(action: PhoneAction, gestureSource: String): String {
        return withContext(Dispatchers.Main) {
            when (action) {
                PhoneAction.TOGGLE_FLASHLIGHT -> toggleFlashlight()
                PhoneAction.HAPTIC_VIBRATE -> triggerVibration()
                PhoneAction.PLAY_ALERT_SOUND -> playAlertSound()
                PhoneAction.NOTIFICATION_ALERT -> showToastAlert(gestureSource)
                PhoneAction.DO_NOTHING -> "Gesto detectado ($gestureSource) sin acción configurada"
            }
        }
    }

    private fun toggleFlashlight(): String {
        if (cameraManager == null) return "Cámara no disponible"
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId != null) {
                isTorchOn = !isTorchOn
                cameraManager.setTorchMode(cameraId, isTorchOn)
                val status = if (isTorchOn) "Linterna ENCENDIDA" else "Linterna APAGADA"
                Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                status
            } else {
                "Sin flash disponible en el dispositivo"
            }
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Error al alternar linterna", e)
            "Error en linterna: ${e.message}"
        }
    }

    private fun triggerVibration(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 150, 80, 250)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(300)
            }
            Toast.makeText(context, "📳 Vibración activada por reloj", Toast.LENGTH_SHORT).show()
            "Vibración háptica ejecutada"
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Error al vibrar", e)
            "Error en vibración: ${e.message}"
        }
    }

    private fun playAlertSound(): String {
        return try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.play()
            Toast.makeText(context, "🔔 Tono de alerta activado por reloj", Toast.LENGTH_SHORT).show()
            "Sonido de alerta reproducido"
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Error al reproducir sonido", e)
            "Error de sonido: ${e.message}"
        }
    }

    private fun showToastAlert(gesture: String): String {
        Toast.makeText(context, "⚡ Gesto detectado en reloj: $gesture", Toast.LENGTH_LONG).show()
        return "Alerta en pantalla mostrada"
    }
}

