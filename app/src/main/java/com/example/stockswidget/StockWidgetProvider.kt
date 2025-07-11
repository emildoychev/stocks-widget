package com.example.stockswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Data class to hold information and view IDs for each stock
internal data class StockInfo(
    val labelViewId: Int,
    val lastUpdatedViewId: Int,
    val profitLossViewId: Int,
    val buyPriceViewId: Int,
    val stockPriceViewId: Int,
    val buyPrice: Double, // For simple stocks, this is the buy price. For complex, it's a placeholder or not directly used for display.
    val amount: Double, // For simple stocks, this is the amount. For complex, this is total shares.
    val apiUrl: String,
    val priceFormat: String = "€%.4f", // Default format
    val isGraphQL: Boolean = false,
    val graphQLQuery: String? = null,
    val graphQLVariables: JSONObject? = null
)

class StockWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "StockWidgetProvider"
        internal const val ACTION_MANUAL_REFRESH = "com.example.stockswidget.ACTION_MANUAL_REFRESH"
        private const val STOCK_UPDATE_WORK_NAME = "com.example.stockswidget.STOCK_UPDATE_WORK"
        private const val WORK_REPEAT_INTERVAL_MINUTES = 30L

        // API URLs
        internal const val API_URL_XETR_CIWP = "https://scanner.tradingview.com/symbol?symbol=XETR%3ACIWP&fields=close"
        internal const val API_URL_EURONEXT_3AMD = "https://scanner.tradingview.com/symbol?symbol=EURONEXT%3A3AMD&fields=close"
        internal const val API_URL_XETR_COMS = "https://scanner.tradingview.com/symbol?symbol=XETR%3ACOMS&fields=close"
        // ABN_GRAPHQL_URL, AMS_VUSA_API_URL, XETR_QDVE_API_URL are defined below with their respective stock details

        // Stock 1: XET | CIWP
        internal const val XET_CIWP_BUY_PRICE_ORIG = 0.7085
        internal const val XET_CIWP_AMOUNT_ORIG = 3740.0
        internal const val XET_CIWP_BUY_PRICE = 0.7085
        internal const val XET_CIWP_AMOUNT = 3740.0

        // Stock 2: EAM | 3AMD
        internal const val EAM_3AMD_BUY_PRICE = 0.538
        internal const val EAM_3AMD_AMOUNT = 27881.0

        // Stock 3: XET | COMS
        internal const val XET_COMS_BUY_PRICE = 2.4290
        internal const val XET_COMS_AMOUNT = 4117.0

        // Stock 4: ABN
        internal const val ABN_BUY_PRICE1 = 183.020
        internal const val ABN_AMOUNT1 = 0.5464
        internal const val ABN_BUY_PRICE2 = 175.070
        internal const val ABN_AMOUNT2 = 0.2856
        internal const val ABN_BUY_PRICE3 = 179.400
        internal const val ABN_AMOUNT3 = 0.2787
        internal const val ABN_BUY_PRICE4 = 188.740
        internal const val ABN_AMOUNT4 = 10.5966
        internal const val ABN_BUY_PRICE5 = 266.860
        internal const val ABN_AMOUNT5 = 30.6977
        internal const val ABN_BUY_PRICE6 = 348.720
        internal const val ABN_AMOUNT6 = 29.7431
        internal const val ABN_GRAPHQL_URL = "https://www.nl.vanguard/gpx/graphql"
        internal val ABN_GRAPHQL_VARIABLES = JSONObject().apply {
            put("portIds", JSONObject.wrap(listOf("9179")))
            put("skipNavPrice", false)
        }
        internal const val ABN_GRAPHQL_QUERY = """
            query PolarisProductDetailFundCardsQuery(${'$'}portIds: [String!]!, ${'$'}skipNavPrice: Boolean!) {
              funds(portIds: ${'$'}portIds) {
                pricingDetails {
                  navPrices(limit: 1) @skip(if: ${'$'}skipNavPrice) {
                    items {
                      asOfDate
                      currencyCode
                      price
                    }
                  }
                }
              }
            }
        """

        // Stock 5: AMS | VUSA
        internal const val AMS_VUSA_BUY_PRICE1 = 75.0
        internal const val AMS_VUSA_AMOUNT1 = 149.0
        internal const val AMS_VUSA_BUY_PRICE2 = 97.75
        internal const val AMS_VUSA_AMOUNT2 = 78.0
        internal const val AMS_VUSA_BUY_PRICE3 = 98.0
        internal const val AMS_VUSA_AMOUNT3 = 27.0
        internal const val AMS_VUSA_API_URL = "https://scanner.tradingview.com/symbol?symbol=EURONEXT%3AVUSA&fields=close"

        // Stock 6: XETR | QDVE
        internal const val XETR_QDVE_BUY_PRICE1 = 28.065
        internal const val XETR_QDVE_AMOUNT1 = 884.0
        internal const val XETR_QDVE_BUY_PRICE2 = 0.0
        internal const val XETR_QDVE_AMOUNT2 = 0.0
        internal const val XETR_QDVE_API_URL = "https://scanner.tradingview.com/symbol?symbol=XETR%3AQDVE&fields=close"


        internal val stocks = listOf(
            StockInfo(
                R.id.stock_label_textview, R.id.last_updated_textview, R.id.profit_loss_textview,
                R.id.buy_price_textview, R.id.stock_price_textview,
                XET_CIWP_BUY_PRICE, XET_CIWP_AMOUNT,
                API_URL_XETR_CIWP,
            ),
            StockInfo(
                R.id.stock_label_textview_stock2, R.id.last_updated_textview_stock2, R.id.profit_loss_textview_stock2,
                R.id.buy_price_textview_stock2, R.id.stock_price_textview_stock2,
                EAM_3AMD_BUY_PRICE, EAM_3AMD_AMOUNT,
                API_URL_EURONEXT_3AMD
            ),
            StockInfo(
                R.id.stock_label_textview_stock3, R.id.last_updated_textview_stock3, R.id.profit_loss_textview_stock3,
                R.id.buy_price_textview_stock3, R.id.stock_price_textview_stock3,
                XET_COMS_BUY_PRICE, XET_COMS_AMOUNT,
                API_URL_XETR_COMS
            ),
            StockInfo(
                R.id.stock_label_textview_stock4, R.id.last_updated_textview_stock4, R.id.profit_loss_textview_stock4,
                R.id.buy_price_textview_stock4, R.id.stock_price_textview_stock4,
                0.0, // Placeholder, actual buy price for ABN is complex
                ABN_AMOUNT1 + ABN_AMOUNT2 + ABN_AMOUNT3 + ABN_AMOUNT4 + ABN_AMOUNT5 + ABN_AMOUNT6, // Total shares
                ABN_GRAPHQL_URL,
                priceFormat = "€%.2f",
                isGraphQL = true,
                graphQLQuery = ABN_GRAPHQL_QUERY,
                graphQLVariables = ABN_GRAPHQL_VARIABLES
            ),
            StockInfo(
                R.id.stock_label_textview_stock5, R.id.last_updated_textview_stock5, R.id.profit_loss_textview_stock5,
                R.id.buy_price_textview_stock5, R.id.stock_price_textview_stock5,
                0.0, // Placeholder, actual buy price for AMS_VUSA is complex
                AMS_VUSA_AMOUNT1 + AMS_VUSA_AMOUNT2 + AMS_VUSA_AMOUNT3, // Total shares
                AMS_VUSA_API_URL,
                priceFormat = "€%.2f"
            ),
            StockInfo(
                R.id.stock_label_textview_stock6, R.id.last_updated_textview_stock6, R.id.profit_loss_textview_stock6,
                R.id.buy_price_textview_stock6, R.id.stock_price_textview_stock6,
                0.0, // Placeholder, actual buy price for XETR_QDVE is complex
                XETR_QDVE_AMOUNT1 + XETR_QDVE_AMOUNT2, // Total shares
                XETR_QDVE_API_URL,
                priceFormat = "€%.2f"
            )
        )
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called. Scheduling periodic work.")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<StockUpdateWorker>(WORK_REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            STOCK_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicWorkRequest
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_MANUAL_REFRESH == intent.action) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            Log.d(TAG, "Manual refresh triggered for widget ID: $appWidgetId")
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val oneTimeWorkRequest = OneTimeWorkRequestBuilder<StockUpdateWorker>()
                    .setConstraints(constraints)
                    .setInputData(workDataOf(StockUpdateWorker.KEY_APP_WIDGET_IDS to intArrayOf(appWidgetId)))
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)
            }
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "onEnabled: First widget instance created. Scheduling initial work.")
        // This is a good place to schedule the first update immediately if desired,
        // or ensure the periodic work is scheduled.
        // For simplicity, onUpdate will handle the periodic scheduling when the first widget is added.
        // If you want an immediate update on first placement, you can enqueue a OneTimeWorkRequest here.
         val appWidgetManager = AppWidgetManager.getInstance(context)
         val thisAppWidget = ComponentName(context.packageName, javaClass.name)
         val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
         if (appWidgetIds.isNotEmpty()) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<StockUpdateWorker>()
                .setConstraints(constraints)
                 .setInputData(workDataOf(StockUpdateWorker.KEY_APP_WIDGET_IDS to appWidgetIds))
                .build() // Consider also making this expedited if needed on first enable
            WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)
         }
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "onDisabled: Last widget instance removed. Cancelling work.")
        WorkManager.getInstance(context).cancelUniqueWork(STOCK_UPDATE_WORK_NAME)
    }
}

