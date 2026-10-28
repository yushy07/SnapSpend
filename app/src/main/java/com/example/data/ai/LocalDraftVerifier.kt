package com.example.data.ai

import com.example.data.local.CardBillHandlingMode
import com.example.data.local.CategoryEntity
import com.example.data.local.DefaultCategories
import com.example.data.local.MerchantMemoryEntity
import com.example.data.local.TransactionType
import com.example.data.rules.DraftTransaction
import com.example.util.MoneyUtils
import java.util.Locale

/**
 * Local verification and deterministic rule enforcement for AI and Fast-Path drafts (Part 2.5 & Part 3.4).
 *
 * 1. Verifies that [DraftTransaction.amountPaise] actually appears in the on-device text (formatted as
 *    `1234`, `1,234`, `1234.50`, `1,234.50`, etc.). If not, flags `amountVerified = false` ("CHECK AMOUNT")
 *    and attaches `regexSuggestedAmountPaise` if the local regex found a different amount.
 * 2. Drops `referenceNumber` if its digits do not appear in the on-device text.
 * 3. Flags `dateSuspicious = true` ("CHECK DATE") if the date is in the future or more than 5 years old.
 * 4. Overrides the category with Merchant Memory if the merchant is known, or [IndianBrandDictionary] if
 *    the category is `"Other"` or invalid.
 * 5. Applies deterministic whole-word section rules in strict priority order:
 *    - Priority 1: `ATM`, `CASH WDL`, `ATW`, `ATM WDL` -> `TRANSFER`, `paymentMode = "CASH"`, `isAtmWithdrawal = true`
 *      (unless it is an ATM fee/charge).
 *    - Priority 2: `SELF TRANSFER`, `OWN ACCOUNT`, `TO SELF`, `CC AUTOPAY` -> `TRANSFER`.
 *    - Priority 3: `CRED CLUB`, `CRED CARD`, `CC BILL`, `CARD PAYMENT`, `BILLDESK CC`, `SBI CARD`,
 *      `HDFC CC`, `ICICI CC`, `AXIS CC` -> applies user's [CardBillHandlingMode] (`ASK` sets `isCardBillCheck = true`,
 *      `TRANSFER` sets `TRANSFER`, `EXPENSE` sets `EXPENSE`).
 */
object LocalDraftVerifier {

    private val ATM_RULE_REGEX = Regex(
        """\b(?:ATM\s+WDL|CASH\s+WDL|ATM\s+CASH|CASH\s+WITHDRAWAL|ATM|ATW)\b""",
        RegexOption.IGNORE_CASE
    )

    private val ATM_FEE_REGEX = Regex(
        """\b(?:FEE|FEES|CHG|CHARGE|CHARGES|TAX|GST|PENALTY|SMS\s+CHG|ANNUAL\s+FEE|MAINTENANCE)\b""",
        RegexOption.IGNORE_CASE
    )

    private val SELF_TRANSFER_RULE_REGEX = Regex(
        """\b(?:SELF\s+TRANSFER|OWN\s+ACCOUNT|TO\s+SELF|CC\s+AUTOPAY)\b""",
        RegexOption.IGNORE_CASE
    )

    private val CARD_BILL_RULE_REGEX = Regex(
        """\b(?:CRED\s+CLUB|CRED\s+CARD|CC\s+BILL|CARD\s+PAYMENT|BILLDESK\s+CC|SBI\s+CARD|HDFC\s+CC|ICICI\s+CC|AXIS\s+CC|CREDIT\s+CARD\s+BILL)\b""",
        RegexOption.IGNORE_CASE
    )

    private const val FIVE_YEARS_MILLIS = 5L * 365L * 24L * 60L * 60L * 1000L

