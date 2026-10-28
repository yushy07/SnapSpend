package com.example

import com.example.data.ai.ExtractionResult
import com.example.data.ai.GeminiCompactSchema
import com.example.data.ai.IndianBrandDictionary
import com.example.data.ai.LocalDraftVerifier
import com.example.data.ai.StatementLineFilter
import com.example.data.ai.UpiRegexPreProcessor
import com.example.data.local.CardBillHandlingMode
import com.example.data.local.DefaultCategories
import com.example.data.local.MerchantMemoryEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.rules.DraftTransaction
import com.example.data.rules.LocalInsightsAndRulesEngine
import com.example.util.MoneyUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScanningPipelineTest {

    @Test
    fun `C1 - IndianBrandDictionary uses exact DefaultCategories names Transport and Fuel and Health`() {
        assertTrue(IndianBrandDictionary.validateAgainstDefaultCategories())
        assertEquals("Transport & Fuel", IndianBrandDictionary.matchCategory("Uber India Systems"))
        assertEquals("Transport & Fuel", IndianBrandDictionary.matchCategory("Ola Cabs"))
        assertEquals("Health", IndianBrandDictionary.matchCategory("Apollo Pharmacy"))
        assertEquals("Health", IndianBrandDictionary.matchCategory("Tata 1mg"))
    }

    @Test
    fun `C2 - Whole word merchant matching prevents Coca Cola matching ola or Steam matching tea or Vegas matching gas`() {
        assertNull(IndianBrandDictionary.matchCategory("Coca Cola Store"))
        assertNull(IndianBrandDictionary.matchCategory("Steam Games Purchase"))
        assertNull(IndianBrandDictionary.matchCategory("Las Vegas Diner"))

        // Also ensure substring category names like "Other" or "Rent" inside merchant names do not trigger false matches
        assertNull(IndianBrandDictionary.matchCategory("Mother Dairy Booth"))
        assertNull(IndianBrandDictionary.matchCategory("Parenting Books Store"))
    }

    @Test
    fun `C3 - Payee regex matches only at word start and truncates trailing status date and UTR`() = runBlocking {
        val rawText = """
            Paid to Swiggy Instamart Successful on 14 Oct 2025 UTR: 528719384712
            ₹245.00
            Google Pay
        """.trimIndent()
        val analysis = UpiRegexPreProcessor.process(
            rawOcrText = rawText,
            categories = DefaultCategories.ALL_DEFAULTS,
            merchantMemories = emptyList()
        )
        assertEquals("Swiggy Instamart", analysis.detectedAnchors?.merchant)
    }

    @Test
    fun `C4 - Amount ignores promo cashback balance lines and rejects fast path if two different candidate amounts remain`() = runBlocking {
        val singleClearAmountWithNoise = """
            Paid to Zomato
            ₹320.00
            Available Balance: ₹14,520.00
            Cashback won ₹25.00
            Scratch card reward up to ₹100
            Completed
            12 Oct 2025, 8:15 PM
            UTR: 412389012345
        """.trimIndent()
        val analysis1 = UpiRegexPreProcessor.process(
            rawOcrText = singleClearAmountWithNoise,
            categories = DefaultCategories.ALL_DEFAULTS,
            merchantMemories = emptyList()
        )
        assertEquals(32000L, analysis1.detectedAnchors?.amountPaise)
        assertEquals(false, analysis1.detectedAnchors?.hasMultipleDistinctAmounts)
        assertNotNull(analysis1.fastPathDraft)

        val twoAmbiguousAmounts = """
            Paid to Zomato
            ₹320.00
            Total Paid ₹350.00
            Completed
            12 Oct 2025, 8:15 PM
            UTR: 412389012345
        """.trimIndent()
        val analysis2 = UpiRegexPreProcessor.process(
            rawOcrText = twoAmbiguousAmounts,
            categories = DefaultCategories.ALL_DEFAULTS,
            merchantMemories = emptyList()
        )
        assertEquals(true, analysis2.detectedAnchors?.hasMultipleDistinctAmounts)
        assertNull(analysis2.fastPathDraft)
    }

    @Test
    fun `C5 - Multi-row check does not treat normal single GPay PhonePe or Paytm receipt as multi-row`() = runBlocking {
        val normalGpayReceipt = """
            Paid to Blinkit
            ₹489.00
            Completed
            18 Sep 2025 at 7:42 pm
            UPI transaction ID: 526119823746
            To: blinkit.payu@axisbank
            From: user@okhdfcbank
            Google Pay
        """.trimIndent()
        val analysis = UpiRegexPreProcessor.process(
            rawOcrText = normalGpayReceipt,
            categories = DefaultCategories.ALL_DEFAULTS,
            merchantMemories = emptyList()
        )
        assertFalse(analysis.isMultiRowLikely)
        assertNotNull(analysis.fastPathDraft)
    }

    @Test
    fun `C6 - Flexible Indian date parser supports all required formats and rejects future dates`() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 29, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMs = nowCal.timeInMillis

        val d1 = MoneyUtils.parseFlexibleIndianDate("28 Oct 2026", nowMs)
        assertTrue(d1.dateFound)
        assertFalse(d1.rejectedFutureDate)

        val d2 = MoneyUtils.parseFlexibleIndianDate("Oct 28, 2026", nowMs)
        assertTrue(d2.dateFound)

        val d3 = MoneyUtils.parseFlexibleIndianDate("28 October 2026 at 9:42 am", nowMs)
        assertTrue(d3.dateFound)
        assertNotNull(d3.time24h)

        val d4 = MoneyUtils.parseFlexibleIndianDate("28 Oct", nowMs)
        assertTrue(d4.dateFound)

        val d5 = MoneyUtils.parseFlexibleIndianDate("28/10/26", nowMs)
        assertTrue(d5.dateFound)

        val d6 = MoneyUtils.parseFlexibleIndianDate("28-10-2026", nowMs)
        assertTrue(d6.dateFound)

        // Future date should be rejected
        val futureDate = MoneyUtils.parseFlexibleIndianDate("30 Nov 2027", nowMs)
        assertFalse(futureDate.dateFound)
        assertTrue(futureDate.rejectedFutureDate)

        // Numeric date normalization before sending to AI
        val normalized = MoneyUtils.normalizeNumericDatesInTextForAi("Txn on 04/10/2026 for Rs 120.00")
        assertTrue(normalized.contains("4 Oct 2026") || normalized.contains("04 Oct 2026"))
    }

    @Test
    fun `C7 - Missing date marks dateFound false and prevents silent Today fallback in AI schema`() {
        val parsedUnknown = MoneyUtils.parseFlexibleIndianDate("unknown")
        assertFalse(parsedUnknown.dateFound)

        val compactJson = """
            {
              "readable": true,
              "items": [
                {"m": "Chai Point", "a": 12000, "d": "unknown", "t": "EXPENSE", "p": "GPay", "s": "SUCCESS", "c": "c1"}
              ]
            }
        """.trimIndent()
        val (idMap, _) = GeminiCompactSchema.buildCategoryIdLegend(DefaultCategories.ALL_DEFAULTS.map { it.name })
        val parsed = GeminiCompactSchema.parseCompactOrLegacyJson(
            jsonString = compactJson,
            source = TransactionSource.SCREENSHOT,
            allowedCategories = DefaultCategories.ALL_DEFAULTS.map { it.name },
            categoryIdMap = idMap
        )
        assertTrue(parsed is ExtractionResult.Success)
        val success = parsed as ExtractionResult.Success
        assertEquals(1, success.drafts.size)
        assertFalse(success.drafts.first().dateFound)
        assertFalse(success.drafts.first().isVerified)
    }

    @Test
    fun `C8 - Direction only marks EARNING on explicit phrases and CRED does not match CREDITED`() = runBlocking {
        val expenseWithCreditedNoise = """
            Paid to Swiggy
            ₹210.00
            Amount credited to merchant account
            Completed
            10 Oct 2025
            UTR: 512345678901
        """.trimIndent()
        val analysisExpense = UpiRegexPreProcessor.process(
            rawOcrText = expenseWithCreditedNoise,
            categories = DefaultCategories.ALL_DEFAULTS,
            merchantMemories = emptyList()
        )
        assertEquals(TransactionType.EXPENSE, analysisExpense.detectedAnchors?.type)
        assertNotEquals("CRED", analysisExpense.detectedAnchors?.paymentApp)

        val explicitEarning = """
            Received from Rahul Sharma
            Credited to your account
            ₹1,500.00
            Completed
            10 Oct 2025
            UTR: 512345678902
        """.trimIndent()
        val analysisEarning = UpiRegexPreProcessor.process(
            rawOcrText = explicitEarning,
            categories = DefaultCategories.ALL_DEFAULTS,
            merchantMemories = emptyList()
        )
        assertEquals(TransactionType.EARNING, analysisEarning.detectedAnchors?.type)
    }

    @Test
    fun `C9 - Fast path triggers only when amount merchant date SUCCESS and UTR or known merchant are present`() = runBlocking {
        val unknownMerchantNoUtr = """
            Paid to Local Kirana Shop XYZ
            ₹150.00
            Completed
            12 Oct 2025
        """.trimIndent()
        val analysisNoFastPath = UpiRegexPreProcessor.process(
            rawOcrText = unknownMerchantNoUtr,
            categories = DefaultCategories.ALL_DEFAULTS,
            merchantMemories = emptyList()
        )
        assertNull(analysisNoFastPath.fastPathDraft)

        // When added to MerchantMemory, fast path succeeds even without UTR
        val memory = listOf(
            MerchantMemoryEntity(
                normalizedMerchant = "local kirana shop xyz",
                displayMerchant = "Local Kirana Shop XYZ",
                categoryName = "Groceries"
            )
        )
        val analysisWithMemory = UpiRegexPreProcessor.process(
            rawOcrText = unknownMerchantNoUtr,
            categories = DefaultCategories.ALL_DEFAULTS,
            merchantMemories = memory
        )
        assertNotNull(analysisWithMemory.fastPathDraft)
        assertEquals("Groceries", analysisWithMemory.fastPathDraft?.category)
    }

    @Test
    fun `StatementLineFilter keeps transaction rows and wrapped narrations while dropping headers and balances`() {
        val rawStatement = """
            HDFC BANK LIMITED
            Page 1 of 3
            IFSC Code: HDFC0001234
            Opening Balance: ₹50,000.00
            12/10/2025 UPI-SWIGGY-ORDER123 ₹340.00
              BANGALORE IN REF 528192837182
            14/10/2025 ATM WDL HSR LAYOUT ₹2,000.00
            Closing Balance: ₹47,660.00
            Terms and Conditions apply
        """.trimIndent()
        val filtered = StatementLineFilter.filterStatementLines(rawStatement)
        assertEquals(2, filtered.keptTransactionRowCount)
        assertTrue(filtered.filteredText.contains("SWIGGY"))
        assertTrue(filtered.filteredText.contains("BANGALORE IN REF"))
        assertFalse(filtered.filteredText.contains("Opening Balance"))
        assertFalse(filtered.filteredText.contains("IFSC Code"))
        assertTrue(filtered.postFilterTokens <= filtered.preFilterTokens)
    }

    @Test
    fun `LocalDraftVerifier enforces ATM Self Transfer and Card Bill priority rules and amount verification`() {
        val ocrText = "14 Oct 2025 ATM CASH WDL ₹2,500.00 UTR 519283746512"
        val rawAtmDraft = DraftTransaction(
            timestamp = System.currentTimeMillis() - 86_400_000L,
            hasExplicitTime = false,
            amountPaise = 250000L,
            type = TransactionType.EXPENSE,
            merchant = "ATM CASH WDL",
            category = "Other",
            paymentApp = "HDFC",
            status = TransactionStatus.SUCCESS,
            referenceNumber = "519283746512",
            note = "",
            source = TransactionSource.SCREENSHOT
        )
        val verifiedAtm = LocalDraftVerifier.verifyAndEnrichDraft(
            draft = rawAtmDraft,
            onDeviceText = ocrText,
            regexAnchors = null,
            categories = DefaultCategories.ALL_DEFAULTS,
            merchantMemories = emptyList(),
            cardBillMode = CardBillHandlingMode.ASK
        )
        assertEquals(TransactionType.TRANSFER, verifiedAtm.type)
        assertEquals("CASH", verifiedAtm.paymentMode)
        assertTrue(verifiedAtm.isAtmWithdrawal)
        assertTrue(verifiedAtm.isVerified)

        // Card Bill rule in ASK mode flags isCardBillCheck = true
        val cardBillOcr = "15 Oct 2025 Paid to CRED CLUB ₹12,400.00"
        val rawCardBill = DraftTransaction(
            timestamp = System.currentTimeMillis() - 86_400_000L,
            hasExplicitTime = false,
            amountPaise = 1240000L,
            type = TransactionType.EXPENSE,
            merchant = "CRED CLUB",
            category = "Bills & Utilities",
            paymentApp = "GPay",
            status = TransactionStatus.SUCCESS,
            referenceNumber = "999999999999", // Hallucinated UTR not in OCR text
            note = "",
            source = TransactionSource.SCREENSHOT
        )
        val verifiedCardBill = LocalDraftVerifier.verifyAndEnrichDraft(
            draft = rawCardBill,
            onDeviceText = cardBillOcr,
            regexAnchors = null,
            categories = DefaultCategories.ALL_DEFAULTS,
            merchantMemories = emptyList(),
            cardBillMode = CardBillHandlingMode.ASK
        )
        assertTrue(verifiedCardBill.isCardBillCheck)
        assertNull(verifiedCardBill.referenceNumber) // Dropped hallucinated UTR
        assertFalse(verifiedCardBill.isVerified) // Requires user check because isCardBillCheck = true
    }

    @Test
    fun `Top 5 Spending Places returns empty list when filtered range has zero transactions`() {
        val emptyRangeTxs = emptyList<TransactionEntity>()
        val topPlaces = LocalInsightsAndRulesEngine.buildTop5SpendingPlaces(emptyRangeTxs)
        assertTrue(topPlaces.isEmpty())
    }

    private fun assertNotEquals(unexpected: String, actual: String?) {
        assertFalse("Expected value to not equal '$unexpected', but was '$actual'", unexpected == actual)
    }
}
