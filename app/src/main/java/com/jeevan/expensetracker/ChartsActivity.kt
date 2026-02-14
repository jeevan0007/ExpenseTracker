package com.jeevan.expensetracker

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.viewmodel.ExpenseViewModel

class ChartsActivity : AppCompatActivity() {

    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var pieChart: PieChart
    private lateinit var tvTotalSpent: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_charts)

        // Enable back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Spending Analysis"

        pieChart = findViewById(R.id.pieChart)
        // If you have a total text view in layout, find it here, otherwise remove this line
        // tvTotalSpent = findViewById(R.id.tvTotalSpent)

        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]

        // Observe Live Data
        expenseViewModel.allExpenses.observe(this) { expenses ->
            setupPieChart(expenses)
        }
    }

    private fun setupPieChart(expenses: List<Expense>) {
        if (expenses.isEmpty()) return

        // 1. Group expenses by category
        val categoryMap = HashMap<String, Double>()
        var totalAmount = 0.0

        for (expense in expenses) {
            // Only chart "Expense" type, ignore "Income"
            if (expense.type == "Expense") {
                val current = categoryMap.getOrDefault(expense.category, 0.0)
                categoryMap[expense.category] = current + expense.amount
                totalAmount += expense.amount
            }
        }

        // 2. Prepare Chart Entries
        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()
        val palette = getChartColors()

        var i = 0
        for ((category, amount) in categoryMap) {
            val emoji = getCategoryEmoji(category)
            // Label is "🍔 Food"
            entries.add(PieEntry(amount.toFloat(), "$emoji $category"))
            colors.add(palette[i % palette.size])
            i++
        }

        // 3. Dataset Configuration
        val dataSet = PieDataSet(entries, "Expenses")
        dataSet.colors = colors
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f

        // Value Lines (The lines pointing to the labels)
        dataSet.valueLinePart1OffsetPercentage = 80f
        dataSet.valueLinePart1Length = 0.4f
        dataSet.valueLinePart2Length = 0.4f
        dataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        dataSet.valueLineColor = Color.BLACK

        // 4. Create Data Object
        val data = PieData(dataSet)
        data.setValueTextSize(14f)
        data.setValueTextColor(Color.BLACK)

        // Custom Formatter to show "₹500" instead of just "500.0"
        data.setValueFormatter(object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "₹${String.format("%.0f", value)}"
            }
        })

        // 5. Apply to Chart
        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.centerText = "Total\n₹${String.format("%.0f", totalAmount)}"
        pieChart.setCenterTextSize(18f)
        pieChart.setCenterTextColor(Color.BLACK)

        // Hallow Center
        pieChart.isDrawHoleEnabled = true
        pieChart.setHoleColor(Color.TRANSPARENT)
        pieChart.holeRadius = 55f
        pieChart.transparentCircleRadius = 60f

        // Legend Settings
        val legend = pieChart.legend
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        legend.textSize = 12f
        legend.isWordWrapEnabled = true

        pieChart.animateY(1000)
        pieChart.invalidate() // Refresh
    }

    private fun getChartColors(): List<Int> {
        return listOf(
            Color.parseColor("#FF6B6B"), // Red
            Color.parseColor("#4ECDC4"), // Teal
            Color.parseColor("#FFE66D"), // Yellow
            Color.parseColor("#1A535C"), // Dark Blue
            Color.parseColor("#FF9F1C")  // Orange
        )
    }

    private fun getCategoryEmoji(category: String): String {
        return when (category) {
            "Food" -> "🍔"
            "Transport" -> "🚗"
            "Shopping" -> "🛍️"
            "Entertainment" -> "🎬"
            "Bills" -> "💡"
            "Healthcare" -> "🏥"
            "Automated" -> "🤖"
            "Salary" -> "💵"
            "Other" -> "📌"
            else -> "💰"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}