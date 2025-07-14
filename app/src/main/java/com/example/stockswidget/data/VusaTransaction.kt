package com.example.stockswidget.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vusa_transactions")
data class VusaTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val stockSymbol: String, // Added stock symbol
    val amount: Double,
    val buyPrice: Double,
    val transactionTimestamp: Long = System.currentTimeMillis(),
    val portfolioName: String,
    val marketPriceAtPurchase: Double? = null // Added this line
)
