package com.jeevan.expensetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.utils.PaymentParser // NEW: Import the Brain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.messageBody ?: ""
                val sender = sms.originatingAddress ?: "Unknown"

                // 1. Send the raw message to the Central Brain (PaymentParser)
                val parsedExpense = PaymentParser.parseSms(body)

                // 2. If the brain successfully extracted money and merchant...
                if (parsedExpense != null) {
                    try {
                        val db = ExpenseDatabase.getDatabase(context)

                        // Fallback description if the parser returns "Unknown"
                        val finalDescription = if (parsedExpense.merchant == "Unknown") {
                            "Automated ($sender)"
                        } else {
                            parsedExpense.merchant
                        }

                        CoroutineScope(Dispatchers.IO).launch {
                            db.expenseDao().insert(
                                Expense(
                                    amount = parsedExpense.amount,
                                    category = detectCategory(finalDescription), // Keep your smart auto-categorizer!
                                    description = finalDescription,
                                    type = parsedExpense.type, // Uses the smart Income/Expense detection!
                                    isRecurring = false,
                                    date = System.currentTimeMillis()
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Database error: ${e.message}")
                    }
                }
            }
        }
    }

    // Your existing smart auto-categorizer
    private fun detectCategory(desc: String): String {
        val d = desc.lowercase()
        return when {
            d.contains("swiggy") || d.contains("zomato") || d.contains("food") -> "Food"
            d.contains("uber") || d.contains("ola") || d.contains("fuel") || d.contains("petrol") -> "Transport"
            d.contains("jio") || d.contains("airtel") || d.contains("bill") || d.contains("netflix") -> "Bills"
            d.contains("amazon") || d.contains("flipkart") -> "Shopping"
            else -> "Automated"
        }
    }
}