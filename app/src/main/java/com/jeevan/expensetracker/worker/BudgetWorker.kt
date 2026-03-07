package com.jeevan.expensetracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import java.util.Calendar
import java.util.Locale

class BudgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext

        // 1. Get Settings
        val prefs = context.getSharedPreferences("ExpenseTracker", Context.MODE_PRIVATE)
        val monthlyBudget = prefs.getFloat("monthly_budget", 0f).toDouble()
        if (monthlyBudget <= 0) return@withContext Result.success()

        // 2. Calculate Start of Current Month
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfThisMonth = calendar.timeInMillis

        // 3. Fetch & Filter Expenses (Only THIS month's expenses)
        val db = ExpenseDatabase.getDatabase(context)
        val allExpenses = db.expenseDao().getAllExpensesSync()

        val currentMonthSpent = allExpenses.filter {
            it.type == "Expense" && it.date >= startOfThisMonth
        }.sumOf { it.amount }

        // 4. Check Threshold (80%)
        val percentage = (currentMonthSpent / monthlyBudget) * 100
        if (percentage >= 80) {
            sendNotification(context, currentMonthSpent, monthlyBudget, percentage)
        }

        return@withContext Result.success()
    }

    private fun sendNotification(context: Context, spent: Double, limit: Double, percent: Double) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "budget_alerts"

        // Create Channel (Required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when you are nearing or exceeding your monthly budget"
                enableLights(true)
                lightColor = Color.RED
            }
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

        // Open App on Click (Bulletproof intent flags)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dynamic Premium Styling
        val isCritical = percent >= 100
        val title = if (isCritical) "🚨 Budget Exceeded!" else "⚠️ Budget Warning"
        val message = "You have spent $spentStr, which is ${String.format("%.1f", percent)}% of your $limitStr monthly limit."
        val colorTint = if (isCritical) Color.parseColor("#D32F2F") else Color.parseColor("#FF9800")

        val notification = NotificationCompat.Builder(context, channelId)
            // It is highly recommended to replace this with your actual app icon: R.mipmap.ic_launcher_round
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)) // Enables expansion
            .setColor(colorTint) // Tints the icon dynamically
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }
}