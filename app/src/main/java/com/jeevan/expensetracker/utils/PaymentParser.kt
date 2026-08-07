package com.jeevan.expensetracker.utils

data class ParsedExpense(
    val amount: Double,
    val merchant: String,
    val source: String,
    val type: String = ExpenseType.EXPENSE
)

object PaymentParser {

    // ---------------------------------------------------------------------------
    // SMS PARSER
    // ---------------------------------------------------------------------------

    fun parseSms(message: String, sender: String = ""): ParsedExpense? {

        // LAYER 1 — SENDER ID FILTER
        // Indian bank/UPI sender IDs are always alphanumeric codes like "VK-HDFCBK"
        // or "JD-PAYTM". A plain phone number is always personal or promotional — drop it.
        if (sender.matches(Regex("^[+0-9]{10,13}$"))) return null

        val lowerMsg = message.lowercase()

        // LAYER 2 — SPAM / PROMOTIONAL KEYWORD BLOCKLIST
        val spamKeywords = listOf(
            "loan", "cash", "win", "offer", "discount", "jackpot",
            "rummy", "bet", "free", "hurry", "claim", "apply now", "kyc",
            // --- ADDED: e-commerce promotional keywords ---
            "starting", "great fr", "deal", "sale", "off", "shop now",
            "buy now", "limited", "exclusive", "launching", "introducing",
            "upgrade", "mah battery", "inch display", "processor", "camera",
            "storage", "ram", "rom", "specification", "specifications", "features"
        )
        if (spamKeywords.any { lowerMsg.contains(it) }) return null

        // LAYER 3 — EMOJI BLOCKER
        // Official bank SMS messages never contain emojis.
        // Kotlin uses \uXXXX (4-hex-digit) escapes only. Supplementary plane emoji
        // (U+1F300 and above) are represented as surrogate pairs: \uD83C\uDF00 etc.
        // The ranges below cover the most common emoji blocks via their surrogate ranges
        // plus the Basic Multilingual Plane symbol blocks (U+2600–U+27BF).
        val emojiRegex = Regex("[\uD83C\uDF00-\uD83D\uDDFF\uD83E\uDD00-\uD83E\uDDFF\u2600-\u26FF\u2700-\u27BF]")
        if (emojiRegex.containsMatchIn(message)) return null

        // LAYER 4 — TRANSACTION KEYWORD VALIDATION
        val isExpense = lowerMsg.contains("debited") || lowerMsg.contains("spent") ||
                lowerMsg.contains("paid") || lowerMsg.contains("deducted")
        val isIncome = lowerMsg.contains("credited") || lowerMsg.contains("received") ||
                lowerMsg.contains("deposited")

        if (!isExpense && !isIncome) return null

        val type = if (isIncome) ExpenseType.INCOME else ExpenseType.EXPENSE

        // LAYER 5 — AMOUNT EXTRACTION
        val amountRegex = Regex("(?i)(?:₹|rs\\.?|inr)\\s*([0-9,]+(?:\\.[0-9]+)?)")
        val match = amountRegex.find(lowerMsg) ?: return null

        val amountStr = match.groupValues[1].replace(",", "")
        val amount = amountStr.toDoubleOrNull() ?: return null

        // LAYER 6 — SANITY THRESHOLD (ignore if > 10 Lakhs)
        if (amount > 1_000_000.0) return null

        val merchant = extractMerchant(message, isIncome)
        return ParsedExpense(amount, merchant, "SMS", type)
    }

