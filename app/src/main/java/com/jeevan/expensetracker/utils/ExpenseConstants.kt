package com.jeevan.expensetracker.utils

/**
 * Single source of truth for all magic strings used across the app.
 * Replacing raw string comparisons ("Income", "Expense" etc.) with these
 * constants means a typo becomes a compile error instead of a silent bug.
 */
object ExpenseType {
    const val INCOME  = "Income"
    const val EXPENSE = "Expense"
}

object RecurrenceType {
    const val NONE    = "None"
    const val MONTHLY = "Monthly"
    const val YEARLY  = "Yearly"
}