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

        // 1. Send the raw notification to the Central Brain (PaymentParser)
        val parsedExpense = PaymentParser.parseNotification(packageName, title, text)

        // 2. If the brain successfully found a transaction...
        if (parsedExpense != null) {

            // --- THE 60-SECOND GLOBAL DUPLICATE BLOCKER (SHARED WITH SMS) ---
            val sharedPref = applicationContext.getSharedPreferences("ExpenseDebounce", Context.MODE_PRIVATE)
            val lastAmount = sharedPref.getFloat("last_amount", -1f)
            val lastTime = sharedPref.getLong("last_time", 0L)
            val lastType = sharedPref.getString("last_type", "")
            val currentTime = System.currentTimeMillis()

            // If the exact same amount and type is processed within 60 seconds by either SMS or Notification, ignore it!
            if (lastAmount == parsedExpense.amount.toFloat() && lastType == parsedExpense.type && (currentTime - lastTime) < 60000) {
                Log.d("UpiListener", "Duplicate Notification ignored to prevent double-logging.")
                return // Skip entirely
            }

            // Save this new transaction to memory to block future duplicates
            sharedPref.edit()
                .putFloat("last_amount", parsedExpense.amount.toFloat())
                .putLong("last_time", currentTime)
                .putString("last_type", parsedExpense.type)
                .commit()
            // ---------------------------------------

            try {
                val db = ExpenseDatabase.getDatabase(applicationContext)

                // Truncate description to 30 chars just to be safe
                val finalDescription = parsedExpense.merchant.take(30)

                CoroutineScope(Dispatchers.IO).launch {
                    // --- ON-DEVICE AI PREDICTION ---
                    var smartCategory = db.expenseDao().predictCategoryForMerchant(finalDescription)

                    // If the AI has no history of this merchant, fallback to keyword detection
                    if (smartCategory == null) {
                        smartCategory = detectCategory(finalDescription)
                    }

                    // --- TRIP & PROJECT CHECK ---
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

                    // 🔥 NEW: Trigger the Beautiful UI Notification
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

        // Create the NotificationChannel for Android O and above
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
            .setSmallIcon(R.mipmap.ic_launcher_round) // Using your app's icon
            .setContentTitle(titleText)
            .setContentText("$formattedAmount $actionText $merchant")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Successfully logged $formattedAmount $actionText $merchant under the '$category' category. Your dashboard has been updated!")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // Use a unique ID so multiple back-to-back expenses show separate notifications
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}