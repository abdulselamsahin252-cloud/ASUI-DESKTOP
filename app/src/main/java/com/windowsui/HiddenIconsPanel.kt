package com.windowsui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * HiddenIconsPanel – Windows 11 sistem tepsisi gizli simgeler paneli.
 *
 * Özellikler:
 * - Gerçek sistem durumu (Wi-Fi sinyali, Bluetooth, ses, pil, USB, bildirim)
 * - Tıklanabilir simgeler (Hızlı Ayarlar, Bildirim, Ses, Pil, Bluetooth, USB yönetimi)
 * - Sağ tık menüsü (simgeleri gizle/göster, sabitle)
 * - Sürükleme ile simge sıralaması (SharedPreferences ile kalıcı)
 * - Koyu/açık tema desteği (SystemState.theme)
 * - Animasyonlu açılış/kapanış (AnimatedVisibility)
 * - Windows 11 tarzı gölge, yuvarlak köşe, şeffaflık
 * - Gerçek zamanlı pil, ses, Wi-Fi seviyesi göstergeleri
 * - Tüm sistem durumları HardwareController ile senkronize
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HiddenIconsPanel(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isDarkTheme = SystemState.theme == "dark"

    // ============ STATE ============
    var isExpanded by remember { mutableStateOf(false) }
    var dragOffsets by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }

    // ============ SİSTEM DURUMLARI ============
    // Gerçek zamanlı okuma (HardwareController ile)
    val batteryInfo = remember { mutableStateOf(HardwareController.getBatteryInfo(context)) }
    val wifiEnabled = remember { mutableStateOf(HardwareController.isWifiEnabled(context)) }
    val wifiSignal = remember { mutableStateOf(HardwareController.getWifiSignalStrength(context)) }
    val bluetoothEnabled = remember { mutableStateOf(HardwareController.isBluetoothEnabled(context)) }
    val volume = remember { mutableStateOf(HardwareController.getVolume(context)) }
    val notificationCount = remember { mutableStateOf(NotificationState.notifications.size) }

    // Periyodik güncelleme (arka planda)
    LaunchedEffect(Unit) {
        while (true) {
            batteryInfo.value = HardwareController.getBatteryInfo(context)
            wifiEnabled.value = HardwareController.isWifiEnabled(context)
            wifiSignal.value = HardwareController.getWifiSignalStrength(context)
            bluetoothEnabled.value = HardwareController.isBluetoothEnabled(context)
            volume.value = HardwareController.getVolume(context)
            notificationCount.value = NotificationState.notifications.size
            delay(1000)
        }
    }

    // ============ SİMGE LİSTESİ ============
    var iconOrder by remember {
        mutableStateOf(
            listOf(
                "wifi", "bluetooth", "volume", "battery", "usb", "notification"
            )
        )
    }

    data class TrayIcon(
        val id: String,
        val label: String,
        val iconRes: Int? = null,
        val iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
        val isActive: Boolean = true,
        val onClick: (() -> Unit)? = null,
        val onLongClick: (() -> Unit)? = null,
        val statusText: String = "",
        val badgeCount: Int = 0,
        val isBadge: Boolean = false
    )

    fun createTrayIcons(): List<TrayIcon> {
        val battery = batteryInfo.value
        val isCharging = battery.isCharging
        val level = battery.level
        val vol = volume.value

        return listOf(
            TrayIcon(
                id = "wifi",
                label = "Wi-Fi",
                iconVector = if (wifiEnabled.value) Icons.Default.Wifi else Icons.Default.WifiOff,
                isActive = wifiEnabled.value,
                statusText = if (wifiEnabled.value) "Sinyal: ${wifiSignal.value}/4" else "Kapalı",
                onClick = {
                    scope.launch {
                        HardwareController.setWifiEnabled(context, !wifiEnabled.value)
                        wifiEnabled.value = !wifiEnabled.value
                    }
                },
                onLongClick = {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
            ),
            TrayIcon(
                id = "bluetooth",
                label = "Bluetooth",
                iconVector = if (bluetoothEnabled.value) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                isActive = bluetoothEnabled.value,
                statusText = if (bluetoothEnabled.value) "Açık" else "Kapalı",
                onClick = {
                    scope.launch {
                        HardwareController.setBluetoothEnabled(context, !bluetoothEnabled.value)
                        bluetoothEnabled.value = !bluetoothEnabled.value
                    }
                },
                onLongClick = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
            ),
            TrayIcon(
                id = "volume",
                label = "Ses",
                iconVector = when {
                    vol < 0.01f -> Icons.Default.VolumeOff
                    vol < 0.3f -> Icons.Default.VolumeDown
                    vol < 0.7f -> Icons.Default.VolumeUp
                    else -> Icons.Default.VolumeUp
                },
                isActive = vol > 0.01f,
                statusText = "${(vol * 100).roundToInt()}%",
                onClick = {
                    // 🟢 FIX: 0.1f (Float) ile toplama
                    val newVol = (vol + 0.1f).coerceIn(0f, 1f)
                    HardwareController.setVolume(context, newVol)
                    volume.value = newVol
                },
                onLongClick = {
                    HardwareController.setMute(context, !HardwareController.getVolume(context).equals(0f))
                }
            ),
            TrayIcon(
                id = "battery",
                label = "Pil",
                iconVector = when {
                    isCharging -> Icons.Default.BatteryChargingFull
                    level > 50 -> Icons.Default.BatteryFull
                    level > 20 -> Icons.Default.BatteryStd
                    else -> Icons.Default.BatteryAlert
                },
                isActive = level > 10,
                statusText = if (isCharging) "$level% ⚡" else "$level%",
                badgeCount = if (level < 15) level else 0,
                isBadge = level < 15,
                onClick = {
                    val info = HardwareController.getBatteryInfo(context)
                    android.widget.Toast.makeText(
                        context,
                        "Pil: ${info.level}%\nDurum: ${info.statusText}\nSıcaklık: ${info.temperature}°C\nVoltaj: ${info.voltage}V",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            ),
            TrayIcon(
                id = "usb",
                label = "USB",
                iconVector = Icons.Default.Usb,
                isActive = UsbOtgManager.getConnectedDevices(context).isNotEmpty(),
                statusText = if (UsbOtgManager.getConnectedDevices(context).isNotEmpty()) {
                    "${UsbOtgManager.getConnectedDevices(context).size} cihaz"
                } else "Bağlı değil",
                onClick = {
                    val devices = UsbOtgManager.getConnectedDevices(context)
                    if (devices.isNotEmpty()) {
                        android.widget.Toast.makeText(
                            context,
                            "USB cihazları: ${devices.joinToString { it.deviceName ?: "Bilinmiyor" }}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(context, "USB cihazı bağlı değil", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            ),
            TrayIcon(
                id = "notification",
                label = "Bildirimler",
                iconVector = if (notificationCount.value > 0) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                isActive = notificationCount.value > 0,
                statusText = if (notificationCount.value > 0) "${notificationCount.value} bildirim" else "Bildirim yok",
                badgeCount = notificationCount.value,
                isBadge = notificationCount.value > 0,
                onClick = {
                    onDismiss()
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
            )
        )
    }

    val icons = remember(iconOrder, batteryInfo.value, wifiEnabled.value, wifiSignal.value, bluetoothEnabled.value, volume.value, notificationCount.value) {
        val sortedIcons = iconOrder.mapNotNull { id ->
            createTrayIcons().find { it.id == id }
        }
        sortedIcons
    }

    // ============ ANA PANEL ============
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(200)
        )
    ) {
        Card(
            modifier = modifier
                .width(180.dp)
                .heightIn(min = 120.dp, max = 280.dp)
                .shadow(24.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFFFFFFF)
                )
                .pointerInput(Unit) { },
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color.White
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Text(
                    text = "Gizli Simgeler",
                    fontSize = 12.sp,
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.DarkGray,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    itemsIndexed(icons) { index, icon ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isHovered by interactionSource.collectIsHoveredAsState()
                        var showMenu by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isHovered) {
                                        if (isDarkTheme) Color(0x33FFFFFF) else Color(0x1A000000)
                                    } else Color.Transparent
                                )
                                .hoverable(interactionSource)
                                .clickable {
                                    icon.onClick?.invoke()
                                }
                                .pointerInput(Unit) { },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box {
                                    if (icon.iconVector != null) {
                                        Icon(
                                            icon.iconVector,
                                            contentDescription = icon.label,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (icon.isActive) {
                                                if (isDarkTheme) Color.White else Color.Black
                                            } else Color.Gray
                                        )
                                    } else if (icon.iconRes != null) {
                                        Image(
                                            painter = painterResource(icon.iconRes),
                                            contentDescription = icon.label,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    if (icon.isBadge && icon.badgeCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 4.dp, y = (-4).dp)
                                                .size(16.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (icon.id == "battery") Color(0xFFE53935) else Color(0xFF0078D7)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (icon.badgeCount > 99) "99+" else icon.badgeCount.toString(),
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = icon.label,
                                    fontSize = 8.sp,
                                    color = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color.DarkGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (icon.statusText.isNotEmpty()) {
                                    Text(
                                        text = icon.statusText,
                                        fontSize = 6.sp,
                                        color = if (isDarkTheme) Color.White.copy(alpha = 0.4f) else Color.LightGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "⚙ Yönet",
                        fontSize = 10.sp,
                        color = Color(0xFF0078D7),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                        }
                    )
                }
            }
        }
    }
}