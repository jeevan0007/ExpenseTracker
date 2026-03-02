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

    suspend fun update(expense: Expense) {
        expenseDao.update(expense)
    }

    // --- 🚨 RECYCLE BIN LOGIC 🚨 ---
    suspend fun moveToRecycleBin(id: Int, timestamp: Long) {
        expenseDao.moveToRecycleBin(id, timestamp)
    }

    suspend fun restoreFromRecycleBin(id: Int) {
        expenseDao.restoreFromRecycleBin(id)
    }

    fun getDeletedExpenses(): LiveData<List<Expense>> {
        return expenseDao.getDeletedExpenses()
    }

    suspend fun emptyRecycleBin() {
        expenseDao.emptyRecycleBin()
    }

    suspend fun hardDelete(expense: Expense) {
        expenseDao.hardDelete(expense)
    }
}