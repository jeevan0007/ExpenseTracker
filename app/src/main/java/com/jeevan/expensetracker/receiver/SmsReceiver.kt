package com.jeevan.expensetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.messageBody
                val sender = sms.originatingAddress ?: ""

                // 1. Analyze Content (We accept ANY sender if the content looks like a bank alert)
                // This fixes the issue where "G1" or personal contacts were ignored.
                parseBankSms(context, body, sender)
            }
        }
    }

    private fun parseBankSms(context: Context, body: String, sender: String) {
        val lowerBody = body.lowercase()

        // 2. Filter: Must contain financial keywords
        if (!lowerBody.contains("debited") &&
            !lowerBody.contains("credited") &&
            !lowerBody.contains("paid") &&
            !lowerBody.contains("spent") &&
            !lowerBody.contains("sent to")) {
            return
        }

        // 3. Universal Regex for Money
        // Matches: "Rs.500", "Rs. 500", "INR 500", "INR500", "Rs 5,000.00"
        // Explanation:
        // (?i) -> Case insensitive
        // (?:rs\.?|inr) -> Match "rs", "rs.", or "inr"
        // \s* -> Match zero or more spaces (The Critical Fix for your "Rs.5052" case)
        // ([\d,]+(?:\.\d+)?) -> Capture digits, commas, and optional decimals
        val amountRegex = Pattern.compile("(?i)(?:rs\\.?[\\s]*|inr[\\s]*)(?i)(?:[\\s]*)([0-9,]+(?:\\.[0-9]+)?)")
        val matcher = amountRegex.matcher(body)

        if (matcher.find()) {
            try {
                // Clean amount (remove commas)
                val amountStr = matcher.group(1)?.replace(",", "") ?: return
                val amount = amountStr.toDouble()

                // 4. Detect Transaction Type
                val type = if (lowerBody.contains("credited") || lowerBody.contains("received")) {
                    "Income"
                } else {
                    "Expense"
                }

                // 5. Extract Merchant Name (Smart Guessing)
                // Looks for "to <Name>" pattern common in UPI messages
                var description = "Automated ($sender)"
                val merchantRegex = Pattern.compile("(?i)\\bto\\s+([a-zA-Z0-9\\s]+?)(?:,|\\.|\\sUPI|\\sRef|$)")
                val merchantMatcher = merchantRegex.matcher(body)
                if (merchantMatcher.find()) {
                    val merchant = merchantMatcher.group(1)?.trim()
                    if (!merchant.isNullOrEmpty()) {
                        description = merchant // e.g., "SWIGGY", "ZOMATO"
                    }
                } else if (lowerBody.contains("swiggy")) {
                    description = "Swiggy"
                } else if (lowerBody.contains("zomato")) {
                    description = "Zomato"
                }

                // 6. Save to Database
                val db = ExpenseDatabase.getDatabase(context)
                CoroutineScope(Dispatchers.IO).launch {
                    db.expenseDao().insert(
                        Expense(
                            amount = amount,
                            category = detectCategory(description), // Auto-categorize!
                            description = description,
                            type = type,
                            isRecurring = false,
                            date = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Parsing error: ${e.message}")
            }
        }
    }

    // Helper: Auto-assign category based on keywords
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