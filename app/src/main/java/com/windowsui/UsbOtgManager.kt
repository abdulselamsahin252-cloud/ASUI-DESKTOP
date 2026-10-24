package com.windowsui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * USB OTG (On-The-Go) Manager for ASUI Desktop.
 *
 * Provides:
 * - Device enumeration with full descriptors (VID, PID, manufacturer, product, serial)
 * - Automatic detection of ADB, MTP, mass storage, and other interface types
 * - USB permission management (Android user consent)
 * - Mount/unmount of USB mass storage devices (requires root)
 * - Power and speed control (root/sysfs)
 * - USB device reset (root)
 * - Live device attach/detach broadcast receiver
 * - USB host mode enable/disable (root)
 * - Advanced USB descriptor parsing (interface, endpoint, class info)
 */
object UsbOtgManager {

    private const val TAG = "UsbOtgManager"
    const val ACTION_USB_PERMISSION = "com.windowsui.action.USB_PERMISSION"
    private var receiverRegistered = false

    // BroadcastReceiver to handle attach/detach and permission results
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
                    if (device != null) {
                        Log.d(TAG, "USB cihazı takıldı: ${device.deviceName}")
                        // Otomatik mount denenebilir (root ile)
                        // veya bildirim gönderilebilir
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
                    if (device != null) {
                        Log.d(TAG, "USB cihazı çıkarıldı: ${device.deviceName}")
                        // Otomatik unmount (root ile)
                    }
                }
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.d(TAG, "USB izni sonucu: $granted")
                }
            }
        }
    }

    /**
     * Başlangıçta broadcast receiver'ı kaydeder.
     */
    fun initialize(context: Context) {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    /**
     * Uygulama kapanırken receiver'ı temizler.
     */
    fun release(context: Context) {
        if (!receiverRegistered) return
        try { context.unregisterReceiver(receiver) } catch (_: Throwable) {}
        receiverRegistered = false
    }

    // ==================== CİHAZ BİLGİLERİ ====================

    /**
     * Bağlı tüm USB cihazlarını listeler.
     */
    fun getConnectedDevices(context: Context): List<UsbDevice> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.deviceList.values.sortedWith(
            compareBy({ it.vendorId }, { it.productId }, { it.deviceName })
        )
    }

    /**
     * Cihazın detaylı bilgilerini döndürür (insan okunabilir formatta).
     */
    suspend fun getDeviceInfo(context: Context, device: UsbDevice): Map<String, String> = withContext(Dispatchers.IO) {
        val info = mutableMapOf<String, String>()
        try {
            info["deviceName"] = device.deviceName ?: "Bilinmiyor"
            info["vendorId"] = "0x${device.vendorId.toString(16).uppercase(Locale.US)}"
            info["productId"] = "0x${device.productId.toString(16).uppercase(Locale.US)}"
            info["deviceClass"] = device.deviceClass.toString()
            info["deviceSubclass"] = device.deviceSubclass.toString()
            info["deviceProtocol"] = device.deviceProtocol.toString()
            info["interfaceCount"] = device.interfaceCount.toString()
            info["hasPermission"] = hasPermission(context, device).toString()
            info["isAdb"] = isAdbDevice(device).toString()

            // Ek bilgiler (sysfs'ten)
            try {
                val sysPath = "/sys/bus/usb/devices/${device.deviceName}"
                val manufacturer = readSysfs("$sysPath/manufacturer")
                val product = readSysfs("$sysPath/product")
                val serial = readSysfs("$sysPath/serial")
                val speed = readSysfs("$sysPath/speed")
                val maxPower = readSysfs("$sysPath/bMaxPower")
                if (manufacturer.isNotBlank()) info["manufacturer"] = manufacturer
                if (product.isNotBlank()) info["product"] = product
                if (serial.isNotBlank()) info["serial"] = serial
                if (speed.isNotBlank()) info["speed"] = speed
                if (maxPower.isNotBlank()) info["maxPower"] = "$maxPower mA"
            } catch (_: Throwable) { }

            // Arayüz detayları
            val interfaces = mutableListOf<String>()
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                val ifaceInfo = "iface[$i] class=${iface.interfaceClass} sub=${iface.interfaceSubclass} proto=${iface.interfaceProtocol} endpoints=${iface.endpointCount}"
                interfaces.add(ifaceInfo)
            }
            info["interfaces"] = interfaces.joinToString("\n")
        } catch (e: Exception) {
            info["error"] = e.message ?: "Bilinmeyen hata"
        }
        info
    }

    // ==================== İZİN YÖNETİMİ ====================

    fun hasPermission(context: Context, device: UsbDevice): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.hasPermission(device)
    }

    fun requestUsbPermission(context: Context, device: UsbDevice) {
        initialize(context)
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (usbManager.hasPermission(device)) return

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    // ==================== ADB / MTP / DEPOLAMA TESPİTİ ====================

    fun isAdbDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            // Vendor specific with ADB protocol
            if (iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                iface.interfaceSubclass == 66 &&
                iface.interfaceProtocol == 1) return true
            if (iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                iface.interfaceSubclass == 42 &&
                iface.interfaceProtocol == 1) return true
        }
        return false
    }

    fun isMtpDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == 0x06 && // MTP
                iface.interfaceSubclass == 0x01 &&
                iface.interfaceProtocol == 0x01) return true
        }
        return false
    }

    fun isMassStorageDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) return true
        }
        return false
    }

    // ==================== ROOT İLE GELİŞMİŞ İŞLEMLER ====================

    /**
     * USB cihazını sysfs üzerinden resetler (root gerektirir).
     */
    suspend fun resetDevice(context: Context, device: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            val path = "/sys/bus/usb/devices/${device.deviceName}/authorized"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo 0 > $path && echo 1 > $path"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * USB host modunu açar/kapatır (root gerektirir).
     */
    suspend fun setUsbHostMode(context: Context, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = if (enable) "echo host > /sys/class/usb_role/ff610000.usb/role" else "echo peripheral > /sys/class/usb_role/ff610000.usb/role"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * USB cihazının güç tüketimini okur (mA cinsinden, root gerektirir).
     */
    suspend fun getPowerConsumption(device: UsbDevice): Int? = withContext(Dispatchers.IO) {
        try {
            val path = "/sys/bus/usb/devices/${device.deviceName}/bMaxPower"
            val content = readSysfs(path)
            content.toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * USB hızını okur (örnek: "480", "12", "1.5" Mbps, root gerektirir).
     */
    suspend fun getSpeed(device: UsbDevice): String? = withContext(Dispatchers.IO) {
        try {
            val path = "/sys/bus/usb/devices/${device.deviceName}/speed"
            readSysfs(path)
        } catch (e: Exception) {
            null
        }
    }

    // ==================== MASS STORAGE MOUNT / UNMOUNT (ROOT) ====================

    /**
     * Bağlı USB depolama aygıtlarının blok cihazlarını listeler (root ile).
     * Örnek: /dev/block/sda1, /dev/block/sdb1 vb.
     */
    suspend fun getUsbBlockDevices(): List<String> = withContext(Dispatchers.IO) {
        val list = mutableListOf<String>()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls /dev/block/sd* 2>/dev/null"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line != null) list.add(line)
            }
            process.waitFor()
        } catch (e: Exception) {}
        list
    }

    /**
     * USB depolama aygıtını mount eder (root gerektirir).
     * Örnek: mountUsb("/dev/block/sda1", "/mnt/usb")
     */
    suspend fun mountUsb(blockDevice: String, mountPoint: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(mountPoint)
            if (!dir.exists()) dir.mkdirs()
            val cmd = "mount -t vfat -o rw,utf8 $blockDevice $mountPoint"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * USB depolama aygıtını unmount eder (root gerektirir).
     */
    suspend fun unmountUsb(mountPoint: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "umount $mountPoint"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Mount edilmiş USB'lerin listesini döndürür (root gerektirir).
     */
    suspend fun getMountedUsb(): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "mount | grep /dev/block/sd"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line != null) {
                    val parts = line.split(" ")
                    if (parts.size >= 3) {
                        val dev = parts[0]
                        val mp = parts[2]
                        result[dev] = mp
                    }
                }
            }
            process.waitFor()
        } catch (e: Exception) {}
        result
    }

    // ==================== YARDIMCI FONKSİYONLAR ====================

    private fun readSysfs(path: String): String {
        return try {
            File(path).readText().trim()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * USB cihazının seri numarasını döndürür (root ile sysfs'ten).
     */
    suspend fun getSerialNumber(device: UsbDevice): String? = withContext(Dispatchers.IO) {
        try {
            val path = "/sys/bus/usb/devices/${device.deviceName}/serial"
            readSysfs(path)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * USB cihazının üretici adını döndürür (root ile sysfs'ten).
     */
    suspend fun getManufacturer(device: UsbDevice): String? = withContext(Dispatchers.IO) {
        try {
            val path = "/sys/bus/usb/devices/${device.deviceName}/manufacturer"
            readSysfs(path)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * USB cihazının ürün adını döndürür (root ile sysfs'ten).
     */
    suspend fun getProduct(device: UsbDevice): String? = withContext(Dispatchers.IO) {
        try {
            val path = "/sys/bus/usb/devices/${device.deviceName}/product"
            readSysfs(path)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Tüm USB cihazlarının özet bilgilerini döndürür (terminal çıktısı için).
     */
    suspend fun getAllDevicesSummary(context: Context): List<String> = withContext(Dispatchers.IO) {
        val devices = getConnectedDevices(context)
        if (devices.isEmpty()) return@withContext listOf("USB cihazı bulunamadı.")
        val summary = mutableListOf<String>()
        devices.forEachIndexed { index, device ->
            val info = getDeviceInfo(context, device)
            val name = info["product"] ?: info["deviceName"] ?: "Bilinmeyen"
            val vid = info["vendorId"] ?: "???"
            val pid = info["productId"] ?: "???"
            val adb = if (isAdbDevice(device)) " [ADB]" else ""
            val perm = if (hasPermission(context, device)) " [İzinli]" else " [İzinsiz]"
            summary.add("[$index] $name  VID=$vid PID=$pid$adb$perm")
        }
        summary
    }
}