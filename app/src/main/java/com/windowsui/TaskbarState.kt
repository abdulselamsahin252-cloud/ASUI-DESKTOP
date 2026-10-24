package com.windowsui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * TaskbarState for ASUI Desktop.
 *
 * Central state management for the taskbar:
 * - Running and pinned apps
 * - Visibility, auto-hide, height, alignment
 * - Clock, date, search visibility
 * - System tray items (Wi-Fi, volume, battery, USB, Bluetooth, etc.)
 * - Notification badge count (sync with NotificationState)
 * - Active window count (sync with WindowState)
 * - Hovered app for previews
 * - Window previews (thumbnails)
 * - Color mode (transparent, semi-transparent, opaque)
 * - Panel states (quick settings, notification panel, start menu)
 * - User profile (sync with SystemState)
 *
 * All state is automatically persisted to SharedPreferences.
 * UI components observe this state via SnapshotStateList and mutableStateOf.
 */
object TaskbarState {

    private const val TAG = "TaskbarState"

    // ==================== ÇALIŞAN VE SABİTLENMİŞ UYGULAMALAR ====================
    // Çalışan uygulamalar (TaskbarComponent tarafından güncellenir)
    val runningApps = mutableStateListOf<DesktopApp>()

    // Sabitlenmiş uygulamalar (SystemState ile senkronize edilir)
    // Doğrudan SystemState.pinnedApps kullanılabilir, ancak burada ayrı tutup senkronize ediyoruz.
    val pinnedApps = mutableStateListOf<DesktopApp>()

    // ==================== GÖREV ÇUBUĞU GÖRÜNÜMÜ ====================
    var isVisible by mutableStateOf(true)
    var autoHide by mutableStateOf(false)
    var height by mutableStateOf(48)             // dp cinsinden
    var alignment by mutableStateOf("center")    // "left", "center", "right"
    var colorMode by mutableStateOf("semi")      // "transparent", "semi", "opaque"

    var showClock by mutableStateOf(true)
    var showDate by mutableStateOf(true)
    var showSearch by mutableStateOf(true)

    // ==================== SİSTEM TEP SİMGE DURUMLARI ====================
    // Sistem tepsisi öğeleri (simgeler ve durumları)
    data class SystemTrayItem(
        val id: String,
        val iconRes: Int,
        val label: String,
        val isActive: Boolean = true,
        val onClick: (() -> Unit)? = null,
        val onLongClick: (() -> Unit)? = null
    )

    val systemTrayItems = mutableStateListOf<SystemTrayItem>()

    // Hızlı durumlar (UI'da gösterilmek üzere)
    var wifiEnabled by mutableStateOf(true)
    var bluetoothEnabled by mutableStateOf(false)
    var volumeLevel by mutableStateOf(70)        // 0-100
    var batteryLevel by mutableStateOf(85)       // 0-100
    var isCharging by mutableStateOf(false)
    var usbConnected by mutableStateOf(false)

    // ==================== PANEL DURUMLARI ====================
    var quickSettingsOpen by mutableStateOf(false)
    var notificationPanelOpen by mutableStateOf(false)
    var startMenuOpen by mutableStateOf(false)

    // ==================== BİLDİRİM ROZETİ ====================
    var notificationBadgeCount by mutableStateOf(0)
        private set

    // NotificationState ile senkronize
    private var notificationSyncJob: kotlinx.coroutines.Job? = null

    fun syncNotificationBadge(context: Context) {
        notificationBadgeCount = NotificationState.notifications.size
        // UI'ı güncellemek için saveState gerekmez (sadece görsel)
        Log.d(TAG, "Notification badge count: $notificationBadgeCount")
    }

    // ==================== AKTİF PENCERE SAYISI ====================
    var activeWindowCount by mutableStateOf(0)
        private set

    // WindowState ile senkronize
    fun syncActiveWindowCount() {
        activeWindowCount = WindowState.windows.filter { !it.isMinimized && !it.isHidden }.size
    }

