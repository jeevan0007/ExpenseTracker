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
import android.hardware.Sensor
import android.hardware.SensorManager
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
import com.jeevan.expensetracker.utils.ShakeDetector
import com.jeevan.expensetracker.viewmodel.ExpenseViewModel
import com.jeevan.expensetracker.worker.BudgetWorker
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

    // --- CURRENCY STATE (PERSISTENT) ---
    private var activeCurrencyRate = 1.0
    private var activeCurrencyLocale = Locale("en", "IN")
    private var isTravelModeActive = false

    // --- SENSOR (SHAKE) ---
    private lateinit var sensorManager: SensorManager
    private var shakeDetector: ShakeDetector? = null

    // Session Tracker
    companion object {
        var isSessionUnlocked = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. LOAD SAVED THEME & CURRENCY IMMEDIATELY
        val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        loadSavedTheme(sharedPref)
        loadSavedCurrency(sharedPref)

        monthlyBudget = sharedPref.getFloat("monthly_budget", 0f).toDouble()

        tvDateHeader = findViewById(R.id.tvDateHeader)
        lottieAnimationView = findViewById(R.id.lottieAnimationView)

        // --- SHAKE DETECTOR SETUP ---
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shakeDetector = ShakeDetector {
            // WHAT HAPPENS WHEN YOU SHAKE:
            resetToDefaults()
        }

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

        // --- WORK MANAGER (Recurring & Budget) ---
        val workRequest = PeriodicWorkRequestBuilder<RecurringExpenseWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("RecurringExpenseWork", ExistingPeriodicWorkPolicy.KEEP, workRequest)

        val budgetRequest = androidx.work.PeriodicWorkRequestBuilder<BudgetWorker>(12, TimeUnit.HOURS).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("BudgetWatchdog", androidx.work.ExistingPeriodicWorkPolicy.KEEP, budgetRequest)

        // --- VIEW MODEL ---
        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]

        val recyclerView = findViewById<RecyclerView>(R.id.rvExpenses)
        adapter = ExpenseAdapter(
            onItemLongClick = { expense -> showDeleteDialog(expense) },
            onItemClick = { expense -> showEditDialog(expense) }
        )
        // APPLY SAVED CURRENCY TO ADAPTER NOW
        adapter.updateCurrency(activeCurrencyRate, activeCurrencyLocale)

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

        // --- v2.0 TRAVEL MODE: FAB LISTENER ---
        val fabTravelMode = findViewById<FloatingActionButton>(R.id.fabTravelMode)
        val initialColor = if (isTravelModeActive) "#4CAF50" else "#FF9800"
        fabTravelMode.backgroundTintList = ColorStateList.valueOf(Color.parseColor(initialColor))

        fabTravelMode.setOnClickListener {
            vibratePhone()
            showCurrencyDialog(fabTravelMode)
        }

        // --- NEW: EXPORT TO CSV BUTTON ---
        val fabExport = findViewById<FloatingActionButton>(R.id.fabExport)
        fabExport.setOnClickListener {
            vibratePhone()
            exportDataToCSV()
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

    // --- LIFECYCLE FOR SENSORS ---
    override fun onResume() {
        super.onResume()
        shakeDetector?.let {
            sensorManager.registerListener(it, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        shakeDetector?.let {
            sensorManager.unregisterListener(it)
        }
    }

    // --- SHAKE TO RESET LOGIC ---
    private fun resetToDefaults() {
        // --- UPDATED: Strong Double-Buzz for Shake ---
        vibrateReset()
        Toast.makeText(this, "🔄 Resetting to Home Mode...", Toast.LENGTH_SHORT).show()

        // 1. Reset Currency to INR
        setCurrency(1.0, Locale("en", "IN"), false)

        // 2. Clear Search & Filters
        expenseViewModel.setSearchQuery("")
        expenseViewModel.clearDateFilter()
        findViewById<EditText>(R.id.etSearch).setText("")

        // 3. Update Header
        tvDateHeader.text = "Showing: All Time"

        // NOTE: We completely removed the "Reset Theme to System" logic here!
        // Your dark/light mode preference will now survive the shake.
    }

    // --- SAVE & LOAD CURRENCY PREFS ---
    private fun loadSavedCurrency(sharedPref: android.content.SharedPreferences) {
        activeCurrencyRate = sharedPref.getFloat("currency_rate", 1.0f).toDouble()
        val lang = sharedPref.getString("currency_lang", "en") ?: "en"
        val country = sharedPref.getString("currency_country", "IN") ?: "IN"
        activeCurrencyLocale = Locale(lang, country)
        isTravelModeActive = activeCurrencyRate != 1.0
    }

    private fun saveCurrencyPrefs(rate: Double, locale: Locale) {
        val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        sharedPref.edit().apply {
            putFloat("currency_rate", rate.toFloat())
            putString("currency_lang", locale.language)
            putString("currency_country", locale.country)
            apply()
        }
    }

    // --- CURRENCY DIALOG ---
    private fun showCurrencyDialog(fab: FloatingActionButton) {
        val currencies = arrayOf(
            "🇮🇳 INR (Base)",
            "🇺🇸 USD ($)",
            "🇪🇺 EUR (€)",
            "🇬🇧 GBP (£)",
            "🇯🇵 JPY (¥)",
            "🇨🇳 CNY (¥)"
        )

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Travel Currency")
        builder.setItems(currencies) { _, which ->
            when (which) {
                0 -> setCurrency(1.0, Locale("en", "IN"), false)
                1 -> setCurrency(0.011, Locale.US, true)
                2 -> setCurrency(0.0093, Locale.GERMANY, true)
                3 -> setCurrency(0.0081, Locale.UK, true)
                4 -> setCurrency(1.69, Locale.JAPAN, true)
                5 -> setCurrency(0.076, Locale.CHINA, true)
            }

            if (isTravelModeActive) {
                fab.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            } else {
                fab.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF9800"))
            }
        }
        val dialog = builder.create()
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.show()
    }

    private fun setCurrency(rate: Double, locale: Locale, isTravel: Boolean) {
        activeCurrencyRate = rate
        activeCurrencyLocale = locale
        isTravelModeActive = isTravel

        saveCurrencyPrefs(rate, locale)

        adapter.updateCurrency(rate, locale)
        updateHeaderCurrency()

        // Only show toast if triggered manually (not by reset)
        if (isTravel) {
            val msg = "Currency: ${locale.displayCountry}"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateHeaderCurrency() {
        val tvBalance = findViewById<TextView>(R.id.tvBalanceAmount)
        val tvIncome = findViewById<TextView>(R.id.tvIncomeAmount)
        val tvExpense = findViewById<TextView>(R.id.tvExpenseAmount)

        val format = NumberFormat.getCurrencyInstance(activeCurrencyLocale)

        val dispBalance = (currentIncome - currentExpense) * activeCurrencyRate
        val dispIncome = currentIncome * activeCurrencyRate
        val dispExpense = currentExpense * activeCurrencyRate

        tvBalance.text = format.format(dispBalance)
        tvIncome.text = format.format(dispIncome)
        tvExpense.text = format.format(dispExpense)
    }

    // --- v2.0 SECRET CAT MODE (SAFE & DISPOSABLE) ---
    private fun triggerSecretCatMode() {
        val rootLayout = window.decorView as ViewGroup

        val tempOverlay = View(this)
        tempOverlay.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        tempOverlay.setBackgroundColor(Color.parseColor("#1A2530"))
        tempOverlay.alpha = 0f
        tempOverlay.isClickable = true
        tempOverlay.isFocusable = true

        val tempCatView = LottieAnimationView(this)
        val catParams = FrameLayout.LayoutParams(dpToPx(350), dpToPx(350))
        catParams.gravity = android.view.Gravity.CENTER
        tempCatView.layoutParams = catParams
        tempCatView.setAnimation(R.raw.error_cat)

        rootLayout.addView(tempOverlay)
        rootLayout.addView(tempCatView)

        tempCatView.playAnimation()
        vibrateGlitchPattern()

        val flicker = ValueAnimator.ofFloat(0f, 0.85f, 0.2f, 0.85f, 0.9f)
        flicker.duration = 400
        flicker.addUpdateListener { animation ->
            tempOverlay.alpha = animation.animatedValue as Float
        }
        flicker.start()

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

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

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
        lottieAnimationView.progress = 0f
        lottieAnimationView.alpha = 1f
        lottieAnimationView.scaleX = 1.1f
        lottieAnimationView.scaleY = 1.1f

        lottieAnimationView.removeAllAnimatorListeners()

        lottieAnimationView.playAnimation()

        lottieAnimationView.postDelayed({
            onThemeSwitch()

            if (isDark) {
                vibratePhone()
                shakeHeaderCard()
                lottieAnimationView.pauseAnimation()

                lottieAnimationView.postDelayed({
                    lottieAnimationView.resumeAnimation()
                    lottieAnimationView.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction { lottieAnimationView.visibility = View.GONE }
                        .start()
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
        shakeAnimator.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            headerCard.translationX = value
            headerCard.translationY = value / 2
        }
        shakeAnimator.start()
    }

    private fun animateNumberRoll(textView: TextView, oldValue: Double, newValue: Double) {
        val animator = ValueAnimator.ofFloat(oldValue.toFloat(), newValue.toFloat())
        animator.duration = 1500
        animator.interpolator = DecelerateInterpolator(1.5f)

        val format = NumberFormat.getCurrencyInstance(activeCurrencyLocale)

        animator.addUpdateListener { animation ->
            val displayValue = (animation.animatedValue as Float) * activeCurrencyRate.toFloat()
            textView.text = format.format(displayValue)
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (abs(oldValue - newValue) > 1) {
                    vibratePhoneLight()
                }
            }
        })
        animator.start()
    }

    private fun updateBalance(tvBalance: TextView) {
        val newBalance = currentIncome - currentExpense
        if (isTravelModeActive) {
            updateHeaderCurrency()
        } else {
            animateNumberRoll(tvBalance, oldBalanceAnimState, newBalance)
            oldBalanceAnimState = newBalance
        }
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

    // --- NEW: HEAVY VIBRATION FOR SHAKE ---
    private fun vibrateReset() {
        val v = getVibrator()
        if (v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Double Heavy Click: Buzz (70ms) - Pause (50ms) - Buzz (70ms)
                val timing = longArrayOf(0, 70, 50, 70)
                v.vibrate(VibrationEffect.createWaveform(timing, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(300)
            }
        }
    }

    // --- DIALOGS & HELPERS ---
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

        // Dynamic Hint
        val symbol = java.util.Currency.getInstance(activeCurrencyLocale).symbol
        etAmount.hint = "Amount ($symbol)"

        applySquishPhysics(btnSave) {
            val amountText = etAmount.text.toString()
            val description = etDescription.text.toString()
            val category = spinnerCategory.selectedItem.toString()
            val type = if (radioGroupType.checkedRadioButtonId == R.id.radioIncome) "Income" else "Expense"

            val rawInput = amountText.toDoubleOrNull()
            if (rawInput == null || rawInput <= 0 || description.isEmpty()) {
                Toast.makeText(this, "Please enter valid details", Toast.LENGTH_SHORT).show()
                return@applySquishPhysics
            }

            expenseBeforeAdd = currentExpense

            // REVERSE MATH LOGIC (Fixing the $1 = ₹1 bug)
            val amountInInr = if (isTravelModeActive) {
                rawInput / activeCurrencyRate
            } else {
                rawInput
            }

            expenseViewModel.insert(Expense(amount = amountInInr, category = category, description = description, type = type, isRecurring = cbRecurring.isChecked))
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

        tvDeleteMessage.text = "Delete \"${expense.description}\"?"

        applySquishPhysics(btnCancel) { dialog.dismiss() }
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

            val format = NumberFormat.getCurrencyInstance(activeCurrencyLocale)
            tvSpent.text = "Spent: ${format.format(currentExpense * activeCurrencyRate)}"
            tvSpent.setTextColor(color)
            tvLimit.text = "Limit: ${format.format(monthlyBudget * activeCurrencyRate)}"

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

    private fun loadSavedTheme(sharedPref: android.content.SharedPreferences) {
        AppCompatDelegate.setDefaultNightMode(sharedPref.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
    }

    // --- PRO-LEVEL EXPORT TO CSV LOGIC ---
    private fun exportDataToCSV() {
        val expenses = expenseViewModel.filteredExpenses.value

        if (expenses.isNullOrEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileName = "ExpenseReport_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.csv"
            val file = java.io.File(cacheDir, fileName)
            val fileWriter = java.io.FileWriter(file)

            // 1. Sort expenses chronologically (Newest to Oldest)
            val sortedExpenses = expenses.sortedByDescending { it.date }

            // 2. Group the data by "Month Year" (e.g., "February 2026")
            val groupedExpenses = sortedExpenses.groupBy {
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(it.date))
            }

            var grandTotalIncome = 0.0
            var grandTotalExpense = 0.0

            // Main Header
            fileWriter.append("PROFESSIONAL EXPENSE LEDGER\n")
            fileWriter.append("Generated on: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}\n\n")

            // 3. Iterate through each month
            val rowDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

            for ((monthYear, monthExpenses) in groupedExpenses) {
                // Month Header
                fileWriter.append("=== $monthYear ===\n")
                fileWriter.append("Date,Description,Category,Type,Amount(INR)\n")

                var monthlyIncome = 0.0
                var monthlyExpense = 0.0

                // Write rows for this month
                for (expense in monthExpenses) {
                    val dateStr = rowDateFormat.format(Date(expense.date))
                    val formattedAmount = String.format(Locale.US, "%.2f", expense.amount)

                    if (expense.type == "Income") {
                        monthlyIncome += expense.amount
                        grandTotalIncome += expense.amount
                    } else {
                        monthlyExpense += expense.amount
                        grandTotalExpense += expense.amount
                    }

                    fileWriter.append("\"$dateStr\",\"${expense.description}\",\"${expense.category}\",\"${expense.type}\",$formattedAmount\n")
                }

                // 4. Monthly Sub-Totals (Aligned under Type and Amount columns)
                val netSavings = monthlyIncome - monthlyExpense
                fileWriter.append(",,,,\n") // Empty spacing row
                fileWriter.append(",,,\"Total Income\",${String.format(Locale.US, "%.2f", monthlyIncome)}\n")
                fileWriter.append(",,,\"Total Expense\",${String.format(Locale.US, "%.2f", monthlyExpense)}\n")
                fileWriter.append(",,,\"Net Savings\",${String.format(Locale.US, "%.2f", netSavings)}\n")
                fileWriter.append("\n\n") // Blank lines before the next month
            }

            // 5. Grand Totals at the very bottom
            fileWriter.append("=== GRAND TOTALS ===\n")
            fileWriter.append(",,,\"Overall Income\",${String.format(Locale.US, "%.2f", grandTotalIncome)}\n")
            fileWriter.append(",,,\"Overall Expense\",${String.format(Locale.US, "%.2f", grandTotalExpense)}\n")
            fileWriter.append(",,,\"Overall Balance\",${String.format(Locale.US, "%.2f", grandTotalIncome - grandTotalExpense)}\n")

            fileWriter.flush()
            fileWriter.close()

            // Trigger Share Intent
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Professional Expense Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Save or Share Pro Report"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}