package com.jeevan.expensetracker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.utils.PaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val text = extras.getString("android.text") ?: ""
        val title = extras.getString("android.title") ?: ""

        val parsedExpense = PaymentParser.parseNotification(packageName, title, text) ?: return
        val description = "UPI: ${parsedExpense.merchant.ifBlank { "$title $text".trim() }}"

        val database = ExpenseDatabase.getDatabase(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            val now = System.currentTimeMillis()
            val duplicateCount = database.expenseDao().countRecentAutoExpense(
                amount = parsedExpense.amount,
                description = description,
                since = now - 2 * 60 * 1000
            )

            if (duplicateCount == 0) {
                database.expenseDao().insert(
                    Expense(
                        amount = parsedExpense.amount,
                        category = "Other",
                        description = description,
                        type = "Expense",
                        isRecurring = false
                    )
                )
            }
        }
    }
}
