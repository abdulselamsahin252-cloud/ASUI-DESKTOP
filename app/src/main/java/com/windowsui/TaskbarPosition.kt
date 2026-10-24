package com.windowsui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * TaskbarPosition – Görev Çubuğu Konum Yöneticisi
 */
object TaskbarPosition {

    private const val TAG = "TaskbarPosition"
    private const val PREFS_NAME = "ASUI_TaskbarPosition"

    enum class Position {
        BOTTOM, TOP, LEFT, RIGHT
    }

    enum class Alignment {
        START, CENTER, END
    }

    private val _position = MutableStateFlow(Position.BOTTOM)
    val position: StateFlow<Position> = _position.asStateFlow()

    private val _alignment = MutableStateFlow(Alignment.CENTER)
    val alignment: StateFlow<Alignment> = _alignment.asStateFlow()

    private val _size = MutableStateFlow(48)
    val size: StateFlow<Int> = _size.asStateFlow()

    private val _autoHide = MutableStateFlow(false)
    val autoHide: StateFlow<Boolean> = _autoHide.asStateFlow()

    private val _isVisible = MutableStateFlow(true)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    private var appContext: Context? = null

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        loadState()
        Log.d(TAG, "TaskbarPosition initialized")
    }

    fun setPosition(newPosition: Position) {
        _position.value = newPosition
        saveState()
    }

    fun togglePosition() {
        val positions = Position.values()
        val currentIndex = positions.indexOf(_position.value)
        val nextIndex = (currentIndex + 1) % positions.size
        setPosition(positions[nextIndex])
    }

    fun setAlignment(newAlignment: Alignment) {
        _alignment.value = newAlignment
        saveState()
    }

    fun toggleAlignment() {
        val alignments = Alignment.values()
        val currentIndex = alignments.indexOf(_alignment.value)
        val nextIndex = (currentIndex + 1) % alignments.size
        setAlignment(alignments[nextIndex])
    }

    fun setSize(newSize: Int) {
        val clamped = newSize.coerceIn(24, 80)
        _size.value = clamped
        saveState()
    }

    fun toggleAutoHide() {
        _autoHide.value = !_autoHide.value
        saveState()
    }

    fun setVisible(visible: Boolean) {
        _isVisible.value = visible
        saveState()
    }

    fun toggleVisible() {
        _isVisible.value = !_isVisible.value
        saveState()
    }

    fun getSpiritPosition(context: Context): Position {
        val personality = runCatching { DeviceInfo.analyzeDevicePersonality(context) }.getOrNull() ?: "Bilinmiyor"
        return when {
            personality.contains("süper kahraman") -> Position.TOP
            personality.contains("denge ustası") -> Position.BOTTOM
            personality.contains("minimalist") -> Position.LEFT
            else -> Position.BOTTOM
        }
    }

    fun magicPosition(): Position {
        val positions = Position.values()
        return positions[Random.nextInt(positions.size)]
    }

    fun getRecommendedPosition(isLandscape: Boolean): Position {
        return if (isLandscape) Position.LEFT else Position.BOTTOM
    }

    fun getPositionName(position: Position): String {
        return when (position) {
            Position.BOTTOM -> "⬇️ Alt"
            Position.TOP -> "⬆️ Üst"
            Position.LEFT -> "⬅️ Sol"
            Position.RIGHT -> "➡️ Sağ"
        }
    }

    fun getAlignmentName(alignment: Alignment): String {
        return when (alignment) {
            Alignment.START -> "⬅️ Başlangıç"
            Alignment.CENTER -> "🔄 Orta"
            Alignment.END -> "➡️ Son"
        }
    }

    fun getPositionSummary(): String {
        return buildString {
            appendLine("📌 GÖREV ÇUBUĞU KONUMU")
            appendLine("─────────────────────────")
            appendLine("Konum: ${getPositionName(_position.value)}")
            appendLine("Hizalama: ${getAlignmentName(_alignment.value)}")
            appendLine("Boyut: ${_size.value}px")
            appendLine("Otomatik Gizleme: ${if (_autoHide.value) "✅ Aktif" else "❌ Pasif"}")
            appendLine("Görünür: ${if (_isVisible.value) "✅ Evet" else "❌ Hayır"}")
        }
    }

    private fun saveState() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("position", _position.value.name)
            putString("alignment", _alignment.value.name)
            putInt("size", _size.value)
            putBoolean("autoHide", _autoHide.value)
            putBoolean("isVisible", _isVisible.value)
        }.apply()
    }

    private fun loadState() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val posName = prefs.getString("position", Position.BOTTOM.name) ?: Position.BOTTOM.name
        val alignName = prefs.getString("alignment", Alignment.CENTER.name) ?: Alignment.CENTER.name
        
        _position.value = try { Position.valueOf(posName) } catch (_: Exception) { Position.BOTTOM }
        _alignment.value = try { Alignment.valueOf(alignName) } catch (_: Exception) { Alignment.CENTER }
        _size.value = prefs.getInt("size", 48)
        _autoHide.value = prefs.getBoolean("autoHide", false)
        _isVisible.value = prefs.getBoolean("isVisible", true)
    }

    fun resetToDefaults() {
        _position.value = Position.BOTTOM
        _alignment.value = Alignment.CENTER
        _size.value = 48
        _autoHide.value = false
        _isVisible.value = true
        saveState()
    }
}

@Composable
fun rememberTaskbarPosition(): TaskbarPosition {
    return remember { TaskbarPosition }
}

@Composable
fun rememberTaskbarPositionState(): TaskbarPositionState {
    return remember { TaskbarPositionState() }
}

class TaskbarPositionState {
    var position by mutableStateOf(TaskbarPosition.Position.BOTTOM)
    var alignment by mutableStateOf(TaskbarPosition.Alignment.CENTER)
    var size by mutableStateOf(48)
    var autoHide by mutableStateOf(false)
    var isVisible by mutableStateOf(true)

    fun load() {
        position = TaskbarPosition.position.value
        alignment = TaskbarPosition.alignment.value
        size = TaskbarPosition.size.value
        autoHide = TaskbarPosition.autoHide.value
        isVisible = TaskbarPosition.isVisible.value
    }

    fun save() {
        TaskbarPosition.setPosition(position)
        TaskbarPosition.setAlignment(alignment)
        TaskbarPosition.setSize(size)
        if (autoHide != TaskbarPosition.autoHide.value) {
            TaskbarPosition.toggleAutoHide()
        }
        if (isVisible != TaskbarPosition.isVisible.value) {
            TaskbarPosition.toggleVisible()
        }
    }
}