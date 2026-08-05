package vip.cdms.drsticker.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

fun vibrate(
    context: Context,
    milliseconds: Long = 100,
    amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE,
) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (vibrator.hasVibrator()) {
        val effect = VibrationEffect.createOneShot(milliseconds, amplitude)
        vibrator.vibrate(effect)
    }
}
