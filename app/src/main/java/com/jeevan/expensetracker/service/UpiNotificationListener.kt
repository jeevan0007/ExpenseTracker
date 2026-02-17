package com.jeevan.expensetracker.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class UpiNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val packageName = sbn?.packageName ?: return
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: "" // Case sensitive for regex
        val fullContent = "$title $text"

        // 1. Filter for Payment Apps (GPay, PhonePe, Paytm, Cred, Amazon Pay)
        val validApps = listOf(
            "com.phonepe.app",
            "com.google.android.apps.nbu.paisa.user",
            "net.one97.paytm",
            "com.dreamplug.androidapp" // Cred
        )
        if (packageName !in validApps) return

        // 2. Parse Logic
        parseUpiContent(fullContent)
    }

    private fun parseUpiContent(content: String) {
        val lowerContent = content.lowercase()

        // Filter: Must be money related
        if (!lowerContent.contains("paid") &&
            !lowerContent.contains("sent") &&
            !lowerContent.contains("debited") &&
            !lowerContent.contains("received")) {
            return
        }

        // 3. Universal Regex (Same strict pattern as SmsReceiver)
        val p = Pattern.compile("(?i)(?:rs\\.?[\\s]*|inr[\\s]*|₹[\\s]*)([0-9,]+(?:\\.[0-9]+)?)")
        val m = p.matcher(content)

        if (m.find()) {
            try {
                val amountStr = m.group(1)?.replace(",", "") ?: return
                val amount = amountStr.toDouble()

                // Income or Expense?
                val type = if (lowerContent.contains("received") || lowerContent.contains("credited")) "Income" else "Expense"

                // Clean Description
                var description = "UPI Transaction"
                if (content.contains("to ")) {
                    description = content.substringAfter("to ").substringBefore(" using").substringBefore(" on")
                } else if (content.contains("from ")) {
                    description = content.substringAfter("from ").substringBefore(" using")
                }

                val db = ExpenseDatabase.getDatabase(applicationContext)
                CoroutineScope(Dispatchers.IO).launch {
                    db.expenseDao().insert(
                        Expense(
                            amount = amount,
                            category = detectCategory(description),
                            description = description.take(30), // Truncate if too long
                            type = type,
                            isRecurring = false,
                            date = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("UPIListener", "Error: ${e.message}")
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
}