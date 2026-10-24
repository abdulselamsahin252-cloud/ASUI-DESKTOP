package com.windowsui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.absoluteValue
import kotlin.math.sin
import kotlin.random.Random

/**
 * IconManager – Merkezi İkon Yönetimi ve Sihirli İkon Fabrikası
 *
 * Tüm uygulama, dosya, sistem ve özel ikonları yönetir.
 * Önbellekleme, dinamik oluşturma, temalama ve "akla hayale gelmeyecek"
 * özellikler ile donatılmıştır.
 *
 * 🚀 "Akla Hayale Gelmez" Özellikler:
 * - 🧘 Ruh İkonu: Cihaz kişiliğine göre özel emoji/ikon (DeviceInfo ile entegre).
 * - ✨ Sihirli İkon: Her çağrıda rastgele renk ve şekil (canlı, önbelleklenmez).
 * - 🎨 Dinamik Renk: İkonu cihaz ruhu rengine veya temaya göre boyar.
 * - 📁 Dosya Türü İkonları: 50+ dosya uzantısı için varsayılan ikonlar.
 * - 🔤 Metin İkonları: Uygulama adının ilk harfi ile renkli arka plan.
 * - 🫥 Gizli İkon: Tamamen şeffaf (eğlence amaçlı).
 * - 🌈 Gökkuşağı İkonu: Renk geçişli ikon (gradient).
 * - ⏳ Canlı İkon: Belirli aralıkta renk değiştiren (UI tarafında kullanılır).
 * - 🔮 Kristal İkon: Şeffaf ve parlama efekti (alpha gradient).
 * - 🌊 Dalga İkonu: Hareketli dalga efekti (statik simülasyon).
 * - 🎭 Maske İkonu: Rastgele iki emoji üst üste bindirilir.
 * - 🔥 Ateş İkonu: Alev efekti (kırmızı-turuncu gradient).
 * - ❄️ Buz İkonu: Buz kristali efekti (mavi-beyaz gradient).
 * - 🧊 Neon İkonu: Neon parlaklığında renkli ikon.
 * - 🕹️ Retro İkonu: Piksel tarzı (kare arka plan).
 * - 🌌 Uzay İkonu: Yıldızlı arka plan.
 * - 💀 Karanlık İkonu: Siyah arka plan, kırmızı göz efekti.
 *
 * Tüm fonksiyonlar ImageBitmap döndürür ve mevcut DesktopApp/DesktopGridItem ile uyumludur.
 */
object IconManager {

    private const val TAG = "IconManager"
    private const val CACHE_SIZE = 250

    // Önbellek: paket adı veya dosya uzantısı -> ImageBitmap
    private val cache = LruCache<String, ImageBitmap>(CACHE_SIZE)

    // Varsayılan ikon boyutları (piksel cinsinden)
    private const val DEFAULT_ICON_SIZE = 96
    private const val SMALL_ICON_SIZE = 48
    private const val LARGE_ICON_SIZE = 144

    // ==================== UYGULAMA İKONLARI ====================

    /**
     * Bir uygulamanın ikonunu döndürür.
     * Önce önbellekten bakar, yoksa PackageManager'dan alır.
     * @param packageName Uygulama paket adı
     * @param context Context
     * @param size İstenen boyut (piksel)
     * @return ImageBitmap veya null (ikon bulunamazsa)
     */
    fun getAppIcon(packageName: String, context: Context, size: Int = DEFAULT_ICON_SIZE): ImageBitmap? {
        val cacheKey = "app_$packageName:$size"
        cache.get(cacheKey)?.let { return it }

        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val iconDrawable = appInfo.loadIcon(pm)
            val bitmap = iconDrawable.toBitmap(size, size)
            val imageBitmap = bitmap.asImageBitmap()
            cache.put(cacheKey, imageBitmap)
            imageBitmap
        } catch (e: Exception) {
            // İkon yoksa metin ikonu oluştur
            val label = try {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (_: Exception) {
                packageName
            }
            getTextIcon(label, size)
        }
    }

    /**
     * DesktopApp'ten ikon alır veya yoksa yaratır.
     */
    fun getIconForDesktopApp(app: DesktopApp, size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        return app.iconBitmap ?: getTextIcon(app.label, size)
    }

    // ==================== DOSYA İKONLARI ====================

    /**
     * Dosya uzantısına göre uygun ikon döndürür.
     */
    fun getFileIcon(file: java.io.File, size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val extension = file.extension.lowercase()
        return getFileIconByExtension(extension, size)
    }

