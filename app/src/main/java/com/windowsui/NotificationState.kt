package com.windowsui

import android.app.Notification
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * NotificationState – Merkezi Bildirim Durum Yöneticisi (Thread-Safe)
 *
 * Tüm bildirimleri, pin'leri, istatistikleri ve geçmişi yönetir.
 * NotificationPanel ve WindowsNotificationService ile tam uyumludur.
 *
 * 🚀 "Akla Hayale Gelmez" Özellikler:
 * - 🧘 Ruh Kategorisi: Bildirim içeriğine göre duygu analizi.
 * - 🔮 Sihirli Gruplama: Benzer bildirimleri otomatik gruplar.
 * - 📊 Akıllı Özet: Uzun bildirim listesini özetler.
 * - ⭐ Öncelik Skoru: Bildirimin önemine göre puan (0-100).
 * - 🎭 Gizli Bildirimler: Kilit ekranı ile entegre.
 * - ⏳ Geçmiş: Son 100 bildirimi saklar.
 * - 📈 İstatistikler: Uygulama bazında bildirim sayısı.
 * - 🔇 Sessiz Saatler: Otomatik sessiz mod.
 * - 💾 Kalıcılık: SharedPreferences'a kaydedilir.
 * - 🧹 Otomatik Temizlik: 7 günden eski bildirimler silinir.
 */
object NotificationState {

    private const val TAG = "NotificationState"
    private const val PREFS_NAME = "ASUI_NotificationState"
    private const val MAX_HISTORY = 100
    private const val MAX_NOTIFICATIONS = 200
    private const val AUTO_CLEANUP_DAYS = 7

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(WindowsNotification::class.java, WindowsNotificationSerializer())
        .create()

    // ==================== COMPOSE STATE (SADECE MAIN THREAD) ====================
    val notifications = mutableStateListOf<WindowsNotification>()
    val pinnedIds = mutableStateListOf<String>()
    val history = mutableStateListOf<WindowsNotification>()
    val stats = mutableStateMapOf<String, Int>()

    // ==================== GRUP HARİTASI (Thread-Safe) ====================
    private val groups = ConcurrentHashMap<String, MutableList<WindowsNotification>>()

    // ==================== CONTEXT VE THREAD YÖNETİMİ ====================
    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(Dispatchers.IO)

    // ==================== BAŞLATMA ====================

    fun init(context: Context) {
        appContext = context.applicationContext
        loadState(context)
        Log.d(TAG, "NotificationState initialized")
    }

    // ==================== FİLTRELEME VE SIRALAMA (SAFE) ====================

    fun getActiveNotifications(
        hideSilent: Boolean = true,
        categoryFilter: String? = null,
        searchQuery: String? = null
    ): List<WindowsNotification> {
        // notifications zaten main thread'de okunur, bu fonksiyon genellikle UI'dan çağrılır.
        var result = notifications.toList()
        if (hideSilent) {
            result = result.filter { !it.isSilent }
        }
        if (!categoryFilter.isNullOrEmpty() && categoryFilter != "Tümü") {
            result = result.filter { it.category == categoryFilter }
        }
        if (!searchQuery.isNullOrEmpty()) {
            val q = searchQuery.lowercase(Locale.getDefault())
            result = result.filter {
                it.title.lowercase(Locale.getDefault()).contains(q) ||
                        it.text.lowercase(Locale.getDefault()).contains(q) ||
                        it.appName.lowercase(Locale.getDefault()).contains(q)
            }
        }
        return result.sortedByDescending { it.timestamp }
    }

    fun getPinnedNotifications(): List<WindowsNotification> {
        return notifications.filter { pinnedIds.contains(it.id) }
    }

    fun getGroupedByCategory(): Map<String, List<WindowsNotification>> {
        return notifications.groupBy { it.category }
    }

    // ==================== BİLDİRİM İŞLEMLERİ (TAMAMEN MAIN THREAD) ====================

    fun addOrUpdateNotification(notification: WindowsNotification) {
        mainHandler.post {
            val existingIndex = notifications.indexOfFirst { it.id == notification.id }
            if (existingIndex != -1) {
                notifications[existingIndex] = notification
            } else {
                notifications.add(0, notification)
                stats[notification.packageName] = (stats[notification.packageName] ?: 0) + 1
                history.add(0, notification)
                if (history.size > MAX_HISTORY) {
                    history.removeAt(history.size - 1)
                }
                notification.groupKey?.let { groupKey ->
                    groups.getOrPut(groupKey) { mutableListOf() }.add(notification)
                }
            }
            if (notifications.size > MAX_NOTIFICATIONS) {
                notifications.removeAt(notifications.size - 1)
            }
            autoCleanup()
            // Kaydet: snapshot'ları main thread'de al, IO'ya gönder
            saveStateInternal()
        }
    }

