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
import com.jeevan.expensetracker.utils.loadSavedCurrency
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class BudgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = context.getSharedPreferences("ExpenseTracker", Context.MODE_PRIVATE)

        val monthlyBudget = prefs.getFloat("monthly_budget", 0f).toDouble()
        if (monthlyBudget <= 0) return Result.success()

        // Start-of-month timestamp — used as the lower bound in the SQL query
        val startOfThisMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Single targeted SQL query — filters by date, type, and reimbursement status
        // at the database level. Previously called getAllExpensesSync() which loaded
        // the entire expense table into memory before filtering in Kotlin.
        val currentMonthSpent = ExpenseDatabase
            .getDatabase(context)
            .expenseDao()
            .getTotalSpentThisMonth(startOfThisMonth)

        val percentage = (currentMonthSpent / monthlyBudget) * 100
        if (percentage >= 80) {
            sendNotification(context, currentMonthSpent, monthlyBudget, percentage)
        }

        return Result.success()
    }

    private fun sendNotification(
        context: Context,
        spent: Double,
        limit: Double,
        percent: Double
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "budget_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Budget Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts when you are nearing or exceeding your monthly budget"
                    enableLights(true)
                    lightColor = Color.RED
                }
            )
        }

        val saved   = context.loadSavedCurrency()
        val format  = NumberFormat.getCurrencyInstance(saved.locale)

        val isCritical = percent >= 100
        val title   = if (isCritical) "🚨 Budget Exceeded!" else "⚠️ Budget Warning"
        val message = "You have spent ${format.format(spent * saved.rate)}, which is " +
                "${String.format("%.1f", percent)}% of your ${format.format(limit * saved.rate)} monthly limit."
        val color   = if (isCritical) Color.parseColor("#D32F2F") else Color.parseColor("#FF9800")

        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        manager.notify(
            1001,
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setColor(color)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        )
    }
}