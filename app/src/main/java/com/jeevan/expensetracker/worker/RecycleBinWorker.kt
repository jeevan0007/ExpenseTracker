package com.jeevan.expensetracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jeevan.expensetracker.data.ExpenseDatabase

class RecycleBinWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val dao = ExpenseDatabase.getDatabase(applicationContext).expenseDao()

            // Calculate the exact millisecond timestamp for 30 days ago
            val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
            val thresholdDate = System.currentTimeMillis() - thirtyDaysInMillis

            // Trigger the permanent delete query for anything older than the threshold
            dao.deleteOldRecycledItems(thresholdDate)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}