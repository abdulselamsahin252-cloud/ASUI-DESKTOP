package com.windowsui

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

/**
 * DesktopScreen – Windows UI ana masaüstü ekranı.
 *
 * Özellikler:
 * - Duvar kağıdı (kullanıcı seçimi veya varsayılan)
 * - Duvar kağıdı slayt gösterisi (belirli aralıkla değişir, sadece 1'den fazla resim varsa)
 * - Masaüstü simgeleri (DesktopGrid ile)
 * - Pencere Yöneticisi (WindowComponent ile) - pencereler görev çubuğunun arkasında
 * - Görev çubuğu (TaskbarComponent ile) - pencerelerin üstünde
 * - Başlat Menüsü (StartMenuPanel ile)
 * - Bildirim Paneli (NotificationPanel ile)
 * - Hızlı Ayarlar Paneli (QuickSettingsPanel ile)
 * - Gizli Simgeler Paneli (HiddenIconsPanel ile)
 * - Kilit Ekranı (LockScreenPanel ile) - en üst overlay
 * - ASUI Store (StoreWindow ile) - ileride Play Store köprüsü
 * - Kişiselleştirme Penceresi (PersonalizationWindow ile)
 * - Sanal masaüstü desteği
 * - Sağ tık menüsü (boş alan)
 * - Klavye kısayolları (WindowsAccessibilityService ile entegre)
 * - Koyu/açık tema uyumu
 * - Ekran döndürme ve boyutlandırma desteği
 * - Animasyonlu geçişler
 *
 * Katmanlama (Z-Index - Arkadan Öne):
 * 1. Duvar Kağıdı
 * 2. Masaüstü Izgarası (DesktopGrid)
 * 3. Pencereler (WindowComponent)
 * 4. Görev Çubuğu (TaskbarComponent)
 * 5. Açılır Paneller (Başlat, Bildirim, Hızlı Ayarlar, Gizli Simgeler, Sağ Tık Menüsü)
 * 6. Kilit Ekranı (LockScreenPanel - En üst overlay)
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DesktopScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("WindowsUI_Prefs", Context.MODE_PRIVATE) }
    var showFirstRunInfo by remember {
        mutableStateOf(!prefs.getBoolean("part2_first_run_info_acknowledged", false))
    }
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // ============ STATE ============
    // Paneller
    var notificationOpen by remember { mutableStateOf(false) }
    var hiddenIconsOpen by remember { mutableStateOf(false) }
    var startMenuOpen by remember { mutableStateOf(false) }
    var quickSettingsOpen by remember { mutableStateOf(false) }
    var personalizationOpen by remember { mutableStateOf(false) }
    var storeOpen by remember { mutableStateOf(false) }

    // Kilit ekranı
    var isLocked by remember { mutableStateOf(SystemState.lockScreenEnabled) }

    // Sağ tık menüsü (boş alan)
    var desktopContextMenuOffset by remember { mutableStateOf<Offset?>(null) }

    // Duvar kağıdı slayt gösterisi
    var wallpaperIndex by remember { mutableStateOf(0) }
    val wallpapers = listOfNotNull(
        SystemState.wallpaperUri
        // İleride birden fazla duvar kağıdı eklenebilir
    )

    // Duvar kağıdı slayt gösterisi (sadece 1'den fazla resim varsa)
    LaunchedEffect(wallpapers.size) {
        if (wallpapers.size > 1) {
            while (true) {
                delay(30000)
                wallpaperIndex = (wallpaperIndex + 1) % wallpapers.size
            }
        }
    }

    // ============ UYGULAMALARI YÜKLE ============
    val allApps = rememberDesktopApps()

    // ============ MASAÜSTÜ GRID STATE ============
    val gridState = remember { DesktopGridState() }

    // ============ ANA KANVAS ============
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput("DesktopScreenRightClick") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            desktopContextMenuOffset = event.changes.first().position
                            startMenuOpen = false
                            notificationOpen = false
                            quickSettingsOpen = false
                            hiddenIconsOpen = false
                            focusManager.clearFocus()
                        }
                    }
                }
            }
            .pointerInput("DesktopScreenTap") {
                detectTapGestures(
                    onTap = {
                        if (startMenuOpen || notificationOpen || quickSettingsOpen || hiddenIconsOpen) {
                            startMenuOpen = false
                            notificationOpen = false
                            quickSettingsOpen = false
                            hiddenIconsOpen = false
                        }
                        focusManager.clearFocus()
                    }
                )
            }
    ) {
        // ==================== 1. KATMAN: DUVAR KAĞIDI ====================
        val currentWallpaperUri = if (wallpapers.isNotEmpty()) {
            wallpapers[wallpaperIndex % wallpapers.size]
        } else {
            SystemState.wallpaperUri
        }

        val wallpaperBitmap = rememberBitmapFromUri(currentWallpaperUri)
        if (wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap,
                contentDescription = "Wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.wallpaper),
                contentDescription = "Wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // ==================== 2. KATMAN: MASAÜSTÜ IZGARASI ====================
        DesktopGrid(
            apps = allApps,
            modifier = Modifier.fillMaxSize(),
            onOpenWindow = { id, title ->
                WindowState.openWindow(id = id, title = title, contentId = id, width = 600, height = 420)
            },
            onOpenWidget = { widget ->
                // Widget açıldığında
            },
            state = gridState
        )

        // ==================== 3. KATMAN: PENCERELER (WindowComponent) ====================
        // Açık pencereleri göster (sanal masaüstüne göre filtrele)
        val currentDesktopId = SystemState.currentDesktop
        val visibleWindows = WindowState.windows
            .filter { it.desktopId == currentDesktopId || it.desktopId.isEmpty() }
            .filter { !it.isMinimized && !it.isHidden }

        visibleWindows.sortedBy { it.zIndex }.forEach { window ->
            WindowComponent(window)
        }

        // ==================== 4. KATMAN: GÖREV ÇUBUĞU (TaskbarComponent) ====================
        // TaskbarState singleton olarak kullanılıyor (object)
        TaskbarComponent(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            onNotificationClick = {
                notificationOpen = !notificationOpen
                if (notificationOpen) {
                    hiddenIconsOpen = false
                    startMenuOpen = false
                    quickSettingsOpen = false
                }
            },
            onArrowClick = {
                hiddenIconsOpen = !hiddenIconsOpen
                if (hiddenIconsOpen) {
                    notificationOpen = false
                    startMenuOpen = false
                    quickSettingsOpen = false
                }
            },
            onStartClick = {
                startMenuOpen = !startMenuOpen
                if (startMenuOpen) {
                    notificationOpen = false
                    hiddenIconsOpen = false
                    quickSettingsOpen = false
                }
            },
            onQuickSettingsClick = {
                quickSettingsOpen = !quickSettingsOpen
                if (quickSettingsOpen) {
                    notificationOpen = false
                    hiddenIconsOpen = false
                    startMenuOpen = false
                }
            }
        )

        // ==================== 5. KATMAN: AÇILIR PANELLER ====================
        // Bildirim Paneli
        if (notificationOpen) {
            NotificationPanel(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 60.dp)
                    .navigationBarsPadding(),
                onReplyClick = { notification ->
                    // Yanıtlama işlemi
                }
            )
        }

        // Gizli Simgeler Paneli
        if (hiddenIconsOpen) {
            HiddenIconsPanel(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 120.dp, bottom = 58.dp)
                    .navigationBarsPadding()
            )
        }

        // Hızlı Ayarlar Paneli
        if (quickSettingsOpen) {
            QuickSettingsPanel(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 58.dp)
                    .navigationBarsPadding()
            )
        }

        // Başlat Menüsü
        if (startMenuOpen) {
            StartMenuPanel(
                apps = allApps,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 58.dp)
                    .navigationBarsPadding(),
                onClose = { startMenuOpen = false },
                onOpenPersonalization = {
                    startMenuOpen = false
                    personalizationOpen = true
                },
                onLockScreen = {
                    startMenuOpen = false
                    isLocked = true
                },
                onOpenStore = {
                    startMenuOpen = false
                    storeOpen = true
                },
                onOpenWindowsApps = {
                    startMenuOpen = false
                    WindowState.openWindow(
                        id = "windows_apps_${System.currentTimeMillis()}",
                        title = "Windows Uygulamaları",
                        contentId = "windows_apps",
                        width = 720,
                        height = 520
                    )
                }
            )
        }

        // ASUI Store (Play Store köprüsü)
        if (storeOpen) {
            StoreWindow(
                onClose = { storeOpen = false }
            )
        }

        // Kişiselleştirme Penceresi
        if (personalizationOpen) {
            PersonalizationWindow(
                onClose = { personalizationOpen = false }
            )
        }

        // Boş alan sağ tık menüsü
        if (desktopContextMenuOffset != null) {
            DesktopEmptyContextMenu(
                offset = desktopContextMenuOffset!!,
                onDismiss = { desktopContextMenuOffset = null },
                onRefresh = {
                    gridState.applyFiltersAndSort()
                    gridState.saveState(context)
                },
                onSort = { sortBy ->
                    gridState.sortBy = sortBy
                    gridState.applyFiltersAndSort()
                    gridState.saveState(context)
                },
                onNewFolder = {
                    // Yeni klasör oluşturma (ileride)
                },
                onPaste = {
                    // Yapıştır (ileride)
                },
                onOpenTerminal = {
                    WindowState.openWindow(
                        id = "terminal_${System.currentTimeMillis()}",
                        title = "Terminal",
                        contentId = "cmd",
                        width = 650,
                        height = 380
                    )
                },
                onOpenWindowsApps = {
                    desktopContextMenuOffset = null
                    WindowState.openWindow(
                        id = "windows_apps_${System.currentTimeMillis()}",
                        title = "Windows Uygulamaları",
                        contentId = "windows_apps",
                        width = 720,
                        height = 520
                    )
                },
                onOpenTaskManager = {
                    // Görev yöneticisi (ileride)
                },
                onOpenPersonalization = {
                    personalizationOpen = true
                },
                onOpenStore = {
                    storeOpen = true
                },
                onToggleHidden = {
                    gridState.showHidden = !gridState.showHidden
                    gridState.applyFiltersAndSort()
                    gridState.saveState(context)
                }
            )
        }

        // ==================== 6. KATMAN: KİLİT EKRANI (En Üst) ====================
        if (isLocked) {
            LockScreenPanel(
                onUnlock = { password ->
                    if (SystemState.checkLockScreenPassword(password)) {
                        isLocked = false
                    }
                },
                onEmergencyCall = {
                    // Acil durum araması (isteğe bağlı)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000)) // yarı saydam karartma
                    .pointerInput(Unit) {
                        // Arka plana tıklamaları engelle
                    }
            )
        }

        // İlk açılış bilgilendirmesi. Onay tercihi uygulama ayarlarında saklanır.
        if (showFirstRunInfo) {
            AlertDialog(
                onDismissRequest = {
                    prefs.edit().putBoolean("part2_first_run_info_acknowledged", true).apply()
                    showFirstRunInfo = false
                },
                title = { Text("ASUI Desktop — Önemli Bilgilendirme") },
                text = {
                    Column(
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Windows .exe bölümüne masaüstünde sağ tıklayarak veya dokunmatik cihazlarda Başlat menüsündeki ‘Windows Uygulamaları (.exe)’ kısayoluna dokunarak ulaşabilirsiniz.")
                        Text("Hedef, Windows uyumluluk motorunu ASUI içine dahil ederek ayrıca Winlator kurulumuna ihtiyaç bırakmamaktır. Ancak bu Part 2 sürümünde Wine/Box64 ve gerekli sistem dosyaları henüz pakete dahil değildir. Bu nedenle gerçek .exe çalıştırma henüz hazır değildir; mevcut arayüz dosya seçme ve içe aktarma işlemlerini sunar.")
                        Text("ASUI bazı özellikler için Android izinleri talep eder. Android güvenlik kuralları nedeniyle tüm sistem izinleri uygulamaya verilemeyebilir ve bazı erişimler kullanıcı onayı gerektirir. Root erişimi isteyen gelişmiş sistem işlemleri root olmayan cihazlarda çalışmayabilir veya sınırlı çalışabilir. Root olsa bile bazı işlemler Android sürümü ve üretici kısıtlamalarına bağlıdır.")
                        Text("İzinleri yalnızca güvendiğiniz ve ihtiyaç duyduğunuz özellikler için verin. Bir izin reddedilirse ilgili özellik devre dışı kalabilir veya hata verebilir.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit().putBoolean("part2_first_run_info_acknowledged", true).apply()
                        showFirstRunInfo = false
                    }) { Text("Anladım") }
                }
            )
        }
    }
}

// ==================== BOŞ ALAN SAĞ TIK MENÜSÜ ====================

@Composable
private fun DesktopEmptyContextMenu(
    offset: Offset,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSort: (String) -> Unit,
    onNewFolder: () -> Unit,
    onPaste: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenWindowsApps: () -> Unit,
    onOpenTaskManager: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenStore: () -> Unit,
    onToggleHidden: () -> Unit
) {
    val isDarkTheme = SystemState.theme == "dark"
    val backgroundColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF3F3F3)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val dividerColor = if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0)

    Popup(
        onDismissRequest = onDismiss,
        offset = IntOffset(offset.x.toInt(), offset.y.toInt()),
        properties = PopupProperties(clippingEnabled = true)
    ) {
        Column(
            modifier = Modifier
                .shadow(16.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .width(200.dp)
                .padding(4.dp)
        ) {
            val menuItems = listOf(
                "Yenile" to { onRefresh() },
                "---" to null,
                "Sırala (Ada Göre)" to { onSort("name") },
                "Sırala (Türe Göre)" to { onSort("type") },
                "Sırala (Tarihe Göre)" to { onSort("date") },
                "---" to null,
                "Yeni Klasör" to { onNewFolder() },
                "Yapıştır" to { onPaste() },
                "---" to null,
                "Terminali Aç" to { onOpenTerminal() },
                "Windows Uygulamaları (.exe)" to { onOpenWindowsApps() },
                "Görev Yöneticisi" to { onOpenTaskManager() },
                "---" to null,
                "ASUI Store" to { onOpenStore() },
                "Gizli öğeleri göster" to { onToggleHidden() },
                "Kişiselleştir" to { onOpenPersonalization() }
            )

            menuItems.forEach { (label, action) ->
                if (label == "---") {
                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                } else {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                onDismiss()
                                action?.invoke()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
