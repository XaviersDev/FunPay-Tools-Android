/*
 * Copyright (c) 2026 XaviersDev (AlliSighs). All rights reserved.
 *
 * This code is proprietary. Modification, distribution, or use
 * of this file without express written permission is strictly prohibited.
 * Unauthorized use will be prosecuted.
 */

package ru.allisighs.funpaytools

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

fun safeParseAosColor(hex: String?): Color {
    return try {
        if (hex.isNullOrBlank()) Color(0xFF00BFA5)
        else Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF00BFA5)
    }
}

val AOS_COLORS = listOf(
    "#FFFFFF", "#FF5252", "#FF4081", "#E040FB", "#7C4DFF",
    "#536DFE", "#448AFF", "#00BCD4", "#00BFA5", "#64FFDA",
    "#69F0AE", "#B2FF59", "#EEFF41", "#FFFF00", "#FFAB40",
    "#FF6D00", "#DD2C00", "#6200EA", "#00C853", "#00B0FF"
)

val AOS_THEME_NAMES = listOf(
    "Aurora", "Neon Pulse", "Hologram", "Terminal", "Orbital",
    "Bento", "Liquid", "Cipher", "Spectrum", "Depth",
    "Plasma", "Zen", "Inferno", "Arctic", "Prism"
)

enum class AosWidgetType(val title: String) {
    TIME("Время"), DATE("Дата"), BALANCE("Общий Баланс"),
    ACTIVE_BALANCE("Доступно"), FROZEN_BALANCE("В холде"),
    UNREAD("Чат (кол-во)"), SALES("Кол-во продаж"),
    ONLINE("Статус Онлайн"), AVATAR("Аватарка")
}

data class AosWidget(
    val id: String = UUID.randomUUID().toString(),
    val type: AosWidgetType,
    var xDp: Float = 100f,
    var yDp: Float = 100f,
    var scale: Float = 1f
)

data class AosConfig(
    val themeIndex: Int = 1,
    val hexColor: String = "#00BFA5",
    val useCustomConstructor: Boolean = false,
    val customBgUri: String? = null,
    val isLandscape: Boolean = true,
    val enableAnimations: Boolean = true,
    val widgets: List<AosWidget> = emptyList()
)

data class AosThemeData(
    val time: String, val date: String, val balanceAll: String,
    val balanceActive: String, val balanceFrozen: String,
    val unreadCount: Int, val online: Boolean, val salesCount: Int,
    val avatarUrl: String?, val username: String
)

object AosManager {
    private const val PREFS = "aos_prefs"
    private val gson = Gson()
    fun getConfig(context: Context): AosConfig {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("config", null)
        return if (json != null) try { gson.fromJson(json, AosConfig::class.java) } catch (e: Exception) { AosConfig() } else AosConfig()
    }
    fun saveConfig(context: Context, config: AosConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("config", gson.toJson(config)).apply()
    }
}

@Composable
fun AosCanvasWrapper(
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(globalScale: Float) -> Unit
) {
    // ИСПРАВЛЕНИЕ СДВИГА: добавлено fillMaxSize() для жесткого центрирования
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val logicalW = if (isLandscape) 800f else 400f
        val logicalH = if (isLandscape) 400f else 800f
        val scale = minOf(maxWidth.value / logicalW, maxHeight.value / logicalH)

        Box(
            modifier = Modifier
                .requiredSize(logicalW.dp, logicalH.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                }
        ) {
            content(scale)
        }
    }
}

