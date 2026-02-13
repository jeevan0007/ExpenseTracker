package com.jeevan.expensetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.jeevan.expensetracker.utils.PaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                val pdus = bundle.get("pdus") as Array<*>
                for (pdu in pdus) {
                    val smsMessage = SmsMessage.createFromPdu(pdu as ByteArray)
                    val messageBody = smsMessage.messageBody

                    val parsed = PaymentParser.parseSms(messageBody)
                    if (parsed != null) {
                        saveExpenseToDb(context, parsed.amount, "Auto: ${parsed.merchant} (${parsed.source})")
                    }
                }
            }
        }
    }

    private fun saveExpenseToDb(context: Context, amount: Double, description: String) {
        val dao = ExpenseDatabase.getDatabase(context).expenseDao()
        CoroutineScope(Dispatchers.IO).launch {
            val expense = Expense(
                amount = amount,
                category = "Automated", // Sets a default category for these
                description = description,
                date = Date().time
            )
            dao.insert(expense)
        }
    }
}