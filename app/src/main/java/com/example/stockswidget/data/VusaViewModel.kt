package com.example.stockswidget.data // Or your preferred package

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay // For simulating network calls

class VusaViewModel(private val dao: VusaTransactionDao) : ViewModel() {

    // Data class for market quotes
    data class MarketQuote(
        val price: Double,
        val change: Double,
        val changePercent: Double
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
            try {
                // Fetch transactions for the portfolio to get unique symbols
                val transactions = getTransactionsForPortfolio(portfolioName).firstOrNull() ?: emptyList()
                val uniqueSymbols = transactions.map { it.stockSymbol }.distinct()

                val updatedMarketData = mutableMapOf<String, MarketQuote?>()
                uniqueSymbols.forEach { symbol ->
                    // Simulate fetching data for each symbol
                    // In a real app, you would make an API call here.
                    delay(200) // Simulate network delay
                    // Replace with actual API call logic.
                    // For now, let'''s generate some placeholder data or handle potential errors.
                    if (symbol.startsWith("FAIL")) { // Simulate a failed fetch for a symbol
                        updatedMarketData[symbol] = null // Or handle error specifically for this symbol
                    } else {
                        updatedMarketData[symbol] = MarketQuote(
                            price = (100..500).random().toDouble(),
                            change = (-5..5).random().toDouble(),
                            changePercent = (-2..2).random().toDouble() / 100
                        )
                    }
                }
                _marketData.value = updatedMarketData
            } catch (e: Exception) {
                // In a real app, handle exceptions more gracefully
                _error.value = "Failed to load market data: ${e.message}"
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
