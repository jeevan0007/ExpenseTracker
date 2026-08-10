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

    private const val PAGE_W  = 595f
    private const val PAGE_H  = 842f
    private const val MARGIN  = 36f

    // FIX: Removed "Type" column — redundant with color+prefix.
    // Redistributed space: Description wider, Amount right-aligned properly.
    private const val COL_DATE  = MARGIN          // 36–136  (100pt)
    private const val COL_CAT   = 146f            // 146–256 (110pt)
    private const val COL_DESC  = 266f            // 266–436 (170pt)
    private const val COL_AMT   = PAGE_W - MARGIN // right-aligned

    private const val TABLE_L   = MARGIN
    private const val TABLE_R   = PAGE_W - MARGIN
    private const val ROW_H     = 22f
    private const val HEADER_H  = 24f

    fun generatePdf(
        context: Context,
        expenses: List<Expense>,
        currencyRate: Double,
        locale: Locale,
        isInvoice: Boolean = false,
        reportTitle: String = "EXPENSE REPORT"
    ): File? {
        if (expenses.isEmpty()) return null

        val pdfDoc  = PdfDocument()
        val pgInfo  = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create()
        var page    = pdfDoc.startPage(pgInfo)
        var canvas  = page.canvas
        var y       = 0f

        // ── Color palette ────────────────────────────────────────────────────
        val brand       = Color.parseColor("#4F46E5")
        val brandDark   = Color.parseColor("#3730A3")
        val incomeClr   = Color.parseColor("#059669")
        val expenseClr  = Color.parseColor("#DC2626")
        val textDark    = Color.parseColor("#111827")
        val textMid     = Color.parseColor("#6B7280")
        val textLight   = Color.parseColor("#9CA3AF")
        val surface     = Color.parseColor("#F9FAFB")
        val border      = Color.parseColor("#E5E7EB")
        val white       = Color.WHITE

        // ── Paint factory ────────────────────────────────────────────────────
        fun p(color: Int, size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
            Paint().apply {
                this.color = color; textSize = size; isAntiAlias = true
                textAlign = align
                typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                else      Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

        fun fillP(color: Int) = Paint().apply { this.color = color; isAntiAlias = true }
        fun strokeP(color: Int, w: Float = 0.8f) = Paint().apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = w; isAntiAlias = true
        }

        val linePaint = strokeP(border)

        // ── Helpers ───────────────────────────────────────────────────────────
        val dateFmt = SimpleDateFormat("dd MMM yy", Locale.getDefault())
        val nowFmt  = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val curFmt  = NumberFormat.getCurrencyInstance(locale)

        fun finishPage() = pdfDoc.finishPage(page)

        fun newPage(): Float {
            finishPage()
            page   = pdfDoc.startPage(pgInfo)
            canvas = page.canvas
            // Repeat table header on new page
            val hY = MARGIN + 10f
            canvas.drawRect(TABLE_L, hY - 14f, TABLE_R, hY + HEADER_H - 14f, fillP(brand))
            canvas.drawText("Date",        COL_DATE + 4f, hY, p(white, 9f, true))
            canvas.drawText("Category",    COL_CAT  + 4f, hY, p(white, 9f, true))
            canvas.drawText("Description", COL_DESC + 4f, hY, p(white, 9f, true))
            canvas.drawText("Amount",      COL_AMT  - 4f, hY, p(white, 9f, true, Paint.Align.RIGHT))
            return hY + HEADER_H
        }

        // FIX: checkY now passes the needed height and returns correct y,
        // preventing the "text runs together on page break" bug.
        fun checkY(needed: Float): Float {
            return if (y + needed > PAGE_H - MARGIN - 20f) newPage() else y
        }

        // ── Pre-compute totals ────────────────────────────────────────────────
        var totalIncome  = 0.0
        var totalExpense = 0.0
        expenses.forEach {
            if (it.type == ExpenseType.INCOME) totalIncome  += it.amount * currencyRate
            else                               totalExpense += it.amount * currencyRate
        }
        val netBalance = totalIncome - totalExpense

        val catColors = listOf("#4F46E5","#10B981","#F59E0B","#F43F5E","#8B5CF6","#06B6D4","#84CC16","#EC4899")
        val categoryMap = expenses
            .filter { it.type == ExpenseType.EXPENSE }
            .groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amount * currencyRate } }
            .entries.sortedByDescending { it.value }

        // ════════════════════════════════════════════════════════════════════
        // PAGE 1 — HEADER
        // ════════════════════════════════════════════════════════════════════

        // Gradient-style header (two-tone rect + triangle accent)
        canvas.drawRect(0f, 0f, PAGE_W, 100f, fillP(brand))
        canvas.drawRect(0f, 80f, PAGE_W, 100f, fillP(brandDark))

        // Diagonal accent
        val tri = android.graphics.Path().apply {
            moveTo(PAGE_W - 100f, 0f); lineTo(PAGE_W, 0f); lineTo(PAGE_W, 100f); close()
        }
        canvas.drawPath(tri, fillP(Color.parseColor("#33FFFFFF")))

        // App name + title
        canvas.drawText("ExpenseTracker", MARGIN, 30f, p(Color.parseColor("#CCFFFFFF"), 10f))
        val finalTitle = if (isInvoice) "REIMBURSEMENT INVOICE" else reportTitle.uppercase()
        canvas.drawText(finalTitle, MARGIN, 62f, p(white, 20f, true))
        canvas.drawText("Generated  ${nowFmt.format(Date())}", MARGIN, 82f, p(Color.parseColor("#CCFFFFFF"), 10f))

        // Transaction count badge — top right
        val badge = "${expenses.size} transactions"
        val badgeW = p(white, 9f).measureText(badge) + 16f
        val badgeL = PAGE_W - MARGIN - badgeW
        canvas.drawRoundRect(RectF(badgeL, 12f, badgeL + badgeW, 30f), 8f, 8f, fillP(Color.parseColor("#33FFFFFF")))
        canvas.drawText(badge, badgeL + 8f, 25f, p(white, 9f))

        y = 116f

        // ── Summary cards row ─────────────────────────────────────────────────
        // FIX: Cards auto-shrink text if amount is long (Indian lakhs format)
        val cardW = (TABLE_R - TABLE_L - 12f) / 3f
        fun summaryCard(left: Float, label: String, value: String, valueColor: Int) {
            val rect = RectF(left, y, left + cardW, y + 58f)
            canvas.drawRoundRect(rect, 10f, 10f, fillP(surface))
            canvas.drawRoundRect(rect, 10f, 10f, strokeP(border))
            // Colour top accent line
            canvas.drawRoundRect(RectF(left, y, left + cardW, y + 3f), 2f, 2f, fillP(valueColor))
            canvas.drawText(label, left + 10f, y + 18f, p(textMid, 8f))
            // FIX: measure text, shrink if needed to avoid clipping
            val valPaint = p(valueColor, 13f, true)
            val maxW = cardW - 20f
            val valW = valPaint.measureText(value)
            val scale = if (valW > maxW) maxW / valW else 1f
            canvas.save()
            canvas.translate(left + 10f, y + 44f)
            canvas.scale(scale, 1f)
            canvas.drawText(value, 0f, 0f, valPaint)
            canvas.restore()
        }

        summaryCard(TABLE_L,               "Total Income",  curFmt.format(totalIncome),  incomeClr)
        summaryCard(TABLE_L + cardW + 6f,  "Total Expense", curFmt.format(totalExpense), expenseClr)
        summaryCard(TABLE_L + (cardW+6f)*2,"Net Balance",   curFmt.format(netBalance),
            if (netBalance >= 0) incomeClr else expenseClr)

        y += 70f

        // ── Category breakdown ────────────────────────────────────────────────
        if (categoryMap.isNotEmpty() && !isInvoice) {
            canvas.drawText("Spending by Category", TABLE_L, y, p(textDark, 10f, true))
            y += 14f

            val barW = TABLE_R - TABLE_L
            val barH = 12f
            // FIX: Use RoundRect for the bar for a polished look
            var xOff = TABLE_L
            categoryMap.take(8).forEachIndexed { i, (_, amt) ->
                val frac = if (totalExpense > 0) (amt / totalExpense).toFloat() else 0f
                val segW = barW * frac
                if (segW > 0f) {
                    canvas.drawRect(xOff, y, xOff + segW, y + barH, fillP(Color.parseColor(catColors[i % catColors.size])))
                    xOff += segW
                }
            }
            // Bar border
            canvas.drawRoundRect(RectF(TABLE_L, y, TABLE_R, y + barH), 4f, 4f, strokeP(border, 0.5f))
            y += barH + 10f

            // Legend — 4 per row max
            var legX = TABLE_L; var legY = y
            categoryMap.take(8).forEachIndexed { i, (cat, amt) ->
                val pct = if (totalExpense > 0) "%.1f%%".format(amt / totalExpense * 100) else "0%"
                canvas.drawCircle(legX + 5f, legY - 3f, 4f, fillP(Color.parseColor(catColors[i % catColors.size])))
                canvas.drawText("${cat.take(12)}  $pct", legX + 12f, legY, p(textMid, 8f))
                legX += 128f
                if (legX > PAGE_W - 150f) { legX = TABLE_L; legY += 14f }
            }
            y = legY + 18f
        }

        // ── Table header ──────────────────────────────────────────────────────
        y = checkY(HEADER_H + 10f)
        canvas.drawRect(TABLE_L, y - 14f, TABLE_R, y + HEADER_H - 14f, fillP(brand))
        canvas.drawText("Date",        COL_DATE + 4f, y, p(white, 9f, true))
        canvas.drawText("Category",    COL_CAT  + 4f, y, p(white, 9f, true))
        canvas.drawText("Description", COL_DESC + 4f, y, p(white, 9f, true))
        canvas.drawText("Amount",      COL_AMT  - 4f, y, p(white, 9f, true, Paint.Align.RIGHT))
        y += HEADER_H

        // ── Table rows ────────────────────────────────────────────────────────
        val sorted = expenses.sortedByDescending { it.date }
        var altRow = false

        for (exp in sorted) {
            y = checkY(ROW_H + 2f)

            if (altRow) canvas.drawRect(TABLE_L, y - ROW_H + 6f, TABLE_R, y + 4f, fillP(surface))
            altRow = !altRow

            // Clean up description: if it's just "INR <number>", show as "Bank Transfer"
            val rawDesc = exp.description.trim()
            val cleanDesc = when {
                rawDesc.matches(Regex("(?i)inr\\s+[0-9,\\.]+")) -> "Bank Transfer"
                rawDesc.length > 20 -> rawDesc.take(18) + "…"
                else -> rawDesc
            }

            val amt        = curFmt.format(exp.amount * currencyRate)
            val prefix     = if (exp.type == ExpenseType.INCOME) "+" else "-"
            val amtPaint   = if (exp.type == ExpenseType.INCOME)
                p(incomeClr, 9f, true, Paint.Align.RIGHT)
            else
                p(expenseClr, 9f, true, Paint.Align.RIGHT)

            canvas.drawText(dateFmt.format(Date(exp.date)),   COL_DATE + 4f, y, p(textMid, 8.5f))
            canvas.drawText(exp.category.take(14),             COL_CAT  + 4f, y, p(textDark, 9f))
            canvas.drawText(cleanDesc,                         COL_DESC + 4f, y, p(textDark, 9f))
            // FIX: Amount right-aligned to COL_AMT — no clipping
            canvas.drawText("$prefix$amt",                     COL_AMT  - 4f, y, amtPaint)

            // Row divider
            canvas.drawLine(TABLE_L, y + 4f, TABLE_R, y + 4f, linePaint)
            y += ROW_H
        }

        // ── Summary footer ────────────────────────────────────────────────────
        // FIX: Don't repeat all 3 values — just show Net Balance prominently.
        // Full breakdown already visible at the top of the report.
        y = checkY(60f)
        y += 12f
        canvas.drawLine(TABLE_L, y, TABLE_R, y, linePaint)
        y += 20f

        val netLabel = if (netBalance >= 0) "Net Savings" else "Net Deficit"
        canvas.drawText(netLabel, TABLE_L, y, p(textDark, 10f, true))
        canvas.drawText(curFmt.format(netBalance), TABLE_R, y,
            p(if (netBalance >= 0) incomeClr else expenseClr, 14f, true, Paint.Align.RIGHT))
        y += 18f

        canvas.drawText("${expenses.size} transactions  ·  " +
                "${sorted.count { it.type != ExpenseType.INCOME }} expenses  ·  " +
                "${sorted.count { it.type == ExpenseType.INCOME }} income",
            TABLE_L, y, p(textLight, 8f))

        // ── Page footer ───────────────────────────────────────────────────────
        // FIX: Draw on every page, not just first
        val footTxt = "ExpenseTracker  •  Confidential  •  ${nowFmt.format(Date())}"
        val footW   = p(textLight, 7.5f, align = Paint.Align.CENTER).measureText(footTxt)
        canvas.drawLine(TABLE_L, PAGE_H - 28f, TABLE_R, PAGE_H - 28f, strokeP(border, 0.5f))
        canvas.drawText(footTxt, PAGE_W / 2f, PAGE_H - 14f, p(textLight, 7.5f, align = Paint.Align.CENTER))

        finishPage()

        return try {
            val prefix   = if (isInvoice) "Invoice_" else "ExpenseReport_"
            val fileName = "$prefix${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.pdf"
            val file     = File(context.cacheDir, fileName)
            pdfDoc.writeTo(FileOutputStream(file))
            pdfDoc.close()
            file
        } catch (e: Exception) {
            Log.e("PdfReportGenerator", "PDF generation failed", e)
            pdfDoc.close()
            null
        }
    }
}