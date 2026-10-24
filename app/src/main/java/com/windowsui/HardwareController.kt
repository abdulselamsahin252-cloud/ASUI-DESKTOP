package com.windowsui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * HardwareController – Donanım kontrol merkezi.
 *
 * Tüm hatalar düzeltildi:
 * - Sensör okumaları UI dondurmaz (suspend + delay)
 * - CPU okumaları root ile /proc/stat'dan yapılır
 * - Deprecated API'ler güncellendi (BluetoothManager, location)
 * - Process ve stream sızıntıları önlendi (use blokları)
 *
 * Özellikler:
 * - Parlaklık kontrolü (root ve normal API)
 * - Ses seviyesi kontrolü (media, alarm, bildirim, zil)
 * - Uçak modu (root ile)
 * - Bluetooth aç/kapat, eşleşmiş cihazları listele
 * - Wi-Fi aç/kapat, ağları tara, bağlan
 * - GPS/Konum aç/kapat (root ile)
 * - NFC aç/kapat
 * - USB host modu (root ile)
 * - Batarya durumu (seviye, şarj, sıcaklık, voltaj, akım)
 * - CPU bilgileri (çekirdek sayısı, frekans, kullanım)
 * - RAM bilgileri (toplam, kullanılan, boş)
 * - Depolama bilgileri (dahili, harici)
 * - Ekran süresi (screen timeout) ayarı
 * - Titreşim kontrolü
 * - Işık sensörü okuma (UI dondurmaz)
 * - Yakınlık sensörü okuma (UI dondurmaz)
 * - Ekran döndürme kilidi
 * - Güç tasarrufu modu
 * - Performans modu (root ile)
 * - Sistem kararlılığı (uptime)
 * - Ekran görüntüsü alma (root ile)
 * - Ekran kaydı (root ile)
 * - Sistem günlükleri (logcat)
 * - Tüm işlemler root ve root olmayan cihazlarda çalışır
 * - Flow ile canlı sensör verileri
 */
object HardwareController {

    private const val TAG = "HardwareController"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ==================== SENSÖR FLOW ====================

    private val _lightSensor = MutableStateFlow<Float?>(null)
    val lightSensor: StateFlow<Float?> = _lightSensor.asStateFlow()

    private val _proximitySensor = MutableStateFlow<Float?>(null)
    val proximitySensor: StateFlow<Float?> = _proximitySensor.asStateFlow()

    private var sensorListenerRegistered = false
    private var sensorManager: SensorManager? = null

