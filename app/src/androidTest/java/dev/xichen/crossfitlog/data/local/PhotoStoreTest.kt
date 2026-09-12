package dev.xichen.crossfitlog.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class PhotoStoreTest {
    @Test fun importsExtremelyNarrowImagesWithoutZeroSizedThumbnail() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PhotoStore(context)
        for ((width, height) in listOf(1 to 2000, 2000 to 1)) {
            val source = File.createTempFile("narrow-photo-", ".png", context.cacheDir)
            var stored: StoredPhoto? = null
            try {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    bitmap.recycle()
                }
                stored = store.import(context.contentResolver, Uri.fromFile(source), UUID.randomUUID().toString())
                val thumbnail = BitmapFactory.decodeFile(store.thumbnailFile(stored.thumbnailFilename)!!.path)
                assertNotNull(thumbnail)
                try {
                    assertTrue(thumbnail.width >= 1 && thumbnail.height >= 1)
                    assertTrue(maxOf(thumbnail.width, thumbnail.height) <= 480)
                } finally {
                    thumbnail.recycle()
                }
            } finally {
                source.delete()
                stored?.let {
                    store.deleteNow(it.photoFilename, it.thumbnailFilename)
                    store.deleteOcrSourceNow(it.ocrSourceFilename)
                }
            }
        }
    }
}
