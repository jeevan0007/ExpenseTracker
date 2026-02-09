package com.jeevan.expensetracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.repository.ExpenseRepository
import kotlinx.coroutines.launch
import java.util.*

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository
    val allExpenses: LiveData<List<Expense>>
    val totalExpenses: LiveData<Double>

    // Filter LiveData
    private val _categoryFilter = MutableLiveData<String>("All")
    private val _dateRangeFilter = MutableLiveData<Pair<Long, Long>?>(null)
    val filteredExpenses: MediatorLiveData<List<Expense>> = MediatorLiveData()

    init {
        val expenseDao = ExpenseDatabase.getDatabase(application).expenseDao()
        repository = ExpenseRepository(expenseDao)
        allExpenses = repository.allExpenses
        totalExpenses = repository.totalExpenses

        // Setup filtered expenses with both category and date filters
        filteredExpenses.addSource(allExpenses) { applyFilters() }
        filteredExpenses.addSource(_categoryFilter) { applyFilters() }
        filteredExpenses.addSource(_dateRangeFilter) { applyFilters() }
    }

    private fun applyFilters() {
        val expenses = allExpenses.value ?: emptyList()
        val category = _categoryFilter.value ?: "All"
        val dateRange = _dateRangeFilter.value

        var filtered = expenses

        // Apply category filter
        if (category != "All") {
            filtered = filtered.filter { it.category == category }
        }

        // Apply date range filter
        if (dateRange != null) {
            val (startDate, endDate) = dateRange
            filtered = filtered.filter { it.date in startDate..endDate }
        }

        filteredExpenses.value = filtered
    }

    fun setCategoryFilter(category: String) {
        _categoryFilter.value = category
    }

    fun setDateRangeFilter(startDate: Long?, endDate: Long?) {
        _dateRangeFilter.value = if (startDate != null && endDate != null) {
            Pair(startDate, endDate)
        } else {
            null
        }
    }

    fun clearDateFilter() {
        _dateRangeFilter.value = null
    }

    fun insert(expense: Expense) = viewModelScope.launch {
        repository.insert(expense)
    }

    fun update(expense: Expense) = viewModelScope.launch {
        repository.update(expense)
    }

    fun delete(expense: Expense) = viewModelScope.launch {
        repository.delete(expense)
    }

    fun getTotalByCategory(category: String): LiveData<Double> {
        return repository.getTotalByCategory(category)
    }
}