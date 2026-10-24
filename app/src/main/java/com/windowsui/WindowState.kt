package com.windowsui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.windowsui.SystemState

/**
 * Window State Manager for ASUI Desktop.
 *
 * Manages:
 * - Opening, closing, focusing, minimizing, maximizing windows
 * - Z-index (stacking order) with full Compose reactivity
 * - Window positions, sizes, and states
 * - Virtual desktop grouping
 * - Window history with undo/restore
 * - Persistence with atomic save operations
 * - Window snapshots (thumbnails)
 * - Transparency, blur, opacity effects
 * - Window animations (fade, scale, slide)
 * - Window grouping and cascading layouts
 * - Keyboard shortcuts (Alt+F4, Ctrl+W, etc.)
 * - Window bookmarks (favorite positions)
 * - Min/max size constraints
 * - Custom title bar colors and icons
 *
 * All windows are stored in a SnapshotStateList for full Compose reactivity.
 * Mutations are performed via copy-and-replace to trigger recomposition.
 */
object WindowState {

    private const val TAG = "WindowState"

    // ==================== VERİ SINIFI (IMMUTABLE, COPY İLE GÜNCELLENİR) ====================

    data class WindowData(
        val id: String,
        val title: String,
        val contentId: String,              // Hangi içerik gösterilecek
        val x: Float = 100f,
        val y: Float = 100f,
        val width: Int = 600,
        val height: Int = 420,
        val zIndex: Int = 0,
        val isMinimized: Boolean = false,
        val isMaximized: Boolean = false,
        val isPinned: Boolean = false,
        val desktopId: String = "",
        val isTransparent: Boolean = false,
        val blurEnabled: Boolean = false,
        val opacity: Float = 1f,            // 0.0 – 1.0
        val animationType: String = "fade", // "fade", "scale", "slide"
        val isHidden: Boolean = false,
        val timestamp: Long = System.currentTimeMillis(),
        val minWidth: Int = 200,
        val minHeight: Int = 150,
        val maxWidth: Int = 1600,
        val maxHeight: Int = 1200,
        val titleBarColor: Int = 0xFFE0E0E0.toInt(),
        val titleBarTextColor: Int = 0xFF000000.toInt(),
        val iconRes: Int? = null,           // Pencere simgesi (R.drawable)
        val groupId: String? = null,        // Grup ID'si (aynı uygulamanın pencereleri)
        val isCascaded: Boolean = false,    // Yığınlanmış mı?
        val bookmarkName: String? = null,   // Yer imi adı (favori konum)
        val soundEnabled: Boolean = true,   // Açma/kapama sesi
        val keyboardShortcuts: Map<String, String> = emptyMap() // Kısayol -> işlem
    )

    // ==================== CANLI PENCERELER ====================
    // SnapshotStateList: Compose tarafından otomatik izlenir
    private val _windows = mutableStateListOf<WindowData>()
    val windows: SnapshotStateList<WindowData> get() = _windows

    // ==================== YAKIN ZAMANDA KAPANAN PENCERELER ====================
    private val _recentlyClosed = mutableStateListOf<WindowData>()
    val recentlyClosed: List<WindowData> get() = _recentlyClosed.toList()

    // ==================== PENCERE GRUPLARI (Sekmeli Görünüm İçin) ====================
    private val _windowGroups = mutableStateListOf<String>()
    val windowGroups: List<String> get() = _windowGroups.toList()

    // ==================== PENCERE YER İMLERİ ====================
    private val _bookmarks = mutableStateListOf<WindowData>()
    val bookmarks: List<WindowData> get() = _bookmarks.toList()

    private var nextZ = 0

    // ==================== KALICI DURUM (SystemState ile) ====================

