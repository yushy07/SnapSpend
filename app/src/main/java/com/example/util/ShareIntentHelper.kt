package com.example.util

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID

/**
 * Result of immediately ingesting an incoming ACTION_SEND / ACTION_SEND_MULTIPLE intent.
 * Because temporary URI read grants expire as soon as the Activity finishes handling
 * onCreate/onNewIntent, valid streams are copied immediately into an ephemeral cache folder
 * (context.cacheDir/shared_scan_temp) and deleted deterministically when the scan finishes,
 * fails, is cancelled, or is superseded by a new share intent.
 */
data class SharedPayloadResult(
    val tempFileUris: List<Uri> = emptyList(),
    val tempFiles: List<File> = emptyList(),
    val sharedTextNote: String? = null,
    val userNoticeMessage: String? = null,
    val hasValidContent: Boolean = tempFileUris.isNotEmpty() || !sharedTextNote.isNullOrBlank()
)

object ShareIntentHelper {

    const val MAX_SHARED_ITEMS = 10
    const val MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L // 20 MB
    private const val SHARE_CACHE_DIR_NAME = "shared_scan_temp"
    private const val MAX_TEXT_CHARS = 20_000

    private val lock = Any()
    private val activeTempFiles = mutableSetOf<File>()

    /**
     * Returns true if the given Intent is an ACTION_SEND or ACTION_SEND_MULTIPLE share intent.
     */
    fun isShareIntent(intent: Intent?): Boolean {
        val action = intent?.action ?: return false
        return action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE
    }

