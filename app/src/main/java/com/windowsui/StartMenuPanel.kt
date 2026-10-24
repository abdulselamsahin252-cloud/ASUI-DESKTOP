package com.windowsui
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.hoverable

import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun StartMenuPanel(
    apps: List<DesktopApp>,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onLockScreen: () -> Unit = {},
    onOpenStore: () -> Unit = {},
    onOpenWindowsApps: () -> Unit = {}
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // ============ STATE ============
    var searchQuery by remember { mutableStateOf("") }
    var contextMenuApp by remember { mutableStateOf<DesktopApp?>(null) }
    var contextMenuOffset by remember { mutableStateOf<Offset?>(null) }

    // Tema
    val isDarkTheme = SystemState.theme == "dark"
    val surfaceColor = if (isDarkTheme) Color(0xFF1C1C1C) else Color(0xFFF3F3F3)
    val cardColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color.White
    val textPrimary = if (isDarkTheme) Color.White else Color.Black
    val textSecondary = if (isDarkTheme) Color.LightGray else Color.DarkGray
    val accentColor = Color(0xFF0078D7)
    val dividerColor = if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0)

    // ============ PROFİL RESMİ ============
    val profilePic = rememberBitmapFromUri(SystemState.profilePicUri)

    // ============ FİLTRELENMİŞ UYGULAMALAR ============
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isEmpty()) {
            apps
        } else {
            apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }
    }

    // ============ SABİTLENMİŞ VE SON UYGULAMALAR ============
    val pinnedAppList = remember(apps, SystemState.pinnedApps) {
        apps.filter { SystemState.pinnedApps.contains(it.packageName) }
    }

    val recentAppList = remember(apps, SystemState.recentApps) {
        apps.filter { SystemState.recentApps.contains(it.packageName) }
    }

    // ============ SANAL MASAÜSTÜ İŞLEMLERİ ============
    fun switchDesktop(delta: Int) {
        val desktops = SystemState.virtualDesktops
        if (desktops.isEmpty()) return
        val currentIndex = desktops.indexOf(SystemState.currentDesktop)
        val newIndex = if (currentIndex + delta < 0) {
            desktops.size - 1
        } else if (currentIndex + delta >= desktops.size) {
            0
        } else {
            currentIndex + delta
        }
        SystemState.switchDesktop(context, desktops[newIndex])
    }

    fun createNewDesktop() {
        SystemState.createVirtualDesktop(context)
    }

    // ============ ANA PANEL ============
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = modifier
                .width(380.dp)
                .heightIn(min = 400.dp, max = 550.dp)
                .shadow(24.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceColor)
                .pointerInput(Unit) { /* Tıklama yayılmasını engelle */ }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ============ ÜST BÖLÜM: KULLANICI PROFİLİ ============
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFE8E8E8)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profil Resmi
                    if (profilePic != null) {
                        Image(
                            bitmap = profilePic,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = SystemState.username.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Kullanıcı Adı
                    Column {
                        Text(
                            text = SystemState.username,
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Kişiselleştir",
                            color = accentColor,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                onClose()
                                onOpenPersonalization()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Ayarlar
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Ayarlar",
                        tint = textSecondary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                onClose()
                                WindowState.openWindow(
                                    id = "settings_${System.currentTimeMillis()}",
                                    title = "Ayarlar",
                                    contentId = "settings",
                                    width = 600,
                                    height = 450
                                )
                            }
                    )
                }

                // ============ ARAMA ÇUBUĞU ============
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFF0F0F0))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(
                            color = textPrimary,
                            fontSize = 13.sp
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { focusManager.clearFocus() }
                        ),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Uygulama ara...",
                                    color = textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Temizle",
                            tint = textSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }

                // ============ DOKUNMATİK EXE KISAYOLU ============
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDarkTheme) Color(0xFF26394A) else Color(0xFFDCEBFA))
                        .clickable {
                            onClose()
                            onOpenWindowsApps()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("EXE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Windows Uygulamaları (.exe)", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Dosya seçme ve içe aktarma", color = textSecondary, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Aç", tint = textSecondary)
                }

                // ============ SABİTLENMİŞ UYGULAMALAR ============
                if (pinnedAppList.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Sabitlenmiş",
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(pinnedAppList) { app ->
                                AppIcon(
                                    app = app,
                                    size = 40,
                                    onClick = {
                                        pm.getLaunchIntentForPackage(app.packageName)
                                            ?.let { context.startActivity(it) }
                                        onClose()
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Divider(color = dividerColor, thickness = 0.5.dp)
                    }
                }

                // ============ SON UYGULAMALAR ============
                if (recentAppList.isNotEmpty() && searchQuery.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Son Kullanılanlar",
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(recentAppList.take(8)) { app ->
                                AppIcon(
                                    app = app,
                                    size = 36,
                                    onClick = {
                                        pm.getLaunchIntentForPackage(app.packageName)
                                            ?.let { context.startActivity(it) }
                                        onClose()
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Divider(color = dividerColor, thickness = 0.5.dp)
                    }
                }

                // ============ TÜM UYGULAMALAR (GRID) ============
                Box(modifier = Modifier.weight(1f)) {
                    if (filteredApps.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Uygulama bulunamadı",
                                color = textSecondary,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps) { app ->
                                AppGridItem(
                                    app = app,
                                    isDarkTheme = isDarkTheme,
                                    textPrimary = textPrimary,
                                    accentColor = accentColor,
                                    onAppClick = {
                                        pm.getLaunchIntentForPackage(app.packageName)
                                            ?.let { context.startActivity(it) }
                                        onClose()
                                    },
                                    onLongPress = { offset ->
                                        contextMenuApp = app
                                        contextMenuOffset = offset
                                    },
                                    onRightClick = { offset ->
                                        contextMenuApp = app
                                        contextMenuOffset = offset
                                    }
                                )
                            }
                        }
                    }
                }

                // ============ ALT BÖLÜM: SANAL MASAÜSTÜ & GÜÇ ============
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFE8E8E8)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Sanal Masaüstü Yöneticisi
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Sol ok
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Önceki masaüstü",
                            tint = textSecondary,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { switchDesktop(-1) }
                        )

                        // Mevcut masaüstü adı (veya ID)
                        val currentDesktopName = SystemState.currentDesktop.takeLast(6)
                        Text(
                            text = if (currentDesktopName.isNotEmpty()) currentDesktopName else "Masaüstü 1",
                            color = textPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.width(70.dp),
                            overflow = TextOverflow.Ellipsis
                        )

                        // Sağ ok
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Sonraki masaüstü",
                            tint = textSecondary,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { switchDesktop(1) }
                        )

                        // Yeni masaüstü oluştur
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Yeni masaüstü",
                            tint = accentColor,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { createNewDesktop() }
                        )
                    }

                    // Güç seçenekleri
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Kilit
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Kilit",
                            tint = textSecondary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    onClose()
                                    onLockScreen()
                                }
                        )
                        // Yeniden Başlat
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Yeniden Başlat",
                            tint = textSecondary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    onClose()
                                    scope.launch {
                                        try {
                                            Runtime.getRuntime()
                                                .exec(arrayOf("su", "-c", "reboot"))
                                        } catch (e: Exception) {}
                                    }
                                }
                        )
                        // Kapat
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "Kapat",
                            tint = Color(0xFFFF5555),
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    onClose()
                                    scope.launch {
                                        try {
                                            Runtime.getRuntime()
                                                .exec(arrayOf("su", "-c", "reboot -p"))
                                        } catch (e: Exception) {
                                            // Root yoksa uygulamayı kapat
                                            Process.killProcess(Process.myPid())
                                        }
                                    }
                                }
                        )
                    }
                }
            }

            // ============ BAĞLAM MENÜSÜ (Sağ Tık / Uzun Basma) ============
            if (contextMenuApp != null && contextMenuOffset != null) {
                Popup(
                    onDismissRequest = { contextMenuApp = null },
                    offset = IntOffset(
                        contextMenuOffset!!.x.toInt(),
                        contextMenuOffset!!.y.toInt()
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .shadow(16.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF3F3F3)
                            )
                            .width(180.dp)
                            .padding(4.dp)
                    ) {
                        val isPinned = SystemState.pinnedApps.contains(contextMenuApp!!.packageName)
                        val isOnDesktop = SystemState.desktopApps.contains(contextMenuApp!!.packageName)

                        Text(
                            text = if (isPinned) "Görev Çubuğundan Kaldır" else "Görev Çubuğuna Sabitle",
                            fontSize = 13.sp,
                            color = textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    if (isPinned) {
                                        SystemState.unpinApp(context, contextMenuApp!!.packageName)
                                        TaskbarState.unpinApp(context, contextMenuApp!!)
                                    } else {
                                        SystemState.pinApp(context, contextMenuApp!!.packageName)
                                        TaskbarState.pinApp(context, contextMenuApp!!)
                                    }
                                    contextMenuApp = null
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )

                        Text(
                            text = if (isOnDesktop) "Masaüstünden Kaldır" else "Masaüstüne Ekle",
                            fontSize = 13.sp,
                            color = textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    if (isOnDesktop) {
                                        SystemState.desktopApps.remove(contextMenuApp!!.packageName)
                                    } else {
                                        SystemState.addDesktopApp(context, contextMenuApp!!.packageName)
                                    }
                                    contextMenuApp = null
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )

                        Divider(color = dividerColor, thickness = 0.5.dp)

                        Text(
                            text = "Uygulama Bilgisi",
                            fontSize = 13.sp,
                            color = textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    contextMenuApp?.let {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                android.net.Uri.parse("package:${it.packageName}")
                                            )
                                        )
                                    }
                                    contextMenuApp = null
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== YARDIMCI BİLEŞENLER ====================

@Composable
private fun AppIcon(
    app: DesktopApp,
    size: Int = 36,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = app.iconBitmap,
            contentDescription = app.label,
            modifier = Modifier.size((size * 0.8).dp)
        )
    }
}

@Composable
private fun AppGridItem(
    app: DesktopApp,
    isDarkTheme: Boolean,
    textPrimary: Color,
    accentColor: Color,
    onAppClick: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onRightClick: (Offset) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(70.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isHovered) accentColor.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .hoverable(interactionSource)
            .pointerInput(app.packageName) {
                detectTapGestures(
                    onLongPress = { offset -> onLongPress(offset) },
                    onTap = { onAppClick() }
                )
            }
            .pointerInput(app.packageName + "_rightclick") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            onRightClick(event.changes.first().position)
                        }
                    }
                }
            }
            .padding(4.dp)
    ) {
        Image(
            bitmap = app.iconBitmap,
            contentDescription = null,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label,
            fontSize = 10.sp,
            color = textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
