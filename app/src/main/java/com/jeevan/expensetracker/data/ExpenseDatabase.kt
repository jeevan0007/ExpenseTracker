package com.jeevan.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// CRITICAL FIX: Bumped to Version 3
@Database(entities = [Expense::class], version = 3, exportSchema = false)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: ExpenseDatabase? = null

        // Path A: For your friends in the real world (Upgrades 1 straight to 3)
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expense_table ADD COLUMN receiptPath TEXT DEFAULT NULL")
            }
        }

        // Path B: For YOUR specific test phone (Rescues it from Version 2 limbo to 3)
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expense_table ADD COLUMN receiptPath TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): ExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "expense_database"
                )
                    // Apply both safe migration paths
                    .addMigrations(MIGRATION_1_3, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}