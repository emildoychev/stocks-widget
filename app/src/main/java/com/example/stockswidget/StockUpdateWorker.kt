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

class StockUpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "StockUpdateWorker"
        const val KEY_APP_WIDGET_IDS = "app_widget_ids"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "stock_widget_refresh_channel"
        val notificationId = System.currentTimeMillis().toInt() // Unique ID for the notification

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Stock Widget Updates", // User-visible name for the channel
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for stock widget manual refresh"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
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

        // Show loading state for all affected widgets first
        widgetIdsToUpdate.forEach { appWidgetId ->
            showLoadingState(context, appWidgetManager, appWidgetId)
        }

        // Fetch prices (this part involves network requests)
        val fetchedPrices = mutableListOf<Double>()
        try {
            for (stock in StockWidgetProvider.stocks) { // Accessing companion object\'s list
                val price = if (stock.isGraphQL) {
                    fetchGraphQLPrice(stock.apiUrl, stock.graphQLQuery!!, stock.graphQLVariables!!)
                } else {
                    fetchPrice(stock.apiUrl)
                }
                fetchedPrices.add(price)
            }
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            // Update each widget
            withContext(Dispatchers.Main) { // UI updates should be on the main thread
                widgetIdsToUpdate.forEach { appWidgetId ->
                    Log.d(TAG, "Applying final update to widget ID $appWidgetId")
                    updateAppWidget(context, appWidgetManager, appWidgetId, fetchedPrices, currentTime)
                }
            }
            Log.d(TAG, "Work finished. Widgets updated.")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during stock data fetching or widget update: ${e.message}", e)
            // Optionally, update widgets to an error state here
            return Result.failure()
        }
    }

    private fun showLoadingState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        Log.d(TAG, "Showing loading state for widget ID: $appWidgetId")
        val views = RemoteViews(context.packageName, R.layout.stock_widget_layout)
        
        // Make loading indicator visible
        views.setViewVisibility(R.id.loading_indicator, View.VISIBLE)
        
        // Hide main content container
        views.setViewVisibility(R.id.content_container, View.INVISIBLE)

        // Explicitly hide all individual stock views and dividers if they are outside content_container
        // or if content_container visibility doesn\'t hide them effectively.
        // This ensures a clean loading state.
        StockWidgetProvider.stocks.forEach { stock ->
             views.setViewVisibility(stock.labelViewId, View.GONE)
             views.setViewVisibility(stock.lastUpdatedViewId, View.GONE)
             views.setViewVisibility(stock.profitLossViewId, View.GONE)
             views.setViewVisibility(stock.buyPriceViewId, View.GONE)
             views.setViewVisibility(stock.stockPriceViewId, View.GONE)
        }
        val dividerIds = listOf(R.id.divider_line, R.id.divider_line_2, R.id.divider_line_3, R.id.divider_line_4, R.id.divider_line_5)
        dividerIds.forEach { views.setViewVisibility(it, View.GONE) }

        try {
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying loading state to widget ID $appWidgetId", e)
        }
    }

    private suspend fun fetchPrice(apiUrl: String): Double {
        Log.d(TAG, "Fetching price for URL: $apiUrl")
        return try {
            val jsonString = withContext(Dispatchers.IO) { URL(apiUrl).readText() }
            val jsonObject = JSONObject(jsonString)
            jsonObject.getDouble("close")
        } catch (e: Exception) {
            Log.e(TAG, "fetchPrice Error for $apiUrl: ${e.message}", e)
            Double.NaN // Return NaN on error
        }
    }

    private suspend fun fetchGraphQLPrice(
        apiUrl: String,
        query: String,
        variables: JSONObject
    ): Double {
        var connection: HttpURLConnection? = null
        Log.d(TAG, "Fetching GraphQL price for URL: $apiUrl")
        return try {
            withContext(Dispatchers.IO) {
                val url = URL(apiUrl)
                connection = url.openConnection() as HttpURLConnection
                connection!!.requestMethod = "POST"
                connection!!.setRequestProperty("Content-Type", "application/json")
                connection!!.setRequestProperty("Accept", "application/json")
                connection!!.setRequestProperty("x-consumer-id", "GPX")
                connection!!.doOutput = true
                connection!!.connectTimeout = 15000
                connection!!.readTimeout = 15000

                val payload = JSONObject()
                payload.put("query", query)
                payload.put("variables", variables)
                
                val escapedPayload = payload.toString().replace("'","'''")
                val curlCommand = """
                    curl -X ${connection!!.requestMethod} "$apiUrl" \
                    -H "Content-Type: ${connection!!.getRequestProperty("Content-Type")}" \
                    -H "Accept: ${connection!!.getRequestProperty("Accept")}" \
                    -H "x-consumer-id: ${connection!!.getRequestProperty("x-consumer-id")}" \
                    -d '$escapedPayload'
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
                        val dataObject = jsonResponse.optJSONObject("data")
                        val fundsArray = dataObject?.optJSONArray("funds")
                        if (fundsArray != null && fundsArray.length() > 0) {
                            val firstFund = fundsArray.optJSONObject(0)
                            val pricingDetails = firstFund?.optJSONObject("pricingDetails")
                            val navPrices = pricingDetails?.optJSONObject("navPrices")
                            val itemsArray = navPrices?.optJSONArray("items")
                            if (itemsArray != null && itemsArray.length() > 0) {
                                val firstItem = itemsArray.optJSONObject(0)
                                return@withContext firstItem?.optDouble("price", Double.NaN) ?: Double.NaN
                            }
                        }
                    } else {
                        Log.e(TAG, "GraphQL Error for $apiUrl. Response: $responseString")
                    }
                }
                Double.NaN // Return NaN if parsing fails or not HTTP_OK
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchGraphQLPrice Error for $apiUrl: ${e.message}", e)
            Double.NaN // Return NaN on error
        } finally {
            connection?.disconnect()
        }
    }
}
