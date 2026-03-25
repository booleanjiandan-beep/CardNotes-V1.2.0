package com.example.cardnote

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.cardnote.data.NoteEntity
import com.example.cardnote.ui.FilterState
import com.example.cardnote.ui.NoteViewModel
import com.example.cardnote.ui.theme.CardNoteTheme
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CardNoteTheme {
                CardNoteApp()
            }
        }
    }
}

// ─────────────────────────────────────────────
// Root App Composable
// ─────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CardNoteApp(viewModel: NoteViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val notes = uiState.filteredNotes
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearSnackbar()
        }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { notes.size.coerceAtLeast(1) }
    )

    LaunchedEffect(notes.size) {
        if (pagerState.currentPage >= notes.size && notes.isNotEmpty()) {
            pagerState.animateScrollToPage(0)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPagerPageChanged(pagerState.currentPage)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0F0F14),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF2A2A3E),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddSheet() },
                containerColor = Color(0xFF6C63FF),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Default.Add, "新增笔记", modifier = Modifier.size(24.dp))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            FilterHeader(
                filterState = filterState,
                totalCount = notes.size,
                searchQuery = uiState.searchQuery,
                isSearchActive = uiState.isSearchActive,
                onToggleDownloaded = { viewModel.toggleDownloadedFilter() },
                onToggleNotDownloaded = { viewModel.toggleNotDownloadedFilter() },
                onToggleSearch = { viewModel.toggleSearch() },
                onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                onClearSearch = { viewModel.clearSearch() }
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (notes.isEmpty()) {
                    EmptyStateView(filterState, uiState.searchQuery)
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        pageSpacing = 16.dp
                    ) { pageIndex ->
                        val note = notes[pageIndex]
                        val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                        NoteCard(
                            note = note,
                            pageOffset = pageOffset,
                            searchQuery = uiState.searchQuery,
                            onEditRequest = { viewModel.showEditSheet(note) },
                            onToggleDownload = { viewModel.toggleDownloadStatus(note) },
                            onDeleteRequest = { viewModel.requestDelete(note) },
                            onRemoveImage = { path -> viewModel.removeImageFromNote(note, path) }
                        )
                    }
                    PageIndicator(
                        currentPage = pagerState.currentPage,
                        pageCount = notes.size,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                    )
                }
            }
        }
    }

    // ── 新增笔记 BottomSheet ──
    if (uiState.showAddSheet) {
        AddNoteBottomSheet(
            isLoading = uiState.isLoading,
            onDismiss = { viewModel.hideAddSheet() },
            title = "新增笔记",
            confirmText = "保存笔记",
            onConfirm = { name, url, downloaded, remarks, imageUris ->
                viewModel.addNote(name, url, downloaded, remarks, imageUris)
            }
        )
    }

    // ── 编辑笔记 BottomSheet ──
    if (uiState.showEditSheet && uiState.noteToEdit != null) {
        AddNoteBottomSheet(
            isLoading = uiState.isLoading,
            onDismiss = { viewModel.hideEditSheet() },
            initialNote = uiState.noteToEdit,
            title = "编辑笔记",
            confirmText = "保存修改",
            onConfirm = { name, url, downloaded, remarks, imageUris ->
                viewModel.updateNote(
                    origin = uiState.noteToEdit!!,
                    name = name,
                    url = url,
                    isDownloaded = downloaded,
                    remarks = remarks,
                    imageUris = imageUris
                )
            }
        )
    }

    // ── 删除确认弹窗 ──
    uiState.noteToDelete?.let { note ->
        DeleteConfirmDialog(
            noteName = note.name,
            imageCount = note.images.size,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() }
        )
    }
}

