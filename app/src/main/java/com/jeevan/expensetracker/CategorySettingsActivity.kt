package com.jeevan.expensetracker

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jeevan.expensetracker.adapter.CategorySettingsAdapter
import com.jeevan.expensetracker.utils.CategoryManager
import com.jeevan.expensetracker.utils.CustomCategory

class CategorySettingsActivity : AppCompatActivity() {

    private lateinit var rvCategories: RecyclerView
    private lateinit var adapter: CategorySettingsAdapter
    private var categoryList: MutableList<CustomCategory> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_settings)

        val headerLayout = findViewById<View>(R.id.headerLayout)
        val rvCategoriesView = findViewById<RecyclerView>(R.id.rvCategories)
        val fabAddCategory = findViewById<FloatingActionButton>(R.id.fabAddCategory)

        // 1. Protect the Header from the Camera Notch
        ViewCompat.setOnApplyWindowInsetsListener(headerLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top + dpToPx(16), view.paddingRight, view.paddingBottom)
            insets
        }

        // 2. Protect the List and the FAB from the Bottom Navigation Bar
        val rootLayout = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Pad the bottom of the list so you can scroll all the way down
            rvCategoriesView.setPadding(
                rvCategoriesView.paddingLeft,
                rvCategoriesView.paddingTop,
                rvCategoriesView.paddingRight,
                systemBars.bottom + dpToPx(80)
            )

            // Push the FAB up so it perfectly matches the Main Dashboard's height
            val fabParams = fabAddCategory.layoutParams as ViewGroup.MarginLayoutParams
            fabParams.bottomMargin = systemBars.bottom + dpToPx(24)
            fabAddCategory.layoutParams = fabParams

            insets
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        rvCategories = findViewById(R.id.rvCategories)
        rvCategories.layoutManager = LinearLayoutManager(this)

        // Load saved categories!
        categoryList = CategoryManager.getCategories(this).toMutableList()

        adapter = CategorySettingsAdapter(categoryList) { categoryToDelete, position ->
            if (categoryList.size <= 1) {
                Toast.makeText(this, "You must have at least one category!", Toast.LENGTH_SHORT).show()
                return@CategorySettingsAdapter
            }

            categoryList.removeAt(position)
            adapter.notifyItemRemoved(position)
            CategoryManager.saveCategories(this, categoryList)
            Toast.makeText(this, "${categoryToDelete.name} deleted", Toast.LENGTH_SHORT).show()
        }

        rvCategories.adapter = adapter

        applySquishPhysics(fabAddCategory) {
            showAddCategoryDialog()
        }
    }

    private fun showAddCategoryDialog() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(48))
            background = androidx.core.content.ContextCompat.getDrawable(this@CategorySettingsActivity, R.drawable.glass_card_background)
        }

        val title = android.widget.TextView(this).apply {
            text = "Create New Category"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(androidx.core.content.ContextCompat.getColor(this@CategorySettingsActivity, android.R.color.white))
            setPadding(0, 0, 0, dpToPx(24))
        }

        // 🔥 NEW: Function to generate a beautiful rounded border
        val createBorderDrawable = {
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(1), android.graphics.Color.parseColor("#80888888")) // Semi-transparent grey border
                setColor(android.graphics.Color.parseColor("#10888888")) // Very subtle inner tint
            }
        }

        val etEmoji = EditText(this).apply {
            hint = "Pick an Emoji (e.g. 🍿)"
            textSize = 24f
            maxLines = 1
            background = createBorderDrawable() // Apply the border
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16)) // Inner spacing

            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, dpToPx(16)) // Space below the field
            layoutParams = params
        }

        val etName = EditText(this).apply {
            hint = "Category Name (e.g. Movies)"
            textSize = 18f
            maxLines = 1
            background = createBorderDrawable() // Apply the border
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16)) // Inner spacing

            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, dpToPx(32)) // Space below the field
            layoutParams = params
        }

        val btnSave = android.widget.Button(this).apply {
            text = "Save Category"
            isAllCaps = false
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)

            // 🔥 The Premium Shape Engine for programmatic buttons
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(28).toFloat() // Gives it that perfect, fully-rounded pill shape
                setColor(android.graphics.Color.parseColor("#4CAF50"))
            }

            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(56)).apply {
                setMargins(0, dpToPx(8), 0, 0)
            }
        }

        layout.addView(title)
        layout.addView(etEmoji)
        layout.addView(etName)
        layout.addView(btnSave)

        bottomSheet.setContentView(layout)

        applySquishPhysics(btnSave) {
            val emoji = etEmoji.text.toString().trim()
            val name = etName.text.toString().trim()

            if (emoji.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "Both fields are required!", Toast.LENGTH_SHORT).show()
                return@applySquishPhysics
            }

            if (categoryList.any { it.name.equals(name, ignoreCase = true) }) {
                Toast.makeText(this, "Category already exists!", Toast.LENGTH_SHORT).show()
                return@applySquishPhysics
            }

            val newCategory = CustomCategory(name, emoji)
            categoryList.add(newCategory)
            adapter.notifyItemInserted(categoryList.size - 1)
            rvCategories.scrollToPosition(categoryList.size - 1)

            CategoryManager.saveCategories(this, categoryList)
            bottomSheet.dismiss()
        }

        layout.alpha = 0f
        layout.animate().alpha(1f).setDuration(300).start()

        bottomSheet.show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
    private fun applySquishPhysics(view: View, onClickAction: () -> Unit) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                }
                android.view.MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
                    onClickAction()
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
                }
            }
            true
        }
    }
}