@Composable
fun AosSettingsScreen(navController: NavController, theme: AppTheme) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current // Добавлено для безопасного вычисления ширины
    var config by remember { mutableStateOf(AosManager.getConfig(context)) }
    val accentColor = ThemeManager.parseColor(theme.accentColor)

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            config = config.copy(customBgUri = uri.toString())
            AosManager.saveConfig(context, config)
        }
    }

    val previewData = AosThemeData(
        time = "12:34", date = SimpleDateFormat("dd MMMM yyyy", Locale("ru")).format(Date()),
        balanceAll = "12 500 ₽", balanceActive = "10 000 ₽", balanceFrozen = "2 500 ₽",
        unreadCount = 3, online = true, salesCount = 142, avatarUrl = null, username = "FunPaySeller"
    )

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).background(Brush.radialGradient(listOf(accentColor, accentColor.copy(0.3f))), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Tv, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Вечный дисплей (AOS)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
                            Text("Дашборд статистики для телефона", fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { navController.navigate("aos_display") }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = accentColor), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("ЗАПУСТИТЬ ДИСПЛЕЙ", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.width(3.dp).height(16.dp).background(accentColor, RoundedCornerShape(2.dp))); Spacer(Modifier.width(8.dp))
                Text("ПРЕДПРОСМОТР", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp)
            }

            val aspectRatio = if (config.isLandscape) 16f / 9f else 9f / 16f
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio).clip(RoundedCornerShape(16.dp)).background(Color.Black).border(2.dp, Brush.linearGradient(listOf(accentColor, accentColor.copy(0.3f))), RoundedCornerShape(16.dp))) {
                AosCanvasWrapper(isLandscape = config.isLandscape) { globalScale ->
                    val aosTint = safeParseAosColor(config.hexColor)
                    if (config.customBgUri != null) {
                        AsyncImage(model = config.customBgUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)))
                    }
                    if (config.useCustomConstructor) {
                        config.widgets.forEach { w ->
                            Box(Modifier.offset(x = w.xDp.dp, y = w.yDp.dp).graphicsLayer { scaleX = w.scale; scaleY = w.scale; transformOrigin = TransformOrigin(0f, 0f) }) {
                                AosWidgetRealDataRenderer(w.type, aosTint, previewData)
                            }
                        }
                    } else {
                        RenderAosTheme(config.themeIndex, aosTint, previewData, config.enableAnimations, null, config.isLandscape)
                    }
                }
                if (!config.useCustomConstructor) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(0.7f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(AOS_THEME_NAMES.getOrElse(config.themeIndex - 1) { "Тема ${config.themeIndex}" }, color = safeParseAosColor(config.hexColor), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.ScreenRotation, null, tint = accentColor, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                        Text("Альбомный режим", color = ThemeManager.parseColor(theme.textPrimaryColor), modifier = Modifier.weight(1f))
                        Switch(checked = config.isLandscape, onCheckedChange = { config = config.copy(isLandscape = it); AosManager.saveConfig(context, config) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(0.4f)))
                    }
                    HorizontalDivider(color = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.1f))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Animation, null, tint = accentColor, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Плавные анимации", color = ThemeManager.parseColor(theme.textPrimaryColor))
                            Text("Отключите для экономии батареи", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp)
                        }
                        Switch(checked = config.enableAnimations, onCheckedChange = { config = config.copy(enableAnimations = it); AosManager.saveConfig(context, config) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(0.4f)))
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { config = config.copy(useCustomConstructor = false); AosManager.saveConfig(context, config) }, modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (!config.useCustomConstructor) accentColor.copy(0.15f) else Color.Transparent), border = BorderStroke(width = if (!config.useCustomConstructor) 2.dp else 1.dp, color = if (!config.useCustomConstructor) accentColor else accentColor.copy(0.5f)), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Palette, null, tint = accentColor, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Готовые темы", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 13.sp)
                }
                OutlinedButton(onClick = { config = config.copy(useCustomConstructor = true); AosManager.saveConfig(context, config) }, modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (config.useCustomConstructor) accentColor.copy(0.15f) else Color.Transparent), border = BorderStroke(width = if (config.useCustomConstructor) 2.dp else 1.dp, color = if (config.useCustomConstructor) accentColor else accentColor.copy(0.5f)), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.DesignServices, null, tint = accentColor, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Свой дизайн", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 13.sp)
                }
            }
        }

        item {
            if (config.customBgUri != null) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp))) {
                    AsyncImage(model = config.customBgUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.5f)))
                    Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Фон установлен", color = Color.White, fontWeight = FontWeight.Medium)
                        Row {
                            IconButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Default.Edit, null, tint = Color.White) }
                            IconButton(onClick = { config = config.copy(customBgUri = null); AosManager.saveConfig(context, config) }) { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252)) }
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth().height(56.dp), border = BorderStroke(1.dp, accentColor.copy(0.5f)), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Image, null, tint = accentColor); Spacer(Modifier.width(8.dp)); Text("Установить фон", color = ThemeManager.parseColor(theme.textPrimaryColor))
                }
            }
        }

        if (config.useCustomConstructor) {
            item {
                Button(onClick = { navController.navigate("aos_constructor") }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = accentColor), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.DesignServices, null); Spacer(Modifier.width(8.dp)); Text("ОТКРЫТЬ РЕДАКТОР", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        } else {
            item {
                // ИСПРАВЛЕНИЕ ЗДЕСЬ: Безопасный вылет за границы экрана без отрицательного паддинга
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .requiredWidth(configuration.screenWidthDp.dp)
                        .offset(x = (-16).dp)
                ) {
                    items(15) { index ->
                        val themeNum = index + 1
                        val isSelected = config.themeIndex == themeNum
                        Box(modifier = Modifier.width(120.dp).height(80.dp).clip(RoundedCornerShape(14.dp)).border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) accentColor else accentColor.copy(0.2f), shape = RoundedCornerShape(14.dp)).clickable { config = config.copy(themeIndex = themeNum); AosManager.saveConfig(context, config) }) {
                            Box(Modifier.fillMaxSize().background(Color.Black)) { AosThemeMiniaturesRenderer(themeNum, safeParseAosColor(config.hexColor)) }
                            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f)))))
                            Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                                Text(AOS_THEME_NAMES.getOrElse(index) { "Тема $themeNum" }, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("$themeNum", color = accentColor, fontSize = 8.sp)
                            }
                            if (isSelected) Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp).background(accentColor, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AOS_COLORS.forEach { hex ->
                    val isSelected = config.hexColor == hex
                    val col = safeParseAosColor(hex)
                    Box(modifier = Modifier.size(if (isSelected) 48.dp else 40.dp).clip(CircleShape).background(col).border(width = if (isSelected) 3.dp else 0.dp, color = if (isSelected) Color.White else Color.Transparent, shape = CircleShape).clickable { config = config.copy(hexColor = hex); AosManager.saveConfig(context, config) }) {
                        if (isSelected) Icon(Icons.Default.Check, null, tint = if (col.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.align(Alignment.Center).size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun AosThemeMiniaturesRenderer(themeIndex: Int, c: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height; val cx = w/2; val cy = h/2
        if (w <= 0 || h <= 0) return@Canvas
        when (themeIndex) {
            1 -> { drawRect(Brush.radialGradient(listOf(c.copy(0.4f), Color.Black))); drawRoundRect(Color.White.copy(0.8f), topLeft = Offset(w*0.3f, h*0.4f), size = Size(w*0.4f, h*0.1f), cornerRadius = CornerRadius(4f)); drawRoundRect(c.copy(0.8f), topLeft = Offset(w*0.35f, h*0.55f), size = Size(w*0.3f, h*0.08f), cornerRadius = CornerRadius(4f)) }
            2 -> { drawCircle(c.copy(0.3f), (h*0.3f).coerceAtLeast(1f), Offset(cx, cy), style = Stroke(2f)); drawCircle(c.copy(0.1f), (h*0.45f).coerceAtLeast(1f), Offset(cx, cy), style = Stroke(1f)); drawRoundRect(Color.White, topLeft = Offset(w*0.35f, cy-h*0.05f), size = Size(w*0.3f, h*0.1f), cornerRadius = CornerRadius(4f)) }
            3 -> { for(i in 0..4) drawLine(c.copy(0.2f), Offset(0f, h*i/4), Offset(w, h*i/4), 1f); drawRect(c, topLeft = Offset(w*0.1f, h*0.7f), size = Size(w*0.2f, h*0.08f)); drawRect(Color.White.copy(0.5f), topLeft = Offset(w*0.1f, h*0.8f), size = Size(w*0.15f, h*0.05f)) }
            4 -> { drawRect(Color.Black); drawRect(c.copy(0.5f), style = Stroke(2f)); for(i in 0..3) drawRect(if(i==0) c else Color.White.copy(0.4f), topLeft = Offset(w*0.1f, h*0.2f+i*h*0.15f), size = Size(w*0.2f+i*w*0.1f, h*0.05f)) }
            5 -> { drawCircle(c.copy(0.5f), (h*0.3f).coerceAtLeast(1f), Offset(cx, cy), style = Stroke(2f)); drawCircle(Color.White, 3f, Offset(cx, cy-h*0.3f)); drawCircle(c, 2f, Offset(cx+h*0.3f, cy)) }
            6 -> { drawRoundRect(Color.White.copy(0.1f), topLeft = Offset(w*0.1f, h*0.1f), size = Size(w*0.8f, h*0.35f), cornerRadius = CornerRadius(4f)); drawRoundRect(c.copy(0.3f), topLeft = Offset(w*0.1f, h*0.55f), size = Size(w*0.35f, h*0.35f), cornerRadius = CornerRadius(4f)); drawRoundRect(Color.White.copy(0.1f), topLeft = Offset(w*0.55f, h*0.55f), size = Size(w*0.35f, h*0.35f), cornerRadius = CornerRadius(4f)) }
            7 -> { drawCircle(c.copy(0.2f), (w*0.2f).coerceAtLeast(1f), Offset(w*0.3f, h*0.4f)); drawCircle(c.copy(0.1f), (w*0.3f).coerceAtLeast(1f), Offset(w*0.7f, h*0.7f)); drawRoundRect(Color.White, topLeft = Offset(cx-w*0.2f, cy-h*0.1f), size = Size(w*0.4f, h*0.2f), cornerRadius = CornerRadius(8f)) }
            8 -> { for(i in 0..6) drawLine(c.copy(0.2f), Offset(w*i/6, 0f), Offset(w*i/6, h), 2f); drawRect(Color.Black.copy(0.8f), topLeft = Offset(w*0.3f, h*0.3f), size = Size(w*0.4f, h*0.4f)); drawRect(c, topLeft = Offset(w*0.35f, h*0.45f), size = Size(w*0.3f, h*0.1f)) }
            9 -> { val bw = w/7f; for(i in 0..6) drawRect(c.copy(0.2f + (i%3)*0.1f), topLeft = Offset(i*bw, h*0.2f), size = Size(bw-2f, h*0.6f)); drawRoundRect(Color.White, topLeft = Offset(w*0.6f, cy-h*0.1f), size = Size(w*0.3f, h*0.2f), cornerRadius = CornerRadius(4f)) }
            10 -> { drawCircle(c.copy(0.1f), (w*0.4f).coerceAtLeast(1f), Offset(cx, cy)); drawRect(Color.White, topLeft = Offset(w*0.1f, h*0.7f), size = Size(w*0.3f, h*0.1f)); drawRect(c, topLeft = Offset(w*0.1f, h*0.85f), size = Size(w*0.2f, h*0.05f)) }
            11 -> { drawCircle(c, (h*0.3f).coerceAtLeast(1f), Offset(cx, cy), style = Stroke(4f)); drawCircle(c.copy(0.3f), (h*0.2f).coerceAtLeast(1f), Offset(cx, cy), style = Stroke(2f)); drawCircle(Color.White, 4f, Offset(cx, cy)) }
            12 -> { drawLine(c.copy(0.3f), Offset(0f, cy), Offset(w, cy), 1f); drawLine(c.copy(0.3f), Offset(cx, 0f), Offset(cx, h), 1f); drawCircle(c.copy(0.2f), (h*0.2f).coerceAtLeast(1f), Offset(cx, cy), style = Stroke(1f)) }
            13 -> { drawRect(Brush.radialGradient(listOf(Color(0xFFFF4500).copy(0.5f), Color.Transparent), center = Offset(cx, h), radius = (w*0.6f).coerceAtLeast(1f))); drawRoundRect(Color(0xFFFFAB00), topLeft = Offset(w*0.35f, cy), size = Size(w*0.3f, h*0.1f), cornerRadius = CornerRadius(4f)) }
            14 -> { drawRect(Brush.verticalGradient(listOf(Color(0xFF00B0FF).copy(0.3f), Color.Transparent))); drawLine(Color.White, Offset(w*0.3f, cy), Offset(w*0.7f, cy), 4f); drawCircle(Color(0xFF00B0FF), 10f, Offset(w*0.8f, h*0.8f)) }
            15 -> { drawLine(c.copy(0.5f), Offset(w*0.2f, cy), Offset(w, 0f), 4f); drawLine(c.copy(0.3f), Offset(w*0.2f, cy), Offset(w, h), 6f); drawCircle(Color.White, 6f, Offset(w*0.2f, cy)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AosConstructorScreen(navController: NavController, theme: AppTheme) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(AosManager.getConfig(context)) }

    val activity = context as? Activity
    DisposableEffect(config.isLandscape) {
        activity?.requestedOrientation = if (config.isLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { config = config.copy(customBgUri = uri.toString()); AosManager.saveConfig(context, config) }
    }

    var selectedWidgetId by remember { mutableStateOf<String?>(null) }
    var showWidgetPanel by remember { mutableStateOf(true) }
    val tint = Color(android.graphics.Color.parseColor(config.hexColor))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Конструктор", color = Color.White, fontWeight = FontWeight.Bold); Text("Двумя пальцами — масштаб", color = Color.Gray, fontSize = 10.sp) } },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                actions = {
                    IconButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Default.Image, null, tint = Color.White) }
                    IconButton(onClick = { config = config.copy(widgets = emptyList()); AosManager.saveConfig(context, config) }) { Icon(Icons.Default.DeleteSweep, null, tint = Color(0xFFFF5252)) }
                    IconButton(onClick = { showWidgetPanel = !showWidgetPanel }) { Icon(Icons.Default.Widgets, null, tint = if (showWidgetPanel) tint else Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF111111))
            )
        }
    ) { padding ->
        val density = LocalDensity.current.density
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080808)).padding(padding)) {

            // Враппер гарантирует нам логическое поле 1920x1080 (или 1080x1920)
            AosCanvasWrapper(isLandscape = config.isLandscape) { globalScale ->
                Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { selectedWidgetId = null } }) {
                    if (config.customBgUri != null) {
                        AsyncImage(model = config.customBgUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)))
                    }
                    // Всегда рисуем сетку поверх
                    Canvas(Modifier.fillMaxSize()) {
                        val step = 60.dp.toPx()
                        for (x in 0..size.width.toInt() step step.toInt()) drawLine(Color.White.copy(0.06f), Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), 2f)
                        for (y in 0..size.height.toInt() step step.toInt()) drawLine(Color.White.copy(0.06f), Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), 2f)
                        drawLine(Color.White.copy(0.2f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2f)
                        drawLine(Color.White.copy(0.2f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
                    }

                    val dummyData = AosThemeData("12:34", "13 Мая 2026", "15 000 ₽", "12 500 ₽", "2 500 ₽", 5, true, 87, null, "Seller")

                    config.widgets.sortedBy { it.id == selectedWidgetId }.forEach { widget ->
                        val isSelected = selectedWidgetId == widget.id

                        Box(
                            modifier = Modifier
                                .offset(x = widget.xDp.dp, y = widget.yDp.dp)
                                .graphicsLayer { scaleX = widget.scale; scaleY = widget.scale; transformOrigin = TransformOrigin.Center }
                                .pointerInput(widget.id) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (widget.scale * zoom).coerceIn(0.3f, 6f)
                                        // pan приходит в экранных пикселях. Делим на глобальный скейл и плотность, чтобы получить dp холста
                                        val dx = pan.x / (globalScale * density) / widget.scale
                                        val dy = pan.y / (globalScale * density) / widget.scale
                                        config = config.copy(widgets = config.widgets.map {
                                            if (it.id == widget.id) it.copy(scale = newScale, xDp = it.xDp + dx, yDp = it.yDp + dy) else it
                                        })
                                    }
                                }
                                .pointerInput(widget.id) { detectTapGestures(onPress = { selectedWidgetId = widget.id }) }
                                .background(if (isSelected) tint.copy(0.15f) else Color.Transparent, RoundedCornerShape(10.dp))
                                .border(if (isSelected) 2.dp else 0.dp, tint, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            AosWidgetRealDataRenderer(widget.type, tint, dummyData)
                        }

                        // Бейдж масштаба рисуется вне graphicsLayer, чтобы не улетал
                        if (isSelected) {
                            Box(modifier = Modifier.offset(x = widget.xDp.dp, y = (widget.yDp - 20).dp).background(tint, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("${"%.1f".format(widget.scale)}x", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (config.widgets.isEmpty()) {
                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Widgets, null, tint = Color.White.copy(0.2f), modifier = Modifier.size(64.dp))
                            Text("Добавьте виджеты", color = Color.White.copy(0.3f), fontSize = 16.sp)
                        }
                    }
                }
            }
            AnimatedVisibility(visible = showWidgetPanel, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(if (config.isLandscape) Alignment.CenterEnd else Alignment.BottomCenter)) {
                if (config.isLandscape) {
                    Column(modifier = Modifier.fillMaxHeight().width(180.dp).background(Color(0xFF111111).copy(0.95f)).windowInsetsPadding(WindowInsets.navigationBars).padding(8.dp)) {
                        Text("Виджеты:", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            items(AosWidgetType.values().toList()) { wType ->
                                OutlinedButton(onClick = { config = config.copy(widgets = config.widgets + AosWidget(type = wType, xDp = 400f, yDp = 200f)); AosManager.saveConfig(context, config) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF1A1A1A)), border = BorderStroke(1.dp, tint.copy(0.5f)), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                                    Icon(getWidgetIcon(wType), null, tint = tint, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(wType.title, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF111111).copy(0.95f)).windowInsetsPadding(WindowInsets.navigationBars).padding(vertical = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AosWidgetType.values().forEach { wType ->
                                OutlinedButton(onClick = { config = config.copy(widgets = config.widgets + AosWidget(type = wType, xDp = 100f, yDp = 400f)); AosManager.saveConfig(context, config) }, colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF1A1A1A)), border = BorderStroke(1.dp, tint.copy(0.5f)), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                                    Icon(getWidgetIcon(wType), null, tint = tint, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(wType.title, color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            // Если выбран виджет, показываем кнопку удаления в углу
            if (selectedWidgetId != null) {
                Box(modifier = Modifier.align(if (config.isLandscape) Alignment.BottomStart else Alignment.TopEnd).padding(16.dp)) {
                    IconButton(onClick = { config = config.copy(widgets = config.widgets.filter { it.id != selectedWidgetId }); AosManager.saveConfig(context, config); selectedWidgetId = null }, modifier = Modifier.background(Color(0xFFFF5252).copy(0.8f), CircleShape)) { Icon(Icons.Default.Delete, null, tint = Color.White) }
                }
            }
        }
    }
}

fun getWidgetIcon(type: AosWidgetType) = when (type) {
    AosWidgetType.TIME -> Icons.Default.Schedule; AosWidgetType.DATE -> Icons.Default.CalendarToday; AosWidgetType.BALANCE -> Icons.Default.AccountBalanceWallet
    AosWidgetType.ACTIVE_BALANCE -> Icons.Default.AttachMoney; AosWidgetType.FROZEN_BALANCE -> Icons.Default.AcUnit; AosWidgetType.UNREAD -> Icons.Default.Chat
    AosWidgetType.SALES -> Icons.Default.ShoppingCart; AosWidgetType.ONLINE -> Icons.Default.Circle; AosWidgetType.AVATAR -> Icons.Default.Person
}

@Composable
fun AosWidgetRealDataRenderer(type: AosWidgetType, color: Color, data: AosThemeData) {
    when (type) {
        AosWidgetType.TIME -> Text(data.time, color = color, fontSize = 48.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"))
        AosWidgetType.DATE -> Text(data.date, color = color.copy(0.85f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
        AosWidgetType.BALANCE -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("БАЛАНС", color = Color.Gray, fontSize = 9.sp, letterSpacing = 1.sp); Text(data.balanceAll, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        AosWidgetType.ACTIVE_BALANCE -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("ДОСТУПНО", color = Color.Gray, fontSize = 9.sp, letterSpacing = 1.sp); Text(data.balanceActive, color = Color(0xFF4CAF50), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        AosWidgetType.FROZEN_BALANCE -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("В ХОЛДЕ", color = Color.Gray, fontSize = 9.sp, letterSpacing = 1.sp); Text(data.balanceFrozen, color = Color(0xFFFFA000), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        AosWidgetType.UNREAD -> Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(40.dp).background(if (data.unreadCount > 0) color.copy(0.2f) else Color.Gray.copy(0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.ChatBubble, null, tint = if (data.unreadCount > 0) color else Color.Gray, modifier = Modifier.size(20.dp)) }; Spacer(Modifier.width(12.dp)); Column { Text("${data.unreadCount}", color = if (data.unreadCount > 0) color else Color.Gray, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("сообщений", color = Color.Gray, fontSize = 11.sp) } }
        AosWidgetType.SALES -> Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(40.dp).background(color.copy(0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.ShoppingCart, null, tint = color, modifier = Modifier.size(20.dp)) }; Spacer(Modifier.width(12.dp)); Column { Text("${data.salesCount}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("продаж", color = color, fontSize = 11.sp) } }
        AosWidgetType.ONLINE -> Row(verticalAlignment = Alignment.CenterVertically) { val statusColor = if (data.online) Color(0xFF00E676) else Color(0xFFFF5252); Box(modifier = Modifier.size(14.dp).background(statusColor, CircleShape)); Spacer(Modifier.width(10.dp)); Text(if (data.online) "ONLINE" else "OFFLINE", color = statusColor, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 2.sp) }
        AosWidgetType.AVATAR -> {
            if (data.avatarUrl != null) AsyncImage(model = data.avatarUrl, contentDescription = null, modifier = Modifier.size(72.dp).clip(CircleShape).border(2.dp, color, CircleShape), contentScale = ContentScale.Crop)
            else Box(modifier = Modifier.size(72.dp).background(Brush.radialGradient(listOf(color.copy(0.4f), color.copy(0.1f))), CircleShape).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) { Text(data.username.take(1).uppercase().ifBlank { "?" }, color = color, fontSize = 28.sp, fontWeight = FontWeight.Black) }
        }
    }
}

// =========================================================================================
// 4. LIVE ЭКРАН (ИДЕАЛЬНАЯ ЗАЩИТА ОТ ВЫГОРАНИЯ И АВТОМАТИЗАЦИЯ В ФОНЕ)
// =========================================================================================

@Composable
fun AosDisplayScreen(navController: NavController, repository: FunPayRepository, theme: AppTheme) {
    val context = LocalContext.current
    val activity = context as? Activity
    val config = remember { AosManager.getConfig(context) }
    val mainColor = Color(android.graphics.Color.parseColor(config.hexColor))

    DisposableEffect(config.isLandscape) {
        activity?.requestedOrientation = if (config.isLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val windowInsetsController = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            windowInsetsController?.show(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        }
    }

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var activeSum by remember { mutableDoubleStateOf(0.0) }
    var frozenSum by remember { mutableDoubleStateOf(0.0) }
    var unread by remember { mutableIntStateOf(0) }
    var salesCount by remember { mutableIntStateOf(0) }

    // Плавная защита от выгорания
    val density = LocalDensity.current.density
    var burnInTargetX by remember { mutableFloatStateOf(0f) }
    var burnInTargetY by remember { mutableFloatStateOf(0f) }
    var lastMinute by remember { mutableIntStateOf(-1) }

    val smoothOffsetX by animateFloatAsState(targetValue = burnInTargetX, animationSpec = tween(3000, easing = LinearOutSlowInEasing), label = "bx")
    val smoothOffsetY by animateFloatAsState(targetValue = burnInTargetY, animationSpec = tween(3000, easing = LinearOutSlowInEasing), label = "by")
    LaunchedEffect(Unit) {
        while (true) {
            val date = Date()
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            currentDate = SimpleDateFormat("dd MMMM yyyy", Locale("ru")).format(date)
            if (date.minutes != lastMinute) {
                lastMinute = date.minutes
                burnInTargetX = (-8..8).random().toFloat() * density
                burnInTargetY = (-8..8).random().toFloat() * density
            }
            delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        // Загружаем продажи полностью в фоне если ещё не загружены
        withContext(Dispatchers.IO) {
            if (!repository.isSalesFullLoaded) {
                try {
                    var token: String? = null
                    val buf = repository.cachedSales.toMutableList()
                    while (true) {
                        val result = repository.fetchSalesPage(token) ?: break
                        val page = result.first
                        val next = result.second
                        val existingIds = buf.map { it.orderId }.toSet()
                        val newOrders = page.filter { it.orderId !in existingIds }
                        buf.addAll(newOrders)
                        repository.cachedSales = buf.toList()
                        token = next
                        if (next == null) {
                            repository.isSalesFullLoaded = true
                            break
                        }
                        kotlinx.coroutines.delay(400)
                    }
                } catch (e: Exception) { }
            }
        }

        while (true) {
            try {
                profile = repository.getSelfProfileUpdated()
                unread = repository.getChats().count { it.isUnread }
                val sales = repository.cachedSales
                salesCount = sales.size
                val currentMs = System.currentTimeMillis()
                val frozen = sales.filter { it.status == "closed" }.mapNotNull { sale ->
                    val saleTime = parseFunPayDateToMsLocal(sale.date)
                    if (saleTime > 0) {
                        val unlockTime = saleTime + 48L * 3600L * 1000L
                        if (currentMs < unlockTime) sale else null
                    } else null
                }.sumOf { it.priceValue }
                frozenSum = frozen
                val total = profile?.totalBalance?.replace(Regex("[^0-9.,]"), "")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                activeSum = maxOf(0.0, total - frozen)
            } catch (e: Exception) { }
            delay(30000)
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { if (!repository.isSalesFullLoaded && repository.cachedSales.isEmpty()) { try { val res = repository.fetchSalesPage(null); if (res != null) repository.cachedSales = res.first } catch (e: Exception) {} } }
        while (true) {
            try {
                profile = repository.getSelfProfileUpdated(); unread = repository.getChats().count { it.isUnread }; val sales = repository.cachedSales; salesCount = sales.size
                val currentMs = System.currentTimeMillis()
                val frozen = sales.filter { it.status == "closed" }.mapNotNull { sale ->
                    val saleTime = parseFunPayDateToMsLocal(sale.date); if (saleTime > 0) { val unlockTime = saleTime + 48L * 3600L * 1000L; if (currentMs < unlockTime) sale else null } else null
                }.sumOf { it.priceValue }
                frozenSum = frozen; val total = profile?.totalBalance?.replace(Regex("[^0-9.,]"), "")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0; activeSum = max(0.0, total - frozen)
            } catch (e: Exception) { }
            delay(30000)
        }
    }

    val liveData = AosThemeData(
        time = currentTime, date = currentDate, balanceAll = profile?.totalBalance ?: "0.00 ₽", balanceActive = String.format(Locale.US, "%.2f ₽", activeSum),
        balanceFrozen = String.format(Locale.US, "%.2f ₽", frozenSum), unreadCount = unread, online = profile?.isOnline ?: false, salesCount = salesCount, avatarUrl = profile?.avatarUrl, username = profile?.username ?: "Продавец"
    )

    var showExitHint by remember { mutableStateOf(false) }
    LaunchedEffect(showExitHint) { if (showExitHint) { delay(2500); showExitHint = false } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures(onDoubleTap = { navController.popBackStack() }, onTap = { showExitHint = true }) }) {

        // Корневой Box увеличен на 1.05, чтобы при смещении не обнажались черные края
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = smoothOffsetX; translationY = smoothOffsetY; scaleX = 1.05f; scaleY = 1.05f }) {
            AosCanvasWrapper(isLandscape = config.isLandscape) { _ ->
                if (config.useCustomConstructor) {
                    if (config.customBgUri != null) { AsyncImage(model = config.customBgUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop); Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f))) }
                    config.widgets.forEach { w ->
                        Box(Modifier.offset(x = w.xDp.dp, y = w.yDp.dp).graphicsLayer { scaleX = w.scale; scaleY = w.scale; transformOrigin = TransformOrigin(0f,0f) }) { AosWidgetRealDataRenderer(w.type, mainColor, liveData) }
                    }
                } else {
                    RenderAosTheme(config.themeIndex, mainColor, liveData, config.enableAnimations, config.customBgUri, config.isLandscape)
                }
            }
        }

        AnimatedVisibility(visible = showExitHint, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.Center)) {
            Box(modifier = Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E))), RoundedCornerShape(20.dp)).border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(20.dp)).padding(horizontal = 28.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.TouchApp, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Двойное нажатие — выход", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

private fun parseFunPayDateToMsLocal(dateStr: String): Long {
    try {
        val now = Calendar.getInstance(); val str = dateStr.lowercase().trim(); val timePart = Regex("(\\d{1,2}):(\\d{2})").find(str); val h = timePart?.groupValues?.get(1)?.toIntOrNull() ?: 0; val m = timePart?.groupValues?.get(2)?.toIntOrNull() ?: 0
        if (str.contains("сегодня")) { now.set(Calendar.HOUR_OF_DAY, h); now.set(Calendar.MINUTE, m); now.set(Calendar.SECOND, 0); return now.timeInMillis }
        if (str.contains("вчера")) { now.add(Calendar.DAY_OF_YEAR, -1); now.set(Calendar.HOUR_OF_DAY, h); now.set(Calendar.MINUTE, m); now.set(Calendar.SECOND, 0); return now.timeInMillis }
        val months = listOf("янв","фев","мар","апр","мая","июн","июл","авг","сен","окт","ноя","дек"); var monthIdx = -1; for (i in months.indices) { if (str.contains(months[i])) { monthIdx = i; break } }
        val dayMatch = Regex("(\\d{1,2})\\s+[а-я]+").find(str); val d = dayMatch?.groupValues?.get(1)?.toIntOrNull() ?: now.get(Calendar.DAY_OF_MONTH); val yearMatch = Regex("(\\d{4})").find(str); val y = yearMatch?.groupValues?.get(1)?.toIntOrNull() ?: now.get(Calendar.YEAR)
        if (monthIdx != -1) { val cal = Calendar.getInstance(); cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, monthIdx); cal.set(Calendar.DAY_OF_MONTH, d); cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m); cal.set(Calendar.SECOND, 0); return cal.timeInMillis }
    } catch (e: Exception) { }
    return 0L
}

// =========================================================================================
// АНИМАЦИОННЫЕ ХЕЛПЕРЫ (Возвращают State)
// =========================================================================================

@Composable fun breathingScaleState(animate: Boolean, from: Float = 1f, to: Float = 1.06f, durationMs: Int = 2000): State<Float> { val t = rememberInfiniteTransition(label = "breathing"); return if (animate) t.animateFloat(from, to, infiniteRepeatable(tween(durationMs, easing = EaseInOutSine), RepeatMode.Reverse), label = "bs") else rememberUpdatedState(from) }
@Composable fun pulseAlphaState(animate: Boolean, from: Float = 0.4f, to: Float = 1f, durationMs: Int = 1400): State<Float> { val t = rememberInfiniteTransition(label = "pulse"); return if (animate) t.animateFloat(from, to, infiniteRepeatable(tween(durationMs, easing = EaseInOut), RepeatMode.Reverse), label = "pa") else rememberUpdatedState(to) }
@Composable fun rotationAngleState(animate: Boolean, durationMs: Int = 8000): State<Float> { val t = rememberInfiniteTransition(label = "rotation"); return if (animate) t.animateFloat(0f, 360f, infiniteRepeatable(tween(durationMs, easing = LinearEasing)), label = "ra") else rememberUpdatedState(0f) }
@Composable fun infiniteScrollState(animate: Boolean, durationMs: Int = 20000): State<Float> { val t = rememberInfiniteTransition(label = "scroll"); return if (animate) t.animateFloat(0f, 100f, infiniteRepeatable(tween(durationMs, easing = LinearEasing)), label = "sc") else rememberUpdatedState(0f) }

// =========================================================================================
// 5. АДАПТИВНЫЕ ТЕМЫ AOD (1-15) - ИДЕАЛЬНАЯ ВЕРСТКА В ОБЕИХ ОРИЕНТАЦИЯХ
// =========================================================================================

@Composable
fun RenderAosTheme(index: Int, color: Color, data: AosThemeData, animate: Boolean, bgUri: String?, isLandscape: Boolean) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (bgUri != null) { AsyncImage(model = bgUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop); Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f))) }
        when (index) {
            1 -> AosTheme1_Aurora(color, data, animate, isLandscape)
            2 -> AosTheme2_NeonPulse(color, data, animate, isLandscape)
            3 -> AosTheme3_Hologram(color, data, animate, isLandscape)
            4 -> AosTheme4_Terminal(color, data, animate, isLandscape)
            5 -> AosTheme5_Orbital(color, data, animate, isLandscape)
            6 -> AosTheme6_Bento(color, data, animate, isLandscape)
            7 -> AosTheme7_Liquid(color, data, animate, isLandscape)
            8 -> AosTheme8_Cipher(color, data, animate, isLandscape)
            9 -> AosTheme9_Spectrum(color, data, animate, isLandscape)
            10 -> AosTheme10_Depth(color, data, animate, isLandscape)
            11 -> AosTheme11_Plasma(color, data, animate, isLandscape)
            12 -> AosTheme12_Zen(color, data, animate, isLandscape)
            13 -> AosTheme13_Inferno(color, data, animate, isLandscape)
            14 -> AosTheme14_Arctic(color, data, animate, isLandscape)
            15 -> AosTheme15_Prism(color, data, animate, isLandscape)
            else -> AosTheme1_Aurora(color, data, animate, isLandscape)
        }
    }
}

@Composable
fun AosTheme1_Aurora(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val breathe = breathingScaleState(animate, 1f, 1.04f)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height * 0.3f
            if (size.width > 0) {
                drawRect(Brush.radialGradient(listOf(color.copy(0.25f), Color.Transparent), center = Offset(cx - size.width * 0.2f, cy), radius = (size.width * 0.8f).coerceAtLeast(1f)))
                drawRect(Brush.radialGradient(listOf(color.copy(0.15f), Color.Transparent), center = Offset(cx + size.width * 0.3f, cy + 80f), radius = (size.width * 0.6f).coerceAtLeast(1f)))
            }
            for (i in 0..4) { val y = size.height * (0.1f + i * 0.07f); drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color.copy(0.06f + i * 0.01f), Color.Transparent)), topLeft = Offset(0f, y), size = Size(size.width, 30f)) }
        }
        // ... остальной код темы остается таким же ...
        val pad = if (isLandscape) 48.dp else 24.dp
        if (isLandscape) {
            Column(modifier = Modifier.align(Alignment.CenterStart).padding(start = pad), horizontalAlignment = Alignment.CenterHorizontally) { AuroraProfile(data, color, 80.dp) }
            Column(modifier = Modifier.align(Alignment.Center).graphicsLayer { scaleX = breathe.value; scaleY = breathe.value }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(data.time, style = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum", brush = Brush.verticalGradient(listOf(Color.White, color.copy(0.8f)))))
                Text(data.date.uppercase(), color = color.copy(0.9f), fontSize = 16.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Light)
            }
            Column(modifier = Modifier.align(Alignment.CenterEnd).padding(end = pad), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.End) {
                AuroraStatCard("ДОСТУПНО", data.balanceActive, color); AuroraStatCard("В ХОЛДЕ", data.balanceFrozen, Color(0xFFFFA000))
                AuroraStatCard("ЧАТЫ", "${data.unreadCount}", if (data.unreadCount > 0) color else Color.Gray); AuroraStatCard("ПРОДАЖИ", "${data.salesCount}", Color.White)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(vertical = pad, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                AuroraProfile(data, color, 64.dp)
                Column(modifier = Modifier.graphicsLayer { scaleX = breathe.value; scaleY = breathe.value }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(data.time, style = TextStyle(fontSize = 72.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum", brush = Brush.verticalGradient(listOf(Color.White, color.copy(0.8f)))))
                    Text(data.date.uppercase(), color = color.copy(0.9f), fontSize = 14.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Light)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { AuroraStatCard("ДОСТУПНО", data.balanceActive, color); AuroraStatCard("ЧАТЫ", "${data.unreadCount}", if (data.unreadCount > 0) color else Color.Gray) }
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.End) { AuroraStatCard("В ХОЛДЕ", data.balanceFrozen, Color(0xFFFFA000)); AuroraStatCard("ПРОДАЖИ", "${data.salesCount}", Color.White) }
                }
            }
        }
    }
}

@Composable fun AuroraProfile(data: AosThemeData, color: Color, size: Dp) {
    if (data.avatarUrl != null) { Box { AsyncImage(model = data.avatarUrl, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape).border(2.dp, Brush.sweepGradient(listOf(color, color.copy(0.3f), color)), CircleShape), contentScale = ContentScale.Crop); Box(modifier = Modifier.size(size*0.25f).align(Alignment.BottomEnd).background(Color.Black, CircleShape).padding(2.dp)) { Box(Modifier.fillMaxSize().background(if (data.online) Color(0xFF00E676) else Color(0xFFFF5252), CircleShape)) } } }
    else { Box(modifier = Modifier.size(size).background(Brush.radialGradient(listOf(color.copy(0.5f), color.copy(0.1f))), CircleShape).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) { Text(data.username.take(1).uppercase().ifBlank { "?" }, color = Color.White, fontSize = size.value.sp*0.4f, fontWeight = FontWeight.Black) } }
    Spacer(Modifier.height(8.dp)); Text(data.username, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}
@Composable fun AuroraStatCard(label: String, value: String, color: Color) { Box(modifier = Modifier.background(Brush.horizontalGradient(listOf(color.copy(0.15f), Color.Transparent)), RoundedCornerShape(12.dp)).border(1.dp, Brush.horizontalGradient(listOf(color.copy(0.5f), Color.Transparent)), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(label, color = Color.Gray, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(2.dp)); Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold) } } }

// ---------------------------------------------------------
// Theme 2: Neon Pulse
// ---------------------------------------------------------
@Composable
fun AosTheme2_NeonPulse(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val pulse = pulseAlphaState(animate, 0.3f, 1f, 800)
    val pulse2 = pulseAlphaState(animate, 0.5f, 0.9f, 1200)
    val rotation = rotationAngleState(animate, 6000)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2
            val maxR = minOf(size.width, size.height) * (if (isLandscape) 0.46f else 0.35f)
            for (i in 0..3) { drawCircle(color.copy(0.15f + i * 0.05f), maxR - i * 18.dp.toPx(), Offset(cx, cy), style = Stroke(2f)) }
            rotate(rotation.value, Offset(cx, cy)) { drawArc(brush = Brush.sweepGradient(listOf(Color.Transparent, color.copy(0.6f), color, Color.Transparent), center = Offset(cx, cy)), startAngle = 0f, sweepAngle = 120f, useCenter = false, topLeft = Offset(cx - maxR, cy - maxR), size = Size(maxR * 2, maxR * 2), style = Stroke(4f)) }
            for (i in 0..7) { val angle = Math.toRadians(i * 45.0 + rotation.value * 0.1); drawCircle(color.copy(0.6f), 4f, Offset(cx + cos(angle).toFloat() * maxR, cy + sin(angle).toFloat() * maxR)) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (data.avatarUrl != null) { AsyncImage(model = data.avatarUrl, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape).border(2.dp, color, CircleShape), contentScale = ContentScale.Crop); Spacer(Modifier.height(12.dp)) }
            Text(data.time, color = Color.White, fontSize = if (isLandscape) 72.sp else 56.sp, fontWeight = FontWeight.Black, style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.graphicsLayer { alpha = pulse2.value })
            Text(data.date, color = color, fontSize = 14.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(24.dp))
            if (isLandscape) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    NeonStat("ДОСТУПНО", data.balanceActive, color); Box(Modifier.width(1.dp).height(36.dp).background(color.copy(0.3f)))
                    NeonStat("ЧАТЫ", "${data.unreadCount}", if (data.unreadCount > 0) color else Color.Gray); Box(Modifier.width(1.dp).height(36.dp).background(color.copy(0.3f)))
                    NeonStat("ПРОДАЖИ", "${data.salesCount}", Color.White)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NeonStat("ДОСТУПНО", data.balanceActive, color)
                    NeonStat("ЧАТЫ", "${data.unreadCount}", if (data.unreadCount > 0) color else Color.Gray)
                    NeonStat("ПРОДАЖИ", "${data.salesCount}", Color.White)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(10.dp).graphicsLayer { alpha = pulse.value }.background(if (data.online) Color(0xFF00E676) else Color(0xFFFF5252), CircleShape)); Spacer(Modifier.width(8.dp)); Text(if (data.online) "ONLINE" else "OFFLINE", color = if (data.online) Color(0xFF00E676) else Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp) }
        }
    }
}
@Composable fun NeonStat(label: String, value: String, color: Color) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(label, fontSize = 10.sp, color = color.copy(0.8f), fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) } }

