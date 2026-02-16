package com.jeevan.expensetracker

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
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.jeevan.expensetracker.adapter.ExpenseAdapter
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.viewmodel.ExpenseViewModel
import com.jeevan.expensetracker.worker.RecurringExpenseWorker
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var adapter: ExpenseAdapter
    private lateinit var lottieAnimationView: LottieAnimationView
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

    // --- CURRENCY STATE ---
    private var activeCurrencyRate = 1.0
    private var activeCurrencyLocale = Locale("en", "IN")
    private var isTravelModeActive = false

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
        lottieAnimationView = findViewById(R.id.lottieAnimationView)

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
                val position = viewHolder.bindingAdapterPosition
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
        val tvBalanceAmount = findViewById<TextView>(R.id.tvBalanceAmount)
        val tvIncomeAmount = findViewById<TextView>(R.id.tvIncomeAmount)
        val tvExpenseAmount = findViewById<TextView>(R.id.tvExpenseAmount)

        expenseViewModel.filteredExpenses.observe(this) { expenses ->
            expenses?.let { adapter.setExpenses(it) }
        }

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

        // --- v2.0 SECRET TRIGGER: 404 CAT ---
        tvBalanceAmount.setOnLongClickListener {
            triggerSecretCatMode()
            true
        }

        // --- TRAVEL MODE FAB ---
        val fabTravelMode = findViewById<FloatingActionButton>(R.id.fabTravelMode)
        fabTravelMode.setOnClickListener {
            vibratePhone()
            showCurrencyDialog(fabTravelMode)
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
            val intent = Intent(this, ChartsActivity::class.java)
            intent.putExtra("CURRENCY_RATE", activeCurrencyRate)
            intent.putExtra("CURRENCY_LOCALE", activeCurrencyLocale.language + "_" + activeCurrencyLocale.country)
            startActivity(intent)
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

    // --- CURRENCY LOGIC ---
    private fun showCurrencyDialog(fab: FloatingActionButton) {
        val currencies = arrayOf("🇮🇳 INR", "🇺🇸 USD", "🇪🇺 EUR", "🇬🇧 GBP", "🇯🇵 JPY", "🇨🇳 CNY")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Travel Currency")
        builder.setItems(currencies) { _, which ->
            when (which) {
                0 -> setCurrency(1.0, Locale("en", "IN"), false)
                1 -> setCurrency(0.012, Locale.US, true)
                2 -> setCurrency(0.011, Locale.GERMANY, true)
                3 -> setCurrency(0.0095, Locale.UK, true)
                4 -> setCurrency(1.75, Locale.JAPAN, true)
                5 -> setCurrency(0.087, Locale.CHINA, true)
            }
            val color = if (isTravelModeActive) "#4CAF50" else "#FF9800"
            fab.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
        }
        builder.show()
    }

    private fun setCurrency(rate: Double, locale: Locale, isTravel: Boolean) {
        activeCurrencyRate = rate
        activeCurrencyLocale = locale
        isTravelModeActive = isTravel
        adapter.updateCurrency(rate, locale)
        updateHeaderCurrency()
    }

    private fun updateHeaderCurrency() {
        val tvBalance = findViewById<TextView>(R.id.tvBalanceAmount)
        val tvIncome = findViewById<TextView>(R.id.tvIncomeAmount)
        val tvExpense = findViewById<TextView>(R.id.tvExpenseAmount)
        val format = NumberFormat.getCurrencyInstance(activeCurrencyLocale)

        tvBalance.text = format.format((currentIncome - currentExpense) * activeCurrencyRate)
        tvIncome.text = format.format(currentIncome * activeCurrencyRate)
        tvExpense.text = format.format(currentExpense * activeCurrencyRate)
    }

    // --- SPIRIT ANIMAL THEME TOGGLE ---
    private fun setupThemeButtonPhysics(button: Button) {
        button.setOnClickListener {
            if (!button.isEnabled) return@setOnClickListener
            button.isEnabled = false
            vibratePhone()
            val isDark = AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES
            triggerSpiritAnimation(isDark) {
                val newMode = if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                getSharedPreferences("ExpenseTracker", MODE_PRIVATE).edit().putInt("theme_mode", newMode).apply()
                AppCompatDelegate.setDefaultNightMode(newMode)
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
        lottieAnimationView.playAnimation()
        lottieAnimationView.postDelayed({
            onThemeSwitch()
            if (isDark) {
                vibratePhone()
                shakeHeaderCard()
                lottieAnimationView.postDelayed({
                    lottieAnimationView.animate().alpha(0f).setDuration(500).withEndAction { lottieAnimationView.visibility = View.GONE }.start()
                }, 800)
            } else {
                lottieAnimationView.postDelayed({
                    lottieAnimationView.animate().alpha(0f).setDuration(400).start()
                }, 1000)
            }
        }, 1100)
    }

    private fun shakeHeaderCard() {
        val headerCard = findViewById<MaterialCardView>(R.id.headerCard)
        val shakeAnimator = ValueAnimator.ofFloat(0f, 15f, -15f, 10f, -10f, 5f, -5f, 0f)
        shakeAnimator.duration = 500
        shakeAnimator.addUpdateListener { headerCard.translationX = it.animatedValue as Float }
        shakeAnimator.start()
    }

    // --- v2.0 SECRET CAT MODE (SAFE & DISPOSABLE) ---
    private fun triggerSecretCatMode() {
        val rootLayout = window.decorView as ViewGroup
        val tempOverlay = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#1A2530"))
            alpha = 0f
            isClickable = true
            isFocusable = true
        }
        val tempCatView = LottieAnimationView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dpToPx(350), dpToPx(350)).apply { gravity = android.view.Gravity.CENTER }
            setAnimation(R.raw.error_cat)
        }
        rootLayout.addView(tempOverlay)
        rootLayout.addView(tempCatView)
        tempCatView.playAnimation()
        vibrateGlitchPattern()
        ValueAnimator.ofFloat(0f, 0.85f, 0.2f, 0.85f, 0.9f).apply {
            duration = 400
            addUpdateListener { tempOverlay.alpha = it.animatedValue as Float }
            start()
        }
        tempCatView.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                tempCatView.animate().alpha(0f).setDuration(400).start()
                tempOverlay.animate().alpha(0f).setDuration(600).withEndAction {
                    rootLayout.removeView(tempOverlay)
                    rootLayout.removeView(tempCatView)
                }.start()
                Toast.makeText(this@MainActivity, "G1 says Balance is purr-fect 😼", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun vibrateGlitchPattern() {
        val vibrator = getVibrator()
        val timings = longArrayOf(0, 40, 80, 40, 150, 60)
        val amplitudes = intArrayOf(0, 160, 0, 200, 0, 180)
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(400)
            }
        }
    }

    private fun animateNumberRoll(textView: TextView, oldValue: Double, newValue: Double) {
        val animator = ValueAnimator.ofFloat(oldValue.toFloat(), newValue.toFloat())
        animator.duration = 1500
        animator.interpolator = DecelerateInterpolator(1.5f)
        val format = NumberFormat.getCurrencyInstance(activeCurrencyLocale)
        animator.addUpdateListener {
            textView.text = format.format((it.animatedValue as Float) * activeCurrencyRate.toFloat())
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (abs(oldValue - newValue) > 1) vibratePhoneLight()
            }
        })
        animator.start()
    }

    private fun updateBalance(tvBalance: TextView) {
        val newBalance = currentIncome - currentExpense
        if (isTravelModeActive) updateHeaderCurrency()
        else {
            animateNumberRoll(tvBalance, oldBalanceAnimState, newBalance)
            oldBalanceAnimState = newBalance
        }
    }

    private fun applySquishPhysics(view: View, onClickAction: () -> Unit) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                MotionEvent.ACTION_UP -> {
                    vibratePhoneLight()
                    v.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(OvershootInterpolator(2f)).start()
                    onClickAction()
                }
                MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).start()
            }
            true
        }
    }

    private fun getVibrator(): Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private fun vibratePhoneLight() { if (getVibrator().hasVibrator()) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getVibrator().vibrate(VibrationEffect.createOneShot(20, 50)) else @Suppress("DEPRECATION") getVibrator().vibrate(20) } }
    private fun vibratePhone() { if (getVibrator().hasVibrator()) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getVibrator().vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") getVibrator().vibrate(50) } }

    private fun launchBiometricLock(overlay: View) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isSessionUnlocked = true
                overlay.animate().alpha(0f).setDuration(400).withEndAction { overlay.visibility = View.GONE }.start()
            }
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun toggleAppLock(button: Button) {
        val sp = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        val enabled = !sp.getBoolean("app_lock_enabled", false)
        sp.edit().putBoolean("app_lock_enabled", enabled).apply()
        updateAppLockButtonText(button)
        if (!enabled) isSessionUnlocked = true
    }

    private fun updateAppLockButtonText(button: Button) { button.text = if (getSharedPreferences("ExpenseTracker", MODE_PRIVATE).getBoolean("app_lock_enabled", false)) "🔒 Lock: ON" else "🔓 Lock: OFF" }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS), 101)
        if (Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) != true) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(this, "Enable Access for Auto-Tracking", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddExpenseDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val catSpinner = view.findViewById<Spinner>(R.id.spinnerCategory)
        val cats = resources.getStringArray(R.array.categories).toMutableList().apply { add("Salary"); add("Automated") }
        catSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cats).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val amt = view.findViewById<TextInputEditText>(R.id.etAmount).text.toString().toDoubleOrNull()
            if (amt != null && amt > 0) {
                expenseBeforeAdd = currentExpense
                expenseViewModel.insert(Expense(amount = amt, category = catSpinner.selectedItem.toString(), description = view.findViewById<TextInputEditText>(R.id.etDescription).text.toString(), type = if (view.findViewById<RadioGroup>(R.id.radioGroupType).checkedRadioButtonId == R.id.radioIncome) "Income" else "Expense", isRecurring = view.findViewById<CheckBox>(R.id.cbRecurring).isChecked))
                dialog.dismiss()
                if (view.findViewById<RadioGroup>(R.id.radioGroupType).checkedRadioButtonId == R.id.radioExpense) shouldCheckBudget = true
            }
        }
        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showEditDialog(expense: Expense) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val amtEt = view.findViewById<TextInputEditText>(R.id.etAmount).apply { setText(expense.amount.toString()) }
        val descEt = view.findViewById<TextInputEditText>(R.id.etDescription).apply { setText(expense.description) }
        val catSpinner = view.findViewById<Spinner>(R.id.spinnerCategory)
        val cats = resources.getStringArray(R.array.categories).toMutableList().apply { add("Salary"); add("Automated") }
        catSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cats).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        catSpinner.setSelection(cats.indexOf(expense.category))
        view.findViewById<RadioGroup>(R.id.radioGroupType).check(if (expense.type == "Income") R.id.radioIncome else R.id.radioExpense)
        view.findViewById<CheckBox>(R.id.cbRecurring).isChecked = expense.isRecurring
        view.findViewById<Button>(R.id.btnSave).apply { text = "Update" }.setOnClickListener {
            val amt = amtEt.text.toString().toDoubleOrNull()
            if (amt != null && amt > 0) {
                expenseViewModel.update(expense.copy(amount = amt, category = catSpinner.selectedItem.toString(), description = descEt.text.toString(), type = if (view.findViewById<RadioGroup>(R.id.radioGroupType).checkedRadioButtonId == R.id.radioIncome) "Income" else "Expense", isRecurring = view.findViewById<CheckBox>(R.id.cbRecurring).isChecked))
                dialog.dismiss()
            }
        }
        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showDeleteDialog(expense: Expense) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<TextView>(R.id.tvDeleteMessage).text = "Delete \"${expense.description}\"?"
        view.findViewById<Button>(R.id.btnConfirmDelete).setOnClickListener { vibratePhone(); expenseViewModel.delete(expense); dialog.dismiss() }
        view.findViewById<Button>(R.id.btnCancelDelete).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showSetBudgetDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_set_budget, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val budgetEt = view.findViewById<TextInputEditText>(R.id.etBudget).apply { if (monthlyBudget > 0) setText(monthlyBudget.toString()) }
        view.findViewById<Button>(R.id.btnSaveBudget).setOnClickListener {
            val b = budgetEt.text.toString().toDoubleOrNull()
            if (b != null && b > 0) {
                monthlyBudget = b
                getSharedPreferences("ExpenseTracker", MODE_PRIVATE).edit().putFloat("monthly_budget", b.toFloat()).apply()
                dialog.dismiss()
                checkBudgetStatus()
            }
        }
        view.findViewById<Button>(R.id.btnCancelBudget).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun checkBudgetStatus() {
        if (monthlyBudget <= 0) return
        val percentage = (currentExpense / monthlyBudget) * 100
        if (percentage >= 80) {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_budget_alert, null)
            val dialog = AlertDialog.Builder(this).setView(view).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val format = NumberFormat.getCurrencyInstance(activeCurrencyLocale)
            view.findViewById<TextView>(R.id.tvSpent).text = "Spent: ${format.format(currentExpense * activeCurrencyRate)}"
            view.findViewById<TextView>(R.id.tvLimit).text = "Limit: ${format.format(monthlyBudget * activeCurrencyRate)}"
            val pb = view.findViewById<LinearProgressIndicator>(R.id.progressBarBudget)
            pb.progress = percentage.toInt().coerceAtMost(100)
            pb.setIndicatorColor(if (percentage >= 100) Color.parseColor("#D32F2F") else Color.parseColor("#FF9800"))
            view.findViewById<Button>(R.id.btnIgnore).setOnClickListener { dialog.dismiss() }
            view.findViewById<Button>(R.id.btnFixBudget).setOnClickListener { dialog.dismiss(); showSetBudgetDialog() }
            dialog.show()
        }
    }

    private fun showDateFilterDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_date_filter, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val rg = view.findViewById<RadioGroup>(R.id.radioGroupDateFilter)
        view.findViewById<Button>(R.id.btnApplyDateFilter).setOnClickListener {
            val cal = Calendar.getInstance()
            when (rg.checkedRadioButtonId) {
                R.id.radioAllTime -> { expenseViewModel.clearDateFilter(); tvDateHeader.text = "Showing: All Time" }
                R.id.radioToday -> { cal.set(Calendar.HOUR_OF_DAY, 0); expenseViewModel.setDateRangeFilter(cal.timeInMillis, System.currentTimeMillis()); tvDateHeader.text = "Showing: Today" }
                R.id.radioThisWeek -> { cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); expenseViewModel.setDateRangeFilter(cal.timeInMillis, System.currentTimeMillis()); tvDateHeader.text = "Showing: This Week" }
                R.id.radioThisMonth -> { cal.set(Calendar.DAY_OF_MONTH, 1); expenseViewModel.setDateRangeFilter(cal.timeInMillis, System.currentTimeMillis()); tvDateHeader.text = "Showing: This Month" }
                R.id.radioLastMonth -> { cal.add(Calendar.MONTH, -1); cal.set(Calendar.DAY_OF_MONTH, 1); val s = cal.timeInMillis; cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); expenseViewModel.setDateRangeFilter(s, cal.timeInMillis); tvDateHeader.text = "Showing: Last Month" }
                R.id.radioCustom -> { dialog.dismiss(); showCustomDateRangePicker(); return@setOnClickListener }
            }
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnCancelDateFilter).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showCustomDateRangePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val s = Calendar.getInstance().apply { set(y, m, d, 0, 0) }.timeInMillis
            DatePickerDialog(this, { _, y2, m2, d2 ->
                val e = Calendar.getInstance().apply { set(y2, m2, d2, 23, 59) }.timeInMillis
                expenseViewModel.setDateRangeFilter(min(s, e), max(s, e)); tvDateHeader.text = "Custom Range"
            }, y, m, d).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateThemeButtonText(b: Button) { b.text = if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) "☀️ Light Mode" else "🌙 Dark Mode" }
    private fun loadSavedTheme() { AppCompatDelegate.setDefaultNightMode(getSharedPreferences("ExpenseTracker", MODE_PRIVATE).getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)) }
}