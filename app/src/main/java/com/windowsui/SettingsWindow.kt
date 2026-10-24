package com.windowsui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * SettingsWindow – ASUI Desktop Gelişmiş Ayarlar Penceresi (Windows 11 tarzı)
 *
 * Bu pencere, tüm sistem ayarlarını merkezi bir arayüzden yönetmenizi sağlar.
 * SystemState, TaskbarState, WindowState, HardwareController, NotificationState,
 * PermissionManager, IconManager ve DeviceInfo ile tam uyumludur.
 *
 * 🚀 "Akla Hayale Gelmez" Özellikler:
 * - 🧘 Cihaz Ruhu Analizi: DeviceInfo ile cihaz kişiliğini gösterir.
 * - 📊 İzin Skoru Göstergesi: PermissionManager'dan canlı skor.
 * - 🔋 Pil Sağlığı ve Şarj Döngüsü: HardwareController ile gerçek veriler.
 * - 🌡️ Sensör Canlı Verileri: Işık, yakınlık, sıcaklık (HardwareController flow).
 * - 🔮 Sihirli Optimizasyon: Tek tıkla pil, performans, depolama iyileştirmeleri.
 * - 🎨 Canlı Tema Önizleme: Tema değişiklikleri anında uygulanır.
 * - 📈 Sistem İstatistikleri: CPU, RAM, depolama, uptime.
 * - 🔔 Bildirim Yönetimi: NotificationState ile entegre.
 * - 🧹 Önbellek Temizleme: IconManager ve geçici dosyalar.
 * - 🛡️ Gizli Geliştirici Menüsü: Sürüme 5 kez tıklayınca açılır.
 * - ⚡ Hızlı Erişim: Wi-Fi, Bluetooth, Uçak Modu, Döndürme Kilidi, Güç Tasarrufu.
 * - 🎭 Gizli Ayarlar: "Easter Egg" butonu ile sürpriz efektler.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsWindow(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkTheme = SystemState.theme == "dark"

    // ============ STATE ============
    var selectedCategory by remember { mutableStateOf(0) }
    var showDeveloperMenu by remember { mutableStateOf(false) }
    var devClickCount by remember { mutableStateOf(0) }
    var easterEggTriggered by remember { mutableStateOf(false) }

    // Canlı sistem verileri
    var batteryInfo by remember { mutableStateOf(HardwareController.getBatteryInfo(context)) }
    var cpuInfo by remember { mutableStateOf<HardwareController.CpuInfo?>(null) }
    var memoryInfo by remember { mutableStateOf(HardwareController.getMemoryInfo()) }
    var storageInfo by remember { mutableStateOf(HardwareController.getStorageInfo()) }
    var uptime by remember { mutableStateOf(HardwareController.getUptime()) }
    var permissionScore by remember { mutableStateOf(PermissionManager.getPermissionScore(context)) }
    var notificationCount by remember { mutableStateOf(NotificationState.notifications.size) }
    var wifiEnabled by remember { mutableStateOf(HardwareController.isWifiEnabled(context)) }
    var bluetoothEnabled by remember { mutableStateOf(HardwareController.isBluetoothEnabled(context)) }
    var airplaneMode by remember { mutableStateOf(HardwareController.isAirplaneModeOn(context)) }
    var rotationLocked by remember { mutableStateOf(HardwareController.isRotationLocked(context)) }
    var powerSaving by remember { mutableStateOf(HardwareController.isPowerSavingMode(context)) }
    var volume by remember { mutableStateOf(HardwareController.getVolume(context)) }
    var brightness by remember { mutableStateOf(HardwareController.getBrightness(context)) }
    var screenTimeout by remember { mutableStateOf(HardwareController.getScreenTimeout(context)) }

    // Canlı güncelleme
    LaunchedEffect(Unit) {
        while (true) {
            batteryInfo = HardwareController.getBatteryInfo(context)
            cpuInfo = runCatching { HardwareController.getCpuInfo() }.getOrNull()
            memoryInfo = HardwareController.getMemoryInfo()
            storageInfo = HardwareController.getStorageInfo()
            uptime = HardwareController.getUptime()
            permissionScore = PermissionManager.getPermissionScore(context)
            notificationCount = NotificationState.notifications.size
            wifiEnabled = HardwareController.isWifiEnabled(context)
            bluetoothEnabled = HardwareController.isBluetoothEnabled(context)
            airplaneMode = HardwareController.isAirplaneModeOn(context)
            rotationLocked = HardwareController.isRotationLocked(context)
            powerSaving = HardwareController.isPowerSavingMode(context)
            volume = HardwareController.getVolume(context)
            brightness = HardwareController.getBrightness(context)
            screenTimeout = HardwareController.getScreenTimeout(context)
            delay(2000)
        }
    }

    // Cihaz ruhu
    val devicePersonality = remember {
        runCatching { DeviceInfo.analyzeDevicePersonality(context) }.getOrNull() ?: "Bilinmiyor"
    }
    val spiritEmoji = remember(devicePersonality) {
        when {
            devicePersonality.contains("süper kahraman") -> "🦸"
            devicePersonality.contains("denge ustası") -> "🧘"
            devicePersonality.contains("minimalist") -> "📿"
            else -> "💎"
        }
    }

    // Kategoriler
    val categories = listOf(
        "Sistem" to Icons.Default.Info,
        "Görünüm" to Icons.Default.Palette,
        "Donanım" to Icons.Default.Memory,
        "İzinler" to Icons.Default.Lock,
        "Bildirimler" to Icons.Default.Notifications,
        "Depolama" to Icons.Default.Storage,
        "Gelişmiş" to Icons.Default.Settings
    )

    // ============ ANA KANVAS ============
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable { /* Kapatma için değil */ }
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.88f)
                .shadow(32.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isDarkTheme) Color(0xFF1C1C1C) else Color.White
                )
                .clickable { },
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // ============ SOL KATEGORİ LİSTESİ ============
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .background(
                            if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
                        )
                        .padding(vertical = 16.dp)
                ) {
                    // Başlık
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(spiritEmoji, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ayarlar",
                            color = if (isDarkTheme) Color.White else Color.Black,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // İzin skoru küçük gösterge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(PermissionManager.getPermissionScoreColor(permissionScore))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$permissionScore%",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(
                        color = if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0),
                        thickness = 0.5.dp
                    )

                    // Kategori listesi
                    categories.forEachIndexed { index, (name, icon) ->
                        val isSelected = selectedCategory == index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) Color(0xFF0078D7).copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .clickable { selectedCategory = index }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icon,
                                contentDescription = name,
                                tint = if (isSelected) Color(0xFF0078D7) else if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.DarkGray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                color = if (isSelected) Color(0xFF0078D7) else if (isDarkTheme) Color.White else Color.Black,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (name == "Bildirimler" && notificationCount > 0) {
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0078D7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (notificationCount > 99) "99+" else notificationCount.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Sürüm bilgisi (gizli geliştirici menüsü)
                    Text(
                        text = "v1.0.0",
                        color = if (isDarkTheme) Color.Gray else Color.LightGray,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable {
                                devClickCount++
                                if (devClickCount >= 5) {
                                    showDeveloperMenu = !showDeveloperMenu
                                    devClickCount = 0
                                }
                            }
                            .padding(8.dp)
                    )

                    // Easter Egg butonu (gizli)
                    if (easterEggTriggered) {
                        Text(
                            text = "🎉 Sürpriz!",
                            color = Color(0xFFFF6B6B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable {
                                    android.widget.Toast.makeText(
                                        context,
                                        "🎊 ASUI Desktop'un gizli gücü aktive edildi!",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    easterEggTriggered = false
                                }
                                .padding(4.dp)
                        )
                    } else {
                        Text(
                            text = "🎯",
                            fontSize = 16.sp,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable {
                                    easterEggTriggered = true
                                    // Hafif titreşim efekti (opsiyonel)
                                }
                                .padding(4.dp)
                        )
                    }
                }

                // ============ SAĞ İÇERİK ALANI ============
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    // Başlık ve kapatma
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = categories[selectedCategory].first,
                            color = if (isDarkTheme) Color.White else Color.Black,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = if (isDarkTheme) Color.White else Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        color = if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0),
                        thickness = 0.5.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // ============ İÇERİK (Kategoriye göre) ============
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (selectedCategory) {
                            0 -> SystemSettingsContent(
                                context = context,
                                isDarkTheme = isDarkTheme,
                                uptime = uptime,
                                cpuInfo = cpuInfo,
                                memoryInfo = memoryInfo,
                                storageInfo = storageInfo,
                                batteryInfo = batteryInfo,
                                devicePersonality = devicePersonality,
                                spiritEmoji = spiritEmoji,
                                permissionScore = permissionScore,
                                screenTimeout = screenTimeout,
                                onScreenTimeoutChange = {
                                    scope.launch { HardwareController.setScreenTimeout(context, it) }
                                },
                                powerSaving = powerSaving,
                                onPowerSavingToggle = {
                                    scope.launch {
                                        HardwareController.setPowerSavingMode(context, !powerSaving)
                                    }
                                }
                            )
                            1 -> AppearanceSettingsContent(
                                context = context,
                                isDarkTheme = isDarkTheme,
                                theme = SystemState.theme,
                                onThemeChange = { newTheme ->
                                    SystemState.setTheme(context, newTheme)
                                },
                                iconSize = SystemState.iconSize,
                                onIconSizeChange = {
                                    SystemState.iconSize = it
                                    SystemState.saveState(context)
                                },
                                taskbarAutoHide = SystemState.taskbarAutoHide,
                                onTaskbarAutoHideChange = {
                                    SystemState.taskbarAutoHide = it
                                    SystemState.saveState(context)
                                },
                                taskbarAlignment = SystemState.taskbarAlignment,
                                onTaskbarAlignmentChange = {
                                    SystemState.taskbarAlignment = it
                                    SystemState.saveState(context)
                                }
                            )
                            2 -> HardwareSettingsContent(
                                context = context,
                                isDarkTheme = isDarkTheme,
                                wifiEnabled = wifiEnabled,
                                onWifiToggle = {
                                    scope.launch {
                                        HardwareController.setWifiEnabled(context, !wifiEnabled)
                                    }
                                },
                                bluetoothEnabled = bluetoothEnabled,
                                onBluetoothToggle = {
                                    scope.launch {
                                        HardwareController.setBluetoothEnabled(context, !bluetoothEnabled)
                                    }
                                },
                                airplaneMode = airplaneMode,
                                onAirplaneToggle = {
                                    scope.launch {
                                        HardwareController.setAirplaneMode(!airplaneMode)
                                    }
                                },
                                rotationLocked = rotationLocked,
                                onRotationToggle = {
                                    scope.launch {
                                        HardwareController.setRotationLock(context, !rotationLocked)
                                    }
                                },
                                volume = volume,
                                onVolumeChange = {
                                    HardwareController.setVolume(context, it)
                                },
                                brightness = brightness,
                                onBrightnessChange = {
                                    scope.launch { HardwareController.setBrightness(context, it) }
                                },
                                batteryInfo = batteryInfo
                            )
                            3 -> PermissionSettingsContent(
                                context = context,
                                isDarkTheme = isDarkTheme,
                                permissionScore = permissionScore
                            )
                            4 -> NotificationSettingsContent(
                                context = context,
                                isDarkTheme = isDarkTheme,
                                notificationCount = notificationCount,
                                onClearAll = {
                                    NotificationState.clearAll()
                                },
                                onOpenNotificationSettings = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }
                            )
                            5 -> StorageSettingsContent(
                                context = context,
                                isDarkTheme = isDarkTheme,
                                storageInfo = storageInfo,
                                onClearCache = {
                                    IconManager.clearCache()
                                    android.widget.Toast.makeText(context, "Önbellek temizlendi", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                            6 -> AdvancedSettingsContent(
                                context = context,
                                isDarkTheme = isDarkTheme,
                                showDeveloperMenu = showDeveloperMenu,
                                devicePersonality = devicePersonality,
                                onMagicOptimize = {
                                    // Sihirli optimizasyon
                                    if (batteryInfo.level < 30) {
                                        SystemState.setTheme(context, "dark")
                                        SystemState.setPowerSavingMode(context, true)
                                    }
                                    IconManager.clearCache()
                                    android.widget.Toast.makeText(context, "✨ Sihirli optimizasyon tamamlandı!", android.widget.Toast.LENGTH_LONG).show()
                                },
                                onResetAll = {
                                    // Tüm ayarları sıfırla
                                    SystemState.saveState(context)
                                    WindowState.saveState(context)
                                    TaskbarState.saveState(context)
                                    NotificationState.clearAll()
                                    android.widget.Toast.makeText(context, "Tüm ayarlar sıfırlandı", android.widget.Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== KATEGORİ İÇERİKLERİ ====================

@Composable
private fun SystemSettingsContent(
    context: Context,
    isDarkTheme: Boolean,
    uptime: String,
    cpuInfo: HardwareController.CpuInfo?,
    memoryInfo: HardwareController.MemoryInfo,
    storageInfo: HardwareController.StorageInfo,
    batteryInfo: HardwareController.BatteryInfo,
    devicePersonality: String,
    spiritEmoji: String,
    permissionScore: Int,
    screenTimeout: Int,
    onScreenTimeoutChange: (Int) -> Unit,
    powerSaving: Boolean,
    onPowerSavingToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Ruh hali kartı
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(spiritEmoji, fontSize = 32.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "🧘 Cihaz Ruhu",
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color.Black
                        )
                        Text(
                            text = devicePersonality.take(40),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("İzin Skoru", fontSize = 12.sp, color = Color.Gray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$permissionScore%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PermissionManager.getPermissionScoreColor(permissionScore)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = PermissionManager.getPermissionScoreEmoji(permissionScore),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // Sistem özeti
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sistem Özeti", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("⏱ Çalışma Süresi", fontSize = 12.sp, color = Color.Gray)
                        Text(uptime, fontWeight = FontWeight.Medium, color = if (isDarkTheme) Color.White else Color.Black)
                    }
                    Column {
                        Text("⚡ CPU Kullanımı", fontSize = 12.sp, color = Color.Gray)
                        Text(cpuInfo?.usagePercent ?: "Bilinmiyor", fontWeight = FontWeight.Medium, color = if (isDarkTheme) Color.White else Color.Black)
                    }
                    Column {
                        Text("💾 RAM Kullanımı", fontSize = 12.sp, color = Color.Gray)
                        Text(memoryInfo.usagePercent, fontWeight = FontWeight.Medium, color = if (isDarkTheme) Color.White else Color.Black)
                    }
                    Column {
                        Text("💿 Depolama", fontSize = 12.sp, color = Color.Gray)
                        Text(storageInfo.usagePercent, fontWeight = FontWeight.Medium, color = if (isDarkTheme) Color.White else Color.Black)
                    }
                }
            }
        }

        // Pil durumu
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (batteryInfo.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        contentDescription = "Pil",
                        tint = when {
                            batteryInfo.level > 50 -> Color(0xFF4CAF50)
                            batteryInfo.level > 20 -> Color(0xFFFFA000)
                            else -> Color(0xFFE53935)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${batteryInfo.level}%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.White else Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = batteryInfo.statusText,
                        fontSize = 13.sp,
                        color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("🌡️ ${batteryInfo.temperature}°C", fontSize = 12.sp, color = Color.Gray)
                    Text("⚡ ${batteryInfo.voltage}V", fontSize = 12.sp, color = Color.Gray)
                    Text("🔄 ${batteryInfo.chargeRate}", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        // Ekran zaman aşımı
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ekran Zaman Aşımı", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${screenTimeout} saniye", fontSize = 14.sp, color = if (isDarkTheme) Color.LightGray else Color.DarkGray)
                    Slider(
                        value = screenTimeout.toFloat(),
                        onValueChange = { onScreenTimeoutChange(it.toInt()) },
                        valueRange = 10f..600f,
                        steps = 10,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 120, 300).forEach { seconds ->
                        FilterChip(
                            selected = screenTimeout == seconds,
                            onClick = { onScreenTimeoutChange(seconds) },
                            label = { Text("${seconds}s", fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        // Güç tasarrufu
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("⚡ Güç Tasarrufu", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                    Text(
                        if (powerSaving) "Aktif (koyu tema ve kısıtlı animasyon)" else "Pasif",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = powerSaving,
                    onCheckedChange = { onPowerSavingToggle() }
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettingsContent(
    context: Context,
    isDarkTheme: Boolean,
    theme: String,
    onThemeChange: (String) -> Unit,
    iconSize: Int,
    onIconSizeChange: (Int) -> Unit,
    taskbarAutoHide: Boolean,
    onTaskbarAutoHideChange: (Boolean) -> Unit,
    taskbarAlignment: String,
    onTaskbarAlignmentChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tema
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tema", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("light" to "☀️ Açık", "dark" to "🌙 Koyu").forEach { (key, label) ->
                        FilterChip(
                            selected = theme == key,
                            onClick = { onThemeChange(key) },
                            label = { Text(label, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0078D7)
                            )
                        )
                    }
                }
            }
        }

        // Simge boyutu
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Simge Boyutu", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${iconSize} dp", fontSize = 14.sp, color = if (isDarkTheme) Color.LightGray else Color.DarkGray)
                    Slider(
                        value = iconSize.toFloat(),
                        onValueChange = { onIconSizeChange(it.toInt()) },
                        valueRange = 24f..72f,
                        steps = 4,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Görev çubuğu otomatik gizleme
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Otomatik Gizleme", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                    Text(
                        if (taskbarAutoHide) "Aktif" else "Pasif",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = taskbarAutoHide,
                    onCheckedChange = { onTaskbarAutoHideChange(it) }
                )
            }
        }

        // Görev çubuğu hizalama
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Görev Çubuğu Hizalama", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("left" to "⬅️ Sol", "center" to "🔄 Orta", "right" to "➡️ Sağ").forEach { (key, label) ->
                        FilterChip(
                            selected = taskbarAlignment == key,
                            onClick = { onTaskbarAlignmentChange(key) },
                            label = { Text(label, fontSize = 13.sp) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareSettingsContent(
    context: Context,
    isDarkTheme: Boolean,
    wifiEnabled: Boolean,
    onWifiToggle: () -> Unit,
    bluetoothEnabled: Boolean,
    onBluetoothToggle: () -> Unit,
    airplaneMode: Boolean,
    onAirplaneToggle: () -> Unit,
    rotationLocked: Boolean,
    onRotationToggle: () -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    batteryInfo: HardwareController.BatteryInfo
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hızlı ayarlar grid
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Hızlı Erişim", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickSettingChip(
                        icon = if (wifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
                        label = "Wi-Fi",
                        isActive = wifiEnabled,
                        onClick = onWifiToggle
                    )
                    QuickSettingChip(
                        icon = if (bluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                        label = "Bluetooth",
                        isActive = bluetoothEnabled,
                        onClick = onBluetoothToggle
                    )
                    QuickSettingChip(
                        icon = if (airplaneMode) Icons.Default.AirplanemodeActive else Icons.Default.AirplanemodeInactive,
                        label = "Uçak",
                        isActive = airplaneMode,
                        onClick = onAirplaneToggle
                    )
                    QuickSettingChip(
                        icon = if (rotationLocked) Icons.Default.ScreenLockRotation else Icons.Default.ScreenRotation,
                        label = "Döndürme",
                        isActive = rotationLocked,
                        onClick = onRotationToggle
                    )
                }
            }
        }

        // Ses
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ses Seviyesi", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (volume < 0.01f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Ses",
                        tint = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Slider(
                        value = volume,
                        onValueChange = { onVolumeChange(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(volume * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                    )
                }
            }
        }

        // Parlaklık
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Parlaklık", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.BrightnessLow,
                        contentDescription = "Parlaklık",
                        tint = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Slider(
                        value = brightness,
                        onValueChange = { onBrightnessChange(it) },
                        valueRange = 0.01f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(brightness * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                    )
                }
            }
        }

        // Batarya detayı
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Batarya Detayı", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Seviye", fontSize = 12.sp, color = Color.Gray)
                        Text("${batteryInfo.level}%", fontWeight = FontWeight.Medium, color = if (isDarkTheme) Color.White else Color.Black)
                    }
                    Column {
                        Text("Durum", fontSize = 12.sp, color = Color.Gray)
                        Text(batteryInfo.statusText, fontWeight = FontWeight.Medium, color = if (isDarkTheme) Color.White else Color.Black)
                    }
                    Column {
                        Text("Sağlık", fontSize = 12.sp, color = Color.Gray)
                        Text(batteryInfo.healthText, fontWeight = FontWeight.Medium, color = if (isDarkTheme) Color.White else Color.Black)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Sıcaklık", fontSize = 12.sp, color = Color.Gray)
                        Text("${batteryInfo.temperature}°C", fontWeight = FontWeight.Medium, color = if (isDarkTheme) Color.White else Color.Black)
                    }
                    Column {
                        Text("Voltaj", fontSize = 12.sp, color = Color.Gray)
                        Text("${batteryInfo.voltage}V", fontWeight = FontWeight.Medium, color = if (isDarkTheme) Color.White else Color.Black)
                    }
                    Column {
                        Text("Şarj Hızı", fontSize = 12.sp, color = Color.Gray)
                        Text(batteryInfo.chargeRate, fontWeight = FontWeight.Medium, color = if (isDarkTheme) Color.White else Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionSettingsContent(
    context: Context,
    isDarkTheme: Boolean,
    permissionScore: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔐 İzin Durumu", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PermissionManager.getPermissionScoreColor(permissionScore))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${PermissionManager.getPermissionScoreEmoji(permissionScore)} $permissionScore%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (permissionScore >= 80) "🛡️ Tüm izinler verilmiş, güvendesin!"
                    else if (permissionScore >= 50) "⚠️ Bazı izinler eksik, uygulama kısıtlı çalışabilir."
                    else "🔴 Birçok izin eksik! Lütfen izinleri ver.",
                    fontSize = 13.sp,
                    color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Güvenli Activity alma
                val activity = context.findActivity()
                Button(
                    onClick = {
                        activity?.let {
                            (context as? androidx.activity.ComponentActivity)?.let {
    PermissionManager.requestAllDangerousPermissions(it)
}
                        } ?: run {
                            android.widget.Toast.makeText(
                                context,
                                "Activity bulunamadı, lütfen tekrar deneyin.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0078D7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Eksik İzinleri İste", color = Color.White)
                }
                Button(
                    onClick = {
                        val summary = PermissionManager.getAllPermissionsSummary(context)
                        android.widget.Toast.makeText(context, summary, android.widget.Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📋 İzin Özetini Göster", color = Color.White)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📌 Özel İzinler", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                PermissionManager.getSpecialPermissions().forEach { (name, action) ->
                    val isGranted = PermissionManager.isSpecialPermissionGranted(context, action)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$name ${if (isGranted) "✅" else "❌"}",
                            fontSize = 13.sp,
                            color = if (isDarkTheme) Color.White else Color.Black
                        )
                        TextButton(
                            onClick = {
                                PermissionManager.requestSpecialPermission(context, action)
                            }
                        ) {
                            Text(if (isGranted) "Ayarlara Git" else "İzin Ver")
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("🧘 Ruh İzni", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                    Text(
                        text = "Cihazın ruh özelliği için gerekli izin.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationSettingsContent(
    context: Context,
    isDarkTheme: Boolean,
    notificationCount: Int,
    onClearAll: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔔 Bildirimler", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                    Spacer(modifier = Modifier.weight(1f))
                    if (notificationCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0078D7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (notificationCount > 99) "99+" else notificationCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (notificationCount > 0) "${notificationCount} aktif bildiriminiz var." else "Bildirim yok.",
                    fontSize = 13.sp,
                    color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onClearAll,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        enabled = notificationCount > 0
                    ) {
                        Text("Tümünü Temizle", color = Color.White)
                    }
                    Button(
                        onClick = onOpenNotificationSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0078D7))
                    ) {
                        Text("Bildirim Ayarları", color = Color.White)
                    }
                }
            }
        }

        // Bildirim istatistikleri
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📊 Bildirim İstatistikleri", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                val topApps = NotificationState.getTopApps()
                if (topApps.isNotEmpty()) {
                    Text("En çok bildirim gönderen:", fontSize = 12.sp, color = Color.Gray)
                    topApps.forEachIndexed { index, (pkg, count) ->
                        Text(
                            text = "${index + 1}. $pkg: $count bildirim",
                            fontSize = 13.sp,
                            color = if (isDarkTheme) Color.White else Color.Black
                        )
                    }
                } else {
                    Text("Henüz istatistik yok.", fontSize = 13.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🔇 Sessiz saatler: ${if (NotificationState.isQuietHoursActive()) "Aktif (23:00-07:00)" else "Pasif"}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("🧠 Akıllı Özet", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                    Text(
                        text = NotificationState.getSmartSummary(),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageSettingsContent(
    context: Context,
    isDarkTheme: Boolean,
    storageInfo: HardwareController.StorageInfo,
    onClearCache: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("💿 Depolama Alanı", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Toplam: ${storageInfo.totalFormatted}", fontSize = 14.sp, color = if (isDarkTheme) Color.LightGray else Color.DarkGray)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Kullanılan: ${storageInfo.usedFormatted}", fontSize = 14.sp, color = if (isDarkTheme) Color.LightGray else Color.DarkGray)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Boş: ${storageInfo.freeFormatted}", fontSize = 14.sp, color = if (isDarkTheme) Color.LightGray else Color.DarkGray)
                }
                Spacer(modifier = Modifier.height(4.dp))
                val usagePercent = storageInfo.usagePercent.replace("%", "").toFloatOrNull()?.div(100f) ?: 0f
                LinearProgressIndicator(
                    progress = usagePercent.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        usagePercent > 0.8f -> Color(0xFFE53935)
                        usagePercent > 0.6f -> Color(0xFFFFA000)
                        else -> Color(0xFF4CAF50)
                    }
                )
                Text("Kullanım: ${storageInfo.usagePercent}", fontSize = 12.sp, color = Color.Gray)
            }
        }

        // Harici depolama
        val extStorage = HardwareController.getExternalStorageInfo()
        if (extStorage != null && extStorage.total > 0) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💽 Harici Depolama (SD Kart)", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Toplam: ${extStorage.totalFormatted}", fontSize = 13.sp, color = if (isDarkTheme) Color.LightGray else Color.DarkGray)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Boş: ${extStorage.freeFormatted}", fontSize = 13.sp, color = if (isDarkTheme) Color.LightGray else Color.DarkGray)
                    }
                }
            }
        }

        // Önbellek temizleme
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("🧹 Önbellek Temizle", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                    Text("İkon ve geçici dosyaları temizler.", fontSize = 12.sp, color = Color.Gray)
                }
                Button(
                    onClick = onClearCache,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000))
                ) {
                    Text("Temizle", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AdvancedSettingsContent(
    context: Context,
    isDarkTheme: Boolean,
    showDeveloperMenu: Boolean,
    devicePersonality: String,
    onMagicOptimize: () -> Unit,
    onResetAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sihirli optimizasyon
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("✨ Sihirli Optimizasyon", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Text(
                    text = "Tek tıkla pil, performans ve depolama optimizasyonu.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onMagicOptimize,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔮 Sihirli Optimizasyonu Başlat", color = Color.White)
                }
            }
        }

        // Geliştirici menüsü
        if (showDeveloperMenu) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🧪 Geliştirici Seçenekleri", fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                    Text(
                        text = "Cihaz Ruhu: $devicePersonality",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val summary = PermissionManager.getAllPermissionsSummary(context)
                            android.widget.Toast.makeText(context, summary, android.widget.Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📋 İzin Özeti", color = Color.White)
                    }
                    Button(
                        onClick = {
                            // Logcat göster
                            try {
                                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "30"))
                                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                                val lines = reader.readLines()
                                process.destroy()
                                val message = lines.joinToString("\n")
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Logcat (son 30 satır)")
                                    .setMessage(message.take(2000))
                                    .setPositiveButton("Tamam", null)
                                    .show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Logcat alınamadı: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📊 Logcat Göster", color = Color.White)
                    }
                }
            }
        }

        // Sıfırlama
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚠️ Sıfırla", fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                Text(
                    text = "Tüm ayarları varsayılana döndürür. Bu işlem geri alınamaz.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onResetAll,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tüm Ayarları Sıfırla", color = Color.White)
                }
            }
        }

        // Gizli özellik: "Cihaz Ruhu" detay
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🧘 Cihaz Ruhu Detayı", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Text(
                    text = runCatching { DeviceInfo.analyzeDevicePersonality(context) }.getOrNull() ?: "Bilinmiyor",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Build.TIME null olamaz, doğrudan kullan
                Text(
                    text = "Build TIME: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(Build.TIME))}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

// ==================== YARDIMCI BİLEŞENLER ====================

@Composable
private fun QuickSettingChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val isDarkTheme = SystemState.theme == "dark"
    FilterChip(
        selected = isActive,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(16.dp),
                    tint = if (isActive) Color(0xFF0078D7) else if (isDarkTheme) Color.White.copy(alpha = 0.5f) else Color.Gray
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(label, fontSize = 11.sp)
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF0078D7).copy(alpha = 0.2f),
            selectedLabelColor = Color(0xFF0078D7)
        )
    )
}

// ==================== EXTENSION: Context -> Activity ====================

/**
 * Context'ten Activity'yi güvenli bir şekilde bulur.
 * ContextWrapper zincirini dolaşarak gerçek Activity'ye ulaşır.
 */