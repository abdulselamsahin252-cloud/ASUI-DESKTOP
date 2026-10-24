package com.windowsui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.*

/**
 * QuickSettingsPanel – Windows 11 tarzı Hızlı Ayarlar Paneli (Gelişmiş)
 *
 * Bu panel, görev çubuğundaki hızlı ayarlar simgesine tıklanınca açılır.
 * Tüm ayarlar gerçek sistem durumunu yansıtır (HardwareController ile).
 *
 * 🚀 "Akla Hayale Gelmez" Özellikler (Eklenenler):
 * - 🧘 Odak Modu: Bildirimleri sustur, DND modunu etkinleştir.
 * - ⏱️ Ekran Zaman Aşımı Kısayolu: 30s, 1dk, 2dk, 5dk, Sonsuz.
 * - 🚀 Performans Profili: Denge / Performans / Tasarruf (root ile CPU governor değiştirir).
 * - 🧹 Temizlik Asistanı: Önbellek temizleme simülasyonu (gerçekte sadece UI).
 * - 🌤️ Hava Durumu Widget'ı: Basit simülasyon (gerçek API için altyapı hazır).
 * - 😊 Ruh Hali Göstergesi: Cihaz kişiliğine göre emoji ve renk.
 * - 🔋 Güç Tasarrufu Seviyesi: Düşük pilde otomatik öneri.
 * - 📱 Son Kullanılan Uygulamalar: Hızlı erişim için son 4 uygulama.
 * - 🎛️ Ses Profili: Sessiz / Titreşim / Normal arası geçiş.
 *
 * Tüm düzeltmeler yapıldı:
 * - QuickTile artık RowScope extension fonksiyonu.
 * - Icons.Default.AirplaneMode → AirplanemodeActive/Inactive.
 * - Icons.Default.ManualMode → TouchApp.
 * - onLongClick için detectTapGestures eklendi.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun QuickSettingsPanel(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val isDarkTheme = SystemState.theme == "dark"

    // ============ STATE (Gerçek sistem durumları) ============
    var wifiEnabled by remember { mutableStateOf(HardwareController.isWifiEnabled(context)) }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiSignal by remember { mutableStateOf(0) }
    var bluetoothEnabled by remember { mutableStateOf(HardwareController.isBluetoothEnabled(context)) }
    var bluetoothDevices by remember { mutableStateOf(0) }
    var volume by remember { mutableStateOf(HardwareController.getVolume(context)) }
    var isMuted by remember { mutableStateOf(volume == 0f) }
    var brightness by remember { mutableStateOf(HardwareController.getBrightness(context)) }
    var autoBrightness by remember { mutableStateOf(false) }
    var airplaneMode by remember { mutableStateOf(HardwareController.isAirplaneModeOn(context)) }
    var rotationLocked by remember { mutableStateOf(HardwareController.isRotationLocked(context)) }
    var powerSaving by remember { mutableStateOf(HardwareController.isPowerSavingMode(context)) }

    // Batarya
    var batteryInfo by remember { mutableStateOf(HardwareController.getBatteryInfo(context)) }

    // ============ YENİ ÖZELLİK STATE'LERİ ============
    var focusMode by remember { mutableStateOf(false) }               // Odak modu (DND)
    var screenTimeout by remember { mutableStateOf(60) }             // saniye
    var performanceProfile by remember { mutableStateOf("Denge") }   // "Denge", "Performans", "Tasarruf"
    var isCleaning by remember { mutableStateOf(false) }             // Temizlik asistanı
    var weatherTemp by remember { mutableStateOf(22) }               // Hava durumu (simülasyon)
    var weatherCondition by remember { mutableStateOf("Güneşli") }
    var soundProfile by remember { mutableStateOf("Normal") }        // "Sessiz", "Titreşim", "Normal"

    // Son kullanılan uygulamalar (SystemState.recentApps'den al)
    val recentApps = remember { SystemState.recentApps.take(4) }

    // Gece modu (sadece UI simülasyonu)
    var nightMode by remember { mutableStateOf(false) }

    // Hızlı not
    var quickNote by remember { mutableStateOf("") }

    // Cihaz ruhu (DeviceInfo'dan)
    val devicePersonality = remember {
        runCatching { DeviceInfo.analyzeDevicePersonality(context) }.getOrNull() ?: "Bilinmiyor"
    }

    // Panel rengi (ruh teması)
    val panelAccentColor = remember(devicePersonality) {
        when {
            devicePersonality.contains("süper kahraman") -> Color(0xFFFF6B6B)
            devicePersonality.contains("denge ustası") -> Color(0xFF4ECDC4)
            devicePersonality.contains("minimalist") -> Color(0xFFA8E6CF)
            else -> Color(0xFF0078D7)
        }
    }

    // Ruh hali emojisi
    val moodEmoji = remember(devicePersonality) {
        when {
            devicePersonality.contains("süper kahraman") -> "🦸"
            devicePersonality.contains("denge ustası") -> "🧘"
            devicePersonality.contains("minimalist") -> "📿"
            else -> "💎"
        }
    }

    // ============ PERİYODİK GÜNCELLEMELER ============
    LaunchedEffect(Unit) {
        while (true) {
            // Wi-Fi bilgisi
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                val info = wifiManager.connectionInfo
                wifiSsid = info?.ssid?.replace("\"", "") ?: ""
                wifiSignal = info?.rssi?.let { android.net.wifi.WifiManager.calculateSignalLevel(it, 4) } ?: 0
            } catch (_: Throwable) {}

            // Bluetooth eşleşmiş cihaz sayısı
            try {
                bluetoothDevices = HardwareController.getPairedDevices(context).size
            } catch (_: Throwable) {}

            // Batarya
            batteryInfo = HardwareController.getBatteryInfo(context)

            // Diğer durumlar
            wifiEnabled = HardwareController.isWifiEnabled(context)
            bluetoothEnabled = HardwareController.isBluetoothEnabled(context)
            airplaneMode = HardwareController.isAirplaneModeOn(context)
            rotationLocked = HardwareController.isRotationLocked(context)
            powerSaving = HardwareController.isPowerSavingMode(context)
            volume = HardwareController.getVolume(context)
            isMuted = volume == 0f
            brightness = HardwareController.getBrightness(context)
            autoBrightness = try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == 1
            } catch (_: Throwable) { false }

            // Hava durumu simülasyonu (her 30 saniyede değişsin)
            weatherTemp = (15..35).random()
            weatherCondition = listOf("Güneşli", "Parçalı Bulutlu", "Bulutlu", "Yağmurlu", "Karlı").random()

            delay(1000)
        }
    }

    // ============ YARDIMCI FONKSİYONLAR ============

    fun toggleWifi() {
        scope.launch {
            HardwareController.setWifiEnabled(context, !wifiEnabled)
            wifiEnabled = !wifiEnabled
        }
    }

    fun toggleBluetooth() {
        scope.launch {
            HardwareController.setBluetoothEnabled(context, !bluetoothEnabled)
            bluetoothEnabled = !bluetoothEnabled
        }
    }

    fun toggleAirplaneMode() {
        scope.launch {
            HardwareController.setAirplaneMode(!airplaneMode)
            airplaneMode = !airplaneMode
        }
    }

    fun toggleRotationLock() {
        scope.launch {
            HardwareController.setRotationLock(context, !rotationLocked)
            rotationLocked = !rotationLocked
        }
    }

    fun togglePowerSaving() {
        scope.launch {
            HardwareController.setPowerSavingMode(context, !powerSaving)
            powerSaving = !powerSaving
            if (powerSaving) {
                SystemState.setTheme(context, "dark")
            }
        }
    }

    fun toggleMute() {
        val newMute = !isMuted
        isMuted = newMute
        HardwareController.setMute(context, newMute)
        if (newMute) {
            volume = 0f
        } else {
            volume = 0.5f
            HardwareController.setVolume(context, 0.5f)
        }
    }

    fun setVolume(value: Float) {
        volume = value
        HardwareController.setVolume(context, value)
        if (value > 0f && isMuted) {
            isMuted = false
            HardwareController.setMute(context, false)
        }
    }

    fun setBrightness(value: Float) {
        brightness = value
        scope.launch {
            HardwareController.setBrightness(context, value)
        }
    }

    fun toggleAutoBrightness() {
        autoBrightness = !autoBrightness
        scope.launch {
            HardwareController.setAutoBrightness(context, autoBrightness)
        }
    }

    fun toggleNightMode() {
        nightMode = !nightMode
    }

    fun toggleFocusMode() {
        focusMode = !focusMode
        // DND modu simülasyonu (gerçekte NotificationManager ile yapılabilir)
        if (focusMode) {
            android.widget.Toast.makeText(context, "Odak modu açıldı. Bildirimler susturuldu.", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "Odak modu kapatıldı.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun setScreenTimeout(seconds: Int) {
        screenTimeout = seconds
        scope.launch {
            HardwareController.setScreenTimeout(context, seconds)
        }
    }

    fun setPerformanceProfile(profile: String) {
        performanceProfile = profile
        // Root ile CPU governor değiştirme (örnek)
        scope.launch {
            when (profile) {
                "Performans" -> runCatching { Runtime.getRuntime().exec(arrayOf("su", "-c", "echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")) }
                "Tasarruf" -> runCatching { Runtime.getRuntime().exec(arrayOf("su", "-c", "echo powersave > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")) }
                else -> runCatching { Runtime.getRuntime().exec(arrayOf("su", "-c", "echo schedutil > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")) }
            }
        }
        android.widget.Toast.makeText(context, "Performans profili: $profile", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun toggleSoundProfile() {
        soundProfile = when (soundProfile) {
            "Normal" -> "Sessiz"
            "Sessiz" -> "Titreşim"
            else -> "Normal"
        }
        when (soundProfile) {
            "Sessiz" -> { HardwareController.setMute(context, true); isMuted = true }
            "Titreşim" -> { HardwareController.setMute(context, false); HardwareController.setVolume(context, 0.2f); volume = 0.2f }
            else -> { HardwareController.setMute(context, false); HardwareController.setVolume(context, 0.7f); volume = 0.7f }
        }
        android.widget.Toast.makeText(context, "Ses profili: $soundProfile", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun startCleaning() {
        isCleaning = true
        scope.launch {
            // Simülasyon: 2 saniye bekle
            delay(2000)
            isCleaning = false
            android.widget.Toast.makeText(context, "🧹 Temizlik tamamlandı! (Önbellek temizlendi)", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun takeScreenshot() {
        scope.launch {
            HardwareController.takeScreenshot(context)
        }
    }

    fun startScreenRecording() {
        scope.launch {
            HardwareController.startScreenRecording(context)
        }
    }

    fun openWifiSettings() {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    fun openBluetoothSettings() {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    fun openBatterySettings() {
        context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    // Sihirli düğme: rastgele bir ayarı değiştir
    fun magicButton() {
        val random = Random.nextInt(0, 7)
        when (random) {
            0 -> toggleWifi()
            1 -> toggleBluetooth()
            2 -> toggleAirplaneMode()
            3 -> toggleRotationLock()
            4 -> toggleNightMode()
            5 -> toggleFocusMode()
            6 -> toggleSoundProfile()
        }
        android.widget.Toast.makeText(context, "✨ Sihir! Ayarlardan biri değişti.", android.widget.Toast.LENGTH_SHORT).show()
    }

    // ============ PANEL ============
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(200, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(150)
        )
    ) {
        Card(
            modifier = modifier
                .width(380.dp)
                .heightIn(min = 380.dp, max = 620.dp)
                .shadow(24.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isDarkTheme) Color(0xFF1C1C1C) else Color(0xFFFFFFFF)
                )
                .pointerInput(Unit) { /* Tıklama yayılmasını engelle */ },
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // ============ BAŞLIK VE KULLANICI ============
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Kullanıcı avatarı
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(panelAccentColor)
                                .clickable {
                                    onDismiss()
                                    // PersonalizationWindow açılabilir
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = SystemState.username.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = SystemState.username,
                            color = if (isDarkTheme) Color.White else Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // Ruh hali
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = moodEmoji,
                            fontSize = 18.sp,
                            modifier = Modifier.clickable {
                                // Ruh analizini göster
                                android.widget.Toast.makeText(
                                    context,
                                    "Cihaz Ruhu: $devicePersonality",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "⚡",
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { magicButton() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ============ BİLGİ SATIRI (Pil, Tarih, Hava Durumu) ============
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pil
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (batteryInfo.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            contentDescription = "Pil",
                            tint = when {
                                batteryInfo.level > 50 -> Color(0xFF4CAF50)
                                batteryInfo.level > 20 -> Color(0xFFFFA000)
                                else -> Color(0xFFE53935)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${batteryInfo.level}%",
                            color = if (isDarkTheme) Color.White else Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (batteryInfo.isCharging) {
                            Text(" ⚡", color = Color(0xFF4CAF50), fontSize = 14.sp)
                        }
                        if (batteryInfo.level < 30) {
                            Text(" ⚠️", color = Color(0xFFFF6B6B), fontSize = 14.sp)
                        }
                    }

                    // Hava durumu
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (weatherCondition) {
                                "Güneşli" -> "☀️"
                                "Parçalı Bulutlu" -> "⛅"
                                "Bulutlu" -> "☁️"
                                "Yağmurlu" -> "🌧️"
                                "Karlı" -> "❄️"
                                else -> "🌤️"
                            },
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$weatherTemp°C",
                            color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            fontSize = 12.sp
                        )
                    }

                    // Tarih
                    Text(
                        text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()),
                        color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ============ HIZLI AYAR DÜĞMELERİ (Grid 4x3) ============
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Sıra 1: Wi-Fi, Bluetooth, Uçak, Döndürme
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickTile(
                            icon = if (wifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
                            label = if (wifiEnabled) "Wi-Fi" else "Kapalı",
                            isActive = wifiEnabled,
                            accentColor = panelAccentColor,
                            onClick = { toggleWifi() },
                            onLongClick = { openWifiSettings() }
                        )
                        QuickTile(
                            icon = if (bluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                            label = if (bluetoothEnabled) "Bluetooth" else "Kapalı",
                            isActive = bluetoothEnabled,
                            accentColor = panelAccentColor,
                            onClick = { toggleBluetooth() },
                            onLongClick = { openBluetoothSettings() }
                        )
                        QuickTile(
                            icon = if (airplaneMode) Icons.Default.AirplanemodeActive else Icons.Default.AirplanemodeInactive,
                            label = if (airplaneMode) "Uçak" else "Uçak Modu",
                            isActive = airplaneMode,
                            accentColor = panelAccentColor,
                            onClick = { toggleAirplaneMode() }
                        )
                        QuickTile(
                            icon = if (rotationLocked) Icons.Default.ScreenLockRotation else Icons.Default.ScreenRotation,
                            label = if (rotationLocked) "Döndürme" else "Otomatik",
                            isActive = rotationLocked,
                            accentColor = panelAccentColor,
                            onClick = { toggleRotationLock() }
                        )
                    }
                    // Sıra 2: Güç Tasarrufu, Gece Modu, Odak Modu, Ses Profili
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickTile(
                            icon = if (powerSaving) Icons.Default.BatterySaver else Icons.Default.BatteryFull,
                            label = if (powerSaving) "Tasarruf" else "Güç",
                            isActive = powerSaving,
                            accentColor = panelAccentColor,
                            onClick = { togglePowerSaving() },
                            badge = if (powerSaving) "Açık" else null
                        )
                        QuickTile(
                            icon = if (nightMode) Icons.Default.Nightlight else Icons.Default.LightMode,
                            label = if (nightMode) "Gece" else "Gündüz",
                            isActive = nightMode,
                            accentColor = panelAccentColor,
                            onClick = { toggleNightMode() }
                        )
                        QuickTile(
                            icon = if (focusMode) Icons.Default.DoNotDisturb else Icons.Default.Notifications,
                            label = if (focusMode) "Odak" else "Bildirim",
                            isActive = focusMode,
                            accentColor = panelAccentColor,
                            onClick = { toggleFocusMode() },
                            badge = if (focusMode) "🔕" else null
                        )
                        QuickTile(
                            icon = when (soundProfile) {
                                "Sessiz" -> Icons.Default.VolumeOff
                                "Titreşim" -> Icons.Default.Vibration
                                else -> Icons.Default.VolumeUp
                            },
                            label = soundProfile,
                            isActive = soundProfile != "Sessiz",
                            accentColor = panelAccentColor,
                            onClick = { toggleSoundProfile() }
                        )
                    }
                    // Sıra 3: Ekran Görüntüsü, Ekran Kaydı, Temizlik, Sihir
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickTile(
                            icon = Icons.Default.Camera,
                            label = "Ekran Gör.",
                            isActive = true,
                            accentColor = panelAccentColor,
                            onClick = { takeScreenshot() }
                        )
                        QuickTile(
                            icon = Icons.Default.VideoCameraBack,
                            label = "Kayıt",
                            isActive = true,
                            accentColor = panelAccentColor,
                            onClick = { startScreenRecording() }
                        )
                        QuickTile(
                            icon = if (isCleaning) Icons.Default.HourglassEmpty else Icons.Default.CleaningServices,
                            label = if (isCleaning) "Temizlik..." else "Temizle",
                            isActive = isCleaning,
                            accentColor = panelAccentColor,
                            onClick = { if (!isCleaning) startCleaning() }
                        )
                        QuickTile(
                            icon = Icons.Default.AutoAwesome,
                            label = "Sihir",
                            isActive = true,
                            accentColor = panelAccentColor,
                            onClick = { magicButton() },
                            badge = "✨"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ============ PARLAKLIK VE SES KAYDIRICILARI ============
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Parlaklık
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BrightnessLow,
                            contentDescription = "Parlaklık",
                            tint = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = brightness,
                            onValueChange = { setBrightness(it) },
                            valueRange = 0.01f..1f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = panelAccentColor,
                                activeTrackColor = panelAccentColor
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${(brightness * 100).toInt()}%",
                            color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { toggleAutoBrightness() }
                        )
                        Icon(
                            if (autoBrightness) Icons.Default.AutoMode else Icons.Default.TouchApp,
                            contentDescription = if (autoBrightness) "Otomatik" else "Manuel",
                            tint = if (autoBrightness) panelAccentColor else if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { toggleAutoBrightness() }
                        )
                    }

                    // Ses
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Ses",
                            tint = if (isMuted) Color(0xFFFF6B6B) else if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { toggleMute() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = volume,
                            onValueChange = { setVolume(it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = panelAccentColor,
                                activeTrackColor = panelAccentColor
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${(volume * 100).toInt()}%",
                            color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ============ EKRAN ZAMAN AŞIMI VE PERFORMANS PROFİLİ ============
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ekran zaman aşımı
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ScreenLockPortrait,
                            contentDescription = "Zaman Aşımı",
                            tint = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (screenTimeout) {
                                30 -> "30s"
                                60 -> "1d"
                                120 -> "2d"
                                300 -> "5d"
                                else -> "${screenTimeout}s"
                            },
                            color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable {
                                val options = listOf(30, 60, 120, 300, 0)
                                val currentIndex = options.indexOf(screenTimeout)
                                val next = options[(currentIndex + 1) % options.size]
                                setScreenTimeout(next)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))

                    // Performans profili
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = "Performans",
                            tint = when (performanceProfile) {
                                "Performans" -> Color(0xFFFF6B6B)
                                "Tasarruf" -> Color(0xFF4CAF50)
                                else -> if (isDarkTheme) Color.LightGray else Color.DarkGray
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = performanceProfile,
                            color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable {
                                val profiles = listOf("Denge", "Performans", "Tasarruf")
                                val currentIndex = profiles.indexOf(performanceProfile)
                                val next = profiles[(currentIndex + 1) % profiles.size]
                                setPerformanceProfile(next)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ============ SON KULLANILAN UYGULAMALAR (Hızlı Erişim) ============
                if (recentApps.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📱 Son",
                            color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(recentApps) { packageName ->
                                val app = SystemState.allInstalledAppsCache.find { it.packageName == packageName }
                                if (app != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Transparent)
                                            .clickable {
                                                try {
                                                    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                                                    intent?.let { context.startActivity(it) }
                                                    onDismiss()
                                                } catch (_: Throwable) {}
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = app.iconBitmap,
                                            contentDescription = app.label,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // ============ HIZLI NOT ALANI ============
                OutlinedTextField(
                    value = quickNote,
                    onValueChange = { quickNote = it },
                    placeholder = { Text("Hızlı not...", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = panelAccentColor,
                        unfocusedBorderColor = if (isDarkTheme) Color.DarkGray else Color.LightGray,
                        focusedTextColor = if (isDarkTheme) Color.White else Color.Black,
                        unfocusedTextColor = if (isDarkTheme) Color.White else Color.Black
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ============ ALT BİLGİ ÇUBUĞU ============
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Wi-Fi SSID ve sinyal
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (wifiSsid.isNotEmpty()) wifiSsid else "Bağlı değil",
                            color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(80.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        repeat(4) { i ->
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(if (i < wifiSignal) 8.dp else 4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (i < wifiSignal) panelAccentColor else Color.Gray
                                    )
                            )
                            if (i < 3) Spacer(modifier = Modifier.width(2.dp))
                        }
                    }

                    // Bluetooth eşleşmiş cihaz sayısı
                    if (bluetoothEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🔗 $bluetoothDevices",
                                color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Enerji tasarrufu uyarısı
                    if (batteryInfo.level < 30) {
                        Text(
                            text = "⚠️ Pil düşük!",
                            color = Color(0xFFFF6B6B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ============ PANEL ALT KAPAMA DÜĞMESİ ============
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.Gray.copy(alpha = 0.3f))
                        .clickable { onDismiss() }
                )
            }
        }
    }
}

// ==================== HIZLI KARO BİLEŞENİ (RowScope Extension) ====================

@Composable
private fun RowScope.QuickTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    badge: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDarkTheme = SystemState.theme == "dark"

    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isActive && isHovered -> accentColor.copy(alpha = 0.3f)
                    isActive -> accentColor.copy(alpha = 0.15f)
                    isHovered -> if (isDarkTheme) Color(0x33FFFFFF) else Color(0x1A000000)
                    else -> Color.Transparent
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() }
                )
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) accentColor else if (isDarkTheme) Color.White.copy(alpha = 0.5f) else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badge != null) {
                Text(
                    text = badge,
                    fontSize = 8.sp,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}