package com.windowsui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Masaüstünde görünen uygulama modeli.
 * Tüm UI bileşenleri bu veri sınıfını kullanır.
 *
 * @param label Uygulamanın görünen adı
 * @param packageName Android paket adı
 * @param iconBitmap Uygulama simgesi (Compose ImageBitmap)
 * @param isSystem Sistem uygulaması mı?
 * @param isEnabled Uygulama etkin mi?
 * @param isLaunchable Başlatılabilir mi (launcher intent'i var mı)?
 * @param category Kategori (Oyun, Sosyal, Üretkenlik, vb.)
 * @param installTime Yükleme zamanı (timestamp)
 * @param updateTime Son güncelleme zamanı (timestamp)
 * @param apkSize APK dosya boyutu (bytes)
 * @param isHidden Kullanıcı tarafından gizlenmiş mi?
 * @param isPinned Görev çubuğuna sabitlenmiş mi?
 */
data class DesktopApp(
    val label: String,
    val packageName: String,
    val iconBitmap: ImageBitmap,
    val isSystem: Boolean = false,
    val isEnabled: Boolean = true,
    val isLaunchable: Boolean = true,
    val category: String = "Diğer",
    val installTime: Long = 0,
    val updateTime: Long = 0,
    val apkSize: Long = 0,
    val isHidden: Boolean = false,
    val isPinned: Boolean = false
) {
    /**
     * Yükleme tarihini insan okunabilir formatta döndürür.
     */
    val installDate: String
        get() = if (installTime > 0) {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(installTime))
        } else "Bilinmiyor"

    /**
     * Güncelleme tarihini insan okunabilir formatta döndürür.
     */
    val updateDate: String
        get() = if (updateTime > 0) {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(updateTime))
        } else "Bilinmiyor"

    /**
     * APK boyutunu insan okunabilir formatta döndürür.
     */
    val apkSizeFormatted: String
        get() = formatSize(apkSize)

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    /**
     * Kategori rengi (UI'da renkli etiket için).
     */

    /**
     * Kategori emojisi (UI'da gösterim için).
     */
    val categoryEmoji: String
        get() = when (category) {
            "Oyun" -> "🎮"
            "Sosyal" -> "💬"
            "Üretkenlik" -> "📊"
            "Müzik" -> "🎵"
            "Fotoğraf" -> "📷"
            "Haber" -> "📰"
            "Alışveriş" -> "🛒"
            "Finans" -> "💰"
            "Sağlık" -> "❤️"
            "Eğitim" -> "📚"
            "Araçlar" -> "🔧"
            else -> "📱"
        }
}

/**
 * Uygulama yöneticisi – masaüstünde gösterilecek tüm uygulamaları toplar,
 * filtreler, sıralar ve önbelleğe alır.
 *
 * Özellikler:
 * - Tüm launcher uygulamalarını listeler
 * - Sistem uygulamalarını filtreleme (opsiyonel)
 * - Android 8.0+ kategori desteği (ApplicationInfo.category)
 * - Uygulama kategorisi tahmini (eski Android'ler için)
 * - Yükleme ve güncelleme tarihleri
 * - APK boyutu
 * - İkon önbelleği
 * - Sıralama ve filtreleme yardımcıları
 * - AppInfo ile uyumlu dönüşüm
 */
object DesktopManager {

    private const val TAG = "DesktopManager"

