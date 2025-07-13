package com.example.stockswidget.data // Or your preferred package

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VusaViewModel(private val dao: VusaTransactionDao) : ViewModel() {

    // Function to get transactions for a specific portfolio
    // Consider making this a StateFlow if you want to observe it like `allTransactions` was.
    fun getTransactionsForPortfolio(portfolioName: String): Flow<List<VusaTransaction>> {
        return dao.getTransactionsForPortfolio(portfolioName)
    }

    // If you need a version that can be collected as StateFlow in Composables:
    // This is more advanced and requires a portfolioName to be passed or managed within the ViewModel.
    // For now, PortfolioDetailActivity will collect the Flow directly.

    // Function to insert a new transaction, now requires portfolioName
    fun insertTransaction(
        amount: Double,
        buyPrice: Double,
        transactionTimestamp: Long,
        currency: String,
        portfolioName: String // Added portfolioName
    ) {
        viewModelScope.launch {
            val transaction = VusaTransaction(
                amount = amount,
                buyPrice = buyPrice,
                transactionTimestamp = transactionTimestamp,
                currency = currency,
                portfolioName = portfolioName // Store the portfolioName
            )
            dao.insertTransaction(transaction)
        }
    }

    // Function to update an existing transaction
    // The VusaTransaction object already contains the portfolioName
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
