package com.jeevan.expensetracker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jeevan.expensetracker.R

class ChartDetailAdapter(private val items: List<ChartItem>) :
    RecyclerView.Adapter<ChartDetailAdapter.ChartViewHolder>() {

    class ChartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val tvPercentage: TextView = itemView.findViewById(R.id.tvPercentage)
        val colorIndicator: View = itemView.findViewById(R.id.viewColorIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chart_detail, parent, false)
        return ChartViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChartViewHolder, position: Int) {
        val item = items[position]

        // Combine Emoji and Category Name
        holder.tvCategory.text = "${item.emoji} ${item.category}"

        // Use the pre-formatted string (e.g., "$ 50.00" or "₹ 50.00")
        holder.tvAmount.text = item.formattedAmount

        holder.tvPercentage.text = "${String.format("%.1f", item.percentage)}%"
        holder.colorIndicator.setBackgroundColor(item.color)
    }

    override fun getItemCount() = items.size
}