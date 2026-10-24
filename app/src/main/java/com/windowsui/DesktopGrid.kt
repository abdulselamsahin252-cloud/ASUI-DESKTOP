package com.windowsui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// ==================== VERİ SINIFLARI ====================

data class DesktopGridItem(
    val id: String,
    val label: String,
    val packageName: String?,
    val isSystem: Boolean = false,
    val isFolder: Boolean = false,
    val folderItems: List<String> = emptyList(),
    val iconBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    val iconRes: Int? = null,
    val isWidget: Boolean = false,
    val widgetType: String = "",
    val widgetData: Map<String, Any> = emptyMap(),
    val isHidden: Boolean = false
)

@Stable
class DesktopGridState(
    initialItems: List<DesktopGridItem> = emptyList()
) {
    private val _allItems = mutableStateListOf<DesktopGridItem>()
    val allItems: List<DesktopGridItem> get() = _allItems

    var filteredItems by mutableStateOf<List<DesktopGridItem>>(emptyList())
        private set

    val selectedIds = mutableStateListOf<String>()
    var renamingId by mutableStateOf<String?>(null)

    var dragOffsets by mutableStateOf<Map<String, Offset>>(emptyMap())
    var dragStartOrigins by mutableStateOf<Map<String, Offset>>(emptyMap())

    var searchQuery by mutableStateOf("")
    var showHidden by mutableStateOf(false)
    var sortBy by mutableStateOf("name")

    var iconSize by mutableStateOf(44)
    var iconSpacing by mutableStateOf(8)

    // ============ ÇERÇEVE SEÇİM (RUBBER-BAND) ============
    var selectionStart by mutableStateOf<Offset?>(null)
    var selectionEnd by mutableStateOf<Offset?>(null)

    var contextMenuTarget by mutableStateOf<String?>(null)
    var contextMenuOffset by mutableStateOf<Offset?>(null)

    val customNames = mutableStateMapOf<String, String>()
    val positions = mutableStateMapOf<String, Offset>()

    init {
        updateItems(initialItems)
    }

    fun updateItems(newItems: List<DesktopGridItem>) {
        _allItems.clear()
        _allItems.addAll(newItems)
        applyFiltersAndSort()
    }

    fun applyFiltersAndSort() {
        var items: List<DesktopGridItem> = _allItems
        if (searchQuery.isNotEmpty()) {
            items = items.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                        it.id.contains(searchQuery, ignoreCase = true)
            }
        }
        if (!showHidden) {
            items = items.filter { !it.isHidden }
        }
        items = when (sortBy) {
            "name" -> items.sortedBy { it.label.lowercase() }
            "type" -> items.sortedBy { it.isFolder }
            "date" -> items.sortedByDescending { it.id }
            else -> items
        }
        filteredItems = items
    }

    fun select(id: String, addToSelection: Boolean = false) {
        if (addToSelection) {
            if (!selectedIds.contains(id)) selectedIds.add(id)
        } else {
            selectedIds.clear()
            selectedIds.add(id)
        }
    }

    fun toggleSelection(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
    }

    fun clearSelection() {
        selectedIds.clear()
        renamingId = null
        selectionStart = null
        selectionEnd = null
    }

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(filteredItems.map { it.id })
    }

    fun isSelected(id: String): Boolean = selectedIds.contains(id)

    fun getPosition(id: String, default: Offset = Offset.Zero): Offset {
        return positions[id] ?: default
    }

    fun setPosition(id: String, position: Offset) {
        positions[id] = position
    }

    fun removePosition(id: String) {
        positions.remove(id)
    }

    fun startDrag(ids: List<String>) {
        val origins = ids.associateWith { id ->
            positions[id] ?: Offset.Zero
        }
        dragStartOrigins = origins
        dragOffsets = origins
    }

    fun updateDrag(delta: Offset) {
        dragOffsets = dragStartOrigins.mapValues { (id, origin) ->
            origin + delta
        }
    }

    fun endDrag() {
        dragOffsets = emptyMap()
        dragStartOrigins = emptyMap()
    }

    fun startRenaming(id: String) {
        renamingId = id
    }

    fun endRenaming() {
        renamingId = null
    }

    fun getCustomName(id: String): String? = customNames[id]

    fun setCustomName(id: String, name: String) {
        customNames[id] = name
    }

    fun showContextMenu(id: String, offset: Offset) {
        contextMenuTarget = id
        contextMenuOffset = offset
    }

    fun hideContextMenu() {
        contextMenuTarget = null
        contextMenuOffset = null
    }

    fun getItem(id: String): DesktopGridItem? {
        return _allItems.find { it.id == id }
    }

    fun getSelectedItems(): List<DesktopGridItem> {
        return selectedIds.mapNotNull { getItem(it) }
    }

    fun getDisplayName(item: DesktopGridItem): String {
        return customNames[item.id] ?: item.label
    }

    fun saveState(context: Context) {
        val prefs = context.getSharedPreferences("ASUI_DesktopGridPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("positions", positions.map { "${it.key}|${it.value.x}|${it.value.y}" }.joinToString(";"))
            putString("customNames", customNames.map { "${it.key}|${it.value}" }.joinToString(";"))
            putString("sortBy", sortBy)
            putBoolean("showHidden", showHidden)
            putInt("iconSize", iconSize)
            putInt("iconSpacing", iconSpacing)
        }.apply()
    }

    fun loadState(context: Context) {
        val prefs = context.getSharedPreferences("ASUI_DesktopGridPrefs", Context.MODE_PRIVATE)
        val posStr = prefs.getString("positions", "") ?: ""
        if (posStr.isNotEmpty()) {
            posStr.split(";").forEach { entry ->
                val parts = entry.split("|")
                if (parts.size == 3) {
                    val x = parts[1].toFloatOrNull() ?: 0f
                    val y = parts[2].toFloatOrNull() ?: 0f
                    positions[parts[0]] = Offset(x, y)
                }
            }
        }
        val namesStr = prefs.getString("customNames", "") ?: ""
        if (namesStr.isNotEmpty()) {
            namesStr.split(";").forEach { entry ->
                val parts = entry.split("|")
                if (parts.size == 2) {
                    customNames[parts[0]] = parts[1]
                }
            }
        }
        sortBy = prefs.getString("sortBy", "name") ?: "name"
        showHidden = prefs.getBoolean("showHidden", false)
        iconSize = prefs.getInt("iconSize", 44)
        iconSpacing = prefs.getInt("iconSpacing", 8)
    }
}

