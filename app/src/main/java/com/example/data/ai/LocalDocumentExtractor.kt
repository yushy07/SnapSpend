package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val TAG = "LocalDocumentExtractor"
const val MAX_PDF_PAGES = 30

class PdfPasswordRequiredException(
    val uri: Uri,
    val isInvalidPasswordAttempt: Boolean = false
) : Exception(
    if (isInvalidPasswordAttempt) {
        "Incorrect PDF password. Please try again."
    } else {
        "This PDF is password-protected. Enter its password to unlock and scan."
    }
)

data class ExtractedPage(
    val pageIndex: Int,
    val rawText: String,
    val cleanedText: String,
    val previewBitmap: Bitmap? = null,
    val usedNativePdfText: Boolean = false
)

data class LocalExtractionResult(
    val pages: List<ExtractedPage>,
    val fullCleanedText: String,
    val isValidText: Boolean,
    val totalCharacters: Int,
    val fallbackBitmaps: List<Bitmap> = emptyList(),
    val readFileMs: Long = 0L,
    val ocrTextMs: Long = 0L,
    val totalPdfPages: Int = 0,
    val skippedPdfPages: Int = 0,
    val isPdf: Boolean = false
)

/**
 * High-speed On-Device Text Extractor using:
 * 1. Native PDF embedded text layer via PDFBox-Android (Part 2.2) — fast & zero bitmap allocation
 *    when the PDF has selectable text.
 * 2. Google ML Kit Text Recognition + Android native PdfRenderer for images or scanned PDF pages
 *    that lack an embedded text layer.
 */
class LocalDocumentExtractor(private val context: Context) {

