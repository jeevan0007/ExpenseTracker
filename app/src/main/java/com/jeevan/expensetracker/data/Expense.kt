package com.jeevan.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense_table")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val category: String,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val type: String, // "Income" or "Expense"
    val isRecurring: Boolean = false, // We keep this so we don't break your existing logic
    val recurrenceType: String = "None", // 🔥 NEW: Can be "Monthly", "Yearly", or "None"
    val receiptPath: String? = null,
    val isDeleted: Boolean = false,
    val deletionDate: Long? = null
)