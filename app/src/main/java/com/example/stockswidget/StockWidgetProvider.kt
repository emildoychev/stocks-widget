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
        internal const val EAM_3AMD_BUY_PRICE = 0.538
        internal const val EAM_3AMD_AMOUNT = 27881
        internal const val XET_COMS_BUY_PRICE = 2.4290 // Corrected buy price
        internal const val XET_COMS_AMOUNT = 4117
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
        // Show loading indicator and hide all stock details
        views.setViewVisibility(R.id.loading_indicator, View.VISIBLE)

        // Hide Stock 1 views
        views.setViewVisibility(R.id.stock_label_textview, View.GONE)
        views.setViewVisibility(R.id.last_updated_textview, View.GONE)
        views.setViewVisibility(R.id.profit_loss_textview, View.GONE)
        views.setViewVisibility(R.id.buy_price_textview, View.GONE)
        views.setViewVisibility(R.id.stock_price_textview, View.GONE)

        // Hide Stock 2 views
        views.setViewVisibility(R.id.stock_label_textview_stock2, View.GONE)
        views.setViewVisibility(R.id.last_updated_textview_stock2, View.GONE)
        views.setViewVisibility(R.id.profit_loss_textview_stock2, View.GONE)
        views.setViewVisibility(R.id.buy_price_textview_stock2, View.GONE)
        views.setViewVisibility(R.id.stock_price_textview_stock2, View.GONE)

        // Hide Stock 3 views
        views.setViewVisibility(R.id.stock_label_textview_stock3, View.GONE)
        views.setViewVisibility(R.id.last_updated_textview_stock3, View.GONE)
        views.setViewVisibility(R.id.profit_loss_textview_stock3, View.GONE)
        views.setViewVisibility(R.id.buy_price_textview_stock3, View.GONE)
        views.setViewVisibility(R.id.stock_price_textview_stock3, View.GONE)

        // Hide dividers
        views.setViewVisibility(R.id.divider_line, View.GONE)
        views.setViewVisibility(R.id.divider_line_2, View.GONE)

        appWidgetManager.updateAppWidget(appWidgetId, views)

        GlobalScope.launch(Dispatchers.IO) {
            var closePrice1 = Double.NaN
            var closePrice2 = Double.NaN
            var closePrice3 = Double.NaN
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            try {
                val url1 = URL("https://scanner.tradingview.com/symbol?symbol=MIL%3AS3CO&fields=close")
                val jsonString1 = url1.readText()
                val jsonObject1 = JSONObject(jsonString1)
                closePrice1 = jsonObject1.getDouble("close")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                val url2 = URL("https://scanner.tradingview.com/symbol?symbol=EURONEXT%3A3AMD&fields=close")
                val jsonString2 = url2.readText()
                val jsonObject2 = JSONObject(jsonString2)
                closePrice2 = jsonObject2.getDouble("close")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                val url3 = URL("https://scanner.tradingview.com/symbol?symbol=XETR%3ACOMS&fields=close")
                val jsonString3 = url3.readText()
                val jsonObject3 = JSONObject(jsonString3)
                closePrice3 = jsonObject3.getDouble("close")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                updateAppWidget(context, appWidgetManager, appWidgetId, closePrice1, closePrice2, closePrice3, currentTime)
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
    price1: Double,
    price2: Double,
    price3: Double,
    updateTime: String
) {
    val views = RemoteViews(context.packageName, R.layout.stock_widget_layout)

    views.setViewVisibility(R.id.loading_indicator, View.GONE)

    // Show dividers
    views.setViewVisibility(R.id.divider_line, View.VISIBLE)
    views.setViewVisibility(R.id.divider_line_2, View.VISIBLE)

    // --- Stock 1 (MIL | S3CO) --- 
    views.setViewVisibility(R.id.stock_label_textview, View.VISIBLE)
    views.setViewVisibility(R.id.last_updated_textview, View.VISIBLE)
    views.setViewVisibility(R.id.profit_loss_textview, View.VISIBLE)
    views.setViewVisibility(R.id.buy_price_textview, View.VISIBLE)
    views.setViewVisibility(R.id.stock_price_textview, View.VISIBLE)

    views.setTextViewText(R.id.buy_price_textview, String.format(Locale.US, "€%.4f", StockWidgetProvider.MIL_S3CO_BUY_PRICE))
    if (price1.isNaN()) {
        views.setTextViewText(R.id.stock_price_textview, "N/A")
        views.setTextColor(R.id.stock_price_textview, Color.WHITE)
        views.setTextViewText(R.id.profit_loss_textview, "N/A")
        views.setTextColor(R.id.profit_loss_textview, Color.WHITE)
    } else {
        views.setTextViewText(R.id.stock_price_textview, String.format(Locale.US, "€%.4f", price1))
        when {
            price1 > StockWidgetProvider.MIL_S3CO_BUY_PRICE -> views.setTextColor(R.id.stock_price_textview, Color.GREEN)
            price1 < StockWidgetProvider.MIL_S3CO_BUY_PRICE -> views.setTextColor(R.id.stock_price_textview, Color.RED)
            else -> views.setTextColor(R.id.stock_price_textview, Color.WHITE)
        }
        val profitOrLoss1 = StockWidgetProvider.MIL_S3CO_AMOUNT * (price1 - StockWidgetProvider.MIL_S3CO_BUY_PRICE)
        views.setTextViewText(R.id.profit_loss_textview, String.format(Locale.US, "€%,.2f", profitOrLoss1))
        when {
            profitOrLoss1 > 0 -> views.setTextColor(R.id.profit_loss_textview, Color.GREEN)
            profitOrLoss1 < 0 -> views.setTextColor(R.id.profit_loss_textview, Color.RED)
            else -> views.setTextColor(R.id.profit_loss_textview, Color.WHITE)
        }
    }
    views.setTextViewText(R.id.last_updated_textview, updateTime)

    // --- Stock 2 (EAM | 3AMD) --- 
    views.setViewVisibility(R.id.stock_label_textview_stock2, View.VISIBLE)
    views.setViewVisibility(R.id.last_updated_textview_stock2, View.VISIBLE)
    views.setViewVisibility(R.id.profit_loss_textview_stock2, View.VISIBLE)
    views.setViewVisibility(R.id.buy_price_textview_stock2, View.VISIBLE)
    views.setViewVisibility(R.id.stock_price_textview_stock2, View.VISIBLE)

    views.setTextViewText(R.id.buy_price_textview_stock2, String.format(Locale.US, "€%.4f", StockWidgetProvider.EAM_3AMD_BUY_PRICE))
    if (price2.isNaN()) {
        views.setTextViewText(R.id.stock_price_textview_stock2, "N/A")
        views.setTextColor(R.id.stock_price_textview_stock2, Color.WHITE)
        views.setTextViewText(R.id.profit_loss_textview_stock2, "N/A")
        views.setTextColor(R.id.profit_loss_textview_stock2, Color.WHITE)
    } else {
        views.setTextViewText(R.id.stock_price_textview_stock2, String.format(Locale.US, "€%.4f", price2))
        when {
            price2 > StockWidgetProvider.EAM_3AMD_BUY_PRICE -> views.setTextColor(R.id.stock_price_textview_stock2, Color.GREEN)
            price2 < StockWidgetProvider.EAM_3AMD_BUY_PRICE -> views.setTextColor(R.id.stock_price_textview_stock2, Color.RED)
            else -> views.setTextColor(R.id.stock_price_textview_stock2, Color.WHITE)
        }
        val profitOrLoss2 = StockWidgetProvider.EAM_3AMD_AMOUNT * (price2 - StockWidgetProvider.EAM_3AMD_BUY_PRICE)
        views.setTextViewText(R.id.profit_loss_textview_stock2, String.format(Locale.US, "€%,.2f", profitOrLoss2))
        when {
            profitOrLoss2 > 0 -> views.setTextColor(R.id.profit_loss_textview_stock2, Color.GREEN)
            profitOrLoss2 < 0 -> views.setTextColor(R.id.profit_loss_textview_stock2, Color.RED)
            else -> views.setTextColor(R.id.profit_loss_textview_stock2, Color.WHITE)
        }
    }
    views.setTextViewText(R.id.last_updated_textview_stock2, updateTime)

    // --- Stock 3 (XET | COMS) --- 
    views.setViewVisibility(R.id.stock_label_textview_stock3, View.VISIBLE)
    views.setViewVisibility(R.id.last_updated_textview_stock3, View.VISIBLE)
    views.setViewVisibility(R.id.profit_loss_textview_stock3, View.VISIBLE)
    views.setViewVisibility(R.id.buy_price_textview_stock3, View.VISIBLE)
    views.setViewVisibility(R.id.stock_price_textview_stock3, View.VISIBLE)

    views.setTextViewText(R.id.buy_price_textview_stock3, String.format(Locale.US, "€%.4f", StockWidgetProvider.XET_COMS_BUY_PRICE))
    if (price3.isNaN()) {
        views.setTextViewText(R.id.stock_price_textview_stock3, "N/A")
        views.setTextColor(R.id.stock_price_textview_stock3, Color.WHITE)
        views.setTextViewText(R.id.profit_loss_textview_stock3, "N/A")
        views.setTextColor(R.id.profit_loss_textview_stock3, Color.WHITE)
    } else {
        views.setTextViewText(R.id.stock_price_textview_stock3, String.format(Locale.US, "€%.4f", price3))
        when {
            price3 > StockWidgetProvider.XET_COMS_BUY_PRICE -> views.setTextColor(R.id.stock_price_textview_stock3, Color.GREEN)
            price3 < StockWidgetProvider.XET_COMS_BUY_PRICE -> views.setTextColor(R.id.stock_price_textview_stock3, Color.RED)
            else -> views.setTextColor(R.id.stock_price_textview_stock3, Color.WHITE)
        }
        val profitOrLoss3 = StockWidgetProvider.XET_COMS_AMOUNT * (price3 - StockWidgetProvider.XET_COMS_BUY_PRICE)
        views.setTextViewText(R.id.profit_loss_textview_stock3, String.format(Locale.US, "€%,.2f", profitOrLoss3))
        when {
            profitOrLoss3 > 0 -> views.setTextColor(R.id.profit_loss_textview_stock3, Color.GREEN)
            profitOrLoss3 < 0 -> views.setTextColor(R.id.profit_loss_textview_stock3, Color.RED)
            else -> views.setTextColor(R.id.profit_loss_textview_stock3, Color.WHITE)
        }
    }
    views.setTextViewText(R.id.last_updated_textview_stock3, updateTime)


    // Refresh button intent
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
