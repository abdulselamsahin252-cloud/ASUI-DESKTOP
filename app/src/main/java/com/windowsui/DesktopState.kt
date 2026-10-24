package com.windowsui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.*

/**
 * DesktopState – Masaüstü durum yöneticisi (Singleton)
 *
 * Tüm masaüstü öğelerinin (simgeler, widget'lar, klasörler) durumunu,
 * konumlarını, özel isimlerini, seçimlerini ve kalıcı ayarlarını yönetir.
 *
 * 🟢 FIX:
 * - saveState() artık sadece sürükleme bittiğinde çağrılır (sürükleme sırasında çağrılmaz)
 * - Context doğrudan parametre olarak geçilir, AppGlobal kaldırıldı
 * - Offset için özel Gson serializer/deserializer eklendi
 * - Tüm koleksiyonlar için güvenli serileştirme
 *
 * Özellikler:
 * - Simge konumları ve özel isimler (kalıcı)
 * - Çoklu seçim (selectedPackages)
 * - Yeniden adlandırma durumu (renamingPackage)
 * - Widget yönetimi (ekle, kaldır, konumlandır, boyutlandır)
 * - Klasör yönetimi (oluştur, sil, içine öğe ekle/çıkar)
 * - Masaüstü panosu (kopyala/kes/yapıştır)
 * - Geri Al / İleri Al (Undo/Redo) – son 20 işlem
 * - Masaüstü yakınlaştırma (zoom) seviyesi
 * - Masaüstü düzeni kilidi
 * - Otomatik düzenleme modu
 * - Duvar kağıdı slayt gösterisi ayarları
 * - Anlık görüntü (snapshot) alma ve geri yükleme
 * - Kalıcılık (SharedPreferences ile JSON serileştirme)
 */
object DesktopState {

    private const val TAG = "DesktopState"
    private const val PREFS_NAME = "ASUI_DesktopStatePrefs"

