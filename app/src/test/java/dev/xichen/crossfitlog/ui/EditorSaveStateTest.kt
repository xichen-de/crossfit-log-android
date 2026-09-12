package dev.xichen.crossfitlog.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSaveStateTest {
    @Test fun saveWaitsForSessionAndPhotoWorkToFinish() {
        val ready = EditorUiState()
        assertTrue(ready.canSave)
        assertFalse(ready.copy(loading = true).canSave)
        assertFalse(ready.copy(photoProcessing = true).canSave)
        assertFalse(ready.copy(whiteboardScanning = true).canSave)
        assertFalse(ready.copy(saving = true).canSave)
        assertFalse(ready.copy(saved = true).canSave)
        assertTrue(ready.copy(error = "Previous save failed").canSave)
    }
}
