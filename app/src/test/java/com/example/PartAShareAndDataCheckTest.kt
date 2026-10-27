package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SnapSpendDatabase
import com.example.data.local.TransactionDao
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.rules.LocalInsightsAndRulesEngine
import com.example.ui.loadDataCheckSnapshotFromSql
import com.example.ui.screens.computeDataCheckMonthBounds
import com.example.util.MoneyUtils
import com.example.util.ShareIntentHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class PartAShareAndDataCheckTest {

    private lateinit var context: Context
    private lateinit var db: SnapSpendDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SnapSpendDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.transactionDao()
        ShareIntentHelper.cleanupAllShareCacheDir(context)
    }

    @After
    fun tearDown() {
        ShareIntentHelper.cleanupAllShareCacheDir(context)
        db.close()
    }

    @Test
    fun formatExactPaise_alwaysIncludesTwoDecimalPlacesWithIndianGrouping() {
        assertEquals("₹1,234.50", MoneyUtils.formatExactPaise(123450L))
        assertEquals("₹20.00", MoneyUtils.formatExactPaise(2000L))
        assertEquals("₹0.00", MoneyUtils.formatExactPaise(0L))
        assertEquals("₹1,25,000.05", MoneyUtils.formatExactPaise(12500005L))
    }

    @Test
    fun dataCheckCounts_mutuallyExclusiveStatusTransferAndTrashedCounts() = runBlocking {
        val now = 1761550000000L
        // 2 active SUCCESS expenses
        dao.insertTransaction(createTx(amountPaise = 15000L, type = TransactionType.EXPENSE, status = TransactionStatus.SUCCESS, timestamp = now))
        dao.insertTransaction(createTx(amountPaise = 50000L, type = TransactionType.EARNING, status = TransactionStatus.SUCCESS, timestamp = now))
        // 1 active PENDING expense
        dao.insertTransaction(createTx(amountPaise = 9900L, type = TransactionType.EXPENSE, status = TransactionStatus.PENDING, timestamp = now))
        // 1 active FAILED expense
        dao.insertTransaction(createTx(amountPaise = 25000L, type = TransactionType.EXPENSE, status = TransactionStatus.FAILED, timestamp = now))
        // 2 active TRANSFER rows (one SUCCESS, one PENDING -> both must count ONLY under Transfer)
        dao.insertTransaction(createTx(amountPaise = 100000L, type = TransactionType.TRANSFER, status = TransactionStatus.SUCCESS, timestamp = now))
        dao.insertTransaction(createTx(amountPaise = 40000L, type = TransactionType.TRANSFER, status = TransactionStatus.PENDING, timestamp = now))
        // 1 trashed row
        dao.insertTransaction(createTx(amountPaise = 8000L, type = TransactionType.EXPENSE, status = TransactionStatus.SUCCESS, timestamp = now, deletedAtMillis = now))

        val snapshot = loadDataCheckSnapshotFromSql(dao, nowMillis = now)

        assertEquals(6, snapshot.totalActiveCount)
        assertEquals(2, snapshot.successCount)
        assertEquals(1, snapshot.pendingCount)
        assertEquals(1, snapshot.failedCount)
        assertEquals(2, snapshot.transferCount)
        assertEquals(1, snapshot.trashedCount)
        // Verify mutually exclusive active categories sum to totalActiveCount
        assertEquals(
            snapshot.totalActiveCount,
            snapshot.successCount + snapshot.pendingCount + snapshot.failedCount + snapshot.transferCount
        )
    }

    @Test
    fun dataCheckSameMillisecondMonthBoundary_matchesHomeAtExactMidnightAndMonthEnd() = runBlocking {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 15, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val anchorMillis = cal.timeInMillis

        val (dcStart, dcEnd) = computeDataCheckMonthBounds(anchorMillis, 0)
        val (homeStart, homeEnd) = LocalInsightsAndRulesEngine.getMonthBounds(anchorMillis, 0)

        // Exact same millisecond bounds in local timezone
        assertEquals(homeStart, dcStart)
        assertEquals(homeEnd, dcEnd)

        // 1. Payment at exact 00:00:00.000 on the 1st of the month -> INCLUDED
        dao.insertTransaction(
            createTx(
                merchant = "Midnight First",
                amountPaise = 123450L,
                type = TransactionType.EXPENSE,
                status = TransactionStatus.SUCCESS,
                timestamp = dcStart
            )
        )
        // 2. Payment at exact 23:59:59.999 on the last day of the month -> INCLUDED
        dao.insertTransaction(
            createTx(
                merchant = "Last Millisecond",
                amountPaise = 50025L,
                type = TransactionType.EXPENSE,
                status = TransactionStatus.SUCCESS,
                timestamp = dcEnd
            )
        )
        // 3. Payment 1ms before month start (previous month 23:59:59.999) -> EXCLUDED from this month
        dao.insertTransaction(
            createTx(
                merchant = "Previous Month Last Ms",
                amountPaise = 99900L,
                type = TransactionType.EXPENSE,
                status = TransactionStatus.SUCCESS,
                timestamp = dcStart - 1L
            )
        )
        // 4. Payment 1ms after month end (next month 00:00:00.000) -> EXCLUDED from this month
        dao.insertTransaction(
            createTx(
                merchant = "Next Month First Ms",
                amountPaise = 88800L,
                type = TransactionType.EXPENSE,
                status = TransactionStatus.SUCCESS,
                timestamp = dcEnd + 1L
            )
        )
        // 5. Pending, Failed, Transfer, and Trashed inside this month -> EXCLUDED from spend
        dao.insertTransaction(createTx(amountPaise = 10000L, type = TransactionType.EXPENSE, status = TransactionStatus.PENDING, timestamp = anchorMillis))
        dao.insertTransaction(createTx(amountPaise = 20000L, type = TransactionType.EXPENSE, status = TransactionStatus.FAILED, timestamp = anchorMillis))
        dao.insertTransaction(createTx(amountPaise = 30000L, type = TransactionType.TRANSFER, status = TransactionStatus.SUCCESS, timestamp = anchorMillis))
        dao.insertTransaction(createTx(amountPaise = 40000L, type = TransactionType.EXPENSE, status = TransactionStatus.SUCCESS, timestamp = anchorMillis, deletedAtMillis = anchorMillis))

        val snapshot = loadDataCheckSnapshotFromSql(dao, nowMillis = anchorMillis)

        // Home screen logic for thisMonthSpentPaise
        val allActive = dao.getAllTransactionsOnce()
        val homeThisMonthSpent = LocalInsightsAndRulesEngine
            .successfulTransactions(allActive.filter { it.timestamp in homeStart..homeEnd })
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amountPaise }

        val expectedPaise = 123450L + 50025L
        assertEquals(expectedPaise, snapshot.thisMonthSpentSqlPaise)
        assertEquals(expectedPaise, homeThisMonthSpent)

        val report = snapshot.buildPlainTextReport(homeThisMonthSpent)
        assertTrue(report.contains("Status: MATCH"))
        assertTrue(report.contains("₹1,734.75"))
    }

    @Test
    fun dataCheckLast10SavedRows_returnsUpTo10ActiveRowsOrderedByIdDesc() = runBlocking {
        val now = 1761550000000L
        for (i in 1..12) {
            dao.insertTransaction(
                createTx(
                    merchant = "Merchant $i",
                    amountPaise = i * 1000L,
                    type = TransactionType.EXPENSE,
                    status = TransactionStatus.SUCCESS,
                    timestamp = now + i,
                    referenceNumber = "REF$i"
                )
            )
        }
        // Soft-delete row 12 -> should be excluded from Last 10 Active Saved Rows
        val allActive = dao.getAllTransactionsOnce()
        val highestId = allActive.maxOf { it.id }
        dao.softDeleteTransaction(highestId, now + 5000L)

        val snapshot = loadDataCheckSnapshotFromSql(dao, nowMillis = now)
        assertEquals(10, snapshot.last10SavedRows.size)
        assertEquals("Merchant 11", snapshot.last10SavedRows.first().merchant)
        assertEquals("Merchant 2", snapshot.last10SavedRows.last().merchant)
    }

    @Test
    fun shareIntent_attachedFileIgnoresCompanionText_andCleansUpPreviousShareOnNewIntent() {
        // Create two fake valid JPEG files to share
        val externalDir = File(context.cacheDir, "test_external_source").apply { mkdirs() }
        val file1 = File(externalDir, "receipt1.jpg").apply {
            // Valid non-executable image bytes
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4))
        }
        val file2 = File(externalDir, "receipt2.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 5, 6, 7, 8))
        }

        // First share intent with both an attached image stream AND companion caption text
        val shareIntent1 = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file1))
            putExtra(Intent.EXTRA_TEXT, "Companion caption that should be ignored")
        }

        val payload1 = ShareIntentHelper.ingestShareIntent(context, shareIntent1)
        assertTrue(payload1.hasValidContent)
        assertEquals(1, payload1.tempFiles.size)
        // Companion text is ignored when attached files exist
        assertNull(payload1.sharedTextNote)

        val tempFileFromFirstShare = payload1.tempFiles.first()
        assertTrue(tempFileFromFirstShare.exists())

        // User does not finish review, goes back to GPay and shares a second screenshot (onNewIntent)
        val shareIntent2 = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file2))
        }

        val payload2 = ShareIntentHelper.ingestShareIntent(context, shareIntent2)
        assertTrue(payload2.hasValidContent)
        assertEquals(1, payload2.tempFiles.size)

        // Temp file from the first share MUST be deleted immediately when the second share arrives
        assertFalse(tempFileFromFirstShare.exists())
        assertTrue(payload2.tempFiles.first().exists())
    }

    @Test
    fun shareIntent_enforces10ItemCap_andRejectsUnsupportedFileTypes() {
        // Test >10 text items shared via ACTION_SEND_MULTIPLE
        val twelveTexts = ArrayList<CharSequence>((1..12).map { "Paid ₹${it * 10} to Shop $it" })
        val multiTextIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putCharSequenceArrayListExtra(Intent.EXTRA_TEXT, twelveTexts)
        }

        val textPayload = ShareIntentHelper.ingestShareIntent(context, multiTextIntent)
        assertTrue(textPayload.hasValidContent)
        assertTrue(textPayload.userNoticeMessage?.contains("Only the first 10") == true)
        assertTrue(textPayload.sharedTextNote?.contains("Shop 10") == true)
        assertFalse(textPayload.sharedTextNote?.contains("Shop 11") == true)

        // Test unsupported MIME type (e.g. video/mp4)
        val externalDir = File(context.cacheDir, "test_external_source").apply { mkdirs() }
        val videoFile = File(externalDir, "clip.mp4").apply {
            writeBytes(byteArrayOf(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70))
        }
        val unsupportedIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(videoFile))
        }
        val rejectedPayload = ShareIntentHelper.ingestShareIntent(context, unsupportedIntent)
        assertFalse(rejectedPayload.hasValidContent)
        assertTrue(rejectedPayload.userNoticeMessage?.contains("unsupported", ignoreCase = true) == true)
    }

    private fun createTx(
        merchant: String = "Test Merchant",
        amountPaise: Long,
        type: TransactionType,
        status: TransactionStatus,
        timestamp: Long,
        referenceNumber: String? = null,
        deletedAtMillis: Long? = null
    ): TransactionEntity {
        return TransactionEntity(
            timestamp = timestamp,
            hasExplicitTime = true,
            amountPaise = amountPaise,
            type = type,
            merchant = merchant,
            category = "Food & Dining",
            paymentApp = "GPay",
            status = status,
            referenceNumber = referenceNumber,
            duplicateKey = MoneyUtils.buildDuplicateKey(referenceNumber, timestamp, true, amountPaise, merchant),
            source = TransactionSource.MANUAL,
            deletedAtMillis = deletedAtMillis
        )
    }
}
