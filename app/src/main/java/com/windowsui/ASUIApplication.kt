package com.windowsui

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.os.Environment
import java.io.File
import java.util.Date

class ASUIApplication : Application() {

    companion object {
        private lateinit var instance: ASUIApplication
        fun getInstance(): ASUIApplication = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("ASUIApplication", "Uygulama baslatildi")

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
    try {
        val dir = File(applicationContext.getExternalFilesDir(null), "ASUI")
        dir.mkdirs()
        val logFile = File(dir, "crash_${Date().time}.log")
        logFile.writeText(
            "Zaman: ${Date()}\n" +
            "Mesaj: ${throwable.message}\n\n" +
            Log.getStackTraceString(throwable) + "\n\n" +
            "--- SEBEP (cause) ---\n" +
            Log.getStackTraceString(throwable.cause ?: throwable)
        )
    } catch (_: Throwable) {}
    
    // android.os.Process.killProcess(android.os.Process.myPid())
}
        instance = this
        Log.d("ASUIApplication", "Uygulama başlatıldı")
        
        // Tüm yöneticileri başlat
        SystemState.loadState(this)
        TaskbarState.loadState(this)
        WindowState.loadState(this)
        NotificationState.init(this)
        PermissionManager.init(this)
        TaskbarPosition.init(this)
        WallpaperManager.init(this)
        WifiController.init(this)
        UsbOtgManager.initialize(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
    BinaryManager.initialize(this@ASUIApplication)
}
    }

    fun getAppContext(): Context = applicationContext
}