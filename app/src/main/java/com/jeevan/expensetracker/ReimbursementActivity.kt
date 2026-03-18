package com.jeevan.expensetracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.utils.PdfReportGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReimbursementActivity : AppCompatActivity() {

    private lateinit var rvReimbursements: RecyclerView
    private lateinit var tvTotalPending: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var btnGenerateInvoice: MaterialButton
    private lateinit var btnMarkPaid: MaterialButton

    // We keep track of which expenses the user has check-marked
    private val selectedExpenses = mutableSetOf<Expense>()
    private var allPendingExpenses = listOf<Expense>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reimbursements)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        rvReimbursements = findViewById(R.id.rvReimbursements)
        tvTotalPending = findViewById(R.id.tvTotalPending)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        btnGenerateInvoice = findViewById(R.id.btnGenerateInvoice)
        btnMarkPaid = findViewById(R.id.btnMarkPaid)

        rvReimbursements.layoutManager = LinearLayoutManager(this)
        val adapter = ReimbursementAdapter()
        rvReimbursements.adapter = adapter

        val db = ExpenseDatabase.getDatabase(this)

        // 1. Observe the Total Amount Owed
        db.expenseDao().getTotalPendingReimbursement().observe(this) { total ->
            val amount = total ?: 0.0
            val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            tvTotalPending.text = format.format(amount)
        }

        // 2. Observe the List of Pending Items
        db.expenseDao().getPendingReimbursements().observe(this) { expenses ->
            allPendingExpenses = expenses

            // By default, select everything when the screen loads
            selectedExpenses.clear()
            selectedExpenses.addAll(expenses)

            adapter.submitList(expenses)

            if (expenses.isEmpty()) {
                rvReimbursements.visibility = View.GONE
                tvEmptyState.visibility = View.VISIBLE
                btnGenerateInvoice.isEnabled = false
                btnMarkPaid.isEnabled = false
            } else {
                rvReimbursements.visibility = View.VISIBLE
                tvEmptyState.visibility = View.GONE
                btnGenerateInvoice.isEnabled = true
                btnMarkPaid.isEnabled = true
            }
        }

        // 3. Handle PDF Generation
        btnGenerateInvoice.setOnClickListener {
            if (selectedExpenses.isEmpty()) {
                Toast.makeText(this, "Select at least one item!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Call our updated PDF generator, passing isInvoice = true
            val pdfFile = PdfReportGenerator.generatePdf(
                context = this,
                expenses = selectedExpenses.toList(),
                currencyRate = 1.0, // Assuming INR default for now
                locale = Locale("en", "IN"),
                isInvoice = true
            )

            if (pdfFile != null) {
                sharePdf(pdfFile)
            } else {
                Toast.makeText(this, "Failed to generate Invoice.", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Handle Marking as Paid (Moves them out of this list)
        btnMarkPaid.setOnClickListener {
            if (selectedExpenses.isEmpty()) return@setOnClickListener

            val idsToMark = selectedExpenses.map { it.id }
            lifecycleScope.launch(Dispatchers.IO) {
                db.expenseDao().markAsReimbursed(idsToMark)
            }
            Toast.makeText(this, "Marked as Paid! 🎉", Toast.LENGTH_SHORT).show()
        }
    }

    // --- RECYCLER VIEW ADAPTER ---
    inner class ReimbursementAdapter : RecyclerView.Adapter<ReimbursementAdapter.ViewHolder>() {
        private var items = listOf<Expense>()
        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

        fun submitList(newItems: List<Expense>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reimbursement, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val expense = items[position]
            holder.tvTitle.text = expense.description
            holder.tvDate.text = dateFormat.format(Date(expense.date))
            holder.tvAmount.text = currencyFormat.format(expense.amount)

            if (expense.clientName.isNullOrBlank()) {
                holder.tvClient.visibility = View.GONE
            } else {
                holder.tvClient.visibility = View.VISIBLE
                holder.tvClient.text = "Client: ${expense.clientName}"
            }

            // Sync checkbox with our selected set
            holder.checkbox.setOnCheckedChangeListener(null) // Clear listener first
            holder.checkbox.isChecked = selectedExpenses.contains(expense)

            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedExpenses.add(expense) else selectedExpenses.remove(expense)
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvItemTitle)
            val tvClient: TextView = view.findViewById(R.id.tvItemClient)
            val tvDate: TextView = view.findViewById(R.id.tvItemDate)
            val tvAmount: TextView = view.findViewById(R.id.tvItemAmount)
            val checkbox: CheckBox = view.findViewById(R.id.checkboxSelect)
        }
    }

    private fun sharePdf(file: java.io.File) {
        val uri: Uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Reimbursement Invoice")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Invoice via..."))
    }
}