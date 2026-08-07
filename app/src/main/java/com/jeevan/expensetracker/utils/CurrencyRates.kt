package com.jeevan.expensetracker.utils

import java.util.Locale

/**
 * Single source of truth for INR-based exchange rates and locale mappings.
 * Previously duplicated in MainActivity and TripDashboardActivity independently.
 * Any rate update only needs to happen here.
 */
object CurrencyRates {

    /** Display labels shown in the currency picker dialog, in order. */
    val displayLabels = arrayOf(
        "INR (Base)",
        "USD ($)",
        "EUR (Euro)",
        "GBP (Pound)",
        "JPY (Yen)",
        "CNY (Yuan)",
        "AUD (A$)",
        "SGD (S$)"
    )

    /** All supported currency codes (INR is the base - rate 1.0). */
    val supportedCodes = listOf("INR", "USD", "EUR", "GBP", "JPY", "CNY", "AUD", "SGD")

    /** Returns the INR to [currency] conversion rate. Returns 1.0 for INR or unknown codes. */
    fun exchangeRate(currency: String): Double = when (currency.uppercase()) {
        "USD" -> 0.011
        "EUR" -> 0.0093
        "GBP" -> 0.0081
        "JPY" -> 1.69
        "CNY" -> 0.076
        "AUD" -> 0.017
        "SGD" -> 0.015
        else  -> 1.0
    }

    /** Returns the display Locale for a given currency code. Defaults to en_IN. */
    fun localeFor(currency: String): Locale = when (currency.uppercase()) {
        "USD" -> Locale.US
        "EUR" -> Locale.GERMANY
        "GBP" -> Locale.UK
        "JPY" -> Locale.JAPAN
        "CNY" -> Locale.CHINA
        "AUD" -> Locale("en", "AU")
        "SGD" -> Locale("en", "SG")
        else  -> Locale("en", "IN")
    }
}

// ── SharedPreferences helpers ─────────────────────────────────────────────────
// Single source of truth for reading and writing the active currency preference.
// Previously duplicated in MainActivity, ChartsActivity, ReimbursementActivity,
// RecycleBinActivity, and BudgetWorker with different key spellings and defaults.

private const val PREFS_NAME         = "ExpenseTracker"
private const val KEY_CURRENCY_RATE  = "currency_rate"
private const val KEY_CURRENCY_LANG  = "currency_lang"
private const val KEY_CURRENCY_COUNTRY = "currency_country"

data class SavedCurrency(val rate: Double, val locale: java.util.Locale)

/**
 * Reads the saved currency rate and locale from SharedPreferences.
 * Returns INR (rate = 1.0, locale = en_IN) if nothing has been saved.
 */
fun android.content.Context.loadSavedCurrency(): SavedCurrency {
    val prefs = getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val rate    = prefs.getFloat(KEY_CURRENCY_RATE, 1.0f).toDouble()
    val lang    = prefs.getString(KEY_CURRENCY_LANG, "en") ?: "en"
    val country = prefs.getString(KEY_CURRENCY_COUNTRY, "IN") ?: "IN"
    return SavedCurrency(rate, java.util.Locale(lang, country))
}

/**
 * Persists the active currency rate and locale to SharedPreferences.
 */
fun android.content.Context.saveCurrencyPrefs(rate: Double, locale: java.util.Locale) {
    getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE).edit()
        .putFloat(KEY_CURRENCY_RATE, rate.toFloat())
        .putString(KEY_CURRENCY_LANG, locale.language)
        .putString(KEY_CURRENCY_COUNTRY, locale.country)
        .apply()
}