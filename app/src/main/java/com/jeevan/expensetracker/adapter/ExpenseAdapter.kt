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
import com.jeevan.expensetracker.utils.RecurrenceType
import java.util.*

class ExpenseAdapter(
    private val onItemLongClick: (Expense) -> Unit,
    private val onItemClick: (Expense) -> Unit
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    private var expenses = emptyList<Expense>()
    private var lastPosition = -1

    private var exchangeRate = 1.0
    private var targetLocale = Locale("en", "IN")

    // Stealth Mode Engine
    private var isStealthMode = false

    // FIX: category cache is now populated once in setExpenses() on the calling
    // thread, so onBindViewHolder never needs to touch SharedPreferences itself.
    private var cachedCategories: Map<String, String> = emptyMap()

    class ExpenseViewHolder(
        itemView: View,
        onItemClick: (Int) -> Unit,
        onItemLongClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        val tvCategoryIcon: TextView = itemView.findViewById(R.id.tvCategoryIcon)
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val badgeReceipt: View = itemView.findViewById(R.id.badgeReceipt)
        val badgeBillable: MaterialCardView = itemView.findViewById(R.id.badgeBillable)
        val tvClientName: TextView = itemView.findViewById(R.id.tvClientName)

        // FIX: GestureDetector created once per ViewHolder in onCreateViewHolder,
        // not on every bind. Creating it in onBindViewHolder allocated a new object
        // on every scroll event — measurable overhead on fast scrolling.
        val gestureDetector = GestureDetector(
            itemView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onItemClick(pos)
                    }
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
        // FIX: pass lambdas that resolve the expense at bind-time via adapterPosition,
        // so the GestureDetector callbacks always get the current item even after
        // the list is updated.
        return ExpenseViewHolder(
            view,
            onItemClick = { pos -> if (pos < expenses.size) onItemClick(expenses[pos]) },
            onItemLongClick = { pos -> if (pos < expenses.size) onItemLongClick(expenses[pos]) }
        )
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val currentExpense = expenses[position]

        // Category icon — reads from pre-loaded cache, no I/O here
        val emoji = cachedCategories[currentExpense.category] ?: "💰"
        holder.tvCategoryIcon.text = emoji
        holder.tvCategory.text = currentExpense.category

        // Description & recurring icon
        holder.tvDescription.text = if (currentExpense.isRecurring) {
            "🔄 ${currentExpense.description}"
        } else {
            currentExpense.description
        }

        // Billable & reimbursed badge
        if (currentExpense.isBillable) {
            holder.badgeBillable.visibility = View.VISIBLE
            val tvBillableLabel = holder.badgeBillable.findViewById<TextView>(R.id.tvBillableText)

            if (currentExpense.isReimbursed) {
                tvBillableLabel?.text = "✅ Reimbursed"
                holder.badgeBillable.setCardBackgroundColor(
                    ColorStateList.valueOf(Color.parseColor("#2E7D32"))
                )
                if (!currentExpense.clientName.isNullOrEmpty()) {
                    holder.tvClientName.visibility = View.VISIBLE
                    holder.tvClientName.text = "Settled: ${currentExpense.clientName}"
                } else {
                    holder.tvClientName.visibility = View.GONE
                }
            } else {
                tvBillableLabel?.text = "💼 Billable"
                holder.badgeBillable.setCardBackgroundColor(
                    ColorStateList.valueOf(Color.parseColor("#1976D2"))
                )
                if (!currentExpense.clientName.isNullOrEmpty()) {
                    holder.tvClientName.visibility = View.VISIBLE
                    holder.tvClientName.text = "Client: ${currentExpense.clientName}"
                } else {
                    holder.tvClientName.visibility = View.GONE
                }
            }
        } else {
            holder.badgeBillable.visibility = View.GONE
            holder.tvClientName.visibility = View.GONE
        }

        // Amount, currency & stealth mode
        val convertedAmount = currentExpense.amount * exchangeRate
        val currencyFormat = NumberFormat.getCurrencyInstance(targetLocale)
        val formattedAmount = currencyFormat.format(convertedAmount)

        if (currentExpense.type == ExpenseType.INCOME) {
            holder.tvAmount.text = if (isStealthMode) "+ ***.**" else "+ $formattedAmount"
            holder.tvAmount.setTextColor(Color.parseColor("#388E3C"))
        } else {
            holder.tvAmount.text = if (isStealthMode) "- ***.**" else "- $formattedAmount"
            holder.tvAmount.setTextColor(Color.parseColor("#D32F2F"))
        }

        // Date formatting
        try {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            holder.tvDate.text = dateFormat.format(Date(currentExpense.date))
        } catch (e: Exception) {
            holder.tvDate.text = "Invalid Date"
        }

        // Receipt badge
        holder.badgeReceipt.visibility =
            if (!currentExpense.receiptPath.isNullOrEmpty()) View.VISIBLE else View.GONE

        // Cascade entrance animation
        setCascadeAnimation(holder.itemView, position)
    }

    private fun setCascadeAnimation(viewToAnimate: View, position: Int) {
        viewToAnimate.animate().cancel()
        if (position > lastPosition) {
            // Only animate items that are genuinely new to the screen.
            // Use a smaller offset (60f vs 150f) so rapid inserts at position 0
            // don't push existing items out of their natural position.
            viewToAnimate.translationY = 60f
            viewToAnimate.alpha = 0f
            viewToAnimate.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
            lastPosition = position
        } else {
            // Item already placed — snap it to its final position immediately
            // so it never appears to collide with a newly inserted neighbour.
            viewToAnimate.translationY = 0f
            viewToAnimate.alpha = 1f
        }
    }

    override fun onViewDetachedFromWindow(holder: ExpenseViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.itemView.animate().cancel()
        holder.itemView.alpha = 1f
        holder.itemView.translationY = 0f
    }

    // FIX: use DiffUtil instead of notifyDataSetChanged() so only the items that
    // actually changed are redrawn. This prevents the cascade entrance animation
    // from re-firing on every single item when the list updates, and is noticeably
    // smoother on lists with 50+ entries.
    // setExpensesWithContext is the primary update method (called from MainActivity).
    // This variant reuses the existing cache for any call site without a Context.
    fun setExpenses(newExpenses: List<Expense>) {
        val diffCallback = ExpenseDiffCallback(expenses, newExpenses)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        if (expenses.isEmpty()) lastPosition = -1
        expenses = newExpenses
        diffResult.dispatchUpdatesTo(this)
    }

    fun setExpensesWithContext(newExpenses: List<Expense>, context: android.content.Context) {
        cachedCategories = CategoryManager.getCategories(context).associate { it.name to it.emoji }
        val diffCallback = ExpenseDiffCallback(expenses, newExpenses)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        // Only reset lastPosition on initial load (empty → populated).
        // Resetting on every update caused all existing items to re-run their
        // entrance animation on each new insert, making them briefly overlap.
        if (expenses.isEmpty()) lastPosition = -1
        expenses = newExpenses
        diffResult.dispatchUpdatesTo(this)
    }

    fun getExpenseAt(position: Int): Expense = expenses[position]

    override fun getItemCount() = expenses.size

    // FIX: DiffUtil for currency changes — only rebinds visible items instead of
    // triggering a full redraw with notifyDataSetChanged()
    fun updateCurrency(rate: Double, locale: Locale) {
        this.exchangeRate = rate
        this.targetLocale = locale
        notifyItemRangeChanged(0, expenses.size)
    }

    // FIX: DiffUtil for stealth mode — same reasoning
    fun setStealthMode(isStealth: Boolean) {
        if (this.isStealthMode != isStealth) {
            this.isStealthMode = isStealth
            notifyItemRangeChanged(0, expenses.size)
        }
    }

    // DiffUtil callback — tells RecyclerView exactly which items changed
    class ExpenseDiffCallback(
        private val oldList: List<Expense>,
        private val newList: List<Expense>
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        // Two items represent the same logical row if they share the same DB id
        override fun areItemsTheSame(oldPos: Int, newPos: Int) =
            oldList[oldPos].id == newList[newPos].id

        // The visual content is identical if every displayed field matches
        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
            val old = oldList[oldPos]
            val new = newList[newPos]
            return old.amount == new.amount
                    && old.category == new.category
                    && old.description == new.description
                    && old.type == new.type
                    && old.date == new.date
                    && old.isRecurring == new.isRecurring
                    && old.isBillable == new.isBillable
                    && old.isReimbursed == new.isReimbursed
                    && old.clientName == new.clientName
                    && old.receiptPath == new.receiptPath
        }
    }
}