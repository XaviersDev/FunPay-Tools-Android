/*
 *
 *  * Copyright (c) 2026 XaviersDev (AlliSighs). All rights reserved.
 *  *
 *  * This code is proprietary. Modification, distribution, or use
 *  * of this file without express written permission is strictly prohibited.
 *  * Unauthorized use will be prosecuted.
 *
 */


package ru.allisighs.funpaytools

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream

/**
 * Эффект движения обоев темы.
 * STATIC — без движения.
 * PARALLAX — лёгкое смещение картинки при наклоне телефона (как темы Telegram).
 */
enum class WallpaperEffect { STATIC, PARALLAX }

data class AppTheme(
    val name: String,
    val primaryColor: String,
    val secondaryColor: String,
    val backgroundColor: String,
    val surfaceColor: String,
    val textPrimaryColor: String,
    val textSecondaryColor: String,
    val accentColor: String,
    val isAmoled: Boolean = false,
    val containerOpacity: Float = 0.9f,
    val borderRadius: Int = 16,
    val originalBackgroundColor: String = "#050505",
    val originalSurfaceColor: String = "#1A1A1A",

    // === Кастомные обои ===
    /** Включены ли обои. Когда true — backgroundColor темы становится прозрачным. */
    val useWallpaper: Boolean = false,
    /** Абсолютный путь к файлу обоев в internal storage. */
    val wallpaperPath: String? = null,
    /** Прозрачность самой картинки обоев (0f — невидима, 1f — полностью видна). */
    val wallpaperAlpha: Float = 1f,
    /** Сила затемнения поверх обоев (0f — без затемнения, 1f — чёрный экран). */
    val wallpaperDim: Float = 0.35f,
    /** Радиус блюра обоев в dp (0 — без блюра). На API < 31 будет имитация через прозрачность. */
    val wallpaperBlur: Int = 0,
    /** Эффект движения. */
    val wallpaperEffect: WallpaperEffect = WallpaperEffect.STATIC,
    /** Сила параллакса (0..1). 0.05 — еле заметно, 0.2 — сильно. */
    val parallaxIntensity: Float = 0.08f,
    /** Таймстемп последней загрузки обоев — для инвалидации кеша bitmap. */
    val wallpaperVersion: Long = 0L
)

