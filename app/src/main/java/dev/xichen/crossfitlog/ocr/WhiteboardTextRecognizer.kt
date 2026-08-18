package dev.xichen.crossfitlog.ocr

import android.net.Uri
import dev.xichen.crossfitlog.domain.RecognizedWhiteboardText

interface WhiteboardTextRecognizer {
    suspend fun recognize(image: Uri): RecognizedWhiteboardText
}
