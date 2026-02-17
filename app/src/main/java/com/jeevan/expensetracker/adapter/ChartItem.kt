package com.jeevan.expensetracker.adapter

data class ChartItem(
    val category: String,
    val amount: Double,
    val percentage: Float,
    val color: Int,
    val emoji: String,
    val formattedAmount: String
)