package com.jeevan.expensetracker.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.utils.PaymentParser // NEW: Import the Brain
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
        // Notice we don't need to check valid packages here anymore, the Parser handles it!
        val parsedExpense = PaymentParser.parseNotification(packageName, title, text)

        // 2. If the brain successfully found a transaction...
        if (parsedExpense != null) {
            try {
                val db = ExpenseDatabase.getDatabase(applicationContext)

                // Truncate description to 30 chars just to be safe
                val finalDescription = parsedExpense.merchant.take(30)

                CoroutineScope(Dispatchers.IO).launch {
                    // --- 🔥 ON-DEVICE AI PREDICTION ---
                    var smartCategory = db.expenseDao().predictCategoryForMerchant(finalDescription)

                    // If the AI has no history of this merchant, fallback to keyword detection
                    if (smartCategory == null) {
                        smartCategory = detectCategory(finalDescription)
                    }

                    // --- 🔥 NEW: TRIP & PROJECT CHECK ---
                    // Find out if the user is currently on an active trip
                    val activeTrip = db.expenseDao().getActiveTrip()
                    val currentTripId = activeTrip?.tripId

                    db.expenseDao().insert(
                        Expense(
                            amount = parsedExpense.amount,
                            category = smartCategory, // Uses the AI Prediction!
                            description = finalDescription,
                            type = parsedExpense.type, // Income or Expense
                            isRecurring = false,
                            date = System.currentTimeMillis(),
                            tripId = currentTripId // 🔥 Attaches to the trip (or null if home)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("UPIListener", "Database error: ${e.message}")
            }
        }
    }

    // Your existing smart auto-categorizer
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
}