package com.jeevan.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Expense::class, TripSpace::class], version = 7, exportSchema = false)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: ExpenseDatabase? = null

        // Migrations 1-3 (Receipts)
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expense_table ADD COLUMN receiptPath TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expense_table ADD COLUMN receiptPath TEXT DEFAULT NULL")
            }
        }

        // Migration 3-4 (Soft Delete)
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expense_table ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expense_table ADD COLUMN deletionDate INTEGER DEFAULT NULL")
            }
        }

        // Migration 4-5 (Recurrence)
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expense_table ADD COLUMN recurrenceType TEXT NOT NULL DEFAULT 'None'")
            }
        }

        // Migration 5-6 (Billable Expenses)
        // 🔥 This is the one handling your current issue
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expense_table ADD COLUMN isBillable INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expense_table ADD COLUMN clientName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE expense_table ADD COLUMN isReimbursed INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration 6-7 (Trip Spaces)
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `trip_table` (
                        `tripId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `tripName` TEXT NOT NULL, 
                        `targetCurrency` TEXT NOT NULL, 
                        `startDate` INTEGER NOT NULL, 
                        `endDate` INTEGER, 
                        `isActive` INTEGER NOT NULL, 
                        `tripBudget` REAL
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE expense_table ADD COLUMN tripId INTEGER DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): ExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "expense_database"
                )
                    .addMigrations(
                        MIGRATION_1_3,
                        MIGRATION_2_3,
                        // Note: If you have gaps (like 1 to 3), ensure all paths are covered
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    // .fallbackToDestructiveMigration() // UNCOMMENT ONLY IF MIGRATIONS FAIL DURING TESTING
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}