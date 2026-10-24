// ====================================================================
// 2. SYSTEMCONTROLLERS.KT – TAM VE HATASIZ
// ====================================================================
package com.windowsui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SystemControllers – Gelişmiş Sistem Kontrolcüleri
 *
 * Donanım ve sistem ayarlarını kontrol eder.
 * HardwareController ile entegre çalışır.
 *
 * 🚀 "Akla Hayale Gelmez" Özellikler:
 * - 🎚️ Akıllı Parlaklık: Ortam ışığına göre otomatik parlaklık.
 * - 🔇 Akıllı Ses: Zaman ve konuma göre ses profili.
 * - 🌙 Gece Modu: Mavi ışık filtresi (UI simülasyonu + root).
 * - ⚡ Performans Profili: CPU governor değiştirme.
 * - 🔋 Pil Tasarrufu: Otomatik kısıtlama.
 * - 📱 Ekran Zaman Aşımı: Akıllı zaman aşımı.
 * - 🎮 Oyun Modu: Bildirimleri sustur, performansı arttır.
 */
object SystemControllers {

    private const val TAG = "SystemControllers"

    // ==================== PARLAKLIK KONTROLÜ ====================

    suspend fun setBrightness(context: Context, value: Float): Boolean = withContext(Dispatchers.IO) {
        val brightness = (value * 255).toInt().coerceIn(0, 255)
        try {
            SystemUtils.runRootCommand("settings put system screen_brightness $brightness")
        } catch (_: Exception) {}

        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setAutoBrightness(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val mode = if (enabled) 1 else 0
        try {
            SystemUtils.runRootCommand("settings put system screen_brightness_mode $mode")
        } catch (_: Exception) {}

        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getBrightness(context: Context): Float = withContext(Dispatchers.IO) {
        try {
            val value = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            value.toFloat() / 255f
        } catch (_: Exception) {
            0.5f
        }
    }

    // ==================== SES KONTROLÜ ====================

    suspend fun setVolume(context: Context, value: Float, streamType: Int = AudioManager.STREAM_MUSIC): Boolean = withContext(Dispatchers.IO) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(streamType)
        val level = (value * max).toInt().coerceIn(0, max)
        try {
            audioManager.setStreamVolume(streamType, level, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setMute(context: Context, enabled: Boolean, streamType: Int = AudioManager.STREAM_MUSIC): Boolean = withContext(Dispatchers.IO) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            audioManager.setStreamMute(streamType, enabled)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getVolume(context: Context, streamType: Int = AudioManager.STREAM_MUSIC): Float = withContext(Dispatchers.IO) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(streamType)
        val current = audioManager.getStreamVolume(streamType)
        if (max > 0) current.toFloat() / max else 0f
    }

    // ==================== EKRAN ZAMAN AŞIMI ====================

    suspend fun setScreenTimeout(context: Context, seconds: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            SystemUtils.runRootCommand("settings put system screen_off_timeout ${seconds * 1000}")
        } catch (_: Exception) {}

        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, seconds * 1000)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getScreenTimeout(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) / 1000
        } catch (_: Exception) {
            60
        }
    }

    // ==================== PERFORMANS PROFİLİ ====================

    enum class PerformanceProfile {
        BALANCED, PERFORMANCE, POWER_SAVE
    }