    /**
     * Tüm başlatılabilir uygulamaları asenkron olarak yükler.
     * Sonuçları `SystemState.allInstalledAppsCache`'e kaydeder.
     *
     * @param context Context
     * @param includeSystem Sistem uygulamalarını da dahil et (varsayılan true)
     * @param forceRefresh Önbelleği yenile (varsayılan false)
     */
    suspend fun loadApps(
        context: Context,
        includeSystem: Boolean = true,
        forceRefresh: Boolean = false
    ): List<DesktopApp> = withContext(Dispatchers.IO) {
        // Önbellek dolu ve yenileme istenmiyorsa direkt önbellekten dön
        if (!forceRefresh && SystemState.isAppsLoaded && SystemState.allInstalledAppsCache.isNotEmpty()) {
            return@withContext SystemState.allInstalledAppsCache.toList()
        }

        val pm = context.packageManager
        val apps = mutableListOf<DesktopApp>()

        try {
            // Launcher intent'i olan tüm uygulamaları al
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved = pm.queryIntentActivities(intent, 0)

            resolved.forEach { resolveInfo ->
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val packageName = appInfo.packageName

                // Sistem uygulamalarını filtrele
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystem && isSystem) return@forEach

                // Uygulama etkin mi?
                val isEnabled = try {
                    pm.getApplicationInfo(packageName, 0).enabled
                } catch (e: Exception) {
                    true
                }

                // İkonu al (önce yüksek kalitede, sonra ölçekle)
                val iconDrawable = try {
                    resolveInfo.loadIcon(pm)
                } catch (e: Exception) {
                    pm.defaultActivityIcon
                }
                // 96x96 boyutunda ikon
                val iconBitmap = iconDrawable.toBitmap(96, 96).asImageBitmap()

                // Kategori tespiti (Android 8.0+ öncelikli)
                val label = resolveInfo.loadLabel(pm).toString()
                val category = getAppCategory(context, appInfo, packageName, label)

                // Yükleme ve güncelleme zamanları
                var installTime = 0L
                var updateTime = 0L
                var apkSize = 0L
                try {
                    val pkgInfo = pm.getPackageInfo(packageName, 0)
                    installTime = pkgInfo.firstInstallTime
                    updateTime = pkgInfo.lastUpdateTime
                    apkSize = File(appInfo.sourceDir).length()
                } catch (e: Exception) {
                    Log.w(TAG, "Paket bilgisi alınamadı: $packageName", e)
                }

                val desktopApp = DesktopApp(
                    label = label,
                    packageName = packageName,
                    iconBitmap = iconBitmap,
                    isSystem = isSystem,
                    isEnabled = isEnabled,
                    isLaunchable = true,
                    category = category,
                    installTime = installTime,
                    updateTime = updateTime,
                    apkSize = apkSize
                )
                apps.add(desktopApp)
            }

            // İsme göre sırala (varsayılan)
            apps.sortBy { it.label.lowercase(Locale.getDefault()) }

            // Önbelleği güncelle
            SystemState.allInstalledAppsCache.clear()
            SystemState.allInstalledAppsCache.addAll(apps)
            SystemState.isAppsLoaded = true
            Log.d(TAG, "${apps.size} uygulama yüklendi")

        } catch (e: Exception) {
            Log.e(TAG, "Uygulama yükleme hatası", e)
            SystemState.isAppsLoaded = false
        }

