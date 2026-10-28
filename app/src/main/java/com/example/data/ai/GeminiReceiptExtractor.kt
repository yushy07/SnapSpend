package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import com.example.data.local.CategoryEntity
import com.example.data.local.DefaultCategories
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.rules.DraftTransaction
import com.example.data.rules.ReceiptLineItem
import com.example.util.MoneyUtils
import com.example.data.local.MerchantMemoryEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

private const val TAG = "GeminiReceiptExtractor"

sealed interface ExtractionResult {
    data class Success(val drafts: List<DraftTransaction>) : ExtractionResult
    data class UnreadableOrNoPayments(val reason: String) : ExtractionResult
    data class Error(val message: String) : ExtractionResult
}

/**
 * Hybrid Local-First receipt, UPI screenshot, multi-page PDF, and natural-language reader.
 *
 * Uses Google ML Kit on-device OCR (<200ms) + 0-API Regex Fast Path (<50ms) + ultra-fast
 * text-only Gemini 3.8-Flash (thinkingBudget = 0) with compressed multimodal vision fallback.
 */
class GeminiReceiptExtractor(private val context: Context) {

    val localExtractor by lazy { LocalDocumentExtractor(context) }
    val visionFallback by lazy { CompressedVisionFallback(context) }

    private suspend fun <T> retryWithExponentialBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 2000L,
        block: suspend (attempt: Int) -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block(attempt)
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                lastException = e
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val isHighDemand = msg.contains("503") || msg.contains("429") ||
                        msg.contains("ResourceExhausted", ignoreCase = true) ||
                        msg.contains("ServiceUnavailable", ignoreCase = true) ||
                        msg.contains("busy", ignoreCase = true) ||
                        msg.contains("too many requests", ignoreCase = true) ||
                        msg.contains("overloaded", ignoreCase = true)