// This function remains top-level or can be moved to a companion object if preferred by worker.
// It's used by StockUpdateWorker to update the widget UI.
internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    prices: List<Double>,
    updateTime: String
) {
    Log.d("updateAppWidget", "Updating widget $appWidgetId with ${prices.size} prices at $updateTime")
    val views = RemoteViews(context.packageName, R.layout.stock_widget_layout)

    // Resetting visibility for all views initially to handle cases where some might have been GONE
    views.setViewVisibility(R.id.loading_indicator, View.GONE)
    views.setViewVisibility(R.id.content_container, View.VISIBLE) // Make sure content container is visible
    
    val dividerIds = listOf(R.id.divider_line, R.id.divider_line_2, R.id.divider_line_3, R.id.divider_line_4, R.id.divider_line_5) // Assuming up to 6 stocks
    dividerIds.forEachIndexed { index, dividerId ->
        views.setViewVisibility(dividerId, if (index < StockWidgetProvider.stocks.size -1) View.VISIBLE else View.GONE)
    }

    StockWidgetProvider.stocks.forEachIndexed { index, stockInfo ->
        views.setViewVisibility(stockInfo.labelViewId, View.VISIBLE)
        views.setViewVisibility(stockInfo.lastUpdatedViewId, View.VISIBLE)
        views.setViewVisibility(stockInfo.profitLossViewId, View.VISIBLE)
        views.setViewVisibility(stockInfo.buyPriceViewId, View.VISIBLE)
        views.setViewVisibility(stockInfo.stockPriceViewId, View.VISIBLE)

        var totalInitialInvestmentCost: Double? = null // For complex stocks

        when (index) {
            3 -> { // ABN Stock
                totalInitialInvestmentCost = (StockWidgetProvider.ABN_AMOUNT1 * StockWidgetProvider.ABN_BUY_PRICE1) +
                                             (StockWidgetProvider.ABN_AMOUNT2 * StockWidgetProvider.ABN_BUY_PRICE2) +
                                             (StockWidgetProvider.ABN_AMOUNT3 * StockWidgetProvider.ABN_BUY_PRICE3) +
                                             (StockWidgetProvider.ABN_AMOUNT4 * StockWidgetProvider.ABN_BUY_PRICE4) +
                                             (StockWidgetProvider.ABN_AMOUNT5 * StockWidgetProvider.ABN_BUY_PRICE5) +
                                             (StockWidgetProvider.ABN_AMOUNT6 * StockWidgetProvider.ABN_BUY_PRICE6)
                views.setTextViewText(stockInfo.buyPriceViewId, String.format(Locale.US, "%.4f", stockInfo.amount))
            }
            4 -> { // AMS_VUSA Stock
                totalInitialInvestmentCost = (StockWidgetProvider.AMS_VUSA_AMOUNT1 * StockWidgetProvider.AMS_VUSA_BUY_PRICE1) +
                                             (StockWidgetProvider.AMS_VUSA_AMOUNT2 * StockWidgetProvider.AMS_VUSA_BUY_PRICE2) +
                                             (StockWidgetProvider.AMS_VUSA_AMOUNT3 * StockWidgetProvider.AMS_VUSA_BUY_PRICE3)
                views.setTextViewText(stockInfo.buyPriceViewId, String.format(Locale.US, "%.0f", stockInfo.amount))
            }
            5 -> { // XETR_QDVE Stock
                totalInitialInvestmentCost = (StockWidgetProvider.XETR_QDVE_AMOUNT1 * StockWidgetProvider.XETR_QDVE_BUY_PRICE1) +
                                             (StockWidgetProvider.XETR_QDVE_AMOUNT2 * StockWidgetProvider.XETR_QDVE_BUY_PRICE2)
                views.setTextViewText(stockInfo.buyPriceViewId, String.format(Locale.US, "%.0f", stockInfo.amount))
            }
            else -> { // Simple stocks (index 0, 1, 2)
                views.setTextViewText(stockInfo.buyPriceViewId, String.format(Locale.US, stockInfo.priceFormat, stockInfo.buyPrice))
            }
        }
        
        views.setTextViewText(stockInfo.lastUpdatedViewId, updateTime)

        val currentPrice = prices.getOrElse(index) { Double.NaN }

        if (currentPrice.isNaN()) {
            views.setTextViewText(stockInfo.stockPriceViewId, "N/A")
            views.setTextColor(stockInfo.stockPriceViewId, Color.WHITE)
            views.setTextViewText(stockInfo.profitLossViewId, "N/A")
            views.setTextColor(stockInfo.profitLossViewId, Color.WHITE)
        } else {
            views.setTextViewText(stockInfo.stockPriceViewId, String.format(Locale.US, stockInfo.priceFormat, currentPrice))
            // Color coding for current price vs buy price (only for simple stocks)
            if (index <= 2) { // Assuming stocks 0, 1, 2 are simple and others are complex
                 when {
                    currentPrice > stockInfo.buyPrice -> views.setTextColor(stockInfo.stockPriceViewId, Color.GREEN)
                    currentPrice < stockInfo.buyPrice -> views.setTextColor(stockInfo.stockPriceViewId, Color.RED)
                    else -> views.setTextColor(stockInfo.stockPriceViewId, Color.WHITE)
                }
            } else { // For complex stocks, always show white or a default color
                 views.setTextColor(stockInfo.stockPriceViewId, Color.WHITE)
            }


            val profitOrLoss: Double
            if (index == 0) { // Stock 1: XET | CIWP - Special profit/loss calculation
                val initialInvestmentCostOrig = StockWidgetProvider.XET_CIWP_BUY_PRICE_ORIG * StockWidgetProvider.XET_CIWP_AMOUNT_ORIG
                val currentMarketValue = currentPrice * stockInfo.amount // stockInfo.amount is XET_CIWP_AMOUNT
                profitOrLoss = currentMarketValue - initialInvestmentCostOrig
            } else if (totalInitialInvestmentCost != null) { // Complex stocks (ABN, AMS_VUSA, XETR_QDVE)
                 profitOrLoss = (stockInfo.amount * currentPrice) - totalInitialInvestmentCost
            } else { // Standard calculation for other simple stocks (index 1, 2)
                profitOrLoss = stockInfo.amount * (currentPrice - stockInfo.buyPrice)
            }
            
            views.setTextViewText(stockInfo.profitLossViewId, String.format(Locale.US, "€%,.2f", profitOrLoss))
            when {
                profitOrLoss > 0 -> views.setTextColor(stockInfo.profitLossViewId, Color.GREEN)
                profitOrLoss < 0 -> views.setTextColor(stockInfo.profitLossViewId, Color.RED)
                else -> views.setTextColor(stockInfo.profitLossViewId, Color.WHITE)
            }
        }
    }

    // Setup refresh button intent
    val intent = Intent(context, StockWidgetProvider::class.java).apply {
        action = StockWidgetProvider.ACTION_MANUAL_REFRESH
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
    val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        appWidgetId, // Use appWidgetId as requestCode to ensure uniqueness for each widget instance
        intent,
        pendingIntentFlag
    )
    views.setOnClickPendingIntent(R.id.refresh_button, pendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}
