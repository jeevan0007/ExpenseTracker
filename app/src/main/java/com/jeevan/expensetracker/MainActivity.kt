package com.jeevan.expensetracker

// --- IMPORTS ---
import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.airbnb.lottie.LottieAnimationView // <--- ADDED FOR WOLF ANIMATION
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.jeevan.expensetracker.adapter.ExpenseAdapter
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.viewmodel.ExpenseViewModel
import com.jeevan.expensetracker.worker.RecurringExpenseWorker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var adapter: ExpenseAdapter
    private lateinit var lottieAnimationView: LottieAnimationView // <--- ANIMATION STAGE
    private var monthlyBudget: Double = 0.0

    // Animation States
    private var currentIncome: Double = 0.0
    private var currentExpense: Double = 0.0
    private var oldBalanceAnimState: Double = 0.0
    private var oldIncomeAnimState: Double = 0.0
    private var oldExpenseAnimState: Double = 0.0

    // Smart Budget Checker
    private var shouldCheckBudget: Boolean = false
    private var expenseBeforeAdd: Double = 0.0

    // UI Header
    private lateinit var tvDateHeader: TextView

    // Session Tracker
    companion object {
        var isSessionUnlocked = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadSavedTheme()

        val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        monthlyBudget = sharedPref.getFloat("monthly_budget", 0f).toDouble()

        tvDateHeader = findViewById(R.id.tvDateHeader)
        lottieAnimationView = findViewById(R.id.lottieAnimationView) // <--- INITIALIZE LOTTIE

        // --- BIOMETRIC LOCK ---
        val lockedOverlay = findViewById<LinearLayout>(R.id.lockedOverlay)
        val btnUnlockScreen = findViewById<Button>(R.id.btnUnlockScreen)
        val isAppLockEnabled = sharedPref.getBoolean("app_lock_enabled", false)

        if (isAppLockEnabled && !isSessionUnlocked) {
            lockedOverlay.visibility = View.VISIBLE
            launchBiometricLock(lockedOverlay)
        } else {
            lockedOverlay.visibility = View.GONE
        }
        btnUnlockScreen.setOnClickListener { launchBiometricLock(lockedOverlay) }

        // --- WORK MANAGER ---
        val workRequest = PeriodicWorkRequestBuilder<RecurringExpenseWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("RecurringExpenseWork", ExistingPeriodicWorkPolicy.KEEP, workRequest)

        // --- VIEW MODEL ---
        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]

        val recyclerView = findViewById<RecyclerView>(R.id.rvExpenses)
        adapter = ExpenseAdapter(
            onItemLongClick = { expense -> showDeleteDialog(expense) },
            onItemClick = { expense -> showEditDialog(expense) }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // --- SWIPE TO DELETE LOGIC ---
        val swipeHandler = object : SwipeGesture(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val expenseToDelete = adapter.getExpenseAt(position)
                expenseViewModel.delete(expenseToDelete)
                Toast.makeText(this@MainActivity, "Deleted Transaction", Toast.LENGTH_SHORT).show()
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // --- SEARCH ---
        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                expenseViewModel.setSearchQuery(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // --- CATEGORY FILTER ---
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

        // --- OBSERVERS ---
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

            if (shouldCheckBudget && currentExpense > expenseBeforeAdd) {
                checkBudgetStatus()
                shouldCheckBudget = false
            }
        }

        // --- BUTTONS ---
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
        setupThemeButtonPhysics(btnToggleTheme)

        val btnToggleAppLock = findViewById<Button>(R.id.btnToggleAppLock)
        updateAppLockButtonText(btnToggleAppLock)
        applySquishPhysics(btnToggleAppLock) { toggleAppLock(btnToggleAppLock) }

        val headerCard = findViewById<MaterialCardView>(R.id.headerCard)
        headerCard.translationY = -50f
        headerCard.alpha = 0f
        headerCard.animate().translationY(0f).alpha(1f).setDuration(800).setInterpolator(OvershootInterpolator(1.2f)).start()

        checkAndRequestPermissions()
    }
    private fun shakeHeaderCard() {
        val headerCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.headerCard)

        // Create a shake animation using a translation offset
        val shakeAnimator = ValueAnimator.ofFloat(0f, 15f, -15f, 10f, -10f, 5f, -5f, 0f)
        shakeAnimator.duration = 500 // Duration of the shake
        shakeAnimator.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            headerCard.translationX = value
            headerCard.translationY = value / 2 // Slight vertical jitter for realism
        }
        shakeAnimator.start()
    }

    // --- SPIRIT ANIMAL THEME TOGGLE (THE WOLF) ---
    private fun setupThemeButtonPhysics(button: Button) {
        button.setOnClickListener {
            if (!button.isEnabled) return@setOnClickListener
            button.isEnabled = false
            vibratePhone()

            val isDark = AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES

            // 1. Play the Spirit Animal FIRST
            triggerSpiritAnimation(isDark) {
                // 2. This block runs at the PERFECT MOMENT (e.g., during the howl)
                val newMode = if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                getSharedPreferences("ExpenseTracker", MODE_PRIVATE).edit().putInt("theme_mode", newMode).apply()
                AppCompatDelegate.setDefaultNightMode(newMode)

                // Refresh button text
                button.text = if (isDark) "☀️ Light Mode" else "🌙 Dark Mode"
                button.isEnabled = true
            }
        }
    }

    private fun triggerSpiritAnimation(isDark: Boolean, onThemeSwitch: () -> Unit) {
        val animationFile = if (isDark) R.raw.wolf_howl else R.raw.sun_inspire
        lottieAnimationView.setAnimation(animationFile)

        lottieAnimationView.visibility = View.VISIBLE
        lottieAnimationView.bringToFront()

        // reset state
        lottieAnimationView.progress = 0f
        lottieAnimationView.alpha = 1f
        lottieAnimationView.scaleX = 1.1f
        lottieAnimationView.scaleY = 1.1f

        lottieAnimationView.playAnimation()

        // --- ENHANCED TIMING ---
        // The wolf reaches full howl around 70% of the way through (frame 80ish)
        lottieAnimationView.postDelayed({
            // 1. Trigger the impact
            onThemeSwitch()

            if (isDark) {
                vibratePhone()
                shakeHeaderCard()

                // 2. PAUSE the animation at the peak of the howl for 800ms
                // This makes the "Howl" feel powerful and intentional
                lottieAnimationView.pauseAnimation()

                lottieAnimationView.postDelayed({
                    // 3. Resume and fade out
                    lottieAnimationView.resumeAnimation()
                    lottieAnimationView.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction { lottieAnimationView.visibility = View.GONE }
                        .start()
                }, 800)
            } else {
                // Sun logic remains smooth
                lottieAnimationView.postDelayed({
                    lottieAnimationView.animate().alpha(0f).setDuration(400).start()
                }, 1000)
            }
        }, 1100) // Timing matched to the "Wolf_Howl.json" peak
    }

    private fun animateNumberRoll(textView: TextView, oldValue: Double, newValue: Double) {
        val animator = ValueAnimator.ofFloat(oldValue.toFloat(), newValue.toFloat())
        animator.duration = 1500
        animator.interpolator = DecelerateInterpolator(1.5f)

        val format = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN"))

        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Float
            textView.text = format.format(animatedValue)
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (Math.abs(oldValue - newValue) > 1) {
                    vibratePhoneLight()
                }
            }
        })
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
                    isSessionUnlocked = true
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
                    if (!newState) isSessionUnlocked = true
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

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0 || description.isEmpty()) {
                Toast.makeText(this, "Please enter valid details", Toast.LENGTH_SHORT).show()
                return@applySquishPhysics
            }

            expenseBeforeAdd = currentExpense

            expenseViewModel.insert(Expense(amount = amount, category = category, description = description, type = type, isRecurring = cbRecurring.isChecked))
            dialog.dismiss()

            if (type == "Expense") {
                shouldCheckBudget = true
            }
        }
        applySquishPhysics(btnCancel) { dialog.dismiss() }
        dialog.show()
    }

    private fun showDeleteDialog(expense: Expense) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val tvDeleteMessage = dialogView.findViewById<TextView>(R.id.tvDeleteMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelDelete)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmDelete)

        tvDeleteMessage.text = "Are you sure you want to delete\n\"${expense.description}\"?"

        applySquishPhysics(btnCancel) {
            dialog.dismiss()
        }

        applySquishPhysics(btnConfirm) {
            vibratePhone()
            expenseViewModel.delete(expense)
            dialog.dismiss()
            Toast.makeText(this, "Transaction Deleted", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
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

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0 || description.isEmpty()) {
                Toast.makeText(this, "Please enter valid details", Toast.LENGTH_SHORT).show()
                return@applySquishPhysics
            }

            expenseBeforeAdd = currentExpense

            expenseViewModel.update(expense.copy(amount = amount, category = spinnerCategory.selectedItem.toString(), description = description, type = type, isRecurring = cbRecurring.isChecked))
            dialog.dismiss()

            if (type == "Expense") {
                shouldCheckBudget = true
            }
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
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_budget_alert, null)
            val dialog = AlertDialog.Builder(this).setView(dialogView).create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

            val tvAlertIcon = dialogView.findViewById<TextView>(R.id.tvAlertIcon)
            val tvAlertTitle = dialogView.findViewById<TextView>(R.id.tvAlertTitle)
            val tvAlertMessage = dialogView.findViewById<TextView>(R.id.tvAlertMessage)
            val progressBar = dialogView.findViewById<LinearProgressIndicator>(R.id.progressBarBudget)
            val tvSpent = dialogView.findViewById<TextView>(R.id.tvSpent)
            val tvLimit = dialogView.findViewById<TextView>(R.id.tvLimit)
            val btnIgnore = dialogView.findViewById<Button>(R.id.btnIgnore)
            val btnFixBudget = dialogView.findViewById<Button>(R.id.btnFixBudget)

            val isCritical = percentage >= 100
            val color = if (isCritical) Color.parseColor("#D32F2F") else Color.parseColor("#FF9800")

            tvAlertIcon.text = if (isCritical) "🚨" else "⚠️"
            tvAlertTitle.text = if (isCritical) "Budget Exceeded!" else "Budget Warning"

            tvAlertMessage.text = "You have used ${String.format("%.1f", percentage)}% of your monthly limit."

            progressBar.progress = percentage.toInt().coerceAtMost(100)
            progressBar.setIndicatorColor(color)

            tvSpent.text = "Spent: ₹${String.format("%.2f", currentExpense)}"
            tvSpent.setTextColor(color)
            tvLimit.text = "Limit: ₹${String.format("%.2f", monthlyBudget)}"

            if (isCritical) {
                btnFixBudget.backgroundTintList = ColorStateList.valueOf(color)
            } else {
                btnFixBudget.backgroundTintList = ColorStateList.valueOf(color)
            }

            applySquishPhysics(btnIgnore) { dialog.dismiss() }
            applySquishPhysics(btnFixBudget) {
                dialog.dismiss()
                showSetBudgetDialog()
            }
            dialog.show()
        }
    }

    private fun showDateFilterDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_date_filter, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupDateFilter)

        applySquishPhysics(dialogView.findViewById<Button>(R.id.btnApplyDateFilter)) {
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

            when (radioGroup.checkedRadioButtonId) {
                R.id.radioAllTime -> {
                    expenseViewModel.clearDateFilter()
                    findViewById<Button>(R.id.btnDateFilter).text = "All Time"
                    tvDateHeader.text = "Showing: All Time"
                }
                R.id.radioToday -> {
                    val start = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                    val end = calendar.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
                    expenseViewModel.setDateRangeFilter(start, end)
                    findViewById<Button>(R.id.btnDateFilter).text = "Today"
                    tvDateHeader.text = "Showing: Today (${dateFormat.format(Date())})"
                }
                R.id.radioThisWeek -> {
                    calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    val start = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                    val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis

                    expenseViewModel.setDateRangeFilter(start, end)
                    findViewById<Button>(R.id.btnDateFilter).text = "This Week"
                    tvDateHeader.text = "Showing: This Week (Mon - Today)"
                }
                R.id.radioThisMonth -> {
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    val start = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                    val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
                    expenseViewModel.setDateRangeFilter(start, end)
                    findViewById<Button>(R.id.btnDateFilter).text = "This Month"
                    tvDateHeader.text = "Showing: This Month"
                }
                R.id.radioLastMonth -> {
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.add(Calendar.MONTH, -1)
                    val start = calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

                    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                    val end = calendar.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis

                    expenseViewModel.setDateRangeFilter(start, end)
                    findViewById<Button>(R.id.btnDateFilter).text = "Last Month"
                    tvDateHeader.text = "Showing: Last Month"
                }
                R.id.radioCustom -> {
                    dialog.dismiss()
                    showCustomDateRangePicker()
                    return@applySquishPhysics
                }
            }
            dialog.dismiss()
        }
        applySquishPhysics(dialogView.findViewById<Button>(R.id.btnCancelDateFilter)) { dialog.dismiss() }
        dialog.show()
    }

    private fun showCustomDateRangePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val startSelected = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }.timeInMillis

            DatePickerDialog(this, { _, y2, m2, d2 ->
                val endSelected = Calendar.getInstance().apply { set(y2, m2, d2, 23, 59, 59) }.timeInMillis

                val finalStart = min(startSelected, endSelected)
                val finalEnd = max(startSelected, endSelected)

                expenseViewModel.setDateRangeFilter(finalStart, finalEnd)
                findViewById<Button>(R.id.btnDateFilter).text = "Custom"

                val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                tvDateHeader.text = "Showing: ${sdf.format(Date(finalStart))} - ${sdf.format(Date(finalEnd))}"

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