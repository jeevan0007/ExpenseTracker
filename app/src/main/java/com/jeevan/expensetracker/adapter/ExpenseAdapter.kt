package com.jeevan.expensetracker.adapter

import android.graphics.Color
import android.content.res.ColorStateList
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jeevan.expensetracker.R
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.utils.CategoryManager
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import com.jeevan.expensetracker.utils.ExpenseType
import java.util.*

class ExpenseAdapter(
    private val onItemLongClick: (Expense) -> Unit,
    private val onItemClick: (Expense) -> Unit
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    private var expenses = emptyList<Expense>()
    private var lastPosition = -1

    private var exchangeRate = 1.0
    private var targetLocale = Locale("en", "IN")
    private var isStealthMode = false

    // FIX — category cache populated once per setExpenses call on the calling thread,
    // never touched inside onBindViewHolder (no SharedPrefs I/O during scroll).
    private var cachedCategories: Map<String, String> = emptyMap()

    // FIX — NumberFormat and SimpleDateFormat created once per adapter instance,
    // not on every bind. These are expensive to construct.
    private var currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(targetLocale)
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    inner class ExpenseViewHolder(
        itemView: View,
        onItemClick: (Int) -> Unit,
        onItemLongClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        val tvCategoryIcon: TextView = itemView.findViewById(R.id.tvCategoryIcon)
        val tvCategory: TextView     = itemView.findViewById(R.id.tvCategory)
        val tvDescription: TextView  = itemView.findViewById(R.id.tvDescription)
        val tvDate: TextView         = itemView.findViewById(R.id.tvDate)
        val tvAmount: TextView       = itemView.findViewById(R.id.tvAmount)
        val badgeReceipt: View       = itemView.findViewById(R.id.badgeReceipt)
        val badgeBillable: MaterialCardView = itemView.findViewById(R.id.badgeBillable)
        val tvClientName: TextView   = itemView.findViewById(R.id.tvClientName)

        // FIX — GestureDetector created once here in the ViewHolder constructor
        // (i.e., in onCreateViewHolder), NOT on every bind. One object per ViewHolder
        // instead of one per scroll event.
        val gestureDetector = GestureDetector(
            itemView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onItemClick(pos)
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onItemLongClick(pos)
                        itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    }
                }
            }
        )

        init {
            itemView.setOnTouchListener { v, event ->
                gestureDetector.onTouchEvent(event)
                when (event.action) {
                    MotionEvent.ACTION_DOWN ->
                        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(300)
                            .setInterpolator(OvershootInterpolator(2f)).start()
                }
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(
            view,
            onItemClick    = { pos -> if (pos < expenses.size) onItemClick(expenses[pos]) },
            onItemLongClick = { pos -> if (pos < expenses.size) onItemLongClick(expenses[pos]) }
        )
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = expenses[position]

        // Category icon — pure Map lookup, zero I/O
        holder.tvCategoryIcon.text = cachedCategories[expense.category] ?: "💰"
        holder.tvCategory.text     = expense.category

        holder.tvDescription.text = if (expense.isRecurring) "🔄 ${expense.description}"
        else expense.description

        // Billable badge
        if (expense.isBillable) {
            holder.badgeBillable.visibility = View.VISIBLE
            val tvLabel = holder.badgeBillable.findViewById<TextView>(R.id.tvBillableText)
            if (expense.isReimbursed) {
                tvLabel?.text = "✅ Reimbursed"
                holder.badgeBillable.setCardBackgroundColor(
                    ColorStateList.valueOf(Color.parseColor("#2E7D32"))
                )
                holder.tvClientName.visibility = if (!expense.clientName.isNullOrEmpty()) View.VISIBLE else View.GONE
                holder.tvClientName.text = "Settled: ${expense.clientName}"
            } else {
                tvLabel?.text = "💼 Billable"
                holder.badgeBillable.setCardBackgroundColor(
                    ColorStateList.valueOf(Color.parseColor("#1976D2"))
                )
                holder.tvClientName.visibility = if (!expense.clientName.isNullOrEmpty()) View.VISIBLE else View.GONE
                holder.tvClientName.text = "Client: ${expense.clientName}"
            }
        } else {
            holder.badgeBillable.visibility = View.GONE
            holder.tvClientName.visibility  = View.GONE
        }

        // Amount — reuse pre-built currencyFormat instead of instantiating on every bind
        val converted = expense.amount * exchangeRate
        val formatted = currencyFormat.format(converted)

        if (expense.type == ExpenseType.INCOME) {
            holder.tvAmount.text      = if (isStealthMode) "+ ***.**" else "+ $formatted"
            holder.tvAmount.setTextColor(Color.parseColor("#388E3C"))
        } else {
            holder.tvAmount.text      = if (isStealthMode) "- ***.**" else "- $formatted"
            holder.tvAmount.setTextColor(Color.parseColor("#D32F2F"))
        }

        // Date — reuse pre-built dateFormat
        try {
            holder.tvDate.text = dateFormat.format(Date(expense.date))
        } catch (e: Exception) {
            holder.tvDate.text = "Invalid Date"
        }

        holder.badgeReceipt.visibility =
            if (!expense.receiptPath.isNullOrEmpty()) View.VISIBLE else View.GONE

        setCascadeAnimation(holder.itemView, position)
    }

    private fun setCascadeAnimation(view: View, position: Int) {
        view.animate().cancel()
        if (position > lastPosition) {
            view.translationY = 60f
            view.alpha = 0f
            view.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
            lastPosition = position
        } else {
            view.translationY = 0f
            view.alpha = 1f
        }
    }

    override fun onViewDetachedFromWindow(holder: ExpenseViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.itemView.animate().cancel()
        holder.itemView.alpha        = 1f
        holder.itemView.translationY = 0f
    }

    // ── Primary update path (called from MainActivity with a Context) ──────────
    fun setExpensesWithContext(newExpenses: List<Expense>, context: android.content.Context) {
        cachedCategories = CategoryManager.getCategories(context).associate { it.name to it.emoji }
        dispatchUpdate(newExpenses)
    }

    // ── Secondary update path (no Context needed, reuses existing cache) ───────
    fun setExpenses(newExpenses: List<Expense>) {
        dispatchUpdate(newExpenses)
    }

    private fun dispatchUpdate(newExpenses: List<Expense>) {
        val diffResult = DiffUtil.calculateDiff(ExpenseDiffCallback(expenses, newExpenses))
        if (expenses.isEmpty()) lastPosition = -1
        expenses = newExpenses
        diffResult.dispatchUpdatesTo(this)
    }

    fun getExpenseAt(position: Int): Expense = expenses[position]
    override fun getItemCount() = expenses.size

    // FIX — update currency without full rebind: notifyItemRangeChanged only
    fun updateCurrency(rate: Double, locale: Locale) {
        if (this.exchangeRate == rate && this.targetLocale == locale) return
        this.exchangeRate  = rate
        this.targetLocale  = locale
        this.currencyFormat = NumberFormat.getCurrencyInstance(locale)
        notifyItemRangeChanged(0, expenses.size)
    }

    // FIX — guard against redundant stealth toggles triggering a full rebind
    fun setStealthMode(isStealth: Boolean) {
        if (this.isStealthMode == isStealth) return
        this.isStealthMode = isStealth
        notifyItemRangeChanged(0, expenses.size)
    }

    // DiffUtil — drives surgical RecyclerView updates
    class ExpenseDiffCallback(
        private val oldList: List<Expense>,
        private val newList: List<Expense>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int) =
            oldList[oldPos].id == newList[newPos].id
        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
            val o = oldList[oldPos]; val n = newList[newPos]
            return o.amount      == n.amount
                    && o.category    == n.category
                    && o.description == n.description
                    && o.type        == n.type
                    && o.date        == n.date
                    && o.isRecurring == n.isRecurring
                    && o.isBillable  == n.isBillable
                    && o.isReimbursed == n.isReimbursed
                    && o.clientName  == n.clientName
                    && o.receiptPath == n.receiptPath
        }
    }
}