package com.jeevan.expensetracker

import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
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
    private lateinit var barChart: BarChart
    private lateinit var rvDetails: RecyclerView
    private lateinit var tvTotalAmount: TextView

    private var chartItemList: List<ChartItem> = ArrayList()
    private var totalSpentAmount: Double = 0.0

    // --- CURRENCY STATE (Global Variables) ---
    private var activeRate: Double = 1.0
    private lateinit var activeFormat: NumberFormat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_charts)

        // 1. LOAD PERSISTENT DATA (Travel Mode / Currency Fix)
        val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        activeRate = sharedPref.getFloat("currency_rate", 1.0f).toDouble()
        val lang = sharedPref.getString("currency_lang", "en") ?: "en"
        val country = sharedPref.getString("currency_country", "IN") ?: "IN"
        val activeLocale = Locale(lang, country)

        activeFormat = NumberFormat.getCurrencyInstance(activeLocale)

        pieChart = findViewById(R.id.pieChart)
        barChart = findViewById(R.id.barChart)
        rvDetails = findViewById(R.id.rvChartDetails)
        tvTotalAmount = findViewById(R.id.tvTotalAmount)

        rvDetails.layoutManager = LinearLayoutManager(this)

        // Setup Custom Back Button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]
        expenseViewModel.allExpenses.observe(this) { expenses ->
            if (expenses != null && expenses.isNotEmpty()) {
                val expenseOnly = expenses.filter { it.type == "Expense" }
                if (expenseOnly.isNotEmpty()) {
                    setupPieChartAndList(expenseOnly)
                }
                setupBarChart(expenses)
            }
        }
    }

    private fun setupPieChartAndList(expenses: List<Expense>) {
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

        sortedCategories.forEachIndexed { index, entry ->
            val percentage = (entry.value / totalSpentAmount * 100).toFloat()
            val color = palette[index % palette.size]
            val emoji = getCategoryEmoji(entry.key)

            // Convert Logic: Raw Amount * Rate
            val convertedAmount = entry.value * activeRate
            val formattedString = activeFormat.format(convertedAmount)

            // Add to Chart (Pie Slice)
            entries.add(PieEntry(convertedAmount.toFloat(), entry.key))

            // Add to List (Bottom Recycler)
            chartItems.add(ChartItem(entry.key, convertedAmount, percentage, color, emoji, formattedString))
        }

        this.chartItemList = chartItems

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = palette
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 12f
        dataSet.setDrawValues(false)

        val data = PieData(dataSet)
        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.setExtraOffsets(20f, 0f, 20f, 0f)

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
                val pieEntry = e as? PieEntry
                val label = pieEntry?.label ?: "Unknown"

                val amount = pieEntry?.value?.toDouble() ?: 0.0
                updateCenterTextSelected(label, amount)

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
        tvTotalAmount.text = "Total Spending: ${activeFormat.format(totalSpentAmount * activeRate)}"
    }

    private fun setupBarChart(expenses: List<Expense>) {
        var totalIncome = 0.0
        var totalExpense = 0.0

        for (expense in expenses) {
            if (expense.type == "Income") totalIncome += expense.amount
            else totalExpense += expense.amount
        }

        // Apply Travel Mode Currency Rates to the Bar Chart too!
        val convertedIncome = (totalIncome * activeRate).toFloat()
        val convertedExpense = (totalExpense * activeRate).toFloat()

        val barEntries = ArrayList<BarEntry>()
        barEntries.add(BarEntry(0f, convertedIncome))
        barEntries.add(BarEntry(1f, convertedExpense))

        val dataSet = BarDataSet(barEntries, "")
        dataSet.colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#F44336"))
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = if (isDarkMode()) Color.WHITE else Color.BLACK

        val data = BarData(dataSet)
        data.barWidth = 0.5f
        barChart.data = data

        barChart.description.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.animateY(1400, Easing.EaseInOutQuad)
        barChart.setExtraOffsets(0f, 0f, 0f, 15f)

        // X-Axis Styling
        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Income", "Expense"))
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.textSize = 14f
        xAxis.textColor = if (isDarkMode()) Color.WHITE else Color.BLACK

        // Y-Axis Styling
        barChart.axisLeft.axisMinimum = 0f
        barChart.axisLeft.textColor = if (isDarkMode()) Color.WHITE else Color.BLACK
        barChart.axisLeft.setDrawGridLines(true)

        barChart.invalidate()
    }

    private fun updateCenterText(label: String, rawAmount: Double) {
        val converted = rawAmount * activeRate
        pieChart.centerText = "$label\n${activeFormat.format(converted)}"
        pieChart.setCenterTextSize(20f)
        pieChart.setCenterTextColor(if (isDarkMode()) Color.WHITE else Color.BLACK)
    }

    private fun updateCenterTextSelected(label: String, convertedAmount: Double) {
        pieChart.centerText = "$label\n${activeFormat.format(convertedAmount)}"
        pieChart.setCenterTextSize(20f)
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

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}