    // 🟢 FIX: Gson Offset adapter ile yapılandırıldı
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Offset::class.java, OffsetSerializer())
        .registerTypeAdapter(Offset::class.java, OffsetDeserializer())
        .create()

    // ==================== SİMGELER ====================
    val selectedPackages = mutableStateListOf<String>()
    val iconOffsets = mutableStateMapOf<String, Offset>()
    val customNames = mutableStateMapOf<String, String>()
    var renamingPackage by mutableStateOf<String?>(null)

    // ==================== WİDGET'LAR ====================
    val widgets = mutableStateMapOf<String, DesktopWidgetData>()
    val widgetOrder = mutableStateListOf<String>()

    // ==================== KLASÖRLER (Offset yerine x,y ile) ====================
    // 🟢 FIX: Offset yerine ayrı x,y alanları kullanıldı
    val folders = mutableStateMapOf<String, DesktopFolderData>()

    // ==================== PANO ====================
    val clipboardItems = mutableStateListOf<String>()
    var isCutOperation by mutableStateOf(false)

    // ==================== GERİ AL / İLERİ AL ====================
    private val undoStack = mutableListOf<DesktopAction>()
    private val redoStack = mutableListOf<DesktopAction>()
    private var isUndoRedoInProgress = false

    // ==================== MASAÜSTÜ DÜZENİ ====================
    var desktopZoom by mutableStateOf(1.0f)
    var isLayoutLocked by mutableStateOf(false)
    var arrangementMode by mutableStateOf("name")
    var showHidden by mutableStateOf(false)

    // ==================== DUVAR KAĞIDI SLAYT ====================
    var wallpaperSlideshowEnabled by mutableStateOf(false)
    var wallpaperSlideshowInterval by mutableStateOf(30)
    val wallpaperSlideshowUris = mutableStateListOf<String>()

    // ==================== KALICILIK (Context ile) ====================
    private var isSavingScheduled = false

    /**
     * 🟢 FIX: saveState artık context parametresi alıyor ve sürükleme sırasında çağrılmıyor.
     * Bu fonksiyon sadece sürükleme bittiğinde veya önemli değişikliklerde çağrılmalıdır.
     */
    fun saveState(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Offset'leri serileştirilebilir hale getir
            val offsetData = iconOffsets.map { (key, offset) ->
                mapOf("key" to key, "x" to offset.x, "y" to offset.y)
            }

            val data = mapOf(
                "iconOffsets" to gson.toJson(offsetData),
                "customNames" to gson.toJson(customNames),
                "widgets" to gson.toJson(widgets.values.toList()),
                "widgetOrder" to gson.toJson(widgetOrder),
                "folders" to gson.toJson(folders.values.toList()),
                "desktopZoom" to desktopZoom,
                "isLayoutLocked" to isLayoutLocked,
                "arrangementMode" to arrangementMode,
                "showHidden" to showHidden,
                "wallpaperSlideshowEnabled" to wallpaperSlideshowEnabled,
                "wallpaperSlideshowInterval" to wallpaperSlideshowInterval,
                "wallpaperSlideshowUris" to gson.toJson(wallpaperSlideshowUris)
            )

            prefs.edit().putString("desktop_state", gson.toJson(data)).apply()
            Log.d(TAG, "Durum kaydedildi (${iconOffsets.size} simge, ${widgets.size} widget)")
        } catch (e: Exception) {
            Log.e(TAG, "Kaydetme hatası", e)
        }
    }

    /**
     * 🟢 FIX: loadState context parametresi alıyor.
     */
    fun loadState(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString("desktop_state", null) ?: return

            val type = object : TypeToken<Map<String, String>>() {}.type
            val data: Map<String, String> = gson.fromJson(json, type)

            // IconOffsets (JSON'dan offset verilerini oku)
            data["iconOffsets"]?.let { offsetJson ->
                val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val list: List<Map<String, Any>> = gson.fromJson(offsetJson, listType)
                list.forEach { entry ->
                    val key = entry["key"] as? String ?: return@forEach
                    val x = (entry["x"] as? Number)?.toFloat() ?: 0f
                    val y = (entry["y"] as? Number)?.toFloat() ?: 0f
                    iconOffsets[key] = Offset(x, y)
                }
            }

            // CustomNames
            data["customNames"]?.let {
                val mapType = object : TypeToken<Map<String, String>>() {}.type
                val map: Map<String, String> = gson.fromJson(it, mapType)
                customNames.putAll(map)
            }

            // Widgets
            data["widgets"]?.let {
                val listType = object : TypeToken<List<DesktopWidgetData>>() {}.type
                val list: List<DesktopWidgetData> = gson.fromJson(it, listType)
                widgets.clear()
                list.forEach { widgets[it.id] = it }
            }

            // WidgetOrder
            data["widgetOrder"]?.let {
                val listType = object : TypeToken<List<String>>() {}.type
                val list: List<String> = gson.fromJson(it, listType)
                widgetOrder.clear()
                widgetOrder.addAll(list)
            }

            // Folders
            data["folders"]?.let {
                val listType = object : TypeToken<List<DesktopFolderData>>() {}.type
                val list: List<DesktopFolderData> = gson.fromJson(it, listType)
                folders.clear()
                list.forEach { folders[it.id] = it }
            }

            // Diğer ayarlar
            desktopZoom = (data["desktopZoom"]?.toFloatOrNull() ?: 1.0f)
            isLayoutLocked = data["isLayoutLocked"]?.toBoolean() ?: false
            arrangementMode = data["arrangementMode"] ?: "name"
            showHidden = data["showHidden"]?.toBoolean() ?: false
            wallpaperSlideshowEnabled = data["wallpaperSlideshowEnabled"]?.toBoolean() ?: false
            wallpaperSlideshowInterval = data["wallpaperSlideshowInterval"]?.toIntOrNull() ?: 30

            data["wallpaperSlideshowUris"]?.let {
                val listType = object : TypeToken<List<String>>() {}.type
                val list: List<String> = gson.fromJson(it, listType)
                wallpaperSlideshowUris.clear()
                wallpaperSlideshowUris.addAll(list)
            }

            Log.d(TAG, "Durum yüklendi: ${iconOffsets.size} simge, ${widgets.size} widget")
        } catch (e: Exception) {
            Log.e(TAG, "Yükleme hatası", e)
        }
    }

    // ==================== WİDGET YÖNETİMİ ====================
    fun addWidget(type: String, x: Float = 100f, y: Float = 100f, context: Context): String {
        val id = "widget_${System.currentTimeMillis()}"
        val width = when (type) {
            "clock" -> 200
            "weather" -> 220
            "notes" -> 240
            "system_monitor" -> 260
            "calendar" -> 280
            else -> 200
        }
        val height = when (type) {
            "clock" -> 140
            "weather" -> 180
            "notes" -> 200
            "system_monitor" -> 160
            "calendar" -> 220
            else -> 150
        }
        widgets[id] = DesktopWidgetData(
            id = id,
            type = type,
            x = x,
            y = y,
            width = width,
            height = height
        )
        if (!widgetOrder.contains(id)) widgetOrder.add(id)
        saveState(context) // Önemli değişiklik, kaydedilir
        Log.d(TAG, "Widget eklendi: $type ($id)")
        return id
    }

    fun removeWidget(id: String, context: Context) {
        widgets.remove(id)
        widgetOrder.remove(id)
        saveState(context)
        Log.d(TAG, "Widget kaldırıldı: $id")
    }

    /**
     * 🟢 FIX: Widget güncellemesi sırasında saveState çağrılmaz.
     * Bu fonksiyon sadece konum/boyut günceller, kaydetme işlemi çağıran tarafından yapılmalıdır.
     */
    fun updateWidget(id: String, x: Float, y: Float, width: Int, height: Int) {
        widgets[id]?.let {
            widgets[id] = it.copy(x = x, y = y, width = width, height = height)
        }
    }

    /**
     * Widget güncellemesini kaydeder (sürükleme bittiğinde çağrılır).
     */
    fun saveWidgetState(context: Context) {
        saveState(context)
    }

    // ==================== KLASÖR YÖNETİMİ ====================
    fun createFolder(name: String, itemIds: List<String> = emptyList(), context: Context): String {
        val id = "folder_${System.currentTimeMillis()}"
        // Varsayılan konum (masaüstünün sol üstüne yakın)
        folders[id] = DesktopFolderData(
            id = id,
            name = name,
            items = itemIds.toMutableList(),
            posX = 50f,
            posY = 50f,
            isExpanded = false
        )
        itemIds.forEach { itemId ->
            if (selectedPackages.contains(itemId)) {
                selectedPackages.remove(itemId)
            }
            iconOffsets.remove(itemId)
        }
        iconOffsets[id] = Offset(50f, 50f)
        saveState(context)
        Log.d(TAG, "Klasör oluşturuldu: $name ($id), ${itemIds.size} öğe")
        return id
    }

    fun deleteFolder(id: String, context: Context) {
        folders[id]?.let { folder ->
            folder.items.forEach { itemId ->
                if (!selectedPackages.contains(itemId)) {
                    selectedPackages.add(itemId)
                }
            }
            folders.remove(id)
            iconOffsets.remove(id)
            customNames.remove(id)
            saveState(context)
            Log.d(TAG, "Klasör silindi: ${folder.name} ($id)")
        }
    }

    fun addItemToFolder(folderId: String, itemId: String, context: Context) {
        folders[folderId]?.let { folder ->
            if (!folder.items.contains(itemId)) {
                folder.items.add(itemId)
                selectedPackages.remove(itemId)
                saveState(context)
            }
        }
    }

    fun removeItemFromFolder(folderId: String, itemId: String, context: Context) {
        folders[folderId]?.let { folder ->
            if (folder.items.contains(itemId)) {
                folder.items.remove(itemId)
                if (!selectedPackages.contains(itemId)) {
                    selectedPackages.add(itemId)
                }
                saveState(context)
            }
        }
    }

    fun renameFolder(folderId: String, newName: String, context: Context) {
        folders[folderId]?.let {
            folders[folderId] = it.copy(name = newName)
            saveState(context)
        }
    }

    // ==================== PANO ====================
    fun copyItems(itemIds: List<String>) {
        clipboardItems.clear()
        clipboardItems.addAll(itemIds)
        isCutOperation = false
        Log.d(TAG, "${itemIds.size} öğe kopyalandı")
    }

    fun cutItems(itemIds: List<String>) {
        clipboardItems.clear()
        clipboardItems.addAll(itemIds)
        isCutOperation = true
        Log.d(TAG, "${itemIds.size} öğe kesildi")
    }

    fun pasteItems(targetId: String? = null, context: Context) {
        if (clipboardItems.isEmpty()) return
        val itemsToPaste = clipboardItems.toList()
        clipboardItems.clear()

        if (isCutOperation) {
            if (targetId != null) {
                itemsToPaste.forEach { itemId ->
                    addItemToFolder(targetId, itemId, context)
                }
            } else {
                selectedPackages.clear()
                selectedPackages.addAll(itemsToPaste)
            }
            isCutOperation = false
        } else {
            Log.d(TAG, "Kopyalama işlemi (kısayol oluşturma ileride)")
        }
        saveState(context)
    }

    // ==================== GERİ AL / İLERİ AL ====================
    fun pushAction(action: DesktopAction) {
        if (isUndoRedoInProgress) return
        redoStack.clear()
        undoStack.add(action)
        if (undoStack.size > 20) {
            undoStack.removeAt(0)
        }
    }

    fun undo(context: Context) {
        if (undoStack.isEmpty()) return
        isUndoRedoInProgress = true
        val action = undoStack.removeAt(undoStack.size - 1)
        action.undo()
        redoStack.add(action)
        isUndoRedoInProgress = false
        saveState(context)
        Log.d(TAG, "Geri alındı: ${action.type}")
    }

    fun redo(context: Context) {
        if (redoStack.isEmpty()) return
        isUndoRedoInProgress = true
        val action = redoStack.removeAt(redoStack.size - 1)
        action.redo()
        undoStack.add(action)
        isUndoRedoInProgress = false
        saveState(context)
        Log.d(TAG, "İleri alındı: ${action.type}")
    }

    // ==================== SEÇİM ====================
    fun clearSelection() {
        selectedPackages.clear()
        renamingPackage = null
    }

    fun selectSingle(id: String) {
        selectedPackages.clear()
        selectedPackages.add(id)
    }

    fun toggleSelection(id: String) {
        if (selectedPackages.contains(id)) {
            selectedPackages.remove(id)
        } else {
            selectedPackages.add(id)
        }
    }

    fun selectAll(visibleIds: List<String>) {
        selectedPackages.clear()
        selectedPackages.addAll(visibleIds)
    }

    // ==================== YARDIMCI ====================
    fun getItemType(id: String): String {
        return when {
            widgets.containsKey(id) -> "widget"
            folders.containsKey(id) -> "folder"
            id == "recycle_bin" -> "system"
            else -> "app"
        }
    }

    fun getDisplayName(id: String, defaultName: String): String {
        return customNames[id] ?: defaultName
    }

    // ==================== ANLIK GÖRÜNTÜ (SNAPSHOT) ====================
    fun createSnapshot(): DesktopSnapshot {
        return DesktopSnapshot(
            iconOffsets = iconOffsets.toMap(),
            customNames = customNames.toMap(),
            widgetData = widgets.values.toList(),
            widgetOrder = widgetOrder.toList(),
            folderData = folders.values.toList(),
            timestamp = System.currentTimeMillis()
        )
    }

    fun restoreSnapshot(snapshot: DesktopSnapshot, context: Context) {
        iconOffsets.clear()
        iconOffsets.putAll(snapshot.iconOffsets)

        customNames.clear()
        customNames.putAll(snapshot.customNames)

        widgets.clear()
        snapshot.widgetData.forEach { widgets[it.id] = it }

        widgetOrder.clear()
        widgetOrder.addAll(snapshot.widgetOrder)

        folders.clear()
        snapshot.folderData.forEach { folders[it.id] = it }

        saveState(context)
        Log.d(TAG, "Anlık görüntü geri yüklendi: ${snapshot.timestamp}")
    }
}

