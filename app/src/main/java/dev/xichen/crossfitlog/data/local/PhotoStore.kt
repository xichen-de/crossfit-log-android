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

data class StoredPhoto(
    val photoFilename: String,
    val thumbnailFilename: String,
    val ocrSourceFilename: String? = null,
)

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
    private val ocrSources = File(context.cacheDir, "whiteboard-ocr").apply { mkdirs() }
    val rootDirectory: File get() = photos

    fun photoFile(filename: String?): File? = filename?.let { File(photos, File(it).name) }?.takeIf(File::exists)
    fun thumbnailFile(filename: String?): File? = filename?.let { File(thumbnails, File(it).name) }?.takeIf(File::exists)
    fun ocrSourceFile(filename: String?): File? = filename?.let { File(ocrSources, File(it).name) }?.takeIf(File::exists)
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
        val ocrSourceFilename = "${UUID.randomUUID()}.source"
        val photoTarget = File(photos, filename)
        val thumbTarget = File(thumbnails, filename)
        val ocrSourceTarget = File(ocrSources, ocrSourceFilename)
        try {
            writeJpegAtomically(full, photoTarget, PHOTO_JPEG_QUALITY)
            writeJpegAtomically(thumb, thumbTarget, THUMBNAIL_JPEG_QUALITY)
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(ocrSourceTarget).use(input::copyTo)
            } ?: error("This image could not be read.")
        } catch (error: Throwable) {
            photoTarget.delete()
            thumbTarget.delete()
            ocrSourceTarget.delete()
            throw error
        } finally {
            if (thumb !== full) thumb.recycle()
            if (full !== rotated) full.recycle()
            rotated.recycle()
        }
        StoredPhoto(filename, filename, ocrSourceFilename)
    }

    suspend fun delete(photoFilename: String?, thumbnailFilename: String?) = withContext(Dispatchers.IO) {
        deleteNow(photoFilename, thumbnailFilename)
    }

    fun deleteNow(photoFilename: String?, thumbnailFilename: String?) {
        photoFilename?.let { File(photos, File(it).name).delete() }
        thumbnailFilename?.let { File(thumbnails, File(it).name).delete() }
    }

    fun deleteOcrSourceNow(filename: String?) {
        filename?.let { File(ocrSources, File(it).name).delete() }
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

    fun copySnapshot(references: List<Pair<String, String?>>, targetPhotos: File) {
        val targetThumbnails = File(targetPhotos, "thumbnails")
        val seen = mutableSetOf<String>()
        references.forEach { (photoFilename, thumbnailFilename) ->
            require(File(photoFilename).name == photoFilename && seen.add("photo:$photoFilename")) { "A stored photo reference is invalid." }
            val thumbnail = requireNotNull(thumbnailFilename) { "A stored thumbnail reference is missing." }
            require(File(thumbnail).name == thumbnail && seen.add("thumbnail:$thumbnail")) { "A stored thumbnail reference is invalid." }
            val sourcePhoto = requireNotNull(photoFile(photoFilename)) { "A workout photo is missing: $photoFilename" }
            val sourceThumbnail = requireNotNull(thumbnailFile(thumbnail)) { "A workout thumbnail is missing: $thumbnail" }
            targetPhotos.mkdirs()
            targetThumbnails.mkdirs()
            sourcePhoto.copyTo(File(targetPhotos, photoFilename), overwrite = false)
            sourceThumbnail.copyTo(File(targetThumbnails, thumbnail), overwrite = false)
        }
    }

    fun validateRestoredImage(file: File) {
        require(file.isFile) { "A workout photo is missing." }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "A restored photo is unreadable." }
        var sample = 1
        while (maxOf(options.outWidth / sample, options.outHeight / sample) > THUMBNAIL_MAX_DIMENSION) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("A restored photo is unreadable.")
        decoded.recycle()
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
