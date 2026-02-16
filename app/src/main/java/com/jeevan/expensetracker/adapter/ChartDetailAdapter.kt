package com.jeevan.expensetracker.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jeevan.expensetracker.R
import java.text.NumberFormat
import java.util.Locale

data class ChartItem(val category: String, val amount: Double, val percentage: Float, val color: Int, val emoji: String)

class ChartDetailAdapter(private val items: List<ChartItem>) : RecyclerView.Adapter<ChartDetailAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCategory: TextView = view.findViewById(R.id.tvCategoryName)
        val tvAmount: TextView = view.findViewById(R.id.tvCategoryAmount)
        val viewColor: View = view.findViewById(R.id.viewColorIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chart_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // Indian Currency Formatter
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

        holder.tvCategory.text = "${item.emoji} ${item.category} (${String.format("%.1f", item.percentage)}%)"
        holder.tvAmount.text = format.format(item.amount) // e.g. ₹16,999

        val background = holder.viewColor.background as GradientDrawable
        background.setColor(item.color)
    }

    override fun getItemCount() = items.size
}