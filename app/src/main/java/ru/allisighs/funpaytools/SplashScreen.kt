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

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Оптимизированный сплеш-экран.
 *
 * Принципы избегания лагов:
 *  - НИКАКОГО Canvas с Brush.sweepGradient — он пересоздаёт shader каждый кадр.
 *  - Все brush/color объекты в remember.
 *  - Минимум infiniteRepeatable — каждая такая анимация это вечный recomp.
 *  - Орбита-окружность — статичный Box с border, не Canvas.
 *  - Спутник по орбите — через offset на State<Float>, не пересоздавая модификаторы.
 */
@Composable
fun SplashScreen(onTimeout: () -> Unit, theme: AppTheme) {
    val accentColor        = ThemeManager.parseColor(theme.accentColor)
    val bgColor            = ThemeManager.parseColor(theme.backgroundColor)
    val textColor          = ThemeManager.parseColor(theme.textPrimaryColor)
    val secondaryTextColor = ThemeManager.parseColor(theme.textSecondaryColor)

    
    val bgBrush = remember(accentColor, bgColor) {
        Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.22f),
                bgColor,
                Color.Black
            ),
            radius = 1400f
        )
    }
    val iconCircleBrush = remember(accentColor) {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.30f),
                accentColor.copy(alpha = 0.10f)
            )
        )
    }

    var startAnimation by remember { mutableStateOf(false) }
    var showOrbit by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }

    
    val iconScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "iconScale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(500),
        label = "iconAlpha"
    )

    
    val infinite = rememberInfiniteTransition(label = "infinite")
    val orbitAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitAngle"
    )

    val ringAlpha by animateFloatAsState(
        targetValue = if (showOrbit) 1f else 0f,
        animationSpec = tween(600),
        label = "ringAlpha"
    )

    
    LaunchedEffect(Unit) {
        startAnimation = true
        delay(280)
        showOrbit = true
        delay(220)
        showTitle = true
        delay(220)
        showSubtitle = true
        delay(1100)
        onTimeout()
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(bgBrush)
    ) {

        
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            
            Box(
                modifier = Modifier.size(260.dp),
                contentAlignment = Alignment.Center
            ) {

                
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .alpha(ringAlpha * 0.4f)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.05f))
                )

                
                
                val angleRad = (orbitAngle.toDouble()) * Math.PI / 180.0
                val orbitRadiusDp = 102f
                val dx = (orbitRadiusDp * cos(angleRad)).toFloat()
                val dy = (orbitRadiusDp * sin(angleRad)).toFloat()

                Box(
                    modifier = Modifier
                        .offset(x = dx.dp, y = dy.dp)
                        .size(40.dp)
                        .alpha(ringAlpha)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_funpay_arrows),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(accentColor),
                        modifier = Modifier.size(22.dp)
                    )
                }

                
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .scale(iconScale)
                        .alpha(iconAlpha)
                        .clip(CircleShape)
                        .background(iconCircleBrush),
                    contentAlignment = Alignment.Center
                ) {
                    AppLauncherIcon(modifier = Modifier.size(108.dp).clip(CircleShape))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(animationSpec = tween(500)) +
                        slideInVertically(initialOffsetY = { 30 }, animationSpec = tween(500))
            ) {
                Text(
                    text = "FunPay Tools",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedVisibility(
                visible = showSubtitle,
                enter = fadeIn(animationSpec = tween(500)) +
                        slideInVertically(initialOffsetY = { 20 }, animationSpec = tween(500))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Приложение для автоматизации FunPay",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = accentColor,
                        modifier = Modifier.alpha(0.9f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "v1.3 • by AlliSighs",
                        fontSize = 11.sp,
                        color = secondaryTextColor,
                        modifier = Modifier.alpha(0.55f)
                    )
                }
            }
        }

        
        val bottomAlpha by animateFloatAsState(
            targetValue = if (showSubtitle) 1f else 0f,
            animationSpec = tween(800),
            label = "bottomAlpha"
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .alpha(bottomAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedDots(color = accentColor)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Загрузка...",
                fontSize = 12.sp,
                color = secondaryTextColor,
                modifier = Modifier.alpha(0.7f)
            )
        }
    }
}

/** Бегущие три точки — лёгкая анимация. */
@Composable
private fun AnimatedDots(color: Color) {
    val infinite = rememberInfiniteTransition(label = "dots")

    val a1 by infinite.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "d1"
    )
    val a2 by infinite.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, 150, FastOutSlowInEasing), RepeatMode.Reverse),
        label = "d2"
    )
    val a3 by infinite.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, 300, FastOutSlowInEasing), RepeatMode.Reverse),
        label = "d3"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).alpha(a1).background(color, CircleShape))
        Box(Modifier.size(7.dp).alpha(a2).background(color, CircleShape))
        Box(Modifier.size(7.dp).alpha(a3).background(color, CircleShape))
    }
}

/**
 * Иконка приложения через PackageManager (работает с adaptive icons).
 */
@Composable
private fun AppLauncherIcon(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 108
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 108
            val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        } catch (e: Throwable) {
            e.printStackTrace()
            android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        }
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}