package com.example.stockswidget.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vusa_transactions")
data class VusaTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Double,
    val buyPrice: Double,
    val transactionTimestamp: Long = System.currentTimeMillis(),
    val currency: String // Added currency field
)
