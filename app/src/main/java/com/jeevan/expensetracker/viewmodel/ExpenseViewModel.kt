package com.jeevan.expensetracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
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
    val totalIncome: LiveData<Double>
    val totalExpenses: LiveData<Double>

    // This "Mediator" is the key. It watches the database AND filters at the same time.
    val filteredExpenses = MediatorLiveData<List<Expense>>()

    // Filter States
    private var currentSearchQuery: String = ""
    private var currentCategoryFilter: String = "All"
    private var currentDateStart: Long = 0L
    private var currentDateEnd: Long = System.currentTimeMillis()
    private var isDateFilterActive: Boolean = false

    init {
        val expenseDao = ExpenseDatabase.getDatabase(application).expenseDao()
        repository = ExpenseRepository(expenseDao)
        allExpenses = repository.allExpenses
        totalIncome = repository.totalIncome
        totalExpenses = repository.totalExpenses

        // FIX: Watch the database for changes
        filteredExpenses.addSource(allExpenses) { expenses ->
            applyFilters(expenses)
        }
    }

    fun insert(expense: Expense) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(expense)
    }

    fun delete(expense: Expense) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(expense)
    }

    fun update(expense: Expense) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(expense)
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

    private fun applyFilters(expenses: List<Expense>?) {
        // FIX: If the list is null, do nothing.
        if (expenses == null) return

        // FIX: Explicitly tell Kotlin this list is NOT null
        var result: List<Expense> = expenses

        // 1. Apply Search Filter
        if (currentSearchQuery.isNotEmpty()) {
            result = result.filter {
                it.description.contains(currentSearchQuery, ignoreCase = true) ||
                        it.category.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        // 2. Apply Category Filter
        if (currentCategoryFilter != "All" && currentCategoryFilter != "All Categories") {
            result = result.filter { it.category == currentCategoryFilter }
        }

        // 3. Apply Date Range Filter
        if (isDateFilterActive) {
            result = result.filter { it.date in currentDateStart..currentDateEnd }
        }

        // Push the final, filtered list to the screen
        filteredExpenses.value = result
    }
}