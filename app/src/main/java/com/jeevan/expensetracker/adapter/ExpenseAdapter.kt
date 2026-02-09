package com.jeevan.expensetracker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    }

    override fun getItemCount() = expenses.size

    fun setExpenses(expenses: List<Expense>) {
        this.expenses = expenses
        notifyDataSetChanged()
    }
}