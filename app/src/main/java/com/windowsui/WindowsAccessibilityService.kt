package com.windowsui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.os.Bundle

/**
 * WindowsAccessibilityService for ASUI Desktop.
 *
 * Provides:
 * - Global keyboard shortcuts (Alt+F4, Win+E, Win+R, Alt+Tab, Win+D, Win+1-9, etc.)
 * - Window focus tracking and updating WindowState
 * - System global actions (back, home, recents)
 * - Gesture detection (3-finger swipe, etc.)
 * - Root-based hardware control (brightness, volume, airplane mode)
 * - Mouse/click simulation on desktop icons
 * - Notification panel and quick settings shortcuts
 * - Screen reader/talkback bridge (optional)
 *
 * Requires user to enable Accessibility Service in Settings.
 */
class WindowsAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WindowsAccessibility"
        private var instance: WindowsAccessibilityService? = null

        fun getInstance(): WindowsAccessibilityService? = instance
    }

    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // WindowManager for overlay views (if needed)
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    // ==================== SERVIS YAŞAM DÖNGÜSÜ ====================

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service Connected")

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Servis yapılandırması
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                flags = flags or AccessibilityServiceInfo.FLAG_ENABLE_ACCESSIBILITY_VOLUME
            }
            notificationTimeout = 100
        }
        serviceInfo = info

        // Klavye olaylarını yakalamak için
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }

        // Başlangıçta mevcut pencereleri tara
        rootInActiveWindow?.let { root ->
            scanWindows(root)
        }
    }

    override fun onDestroy() {
        instance = null
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
        super.onDestroy()
    }

    // ==================== OLAY YAKALAMA ====================

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.source?.let { node ->
                    val packageName = event.packageName?.toString() ?: ""
                    val className = event.className?.toString() ?: ""
                    Log.d(TAG, "Window changed: $packageName / $className")
                    // Odaklanan pencereyi WindowState'e bildir
                    // Şimdilik sadece log, ileride WindowState.focusWindowById() çağrılabilir
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Masaüstü simgeleri güncellenmiş olabilir
            }
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START -> {
                // Jest başlangıcı
            }
            AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> {
                // Jest sonu
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // Tıklama olayları
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                // Uzun tıklama
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // Bildirim değişiklikleri (NotificationService zaten var)
            }
        }
    }

    // ==================== KLAVYE KISAYOLLARI (API 28+) ====================

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        // Sadece aşağı basışları yakala (tekrarları önlemek için)
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val keyCode = event.keyCode
        val isCtrl = event.isCtrlPressed
        val isAlt = event.isAltPressed
        val isShift = event.isShiftPressed
        val isMeta = event.isMetaPressed // Win tuşu

        // ============ WINDOWS KISAYOLLARI ============

        // Alt + F4 → Pencereyi kapat
        if (isAlt && keyCode == KeyEvent.KEYCODE_F4) {
            Log.d(TAG, "Alt+F4 pressed")
            val focusedWindow = WindowState.windows.maxByOrNull { it.zIndex }
            if (focusedWindow != null) {
                WindowState.closeWindow(focusedWindow, saveToHistory = true)
                WindowState.saveState(applicationContext)
            }
            return true
        }

        // Win + E → Dosya Gezgini
        if (isMeta && keyCode == KeyEvent.KEYCODE_E) {
            Log.d(TAG, "Win+E pressed")
            WindowState.openWindow(
                id = "explorer_${System.currentTimeMillis()}",
                title = "Dosya Gezgini",
                contentId = "explorer",
                width = 800,
                height = 500
            )
            WindowState.saveState(applicationContext)
            return true
        }

        // Win + R → Çalıştır (CmdTerminal)
        if (isMeta && keyCode == KeyEvent.KEYCODE_R) {
            Log.d(TAG, "Win+R pressed")
            WindowState.openWindow(
                id = "run_${System.currentTimeMillis()}",
                title = "Çalıştır",
                contentId = "cmd",
                width = 450,
                height = 200
            )
            WindowState.saveState(applicationContext)
            return true
        }

        // Win + D → Masaüstünü göster (tüm pencereleri minimize et)
        if (isMeta && keyCode == KeyEvent.KEYCODE_D) {
            Log.d(TAG, "Win+D pressed")
            WindowState.minimizeAllWindows()
            WindowState.saveState(applicationContext)
            return true
        }

        // Win + Tab → Görev Görünümü (sanal masaüstleri arası geçiş)
        if (isMeta && keyCode == KeyEvent.KEYCODE_TAB) {
            Log.d(TAG, "Win+Tab pressed")
            // Sanal masaüstü değiştir
            val desktops = SystemState.virtualDesktops
            if (desktops.isNotEmpty()) {
                val current = SystemState.currentDesktop
                val index = desktops.indexOf(current)
                val nextIndex = if (index + 1 < desktops.size) index + 1 else 0
                SystemState.switchDesktop(applicationContext, desktops[nextIndex])
            }
            return true
        }

        // Alt + Tab → Pencere değiştir (Windows'daki gibi döngü)
        if (isAlt && keyCode == KeyEvent.KEYCODE_TAB) {
            Log.d(TAG, "Alt+Tab pressed")
            val windows = WindowState.windows.filter { !it.isMinimized && !it.isHidden }
            if (windows.isNotEmpty()) {
                val focused = windows.maxByOrNull { it.zIndex }
                val index = if (focused != null) windows.indexOf(focused) else -1
                val nextIndex = if (index + 1 < windows.size) index + 1 else 0
                val nextWindow = windows[nextIndex]
                WindowState.focusWindow(nextWindow)
                WindowState.saveState(applicationContext)
            }
            return true
        }

        // Win + 1-9 → Görev çubuğundaki ilk 9 uygulamayı başlat / odakla
        if (isMeta && keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) {
            val index = keyCode - KeyEvent.KEYCODE_1
            Log.d(TAG, "Win+${index+1} pressed")
            val pinned = SystemState.pinnedApps
            if (index < pinned.size) {
                val app = pinned[index]
                val existing = WindowState.windows.find { it.contentId == app }
                if (existing != null) {
                    WindowState.focusWindow(existing)
                    if (existing.isMinimized) {
                        // Restore et
                        WindowState.restoreWindow(existing)
                    }
                } else {
                    val intent = packageManager.getLaunchIntentForPackage(app)
                    if (intent != null) {
                        startActivity(intent)
                    }
                }
                WindowState.saveState(applicationContext)
            }
            return true
        }

        // Ctrl + C, Ctrl+V (kopyala/yapıştır) – şimdilik sadece terminal için, zaten CmdTerminal'de var
        // Ama global olarak da ekleyebiliriz.

        // F11 → Tam ekran geçişi (mevcut pencere)
        if (keyCode == KeyEvent.KEYCODE_F11) {
            Log.d(TAG, "F11 pressed")
            val focused = WindowState.windows.maxByOrNull { it.zIndex }
            if (focused != null) {
                WindowState.maximizeWindow(focused)
                WindowState.saveState(applicationContext)
            }
            return true
        }

        // ESC → Odaklanmış pencereyi kapat (isteğe bağlı)
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            Log.d(TAG, "ESC pressed")
            val focused = WindowState.windows.maxByOrNull { it.zIndex }
            if (focused != null && focused.contentId != "desktop") {
                WindowState.closeWindow(focused, saveToHistory = true)
                WindowState.saveState(applicationContext)
            }
            return true
        }

        return false // Diğer tuşlar normal davranışa gönderilsin
    }

    // ==================== JEST YAKALAMA (3 PARMAK KAYDIRMA, vb.) ====================

    override fun onGesture(gestureId: Int): Boolean {
        Log.d(TAG, "Gesture: $gestureId")
        return when (gestureId) {
            GESTURE_SWIPE_DOWN_AND_LEFT -> {
                // Hızlı Ayarlar panelini aç
                // TaskbarComponent'deki quick settings açma işlemini tetikle
                // Bunu bir broadcast veya callback ile yapabiliriz.
                // Şimdilik sadece log
                true
            }
            GESTURE_SWIPE_UP_AND_RIGHT -> {
                // Bildirim panelini aç
                true
            }
            else -> super.onGesture(gestureId)
        }
    }

    // ==================== SİSTEM EYLEMLERİ ====================

    fun performSystemAction(action: Int): Boolean {
        return performGlobalAction(action)
    }

    // ==================== FAREMOUSE / KLİK SİMÜLASYONU ====================

    /**
     * Ekranda belirtilen koordinatlara tıklama simüle eder.
     * @param x piksel cinsinden X koordinatı
     * @param y piksel cinsinden Y koordinatı
     */
    fun simulateClick(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Belirtilen metni (veya kısayolu) aktif pencereye yazar.
     */
    fun typeText(text: String): Boolean {
        return try {
            val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (node != null) {
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // ==================== PENCERE TARAMA ====================

    /**
     * Aktif penceredeki tüm AccessibilityNodeInfo'ları tarar,
     * masaüstü simgelerini veya pencere başlıklarını bulur.
     */
    private fun scanWindows(node: AccessibilityNodeInfo) {
        // Örnek: packageName veya className ile filtrele
        // Şimdilik sadece log
    }

    // ==================== OVERLAY (KLAVYE / FARE GÖSTERGESİ) ====================

    /**
     * Ekran üzerinde geçici bir overlay gösterir (örnek: klavye kısayol bildirimi).
     */
    fun showOverlay(view: View) {
        try {
            if (overlayView != null) {
                windowManager.removeView(overlayView)
                overlayView = null
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            windowManager.addView(view, params)
            overlayView = view
            // 2 saniye sonra kaldır
            handler.postDelayed({
                try {
                    overlayView?.let {
                        windowManager.removeView(it)
                        overlayView = null
                    }
                } catch (e: Exception) {}
            }, 2000)
        } catch (e: Exception) {
            Log.e(TAG, "Overlay gösterilemedi", e)
        }
    }

    // ==================== ROOT İLE DONANIM KONTROLÜ (KISAYOLLAR) ====================

    /**
     * Ses seviyesini ayarla (root ile).
     */
    fun setVolume(level: Int) {
        coroutineScope.launch {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "media volume --set $level"))
                p.waitFor()
            } catch (e: Exception) {}
        }
    }

    /**
     * Parlaklığı ayarla (root ile).
     */
    fun setBrightness(value: Float) {
        coroutineScope.launch {
            try {
                val brightness = (value * 255).toInt().coerceIn(0, 255)
                val cmd = "settings put system screen_brightness $brightness"
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                p.waitFor()
            } catch (e: Exception) {}
        }
    }

    /**
     * Uçak modunu aç/kapat (root ile).
     */
    fun setAirplaneMode(enabled: Boolean) {
        coroutineScope.launch {
            try {
                val cmd = if (enabled) "settings put global airplane_mode_on 1" else "settings put global airplane_mode_on 0"
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                p.waitFor()
                // Broadcast'i tetikle
                val intent = android.content.Intent(android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED)
                sendBroadcast(intent)
            } catch (e: Exception) {}
        }
    }

    // ==================== YARDIMCI ====================

    /**
     * Servisin aktif olup olmadığını kontrol eder.
     */
    fun isEnabled(): Boolean {
        return instance != null
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }
}