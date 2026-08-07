package com.jeevan.expensetracker.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jeevan.expensetracker.R
import java.text.NumberFormat
import java.util.Locale

data class CategoryData(
    val name: String,
    val amount: Double,
    val percentage: Float,
    val color: Int
)

class CategoryAdapter : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private var categories = emptyList<CategoryData>()

    // FIX: currency format stored as instance field and updated via updateCurrency(),
    // instead of hardcoding ₹ which breaks when the user switches to USD/EUR/etc.
    private var currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorIndicator: View     = itemView.findViewById(R.id.colorIndicator)
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
        holder.tvCategoryName.text       = category.name
        holder.tvCategoryAmount.text     = currencyFormat.format(category.amount)
        holder.tvCategoryPercentage.text = "(${String.format("%.1f", category.percentage)}%)"
    }

    override fun getItemCount() = categories.size

    // FIX: DiffUtil replaces notifyDataSetChanged() — only changed rows are redrawn.
    // Previously the entire list was invalidated on every LiveData emission.
    fun setCategories(newCategories: List<CategoryData>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = categories.size
            override fun getNewListSize() = newCategories.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                categories[oldPos].name == newCategories[newPos].name
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val o = categories[oldPos]; val n = newCategories[newPos]
                return o.amount     == n.amount
                        && o.percentage == n.percentage
                        && o.color      == n.color
            }
        })
        categories = newCategories
        diff.dispatchUpdatesTo(this)
    }

    // Call when the active currency changes so amounts display in the correct symbol.
    fun updateCurrency(locale: Locale) {
        if (currencyFormat.currency?.currencyCode == NumberFormat.getCurrencyInstance(locale).currency?.currencyCode) return
        currencyFormat = NumberFormat.getCurrencyInstance(locale)
        notifyItemRangeChanged(0, categories.size)
    }
}