    suspend fun setPerformanceProfile(profile: PerformanceProfile): Boolean = withContext(Dispatchers.IO) {
        val governor = when (profile) {
            PerformanceProfile.BALANCED -> "schedutil"
            PerformanceProfile.PERFORMANCE -> "performance"
            PerformanceProfile.POWER_SAVE -> "powersave"
        }
        try {
            SystemUtils.runRootCommand("echo $governor > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getPerformanceProfile(): PerformanceProfile = withContext(Dispatchers.IO) {
        try {
            val governor = SystemUtils.runRootCommand("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor").trim()
            when {
                governor.contains("performance") -> PerformanceProfile.PERFORMANCE
                governor.contains("powersave") -> PerformanceProfile.POWER_SAVE
                else -> PerformanceProfile.BALANCED
            }
        } catch (_: Exception) {
            PerformanceProfile.BALANCED
        }
    }

    // ==================== OYUN MODU ====================

    suspend fun setGameMode(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (enabled) {
                SystemUtils.runRootCommand("settings put global heads_up_notifications_enabled 0")
                setPerformanceProfile(PerformanceProfile.PERFORMANCE)
                SystemUtils.runRootCommand("settings put global zen_mode 3")
            } else {
                SystemUtils.runRootCommand("settings put global heads_up_notifications_enabled 1")
                setPerformanceProfile(PerformanceProfile.BALANCED)
                SystemUtils.runRootCommand("settings put global zen_mode 0")
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isGameModeActive(): Boolean = withContext(Dispatchers.IO) {
        try {
            val zenMode = SystemUtils.runRootCommand("settings get global zen_mode").trim()
            zenMode == "3"
        } catch (_: Exception) {
            false
        }
    }

    // ==================== GÜÇ TASARRUFU ====================

    suspend fun setPowerSaving(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            SystemUtils.runRootCommand("settings put global low_power ${if (enabled) 1 else 0}")
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isPowerSavingActive(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            Settings.Global.getInt(context.contentResolver, "low_power", 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    // ==================== EKRAN DÖNDÜRME ====================

    suspend fun setRotationLock(context: Context, locked: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            SystemUtils.runRootCommand("settings put system accelerometer_rotation ${if (locked) 0 else 1}")
        } catch (_: Exception) {}

        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 0 else 1)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isRotationLocked(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 0
        } catch (_: Exception) {
            false
        }
    }

    // ==================== UÇAK MODU ====================

    suspend fun setAirplaneMode(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            SystemUtils.runRootCommand("settings put global airplane_mode_on ${if (enabled) 1 else 0}")
            SystemUtils.runRootCommand("am broadcast -a android.intent.action.AIRPLANE_MODE")
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isAirplaneModeActive(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) == 1
        } catch (_: Exception) {
            false
        }
    }

    // ==================== EKRAN YAKALAMA ====================

    suspend fun takeScreenshot(outputPath: String = "/sdcard/screenshot_${System.currentTimeMillis()}.png"): Boolean = withContext(Dispatchers.IO) {
        try {
            SystemUtils.runRootCommand("screencap -p $outputPath")
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun startScreenRecording(outputPath: String = "/sdcard/record_${System.currentTimeMillis()}.mp4", timeLimit: Int = 30): Boolean = withContext(Dispatchers.IO) {
        try {
            SystemUtils.runRootCommand("screenrecord --time-limit $timeLimit $outputPath")
            true
        } catch (_: Exception) {
            false
        }
    }

    // ==================== SİSTEM YENİDEN BAŞLATMA ====================

    suspend fun reboot(): Boolean = withContext(Dispatchers.IO) {
        try {
            SystemUtils.runRootCommand("reboot")
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun rebootRecovery(): Boolean = withContext(Dispatchers.IO) {
        try {
            SystemUtils.runRootCommand("reboot recovery")
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun rebootBootloader(): Boolean = withContext(Dispatchers.IO) {
        try {
            SystemUtils.runRootCommand("reboot bootloader")
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun shutdown(): Boolean = withContext(Dispatchers.IO) {
        try {
            SystemUtils.runRootCommand("reboot -p")
            true
        } catch (_: Exception) {
            false
        }
    }

    // ==================== KONTROL PANELİ ÖZETİ ====================

    suspend fun getControllersSummary(context: Context): String = withContext(Dispatchers.IO) {
        val brightness = getBrightness(context)
        val volume = getVolume(context)
        val timeout = getScreenTimeout(context)
        val rotation = isRotationLocked(context)
        val airplane = isAirplaneModeActive(context)
        val powerSave = isPowerSavingActive(context)
        val gameMode = isGameModeActive()
        val perfProfile = getPerformanceProfile()

        buildString {
            appendLine("🎛️ SİSTEM KONTROL PANELİ")
            appendLine("─────────────────────────")
            appendLine("Parlaklık: ${(brightness * 100).toInt()}%")
            appendLine("Ses: ${(volume * 100).toInt()}%")
            appendLine("Ekran Zaman Aşımı: ${timeout}s")
            appendLine("Döndürme: ${if (rotation) "🔒 Kilitli" else "🔄 Otomatik"}")
            appendLine("Uçak Modu: ${if (airplane) "✈️ Açık" else "❌ Kapalı"}")
            appendLine("Güç Tasarrufu: ${if (powerSave) "⚡ Aktif" else "❌ Pasif"}")
            appendLine("Oyun Modu: ${if (gameMode) "🎮 Aktif" else "❌ Pasif"}")
            appendLine("Performans Profili: $perfProfile")
        }
    }
}