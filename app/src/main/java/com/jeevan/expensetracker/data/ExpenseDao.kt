package com.jeevan.expensetracker.data

import androidx.lifecycle.LiveData
import androidx.room.*

// 🔥 UPDATED: Added @ColumnInfo for KSP stability and reimbursedTotal for chart breakdowns
data class CategoryTotal(
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "total") val total: Double,
    @ColumnInfo(name = "reimbursedTotal") val reimbursedTotal: Double = 0.0
)

@Dao
interface ExpenseDao {

    // --- ACTIVE DASHBOARD ITEMS ONLY (isDeleted = 0) ---
    @Query("SELECT * FROM expense_table WHERE isDeleted = 0")
    fun getAllExpensesSync(): List<Expense>

    @Query("SELECT * FROM expense_table WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllExpenses(): LiveData<List<Expense>>

    @Query("SELECT SUM(amount) FROM expense_table WHERE type = 'Income' AND isDeleted = 0")
    fun getTotalIncome(): LiveData<Double>

    // 🔥 SMART BUDGET CALCULATION:
    // Only sums expenses that are NOT [Billable AND Reimbursed].
    @Query("""
        SELECT SUM(amount) FROM expense_table 
        WHERE type = 'Expense' 
        AND isDeleted = 0 
        AND (isBillable = 0 OR isReimbursed = 0)
    """)
    fun getTotalExpenses(): LiveData<Double>

    // 🔥 NET BALANCE CALCULATION:
    @Query("""
        SELECT 
        (SELECT COALESCE(SUM(amount), 0.0) FROM expense_table WHERE type = 'Income' AND isDeleted = 0) - 
        (SELECT COALESCE(SUM(amount), 0.0) FROM expense_table WHERE type = 'Expense' AND isDeleted = 0 AND (isBillable = 0 OR isReimbursed = 0))
    """)
    fun getNetBalance(): LiveData<Double>

    @Query("SELECT * FROM expense_table WHERE isRecurring = 1 AND isDeleted = 0")
    fun getRecurringExpenses(): List<Expense>

    @Query("SELECT COUNT(*) FROM expense_table WHERE description = :desc AND category = :category AND date BETWEEN :start AND :end AND isDeleted = 0")
    fun checkExpenseExistsThisMonth(desc: String, category: String, start: Long, end: Long): Int

    // 🔥 UPDATED CHART QUERY: Includes reimbursement breakdown per category
    @Query("""
        SELECT category, 
               SUM(amount) as total,
               SUM(CASE WHEN isBillable = 1 AND isReimbursed = 1 THEN amount ELSE 0 END) as reimbursedTotal
        FROM expense_table 
        WHERE type = 'Expense' AND isDeleted = 0 
        GROUP BY category
    """)
    fun getTotalByCategory(): LiveData<List<CategoryTotal>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    // --- RECYCLE BIN LOGIC ---
    @Query("UPDATE expense_table SET isDeleted = 1, deletionDate = :timestamp WHERE id = :id")
    suspend fun moveToRecycleBin(id: Int, timestamp: Long)

    @Query("UPDATE expense_table SET isDeleted = 0, deletionDate = NULL WHERE id = :id")
    suspend fun restoreFromRecycleBin(id: Int)

    @Query("SELECT * FROM expense_table WHERE isDeleted = 1 ORDER BY deletionDate DESC")
    fun getDeletedExpenses(): LiveData<List<Expense>>

    @Query("DELETE FROM expense_table WHERE isDeleted = 1 AND deletionDate < :thresholdDate")
    suspend fun deleteOldRecycledItems(thresholdDate: Long)

    @Query("DELETE FROM expense_table WHERE isDeleted = 1")
    suspend fun emptyRecycleBin()

    @Delete
    suspend fun hardDelete(expense: Expense)

    // --- ON-DEVICE AI CATEGORY PREDICTION ---
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
    @Query("SELECT * FROM expense_table WHERE isBillable = 1 AND isReimbursed = 0 AND isDeleted = 0 ORDER BY date DESC")
    fun getPendingReimbursements(): LiveData<List<Expense>>

    @Query("SELECT SUM(amount) FROM expense_table WHERE isBillable = 1 AND isReimbursed = 0 AND isDeleted = 0")
    fun getTotalPendingReimbursement(): LiveData<Double>

    // 🔥 NEW: Global "Money Recovered" metric
    @Query("""
        SELECT SUM(amount) FROM expense_table 
        WHERE type = 'Expense' 
        AND isBillable = 1 
        AND isReimbursed = 1 
        AND isDeleted = 0
    """)
    fun getTotalRecoveredMoney(): LiveData<Double?>

    @Query("UPDATE expense_table SET isReimbursed = 1 WHERE id IN (:expenseIds)")
    suspend fun markAsReimbursed(expenseIds: List<Int>)

    // --- TRIP & PROJECT WORKFLOW ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripSpace): Long

    @Update
    suspend fun updateTrip(trip: TripSpace)

    @Query("SELECT * FROM trip_table ORDER BY startDate DESC")
    fun getAllTrips(): LiveData<List<TripSpace>>

    @Query("SELECT * FROM trip_table WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTrip(): TripSpace?

    @Query("UPDATE trip_table SET isActive = 0")
    suspend fun deactivateAllTrips()

    @Query("SELECT * FROM expense_table WHERE tripId = :tripId AND isDeleted = 0 ORDER BY date DESC")
    fun getExpensesForTrip(tripId: Int): LiveData<List<Expense>>

    @Query("SELECT * FROM expense_table WHERE tripId = :tripId AND isDeleted = 0 ORDER BY date DESC")
    suspend fun getExpensesForTripSync(tripId: Int): List<Expense>

    @Query("SELECT SUM(amount) FROM expense_table WHERE tripId = :tripId AND type = 'Expense' AND isDeleted = 0")
    fun getTotalSpentForTrip(tripId: Int): LiveData<Double>
}