                lastException = e
                if (attempt < maxAttempts) {
                    Log.w(TAG, "Gemini call attempt $attempt failed (high demand/error: $msg), retrying in ${currentDelay}ms...", e)
                    delay(currentDelay)
                    currentDelay *= 2L
                    continue
                }
            }
        }
        throw lastException ?: IllegalStateException("AI request failed after $maxAttempts attempts.")
    }

    private fun buildExtractionSchema(allowedCategoryNames: List<String>): Schema {
        val safeCategories = allowedCategoryNames.ifEmpty {
            DefaultCategories.ALL_DEFAULTS.map { it.name }
        }.distinct()

        val lineItemSchema = Schema.obj(
            properties = mapOf(
                "itemName" to Schema.string(
                    description = "Name of the individual purchased item on an itemized bill or receipt"
                ),
                "quantity" to Schema.integer(
                    description = "Quantity of this item (default 1)"
                ),
                "pricePaise" to Schema.long(
                    description = "Total price for this line item in whole Indian paise (e.g. ₹155.50 -> 15550)"
                ),
                "priceText" to Schema.string(
                    description = "Exact price text for this line item in Rupees (e.g. '155.50')"
                )
            )
        )

        val paymentItemSchema = Schema.obj(
            properties = mapOf(
                "dateIso" to Schema.string(
                    description = "Payment date in YYYY-MM-DD format, or 'unknown' if not visible"
                ),
                "time24h" to Schema.string(
                    description = "Payment time in 24-hour HH:mm format (e.g. '14:35'), or 'unknown' if not visible"
                ),
                "amountPaise" to Schema.long(
                    description = "Exact total payment amount in whole Indian paise (1 Rupee = 100 paise, e.g., ₹150.50 is 15050, ₹20 is 2000). Return 0 if unknown."
                ),
                "amountText" to Schema.string(
                    description = "Exact total amount text in Rupees as written (e.g. '150.50' or '20')"
                ),
                "type" to Schema.enumeration(
                    values = listOf("EXPENSE", "EARNING", "TRANSFER"),
                    description = "EXPENSE for money out, EARNING for money in or refunds/cashback, TRANSFER for money moved between the user's own accounts"
                ),
                "merchant" to Schema.string(
                    description = "Clean, human-readable merchant or person name (e.g. clean 'UPI/412983/SWIGGY INSTAMART/BLR' into 'Swiggy Instamart'), or 'unknown' if unreadable"
                ),
                "rawMerchantText" to Schema.string(
                    description = "Original uncleaned bank/UPI descriptor if it contained cryptic codes (e.g. 'UPI/412983/SWIGGY INSTAMART/BLR'), or empty string if already clean"
                ),
                "paymentApp" to Schema.string(
                    description = "Payment app or bank name (e.g. GPay, PhonePe, Paytm, HDFC Bank, SBI, Amazon Pay, Cash, Unknown)"
                ),
                "referenceNumber" to Schema.string(
                    description = "UPI transaction ID, UTR, or bank reference number if visible, else 'unknown'"
                ),
                "status" to Schema.enumeration(
                    values = listOf("SUCCESS", "FAILED", "PENDING"),
                    description = "Payment status: SUCCESS, FAILED, or PENDING"
                ),
                "suggestedCategory" to Schema.enumeration(
                    values = safeCategories,
                    description = "Category strictly chosen from the allowed category list"
                ),
                "confidence" to Schema.double(
                    description = "Confidence score from 0.0 (unsure/blurry) to 1.0 (crystal clear)"
                ),
                "note" to Schema.string(
                    description = "Brief note or remark visible on the payment, or empty string"
                ),
                "suggestedSplitWays" to Schema.integer(
                    description = "If the user or receipt explicitly mentions splitting equally among N people (e.g. 'split 2 ways' -> 2), return N; otherwise return 1"
                ),
                "paymentMode" to Schema.enumeration(
                    values = listOf("ONLINE", "CASH"),
                    description = "CASH for physical cash payments or ATM cash withdrawals (ATM WDL, CASH WITHDRAWAL, ATW); ONLINE for UPI, cards, netbanking, or bank transfers"
                ),
                "lineItems" to Schema.array(
                    items = lineItemSchema,
                    description = "Individual itemized receipt lines if visible on the bill/receipt; empty array if single UPI summary"
                )
            )
        )

        return Schema.obj(
            properties = mapOf(
                "isReadablePaymentDocument" to Schema.boolean(
                    description = "True if the input contains at least one readable payment, receipt, statement entry, or expense description; false if unreadable or unrelated"
                ),
                "unreadableReason" to Schema.string(
                    description = "If isReadablePaymentDocument is false, explain clearly why"
                ),
                "payments" to Schema.array(
                    items = paymentItemSchema,
                    description = "Every payment transaction extracted"
                )
            )
        )
    }

    /**
     * High-Speed Hybrid Local-First extraction:
     * Phase 1: Local on-device ML Kit OCR (<200ms, 0 tokens)
     * Phase 2: Local Regex noise filter & 0-API Fast Path (<50ms, 0 tokens)
     * Phase 3: Ultra-Fast Text-Only Gemini 3.8-Flash (thinkingBudget = 0) with multi-row batching
     * Phase 4: Compressed Multimodal Vision Fallback safety net
     */
    suspend fun extractFromUris(
        uris: List<Uri>,
        categories: List<CategoryEntity>,
        merchantMemories: List<MerchantMemoryEntity> = emptyList(),
        onProgress: (currentStep: Int, totalSteps: Int, statusText: String) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) {
            return@withContext ExtractionResult.Error("No file selected.")
        }

        try {
            // STEP 1: On-Device ML Kit Text Extraction (<200ms, 0 tokens)
            onProgress(1, 3, "Extracting text on device...")
            val ocrResult = localExtractor.extractFromUris(uris) { current, total ->
                onProgress(current, maxOf(total, 3), "Extracting text on device ($current/$total)...")
            }

            // STEP 2: Local Regex Noise Stripping & 0-API Fast Path (<50ms, 0 tokens)
            onProgress(2, 3, "Filtering receipt text...")
            val primarySource = if (uris.any { it.toString().endsWith(".pdf", ignoreCase = true) }) {
                TransactionSource.PDF
            } else {
                TransactionSource.SCREENSHOT
            }

            val regexResult = UpiRegexPreProcessor.process(
                rawOcrText = ocrResult.fullCleanedText,
                categories = categories,
                merchantMemories = merchantMemories,
                source = primarySource
            )

            // 0-API Fast Path: If high-confidence anchor matches known category, construct draft locally without Gemini!
            if (regexResult.fastPathDraft != null) {
                Log.d(TAG, "0-API Fast Path matched! Merchant: ${regexResult.fastPathDraft.merchant}")
                return@withContext ExtractionResult.Success(listOf(regexResult.fastPathDraft))
            }

            // STEP 3: Ultra-Fast Text-Only Gemini 3.8-Flash Call (Primary Cloud Path)
            if (ocrResult.isValidText) {
                val txCountLabel = if (regexResult.isMultiRowLikely) "statement" else "1"
                onProgress(2, 3, "Categorising $txCountLabel transaction(s)...")

                val textResult = extractFromCleanedOcrText(
                    cleanedOcrText = regexResult.cleanedText,
                    categories = categories,
                    detectedAnchors = regexResult.detectedAnchors,
                    source = primarySource
                )

                if (textResult is ExtractionResult.Success && textResult.drafts.isNotEmpty()) {
                    return@withContext textResult
                }
            }

            // STEP 4: Compressed Multimodal Vision Fallback (Safety Net for blurry, handwritten, or low-light receipts)
            onProgress(2, 3, "Enhancing & scanning photo...")
            val fallbackResult = visionFallback.analyzeWithVisionFallback(
                sourceUri = uris.firstOrNull(),
                sourceBitmap = ocrResult.fallbackBitmaps.firstOrNull(),
                partialOcrHint = ocrResult.fullCleanedText,
                categories = categories,
                source = primarySource,
                onStatusUpdate = { msg -> onProgress(2, 3, msg) }
            )

            fallbackResult
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini extraction timed out", e)
            ExtractionResult.Error("AI reading timed out after retrying. Check your internet or queue this scan for later.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini extraction failed", e)
            ExtractionResult.Error(
                e.localizedMessage ?: "Couldn't reach Gemini AI. Your picked file is kept safe — tap Try Again or Queue Scan."
            )
        }
    }

    /**
     * Ultra-fast text-only Gemini 3.8-Flash request (no image/PDF bytes).
     * Configured for sub-second latency with temperature = 0.1f and thinkingBudget = 0.
     * Supports single receipts and multi-row statements.
     */
    suspend fun extractFromCleanedOcrText(
        cleanedOcrText: String,
        categories: List<CategoryEntity>,
        detectedAnchors: DetectedUpiAnchors? = null,
        source: TransactionSource = TransactionSource.SCREENSHOT
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val allowedCategories = categories.map { it.name }.ifEmpty {
            DefaultCategories.ALL_DEFAULTS.map { it.name }
        }.distinct()

        val schema = buildExtractionSchema(allowedCategories)
        val config = generationConfig {
            temperature = 0.1f
            responseMimeType = "application/json"
            responseSchema = schema
            thinkingConfig = thinkingConfig {
                thinkingBudget = 0
            }
        }

        val model = Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )

        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val prompt = buildString {
            appendLine("You are SnapSpend's ultra-fast text-only Indian payment, UPI receipt & bank statement parser.")
            appendLine("Parse the following OCR text from an Indian UPI receipt, bill, or bank statement into structured JSON transaction(s).")
            appendLine("Extract merchant/payee, amount in paise (Long), date/timestamp, paymentMode (ONLINE/CASH), type (EXPENSE/INCOME), and best-fit category.")
            if (detectedAnchors != null) {
                appendLine("PRE-DETECTED REGEX HINTS:")
                detectedAnchors.amountPaise?.let { appendLine("- Amount in paise: $it") }
                detectedAnchors.merchant?.let { appendLine("- Merchant candidate: $it") }
                detectedAnchors.utrOrRef?.let { appendLine("- UTR / Ref: $it") }
                appendLine("- App: ${detectedAnchors.paymentApp}")
                appendLine("- Direction hint: ${detectedAnchors.type}")
            }
            appendLine()
            appendLine("CLEANED OCR TEXT:")
            appendLine(cleanedOcrText)
            appendLine()
            appendLine("STRICT RULES:")
            appendLine("1. Allowed Categories ONLY: ${allowedCategories.joinToString(", ")}. Choose the single best fit. Never invent a new category.")
            appendLine("2. Multi-Row Statements: If multiple payments/rows are visible in the text, extract ALL rows into a single JSON array (`payments`) in one pass.")
            appendLine("3. Amounts: All amounts MUST be converted to whole Indian paise in `amountPaise` (e.g. ₹145.50 -> 14550L, ₹20 -> 2000L).")
            appendLine("4. Clean cryptic bank/UPI narration (e.g. 'UPI/412983/SWIGGY INSTAMART/BLR') into clean merchant name ('Swiggy Instamart').")
            appendLine("5. Direction: Money out = EXPENSE, refunds/money received = EARNING, ATM cash withdrawal = TRANSFER with paymentMode = CASH.")
            appendLine("6. Date: YYYY-MM-DD ($todayIso if unknown); Time: HH:mm in 24h format.")
        }

        try {
            val rawJson = retryWithExponentialBackoff(maxAttempts = 3, initialDelayMs = 900L) {
                val response = withTimeout(45_000L) {
                    model.generateContent(prompt)
                }
                val text = response.text
                if (text.isNullOrBlank()) {
                    throw IllegalStateException("Empty text response from Gemini.")
                }
                text
            }
            parseExtractionJson(rawJson, source, allowedCategories)
        } catch (e: Exception) {
            Log.e(TAG, "extractFromCleanedOcrText failed", e)
            ExtractionResult.Error(e.localizedMessage ?: "Text-only extraction failed.")
        }
    }

    /**
     * Converts a single user-typed, voice-dictated, or pasted payment note (e.g.
     * "Paid 480 at Blue Tokai via GPay split 2 ways" or a pasted bank SMS) into structured
     * DraftTransactions using `gemini-4-argon` with `Schema.obj` + automatic exponential backoff retry
     * and local deterministic fallback if offline.
     *
     * PRIVACY GUARANTEE: Only the single `userInput` string entered by Yu is sent to Gemini.
     * Stored Room transactions are NEVER sent to any AI model.
     */
    suspend fun extractFromNaturalLanguageText(
        userInput: String,
        categories: List<CategoryEntity>,
        defaultPaymentMode: String = "ONLINE",
        defaultCashType: TransactionType? = null
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val trimmedInput = userInput.trim()
        if (trimmedInput.isEmpty()) {
            return@withContext ExtractionResult.Error("Please type or speak a payment description first.")
        }

        val allowedCategories = categories.map { it.name }.ifEmpty {
            DefaultCategories.ALL_DEFAULTS.map { it.name }
        }
        val schema = buildExtractionSchema(allowedCategories)
        val config = generationConfig {
            temperature = 0.1f
            responseMimeType = "application/json"
            responseSchema = schema
            thinkingConfig = thinkingConfig {
                thinkingBudget = 0
            }
        }

        val model = Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )

        val now = Date()
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val nowTime24h = SimpleDateFormat("HH:mm", Locale.US).format(now)
        val normalizedDefaultMode = if (defaultPaymentMode.equals("CASH", ignoreCase = true)) "CASH" else "ONLINE"
        val defaultTypeHint = defaultCashType?.name ?: "EXPENSE"

        val prompt = """
            You are SnapSpend's precision Indian payment & receipt parser.
            Parse the user's payment note, rough cash log, voice transcription, or pasted bank alert into structured payment transaction(s).
            
            USER INPUT:
            "$trimmedInput"
            
            STRICT RULES:
            1. Allowed Categories ONLY: ${allowedCategories.joinToString(", ")}. Never invent a new category.
            2. MULTI-ITEM SPLITTING: If the user mentions multiple separate payments in one note (e.g. "40 momos, 60 auto" or "auto 60, samosa 30"), return EACH payment as a separate entry in the `payments` array with its own merchant, category, and `amountPaise`.
            3. Clean messy UPI/bank strings (like 'UPI/412983/SWIGGY INSTAMART/BLR') into a clean human-friendly `merchant` name ('Swiggy Instamart') and put the raw string in `rawMerchantText`. For rough cash notes like "40 momos near station", set `merchant` to a clean title-cased name like "Momos Near Station" and choose the best matching `suggestedCategory` (e.g. "Food & Dining").
            4. Convert rupee amounts into exact whole paise in `amountPaise` (e.g. ₹150.50 -> 15050, ₹40 -> 4000) and also set `amountText`.
            5. Default `paymentMode` is "$normalizedDefaultMode" and default `type` is "$defaultTypeHint" unless the note clearly indicates otherwise (e.g. "got 500 cash from friend" -> type = "EARNING", or ATM cash withdrawal "ATM WDL" / "CASH WITHDRAWAL" / "ATW" -> type = "TRANSFER" and paymentMode = "CASH"). If `paymentMode` is "CASH", set `paymentApp` to "Cash".
            6. If the user mentions splitting a bill equally (e.g. "split 2 ways", "divided by 3"), set `amountPaise` to the FULL bill amount and set `suggestedSplitWays` to that number (e.g. 2 or 3).
            7. If no date or time is mentioned in the note, use today's date ($todayIso) and current time ($nowTime24h).
        """.trimIndent()

        try {
            val rawJson = retryWithExponentialBackoff(maxAttempts = 3, initialDelayMs = 900L) {
                val response = withTimeout(60_000L) {
                    model.generateContent(prompt)
                }
                val text = response.text
                if (text.isNullOrBlank()) {
                    throw IllegalStateException("Empty response from Gemini.")
                }
                text
            }
            parseExtractionJson(
                jsonString = rawJson,
                source = TransactionSource.MANUAL,
                allowedCategories = allowedCategories,
                defaultPaymentMode = normalizedDefaultMode,
                defaultCashType = defaultCashType
            )
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            Log.w(TAG, "Natural-language AI timed out; using local fallback parser", e)
            parseNaturalLanguageNoteLocally(trimmedInput, allowedCategories, normalizedDefaultMode, defaultCashType)
                ?: ExtractionResult.Error("AI parsing timed out. Tap Try Again or Queue for later.")
        } catch (e: Exception) {
            Log.w(TAG, "Natural-language AI failed after retries; attempting local fallback parser", e)
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
            val isAtm = com.example.data.rules.LocalInsightsAndRulesEngine.isAtmCashWithdrawalText(seg)

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

            val defaultCat = when {
                resolvedType == TransactionType.TRANSFER -> "Transfers"
                resolvedType == TransactionType.EARNING -> allowedCategories.firstOrNull { it.contains("Income", ignoreCase = true) || it.contains("Gift", ignoreCase = true) } ?: "Other Income"
                lower.contains("auto") || lower.contains("cab") || lower.contains("bus") || lower.contains("metro") || lower.contains("rickshaw") || lower.contains("petrol") ->
                    allowedCategories.firstOrNull { it.contains("Transport", ignoreCase = true) } ?: "Transport & Fuel"
                lower.contains("momo") || lower.contains("samosa") || lower.contains("chai") || lower.contains("coffee") || lower.contains("dinner") || lower.contains("lunch") || lower.contains("swiggy") || lower.contains("zomato") ->
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
                paymentMode = resolvedMode
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
        val categoryEntities = allowedCategories.map {
            CategoryEntity(
                name = it,
                type = com.example.data.local.CategoryType.EXPENSE,
                colorHex = "#8FE3F0",
                iconName = "category",
                isDefault = true
            )
        }

        // 1. Run on-device ML Kit OCR (<200ms)
        try {
            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val visionText = com.google.mlkit.vision.text.TextRecognition
                .getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(inputImage)
                .await()
            val orderedText = localExtractor.stripStatusBarNoise(visionText.text)

            if (orderedText.length >= 15 && orderedText.any { it.isDigit() }) {
                val regexResult = UpiRegexPreProcessor.process(
                    rawOcrText = orderedText,
                    categories = categoryEntities,
                    merchantMemories = emptyList<MerchantMemoryEntity>(),
                    source = source
                )

                if (regexResult.fastPathDraft != null) {
                    return@withContext ExtractionResult.Success(listOf(regexResult.fastPathDraft))
                }

                val textResult = extractFromCleanedOcrText(
                    cleanedOcrText = regexResult.cleanedText,
                    categories = categoryEntities,
                    detectedAnchors = regexResult.detectedAnchors,
                    source = source
                )

                if (textResult is ExtractionResult.Success && textResult.drafts.isNotEmpty()) {
                    return@withContext textResult
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Local OCR on bitmap failed, falling back to compressed vision", e)
        }

        // Fallback to compressed vision safety net
        visionFallback.analyzeWithVisionFallback(
            sourceBitmap = bitmap,
            sourceUri = null,
            partialOcrHint = "",
            categories = categoryEntities,
            source = source
        )
    }

    private fun parseExtractionJson(
        jsonString: String,
        source: TransactionSource,
        allowedCategories: List<String>,
        defaultPaymentMode: String = "ONLINE",
        defaultCashType: TransactionType? = null
    ): ExtractionResult {
        val root = JSONObject(jsonString)
        val isReadable = root.optBoolean("isReadablePaymentDocument", true)
        val unreadableReason = root.optString("unreadableReason", "")
        val paymentsArray = root.optJSONArray("payments")

        if (!isReadable || paymentsArray == null || paymentsArray.length() == 0) {
            val msg = unreadableReason.ifBlank {
                "Couldn't read a payment from this input. Make sure it clearly shows a payment or receipt."
            }
            return ExtractionResult.UnreadableOrNoPayments(msg)
        }

        val drafts = mutableListOf<DraftTransaction>()
        for (i in 0 until paymentsArray.length()) {
            val item = paymentsArray.getJSONObject(i)
            val dateIso = item.optString("dateIso", "unknown")
            val time24h = item.optString("time24h", "unknown")
            val (timestampMillis, hasExplicitTime) = MoneyUtils.parseDateAndTime(dateIso, time24h)

            val amountText = item.optString("amountText", "")
            val amountFromText = MoneyUtils.parseRupeesToPaise(amountText)
            val amountFromLong = item.optLong("amountPaise", 0L)
            val finalAmountPaise = when {
                amountFromText != null && amountFromText > 0L -> amountFromText
                amountFromLong > 0L -> amountFromLong
                else -> 0L
            }

            val typeStr = item.optString("type", defaultCashType?.name ?: "EXPENSE").uppercase(Locale.US)
            val parsedType = runCatching { TransactionType.valueOf(typeStr) }.getOrDefault(defaultCashType ?: TransactionType.EXPENSE)

            val rawMerchantField = item.optString("merchant", "unknown").trim().ifBlank { "unknown" }
            val explicitRawText = item.optString("rawMerchantText", "").trim()
                .takeIf { !it.equals("unknown", ignoreCase = true) }
                .orEmpty()
            val (cleanedMerchant, fallbackRaw) = MoneyUtils.cleanRawUpiOrBankMerchant(rawMerchantField)
            val finalRawMerchant = explicitRawText.ifBlank { fallbackRaw }

            val note = item.optString("note", "").takeIf { !it.equals("unknown", ignoreCase = true) }.orEmpty()
            val isAtmWithdrawal = com.example.data.rules.LocalInsightsAndRulesEngine.isAtmCashWithdrawalText(rawMerchantField) ||
                com.example.data.rules.LocalInsightsAndRulesEngine.isAtmCashWithdrawalText(finalRawMerchant) ||
                com.example.data.rules.LocalInsightsAndRulesEngine.isAtmCashWithdrawalText(note)

            val type = if (isAtmWithdrawal) TransactionType.TRANSFER else parsedType
            val rawModeStr = item.optString("paymentMode", defaultPaymentMode).uppercase(Locale.US)
            val resolvedPaymentMode = when {
                isAtmWithdrawal -> "CASH"
                defaultPaymentMode.equals("CASH", ignoreCase = true) -> "CASH"
                rawModeStr == "CASH" -> "CASH"
                else -> "ONLINE"
            }

            val rawPaymentApp = item.optString("paymentApp", if (resolvedPaymentMode == "CASH") "Cash" else "Unknown").trim().ifBlank {
                if (resolvedPaymentMode == "CASH") "Cash" else "Unknown"
            }
            val paymentApp = if (resolvedPaymentMode == "CASH" && (rawPaymentApp.equals("Unknown", ignoreCase = true) || rawPaymentApp.isBlank())) {
                "Cash"
            } else {
                rawPaymentApp
            }

            val refRaw = item.optString("referenceNumber", "unknown").trim()
            val referenceNumber = if (refRaw.equals("unknown", ignoreCase = true) || refRaw.isBlank()) null else refRaw

            val statusStr = item.optString("status", "SUCCESS").uppercase(Locale.US)
            val status = runCatching { TransactionStatus.valueOf(statusStr) }.getOrDefault(TransactionStatus.SUCCESS)

            val rawCategory = item.optString("suggestedCategory", "Other")
            val validCategory = when {
                isAtmWithdrawal || type == TransactionType.TRANSFER -> "Transfers"
                else -> allowedCategories.firstOrNull { it.equals(rawCategory, ignoreCase = true) }
                    ?: if (type == TransactionType.EARNING) "Other Income" else "Other"
            }

            val confidence = item.optDouble("confidence", 0.85).toFloat().coerceIn(0f, 1f)
            val suggestedSplitWays = item.optInt("suggestedSplitWays", 1).coerceIn(1, 20)

            val lineItemsArray = item.optJSONArray("lineItems")
            val parsedLineItems = mutableListOf<ReceiptLineItem>()
            if (lineItemsArray != null) {
                for (j in 0 until lineItemsArray.length()) {
                    val liObj = lineItemsArray.optJSONObject(j) ?: continue
                    val liName = liObj.optString("itemName", "").trim()
                    if (liName.isEmpty() || liName.equals("unknown", ignoreCase = true)) continue
                    val liQty = liObj.optInt("quantity", 1).coerceAtLeast(1)
                    val liPriceText = liObj.optString("priceText", "")
                    val liPaise = MoneyUtils.parseRupeesToPaise(liPriceText)
                        ?: liObj.optLong("pricePaise", 0L)
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
                merchant = cleanedMerchant,
                rawMerchantText = finalRawMerchant,
                category = validCategory,
                paymentApp = paymentApp,
                referenceNumber = referenceNumber,
                status = status,
                note = note,
                source = source,
                confidence = confidence,
                lineItems = parsedLineItems,
                paymentMode = resolvedPaymentMode
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

    private fun decodeScaledBitmap(uri: Uri): Bitmap? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
            val maxDim = 1600
            var sampleSize = 1
            while (boundsOptions.outWidth / sampleSize > maxDim || boundsOptions.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode image URI: $uri", e)
            null
        }
    }

    private fun getPdfPageCount(uri: Uri): Int {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    min(renderer.pageCount, 15)
                }
            } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read PDF page count", e)
            0
        }
    }

    private fun renderPdfPageToBitmap(uri: Uri, pageIndex: Int): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = 2
                        val width = (page.width * scale).coerceAtMost(1800)
                        val height = (page.height * scale).coerceAtMost(2400)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render PDF page $pageIndex", e)
            null
        }
    }

    /**
     * Generates a realistic UPI & itemized receipt screenshot bitmap on-device so Yu can test
     * Gemini's UPI merchant cleanup (`UPI/431988.../BLUE TOKAI/BLR` -> `Blue Tokai`) and
     * itemized bill-split calculator (`Iced Latte` + `Almond Croissant`) even on a fresh emulator.
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