        return@withContext apps
    }

    /**
     * Android 8.0+ (API 26) sistem kategorisini öncelikli olarak dener,
     * yoksa metin eşleştirmesine geçer.
     */
    private fun getAppCategory(
        context: Context,
        appInfo: ApplicationInfo,
        packageName: String,
        label: String
    ): String {
        // Android 8.0+ (API 26) sistem kategorisi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val category = appInfo.category
                when (category) {
                    ApplicationInfo.CATEGORY_GAME -> return "Oyun"
                    ApplicationInfo.CATEGORY_AUDIO -> return "Müzik"
                    ApplicationInfo.CATEGORY_VIDEO -> return "Video"
                    ApplicationInfo.CATEGORY_IMAGE -> return "Fotoğraf"
                    ApplicationInfo.CATEGORY_SOCIAL -> return "Sosyal"
                    ApplicationInfo.CATEGORY_NEWS -> return "Haber"
                    ApplicationInfo.CATEGORY_MAPS -> return "Harita"
                    ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "Üretkenlik"
                    else -> if (category != ApplicationInfo.CATEGORY_UNDEFINED) {
                        return "Diğer"
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Kategori okunamadı: $packageName", e)
            }
        }

        // Fallback: Metin eşleştirmesi
        return guessCategoryByText(packageName, label)
    }

    /**
     * Metin eşleştirmesi ile kategori tahmini (eski Android'ler için).
     */
    private fun guessCategoryByText(packageName: String, label: String): String {
        val lowerPkg = packageName.lowercase(Locale.getDefault())
        val lowerLabel = label.lowercase(Locale.getDefault())

        return when {
            lowerPkg.contains("game") || lowerLabel.contains("oyun") -> "Oyun"
            lowerPkg.contains("chat") || lowerPkg.contains("whatsapp") || lowerPkg.contains("telegram") ||
                    lowerPkg.contains("messenger") || lowerLabel.contains("sosyal") || lowerLabel.contains("sohbet") -> "Sosyal"
            lowerPkg.contains("office") || lowerPkg.contains("word") || lowerPkg.contains("excel") ||
                    lowerPkg.contains("powerpoint") || lowerLabel.contains("üretkenlik") || lowerLabel.contains("ofis") -> "Üretkenlik"
            lowerPkg.contains("music") || lowerPkg.contains("spotify") || lowerPkg.contains("player") -> "Müzik"
            lowerPkg.contains("camera") || lowerPkg.contains("gallery") || lowerPkg.contains("photo") -> "Fotoğraf"
            lowerPkg.contains("news") || lowerPkg.contains("haber") -> "Haber"
            lowerPkg.contains("shop") || lowerPkg.contains("market") || lowerPkg.contains("alışveriş") -> "Alışveriş"
            lowerPkg.contains("bank") || lowerPkg.contains("finance") || lowerPkg.contains("finans") -> "Finans"
            lowerPkg.contains("health") || lowerPkg.contains("sağlık") -> "Sağlık"
            lowerPkg.contains("learn") || lowerPkg.contains("edu") || lowerPkg.contains("eğitim") -> "Eğitim"
            lowerPkg.contains("tool") || lowerPkg.contains("utility") || lowerPkg.contains("araç") -> "Araçlar"
            else -> "Diğer"
        }
    }

    /**
     * Kategoriye göre filtreleme yapar.
     */
    fun filterByCategory(apps: List<DesktopApp>, category: String): List<DesktopApp> {
        return if (category == "Tümü") apps else apps.filter { it.category == category }
    }

    /**
     * Arama metnine göre filtreleme yapar (isim veya paket adı).
     */
    fun searchApps(apps: List<DesktopApp>, query: String): List<DesktopApp> {
        if (query.isBlank()) return apps
        val lowerQuery = query.lowercase(Locale.getDefault())
        return apps.filter {
            it.label.lowercase(Locale.getDefault()).contains(lowerQuery) ||
            it.packageName.lowercase(Locale.getDefault()).contains(lowerQuery)
        }
    }

    /**
     * Uygulamaları belirtilen kritere göre sıralar.
     */
    fun sortApps(apps: List<DesktopApp>, sortBy: String): List<DesktopApp> {
        return when (sortBy.lowercase(Locale.getDefault())) {
            "name" -> apps.sortedBy { it.label.lowercase(Locale.getDefault()) }
            "date" -> apps.sortedByDescending { it.installTime }
            "size" -> apps.sortedByDescending { it.apkSize }
            "category" -> apps.sortedBy { it.category }
            else -> apps
        }
    }
}

// ==================== COMPOSE ENTEGRASYONU ====================

/**
 * Composable fonksiyon – uygulama listesini yükler ve state olarak sunar.
 * Uygulama başlangıcında çağrılır, otomatik olarak önbelleğe alır.
 *
 * @param includeSystem Sistem uygulamalarını dahil et (varsayılan true)
 * @param forceRefresh Önbelleği yenile (varsayılan false)
 * @return DesktopApp listesi (State)
 */
@Composable
fun rememberDesktopApps(
    includeSystem: Boolean = true,
    forceRefresh: Boolean = false
): List<DesktopApp> {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<DesktopApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 🟢 FIX: LaunchedEffect(forceRefresh) olarak sadeleştirildi
    LaunchedEffect(forceRefresh) {
        isLoading = true
        try {
            val loaded = DesktopManager.loadApps(
                context = context,
                includeSystem = includeSystem,
                forceRefresh = forceRefresh
            )
            apps = loaded
        } catch (e: Exception) {
            Log.e("DesktopManager", "Yükleme hatası", e)
            apps = emptyList()
        } finally {
            isLoading = false
        }
    }

    // Eğer önbellek doluysa ve yükleme bitene kadar önbelleği göster
    if (!isLoading && apps.isEmpty() && SystemState.isAppsLoaded) {
        apps = SystemState.allInstalledAppsCache.toList()
    }

    return apps
}

/**
 * AppInfo'dan DesktopApp'e dönüşüm extension'ı.
 */
fun AppInfo.toDesktopApp(): DesktopApp {
    return DesktopApp(
        label = displayName,
        packageName = packageName,
        iconBitmap = iconBitmap ?: android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap(),
        isSystem = system,
        isEnabled = true,
        isLaunchable = isLaunchable,
        category = category,
        installTime = installTime,
        updateTime = updateTime,
        apkSize = apkSize
    )
}
