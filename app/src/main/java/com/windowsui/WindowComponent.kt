package com.windowsui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random
import com.windowsui.SettingsWindow
import com.windowsui.StoreWindow
import com.windowsui.PersonalizationWindow

/**
 * WindowComponent – ASUI Desktop Pencere Bileşeni (Windows 11 tarzı)
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WindowComponent(window: WindowState.WindowData) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val isDarkTheme = SystemState.theme == "dark"
    val animationsEnabled = SystemState.animationsEnabled

    val devicePersonality = remember {
        runCatching { DeviceInfo.analyzeDevicePersonality(context) }.getOrNull() ?: "Bilinmiyor"
    }
    val spiritColor = remember(devicePersonality) {
        when {
            devicePersonality.contains("süper kahraman") -> Color(0xFFFF6B6B)
            devicePersonality.contains("denge ustası") -> Color(0xFF4ECDC4)
            devicePersonality.contains("minimalist") -> Color(0xFFA8E6CF)
            else -> Color(0xFF0078D7)
        }
    }

    val titleBarColor = if (window.titleBarColor != 0) {
        Color(window.titleBarColor.toLong())
    } else {
        spiritColor
    }

    var isDragging by remember { mutableStateOf(false) }
    var isResizing by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(Offset.Zero) }

    val resizeHandleSize = 16.dp

    val animationSpec = if (animationsEnabled) {
        tween<Float>(300, easing = FastOutSlowInEasing)
    } else {
        tween<Float>(0, easing = LinearEasing)
    }

    val cornerRadius = 12.dp
    val shadowElevation = if (window.isPinned) 24.dp else 12.dp

    fun onDragStart() {
        isDragging = true
        WindowState.focusWindow(window)
        WindowState.saveState(context)
    }

    fun onDrag(delta: Offset) {
        if (!window.isPinned && !window.isMaximized) {
            val newX = (window.x + delta.x).coerceAtLeast(0f)
            val newY = (window.y + delta.y).coerceAtLeast(0f)
            WindowState.setWindowPosition(window, newX, newY)
        }
    }

    fun onDragEnd() {
        isDragging = false
        WindowState.saveState(context)
    }

    fun onResizeStart() {
        isResizing = true
        WindowState.focusWindow(window)
    }

    fun onResize(delta: Offset) {
        if (!window.isPinned && !window.isMaximized) {
            val newWidth = (window.width + delta.x).roundToInt().coerceIn(window.minWidth, window.maxWidth)
            val newHeight = (window.height + delta.y).roundToInt().coerceIn(window.minHeight, window.maxHeight)
            WindowState.setWindowSize(window, newWidth, newHeight)
        }
    }

    fun onResizeEnd() {
        isResizing = false
        WindowState.saveState(context)
    }

    fun closeWindow() {
        WindowState.closeWindow(window, saveToHistory = true)
        WindowState.saveState(context)
    }

    fun minimizeWindow() {
        WindowState.minimizeWindow(window)
        WindowState.saveState(context)
    }

    fun maximizeWindow() {
        WindowState.maximizeWindow(window)
        WindowState.saveState(context)
    }

    fun togglePin() {
        WindowState.pinWindow(window)
        WindowState.saveState(context)
    }

    fun magicResize() {
        val newX = Random.nextInt(0, 500).toFloat()
        val newY = Random.nextInt(0, 300).toFloat()
        val newWidth = Random.nextInt(300, 800)
        val newHeight = Random.nextInt(200, 600)
        WindowState.setWindowPosition(window, newX, newY)
        WindowState.setWindowSize(window, newWidth, newHeight)
        WindowState.saveState(context)
    }

    fun showSpiritInfo() {
        android.widget.Toast.makeText(
            context,
            "Cihaz Ruhu: $devicePersonality",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    @Composable
    fun WindowContent() {
        when (window.contentId) {
            "explorer" -> FileExplorerContent(onClose = { closeWindow() })
            "cmd" -> CmdTerminalContent(onClose = { closeWindow() })
            "windows_apps" -> WinExeRuntimeWindow(onClose = { closeWindow() })
            "settings" -> SettingsWindow(onClose = { closeWindow() })
            "store" -> StoreWindow(onClose = { closeWindow() })
            "personalization" -> PersonalizationWindow(onClose = { closeWindow() })
            "recycle_bin" -> RecycleBinContent(onClose = { closeWindow() })
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        if (isDarkTheme) Color(0xFF1A1A1A) else Color.White
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "İçerik: ${window.contentId}",
                        color = if (isDarkTheme) Color.White else Color.Black
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = !window.isHidden,
        enter = fadeIn(animationSpec = animationSpec) +
                scaleIn(initialScale = 0.9f, animationSpec = animationSpec) +
                slideIn(initialOffset = { IntOffset(0, 50) }, animationSpec = tween(if (animationsEnabled) 300 else 0)),
        exit = fadeOut(animationSpec = animationSpec) +
                scaleOut(targetScale = 0.9f, animationSpec = animationSpec) +
                slideOut(targetOffset = { IntOffset(0, 50) }, animationSpec = tween(if (animationsEnabled) 300 else 0))
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        window.x.roundToInt(),
                        window.y.roundToInt()
                    )
                }
                .width(window.width.dp)
                .height(window.height.dp)
                .shadow(
                    elevation = shadowElevation,
                    shape = RoundedCornerShape(cornerRadius),
                    clip = false
                )
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    if (window.isTransparent) {
                        Color.Transparent
                    } else {
                        if (isDarkTheme) Color(0xFF1A1A1A) else Color.White
                    }
                )
                .graphicsLayer {
                    if (window.blurEnabled) {
                        alpha = window.opacity
                    } else {
                        alpha = window.opacity
                    }
                }
                .pointerInput("WindowDrag") {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { _, dragAmount -> onDrag(dragAmount) },
                        onDragEnd = { onDragEnd() }
                    )
                }
                .pointerInput("WindowRightClick") {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                event.changes.forEach { it.consume() }
                                showContextMenu = true
                                contextMenuOffset = event.changes.first().position
                            }
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(
                            if (window.isTransparent) titleBarColor.copy(alpha = 0.7f)
                            else titleBarColor
                        )
                        .pointerInput("TitleBarDrag") {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDrag = { _, dragAmount -> onDrag(dragAmount) },
                                onDragEnd = { onDragEnd() }
                            )
                        }
                        .pointerInput("TitleBarRightClick") {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                        event.changes.forEach { it.consume() }
                                        showContextMenu = true
                                        contextMenuOffset = event.changes.first().position
                                    }
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        if (window.iconRes != null) {
                            Icon(
                                painter = painterResource(window.iconRes),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(
                                Icons.Default.Window,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Text(
                            text = window.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { showSpiritInfo() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("🧘", fontSize = 14.sp)
                        }

                        IconButton(
                            onClick = { magicResize() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Sihir",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { togglePin() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                if (window.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                                contentDescription = if (window.isPinned) "Sabitlendi" else "Sabitle",
                                tint = if (window.isPinned) Color(0xFFFFD700) else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { minimizeWindow() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = "Küçült",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { maximizeWindow() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                if (window.isMaximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (window.isMaximized) "Eski haline döndür" else "Büyüt",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { closeWindow() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            if (window.isTransparent) Color.Transparent
                            else if (isDarkTheme) Color(0xFF1A1A1A) else Color.White
                        )
                ) {
                    WindowContent()
                }

                if (!window.isMaximized && !window.isPinned) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .size(resizeHandleSize)
                            .background(Color.Transparent)
                            .pointerInput("ResizeHandle") {
                                detectDragGestures(
                                    onDragStart = { onResizeStart() },
                                    onDrag = { _, dragAmount -> onResize(dragAmount) },
                                    onDragEnd = { onResizeEnd() }
                                )
                            },
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Boyutlandır",
                            tint = if (isDarkTheme) Color.Gray else Color.LightGray,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            if (showContextMenu) {
                Popup(
                    onDismissRequest = { showContextMenu = false },
                    offset = IntOffset(contextMenuOffset.x.toInt(), contextMenuOffset.y.toInt())
                ) {
                    WindowContextMenu(
                        window = window,
                        onClose = { closeWindow() },
                        onMinimize = { minimizeWindow() },
                        onMaximize = { maximizeWindow() },
                        onPin = { togglePin() },
                        onDismiss = { showContextMenu = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun WindowContextMenu(
    window: WindowState.WindowData,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onPin: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDarkTheme = SystemState.theme == "dark"
    val backgroundColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF3F3F3)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val dividerColor = if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0)

    Column(
        modifier = Modifier
            .shadow(16.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .width(180.dp)
            .padding(4.dp)
    ) {
        val items = listOf(
            "Küçült" to onMinimize,
            "Büyüt" to onMaximize,
            "Her Zaman Üstte" to onPin,
            "Kapat" to onClose
        )

        items.forEachIndexed { index, (label, action) ->
            val isDanger = label == "Kapat"
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (isDanger) Color(0xFFE53935) else textColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        onDismiss()
                        action()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            if (index < items.size - 1) {
                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun RecycleBinContent(onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Geri Dönüşüm Kutusu", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Henüz içerik yok.", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onClose) {
                Text("Kapat")
            }
        }
    }
}
