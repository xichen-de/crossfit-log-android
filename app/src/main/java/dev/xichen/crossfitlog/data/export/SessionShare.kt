package dev.xichen.crossfitlog.data.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.xichen.crossfitlog.domain.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

suspend fun prepareSessionShare(context: Context, session: WorkoutSession): Intent = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, "session-exports").apply {
        check(isDirectory || mkdirs()) { "Could not create export directory." }
    }
    // Keep recent attachments available to receiving apps, and bound old cache growth.
    val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
    directory.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
    val date = Instant.ofEpochMilli(session.sessionTime).atZone(ZoneId.systemDefault()).toLocalDate()
    val file = File(directory, "CrossFit_Session_${date}_${UUID.randomUUID()}.json")
    file.writeText(DataExportCodec.encode(DataExportCodec.buildSession(session)))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.session-exports", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "Session JSON", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    Intent.createChooser(send, "Share session JSON")
}
