// ====================================================================
// 2. EXTENSIONS.KT – TAM VE HATASIZ
// ====================================================================
package com.windowsui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

inline fun Context.withActivity(
    crossinline onFound: (Activity) -> Unit,
    crossinline onNotFound: () -> Unit = {}
) {
    val activity = findActivity()
    if (activity != null) {
        onFound(activity)
    } else {
        onNotFound()
    }
}

@Composable
fun rememberBitmapFromUri(uri: Uri?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    
    LaunchedEffect(uri) {
        bitmap = Utils.loadBitmapFromUri(context, uri)
    }
    
    return bitmap
}

@Composable
fun rememberBitmapFromUriSuspend(uri: Uri?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            Utils.loadBitmapFromUri(context, uri)
        }
    }
    
    return bitmap
}

fun Context.openNotificationSettings() {
    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

fun Context.openAccessibilitySettings() {
    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

fun Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${this@openAppSettings.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

fun Context.openAppSettingsForPackage(packageName: String) {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

fun Context.openWifiSettings() {
    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

fun Context.openBluetoothSettings() {
    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

fun Context.openAirplaneSettings() {
    startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

fun Context.openLocationSettings() {
    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

fun Color.toAndroidColor(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}

fun Int.toComposeColor(): Color {
    return Color(
        red = android.graphics.Color.red(this) / 255f,
        green = android.graphics.Color.green(this) / 255f,
        blue = android.graphics.Color.blue(this) / 255f,
        alpha = android.graphics.Color.alpha(this) / 255f
    )
}

fun Color.isDark(): Boolean {
    val luminance = 0.299 * red + 0.587 * green + 0.114 * blue
    return luminance < 0.5
}

fun Color.contrastColor(): Color {
    return if (isDark()) Color.White else Color.Black
}

fun String.toTitleCase(): String {
    return if (isNotEmpty()) {
        replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
        }
    } else this
}

fun String.ellipsize(maxLength: Int): String {
    return if (length > maxLength) {
        take(maxLength) + "..."
    } else this
}

fun String?.isNullOrBlankOrEmpty(): Boolean {
    return this == null || this.isBlank() || this.isEmpty()
}

fun String.toIntSafe(default: Int = 0): Int {
    return toIntOrNull() ?: default
}

fun String.toLongSafe(default: Long = 0L): Long {
    return toLongOrNull() ?: default
}