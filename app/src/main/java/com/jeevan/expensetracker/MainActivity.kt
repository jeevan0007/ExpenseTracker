package com.jeevan.expensetracker

import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import android.app.DatePickerDialog
import android.view.animation.AnimationUtils
import android.view.animation.Animation
import android.widget.RadioButton
import android.widget.RadioGroup
import java.util.*
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.jeevan.expensetracker.adapter.ExpenseAdapter
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.viewmodel.ExpenseViewModel
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var adapter: ExpenseAdapter
    private var monthlyBudget: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Load saved theme
        loadSavedTheme()

        // Load saved budget
        val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        monthlyBudget = sharedPref.getFloat("monthly_budget", 0f).toDouble()

        // Initialize ViewModel
        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]

        // Setup RecyclerView with click listeners
        val recyclerView = findViewById<RecyclerView>(R.id.rvExpenses)
        adapter = ExpenseAdapter(
            onItemLongClick = { expense -> showDeleteDialog(expense) },
            onItemClick = { expense -> showEditDialog(expense) }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Setup category filter spinner with modern style
        val spinnerCategoryFilter = findViewById<Spinner>(R.id.spinnerCategoryFilter)
        val filterCategories = mutableListOf("All Categories")
        filterCategories.addAll(resources.getStringArray(R.array.categories))
        val filterAdapter = ArrayAdapter(this, R.layout.spinner_item, filterCategories)
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategoryFilter.adapter = filterAdapter

        spinnerCategoryFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedCategory = if (position == 0) "All" else filterCategories[position]
                expenseViewModel.setCategoryFilter(selectedCategory)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

// Observe filtered expenses
        expenseViewModel.filteredExpenses.observe(this) { expenses ->
            expenses?.let { adapter.setExpenses(it) }
        }

        // Observe total expenses
        val tvTotalAmount = findViewById<TextView>(R.id.tvTotalAmount)
        expenseViewModel.totalExpenses.observe(this) { total ->
            tvTotalAmount.text = "₹${String.format("%.2f", total ?: 0.0)}"
        }

        // Floating Action Button - Add Expense with animation
        val fabAddExpense = findViewById<FloatingActionButton>(R.id.fabAddExpense)

// Scale animation on the FAB
        val scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_bounce)
        fabAddExpense.startAnimation(scaleAnimation)

        fabAddExpense.setOnClickListener {
            vibratePhone()
            // Press animation
            it.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()

            showAddExpenseDialog()
        }

        // Set Budget Button
        findViewById<Button>(R.id.btnSetBudget).setOnClickListener {
            vibratePhone()
            showSetBudgetDialog()
        }

        // Date Filter Button
        val btnDateFilter = findViewById<Button>(R.id.btnDateFilter)
        btnDateFilter.setOnClickListener {
            showDateFilterDialog()
        }
        // View Charts Button
        findViewById<Button>(R.id.btnViewCharts).setOnClickListener {
            vibratePhone()
            val intent = android.content.Intent(this, ChartsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        // Theme Toggle Button
        val btnToggleTheme = findViewById<Button>(R.id.btnToggleTheme)
        updateThemeButtonText(btnToggleTheme)

        btnToggleTheme.setOnClickListener {
            toggleTheme(btnToggleTheme)
        }
    }

    private fun showAddExpenseDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        // Get views from dialog
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        // Setup category spinner
        val categories = resources.getStringArray(R.array.categories)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = spinnerAdapter

        // Save button click
        btnSave.setOnClickListener {
            vibratePhone()
            val amountText = etAmount.text.toString()
            val description = etDescription.text.toString()
            val category = spinnerCategory.selectedItem.toString()

            if (amountText.isEmpty()) {
                Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (description.isEmpty()) {
                Toast.makeText(this, "Please enter description", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create and insert expense
            val expense = Expense(
                amount = amount,
                category = category,
                description = description
            )
            expenseViewModel.insert(expense)

            Toast.makeText(this, "Expense added!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()

            // Check budget after adding expense
            checkBudgetStatus()
        }

        // Cancel button click
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteDialog(expense: Expense) {
        val deleteDialog = AlertDialog.Builder(this)
            .setTitle("Delete Expense")
            .setMessage("Are you sure you want to delete this expense?\n\n${expense.description} - ₹${String.format("%.2f", expense.amount)}")
            .setPositiveButton("Delete") { _, _ ->
                expenseViewModel.delete(expense)
                Toast.makeText(this, "Expense deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        deleteDialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        deleteDialog.show()

    }

    private fun showEditDialog(expense: Expense) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation  // ADD THIS LINE

        // Get views from dialog
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        // Setup category spinner
        val categories = resources.getStringArray(R.array.categories)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = spinnerAdapter

        // Pre-fill with existing expense data
        etAmount.setText(expense.amount.toString())
        etDescription.setText(expense.description)
        val categoryPosition = categories.indexOf(expense.category)
        if (categoryPosition >= 0) {
            spinnerCategory.setSelection(categoryPosition)
        }

        // Change button text to "Update"
        btnSave.text = "Update"

        // Save button click
        btnSave.setOnClickListener {
            val amountText = etAmount.text.toString()
            val description = etDescription.text.toString()
            val category = spinnerCategory.selectedItem.toString()

            if (amountText.isEmpty()) {
                Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (description.isEmpty()) {
                Toast.makeText(this, "Please enter description", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Update existing expense
            val updatedExpense = expense.copy(
                amount = amount,
                category = category,
                description = description
            )
            expenseViewModel.update(updatedExpense)

            Toast.makeText(this, "Expense updated!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()

            // Check budget after updating expense
            checkBudgetStatus()
        }

        // Cancel button click
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSetBudgetDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_budget, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation  // ADD THIS LINE

        val etBudget = dialogView.findViewById<TextInputEditText>(R.id.etBudget)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveBudget)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelBudget)

        // Pre-fill current budget if set
        if (monthlyBudget > 0) {
            etBudget.setText(monthlyBudget.toString())
        }

        btnSave.setOnClickListener {
            val budgetText = etBudget.text.toString()

            if (budgetText.isEmpty()) {
                Toast.makeText(this, "Please enter budget amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val budget = budgetText.toDoubleOrNull()
            if (budget == null || budget <= 0) {
                Toast.makeText(this, "Please enter a valid budget", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save budget
            monthlyBudget = budget
            val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
            with(sharedPref.edit()) {
                putFloat("monthly_budget", budget.toFloat())
                apply()
            }

            Toast.makeText(this, "Budget set to ₹${String.format("%.2f", budget)}", Toast.LENGTH_SHORT).show()
            dialog.dismiss()

            // Check budget status immediately
            checkBudgetStatus()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun checkBudgetStatus() {
        if (monthlyBudget <= 0) return

        val currentTotal = expenseViewModel.totalExpenses.value ?: 0.0
        val percentage = (currentTotal / monthlyBudget) * 100

        when {
            percentage >= 100 -> {
                val exceededDialog = AlertDialog.Builder(this)
                    .setTitle("⚠️ Budget Exceeded!")
                    .setMessage("You have exceeded your monthly budget!\n\nBudget: ₹${String.format("%.2f", monthlyBudget)}\nSpent: ₹${String.format("%.2f", currentTotal)}\nOver by: ₹${String.format("%.2f", currentTotal - monthlyBudget)}")
                    .setPositiveButton("OK", null)
                    .create()

                exceededDialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
                exceededDialog.show()
            }
            percentage >= 80 -> {
                val warningDialog = AlertDialog.Builder(this)
                    .setTitle("⚠️ Budget Warning")
                    .setMessage("You have used ${String.format("%.0f", percentage)}% of your monthly budget.\n\nBudget: ₹${String.format("%.2f", monthlyBudget)}\nSpent: ₹${String.format("%.2f", currentTotal)}\nRemaining: ₹${String.format("%.2f", monthlyBudget - currentTotal)}")
                    .setPositiveButton("OK", null)
                    .create()

                warningDialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
                warningDialog.show()
            }
        }
    }
    private fun showDateFilterDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_date_filter, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation  // ADD THIS LINE

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupDateFilter)
        val btnApply = dialogView.findViewById<Button>(R.id.btnApplyDateFilter)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelDateFilter)

        btnApply.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            val calendar = Calendar.getInstance()

            when (selectedId) {
                R.id.radioAllTime -> {
                    expenseViewModel.clearDateFilter()
                    findViewById<Button>(R.id.btnDateFilter).text = "All Time"
                }
                R.id.radioToday -> {
                    val startOfDay = calendar.apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    val endOfDay = calendar.apply {
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }.timeInMillis

                    expenseViewModel.setDateRangeFilter(startOfDay, endOfDay)
                    findViewById<Button>(R.id.btnDateFilter).text = "Today"
                }
                R.id.radioThisWeek -> {
                    calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                    val startOfWeek = calendar.apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }.timeInMillis

                    val endOfWeek = Calendar.getInstance().timeInMillis

                    expenseViewModel.setDateRangeFilter(startOfWeek, endOfWeek)
                    findViewById<Button>(R.id.btnDateFilter).text = "This Week"
                }
                R.id.radioThisMonth -> {
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    val startOfMonth = calendar.apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }.timeInMillis

                    val endOfMonth = Calendar.getInstance().timeInMillis

                    expenseViewModel.setDateRangeFilter(startOfMonth, endOfMonth)
                    findViewById<Button>(R.id.btnDateFilter).text = "This Month"
                }
                R.id.radioLastMonth -> {
                    calendar.add(Calendar.MONTH, -1)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    val startOfLastMonth = calendar.apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }.timeInMillis

                    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                    val endOfLastMonth = calendar.apply {
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }.timeInMillis

                    expenseViewModel.setDateRangeFilter(startOfLastMonth, endOfLastMonth)
                    findViewById<Button>(R.id.btnDateFilter).text = "Last Month"
                }
                R.id.radioCustom -> {
                    dialog.dismiss()
                    showCustomDateRangePicker()
                    return@setOnClickListener
                }
            }

            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showCustomDateRangePicker() {
        val calendar = Calendar.getInstance()

        // Pick start date
        DatePickerDialog(this, { _, year, month, day ->
            val startCalendar = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startDate = startCalendar.timeInMillis

            // Pick end date
            DatePickerDialog(this, { _, year2, month2, day2 ->
                val endCalendar = Calendar.getInstance().apply {
                    set(year2, month2, day2, 23, 59, 59)
                }
                val endDate = endCalendar.timeInMillis

                expenseViewModel.setDateRangeFilter(startDate, endDate)
                findViewById<Button>(R.id.btnDateFilter).text = "Custom"
            }, year, month, day).show()

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }
    private fun toggleTheme(button: Button) {
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val newMode = if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }

        // Save preference
        val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt("theme_mode", newMode)
            apply()
        }

        // Apply theme
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    private fun updateThemeButtonText(button: Button) {
        vibratePhone()
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        button.text = if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            "☀️ Light Mode"
        } else {
            "🌙 Dark Mode"
        }
    }

    private fun loadSavedTheme() {
        val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        val savedTheme = sharedPref.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)
    }
    private fun vibratePhone() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }
}