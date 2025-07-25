package com.example.stockswidget.data // Or your preferred package

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class VusaViewModel(private val dao: VusaTransactionDao) : ViewModel() {

    // Flow to observe all transactions from the database
    val allTransactions: Flow<List<VusaTransaction>> = dao.getAllTransactions()

    // Function to insert a new transaction
    fun insertTransaction(amount: Double, buyPrice: Double, transactionTimestamp: Long, currency: String) { // Added currency
        viewModelScope.launch {
            val transaction = VusaTransaction(
                amount = amount,
                buyPrice = buyPrice,
                transactionTimestamp = transactionTimestamp, // Use the provided timestamp
                currency = currency // Store the currency
            )
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
