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

    @Query("SELECT * FROM vusa_transactions ORDER BY transactionTimestamp DESC")
    fun getAllTransactions(): Flow<List<VusaTransaction>>

    @Query("SELECT * FROM vusa_transactions WHERE id = :id")
    suspend fun getTransactionById(id: Int): VusaTransaction?

    @Query("DELETE FROM vusa_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)
}
