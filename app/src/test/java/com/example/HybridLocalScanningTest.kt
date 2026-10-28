package com.example

import com.example.data.ai.BankStatementParserRegistry
import com.example.data.ai.LocalDocumentExtractor
import com.example.data.ai.SbiStatementRowParser
import com.example.data.ai.StatementCoPilotMerger
import com.example.data.ai.UpiRegexPreProcessor
import com.example.data.local.CardBillHandlingMode
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.data.local.MerchantMemoryEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridLocalScanningTest {

    @Test
    fun testStripStatusBarNoise() {
        val noisyText = """
            09:42 AM
            45.2 KB/s  VoLTE  85%
            Paid to Blue Tokai Coffee
            ₹420.00
            Completed • 28 Oct 2026
            UPI Ref: 431988271645
            Powered by UPI
        """.trimIndent()

        val cleaned = UpiRegexPreProcessor.stripNoiseAndClutter(noisyText)
        assertTrue("Should remove speed", !cleaned.contains("KB/s"))
        assertTrue("Should remove battery %", !cleaned.contains("85%"))
        assertTrue("Should remove VoLTE", !cleaned.contains("VoLTE"))
        assertTrue("Should retain Blue Tokai", cleaned.contains("Blue Tokai Coffee"))
        assertTrue("Should retain amount", cleaned.contains("₹420.00"))
    }

    @Test
    fun testUpiRegexAnchorExtraction() {
        val receiptText = """
            Paid to UPI/987654/SWIGGY INSTAMART/BLR
            ₹450.50
            Payment Successful
            28 Oct 2026, 08:30 PM
            UPI transaction ID: 987654321012
            Google Pay
        """.trimIndent()

        val anchors = UpiRegexPreProcessor.extractAnchors(receiptText)
        assertNotNull(anchors)
        assertEquals(45050L, anchors?.amountPaise)
        assertEquals("Swiggy Instamart", anchors?.merchant)
        assertEquals("987654321012", anchors?.utrOrRef)
        assertEquals("GPay", anchors?.paymentApp)
        assertEquals(TransactionType.EXPENSE, anchors?.type)
        assertEquals("2026-10-28", anchors?.dateIso)
    }

    @Test
    fun test0ApiFastPathWithKnownMerchantMemory() = runBlocking {
        val ocrText = """
            Paid to Swiggy Instamart
            ₹320.00
            Payment Successful
            UPI transaction ID: 112233445566
        """.trimIndent()

        val categories = listOf(
            CategoryEntity(id = 1, name = "Groceries", type = CategoryType.EXPENSE, colorHex = "#65D46E", iconName = "shopping_basket", isDefault = true)
        )
        val memories = listOf(
            MerchantMemoryEntity(normalizedMerchant = "swiggy instamart", displayMerchant = "Swiggy Instamart", categoryName = "Groceries")
        )

        val result = UpiRegexPreProcessor.process(
            rawOcrText = ocrText,
            categories = categories,
            merchantMemories = memories,
            source = TransactionSource.SCREENSHOT
        )

        assertNotNull("Should create fast path draft locally in 0ms without Gemini", result.fastPathDraft)
        assertEquals("Swiggy Instamart", result.fastPathDraft?.merchant)
        assertEquals(32000L, result.fastPathDraft?.amountPaise)
        assertEquals("Groceries", result.fastPathDraft?.category)
        assertEquals(0.95f, result.fastPathDraft?.confidence ?: 0f, 0.01f)
    }

    @Test
    fun testDetectMultiRowStatement() {
        val statementText = """
            Date        Description                Debit      Credit     Balance
            10/10/2026  UPI/Swiggy/5512            ₹350.00               ₹14,500.00
            11/10/2026  UPI/Uber/8812              ₹180.00               ₹14,320.00
            12/10/2026  SALARY CREDIT                         ₹50,000.00 ₹64,320.00
        """.trimIndent()

        val isMulti = UpiRegexPreProcessor.detectIfMultiRowStatement(statementText)
        assertTrue("Statement with multiple amounts should be identified as multi-row", isMulti)
    }

    @Test
    fun testQuickCashFastParserMultiItem() {
        val categories = listOf(
            CategoryEntity(id = 1, name = "Food & Dining", type = CategoryType.EXPENSE, colorHex = "#FF6F59", iconName = "restaurant", isDefault = true),
            CategoryEntity(id = 2, name = "Transport & Fuel", type = CategoryType.EXPENSE, colorHex = "#8FE3F0", iconName = "directions_car", isDefault = true)
        )
        val drafts = com.example.data.ai.QuickCashFastParser.parseFastDrafts(
            rawInput = "20 chai, 50 auto, 140 maggi",
            categories = categories,
            merchantMemories = emptyList(),
            defaultType = TransactionType.EXPENSE,
            nowMillis = 1730000000000L
        )
        assertNotNull("Should parse multi-item Quick Cash note locally in <20ms", drafts)
        assertEquals(3, drafts?.size)
        assertEquals(2000L, drafts?.get(0)?.amountPaise)
        assertEquals("Chai", drafts?.get(0)?.merchant)
        assertEquals("Food & Dining", drafts?.get(0)?.category)
        assertEquals("CASH", drafts?.get(0)?.paymentMode)

        assertEquals(5000L, drafts?.get(1)?.amountPaise)
        assertEquals("Auto", drafts?.get(1)?.merchant)
        assertEquals("Transport & Fuel", drafts?.get(1)?.category)

        assertEquals(14000L, drafts?.get(2)?.amountPaise)
        assertEquals("Maggi", drafts?.get(2)?.merchant)
        assertEquals("Food & Dining", drafts?.get(2)?.category)
    }

    @Test
    fun testAtmWithdrawalDoubleCountingPrevention() {
        val atmTx = com.example.data.local.TransactionEntity(
            id = 1L,
            timestamp = 1730000000000L,
            hasExplicitTime = true,
            amountPaise = 200000L, // ₹2,000
            type = TransactionType.EXPENSE,
            merchant = "ATM WDL HDFC",
            category = "Other",
            paymentApp = "HDFC Bank",
            referenceNumber = "ATW123",
            status = com.example.data.local.TransactionStatus.SUCCESS,
            note = "ATM cash withdrawal",
            source = TransactionSource.PDF,
            duplicateKey = "ref:atw123",
            paymentMode = "CASH",
            isAtmWithdrawal = true
        )
        val cashSpendTx = atmTx.copy(
            id = 2L,
            amountPaise = 5000L,
            merchant = "Auto",
            category = "Transport & Fuel",
            paymentApp = "Cash",
            isAtmWithdrawal = false
        )

        assertTrue("ATM withdrawal must be detected", com.example.data.rules.LocalInsightsAndRulesEngine.isAtmCashWithdrawalText("ATM WDL HDFC ATW"))
        assertTrue("ATM withdrawal must NOT be counted as Spent", !com.example.data.rules.LocalInsightsAndRulesEngine.isCountableExpense(atmTx))
        assertTrue("Regular cash expense MUST be counted as Spent", com.example.data.rules.LocalInsightsAndRulesEngine.isCountableExpense(cashSpendTx))
        assertEquals(200000L, com.example.data.rules.LocalInsightsAndRulesEngine.computeCashWalletDeltaForTransaction(atmTx))
        assertEquals(-5000L, com.example.data.rules.LocalInsightsAndRulesEngine.computeCashWalletDeltaForTransaction(cashSpendTx))
    }

    @Test
    fun testSbiTabularStatementDeterministicParsing() {
        val sbiRawText = """
            Statement From : 25-08-2026 to 28-10-2026
            Value Date   Post Date   Details   Ref No/Cheque No   Debit   Credit   Balance
            25/08/2026   25/08/2026   DEP TFR UPI/CR/100705244374/BINOD KU/PUNB/100705244374 AT 06945 CITY BRANCH   -   5,000.00   5,000.00
            25/08/2026   25/08/2026   WDL TFR SBY2B000213127815028 O/F SB GENERAL INSURANCE AT 06945 CITY BRANCH   500.00   -   4,500.00
            25/08/2026   25/08/2026   DEP TFR UPI/CR/623701124438/BINOD KU/PUNB/8811075674 AT 06945 CITY BRANCH   -   11.00   4,511.00
            29/08/2026   29/08/2026   DEP TFR UPI/CR/114667104104/BINOD KU/PUNB/8811075674 AT 06945 CITY BRANCH   -   211.00   4,722.00
            31/08/2026   31/08/2026   WDL TFR UPI/DR/624468600184/JAYA PR/SBIN/8811075674 AT 06945 CITY BRANCH   11.00   -   4,711.00
            31/08/2026   31/08/2026   DEP TFR UPI/CR/114674341048/ONE97 CO/utib/paytm-4351 AT 06945 CITY BRANCH   -   2.00   4,713.00
            31/08/2026   31/08/2026   WDL TFR UPI/DR/114668723342/Jio/YESB/jioupi/908581 AT 06945 CITY BRANCH   39.00   -   4,674.00
            31/08/2026   31/08/2026   WDL TFR UPI/DR/114521011116/Jio/YESB/jioupi/908581 AT 06945 CITY BRANCH   29.00   -   4,645.00
            31/08/2026   31/08/2026   DEP TFR UPI/CR/115166012434/ONE97 CO/utib/paytm-4351 AT 06945 CITY BRANCH   -   3.00   4,648.00
            31/08/2026   31/08/2026   DEP TFR UPI/CR/115161448206/ONE97 CO/utib/paytm-4351 AT 06945 CITY BRANCH   -   5.00   4,653.00
            01/09/2026   01/09/2026   WDL TFR UPI/DR/624510017044/JAI MATA/SBIN/985921 AT 06945 CITY BRANCH   60.00   -   4,593.00
            01/09/2026   01/09/2026   DEP TFR UPI/CR/114580361244/ONE97 CO/utib/paytm-4351 AT 06945 CITY BRANCH   -   1.00   4,594.00
            02/09/2026   02/09/2026   WDL TFR UPI/DR/114707243104/Indu shop/YESB/480436066/Sw AT 06945 CITY BRANCH   18.00   -   4,576.00
            02/09/2026   02/09/2026   WDL TFR UPI/DR/624619220102/JAI MATA/SBIN/985921 AT 06945 CITY BRANCH   60.00   -   4,516.00
            02/09/2026   02/09/2026   DEP TFR UPI/CR/114705031244/ONE97 CO/utib/paytm-4351 AT 06945 CITY BRANCH   -   2.00   4,518.00
            05/09/2026   05/09/2026   DEP TFR UPI/CR/115014412234/UTTAM K/SBIN/utt-17700 AT 06945 CITY BRANCH   -   1,770.00   6,288.00
            14/09/2026   14/09/2026   WDL TFR UPI/DR/625880034444/RELIANCE/HDFC/reliance-retail AT 06945 CITY BRANCH   196.83   -   6,091.17
            14/09/2026   14/09/2026   DEP TFR UPI/CR/114889423576/ONE97 CO/utib/paytm-4351 AT 06945 CITY BRANCH   -   4.00   6,095.17
        """.trimIndent()

        val matchingParser = BankStatementParserRegistry.findMatchingParser(sbiRawText)
        assertNotNull("Should find matching SBI statement parser", matchingParser)
        assertEquals("SBI", matchingParser?.bankId)

        val drafts = matchingParser!!.parse(sbiRawText)
        assertEquals(18, drafts.size)

        // 1st row: Binod Ku +₹5,000.00
        val d0 = drafts[0]
        assertEquals(500000L, d0.amountPaise)
        assertEquals(TransactionType.EARNING, d0.type)
        assertEquals("Binod Ku", d0.merchant)
        assertEquals("Received from People", d0.category)
        assertEquals("100705244374", d0.referenceNumber)
        assertTrue("Draft must be verified", d0.isVerified)

        // 2nd row: SB General Insurance -₹500.00
        val d1 = drafts[1]
        assertEquals(50000L, d1.amountPaise)
        assertEquals(TransactionType.EXPENSE, d1.type)
        assertEquals("SBI General Insurance", d1.merchant)
        assertEquals("Health", d1.category)
        assertTrue("Draft must be verified", d1.isVerified)

        // 7th row (index 6): Jio -₹39.00
        val d6 = drafts[6]
        assertEquals(3900L, d6.amountPaise)
        assertEquals(TransactionType.EXPENSE, d6.type)
        assertEquals("Jio", d6.merchant)
        assertEquals("Bills & Utilities", d6.category)
        assertEquals("114668723342", d6.referenceNumber)
        assertTrue("Draft must be verified", d6.isVerified)

        // 17th row (index 16): Reliance -₹196.83
        val d16 = drafts[16]
        assertEquals(19683L, d16.amountPaise)
        assertEquals(TransactionType.EXPENSE, d16.type)
        assertEquals("Reliance", d16.merchant)
        assertEquals("Shopping", d16.category)
        assertEquals("625880034444", d16.referenceNumber)
        assertTrue("Draft must be verified", d16.isVerified)

        // Check running balance math verification on all consecutive rows
        drafts.forEach { draft ->
            assertTrue("Every consecutive row with math check must be verified", draft.isVerified)
            assertEquals(0.99f, draft.confidence, 0.01f)
        }
    }

    @Test
    fun testStatementCoPilotAiMergerPolicy() {
        val sbiRawText = """
            Value Date   Post Date   Details   Ref No/Cheque No   Debit   Credit   Balance
            25/08/2026   25/08/2026   DEP TFR UPI/CR/100705244374/BINOD KU/PUNB/100705244374 AT 06945 CITY BRANCH   -   5,000.00   5,000.00
            25/08/2026   25/08/2026   WDL TFR SBY2B000213127815028 O/F SB GENERAL INSURANCE AT 06945 CITY BRANCH   500.00   -   4,500.00
            14/09/2026   14/09/2026   WDL TFR UPI/DR/625880034444/RELIANCE/HDFC/reliance-retail AT 06945 CITY BRANCH   196.83   -   4,303.17
        """.trimIndent()

        val localDrafts = SbiStatementRowParser.parse(sbiRawText)
        assertEquals(3, localDrafts.size)

        // Simulate Gemini 3.8-Flash AI enriched drafts with human-friendly merchant titles and categories
        val aiEnrichedDrafts = listOf(
            localDrafts[0].copy(
                merchant = "Binod Kumar (Transfer)",
                category = "Received from People"
            ),
            localDrafts[1].copy(
                merchant = "SBI General Insurance Policy",
                category = "Health"
            ),
            localDrafts[2].copy(
                merchant = "Reliance Fresh / Retail",
                category = "Groceries"
            )
        )

        val merged = StatementCoPilotMerger.mergeStatementDraftsWithAiEnrichment(
            localDrafts = localDrafts,
            aiDrafts = aiEnrichedDrafts,
            rawCleanedText = sbiRawText,
            sha256Hash = "test_sha_256",
            regexAnchors = null,
            categories = com.example.data.local.DefaultCategories.ALL_DEFAULTS,
            merchantMemories = emptyList(),
            cardBillMode = CardBillHandlingMode.ASK
        )

        assertEquals(3, merged.size)

        // 1st row: Binod Kumar (Transfer)
        assertEquals(500000L, merged[0].amountPaise)
        assertEquals("Binod Kumar (Transfer)", merged[0].merchant)
        assertEquals("Received from People", merged[0].category)
        assertEquals(TransactionType.EARNING, merged[0].type)
        assertEquals("100705244374", merged[0].referenceNumber)
        assertTrue(merged[0].isVerified)
        assertTrue(merged[0].amountVerified)

        // 2nd row: SBI General Insurance Policy
        assertEquals(50000L, merged[1].amountPaise)
        assertEquals("SBI General Insurance Policy", merged[1].merchant)
        assertEquals("Health", merged[1].category)
        assertEquals(TransactionType.EXPENSE, merged[1].type)
        assertTrue(merged[1].isVerified)

        // 3rd row: Reliance Fresh / Retail
        assertEquals(19683L, merged[2].amountPaise)
        assertEquals("Reliance Fresh / Retail", merged[2].merchant)
        assertEquals("Groceries", merged[2].category)
        assertEquals(TransactionType.EXPENSE, merged[2].type)
        assertEquals("625880034444", merged[2].referenceNumber)
        assertTrue(merged[2].isVerified)
    }
}

