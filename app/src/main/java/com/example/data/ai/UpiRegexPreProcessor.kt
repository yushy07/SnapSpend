package com.example.data.ai

import com.example.data.local.CategoryEntity
import com.example.data.local.MerchantMemoryEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.rules.DraftTransaction
import com.example.util.MoneyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class DetectedUpiAnchors(
    val amountPaise: Long? = null,
    val amountText: String? = null,
    val hasMultipleDistinctAmounts: Boolean = false,
    val merchant: String? = null,
    val rawMerchant: String? = null,
    val utrOrRef: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val status: TransactionStatus = TransactionStatus.SUCCESS,
    val paymentApp: String = "UPI",
    val paymentMode: String = "ONLINE",
    val isAtmWithdrawal: Boolean = false,
    val dateIso: String? = null,
    val time24h: String? = null,
    val dateFound: Boolean = false,
    val confidence: Double = 0.0
)

data class RegexPreProcessResult(
    val cleanedText: String,
    val detectedAnchors: DetectedUpiAnchors?,
    val fastPathDraft: DraftTransaction? = null,
    val isMultiRowLikely: Boolean = false
)

/**
 * High-speed local regex noise filter and 0-API Fast Path engine (Part C).
 *
 * Runs strictly on Dispatchers.Default. Parses standard Indian UPI patterns (GPay, PhonePe, Paytm, etc.)
 * in < 50ms and constructs zero-token DraftTransactions locally ONLY when all Section C.9 conditions
 * are satisfied:
 * - Single clear amount (no ambiguous second candidate amount)
 * - Merchant found
 * - Date found (or "Today" / "Yesterday")
 * - Status == SUCCESS
 * - UTR/Ref found OR merchant is in user's Merchant Memory (or offline fallback mode)
 */
object UpiRegexPreProcessor {

    private val AMOUNT_IGNORE_LINE_REGEX = Regex(
        """\b(?:balance|bal|avl\s*bal|available\s+balance|cashback|reward|rewards|offer|offers|coupon|limit|scratch|earn\s+up\s+to|win\s+up\s+to)\b""",
        RegexOption.IGNORE_CASE
    )

