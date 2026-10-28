package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.ai.ExtractionResult
import com.example.data.ai.GeminiReceiptExtractor
import com.example.data.local.AppDatabase
import com.example.data.local.CategoryType
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.repository.SnapSpendRepository
import com.example.data.rules.DraftTransaction
import com.example.data.rules.LocalInsightsAndRulesEngine
import com.example.ui.screens.applyQuickAmountToCashNote
import com.example.util.MoneyUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationTest {

    private val testDbName = "migration_test_snapspend.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3_preservesAllExistingColumnsAndSetsDeletedAtMillisNull() {
        // 1. Create the database at schema version 2 using exported schema 2.json and insert two real rows
        helper.createDatabase(testDbName, 2).apply {
            execSQL(
                """
                INSERT INTO transactions (
                    id, timestamp, hasExplicitTime, amountPaise, type, merchant,
                    category, categoryId, paymentApp, referenceNumber, status,
                    note, createdAt, source, duplicateKey
                ) VALUES (
                    101, 1730001000000, 1, 24550, 'EXPENSE', 'Blue Tokai Coffee',
                    'Food & Dining', 1, 'GPay', 'UTR101202303', 'SUCCESS',
                    'Morning latte', 1730001005000, 'SCREENSHOT', 'ref:utr101202303'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO transactions (
                    id, timestamp, hasExplicitTime, amountPaise, type, merchant,
                    category, categoryId, paymentApp, referenceNumber, status,
                    note, createdAt, source, duplicateKey
                ) VALUES (
                    102, 1730005000000, 0, 120000, 'EXPENSE', 'Swiggy Instamart',
                    'Groceries', 2, 'PhonePe', NULL, 'PENDING',
                    'Weekly groceries', 1730005009000, 'MANUAL', '2024-10-26|none|120000|swiggy instamart'
                )
                """.trimIndent()
            )
            close()
        }

        // 2. Run AppDatabase.MIGRATION_2_3 and validate against exported schema 3.json
        val migratedDb = helper.runMigrationsAndValidate(
            testDbName,
            3,
            true,
            AppDatabase.MIGRATION_2_3
        )

        // 3. Query migrated rows and verify every column value from v2 is unchanged and deletedAtMillis is NULL
        val cursor = migratedDb.query(
            """
            SELECT id, timestamp, hasExplicitTime, amountPaise, type, merchant,
                   category, categoryId, paymentApp, referenceNumber, status,
                   note, createdAt, source, duplicateKey, deletedAtMillis
            FROM transactions
            ORDER BY id ASC
            """.trimIndent()
        )

        assertEquals(2, cursor.count)

        assertTrue(cursor.moveToFirst())
        assertEquals(101L, cursor.getLong(0))
        assertEquals(1730001000000L, cursor.getLong(1))
        assertEquals(1, cursor.getInt(2))
        assertEquals(24550L, cursor.getLong(3))
        assertEquals("EXPENSE", cursor.getString(4))
        assertEquals("Blue Tokai Coffee", cursor.getString(5))
        assertEquals("Food & Dining", cursor.getString(6))
        assertEquals(1L, cursor.getLong(7))
        assertEquals("GPay", cursor.getString(8))
        assertEquals("UTR101202303", cursor.getString(9))
        assertEquals("SUCCESS", cursor.getString(10))
        assertEquals("Morning latte", cursor.getString(11))
        assertEquals(1730001005000L, cursor.getLong(12))
        assertEquals("SCREENSHOT", cursor.getString(13))
        assertEquals("ref:utr101202303", cursor.getString(14))
        assertTrue(cursor.isNull(15))

        assertTrue(cursor.moveToNext())
        assertEquals(102L, cursor.getLong(0))
        assertEquals(1730005000000L, cursor.getLong(1))
        assertEquals(0, cursor.getInt(2))
        assertEquals(120000L, cursor.getLong(3))
        assertEquals("EXPENSE", cursor.getString(4))
        assertEquals("Swiggy Instamart", cursor.getString(5))
        assertEquals("Groceries", cursor.getString(6))
        assertEquals(2L, cursor.getLong(7))
        assertEquals("PhonePe", cursor.getString(8))
        assertTrue(cursor.isNull(9))
        assertEquals("PENDING", cursor.getString(10))
        assertEquals("Weekly groceries", cursor.getString(11))
        assertEquals(1730005009000L, cursor.getLong(12))
        assertEquals("MANUAL", cursor.getString(13))
        assertEquals("2024-10-26|none|120000|swiggy instamart", cursor.getString(14))
        assertTrue(cursor.isNull(15))

        cursor.close()
        migratedDb.close()
    }

    @Test
    fun migrate3To4_preservesAllExistingRowsAndDefaultsPaymentModeToOnline() {
        val dbName3To4 = "migration_test_3_to_4.db"
        // 1. Create the database at schema version 3 using exported schema 3.json and insert two real rows
        helper.createDatabase(dbName3To4, 3).apply {
            execSQL(
                """
                INSERT INTO transactions (
                    id, timestamp, hasExplicitTime, amountPaise, type, merchant,
                    category, categoryId, paymentApp, referenceNumber, status,
                    note, createdAt, source, duplicateKey, deletedAtMillis
                ) VALUES (
                    201, 1730002000000, 1, 35000, 'EXPENSE', 'Metro Recharge',
                    'Transport & Fuel', 4, 'GPay', 'UTR201999', 'SUCCESS',
                    'Card recharge', 1730002005000, 'SCREENSHOT', 'ref:utr201999', NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO transactions (
                    id, timestamp, hasExplicitTime, amountPaise, type, merchant,
                    category, categoryId, paymentApp, referenceNumber, status,
                    note, createdAt, source, duplicateKey, deletedAtMillis
                ) VALUES (
                    202, 1730008000000, 0, 89900, 'EXPENSE', 'Zomato Order',
                    'Food & Dining', 1, 'PhonePe', NULL, 'SUCCESS',
                    'Trashed dinner', 1730008009000, 'MANUAL', '2024-10-27|none|89900|zomato order', 1730009000000
                )
                """.trimIndent()
            )
            close()
        }

        // 2. Run AppDatabase.MIGRATION_3_4 and validate against exported schema 4.json
        val migratedDb = helper.runMigrationsAndValidate(
            dbName3To4,
            4,
            true,
            AppDatabase.MIGRATION_3_4
        )

        // 3. Query migrated rows and verify every column value from v3 is preserved, paymentMode defaults to 'ONLINE', and isAtmWithdrawal defaults to 0
        val cursor = migratedDb.query(
            """
            SELECT id, timestamp, hasExplicitTime, amountPaise, type, merchant,
                   category, categoryId, paymentApp, referenceNumber, status,
                   note, createdAt, source, duplicateKey, deletedAtMillis, paymentMode, isAtmWithdrawal
            FROM transactions
            ORDER BY id ASC
            """.trimIndent()
        )

        assertEquals(2, cursor.count)

        assertTrue(cursor.moveToFirst())
        assertEquals(201L, cursor.getLong(0))
        assertEquals(1730002000000L, cursor.getLong(1))
        assertEquals(1, cursor.getInt(2))
        assertEquals(35000L, cursor.getLong(3))
        assertEquals("EXPENSE", cursor.getString(4))
        assertEquals("Metro Recharge", cursor.getString(5))
        assertEquals("Transport & Fuel", cursor.getString(6))
        assertEquals(4L, cursor.getLong(7))
        assertEquals("GPay", cursor.getString(8))
        assertEquals("UTR201999", cursor.getString(9))
        assertEquals("SUCCESS", cursor.getString(10))
        assertEquals("Card recharge", cursor.getString(11))
        assertEquals(1730002005000L, cursor.getLong(12))
        assertEquals("SCREENSHOT", cursor.getString(13))
        assertEquals("ref:utr201999", cursor.getString(14))
        assertTrue(cursor.isNull(15))
        assertEquals("ONLINE", cursor.getString(16))
        assertEquals(0, cursor.getInt(17))

        assertTrue(cursor.moveToNext())
        assertEquals(202L, cursor.getLong(0))
        assertEquals(1730008000000L, cursor.getLong(1))
        assertEquals(0, cursor.getInt(2))
        assertEquals(89900L, cursor.getLong(3))
        assertEquals("EXPENSE", cursor.getString(4))
        assertEquals("Zomato Order", cursor.getString(5))
        assertEquals("Food & Dining", cursor.getString(6))
        assertEquals(1L, cursor.getLong(7))
        assertEquals("PhonePe", cursor.getString(8))
        assertTrue(cursor.isNull(9))
        assertEquals("SUCCESS", cursor.getString(10))
        assertEquals("Trashed dinner", cursor.getString(11))
        assertEquals(1730008009000L, cursor.getLong(12))
        assertEquals("MANUAL", cursor.getString(13))
        assertEquals("2024-10-27|none|89900|zomato order", cursor.getString(14))
        assertEquals(1730009000000L, cursor.getLong(15))
        assertEquals("ONLINE", cursor.getString(16))
        assertEquals(0, cursor.getInt(17))

        cursor.close()
        migratedDb.close()
    }

    @Test
    fun softDeleteRestoreCategoryRenamePurgeAndBackup_behaveAccordingToSpec(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = SnapSpendRepository(db)
        val dao = db.dao()

        try {
            repo.ensureDefaultCategoriesSeeded()
            val (ts, hasTime) = MoneyUtils.parseDateAndTime("2026-10-25", "10:30")
            val dupKey = MoneyUtils.buildDuplicateKey("UTR555", ts, hasTime, 2000L, "Chai Point")

            val id1 = repo.saveTransaction(
                TransactionEntity(
                    timestamp = ts,
                    hasExplicitTime = hasTime,
                    amountPaise = 2000L,
                    type = TransactionType.EXPENSE,
                    merchant = "Chai Point",
                    category = "Food & Dining",
                    paymentApp = "GPay",
                    referenceNumber = "UTR555",
                    status = TransactionStatus.SUCCESS,
                    source = TransactionSource.MANUAL,
                    duplicateKey = dupKey
                )
            )

            // Soft-delete moves row to Trash and hides it from active queries + duplicate checks
            val deletedTime = 1730010000000L
            repo.softDeleteTransaction(id1, deletedTime)
            assertEquals(0, repo.getAllTransactionsOnce().size)
            assertEquals(1, repo.getTrashedTransactionsOnce().size)
            assertNull(dao.findByDuplicateKey(dupKey))
            assertNull(dao.findByReferenceNumber("UTR555"))

            // Renaming a category updates trashed rows too so restoring later does not resurrect an old category
            repo.addOrUpdateCategory(
                id = 1L,
                oldName = "Food & Dining",
                newName = "Cafes & Dining",
                type = CategoryType.EXPENSE,
                colorHex = "#FF6F59",
                iconName = "restaurant",
                isDefault = true
            )
            assertEquals("Cafes & Dining", repo.getTrashedTransactionsOnce().first().category)

            // CSV export excludes trashed rows; JSON export includes trashed rows with deletedAtMillis
            val csv = repo.exportTransactionsCsv()
            assertFalse(csv.contains("Chai Point"))
            val json = repo.exportFullBackupJson()
            assertTrue(json.contains("Chai Point"))
            assertTrue(json.contains("1730010000000"))

            // Restore brings the row back with deletedAtMillis == null and the renamed category intact
            repo.restoreTransaction(id1)
            val activeAfterRestore = repo.getAllTransactionsOnce()
            assertEquals(1, activeAfterRestore.size)
            assertNull(activeAfterRestore.first().deletedAtMillis)
            assertEquals("Cafes & Dining", activeAfterRestore.first().category)

            // 30-day startup purge removes trashed items older than cutoffMillis
            val thirtyOneDaysAgo = 1700000000000L
            repo.softDeleteTransaction(id1, thirtyOneDaysAgo)
            val purgedCount = repo.purgeExpiredTrash(thirtyOneDaysAgo + 1000L)
            assertEquals(1, purgedCount)
            assertEquals(0, repo.getTrashedTransactionsOnce().size)

            // Importing an old JSON backup without deletedAtMillis or paymentMode defaults paymentMode to ONLINE
            val legacyJson = """
                {
                  "schemaVersion": 1,
                  "transactions": [
                    {
                      "id": 99,
                      "timestamp": 1730001000000,
                      "hasExplicitTime": true,
                      "amountPaise": 5000,
                      "type": "EXPENSE",
                      "merchant": "Legacy Tea Stall",
                      "category": "Cafes & Dining",
                      "paymentApp": "Cash",
                      "status": "SUCCESS",
                      "note": "Old backup row",
                      "createdAt": 1730001000000,
                      "source": "MANUAL",
                      "duplicateKey": "2024-10-27|10:00|5000|legacy tea stall"
                    }
                  ]
                }
            """.trimIndent()
            val summary = repo.importFromBackupJson(legacyJson, replaceEverything = true)
            assertEquals(1, summary.insertedCount)
            val imported = repo.getAllTransactionsOnce()
            assertEquals(1, imported.size)
            assertEquals("Legacy Tea Stall", imported.first().merchant)
            assertNull(imported.first().deletedAtMillis)
            assertEquals("ONLINE", imported.first().paymentMode)
        } finally {
            db.close()
        }
    }

    @Test
    fun cashWalletMath_atmWithdrawalClassification_quickAmountHelper_backupAndDataCheckCounts_behaveAccordingToSpec(): Unit = runBlocking {
        // 1. Quick amount button helper tests
        assertEquals("20 ", applyQuickAmountToCashNote("", 20))
        assertEquals("60 momos", applyQuickAmountToCashNote("40 momos", 20))
        assertEquals("auto 60, samosa 80", applyQuickAmountToCashNote("auto 60, samosa 30", 50))
        assertEquals("momos 100", applyQuickAmountToCashNote("momos", 100))

        // 2. Multi-item Smart Cash local parser test ("auto 60, samosa 30")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val extractor = GeminiReceiptExtractor(context)
        val multiResult = extractor.parseNaturalLanguageNoteLocally(
            note = "auto 60, samosa 30",
            allowedCategories = listOf("Food & Dining", "Transport & Fuel", "Other"),
            defaultPaymentMode = "CASH",
            defaultCashType = TransactionType.EXPENSE
        )
        assertTrue(multiResult is ExtractionResult.Success)
        val multiDrafts = (multiResult as ExtractionResult.Success).drafts
        assertEquals(2, multiDrafts.size)
        assertEquals(6000L, multiDrafts[0].amountPaise)
        assertEquals("CASH", multiDrafts[0].paymentMode)
        assertEquals(TransactionType.EXPENSE, multiDrafts[0].type)
        assertEquals(3000L, multiDrafts[1].amountPaise)
        assertEquals("CASH", multiDrafts[1].paymentMode)

        // 3. ATM cash withdrawal detection & auto-classification (prevents double-counting)
        assertTrue(LocalInsightsAndRulesEngine.isAtmCashWithdrawalText("ATM WDL HDFC BANK"))
        assertTrue(LocalInsightsAndRulesEngine.isAtmCashWithdrawalText("CASH WITHDRAWAL AT SBI ATM"))
        assertTrue(LocalInsightsAndRulesEngine.isAtmCashWithdrawalText("ATW-409123 AXIS BANK"))

        val atmDraft = LocalInsightsAndRulesEngine.enrichAndResolveDraft(
            rawDraft = DraftTransaction(
                merchant = "ATM WDL SBI",
                rawMerchantText = "ATM WDL SBI",
                amountPaise = 200000L, // ₹2,000
                type = TransactionType.EXPENSE,
                category = "Other",
                timestamp = System.currentTimeMillis(),
                hasExplicitTime = true,
                paymentApp = "SBI",
                referenceNumber = "ATW998877",
                status = TransactionStatus.SUCCESS,
                source = TransactionSource.PDF,
                paymentMode = "ONLINE"
            ),
            existingTransactions = emptyList(),
            merchantMemoryMap = emptyMap()
        )
        assertEquals(TransactionType.TRANSFER, atmDraft.type)
        assertEquals("CASH", atmDraft.paymentMode)

        // 4. Cash in Hand paise math & Data Check Online vs Cash SQL counts
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = SnapSpendRepository(db)
        val dao = db.dao()

        try {
            repo.ensureDefaultCategoriesSeeded()
            val now = System.currentTimeMillis()
            val (monthStart, monthEnd) = LocalInsightsAndRulesEngine.getMonthBounds(now, 0)

            // a) ATM Withdrawal / Cash Top-up: +₹2,000 (200_000 paise) -> TRANSFER + CASH
            repo.saveTransaction(
                TransactionEntity(
                    timestamp = now,
                    hasExplicitTime = true,
                    amountPaise = 200_000L,
                    type = TransactionType.TRANSFER,
                    merchant = "ATM Withdrawal",
                    category = "Other",
                    paymentApp = "Cash",
                    status = TransactionStatus.SUCCESS,
                    source = TransactionSource.MANUAL,
                    duplicateKey = "atm-topup-1",
                    paymentMode = "CASH"
                )
            )
            // b) Received Cash from friend: +₹500 (50_000 paise) -> EARNING + CASH
            repo.saveTransaction(
                TransactionEntity(
                    timestamp = now,
                    hasExplicitTime = true,
                    amountPaise = 50_000L,
                    type = TransactionType.EARNING,
                    merchant = "Friend Cash Back",
                    category = "Gifts & Refunds",
                    paymentApp = "Cash",
                    status = TransactionStatus.SUCCESS,
                    source = TransactionSource.MANUAL,
                    duplicateKey = "cash-earn-1",
                    paymentMode = "CASH"
                )
            )
            // c) Spent Cash on Momos: -₹40 (4_000 paise) -> EXPENSE + CASH
            repo.saveTransaction(
                TransactionEntity(
                    timestamp = now,
                    hasExplicitTime = true,
                    amountPaise = 4_000L,
                    type = TransactionType.EXPENSE,
                    merchant = "Momos Stall",
                    category = "Food & Dining",
                    paymentApp = "Cash",
                    status = TransactionStatus.SUCCESS,
                    source = TransactionSource.MANUAL,
                    duplicateKey = "cash-exp-1",
                    paymentMode = "CASH"
                )
            )
            // d) Spent Online on GPay: ₹150 (15_000 paise) -> EXPENSE + ONLINE
            repo.saveTransaction(
                TransactionEntity(
                    timestamp = now,
                    hasExplicitTime = true,
                    amountPaise = 15_000L,
                    type = TransactionType.EXPENSE,
                    merchant = "Swiggy",
                    category = "Food & Dining",
                    paymentApp = "GPay",
                    status = TransactionStatus.SUCCESS,
                    source = TransactionSource.SCREENSHOT,
                    duplicateKey = "online-exp-1",
                    paymentMode = "ONLINE"
                )
            )

            val allTx = repo.getAllTransactionsOnce()
            val walletTotals = LocalInsightsAndRulesEngine.computeCashWalletTotals(
                allTransactions = allTx,
                nowMillis = now
            )

            // Cash in Hand = 200_000 (TRANSFER CASH) + 50_000 (EARNING CASH) - 4_000 (EXPENSE CASH) = 246_000 paise (₹2,460.00)
            assertEquals(246_000L, walletTotals.cashInHandPaise)
            // Cash Spent This Month = 4_000 paise (₹40.00) — ATM withdrawal is NOT double-counted as spent!
            assertEquals(4_000L, walletTotals.thisMonthCashSpentPaise)

            // Data Check plain SQL counts: 1 ONLINE active, 3 CASH active
            assertEquals(1, dao.getDataCheckOnlineActiveCount())
            assertEquals(3, dao.getDataCheckCashActiveCount())
            // Total monthly spent across all modes = 4_000 (Cash) + 15_000 (Online) = 19_000 paise
            assertEquals(19_000L, dao.getDataCheckMonthMoneyOutPaise(monthStart, monthEnd))

            // CSV and JSON backup round-trip preserves paymentMode = "CASH"
            val exportedCsv = repo.exportTransactionsCsv()
            assertTrue(exportedCsv.contains("Payment Mode"))
            assertTrue(exportedCsv.contains("\"CASH\""))
            val exportedJson = repo.exportFullBackupJson()
            assertTrue(exportedJson.contains("paymentMode"))
            assertTrue(exportedJson.contains("CASH"))

            repo.importFromBackupJson(exportedJson, replaceEverything = true)
            assertEquals(1, dao.getDataCheckOnlineActiveCount())
            assertEquals(3, dao.getDataCheckCashActiveCount())

            repo.importTransactionsCsv(exportedCsv, replaceEverything = true)
            assertEquals(1, dao.getDataCheckOnlineActiveCount())
            assertEquals(3, dao.getDataCheckCashActiveCount())
        } finally {
            db.close()
        }
    }
}