// ─────────────────────────────────────────────
// 新增笔记 BottomSheet
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNoteBottomSheet(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    initialNote: NoteEntity? = null,
    title: String = "新增笔记",
    confirmText: String = "保存笔记",
    onConfirm: (name: String, url: String, isDownloaded: Boolean, remarks: String, imageUris: List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(initialNote?.id) { mutableStateOf(initialNote?.name.orEmpty()) }
    var url by remember(initialNote?.id) { mutableStateOf(initialNote?.url.orEmpty()) }
    var isDownloaded by remember(initialNote?.id) { mutableStateOf(initialNote?.isDownloaded ?: false) }
    var remarks by remember(initialNote?.id) { mutableStateOf(initialNote?.remarks.orEmpty()) }
    var selectedUris by remember(initialNote?.id) {
        mutableStateOf(
            initialNote?.images?.map { path -> Uri.fromFile(File(path)) } ?: emptyList()
        )
    }
    var nameError by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> selectedUris = (selectedUris + uris).distinct().take(9) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.let { selectedUris = (selectedUris + it).distinct().take(9) } }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val tmpFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmpFile)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val tmpFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmpFile)
            cameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3A3A55))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "关闭", tint = Color(0xFF8888AA))
                }
            }

            NoteTextField(
                value = name, onValueChange = { name = it; nameError = false },
                label = "笔记名称 *", placeholder = "请输入笔记标题",
                isError = nameError, errorMessage = "名称不能为空",
                leadingIcon = Icons.Default.Title
            )
            NoteTextField(
                value = url, onValueChange = { url = it },
                label = "URL", placeholder = "https://example.com",
                leadingIcon = Icons.Default.Link
            )
            NoteTextField(
                value = remarks, onValueChange = { remarks = it },
                label = "备注", placeholder = "添加备注内容…",
                singleLine = false, minLines = 3, leadingIcon = Icons.Default.Notes
            )

            // 下载状态开关
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF252540)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (isDownloaded) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            null,
                            tint = if (isDownloaded) Color(0xFF4CAF50) else Color(0xFF8888AA),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(if (isDownloaded) "已下载" else "未下载", color = Color.White, fontSize = 14.sp)
                    }
                    Switch(
                        checked = isDownloaded, onCheckedChange = { isDownloaded = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF3A3A55)
                        )
                    )
                }
            }

            // 图片区域
            Text("图片", fontSize = 14.sp, color = Color(0xFF8888AA), fontWeight = FontWeight.Medium)
            LazyImageGrid(
                uris = selectedUris,
                onAddClick = { showImageSourceDialog = true },
                onRemove = { uri -> selectedUris = selectedUris - uri }
            )
            Text(
                "图片将复制到应用私有存储，删除原文件不影响显示（最多9张）",
                fontSize = 11.sp, color = Color(0xFF55556A), lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    if (name.isBlank()) nameError = true
                    else onConfirm(name, url, isDownloaded, remarks, selectedUris)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(confirmText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showImageSourceDialog) {
        ImageSourceDialog(
            onGallery = { showImageSourceDialog = false; galleryLauncher.launch("image/*") },
            onCamera = { showImageSourceDialog = false; launchCamera() },
            onDismiss = { showImageSourceDialog = false }
        )
    }
}

@Composable
fun ImageSourceDialog(onGallery: () -> Unit, onCamera: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF252535),
        title = { Text("添加图片", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = onGallery, shape = RoundedCornerShape(12.dp), color = Color(0xFF1E1E2E)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.PhotoLibrary, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(24.dp))
                        Text("从相册选择", color = Color.White, fontSize = 15.sp)
                    }
                }
                Surface(onClick = onCamera, shape = RoundedCornerShape(12.dp), color = Color(0xFF1E1E2E)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(24.dp))
                        Text("拍照", color = Color.White, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF8888AA)) } }
    )
}

@Composable
fun LazyImageGrid(uris: List<Uri>, onAddClick: () -> Unit, onRemove: (Uri) -> Unit) {
    val canAdd = uris.size < 9
    val totalSlots = uris.size + if (canAdd) 1 else 0
    val columns = 3
    val rows = (totalSlots + columns - 1) / columns

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(columns) { col ->
                    val index = row * columns + col
                    Box(modifier = Modifier.weight(1f)) {
                        when {
                            index < uris.size -> ImagePreviewItem(uri = uris[index], onRemove = { onRemove(uris[index]) })
                            index == uris.size && canAdd -> AddImagePlaceholder(onClick = onAddClick)
                            else -> Spacer(modifier = Modifier.aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImagePreviewItem(uri: Uri, onRemove: () -> Unit) {
    Box(modifier = Modifier.aspectRatio(1f)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(24.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
        ) {
            Icon(Icons.Default.Close, "移除", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun AddImagePlaceholder(onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier.aspectRatio(1f),
        shape = RoundedCornerShape(10.dp), color = Color(0xFF252540),
        border = BorderStroke(1.dp, Color(0xFF3A3A55))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.AddPhotoAlternate, "添加图片", tint = Color(0xFF6C63FF), modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text("添加图片", fontSize = 11.sp, color = Color(0xFF6655CC))
        }
    }
}

@Composable
fun NoteTextField(
    value: String, onValueChange: (String) -> Unit,
    label: String, placeholder: String,
    isError: Boolean = false, errorMessage: String = "",
    singleLine: Boolean = true, minLines: Int = 1,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Column {
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label, fontSize = 13.sp) },
            placeholder = { Text(placeholder, color = Color(0xFF555566), fontSize = 13.sp) },
            singleLine = singleLine, minLines = minLines, isError = isError,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF6C63FF), unfocusedBorderColor = Color(0xFF3A3A55),
                errorBorderColor = Color(0xFFFF5252), focusedTextColor = Color.White,
                unfocusedTextColor = Color.White, cursorColor = Color(0xFF6C63FF),
                focusedContainerColor = Color(0xFF1E1E30), unfocusedContainerColor = Color(0xFF1A1A28),
                focusedLabelColor = Color(0xFF6C63FF), unfocusedLabelColor = Color(0xFF8888AA)
            ),
            leadingIcon = leadingIcon?.let {
                { Icon(it, null, tint = Color(0xFF6655CC), modifier = Modifier.size(18.dp)) }
            }
        )
        if (isError && errorMessage.isNotEmpty()) {
            Text(errorMessage, color = Color(0xFFFF5252), fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
    }
}

// ─────────────────────────────────────────────
// 删除确认弹窗
// ─────────────────────────────────────────────
@Composable
fun DeleteConfirmDialog(noteName: String, imageCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E2E),
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFFF4444).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFFF4444), modifier = Modifier.size(28.dp))
            }
        },
        title = {
            Text("确认删除", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("「$noteName」", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp, textAlign = TextAlign.Center)
                Text("将被永久删除，此操作无法撤销。", color = Color(0xFF8888AA), fontSize = 13.sp, textAlign = TextAlign.Center)
                if (imageCount > 0) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFF4444).copy(alpha = 0.08f)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Image, null, tint = Color(0xFFFF7777), modifier = Modifier.size(14.dp))
                            Text("同时删除 $imageCount 张关联图片", color = Color(0xFFFF7777), fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)), shape = RoundedCornerShape(10.dp)) {
                Text("删除", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8888AA)),
                border = BorderStroke(1.dp, Color(0xFF3A3A55))) {
                Text("取消")
            }
        }
    )
}

// ─────────────────────────────────────────────
// 顶部筛选栏（含搜索）
// ─────────────────────────────────────────────
@Composable
fun FilterHeader(
    filterState: FilterState, totalCount: Int,
    searchQuery: String, isSearchActive: Boolean,
    onToggleDownloaded: () -> Unit, onToggleNotDownloaded: () -> Unit,
    onToggleSearch: () -> Unit, onSearchQueryChange: (String) -> Unit, onClearSearch: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) { delay(100); runCatching { focusRequester.requestFocus() } }
    }

    Surface(color = Color(0xFF1A1A24), shadowElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnimatedVisibility(!isSearchActive, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Column {
                        Text("卡片笔记", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("共 $totalCount 条记录", fontSize = 12.sp, color = Color(0xFF8888AA))
                    }
                }
                AnimatedVisibility(isSearchActive, modifier = Modifier.weight(1f), enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = { Text("搜索名称、URL、备注…", color = Color(0xFF5555AA), fontSize = 14.sp) },
                        singleLine = true, shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6C63FF), unfocusedBorderColor = Color(0xFF3A3A55),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF6C63FF),
                            focusedContainerColor = Color(0xFF252535), unfocusedContainerColor = Color(0xFF252535)
                        ),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, null, tint = Color(0xFF8888AA), modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (isSearchActive || searchQuery.isNotEmpty()) Color(0xFF6C63FF).copy(alpha = 0.2f) else Color(0xFF2A2A38))
                    ) {
                        Icon(
                            if (isSearchActive) Icons.Default.SearchOff else Icons.Default.Search, "搜索",
                            tint = if (isSearchActive || searchQuery.isNotEmpty()) Color(0xFF6C63FF) else Color(0xFF8888AA)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = !menuExpanded },
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (menuExpanded || !filterState.showAll) Color(0xFF6C63FF).copy(alpha = 0.2f) else Color(0xFF2A2A38))
                        ) {
                            Icon(Icons.Default.FilterList, "筛选", tint = if (!filterState.showAll) Color(0xFF6C63FF) else Color(0xFF8888AA))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(Color(0xFF2A2A38)).width(200.dp)) {
                            Text("筛选条件", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                fontSize = 12.sp, color = Color(0xFF8888AA), fontWeight = FontWeight.Medium)
                            FilterCheckboxItem("已下载", filterState.showDownloaded, Color(0xFF4CAF50)) { onToggleDownloaded() }
                            FilterCheckboxItem("未下载", filterState.showNotDownloaded, Color(0xFFFF7043)) { onToggleNotDownloaded() }
                        }
                    }
                }
            }
            val showBanner = searchQuery.isNotEmpty() || !filterState.showAll
            AnimatedVisibility(visible = showBanner) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF6C63FF).copy(alpha = 0.1f)).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (searchQuery.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Search, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(13.dp))
                            Text("\"$searchQuery\"", fontSize = 12.sp, color = Color(0xFF6C63FF), fontWeight = FontWeight.Medium)
                        }
                    }
                    if (!filterState.showAll) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.FilterList, null, tint = Color(0xFF8888CC), modifier = Modifier.size(13.dp))
                            Text(if (filterState.showDownloaded) "已下载" else "未下载", fontSize = 12.sp, color = Color(0xFF8888CC))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("$totalCount 条", fontSize = 11.sp, color = Color(0xFF6655AA))
                }
            }
        }
    }
}

@Composable
fun FilterCheckboxItem(label: String, checked: Boolean, color: Color, onCheckedChange: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange() }.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onCheckedChange() },
            colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = Color(0xFF8888AA)))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
    }
}

// ─────────────────────────────────────────────
// 笔记卡片
// ─────────────────────────────────────────────
@Composable
fun NoteCard(
    note: NoteEntity, pageOffset: Float, searchQuery: String = "",
    onEditRequest: () -> Unit,
    onToggleDownload: () -> Unit,
    onDeleteRequest: () -> Unit,
    onRemoveImage: (String) -> Unit = {}
) {
    val scale by animateFloatAsState(
        targetValue = 1f - (abs(pageOffset) * 0.08f).coerceIn(0f, 0.08f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "cardScale"
    )

    var currentImageIndex by remember(note.id) { mutableIntStateOf(0) }
    var isImageEditMode by remember(note.id) { mutableStateOf(false) }
    var imageToDelete by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(note.images.size) {
        if (note.images.isEmpty()) {
            isImageEditMode = false
        } else if (currentImageIndex >= note.images.size) {
            currentImageIndex = note.images.size - 1
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth().fillMaxHeight(0.82f)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                alpha = 1f - (abs(pageOffset) * 0.3f).coerceIn(0f, 0.3f)
            }
            .shadow(
                24.dp, RoundedCornerShape(24.dp),
                ambientColor = Color(0xFF6C63FF).copy(alpha = 0.3f),
                spotColor = Color(0xFF6C63FF).copy(alpha = 0.4f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── 图片区域 ──
            // 使用两个独立 pointerInput 叠加：
            //   1. detectTapGestures(onLongPress) → 触发编辑模式
            //   2. detectDragGestures            → 拖动切换图片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF252535))
                    // 长按检测（独立 pointerInput key 防止覆盖）
                    .pointerInput(note.id) {
                        detectTapGestures(
                            onLongPress = {
                                if (note.images.isNotEmpty()) {
                                    isImageEditMode = true
                                }
                            }
                        )
                    }
                    // 左右滑动切换图片（不依赖长按进入编辑模式）
                    .pointerInput(note.id, note.images.size) {
                        var dragDistanceX = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragDistanceX += dragAmount
                                if (note.images.size > 1 && abs(dragDistanceX) > 64f) {
                                    currentImageIndex = if (dragDistanceX < 0f)
                                        (currentImageIndex + 1) % note.images.size
                                    else
                                        (currentImageIndex - 1 + note.images.size) % note.images.size
                                    dragDistanceX = 0f
                                }
                            },
                            onDragEnd = { dragDistanceX = 0f },
                            onDragCancel = { dragDistanceX = 0f }
                        )
                    }
            ) {
                if (note.images.isNotEmpty()) {

                    // 当前图片（用 if/else 替代 AnimatedVisibility，避免 BoxScope 冲突）
                    val currentPath = note.images.getOrNull(currentImageIndex) ?: note.images.first()
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(if (currentPath.startsWith("/")) File(currentPath) else Uri.parse(currentPath))
                            .crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // 底部：编辑模式缩略图条 OR 普通页码点
                    // 用 Box 包裹，统一 align，避免 AnimatedVisibility 的 scope 问题
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        // 编辑模式缩略图条
                        if (isImageEditMode) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.75f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    note.images.forEachIndexed { index, path ->
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                    width = if (index == currentImageIndex) 2.dp else 0.dp,
                                                    color = Color(0xFF6C63FF),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { currentImageIndex = index }
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(if (path.startsWith("/")) File(path) else Uri.parse(path))
                                                    .crossfade(false).build(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            // 删除角标
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp)
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFF4444))
                                                    .clickable { imageToDelete = path },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Close, "删除此图片",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    // 退出编辑模式
                                    IconButton(
                                        onClick = { isImageEditMode = false },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.15f))
                                    ) {
                                        Icon(Icons.Default.Check, "完成", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        
                        } else if (note.images.size > 1) {
                            // 普通页码点
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                note.images.forEachIndexed { index, _ ->
                                    Box(
                                        modifier = Modifier
                                            .size(
                                                width = if (index == currentImageIndex) 20.dp else 6.dp,
                                                height = 6.dp
                                            )
                                            .clip(CircleShape)
                                            .background(
                                                if (index == currentImageIndex) Color.White
                                                else Color.White.copy(0.4f)
                                            )
                                            .animateContentSize()
                                    )
                                }
                            }
                        }
                    }

                    // 左上角：编辑模式标签（独立 Box，不与底部 Box 冲突）
                    if (isImageEditMode) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF6C63FF).copy(alpha = 0.85f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(11.dp))
                                Text("图片编辑模式", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }

                } else {
                    // 无图占位
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ImageNotSupported, null, tint = Color(0xFF3A3A55), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("暂无图片", color = Color(0xFF3A3A55), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("长按图片区域可管理图片", color = Color(0xFF2A2A45), fontSize = 11.sp)
                    }
                }

                // 下载状态徽章
                if (!isImageEditMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (note.isDownloaded) Color(0xFF4CAF50).copy(0.9f)
                                else Color(0xFFFF7043).copy(0.9f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (note.isDownloaded) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                null, tint = Color.White, modifier = Modifier.size(12.dp)
                            )
                            Text(
                                if (note.isDownloaded) "已下载" else "未下载",
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } // end 图片 Box

            // ── 内容区域 ──
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                HighlightText(note.name, searchQuery, Color.White, 20.sp, 2, FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Link, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(14.dp))
                    HighlightText(
                        note.url.ifBlank { "未填写 URL" },
                        searchQuery,
                        Color(0xFF6C63FF),
                        12.sp,
                        1,
                        modifier = Modifier.weight(1f)
                    )
                    if (note.url.isNotBlank()) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(note.url))
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "复制URL", tint = Color(0xFF8B83FF), modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (note.remarks.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF252535)) {
                        HighlightText(
                            note.remarks, searchQuery, Color(0xFFCCCCDD), 13.sp, 3,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEditRequest,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6C63FF)),
                        border = BorderStroke(1.dp, Color(0xFF6C63FF).copy(0.5f))
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("编辑", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onToggleDownload,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (note.isDownloaded) Color(0xFFFF7043) else Color(0xFF4CAF50)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (note.isDownloaded) Color(0xFFFF7043).copy(0.5f) else Color(0xFF4CAF50).copy(0.5f)
                        )
                    ) {
                        Icon(
                            if (note.isDownloaded) Icons.Default.CloudOff else Icons.Default.CloudDownload,
                            null, modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (note.isDownloaded) "标为未下载" else "标为已下载", fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = onDeleteRequest,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFF4444).copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.Delete, "删除笔记", tint = Color(0xFFFF4444))
                    }
                }
            }
        }
    }

    // 单张图片删除确认弹窗
    imageToDelete?.let { path ->
        AlertDialog(
            onDismissRequest = { imageToDelete = null },
            containerColor = Color(0xFF1E1E2E),
            shape = RoundedCornerShape(16.dp),
            icon = {
                Icon(Icons.Default.HideImage, null, tint = Color(0xFFFF7043), modifier = Modifier.size(32.dp))
            },
            title = {
                Text(
                    "删除这张图片？", color = Color.White, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "图片文件将从应用存储中永久删除。", color = Color(0xFF8888AA),
                    fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { onRemoveImage(path); imageToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7043)),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("删除", color = Color.White, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { imageToDelete = null },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF3A3A55)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8888AA))
                ) { Text("取消") }
            }
        )
    }
}

// ─────────────────────────────────────────────
// 高亮 Text
// ─────────────────────────────────────────────
@Composable
fun HighlightText(
    text: String, query: String, baseColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    maxLines: Int = Int.MAX_VALUE,
    fontWeight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier
) {
    if (query.isBlank()) {
        Text(text, color = baseColor, fontSize = fontSize, fontWeight = fontWeight, maxLines = maxLines,
            overflow = TextOverflow.Ellipsis, modifier = modifier, lineHeight = (fontSize.value * 1.5f).sp)
        return
    }
    val annotated = buildAnnotatedString {
        val lower = text.lowercase(); val lowerQ = query.lowercase(); var cursor = 0
        while (cursor < text.length) {
            val idx = lower.indexOf(lowerQ, cursor)
            if (idx == -1) { withStyle(androidx.compose.ui.text.SpanStyle(color = baseColor, fontWeight = fontWeight)) { append(text.substring(cursor)) }; break }
            if (idx > cursor) withStyle(androidx.compose.ui.text.SpanStyle(color = baseColor, fontWeight = fontWeight)) { append(text.substring(cursor, idx)) }
            withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFFFD54F), background = Color(0xFFFFD54F).copy(alpha = 0.2f), fontWeight = FontWeight.SemiBold)) { append(text.substring(idx, idx + lowerQ.length)) }
            cursor = idx + lowerQ.length
        }
    }
    Text(annotated, fontSize = fontSize, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier, lineHeight = (fontSize.value * 1.5f).sp)
}

// ─────────────────────────────────────────────
// 页码指示器
// ─────────────────────────────────────────────
@Composable
fun PageIndicator(currentPage: Int, pageCount: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount.coerceAtMost(8)) { index ->
            val isSelected = index == currentPage
            Box(
                modifier = Modifier.animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    .size(width = if (isSelected) 24.dp else 8.dp, height = 8.dp).clip(CircleShape)
                    .background(if (isSelected) Color(0xFF6C63FF) else Color.White.copy(alpha = 0.3f))
            )
        }
        if (pageCount > 8) Text("${currentPage + 1}/$pageCount", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

// ─────────────────────────────────────────────
// 空状态视图
// ─────────────────────────────────────────────
@Composable
fun EmptyStateView(filterState: FilterState, searchQuery: String = "") {
    val isSearchEmpty = searchQuery.isNotBlank()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier.size(120.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF6C63FF).copy(alpha = 0.2f), Color.Transparent), radius = 200f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (isSearchEmpty) Icons.Default.ManageSearch else Icons.Default.SearchOff, null,
                tint = Color(0xFF6C63FF).copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(if (isSearchEmpty) "未找到匹配结果" else "没有符合条件的笔记",
            fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color(0xFF8888AA))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when {
                isSearchEmpty -> "「$searchQuery」没有匹配的笔记，换个关键词试试"
                !filterState.showAll -> "尝试调整顶部的筛选条件"
                else -> "点击右下角 + 按钮添加新笔记"
            },
            fontSize = 14.sp, color = Color(0xFF555566), textAlign = TextAlign.Center
        )
    }
}
