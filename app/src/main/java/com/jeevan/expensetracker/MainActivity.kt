package com.jeevan.expensetracker

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

        // Floating Action Button - Add Expense
        val fabAddExpense = findViewById<FloatingActionButton>(R.id.fabAddExpense)
        fabAddExpense.setOnClickListener {
            showAddExpenseDialog()
        }

        // Set Budget Button
        findViewById<Button>(R.id.btnSetBudget).setOnClickListener {
            showSetBudgetDialog()
        }

        // View Charts Button
        findViewById<Button>(R.id.btnViewCharts).setOnClickListener {
            val intent = android.content.Intent(this, ChartsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showAddExpenseDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

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
        AlertDialog.Builder(this)
            .setTitle("Delete Expense")
            .setMessage("Are you sure you want to delete this expense?\n\n${expense.description} - ₹${String.format("%.2f", expense.amount)}")
            .setPositiveButton("Delete") { _, _ ->
                expenseViewModel.delete(expense)
                Toast.makeText(this, "Expense deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(expense: Expense) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

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
                AlertDialog.Builder(this)
                    .setTitle("⚠️ Budget Exceeded!")
                    .setMessage("You have exceeded your monthly budget!\n\nBudget: ₹${String.format("%.2f", monthlyBudget)}\nSpent: ₹${String.format("%.2f", currentTotal)}\nOver by: ₹${String.format("%.2f", currentTotal - monthlyBudget)}")
                    .setPositiveButton("OK", null)
                    .show()
            }
            percentage >= 80 -> {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ Budget Warning")
                    .setMessage("You have used ${String.format("%.0f", percentage)}% of your monthly budget.\n\nBudget: ₹${String.format("%.2f", monthlyBudget)}\nSpent: ₹${String.format("%.2f", currentTotal)}\nRemaining: ₹${String.format("%.2f", monthlyBudget - currentTotal)}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

}