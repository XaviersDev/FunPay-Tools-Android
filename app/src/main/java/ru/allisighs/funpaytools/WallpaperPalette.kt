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

import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min

/**
 * Умная генерация палитры из обоев.
 *
 * Что делается:
 *  1. Bitmap уменьшается до 100×100 для скорости.
 *  2. Пиксели группируются по 24 hue-корзинам (по 15° для лучшей точности).
 *  3. Считаются доминирующие цвета по weight × saturation.
 *  4. Цвета подгоняются так, чтобы:
 *     - accent был ярким и насыщенным (минимум сатурации 0.7),
 *       чтобы кнопки/активные элементы было видно на любом фоне;
 *     - background был очень тёмный (lightness ~3%), с лёгким оттенком
 *       картинки, чтобы при выключении обоев тема не выглядела чёрной;
 *     - surface получал АЛЬФА-канал (~80%) когда генерация под обои,
 *       чтобы карточки красиво просвечивали обои;
 *     - текст имел гарантированный контраст 4.5:1 к surface.
 */
object WallpaperPalette {

    private const val SAMPLE_SIZE = 100

    /**
     * @param forWallpaper если true — surface будет с альфа-каналом,
     *        чтобы карточки полупрозрачно смотрелись поверх обоев.
     */
    fun generate(bitmap: Bitmap, forWallpaper: Boolean = true): GeneratedPalette {
        val scaled = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        scaled.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        if (scaled !== bitmap) scaled.recycle()

        // 24 корзины (по 15°)
        val buckets = Array(24) { HueBucket() }
        val veryDark = HueBucket()
        val veryLight = HueBucket()

        val hsl = FloatArray(3)
        for (p in pixels) {
            val alpha = (p ushr 24) and 0xFF
            if (alpha < 128) continue
            ColorUtils.colorToHSL(p, hsl)
            val h = hsl[0]
            val s = hsl[1]
            val l = hsl[2]

            when {
                l < 0.08f -> veryDark.add(h, s, l)
                l > 0.94f -> veryLight.add(h, s, l)
                s < 0.06f -> if (l < 0.5f) veryDark.add(h, s, l) else veryLight.add(h, s, l)
                else -> {
                    val idx = ((h / 15f).toInt()).coerceIn(0, 23)
                    buckets[idx].add(h, s, l)
                }
            }
        }

        // Vibrant: корзина с максимальным count × saturation × (1 - |L - 0.5|)
        // — то есть с яркими, не пересвеченными цветами.
        val vibrant = buckets
            .filter { it.count > 0 }
            .maxByOrNull { it.count * it.avgS() * (1f - abs(it.avgL() - 0.5f) * 1.5f).coerceAtLeast(0.2f) }
            ?: buckets[0]

        // Dominant — самая тёмная цветная корзина или veryDark
        val totalPixels = pixels.size
        val dominant: HueBucket = run {
            val darkest = buckets.filter { it.count > 0 }.minByOrNull { it.avgL() }
            when {
                veryDark.count > totalPixels / 4 -> veryDark
                darkest != null && darkest.avgL() < 0.35f -> darkest
                else -> vibrant
            }
        }

        // Secondary — другая vibrant корзина
        val secondary: HueBucket = buckets
            .filter { it !== vibrant && it.count > 0 }
            .maxByOrNull { it.count * it.avgS() }
            ?: vibrant

        // === Цвета ===
        val accentInt = hslToColor(
            hue = vibrant.avgH(),
            sat = max(0.70f, vibrant.avgS()),
            light = 0.55f
        )
        val primaryInt = hslToColor(
            hue = vibrant.avgH(),
            sat = max(0.70f, vibrant.avgS()),
            light = 0.48f
        )
        val secondaryInt = hslToColor(
            hue = secondary.avgH(),
            sat = max(0.35f, secondary.avgS()),
            light = 0.32f
        )
        // background: очень тёмный, с оттенком dominant — глубокий и атмосферный.
        val backgroundInt = hslToColor(
            hue = dominant.avgH(),
            sat = min(0.45f, dominant.avgS()),
            light = 0.03f
        )
        // surface (БЕЗ альфы) — для AMOLED-режима и для тем без обоев.
        // На тон светлее background, чтобы карточки выделялись.
        val surfaceInt = hslToColor(
            hue = dominant.avgH(),
            sat = min(0.4f, dominant.avgS() * 0.9f),
            light = 0.12f
        )

        // === ТЕКСТ ===
        // Гарантируем контраст к surface (4.5:1 — WCAG AA для обычного текста).
        val textPrimaryInt = pickHighContrastText(surfaceInt, vibrant.avgH())
        val textSecondaryInt = withMutedAlpha(textPrimaryInt)

        // === SURFACE С АЛЬФА-КАНАЛОМ для тем с обоями ===
        // alpha = 0xCC (~80%) — карточки красиво просвечивают обои, текст читается.
        val surfaceWithAlpha = if (forWallpaper) {
            // Берём чуть более насыщенный/тёплый surface — он будет ложиться на обои
            // и нужно чтобы цветовой оттенок не терялся.
            val opaqueBase = hslToColor(
                hue = dominant.avgH(),
                sat = min(0.5f, dominant.avgS() * 1.2f),
                light = 0.10f
            )
            // Альфа 0xD9 = 217/255 ≈ 85% непрозрачности —
            // чуть просвечивает, но текст хорошо читается.
            val alpha = 0xD9
            ((alpha shl 24) or (opaqueBase and 0x00FFFFFF))
        } else {
            surfaceInt
        }

        return GeneratedPalette(
            primaryHex       = toHexRgb(primaryInt),
            secondaryHex     = toHexRgb(secondaryInt),
            accentHex        = toHexRgb(accentInt),
            backgroundHex    = toHexRgb(backgroundInt),
            // Surface — с альфой! Формат #AARRGGBB.
            surfaceHex       = toHexArgb(surfaceWithAlpha),
            textPrimaryHex   = toHexRgb(textPrimaryInt),
            textSecondaryHex = toHexRgb(textSecondaryInt)
        )
    }

