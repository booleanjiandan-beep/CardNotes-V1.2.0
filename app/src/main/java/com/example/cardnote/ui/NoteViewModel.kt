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

// 筛选状态数据类
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
    // 新增/编辑表单
    val showAddSheet: Boolean = false,
    val showEditSheet: Boolean = false,
    val noteToEdit: NoteEntity? = null,
    // 删除确认：非 null 时弹出确认弹窗
    val noteToDelete: NoteEntity? = null,
    // 操作反馈 snackbar
    val snackbarMessage: String? = null
)

// 内部驱动查询的组合 key
private data class QueryKey(val filter: FilterState, val search: String)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NoteRepository
    private val appContext = application.applicationContext

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _rawSearch = MutableStateFlow("")
    private val _searchQuery = _rawSearch
        .debounce(300)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _uiState = MutableStateFlow(NoteUiState())
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    init {
        val dao = NoteDatabase.getDatabase(application).noteDao()
        repository = NoteRepository(dao)

        viewModelScope.launch {
            combine(_filterState, _searchQuery) { filter, search ->
                QueryKey(filter, search)
            }
                .flatMapLatest { key ->
                    if (key.search.isBlank()) {
                        when {
                            key.filter.showAll        -> repository.getAllNotes()
                            key.filter.showDownloaded -> repository.getDownloadedNotes()
                            else                      -> repository.getNotDownloadedNotes()
                        }
                    } else {
                        repository.searchNotes(key.search, key.filter)
                    }
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

    // ── 新增表单 ──

    fun showAddSheet() {
        _uiState.update { it.copy(showAddSheet = true) }
    }

    fun hideAddSheet() {
        _uiState.update { it.copy(showAddSheet = false) }
    }

    fun showEditSheet(note: NoteEntity) {
        _uiState.update { it.copy(showEditSheet = true, noteToEdit = note) }
    }

    fun hideEditSheet() {
        _uiState.update { it.copy(showEditSheet = false, noteToEdit = null) }
    }

    /**
     * 新增笔记：
     * 1. 将传入的外部 URI 列表复制到 App 私有目录
     * 2. 用内部路径替换 URI 存入数据库
     */
    fun addNote(
        name: String,
        url: String,
        isDownloaded: Boolean,
        remarks: String,
        imageUris: List<Uri>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 复制图片到私有目录，得到内部绝对路径
                val internalPaths = ImageStorageManager.copyAllToPrivateStorage(appContext, imageUris)

                val note = NoteEntity(
                    name = name.trim(),
                    url = url.trim(),
                    isDownloaded = isDownloaded,
                    remarks = remarks.trim(),
                    images = internalPaths
                )
                repository.insertNote(note)
                _uiState.update { it.copy(showAddSheet = false, snackbarMessage = "笔记已添加") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "添加失败：${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateNote(
        origin: NoteEntity,
        name: String,
        url: String,
        isDownloaded: Boolean,
        remarks: String,
        imageUris: List<Uri>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val existingInternalPaths = imageUris.mapNotNull { uri ->
                    when {
                        uri.scheme == "file" -> uri.path
                        uri.toString().startsWith("/") -> uri.toString()
                        else -> null
                    }
                }
                val externalUris = imageUris.filter { uri ->
                    val asString = uri.toString()
                    !(uri.scheme == "file" || asString.startsWith("/"))
                }
                val copiedPaths = ImageStorageManager.copyAllToPrivateStorage(appContext, externalUris)
                val finalPaths = (existingInternalPaths + copiedPaths).distinct()

                val updated = origin.copy(
                    name = name.trim(),
                    url = url.trim(),
                    isDownloaded = isDownloaded,
                    remarks = remarks.trim(),
                    images = finalPaths
                )
                repository.updateNote(updated)
                _uiState.update {
                    it.copy(
                        showEditSheet = false,
                        noteToEdit = null,
                        snackbarMessage = "笔记已更新"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "更新失败：${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ── 删除（带二次确认）──

    /** 触发删除确认弹窗 */
    fun requestDelete(note: NoteEntity) {
        _uiState.update { it.copy(noteToDelete = note) }
    }

    /** 取消删除 */
    fun cancelDelete() {
        _uiState.update { it.copy(noteToDelete = null) }
    }

    /** 确认删除：同时清理私有目录中的图片文件 */
    fun confirmDelete() {
        val note = _uiState.value.noteToDelete ?: return
        viewModelScope.launch {
            // 先删除私有图片文件
            ImageStorageManager.deleteImages(note.images)
            // 再删除数据库记录
            repository.deleteNote(note)
            _uiState.update { it.copy(noteToDelete = null, snackbarMessage = "「${note.name}」已删除") }
        }
    }

    fun toggleDownloadStatus(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateDownloadStatus(note.id, !note.isDownloaded)
        }
    }

    /**
     * 从笔记中删除单张图片：
     * 1. 删除私有目录中的图片文件
     * 2. 更新数据库中的 images 列表
     */
    fun removeImageFromNote(note: NoteEntity, imagePath: String) {
        viewModelScope.launch {
            ImageStorageManager.deleteImage(imagePath)
            val updated = note.copy(images = note.images - imagePath)
            repository.updateNote(updated)
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