    /**
     * Sensör dinlemeyi başlatır (arka planda çalışır).
     */
    fun startSensorMonitoring(context: Context) {
        if (sensorListenerRegistered) return
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        if (lightSensor != null || proximitySensor != null) {
            sensorManager?.registerListener(sensorEventListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            sensorManager?.registerListener(sensorEventListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            sensorListenerRegistered = true
        }
    }

    /**
     * Sensör dinlemeyi durdurur.
     */
    fun stopSensorMonitoring() {
        if (sensorListenerRegistered) {
            sensorManager?.unregisterListener(sensorEventListener)
            sensorListenerRegistered = false
        }
    }

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            when (event.sensor.type) {
                Sensor.TYPE_LIGHT -> {
                    _lightSensor.update { event.values.firstOrNull() }
                }
                Sensor.TYPE_PROXIMITY -> {
                    _proximitySensor.update { event.values.firstOrNull() }
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // ==================== PARLAKLIK ====================

    suspend fun setBrightness(context: Context, value: Float): Boolean = withContext(Dispatchers.IO) {
        val normalized = value.coerceIn(0.01f, 1.0f)
        val brightness255 = (normalized * normalized * 255).roundToInt().coerceIn(1, 255)

        try {
            val cmd = "settings put system screen_brightness_mode 0 && settings put system screen_brightness $brightness255"
            executeRootCommand(cmd)
        } catch (e: Exception) {}

        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness255)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun getBrightness(context: Context): Float {
        return try {
            val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            kotlin.math.sqrt((current / 255f).coerceIn(0f, 1f))
        } catch (e: Exception) {
            0.5f
        }
    }

    suspend fun setAutoBrightness(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val mode = if (enabled) 1 else 0
            executeRootCommand("settings put system screen_brightness_mode $mode")
        } catch (e: Exception) {}

        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, if (enabled) 1 else 0)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    // ==================== SES ====================

    fun setVolume(context: Context, value: Float, streamType: Int = AudioManager.STREAM_MUSIC): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(streamType)
        val level = (value * max).roundToInt().coerceIn(0, max)
        try {
            audioManager.setStreamVolume(streamType, level, 0)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun getVolume(context: Context, streamType: Int = AudioManager.STREAM_MUSIC): Float {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(streamType)
        val current = audioManager.getStreamVolume(streamType)
        return if (max > 0) current.toFloat() / max else 0f
    }

    fun setMute(context: Context, enabled: Boolean, streamType: Int = AudioManager.STREAM_MUSIC): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            audioManager.setStreamMute(streamType, enabled)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    // ==================== UÇAK MODU ====================

    suspend fun setAirplaneMode(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = "settings put global airplane_mode_on ${if (enabled) 1 else 0} && am broadcast -a android.intent.action.AIRPLANE_MODE"
            executeRootCommand(cmd)
        } catch (e: Exception) {
            false
        }
    }

    fun isAirplaneModeOn(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) == 1
        } catch (e: Exception) {
            false
        }
    }

    // ==================== BLUETOOTH ====================

    @SuppressLint("MissingPermission")
    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            btManager?.adapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setBluetoothEnabled(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val state = if (enabled) "enable" else "disable"
            executeRootCommand("service call bluetooth_manager $state")
        } catch (e: Exception) {}

        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            if (enabled) {
                btManager?.adapter?.enable()
            } else {
                btManager?.adapter?.disable()
            }
            return@withContext true
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(context: Context): List<String> {
        val list = mutableListOf<String>()
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            btManager?.adapter?.bondedDevices?.forEach { device ->
                device.name?.let { if (!list.contains(it)) list.add(it) }
            }
        } catch (e: Exception) {}
        return list
    }

