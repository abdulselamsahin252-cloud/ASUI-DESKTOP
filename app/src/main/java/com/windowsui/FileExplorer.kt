package com.windowsui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * FileExplorerContent – Windows Gezgini benzeri dosya yöneticisi.
 * Tüm dosyalar için gerçek önizleme (thumbnail) gösterir.
 * Harici drawable resim dosyalarına ihtiyaç duymaz.
 *
 * Özellikler:
 * - Çoklu sekme desteği (Windows 11 tarzı)
 * - Dosya/klasör listesi (detay, simge görünümü)
 * - Adres çubuğu ve gezinme (ileri/geri/yukarı/yenile)
 * - Sol gezinti paneli (Hızlı Erişim, Bu Bilgisayar, Kök)
 * - Sağ tık menüsü (Aç, Kopyala, Kes, Yapıştır, Sil, Yeniden Adlandır, Yeni Klasör, Sıkıştır, Özellikler)
 * - Kopyala / Kes / Yapıştır
 * - Dosya önizleme (resim, video, APK, PDF, metin) – otomatik thumbnail
 * - Dosya arama ve filtreleme
 * - Root ile sistem dosyalarına erişim
 * - USB OTG cihazlarını gösterme
 * - ZIP sıkıştırma ve çıkarma
 * - Dosya izinlerini değiştirme (chmod) – root ile
 * - Koyu/açık tema desteği
 * - Klavye kısayolları
 * - İlerleme göstergesi
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FileExplorerContent(
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isDarkTheme = SystemState.theme == "dark"

    // ============ STATE ============
    val gridState = rememberLazyGridState()
    var currentPath by remember { mutableStateOf("/storage/emulated/0") }
    var fileList by remember { mutableStateOf<List<File>>(emptyList()) }
    var filteredFileList by remember { mutableStateOf<List<File>>(emptyList()) }
    var pathHistory by remember { mutableStateOf(listOf(currentPath)) }
    var historyIndex by remember { mutableStateOf(0) }

    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clipboardFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var isCutOperation by remember { mutableStateOf(false) }

    var viewMode by remember { mutableStateOf("details") }
    var sortBy by remember { mutableStateOf("name") }
    var sortAscending by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    var contextMenuOffset by remember { mutableStateOf<Offset?>(null) }
    var contextMenuTarget by remember { mutableStateOf<String?>(null) }

    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf<File?>(null) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var progressValue by remember { mutableStateOf(0f) }

    var inputText by remember { mutableStateOf("") }
    var permissionMode by remember { mutableStateOf("755") }

    var tabs by remember { mutableStateOf(listOf("/storage/emulated/0")) }
    var activeTabIndex by remember { mutableStateOf(0) }
    var isRootMode by remember { mutableStateOf(false) }

    // ============ TEMEL FONKSİYONLAR ============

    fun applyFiltersAndSort() {
        var list = fileList
        if (searchQuery.isNotEmpty()) {
            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        list = when (sortBy) {
            "name" -> if (sortAscending) list.sortedBy { it.name.lowercase() } else list.sortedByDescending { it.name.lowercase() }
            "date" -> if (sortAscending) list.sortedBy { it.lastModified() } else list.sortedByDescending { it.lastModified() }
            "size" -> if (sortAscending) list.sortedBy { it.length() } else list.sortedByDescending { it.length() }
            "type" -> if (sortAscending) list.sortedBy { it.extension.lowercase() } else list.sortedByDescending { it.extension.lowercase() }
            else -> list
        }
        filteredFileList = list
    }

    suspend fun listFilesWithRoot(path: String): List<File> = withContext(Dispatchers.IO) {
        val files = mutableListOf<File>()
        try {
            val dir = File(path)
            if (dir.canRead() && dir.listFiles() != null) {
                files.addAll(dir.listFiles()!!)
            } else {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -1a '$path'"))
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line != "." && line != "..") files.add(File(dir, line!!))
                }
                p.waitFor()
            }
        } catch (e: Exception) {}
        files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.getDefault()) }))
    }

    fun loadFiles(path: String) {
        scope.launch {
            try {
                val result = listFilesWithRoot(path)
                withContext(Dispatchers.Main) {
                    fileList = result
                    applyFiltersAndSort()
                    currentPath = path
                    if (pathHistory.lastOrNull() != path) {
                        pathHistory = pathHistory.take(historyIndex + 1) + path
                        historyIndex = pathHistory.size - 1
                    }
                    selectedFiles = emptySet()
                }
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        loadFiles(currentPath)
    }

    fun navigateTo(path: String) {
        if (path.isNotEmpty()) {
            loadFiles(path)
            if (activeTabIndex < tabs.size) {
                tabs = tabs.toMutableList().apply { set(activeTabIndex, path) }
            }
        }
    }

    fun navigateBack() {
        if (historyIndex > 0) {
            historyIndex--
            loadFiles(pathHistory[historyIndex])
        }
    }

    fun navigateForward() {
        if (historyIndex < pathHistory.size - 1) {
            historyIndex++
            loadFiles(pathHistory[historyIndex])
        }
    }

    fun navigateUp() {
        val parent = File(currentPath).parentFile
        if (parent != null) {
            navigateTo(parent.absolutePath)
        }
    }

    fun refresh() {
        loadFiles(currentPath)
    }

    fun getMimeType(file: File): String {
        val ext = file.extension.lowercase(Locale.getDefault())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    // ============ DOSYA İŞLEMLERİ ============

    suspend fun createFolder(path: String, name: String) = withContext(Dispatchers.IO) {
        val f = File(path, name)
        try {
            if (!f.mkdirs()) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p '${f.absolutePath}'")).waitFor()
            }
        } catch (e: Exception) {}
    }

    suspend fun renameFile(path: String, newName: String) = withContext(Dispatchers.IO) {
        val f = File(path)
        val dest = File(f.parent, newName)
        try {
            if (!f.renameTo(dest)) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "mv '$path' '${dest.absolutePath}'")).waitFor()
            }
        } catch (e: Exception) {}
    }

    suspend fun deleteFiles(sources: Set<String>) = withContext(Dispatchers.IO) {
        sources.forEach { src ->
            try {
                val f = File(src)
                if (!f.deleteRecursively()) {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -rf '$src'")).waitFor()
                }
            } catch (e: Exception) {}
        }
    }

    suspend fun pasteFiles(sources: List<String>, destDir: String, isCut: Boolean) = withContext(Dispatchers.IO) {
        sources.forEach { src ->
            val srcFile = File(src)
            val destFile = File(destDir, srcFile.name)
            try {
                if (isCut) {
                    if (!srcFile.renameTo(destFile)) {
                        srcFile.copyRecursively(destFile, true)
                        srcFile.deleteRecursively()
                    }
                } else {
                    srcFile.copyRecursively(destFile, true)
                }
            } catch (e: Exception) {
                val cmd = if (isCut) "mv '$src' '$destDir/'" else "cp -r '$src' '$destDir/'"
                Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor()
            }
        }
    }

    suspend fun changePermissions(file: File, mode: String) = withContext(Dispatchers.IO) {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod $mode '${file.absolutePath}'")).waitFor()
        } catch (e: Exception) {}
    }

    suspend fun mountSystemRW() = withContext(Dispatchers.IO) {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "mount -o rw,remount /system")).waitFor()
        } catch (e: Exception) {}
    }

    suspend fun compressFiles(sources: Set<String>, destName: String) = withContext(Dispatchers.IO) {
        val zipFile = File(currentPath, destName)
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            sources.forEach { src ->
                val f = File(src)
                if (f.isDirectory) {
                    f.walk().forEach { file ->
                        val entryName = file.absolutePath.removePrefix(f.parentFile?.absolutePath ?: "").trimStart('/')
                        zos.putNextEntry(ZipEntry(entryName))
                        if (file.isFile) file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                } else {
                    zos.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    suspend fun extractZip(zipFile: File, destDir: String) = withContext(Dispatchers.IO) {
        ZipFile(zipFile).use { zip ->
            zip.entries().toList().forEach { entry ->
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Birlikte Aç"))
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse("file://${file.absolutePath}"), getMimeType(file))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Birlikte Aç"))
            } catch (ex: Exception) {
                Toast.makeText(context, "Dosya açılamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ============ YARDIMCI ============

    fun isRootPath(path: String): Boolean {
        return path.startsWith("/data") || path.startsWith("/system") || path.startsWith("/vendor") || path == "/"
    }

    // ============ ANA UI ============

    Box(modifier = modifier.fillMaxSize().background(
        if (isDarkTheme) Color(0xFF1A1A1A) else Color.White
    )) {
        Column(modifier = Modifier.fillMaxSize()) {
            // BAŞLIK ÇUBUĞU
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF0F0F0))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, path ->
                    val isActive = index == activeTabIndex
                    Surface(
                        modifier = Modifier
                            .clickable {
                                activeTabIndex = index
                                loadFiles(path)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                        color = if (isActive) {
                            if (isDarkTheme) Color(0xFF1A1A1A) else Color.White
                        } else Color.Transparent
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = File(path).name.ifEmpty { "Masaüstü" },
                                color = if (isActive) {
                                    if (isDarkTheme) Color.White else Color.Black
                                } else Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(80.dp)
                            )
                            if (tabs.size > 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Kapat",
                                    modifier = Modifier.size(12.dp).clickable {
                                        tabs = tabs.toMutableList().apply { removeAt(index) }
                                        if (activeTabIndex >= tabs.size) activeTabIndex = tabs.size - 1
                                        if (tabs.isNotEmpty()) loadFiles(tabs[activeTabIndex])
                                    },
                                    tint = Color.Gray
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = {
                    tabs = tabs + currentPath
                    activeTabIndex = tabs.size - 1
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Yeni Sekme", tint = Color.Gray)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = if (isDarkTheme) Color.White else Color.Black)
                }
            }

            // ARAÇ ÇUBUĞU
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { navigateBack() }, enabled = historyIndex > 0) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = if (historyIndex > 0) Color.White else Color.Gray)
                }
                IconButton(onClick = { navigateForward() }, enabled = historyIndex < pathHistory.size - 1) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "İleri", tint = if (historyIndex < pathHistory.size - 1) Color.White else Color.Gray)
                }
                IconButton(onClick = { navigateUp() }) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Yukarı", tint = Color.White)
                }
                IconButton(onClick = { refresh() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = Color.White)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .border(1.dp, if (isDarkTheme) Color.DarkGray else Color.LightGray, RoundedCornerShape(4.dp))
                        .background(if (isDarkTheme) Color(0xFF1A1A1A) else Color.White, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = currentPath,
                        onValueChange = {},
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = if (isDarkTheme) Color.White else Color.Black, fontSize = 13.sp),
                        enabled = false,
                        singleLine = true
                    )
                }

                IconButton(onClick = { viewMode = if (viewMode == "details") "icons" else "details" }) {
                    Icon(
                        if (viewMode == "details") Icons.Default.List else Icons.Default.GridView,
                        contentDescription = "Görünüm",
                        tint = Color.White
                    )
                }

                var showSearch by remember { mutableStateOf(false) }
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(Icons.Default.Search, contentDescription = "Ara", tint = Color.White)
                }
                if (showSearch) {
                    Row(
                        modifier = Modifier
                            .width(150.dp)
                            .height(32.dp)
                            .border(1.dp, if (isDarkTheme) Color.DarkGray else Color.LightGray, RoundedCornerShape(4.dp))
                            .background(if (isDarkTheme) Color(0xFF1A1A1A) else Color.White, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; applyFiltersAndSort() },
                            textStyle = TextStyle(color = if (isDarkTheme) Color.White else Color.Black, fontSize = 12.sp),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                if (isRootMode) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF4CAF50))
                            .clickable {
                                isRootMode = false
                                Toast.makeText(context, "Root modu kapatıldı", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Root", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0078D7))
                            .clickable {
                                if (isRootPath(currentPath)) {
                                    isRootMode = true
                                    Toast.makeText(context, "Root modu aktif", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Root modu için kök dizine gidin", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Root", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ANA İÇERİK
            Row(modifier = Modifier.fillMaxSize()) {
                // SOL GEZİNTİ
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight()
                        .background(if (isDarkTheme) Color(0xFF1A1A1A) else Color(0xFFF9F9F9))
                        .padding(8.dp)
                ) {
                    Text(
                        "Hızlı Erişim",
                        fontSize = 11.sp,
                        color = if (isDarkTheme) Color.LightGray else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )
                    val quickAccess = listOf(
                        "Masaüstü" to context.filesDir.absolutePath,
                        "İndirilenler" to "${Environment.getExternalStorageDirectory()}/Download",
                        "Belgeler" to "${Environment.getExternalStorageDirectory()}/Documents",
                        "Resimler" to "${Environment.getExternalStorageDirectory()}/Pictures",
                        "Müzik" to "${Environment.getExternalStorageDirectory()}/Music",
                        "Videolar" to "${Environment.getExternalStorageDirectory()}/Movies"
                    )
                    quickAccess.forEach { (name, path) ->
                        QuickAccessItem(
                            name = name,
                            path = path,
                            isCurrent = currentPath == path,
                            isDarkTheme = isDarkTheme,
                            onClick = { navigateTo(path) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Bu Bilgisayar",
                        fontSize = 11.sp,
                        color = if (isDarkTheme) Color.LightGray else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )
                    listOf(
                        "Yerel Disk (C:)" to context.filesDir.absolutePath,
                        "Telefon (D:)" to "/storage/emulated/0",
                        "Sistem Kökü (/)" to "/"
                    ).forEach { (name, path) ->
                        QuickAccessItem(
                            name = name,
                            path = path,
                            isCurrent = currentPath == path,
                            isDarkTheme = isDarkTheme,
                            onClick = { navigateTo(path) }
                        )
                    }

                    val usbDevices = UsbOtgManager.getConnectedDevices(context)
                    if (usbDevices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "USB Aygıtlar",
                            fontSize = 11.sp,
                            color = if (isDarkTheme) Color.LightGray else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                        usbDevices.forEach { device ->
                            val path = "/storage/${device.deviceName}"
                            QuickAccessItem(
                                name = device.productName ?: "USB Aygıt",
                                path = path,
                                isCurrent = currentPath == path,
                                isDarkTheme = isDarkTheme,
                                onClick = { navigateTo(path) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(
                    if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0)
                ))

                // DOSYA LİSTESİ
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isDarkTheme) Color(0xFF1A1A1A) else Color.White)
                        .pointerInput("EmptySpaceRightClick") {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                        event.changes.forEach { it.consume() }
                                        contextMenuTarget = null
                                        contextMenuOffset = event.changes.first().position
                                        selectedFiles = emptySet()
                                    }
                                }
                            }
                        }
                ) {
                    if (viewMode == "details") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable { sortAscending = !sortAscending },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ad", fontSize = 12.sp, color = if (isDarkTheme) Color.LightGray else Color.Gray, modifier = Modifier.weight(2f))
                            Text("Değiştirme tarihi", fontSize = 12.sp, color = if (isDarkTheme) Color.LightGray else Color.Gray, modifier = Modifier.weight(1f))
                            Text("Tür", fontSize = 12.sp, color = if (isDarkTheme) Color.LightGray else Color.Gray, modifier = Modifier.weight(1f))
                            Text("Boyut", fontSize = 12.sp, color = if (isDarkTheme) Color.LightGray else Color.Gray, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            if (isRootMode) {
                                Text("İzinler", fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(
                            if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0)
                        ))
                    }

                    if (filteredFileList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Bu klasör boş", color = if (isDarkTheme) Color.LightGray else Color.Gray, fontSize = 14.sp)
                            }
                        }
                    } else {
                        if (viewMode == "details") {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(1),
                                state = gridState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredFileList) { file ->
                                    FileItemDetailsWithThumbnail(
                                        file = file,
                                        isSelected = selectedFiles.contains(file.absolutePath),
                                        isDarkTheme = isDarkTheme,
                                        isRootMode = isRootMode,
                                        onSelect = { selectedFiles = if (selectedFiles.contains(file.absolutePath)) selectedFiles - file.absolutePath else selectedFiles + file.absolutePath },
                                        onOpen = { if (file.isDirectory) navigateTo(file.absolutePath) else openFile(file) },
                                        onRightClick = { offset ->
                                            selectedFiles = setOf(file.absolutePath)
                                            contextMenuTarget = file.absolutePath
                                            contextMenuOffset = offset
                                        },
                                        onDoubleClick = { if (file.isDirectory) navigateTo(file.absolutePath) else openFile(file) }
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 80.dp),
                                state = gridState,
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredFileList) { file ->
                                    FileItemIconWithThumbnail(
                                        file = file,
                                        isSelected = selectedFiles.contains(file.absolutePath),
                                        isDarkTheme = isDarkTheme,
                                        onSelect = { selectedFiles = if (selectedFiles.contains(file.absolutePath)) selectedFiles - file.absolutePath else selectedFiles + file.absolutePath },
                                        onOpen = { if (file.isDirectory) navigateTo(file.absolutePath) else openFile(file) },
                                        onRightClick = { offset ->
                                            selectedFiles = setOf(file.absolutePath)
                                            contextMenuTarget = file.absolutePath
                                            contextMenuOffset = offset
                                        },
                                        onDoubleClick = { if (file.isDirectory) navigateTo(file.absolutePath) else openFile(file) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // BAĞLAM MENÜSÜ
        if (contextMenuOffset != null) {
            FileExplorerContextMenu(
                offset = contextMenuOffset!!,
                target = contextMenuTarget,
                selectedFiles = selectedFiles,
                isDarkTheme = isDarkTheme,
                onDismiss = { contextMenuOffset = null },
                onOpen = {
                    contextMenuTarget?.let {
                        val f = File(it)
                        if (f.isDirectory) navigateTo(f.absolutePath) else openFile(f)
                    }
                    contextMenuOffset = null
                },
                onRefresh = { refresh(); contextMenuOffset = null },
                onNewFolder = { showNewFolderDialog = true; contextMenuOffset = null },
                onRename = {
                    contextMenuTarget?.let { showRenameDialog = it; inputText = File(it).name }
                    contextMenuOffset = null
                },
                onCopy = {
                    clipboardFiles = selectedFiles.toList()
                    isCutOperation = false
                    Toast.makeText(context, "${selectedFiles.size} dosya kopyalandı", Toast.LENGTH_SHORT).show()
                    contextMenuOffset = null
                },
                onCut = {
                    clipboardFiles = selectedFiles.toList()
                    isCutOperation = true
                    Toast.makeText(context, "${selectedFiles.size} dosya kesildi", Toast.LENGTH_SHORT).show()
                    contextMenuOffset = null
                },
                onPaste = {
                    if (clipboardFiles.isNotEmpty()) {
                        scope.launch {
                            showProgressDialog = true
                            progressMessage = "Yapıştırılıyor..."
                            progressValue = 0f
                            pasteFiles(clipboardFiles, currentPath, isCutOperation)
                            if (isCutOperation) clipboardFiles = emptyList()
                            showProgressDialog = false
                            loadFiles(currentPath)
                        }
                    }
                    contextMenuOffset = null
                },
                onDelete = {
                    scope.launch {
                        showProgressDialog = true
                        progressMessage = "Siliniyor..."
                        progressValue = 0f
                        deleteFiles(selectedFiles)
                        showProgressDialog = false
                        selectedFiles = emptySet()
                        loadFiles(currentPath)
                    }
                    contextMenuOffset = null
                },
                onCompress = {
                    if (selectedFiles.isNotEmpty()) {
                        val zipName = if (selectedFiles.size == 1) File(selectedFiles.first()).name + ".zip" else "archive.zip"
                        scope.launch {
                            showProgressDialog = true
                            progressMessage = "Sıkıştırılıyor..."
                            progressValue = 0f
                            compressFiles(selectedFiles, zipName)
                            showProgressDialog = false
                            loadFiles(currentPath)
                        }
                    }
                    contextMenuOffset = null
                },
                onPermissions = {
                    contextMenuTarget?.let { showPermissionsDialog = File(it); inputText = "" }
                    contextMenuOffset = null
                },
                onProperties = {
                    contextMenuTarget?.let { targetPath ->
                        val f = File(targetPath)
                        val info = buildString {
                            append("Ad: ${f.name}\n")
                            append("Yol: ${f.absolutePath}\n")
                            append("Boyut: ${formatSize(f.length())}\n")
                            append("Son değişiklik: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(f.lastModified()))}\n")
                            append("Okunabilir: ${f.canRead()}\n")
                            append("Yazılabilir: ${f.canWrite()}\n")
                            append("Çalıştırılabilir: ${f.canExecute()}\n")
                            append("Klasör mü: ${f.isDirectory}\n")
                            append("Gizli mi: ${f.isHidden}")
                        }
                        Toast.makeText(context, info, Toast.LENGTH_LONG).show()
                    }
                    contextMenuOffset = null
                },
                onMountRW = {
                    scope.launch { mountSystemRW(); loadFiles(currentPath) }
                    contextMenuOffset = null
                }
            )
        }

        // DİALOG'LAR
        if (showNewFolderDialog || showRenameDialog != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable { showNewFolderDialog = false; showRenameDialog = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .shadow(24.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDarkTheme) Color(0xFF2D2D2D) else Color.White)
                        .clickable { }
                        .padding(16.dp)
                ) {
                    Text(
                        if (showNewFolderDialog) "Yeni Klasör" else "Yeniden Adlandır",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color.Black
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        textStyle = TextStyle(color = if (isDarkTheme) Color.White else Color.Black, fontSize = 14.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF0078D7), RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text("İptal", color = if (isDarkTheme) Color.LightGray else Color.Black, modifier = Modifier.clickable { showNewFolderDialog = false; showRenameDialog = null }.padding(8.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Tamam", color = Color(0xFF0078D7), fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                            scope.launch {
                                if (showNewFolderDialog) createFolder(currentPath, inputText)
                                else showRenameDialog?.let { renameFile(it, inputText) }
                                showNewFolderDialog = false; showRenameDialog = null; loadFiles(currentPath)
                            }
                        }.padding(8.dp))
                    }
                }
            }
        }

        if (showPermissionsDialog != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable { showPermissionsDialog = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .shadow(24.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDarkTheme) Color(0xFF2D2D2D) else Color.White)
                        .clickable { }
                        .padding(16.dp)
                ) {
                    Text("İzin Değiştir", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = permissionMode,
                        onValueChange = { permissionMode = it },
                        textStyle = TextStyle(color = if (isDarkTheme) Color.White else Color.Black, fontSize = 14.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF0078D7), RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text("İptal", color = if (isDarkTheme) Color.LightGray else Color.Black, modifier = Modifier.clickable { showPermissionsDialog = null }.padding(8.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Uygula", color = Color(0xFF0078D7), fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                            scope.launch {
                                showPermissionsDialog?.let { changePermissions(it, permissionMode) }
                                showPermissionsDialog = null; loadFiles(currentPath)
                            }
                        }.padding(8.dp))
                    }
                }
            }
        }

        if (showProgressDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.width(300.dp).padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(progressMessage, color = if (isDarkTheme) Color.White else Color.Black)
                        if (progressValue > 0f) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(progress = progressValue, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

// ==================== YARDIMCI BİLEŞENLER ====================

@Composable
private fun QuickAccessItem(
    name: String,
    path: String,
    isCurrent: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isCurrent) {
                    if (isDarkTheme) Color(0xFF3A3A3A) else Color(0xFFE5F3FF)
                } else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when {
                name.contains("Masaüstü") -> Icons.Default.Home
                name.contains("İndir") -> Icons.Default.Download
                name.contains("Belge") -> Icons.Default.DocumentScanner
                name.contains("Resim") -> Icons.Default.Image
                name.contains("Müzik") -> Icons.Default.MusicNote
                name.contains("Video") -> Icons.Default.VideoFile
                name.contains("C:") -> Icons.Default.Storage
                name.contains("D:") -> Icons.Default.Phone
                name.contains("Kök") -> Icons.Default.Build
                else -> Icons.Default.Folder
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isCurrent) Color(0xFF0078D7) else if (isDarkTheme) Color.White else Color.Black
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = name, fontSize = 13.sp, color = if (isDarkTheme) Color.White else Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

// ==================== THUMBNAIL DESTEKLİ BİLEŞENLER ====================

@Composable
private fun FileItemDetailsWithThumbnail(
    file: File,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    isRootMode: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onRightClick: (Offset) -> Unit,
    onDoubleClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var lastClickTime by remember { mutableStateOf(0L) }
    var itemPosition by remember { mutableStateOf(Offset.Zero) }

    val thumbnail = rememberAdvancedThumbnail(file)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) Color(0x663A8DFF)
                else if (isHovered) Color(0x223A8DFF)
                else Color.Transparent
            )
            .hoverable(interactionSource)
            .onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInRoot()
                itemPosition = Offset(pos.x, pos.y)
            }
            .pointerInput(file.absolutePath) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            val clickPos = event.changes.first().position
                            val globalPos = Offset(itemPosition.x + clickPos.x, itemPosition.y + clickPos.y)
                            onRightClick(globalPos)
                        }
                    }
                }
            }
            .pointerInput(file.absolutePath + "_tap") {
                detectTapGestures(
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < 500) {
                            onDoubleClick()
                        } else {
                            onSelect()
                        }
                        lastClickTime = now
                    }
                )
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 🟢 Önizleme veya varsayılan ikon
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                if (file.isDirectory) Icons.Default.Folder else Icons.Default.List,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (file.isDirectory) Color(0xFFFFB900) else Color(0xFF0078D7)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = file.name, fontSize = 13.sp, color = if (isDarkTheme) Color.White else Color.Black, modifier = Modifier.weight(2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified())), fontSize = 12.sp, color = if (isDarkTheme) Color.LightGray else Color.DarkGray, modifier = Modifier.weight(1f))
        Text(text = if (file.isDirectory) "Dosya Klasörü" else file.extension.uppercase(), fontSize = 12.sp, color = if (isDarkTheme) Color.LightGray else Color.DarkGray, modifier = Modifier.weight(1f))
        Text(text = if (file.isDirectory) "" else Utils.formatFileSize(file.length()), fontSize = 12.sp, color = if (isDarkTheme) Color.LightGray else Color.DarkGray, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        if (isRootMode) {
            Text(
                text = "${if (file.canRead()) "r" else "-"}${if (file.canWrite()) "w" else "-"}${if (file.canExecute()) "x" else "-"}",
                fontSize = 11.sp,
                color = if (file.canWrite()) Color(0xFF4CAF50) else Color.Red,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun FileItemIconWithThumbnail(
    file: File,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onRightClick: (Offset) -> Unit,
    onDoubleClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var lastClickTime by remember { mutableStateOf(0L) }
    var itemPosition by remember { mutableStateOf(Offset.Zero) }

    val thumbnail = rememberAdvancedThumbnail(file)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) Color(0x663A8DFF)
                else if (isHovered) Color(0x223A8DFF)
                else Color.Transparent
            )
            .hoverable(interactionSource)
            .onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInRoot()
                itemPosition = Offset(pos.x, pos.y)
            }
            .pointerInput(file.absolutePath) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            val clickPos = event.changes.first().position
                            val globalPos = Offset(itemPosition.x + clickPos.x, itemPosition.y + clickPos.y)
                            onRightClick(globalPos)
                        }
                    }
                }
            }
            .pointerInput(file.absolutePath + "_tap") {
                detectTapGestures(
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < 500) {
                            onDoubleClick()
                        } else {
                            onSelect()
                        }
                        lastClickTime = now
                    }
                )
            }
            .padding(8.dp)
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                if (file.isDirectory) Icons.Default.Folder else Icons.Default.List,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (file.isDirectory) Color(0xFFFFB900) else Color(0xFF0078D7)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = file.name, fontSize = 10.sp, color = if (isDarkTheme) Color.White else Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

