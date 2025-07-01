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

// Data class to hold information and view IDs for each stock
internal data class StockInfo(
    val labelViewId: Int,
    val lastUpdatedViewId: Int,
    val profitLossViewId: Int,
    val buyPriceViewId: Int,
    val stockPriceViewId: Int,
    val buyPrice: Double,
    val amount: Int,
    val apiUrl: String,
    val priceFormat: String = "€%.4f" // Default format, can be overridden
)

class StockWidgetProvider : AppWidgetProvider() {

    companion object {
        internal const val ACTION_MANUAL_REFRESH = "com.example.stockswidget.ACTION_MANUAL_REFRESH"

        // Stock 1: MIL | S3CO
        internal const val MIL_S3CO_BUY_PRICE = 0.0847
        internal const val MIL_S3CO_AMOUNT = 52356

        // Stock 2: EAM | 3AMD
        internal const val EAM_3AMD_BUY_PRICE = 0.538
        internal const val EAM_3AMD_AMOUNT = 27881

        // Stock 3: XET | COMS
        internal const val XET_COMS_BUY_PRICE = 2.4290
        internal const val XET_COMS_AMOUNT = 4117

        internal val stocks = listOf(
            StockInfo(
                R.id.stock_label_textview, R.id.last_updated_textview, R.id.profit_loss_textview,
                R.id.buy_price_textview, R.id.stock_price_textview,
                MIL_S3CO_BUY_PRICE, MIL_S3CO_AMOUNT,
                "https://scanner.tradingview.com/symbol?symbol=MIL%3AS3CO&fields=close"
            ),
            StockInfo(
                R.id.stock_label_textview_stock2, R.id.last_updated_textview_stock2, R.id.profit_loss_textview_stock2,
                R.id.buy_price_textview_stock2, R.id.stock_price_textview_stock2,
                EAM_3AMD_BUY_PRICE, EAM_3AMD_AMOUNT,
                "https://scanner.tradingview.com/symbol?symbol=EURONEXT%3A3AMD&fields=close",
                priceFormat = "€%.3f" // EAM_3AMD uses 3 decimal places
            ),
            StockInfo(
                R.id.stock_label_textview_stock3, R.id.last_updated_textview_stock3, R.id.profit_loss_textview_stock3,
                R.id.buy_price_textview_stock3, R.id.stock_price_textview_stock3,
                XET_COMS_BUY_PRICE, XET_COMS_AMOUNT,
                "https://scanner.tradingview.com/symbol?symbol=XETR%3ACOMS&fields=close"
            )
        )
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

    private suspend fun fetchPrice(apiUrl: String): Double {
        return try {
            val jsonString = URL(apiUrl).readText()
            val jsonObject = JSONObject(jsonString)
            jsonObject.getDouble("close")
        } catch (e: Exception) {
            e.printStackTrace()
            Double.NaN // Return NaN on error
        }
    }

    private fun fetchStockData(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.stock_widget_layout)
        views.setViewVisibility(R.id.loading_indicator, View.VISIBLE)
        views.setViewVisibility(R.id.divider_line, View.GONE)
        views.setViewVisibility(R.id.divider_line_2, View.GONE)

        // Hide all stock details
        stocks.forEach { stock ->
            views.setViewVisibility(stock.labelViewId, View.GONE)
            views.setViewVisibility(stock.lastUpdatedViewId, View.GONE)
            views.setViewVisibility(stock.profitLossViewId, View.GONE)
            views.setViewVisibility(stock.buyPriceViewId, View.GONE)
            views.setViewVisibility(stock.stockPriceViewId, View.GONE)
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)

        GlobalScope.launch(Dispatchers.IO) {
            val fetchedPrices = mutableListOf<Double>()
            for (stock in stocks) {
                fetchedPrices.add(fetchPrice(stock.apiUrl))
            }
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            withContext(Dispatchers.Main) {
                updateAppWidget(context, appWidgetManager, appWidgetId, fetchedPrices, currentTime)
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
    prices: List<Double>,
    updateTime: String
) {
    val views = RemoteViews(context.packageName, R.layout.stock_widget_layout)
    views.setViewVisibility(R.id.loading_indicator, View.GONE)
    views.setViewVisibility(R.id.divider_line, View.VISIBLE)
    views.setViewVisibility(R.id.divider_line_2, View.VISIBLE)

    StockWidgetProvider.stocks.forEachIndexed { index, stockInfo ->
        // Set visibility for all views related to this stock
        views.setViewVisibility(stockInfo.labelViewId, View.VISIBLE)
        views.setViewVisibility(stockInfo.lastUpdatedViewId, View.VISIBLE)
        views.setViewVisibility(stockInfo.profitLossViewId, View.VISIBLE)
        views.setViewVisibility(stockInfo.buyPriceViewId, View.VISIBLE)
        views.setViewVisibility(stockInfo.stockPriceViewId, View.VISIBLE)

        views.setTextViewText(stockInfo.buyPriceViewId, String.format(Locale.US, stockInfo.priceFormat, stockInfo.buyPrice))
        views.setTextViewText(stockInfo.lastUpdatedViewId, updateTime)

        val currentPrice = prices.getOrElse(index) { Double.NaN }

        if (currentPrice.isNaN()) {
            views.setTextViewText(stockInfo.stockPriceViewId, "N/A")
            views.setTextColor(stockInfo.stockPriceViewId, Color.WHITE)
            views.setTextViewText(stockInfo.profitLossViewId, "N/A")
            views.setTextColor(stockInfo.profitLossViewId, Color.WHITE)
        } else {
            views.setTextViewText(stockInfo.stockPriceViewId, String.format(Locale.US, stockInfo.priceFormat, currentPrice))
            when {
                currentPrice > stockInfo.buyPrice -> views.setTextColor(stockInfo.stockPriceViewId, Color.GREEN)
                currentPrice < stockInfo.buyPrice -> views.setTextColor(stockInfo.stockPriceViewId, Color.RED)
                else -> views.setTextColor(stockInfo.stockPriceViewId, Color.WHITE)
            }

            val profitOrLoss = stockInfo.amount * (currentPrice - stockInfo.buyPrice)
            views.setTextViewText(stockInfo.profitLossViewId, String.format(Locale.US, "€%,.2f", profitOrLoss))
            when {
                profitOrLoss > 0 -> views.setTextColor(stockInfo.profitLossViewId, Color.GREEN)
                profitOrLoss < 0 -> views.setTextColor(stockInfo.profitLossViewId, Color.RED)
                else -> views.setTextColor(stockInfo.profitLossViewId, Color.WHITE)
            }
        }
    }

    // Refresh button intent
    val intent = Intent(context, StockWidgetProvider::class.java).apply {
        action = StockWidgetProvider.ACTION_MANUAL_REFRESH
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId, // Use appWidgetId as requestCode to ensure uniqueness for each widget instance
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.refresh_button, pendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}
