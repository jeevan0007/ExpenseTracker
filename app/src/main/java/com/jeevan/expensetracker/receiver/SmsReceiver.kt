package com.jeevan.expensetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in msgs) {
                val messageBody = sms.messageBody
                val sender = sms.originatingAddress ?: ""

                // Simple check: Is this a bank SMS?
                if (sender.contains("BANK", ignoreCase = true) || messageBody.contains("debited", ignoreCase = true)) {
                    parseSms(context, messageBody)
                }
            }
        }
    }

    private fun parseSms(context: Context, message: String) {
        // Regex to find amounts like "Rs. 500" or "INR 500.00"
        val pattern = Pattern.compile("(?i)(?:Rs\\.?|INR)\\s*(\\d+(?:\\.\\d{1,2})?)")
        val matcher = pattern.matcher(message)

        if (matcher.find()) {
            val amountString = matcher.group(1)
            val amount = amountString?.toDoubleOrNull()

            if (amount != null) {
                val database = ExpenseDatabase.getDatabase(context)
                CoroutineScope(Dispatchers.IO).launch {
                    database.expenseDao().insert(
                        Expense(
                            amount = amount,
                            category = "Other",
                            description = "SMS: $message", // Truncate if too long in real app
                            type = "Expense", // FIX: Explicitly set type
                            isRecurring = false
                        )
                    )
                }
            }
        }
    }
}