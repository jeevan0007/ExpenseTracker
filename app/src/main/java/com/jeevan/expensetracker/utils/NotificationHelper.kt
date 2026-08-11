package com.jeevan.expensetracker.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jeevan.expensetracker.R

/**
 * Central notification helper — single source of truth for all
 * auto-tracked expense notifications fired by SmsReceiver and
 * UpiNotificationListener.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "expense_logged_v3" // Updated for style changes
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
                enableLights(true)
                lightColor = Color.GREEN
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val formattedAmount = "₹%.2f".format(amount)
        val isIncome = type.lowercase() == "income"
        
        // 🎨 Color & Emoji logic
        val accentColor = ContextCompat.getColor(
            context, 
            if (isIncome) R.color.income_600 else R.color.expense_600
        )
        
        val categoryEmoji = getEmojiForCategory(category)
        val titleText = if (isIncome) "$categoryEmoji Income Received!" else "$categoryEmoji Expense Tracked!"
        val contentText = if (isIncome) {
            "Received $formattedAmount from $merchant"
        } else {
            "Spent $formattedAmount on $merchant"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_save) // Use a cleaner icon for the status bar
            .setColor(accentColor)
            .setColorized(true) // Makes the notification look "Premium"
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setSound(soundUri)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Transaction logged successfully!\n" +
                        "💰 Amount: $formattedAmount\n" +
                        "🏢 Merchant: $merchant\n" +
                        "🏷️ Category: $category"
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_PROMO)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun getEmojiForCategory(category: String): String {
        return when (category.lowercase()) {
            "food" -> "🍔"
            "transport" -> "🚗"
            "shopping" -> "🛍️"
            "entertainment" -> "🎬"
            "bills" -> "💡"
            "healthcare" -> "🏥"
            "salary" -> "💵"
            else -> "✨"
        }
    }
}
