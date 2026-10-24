@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.windowsui

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TaskbarComponent(
    modifier: Modifier = Modifier,
    onNotificationClick: () -> Unit = {},
    onArrowClick: () -> Unit = {},
    onStartClick: () -> Unit = {},
    onQuickSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDarkTheme = SystemState.theme == "dark"
    
    var currentTime by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    var currentDate by remember { mutableStateOf(SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date())) }
    var batteryLevel by remember { mutableStateOf(50) }
    var isCharging by remember { mutableStateOf(false) }
    var batteryTemp by remember { mutableStateOf(25.0) }
    var notificationCount by remember { mutableStateOf(NotificationState.notifications.size) }
    var wifiEnabled by remember { mutableStateOf(HardwareController.isWifiEnabled(context)) }
    var bluetoothEnabled by remember { mutableStateOf(HardwareController.isBluetoothEnabled(context)) }
    var volumeLevel by remember { mutableStateOf((HardwareController.getVolume(context) * 100).toInt()) }
    
    val devicePersonality = remember {
        runCatching { DeviceInfo.analyzeDevicePersonality(context) }.getOrNull() ?: "Bilinmiyor"
    }
    val spiritEmoji = remember(devicePersonality) { Utils.getSpiritEmoji(devicePersonality) }
    val spiritColor = remember(devicePersonality) { Utils.getSpiritColor(devicePersonality) }
    
    val runningApps = TaskbarState.runningApps
    val pinnedApps = TaskbarState.pinnedApps
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            currentDate = SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date())
            val battery = HardwareController.getBatteryInfo(context)
            batteryLevel = battery.level
            isCharging = battery.isCharging
            batteryTemp = battery.temperature
            notificationCount = NotificationState.notifications.size
            wifiEnabled = HardwareController.isWifiEnabled(context)
            bluetoothEnabled = HardwareController.isBluetoothEnabled(context)
            volumeLevel = (HardwareController.getVolume(context) * 100).toInt()
            TaskbarState.syncAll(context)
            delay(1000)
        }
    }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        color = if (isDarkTheme) {
            Color(0xDF1C1C1C).copy(alpha = 0.85f)
        } else {
            Color(0xDFF0F0F0).copy(alpha = 0.85f)
        },
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // SOL TARAF
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onStartClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Menu, contentDescription = "Başlat", tint = if (isDarkTheme) Color.White else Color.Black)
                }
                
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(spiritColor)
                        .combinedClickable(
                            onClick = {
                                android.widget.Toast.makeText(
                                    context,
                                    "🧘 $devicePersonality",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(spiritEmoji, fontSize = 14.sp)
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.width(200.dp)
                ) {
                    items(pinnedApps) { app ->
                        TaskbarAppIcon(
                            app = app,
                            isRunning = runningApps.any { it.packageName == app.packageName },
                            isDarkTheme = isDarkTheme,
                            onClick = {
                                val existingWindow = WindowState.windows.find { it.contentId == app.packageName }
                                if (existingWindow != null) {
                                    WindowState.focusWindow(existingWindow)
                                    if (existingWindow.isMinimized) {
                                        WindowState.restoreWindow(existingWindow)
                                    }
                                } else {
                                    TaskbarState.runningApps.add(app)
                                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    intent?.let { context.startActivity(intent) }
                                }
                            },
                            onLongClick = {
                                TaskbarState.unpinApp(context, app)
                                SystemState.unpinApp(context, app.packageName)
                            }
                        )
                    }
                }
            }
            
            // ORTA TARAF (Çalışan uygulamalar)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                runningApps.distinctBy { it.packageName }.forEach { app ->
                    val window = WindowState.windows.find { it.contentId == app.packageName }
                    TaskbarRunningApp(
                        app = app,
                        isDarkTheme = isDarkTheme,
                        isActive = window != null && !window.isMinimized,
                        onClick = {
                            val existingWindow = WindowState.windows.find { it.contentId == app.packageName }
                            if (existingWindow != null) {
                                WindowState.focusWindow(existingWindow)
                                if (existingWindow.isMinimized) {
                                    WindowState.restoreWindow(existingWindow)
                                }
                            } else {
                                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                intent?.let { context.startActivity(intent) }
                            }
                        },
                        onClose = {
                            TaskbarState.runningApps.removeAll { it.packageName == app.packageName }
                            WindowState.windows.find { it.contentId == app.packageName }?.let {
                                WindowState.closeWindow(it)
                            }
                        }
                    )
                }
            }
            
            // SAĞ TARAF
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        val allApps = SystemState.allInstalledAppsCache
                        if (allApps.isNotEmpty()) {
                            val randomApp = allApps.randomOrNull()
                            randomApp?.let {
                                TaskbarState.runningApps.add(it)
                                val intent = context.packageManager.getLaunchIntentForPackage(it.packageName)
                                intent?.let { context.startActivity(intent) }
                                android.widget.Toast.makeText(
                                    context,
                                    "✨ ${it.label} açıldı!",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("🔮", fontSize = 16.sp)
                }
                
                IconButton(onClick = { context.openWifiSettings() }, modifier = Modifier.size(28.dp)) {
                    Icon(if (wifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff, contentDescription = "Wi-Fi", tint = if (wifiEnabled) spiritColor else Color.Gray, modifier = Modifier.size(18.dp))
                }
                
                IconButton(onClick = { context.openBluetoothSettings() }, modifier = Modifier.size(28.dp)) {
                    Icon(if (bluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled, contentDescription = "Bluetooth", tint = if (bluetoothEnabled) spiritColor else Color.Gray, modifier = Modifier.size(16.dp))
                }
                
                IconButton(
                    onClick = {
                        val currentVol = HardwareController.getVolume(context)
                        HardwareController.setMute(context, currentVol > 0f)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(if (volumeLevel > 0) Icons.Default.VolumeUp else Icons.Default.VolumeOff, contentDescription = "Ses", tint = if (volumeLevel > 0) { if (isDarkTheme) Color.White else Color.Black } else Color.Gray, modifier = Modifier.size(18.dp))
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (batteryLevel < 20) Color(0xFFE53935).copy(alpha = 0.2f) else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .combinedClickable(
                            onClick = {
                                val info = HardwareController.getBatteryInfo(context)
                                android.widget.Toast.makeText(
                                    context,
                                    "Pil: ${info.level}%\nDurum: ${info.statusText}\nSıcaklık: ${info.temperature}°C\nVoltaj: ${info.voltage}V",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                ) {
                    Icon(if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull, contentDescription = "Pil", tint = when { batteryLevel > 50 -> Color(0xFF4CAF50); batteryLevel > 20 -> Color(0xFFFFA000); else -> Color(0xFFE53935) }, modifier = Modifier.size(18.dp))
                    if (isCharging) Text("⚡", fontSize = 10.sp)
                    if (batteryTemp > 40) Text("🔥", fontSize = 10.sp)
                    Text(text = "$batteryLevel%", color = if (isDarkTheme) Color.White else Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                
                Box(modifier = Modifier.combinedClickable(onClick = onNotificationClick)) {
                    Icon(if (notificationCount > 0) Icons.Default.Notifications else Icons.Default.NotificationsNone, contentDescription = "Bildirimler", tint = if (notificationCount > 0) spiritColor else Color.Gray, modifier = Modifier.size(20.dp))
                    if (notificationCount > 0) {
                        Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE53935)), contentAlignment = Alignment.Center) {
                            Text(text = if (notificationCount > 99) "99+" else notificationCount.toString(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                IconButton(onClick = onQuickSettingsClick, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Hızlı Ayarlar", tint = if (isDarkTheme) Color.White else Color.Black, modifier = Modifier.size(18.dp))
                }
                
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.combinedClickable(onClick = onQuickSettingsClick).padding(horizontal = 4.dp)) {
                    Text(text = currentTime, color = if (isDarkTheme) Color.White else Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(text = currentDate, color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun TaskbarAppIcon(
    app: DesktopApp,
    isRunning: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isRunning -> if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.05f)
                    isHovered -> if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.03f)
                    else -> Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = app.iconBitmap ?: IconManager.getTextIcon(app.label, 24),
            contentDescription = app.label,
            modifier = Modifier.size(24.dp)
        )
        if (isRunning) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .size(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF0078D7))
            )
        }
    }
}

@Composable
private fun TaskbarRunningApp(
    app: DesktopApp,
    isDarkTheme: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isActive -> if (isDarkTheme) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.08f)
                    isHovered -> if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.04f)
                    else -> Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onClose)
            .padding(horizontal = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(
                bitmap = app.iconBitmap ?: IconManager.getTextIcon(app.label, 20),
                contentDescription = app.label,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = app.label,
                color = if (isDarkTheme) Color.White else Color.Black,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(60.dp)
            )
            if (isHovered || isActive) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Kapat",
                    tint = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp).clickable { onClose() }
                )
            }
        }
    }
}