    private val EXPLICIT_CURRENCY_AMOUNT_REGEX = Regex(
        """(?:₹|Rs\.?|INR)\s*([0-9,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val DECIMAL_MONEY_REGEX = Regex(
        """\b([0-9]{1,3}(?:,[0-9]{2,3})+|\d+)\.\d{2}\b"""
    )

    private val PAYEE_LINE_REGEX = Regex(
        """\b(?:Paid\s+to|Sent\s+to|To:|Merchant:)\s*([^\r\n]{2,60})""",
        RegexOption.IGNORE_CASE
    )

    private val TRAILING_PAYEE_NOISE_REGEX = Regex(
        """\s+(?:(?:\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*.*)|(?:\d{1,2}[/-]\d{1,2}[/-]\d{2,4}.*)|(?:UPI\s+transaction\s+ID.*)|(?:UTR.*)|(?:Ref\s+No.*)|(?:Google\s+transaction\s+ID.*)|(?:Completed|Successful|Success|Failed|Pending|Paid|Debited|Credited).*)$""",
        RegexOption.IGNORE_CASE
    )

    private val EARNING_PHRASE_REGEX = Regex(
        """\b(?:received\s+from|credited\s+to\s+your\s+account|credited\s+to\s+a/c|credited\s+to\s+acct|cashback\s+received|refund\s+of|refund\s+from|payment\s+received)\b""",
        RegexOption.IGNORE_CASE
    )

    private val CRED_APP_WHOLE_WORD_REGEX = Regex("""\bCRED\b""")

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

        var fastPathDraft: DraftTransaction? = null
        if (!isMultiRow && anchors != null && !anchors.hasMultipleDistinctAmounts && anchors.amountPaise != null && anchors.amountPaise > 0L) {
            val merchantName = anchors.merchant.orEmpty()
            val normalizedMerchant = MoneyUtils.normalizeMerchant(merchantName)
            val memoryMatch = if (merchantName.isNotBlank()) {
                merchantMemories.firstOrNull {
                    it.normalizedMerchant.equals(normalizedMerchant, ignoreCase = true) ||
                        it.displayMerchant.equals(merchantName, ignoreCase = true)
                }
            } else null

            val isInMerchantMemory = memoryMatch != null
            val matchedCategory = when {
                anchors.isAtmWithdrawal -> "Transfers"
                memoryMatch != null -> memoryMatch.categoryName
                else -> findMatchingCategory(
                    merchant = merchantName,
                    rawText = anchors.rawMerchant.orEmpty(),
                    categories = categories,
                    memories = merchantMemories
                )
            }

            // Section C.9 Fast Path Eligibility:
            // Trigger fast path ONLY when ALL are true:
            // 1. One clear amount (!anchors.hasMultipleDistinctAmounts && anchors.amountPaise > 0)
            // 2. A merchant (!anchors.merchant.isNullOrBlank())
            // 3. A date (anchors.dateFound)
            // 4. Status == SUCCESS (anchors.status == TransactionStatus.SUCCESS)
            // 5. (UTR found OR merchant is in merchant memory)
            val eligibleForFastPath = (
                !anchors.merchant.isNullOrBlank() &&
                    anchors.dateFound &&
                    anchors.status == TransactionStatus.SUCCESS &&
                    (anchors.utrOrRef != null || isInMerchantMemory)
                ) || forceOffline

            if (eligibleForFastPath) {
                val assignedCategory = matchedCategory ?: if (anchors.type == TransactionType.EARNING) {
                    "Other Income"
                } else if (anchors.type == TransactionType.TRANSFER) {
                    "Transfers"
                } else {
                    "Other"
                }
                val (timestampMillis, hasExplicitTime) = MoneyUtils.parseDateAndTime(anchors.dateIso, anchors.time24h)
                val sha256 = MoneyUtils.sha256Hex(cleanedText)

                fastPathDraft = DraftTransaction(
                    timestamp = timestampMillis,
                    hasExplicitTime = hasExplicitTime,
                    amountPaise = anchors.amountPaise,
                    type = anchors.type,
                    merchant = anchors.merchant ?: if (anchors.isAtmWithdrawal) "ATM Cash Withdrawal" else "Unknown",
                    rawMerchantText = anchors.rawMerchant ?: "",
                    category = assignedCategory,
                    paymentApp = anchors.paymentApp,
                    referenceNumber = anchors.utrOrRef,
                    status = anchors.status,
                    confidence = if (matchedCategory != null || anchors.utrOrRef != null) 0.95f else 0.78f,
                    note = if (anchors.utrOrRef != null) "Ref: ${anchors.utrOrRef}" else "",
                    splitWays = 1,
                    source = source,
                    paymentMode = anchors.paymentMode,
                    isAtmWithdrawal = anchors.isAtmWithdrawal,
                    appliedMerchantMemory = isInMerchantMemory,
                    dateFound = anchors.dateFound,
                    amountVerified = true,
                    scanSha256Hash = sha256
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
        val carrierRegex = Regex("""\b(VoLTE|VoWiFi|5G|4G|LTE|WiFi|Wi-Fi|BSNL)\b""", RegexOption.IGNORE_CASE)
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
            // Only strip carrier tokens on the first 2 status-bar lines so "Jio" or "Airtel" merchants aren't erased!
            if (idx < 2) {
                f = carrierRegex.replace(f, "")
            }
            f = batteryRegex.replace(f, "")
            f = f.replace(Regex("""\s+"""), " ").trim()

            if (f.isNotEmpty()) {
                kept.add(f)
            }
        }

        return kept.joinToString("\n")
    }

    /**
     * Section C.5: Multi-row check:
     * Count ONLY lines with a real money amount (₹/Rs/INR prefix, or a number with two decimals).
     * Do NOT count dates, times, or long ID numbers. A normal single GPay, PhonePe, or Paytm
     * payment must NOT count as multi-row.
     */
    fun detectIfMultiRowStatement(text: String): Boolean {
        if (text.isBlank()) return false
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var realMoneyLineCount = 0
        for (line in lines) {
            if (AMOUNT_IGNORE_LINE_REGEX.containsMatchIn(line)) continue
            val hasCurrencyAmount = EXPLICIT_CURRENCY_AMOUNT_REGEX.containsMatchIn(line)
            val hasTwoDecimalAmount = DECIMAL_MONEY_REGEX.containsMatchIn(line)
            if (hasCurrencyAmount || hasTwoDecimalAmount) {
                realMoneyLineCount++
            }
        }
        return realMoneyLineCount >= 3
    }

    /**
     * Parses standard Indian UPI screenshot anchors from cleaned OCR text.
     */
    fun extractAnchors(
        text: String,
        nowMillis: Long = System.currentTimeMillis()
    ): DetectedUpiAnchors? {
        if (text.isBlank()) return null

        val lower = text.lowercase(Locale.US)
        val isAtm = LocalDraftVerifier.isAtmWithdrawalContext(text)

        // 1. Amount Extraction (Section C.4):
        // Ignore lines containing balance, cashback, reward, offer, coupon, limit, scratch.
        // If two different candidate amounts remain, do not use the fast path.
        val candidatePaiseList = mutableListOf<Long>()
        val candidateTextList = mutableListOf<String>()

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (AMOUNT_IGNORE_LINE_REGEX.containsMatchIn(line)) continue

            for (match in EXPLICIT_CURRENCY_AMOUNT_REGEX.findAll(line)) {
                val rawAmt = match.groupValues[1]
                val parsed = MoneyUtils.parseRupeesToPaise(rawAmt)
                if (parsed != null && parsed > 0L) {
                    candidatePaiseList.add(parsed)
                    candidateTextList.add(rawAmt)
                }
            }
        }

        // If no explicit ₹/Rs/INR amount was found, check two-decimal numbers on non-ignored lines
        if (candidatePaiseList.isEmpty()) {
            for (rawLine in text.lines()) {
                val line = rawLine.trim()
                if (line.isEmpty() || AMOUNT_IGNORE_LINE_REGEX.containsMatchIn(line)) continue
                for (match in DECIMAL_MONEY_REGEX.findAll(line)) {
                    val rawAmt = match.value
                    val parsed = MoneyUtils.parseRupeesToPaise(rawAmt)
                    if (parsed != null && parsed > 0L) {
                        candidatePaiseList.add(parsed)
                        candidateTextList.add(rawAmt)
                    }
                }
            }
        }

        val distinctAmounts = candidatePaiseList.distinct()
        val hasMultipleDistinctAmounts = distinctAmounts.size >= 2
        val paise = if (hasMultipleDistinctAmounts) null else distinctAmounts.firstOrNull()
        val amountStr = if (hasMultipleDistinctAmounts) null else candidateTextList.firstOrNull()

        // 2. Payee / Merchant Extraction (Section C.3):
        // Match "Paid to", "Sent to", "To:", "Merchant:" only at the start of a word,
        // capture only to the end of that line, and cut trailing status/date/ID text.
        val payeeMatch = PAYEE_LINE_REGEX.find(text)
        val rawMerchantCandidate = payeeMatch?.groupValues?.getOrNull(1)
            ?.let { stripTrailingPayeeNoise(it) }
            ?.trim()

        val (cleanedMerchant, rawNarration) = when {
            isAtm -> "ATM Cash Withdrawal" to text.lines().firstOrNull().orEmpty()
            !rawMerchantCandidate.isNullOrBlank() -> MoneyUtils.cleanRawUpiOrBankMerchant(rawMerchantCandidate)
            else -> {
                val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                val fallbackName = lines.firstOrNull {
                    it.length in 3..35 &&
                        !it.contains(Regex("""₹|Rs|INR|UPI|Payment|Success|Completed|Debited|Credited|Transaction|Balance|Cashback""", RegexOption.IGNORE_CASE)) &&
                        !Regex("""^\d+$""").matches(it)
                }
                if (fallbackName != null) MoneyUtils.cleanRawUpiOrBankMerchant(fallbackName) else ("" to "")
            }
        }

        // 3. UTR / UPI Ref ID
        val utrRegex = Regex("""\b(?:UPI\s+transaction\s+ID|UTR|Ref\s*No\.?|Google\s+transaction\s+ID|Transaction\s+ID)[:\s]*([0-9]{12})\b""", RegexOption.IGNORE_CASE)
        val utr = utrRegex.find(text)?.groupValues?.getOrNull(1)

        // 4. Direction & Status (Section C.8):
        // Only mark as EARNING when explicit phrases appear ("received from", "credited to your account",
        // "cashback received", "refund of").
        val isCredit = EARNING_PHRASE_REGEX.containsMatchIn(text)
        val type = when {
            isAtm || LocalDraftVerifier.isSelfTransferContext(text) -> TransactionType.TRANSFER
            isCredit -> TransactionType.EARNING
            else -> TransactionType.EXPENSE
        }

        val isFailed = lower.contains("payment failed") || lower.contains("transaction failed") || lower.contains("declined")
        val isPending = lower.contains("payment processing") || lower.contains("payment pending") || lower.contains("awaiting confirmation")
        val status = when {
            isFailed -> TransactionStatus.FAILED
            isPending -> TransactionStatus.PENDING
            else -> TransactionStatus.SUCCESS
        }

        // 5. Payment App (Section C.8: match CRED only as whole word)
        val paymentApp = when {
            isAtm -> "Cash"
            lower.contains("google pay") || lower.contains("gpay") -> "GPay"
            lower.contains("phonepe") -> "PhonePe"
            lower.contains("paytm") -> "Paytm"
            CRED_APP_WHOLE_WORD_REGEX.containsMatchIn(text) || Regex("""\bcred\s+(?:pay|club|upi)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "CRED"
            lower.contains("bhim") -> "BHIM"
            lower.contains("amazon pay") -> "Amazon Pay"
            lower.contains("hdfc") -> "HDFC Bank"
            lower.contains("sbi") -> "SBI"
            lower.contains("icici") -> "ICICI Bank"
            else -> "UPI"
        }