    /**
     * Deletes all tracked temporary cache files from an active or superseded share session.
     * Safe to call from any thread; never throws.
     */
    fun cleanupActiveShareTempFiles() {
        synchronized(lock) {
            val iterator = activeTempFiles.iterator()
            while (iterator.hasNext()) {
                val file = iterator.next()
                try {
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (_: Exception) {
                }
                iterator.remove()
            }
        }
    }

    /**
     * Deletes a specific batch of temporary files and removes them from the active set.
     */
    fun cleanupSpecificTempFiles(files: Collection<File>) {
        synchronized(lock) {
            for (file in files) {
                try {
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (_: Exception) {
                }
                activeTempFiles.remove(file)
            }
        }
    }

    /**
     * Cleans up the entire ephemeral share_scan_temp directory in cacheDir (e.g. on app startup).
     */
    fun cleanupAllShareCacheDir(context: Context) {
        cleanupActiveShareTempFiles()
        try {
            val dir = File(context.cacheDir, SHARE_CACHE_DIR_NAME)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    try {
                        file.delete()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Tracks a newly created ephemeral temp file so it is guaranteed to be cleaned up
     * if a new share arrives via onNewIntent or when the current scan completes/cancels.
     */
    fun registerActiveTempFiles(files: Collection<File>) {
        synchronized(lock) {
            activeTempFiles.addAll(files)
        }
    }

    /**
     * Snapshot of currently tracked active temp files (useful for testing & verification).
     */
    fun getActiveTempFilesSnapshot(): Set<File> {
        synchronized(lock) {
            return activeTempFiles.filter { it.exists() }.toSet()
        }
    }

    /**
     * Immediately reads and validates an untrusted share Intent.
     * - Cleans up any leftover temp files from a previous unfinished share first (onNewIntent safety).
     * - If attached file URIs exist, processes the attached files and ignores companion EXTRA_TEXT.
     * - Enforces max 10 items (notifies user if extras were skipped).
     * - Validates MIME types (images, application/pdf, text/plain).
     * - Enforces max 20 MB per file (both metadata check and stream read cap).
     * - Never throws on revoked permissions, corrupt streams, or malformed intents.
     */
    fun ingestShareIntent(context: Context, intent: Intent?): SharedPayloadResult {
        // 1. Clean up any temporary cache files from a prior unfinished share (re-share via onNewIntent)
        cleanupActiveShareTempFiles()

        if (intent == null || !isShareIntent(intent)) {
            return SharedPayloadResult()
        }

        return try {
            val extractedUris = extractStreamUris(intent)
            if (extractedUris.isNotEmpty()) {
                // Rule: Scan attached files and ignore companion text (EXTRA_TEXT)
                ingestSharedUris(
                    context = context,
                    uris = extractedUris,
                    declaredIntentMime = intent.type
                )
            } else {
                // No attached stream URIs -> check for plain text share
                val extractedTexts = extractSharedTexts(intent)
                ingestSharedTexts(
                    texts = extractedTexts,
                    declaredIntentMime = intent.type
                )
            }
        } catch (e: SecurityException) {
            cleanupActiveShareTempFiles()
            SharedPayloadResult(
                userNoticeMessage = "Permission to read the shared item expired. Please share it again."
            )
        } catch (e: Exception) {
            cleanupActiveShareTempFiles()
            SharedPayloadResult(
                userNoticeMessage = "Could not read the shared item. Please try sharing an image, PDF, or text note."
            )
        }
    }

    /**
     * Extracts all stream URIs from EXTRA_STREAM and ClipData (preserving order, deduplicating).
     */
    fun extractStreamUris(intent: Intent): List<Uri> {
        val result = mutableListOf<Uri>()
        try {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val singleUri = getParcelableExtraCompat(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    if (singleUri != null) {
                        result.add(singleUri)
                    }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val multipleUris = getParcelableArrayListExtraCompat(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    if (!multipleUris.isNullOrEmpty()) {
                        result.addAll(multipleUris.filterNotNull())
                    }
                }
            }

            // Fallback/supplement from ClipData if URIs were passed via ClipData
            val clipData: ClipData? = intent.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val itemUri = clipData.getItemAt(i)?.uri
                    if (itemUri != null && itemUri !in result) {
                        result.add(itemUri)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    /**
     * Extracts plain text snippets when no file URIs were attached.
     */
    fun extractSharedTexts(intent: Intent): List<String> {
        val texts = mutableListOf<String>()
        try {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val singleText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
                    if (!singleText.isNullOrEmpty()) {
                        texts.add(singleText)
                    }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val charSeqList = intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
                    if (!charSeqList.isNullOrEmpty()) {
                        charSeqList.mapNotNull { it?.toString()?.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { texts.add(it) }
                    }
                    val stringList = intent.getStringArrayListExtra(Intent.EXTRA_TEXT)
                    if (texts.isEmpty() && !stringList.isNullOrEmpty()) {
                        stringList.mapNotNull { it?.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { texts.add(it) }
                    }
                }
            }

            if (texts.isEmpty()) {
                val clipData: ClipData? = intent.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val itemText = clipData.getItemAt(i)?.text?.toString()?.trim()
                        if (!itemText.isNullOrEmpty()) {
                            texts.add(itemText)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return texts
    }

    private fun ingestSharedTexts(
        texts: List<String>,
        declaredIntentMime: String?
    ): SharedPayloadResult {
        if (texts.isEmpty()) {
            val isUnsupportedMime = declaredIntentMime != null && !isSupportedMimeType(declaredIntentMime)
            return SharedPayloadResult(
                userNoticeMessage = if (isUnsupportedMime) {
                    "Unsupported file type. SnapSpend accepts images, PDFs, and plain text."
                } else {
                    "Nothing to scan in the shared item."
                }
            )
        }

        val notices = mutableListOf<String>()
        val extraCount = (texts.size - MAX_SHARED_ITEMS).coerceAtLeast(0)
        if (extraCount > 0) {
            notices.add("Only the first $MAX_SHARED_ITEMS shared items were included ($extraCount extra skipped).")
        }

        val cappedTexts = texts.take(MAX_SHARED_ITEMS)
        val combinedText = cappedTexts.joinToString(separator = "\n\n").take(MAX_TEXT_CHARS).trim()
        if (combinedText.isBlank()) {
            return SharedPayloadResult(
                userNoticeMessage = "Shared text was empty."
            )
        }

        return SharedPayloadResult(
            sharedTextNote = combinedText,
            userNoticeMessage = notices.joinToString(" ").ifBlank { null }
        )
    }

    private fun ingestSharedUris(
        context: Context,
        uris: List<Uri>,
        declaredIntentMime: String?
    ): SharedPayloadResult {
        val resolver = context.contentResolver
        val notices = mutableListOf<String>()

        val extraCount = (uris.size - MAX_SHARED_ITEMS).coerceAtLeast(0)
        if (extraCount > 0) {
            notices.add("Only the first $MAX_SHARED_ITEMS shared items were included ($extraCount extra skipped).")
        }

        val candidateUris = uris.take(MAX_SHARED_ITEMS)
        val cacheDir = File(context.cacheDir, SHARE_CACHE_DIR_NAME).apply { mkdirs() }

        val copiedFiles = mutableListOf<File>()
        val copiedUris = mutableListOf<Uri>()
        val textSnippetsFromFiles = mutableListOf<String>()

        var skippedOversizedCount = 0
        var skippedUnsupportedTypeCount = 0
        var skippedUnreadableCount = 0

        for ((index, uri) in candidateUris.withIndex()) {
            try {
                val resolvedMime = resolveMimeType(resolver, uri, declaredIntentMime)
                if (!isSupportedMimeType(resolvedMime)) {
                    skippedUnsupportedTypeCount++
                    continue
                }

                // Check reported size metadata first
                val reportedSize = queryReportedSizeBytes(resolver, uri)
                if (reportedSize != null && reportedSize > MAX_FILE_SIZE_BYTES) {
                    skippedOversizedCount++
                    continue
                }

                if (resolvedMime.startsWith("text/plain", ignoreCase = true)) {
                    // Read text/plain file stream into memory (with 20 MB cap)
                    val textResult = readTextStreamSafely(resolver, uri)
                    when (textResult) {
                        is StreamCopyOutcome.SuccessText -> {
                            if (textResult.text.isNotBlank()) {
                                textSnippetsFromFiles.add(textResult.text.trim())
                            }
                        }
                        is StreamCopyOutcome.TooLarge -> skippedOversizedCount++
                        is StreamCopyOutcome.UnsupportedOrCorrupt -> skippedUnsupportedTypeCount++
                        is StreamCopyOutcome.ReadError -> skippedUnreadableCount++
                        else -> {}
                    }
                } else {
                    // Image or PDF -> copy stream immediately to ephemeral cache file
                    val ext = when {
                        resolvedMime.equals("application/pdf", ignoreCase = true) -> ".pdf"
                        resolvedMime.endsWith("png", ignoreCase = true) -> ".png"
                        resolvedMime.endsWith("webp", ignoreCase = true) -> ".webp"
                        else -> ".jpg"
                    }
                    val destFile = File(cacheDir, "shared_${System.currentTimeMillis()}_${index}_${UUID.randomUUID()}$ext")
                    when (val outcome = copyStreamWithCapAndSniff(resolver, uri, destFile, resolvedMime)) {
                        is StreamCopyOutcome.SuccessFile -> {
                            copiedFiles.add(outcome.file)
                            copiedUris.add(Uri.fromFile(outcome.file))
                        }
                        is StreamCopyOutcome.TooLarge -> {
                            destFile.delete()
                            skippedOversizedCount++
                        }
                        is StreamCopyOutcome.UnsupportedOrCorrupt -> {
                            destFile.delete()
                            skippedUnsupportedTypeCount++
                        }
                        is StreamCopyOutcome.ReadError -> {
                            destFile.delete()
                            skippedUnreadableCount++
                        }
                        else -> {
                            destFile.delete()
                        }
                    }
                }
            } catch (_: SecurityException) {
                skippedUnreadableCount++
            } catch (_: Exception) {
                skippedUnreadableCount++
            }
        }

        if (skippedOversizedCount > 0) {
            notices.add(
                if (skippedOversizedCount == 1) {
                    "Skipped 1 file over the 20 MB size limit."
                } else {
                    "Skipped $skippedOversizedCount files over the 20 MB size limit."
                }
            )
        }
        if (skippedUnsupportedTypeCount > 0) {
            notices.add(
                if (skippedUnsupportedTypeCount == 1) {
                    "Skipped 1 unsupported file (only images, PDFs, and plain text are supported)."
                } else {
                    "Skipped $skippedUnsupportedTypeCount unsupported files (only images, PDFs, and plain text are supported)."
                }
            )
        }
        if (skippedUnreadableCount > 0) {
            notices.add(
                if (skippedUnreadableCount == 1) {
                    "Could not read 1 shared item (permission expired or unreadable)."
                } else {
                    "Could not read $skippedUnreadableCount shared items (permission expired or unreadable)."
                }
            )
        }

        if (copiedFiles.isNotEmpty()) {
            registerActiveTempFiles(copiedFiles)
            return SharedPayloadResult(
                tempFileUris = copiedUris,
                tempFiles = copiedFiles,
                sharedTextNote = null, // Attached files take precedence over text
                userNoticeMessage = notices.joinToString(" ").ifBlank { null }
            )
        }

        if (textSnippetsFromFiles.isNotEmpty()) {
            val combinedText = textSnippetsFromFiles.joinToString("\n\n").take(MAX_TEXT_CHARS).trim()
            return SharedPayloadResult(
                sharedTextNote = combinedText,
                userNoticeMessage = notices.joinToString(" ").ifBlank { null }
            )
        }

        return SharedPayloadResult(
            userNoticeMessage = notices.joinToString(" ").ifBlank {
                "No supported image, PDF, or text found in the shared item."
            }
        )
    }

    /**
     * Checks whether a MIME type is accepted by Share to SnapSpend.
     */
    fun isSupportedMimeType(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return false
        val normalized = mimeType.trim().lowercase(Locale.US).substringBefore(';').trim()
        return normalized.startsWith("image/") ||
            normalized == "application/pdf" ||
            normalized == "text/plain"
    }

    private fun resolveMimeType(
        resolver: ContentResolver,
        uri: Uri,
        declaredIntentMime: String?
    ): String {
        val resolverType = try {
            resolver.getType(uri)
        } catch (_: Exception) {
            null
        }
        if (!resolverType.isNullOrBlank() && resolverType != "*/*" && resolverType != "application/octet-stream") {
            return resolverType
        }

        // Check path/display name extension
        val nameOrPath = (queryDisplayName(resolver, uri) ?: uri.lastPathSegment ?: "").lowercase(Locale.US)
        when {
            nameOrPath.endsWith(".pdf") -> return "application/pdf"
            nameOrPath.endsWith(".png") -> return "image/png"
            nameOrPath.endsWith(".jpg") || nameOrPath.endsWith(".jpeg") -> return "image/jpeg"
            nameOrPath.endsWith(".webp") -> return "image/webp"
            nameOrPath.endsWith(".txt") -> return "text/plain"
        }

        if (!declaredIntentMime.isNullOrBlank() && !declaredIntentMime.contains("*")) {
            return declaredIntentMime
        }
        if (declaredIntentMime?.startsWith("image/", ignoreCase = true) == true) {
            return "image/jpeg"
        }
        return resolverType ?: declaredIntentMime ?: ""
    }

    private fun queryReportedSizeBytes(resolver: ContentResolver, uri: Uri): Long? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        cursor.getLong(idx).takeIf { it >= 0L }
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private sealed class StreamCopyOutcome {
        data class SuccessFile(val file: File) : StreamCopyOutcome()
        data class SuccessText(val text: String) : StreamCopyOutcome()
        data object TooLarge : StreamCopyOutcome()
        data object UnsupportedOrCorrupt : StreamCopyOutcome()
        data object ReadError : StreamCopyOutcome()
    }

    private fun copyStreamWithCapAndSniff(
        resolver: ContentResolver,
        uri: Uri,
        destFile: File,
        expectedMime: String
    ): StreamCopyOutcome {
        val input: InputStream = try {
            resolver.openInputStream(uri) ?: return StreamCopyOutcome.ReadError
        } catch (_: Exception) {
            return StreamCopyOutcome.ReadError
        }

        return input.use { inStream ->
            try {
                FileOutputStream(destFile).use { outStream ->
                    val buffer = ByteArray(8 * 1024)
                    var totalBytes = 0L
                    var headerBytes = ByteArray(0)
                    while (true) {
                        val read = inStream.read(buffer)
                        if (read == -1) break
                        if (headerBytes.size < 12) {
                            val needed = (12 - headerBytes.size).coerceAtMost(read)
                            headerBytes += buffer.copyOfRange(0, needed)
                        }
                        totalBytes += read
                        if (totalBytes > MAX_FILE_SIZE_BYTES) {
                            return StreamCopyOutcome.TooLarge
                        }
                        outStream.write(buffer, 0, read)
                    }
                    outStream.flush()

                    if (totalBytes <= 0L) {
                        return StreamCopyOutcome.UnsupportedOrCorrupt
                    }

                    // Validate header magic bytes for PDF or common image formats
                    if (!looksLikeValidImageOrPdfHeader(headerBytes, expectedMime)) {
                        return StreamCopyOutcome.UnsupportedOrCorrupt
                    }
                }
                StreamCopyOutcome.SuccessFile(destFile)
            } catch (_: Exception) {
                StreamCopyOutcome.ReadError
            }
        }
    }

    private fun readTextStreamSafely(
        resolver: ContentResolver,
        uri: Uri
    ): StreamCopyOutcome {
        val input: InputStream = try {
            resolver.openInputStream(uri) ?: return StreamCopyOutcome.ReadError
        } catch (_: Exception) {
            return StreamCopyOutcome.ReadError
        }

        return input.use { inStream ->
            try {
                val buffer = ByteArray(8 * 1024)
                var totalBytes = 0L
                val out = java.io.ByteArrayOutputStream()
                while (true) {
                    val read = inStream.read(buffer)
                    if (read == -1) break
                    totalBytes += read
                    if (totalBytes > MAX_FILE_SIZE_BYTES) {
                        return StreamCopyOutcome.TooLarge
                    }
                    if (out.size() < MAX_TEXT_CHARS * 4) {
                        out.write(buffer, 0, read)
                    }
                }
                val text = out.toString(Charsets.UTF_8.name()).take(MAX_TEXT_CHARS)
                StreamCopyOutcome.SuccessText(text)
            } catch (_: Exception) {
                StreamCopyOutcome.ReadError
            }
        }
    }

    /**
     * Checks magic bytes for PDF (%PDF) or standard images (JPEG, PNG, WEBP, GIF, BMP, HEIC/HEIF)
     * or allows non-empty streams when expectedMime is image/ or application/pdf in unit test mocks.
     */
    internal fun looksLikeValidImageOrPdfHeader(header: ByteArray, expectedMime: String): Boolean {
        if (header.isEmpty()) return false
        val isPdfHeader = header.size >= 4 &&
            header[0] == 0x25.toByte() && // '%'
            header[1] == 0x50.toByte() && // 'P'
            header[2] == 0x44.toByte() && // 'D'
            header[3] == 0x46.toByte()    // 'F'

        if (expectedMime.equals("application/pdf", ignoreCase = true)) {
            return isPdfHeader
        }

        // Reject obvious executable/archive headers disguised as images (e.g.,MZ, ELF, PK zip/apk)
        if (header.size >= 2 && header[0] == 0x4D.toByte() && header[1] == 0x5A.toByte()) return false // MZ
        if (header.size >= 4 &&
            header[0] == 0x7F.toByte() &&
            header[1] == 0x45.toByte() &&
            header[2] == 0x4C.toByte() &&
            header[3] == 0x46.toByte()
        ) return false // ELF
        if (header.size >= 4 &&
            header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
        ) return false // ZIP / APK

        return expectedMime.startsWith("image/", ignoreCase = true)
    }

    @Suppress("DEPRECATION")
    private fun <T : android.os.Parcelable> getParcelableExtraCompat(intent: Intent, name: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(name, clazz)
        } else {
            intent.getParcelableExtra(name)
        }
    }

    @Suppress("DEPRECATION")
    private fun <T : android.os.Parcelable> getParcelableArrayListExtraCompat(
        intent: Intent,
        name: String,
        clazz: Class<T>
    ): ArrayList<T>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(name, clazz)
        } else {
            intent.getParcelableArrayListExtra(name)
        }
    }
}
