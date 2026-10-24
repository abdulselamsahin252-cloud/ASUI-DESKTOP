package com.windowsui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * AppInfo – Gelişmiş uygulama bilgi sınıfı.
 *
 * Bu sınıf, Android'de yüklü bir uygulama hakkında detaylı bilgi tutar.
 * DesktopApp ile tam uyumludur ve UI'da kullanılmak üzere ikon bitmap'ini de içerir.
 *
 * Özellikler:
 * - Temel bilgiler (paket adı, görünen ad, sürüm, hedef SDK)
 * - Sistem uygulaması mı, kullanıcı uygulaması mı?
 * - Yükleme tarihi ve son güncelleme tarihi
 * - APK boyutu, veri boyutu, önbellek boyutu (root ile)
 * - Uygulama izinleri listesi
 * - Kategori (oyun, sosyal, üretkenlik, vb.)
 * - Varsayılan uygulama olup olmadığı (tarayıcı, launcher, SMS, vb.)
 * - Kullanım istatistikleri (son kullanım zamanı, kullanım süresi) – UsageStats ile entegre
 * - Widget, bildirim, arka plan çalışma desteği
 * - Başlatılabilir mi (launcher intent'i var mı?)
 * - Uygulama simgesi (Drawable veya ImageBitmap)
 */
data class AppInfo(
    // ============ TEMEL BİLGİLER ============
    val packageName: String,
    val displayName: String,
    val icon: String, // Drawable veya bitmap'in string temsili (URI veya base64) – eski sistemle uyumluluk için
    val system: Boolean = false,

    // ============ YENİ ÖZELLİKLER ============
    val versionName: String? = null,
    val versionCode: Long = 0,
    val targetSdk: Int = 0,
    val minSdk: Int = 0,

    val installTime: Long = 0,
    val updateTime: Long = 0,
    val apkSize: Long = 0,          // APK dosya boyutu
    val dataSize: Long = 0,          // /data/data/... boyutu (root ile)
    val cacheSize: Long = 0,         // önbellek boyutu

    val isEnabled: Boolean = true,
    val isLaunchable: Boolean = false,
    val isDefaultBrowser: Boolean = false,
    val isDefaultLauncher: Boolean = false,
    val isDefaultSms: Boolean = false,

    val category: String = "Diğer",  // Oyun, Sosyal, Üretkenlik, Müzik, Fotoğraf, Haber, Alışveriş, Finans, Sağlık, Eğitim, Araçlar, Diğer
    val permissions: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val services: List<String> = emptyList(),

    val hasWidget: Boolean = false,
    val hasNotifications: Boolean = true,
    val canRunInBackground: Boolean = true,

    val lastUsageTime: Long = 0,     // UsageStats ile
    val totalUsageTime: Long = 0,    // UsageStats ile
    val launchCount: Int = 0,        // UsageStats ile

    val isHidden: Boolean = false,   // Kullanıcı tarafından gizlenmiş mi?
    val isPinned: Boolean = false,   // Görev çubuğuna sabitlenmiş mi?

    // UI için ikon bitmap'i (Compose'da kullanmak üzere)
    val iconBitmap: ImageBitmap? = null
) {

    /**
     * Uygulamanın insan okunabilir yükleme tarihi.
     */
    val installDate: String
        get() = if (installTime > 0) {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(installTime))
        } else "Bilinmiyor"

    /**
     * Uygulamanın insan okunabilir güncelleme tarihi.
     */
    val updateDate: String
        get() = if (updateTime > 0) {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(updateTime))
        } else "Bilinmiyor"

    /**
     * APK boyutunu insan okunabilir formatta döndürür.
     */
    val apkSizeFormatted: String
        get() = formatSize(apkSize)

    /**
     * Veri boyutunu insan okunabilir formatta döndürür.
     */
    val dataSizeFormatted: String
        get() = formatSize(dataSize)

    /**
     * Önbellek boyutunu insan okunabilir formatta döndürür.
     */
    val cacheSizeFormatted: String
        get() = formatSize(cacheSize)

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    /**
     * Uygulamanın kategori rengi (UI'da renkli etiket için).
     */

    /**
     * Uygulamanın kategori emojisi (UI'da gösterim için).
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

    companion object {
        /**
         * PackageManager ve ApplicationInfo'dan AppInfo oluşturur.
         */
        fun fromApplicationInfo(
            context: android.content.Context,
            appInfo: ApplicationInfo,
            pm: PackageManager
        ): AppInfo {
            val packageName = appInfo.packageName
            var versionName: String? = null
            var versionCode = 0L
            var targetSdk = 0
            var minSdk = 0
            var installTime = 0L
            var updateTime = 0L

            try {
                val pkgInfo = pm.getPackageInfo(packageName, 0)
                versionName = pkgInfo.versionName
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }
                targetSdk = appInfo.targetSdkVersion
                minSdk = appInfo.minSdkVersion
                installTime = pkgInfo.firstInstallTime
                updateTime = pkgInfo.lastUpdateTime
            } catch (e: Exception) {}

            // APK boyutu
            val apkSize = try {
                File(appInfo.sourceDir).length()
            } catch (e: Exception) {
                0L
            }

            // İkon
            val iconDrawable = try {
                appInfo.loadIcon(pm)
            } catch (e: Exception) {
                pm.defaultActivityIcon
            }
            val iconBitmap = try {
                iconDrawable.toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }

            // Başlatılabilir mi?
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            val isLaunchable = launchIntent != null

            // Kategori tahmini (basit)
            val category = guessCategory(packageName, appInfo.loadLabel(pm).toString())

            // İzinler (isteğe bağlı)
            val permissions = try {
                val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                pkgInfo.requestedPermissions?.toList() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            return AppInfo(
                packageName = packageName,
                displayName = appInfo.loadLabel(pm).toString(),
                icon = appInfo.packageName, // Eski sistemle uyumluluk için
                system = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                versionName = versionName,
                versionCode = versionCode,
                targetSdk = targetSdk,
                minSdk = minSdk,
                installTime = installTime,
                updateTime = updateTime,
                apkSize = apkSize,
                isLaunchable = isLaunchable,
                category = category,
                permissions = permissions,
                iconBitmap = iconBitmap
            )
        }

        /**
         * Basit kategori tahmini.
         */
        private fun guessCategory(packageName: String, label: String): String {
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
    }
}