// ---------------------------------------------------------
// Theme 3: Hologram
// ---------------------------------------------------------
@Composable
fun AosTheme3_Hologram(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val alpha = pulseAlphaState(animate, 0.6f, 1f, 2000)
    val scanLine = rememberInfiniteTransition(label = "scan").animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse), label = "sl")
    val pad = if (isLandscape) 48.dp else 24.dp

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0..50) drawLine(color.copy(0.03f), Offset(0f, size.height * i / 50f), Offset(size.width, size.height * i / 50f), 1f)
            if (animate) { val sy = size.height * scanLine.value; drawRect(Brush.verticalGradient(listOf(Color.Transparent, color.copy(0.15f), Color.Transparent), startY = sy - 30f, endY = sy + 30f)) }
            val m = pad.toPx() / 2f; val l = 30.dp.toPx(); val c = color.copy(alpha.value)
            val path = Path().apply {
                moveTo(m + l, m); lineTo(m, m); lineTo(m, m + l)
                moveTo(size.width - m - l, m); lineTo(size.width - m, m); lineTo(size.width - m, m + l)
                moveTo(m + l, size.height - m); lineTo(m, size.height - m); lineTo(m, size.height - m - l)
                moveTo(size.width - m - l, size.height - m); lineTo(size.width - m, size.height - m); lineTo(size.width - m, size.height - m - l)
            }
            drawPath(path, c, style = Stroke(width = 3f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
            val cx = size.width / 2; val cy = size.height / 2
            drawCircle(color.copy(0.2f), 4f, Offset(cx, cy)); drawLine(color.copy(0.2f), Offset(cx - 20f, cy), Offset(cx + 20f, cy), 1f); drawLine(color.copy(0.2f), Offset(cx, cy - 20f), Offset(cx, cy + 20f), 1f)
        }

        Column(modifier = Modifier.align(Alignment.TopStart).padding(pad).graphicsLayer { this.alpha = alpha.value }) {
            Text("◈ TIME", color = color.copy(0.6f), fontSize = 10.sp, letterSpacing = 3.sp, fontFamily = FontFamily.Monospace)
            Text(data.time, color = Color.White, fontSize = if (isLandscape) 64.sp else 48.sp, fontWeight = FontWeight.ExtraLight, style = TextStyle(fontFeatureSettings = "tnum"))
            Text(data.date.uppercase(), color = color, fontSize = 12.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
        }

        Row(modifier = Modifier.align(Alignment.TopEnd).padding(pad).graphicsLayer { this.alpha = alpha.value }, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) { Text(data.username.uppercase(), color = Color.White, fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold); Text("◈ ${if (data.online) "ONLINE" else "OFFLINE"}", color = if (data.online) Color(0xFF00E676) else Color(0xFFFF5252), fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
            Spacer(Modifier.width(12.dp))
            if (data.avatarUrl != null) AsyncImage(model = data.avatarUrl, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, color, RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            else Box(Modifier.size(36.dp).background(color.copy(0.15f), RoundedCornerShape(8.dp)).border(1.dp, color, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text(data.username.take(1).uppercase().ifBlank{"?"}, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }

        if (isLandscape) {
            Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = pad).graphicsLayer { this.alpha = alpha.value }, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                HudDataBlock("ДОСТУПНО", data.balanceActive, color); HudDataBlock("ЗАМОРОЖЕНО", data.balanceFrozen, Color(0xFFFFA000))
                HudDataBlock("СООБЩЕНИЯ", "${data.unreadCount}", if (data.unreadCount > 0) color else Color.Gray); HudDataBlock("ПРОДАЖИ", "${data.salesCount}", Color.White)
            }
        } else {
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(pad).graphicsLayer { this.alpha = alpha.value }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HudDataBlock("ДОСТУПНО", data.balanceActive, color); HudDataBlock("ЗАМОРОЖЕНО", data.balanceFrozen, Color(0xFFFFA000))
            }
            Column(modifier = Modifier.align(Alignment.BottomEnd).padding(pad).graphicsLayer { this.alpha = alpha.value }, verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.End) {
                HudDataBlock("СООБЩЕНИЯ", "${data.unreadCount}", if (data.unreadCount > 0) color else Color.Gray)
                HudDataBlock("ПРОДАЖИ", "${data.salesCount}", Color.White)
            }
        }
    }
}
@Composable fun HudDataBlock(label: String, value: String, color: Color) { Column(horizontalAlignment = Alignment.End) { Box(Modifier.width(80.dp).height(1.dp).background(color.copy(0.4f))); Spacer(Modifier.height(4.dp)); Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace); Spacer(Modifier.height(2.dp)); Text(label, color = Color.Gray, fontSize = 9.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace); Box(Modifier.width(80.dp).height(1.dp).background(color.copy(0.4f))) } }

@Composable
fun AosTheme4_Terminal(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val alpha = pulseAlphaState(animate, 0.7f, 1f, 3000)
    val blink = pulseAlphaState(animate, 0f, 1f, 500)
    val scanLine = infiniteScrollState(animate, 6000)

    Box(Modifier.fillMaxSize().background(Color(0xFF050505))) {
        // Эффект ЭЛТ (старого монитора)
        Canvas(Modifier.fillMaxSize()) {
            val step = 4.dp.toPx()
            for (i in 0..(size.height / step).toInt()) {
                drawLine(Color.White.copy(0.03f), Offset(0f, i * step), Offset(size.width, i * step), 1f)
            }
            // Сканирующая линия
            if (animate) {
                val y = (scanLine.value / 100f) * size.height
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, color.copy(0.2f), color.copy(0.5f), Color.Transparent), startY = y - 40f, endY = y + 40f))
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp).border(1.dp, color.copy(0.3f), RoundedCornerShape(8.dp)).background(Color.Black.copy(0.4f), RoundedCornerShape(8.dp))) {
            // MacOS-подобный хедер окна
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(Color(0xFF151515), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).padding(12.dp)) {
                Box(Modifier.size(10.dp).background(Color(0xFFFF5F56), CircleShape)); Spacer(Modifier.width(8.dp))
                Box(Modifier.size(10.dp).background(Color(0xFFFFBD2E), CircleShape)); Spacer(Modifier.width(8.dp))
                Box(Modifier.size(10.dp).background(Color(0xFF27C93F), CircleShape)); Spacer(Modifier.width(16.dp))
                Text("root@funpay-os: ~", color = color.copy(0.8f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text(data.time, color = color, fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum", shadow = Shadow(color, blurRadius = 8f)), modifier = Modifier.graphicsLayer { this.alpha = alpha.value })
            }
            HorizontalDivider(color = color.copy(0.2f))

            Column(Modifier.padding(16.dp)) {
                Text("FunPay OS v2.0.26 (tty1)", color = color.copy(0.6f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))

                TermLine("> whoami", color.copy(0.5f)); TermLine(data.username, Color.White); Spacer(Modifier.height(8.dp))
                TermLine("> date --iso-8601", color.copy(0.5f)); TermLine(data.date, Color.White); Spacer(Modifier.height(8.dp))

                TermLine("> systemctl status funpay-wallet", color.copy(0.5f))
                TermLine("  [ACTIVE]   Available: ${data.balanceActive}", Color(0xFF69F0AE))
                TermLine("  [FROZEN]   Hold:      ${data.balanceFrozen}", Color(0xFFFFAB40)); Spacer(Modifier.height(8.dp))

                TermLine("> netstat -an | grep messages", color.copy(0.5f))
                TermLine("  UNREAD_MSG count:     ${data.unreadCount}", if (data.unreadCount > 0) color else Color.Gray); Spacer(Modifier.height(8.dp))

                TermLine("> df -h /sales", color.copy(0.5f))
                TermLine("  TOTAL_SALES:          ${data.salesCount} units", Color.White); Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("[root@funpay-os ~]# ", color = color.copy(0.8f), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    Text("█", color = color, fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.graphicsLayer { this.alpha = if (blink.value > 0.5f) 1f else 0f }, style = TextStyle(shadow = Shadow(color, blurRadius = 10f)))
                }
            }
        }
    }
}
// Если TermLine нет, добавьте эту строчку ниже:
@Composable fun TermLine(text: String, color: Color) { Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp, style = TextStyle(shadow = Shadow(color.copy(0.5f), blurRadius = 6f)), maxLines = 1, overflow = TextOverflow.Ellipsis) }

