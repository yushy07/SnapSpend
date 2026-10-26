package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.repository.SnapSpendRepository
import com.example.data.rules.DraftTransaction
import com.example.data.rules.ResolutionMode
import com.example.util.MoneyUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: SnapSpendRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SnapSpendRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SnapSpend", appName)
    }

    @Test
    fun phase1Check_addThreeTransactionsReopenDatabaseAndDeleteOne(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "phase1_verification.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. Open disk-backed AppDatabase and insert 3 manual transactions in paise (Long)
        val db1 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val repo1 = SnapSpendRepository(db1)
        repo1.ensureDefaultCategoriesSeeded()

        val (ts1, _) = MoneyUtils.parseDateAndTime("2026-10-25", "09:15")
        val (ts2, _) = MoneyUtils.parseDateAndTime("2026-10-25", "13:30")
        val (ts3, _) = MoneyUtils.parseDateAndTime("2026-10-25", "19:45")

        val id1 = repo1.saveTransaction(
            TransactionEntity(
                timestamp = ts1,
                amountPaise = 24550L, // ₹245.50
                type = TransactionType.EXPENSE,
                merchant = "Blue Tokai",
                category = "Food & Dining",
                paymentApp = "GPay",
                duplicateKey = MoneyUtils.buildDuplicateKey(null, ts1, true, 24550L, "Blue Tokai")
            )
        )
        val id2 = repo1.saveTransaction(
            TransactionEntity(
                timestamp = ts2,
                amountPaise = 68000L, // ₹680.00
                type = TransactionType.EXPENSE,
                merchant = "Swiggy Instamart",
                category = "Groceries",
                paymentApp = "PhonePe",
                duplicateKey = MoneyUtils.buildDuplicateKey(null, ts2, true, 68000L, "Swiggy Instamart")
            )
        )
        val id3 = repo1.saveTransaction(
            TransactionEntity(
                timestamp = ts3,
                amountPaise = 145000L, // ₹1,450.00
                type = TransactionType.EXPENSE,
                merchant = "IndianOil",
                category = "Transport & Fuel",
                paymentApp = "Paytm",
                duplicateKey = MoneyUtils.buildDuplicateKey(null, ts3, true, 145000L, "IndianOil")
            )
        )
        db1.close()

        // 2. Reopen disk-backed AppDatabase ("close the app fully, open it again, they're still there")
        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        val repo2 = SnapSpendRepository(db2)

        val reopenedList = repo2.getAllTransactionsOnce()
        assertEquals(3, reopenedList.size)
        assertEquals(237550L, reopenedList.sumOf { it.amount })

        // 3. Delete one transaction, verify it is gone
        repo2.deleteTransaction(id2)
        val afterDelete = repo2.getAllTransactionsOnce()
        assertEquals(2, afterDelete.size)
        assertEquals(listOf(id3, id1), afterDelete.map { it.id })

        db2.close()
        dbFile.delete()
        Unit
    }

    @Test
    fun roomDatabase_persistsPaiseTransactionsAndUpdatesPendingToSuccess(): Unit = runBlocking {
        repository.ensureDefaultCategoriesSeeded()
        val categories = repository.getAllCategoriesOnce()
        assertEquals(18, categories.size)

        val (ts, hasTime) = MoneyUtils.parseDateAndTime("2026-10-25", "11:30")
        val pendingTx = TransactionEntity(
            timestamp = ts,
            hasExplicitTime = hasTime,
            amountPaise = 15050L, // ₹150.50
            type = TransactionType.EXPENSE,
            merchant = "Blue Tokai",
            category = "Food & Dining",
            paymentApp = "GPay",
            referenceNumber = "UTR123456",
            status = TransactionStatus.PENDING,
            duplicateKey = MoneyUtils.buildDuplicateKey("UTR123456", ts, hasTime, 15050L, "Blue Tokai")
        )
        val savedId = repository.saveTransaction(pendingTx, learnMerchantCategory = true)

        val upgradeDraft = DraftTransaction(
            timestamp = ts,
            hasExplicitTime = hasTime,
            amountPaise = 15050L,
            type = TransactionType.EXPENSE,
            merchant = "Blue Tokai",
            category = "Food & Dining",
            paymentApp = "GPay",
            referenceNumber = "UTR123456",
            status = TransactionStatus.SUCCESS,
            source = TransactionSource.SCREENSHOT,
            existingMatchId = savedId,
            existingMatchStatus = TransactionStatus.PENDING,
            resolutionMode = ResolutionMode.UPDATE_PENDING_TO_FINAL,
            isSkipped = false
        )

        val summary = repository.commitConfirmedDrafts(listOf(upgradeDraft))
        assertEquals(0, summary.insertedCount)
        assertEquals(1, summary.updatedPendingCount)

        val allTxs = repository.getAllTransactionsOnce()
        assertEquals(1, allTxs.size)
        assertEquals(TransactionStatus.SUCCESS, allTxs.first().status)
        assertEquals(15050L, allTxs.first().amountPaise)
    }

    @Test
    fun daoFiltersDateRangePendingToSuccessAndUiPresentationConversion(): Unit = runBlocking {
        val txDao = database.transactionDao()
        val catDao = database.categoryDao()

        val customCategoryId = catDao.insertCategory(
            com.example.data.local.Category(
                name = "Specialty Coffee",
                type = "expense",
                colorHex = "#FF9C9C",
                iconName = "restaurant"
            )
        )

        val (tsOct20, _) = MoneyUtils.parseDateAndTime("2026-10-20", "08:30")
        val (tsOct25, _) = MoneyUtils.parseDateAndTime("2026-10-25", "14:15")

        // Insert PENDING transaction using requested constructor fields
        val pendingTx = com.example.data.local.Transaction(
            date = tsOct20,
            amountInPaise = 15050L, // ₹150.50
            type = "expense",
            merchant = "Subko Coffee",
            categoryId = customCategoryId,
            paymentApp = "GPay",
            referenceNumber = "UTR998877",
            status = "pending",
            note = "Morning pour-over",
            createdAt = tsOct20,
            category = "Specialty Coffee"
        )
        val insertedId = txDao.insertOrUpdatePendingToSuccess(pendingTx)

        // Upgrade PENDING -> SUCCESS via DAO insertOrUpdatePendingToSuccess
        val completedTx = com.example.data.local.Transaction(
            date = tsOct20,
            amountInPaise = 15050L,
            type = "expense",
            merchant = "Subko Coffee",
            categoryId = customCategoryId,
            paymentApp = "GPay",
            referenceNumber = "UTR998877",
            status = "success",
            note = "Morning pour-over",
            createdAt = tsOct20,
            category = "Specialty Coffee"
        )
        val upgradedId = txDao.insertOrUpdatePendingToSuccess(completedTx)
        assertEquals(insertedId, upgradedId)

        // Insert a second transaction on Oct 25
        txDao.insertTransaction(
            com.example.data.local.Transaction(
                date = tsOct25,
                amountInPaise = 250000L, // ₹2,500.00
                type = "earning",
                merchant = "Client Invoice",
                categoryId = 15L,
                paymentApp = "NEFT",
                referenceNumber = "NEFT554433",
                status = "success",
                note = "Design sprint",
                createdAt = tsOct25,
                category = "Freelance/Side Income"
            )
        )

        // Verify date-range query
        val oct20Only = txDao.getTransactionsByDateRange(tsOct20 - 1000L, tsOct20 + 1000L)
        assertEquals(1, oct20Only.size)
        assertEquals(TransactionStatus.SUCCESS, oct20Only.first().status)

        // Verify filtered UI presentation query converts Long paise (15050L) -> "₹150.50"
        val filteredUi = txDao.getFilteredTransactionsUi(
            searchQuery = "Subko",
            categoryId = customCategoryId,
            status = TransactionStatus.SUCCESS
        )
        assertEquals(1, filteredUi.size)
        assertEquals(15050L, filteredUi.first().amountInPaise)
        assertEquals("₹150.50", filteredUi.first().formattedAmountRupees)
        assertEquals("-₹150.50", filteredUi.first().signedFormattedAmountRupees)
    }
}
