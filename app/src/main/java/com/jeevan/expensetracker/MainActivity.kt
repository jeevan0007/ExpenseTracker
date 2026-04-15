package com.jeevan.expensetracker

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.jeevan.expensetracker.adapter.ExpenseAdapter
import com.jeevan.expensetracker.data.ExpenseDatabase
import com.google.android.material.datepicker.MaterialDatePicker
import com.jeevan.expensetracker.data.Expense
import com.jeevan.expensetracker.utils.CategoryManager
import com.jeevan.expensetracker.utils.ShakeDetector
import com.jeevan.expensetracker.viewmodel.ExpenseViewModel
import com.jeevan.expensetracker.worker.BudgetWorker
import com.jeevan.expensetracker.worker.RecurringExpenseWorker
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 🔥 ML KIT IMPORTS
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : AppCompatActivity() {

    private lateinit var expenseViewModel: ExpenseViewModel
    private lateinit var adapter: ExpenseAdapter
    private lateinit var lottieAnimationView: LottieAnimationView

    private lateinit var tvPredictiveInsight: TextView
    private var monthlyBudget: Double = 0.0

    // Animation States
    private var currentIncome: Double = 0.0
    private var currentExpense: Double = 0.0
    private var oldBalanceAnimState: Double = 0.0
    private var oldIncomeAnimState: Double = 0.0
    private var oldExpenseAnimState: Double = 0.0
    private val numberAnimators = mutableMapOf<Int, ValueAnimator>()

    // Premium FAB State
    private var isFabExpanded = false

    // Smart Budget Checker
    private var shouldCheckBudget: Boolean = false
    private var expenseBeforeAdd: Double = 0.0

    // UI Header
    private lateinit var tvDateHeader: TextView

    // --- CURRENCY STATE (PERSISTENT) ---
    private var activeCurrencyRate = 1.0
    private var activeCurrencyLocale = Locale("en", "IN")
    private var isTravelModeActive = false

    // --- STEALTH MODE ---
    private var isStealthMode = false

    // --- SENSOR (SHAKE) ---
    private lateinit var sensorManager: SensorManager
    private var shakeDetector: ShakeDetector? = null

    // --- RECEIPT PHOTO STATE & ML KIT ---
    private var tempReceiptUri: android.net.Uri? = null
    private var currentReceiptPreview: ImageView? = null
    private var activeAmountInput: EditText? = null // Remembers which box to type the AI data into

    // Pro-Level Biometric Engine variable
    private var authPrompt: BiometricPrompt? = null

    // Modern Photo Picker Launcher
    private val pickReceiptLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let {
            tempReceiptUri = it
            currentReceiptPreview?.apply {
                visibility = View.VISIBLE
                setImageURI(it)
                scaleX = 0f
                scaleY = 0f
                animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.5f)).start()

                // 🔥 NEW: Find the buttons from the current preview's parent layout
                val parentLayout = this.parent as? View
                parentLayout?.let { view ->
                    val btnRemove = view.findViewById<Button>(R.id.btnRemoveReceipt)
                    val btnAttach = view.findViewById<Button>(R.id.btnAttachReceipt)

                    btnRemove?.visibility = View.VISIBLE
                    btnAttach?.text = "Change"
                }
            }
            vibratePhoneLight()

            // Trigger the Offline AI Scanner
            processReceiptImage(it)
        }
    }

    // --- NOTIFICATION PERMISSION REQUEST (ANDROID 13+) ---
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity", "POST_NOTIFICATIONS permission granted.")
            } else {
                Toast.makeText(this, "Notifications disabled. You won't see expense alerts.", Toast.LENGTH_LONG).show()
                Log.w("MainActivity", "POST_NOTIFICATIONS permission denied.")
            }
        }

    // 🔥 THE ML KIT RECEIPT SCANNER ENGINE
    private fun processReceiptImage(uri: android.net.Uri) {
        Toast.makeText(this, "Scanning receipt...", Toast.LENGTH_SHORT).show()
        try {
            val image = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extractedAmount = extractTotalAmount(visionText.text)
                    if (extractedAmount != null && extractedAmount > 0) {
                        activeAmountInput?.setText(extractedAmount.toString())
                        vibratePhone()
                        Toast.makeText(this, "Auto-filled: $extractedAmount", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Could not find a clear total amount.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to scan receipt.", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractTotalAmount(text: String): Double? {
        val regex = Regex("""\b\d{1,3}(?:,\d{3})*\.\d{2}\b|\b\d+\.\d{2}\b""")
        val matches = regex.findAll(text)
        var maxAmount = 0.0

        for (match in matches) {
            val cleanString = match.value.replace(",", "")
            val value = cleanString.toDoubleOrNull() ?: 0.0

            if (value > maxAmount) {
                maxAmount = value
            }
        }
        return if (maxAmount > 0) maxAmount else null
    }

    private fun saveReceiptToInternalStorage(uri: android.net.Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val fileName = "receipt_${System.currentTimeMillis()}.jpg"
            val file = java.io.File(filesDir, fileName)
            val outputStream = java.io.FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        var isSessionUnlocked = false
        var backgroundedTime = 0L
        var isNavigatingInternally = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawerLayout)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val btnOpenDrawer = findViewById<ImageView>(R.id.btnOpenDrawer)
            val drawerParams = btnOpenDrawer.layoutParams as ViewGroup.MarginLayoutParams
            drawerParams.topMargin = systemBars.top + dpToPx(16)
            btnOpenDrawer.layoutParams = drawerParams

            val fabMain = findViewById<FloatingActionButton>(R.id.fabMain)
            val fabParams = fabMain.layoutParams as ViewGroup.MarginLayoutParams
            fabParams.bottomMargin = systemBars.bottom + dpToPx(24)
            fabMain.layoutParams = fabParams

            val rvExpenses = findViewById<RecyclerView>(R.id.rvExpenses)
            rvExpenses.setPadding(0, 0, 0, systemBars.bottom + dpToPx(80))
            insets
        }

        val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        loadSavedTheme(sharedPref)
        loadSavedCurrency(sharedPref)

        isStealthMode = sharedPref.getBoolean("stealth_mode", false)
        val ivStealthToggle = findViewById<ImageView>(R.id.ivStealthToggle)
        ivStealthToggle.setImageResource(if (isStealthMode) android.R.drawable.ic_secure else android.R.drawable.ic_menu_view)

        applySquishPhysics(ivStealthToggle) {
            isStealthMode = !isStealthMode
            sharedPref.edit().putBoolean("stealth_mode", isStealthMode).apply()

            ivStealthToggle.animate().alpha(0f).setDuration(150).withEndAction {
                ivStealthToggle.setImageResource(if (isStealthMode) android.R.drawable.ic_secure else android.R.drawable.ic_menu_view)
                updateAllStealthUI()
                ivStealthToggle.animate().alpha(1f).setDuration(150).start()
            }.start()
        }
        tvPredictiveInsight = findViewById(R.id.tvPredictiveInsight)

        monthlyBudget = sharedPref.getFloat("monthly_budget", 0f).toDouble()

        tvDateHeader = findViewById(R.id.tvDateHeader)
        lottieAnimationView = findViewById(R.id.lottieAnimationView)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navView = findViewById<NavigationView>(R.id.navView)

        val btnOpenDrawer = findViewById<ImageView>(R.id.btnOpenDrawer)
        applySquishPhysics(btnOpenDrawer) {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_trips -> {
                    isNavigatingInternally = true
                    val intent = Intent(this@MainActivity, TripDashboardActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_recycle_bin -> {
                    isNavigatingInternally = true
                    val intent = Intent(this, RecycleBinActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_settings -> {
                    isNavigatingInternally = true
                    val intent = Intent(this@MainActivity, CategorySettingsActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
            true
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shakeDetector = ShakeDetector { resetToDefaults() }

        val lockedOverlay = findViewById<LinearLayout>(R.id.lockedOverlay)
        val btnUnlockScreen = findViewById<Button>(R.id.btnUnlockScreen)
        val isAppLockEnabled = sharedPref.getBoolean("app_lock_enabled", false)

        if (isAppLockEnabled && !isSessionUnlocked) {
            lockedOverlay.visibility = View.VISIBLE
            lockedOverlay.alpha = 1f
        } else {
            lockedOverlay.visibility = View.GONE
        }

        btnUnlockScreen.setOnClickListener { launchBiometricLock(lockedOverlay) }

        val workRequest = PeriodicWorkRequestBuilder<RecurringExpenseWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("RecurringExpenseWork", ExistingPeriodicWorkPolicy.KEEP, workRequest)

        val budgetRequest = PeriodicWorkRequestBuilder<BudgetWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("BudgetWatchdog", ExistingPeriodicWorkPolicy.KEEP, budgetRequest)

        val cleanerRequest = PeriodicWorkRequestBuilder<com.jeevan.expensetracker.worker.RecycleBinWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("RecycleBinCleaner", ExistingPeriodicWorkPolicy.KEEP, cleanerRequest)

        expenseViewModel = ViewModelProvider(this)[ExpenseViewModel::class.java]

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startOfMonth = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfMonth = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        expenseViewModel.setDateRangeFilter(startOfMonth, endOfMonth)

        findViewById<Button>(R.id.btnDateFilter).text = "This Month"
        tvDateHeader.text = "Showing: This Month"

        val recyclerView = findViewById<RecyclerView>(R.id.rvExpenses)
        adapter = ExpenseAdapter(
            onItemLongClick = { expense -> showDeleteDialog(expense) },
            onItemClick = { expense -> showEditDialog(expense) }
        )

        adapter.updateCurrency(activeCurrencyRate, activeCurrencyLocale)
        adapter.setStealthMode(isStealthMode)

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && isFabExpanded) {
                    closeFabMenu()
                }
            }
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val fabMain = findViewById<FloatingActionButton>(R.id.fabMain)
                if (dy > 10 && fabMain.isShown) {
                    fabMain.hide()
                } else if (dy < -10 && !fabMain.isShown) {
                    fabMain.show()
                }
            }
        })

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                vibratePhoneLight()
                val position = viewHolder.bindingAdapterPosition
                val expenseToDelete = adapter.getExpenseAt(position)
                showDeleteDialog(expenseToDelete, position)
            }

            override fun onChildDraw(c: android.graphics.Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val itemView = viewHolder.itemView
                if (dX != 0f) {
                    val swipeProgress = Math.min(Math.abs(dX) / itemView.width.toFloat(), 1f)
                    val scale = 1f - (0.05f * swipeProgress)
                    itemView.scaleX = scale
                    itemView.scaleY = scale

                    val paint = android.graphics.Paint()
                    paint.color = android.graphics.Color.parseColor("#FF3B30")
                    paint.isAntiAlias = true
                    paint.alpha = (swipeProgress * 255).toInt().coerceIn(0, 255)

                    val scaleOffsetWidth = (itemView.width * (0.05f * swipeProgress)) / 2
                    val scaleOffsetHeight = (itemView.height * (0.05f * swipeProgress)) / 2

                    val bgRect = android.graphics.RectF(
                        itemView.left.toFloat() + scaleOffsetWidth,
                        itemView.top.toFloat() + scaleOffsetHeight,
                        itemView.right.toFloat() - scaleOffsetWidth,
                        itemView.bottom.toFloat() - scaleOffsetHeight
                    )
                    c.drawRoundRect(bgRect, 40f, 40f, paint)

                    val icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_delete)
                    icon?.let {
                        it.setTint(android.graphics.Color.WHITE)
                        val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                        val iconTop = itemView.top + iconMargin
                        val iconBottom = iconTop + it.intrinsicHeight

                        if (dX > 0) {
                            val iconLeft = itemView.left + iconMargin
                            val iconRight = iconLeft + it.intrinsicWidth
                            it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        } else {
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

        val itemTouchHelper = ItemTouchHelper(swipeCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                expenseViewModel.setSearchQuery(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val spinnerCategoryFilter = findViewById<Spinner>(R.id.spinnerCategoryFilter)
        spinnerCategoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCategory = parent?.getItemAtPosition(position).toString()
                val filterValue = if (selectedCategory == "All Categories") "All" else selectedCategory
                expenseViewModel.setCategoryFilter(filterValue)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val tvBalanceAmount = findViewById<TextView>(R.id.tvBalanceAmount)
        val tvIncomeAmount = findViewById<TextView>(R.id.tvIncomeAmount)
        val tvExpenseAmount = findViewById<TextView>(R.id.tvExpenseAmount)
        val layoutEmptyState = findViewById<LinearLayout>(R.id.layoutEmptyState)
        val rvExpensesView = findViewById<RecyclerView>(R.id.rvExpenses)
        val appBarLayout = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
        val appBarContent = findViewById<View>(R.id.appBarContent)

        expenseViewModel.filteredExpenses.observe(this) { expenses ->
            val params = appBarContent.layoutParams as com.google.android.material.appbar.AppBarLayout.LayoutParams

            if (expenses.isNullOrEmpty()) {
                appBarLayout.setExpanded(true, true)
                params.scrollFlags = 0
                appBarContent.layoutParams = params
                rvExpensesView.visibility = View.GONE
                layoutEmptyState.visibility = View.VISIBLE
                layoutEmptyState.alpha = 0f
                layoutEmptyState.animate().alpha(1f).setDuration(400).start()
                adapter.setExpenses(emptyList())
            } else {
                params.scrollFlags = com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
                appBarContent.layoutParams = params
                rvExpensesView.visibility = View.VISIBLE
                layoutEmptyState.visibility = View.GONE

                val context = rvExpensesView.context
                val controller = android.view.animation.AnimationUtils.loadLayoutAnimation(context, R.anim.layout_anim_cascade)
                rvExpensesView.layoutAnimation = controller

                adapter.setExpenses(expenses)
                rvExpensesView.scheduleLayoutAnimation()
            }
        }

        expenseViewModel.totalIncome.observe(this) { income ->
            currentIncome = income ?: 0.0
            if (isStealthMode) {
                findViewById<TextView>(R.id.tvIncomeAmount).text = "***.**"
                oldIncomeAnimState = currentIncome
            } else {
                animateNumberRoll(findViewById<TextView>(R.id.tvIncomeAmount), oldIncomeAnimState, currentIncome)
                oldIncomeAnimState = currentIncome
            }
            updateBalance(findViewById(R.id.tvBalanceAmount))
        }

        expenseViewModel.totalExpenses.observe(this) { expense ->
            currentExpense = expense ?: 0.0
            if (isStealthMode) {
                findViewById<TextView>(R.id.tvExpenseAmount).text = "***.**"
                oldExpenseAnimState = currentExpense
            } else {
                animateNumberRoll(findViewById<TextView>(R.id.tvExpenseAmount), oldExpenseAnimState, currentExpense)
                oldExpenseAnimState = currentExpense
            }
            updateBalance(findViewById(R.id.tvBalanceAmount))

            // 🔥 Trigger the new Burn Rate math here!
            updatePredictiveInsight()

            if (shouldCheckBudget && currentExpense > expenseBeforeAdd) {
                checkBudgetStatus()
                shouldCheckBudget = false
            }
        }

        tvBalanceAmount.setOnLongClickListener {
            triggerSecretCatMode()
            true
        }

        setupPremiumFab()

        val fabTravelMode = findViewById<FloatingActionButton>(R.id.fabTravelMode)
        val initialColor = if (isTravelModeActive) "#4CAF50" else "#FF9800"
        fabTravelMode.backgroundTintList = ColorStateList.valueOf(Color.parseColor(initialColor))

        fabTravelMode.setOnClickListener {
            closeFabMenu()
            vibratePhone()
            showCurrencyDialog(fabTravelMode)
        }

        val fabExport = findViewById<FloatingActionButton>(R.id.fabExport)
        fabExport.setOnClickListener {
            closeFabMenu()
            vibratePhone()
            exportDataToCSV()
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
                    closeFabMenu()
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
            isNavigatingInternally = true
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
        // --- CHECK AND REQUEST PERMISSIONS ON STARTUP ---
        checkNotificationPermission()
    }

    // --- HELPER FUNCTION FOR PERMISSION ---
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission is already granted
                    Log.d("MainActivity", "Notification permission already granted.")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Explain to the user why we need the permission, then request it
                    Toast.makeText(this, "We need notification permission to alert you when expenses are auto-logged.", Toast.LENGTH_LONG).show()
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Directly ask for the permission
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun updateAllStealthUI() {
        val tvIncome = findViewById<TextView>(R.id.tvIncomeAmount)
        val tvExpense = findViewById<TextView>(R.id.tvExpenseAmount)
        val tvBalance = findViewById<TextView>(R.id.tvBalanceAmount)

        if (isStealthMode) {
            tvIncome.text = "***.**"
            tvExpense.text = "***.**"
            tvBalance.text = "***.**"
        } else {
            // 🔥 Always roll them when un-hiding!
            animateNumberRoll(tvIncome, 0.0, currentIncome)
            animateNumberRoll(tvExpense, 0.0, currentExpense)
            animateNumberRoll(tvBalance, 0.0, currentIncome - currentExpense)
        }

        updatePredictiveInsight()

        if (::adapter.isInitialized) {
            adapter.setStealthMode(isStealthMode)
        }
    }

    private fun setupPremiumFab() {
        val fabMain = findViewById<FloatingActionButton>(R.id.fabMain)
        val dimOverlay = findViewById<View>(R.id.fabDimOverlay)
        val layoutAdd = findViewById<View>(R.id.layoutFabAdd)
        val layoutTravel = findViewById<View>(R.id.layoutFabTravel)
        val layoutExport = findViewById<View>(R.id.layoutFabExport)

        fabMain.setOnClickListener {
            isFabExpanded = !isFabExpanded
            vibratePhoneLight()
            if (isFabExpanded) {
                toggleGlassBlur(true)
                dimOverlay.visibility = View.VISIBLE
                dimOverlay.animate().alpha(1f).setDuration(200).start()
                fabMain.animate().rotation(135f).setDuration(250).start()

                val layouts = listOf(layoutAdd, layoutTravel, layoutExport)
                layouts.forEachIndexed { index, layout ->
                    layout.translationY = 50f
                    layout.alpha = 0f
                    layout.visibility = View.VISIBLE
                    layout.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setStartDelay((index * 40).toLong())
                        .setDuration(200)
                        .start()
                }
            } else {
                closeFabMenu()
            }
        }
        dimOverlay.setOnClickListener { closeFabMenu() }
    }

    private fun closeFabMenu() {
        if (!isFabExpanded) return
        isFabExpanded = false
        toggleGlassBlur(false)

        val fabMain = findViewById<FloatingActionButton>(R.id.fabMain)
        val dimOverlay = findViewById<View>(R.id.fabDimOverlay)

        fabMain.animate().rotation(0f).setDuration(250).start()

        dimOverlay.animate().alpha(0f).setDuration(200).withEndAction {
            dimOverlay.visibility = View.GONE
        }.start()

        val layouts = listOf(R.id.layoutFabAdd, R.id.layoutFabTravel, R.id.layoutFabExport)
        layouts.forEach { id ->
            val layout = findViewById<View>(id)
            layout.animate()
                .translationY(50f)
                .alpha(0f)
                .setStartDelay(0)
                .setDuration(150)
                .withEndAction { layout.visibility = View.GONE }
                .start()
        }
    }

    override fun onStart() {
        super.onStart()
        isNavigatingInternally = false
        val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        val isAppLockEnabled = sharedPref.getBoolean("app_lock_enabled", false)
        val lockedOverlay = findViewById<LinearLayout>(R.id.lockedOverlay)

        if (backgroundedTime > 0 && (System.currentTimeMillis() - backgroundedTime) > 3000) {
            isSessionUnlocked = false
        }
        backgroundedTime = 0L

        if (isAppLockEnabled && !isSessionUnlocked) {
            lockedOverlay.visibility = View.VISIBLE
            lockedOverlay.alpha = 1f
            toggleGlassBlur(true)
            lockedOverlay.postDelayed({ launchBiometricLock(lockedOverlay) }, 300)
        } else {
            lockedOverlay.visibility = View.GONE
            toggleGlassBlur(false)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isNavigatingInternally) {
            backgroundedTime = System.currentTimeMillis()
        }
    }

    override fun onResume() {
        super.onResume()
        shakeDetector?.let {
            sensorManager.registerListener(it, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI)
        }

        val spinnerCategoryFilter = findViewById<Spinner>(R.id.spinnerCategoryFilter)
        val filterCategories = mutableListOf("All Categories").apply {
            addAll(CategoryManager.getCategories(this@MainActivity).map { it.name })
        }

        val filterAdapter = ArrayAdapter(this, R.layout.spinner_item, filterCategories)
        filterAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)

        val currentSelection = spinnerCategoryFilter.selectedItem?.toString()
        spinnerCategoryFilter.adapter = filterAdapter
        val index = filterCategories.indexOf(currentSelection)
        if (index >= 0) spinnerCategoryFilter.setSelection(index)
    }

    override fun onPause() {
        super.onPause()
        shakeDetector?.let { sensorManager.unregisterListener(it) }
    }

    private fun resetToDefaults() {
        vibrateReset()
        Toast.makeText(this, "🔄 Resetting to Home Mode...", Toast.LENGTH_SHORT).show()

        // 1. Reset Currency
        setCurrency(1.0, Locale("en", "IN"), false)

        // 2. Reset Search
        expenseViewModel.setSearchQuery("")
        findViewById<EditText>(R.id.etSearch).setText("")

        // 3. Reset Category
        expenseViewModel.setCategoryFilter("All")
        findViewById<Spinner>(R.id.spinnerCategoryFilter).setSelection(0)

        // 4. Reset Date Filter back to "This Month" instead of "All Time"
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startOfMonth = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfMonth = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        expenseViewModel.setDateRangeFilter(startOfMonth, endOfMonth)

        findViewById<Button>(R.id.btnDateFilter).text = "This Month"
        tvDateHeader.text = "Showing: This Month"
    }

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

    private fun showCurrencyDialog(fab: FloatingActionButton) {
        val currencies = arrayOf("🇮🇳 INR (Base)", "🇺🇸 USD ($)", "🇪🇺 EUR (€)", "🇬🇧 GBP (£)", "🇯🇵 JPY (¥)", "🇨🇳 CNY (¥)")
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
        if (isTravel) {
            val msg = "Currency: ${locale.displayCountry}"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateHeaderCurrency() {
        val tvBalance = findViewById<TextView>(R.id.tvBalanceAmount)
        val tvIncome = findViewById<TextView>(R.id.tvIncomeAmount)
        val tvExpense = findViewById<TextView>(R.id.tvExpenseAmount)

        if (isStealthMode) {
            tvBalance.text = "***.**"
            tvIncome.text = "***.**"
            tvExpense.text = "***.**"
        } else {
            // 🔥 Force a cool re-roll from 0 whenever the currency changes!
            animateNumberRoll(tvIncome, 0.0, currentIncome)
            animateNumberRoll(tvExpense, 0.0, currentExpense)
            animateNumberRoll(tvBalance, 0.0, currentIncome - currentExpense)

            // Reset the tracking states so future updates roll from the correct spot
            oldIncomeAnimState = currentIncome
            oldExpenseAnimState = currentExpense
            oldBalanceAnimState = currentIncome - currentExpense
        }
    }

    private fun triggerSecretCatMode() {
        val rootLayout = window.decorView as ViewGroup
        val tempOverlay = View(this)
        tempOverlay.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        tempOverlay.setBackgroundColor(Color.parseColor("#1A2530"))
        tempOverlay.alpha = 0f
        tempOverlay.isClickable = true
        tempOverlay.isFocusable = true

        val tempAnimationView = LottieAnimationView(this)
        val animParams = FrameLayout.LayoutParams(dpToPx(350), dpToPx(350))
        animParams.gravity = android.view.Gravity.CENTER
        tempAnimationView.layoutParams = animParams
        tempAnimationView.setAnimation(R.raw.make_it_rain)

        rootLayout.addView(tempOverlay)
        rootLayout.addView(tempAnimationView)

        tempAnimationView.playAnimation()
        vibrateGlitchPattern()

        val flicker = ValueAnimator.ofFloat(0f, 0.85f, 0.2f, 0.85f, 0.9f)
        flicker.duration = 400
        flicker.addUpdateListener { animation -> tempOverlay.alpha = animation.animatedValue as Float }
        flicker.start()

        tempAnimationView.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                tempAnimationView.animate().alpha(0f).setDuration(400).start()
                tempOverlay.animate().alpha(0f).setDuration(600).withEndAction {
                    rootLayout.removeView(tempOverlay)
                    rootLayout.removeView(tempAnimationView)
                }.start()
                Toast.makeText(this@MainActivity, "G1 says Make it rain! 💸", Toast.LENGTH_SHORT).show()
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
        shakeAnimator.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            headerCard.translationX = value
            headerCard.translationY = value / 2
        }
        shakeAnimator.start()
    }

    private fun animateNumberRoll(textView: TextView, oldValue: Double, newValue: Double) {
        val viewId = textView.id

        // 🛡️ THE BULLETPROOF SHIELD
        // If the database re-emits the exact same target number, ignore it so we don't kill the active animation!
        if (abs(oldValue - newValue) < 0.01) {
            if (numberAnimators[viewId]?.isRunning != true) {
                val format = NumberFormat.getCurrencyInstance(activeCurrencyLocale)
                textView.text = format.format(newValue * activeCurrencyRate)
            }
            return
        }

        // 🌊 FLUID INTERRUPTION
        // If an animation is already running (e.g. Income and Expense loaded simultaneously),
        // intercept it mid-air instead of snapping!
        var startValue = oldValue.toFloat()
        numberAnimators[viewId]?.let { activeAnimator ->
            if (activeAnimator.isRunning) {
                // Grab the exact number it is currently on
                startValue = activeAnimator.animatedValue as Float
                activeAnimator.cancel()
            }
        }

        // 🚀 THE RESET OVERRIDE
        // If we explicitly passed 0.0 (like on app launch or currency switch), force it to start from 0
        if (oldValue == 0.0) {
            startValue = 0.0f
        }

        val animator = ValueAnimator.ofFloat(startValue, newValue.toFloat())
        animator.duration = 1500
        animator.interpolator = DecelerateInterpolator(1.5f)

        val format = NumberFormat.getCurrencyInstance(activeCurrencyLocale)

        animator.addUpdateListener { animation ->
            val displayValue = (animation.animatedValue as Float) * activeCurrencyRate.toFloat()
            textView.text = format.format(displayValue)
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (abs(startValue - newValue.toFloat()) > 1) vibratePhoneLight()
            }
        })

        numberAnimators[viewId] = animator
        animator.start()
    }

    private fun updateBalance(tvBalance: TextView) {
        val newBalance = currentIncome - currentExpense
        if (isStealthMode) {
            tvBalance.text = "***.**"
            oldBalanceAnimState = newBalance // keep tracking silently
        } else {
            // 🔥 Removed the travel mode bypass! Let the numbers roll!
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

    private fun vibrateReset() {
        val v = getVibrator()
        if (v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timing = longArrayOf(0, 70, 50, 70)
                v.vibrate(VibrationEffect.createWaveform(timing, -1))
            } else {
                @Suppress("DEPRECATION") v.vibrate(300)
            }
        }
    }

    private fun launchBiometricLock(overlay: View) {
        authPrompt?.cancelAuthentication()

        val executor = ContextCompat.getMainExecutor(this)
        authPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "App locked. Tap to retry.", Toast.LENGTH_SHORT).show()
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isSessionUnlocked = true
                    toggleGlassBlur(false)
                    overlay.animate().alpha(0f).setDuration(400).withEndAction {
                        overlay.visibility = View.GONE
                    }.start()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Expense Tracker")
            .setSubtitle("Authenticate to view your data")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        try {
            authPrompt?.authenticate(promptInfo)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleAppLock(button: Button) {
        val sharedPref = getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
        val isCurrentlyEnabled = sharedPref.getBoolean("app_lock_enabled", false)

        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        if (!isCurrentlyEnabled && biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Please set up a screen lock (PIN/Fingerprint) in your phone settings first.", Toast.LENGTH_LONG).show()
            return
        }

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
            .setAllowedAuthenticators(authenticators)
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
        val dialog = AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog).setView(dialogView).create()
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val radioGroupType = dialogView.findViewById<RadioGroup>(R.id.radioGroupType)
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val spinnerRecurrence = dialogView.findViewById<Spinner>(R.id.spinnerRecurrence)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnAttachReceipt = dialogView.findViewById<Button>(R.id.btnAttachReceipt)

        val btnRemoveReceipt = dialogView.findViewById<Button>(R.id.btnRemoveReceipt)
        currentReceiptPreview = dialogView.findViewById(R.id.ivReceiptPreview)

        tempReceiptUri = null
        btnRemoveReceipt.visibility = android.view.View.GONE

        // 🔥 FIXED: Initialize both Billable AND Reimbursed switches
        val switchBillable = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchBillable)
        val switchReimbursed = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchReimbursed)
        val layoutClientName = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutClientName)
        val etClientName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etClientName)

        // Sync visibility for both fields
        switchBillable.setOnCheckedChangeListener { _, isChecked ->
            val visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            layoutClientName.visibility = visibility
            switchReimbursed.visibility = visibility
        }

        activeAmountInput = etAmount

        currentReceiptPreview?.setOnClickListener {
            showFullScreenReceipt(tempReceiptUri, null)
        }

        val categories = CategoryManager.getCategories(this).map { it.name }.toMutableList()
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = spinnerAdapter

        val recurrenceOptions = listOf("None", "Monthly", "Yearly")
        val recurrenceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, recurrenceOptions)
        recurrenceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRecurrence.adapter = recurrenceAdapter

        val symbol = java.util.Currency.getInstance(activeCurrencyLocale).symbol
        etAmount.hint = "Amount ($symbol)"

        applySquishPhysics(btnRemoveReceipt) {
            currentReceiptPreview?.visibility = android.view.View.GONE
            currentReceiptPreview?.setImageDrawable(null)
            tempReceiptUri = null
            btnAttachReceipt.text = "📷 Attach Receipt"
            btnRemoveReceipt.visibility = android.view.View.GONE
            vibratePhoneLight()
        }

        applySquishPhysics(btnAttachReceipt) {
            isNavigatingInternally = true
            pickReceiptLauncher.launch("image/*")
        }

        applySquishPhysics(btnSave) {
            val amountText = etAmount.text.toString()
            val description = etDescription.text.toString()
            val category = spinnerCategory.selectedItem.toString()
            val type = if (radioGroupType.checkedRadioButtonId == R.id.radioIncome) "Income" else "Expense"
            val selectedRecurrence = spinnerRecurrence.selectedItem.toString()
            val isRecurringFlag = selectedRecurrence != "None"

            val isBillable = switchBillable.isChecked
            val clientName = if (isBillable) etClientName.text.toString().trim() else null

            // 🔥 FIXED: Get the value from the switch before using it
            val isReimbursedValue = if (isBillable) switchReimbursed.isChecked else false

            val rawInput = amountText.toDoubleOrNull()
            var hasError = false

            if (rawInput == null || rawInput <= 0) {
                shakeErrorView(etAmount)
                hasError = true
            }
            if (description.isEmpty()) {
                shakeErrorView(etDescription)
                hasError = true
            }

            if (isBillable && clientName.isNullOrEmpty()) {
                shakeErrorView(etClientName)
                hasError = true
            }

            if (hasError) {
                vibratePhone()
                Toast.makeText(this@MainActivity, "Please fill required fields", Toast.LENGTH_SHORT).show()
                return@applySquishPhysics
            }

            expenseBeforeAdd = currentExpense
            val amountInInr = if (isTravelModeActive) rawInput!! / activeCurrencyRate else rawInput!!

            var finalReceiptPath: String? = null
            tempReceiptUri?.let { uri ->
                finalReceiptPath = saveReceiptToInternalStorage(uri)
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val db = ExpenseDatabase.getDatabase(this@MainActivity)
                val activeTrip = db.expenseDao().getActiveTrip()

                val newExpense = Expense(
                    amount = amountInInr,
                    category = category,
                    description = description,
                    type = type,
                    isRecurring = isRecurringFlag,
                    recurrenceType = selectedRecurrence,
                    receiptPath = finalReceiptPath,
                    isBillable = isBillable,
                    clientName = clientName,
                    isReimbursed = isReimbursedValue, // 🔥 Now it's resolved!
                    tripId = activeTrip?.tripId
                )

                withContext(Dispatchers.Main) {
                    expenseViewModel.insert(newExpense)
                    triggerBalancePulse(type == "Income")
                    dialog.dismiss()
                    if (type == "Expense") shouldCheckBudget = true
                }
            }
        }
        applySquishPhysics(btnCancel) { dialog.dismiss() }
        dialog.show()
    }

    private fun showDeleteDialog(expense: Expense, position: Int? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val tvDeleteMessage = dialogView.findViewById<TextView>(R.id.tvDeleteMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelDelete)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmDelete)

        tvDeleteMessage.text = "Delete \"${expense.description}\"?"

        applySquishPhysics(btnCancel) {
            dialog.dismiss()
            position?.let { adapter.notifyItemChanged(it) }
        }

        dialog.setOnCancelListener {
            position?.let { adapter.notifyItemChanged(it) }
        }

        applySquishPhysics(btnConfirm) {
            vibratePhone()
            expenseViewModel.delete(expense)
            dialog.dismiss()
            Toast.makeText(this, "Moved to Recycle Bin 🗑️", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun showEditDialog(expense: Expense) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null)

        val dialog = AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setView(dialogView)
            .create()

        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        // View Initializations
        val radioGroupType = dialogView.findViewById<RadioGroup>(R.id.radioGroupType)
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val spinnerRecurrence = dialogView.findViewById<Spinner>(R.id.spinnerRecurrence)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        // Receipt UI
        val btnAttachReceipt = dialogView.findViewById<Button>(R.id.btnAttachReceipt)
        val btnRemoveReceipt = dialogView.findViewById<Button>(R.id.btnRemoveReceipt)
        currentReceiptPreview = dialogView.findViewById(R.id.ivReceiptPreview)

        // Billable & Reimbursed UI
        val switchBillable = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchBillable)
        val layoutClientName = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutClientName)
        val etClientName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etClientName)
        val switchReimbursed = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchReimbursed)

        tempReceiptUri = null
        activeAmountInput = etAmount

        // --- POPULATE DATA ---
        etAmount.setText(expense.amount.toString())
        etDescription.setText(expense.description)
        radioGroupType.check(if (expense.type == "Income") R.id.radioIncome else R.id.radioExpense)

        // 🔥 Populate Billable & Reimbursed states
        switchBillable.isChecked = expense.isBillable
        switchReimbursed.isChecked = expense.isReimbursed

        if (expense.isBillable) {
            layoutClientName.visibility = View.VISIBLE
            switchReimbursed.visibility = View.VISIBLE
            etClientName.setText(expense.clientName)
        }

        // 🔥 Sync visibility for both fields
        switchBillable.setOnCheckedChangeListener { _, isChecked ->
            val visibility = if (isChecked) View.VISIBLE else View.GONE
            layoutClientName.visibility = visibility
            switchReimbursed.visibility = visibility
        }

        // Handle existing receipt
        var finalReceiptPath: String? = expense.receiptPath
        if (expense.receiptPath != null) {
            val file = java.io.File(expense.receiptPath)
            if (file.exists()) {
                currentReceiptPreview?.visibility = View.VISIBLE
                currentReceiptPreview?.setImageURI(android.net.Uri.fromFile(file))
                btnAttachReceipt.text = "Change"
                btnRemoveReceipt.visibility = View.VISIBLE
            }
        }

        // --- BUTTON LOGIC ---

        applySquishPhysics(btnRemoveReceipt) {
            currentReceiptPreview?.visibility = View.GONE
            currentReceiptPreview?.setImageDrawable(null)
            tempReceiptUri = null
            finalReceiptPath = null
            btnAttachReceipt.text = "📷 Attach Receipt"
            btnRemoveReceipt.visibility = View.GONE
            vibratePhoneLight()
        }

        applySquishPhysics(btnAttachReceipt) {
            isNavigatingInternally = true
            pickReceiptLauncher.launch("image/*")
        }

        // Spinners setup
        val categories = CategoryManager.getCategories(this).map { it.name }.toMutableList()
        spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        categories.indexOf(expense.category).takeIf { it >= 0 }?.let { spinnerCategory.setSelection(it) }

        val recurrenceOptions = listOf("None", "Monthly", "Yearly")
        spinnerRecurrence.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, recurrenceOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val recIndex = recurrenceOptions.indexOf(expense.recurrenceType)
        if (recIndex >= 0) spinnerRecurrence.setSelection(recIndex)

        btnSave.text = "Update"
        applySquishPhysics(btnSave) {
            val amountText = etAmount.text.toString()
            val description = etDescription.text.toString()
            val type = if (radioGroupType.checkedRadioButtonId == R.id.radioIncome) "Income" else "Expense"
            val selectedRecurrence = spinnerRecurrence.selectedItem.toString()

            val isBillable = switchBillable.isChecked
            val clientName = if (isBillable) etClientName.text.toString().trim() else null
            val isReimbursed = if (isBillable) switchReimbursed.isChecked else false

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0 || description.isEmpty()) {
                vibratePhone()
                if (amount == null) shakeErrorView(etAmount)
                if (description.isEmpty()) shakeErrorView(etDescription)
                return@applySquishPhysics
            }

            tempReceiptUri?.let { uri ->
                finalReceiptPath = saveReceiptToInternalStorage(uri)
            }

            expenseViewModel.update(
                expense.copy(
                    amount = amount,
                    category = spinnerCategory.selectedItem.toString(),
                    description = description,
                    type = type,
                    isRecurring = selectedRecurrence != "None",
                    recurrenceType = selectedRecurrence,
                    receiptPath = finalReceiptPath,
                    isBillable = isBillable,
                    clientName = clientName,
                    isReimbursed = isReimbursed // 🔥 Save the state
                )
            )
            dialog.dismiss()
            if (type == "Expense") shouldCheckBudget = true
        }

        applySquishPhysics(btnCancel) { dialog.dismiss() }
        dialog.show()
    }

    private fun showSetBudgetDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_budget, null)

        // 🔥 FIX: Added Material3 Theme Overlay to fix Dark Mode text/background colors
        val dialog = AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setView(dialogView)
            .create()

        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        val etBudget = dialogView.findViewById<TextInputEditText>(R.id.etBudget)
        val btnSaveBudget = dialogView.findViewById<Button>(R.id.btnSaveBudget)
        val btnCancelBudget = dialogView.findViewById<Button>(R.id.btnCancelBudget)

        // Pre-fill existing budget
        if (monthlyBudget > 0) {
            etBudget.setText(monthlyBudget.toString())
        }

        applySquishPhysics(btnSaveBudget) {
            val budgetText = etBudget.text.toString()
            val budget = budgetText.toDoubleOrNull()

            if (budget == null || budget <= 0) {
                shakeErrorView(etBudget)
                vibratePhone()
                return@applySquishPhysics
            }

            // Save to variable and SharedPreferences
            monthlyBudget = budget
            getSharedPreferences("ExpenseTracker", MODE_PRIVATE)
                .edit()
                .putFloat("monthly_budget", budget.toFloat())
                .apply()

            vibratePhone()
            dialog.dismiss()

            // Refresh UI components
            updatePredictiveInsight()
            checkBudgetStatus()

            Toast.makeText(this, "Budget updated! 🎯", Toast.LENGTH_SHORT).show()
        }

        applySquishPhysics(btnCancelBudget) {
            dialog.dismiss()
        }

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

            btnFixBudget.backgroundTintList = ColorStateList.valueOf(color)

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
        val datePicker = MaterialDatePicker.Builder.dateRangePicker().setTitleText("Select Date Range").setTheme(R.style.PremiumDatePickerTheme).build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val utcStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selection.first }
            val utcEnd = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selection.second }

            val localStart = Calendar.getInstance().apply { set(utcStart.get(Calendar.YEAR), utcStart.get(Calendar.MONTH), utcStart.get(Calendar.DAY_OF_MONTH), 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val localEnd = Calendar.getInstance().apply { set(utcEnd.get(Calendar.YEAR), utcEnd.get(Calendar.MONTH), utcEnd.get(Calendar.DAY_OF_MONTH), 23, 59, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis

            expenseViewModel.setDateRangeFilter(localStart, localEnd)
            findViewById<Button>(R.id.btnDateFilter).text = "Custom"
            val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
            tvDateHeader.text = "Showing: ${sdf.format(Date(localStart))} - ${sdf.format(Date(localEnd))}"
            vibratePhone()
        }
        datePicker.show(supportFragmentManager, "MATERIAL_DATE_RANGE_PICKER")
    }

    private fun updateThemeButtonText(button: Button) {
        button.text = if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) "☀️ Light Mode" else "🌙 Dark Mode"
    }

    private fun loadSavedTheme(sharedPref: android.content.SharedPreferences) {
        AppCompatDelegate.setDefaultNightMode(sharedPref.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
    }

    private fun exportDataToCSV() {
        val expenses = expenseViewModel.filteredExpenses.value

        if (expenses.isNullOrEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Generating Professional PDF...", Toast.LENGTH_SHORT).show()

        try {
            val pdfFile = com.jeevan.expensetracker.utils.PdfReportGenerator.generatePdf(
                this,
                expenses,
                activeCurrencyRate,
                activeCurrencyLocale
            )

            if (pdfFile != null) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    pdfFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Professional Expense Report")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Save or Share PDF Report"))
            } else {
                Toast.makeText(this, "Failed to create PDF", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun triggerBalancePulse(isIncome: Boolean) {
        val tvBalance = findViewById<TextView>(R.id.tvBalanceAmount)
        val originalColor = tvBalance.currentTextColor
        val pulseColor = if (isIncome) Color.parseColor("#388E3C") else Color.parseColor("#D32F2F")

        tvBalance.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).withEndAction {
            tvBalance.animate().scaleX(1.0f).scaleY(1.0f).setDuration(500).setInterpolator(OvershootInterpolator()).start()
        }.start()

        val colorAnimation = ValueAnimator.ofArgb(originalColor, pulseColor, originalColor)
        colorAnimation.duration = 1000
        colorAnimation.addUpdateListener { animator -> tvBalance.setTextColor(animator.animatedValue as Int) }
        colorAnimation.start()

        vibratePhoneLight()
    }

    private fun showFullScreenReceipt(imageUri: android.net.Uri?, imagePath: String?) {
        if (imageUri == null && imagePath == null) return

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = android.widget.ImageView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            if (imageUri != null) setImageURI(imageUri) else if (imagePath != null) setImageURI(android.net.Uri.fromFile(java.io.File(imagePath)))
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setContentView(imageView)
        dialog.window?.attributes?.windowAnimations = android.R.style.Animation_Dialog
        dialog.show()
    }

    private fun shakeErrorView(view: View) {
        val shake = android.animation.ObjectAnimator.ofFloat(view, "translationX", 0f, 20f, -20f, 15f, -15f, 6f, -6f, 0f)
        shake.duration = 400
        shake.interpolator = DecelerateInterpolator()
        shake.start()
    }

    private fun toggleGlassBlur(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = if (enable) {
                android.graphics.RenderEffect.createBlurEffect(40f, 40f, android.graphics.Shader.TileMode.CLAMP)
            } else {
                null
            }
            findViewById<View>(R.id.appBarLayout).setRenderEffect(blurEffect)
            findViewById<View>(R.id.rvExpenses).setRenderEffect(blurEffect)
            findViewById<View>(R.id.layoutEmptyState).setRenderEffect(blurEffect)
        }
    }

    // 🔥 ADVANCED PREDICTIVE ENGINE (BURN RATE)
    private fun updatePredictiveInsight() {
        if (monthlyBudget <= 0) {
            tvPredictiveInsight.visibility = View.GONE
            return
        }

        val calendar = Calendar.getInstance()
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val daysRemaining = (daysInMonth - currentDay) + 1

        val remainingBudget = monthlyBudget - currentExpense
        tvPredictiveInsight.visibility = View.VISIBLE

        if (isStealthMode) {
            tvPredictiveInsight.text = "Financial Insight: Hidden"
            tvPredictiveInsight.setTextColor(Color.parseColor("#80000000"))
            return
        }

        val format = NumberFormat.getCurrencyInstance(activeCurrencyLocale)

        // STATE 1: Already broke the budget
        if (remainingBudget < 0) {
            val overage = abs(remainingBudget)
            tvPredictiveInsight.text = "🚨 Budget Exceeded by ${format.format(overage * activeCurrencyRate)}!"
            tvPredictiveInsight.setTextColor(Color.parseColor("#D32F2F")) // Deep Red
            return
        }

        // Calculate Burn Rate Velocity
        val effectiveDaysPassed = if (currentDay > 0) currentDay else 1
        val averageDailySpend = currentExpense / effectiveDaysPassed
        val safeDailySpend = remainingBudget / daysRemaining

        // STATE 2: Perfect spending / Haven't spent much yet
        if (averageDailySpend == 0.0) {
            tvPredictiveInsight.text = "✨ Perfect! Safe to spend: ${format.format(safeDailySpend * activeCurrencyRate)} / day"
            tvPredictiveInsight.setTextColor(Color.parseColor("#388E3C")) // Green
            return
        }

        val projectedTotal = averageDailySpend * daysInMonth

        // STATE 3: Burning money too fast (The Warning Engine)
        if (projectedTotal > monthlyBudget) {
            val daysUntilZero = remainingBudget / averageDailySpend
            val daysLeftInt = kotlin.math.floor(daysUntilZero).toInt()

            if (daysLeftInt <= 0) {
                tvPredictiveInsight.text = "⚠️ Warning: Budget will empty today at this rate!"
            } else {
                tvPredictiveInsight.text = "⚠️ At this rate, budget empties in $daysLeftInt days!"
            }
            tvPredictiveInsight.setTextColor(Color.parseColor("#F57C00")) // Orange Warning
        } else {
            // STATE 4: On track
            tvPredictiveInsight.text = "✅ On track! Safe to spend: ${format.format(safeDailySpend * activeCurrencyRate)} / day"
            tvPredictiveInsight.setTextColor(Color.parseColor("#388E3C")) // Green
        }
    }
}