@Composable
fun AosTheme5_Orbital(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val orbit1 = rotationAngleState(animate, 20000)
    val orbit2 = rotationAngleState(animate, -15000)
    val orbit3 = rotationAngleState(animate, 30000)
    val pulse = pulseAlphaState(animate, 0.6f, 1f, 1000)

    Box(Modifier.fillMaxSize().background(Color(0xFF030305)), contentAlignment = Alignment.Center) {
        // Огромная аватарка в центре
        val avatarSize = if (isLandscape) 220.dp else 180.dp

        // Сложные орбиты на фоне
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2
            val r1 = avatarSize.toPx() / 1.8f
            val r2 = avatarSize.toPx() / 1.4f
            val r3 = avatarSize.toPx() / 1.1f

            rotate(orbit1.value, Offset(cx, cy)) {
                drawCircle(color.copy(0.3f), r1, Offset(cx, cy), style = Stroke(4f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 10f))))
                drawArc(color, startAngle = 0f, sweepAngle = 90f, useCenter = false, topLeft = Offset(cx-r1, cy-r1), size = Size(r1*2, r1*2), style = Stroke(6f))
            }
            rotate(orbit2.value, Offset(cx, cy)) {
                drawCircle(color.copy(0.1f), r2, Offset(cx, cy), style = Stroke(2f))
                drawArc(Color.White.copy(0.8f), startAngle = 180f, sweepAngle = 60f, useCenter = false, topLeft = Offset(cx-r2, cy-r2), size = Size(r2*2, r2*2), style = Stroke(4f, cap = StrokeCap.Round))
                drawCircle(Color.White, 8f, Offset(cx + r2, cy))
            }
            rotate(orbit3.value, Offset(cx, cy)) {
                drawCircle(color.copy(0.2f), r3, Offset(cx, cy), style = Stroke(1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 15f))))
                drawCircle(color, 6f, Offset(cx, cy - r3))
                drawCircle(Color(0xFFFFA000), 4f, Offset(cx, cy + r3))
            }
        }

        // Сама Аватарка
        Box(modifier = Modifier.size(avatarSize).clip(CircleShape).background(Color.Black).border(3.dp, color.copy(pulse.value), CircleShape), contentAlignment = Alignment.Center) {
            if (data.avatarUrl != null) {
                AsyncImage(model = data.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(data.username.take(1).uppercase(), color = color, fontSize = 80.sp, fontWeight = FontWeight.Black)
            }
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(0.6f)))))
        }

        // Данные раскиданы вокруг аватара
        if (isLandscape) {
            Column(Modifier.align(Alignment.CenterStart).padding(start = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(data.time, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black, style = TextStyle(fontFeatureSettings = "tnum", shadow = Shadow(color, blurRadius = 15f)))
                Text(data.date, color = color.copy(0.8f), fontSize = 14.sp, letterSpacing = 2.sp)
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.End) {
                OrbitalCard("БАЛАНС", data.balanceActive, color)
                OrbitalCard("В ХОЛДЕ", data.balanceFrozen, Color(0xFFFFA000))
                OrbitalCard("ЧАТЫ", "${data.unreadCount}", if(data.unreadCount>0) color else Color.White)
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(if(data.online) Color(0xFF00E676) else Color(0xFFFF5252), CircleShape).border(2.dp, Color.White, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(data.username.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            }
        } else {
            Column(Modifier.align(Alignment.TopCenter).padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(data.time, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black, style = TextStyle(fontFeatureSettings = "tnum", shadow = Shadow(color, blurRadius = 15f)))
                Text(data.date, color = color.copy(0.8f), fontSize = 14.sp, letterSpacing = 2.sp)
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                OrbitalCard("БАЛАНС", data.balanceActive, color)
                OrbitalCard("ЧАТЫ", "${data.unreadCount}", if(data.unreadCount>0) color else Color.White)
            }
        }
    }
}
@Composable fun OrbitalCard(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.background(color.copy(0.1f), RoundedCornerShape(12.dp)).border(1.dp, color.copy(0.4f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = Shadow(color, blurRadius = 8f)))
        Text(label, color = Color.White.copy(0.6f), fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun AosTheme6_Bento(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val pulse = pulseAlphaState(animate, 0.7f, 1f, 1800)
    val breathe = breathingScaleState(animate, 1f, 1.03f, 3000)

    // Считаем фракции для мини-баров
    val balanceValue = data.balanceActive.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
    val frozenValue  = data.balanceFrozen.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
    val totalValue   = (balanceValue + frozenValue).coerceAtLeast(1f)
    val activeFrac   = (balanceValue / totalValue).coerceIn(0f, 1f)
    val frozenFrac   = (frozenValue  / totalValue).coerceIn(0f, 1f)
    val salesFrac    = (data.salesCount.toFloat() / maxOf(data.salesCount.toFloat(), 200f)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070710))
    ) {
        // Фоновый градиент-туманность
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(color.copy(0.08f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.15f),
                    radius = size.width * 0.6f
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFFFF0080).copy(0.06f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.85f),
                    radius = size.width * 0.5f
                )
            )
        }

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ЛЕВАЯ КОЛОНКА: Время + дата + аватар
                Column(
                    modifier = Modifier.weight(1.1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Карточка времени
                    BentoGlassCard(
                        modifier = Modifier.weight(1.4f).fillMaxWidth(),
                        color = color
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Точки-индикатор онлайна
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (data.online) Color(0xFF00E676) else Color(0xFF555555),
                                            CircleShape
                                        )
                                        .graphicsLayer { alpha = if (data.online) pulse.value else 0.5f }
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .height(1.dp)
                                        .weight(1f)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(color.copy(0.4f), Color.Transparent)
                                            )
                                        )
                                )
                            }
                            // Время
                            Text(
                                data.time,
                                style = TextStyle(
                                    fontSize = 72.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFeatureSettings = "tnum",
                                    brush = Brush.verticalGradient(
                                        listOf(Color.White, color.copy(0.75f))
                                    )
                                ),
                                modifier = Modifier.graphicsLayer {
                                    scaleX = breathe.value; scaleY = breathe.value
                                }
                            )
                            // Дата
                            Text(
                                data.date.uppercase(),
                                color = color.copy(0.7f),
                                fontSize = 11.sp,
                                letterSpacing = 3.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Карточка аватара + имя
                    BentoGlassCard(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        color = Color.White.copy(0.08f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (data.avatarUrl != null) {
                                AsyncImage(
                                    model = data.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, color.copy(0.5f), CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            Brush.radialGradient(listOf(color.copy(0.5f), color.copy(0.15f))),
                                            CircleShape
                                        )
                                        .border(2.dp, color.copy(0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        data.username.take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Column {
                                Text(
                                    data.username,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (data.online) "ONLINE" else "OFFLINE",
                                    color = if (data.online) Color(0xFF00E676) else Color(0xFF555555),
                                    fontSize = 9.sp,
                                    letterSpacing = 2.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // СРЕДНЯЯ КОЛОНКА: Баланс + холд
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Доступный баланс
                    BentoGlassCard(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        color = Color(0xFF00E676).copy(0.15f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(Modifier.size(16.dp)) {
                                    drawCircle(Color(0xFF00E676).copy(0.3f))
                                    drawCircle(Color(0xFF00E676), radius = size.minDimension * 0.25f)
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier.height(1.dp).weight(1f)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF00E676).copy(0.4f), Color.Transparent)
                                            )
                                        )
                                )
                            }
                            Column {
                                Text(
                                    data.balanceActive,
                                    color = Color(0xFF00E676),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Бар доступного баланса
                                BentoProgressBar(fraction = activeFrac, color = Color(0xFF00E676))
                            }
                        }
                    }

                    // Холд
                    BentoGlassCard(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        color = Color(0xFFFFA000).copy(0.12f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(Modifier.size(16.dp)) {
                                    drawCircle(Color(0xFFFFA000).copy(0.3f))
                                    drawCircle(Color(0xFFFFA000), radius = size.minDimension * 0.25f)
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier.height(1.dp).weight(1f)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFFFFA000).copy(0.4f), Color.Transparent)
                                            )
                                        )
                                )
                            }
                            Column {
                                Text(
                                    data.balanceFrozen,
                                    color = Color(0xFFFFA000),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                BentoProgressBar(fraction = frozenFrac, color = Color(0xFFFFA000))
                            }
                        }
                    }

                    // Общий баланс
                    BentoGlassCard(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        color = color.copy(0.1f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                data.balanceAll,
                                color = color,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            // Соотношение доступно/холд визуально
                            Row(
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                            ) {
                                Box(
                                    Modifier.weight(activeFrac.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(Color(0xFF00E676))
                                )
                                Box(
                                    Modifier.weight(frozenFrac.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(Color(0xFFFFA000))
                                )
                            }
                        }
                    }
                }

                // ПРАВАЯ КОЛОНКА: Продажи + чаты
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Продажи — большая карточка
                    BentoGlassCard(
                        modifier = Modifier.weight(1.6f).fillMaxWidth(),
                        color = Color.White.copy(0.04f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                null,
                                tint = Color.White.copy(0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    "${data.salesCount}",
                                    color = Color.White,
                                    fontSize = 52.sp,
                                    fontWeight = FontWeight.Black,
                                    style = TextStyle(shadow = Shadow(color.copy(0.4f), blurRadius = 20f))
                                )
                                BentoProgressBar(fraction = salesFrac, color = Color.White.copy(0.5f))
                            }
                        }
                    }

                    // Чаты
                    BentoGlassCard(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        color = if (data.unreadCount > 0) color.copy(0.18f) else Color.White.copy(0.04f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (data.unreadCount > 0) color.copy(0.25f) else Color.White.copy(0.06f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ChatBubble,
                                    null,
                                    tint = if (data.unreadCount > 0) color else Color.White.copy(0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                "${data.unreadCount}",
                                color = if (data.unreadCount > 0) color else Color.White.copy(0.3f),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        } else {
            // ПОРТРЕТНАЯ ориентация
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Верх: время
                BentoGlassCard(
                    modifier = Modifier.weight(1.2f).fillMaxWidth(),
                    color = color
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        if (data.online) Color(0xFF00E676) else Color(0xFF555555),
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                data.username,
                                color = Color.White.copy(0.6f),
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            data.time,
                            style = TextStyle(
                                fontSize = 80.sp,
                                fontWeight = FontWeight.Black,
                                fontFeatureSettings = "tnum",
                                brush = Brush.verticalGradient(listOf(Color.White, color.copy(0.7f)))
                            )
                        )
                        Text(
                            data.date.uppercase(),
                            color = color.copy(0.7f),
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoGlassCard(modifier = Modifier.weight(1f), color = Color(0xFF00E676).copy(0.15f)) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Canvas(Modifier.size(12.dp)) {
                                drawCircle(Color(0xFF00E676))
                            }
                            Column {
                                Text(
                                    data.balanceActive,
                                    color = Color(0xFF00E676),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                BentoProgressBar(fraction = activeFrac, color = Color(0xFF00E676))
                            }
                        }
                    }
                    BentoGlassCard(modifier = Modifier.weight(1f), color = Color(0xFFFFA000).copy(0.12f)) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Canvas(Modifier.size(12.dp)) {
                                drawCircle(Color(0xFFFFA000))
                            }
                            Column {
                                Text(
                                    data.balanceFrozen,
                                    color = Color(0xFFFFA000),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                BentoProgressBar(fraction = frozenFrac, color = Color(0xFFFFA000))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoGlassCard(modifier = Modifier.weight(1.5f), color = Color.White.copy(0.04f)) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(Icons.Default.ShoppingCart, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(16.dp))
                            Column {
                                Text(
                                    "${data.salesCount}",
                                    color = Color.White,
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Black
                                )
                                BentoProgressBar(fraction = salesFrac, color = Color.White.copy(0.4f))
                            }
                        }
                    }
                    BentoGlassCard(
                        modifier = Modifier.weight(1f),
                        color = if (data.unreadCount > 0) color.copy(0.18f) else Color.White.copy(0.04f)
                    ) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(
                                Icons.Default.ChatBubble,
                                null,
                                tint = if (data.unreadCount > 0) color else Color.White.copy(0.2f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "${data.unreadCount}",
                                color = if (data.unreadCount > 0) color else Color.White.copy(0.2f),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

// Стеклянная Bento-карточка
@Composable
fun BentoGlassCard(
    modifier: Modifier = Modifier,
    color: Color,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(0.07f), Color.White.copy(0.02f))
                ),
                RoundedCornerShape(18.dp)
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(0.15f), Color.White.copy(0.03f))
                ),
                RoundedCornerShape(18.dp)
            )
            .padding(14.dp),
        content = content
    )
}

