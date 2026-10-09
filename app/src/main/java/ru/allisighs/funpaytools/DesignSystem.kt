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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Текущая тема приложения для "старых" токенов дизайна ниже (они используются
 * на экранах входа/разрешений). Для тёмных тем токены остаются прежними,
 * для светлых (FunPay) — берутся из темы, иначе светлый текст пропадает на белом.
 */
object DesignTokens {
    @Volatile var theme: AppTheme? = null
    val light: Boolean get() = theme?.let { ThemeManager.isLight(it) } == true
}

private fun tokenColor(hex: String?, fallback: Color): Color =
    if (DesignTokens.light && hex != null) ThemeManager.parseColor(hex) else fallback

val BlackBg: Color get() = tokenColor(DesignTokens.theme?.backgroundColor, Color(0xFF050505))
val PurpleAccent: Color get() = tokenColor(DesignTokens.theme?.accentColor, Color(0xFF651FFF))
val PurpleDark: Color get() = tokenColor(DesignTokens.theme?.secondaryColor, Color(0xFF311B92))
val GlassWhite: Color get() = tokenColor(DesignTokens.theme?.surfaceColor, Color(0xFF1A1A1A).copy(alpha = 0.9f))
val GlassBorder: Color get() = if (DesignTokens.light) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.15f)
val TextPrimary: Color get() = tokenColor(DesignTokens.theme?.textPrimaryColor, Color(0xFFEEEEEE))
val TextSecondary: Color get() = tokenColor(DesignTokens.theme?.textSecondaryColor, Color(0xFFB0B0B0))

private val darkGradient = Brush.verticalGradient(colors = listOf(Color.Black, Color(0xFF0D001A), Color.Black))

val AppGradient: Brush
    get() {
        val t = DesignTokens.theme
        return if (t != null && ThemeManager.isLight(t)) {
            val bg = ThemeManager.parseColorOpaque(t.originalBackgroundColor.ifBlank { t.backgroundColor })
            Brush.verticalGradient(colors = listOf(bg, bg))
        } else darkGradient
    }

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF651FFF),
    secondary = Color(0xFF311B92),
    background = Color(0xFF050505),
    surface = Color(0xFF1A1A1A).copy(alpha = 0.9f),
    onPrimary = Color.White,
    onBackground = Color(0xFFEEEEEE),
    onSurface = Color(0xFFEEEEEE)
)

/** Material-схема под тему: для светлых тем — светлая, для тёмных — прежняя. */
fun colorSchemeFor(theme: AppTheme): androidx.compose.material3.ColorScheme {
    if (!ThemeManager.isLight(theme)) return DarkColorScheme
    val accent = ThemeManager.parseColor(theme.accentColor)
    val bg = ThemeManager.parseColorOpaque(theme.originalBackgroundColor.ifBlank { theme.backgroundColor })
    val surface = ThemeManager.parseColorOpaque(theme.originalSurfaceColor.ifBlank { theme.surfaceColor })
    val text = ThemeManager.parseColor(theme.textPrimaryColor)
    val text2 = ThemeManager.parseColor(theme.textSecondaryColor)
    return androidx.compose.material3.lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = 0.14f),
        onPrimaryContainer = text,
        secondary = ThemeManager.parseColor(theme.secondaryColor),
        onSecondary = Color.White,
        secondaryContainer = accent.copy(alpha = 0.12f),
        onSecondaryContainer = text,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = Color(0xFFE6EBF2),
        onSurfaceVariant = text2,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = Color(0xFFF1F4F8),
        surfaceContainerLow = surface,
        surfaceContainerLowest = surface,
        outline = Color(0xFFC9D1DC),
        outlineVariant = Color(0xFFE1E6ED)
    )
}

fun Modifier.liquidGlass(): Modifier = this
    .clip(RoundedCornerShape(16.dp))
    .background(GlassWhite)
    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))