package com.jeevan.expensetracker.utils

import android.util.Log

data class ParsedExpense(
    val amount: Double,
    val merchant: String,
    val source: String,
    val type: String = ExpenseType.EXPENSE
)

object PaymentParser {

    private const val TAG = "PaymentParser"

    // ---------------------------------------------------------------------------
    // SMS PARSER
    // ---------------------------------------------------------------------------

    fun parseSms(message: String, sender: String = ""): ParsedExpense? {

        // LAYER 1 — SENDER ID FILTER
        if (sender.matches(Regex("^[+0-9]{10,13}$"))) return null

        val lowerMsg = message.lowercase()

        // LAYER 2 — SPAM / PROMOTIONAL KEYWORD BLOCKLIST
        val spamKeywords = listOf(
            "loan", "win", "jackpot", "rummy", "bet", "hurry", "claim",
            "apply now", "kyc", "starting", "great fr", "deal",
            "shop now", "buy now", "limited", "exclusive", "launching",
            "introducing", "upgrade", "mah battery", "inch display",
            "processor", "camera", "storage", "ram", "rom",
            "specification", "specifications", "features",
            "sale is live", "credit limit", "activate",
            "congratulations", "eligible", "pre-approved"
        )
        // Note: "sale", "offer", "discount", "free", "cash" removed from blocklist
        // because legitimate SMS can say "cashback credited" or "offer applied"
        // — use full-phrase matches instead to reduce false positives
        if (spamKeywords.any { lowerMsg.contains(it) }) {
            Log.d(TAG, "SMS REJECTED (spam keyword) from $sender")
            return null
        }

        // LAYER 3 — EMOJI BLOCKER (bank SMS never contain emojis)
        val emojiRegex = Regex("[\uD83C\uDF00-\uD83D\uDDFF\uD83E\uDD00-\uD83E\uDDFF\u2600-\u26FF\u2700-\u27BF]")
        if (emojiRegex.containsMatchIn(message)) return null

        // LAYER 4 — TRANSACTION KEYWORD VALIDATION
        // BUG FIX: Use word-boundary-aware matching to avoid "credited" matching
        // inside "credit limit", "accredited" etc.
        val isExpense = lowerMsg.contains("debited") ||
                lowerMsg.contains(" spent ") ||
                lowerMsg.contains("paid to") ||
                lowerMsg.contains("deducted")
        val isIncome = lowerMsg.contains("credited to") ||
                lowerMsg.contains("credited your") ||
                lowerMsg.contains("credited in") ||
                lowerMsg.contains("received in") ||
                lowerMsg.contains("deposited")

        if (!isExpense && !isIncome) {
            Log.d(TAG, "SMS REJECTED (no transaction keyword) from $sender")
            return null
        }

        val type = if (isIncome) ExpenseType.INCOME else ExpenseType.EXPENSE

        // LAYER 5 — AMOUNT EXTRACTION
        val amountRegex = Regex("(?i)(?:₹|rs\\.?|inr)\\s*([0-9,]+(?:\\.[0-9]+)?)")
        val match = amountRegex.find(lowerMsg) ?: return null
        val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null

        // LAYER 6 — SANITY THRESHOLD
        if (amount > 1_000_000.0) return null

        val merchant = extractMerchant(message, isIncome)
        return ParsedExpense(amount, merchant, "SMS", type)
    }