// Минималистичный прогресс-бар
@Composable
fun BentoProgressBar(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(listOf(color, color.copy(0.5f)))
                )
        )
    }
}

// ---------------------------------------------------------
// Theme 7: Liquid — переработка
// ---------------------------------------------------------
@Composable
fun AosTheme7_Liquid(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val t1 = rotationAngleState(animate, 18000)
    val t2 = rotationAngleState(animate, -24000)
    val pulse = pulseAlphaState(animate, 0.5f, 1f, 2000)
    val breathe = breathingScaleState(animate, 1f, 1.05f, 3500)

    val balanceValue = data.balanceActive.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
    val frozenValue  = data.balanceFrozen.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
    val totalValue   = (balanceValue + frozenValue).coerceAtLeast(1f)
    val activeFrac   = (balanceValue / totalValue).coerceIn(0f, 1f)
    val salesFrac    = (data.salesCount.toFloat() / maxOf(data.salesCount.toFloat(), 200f)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050510))
    ) {
        // Жидкие blob-фоны
        Canvas(Modifier.fillMaxSize()) {
            val a1 = Math.toRadians(t1.value.toDouble())
            val a2 = Math.toRadians(t2.value.toDouble())

            val cx1 = size.width * 0.3f + sin(a1).toFloat() * size.width * 0.12f
            val cy1 = size.height * 0.4f + cos(a1).toFloat() * size.height * 0.1f
            drawCircle(
                Brush.radialGradient(
                    listOf(color.copy(0.22f), Color.Transparent),
                    center = Offset(cx1, cy1),
                    radius = size.minDimension * 0.45f
                ),
                radius = size.minDimension * 0.45f,
                center = Offset(cx1, cy1)
            )

            val cx2 = size.width * 0.7f + cos(a2).toFloat() * size.width * 0.1f
            val cy2 = size.height * 0.6f + sin(a2).toFloat() * size.height * 0.1f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFF0080).copy(0.12f), Color.Transparent),
                    center = Offset(cx2, cy2),
                    radius = size.minDimension * 0.38f
                ),
                radius = size.minDimension * 0.38f,
                center = Offset(cx2, cy2)
            )
        }

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // ЛЕВЫЙ БЛОК: Время + аватар
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Аватар с жидким кольцом
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            rotate(t1.value, Offset(size.width / 2, size.height / 2)) {
                                drawCircle(
                                    Brush.sweepGradient(
                                        listOf(color, color.copy(0.1f), color),
                                        center = Offset(size.width / 2, size.height / 2)
                                    ),
                                    style = Stroke(3.dp.toPx())
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0D0D1A)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (data.avatarUrl != null) {
                                AsyncImage(
                                    model = data.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    data.username.take(1).uppercase(),
                                    color = color,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        // Онлайн-индикатор
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .background(Color(0xFF050510), CircleShape)
                                .padding(3.dp)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (data.online) Color(0xFF00E676) else Color(0xFF333333),
                                        CircleShape
                                    )
                            )
                        }
                    }

                    // Время — большое
                    Column {
                        Text(
                            data.time,
                            style = TextStyle(
                                fontSize = 80.sp,
                                fontWeight = FontWeight.Black,
                                fontFeatureSettings = "tnum",
                                brush = Brush.verticalGradient(listOf(Color.White, color.copy(0.6f)))
                            ),
                            modifier = Modifier.graphicsLayer {
                                scaleX = breathe.value; scaleY = breathe.value
                            }
                        )
                        Text(
                            data.date,
                            color = color.copy(0.6f),
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // ПРАВЫЙ БЛОК: Статистика жидкими пиллами
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Доступный баланс — большая пилла
                    LiquidStatPill(
                        value = data.balanceActive,
                        fraction = activeFrac,
                        primaryColor = Color(0xFF00E676),
                        icon = Icons.Default.TrendingUp,
                        isLarge = true
                    )

                    // Холд
                    LiquidStatPill(
                        value = data.balanceFrozen,
                        fraction = (frozenValue / totalValue).coerceIn(0f, 1f),
                        primaryColor = Color(0xFFFFA000),
                        icon = Icons.Default.Pause,
                        isLarge = false
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Продажи
                        LiquidRoundStat(
                            value = "${data.salesCount}",
                            fraction = salesFrac,
                            color = Color.White,
                            icon = Icons.Default.ShoppingCart
                        )

                        // Чаты
                        LiquidRoundStat(
                            value = "${data.unreadCount}",
                            fraction = if (data.unreadCount > 0) 1f else 0f,
                            color = if (data.unreadCount > 0) color else Color(0xFF333333),
                            icon = Icons.Default.ChatBubble
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Верх: аватар
                Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        rotate(t1.value, Offset(size.width / 2, size.height / 2)) {
                            drawCircle(
                                Brush.sweepGradient(
                                    listOf(color, color.copy(0.1f), color),
                                    center = Offset(size.width / 2, size.height / 2)
                                ),
                                style = Stroke(3.dp.toPx())
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.size(68.dp).clip(CircleShape).background(Color(0xFF0D0D1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (data.avatarUrl != null) {
                            AsyncImage(model = data.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Text(data.username.take(1).uppercase(), color = color, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // Время
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        data.time,
                        style = TextStyle(
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Black,
                            fontFeatureSettings = "tnum",
                            brush = Brush.verticalGradient(listOf(Color.White, color.copy(0.6f)))
                        ),
                        modifier = Modifier.graphicsLayer { scaleX = breathe.value; scaleY = breathe.value }
                    )
                    Text(data.date, color = color.copy(0.6f), fontSize = 13.sp)
                }

                // Статы
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    LiquidStatPill(
                        value = data.balanceActive,
                        fraction = activeFrac,
                        primaryColor = Color(0xFF00E676),
                        icon = Icons.Default.TrendingUp,
                        isLarge = true
                    )
                    LiquidStatPill(
                        value = data.balanceFrozen,
                        fraction = (frozenValue / totalValue).coerceIn(0f, 1f),
                        primaryColor = Color(0xFFFFA000),
                        icon = Icons.Default.Pause,
                        isLarge = false
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiquidRoundStat(
                            value = "${data.salesCount}",
                            fraction = salesFrac,
                            color = Color.White,
                            icon = Icons.Default.ShoppingCart,
                            modifier = Modifier.weight(1f)
                        )
                        LiquidRoundStat(
                            value = "${data.unreadCount}",
                            fraction = if (data.unreadCount > 0) 1f else 0f,
                            color = if (data.unreadCount > 0) color else Color(0xFF333333),
                            icon = Icons.Default.ChatBubble,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// Жидкая пилла со встроенным прогресс-баром
@Composable
fun LiquidStatPill(
    value: String,
    fraction: Float,
    primaryColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLarge: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isLarge) 64.dp else 52.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(Color.White.copy(0.05f))
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(primaryColor.copy(0.4f), primaryColor.copy(0.05f))),
                RoundedCornerShape(50.dp)
            )
    ) {
        // Жидкое заполнение
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(primaryColor.copy(0.25f), primaryColor.copy(0.05f))
                    )
                )
        )
        // Контент
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = primaryColor.copy(0.8f), modifier = Modifier.size(if (isLarge) 18.dp else 14.dp))
            Text(
                value,
                color = primaryColor,
                fontSize = if (isLarge) 22.sp else 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Круглая жидкая статистика
@Composable
fun LiquidRoundStat(
    value: String,
    fraction: Float,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(Color.White.copy(0.05f))
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(color.copy(0.3f), Color.Transparent)),
                RoundedCornerShape(50.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Заполнение
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .background(
                    Brush.horizontalGradient(listOf(color.copy(0.2f), Color.Transparent))
                )
        )
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = color.copy(0.7f), modifier = Modifier.size(14.dp))
            Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ---------------------------------------------------------
// Theme 8: Cipher
// ---------------------------------------------------------
@Composable
fun AosTheme8_Cipher(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val alpha = pulseAlphaState(animate, 0.7f, 1f, 2000)
    // Легковесная матрица, генерируем один раз
    val matrixText = remember { buildString { repeat(80) { repeat(40) { append("0123456789ABCDEF₽$%&@#!?".random()) }; append("\n") } } }
    val scroll = infiniteScrollState(animate, 20000)

    Box(Modifier.fillMaxSize().clipToBounds()) {
        Text(matrixText, color = color.copy(0.15f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp, modifier = Modifier.fillMaxSize().padding(8.dp).graphicsLayer { translationY = (scroll.value % 100f) * -10f })

        Column(modifier = Modifier.align(Alignment.Center).fillMaxWidth(if(isLandscape) 0.6f else 0.9f).background(Color.Black.copy(0.85f), RoundedCornerShape(20.dp)).border(2.dp, color.copy(alpha.value), RoundedCornerShape(20.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("[ ENCRYPTED STATS ]", color = color.copy(0.6f), fontSize = 10.sp, letterSpacing = 3.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(16.dp))
            Text(data.time, color = color, fontSize = if(isLandscape) 64.sp else 48.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.graphicsLayer { this.alpha = alpha.value })
            Text(data.date, color = Color.White.copy(0.7f), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(20.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(color.copy(0.3f))); Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                CipherStat("FUNDS", data.balanceActive, Color(0xFF69F0AE)); CipherStat("MSG", "${data.unreadCount}", if (data.unreadCount > 0) color else Color.Gray); CipherStat("SALES", "${data.salesCount}", Color.White)
            }
        }
    }
}
@Composable fun CipherStat(label: String, value: String, color: Color) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1); Text(label, color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp) } }

@Composable
fun AosTheme9_Spectrum(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val slowRotation = rotationAngleState(animate, 40000)
    val slowRotation2 = rotationAngleState(animate, -60000)
    val wavePhase = infiniteScrollState(animate, 25000)
    val breathe = breathingScaleState(animate, 1f, 1.04f, 4000)
    val pulse = pulseAlphaState(animate, 0.4f, 1f, 2000)
    val pulse2 = pulseAlphaState(animate, 0.6f, 0.95f, 3200)

    val cList = listOf(Color(0xFFFF0080), color, Color(0xFF00DFD8), Color(0xFFFF0080))
    val cListExtended = listOf(
        Color(0xFFFF0080), Color(0xFFFF6B35), color,
        Color(0xFF00DFD8), Color(0xFF007CF0), Color(0xFFFF0080)
    )

    // Производные данные в числа
    val balanceValue = data.balanceActive.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
    val balanceMax = maxOf(balanceValue, 50000f)
    val balanceFraction = (balanceValue / balanceMax).coerceIn(0f, 1f)

    val frozenValue = data.balanceFrozen.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
    val totalValue = data.balanceAll.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 1f
    val frozenFraction = if (totalValue > 0f) (frozenValue / totalValue).coerceIn(0f, 1f) else 0f

    val salesFraction = (data.salesCount.toFloat() / maxOf(data.salesCount.toFloat(), 200f)).coerceIn(0f, 1f)
    val unreadFraction = if (data.unreadCount > 0) 1f else 0f

    Box(Modifier.fillMaxSize().background(Color(0xFF03030A)).clipToBounds()) {

        // ═══ ФОНОВЫЙ СЛОЙ: Медленно движущиеся туманности ═══
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val phase = wavePhase.value / 100f * (2 * Math.PI).toFloat()

            // Туманности — огромные мягкие blob'ы
            rotate(slowRotation.value * 0.3f, Offset(w / 2, h / 2)) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFFFF0080).copy(0.18f), Color.Transparent),
                        center = Offset(w * 0.25f, h * 0.3f),
                        radius = w * 0.55f
                    ), radius = w * 0.55f, center = Offset(w * 0.25f, h * 0.3f)
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(color.copy(0.15f), Color.Transparent),
                        center = Offset(w * 0.75f, h * 0.65f),
                        radius = w * 0.5f
                    ), radius = w * 0.5f, center = Offset(w * 0.75f, h * 0.65f)
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFF00DFD8).copy(0.12f), Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.8f),
                        radius = w * 0.4f
                    ), radius = w * 0.4f, center = Offset(w * 0.5f, h * 0.8f)
                )
            }

            // Синусоидальная волна снизу (красота фона)
            val path = Path()
            path.moveTo(0f, h)
            for (x in 0..w.toInt() step 8) {
                val nx = x / w
                val y = h * 0.72f +
                        sin(nx * Math.PI * 2.5 + phase).toFloat() * (h * 0.07f) +
                        sin(nx * Math.PI * 5.0 + phase * 1.7f).toFloat() * (h * 0.03f)
                path.lineTo(x.toFloat(), y)
            }
            path.lineTo(w, h); path.close()
            drawPath(path, Brush.verticalGradient(
                listOf(color.copy(0.18f), color.copy(0.06f), Color.Transparent),
                startY = h * 0.65f, endY = h
            ))

            // Вторая волна, сдвинутая по фазе
            val path2 = Path()
            path2.moveTo(0f, h)
            for (x in 0..w.toInt() step 8) {
                val nx = x / w
                val y = h * 0.80f +
                        sin(nx * Math.PI * 3.0 + phase * 1.3f + 1.5f).toFloat() * (h * 0.05f)
                path2.lineTo(x.toFloat(), y)
            }
            path2.lineTo(w, h); path2.close()
            drawPath(path2, Brush.verticalGradient(
                listOf(Color(0xFFFF0080).copy(0.1f), Color.Transparent),
                startY = h * 0.75f, endY = h
            ))
        }

        // ═══ СРЕДНИЙ СЛОЙ: Орбиты и кольца ═══
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2
            val avatarR = (if (isLandscape) 110.dp else 95.dp).toPx()

            // Внешние декоративные орбиты
            rotate(slowRotation.value, Offset(cx, cy)) {
                // Тонкая пунктирная орбита
                drawCircle(
                    color.copy(0.12f), avatarR * 1.55f, Offset(cx, cy),
                    style = Stroke(1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 14f)))
                )
                // Яркий радужный arc — быстрее всех
                drawArc(
                    brush = Brush.sweepGradient(cList, center = Offset(cx, cy)),
                    startAngle = 0f, sweepAngle = 200f, useCenter = false,
                    topLeft = Offset(cx - avatarR * 1.55f, cy - avatarR * 1.55f),
                    size = Size(avatarR * 3.1f, avatarR * 3.1f),
                    style = Stroke(5f, cap = StrokeCap.Round)
                )
                // Точки-маркеры на орбите
                for (i in 0..5) {
                    val a = Math.toRadians(i * 60.0)
                    drawCircle(
                        cListExtended[i].copy(0.8f), 5f,
                        Offset(cx + cos(a).toFloat() * avatarR * 1.55f, cy + sin(a).toFloat() * avatarR * 1.55f)
                    )
                }
            }

            rotate(slowRotation2.value, Offset(cx, cy)) {
                // Средняя орбита — прозрачнее
                drawCircle(
                    Brush.sweepGradient(listOf(Color.Transparent, Color(0xFF00DFD8).copy(0.3f), Color.Transparent), center = Offset(cx, cy)),
                    avatarR * 1.9f, Offset(cx, cy),
                    style = Stroke(3f)
                )
                // Планета на орбите
                val px = cx + avatarR * 1.9f
                drawCircle(Color(0xFF00DFD8).copy(0.9f), 8f, Offset(px, cy))
                drawCircle(Color(0xFF00DFD8).copy(0.3f), 16f, Offset(px, cy))
            }

            // Самая внешняя орбита — еле видна
            rotate(slowRotation.value * 0.4f, Offset(cx, cy)) {
                drawCircle(
                    color.copy(0.06f), avatarR * 2.4f, Offset(cx, cy),
                    style = Stroke(1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 20f)))
                )
                drawCircle(Color(0xFFFF0080).copy(0.7f), 5f, Offset(cx, cy - avatarR * 2.4f))
            }
        }

        // ═══ АВАТАР В ЦЕНТРЕ ═══
        val avatarSizeDp = if (isLandscape) 220.dp else 190.dp
        Box(modifier = Modifier.align(Alignment.Center).graphicsLayer { scaleX = breathe.value; scaleY = breathe.value }) {
            Box(modifier = Modifier.size(avatarSizeDp), contentAlignment = Alignment.Center) {
                // Радужное кольцо-ореол
                Canvas(Modifier.fillMaxSize()) {
                    rotate(slowRotation.value, Offset(size.width / 2, size.height / 2)) {
                        drawCircle(
                            Brush.sweepGradient(cListExtended, center = Offset(size.width / 2, size.height / 2)),
                            style = Stroke(6.dp.toPx())
                        )
                    }
                    // Внутреннее мягкое свечение
                    drawCircle(
                        Brush.radialGradient(
                            listOf(color.copy(0.3f), Color.Transparent),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width * 0.55f
                        )
                    )
                }
                // Сама аватарка
                Box(
                    Modifier.size(avatarSizeDp - 16.dp).clip(CircleShape)
                        .background(Color(0xFF0D0D1A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (data.avatarUrl != null) {
                        AsyncImage(model = data.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        // Если нет аватарки — красивый абстрактный паттерн
                        Canvas(Modifier.fillMaxSize()) {
                            val c = size.width / 2f; val rr = size.width * 0.38f
                            for (i in 0..5) {
                                val angle = Math.toRadians(i * 60.0)
                                drawLine(
                                    Brush.linearGradient(listOf(cListExtended[i].copy(0.6f), Color.Transparent)),
                                    Offset(c, c), Offset(c + cos(angle).toFloat() * rr, c + sin(angle).toFloat() * rr),
                                    strokeWidth = 2f
                                )
                                drawCircle(cListExtended[i].copy(0.5f), 4f, Offset(c + cos(angle).toFloat() * rr, c + sin(angle).toFloat() * rr))
                            }
                            drawCircle(color.copy(0.4f), rr * 0.25f, Offset(c, c), style = Stroke(3f))
                            drawCircle(color.copy(0.15f), rr * 0.1f, Offset(c, c))
                        }
                    }
                    // Overlay-виньетка для глубины
                    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(0.55f)))))
                }
                // Индикатор онлайна
                Box(
                    Modifier.align(Alignment.BottomEnd).offset((-12).dp, (-12).dp)
                        .size(26.dp).background(Color(0xFF03030A), CircleShape).padding(5.dp)
                ) {
                    Box(Modifier.fillMaxSize().background(
                        if (data.online) Color(0xFF00E676) else Color(0xFF555555), CircleShape
                    ))
                    if (data.online) {
                        Box(Modifier.fillMaxSize().background(
                            Brush.radialGradient(listOf(Color(0xFF00E676).copy(0.5f), Color.Transparent)), CircleShape
                        ).graphicsLayer { alpha = pulse.value })
                    }
                }
            }
        }

        // ═══ ДАННЫЕ: Визуальные арки вокруг (без текста) ═══
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2
            val avatarR = (if (isLandscape) 110.dp else 95.dp).toPx()
            val arcR = avatarR * 2.15f
            val stroke = 10.dp.toPx()
            val gapDeg = 8f

            // Арка 1: Баланс (зелёная) — верхняя правая четверть
            val balanceSweep = balanceFraction * (90f - gapDeg * 2)
            drawArc(
                brush = Brush.sweepGradient(listOf(Color(0xFF00E676).copy(0.2f), Color(0xFF00E676), Color(0xFF00E676).copy(0.2f)), center = Offset(cx, cy)),
                startAngle = -90f + gapDeg,
                sweepAngle = balanceSweep.coerceAtLeast(2f),
                useCenter = false,
                topLeft = Offset(cx - arcR, cy - arcR), size = Size(arcR * 2, arcR * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            // Трек арки 1 (фон)
            drawArc(
                color = Color(0xFF00E676).copy(0.08f),
                startAngle = -90f + gapDeg, sweepAngle = 90f - gapDeg * 2, useCenter = false,
                topLeft = Offset(cx - arcR, cy - arcR), size = Size(arcR * 2, arcR * 2),
                style = Stroke(stroke * 0.4f)
            )

            // Арка 2: Чаты (цвет темы) — верхняя левая четверть
            val unreadSweep = unreadFraction * (90f - gapDeg * 2)
            drawArc(
                brush = Brush.sweepGradient(listOf(color.copy(0.2f), color, color.copy(0.2f)), center = Offset(cx, cy)),
                startAngle = -180f + gapDeg,
                sweepAngle = unreadSweep.coerceAtLeast(if (data.unreadCount > 0) 4f else 0f),
                useCenter = false,
                topLeft = Offset(cx - arcR, cy - arcR), size = Size(arcR * 2, arcR * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color.copy(0.08f),
                startAngle = -180f + gapDeg, sweepAngle = 90f - gapDeg * 2, useCenter = false,
                topLeft = Offset(cx - arcR, cy - arcR), size = Size(arcR * 2, arcR * 2),
                style = Stroke(stroke * 0.4f)
            )

            // Арка 3: Продажи (розовая) — нижняя левая
            val salesSweep = salesFraction * (90f - gapDeg * 2)
            drawArc(
                brush = Brush.sweepGradient(listOf(Color(0xFFFF0080).copy(0.2f), Color(0xFFFF0080), Color(0xFFFF0080).copy(0.2f)), center = Offset(cx, cy)),
                startAngle = 90f + gapDeg,
                sweepAngle = salesSweep.coerceAtLeast(2f),
                useCenter = false,
                topLeft = Offset(cx - arcR, cy - arcR), size = Size(arcR * 2, arcR * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFFFF0080).copy(0.08f),
                startAngle = 90f + gapDeg, sweepAngle = 90f - gapDeg * 2, useCenter = false,
                topLeft = Offset(cx - arcR, cy - arcR), size = Size(arcR * 2, arcR * 2),
                style = Stroke(stroke * 0.4f)
            )

            // Арка 4: Замороженный баланс (оранжевая) — нижняя правая
            val frozenSweep = frozenFraction * (90f - gapDeg * 2)
            drawArc(
                brush = Brush.sweepGradient(listOf(Color(0xFFFFA000).copy(0.2f), Color(0xFFFFA000), Color(0xFFFFA000).copy(0.2f)), center = Offset(cx, cy)),
                startAngle = 0f + gapDeg,
                sweepAngle = frozenSweep.coerceAtLeast(2f),
                useCenter = false,
                topLeft = Offset(cx - arcR, cy - arcR), size = Size(arcR * 2, arcR * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFFFFA000).copy(0.08f),
                startAngle = 0f + gapDeg, sweepAngle = 90f - gapDeg * 2, useCenter = false,
                topLeft = Offset(cx - arcR, cy - arcR), size = Size(arcR * 2, arcR * 2),
                style = Stroke(stroke * 0.4f)
            )

            // Светящиеся торцы заполненных арок
            listOf(
                Triple(-90f + gapDeg + balanceSweep, arcR, Color(0xFF00E676)),
                Triple(-180f + gapDeg + (if (data.unreadCount > 0) unreadSweep else 0f), arcR, color),
                Triple(90f + gapDeg + salesSweep, arcR, Color(0xFFFF0080)),
                Triple(0f + gapDeg + frozenSweep, arcR, Color(0xFFFFA000))
            ).forEach { (angleDeg, r, col) ->
                val rad = Math.toRadians(angleDeg.toDouble())
                val tip = Offset(cx + cos(rad).toFloat() * r, cy + sin(rad).toFloat() * r)
                drawCircle(col, stroke / 2f, tip)
                drawCircle(col.copy(0.4f), stroke, tip)
            }
        }

        // ═══ ВРЕМЯ: Крупное, снаружи аватара (позиционировано по ориентации) ═══
        if (isLandscape) {
            // Время — слева
            Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)) {
                Text(
                    data.time,
                    style = TextStyle(
                        fontSize = 88.sp,
                        fontWeight = FontWeight.Black,
                        fontFeatureSettings = "tnum",
                        brush = Brush.verticalGradient(listOf(Color.White, color.copy(0.7f)))
                    ),
                    modifier = Modifier.graphicsLayer { alpha = pulse2.value }
                )
            }
            // Дата — снизу слева (только дата, без слов — это число+месяц)
            // Мы покажем дату как точки/тире паттерн дня недели визуально
            // Точки дня недели
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 28.dp)) {
                Canvas(Modifier.size(140.dp, 12.dp)) {
                    val cal = Calendar.getInstance()
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat
                    // 7 точек = дни недели, текущий день — яркий
                    for (i in 0..6) {
                        val isSunday = i == 0; val dayNum = if (isSunday) 1 else i + 1
                        val isToday = dayNum == dayOfWeek
                        val x = i * (size.width / 6f)
                        drawCircle(
                            if (isToday) color else Color.White.copy(0.18f),
                            if (isToday) 7f else 4f,
                            Offset(x, size.height / 2)
                        )
                        if (isToday) drawCircle(color.copy(0.35f), 13f, Offset(x, size.height / 2))
                    }
                }
            }
            // Числа продаж — справа, крупно
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 36.dp)) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Иконка-блок вместо текста
                    SpectrumIconBlock(data.salesCount.toString(), Color.White, Icons.Default.ShoppingCart, Color.White.copy(0.1f))
                    SpectrumIconBlock(data.unreadCount.toString(), if (data.unreadCount > 0) color else Color.Gray, Icons.Default.ChatBubble, if (data.unreadCount > 0) color.copy(0.1f) else Color.Gray.copy(0.05f))
                }
            }
        } else {
            // Время — сверху
            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp)) {
                Text(
                    data.time,
                    style = TextStyle(
                        fontSize = 100.sp,
                        fontWeight = FontWeight.Black,
                        fontFeatureSettings = "tnum",
                        brush = Brush.verticalGradient(listOf(Color.White, color.copy(0.7f)))
                    ),
                    modifier = Modifier.graphicsLayer { alpha = pulse2.value }
                )
            }
            // Иконки снизу
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    SpectrumIconBlock(data.salesCount.toString(), Color.White, Icons.Default.ShoppingCart, Color.White.copy(0.08f))
                    SpectrumIconBlock(data.unreadCount.toString(), if (data.unreadCount > 0) color else Color.Gray, Icons.Default.ChatBubble, if (data.unreadCount > 0) color.copy(0.1f) else Color.Gray.copy(0.05f))
                }
            }
            // Точки-дни недели снизу
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                Canvas(Modifier.size(100.dp, 8.dp)) {
                    val cal = Calendar.getInstance()
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                    for (i in 0..6) {
                        val dayNum = if (i == 0) 1 else i + 1
                        val isToday = dayNum == dayOfWeek
                        val x = i * (size.width / 6f)
                        drawCircle(
                            if (isToday) color else Color.White.copy(0.15f),
                            if (isToday) 5f else 3f,
                            Offset(x, size.height / 2)
                        )
                    }
                }
            }
        }
    }
}

