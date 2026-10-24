package com.windowsui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * System State for ASUI Desktop.
 *
 * Central state management with full persistence.
 * All lists are stored as comma-separated strings to preserve order.
 * Every mutation automatically persists to SharedPreferences.
 *
 * Features:
 * - User profile (name, picture)
 * - Wallpaper and themes (dark/light)
 * - Desktop apps, recycle bin, virtual desktops, widgets
 * - Taskbar settings (auto-hide, height, clock/date visibility)
 * - Lock screen settings (enabled, password)
 * - Default applications (file manager, terminal, browser)
 * - Screen timeout, icon size, brightness, volume
 * - Recent apps, favorites, pinned apps (taskbar)
 * - Power saving mode, animations, notification settings
 * - All installed apps cache (DesktopApp)
 *
 * All state is automatically saved on every change.
 */
object SystemState {

    private const val TAG = "SystemState"

    // ==================== KULLANICI PROFİLİ ====================
    var username by mutableStateOf("Sahibi")
    var profilePicUri by mutableStateOf<Uri?>(null)
    var wallpaperUri by mutableStateOf<Uri?>(null)

    // ==================== TEMA ====================
    var theme by mutableStateOf("dark") // "dark" veya "light"

    // ==================== MASAÜSTÜ UYGULAMALARI (Sıralı Liste) ====================
    val desktopApps = mutableStateListOf<String>()      // packageName'ler
    val recycleBinItems = mutableStateListOf<String>()  // packageName'ler
    val recentApps = mutableStateListOf<String>()       // en son açılanlar (sıralı)
    val favoriteApps = mutableStateListOf<String>()     // sık kullanılanlar (sıralı)
    val pinnedApps = mutableStateListOf<String>()       // görev çubuğuna sabitlenenler (sıralı)

    // ==================== SANAL MASAÜSTLERİ ====================
    val virtualDesktops = mutableStateListOf<String>()  // ID'ler (sıralı)
    var currentDesktop by mutableStateOf("")

    // ==================== WIDGET'LAR ====================
    val widgets = mutableStateListOf<String>()          // Widget adları (sıralı)

    // ==================== GÖREV ÇUBUĞU AYARLARI ====================
    var taskbarAutoHide by mutableStateOf(false)
    var taskbarHeight by mutableStateOf(48)             // dp cinsinden
    var showClock by mutableStateOf(true)
    var showDate by mutableStateOf(true)
    var taskbarAlignment by mutableStateOf("center")    // "left", "center", "right"

    // ==================== KİLİT EKRANI ====================
    var lockScreenEnabled by mutableStateOf(false)
    var lockScreenPassword by mutableStateOf<String?>(null)

    // ==================== VARSAYILAN UYGULAMALAR ====================
    var defaultFileManager by mutableStateOf("com.windowsui.FileExplorer")
    var defaultTerminal by mutableStateOf("com.windowsui.CmdTerminal")
    var defaultBrowser by mutableStateOf("com.android.chrome")
    var defaultLauncher by mutableStateOf("com.windowsui.MainActivity")

    // ==================== SİSTEM AYARLARI ====================
    var screenTimeout by mutableStateOf(60)             // saniye cinsinden (0 = hiç kapanmasın)
    var iconSize by mutableStateOf(44)                  // dp cinsinden
    var brightness by mutableStateOf(0.5f)              // 0.0 – 1.0
    var volume by mutableStateOf(0.7f)                  // 0.0 – 1.0
    var powerSavingMode by mutableStateOf(false)
    var animationsEnabled by mutableStateOf(true)

    // ==================== BİLDİRİM AYARLARI ====================
    var notificationsEnabled by mutableStateOf(true)
    var notificationSoundEnabled by mutableStateOf(true)

    // ==================== ÖNBellek (Tüm yüklü uygulamalar) ====================
    var isAppsLoaded by mutableStateOf(false)
    val allInstalledAppsCache = mutableStateListOf<DesktopApp>()

    // ==================== PRIVATE – PERSISTENCE ====================

    private fun saveStringList(prefs: android.content.SharedPreferences, key: String, list: List<String>) {
        prefs.edit().putString(key, list.joinToString(",")).apply()
    }

    private fun loadStringList(prefs: android.content.SharedPreferences, key: String): List<String> {
        val raw = prefs.getString(key, null)
        return if (raw.isNullOrEmpty()) emptyList() else raw.split(",").filter { it.isNotEmpty() }
    }

    private fun saveStringSetCompat(prefs: android.content.SharedPreferences, key: String, set: Set<String>) {
        prefs.edit().putStringSet(key, set).apply()
    }