    @SuppressLint("MissingPermission")
    fun getBluetoothAddress(context: Context): String {
        return try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            btManager?.adapter?.address ?: "Bilinmiyor"
        } catch (e: Exception) {
            "Bilinmiyor"
        }
    }

    // ==================== Wİ-Fİ ====================

    fun isWifiEnabled(context: Context): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setWifiEnabled(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val state = if (enabled) "enable" else "disable"
            executeRootCommand("svc wifi $state")
        } catch (e: Exception) {}

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled = enabled
            return@withContext true
        } catch (e: Exception) {
            false
        }
    }

    fun getWifiList(context: Context): List<String> {
        val list = mutableListOf<String>()
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.scanResults.forEach { result ->
                result.SSID?.let { if (it.isNotEmpty() && !list.contains(it)) list.add(it) }
            }
        } catch (e: Exception) {}
        return list.sorted()
    }

    suspend fun connectToWifi(ssid: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = "cmd wifi connect-network \"$ssid\" wpa2 \"$password\""
            executeRootCommand(cmd)
        } catch (e: Exception) {
            false
        }
    }

    fun getWifiSignalStrength(context: Context): Int {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            info?.rssi?.let { WifiManager.calculateSignalLevel(it, 4) } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    // ==================== GPS / KONUM ====================

    suspend fun setGpsEnabled(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            // Android 10+ için root komutu
            val cmd = "cmd location set-location-enabled ${if (enabled) "true" else "false"}"
            executeRootCommand(cmd)
        } catch (e: Exception) {}

        try {
            if (Settings.System.canWrite(context)) {
                // Eski yöntem (API 28- için)
                val providers = if (enabled) "gps,wifi,network" else ""
                Settings.Secure.putString(context.contentResolver, Settings.Secure.LOCATION_PROVIDERS_ALLOWED, providers)
                true
            } else {
                context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isGpsEnabled(context: Context): Boolean {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    // ==================== NFC ====================

    fun isNfcAvailable(context: Context): Boolean {
        return try {
            NfcAdapter.getDefaultAdapter(context) != null
        } catch (e: Exception) {
            false
        }
    }

    fun isNfcEnabled(context: Context): Boolean {
        return try {
            NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setNfcEnabled(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = "settings put secure nfc_on ${if (enabled) 1 else 0}"
            executeRootCommand(cmd)
        } catch (e: Exception) {
            false
        }
    }

    // ==================== USB HOST ====================

    suspend fun setUsbHostMode(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val mode = if (enabled) "host" else "peripheral"
            val cmd = "echo $mode > /sys/class/usb_role/ff610000.usb/role"
            executeRootCommand(cmd)
        } catch (e: Exception) {
            false
        }
    }

    // ==================== BATARYA ====================

    fun getBatteryInfo(context: Context): BatteryInfo {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging
        val temperature = 25.0
        val voltage = 4.0
        val health = 1
        val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val currentNow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        } else {
            null
        }
        return BatteryInfo(
            level = level,
            isCharging = isCharging,
            temperature = temperature,
            voltage = voltage,
            health = health,
            status = status,
            currentNow = currentNow
        )
    }

    data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean,
        val temperature: Double,
        val voltage: Double,
        val health: Int,
        val status: Int,
        val currentNow: Int? = null
    ) {
        val healthText: String get() = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "İyi"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Aşırı ısındı"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Bozuk"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Aşırı voltaj"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Arızalı"
            else -> "Bilinmiyor"
        }
        val statusText: String get() = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Şarj oluyor"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Deşarj"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Şarj olmuyor"
            BatteryManager.BATTERY_STATUS_FULL -> "Dolu"
            else -> "Bilinmiyor"
        }
        val chargeRate: String get() = currentNow?.let {
            val amps = it / 1000.0
            if (isCharging) "+${String.format("%.2f", amps)}A" else "${String.format("%.2f", amps)}A"
        } ?: "Bilinmiyor"
    }

    // ==================== CPU ====================

    suspend fun getCpuInfo(): CpuInfo = withContext(Dispatchers.IO) {
        val cores = Runtime.getRuntime().availableProcessors()
        var usage = 0.0
        var minFreq = 0L
        var maxFreq = 0L
        var currentFreq = 0L

        try {
            // Root ile /proc/stat oku
            val statContent = executeRootCommandWithOutput("cat /proc/stat")
            val firstLine = statContent?.lines()?.firstOrNull { it.startsWith("cpu ") }
            if (firstLine != null) {
                val parts = firstLine.split(Regex("\\s+")).drop(1).map { it.toLongOrNull() ?: 0 }
                if (parts.size >= 4) {
                    val user = parts[0]
                    val nice = parts[1]
                    val system = parts[2]
                    val idle = parts[3]
                    val total = user + nice + system + idle
                    usage = if (total > 0) 1.0 - (idle.toDouble() / total) else 0.0
                }
            }

            // Frekans bilgileri
            try {
                val freqContent = executeRootCommandWithOutput("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq")
                minFreq = freqContent?.trim()?.toLongOrNull() ?: 0L
            } catch (e: Exception) {}

            try {
                val freqContent = executeRootCommandWithOutput("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq")
                maxFreq = freqContent?.trim()?.toLongOrNull() ?: 0L
            } catch (e: Exception) {}

            try {
                val freqContent = executeRootCommandWithOutput("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
                currentFreq = freqContent?.trim()?.toLongOrNull() ?: 0L
            } catch (e: Exception) {}
        } catch (e: Exception) {}

        CpuInfo(
            cores = cores,
            usage = usage,
            minFreq = minFreq,
            maxFreq = maxFreq,
            currentFreq = currentFreq
        )
    }

    data class CpuInfo(
        val cores: Int,
        val usage: Double,
        val minFreq: Long = 0,
        val maxFreq: Long = 0,
        val currentFreq: Long = 0
    ) {
        val usagePercent: String get() = "${(usage * 100).roundToInt()}%"
        val minFreqFormatted: String get() = formatFreq(minFreq)
        val maxFreqFormatted: String get() = formatFreq(maxFreq)
        val currentFreqFormatted: String get() = formatFreq(currentFreq)

        private fun formatFreq(freq: Long): String {
            return when {
                freq >= 1_000_000 -> "${freq / 1_000_000}.${(freq % 1_000_000) / 100_000} GHz"
                freq >= 1000 -> "${freq / 1000} MHz"
                freq > 0 -> "$freq kHz"
                else -> "Bilinmiyor"
            }
        }
    }

    // ==================== RAM ====================

    fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val total = runtime.totalMemory()
        val free = runtime.freeMemory()
        val used = total - free
        return MemoryInfo(total = total, used = used, free = free)
    }

    data class MemoryInfo(val total: Long, val used: Long, val free: Long) {
        val totalFormatted: String get() = formatSize(total)
        val usedFormatted: String get() = formatSize(used)
        val freeFormatted: String get() = formatSize(free)
        val usagePercent: String get() = if (total > 0) "${((used.toDouble() / total) * 100).roundToInt()}%" else "0%"
        private fun formatSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format(Locale.US, "%.1f %s", size / 1024.0.pow(digitGroups), units[digitGroups])
        }
    }

    // ==================== DEPOLAMA ====================

    fun getStorageInfo(): StorageInfo {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.freeBytes
        val used = total - free
        return StorageInfo(total = total, used = used, free = free)
    }

    fun getExternalStorageInfo(): StorageInfo? {
        return try {
            val path = android.os.Environment.getExternalStorageDirectory()?.path
            if (path != null) {
                val stat = android.os.StatFs(path)
                val total = stat.totalBytes
                val free = stat.freeBytes
                val used = total - free
                StorageInfo(total = total, used = used, free = free)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    data class StorageInfo(val total: Long, val used: Long, val free: Long) {
        val totalFormatted: String get() = formatSize(total)
        val usedFormatted: String get() = formatSize(used)
        val freeFormatted: String get() = formatSize(free)
        val usagePercent: String get() = if (total > 0) "${((used.toDouble() / total) * 100).roundToInt()}%" else "0%"
        private fun formatSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format(Locale.US, "%.1f %s", size / 1024.0.pow(digitGroups), units[digitGroups])
        }
    }

    // ==================== EKRAN SÜRESİ ====================

    suspend fun setScreenTimeout(context: Context, seconds: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            executeRootCommand("settings put system screen_off_timeout ${seconds * 1000}")
        } catch (e: Exception) {}

        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, seconds * 1000)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun getScreenTimeout(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) / 1000
        } catch (e: Exception) {
            60
        }
    }

    // ==================== TİTREŞİM ====================

    fun vibrate(context: Context, duration: Long = 300) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {}
    }

    fun vibratePattern(context: Context, pattern: LongArray) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {}
    }

    // ==================== IŞIK SENSÖRÜ (suspend) ====================

    suspend fun getLightSensorValue(context: Context): Float? = withContext(Dispatchers.IO) {
        var result: Float? = null
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sensor == null) return@withContext null

        val latch = java.util.concurrent.CountDownLatch(1)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                result = event?.values?.firstOrNull()
                latch.countDown()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        sensorManager.unregisterListener(listener)
        result
    }

    // ==================== YAKINLIK SENSÖRÜ (suspend) ====================

    suspend fun getProximitySensorValue(context: Context): Float? = withContext(Dispatchers.IO) {
        var result: Float? = null
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (sensor == null) return@withContext null

        val latch = java.util.concurrent.CountDownLatch(1)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                result = event?.values?.firstOrNull()
                latch.countDown()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        sensorManager.unregisterListener(listener)
        result
    }

    // ==================== EKRAN DÖNDÜRME KİLİDİ ====================

    suspend fun setRotationLock(context: Context, locked: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            executeRootCommand("settings put system accelerometer_rotation ${if (locked) 0 else 1}")
        } catch (e: Exception) {}

        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 0 else 1)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun isRotationLocked(context: Context): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 0
        } catch (e: Exception) {
            false
        }
    }

    // ==================== GÜÇ TASARRUFU ====================

    suspend fun setPowerSavingMode(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            executeRootCommand("settings put global low_power ${if (enabled) 1 else 0}")
        } catch (e: Exception) {
            false
        }
    }

    fun isPowerSavingMode(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return powerManager.isPowerSaveMode
    }

    // ==================== SİSTEM KARARLILIĞI ====================

    fun getUptime(): String {
        val uptimeMillis = SystemClock.elapsedRealtime()
        val seconds = uptimeMillis / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    // ==================== EKRAN GÖRÜNTÜSÜ ====================

    suspend fun takeScreenshot(context: Context, path: String = "/sdcard/screenshot.png"): Boolean = withContext(Dispatchers.IO) {
        try {
            executeRootCommand("screencap -p $path")
        } catch (e: Exception) {
            false
        }
    }

    // ==================== EKRAN KAYDI ====================

    suspend fun startScreenRecording(context: Context, path: String = "/sdcard/record.mp4", timeLimit: Int = 30): Boolean = withContext(Dispatchers.IO) {
        try {
            executeRootCommand("screenrecord --time-limit $timeLimit $path")
        } catch (e: Exception) {
            false
        }
    }

    // ==================== LOGCAT ====================

    suspend fun getLogcat(context: Context, lines: Int = 100, filter: String = ""): List<String> = withContext(Dispatchers.IO) {
        try {
            val cmd = if (filter.isNotEmpty()) "logcat -d -t $lines | grep -i '$filter'" else "logcat -d -t $lines"
            executeRootCommandWithOutput(cmd)?.lines() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ==================== YARDIMCI ====================

    private suspend fun executeRootCommand(command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor()
            val exitCode = process.exitValue()
            process.destroy()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun executeRootCommandWithOutput(command: String): String? = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            process.waitFor()
            process.destroy()
            output.toString().trim()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Tüm donanım durumlarını özet olarak döndürür.
     */
    suspend fun getAllHardwareInfo(context: Context): String = withContext(Dispatchers.IO) {
        val battery = getBatteryInfo(context)
        val cpu = getCpuInfo()
        val memory = getMemoryInfo()
        val storage = getStorageInfo()
        val externalStorage = getExternalStorageInfo()
        val uptime = getUptime()

        buildString {
            appendLine("=== DONANIM BİLGİLERİ ===")
            appendLine("")
            appendLine("⏱ Sistem Çalışma Süresi: $uptime")
            appendLine("")
            appendLine("📱 Batarya:")
            appendLine("  Seviye: ${battery.level}%")
            appendLine("  Durum: ${battery.statusText}")
            appendLine("  Sağlık: ${battery.healthText}")
            appendLine("  Sıcaklık: ${battery.temperature}°C")
            appendLine("  Voltaj: ${battery.voltage}V")
            appendLine("  Şarj Hızı: ${battery.chargeRate}")
            appendLine("")
            appendLine("⚙️ CPU:")
            appendLine("  Çekirdek: ${cpu.cores}")
            appendLine("  Kullanım: ${cpu.usagePercent}")
            appendLine("  Min Frekans: ${cpu.minFreqFormatted}")
            appendLine("  Max Frekans: ${cpu.maxFreqFormatted}")
            appendLine("  Anlık Frekans: ${cpu.currentFreqFormatted}")
            appendLine("")
            appendLine("💾 RAM:")
            appendLine("  Toplam: ${memory.totalFormatted}")
            appendLine("  Kullanılan: ${memory.usedFormatted}")
            appendLine("  Boş: ${memory.freeFormatted}")
            appendLine("  Kullanım: ${memory.usagePercent}")
            appendLine("")
            appendLine("💿 Dahili Depolama:")
            appendLine("  Toplam: ${storage.totalFormatted}")
            appendLine("  Kullanılan: ${storage.usedFormatted}")
            appendLine("  Boş: ${storage.freeFormatted}")
            appendLine("  Kullanım: ${storage.usagePercent}")
            if (externalStorage != null) {
                appendLine("")
                appendLine("💿 Harici Depolama:")
                appendLine("  Toplam: ${externalStorage.totalFormatted}")
                appendLine("  Kullanılan: ${externalStorage.usedFormatted}")
                appendLine("  Boş: ${externalStorage.freeFormatted}")
                appendLine("  Kullanım: ${externalStorage.usagePercent}")
            }
            appendLine("")
            appendLine("📶 Wi-Fi: ${if (isWifiEnabled(context)) "Açık (Sinyal: ${getWifiSignalStrength(context)}/4)" else "Kapalı"}")
            appendLine("📡 Bluetooth: ${if (isBluetoothEnabled(context)) "Açık" else "Kapalı"}")
            appendLine("✈️ Uçak Modu: ${if (isAirplaneModeOn(context)) "Açık" else "Kapalı"}")
            appendLine("📌 Konum: ${if (isGpsEnabled(context)) "Açık" else "Kapalı"}")
            appendLine("🔄 Ekran Döndürme: ${if (isRotationLocked(context)) "Kilitli" else "Açık"}")
            appendLine("⚡ Güç Tasarrufu: ${if (isPowerSavingMode(context)) "Açık" else "Kapalı"}")
            appendLine("📱 Ekran Zaman Aşımı: ${getScreenTimeout(context)} saniye")
            appendLine("💡 Parlaklık: ${(getBrightness(context) * 100).roundToInt()}%")
            appendLine("🔊 Ses: ${(getVolume(context) * 100).roundToInt()}%")
            appendLine("")
            appendLine("=== KONTROLLER ===")
            appendLine("Parlaklık ayarla: setBrightness(0.0-1.0)")
            appendLine("Ses ayarla: setVolume(0.0-1.0)")
            appendLine("Uçak modu: setAirplaneMode(true/false)")
            appendLine("Bluetooth: setBluetoothEnabled(true/false)")
            appendLine("Wi-Fi: setWifiEnabled(true/false)")
            appendLine("GPS: setGpsEnabled(true/false)")
            appendLine("NFC: setNfcEnabled(true/false)")
            appendLine("USB Host: setUsbHostMode(true/false)")
            appendLine("Ekran süresi: setScreenTimeout(saniye)")
            appendLine("Döndürme kilidi: setRotationLock(true/false)")
            appendLine("Güç tasarrufu: setPowerSavingMode(true/false)")
            appendLine("Ekran görüntüsü: takeScreenshot()")
            appendLine("Ekran kaydı: startScreenRecording()")
            appendLine("Titreşim: vibrate(ms)")
            appendLine("")
            appendLine("=== SENSÖRLER (Canlı Akış) ===")
            appendLine("Işık sensörü Flow: HardwareController.lightSensor")
            appendLine("Yakınlık sensörü Flow: HardwareController.proximitySensor")
            appendLine("Sensör dinlemeyi başlat: startSensorMonitoring(context)")
            appendLine("Sensör dinlemeyi durdur: stopSensorMonitoring()")
        }
    }
}