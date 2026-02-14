package com.jeevan.expensetracker

import com.jeevan.expensetracker.utils.PaymentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentParserTest {

    @Test
    fun parseSms_extractsAmountAndMerchant() {
        val message = "INR 1,250.50 debited via UPI to freshmart@upi"

        val parsed = PaymentParser.parseSms(message)

        assertNotNull(parsed)
        assertEquals(1250.50, parsed?.amount ?: 0.0, 0.0)
        assertEquals("freshmart@upi", parsed?.merchant)
    }

    @Test
    fun parseSms_returnsNullWhenNoTransactionWords() {
        val message = "Your OTP is 123456"

        val parsed = PaymentParser.parseSms(message)

        assertNull(parsed)
    }

    @Test
    fun parseNotification_extractsUpiAmount() {
        val packageName = "com.google.android.apps.nbu.paisa.user"
        val title = "Paid ₹200 to Metro Store"
        val text = "Payment successful"

        val parsed = PaymentParser.parseNotification(packageName, title, text)

        assertNotNull(parsed)
        assertEquals(200.0, parsed?.amount ?: 0.0, 0.0)
        assertEquals("UPI App", parsed?.source)
    }

    @Test
    fun parseNotification_ignoresUnsupportedPackage() {
        val parsed = PaymentParser.parseNotification(
            packageName = "com.random.app",
            title = "Paid ₹200",
            text = "sent"
        )

        assertNull(parsed)
    }
}
