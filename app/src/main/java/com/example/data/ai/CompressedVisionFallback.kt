package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.example.data.local.CategoryEntity
import com.example.data.local.DefaultCategories
import com.example.data.local.TransactionSource
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "CompressedVisionFallback"
private const val VISION_TIMEOUT_MS = 30_000L
private const val VISION_MAX_ATTEMPTS = 2

data class VisionCallTelemetry(
    val result: ExtractionResult,
    val visionMs: Long = 0L,
    val retries: Int = 0,
    val promptTokens: Int = 0,
    val outputTokens: Int = 0,
    val isRateLimited: Boolean = false
)

/**
 * Phase 4 Safety Net: Compressed Multimodal Vision Fallback.
 * Engaged when on-device OCR is unreadable (<15 chars / 0 digits) or text-only Gemini fails.
 * Downscales images to max 1024px at 75% JPEG quality, uses [GEMINI_MODEL_NAME] (`gemini-3.8-flash`)
 * with `thinkingBudget = 0`, 30s timeout, max 2 attempts, and the compact short-key schema.
 */
class CompressedVisionFallback(private val context: Context) {

    suspend fun analyzeWithVisionFallback(
        sourceBitmap: Bitmap? = null,
        sourceUri: Uri? = null,
        partialOcrHint: String = "",
        categories: List<CategoryEntity>,
        source: TransactionSource = TransactionSource.SCREENSHOT,
        onStatusUpdate: (String) -> Unit = {}
    ): ExtractionResult {
        return analyzeWithVisionFallbackTelemetry(
            sourceBitmap = sourceBitmap,
            sourceUri = sourceUri,
            partialOcrHint = partialOcrHint,
            categories = categories,
            source = source,
            onStatusUpdate = onStatusUpdate
        ).result
    }

    suspend fun analyzeWithVisionFallbackTelemetry(
        sourceBitmap: Bitmap? = null,
        sourceUri: Uri? = null,
        partialOcrHint: String = "",
        categories: List<CategoryEntity>,
        source: TransactionSource = TransactionSource.SCREENSHOT,
        onStatusUpdate: (String) -> Unit = {}
    ): VisionCallTelemetry = withContext(Dispatchers.IO) {
        val t0 = SystemClock.elapsedRealtime()
        onStatusUpdate("Enhancing & scanning photo...")

        val bitmap = when {
            sourceUri != null -> ImageCompressor.compressForVisionFallback(context, sourceUri)
            sourceBitmap != null -> {
                val bytes = ImageCompressor.compressBitmapToJpegBytes(sourceBitmap, maxEdge = 1024, quality = 75)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            else -> null
        }

        if (bitmap == null) {
            return@withContext VisionCallTelemetry(
                result = ExtractionResult.UnreadableOrNoPayments(
                    "Could not process or compress the image for visual reading."
                ),
                visionMs = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
            )
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

        val prompt = buildString {
            appendLine("You are SnapSpend's precision Indian receipt, camera photo, handwritten bill & statement reader.")
            appendLine("Visually inspect the image and extract every payment into `items` using the compact JSON schema.")
            appendLine("CATEGORY ID LEGEND ($categoryLegend) -> return the short ID (e.g. c1) in `c`.")
            appendLine("RULES:")
            appendLine("1. `a` = exact total amount in whole Indian paise (₹150.50 -> 15050, ₹40 -> 4000).")
            appendLine("2. `m` = clean merchant or person name (clean 'UPI/412983/SWIGGY' -> 'Swiggy'). Map person names to 'Sent to People'.")
            appendLine("3. `t` = EXPENSE for money out, EARNING for money received/refunds/cashback, TRANSFER for transfers between own accounts or ATM withdrawals (ATM, CASH WDL, ATW).")
            appendLine("4. `p` = 'CASH' for physical cash or ATM withdrawals; otherwise app/bank name ('GPay', 'PhonePe', 'Paytm', 'CRED', 'UPI', 'Card').")
            appendLine("5. `d` = YYYY-MM-DD, or 'unknown' if no date is visible on the image (never guess today's date). `tm` = HH:mm (24h) or 'unknown'.")
            appendLine("6. If unreadable or not a payment/receipt, set `readable` = false and explain in `reason`.")
            if (partialOcrHint.isNotBlank()) {
                appendLine("PARTIAL OCR TEXT:")
                appendLine(com.example.util.MoneyUtils.normalizeNumericDatesInTextForAi(partialOcrHint.take(300)))
            }
        }

        var retriesUsed = 0
        var promptTok = 0
        var outputTok = 0

        try {
            var currentDelay = 1000L
            var rawJson = ""
            for (attempt in 1..VISION_MAX_ATTEMPTS) {
                try {
                    if (attempt > 1) {
                        retriesUsed = attempt - 1
                        onStatusUpdate("Retrying visual scan (attempt $attempt)...")
                    }
                    val response = withTimeout(VISION_TIMEOUT_MS) {
                        model.generateContent(
                            content {
                                image(bitmap)
                                text(prompt)
                            }
                        )
                    }
                    promptTok += response.usageMetadata?.promptTokenCount ?: 0
                    outputTok += response.usageMetadata?.candidatesTokenCount ?: 0
                    val text = response.text
                    if (text.isNullOrBlank()) {
                        throw IllegalStateException("Empty visual response from Gemini.")
                    }
                    rawJson = text
                    break
                } catch (e: CancellationException) {
                    if (e !is TimeoutCancellationException) throw e
                    if (attempt >= VISION_MAX_ATTEMPTS) throw e
                    delay(currentDelay)
                    currentDelay *= 2L
                } catch (e: Exception) {
                    val classification = GeminiCompactSchema.classifyAiError(e)
                    if (classification == AiErrorKind.RATE_LIMITED_429) {
                        val elapsed = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
                        return@withContext VisionCallTelemetry(
                            result = ExtractionResult.RateLimited("AI limit reached. Queued, will retry later"),
                            visionMs = elapsed,
                            retries = retriesUsed,
                            promptTokens = promptTok,
                            outputTokens = outputTok,
                            isRateLimited = true
                        )
                    }
                    if (classification == AiErrorKind.FATAL_CLIENT_ERROR || attempt >= VISION_MAX_ATTEMPTS) {
                        throw e
                    }
                    delay(currentDelay)
                    currentDelay *= 2L
                }
            }

            val parsed = GeminiCompactSchema.parseCompactOrLegacyJson(
                jsonString = rawJson,
                source = source,
                allowedCategories = allowedCategories,
                categoryIdMap = categoryIdMap
            )
            val elapsed = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
            VisionCallTelemetry(
                result = parsed,
                visionMs = elapsed,
                retries = retriesUsed,
                promptTokens = promptTok,
                outputTokens = outputTok
            )
        } catch (e: TimeoutCancellationException) {
            val elapsed = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
            VisionCallTelemetry(
                result = ExtractionResult.Error("Visual scan timed out after 30s. Check your internet or Queue Scan."),
                visionMs = elapsed,
                retries = retriesUsed,
                promptTokens = promptTok,
                outputTokens = outputTok
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "CompressedVisionFallback failed", e)
            val elapsed = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
            VisionCallTelemetry(
                result = ExtractionResult.Error(
                    GeminiCompactSchema.formatFatalErrorMessage(e)
                ),
                visionMs = elapsed,
                retries = retriesUsed,
                promptTokens = promptTok,
                outputTokens = outputTok
            )
        } finally {
            if (bitmap != sourceBitmap && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
}
