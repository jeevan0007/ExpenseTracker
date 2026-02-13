package com.jeevan.expensetracker.utils


data class ParsedExpense(val amount: Double, val merchant: String, val source: String)

object PaymentParser {

    fun parseSms(message: String): ParsedExpense? {
        val lowerMsg = message.lowercase()
        // Broad check for transactional keywords
        if (!lowerMsg.contains("debited") && !lowerMsg.contains("spent") && !lowerMsg.contains("paid")) return null

        // Regex to extract amount (Looks for Rs., INR, etc. followed by numbers)
        val amountRegex = Regex("(?i)(?:rs\\.?|inr)\\s*([0-9,]+(?:\\.[0-9]+)?)")
        val match = amountRegex.find(lowerMsg)

        if (match != null) {
            val amountStr = match.groupValues[1].replace(",", "")
            val amount = amountStr.toDoubleOrNull() ?: return null

            // Regex to extract merchant (Looks for words after UPI, VPA, or "to")
            val upiRegex = Regex("(?i)(?:vpa|upi|to)\\s*([a-zA-Z0-9.@]+)")
            val upiMatch = upiRegex.find(lowerMsg)
            val merchant = upiMatch?.groupValues?.get(1) ?: "Bank Transfer"

            return ParsedExpense(amount, merchant, "SMS")
        }
        return null
    }

    fun parseNotification(packageName: String, title: String, text: String): ParsedExpense? {
        val lowerText = text.lowercase()
        val lowerTitle = title.lowercase()

        // Target specific UPI apps
        val validPackages = listOf(
            "com.google.android.apps.nbu.paisa.user", // GPay
            "com.phonepe.app",                        // PhonePe
            "net.one97.paytm",                        // Paytm
            "in.amazon.mShop.android.shopping"        // Amazon Pay
        )

        if (packageName in validPackages) {
            if (lowerText.contains("paid") || lowerText.contains("₹") || lowerTitle.contains("paid")) {
                // Look for ₹ symbol or Rs.
                val amountRegex = Regex("(?:₹|rs\\.?|inr)\\s*([0-9,]+(?:\\.[0-9]+)?)")
                val match = amountRegex.find(lowerText) ?: amountRegex.find(lowerTitle)

                if (match != null) {
                    val amountStr = match.groupValues[1].replace(",", "")
                    val amount = amountStr.toDoubleOrNull() ?: return null

                    // Cleanup title to get merchant name
                    val merchant = title.replace(Regex("(?i)paid (?:₹|rs\\.?|inr)\\s*[0-9,]+(?:\\.[0-9]+)? to "), "")
                    return ParsedExpense(amount, merchant.trim(), "UPI App")
                }
            }
        }
        return null
    }
}