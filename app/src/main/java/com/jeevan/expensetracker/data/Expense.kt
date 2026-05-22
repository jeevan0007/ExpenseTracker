package com.jeevan.expensetracker.data

import androidx.room.Entity
import com.jeevan.expensetracker.utils.ExpenseType
import com.jeevan.expensetracker.utils.RecurrenceType
import androidx.room.PrimaryKey

@Entity(tableName = "expense_table")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val category: String,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val type: String,
    val isRecurring: Boolean = false,
    val recurrenceType: String = RecurrenceType.NONE,
    val receiptPath: String? = null,
    val isDeleted: Boolean = false,
    val deletionDate: Long? = null,

    // --- Billable / Reimbursement Tracking ---
    val isBillable: Boolean = false,
    val clientName: String? = null,
    val isReimbursed: Boolean = false,

    // --- 🔥 NEW: Trip / Project Linking ---
    val tripId: Int? = null // Null means it's just a standard daily expense
)