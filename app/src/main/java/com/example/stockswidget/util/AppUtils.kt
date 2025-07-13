package com.example.stockswidget.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

// Helper function to format currency (dynamic decimals)
fun formatDynamicDecimal(value: Double): String {
    if (value.isNaN() || value.isInfinite()) {
        return value.toString() // Or a placeholder like "N/A"
    }
    // Check if it's a whole number
    if (value % 1.0 == 0.0) {
        return value.toLong().toString()
    }
    // For numbers with decimals, use BigDecimal to strip trailing zeros accurately
    return BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
}

// Helper function to format currency based on the symbol (dynamic decimals)
fun formatCurrency(amount: Double, currencySymbol: String): String {
    val formattedNumber = formatDynamicDecimal(amount)
    return when (currencySymbol) {
        "€" -> "€ $formattedNumber"
        "$" -> "$ $formattedNumber"
        "Other" -> formattedNumber // No symbol for "Other"
        else -> formattedNumber // Default, no symbol
    }
}

// Helper function to format currency with fixed two decimal places
fun formatCurrencyFixed(amount: Double, currencySymbol: String): String {
    val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val formattedNumber = numberFormat.format(amount)
    return when (currencySymbol) {
        "€" -> "€ $formattedNumber"
        "$" -> "$ $formattedNumber"
        "Other" -> formattedNumber // No symbol for "Other"
        else -> formattedNumber // Default, no symbol
    }
}

// Function to fetch search results using HttpURLConnection
suspend fun fetchSearchResultsFromApi(urlString: String): Map<String, Any?> {
    return withContext(Dispatchers.IO) {
        val url = try {
            URL(urlString)
        } catch (e: java.net.MalformedURLException) {
            return@withContext mapOf("error" to "Invalid URL: ${e.message}")
        }
        var connection: HttpURLConnection? = null
        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000 // 15 seconds
            connection.readTimeout = 15000  // 15 seconds
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                mapOf("content" to response.toString())
            } else {
                mapOf("error" to "HTTP error code: $responseCode - ${connection.responseMessage}")
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Unknown network error during search API call"))
        } finally {
            connection?.disconnect()
        }
    }
}
