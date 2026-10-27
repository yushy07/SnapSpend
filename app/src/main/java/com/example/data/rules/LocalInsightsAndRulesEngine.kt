package com.example.data.rules

import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.DefaultCategories
import com.example.data.local.MerchantMemoryEntity
import com.example.data.local.RepeatingPaymentEntity
import com.example.data.local.RepeatingState
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

enum class ResolutionMode {
    INSERT_NEW,
    UPDATE_PENDING_TO_FINAL,
    SKIP_DUPLICATE
}

data class ReceiptLineItem(
    val itemName: String,
    val quantity: Int = 1,
    val pricePaise: Long, // Exact paise for this line item
    val isSelectedForSplit: Boolean = true
)

data class DraftTransaction(
    val draftId: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val hasExplicitTime: Boolean,
    val amountPaise: Long, // Whole paise
    val type: TransactionType,
    val merchant: String,
    val category: String,
    val paymentApp: String,
    val referenceNumber: String?,
    val status: TransactionStatus,
    val note: String = "",
    val source: TransactionSource,
    val confidence: Float = 1.0f,
    val existingMatchId: Long? = null,
    val existingMatchStatus: TransactionStatus? = null,
    val resolutionMode: ResolutionMode = ResolutionMode.INSERT_NEW,
    val isSkipped: Boolean = false,
    val appliedMerchantMemory: Boolean = false,
    val categoryWasManuallyEdited: Boolean = false,
    val rawMerchantText: String = "",
    val lineItems: List<ReceiptLineItem> = emptyList(),
    val originalAmountPaise: Long = amountPaise,
    val splitWays: Int = 1, // 1 = full bill, >1 = equal split (÷N), 0 = itemized pick
    val paymentMode: String = "ONLINE"
) {
    val isLowConfidence: Boolean
        get() = confidence < 0.75f || merchant.equals("unknown", ignoreCase = true) || amountPaise <= 0L

    val duplicateKey: String
        get() = MoneyUtils.buildDuplicateKey(
            referenceNumber = referenceNumber,
            timestampMillis = timestamp,
            hasExplicitTime = hasExplicitTime,
            amountPaise = amountPaise,
            merchant = merchant
        )

    /**
     * Splits the original bill amount equally among `ways` people using exact integer paise math.
     */
    fun applyEqualSplit(ways: Int): DraftTransaction {
        val safeWays = ways.coerceIn(1, 20)
        val baseTotal = if (originalAmountPaise > 0L) originalAmountPaise else amountPaise
        val newAmountPaise = if (safeWays == 1) {
            baseTotal
        } else {
            (baseTotal / safeWays.toLong()).coerceAtLeast(1L)
        }
        val cleanBaseNote = note
            .replace(Regex("\\s*·?\\s*\\[Split[^\\]]*\\]"), "")
            .trim()
        val splitSuffix = if (safeWays > 1) {
            "[Split 1/$safeWays of ${MoneyUtils.formatPaise(baseTotal)}]"
        } else {
            ""
        }
        val combinedNote = listOf(cleanBaseNote, splitSuffix)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        return copy(
            amountPaise = newAmountPaise,
            originalAmountPaise = baseTotal,
            splitWays = safeWays,
            lineItems = lineItems.map { it.copy(isSelectedForSplit = true) },
            note = combinedNote
        )
    }

    /**
     * Toggles a single receipt line item in Itemized Split Mode and recalculates `amountPaise`
     * as the exact paise sum of selected items.
     */
    fun toggleLineItemSelection(index: Int): DraftTransaction {
        if (index !in lineItems.indices) return this
        val baseTotal = if (originalAmountPaise > 0L) originalAmountPaise else amountPaise
        val updatedItems = lineItems.mapIndexed { idx, item ->
            if (idx == index) item.copy(isSelectedForSplit = !item.isSelectedForSplit) else item
        }
        val selectedItems = updatedItems.filter { it.isSelectedForSplit }
        val sumSelectedPaise = selectedItems.sumOf { it.pricePaise }
        val newAmountPaise = if (sumSelectedPaise > 0L) sumSelectedPaise else baseTotal

        val cleanBaseNote = note
            .replace(Regex("\\s*·?\\s*\\[Split[^\\]]*\\]"), "")
            .trim()
        val splitSuffix = if (selectedItems.size in 1 until updatedItems.size) {
            "[Split: ${selectedItems.joinToString(", ") { it.itemName }} of ${MoneyUtils.formatPaise(baseTotal)}]"
        } else {
            ""
        }
        val combinedNote = listOf(cleanBaseNote, splitSuffix)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        return copy(
            amountPaise = newAmountPaise,
            originalAmountPaise = baseTotal,
            splitWays = if (selectedItems.size == updatedItems.size) 1 else 0,
            lineItems = updatedItems,
            note = combinedNote
        )
    }
}

