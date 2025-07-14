package com.example.stockswidget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [VusaTransaction::class], version = 4, exportSchema = false) // Incremented version to 4
abstract class AppDatabase : RoomDatabase() {

    abstract fun vusaTransactionDao(): VusaTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from 1 to 2: Add currency column
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vusa_transactions ADD COLUMN currency TEXT NOT NULL DEFAULT '€'")
            }
        }

        // Migration from 2 to 3: Add portfolioName column
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vusa_transactions ADD COLUMN portfolioName TEXT NOT NULL DEFAULT 'Default Portfolio'")
            }
        }

        // Migration from 3 to 4: Add stockSymbol column
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Assuming stockSymbol cannot be null. Provide a sensible default if necessary,
                // or make it nullable in your VusaTransaction entity if appropriate.
                // For simplicity, using an empty string as a non-null default.
                db.execSQL("ALTER TABLE vusa_transactions ADD COLUMN stockSymbol TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stocks_widget_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4) // Added MIGRATION_3_4
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
