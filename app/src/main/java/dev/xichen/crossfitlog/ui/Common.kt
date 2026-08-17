package dev.xichen.crossfitlog.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.xichen.crossfitlog.domain.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm")
private val dayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
fun formatDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateFormatter)
fun formatDay(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dayFormatter)
fun localDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
fun sessionsOnDay(sessions: List<WorkoutSession>, dayMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): List<WorkoutSession> {
    val day = localDate(dayMillis, zoneId)
    return sessions.filter { localDate(it.sessionTime, zoneId) == day }
}

@Composable
fun LocalPhoto(file: File?, description: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val image = rememberLocalImage(file)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (image != null) Image(image!!, description, Modifier.fillMaxSize(), contentScale = contentScale)
        else Icon(Icons.Outlined.ImageNotSupported, "No whiteboard photo", Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f))
    }
}

@Composable
fun FullscreenPhotoViewer(file: File?, description: String, onDismiss: () -> Unit) {
    val image = rememberLocalImage(file)
    var scale by remember(file?.path) { mutableFloatStateOf(1f) }
    var offset by remember(file?.path) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 6f)
        scale = nextScale
        offset = if (nextScale == 1f) Offset.Zero else offset + panChange
    }
    fun reset() { scale = 1f; offset = Offset.Zero }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("fullscreen-photo")) {
            if (image != null) Image(
                bitmap = image,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .transformable(transformState)
                    .pointerInput(file?.path) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) reset() else scale = 2.5f
                        })
                    },
            ) else Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.ImageNotSupported, null, Modifier.size(42.dp), tint = Color.White)
                Text("This photo is unavailable.", color = Color.White, textAlign = TextAlign.Center)
            }

            if (scale > 1f) IconButton(onClick = ::reset, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp).background(Color.Black.copy(alpha = .55f))) {
                Icon(Icons.Outlined.RestartAlt, "Reset zoom", tint = Color.White)
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Pinch to zoom · Drag to pan · Double-tap to zoom or reset",
                    color = Color.White.copy(alpha = .85f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.background(Color.Black.copy(alpha = .55f)).padding(12.dp),
                )
                Spacer(Modifier.height(16.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = .18f), CircleShape).testTag("close-fullscreen-photo"),
                ) {
                    Icon(Icons.Outlined.Close, "Close full-screen photo", Modifier.size(28.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun rememberLocalImage(file: File?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, file?.path, file?.lastModified()) {
        value = withContext(Dispatchers.IO) { file?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    }
    return image
}
