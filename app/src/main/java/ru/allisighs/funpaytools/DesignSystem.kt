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

val BlackBg = Color(0xFF050505)
val PurpleAccent = Color(0xFF651FFF)
val PurpleDark = Color(0xFF311B92)
val GlassWhite = Color(0xFF1A1A1A).copy(alpha = 0.9f)
val GlassBorder = Color.White.copy(alpha = 0.15f)
val TextPrimary = Color(0xFFEEEEEE)
val TextSecondary = Color(0xFFB0B0B0)

val AppGradient = Brush.verticalGradient(
    colors = listOf(Color.Black, Color(0xFF0D001A), Color.Black)
)

val DarkColorScheme = darkColorScheme(
    primary = PurpleAccent,
    secondary = PurpleDark,
    background = BlackBg,
    surface = GlassWhite,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

fun Modifier.liquidGlass(): Modifier = this
    .clip(RoundedCornerShape(16.dp))
    .background(GlassWhite)
    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))