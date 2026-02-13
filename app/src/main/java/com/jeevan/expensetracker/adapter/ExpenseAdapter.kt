package com.jeevan.expensetracker.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jeevan.expensetracker.R
import com.jeevan.expensetracker.data.Expense
import java.text.SimpleDateFormat
import java.util.*

class ExpenseAdapter(
    private val onItemLongClick: (Expense) -> Unit,
    private val onItemClick: (Expense) -> Unit
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    private var expenses = emptyList<Expense>()
    private var lastPosition = -1

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

        val emoji = getCategoryEmoji(currentExpense.category)
        holder.tvCategoryIcon.text = emoji
        holder.tvCategory.text = currentExpense.category

        if (currentExpense.isRecurring) {
            holder.tvDescription.text = "🔄 ${currentExpense.description}"
        } else {
            holder.tvDescription.text = currentExpense.description
        }

        if (currentExpense.type == "Income") {
            holder.tvAmount.text = "+ ₹${String.format("%.2f", currentExpense.amount)}"
            holder.tvAmount.setTextColor(Color.parseColor("#388E3C"))
        } else {
            holder.tvAmount.text = "- ₹${String.format("%.2f", currentExpense.amount)}"
            holder.tvAmount.setTextColor(Color.parseColor("#D32F2F"))
        }

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        holder.tvDate.text = dateFormat.format(Date(currentExpense.date))

        // ULTRA PREMIUM FEATURE 1: Squish & Spring Touch Physics
        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(150).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(300)
                        .setInterpolator(OvershootInterpolator(2f)).start()
                }
            }
            false // Pass on to click listeners
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(currentExpense)
            true
        }

        holder.itemView.setOnClickListener {
            onItemClick(currentExpense)
        }

        setPremiumEntranceAnimation(holder.itemView, position)
    }

    // ULTRA PREMIUM FEATURE 2: Staggered Spring Cascade
    private fun setPremiumEntranceAnimation(viewToAnimate: View, position: Int) {
        if (position > lastPosition) {
            viewToAnimate.translationY = 150f
            viewToAnimate.alpha = 0f
            viewToAnimate.scaleX = 0.8f
            viewToAnimate.scaleY = 0.8f

            viewToAnimate.animate()
                .translationY(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(OvershootInterpolator(1.8f)) // Bouncy overshoot
                .setDuration(500)
                .setStartDelay((position * 40).toLong()) // Staggered domino effect
                .start()

            lastPosition = position
        }
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

    override fun getItemCount() = expenses.size

    fun setExpenses(expenses: List<Expense>) {
        this.expenses = expenses
        lastPosition = -1  // Reset animation state
        notifyDataSetChanged()
    }
}