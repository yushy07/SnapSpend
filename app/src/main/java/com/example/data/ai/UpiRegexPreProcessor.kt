package com.example.data.ai

import com.example.data.local.CategoryEntity
import com.example.data.local.DefaultCategories
import com.example.data.local.MerchantMemoryEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.rules.DraftTransaction
import com.example.util.MoneyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DetectedUpiAnchors(
    val amountPaise: Long? = null,
    val amountText: String? = null,
    val merchant: String? = null,
    val rawMerchant: String? = null,
    val utrOrRef: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val status: TransactionStatus = TransactionStatus.SUCCESS,
    val paymentApp: String = "UPI",
    val paymentMode: String = "ONLINE",
    val dateIso: String? = null,
    val time24h: String? = null,
    val confidence: Double = 0.0
)

data class RegexPreProcessResult(
    val cleanedText: String,
    val detectedAnchors: DetectedUpiAnchors?,
    val fastPathDraft: DraftTransaction? = null,
    val isMultiRowLikely: Boolean = false
)

/**
 * High-speed local regex noise filter and 0-API Fast Path engine.
 *
 * Runs strictly on Dispatchers.Default. Parses standard Indian UPI patterns (GPay, PhonePe, Paytm, etc.)
 * in < 50ms and can construct zero-token DraftTransactions locally when high confidence anchors match
 * a known user category or memory.
 */
object UpiRegexPreProcessor {

    suspend fun process(
        rawOcrText: String,
        categories: List<CategoryEntity>,
        merchantMemories: List<MerchantMemoryEntity>,
        source: TransactionSource = TransactionSource.SCREENSHOT,
        forceOffline: Boolean = false
    ): RegexPreProcessResult = withContext(Dispatchers.Default) {
        val cleanedText = stripNoiseAndClutter(rawOcrText)
        val isMultiRow = detectIfMultiRowStatement(cleanedText)
        val anchors = extractAnchors(cleanedText)

        // 0-API Fast Path: If not multi-row, high confidence amount + payee found,
        // and merchant matches a known category in Room DB or standard dictionary.
        var fastPathDraft: DraftTransaction? = null
        if (!isMultiRow && anchors != null && anchors.amountPaise != null && anchors.amountPaise > 0L && !anchors.merchant.isNullOrBlank()) {
            val matchedCategory = findMatchingCategory(
                merchant = anchors.merchant,
                categories = categories,
                memories = merchantMemories
            )

            if (matchedCategory != null || forceOffline) {
                val assignedCategory = matchedCategory ?: "Other"
                val (timestampMillis, hasExplicitTime) = MoneyUtils.parseDateAndTime(anchors.dateIso, anchors.time24h)

                fastPathDraft = DraftTransaction(
                    timestamp = timestampMillis,
                    hasExplicitTime = hasExplicitTime,
                    amountPaise = anchors.amountPaise,
                    type = anchors.type,
                    merchant = anchors.merchant,
                    rawMerchantText = anchors.rawMerchant ?: "",
                    category = assignedCategory,
                    paymentApp = anchors.paymentApp,
                    referenceNumber = anchors.utrOrRef,
                    status = anchors.status,
                    confidence = if (matchedCategory != null) 0.95f else 0.75f,
                    note = if (anchors.utrOrRef != null) "Ref: ${anchors.utrOrRef}" else "",
                    splitWays = 1,
                    source = source,
                    paymentMode = anchors.paymentMode
                )
            }
        }

        RegexPreProcessResult(
            cleanedText = cleanedText,
            detectedAnchors = anchors,
            fastPathDraft = fastPathDraft,
            isMultiRowLikely = isMultiRow
        )
    }

