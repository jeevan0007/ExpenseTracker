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

    // --- ON-DEVICE AI CATEGORY PREDICTION ---
    // Finds the most frequently used category for a specific merchant/description
    @Query("""
        SELECT category FROM expense_table 
        WHERE LOWER(description) = LOWER(:merchant) 
        AND type = 'Expense' 
        AND isDeleted = 0 
        GROUP BY category 
        ORDER BY COUNT(id) DESC 
        LIMIT 1
    """)
    suspend fun predictCategoryForMerchant(merchant: String): String?

    // --- REIMBURSEMENT WORKFLOW ---

    // 1. Get all expenses marked as billable but not yet paid back by the client
    @Query("SELECT * FROM expense_table WHERE isBillable = 1 AND isReimbursed = 0 AND isDeleted = 0 ORDER BY date DESC")
    fun getPendingReimbursements(): LiveData<List<Expense>>

    // 2. Get total pending reimbursement amount to display at the top of the UI
    @Query("SELECT SUM(amount) FROM expense_table WHERE isBillable = 1 AND isReimbursed = 0 AND isDeleted = 0")
    fun getTotalPendingReimbursement(): LiveData<Double>

    // 3. Mark a batch of expenses as reimbursed (e.g., when HR approves the PDF report)
    @Query("UPDATE expense_table SET isReimbursed = 1 WHERE id IN (:expenseIds)")
    suspend fun markAsReimbursed(expenseIds: List<Int>)

    // --- 🔥 NEW: TRIP & PROJECT WORKFLOW ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripSpace): Long

    @Update
    suspend fun updateTrip(trip: TripSpace)

    // Gets all trips for the dashboard
    @Query("SELECT * FROM trip_table ORDER BY startDate DESC")
    fun getAllTrips(): LiveData<List<TripSpace>>

    // Finds the currently active trip (if any) so the SMS receiver knows where to log auto-expenses
    @Query("SELECT * FROM trip_table WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTrip(): TripSpace?

    // Deactivates all trips (ensures only one is active at a time)
    @Query("UPDATE trip_table SET isActive = 0")
    suspend fun deactivateAllTrips()

    // Gets all expenses associated with a specific trip for the PDF export
    @Query("SELECT * FROM expense_table WHERE tripId = :tripId AND isDeleted = 0 ORDER BY date DESC")
    fun getExpensesForTrip(tripId: Int): LiveData<List<Expense>>
    @Query("SELECT * FROM expense_table WHERE tripId = :tripId AND isDeleted = 0 ORDER BY date DESC")
    suspend fun getExpensesForTripSync(tripId: Int): List<Expense>

    // Gets the total spent on a specific trip
    @Query("SELECT SUM(amount) FROM expense_table WHERE tripId = :tripId AND type = 'Expense' AND isDeleted = 0")
    fun getTotalSpentForTrip(tripId: Int): LiveData<Double>

}

// Helper class for Chart Data
data class CategoryTotal(val category: String, val total: Double)