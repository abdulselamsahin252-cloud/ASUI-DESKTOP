package com.windowsui
import androidx.compose.foundation.lazy.items

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ==================== VERİ SINIFLARI ====================

data class StoreApp(
    val packageName: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap? = null,
    val developer: String = "",
    val rating: Float = 0f,
    val installs: String = "",
    val isInstalled: Boolean = false,
    val isUpdatable: Boolean = false,
    val version: String = "",
    val description: String = "",
    val category: String = "Diğer",
    val screenshots: List<String> = emptyList(),
    val downloadCount: Int = 0
)

data class StoreCategory(
    val name: String,
    val icon: Int
)

data class DownloadProgress(
    val packageName: String,
    val progress: Float,
    val status: String // "downloading", "installing", "done", "failed"
)

// ==================== ASUI STORE ====================

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun StoreWindow(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val scope = rememberCoroutineScope()

    // ============ STATE ============
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tümü") }
    var selectedApp by remember { mutableStateOf<StoreApp?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var installedApps by remember { mutableStateOf<List<StoreApp>>(emptyList()) }
    var recommendedApps by remember { mutableStateOf<List<StoreApp>>(emptyList()) }
    var downloadProgresses by remember { mutableStateOf<Map<String, DownloadProgress>>(emptyMap()) }

    // Kategoriler (Windows Store tarzı)
    val categories = listOf(
        "Tümü" to Icons.Default.Apps,
        "Oyun" to Icons.Default.Games,
        "Sosyal" to Icons.Default.Chat,
        "Müzik" to Icons.Default.MusicNote,
        "Fotoğraf" to Icons.Default.PhotoCamera,
        "Üretkenlik" to Icons.Default.Work,
        "Finans" to Icons.Default.AttachMoney,
        "Eğitim" to Icons.Default.School
    )

    // Varsayılan uygulama listesi (örnek)
    val defaultApps = listOf(
        StoreApp(
            packageName = "com.android.chrome",
            label = "Chrome",
            developer = "Google LLC",
            rating = 4.5f,
            installs = "5B+",
            category = "Üretkenlik",
            description = "Hızlı, güvenli ve kişiselleştirilebilir web tarayıcısı."
        ),
        StoreApp(
            packageName = "com.whatsapp",
            label = "WhatsApp",
            developer = "WhatsApp LLC",
            rating = 4.3f,
            installs = "2B+",
            category = "Sosyal",
            description = "Dünyanın en popüler mesajlaşma uygulaması."
        ),
        StoreApp(
            packageName = "com.spotify.music",
            label = "Spotify",
            developer = "Spotify AB",
            rating = 4.4f,
            installs = "1B+",
            category = "Müzik",
            description = "Milyonlarca şarkı ve podcast."
        ),
        StoreApp(
            packageName = "com.instagram.android",
            label = "Instagram",
            developer = "Meta Platforms",
            rating = 4.2f,
            installs = "1B+",
            category = "Sosyal",
            description = "Fotoğraf ve video paylaşma platformu."
        ),
        StoreApp(
            packageName = "com.netflix.mediaclient",
            label = "Netflix",
            developer = "Netflix Inc.",
            rating = 4.6f,
            installs = "500M+",
            category = "Eğlence",
            description = "Dizi, film ve belgesel platformu."
        ),
        StoreApp(
            packageName = "com.microsoft.office.officehubrow",
            label = "Microsoft Office",
            developer = "Microsoft Corporation",
            rating = 4.1f,
            installs = "500M+",
            category = "Üretkenlik",
            description = "Word, Excel, PowerPoint ve daha fazlası."
        )
    )

    // ============ YÜKLEME ============
    LaunchedEffect(Unit) {
        isLoading = true
        // Cihazdaki yüklü uygulamaları kontrol et
        val allApps = DesktopManager.loadApps(context, includeSystem = false)
        val installedPackages = allApps.map { it.packageName }.toSet()

        val updatedApps = defaultApps.map { app ->
            val isInstalled = installedPackages.contains(app.packageName)
            val isUpdatable = isInstalled // Gerçek güncelleme kontrolü ileride yapılabilir
            app.copy(
                isInstalled = isInstalled,
                isUpdatable = isUpdatable,
                // İkonları al (placeholder)
                icon = try {
                    val appInfo = pm.getApplicationInfo(app.packageName, 0)
                    appInfo.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            )
        }

        installedApps = updatedApps.filter { it.isInstalled }
        recommendedApps = updatedApps.filter { !it.isInstalled }
        isLoading = false
    }

    // ============ FİLTRELEME ============
    val filteredApps = remember(recommendedApps, searchQuery, selectedCategory) {
        recommendedApps.filter { app ->
            val matchesSearch = searchQuery.isEmpty() ||
                    app.label.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "Tümü" || app.category == selectedCategory
            matchesSearch && matchesCategory
        }
    }

    // ============ UYGULAMA AÇMA / YÜKLEME / KALDIRMA ============
    fun openApp(app: StoreApp) {
        try {
            val intent = pm.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                // Play Store'a yönlendir
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (e: Exception) {
            Log.e("StoreWindow", "Uygulama açılamadı", e)
        }
    }

    fun downloadApp(app: StoreApp) {
        scope.launch {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    Log.e("StoreWindow", "Uygulama açılamadı", ex)
                }
            }
        }
    }

    fun updateApp(app: StoreApp) {
        downloadApp(app) // Güncelleme için Play Store'a yönlendir
    }

    fun uninstallApp(app: StoreApp) {
        scope.launch {
            try {
                val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {}
        }
    }

    // ============ ANA UI ============
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable { /* Kapatma için değil, sadece arka plan */ }
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.85f)
                .padding(16.dp)
                .shadow(32.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (SystemState.theme == "dark") Color(0xFF1C1C1C) else Color.White
                )
                .clickable { /* Kartın içine tıklamayı engelleme */ },
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ============ BAŞLIK ÇUBUĞU ============
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (SystemState.theme == "dark") Color(0xFF2D2D2D) else Color(0xFFF0F0F0)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🛒", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ASUI Store",
                            color = if (SystemState.theme == "dark") Color.White else Color.Black,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0078D7))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Beta", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = if (SystemState.theme == "dark") Color.White else Color.Black
                        )
                    }
                }

                // ============ ARAMA ÇUBUĞU ============
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Uygulama ara...",
                                color = if (SystemState.theme == "dark") Color.LightGray else Color.DarkGray
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Temizle")
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0078D7),
                            unfocusedBorderColor = if (SystemState.theme == "dark") Color.DarkGray else Color.LightGray,
                            focusedTextColor = if (SystemState.theme == "dark") Color.White else Color.Black,
                            unfocusedTextColor = if (SystemState.theme == "dark") Color.White else Color.Black
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { /* Arama yap */ })
                    )

                    // 🟢 FIX: DropdownMenu expanded kontrolü
                    var showCategoryMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { showCategoryMenu = !showCategoryMenu },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (SystemState.theme == "dark") Color.White else Color.Black
                            )
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filtre")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(selectedCategory.take(12))
                        }

                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false },
                            modifier = Modifier.background(
                                if (SystemState.theme == "dark") Color(0xFF2D2D2D) else Color.White
                            )
                        ) {
                            categories.forEach { (name, icon) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(name)
                                        }
                                    },
                                    onClick = {
                                        selectedCategory = name
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // ============ İÇERİK ============
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Uygulamalar yükleniyor...",
                                color = if (SystemState.theme == "dark") Color.White else Color.Black
                            )
                        }
                    }
                } else {
                    if (selectedApp != null) {
                        StoreAppDetail(
                            app = selectedApp!!,
                            onBack = { selectedApp = null },
                            onOpen = { openApp(it) },
                            onDownload = { downloadApp(it) },
                            onUpdate = { updateApp(it) },
                            onUninstall = { uninstallApp(it) }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            // Yüklü uygulamalar
                            if (installedApps.isNotEmpty()) {
                                Text(
                                    text = "📦 Yüklü Uygulamalar",
                                    color = if (SystemState.theme == "dark") Color.White else Color.Black,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(installedApps.take(10)) { app ->
                                        StoreAppCard(
                                            app = app,
                                            onClick = { selectedApp = app },
                                            onAction = {
                                                if (app.isUpdatable) updateApp(app)
                                                else if (app.isInstalled) openApp(app)
                                                else downloadApp(app)
                                            }
                                        )
                                    }
                                }
                            }

                            // Önerilen uygulamalar
                            Text(
                                text = "✨ Önerilen Uygulamalar",
                                color = if (SystemState.theme == "dark") Color.White else Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 140.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 80.dp)
                            ) {
                                items(filteredApps) { app ->
                                    StoreAppCard(
                                        app = app,
                                        onClick = { selectedApp = app },
                                        onAction = {
                                            if (app.isUpdatable) updateApp(app)
                                            else if (app.isInstalled) openApp(app)
                                            else downloadApp(app)
                                        }
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

// ==================== UYGULAMA KARTI ====================

@Composable
private fun StoreAppCard(
    app: StoreApp,
    onClick: () -> Unit,
    onAction: () -> Unit
) {
    val isDarkTheme = SystemState.theme == "dark"
    val cardColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = if (isDarkTheme) Color.LightGray else Color.DarkGray

    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0078D7)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.label.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = app.label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = app.developer,
                color = secondaryTextColor,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("⭐", fontSize = 12.sp)
                // 🟢 FIX: Locale.US ile formatlama
                Text(
                    text = String.format(Locale.US, "%.1f", app.rating),
                    color = textColor,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = app.installs,
                    color = secondaryTextColor,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        app.isUpdatable -> Color(0xFFFFA000)
                        app.isInstalled -> Color(0xFF4CAF50)
                        else -> Color(0xFF0078D7)
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = when {
                        app.isUpdatable -> "Güncelle"
                        app.isInstalled -> "Aç"
                        else -> "Yükle"
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==================== UYGULAMA DETAYI ====================

@Composable
private fun StoreAppDetail(
    app: StoreApp,
    onBack: () -> Unit,
    onOpen: (StoreApp) -> Unit,
    onDownload: (StoreApp) -> Unit,
    onUpdate: (StoreApp) -> Unit,
    onUninstall: (StoreApp) -> Unit
) {
    val isDarkTheme = SystemState.theme == "dark"
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = if (isDarkTheme) Color.LightGray else Color.DarkGray
    val context = LocalContext.current
    val pm = context.packageManager

    // 🟢 FIX: Uygulama açma kontrolü
    val canOpen = try {
        pm.getLaunchIntentForPackage(app.packageName) != null
    } catch (e: Exception) {
        false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = textColor)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Uygulama Detayı",
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0078D7)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.label.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = app.label,
                    color = textColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = app.developer,
                    color = secondaryTextColor,
                    fontSize = 14.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⭐ ${String.format(Locale.US, "%.1f", app.rating)}", color = textColor)
                    Text("•", color = secondaryTextColor)
                    Text(app.installs, color = secondaryTextColor)
                    Text("•", color = secondaryTextColor)
                    Text(app.category, color = secondaryTextColor)
                }
                Text(
                    text = if (app.isInstalled) "✅ Yüklü" else "❌ Yüklü değil",
                    color = if (app.isInstalled) Color(0xFF4CAF50) else Color(0xFFE53935),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Açıklama",
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = app.description,
                    color = secondaryTextColor,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 🟢 FIX: Ana buton – Aç / Güncelle / Yükle
            Button(
                onClick = {
                    when {
                        app.isUpdatable -> onUpdate(app)
                        app.isInstalled && canOpen -> onOpen(app)
                        app.isInstalled -> onUninstall(app)
                        else -> onDownload(app)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        app.isUpdatable -> Color(0xFFFFA000)
                        app.isInstalled && canOpen -> Color(0xFF4CAF50)
                        else -> Color(0xFF0078D7)
                    }
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when {
                        app.isUpdatable -> "Güncelle"
                        app.isInstalled && canOpen -> "Aç"
                        else -> "Play Store'da Aç"
                    },
                    color = Color.White
                )
            }

            if (app.isInstalled) {
                Button(
                    onClick = { onUninstall(app) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    modifier = Modifier.weight(0.5f)
                ) {
                    Text("Kaldır", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "📌 Bu uygulama Play Store üzerinden yönetilmektedir.",
            color = secondaryTextColor,
            fontSize = 11.sp
        )
    }
}