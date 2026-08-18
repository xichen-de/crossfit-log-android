package dev.xichen.crossfitlog.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.xichen.crossfitlog.domain.RecognizedWhiteboardLine
import dev.xichen.crossfitlog.domain.RecognizedWhiteboardText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitWhiteboardTextRecognizer(private val context: Context) : WhiteboardTextRecognizer {
    override suspend fun recognize(image: Uri): RecognizedWhiteboardText {
        val prepared = WhiteboardImagePreprocessor(context.contentResolver).prepare(image)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val original = recognizer.process(InputImage.fromBitmap(prepared.original, 0)).await()
            val enhanced = try {
                recognizer.process(InputImage.fromBitmap(prepared.enhanced, 0)).await()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                null // The original pass is still useful if enhancement recognition alone fails.
            }
            val lines = (original.textBlocks + enhanced?.textBlocks.orEmpty()).flatMap { block ->
                block.lines.map { line -> RecognizedWhiteboardLine(line.text, line.elements.map { it.text }) }
            }.distinctBy { line -> line.text.trim().lowercase() }
            RecognizedWhiteboardText(lines)
        } finally {
            recognizer.close()
            prepared.recycle()
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
