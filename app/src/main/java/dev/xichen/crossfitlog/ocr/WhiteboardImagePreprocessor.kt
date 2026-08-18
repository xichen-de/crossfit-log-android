package dev.xichen.crossfitlog.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PreparedWhiteboardImages(
    val original: Bitmap,
    val enhanced: Bitmap,
) {
    fun recycle() {
        if (enhanced !== original) enhanced.recycle()
        original.recycle()
    }
}

/** Rotation correction, bounded decoding, modest upscaling, grayscale, and contrast enhancement. */
internal class WhiteboardImagePreprocessor(private val resolver: ContentResolver) {
    private companion object {
        const val MAX_DECODE_DIMENSION = 2560
        const val UPSCALE_BELOW_DIMENSION = 1600
        const val UPSCALE_TARGET_DIMENSION = 2000
        const val CONTRAST = 1.35f
    }

    suspend fun prepare(uri: Uri): PreparedWhiteboardImages = withContext(Dispatchers.IO) {
        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val bounds = BitmapFactory.Options().also { options ->
            options.inJustDecodeBounds = true
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This image could not be read." }
        var sampleSize = 1
        while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > MAX_DECODE_DIMENSION) sampleSize *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: error("This image could not be read.")
        val rotated = if (rotation == 0) decoded else Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true,
        ).also { decoded.recycle() }
        val largest = maxOf(rotated.width, rotated.height)
        val original = if (largest < UPSCALE_BELOW_DIMENSION) {
            val scale = UPSCALE_TARGET_DIMENSION.toFloat() / largest
            Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt(), (rotated.height * scale).toInt(), true)
                .also { rotated.recycle() }
        } else rotated
        PreparedWhiteboardImages(original, enhance(original))
    }

    private fun enhance(source: Bitmap): Bitmap {
        val target = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val translation = (-0.5f * CONTRAST + 0.5f) * 255f
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(ColorMatrix(floatArrayOf(
                CONTRAST, 0f, 0f, 0f, translation,
                0f, CONTRAST, 0f, 0f, translation,
                0f, 0f, CONTRAST, 0f, translation,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        Canvas(target).drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        })
        return target
    }
}
