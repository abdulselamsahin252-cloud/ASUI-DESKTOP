package com.windowsui

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OTG / USB ADB Bridge for ASUI Desktop.
 * 
 * Provides a full ADB interface over USB OTG and Wi-Fi.
 * Supports root, shell, push/pull, backup, restore, screen recording,
 * screenshot, install/uninstall, logcat, reboot, fastboot, sideload,
 * bugreport, and more.
 * 
 * All functions are designed to be called from CmdTerminal.kt
 * via OTG ADB ... commands.
 */
object OtgAdbBridge {
    private const val ASSET_ROOT = "bin"
    private const val ADB_NAME = "adb"
    private const val FASTBOOT_NAME = "fastboot"
    private const val TIMEOUT_SECONDS = 120L

    // ==================== ABI TESPİTİ (x86, x86_64, ARM64, ARMv7) ====================
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

    // ==================== BINARY ÇIKARMA ====================

    suspend fun adbPath(context: Context): File = withContext(Dispatchers.IO) {
        val out = File(context.filesDir, "bin/adb")
        if (!out.exists() || out.length() < 100_000L) {
            out.parentFile?.mkdirs()
            val asset = "$ASSET_ROOT/${abiFolder()}/$ADB_NAME"
            context.assets.open(asset).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        out.setExecutable(true, false)
        out
    }

    suspend fun fastbootPath(context: Context): File = withContext(Dispatchers.IO) {
        val out = File(context.filesDir, "bin/fastboot")
        if (!out.exists() || out.length() < 100_000L) {
            out.parentFile?.mkdirs()
            val asset = "$ASSET_ROOT/${abiFolder()}/$FASTBOOT_NAME"
            context.assets.open(asset).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        out.setExecutable(true, false)
        out
    }

    // ==================== ÇEKİRDEK EXECUTE FONKSİYONLARI ====================

    /**
     * ADB komutlarını çalıştırır. Root modu aktifse su ile çalıştırır.
     * USB OTG üzerinden veya Wi-Fi üzerinden ADB bağlantısını destekler.
     */
    suspend fun execute(
        context: Context,
        arguments: List<String>,
        root: Boolean = false,
        timeoutSeconds: Long = TIMEOUT_SECONDS
    ): List<String> = withContext(Dispatchers.IO) {
        val adb = adbPath(context)
        val command = arguments.joinToString(" ") { shellQuote(it) }

        // ADB over Wi-Fi: "connect" ile başlıyorsa özel işlem
        if (arguments.isNotEmpty() && arguments[0].equals("connect", true)) {
            return@withContext executeAdbCommand(context, adb, arguments, root, timeoutSeconds)
        }

        // Root modu aktifse su ile çalıştır
        val process = if (root) {
            ProcessBuilder("/system/bin/su", "-c", "${shellQuote(adb.absolutePath)} $command")
        } else {
            ProcessBuilder(listOf(adb.absolutePath) + arguments)
        }

        // Ortam değişkenlerini ayarla (ADB'nin düzgün çalışması için)
        process.environment()["HOME"] = File(context.filesDir, "adb-home").apply { mkdirs() }.absolutePath
        process.environment()["ADB_VENDOR_KEYS"] = File(context.filesDir, "adb-home/.android").apply { mkdirs() }.absolutePath
        process.environment()["ADB_LIBUSB"] = "1"
        process.redirectErrorStream(true)

        try {
            val p = process.start()
            val text = p.inputStream.bufferedReader().use { it.readText() }
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                return@withContext listOf("ADB: işlem zaman aşımına uğradı (${timeoutSeconds}s).")
            }
            val lines = text.replace("\r", "").split("\n").filter { it.isNotEmpty() }
            if (p.exitValue() != 0 && lines.isEmpty()) {
                listOf("ADB hata kodu: ${p.exitValue()}")
            } else {
                lines
            }
        } catch (t: Throwable) {
            listOf("ADB çalıştırılamadı: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    /**
     * Fastboot komutlarını çalıştırır.
     */
    suspend fun executeFastboot(
        context: Context,
        arguments: List<String>,
        timeoutSeconds: Long = TIMEOUT_SECONDS
    ): List<String> = withContext(Dispatchers.IO) {
        val fastboot = fastbootPath(context)
        val command = arguments.joinToString(" ") { shellQuote(it) }

        val process = ProcessBuilder(listOf(fastboot.absolutePath) + arguments)
        process.environment()["HOME"] = File(context.filesDir, "fastboot-home").apply { mkdirs() }.absolutePath
        process.redirectErrorStream(true)

        try {
            val p = process.start()
            val text = p.inputStream.bufferedReader().use { it.readText() }
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                return@withContext listOf("Fastboot: işlem zaman aşımına uğradı (${timeoutSeconds}s).")
            }
            val lines = text.replace("\r", "").split("\n").filter { it.isNotEmpty() }
            if (p.exitValue() != 0 && lines.isEmpty()) {
                listOf("Fastboot hata kodu: ${p.exitValue()}")
            } else {
                lines
            }
        } catch (t: Throwable) {
            listOf("Fastboot çalıştırılamadı: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    /**
     * ADB over Wi-Fi için özel komut işleme (ortam değişkenleri ile).
     */
    private suspend fun executeAdbCommand(
        context: Context,
        adb: File,
        arguments: List<String>,
        root: Boolean,
        timeoutSeconds: Long
    ): List<String> = withContext(Dispatchers.IO) {
        val command = arguments.joinToString(" ") { shellQuote(it) }
        val process = if (root) {
            ProcessBuilder("/system/bin/su", "-c", "${shellQuote(adb.absolutePath)} $command")
        } else {
            ProcessBuilder(listOf(adb.absolutePath) + arguments)
        }
        // Ortam değişkenlerini buraya da ekle
        process.environment()["HOME"] = File(context.filesDir, "adb-home").apply { mkdirs() }.absolutePath
        process.environment()["ADB_VENDOR_KEYS"] = File(context.filesDir, "adb-home/.android").apply { mkdirs() }.absolutePath
        process.environment()["ADB_LIBUSB"] = "1"
        process.redirectErrorStream(true)
        try {
            val p = process.start()
            val text = p.inputStream.bufferedReader().use { it.readText() }
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                return@withContext listOf("ADB Wi-Fi: zaman aşımı.")
            }
            text.replace("\r", "").split("\n").filter { it.isNotEmpty() }
        } catch (t: Throwable) {
            listOf("ADB Wi-Fi hatası: ${t.message}")
        }
    }

    private fun shellQuote(value: String): String {
        if (value.isEmpty()) return "''"
        return "'" + value.replace("'", "'\\''") + "'"
    }

    // ==================== YARDIMCI FONKSİYONLAR (Terminal ile uyumlu) ====================

    suspend fun getDevices(context: Context, root: Boolean = false): List<String> {
        return execute(context, listOf("devices"), root)
    }

    suspend fun enableRoot(context: Context): List<String> {
        return execute(context, listOf("root"), true)
    }

    suspend fun shell(context: Context, command: String, root: Boolean = false): List<String> {
        return execute(context, listOf("shell", command), root)
    }

    suspend fun push(context: Context, local: String, remote: String, root: Boolean = false): List<String> {
        return execute(context, listOf("push", local, remote), root)
    }

    suspend fun pull(context: Context, remote: String, local: String, root: Boolean = false): List<String> {
        return execute(context, listOf("pull", remote, local), root)
    }

    suspend fun backup(context: Context, output: String = "/sdcard/backup.ab", root: Boolean = false): List<String> {
        return execute(context, listOf("backup", "-apk", "-shared", "-all", "-system", "-f", output), root)
    }

    suspend fun restore(context: Context, input: String = "/sdcard/backup.ab", root: Boolean = false): List<String> {
        return execute(context, listOf("restore", input), root)
    }

    /**
     * Ekran kaydı başlatır. Varsayılan olarak 30 saniye kaydeder.
     * Arka planda çalıştırmak için `--background` eklenebilir.
     */
    suspend fun screenrecord(
        context: Context,
        output: String = "/sdcard/record.mp4",
        timeLimit: Int = 30,
        background: Boolean = false,
        root: Boolean = false
    ): List<String> {
        val args = mutableListOf("shell", "screenrecord")
        if (background) args.add("--background")
        args.add("--time-limit")
        args.add(timeLimit.toString())
        args.add(output)
        return execute(context, args, root)
    }

    /**
     * Ekran görüntüsü alır.
     */
    suspend fun screenshot(context: Context, output: String = "/sdcard/screenshot.png", root: Boolean = false): List<String> {
        return execute(context, listOf("shell", "screencap", "-p", output), root)
    }

    suspend fun install(context: Context, apkPath: String, root: Boolean = false): List<String> {
        return execute(context, listOf("install", "-r", apkPath), root)
    }

    suspend fun uninstall(context: Context, packageName: String, root: Boolean = false): List<String> {
        return execute(context, listOf("uninstall", packageName), root)
    }

    /**
     * Logcat çıktısını alır.
     * `-d` ile tek seferlik döküm alır ve çıkar.
     * Filtre eklenebilir (örnek: "ActivityManager:I *:S")
     */
    suspend fun logcat(context: Context, filter: String = "", dumpOnce: Boolean = true, root: Boolean = false): List<String> {
        val args = mutableListOf("logcat")
        if (dumpOnce) args.add("-d")
        if (filter.isNotEmpty()) args.add(filter)
        return execute(context, args, root)
    }

    suspend fun reboot(context: Context, mode: String = "", root: Boolean = false): List<String> {
        val args = mutableListOf("reboot")
        if (mode.isNotEmpty()) args.add(mode)
        return execute(context, args, root)
    }

    suspend fun rebootBootloader(context: Context, root: Boolean = false): List<String> {
        return execute(context, listOf("reboot", "bootloader"), root)
    }

    suspend fun sideload(context: Context, otaFile: String, root: Boolean = false): List<String> {
        return execute(context, listOf("sideload", otaFile), root)
    }

    suspend fun bugreport(context: Context, output: String = "/sdcard/bugreport.zip", root: Boolean = false): List<String> {
        return execute(context, listOf("bugreport", output), root)
    }

    suspend fun getProperties(context: Context, root: Boolean = false): List<String> {
        return execute(context, listOf("shell", "getprop"), root)
    }

    suspend fun setSystemSetting(context: Context, namespace: String, key: String, value: String, root: Boolean = false): List<String> {
        return execute(context, listOf("shell", "settings", "put", namespace, key, value), root)
    }

    suspend fun rootShell(context: Context, command: String): List<String> {
        return execute(context, listOf("shell", "su", "-c", command), true)
    }

    // ==================== ADB OVER Wi-Fi ====================

    suspend fun connectWifi(context: Context, ip: String, port: Int = 5555, root: Boolean = false): List<String> {
        return execute(context, listOf("connect", "$ip:$port"), root)
    }

    suspend fun disconnectWifi(context: Context, ip: String = "", root: Boolean = false): List<String> {
        val args = if (ip.isNotEmpty()) listOf("disconnect", ip) else listOf("disconnect")
        return execute(context, args, root)
    }

    // ==================== FASTBOOT KOMUTLARI ====================

    suspend fun fastbootDevices(context: Context): List<String> {
        return executeFastboot(context, listOf("devices"))
    }

    suspend fun fastbootOemUnlock(context: Context): List<String> {
        return executeFastboot(context, listOf("oem", "unlock"))
    }

    suspend fun fastbootFlash(context: Context, partition: String, file: String): List<String> {
        return executeFastboot(context, listOf("flash", partition, file))
    }

    suspend fun fastbootReboot(context: Context): List<String> {
        return executeFastboot(context, listOf("reboot"))
    }

    suspend fun fastbootErase(context: Context, partition: String): List<String> {
        return executeFastboot(context, listOf("erase", partition))
    }

    suspend fun fastbootGetvar(context: Context, varName: String): List<String> {
        return executeFastboot(context, listOf("getvar", varName))
    }

    suspend fun fastbootOemDeviceInfo(context: Context): List<String> {
        return executeFastboot(context, listOf("oem", "device-info"))
    }

    // ==================== GELİŞMİŞ / ÖZEL KOMUTLAR ====================

    /**
     * ADB shell ile cihaza dosya yazar (root gerektirir).
     */
    suspend fun writeFile(context: Context, remotePath: String, content: String, root: Boolean = true): List<String> {
        val escaped = content.replace("'", "'\\''")
        return execute(context, listOf("shell", "echo", "'$escaped'", ">", remotePath), root)
    }

    /**
     * ADB shell ile cihazdan dosya okur.
     */
    suspend fun readFile(context: Context, remotePath: String, root: Boolean = false): List<String> {
        return execute(context, listOf("shell", "cat", remotePath), root)
    }

    /**
     * ADB shell ile cihazda işlem çalıştırır ve çıktısını döndürür.
     */
    suspend fun runCommand(context: Context, command: String, root: Boolean = false): List<String> {
        return execute(context, listOf("shell", command), root)
    }

    /**
     * ADB shell ile cihaza APK yükler (yükleme ilerlemesini gösterir).
     */
    suspend fun installWithProgress(context: Context, apkPath: String, root: Boolean = false): List<String> {
        return execute(context, listOf("install", "-r", "-d", apkPath), root)
    }

    /**
     * ADB shell ile cihazda paket listesini alır.
     */
    suspend fun listPackages(context: Context, filter: String = "", root: Boolean = false): List<String> {
        val args = mutableListOf("shell", "pm", "list", "packages")
        if (filter.isNotEmpty()) args.add(filter)
        return execute(context, args, root)
    }

    /**
     * ADB shell ile cihazda aktivite başlatır.
     */
    suspend fun startActivity(context: Context, packageName: String, activity: String, root: Boolean = false): List<String> {
        return execute(context, listOf("shell", "am", "start", "-n", "$packageName/$activity"), root)
    }

    /**
     * ADB shell ile cihazda bir uygulamayı durdurur.
     */
    suspend fun forceStop(context: Context, packageName: String, root: Boolean = false): List<String> {
        return execute(context, listOf("shell", "am", "force-stop", packageName), root)
    }

    /**
     * ADB shell ile cihazda dosya izinlerini değiştirir (chmod).
     */
    suspend fun chmod(context: Context, mode: String, file: String, root: Boolean = true): List<String> {
        return execute(context, listOf("shell", "su", "-c", "chmod", mode, file), root)
    }

    /**
     * ADB shell ile cihazda dosya sahibini değiştirir (chown).
     */
    suspend fun chown(context: Context, owner: String, file: String, root: Boolean = true): List<String> {
        return execute(context, listOf("shell", "su", "-c", "chown", owner, file), root)
    }

    /**
     * ADB shell ile cihazda mount işlemi yapar.
     */
    suspend fun mount(context: Context, device: String, mountPoint: String, options: String = "", root: Boolean = true): List<String> {
        val args = mutableListOf("shell", "su", "-c", "mount")
        if (options.isNotEmpty()) args.add("-o")
        if (options.isNotEmpty()) args.add(options)
        args.add(device)
        args.add(mountPoint)
        return execute(context, args, root)
    }

    /**
     * ADB shell ile cihazda umount işlemi yapar.
     */
    suspend fun umount(context: Context, mountPoint: String, root: Boolean = true): List<String> {
        return execute(context, listOf("shell", "su", "-c", "umount", mountPoint), root)
    }
}