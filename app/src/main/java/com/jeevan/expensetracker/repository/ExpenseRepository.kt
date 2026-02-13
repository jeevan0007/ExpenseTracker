package com.jeevan.expensetracker.repository

import androidx.lifecycle.LiveData
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDao

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    val allExpenses: LiveData<List<Expense>> = expenseDao.getAllExpenses()
    val totalExpenses: LiveData<Double> = expenseDao.getTotalExpenses()
    val totalIncome: LiveData<Double> = expenseDao.getTotalIncome() // NEW

    suspend fun insert(expense: Expense) {
        expenseDao.insert(expense)
    }

    suspend fun update(expense: Expense) {
        expenseDao.update(expense)
    }

    suspend fun delete(expense: Expense) {
        expenseDao.delete(expense)
    }

    fun getTotalByCategory(category: String): LiveData<Double> {
        return expenseDao.getTotalByCategory(category)
    }
}