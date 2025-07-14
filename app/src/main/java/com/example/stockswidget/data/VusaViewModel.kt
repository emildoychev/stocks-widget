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
    fun getTransactionsForPortfolio(portfolioName: String): Flow<List<VusaTransaction>> {
        return dao.getTransactionsForPortfolio(portfolioName)
    }

    /**
     * Inserts a VusaTransaction into the database.
     * The VusaTransaction object should be fully formed before calling this method.
     */
    fun insertTransaction(transaction: VusaTransaction) { // Changed signature
        viewModelScope.launch {
            // The transaction object is now passed directly
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
