package com.jeevan.expensetracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.data.ExpenseRepository
import com.jeevan.expensetracker.utils.ExpenseType
import com.jeevan.expensetracker.utils.RecurrenceType
import java.util.Calendar

class RecurringExpenseWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = ExpenseDatabase.getDatabase(applicationContext)
        val repository = ExpenseRepository(database.expenseDao())

        // 1. Get all recurring expenses
        val recurringExpenses = repository.getRecurringExpenses()

        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()

        // --- 2. CALCULATE MONTHLY BOUNDARIES ---
        calendar.timeInMillis = now
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfMonth = calendar.timeInMillis

        // --- 3. CALCULATE YEARLY BOUNDARIES ---
        calendar.timeInMillis = now
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfYear = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_YEAR, calendar.getActualMaximum(Calendar.DAY_OF_YEAR))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfYear = calendar.timeInMillis

        // --- 4. PROCESS EACH EXPENSE ---
        for (expense in recurringExpenses) {

            // Determine period: Yearly or Monthly (default).
            val isYearly = expense.recurrenceType == RecurrenceType.YEARLY

            val startCheck = if (isYearly) startOfYear else startOfMonth
            val endCheck = if (isYearly) endOfYear else endOfMonth

            val exists = repository.checkExpenseExistsThisMonth(
                desc = expense.description,
                category = expense.category,
                start = startCheck,
                end = endCheck
            )

            // If it hasn't been paid in this period, generate a fresh copy for today!
            if (exists == 0) {
                val newExpense = expense.copy(
                    id = 0, // Room auto-generates a new primary key when id is 0
                    date = now
                )
                repository.insert(newExpense)
            }
        }

        return Result.success()
    }
}