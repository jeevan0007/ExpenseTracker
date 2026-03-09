package com.jeevan.expensetracker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jeevan.expensetracker.R
import com.jeevan.expensetracker.utils.CustomCategory

class CategorySettingsAdapter(
    private val categories: MutableList<CustomCategory>,
    private val onDeleteClick: (CustomCategory, Int) -> Unit
) : RecyclerView.Adapter<CategorySettingsAdapter.CategoryViewHolder>() {

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvEmoji: TextView = itemView.findViewById(R.id.tvCategoryEmoji)
        val tvName: TextView = itemView.findViewById(R.id.tvCategoryName)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_edit, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.tvEmoji.text = category.emoji
        holder.tvName.text = category.name

        // Add a little squish physics to the delete button
        holder.btnDelete.setOnClickListener {
            it.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                onDeleteClick(category, holder.bindingAdapterPosition)
            }.start()
        }
    }

    override fun getItemCount(): Int = categories.size
}