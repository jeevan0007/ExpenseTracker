package com.jeevan.expensetracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.jeevan.expensetracker.R

class QuickAddWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {

            val intent = Intent(context, WidgetAddExpenseActivity::class.java).apply {
                // Explicitly tell the OS exactly which file to open
                component = android.content.ComponentName(context, WidgetAddExpenseActivity::class.java)
                action = "com.jeevan.expensetracker.FORCE_WIDGET_ADD_$appWidgetId"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }

            // CRITICAL FIX: FLAG_CANCEL_CURRENT destroys the stubborn ColorOS cache
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId, intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)

            // CRITICAL FIX: Attach the click listener to both the Image AND the Root layout
            views.setOnClickPendingIntent(R.id.btnWidgetAdd, pendingIntent)
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}