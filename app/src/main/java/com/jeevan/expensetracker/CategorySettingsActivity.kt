package com.jeevan.expensetracker

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jeevan.expensetracker.adapter.CategorySettingsAdapter
import com.jeevan.expensetracker.utils.CategoryManager
import com.jeevan.expensetracker.utils.CustomCategory
import com.jeevan.expensetracker.utils.applySquishPhysics
import com.jeevan.expensetracker.utils.dpToPx

class CategorySettingsActivity : AppCompatActivity() {

    private lateinit var rvCategories: RecyclerView
    private lateinit var adapter: CategorySettingsAdapter
    private var categoryList: MutableList<CustomCategory> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_settings)

        val headerLayout   = findViewById<View>(R.id.headerLayout)
        val rvCategoriesView = findViewById<RecyclerView>(R.id.rvCategories)
        val fabAddCategory = findViewById<FloatingActionButton>(R.id.fabAddCategory)

        // Protect header from notch
        ViewCompat.setOnApplyWindowInsetsListener(headerLayout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top + dpToPx(16), view.paddingRight, view.paddingBottom)
            insets
        }

        // Protect list + FAB from bottom nav bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            rvCategoriesView.setPadding(
                rvCategoriesView.paddingLeft,
                rvCategoriesView.paddingTop,
                rvCategoriesView.paddingRight,
                bars.bottom + dpToPx(80)
            )
            val fabParams = fabAddCategory.layoutParams as ViewGroup.MarginLayoutParams
            fabParams.bottomMargin = bars.bottom + dpToPx(24)
            fabAddCategory.layoutParams = fabParams
            insets
        }

        // Back button — uses new OnBackPressedDispatcher (non-deprecated)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                @Suppress("DEPRECATION") // overrideActivityTransition requires API 34; minSdk is 24
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        })

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        rvCategories = rvCategoriesView
        rvCategories.layoutManager = LinearLayoutManager(this)

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

        fabAddCategory.applySquishPhysics { showAddCategoryDialog() }
    }

    private fun showAddCategoryDialog() {
        val bottomSheet = BottomSheetDialog(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(48))
            background = ContextCompat.getDrawable(this@CategorySettingsActivity, R.drawable.glass_card_background)
        }

        val title = android.widget.TextView(this).apply {
            text = "Create New Category"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@CategorySettingsActivity, android.R.color.white))
            setPadding(0, 0, 0, dpToPx(24))
        }

        fun borderDrawable() = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = this@CategorySettingsActivity.dpToPx(12).toFloat()
            setStroke(this@CategorySettingsActivity.dpToPx(1), Color.parseColor("#80888888"))
            setColor(Color.parseColor("#10888888"))
        }

        val etEmoji = EditText(this).apply {
            hint = "Pick an Emoji (e.g. 🍿)"
            textSize = 24f
            maxLines = 1
            background = borderDrawable()
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dpToPx(16)) }
        }

        val etName = EditText(this).apply {
            hint = "Category Name (e.g. Movies)"
            textSize = 18f
            maxLines = 1
            background = borderDrawable()
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dpToPx(32)) }
        }

        val btnSave = android.widget.Button(this).apply {
            text = "Save Category"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = this@CategorySettingsActivity.dpToPx(28).toFloat()
                setColor(Color.parseColor("#4CAF50"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                this@CategorySettingsActivity.dpToPx(56)
            ).also { it.setMargins(0, this@CategorySettingsActivity.dpToPx(8), 0, 0) }
        }

        layout.addView(title)
        layout.addView(etEmoji)
        layout.addView(etName)
        layout.addView(btnSave)
        bottomSheet.setContentView(layout)

        btnSave.applySquishPhysics {
            val emoji = etEmoji.text.toString().trim()
            val name  = etName.text.toString().trim()

            if (emoji.isEmpty() || name.isEmpty()) {
                Toast.makeText(this@CategorySettingsActivity, "Both fields are required!", Toast.LENGTH_SHORT).show()
                return@applySquishPhysics
            }
            if (categoryList.any { it.name.equals(name, ignoreCase = true) }) {
                Toast.makeText(this@CategorySettingsActivity, "Category already exists!", Toast.LENGTH_SHORT).show()
                return@applySquishPhysics
            }

            categoryList.add(CustomCategory(name, emoji))
            adapter.notifyItemInserted(categoryList.size - 1)
            rvCategories.scrollToPosition(categoryList.size - 1)
            CategoryManager.saveCategories(this@CategorySettingsActivity, categoryList)
            bottomSheet.dismiss()
        }

        layout.alpha = 0f
        layout.animate().alpha(1f).setDuration(300).start()
        bottomSheet.show()
    }
}