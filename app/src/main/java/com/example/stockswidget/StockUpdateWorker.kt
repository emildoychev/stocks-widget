package com.example.stockswidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
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
import java.util.TimeZone // Added for TimeZone

class StockUpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "StockUpdateWorker"
        const val KEY_APP_WIDGET_IDS = "app_widget_ids"

        // Notification Channel Constants
        private const val NOTIFICATION_CHANNEL_ID = "stock_widget_refresh_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Stock Widget Updates"
        private const val NOTIFICATION_CHANNEL_DESC = "Notifications for stock widget manual refresh"

        // Network Request Constants
        private const val HTTP_METHOD_POST = "POST"
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_X_CONSUMER_ID = "x-consumer-id"
        private const val APPLICATION_JSON = "application/json"
        private const val X_CONSUMER_ID_VALUE = "GPX" // Vanguard specific
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 15000

        // JSON keys
        private const val JSON_KEY_CLOSE = "close"
        private const val JSON_KEY_LAST_BAR_UPDATE_TIME = "last_bar_update_time"
        private const val JSON_KEY_QUERY = "query"
        private const val JSON_KEY_VARIABLES = "variables"
        private const val JSON_KEY_DATA = "data"
        private const val JSON_KEY_FUNDS = "funds"
        private const val JSON_KEY_PRICING_DETAILS = "pricingDetails"
        private const val JSON_KEY_NAV_PRICES = "navPrices"
        private const val JSON_KEY_ITEMS = "items"
        private const val JSON_KEY_PRICE = "price"
        private const val JSON_KEY_AS_OF_DATE = "asOfDate" // Added for GraphQL date
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationId = System.currentTimeMillis().toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = NOTIFICATION_CHANNEL_DESC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Refreshing Widget Data")
            .setContentText("Fetching latest stock prices...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setAutoCancel(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork called")
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val inputWidgetIds = inputData.getIntArray(KEY_APP_WIDGET_IDS)

        val widgetIdsToUpdate: IntArray = if (inputWidgetIds != null && inputWidgetIds.isNotEmpty()) {
            inputWidgetIds
        } else {
            val componentName = ComponentName(context, StockWidgetProvider::class.java)
            appWidgetManager.getAppWidgetIds(componentName).takeIf { it.isNotEmpty() }
                ?: return Result.success().also { Log.d(TAG, "No AppWidgetIds found.") }
        }

        Log.d(TAG, "Updating widgets: ${widgetIdsToUpdate.joinToString()}")
        widgetIdsToUpdate.forEach { showLoadingState(context, appWidgetManager, it) }

        val fetchedPrices = mutableListOf<Double>()
        val fetchedTimeData = mutableListOf<Any?>() // Can store Long? (timestamp) or String? (asOfDate)

        try {
            StockWidgetProvider.stocks.forEachIndexed { index, stock ->
                if (stock.isGraphQL) {
                    // Assuming ABN is at index 3 and is the only one providing asOfDate this way
                    val (price, asOfDate) = fetchGraphQLPrice(stock.apiUrl, stock.graphQLQuery!!, stock.graphQLVariables!!)
                    fetchedPrices.add(price)
                    if (index == 3) { // ABN stock index
                        fetchedTimeData.add(asOfDate)
                    } else {
                        fetchedTimeData.add(null) // Other GraphQL stocks might not have/need this
                    }
                } else {
                    val (price, timestamp) = fetchPrice(stock.apiUrl)
                    fetchedPrices.add(price)
                    fetchedTimeData.add(timestamp)
                }
            }

            val formattedUpdateTimes = fetchedTimeData.mapIndexed { index, timeData ->
                when (timeData) {
                    is String -> { // Handles asOfDate for ABN (index 3)
                        if (index == 3) {
                            try {
                                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val outputFormat = SimpleDateFormat("dd.MM", Locale.getDefault()) // Changed format here
                                outputFormat.timeZone = TimeZone.getDefault() // Ensure correct timezone
                                val date = inputFormat.parse(timeData)
                                if (date != null) outputFormat.format(date) else "N/A"
                            } catch (e: Exception) {
                                Log.e(TAG, "Error formatting asOfDate '$timeData': ${e.message}", e)
                                "N/A"
                            }
                        } else {
                             "N/A" // Should not happen if only ABN provides String date
                        }
                    }
                    is Long -> { // Handles Unix timestamp for TradingView APIs
                        try {
                            val date = Date(timeData * 1000L)
                            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                            sdf.timeZone = TimeZone.getDefault()
                            sdf.format(date)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error formatting timestamp $timeData: ${e.message}", e)
                            "N/A"
                        }
                    }
                    else -> "N/A" // For null or unexpected types
                }
            }

            withContext(Dispatchers.Main) {
                widgetIdsToUpdate.forEach { appWidgetId ->
                    updateAppWidget(context, appWidgetManager, appWidgetId, fetchedPrices, formattedUpdateTimes)
                }
            }
            Log.d(TAG, "Work finished. Widgets updated.")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during stock data fetching or widget update: ${e.message}", e)
            widgetIdsToUpdate.forEach { appWidgetId ->
                 try {
                    val views = RemoteViews(context.packageName, R.layout.stock_widget_layout)
                    views.setViewVisibility(R.id.loading_indicator, View.GONE)
                    views.setViewVisibility(R.id.content_container, View.VISIBLE)
                    appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                 } catch (re: Exception) {
                     Log.e(TAG, "Error resetting widget $appWidgetId to non-loading state after failure", re)
                 }
            }
            return Result.failure()
        }
    }

    private fun showLoadingState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.stock_widget_layout)
        views.setViewVisibility(R.id.loading_indicator, View.VISIBLE)
        views.setViewVisibility(R.id.content_container, View.INVISIBLE)
        try {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying loading state to widget ID $appWidgetId", e)
        }
    }

    private suspend fun fetchPrice(apiUrl: String): Pair<Double, Long?> {
        return try {
            val jsonString = withContext(Dispatchers.IO) { URL(apiUrl).readText() }
            val jsonObject = JSONObject(jsonString)
            val price = jsonObject.optDouble(JSON_KEY_CLOSE, Double.NaN)
            val timestamp = if (jsonObject.has(JSON_KEY_LAST_BAR_UPDATE_TIME)) {
                jsonObject.optLong(JSON_KEY_LAST_BAR_UPDATE_TIME, -1L)
            } else {
                -1L
            }
            Pair(price, if (timestamp == -1L) null else timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "fetchPrice Error for $apiUrl: ${e.message}", e)
            Pair(Double.NaN, null)
        }
    }

    // Changed return type to Pair<Double, String?> to include asOfDate
    private suspend fun fetchGraphQLPrice(
        apiUrl: String,
        query: String,
        variables: JSONObject
    ): Pair<Double, String?> {
        var connection: HttpURLConnection? = null
        return try {
            withContext(Dispatchers.IO) {
                val url = URL(apiUrl)
                connection = url.openConnection() as HttpURLConnection
                // ... (connection setup remains the same) ...
                connection!!.requestMethod = HTTP_METHOD_POST
                connection!!.setRequestProperty(HEADER_CONTENT_TYPE, APPLICATION_JSON)
                connection!!.setRequestProperty(HEADER_ACCEPT, APPLICATION_JSON)
                connection!!.setRequestProperty(HEADER_X_CONSUMER_ID, X_CONSUMER_ID_VALUE)
                connection!!.doOutput = true
                connection!!.connectTimeout = CONNECT_TIMEOUT_MS
                connection!!.readTimeout = READ_TIMEOUT_MS

                val payload = JSONObject().apply {
                    put(JSON_KEY_QUERY, query)
                    put(JSON_KEY_VARIABLES, variables)
                }
                
                OutputStreamWriter(connection!!.outputStream, "UTF-8").use { it.write(payload.toString()); it.flush() }

                val responseCode = connection!!.responseCode
                val streamReader = if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStreamReader(connection!!.inputStream)
                } else {
                    InputStreamReader(connection!!.errorStream ?: connection!!.inputStream)
                }

                BufferedReader(streamReader).use { reader ->
                    val responseString = reader.readText()
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val jsonResponse = JSONObject(responseString)
                        val dataObject = jsonResponse.optJSONObject(JSON_KEY_DATA)
                        val fundsArray = dataObject?.optJSONArray(JSON_KEY_FUNDS)
                        if (fundsArray != null && fundsArray.length() > 0) {
                            val firstFund = fundsArray.optJSONObject(0)
                            val pricingDetails = firstFund?.optJSONObject(JSON_KEY_PRICING_DETAILS)
                            val navPrices = pricingDetails?.optJSONObject(JSON_KEY_NAV_PRICES)
                            val itemsArray = navPrices?.optJSONArray(JSON_KEY_ITEMS)
                            if (itemsArray != null && itemsArray.length() > 0) {
                                val firstItem = itemsArray.optJSONObject(0)
                                val price = firstItem?.optDouble(JSON_KEY_PRICE, Double.NaN) ?: Double.NaN
                                val asOfDate = firstItem?.optString(JSON_KEY_AS_OF_DATE, null) // Parse asOfDate
                                return@withContext Pair(price, asOfDate)
                            }
                        }
                    } else {
                        Log.e(TAG, "GraphQL Error for $apiUrl. Response: $responseString")
                    }
                }
                Pair(Double.NaN, null) // Default return on error or missing data
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchGraphQLPrice Error for $apiUrl: ${e.message}", e)
            Pair(Double.NaN, null)
        } finally {
            connection?.disconnect()
        }
    }
}