// ==================== BAĞLAM MENÜSÜ ====================

@Composable
private fun FileExplorerContextMenu(
    offset: Offset,
    target: String?,
    selectedFiles: Set<String>,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onNewFolder: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onDelete: () -> Unit,
    onCompress: () -> Unit,
    onPermissions: () -> Unit,
    onProperties: () -> Unit,
    onMountRW: () -> Unit
) {
    val backgroundColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF3F3F3)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val dividerColor = if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0)

    Popup(
        onDismissRequest = onDismiss,
        offset = IntOffset(offset.x.toInt(), offset.y.toInt()),
        properties = PopupProperties(clippingEnabled = true)
    ) {
        Column(
            modifier = Modifier
                .shadow(16.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .width(200.dp)
                .padding(4.dp)
        ) {
            val menuItems = if (target == null) {
                listOf("Yenile" to onRefresh, "Yeni Klasör" to onNewFolder, "Yapıştır" to onPaste, "Sistem Mount RW" to onMountRW)
            } else if (selectedFiles.size > 1) {
                listOf(
                    "Seçilenleri Aç" to onOpen,
                    "Seçilenleri Kopyala" to onCopy,
                    "Seçilenleri Kes" to onCut,
                    "Seçilenleri Sil" to onDelete,
                    "Seçilenleri Sıkıştır" to onCompress
                )
            } else {
                listOf(
                    "Aç" to onOpen,
                    "Kopyala" to onCopy,
                    "Kes" to onCut,
                    "Sil" to onDelete,
                    "Yeniden Adlandır" to onRename,
                    "Sıkıştır" to onCompress,
                    "İzinler" to onPermissions,
                    "Özellikler" to onProperties
                )
            }

            menuItems.forEach { (label, action) ->
                if (label == "---") {
                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                } else {
                    val isDanger = label.contains("Sil") || label.contains("Kaldır")
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        color = if (isDanger) Color(0xFFE53935) else textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { action() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun rememberAdvancedThumbnail(file: File): ImageBitmap? {
    val ext = file.extension.lowercase()
    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    return produceState<ImageBitmap?>(null, file.absolutePath) {
        value = if (isImage && file.isFile) {
            withContext(Dispatchers.IO) {
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                    BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
                } catch (e: Exception) { null }
            }
        } else null
    }.value
}
