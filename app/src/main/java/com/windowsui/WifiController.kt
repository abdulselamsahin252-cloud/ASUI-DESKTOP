// ====================================================================
// 3. WIFICONTROLLER.KT – TAM VE HATASIZ (Root ile Çalışır)
// ====================================================================
package com.windowsui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.*

/**
 * WifiController – Wi-Fi Kontrolcüsü
 * NOT: Android 10+ için bağlanma işlemleri root gerektirir.
 */
object WifiController {

    private const val TAG = "WifiController"
    private const val PREFS_NAME = "ASUI_WifiController"

    data class WifiNetwork(
        val ssid: String,
        val bssid: String,
        val signalStrength: Int,
        val frequency: Int,
        val capabilities: String,
        val timestamp: Long
    ) {
        val signalLevel: String get() = when {
            signalStrength > -50 -> "📶 Mükemmel"
            signalStrength > -60 -> "📶 İyi"
            signalStrength > -70 -> "📶 Orta"
            else -> "📶 Zayıf"
        }
        
        val isOpen: Boolean get() = capabilities.contains("ESS") && 
                                     !capabilities.contains("WPA") && 
                                     !capabilities.contains("WEP")
        
        val isSecure: Boolean get() = !isOpen
        
        val securityType: String get() = when {
            capabilities.contains("WPA3") -> "🔐 WPA3"
            capabilities.contains("WPA2") -> "🔐 WPA2"
            capabilities.contains("WPA") -> "🔐 WPA"
            capabilities.contains("WEP") -> "🔐 WEP"
            else -> "🌐 Açık"
        }
    }

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _ssid = MutableStateFlow("")
    val ssid: StateFlow<String> = _ssid.asStateFlow()

    private val _signalStrength = MutableStateFlow(0)
    val signalStrength: StateFlow<Int> = _signalStrength.asStateFlow()

    private val _ipAddress = MutableStateFlow("")
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _networkHistory = MutableStateFlow<List<String>>(emptyList())
    val networkHistory: StateFlow<List<String>> = _networkHistory.asStateFlow()

