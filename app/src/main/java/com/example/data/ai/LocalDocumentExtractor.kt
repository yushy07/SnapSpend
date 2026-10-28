package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val TAG = "LocalDocumentExtractor"

data class ExtractedPage(
    val pageIndex: Int,
    val rawText: String,
    val cleanedText: String,
    val previewBitmap: Bitmap? = null
)

data class LocalExtractionResult(
    val pages: List<ExtractedPage>,
    val fullCleanedText: String,
    val isValidText: Boolean,
    val totalCharacters: Int,
    val fallbackBitmaps: List<Bitmap> = emptyList()
)

/**
 * High-speed On-Device Text Extractor (< 200ms, 0 tokens) using Google ML Kit Text Recognition
 * and Android native PdfRenderer.
 *
 * Runs strictly on Dispatchers.IO with memory-safe bitmap decoding and spatial reading order sorting.
 */
class LocalDocumentExtractor(private val context: Context) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extractFromUris(
        uris: List<Uri>,
        onPageExtracted: (pageIndex: Int, totalPages: Int) -> Unit = { _, _ -> }
    ): LocalExtractionResult = withContext(Dispatchers.IO) {
        val extractedPages = mutableListOf<ExtractedPage>()
        val fallbackBitmaps = mutableListOf<Bitmap>()

        var overallIndex = 0
        for (uri in uris) {
            val mime = context.contentResolver.getType(uri)?.lowercase(Locale.US) ?: ""
            val isPdf = mime.contains("pdf") || uri.toString().lowercase(Locale.US).endsWith(".pdf")

            if (isPdf) {
                val pdfPages = extractPdfText(uri)
                for (p in pdfPages) {
                    extractedPages.add(p)
                    p.previewBitmap?.let { fallbackBitmaps.add(it) }
                    overallIndex++
                    onPageExtracted(overallIndex, max(overallIndex, uris.size))
                }
            } else {
                val imagePage = extractImageText(uri, overallIndex)
                if (imagePage != null) {
                    extractedPages.add(imagePage)
                    imagePage.previewBitmap?.let { fallbackBitmaps.add(it) }
                }
                overallIndex++
                onPageExtracted(overallIndex, max(overallIndex, uris.size))
            }
        }

        val fullCleanedText = extractedPages.joinToString("\n\n--- PAGE BREAK ---\n\n") { it.cleanedText }.trim()
        val totalChars = fullCleanedText.length
        val hasDigits = fullCleanedText.any { it.isDigit() }
        val isValid = totalChars >= 15 && hasDigits

        LocalExtractionResult(
            pages = extractedPages,
            fullCleanedText = fullCleanedText,
            isValidText = isValid,
            totalCharacters = totalChars,
            fallbackBitmaps = fallbackBitmaps
        )
    }

    private suspend fun extractImageText(uri: Uri, pageIndex: Int): ExtractedPage? {
        val bitmap = ImageCompressor.decodeScaledBitmapForOcr(context, uri, targetWidth = 1280) ?: return null
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(inputImage).await()
            val orderedRawText = sortVisionTextSpatially(visionText)
            val cleaned = stripStatusBarNoise(orderedRawText)
            ExtractedPage(
                pageIndex = pageIndex,
                rawText = orderedRawText,
                cleanedText = cleaned,
                previewBitmap = bitmap
            )
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit OCR failed on image at index $pageIndex", e)
            ExtractedPage(
                pageIndex = pageIndex,
                rawText = "",
                cleanedText = "",
                previewBitmap = bitmap
            )
        }
    }

    private suspend fun extractPdfText(uri: Uri): List<ExtractedPage> {
        val resultList = mutableListOf<ExtractedPage>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val maxPagesToProcess = min(pageCount, 10) // Guard against 100+ page statements

            for (i in 0 until maxPagesToProcess) {
                val page = renderer.openPage(i)
                val targetWidth = 1280
                val scale = targetWidth.toFloat() / max(page.width, 1).toFloat()
                val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                try {
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    val visionText = recognizer.process(inputImage).await()
                    val orderedText = sortVisionTextSpatially(visionText)
                    val cleaned = stripStatusBarNoise(orderedText)
                    resultList.add(
                        ExtractedPage(
                            pageIndex = i,
                            rawText = orderedText,
                            cleanedText = cleaned,
                            previewBitmap = bitmap
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "ML Kit OCR failed on PDF page $i", e)
                    resultList.add(
                        ExtractedPage(
                            pageIndex = i,
                            rawText = "",
                            cleanedText = "",
                            previewBitmap = bitmap
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render PDF for local OCR: $uri", e)
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
        return resultList
    }

    /**
     * Orders text lines spatially top-to-bottom, left-to-right.
     * Clusters lines whose vertical tops differ by <= 14px into the same visual row.
     */
    private fun sortVisionTextSpatially(visionText: Text): String {
        val allLines = mutableListOf<Text.Line>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                allLines.add(line)
            }
        }

        if (allLines.isEmpty()) return visionText.text

        allLines.sortWith { a, b ->
            val boxA = a.boundingBox
            val boxB = b.boundingBox
            val topA = boxA?.top ?: 0
            val topB = boxB?.top ?: 0
            val leftA = boxA?.left ?: 0
            val leftB = boxB?.left ?: 0

            // If vertical difference is small, sort horizontally (left-to-right table row)
            if (abs(topA - topB) <= 14) {
                leftA.compareTo(leftB)
            } else {
                topA.compareTo(topB)
            }
        }

        val sb = StringBuilder()
        var lastTop = -1
        for (line in allLines) {
            val top = line.boundingBox?.top ?: 0
            if (lastTop != -1 && abs(top - lastTop) > 14) {
                sb.append("\n")
            } else if (sb.isNotEmpty()) {
                sb.append("   ")
            }
            sb.append(line.text.trim())
            lastTop = top
        }

        return sb.toString().trim()
    }

    /**
     * Strips common mobile status-bar noise, network speeds, carrier tags, and battery %
     * so downstream parsing and token counts are as compact and accurate as possible.
     */
    fun stripStatusBarNoise(text: String): String {
        if (text.isBlank()) return ""
        val lines = text.split("\n")
        val cleanedLines = mutableListOf<String>()

        val speedRegex = Regex("""\b\d+(\.\d+)?\s*(KB/s|MB/s|kbps|mbps)(?!\w)""", RegexOption.IGNORE_CASE)
        val batteryRegex = Regex("""\b\d{1,3}%(?!\w)""")
        val carrierRegex = Regex("""\b(VoLTE|VoWiFi|5G|4G|LTE|WiFi|Wi-Fi|Jio|Airtel|Vi|BSNL)\b""", RegexOption.IGNORE_CASE)
        val clockRegex = Regex("""^\s*\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?\s*$""")

        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // Drop status-bar clock or pure battery/carrier indicators appearing in the first 2 lines
            if (index < 2 && (clockRegex.matches(line) || (batteryRegex.matches(line) && line.length <= 5))) {
                continue
            }

            var filtered = speedRegex.replace(line, "")
            filtered = carrierRegex.replace(filtered, "")
            filtered = batteryRegex.replace(filtered, "")
            filtered = filtered.replace(Regex("""\s+"""), " ").trim()

            if (filtered.isNotBlank()) {
                cleanedLines.add(filtered)
            }
        }

        return cleanedLines.joinToString("\n")
    }
}
