package com.jeevan.expensetracker

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.jeevan.expensetracker.adapter.ChartDetailAdapter
import com.jeevan.expensetracker.adapter.ChartItem
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.viewmodel.ExpenseViewModel
import java.text.NumberFormat
import java.util.Locale

class ChartsActivity : AppCompatActivity() {

    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var pieChart: PieChart
    private lateinit var rvDetails: RecyclerView
    private lateinit var tvTotalAmount: TextView

    private var chartItemList: List<ChartItem> = ArrayList()
    private var totalSpentAmount: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_charts)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Analysis"

        pieChart = findViewById(R.id.pieChart)
        rvDetails = findViewById(R.id.rvChartDetails)
        tvTotalAmount = findViewById(R.id.tvTotalAmount)

        rvDetails.layoutManager = LinearLayoutManager(this)

        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]
        expenseViewModel.allExpenses.observe(this) { expenses ->
            val expenseOnly = expenses.filter { it.type == "Expense" }
            if (expenseOnly.isNotEmpty()) {
                setupChartAndList(expenseOnly)
            }
        }
    }

    private fun setupChartAndList(expenses: List<Expense>) {
        val categoryMap = HashMap<String, Double>()
        totalSpentAmount = 0.0

        for (expense in expenses) {
            val current = categoryMap.getOrDefault(expense.category, 0.0)
            categoryMap[expense.category] = current + expense.amount
            totalSpentAmount += expense.amount
        }

        val sortedCategories = categoryMap.entries.sortedByDescending { it.value }

        val entries = ArrayList<PieEntry>()
        val chartItems = ArrayList<ChartItem>()
        val palette = getChartColors()

        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

        sortedCategories.forEachIndexed { index, entry ->
            val percentage = (entry.value / totalSpentAmount * 100).toFloat()
            val color = palette[index % palette.size]
            val emoji = getCategoryEmoji(entry.key)

            // We add the label to the entry so we can retrieve it on tap,
            // but we will hide it from being drawn below.
            entries.add(PieEntry(entry.value.toFloat(), entry.key))

            chartItems.add(ChartItem(entry.key, entry.value, percentage, color, emoji))
        }

        this.chartItemList = chartItems

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = palette
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 12f // Increase pop-out effect
        dataSet.setDrawValues(false) // Hide Numbers on chart

        val data = PieData(dataSet)
        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.setExtraOffsets(20f, 0f, 20f, 0f)

        // --- THE FIX: HIDE LABELS ON CHART ---
        pieChart.setDrawEntryLabels(false)

        pieChart.isDrawHoleEnabled = true
        pieChart.setHoleColor(Color.TRANSPARENT)
        pieChart.holeRadius = 70f
        pieChart.transparentCircleRadius = 75f

        updateCenterText("Total", totalSpentAmount)

        pieChart.animateY(1400, Easing.EaseInOutQuad)

        pieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                if (h == null) return

                // When tapped, get the name from the entry we stored earlier
                // PieEntry stores the label we passed in the constructor
                val pieEntry = e as? PieEntry
                val label = pieEntry?.label ?: "Unknown"
                val amount = pieEntry?.value?.toDouble() ?: 0.0

                updateCenterText(label, amount)

                // Scroll list to match
                val index = h.x.toInt()
                if (index in chartItemList.indices) {
                    rvDetails.smoothScrollToPosition(index)
                }
            }

            override fun onNothingSelected() {
                updateCenterText("Total", totalSpentAmount)
            }
        })

        pieChart.invalidate()

        rvDetails.adapter = ChartDetailAdapter(chartItems)
        tvTotalAmount.text = "Total Spending: ${format.format(totalSpentAmount)}"
    }

    private fun updateCenterText(label: String, amount: Double) {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        pieChart.centerText = "$label\n${format.format(amount)}"
        pieChart.setCenterTextSize(22f)
        pieChart.setCenterTextColor(if (isDarkMode()) Color.WHITE else Color.BLACK)
    }

    private fun getChartColors(): List<Int> {
        return listOf(
            Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"),
            Color.parseColor("#FFE66D"), Color.parseColor("#1A535C"),
            Color.parseColor("#FF9F1C"), Color.parseColor("#C7F464"),
            Color.parseColor("#55D6BE"), Color.parseColor("#ACFCD9")
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

    private fun isDarkMode(): Boolean {
        return AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}