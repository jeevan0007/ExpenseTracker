package com.jeevan.expensetracker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jeevan.expensetracker.adapter.ExpenseAdapter
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.viewmodel.ExpenseViewModel
import kotlin.math.abs
import kotlin.math.min
import com.jeevan.expensetracker.utils.applySquishPhysics
import com.jeevan.expensetracker.utils.dpToPx
import com.jeevan.expensetracker.utils.loadSavedCurrency
import com.jeevan.expensetracker.utils.vibrate
import com.jeevan.expensetracker.utils.vibrateLight
import com.jeevan.expensetracker.utils.vibrateHeavy

class RecycleBinActivity : AppCompatActivity() {

    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var adapter: ExpenseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Enable Edge-to-Edge Immersive Mode
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycle_bin)

        val headerLayout = findViewById<LinearLayout>(R.id.headerLayout)
        val rvDeletedExpenses = findViewById<RecyclerView>(R.id.rvDeletedExpenses)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnEmptyBin = findViewById<Button>(R.id.btnEmptyBin)
        val layoutEmptyBin = findViewById<LinearLayout>(R.id.layoutEmptyBin)

        // 2. Protect UI from Notches and Swipe Bars
        ViewCompat.setOnApplyWindowInsetsListener(headerLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top + dpToPx(16), view.paddingRight, view.paddingBottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(rvDeletedExpenses) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom + dpToPx(24))
            insets
        }

        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]

        val saved = loadSavedCurrency()

        adapter = ExpenseAdapter(
            onItemClick = { expense -> showRestoreDialog(expense) },
            onItemLongClick = { expense -> showHardDeleteDialog(expense) }
        )
        adapter.updateCurrency(saved.rate, saved.locale)

        rvDeletedExpenses.adapter = adapter
        rvDeletedExpenses.layoutManager = LinearLayoutManager(this)

        // --- PREMIUM DUAL-SWIPE ENGINE ---
        setupSwipeActions(rvDeletedExpenses)

        // Track whether the list has been shown at least once this session.
        // The cascade entrance animation should only fire on first load —
        // firing it on every observe emission (e.g. after a restore) causes
        // all remaining items to re-run their entrance animation simultaneously.
        var hasAnimatedInitialLoad = false

        expenseViewModel.getDeletedExpenses().observe(this) { expenses ->
            if (expenses.isNullOrEmpty()) {
                rvDeletedExpenses.visibility = View.GONE
                layoutEmptyBin.visibility = View.VISIBLE
                layoutEmptyBin.alpha = 0f
                layoutEmptyBin.animate().alpha(1f).setDuration(400).start()

                btnEmptyBin.isEnabled = false
                btnEmptyBin.alpha = 0.5f
                hasAnimatedInitialLoad = false // reset so it re-animates if items return
            } else {
                rvDeletedExpenses.visibility = View.VISIBLE
                layoutEmptyBin.visibility = View.GONE
                btnEmptyBin.isEnabled = true
                btnEmptyBin.alpha = 1.0f

                adapter.setExpenses(expenses)

                // Only run the cascade on the first emission — incremental
                // updates (restore/delete) use DiffUtil inside the adapter instead.
                if (!hasAnimatedInitialLoad) {
                    val controller = android.view.animation.AnimationUtils
                        .loadLayoutAnimation(rvDeletedExpenses.context, R.anim.layout_anim_cascade)
                    rvDeletedExpenses.layoutAnimation = controller
                    rvDeletedExpenses.scheduleLayoutAnimation()
                    hasAnimatedInitialLoad = true
                }
            }
        }

        // Apply Premium Physics
        btnBack.applySquishPhysics {
            finish()
            @Suppress("DEPRECATION") // overrideActivityTransition requires API 34; minSdk is 24
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        btnEmptyBin.applySquishPhysics {
            showEmptyBinDialog()
        }
    }

    // --- DUAL SWIPE LOGIC ---
    private fun setupSwipeActions(recyclerView: RecyclerView) {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val expense = adapter.getExpenseAt(position)

                if (direction == ItemTouchHelper.RIGHT) {
                    // Swiped Right -> Restore
                    vibrate()
                    showRestoreDialog(expense, position)
                } else if (direction == ItemTouchHelper.LEFT) {
                    // Swiped Left -> Hard Delete
                    vibrateHeavy()
                    showHardDeleteDialog(expense, position)
                }
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val itemView = viewHolder.itemView
                if (dX != 0f) {
                    val swipeProgress = min(abs(dX) / itemView.width.toFloat(), 1f)
                    val scale = 1f - (0.05f * swipeProgress)
                    itemView.scaleX = scale
                    itemView.scaleY = scale

                    val paint = Paint()
                    paint.isAntiAlias = true
                    paint.alpha = (swipeProgress * 255).toInt().coerceIn(0, 255)

                    // Determine color based on swipe direction
                    paint.color = if (dX > 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")

                    val scaleOffsetWidth = (itemView.width * (0.05f * swipeProgress)) / 2
                    val scaleOffsetHeight = (itemView.height * (0.05f * swipeProgress)) / 2

                    val bgRect = RectF(
                        itemView.left.toFloat() + scaleOffsetWidth,
                        itemView.top.toFloat() + scaleOffsetHeight,
                        itemView.right.toFloat() - scaleOffsetWidth,
                        itemView.bottom.toFloat() - scaleOffsetHeight
                    )
                    c.drawRoundRect(bgRect, 40f, 40f, paint)

                    // Draw Icons
                    val iconRes = if (dX > 0) android.R.drawable.ic_menu_revert else R.drawable.ic_delete
                    val icon = ContextCompat.getDrawable(this@RecycleBinActivity, iconRes)

                    icon?.let {
                        it.setTint(Color.WHITE)
                        val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                        val iconTop = itemView.top + iconMargin
                        val iconBottom = iconTop + it.intrinsicHeight

                        if (dX > 0) { // Swiping Right
                            val iconLeft = itemView.left + iconMargin
                            val iconRight = iconLeft + it.intrinsicWidth
                            it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        } else { // Swiping Left
                            val iconRight = itemView.right - iconMargin
                            val iconLeft = iconRight - it.intrinsicWidth
                            it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        }
                        it.alpha = (swipeProgress * 255).toInt().coerceIn(0, 255)
                        it.draw(c)
                    }
                } else {
                    itemView.scaleX = 1f
                    itemView.scaleY = 1f
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.scaleX = 1f
                viewHolder.itemView.scaleY = 1f
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }

    private fun showRestoreDialog(expense: Expense, position: Int? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        // Shapeshift into a RESTORE dialog
        dialogView.findViewById<TextView>(R.id.tvDialogIcon).text = "✨"
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "Restore Transaction?"

        val tvDeleteMessage = dialogView.findViewById<TextView>(R.id.tvDeleteMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelDelete)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmDelete)

        tvDeleteMessage.text = "Restore \"${expense.description}\" to dashboard?"
        btnConfirm.text = "Restore"
        btnConfirm.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))

        btnCancel.applySquishPhysics {
            dialog.dismiss()
            position?.let { adapter.notifyItemChanged(it) }
        }

        dialog.setOnCancelListener { position?.let { adapter.notifyItemChanged(it) } }

        btnConfirm.applySquishPhysics {
            vibrate()
            expenseViewModel.restore(expense)
            dialog.dismiss()
            Toast.makeText(this, "Restored successfully ✨", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun showHardDeleteDialog(expense: Expense, position: Int? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        // Shapeshift into a HARD DELETE dialog
        dialogView.findViewById<TextView>(R.id.tvDialogIcon).text = "🔥"
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "Permanently Delete?"

        val tvDeleteMessage = dialogView.findViewById<TextView>(R.id.tvDeleteMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelDelete)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmDelete)

        tvDeleteMessage.text = "Permanently delete \"${expense.description}\"?"
        btnConfirm.text = "Delete Forever"

        btnCancel.applySquishPhysics {
            dialog.dismiss()
            position?.let { adapter.notifyItemChanged(it) }
        }

        dialog.setOnCancelListener { position?.let { adapter.notifyItemChanged(it) } }

        btnConfirm.applySquishPhysics {
            vibrateHeavy()
            expenseViewModel.hardDelete(expense)
            dialog.dismiss()
            Toast.makeText(this, "Permanently deleted 🌪️", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun showEmptyBinDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        // Shapeshift into an EMPTY BIN dialog
        dialogView.findViewById<TextView>(R.id.tvDialogIcon).text = "☢️"
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "Empty Recycle Bin?"

        val tvDeleteMessage = dialogView.findViewById<TextView>(R.id.tvDeleteMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelDelete)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmDelete)

        tvDeleteMessage.text = "Destroy all items in the Recycle Bin forever?"
        btnConfirm.text = "Empty Bin"

        btnCancel.applySquishPhysics { dialog.dismiss() }
        btnConfirm.applySquishPhysics {
            vibrateHeavy()
            expenseViewModel.emptyRecycleBin()
            dialog.dismiss()
            Toast.makeText(this, "Bin Emptied 🗑️", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    // applySquishPhysics, getVibrator, vibratePhone*, dpToPx
    // → moved to ViewExtensions.kt / VibrationExtensions.kt

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        @Suppress("DEPRECATION") // overrideActivityTransition requires API 34; minSdk is 24
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}