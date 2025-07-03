package com.example.stockswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
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
    val buyPrice: Double, // For simple stocks, this is the buy price. For complex, it\\\'s a placeholder or not directly used for display.
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

        // Stock 1: MIL | S3CO
        internal const val MIL_S3CO_BUY_PRICE = 0.0847
        internal const val MIL_S3CO_AMOUNT = 52356.0

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
                "https://scanner.tradingview.com/symbol?symbol=EURONEXT%3A3AMD&fields=close"
            ),
            StockInfo(
                R.id.stock_label_textview_stock3, R.id.last_updated_textview_stock3, R.id.profit_loss_textview_stock3,
                R.id.buy_price_textview_stock3, R.id.stock_price_textview_stock3,
                XET_COMS_BUY_PRICE, XET_COMS_AMOUNT,
                "https://scanner.tradingview.com/symbol?symbol=XETR%3ACOMS&fields=close"
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
        Log.d(TAG, "Fetching price for URL: $apiUrl")
        return try {
            val jsonString = URL(apiUrl).readText()
            val jsonObject = JSONObject(jsonString)
            jsonObject.getDouble("close")
        } catch (e: Exception) {
            Log.e(TAG, "fetchPrice Error for $apiUrl: ${'$'}{e.message}", e)
            Double.NaN
        }
    }

    private suspend fun fetchGraphQLPrice(
        apiUrl: String,
        query: String,
        variables: JSONObject
    ): Double {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(apiUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("x-consumer-id", "GPX")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val payload = JSONObject()
            payload.put("query", query)
            payload.put("variables", variables)

            val escapedPayload = payload.toString().replace("'", "\'\'\'")
            val curlCommand = """
                curl -X ${connection.requestMethod} "$apiUrl" \
                -H "Content-Type: ${connection.getRequestProperty("Content-Type")}" \
                -H "Accept: ${connection.getRequestProperty("Accept")}" \
                -H "x-consumer-id: ${connection.getRequestProperty("x-consumer-id")}" \
                -d '$escapedPayload'
            """.trimIndent()
            Log.d(TAG, "Equivalent cURL command:\n$curlCommand")

            Log.d(TAG, "GraphQL Payload for $apiUrl: ${'$'}{payload.toString()}")
            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "GraphQL Response Code for $apiUrl: $responseCode")

            val streamReader = if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStreamReader(connection.inputStream)
            } else {
                InputStreamReader(connection.errorStream ?: connection.inputStream)
            }

            BufferedReader(streamReader).use { reader ->
                val responseString = reader.readText()
                Log.d(TAG, "GraphQL Raw Response for $apiUrl: $responseString")
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonResponse = JSONObject(responseString)
                    val dataObject = jsonResponse.optJSONObject("data")
                    val fundsArray = dataObject?.optJSONArray("funds")
                    if (fundsArray != null && fundsArray.length() > 0) {
                        val firstFund = fundsArray.optJSONObject(0)
                        val pricingDetails = firstFund?.optJSONObject("pricingDetails")
                        val navPrices = pricingDetails?.optJSONObject("navPrices")
                        val itemsArray = navPrices?.optJSONArray("items")
                        if (itemsArray != null && itemsArray.length() > 0) {
                            val firstItem = itemsArray.optJSONObject(0)
                            return firstItem?.optDouble("price", Double.NaN) ?: Double.NaN
                        }
                    }
                } else {
                    Log.e(TAG, "GraphQL Error for $apiUrl. Response: $responseString")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchGraphQLPrice Error for $apiUrl: ${'$'}{e.message}", e)
        } finally {
            connection?.disconnect()
        }
        return Double.NaN
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
        views.setViewVisibility(R.id.divider_line_3, View.GONE)
        views.setViewVisibility(R.id.divider_line_4, View.GONE)
        views.setViewVisibility(R.id.divider_line_5, View.GONE)


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
                val price = if (stock.isGraphQL) {
                    fetchGraphQLPrice(stock.apiUrl, stock.graphQLQuery!!, stock.graphQLVariables!!)
                } else {
                    fetchPrice(stock.apiUrl)
                }
                fetchedPrices.add(price)
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
    views.setViewVisibility(R.id.divider_line_3, View.VISIBLE)
    views.setViewVisibility(R.id.divider_line_4, View.VISIBLE)
    views.setViewVisibility(R.id.divider_line_5, View.VISIBLE)

    StockWidgetProvider.stocks.forEachIndexed { index, stockInfo ->
        views.setViewVisibility(stockInfo.labelViewId, View.VISIBLE)
        views.setViewVisibility(stockInfo.lastUpdatedViewId, View.VISIBLE)
        views.setViewVisibility(stockInfo.profitLossViewId, View.VISIBLE)
        views.setViewVisibility(stockInfo.buyPriceViewId, View.VISIBLE)
        views.setViewVisibility(stockInfo.stockPriceViewId, View.VISIBLE)

        var totalInitialInvestmentCost: Double? = null
        if (index == 3) { // ABN Stock
            totalInitialInvestmentCost = (StockWidgetProvider.ABN_AMOUNT1 * StockWidgetProvider.ABN_BUY_PRICE1) +
                                         (StockWidgetProvider.ABN_AMOUNT2 * StockWidgetProvider.ABN_BUY_PRICE2) +
                                         (StockWidgetProvider.ABN_AMOUNT3 * StockWidgetProvider.ABN_BUY_PRICE3) +
                                         (StockWidgetProvider.ABN_AMOUNT4 * StockWidgetProvider.ABN_BUY_PRICE4) +
                                         (StockWidgetProvider.ABN_AMOUNT5 * StockWidgetProvider.ABN_BUY_PRICE5) +
                                         (StockWidgetProvider.ABN_AMOUNT6 * StockWidgetProvider.ABN_BUY_PRICE6)
        } else if (index == 4) { // AMS_VUSA Stock
            totalInitialInvestmentCost = (StockWidgetProvider.AMS_VUSA_AMOUNT1 * StockWidgetProvider.AMS_VUSA_BUY_PRICE1) +
                                         (StockWidgetProvider.AMS_VUSA_AMOUNT2 * StockWidgetProvider.AMS_VUSA_BUY_PRICE2) +
                                         (StockWidgetProvider.AMS_VUSA_AMOUNT3 * StockWidgetProvider.AMS_VUSA_BUY_PRICE3)
        }

        if (index == 3) { // ABN Stock
            // Display total shares (amount) formatted to 4 decimal places
            views.setTextViewText(stockInfo.buyPriceViewId, String.format(Locale.US, "%.4f", stockInfo.amount))
        } else if (index == 4) { // AMS_VUSA Stock
            // Display total shares (amount) formatted to 0 decimal places
            views.setTextViewText(stockInfo.buyPriceViewId, String.format(Locale.US, "%.0f", stockInfo.amount))
        } else { // Simple stocks
            views.setTextViewText(stockInfo.buyPriceViewId, String.format(Locale.US, stockInfo.priceFormat, stockInfo.buyPrice))
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
            if (index != 3 && index != 4) { // For simple stocks, color based on buy price vs current price
                 when {
                    currentPrice > stockInfo.buyPrice -> views.setTextColor(stockInfo.stockPriceViewId, Color.GREEN)
                    currentPrice < stockInfo.buyPrice -> views.setTextColor(stockInfo.stockPriceViewId, Color.RED)
                    else -> views.setTextColor(stockInfo.stockPriceViewId, Color.WHITE)
                }
            } // For complex stocks (ABN, AMS_VUSA), stockPriceViewId color remains default (usually white based on XML)

            val profitOrLoss: Double
            if (index == 3 || index == 4) { // ABN or AMS_VUSA - use pre-calculated totalInitialInvestmentCost for profit/loss
                 profitOrLoss = (stockInfo.amount * currentPrice) - totalInitialInvestmentCost!! // stockInfo.amount is total shares
            } else { // Standard calculation for other stocks
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
