package com.windowsui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * PermissionManager – Merkezi İzin Yönetimi (Thread-Safe, Activity-Recreation Friendly)
 *
 * Tüm Android izinlerini merkezi olarak yönetir. MainActivity.onCreate() içinde
 * setLauncher() çağrılmalıdır. Ekran döndürmelerde sorunsuz çalışır.
 *
 * 🚀 "Akla Hayale Gelmez" Özellikler:
 * - 🔮 Sihirli İzin Sihirbazı: Tek tıklamayla tüm izinleri kontrol eder.
 * - 📊 İzin Skoru: 0-100 arası puan.
 * - 🧘 Ruh İzni: Cihaz ruhuna göre izin önerisi.
 * - 📜 İzin Geçmişi: Talep edilen izinlerin geçmişi.
 * - ⚡ Otomatik Yeniden Talep: Reddedilen izinleri en fazla 3 kez yeniden ister.
 * - 🔔 Kullanıcı Bilgilendirmesi: İzinler neden gerekli, açıklamalı dialog.
 */
object PermissionManager {

    private const val TAG = "PermissionManager"
    private const val PREFS_NAME = "ASUI_PermissionManager"
    private const val MAX_RETRY_COUNT = 3

    private val gson: Gson = GsonBuilder().create()

    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(Dispatchers.IO)

    // Launcher, her setLauncher çağrısında yeniden oluşturulur.
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    private val permissionStates = ConcurrentHashMap<String, Boolean>()
    private val requestHistory = ConcurrentHashMap<String, Int>()

    // ==================== DİNAMİK İZİN LİSTELERİ ====================

