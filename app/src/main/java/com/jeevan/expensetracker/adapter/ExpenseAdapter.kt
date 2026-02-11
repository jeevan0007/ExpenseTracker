package com.jeevan.expensetracker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
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

        // Get emoji for category
        val emoji = getCategoryEmoji(currentExpense.category)
        holder.tvCategoryIcon.text = emoji
        holder.tvCategory.text = currentExpense.category
        holder.tvDescription.text = currentExpense.description
        holder.tvAmount.text = "₹${String.format("%.2f", currentExpense.amount)}"

        // Format date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        holder.tvDate.text = dateFormat.format(Date(currentExpense.date))

        // Long click to delete
        holder.itemView.setOnLongClickListener {
            onItemLongClick(currentExpense)
            true
        }

        // Click to edit
        holder.itemView.setOnClickListener {
            onItemClick(currentExpense)
        }

        // Smooth slide-up animation
        setAnimation(holder.itemView, position)
    }

    private fun setAnimation(viewToAnimate: View, position: Int) {
        // If the bound view wasn't previously displayed on screen, it's animated
        if (position > lastPosition) {
            val animation = AnimationUtils.loadAnimation(viewToAnimate.context, R.anim.slide_up_fade_in)
            viewToAnimate.startAnimation(animation)
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
            "Other" -> "📌"
            else -> "💰"
        }
    }

    override fun getItemCount() = expenses.size

    fun setExpenses(expenses: List<Expense>) {
        this.expenses = expenses
        lastPosition = -1  // Reset animation position
        notifyDataSetChanged()
    }
}