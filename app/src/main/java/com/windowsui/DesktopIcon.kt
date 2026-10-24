package com.windowsui
import kotlin.math.roundToInt

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch

/**
 * DesktopIcon – Masaüstündeki tek bir simge bileşeni.
 *
 * Özellikler:
 * - Sürükle-bırak (drag & drop)
 * - Tek tıklama ile seçme
 * - Çift tıklama ile uygulama açma
 * - Yavaş tıklama ile yeniden adlandırma
 * - Sağ tık / uzun basma ile bağlam menüsü
 * - Hover efekti
 * - Klasör, widget, sistem öğesi desteği
 * - Koyu/açık tema uyumu
 * - Animasyonlu açılış/kapanış
 * - Menü taşması engeli
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DesktopIcon(
    item: DesktopGridItem,
    state: DesktopGridState,
    apps: List<DesktopApp>,
    onOpenWindow: (String, String) -> Unit,
    onOpenWidget: (DesktopWidgetData) -> Unit = {}
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val isSelected = state.isSelected(item.id)
    val isRenaming = state.renamingId == item.id
    val isDarkTheme = SystemState.theme == "dark"

    var lastTapTime by remember { mutableStateOf(0L) }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // ============ ANA KART ============
    AnimatedVisibility(
        visible = !item.isHidden,
        enter = fadeIn(animationSpec = tween(300)) + scaleIn(
            initialScale = 0.8f,
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(animationSpec = tween(200)) + scaleOut(
            targetScale = 0.8f,
            animationSpec = tween(200)
        )
    ) {
        Box(
            modifier = Modifier
                .width((state.iconSize + 16).dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        isSelected && !isRenaming -> Color(0x663A8DFF)
                        isHovered && !isRenaming -> Color(0x223A8DFF)
                        else -> Color.Transparent
                    }
                )
                .hoverable(interactionSource)
                .pointerInput(item.id + "_drag") {
                    detectDragGestures(
                        onDragStart = {
                            state.renamingId = null
                            if (!state.isSelected(item.id)) {
                                state.clearSelection()
                                state.select(item.id)
                            }
                            val selectedIds = state.selectedIds.toList()
                            state.startDrag(selectedIds)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            state.updateDrag(dragAmount)
                        },
                        onDragEnd = {
                            val ids = state.selectedIds.toList()
                            val cellWidth = with(density) {
                                (state.iconSize + state.iconSpacing * 2).dp.toPx()
                            }
                            val cellHeight = with(density) {
                                (state.iconSize + 40 + state.iconSpacing * 2).dp.toPx()
                            }
                            val startX = with(density) { 16.dp.toPx() }
                            val startY = with(density) { 16.dp.toPx() }

                            ids.forEach { id ->
                                val pos = state.dragOffsets[id] ?: return@forEach
                                val col = ((pos.x - startX) / cellWidth).roundToInt().coerceAtLeast(0)
                                val row = ((pos.y - startY) / cellHeight).roundToInt().coerceAtLeast(0)
                                state.setPosition(id, Offset(
                                    startX + (col * cellWidth),
                                    startY + (row * cellHeight)
                                ))
                            }
                            state.endDrag()
                            state.saveState(context)
                        }
                    )
                }
                .pointerInput(item.id + "_rightclick") {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                event.changes.forEach { it.consume() }
                                if (!state.isSelected(item.id)) {
                                    state.clearSelection()
                                    state.select(item.id)
                                }
                                state.showContextMenu(item.id, event.changes.first().position)
                            }
                        }
                    }
                }
                .pointerInput(item.id + "_tap") {
                    detectTapGestures(
                        onLongPress = { offset ->
                            if (!state.isSelected(item.id)) {
                                state.clearSelection()
                                state.select(item.id)
                            }
                            state.showContextMenu(item.id, offset)
                        },
                        onTap = {
                            val now = System.currentTimeMillis()
                            if (isSelected && !item.isSystem && (now - lastTapTime in 400..2500)) {
                                state.startRenaming(item.id)
                            } else {
                                state.clearSelection()
                                state.select(item.id)
                                state.renamingId = null
                                if (item.isSystem && item.id == "recycle_bin") {
                                    onOpenWindow("recycle_bin", "Geri Dönüşüm Kutusu")
                                }
                            }
                            lastTapTime = now
                        },
                        onDoubleTap = {
                            state.renamingId = null
                            when {
                                item.isSystem && item.id == "recycle_bin" -> {
                                    onOpenWindow("recycle_bin", "Geri Dönüşüm Kutusu")
                                }
                                item.packageName != null -> {
                                    val app = apps.find { it.packageName == item.packageName }
                                    if (app != null) {
                                        if (!TaskbarState.runningApps.any { it.packageName == app.packageName }) {
                                            TaskbarState.runningApps.add(app)
                                        }
                                        pm.getLaunchIntentForPackage(item.packageName)?.let { context.startActivity(it) }
                                    }
                                }
                                item.isWidget -> {
                                    onOpenWidget(
                                        DesktopWidgetData(
                                            id = item.id,
                                            type = item.widgetType,
                                            x = state.getPosition(item.id).x,
                                            y = state.getPosition(item.id).y,
                                            data = item.widgetData
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    item.iconBitmap != null -> {
                        Image(
                            bitmap = item.iconBitmap,
                            contentDescription = null,
                            modifier = Modifier.size(state.iconSize.dp)
                        )
                    }
                    item.iconRes != null -> {
                        Image(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(state.iconSize.dp)
                        )
                    }
                    else -> {
                        Icon(
                            if (item.isFolder) Icons.Default.Folder else Icons.Default.Apps,
                            contentDescription = null,
                            tint = if (isDarkTheme) Color.White else Color.Black,
                            modifier = Modifier.size(state.iconSize.dp)
                        )
                    }
                }

                val displayName = state.getCustomName(item.id) ?: item.label

                if (isRenaming) {
                    var tempText by remember { mutableStateOf(displayName) }
                    val focusRequester = remember { FocusRequester() }

                    BasicTextField(
                        value = tempText,
                        onValueChange = {
                            tempText = it
                            state.setCustomName(item.id, it)
                        },
                        textStyle = TextStyle(
                            color = if (isDarkTheme) Color.White else Color.Black,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                state.endRenaming()
                                focusManager.clearFocus()
                                state.saveState(context)
                            }
                        ),
                        modifier = Modifier
                            .background(
                                if (isDarkTheme) Color.Black.copy(alpha = 0.3f)
                                else Color.White.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(2.dp)
                            .focusRequester(focusRequester),
                        singleLine = true
                    )
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                } else {
                    Text(
                        text = displayName,
                        fontSize = 11.sp,
                        color = if (isDarkTheme) Color.White else Color.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .background(
                                if (isDarkTheme) Color.Black.copy(alpha = 0.4f)
                                else Color.White.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            if (item.isFolder && item.folderItems.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0078D7)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.folderItems.size.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (item.isWidget) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("🧩", fontSize = 10.sp)
                }
            }

            if (state.contextMenuTarget == item.id && state.contextMenuOffset != null) {
                DesktopIconContextMenu(
                    item = item,
                    state = state,
                    apps = apps,
                    onOpenWindow = onOpenWindow,
                    onDismiss = { state.hideContextMenu() }
                )
            }
        }
    }
}

// ==================== BAĞLAM MENÜSÜ ====================

@Composable
private fun DesktopIconContextMenu(
    item: DesktopGridItem,
    state: DesktopGridState,
    apps: List<DesktopApp>,
    onOpenWindow: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val isDarkTheme = SystemState.theme == "dark"

    val backgroundColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF3F3F3)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val dividerColor = if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0)

    Popup(
        onDismissRequest = onDismiss,
        offset = IntOffset(
            state.contextMenuOffset!!.x.toInt(),
            state.contextMenuOffset!!.y.toInt()
        ),
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
            // 🟢 FIX: pinnedApps contains -> any ile kontrol
            val isPinned = SystemState.pinnedApps.contains(item.packageName)
            val isOnDesktop = SystemState.desktopApps.contains(item.packageName)
            val selectedCount = state.selectedIds.size

            val menuItems = when {
                item.isSystem && item.id == "recycle_bin" -> listOf(
                    "Aç", "Çöp Kutusunu Boşalt", "Özellikler"
                )
                item.isFolder -> listOf(
                    "Aç", "Yeniden Adlandır", "Sil", "Özellikler"
                )
                item.isWidget -> listOf(
                    "Kaldır", "Yeniden Adlandır", "Özellikler"
                )
                selectedCount > 1 -> listOf(
                    "Seçilenleri Aç", "Seçilenleri Sil", "Seçilenleri Klasöre Taşı"
                )
                item.packageName != null -> listOf(
                    "Aç",
                    if (isPinned) "Görev Çubuğundan Kaldır" else "Görev Çubuğuna Sabitle",
                    if (isOnDesktop) "Masaüstünden Kaldır" else "Masaüstüne Ekle",
                    "Yeniden Adlandır",
                    "Uygulamayı Kaldır",
                    "Özellikler"
                )
                else -> listOf("Yeniden Adlandır", "Sil", "Özellikler")
            }

            menuItems.forEach { m ->
                val isDanger = m.contains("Sil") || m.contains("Kaldır") || m.contains("Boşalt")
                MenuItem(
                    text = m,
                    isDanger = isDanger,
                    textColor = textColor,
                    onClick = {
                        onDismiss()
                        when (m) {
                            "Aç" -> {
                                if (item.isSystem && item.id == "recycle_bin") {
                                    onOpenWindow("recycle_bin", "Geri Dönüşüm Kutusu")
                                } else if (item.packageName != null) {
                                    pm.getLaunchIntentForPackage(item.packageName)?.let { context.startActivity(it) }
                                }
                            }
                            "Görev Çubuğuna Sabitle" -> {
                                if (item.packageName != null) {
                                    apps.find { it.packageName == item.packageName }?.let {
                                        if (!SystemState.pinnedApps.contains(it.packageName)) {
                                            SystemState.pinApp(context, it.packageName)
                                            TaskbarState.pinApp(context, it)
                                        }
                                    }
                                }
                            }
                            "Görev Çubuğundan Kaldır" -> {
                                if (item.packageName != null) {
                                    apps.find { it.packageName == item.packageName }?.let {
                                        SystemState.unpinApp(context, it.packageName)
                                        TaskbarState.unpinApp(context, it)
                                    }
                                }
                            }
                            "Masaüstüne Ekle" -> {
                                if (item.packageName != null) {
                                    SystemState.addDesktopApp(context, item.packageName)
                                }
                            }
                            "Masaüstünden Kaldır" -> {
                                if (item.packageName != null) {
                                    SystemState.removeDesktopApp(context, item.packageName)
                                }
                                state.removePosition(item.id)
                                state.saveState(context)
                            }
                            "Yeniden Adlandır" -> {
                                state.startRenaming(item.id)
                            }
                            "Uygulamayı Kaldır" -> {
                                if (item.packageName != null) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${item.packageName}")
                                        )
                                    )
                                }
                            }
                            "Özellikler" -> {
                                if (item.packageName != null) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${item.packageName}")
                                        )
                                    )
                                }
                            }
                            "Seçilenleri Aç" -> {
                                state.selectedIds.forEach { id ->
                                    val pkg = state.getItem(id)?.packageName
                                    if (pkg != null) {
                                        pm.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) }
                                    }
                                }
                            }
                            "Seçilenleri Sil" -> {
                                state.selectedIds.forEach { id ->
                                    val pkg = state.getItem(id)?.packageName
                                    if (pkg != null) {
                                        SystemState.removeDesktopApp(context, pkg)
                                        state.removePosition(id)
                                    }
                                }
                                state.clearSelection()
                                state.saveState(context)
                            }
                            "Seçilenleri Klasöre Taşı" -> {
                                // İleride klasör oluşturma işlemi
                            }
                            "Çöp Kutusunu Boşalt" -> {
                                SystemState.recycleBinItems.clear()
                            }
                            "Kaldır" -> {
                                if (item.isWidget) {
                                    state.updateItems(state.allItems.filter { it.id != item.id })
                                    state.saveState(context)
                                }
                            }
                        }
                    }
                )
                if (menuItems.indexOf(m) < menuItems.size - 1) {
                    // 🟢 FIX: Divider → HorizontalDivider (Material 3)
                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    isDanger: Boolean = false,
    textColor: Color,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = if (isDanger) Color(0xFFE53935) else textColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}