    // ==================== FARE İLE ÜZERİNE GELİNEN UYGULAMA / ÖNİZLEME ====================
    var hoveredApp: DesktopApp? by mutableStateOf(null)
    var hoveredWindowId: String? by mutableStateOf(null)

    // Pencere önizlemeleri (WindowState ile senkronize)
    // Key: windowId, Value: WindowSnapshot (Bitmap veya herhangi bir veri)
    val windowPreviews = mutableStateMapOf<String, WindowState.WindowSnapshot>()

    // ==================== KULLANICI PROFİLİ (SystemState ile senkronize) ====================
    var username by mutableStateOf("Sahibi")
    var profilePicUri by mutableStateOf<android.net.Uri?>(null)

    fun syncUserProfile() {
        username = SystemState.username
        profilePicUri = SystemState.profilePicUri
    }

    // ==================== GÖREV ÇUBUĞU RENKLERİ (Tema ile senkronize) ====================
    var backgroundColor by mutableStateOf(androidx.compose.ui.graphics.Color(0xDF1C1C1C))
    var hoverColor by mutableStateOf(androidx.compose.ui.graphics.Color(0x33FFFFFF))
    var accentColor by mutableStateOf(androidx.compose.ui.graphics.Color(0xFF0078D7))

    fun syncTheme() {
        val isDark = SystemState.theme == "dark"
        backgroundColor = if (isDark) {
            androidx.compose.ui.graphics.Color(0xDF1C1C1C)
        } else {
            androidx.compose.ui.graphics.Color(0xDFF0F0F0)
        }
        hoverColor = if (isDark) {
            androidx.compose.ui.graphics.Color(0x33FFFFFF)
        } else {
            androidx.compose.ui.graphics.Color(0x33000000)
        }
    }

    // ==================== KALICI DURUM (SharedPreferences) ====================

    fun loadState(context: Context) {
        val prefs = context.getSharedPreferences("ASUI_TaskbarState", Context.MODE_PRIVATE)

        isVisible = prefs.getBoolean("isVisible", true)
        autoHide = prefs.getBoolean("autoHide", false)
        height = prefs.getInt("height", 48)
        alignment = prefs.getString("alignment", "center") ?: "center"
        colorMode = prefs.getString("colorMode", "semi") ?: "semi"

        showClock = prefs.getBoolean("showClock", true)
        showDate = prefs.getBoolean("showDate", true)
        showSearch = prefs.getBoolean("showSearch", true)

        quickSettingsOpen = prefs.getBoolean("quickSettingsOpen", false)
        notificationPanelOpen = prefs.getBoolean("notificationPanelOpen", false)
        startMenuOpen = prefs.getBoolean("startMenuOpen", false)

        // Sistem tepsisi öğelerini yükle (basitçe varsayılan, daha sonra dinamik olabilir)
        // Şimdilik sadece log
        Log.d(TAG, "TaskbarState loaded")
    }

    fun saveState(context: Context) {
        val prefs = context.getSharedPreferences("ASUI_TaskbarState", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("isVisible", isVisible)
            putBoolean("autoHide", autoHide)
            putInt("height", height)
            putString("alignment", alignment)
            putString("colorMode", colorMode)

            putBoolean("showClock", showClock)
            putBoolean("showDate", showDate)
            putBoolean("showSearch", showSearch)

            putBoolean("quickSettingsOpen", quickSettingsOpen)
            putBoolean("notificationPanelOpen", notificationPanelOpen)
            putBoolean("startMenuOpen", startMenuOpen)
        }.apply()
        Log.d(TAG, "TaskbarState saved")
    }

    // ==================== SENKRONİZASYON (UI'da çağrılacak) ====================

    /**
     * Tüm bağımlı durumları senkronize eder (NotificationState, WindowState, SystemState).
     * TaskbarComponent veya DesktopScreen'den periyodik olarak çağrılabilir.
     */
    fun syncAll(context: Context) {
        syncNotificationBadge(context)
        syncActiveWindowCount()
        syncUserProfile()
        syncTheme()
        // SystemTray öğelerini güncelle (WiFi, Bluetooth, USB, vb.)
        // Burada donanım durumları okunabilir (örnek: UsbOtgManager)
        updateSystemTrayItems(context)
    }

