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
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Слой обоев темы. Рендерится ОДИН РАЗ на корне UI поверх AppGradient.
 *
 * Эффекты:
 *  - alpha — прозрачность картинки.
 *  - dim — чёрная заливка поверх (затемнение).
 *  - параллакс — смещение картинки по показаниям акселерометра.
 *
 * Блюра нет (как в Telegram-темах) — он не нужен и съедал производительность.
 */
@Composable
fun WallpaperLayer(theme: AppTheme, modifier: Modifier = Modifier) {
    
    
    
    
    val cacheKey = "${theme.wallpaperPath}#${theme.wallpaperVersion}"
    var bitmap by remember(cacheKey) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(cacheKey, theme.useWallpaper) {
        if (theme.useWallpaper && !theme.wallpaperPath.isNullOrBlank()) {
            bitmap = withContext(Dispatchers.IO) {
                ThemeManager.loadWallpaperBitmap(theme.wallpaperPath)
            }
        } else {
            bitmap = null
        }
    }

    
    
    val parallax = rememberParallaxOffset(
        enabled = theme.useWallpaper && theme.wallpaperEffect == WallpaperEffect.PARALLAX,
        intensity = theme.parallaxIntensity
    )

    val bmp = bitmap
    if (!theme.useWallpaper || bmp == null) return

    Box(modifier = modifier.fillMaxSize()) {

        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = theme.wallpaperAlpha,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = if (theme.wallpaperEffect == WallpaperEffect.PARALLAX) 1.12f else 1.0f
                    scaleX = scale
                    scaleY = scale
                    translationX = parallax.first
                    translationY = parallax.second
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = theme.wallpaperDim))
        )
    }
}

/**
 * Возвращает смещения параллакса.
 * Принимает enabled-флаг чтобы количество хуков было одинаковым при любом состоянии.
 */
@Composable
private fun rememberParallaxOffset(enabled: Boolean, intensity: Float): Pair<Float, Float> {
    val context = LocalContext.current
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    DisposableEffect(enabled, intensity) {
        if (!enabled) {
            offsetX = 0f
            offsetY = 0f
            return@DisposableEffect onDispose { }
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val density = context.resources.displayMetrics.density
        val maxShiftPx = 200f * density * intensity

        var smoothX = 0f
        var smoothY = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                val rawX = -(event.values[0] / 9.81f).coerceIn(-1f, 1f)
                val rawY =  (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                smoothX = smoothX * 0.85f + rawX * 0.15f
                smoothY = smoothY * 0.85f + rawY * 0.15f
                offsetX = smoothX * maxShiftPx
                offsetY = smoothY * maxShiftPx
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    return offsetX to offsetY
}





fun AppTheme.effectiveBackground(): Color =
    if (useWallpaper) Color.Transparent
    else ThemeManager.parseColor(backgroundColor)

fun AppTheme.effectiveSurface(): Color =
    if (useWallpaper) ThemeManager.parseColor(surfaceColor).copy(alpha = containerOpacity)
    else ThemeManager.parseColor(surfaceColor)