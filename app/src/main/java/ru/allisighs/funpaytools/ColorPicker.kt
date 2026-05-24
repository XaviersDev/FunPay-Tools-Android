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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * HSV-пикер с поддержкой alpha и hex-вводом.
 *
 * Состоит из:
 *  - Большое поле S/V (saturation × value) — выбор насыщенности и яркости.
 *  - Hue-слайдер (цветовой круг развёрнутый в полоску).
 *  - Alpha-слайдер с шахматкой.
 *  - Превью текущего цвета и поле для hex-кода #AARRGGBB.
 *
 *  initialColor — строка вида #RRGGBB или #AARRGGBB.
 *  onColorSelected — возвращает строку #AARRGGBB.
 */
@Composable
fun ColorPickerDialog(
    initialColor: String,
    allowAlpha: Boolean = true,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = remember(initialColor) { parseHexColorSafe(initialColor) }
    val initialHsv = remember(initial) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(initial, arr)
        arr
    }

    var hue by remember { mutableStateOf(initialHsv[0]) }
    var sat by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }
    var alpha by remember { mutableStateOf(android.graphics.Color.alpha(initial) / 255f) }

    val currentColor = remember(hue, sat, value, alpha) {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        val a = (alpha * 255f).toInt().coerceIn(0, 255)
        Color(
            red = android.graphics.Color.red(rgb) / 255f,
            green = android.graphics.Color.green(rgb) / 255f,
            blue = android.graphics.Color.blue(rgb) / 255f,
            alpha = a / 255f
        )
    }

    val currentHex = remember(currentColor, allowAlpha) {
        colorToHex(currentColor, includeAlpha = allowAlpha)
    }

    var hexInput by remember { mutableStateOf(currentHex) }
    LaunchedEffect(currentHex) { hexInput = currentHex }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите цвет", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // === Поле S/V ===
                SaturationValuePanel(
                    hue = hue,
                    sat = sat,
                    value = value,
                    onChange = { s, v ->
                        sat = s; value = v
                    }
                )

                // === Hue slider ===
                HueSlider(
                    hue = hue,
                    onHueChange = { hue = it }
                )

                // === Alpha slider ===
                if (allowAlpha) {
                    AlphaSlider(
                        color = Color(
                            red = currentColor.red,
                            green = currentColor.green,
                            blue = currentColor.blue,
                            alpha = 1f
                        ),
                        alpha = alpha,
                        onAlphaChange = { alpha = it }
                    )
                }

                // === Превью + Hex ===
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Превью с шахматкой
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(checkerboardBrush())
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(currentColor)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { newValue ->
                            hexInput = newValue.uppercase()
                            // Парсим только если строка похожа на полный hex
                            if (newValue.length >= 7) {
                                val parsed = parseHexColorSafe(newValue)
                                val arr = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsed, arr)
                                hue = arr[0]; sat = arr[1]; value = arr[2]
                                if (allowAlpha) alpha = android.graphics.Color.alpha(parsed) / 255f
                            }
                        },
                        label = { Text("Hex", fontSize = 11.sp) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(currentHex) }) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// =============================================================================
//                              SUB-COMPONENTS
// =============================================================================

