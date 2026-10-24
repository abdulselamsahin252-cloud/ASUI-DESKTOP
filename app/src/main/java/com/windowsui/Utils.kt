package com.windowsui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

object Utils {

    private const val TAG = "Utils"
    
    private val bitmapCache = ConcurrentHashMap<String, ImageBitmap>()
    private val random = kotlin.random.Random
    
    suspend fun loadBitmapFromUri(context: Context, uri: Uri?): ImageBitmap? {
        if (uri == null) return null
        val key = uri.toString()
        bitmapCache[key]?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = if (uri.scheme == "file") {
                    val file = File(uri.path ?: "")
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                } else {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                }
                val result = bitmap?.asImageBitmap()
                if (result != null) {
                    bitmapCache[key] = result
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "Bitmap yüklenemedi: ${e.message}")
                null
            }
        }
    }
    
    fun clearBitmapCache() {
        bitmapCache.clear()
    }
    
    fun evictBitmapCache(key: String) {
        bitmapCache.remove(key)
    }
    
    @Composable
    fun rememberCurrentTime(updateInterval: Long = 1000): String {
        var time by remember { mutableStateOf(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(updateInterval)
                time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            }
        }
        return time
    }
    
    @Composable
    fun rememberCurrentDate(): String {
        return remember { 
            SimpleDateFormat("dd MMMM yyyy", Locale("tr", "TR")).format(Date())
        }
    }
    
    @Composable
    fun rememberCurrentDateShort(): String {
        return remember { 
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        }
    }
    
    @Composable
    fun rememberCurrentTimeShort(): String {
        var time by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(10000)
                time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            }
        }
        return time
    }
    
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(
            Locale.US, 
            "%.1f %s", 
            size / Math.pow(1024.0, digitGroups.toDouble()), 
            units[digitGroups]
        )
    }
    
    fun randomColor(seed: String = ""): androidx.compose.ui.graphics.Color {
        val hash = if (seed.isNotEmpty()) seed.hashCode() else random.nextInt()
        val hue = (hash.absoluteValue % 360) / 360f
        return androidx.compose.ui.graphics.Color.hsv(hue, 0.7f, 0.9f)
    }
    
    fun randomEmoji(): String {
        val emojis = listOf("✨", "🌟", "🎉", "🧙", "🪄", "🌈", "🔥", "💫", "⭐", "🚀", "🎯", "💎", "🦄")
        return emojis.random()
    }
    
    fun randomColorFromText(text: String): androidx.compose.ui.graphics.Color {
        val colors = listOf(
            androidx.compose.ui.graphics.Color(0xFFE53935),
            androidx.compose.ui.graphics.Color(0xFF4CAF50),
            androidx.compose.ui.graphics.Color(0xFF2196F3),
            androidx.compose.ui.graphics.Color(0xFFFF9800),
            androidx.compose.ui.graphics.Color(0xFF9C27B0),
            androidx.compose.ui.graphics.Color(0xFF00BCD4),
            androidx.compose.ui.graphics.Color(0xFFFF6B6B),
            androidx.compose.ui.graphics.Color(0xFF4ECDC4),
            androidx.compose.ui.graphics.Color(0xFF45B7D1),
            androidx.compose.ui.graphics.Color(0xFFFFEAA7),
            androidx.compose.ui.graphics.Color(0xFFA8E6CF),
            androidx.compose.ui.graphics.Color(0xFFDDA0DD)
        )
        val index = text.hashCode().absoluteValue % colors.size
        return colors[index]
    }
    
    fun getSpiritEmoji(personality: String): String {
        return when {
            personality.contains("süper kahraman") -> "🦸"
            personality.contains("denge ustası") -> "🧘"
            personality.contains("minimalist") -> "📿"
            else -> "💎"
        }
    }
    
    fun getSpiritColor(personality: String): androidx.compose.ui.graphics.Color {
        return when {
            personality.contains("süper kahraman") -> androidx.compose.ui.graphics.Color(0xFFFF6B6B)
            personality.contains("denge ustası") -> androidx.compose.ui.graphics.Color(0xFF4ECDC4)
            personality.contains("minimalist") -> androidx.compose.ui.graphics.Color(0xFFA8E6CF)
            else -> androidx.compose.ui.graphics.Color(0xFF0078D7)
        }
    }
    
    fun getSpiritEmojiFromContext(context: Context): String {
        val personality = runCatching { DeviceInfo.analyzeDevicePersonality(context) }.getOrNull() ?: "Bilinmiyor"
        return getSpiritEmoji(personality)
    }
    
    fun getSpiritColorFromContext(context: Context): androidx.compose.ui.graphics.Color {
        val personality = runCatching { DeviceInfo.analyzeDevicePersonality(context) }.getOrNull() ?: "Bilinmiyor"
        return getSpiritColor(personality)
    }
    
    fun isKeyboardShortcut(event: android.view.KeyEvent): String? {
        val isCtrl = event.isCtrlPressed
        val isAlt = event.isAltPressed
        val isMeta = event.isMetaPressed
        val keyCode = event.keyCode
        
        return when {
            isAlt && keyCode == android.view.KeyEvent.KEYCODE_F4 -> "Alt+F4"
            isMeta && keyCode == android.view.KeyEvent.KEYCODE_E -> "Win+E"
            isMeta && keyCode == android.view.KeyEvent.KEYCODE_R -> "Win+R"
            isMeta && keyCode == android.view.KeyEvent.KEYCODE_D -> "Win+D"
            isMeta && keyCode == android.view.KeyEvent.KEYCODE_TAB -> "Win+Tab"
            isAlt && keyCode == android.view.KeyEvent.KEYCODE_TAB -> "Alt+Tab"
            isCtrl && keyCode == android.view.KeyEvent.KEYCODE_C -> "Ctrl+C"
            isCtrl && keyCode == android.view.KeyEvent.KEYCODE_V -> "Ctrl+V"
            isCtrl && keyCode == android.view.KeyEvent.KEYCODE_X -> "Ctrl+X"
            isCtrl && keyCode == android.view.KeyEvent.KEYCODE_Z -> "Ctrl+Z"
            isCtrl && keyCode == android.view.KeyEvent.KEYCODE_Y -> "Ctrl+Y"
            keyCode == android.view.KeyEvent.KEYCODE_F11 -> "F11"
            keyCode == android.view.KeyEvent.KEYCODE_ESCAPE -> "ESC"
            else -> null
        }
    }
}