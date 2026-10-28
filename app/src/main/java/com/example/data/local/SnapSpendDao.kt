package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction as RoomTransaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface TransactionDao {
    // --- Basic Fetch Queries ---
    @Query("SELECT * FROM transactions WHERE deletedAtMillis IS NULL ORDER BY timestamp DESC, id DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deletedAtMillis IS NULL ORDER BY timestamp DESC, id DESC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    suspend fun getAllTransactionsIncludingTrashOnce(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAtMillis IS NULL LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE duplicateKey = :duplicateKey AND deletedAtMillis IS NULL LIMIT 1")
    suspend fun findByDuplicateKey(duplicateKey: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE referenceNumber IS NOT NULL AND LOWER(TRIM(referenceNumber)) = LOWER(TRIM(:refNumber)) AND deletedAtMillis IS NULL LIMIT 1")
    suspend fun findByReferenceNumber(refNumber: String): TransactionEntity?

    // --- Date-Range Queries ---
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startDate AND :endDate AND deletedAtMillis IS NULL ORDER BY timestamp DESC, id DESC")
    fun observeTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startDate AND :endDate AND deletedAtMillis IS NULL ORDER BY timestamp DESC, id DESC")
    suspend fun getTransactionsByDateRange(startDate: Long, endDate: Long): List<TransactionEntity>

    // --- Multi-Filter + Date-Range Queries ---
    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAtMillis IS NULL
          AND (:searchQuery = '' 
               OR LOWER(merchant) LIKE '%' || LOWER(:searchQuery) || '%'
               OR LOWER(category) LIKE '%' || LOWER(:searchQuery) || '%'
               OR LOWER(note) LIKE '%' || LOWER(:searchQuery) || '%'
               OR LOWER(paymentApp) LIKE '%' || LOWER(:searchQuery) || '%'
               OR (referenceNumber IS NOT NULL AND LOWER(referenceNumber) LIKE '%' || LOWER(:searchQuery) || '%'))
          AND (:type IS NULL OR type = :type)
          AND (:category IS NULL OR LOWER(category) = LOWER(:category))
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:status IS NULL OR status = :status)
          AND (:paymentApp IS NULL OR LOWER(paymentApp) = LOWER(:paymentApp))
          AND (:startDate IS NULL OR timestamp >= :startDate)
          AND (:endDate IS NULL OR timestamp <= :endDate)
          AND (:minAmountInPaise IS NULL OR amountPaise >= :minAmountInPaise)
          AND (:maxAmountInPaise IS NULL OR amountPaise <= :maxAmountInPaise)
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun observeFilteredTransactions(
        searchQuery: String = "",
        type: TransactionType? = null,
        category: String? = null,
        categoryId: Long? = null,
        status: TransactionStatus? = null,
        paymentApp: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        minAmountInPaise: Long? = null,
        maxAmountInPaise: Long? = null
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAtMillis IS NULL
          AND (:searchQuery = '' 
               OR LOWER(merchant) LIKE '%' || LOWER(:searchQuery) || '%'
               OR LOWER(category) LIKE '%' || LOWER(:searchQuery) || '%'
               OR LOWER(note) LIKE '%' || LOWER(:searchQuery) || '%'
               OR LOWER(paymentApp) LIKE '%' || LOWER(:searchQuery) || '%'
               OR (referenceNumber IS NOT NULL AND LOWER(referenceNumber) LIKE '%' || LOWER(:searchQuery) || '%'))
          AND (:type IS NULL OR type = :type)
          AND (:category IS NULL OR LOWER(category) = LOWER(:category))
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:status IS NULL OR status = :status)
          AND (:paymentApp IS NULL OR LOWER(paymentApp) = LOWER(:paymentApp))
          AND (:startDate IS NULL OR timestamp >= :startDate)
          AND (:endDate IS NULL OR timestamp <= :endDate)
          AND (:minAmountInPaise IS NULL OR amountPaise >= :minAmountInPaise)
          AND (:maxAmountInPaise IS NULL OR amountPaise <= :maxAmountInPaise)
        ORDER BY timestamp DESC, id DESC
        """
    )
    suspend fun getFilteredTransactions(
        searchQuery: String = "",
        type: TransactionType? = null,
        category: String? = null,
        categoryId: Long? = null,
        status: TransactionStatus? = null,
        paymentApp: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        minAmountInPaise: Long? = null,
        maxAmountInPaise: Long? = null
    ): List<TransactionEntity>

    // --- UI Presentation Queries (converting Long paise to UI Rupee formatting) ---
    fun observeAllTransactionsUi(): Flow<List<TransactionUiPresentation>> =
        observeAllTransactions().map { list -> list.map { it.toUiPresentation() } }

    fun observeTransactionsByDateRangeUi(startDate: Long, endDate: Long): Flow<List<TransactionUiPresentation>> =
        observeTransactionsByDateRange(startDate, endDate).map { list -> list.map { it.toUiPresentation() } }

    fun observeFilteredTransactionsUi(
        searchQuery: String = "",
        type: TransactionType? = null,
        category: String? = null,
        categoryId: Long? = null,
        status: TransactionStatus? = null,
        paymentApp: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        minAmountInPaise: Long? = null,
        maxAmountInPaise: Long? = null
    ): Flow<List<TransactionUiPresentation>> =
        observeFilteredTransactions(
            searchQuery = searchQuery,
            type = type,
            category = category,
            categoryId = categoryId,
            status = status,
            paymentApp = paymentApp,
            startDate = startDate,
            endDate = endDate,
            minAmountInPaise = minAmountInPaise,
            maxAmountInPaise = maxAmountInPaise
        ).map { list -> list.map { it.toUiPresentation() } }

    suspend fun getFilteredTransactionsUi(
        searchQuery: String = "",
        type: TransactionType? = null,
        category: String? = null,
        categoryId: Long? = null,
        status: TransactionStatus? = null,
        paymentApp: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        minAmountInPaise: Long? = null,
        maxAmountInPaise: Long? = null
    ): List<TransactionUiPresentation> =
        getFilteredTransactions(
            searchQuery = searchQuery,
            type = type,
            category = category,
            categoryId = categoryId,
            status = status,
            paymentApp = paymentApp,
            startDate = startDate,
            endDate = endDate,
            minAmountInPaise = minAmountInPaise,
            maxAmountInPaise = maxAmountInPaise
        ).map { it.toUiPresentation() }

    // --- Insert Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    // --- Update & Pending-to-Success Update Operations ---
    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    /**
     * Specifically updates a PENDING transaction to SUCCESS (or final status) by ID.
     */
    @Query(
        """
        UPDATE transactions 
        SET status = :newStatus,
            timestamp = :updatedTimestamp,
            amountPaise = :amountInPaise,
            paymentApp = :paymentApp,
            referenceNumber = COALESCE(:referenceNumber, referenceNumber),
            note = CASE WHEN :note = '' THEN note ELSE :note END
        WHERE id = :id AND status = 'PENDING' AND deletedAtMillis IS NULL
        """
    )
    suspend fun updatePendingTransactionToSuccess(
        id: Long,
        newStatus: TransactionStatus = TransactionStatus.SUCCESS,
        updatedTimestamp: Long,
        amountInPaise: Long,
        paymentApp: String,
        referenceNumber: String? = null,
        note: String = ""
    ): Int

    /**
     * Updates a PENDING transaction matching the reference number to SUCCESS in place.
     */
    @Query(
        """
        UPDATE transactions
        SET status = :newStatus,
            timestamp = :updatedTimestamp
        WHERE referenceNumber IS NOT NULL
          AND LOWER(TRIM(referenceNumber)) = LOWER(TRIM(:referenceNumber))
          AND status = 'PENDING'
          AND deletedAtMillis IS NULL
        """
    )
    suspend fun updatePendingToSuccessByReferenceNumber(
        referenceNumber: String,
        newStatus: TransactionStatus = TransactionStatus.SUCCESS,
        updatedTimestamp: Long = System.currentTimeMillis()
    ): Int

    /**
     * Updates a PENDING transaction matching the duplicateKey to SUCCESS in place.
     */
    @Query(
        """
        UPDATE transactions
        SET status = :newStatus,
            timestamp = :updatedTimestamp
        WHERE duplicateKey = :duplicateKey
          AND status = 'PENDING'
          AND deletedAtMillis IS NULL
        """
    )
    suspend fun updatePendingToSuccessByDuplicateKey(
        duplicateKey: String,
        newStatus: TransactionStatus = TransactionStatus.SUCCESS,
        updatedTimestamp: Long = System.currentTimeMillis()
    ): Int

    /**
     * Atomic Pending-to-Success upsert logic:
     * - Checks if a transaction with the same `referenceNumber` (if present) or `duplicateKey` exists.
     * - If an existing entry is found with `status == PENDING` and the incoming transaction is now `SUCCESS` (or `FAILED`),
     *   updates the existing record in place instead of skipping or duplicating it.
     * - If no matching record exists, inserts the new transaction.
     * - If an already finalized matching record exists, returns `-1L` (duplicate skipped).
     */
    @RoomTransaction
    suspend fun insertOrUpdatePendingToSuccess(incoming: TransactionEntity): Long {
        val ref = incoming.referenceNumber?.trim()?.takeIf { it.isNotEmpty() }
        val existing = if (ref != null) {
            findByReferenceNumber(ref) ?: findByDuplicateKey(incoming.duplicateKey)
        } else {
            findByDuplicateKey(incoming.duplicateKey)
        }

        return when {
            existing == null -> {
                insertTransaction(incoming)
            }
            existing.status == TransactionStatus.PENDING && incoming.status != TransactionStatus.PENDING -> {
                val upgraded = incoming.copy(
                    id = existing.id,
                    category = if (incoming.category.isNotBlank() && incoming.category != "Other") incoming.category else existing.category,
                    categoryId = if (incoming.categoryId != 0L) incoming.categoryId else existing.categoryId,
                    note = incoming.note.ifBlank { existing.note },
                    paymentMode = if (incoming.paymentMode == "CASH" || incoming.isAtmWithdrawal || existing.isAtmWithdrawal) "CASH" else existing.paymentMode,
                    isAtmWithdrawal = incoming.isAtmWithdrawal || existing.isAtmWithdrawal
                )
                updateTransaction(upgraded)
                existing.id
            }
            else -> {
                -1L // Duplicate of an already finalized transaction
            }
        }
    }

    // --- Soft Delete, Restore & Trash Operations ---
    @Query("SELECT * FROM transactions WHERE deletedAtMillis IS NOT NULL ORDER BY deletedAtMillis DESC, id DESC")
    fun observeTrashedTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deletedAtMillis IS NOT NULL ORDER BY deletedAtMillis DESC, id DESC")
    suspend fun getTrashedTransactionsOnce(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NOT NULL")
    fun observeTrashedCount(): Flow<Int>

    @Query("UPDATE transactions SET deletedAtMillis = :deletedAtMillis WHERE id = :id")
    suspend fun softDeleteTransaction(id: Long, deletedAtMillis: Long)

    @Query("UPDATE transactions SET deletedAtMillis = NULL WHERE id = :id")
    suspend fun restoreTransaction(id: Long)

    @Query("DELETE FROM transactions WHERE deletedAtMillis IS NOT NULL")
    suspend fun emptyTrash()

    @Query("DELETE FROM transactions WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :cutoffMillis")
    suspend fun purgeTrashedOlderThan(cutoffMillis: Long): Int

    // --- Hard Delete Operations (Intentionally Unfiltered so they remove trashed rows too) ---
    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    // --- Independent Plain SQL Queries for Data Check Screen (Never reuses Home/Charts/Insights logic) ---
    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL")
    suspend fun getDataCheckTotalActiveCount(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL AND status = 'SUCCESS' AND type != 'TRANSFER'")
    suspend fun getDataCheckSuccessCount(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL AND status = 'PENDING' AND type != 'TRANSFER'")
    suspend fun getDataCheckPendingCount(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL AND status = 'FAILED' AND type != 'TRANSFER'")
    suspend fun getDataCheckFailedCount(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL AND type = 'TRANSFER'")
    suspend fun getDataCheckTransferCount(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NOT NULL")
    suspend fun getDataCheckTrashedCount(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL AND paymentMode = 'ONLINE'")
    suspend fun getDataCheckOnlineActiveCount(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL AND paymentMode = 'CASH'")
    suspend fun getDataCheckCashActiveCount(): Int

    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE deletedAtMillis IS NULL
          AND status = 'SUCCESS'
          AND type = 'EARNING'
          AND timestamp BETWEEN :startMillis AND :endMillis
        """
    )
    suspend fun getDataCheckMonthMoneyInPaise(startMillis: Long, endMillis: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE deletedAtMillis IS NULL
          AND status = 'SUCCESS'
          AND type = 'EXPENSE'
          AND isAtmWithdrawal = 0
          AND timestamp BETWEEN :startMillis AND :endMillis
        """
    )
    suspend fun getDataCheckMonthMoneyOutPaise(startMillis: Long, endMillis: Long): Long

    @Query(
        """
        SELECT COUNT(*)
        FROM transactions
        WHERE deletedAtMillis IS NULL
          AND status = 'SUCCESS'
          AND type != 'TRANSFER'
          AND timestamp BETWEEN :startMillis AND :endMillis
        """
    )
    suspend fun getDataCheckMonthPaymentCount(startMillis: Long, endMillis: Long): Int

    @Query("SELECT * FROM transactions WHERE deletedAtMillis IS NULL ORDER BY id DESC LIMIT 10")
    suspend fun getDataCheckLast10SavedRows(): List<TransactionEntity>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isDefault DESC, name ASC")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY isDefault DESC, name ASC")
    fun observeCategoriesByType(type: CategoryType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY isDefault DESC, name ASC")
    suspend fun getAllCategoriesOnce(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategoriesIgnore(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND isDefault = 0")
    suspend fun deleteCustomCategory(id: Long)

    @Query("DELETE FROM categories")
    suspend fun clearAllCategories()
}

@Dao
interface SnapSpendDao : TransactionDao, CategoryDao {

    @Query("UPDATE transactions SET category = :newName WHERE category = :oldName")
    suspend fun updateTransactionsCategoryName(oldName: String, newName: String)

    @Query("UPDATE budgets SET categoryName = :newName WHERE categoryName = :oldName")
    suspend fun updateBudgetCategoryName(oldName: String, newName: String)

    @Query("UPDATE merchant_memory SET categoryName = :newName WHERE categoryName = :oldName")
    suspend fun updateMerchantMemoryCategoryName(oldName: String, newName: String)

    // --- Merchant Memory ---
    @Query("SELECT * FROM merchant_memory ORDER BY updatedAt DESC")
    fun observeAllMerchantMemories(): Flow<List<MerchantMemoryEntity>>

    @Query("SELECT * FROM merchant_memory")
    suspend fun getAllMerchantMemoriesOnce(): List<MerchantMemoryEntity>

    @Query("SELECT * FROM merchant_memory WHERE normalizedMerchant = :normalizedMerchant LIMIT 1")
    suspend fun getMerchantMemory(normalizedMerchant: String): MerchantMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMerchantMemory(memory: MerchantMemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMerchantMemories(memories: List<MerchantMemoryEntity>)

    @Query("DELETE FROM merchant_memory WHERE normalizedMerchant = :normalizedMerchant")
    suspend fun deleteMerchantMemory(normalizedMerchant: String)

    @Query("DELETE FROM merchant_memory")
    suspend fun clearAllMerchantMemories()

    // --- Budgets ---
    @Query("SELECT * FROM budgets ORDER BY categoryName ASC")
    fun observeAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgetsOnce(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE categoryName = :categoryName")
    suspend fun deleteBudget(categoryName: String)

    @Query("DELETE FROM budgets")
    suspend fun clearAllBudgets()

    // --- Repeating Payments ---
    @Query("SELECT * FROM repeating_payments ORDER BY nextExpectedTimestamp ASC")
    fun observeAllRepeatingPayments(): Flow<List<RepeatingPaymentEntity>>

    @Query("SELECT * FROM repeating_payments")
    suspend fun getAllRepeatingPaymentsOnce(): List<RepeatingPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRepeatingPayment(payment: RepeatingPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepeatingPayments(payments: List<RepeatingPaymentEntity>)

    @Query("DELETE FROM repeating_payments")
    suspend fun clearAllRepeatingPayments()
}
