package dev.xichen.crossfitlog.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import dev.xichen.crossfitlog.data.local.PhotoStore
import java.io.File

@Composable
fun CameraCaptureScreen(photoStore: PhotoStore, onCancel: () -> Unit, onCaptured: (File) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> granted = allowed; denied = !allowed }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    if (denied) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera permission is needed only when you take a whiteboard photo."); Spacer(Modifier.height(12.dp)); Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Try again") }; TextButton(onClick = onCancel) { Text("Cancel") }
        } }; return
    }
    if (!granted) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val controller = remember { LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) } }
    DisposableEffect(lifecycleOwner) { runCatching { controller.bindToLifecycle(lifecycleOwner) }.onFailure { onError("The camera is unavailable.") }; onDispose { controller.unbind() } }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { PreviewView(it).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FIT_CENTER } }, Modifier.fillMaxSize())
        IconButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) { Icon(Icons.Outlined.Close, "Cancel camera", tint = MaterialTheme.colorScheme.onPrimary) }
        FloatingActionButton(onClick = {
            val file = photoStore.newCameraFile()
            controller.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onCaptured(file)
                override fun onError(exception: ImageCaptureException) { file.delete(); onError("The photo could not be taken.") }
            })
        }, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) { Icon(Icons.Outlined.PhotoCamera, "Take photo") }
    }
}
