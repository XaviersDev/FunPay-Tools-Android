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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCustomizationScreen(
    currentTheme: AppTheme,
    onThemeChanged: (AppTheme) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTheme by remember { mutableStateOf(currentTheme) }
    var amoledMode by remember { mutableStateOf(ThemeManager.isAmoledMode(context)) }
    var showColorPicker by remember { mutableStateOf(false) }
    var editingColor by remember { mutableStateOf("") }
    var isGeneratingPalette by remember { mutableStateOf(false) }

    fun commit(theme: AppTheme) {
        // Применяем wallpaper-override СРАЗУ — чтобы и сохранённая тема,
        // и тема, улетающая в UI через onThemeChanged, были согласованы.
        // Без этого, после нажатия "Палитра" backgroundColor становится непрозрачным
        // в живой теме (saveTheme его поправит, но UI уже увидит непрозрачный фон
        // и перекроет обои до перезагрузки).
        val patched = ThemeManager.applyWallpaperBackgroundOverride(theme)
        selectedTheme = patched
        ThemeManager.saveTheme(context, patched)
        onThemeChanged(patched)
    }

    // === Picker для обоев ===
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isGeneratingPalette = true
                val (savedPath, generatedPalette) = withContext(Dispatchers.IO) {
                    val path = ThemeManager.saveWallpaperFromUri(context, uri) ?: return@withContext null to null
                    val bmp = ThemeManager.loadWallpaperBitmap(path) ?: return@withContext path to null
                    val pal = try { WallpaperPalette.generate(bmp) } catch (e: Exception) { null }
                    path to pal
                }

                if (savedPath != null) {
                    val base = selectedTheme.copy(
                        useWallpaper = true,
                        wallpaperPath = savedPath,
                        wallpaperVersion = System.currentTimeMillis()  // инвалидация кеша bitmap
                    )
                    val withPalette = if (generatedPalette != null) {
                        WallpaperPalette.apply(base, generatedPalette)
                    } else base
                    commit(withPalette)
                }
                isGeneratingPalette = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Кастомизация", color = ThemeManager.parseColor(selectedTheme.textPrimaryColor)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ThemeManager.parseColor(selectedTheme.textPrimaryColor))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ThemeManager.parseColor(selectedTheme.surfaceColor)
                )
            )
        },
        containerColor = ThemeManager.parseColor(selectedTheme.backgroundColor)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ==================== AMOLED ====================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = ThemeManager.parseColor(selectedTheme.surfaceColor)
                    ),
                    shape = RoundedCornerShape(selectedTheme.borderRadius.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AMOLED режим",
                                fontSize = 16.sp,
                                color = ThemeManager.parseColor(selectedTheme.textPrimaryColor)
                            )
                            Switch(
                                checked = amoledMode,
                                enabled = !selectedTheme.useWallpaper,
                                onCheckedChange = {
                                    amoledMode = it
                                    ThemeManager.setAmoledMode(context, it)
                                    val updated = if (it) {
                                        selectedTheme.copy(
                                            backgroundColor = "#000000",
                                            surfaceColor = "#000000",
                                            isAmoled = true
                                        )
                                    } else {
                                        selectedTheme.copy(
                                            backgroundColor = selectedTheme.originalBackgroundColor,
                                            surfaceColor = selectedTheme.originalSurfaceColor,
                                            isAmoled = false
                                        )
                                    }
                                    commit(updated)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ThemeManager.parseColor(selectedTheme.accentColor),
                                    checkedTrackColor = ThemeManager.parseColor(selectedTheme.accentColor).copy(alpha = 0.5f)
                                )
                            )
                        }
                        if (selectedTheme.useWallpaper) {
                            Text(
                                "Недоступно при включённых обоях",
                                fontSize = 11.sp,
                                color = ThemeManager.parseColor(selectedTheme.textSecondaryColor),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // ==================== ОБОИ ====================
            item {
                Text(
                    text = "Обои темы",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.parseColor(selectedTheme.textPrimaryColor)
                )
            }

            item {
                WallpaperCard(
                    theme = selectedTheme,
                    isGenerating = isGeneratingPalette,
                    onPick = {
                        wallpaperPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemove = {
                        ThemeManager.deleteWallpaper(context)
                        commit(selectedTheme.copy(
                            useWallpaper = false,
                            wallpaperPath = null
                        ))
                    },
                    onRegeneratePalette = {
                        val path = selectedTheme.wallpaperPath ?: return@WallpaperCard
                        scope.launch {
                            isGeneratingPalette = true
                            val pal = withContext(Dispatchers.IO) {
                                val bmp = ThemeManager.loadWallpaperBitmap(path)
                                if (bmp != null) try { WallpaperPalette.generate(bmp) } catch (e: Exception) { null } else null
                            }
                            if (pal != null) commit(WallpaperPalette.apply(selectedTheme, pal))
                            isGeneratingPalette = false
                        }
                    },
                    onChange = { commit(it) }
                )
            }

            // ==================== ГОТОВЫЕ ТЕМЫ ====================
            item {
                Text(
                    text = "Готовые темы",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.parseColor(selectedTheme.textPrimaryColor)
                )
            }

            items(ThemeManager.defaultThemes) { theme ->
                ThemePreviewCard(
                    theme = theme,
                    isSelected = theme.name == selectedTheme.name && !amoledMode && !selectedTheme.useWallpaper,
                    onClick = {
                        amoledMode = false
                        ThemeManager.setAmoledMode(context, false)
                        // Сохраняем настройки обоев если они уже были
                        val merged = theme.copy(
                            useWallpaper = selectedTheme.useWallpaper,
                            wallpaperPath = selectedTheme.wallpaperPath,
                            wallpaperAlpha = selectedTheme.wallpaperAlpha,
                            wallpaperDim = selectedTheme.wallpaperDim,
                            wallpaperBlur = selectedTheme.wallpaperBlur,
                            wallpaperEffect = selectedTheme.wallpaperEffect,
                            parallaxIntensity = selectedTheme.parallaxIntensity
                        )
                        commit(merged)
                    }
                )
            }

            // ==================== ПАЛИТРА ЦВЕТОВ ====================
            item {
                Text(
                    text = "Палитра цветов",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.parseColor(selectedTheme.textPrimaryColor),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = ThemeManager.parseColor(selectedTheme.surfaceColor)
                    ),
                    shape = RoundedCornerShape(selectedTheme.borderRadius.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ColorOption("Основной цвет", selectedTheme.primaryColor) {
                            editingColor = "primary"; showColorPicker = true
                        }
                        ColorOption("Акцент", selectedTheme.accentColor) {
                            editingColor = "accent"; showColorPicker = true
                        }
                        if (!amoledMode) {
                            ColorOption("Фон", selectedTheme.backgroundColor) {
                                editingColor = "background"; showColorPicker = true
                            }
                            ColorOption("Поверхность", selectedTheme.surfaceColor) {
                                editingColor = "surface"; showColorPicker = true
                            }
                        }
                        ColorOption("Основной текст", selectedTheme.textPrimaryColor) {
                            editingColor = "textPrimary"; showColorPicker = true
                        }
                        ColorOption("Вторичный текст", selectedTheme.textSecondaryColor) {
                            editingColor = "textSecondary"; showColorPicker = true
                        }
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = when (editingColor) {
                "primary"        -> selectedTheme.primaryColor
                "accent"         -> selectedTheme.accentColor
                "background"     -> selectedTheme.backgroundColor
                "surface"        -> selectedTheme.surfaceColor
                "textPrimary"    -> selectedTheme.textPrimaryColor
                "textSecondary"  -> selectedTheme.textSecondaryColor
                else -> "#FFFFFF"
            },
            onColorSelected = { color ->
                val updated = when (editingColor) {
                    "primary"        -> selectedTheme.copy(primaryColor = color)
                    "accent"         -> selectedTheme.copy(accentColor = color)
                    "background"     -> selectedTheme.copy(backgroundColor = color, originalBackgroundColor = color)
                    "surface"        -> selectedTheme.copy(surfaceColor = color, originalSurfaceColor = color)
                    "textPrimary"    -> selectedTheme.copy(textPrimaryColor = color)
                    "textSecondary"  -> selectedTheme.copy(textSecondaryColor = color)
                    else -> selectedTheme
                }
                commit(updated)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

// =============================================================================
//                              WALLPAPER CARD
// =============================================================================

@Composable
private fun WallpaperCard(
    theme: AppTheme,
    isGenerating: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    onRegeneratePalette: () -> Unit,
    onChange: (AppTheme) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor)
        ),
        shape = RoundedCornerShape(theme.borderRadius.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // === Превью + кнопки ===
            // === Живое превью: показывает РОВНО то, что увидит пользователь ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onPick() }
            ) {
                val path = theme.wallpaperPath
                if (theme.useWallpaper && !path.isNullOrBlank()) {
                    val cacheKey = "${path}#${theme.wallpaperVersion}"
                    var bmp by remember(cacheKey) { mutableStateOf<android.graphics.Bitmap?>(null) }
                    LaunchedEffect(cacheKey) {
                        bmp = withContext(Dispatchers.IO) {
                            ThemeManager.loadWallpaperBitmap(path)
                        }
                    }
                    bmp?.let { b ->
                        // Точно те же эффекты, что в WallpaperLayer
                        Image(
                            bitmap = b.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = theme.wallpaperAlpha,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val scale = if (theme.wallpaperEffect == WallpaperEffect.PARALLAX) 1.12f else 1.0f
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = theme.wallpaperDim))
                        )
                        // Мок UI поверх — чтобы видеть как обои будут смотреться под контентом
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Топбар-имитация
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity))
                            )
                            // Карточка-имитация
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity))
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = ThemeManager.parseColor(theme.textSecondaryColor),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Нажмите, чтобы выбрать картинку",
                            fontSize = 13.sp,
                            color = ThemeManager.parseColor(theme.textSecondaryColor)
                        )
                    }
                }

                if (isGenerating) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = ThemeManager.parseColor(theme.accentColor),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            if (theme.useWallpaper && !theme.wallpaperPath.isNullOrBlank()) {

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ThemeManager.parseColor(theme.accentColor)
                        )
                    ) { Text("Заменить", fontSize = 12.sp) }

                    OutlinedButton(
                        onClick = onRegeneratePalette,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ThemeManager.parseColor(theme.accentColor)
                        )
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Палитра", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onRemove,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFE53935)
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // === Слайдеры ===
                SliderRow(
                    label = "Затемнение",
                    value = theme.wallpaperDim,
                    valueText = "${(theme.wallpaperDim * 100).toInt()}%",
                    range = 0f..0.9f,
                    theme = theme,
                    onValueChange = { onChange(theme.copy(wallpaperDim = it)) }
                )

                Spacer(Modifier.height(12.dp))

                // === Эффект ===
                Text(
                    "Эффект",
                    fontSize = 13.sp,
                    color = ThemeManager.parseColor(theme.textSecondaryColor)
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EffectChip(
                        label = "Статика",
                        selected = theme.wallpaperEffect == WallpaperEffect.STATIC,
                        theme = theme,
                        modifier = Modifier.weight(1f)
                    ) { onChange(theme.copy(wallpaperEffect = WallpaperEffect.STATIC)) }

                    EffectChip(
                        label = "Движение",
                        selected = theme.wallpaperEffect == WallpaperEffect.PARALLAX,
                        theme = theme,
                        modifier = Modifier.weight(1f)
                    ) { onChange(theme.copy(wallpaperEffect = WallpaperEffect.PARALLAX)) }
                }

                if (theme.wallpaperEffect == WallpaperEffect.PARALLAX) {
                    Spacer(Modifier.height(12.dp))
                    SliderRow(
                        label = "Сила движения",
                        value = theme.parallaxIntensity,
                        valueText = "${(theme.parallaxIntensity * 100).toInt()}%",
                        range = 0.02f..0.30f,
                        theme = theme,
                        onValueChange = { onChange(theme.copy(parallaxIntensity = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    theme: AppTheme,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 13.sp, color = ThemeManager.parseColor(theme.textPrimaryColor))
            Text(valueText, fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ThemeManager.parseColor(theme.accentColor),
                activeTrackColor = ThemeManager.parseColor(theme.accentColor),
                inactiveTrackColor = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun EffectChip(
    label: String,
    selected: Boolean,
    theme: AppTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) accent else accent.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else ThemeManager.parseColor(theme.textPrimaryColor)
        )
    }
}

// =============================================================================
//                              ОСТАЛЬНОЕ (не менялось по логике)
// =============================================================================

@Composable
fun ThemePreviewCard(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    ThemeManager.parseColor(theme.accentColor),
                    RoundedCornerShape(theme.borderRadius.dp)
                ) else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor)
        ),
        shape = RoundedCornerShape(theme.borderRadius.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = theme.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.parseColor(theme.textPrimaryColor)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(ThemeManager.parseColor(theme.primaryColor))
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(ThemeManager.parseColor(theme.secondaryColor))
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(ThemeManager.parseColor(theme.accentColor))
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = ThemeManager.parseColor(theme.accentColor)
                )
            }
        }
    }
}

@Composable
fun ColorOption(label: String, color: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = color.uppercase(),
                fontSize = 12.sp,
                color = Color.Gray
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ThemeManager.parseColor(color))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}

// ColorPickerDialog теперь живёт в ColorPicker.kt — там настоящий HSV picker с alpha.