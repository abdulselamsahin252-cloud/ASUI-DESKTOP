package com.windowsui.taskbar

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.windowsui.SystemState
import com.windowsui.TaskbarState as MainTaskbarState

/**
 * TaskbarState (taskbar alt paketi)
 *
 * Bu veri sınıfı, görev çubuğunun temel yapılandırma ve durum bilgilerini tutar.
 * Ana TaskbarState (com.windowsui.TaskbarState) ile senkronize çalışır.
 *
 * Özellikler:
 * - Görünürlük, yükseklik, otomatik gizlenme, hizalama
 * - Renk modu (şeffaf, yarı saydam, opak)
 * - Sistem tepsisi öğeleri (Wi-Fi, Bluetooth, Ses, Pil, USB, Bildirim)
 * - Bildirim rozeti ve aktif pencere sayısı
 * - Kullanıcı profili (SystemState ile senkronize)
 * - Tema renkleri (SystemState.theme ile senkronize)
 * - Kalıcılık (SharedPreferences ile kaydetme/yükleme)
 *
 * Tüm değişiklikler otomatik olarak ana TaskbarState'e yansıtılır.
 */
data class TaskbarState(
    // ============ GÖRÜNÜM ============
    var isVisible: Boolean = true,
    var autoHide: Boolean = false,
    var height: Int = 48,                    // dp cinsinden
    var alignment: String = "center",        // "left", "center", "right"
    var colorMode: String = "semi",          // "transparent", "semi", "opaque"

    // ============ GÖSTERİM AYARLARI ============
    var showClock: Boolean = true,
    var showDate: Boolean = true,
    var showSearch: Boolean = true,

    // ============ SİSTEM TEP SİMGE DURUMLARI ============
    var wifiEnabled: Boolean = true,
    var bluetoothEnabled: Boolean = false,
    var volumeLevel: Int = 70,               // 0-100
    var batteryLevel: Int = 85,              // 0-100
    var isCharging: Boolean = false,
    var usbConnected: Boolean = false,

    // ============ PANEL DURUMLARI ============
    var quickSettingsOpen: Boolean = false,
    var notificationPanelOpen: Boolean = false,
    var startMenuOpen: Boolean = false,

    // ============ ROZET VE SAYAÇLAR ============
    var notificationBadgeCount: Int = 0,
    var activeWindowCount: Int = 0,

    // ============ KULLANICI PROFİLİ ============
    var username: String = "Sahibi",
    var profilePicUri: String? = null,

    // ============ TEMA RENKLERİ ============
    var backgroundColor: Long = 0xDF1C1C1C,
    var hoverColor: Long = 0x33FFFFFF,
    var accentColor: Long = 0xFF0078D7
) {

    /**
     * Tema renklerini Compose Color nesnelerine dönüştürür.
     */
    fun getBackgroundColor(): Color = Color(backgroundColor)
    fun getHoverColor(): Color = Color(hoverColor)
    fun getAccentColor(): Color = Color(accentColor)

    /**
     * Ana TaskbarState ile senkronize eder.
     * UI'da periyodik olarak çağrılabilir.
     */
    fun syncWithMain(context: Context) {
        // Görünüm
        MainTaskbarState.isVisible = isVisible
        MainTaskbarState.autoHide = autoHide
        MainTaskbarState.height = height
        MainTaskbarState.alignment = alignment
        MainTaskbarState.colorMode = colorMode

        // Gösterim
        MainTaskbarState.showClock = showClock
        MainTaskbarState.showDate = showDate
        MainTaskbarState.showSearch = showSearch

        // Panel durumları
        MainTaskbarState.quickSettingsOpen = quickSettingsOpen
        MainTaskbarState.notificationPanelOpen = notificationPanelOpen
        MainTaskbarState.startMenuOpen = startMenuOpen

        // Profil
        MainTaskbarState.username = SystemState.username
        MainTaskbarState.profilePicUri = SystemState.profilePicUri

        // Tema
        MainTaskbarState.syncTheme()

        // Rozet ve sayaçlar (NotificationState ve WindowState ile senkronize)
        MainTaskbarState.syncNotificationBadge(context)
        MainTaskbarState.syncActiveWindowCount()

        Log.d("TaskbarState", "Senkronize edildi")
    }

    /**
     * Ana TaskbarState'ten bu veri sınıfına geri yükler.
     */
    fun loadFromMain() {
        isVisible = MainTaskbarState.isVisible
        autoHide = MainTaskbarState.autoHide
        height = MainTaskbarState.height
        alignment = MainTaskbarState.alignment
        colorMode = MainTaskbarState.colorMode

        showClock = MainTaskbarState.showClock
        showDate = MainTaskbarState.showDate
        showSearch = MainTaskbarState.showSearch

        quickSettingsOpen = MainTaskbarState.quickSettingsOpen
        notificationPanelOpen = MainTaskbarState.notificationPanelOpen
        startMenuOpen = MainTaskbarState.startMenuOpen

        username = SystemState.username
        profilePicUri = SystemState.profilePicUri?.toString()

        // Renkleri ana TaskbarState'ten al
        backgroundColor = MainTaskbarState.backgroundColor.value.toLong()
        hoverColor = MainTaskbarState.hoverColor.value.toLong()
        accentColor = MainTaskbarState.accentColor.value.toLong()

        notificationBadgeCount = MainTaskbarState.notificationBadgeCount
        activeWindowCount = MainTaskbarState.activeWindowCount
    }

    // ==================== KALICI DURUM (SharedPreferences) ====================

    companion object {
        private const val PREFS_NAME = "ASUI_TaskbarStatePrefs"
        private const val TAG = "TaskbarState"

        /**
         * SharedPreferences'ten yükler.
         */
        fun load(context: Context): TaskbarState {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return TaskbarState(
                isVisible = prefs.getBoolean("isVisible", true),
                autoHide = prefs.getBoolean("autoHide", false),
                height = prefs.getInt("height", 48),
                alignment = prefs.getString("alignment", "center") ?: "center",
                colorMode = prefs.getString("colorMode", "semi") ?: "semi",
                showClock = prefs.getBoolean("showClock", true),
                showDate = prefs.getBoolean("showDate", true),
                showSearch = prefs.getBoolean("showSearch", true),
                wifiEnabled = prefs.getBoolean("wifiEnabled", true),
                bluetoothEnabled = prefs.getBoolean("bluetoothEnabled", false),
                volumeLevel = prefs.getInt("volumeLevel", 70),
                batteryLevel = prefs.getInt("batteryLevel", 85),
                isCharging = prefs.getBoolean("isCharging", false),
                usbConnected = prefs.getBoolean("usbConnected", false),
                quickSettingsOpen = prefs.getBoolean("quickSettingsOpen", false),
                notificationPanelOpen = prefs.getBoolean("notificationPanelOpen", false),
                startMenuOpen = prefs.getBoolean("startMenuOpen", false),
                notificationBadgeCount = prefs.getInt("notificationBadgeCount", 0),
                activeWindowCount = prefs.getInt("activeWindowCount", 0),
                username = prefs.getString("username", "Sahibi") ?: "Sahibi",
                profilePicUri = prefs.getString("profilePicUri", null),
                backgroundColor = prefs.getLong("backgroundColor", 0xDF1C1C1C),
                hoverColor = prefs.getLong("hoverColor", 0x33FFFFFF),
                accentColor = prefs.getLong("accentColor", 0xFF0078D7)
            )
        }

        /**
         * SharedPreferences'e kaydeder.
         */
        fun save(context: Context, state: TaskbarState) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("isVisible", state.isVisible)
                putBoolean("autoHide", state.autoHide)
                putInt("height", state.height)
                putString("alignment", state.alignment)
                putString("colorMode", state.colorMode)
                putBoolean("showClock", state.showClock)
                putBoolean("showDate", state.showDate)
                putBoolean("showSearch", state.showSearch)
                putBoolean("wifiEnabled", state.wifiEnabled)
                putBoolean("bluetoothEnabled", state.bluetoothEnabled)
                putInt("volumeLevel", state.volumeLevel)
                putInt("batteryLevel", state.batteryLevel)
                putBoolean("isCharging", state.isCharging)
                putBoolean("usbConnected", state.usbConnected)
                putBoolean("quickSettingsOpen", state.quickSettingsOpen)
                putBoolean("notificationPanelOpen", state.notificationPanelOpen)
                putBoolean("startMenuOpen", state.startMenuOpen)
                putInt("notificationBadgeCount", state.notificationBadgeCount)
                putInt("activeWindowCount", state.activeWindowCount)
                putString("username", state.username)
                putString("profilePicUri", state.profilePicUri)
                putLong("backgroundColor", state.backgroundColor)
                putLong("hoverColor", state.hoverColor)
                putLong("accentColor", state.accentColor)
            }.apply()
            Log.d(TAG, "TaskbarState kaydedildi")
        }
    }
}

/**
 * TaskbarState için extension fonksiyonlar.
 */
fun TaskbarState.applyToMain(context: Context) {
    this.syncWithMain(context)
}

fun TaskbarState.loadFromMainAndSave(context: Context) {
    this.loadFromMain()
    TaskbarState.save(context, this)
}