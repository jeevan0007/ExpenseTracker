package com.jeevan.expensetracker.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.utils.NotificationHelper
import com.jeevan.expensetracker.utils.PaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class UpiNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "UpiNotificationListener"
        // Shared debounce prefs key (same as SmsReceiver)
        private const val PREFS_DEBOUNCE = "ExpenseDebounce"
        private const val DEBOUNCE_WINDOW_MS = 60_000L
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        // ── GATE 1: Skip ongoing / foreground-service notifications ─────────────
        // These are persistent status bar items (music player, navigation, etc.)
        // and never represent discrete payment events.
        val isOngoing = (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (isOngoing) {
            Log.d(TAG, "SKIPPED (ongoing notification) from $packageName")
            return
        }

        // ── GATE 2: Skip group-summary notifications ──────────────────────────
        // Summary notifications bundle individual alerts and contain no real data.
        val isGroupSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) {
            Log.d(TAG, "SKIPPED (group summary) from $packageName")
            return
        }

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text  = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()  ?: ""

        // ── GATE 3: Skip empty notifications ──────────────────────────────────
        if (title.isBlank() && text.isBlank()) {
            Log.d(TAG, "SKIPPED (empty notification) from $packageName")
            return
        }

        Log.d(TAG, "Evaluating notification from $packageName | title='$title' | text='$text'")

        // ── GATE 4: Hand off to the central PaymentParser ─────────────────────
        // PaymentParser now owns the full allowlist + keyword + amount validation.
        val parsedExpense = PaymentParser.parseNotification(packageName, title, text) ?: return

        // ── GATE 5: 60-SECOND GLOBAL DUPLICATE BLOCKER ────────────────────────
        // Shared with SmsReceiver so a bank SMS and a UPI app notification for the
        // same transaction don't both get logged.
        val sharedPref = applicationContext.getSharedPreferences(PREFS_DEBOUNCE, MODE_PRIVATE)
        val lastAmount = sharedPref.getFloat("last_amount", -1f)
        val lastTime   = sharedPref.getLong("last_time", 0L)
        val lastType   = sharedPref.getString("last_type", "")
        val currentTime = System.currentTimeMillis()

        if (lastAmount == parsedExpense.amount.toFloat()
            && lastType == parsedExpense.type
            && (currentTime - lastTime) < DEBOUNCE_WINDOW_MS
        ) {
            Log.d(TAG, "SKIPPED (duplicate within ${DEBOUNCE_WINDOW_MS / 1000}s) from $packageName")
            return
        }

        sharedPref.edit()
            .putFloat("last_amount", parsedExpense.amount.toFloat())
            .putLong("last_time", currentTime)
            .putString("last_type", parsedExpense.type)
            .apply()

        val finalDescription = parsedExpense.merchant.ifBlank { "UPI Payment" }

        serviceScope.launch {
            try {
                val db = ExpenseDatabase.getDatabase(applicationContext)

                // On-device AI: check prior history for this merchant
                var smartCategory = db.expenseDao().predictCategoryForMerchant(finalDescription)

                // Fallback to keyword detection if no history
                if (smartCategory == null) {
                    smartCategory = PaymentParser.detectCategory(finalDescription)
                }

                // Link to active trip/project space if one is running
                val activeTrip    = db.expenseDao().getActiveTrip()
                val currentTripId = activeTrip?.tripId

                db.expenseDao().insert(
                    Expense(
                        amount       = parsedExpense.amount,
                        category     = smartCategory,
                        description  = finalDescription,
                        type         = parsedExpense.type,
                        isRecurring  = false,
                        date         = currentTime,
                        tripId       = currentTripId
                    )
                )

                Log.i(TAG, "LOGGED ₹${parsedExpense.amount} | $finalDescription | $smartCategory | from $packageName")

                NotificationHelper.showExpenseTrackedNotification(
                    applicationContext,
                    parsedExpense.amount,
                    finalDescription,
                    smartCategory,
                    parsedExpense.type
                )
            } catch (e: Exception) {
                Log.e(TAG, "Database insert failed: ${e.message}", e)
            }
        }
    }
}