# ============================================================
# ExpenseTracker — ProGuard / R8 rules
# Required for isMinifyEnabled = true in release builds.
# Without these, R8 strips classes that are accessed by
# reflection, JNI, or annotation processors at runtime,
# causing crashes that only appear in release APKs.
# ============================================================

# ── Keep line numbers in crash stack traces ─────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Room ────────────────────────────────────────────────────
# Room generates DAO implementation classes at compile time via
# KSP. R8 must not rename or strip the generated *_Impl classes
# or the database entity fields that Room maps to SQL columns.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *

# ── Kotlin serialization / data classes ─────────────────────
# Kotlin data classes used as Room entities have fields accessed
# by name at runtime. Prevent R8 from renaming them.
-keepclassmembers class com.jeevan.expensetracker.data.** {
    public <init>(...);
    public ** component*();
    public ** copy(...);
}

# ── Kotlin coroutines ────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── WorkManager ──────────────────────────────────────────────
# WorkManager reflects on Worker subclass constructors
# (Context, WorkerParameters) to instantiate them.
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Lottie ───────────────────────────────────────────────────
# Lottie parses JSON animation files at runtime using field
# names. Renaming breaks animation loading.
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ── MPAndroidChart ───────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ── ML Kit (Text Recognition) ────────────────────────────────
# ML Kit loads its native model pipeline via reflection.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_latin.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_vision_text_latin.**

# ── Biometric ────────────────────────────────────────────────
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# ── AndroidX / Material ──────────────────────────────────────
# Material components reference their XML attributes by name
# in LayoutInflater. Keep the public API surface.
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ── App components accessed via Manifest ─────────────────────
# Activities, Services, BroadcastReceivers, and Providers
# declared in AndroidManifest.xml are instantiated by the OS
# using their fully-qualified class name — never rename them.
-keep class com.jeevan.expensetracker.MainActivity
-keep class com.jeevan.expensetracker.ChartsActivity
-keep class com.jeevan.expensetracker.RecycleBinActivity
-keep class com.jeevan.expensetracker.CategorySettingsActivity
-keep class com.jeevan.expensetracker.ReimbursementActivity
-keep class com.jeevan.expensetracker.TripDashboardActivity
-keep class com.jeevan.expensetracker.receiver.SmsReceiver
-keep class com.jeevan.expensetracker.service.UpiNotificationListener
-keep class com.jeevan.expensetracker.widget.QuickAddWidgetProvider
-keep class com.jeevan.expensetracker.widget.WidgetAddExpenseActivity

# ── FileProvider ─────────────────────────────────────────────
-keep class androidx.core.content.FileProvider

# ── Suppress common harmless warnings ────────────────────────
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile