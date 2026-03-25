package com.example.cardnote.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardnote.data.NoteDatabase
import com.example.cardnote.data.NoteEntity
import com.example.cardnote.data.NoteRepository
import com.example.cardnote.util.ImageStorageManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FilterState(
    val showDownloaded: Boolean = true,
    val showNotDownloaded: Boolean = true
) {
    val showAll: Boolean get() = showDownloaded == showNotDownloaded
}

data class NoteUiState(
    val filteredNotes: List<NoteEntity> = emptyList(),
    val filterState: FilterState = FilterState(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = false,
    val currentPagerIndex: Int = 0,
    val showAddSheet: Boolean = false,
    val noteToEdit: NoteEntity? = null,   // 非 null 时打开编辑 Sheet
    val noteToDelete: NoteEntity? = null,
    val snackbarMessage: String? = null
)

private data class QueryKey(val filter: FilterState, val search: String)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NoteRepository
    private val appContext = application.applicationContext

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _rawSearch = MutableStateFlow("")
    private val _searchQuery = _rawSearch
        .debounce(300).distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _uiState = MutableStateFlow(NoteUiState())
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    init {
        val dao = NoteDatabase.getDatabase(application).noteDao()
        repository = NoteRepository(dao)

        viewModelScope.launch {
            combine(_filterState, _searchQuery) { f, s -> QueryKey(f, s) }
                .flatMapLatest { key ->
                    if (key.search.isBlank()) when {
                        key.filter.showAll        -> repository.getAllNotes()
                        key.filter.showDownloaded -> repository.getDownloadedNotes()
                        else                      -> repository.getNotDownloadedNotes()
                    } else repository.searchNotes(key.search, key.filter)
                }
                .collect { notes ->
                    _uiState.update { state ->
                        state.copy(
                            filteredNotes = notes,
                            currentPagerIndex = if (state.currentPagerIndex >= notes.size) 0
                            else state.currentPagerIndex
                        )
                    }
                }
        }
        viewModelScope.launch {
            _searchQuery.collect { q ->
                _uiState.update { it.copy(searchQuery = q, currentPagerIndex = 0) }
            }
        }
    }

    // ── 搜索 ──
    fun onSearchQueryChange(query: String) {
        _rawSearch.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }
    fun toggleSearch() {
        val active = !_uiState.value.isSearchActive
        _uiState.update { it.copy(isSearchActive = active) }
        if (!active) clearSearch()
    }
    fun clearSearch() {
        _rawSearch.value = ""
        _uiState.update { it.copy(searchQuery = "", isSearchActive = false, currentPagerIndex = 0) }
    }

    // ── 筛选 ──
    fun toggleDownloadedFilter() {
        _filterState.update { it.copy(showDownloaded = !it.showDownloaded) }
        _uiState.update { it.copy(currentPagerIndex = 0) }
    }
    fun toggleNotDownloadedFilter() {
        _filterState.update { it.copy(showNotDownloaded = !it.showNotDownloaded) }
        _uiState.update { it.copy(currentPagerIndex = 0) }
    }

    // ── Pager ──
    fun onPagerPageChanged(index: Int) {
        _uiState.update { it.copy(currentPagerIndex = index) }
    }

    // ── 新增 Sheet ──
    fun showAddSheet() { _uiState.update { it.copy(showAddSheet = true) } }
    fun hideAddSheet() { _uiState.update { it.copy(showAddSheet = false) } }

    fun addNote(name: String, url: String, isDownloaded: Boolean, remarks: String, imageUris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val paths = ImageStorageManager.copyAllToPrivateStorage(appContext, imageUris)
                repository.insertNote(NoteEntity(name = name.trim(), url = url.trim(),
                    isDownloaded = isDownloaded, remarks = remarks.trim(), images = paths))
                _uiState.update { it.copy(showAddSheet = false, snackbarMessage = "笔记已添加") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "添加失败：${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ── 编辑 Sheet ──
    fun showEditSheet(note: NoteEntity) { _uiState.update { it.copy(noteToEdit = note) } }
    fun hideEditSheet() { _uiState.update { it.copy(noteToEdit = null) } }

    /**
     * 保存编辑：
     * - newImageUris: 用户本次新选的外部 URI（需复制）
     * - keptPaths:    用户保留的已有内部路径（直接沿用）
     * - 原来有但不在 keptPaths 里的路径 → 删除文件
     */
    fun saveEdit(
        note: NoteEntity,
        name: String,
        url: String,
        isDownloaded: Boolean,
        remarks: String,
        keptPaths: List<String>,
        newImageUris: List<Uri>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 删除被移除的旧图片文件
                val removedPaths = note.images - keptPaths.toSet()
                ImageStorageManager.deleteImages(removedPaths)
                // 复制新图片
                val newPaths = ImageStorageManager.copyAllToPrivateStorage(appContext, newImageUris)
                val finalPaths = (keptPaths + newPaths).take(9)
                repository.updateNote(note.copy(
                    name = name.trim(), url = url.trim(),
                    isDownloaded = isDownloaded, remarks = remarks.trim(),
                    images = finalPaths
                ))
                _uiState.update { it.copy(noteToEdit = null, snackbarMessage = "笔记已更新") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "保存失败：${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ── 删除 ──
    fun requestDelete(note: NoteEntity) { _uiState.update { it.copy(noteToDelete = note) } }
    fun cancelDelete()  { _uiState.update { it.copy(noteToDelete = null) } }
    fun confirmDelete() {
        val note = _uiState.value.noteToDelete ?: return
        viewModelScope.launch {
            ImageStorageManager.deleteImages(note.images)
            repository.deleteNote(note)
            _uiState.update { it.copy(noteToDelete = null, snackbarMessage = "「${note.name}」已删除") }
        }
    }

    fun toggleDownloadStatus(note: NoteEntity) {
        viewModelScope.launch { repository.updateDownloadStatus(note.id, !note.isDownloaded) }
    }

    fun removeImageFromNote(note: NoteEntity, imagePath: String) {
        viewModelScope.launch {
            ImageStorageManager.deleteImage(imagePath)
            repository.updateNote(note.copy(images = note.images - imagePath))
        }
    }

    fun clearSnackbar() { _uiState.update { it.copy(snackbarMessage = null) } }
}
