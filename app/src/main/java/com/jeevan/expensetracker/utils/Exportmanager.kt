package com.jeevan.expensetracker.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.jeevan.expensetracker.data.Expense
import java.util.Locale

/**
 * Handles PDF generation and sharing.
 * Previously exportDataToCSV() in MainActivity — despite the name,
 * it generated a PDF, not a CSV. Extracted here since it has no
 * dependency on Activity UI state.
 */
object ExportManager {

    private const val TAG = "ExportManager"

    /**
     * Generates a PDF report for [expenses] and launches the system
     * share sheet. Shows a Toast on failure.
     */
    fun exportToPdf(
        context: Context,
        expenses: List<Expense>,
        currencyRate: Double,
        currencyLocale: Locale
    ) {
        if (expenses.isEmpty()) {
            Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, "Generating Professional PDF...", Toast.LENGTH_SHORT).show()

        try {
            val pdfFile = PdfReportGenerator.generatePdf(
                context,
                expenses,
                currencyRate,
                currencyLocale
            ) ?: run {
                Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Professional Expense Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Save or Share PDF Report"))

        } catch (e: Exception) {
            Log.e(TAG, "PDF generation failed", e)
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}