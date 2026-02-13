package com.jeevan.expensetracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import java.util.Calendar

class RecurringExpenseWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = ExpenseDatabase.getDatabase(applicationContext).expenseDao()
        val recurringExpenses = dao.getRecurringExpenses()

        if (recurringExpenses.isEmpty()) return Result.success()

        val todayCalendar = Calendar.getInstance()
        val currentDayOfMonth = todayCalendar.get(Calendar.DAY_OF_MONTH)

        // Find exactly when this current month started so we can check if it was paid already
        val startOfMonthCalendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = startOfMonthCalendar.timeInMillis

        for (expense in recurringExpenses) {
            // Find out what day of the month the original subscription was bought
            val expenseCalendar = Calendar.getInstance().apply { timeInMillis = expense.date }
            val originalBillingDay = expenseCalendar.get(Calendar.DAY_OF_MONTH)

            // Handle short months (e.g., If billing is on the 31st, but it's February, bill on the 28th)
            val maxDaysThisMonth = todayCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val targetBillingDay = if (originalBillingDay > maxDaysThisMonth) maxDaysThisMonth else originalBillingDay

            // Only add the expense if today is the billing day (or past it)
            if (currentDayOfMonth >= targetBillingDay) {

                // Check if we already paid it this month
                val count = dao.checkExpenseExistsThisMonth(expense.description, expense.amount, startOfMonth)

                if (count == 0) {
                    val newExpense = Expense(
                        amount = expense.amount,
                        category = expense.category,
                        description = expense.description,
                        type = expense.type,
                        date = System.currentTimeMillis(), // Logs it for today!
                        isRecurring = true
                    )
                    dao.insert(newExpense)
                }
            }
        }

        return Result.success()
    }
}