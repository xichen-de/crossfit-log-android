package dev.xichen.crossfitlog.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.xichen.crossfitlog.data.local.PhotoStore
import dev.xichen.crossfitlog.data.repository.WorkoutRepository
import dev.xichen.crossfitlog.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class EditorMovement(val id: String = UUID.randomUUID().toString(), val name: String = "", val load: String = "", val result: String = "", val note: String = "")

@Serializable
data class EditorDraft(
    val id: String = UUID.randomUUID().toString(),
    val sessionTime: Long = System.currentTimeMillis(),
    val sessionNote: String = "",
    val photoFilename: String? = null,
    val thumbnailFilename: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val movements: List<EditorMovement> = listOf(EditorMovement()),
)

data class EditorUiState(
    val draft: EditorDraft = EditorDraft(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val photoProcessing: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class EditorViewModel(
    private val savedState: SavedStateHandle,
    private val repository: WorkoutRepository,
    private val photoStore: PhotoStore,
    private val existingId: String?,
) : ViewModel() {
    val isEditing: Boolean get() = existingId != null
    private val json = Json { ignoreUnknownKeys = true }
    private val saveGuard = DuplicateSaveGuard()
    private val _state = MutableStateFlow(EditorUiState(loading = existingId != null))
    val state = _state.asStateFlow()
    private var initialized = false
    private var originalPhotoFilename: String? = null
    private var originalThumbnailFilename: String? = null

    init {
        val restored = savedState.get<String>("draft")?.let { runCatching { json.decodeFromString<EditorDraft>(it) }.getOrNull() }
        if (restored != null) {
            _state.value = EditorUiState(draft = restored)
            initialized = true
            if (savedState.contains("originalPhotoFilename")) {
                originalPhotoFilename = savedState.get<String>("originalPhotoFilename")?.ifEmpty { null }
                originalThumbnailFilename = savedState.get<String>("originalThumbnailFilename")?.ifEmpty { null }
            } else if (existingId != null) {
                _state.update { it.copy(loading = true) }
                viewModelScope.launch {
                    rememberOriginalPhoto(repository.getSession(existingId))
                    _state.update { it.copy(loading = false) }
                }
            }
        } else if (existingId != null) {
            viewModelScope.launch {
                repository.getSession(existingId)?.let { session ->
                    rememberOriginalPhoto(session)
                    update(session.toDraft(), persist = true)
                } ?: run { _state.update { it.copy(loading = false, error = "This session no longer exists.") } }
                initialized = true
            }
        } else {
            persist(_state.value.draft)
            initialized = true
        }
    }

    fun setNote(value: String) = mutate { copy(sessionNote = value) }
    fun setTime(value: Long) = mutate { copy(sessionTime = value) }
    fun addMovement() = mutate { copy(movements = movements + EditorMovement()) }
    fun removeMovement(index: Int) = mutate { copy(movements = movements.filterIndexed { i, _ -> i != index }.ifEmpty { listOf(EditorMovement()) }) }
    fun moveMovement(index: Int, delta: Int) = mutate {
        val target = index + delta
        if (target !in movements.indices) this else copy(movements = movements.toMutableList().also { list ->
            val item = list.removeAt(index); list.add(target, item)
        })
    }
    fun updateMovement(index: Int, value: EditorMovement) = mutate { copy(movements = movements.toMutableList().also { it[index] = value }) }
    fun clearError() = _state.update { it.copy(error = null) }
    fun showError(message: String) = _state.update { it.copy(error = message) }
    fun removePhoto() {
        val current = _state.value.draft
        if (current.photoFilename != originalPhotoFilename || current.thumbnailFilename != originalThumbnailFilename) {
            photoStore.deleteNow(current.photoFilename, current.thumbnailFilename)
        }
        mutate { copy(photoFilename = null, thumbnailFilename = null) }
    }

    fun suggestions(text: String): Flow<List<String>> = repository.suggestions(text)

    fun importPhoto(resolver: ContentResolver, uri: Uri) {
        if (_state.value.photoProcessing) return
        viewModelScope.launch {
            _state.update { it.copy(photoProcessing = true, error = null) }
            val previousPhoto = _state.value.draft.photoFilename
            val previousThumbnail = _state.value.draft.thumbnailFilename
            runCatching { photoStore.import(resolver, uri, _state.value.draft.id) }
                .onSuccess { stored ->
                    mutate { copy(photoFilename = stored.photoFilename, thumbnailFilename = stored.thumbnailFilename) }
                    if (previousPhoto != originalPhotoFilename || previousThumbnail != originalThumbnailFilename) {
                        photoStore.delete(previousPhoto, previousThumbnail)
                    }
                }
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "The photo could not be saved.") } }
            _state.update { it.copy(photoProcessing = false) }
        }
    }

    fun save() {
        val current = _state.value
        if (current.saved || !saveGuard.tryStart()) return
        val invalid = current.draft.movements.indexOfFirst { !isMovementNameValid(it.name) }
        if (invalid >= 0) { saveGuard.reset(); _state.update { it.copy(error = "Movement ${invalid + 1} needs a name.") }; return }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val domain = _state.value.draft.toDomain()
                if (existingId == null) repository.create(domain) else repository.update(domain)
            }.onSuccess {
                val savedDraft = _state.value.draft
                if (savedDraft.photoFilename != originalPhotoFilename || savedDraft.thumbnailFilename != originalThumbnailFilename) {
                    photoStore.delete(originalPhotoFilename, originalThumbnailFilename)
                    originalPhotoFilename = savedDraft.photoFilename
                    originalThumbnailFilename = savedDraft.thumbnailFilename
                }
                savedState.remove<String>("draft")
                savedState.remove<String>("originalPhotoFilename")
                savedState.remove<String>("originalThumbnailFilename")
                _state.update { it.copy(saving = false, saved = true) }
            }.onFailure { error ->
                saveGuard.reset()
                _state.update { it.copy(saving = false, error = error.message ?: "The session could not be saved.") }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val sessionId = existingId ?: return
        viewModelScope.launch {
            val session = repository.getSession(sessionId) ?: return@launch
            runCatching {
                repository.delete(session)
                photoStore.delete(session.photoFilename, session.thumbnailFilename)
                val draft = _state.value.draft
                if (draft.photoFilename != session.photoFilename || draft.thumbnailFilename != session.thumbnailFilename) {
                    photoStore.delete(draft.photoFilename, draft.thumbnailFilename)
                }
            }.onSuccess { onDeleted() }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "The session could not be deleted.") } }
        }
    }

    fun photoFile(): File? = photoStore.photoFile(_state.value.draft.photoFilename)

    override fun onCleared() {
        if (!_state.value.saved) {
            val draft = _state.value.draft
            if (draft.photoFilename != originalPhotoFilename || draft.thumbnailFilename != originalThumbnailFilename) {
                photoStore.deleteNow(draft.photoFilename, draft.thumbnailFilename)
            }
        }
        super.onCleared()
    }

    private fun mutate(block: EditorDraft.() -> EditorDraft) = update(_state.value.draft.block(), true)
    private fun update(draft: EditorDraft, persist: Boolean) {
        _state.update { it.copy(draft = draft, loading = false) }
        if (persist) persist(draft)
    }
    private fun persist(draft: EditorDraft) { savedState["draft"] = json.encodeToString(draft) }
    private fun rememberOriginalPhoto(session: WorkoutSession?) {
        originalPhotoFilename = session?.photoFilename
        originalThumbnailFilename = session?.thumbnailFilename
        savedState["originalPhotoFilename"] = originalPhotoFilename.orEmpty()
        savedState["originalThumbnailFilename"] = originalThumbnailFilename.orEmpty()
    }

    private fun WorkoutSession.toDraft() = EditorDraft(id, sessionTime, sessionNote, photoFilename, thumbnailFilename, createdAt,
        movements.map { EditorMovement(it.id, it.name, it.load, it.result, it.note) })
    private fun EditorDraft.toDomain(): WorkoutSession {
        val now = System.currentTimeMillis()
        return WorkoutSession(id, sessionTime, cleanText(sessionNote), photoFilename, thumbnailFilename, createdAt, now,
            movements.mapIndexed { index, m -> MovementRecord(m.id, id, cleanText(m.name), normalizeMovementName(m.name), cleanText(m.load), cleanText(m.result), cleanText(m.note), index) })
    }
}

class DuplicateSaveGuard {
    private val running = AtomicBoolean(false)
    fun tryStart(): Boolean = running.compareAndSet(false, true)
    fun reset() = running.set(false)
}
