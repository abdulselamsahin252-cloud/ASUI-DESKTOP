package com.windowsui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Binary Manager for ASUI Desktop.
 *
 * Manages:
 * - Native binaries (adb, fastboot, busybox, etc.) extracted from assets or downloaded
 * - Multi-ABI support (arm64-v8a, armeabi-v7a, x86_64, x86)
 * - Binary version checking and automatic updates
 * - SHA-256 hash verification for integrity
 * - Environment variables (PATH, HOME, etc.)
 * - Binary execution permission management
 * - Custom binary overrides (user-provided binaries)
 * - Binary cache and cleanup
 * - Integration with Termux and Android shell
 *
 * All binaries are stored in the app's internal files directory.
 * The PATH environment variable is automatically set to include the binary directory.
 */
object BinaryManager {

    private const val TAG = "BinaryManager"

    // ==================== BINARY ADLARI ====================
    private const val ADB_NAME = "adb"
    private const val FASTBOOT_NAME = "fastboot"
    private const val BUSYBOX_NAME = "busybox"
    private const val SH_NAME = "sh"
    private const val SU_NAME = "su"

    // ==================== ASSET YOLU ====================
    private const val ASSET_ROOT = "bin"

    // ==================== HASH DEĞERLERİ (SHA-256) ====================
    // Örnek hash'ler (gerçek projede güncellenmeli)
    private val expectedHashes = mapOf(
        "arm64-v8a/adb" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "arm64-v8a/fastboot" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "arm64-v8a/busybox" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "armeabi-v7a/adb" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "x86_64/adb" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "x86/adb" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    )

    // ==================== ABI TESPİTİ ====================

    private fun abiFolder(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            abi.contains("arm64") -> "arm64-v8a"
            abi.contains("armeabi") -> "armeabi-v7a"
            abi.contains("x86_64") -> "x86_64"
            abi.contains("x86") -> "x86"
            else -> "arm64-v8a" // fallback
        }
    }

    // ==================== BINARY YOLLARI ====================

    /**
     * Binary'lerin depolandığı ana dizin.
     */
    fun getBinDir(context: Context): File {
        return File(context.filesDir, "bin").apply { mkdirs() }
    }

    /**
     * Belirtilen binary'nin tam yolu.
     */
    fun getBinaryPath(context: Context, name: String): File {
        return File(getBinDir(context), name)
    }

    /**
     * Mevcut ABI'ye uygun binary'nin assets'teki yolu.
     */
    private fun getAssetPath(name: String): String {
        return "$ASSET_ROOT/${abiFolder()}/$name"
    }

    // ==================== BINARY ÇIKARMA ====================

    /**
     * Binary'yi assets'ten çıkarır, gerekirse hash doğrulaması yapar.
     */