    fun loadState(context: Context) {
        val prefs = context.getSharedPreferences("ASUI_WindowState", Context.MODE_PRIVATE)
        val windowCount = prefs.getInt("windowCount", 0)
        if (windowCount == 0) {
            _windows.clear()
            return
        }

        val loadedWindows = mutableListOf<WindowData>()
        for (i in 0 until windowCount) {
            val prefix = "window_$i"
            val id = prefs.getString("${prefix}_id", "") ?: continue
            if (id.isEmpty()) continue

            val window = WindowData(
                id = id,
                title = prefs.getString("${prefix}_title", "Pencere") ?: "Pencere",
                contentId = prefs.getString("${prefix}_contentId", "") ?: "",
                x = prefs.getFloat("${prefix}_x", 100f),
                y = prefs.getFloat("${prefix}_y", 100f),
                width = prefs.getInt("${prefix}_width", 600),
                height = prefs.getInt("${prefix}_height", 420),
                zIndex = prefs.getInt("${prefix}_zIndex", 0),
                isMinimized = prefs.getBoolean("${prefix}_isMinimized", false),
                isMaximized = prefs.getBoolean("${prefix}_isMaximized", false),
                isPinned = prefs.getBoolean("${prefix}_isPinned", false),
                desktopId = prefs.getString("${prefix}_desktopId", "") ?: "",
                isTransparent = prefs.getBoolean("${prefix}_isTransparent", false),
                blurEnabled = prefs.getBoolean("${prefix}_blurEnabled", false),
                opacity = prefs.getFloat("${prefix}_opacity", 1f),
                animationType = prefs.getString("${prefix}_animationType", "fade") ?: "fade",
                isHidden = prefs.getBoolean("${prefix}_isHidden", false),
                timestamp = prefs.getLong("${prefix}_timestamp", System.currentTimeMillis()),
                minWidth = prefs.getInt("${prefix}_minWidth", 200),
                minHeight = prefs.getInt("${prefix}_minHeight", 150),
                maxWidth = prefs.getInt("${prefix}_maxWidth", 1600),
                maxHeight = prefs.getInt("${prefix}_maxHeight", 1200),
                titleBarColor = prefs.getInt("${prefix}_titleBarColor", 0xFFE0E0E0.toInt()),
                titleBarTextColor = prefs.getInt("${prefix}_titleBarTextColor", 0xFF000000.toInt()),
                iconRes = prefs.getInt("${prefix}_iconRes", 0).takeIf { it != 0 },
                groupId = prefs.getString("${prefix}_groupId", null),
                isCascaded = prefs.getBoolean("${prefix}_isCascaded", false),
                bookmarkName = prefs.getString("${prefix}_bookmarkName", null),
                soundEnabled = prefs.getBoolean("${prefix}_soundEnabled", true)
            )
            loadedWindows.add(window)
            if (window.zIndex > nextZ) nextZ = window.zIndex + 1
        }

        // Grupları da yükle
        val groupCount = prefs.getInt("groupCount", 0)
        _windowGroups.clear()
        for (i in 0 until groupCount) {
            val gid = prefs.getString("group_$i", "") ?: continue
            if (gid.isNotEmpty()) _windowGroups.add(gid)
        }

        // Yer imlerini yükle
        val bookmarkCount = prefs.getInt("bookmarkCount", 0)
        _bookmarks.clear()
        for (i in 0 until bookmarkCount) {
            val prefix = "bookmark_$i"
            val id = prefs.getString("${prefix}_id", "") ?: continue
            if (id.isEmpty()) continue
            val bm = WindowData(
                id = id,
                title = prefs.getString("${prefix}_title", "Yer İmi") ?: "Yer İmi",
                contentId = prefs.getString("${prefix}_contentId", "") ?: "",
                x = prefs.getFloat("${prefix}_x", 100f),
                y = prefs.getFloat("${prefix}_y", 100f),
                width = prefs.getInt("${prefix}_width", 600),
                height = prefs.getInt("${prefix}_height", 420),
                bookmarkName = prefs.getString("${prefix}_bookmarkName", null)
            )
            _bookmarks.add(bm)
        }

        _windows.clear()
        _windows.addAll(loadedWindows)
        Log.d(TAG, "Loaded ${loadedWindows.size} windows")
    }

