package com.jeevan.expensetracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.data.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository
    val allExpenses: LiveData<List<Expense>>

    private val _totalIncome = MutableLiveData<Double>(0.0)
    val totalIncome: LiveData<Double> get() = _totalIncome

    private val _totalExpenses = MutableLiveData<Double>(0.0)
    val totalExpenses: LiveData<Double> get() = _totalExpenses

    // 🔥 NEW: This fixes the "Unresolved reference" in ChartsActivity
    val totalRecoveredMoney: LiveData<Double?>

    val filteredExpenses = MediatorLiveData<List<Expense>>()

    private var currentSearchQuery: String = ""
    private var currentCategoryFilter: String = "All"
    private var currentDateStart: Long = 0L
    private var currentDateEnd: Long = System.currentTimeMillis()
    private var isDateFilterActive: Boolean = false

    init {
        val expenseDao = ExpenseDatabase.getDatabase(application).expenseDao()
        repository = ExpenseRepository(expenseDao)
        allExpenses = repository.allExpenses

        // 🔥 NEW: Connect to the repository's new recovery query
        totalRecoveredMoney = repository.totalRecoveredMoney

        filteredExpenses.addSource(allExpenses) { expenses ->
            applyFilters(expenses)
        }
    }

    fun insert(expense: Expense) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(expense)
    }

    fun delete(expense: Expense) = viewModelScope.launch(Dispatchers.IO) {
        repository.moveToRecycleBin(expense.id, System.currentTimeMillis())
    }

    fun update(expense: Expense) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(expense)
    }

    // --- RECYCLE BIN ACTIONS ---
    fun restore(expense: Expense) = viewModelScope.launch(Dispatchers.IO) {
        repository.restoreFromRecycleBin(expense.id)
    }

    fun getDeletedExpenses(): LiveData<List<Expense>> {
        return repository.getDeletedExpenses()
    }

    fun emptyRecycleBin() = viewModelScope.launch(Dispatchers.IO) {
        repository.emptyRecycleBin()
    }

    fun hardDelete(expense: Expense) = viewModelScope.launch(Dispatchers.IO) {
        repository.hardDelete(expense)
    }

    fun setSearchQuery(query: String) {
        currentSearchQuery = query
        applyFilters(allExpenses.value)
    }

    fun setCategoryFilter(category: String) {
        currentCategoryFilter = category
        applyFilters(allExpenses.value)
    }

    fun setDateRangeFilter(start: Long, end: Long) {
        currentDateStart = start
        currentDateEnd = end
        isDateFilterActive = true
        applyFilters(allExpenses.value)
    }

    fun clearDateFilter() {
        isDateFilterActive = false
        applyFilters(allExpenses.value)
    }

    fun getAllExpensesForExport(): List<Expense>? {
        return allExpenses.value
    }

    private fun applyFilters(expenses: List<Expense>?) {
        if (expenses == null) return

        var result: List<Expense> = expenses

        // 1. Search Filter
        if (currentSearchQuery.isNotEmpty()) {
            result = result.filter {
                it.description.contains(currentSearchQuery, ignoreCase = true) ||
                        it.category.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        // 2. Category Filter
        if (currentCategoryFilter != "All" && currentCategoryFilter != "All Categories") {
            result = result.filter { it.category == currentCategoryFilter }
        }

        // 3. Date Range Filter
        if (isDateFilterActive) {
            result = result.filter { it.date in currentDateStart..currentDateEnd }
        }

        var calculatedIncome = 0.0
        var calculatedExpense = 0.0

        for (item in result) {
            if (item.type == "Income") {
                calculatedIncome += item.amount
            } else if (item.type == "Expense") {
                // 🔥 SMART MATH:
                // Only add to "Total Spent" if it's a personal expense
                // OR if it's billable but you haven't been paid back yet.
                if (!item.isBillable || !item.isReimbursed) {
                    calculatedExpense += item.amount
                }
            }
        }

        _totalIncome.value = calculatedIncome
        _totalExpenses.value = calculatedExpense
        filteredExpenses.value = result
    }
}