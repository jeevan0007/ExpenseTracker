package com.jeevan.expensetracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

            // Determine if it is Yearly or Monthly.
            // (Assuming you add a recurrenceType string to your Expense data class)
            val isYearly = expense.recurrenceType == "Yearly"

            val startCheck = if (isYearly) startOfYear else startOfMonth
            val endCheck = if (isYearly) endOfYear else endOfMonth

            // Note: You may want to rename 'checkExpenseExistsThisMonth' in your DAO to
            // 'checkExpenseExistsInPeriod' since it now handles both months and years!
            val exists = repository.checkExpenseExistsThisMonth(
                desc = expense.description,
                category = expense.category,
                start = startCheck,
                end = endCheck
            )

            // If it hasn't been paid in this period, generate a fresh copy for today!
            if (exists == 0) {
                val newExpense = expense.copy(
                    id = 0, // 0 ID tells Room Database to generate a NEW unique ID
                    date = now
                )
                repository.insert(newExpense)
            }
        }

        return Result.success()
    }
}