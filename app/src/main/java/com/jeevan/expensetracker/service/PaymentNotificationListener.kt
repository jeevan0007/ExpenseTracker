package com.jeevan.expensetracker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class PaymentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val text = extras.getString("android.text") ?: ""
        val title = extras.getString("android.title") ?: ""
        val content = "$title $text"

        // Check for GPay, PhonePe, Paytm
        if (packageName.contains("google.android.apps.nbu.paisa.user") ||
            packageName.contains("phonepe") ||
            packageName.contains("paytm")) {

            if (content.contains("paid", ignoreCase = true) || content.contains("sent", ignoreCase = true)) {
                parseNotification(content)
            }
        }
    }

    private fun parseNotification(content: String) {
        val pattern = Pattern.compile("(?i)(?:Rs\\.?|₹)\\s*(\\d+(?:\\.\\d{1,2})?)")
        val matcher = pattern.matcher(content)

        if (matcher.find()) {
            val amount = matcher.group(1)?.toDoubleOrNull()
            if (amount != null) {
                val database = ExpenseDatabase.getDatabase(applicationContext)
                CoroutineScope(Dispatchers.IO).launch {
                    database.expenseDao().insert(
                        Expense(
                            amount = amount,
                            category = "Other",
                            description = "UPI: $content",
                            type = "Expense", // FIX: Explicitly set type
                            isRecurring = false
                        )
                    )
                }
            }
        }
    }
}