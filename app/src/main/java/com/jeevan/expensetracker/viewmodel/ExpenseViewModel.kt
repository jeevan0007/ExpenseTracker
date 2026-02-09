package com.jeevan.expensetracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.repository.ExpenseRepository
import kotlinx.coroutines.launch

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository
    val allExpenses: LiveData<List<Expense>>
    val totalExpenses: LiveData<Double>

    // Filter LiveData
    private val _categoryFilter = MutableLiveData<String>("All")
    val filteredExpenses: LiveData<List<Expense>>

    init {
        val expenseDao = ExpenseDatabase.getDatabase(application).expenseDao()
        repository = ExpenseRepository(expenseDao)
        allExpenses = repository.allExpenses
        totalExpenses = repository.totalExpenses

        // Setup filtered expenses
        filteredExpenses = _categoryFilter.switchMap { category ->
            if (category == "All") {
                allExpenses
            } else {
                // Filter expenses by category
                allExpenses.switchMap { expenses ->
                    val filtered = expenses.filter { it.category == category }
                    MutableLiveData(filtered)
                }
            }
        }
    }

    fun setCategoryFilter(category: String) {
        _categoryFilter.value = category
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