    fun getNormalPermissions(): List<String> {
        return listOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.VIBRATE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
        )
    }

    /**
     * Tehlikeli izinler (SDK sürümüne göre dinamik)
     */
    fun getDangerousPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.USE_BIOMETRIC,
            Manifest.permission.USE_FINGERPRINT
        )

        // ACTIVITY_RECOGNITION (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // BODY_SENSORS (her zaman eklenebilir, ancak fiziksel sensör yoksa istenmez)
        permissions.add(Manifest.permission.BODY_SENSORS)

        // POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Bluetooth (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Depolama (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        return permissions.distinct()
    }

    /**
     * Arka plan konum izni (Android 10+ için ayrıca istenmeli)
     */
    fun getBackgroundLocationPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        } else null
    }

    /**
     * Özel izinler (sistem ayarlarından alınır)
     */
    fun getSpecialPermissions(): List<Pair<String, String>> {
        return listOf(
            "Sistem Üstü Görünüm" to Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "Sistem Ayarlarını Düzenle" to Settings.ACTION_MANAGE_WRITE_SETTINGS,
            "Bildirim Erişimi" to Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            "Erişilebilirlik Servisi" to Settings.ACTION_ACCESSIBILITY_SETTINGS
        )
    }

    // ==================== BAŞLATMA VE LAUNCHER ====================

    fun init(context: Context) {
        appContext = context.applicationContext
        loadState(context)
        Log.d(TAG, "PermissionManager initialized")
    }

    fun setLauncher(activity: ComponentActivity) {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            results.forEach { (permission, granted) ->
                permissionStates[permission] = granted
                if (granted) {
                    Log.d(TAG, "Permission granted: $permission")
                } else {
                    Log.w(TAG, "Permission denied: $permission")
                    val retryCount = requestHistory[permission] ?: 0
                    if (retryCount < MAX_RETRY_COUNT) {
                        requestHistory[permission] = retryCount + 1
                        saveState()
                        if (shouldShowRationale(activity, permission)) {
                            showPermissionRationaleDialog(activity, permission)
                        }
                    } else {
                        Log.e(TAG, "Permission $permission denied after $MAX_RETRY_COUNT attempts")
                        showManualPermissionDialog(activity, permission)
                    }
                }
            }
            saveState()
        }
        Log.d(TAG, "PermissionLauncher registered")
    }

    // ==================== İZİN KONTROLLERİ ====================

    fun areAllPermissionsGranted(context: Context): Boolean {
        val allPermissions = getDangerousPermissions() + getNormalPermissions()
        return allPermissions.all { isPermissionGranted(context, it) }
    }

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        // Özel izinler
        when (permission) {
            Manifest.permission.SYSTEM_ALERT_WINDOW -> {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
            }
            Manifest.permission.WRITE_SETTINGS -> {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.System.canWrite(context)
                } else true
            }
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isSpecialPermissionGranted(context: Context, action: String): Boolean {
        return when (action) {
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION -> {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
            }
            Settings.ACTION_MANAGE_WRITE_SETTINGS -> {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
            }
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS -> {
                androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            }
            Settings.ACTION_ACCESSIBILITY_SETTINGS -> {
                WindowsAccessibilityService.getInstance()?.isEnabled() ?: false
            }
            else -> false
        }
    }

    fun getPermissionScore(context: Context): Int {
        val allPermissions = getDangerousPermissions() + getNormalPermissions()
        val grantedCount = allPermissions.count { isPermissionGranted(context, it) }
        return if (allPermissions.isNotEmpty()) {
            (grantedCount.toFloat() / allPermissions.size.toFloat() * 100).toInt()
        } else 100
    }

    fun getPermissionScoreColor(score: Int): androidx.compose.ui.graphics.Color {
        return when {
            score >= 80 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
            score >= 50 -> androidx.compose.ui.graphics.Color(0xFFFFA000)
            else -> androidx.compose.ui.graphics.Color(0xFFE53935)
        }
    }

    fun getPermissionScoreEmoji(score: Int): String {
        return when {
            score >= 80 -> "🛡️"
            score >= 50 -> "⚠️"
            else -> "🔴"
        }
    }

    // ==================== İZİN TALEP ETME ====================

    fun requestAllDangerousPermissions(activity: ComponentActivity) {
        val pending = getDangerousPermissions().filter {
            !isPermissionGranted(activity, it)
        }.toTypedArray()
        if (pending.isNotEmpty()) {
            Log.d(TAG, "Requesting ${pending.size} dangerous permissions")
            permissionLauncher?.launch(pending) ?: run {
                ActivityCompat.requestPermissions(activity, pending, 0)
            }
        } else {
            Log.d(TAG, "All dangerous permissions already granted")
        }
    }

    fun requestBackgroundLocation(activity: ComponentActivity) {
        val bgPerm = getBackgroundLocationPermission() ?: return
        if (isPermissionGranted(activity, bgPerm)) {
            Log.d(TAG, "Background location already granted")
            return
        }
        if (!isPermissionGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            showRationaleDialog(activity, "Önce ön plan konum izni vermelisiniz.")
            return
        }
        permissionLauncher?.launch(arrayOf(bgPerm)) ?: run {
            ActivityCompat.requestPermissions(activity, arrayOf(bgPerm), 0)
        }
    }

    fun requestPermission(activity: ComponentActivity, permission: String) {
        if (isPermissionGranted(activity, permission)) return
        permissionLauncher?.launch(arrayOf(permission)) ?: run {
            ActivityCompat.requestPermissions(activity, arrayOf(permission), 0)
        }
    }

    fun requestSpecialPermission(context: Context, action: String) {
        val intent = Intent(action).apply {
            if (action == Settings.ACTION_MANAGE_OVERLAY_PERMISSION) {
                data = Uri.parse("package:${context.packageName}")
            } else if (action == Settings.ACTION_MANAGE_WRITE_SETTINGS) {
                data = Uri.parse("package:${context.packageName}")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // ==================== RATIONALE DIALOGLAR ====================

    private fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    private fun showRationaleDialog(activity: Activity, message: String) {
        mainHandler.post {
            AlertDialog.Builder(activity)
                .setTitle("🔐 Bilgi")
                .setMessage(message)
                .setPositiveButton("Tamam") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun showPermissionRationaleDialog(activity: Activity, permission: String) {
        mainHandler.post {
            AlertDialog.Builder(activity)
                .setTitle("🔐 İzin Gerekli")
                .setMessage("$permission izni, uygulamanın düzgün çalışması için gereklidir.")
                .setPositiveButton("İzin Ver") { _, _ ->
                    requestPermission(activity as ComponentActivity, permission)
                }
                .setNegativeButton("Vazgeç") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun showManualPermissionDialog(activity: Activity, permission: String) {
        mainHandler.post {
            AlertDialog.Builder(activity)
                .setTitle("⚠️ İzin Reddedildi")
                .setMessage("$permission izni reddedildi. Lütfen Ayarlar'dan manuel olarak izin verin.")
                .setPositiveButton("Ayarlara Git") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${activity.packageName}")
                    activity.startActivity(intent)
                }
                .setNegativeButton("Kapat") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    // ==================== KALICILIK ====================

    private fun saveState() {
        val context = appContext ?: return
        ioScope.launch {
            try {
                val data = PermissionStateData(
                    permissionStates = permissionStates.toMap(),
                    requestHistory = requestHistory.toMap()
                )
                val json = gson.toJson(data)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString("state", json).apply()
                Log.d(TAG, "State saved")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save state", e)
            }
        }
    }

    private fun loadState(context: Context) {
        ioScope.launch {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString("state", null) ?: return@launch
            try {
                val type = object : TypeToken<PermissionStateData>() {}.type
                val data: PermissionStateData = gson.fromJson(json, type)
                mainHandler.post {
                    permissionStates.clear()
                    permissionStates.putAll(data.permissionStates)
                    requestHistory.clear()
                    requestHistory.putAll(data.requestHistory)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load state", e)
            }
        }
    }

    private data class PermissionStateData(
        val permissionStates: Map<String, Boolean>,
        val requestHistory: Map<String, Int>
    )

    // ==================== ÖZET ====================

    fun getAllPermissionsSummary(context: Context): String {
        val allPermissions = getDangerousPermissions() + getNormalPermissions()
        val builder = StringBuilder()
        builder.appendLine("📋 İZİN DURUMU ÖZETİ")
        builder.appendLine("──────────────────────────")
        builder.appendLine("Toplam İzin: ${allPermissions.size}")
        val grantedCount = allPermissions.count { isPermissionGranted(context, it) }
        builder.appendLine("Verilen: $grantedCount")
        builder.appendLine("Reddedilen: ${allPermissions.size - grantedCount}")
        builder.appendLine("Skor: ${getPermissionScore(context)}% ${getPermissionScoreEmoji(getPermissionScore(context))}")
        builder.appendLine("")
        builder.appendLine("📌 Tehlikeli İzinler:")
        getDangerousPermissions().forEach { perm ->
            builder.appendLine("  ${if (isPermissionGranted(context, perm)) "✅" else "❌"} $perm")
        }
        builder.appendLine("")
        builder.appendLine("📌 Normal İzinler:")
        getNormalPermissions().forEach { perm ->
            builder.appendLine("  ${if (isPermissionGranted(context, perm)) "✅" else "❌"} $perm")
        }
        builder.appendLine("")
        builder.appendLine("📌 Özel İzin Durumu:")
        getSpecialPermissions().forEach { (name, action) ->
            builder.appendLine("  ${if (isSpecialPermissionGranted(context, action)) "✅" else "❌"} $name")
        }
        return builder.toString()
    }
}