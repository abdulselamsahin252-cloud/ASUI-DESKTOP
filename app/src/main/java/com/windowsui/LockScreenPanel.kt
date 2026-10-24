package com.windowsui

import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

/**
 * LockScreenPanel – Windows 11 tarzı kilit ekranı.
 *
 * Özellikler:
 * - Duvar kağıdı arka plan (mevcut duvar kağıdını bulanıklaştırarak)
 * - Büyük saat ve tarih (Windows 11 stili)
 * - Kullanıcı avatarı ve adı (SystemState'dan)
 * - Bildirim sayacı (NotificationState ile senkronize)
 * - Şifre giriş alanı (Enter ile gönderme)
 * - Biyometrik (parmak izi / yüz) desteği (Android 6.0+)
 * - Yanlış şifre animasyonu (sallama ve titreşim) – tek seferlik
 * - Başarısız deneme sayısı (5 deneme sonra 30 saniye kilit)
 * - Acil durum araması (düğme)
 * - Güç seçenekleri (Kapat, Yeniden Başlat, Uyku) – root ile
 * - Ağ, pil, ses durum göstergeleri (TaskbarState ile senkronize)
 * - Animasyonlu geçişler (açılırken fade, kayma)
 * - Koyu/açık tema uyumu (arka plan karartma)
 * - Klavye kısayolu (Enter ile şifre gönderme)
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LockScreenPanel(
    onUnlock: (String) -> Unit,
    onEmergencyCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // ============ STATE ============
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLockedOut by remember { mutableStateOf(false) }
    var lockoutTimer by remember { mutableStateOf(0) }
    var failedAttempts by remember { mutableStateOf(0) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // 🟢 FIX: shakeAnim için Animatable kullan
    val shakeAnim = remember { Animatable(0f) }

    // Biyometrik destek
    var isBiometricAvailable by remember { mutableStateOf(false) }
    var biometricCancellationSignal by remember { mutableStateOf<CancellationSignal?>(null) }

    // Zaman ve tarih
    val currentTime = Utils.rememberCurrentTime()
    val currentDate = Utils.rememberCurrentDate()

    // Bildirim sayısı
    val notificationCount = NotificationState.notifications.size

    // Duvar kağıdı (mevcut duvar kağıdını al)
    val wallpaperUri = SystemState.wallpaperUri
    val wallpaperBitmap = rememberBitmapFromUri(wallpaperUri)

    // ============ KİLİT SÜRESİ SAYACI ============
    LaunchedEffect(isLockedOut, lockoutTimer) {
        if (isLockedOut && lockoutTimer > 0) {
            delay(1000)
            lockoutTimer -= 1
            if (lockoutTimer == 0) {
                isLockedOut = false
                failedAttempts = 0
            }
        }
    }

    // ============ BİYOMETRİK KONTROL ============
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isBiometricAvailable = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FINGERPRINT)
        }
    }

    // ============ ŞİFRE DOĞRULAMA ============
    fun verifyPassword(input: String) {
        if (isLockedOut) {
            errorMessage = "Çok fazla başarısız deneme. ${lockoutTimer}s bekleyin."
            return
        }

        if (SystemState.checkLockScreenPassword(input)) {
            // Başarılı
            onUnlock(input)
            password = ""
            errorMessage = null
            failedAttempts = 0
        } else {
            // Başarısız
            failedAttempts++
            errorMessage = if (failedAttempts >= 5) {
                "Çok fazla başarısız deneme. 30 saniye bekleyin."
            } else {
                "Yanlış şifre. Kalan deneme: ${5 - failedAttempts}"
            }

            // 🟢 FIX: Animatable ile tek seferlik sallama
            scope.launch {
                shakeAnim.snapTo(0f)
                shakeAnim.animateTo(
                    targetValue = 30f,
                    animationSpec = keyframes {
                        durationMillis = 300
                        0f at 0 with EaseIn
                        30f at 50
                        -30f at 100
                        15f at 150
                        -15f at 200
                        0f at 300
                    }
                )
                shakeAnim.snapTo(0f)
            }

            // Titreşim
            try {
                val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(300)
                }
            } catch (e: Exception) {}

            // 5 başarısız deneme sonrası kilitle
            if (failedAttempts >= 5) {
                isLockedOut = true
                lockoutTimer = 30
            }

            // Şifreyi temizle
            password = ""
        }
    }

    // ============ BİYOMETRİK BAŞLAT ============
    fun startBiometric() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (!isBiometricAvailable) return

        // 🟢 FIX: AuthenticationCallback eklendi
        val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                // Biyometrik başarılı, kilidi aç
                onUnlock("")
                biometricCancellationSignal = null
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                errorMessage = "Parmak izi veya yüz tanınamadı. Tekrar deneyin."
                // Tekrar sallama
                scope.launch {
                    shakeAnim.snapTo(0f)
                    shakeAnim.animateTo(
                        targetValue = 20f,
                        animationSpec = keyframes {
                            durationMillis = 250
                            0f at 0
                            20f at 50
                            -20f at 100
                            10f at 150
                            -10f at 200
                            0f at 250
                        }
                    )
                    shakeAnim.snapTo(0f)
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                errorMessage = errString.toString()
                biometricCancellationSignal = null
            }
        }

        val biometricPrompt = BiometricPrompt.Builder(context)
            .setTitle("ASUI Desktop")
            .setSubtitle("Parmak izi veya yüz ile oturum açın")
            .setDescription("Cihazınızın biyometrik özelliğini kullanarak kilidi açın")
            .setNegativeButton("İptal", ContextCompat.getMainExecutor(context)) { _, _ ->
                biometricCancellationSignal = null
            }
            .build()

        val cancelSignal = CancellationSignal()
        biometricCancellationSignal = cancelSignal
        biometricPrompt.authenticate(cancelSignal, ContextCompat.getMainExecutor(context), authenticationCallback)
    }

    // ============ ANA KANVAS ============
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(500, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xE6000000)) // Yarı saydam karartma
        ) {
            // ============ ARKA PLAN: DUVAR KAĞIDI (BULANIK) ============
            if (wallpaperBitmap != null) {
                Image(
                    bitmap = wallpaperBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(
                            radiusX = 24.dp,
                            radiusY = 24.dp,
                            edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded
                        ),
                    contentScale = ContentScale.Crop,
                    alpha = 0.6f
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.wallpaper),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(
                            radiusX = 24.dp,
                            radiusY = 24.dp
                        ),
                    contentScale = ContentScale.Crop,
                    alpha = 0.6f
                )
            }

            // ============ İÇERİK ============
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ============ ÜST BÖLÜM: SAAT / TARİH / DURUM ============
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 40.dp)
                ) {
                    Text(
                        text = currentTime,
                        color = Color.White,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentDate,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val batteryLevel = TaskbarState.batteryLevel
                        val isCharging = TaskbarState.isCharging
                        Icon(
                            if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            contentDescription = "Pil",
                            tint = when {
                                batteryLevel > 50 -> Color(0xFF4CAF50)
                                batteryLevel > 20 -> Color(0xFFFFA000)
                                else -> Color(0xFFE53935)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "$batteryLevel%",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        if (TaskbarState.wifiEnabled) {
                            Icon(
                                Icons.Default.Wifi,
                                contentDescription = "Wi-Fi",
                                tint = Color(0xFF4A9EFF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (TaskbarState.volumeLevel > 0) {
                            Icon(
                                if (TaskbarState.volumeLevel > 50) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                contentDescription = "Ses",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (notificationCount > 0) {
                            Badge(
                                modifier = Modifier.padding(start = 4.dp),
                                containerColor = Color(0xFF0078D7)
                            ) {
                                Text(
                                    text = if (notificationCount > 99) "99+" else notificationCount.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // ============ ORTA BÖLÜM: KULLANICI PROFİLİ ============
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    val profilePic = rememberBitmapFromUri(SystemState.profilePicUri)
                    if (profilePic != null) {
                        Image(
                            bitmap = profilePic,
                            contentDescription = "Profil Resmi",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(40.dp))
                                .shadow(8.dp, RoundedCornerShape(40.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(40.dp))
                                .background(Color(0xFF0078D7))
                                .shadow(8.dp, RoundedCornerShape(40.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = SystemState.username.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = SystemState.username,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Oturum açmak için şifrenizi girin",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }

                // ============ ALT BÖLÜM: ŞİFRE GİRİŞİ ============
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    // 🟢 FIX: shake animasyonu offset ile uygulandı
                    val shakeOffsetX = shakeAnim.value

                    OutlinedTextField(
                        value = password,
                        onValueChange = { if (!isLockedOut) password = it },
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .offset { IntOffset(shakeOffsetX.toInt(), 0) }
                            .animateContentSize(),
                        label = {
                            Text(
                                if (isLockedOut) "Kilitli" else "Şifre",
                                color = if (isLockedOut) Color.Red else Color.White.copy(alpha = 0.7f)
                            )
                        },
                        visualTransformation = if (isPasswordVisible) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { verifyPassword(password) }
                        ),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 18.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (errorMessage != null) Color.Red else Color(0xFF0078D7),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                            focusedLabelColor = if (errorMessage != null) Color.Red else Color(0xFF0078D7),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                            cursorColor = Color.White
                        ),
                        trailingIcon = {
                            Row {
                                IconButton(
                                    onClick = { isPasswordVisible = !isPasswordVisible },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) "Şifreyi gizle" else "Şifreyi göster",
                                        tint = Color.White
                                    )
                                }
                                if (isBiometricAvailable) {
                                    IconButton(
                                        onClick = { startBiometric() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Fingerprint,
                                            contentDescription = "Parmak izi",
                                            tint = Color(0xFF4A9EFF)
                                        )
                                    }
                                }
                            }
                        },
                        enabled = !isLockedOut,
                        isError = errorMessage != null
                    )

                    if (errorMessage != null) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { -it },
                            exit = fadeOut()
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = Color.Red,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(0.6f),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = onEmergencyCall,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Acil Durum")
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot -p"))
                                        } catch (e: Exception) {}
                                    }
                                }
                            ) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = "Kapat", tint = Color(0xFFFF5555))
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
                                        } catch (e: Exception) {}
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Yeniden Başlat", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}