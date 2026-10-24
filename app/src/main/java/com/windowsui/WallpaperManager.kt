package com.windowsui

import android.Manifest
import android.app.WallpaperManager as AndroidWallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import androidx.compose.ui.graphics.toArgb

/**
 * WallpaperManager – Gelişmiş Duvar Kağıdı Yöneticisi
 */
object WallpaperManager {

    private const val TAG = "WallpaperManager"
    private const val PREFS_NAME = "ASUI_WallpaperManager"
    private const val CACHE_SIZE = 10

    private val _currentUri = MutableStateFlow<Uri?>(null)
    val currentUri: StateFlow<Uri?> = _currentUri.asStateFlow()

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _slideshowEnabled = MutableStateFlow(false)
    val slideshowEnabled: StateFlow<Boolean> = _slideshowEnabled.asStateFlow()

    private val _slideshowInterval = MutableStateFlow(30)
    val slideshowInterval: StateFlow<Int> = _slideshowInterval.asStateFlow()

    private val _blurEnabled = MutableStateFlow(false)
    val blurEnabled: StateFlow<Boolean> = _blurEnabled.asStateFlow()

    private val _blurRadius = MutableStateFlow(12)
    val blurRadius: StateFlow<Int> = _blurRadius.asStateFlow()

    private var appContext: Context? = null
    private var wallpaperHistory = mutableListOf<Uri>()
    private var slideshowJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        loadState()
        Log.d(TAG, "WallpaperManager initialized")
    }

    private fun hasStoragePermission(): Boolean {
        val ctx = appContext ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun setWallpaper(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext false
        try {
            val bitmap = loadBitmapFromUri(uri) ?: return@withContext false
            
            val wallpaperManager = AndroidWallpaperManager.getInstance(ctx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(bitmap, null, false, AndroidWallpaperManager.FLAG_SYSTEM)
            } else {
                @Suppress("DEPRECATION")
                wallpaperManager.setBitmap(bitmap)
            }
            
            _currentUri.value = uri
            _currentBitmap.value = bitmap
            addToHistory(uri)
            saveState()
            
            Log.d(TAG, "Wallpaper set: $uri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Wallpaper set error: ${e.message}")
            false
        }
    }

    suspend fun setWallpaperBitmap(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext false
        try {
            val wallpaperManager = AndroidWallpaperManager.getInstance(ctx)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(bitmap, null, false, AndroidWallpaperManager.FLAG_SYSTEM)
            } else {
                @Suppress("DEPRECATION")
                wallpaperManager.setBitmap(bitmap)
            }
            
            _currentBitmap.value = bitmap
            saveState()
            
            Log.d(TAG, "Wallpaper set from bitmap")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Wallpaper set error: ${e.message}")
            false
        }
    }

    suspend fun resetWallpaper(): Boolean = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext false
        try {
            val wallpaperManager = AndroidWallpaperManager.getInstance(ctx)
            wallpaperManager.clear(AndroidWallpaperManager.FLAG_SYSTEM)
            
            _currentUri.value = null
            _currentBitmap.value = null
            saveState()
            
            Log.d(TAG, "Wallpaper reset to default")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Wallpaper reset error: ${e.message}")
            false
        }
    }

    fun getSpiritColor(context: Context): androidx.compose.ui.graphics.Color {
        val personality = runCatching { DeviceInfo.analyzeDevicePersonality(context) }.getOrNull() ?: "Bilinmiyor"
        return Utils.getSpiritColor(personality)
    }

    suspend fun createSpiritWallpaper(context: Context): Boolean = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext false
        try {
            val color = getSpiritColor(context)
            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(color.toArgb())
            
            val gradient = android.graphics.LinearGradient(
                0f, 0f, 1080f, 1920f,
                intArrayOf(color.toArgb(), androidx.compose.ui.graphics.Color.White.toArgb()),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            val paint = android.graphics.Paint().apply { shader = gradient }
            canvas.drawRect(0f, 0f, 1080f, 1920f, paint)
            
            setWallpaperBitmap(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Spirit wallpaper error: ${e.message}")
            false
        }
    }

    suspend fun magicWallpaper(context: Context): Boolean = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext false
        try {
            if (!hasStoragePermission()) {
                Log.w(TAG, "Storage permission required for magic wallpaper")
                return@withContext createSpiritWallpaper(context)
            }
            val uris = getAllWallpaperUris()
            if (uris.isNotEmpty()) {
                val randomUri = uris.random()
                setWallpaper(randomUri)
            } else {
                createSpiritWallpaper(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Magic wallpaper error: ${e.message}")
            false
        }
    }

    suspend fun applyBlurEffect(radius: Int = 12): Boolean = withContext(Dispatchers.IO) {
        try {
            _blurRadius.value = radius
            _blurEnabled.value = true
            saveState()
            Log.d(TAG, "Blur effect enabled: radius=$radius")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Blur effect error: ${e.message}")
            false
        }
    }

    suspend fun extractColorPalette(): List<Int> = withContext(Dispatchers.IO) {
        val bitmap = _currentBitmap.value ?: return@withContext emptyList()
        try {
            val colors = mutableListOf<Int>()
            val width = bitmap.width
            val height = bitmap.height
            val step = 50
            
            for (x in 0 until width step step) {
                for (y in 0 until height step step) {
                    val pixel = bitmap.getPixel(x, y)
                    colors.add(pixel)
                    if (colors.size >= 8) break
                }
                if (colors.size >= 8) break
            }
            colors
        } catch (e: Exception) {
            Log.e(TAG, "Palette extraction error: ${e.message}")
            emptyList()
        }
    }

    suspend fun startSlideshow(intervalSeconds: Int = 30) {
        val ctx = appContext ?: return
        stopSlideshow()
        _slideshowEnabled.value = true
        _slideshowInterval.value = intervalSeconds
        saveState()
        
        slideshowJob = scope.launch {
            if (!hasStoragePermission()) {
                _slideshowEnabled.value = false
                Log.w(TAG, "Storage permission required for slideshow")
                return@launch
            }
            
            val uris = getAllWallpaperUris()
            if (uris.isEmpty()) {
                _slideshowEnabled.value = false
                return@launch
            }
            
            var index = 0
            while (_slideshowEnabled.value) {
                val uri = uris[index % uris.size]
                setWallpaper(uri)
                index++
                delay(intervalSeconds * 1000L)
            }
        }
    }

    fun stopSlideshow() {
        _slideshowEnabled.value = false
        slideshowJob?.cancel()
        slideshowJob = null
        saveState()
        Log.d(TAG, "Slideshow stopped")
    }

    private suspend fun getAllWallpaperUris(): List<Uri> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext emptyList()
        val uris = mutableListOf<Uri>()
        try {
            if (!hasStoragePermission()) {
                Log.w(TAG, "Storage permission required for gallery scan")
                return@withContext emptyList()
            }
            
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA)
            val cursor = ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    uris.add(uri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gallery scan error: ${e.message}")
        }
        uris
    }

    private suspend fun loadBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext null
        try {
            ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap load error: ${e.message}")
            null
        }
    }

    private fun addToHistory(uri: Uri) {
        wallpaperHistory.add(0, uri)
        if (wallpaperHistory.size > CACHE_SIZE) {
            wallpaperHistory.removeAt(wallpaperHistory.size - 1)
        }
    }

    fun getHistory(): List<Uri> = wallpaperHistory.toList()

    fun clearHistory() {
        wallpaperHistory.clear()
    }

    private fun saveState() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("currentUri", _currentUri.value?.toString())
            putBoolean("slideshowEnabled", _slideshowEnabled.value)
            putInt("slideshowInterval", _slideshowInterval.value)
            putBoolean("blurEnabled", _blurEnabled.value)
            putInt("blurRadius", _blurRadius.value)
            putString("history", wallpaperHistory.joinToString("|") { it.toString() })
        }.apply()
    }

    private fun loadState() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val uriString = prefs.getString("currentUri", null)
        _currentUri.value = uriString?.let { Uri.parse(it) }
        
        _slideshowEnabled.value = prefs.getBoolean("slideshowEnabled", false)
        _slideshowInterval.value = prefs.getInt("slideshowInterval", 30)
        _blurEnabled.value = prefs.getBoolean("blurEnabled", false)
        _blurRadius.value = prefs.getInt("blurRadius", 12)
        
        val historyStr = prefs.getString("history", "") ?: ""
        wallpaperHistory.clear()
        if (historyStr.isNotEmpty()) {
            historyStr.split("|").forEach { str ->
                try {
                    wallpaperHistory.add(Uri.parse(str))
                } catch (_: Exception) {}
            }
        }
    }

    fun resetToDefaults() {
        stopSlideshow()
        _currentUri.value = null
        _currentBitmap.value = null
        _blurEnabled.value = false
        _blurRadius.value = 12
        wallpaperHistory.clear()
        saveState()
    }
}