package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.data.local.CategoryEntity
import com.example.data.local.DefaultCategories
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.rules.DraftTransaction
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "CompressedVisionFallback"

/**
 * Phase 4 Safety Net: Compressed Multimodal Vision Fallback.
 * Engaged when on-device OCR is unreadable (<15 chars / 0 digits) or text-only Gemini fails.
 * Downscales images to max 1024px at 75% JPEG quality, applies zero thinking overhead,
 * and calls gemini-3.8-flash with exponential backoff retry.
 */
class CompressedVisionFallback(private val context: Context) {

    suspend fun analyzeWithVisionFallback(
        sourceBitmap: Bitmap? = null,
        sourceUri: Uri? = null,
        partialOcrHint: String = "",
        categories: List<CategoryEntity>,
        source: TransactionSource = TransactionSource.SCREENSHOT,
        onStatusUpdate: (String) -> Unit = {}
    ): ExtractionResult = withContext(Dispatchers.IO) {
        onStatusUpdate("Enhancing & scanning photo...")

        // 1. Prepare memory-safe, orientation-normalized, 1024px compressed bitmap
        val bitmap = when {
            sourceUri != null -> ImageCompressor.compressForVisionFallback(context, sourceUri)
            sourceBitmap != null -> {
                val bytes = ImageCompressor.compressBitmapToJpegBytes(sourceBitmap, maxEdge = 1024, quality = 75)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            else -> null
        }

        if (bitmap == null) {
            return@withContext ExtractionResult.UnreadableOrNoPayments(
                "Could not process or compress the image for visual reading."
            )
        }

        val allowedCategories = categories.map { it.name }.ifEmpty {
            DefaultCategories.ALL_DEFAULTS.map { it.name }
        }.distinct()

        val schema = buildVisionExtractionSchema(allowedCategories)
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
            appendLine("You are SnapSpend's precision Indian receipt, camera photo, handwritten bill & statement reader.")
            appendLine("This receipt or statement could not be read cleanly via local OCR. Visually inspect the compressed image to extract merchant/payee, amount in paise (Long), date, paymentMode (ONLINE/CASH), type (EXPENSE/INCOME), and best-fit category.")
            if (partialOcrHint.isNotBlank()) {
                appendLine("PARTIAL OCR TEXT HINT FROM DEVICE:")
                appendLine(partialOcrHint.take(300))
            }
            appendLine()
            appendLine("STRICT RULES:")
            appendLine("1. Allowed Categories ONLY: ${allowedCategories.joinToString(", ")}. Choose the single best fit. Never invent a category.")
            appendLine("2. Clean cryptic merchant strings into human-friendly names (e.g. clean 'UPI/412983/SWIGGY' to 'Swiggy').")
            appendLine("3. Convert total rupee amount into whole paise in `amountPaise` (₹150.50 -> 15050, ₹40 -> 4000).")
            appendLine("4. If the bill has itemized lines, extract each into `lineItems` with `itemName`, `quantity`, and `pricePaise`.")
            appendLine("5. If physical cash or ATM withdrawal, set `paymentMode` = 'CASH' (and type = 'TRANSFER' for ATM). Otherwise default to 'ONLINE'.")
            appendLine("6. Date format: YYYY-MM-DD ($todayIso if unknown); Time format: HH:mm in 24h.")
            appendLine("7. If the image is completely unreadable or does not contain any payment/receipt, set `isReadablePaymentDocument` = false with clear `unreadableReason`.")
        }

        try {
            val rawJson = retryWithExponentialBackoff(maxAttempts = 3, initialDelayMs = 1200L) { attempt ->
                if (attempt > 1) {
                    onStatusUpdate("Retrying visual scan (attempt $attempt)...")
                }
                val response = withTimeout(60_000L) {
                    model.generateContent(
                        content {
                            image(bitmap)
                            text(prompt)
                        }
                    )
                }
                val text = response.text
                if (text.isNullOrBlank()) {
                    throw IllegalStateException("Empty visual response from Gemini.")
                }
                text
            }

            parseVisionJson(rawJson, source, allowedCategories)
        } catch (e: TimeoutCancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "CompressedVisionFallback failed after retries", e)
            ExtractionResult.Error(e.localizedMessage ?: "Visual scan failed after retries. Tap Try Again or Queue Scan.")
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private suspend fun <T> retryWithExponentialBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1200L,
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
                val isRecoverable = msg.contains("503") || msg.contains("429") ||
                        msg.contains("ResourceExhausted", ignoreCase = true) ||
                        msg.contains("ServiceUnavailable", ignoreCase = true) ||
                        msg.contains("busy", ignoreCase = true) ||
                        msg.contains("overloaded", ignoreCase = true) ||
                        msg.contains("too many requests", ignoreCase = true)

                lastException = e
                if (attempt < maxAttempts && isRecoverable) {
                    Log.w(TAG, "Vision attempt $attempt failed ($msg), retrying in ${currentDelay}ms...", e)
                    delay(currentDelay)
                    currentDelay *= 2L
                    continue
                } else if (attempt < maxAttempts) {
                    delay(currentDelay)
                    currentDelay *= 2L
                    continue
                }
            }
        }
        throw lastException ?: IllegalStateException("Vision fallback failed after $maxAttempts attempts.")
    }

    private fun buildVisionExtractionSchema(allowedCategories: List<String>): Schema {
        val lineItemSchema = Schema.obj(
            properties = mapOf(
                "itemName" to Schema.string(description = "Name of the purchased item"),
                "quantity" to Schema.integer(description = "Quantity"),
                "pricePaise" to Schema.long(description = "Price in paise"),
                "priceText" to Schema.string(description = "Price text in rupees")
            )
        )

        val paymentItemSchema = Schema.obj(
            properties = mapOf(
                "dateIso" to Schema.string(description = "YYYY-MM-DD or 'unknown'"),
                "time24h" to Schema.string(description = "HH:mm or 'unknown'"),
                "amountPaise" to Schema.long(description = "Amount in whole paise (e.g. ₹150.50 -> 15050)"),
                "amountText" to Schema.string(description = "Amount text in rupees"),
                "type" to Schema.enumeration(listOf("EXPENSE", "EARNING", "TRANSFER")),
                "merchant" to Schema.string(description = "Clean merchant name"),
                "rawMerchantText" to Schema.string(description = "Raw merchant string"),
                "paymentApp" to Schema.string(description = "Payment app or Cash"),
                "referenceNumber" to Schema.string(description = "UPI ref/UTR or unknown"),
                "status" to Schema.enumeration(listOf("SUCCESS", "FAILED", "PENDING")),
                "suggestedCategory" to Schema.enumeration(allowedCategories),
                "confidence" to Schema.double(description = "Confidence 0.0 to 1.0"),
                "note" to Schema.string(description = "Optional note"),
                "suggestedSplitWays" to Schema.integer(description = "Split count"),
                "paymentMode" to Schema.enumeration(listOf("ONLINE", "CASH")),
                "lineItems" to Schema.array(lineItemSchema)
            )
        )

        return Schema.obj(
            properties = mapOf(
                "isReadablePaymentDocument" to Schema.boolean(description = "True if readable payment"),
                "unreadableReason" to Schema.string(description = "Reason if unreadable"),
                "payments" to Schema.array(paymentItemSchema)
            )
        )
    }

    private fun parseVisionJson(
        jsonString: String,
        source: TransactionSource,
        allowedCategories: List<String>
    ): ExtractionResult {
        val root = JSONObject(jsonString)
        val isReadable = root.optBoolean("isReadablePaymentDocument", true)
        val unreadableReason = root.optString("unreadableReason", "")
        val paymentsArray = root.optJSONArray("payments")

        if (!isReadable || paymentsArray == null || paymentsArray.length() == 0) {
            val msg = unreadableReason.ifBlank {
                "Could not clearly read payment details from this photo. Please ensure good lighting and clear text."
            }
            return ExtractionResult.UnreadableOrNoPayments(msg)
        }

        val drafts = mutableListOf<DraftTransaction>()
        for (i in 0 until paymentsArray.length()) {
            val item = paymentsArray.getJSONObject(i)
            val amountPaise = item.optLong("amountPaise", 0L)
            if (amountPaise <= 0L) continue

            val rawCategory = item.optString("suggestedCategory", "Other")
            val validCategory = if (allowedCategories.any { it.equals(rawCategory, ignoreCase = true) }) {
                allowedCategories.first { it.equals(rawCategory, ignoreCase = true) }
            } else {
                "Other"
            }

            val typeStr = item.optString("type", "EXPENSE")
            val type = try { TransactionType.valueOf(typeStr) } catch (_: Exception) { TransactionType.EXPENSE }
            val statusStr = item.optString("status", "SUCCESS")
            val status = try { TransactionStatus.valueOf(statusStr) } catch (_: Exception) { TransactionStatus.SUCCESS }

            val rawItemsArray = item.optJSONArray("lineItems")
            val lineItems = mutableListOf<com.example.data.rules.ReceiptLineItem>()
            if (rawItemsArray != null) {
                for (j in 0 until rawItemsArray.length()) {
                    val li = rawItemsArray.getJSONObject(j)
                    val pricePaise = li.optLong("pricePaise", 0L)
                    val itemName = li.optString("itemName", "").trim()
                    if (itemName.isNotBlank() && pricePaise > 0L) {
                        lineItems.add(
                            com.example.data.rules.ReceiptLineItem(
                                itemName = itemName,
                                quantity = li.optInt("quantity", 1).coerceAtLeast(1),
                                pricePaise = pricePaise,
                                isSelectedForSplit = true
                            )
                        )
                    }
                }
            }

            val dateIso = item.optString("dateIso", "unknown")
            val time24h = item.optString("time24h", "unknown")
            val (timestampMillis, hasExplicitTime) = com.example.util.MoneyUtils.parseDateAndTime(dateIso, time24h)

            drafts.add(
                DraftTransaction(
                    timestamp = timestampMillis,
                    hasExplicitTime = hasExplicitTime,
                    amountPaise = amountPaise,
                    type = type,
                    merchant = item.optString("merchant", "Unknown Merchant"),
                    rawMerchantText = item.optString("rawMerchantText", ""),
                    category = validCategory,
                    paymentApp = item.optString("paymentApp", "UPI"),
                    referenceNumber = item.optString("referenceNumber", "unknown"),
                    status = status,
                    confidence = item.optDouble("confidence", 0.85).toFloat(),
                    note = item.optString("note", ""),
                    splitWays = item.optInt("suggestedSplitWays", 1),
                    lineItems = lineItems,
                    source = source,
                    paymentMode = item.optString("paymentMode", "ONLINE")
                )
            )
        }

        return if (drafts.isNotEmpty()) {
            ExtractionResult.Success(drafts)
        } else {
            ExtractionResult.UnreadableOrNoPayments("No valid payment amounts found in the visual scan.")
        }
    }
}
