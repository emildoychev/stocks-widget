package com.example.stockswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
        internal const val MIL_S3CO_BUY_PRICE = 0.0847
        internal const val MIL_S3CO_AMOUNT = 52356
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
        views.setViewVisibility(R.id.stock_label_textview, View.GONE)
        views.setViewVisibility(R.id.last_updated_textview, View.GONE)
        views.setViewVisibility(R.id.profit_loss_textview, View.GONE) // Hide profit/loss during load
        views.setViewVisibility(R.id.buy_price_textview, View.GONE) 
        views.setViewVisibility(R.id.stock_price_textview, View.GONE) 
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
    views.setViewVisibility(R.id.stock_label_textview, View.VISIBLE)
    views.setViewVisibility(R.id.last_updated_textview, View.VISIBLE)
    views.setViewVisibility(R.id.profit_loss_textview, View.VISIBLE) // Show profit/loss
    views.setViewVisibility(R.id.buy_price_textview, View.VISIBLE) 
    views.setViewVisibility(R.id.stock_price_textview, View.VISIBLE) 

    // Set Buy Price
    views.setTextViewText(R.id.buy_price_textview, String.format(Locale.US, "€%.4f", StockWidgetProvider.MIL_S3CO_BUY_PRICE))

    // Set Current Stock Price and Color
    if (price.isNaN()) {
        views.setTextViewText(R.id.stock_price_textview, "N/A") // Removed "C: " prefix
        views.setTextColor(R.id.stock_price_textview, Color.WHITE)
        views.setTextViewText(R.id.profit_loss_textview, "N/A")
        views.setTextColor(R.id.profit_loss_textview, Color.WHITE)
    } else {
        views.setTextViewText(R.id.stock_price_textview, String.format(Locale.US, "€%.4f", price))
        when {
            price > StockWidgetProvider.MIL_S3CO_BUY_PRICE -> views.setTextColor(R.id.stock_price_textview, Color.GREEN)
            price < StockWidgetProvider.MIL_S3CO_BUY_PRICE -> views.setTextColor(R.id.stock_price_textview, Color.RED)
            else -> views.setTextColor(R.id.stock_price_textview, Color.WHITE)
        }

        // Calculate and Display Profit/Loss
        val profitOrLoss = StockWidgetProvider.MIL_S3CO_AMOUNT * (price - StockWidgetProvider.MIL_S3CO_BUY_PRICE)
        views.setTextViewText(R.id.profit_loss_textview, String.format(Locale.US, "€%.2f", profitOrLoss))
        when {
            profitOrLoss > 0 -> views.setTextColor(R.id.profit_loss_textview, Color.GREEN)
            profitOrLoss < 0 -> views.setTextColor(R.id.profit_loss_textview, Color.RED)
            else -> views.setTextColor(R.id.profit_loss_textview, Color.WHITE)
        }
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