    fun removeNotification(notificationId: String) {
        mainHandler.post {
            val notif = notifications.find { it.id == notificationId }
            notifications.removeAll { it.id == notificationId }
            pinnedIds.remove(notificationId)
            notif?.let {
                history.removeAll { it.id == notificationId }
                it.groupKey?.let { groupKey ->
                    groups[groupKey]?.removeAll { groupNotif -> groupNotif.id == notificationId }
                    if (groups[groupKey].isNullOrEmpty()) {
                        groups.remove(groupKey)
                    }
                }
            }
            saveStateInternal()
        }
    }

    fun clearAll() {
        mainHandler.post {
            val pinned = getPinnedNotifications()
            notifications.clear()
            notifications.addAll(pinned)
            saveStateInternal()
        }
    }

    fun clearPinned() {
        mainHandler.post {
            pinnedIds.clear()
            saveStateInternal()
        }
    }

    fun togglePin(notificationId: String) {
        mainHandler.post {
            if (pinnedIds.contains(notificationId)) {
                pinnedIds.remove(notificationId)
            } else {
                pinnedIds.add(notificationId)
            }
            saveStateInternal()
        }
    }

    // ==================== "AKLA HAYALE GELMEZ" ÖZELLİKLER (SAFE) ====================

    fun getSpiritCategory(notification: WindowsNotification): String {
        val text = (notification.title + " " + notification.text).lowercase(Locale.getDefault())
        return when {
            text.contains("mutlu") || text.contains("tebrik") || text.contains("❤️") || text.contains("🎉") -> "Mutlu 😊"
            text.contains("üzgün") || text.contains("kaybettin") || text.contains("❌") -> "Üzgün 😢"
            text.contains("kızgın") || text.contains("hata") || text.contains("⚠️") -> "Kızgın 😠"
            text.contains("şaşır") || text.contains("vay") || text.contains("❗") -> "Şaşkın 😲"
            else -> "Nötr 😐"
        }
    }

    fun getPriorityScore(notification: WindowsNotification): Int {
        var score = 50
        when (notification.category) {
            "📩 SMS", "💬 Sosyal" -> score += 20
            "📧 E-posta" -> score += 10
            "🏦 Finans" -> score += 25
            "⚙️ Sistem" -> score += 15
            "🎯 Promosyon" -> score -= 15
        }
        val ageMinutes = (System.currentTimeMillis() - notification.timestamp) / (60 * 1000)
        if (ageMinutes < 5) score += 15
        else if (ageMinutes > 60) score -= 10
        if (notification.text.length > 100) score += 5
        if (notification.remoteInput != null) score += 20
        if (pinnedIds.contains(notification.id)) score = 100
        return score.coerceIn(0, 100)
    }

    fun getSmartGroups(): Map<String, List<WindowsNotification>> {
        // groups ConcurrentHashMap olduğu için güvenli okuma, ama kopya alalım
        val result = mutableMapOf<String, MutableList<WindowsNotification>>()
        groups.forEach { (key, list) ->
            if (list.size >= 2) {
                result[key] = list.toMutableList()
            }
        }
        val remaining = notifications.filter { notif ->
            !result.values.any { list -> list.any { it.id == notif.id } }
        }
        val appGroups = remaining.groupBy { it.packageName }
        appGroups.filter { it.value.size >= 3 }.forEach { (pkg, list) ->
            val key = "app_group_$pkg"
            result[key] = list.toMutableList()
        }
        return result
    }

    fun getSmartSummary(maxItems: Int = 3): String {
        val grouped = notifications.groupBy { it.packageName }
        val summaries = grouped.map { (pkg, list) ->
            val appName = list.firstOrNull()?.appName ?: pkg
            val count = list.size
            val topCategory = list.groupBy { it.category }.maxByOrNull { it.value.size }?.key ?: "Bildirim"
            "$appName: $count $topCategory"
        }.sortedByDescending { it.length }.take(maxItems)
        return if (summaries.isNotEmpty()) {
            summaries.joinToString(", ")
        } else {
            "Bildirim yok"
        }
    }