    fun apply(base: AppTheme, p: GeneratedPalette): AppTheme = base.copy(
        primaryColor        = p.primaryHex,
        secondaryColor      = p.secondaryHex,
        accentColor         = p.accentHex,
        backgroundColor     = p.backgroundHex,
        surfaceColor        = p.surfaceHex,
        textPrimaryColor    = p.textPrimaryHex,
        textSecondaryColor  = p.textSecondaryHex,
        originalBackgroundColor = p.backgroundHex,
        // originalSurfaceColor — всегда непрозрачный (RGB-часть из p.surfaceHex без альфы).
        // Это позволит корректно восстановить surface при выключении обоев.
        originalSurfaceColor    = stripAlpha(p.surfaceHex)
    )

    private fun stripAlpha(hex: String): String {
        val s = hex.trim().removePrefix("#")
        return if (s.length == 8) "#${s.substring(2)}" else hex
    }

    // =================== INTERNAL ===================

    private class HueBucket {
        var count: Int = 0
        private var sumSinH = 0.0
        private var sumCosH = 0.0
        private var sumS = 0f
        private var sumL = 0f

        fun add(h: Float, s: Float, l: Float) {
            count++
            val r = Math.toRadians(h.toDouble())
            sumSinH += Math.sin(r)
            sumCosH += Math.cos(r)
            sumS += s
            sumL += l
        }

        fun avgH(): Float {
            if (count == 0) return 0f
            var deg = Math.toDegrees(Math.atan2(sumSinH, sumCosH)).toFloat()
            if (deg < 0f) deg += 360f
            return deg
        }
        fun avgS(): Float = if (count == 0) 0f else sumS / count
        fun avgL(): Float = if (count == 0) 0f else sumL / count
    }

    private fun abs(v: Float): Float = if (v < 0f) -v else v

    private fun hslToColor(hue: Float, sat: Float, light: Float): Int {
        val arr = floatArrayOf(
            hue.coerceIn(0f, 360f),
            sat.coerceIn(0f, 1f),
            light.coerceIn(0f, 1f)
        )
        return ColorUtils.HSLToColor(arr)
    }

    /**
     * Выбирает текстовый цвет с гарантированным контрастом к фону.
     * Если ни белый ни чёрный не дают 4.5:1 — возвращает самый контрастный.
     * Опционально подкрашивает текст под тон акцента (тёплый оттенок).
     */
    private fun pickHighContrastText(bg: Int, accentHue: Float): Int {
        val light = 0xFFEEEEEE.toInt()
        val dark  = 0xFF0F0F0F.toInt()
        val ratioLight = ColorUtils.calculateContrast(light, bg)
        val ratioDark  = ColorUtils.calculateContrast(dark, bg)

        // Тёплый/холодный нейтральный с лёгкой подкраской по hue акцента
        val tinted = if (ratioLight >= 4.5) {
            // Слегка тонируем светлый текст оттенком акцента (очень тонко)
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(light, hsl)
            hsl[0] = accentHue
            hsl[1] = 0.04f  // едва заметный оттенок
            hsl[2] = 0.92f
            ColorUtils.HSLToColor(hsl)
        } else if (ratioDark >= 4.5) {
            dark
        } else {
            // Контраст недостаточен — берём вариант с лучшим
            if (ratioLight >= ratioDark) light else dark
        }
        return tinted
    }

    /** Приглушённая версия text-цвета для secondary text. */
    private fun withMutedAlpha(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        // Сдвигаем lightness к средне-серому, сохраняя оттенок
        hsl[2] = (0.5f + (hsl[2] - 0.5f) * 0.65f).coerceIn(0f, 1f)
        return ColorUtils.HSLToColor(hsl)
    }

    /** RGB → #RRGGBB */
    private fun toHexRgb(c: Int): String = String.format("#%06X", 0xFFFFFF and c)

    /** ARGB → #AARRGGBB */
    private fun toHexArgb(c: Int): String = String.format("#%08X", c.toLong() and 0xFFFFFFFFL)
}

data class GeneratedPalette(
    val primaryHex: String,
    val secondaryHex: String,
    val accentHex: String,
    val backgroundHex: String,
    /** Может быть #RRGGBB или #AARRGGBB — последнее когда сгенерировано под обои. */
    val surfaceHex: String,
    val textPrimaryHex: String,
    val textSecondaryHex: String
)