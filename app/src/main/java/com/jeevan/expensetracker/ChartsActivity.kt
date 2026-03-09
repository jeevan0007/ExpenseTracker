package com.jeevan.expensetracker

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.jeevan.expensetracker.adapter.ChartDetailAdapter
import com.jeevan.expensetracker.adapter.ChartItem
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.viewmodel.ExpenseViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChartsActivity : AppCompatActivity() {

    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var pieChart: PieChart
    private lateinit var barChart: BarChart
    private lateinit var rvDetails: RecyclerView
    private lateinit var tvTotalAmount: TextView
    private lateinit var tvPieChartTitle: TextView

    // --- MASTER DATA & FILTERS ---
    private var masterExpenseList: List<Expense> = emptyList()
    private var chartItemList: List<ChartItem> = ArrayList()
    private var totalFilteredAmount: Double = 0.0

    private var isExpenseMode: Boolean = true // True for Expense, False for Income
    private var chartStartTime: Long = 0L
    private var chartEndTime: Long = Long.MAX_VALUE

    // --- CURRENCY STATE (Global Variables) ---
    private var activeRate: Double = 1.0
    private lateinit var activeFormat: NumberFormat

    // --- PREMIUM FONT ---
    private var customTypeface: Typeface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_charts)

        customTypeface = ResourcesCompat.getFont(this, R.font.inter)

        val headerLayout = findViewById<View>(R.id.headerLayout)
        val filterBar = findViewById<View>(R.id.filterBar)
        val scrollView = findViewById<ScrollView>(R.id.chartsScrollView)

        ViewCompat.setOnApplyWindowInsetsListener(headerLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top + dpToPx(16), view.paddingRight, view.paddingBottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom + dpToPx(24))
            insets
        }

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
        tvPieChartTitle = findViewById(R.id.tvPieChartTitle)

        rvDetails.layoutManager = LinearLayoutManager(this)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        setupFilters()
        setThisMonthFilterSilently()

        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]
        expenseViewModel.allExpenses.observe(this) { expenses ->
            if (expenses != null) {
                masterExpenseList = expenses
                refreshCharts()
            }
        }
    }

    private fun setupFilters() {
        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupType)
        val btnDateFilter = findViewById<MaterialButton>(R.id.btnDateFilter)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isExpenseMode = (checkedId == R.id.btnFilterExpense)
                tvPieChartTitle.text = if (isExpenseMode) "Spending by Category" else "Income by Source"
                refreshCharts()
            }
        }

        btnDateFilter.setOnClickListener {
            it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
                showChartDateFilterDialog(btnDateFilter)
            }.start()
        }
    }

    private fun refreshCharts() {
        if (masterExpenseList.isEmpty()) return

        val dateFilteredList = masterExpenseList.filter { it.date in chartStartTime..chartEndTime }
        setupBarChart(dateFilteredList)

        val targetType = if (isExpenseMode) "Expense" else "Income"
        val fullyFilteredList = dateFilteredList.filter { it.type == targetType }
        setupPieChartAndList(fullyFilteredList)
    }

    private fun setupPieChartAndList(expenses: List<Expense>) {
        val categoryMap = HashMap<String, Double>()
        totalFilteredAmount = 0.0

        for (expense in expenses) {
            val current = categoryMap.getOrDefault(expense.category, 0.0)
            categoryMap[expense.category] = current + expense.amount
            totalFilteredAmount += expense.amount
        }

        val sortedCategories = categoryMap.entries.sortedByDescending { it.value }

        val entries = ArrayList<PieEntry>()
        val chartItems = ArrayList<ChartItem>()
        val palette = getChartColors()

        // 🔥 NEW: Fetch the dynamic categories right before drawing the chart
        val dynamicEmojis = com.jeevan.expensetracker.utils.CategoryManager.getCategories(this).associate { it.name to it.emoji }

        if (totalFilteredAmount > 0) {
            sortedCategories.forEachIndexed { index, entry ->
                val percentage = (entry.value / totalFilteredAmount * 100).toFloat()
                val color = palette[index % palette.size]

                // 🔥 NEW: Look up the custom emoji, or default to a money bag
                val emoji = dynamicEmojis[entry.key] ?: "💰"

                val convertedAmount = entry.value * activeRate
                val formattedString = activeFormat.format(convertedAmount)

                entries.add(PieEntry(convertedAmount.toFloat(), entry.key))
                chartItems.add(ChartItem(entry.key, convertedAmount, percentage, color, emoji, formattedString))
            }
        } else {
            entries.add(PieEntry(1f, "No Data"))
            pieChart.setCenterTextSize(14f)
        }

        this.chartItemList = chartItems

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = if (totalFilteredAmount > 0) palette else listOf(Color.GRAY)
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
        pieChart.holeRadius = 65f
        pieChart.transparentCircleRadius = 70f
        pieChart.setDrawCenterText(true)
        pieChart.centerTextRadiusPercent = 100f
        customTypeface?.let { pieChart.setCenterTextTypeface(it) }

        updateCenterTextTotal()

        pieChart.animateY(1200, Easing.EaseOutBounce)

        pieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                if (h == null || totalFilteredAmount == 0.0) return

                val pieEntry = e as? PieEntry
                val label = pieEntry?.label ?: "Unknown"
                val convertedAmount = pieEntry?.value?.toDouble() ?: 0.0

                pieChart.centerText = "$label\n${activeFormat.format(convertedAmount)}"
                pieChart.setCenterTextSize(18f)
                pieChart.setCenterTextColor(if (isDarkMode()) Color.WHITE else Color.BLACK)

                val index = h.x.toInt()
                if (index in chartItemList.indices) {
                    val smoothScroller = object : androidx.recyclerview.widget.LinearSmoothScroller(this@ChartsActivity) {
                        override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                            return 150f / displayMetrics.densityDpi
                        }
                        override fun getVerticalSnapPreference() = SNAP_TO_START
                    }
                    smoothScroller.targetPosition = index
                    rvDetails.layoutManager?.startSmoothScroll(smoothScroller)
                    (rvDetails.adapter as? ChartDetailAdapter)?.setHighlight(index)
                }
            }

            override fun onNothingSelected() {
                updateCenterTextTotal()
                (rvDetails.adapter as? ChartDetailAdapter)?.setHighlight(-1)
            }
        })

        pieChart.invalidate()
        rvDetails.adapter = ChartDetailAdapter(chartItems)

        val typeLabel = if (isExpenseMode) "Total Spending" else "Total Income"
        tvTotalAmount.text = "$typeLabel: ${activeFormat.format(totalFilteredAmount * activeRate)}"
    }

    private fun setupBarChart(expenses: List<Expense>) {
        var totalIncome = 0.0
        var totalExpense = 0.0

        for (expense in expenses) {
            if (expense.type == "Income") totalIncome += expense.amount
            else totalExpense += expense.amount
        }

        val convertedIncome = (totalIncome * activeRate).toFloat()
        val convertedExpense = (totalExpense * activeRate).toFloat()

        val barEntries = ArrayList<BarEntry>()
        barEntries.add(BarEntry(0f, convertedIncome))
        barEntries.add(BarEntry(1f, convertedExpense))

        val dataSet = BarDataSet(barEntries, "")
        dataSet.colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#FF5252"))
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = if (isDarkMode()) Color.WHITE else Color.BLACK
        customTypeface?.let { dataSet.valueTypeface = it }

        val data = BarData(dataSet)
        data.barWidth = 0.5f
        barChart.data = data

        barChart.description.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.animateY(1200, Easing.EaseOutQuad)
        barChart.setExtraOffsets(0f, 0f, 0f, 15f)

        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Income", "Expense"))
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.textSize = 14f
        xAxis.textColor = if (isDarkMode()) Color.WHITE else Color.BLACK
        customTypeface?.let { xAxis.typeface = it }

        barChart.axisLeft.axisMinimum = 0f
        // 🔥 FIX: Add top spacing so the text values don't get chopped off!
        barChart.axisLeft.spaceTop = 20f
        barChart.axisLeft.textColor = if (isDarkMode()) Color.WHITE else Color.BLACK
        barChart.axisLeft.setDrawGridLines(true)
        customTypeface?.let { barChart.axisLeft.typeface = it }
        barChart.setTouchEnabled(false)

        barChart.invalidate()
    }

    private fun showChartDateFilterDialog(btnDateFilter: MaterialButton) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_date_filter, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupDateFilter)

        dialogView.findViewById<Button>(R.id.btnApplyDateFilter).setOnClickListener {
            val calendar = Calendar.getInstance()

            when (radioGroup.checkedRadioButtonId) {
                R.id.radioAllTime -> {
                    chartStartTime = 0L
                    chartEndTime = Long.MAX_VALUE
                    btnDateFilter.text = "All Time"
                }
                R.id.radioToday -> {
                    chartStartTime = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                    chartEndTime = calendar.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
                    btnDateFilter.text = "Today"
                }
                R.id.radioThisMonth -> {
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    chartStartTime = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                    chartEndTime = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
                    btnDateFilter.text = "This Month"
                }
                R.id.radioLastMonth -> {
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.add(Calendar.MONTH, -1)
                    chartStartTime = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                    chartEndTime = calendar.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
                    btnDateFilter.text = "Last Month"
                }
                R.id.radioThisWeek -> {
                    calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    chartStartTime = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                    chartEndTime = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
                    btnDateFilter.text = "This Week"
                }
                // 🔥 FIX: Re-wired the Custom option properly!
                R.id.radioCustom -> {
                    dialog.dismiss()
                    showCustomDateRangePicker(btnDateFilter)
                    return@setOnClickListener
                }
            }
            refreshCharts()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnCancelDateFilter).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // 🔥 FIX: Copied the calendar picker over from MainActivity
    private fun showCustomDateRangePicker(btnDateFilter: MaterialButton) {
        val datePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .setTheme(R.style.PremiumDatePickerTheme)
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val utcStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selection.first }
            val utcEnd = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selection.second }

            chartStartTime = Calendar.getInstance().apply { set(utcStart.get(Calendar.YEAR), utcStart.get(Calendar.MONTH), utcStart.get(Calendar.DAY_OF_MONTH), 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            chartEndTime = Calendar.getInstance().apply { set(utcEnd.get(Calendar.YEAR), utcEnd.get(Calendar.MONTH), utcEnd.get(Calendar.DAY_OF_MONTH), 23, 59, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis

            val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
            btnDateFilter.text = "${sdf.format(Date(chartStartTime))} - ${sdf.format(Date(chartEndTime))}"
            refreshCharts()
        }
        datePicker.show(supportFragmentManager, "MATERIAL_DATE_RANGE_PICKER")
    }

    private fun setThisMonthFilterSilently() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        chartStartTime = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        chartEndTime = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
    }

    private fun updateCenterTextTotal() {
        val convertedTotal = totalFilteredAmount * activeRate
        val prefix = if (isExpenseMode) "Total Spent" else "Total Income"

        if (totalFilteredAmount == 0.0) {
            pieChart.centerText = "No Data\n"
        } else {
            pieChart.centerText = "$prefix\n${activeFormat.format(convertedTotal)}"
        }

        pieChart.setCenterTextSize(16f)
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

    // 🔥 FIX: 100% reliable System Dark Mode detection
    private fun isDarkMode(): Boolean {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}