// Блок с иконкой и цифрой — без единого слова
@Composable
fun SpectrumIconBlock(value: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color) {
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(0.25f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = color.copy(0.7f), modifier = Modifier.size(18.dp))
        Text(value, color = color, fontSize = 26.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = Shadow(color.copy(0.5f), blurRadius = 12f)))
    }
}

@Composable
fun SpectrumHugeAvatar(data: AosThemeData, cList: List<Color>, rotation: Float, sizeDp: Dp) {
    Box(modifier = Modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        // Медленно вращающееся радужное кольцо спектра
        Canvas(Modifier.fillMaxSize()) {
            rotate(rotation, Offset(size.width / 2, size.height / 2)) {
                drawCircle(Brush.sweepGradient(cList, center = Offset(size.width / 2, size.height / 2)), style = Stroke(8.dp.toPx()))
            }
        }
        // Сама аватарка
        Box(Modifier.size(sizeDp - 20.dp).clip(CircleShape).background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
            if (data.avatarUrl != null) {
                AsyncImage(model = data.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(data.username.take(1).uppercase(), style = TextStyle(fontSize = (sizeDp.value * 0.4f).sp, fontWeight = FontWeight.Black, brush = Brush.linearGradient(cList)))
            }
        }
        // Массивный индикатор онлайна
        Box(Modifier.align(Alignment.BottomEnd).offset(x = (-20).dp, y = (-20).dp).size(32.dp).background(Color(0xFF05050A), CircleShape).padding(6.dp)) {
            Box(Modifier.fillMaxSize().background(if(data.online) Color(0xFF00E676) else Color(0xFFFF5252), CircleShape))
        }
    }
}

@Composable
fun SpectrumHugeStat(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.End) {
        // Цифры огромного размера со светящейся тенью
        Text(value, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Black, style = TextStyle(shadow = Shadow(accent.copy(0.6f), blurRadius = 24f)))
        Text(label, color = accent, fontSize = 14.sp, letterSpacing = 3.sp, fontWeight = FontWeight.ExtraBold)
    }
}
@Composable fun SpectrumStatRow(label: String, value: String, color: Color) { Row(modifier = Modifier.background(color.copy(0.08f), RoundedCornerShape(12.dp)).border(1.dp, color.copy(0.2f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Text(label, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(80.dp), textAlign = TextAlign.End); Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold) } }

// ---------------------------------------------------------
// Theme 10: Depth
// ---------------------------------------------------------
@Composable
fun AosTheme10_Depth(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val breathe = breathingScaleState(animate, 1f, 1.02f, 5000)

    Box(Modifier.fillMaxSize()) {
        Text(data.time.take(5), color = color.copy(0.06f), fontSize = if(isLandscape) 300.sp else 180.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center).graphicsLayer { scaleX = breathe.value; scaleY = breathe.value }, maxLines = 1, overflow = TextOverflow.Visible)
        Text(data.username.uppercase(), color = Color.White.copy(0.04f), fontSize = if(isLandscape) 80.sp else 48.sp, fontWeight = FontWeight.Black, letterSpacing = 8.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp), maxLines = 1, overflow = TextOverflow.Visible)

        Row(modifier = Modifier.align(Alignment.TopEnd).padding(32.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(10.dp).background(if (data.online) Color(0xFF00E676) else Color(0xFFFF5252), CircleShape)); Spacer(Modifier.width(8.dp)); Text(if (data.online) "ONLINE" else "OFFLINE", color = if (data.online) Color(0xFF00E676) else Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }

        Box(modifier = Modifier.align(if(isLandscape) Alignment.BottomStart else Alignment.BottomCenter).padding(32.dp)) {
            Column(horizontalAlignment = if(isLandscape) Alignment.Start else Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) { if (data.avatarUrl != null) { AsyncImage(model = data.avatarUrl, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape).border(1.dp, color, CircleShape), contentScale = ContentScale.Crop); Spacer(Modifier.width(12.dp)) }; Text(data.username, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp)); Text(data.time, color = Color.White, fontSize = if(isLandscape) 80.sp else 64.sp, fontWeight = FontWeight.Black, style = TextStyle(fontFeatureSettings = "tnum")); Text(data.date, color = color, fontSize = 18.sp); Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { DepthStat("Доступно", data.balanceActive, color); DepthStat("Чаты", "${data.unreadCount}", if (data.unreadCount > 0) color else Color.Gray); DepthStat("Продажи", "${data.salesCount}", Color.White) }
            }
        }
    }
}
@Composable fun DepthStat(label: String, value: String, color: Color) { Column { Text(label, color = Color.Gray, fontSize = 9.sp, letterSpacing = 1.sp); Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1) } }

