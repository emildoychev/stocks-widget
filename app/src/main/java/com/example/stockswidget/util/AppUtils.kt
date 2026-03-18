package com.example.stockswidget.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
// import java.text.NumberFormat // No longer needed
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

// Helper function to format decimal input
fun formatDecimalInput(text: String, maxDecimalPlaces: Int = 4): String {
    val filtered = text.foldIndexed("") { _, acc, char ->
        if (char.isDigit()) acc + char
        else if ((char == '.' || char == ',') && !acc.contains('.')) acc + '.'
        else acc
    }
    val parts = filtered.split('.', limit = 2)
    val integerPart = parts[0]
    val fractionalPart = if (parts.size > 1) parts[1].take(maxDecimalPlaces) else null
    return when {
        fractionalPart != null -> if (integerPart.isEmpty()) "0.$fractionalPart" else "$integerPart.$fractionalPart"
        integerPart.isEmpty() && filtered.contains('.') -> "0."
        else -> integerPart
    }
}
