package com.example.stockswidget.data // Or your preferred package

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.stockswidget.util.fetchSearchResultsFromApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class VusaViewModel(private val dao: VusaTransactionDao) : ViewModel() {

    // Data class for market quotes
    data class MarketQuote(
        val price: Double,
        val change: Double,
        val changePercent: Double // As a decimal, e.g., 0.01 for 1%
    )

    // StateFlow for market data
    private val _marketData = MutableStateFlow<Map<String, MarketQuote?>>(emptyMap())
    val marketData: StateFlow<Map<String, MarketQuote?>> = _marketData.asStateFlow()

    // StateFlow for loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // StateFlow for error messages
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Function to get transactions for a specific portfolio
    fun getTransactionsForPortfolio(portfolioName: String): Flow<List<VusaTransaction>> {
        return dao.getTransactionsForPortfolio(portfolioName)
    }

    /**
     * Inserts a VusaTransaction into the database.
     * The VusaTransaction object should be fully formed before calling this method.
     */
    fun addVusaTransaction(transaction: VusaTransaction) {
        viewModelScope.launch {
            dao.insertTransaction(transaction)
        }
    }

    // Function to update an existing transaction
    fun updateTransaction(transaction: VusaTransaction) {
        viewModelScope.launch {
            dao.updateTransaction(transaction)
        }
    }

    // Function to delete a transaction by its ID
    fun deleteTransactionById(id: Int) {
        viewModelScope.launch {
            dao.deleteTransactionById(id)
        }
    }

    // Function to refresh market data for a portfolio
    fun refreshMarketDataForPortfolio(portfolioName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val updatedMarketData = mutableMapOf<String, MarketQuote?>()

            try {
                val transactions = getTransactionsForPortfolio(portfolioName).firstOrNull() ?: emptyList()
                val uniqueSymbols = transactions.map { it.stockSymbol }.distinct()

                if (uniqueSymbols.isNotEmpty()) {
                    val encodedSymbols = uniqueSymbols.joinToString("%7C") { URLEncoder.encode(it, "UTF-8") }
                    val apiUrl = "https://quote.cnbc.com/quote-html-webservice/quote.htm?&symbols=${encodedSymbols}&output=json"
                    Log.d("VusaViewModel", "Fetching market data from URL: $apiUrl")

                    val jsonResponseString = fetchSearchResultsFromApi(apiUrl)

                    if (jsonResponseString != null) {
                        val responseJson = JSONObject(jsonResponseString)
                        val quoteResponse = responseJson.optJSONObject("QuoteResponse")
                        val quotesArray = quoteResponse?.optJSONArray("Quote") ?: JSONArray()

                        for (i in 0 until quotesArray.length()) {
                            val stockObject = quotesArray.getJSONObject(i)
                            val symbol = stockObject.optString("symbol")

                            if (symbol.isNotEmpty()) {
                                val lastPriceStr = stockObject.optString("last", "0.0")
                                val changeStr = stockObject.optString("change", "0.0")
                                val changePercentStr = stockObject.optString("change_pct", "0.0")

                                val price = lastPriceStr.toDoubleOrNull() ?: 0.0
                                val change = changeStr.toDoubleOrNull() ?: 0.0
                                val changePercentValue = changePercentStr.removeSuffix("%").toDoubleOrNull() ?: 0.0
                                // API gives change_pct sometimes as "0.55" for +0.55%
                                // If it does not contain '%', assume it is already a percentage value like 0.55 meaning 0.55%
                                // If it was "0.55%", removeSuffix makes it "0.55".
                                // We need to convert this to decimal: 0.55 -> 0.0055
                                val changePercent = if (changePercentStr.contains("%")) changePercentValue / 100.0 else changePercentValue / 100.0 // Assuming it should always be divided by 100 if it's like "0.55" or "0.55%"

                                updatedMarketData[symbol] = MarketQuote(
                                    price = price,
                                    change = change,
                                    changePercent = changePercent
                                )
                            }
                        }
                        // Ensure all unique symbols have an entry, even if null
                        uniqueSymbols.forEach { sym ->
                            if (!updatedMarketData.containsKey(sym)) {
                                updatedMarketData[sym] = null
                                Log.w("VusaViewModel", "No market data found for symbol: $sym")
                            }
                        }
                    } else {
                        _error.value = "Failed to fetch market data (null response)"
                        Log.e("VusaViewModel", "API response was null for URL: $apiUrl")
                         uniqueSymbols.forEach { sym -> updatedMarketData[sym] = null }
                    }
                }
                _marketData.value = updatedMarketData
            } catch (e: Exception) {
                _error.value = "Error refreshing market data: ${e.message}"
                Log.e("VusaViewModel", "Exception in refreshMarketDataForPortfolio", e)
                // Ensure all symbols are at least nulled out in case of partial success before exception
                val transactions = getTransactionsForPortfolio(portfolioName).firstOrNull() ?: emptyList()
                transactions.map { it.stockSymbol }.distinct().forEach { sym ->
                     if (!updatedMarketData.containsKey(sym)) {
                         updatedMarketData[sym] = null
                     }
                }
                 _marketData.value = updatedMarketData // Propagate partial or all-null data
            } finally {
                _isLoading.value = false
            }
        }
    }
}

// Factory to create VusaViewModel with VusaTransactionDao dependency
class VusaViewModelFactory(private val dao: VusaTransactionDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VusaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VusaViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
