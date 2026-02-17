package com.jeevan.expensetracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jeevan.expensetracker.MainActivity
import com.jeevan.expensetracker.R
import com.jeevan.expensetracker.data.ExpenseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class BudgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext

        // 1. Get Settings
        val prefs = context.getSharedPreferences("ExpenseTracker", Context.MODE_PRIVATE)
        val monthlyBudget = prefs.getFloat("monthly_budget", 0f).toDouble()
        if (monthlyBudget <= 0) return@withContext Result.success()

        // 2. Calculate Total Expenses (This Month)
        val db = ExpenseDatabase.getDatabase(context)
        // Note: For simplicity, we are summing ALL expenses.
        // Ideally, you'd filter by month, but this matches your current Dashboard logic.
        val expenses = db.expenseDao().getAllExpensesSync()
        val totalSpent = expenses.filter { it.type == "Expense" }.sumOf { it.amount }

        // 3. Check Threshold (80%)
        val percentage = (totalSpent / monthlyBudget) * 100
        if (percentage >= 80) {
            sendNotification(context, totalSpent, monthlyBudget, percentage)
        }

        return@withContext Result.success()
    }

    private fun sendNotification(context: Context, spent: Double, limit: Double, percent: Double) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "budget_alerts"

        // Create Channel (Required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Budget Alerts", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        // Get Saved Currency for Display
        val prefs = context.getSharedPreferences("ExpenseTracker", Context.MODE_PRIVATE)
        val rate = prefs.getFloat("currency_rate", 1.0f).toDouble()
        val lang = prefs.getString("currency_lang", "en") ?: "en"
        val country = prefs.getString("currency_country", "IN") ?: "IN"
        val format = NumberFormat.getCurrencyInstance(Locale(lang, country))

        // Format Amounts
        val spentStr = format.format(spent * rate)
        val limitStr = format.format(limit * rate)

        // Open App on Click
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val title = if (percent >= 100) "🚨 Budget Exceeded!" else "⚠️ Budget Warning"
        val message = "You've spent $spentStr of your $limitStr limit."

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Or your app icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }
}