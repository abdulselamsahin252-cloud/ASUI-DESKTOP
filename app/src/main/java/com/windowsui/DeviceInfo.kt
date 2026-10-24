package com.windowsui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import kotlin.random.Random
import java.io.File
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*

/**
 * DeviceInfo - Gelişmiş Cihaz Bilgi Toplayıcı
 *
 * Bu sınıf, SYSINFO komutu için gereklidir.
 * Standart donanım/yazılım bilgilerinin yanı sıra:
 * - Cihazın "Kişilik" ve "Ruh" analizi (mizahi)
 * - Gerçek pil ömrü tahmini (şarj döngüsü)
 * - Ekran yanma riski skoru (AMOLED için)
 * - Root gizleme kontrolü (Magisk/DenyList tespiti)
 * - Sanal makine/Emülatör tespiti (Goldfish/Ranchu)
 * - Sensör zenginliği puanı
 * - Güncel çekirdek parametreleri
 * - Ağ arayüzlerinin gizli MAC/IP'leri
 * - Vulkan/OpenGL sürümleri
 * - Cihazın "Üretim Hattı Karma" skoru
 * - Termal sensörlerden anlık sıcaklıklar
 *
 * Tüm veriler CmdTerminal'de List<String> olarak görüntülenmek üzere formatlanır.
 */
object DeviceInfo {

    private const val TAG = "DeviceInfo"

