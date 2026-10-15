package ru.allisighs.funpaytools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Синий цвет метки из расширения (#1b75bb), чтобы «свои» узнавались одинаково везде. */
val FptBlue = Color(0xFF1B75BB)

/**
 * Тонкая плашка как плеер музыки в Telegram: «Собеседник тоже пользуется FunPay Tools».
 * Показывается под шапкой чата (или под плашкой «Смотрит лот»), закрывается крестиком.
 */
@Composable
fun FptPeerBanner(palette: ChatPalette, theme: AppTheme, onClose: () -> Unit) {
    val surface = ThemeManager.dialogSurface(theme)
    val accent = palette.accent
    val shine = remember { Animatable(-0.3f) }
    LaunchedEffect(Unit) {
        // Один «блик» по плашке при появлении
        shine.animateTo(1.3f, tween(1100, delayMillis = 180, easing = FastOutSlowInEasing))
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(surface)
                .drawWithContent {
                    drawContent()
                    val p = shine.value
                    if (p > -0.3f && p < 1.3f) {
                        val x = size.width * p
                        val w = size.width * 0.28f
                        drawRect(
                            Brush.linearGradient(
                                colors = listOf(Color.Transparent, accent.copy(alpha = if (palette.isLight) 0.14f else 0.22f), Color.Transparent),
                                start = Offset(x - w, 0f),
                                end = Offset(x + w, size.height)
                            )
                        )
                    }
                }
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.15f), FptBlue))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Handshake, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                buildAnnotatedString {
                    append("Собеседник тоже пользуется ")
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append("FunPay Tools") }
                    append(" 🎉")
                },
                fontSize = 13.sp,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Скрыть", tint = palette.secondary, modifier = Modifier.size(18.dp))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(palette.secondary.copy(alpha = 0.18f))
        )
    }
}

/** Подсказка в меню «три точки»: собеседник пользуется FunPay Tools. */
@Composable
fun FptMenuHint(theme: AppTheme) {
    Row(
        modifier = Modifier
            .widthIn(min = 260.dp, max = 300.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(FptBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Handshake, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Собеседник пользуется FunPay Tools",
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = ThemeManager.parseColor(theme.textSecondaryColor)
        )
    }
}