    private fun updateSystemTrayItems(context: Context) {
        // Mevcut öğeleri temizle
        systemTrayItems.clear()

        // Wi-Fi
        systemTrayItems.add(
            SystemTrayItem(
                id = "wifi",
                iconRes = if (wifiEnabled) R.drawable.wifi else R.drawable.wifi,
                label = "Wi-Fi",
                isActive = wifiEnabled,
                onClick = { /* Wi-Fi ayarları aç */ }
            )
        )

        // Bluetooth
        systemTrayItems.add(
            SystemTrayItem(
                id = "bluetooth",
                iconRes = R.drawable.bluetooth,
                label = "Bluetooth",
                isActive = bluetoothEnabled,
                onClick = { /* Bluetooth ayarları aç */ }
            )
        )

        // Ses
        systemTrayItems.add(
            SystemTrayItem(
                id = "volume",
                iconRes = if (volumeLevel > 0) R.drawable.volume else R.drawable.volume,
                label = "Ses: $volumeLevel%",
                isActive = volumeLevel > 0,
                onClick = { /* Ses ayarları aç */ }
            )
        )

        // Pil
        systemTrayItems.add(
            SystemTrayItem(
                id = "battery",
                iconRes = R.drawable.battery,
                label = "Pil: $batteryLevel%${if (isCharging) " (Şarj oluyor)" else ""}",
                isActive = batteryLevel > 10,
                onClick = { /* Pil bilgileri aç */ }
            )
        )

        // USB
        if (usbConnected) {
            systemTrayItems.add(
                SystemTrayItem(
                    id = "usb",
                    iconRes = R.drawable.usb,
                    label = "USB bağlandı",
                    isActive = true,
                    onClick = { /* USB bilgileri aç */ }
                )
            )
        }

        // Bildirim
        systemTrayItems.add(
            SystemTrayItem(
                id = "notification",
                iconRes = R.drawable.notification,
                label = "Bildirimler ($notificationBadgeCount)",
                isActive = notificationBadgeCount > 0,
                onClick = { /* Bildirim panelini aç */ }
            )
        )
    }

    // ==================== YARDIMCI ====================

    /**
     * Görev çubuğu simgelerinden birine tıklandığında çağrılır.
     */
    fun onSystemTrayItemClick(itemId: String) {
        when (itemId) {
            "wifi" -> { /* Wi-Fi paneli aç */ }
            "bluetooth" -> { /* Bluetooth paneli aç */ }
            "volume" -> { /* Ses paneli aç */ }
            "battery" -> { /* Pil paneli aç */ }
            "usb" -> { /* USB bilgileri aç */ }
            "notification" -> { notificationPanelOpen = !notificationPanelOpen }
        }
    }

    /**
     * Görev çubuğunun görünürlüğünü değiştirir (otomatik gizlenme modunda).
     */
    fun toggleVisibility() {
        if (autoHide) {
            isVisible = !isVisible
        }
    }

    /**
     * Bir uygulamayı sabitler.
     */
    fun pinApp(context: android.content.Context, app: DesktopApp) {
        if (!pinnedApps.any { it.packageName == app.packageName }) {
            pinnedApps.add(app)
            // SystemState ile senkronize et
            SystemState.pinApp(context, app.packageName)
        }
    }

    /**
     * Bir uygulamayı sabitlemeden çıkarır.
     */
    fun unpinApp(context: android.content.Context, app: DesktopApp) {
        pinnedApps.removeAll { it.packageName == app.packageName }
        SystemState.unpinApp(context, app.packageName)
    }

    /**
     * Görev çubuğunu sıfırlar (varsayılan ayarlara döndürür).
     */
    fun resetToDefaults(context: Context) {
        isVisible = true
        autoHide = false
        height = 48
        alignment = "center"
        colorMode = "semi"
        showClock = true
        showDate = true
        showSearch = true
        quickSettingsOpen = false
        notificationPanelOpen = false
        startMenuOpen = false
        pinnedApps.clear()
        saveState(context)
    }
}