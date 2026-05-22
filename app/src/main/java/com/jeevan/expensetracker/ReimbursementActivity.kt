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
import androidx.recyclerview.widget.DiffUtil
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
import com.jeevan.expensetracker.utils.CurrencyRates
import java.util.Locale

class ReimbursementActivity : AppCompatActivity() {

    private lateinit var rvReimbursements: RecyclerView
    private lateinit var tvTotalPending: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var btnGenerateInvoice: MaterialButton
    private lateinit var btnMarkPaid: MaterialButton

    private val selectedExpenses = mutableSetOf<Expense>()
    private var allPendingExpenses = listOf<Expense>()

    // FIX: read the active currency from SharedPreferences so the invoice
    // uses the correct symbol instead of always showing ₹.
    private var activeCurrencyLocale = Locale("en", "IN")
    private var activeCurrencyRate = 1.0

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

        // FIX: load saved currency preference (same prefs key MainActivity uses)
        val prefs = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        val savedCurrencyCode = prefs.getString("currency_code", "INR") ?: "INR"
        activeCurrencyRate = prefs.getFloat("currency_rate", 1.0f).toDouble()
        activeCurrencyLocale = CurrencyRates.localeFor(savedCurrencyCode)

        rvReimbursements.layoutManager = LinearLayoutManager(this)
        val adapter = ReimbursementAdapter(activeCurrencyLocale, activeCurrencyRate)
        rvReimbursements.adapter = adapter

        val db = ExpenseDatabase.getDatabase(this)

        // Observe total amount owed — shown in the header card
        db.expenseDao().getTotalPendingReimbursement().observe(this) { total ->
            val amount = (total ?: 0.0) * activeCurrencyRate
            val format = NumberFormat.getCurrencyInstance(activeCurrencyLocale)
            tvTotalPending.text = format.format(amount)
        }

        // Observe the list of pending items
        db.expenseDao().getPendingReimbursements().observe(this) { expenses ->
            allPendingExpenses = expenses

            // Pre-select all items when the screen loads
            selectedExpenses.clear()
            selectedExpenses.addAll(expenses)

            adapter.submitList(expenses)

            val hasItems = expenses.isNotEmpty()
            rvReimbursements.visibility = if (hasItems) View.VISIBLE else View.GONE
            tvEmptyState.visibility = if (hasItems) View.GONE else View.VISIBLE
            btnGenerateInvoice.isEnabled = hasItems
            btnMarkPaid.isEnabled = hasItems
        }

        // Generate PDF invoice for the selected items
        btnGenerateInvoice.setOnClickListener {
            if (selectedExpenses.isEmpty()) {
                Toast.makeText(this, "Select at least one item!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // FIX: pass active currency rate and locale so invoice matches the
            // currency the user is currently working in, not always INR
            val pdfFile = PdfReportGenerator.generatePdf(
                context = this,
                expenses = selectedExpenses.toList(),
                currencyRate = activeCurrencyRate,
                locale = activeCurrencyLocale,
                isInvoice = true
            )

            if (pdfFile != null) {
                sharePdf(pdfFile)
            } else {
                Toast.makeText(this, "Failed to generate Invoice.", Toast.LENGTH_SHORT).show()
            }
        }

        // Mark selected items as reimbursed — removes them from this list
        btnMarkPaid.setOnClickListener {
            if (selectedExpenses.isEmpty()) return@setOnClickListener
            val idsToMark = selectedExpenses.map { it.id }
            lifecycleScope.launch(Dispatchers.IO) {
                db.expenseDao().markAsReimbursed(idsToMark)
            }
            Toast.makeText(this, "Marked as Paid! 🎉", Toast.LENGTH_SHORT).show()
        }
    }

    // Inner adapter — currency locale and rate passed in from the activity so
    // the adapter never reads SharedPreferences itself
    inner class ReimbursementAdapter(
        private val currencyLocale: Locale,
        private val currencyRate: Double
    ) : RecyclerView.Adapter<ReimbursementAdapter.ViewHolder>() {

        private var items = listOf<Expense>()
        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        private val currencyFormat = NumberFormat.getCurrencyInstance(currencyLocale)

        // FIX: DiffUtil replaces notifyDataSetChanged() so only changed rows rebind
        fun submitList(newItems: List<Expense>) {
            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = items.size
                override fun getNewListSize() = newItems.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                    items[oldPos].id == newItems[newPos].id
                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                    val o = items[oldPos]; val n = newItems[newPos]
                    return o.amount == n.amount
                            && o.description == n.description
                            && o.clientName == n.clientName
                            && o.date == n.date
                            && o.isReimbursed == n.isReimbursed
                }
            })
            items = newItems
            diffResult.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_reimbursement, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val expense = items[position]
            holder.tvTitle.text = expense.description
            holder.tvDate.text = dateFormat.format(Date(expense.date))

            // FIX: apply currency conversion so the displayed amount matches the
            // active currency — previously always showed raw INR value
            holder.tvAmount.text = currencyFormat.format(expense.amount * currencyRate)

            if (expense.clientName.isNullOrBlank()) {
                holder.tvClient.visibility = View.GONE
            } else {
                holder.tvClient.visibility = View.VISIBLE
                holder.tvClient.text = "Client: ${expense.clientName}"
            }

            // Clear listener before setting checked state to prevent a feedback loop
            holder.checkbox.setOnCheckedChangeListener(null)
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
        val uri: Uri = FileProvider.getUriForFile(
            this, "${applicationContext.packageName}.provider", file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Reimbursement Invoice")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Invoice via..."))
    }
}