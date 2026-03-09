package com.jeevan.expensetracker.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.jeevan.expensetracker.data.Expense
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object PdfReportGenerator {

    fun generatePdf(context: Context, expenses: List<Expense>, currencyRate: Double, locale: Locale): File? {
        if (expenses.isEmpty()) return null

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 Size
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // --- PAINTS (Our digital brushes) ---
        val titlePaint = Paint().apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 24f; color = Color.parseColor("#1A237E") }
        val subtitlePaint = Paint().apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); textSize = 14f; color = Color.GRAY }
        val headerPaint = Paint().apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 12f; color = Color.WHITE }
        val textPaint = Paint().apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); textSize = 12f; color = Color.BLACK }
        val positivePaint = Paint().apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 12f; color = Color.parseColor("#388E3C") }
        val negativePaint = Paint().apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 12f; color = Color.parseColor("#D32F2F") }
        val bgPaint = Paint().apply { color = Color.parseColor("#3F51B5") }
        val altRowPaint = Paint().apply { color = Color.parseColor("#F5F5F5") }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        var yPosition = 50f

        // --- HEADER ---
        canvas.drawText("PROFESSIONAL EXPENSE LEDGER", 40f, yPosition, titlePaint)
        yPosition += 25f
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        canvas.drawText("Generated on: ${sdf.format(Date())}", 40f, yPosition, subtitlePaint)
        yPosition += 40f

        // --- TABLE HEADER BACKGROUND ---
        canvas.drawRect(40f, yPosition - 15f, 555f, yPosition + 15f, bgPaint)

        // --- TABLE COLUMNS ---
        canvas.drawText("Date", 50f, yPosition + 5f, headerPaint)
        canvas.drawText("Category", 180f, yPosition + 5f, headerPaint)
        canvas.drawText("Description", 280f, yPosition + 5f, headerPaint)
        canvas.drawText("Amount", 480f, yPosition + 5f, headerPaint)
        yPosition += 35f

        val currencyFormat = NumberFormat.getCurrencyInstance(locale)
        val rowDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        var isAltRow = false

        var totalIncome = 0.0
        var totalExpense = 0.0

        val sortedExpenses = expenses.sortedByDescending { it.date }

        // --- DRAW ROWS ---
        for (expense in sortedExpenses) {
            // Pagination Check: If we reach the bottom of the page, start a new one!
            if (yPosition > 780f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPosition = 50f
            }

            if (isAltRow) {
                canvas.drawRect(40f, yPosition - 15f, 555f, yPosition + 15f, altRowPaint)
            }
            isAltRow = !isAltRow

            // Truncate long descriptions so they don't overlap columns
            var desc = expense.description
            if (desc.length > 20) desc = desc.substring(0, 17) + "..."

            val convertedAmount = expense.amount * currencyRate
            val formattedAmount = currencyFormat.format(convertedAmount)

            canvas.drawText(rowDateFormat.format(Date(expense.date)), 50f, yPosition + 5f, textPaint)
            canvas.drawText(expense.category, 180f, yPosition + 5f, textPaint)
            canvas.drawText(desc, 280f, yPosition + 5f, textPaint)

            if (expense.type == "Income") {
                canvas.drawText("+$formattedAmount", 480f, yPosition + 5f, positivePaint)
                totalIncome += convertedAmount
            } else {
                canvas.drawText("-$formattedAmount", 480f, yPosition + 5f, negativePaint)
                totalExpense += convertedAmount
            }

            yPosition += 30f
        }

        // --- DRAW SUMMARY FOOTER ---
        yPosition += 20f
        if (yPosition > 750f) { // Pagination check for summary
            pdfDocument.finishPage(page)
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            yPosition = 50f
        }

        canvas.drawLine(40f, yPosition, 555f, yPosition, linePaint)
        yPosition += 30f

        canvas.drawText("SUMMARY", 40f, yPosition, titlePaint)
        yPosition += 30f

        canvas.drawText("Total Income:", 40f, yPosition, textPaint)
        canvas.drawText(currencyFormat.format(totalIncome), 150f, yPosition, positivePaint)
        yPosition += 20f

        canvas.drawText("Total Expense:", 40f, yPosition, textPaint)
        canvas.drawText(currencyFormat.format(totalExpense), 150f, yPosition, negativePaint)
        yPosition += 20f

        canvas.drawLine(40f, yPosition, 250f, yPosition, linePaint)
        yPosition += 20f

        val netBalance = totalIncome - totalExpense
        canvas.drawText("Net Balance:", 40f, yPosition, textPaint)
        val balancePaint = if (netBalance >= 0) positivePaint else negativePaint
        canvas.drawText(currencyFormat.format(netBalance), 150f, yPosition, balancePaint)

        pdfDocument.finishPage(page)

        // --- SAVE FILE ---
        return try {
            val fileName = "ExpenseReport_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.pdf"
            val file = File(context.cacheDir, fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}