// ==================== ANA BİLEŞEN ====================

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DesktopGrid(
    apps: List<DesktopApp>,
    modifier: Modifier = Modifier,
    onOpenWindow: (id: String, title: String) -> Unit = { _, _ -> },
    onOpenWidget: (DesktopWidgetData) -> Unit = {},
    state: DesktopGridState = remember { DesktopGridState() }
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // ============ YENİ ÖĞELERİ OLUŞTUR ============
    LaunchedEffect(apps) {
        val items = buildItems(apps, context, pm)
        state.updateItems(items)
        state.loadState(context)
    }

    // ============ IZGARA BOYUTLARI ============
    val cellWidth = with(density) { (state.iconSize + state.iconSpacing * 2).dp.toPx() }
    val cellHeight = with(density) { (state.iconSize + 40 + state.iconSpacing * 2).dp.toPx() }
    val startX = with(density) { 16.dp.toPx() }
    val startY = with(density) { 16.dp.toPx() }
    val maxRows = max(3, ((config.screenHeightDp - 100) / (state.iconSize / 40 + 60)))

    fun getGridPosition(id: String, default: Offset = Offset.Zero): Offset {
        val saved = state.getPosition(id)
        if (saved != Offset.Zero) return saved
        val occupied = state.positions.values.toSet()
        var col = 0
        var row = 0
        var found = false
        while (!found) {
            val pos = Offset(startX + (col * cellWidth), startY + (row * cellHeight))
            if (!occupied.contains(pos)) {
                state.setPosition(id, pos)
                return pos
            }
            row++
            if (row >= maxRows) {
                row = 0
                col++
            }
        }
        return default
    }

    val filteredItems = state.filteredItems
    var showSearchBar by remember { mutableStateOf(false) }

    // ============ ANA KANVAS (ÇERÇEVE SEÇİM DESTEKLİ) ============
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput("DesktopCanvas") {
                detectDragGestures(
                    onDragStart = { offset ->
                        state.clearSelection()
                        state.selectionStart = offset
                        state.selectionEnd = offset
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.selectionEnd = state.selectionEnd?.plus(dragAmount)
                        // Seçim çerçevesi içindeki öğeleri seç
                        val l = minOf(state.selectionStart!!.x, state.selectionEnd!!.x)
                        val t = minOf(state.selectionStart!!.y, state.selectionEnd!!.y)
                        val r = maxOf(state.selectionStart!!.x, state.selectionEnd!!.x)
                        val b = maxOf(state.selectionStart!!.y, state.selectionEnd!!.y)
                        val selRect = Rect(l, t, r, b)

                        state.selectedIds.clear()
                        filteredItems.forEach { item ->
                            val pos = getGridPosition(item.id)
                            val itemRect = Rect(
                                pos.x,
                                pos.y,
                                pos.x + with(density) { (state.iconSize + 16).dp.toPx() },
                                pos.y + with(density) { (state.iconSize + 16 + 40).dp.toPx() }
                            )
                            if (selRect.overlaps(itemRect)) {
                                state.selectedIds.add(item.id)
                            }
                        }
                    },
                    onDragEnd = {
                        state.selectionStart = null
                        state.selectionEnd = null
                    }
                )
            }
            .pointerInput("DesktopBgTap") {
                detectTapGestures(
                    onTap = {
                        if (state.selectionStart == null) {
                            state.clearSelection()
                            showSearchBar = false
                            focusManager.clearFocus()
                        }
                    },
                    onLongPress = { offset ->
                        if (state.selectedIds.isEmpty()) {
                            state.contextMenuTarget = null
                            state.contextMenuOffset = offset
                        } else {
                            state.contextMenuTarget = state.selectedIds.firstOrNull()
                            state.contextMenuOffset = offset
                        }
                    }
                )
            }
            .pointerInput("DesktopBgRightClick") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            state.clearSelection()
                            state.contextMenuTarget = null
                            state.contextMenuOffset = event.changes.first().position
                        }
                    }
                }
            }
    ) {
        // ============ SEÇİM ÇERÇEVESİ (RUBBER-BAND) ============
        if (state.selectionStart != null && state.selectionEnd != null) {
            val l = minOf(state.selectionStart!!.x, state.selectionEnd!!.x)
            val t = minOf(state.selectionStart!!.y, state.selectionEnd!!.y)
            val w = abs(state.selectionStart!!.x - state.selectionEnd!!.x)
            val h = abs(state.selectionStart!!.y - state.selectionEnd!!.y)
            Box(
                modifier = Modifier
                    .offset { IntOffset(l.toInt(), t.toInt()) }
                    .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                    .background(Color(0x443A8DFF))
                    .border(1.dp, Color(0xFF3A8DFF))
            )
        }

        // ============ ÖĞELER (KEY İLE OPTİMİZE) ============
        filteredItems.forEach { item ->
            val basePos = getGridPosition(item.id)
            val dragOffset = state.dragOffsets[item.id] ?: Offset.Zero
            val currentPos = basePos + dragOffset

            val isSelected = state.isSelected(item.id)
            val isRenaming = state.renamingId == item.id
            var showMenu by remember { mutableStateOf(false) }
            var lastTapTime by remember { mutableStateOf(0L) }

            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()

            // ============ TEK ÖĞE (KEY İLE SARILMIŞ) ============
            key(item.id) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                currentPos.x.roundToInt(),
                                currentPos.y.roundToInt()
                            )
                        }
                        .width((state.iconSize + 16).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected && !isRenaming) Color(0x663A8DFF)
                            else if (isHovered && !isRenaming) Color(0x223A8DFF)
                            else Color.Transparent
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
                                    if (item.isSystem && item.id == "recycle_bin") {
                                        onOpenWindow("recycle_bin", "Geri Dönüşüm Kutusu")
                                    } else if (item.packageName != null) {
                                        val app = apps.find { it.packageName == item.packageName }
                                        if (app != null) {
                                            if (!TaskbarState.runningApps.any { it.packageName == app.packageName }) {
                                                TaskbarState.runningApps.add(app)
                                            }
                                            pm.getLaunchIntentForPackage(item.packageName)?.let { context.startActivity(it) }
                                        }
                                    } else if (item.isWidget) {
                                        val widgetData = DesktopWidgetData(
                                            id = item.id,
                                            type = item.widgetType,
                                            x = currentPos.x,
                                            y = currentPos.y,
                                            data = item.widgetData
                                        )
                                        onOpenWidget(widgetData)
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
                        if (item.iconBitmap != null) {
                            Image(
                                bitmap = item.iconBitmap,
                                contentDescription = null,
                                modifier = Modifier.size(state.iconSize.dp)
                            )
                        } else if (item.iconRes != null) {
                            Image(
                                painter = painterResource(item.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(state.iconSize.dp)
                            )
                        } else {
                            Icon(
                                if (item.isFolder) Icons.Default.Folder else Icons.Default.Apps,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(state.iconSize.dp)
                            )
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
                                    color = Color.White,
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
                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
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
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
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
                        ContextMenu(
                            item = item,
                            state = state,
                            context = context,
                            pm = pm,
                            apps = apps,
                            onOpenWindow = onOpenWindow,
                            onDismiss = { state.hideContextMenu() }
                        )
                    }
                }
            }
        }

        // ============ MASAÜSTÜ ARAMA ÇUBUĞU ============
        AnimatedVisibility(
            visible = showSearchBar,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            DesktopSearchBar(
                searchQuery = state.searchQuery,
                onSearchChange = {
                    state.searchQuery = it
                    state.applyFiltersAndSort()
                },
                onClose = { showSearchBar = false },
                onClear = {
                    state.searchQuery = ""
                    state.applyFiltersAndSort()
                }
            )
        }

        // ============ BOŞ ALAN SAĞ TIK MENÜSÜ ============
        if (state.contextMenuTarget == null && state.contextMenuOffset != null) {
            Popup(
                onDismissRequest = { state.hideContextMenu() },
                offset = IntOffset(
                    state.contextMenuOffset!!.x.toInt(),
                    state.contextMenuOffset!!.y.toInt()
                )
            ) {
                DesktopEmptyContextMenu(
                    state = state,
                    onOpenWidget = { type ->
                        val id = "widget_${System.currentTimeMillis()}"
                        val item = DesktopGridItem(
                            id = id,
                            label = type.replaceFirstChar { it.uppercase() },
                            packageName = null,
                            isWidget = true,
                            widgetType = type,
                            iconRes = null
                        )
                        state.updateItems(state.allItems + item)
                        state.saveState(context)
                    },
                    onSort = { sort ->
                        state.sortBy = sort
                        state.applyFiltersAndSort()
                        state.saveState(context)
                    },
                    onDismiss = { state.hideContextMenu() }
                )
            }
        }
    }
}

