package com.windowsui
import androidx.compose.foundation.lazy.LazyRow

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NotificationPanel(
    modifier: Modifier = Modifier,
    onReplyClick: (WindowsNotification) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Bildirimler
    val allNotifications = NotificationState.notifications
    val isAccessGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    // ============ FİLTRELEME VE AYARLAR ============
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tümü") }
    var showOnlyPinned by remember { mutableStateOf(false) }
    var hideSilent by remember { mutableStateOf(true) }

    // Çoklu seçim
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Kategoriler
    val categories = listOf("Tümü") + NotificationState.notifications.map { it.category }.distinct().sorted()

    // Filtrelenmiş bildirimler
    val filteredNotifications = remember(allNotifications, searchQuery, selectedCategory, showOnlyPinned, hideSilent) {
        allNotifications
            .filter { notif ->
                val matchesSearch = if (searchQuery.isNotEmpty()) {
                    notif.title.contains(searchQuery, ignoreCase = true) ||
                            notif.text.contains(searchQuery, ignoreCase = true) ||
                            notif.appName.contains(searchQuery, ignoreCase = true)
                } else true
                val matchesCategory = selectedCategory == "Tümü" || notif.category == selectedCategory
                val matchesPinned = if (showOnlyPinned) notif.isPinned else true
                val matchesSilent = if (hideSilent) !notif.isSilent else true
                matchesSearch && matchesCategory && matchesPinned && matchesSilent
            }
            .sortedByDescending { it.timestamp }
    }

    // ============ TEMA RENKLERİ ============
    val isDarkTheme = SystemState.theme == "dark"
    val surfaceColor = if (isDarkTheme) Color(0xFF1C1C1C) else Color.White
    val backgroundColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF3F3F3)
    val cardColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFF8F8F8)
    val textPrimary = if (isDarkTheme) Color.White else Color.Black
    val textSecondary = if (isDarkTheme) Color.LightGray else Color.DarkGray
    val accentColor = Color(0xFF0078D7)
    val dividerColor = if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0)

    // ============ BİLDİRİM SAYACI ============
    val notificationCount = filteredNotifications.size

    // ============ ANA PANEL ============
    Box(
        modifier = modifier
            .width(400.dp)
            .heightIn(min = 300.dp, max = 580.dp)
            .shadow(24.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .pointerInput(Unit) { /* Tıklama yayılmasını engelle */ }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ============ BAŞLIK ÇUBUĞU ============
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bildirimler",
                    color = textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Bildirim sayacı
                if (notificationCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (notificationCount > 99) "99+" else notificationCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Tümünü temizle
                Text(
                    text = "Tümünü temizle",
                    color = if (notificationCount > 0) accentColor else Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(enabled = notificationCount > 0) {
                        if (selectionMode) {
                            // Seçili olanları temizle
                            selectedIds.forEach { id ->
                                NotificationState.notifications.removeAll { it.id == id }
                            }
                            selectedIds = emptySet()
                            selectionMode = false
                        } else {
                            NotificationState.notifications.removeAll { !it.isPinned }
                        }
                    }.padding(start = 8.dp)
                )
            }

            // ============ ARAÇ ÇUBUĞU (Arama, Filtreler) ============
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Arama kutusu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                        textStyle = LocalTextStyle.current.copy(color = textPrimary, fontSize = 13.sp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { })
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

                Spacer(modifier = Modifier.height(6.dp))

                // Kategori ve filtreleme (sadece bildirim varsa)
                if (notificationCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Kategori chip'leri (önceki kodun aynısı)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(categories) { category ->
                                val isSelected = category == selectedCategory
                                Surface(
                                    modifier = Modifier.clickable {
                                        if (isSelected) selectedCategory = "Tümü" else selectedCategory = category
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) accentColor else if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0),
                                    tonalElevation = 0.dp
                                ) {
                                    Text(
                                        text = category,
                                        color = if (isSelected) Color.White else textSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Filtre düğmeleri
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Sabitlenmiş filtre
                            Icon(
                                if (showOnlyPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                                contentDescription = "Sabitlenmiş",
                                tint = if (showOnlyPinned) accentColor else textSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { showOnlyPinned = !showOnlyPinned }
                            )
                            // Sessiz gizleme
                            Icon(
                                if (hideSilent) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Sessizleri gizle",
                                tint = if (hideSilent) accentColor else textSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { hideSilent = !hideSilent }
                            )
                            // Çoklu seçim
                            Icon(
                                if (selectionMode) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = "Seçim modu",
                                tint = if (selectionMode) accentColor else textSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        selectionMode = !selectionMode
                                        if (!selectionMode) selectedIds = emptySet()
                                    }
                            )
                        }
                    }
                }
            }

            Divider(color = dividerColor, thickness = 1.dp)

            // ============ BİLDİRİM LİSTESİ ============
            Box(modifier = Modifier.weight(1f)) {
                if (!isAccessGranted) {
                    // Bildirim izni yok
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize().padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Bildirim erişimi kapalı",
                            color = textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ASUI Desktop'un bildirimleri görebilmesi için\n izin vermeniz gerekiyor.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "İzin ver",
                            color = accentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFF0F0F0))
                                .clickable {
                                    context.startActivity(
                                        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                } else if (notificationCount == 0) {
                    // Bildirim yok
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize().padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Bildirim yok",
                            color = textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Yeni bildirimler burada görünecek.",
                            color = textSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    // Bildirim listesi
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = filteredNotifications,
                            key = { it.id }
                        ) { notif ->
                            // Her bildirim için kart
                            NotificationCard(
                                notification = notif,
                                isDarkTheme = isDarkTheme,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                cardColor = cardColor,
                                accentColor = accentColor,
                                selectionMode = selectionMode,
                                isSelected = selectedIds.contains(notif.id),
                                onSelect = {
                                    selectedIds = if (selectedIds.contains(notif.id)) {
                                        selectedIds - notif.id
                                    } else {
                                        selectedIds + notif.id
                                    }
                                },
                                onPinToggle = {
                                    val index = NotificationState.notifications.indexOf(notif)
                                    if (index != -1) {
                                        NotificationState.notifications[index] = notif.copy(isPinned = !notif.isPinned)
                                    }
                                },
                                onDelete = {
                                    NotificationState.notifications.remove(notif)
                                    if (selectedIds.contains(notif.id)) {
                                        selectedIds = selectedIds - notif.id
                                    }
                                },
                                onReply = { onReplyClick(notif) },
                                onActionClick = { action ->
                                    try {
                                        action.actionIntent?.send(context, 0, null)
                                    } catch (e: Exception) {}
                                }
                            )
                        }
                    }
                }
            }

            // ============ ALT BİLGİ ÇUBUĞU ============
            if (notificationCount > 0) {
                Divider(color = dividerColor, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$notificationCount bildirim",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (selectionMode && selectedIds.isNotEmpty()) {
                            Text(
                                text = "${selectedIds.size} seçili",
                                color = accentColor,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Temizle",
                                color = accentColor,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable {
                                    selectedIds.forEach { id ->
                                        NotificationState.notifications.removeAll { it.id == id }
                                    }
                                    selectedIds = emptySet()
                                    selectionMode = false
                                }
                            )
                        }
                        Text(
                            text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()),
                            color = textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// ==================== BİLDİRİM KARTI ====================

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun NotificationCard(
    notification: WindowsNotification,
    isDarkTheme: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    cardColor: Color,
    accentColor: Color,
    selectionMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPinToggle: () -> Unit,
    onDelete: () -> Unit,
    onReply: () -> Unit,
    onActionClick: (Notification.Action) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    var expanded by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    // Uygulama ikonu
    val appIcon = remember(notification.packageName) {
        try {
            pm.getApplicationIcon(notification.packageName).toBitmap(48, 48).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    // Zaman bilgisi
    val timeText = remember(notification.timestamp) {
        val diff = System.currentTimeMillis() - notification.timestamp
        when {
            diff < 60_000 -> "Şimdi"
            diff < 3600_000 -> "${diff / 60_000} dk"
            diff < 86400_000 -> "${diff / 3600_000} sa"
            else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(notification.timestamp))
        }
    }

    // Bildirim öncelik rengi
    val priorityColor = when (notification.priority) {
        Notification.PRIORITY_HIGH -> Color(0xFFFF4444)
        Notification.PRIORITY_DEFAULT -> accentColor
        Notification.PRIORITY_LOW -> Color(0xFF888888)
        else -> Color.Transparent
    }

    // Animasyonlu kart
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(
            animationSpec = tween(200)
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isHovered) 4.dp else 2.dp,
                    shape = RoundedCornerShape(10.dp)
                )
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isSelected) accentColor.copy(alpha = 0.15f)
                    else cardColor
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (selectionMode) {
                            onSelect()
                        } else {
                            // Genişletme / daraltma
                            expanded = !expanded
                            if (expanded) showActions = true
                        }
                    }
                )
                .pointerInput(Unit) { /* Sağ tık vs. */ }
                .padding(12.dp),
            color = Color.Transparent
        ) {
            Column {
                // ============ ÜST BÖLÜM: İKON, BAŞLIK, ZAMAN, PIN ============
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Uygulama ikonu
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = notification.appName.take(2).uppercase(),
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    // Başlık ve metin
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = notification.title,
                                color = textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            // Öncelik işareti
                            if (notification.priority >= Notification.PRIORITY_HIGH) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(priorityColor)
                                )
                            }
                            // Kategori etiketi
                            Text(
                                text = notification.category,
                                color = textSecondary,
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE8E8E8)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (expanded) notification.text else notification.text.take(80) + if (notification.text.length > 80) "..." else "",
                            color = textSecondary,
                            fontSize = 13.sp,
                            maxLines = if (expanded) 10 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Sağ taraftaki butonlar (zaman, pin, sil)
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = timeText,
                            color = textSecondary,
                            fontSize = 10.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Pin butonu
                            Icon(
                                if (notification.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                                contentDescription = if (notification.isPinned) "Sabitlendi" else "Sabitle",
                                tint = if (notification.isPinned) accentColor else textSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onPinToggle() }
                            )
                            // Sil butonu
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Kaldır",
                                tint = textSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onDelete() }
                            )
                        }
                    }
                }

                // ============ ALT BÖLÜM: EYLEMLER (Expanded) ============
                AnimatedVisibility(
                    visible = expanded && notification.actions.isNotEmpty(),
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(
                            color = if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE0E0E0),
                            thickness = 0.5.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Eylem butonları
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // RemoteInput yanıtlama varsa
                            if (notification.replyPendingIntent != null && notification.remoteInput != null) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onReply() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = accentColor
                                ) {
                                    Text(
                                        text = "Yanıtla",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Diğer eylemler
                            notification.actions.forEach { action ->
                                if (action.getRemoteInputs().isNullOrEmpty()) {
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onActionClick(action) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isDarkTheme) Color(0xFF3D3D3D) else Color(0xFFE8E8E8)
                                    ) {
                                        Text(
                                            text = action.title.toString(),
                                            color = textPrimary,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}