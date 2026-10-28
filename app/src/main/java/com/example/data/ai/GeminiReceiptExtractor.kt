package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.example.data.local.CardBillHandlingMode
import com.example.data.local.CategoryEntity
import com.example.data.local.ConfirmedScanRecord
import com.example.data.local.DefaultCategories
import com.example.data.local.MerchantMemoryEntity
import com.example.data.local.ScanLogEntry
import com.example.data.local.ScanPath
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.rules.DraftTransaction
import com.example.data.rules.ReceiptLineItem
import com.example.util.MoneyUtils
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "GeminiReceiptExtractor"
const val GEMINI_MODEL_NAME = "gemini-3.8-flash"
private const val TEXT_TIMEOUT_MS = 20_000L
private const val TEXT_MAX_ATTEMPTS = 2
private const val PDF_PAGE_CHUNK_SIZE = 2
private const val STATEMENT_ROW_CHUNK_SIZE = 40

enum class AiErrorKind {
    RETRYABLE_NETWORK_OR_503,
    RATE_LIMITED_429,
    FATAL_CLIENT_ERROR
}

sealed interface ExtractionResult {
    data class Success(
        val drafts: List<DraftTransaction>,
        val logEntry: ScanLogEntry? = null,
        val skippedPdfPages: Int = 0
    ) : ExtractionResult

    data class SkippedKnown(
        val existingTransactionId: Long?,
        val amountPaise: Long,
        val merchant: String,
        val dateFormatted: String,
        val logEntry: ScanLogEntry
    ) : ExtractionResult

    data class RateLimited(
        val message: String = "AI limit reached. Queued, will retry later",
        val logEntry: ScanLogEntry? = null
    ) : ExtractionResult

    data class PdfPasswordRequired(
        val uri: Uri,
        val isInvalidPasswordAttempt: Boolean = false
    ) : ExtractionResult

    data class UnreadableOrNoPayments(
        val reason: String,
        val logEntry: ScanLogEntry? = null
    ) : ExtractionResult

    data class Error(
        val message: String,
        val logEntry: ScanLogEntry? = null
    ) : ExtractionResult
}

data class PerFileScanOutcome(
    val uri: Uri,
    val result: ExtractionResult,
    val logEntry: ScanLogEntry
)

/**
 * Shared compact schema builder, error classifier, and JSON parser for both text and vision Gemini calls
 * (Part 3.2 & Part 3.3).
 */
object GeminiCompactSchema {

    fun buildCategoryIdLegend(allowedCategories: List<String>): Pair<Map<String, String>, String> {
        val safe = allowedCategories.ifEmpty {
            DefaultCategories.ALL_DEFAULTS.map { it.name }
        }.distinct()
        val map = LinkedHashMap<String, String>()
        val legendParts = mutableListOf<String>()
        safe.forEachIndexed { index, name ->
            val id = "c${index + 1}"
            map[id] = name
            legendParts.add("$id=$name")
        }
        return map to legendParts.joinToString(", ")
    }

    fun buildCompactSchema(categoryIds: List<String>): Schema {
        val safeIds = categoryIds.ifEmpty { listOf("c1") }
        val lineItemSchema = Schema.obj(
            properties = mapOf(
                "n" to Schema.string(description = "Item name"),
                "q" to Schema.integer(description = "Quantity (default 1)"),
                "a" to Schema.long(description = "Total line price in whole paise")
            )
        )

        val itemSchema = Schema.obj(
            properties = mapOf(
                "m" to Schema.string(description = "Clean human-readable merchant or person name, or 'unknown'"),
                "a" to Schema.long(description = "Exact amount in whole Indian paise (₹150.50 -> 15050). Return 0 if unknown."),
                "d" to Schema.string(description = "Date in YYYY-MM-DD, or 'unknown' if not visible"),
                "tm" to Schema.string(description = "Time in 24h HH:mm, or 'unknown' if not visible"),
                "t" to Schema.enumeration(
                    values = listOf("EXPENSE", "EARNING", "TRANSFER"),
                    description = "EXPENSE=money out, EARNING=money received/refund/cashback, TRANSFER=own account or ATM cash withdrawal"
                ),
                "p" to Schema.string(
                    description = "'CASH' for cash/ATM withdrawal, else app/method name ('GPay','PhonePe','Paytm','CRED','Card','UPI','Bank')"
                ),
                "s" to Schema.enumeration(
                    values = listOf("SUCCESS", "FAILED", "PENDING"),
                    description = "Payment status"
                ),
                "r" to Schema.string(
                    description = "12-digit UPI UTR or bank reference number if visible, else 'unknown'"
                ),
                "c" to Schema.enumeration(
                    values = safeIds,
                    description = "Category ID from the legend"
                ),
                "sp" to Schema.integer(
                    description = "Equal split count if explicitly mentioned (e.g. 'split 2 ways' -> 2), else 1"
                ),
                "li" to Schema.array(
                    items = lineItemSchema,
                    description = "Itemized bill lines if visible, else empty array"
                )
            )
        )

        return Schema.obj(
            properties = mapOf(
                "readable" to Schema.boolean(description = "True if input has at least one readable payment"),
                "reason" to Schema.string(description = "Reason if readable is false"),
                "items" to Schema.array(items = itemSchema, description = "Extracted payments")
            )
        )
    }

    fun classifyAiError(e: Exception): AiErrorKind {
        val msg = (e.message ?: "").lowercase(Locale.US)
        return when {
            msg.contains("429") ||
                msg.contains("resourceexhausted") ||
                msg.contains("resource_exhausted") ||
                msg.contains("quota") ||
                msg.contains("too many requests") ||
                msg.contains("rate limit") -> AiErrorKind.RATE_LIMITED_429

            msg.contains("400") ||
                msg.contains("403") ||
                msg.contains("404") ||
                msg.contains("invalidargument") ||
                msg.contains("permissiondenied") ||
                msg.contains("notfound") ||
                msg.contains("api key not valid") -> AiErrorKind.FATAL_CLIENT_ERROR

            else -> AiErrorKind.RETRYABLE_NETWORK_OR_503
        }
    }

