package com.jeevan.expensetracker.widget

import android.os.Bundle
import android.view.View
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

    // --- RECEIPT ENGINE VARIABLES ---
    private var tempReceiptUri: android.net.Uri? = null
    private var currentReceiptPreview: ImageView? = null

    // The Modern Photo Picker specifically for the Widget
    private val pickReceiptLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let {
            tempReceiptUri = it
            currentReceiptPreview?.apply {
                visibility = View.VISIBLE
                setImageURI(it)
                // Smooth pop-in animation
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

        // 🔥 NEW: Grab the Receipt Button & Image View
        val btnAttachReceipt = findViewById<Button>(R.id.btnAttachReceipt)
        currentReceiptPreview = findViewById(R.id.ivReceiptPreview)

        val categories = resources.getStringArray(R.array.categories).toMutableList()
        categories.addAll(listOf("Rent", "Fuel", "Salary", "Automated"))
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = spinnerAdapter

        val recurrenceOptions = listOf("None", "Monthly", "Yearly")
        val recurrenceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, recurrenceOptions)
        recurrenceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRecurrence.adapter = recurrenceAdapter

        // 🔥 NEW: Launch Gallery on Click
        btnAttachReceipt.setOnClickListener {
            pickReceiptLauncher.launch("image/*")
        }

        btnSave.setOnClickListener {
            val amountText = etAmount.text.toString()
            val description = etDescription.text.toString()
            val category = spinnerCategory.selectedItem.toString()
            val type = if (radioGroupType.checkedRadioButtonId == R.id.radioIncome) "Income" else "Expense"

            val selectedRecurrence = spinnerRecurrence.selectedItem.toString()
            val isRecurringFlag = selectedRecurrence != "None"

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0 || description.isEmpty()) {
                Toast.makeText(this, "Please enter valid details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🔥 NEW: Save the image to secure internal storage before writing to DB
            var finalReceiptPath: String? = null
            tempReceiptUri?.let { uri ->
                finalReceiptPath = saveReceiptToInternalStorage(uri)
            }

            val db = ExpenseDatabase.getDatabase(this)
            CoroutineScope(Dispatchers.IO).launch {
                db.expenseDao().insert(
                    Expense(
                        amount = amount,
                        category = category,
                        description = description,
                        type = type,
                        isRecurring = isRecurringFlag,
                        recurrenceType = selectedRecurrence,
                        receiptPath = finalReceiptPath, // Include the image path!
                        date = System.currentTimeMillis()
                    )
                )
            }

            Toast.makeText(this, "Expense Logged!", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    // 🔥 NEW: The Secure Vault logic for the Widget
    private fun saveReceiptToInternalStorage(uri: android.net.Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val fileName = "receipt_widget_${System.currentTimeMillis()}.jpg"
            val file = java.io.File(filesDir, fileName)
            val outputStream = java.io.FileOutputStream(file)

            inputStream.copyTo(outputStream)

            inputStream.close()
            outputStream.close()

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}