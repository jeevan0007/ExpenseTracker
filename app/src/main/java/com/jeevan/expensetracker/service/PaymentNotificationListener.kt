package com.jeevan.expensetracker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.utils.PaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class PaymentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        val parsed = PaymentParser.parseNotification(packageName, title, text)
        if (parsed != null) {
            saveExpenseToDb(parsed.amount, "Auto: ${parsed.merchant} (${parsed.source})")
        }
    }

    private fun saveExpenseToDb(amount: Double, description: String) {
        val dao = ExpenseDatabase.getDatabase(applicationContext).expenseDao()
        CoroutineScope(Dispatchers.IO).launch {
            val expense = Expense(
                amount = amount,
                category = "Automated", // Sets a default category
                description = description,
                date = Date().time
            )
            dao.insert(expense)
        }
    }
}