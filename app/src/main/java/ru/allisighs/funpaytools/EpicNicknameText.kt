package ru.allisighs.funpaytools

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class MiniParticle(
    var x: Float, var y: Float, var s: Float,
    var sx: Float, var sy: Float, var a: Float,
    var char: String = "", var ox: Float = 0f, var ang: Float = 0f
)

@Composable
fun EpicNicknameText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    val config = EpicNicksManager.nicksMap[text]

    if (config == null) {
        Text(text = text, modifier = modifier, style = style, maxLines = maxLines, overflow = overflow)
        return
    }

    val c1 = parseHexColor(config.c1)
    val c2 = parseHexColor(config.c2)
    val c3 = config.c3?.let { parseHexColor(it) }
    val colors = listOfNotNull(c1, c2, c3 ?: c1)

    val isGlow = config.an.contains("glow")
    val isPulse = config.an.contains("pulse")
    val isWave = config.an.contains("wave")
    val isGlitch = config.an.contains("glitch")

    val infiniteTransition = rememberInfiniteTransition(label = "epic_anim")

    val waveOffset by if (isWave) infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween((config.spd * 1000).toInt(), easing = LinearEasing), RepeatMode.Reverse), label = "wave"
    ) else remember { mutableFloatStateOf(0f) }

    val pulseAlpha by if (isPulse) infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween((config.spd * 1000).toInt(), easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    ) else remember { mutableFloatStateOf(1f) }

    val glitchOffset by if (isGlitch) infiniteTransition.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(100, easing = LinearEasing), RepeatMode.Reverse), label = "glitch"
    ) else remember { mutableFloatStateOf(0f) }

    val brush = Brush.linearGradient(
        colors = colors,
        start = Offset(waveOffset, 0f),
        end = Offset(waveOffset + 500f, 500f)
    )

    val shadow = if (isGlow) Shadow(
        color = c1.copy(alpha = 0.8f * pulseAlpha),
        blurRadius = 15f
    ) else null

    // ОПТИМИЗАЦИЯ: Обычный список вместо StateList, чтобы избежать рекомпозиций при мутации
    val particles = remember { ArrayList<MiniParticle>() }
    val overlayType = config.ov

    // Этот тик обновляет ТОЛЬКО Canvas слой каждую миллисекунду, не затрагивая UI элементы (Text)
    val drawTick by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "tick"
    )

    Box(modifier = modifier.offset(x = glitchOffset.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = style.copy(
                brush = brush,
                shadow = shadow,
                fontWeight = FontWeight.ExtraBold,
            ),
            modifier = Modifier.align(Alignment.Center).graphicsLayer(alpha = pulseAlpha),
            maxLines = maxLines,
            overflow = overflow
        )

        if (overlayType != "none") {
            val pColorHex = config.pc

            Canvas(modifier = Modifier.matchParentSize()) {
                val _tick = drawTick // Читаем state, чтобы Compose перерисовывал Canvas каждый кадр
                val w = size.width
                val h = size.height

                // Спавн частиц
                if (particles.size < 6) {
                    when (overlayType) {
                        "fire" -> if (Random.nextFloat() < 0.2f) particles.add(MiniParticle(Random.nextFloat() * 100f, 100f, Random.nextFloat() * 3f + 1f, (Random.nextFloat() - 0.5f) * 1f, Random.nextFloat() * -2f - 1f, 1f))
                        "snow" -> if (Random.nextFloat() < 0.15f) particles.add(MiniParticle(Random.nextFloat() * 100f, 0f, Random.nextFloat() * 1.5f + 0.5f, (Random.nextFloat() - 0.5f), Random.nextFloat() * 2f + 1f, 1f))
                        "sparks" -> if (Random.nextFloat() < 0.2f) particles.add(MiniParticle(Random.nextFloat() * 100f, 80f, Random.nextFloat() * 1.5f + 0.5f, (Random.nextFloat() - 0.5f) * 3f, Random.nextFloat() * -3f - 1f, 1f))
                        "matrix" -> if (Random.nextFloat() < 0.05f) particles.add(MiniParticle(Random.nextFloat() * 100f, 0f, 0f, 0f, 2f, 1f, char = listOf("1","0","A","Z").random()))
                        "stars" -> if (Random.nextFloat() < 0.15f) particles.add(MiniParticle(Random.nextFloat() * 100f, Random.nextFloat() * 100f, Random.nextFloat() * 1.5f, 0.05f, 0f, 0f))
                        "orbs" -> if (Random.nextFloat() < 0.1f) particles.add(MiniParticle(Random.nextFloat() * 100f, 100f, Random.nextFloat() * 3f + 2f, 0f, Random.nextFloat() * -1f - 0.5f, 0.8f, ox = Random.nextFloat() * 100f, ang = Random.nextFloat() * 6f))
                    }
                }

                // Физика и отрисовка
                val iterator = particles.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    val px = (p.x / 100f) * w
                    val py = (p.y / 100f) * h
                    val radius = p.s.dp.toPx()

                    when (overlayType) {
                        "fire" -> {
                            p.x += p.sx; p.y += p.sy; p.s *= 0.9f; p.a -= 0.05f
                            if (p.a <= 0 || p.s <= 0.5f) iterator.remove()
                            else drawCircle(color = getParticleColor(pColorHex, Color(1f, max(0f, 0.86f * p.a), 0f), p.a), radius = radius, center = Offset(px, py))
                        }
                        "snow" -> {
                            p.x += p.sx; p.y += p.sy; p.a = (100f - p.y) / 100f
                            if (p.y > 100f) iterator.remove()
                            else drawCircle(color = getParticleColor(pColorHex, Color.White, p.a), radius = radius, center = Offset(px, py))
                        }
                        "sparks" -> {
                            p.x += p.sx; p.y += p.sy; p.a -= 0.05f
                            if (p.a <= 0f) iterator.remove()
                            else drawRect(color = getParticleColor(pColorHex, Color(1f, 1f, 0.2f), p.a), topLeft = Offset(px, py), size = Size(radius, radius * 2))
                        }
                        "matrix" -> {
                            p.y += p.sy; p.a = (100f - p.y) / 100f
                            if (p.y > 100f) iterator.remove()
                            else {
                                val c = getParticleColor(pColorHex, Color.Green, p.a)
                                drawContext.canvas.nativeCanvas.drawText(p.char, px, py, android.graphics.Paint().apply {
                                    color = android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
                                    textSize = 24f
                                })
                            }
                        }
                        "stars" -> {
                            p.a += p.sx
                            if (p.a > 1f) { p.a = 1f; p.sx = -0.05f }
                            if (p.a < 0f) iterator.remove()
                            else drawCircle(color = getParticleColor(pColorHex, Color.White, p.a), radius = radius, center = Offset(px, py))
                        }
                        "orbs" -> {
                            p.ang += 0.1f; p.x = p.ox + sin(p.ang) * 5f; p.y += p.sy; p.a -= 0.02f
                            if (p.a <= 0f) iterator.remove()
                            else drawCircle(color = getParticleColor(pColorHex, Color(0.8f, 0.5f, 1f), p.a), radius = radius, center = Offset(px, py))
                        }
                    }
                }
            }
        }
    }
}

// Функция-помощник: если кастомный цвет установлен — используем его + применяем текущую альфу, иначе дефолтный
fun getParticleColor(customHex: String?, defaultColor: Color, alpha: Float): Color {
    val a = alpha.coerceIn(0f, 1f)
    if (!customHex.isNullOrBlank()) {
        return parseHexColor(customHex).copy(alpha = a)
    }
    return defaultColor.copy(alpha = a)
}

fun parseHexColor(hexString: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hexString))
    } catch (e: Exception) {
        Color.White
    }
}