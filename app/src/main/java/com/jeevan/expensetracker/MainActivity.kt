package com.jeevan.expensetracker

import android.Manifest
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import android.app.DatePickerDialog
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.ViewAnimationUtils
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.*
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.jeevan.expensetracker.adapter.ExpenseAdapter
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.viewmodel.ExpenseViewModel
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import com.jeevan.expensetracker.worker.RecurringExpenseWorker

class MainActivity : AppCompatActivity() {

    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var adapter: ExpenseAdapter
    private var monthlyBudget: Double = 0.0

    // Trackers for Kinetic Rolling
    private var currentIncome: Double = 0.0
    private var currentExpense: Double = 0.0
    private var oldBalanceAnimState: Double = 0.0
    private var oldIncomeAnimState: Double = 0.0
    private var oldExpenseAnimState: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadSavedTheme()

        val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        monthlyBudget = sharedPref.getFloat("monthly_budget", 0f).toDouble()

        val lockedOverlay = findViewById<LinearLayout>(R.id.lockedOverlay)
        val btnUnlockScreen = findViewById<Button>(R.id.btnUnlockScreen)

        val isAppLockEnabled = sharedPref.getBoolean("app_lock_enabled", false)
        if (isAppLockEnabled) {
            lockedOverlay.visibility = View.VISIBLE
            launchBiometricLock(lockedOverlay)
        }

        btnUnlockScreen.setOnClickListener { launchBiometricLock(lockedOverlay) }

