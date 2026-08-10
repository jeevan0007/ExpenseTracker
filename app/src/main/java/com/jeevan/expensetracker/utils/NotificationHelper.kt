package com.jeevan.expensetracker.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jeevan.expensetracker.R

/**
 * Central notification helper — single source of truth for all
 * auto-tracked expense notifications fired by SmsReceiver and
 * UpiNotificationListener. Any future style changes (icon, channel
 * priority, copy) only need to be made here.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "expense_logged_v2" // Updated ID to force sound update
    private const val CHANNEL_NAME = "Expense Logged Alerts"
    private const val CHANNEL_DESC = "Notifications for successfully tracked expenses"

    fun showExpenseTrackedNotification(
        context: Context,
        amount: Double,
        merchant: String,
        category: String,
        type: String
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.mario_coin}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { 
                description = CHANNEL_DESC
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val formattedAmount = "₹%.2f".format(amount)
        val isIncome = type.lowercase() == "income"
        val actionText = if (isIncome) "received from" else "spent on"
        val titleText = if (isIncome) "💰 Income Tracked!" else "💸 Expense Tracked!"

        val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(titleText)
            .setContentText("$formattedAmount $actionText $merchant")
            .setSound(soundUri) // For backward compatibility (Pre-Oreo)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Successfully logged $formattedAmount $actionText $merchant " +
                                "under the '$category' category. Your dashboard has been updated!"
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}