enum class BarHighlightRole {
    NORMAL,
    HIGHEST,
    LOWEST
}

data class PillBarEntry(
    val label: String,
    val subLabel: String = "",
    val amountPaise: Long,
    val percentageOfTotal: Int,
    val fractionOfMax: Float,
    val role: BarHighlightRole
)

data class CategorySlice(
    val categoryName: String,
    val colorHex: String,
    val amountPaise: Long,
    val percentage: Int,
    val transactionCount: Int
)

data class LineChartPoint(
    val label: String,
    val timestamp: Long,
    val amountPaise: Long
)

data class PeriodComparison(
    val title: String,
    val currentLabel: String,
    val previousLabel: String,
    val currentSpentPaise: Long,
    val previousSpentPaise: Long,
    val currentEarnedPaise: Long,
    val previousEarnedPaise: Long,
    val deltaPercent: Int? // null if previous was 0
)

data class MonthlyIncomeExpenseBar(
    val monthLabel: String,
    val earnedPaise: Long,
    val spentPaise: Long
)

data class TopMerchantItem(
    val rank: Int,
    val merchant: String,
    val category: String,
    val totalSpentPaise: Long,
    val count: Int,
    val sharePercent: Int
)

data class BudgetStatusItem(
    val categoryName: String,
    val colorHex: String,
    val limitPaise: Long,
    val spentPaise: Long,
    val usagePercent: Int,
    val isWarning80: Boolean,
    val isExceeded100: Boolean
)

data class CashWalletTotals(
    val cashInHandPaise: Long,
    val thisMonthCashSpentPaise: Long,
    val cashTransfersInPaise: Long,
    val cashEarningsInPaise: Long,
    val cashExpensesOutPaise: Long
)

/**
 * 100% On-Device Local Math & Smart Rules Engine.
 * Stored transaction data NEVER leaves the device for AI insights.
 * All totals and ratios are computed from Long paise integers.
 */
object LocalInsightsAndRulesEngine {

    /**
     * Resolves an AI-extracted draft transaction against existing Room transactions
     * and learned Merchant Memory rules:
     * 1. Cleans cryptic UPI/NEFT/POS merchant names while preserving raw descriptor if modified.
     * 2. Refunds ("Refunds & Cashback" or merchant/note mentioning refund) are set to EARNING.
     * 3. Learned Merchant Memory overrides AI's suggested category.
     * 4. Duplicate key checks referenceNumber first; otherwise date + HH:mm + amountPaise + merchant.
     * 5. If an existing match was PENDING and the new scan is SUCCESS (or FAILED),
     *    sets ResolutionMode.UPDATE_PENDING_TO_FINAL (updates existing row in place, not skipped!).
     * 6. If an existing match was already final, sets ResolutionMode.SKIP_DUPLICATE (skipped by default).
     */
    private val ATM_TOKEN_REGEX = Regex("""\bATW\b""", RegexOption.IGNORE_CASE)

    /**
     * Detects ATM cash withdrawal descriptions from bank statements, SMS alerts, or receipts
     * ("ATM WDL", "CASH WITHDRAWAL", "ATW", etc.) so they are classified as TRANSFER + CASH
     * (topping up the Cash Wallet without double-counting as an expense).
     */
    fun isAtmCashWithdrawalText(text: String): Boolean {
        if (text.isBlank()) return false
        val upper = text.uppercase(Locale.US)
        return upper.contains("ATM WDL") ||
            upper.contains("ATM-WDL") ||
            upper.contains("ATM_WDL") ||
            upper.contains("CASH WITHDRAWAL") ||
            upper.contains("ATM CASH") ||
            upper.contains("CASH WDL") ||
            upper.contains("SELF WITHDRAWAL") ||
            upper.contains("ATW/") ||
            upper.contains("ATW-") ||
            ATM_TOKEN_REGEX.containsMatchIn(upper)
    }