    /**
     * Dosya uzantısına göre ikon döndürür.
     */
    fun getFileIconByExtension(extension: String, size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val cacheKey = "file_$extension:$size"
        cache.get(cacheKey)?.let { return it }

        val icon = when (extension) {
            // Resimler
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico" ->
                getTextIcon("🖼️", size, Color(0xFFFF9800))
            // Videolar
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp" ->
                getTextIcon("🎬", size, Color(0xFFE53935))
            // Müzik
            "mp3", "wav", "flac", "aac", "ogg", "wma" ->
                getTextIcon("🎵", size, Color(0xFF4CAF50))
            // Dokümanlar
            "pdf" -> getTextIcon("📄", size, Color(0xFFE53935))
            "doc", "docx" -> getTextIcon("📝", size, Color(0xFF2196F3))
            "xls", "xlsx" -> getTextIcon("📊", size, Color(0xFF4CAF50))
            "ppt", "pptx" -> getTextIcon("📽️", size, Color(0xFFFF9800))
            "txt" -> getTextIcon("📃", size, Color(0xFF9E9E9E))
            "rtf" -> getTextIcon("📄", size, Color(0xFF795548))
            // Arşivler
            "zip", "rar", "7z", "tar", "gz" ->
                getTextIcon("📦", size, Color(0xFF607D8B))
            // Kod
            "kt", "java", "cpp", "c", "py", "js", "html", "css", "xml", "json" ->
                getTextIcon("💻", size, Color(0xFF3F51B5))
            // APK
            "apk" -> getTextIcon("📲", size, Color(0xFFFF6B6B))
            // Klasör
            else -> getTextIcon("📁", size, Color(0xFFFFB900))
        }

        cache.put(cacheKey, icon)
        return icon
    }

    /**
     * MIME türüne göre ikon döndürür.
     */
    fun getMimeTypeIcon(mimeType: String, size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val extension = when {
            mimeType.startsWith("image/") -> "jpg"
            mimeType.startsWith("video/") -> "mp4"
            mimeType.startsWith("audio/") -> "mp3"
            mimeType.startsWith("text/") -> "txt"
            mimeType == "application/pdf" -> "pdf"
            mimeType == "application/zip" -> "zip"
            mimeType == "application/vnd.android.package-archive" -> "apk"
            else -> "file"
        }
        return getFileIconByExtension(extension, size)
    }

    // ==================== METİN İKONLARI (İlk Harf / Emoji) ====================

    /**
     * Verilen metnin ilk harfini veya emoji'sini içeren bir ikon oluşturur.
     * Arka plan rengi belirtilmezse otomatik seçilir.
     */
    fun getTextIcon(text: String, size: Int = DEFAULT_ICON_SIZE, backgroundColor: Color? = null): ImageBitmap {
        val cacheKey = "text_${text}_${size}_${backgroundColor?.toArgb() ?: "default"}"
        cache.get(cacheKey)?.let { return it }

        val bgColor = backgroundColor ?: getColorForText(text)
        val bitmap = createTextBitmap(text, size, bgColor)
        val imageBitmap = bitmap.asImageBitmap()
        cache.put(cacheKey, imageBitmap)
        return imageBitmap
    }