    /**
     * Strips status bar icons, battery, mobile data speed, carrier names, and standard footer text.
     */
    fun stripNoiseAndClutter(text: String): String {
        if (text.isBlank()) return ""
        val lines = text.split("\n")
        val kept = mutableListOf<String>()

        val speedRegex = Regex("""\b\d+(\.\d+)?\s*(KB/s|MB/s|kbps|mbps)(?!\w)""", RegexOption.IGNORE_CASE)
        val batteryRegex = Regex("""\b\d{1,3}%(?!\w)""")
        val carrierRegex = Regex("""\b(VoLTE|VoWiFi|5G|4G|LTE|WiFi|Wi-Fi|Jio|Airtel|Vi|BSNL)\b""", RegexOption.IGNORE_CASE)
        val footerDisclaimer = Regex("""\b(Powered by UPI|Contact PhonePe Support|Google Pay Help|Having issues\?|Report a problem)\b""", RegexOption.IGNORE_CASE)
        val clockRegex = Regex("""^\s*\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?\s*$""")

        for ((idx, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (idx < 2 && (clockRegex.matches(line) || (batteryRegex.matches(line) && line.length <= 5))) {
                continue
            }
            if (footerDisclaimer.containsMatchIn(line)) {
                continue
            }

            var f = speedRegex.replace(line, "")
            f = carrierRegex.replace(f, "")
            f = batteryRegex.replace(f, "")
            f = f.replace(Regex("""\s+"""), " ").trim()

            if (f.isNotEmpty()) {
                kept.add(f)
            }
        }

        return kept.joinToString("\n")
    }

    /**
     * Inspects text for multiple transaction rows (e.g. statements with multiple amounts/dates).
     */
    fun detectIfMultiRowStatement(text: String): Boolean {
        val amountRegex = Regex("""(?:₹|Rs\.?|INR)?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val lines = text.split("\n").filter { it.isNotBlank() }
        var amountCount = 0
        for (l in lines) {
            if (amountRegex.containsMatchIn(l)) {
                amountCount++
            }
        }
        return amountCount >= 3 || (lines.size >= 8 && amountCount >= 2)
    }

    /**
     * Parses standard Indian UPI screenshot anchors from cleaned OCR text.
     */
    fun extractAnchors(text: String): DetectedUpiAnchors? {
        if (text.isBlank()) return null

        val lower = text.lowercase(Locale.US)

        // 1. Amount Extraction
        val amountRegex = Regex("""(?:₹|Rs\.?|INR)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(text)
        val paise = amountMatch?.groupValues?.getOrNull(1)?.let { MoneyUtils.parseRupeesToPaise(it) }
        val amountStr = amountMatch?.groupValues?.getOrNull(1)

        // 2. Payee / Merchant Extraction
        val payeeRegex = Regex("""(?:Paid to|Sent to|To:?|Merchant:?)\s*([A-Za-z0-9\s.&'/\-]{2,60})""", RegexOption.IGNORE_CASE)
        val payeeMatch = payeeRegex.find(text)
        val rawMerchantCandidate = payeeMatch?.groupValues?.getOrNull(1)?.trim()

        val (cleanedMerchant, rawNarration) = if (!rawMerchantCandidate.isNullOrBlank()) {
            MoneyUtils.cleanRawUpiOrBankMerchant(rawMerchantCandidate)
        } else {
            // Check top lines for prominent name if preceded by a greeting or standalone title
            val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val fallbackName = lines.firstOrNull { it.length in 3..35 && !it.contains(Regex("""₹|Rs|UPI|Payment|Success|Debited|Credited""", RegexOption.IGNORE_CASE)) }
            if (fallbackName != null) MoneyUtils.cleanRawUpiOrBankMerchant(fallbackName) else ("" to "")
        }

        // 3. UTR / UPI Ref ID
        val utrRegex = Regex("""(?:UPI transaction ID|UTR|Ref No\.?|Google transaction ID)[:\s]*([0-9]{12})""", RegexOption.IGNORE_CASE)
        val utr = utrRegex.find(text)?.groupValues?.getOrNull(1)

        // 4. Direction & Status
        val isCredit = lower.contains("received from") || lower.contains("credited") || lower.contains("cashback received") || lower.contains("refund")
        val type = if (isCredit) TransactionType.EARNING else TransactionType.EXPENSE

        val isFailed = lower.contains("payment failed") || lower.contains("transaction failed") || lower.contains("declined")
        val isPending = lower.contains("payment processing") || lower.contains("payment pending") || lower.contains("awaiting confirmation")
        val status = when {
            isFailed -> TransactionStatus.FAILED
            isPending -> TransactionStatus.PENDING
            else -> TransactionStatus.SUCCESS
        }

        // 5. Payment App
        val paymentApp = when {
            lower.contains("google pay") || lower.contains("gpay") -> "GPay"
            lower.contains("phonepe") -> "PhonePe"
            lower.contains("paytm") -> "Paytm"
            lower.contains("cred") -> "CRED"
            lower.contains("bhim") -> "BHIM"
            lower.contains("amazon pay") -> "Amazon Pay"
            lower.contains("hdfc") -> "HDFC Bank"
            lower.contains("sbi") -> "SBI"
            lower.contains("icici") -> "ICICI Bank"
            else -> "UPI"
        }

        // 6. Date & Time
        val dateRegex = Regex("""\b(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{4})\b""", RegexOption.IGNORE_CASE)
        val numericDateRegex = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\b""")
        val timeRegex = Regex("""\b(\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?)\b""")

        var dateIso: String? = null
        val dateMatch = dateRegex.find(text)
        if (dateMatch != null) {
            try {
                val parsed = SimpleDateFormat("d MMM yyyy", Locale.US).parse(dateMatch.groupValues[1])
                if (parsed != null) dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed)
            } catch (_: Exception) {}
        }
        if (dateIso == null) {
            val numMatch = numericDateRegex.find(text)
            if (numMatch != null) {
                try {
                    val raw = numMatch.groupValues[1].replace('/', '-')
                    val parsed = SimpleDateFormat("dd-MM-yyyy", Locale.US).parse(raw)
                    if (parsed != null) dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed)
                } catch (_: Exception) {}
            }
        }

        val time24h = timeRegex.find(text)?.let { m ->
            val rawTime = m.groupValues[1].trim()
            try {
                if (rawTime.contains(Regex("""(?i)am|pm"""))) {
                    val parsed = SimpleDateFormat("hh:mm a", Locale.US).parse(rawTime)
                    if (parsed != null) SimpleDateFormat("HH:mm", Locale.US).format(parsed) else null
                } else {
                    rawTime
                }
            } catch (_: Exception) { null }
        }

        val confidence = when {
            paise != null && cleanedMerchant.isNotBlank() && utr != null -> 0.98
            paise != null && cleanedMerchant.isNotBlank() -> 0.88
            paise != null -> 0.65
            else -> 0.30
        }

        return DetectedUpiAnchors(
            amountPaise = paise,
            amountText = amountStr,
            merchant = cleanedMerchant.ifBlank { null },
            rawMerchant = rawNarration.ifBlank { null },
            utrOrRef = utr,
            type = type,
            status = status,
            paymentApp = paymentApp,
            paymentMode = "ONLINE",
            dateIso = dateIso,
            time24h = time24h,
            confidence = confidence
        )
    }

    /**
     * Matches a merchant against user memories, categories, or default dictionary rules.
     */
    fun findMatchingCategory(
        merchant: String,
        categories: List<CategoryEntity>,
        memories: List<MerchantMemoryEntity>
    ): String? {
        val trimmed = merchant.trim()
        if (trimmed.isEmpty()) return null

        // 1. Direct user merchant memory
        val memory = memories.firstOrNull {
            it.normalizedMerchant.equals(trimmed.lowercase(Locale.US), ignoreCase = true) ||
            it.displayMerchant.equals(trimmed, ignoreCase = true)
        }
        if (memory != null && categories.any { it.name.equals(memory.categoryName, ignoreCase = true) }) {
            return memory.categoryName
        }

        // 2. Keyword check against existing categories
        val lower = trimmed.lowercase(Locale.US)
        for (cat in categories) {
            if (lower.contains(cat.name.lowercase(Locale.US))) {
                return cat.name
            }
        }

        // 3. Common Indian merchants dictionary
        val defaultMatch = when {
            lower.contains("swiggy") || lower.contains("zomato") || lower.contains("eats") ||
            lower.contains("cafe") || lower.contains("coffee") || lower.contains("restaurant") ||
            lower.contains("chai") || lower.contains("tea") || lower.contains("mcdonald") ||
            lower.contains("burger") || lower.contains("pizza") || lower.contains("starbucks") -> "Food & Dining"

            lower.contains("zepto") || lower.contains("blinkit") || lower.contains("instamart") ||
            lower.contains("bigbasket") || lower.contains("supermarket") || lower.contains("mart") ||
            lower.contains("grocery") -> "Groceries"

            lower.contains("uber") || lower.contains("ola") || lower.contains("rapido") ||
            lower.contains("metro") || lower.contains("irctc") || lower.contains("petrol") ||
            lower.contains("fuel") || lower.contains("indian oil") || lower.contains("bpcl") ||
            lower.contains("hpcl") -> "Transportation"

            lower.contains("amazon") || lower.contains("flipkart") || lower.contains("myntra") ||
            lower.contains("meesho") || lower.contains("nykaa") || lower.contains("zara") ||
            lower.contains("h&m") -> "Shopping"

            lower.contains("bescom") || lower.contains("electricity") || lower.contains("airtel") ||
            lower.contains("jio") || lower.contains("broadband") || lower.contains("recharge") ||
            lower.contains("cylinder") || lower.contains("gas") -> "Bills & Utilities"

            lower.contains("bookmyshow") || lower.contains("pvr") || lower.contains("inox") ||
            lower.contains("netflix") || lower.contains("spotify") || lower.contains("prime") -> "Entertainment"

            lower.contains("pharmacy") || lower.contains("apollo") || lower.contains("1mg") ||
            lower.contains("medplus") || lower.contains("hospital") || lower.contains("clinic") -> "Health & Medical"

            else -> null
        }

        if (defaultMatch != null && categories.any { it.name.equals(defaultMatch, ignoreCase = true) }) {
            return defaultMatch
        }

        return null
    }
}
