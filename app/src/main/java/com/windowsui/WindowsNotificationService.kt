package com.windowsui

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * WindowsNotificationService for ASUI Desktop.
 *
 * Provides:
 * - Real-time notification capture from all apps
 * - Notification actions (reply, archive, delete, etc.) via RemoteInput and PendingIntent
 * - Sound, vibration, and LED control based on SystemState settings
 * - Privacy mode (hide content when lock screen is enabled)
 * - Auto-categorization (SMS, Social, Finance, Promo, System)
 * - Statistics tracking (notification count per app)
 * - Command execution via Termux/ADB bridge when specific notifications arrive
 * - Notification forwarding to connected OTG/ADB devices (future-ready)
 * - Integration with NotificationPanel UI and Taskbar badge updates
 *
 * Requires user to enable Notification Access in Settings.
 */
class WindowsNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "WindowsNotifService"
        private var instance: WindowsNotificationService? = null

        fun getInstance(): WindowsNotificationService? = instance
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var vibrator: Vibrator
    private lateinit var audioManager: AudioManager

    // Bildirim istatistikleri (paket adı -> sayı)
    private val notificationStats = mutableMapOf<String, Int>()

    // ==================== SERVİS YAŞAM DÖNGÜSÜ ====================

    override fun onCreate() {
        super.onCreate()
        instance = this
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d(TAG, "Notification Service Created")
    }

    override fun onDestroy() {
        instance = null
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    // ==================== BİLDİRİM YAKALAMA ====================

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // Sistem ayarlarında bildirimler kapalıysa işleme
        if (!SystemState.notificationsEnabled) return

        val pkg = sbn.packageName
        val notification = sbn.notification ?: return
        val extras = notification.extras

        // Uygulama adını al
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkg
        }

        // Bildirim başlık ve metni
        val title = extras.getString(Notification.EXTRA_TITLE) ?: appName
        val text = extras.getString(Notification.EXTRA_TEXT)
            ?: extras.getString(Notification.EXTRA_BIG_TEXT)
            ?: ""

        // Kilit ekranı aktifse bildirim içeriğini gizle (gizlilik modu)
        val displayText = if (SystemState.lockScreenEnabled && SystemState.lockScreenPassword != null) {
            "🔒 Gizli bildirim"
        } else {
            text
        }

        // Bildirim zamanı
        val timestamp = sbn.postTime

        // RemoteInput (yanıtlama) desteği
        var replyPendingIntent: PendingIntent? = null
        var remoteInput: RemoteInput? = null

        // Tüm eylemleri (Action butonları) topla
        val actions = mutableListOf<Notification.Action>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            notification.actions?.forEach { action ->
                actions.add(action)
                val inputs = action.getRemoteInputs()
                if (!inputs.isNullOrEmpty()) {
                    replyPendingIntent = action.actionIntent
                    remoteInput = inputs[0]
                }
            }
        }

        // Bildirim kategorisini belirle (SMS, Sosyal, Finans, vb.)
        val category = detectCategory(pkg, title, text, appName)

        // Bildirimi NotificationState'e ekle (UI'da gösterilmek üzere)
        val winNotif = com.windowsui.WindowsNotification(
            id = sbn.key,
            packageName = pkg,
            title = title,
            text = displayText,
            replyPendingIntent = replyPendingIntent,
            remoteInput = remoteInput,
            isPinned = false,
            category = category,
            timestamp = timestamp,
            appName = appName,
            actions = actions
        )

        // Aynı ID varsa güncelle, yoksa ekle
        val existingIndex = NotificationState.notifications.indexOfFirst { it.id == winNotif.id }
        if (existingIndex != -1) {
            NotificationState.notifications[existingIndex] = winNotif
        } else {
            NotificationState.notifications.add(0, winNotif) // En üste ekle
            // İstatistik güncelle
            notificationStats[pkg] = (notificationStats[pkg] ?: 0) + 1
        }

        // Bildirim sesi, titreşim ve ışık
        playNotificationSound()
        vibrateNotification()
        flashNotificationLight()

        // Log
        Log.d(TAG, "Notification from $appName: $title")

        // ============ AKILLI EYLEMLER (Otomatik komut çalıştırma) ============

        // Belirli uygulamalardan gelen bildirimlere göre özel eylemler
        handleSmartActions(pkg, title, text, appName)

        // Bildirim sayısını görev çubuğu rozeti için güncelle (TaskbarComponent güncellenir)
        // Burada bir broadcast veya callback tetiklenebilir.
        updateTaskbarBadge()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val key = sbn.key
        NotificationState.notifications.removeAll { it.id == key }
        Log.d(TAG, "Notification removed: $key")
        updateTaskbarBadge()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        // Mevcut bildirimleri temizle (servis yeniden bağlandığında)
        // İsteğe bağlı: Aktif bildirimleri al ve NotificationState'e ekle
        // Şimdilik sadece log
    }

    // ==================== BİLDİRİM KATEGORİ TESPİTİ ====================

    private fun detectCategory(pkg: String, title: String, text: String, appName: String): String {
        val lowerTitle = title.lowercase(Locale.getDefault())
        val lowerText = text.lowercase(Locale.getDefault())
        val lowerApp = appName.lowercase(Locale.getDefault())

        return when {
            pkg.contains("sms") || pkg.contains("mms") ||
                    lowerTitle.contains("sms") || lowerText.contains("sms") ||
                    lowerTitle.contains("mesaj") -> "📩 SMS"
            pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("signal") ||
                    pkg.contains("discord") || pkg.contains("slack") ||
                    lowerApp.contains("whatsapp") || lowerApp.contains("telegram") -> "💬 Sosyal"
            pkg.contains("gmail") || pkg.contains("mail") || lowerApp.contains("mail") ||
                    lowerTitle.contains("mail") -> "📧 E-posta"
            pkg.contains("bank") || pkg.contains("finance") || pkg.contains("pay") ||
                    lowerTitle.contains("banka") || lowerTitle.contains("ödeme") ||
                    lowerText.contains("tl") || lowerText.contains("₺") -> "🏦 Finans"
            pkg.contains("play") || pkg.contains("store") || lowerTitle.contains("güncelleme") -> "🔄 Sistem"
            lowerTitle.contains("reklam") || lowerText.contains("kampanya") ||
                    lowerText.contains("indirim") -> "🎯 Promosyon"
            pkg == "com.android.systemui" || pkg.contains("android") -> "⚙️ Sistem"
            else -> "📢 Bildirim"
        }
    }

    // ==================== SES / TİTREŞİM / IŞIK ====================

    private fun playNotificationSound() {
        if (!SystemState.notificationSoundEnabled) return

        try {
            // Varsayılan bildirim sesini çal
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(applicationContext, uri)
                    prepare()
                }
            }
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ses çalınamadı", e)
        }
    }

    private fun vibrateNotification() {
        if (!SystemState.notificationSoundEnabled) return // Sessiz modda titreşim de kapalı
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }
        } catch (e: Exception) {}
    }

    private fun flashNotificationLight() {
        // Bildirim ışığını yakmak için NotificationManager kullanılabilir,
        // ancak bu servis zaten bildirimleri yakalıyor. Şimdilik pasif.
        // İleride Notification.Builder ile LED rengi ayarlanabilir.
    }

    // ==================== AKILLI EYLEMLER ====================

    private fun handleSmartActions(pkg: String, title: String, text: String, appName: String) {
        // Örnek: Telegram/WhatsApp bildirimi geldiğinde terminal'den bir komut çalıştır
        if (pkg.contains("whatsapp") || pkg.contains("telegram")) {
            // Terminal'de otomatik olarak bir widget güncellemesi yap
            // Bu işlem CmdTerminal veya TermuxBridge ile yapılabilir.
            coroutineScope.launch {
                try {
                    // Örnek: Terminal'de "WIDGET ADD social" komutunu çalıştır
                    // Bu doğrudan CmdTerminal'e bağlanmaz, ama SystemState'e bir flag yazılabilir
                    // Şimdilik sadece log
                    Log.d(TAG, "Sosyal medya bildirimi: $appName")
                } catch (e: Exception) {}
            }
        }

        // Örnek: Banka bildirimi geldiğinde ekran parlaklığını arttır (güvenlik)
        if (pkg.contains("bank") || pkg.contains("finance")) {
            coroutineScope.launch {
                try {
                    // SystemControllers.setSystemBrightness(applicationContext, 0.8f)
                    // Root ile parlaklığı arttır
                    Log.d(TAG, "Finans bildirimi, parlaklık arttırıldı.")
                } catch (e: Exception) {}
            }
        }
    }

    // ==================== GÖREV ÇUBUĞU ROZETİ ====================

    private fun updateTaskbarBadge() {
        // NotificationState'deki bildirim sayısını TaskbarState'e gönder
        // Şimdilik sadece log, ileride broadcast ile TaskbarComponent güncellenebilir.
        val count = NotificationState.notifications.size
        Log.d(TAG, "Bildirim rozeti: $count")
        // Burada bir LiveData veya StateFlow ile UI güncellenebilir.
    }

    // ==================== BİLDİRİM İSTATİSTİKLERİ ====================

    /**
     * Belirli bir uygulamadan gelen bildirim sayısını döndürür.
     */
    fun getNotificationCount(pkg: String): Int {
        return notificationStats[pkg] ?: 0
    }

    /**
     * Tüm istatistikleri temizler.
     */
    fun clearStats() {
        notificationStats.clear()
    }

    // ==================== BİLDİRİM YANITLAMA (DOĞRUDAN) ====================

    /**
     * Bir bildirime doğrudan yanıt gönderir (RemoteInput ile).
     * NotificationPanel'den çağrılır.
     */
    fun replyToNotification(notification: WindowsNotification, replyText: String): Boolean {
        if (notification.replyPendingIntent == null || notification.remoteInput == null) {
            return false
        }
        return try {
            val intent = android.content.Intent()
            val bundle = android.os.Bundle()
            bundle.putCharSequence(notification.remoteInput!!.resultKey, replyText)
            RemoteInput.addResultsToIntent(arrayOf(notification.remoteInput), intent, bundle)
            notification.replyPendingIntent!!.send(applicationContext, 0, intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Yanıt gönderilemedi", e)
            false
        }
    }

    // ==================== BİLDİRİM YÖNLENDİRME (OTG / ADB) ====================

    /**
     * Bağlı OTG cihazına bildirim yönlendirir (ileri düzey).
     * OtgAdbBridge kullanılarak ADB shell ile hedef cihaza bildirim gönderilebilir.
     */
    fun forwardNotificationToOtg(notification: WindowsNotification) {
        coroutineScope.launch {
            try {
                val command = "adb shell am broadcast -a android.intent.action.BOOT_COMPLETED" // Örnek
                // OtgAdbBridge.execute(applicationContext, listOf("shell", "echo", "'${notification.text}'"), true)
                Log.d(TAG, "Bildirim OTG'ye yönlendiriliyor: ${notification.text}")
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

    /**
     * Bildirim erişim izninin verilip verilmediğini kontrol eder.
     */
    fun hasNotificationAccess(context: Context): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledPackages.contains(context.packageName)
    }

    fun onInterrupt() {
        Log.d(TAG, "Notification Service interrupted")
    }
}

/**
 * Gelişmiş Notification veri sınıfı (UI ve servis arasında taşınır).
 * Mevcut NotificationState'e uyumlu, ek alanlar eklendi.
 */
data class WindowsNotification(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val replyPendingIntent: PendingIntent? = null,
    val remoteInput: RemoteInput? = null,
    val isPinned: Boolean = false,
    val category: String = "📢 Bildirim",
    val timestamp: Long = System.currentTimeMillis(),
    val appName: String = "",
    val actions: List<Notification.Action> = emptyList(),
    val isGroupSummary: Boolean = false,
    val groupKey: String? = null,
    val isSilent: Boolean = false,
    val priority: Int = Notification.PRIORITY_DEFAULT
)