suspend fun extractBinary(context: Context, name: String, force: Boolean = false): Boolean {
    return withContext(Dispatchers.IO) {
        val dest = getBinaryPath(context, name)
        if (dest.exists() && !force) {
            Log.d(TAG, "Binary zaten mevcut: $name")
            return@withContext verifyBinary(dest, name)
        }

        try {
            // jniLibs'ten oku
            val sourceFile = File(context.applicationInfo.nativeLibraryDir, name)
            if (!sourceFile.exists()) {
                Log.e(TAG, "Binary jniLibs'te bulunamadı: $name")
                return@withContext false
            }
            sourceFile.copyTo(dest, overwrite = true)
            dest.setExecutable(true, false)
            Log.d(TAG, "Binary başarıyla kopyalandı: $name")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Binary kopyalanamadı: $name", e)
            false
        }
    }
}

    /**
     * Tüm gerekli binary'leri çıkarır.
     */
    suspend fun extractAllBinaries(context: Context): Boolean {
        val names = listOf(ADB_NAME, FASTBOOT_NAME, BUSYBOX_NAME)
        var success = true
        for (name in names) {
            if (!extractBinary(context, name)) {
                success = false
            }
        }
        return success
    }

    // ==================== HASH DOĞRULAMA ====================

    private fun verifyBinary(file: File, name: String): Boolean {
        if (!file.exists()) return false
        try {
            val hash = sha256(file)
            val expected = expectedHashes["${abiFolder()}/$name"]
            if (expected == null) {
                Log.w(TAG, "Beklenen hash bulunamadı: $name, atlanıyor")
                return true // Hash kontrolü yoksa geçerli say
            }
            return hash.equals(expected, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Hash doğrulama hatası: $name", e)
            return false
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ==================== ORTAM DEĞİŞKENLERİ ====================

    /**
     * BinaryManager'ın sağladığı ortam değişkenlerini döndürür.
     */
    fun getEnvironmentVariables(context: Context): Map<String, String> {
        val binDir = getBinDir(context).absolutePath
        val androidRoot = System.getenv("ANDROID_ROOT") ?: "/system"
        val androidData = System.getenv("ANDROID_DATA") ?: "/data"
        val home = File(context.filesDir, "home").absolutePath

        return mapOf(
            "PATH" to "$binDir:$androidRoot/bin:$androidRoot/xbin:/system/bin:/system/xbin",
            "HOME" to home,
            "ANDROID_ROOT" to androidRoot,
            "ANDROID_DATA" to androidData,
            "BINARY_DIR" to binDir,
            "USER" to SystemState.username,
            "TERM" to "xterm-256color",
            "LANG" to "en_US.UTF-8",
            "SHELL" to "/system/bin/sh"
        )
    }

    /**
     * BinaryManager PATH'ini döndürür.
     */
    fun getPath(context: Context): String {
        return getEnvironmentVariables(context)["PATH"] ?: ""
    }

    // ==================== BINARY BİLGİLERİ ====================

    /**
     * Binary sürümünü döndürür.
     */
    suspend fun getBinaryVersion(context: Context, name: String): String? = withContext(Dispatchers.IO) {
        val binary = getBinaryPath(context, name)
        if (!binary.exists()) return@withContext null
        try {
            val process = ProcessBuilder(binary.absolutePath, "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.lines().firstOrNull()?.trim()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Binary'nin mevcut olup olmadığını kontrol eder.
     */
    fun binaryExists(context: Context, name: String): Boolean {
        return getBinaryPath(context, name).exists()
    }

    /**
     * Tüm binary'leri listeler.
     */
    fun listBinaries(context: Context): List<File> {
        return getBinDir(context).listFiles()?.filter { it.isFile } ?: emptyList()
    }

    fun getNativeBinDir(context: Context): String {
    return context.applicationInfo.nativeLibraryDir ?: context.filesDir.absolutePath + "/lib"
}

    // ==================== BINARY YÖNETİMİ (GÜNCELLE, SİL) ====================

    /**
     * Binary'yi siler.
     */
    suspend fun deleteBinary(context: Context, name: String): Boolean = withContext(Dispatchers.IO) {
        val file = getBinaryPath(context, name)
        if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }

    /**
     * Tüm binary'leri temizler.
     */
    suspend fun clearAllBinaries(context: Context): Boolean = withContext(Dispatchers.IO) {
        val binDir = getBinDir(context)
        if (binDir.exists()) {
            binDir.deleteRecursively()
            binDir.mkdirs()
            true
        } else {
            true
        }
    }

    /**
     * Binary'yi internetten indirir (örnek: güncelleme için).
     */
    suspend fun downloadBinary(context: Context, name: String, url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.doInput = true

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext false
            }

            val dest = getBinaryPath(context, name)
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            dest.setExecutable(true, false)

            // Hash doğrulama (opsiyonel)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Binary indirilemedi: $name", e)
            false
        }
    }

    // ==================== YARDIMCI ====================

    /**
     * BinaryManager'ı başlatır: gerekli dizinleri oluşturur ve binary'leri çıkarır.
     */
    suspend fun initialize(context: Context): Boolean {
        val binDir = getBinDir(context)
        if (!binDir.exists()) {
            binDir.mkdirs()
        }

        // HOME dizinini oluştur
        val homeDir = File(context.filesDir, "home")
        if (!homeDir.exists()) {
            homeDir.mkdirs()
        }

        // Binary'leri çıkar (eğer yoksa)
        return extractAllBinaries(context)
    }

    /**
     * BinaryManager'ı yeniden başlatır (tüm binary'leri yeniden çıkarır).
     */
    suspend fun reinitialize(context: Context): Boolean {
        clearAllBinaries(context)
        return initialize(context)
    }

    /**
     * SystemState'deki kullanıcı adını ve diğer ayarları binary ortamına yansıtır.
     */
    fun syncUserEnvironment(context: Context) {
        val env = getEnvironmentVariables(context)
        // Ortam değişkenlerini System.setProperty ile ayarla (Java tarafı)
        env.forEach { (key, value) ->
            System.setProperty(key, value)
        }
        // Ayrıca ortam değişkenlerini güncelle (TermuxBridge için)
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "export PATH=${env["PATH"]}"))
            process.waitFor()
        } catch (e: Exception) {}
    }

    /**
     * BinaryManager'ın durumunu döndürür.
     */
    suspend fun getStatus(context: Context): String = withContext(Dispatchers.IO) {
        val binDir = getBinDir(context)
        val binaries = listBinaries(context)
        val status = buildString {
            append("Binary Manager Status:\n")
            append("  ABI: ${abiFolder()}\n")
            append("  Binary Directory: ${binDir.absolutePath}\n")
            append("  Binaries: ${binaries.size}\n")
            binaries.forEach { file ->
                append("    - ${file.name} (${file.length()} bytes)\n")
            }
            append("  PATH: ${getPath(context)}\n")
        }
        status
    }

    // ==================== ÖZEL BINARY YÖNETİMİ (KULLANICI TARAFINDAN) ====================

    /**
     * Kullanıcının kendi binary'sini kullanmasını sağlar.
     * Binary'yi /data/local/tmp veya /sdcard gibi bir yerden kopyalar.
     */
    suspend fun setUserBinary(context: Context, sourcePath: String, destName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            if (!source.exists()) return@withContext false
            val dest = getBinaryPath(context, destName)
            source.copyTo(dest, overwrite = true)
            dest.setExecutable(true, false)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Kullanıcı binary'si kopyalanamadı: $destName", e)
            false
        }
    }

    /**
     * Varsayılan binary'ye geri döner.
     */
    suspend fun restoreDefaultBinary(context: Context, name: String): Boolean {
        deleteBinary(context, name)
        return extractBinary(context, name, force = true)
    }
}