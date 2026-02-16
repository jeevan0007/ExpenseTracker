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

        // 1. Emoji & Text
        val emoji = getCategoryEmoji(currentExpense.category)
        holder.tvCategoryIcon.text = emoji
        holder.tvCategory.text = currentExpense.category

        if (currentExpense.isRecurring) {
            holder.tvDescription.text = "🔄 ${currentExpense.description}"
        } else {
            holder.tvDescription.text = currentExpense.description
        }

        // 2. Color Coding
        if (currentExpense.type == "Income") {
            holder.tvAmount.text = "+ ₹${String.format("%.2f", currentExpense.amount)}"
            holder.tvAmount.setTextColor(Color.parseColor("#388E3C")) // Green
        } else {
            holder.tvAmount.text = "- ₹${String.format("%.2f", currentExpense.amount)}"
            holder.tvAmount.setTextColor(Color.parseColor("#D32F2F")) // Red
        }

        // 3. Date
        try {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            holder.tvDate.text = dateFormat.format(Date(currentExpense.date))
        } catch (e: Exception) {
            holder.tvDate.text = "Invalid Date"
        }

        // --- NEW: SQUISH PHYSICS (Safe Version) ---
        // This only shrinks the item when you press it.
        // It does NOT hide the item on load, so no "Ghost List" bug!
        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Shrink slightly when pressed
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Bounce back to normal size
                    v.animate().scaleX(1f).scaleY(1f).setDuration(300)
                        .setInterpolator(OvershootInterpolator(2f)).start()
                }
            }
            false // Important: Return false so the click listener still works
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(currentExpense)
            true
        }

        holder.itemView.setOnClickListener {
            onItemClick(currentExpense)
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
        notifyDataSetChanged()
    }
}