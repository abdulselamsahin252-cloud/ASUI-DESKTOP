package com.windowsui

import androidx.compose.ui.geometry.Offset
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * DesktopStorage – Masaüstü kalıcı depolama yöneticisi.
 *
 * DesktopState ile entegre çalışır, simge konumları, özel isimler, widget'lar,
 * klasörler ve tüm masaüstü ayarlarını kaydeder, yükler, yedekler ve geri yükler.
 *
 * Özellikler:
 * - Simge konumları ve özel isimler (DesktopState ile senkronize)
 * - Masaüstü düzenini dışa aktarma (JSON dosyası olarak)
 * - Masaüstü düzenini içe aktarma (JSON dosyasından)
 * - Otomatik ve manuel yedekleme
 * - Birden fazla profil (farklı masaüstü düzenleri) desteği
 * - Geri yükleme noktaları (snapshot) yönetimi
 * - Dosya seçici ile içe/dışa aktarma (Storage Access Framework)
 * - Yedekleme listesi ve silme
 * - JSON formatında tam serileştirme (Offset adapter ile)
 */
object DesktopStorage {

    private const val TAG = "DesktopStorage"
    private const val BACKUP_DIR = "desktop_backups"
    private const val EXPORT_FILE_NAME = "desktop_layout.json"

