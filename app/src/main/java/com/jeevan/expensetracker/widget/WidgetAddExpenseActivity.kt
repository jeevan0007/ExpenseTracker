package com.jeevan.expensetracker.widget

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.jeevan.expensetracker.R
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.utils.CategoryManager
import kotlinx.coroutines.Dispatchers
import com.jeevan.expensetracker.utils.ExpenseType
import com.jeevan.expensetracker.utils.RecurrenceType
import com.jeevan.expensetracker.utils.ReceiptScanner
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetAddExpenseActivity : AppCompatActivity() {

    private var tempReceiptUri: android.net.Uri? = null
    private var currentReceiptPreview: ImageView? = null

    private val pickReceiptLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            tempReceiptUri = it
            currentReceiptPreview?.apply {
                visibility = View.VISIBLE
                setImageURI(it)
                scaleX = 0f
                scaleY = 0f
                animate().scaleX(1f).scaleY(1f).setDuration(300).start()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_add_expense)

        findViewById<View>(android.R.id.content).setPadding(48, 48, 48, 48)
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
        val spinnerRecurrence = findViewById<Spinner>(R.id.spinnerRecurrence)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val btnAttachReceipt = findViewById<Button>(R.id.btnAttachReceipt)
        currentReceiptPreview = findViewById(R.id.ivReceiptPreview)

        // FIX: use CategoryManager instead of the hardcoded XML array so custom
        // categories created by the user actually appear in the widget spinner.
        val categories = CategoryManager.getCategories(this).map { it.name }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = spinnerAdapter

        val recurrenceOptions = listOf(RecurrenceType.NONE, RecurrenceType.MONTHLY, RecurrenceType.YEARLY)
        val recurrenceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, recurrenceOptions)
        recurrenceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRecurrence.adapter = recurrenceAdapter

        btnAttachReceipt.setOnClickListener {
            pickReceiptLauncher.launch("image/*")
        }

        btnSave.setOnClickListener {
            val amountText = etAmount.text.toString()
            val description = etDescription.text.toString()
            val category = spinnerCategory.selectedItem.toString()
            val type = if (radioGroupType.checkedRadioButtonId == R.id.radioIncome) ExpenseType.INCOME else ExpenseType.EXPENSE
            val selectedRecurrence = spinnerRecurrence.selectedItem.toString()
            val isRecurringFlag = selectedRecurrence != RecurrenceType.NONE

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0 || description.isEmpty()) {
                Toast.makeText(this, "Please enter valid details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // FIX: use lifecycleScope instead of a bare CoroutineScope so the
            // coroutine is cancelled if the activity is destroyed before it finishes.
            lifecycleScope.launch(Dispatchers.IO) {
                // Save receipt to internal storage on IO thread (not main thread)
                val finalReceiptPath = tempReceiptUri?.let {
                    ReceiptScanner.saveToInternalStorage(this@WidgetAddExpenseActivity, it)
                }

                val db = ExpenseDatabase.getDatabase(this@WidgetAddExpenseActivity)
                db.expenseDao().insert(
                    Expense(
                        amount = amount,
                        category = category,
                        description = description,
                        type = type,
                        isRecurring = isRecurringFlag,
                        recurrenceType = selectedRecurrence,
                        receiptPath = finalReceiptPath,
                        date = System.currentTimeMillis()
                    )
                )

                // Switch back to main thread to show Toast and close
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@WidgetAddExpenseActivity,
                        "Expense Logged!",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        }

        btnCancel.setOnClickListener { finish() }
    }

    // saveReceiptToInternalStorage → replaced by ReceiptScanner.saveToInternalStorage()
}