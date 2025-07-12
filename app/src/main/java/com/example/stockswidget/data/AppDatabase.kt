package com.example.stockswidget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [VusaTransaction::class], version = 2, exportSchema = false) // Increment version
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stocks_widget_database"
                )
                .addMigrations(MIGRATION_1_2) // Add the migration
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
