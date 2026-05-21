package com.jeevan.expensetracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jeevan.expensetracker.R
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.utils.PaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UpiNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val packageName = sbn?.packageName ?: return
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""

        val parsedExpense = PaymentParser.parseNotification(packageName, title, text)

        if (parsedExpense != null) {
            val finalDescription = parsedExpense.merchant.take(30)

            // --- 🔥 FIX: COMPOSITE TRANSACTION FINGERPRINT DEBOUNCER (SHARED GLOBAL SPACE) ---
            val sharedPref = applicationContext.getSharedPreferences("ExpenseDebounce", Context.MODE_PRIVATE)
            val currentTime = System.currentTimeMillis()

            // Generate matching key signatures ensuring cross-ingestion sources evaluate identically
            val cleanMerchantId = finalDescription.lowercase().replace("\\s+".toRegex(), "")
            val fingerprintKey = "tx_${parsedExpense.amount}_${parsedExpense.type}_$cleanMerchantId"

            val lastTimeForFingerprint = sharedPref.getLong(fingerprintKey, 0L)

            if ((currentTime - lastTimeForFingerprint) < 60000) {
                Log.d("UpiListener", "Duplicate fingerprint rejected via notification source ($fingerprintKey).")
                return
            }

            // Immediately block additional arriving streams
            sharedPref.edit()
                .putLong(fingerprintKey, currentTime)
                .commit()
            // ---------------------------------------------------------------------------------

            try {
                val db = ExpenseDatabase.getDatabase(applicationContext)

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

                    showSuccessNotification(applicationContext, parsedExpense.amount, finalDescription, smartCategory, parsedExpense.type)
                }
            } catch (e: Exception) {
                Log.e("UPIListener", "Database error: ${e.message}")
            }
        }
    }

    private fun detectCategory(desc: String): String {
        val d = desc.lowercase()
        return when {
            d.contains("swiggy") || d.contains("zomato") || d.contains("pizza") -> "Food"
            d.contains("uber") || d.contains("ola") || d.contains("rapido") -> "Transport"
            d.contains("bescom") || d.contains("bill") || d.contains("recharge") -> "Bills"
            d.contains("mart") || d.contains("store") -> "Shopping"
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