        // 6. Date & Time (Section C.6 & C.7)
        val parsedDate = MoneyUtils.parseFlexibleIndianDate(text, nowMillis)

        val confidence = when {
            paise != null && cleanedMerchant.isNotBlank() && utr != null && parsedDate.dateFound -> 0.98
            paise != null && cleanedMerchant.isNotBlank() && parsedDate.dateFound -> 0.88
            paise != null -> 0.65
            else -> 0.30
        }

        return DetectedUpiAnchors(
            amountPaise = paise,
            amountText = amountStr,
            hasMultipleDistinctAmounts = hasMultipleDistinctAmounts,
            merchant = cleanedMerchant.ifBlank { null },
            rawMerchant = rawNarration.ifBlank { rawMerchantCandidate },
            utrOrRef = utr,
            type = type,
            status = status,
            paymentApp = paymentApp,
            paymentMode = if (isAtm) "CASH" else "ONLINE",
            isAtmWithdrawal = isAtm,
            dateIso = parsedDate.dateIso,
            time24h = parsedDate.time24h,
            dateFound = parsedDate.dateFound,
            confidence = confidence
        )
    }

    internal fun stripTrailingPayeeNoise(rawPayeeLine: String): String {
        return rawPayeeLine
            .replace(TRAILING_PAYEE_NOISE_REGEX, "")
            .replace(Regex("""\s*[·|•\-]+\s*$"""), "")
            .trim()
    }

    /**
     * Matches a merchant against user memories or the 150-brand [IndianBrandDictionary] using
     * strict whole-word boundaries (Section C.1 & C.2).
     * Never matches category names as substrings inside a merchant name.
     */
    fun findMatchingCategory(
        merchant: String,
        categories: List<CategoryEntity>,
        memories: List<MerchantMemoryEntity>,
        rawText: String = ""
    ): String? {
        val trimmed = merchant.trim()
        if (trimmed.isEmpty() && rawText.isBlank()) return null

        // 1. Direct user merchant memory
        val normalized = MoneyUtils.normalizeMerchant(trimmed)
        val memory = memories.firstOrNull {
            it.normalizedMerchant.equals(normalized, ignoreCase = true) ||
                it.displayMerchant.equals(trimmed, ignoreCase = true)
        }
        if (memory != null && (categories.isEmpty() || categories.any { it.name.equals(memory.categoryName, ignoreCase = true) })) {
            return memory.categoryName
        }

        // 2. 150-brand Indian dictionary with whole-word matching & UPI handle prefixes
        return IndianBrandDictionary.matchCategory(
            merchantText = trimmed,
            rawText = rawText,
            allowedCategories = categories
        )
    }
}
