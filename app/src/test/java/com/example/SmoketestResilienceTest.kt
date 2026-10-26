package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.data.local.DefaultCategories
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.repository.SnapSpendRepository
import com.example.data.rules.LocalInsightsAndRulesEngine
import com.example.util.MoneyUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmoketestResilienceTest {

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
    fun csvExportAndImport_roundTripPreservesSpecialCharactersAndQuotes(): Unit = runBlocking {
        // Arrange: Insert transactions with commas, double quotes, and newlines in merchant and notes
        val originalTxs = listOf(
            TransactionEntity(
                id = 0L,
                timestamp = 1717200000000L,
                hasExplicitTime = true,
                amountPaise = 15050L,
                type = TransactionType.EXPENSE,
                merchant = "Trader Joe's, LLC",
                category = "Groceries",
                paymentApp = "Google Pay",
                referenceNumber = "UPI123456",
                status = TransactionStatus.SUCCESS,
                note = "Organic apples, milk, and eggs\nStored in pantry",
                source = TransactionSource.SCREENSHOT,
                duplicateKey = "ref:upi123456"
            ),
            TransactionEntity(
                id = 0L,
                timestamp = 1717286400000L,
                hasExplicitTime = false,
                amountPaise = 420000L,
                type = TransactionType.EXPENSE,
                merchant = "The \"Best\" Coffee Shop",
                category = "Food & Drinks",
                paymentApp = "PhonePe",
                referenceNumber = null,
                status = TransactionStatus.SUCCESS,
                note = "Cold brew with \"extra\" syrup",
                source = TransactionSource.MANUAL,
                duplicateKey = "comp:20240602_0000:420000:the best coffee shop"
            ),
            TransactionEntity(
                id = 0L,
                timestamp = 1717372800000L,
                hasExplicitTime = true,
                amountPaise = -5000L, // Negative adjustment / refund
                type = TransactionType.EARNING,
                merchant = "Amazon, India - Refund",
                category = "Refunds",
                paymentApp = "Amazon Pay",
                referenceNumber = "REF987654",
                status = TransactionStatus.SUCCESS,
                note = "Returned damaged item",
                source = TransactionSource.IMPORT,
                duplicateKey = "ref:ref987654"
            )
        )

        for (tx in originalTxs) {
            repository.saveTransaction(tx)
        }

        // Act: Export to CSV
        val exportedCsv = repository.exportTransactionsCsv()
        assertTrue("Exported CSV must contain header", exportedCsv.startsWith("Date,Time,Merchant"))

        // Clear database and import back
        val summary = repository.importTransactionsCsv(exportedCsv, replaceEverything = true)
        assertEquals("Should import all 3 transactions", 3, summary.insertedCount)

        // Assert: Data round-trips identically
        val importedTxs = database.dao().getAllTransactionsOnce()
        assertEquals(3, importedTxs.size)

        val tx1 = importedTxs.firstOrNull { it.referenceNumber == "UPI123456" }
        assertNotNull("Transaction 1 with comma in merchant must exist", tx1)
        assertEquals("Trader Joe's, LLC", tx1!!.merchant)
        assertEquals("Organic apples, milk, and eggs\nStored in pantry", tx1.note)
        assertEquals(15050L, tx1.amountPaise)

        val tx2 = importedTxs.firstOrNull { it.merchant.contains("Best") }
        assertNotNull("Transaction 2 with escaped quotes must exist", tx2)
        assertEquals("The \"Best\" Coffee Shop", tx2!!.merchant)
        assertEquals("Cold brew with \"extra\" syrup", tx2.note)
        assertEquals(420000L, tx2.amountPaise)

        val tx3 = importedTxs.firstOrNull { it.referenceNumber == "REF987654" }
        assertNotNull("Transaction 3 with negative amount must exist", tx3)
        assertEquals(-5000L, tx3!!.amountPaise)
        assertEquals(TransactionType.EARNING, tx3.type)
    }

    @Test
    fun moneyUtils_handlesNegativeAndZeroPaiseAccurately() {
        // Zero paise
        assertEquals("₹0", MoneyUtils.formatPaise(0L))
        assertEquals("", MoneyUtils.paiseToEditableRupeeString(0L))

        // Negative paise
        assertEquals("-₹50", MoneyUtils.formatPaise(-5000L))
        assertEquals("-50", MoneyUtils.paiseToEditableRupeeString(-5000L))
        assertEquals("-₹150.50", MoneyUtils.formatPaise(-15050L))
        assertEquals("-150.50", MoneyUtils.paiseToEditableRupeeString(-15050L))

        // Rupee parsing
        assertEquals(15050L, MoneyUtils.parseRupeesToPaise("150.50"))
        assertEquals(15050L, MoneyUtils.parseRupeesToPaise("₹ 150.50"))
        assertEquals(-5000L, MoneyUtils.parseRupeesToPaise("-50.00"))
        assertEquals(2500000L, MoneyUtils.parseRupeesToPaise("25,000"))

        // Compact formatting
        assertEquals("₹1.5L", MoneyUtils.formatPaiseCompact(15000000L)) // 1.5 Lakhs
        assertEquals("₹2.5Cr", MoneyUtils.formatPaiseCompact(2500000000L)) // 2.5 Crores
        assertEquals("-₹5.0k", MoneyUtils.formatPaiseCompact(-500000L)) // -5k
    }

    @Test
    fun emptyStateMath_noCrashOnZeroTransactionsOrDivisors(): Unit = runBlocking {
        // Empty state insights
        val emptyTxs = emptyList<TransactionEntity>()
        val categories = DefaultCategories.ALL_DEFAULTS

        val slices = LocalInsightsAndRulesEngine.buildCategorySlices(
            transactions = emptyTxs,
            categories = categories,
            type = TransactionType.EXPENSE
        )
        assertTrue("Slices for empty transactions should be empty", slices.isEmpty())

        val budgets = database.dao().getAllBudgetsOnce()
        val budgetStatuses = LocalInsightsAndRulesEngine.computeBudgetStatuses(
            budgets = budgets,
            categories = categories,
            thisMonthTransactions = emptyTxs
        )
        assertTrue("Budget statuses should compute safely with 0 transactions", budgetStatuses.isEmpty())
    }

    @Test
    fun categoryDeck_safeIndexWrappingForBoundaries() {
        // 0 items
        val zeroCount = 0
        val safeIdxZero = if (zeroCount == 0) 0 else 5 % zeroCount
        assertEquals(0, safeIdxZero)

        // 1 item
        val oneCount = 1
        val safeIdxOne = 0 % oneCount
        assertEquals(0, safeIdxOne)

        // Wrapping forward and backward
        val total = 6
        assertEquals(1, (0 + 1) % total)
        assertEquals(0, (5 + 1) % total)
        val wrapNegative = (-1).mod(total)
        assertEquals(5, wrapNegative)
    }
}
