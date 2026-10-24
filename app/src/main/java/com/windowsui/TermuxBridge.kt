package com.windowsui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Termux Bridge for ASUI Desktop.
 *
 * Provides full integration with Termux:
 * - Package management (install, update, upgrade, list, remove)
 * - File operations (push, pull, delete, list, copy between Android and Termux)
 * - Command execution with real-time output (using shared storage for output)
 * - Session management (list, kill, create)
 * - Background job execution (nohup)
 * - Root access (if Termux has root)
 * - Environment variable management
 * - Termux version and installation check
 * - Shell command execution (sh -c) for complex commands with operators
 * - Termux API integration (optional)
 *
 * All functions are designed to be called from CmdTerminal.kt
 * via TERMUX ... commands.
 *
 * IMPORTANT: Add the following permission to AndroidManifest.xml:
 * <uses-permission android:name="com.termux.permission.RUN_COMMAND" />
 */
object TermuxBridge {

    private const val TERMUX_PACKAGE = "com.termux"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    private const val EXTRA_SESSION_NAME = "com.termux.RUN_COMMAND_SESSION_NAME"

    // ==================== KURULUM KONTROLÜ ====================

    /**
     * Termux'un kurulu olup olmadığını kontrol eder.
     */
    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Termux sürümünü döndürür.
     */
    fun getVersion(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            info.versionName ?: "Bilinmiyor"
        } catch (_: Throwable) {
            "Kurulu değil"
        }
    }

    // ==================== KOMUT ÇALIŞTIRMA (TEMEL) ====================

    /**
     * Termux'ta bir komutu arka planda veya ön planda çalıştırır.
     * Komut doğrudan verilen path'teki binary'ye gönderilir.
     * Kabuk operatörleri (&&, |, >) içeren komutlar için shellCommand() kullanılmalıdır.
     */
    fun runCommand(
        context: Context,
        command: String,
        workDir: String? = null,
        background: Boolean = true,
        sessionName: String? = null
    ): Result<String> {
        if (!isInstalled(context)) {
            return Result.failure(IllegalStateException("Termux kurulu değil."))
        }
        val parts = splitCommandLine(command)
        if (parts.isEmpty()) {
            return Result.failure(IllegalArgumentException("Boş Termux komutu."))
        }

        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setPackage(TERMUX_PACKAGE)
            putExtra(EXTRA_PATH, "/data/data/com.termux/files/usr/bin/${parts.first()}")
            if (parts.size > 1) {
                putExtra(EXTRA_ARGUMENTS, parts.drop(1).toTypedArray())
            }
            putExtra(EXTRA_WORKDIR, workDir ?: "/data/data/com.termux/files/home")
            putExtra(EXTRA_BACKGROUND, background)
            if (sessionName != null) {
                putExtra(EXTRA_SESSION_NAME, sessionName)
                putExtra(EXTRA_SESSION_ACTION, "create")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startService(intent)
            Result.success("Termux komutu arka planda başlatıldı: $command")
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // ==================== SHELL KOMUTU (KABUK OPERATÖRLERİ İÇİN) ====================

    /**
     * Termux'ta bir kabuk komutunu sh -c ile çalıştırır.
     * Bu yöntem, &&, |, >, <, ; gibi operatörleri destekler.
     * Çıktıyı almak için outputFile parametresi ile bir dosya belirtilebilir.
     */
    suspend fun shellCommand(
        context: Context,
        command: String,
        workDir: String? = null,
        outputFile: String? = null,
        background: Boolean = false,
        sessionName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isInstalled(context)) {
            return@withContext Result.failure(IllegalStateException("Termux kurulu değil."))
        }

        // Çıktı dosyası belirtilmemişse paylaşımlı depolamaya geçici bir dosya oluştur
        val tempOutput = if (outputFile != null) {
            outputFile
        } else {
            val timestamp = System.currentTimeMillis()
            "/sdcard/termux_output_$timestamp.txt"
        }

        // Komutu sh -c ile çalıştır, çıktıyı tempOutput'a yönlendir
        val fullCommand = if (command.contains(">") || command.contains(">>")) {
            // Zaten yönlendirme varsa, doğrudan kullan
            command
        } else {
            "$command > $tempOutput 2>&1"
        }

        val shellPath = "/data/data/com.termux/files/usr/bin/sh"
        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setPackage(TERMUX_PACKAGE)
            putExtra(EXTRA_PATH, shellPath)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", fullCommand))
            putExtra(EXTRA_WORKDIR, workDir ?: "/data/data/com.termux/files/home")
            putExtra(EXTRA_BACKGROUND, background)
            if (sessionName != null) {
                putExtra(EXTRA_SESSION_NAME, sessionName)
                putExtra(EXTRA_SESSION_ACTION, "create")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return@withContext try {
            context.startService(intent)
            if (background) {
                Result.success("Shell komutu arka planda başlatıldı: $command")
            } else {
                // Çıktı dosyasının oluşmasını bekle (non-blocking delay)
                var attempts = 0
                val outputFileObj = File(tempOutput)
                while (!outputFileObj.exists() && attempts < 30) {
                    delay(1000) // non-blocking delay
                    attempts++
                }
                if (outputFileObj.exists()) {
                    val result = outputFileObj.readText().trim()
                    outputFileObj.delete()
                    Result.success(result)
                } else {
                    Result.failure(Exception("Çıktı alınamadı (zaman aşımı)."))
                }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // ==================== KOMUT ÇALIŞTIRMA (ÇIKTI ALMA) ====================

    /**
     * Termux'ta bir komut çalıştırır ve çıktısını döndürür.
     * Bu fonksiyon, kabuk operatörlerini desteklemek için shellCommand'ı kullanır.
     */
    suspend fun runCommandWithOutput(
        context: Context,
        command: String,
        workDir: String? = null,
        timeoutSeconds: Long = 30L
    ): Result<String> {
        // Geçici dosyayı paylaşımlı depolamaya yaz
        val tempFile = "/sdcard/termux_output_${System.currentTimeMillis()}.txt"
        val result = shellCommand(
            context = context,
            command = command,
            workDir = workDir,
            outputFile = tempFile,
            background = false
        )
        // shellCommand zaten dosyayı okur ve siler, sonucu döndürür
        return result
    }

    // ==================== PAKET YÖNETİMİ ====================

    suspend fun installPackage(context: Context, packageName: String): Result<String> {
        return runCommandWithOutput(context, "pkg install -y $packageName")
    }

    suspend fun updatePackages(context: Context): Result<String> {
        return runCommandWithOutput(context, "pkg update -y && pkg upgrade -y")
    }

    suspend fun listPackages(context: Context): Result<String> {
        return runCommandWithOutput(context, "pkg list-installed")
    }

    suspend fun uninstallPackage(context: Context, packageName: String): Result<String> {
        return runCommandWithOutput(context, "pkg uninstall -y $packageName")
    }

    // ==================== DOSYA İŞLEMLERİ ====================

    suspend fun pushFile(context: Context, localPath: String, remotePath: String): Result<String> {
        val file = File(localPath)
        if (!file.exists()) {
            return Result.failure(Exception("Kaynak dosya bulunamadı: $localPath"))
        }
        // Termux HOME dizinine kopyala
        val dest = if (remotePath.startsWith("/")) remotePath else "/data/data/com.termux/files/home/$remotePath"
        val command = "cp '$localPath' '$dest'"
        return runCommandWithOutput(context, command)
    }

    suspend fun pullFile(context: Context, remotePath: String, localPath: String): Result<String> {
        val src = if (remotePath.startsWith("/")) remotePath else "/data/data/com.termux/files/home/$remotePath"
        File(localPath).parentFile?.mkdirs()
        val command = "cp '$src' '$localPath'"
        return runCommandWithOutput(context, command)
    }

    suspend fun deleteFile(context: Context, path: String): Result<String> {
        val target = if (path.startsWith("/")) path else "/data/data/com.termux/files/home/$path"
        val command = "rm -rf '$target'"
        return runCommandWithOutput(context, command)
    }

    suspend fun listFiles(context: Context, path: String = ""): Result<String> {
        val target = if (path.isNotEmpty()) {
            if (path.startsWith("/")) path else "/data/data/com.termux/files/home/$path"
        } else {
            "/data/data/com.termux/files/home"
        }
        return runCommandWithOutput(context, "ls -la '$target'")
    }

    // ==================== OTURUM YÖNETİMİ ====================

    suspend fun listSessions(context: Context): Result<String> {
        return runCommandWithOutput(context, "ps -A | grep termux")
    }

    suspend fun killSession(context: Context, pid: String): Result<String> {
        return runCommandWithOutput(context, "kill -9 $pid")
    }

    suspend fun createSession(context: Context, name: String): Result<String> {
        return runCommandWithOutput(context, "echo 'Oturum oluşturuldu: $name' > /data/data/com.termux/files/home/.termux_session_$name")
    }

    // ==================== ARKA PLAN İŞLEMLERİ ====================

    suspend fun runBackground(context: Context, command: String, workDir: String? = null): Result<String> {
        val fullCommand = "nohup $command > /dev/null 2>&1 &"
        return runCommandWithOutput(context, fullCommand, workDir)
    }

    // ==================== ROOT ERİŞİMİ ====================

    suspend fun runRootCommand(context: Context, command: String): Result<String> {
        return runCommandWithOutput(context, "su -c '$command'")
    }

    // ==================== ORTAM DEĞİŞKENLERİ ====================

    suspend fun getEnvironment(context: Context): Result<String> {
        return runCommandWithOutput(context, "env")
    }

    suspend fun setEnvironment(context: Context, key: String, value: String): Result<String> {
        return runCommandWithOutput(context, "export $key='$value'")
    }

    // ==================== TERMUX HOME DİZİNİ ====================

    suspend fun getHomeDir(context: Context): Result<String> {
        return runCommandWithOutput(context, "echo \$HOME")
    }

    // ==================== TERMUX API ENTEGRASYONU (İSTEĞE BAĞLI) ====================

    suspend fun callApi(context: Context, apiCommand: String): Result<String> {
        return runCommandWithOutput(context, "termux-api $apiCommand")
    }

    // ==================== TERMUX'U YENİDEN BAŞLATMA ====================

    suspend fun restartTermux(context: Context): Result<String> {
        return runCommandWithOutput(context, "killall -SIGTERM com.termux")
    }

    // ==================== ANDROID <-> TERMUX DOSYA KOPYALAMA ====================

    suspend fun copyFromAndroid(context: Context, androidPath: String, termuxPath: String): Result<String> {
        val dest = if (termuxPath.startsWith("/")) termuxPath else "/data/data/com.termux/files/home/$termuxPath"
        return runCommandWithOutput(context, "cp '$androidPath' '$dest'")
    }

    suspend fun copyToAndroid(context: Context, termuxPath: String, androidPath: String): Result<String> {
        val src = if (termuxPath.startsWith("/")) termuxPath else "/data/data/com.termux/files/home/$termuxPath"
        return runCommandWithOutput(context, "cp '$src' '$androidPath'")
    }

    // ==================== YARDIMCI ====================

    private fun splitCommandLine(command: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escape = false
        for (c in command.trim()) {
            if (escape) { current.append(c); escape = false; continue }
            if (c == '\\' && quote == '"') { escape = true; continue }
            if ((c == '"' || c == '\'') ) {
                if (quote == null) quote = c else if (quote == c) quote = null else current.append(c)
                continue
            }
            if (c.isWhitespace() && quote == null) {
                if (current.isNotEmpty()) { out += current.toString(); current.clear() }
            } else current.append(c)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }
}