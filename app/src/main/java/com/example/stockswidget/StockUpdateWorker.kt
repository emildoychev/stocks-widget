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
        private const val JSON_KEY_LAST_BAR_UPDATE_TIME = "last_bar_update_time" // Added
        private const val JSON_KEY_QUERY = "query"
        private const val JSON_KEY_VARIABLES = "variables"
        private const val JSON_KEY_DATA = "data"
        private const val JSON_KEY_FUNDS = "funds"
        private const val JSON_KEY_PRICING_DETAILS = "pricingDetails"
        private const val JSON_KEY_NAV_PRICES = "navPrices"
        private const val JSON_KEY_ITEMS = "items"
        private const val JSON_KEY_PRICE = "price"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationId = System.currentTimeMillis().toInt() // Unique ID for the notification

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
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this drawable exists
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
            Log.d(TAG, "Updating specific widget IDs: ${inputWidgetIds.joinToString()}")
            inputWidgetIds
        } else {
            val componentName = ComponentName(context, StockWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (allWidgetIds.isEmpty()) {
                Log.d(TAG, "No AppWidgetIds found for StockWidgetProvider.")
                return Result.success() // Nothing to do
            }
            Log.d(TAG, "Updating all widget IDs for StockWidgetProvider: ${allWidgetIds.joinToString()}")
            allWidgetIds
        }

        if (widgetIdsToUpdate.isEmpty()) {
            Log.w(TAG, "No AppWidgetIds found to update.")
            return Result.success() // Nothing to do if no widgets
        }
        
        Log.d(TAG, "Updating widgets: ${widgetIdsToUpdate.joinToString()}")

        widgetIdsToUpdate.forEach { appWidgetId ->
            showLoadingState(context, appWidgetManager, appWidgetId)
        }

        val fetchedPrices = mutableListOf<Double>()
        val fetchedUpdateTimestampsAsLongs = mutableListOf<Long?>() // To store raw timestamps

        try {
            for (stock in StockWidgetProvider.stocks) {
                if (stock.isGraphQL) {
                    val price = fetchGraphQLPrice(stock.apiUrl, stock.graphQLQuery!!, stock.graphQLVariables!!)
                    fetchedPrices.add(price)
                    fetchedUpdateTimestampsAsLongs.add(null) // GraphQL doesn\'t provide this specific timestamp
                } else {
                    val (price, timestamp) = fetchPrice(stock.apiUrl)
                    fetchedPrices.add(price)
                    fetchedUpdateTimestampsAsLongs.add(timestamp)
                }
            }

            val formattedUpdateTimes = fetchedUpdateTimestampsAsLongs.map { timestamp ->
                if (timestamp != null) {
                    try {
                        val date = Date(timestamp * 1000L) // Convert Unix seconds to milliseconds
                        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault()) // Changed to AM/PM format, no leading zero for hour
                        sdf.timeZone = TimeZone.getDefault() // Use device\'s default timezone
                        sdf.format(date)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error formatting timestamp $timestamp: ${e.message}", e)
                        "N/A"
                    }
                } else {
                    "N/A" // For GraphQL or missing timestamps
                }
            }

            withContext(Dispatchers.Main) {
                widgetIdsToUpdate.forEach { appWidgetId ->
                    Log.d(TAG, "Applying final update to widget ID $appWidgetId")
                    // Pass the list of formatted times to updateAppWidget
                    updateAppWidget(context, appWidgetManager, appWidgetId, fetchedPrices, formattedUpdateTimes)
                }
            }
            Log.d(TAG, "Work finished. Widgets updated.")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during stock data fetching or widget update: ${e.message}", e)
            // Attempt to clear loading state on failure for affected widgets
            widgetIdsToUpdate.forEach { appWidgetId ->
                 try {
                    val views = RemoteViews(context.packageName, R.layout.stock_widget_layout)
                    views.setViewVisibility(R.id.loading_indicator, View.GONE)
                    views.setViewVisibility(R.id.content_container, View.VISIBLE)
                    // You might want to set text to "Error" or "N/A" here for all fields
                    appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views) // Use partiallyUpdate to avoid full re-layout if not needed
                 } catch (re: Exception) {
                     Log.e(TAG, "Error resetting widget $appWidgetId to non-loading state after failure", re)
                 }
            }
            return Result.failure()
        }
    }

    private fun showLoadingState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        Log.d(TAG, "Showing loading state for widget ID: $appWidgetId")
        val views = RemoteViews(context.packageName, R.layout.stock_widget_layout)
        
        views.setViewVisibility(R.id.loading_indicator, View.VISIBLE)
        views.setViewVisibility(R.id.content_container, View.INVISIBLE)

        try {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying loading state to widget ID $appWidgetId", e)
        }
    }

    // Changed return type to Pair<Double, Long?>
    private suspend fun fetchPrice(apiUrl: String): Pair<Double, Long?> {
        Log.d(TAG, "Fetching data for URL: $apiUrl")
        return try {
            val jsonString = withContext(Dispatchers.IO) { URL(apiUrl).readText() }
            val jsonObject = JSONObject(jsonString)
            val price = jsonObject.optDouble(JSON_KEY_CLOSE, Double.NaN)
            val timestamp = if (jsonObject.has(JSON_KEY_LAST_BAR_UPDATE_TIME)) {
                jsonObject.optLong(JSON_KEY_LAST_BAR_UPDATE_TIME, -1L) // Get as Long
            } else {
                -1L // Indicate missing
            }
            Log.d(TAG, "Fetched price: $price, timestamp: $timestamp for $apiUrl")
            Pair(price, if(timestamp == -1L) null else timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "fetchPrice Error for $apiUrl: ${e.message}", e)
            Pair(Double.NaN, null) // Return NaN for price and null for timestamp on error
        }
    }

    private suspend fun fetchGraphQLPrice(
        apiUrl: String,
        query: String,
        variables: JSONObject
    ): Double { // GraphQL price fetching remains the same, no timestamp from this source
        var connection: HttpURLConnection? = null
        Log.d(TAG, "Fetching GraphQL price for URL: $apiUrl")
        return try {
            withContext(Dispatchers.IO) {
                val url = URL(apiUrl)
                connection = url.openConnection() as HttpURLConnection
                connection!!.requestMethod = HTTP_METHOD_POST
                connection!!.setRequestProperty(HEADER_CONTENT_TYPE, APPLICATION_JSON)
                connection!!.setRequestProperty(HEADER_ACCEPT, APPLICATION_JSON)
                connection!!.setRequestProperty(HEADER_X_CONSUMER_ID, X_CONSUMER_ID_VALUE)
                connection!!.doOutput = true
                connection!!.connectTimeout = CONNECT_TIMEOUT_MS
                connection!!.readTimeout = READ_TIMEOUT_MS

                val payload = JSONObject()
                payload.put(JSON_KEY_QUERY, query)
                payload.put(JSON_KEY_VARIABLES, variables)
                
                val escapedPayload = payload.toString().replace("\'","\\\'\\\'\\\'")
                val curlCommand = """
                    curl -X ${connection!!.requestMethod} "$apiUrl" \
                    -H "$HEADER_CONTENT_TYPE: ${connection!!.getRequestProperty(HEADER_CONTENT_TYPE)}" \
                    -H "$HEADER_ACCEPT: ${connection!!.getRequestProperty(HEADER_ACCEPT)}" \
                    -H "$HEADER_X_CONSUMER_ID: ${connection!!.getRequestProperty(HEADER_X_CONSUMER_ID)}" \
                    -d \'$escapedPayload\'
                """.trimIndent()
                Log.d(TAG, "Equivalent cURL command (from worker):\n$curlCommand")

                Log.d(TAG, "GraphQL Payload for $apiUrl: ${payload.toString()}")
                OutputStreamWriter(connection!!.outputStream, "UTF-8").use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = connection!!.responseCode
                Log.d(TAG, "GraphQL Response Code for $apiUrl: $responseCode")

                val streamReader = if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStreamReader(connection!!.inputStream)
                } else {
                    InputStreamReader(connection!!.errorStream ?: connection!!.inputStream)
                }

                BufferedReader(streamReader).use { reader ->
                    val responseString = reader.readText()
                    Log.d(TAG, "GraphQL Raw Response for $apiUrl: $responseString")
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
                                return@withContext firstItem?.optDouble(JSON_KEY_PRICE, Double.NaN) ?: Double.NaN
                            }
                        }
                    } else {
                        Log.e(TAG, "GraphQL Error for $apiUrl. Response: $responseString")
                    }
                }
                Double.NaN
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchGraphQLPrice Error for $apiUrl: ${e.message}", e)
            Double.NaN
        } finally {
            connection?.disconnect()
        }
    }
}
