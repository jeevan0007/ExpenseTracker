package com.jeevan.expensetracker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jeevan.expensetracker.adapter.ExpenseAdapter
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.viewmodel.ExpenseViewModel
import java.util.Locale

class RecycleBinActivity : AppCompatActivity() {

    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var adapter: ExpenseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycle_bin)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnEmptyBin = findViewById<Button>(R.id.btnEmptyBin)
        val rvDeletedExpenses = findViewById<RecyclerView>(R.id.rvDeletedExpenses)
        val layoutEmptyBin = findViewById<LinearLayout>(R.id.layoutEmptyBin)

        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]

        val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        val rate = sharedPref.getFloat("currency_rate", 1.0f).toDouble()
        val locale = Locale(sharedPref.getString("currency_lang", "en") ?: "en", sharedPref.getString("currency_country", "IN") ?: "IN")

        adapter = ExpenseAdapter(
            onItemClick = { expense -> showRestoreDialog(expense) },
            onItemLongClick = { expense -> showHardDeleteDialog(expense) }
        )
        adapter.updateCurrency(rate, locale)

        rvDeletedExpenses.adapter = adapter
        rvDeletedExpenses.layoutManager = LinearLayoutManager(this)

        expenseViewModel.getDeletedExpenses().observe(this) { expenses ->
            if (expenses.isNullOrEmpty()) {
                rvDeletedExpenses.visibility = View.GONE
                layoutEmptyBin.visibility = View.VISIBLE
                layoutEmptyBin.alpha = 0f
                layoutEmptyBin.animate().alpha(1f).setDuration(400).start()

                btnEmptyBin.isEnabled = false
                btnEmptyBin.alpha = 0.5f
            } else {
                rvDeletedExpenses.visibility = View.VISIBLE
                layoutEmptyBin.visibility = View.GONE
                btnEmptyBin.isEnabled = true
                btnEmptyBin.alpha = 1.0f

                // --- CASCADING ANIMATION TRIGGER ---
                val context = rvDeletedExpenses.context
                val controller = android.view.animation.AnimationUtils.loadLayoutAnimation(context, R.anim.layout_anim_cascade)
                rvDeletedExpenses.layoutAnimation = controller

                adapter.setExpenses(expenses)
                rvDeletedExpenses.scheduleLayoutAnimation() // Forces the cascade to play!
            }
        }

        // Apply Premium Physics to Top Bar Buttons
        applySquishPhysics(btnBack) {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        applySquishPhysics(btnEmptyBin) {
            showEmptyBinDialog()
        }
    }

    // --- REUSING YOUR CUSTOM DIALOGS WITH PHYSICS ---

    private fun showRestoreDialog(expense: Expense) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val tvDeleteMessage = dialogView.findViewById<TextView>(R.id.tvDeleteMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelDelete)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmDelete)

        tvDeleteMessage.text = "Restore \"${expense.description}\" to dashboard?"
        btnConfirm.text = "Restore"
        btnConfirm.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50")) // Green for restore!

        applySquishPhysics(btnCancel) { dialog.dismiss() }
        applySquishPhysics(btnConfirm) {
            vibratePhone()
            expenseViewModel.restore(expense)
            dialog.dismiss()
            Toast.makeText(this, "Restored successfully ✨", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun showHardDeleteDialog(expense: Expense) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val tvDeleteMessage = dialogView.findViewById<TextView>(R.id.tvDeleteMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelDelete)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmDelete)

        tvDeleteMessage.text = "Permanently delete \"${expense.description}\"?"
        btnConfirm.text = "Delete Forever"

        applySquishPhysics(btnCancel) { dialog.dismiss() }
        applySquishPhysics(btnConfirm) {
            vibratePhoneHeavy() // Extra heavy feedback for permanent destruction
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

        val tvDeleteMessage = dialogView.findViewById<TextView>(R.id.tvDeleteMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelDelete)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmDelete)

        tvDeleteMessage.text = "Destroy all items in the Recycle Bin forever?"
        btnConfirm.text = "Empty Bin"

        applySquishPhysics(btnCancel) { dialog.dismiss() }
        applySquishPhysics(btnConfirm) {
            vibratePhoneHeavy()
            expenseViewModel.emptyRecycleBin()
            dialog.dismiss()
            Toast.makeText(this, "Bin Emptied 🗑️", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    // --- PHYSICS & HAPTICS ENGINE ---

    private fun applySquishPhysics(view: View, onClickAction: () -> Unit) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).setInterpolator(DecelerateInterpolator()).start()
                }
                MotionEvent.ACTION_UP -> {
                    vibratePhoneLight()
                    v.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(OvershootInterpolator(2f)).start()
                    onClickAction()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(OvershootInterpolator(2f)).start()
                }
            }
            true
        }
    }

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun vibratePhoneLight() {
        val vibrator = getVibrator()
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, 50))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(20)
            }
        }
    }

    private fun vibratePhone() {
        val vibrator = getVibrator()
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(50)
            }
        }
    }

    private fun vibratePhoneHeavy() {
        val vibrator = getVibrator()
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, 255))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(100)
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}