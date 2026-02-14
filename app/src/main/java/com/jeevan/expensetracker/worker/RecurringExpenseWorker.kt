package com.jeevan.expensetracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.data.ExpenseRepository
import java.util.Calendar

class RecurringExpenseWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = ExpenseDatabase.getDatabase(applicationContext)
        val repository = ExpenseRepository(database.expenseDao())

        // 1. Get all recurring expenses (Subscriptions, Rent, etc.)
        val recurringExpenses = repository.getRecurringExpenses()

        // 2. Define the "Current Month" range
        val calendar = Calendar.getInstance()

        // Start of this month (e.g., Feb 1st 00:00:00)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfMonth = calendar.timeInMillis

        // End of this month (e.g., Feb 28th 23:59:59)
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfMonth = calendar.timeInMillis

        for (expense in recurringExpenses) {
            // FIX: Pass the correct 4 parameters:
            // 1. Description (String)
            // 2. Category (String) - previously you might have passed amount (Double) here by mistake
            // 3. Start Date (Long)
            // 4. End Date (Long)

            val exists = repository.checkExpenseExistsThisMonth(
                desc = expense.description,
                category = expense.category,
                start = startOfMonth,
                end = endOfMonth
            )

            // If it hasn't been paid this month, add it now
            if (exists == 0) {
                val newExpense = expense.copy(
                    id = 0, // 0 ID tells Room to generate a NEW unique ID
                    date = System.currentTimeMillis() // Set date to Today
                )
                repository.insert(newExpense)
            }
        }

        return Result.success()
    }
}