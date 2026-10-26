package com.example

import com.example.data.local.MerchantMemoryEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.rules.DraftTransaction
import com.example.data.rules.LocalInsightsAndRulesEngine
import com.example.data.rules.ReceiptLineItem
import com.example.data.rules.ResolutionMode
import com.example.util.MoneyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun paiseParsingAndFormatting_isExactLongInteger() {
        assertEquals(15050L, MoneyUtils.parseRupeesToPaise("150.50"))
        assertEquals(2000L, MoneyUtils.parseRupeesToPaise("₹20"))
        assertEquals(12500000L, MoneyUtils.parseRupeesToPaise("1,25,000.00"))
        assertEquals("₹150.50", MoneyUtils.formatPaise(15050L))
        assertEquals("₹20", MoneyUtils.formatPaise(2000L))
        assertEquals("₹1,25,000", MoneyUtils.formatPaise(12500000L))
    }

    @Test
    fun duplicateKey_includesHourAndMinuteSoTwoSameDayChaisAreNotDuplicates() {
        val (morningTs, hasMorningTime) = MoneyUtils.parseDateAndTime("2026-10-25", "10:15")
        val (eveningTs, hasEveningTime) = MoneyUtils.parseDateAndTime("2026-10-25", "16:45")

        val key1 = MoneyUtils.buildDuplicateKey(
            referenceNumber = null,
            timestampMillis = morningTs,
            hasExplicitTime = hasMorningTime,
            amountPaise = 2000L, // ₹20
            merchant = "Chai Point"
        )
        val key2 = MoneyUtils.buildDuplicateKey(
            referenceNumber = null,
            timestampMillis = eveningTs,
            hasExplicitTime = hasEveningTime,
            amountPaise = 2000L, // ₹20
            merchant = "Chai Point"
        )

        assertNotEquals(key1, key2)
    }

    @Test
    fun pendingToSuccess_updatesExistingRecordInsteadOfSkipping() {
        val (ts, hasTime) = MoneyUtils.parseDateAndTime("2026-10-25", "14:20")
        val existingPending = TransactionEntity(
            id = 42L,
            timestamp = ts,
            hasExplicitTime = hasTime,
            amountPaise = 45000L,
            type = TransactionType.EXPENSE,
            merchant = "Swiggy",
            category = "Food & Dining",
            paymentApp = "GPay",
            referenceNumber = "UTR99887766",
            status = TransactionStatus.PENDING,
            duplicateKey = "ref:utr99887766"
        )

        val incomingSuccessDraft = DraftTransaction(
            timestamp = ts,
            hasExplicitTime = hasTime,
            amountPaise = 45000L,
            type = TransactionType.EXPENSE,
            merchant = "Swiggy",
            category = "Other",
            paymentApp = "GPay",
            referenceNumber = "UTR99887766",
            status = TransactionStatus.SUCCESS,
            source = TransactionSource.SCREENSHOT
        )

        val memoryMap = mapOf(
            "swiggy" to MerchantMemoryEntity(
                normalizedMerchant = "swiggy",
                displayMerchant = "Swiggy",
                categoryName = "Food & Dining"
            )
        )

        val resolved = LocalInsightsAndRulesEngine.enrichAndResolveDraft(
            rawDraft = incomingSuccessDraft,
            existingTransactions = listOf(existingPending),
            merchantMemoryMap = memoryMap
        )

        assertEquals(ResolutionMode.UPDATE_PENDING_TO_FINAL, resolved.resolutionMode)
        assertEquals(42L, resolved.existingMatchId)
        assertFalse(resolved.isSkipped)
        assertEquals("Food & Dining", resolved.category)
        assertTrue(resolved.appliedMerchantMemory)
    }

    @Test
    fun cleanRawUpiOrBankMerchant_extractsCleanDisplayMerchant() {
        val (clean1, raw1) = MoneyUtils.cleanRawUpiOrBankMerchant("UPI/431988271645/BLUE TOKAI COFFEE/BLR/okaxis")
        assertEquals("Blue Tokai Coffee", clean1)
        assertEquals("UPI/431988271645/BLUE TOKAI COFFEE/BLR/okaxis", raw1)
        assertEquals("blue tokai coffee", MoneyUtils.normalizeMerchant(clean1))

        val (clean2, raw2) = MoneyUtils.cleanRawUpiOrBankMerchant("POS 441928 SWIGGY INSTAMART PVT LTD")
        assertEquals("Swiggy Instamart", clean2)
        assertEquals("POS 441928 SWIGGY INSTAMART PVT LTD", raw2)
        assertEquals("swiggy instamart", MoneyUtils.normalizeMerchant(clean2))
    }

    @Test
    fun draftBillSplitAndItemizedSelection_usesExactLongPaise() {
        val (ts, hasTime) = MoneyUtils.parseDateAndTime("2026-10-25", "20:00")
        val draft = DraftTransaction(
            timestamp = ts,
            hasExplicitTime = hasTime,
            amountPaise = 69500L,
            originalAmountPaise = 69500L,
            type = TransactionType.EXPENSE,
            merchant = "Blue Tokai Coffee",
            category = "Food & Dining",
            paymentApp = "GPay",
            referenceNumber = "431988271645",
            status = TransactionStatus.SUCCESS,
            source = TransactionSource.SCREENSHOT,
            lineItems = listOf(
                ReceiptLineItem("Iced Vietnamese Latte", 1, 28000L, isSelectedForSplit = true),
                ReceiptLineItem("Almond Croissant", 1, 24000L, isSelectedForSplit = true),
                ReceiptLineItem("Pour Over Blend", 1, 17500L, isSelectedForSplit = true)
            )
        )

        val split2 = draft.applyEqualSplit(2)
        assertEquals(34750L, split2.amountPaise)
        assertEquals(2, split2.splitWays)
        assertTrue(split2.note.contains("Split 1/2"))

        // Unselect Almond Croissant (24000L) -> remaining is 28000 + 17500 = 45500L
        val itemized = draft.toggleLineItemSelection(1)
        assertEquals(45500L, itemized.amountPaise)
        assertEquals(0, itemized.splitWays)
        assertTrue(itemized.note.contains("Split:"))
    }
}