    fun enrichAndResolveDraft(
        rawDraft: DraftTransaction,
        existingTransactions: List<TransactionEntity>,
        merchantMemoryMap: Map<String, MerchantMemoryEntity>
    ): DraftTransaction {
        val (cleanedMerchant, extractedRawFromMerchant) = MoneyUtils.cleanRawUpiOrBankMerchant(rawDraft.merchant)
        val preservedRawMerchant = rawDraft.rawMerchantText.ifBlank { extractedRawFromMerchant }
        val normMerchant = MoneyUtils.normalizeMerchant(cleanedMerchant)
        val learnedMemory = merchantMemoryMap[normMerchant]

        val isAtmWithdrawal = isAtmCashWithdrawalText(rawDraft.merchant) ||
            isAtmCashWithdrawalText(rawDraft.rawMerchantText) ||
            isAtmCashWithdrawalText(rawDraft.note) ||
            isAtmCashWithdrawalText(cleanedMerchant)

        // Rule: Refunds count as earnings ("Refunds & Cashback"), not negative expenses
        val isRefund = !isAtmWithdrawal && (
            rawDraft.category.equals("Refunds & Cashback", ignoreCase = true) ||
                rawDraft.note.contains("refund", ignoreCase = true)
            )
        val resolvedType = when {
            isAtmWithdrawal -> TransactionType.TRANSFER
            isRefund -> TransactionType.EARNING
            else -> rawDraft.type
        }
        val resolvedCategory = when {
            isAtmWithdrawal -> "Transfers"
            learnedMemory != null && resolvedType != TransactionType.TRANSFER -> learnedMemory.categoryName
            isRefund -> "Refunds & Cashback"
            else -> rawDraft.category
        }
        val resolvedPaymentMode = when {
            isAtmWithdrawal -> "CASH"
            rawDraft.paymentMode.equals("CASH", ignoreCase = true) -> "CASH"
            rawDraft.paymentApp.equals("Cash", ignoreCase = true) -> "CASH"
            else -> "ONLINE"
        }

        val normRef = MoneyUtils.normalizeReference(rawDraft.referenceNumber)
        val candidateDuplicateKey = MoneyUtils.buildDuplicateKey(
            referenceNumber = rawDraft.referenceNumber,
            timestampMillis = rawDraft.timestamp,
            hasExplicitTime = rawDraft.hasExplicitTime,
            amountPaise = rawDraft.amountPaise,
            merchant = cleanedMerchant
        )

        val matchedExisting = existingTransactions.firstOrNull { existing ->
            val existingRef = MoneyUtils.normalizeReference(existing.referenceNumber)
            if (normRef != null && existingRef != null) {
                normRef == existingRef
            } else {
                existing.duplicateKey == candidateDuplicateKey
            }
        }

        val resolutionMode: ResolutionMode
        val shouldSkipByDefault: Boolean

        if (matchedExisting != null) {
            if (matchedExisting.status == TransactionStatus.PENDING &&
                rawDraft.status != TransactionStatus.PENDING
            ) {
                // Pending -> Success / Failed transition: update old entry in place!
                resolutionMode = ResolutionMode.UPDATE_PENDING_TO_FINAL
                shouldSkipByDefault = false
            } else {
                // Already recorded: mark as duplicate and skip by default
                resolutionMode = ResolutionMode.SKIP_DUPLICATE
                shouldSkipByDefault = true
            }
        } else {
            resolutionMode = ResolutionMode.INSERT_NEW
            shouldSkipByDefault = false
        }

        return rawDraft.copy(
            merchant = cleanedMerchant,
            rawMerchantText = preservedRawMerchant,
            type = resolvedType,
            category = resolvedCategory,
            paymentMode = resolvedPaymentMode,
            existingMatchId = matchedExisting?.id,
            existingMatchStatus = matchedExisting?.status,
            resolutionMode = resolutionMode,
            isSkipped = shouldSkipByDefault,
            appliedMerchantMemory = learnedMemory != null
        )
    }

