package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class SnapSpendConverters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.EXPENSE)

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus): String = value.name

    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus =
        runCatching { TransactionStatus.valueOf(value) }.getOrDefault(TransactionStatus.SUCCESS)

    @TypeConverter
    fun fromTransactionSource(value: TransactionSource): String = value.name

    @TypeConverter
    fun toTransactionSource(value: String): TransactionSource =
        runCatching { TransactionSource.valueOf(value) }.getOrDefault(TransactionSource.MANUAL)

    @TypeConverter
    fun fromCategoryType(value: CategoryType): String = value.name

    @TypeConverter
    fun toCategoryType(value: String): CategoryType =
        runCatching { CategoryType.valueOf(value) }.getOrDefault(CategoryType.EXPENSE)

    @TypeConverter
    fun fromRepeatingState(value: RepeatingState): String = value.name

    @TypeConverter
    fun toRepeatingState(value: String): RepeatingState =
        runCatching { RepeatingState.valueOf(value) }.getOrDefault(RepeatingState.SUGGESTED)
}

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        MerchantMemoryEntity::class,
        BudgetEntity::class,
        RepeatingPaymentEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(SnapSpendConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SnapSpendDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN deletedAtMillis INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN paymentMode TEXT NOT NULL DEFAULT 'ONLINE'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN isAtmWithdrawal INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun ensureV4ColumnsIfAlreadyAtV4(context: Context, dbName: String) {
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) return
            runCatching {
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                ).use { rawDb ->
                    if (rawDb.version == 4) {
                        val existingCols = mutableSetOf<String>()
                        rawDb.rawQuery("PRAGMA table_info(transactions)", null).use { cursor ->
                            val nameIdx = cursor.getColumnIndex("name")
                            while (cursor.moveToNext()) {
                                if (nameIdx >= 0) existingCols.add(cursor.getString(nameIdx))
                            }
                        }
                        if (existingCols.isNotEmpty()) {
                            if ("paymentMode" !in existingCols) {
                                rawDb.execSQL("ALTER TABLE transactions ADD COLUMN paymentMode TEXT NOT NULL DEFAULT 'ONLINE'")
                            }
                            if ("isAtmWithdrawal" !in existingCols) {
                                rawDb.execSQL("ALTER TABLE transactions ADD COLUMN isAtmWithdrawal INTEGER NOT NULL DEFAULT 0")
                            }
                        }
                    }
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    ensureV4ColumnsIfAlreadyAtV4(context.applicationContext, "snapspend_local.db")
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "snapspend_local.db"
                    )
                        .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                        .build()
                        .also { INSTANCE = it }
                }
            }
        }
    }
}

typealias SnapSpendDatabase = AppDatabase
