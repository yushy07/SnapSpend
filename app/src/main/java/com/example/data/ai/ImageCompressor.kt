package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "ImageCompressor"

/**
 * Memory-safe Image Downscaling and JPEG Compression utility.
 * Prevents OutOfMemoryErrors on 12MP–50MP camera photos by using inJustDecodeBounds + inSampleSize,
 * normalizes EXIF orientation, caps the longest edge to 1024px, and compresses at 75% JPEG quality.
 */
object ImageCompressor {

    private const val MAX_VISION_DIMENSION = 1024
    private const val JPEG_QUALITY = 75

    /**
     * Prepares a compressed, orientation-corrected Bitmap for Gemini Multimodal Vision Fallback.
     */
    suspend fun compressForVisionFallback(
        context: Context,
        uri: Uri
    ): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = compressUriToJpegBytes(context, uri, maxEdge = MAX_VISION_DIMENSION, quality = JPEG_QUALITY)
            ?: return@withContext null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * Compresses a given Bitmap to a 75% quality JPEG byte array with maxEdge capping.
     */
    fun compressBitmapToJpegBytes(
        source: Bitmap,
        maxEdge: Int = MAX_VISION_DIMENSION,
        quality: Int = JPEG_QUALITY
    ): ByteArray {
        val scaled = scaleBitmapToMaxEdge(source, maxEdge)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        if (scaled != source && !scaled.isRecycled) {
            scaled.recycle()
        }
        return stream.toByteArray()
    }

    /**
     * Safely reads a URI, inspects dimensions, reads EXIF orientation, applies downsampling,
     * scales to maxEdge, and compresses to JPEG bytes.
     */
    suspend fun compressUriToJpegBytes(
        context: Context,
        uri: Uri,
        maxEdge: Int = MAX_VISION_DIMENSION,
        quality: Int = JPEG_QUALITY
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // 1. Inspect bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@withContext null

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return@withContext null

            // 2. Read EXIF rotation
            val rotationDegrees = getExifRotationDegrees(context, uri)

            // 3. Compute inSampleSize
            val longestEdge = max(origWidth, origHeight)
            var inSampleSize = 1
            while ((longestEdge / (inSampleSize * 2)) >= maxEdge) {
                inSampleSize *= 2
            }

            // 4. Decode downsampled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val downsampled = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            // 5. Apply EXIF rotation if needed
            val orientedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(downsampled, 0, 0, downsampled.width, downsampled.height, matrix, true)
                if (rotated != downsampled && !downsampled.isRecycled) {
                    downsampled.recycle()
                }
                rotated
            } else {
                downsampled
            }

            // 6. Scale so longest edge <= maxEdge
            val finalScaled = scaleBitmapToMaxEdge(orientedBitmap, maxEdge)

            // 7. Compress to JPEG
            val out = ByteArrayOutputStream()
            finalScaled.compress(Bitmap.CompressFormat.JPEG, quality, out)

            if (finalScaled != orientedBitmap && !finalScaled.isRecycled) {
                finalScaled.recycle()
            }
            if (!orientedBitmap.isRecycled) {
                orientedBitmap.recycle()
            }

            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image at uri $uri", e)
            null
        }
    }

    /**
     * Decodes a scaled Bitmap specifically for on-device ML Kit OCR (e.g. ~1280px width).
     */
    suspend fun decodeScaledBitmapForOcr(
        context: Context,
        uri: Uri,
        targetWidth: Int = 1280
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return@withContext null

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return@withContext null

            val rotationDegrees = getExifRotationDegrees(context, uri)

            var sampleSize = 1
            while ((origWidth / (sampleSize * 2)) >= targetWidth) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            val oriented = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                if (rotated != raw && !raw.isRecycled) {
                    raw.recycle()
                }
                rotated
            } else {
                raw
            }

            // Scale width to targetWidth if wider
            if (oriented.width > targetWidth) {
                val ratio = targetWidth.toFloat() / oriented.width.toFloat()
                val targetHeight = (oriented.height * ratio).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(oriented, targetWidth, targetHeight, true)
                if (scaled != oriented && !oriented.isRecycled) {
                    oriented.recycle()
                }
                scaled
            } else {
                oriented
            }
        } catch (e: Exception) {
            Log.e(TAG, "decodeScaledBitmapForOcr failed for $uri", e)
            null
        }
    }

    private fun scaleBitmapToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val currentMax = max(width, height)
        if (currentMax <= maxEdge) return bitmap

        val scale = maxEdge.toFloat() / currentMax.toFloat()
        val newWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val newHeight = (height * scale).roundToInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun getExifRotationDegrees(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF orientation for $uri", e)
            0
        }
    }
}