    /**
     * Computes Cash Wallet ("Cash in Hand") balance and this month's Cash Spent in whole paise:
     * cashInHandPaise = (non-trashed SUCCESS where type == TRANSFER && paymentMode == "CASH")
     *                 + (non-trashed SUCCESS where type == EARNING && paymentMode == "CASH")
     *                 - (non-trashed SUCCESS where type == EXPENSE && paymentMode == "CASH")
     */
    fun computeCashWalletTotals(
        allTransactions: List<TransactionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): CashWalletTotals {
        val activeSuccessCash = allTransactions.filter { tx ->
            tx.deletedAtMillis == null &&
                tx.status == TransactionStatus.SUCCESS &&
                tx.paymentMode.equals("CASH", ignoreCase = true)
        }
        val cashTransfersInPaise = activeSuccessCash
            .filter { it.type == TransactionType.TRANSFER }
            .sumOf { it.amountPaise }
        val cashEarningsInPaise = activeSuccessCash
            .filter { it.type == TransactionType.EARNING }
            .sumOf { it.amountPaise }
        val cashExpensesOutPaise = activeSuccessCash
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amountPaise }
        val cashInHandPaise = cashTransfersInPaise + cashEarningsInPaise - cashExpensesOutPaise

        val (monthStart, monthEnd) = getMonthBounds(nowMillis, 0)
        val thisMonthCashSpentPaise = activeSuccessCash
            .filter { it.type == TransactionType.EXPENSE && it.timestamp in monthStart..monthEnd }
            .sumOf { it.amountPaise }

