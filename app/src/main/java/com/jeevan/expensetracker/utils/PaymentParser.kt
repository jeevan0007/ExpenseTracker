package com.jeevan.expensetracker.utils

data class ParsedExpense(
    val amount: Double,
    val merchant: String,
    val source: String,
    val type: String = "Expense"
)

object PaymentParser {

    // UPDATE: Added sender parameter with a default empty string
    fun parseSms(message: String, sender: String = ""): ParsedExpense? {

        // --- 🛡️ 1. SENDER ID FILTER (Crucial for Indian Banks) ---
        // Indian bank/UPI sender IDs format is usually "XX-HDFCBK" or similar alphabetic codes.
        // If the sender is a standard 10-digit or 12-digit phone number, it's personal or spam. Drop it.
        if (sender.matches(Regex("^[+0-9]{10,13}$"))) return null

        val lowerMsg = message.lowercase()

        // --- 🛡️ 2. NEGATIVE KEYWORD BLOCKLIST ---
        // If it contains promotional words, immediately discard.
        val spamKeywords = listOf(
            "loan", "cash", "win", "offer", "discount", "jackpot",
            "rummy", "bet", "free", "hurry", "claim", "apply now", "kyc"
        )
        if (spamKeywords.any { lowerMsg.contains(it) }) return null

        // --- 🛡️ 3. EMOJI BLOCKER ---
        // Official bank messages NEVER use emojis.
        val emojiRegex = Regex("[\\x{1F300}-\\x{1F6FF}|\\x{2600}-\\x{26FF}|\\x{2700}-\\x{27BF}]")
        if (emojiRegex.containsMatchIn(message)) return null

        // --- 4. Standard Transaction Validation ---
        val isExpense = lowerMsg.contains("debited") || lowerMsg.contains("spent") || lowerMsg.contains("paid") || lowerMsg.contains("deducted")
        val isIncome = lowerMsg.contains("credited") || lowerMsg.contains("received") || lowerMsg.contains("deposited")

        // If it's neither, ignore the SMS
        if (!isExpense && !isIncome) return null

        val type = if (isIncome) "Income" else "Expense"

        // --- 5. Extract Amount ---
        val amountRegex = Regex("(?i)(?:₹|rs\\.?|inr)\\s*([0-9,]+(?:\\.[0-9]+)?)")
        val match = amountRegex.find(lowerMsg)

        if (match != null) {
            val amountStr = match.groupValues[1].replace(",", "")
            val amount = amountStr.toDoubleOrNull() ?: return null

            // --- 🛡️ 6. SANITY THRESHOLD ---
            // If the automated amount is suspiciously massive (e.g., > 10 Lakhs), ignore it to be safe.
            if (amount > 1000000.0) return null

            val merchant = extractMerchant(message, isIncome)
            return ParsedExpense(amount, merchant, "SMS", type)
        }
        return null
    }
        private fun extractMerchant(message: String, isIncome: Boolean): String {
        val singleLineMsg = message.replace("\r", "").replace("\n", " ")

        // --- 🚨 NEW: ICICI Standing Instruction / Auto Debit ---
        // Catches: "...for NETFLIX to be debited..."
        val iciciAutoRegex = Regex("(?i)for\\s+([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)\\s+to be debited")
        val iciciAutoMatch = iciciAutoRegex.find(singleLineMsg)
        if (iciciAutoMatch != null) return iciciAutoMatch.groupValues[1].trim().uppercase()

        // --- 🚨 NEW: Generic "debited/paid for/towards" ---
        // Catches: "Rs 500 debited from A/C for Amazon."
        val debitedForRegex = Regex("(?i)(?:debited|paid)(?:.*?)(?:for|towards)\\s+([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)(?:\\.|,| on | from )")
        val debitedForMatch = debitedForRegex.find(singleLineMsg)
        if (debitedForMatch != null) {
            val found = debitedForMatch.groupValues[1].trim().uppercase()
            if (!found.contains("A/C") && !found.contains("CARD") && !found.contains("BANK")) return found
        }

        // --- 🚨 NEW: EMI / ECS Auto-Clearances ---
        // Catches: "EMI of Rs. 99650 is scheduled..."
        val ecsRegex = Regex("(?i)(?:EMI|ECS) of (?:INR|Rs\\.?|₹)")
        if (ecsRegex.containsMatchIn(singleLineMsg)) {
            return "EMI / Auto-Debit"
        }

        // 1. ICICI BANK: "on [Date] on [Merchant]. Avl Limit"
        val iciciRegex = Regex("(?i)on\\s+\\d{1,2}-[a-zA-Z]{3}-\\d{2}\\s+on\\s+(.+?)(?:\\.|\\s+Avl Limit)")
        val iciciMatch = iciciRegex.find(singleLineMsg)
        if (iciciMatch != null) return iciciMatch.groupValues[1].trim().uppercase()

        // 2. AXIS BANK: Multi-line parsing where merchant is exactly above "Avl Limit"
        val lines = message.replace("\r", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val avlIndex = lines.indexOfFirst { it.lowercase().contains("avl limit") }
        if (avlIndex > 0) {
            val potentialMerchant = lines[avlIndex - 1]
            if (!potentialMerchant.lowercase().contains("ist") && !potentialMerchant.lowercase().contains("card")) {
                return potentialMerchant.uppercase()
            }
        }

        // 3. SBI BANK: Looks for "Ref: UPI/012345/MerchantName"
        val sbiRegex = Regex("(?i)(?:Ref(?:\\s|\\:)?|UPI(?:\\/|\\s))\\d{6,12}(?:\\/|\\s)([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)(?:\\.|\\n| on | Avl | Bal |\\,)")
        val sbiMatch = sbiRegex.find(singleLineMsg)
        if (sbiMatch != null && sbiMatch.groupValues[1].isNotBlank()) return sbiMatch.groupValues[1].trim().uppercase()

        // 4. INCOME CATCHER: "received from [Name]" or "credited by [Name]"
        if (isIncome) {
            val incomeRegex = Regex("(?i)(?:from|by)\\s+([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)(?:\\.|\\n| on | Avl | Bal | Val | Ref |\\,|\\;|\\(|is )")
            val incomeMatch = incomeRegex.find(singleLineMsg)
            if (incomeMatch != null) {
                val found = incomeMatch.groupValues[1].trim().uppercase()
                if (!found.contains("A/C") && !found.contains("ACCOUNT")) return found
            }
        }

        // 5. STANDARD BANKS (HDFC, Kotak, PNB): Looks for "at", "to", "info:", "vpa"
        val standardRegex = Regex("(?i)(?:at|to|info(?::|\\-)|vpa|upi(?:\\/|\\s))\\s+([A-Za-z0-9\\s\\.\\&@\\-\\*]+?)(?:\\.|\\n| on | Avl | Bal | Val | Ref |\\,|\\;|\\(|is )")
        val standardMatch = standardRegex.find(singleLineMsg)
        if (standardMatch != null) {
            val found = standardMatch.groupValues[1].trim().uppercase()
            if (!found.contains("A/C") && !found.contains("ACCOUNT") && !found.contains("BANK")) return found
        }

        return if (isIncome) "Deposit/Refund" else "Bank Transfer"
    }

    fun parseNotification(packageName: String, title: String, text: String): ParsedExpense? {
        val lowerText = text.lowercase()
        val lowerTitle = title.lowercase()

        // Added ICICI iMobile, PayZapp, and Yono SBI to the valid list
        val validPackages = listOf(
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.amazon.mShop.android.shopping",
            "com.csam.icici.bank.imobile",
            "com.sbi.SBIFreedomPlus",
            "com.hdfcbank.payzapp"
        )

        if (packageName in validPackages) {
            val isExpense = lowerText.contains("paid") || lowerText.contains("sent") || lowerTitle.contains("paid")
            val isIncome = lowerText.contains("received") || lowerText.contains("credited") || lowerTitle.contains("received")

            if (isExpense || isIncome || lowerText.contains("₹")) {
                val amountRegex = Regex("(?:₹|rs\\.?|inr)\\s*([0-9,]+(?:\\.[0-9]+)?)")
                val match = amountRegex.find(lowerText) ?: amountRegex.find(lowerTitle)

                if (match != null) {
                    val amountStr = match.groupValues[1].replace(",", "")
                    val amount = amountStr.toDoubleOrNull() ?: return null

                    var merchant = "Unknown"
                    if (isExpense) {
                        val toRegex = Regex("(?i)(?:to|paid)\\s+([A-Za-z0-9\\s\\.\\@]+)")
                        val toMatch = toRegex.find(text) ?: toRegex.find(title)
                        if (toMatch != null) merchant = toMatch.groupValues[1].replace(Regex("(?i)paid|₹|rs\\.?|inr|[0-9,]+(?:\\.[0-9]+)?"), "").trim()
                    } else if (isIncome) {
                        val fromRegex = Regex("(?i)from\\s+([A-Za-z0-9\\s\\.\\@]+)")
                        val fromMatch = fromRegex.find(text) ?: fromRegex.find(title)
                        if (fromMatch != null) merchant = fromMatch.groupValues[1].trim()
                    }

                    if (merchant == "Unknown" || merchant.isBlank()) {
                        merchant = title.replace(Regex("(?i)(paid|received|sent|₹|rs\\.?|inr|[0-9,]+(?:\\.[0-9]+)?|to|from)"), "").trim()
                    }

                    val type = if (isIncome) "Income" else "Expense"
                    return ParsedExpense(amount, merchant.uppercase(), "UPI App", type)
                }
            }
        }
        return null
    }
}