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

        // Trigger the scroll animation engine
        setCascadeAnimation(holder.itemView, position)

        // --- THE MAGIC GLOW LOGIC ---
        if (position == highlightedPosition) {
            // Create a super soft, 15% transparent version of the category's actual color
            val glowColor = Color.argb(40, Color.red(item.color), Color.green(item.color), Color.blue(item.color))
            holder.itemView.setBackgroundColor(glowColor)

            // Give it a tiny "pop" animation so it demands attention
            holder.itemView.scaleX = 0.95f
            holder.itemView.scaleY = 0.95f
            holder.itemView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(400)
                .setInterpolator(OvershootInterpolator(2f))
                .start()
        } else {
            // Reset to normal completely transparent background if not selected
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            holder.itemView.scaleX = 1.0f
            holder.itemView.scaleY = 1.0f
        }
    }

    // --- SCROLL ANIMATION ENGINE ---
    private fun setCascadeAnimation(viewToAnimate: View, position: Int) {
        if (position > lastPosition) {
            // Scrolling DOWN: Slide up and fade in
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
            // Scrolling UP or Refreshing: Snap instantly into place to prevent ghosting
            viewToAnimate.translationY = 0f
            viewToAnimate.alpha = 1f
        }
    }

    // --- CRITICAL RECYCLING FIX ---
    override fun onViewDetachedFromWindow(holder: ChartViewHolder) {
        super.onViewDetachedFromWindow(holder)
        // If the view goes off-screen mid-animation, kill the animation and reset it!
        holder.itemView.animate().cancel()
        holder.itemView.alpha = 1f
        holder.itemView.translationY = 0f
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
    }

    override fun getItemCount(): Int {
        return items.size
    }

    // Call this from ChartsActivity to trigger the glow
    fun setHighlight(position: Int) {
        val previousPosition = highlightedPosition
        highlightedPosition = position

        // Only refresh the rows that changed so the list stays lightning fast
        if (previousPosition != -1) {
            notifyItemChanged(previousPosition)
        }
        if (highlightedPosition != -1) {
            notifyItemChanged(highlightedPosition)
        }
    }
}