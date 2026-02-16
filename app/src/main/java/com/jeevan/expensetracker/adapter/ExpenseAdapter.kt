package com.jeevan.expensetracker.adapter

import android.graphics.Color
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jeevan.expensetracker.R
import com.jeevan.expensetracker.data.Expense
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ExpenseAdapter(
    private val onItemLongClick: (Expense) -> Unit,
    private val onItemClick: (Expense) -> Unit
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    private var expenses = emptyList<Expense>()
    private var lastPosition = -1

    // --- CURRENCY STATE (Added for Travel Mode) ---
    private var exchangeRate = 1.0 // Default: 1.0 (No conversion)
    private var targetLocale = Locale("en", "IN") // Default: India (₹)

    class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCategoryIcon: TextView = itemView.findViewById(R.id.tvCategoryIcon)
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val currentExpense = expenses[position]

        // --- 1. DATA BINDING (Your Original Logic) ---
        val emoji = getCategoryEmoji(currentExpense.category)
        holder.tvCategoryIcon.text = emoji
        holder.tvCategory.text = currentExpense.category

        if (currentExpense.isRecurring) {
            holder.tvDescription.text = "🔄 ${currentExpense.description}"
        } else {
            holder.tvDescription.text = currentExpense.description
        }

        // --- NEW CURRENCY MATH ---
        // Calculate Converted Amount
        val convertedAmount = currentExpense.amount * exchangeRate

        // Format (Automatically adds $, €, ¥, ₹ based on Locale)
        val currencyFormat = NumberFormat.getCurrencyInstance(targetLocale)
        val formattedAmount = currencyFormat.format(convertedAmount)

        if (currentExpense.type == "Income") {
            // Added space for readability "+ $100.00"
            holder.tvAmount.text = "+ $formattedAmount"
            holder.tvAmount.setTextColor(Color.parseColor("#388E3C"))
        } else {
            holder.tvAmount.text = "- $formattedAmount"
            holder.tvAmount.setTextColor(Color.parseColor("#D32F2F"))
        }

        try {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            holder.tvDate.text = dateFormat.format(Date(currentExpense.date))
        } catch (e: Exception) {
            holder.tvDate.text = "Invalid Date"
        }

        // --- 2. SCROLL ANIMATION (Your Original Logic) ---
        setAnimation(holder.itemView, position)

        // --- 3. PHYSICS + CLICKS + LONG PRESS (Your Original Logic) ---
        val gestureDetector = GestureDetector(holder.itemView.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onItemClick(currentExpense)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                onItemLongClick(currentExpense)
                holder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        })

        holder.itemView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(300)
                        .setInterpolator(OvershootInterpolator(2f)).start()
                }
            }
            true
        }
    }

    private fun setAnimation(viewToAnimate: View, position: Int) {
        if (position > lastPosition) {
            val animation = AnimationUtils.loadAnimation(viewToAnimate.context, android.R.anim.slide_in_left)
            animation.duration = 400
            viewToAnimate.startAnimation(animation)
            lastPosition = position
        }
    }

    override fun onViewDetachedFromWindow(holder: ExpenseViewHolder) {
        holder.itemView.clearAnimation()
    }

    fun setExpenses(expenses: List<Expense>) {
        this.expenses = expenses
        this.lastPosition = -1
        notifyDataSetChanged()
    }

    fun getExpenseAt(position: Int): Expense {
        return expenses[position]
    }

    override fun getItemCount() = expenses.size

    // --- NEW HELPER: Called by MainActivity to switch currency ---
    fun updateCurrency(rate: Double, locale: Locale) {
        this.exchangeRate = rate
        this.targetLocale = locale
        notifyDataSetChanged() // Refreshes the list instantly
    }

    private fun getCategoryEmoji(category: String): String {
        return when (category) {
            "Food" -> "🍔"
            "Transport" -> "🚗"
            "Shopping" -> "🛍️"
            "Entertainment" -> "🎬"
            "Bills" -> "💡"
            "Healthcare" -> "🏥"
            "Automated" -> "🤖"
            "Salary" -> "💵"
            "Other" -> "📌"
            else -> "💰"
        }
    }
}