    private fun extractMerchant(message: String, isIncome: Boolean): String {
        val singleLineMsg = message.replace("\r", "").replace("\n", " ")

        val iciciAutoRegex = Regex("(?i)for\\s+([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)\\s+to be debited")
        iciciAutoRegex.find(singleLineMsg)?.let { return it.groupValues[1].trim().uppercase() }

        val debitedForRegex = Regex("(?i)(?:debited|paid)(?:.*?)(?:for|towards)\\s+([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)(?:\\.|,| on | from )")
        debitedForRegex.find(singleLineMsg)?.let {
            val found = it.groupValues[1].trim().uppercase()
            if (!found.contains("A/C") && !found.contains("CARD") && !found.contains("BANK")) return found
        }

        val ecsRegex = Regex("(?i)(?:EMI|ECS) of (?:INR|Rs\\.?|₹)")
        if (ecsRegex.containsMatchIn(singleLineMsg)) return "EMI / Auto-Debit"

        val iciciRegex = Regex("(?i)on\\s+\\d{1,2}-[a-zA-Z]{3}-\\d{2}\\s+on\\s+(.+?)(?:\\.|\\s+Avl Limit)")
        iciciRegex.find(singleLineMsg)?.let { return it.groupValues[1].trim().uppercase() }

        val lines = message.replace("\r", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val avlIndex = lines.indexOfFirst { it.lowercase().contains("avl limit") }
        if (avlIndex > 0) {
            val potentialMerchant = lines[avlIndex - 1]
            if (!potentialMerchant.lowercase().contains("ist") && !potentialMerchant.lowercase().contains("card")) {
                return potentialMerchant.uppercase()
            }
        }

        val sbiRegex = Regex("(?i)(?:Ref(?:\\s|\\:)?|UPI(?:\\/|\\s))\\d{6,12}(?:\\/|\\s)([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)(?:\\.|\\n| on | Avl | Bal |\\,)")
        sbiRegex.find(singleLineMsg)?.let {
            val found = it.groupValues[1].trim().uppercase()
            if (found.isNotBlank()) return found
        }

        if (isIncome) {
            val incomeRegex = Regex("(?i)(?:from|by)\\s+([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)(?:\\.|\\n| on | Avl | Bal | Val | Ref |\\,|\\;|\\(|is )")
            incomeRegex.find(singleLineMsg)?.let {
                val found = it.groupValues[1].trim().uppercase()
                if (!found.contains("A/C") && !found.contains("ACCOUNT")) return found
            }
        }

        val standardRegex = Regex("(?i)(?:at|to|info(?::|\\-)|vpa|upi(?:\\/|\\s))\\s+([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)(?:\\.|\\n| on | Avl | Bal | Val | Ref |\\,|\\;|\\(|is )")
        standardRegex.find(singleLineMsg)?.let {
            val found = it.groupValues[1].trim().uppercase()
            if (!found.contains("A/C") && !found.contains("ACCOUNT") && !found.contains("BANK")) return found
        }

        return if (isIncome) "Deposit/Refund" else "Bank Transfer"
    }

    // ---------------------------------------------------------------------------
    // NOTIFICATION PARSER
    // ---------------------------------------------------------------------------

    fun parseNotification(packageName: String, title: String, text: String): ParsedExpense? {
        val lowerText = text.lowercase()
        val lowerTitle = title.lowercase()

        // ── LAYER 1: STRICT PACKAGE ALLOWLIST ────────────────────────────────────
        val allowedPackages = setOf(
            "com.google.android.apps.nbu.paisa.user",  // Google Pay
            "com.phonepe.app",                          // PhonePe
            "net.one97.paytm",                          // Paytm
            "com.csam.icici.bank.imobile",              // ICICI iMobile
            "com.sbi.SBIFreedomPlus",                  // YONO SBI
            "com.hdfcbank.payzapp",                    // HDFC PayZapp
            "com.axis.mobile",                         // Axis Mobile
            "com.msf.kbank.mobile",                    // Kotak Mobile
            "com.csb.csb_mobile_banking"               // CSB Bank
        )

        if (packageName !in allowedPackages) {
            Log.d(TAG, "REJECTED (not in allowlist): $packageName")
            return null
        }

        // ── LAYER 2: PROMOTIONAL / SPAM KEYWORD FILTER ───────────────────────────
        // BUG FIX 1 (Paytm promo): Paytm sends promotional notifications from
        // the same package as payment notifications. Reject them before keyword check.
        val promoKeywords = listOf(
            "sale is live", "credit limit", "activate", "cashback offer",
            "earn", "reward", "win", "jackpot", "upgrade now", "limited offer",
            "pre-approved", "congratulations", "eligible for", "shop now",
            "buy now", "get up to", "up to ₹", "upto ₹",
            "postpaid", "emi plan", "insurance", "mutual fund", "invest"
        )
        val combined = "$lowerTitle $lowerText"
        if (promoKeywords.any { combined.contains(it) }) {
            Log.d(TAG, "REJECTED (promo keyword) from $packageName: '$title'")
            return null
        }

        // ── LAYER 3: TRANSACTION KEYWORD VALIDATION ──────────────────────────────
        // BUG FIX 2 (GPay "paid you" = income logged as expense):
        // "paid" alone is ambiguous — "X paid you" = INCOME, "you paid X" = EXPENSE.
        // Must check directionality: "paid you" / "paid to you" = income.

        val isExpense = (lowerText.contains("you paid") ||
                lowerText.contains("paid to") ||
                lowerText.contains("sent to") ||
                lowerText.contains("debited") ||
                lowerTitle.contains("you paid") ||
                lowerTitle.contains("sent to") ||
                lowerTitle.contains("debited")) &&
                // Exclude "paid you" patterns
                !lowerText.contains("paid you") &&
                !lowerTitle.contains("paid you")

        // BUG FIX 3 (Paytm "credit limit" matched "credited"):
        // Use exact phrase matching — "credited to" / "credited your" not just "credit"
        val isIncome = lowerText.contains("paid you") ||
                lowerTitle.contains("paid you") ||
                lowerText.contains("received ₹") ||
                lowerText.contains("money received") ||
                lowerText.contains("credited to your") ||
                lowerTitle.contains("credited to your") ||
                lowerText.contains("you received")

        if (!isExpense && !isIncome) {
            Log.d(TAG, "REJECTED (no transaction keyword) from $packageName: title='$title'")
            return null
        }

        // ── LAYER 4: AMOUNT EXTRACTION ───────────────────────────────────────────
        val amountRegex = Regex("(?:₹|rs\\.?|inr)\\s*([0-9,]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
        val match = amountRegex.find(lowerText) ?: amountRegex.find(lowerTitle)

        if (match == null) {
            Log.d(TAG, "REJECTED (no amount) from $packageName: text='$text'")
            return null
        }

        val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null

        // ── LAYER 5: SANITY THRESHOLD ─────────────────────────────────────────────
        if (amount > 1_000_000.0) {
            Log.d(TAG, "REJECTED (amount > 10L) from $packageName: ₹$amount")
            return null
        }

        // ── LAYER 6: MERCHANT EXTRACTION ─────────────────────────────────────────
        var merchant = "Unknown"
        if (isExpense) {
            val toRegex = Regex("(?i)(?:to|paid to|sent to)\\s+([A-Za-z0-9\\s\\.\\@]+)")
            val toMatch = toRegex.find(text) ?: toRegex.find(title)
            if (toMatch != null) {
                merchant = toMatch.groupValues[1]
                    .replace(Regex("(?i)paid|₹|rs\\.?|inr|[0-9,]+(?:\\.[0-9]+)?"), "")
                    .trim()
            }
        } else {
            val fromRegex = Regex("(?i)(?:from|by)\\s+([A-Za-z0-9\\s\\.\\@]+)")
            val fromMatch = fromRegex.find(text) ?: fromRegex.find(title)
            if (fromMatch != null) merchant = fromMatch.groupValues[1].trim()
        }

        if (merchant == "Unknown" || merchant.isBlank()) {
            merchant = title.replace(Regex("(?i)(paid|received|sent|₹|rs\\.?|inr|[0-9,]+(?:\\.[0-9]+)?|to|from)"), "").trim()
        }

        val type = if (isIncome) ExpenseType.INCOME else ExpenseType.EXPENSE
        Log.i(TAG, "ACCEPTED from $packageName: ₹$amount, merchant='$merchant', type=$type")

        return ParsedExpense(amount, merchant.take(30).uppercase(), "UPI App", type)
    }

    // ---------------------------------------------------------------------------
    // CATEGORY DETECTOR
    // ---------------------------------------------------------------------------
    fun detectCategory(desc: String): String {
        val d = desc.lowercase()
        return when {
            d.contains("swiggy") || d.contains("zomato") || d.contains("food") ||
                    d.contains("pizza") || d.contains("restaurant") -> "Food"
            d.contains("uber") || d.contains("ola") || d.contains("rapido") ||
                    d.contains("fuel") || d.contains("petrol") -> "Transport"
            d.contains("jio") || d.contains("airtel") || d.contains("bill") ||
                    d.contains("netflix") || d.contains("bescom") ||
                    d.contains("recharge") -> "Bills"
            d.contains("amazon") || d.contains("flipkart") || d.contains("myntra") ||
                    d.contains("mart") || d.contains("store") -> "Shopping"
            else -> "Automated"
        }
    }
}