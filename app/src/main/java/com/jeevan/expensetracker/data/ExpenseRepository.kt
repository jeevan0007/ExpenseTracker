package com.jeevan.expensetracker.data

import androidx.lifecycle.LiveData

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    val allExpenses: LiveData<List<Expense>> = expenseDao.getAllExpenses()
    val totalIncome: LiveData<Double> = expenseDao.getTotalIncome()
    val totalExpenses: LiveData<Double> = expenseDao.getTotalExpenses()

    // FIX: Add the missing function for Charts
    fun getTotalByCategory(): LiveData<List<CategoryTotal>> {
        return expenseDao.getTotalByCategory()
    }

    // FIX: Add missing functions for Worker
    fun getRecurringExpenses(): List<Expense> {
        return expenseDao.getRecurringExpenses()
    }

    fun checkExpenseExistsThisMonth(desc: String, category: String, start: Long, end: Long): Int {
        return expenseDao.checkExpenseExistsThisMonth(desc, category, start, end)
    }

    suspend fun insert(expense: Expense) {
        expenseDao.insert(expense)
    }

    suspend fun delete(expense: Expense) {
        expenseDao.delete(expense)
    }

    suspend fun update(expense: Expense) {
        expenseDao.update(expense)
    }
}