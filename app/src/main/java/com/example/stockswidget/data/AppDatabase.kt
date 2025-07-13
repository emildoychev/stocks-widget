package com.example.stockswidget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [VusaTransaction::class], version = 3, exportSchema = false) // Increment version to 3
abstract class AppDatabase : RoomDatabase() {

    abstract fun vusaTransactionDao(): VusaTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from 1 to 2: Add currency column with a default value
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vusa_transactions ADD COLUMN currency TEXT NOT NULL DEFAULT '€'")
            }
        }

        // Migration from 2 to 3: Add portfolioName column with a default value
        // For existing rows, you might want a more sensible default or allow NULL if appropriate,
        // but for simplicity, we'll use an empty string as a non-null default.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vusa_transactions ADD COLUMN portfolioName TEXT NOT NULL DEFAULT 'Default Portfolio'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stocks_widget_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // Add all migrations
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
