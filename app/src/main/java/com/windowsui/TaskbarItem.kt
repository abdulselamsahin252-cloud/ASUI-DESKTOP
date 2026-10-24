// ====================================================================
// 3. TASKBARITEM.KT – TAM VE HATASIZ
// ====================================================================
package com.windowsui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * TaskbarItem – Görev Çubuğu Öğe Veri Sınıfı
 *
 * Görev çubuğunda görünen tüm öğeleri (uygulama, sistem, widget) temsil eder.
 * TaskbarComponent ile tam uyumludur.
 *
 * 🚀 Özellikler:
 * - Uygulama, sistem, widget, sanal masaüstü desteği.
 * - Z-index (öncelik) ve gruplama.
 * - Canlı durum (çalışıyor, sabitli, bildirim var).
 * - Özel ikon ve renk desteği.
 * - Açılır menü ve bağlam menüsü verileri.
 */
data class TaskbarItem(
    // ============ TEMEL BİLGİLER ============
    val id: String,
    val label: String,
    val packageName: String? = null,
    val icon: ImageBitmap? = null,
    val iconRes: Int? = null,
    
    // ============ DURUM ============
    val isRunning: Boolean = false,
    val isPinned: Boolean = false,
    val isSystem: Boolean = false,
    val isWidget: Boolean = false,
    val isDesktop: Boolean = false,
    val hasNotification: Boolean = false,
    val isActive: Boolean = false,
    val isMinimized: Boolean = false,
    
    // ============ ÖNCELİK VE GRUPLAMA ============
    val priority: Int = 0,
    val groupId: String? = null,
    val orderIndex: Int = 0,
    
    // ============ GELİŞMİŞ ============
    val badgeCount: Int = 0,
    val badgeColor: androidx.compose.ui.graphics.Color? = null,
    val customColor: androidx.compose.ui.graphics.Color? = null,
    
    // ============ META VERİ ============
    val windowId: String? = null,
    val contentId: String? = null,
    val extra: Map<String, Any> = emptyMap(),
    
    // ============ AKSİYONLAR ============
    val onClick: (() -> Unit)? = null,
    val onLongClick: (() -> Unit)? = null,
    val onClose: (() -> Unit)? = null
) {
    /**
     * Öğenin görünen adını döndürür.
     */
    fun getDisplayName(): String = label
    
    /**
     * Öğenin ikonunu döndürür (öncelik sırası).
     * Context ile birlikte iconRes'ten yükler.
     */
    fun getIcon(context: Context): ImageBitmap? {
        return icon ?: iconRes?.let { resId ->
            try {
                BitmapFactory.decodeResource(context.resources, resId).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    
    /**
     * Öğenin türünü döndürür.
     */
    fun getType(): String = when {
        isWidget -> "widget"
        isSystem -> "system"
        isDesktop -> "desktop"
        packageName != null -> "app"
        else -> "other"
    }
    
    /**
     * Öğenin kısa durumunu döndürür.
     */
    fun getStatus(): String = when {
        isRunning && isActive -> "🟢 Çalışıyor"
        isRunning -> "🟡 Arka planda"
        isPinned -> "📌 Sabitli"
        hasNotification -> "🔔 Bildirim var"
        else -> "⏸️ Beklemede"
    }
}

/**
 * TaskbarItem oluşturmak için yardımcı fonksiyonlar.
 */
object TaskbarItemFactory {
    
    /**
     * DesktopApp'ten TaskbarItem oluşturur.
     */
    fun fromDesktopApp(
        app: DesktopApp,
        isRunning: Boolean = false,
        isPinned: Boolean = false,
        hasNotification: Boolean = false,
        isActive: Boolean = false,
        isMinimized: Boolean = false,
        windowId: String? = null,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        onClose: (() -> Unit)? = null
    ): TaskbarItem {
        return TaskbarItem(
            id = app.packageName,
            label = app.label,
            packageName = app.packageName,
            icon = app.iconBitmap,
            isRunning = isRunning,
            isPinned = isPinned,
            isSystem = app.isSystem,
            isWidget = false,
            hasNotification = hasNotification,
            isActive = isActive,
            isMinimized = isMinimized,
            windowId = windowId,
            contentId = app.packageName,
            onClick = onClick,
            onLongClick = onLongClick,
            onClose = onClose
        )
    }
    
    /**
     * Sistem öğesi oluşturur.
     */
    fun createSystemItem(
        id: String,
        label: String,
        iconRes: Int,
        onClick: (() -> Unit)? = null
    ): TaskbarItem {
        return TaskbarItem(
            id = id,
            label = label,
            iconRes = iconRes,
            isSystem = true,
            onClick = onClick
        )
    }
    
    /**
     * Widget öğesi oluşturur.
     */
    fun createWidgetItem(
        id: String,
        label: String,
        icon: ImageBitmap? = null,
        onClick: (() -> Unit)? = null
    ): TaskbarItem {
        return TaskbarItem(
            id = id,
            label = label,
            icon = icon,
            isWidget = true,
            onClick = onClick
        )
    }
    
    /**
     * Sanal masaüstü öğesi oluşturur.
     */
    fun createDesktopItem(
        id: String,
        label: String,
        isActive: Boolean = false,
        onClick: (() -> Unit)? = null
    ): TaskbarItem {
        return TaskbarItem(
            id = id,
            label = label,
            isDesktop = true,
            isActive = isActive,
            onClick = onClick
        )
    }
}

/**
 * TaskbarItem listesi için extension fonksiyonları.
 */
fun List<TaskbarItem>.filterRunning(): List<TaskbarItem> = filter { it.isRunning }
fun List<TaskbarItem>.filterPinned(): List<TaskbarItem> = filter { it.isPinned }
fun List<TaskbarItem>.filterActive(): List<TaskbarItem> = filter { it.isActive }
fun List<TaskbarItem>.filterWithNotifications(): List<TaskbarItem> = filter { it.hasNotification }

fun List<TaskbarItem>.sortByPriority(): List<TaskbarItem> = sortedByDescending { it.priority }
fun List<TaskbarItem>.sortByOrder(): List<TaskbarItem> = sortedBy { it.orderIndex }

fun List<TaskbarItem>.groupByType(): Map<String, List<TaskbarItem>> = groupBy { it.getType() }
fun List<TaskbarItem>.groupByGroupId(): Map<String?, List<TaskbarItem>> = groupBy { it.groupId }