    companion object {
        private val pdfBoxInitialized = AtomicBoolean(false)
    }

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private fun ensurePdfBoxInitialized() {
        if (pdfBoxInitialized.compareAndSet(false, true)) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "PDFBoxResourceLoader init warning", e)
            }
        }
    }

    suspend fun extractSingleUri(
        uri: Uri,
        pdfPassword: String? = null,
        onPageExtracted: (pageIndex: Int, totalPages: Int) -> Unit = { _, _ -> }
    ): LocalExtractionResult = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)?.lowercase(Locale.US) ?: ""
        val isPdf = mime.contains("pdf") || uri.toString().lowercase(Locale.US).endsWith(".pdf")

        if (isPdf) {
            extractPdfDocument(uri, pdfPassword, onPageExtracted)
        } else {
            val t0 = SystemClock.elapsedRealtime()
            val bitmap = ImageCompressor.decodeScaledBitmapForOcr(context, uri, targetWidth = 1280)
            val readMs = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)

            val tOcrStart = SystemClock.elapsedRealtime()
            val page = if (bitmap != null) {
                extractImageTextFromBitmap(bitmap, 0)
            } else {
                null
            }
            val ocrMs = (SystemClock.elapsedRealtime() - tOcrStart).coerceAtLeast(1L)
            onPageExtracted(1, 1)

            val pages = listOfNotNull(page)
            val fullCleaned = pages.joinToString("\n\n") { it.cleanedText }.trim()
            val hasDigits = fullCleaned.any { it.isDigit() }
            LocalExtractionResult(
                pages = pages,
                fullCleanedText = fullCleaned,
                isValidText = fullCleaned.length >= 15 && hasDigits,
                totalCharacters = fullCleaned.length,
                fallbackBitmaps = listOfNotNull(page?.previewBitmap),
                readFileMs = readMs,
                ocrTextMs = ocrMs,
                totalPdfPages = 0,
                skippedPdfPages = 0,
                isPdf = false
            )
        }
    }

    suspend fun extractFromUris(
        uris: List<Uri>,
        pdfPassword: String? = null,
        onPageExtracted: (pageIndex: Int, totalPages: Int) -> Unit = { _, _ -> }
    ): LocalExtractionResult = withContext(Dispatchers.IO) {
        val extractedPages = mutableListOf<ExtractedPage>()
        val fallbackBitmaps = mutableListOf<Bitmap>()
        var totalReadMs = 0L
        var totalOcrMs = 0L
        var totalPdfPagesCount = 0
        var totalSkippedPages = 0
        var anyPdf = false

        for ((idx, uri) in uris.withIndex()) {
            val single = extractSingleUri(
                uri = uri,
                pdfPassword = pdfPassword,
                onPageExtracted = { pageIdx, pageTotal ->
                    onPageExtracted(idx + pageIdx, max(uris.size, pageTotal))
                }
            )
            extractedPages.addAll(single.pages)
            fallbackBitmaps.addAll(single.fallbackBitmaps)
            totalReadMs += single.readFileMs
            totalOcrMs += single.ocrTextMs
            totalPdfPagesCount += single.totalPdfPages
            totalSkippedPages += single.skippedPdfPages
            if (single.isPdf) anyPdf = true
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
            fallbackBitmaps = fallbackBitmaps,
            readFileMs = totalReadMs,
            ocrTextMs = totalOcrMs,
            totalPdfPages = totalPdfPagesCount,
            skippedPdfPages = totalSkippedPages,
            isPdf = anyPdf
        )
    }

    private suspend fun extractImageTextFromBitmap(bitmap: Bitmap, pageIndex: Int): ExtractedPage {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(inputImage).await()
            val orderedRawText = sortVisionTextSpatially(visionText)
            val cleaned = stripStatusBarNoise(orderedRawText)
            ExtractedPage(
                pageIndex = pageIndex,
                rawText = orderedRawText,
                cleanedText = cleaned,
                previewBitmap = bitmap,
                usedNativePdfText = false
            )
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit OCR failed on image at index $pageIndex", e)
            ExtractedPage(
                pageIndex = pageIndex,
                rawText = "",
                cleanedText = "",
                previewBitmap = bitmap,
                usedNativePdfText = false
            )
        }
    }

    /**
     * Part 2.2: Reads the PDF's embedded text layer first via PDFBox-Android.
     * Renders to bitmap + ML Kit OCR ONLY for pages whose embedded text layer is blank (<15 chars).
     * Caps at [MAX_PDF_PAGES] (30 pages) and reports any skipped pages.
     */
    private suspend fun extractPdfDocument(
        uri: Uri,
        pdfPassword: String?,
        onPageExtracted: (pageIndex: Int, totalPages: Int) -> Unit
    ): LocalExtractionResult {
        ensurePdfBoxInitialized()
        val tReadStart = SystemClock.elapsedRealtime()
        val pdfBytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read PDF bytes: $uri", e)
            null
        }
        val readFileMs = (SystemClock.elapsedRealtime() - tReadStart).coerceAtLeast(1L)

        if (pdfBytes == null || pdfBytes.isEmpty()) {
            return LocalExtractionResult(
                pages = emptyList(),
                fullCleanedText = "",
                isValidText = false,
                totalCharacters = 0,
                readFileMs = readFileMs,
                isPdf = true
            )
        }

        val tOcrStart = SystemClock.elapsedRealtime()
        val pages = mutableListOf<ExtractedPage>()
        val fallbackBitmaps = mutableListOf<Bitmap>()
        var totalPageCount = 0
        var skippedPages = 0
        val pagesNeedingBitmapOcr = mutableListOf<Int>()

        // 1. Try native PDF text layer via PDFBox
        var pdDocument: PDDocument? = null
        try {
            pdDocument = if (!pdfPassword.isNullOrEmpty()) {
                PDDocument.load(pdfBytes, pdfPassword)
            } else {
                PDDocument.load(pdfBytes)
            }

            totalPageCount = pdDocument.numberOfPages
            val pagesToProcess = min(totalPageCount, MAX_PDF_PAGES)
            skippedPages = (totalPageCount - pagesToProcess).coerceAtLeast(0)

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }

            for (pageIdx in 0 until pagesToProcess) {
                stripper.startPage = pageIdx + 1
                stripper.endPage = pageIdx + 1
                val rawPageText = try {
                    stripper.getText(pdDocument).orEmpty().trim()
                } catch (e: Exception) {
                    ""
                }
                val cleaned = stripStatusBarNoise(rawPageText)
                if (cleaned.length >= 15 && cleaned.any { it.isDigit() }) {
                    pages.add(
                        ExtractedPage(
                            pageIndex = pageIdx,
                            rawText = rawPageText,
                            cleanedText = cleaned,
                            previewBitmap = null,
                            usedNativePdfText = true
                        )
                    )
                    onPageExtracted(pageIdx + 1, pagesToProcess)
                } else {
                    pagesNeedingBitmapOcr.add(pageIdx)
                }
            }
        } catch (e: InvalidPasswordException) {
            throw PdfPasswordRequiredException(
                uri = uri,
                isInvalidPasswordAttempt = !pdfPassword.isNullOrEmpty()
            )
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                throw PdfPasswordRequiredException(
                    uri = uri,
                    isInvalidPasswordAttempt = !pdfPassword.isNullOrEmpty()
                )
            }
            Log.w(TAG, "PDFBox text extraction failed, falling back to PdfRenderer OCR", e)
        } finally {
            try {
                pdDocument?.close()
            } catch (_: Exception) {
            }
        }

        // 2. For pages without an embedded text layer (or if PDFBox failed), render via PdfRenderer + ML Kit OCR
        if (pagesNeedingBitmapOcr.isNotEmpty() || (pages.isEmpty() && totalPageCount == 0)) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    renderer = PdfRenderer(pfd)
                    if (totalPageCount == 0) {
                        totalPageCount = renderer.pageCount
                        val pagesToProcess = min(totalPageCount, MAX_PDF_PAGES)
                        skippedPages = (totalPageCount - pagesToProcess).coerceAtLeast(0)
                        for (i in 0 until pagesToProcess) {
                            pagesNeedingBitmapOcr.add(i)
                        }
                    }
                    val pagesToProcess = min(totalPageCount, MAX_PDF_PAGES)
                    for (pageIdx in pagesNeedingBitmapOcr) {
                        if (pageIdx >= renderer.pageCount) continue
                        val page = renderer.openPage(pageIdx)
                        val targetWidth = 1280
                        val scale = targetWidth.toFloat() / max(page.width, 1).toFloat()
                        val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val extractedPage = extractImageTextFromBitmap(bitmap, pageIdx)
                        pages.add(extractedPage)
                        fallbackBitmaps.add(bitmap)
                        onPageExtracted(pageIdx + 1, pagesToProcess)
                    }
                }
            } catch (e: SecurityException) {
                throw PdfPasswordRequiredException(
                    uri = uri,
                    isInvalidPasswordAttempt = !pdfPassword.isNullOrEmpty()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed PdfRenderer fallback on $uri", e)
            } finally {
                try { renderer?.close() } catch (_: Exception) {}
                try { pfd?.close() } catch (_: Exception) {}
            }
        }

        pages.sortBy { it.pageIndex }
        val ocrTextMs = (SystemClock.elapsedRealtime() - tOcrStart).coerceAtLeast(1L)
        val fullCleanedText = pages.joinToString("\n\n--- PAGE BREAK ---\n\n") { it.cleanedText }.trim()
        val hasDigits = fullCleanedText.any { it.isDigit() }

        return LocalExtractionResult(
            pages = pages,
            fullCleanedText = fullCleanedText,
            isValidText = fullCleanedText.length >= 15 && hasDigits,
            totalCharacters = fullCleanedText.length,
            fallbackBitmaps = fallbackBitmaps,
            readFileMs = readFileMs,
            ocrTextMs = ocrTextMs,
            totalPdfPages = totalPageCount,
            skippedPdfPages = skippedPages,
            isPdf = true
        )
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
        val carrierRegex = Regex("""\b(VoLTE|VoWiFi|5G|4G|LTE|WiFi|Wi-Fi|BSNL)\b""", RegexOption.IGNORE_CASE)
        val clockRegex = Regex("""^\s*\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?\s*$""")

        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (index < 2 && (clockRegex.matches(line) || (batteryRegex.matches(line) && line.length <= 5))) {
                continue
            }

            var filtered = speedRegex.replace(line, "")
            if (index < 2) {
                filtered = carrierRegex.replace(filtered, "")
            }
            filtered = batteryRegex.replace(filtered, "")
            filtered = filtered.replace(Regex("""\s+"""), " ").trim()

            if (filtered.isNotBlank()) {
                cleanedLines.add(filtered)
            }
        }

        return cleanedLines.joinToString("\n")
    }
}
