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
import androidx.recyclerview.widget.DiffUtil
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
import com.jeevan.expensetracker.utils.CurrencyRates
import java.util.Locale

class TripDashboardActivity : AppCompatActivity() {

    private lateinit var db: ExpenseDatabase
    private lateinit var adapter: TripAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBarLayout)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
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
            // FIX: submitList now uses DiffUtil under the hood
            adapter.submitList(trips)
        }

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddTrip).setOnClickListener {
            showCreateTripDialog()
        }
    }

    private fun showCreateTripDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_trip, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val etTripName = dialogView.findViewById<TextInputEditText>(R.id.etTripName)
        val etTargetCurrency = dialogView.findViewById<TextInputEditText>(R.id.etTargetCurrency)
        val btnSaveTrip = dialogView.findViewById<Button>(R.id.btnSaveTrip)
        val btnCancelTrip = dialogView.findViewById<Button>(R.id.btnCancelTrip)

        // Show supported codes as a hint so the user knows what to type
        etTargetCurrency?.hint = CurrencyRates.supportedCodes.joinToString(" / ")

        btnSaveTrip.setOnClickListener {
            val name = etTripName.text.toString().trim()
            val rawCurrency = etTargetCurrency?.text?.toString()?.trim()?.uppercase() ?: ""

            // FIX: validate the typed code against the known list so an unrecognised
            // string can't slip through and silently fall back to the INR rate.
            val currency = when {
                rawCurrency.isEmpty() -> "INR"
                CurrencyRates.supportedCodes.contains(rawCurrency) -> rawCurrency
                else -> {
                    etTargetCurrency?.error = "Use one of: ${CurrencyRates.supportedCodes.joinToString(", ")}"
                    return@setOnClickListener
                }
            }

            if (name.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.expenseDao().deactivateAllTrips()
                    db.expenseDao().insertTrip(
                        TripSpace(
                            tripName = name,
                            targetCurrency = currency,
                            startDate = System.currentTimeMillis(),
                            endDate = null,
                            isActive = true
                        )
                    )
                }
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter a trip name", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancelTrip.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    inner class TripAdapter : RecyclerView.Adapter<TripAdapter.TripViewHolder>() {

        private var trips = listOf<TripSpace>()
        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // FIX: DiffUtil replaces notifyDataSetChanged() so only changed rows rebind
        fun submitList(newTrips: List<TripSpace>) {
            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = trips.size
                override fun getNewListSize() = newTrips.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                    trips[oldPos].tripId == newTrips[newPos].tripId
                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                    val o = trips[oldPos]; val n = newTrips[newPos]
                    return o.tripName == n.tripName
                            && o.isActive == n.isActive
                            && o.targetCurrency == n.targetCurrency
                            && o.endDate == n.endDate
                            && o.tripBudget == n.tripBudget
                }
            })
            trips = newTrips
            diffResult.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trip, parent, false)
            return TripViewHolder(view)
        }

        override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
            val trip = trips[position]
            holder.tvTripName.text = trip.tripName

            holder.tvTripIcon.text = when {
                trip.tripName.lowercase().let {
                    it.contains("visit") || it.contains("travel") ||
                            it.contains("trip") || it.contains("flight") } -> "✈️"
                trip.tripName.lowercase().let {
                    it.contains("audit") || it.contains("project") ||
                            it.contains("client") || it.contains("work") } -> "💼"
                trip.tripName.lowercase().let {
                    it.contains("vacation") || it.contains("holiday") ||
                            it.contains("beach") } -> "🌴"
                trip.tripName.lowercase().let {
                    it.contains("conference") || it.contains("event") ||
                            it.contains("meet") } -> "🎟️"
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
                    db.expenseDao().updateTrip(
                        trip.copy(isActive = false, endDate = System.currentTimeMillis())
                    )
                }
            }

            holder.btnExportTrip.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    val expensesToExport = db.expenseDao().getExpensesForTripSync(trip.tripId)

                    if (expensesToExport.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@TripDashboardActivity,
                                "No expenses logged for this trip yet.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    val pdfFile = PdfReportGenerator.generatePdf(
                        context = this@TripDashboardActivity,
                        expenses = expensesToExport,
                        currencyRate = CurrencyRates.exchangeRate(trip.targetCurrency),
                        locale = CurrencyRates.localeFor(trip.targetCurrency),
                        isInvoice = false,
                        reportTitle = "Trip Space: ${trip.tripName}"
                    )

                    withContext(Dispatchers.Main) {
                        if (pdfFile != null) {
                            sharePdf(pdfFile)
                        } else {
                            Toast.makeText(
                                this@TripDashboardActivity,
                                "Failed to generate report.",
                                Toast.LENGTH_SHORT
                            ).show()
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
        val uri: Uri = FileProvider.getUriForFile(
            this, "${applicationContext.packageName}.provider", file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Trip Expense Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Trip Report via..."))
    }
}