// ==================== BAĞLAM MENÜSÜ ====================

@Composable
private fun ContextMenu(
    item: DesktopGridItem,
    state: DesktopGridState,
    context: Context,
    pm: android.content.pm.PackageManager,
    apps: List<DesktopApp>,
    onOpenWindow: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val isDarkTheme = SystemState.theme == "dark"
    val backgroundColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF3F3F3)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val dividerColor = if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0)

    Popup(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .shadow(16.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .width(200.dp)
                .padding(4.dp)
        ) {
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
                                // Klasör oluştur (ileri düzey)
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
                    Divider(color = dividerColor, thickness = 0.5.dp)
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

// ==================== BOŞ ALAN MENÜSÜ ====================

@Composable
private fun DesktopEmptyContextMenu(
    state: DesktopGridState,
    onOpenWidget: (String) -> Unit,
    onSort: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isDarkTheme = SystemState.theme == "dark"
    val backgroundColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF3F3F3)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val dividerColor = if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0)

    Popup(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .shadow(16.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .width(200.dp)
                .padding(4.dp)
        ) {
            val items = listOf(
                "Yenile",
                "---",
                "Sırala (Ada Göre)" to { onSort("name") },
                "Sırala (Türe Göre)" to { onSort("type") },
                "Sırala (Tarihe Göre)" to { onSort("date") },
                "---",
                "Yeni Klasör",
                "Yapıştır",
                "---",
                "Widget Ekle → Saat" to { onOpenWidget("clock") },
                "Widget Ekle → Hava Durumu" to { onOpenWidget("weather") },
                "Widget Ekle → Notlar" to { onOpenWidget("notes") },
                "Widget Ekle → Sistem Monitörü" to { onOpenWidget("system_monitor") },
                "Widget Ekle → Takvim" to { onOpenWidget("calendar") },
                "---",
                "Gizli öğeleri göster" to { state.showHidden = !state.showHidden; state.applyFiltersAndSort() },
                "Terminali Aç",
                "Görev Yöneticisi",
                "Kişiselleştir"
            )

            items.forEach { item ->
                when (item) {
                    "---" -> Divider(color = dividerColor, thickness = 0.5.dp)
                    else -> {
                        val label = if (item is Pair<*, *>) item.first.toString() else item.toString()
                        val action = if (item is Pair<*, *>) item.second as? () -> Unit else null
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            color = textColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    onDismiss()
                                    when (label) {
                                        "Yenile" -> state.applyFiltersAndSort()
                                        "Yeni Klasör" -> {}
                                        "Yapıştır" -> {}
                                        "Terminali Aç" -> WindowState.openWindow(
                                            id = "terminal_${System.currentTimeMillis()}",
                                            title = "Terminal",
                                            contentId = "cmd",
                                            width = 650,
                                            height = 380
                                        )
                                        "Görev Yöneticisi" -> {}
                                        "Kişiselleştir" -> {}
                                        else -> action?.invoke()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== ARAMA ÇUBUĞU ====================

@Composable
private fun DesktopSearchBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp)),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { onClose() }
                    )
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Temizle",
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onClear() }
                    )
                }
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Kapat",
                    tint = Color.Gray,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onClose() }
                )
            }
        }
    }
}

// ==================== YARDIMCI FONKSİYONLAR ====================

private fun buildItems(
    apps: List<DesktopApp>,
    context: Context,
    pm: android.content.pm.PackageManager
): List<DesktopGridItem> {
    val items = mutableListOf<DesktopGridItem>()

    val isRecycleEmpty = SystemState.recycleBinItems.isEmpty()
    items.add(
        DesktopGridItem(
            id = "recycle_bin",
            label = "Geri Dönüşüm Kutusu",
            packageName = null,
            isSystem = true,
            iconRes = if (isRecycleEmpty) R.drawable.recycle_bin_empty else R.drawable.recycle_bin_full
        )
    )

    apps.filter { SystemState.desktopApps.contains(it.packageName) }.forEach { app ->
        items.add(
            DesktopGridItem(
                id = app.packageName,
                label = DesktopState.customNames[app.packageName] ?: app.label,
                packageName = app.packageName,
                iconBitmap = app.iconBitmap
            )
        )
    }

    return items
}