    fun formatFatalErrorMessage(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("400") || msg.contains("InvalidArgument", ignoreCase = true) ->
                "AI rejected the request format (400 Bad Request). Please try another file."
            msg.contains("403") || msg.contains("PermissionDenied", ignoreCase = true) ->
                "AI access denied (403 Forbidden). Please check your Firebase AI configuration."
            msg.contains("404") || msg.contains("NotFound", ignoreCase = true) ->
                "AI model '$GEMINI_MODEL_NAME' was not found (404)."
            else -> e.localizedMessage ?: "Couldn't reach Gemini AI. Tap Try Again or Queue Scan."
        }
    }

    /**
     * Parses both the compact short-key schema (`readable`, `reason`, `items`: `m,a,d,tm,t,p,s,r,c,sp,li`)
     * and the legacy long-key schema (`isReadablePaymentDocument`, `payments`) seamlessly.
     */
    fun parseCompactOrLegacyJson(
        jsonString: String,
        source: TransactionSource,
        allowedCategories: List<String>,
        categoryIdMap: Map<String, String> = emptyMap(),
        defaultPaymentMode: String = "ONLINE",
        defaultCashType: TransactionType? = null,
        missingDateDefaultsToToday: Boolean = false,
        scanSha256Hash: String? = null
    ): ExtractionResult {
        val root = JSONObject(jsonString)
        val isReadable = if (root.has("readable")) {
            root.optBoolean("readable", true)
        } else {
            root.optBoolean("isReadablePaymentDocument", true)
        }
        val unreadableReason = root.optString("reason", root.optString("unreadableReason", ""))
        val itemsArray = root.optJSONArray("items") ?: root.optJSONArray("payments")

        if (!isReadable || itemsArray == null || itemsArray.length() == 0) {
            val msg = unreadableReason.ifBlank {
                "Couldn't read a payment from this input. Make sure it clearly shows a payment or receipt."
            }
            return ExtractionResult.UnreadableOrNoPayments(msg)
        }

        val drafts = mutableListOf<DraftTransaction>()
        for (i in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(i) ?: continue

            val rawDate = item.optString("d", item.optString("dateIso", "unknown")).trim()
            val rawTime = item.optString("tm", item.optString("time24h", "unknown")).trim()

            // Section C.7: If no date is found ("unknown"), mark dateFound = false and preselect today
            // (for typed natural-language notes where missingDateDefaultsToToday = true, dateFound is true).
            val isDateUnknown = rawDate.isBlank() ||
                rawDate.equals("unknown", ignoreCase = true) ||
                rawDate.equals("null", ignoreCase = true)
            val dateFound = !isDateUnknown || missingDateDefaultsToToday
            val (timestampMillis, hasExplicitTime) = if (isDateUnknown) {
                MoneyUtils.parseDateAndTime(MoneyUtils.formatIsoDate(System.currentTimeMillis()), rawTime)
            } else {
                // Also support flexible Indian date strings if AI returned "28 Oct 2026" instead of YYYY-MM-DD
                val isoCandidate = if (Regex("""^\d{4}-\d{2}-\d{2}$""").matches(rawDate)) {
                    rawDate
                } else {
                    MoneyUtils.parseFlexibleIndianDate(rawDate).dateIso ?: rawDate
                }
                MoneyUtils.parseDateAndTime(isoCandidate, rawTime)
            }

            val amountText = item.optString("amountText", "")
            val amountFromText = MoneyUtils.parseRupeesToPaise(amountText)
            val amountFromLong = if (item.has("a")) item.optLong("a", 0L) else item.optLong("amountPaise", 0L)
            val finalAmountPaise = when {
                amountFromText != null && amountFromText > 0L -> amountFromText
                amountFromLong > 0L -> amountFromLong
                else -> 0L
            }

            val rawTypeStr = item.optString("t", item.optString("type", defaultCashType?.name ?: "EXPENSE")).uppercase(Locale.US)
            val normalizedTypeStr = if (rawTypeStr == "INCOME") "EARNING" else rawTypeStr
            val parsedType = runCatching { TransactionType.valueOf(normalizedTypeStr) }
                .getOrDefault(defaultCashType ?: TransactionType.EXPENSE)

            val rawMerchantField = item.optString("m", item.optString("merchant", "unknown")).trim().ifBlank { "unknown" }
            val explicitRawText = item.optString("rawMerchantText", "").trim()
                .takeIf { !it.equals("unknown", ignoreCase = true) }
                .orEmpty()
            val (cleanedMerchant, fallbackRaw) = MoneyUtils.cleanRawUpiOrBankMerchant(rawMerchantField)
            val finalRawMerchant = explicitRawText.ifBlank { fallbackRaw }

            val note = item.optString("note", "").takeIf { !it.equals("unknown", ignoreCase = true) }.orEmpty()
            val rawMethodOrMode = item.optString("p", item.optString("paymentMode", item.optString("paymentApp", defaultPaymentMode))).trim()

            val isAtmWithdrawal = item.optBoolean("isAtmWithdrawal", false) ||
                LocalDraftVerifier.isAtmWithdrawalContext("$rawMerchantField $finalRawMerchant $note $rawMethodOrMode")

            val type = if (isAtmWithdrawal) TransactionType.TRANSFER else parsedType
            val isCashMode = isAtmWithdrawal ||
                defaultPaymentMode.equals("CASH", ignoreCase = true) ||
                rawMethodOrMode.equals("CASH", ignoreCase = true) ||
                item.optString("paymentMode", "").equals("CASH", ignoreCase = true)
            val resolvedPaymentMode = if (isCashMode) "CASH" else "ONLINE"

            val paymentApp = when {
                resolvedPaymentMode == "CASH" -> "Cash"
                rawMethodOrMode.isNotBlank() &&
                    !rawMethodOrMode.equals("ONLINE", ignoreCase = true) &&
                    !rawMethodOrMode.equals("unknown", ignoreCase = true) -> rawMethodOrMode
                else -> item.optString("paymentApp", "UPI").ifBlank { "UPI" }
            }

            val refRaw = item.optString("r", item.optString("referenceNumber", "unknown")).trim()
            val referenceNumber = if (refRaw.equals("unknown", ignoreCase = true) || refRaw.equals("null", ignoreCase = true) || refRaw.isBlank()) {
                null
            } else {
                refRaw
            }

            val statusStr = item.optString("s", item.optString("status", "SUCCESS")).uppercase(Locale.US)
            val status = runCatching { TransactionStatus.valueOf(statusStr) }.getOrDefault(TransactionStatus.SUCCESS)

            val rawCatCode = item.optString("c", item.optString("suggestedCategory", "Other")).trim()
            val mappedCategoryName = categoryIdMap[rawCatCode.lowercase(Locale.US)] ?: rawCatCode
            val validCategory = when {
                isAtmWithdrawal || type == TransactionType.TRANSFER -> "Transfers"
                else -> allowedCategories.firstOrNull { it.equals(mappedCategoryName, ignoreCase = true) }
                    ?: DefaultCategories.ALL_DEFAULTS.firstOrNull { it.name.equals(mappedCategoryName, ignoreCase = true) }?.name
                    ?: if (type == TransactionType.EARNING) "Other Income" else "Other"
            }

            val confidence = item.optDouble("confidence", 0.88).toFloat().coerceIn(0f, 1f)
            val suggestedSplitWays = item.optInt("sp", item.optInt("suggestedSplitWays", 1)).coerceIn(1, 20)

            val lineItemsArray = item.optJSONArray("li") ?: item.optJSONArray("lineItems")
            val parsedLineItems = mutableListOf<ReceiptLineItem>()
            if (lineItemsArray != null) {
                for (j in 0 until lineItemsArray.length()) {
                    val liObj = lineItemsArray.optJSONObject(j) ?: continue
                    val liName = liObj.optString("n", liObj.optString("itemName", "")).trim()
                    if (liName.isEmpty() || liName.equals("unknown", ignoreCase = true)) continue
                    val liQty = liObj.optInt("q", liObj.optInt("quantity", 1)).coerceAtLeast(1)
                    val liPriceText = liObj.optString("priceText", "")
                    val liPaise = MoneyUtils.parseRupeesToPaise(liPriceText)
                        ?: if (liObj.has("a")) liObj.optLong("a", 0L) else liObj.optLong("pricePaise", 0L)
                    if (liPaise > 0L) {
                        parsedLineItems.add(
                            ReceiptLineItem(
                                itemName = liName,
                                quantity = liQty,
                                pricePaise = liPaise,
                                isSelectedForSplit = true
                            )
                        )
                    }
                }
            }

            val baseDraft = DraftTransaction(
                timestamp = timestampMillis,
                hasExplicitTime = hasExplicitTime,
                amountPaise = finalAmountPaise,
                originalAmountPaise = finalAmountPaise,
                type = type,
                merchant = if (isAtmWithdrawal && (cleanedMerchant.isBlank() || cleanedMerchant.equals("unknown", ignoreCase = true))) {
                    "ATM Cash Withdrawal"
                } else {
                    cleanedMerchant
                },
                rawMerchantText = finalRawMerchant,
                category = validCategory,
                paymentApp = paymentApp,
                referenceNumber = referenceNumber,
                status = status,
                note = note,
                source = source,
                confidence = confidence,
                lineItems = parsedLineItems,
                paymentMode = resolvedPaymentMode,
                isAtmWithdrawal = isAtmWithdrawal,
                dateFound = dateFound,
                scanSha256Hash = scanSha256Hash
            )

            val finalDraft = if (suggestedSplitWays > 1) {
                baseDraft.applyEqualSplit(suggestedSplitWays)
            } else {
                baseDraft
            }
            drafts.add(finalDraft)
        }

        return ExtractionResult.Success(drafts)
    }
}

/**
 * Hybrid Local-First receipt, UPI screenshot, multi-page PDF, and natural-language reader.
 */
class GeminiReceiptExtractor(private val context: Context) {

    val localExtractor by lazy { LocalDocumentExtractor(context) }
    val visionFallback by lazy { CompressedVisionFallback(context) }