    private var appContext: Context? = null
    private var wifiManager: WifiManager? = null

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        loadState()
        updateStatus()
        Log.d(TAG, "WifiController initialized")
    }

    private fun hasLocationPermission(): Boolean {
        val ctx = appContext ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    suspend fun enableWifi(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val wm = wifiManager ?: return@withContext false
        try {
            wm.isWifiEnabled = enabled
            _isEnabled.value = enabled
            saveState()
            updateStatus()
            Log.d(TAG, "Wi-Fi ${if (enabled) "enabled" else "disabled"}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Wi-Fi enable error: ${e.message}")
            false
        }
    }

    suspend fun scanNetworks(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        val wm = wifiManager ?: return@withContext emptyList()
        try {
            if (!hasLocationPermission()) {
                Log.w(TAG, "Location permission required for Wi-Fi scan")
                return@withContext emptyList()
            }
            
            wm.startScan()
            val results = wm.scanResults ?: return@withContext emptyList()
            
            results.map { result ->
                WifiNetwork(
                    ssid = result.SSID ?: "Bilinmiyor",
                    bssid = result.BSSID ?: "",
                    signalStrength = result.level,
                    frequency = result.frequency,
                    capabilities = result.capabilities ?: "",
                    timestamp = result.timestamp
                )
            }.distinctBy { it.ssid }.sortedByDescending { it.signalStrength }
        } catch (e: Exception) {
            Log.e(TAG, "Scan error: ${e.message}")
            emptyList()
        }
    }

    suspend fun connectToNetwork(ssid: String, password: String? = null): Boolean = withContext(Dispatchers.IO) {
        val wm = wifiManager ?: return@withContext false
        try {
            // Root ile bağlanmayı dene
            val result = SystemUtils.runRootCommand("svc wifi enable && cmd wifi connect-network \"$ssid\" wpa2 \"$password\"")
            if (result.contains("Connected")) {
                _ssid.value = ssid
                saveState()
                updateStatus()
                Log.d(TAG, "Connected to: $ssid (via root)")
                return@withContext true
            }
            
            // Root yoksa normal yöntem (Android 10- çalışır)
            val scanResults = wm.scanResults
            val network = scanResults.find { it.SSID == ssid }
            if (network == null) {
                Log.e(TAG, "Network not found: $ssid")
                return@withContext false
            }
            
            val isOpen = network.capabilities.contains("ESS") && 
                        !network.capabilities.contains("WPA") &&
                        !network.capabilities.contains("WEP")
            
            val config = WifiConfiguration().apply {
                this.SSID = "\"$ssid\""
                if (isOpen) {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                } else if (password != null) {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    this.preSharedKey = "\"$password\""
                } else {
                    return@withContext false
                }
            }
            
            val networkId = wm.addNetwork(config)
            if (networkId == -1) {
                Log.e(TAG, "Failed to add network")
                return@withContext false
            }
            
            wm.enableNetwork(networkId, true)
            wm.reconnect()
            
            _ssid.value = ssid
            saveState()
            updateStatus()
            
            Log.d(TAG, "Connected to: $ssid")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connect error: ${e.message}")
            false
        }
    }

    suspend fun disconnect(): Boolean = withContext(Dispatchers.IO) {
        val wm = wifiManager ?: return@withContext false
        try {
            wm.disconnect()
            _ssid.value = ""
            saveState()
            updateStatus()
            Log.d(TAG, "Disconnected")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error: ${e.message}")
            false
        }
    }

    suspend fun forgetNetwork(ssid: String): Boolean = withContext(Dispatchers.IO) {
        val wm = wifiManager ?: return@withContext false
        try {
            val configs = wm.configuredNetworks
            configs?.find { it.SSID == "\"$ssid\"" }?.let { config ->
                wm.removeNetwork(config.networkId)
                wm.saveConfiguration()
                Log.d(TAG, "Network forgotten: $ssid")
                return@withContext true
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Forget error: ${e.message}")
            false
        }
    }

    fun updateStatus() {
        val wm = wifiManager ?: return
        _isEnabled.value = wm.isWifiEnabled
        
        if (wm.isWifiEnabled) {
            val info = wm.connectionInfo
            info?.let {
                _ssid.value = it.ssid?.replace("\"", "") ?: ""
                _signalStrength.value = WifiManager.calculateSignalLevel(it.rssi, 5)
                val ip = it.ipAddress
                if (ip != 0) {
                    _ipAddress.value = "${(ip and 0xFF)}.${(ip shr 8 and 0xFF)}.${(ip shr 16 and 0xFF)}.${(ip shr 24 and 0xFF)}"
                }
            }
        } else {
            _ssid.value = ""
            _signalStrength.value = 0
            _ipAddress.value = ""
        }
    }

    fun getSignalBars(): String {
        val level = _signalStrength.value
        return when (level) {
            0 -> "📶"
            1 -> "📶▁"
            2 -> "📶▁▂"
            3 -> "📶▁▂▃"
            4 -> "📶▁▂▃▄"
            5 -> "📶▁▂▃▄▅"
            else -> "📶"
        }
    }

    fun getSpiritNetwork(context: Context): String {
        val personality = runCatching { DeviceInfo.analyzeDevicePersonality(context) }.getOrNull() ?: "Bilinmiyor"
        return when {
            personality.contains("süper kahraman") -> "✨ ASUI_SUPER"
            personality.contains("denge ustası") -> "🧘 ASUI_BALANCE"
            personality.contains("minimalist") -> "📿 ASUI_MINIMAL"
            else -> "💎 ASUI_DESKTOP"
        }
    }

    suspend fun getNetworkReport(): String = withContext(Dispatchers.IO) {
        val networks = scanNetworks()
        val total = networks.size
        val secured = networks.count { it.isSecure }
        val open = networks.count { it.isOpen }
        val best = networks.maxByOrNull { it.signalStrength }

        buildString {
            appendLine("📊 AĞ ANALİZ RAPORU")
            appendLine("─────────────────────────")
            appendLine("Toplam Ağ: $total")
            appendLine("Güvenli: $secured")
            appendLine("Açık: $open")
            appendLine("Bağlı: ${_ssid.value}")
            appendLine("Sinyal: ${_signalStrength.value}/5")
            best?.let {
                appendLine("En İyi Sinyal: ${it.ssid} (${it.signalStrength} dBm)")
                appendLine("Güvenlik: ${it.securityType}")
            }
            appendLine("─────────────────────────")
            appendLine("📡 AĞ LİSTESİ")
            networks.take(10).forEach { network ->
                appendLine("${network.securityType} ${network.ssid} (${network.signalLevel})")
            }
            if (networks.size > 10) {
                appendLine("... ve ${networks.size - 10} ağ daha")
            }
        }
    }

    private fun saveState() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("lastSsid", _ssid.value)
            putString("history", _networkHistory.value.joinToString("|"))
        }.apply()
    }

    private fun loadState() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        _ssid.value = prefs.getString("lastSsid", "") ?: ""
        
        val historyStr = prefs.getString("history", "") ?: ""
        if (historyStr.isNotEmpty()) {
            _networkHistory.value = historyStr.split("|").filter { it.isNotEmpty() }
        }
    }

    fun resetToDefaults() {
        _networkHistory.value = emptyList()
        saveState()
        Log.d(TAG, "Reset to defaults")
    }
}