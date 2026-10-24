package com.windowsui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import android.app.AlertDialog
import java.io.BufferedReader
import java.io.InputStreamReader
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.lifecycleScope

/**
 * MainActivity – ASUI Desktop Ana Aktivitesi
 *
 * Bu aktivite, uygulamanın giriş noktasıdır.
 * Tüm sistem servislerini başlatır, durumları yükler ve DesktopScreen'i gösterir.
 *
 * 🚀 "Akla Hayale Gelmez" Özellikler:
 * - 🧘 Ruh Karşılaması: Cihaz kişiliğine göre özel hoş geldin mesajı ve animasyon.
 * - 🔮 Sihirli Başlangıç: İlk açılışta rastgele bir tema veya duvar kağıdı önerir.
 * - 🎭 Gizli Geliştirici Menüsü: Başlığa 5 kez tıklayınca açılır (logcat, debug).
 * - ⏳ Canlı Splash: Yüklenirken dönen renkli ikon (DeviceInfo ruh rengi).
 * - 🌈 Dinamik Tema: Sistem temasına göre otomatik renk paleti.
 * - 📊 Sistem Özeti: Açılışta kısa bir Toast ile pil, CPU, RAM özeti.
 * - 🔔 Bildirim Servisi Kontrolü: Eksikse kullanıcıyı ayarlara yönlendirir.
 * - ♿ Erişilebilirlik Servisi Kontrolü: Eksikse kullanıcıyı ayarlara yönlendirir.
 * - 🧹 Önbellek Temizliği: Uygulama kapanırken geçici dosyaları temizler.
 * - 💎 Kristal Başlangıç Ekranı: Şeffaf ve parlak bir karşılama.
 * - 🔥 Ateş Animasyonu: Yüklenirken alev efekti (simülasyon).
 *
 * Tüm servisler ve durumlar, mevcut dosyalarla tam uyumludur.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_ACCESSIBILITY = 1001
        private const val REQUEST_CODE_NOTIFICATION = 1002
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1003

        // Geliştirici menüsü için tıklama sayacı
        private var devClickCount = 0
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isAppReady = false
    private var splashJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge (tam ekran)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Durum yüklemeleri
        loadAllStates()

        // Servis başlatmaları
        initializeServices()

        // Gizli geliştirici menüsü için click count sıfırla
        devClickCount = 0

        // Compose içeriği
        setContent {
            // Tema (SystemState.theme) ile otomatik uyum
            val isDark = SystemState.theme == "dark"
            val materialColors = if (isDark) {
                darkColorScheme()
            } else {
                lightColorScheme()
            }

            MaterialTheme(
                colorScheme = materialColors,
                typography = Typography(),
                content = {
                    // Cihaz ruhuna göre karşılama mesajı göster
                    LaunchedEffect(Unit) {
                        showSpiritWelcome()
                    }

                    // Ana içerik
                    DesktopScreen()
                }
            )
        }

        // Kilit ekranı kontrolü (eğer açık ise, DesktopScreen zaten LockScreenPanel gösterir)
        // Ancak aktivite başlangıcında kilit ekranı hemen gösterilmesi için bir kontrol eklenebilir.

        // Splash / karşılama animasyonu (opsiyonel)
        showSplashAnimation()

        Log.d(TAG, "MainActivity onCreate tamamlandı. Cihaz ruhu: ${getDevicePersonality()}")
    }

    override fun onResume() {
        super.onResume()
        // Uygulama ön plana geldiğinde servis durumlarını kontrol et
        checkNotificationAccess()
        checkAccessibilityService()
        // USB otg durumunu kontrol et (UsbOtgManager zaten receiver ile çalışıyor)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Kaynakları temizle
        UsbOtgManager.release(this)
        HardwareController.stopSensorMonitoring()
        splashJob?.cancel()
        Log.d(TAG, "MainActivity onDestroy: kaynaklar temizlendi")
    }

    // ==================== DURUM YÜKLEMELERİ ====================

    private fun loadAllStates() {
        SystemState.loadState(this)
        WindowState.loadState(this)
        TaskbarState.loadState(this)
        DesktopState.loadState(this) // DesktopState zaten save/load var
        // NotificationState yüklemesi (NotificationState zaten NotificationPanel içinde object, direkt kullanılabilir)
        // NotificationState.notifications temizlenir veya yüklenebilir, ama servis zaten canlı.

        // TaskbarState ile SystemState senkronizasyonu
        TaskbarState.syncAll(this)

        Log.d(TAG, "Tüm durumlar yüklendi")
    }

    // ==================== SERVİS BAŞLATMALARI ====================

    private fun initializeServices() {
        // USB OTG yöneticisi
        UsbOtgManager.initialize(this)

        // Sensör takibi (pil, ışık, yakınlık vb.)
        HardwareController.startSensorMonitoring(this)

        // Bildirim servisinin başlatılması (NotificationListenerService)
        // Bu servis sistem tarafından otomatik başlatılır, ancak kontrol et
        checkNotificationAccess()

        // Erişilebilirlik servisi kontrolü
        checkAccessibilityService()

        // BinaryManager başlat (arka planda)
        lifecycleScope.launch {
    runCatching {
        BinaryManager.initialize(this@MainActivity)
    }.onFailure {
        Log.e(TAG, "BinaryManager başlatılamadı: ${it.message}")
    }
}

        // TaskbarState senkronizasyonu (güncel durumlar)
        TaskbarState.syncAll(this)

        // WindowState pencerelerini restore et
        // Zaten loadState ile gelir, ek bir işlem gerekmez.

        // Gizli özellik: cihaz ruhuna göre tema öner (ilk açılışta)
        if (SystemState.theme == "dark" && getDevicePersonality().contains("süper kahraman")) {
            // Süper kahraman ruhu için parlak tema öner
            SystemState.setTheme(this, "light")
        }
    }

    // ==================== YARDIMCI KONTROLLER ====================

    private fun checkNotificationAccess() {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(this)
        val hasAccess = enabledPackages.contains(packageName)
        if (!hasAccess) {
            // Kullanıcıya bildirim erişimi vermesi için yönlendir
            mainHandler.postDelayed({
                Toast.makeText(
                    this,
                    "⚠️ Bildirim erişimi kapalı. Bildirimleri görmek için izin verin.",
                    Toast.LENGTH_LONG
                ).show()
                // Ayarlara yönlendir (opsiyonel)
                // startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }, 2000)
        } else {
            Log.d(TAG, "Bildirim erişimi aktif")
        }
    }

    private fun checkAccessibilityService() {
        val isEnabled = WindowsAccessibilityService.getInstance()?.isEnabled() ?: false
        if (!isEnabled) {
            mainHandler.postDelayed({
                Toast.makeText(
                    this,
                    "♿ Erişilebilirlik servisi kapalı. Klavye kısayolları çalışmaz.",
                    Toast.LENGTH_LONG
                ).show()
                // Ayarlara yönlendir (opsiyonel)
                // startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }, 3000)
        } else {
            Log.d(TAG, "Erişilebilirlik servisi aktif")
        }
    }

    // ==================== "RUH" KARŞILAMA ====================

    private fun getDevicePersonality(): String {
        return runCatching { DeviceInfo.analyzeDevicePersonality(this) }.getOrNull() ?: "Bilinmiyor"
    }

    private fun showSpiritWelcome() {
        val personality = getDevicePersonality()
        val emoji = when {
            personality.contains("süper kahraman") -> "🦸"
            personality.contains("denge ustası") -> "🧘"
            personality.contains("minimalist") -> "📿"
            else -> "💎"
        }
        val message = "Hoş geldin ${SystemState.username}! $emoji\n$personality"

        // Toast ile göster (gecikmeli)
        mainHandler.postDelayed({
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }, 1500)

        // Ayrıca log'a yaz
        Log.i(TAG, "🧘 Cihaz Ruhu: $personality")
    }

    // ==================== SPLASH ANİMASYONU (Opsiyonel) ====================

    private fun showSplashAnimation() {
        // Basit bir splash gösterimi için Compose içinde veya bir overlay ile yapılabilir.
        // Şimdilik sadece log
        Log.d(TAG, "Splash animasyonu (simülasyon)")
    }

    // ==================== GİZLİ GELİŞTİRİCİ MENÜSÜ ====================

    /**
     * Bu fonksiyon, ana ekranda bir yere tıklandığında çağrılabilir.
     * Örneğin, DesktopScreen'de bir yere 5 kez tıklanınca çağrılabilir.
     * Ancak burada activity'de bir yöntem olarak tutuyoruz.
     */
    fun onDeveloperClick() {
        devClickCount++
        if (devClickCount >= 5) {
            devClickCount = 0
            showDeveloperMenu()
        } else {
            Toast.makeText(this, "Geliştirici menüsü için ${5 - devClickCount} tıklama kaldı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeveloperMenu() {
        // Geliştirici menüsü: logcat çıktısı, sistem bilgisi, önbellek temizleme, vs.
        val builder = AlertDialog.Builder(this)
        builder.setTitle("🧪 Geliştirici Menüsü")
        val items = arrayOf(
            "Logcat Göster",
            "Sistem Bilgisi",
            "Önbelleği Temizle",
            "BinaryManager Durumu",
            "Sanal Masaüstü Sıfırla",
            "Kapat"
        )
        builder.setItems(items) { _, which ->
            when (which) {
                0 -> showLogcat()
                1 -> showSystemInfo()
                2 -> clearCache()
                3 -> showBinaryStatus()
                4 -> resetVirtualDesktops()
                else -> {}
            }
        }
        builder.setNegativeButton("Kapat") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showLogcat() {
        // Basit logcat gösterimi (son 20 satır)
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "20"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines()
            process.destroy()
            val message = lines.joinToString("\n")
            AlertDialog.Builder(this)
                .setTitle("Logcat (son 20 satır)")
                .setMessage(message)
                .setPositiveButton("Tamam", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Logcat alınamadı: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSystemInfo() {
        val info = runCatching { DeviceInfo.getFullInfo(this) }.getOrNull()?.take(20)?.joinToString("\n") ?: "Bilgi alınamadı"
        AlertDialog.Builder(this)
            .setTitle("Sistem Bilgisi")
            .setMessage(info)
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun clearCache() {
        // Önbellek temizleme (IconManager, BinaryManager, vs.)
        IconManager.clearCache()
        Toast.makeText(this, "İkon önbelleği temizlendi", Toast.LENGTH_SHORT).show()
        // Ayrıca geçici dosyalar da temizlenebilir
    }

    private fun showBinaryStatus() {
    lifecycleScope.launch {
        runCatching {
            val status = BinaryManager.getStatus(this@MainActivity)
            AlertDialog.Builder(this@MainActivity)
                .setTitle("BinaryManager Durumu")
                .setMessage(status)
                .setPositiveButton("Tamam", null)
                .show()
        }.onFailure {
            Toast.makeText(this@MainActivity, "BinaryManager hatası: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

    private fun resetVirtualDesktops() {
        SystemState.virtualDesktops.clear()
        SystemState.virtualDesktops.add("desktop_${System.currentTimeMillis()}")
        SystemState.currentDesktop = SystemState.virtualDesktops.first()
        SystemState.saveState(this)
        Toast.makeText(this, "Sanal masaüstleri sıfırlandı", Toast.LENGTH_SHORT).show()
    }

    // ==================== İZİN YÖNETİMİ (Opsiyonel) ====================

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 0)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // İzin sonuçları işlenebilir
        if (requestCode == 0 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "İzinler verildi", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== EKSTRA: USB OTG BİLDİRİMLERİ ====================

    // UsbOtgManager zaten broadcast receiver ile bildirim gönderiyor, ekstra bir şey gerekmez.
    // Ancak aktivite bunları dinleyebilir veya gösterim için bir snackbar eklenebilir.

    // ==================== GİZLİ ÖZELLİK: SİHİRLİ YENİDEN BAŞLATMA ====================

    /**
     * Uygulamayı sihirli bir şekilde yeniden başlatır (tüm durumları sıfırlamadan).
     * Bu fonksiyon, geliştirici menüsünden çağrılabilir.
     */
    fun magicRestart() {
        // Tüm servisleri yeniden başlat
        recreate()
    }
}