        return CashWalletTotals(
            cashInHandPaise = cashInHandPaise,
            thisMonthCashSpentPaise = thisMonthCashSpentPaise,
            cashTransfersInPaise = cashTransfersInPaise,
            cashEarningsInPaise = cashEarningsInPaise,
            cashExpensesOutPaise = cashExpensesOutPaise
        )
    }

    /**
     * Filters transactions that count toward real financial totals:
     * Only Status == SUCCESS are counted (FAILED and PENDING are excluded).
     */
    fun successfulTransactions(transactions: List<TransactionEntity>): List<TransactionEntity> {
        return transactions.filter { it.status == TransactionStatus.SUCCESS }
    }

    /**
     * Computes Pill Bar Chart entries with HIGHEST (white fill) and LOWEST (black fill) roles.
     */
    fun buildPillBars(rawBuckets: List<Pair<String, Long>>, subLabels: List<String> = emptyList()): List<PillBarEntry> {
        if (rawBuckets.isEmpty()) return emptyList()
        val totalPaise = rawBuckets.sumOf { it.second }.coerceAtLeast(1L)
        val maxPaise = rawBuckets.maxOf { it.second }
        val nonZeroAmounts = rawBuckets.map { it.second }.filter { it > 0L }
        val highestVal = nonZeroAmounts.maxOrNull() ?: 0L
        val lowestVal = if (nonZeroAmounts.size >= 2) nonZeroAmounts.minOrNull() ?: 0L else -1L

        var highestAssigned = false
        var lowestAssigned = false

        return rawBuckets.mapIndexed { idx, (label, amountPaise) ->
            val pct = ((amountPaise * 100L) / totalPaise).toInt().coerceIn(0, 100)
            val fraction = if (maxPaise > 0L) {
                (amountPaise.toFloat() / maxPaise.toFloat()).coerceIn(0.08f, 1f)
            } else {
                0.08f
            }
            val role = when {
                amountPaise > 0L && amountPaise == highestVal && !highestAssigned -> {
                    highestAssigned = true
                    BarHighlightRole.HIGHEST
                }
                amountPaise > 0L && amountPaise == lowestVal && lowestVal != highestVal && !lowestAssigned -> {
                    lowestAssigned = true
                    BarHighlightRole.LOWEST
                }
                else -> BarHighlightRole.NORMAL
            }
            PillBarEntry(
                label = label,
                subLabel = subLabels.getOrElse(idx) { "" },
                amountPaise = amountPaise,
                percentageOfTotal = pct,
                fractionOfMax = if (amountPaise == 0L) 0.08f else fraction,
                role = role
            )
        }
    }

    /**
     * Builds Category Pie/Donut slices for a given list of transactions.
     */
    fun buildCategorySlices(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        type: TransactionType = TransactionType.EXPENSE
    ): List<CategorySlice> {
        val valid = successfulTransactions(transactions).filter { it.type == type && it.amountPaise > 0L }
        val totalPaise = valid.sumOf { it.amountPaise }
        if (totalPaise <= 0L) return emptyList()

        val colorMap = categories.associate { it.name to it.colorHex }

        val grouped = valid.groupBy { it.category }
            .map { (catName, txs) ->
                val sum = txs.sumOf { it.amountPaise }
                Triple(catName, sum, txs.size)
            }
            .sortedByDescending { it.second }

        return grouped.map { (catName, sumPaise, count) ->
            val pct = ((sumPaise * 100L) / totalPaise).toInt().coerceAtLeast(1)
            val hex = colorMap[catName] ?: DefaultCategories.defaultColorForName(catName)
            CategorySlice(
                categoryName = catName,
                colorHex = hex,
                amountPaise = sumPaise,
                percentage = pct,
                transactionCount = count
            )
        }
    }

    /**
     * Computes Top 5 Spending Places (merchants) in exact paise.
     */
    fun buildTop5SpendingPlaces(transactions: List<TransactionEntity>): List<TopMerchantItem> {
        val expenses = successfulTransactions(transactions).filter {
            it.type == TransactionType.EXPENSE && it.amountPaise > 0L
        }
        val totalSpentPaise = expenses.sumOf { it.amountPaise }.coerceAtLeast(1L)

        return expenses
            .groupBy { MoneyUtils.normalizeMerchant(it.merchant) }
            .values
            .map { group ->
                val displayMerchant = group.first().merchant
                val dominantCategory = group.groupBy { it.category }
                    .maxByOrNull { it.value.size }?.key ?: group.first().category
                val sumPaise = group.sumOf { it.amountPaise }
                Triple(displayMerchant, dominantCategory, Pair(sumPaise, group.size))
            }
            .sortedByDescending { it.third.first }
            .take(5)
            .mapIndexed { idx, (merchant, category, stats) ->
                val sharePct = ((stats.first * 100L) / totalSpentPaise).toInt().coerceIn(1, 100)
                TopMerchantItem(
                    rank = idx + 1,
                    merchant = merchant,
                    category = category,
                    totalSpentPaise = stats.first,
                    count = stats.second,
                    sharePercent = sharePct
                )
            }
    }

    /**
     * Computes Week vs Week, Month vs Month, and Year vs Year comparisons in exact paise.
     */
    fun buildPeriodComparisons(
        allTransactions: List<TransactionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<PeriodComparison> {
        val valid = successfulTransactions(allTransactions)

        // 1. This Week vs Last Week
        val (thisWeekStart, thisWeekEnd) = getWeekBounds(nowMillis, 0)
        val (lastWeekStart, lastWeekEnd) = getWeekBounds(nowMillis, -1)

        // 2. This Month vs Last Month
        val (thisMonthStart, thisMonthEnd) = getMonthBounds(nowMillis, 0)
        val (lastMonthStart, lastMonthEnd) = getMonthBounds(nowMillis, -1)

        // 3. This Year vs Last Year
        val (thisYearStart, thisYearEnd) = getYearBounds(nowMillis, 0)
        val (lastYearStart, lastYearEnd) = getYearBounds(nowMillis, -1)

        return listOf(
            createComparison(
                title = "Week vs Week",
                currentLabel = "This week",
                previousLabel = "Last week",
                transactions = valid,
                curStart = thisWeekStart,
                curEnd = thisWeekEnd,
                prevStart = lastWeekStart,
                prevEnd = lastWeekEnd
            ),
            createComparison(
                title = "Month vs Month",
                currentLabel = "This month",
                previousLabel = "Last month",
                transactions = valid,
                curStart = thisMonthStart,
                curEnd = thisMonthEnd,
                prevStart = lastMonthStart,
                prevEnd = lastMonthEnd
            ),
            createComparison(
                title = "Year vs Year",
                currentLabel = "This year",
                previousLabel = "Last year",
                transactions = valid,
                curStart = thisYearStart,
                curEnd = thisYearEnd,
                prevStart = lastYearStart,
                prevEnd = lastYearEnd
            )
        )
    }

    private fun createComparison(
        title: String,
        currentLabel: String,
        previousLabel: String,
        transactions: List<TransactionEntity>,
        curStart: Long,
        curEnd: Long,
        prevStart: Long,
        prevEnd: Long
    ): PeriodComparison {
        val curTxs = transactions.filter { it.timestamp in curStart..curEnd }
        val prevTxs = transactions.filter { it.timestamp in prevStart..prevEnd }

        val curSpent = curTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }
        val prevSpent = prevTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }
        val curEarned = curTxs.filter { it.type == TransactionType.EARNING }.sumOf { it.amountPaise }
        val prevEarned = prevTxs.filter { it.type == TransactionType.EARNING }.sumOf { it.amountPaise }

        val deltaPct = if (prevSpent > 0L) {
            (((curSpent - prevSpent) * 100L) / prevSpent).toInt()
        } else {
            null
        }

        return PeriodComparison(
            title = title,
            currentLabel = currentLabel,
            previousLabel = previousLabel,
            currentSpentPaise = curSpent,
            previousSpentPaise = prevSpent,
            currentEarnedPaise = curEarned,
            previousEarnedPaise = prevEarned,
            deltaPercent = deltaPct
        )
    }

    /**
     * Generates 100% On-Device Plain-Math Text Insights.
     * Zero transaction data is ever sent to Gemini.
     */
    fun generateLocalMathInsights(
        allTransactions: List<TransactionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<String> {
        val valid = successfulTransactions(allTransactions)
        if (valid.isEmpty()) {
            return listOf("Add or scan your first payment screenshot to see your spending insights.")
        }

        val insights = mutableListOf<String>()
        val (thisMonthStart, thisMonthEnd) = getMonthBounds(nowMillis, 0)
        val (lastMonthStart, lastMonthEnd) = getMonthBounds(nowMillis, -1)

        val thisMonthExpenses = valid.filter {
            it.type == TransactionType.EXPENSE && it.timestamp in thisMonthStart..thisMonthEnd
        }
        val lastMonthExpenses = valid.filter {
            it.type == TransactionType.EXPENSE && it.timestamp in lastMonthStart..lastMonthEnd
        }

        val thisMonthSpent = thisMonthExpenses.sumOf { it.amountPaise }
        val lastMonthSpent = lastMonthExpenses.sumOf { it.amountPaise }

        // 1. Category-level month-over-month comparison (e.g. "Food & Dining spending is up 30% from last month")
        val thisMonthByCat = thisMonthExpenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amountPaise } }
        val lastMonthByCat = lastMonthExpenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amountPaise } }

        val allCats = (thisMonthByCat.keys + lastMonthByCat.keys).distinct()
        var biggestShiftSentence: String? = null
        var biggestAbsDeltaPaise = 0L

        for (cat in allCats) {
            val cur = thisMonthByCat[cat] ?: 0L
            val prev = lastMonthByCat[cat] ?: 0L
            if (prev > 0L && cur > 0L && cur != prev) {
                val pct = (((cur - prev) * 100L) / prev).toInt()
                val absDelta = abs(cur - prev)
                if (abs(pct) >= 5 && absDelta > biggestAbsDeltaPaise) {
                    biggestAbsDeltaPaise = absDelta
                    val direction = if (pct > 0) "up ${pct}%" else "down ${abs(pct)}%"
                    biggestShiftSentence = "$cat spending is $direction from last month (${MoneyUtils.formatPaise(cur)} vs ${MoneyUtils.formatPaise(prev)})."
                }
            }
        }
        if (biggestShiftSentence != null) {
            insights.add(biggestShiftSentence)
        }

        // 2. Overall monthly spend comparison
        if (lastMonthSpent > 0L && thisMonthSpent > 0L) {
            val totalPct = (((thisMonthSpent - lastMonthSpent) * 100L) / lastMonthSpent).toInt()
            if (totalPct > 0) {
                insights.add("Total spending this month is up $totalPct% compared to last month.")
            } else if (totalPct < 0) {
                insights.add("Nice pace — total spending this month is down ${abs(totalPct)}% from last month.")
            } else {
                insights.add("Your spending this month matches last month to the exact rupee.")
            }
        } else if (thisMonthSpent > 0L) {
            val topCat = thisMonthByCat.maxByOrNull { it.value }
            if (topCat != null) {
                val share = ((topCat.value * 100L) / thisMonthSpent).toInt()
                insights.add("${topCat.key} is your biggest category this month at $share% (${MoneyUtils.formatPaise(topCat.value)}).")
            }
        }

        // 3. Top merchant concentration
        val topMerchants = buildTop5SpendingPlaces(thisMonthExpenses.ifEmpty { valid })
        if (topMerchants.isNotEmpty()) {
            val top = topMerchants.first()
            insights.add("Top place: ${top.merchant} with ${top.count} payment${if (top.count == 1) "" else "s"} totaling ${MoneyUtils.formatPaise(top.totalSpentPaise)} (${top.sharePercent}% of spend).")
        }

        // 4. Net savings insight
        val thisMonthEarned = valid.filter {
            it.type == TransactionType.EARNING && it.timestamp in thisMonthStart..thisMonthEnd
        }.sumOf { it.amountPaise }
        if (thisMonthEarned > 0L) {
            val leftPaise = thisMonthEarned - thisMonthSpent
            if (leftPaise >= 0L) {
                val savedPct = ((leftPaise * 100L) / thisMonthEarned).toInt()
                insights.add("You have ${MoneyUtils.formatPaise(leftPaise)} ($savedPct% of earnings) left this month.")
            } else {
                insights.add("Heads up: expenses exceed this month's recorded earnings by ${MoneyUtils.formatPaise(abs(leftPaise))}.")
            }
        }

        return insights.distinct().take(4)
    }

    /**
     * Detects repeating payments locally (same merchant, similar amount within ±15%, regular day gap).
     */
    fun detectRepeatingPayments(
        allTransactions: List<TransactionEntity>,
        existingSaved: List<RepeatingPaymentEntity>
    ): List<RepeatingPaymentEntity> {
        val existingStateMap = existingSaved.associateBy { it.id }
        val expenses = successfulTransactions(allTransactions)
            .filter { it.type == TransactionType.EXPENSE && it.amountPaise > 0L }

        val detected = mutableListOf<RepeatingPaymentEntity>()
        val groupedByMerchant = expenses.groupBy { MoneyUtils.normalizeMerchant(it.merchant) }

        for ((normKey, txList) in groupedByMerchant) {
            if (normKey == "unknown" || txList.size < 2) continue
            val sorted = txList.sortedBy { it.timestamp }
            val latest = sorted.last()
            val prev = sorted[sorted.size - 2]

            val avgAmount = sorted.map { it.amountPaise }.average().toLong()
            if (avgAmount <= 0L) continue

            // Check if amounts are within ±15% of average or latest/previous
            val diffRatio = abs(latest.amountPaise - prev.amountPaise).toDouble() / prev.amountPaise.coerceAtLeast(1L).toDouble()
            if (diffRatio > 0.18) continue

            val gapMillis = abs(latest.timestamp - prev.timestamp)
            val gapDays = (gapMillis / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(1)

            // Match typical subscription / rent / utility cadences (Weekly ~7d, Biweekly ~14d, Monthly ~25-35d, Yearly ~355-375d)
            val normalizedGapDays = when (gapDays) {
                in 5..9 -> 7
                in 12..16 -> 14
                in 24..36 -> 30
                in 80..100 -> 90
                in 350..380 -> 365
                else -> if (sorted.size >= 3) gapDays else continue
            }

            val nextExpected = latest.timestamp + (normalizedGapDays.toLong() * 24L * 60L * 60L * 1000L)
            val priorState = existingStateMap[normKey]?.state ?: RepeatingState.SUGGESTED

            detected.add(
                RepeatingPaymentEntity(
                    id = normKey,
                    merchant = latest.merchant,
                    category = latest.category,
                    usualAmountPaise = latest.amountPaise,
                    usualGapDays = normalizedGapDays,
                    nextExpectedTimestamp = nextExpected,
                    state = priorState
                )
            )
        }
        return detected.sortedBy { it.nextExpectedTimestamp }
    }

    /**
     * Computes monthly budget status per category and flags 80% and 100% thresholds.
     */
    fun computeBudgetStatuses(
        budgets: List<BudgetEntity>,
        categories: List<CategoryEntity>,
        thisMonthTransactions: List<TransactionEntity>
    ): List<BudgetStatusItem> {
        val colorMap = categories.associate { it.name to it.colorHex }
        val expensesByCategory = successfulTransactions(thisMonthTransactions)
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amountPaise } }

        return budgets
            .filter { it.monthlyLimitPaise > 0L }
            .map { budget ->
                val spent = expensesByCategory[budget.categoryName] ?: 0L
                val pct = ((spent * 100L) / budget.monthlyLimitPaise).toInt()
                BudgetStatusItem(
                    categoryName = budget.categoryName,
                    colorHex = colorMap[budget.categoryName] ?: DefaultCategories.defaultColorForName(budget.categoryName),
                    limitPaise = budget.monthlyLimitPaise,
                    spentPaise = spent,
                    usagePercent = pct,
                    isWarning80 = pct in 80..99,
                    isExceeded100 = pct >= 100
                )
            }
            .sortedByDescending { it.usagePercent }
    }

    /**
     * Builds last 6 months Income vs Expense bar items.
     */
    fun buildMonthlyIncomeExpenseBars(
        allTransactions: List<TransactionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<MonthlyIncomeExpenseBar> {
        val valid = successfulTransactions(allTransactions)
        val fmt = SimpleDateFormat("MMM", Locale.US)
        return (5 downTo 0).map { offset ->
            val (start, end) = getMonthBounds(nowMillis, -offset)
            val monthTxs = valid.filter { it.timestamp in start..end }
            val earned = monthTxs.filter { it.type == TransactionType.EARNING }.sumOf { it.amountPaise }
            val spent = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }
            MonthlyIncomeExpenseBar(
                monthLabel = fmt.format(Date(start)),
                earnedPaise = earned,
                spentPaise = spent
            )
        }
    }

    /**
     * Builds Line Chart points for Daily (last 7 days), Weekly (last 6 weeks), or Monthly (last 6 months).
     */
    fun buildLineChartPoints(
        allTransactions: List<TransactionEntity>,
        granularity: String, // "DAILY", "WEEKLY", "MONTHLY"
        nowMillis: Long = System.currentTimeMillis()
    ): List<LineChartPoint> {
        val validExpenses = successfulTransactions(allTransactions)
            .filter { it.type == TransactionType.EXPENSE }

        return when (granularity) {
            "DAILY" -> {
                val fmt = SimpleDateFormat("EEE", Locale.US)
                (6 downTo 0).map { dayOffset ->
                    val (start, end) = getDayBounds(nowMillis, -dayOffset)
                    val sum = validExpenses.filter { it.timestamp in start..end }.sumOf { it.amountPaise }
                    LineChartPoint(label = fmt.format(Date(start)), timestamp = start, amountPaise = sum)
                }
            }
            "WEEKLY" -> {
                val fmt = SimpleDateFormat("dd MMM", Locale.US)
                (5 downTo 0).map { weekOffset ->
                    val (start, end) = getWeekBounds(nowMillis, -weekOffset)
                    val sum = validExpenses.filter { it.timestamp in start..end }.sumOf { it.amountPaise }
                    LineChartPoint(label = fmt.format(Date(start)), timestamp = start, amountPaise = sum)
                }
            }
            else -> {
                val fmt = SimpleDateFormat("MMM", Locale.US)
                (5 downTo 0).map { monthOffset ->
                    val (start, end) = getMonthBounds(nowMillis, -monthOffset)
                    val sum = validExpenses.filter { it.timestamp in start..end }.sumOf { it.amountPaise }
                    LineChartPoint(label = fmt.format(Date(start)), timestamp = start, amountPaise = sum)
                }
            }
        }
    }

    fun getDayBounds(anchorMillis: Long, dayOffset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = anchorMillis
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return Pair(start, cal.timeInMillis)
    }

    fun getWeekBounds(anchorMillis: Long, weekOffset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = anchorMillis
            firstDayOfWeek = Calendar.MONDAY
            add(Calendar.WEEK_OF_YEAR, weekOffset)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return Pair(start, cal.timeInMillis)
    }

    fun getMonthBounds(anchorMillis: Long, monthOffset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = anchorMillis
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
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

    fun getYearBounds(anchorMillis: Long, yearOffset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = anchorMillis
            add(Calendar.YEAR, yearOffset)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.MONTH, Calendar.DECEMBER)
        cal.set(Calendar.DAY_OF_MONTH, 31)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return Pair(start, cal.timeInMillis)
    }
}