    fun isDeviceOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Processes a single URI end-to-end (Part 2.1):
     * 1. Native PDF text layer or on-device ML Kit OCR
     * 2. Local regex + `SKIPPED_KNOWN` check (UTR match in Room DB or SHA-256 hash match in DataStore)
     * 3. 0-API Fast Path check (Section C.9)
     * 4. Statement Line Filter + chunked Gemini Text calls (20s timeout, max 2 attempts)
     * 5. Per-file Vision Fallback if image OCR/text failed
     * 6. LocalDraftVerifier verification & deterministic section rules
     */
    suspend fun extractSingleUriWithPipeline(
        uri: Uri,
        categories: List<CategoryEntity>,
        merchantMemories: List<MerchantMemoryEntity>,
        pdfPassword: String? = null,
        cardBillMode: CardBillHandlingMode = CardBillHandlingMode.ASK,
        findExistingByRef: suspend (String) -> TransactionEntity? = { null },
        findConfirmedHash: suspend (String) -> ConfirmedScanRecord? = { null },
        onProgress: (statusText: String) -> Unit = {}
    ): PerFileScanOutcome = withContext(Dispatchers.IO) {
        val totalStart = SystemClock.elapsedRealtime()
        var readMs = 0L
        var ocrMs = 0L
        var regexMs = 0L
        var aiTextMs = 0L
        var visionMs = 0L
        var retries = 0
        var promptTok = 0
        var outputTok = 0
        var preFilterTok = 0
        var postFilterTok = 0

        try {
            // STEP 1: On-Device Extraction (PDFBox native text first, ML Kit OCR fallback)
            onProgress("Reading file on device...")
            val ocrResult = try {
                localExtractor.extractSingleUri(uri, pdfPassword) { readPages, totalPages ->
                    if (totalPages > 1) {
                        onProgress("Read $readPages of $totalPages pages...")
                    }
                }
            } catch (pwdEx: PdfPasswordRequiredException) {
                val totalElapsed = (SystemClock.elapsedRealtime() - totalStart).coerceAtLeast(1L)
                val log = ScanLogEntry(totalMs = totalElapsed, path = ScanPath.FAST_PATH)
                return@withContext PerFileScanOutcome(
                    uri = uri,
                    result = ExtractionResult.PdfPasswordRequired(uri, pwdEx.isInvalidPasswordAttempt),
                    logEntry = log
                )
            }
            readMs = ocrResult.readFileMs
            ocrMs = ocrResult.ocrTextMs

            val source = if (ocrResult.isPdf) TransactionSource.PDF else TransactionSource.SCREENSHOT

            // STEP 2: Local Regex Pre-Processor & SKIPPED_KNOWN Check
            val regexStart = SystemClock.elapsedRealtime()
            val regexResult = UpiRegexPreProcessor.process(
                rawOcrText = ocrResult.fullCleanedText,
                categories = categories,
                merchantMemories = merchantMemories,
                source = source
            )
            regexMs = (SystemClock.elapsedRealtime() - regexStart).coerceAtLeast(1L)

            val cleanedText = regexResult.cleanedText
            val sha256Hash = if (cleanedText.length >= 15) MoneyUtils.sha256Hex(cleanedText) else ""

            // Section D: Check if UTR or SHA-256 hash is already saved before making any network call!
            if (!regexResult.isMultiRowLikely && ocrResult.pages.size <= 1) {
                val refCandidate = regexResult.detectedAnchors?.utrOrRef
                if (!refCandidate.isNullOrBlank()) {
                    val existingTx = findExistingByRef(refCandidate)
                    if (existingTx != null) {
                        val totalElapsed = (SystemClock.elapsedRealtime() - totalStart).coerceAtLeast(1L)
                        val log = ScanLogEntry(
                            readFileMs = readMs,
                            ocrTextMs = ocrMs,
                            regexMs = regexMs,
                            totalMs = totalElapsed,
                            path = ScanPath.SKIPPED_KNOWN
                        )
                        return@withContext PerFileScanOutcome(
                            uri = uri,
                            result = ExtractionResult.SkippedKnown(
                                existingTransactionId = existingTx.id,
                                amountPaise = existingTx.amountPaise,
                                merchant = existingTx.merchant,
                                dateFormatted = MoneyUtils.formatShortDateTime(existingTx.timestamp, existingTx.hasExplicitTime),
                                logEntry = log
                            ),
                            logEntry = log
                        )
                    }
                }
                if (sha256Hash.isNotBlank()) {
                    val confirmedRecord = findConfirmedHash(sha256Hash)
                    if (confirmedRecord != null) {
                        val totalElapsed = (SystemClock.elapsedRealtime() - totalStart).coerceAtLeast(1L)
                        val log = ScanLogEntry(
                            readFileMs = readMs,
                            ocrTextMs = ocrMs,
                            regexMs = regexMs,
                            totalMs = totalElapsed,
                            path = ScanPath.SKIPPED_KNOWN
                        )
                        return@withContext PerFileScanOutcome(
                            uri = uri,
                            result = ExtractionResult.SkippedKnown(
                                existingTransactionId = confirmedRecord.transactionId.takeIf { it > 0L },
                                amountPaise = confirmedRecord.amountPaise,
                                merchant = confirmedRecord.merchant,
                                dateFormatted = confirmedRecord.dateFormatted,
                                logEntry = log
                            ),
                            logEntry = log
                        )
                    }
                }
            }

            // STEP 3: 0-API Fast Path (Section C.9)
            if (regexResult.fastPathDraft != null) {
                val verifiedFastDraft = LocalDraftVerifier.verifyAndEnrichDraft(
                    draft = regexResult.fastPathDraft.copy(scanSha256Hash = sha256Hash.ifBlank { null }),
                    onDeviceText = cleanedText,
                    regexAnchors = regexResult.detectedAnchors,
                    categories = categories,
                    merchantMemories = merchantMemories,
                    cardBillMode = cardBillMode
                )
                val totalElapsed = (SystemClock.elapsedRealtime() - totalStart).coerceAtLeast(1L)
                val log = ScanLogEntry(
                    readFileMs = readMs,
                    ocrTextMs = ocrMs,
                    regexMs = regexMs,
                    totalMs = totalElapsed,
                    path = ScanPath.FAST_PATH
                )
                return@withContext PerFileScanOutcome(
                    uri = uri,
                    result = ExtractionResult.Success(
                        drafts = listOf(verifiedFastDraft),
                        logEntry = log,
                        skippedPdfPages = ocrResult.skippedPdfPages
                    ),
                    logEntry = log
                )
            }

            // Check offline state before attempting Gemini network calls (Part 2.4)
            if (!isDeviceOnline()) {
                val totalElapsed = (SystemClock.elapsedRealtime() - totalStart).coerceAtLeast(1L)
                val log = ScanLogEntry(
                    readFileMs = readMs,
                    ocrTextMs = ocrMs,
                    regexMs = regexMs,
                    totalMs = totalElapsed,
                    path = ScanPath.TEXT_AI
                )
                return@withContext PerFileScanOutcome(
                    uri = uri,
                    result = ExtractionResult.RateLimited("Offline. Scan queued and will retry when online.", log),
                    logEntry = log
                )
            }

            // STEP 4: Text-Only Gemini 3.8-Flash Call (with chunking & StatementLineFilter for multi-row/PDF)
            if (ocrResult.isValidText) {
                val aiStart = SystemClock.elapsedRealtime()
                val textOutcome = if (ocrResult.isPdf && ocrResult.pages.size > 1) {
                    extractPdfPagesInChunks(
                        pages = ocrResult.pages,
                        categories = categories,
                        source = source,
                        onProgress = onProgress
                    )
                } else if (regexResult.isMultiRowLikely) {
                    extractMultiRowStatementText(
                        rawCleanedText = cleanedText,
                        categories = categories,
                        source = source,
                        onProgress = onProgress
                    )
                } else {
                    onProgress("Categorising transaction...")
                    callGeminiTextChunk(
                        textChunk = cleanedText,
                        categories = categories,
                        source = source,
                        scanSha256Hash = sha256Hash.ifBlank { null }
                    )
                }
                aiTextMs = (SystemClock.elapsedRealtime() - aiStart).coerceAtLeast(1L)
                retries += textOutcome.retries
                promptTok += textOutcome.promptTokens
                outputTok += textOutcome.outputTokens
                preFilterTok += textOutcome.preFilterTokens
                postFilterTok += textOutcome.postFilterTokens

                if (textOutcome.result is ExtractionResult.RateLimited) {
                    val totalElapsed = (SystemClock.elapsedRealtime() - totalStart).coerceAtLeast(1L)
                    val log = ScanLogEntry(
                        readFileMs = readMs,
                        ocrTextMs = ocrMs,
                        regexMs = regexMs,
                        geminiTextMs = aiTextMs,
                        totalMs = totalElapsed,
                        path = ScanPath.TEXT_AI,
                        retries = retries,
                        promptTokens = promptTok,
                        outputTokens = outputTok,
                        preFilterTokens = preFilterTok,
                        postFilterTokens = postFilterTok
                    )
                    return@withContext PerFileScanOutcome(uri, textOutcome.result.copy(logEntry = log), log)
                }

                if (textOutcome.result is ExtractionResult.Success && textOutcome.result.drafts.isNotEmpty()) {
                    val verifiedDrafts = textOutcome.result.drafts.map { draft ->
                        LocalDraftVerifier.verifyAndEnrichDraft(
                            draft = draft.copy(
                                missingRowsWarning = textOutcome.missingRowsWarning ?: draft.missingRowsWarning,
                                scanSha256Hash = if (textOutcome.result.drafts.size == 1) sha256Hash.ifBlank { null } else null
                            ),
                            onDeviceText = ocrResult.fullCleanedText,
                            regexAnchors = if (textOutcome.result.drafts.size == 1) regexResult.detectedAnchors else null,
                            categories = categories,
                            merchantMemories = merchantMemories,
                            cardBillMode = cardBillMode
                        )
                    }
                    val totalElapsed = (SystemClock.elapsedRealtime() - totalStart).coerceAtLeast(1L)
                    val log = ScanLogEntry(
                        readFileMs = readMs,
                        ocrTextMs = ocrMs,
                        regexMs = regexMs,
                        geminiTextMs = aiTextMs,
                        totalMs = totalElapsed,
                        path = ScanPath.TEXT_AI,
                        retries = retries,
                        promptTokens = promptTok,
                        outputTokens = outputTok,
                        preFilterTokens = preFilterTok,
                        postFilterTokens = postFilterTok
                    )
                    return@withContext PerFileScanOutcome(
                        uri = uri,
                        result = ExtractionResult.Success(
                            drafts = verifiedDrafts,
                            logEntry = log,
                            skippedPdfPages = ocrResult.skippedPdfPages
                        ),
                        logEntry = log
                    )
                }
            }

            // STEP 5: Compressed Multimodal Vision Fallback (runs on this specific failed file!)
            onProgress("Enhancing & scanning photo...")
            val visionTelemetry = visionFallback.analyzeWithVisionFallbackTelemetry(
                sourceUri = if (!ocrResult.isPdf) uri else null,
                sourceBitmap = ocrResult.fallbackBitmaps.firstOrNull(),
                partialOcrHint = ocrResult.fullCleanedText,
                categories = categories,
                source = source,
                onStatusUpdate = onProgress
            )
            visionMs = visionTelemetry.visionMs
            retries += visionTelemetry.retries
            promptTok += visionTelemetry.promptTokens
            outputTok += visionTelemetry.outputTokens

            val totalElapsed = (SystemClock.elapsedRealtime() - totalStart).coerceAtLeast(1L)
            val log = ScanLogEntry(
                readFileMs = readMs,
                ocrTextMs = ocrMs,
                regexMs = regexMs,
                geminiTextMs = aiTextMs,
                visionFallbackMs = visionMs,
                totalMs = totalElapsed,
                path = ScanPath.VISION_AI,
                retries = retries,
                promptTokens = promptTok,
                outputTokens = outputTok,
                preFilterTokens = preFilterTok,
                postFilterTokens = postFilterTok
            )

            val finalVisionResult = when (val r = visionTelemetry.result) {
                is ExtractionResult.Success -> {
                    val verified = r.drafts.map { draft ->
                        LocalDraftVerifier.verifyAndEnrichDraft(
                            draft = draft.copy(scanSha256Hash = sha256Hash.ifBlank { null }),
                            onDeviceText = ocrResult.fullCleanedText,
                            regexAnchors = regexResult.detectedAnchors,
                            categories = categories,
                            merchantMemories = merchantMemories,
                            cardBillMode = cardBillMode
                        )
                    }
                    ExtractionResult.Success(verified, log, ocrResult.skippedPdfPages)
                }
                is ExtractionResult.RateLimited -> r.copy(logEntry = log)
                is ExtractionResult.UnreadableOrNoPayments -> r.copy(logEntry = log)
                is ExtractionResult.Error -> r.copy(logEntry = log)
                else -> r
            }

            PerFileScanOutcome(uri = uri, result = finalVisionResult, logEntry = log)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Per-file pipeline failed for $uri", e)
            val totalElapsed = (SystemClock.elapsedRealtime() - totalStart).coerceAtLeast(1L)
            val log = ScanLogEntry(
                readFileMs = readMs,
                ocrTextMs = ocrMs,
                regexMs = regexMs,
                geminiTextMs = aiTextMs,
                visionFallbackMs = visionMs,
                totalMs = totalElapsed,
                path = if (visionMs > 0L) ScanPath.VISION_AI else ScanPath.TEXT_AI,
                retries = retries,
                promptTokens = promptTok,
                outputTokens = outputTok
            )
            PerFileScanOutcome(
                uri = uri,
                result = ExtractionResult.Error(
                    e.localizedMessage ?: "Couldn't read this file. Tap Retry.",
                    logEntry = log
                ),
                logEntry = log
            )
        }
    }

