package com.jeevan.expensetracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.data.TripSpace
import com.jeevan.expensetracker.utils.PdfReportGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TripDashboardActivity : AppCompatActivity() {

    private lateinit var db: ExpenseDatabase
    private lateinit var adapter: TripAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_dashboard)

        // --- 🔥 FIX: Dynamic System Navigation Bar Avoidance ---
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBarLayout)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)

            // Push the FAB up so it doesn't collide with the bottom nav bar
            val fabAddTrip = findViewById<ExtendedFloatingActionButton>(R.id.fabAddTrip)
            val params = fabAddTrip.layoutParams as ViewGroup.MarginLayoutParams
            val defaultMarginDp = (24 * resources.displayMetrics.density).toInt()
            params.bottomMargin = systemBars.bottom + defaultMarginDp
            fabAddTrip.layoutParams = params

            insets
        }

        db = ExpenseDatabase.getDatabase(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rvTrips = findViewById<RecyclerView>(R.id.rvTrips)
        rvTrips.layoutManager = LinearLayoutManager(this)
        adapter = TripAdapter()
        rvTrips.adapter = adapter

        db.expenseDao().getAllTrips().observe(this) { trips ->
            adapter.submitList(trips)
        }

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddTrip).setOnClickListener {
            showCreateTripDialog()
        }
    }

    // --- 🔥 FIX: Premium Dialog UI Implementation ---
    private fun showCreateTripDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_trip, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        // Apply your custom dialog entrance animation if it exists
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val etTripName = dialogView.findViewById<TextInputEditText>(R.id.etTripName)
        val etTargetCurrency = dialogView.findViewById<TextInputEditText>(R.id.etTargetCurrency)
        val btnSaveTrip = dialogView.findViewById<Button>(R.id.btnSaveTrip)
        val btnCancelTrip = dialogView.findViewById<Button>(R.id.btnCancelTrip)

        btnSaveTrip.setOnClickListener {
            val name = etTripName.text.toString().trim()
            val currency = etTargetCurrency.text.toString().trim()

            if (name.isNotEmpty() && currency.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    // Deactivate any currently running trips so only ONE is active
                    db.expenseDao().deactivateAllTrips()
                    // Start the new one
                    val newTrip = TripSpace(
                        tripName = name,
                        targetCurrency = currency.uppercase(),
                        startDate = System.currentTimeMillis(),
                        endDate = null,
                        isActive = true
                    )
                    db.expenseDao().insertTrip(newTrip)
                }
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancelTrip.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    inner class TripAdapter : RecyclerView.Adapter<TripAdapter.TripViewHolder>() {
        private var trips = listOf<TripSpace>()
        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        fun submitList(newTrips: List<TripSpace>) {
            trips = newTrips
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trip, parent, false)
            return TripViewHolder(view)
        }

        override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
            val trip = trips[position]
            holder.tvTripName.text = trip.tripName

            // Smart Emoji Logic based on Trip Name
            val nameLower = trip.tripName.lowercase()
            holder.tvTripIcon.text = when {
                nameLower.contains("visit") || nameLower.contains("travel") || nameLower.contains("trip") || nameLower.contains("flight") -> "✈️"
                nameLower.contains("audit") || nameLower.contains("project") || nameLower.contains("client") || nameLower.contains("work") -> "💼"
                nameLower.contains("vacation") || nameLower.contains("holiday") || nameLower.contains("beach") -> "🌴"
                nameLower.contains("conference") || nameLower.contains("event") || nameLower.contains("meet") -> "🎟️"
                else -> "🗺️"
            }

            val startDateStr = dateFormat.format(Date(trip.startDate))
            val endDateStr = if (trip.endDate != null) dateFormat.format(Date(trip.endDate)) else "Ongoing"
            holder.tvTripDetails.text = "Currency: ${trip.targetCurrency} | $startDateStr - $endDateStr"

            if (trip.isActive) {
                holder.chipStatus.text = "ACTIVE"
                holder.chipStatus.setChipBackgroundColorResource(android.R.color.holo_green_dark)
                holder.btnEndTrip.visibility = View.VISIBLE
            } else {
                holder.chipStatus.text = "COMPLETED"
                holder.chipStatus.setChipBackgroundColorResource(android.R.color.darker_gray)
                holder.btnEndTrip.visibility = View.GONE
            }

            holder.btnEndTrip.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    val completedTrip = trip.copy(isActive = false, endDate = System.currentTimeMillis())
                    db.expenseDao().updateTrip(completedTrip)
                }
            }

            holder.btnExportTrip.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    val expensesToExport = db.expenseDao().getExpensesForTripSync(trip.tripId)

                    if (expensesToExport.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@TripDashboardActivity, "No expenses logged for this trip yet.", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    // 🔥 FIX 1: Reverse the database INR back into the local currency!
                    val exportRate = when (trip.targetCurrency.uppercase()) {
                        "USD" -> 0.011
                        "EUR" -> 0.0093
                        "GBP" -> 0.0081
                        "JPY" -> 1.69
                        "CNY" -> 0.076
                        else -> 1.0
                    }

                    val tripLocale = when (trip.targetCurrency.uppercase()) {
                        "USD" -> Locale.US
                        "EUR" -> Locale.GERMANY
                        "GBP" -> Locale.UK
                        "JPY" -> Locale.JAPAN
                        "CNY" -> Locale.CHINA
                        else -> Locale("en", "IN")
                    }

                    // 🔥 FIX 2: Pass the Trip Name into the PDF Generator
                    val pdfFile = PdfReportGenerator.generatePdf(
                        context = this@TripDashboardActivity,
                        expenses = expensesToExport,
                        currencyRate = exportRate,
                        locale = tripLocale,
                        isInvoice = false,
                        reportTitle = "Trip Space: ${trip.tripName}" // Passes the custom title!
                    )

                    withContext(Dispatchers.Main) {
                        if (pdfFile != null) {
                            sharePdf(pdfFile)
                        } else {
                            Toast.makeText(this@TripDashboardActivity, "Failed to generate report.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        override fun getItemCount() = trips.size

        inner class TripViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTripIcon: TextView = view.findViewById(R.id.tvTripIcon)
            val tvTripName: TextView = view.findViewById(R.id.tvTripName)
            val tvTripDetails: TextView = view.findViewById(R.id.tvTripDetails)
            val chipStatus: Chip = view.findViewById(R.id.chipStatus)
            val btnEndTrip: Button = view.findViewById(R.id.btnEndTrip)
            val btnExportTrip: Button = view.findViewById(R.id.btnExportTrip)
        }
    }

    private fun sharePdf(file: java.io.File) {
        val uri: Uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Trip Expense Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Trip Report via..."))
    }
}