@Composable
private fun SaturationValuePanel(
    hue: Float,
    sat: Float,
    value: Float,
    onChange: (sat: Float, value: Float) -> Unit
) {
    val pureHueColor = remember(hue) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    }

    var size by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(8.dp))
            // Базовый слой: hue → белый (по горизонтали)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color.White, pureHueColor)
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
    ) {
        // Поверх: прозрачный → чёрный (по вертикали)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black)
                    )
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        size = Size(this.size.width.toFloat(), this.size.height.toFloat())
                        val x = change.position.x.coerceIn(0f, size.width)
                        val y = change.position.y.coerceIn(0f, size.height)
                        val newSat = if (size.width > 0) x / size.width else 0f
                        val newVal = if (size.height > 0) 1f - (y / size.height) else 1f
                        onChange(newSat, newVal)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        size = Size(this.size.width.toFloat(), this.size.height.toFloat())
                        val newSat = if (size.width > 0) (tap.x / size.width).coerceIn(0f, 1f) else 0f
                        val newVal = if (size.height > 0) (1f - tap.y / size.height).coerceIn(0f, 1f) else 1f
                        onChange(newSat, newVal)
                    }
                }
        ) {
            // Курсор
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = sat * this.size.width
                val cy = (1f - value) * this.size.height
                drawCircle(
                    color = Color.White,
                    radius = 10.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = Color.Black,
                    radius = 11.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun HueSlider(hue: Float, onHueChange: (Float) -> Unit) {
    val hueBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
                Color(0xFFFF0000)
            )
        )
    }
    var widthPx by remember { mutableStateOf(1f) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(hueBrush)
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    widthPx = this.size.width.toFloat()
                    val x = change.position.x.coerceIn(0f, widthPx)
                    onHueChange((x / widthPx) * 360f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    widthPx = this.size.width.toFloat()
                    onHueChange((tap.x / widthPx).coerceIn(0f, 1f) * 360f)
                }
            }
    ) {
        // Thumb
        val thumbX = with(density) {
            val px = (hue / 360f).coerceIn(0f, 1f) * widthPx
            px.toDp()
        }
        Box(
            modifier = Modifier
                .offset(x = thumbX - 12.dp)
                .align(Alignment.CenterStart)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
        )
    }
}

@Composable
private fun AlphaSlider(color: Color, alpha: Float, onAlphaChange: (Float) -> Unit) {
    var widthPx by remember { mutableStateOf(1f) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(checkerboardBrush())
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
    ) {
        // Градиент прозрачности
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0f), color.copy(alpha = 1f))
                    )
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        widthPx = this.size.width.toFloat()
                        val x = change.position.x.coerceIn(0f, widthPx)
                        onAlphaChange(x / widthPx)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        widthPx = this.size.width.toFloat()
                        onAlphaChange((tap.x / widthPx).coerceIn(0f, 1f))
                    }
                }
        )
        val thumbX = with(density) {
            val px = alpha.coerceIn(0f, 1f) * widthPx
            px.toDp()
        }
        Box(
            modifier = Modifier
                .offset(x = thumbX - 12.dp)
                .align(Alignment.CenterStart)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
        )
    }
}

// =============================================================================
//                              HELPERS
// =============================================================================

/** Шахматка для подсветки прозрачности. */
private fun checkerboardBrush(): Brush {
    // Простой "клетчатый" эффект через linear gradient — не идеально, но быстро.
    return Brush.linearGradient(
        colors = listOf(Color(0xFFCCCCCC), Color(0xFF888888)),
        start = Offset(0f, 0f),
        end = Offset(20f, 20f),
        tileMode = androidx.compose.ui.graphics.TileMode.Repeated
    )
}

/** Безопасный парсинг hex-строки в Int ARGB. */
private fun parseHexColorSafe(hex: String): Int {
    return try {
        var s = hex.trim().removePrefix("#")
        // Принимаем форматы: RGB, RRGGBB, AARRGGBB
        s = when (s.length) {
            3 -> "FF" + s.map { "$it$it" }.joinToString("")
            6 -> "FF$s"
            8 -> s
            else -> "FFFFFFFF"
        }
        s.toLong(16).toInt()
    } catch (e: Exception) {
        0xFFFFFFFF.toInt()
    }
}

/** Compose Color → строка #AARRGGBB или #RRGGBB. */
private fun colorToHex(color: Color, includeAlpha: Boolean = true): String {
    val a = (color.alpha * 255).toInt().coerceIn(0, 255)
    val r = (color.red   * 255).toInt().coerceIn(0, 255)
    val g = (color.green * 255).toInt().coerceIn(0, 255)
    val b = (color.blue  * 255).toInt().coerceIn(0, 255)
    return if (includeAlpha) {
        "#%02X%02X%02X%02X".format(a, r, g, b)
    } else {
        "#%02X%02X%02X".format(r, g, b)
    }
}