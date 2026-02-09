package com.jeevan.expensetracker

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.jeevan.expensetracker.adapter.CategoryAdapter
import com.jeevan.expensetracker.adapter.CategoryData
import com.jeevan.expensetracker.viewmodel.ExpenseViewModel

class ChartsActivity : AppCompatActivity() {

    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var pieChart: PieChart
    private lateinit var categoryAdapter: CategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_charts)

        // Enable back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Spending Analysis"

        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]
        pieChart = findViewById(R.id.pieChart)

        // Setup RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.rvCategoryList)
        categoryAdapter = CategoryAdapter()
        recyclerView.adapter = categoryAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Observe expenses and update chart
        expenseViewModel.allExpenses.observe(this) { expenses ->
            if (expenses.isNullOrEmpty()) {
                return@observe
            }

            // Calculate spending by category
            val categoryTotals = expenses.groupBy { it.category }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            val total = categoryTotals.values.sum()

            // Prepare data for pie chart
            val entries = ArrayList<PieEntry>()
            val categoryDataList = ArrayList<CategoryData>()
            val colors = getChartColors()

            categoryTotals.entries.forEachIndexed { index, entry ->
                val percentage = ((entry.value / total) * 100).toFloat()
                val emoji = getCategoryEmoji(entry.key)
                entries.add(PieEntry(entry.value.toFloat(), emoji))  // Only emoji on pie

                categoryDataList.add(
                    CategoryData(
                        name = "$emoji ${entry.key}",  // Full name in list
                        amount = entry.value,
                        percentage = percentage,
                        color = colors[index % colors.size]
                    )
                )
            }
            // Sort by amount descending
            categoryDataList.sortByDescending { it.amount }

            // Update RecyclerView
            categoryAdapter.setCategories(categoryDataList)

            // Setup pie chart with external emoji labels only
            val dataSet = PieDataSet(entries, "")
            dataSet.colors = colors
            dataSet.sliceSpace = 3f

            // Configure value lines (the pointer lines)
            dataSet.valueLinePart1OffsetPercentage = 80f
            dataSet.valueLinePart1Length = 0.4f
            dataSet.valueLinePart2Length = 0.6f
            dataSet.valueLineColor = Color.BLACK
            dataSet.valueLineWidth = 1.5f
            dataSet.xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            dataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE

            // Show only emoji on external labels
            dataSet.valueTextSize = 18f
            dataSet.valueTextColor = Color.BLACK
            dataSet.setDrawValues(true)

            // Custom formatter - just emoji, no percentage
            dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getPieLabel(value: Float, pieEntry: PieEntry): String {
                    return pieEntry.label  // Just the emoji
                }
            }

            val data = PieData(dataSet)
            pieChart.data = data
            pieChart.description.isEnabled = false
            pieChart.legend.isEnabled = false
            pieChart.setDrawEntryLabels(false)
            pieChart.centerText = "Total\n₹${String.format("%.2f", total)}"
            pieChart.setCenterTextSize(20f)
            pieChart.setCenterTextColor(Color.BLACK)
            pieChart.setHoleColor(Color.WHITE)
            pieChart.setTransparentCircleColor(Color.WHITE)
            pieChart.setTransparentCircleAlpha(110)
            pieChart.holeRadius = 50f
            pieChart.transparentCircleRadius = 53f
            pieChart.setDrawCenterText(true)
            pieChart.rotationAngle = 0f
            pieChart.isRotationEnabled = true
            pieChart.isHighlightPerTapEnabled = true
            pieChart.setExtraOffsets(20f, 20f, 20f, 20f)
            pieChart.animateY(1000)
            pieChart.invalidate()
        }
    }

    private fun getChartColors(): List<Int> {
        return listOf(
            Color.rgb(255, 102, 102),  // Red
            Color.rgb(102, 178, 255),  // Blue
            Color.rgb(255, 178, 102),  // Orange
            Color.rgb(178, 255, 102),  // Green
            Color.rgb(255, 102, 255),  // Pink
            Color.rgb(102, 255, 255),  // Cyan
            Color.rgb(255, 204, 102)   // Yellow
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    private fun getCategoryEmoji(category: String): String {
        return when (category) {
            "Food" -> "🍔"
            "Transport" -> "🚗"
            "Shopping" -> "🛍️"
            "Entertainment" -> "🎬"
            "Bills" -> "💡"
            "Healthcare" -> "🏥"
            "Other" -> "📌"
            else -> "💰"
        }
    }
}