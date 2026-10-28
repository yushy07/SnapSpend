package com.example

import com.example.data.ai.LocalDocumentExtractor
import com.example.data.ai.UpiRegexPreProcessor
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
}
