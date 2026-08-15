package dev.xichen.crossfitlog.data.local

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class StoredPhoto(val photoFilename: String, val thumbnailFilename: String)

class PhotoStore(private val context: Context) {
    private companion object {
        const val DECODE_MAX_DIMENSION = 2560
        const val PHOTO_MAX_DIMENSION = 1920
        const val PHOTO_JPEG_QUALITY = 84
        const val THUMBNAIL_MAX_DIMENSION = 480
        const val THUMBNAIL_JPEG_QUALITY = 78
    }

    private val photos = File(context.filesDir, "photos").apply { mkdirs() }
    private val thumbnails = File(photos, "thumbnails").apply { mkdirs() }

    fun photoFile(filename: String?): File? = filename?.let { File(photos, File(it).name) }?.takeIf(File::exists)
    fun thumbnailFile(filename: String?): File? = filename?.let { File(thumbnails, File(it).name) }?.takeIf(File::exists)
    fun newCameraFile(): File = File(context.cacheDir, "camera-${System.nanoTime()}.jpg")

    suspend fun import(resolver: ContentResolver, uri: Uri, sessionId: String): StoredPhoto = withContext(Dispatchers.IO) {
        val orientation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val bounds = BitmapFactory.Options().also { options ->
            options.inJustDecodeBounds = true
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This image could not be read." }
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > DECODE_MAX_DIMENSION) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("This image could not be read.")
        val rotated = if (orientation == 0) decoded else Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(orientation.toFloat()) }, true
        ).also { decoded.recycle() }
        val full = scaleDown(rotated, PHOTO_MAX_DIMENSION)
        val thumb = scaleDown(full, THUMBNAIL_MAX_DIMENSION)
        // A new filename keeps an unsaved edit from overwriting the photo referenced by SQLite.
        val filename = "$sessionId-${UUID.randomUUID()}.jpg"
        val photoTarget = File(photos, filename)
        val thumbTarget = File(thumbnails, filename)
        try {
            writeJpegAtomically(full, photoTarget, PHOTO_JPEG_QUALITY)
            writeJpegAtomically(thumb, thumbTarget, THUMBNAIL_JPEG_QUALITY)
        } catch (error: Throwable) {
            photoTarget.delete()
            thumbTarget.delete()
            throw error
        } finally {
            if (thumb !== full) thumb.recycle()
            if (full !== rotated) full.recycle()
            rotated.recycle()
        }
        StoredPhoto(filename, filename)
    }

    suspend fun delete(photoFilename: String?, thumbnailFilename: String?) = withContext(Dispatchers.IO) {
        deleteNow(photoFilename, thumbnailFilename)
    }

    fun deleteNow(photoFilename: String?, thumbnailFilename: String?) {
        photoFilename?.let { File(photos, File(it).name).delete() }
        thumbnailFilename?.let { File(thumbnails, File(it).name).delete() }
    }

    fun installRestoredPhoto(staged: File, sessionId: String): StoredPhoto {
        val filename = "$sessionId.jpg"
        val full = File(photos, filename)
        val thumbFile = File(thumbnails, filename)
        check(!full.exists() && !thumbFile.exists()) { "A photo with this ID already exists." }
        staged.copyTo(full, overwrite = false)
        val bitmap = BitmapFactory.decodeFile(full.path) ?: run { full.delete(); error("Restored photo is unreadable.") }
        val thumb = scaleDown(bitmap, THUMBNAIL_MAX_DIMENSION)
        try { writeJpegAtomically(thumb, thumbFile, THUMBNAIL_JPEG_QUALITY) }
        finally { if (thumb !== bitmap) thumb.recycle(); bitmap.recycle() }
        return StoredPhoto(filename, filename)
    }

    private fun scaleDown(source: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxDimension) return source
        val scale = maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
    }

    private fun writeJpegAtomically(bitmap: Bitmap, target: File, quality: Int) {
        val temp = File(target.parentFile, ".${target.name}.tmp")
        FileOutputStream(temp).use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)) }
        check(temp.renameTo(target)) { "Could not store the image." }
    }
}
