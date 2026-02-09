package com.jeevan.expensetracker.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jeevan.expensetracker.R

data class CategoryData(
    val name: String,
    val amount: Double,
    val percentage: Float,
    val color: Int
)

class CategoryAdapter : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private var categories = emptyList<CategoryData>()

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorIndicator: View = itemView.findViewById(R.id.colorIndicator)
        val tvCategoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        val tvCategoryAmount: TextView = itemView.findViewById(R.id.tvCategoryAmount)
        val tvCategoryPercentage: TextView = itemView.findViewById(R.id.tvCategoryPercentage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.colorIndicator.setBackgroundColor(category.color)
        holder.tvCategoryName.text = category.name
        holder.tvCategoryAmount.text = "₹${String.format("%.2f", category.amount)}"
        holder.tvCategoryPercentage.text = "(${String.format("%.1f", category.percentage)}%)"
    }

    override fun getItemCount() = categories.size

    fun setCategories(categories: List<CategoryData>) {
        this.categories = categories
        notifyDataSetChanged()
    }
}