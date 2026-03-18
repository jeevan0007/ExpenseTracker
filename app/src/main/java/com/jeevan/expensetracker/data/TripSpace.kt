package com.jeevan.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_table")
data class TripSpace(
    @PrimaryKey(autoGenerate = true) val tripId: Int = 0,
    val tripName: String,             // e.g., "China Client Visit" or "Q3 Audit"
    val targetCurrency: String,       // e.g., "CNY", "USD", "INR"
    val startDate: Long,
    val endDate: Long?,               // Nullable, in case it's an ongoing project
    val isActive: Boolean = false,    // Only ONE trip can be active at a time
    val tripBudget: Double? = null    // Optional dedicated budget for the space
)