// ==================== OFFSET SERIALIZER / DESERIALIZER ====================

/**
 * 🟢 FIX: Offset için özel Gson serializer (serileştirme)
 */
class OffsetSerializer : JsonSerializer<Offset> {
    override fun serialize(
        src: Offset,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        val json = JsonObject()
        json.addProperty("x", src.x)
        json.addProperty("y", src.y)
        return json
    }
}

/**
 * 🟢 FIX: Offset için özel Gson deserializer (okuma)
 */
class OffsetDeserializer : JsonDeserializer<Offset> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Offset {
        val obj = json.asJsonObject
        val x = obj.get("x")?.asFloat ?: 0f
        val y = obj.get("y")?.asFloat ?: 0f
        return Offset(x, y)
    }
}

// ==================== VERİ SINIFLARI ====================

data class DesktopWidgetData(
    val id: String,
    val type: String,
    val x: Float,
    val y: Float,
    val width: Int = 200,
    val height: Int = 150,
    val data: Map<String, Any> = emptyMap()
)

/**
 * 🟢 FIX: Offset yerine posX, posY kullanıldı (serileştirme sorunları için)
 */
data class DesktopFolderData(
    val id: String,
    val name: String,
    val items: MutableList<String>,
    val posX: Float,
    val posY: Float,
    val isExpanded: Boolean = false
)

data class DesktopAction(
    val type: String,
    val undo: () -> Unit,
    val redo: () -> Unit
)

data class DesktopSnapshot(
    val iconOffsets: Map<String, Offset>,
    val customNames: Map<String, String>,
    val widgetData: List<DesktopWidgetData>,
    val widgetOrder: List<String>,
    val folderData: List<DesktopFolderData>,
    val timestamp: Long
)