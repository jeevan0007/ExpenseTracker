package com.jeevan.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// FIX: Explicitly set the table name to "expense_table" to match the DAO queries
@Entity(tableName = "expense_table")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val category: String,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val type: String, // "Income" or "Expense"
    val isRecurring: Boolean = false,
    val receiptPath: String? = null
)