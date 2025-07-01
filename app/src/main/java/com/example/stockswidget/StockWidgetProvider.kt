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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockWidgetProvider : AppWidgetProvider() {

    companion object {
        internal const val ACTION_MANUAL_REFRESH = "com.example.stockswidget.ACTION_MANUAL_REFRESH"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            fetchStockData(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
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
        val views = RemoteViews(context.packageName, R.layout.stock_widget_layout)
        views.setViewVisibility(R.id.loading_indicator, View.VISIBLE)
        views.setViewVisibility(R.id.stock_price_textview, View.GONE)
        views.setViewVisibility(R.id.stock_label_textview, View.GONE)
        views.setViewVisibility(R.id.last_updated_textview, View.GONE) // Hide last updated time during load
        appWidgetManager.updateAppWidget(appWidgetId, views)

        GlobalScope.launch(Dispatchers.IO) {
            var closePrice = Double.NaN
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            try {
                val url = URL("https://scanner.tradingview.com/symbol?symbol=MIL%3AS3CO&fields=close")
                val jsonString = url.readText()
                val jsonObject = JSONObject(jsonString)
                closePrice = jsonObject.getDouble("close")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, closePrice, currentTime)
                }
            }
        }
    }

    override fun onEnabled(context: Context) {}

    override fun onDisabled(context: Context) {}
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    price: Double,
    updateTime: String
) {
    val views = RemoteViews(context.packageName, R.layout.stock_widget_layout)

    views.setViewVisibility(R.id.loading_indicator, View.GONE)
    views.setViewVisibility(R.id.stock_price_textview, View.VISIBLE)
    views.setViewVisibility(R.id.stock_label_textview, View.VISIBLE)
    views.setViewVisibility(R.id.last_updated_textview, View.VISIBLE) // Show last updated time

    if (price.isNaN()) {
        views.setTextViewText(R.id.stock_price_textview, "N/A")
    } else {
        views.setTextViewText(R.id.stock_price_textview, String.format(Locale.US, "€%.4f", price))
    }
    views.setTextViewText(R.id.last_updated_textview, updateTime)

    val intent = Intent(context, StockWidgetProvider::class.java).apply {
        action = StockWidgetProvider.ACTION_MANUAL_REFRESH
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.refresh_button, pendingIntent)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
