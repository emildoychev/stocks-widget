package com.example.stockswidget

import android.app.Application
import com.example.stockswidget.data.AppDatabase
import com.example.stockswidget.data.VusaTransactionDao

class StocksWidgetApplication : Application() {

    // Lazy-initialized database instance
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    // Lazy-initialized DAO instance from the database
    val vusaTransactionDao: VusaTransactionDao by lazy { database.vusaTransactionDao() }

    override fun onCreate() {
        super.onCreate()
        // You can perform other initializations here if needed
    }
}
