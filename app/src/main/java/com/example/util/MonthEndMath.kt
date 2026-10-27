package com.example.util

import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import java.util.Calendar
import java.util.TimeZone

data class MonthEndEstimateResult(
    val spentSoFarPaise: Long,
    val successfulExpenseCount: Int,
    val dayOfMonth: Int,
    val daysInMonth: Int,
    val daysLeftIncludingToday: Int,
    val showPaceEstimate: Boolean,
    val projectedMonthEndPaise: Long?,
    val paceLineText: String,
    val hasBudget: Boolean,
    val isOverBudget: Boolean,
    val safeToSpendPerDayPaise: Long?,
    val overBudgetByPaise: Long?,
    val safeLineText: String
)

data class QuickRepeatChip(
    val key: String,
    val merchant: String,
    val category: String,
    val amountPaise: Long,
    val paymentApp: String,
    val countInLast90Days: Int,
    val lastTimestamp: Long,
    val paymentMode: String = "ONLINE"
) {
    val label: String
        get() = "$merchant ${MoneyUtils.formatPaise(amountPaise)}"
}

object MonthEndMath {

    /**
     * Computes Month-End Estimate and Safe-to-Spend-Per-Day using 100% whole-number paise math.
     * Accepts [anchorMillis] and [timeZone] as inputs instead of reading the system clock directly,
     * so unit tests can deterministically test day 1, mid-month, last day, and leap-year February.
     */
    fun computeMonthEndEstimate(
        transactions: List<TransactionEntity>,
        totalMonthlyBudgetPaise: Long,
        anchorMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): MonthEndEstimateResult {
        val (monthStartMillis, monthEndMillis) = getMonthBoundsInTimeZone(anchorMillis, timeZone)

        val validExpensesThisMonth = transactions.filter { tx ->
            tx.deletedAtMillis == null &&
                tx.status == TransactionStatus.SUCCESS &&
                tx.type == TransactionType.EXPENSE &&
                tx.amountPaise > 0L &&
                tx.timestamp in monthStartMillis..monthEndMillis
        }

        val spentSoFarPaise = validExpensesThisMonth.sumOf { it.amountPaise }
        val expenseCount = validExpensesThisMonth.size

        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = anchorMillis
        }
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(dayOfMonth)
        val daysLeftIncludingToday = (daysInMonth - dayOfMonth + 1).coerceAtLeast(1)

        // Line a): Show only when there are at least 5 payments and at least 5 days have passed this month.
        // Multiply first, then divide, using whole Long paise: (spentPaise * daysInMonth) / dayOfMonth
        val showPaceEstimate = expenseCount >= 5 && dayOfMonth >= 5
        val projectedMonthEndPaise: Long? = if (showPaceEstimate) {
            (spentSoFarPaise * daysInMonth.toLong()) / dayOfMonth.toLong()
        } else {
            null
        }
        val paceLineText = if (projectedMonthEndPaise != null) {
            "At this pace you'll spend ${MoneyUtils.formatPaise(projectedMonthEndPaise)} by month end"
        } else {
            "Add a few more payments to see an estimate"
        }

        // Line b): SAFE TO SPEND PER DAY: ₹Y = (total monthly budget − spent so far) ÷ days left, counting today.
        val hasBudget = totalMonthlyBudgetPaise > 0L
        var isOverBudget = false
        var safeToSpendPerDayPaise: Long? = null
        var overBudgetByPaise: Long? = null

        val safeLineText = if (!hasBudget) {
            "Set a budget to see this"
        } else {
            val remainingPaise = totalMonthlyBudgetPaise - spentSoFarPaise
            if (remainingPaise < 0L) {
                isOverBudget = true
                overBudgetByPaise = -remainingPaise
                "Over budget by ${MoneyUtils.formatPaise(overBudgetByPaise)}"
            } else {
                // Round down for per-day amounts via whole-number Long division
                safeToSpendPerDayPaise = remainingPaise / daysLeftIncludingToday.toLong()
                "SAFE TO SPEND PER DAY: ${MoneyUtils.formatPaise(safeToSpendPerDayPaise)}"
            }
        }

        return MonthEndEstimateResult(
            spentSoFarPaise = spentSoFarPaise,
            successfulExpenseCount = expenseCount,
            dayOfMonth = dayOfMonth,
            daysInMonth = daysInMonth,
            daysLeftIncludingToday = daysLeftIncludingToday,
            showPaceEstimate = showPaceEstimate,
            projectedMonthEndPaise = projectedMonthEndPaise,
            paceLineText = paceLineText,
            hasBudget = hasBudget,
            isOverBudget = isOverBudget,
            safeToSpendPerDayPaise = safeToSpendPerDayPaise,
            overBudgetByPaise = overBudgetByPaise,
            safeLineText = safeLineText
        )
    }

    /**
     * Computes up to 6 Quick Repeat chips from existing transactions:
     * - Successful expenses (not failed, pending, transfer, or trashed)
     * - Same merchant and same amount that appear 3 or more times in the last 90 days
     * - Ordered by most frequent first, then most recent.
     */
    fun computeQuickRepeats(
        transactions: List<TransactionEntity>,
        nowMillis: Long,
        maxChips: Int = 6
    ): List<QuickRepeatChip> {
        val ninetyDaysMillis = 90L * 24L * 60L * 60L * 1000L
        val cutoffMillis = nowMillis - ninetyDaysMillis

        val eligible = transactions.filter { tx ->
            tx.deletedAtMillis == null &&
                tx.status == TransactionStatus.SUCCESS &&
                tx.type == TransactionType.EXPENSE &&
                tx.amountPaise > 0L &&
                tx.timestamp in cutoffMillis..nowMillis &&
                MoneyUtils.normalizeMerchant(tx.merchant) != "unknown"
        }

        return eligible
            .groupBy { tx ->
                "${MoneyUtils.normalizeMerchant(tx.merchant)}|${tx.amountPaise}|${tx.paymentMode.uppercase()}"
            }
            .values
            .filter { group -> group.size >= 3 }
            .map { group ->
                val sortedByRecent = group.sortedWith(
                    compareByDescending<TransactionEntity> { it.timestamp }.thenByDescending { it.id }
                )
                val latest = sortedByRecent.first()
                val modeNormalized = if (latest.paymentMode.equals("CASH", ignoreCase = true)) "CASH" else "ONLINE"
                val normKey = "${MoneyUtils.normalizeMerchant(latest.merchant)}|${latest.amountPaise}|$modeNormalized"
                QuickRepeatChip(
                    key = normKey,
                    merchant = latest.merchant,
                    category = latest.category,
                    amountPaise = latest.amountPaise,
                    paymentApp = latest.paymentApp,
                    countInLast90Days = group.size,
                    lastTimestamp = latest.timestamp,
                    paymentMode = modeNormalized
                )
            }
            .sortedWith(
                compareByDescending<QuickRepeatChip> { it.countInLast90Days }
                    .thenByDescending { it.lastTimestamp }
            )
            .take(maxChips)
    }

    fun getMonthBoundsInTimeZone(anchorMillis: Long, timeZone: TimeZone): Pair<Long, Long> {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = anchorMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return Pair(start, cal.timeInMillis)
    }
}