    fun saveState(context: Context) {
        val prefs = context.getSharedPreferences("ASUI_WindowState", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.clear()

        // Pencereleri kaydet
        editor.putInt("windowCount", _windows.size)
        _windows.forEachIndexed { i, window ->
            val prefix = "window_$i"
            editor.putString("${prefix}_id", window.id)
            editor.putString("${prefix}_title", window.title)
            editor.putString("${prefix}_contentId", window.contentId)
            editor.putFloat("${prefix}_x", window.x)
            editor.putFloat("${prefix}_y", window.y)
            editor.putInt("${prefix}_width", window.width)
            editor.putInt("${prefix}_height", window.height)
            editor.putInt("${prefix}_zIndex", window.zIndex)
            editor.putBoolean("${prefix}_isMinimized", window.isMinimized)
            editor.putBoolean("${prefix}_isMaximized", window.isMaximized)
            editor.putBoolean("${prefix}_isPinned", window.isPinned)
            editor.putString("${prefix}_desktopId", window.desktopId)
            editor.putBoolean("${prefix}_isTransparent", window.isTransparent)
            editor.putBoolean("${prefix}_blurEnabled", window.blurEnabled)
            editor.putFloat("${prefix}_opacity", window.opacity)
            editor.putString("${prefix}_animationType", window.animationType)
            editor.putBoolean("${prefix}_isHidden", window.isHidden)
            editor.putLong("${prefix}_timestamp", window.timestamp)
            editor.putInt("${prefix}_minWidth", window.minWidth)
            editor.putInt("${prefix}_minHeight", window.minHeight)
            editor.putInt("${prefix}_maxWidth", window.maxWidth)
            editor.putInt("${prefix}_maxHeight", window.maxHeight)
            editor.putInt("${prefix}_titleBarColor", window.titleBarColor)
            editor.putInt("${prefix}_titleBarTextColor", window.titleBarTextColor)
            editor.putInt("${prefix}_iconRes", window.iconRes ?: 0)
            editor.putString("${prefix}_groupId", window.groupId)
            editor.putBoolean("${prefix}_isCascaded", window.isCascaded)
            editor.putString("${prefix}_bookmarkName", window.bookmarkName)
            editor.putBoolean("${prefix}_soundEnabled", window.soundEnabled)
        }

        // Grupları kaydet
        editor.putInt("groupCount", _windowGroups.size)
        _windowGroups.forEachIndexed { i, gid -> editor.putString("group_$i", gid) }

        // Yer imlerini kaydet
        editor.putInt("bookmarkCount", _bookmarks.size)
        _bookmarks.forEachIndexed { i, bm ->
            val prefix = "bookmark_$i"
            editor.putString("${prefix}_id", bm.id)
            editor.putString("${prefix}_title", bm.title)
            editor.putString("${prefix}_contentId", bm.contentId)
            editor.putFloat("${prefix}_x", bm.x)
            editor.putFloat("${prefix}_y", bm.y)
            editor.putInt("${prefix}_width", bm.width)
            editor.putInt("${prefix}_height", bm.height)
            editor.putString("${prefix}_bookmarkName", bm.bookmarkName)
        }

        editor.apply()
        Log.d(TAG, "Saved ${_windows.size} windows")
    }

    // ==================== REAKTİF GÜNCELLEME YARDIMCISI ====================

    /**
     * Bir pencereyi günceller (değişiklikleri Compose'a bildirir).
     * WindowData'yı kopyalayıp değiştirir ve listeye yeniden atar.
     */
    private fun updateWindow(window: WindowData, block: WindowData.() -> WindowData) {
        val index = _windows.indexOf(window)
        if (index != -1) {
            _windows[index] = block(window)
        }
    }

    // ==================== PENCERE YÖNETİMİ ====================

    fun openWindow(
        id: String,
        title: String,
        contentId: String,
        width: Int = 600,
        height: Int = 420,
        desktopId: String? = null,
        isPinned: Boolean = false,
        isTransparent: Boolean = false,
        blurEnabled: Boolean = false,
        opacity: Float = 1f,
        animationType: String = "fade",
        groupId: String? = null,
        titleBarColor: Int = 0xFFE0E0E0.toInt(),
        titleBarTextColor: Int = 0xFF000000.toInt(),
        iconRes: Int? = null,
        soundEnabled: Boolean = true
    ): WindowData {
        // Aynı contentId'den zaten açık bir pencere varsa onu odakla
        val existing = _windows.find { it.contentId == contentId && !it.isHidden }
        if (existing != null) {
            focusWindow(existing)
            return existing
        }

        val actualDesktopId = desktopId ?: SystemState.currentDesktop
        val newWindow = WindowData(
            id = id,
            title = title,
            contentId = contentId,
            width = width,
            height = height,
            zIndex = nextZ++,
            desktopId = actualDesktopId,
            isPinned = isPinned,
            isTransparent = isTransparent,
            blurEnabled = blurEnabled,
            opacity = opacity,
            animationType = animationType,
            timestamp = System.currentTimeMillis(),
            groupId = groupId,
            titleBarColor = titleBarColor,
            titleBarTextColor = titleBarTextColor,
            iconRes = iconRes,
            soundEnabled = soundEnabled
        )
        _windows.add(newWindow)
        focusWindow(newWindow)

        // Grup yoksa oluştur
        if (groupId != null && !_windowGroups.contains(groupId)) {
            _windowGroups.add(groupId)
        }

        return newWindow
    }

    fun closeWindow(window: WindowData, saveToHistory: Boolean = true) {
        if (saveToHistory) {
            _recentlyClosed.removeAll { it.id == window.id }
            _recentlyClosed.add(0, window.copy())
            if (_recentlyClosed.size > 10) {
                _recentlyClosed.removeAt(_recentlyClosed.size - 1)
            }
        }
        _windows.remove(window)
        reorderZIndices()

        // Eğer grup boşaldıysa grubu sil
        if (window.groupId != null && _windows.none { it.groupId == window.groupId }) {
            _windowGroups.remove(window.groupId)
        }
    }

    fun focusWindow(window: WindowData) {
        val maxZ = _windows.maxOfOrNull { it.zIndex } ?: 0
        updateWindow(window) { copy(zIndex = maxZ + 1) }
        nextZ = maxZ + 2
    }

    fun minimizeWindow(window: WindowData) {
        val minZ = _windows.minOfOrNull { it.zIndex } ?: 0
        updateWindow(window) {
            copy(
                isMinimized = true,
                zIndex = if (zIndex > minZ) minZ - 1 else zIndex
            )
        }
    }

    fun restoreWindow(window: WindowData) {
        updateWindow(window) { copy(isMinimized = false) }
    }

    fun maximizeWindow(window: WindowData) {
        updateWindow(window) { copy(isMaximized = !isMaximized) }
    }

    fun pinWindow(window: WindowData) {
        val newPinned = !window.isPinned
        val maxZ = _windows.maxOfOrNull { it.zIndex } ?: 0
        updateWindow(window) {
            copy(
                isPinned = newPinned,
                zIndex = if (newPinned) maxZ + 1 else zIndex
            )
        }
        if (newPinned) nextZ = maxZ + 2
    }

    fun setWindowPosition(window: WindowData, x: Float, y: Float) {
        updateWindow(window) { copy(x = x, y = y) }
    }

    fun setWindowSize(window: WindowData, width: Int, height: Int) {
        val clampedWidth = width.coerceIn(window.minWidth, window.maxWidth)
        val clampedHeight = height.coerceIn(window.minHeight, window.maxHeight)
        updateWindow(window) { copy(width = clampedWidth, height = clampedHeight) }
    }

    fun setWindowTransparency(window: WindowData, enabled: Boolean) {
        updateWindow(window) { copy(isTransparent = enabled) }
    }

    fun setWindowOpacity(window: WindowData, opacity: Float) {
        updateWindow(window) { copy(opacity = opacity.coerceIn(0f, 1f)) }
    }

    fun setWindowBlur(window: WindowData, enabled: Boolean) {
        updateWindow(window) { copy(blurEnabled = enabled) }
    }

    fun setWindowAnimation(window: WindowData, type: String) {
        updateWindow(window) { copy(animationType = type) }
    }

    fun hideWindow(window: WindowData) {
        updateWindow(window) { copy(isHidden = true) }
    }

    fun showWindow(window: WindowData) {
        updateWindow(window) { copy(isHidden = false) }
    }

    fun setWindowTitle(window: WindowData, title: String) {
        updateWindow(window) { copy(title = title) }
    }

    fun setWindowTitleBarColor(window: WindowData, color: Int) {
        updateWindow(window) { copy(titleBarColor = color) }
    }

    fun setWindowGroup(window: WindowData, groupId: String?) {
        updateWindow(window) { copy(groupId = groupId) }
        if (groupId != null && !_windowGroups.contains(groupId)) {
            _windowGroups.add(groupId)
        }
    }

    // ==================== SANAL MASAÜSTÜ DESTEĞİ ====================

    fun getWindowsForDesktop(desktopId: String): List<WindowData> {
        return _windows.filter { it.desktopId == desktopId && !it.isHidden }
    }

    fun moveWindowToDesktop(window: WindowData, desktopId: String) {
        updateWindow(window) { copy(desktopId = desktopId) }
    }

    fun closeAllWindowsOnDesktop(desktopId: String) {
        val toRemove = _windows.filter { it.desktopId == desktopId }
        // Kopya üzerinde döngü (ConcurrentModification önleme)
        toRemove.forEach { closeWindow(it, saveToHistory = false) }
    }

    // ==================== GRUPLAMA / YIĞINLAMA ====================

    fun cascadeWindows(desktopId: String? = null) {
        var offsetX = 30
        var offsetY = 30
        val targetWindows = if (desktopId != null) {
            _windows.filter { it.desktopId == desktopId && !it.isHidden && !it.isMinimized }
        } else {
            _windows.filter { !it.isHidden && !it.isMinimized }
        }

        targetWindows.forEachIndexed { i, window ->
            val newX = offsetX + (i * 30).coerceAtMost(200)
            val newY = offsetY + (i * 30).coerceAtMost(200)
            updateWindow(window) { copy(x = newX.toFloat(), y = newY.toFloat(), isCascaded = true) }
        }
    }

    fun tileWindows(desktopId: String? = null) {
        val targetWindows = if (desktopId != null) {
            _windows.filter { it.desktopId == desktopId && !it.isHidden && !it.isMinimized }
        } else {
            _windows.filter { !it.isHidden && !it.isMinimized }
        }

        if (targetWindows.isEmpty()) return

        val count = targetWindows.size
        val cols = kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt()
        val rows = kotlin.math.ceil(count.toDouble() / cols).toInt()
        val winWidth = (800 / cols).coerceAtLeast(200)
        val winHeight = (600 / rows).coerceAtLeast(150)

        targetWindows.forEachIndexed { i, window ->
            val col = i % cols
            val row = i / cols
            updateWindow(window) {
                copy(
                    x = (col * winWidth + 20).toFloat(),
                    y = (row * winHeight + 20).toFloat(),
                    width = winWidth - 40,
                    height = winHeight - 40,
                    isCascaded = false
                )
            }
        }
    }

    // ==================== YER İMLERİ ====================

    fun addBookmark(window: WindowData, name: String) {
        val bookmark = window.copy(bookmarkName = name)
        _bookmarks.removeAll { it.id == bookmark.id }
        _bookmarks.add(bookmark)
    }

    fun removeBookmark(windowId: String) {
        _bookmarks.removeAll { it.id == windowId }
    }

    fun applyBookmark(bookmark: WindowData): WindowData? {
        val existing = _windows.find { it.id == bookmark.id }
        if (existing != null) {
            updateWindow(existing) {
                copy(
                    x = bookmark.x,
                    y = bookmark.y,
                    width = bookmark.width,
                    height = bookmark.height,
                    bookmarkName = bookmark.bookmarkName
                )
            }
            return existing
        }
        // Yeni pencere aç
        return openWindow(
            id = bookmark.id,
            title = bookmark.title,
            contentId = bookmark.contentId,
            width = bookmark.width,
            height = bookmark.height,
            desktopId = bookmark.desktopId
        )
    }

    // ==================== YAKIN ZAMANDA KAPANANLAR ====================

    fun restoreRecentlyClosed(index: Int = 0): WindowData? {
        if (index < 0 || index >= _recentlyClosed.size) return null
        val data = _recentlyClosed.removeAt(index)
        return openWindow(
            id = data.id,
            title = data.title,
            contentId = data.contentId,
            width = data.width,
            height = data.height,
            desktopId = data.desktopId,
            isPinned = data.isPinned,
            isTransparent = data.isTransparent,
            blurEnabled = data.blurEnabled,
            opacity = data.opacity,
            animationType = data.animationType,
            groupId = data.groupId,
            titleBarColor = data.titleBarColor,
            titleBarTextColor = data.titleBarTextColor,
            iconRes = data.iconRes,
            soundEnabled = data.soundEnabled
        )
    }

    fun clearRecentlyClosed() {
        _recentlyClosed.clear()
    }

    // ==================== Z-INDEX YENİDEN DÜZENLEME ====================

    private fun reorderZIndices() {
        val sorted = _windows.sortedBy { it.zIndex }
        sorted.forEachIndexed { i, window ->
            // Z-index'i güncelle, Compose'a bildir
            val idx = _windows.indexOf(window)
            if (idx != -1) {
                _windows[idx] = window.copy(zIndex = i)
            }
        }
        nextZ = sorted.size
    }

    // ==================== YARDIMCI ====================

    fun isWindowOpen(contentId: String): Boolean {
        return _windows.any { it.contentId == contentId && !it.isHidden }
    }

    fun findWindowById(id: String): WindowData? {
        return _windows.find { it.id == id }
    }

    fun findWindowByContent(contentId: String): WindowData? {
        return _windows.find { it.contentId == contentId && !it.isHidden }
    }

    // ==================== TOPLU İŞLEMLER (Thread Safe) ====================

    fun closeAllWindows() {
        // Kopya üzerinde döngü (ConcurrentModification önleme)
        _windows.toList().forEach { closeWindow(it, saveToHistory = false) }
    }

    fun minimizeAllWindows() {
        _windows.toList().forEach { minimizeWindow(it) }
    }

    fun restoreAllWindows() {
        _windows.toList().forEach { restoreWindow(it) }
    }

    fun closeAllWindowsOnGroup(groupId: String) {
        _windows.filter { it.groupId == groupId }.toList().forEach {
            closeWindow(it, saveToHistory = false)
        }
    }

    // ==================== PENCERE KLAVYE KISAYOLLARI ====================

    fun handleKeyboardShortcut(window: WindowData, shortcut: String): Boolean {
        return when (shortcut) {
            "Alt+F4" -> { closeWindow(window); true }
            "Ctrl+W" -> { closeWindow(window); true }
            "Ctrl+M" -> { minimizeWindow(window); true }
            "Ctrl+Shift+M" -> { restoreWindow(window); true }
            "F11" -> { maximizeWindow(window); true }
            else -> false
        }
    }

    // ==================== PENCERE SNAPSHOT (ÖNİZLEME) ====================

    data class WindowSnapshot(
        val windowId: String,
        val bitmap: Any? = null
    )

    private val snapshots = mutableMapOf<String, WindowSnapshot>()

    fun setSnapshot(windowId: String, snapshot: Any?) {
        snapshots[windowId] = WindowSnapshot(windowId, snapshot)
    }

    fun getSnapshot(windowId: String): WindowSnapshot? {
        return snapshots[windowId]
    }

    fun clearSnapshots() {
        snapshots.clear()
    }

    // ==================== PENCERE OLAY DİNLEYİCİLERİ ====================

    // Örnek olay dinleyici (genişletilebilir)
    interface WindowEventListener {
        fun onWindowOpened(window: WindowData)
        fun onWindowClosed(window: WindowData)
        fun onWindowFocused(window: WindowData)
        fun onWindowMinimized(window: WindowData)
        fun onWindowMaximized(window: WindowData)
        fun onWindowMoved(window: WindowData)
        fun onWindowResized(window: WindowData)
    }

    private val listeners = mutableListOf<WindowEventListener>()

    fun addListener(listener: WindowEventListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: WindowEventListener) {
        listeners.remove(listener)
    }

    private fun notifyWindowOpened(window: WindowData) {
        listeners.forEach { it.onWindowOpened(window) }
    }

    private fun notifyWindowClosed(window: WindowData) {
        listeners.forEach { it.onWindowClosed(window) }
    }

    // ... diğer bildirimler de benzer şekilde eklenebilir
}