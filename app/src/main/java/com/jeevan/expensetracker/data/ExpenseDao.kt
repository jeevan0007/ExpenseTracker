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

    @Query("SELECT SUM(amount) FROM expenses")
    fun getTotalExpenses(): LiveData<Double>

    @Query("SELECT SUM(amount) FROM expenses WHERE category = :category")
    fun getTotalByCategory(category: String): LiveData<Double>
}