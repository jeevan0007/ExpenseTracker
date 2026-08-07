package com.jeevan.expensetracker.utils

import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Applies a satisfying press-and-release squish animation to any View,
 * firing [onClickAction] on ACTION_UP.
 *
 * Replaces the identical private fun applySquishPhysics() that was
 * copy-pasted into MainActivity, RecycleBinActivity, and
 * CategorySettingsActivity.
 */
fun View.applySquishPhysics(onClickAction: () -> Unit) {
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN ->
                v.animate()
                    .scaleX(0.92f).scaleY(0.92f)
                    .setDuration(100)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

            MotionEvent.ACTION_UP -> {
                v.context.vibrateLight()
                v.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
                onClickAction()
            }

            MotionEvent.ACTION_CANCEL ->
                v.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
        }
        true
    }
}

/**
 * Converts dp to pixels using the View's own display metrics.
 * Use this inside View.apply { } blocks or on a View receiver.
 */
fun View.dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density).toInt()

/**
 * Converts dp to pixels using a Context (Activity, Service, etc.).
 * Use this inside Activity methods or any non-View scope where
 * View.dpToPx() would fail due to receiver type mismatch.
 */
fun android.content.Context.dpToPx(dp: Int): Int =
    (dp * resources.displayMetrics.density).toInt()