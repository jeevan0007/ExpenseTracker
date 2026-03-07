package com.jeevan.expensetracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData // 🔥 NEW: Imported this to handle dynamic updates
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

    // 🔥 NEW: Make these Mutable so we can update them dynamically based on filters!
    private val _totalIncome = MutableLiveData<Double>(0.0)
    val totalIncome: LiveData<Double> get() = _totalIncome

    private val _totalExpenses = MutableLiveData<Double>(0.0)
    val totalExpenses: LiveData<Double> get() = _totalExpenses

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

        // We REMOVED the direct repository ties for totalIncome and totalExpenses
        // because they were hardwired to "All Time".

        // FIX: Watch the database for changes
        filteredExpenses.addSource(allExpenses) { expenses ->
            applyFilters(expenses)
        }
    }

    fun insert(expense: Expense) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(expense)
    }

    // --- 🚨 THE NINJA SWAP 🚨 ---
    // UI thinks it deletes, but it actually soft-deletes to the bin!
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

    // Function to get raw list of expenses for exporting
    fun getAllExpensesForExport(): List<Expense>? {
        return allExpenses.value
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

        // 🔥 NEW: Dynamically calculate Income and Expenses for the FILTERED list
        var calculatedIncome = 0.0
        var calculatedExpense = 0.0

        for (item in result) {
            if (item.type == "Income") {
                calculatedIncome += item.amount
            } else if (item.type == "Expense") {
                calculatedExpense += item.amount
            }
        }

        // Push the new math to the UI cards
        _totalIncome.value = calculatedIncome
        _totalExpenses.value = calculatedExpense

        // Push the final, filtered list to the screen
        filteredExpenses.value = result
    }
}