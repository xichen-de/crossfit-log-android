package dev.xichen.crossfitlog.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.xichen.crossfitlog.domain.WorkoutSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SessionShareTest {
    @Suppress("DEPRECATION")
    @Test fun sharedAttachmentIsReadableJsonWithTemporaryReadPermission() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = WorkoutSession("shared", 100, "Session note", null, null, 100, 100, emptyList())
        val chooser = prepareSessionShare(context, session)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/json", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        try {
            val content = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val export = Json.decodeFromString<CrossFitDataExport>(content)
            assertEquals(1, export.sessionCount)
            assertEquals("shared", export.sessions.single().id)
            assertEquals("Session note", export.sessions.single().sessionNote)
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }
}
