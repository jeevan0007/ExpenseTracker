package com.jeevan.expensetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.utils.PaymentParser
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

                    // --- THE 60-SECOND DUPLICATE BLOCKER ---
                    val sharedPref = context.getSharedPreferences("SmsDebounce", Context.MODE_PRIVATE)
                    val lastAmount = sharedPref.getFloat("last_amount", -1f)
                    val lastTime = sharedPref.getLong("last_time", 0L)
                    val currentTime = System.currentTimeMillis()

                    // If the exact same amount is processed within 60 seconds, ignore it!
                    if (lastAmount == parsedExpense.amount.toFloat() && (currentTime - lastTime) < 60000) {
                        Log.d("SmsReceiver", "Duplicate Bank SMS ignored to prevent double-logging.")
                        continue // Skip to the next SMS
                    }

                    // Save this new transaction to memory to block future duplicates
                    sharedPref.edit()
                        .putFloat("last_amount", parsedExpense.amount.toFloat())
                        .putLong("last_time", currentTime)
                        .apply()
                    // ---------------------------------------

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
                                    category = detectCategory(finalDescription),
                                    description = finalDescription,
                                    type = parsedExpense.type,
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

    private fun detectCategory(desc: String): String {
        val d = desc.lowercase()
        return when {
            d.contains("swiggy") || d.contains("zomato") || d.contains("food") -> "Food"
            d.contains("uber") || d.contains("ola") || d.contains("fuel") || d.contains("petrol") -> "Transport"
            d.contains("jio") || d.contains("airtel") || d.contains("bill") || d.contains("netflix") -> "Bills"
            d.contains("amazon") || d.contains("flipkart") || d.contains("myntra") -> "Shopping"
            else -> "Automated"
        }
    }
}