// ---------------------------------------------------------
// Theme 11: Plasma
// ---------------------------------------------------------
@Composable
fun AosTheme11_Plasma(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val rotation = rotationAngleState(animate, 4000)
    val rotation2 = rotationAngleState(animate, 7000)
    val pulse = pulseAlphaState(animate, 0.5f, 1f)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2; val r1 = minOf(size.width, size.height) * 0.38f
            rotate(rotation.value, Offset(cx, cy)) { drawCircle(Brush.sweepGradient(listOf(color, color.copy(0.4f), Color.Transparent, color.copy(0.2f), color), center = Offset(cx, cy)), r1, Offset(cx, cy), style = Stroke(6f)) }
            rotate(-rotation2.value, Offset(cx, cy)) { drawCircle(Brush.sweepGradient(listOf(Color.Transparent, color.copy(0.3f), color.copy(0.7f), Color.Transparent), center = Offset(cx, cy)), r1*0.8f, Offset(cx, cy), style = Stroke(3f)) }
            drawCircle(color.copy(0.08f), r1 * 0.55f, Offset(cx, cy))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (data.avatarUrl != null) { AsyncImage(model = data.avatarUrl, contentDescription = null, modifier = Modifier.size(52.dp).clip(CircleShape).border(2.dp, color, CircleShape), contentScale = ContentScale.Crop); Spacer(Modifier.height(8.dp)) }
            Text(data.time.take(5), color = Color.White, fontSize = if(isLandscape) 80.sp else 64.sp, fontWeight = FontWeight.Black, modifier = Modifier.graphicsLayer { this.alpha = pulse.value }, style = TextStyle(fontFeatureSettings = "tnum"))
            Text(data.time.takeLast(2), color = color, fontSize = 32.sp, fontWeight = FontWeight.Light, style = TextStyle(fontFeatureSettings = "tnum"))
            Spacer(Modifier.height(8.dp)); Text(data.date, color = Color.White.copy(0.6f), fontSize = 14.sp)
        }
        if(isLandscape){
            PlasmaCorner(modifier = Modifier.align(Alignment.TopStart).padding(32.dp), label = "БАЛАНС", value = data.balanceActive, color = Color(0xFF69F0AE))
            PlasmaCorner(modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp), label = "ЧАТЫ", value = "${data.unreadCount}", color = if (data.unreadCount > 0) color else Color.Gray, alignEnd = true)
        } else {
            Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)){
                Text(data.balanceActive, color = Color(0xFF69F0AE), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${data.unreadCount} сообщений", color = if(data.unreadCount>0) color else Color.Gray, fontSize = 20.sp)
            }
        }
    }
}
@Composable fun PlasmaCorner(modifier: Modifier, label: String, value: String, color: Color, alignEnd: Boolean = false) { Column(modifier = modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) { Text(label, color = Color.Gray, fontSize = 10.sp, letterSpacing = 1.sp); Text(value, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1) } }

