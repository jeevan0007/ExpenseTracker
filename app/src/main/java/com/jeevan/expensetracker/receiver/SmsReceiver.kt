package com.jeevan.expensetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.utils.PaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isEmpty()) return

        val sender = msgs.firstOrNull()?.originatingAddress.orEmpty()
        val fullMessage = msgs.joinToString(separator = "") { it.messageBody.orEmpty() }.trim()
        if (fullMessage.isEmpty()) return

        val looksLikePayment = sender.contains("BANK", ignoreCase = true) ||
            fullMessage.contains("debited", ignoreCase = true) ||
            fullMessage.contains("spent", ignoreCase = true) ||
            fullMessage.contains("paid", ignoreCase = true)

        if (looksLikePayment) {
            parseSms(context, fullMessage)
        }
    }

    private fun parseSms(context: Context, message: String) {
        val parsedExpense = PaymentParser.parseSms(message) ?: return

        val database = ExpenseDatabase.getDatabase(context)
        val description = "SMS: ${parsedExpense.merchant}"

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
