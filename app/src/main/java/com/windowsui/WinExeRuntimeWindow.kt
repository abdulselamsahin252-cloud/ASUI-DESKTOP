package com.windowsui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.winlator.AsuiBridge
import com.winlator.core.Callback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** ASUI icinden Windows .exe calistirma penceresi (motor: gomulu Winlator = Wine + Box64). */
@Composable
fun WinExeRuntimeWindow(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = context as? LifecycleOwner
    var selectedExe by remember { mutableStateOf<File?>(null) }
    var runtimeReady by remember { mutableStateOf(WinExeRuntimeManager.isReady(context)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(
            if (WinExeRuntimeManager.isReady(context)) {
                "Altyapı hazır. Bir .exe seç."
            } else {
                "Altyapı henüz kurulmadı. Önce \"Altyapıyı Kur\" düğmesine bas."
            }
        )
    }

    // Kurulum ekranindan donunce durumu yenile.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                runtimeReady = WinExeRuntimeManager.isReady(context)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            busy = true
            status = "EXE dosyası ASUI alanına kopyalanıyor…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    WinExeRuntimeManager.importExe(context, uri)
                }
                selectedExe = result.getOrNull()
                status = result.fold(
                    onSuccess = { "Dosya hazır: ${it.name}" },
                    onFailure = { it.message ?: "Dosya alınamadı." }
                )
                runtimeReady = WinExeRuntimeManager.isReady(context)
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Windows Uygulamaları", style = MaterialTheme.typography.titleLarge)
        Text(
            "Windows .exe dosyalarını ASUI içinden çalıştır. Ayrı uygulama gerekmez.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Uyumluluk altyapısı", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (runtimeReady) {
                        "Wine + Box64 kurulu."
                    } else {
                        "Wine + Box64 dosyaları ilk kullanımda açılır (birkaç dakika sürebilir)."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                    if (!runtimeReady) {
                        Button(
                            onClick = { 
                                try {
                                    // Activity'yi başlat
                                    val intent = AsuiBridge.createSetupIntent(context)
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.applicationContext.startActivity(intent)
                                } catch (e: Exception) {
                                    // Hata olursa ekranda göster
                                    android.widget.Toast.makeText(context, "Hata: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    e.printStackTrace()
                                }
                            },
                            enabled = !busy
                        ) { Text("Altyapıyı Kur") }
                    }

            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                enabled = !busy
            ) { Text(".exe Dosyası Seç") }
            OutlinedButton(onClick = onClose) { Text("Kapat") }
        }

        selectedExe?.let { file ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Seçilen dosya", style = MaterialTheme.typography.titleSmall)
                    Text(file.name, style = MaterialTheme.typography.bodyMedium)
                    Text("Boyut: ${formatBytes(file.length())}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text(status, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                val exe = selectedExe ?: return@Button
                busy = true
                status = "Ortam hazırlanıyor…"
                AsuiBridge.prepareContainer(
                    context,
                    Callback<Int> { containerId ->
                        busy = false
                        if (containerId > 0) {
                            status = "${exe.name} başlatılıyor…"
                            context.startActivity(
                                AsuiBridge.createRunIntent(context, containerId, exe.name)
                            )
                        } else {
                            status = "Ortam (container) oluşturulamadı."
                        }
                    }
                )
            },
            enabled = selectedExe != null && runtimeReady && !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Çalıştır") }

        Text(
            "Not: Sadece ARM64 cihazlarda çalışır. İlk açılış yavaş olabilir; her program çalışmayabilir.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

/** Winlator motoruna giden depolama + durum köprüsü. */
object WinExeRuntimeManager {
    fun isReady(context: Context): Boolean = AsuiBridge.isRuntimeInstalled(context)

    fun importExe(context: Context, uri: Uri): Result<File> = runCatching {
        val displayName = queryDisplayName(context, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "selected.exe"
        val safeName = displayName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._() -]"), "_")
            .take(120)
        require(safeName.endsWith(".exe", ignoreCase = true)) {
            "Lütfen .exe uzantılı bir Windows programı seç."
        }
        val dir = AsuiBridge.getProgramsDir(context)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Uygulama dosya alanı oluşturulamadı.")
        val destination = File(dir, safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().buffered().use { output -> input.copyTo(output) }
        } ?: throw IOException("Seçilen dosya okunamadı.")
        require(destination.isFile && destination.length() > 0L) { "Dosya boş veya kopyalanamadı." }
        destination
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }
}