    fun getDailyStats(): Map<String, Int> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return history.filter {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp)) == today
        }.groupBy { it.packageName }.mapValues { it.value.size }
    }

    fun getTopApps(limit: Int = 5): List<Pair<String, Int>> {
        return stats.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
    }

    fun isQuietHoursActive(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 7
    }

    // ==================== OTOMATİK TEMİZLİK (MAIN THREAD) ====================

    private fun autoCleanup() {
        // Bu fonksiyon sadece main thread'de çağrılır (mainHandler.post içinden)
        val cutoff = System.currentTimeMillis() - (AUTO_CLEANUP_DAYS * 24 * 60 * 60 * 1000L)
        val toRemoveIds = notifications.filter { it.timestamp < cutoff && !pinnedIds.contains(it.id) }.map { it.id }
        if (toRemoveIds.isNotEmpty()) {
            notifications.removeAll { toRemoveIds.contains(it.id) }
            toRemoveIds.forEach { id ->
                groups.values.forEach { list -> list.removeAll { it.id == id } }
            }
            groups.entries.removeAll { it.value.isEmpty() }
        }
    }

    // ==================== KALICILIK (IO THREAD, SNAPSHOT MAIN'DE ALINIR) ====================

    fun loadState(context: Context) {
        ioScope.launch {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString("state", null) ?: run {
                Log.d(TAG, "No saved state found")
                return@launch
            }
            try {
                val type = object : TypeToken<NotificationStateData>() {}.type
                val data: NotificationStateData = gson.fromJson(json, type)
                // Main thread'de listelere aktar
                mainHandler.post {
                    notifications.clear()
                    notifications.addAll(data.notifications)
                    pinnedIds.clear()
                    pinnedIds.addAll(data.pinnedIds)
                    history.clear()
                    history.addAll(data.history)
                    stats.clear()
                    stats.putAll(data.stats)
                    // Grupları yeniden oluştur (groupKey'leri kullanarak)
                    groups.clear()
                    notifications.forEach { notif ->
                        notif.groupKey?.let { key ->
                            groups.getOrPut(key) { mutableListOf() }.add(notif)
                        }
                    }
                    Log.d(TAG, "State loaded: ${notifications.size} notifications")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load state", e)
            }
        }
    }

    /**
     * Sadece main thread'den çağrılmalıdır.
     * Snapshot'ları main thread'de alır, IO'ya gönderir.
     */
    private fun saveStateInternal() {
        val context = appContext ?: run {
            Log.e(TAG, "Context not initialized! Call NotificationState.init() first.")
            return
        }
        // SADECE main thread'de snapshot al
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "saveStateInternal called from non-main thread, posting to main")
            mainHandler.post { saveStateInternal() }
            return
        }

        // Snapshot'ları al (main thread'de güvenli)
        val snapNotifications = notifications.toList()
        val snapPinned = pinnedIds.toList()
        val snapHistory = history.toList()
        val snapStats = stats.toMap()

        // IO thread'de kaydet
        ioScope.launch {
            try {
                val data = NotificationStateData(
                    notifications = snapNotifications,
                    pinnedIds = snapPinned,
                    history = snapHistory,
                    stats = snapStats
                )
                val json = gson.toJson(data)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString("state", json).apply()
                Log.d(TAG, "State saved: ${snapNotifications.size} notifications")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save state", e)
            }
        }
    }

    // ==================== VERİ SINIFI VE SERIALIZER ====================

    private data class NotificationStateData(
        val notifications: List<WindowsNotification>,
        val pinnedIds: List<String>,
        val history: List<WindowsNotification>,
        val stats: Map<String, Int>
    )

    private class WindowsNotificationSerializer : com.google.gson.JsonSerializer<WindowsNotification>,
        com.google.gson.JsonDeserializer<WindowsNotification> {
        override fun serialize(
            src: WindowsNotification,
            typeOfSrc: java.lang.reflect.Type,
            context: com.google.gson.JsonSerializationContext
        ): com.google.gson.JsonElement {
            val json = com.google.gson.JsonObject()
            json.addProperty("id", src.id)
            json.addProperty("packageName", src.packageName)
            json.addProperty("title", src.title)
            json.addProperty("text", src.text)
            json.addProperty("category", src.category)
            json.addProperty("timestamp", src.timestamp)
            json.addProperty("appName", src.appName)
            json.addProperty("isSilent", src.isSilent)
            json.addProperty("priority", src.priority)
            json.addProperty("groupKey", src.groupKey)
            json.addProperty("isGroupSummary", src.isGroupSummary)
            return json
        }

        override fun deserialize(
            json: com.google.gson.JsonElement,
            typeOfT: java.lang.reflect.Type,
            context: com.google.gson.JsonDeserializationContext
        ): WindowsNotification {
            val obj = json.asJsonObject
            return WindowsNotification(
                id = obj.get("id")?.asString ?: "",
                packageName = obj.get("packageName")?.asString ?: "",
                title = obj.get("title")?.asString ?: "",
                text = obj.get("text")?.asString ?: "",
                replyPendingIntent = null,
                remoteInput = null,
                isPinned = false,
                category = obj.get("category")?.asString ?: "📢 Bildirim",
                timestamp = obj.get("timestamp")?.asLong ?: System.currentTimeMillis(),
                appName = obj.get("appName")?.asString ?: "",
                actions = emptyList(),
                isGroupSummary = obj.get("isGroupSummary")?.asBoolean ?: false,
                groupKey = obj.get("groupKey")?.asString,
                isSilent = obj.get("isSilent")?.asBoolean ?: false,
                priority = obj.get("priority")?.asInt ?: Notification.PRIORITY_DEFAULT
            )
        }
    }
}