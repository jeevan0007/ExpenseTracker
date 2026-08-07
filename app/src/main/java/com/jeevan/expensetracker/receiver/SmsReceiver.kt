package com.jeevan.expensetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.utils.NotificationHelper
import com.jeevan.expensetracker.utils.PaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        private const val PREFS_DEBOUNCE = "ExpenseDebounce"
        private const val DEBOUNCE_WINDOW_MS = 60_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        // Group multipart SMS into a single string to prevent double-parsing
        val body = buildString { messages.forEach { append(it.messageBody ?: "") } }
        val sender = messages.firstOrNull()?.originatingAddress ?: "Unknown"

        val parsedExpense = PaymentParser.parseSms(body, sender) ?: return

        // ── DUPLICATE BLOCKER ────────────────────────────────────────────────
        // FIX: store amount as Long (paise) instead of Float to avoid precision
        // loss on amounts like ₹14,499.50 — Float only has ~7 significant digits,
        // which can cause two different amounts to compare as equal.
        val prefs = context.getSharedPreferences(PREFS_DEBOUNCE, Context.MODE_PRIVATE)
        val lastAmountPaise = prefs.getLong("last_amount_paise", -1L)
        val lastTime        = prefs.getLong("last_time", 0L)
        val lastType        = prefs.getString("last_type", "")
        val currentTime     = System.currentTimeMillis()
        val currentPaise    = (parsedExpense.amount * 100).toLong()

        if (lastAmountPaise == currentPaise
            && lastType == parsedExpense.type
            && (currentTime - lastTime) < DEBOUNCE_WINDOW_MS
        ) {
            Log.d(TAG, "Duplicate SMS ignored (same amount+type within ${DEBOUNCE_WINDOW_MS / 1000}s)")
            return
        }

        prefs.edit()
            .putLong("last_amount_paise", currentPaise)
            .putLong("last_time", currentTime)
            .putString("last_type", parsedExpense.type)
            .apply()

        val finalDescription = parsedExpense.merchant
            .takeIf { it != "Unknown" }
            ?: "Automated ($sender)"

        // goAsync() keeps the process alive while the coroutine runs — without it,
        // the system may kill the receiver before the DB insert completes.
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = ExpenseDatabase.getDatabase(context).expenseDao()

                val smartCategory = dao.predictCategoryForMerchant(finalDescription)
                    ?: PaymentParser.detectCategory(finalDescription)

                val currentTripId = dao.getActiveTrip()?.tripId

                dao.insert(
                    Expense(
                        amount      = parsedExpense.amount,
                        category    = smartCategory,
                        description = finalDescription,
                        type        = parsedExpense.type,
                        isRecurring = false,
                        date        = currentTime,
                        tripId      = currentTripId
                    )
                )

                Log.i(TAG, "LOGGED ₹${parsedExpense.amount} | $finalDescription | $smartCategory")

                NotificationHelper.showExpenseTrackedNotification(
                    context,
                    parsedExpense.amount,
                    finalDescription,
                    smartCategory,
                    parsedExpense.type
                )
            } catch (e: Exception) {
                Log.e(TAG, "Database insert failed: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}