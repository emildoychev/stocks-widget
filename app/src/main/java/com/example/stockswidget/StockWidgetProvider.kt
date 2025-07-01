package com.example.stockswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class StockWidgetProvider : AppWidgetProvider() {

    companion object {
        internal const val ACTION_MANUAL_REFRESH = "com.example.stockswidget.ACTION_MANUAL_REFRESH"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            fetchStockData(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // Important to call super
        if (ACTION_MANUAL_REFRESH == intent.action) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                fetchStockData(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun fetchStockData(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Show loading indicator
        val views = RemoteViews(context.packageName, R.layout.stock_widget_layout)
        views.setViewVisibility(R.id.loading_indicator, View.VISIBLE)
        views.setViewVisibility(R.id.stock_price_textview, View.GONE)
        appWidgetManager.updateAppWidget(appWidgetId, views)

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://scanner.tradingview.com/symbol?symbol=MIL%3AS3CO&fields=close")
                val jsonString = url.readText()
                val jsonObject = JSONObject(jsonString)
                val closePrice = jsonObject.getDouble("close")

                withContext(Dispatchers.Main) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, closePrice)
                }
            } catch (e: Exception) {
                // Handle error, e.g., show a default error message in the widget
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, Double.NaN) // Or some error indicator
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    price: Double
) {
    val views = RemoteViews(context.packageName, R.layout.stock_widget_layout)

    // Hide loading indicator, show price text
    views.setViewVisibility(R.id.loading_indicator, View.GONE)
    views.setViewVisibility(R.id.stock_price_textview, View.VISIBLE)

    if (price.isNaN()) {
        views.setTextViewText(R.id.stock_price_textview, "N/A")
    } else {
        views.setTextViewText(R.id.stock_price_textview, String.format("€%.4f", price))
    }

    // Setup for refresh button
    val intent = Intent(context, StockWidgetProvider::class.java).apply {
        action = StockWidgetProvider.ACTION_MANUAL_REFRESH
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId, // Use appWidgetId as requestCode to ensure PIs for different widget instances are unique
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.refresh_button, pendingIntent)
    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
