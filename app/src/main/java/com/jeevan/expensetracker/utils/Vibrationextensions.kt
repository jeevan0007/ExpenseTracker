package com.jeevan.expensetracker.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Returns the system Vibrator, handling the API-31 VibratorManager
 * split cleanly in one place.
 *
 * Replaces identical private fun getVibrator() copy-pasted into
 * MainActivity and RecycleBinActivity.
 */
fun Context.getVibrator(): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

/**
 * Short, soft tap — used on every button press.
 * 20 ms at half amplitude.
 */
fun Context.vibrateLight() {
    val v = getVibrator()
    if (!v.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(VibrationEffect.createOneShot(20, 50))
    } else {
        @Suppress("DEPRECATION") v.vibrate(20)
    }
}

/**
 * Standard confirm tap — used on FAB press, save, delete confirm.
 * 50 ms at default amplitude.
 */
fun Context.vibrate() {
    val v = getVibrator()
    if (!v.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION") v.vibrate(50)
    }
}

/**
 * Heavy impact — used for permanent destructive actions (hard delete).
 * 100 ms at max amplitude.
 */
fun Context.vibrateHeavy() {
    val v = getVibrator()
    if (!v.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(VibrationEffect.createOneShot(100, 255))
    } else {
        @Suppress("DEPRECATION") v.vibrate(100)
    }
}

/**
 * Double-pulse reset pattern — used on shake-to-reset.
 * Two short bursts: 70 ms, pause, 70 ms.
 */
fun Context.vibrateReset() {
    val v = getVibrator()
    if (!v.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 50, 70), -1))
    } else {
        @Suppress("DEPRECATION") v.vibrate(300)
    }
}

/**
 * Glitch pattern — used for the secret cat / make-it-rain easter egg.
 * Irregular multi-burst with varying amplitudes.
 */
fun Context.vibrateGlitch() {
    val v = getVibrator()
    if (!v.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 40, 80, 40, 150, 60),
                intArrayOf(0, 160, 0, 200, 0, 180),
                -1
            )
        )
    } else {
        @Suppress("DEPRECATION") v.vibrate(400)
    }
}