    private fun extractMerchant(message: String, isIncome: Boolean): String {
        val singleLineMsg = message.replace("\r", "").replace("\n", " ")

        // ICICI Standing Instruction / Auto Debit
        val iciciAutoRegex = Regex("(?i)for\\s+([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)\\s+to be debited")
        iciciAutoRegex.find(singleLineMsg)?.let { return it.groupValues[1].trim().uppercase() }

        // Generic "debited/paid for/towards"
        val debitedForRegex = Regex("(?i)(?:debited|paid)(?:.*?)(?:for|towards)\\s+([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)(?:\\.|,| on | from )")
        debitedForRegex.find(singleLineMsg)?.let {
            val found = it.groupValues[1].trim().uppercase()
            if (!found.contains("A/C") && !found.contains("CARD") && !found.contains("BANK")) return found
        }

        // EMI / ECS Auto-Clearances
        val ecsRegex = Regex("(?i)(?:EMI|ECS) of (?:INR|Rs\\.?|₹)")
        if (ecsRegex.containsMatchIn(singleLineMsg)) return "EMI / Auto-Debit"

        // ICICI BANK: "on [Date] on [Merchant]. Avl Limit"
        val iciciRegex = Regex("(?i)on\\s+\\d{1,2}-[a-zA-Z]{3}-\\d{2}\\s+on\\s+(.+?)(?:\\.|\\s+Avl Limit)")
        iciciRegex.find(singleLineMsg)?.let { return it.groupValues[1].trim().uppercase() }

        // AXIS BANK: merchant is the line above "Avl Limit"
        val lines = message.replace("\r", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val avlIndex = lines.indexOfFirst { it.lowercase().contains("avl limit") }
        if (avlIndex > 0) {
            val potentialMerchant = lines[avlIndex - 1]
            if (!potentialMerchant.lowercase().contains("ist") && !potentialMerchant.lowercase().contains("card")) {
                return potentialMerchant.uppercase()
            }
        }

        // SBI BANK: "Ref: UPI/012345/MerchantName"
        val sbiRegex = Regex("(?i)(?:Ref(?:\\s|\\:)?|UPI(?:\\/|\\s))\\d{6,12}(?:\\/|\\s)([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)(?:\\.|\\n| on | Avl | Bal |\\,)")
        sbiRegex.find(singleLineMsg)?.let {
            val found = it.groupValues[1].trim().uppercase()
            if (found.isNotBlank()) return found
        }

        // INCOME: "received from [Name]" or "credited by [Name]"
        if (isIncome) {
            val incomeRegex = Regex("(?i)(?:from|by)\\s+([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)(?:\\.|\\n| on | Avl | Bal | Val | Ref |\\,|\\;|\\(|is )")
            incomeRegex.find(singleLineMsg)?.let {
                val found = it.groupValues[1].trim().uppercase()
                if (!found.contains("A/C") && !found.contains("ACCOUNT")) return found
            }
        }

        // STANDARD BANKS (HDFC, Kotak, PNB): "at", "to", "info:", "vpa"
        val standardRegex = Regex("(?i)(?:at|to|info(?::|\\-)|vpa|upi(?:\\/|\\s))\\s+([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)(?:\\.|\\n| on | Avl | Bal | Val | Ref |\\,|\\;|\\(|is )")
        standardRegex.find(singleLineMsg)?.let {
            val found = it.groupValues[1].trim().uppercase()
            if (!found.contains("A/C") && !found.contains("ACCOUNT") && !found.contains("BANK")) return found
        }

        return if (isIncome) "Deposit/Refund" else "Bank Transfer"
    }

    // ---------------------------------------------------------------------------
    // NOTIFICATION PARSER  (UPI Apps: GPay, PhonePe, Paytm, ICICI, SBI, HDFC)
    // ---------------------------------------------------------------------------

    fun parseNotification(packageName: String, title: String, text: String): ParsedExpense? {
        val lowerText = text.lowercase()
        val lowerTitle = title.lowercase()

        // ── LAYER 1: STRICT PACKAGE ALLOWLIST ───────────────────────────────────
        // Only these packages represent genuine payment apps we want to track.
        // Everything else — including Amazon, Flipkart, Myntra — is rejected here
        // before any further processing.
        val allowedPackages = setOf(
            "com.google.android.apps.nbu.paisa.user",  // Google Pay
            "com.phonepe.app",                          // PhonePe
            "net.one97.paytm",                          // Paytm
            "com.csam.icici.bank.imobile",             // ICICI iMobile
            "com.sbi.SBIFreedomPlus",                  // YONO SBI
            "com.hdfcbank.payzapp",                    // HDFC PayZapp
            "com.axis.mobile",                         // Axis Mobile
            "com.msf.kbank.mobile",                    // Kotak Mobile
            "com.csb.csb_mobile_banking"               // CSB Bank
        )

        if (packageName !in allowedPackages) {
            android.util.Log.d("PaymentParser", "REJECTED (not in allowlist): $packageName")
            return null
        }

        // ── LAYER 2: TRANSACTION KEYWORD VALIDATION ─────────────────────────────
        // The notification must explicitly mention a payment action.
        val isExpense = lowerText.contains("paid") || lowerText.contains("sent") ||
                lowerTitle.contains("paid") || lowerTitle.contains("sent") ||
                lowerText.contains("debited") || lowerTitle.contains("debited")
        val isIncome = lowerText.contains("received") || lowerText.contains("credited") ||
                lowerTitle.contains("received") || lowerTitle.contains("credited")

        if (!isExpense && !isIncome) {
            android.util.Log.d("PaymentParser", "REJECTED (no transaction keyword) from $packageName: title='$title'")
            return null
        }

        // ── LAYER 3: AMOUNT EXTRACTION ───────────────────────────────────────────
        val amountRegex = Regex("(?:₹|rs\\.?|inr)\\s*([0-9,]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
        val match = amountRegex.find(lowerText) ?: amountRegex.find(lowerTitle)

        if (match == null) {
            android.util.Log.d("PaymentParser", "REJECTED (no amount found) from $packageName: text='$text'")
            return null
        }

        val amountStr = match.groupValues[1].replace(",", "")
        val amount = amountStr.toDoubleOrNull() ?: return null

        // ── LAYER 4: SANITY THRESHOLD ────────────────────────────────────────────
        if (amount > 1_000_000.0) {
            android.util.Log.d("PaymentParser", "REJECTED (amount > 10L): ₹$amount from $packageName")
            return null
        }

        // ── LAYER 5: MERCHANT EXTRACTION ─────────────────────────────────────────
        var merchant = "Unknown"
        if (isExpense) {
            val toRegex = Regex("(?i)(?:to|paid)\\s+([A-Za-z0-9\\s\\.\\@]+)")
            val toMatch = toRegex.find(text) ?: toRegex.find(title)
            if (toMatch != null) {
                merchant = toMatch.groupValues[1]
                    .replace(Regex("(?i)paid|₹|rs\\.?|inr|[0-9,]+(?:\\.[0-9]+)?"), "")
                    .trim()
            }
        } else if (isIncome) {
            val fromRegex = Regex("(?i)from\\s+([A-Za-z0-9\\s\\.\\@]+)")
            val fromMatch = fromRegex.find(text) ?: fromRegex.find(title)
            if (fromMatch != null) merchant = fromMatch.groupValues[1].trim()
        }

        if (merchant == "Unknown" || merchant.isBlank()) {
            merchant = title.replace(Regex("(?i)(paid|received|sent|₹|rs\\.?|inr|[0-9,]+(?:\\.[0-9]+)?|to|from)"), "").trim()
        }

        val type = if (isIncome) ExpenseType.INCOME else ExpenseType.EXPENSE

        android.util.Log.d("PaymentParser", "ACCEPTED from $packageName: ₹$amount, merchant='$merchant', type=$type")

        return ParsedExpense(amount, merchant.take(30).uppercase(), "UPI App", type)
    }

    // ---------------------------------------------------------------------------
    // UNIFIED CATEGORY DETECTOR
    // Used by both SmsReceiver and UpiNotificationListener.
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