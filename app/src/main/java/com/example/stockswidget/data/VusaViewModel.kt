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
    fun insertTransaction(amount: Double, buyPrice: Double) {
        viewModelScope.launch {
            val transaction = VusaTransaction(amount = amount, buyPrice = buyPrice)
            dao.insertTransaction(transaction)
        }
    }

    // You can add methods for update, delete, getById later as needed
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