        val workRequest = PeriodicWorkRequestBuilder<RecurringExpenseWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("RecurringExpenseWork", ExistingPeriodicWorkPolicy.KEEP, workRequest)

        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]

        val recyclerView = findViewById<RecyclerView>(R.id.rvExpenses)
        adapter = ExpenseAdapter(
            onItemLongClick = { expense -> showDeleteDialog(expense) },
            onItemClick = { expense -> showEditDialog(expense) }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                expenseViewModel.setSearchQuery(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val spinnerCategoryFilter = findViewById<Spinner>(R.id.spinnerCategoryFilter)
        val filterCategories = mutableListOf("All Categories").apply {
            addAll(resources.getStringArray(R.array.categories))
            add("Automated")
        }
        val filterAdapter = ArrayAdapter(this, R.layout.spinner_item, filterCategories)
        filterAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerCategoryFilter.adapter = filterAdapter

        spinnerCategoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCategory = if (position == 0) "All" else filterCategories[position]
                expenseViewModel.setCategoryFilter(selectedCategory)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        expenseViewModel.filteredExpenses.observe(this) { expenses ->
            expenses?.let { adapter.setExpenses(it) }
        }

        val tvBalanceAmount = findViewById<TextView>(R.id.tvBalanceAmount)
        val tvIncomeAmount = findViewById<TextView>(R.id.tvIncomeAmount)
        val tvExpenseAmount = findViewById<TextView>(R.id.tvExpenseAmount)

        expenseViewModel.totalIncome.observe(this) { income ->
            currentIncome = income ?: 0.0
            animateNumberRoll(tvIncomeAmount, oldIncomeAnimState, currentIncome)
            oldIncomeAnimState = currentIncome
            updateBalance(tvBalanceAmount)
        }

        expenseViewModel.totalExpenses.observe(this) { expense ->
            currentExpense = expense ?: 0.0
            animateNumberRoll(tvExpenseAmount, oldExpenseAnimState, currentExpense)
            oldExpenseAnimState = currentExpense
            updateBalance(tvBalanceAmount)
        }

        val fabAddExpense = findViewById<FloatingActionButton>(R.id.fabAddExpense)
        fabAddExpense.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    vibratePhoneLight()
                    v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(150).setInterpolator(DecelerateInterpolator()).start()
                }
                MotionEvent.ACTION_UP -> {
                    vibratePhone()
                    v.animate().scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(OvershootInterpolator(2.5f)).start()
                    showAddExpenseDialog()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(OvershootInterpolator(2.5f)).start()
                }
            }
            true
        }

        applySquishPhysics(findViewById<Button>(R.id.btnSetBudget)) { showSetBudgetDialog() }
        applySquishPhysics(findViewById<Button>(R.id.btnDateFilter)) { showDateFilterDialog() }
        applySquishPhysics(findViewById<Button>(R.id.btnViewCharts)) {
            startActivity(Intent(this, ChartsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        val btnToggleTheme = findViewById<Button>(R.id.btnToggleTheme)
        updateThemeButtonText(btnToggleTheme)
        setupThemeButtonPhysics(btnToggleTheme) // NEW: Circular Reveal Engine

        val btnToggleAppLock = findViewById<Button>(R.id.btnToggleAppLock)
        updateAppLockButtonText(btnToggleAppLock)
        applySquishPhysics(btnToggleAppLock) { toggleAppLock(btnToggleAppLock) }

        val headerCard = findViewById<MaterialCardView>(R.id.headerCard)
        headerCard.translationY = -50f
        headerCard.alpha = 0f
        headerCard.animate().translationY(0f).alpha(1f).setDuration(800).setInterpolator(OvershootInterpolator(1.2f)).start()

        checkAndRequestPermissions()
    }

    // --- ULTRA PREMIUM FEATURE 6: The Telegram-Style Circular Reveal ---
    private fun setupThemeButtonPhysics(button: Button) {
        button.setOnClickListener {
            if (!button.isEnabled) return@setOnClickListener
            button.isEnabled = false
            vibratePhone()

            val currentMode = AppCompatDelegate.getDefaultNightMode()
            val newMode = if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            val isDark = newMode == AppCompatDelegate.MODE_NIGHT_YES
            val nextText = if (isDark) "☀️ Light Mode" else "🌙 Dark Mode"

            // 1. Calculate exactly where your finger tapped the button
            val location = IntArray(2)
            button.getLocationInWindow(location)
            val cx = location[0] + button.width / 2
            val cy = location[1] + button.height / 2

            // 2. Generate a giant color layer over the app
            val rootView = window.decorView.rootView as ViewGroup
            val rippleView = View(this)
            rippleView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

            // Ripple color depends on the mode we are switching to
            val colorRes = if (isDark) Color.parseColor("#121212") else Color.parseColor("#FFFFFF")
            rippleView.setBackgroundColor(colorRes)

            // Add the layer to the very top of the screen
            rootView.addView(rippleView)

            // 3. Calculate the distance to the furthest corner of the screen
            val finalRadius = Math.hypot(rootView.width.toDouble(), rootView.height.toDouble()).toFloat()

            // 4. Ignite the Circular Reveal expanding from the button!
            val anim = ViewAnimationUtils.createCircularReveal(rippleView, cx, cy, 0f, finalRadius)
            anim.duration = 350 // Smooth ripple speed
            anim.interpolator = DecelerateInterpolator()

            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 5. Once the screen is swallowed by the ripple, instantly swap the real app colors underneath
                    getSharedPreferences("ExpenseTracker", MODE_PRIVATE).edit().putInt("theme_mode", newMode).apply()
                    AppCompatDelegate.setDefaultNightMode(newMode)

                    // 6. Disable the default Android activity restart flash, making it perfectly seamless!
                    overridePendingTransition(0, 0)
                }
            })

            // Still do the cool 3D coin flip on the button itself while the ripple expands
            button.animate()
                .scaleX(0.8f).scaleY(0.8f)
                .rotationX(90f)
                .setDuration(150)
                .withEndAction {
                    button.text = nextText
                    button.rotationX = -90f
                    button.animate()
                        .scaleX(1f).scaleY(1f)
                        .rotationX(0f)
                        .setDuration(150)
                        .setInterpolator(OvershootInterpolator(2f))
                        .start()
                }.start()

            // Boom! Start the ripple.
            anim.start()
        }
    }
    // -------------------------------------------------------------------

    private fun animateNumberRoll(textView: TextView, oldValue: Double, newValue: Double) {
        val animator = ValueAnimator.ofFloat(oldValue.toFloat(), newValue.toFloat())
        animator.duration = 1200
        animator.interpolator = OvershootInterpolator(1f)
        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Float
            textView.text = "₹${String.format("%.2f", animatedValue)}"
        }
        animator.start()
    }

    private fun updateBalance(tvBalance: TextView) {
        val newBalance = currentIncome - currentExpense
        animateNumberRoll(tvBalance, oldBalanceAnimState, newBalance)
        oldBalanceAnimState = newBalance
    }

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

    private fun vibratePhoneLight() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, 50))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(20)
            }
        }
    }

    private fun vibratePhone() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(50)
            }
        }
    }

    private fun launchBiometricLock(overlay: View) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "App locked.", Toast.LENGTH_SHORT).show()
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    overlay.animate().alpha(0f).setDuration(400).withEndAction { overlay.visibility = View.GONE }.start()
                }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Expense Tracker")
            .setSubtitle("Authenticate to view your data")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun toggleAppLock(button: Button) {
        val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        val isCurrentlyEnabled = sharedPref.getBoolean("app_lock_enabled", false)

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val newState = !isCurrentlyEnabled
                    sharedPref.edit().putBoolean("app_lock_enabled", newState).apply()
                    updateAppLockButtonText(button)
                    Toast.makeText(applicationContext, "App Lock ${if (newState) "enabled" else "disabled"}!", Toast.LENGTH_SHORT).show()
                }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (isCurrentlyEnabled) "Disable App Lock" else "Enable App Lock")
            .setSubtitle("Authenticate to confirm")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun updateAppLockButtonText(button: Button) {
        val isEnabled = getSharedPreferences("ExpenseTracker", MODE_PRIVATE).getBoolean("app_lock_enabled", false)
        button.text = if (isEnabled) "🔒 Lock: ON" else "🔓 Lock: OFF"
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS), 101)
        }
        if (!isNotificationServiceEnabled()) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Enable Notification Access to track UPI automatically", Toast.LENGTH_LONG).show()
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(pkgName)
    }

    private fun showAddExpenseDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val radioGroupType = dialogView.findViewById<RadioGroup>(R.id.radioGroupType)
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val cbRecurring = dialogView.findViewById<CheckBox>(R.id.cbRecurring)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        val categories = resources.getStringArray(R.array.categories).toMutableList()
        categories.add("Salary"); categories.add("Automated")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = spinnerAdapter

        applySquishPhysics(btnSave) {
            val amountText = etAmount.text.toString()
            val description = etDescription.text.toString()
            val category = spinnerCategory.selectedItem.toString()
            val type = if (radioGroupType.checkedRadioButtonId == R.id.radioIncome) "Income" else "Expense"

            if (amountText.isEmpty() || description.isEmpty() || amountText.toDoubleOrNull()?.let { it <= 0 } == true) {
                Toast.makeText(this, "Please enter valid details", Toast.LENGTH_SHORT).show()
                return@applySquishPhysics
            }

            expenseViewModel.insert(Expense(amount = amountText.toDouble(), category = category, description = description, type = type, isRecurring = cbRecurring.isChecked))
            dialog.dismiss()
            if (type == "Expense") checkBudgetStatus()
        }
        applySquishPhysics(btnCancel) { dialog.dismiss() }
        dialog.show()
    }

    private fun showDeleteDialog(expense: Expense) {
        AlertDialog.Builder(this)
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete this ${expense.type}?\n\n${expense.description} - ₹${String.format("%.2f", expense.amount)}")
            .setPositiveButton("Delete") { _, _ -> expenseViewModel.delete(expense) }
            .setNegativeButton("Cancel", null)
            .create().apply { window?.attributes?.windowAnimations = R.style.DialogAnimation; show() }
    }

    private fun showEditDialog(expense: Expense) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val radioGroupType = dialogView.findViewById<RadioGroup>(R.id.radioGroupType)
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val cbRecurring = dialogView.findViewById<CheckBox>(R.id.cbRecurring)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        val categories = resources.getStringArray(R.array.categories).toMutableList()
        categories.add("Salary"); categories.add("Automated")
        spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        etAmount.setText(expense.amount.toString())
        etDescription.setText(expense.description)
        cbRecurring.isChecked = expense.isRecurring
        radioGroupType.check(if (expense.type == "Income") R.id.radioIncome else R.id.radioExpense)
        categories.indexOf(expense.category).takeIf { it >= 0 }?.let { spinnerCategory.setSelection(it) }

        btnSave.text = "Update"
        applySquishPhysics(btnSave) {
            val amountText = etAmount.text.toString()
            val description = etDescription.text.toString()
            val type = if (radioGroupType.checkedRadioButtonId == R.id.radioIncome) "Income" else "Expense"

            if (amountText.isEmpty() || description.isEmpty() || amountText.toDoubleOrNull()?.let { it <= 0 } == true) return@applySquishPhysics

            expenseViewModel.update(expense.copy(amount = amountText.toDouble(), category = spinnerCategory.selectedItem.toString(), description = description, type = type, isRecurring = cbRecurring.isChecked))
            dialog.dismiss()
            if (type == "Expense") checkBudgetStatus()
        }
        applySquishPhysics(btnCancel) { dialog.dismiss() }
        dialog.show()
    }

    private fun showSetBudgetDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_budget, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val etBudget = dialogView.findViewById<TextInputEditText>(R.id.etBudget)
        if (monthlyBudget > 0) etBudget.setText(monthlyBudget.toString())

        applySquishPhysics(dialogView.findViewById<Button>(R.id.btnSaveBudget)) {
            val budget = etBudget.text.toString().toDoubleOrNull()
            if (budget == null || budget <= 0) return@applySquishPhysics
            monthlyBudget = budget
            getSharedPreferences("ExpenseTracker", MODE_PRIVATE).edit().putFloat("monthly_budget", budget.toFloat()).apply()
            dialog.dismiss()
            checkBudgetStatus()
        }
        applySquishPhysics(dialogView.findViewById<Button>(R.id.btnCancelBudget)) { dialog.dismiss() }
        dialog.show()
    }

    private fun checkBudgetStatus() {
        if (monthlyBudget <= 0) return
        val percentage = (currentExpense / monthlyBudget) * 100
        if (percentage >= 80) {
            AlertDialog.Builder(this)
                .setTitle(if (percentage >= 100) "⚠️ Budget Exceeded!" else "⚠️ Budget Warning")
                .setMessage("You have used ${String.format("%.0f", percentage)}% of your budget.\nSpent: ₹${String.format("%.2f", currentExpense)}")
                .setPositiveButton("OK", null)
                .create().apply { window?.attributes?.windowAnimations = R.style.DialogAnimation; show() }
        }
    }

    private fun showDateFilterDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_date_filter, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupDateFilter)

        applySquishPhysics(dialogView.findViewById<Button>(R.id.btnApplyDateFilter)) {
            val calendar = Calendar.getInstance()
            when (radioGroup.checkedRadioButtonId) {
                R.id.radioAllTime -> { expenseViewModel.clearDateFilter(); findViewById<Button>(R.id.btnDateFilter).text = "All Time" }
                R.id.radioToday -> {
                    val start = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
                    expenseViewModel.setDateRangeFilter(start, Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }.timeInMillis)
                    findViewById<Button>(R.id.btnDateFilter).text = "Today"
                }
                R.id.radioCustom -> { dialog.dismiss(); showCustomDateRangePicker(); return@applySquishPhysics }
            }
            dialog.dismiss()
        }
        applySquishPhysics(dialogView.findViewById<Button>(R.id.btnCancelDateFilter)) { dialog.dismiss() }
        dialog.show()
    }

    private fun showCustomDateRangePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val start = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }.timeInMillis
            DatePickerDialog(this, { _, y2, m2, d2 ->
                expenseViewModel.setDateRangeFilter(start, Calendar.getInstance().apply { set(y2, m2, d2, 23, 59, 59) }.timeInMillis)
                findViewById<Button>(R.id.btnDateFilter).text = "Custom"
            }, y, m, d).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateThemeButtonText(button: Button) {
        button.text = if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) "☀️ Light Mode" else "🌙 Dark Mode"
    }

    private fun loadSavedTheme() {
        AppCompatDelegate.setDefaultNightMode(getSharedPreferences("ExpenseTracker", MODE_PRIVATE).getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
    }
}