    private data class TextCallTelemetry(
        val result: ExtractionResult,
        val retries: Int = 0,
        val promptTokens: Int = 0,
        val outputTokens: Int = 0,
        val preFilterTokens: Int = 0,
        val postFilterTokens: Int = 0,
        val missingRowsWarning: String? = null
    )

    private suspend fun extractPdfPagesInChunks(
        pages: List<ExtractedPage>,
        categories: List<CategoryEntity>,
        source: TransactionSource,
        onProgress: (String) -> Unit
    ): TextCallTelemetry = coroutineScope {
        val pageChunks = pages.chunked(PDF_PAGE_CHUNK_SIZE)
        val semaphore = Semaphore(3)
        val completedChunks = java.util.concurrent.atomic.AtomicInteger(0)

        val outcomes = pageChunks.mapIndexed { chunkIdx, chunkPages ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val rawChunkText = chunkPages.joinToString("\n\n") { it.cleanedText }.trim()
                    val filtered = StatementLineFilter.filterStatementLines(rawChunkText)
                    val outcome = callGeminiTextChunk(
                        textChunk = filtered.filteredText,
                        categories = categories,
                        source = source
                    )
                    val done = completedChunks.incrementAndGet()
                    val readPagesCount = (done * PDF_PAGE_CHUNK_SIZE).coerceAtMost(pages.size)
                    onProgress("Read $readPagesCount of ${pages.size} pages...")
                    outcome.copy(
                        preFilterTokens = filtered.preFilterTokens,
                        postFilterTokens = filtered.postFilterTokens,
                        missingRowsWarning = if (
                            outcome.result is ExtractionResult.Success &&
                            filtered.keptTransactionRowCount >= 3 &&
                            outcome.result.drafts.size < filtered.keptTransactionRowCount
                        ) {
                            "SOME ROWS MAY BE MISSING (${outcome.result.drafts.size} of ~${filtered.keptTransactionRowCount} read)"
                        } else null
                    )
                }
            }
        }.awaitAll()

        combineChunkOutcomes(outcomes)
    }

    private suspend fun extractMultiRowStatementText(
        rawCleanedText: String,
        categories: List<CategoryEntity>,
        source: TransactionSource,
        onProgress: (String) -> Unit
    ): TextCallTelemetry = coroutineScope {
        // Check if a deterministic BankStatementParser matches first (Part 3.7)
        val parser = BankStatementParserRegistry.findMatchingParser(rawCleanedText)
        if (parser != null) {
            val parsedDrafts = parser.parse(rawCleanedText)
            if (parsedDrafts.isNotEmpty()) {
                return@coroutineScope TextCallTelemetry(
                    result = ExtractionResult.Success(parsedDrafts)
                )
            }
        }

        val filtered = StatementLineFilter.filterStatementLines(rawCleanedText)
        val lines = filtered.filteredText.lines().filter { it.isNotBlank() }
        if (lines.size <= STATEMENT_ROW_CHUNK_SIZE) {
            onProgress("Categorising statement rows...")
            val single = callGeminiTextChunk(
                textChunk = filtered.filteredText,
                categories = categories,
                source = source
            )
            val missingWarning = if (
                single.result is ExtractionResult.Success &&
                filtered.keptTransactionRowCount >= 3 &&
                single.result.drafts.size < filtered.keptTransactionRowCount
            ) {
                "SOME ROWS MAY BE MISSING (${single.result.drafts.size} of ~${filtered.keptTransactionRowCount} read)"
            } else null
            return@coroutineScope single.copy(
                preFilterTokens = filtered.preFilterTokens,
                postFilterTokens = filtered.postFilterTokens,
                missingRowsWarning = missingWarning
            )
        }

        val lineChunks = lines.chunked(STATEMENT_ROW_CHUNK_SIZE)
        val semaphore = Semaphore(3)
        val outcomes = lineChunks.mapIndexed { idx, chunkLines ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    onProgress("Categorising statement batch ${idx + 1} of ${lineChunks.size}...")
                    callGeminiTextChunk(
                        textChunk = chunkLines.joinToString("\n"),
                        categories = categories,
                        source = source
                    )
                }
            }
        }.awaitAll()

        val combined = combineChunkOutcomes(outcomes)
        val missingWarning = if (
            combined.result is ExtractionResult.Success &&
            filtered.keptTransactionRowCount >= 3 &&
            combined.result.drafts.size < filtered.keptTransactionRowCount
        ) {
            "SOME ROWS MAY BE MISSING (${combined.result.drafts.size} of ~${filtered.keptTransactionRowCount} read)"
        } else null

        combined.copy(
            preFilterTokens = filtered.preFilterTokens,
            postFilterTokens = filtered.postFilterTokens,
            missingRowsWarning = missingWarning
        )
    }

    private fun combineChunkOutcomes(outcomes: List<TextCallTelemetry>): TextCallTelemetry {
        val allDrafts = mutableListOf<DraftTransaction>()
        var totalRetries = 0
        var totalPromptTok = 0
        var totalOutputTok = 0
        var totalPreTok = 0
        var totalPostTok = 0
        var firstWarning: String? = null
        var sawRateLimit: ExtractionResult.RateLimited? = null
        var firstError: ExtractionResult? = null

        for (o in outcomes) {
            totalRetries += o.retries
            totalPromptTok += o.promptTokens
            totalOutputTok += o.outputTokens
            totalPreTok += o.preFilterTokens
            totalPostTok += o.postFilterTokens
            if (firstWarning == null && o.missingRowsWarning != null) {
                firstWarning = o.missingRowsWarning
            }
            when (val r = o.result) {
                is ExtractionResult.Success -> allDrafts.addAll(r.drafts)
                is ExtractionResult.RateLimited -> if (sawRateLimit == null) sawRateLimit = r
                else -> if (firstError == null) firstError = r
            }
        }

        val finalResult = when {
            allDrafts.isNotEmpty() -> ExtractionResult.Success(allDrafts)
            sawRateLimit != null -> sawRateLimit
            firstError != null -> firstError
            else -> ExtractionResult.UnreadableOrNoPayments("No transactions found in statement.")
        }

        return TextCallTelemetry(
            result = finalResult,
            retries = totalRetries,
            promptTokens = totalPromptTok,
            outputTokens = totalOutputTok,
            preFilterTokens = totalPreTok,
            postFilterTokens = totalPostTok,
            missingRowsWarning = firstWarning
        )
    }

    /**
     * Backward-compatible multi-URI entry point used by background worker or callers that want a single aggregated result.
     * Processes each URI independently with up to 3 concurrent files (Part 2.1).
     */
    suspend fun extractFromUris(
        uris: List<Uri>,
        categories: List<CategoryEntity>,
        merchantMemories: List<MerchantMemoryEntity> = emptyList(),
        onProgress: (currentStep: Int, totalSteps: Int, statusText: String) -> Unit
    ): ExtractionResult = coroutineScope {
        if (uris.isEmpty()) {
            return@coroutineScope ExtractionResult.Error("No file selected.")
        }

        val semaphore = Semaphore(3)
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val outcomes = uris.mapIndexed { idx, uri ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    onProgress(idx + 1, uris.size, "Scanning file ${idx + 1} of ${uris.size}...")
                    val outcome = extractSingleUriWithPipeline(
                        uri = uri,
                        categories = categories,
                        merchantMemories = merchantMemories,
                        onProgress = { msg ->
                            onProgress(completed.get() + 1, uris.size, msg)
                        }
                    )
                    completed.incrementAndGet()
                    outcome
                }
            }
        }.awaitAll()

        val allDrafts = outcomes.flatMap { (it.result as? ExtractionResult.Success)?.drafts.orEmpty() }
        if (allDrafts.isNotEmpty()) {
            ExtractionResult.Success(allDrafts, outcomes.firstOrNull()?.logEntry)
        } else {
            outcomes.firstOrNull()?.result ?: ExtractionResult.Error("Scan failed.")
        }
    }

    /**
     * Ultra-fast text-only Gemini 3.8-Flash request (`thinkingBudget = 0`, `temperature = 0.1f`,
     * 20s timeout, max 2 attempts, compact short-key schema, fixed instructions first, normalized text last).
     */
    suspend fun extractFromCleanedOcrText(
        cleanedOcrText: String,
        categories: List<CategoryEntity>,
        detectedAnchors: DetectedUpiAnchors? = null,
        source: TransactionSource = TransactionSource.SCREENSHOT
    ): ExtractionResult {
        return callGeminiTextChunk(
            textChunk = cleanedOcrText,
            categories = categories,
            source = source
        ).result
    }

    private suspend fun callGeminiTextChunk(
        textChunk: String,
        categories: List<CategoryEntity>,
        source: TransactionSource,
        scanSha256Hash: String? = null
    ): TextCallTelemetry = withContext(Dispatchers.IO) {
        val allowedCategories = categories.map { it.name }.ifEmpty {
            DefaultCategories.ALL_DEFAULTS.map { it.name }
        }.distinct()

        val (categoryIdMap, categoryLegend) = GeminiCompactSchema.buildCategoryIdLegend(allowedCategories)
        val schema = GeminiCompactSchema.buildCompactSchema(categoryIdMap.keys.toList())
        val config = generationConfig {
            temperature = 0.1f
            responseMimeType = "application/json"
            responseSchema = schema
            thinkingConfig = thinkingConfig {
                thinkingBudget = 0
            }
        }

        val model = Firebase.ai.generativeModel(
            modelName = GEMINI_MODEL_NAME,
            generationConfig = config
        )

        val dateNormalizedText = MoneyUtils.normalizeNumericDatesInTextForAi(textChunk)

        // Fixed instructions first, variable text last (Part 3.2). No PRE-DETECTED REGEX HINTS block.
        val prompt = buildString {
            appendLine("You are SnapSpend's Indian UPI receipt, bill & bank statement parser.")
            appendLine("Extract every payment transaction into `items` using the compact JSON schema.")
            appendLine("CATEGORY ID LEGEND ($categoryLegend) -> return the short ID (e.g. c1) in `c`.")
            appendLine("STRICT RULES:")
            appendLine("1. `a` = exact amount in whole Indian paise (e.g. ₹145.50 -> 14550, ₹20 -> 2000).")
            appendLine("2. `m` = clean human-readable merchant or person name (e.g. clean 'UPI/412983/SWIGGY INSTAMART/BLR' -> 'Swiggy Instamart'). If paid to a person's name, choose the 'Sent to People' category ID.")
            appendLine("3. `t` = EXPENSE for money out, EARNING for money received/refunds/cashback, TRANSFER for money moved between the user's own accounts or ATM cash withdrawals (ATM, CASH WDL, ATW).")
            appendLine("4. `p` = 'CASH' for physical cash or ATM cash withdrawals; otherwise the payment app/method ('GPay', 'PhonePe', 'Paytm', 'CRED', 'Card', 'UPI', 'Bank').")
            appendLine("5. `d` = YYYY-MM-DD, or 'unknown' if no date is visible in the text (never guess today's date when missing). `tm` = HH:mm in 24h format, or 'unknown'.")
            appendLine("6. `r` = 12-digit UPI UTR / reference number if visible, else 'unknown'.")
            appendLine("7. Multi-row statements: extract ALL payment rows in the text into `items`.")
            appendLine()
            appendLine("TEXT:")
            appendLine(dateNormalizedText)
        }

        var retriesUsed = 0
        var promptTok = 0
        var outputTok = 0
        var currentDelay = 900L

        for (attempt in 1..TEXT_MAX_ATTEMPTS) {
            try {
                if (attempt > 1) retriesUsed = attempt - 1
                val response = withTimeout(TEXT_TIMEOUT_MS) {
                    model.generateContent(prompt)
                }
                promptTok += response.usageMetadata?.promptTokenCount ?: 0
                outputTok += response.usageMetadata?.candidatesTokenCount ?: 0
                val rawJson = response.text
                if (rawJson.isNullOrBlank()) {
                    throw IllegalStateException("Empty text response from Gemini.")
                }
                val parsed = GeminiCompactSchema.parseCompactOrLegacyJson(
                    jsonString = rawJson,
                    source = source,
                    allowedCategories = allowedCategories,
                    categoryIdMap = categoryIdMap,
                    scanSha256Hash = scanSha256Hash
                )
                return@withContext TextCallTelemetry(
                    result = parsed,
                    retries = retriesUsed,
                    promptTokens = promptTok,
                    outputTokens = outputTok
                )
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                if (attempt >= TEXT_MAX_ATTEMPTS) {
                    return@withContext TextCallTelemetry(
                        result = ExtractionResult.Error("AI reading timed out after 20s. Check your internet or Queue Scan."),
                        retries = retriesUsed,
                        promptTokens = promptTok,
                        outputTokens = outputTok
                    )
                }
                delay(currentDelay)
                currentDelay *= 2L
            } catch (e: Exception) {
                val kind = GeminiCompactSchema.classifyAiError(e)
                if (kind == AiErrorKind.RATE_LIMITED_429) {
                    return@withContext TextCallTelemetry(
                        result = ExtractionResult.RateLimited("AI limit reached. Queued, will retry later"),
                        retries = retriesUsed,
                        promptTokens = promptTok,
                        outputTokens = outputTok
                    )
                }
                if (kind == AiErrorKind.FATAL_CLIENT_ERROR || attempt >= TEXT_MAX_ATTEMPTS) {
                    Log.e(TAG, "callGeminiTextChunk failed", e)
                    return@withContext TextCallTelemetry(
                        result = ExtractionResult.Error(GeminiCompactSchema.formatFatalErrorMessage(e)),
                        retries = retriesUsed,
                        promptTokens = promptTok,
                        outputTokens = outputTok
                    )
                }
                delay(currentDelay)
                currentDelay *= 2L
            }
        }

        TextCallTelemetry(
            result = ExtractionResult.Error("Text extraction failed."),
            retries = retriesUsed,
            promptTokens = promptTok,
            outputTokens = outputTok
        )
    }

    /**
     * Converts a single user-typed, voice-dictated, or pasted payment note into structured
     * DraftTransactions using [GEMINI_MODEL_NAME] (`gemini-3.8-flash`) with the compact short-key schema
     * and records a `NOTE_AI` scan log entry.
     */
    suspend fun extractFromNaturalLanguageText(
        userInput: String,
        categories: List<CategoryEntity>,
        defaultPaymentMode: String = "ONLINE",
        defaultCashType: TransactionType? = null
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val t0 = SystemClock.elapsedRealtime()
        val trimmedInput = userInput.trim()
        if (trimmedInput.isEmpty()) {
            return@withContext ExtractionResult.Error("Please type or speak a payment description first.")
        }

        val allowedCategories = categories.map { it.name }.ifEmpty {
            DefaultCategories.ALL_DEFAULTS.map { it.name }
        }.distinct()

        val (categoryIdMap, categoryLegend) = GeminiCompactSchema.buildCategoryIdLegend(allowedCategories)
        val schema = GeminiCompactSchema.buildCompactSchema(categoryIdMap.keys.toList())
        val config = generationConfig {
            temperature = 0.1f
            responseMimeType = "application/json"
            responseSchema = schema
            thinkingConfig = thinkingConfig {
                thinkingBudget = 0
            }
        }

        val model = Firebase.ai.generativeModel(
            modelName = GEMINI_MODEL_NAME,
            generationConfig = config
        )

        val now = Date()
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val nowTime24h = SimpleDateFormat("HH:mm", Locale.US).format(now)
        val normalizedDefaultMode = if (defaultPaymentMode.equals("CASH", ignoreCase = true)) "CASH" else "ONLINE"
        val defaultTypeHint = defaultCashType?.name ?: "EXPENSE"

        val prompt = buildString {
            appendLine("You are SnapSpend's precision Indian payment note & SMS parser.")
            appendLine("Parse the user's payment note, rough cash log, or pasted bank alert into `items`.")
            appendLine("CATEGORY ID LEGEND ($categoryLegend) -> return the short ID (e.g. c1) in `c`.")
            appendLine("STRICT RULES:")
            appendLine("1. MULTI-ITEM SPLITTING: If the user mentions multiple separate payments (e.g. '40 momos, 60 auto'), return EACH as a separate entry in `items`.")
            appendLine("2. `a` = exact rupee amount in whole paise (₹150.50 -> 15050, ₹40 -> 4000).")
            appendLine("3. `m` = clean title-cased merchant or item name. Map person names to 'Sent to People'.")
            appendLine("4. Default `p` is '$normalizedDefaultMode' and default `t` is '$defaultTypeHint' unless the note indicates otherwise (e.g. 'got 500 cash' -> EARNING; 'ATM WDL' / 'ATW' -> TRANSFER with `p`='CASH').")
            appendLine("5. If splitting equally among N people (e.g. 'split 2 ways'), set `a` to the full bill and `sp` = N.")
            appendLine("6. If no date or time is mentioned in the note, use today's date ($todayIso) and time ($nowTime24h).")
            appendLine()
            appendLine("USER INPUT:")
            appendLine(MoneyUtils.normalizeNumericDatesInTextForAi(trimmedInput))
        }

        var retriesUsed = 0
        var promptTok = 0
        var outputTok = 0
        var currentDelay = 900L

        try {
            var rawJson = ""
            for (attempt in 1..TEXT_MAX_ATTEMPTS) {
                try {
                    if (attempt > 1) retriesUsed = attempt - 1
                    val response = withTimeout(TEXT_TIMEOUT_MS) {
                        model.generateContent(prompt)
                    }
                    promptTok += response.usageMetadata?.promptTokenCount ?: 0
                    outputTok += response.usageMetadata?.candidatesTokenCount ?: 0
                    val text = response.text
                    if (text.isNullOrBlank()) throw IllegalStateException("Empty response from Gemini.")
                    rawJson = text
                    break
                } catch (e: CancellationException) {
                    if (e !is TimeoutCancellationException) throw e
                    if (attempt >= TEXT_MAX_ATTEMPTS) throw e
                    delay(currentDelay)
                    currentDelay *= 2L
                } catch (e: Exception) {
                    val kind = GeminiCompactSchema.classifyAiError(e)
                    if (kind == AiErrorKind.FATAL_CLIENT_ERROR || kind == AiErrorKind.RATE_LIMITED_429 || attempt >= TEXT_MAX_ATTEMPTS) {
                        throw e
                    }
                    delay(currentDelay)
                    currentDelay *= 2L
                }
            }

            val elapsed = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
            val logEntry = ScanLogEntry(
                geminiTextMs = elapsed,
                totalMs = elapsed,
                path = ScanPath.NOTE_AI,
                retries = retriesUsed,
                promptTokens = promptTok,
                outputTokens = outputTok
            )

            val parsed = GeminiCompactSchema.parseCompactOrLegacyJson(
                jsonString = rawJson,
                source = TransactionSource.MANUAL,
                allowedCategories = allowedCategories,
                categoryIdMap = categoryIdMap,
                defaultPaymentMode = normalizedDefaultMode,
                defaultCashType = defaultCashType,
                missingDateDefaultsToToday = true
            )
            if (parsed is ExtractionResult.Success) {
                parsed.copy(logEntry = logEntry)
            } else {
                parsed
            }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            Log.w(TAG, "Natural-language AI timed out; using local fallback parser", e)
            parseNaturalLanguageNoteLocally(trimmedInput, allowedCategories, normalizedDefaultMode, defaultCashType)
                ?: ExtractionResult.Error("AI parsing timed out. Tap Try Again or Queue for later.")
        } catch (e: Exception) {
            Log.w(TAG, "Natural-language AI failed; attempting local fallback parser", e)
            parseNaturalLanguageNoteLocally(trimmedInput, allowedCategories, normalizedDefaultMode, defaultCashType)
                ?: ExtractionResult.Error(e.localizedMessage ?: "Couldn't reach Gemini AI. Tap Try Again or Queue for later.")
        }
    }

    /**
     * Local deterministic fallback parser for natural-language notes, multi-item cash logs, or bank SMS when offline.
     */
    internal fun parseNaturalLanguageNoteLocally(
        note: String,
        allowedCategories: List<String>,
        defaultPaymentMode: String = "ONLINE",
        defaultCashType: TransactionType? = null
    ): ExtractionResult? {
        val normalizedMode = if (defaultPaymentMode.equals("CASH", ignoreCase = true)) "CASH" else "ONLINE"
        val segments = note.split(Regex("""[,;\n]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val amountRegex = Regex("""(?:₹|Rs\.?|INR)?\s*(\d[\d,]*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val parsedDrafts = mutableListOf<DraftTransaction>()

        val targetSegments = if (segments.size > 1 && segments.count { amountRegex.containsMatchIn(it) } >= 2) {
            segments
        } else {
            listOf(note.trim())
        }

        for (seg in targetSegments) {
            val amountMatch = amountRegex.find(seg) ?: continue
            val paise = MoneyUtils.parseRupeesToPaise(amountMatch.groupValues[1]) ?: continue
            if (paise <= 0L) continue

            val splitRegex = Regex("""(?:split|divide[d]?)\s+(?:by\s+)?(\d+)\s*(?:ways|people)?""", RegexOption.IGNORE_CASE)
            val splitWays = splitRegex.find(seg)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 20) ?: 1

            val lower = seg.lowercase(Locale.US)
            val isAtm = LocalDraftVerifier.isAtmWithdrawalContext(seg)

            val (cleanedMerchant, rawMerchant) = MoneyUtils.cleanRawUpiOrBankMerchant(seg)
            val atMerchantRegex = Regex("""(?:at|to|from)\s+([A-Za-z0-9 &'.\-]{2,28}?)(?:\s+(?:via|on|for|split|rs|₹)|\s*$)""", RegexOption.IGNORE_CASE)
            val strippedCashMerchant = seg
                .replace(amountMatch.value, " ")
                .replace(Regex("""\b(?:got|paid|spent|cash|rs\.?|inr|from|for|to|at)\b""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .takeIf { it.length in 2..36 }
                ?.split(" ")
                ?.joinToString(" ") { word ->
                    word.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString() }
                }

            val merchantCandidate = when {
                isAtm -> "ATM Cash Withdrawal"
                atMerchantRegex.find(seg) != null -> atMerchantRegex.find(seg)!!.groupValues[1].trim()
                normalizedMode == "CASH" && !strippedCashMerchant.isNullOrBlank() -> strippedCashMerchant
                cleanedMerchant.length in 2..32 && !cleanedMerchant.equals(seg, ignoreCase = true) -> cleanedMerchant
                !strippedCashMerchant.isNullOrBlank() -> strippedCashMerchant
                else -> "Quick Note Payment"
            }

            val resolvedMode = if (isAtm || normalizedMode == "CASH" || lower.contains("cash")) "CASH" else "ONLINE"
            val paymentApp = when {
                resolvedMode == "CASH" -> "Cash"
                lower.contains("gpay") || lower.contains("google pay") -> "GPay"
                lower.contains("phonepe") -> "PhonePe"
                lower.contains("paytm") -> "Paytm"
                lower.contains("hdfc") -> "HDFC Bank"
                lower.contains("sbi") -> "SBI"
                lower.contains("icici") -> "ICICI Bank"
                else -> "UPI"
            }

            val resolvedType = when {
                isAtm || lower.contains("atm") || lower.contains("top up") || lower.contains("withdrawal") -> TransactionType.TRANSFER
                lower.contains("credited") || lower.contains("refund") || lower.contains("received") || lower.contains("got ") -> TransactionType.EARNING
                defaultCashType != null -> defaultCashType
                else -> TransactionType.EXPENSE
            }

            val brandCat = IndianBrandDictionary.matchCategory(merchantCandidate, seg)
            val defaultCat = when {
                resolvedType == TransactionType.TRANSFER -> "Transfers"
                resolvedType == TransactionType.EARNING -> allowedCategories.firstOrNull { it.contains("Income", ignoreCase = true) || it.contains("Gift", ignoreCase = true) } ?: "Other Income"
                brandCat != null -> brandCat
                lower.contains("auto") || lower.contains("cab") || lower.contains("bus") || lower.contains("metro") || lower.contains("rickshaw") || lower.contains("petrol") ->
                    allowedCategories.firstOrNull { it.contains("Transport", ignoreCase = true) } ?: "Transport & Fuel"
                lower.contains("momo") || lower.contains("samosa") || lower.contains("chai") || lower.contains("coffee") || lower.contains("dinner") || lower.contains("lunch") ->
                    allowedCategories.firstOrNull { it.contains("Food", ignoreCase = true) } ?: "Food & Dining"
                lower.contains("milk") || lower.contains("bread") || lower.contains("veggie") || lower.contains("grocery") ->
                    allowedCategories.firstOrNull { it.contains("Grocer", ignoreCase = true) } ?: "Groceries"
                else -> allowedCategories.firstOrNull() ?: "Food & Dining"
            }

            val baseDraft = DraftTransaction(
                timestamp = System.currentTimeMillis(),
                hasExplicitTime = true,
                amountPaise = paise,
                originalAmountPaise = paise,
                type = resolvedType,
                merchant = merchantCandidate,
                rawMerchantText = rawMerchant,
                category = defaultCat,
                paymentApp = paymentApp,
                referenceNumber = null,
                status = TransactionStatus.SUCCESS,
                note = seg,
                source = TransactionSource.MANUAL,
                confidence = 0.82f,
                paymentMode = resolvedMode,
                isAtmWithdrawal = isAtm
            )

            val finalDraft = if (splitWays > 1) baseDraft.applyEqualSplit(splitWays) else baseDraft
            parsedDrafts.add(finalDraft)
        }

        return if (parsedDrafts.isNotEmpty()) ExtractionResult.Success(parsedDrafts) else null
    }

    suspend fun analyzeBitmapWithGemini(
        bitmap: Bitmap,
        allowedCategories: List<String>,
        source: TransactionSource
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val totalStart = SystemClock.elapsedRealtime()
        val categoryEntities = allowedCategories.map {
            CategoryEntity(
                name = it,
                type = com.example.data.local.CategoryType.EXPENSE,
                colorHex = "#8FE3F0",
                iconName = "category",
                isDefault = true
            )
        }

        var ocrMs = 0L
        var regexMs = 0L
        var orderedText = ""
        try {
            val tOcr = SystemClock.elapsedRealtime()
            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val visionText = com.google.mlkit.vision.text.TextRecognition
                .getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(inputImage)
                .await()
            orderedText = localExtractor.stripStatusBarNoise(visionText.text)
            ocrMs = (SystemClock.elapsedRealtime() - tOcr).coerceAtLeast(1L)

            if (orderedText.length >= 15 && orderedText.any { it.isDigit() }) {
                val tRegex = SystemClock.elapsedRealtime()
                val regexResult = UpiRegexPreProcessor.process(
                    rawOcrText = orderedText,
                    categories = categoryEntities,
                    merchantMemories = emptyList(),
                    source = source
                )
                regexMs = (SystemClock.elapsedRealtime() - tRegex).coerceAtLeast(1L)

                if (regexResult.fastPathDraft != null) {
                    val verified = LocalDraftVerifier.verifyAndEnrichDraft(
                        draft = regexResult.fastPathDraft,
                        onDeviceText = regexResult.cleanedText,
                        regexAnchors = regexResult.detectedAnchors,
                        categories = categoryEntities,
                        merchantMemories = emptyList()
                    )
                    val totalElapsed = (SystemClock.elapsedRealtime() - totalStart).coerceAtLeast(1L)
                    val log = ScanLogEntry(
                        ocrTextMs = ocrMs,
                        regexMs = regexMs,
                        totalMs = totalElapsed,
                        path = ScanPath.FAST_PATH
                    )
                    return@withContext ExtractionResult.Success(listOf(verified), log)
                }

                val tAi = SystemClock.elapsedRealtime()
                val textTelemetry = callGeminiTextChunk(
                    textChunk = regexResult.cleanedText,
                    categories = categoryEntities,
                    source = source
                )
                val aiMs = (SystemClock.elapsedRealtime() - tAi).coerceAtLeast(1L)

                if (textTelemetry.result is ExtractionResult.Success && textTelemetry.result.drafts.isNotEmpty()) {
                    val verified = textTelemetry.result.drafts.map { draft ->
                        LocalDraftVerifier.verifyAndEnrichDraft(
                            draft = draft,
                            onDeviceText = regexResult.cleanedText,
                            regexAnchors = regexResult.detectedAnchors,
                            categories = categoryEntities,
                            merchantMemories = emptyList()
                        )
                    }
                    val totalElapsed = (SystemClock.elapsedRealtime() - totalStart).coerceAtLeast(1L)
                    val log = ScanLogEntry(
                        ocrTextMs = ocrMs,
                        regexMs = regexMs,
                        geminiTextMs = aiMs,
                        totalMs = totalElapsed,
                        path = ScanPath.TEXT_AI,
                        retries = textTelemetry.retries,
                        promptTokens = textTelemetry.promptTokens,
                        outputTokens = textTelemetry.outputTokens
                    )
                    return@withContext ExtractionResult.Success(verified, log)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Local OCR on bitmap failed, falling back to compressed vision", e)
        }

        val visionTelemetry = visionFallback.analyzeWithVisionFallbackTelemetry(
            sourceBitmap = bitmap,
            sourceUri = null,
            partialOcrHint = orderedText,
            categories = categoryEntities,
            source = source
        )
        val totalElapsed = (SystemClock.elapsedRealtime() - totalStart).coerceAtLeast(1L)
        val log = ScanLogEntry(
            ocrTextMs = ocrMs,
            regexMs = regexMs,
            visionFallbackMs = visionTelemetry.visionMs,
            totalMs = totalElapsed,
            path = ScanPath.VISION_AI,
            retries = visionTelemetry.retries,
            promptTokens = visionTelemetry.promptTokens,
            outputTokens = visionTelemetry.outputTokens
        )
        when (val r = visionTelemetry.result) {
            is ExtractionResult.Success -> ExtractionResult.Success(r.drafts, log)
            is ExtractionResult.RateLimited -> r.copy(logEntry = log)
            is ExtractionResult.UnreadableOrNoPayments -> r.copy(logEntry = log)
            is ExtractionResult.Error -> r.copy(logEntry = log)
            else -> r
        }
    }

    /**
     * Generates a realistic UPI & itemized receipt screenshot bitmap on-device for sample testing.
     */
    fun createSampleUpiScreenshotBitmap(presetIndex: Int): Bitmap {
        val width = 900
        val height = 1520
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#F8F9FA"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        data class SamplePreset(
            val appName: String,
            val rawMerchantHeader: String,
            val amountText: String,
            val dateText: String,
            val timeText: String,
            val utr: String,
            val statusText: String,
            val items: List<Pair<String, String>>,
            val noteText: String
        )

        val todayStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())
        val presets = listOf(
            SamplePreset(
                appName = "GPay",
                rawMerchantHeader = "UPI/431988271645/BLUE TOKAI COFFEE/BLR",
                amountText = "₹420.00",
                dateText = todayStr,
                timeText = "10:42 AM",
                utr = "431988271645",
                statusText = "Paid • Completed",
                items = listOf(
                    "1x Iced Vietnamese Latte" to "₹240.00",
                    "1x Almond Croissant" to "₹180.00"
                ),
                noteText = "Morning coffee & pastry"
            ),
            SamplePreset(
                appName = "PhonePe",
                rawMerchantHeader = "UPI/918273645512/SWIGGY INSTAMART/BANGALORE",
                amountText = "₹680.00",
                dateText = todayStr,
                timeText = "07:15 PM",
                utr = "918273645512",
                statusText = "Payment Successful",
                items = listOf(
                    "1x Sourdough Bread & Eggs" to "₹260.00",
                    "1x Fresh Avocados & Berries" to "₹420.00"
                ),
                noteText = "Weekly groceries"
            ),
            SamplePreset(
                appName = "Paytm",
                rawMerchantHeader = "POS 774125 INDIANOIL FUEL STATION MUMBAI",
                amountText = "₹1,450.00",
                dateText = todayStr,
                timeText = "06:20 PM",
                utr = "774125896321",
                statusText = "Paid Successfully",
                items = listOf(
                    "1x XP95 Petrol Full Tank" to "₹1,450.00"
                ),
                noteText = "Fuel refill"
            )
        )
        val p = presets[presetIndex % presets.size]

        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(50f, 90f, 850f, 1420f), 48f, 48f, paint)

        paint.color = Color.parseColor("#1B873F")
        canvas.drawCircle(450f, 220f, 52f, paint)

        paint.color = Color.WHITE
        paint.textSize = 52f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("✓", 450f, 240f, paint)

        paint.color = Color.parseColor("#111111")
        paint.textSize = 32f
        canvas.drawText("Paid to ${p.rawMerchantHeader}", 450f, 340f, paint)

        paint.textSize = 84f
        canvas.drawText(p.amountText, 450f, 460f, paint)

        paint.color = Color.parseColor("#2E7D32")
        paint.textSize = 30f
        canvas.drawText(p.statusText, 450f, 530f, paint)

        paint.color = Color.parseColor("#444444")
        paint.textSize = 28f
        canvas.drawText("${p.dateText}, ${p.timeText}", 450f, 595f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#111111")
        paint.textSize = 30f
        canvas.drawText("Itemized Bill Breakdown:", 110f, 690f, paint)

        var yPos = 745f
        for ((itemName, itemPrice) in p.items) {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("• $itemName", 130f, yPos, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(itemPrice, 790f, yPos, paint)
            yPos += 52f
        }

        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#444444")
        canvas.drawText("Payment App: ${p.appName}", 110f, yPos + 60f, paint)
        canvas.drawText("UPI Transaction ID: ${p.utr}", 110f, yPos + 120f, paint)
        canvas.drawText("Note: ${p.noteText}", 110f, yPos + 180f, paint)

        return bmp
    }
}