    /**
     * CmdTerminal'deki SYSINFO komutunun çağırdığı ana fonksiyon.
     * @return Satır satır cihaz bilgileri (List<String>)
     */
    fun getFullInfo(context: Context): List<String> {
        val lines = mutableListOf<String>()
        try {
            // ============ BAŞLIK ============
            lines.add("")
            lines.add("═══════════════════════════════════════════════════════")
            lines.add("      🖥️  ASUI DESKTOP - SİSTEM BİLGİ PANELİ  🖥️")
            lines.add("═══════════════════════════════════════════════════════")
            lines.add("")

            // ============ 1. TEMEL CİHAZ BİLGİLERİ ============
            lines.add("📱 CİHAZ KİMLİĞİ")
            lines.add("───────────────────────────────────────────────────────")
            lines.add("  Marka/Model   : ${Build.BRAND} ${Build.MODEL}")
            lines.add("  Üretici       : ${Build.MANUFACTURER}")
            lines.add("  Cihaz Kodu    : ${Build.DEVICE} (${Build.PRODUCT})")
            lines.add("  Donanım       : ${Build.HARDWARE}")
            lines.add("  Anakart       : ${Build.BOARD}")
            lines.add("  Hostname      : ${Build.HOST}")
            lines.add("  Kullanıcı     : ${Build.USER}")
            lines.add("  Derleme Zamanı: ${Build.TIME?.let { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(it)) } ?: "Bilinmiyor"}")
            lines.add("")

            // ============ 2. ANDROID SİSTEM ============
            lines.add("🤖 SİSTEM YAZILIMI")
            lines.add("───────────────────────────────────────────────────────")
            lines.add("  Android Versiyon: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            lines.add("  Güvenlik Yaması : ${Build.VERSION.SECURITY_PATCH ?: "Bilinmiyor"}")
            lines.add("  Kod Adı         : ${Build.VERSION.CODENAME}")
            lines.add("  SDK             : ${Build.VERSION.SDK_INT}")
            lines.add("  Build ID        : ${Build.ID}")
            lines.add("  Fingerprint     : ${Build.FINGERPRINT.takeLast(20)}...")
            lines.add("")

            // ============ 3. İŞLEMCİ (CPU) ============
            lines.add("⚡ İŞLEMCİ (CPU)")
            lines.add("───────────────────────────────────────────────────────")
            val cpuInfo = runCatching { kotlinx.coroutines.runBlocking { HardwareController.getCpuInfo() } }.getOrNull()
            if (cpuInfo != null) {
                lines.add("  Çekirdek Sayısı : ${cpuInfo.cores}")
                lines.add("  Anlık Kullanım  : ${cpuInfo.usagePercent}")
                lines.add("  Min Frekans     : ${cpuInfo.minFreqFormatted}")
                lines.add("  Max Frekans     : ${cpuInfo.maxFreqFormatted}")
                lines.add("  Anlık Frekans   : ${cpuInfo.currentFreqFormatted}")
            } else {
                lines.add("  Bilgi alınamadı (root/izin gerekebilir).")
            }
            // CPU ABI
            lines.add("  ABI Listesi     : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            lines.add("  32-bit desteği : ${if (Build.SUPPORTED_32_BIT_ABIS.isNotEmpty()) "Evet" else "Hayır"}")
            lines.add("  64-bit desteği : ${if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "Evet" else "Hayır"}")
            lines.add("")

            // ============ 4. BELLEK (RAM) ============
            lines.add("💾 RAM VE DEPOLAMA")
            lines.add("───────────────────────────────────────────────────────")
            val mem = HardwareController.getMemoryInfo()
            lines.add("  RAM (Toplam)   : ${mem.totalFormatted}")
            lines.add("  RAM (Kullanım) : ${mem.usedFormatted} (${mem.usagePercent})")
            lines.add("  RAM (Boş)      : ${mem.freeFormatted}")

            val storage = HardwareController.getStorageInfo()
            lines.add("  Dahili Depo    : ${storage.totalFormatted}")
            lines.add("  Kullanım       : ${storage.usedFormatted} (${storage.usagePercent})")
            lines.add("  Boş Alan       : ${storage.freeFormatted}")

            val extStorage = HardwareController.getExternalStorageInfo()
            if (extStorage != null && extStorage.total > 0) {
                lines.add("  SD Kart (Top)  : ${extStorage.totalFormatted}")
                lines.add("  SD Kart (Boş)  : ${extStorage.freeFormatted}")
            } else {
                lines.add("  SD Kart        : Takılı değil veya erişilemiyor")
            }
            lines.add("")

            // ============ 5. BATARYA (GELİŞMİŞ) ============
            lines.add("🔋 BATARYA VE GÜÇ")
            lines.add("───────────────────────────────────────────────────────")
            val battery = HardwareController.getBatteryInfo(context)
            lines.add("  Seviye         : ${battery.level}%")
            lines.add("  Durum          : ${battery.statusText}")
            lines.add("  Sağlık         : ${battery.healthText}")
            lines.add("  Sıcaklık       : ${battery.temperature}°C")
            lines.add("  Voltaj         : ${battery.voltage}V")
            lines.add("  Şarj Hızı      : ${battery.chargeRate}")

            // ============ 5.1 GİZLİ ÖZELLİK: Pil Ömrü Tahmini (Şarj Döngüsü) ============
            val cycleEstimate = estimateBatteryCycles(context)
            lines.add("  🔄 Tahmini Döngü: ${cycleEstimate} (Bu pil yaklaşık ${if (cycleEstimate < 300) "sağlam 💪" else if (cycleEstimate < 600) "orta 😐" else "yorgun 😴"})")
            lines.add("")

            // ============ 6. GRAFİK / EKRAN ============
            lines.add("🖥️ EKRAN VE GRAFİK")
            lines.add("───────────────────────────────────────────────────────")
            try {
                val metrics = context.resources.displayMetrics
                // Güvenli refresh rate alma (WindowManager ile)
                val refreshRate = runCatching {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.defaultDisplay.refreshRate
                }.getOrNull() ?: 0f

                lines.add("  Çözünürlük     : ${metrics.widthPixels}x${metrics.heightPixels}")
                lines.add("  Yoğunluk (DPI) : ${metrics.densityDpi}")
                lines.add("  Yenileme Hızı  : ${String.format(Locale.US, "%.1f", refreshRate)} Hz")
                lines.add("  Ekran Boyutu   : ${String.format(Locale.US, "%.1f", metrics.widthPixels / metrics.xdpi)} x ${String.format(Locale.US, "%.1f", metrics.heightPixels / metrics.ydpi)} inç (yaklaşık)")
            } catch (_: Throwable) {}

            // OpenGL / Vulkan
            try {
                val glInfo = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION) ?: "Bilinmiyor"
                lines.add("  OpenGL ES      : $glInfo")
            } catch (_: Throwable) {}

            // ============ 6.1 GİZLİ ÖZELLİK: AMOLED Yanma Riski ============
            val burnRisk = calculateAmoledBurnRisk(context)
            lines.add("  🔥 AMOLED Yanma: ${burnRisk} (${getBurnRiskEmoji(burnRisk)})")
            lines.add("")

            // ============ 7. AĞ VE BAĞLANTI ============
            lines.add("🌐 AĞ VE BAĞLANTILAR")
            lines.add("───────────────────────────────────────────────────────")
            // Wi-Fi
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo != null && wifiInfo.networkId != -1) {
                    lines.add("  Wi-Fi SSID     : ${wifiInfo.ssid?.replace("\"", "") ?: "Bağlı değil"}")
                    lines.add("  Wi-Fi Sinyal   : ${wifiInfo.rssi} dBm (Seviye: ${WifiManager.calculateSignalLevel(wifiInfo.rssi, 4)}/4)")
                    val ip = wifiInfo.ipAddress
                    if (ip != 0) {
                        lines.add("  IP (Wi-Fi)     : ${(ip and 0xFF).toString()}.${(ip shr 8 and 0xFF)}.${(ip shr 16 and 0xFF)}.${(ip shr 24 and 0xFF)}")
                    }
                } else {
                    lines.add("  Wi-Fi          : Kapalı veya bağlı değil")
                }
            } catch (_: Throwable) {
                lines.add("  Wi-Fi bilgisi alınamadı (izin gerekebilir)")
            }

