package com.jeevan.expensetracker.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.jeevan.expensetracker.data.Expense
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object PdfReportGenerator {

    private const val PAGE_W = 595f   // A4 width in points
    private const val PAGE_H = 842f   // A4 height in points
    private const val MARGIN = 40f
    private const val COL_DATE   = MARGIN
    private const val COL_CAT   = 130f
    private const val COL_DESC  = 230f
    private const val COL_TYPE  = 390f
    private const val COL_AMT   = 460f
    private const val TABLE_R   = PAGE_W - MARGIN

    fun generatePdf(
        context: Context,
        expenses: List<Expense>,
        currencyRate: Double,
        locale: Locale,
        isInvoice: Boolean = false,
        reportTitle: String = "EXPENSE REPORT"
    ): File? {
        if (expenses.isEmpty()) return null

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var y = 0f

        // ── Paints ────────────────────────────────────────────────────────────
        val brandColor    = Color.parseColor("#4F46E5") // indigo primary
        val incomeColor   = Color.parseColor("#059669") // emerald
        val expenseColor  = Color.parseColor("#E11D48") // rose
        val textDark      = Color.parseColor("#0F0F23")
        val textMid       = Color.parseColor("#6B7280")
        val textLight     = Color.parseColor("#9CA3AF")
        val surfaceGray   = Color.parseColor("#F8F8FC")
        val borderColor   = Color.parseColor("#E5E7EB")

        fun paint(color: Int, size: Float, bold: Boolean = false) = Paint().apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val titleP    = paint(Color.WHITE, 22f, true)
        val subtitleP = paint(Color.WHITE, 11f, false)
        val headP     = paint(Color.WHITE, 10f, true)
        val bodyP     = paint(textDark, 10f, false)
        val bodySmP   = paint(textMid, 9f, false)
        val boldP     = paint(textDark, 10f, true)
        val incomeP   = paint(incomeColor, 10f, true)
        val expenseP  = paint(expenseColor, 10f, true)
        val bgP       = Paint().apply { isAntiAlias = true }
        val lineP     = Paint().apply { color = borderColor; strokeWidth = 0.8f }

        val currencyFormat = NumberFormat.getCurrencyInstance(locale)
        val dateFormat     = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val nowFormat      = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        fun finishPage() { pdfDocument.finishPage(page) }
        fun newPage(): Float {
            finishPage()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            return MARGIN + 20f
        }
        fun checkY(needed: Float): Float {
            return if (y + needed > PAGE_H - MARGIN) newPage() else y
        }

        // ── Header band ────────────────────────────────────────────────────────
        bgP.color = brandColor
        canvas.drawRect(0f, 0f, PAGE_W, 110f, bgP)

        // Subtle diagonal accent
        bgP.color = Color.parseColor("#33FFFFFF")
        val path = android.graphics.Path()
        path.moveTo(PAGE_W - 120f, 0f)
        path.lineTo(PAGE_W, 0f)
        path.lineTo(PAGE_W, 110f)
        path.close()
        canvas.drawPath(path, bgP)

        // App name
        canvas.drawText("ExpenseTracker", MARGIN, 38f, paint(Color.parseColor("#CCFFFFFF"), 11f, false))

        // Report title
        val finalTitle = if (isInvoice) "REIMBURSEMENT INVOICE" else reportTitle.uppercase()
        canvas.drawText(finalTitle, MARGIN, 68f, titleP)

        // Generated date
        canvas.drawText("Generated  ${nowFormat.format(Date())}", MARGIN, 88f, subtitleP)

        // Record count top-right
        val countText = "${expenses.size} transaction${if (expenses.size != 1) "s" else ""}"
        val countW = paint(Color.WHITE, 10f, false).measureText(countText)
        canvas.drawText(countText, PAGE_W - MARGIN - countW, 65f, paint(Color.WHITE, 10f, false))

        y = 130f

        // ── Summary box ─────────────────────────────────────────────────────────
        var totalIncome  = 0.0
        var totalExpense = 0.0
        expenses.forEach {
            if (it.type == ExpenseType.INCOME) totalIncome += it.amount * currencyRate
            else totalExpense += it.amount * currencyRate
        }
        val netBalance = totalIncome - totalExpense
        val categoryMap = expenses
            .filter { it.type == ExpenseType.EXPENSE }
            .groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amount * currencyRate } }
            .entries.sortedByDescending { it.value }

        // Summary row — 3 boxes
        val boxW = (PAGE_W - MARGIN * 2 - 16f) / 3f
        fun summaryBox(left: Float, label: String, value: String, valueColor: Int) {
            bgP.color = surfaceGray
            val rect = RectF(left, y, left + boxW, y + 60f)
            canvas.drawRoundRect(rect, 8f, 8f, bgP)
            bgP.color = borderColor
            // border
            val borderP = Paint().apply { color = borderColor; style = Paint.Style.STROKE; strokeWidth = 0.8f; isAntiAlias = true }
            canvas.drawRoundRect(rect, 8f, 8f, borderP)
            canvas.drawText(label, left + 12f, y + 22f, paint(textMid, 9f, false))
            canvas.drawText(value, left + 12f, y + 46f, paint(valueColor, 13f, true))
        }

        summaryBox(MARGIN, "Total Income",  currencyFormat.format(totalIncome), incomeColor)
        summaryBox(MARGIN + boxW + 8f, "Total Expense", currencyFormat.format(totalExpense), expenseColor)
        summaryBox(MARGIN + (boxW + 8f) * 2f, "Net Balance", currencyFormat.format(netBalance),
            if (netBalance >= 0) incomeColor else expenseColor)

        y += 76f

        // ── Category breakdown bar ───────────────────────────────────────────────
        if (categoryMap.isNotEmpty() && !isInvoice) {
            canvas.drawText("Top Categories", MARGIN, y, paint(textDark, 11f, true))
            y += 18f

            val barTotalW = PAGE_W - MARGIN * 2f
            val catColors = listOf("#4F46E5","#10B981","#F59E0B","#F43F5E","#8B5CF6","#06B6D4","#84CC16","#EC4899")
            var xOff = MARGIN

            categoryMap.take(8).forEachIndexed { i, (cat, amt) ->
                val frac = if (totalExpense > 0) (amt / totalExpense).toFloat() else 0f
                val segW = barTotalW * frac
                bgP.color = Color.parseColor(catColors[i % catColors.size])
                canvas.drawRect(xOff, y, xOff + segW, y + 10f, bgP)
                xOff += segW
            }
            y += 18f

            // Legend
            var legX = MARGIN
            var legY = y
            categoryMap.take(8).forEachIndexed { i, (cat, amt) ->
                val pct = if (totalExpense > 0) (amt / totalExpense * 100).toInt() else 0
                bgP.color = Color.parseColor(catColors[i % catColors.size])
                canvas.drawCircle(legX + 5f, legY - 3f, 5f, bgP)
                val label = "${cat.take(14)}  $pct%"
                canvas.drawText(label, legX + 14f, legY, paint(textMid, 8f, false))
                legX += 120f
                if (legX > PAGE_W - 160f) { legX = MARGIN; legY += 14f }
            }
            y = legY + 20f
        }

        // ── Table header ─────────────────────────────────────────────────────────
        y = checkY(40f)
        bgP.color = brandColor
        canvas.drawRect(MARGIN, y - 14f, TABLE_R, y + 10f, bgP)
        canvas.drawText("Date",        COL_DATE + 4f, y, headP)
        canvas.drawText("Category",    COL_CAT  + 4f, y, headP)
        canvas.drawText("Description", COL_DESC + 4f, y, headP)
        canvas.drawText("Type",        COL_TYPE + 4f, y, headP)
        canvas.drawText("Amount",      COL_AMT  + 4f, y, headP)
        y += 18f

        // ── Table rows ────────────────────────────────────────────────────────────
        val sorted = expenses.sortedByDescending { it.date }
        var rowAlt = false

        for (expense in sorted) {
            y = checkY(28f)

            if (rowAlt) {
                bgP.color = surfaceGray
                canvas.drawRect(MARGIN, y - 12f, TABLE_R, y + 8f, bgP)
            }
            rowAlt = !rowAlt

            val desc = buildString {
                append(expense.description)
                if (isInvoice && !expense.clientName.isNullOrBlank()) append(" [${expense.clientName}]")
            }.let { if (it.length > 22) it.take(19) + "…" else it }

            val amt = currencyFormat.format(expense.amount * currencyRate)
            val amtPaint = if (expense.type == ExpenseType.INCOME) incomeP else expenseP
            val prefix = if (expense.type == ExpenseType.INCOME) "+" else "-"

            canvas.drawText(dateFormat.format(Date(expense.date)),     COL_DATE + 4f, y, bodySmP)
            canvas.drawText(expense.category.take(16),                  COL_CAT  + 4f, y, bodyP)
            canvas.drawText(desc,                                        COL_DESC + 4f, y, bodyP)
            canvas.drawText(if (expense.type == ExpenseType.INCOME) "Income" else "Expense", COL_TYPE + 4f, y, bodySmP)
            canvas.drawText("$prefix$amt",                               COL_AMT  + 4f, y, amtPaint)

            // Bottom divider
            canvas.drawLine(MARGIN, y + 10f, TABLE_R, y + 10f, lineP)
            y += 22f
        }

        // ── Footer summary ─────────────────────────────────────────────────────
        y = checkY(80f)
        y += 16f
        canvas.drawLine(MARGIN, y, TABLE_R, y, lineP)
        y += 20f

        canvas.drawText("SUMMARY", MARGIN, y, paint(brandColor, 13f, true))
        y += 20f

        if (isInvoice) {
            canvas.drawText("Total Reimbursable:", MARGIN, y, bodyP)
            canvas.drawText(currencyFormat.format(totalExpense), COL_AMT, y, paint(incomeColor, 11f, true))
        } else {
            canvas.drawText("Total Income:",   MARGIN, y, bodyP)
            canvas.drawText(currencyFormat.format(totalIncome), 200f, y, paint(incomeColor, 11f, true))
            y += 18f
            canvas.drawText("Total Expense:",  MARGIN, y, bodyP)
            canvas.drawText(currencyFormat.format(totalExpense), 200f, y, paint(expenseColor, 11f, true))
            y += 18f
            canvas.drawLine(MARGIN, y, 320f, y, lineP)
            y += 16f
            canvas.drawText("Net Balance:", MARGIN, y, boldP)
            canvas.drawText(currencyFormat.format(netBalance), 200f, y,
                paint(if (netBalance >= 0) incomeColor else expenseColor, 13f, true))
        }

        // Footer branding
        y = PAGE_H - 30f
        val footerTxt = "Generated by ExpenseTracker  •  ${nowFormat.format(Date())}"
        val footerW = paint(textLight, 8f, false).measureText(footerTxt)
        canvas.drawText(footerTxt, (PAGE_W - footerW) / 2f, y, paint(textLight, 8f, false))

        finishPage()

        return try {
            val prefix = if (isInvoice) "Invoice_" else "ExpenseReport_"
            val fileName = "$prefix${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.pdf"
            val file = File(context.cacheDir, fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            Log.e("PdfReportGenerator", "PDF generation failed", e)
            pdfDocument.close()
            null
        }
    }
}