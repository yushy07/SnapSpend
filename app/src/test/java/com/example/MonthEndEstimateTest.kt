package com.example

import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.util.MonthEndMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MonthEndEstimateTest {

    private val istZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    private fun dateMillis(
        year: Int,
        monthOneBased: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0
    ): Long {
        return Calendar.getInstance(istZone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthOneBased - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun makeExpense(
        id: Long,
        timestamp: Long,
        amountPaise: Long,
        merchant: String = "Merchant $id",
        category: String = "Food & Dining",
        status: TransactionStatus = TransactionStatus.SUCCESS,
        type: TransactionType = TransactionType.EXPENSE,
        deletedAtMillis: Long? = null
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            timestamp = timestamp,
            hasExplicitTime = true,
            amountPaise = amountPaise,
            type = type,
            merchant = merchant,
            category = category,
            paymentApp = "GPay",
            status = status,
            deletedAtMillis = deletedAtMillis
        )
    }

    @Test
    fun day1_hidesPaceEstimateEvenWith5Payments_andComputesSafeToSpendOverFullMonth() {
        val anchor = dateMillis(2026, 10, 1) // Oct 1, 2026 (31 days in month)
        val txs = (1L..6L).map { idx ->
            makeExpense(idx, anchor - idx * 60_000L, 10_000L) // 6 x ₹100 = ₹600 (60,000 paise)
        }
        val budgetPaise = 3_160_000L // ₹31,600

        val res = MonthEndMath.computeMonthEndEstimate(
            transactions = txs,
            totalMonthlyBudgetPaise = budgetPaise,
            anchorMillis = anchor,
            timeZone = istZone
        )

        assertEquals(1, res.dayOfMonth)
        assertEquals(31, res.daysInMonth)
        assertEquals(31, res.daysLeftIncludingToday)
        assertEquals(6, res.successfulExpenseCount)
        assertFalse(res.showPaceEstimate)
        assertNull(res.projectedMonthEndPaise)
        assertEquals("Add a few more payments to see an estimate", res.paceLineText)

        // (3,160,000 - 60,000) / 31 = 3,100,000 / 31 = 100,000 paise (₹1,000)
        assertEquals(100_000L, res.safeToSpendPerDayPaise)
        assertEquals("SAFE TO SPEND PER DAY: ₹1,000", res.safeLineText)
    }

    @Test
    fun midMonthDay_multipliesBeforeDividingInLongPaise_andRoundsDownPerDay() {
        val anchor = dateMillis(2026, 10, 7) // Day 7 of 31-day October (25 days left counting today)
        // Pick spent = 1,000 paise across 5 expenses so (1000 / 7) * 31 = 142 * 31 = 4402 (wrong early division),
        // whereas (1000 * 31) / 7 = 31000 / 7 = 4428 paise (exact multiply-first Long math).
        val txs = (1L..5L).map { idx ->
            makeExpense(idx, dateMillis(2026, 10, idx.toInt()), 200L)
        }
        val budgetPaise = 2_019L // Remaining = 2019 - 1000 = 1019 paise; 1019 / 25 = 40 paise (rounds down)

        val res = MonthEndMath.computeMonthEndEstimate(
            transactions = txs,
            totalMonthlyBudgetPaise = budgetPaise,
            anchorMillis = anchor,
            timeZone = istZone
        )

        assertTrue(res.showPaceEstimate)
        assertEquals(4_428L, res.projectedMonthEndPaise)
        assertEquals("At this pace you'll spend ₹44.28 by month end", res.paceLineText)
        assertEquals(25, res.daysLeftIncludingToday)
        assertEquals(40L, res.safeToSpendPerDayPaise)
        assertEquals("SAFE TO SPEND PER DAY: ₹0.40", res.safeLineText)
    }

    @Test
    fun lastDayOfMonth_hasOneDayLeftIncludingToday_andProjectsExactCurrentSpend() {
        val anchor = dateMillis(2026, 10, 31) // Day 31 of 31
        val txs = (1L..5L).map { idx ->
            makeExpense(idx, dateMillis(2026, 10, (idx * 5).toInt()), 50_000L) // 5 x ₹500 = ₹2,500
        }
        val budgetPaise = 300_000L // ₹3,000

        val res = MonthEndMath.computeMonthEndEstimate(
            transactions = txs,
            totalMonthlyBudgetPaise = budgetPaise,
            anchorMillis = anchor,
            timeZone = istZone
        )

        assertEquals(31, res.dayOfMonth)
        assertEquals(31, res.daysInMonth)
        assertEquals(1, res.daysLeftIncludingToday)
        assertTrue(res.showPaceEstimate)
        assertEquals(250_000L, res.projectedMonthEndPaise)
        assertEquals("At this pace you'll spend ₹2,500 by month end", res.paceLineText)
        assertEquals(50_000L, res.safeToSpendPerDayPaise)
        assertEquals("SAFE TO SPEND PER DAY: ₹500", res.safeLineText)
    }

    @Test
    fun leapFebruary2024_uses29DaysInMonth() {
        val anchor = dateMillis(2024, 2, 10) // Feb 10, 2024 (leap year -> 29 days, 20 days left including Feb 10)
        val txs = (1L..5L).map { idx ->
            makeExpense(idx, dateMillis(2024, 2, idx.toInt()), 20_000L) // 5 x ₹200 = ₹1,000 (100,000 paise)
        }
        val budgetPaise = 500_000L // ₹5,000

        val res = MonthEndMath.computeMonthEndEstimate(
            transactions = txs,
            totalMonthlyBudgetPaise = budgetPaise,
            anchorMillis = anchor,
            timeZone = istZone
        )

        assertEquals(10, res.dayOfMonth)
        assertEquals(29, res.daysInMonth)
        assertEquals(20, res.daysLeftIncludingToday)
        assertTrue(res.showPaceEstimate)
        // (100,000 * 29) / 10 = 290,000 paise (₹2,900)
        assertEquals(290_000L, res.projectedMonthEndPaise)
        assertEquals("At this pace you'll spend ₹2,900 by month end", res.paceLineText)
        // (500,000 - 100,000) / 20 = 20,000 paise (₹200)
        assertEquals(20_000L, res.safeToSpendPerDayPaise)
        assertEquals("SAFE TO SPEND PER DAY: ₹200", res.safeLineText)
    }

    @Test
    fun noBudget_showsSetABudgetToSeeThis() {
        val anchor = dateMillis(2026, 10, 15)
        val txs = (1L..5L).map { idx ->
            makeExpense(idx, dateMillis(2026, 10, idx.toInt()), 10_000L)
        }

        val res = MonthEndMath.computeMonthEndEstimate(
            transactions = txs,
            totalMonthlyBudgetPaise = 0L,
            anchorMillis = anchor,
            timeZone = istZone
        )

        assertFalse(res.hasBudget)
        assertFalse(res.isOverBudget)
        assertNull(res.safeToSpendPerDayPaise)
        assertEquals("Set a budget to see this", res.safeLineText)
    }

    @Test
    fun overBudget_showsOverBudgetByAmount() {
        val anchor = dateMillis(2026, 10, 15)
        val txs = (1L..5L).map { idx ->
            makeExpense(idx, dateMillis(2026, 10, idx.toInt()), 30_000L) // Total ₹1,500 (150,000 paise)
        }

        val res = MonthEndMath.computeMonthEndEstimate(
            transactions = txs,
            totalMonthlyBudgetPaise = 100_000L, // Budget ₹1,000
            anchorMillis = anchor,
            timeZone = istZone
        )

        assertTrue(res.hasBudget)
        assertTrue(res.isOverBudget)
        assertNull(res.safeToSpendPerDayPaise)
        assertEquals(50_000L, res.overBudgetByPaise)
        assertEquals("Over budget by ₹500", res.safeLineText)
    }

    @Test
    fun fewerThan5ValidPayments_hidesPaceEstimate_andIgnoresTrashedFailedPendingTransfers() {
        val anchor = dateMillis(2026, 10, 20)
        val txs = listOf(
            makeExpense(1L, dateMillis(2026, 10, 2), 10_000L),
            makeExpense(2L, dateMillis(2026, 10, 4), 10_000L),
            makeExpense(3L, dateMillis(2026, 10, 6), 10_000L),
            makeExpense(4L, dateMillis(2026, 10, 8), 10_000L),
            // Ignored rows:
            makeExpense(5L, dateMillis(2026, 10, 9), 50_000L, status = TransactionStatus.PENDING),
            makeExpense(6L, dateMillis(2026, 10, 10), 50_000L, status = TransactionStatus.FAILED),
            makeExpense(7L, dateMillis(2026, 10, 11), 50_000L, type = TransactionType.TRANSFER),
            makeExpense(8L, dateMillis(2026, 10, 12), 50_000L, deletedAtMillis = anchor - 1000L)
        )

        val res = MonthEndMath.computeMonthEndEstimate(
            transactions = txs,
            totalMonthlyBudgetPaise = 100_000L,
            anchorMillis = anchor,
            timeZone = istZone
        )

        assertEquals(4, res.successfulExpenseCount)
        assertEquals(40_000L, res.spentSoFarPaise)
        assertFalse(res.showPaceEstimate)
        assertNull(res.projectedMonthEndPaise)
        assertEquals("Add a few more payments to see an estimate", res.paceLineText)
    }

    @Test
    fun quickRepeats_requires3InLast90Days_ignoresTrashedPendingTransfers_andSortsByFrequencyThenRecent() {
        val now = dateMillis(2026, 10, 25)
        val dayMs = 24L * 60L * 60L * 1000L

        val txs = listOf(
            // Chai ₹20 -> 4 times in last 90 days (most frequent)
            makeExpense(1L, now - 1 * dayMs, 2000L, merchant = "Chai"),
            makeExpense(2L, now - 2 * dayMs, 2000L, merchant = "Chai"),
            makeExpense(3L, now - 3 * dayMs, 2000L, merchant = "Chai"),
            makeExpense(4L, now - 4 * dayMs, 2000L, merchant = "Chai"),
            // Metro ₹40 -> 3 times, most recent at now - 2h
            makeExpense(5L, now - 7_200_000L, 4000L, merchant = "Metro"),
            makeExpense(6L, now - 5 * dayMs, 4000L, merchant = "Metro"),
            makeExpense(7L, now - 10 * dayMs, 4000L, merchant = "Metro"),
            // Coffee ₹150 -> 3 times, most recent at now - 3 days
            makeExpense(8L, now - 3 * dayMs, 15000L, merchant = "Coffee"),
            makeExpense(9L, now - 6 * dayMs, 15000L, merchant = "Coffee"),
            makeExpense(10L, now - 9 * dayMs, 15000L, merchant = "Coffee"),
            // Snacks ₹50 -> 2 active + 1 trashed (should NOT qualify)
            makeExpense(11L, now - 1 * dayMs, 5000L, merchant = "Snacks"),
            makeExpense(12L, now - 2 * dayMs, 5000L, merchant = "Snacks"),
            makeExpense(13L, now - 3 * dayMs, 5000L, merchant = "Snacks", deletedAtMillis = now)
        )

        val chips = MonthEndMath.computeQuickRepeats(txs, nowMillis = now, maxChips = 6)
        assertEquals(3, chips.size)
        assertEquals("Chai ₹20", chips[0].label)
        assertEquals("Metro ₹40", chips[1].label)
        assertEquals("Coffee ₹150", chips[2].label)
    }
}
