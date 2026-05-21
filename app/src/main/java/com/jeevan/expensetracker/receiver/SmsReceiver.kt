package com.jeevan.expensetracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jeevan.expensetracker.R
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.utils.PaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            val smsBodyBuilder = StringBuilder()
            var sender = "Unknown"
            for (sms in messages) {
                smsBodyBuilder.append(sms.messageBody ?: "")
                sender = sms.originatingAddress ?: "Unknown"
            }
            val body = smsBodyBuilder.toString()

            val parsedExpense = PaymentParser.parseSms(body, sender)

            if (parsedExpense != null) {
                val finalDescription = if (parsedExpense.merchant == "Unknown") {
                    "Automated ($sender)"
                } else {
                    parsedExpense.merchant
                }

                // --- 🔥 FIX: COMPOSITE TRANSACTION FINGERPRINT DEBOUNCER ---
                val sharedPref = context.getSharedPreferences("ExpenseDebounce", Context.MODE_PRIVATE)
                val currentTime = System.currentTimeMillis()

                // Construct a unique key using amount, classification, and specific merchant tracking info
                val cleanMerchantId = finalDescription.lowercase().replace("\\s+".toRegex(), "")
                val fingerprintKey = "tx_${parsedExpense.amount}_${parsedExpense.type}_$cleanMerchantId"

                val lastTimeForFingerprint = sharedPref.getLong(fingerprintKey, 0L)

                if ((currentTime - lastTimeForFingerprint) < 60000) {
                    Log.d("SmsReceiver", "Duplicate fingerprint detected ($fingerprintKey). Transaction ignored.")
                    return
                }

                // Synchronously lock this unique fingerprint to block simultaneous bank alerts
                sharedPref.edit()
                    .putLong(fingerprintKey, currentTime)
                    .commit()
                // ------------------------------------------------------------

                try {
                    val db = ExpenseDatabase.getDatabase(context)

                    CoroutineScope(Dispatchers.IO).launch {
                        var smartCategory = db.expenseDao().predictCategoryForMerchant(finalDescription)

                        if (smartCategory == null) {
                            smartCategory = detectCategory(finalDescription)
                        }

                        val activeTrip = db.expenseDao().getActiveTrip()
                        val currentTripId = activeTrip?.tripId

                        db.expenseDao().insert(
                            Expense(
                                amount = parsedExpense.amount,
                                category = smartCategory,
                                description = finalDescription,
                                type = parsedExpense.type,
                                isRecurring = false,
                                date = System.currentTimeMillis(),
                                tripId = currentTripId
                            )
                        )

                        showSuccessNotification(context, parsedExpense.amount, finalDescription, smartCategory, parsedExpense.type)
                    }
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Database error: ${e.message}")
                }
            }
        }
    }

    private fun detectCategory(desc: String): String {
        val d = desc.lowercase()
        return when {
            d.contains("swiggy") || d.contains("zomato") || d.contains("food") -> "Food"
            d.contains("uber") || d.contains("ola") || d.contains("fuel") || d.contains("petrol") -> "Transport"
            d.contains("jio") || d.contains("airtel") || d.contains("bill") || d.contains("netflix") -> "Bills"
            d.contains("amazon") || d.contains("flipkart") || d.contains("myntra") -> "Shopping"
            else -> "Automated"
        }
    }

    private fun showSuccessNotification(context: Context, amount: Double, merchant: String, category: String, type: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "expense_logged_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Expense Logged Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for successfully tracked expenses"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val formattedAmount = "₹%.2f".format(amount)
        val actionText = if (type.lowercase() == "income") "received from" else "spent on"
        val titleText = if (type.lowercase() == "income") "💰 Income Tracked!" else "💸 Expense Tracked!"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(titleText)
            .setContentText("$formattedAmount $actionText $merchant")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Successfully logged $formattedAmount $actionText $merchant under the '$category' category. Your dashboard has been updated!")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}