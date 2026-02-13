package com.jeevan.expensetracker.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): LiveData<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE type = 'Expense'")
    fun getTotalExpenses(): LiveData<Double>

    @Query("SELECT SUM(amount) FROM expenses WHERE type = 'Income'")
    fun getTotalIncome(): LiveData<Double>

    @Query("SELECT SUM(amount) FROM expenses WHERE category = :category AND type = 'Expense'")
    fun getTotalByCategory(category: String): LiveData<Double>

    // NEW: Get all recurring subscriptions
    @Query("SELECT * FROM expenses WHERE isRecurring = 1")
    suspend fun getRecurringExpenses(): List<Expense>

    // NEW: Check if this month's subscription was already paid
    @Query("SELECT COUNT(*) FROM expenses WHERE description = :description AND amount = :amount AND date >= :startOfMonth")
    suspend fun checkExpenseExistsThisMonth(description: String, amount: Double, startOfMonth: Long): Int
}