    // Gson instance (DesktopState ile aynı adapter'ları kullan)
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Offset::class.java, OffsetSerializer())
        .registerTypeAdapter(Offset::class.java, OffsetDeserializer())
        .setPrettyPrinting()
        .create()

    // ==================== TEMEL KAYDETME / YÜKLEME ====================

    /**
     * Masaüstü durumunu SharedPreferences'e kaydeder.
     * DesktopState.saveState()'i çağırır.
     */
    fun saveState(context: Context) {
        DesktopState.saveState(context)
    }

    /**
     * Masaüstü durumunu SharedPreferences'ten yükler.
     * DesktopState.loadState()'i çağırır.
     */
    fun loadState(context: Context) {
        DesktopState.loadState(context)
    }

    // ==================== DIŞA AKTAR (EXPORT) ====================

    /**
     * Masaüstü düzenini JSON dosyası olarak dışa aktarır.
     * @param context Context
     * @param uri Hedef dosya URI'si (Storage Access Framework ile seçilmiş)
     * @return Başarılı mı?
     */
    suspend fun exportLayout(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val snapshot = DesktopState.createSnapshot()
            val json = gson.toJson(snapshot)

            val resolver = context.contentResolver
            resolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json)
                    writer.flush()
                }
                Log.d(TAG, "Masaüstü düzeni dışa aktarıldı: $uri")
                return@withContext true
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Dışa aktarma hatası", e)
            false
        }
    }

    /**
     * Varsayılan dosya adı ile dışa aktarır (Download klasörüne).
     * @param context Context
     * @param fileName Dosya adı (varsayılan: desktop_layout_YYYYMMDD_HHmmss.json)
     * @return Dışa aktarılan dosyanın URI'si veya null
     */
    suspend fun exportLayoutToDownloads(
        context: Context,
        fileName: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val finalName = fileName ?: "desktop_layout_$timestamp.json"

            val contentResolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val uri = contentResolver.insert(
                android.provider.MediaStore.Files.getContentUri("external"),
                contentValues
            ) ?: return@withContext null

            val snapshot = DesktopState.createSnapshot()
            val json = gson.toJson(snapshot)

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json)
                    writer.flush()
                }
            }
            Log.d(TAG, "Masaüstü düzeni indirilenlere aktarıldı: $finalName")
            return@withContext uri
        } catch (e: Exception) {
            Log.e(TAG, "Dışa aktarma hatası", e)
            null
        }
    }

    // ==================== İÇE AKTAR (IMPORT) ====================

    /**
     * JSON dosyasından masaüstü düzenini içe aktarır.
     * @param context Context
     * @param uri Kaynak dosya URI'si
     * @return Başarılı mı?
     */
    suspend fun importLayout(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            resolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val snapshot: DesktopSnapshot = gson.fromJson(
                        reader,
                        DesktopSnapshot::class.java
                    )
                    DesktopState.restoreSnapshot(snapshot, context)
                    Log.d(TAG, "Masaüstü düzeni içe aktarıldı: $uri")
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "İçe aktarma hatası", e)
            false
        }
    }

    // ==================== YEDEKLEME (BACKUP) ====================

    /**
     * Mevcut masaüstü düzeninin yedeğini alır.
     * @param context Context
     * @param backupName Yedek adı (opsiyonel)
     * @return Yedek dosyasının yolu (File) veya null
     */
    suspend fun createBackup(context: Context, backupName: String? = null): File? = withContext(Dispatchers.IO) {
        try {
            val backupDir = File(context.filesDir, BACKUP_DIR)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val name = backupName ?: "backup_$timestamp"
            val backupFile = File(backupDir, "$name.json")

            val snapshot = DesktopState.createSnapshot()
            val json = gson.toJson(snapshot)

            backupFile.writeText(json)
            Log.d(TAG, "Yedek oluşturuldu: ${backupFile.absolutePath}")
            return@withContext backupFile
        } catch (e: Exception) {
            Log.e(TAG, "Yedek oluşturma hatası", e)
            null
        }
    }

    /**
     * Otomatik yedekleme (periyodik olarak çağrılabilir).
     * Sadece son 10 yedeği tutar.
     */
    suspend fun autoBackup(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val backupDir = File(context.filesDir, BACKUP_DIR)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            // Eski yedekleri temizle (sadece son 10'u tut)
            val backups = backupDir.listFiles()
                ?.filter { it.isFile && it.extension == "json" }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            if (backups.size >= 10) {
                backups.drop(10).forEach { it.delete() }
            }

            val backupFile = createBackup(context, "auto_${System.currentTimeMillis()}")
            Log.d(TAG, "Otomatik yedek alındı: ${backupFile?.name}")
            return@withContext backupFile
        } catch (e: Exception) {
            Log.e(TAG, "Otomatik yedekleme hatası", e)
            null
        }
    }

    /**
     * Yedek listesini döndürür.
     */
    fun listBackups(context: Context): List<File> {
        val backupDir = File(context.filesDir, BACKUP_DIR)
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Bir yedeği geri yükler.
     * @param context Context
     * @param backupFile Yedek dosyası
     * @return Başarılı mı?
     */
    suspend fun restoreBackup(context: Context, backupFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = backupFile.readText()
            val snapshot: DesktopSnapshot = gson.fromJson(json, DesktopSnapshot::class.java)
            DesktopState.restoreSnapshot(snapshot, context)
            Log.d(TAG, "Yedek geri yüklendi: ${backupFile.name}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Yedek geri yükleme hatası", e)
            false
        }
    }

    /**
     * Bir yedeği siler.
     */
    fun deleteBackup(backupFile: File): Boolean {
        return try {
            backupFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Yedek silme hatası", e)
            false
        }
    }

    // ==================== PROFİL YÖNETİMİ ====================

    /**
     * Farklı bir masaüstü profilini kaydeder.
     * @param context Context
     * @param profileName Profil adı
     * @return Başarılı mı?
     */
    suspend fun saveProfile(context: Context, profileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val profileDir = File(context.filesDir, "profiles")
            if (!profileDir.exists()) {
                profileDir.mkdirs()
            }

            val snapshot = DesktopState.createSnapshot()
            val json = gson.toJson(snapshot)
            val profileFile = File(profileDir, "$profileName.json")
            profileFile.writeText(json)

            Log.d(TAG, "Profil kaydedildi: $profileName")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Profil kaydetme hatası", e)
            false
        }
    }

    /**
     * Bir profili yükler.
     */
    suspend fun loadProfile(context: Context, profileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val profileFile = File(context.filesDir, "profiles/$profileName.json")
            if (!profileFile.exists()) return@withContext false

            val json = profileFile.readText()
            val snapshot: DesktopSnapshot = gson.fromJson(json, DesktopSnapshot::class.java)
            DesktopState.restoreSnapshot(snapshot, context)

            Log.d(TAG, "Profil yüklendi: $profileName")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Profil yükleme hatası", e)
            false
        }
    }

    /**
     * Tüm profilleri listeler.
     */
    fun listProfiles(context: Context): List<String> {
        val profileDir = File(context.filesDir, "profiles")
        if (!profileDir.exists()) return emptyList()
        return profileDir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
    }

    /**
     * Bir profili siler.
     */
    fun deleteProfile(context: Context, profileName: String): Boolean {
        return try {
            val profileFile = File(context.filesDir, "profiles/$profileName.json")
            profileFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Profil silme hatası", e)
            false
        }
    }

    // ==================== YARDIMCI ====================

    /**
     * Dosya seçici ile dışa aktarma (SAF).
     * @param context Context
     * @return URI veya null
     */
    suspend fun exportWithFilePicker(context: Context): Uri? = withContext(Dispatchers.IO) {
        // Bu fonksiyon UI tarafında ActivityResultLauncher ile kullanılmalıdır.
        // Şimdilik sadece URI döndürür.
        return@withContext null
    }

    /**
     * Dosya seçici ile içe aktarma (SAF).
     */
    suspend fun importWithFilePicker(context: Context): Uri? = withContext(Dispatchers.IO) {
        // Bu fonksiyon UI tarafında ActivityResultLauncher ile kullanılmalıdır.
        return@withContext null
    }

    /**
     * Tüm masaüstü verilerini sıfırlar (fabrika ayarları).
     */
    fun resetDesktop(context: Context) {
        DesktopState.iconOffsets.clear()
        DesktopState.customNames.clear()
        DesktopState.widgets.clear()
        DesktopState.widgetOrder.clear()
        DesktopState.folders.clear()
        DesktopState.selectedPackages.clear()
        DesktopState.renamingPackage = null
        DesktopState.desktopZoom = 1.0f
        DesktopState.isLayoutLocked = false
        DesktopState.arrangementMode = "name"
        DesktopState.showHidden = false
        DesktopState.saveState(context)
        Log.d(TAG, "Masaüstü sıfırlandı")
    }

    /**
     * Yedekleme dizinini temizler.
     */
    fun cleanBackups(context: Context) {
        val backupDir = File(context.filesDir, BACKUP_DIR)
        if (backupDir.exists()) {
            backupDir.deleteRecursively()
            backupDir.mkdirs()
            Log.d(TAG, "Yedekler temizlendi")
        }
    }
}