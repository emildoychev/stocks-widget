package com.example.stockswidget.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VusaTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: VusaTransaction)

    @Update
    suspend fun updateTransaction(transaction: VusaTransaction)

    // Modified to fetch transactions for a specific portfolio
    @Query("SELECT * FROM vusa_transactions WHERE portfolioName = :portfolioName ORDER BY transactionTimestamp DESC")
    fun getTransactionsForPortfolio(portfolioName: String): Flow<List<VusaTransaction>>

    // Kept for potential use if you need all transactions across all portfolios, otherwise can be removed.
    @Query("SELECT * FROM vusa_transactions ORDER BY transactionTimestamp DESC")
    fun getAllTransactions_UNFILTERED(): Flow<List<VusaTransaction>> // Renamed for clarity

    @Query("SELECT * FROM vusa_transactions WHERE id = :id")
    suspend fun getTransactionById(id: Int): VusaTransaction?

    @Query("DELETE FROM vusa_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)
}
