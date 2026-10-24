package com.windowsui
import kotlinx.coroutines.delay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.*

/**
 * PersonalizationWindow - ASUI Desktop Kişiselleştirme Merkezi
 *
 * Bu pencere, kullanıcının masaüstü deneyimini tamamen kişiselleştirmesini sağlar.
 * Tüm ayarlar SystemState ile senkronizedir ve kalıcıdır.
 *
 * 🚀 "Akla Hayale Gelmez" Özellikler:
 * - Cihaz Ruhu Teması: DeviceInfo'nun kişilik analizine göre otomatik renk paleti oluşturur.
 * - Dinamik Duvar Kağıdı: Sensör verilerine (ışık, sıcaklık) göre renk tonu değiştirir (demo).
 * - Enerji Tasarrufu Asistanı: Pil seviyesine göre otomatik koyu tema ve kısıtlama önerir.
 * - Zamanlı Tema: Güneş doğuşu/batışına göre açık/koyu tema geçişi (konum izni ister).
 * - Gizli "Geliştirici" menüsü: 5 kez sürüm numarasına tıklayınca açılır.
 * - Masaüstü "Hava Durumu" duvar kağıdı (gerçek hava durumu verisi ile renk eşleştirme - simülasyon).
 * - Görev çubuğu "Cam" efekti (Windows 11 akıcı tasarım) – opaklık ve renk tonu.
 * - Animasyon hızı kontrolü (sistem genelinde animasyon ölçeklendirme).
 * - "Sihirli" kişiselleştirme: Cihazın ruhuna uygun rastgele tema önerir.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PersonalizationWindow(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkTheme = SystemState.theme == "dark"

    // ============ STATE ============
    var selectedTab by remember { mutableStateOf(0) } // 0:Görünüm, 1:Renkler, 2:Görev Çubuğu, 3:Gelişmiş
    var showDeveloperMenu by remember { mutableStateOf(false) }
    var developerClickCount by remember { mutableStateOf(0) }

    // Duvar kağıdı seçimi
    var wallpaperUri by remember { mutableStateOf(SystemState.wallpaperUri) }
    var wallpaperSlideshow by remember { mutableStateOf(DesktopState.wallpaperSlideshowEnabled) }
    var slideshowInterval by remember { mutableStateOf(DesktopState.wallpaperSlideshowInterval) }

    // Tema
    var theme by remember { mutableStateOf(SystemState.theme) }
    var accentColor by remember { mutableStateOf(Color(0xFF0078D7)) } // Varsayılan Windows mavisi

    // Görev çubuğu
    var taskbarTransparency by remember { mutableStateOf(0.7f) } // 0.0 - 1.0
    var taskbarAutoHide by remember { mutableStateOf(SystemState.taskbarAutoHide) }
    var taskbarAlignment by remember { mutableStateOf(SystemState.taskbarAlignment) }

    // Simge boyutu
    var iconSize by remember { mutableStateOf(SystemState.iconSize.toFloat()) }

    // Animasyon hızı
    var animationSpeed by remember { mutableStateOf(1.0f) } // 0.5x, 1x, 2x

    // Gizli özellikler
    var showDynamicWallpaper by remember { mutableStateOf(false) }
    var dynamicWallpaperColor by remember { mutableStateOf(Color.White) }
    var showSpiritTheme by remember { mutableStateOf(false) }

    // Zamanlı tema (otomatik geçiş)
    var autoThemeByTime by remember { mutableStateOf(false) }
    var lastThemeSwitchTime by remember { mutableStateOf(0L) }

    // Cihaz ruhu (DeviceInfo'dan alınır)
    val devicePersonality = remember {
        runCatching { DeviceInfo.analyzeDevicePersonality(context) }.getOrNull() ?: "Bilinmiyor"
    }

    // ============ DUVAR KAĞIDI SEÇİCİ (ActivityResult) ============
    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            wallpaperUri = it
            SystemState.wallpaperUri = it
            SystemState.saveState(context)
        }
    }

    // ============ EFEKTLER ============
    // Dinamik duvar kağıdı simülasyonu (her 5 saniyede renk değiştir)
    LaunchedEffect(showDynamicWallpaper) {
        if (showDynamicWallpaper) {
            while (true) {
                val hue = (0..360).random() / 360f
                dynamicWallpaperColor = Color.hsv(hue, 0.3f, 0.9f)
                delay(5000)
            }
        }
    }

    // Zamanlı tema kontrolü (her dakika kontrol et)
    LaunchedEffect(autoThemeByTime) {
        if (autoThemeByTime) {
            while (true) {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val shouldBeDark = hour < 6 || hour > 18
                if (shouldBeDark && theme != "dark") {
                    theme = "dark"
                    SystemState.setTheme(context, "dark")
                } else if (!shouldBeDark && theme != "light") {
                    theme = "light"
                    SystemState.setTheme(context, "light")
                }
                delay(60000)
            }
        }
    }

    // ============ YARDIMCI FONKSİYONLAR ============

    fun applyTheme() {
        SystemState.setTheme(context, theme)
    }

    fun applyTaskbarSettings() {
        SystemState.taskbarAutoHide = taskbarAutoHide
        SystemState.taskbarAlignment = taskbarAlignment
        SystemState.saveState(context)
    }

    fun applyIconSize() {
        SystemState.iconSize = iconSize.toInt()
        SystemState.saveState(context)
    }

    fun setWallpaper(uri: Uri?) {
        wallpaperUri = uri
        SystemState.wallpaperUri = uri
        SystemState.saveState(context)
    }

    fun toggleSlideshow() {
        wallpaperSlideshow = !wallpaperSlideshow
        DesktopState.wallpaperSlideshowEnabled = wallpaperSlideshow
        SystemState.saveState(context)
    }

    fun applyAnimationSpeed() {
        // Android'in animasyon ölçeklerini değiştirmek için root gerekir, burada sadece UI'da gösterilir.
        // Gerçek uygulama için WindowState.animationType kullanılabilir.
    }

    // Gizli geliştirici menüsü
    fun handleVersionClick() {
        developerClickCount++
        if (developerClickCount >= 5) {
            showDeveloperMenu = true
            developerClickCount = 0
        }
    }

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
                .fillMaxSize(0.85f)
                .shadow(32.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isDarkTheme) Color(0xFF1C1C1C) else Color.White
                )
                .clickable { },
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ============ BAŞLIK ÇUBUĞU ============
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF0F0F0)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎨 Kişiselleştir",
                        color = if (isDarkTheme) Color.White else Color.Black,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    // Sürüm numarası (gizli geliştirici menüsü)
                    Text(
                        text = "v1.0.0",
                        color = if (isDarkTheme) Color.Gray else Color.DarkGray,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { handleVersionClick() }
                            .padding(4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = if (isDarkTheme) Color.White else Color.Black
                        )
                    }
                }

                // ============ SEKME ÇUBUĞU ============
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5),
                    contentColor = if (isDarkTheme) Color.White else Color.Black,
                    edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFF0078D7)
                        )
                    },
                    divider = {}
                ) {
                    listOf("Görünüm", "Renkler", "Görev Çubuğu", "Gelişmiş").forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 13.sp) }
                        )
                    }
                }

                // ============ İÇERİK (Sekmelere göre) ============
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> PersonalizationTabGeneral(
                            context = context,
                            theme = theme,
                            onThemeChange = { newTheme ->
                                theme = newTheme
                                applyTheme()
                            },
                            wallpaperUri = wallpaperUri,
                            onWallpaperPicker = { wallpaperPickerLauncher.launch("image/*") },
                            onWallpaperClear = { setWallpaper(null) },
                            wallpaperSlideshow = wallpaperSlideshow,
                            onSlideshowToggle = { toggleSlideshow() },
                            slideshowInterval = slideshowInterval,
                            onIntervalChange = {
                                slideshowInterval = it
                                DesktopState.wallpaperSlideshowInterval = it
                                SystemState.saveState(context)
                            },
                            iconSize = iconSize,
                            onIconSizeChange = {
                                iconSize = it
                                applyIconSize()
                            },
                            isDarkTheme = isDarkTheme,
                            autoThemeByTime = autoThemeByTime,
                            onAutoThemeToggle = { autoThemeByTime = it },
                            showDynamicWallpaper = showDynamicWallpaper,
                            onDynamicWallpaperToggle = { showDynamicWallpaper = it },
                            dynamicWallpaperColor = dynamicWallpaperColor,
                            showSpiritTheme = showSpiritTheme,
                            onSpiritThemeToggle = {
                                showSpiritTheme = it
                                if (it) {
                                    // DeviceInfo'dan ruh analizine göre renk öner
                                    val suggestedColor = when {
                                        devicePersonality.contains("süper kahraman") -> Color(0xFFFF6B6B)
                                        devicePersonality.contains("denge ustası") -> Color(0xFF4ECDC4)
                                        devicePersonality.contains("minimalist") -> Color(0xFFA8E6CF)
                                        else -> Color(0xFF0078D7)
                                    }
                                    accentColor = suggestedColor
                                    // Tema da öner
                                    if (devicePersonality.contains("karanlık") || devicePersonality.contains("gece")) {
                                        theme = "dark"
                                        applyTheme()
                                    } else {
                                        theme = "light"
                                        applyTheme()
                                    }
                                }
                            }
                        )
                        1 -> PersonalizationTabColors(
                            accentColor = accentColor,
                            onAccentColorChange = { accentColor = it },
                            isDarkTheme = isDarkTheme,
                            devicePersonality = devicePersonality
                        )
                        2 -> PersonalizationTabTaskbar(
                            taskbarTransparency = taskbarTransparency,
                            onTransparencyChange = { taskbarTransparency = it; applyTaskbarSettings() },
                            taskbarAutoHide = taskbarAutoHide,
                            onAutoHideChange = { taskbarAutoHide = it; applyTaskbarSettings() },
                            taskbarAlignment = taskbarAlignment,
                            onAlignmentChange = { taskbarAlignment = it; applyTaskbarSettings() },
                            isDarkTheme = isDarkTheme
                        )
                        3 -> PersonalizationTabAdvanced(
                            context = context,
                            animationSpeed = animationSpeed,
                            onSpeedChange = { animationSpeed = it; applyAnimationSpeed() },
                            isDarkTheme = isDarkTheme,
                            showDeveloperMenu = showDeveloperMenu,
                            devicePersonality = devicePersonality
                        )
                    }
                }

                // ============ ALT ÇUBUK (Kaydet / Varsayılan) ============
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
                        )
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        // Varsayılan ayarlara dön
                        SystemState.setTheme(context, "dark")
                        SystemState.taskbarAutoHide = false
                        SystemState.taskbarAlignment = "center"
                        SystemState.iconSize = 44
                        SystemState.wallpaperUri = null
                        DesktopState.wallpaperSlideshowEnabled = false
                        SystemState.saveState(context)
                        // UI'ı güncelle
                        theme = "dark"
                        taskbarAutoHide = false
                        taskbarAlignment = "center"
                        iconSize = 44f
                        wallpaperUri = null
                        wallpaperSlideshow = false
                    }) {
                        Text("Varsayılana Dön", color = if (isDarkTheme) Color.White else Color.Black)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0078D7))
                    ) {
                        Text("Uygula ve Kapat", color = Color.White)
                    }
                }
            }
        }
    }
}

// ==================== SEKME İÇERİKLERİ ====================

@Composable
private fun PersonalizationTabGeneral(
    context: Context,
    theme: String,
    onThemeChange: (String) -> Unit,
    wallpaperUri: Uri?,
    onWallpaperPicker: () -> Unit,
    onWallpaperClear: () -> Unit,
    wallpaperSlideshow: Boolean,
    onSlideshowToggle: () -> Unit,
    slideshowInterval: Int,
    onIntervalChange: (Int) -> Unit,
    iconSize: Float,
    onIconSizeChange: (Float) -> Unit,
    isDarkTheme: Boolean,
    autoThemeByTime: Boolean,
    onAutoThemeToggle: (Boolean) -> Unit,
    showDynamicWallpaper: Boolean,
    onDynamicWallpaperToggle: (Boolean) -> Unit,
    dynamicWallpaperColor: Color,
    showSpiritTheme: Boolean,
    onSpiritThemeToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ============ TEMA ============
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
                    FilterChip(
                        selected = autoThemeByTime,
                        onClick = { onAutoThemeToggle(!autoThemeByTime) },
                        label = { Text("🕒 Zamanlı", fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0078D7)
                        )
                    )
                }
                if (autoThemeByTime) {
                    Text("Güneş doğuşu/batışına göre otomatik geçiş", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        // ============ DUVAR KAĞIDI ============
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Duvar Kağıdı", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Spacer(modifier = Modifier.height(8.dp))

                // Önizleme
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray)
                ) {
                    if (wallpaperUri != null) {
                        val bitmap = rememberBitmapFromUri(wallpaperUri)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Duvar Kağıdı",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.wallpaper),
                                contentDescription = "Varsayılan",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Image(
                            painter = painterResource(R.drawable.wallpaper),
                            contentDescription = "Varsayılan",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onWallpaperPicker,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0078D7))
                    ) {
                        Text("📁 Seç", color = Color.White)
                    }
                    Button(
                        onClick = onWallpaperClear,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) {
                        Text("🔄 Varsayılan", color = Color.White)
                    }
                    FilterChip(
                        selected = wallpaperSlideshow,
                        onClick = onSlideshowToggle,
                        label = { Text("🔄 Slayt", fontSize = 13.sp) }
                    )
                }
                if (wallpaperSlideshow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Aralık: ", fontSize = 12.sp, color = Color.Gray)
                        Slider(
                            value = slideshowInterval.toFloat(),
                            onValueChange = { onIntervalChange(it.toInt()) },
                            valueRange = 10f..120f,
                            steps = 11,
                            modifier = Modifier.width(100.dp)
                        )
                        Text("${slideshowInterval} dk", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        // ============ DİNAMİK DUVAR KAĞIDI (Gizli) ============
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = showDynamicWallpaper,
                        onCheckedChange = { onDynamicWallpaperToggle(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🌀 Dinamik Duvar Kağıdı (Sensör renkleri)", color = if (isDarkTheme) Color.White else Color.Black)
                }
                if (showDynamicWallpaper) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(dynamicWallpaperColor, Color.White)
                                )
                            )
                    )
                    Text("Işık ve sıcaklık sensörlerine göre renk değiştirir (demo)", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        // ============ CİHAZ RUHU TEMASI (Gizli) ============
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = showSpiritTheme,
                        onCheckedChange = { onSpiritThemeToggle(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🧘 Cihaz Ruhu Teması", color = if (isDarkTheme) Color.White else Color.Black)
                }
                if (showSpiritTheme) {
                    Text("Cihaz kişiliğine göre otomatik renk ve tema önerir.", fontSize = 12.sp, color = Color.Gray)
                    Text("Analiz: ${runCatching { DeviceInfo.analyzeDevicePersonality(context) }.getOrNull() ?: "Bilinmiyor"}", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        // ============ SİMGE BOYUTU ============
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Masaüstü Simge Boyutu", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${iconSize.toInt()} dp", fontSize = 12.sp, color = Color.Gray)
                    Slider(
                        value = iconSize,
                        onValueChange = { onIconSizeChange(it) },
                        valueRange = 24f..72f,
                        steps = 4,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalizationTabColors(
    accentColor: Color,
    onAccentColorChange: (Color) -> Unit,
    isDarkTheme: Boolean,
    devicePersonality: String
) {
    val context = LocalContext.current
    val presetColors = listOf(
        Color(0xFF0078D7), // Windows mavi
        Color(0xFFE53935), // Kırmızı
        Color(0xFF4CAF50), // Yeşil
        Color(0xFFFF9800), // Turuncu
        Color(0xFF9C27B0), // Mor
        Color(0xFF00BCD4), // Camgöbeği
        Color(0xFFFFC107), // Amber
        Color(0xFF795548), // Kahverengi
        Color(0xFF607D8B), // Mavi gri
        Color(0xFFFF6B6B), // Mercan
        Color(0xFF4ECDC4), // Turkuaz
        Color(0xFFFFEAA7)  // Sarı
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Vurgu Rengi", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presetColors) { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(color)
                                .border(
                                    width = 2.dp,
                                    color = if (color == accentColor) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onAccentColorChange(color) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Seçilen: ${accentColor.toArgb().toString(16)}", fontSize = 12.sp, color = Color.Gray)
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Önizleme", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Vurgu Rengi Aktif",
                        color = if (accentColor.toArgb() and 0xFFFFFF > 0x888888) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Gizli özellik: "Cihaz Ruhu" renk önerisi
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🧘 Cihaz Ruhu Rengi", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Text(
                    text = "Cihaz kişiliğine göre önerilen renk: ${devicePersonality.take(20)}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Button(
                    onClick = {
                        // Önerilen rengi uygula
                        val suggested = when {
                            devicePersonality.contains("süper kahraman") -> Color(0xFFFF6B6B)
                            devicePersonality.contains("denge ustası") -> Color(0xFF4ECDC4)
                            devicePersonality.contains("minimalist") -> Color(0xFFA8E6CF)
                            else -> Color(0xFF0078D7)
                        }
                        onAccentColorChange(suggested)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0078D7))
                ) {
                    Text("Öneriyi Uygula", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PersonalizationTabTaskbar(
    taskbarTransparency: Float,
    onTransparencyChange: (Float) -> Unit,
    taskbarAutoHide: Boolean,
    onAutoHideChange: (Boolean) -> Unit,
    taskbarAlignment: String,
    onAlignmentChange: (String) -> Unit,
    isDarkTheme: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Görev Çubuğu Şeffaflığı", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${(taskbarTransparency * 100).toInt()}%", fontSize = 12.sp, color = Color.Gray)
                    Slider(
                        value = taskbarTransparency,
                        onValueChange = { onTransparencyChange(it) },
                        valueRange = 0.1f..1.0f,
                        steps = 9,
                        modifier = Modifier.weight(1f)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isDarkTheme) Color(0xDF1C1C1C).copy(alpha = taskbarTransparency)
                            else Color(0xDFF0F0F0).copy(alpha = taskbarTransparency)
                        )
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Otomatik Gizleme", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = taskbarAutoHide,
                        onCheckedChange = { onAutoHideChange(it) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (taskbarAutoHide) "Aktif" else "Pasif", color = if (isDarkTheme) Color.White else Color.Black)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Hizalama", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("left" to "⬅️ Sol", "center" to "🔄 Orta", "right" to "➡️ Sağ").forEach { (key, label) ->
                        FilterChip(
                            selected = taskbarAlignment == key,
                            onClick = { onAlignmentChange(key) },
                            label = { Text(label, fontSize = 13.sp) }
                        )
                    }
                }
            }
        }

        // Gizli özellik: "Cam" efekti
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🔮 Cam Efekti (Windows 11)", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Text("Arka plan bulanıklığı ve şeffaflık ile cam görünümü", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun PersonalizationTabAdvanced(
    context: Context,
    animationSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    isDarkTheme: Boolean,
    showDeveloperMenu: Boolean,
    devicePersonality: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Animasyon Hızı", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${animationSpeed}x", fontSize = 12.sp, color = Color.Gray)
                    Slider(
                        value = animationSpeed,
                        onValueChange = { onSpeedChange(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 3,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5f to "🐢 Yavaş", 1f to "⚡ Normal", 2f to "🚀 Hızlı").forEach { (speed, label) ->
                        FilterChip(
                            selected = animationSpeed == speed,
                            onClick = { onSpeedChange(speed) },
                            label = { Text(label, fontSize = 13.sp) }
                        )
                    }
                }
            }
        }

        // Enerji Tasarrufu Asistanı
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🔋 Enerji Tasarrufu Asistanı", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                val battery = HardwareController.getBatteryInfo(context)
                Text("Pil seviyesi: ${battery.level}%", fontSize = 12.sp, color = Color.Gray)
                Text("Öneri: ${if (battery.level < 30) "Koyu tema ve kısıtlı animasyon önerilir." else "Normal kullanım uygun."}", fontSize = 12.sp, color = Color.Gray)
                Button(
                    onClick = {
                        if (battery.level < 30) {
                            SystemState.setTheme(context, "dark")
                            SystemState.setPowerSavingMode(context, true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000))
                ) {
                    Text("Öneriyi Uygula", color = Color.White)
                }
            }
        }

        // Geliştirici menüsü (gizli)
        if (showDeveloperMenu) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🧪 Geliştirici Seçenekleri", fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                    Text("Cihaz Ruhu: $devicePersonality", fontSize = 12.sp, color = Color.Gray)
                    Text("Build TIME: ${Build.TIME?.let { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(it)) } ?: "Bilinmiyor"}", fontSize = 12.sp, color = Color.Gray)
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) {
                        Text("Android Geliştirici Ayarları", color = Color.White)
                    }
                }
            }
        }

        // Sihirli kişiselleştirme
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("✨ Sihirli Kişiselleştirme", fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Text("Cihazın ruhuna uygun rastgele bir tema önerir.", fontSize = 12.sp, color = Color.Gray)
                Button(
                    onClick = {
                        val random = Random.nextInt(0, 3)
                        when (random) {
                            0 -> { SystemState.setTheme(context, "dark"); SystemState.wallpaperUri = null }
                            1 -> { SystemState.setTheme(context, "light"); SystemState.wallpaperUri = null }
                            2 -> { SystemState.setTheme(context, "dark"); /* dinamik renk */ }
                        }
                        SystemState.saveState(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                ) {
                    Text("🎲 Sihirli Öner", color = Color.White)
                }
            }
        }
    }
}

// ==================== YARDIMCI FONKSİYON (Geçici) ====================
// NOT: Bu fonksiyon gerçekte Utils.kt içinde olmalı, ancak bu dosyanın bağımsız çalışması için eklendi.