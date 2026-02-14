package com.jeevan.expensetracker.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expense_table ORDER BY date DESC")
    fun getAllExpenses(): LiveData<List<Expense>>

    @Query("SELECT SUM(amount) FROM expense_table WHERE type = 'Income'")
    fun getTotalIncome(): LiveData<Double>

    @Query("SELECT SUM(amount) FROM expense_table WHERE type = 'Expense'")
    fun getTotalExpenses(): LiveData<Double>

    // FIX 1: Add missing query for Recurring Worker
    @Query("SELECT * FROM expense_table WHERE isRecurring = 1")
    fun getRecurringExpenses(): List<Expense>

    // FIX 2: Add missing query to prevent duplicate auto-expenses
    @Query("SELECT COUNT(*) FROM expense_table WHERE description = :desc AND category = :category AND date BETWEEN :start AND :end")
    fun checkExpenseExistsThisMonth(desc: String, category: String, start: Long, end: Long): Int

    // FIX 3: Add missing query for Charts (Category totals)
    @Query("SELECT category, SUM(amount) as total FROM expense_table WHERE type = 'Expense' GROUP BY category")
    fun getTotalByCategory(): LiveData<List<CategoryTotal>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Update
    suspend fun update(expense: Expense)
}

// Helper class for Chart Data
data class CategoryTotal(val category: String, val total: Double)