    private fun loadStringSetCompat(prefs: android.content.SharedPreferences, key: String): Set<String> {
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    // ==================== PUBLIC – PERSISTENCE ====================

    /**
     * Tüm durumu SharedPreferences'ten yükler.
     */
    fun loadState(context: Context) {
        val prefs = context.getSharedPreferences("ASUI_SystemState", Context.MODE_PRIVATE)

        // Kullanıcı
        username = prefs.getString("username", "Sahibi") ?: "Sahibi"
        profilePicUri = prefs.getString("profilePicUri", null)?.let { Uri.parse(it) }
        wallpaperUri = prefs.getString("wallpaperUri", null)?.let { Uri.parse(it) }

        // Tema
        theme = prefs.getString("theme", "dark") ?: "dark"

        // Sıralı listeler (joinToString ile)
        desktopApps.clear()
        desktopApps.addAll(loadStringList(prefs, "desktopApps"))

        recycleBinItems.clear()
        recycleBinItems.addAll(loadStringList(prefs, "recycleBinItems"))

        recentApps.clear()
        recentApps.addAll(loadStringList(prefs, "recentApps"))

        favoriteApps.clear()
        favoriteApps.addAll(loadStringList(prefs, "favoriteApps"))

        pinnedApps.clear()
        pinnedApps.addAll(loadStringList(prefs, "pinnedApps"))

        // Sanal masaüstleri
        virtualDesktops.clear()
        virtualDesktops.addAll(loadStringList(prefs, "virtualDesktops"))
        currentDesktop = prefs.getString("currentDesktop", "") ?: ""

        // Widget'lar
        widgets.clear()
        widgets.addAll(loadStringList(prefs, "widgets"))

        // Görev çubuğu
        taskbarAutoHide = prefs.getBoolean("taskbarAutoHide", false)
        taskbarHeight = prefs.getInt("taskbarHeight", 48)
        showClock = prefs.getBoolean("showClock", true)
        showDate = prefs.getBoolean("showDate", true)
        taskbarAlignment = prefs.getString("taskbarAlignment", "center") ?: "center"

        // Kilit ekranı
        lockScreenEnabled = prefs.getBoolean("lockScreenEnabled", false)
        lockScreenPassword = prefs.getString("lockScreenPassword", null)

        // Varsayılan uygulamalar
        defaultFileManager = prefs.getString("defaultFileManager", "com.windowsui.FileExplorer") ?: "com.windowsui.FileExplorer"
        defaultTerminal = prefs.getString("defaultTerminal", "com.windowsui.CmdTerminal") ?: "com.windowsui.CmdTerminal"
        defaultBrowser = prefs.getString("defaultBrowser", "com.android.chrome") ?: "com.android.chrome"
        defaultLauncher = prefs.getString("defaultLauncher", "com.windowsui.MainActivity") ?: "com.windowsui.MainActivity"

        // Sistem
        screenTimeout = prefs.getInt("screenTimeout", 60)
        iconSize = prefs.getInt("iconSize", 44)
        brightness = prefs.getFloat("brightness", 0.5f)
        volume = prefs.getFloat("volume", 0.7f)
        powerSavingMode = prefs.getBoolean("powerSavingMode", false)
        animationsEnabled = prefs.getBoolean("animationsEnabled", true)

        // Bildirimler
        notificationsEnabled = prefs.getBoolean("notificationsEnabled", true)
        notificationSoundEnabled = prefs.getBoolean("notificationSoundEnabled", true)

        Log.d(TAG, "State loaded")
    }

    /**
     * Tüm durumu SharedPreferences'e kaydeder.
     */
    fun saveState(context: Context) {
        val prefs = context.getSharedPreferences("ASUI_SystemState", Context.MODE_PRIVATE)
        prefs.edit().apply {
            // Kullanıcı
            putString("username", username)
            putString("profilePicUri", profilePicUri?.toString())
            putString("wallpaperUri", wallpaperUri?.toString())

            // Tema
            putString("theme", theme)

            // Sıralı listeler (joinToString)
            putString("desktopApps", desktopApps.joinToString(","))
            putString("recycleBinItems", recycleBinItems.joinToString(","))
            putString("recentApps", recentApps.joinToString(","))
            putString("favoriteApps", favoriteApps.joinToString(","))
            putString("pinnedApps", pinnedApps.joinToString(","))
            putString("virtualDesktops", virtualDesktops.joinToString(","))
            putString("widgets", widgets.joinToString(","))

            putString("currentDesktop", currentDesktop)

            // Görev çubuğu
            putBoolean("taskbarAutoHide", taskbarAutoHide)
            putInt("taskbarHeight", taskbarHeight)
            putBoolean("showClock", showClock)
            putBoolean("showDate", showDate)
            putString("taskbarAlignment", taskbarAlignment)

            // Kilit ekranı
            putBoolean("lockScreenEnabled", lockScreenEnabled)
            putString("lockScreenPassword", lockScreenPassword)

            // Varsayılan uygulamalar
            putString("defaultFileManager", defaultFileManager)
            putString("defaultTerminal", defaultTerminal)
            putString("defaultBrowser", defaultBrowser)
            putString("defaultLauncher", defaultLauncher)

            // Sistem
            putInt("screenTimeout", screenTimeout)
            putInt("iconSize", iconSize)
            putFloat("brightness", brightness)
            putFloat("volume", volume)
            putBoolean("powerSavingMode", powerSavingMode)
            putBoolean("animationsEnabled", animationsEnabled)

            // Bildirimler
            putBoolean("notificationsEnabled", notificationsEnabled)
            putBoolean("notificationSoundEnabled", notificationSoundEnabled)

            apply()
        }
        Log.d(TAG, "State saved")
    }

    // ==================== MUTASYON FONKSİYONLARI (Otomatik Kaydetme) ====================

    private fun mutateList(context: Context, list: MutableList<String>, action: (MutableList<String>) -> Unit) {
        action(list)
        saveState(context)
    }

    fun addDesktopApp(context: Context, packageName: String) {
        mutateList(context, desktopApps) {
            if (!it.contains(packageName)) it.add(packageName)
        }
    }

    fun removeDesktopApp(context: Context, packageName: String) {
        mutateList(context, desktopApps) {
            if (it.contains(packageName)) {
                it.remove(packageName)
                if (!recycleBinItems.contains(packageName)) {
                    recycleBinItems.add(packageName)
                }
            }
        }
        saveState(context) // recycleBinItems değiştiği için tekrar kaydet
    }

    fun restoreFromRecycleBin(context: Context, packageName: String) {
        mutateList(context, recycleBinItems) {
            if (it.contains(packageName)) {
                it.remove(packageName)
                if (!desktopApps.contains(packageName)) {
                    desktopApps.add(packageName)
                }
            }
        }
        saveState(context)
    }

    fun emptyRecycleBin(context: Context) {
        mutateList(context, recycleBinItems) { it.clear() }
    }

    fun addRecentApp(context: Context, packageName: String) {
        mutateList(context, recentApps) {
            it.remove(packageName) // önce kaldır, sonra başa ekle
            it.add(0, packageName)
            if (it.size > 20) it.removeAt(it.size - 1) // max 20
        }
    }

    fun addFavoriteApp(context: Context, packageName: String) {
        mutateList(context, favoriteApps) {
            if (!it.contains(packageName)) it.add(packageName)
        }
    }

    fun removeFavoriteApp(context: Context, packageName: String) {
        mutateList(context, favoriteApps) { it.remove(packageName) }
    }

    fun pinApp(context: Context, packageName: String) {
        mutateList(context, pinnedApps) {
            if (!it.contains(packageName)) it.add(packageName)
        }
    }

    fun unpinApp(context: Context, packageName: String) {
        mutateList(context, pinnedApps) { it.remove(packageName) }
    }

    fun createVirtualDesktop(context: Context): String {
        val id = "desktop_${System.currentTimeMillis()}"
        mutateList(context, virtualDesktops) { it.add(id) }
        currentDesktop = id
        saveState(context)
        return id
    }

    fun switchDesktop(context: Context, id: String) {
        if (virtualDesktops.contains(id)) {
            currentDesktop = id
            saveState(context)
        }
    }

    fun removeVirtualDesktop(context: Context, id: String) {
        if (virtualDesktops.size <= 1) return
        mutateList(context, virtualDesktops) {
            if (it.contains(id)) {
                it.remove(id)
                if (currentDesktop == id) {
                    currentDesktop = it.firstOrNull() ?: ""
                }
            }
        }
        saveState(context)
    }

    fun addWidget(context: Context, name: String) {
        mutateList(context, widgets) {
            if (!it.contains(name)) it.add(name)
        }
    }

    fun removeWidget(context: Context, name: String) {
        mutateList(context, widgets) { it.remove(name) }
    }

    fun setTheme(context: Context, newTheme: String) {
        if (newTheme == "dark" || newTheme == "light") {
            theme = newTheme
            saveState(context)
        }
    }

    fun setLockScreenPassword(context: Context, password: String?) {
        lockScreenPassword = password
        lockScreenEnabled = password != null && password.isNotEmpty()
        saveState(context)
    }

    fun checkLockScreenPassword(input: String): Boolean {
        return if (lockScreenEnabled && lockScreenPassword != null) {
            input == lockScreenPassword
        } else {
            true
        }
    }

    fun setBrightness(context: Context, value: Float) {
        brightness = value.coerceIn(0f, 1f)
        saveState(context)
    }

    fun setVolume(context: Context, value: Float) {
        volume = value.coerceIn(0f, 1f)
        saveState(context)
    }

    fun setPowerSavingMode(context: Context, enabled: Boolean) {
        powerSavingMode = enabled
        saveState(context)
    }

    fun setAnimationsEnabled(context: Context, enabled: Boolean) {
        animationsEnabled = enabled
        saveState(context)
    }
}