    fun verifyAndEnrichDraft(
        draft: DraftTransaction,
        onDeviceText: String,
        regexAnchors: DetectedUpiAnchors?,
        categories: List<CategoryEntity>,
        merchantMemories: List<MerchantMemoryEntity>,
        cardBillMode: CardBillHandlingMode = CardBillHandlingMode.ASK,
        nowMillis: Long = System.currentTimeMillis()
    ): DraftTransaction {
        // 1. Verify amount against on-device text
        val hasText = onDeviceText.isNotBlank()
        val amountAppearsInText = if (!hasText) {
            true // Vision fallback on image with 0 OCR text cannot check OCR text string
        } else {
            doesAmountAppearInText(draft.amountPaise, onDeviceText)
        }

        val regexAmount = regexAnchors?.amountPaise?.takeIf { it > 0L && it != draft.amountPaise }

        // 2. Verify referenceNumber appears in on-device text
        val verifiedRef = draft.referenceNumber?.takeIf { ref ->
            val cleanedRef = MoneyUtils.normalizeReference(ref)
            if (cleanedRef == null) {
                false
            } else if (!hasText) {
                true
            } else {
                val strippedText = onDeviceText.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
                strippedText.contains(cleanedRef)
            }
        }

        // 3. Check date bounds (future or > 5 years old)
        val endOfTodayMillis = nowMillis + (12L * 60L * 60L * 1000L)
        val isDateSuspicious = if (!draft.dateFound) {
            false // Already flagged by dateFound = false
        } else {
            draft.timestamp > endOfTodayMillis || (nowMillis - draft.timestamp) > FIVE_YEARS_MILLIS
        }

        // 4. Category verification & Merchant Memory override
        val validCategoryNames = (categories.map { it.name } + DefaultCategories.ALL_DEFAULTS.map { it.name } + "Transfers").toSet()
        val normalizedMerchant = MoneyUtils.normalizeMerchant(draft.merchant)
        val memoryMatch = merchantMemories.firstOrNull {
            it.normalizedMerchant.equals(normalizedMerchant, ignoreCase = true) ||
                it.displayMerchant.equals(draft.merchant.trim(), ignoreCase = true)
        }

        var resolvedCategory = when {
            memoryMatch != null && validCategoryNames.any { it.equals(memoryMatch.categoryName, ignoreCase = true) } ->
                memoryMatch.categoryName
            else -> {
                val brandMatch = IndianBrandDictionary.matchCategory(
                    merchantText = draft.merchant,
                    rawText = draft.rawMerchantText,
                    allowedCategories = categories
                )
                when {
                    brandMatch != null && (draft.category.equals("Other", ignoreCase = true) || draft.category !in validCategoryNames) ->
                        brandMatch
                    validCategoryNames.any { it.equals(draft.category, ignoreCase = true) } ->
                        validCategoryNames.first { it.equals(draft.category, ignoreCase = true) }
                    brandMatch != null -> brandMatch
                    draft.type == TransactionType.EARNING -> "Other Income"
                    draft.type == TransactionType.TRANSFER -> "Transfers"
                    else -> "Other"
                }
            }
        }

        // 5. Apply deterministic whole-word section rules in strict priority order
        val combinedContext = "${draft.merchant} ${draft.rawMerchantText} ${draft.note}".trim()
        var resolvedType = draft.type
        var resolvedPaymentMode = draft.paymentMode
        var resolvedIsAtm = draft.isAtmWithdrawal
        var resolvedIsCardBillCheck = draft.isCardBillCheck

        if (isAtmWithdrawalContext(combinedContext) || draft.isAtmWithdrawal) {
            // Priority 1: ATM / CASH WDL / ATW / ATM WDL
            resolvedType = TransactionType.TRANSFER
            resolvedPaymentMode = "CASH"
            resolvedIsAtm = true
            resolvedCategory = "Transfers"
            resolvedIsCardBillCheck = false
        } else if (isSelfTransferContext(combinedContext)) {
            // Priority 2: SELF TRANSFER / OWN ACCOUNT / TO SELF / CC AUTOPAY
            resolvedType = TransactionType.TRANSFER
            resolvedCategory = "Transfers"
            resolvedIsCardBillCheck = false
        } else if (isCardBillContext(combinedContext)) {
            // Priority 3: CRED CLUB / CRED CARD / CC BILL / CARD PAYMENT / etc.
            when (cardBillMode) {
                CardBillHandlingMode.ASK -> {
                    resolvedType = TransactionType.TRANSFER
                    resolvedCategory = "Transfers"
                    resolvedIsCardBillCheck = true
                }
                CardBillHandlingMode.TRANSFER -> {
                    resolvedType = TransactionType.TRANSFER
                    resolvedCategory = "Transfers"
                    resolvedIsCardBillCheck = false
                }
                CardBillHandlingMode.EXPENSE -> {
                    resolvedType = TransactionType.EXPENSE
                    if (resolvedCategory == "Transfers") {
                        resolvedCategory = "Bills & Utilities"
                    }
                    resolvedIsCardBillCheck = false
                }
            }
        }

        return draft.copy(
            type = resolvedType,
            category = resolvedCategory,
            paymentMode = resolvedPaymentMode,
            isAtmWithdrawal = resolvedIsAtm,
            referenceNumber = verifiedRef,
            appliedMerchantMemory = memoryMatch != null || draft.appliedMerchantMemory,
            amountVerified = amountAppearsInText,
            regexSuggestedAmountPaise = if (!amountAppearsInText) regexAmount else null,
            dateSuspicious = isDateSuspicious,
            isCardBillCheck = resolvedIsCardBillCheck
        )
    }

    fun isAtmWithdrawalContext(text: String): Boolean {
        if (text.isBlank()) return false
        if (ATM_FEE_REGEX.containsMatchIn(text)) return false
        return ATM_RULE_REGEX.containsMatchIn(text)
    }

    fun isSelfTransferContext(text: String): Boolean {
        if (text.isBlank()) return false
        return SELF_TRANSFER_RULE_REGEX.containsMatchIn(text)
    }

    fun isCardBillContext(text: String): Boolean {
        if (text.isBlank()) return false
        return CARD_BILL_RULE_REGEX.containsMatchIn(text)
    }

    /**
     * Checks whether [amountPaise] appears in [text] in any standard Indian/Western number representation:
     * e.g., for 123450L (₹1,234.50): "1234.50", "1,234.50"
     * e.g., for 125000L (₹1,250): "1250", "1,250", "1250.00", "1,250.00"
     */
    fun doesAmountAppearInText(amountPaise: Long, text: String): Boolean {
        if (amountPaise <= 0L || text.isBlank()) return false
        val rupees = amountPaise / 100L
        val remPaise = amountPaise % 100L

        // Normalize commas and spaces between digits in text for fast substring check
        val commaStrippedText = text.replace(",", "")

        return if (remPaise == 0L) {
            val plainRupees = rupees.toString()
            // Match whole number or .00
            val pattern = Regex("""(?<!\d)${Regex.escape(plainRupees)}(?:\.0{1,2})?(?!\d)""")
            pattern.containsMatchIn(commaStrippedText)
        } else {
            val exactDec = String.format(Locale.US, "%d.%02d", rupees, remPaise)
            val trimmedDec = exactDec.trimEnd('0')
            val pattern = Regex("""(?<!\d)(?:${Regex.escape(exactDec)}|${Regex.escape(trimmedDec)})(?!\d)""")
            pattern.containsMatchIn(commaStrippedText)
        }
    }
}