object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_CURRENT_THEME = "current_theme"
    private const val KEY_AMOLED_MODE = "amoled_mode"
    private const val WALLPAPER_FILENAME = "theme_wallpaper.jpg"

    val defaultThemes = listOf(
        AppTheme(
            name = "Purple Dream",
            primaryColor = "#651FFF",
            secondaryColor = "#311B92",
            backgroundColor = "#050505",
            surfaceColor = "#1A1A1A",
            textPrimaryColor = "#EEEEEE",
            textSecondaryColor = "#B0B0B0",
            accentColor = "#651FFF",
            originalBackgroundColor = "#050505",
            originalSurfaceColor = "#1A1A1A"
        ),
        AppTheme(
            name = "Ocean Blue",
            primaryColor = "#0091EA",
            secondaryColor = "#01579B",
            backgroundColor = "#000510",
            surfaceColor = "#0A1929",
            textPrimaryColor = "#E3F2FD",
            textSecondaryColor = "#90CAF9",
            accentColor = "#00B0FF",
            originalBackgroundColor = "#000510",
            originalSurfaceColor = "#0A1929"
        ),
        AppTheme(
            name = "Emerald",
            primaryColor = "#00C853",
            secondaryColor = "#1B5E20",
            backgroundColor = "#001505",
            surfaceColor = "#0D2818",
            textPrimaryColor = "#E8F5E9",
            textSecondaryColor = "#A5D6A7",
            accentColor = "#00E676",
            originalBackgroundColor = "#001505",
            originalSurfaceColor = "#0D2818"
        ),
        AppTheme(
            name = "Sunset Orange",
            primaryColor = "#FF6D00",
            secondaryColor = "#BF360C",
            backgroundColor = "#150500",
            surfaceColor = "#2E1A0D",
            textPrimaryColor = "#FFF3E0",
            textSecondaryColor = "#FFCC80",
            accentColor = "#FF9100",
            originalBackgroundColor = "#150500",
            originalSurfaceColor = "#2E1A0D"
        ),
        AppTheme(
            name = "Rose Gold",
            primaryColor = "#EC407A",
            secondaryColor = "#880E4F",
            backgroundColor = "#15000A",
            surfaceColor = "#2A0D1A",
            textPrimaryColor = "#FCE4EC",
            textSecondaryColor = "#F48FB1",
            accentColor = "#F50057",
            originalBackgroundColor = "#15000A",
            originalSurfaceColor = "#2A0D1A"
        ),
        AppTheme(
            name = "Cyber Violet",
            primaryColor = "#D500F9",
            secondaryColor = "#6A1B9A",
            backgroundColor = "#0D000F",
            surfaceColor = "#1F0A2E",
            textPrimaryColor = "#F3E5F5",
            textSecondaryColor = "#CE93D8",
            accentColor = "#E040FB",
            originalBackgroundColor = "#0D000F",
            originalSurfaceColor = "#1F0A2E"
        )
    )

    fun saveTheme(context: Context, theme: AppTheme) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // КЛЮЧЕВОЕ: когда обои включены, делаем backgroundColor прозрачным
        // (с сохранением оригинала в originalBackgroundColor) — это позволяет
        // обоям просвечивать через все Scaffold/Surface, без правок MainActivity.
        val patched = applyWallpaperBackgroundOverride(theme)
        val json = Gson().toJson(patched)
        prefs.edit().putString(KEY_CURRENT_THEME, json).apply()
    }

    /**
     * Возвращает копию темы с учётом включённых обоев:
     * 1. backgroundColor → прозрачный (originalBackgroundColor сохраняется),
     *    чтобы Scaffold/Surface не перекрывали обои на корне приложения.
     * 2. containerOpacity синхронизируется с альфой surfaceColor.
     *    Это позволяет всем местам в коде, которые делают
     *    parseColor(surfaceColor).copy(alpha = containerOpacity)
     *    получить корректную альфу автоматически — без правок MainActivity.
     */
    fun applyWallpaperBackgroundOverride(theme: AppTheme): AppTheme {
        // === Извлекаем альфу из surfaceColor и кладём в containerOpacity ===
        // Это решает проблему: многие места в коде делают
        // parseColor(surfaceColor).copy(alpha = containerOpacity),
        // что выкидывает альфу из самого hex-цвета. Синхронизируем поля.
        val extractedAlpha = extractAlphaFromHex(theme.surfaceColor)
        val syncedOpacity = extractedAlpha ?: theme.containerOpacity

        val themeWithSyncedOpacity = if (extractedAlpha != null &&
            extractedAlpha != theme.containerOpacity) {
            theme.copy(containerOpacity = syncedOpacity)
        } else theme

        return if (themeWithSyncedOpacity.useWallpaper) {
            val origBg = if (themeWithSyncedOpacity.backgroundColor.equals("#00000000", true) ||
                themeWithSyncedOpacity.backgroundColor.equals("#0000", true)) {
                themeWithSyncedOpacity.originalBackgroundColor
            } else {
                themeWithSyncedOpacity.backgroundColor
            }
            themeWithSyncedOpacity.copy(
                backgroundColor = "#00000000",
                originalBackgroundColor = origBg
            )
        } else {
            // Обои выключены — восстанавливаем непрозрачный background
            if (themeWithSyncedOpacity.backgroundColor.equals("#00000000", true) ||
                themeWithSyncedOpacity.backgroundColor.equals("#0000", true)) {
                themeWithSyncedOpacity.copy(backgroundColor = themeWithSyncedOpacity.originalBackgroundColor)
            } else themeWithSyncedOpacity
        }
    }

    /**
     * Извлекает alpha-канал из hex-строки.
     * Возвращает Float [0..1], или null если у цвета нет явной альфы (#RRGGBB).
     */
    private fun extractAlphaFromHex(hex: String): Float? {
        val s = hex.trim().removePrefix("#")
        if (s.length != 8) return null
        return try {
            val alphaByte = s.substring(0, 2).toInt(16)
            alphaByte / 255f
        } catch (e: Exception) { null }
    }

    fun loadTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CURRENT_THEME, null)
        if (json.isNullOrBlank()) return defaultThemes[0]

        return try {
            val raw = Gson().fromJson(json, AppTheme::class.java) ?: return defaultThemes[0]
            applyWallpaperBackgroundOverride(sanitizeTheme(raw))
        } catch (e: Exception) {
            e.printStackTrace()
            defaultThemes[0]
        }
    }

    /**
     * Защита от старого JSON: Gson через рефлексию минует конструктор Kotlin
     * и НЕ применяет default values. Поля, отсутствующие в старом JSON,
     * могут оказаться null даже у non-nullable свойств — это вызовет
     * NullPointerException при первом обращении.
     *
     * Здесь мы пересоздаём тему через конструктор, явно подставляя дефолты
     * для всего, что может быть null.
     */
    private fun sanitizeTheme(t: AppTheme): AppTheme {
        val d = defaultThemes[0]
        // Используем рефлексию через JsonElement, чтобы прочитать поля безопасно?
        // Слишком сложно. Применяем простой трюк: пересоздаём через копирование.
        // Если t.wallpaperEffect == null (Gson не нашёл поле), мы это поймаем
        // через runCatching при обращении и подставим STATIC.
        val safeEffect = runCatching { t.wallpaperEffect }.getOrNull() ?: WallpaperEffect.STATIC
        val safeName = runCatching { t.name }.getOrNull() ?: d.name
        val safePrimary = runCatching { t.primaryColor }.getOrNull() ?: d.primaryColor
        val safeSecondary = runCatching { t.secondaryColor }.getOrNull() ?: d.secondaryColor
        val safeBg = runCatching { t.backgroundColor }.getOrNull() ?: d.backgroundColor
        val safeSurface = runCatching { t.surfaceColor }.getOrNull() ?: d.surfaceColor
        val safeTextP = runCatching { t.textPrimaryColor }.getOrNull() ?: d.textPrimaryColor
        val safeTextS = runCatching { t.textSecondaryColor }.getOrNull() ?: d.textSecondaryColor
        val safeAccent = runCatching { t.accentColor }.getOrNull() ?: d.accentColor
        val safeOrigBg = runCatching { t.originalBackgroundColor }.getOrNull() ?: safeBg
        val safeOrigSurface = runCatching { t.originalSurfaceColor }.getOrNull() ?: safeSurface

        val safeAlpha = runCatching { t.wallpaperAlpha }.getOrNull()
            ?.let { if (it.isNaN() || it <= 0f) 1f else it.coerceIn(0f, 1f) } ?: 1f
        val safeDim = runCatching { t.wallpaperDim }.getOrNull()
            ?.let { if (it.isNaN()) 0.35f else it.coerceIn(0f, 1f) } ?: 0.35f
        val safeBlur = runCatching { t.wallpaperBlur }.getOrNull()
            ?.let { if (it < 0) 0 else it } ?: 0
        val safeParallax = runCatching { t.parallaxIntensity }.getOrNull()
            ?.let { if (it.isNaN() || it <= 0f) 0.08f else it } ?: 0.08f
        val safeVersion = runCatching { t.wallpaperVersion }.getOrNull() ?: 0L
        val safeOpacity = runCatching { t.containerOpacity }.getOrNull()
            ?.let { if (it.isNaN() || it == 0f) 0.9f else it } ?: 0.9f
        val safeRadius = runCatching { t.borderRadius }.getOrNull()
            ?.let { if (it <= 0) 16 else it } ?: 16
        val safeUseWp = runCatching { t.useWallpaper }.getOrNull() ?: false
        val safeIsAmoled = runCatching { t.isAmoled }.getOrNull() ?: false
        val safeWpPath = runCatching { t.wallpaperPath }.getOrNull()

        return AppTheme(
            name                   = safeName,
            primaryColor           = safePrimary,
            secondaryColor         = safeSecondary,
            backgroundColor        = safeBg,
            surfaceColor           = safeSurface,
            textPrimaryColor       = safeTextP,
            textSecondaryColor     = safeTextS,
            accentColor            = safeAccent,
            isAmoled               = safeIsAmoled,
            containerOpacity       = safeOpacity,
            borderRadius           = safeRadius,
            originalBackgroundColor= safeOrigBg,
            originalSurfaceColor   = safeOrigSurface,
            useWallpaper           = safeUseWp,
            wallpaperPath          = safeWpPath,
            wallpaperAlpha         = safeAlpha,
            wallpaperDim           = safeDim,
            wallpaperBlur          = safeBlur,
            wallpaperEffect        = safeEffect,
            parallaxIntensity      = safeParallax,
            wallpaperVersion       = safeVersion
        )
    }

    fun setAmoledMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
    }

    fun isAmoledMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AMOLED_MODE, false)
    }

    fun parseColor(hexColor: String): Color {
        return try {
            Color(android.graphics.Color.parseColor(hexColor))
        } catch (e: Exception) {
            Color.White
        }
    }

    // =========================================================================
    //                              WALLPAPER I/O
    // =========================================================================

    /**
     * Копирует выбранную картинку из Uri в internal storage приложения,
     * чтобы она пережила перезагрузки и не зависела от внешнего хранилища.
     * Возвращает абсолютный путь к сохранённой картинке, или null при ошибке.
     */
    fun saveWallpaperFromUri(context: Context, uri: Uri): String? {
        return try {
            val outFile = File(context.filesDir, WALLPAPER_FILENAME)

            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return null
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Удаляет сохранённый файл обоев. */
    fun deleteWallpaper(context: Context) {
        try {
            File(context.filesDir, WALLPAPER_FILENAME).delete()
        } catch (_: Exception) {}
    }

    /**
     * Возвращает Bitmap обоев или null, если файла нет.
     * Загружается с разумным sample size, чтобы не сожрать память.
     */
    fun loadWallpaperBitmap(path: String?): android.graphics.Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            // Замер размера сначала
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)

            // Целевые размеры — телефонный экран в worst case ~1440x3200
            val maxDim = 1600
            var sample = 1
            while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
                sample *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, decodeOpts)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}