    private fun createTextBitmap(text: String, size: Int, bgColor: Color): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Arka plan (yuvarlak dikdörtgen)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor.toArgb()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 16f, 16f, paint)

        // Metin / Emoji
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.toArgb()
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }

        // Eğer text emoji ise (ör. "📁"), doğrudan çiz
        val displayText = if (text.length <= 2 && text.codePointAt(0) > 0x1F000) text else text.take(1).uppercase()

        val x = size / 2f
        val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(displayText, x, y, textPaint)

        return bitmap
    }

    /**
     * Bir metne göre arka plan rengi üretir.
     */
    private fun getColorForText(text: String): Color {
        val colors = listOf(
            Color(0xFFE53935), Color(0xFF4CAF50), Color(0xFF2196F3),
            Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF00BCD4),
            Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFF45B7D1),
            Color(0xFFFFEAA7), Color(0xFFA8E6CF), Color(0xFFDDA0DD)
        )
        val index = text.hashCode().absoluteValue % colors.size
        return colors[index]
    }

    // ==================== EMOJİ İKONLARI ====================

    /**
     * Bir emoji karakterini ikon olarak döndürür.
     */
    fun getEmojiIcon(emoji: String, size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val cacheKey = "emoji_$emoji:$size"
        cache.get(cacheKey)?.let { return it }

        val bitmap = createTextBitmap(emoji, size, Color.Transparent)
        val imageBitmap = bitmap.asImageBitmap()
        cache.put(cacheKey, imageBitmap)
        return imageBitmap
    }

    // ==================== RUHU İKONU (DeviceInfo Entegrasyonu) ====================

    /**
     * Cihaz kişiliğine göre özel bir ikon döndürür.
     * DeviceInfo.analyzeDevicePersonality() sonucuna göre emoji belirlenir.
     */
    fun getSpiritIcon(context: Context, size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val personality = runCatching { DeviceInfo.analyzeDevicePersonality(context) }.getOrNull() ?: "Bilinmiyor"
        val emoji = when {
            personality.contains("süper kahraman") -> "🦸"
            personality.contains("denge ustası") -> "🧘"
            personality.contains("minimalist") -> "📿"
            else -> "💎"
        }
        // Ruh rengi ile arka plan
        val color = when {
            personality.contains("süper kahraman") -> Color(0xFFFF6B6B)
            personality.contains("denge ustası") -> Color(0xFF4ECDC4)
            personality.contains("minimalist") -> Color(0xFFA8E6CF)
            else -> Color(0xFF0078D7)
        }
        return getTextIcon(emoji, size, color)
    }

    // ==================== SİHİRLİ İKON (Rastgele) ====================

    /**
     * Her çağrıda rastgele renk ve şekil içeren "sihirli" ikon oluşturur.
     * Önbelleklenmez.
     */
    fun getMagicIcon(size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val colors = listOf(
            Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFF45B7D1),
            Color(0xFFFFEAA7), Color(0xFFA8E6CF), Color(0xFFFF8A5C)
        )
        val randomColor = colors.random()
        val emojis = listOf("✨", "🌟", "🎉", "🧙", "🪄", "🌈", "🔥", "💫")
        val randomEmoji = emojis.random()
        return getTextIcon(randomEmoji, size, randomColor)
    }

    // ==================== MASKE İKONU (İki Emoji Üst Üste) ====================

    /**
     * Rastgele iki emojiyi üst üste bindirerek maske efekti oluşturur.
     */
    fun getMaskedIcon(size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val emojis = listOf("😊", "😂", "🤣", "❤️", "🔥", "⭐", "🌟", "💎", "🎯", "🧠", "💪", "🦸", "🧘", "👾", "🤖", "👽")
        val mainEmoji = emojis.random()
        val maskEmoji = emojis.random()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Arka plan (koyu)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFF2D2D2D).toArgb()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 16f, 16f, bgPaint)

        // Ana emoji (büyük)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.toArgb()
            textSize = size * 0.7f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val x = size / 2f
        val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(mainEmoji, x, y, textPaint)

        // Maske emojisi (küçük, sağ alt köşede)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFFFD700).toArgb()
            textSize = size * 0.3f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(maskEmoji, size.toFloat() - 8f, size.toFloat() - 8f, maskPaint)

        return bitmap.asImageBitmap()
    }

    // ==================== GÖKKUŞAĞI İKONU (Gradient) ====================

    /**
     * Renk geçişli bir ikon oluşturur.
     */
    fun getRainbowIcon(text: String = "🌈", size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Gradient arka plan
        val colors = intArrayOf(
            0xFFFF0000.toInt(), 0xFFFF6B6B.toInt(),
            0xFFFF9800.toInt(), 0xFFFFEB3B.toInt(),
            0xFF4CAF50.toInt(), 0xFF2196F3.toInt(),
            0xFF9C27B0.toInt()
        )
        val shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), colors, null, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 16f, 16f, paint)

        // Metin (beyaz)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.toArgb()
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val x = size / 2f
        val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, x, y, textPaint)

        return bitmap.asImageBitmap()
    }

    // ==================== GİZLİ İKON (Şeffaf) ====================

    /**
     * Tamamen şeffaf bir ikon (eğlence amaçlı).
     */
    fun getHiddenIcon(size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.Transparent.toArgb())
        return bitmap.asImageBitmap()
    }

    // ==================== KRISTAL İKON (Şeffaf + Parlama) ====================

    /**
     * Şeffaf arka plan üzerinde parlama efekti olan kristal ikon.
     */
    fun getCrystalIcon(emoji: String = "💎", size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Parlama efekti (radial gradient)
        val shader = RadialGradient(
            size * 0.3f, size * 0.3f, size * 0.6f,
            intArrayOf(Color.White.toArgb(), Color.Transparent.toArgb()),
            null, Shader.TileMode.CLAMP
        )
        paint.shader = shader
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // Emoji (altın rengi)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFFFD700).toArgb()
            textSize = size * 0.6f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val x = size / 2f
        val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(emoji, x, y, textPaint)

        return bitmap.asImageBitmap()
    }

    // ==================== DALGA İKONU (Hareketli Dalga Efekti) ====================

    /**
     * Dalga efekti simülasyonu olan ikon.
     * @param waveHeight Dalga yüksekliği (0..1)
     */
    fun getWaveIcon(emoji: String = "🌊", size: Int = DEFAULT_ICON_SIZE, waveHeight: Float = 0.3f): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Mavi arka plan
        paint.color = Color(0xFF1A237E).toArgb()
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // Dalga (sinüs eğrisi) - Double ile işlem yap
        paint.color = Color(0xFF42A5F5).toArgb()
        paint.style = Paint.Style.FILL
        val path = Path().apply {
            moveTo(0f, size * (0.5f + waveHeight * 0.5f))
            for (x in 0..size) {
                val radians = x / (size / 4f) * Math.PI
                val y = size * (0.5f + waveHeight * 0.3f * sin(radians).toFloat())
                lineTo(x.toFloat(), y)
            }
            lineTo(size.toFloat(), size.toFloat())
            lineTo(0f, size.toFloat())
            close()
        }
        canvas.drawPath(path, paint)

        // Emoji (üstte)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.toArgb()
            textSize = size * 0.4f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val x = size / 2f
        val y = size * 0.3f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(emoji, x, y, textPaint)

        return bitmap.asImageBitmap()
    }

    // ==================== ATEŞ İKONU (Alev Efekti) ====================

    fun getFireIcon(emoji: String = "🔥", size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Kırmızı-turuncu gradient
        val colors = intArrayOf(
            0xFFFF6B00.toInt(), 0xFFFF4500.toInt(),
            0xFFFF0000.toInt(), 0xFFFF8C00.toInt()
        )
        val shader = RadialGradient(
            size * 0.5f, size * 0.5f, size * 0.7f,
            colors, null, Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 16f, 16f, paint)

        // Emoji (beyaz)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.toArgb()
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val x = size / 2f
        val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(emoji, x, y, textPaint)

        return bitmap.asImageBitmap()
    }

    // ==================== BUZ İKONU (Buz Kristali) ====================

    fun getIceIcon(emoji: String = "❄️", size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Mavi-beyaz gradient
        val colors = intArrayOf(
            0xFFE3F2FD.toInt(), 0xFF90CAF9.toInt(),
            0xFF42A5F5.toInt(), 0xFF0D47A1.toInt()
        )
        val shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), colors, null, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 16f, 16f, paint)

        // Buz kristali çizgileri (dekoratif)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.copy(alpha = 0.3f).toArgb()
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(size * 0.2f, size * 0.1f, size * 0.8f, size * 0.9f, linePaint)
        canvas.drawLine(size * 0.8f, size * 0.1f, size * 0.2f, size * 0.9f, linePaint)
        canvas.drawLine(size * 0.5f, 0f, size * 0.5f, size.toFloat(), linePaint)

        // Emoji
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.toArgb()
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val x = size / 2f
        val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(emoji, x, y, textPaint)

        return bitmap.asImageBitmap()
    }

    // ==================== NEON İKONU (Neon Parlaklığı) ====================

    fun getNeonIcon(emoji: String = "🧊", size: Int = DEFAULT_ICON_SIZE, color: Color = Color(0xFF00E5FF)): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Koyu arka plan
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color(0xFF0D0D0D).toArgb()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 16f, 16f, bgPaint)

        // Neon parlaması (dış glow)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textSize = size * 0.65f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(size * 0.2f, 0f, 0f, color.toArgb())
        }
        val x = size / 2f
        val y = size / 2f - (glowPaint.descent() + glowPaint.ascent()) / 2f
        canvas.drawText(emoji, x, y, glowPaint)

        // İç metin (beyaz)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.White.toArgb()
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(emoji, x, y, textPaint)

        return bitmap.asImageBitmap()
    }

    // ==================== RETRO İKONU (Piksel Tarzı) ====================

    fun getRetroIcon(emoji: String = "🕹️", size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Kareli arka plan (piksel efekti)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFF2D2D2D).toArgb()
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Kareler (piksel efekti)
        val pixelSize = size / 8f
        val pixelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        for (i in 0 until 8) {
            for (j in 0 until 8) {
                val x = i * pixelSize
                val y = j * pixelSize
                val isFilled = (i + j) % 2 == 0
                pixelPaint.color = if (isFilled) Color(0xFF4A4A4A).toArgb() else Color(0xFF1A1A1A).toArgb()
                canvas.drawRect(x, y, x + pixelSize, y + pixelSize, pixelPaint)
            }
        }

        // Emoji
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFFFD700).toArgb()
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val x = size / 2f
        val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(emoji, x, y, textPaint)

        return bitmap.asImageBitmap()
    }

    // ==================== UZAY İKONU (Yıldızlı Arka Plan) ====================

    fun getSpaceIcon(emoji: String = "🌌", size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Koyu mavi arka plan
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFF0D0D2B).toArgb()
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Yıldızlar (rastgele noktalar)
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.toArgb()
            style = Paint.Style.FILL
        }
        val random = Random(42)
        for (i in 0..30) {
            val x = random.nextFloat() * size
            val y = random.nextFloat() * size
            val radius = 1f + random.nextFloat() * 2f
            canvas.drawCircle(x, y, radius, starPaint)
        }

        // Emoji
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.toArgb()
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val x = size / 2f
        val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(emoji, x, y, textPaint)

        return bitmap.asImageBitmap()
    }

    // ==================== KARANLIK İKON (Kırmızı Göz Efekti) ====================

    fun getDarkIcon(emoji: String = "💀", size: Int = DEFAULT_ICON_SIZE): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Siyah arka plan
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.Black.toArgb()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 16f, 16f, bgPaint)

        // Kırmızı göz efekti (iki nokta)
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFFF0000).toArgb()
            style = Paint.Style.FILL
            setShadowLayer(10f, 0f, 0f, Color(0xFFFF0000).toArgb())
        }
        canvas.drawCircle(size * 0.3f, size * 0.4f, size * 0.1f, eyePaint)
        canvas.drawCircle(size * 0.7f, size * 0.4f, size * 0.1f, eyePaint)

        // Emoji (büyük, kırmızı)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFFF4444).toArgb()
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val x = size / 2f
        val y = size * 0.6f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(emoji, x, y, textPaint)

        return bitmap.asImageBitmap()
    }

    // ==================== RENKLENDİRME VE TEMA ====================

    /**
     * Bir ikonu belirtilen Compose rengine boyar (arka planı koruyarak).
     * Sadece opak olmayan pikseleri boyar.
     */
    fun colorizeIcon(icon: ImageBitmap, color: Color): ImageBitmap {
        val srcBitmap = icon.asAndroidBitmap()
        val dstBitmap = srcBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(dstBitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb() // this.color ile parametre çakışması giderildi
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        canvas.drawRect(0f, 0f, dstBitmap.width.toFloat(), dstBitmap.height.toFloat(), paint)
        return dstBitmap.asImageBitmap()
    }

    /**
     * İkonu koyu/açık temaya göre optimize eder.
     * Koyu tema: arka plan siyah, ikon beyaz; açık tema: arka plan beyaz, ikon siyah.
     */
    fun applyTheme(icon: ImageBitmap, isDarkTheme: Boolean): ImageBitmap {
        val srcBitmap = icon.asAndroidBitmap()
        val dstBitmap = srcBitmap.copy(Bitmap.Config.ARGB_8888, true)
        for (x in 0 until dstBitmap.width) {
            for (y in 0 until dstBitmap.height) {
                val pixel = dstBitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) > 0) {
                    val r = 255 - android.graphics.Color.red(pixel)
                    val g = 255 - android.graphics.Color.green(pixel)
                    val b = 255 - android.graphics.Color.blue(pixel)
                    dstBitmap.setPixel(x, y, android.graphics.Color.argb(android.graphics.Color.alpha(pixel), r, g, b))
                }
            }
        }
        return dstBitmap.asImageBitmap()
    }

    // ==================== CANLI İKON (Zamanla Değişen) ====================

    /**
     * Belirli bir aralıkta renk değiştiren canlı ikon.
     * UI'da LaunchedEffect ile periyodik olarak yeniden çağrılabilir.
     */
    fun getLiveIcon(text: String = "💫", size: Int = DEFAULT_ICON_SIZE, step: Int = 0): ImageBitmap {
        val hue = (step % 360) / 360f
        val color = Color.hsv(hue, 0.7f, 0.9f)
        return getTextIcon(text, size, color)
    }

    // ==================== Önbellek Yönetimi ====================

    fun clearCache() {
        cache.evictAll()
    }

    fun evict(key: String) {
        cache.remove(key)
    }
}