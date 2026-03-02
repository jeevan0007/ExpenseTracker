package com.jeevan.expensetracker.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ExpenseDao {

    // --- ACTIVE DASHBOARD ITEMS ONLY (isDeleted = 0) ---
    @Query("SELECT * FROM expense_table WHERE isDeleted = 0")
    fun getAllExpensesSync(): List<Expense>

    @Query("SELECT * FROM expense_table WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllExpenses(): LiveData<List<Expense>>

    @Query("SELECT SUM(amount) FROM expense_table WHERE type = 'Income' AND isDeleted = 0")
    fun getTotalIncome(): LiveData<Double>

    @Query("SELECT SUM(amount) FROM expense_table WHERE type = 'Expense' AND isDeleted = 0")
    fun getTotalExpenses(): LiveData<Double>

    @Query("SELECT * FROM expense_table WHERE isRecurring = 1 AND isDeleted = 0")
    fun getRecurringExpenses(): List<Expense>

    @Query("SELECT COUNT(*) FROM expense_table WHERE description = :desc AND category = :category AND date BETWEEN :start AND :end AND isDeleted = 0")
    fun checkExpenseExistsThisMonth(desc: String, category: String, start: Long, end: Long): Int

    @Query("SELECT category, SUM(amount) as total FROM expense_table WHERE type = 'Expense' AND isDeleted = 0 GROUP BY category")
    fun getTotalByCategory(): LiveData<List<CategoryTotal>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    // --- RECYCLE BIN LOGIC ---
    // Soft Delete: Flips the switch and stamps the current time
    @Query("UPDATE expense_table SET isDeleted = 1, deletionDate = :timestamp WHERE id = :id")
    suspend fun moveToRecycleBin(id: Int, timestamp: Long)

    // Restore: Flips the switch back and clears the timestamp
    @Query("UPDATE expense_table SET isDeleted = 0, deletionDate = NULL WHERE id = :id")
    suspend fun restoreFromRecycleBin(id: Int)

    // Fetch deleted items for the Recycle Bin UI
    @Query("SELECT * FROM expense_table WHERE isDeleted = 1 ORDER BY deletionDate DESC")
    fun getDeletedExpenses(): LiveData<List<Expense>>

    // Auto-cleaner query (deletes things older than 30 days)
    @Query("DELETE FROM expense_table WHERE isDeleted = 1 AND deletionDate < :thresholdDate")
    suspend fun deleteOldRecycledItems(thresholdDate: Long)

    // Manual "Empty Bin"
    @Query("DELETE FROM expense_table WHERE isDeleted = 1")
    suspend fun emptyRecycleBin()

    // Single permanent delete
    @Delete
    suspend fun hardDelete(expense: Expense)
}

// Helper class for Chart Data
data class CategoryTotal(val category: String, val total: Double)