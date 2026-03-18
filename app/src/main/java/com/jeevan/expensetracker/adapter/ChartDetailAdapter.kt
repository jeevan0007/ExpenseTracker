package com.jeevan.expensetracker.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jeevan.expensetracker.R

class ChartDetailAdapter(private val items: List<ChartItem>) :
    RecyclerView.Adapter<ChartDetailAdapter.ChartViewHolder>() {

    // Tracks which row is currently glowing
    private var highlightedPosition = -1

    // Tracks the scroll animation
    private var lastPosition = -1

    class ChartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val viewColorIndicator: View = view.findViewById(R.id.viewColorIndicator)
        val tvEmoji: TextView = view.findViewById(R.id.tvEmoji)
        val tvCategoryName: TextView = view.findViewById(R.id.tvCategoryName)
        val tvPercentage: TextView = view.findViewById(R.id.tvPercentage)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val tvRecovered: TextView = view.findViewById(R.id.tvRecovered) // 🔥 NEW: Recovery badge
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

        // 🔥 FIX: Renamed from formattedString to formattedAmount to match updated ChartItem
        holder.tvAmount.text = item.formattedAmount

        // 🔥 NEW: Show the "Recovered" badge if money was claimed back for this category
        if (item.formattedReimbursed.isNotEmpty() &&
            item.formattedReimbursed != "₹0.00" &&
            item.formattedReimbursed != "₹0" &&
            item.formattedReimbursed != "$0.00") {
            holder.tvRecovered.visibility = View.VISIBLE
            holder.tvRecovered.text = "✓ Recovered ${item.formattedReimbursed}"
        } else {
            holder.tvRecovered.visibility = View.GONE
        }

        // Trigger the scroll animation engine
        setCascadeAnimation(holder.itemView, position)

        // --- THE MAGIC GLOW LOGIC ---
        if (position == highlightedPosition) {
            val glowColor = Color.argb(40, Color.red(item.color), Color.green(item.color), Color.blue(item.color))
            holder.itemView.setBackgroundColor(glowColor)

            holder.itemView.scaleX = 0.95f
            holder.itemView.scaleY = 0.95f
            holder.itemView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(400)
                .setInterpolator(OvershootInterpolator(2f))
                .start()
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            holder.itemView.scaleX = 1.0f
            holder.itemView.scaleY = 1.0f
        }
    }

    private fun setCascadeAnimation(viewToAnimate: View, position: Int) {
        if (position > lastPosition) {
            viewToAnimate.translationY = 150f
            viewToAnimate.alpha = 0f

            viewToAnimate.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(350)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()

            lastPosition = position
        } else {
            viewToAnimate.translationY = 0f
            viewToAnimate.alpha = 1f
        }
    }

    override fun onViewDetachedFromWindow(holder: ChartViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.itemView.animate().cancel()
        holder.itemView.alpha = 1f
        holder.itemView.translationY = 0f
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
    }

    override fun getItemCount(): Int = items.size

    fun setHighlight(position: Int) {
        val previousPosition = highlightedPosition
        highlightedPosition = position

        if (previousPosition != -1) {
            notifyItemChanged(previousPosition)
        }
        if (highlightedPosition != -1) {
            notifyItemChanged(highlightedPosition)
        }
    }
}