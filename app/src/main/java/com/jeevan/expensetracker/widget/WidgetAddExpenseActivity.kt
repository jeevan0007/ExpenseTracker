package com.jeevan.expensetracker.widget

import android.os.Bundle
import android.view.View // <-- THIS IS THE FIX!
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.jeevan.expensetracker.R
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetAddExpenseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inflate the layout directly as the Activity!
        setContentView(R.layout.dialog_add_expense)

        // 2. Give the layout some breathing room so it matches AlertDialog padding
        findViewById<View>(android.R.id.content).setPadding(48, 48, 48, 48)

        // 3. Force the window to be 90% of the screen width so buttons don't shrink
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        setupWidgetLogic()
    }

    private fun setupWidgetLogic() {
        val radioGroupType = findViewById<RadioGroup>(R.id.radioGroupType)
        val etAmount = findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription = findViewById<TextInputEditText>(R.id.etDescription)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerCategory)
        val cbRecurring = findViewById<CheckBox>(R.id.cbRecurring)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnCancel = findViewById<Button>(R.id.btnCancel)

        val categories = resources.getStringArray(R.array.categories).toMutableList()
        categories.addAll(listOf("Rent", "Fuel", "Salary", "Automated"))
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = spinnerAdapter

        btnSave.setOnClickListener {
            val amountText = etAmount.text.toString()
            val description = etDescription.text.toString()
            val category = spinnerCategory.selectedItem.toString()
            val type = if (radioGroupType.checkedRadioButtonId == R.id.radioIncome) "Income" else "Expense"

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0 || description.isEmpty()) {
                Toast.makeText(this, "Please enter valid details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save directly to the Room DB
            val db = ExpenseDatabase.getDatabase(this)
            CoroutineScope(Dispatchers.IO).launch {
                db.expenseDao().insert(
                    Expense(
                        amount = amount,
                        category = category,
                        description = description,
                        type = type,
                        isRecurring = cbRecurring.isChecked,
                        date = System.currentTimeMillis()
                    )
                )
            }

            Toast.makeText(this, "Expense Logged!", Toast.LENGTH_SHORT).show()
            finish() // Instantly close back to the home screen
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }
}