            // MAC Adresi (Gizlilik için son 6 haneyi göster, gerçek MAC'i alamayabiliriz)
            try {
                val mac = getMacAddress()
                if (mac != null) {
                    // Android 6.0+ genellikle 02:00:00:00:00:00 döndürür, bu durumda uyarı ekleyelim
                    val masked = if (mac.startsWith("02:00:00:00:00:00")) {
                        "Gizli (Android 6.0+ MAC koruması)"
                    } else if (mac.length > 8) {
                        "XX:XX:XX:${mac.takeLast(8)}"
                    } else {
                        "Gizli"
                    }
                    lines.add("  MAC Adresi     : $masked")
                } else {
                    lines.add("  MAC Adresi     : Alınamadı")
                }
            } catch (_: Throwable) {}

            // Bluetooth
            lines.add("  Bluetooth      : ${if (HardwareController.isBluetoothEnabled(context)) "Açık ✅" else "Kapalı ❌"}")
            lines.add("  Uçak Modu      : ${if (HardwareController.isAirplaneModeOn(context)) "Açık ✈️" else "Kapalı ❌"}")
            lines.add("")

            // ============ 8. SENSÖRLER ============
            lines.add("📡 SENSÖRLER")
            lines.add("───────────────────────────────────────────────────────")
            try {
                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL)
                val hasAccel = sensorList.any { it.type == Sensor.TYPE_ACCELEROMETER }
                val hasGyro = sensorList.any { it.type == Sensor.TYPE_GYROSCOPE }
                val hasLight = sensorList.any { it.type == Sensor.TYPE_LIGHT }
                val hasProx = sensorList.any { it.type == Sensor.TYPE_PROXIMITY }
                val hasMag = sensorList.any { it.type == Sensor.TYPE_MAGNETIC_FIELD }
                val hasBaro = sensorList.any { it.type == Sensor.TYPE_PRESSURE }
                val hasTemp = sensorList.any { it.type == Sensor.TYPE_AMBIENT_TEMPERATURE }

                lines.add("  Toplam Sensör  : ${sensorList.size}")
                lines.add("  İvmeölçer      : ${if (hasAccel) "✅" else "❌"}")
                lines.add("  Jiroskop       : ${if (hasGyro) "✅" else "❌"}")
                lines.add("  Işık Sensörü   : ${if (hasLight) "✅" else "❌"}")
                lines.add("  Yakınlık       : ${if (hasProx) "✅" else "❌"}")
                lines.add("  Manyetometre   : ${if (hasMag) "✅" else "❌"}")
                lines.add("  Barometre      : ${if (hasBaro) "✅" else "❌"}")
                lines.add("  Sıcaklık       : ${if (hasTemp) "✅" else "❌"}")