// ---------------------------------------------------------
// Theme 12: Zen
// ---------------------------------------------------------
@Composable
fun AosTheme12_Zen(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val breathe = breathingScaleState(animate, 1f, 1.015f, 5000)
    val alpha = pulseAlphaState(animate, 0.7f, 1f, 4000)

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A08))) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 1.dp.toPx()
            if (isLandscape) {
                drawLine(color.copy(0.08f), Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), sw)
                drawLine(color.copy(0.08f), Offset(size.width * 2 / 3f, 0f), Offset(size.width * 2 / 3f, size.height), sw)
            } else {
                drawLine(color.copy(0.08f), Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), sw)
                drawLine(color.copy(0.08f), Offset(0f, size.height * 2 / 3f), Offset(size.width, size.height * 2 / 3f), sw)
            }
            drawCircle(color.copy(0.1f), size.minDimension * 0.4f, Offset(size.width/2, size.height/2), style = Stroke(sw))
        }

        Text("時", color = color.copy(0.1f), fontSize = 240.sp, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.Center).graphicsLayer { scaleX = breathe.value; scaleY = breathe.value })

        Column(modifier = Modifier.align(if(isLandscape) Alignment.BottomCenter else Alignment.TopCenter).padding(if(isLandscape) PaddingValues(bottom=56.dp) else PaddingValues(top=56.dp)).graphicsLayer { this.alpha = alpha.value }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(data.time, color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 4.sp, style = TextStyle(fontFeatureSettings = "tnum"))
            Text(data.date, color = color.copy(0.6f), fontSize = 14.sp, letterSpacing = 2.sp)
        }
        Column(modifier = Modifier.align(if(isLandscape) Alignment.CenterEnd else Alignment.BottomCenter).padding(if(isLandscape) PaddingValues(end=48.dp) else PaddingValues(bottom=56.dp)), verticalArrangement = Arrangement.spacedBy(24.dp), horizontalAlignment = if(isLandscape) Alignment.End else Alignment.CenterHorizontally) { ZenStat(data.balanceActive, "доступно"); ZenStat("${data.unreadCount}", "чат") }
    }
}
@Composable fun ZenStat(value: String, label: String) { Column(horizontalAlignment = Alignment.End) { Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp); Text(label, color = Color.Gray, fontSize = 10.sp, letterSpacing = 2.sp) } }

@Composable
fun AosTheme13_Inferno(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val flicker = pulseAlphaState(animate, 0.8f, 1f, 200)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A0000), Color.Black)))) {
        Canvas(Modifier.fillMaxSize()) {
            val r = (size.width * 0.8f).coerceAtLeast(1f)
            drawRect(Brush.radialGradient(listOf(Color(0xFFFF4500).copy(0.2f), Color.Transparent), center = Offset(size.width / 2, size.height), radius = r))
        }
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(data.time, style = TextStyle(fontSize = if(isLandscape) 120.sp else 80.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum", brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFF6D00)))), modifier = Modifier.graphicsLayer { alpha = flicker.value })
        }
        if (isLandscape) {
            Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).fillMaxWidth(0.8f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfernoCard("ДОСТУПНО", data.balanceActive, Color(0xFFFF6D00), modifier = Modifier.weight(1f)); InfernoCard("ЧАТЫ", "${data.unreadCount}", if (data.unreadCount > 0) Color(0xFFFF4500) else Color.Gray, modifier = Modifier.weight(1f))
            }
        } else {
            Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).fillMaxWidth(0.8f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfernoCard("ДОСТУПНО", data.balanceActive, Color(0xFFFF6D00), modifier = Modifier.fillMaxWidth()); InfernoCard("ЧАТЫ", "${data.unreadCount}", if (data.unreadCount > 0) Color(0xFFFF4500) else Color.Gray, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
@Composable fun InfernoCard(label: String, value: String, color: Color, modifier: Modifier) { Box(modifier = modifier.background(Brush.verticalGradient(listOf(color.copy(0.2f), color.copy(0.05f))), RoundedCornerShape(14.dp)).border(1.dp, color.copy(0.4f), RoundedCornerShape(14.dp)).padding(16.dp)) { Column { Text(label, color = Color.Gray, fontSize = 10.sp, letterSpacing = 1.sp); Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1) } } }

// ---------------------------------------------------------
// Theme 14: Arctic
// ---------------------------------------------------------
@Composable
fun AosTheme14_Arctic(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val iceColor = Color(0xFF00B0FF); val snowColor = Color(0xFFE3F2FD)

    // Генерируем снежинки один раз (случайные координаты 0.0..1.0)
    val flakes = remember { List(8) { Pair(Math.random().toFloat(), Math.random().toFloat()) } }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF001F3F), Color(0xFF001020))))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(iceColor.copy(0.08f), Color.Transparent), endY = size.height * 0.5f))
            val rad = 3.dp.toPx()
            flakes.forEach { (fx, fy) -> val p = Offset(size.width * fx, size.height * fy); drawCircle(snowColor.copy(0.4f), rad, p) }
        }
        if (isLandscape) {
            Row(Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxHeight().weight(1f).padding(48.dp), verticalArrangement = Arrangement.Center) { Text(data.time, style = TextStyle(fontSize = 120.sp, fontWeight = FontWeight.ExtraLight, fontFeatureSettings = "tnum", brush = Brush.verticalGradient(listOf(snowColor, iceColor)))); Text(data.date, color = iceColor.copy(0.7f), fontSize = 16.sp) }
                Column(modifier = Modifier.fillMaxHeight().weight(0.8f).padding(40.dp), verticalArrangement = Arrangement.Center) { ArcticStat("БАЛАНС", data.balanceActive, Color(0xFF80D8FF)); Spacer(Modifier.height(20.dp)); ArcticStat("СООБЩЕНИЯ", "${data.unreadCount}", if (data.unreadCount > 0) iceColor else Color.Gray) }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(data.time, style = TextStyle(fontSize = 80.sp, fontWeight = FontWeight.ExtraLight, fontFeatureSettings = "tnum", brush = Brush.verticalGradient(listOf(snowColor, iceColor)))); Text(data.date, color = iceColor.copy(0.7f), fontSize = 16.sp) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { ArcticStat("БАЛАНС", data.balanceActive, Color(0xFF80D8FF)); Spacer(Modifier.height(24.dp)); ArcticStat("СООБЩЕНИЯ", "${data.unreadCount}", if (data.unreadCount > 0) iceColor else Color.Gray) }
            }
        }
    }
}
@Composable fun ArcticStat(label: String, value: String, color: Color) { Column { Text(label, color = Color.Gray, fontSize = 10.sp, letterSpacing = 2.sp); Text(value, color = color, fontSize = 32.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp); Box(Modifier.width(64.dp).height(1.dp).background(color.copy(0.3f))) } }

// ---------------------------------------------------------
// Theme 15: Prism
// ---------------------------------------------------------
@Composable
fun AosTheme15_Prism(color: Color, data: AosThemeData, animate: Boolean, isLandscape: Boolean) {
    val prismColors = listOf(Color(0xFFFF0080), Color(0xFF7928CA), Color(0xFF00DFD8), Color(0xFF007CF0))
    val prismRot = rotationAngleState(animate, 40000)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2
            rotate(prismRot.value, Offset(cx, cy)) {
                prismColors.forEachIndexed { i, c -> val angle = Math.toRadians((i * 90.0)); val ex = cx + cos(angle).toFloat() * size.width * 1.5f; val ey = cy + sin(angle).toFloat() * size.height * 1.5f; drawLine(c.copy(0.4f), Offset(cx, cy), Offset(ex, ey), 4f + i * 1f) }
            }
        }
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(data.time, style = TextStyle(fontSize = if(isLandscape) 140.sp else 80.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum", color = Color.White))
            Text(data.date.uppercase(), color = Color.White.copy(0.5f), fontSize = 16.sp, letterSpacing = 3.sp)
        }
        Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).fillMaxWidth(0.8f), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(data.balanceActive, color = prismColors[2], fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("БАЛАНС", color = Color.Gray, fontSize = 10.sp) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${data.unreadCount}", color = if(data.unreadCount>0) prismColors[0] else Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("ЧАТЫ", color = Color.Gray, fontSize = 10.sp) }
        }
    }
}
