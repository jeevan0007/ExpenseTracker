package com.jeevan.expensetracker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jeevan.expensetracker.R

class ChartDetailAdapter(private val items: List<ChartItem>) :
    RecyclerView.Adapter<ChartDetailAdapter.ChartViewHolder>() {

    class ChartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // MATCHED: These IDs now perfectly match your new item_chart_detail.xml
        val viewColorIndicator: View = view.findViewById(R.id.viewColorIndicator)
        val tvEmoji: TextView = view.findViewById(R.id.tvEmoji)
        val tvCategoryName: TextView = view.findViewById(R.id.tvCategoryName)
        val tvPercentage: TextView = view.findViewById(R.id.tvPercentage)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chart_detail, parent, false)
        return ChartViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChartViewHolder, position: Int) {
        val item = items[position]

        holder.viewColorIndicator.setBackgroundColor(item.color)
        holder.tvEmoji.text = item.emoji
        holder.tvCategoryName.text = item.category
        holder.tvPercentage.text = String.format("%.1f%%", item.percentage)
        holder.tvAmount.text = item.formattedString
    }

    override fun getItemCount(): Int {
        return items.size
    }
}