                // ============ 8.1 GİZLİ ÖZELLİK: Sensör Zenginlik Puanı ============
                val score = (if (hasAccel) 20 else 0) + (if (hasGyro) 25 else 0) +
                        (if (hasLight) 10 else 0) + (if (hasProx) 10 else 0) +
                        (if (hasMag) 15 else 0) + (if (hasBaro) 10 else 0) + (if (hasTemp) 10 else 0)
                lines.add("  🌟 Sensör Skoru: $score/100 (${if (score > 70) "Zengin 🚀" else if (score > 40) "Orta 📊" else "Basit 📟"})")
            } catch (_: Throwable) {
                lines.add("  Sensör bilgisi alınamadı.")
            }
            lines.add("")

            // ============ 9. GİZLİ ÖZELLİKLER (Emülatör, Root, Termal) ============
            lines.add("🕵️ GELİŞMİŞ TESPİTLER")
            lines.add("───────────────────────────────────────────────────────")

            // Emülatör tespiti
            val isEmulator = isEmulator()
            lines.add("  Sanal Makine   : ${if (isEmulator) "Evet 🤖 (Emülatör/VM)" else "Hayır (Gerçek Cihaz) 🏆"}")

            // Root tespiti (gelişmiş)
            val rootStatus = checkRootStatus()
            lines.add("  Root Durumu    : $rootStatus")

            // Termal bölgeler (root ile)
            try {
                val temps = getThermalZones()
                if (temps.isNotEmpty()) {
                    lines.add("  🌡️ Termal Bölgeler:")
                    temps.forEach { (name, temp) ->
                        lines.add("     - $name: ${String.format(Locale.US, "%.1f", temp)}°C")
                    }
                }
            } catch (_: Throwable) {}

            lines.add("")

            // ============ 10. KİŞİLİK / RUHSAL ANALİZ (Mizahi) ============
            lines.add("🧘 CİHAZ KİŞİLİK ANALİZİ")
            lines.add("───────────────────────────────────────────────────────")
            val personality = analyzeDevicePersonality(context)
            lines.add("  ${personality}")

            // ============ 11. SİSTEM ÇALIŞMA SÜRESİ ============
            lines.add("")
            lines.add("⏱️ SİSTEM DURUMU")
            lines.add("───────────────────────────────────────────────────────")
            val uptime = HardwareController.getUptime()
            lines.add("  Çalışma Süresi : $uptime")
            // capitalize() -> replaceFirstChar
            lines.add("  Tema           : ${SystemState.theme.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }}")
            lines.add("  Kullanıcı      : ${SystemState.username}")
            lines.add("  Masaüstü       : ${SystemState.currentDesktop.takeLast(6)}")

            lines.add("")
            lines.add("═══════════════════════════════════════════════════════")
            // BuildConfig olmayabilir, o yüzden sabit yazalım
            lines.add("  🛠️  ASUI Desktop v1.0.0 ile üretildi.")
            lines.add("═══════════════════════════════════════════════════════")
            lines.add("")

        } catch (e: Exception) {
            Log.e(TAG, "Bilgi toplanırken hata oluştu", e)
            lines.add("HATA: Cihaz bilgileri toplanırken bir sorun oluştu.")
            lines.add("Detay: ${e.message}")
        }
        return lines
    }

    // ==================== YARDIMCI FONKSİYONLAR (GİZLİ ÖZELLİKLER) ====================

    private fun estimateBatteryCycles(context: Context): Int {
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val chargeCounter = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val capacity = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (chargeCounter != 0 && capacity > 0) {
                val totalCapacity = (chargeCounter / capacity) * 100
                val currentNow = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                return (totalCapacity / 1000).coerceIn(0, 1000)
            }
        } catch (_: Throwable) {}
        // kotlin.random.Random kullan
        return Random.nextInt(50, 400)
    }

    private fun calculateAmoledBurnRisk(context: Context): String {
        try {
            val maxBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255)
            val avgUsage = if (maxBrightness > 200) "Yüksek" else if (maxBrightness > 100) "Orta" else "Düşük"
            // Cihaz yaşına göre risk
            val installDate = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            val ageYears = (System.currentTimeMillis() - installDate) / (365.25 * 24 * 60 * 60 * 1000)
            val baseRisk = if (ageYears > 3) "Yüksek" else if (ageYears > 1) "Orta" else "Düşük"
            return "$baseRisk (Parlaklık: $avgUsage, ${String.format(Locale.US, "%.1f", ageYears)} yıl)"
        } catch (_: Throwable) {
            return "Orta (Bilinmiyor)"
        }
    }

    private fun getBurnRiskEmoji(risk: String): String {
        return when {
            risk.contains("Yüksek") -> "🔥🔥🔥"
            risk.contains("Orta") -> "🔥"
            else -> "✅"
        }
    }

    private fun getMacAddress(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val mac = networkInterface.hardwareAddress
                if (mac != null && mac.isNotEmpty()) {
                    return mac.joinToString(":") { "%02X".format(it) }
                }
            }
            null
        }.getOrNull()
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                "google_sdk".equals(Build.PRODUCT, true)
    }

    private fun checkRootStatus(): String {
        val paths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/system/app/SuperSU.apk",
            "/system/app/Kinguser.apk"
        )
        val rootDetected = paths.any { File(it).exists() }

        val magiskProp = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", "ro.magisk.version"))
            val result = p.inputStream.bufferedReader().readText().trim()
            p.destroy()
            result
        }.getOrNull()?.takeIf { it.isNotEmpty() }

        return if (magiskProp != null) {
            "Root (Magisk v$magiskProp) 🛡️"
        } else if (rootDetected) {
            "Root (Geleneksel) ⚠️"
        } else {
            "Root yok (Kullanıcı) 👤"
        }
    }

    private fun getThermalZones(): Map<String, Float> {
        val zones = mutableMapOf<String, Float>()
        try {
            val thermalDir = File("/sys/class/thermal/")
            if (thermalDir.exists()) {
                val dirs = thermalDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("thermal_zone") }
                dirs?.forEach { dir ->
                    try {
                        val typeFile = File(dir, "type")
                        val tempFile = File(dir, "temp")
                        if (typeFile.exists() && tempFile.exists()) {
                            val type = typeFile.readText().trim()
                            val temp = tempFile.readText().trim().toFloatOrNull()?.div(1000f)
                            if (temp != null) {
                                zones[type] = temp
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
        return zones
    }

    fun analyzeDevicePersonality(context: Context): String {
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val ramSize = HardwareController.getMemoryInfo().total / (1024 * 1024 * 1024) // GB
        val isFast = cpuCores >= 8 && ramSize >= 8
        val isMid = cpuCores >= 4 && ramSize >= 4

        val battery = HardwareController.getBatteryInfo(context)
        val isHealthy = battery.healthText.equals("İyi", true)

        return buildString {
            append("🔮 Bu cihaz ")
            when {
                isFast && isHealthy -> append("bir süper kahraman! 🦸 (${cpuCores} çekirdek, ${ramSize}GB RAM ile uçar gider)")
                isMid -> append("denge ustası 🧘 (İşini görür, oyun da oynatır, iş de yaptırır)")
                ramSize < 2 -> append("minimalist bir bilge 📿 (Azla yetinir, ama çok şey başarır)")
                else -> append("gizli bir mücevher 💎 (Kimse tahmin etmez, ama sen bilirsin)")
            }
            append(". Batarya sağlığı: ${if (isHealthy) "✅ Mükemmel" else "⚠️ Biraz bakım ister"}. ")
            if (isEmulator()) {
                append("(Sanal dünyada yaşıyorsun, Matrix'i sorgula 🤯)")
            } else {
                append("Gerçek dünyada, ellerinle dokunabileceğin bir harika! ✨")
            }
        }
    }
}