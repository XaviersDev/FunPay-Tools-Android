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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCustomizationScreen(
    currentTheme: AppTheme,
    onThemeChanged: (AppTheme) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var selectedTheme by remember { mutableStateOf(currentTheme) }
    var amoledMode by remember { mutableStateOf(ThemeManager.isAmoledMode(context)) }
    var showColorPicker by remember { mutableStateOf(false) }
    var editingColor by remember { mutableStateOf("") }

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
                                    selectedTheme = updated
                                    ThemeManager.saveTheme(context, updated)
                                    onThemeChanged(updated)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ThemeManager.parseColor(selectedTheme.accentColor),
                                    checkedTrackColor = ThemeManager.parseColor(selectedTheme.accentColor).copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }

            
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
                    isSelected = theme.name == selectedTheme.name && !amoledMode,
                    onClick = {
                        amoledMode = false
                        ThemeManager.setAmoledMode(context, false)
                        selectedTheme = theme
                        ThemeManager.saveTheme(context, theme)
                        onThemeChanged(theme)
                    }
                )
            }

            
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
                            editingColor = "primary"
                            showColorPicker = true
                        }
                        ColorOption("Акцент", selectedTheme.accentColor) {
                            editingColor = "accent"
                            showColorPicker = true
                        }
                        if (!amoledMode) {
                            ColorOption("Фон", selectedTheme.backgroundColor) {
                                editingColor = "background"
                                showColorPicker = true
                            }
                            ColorOption("Поверхность", selectedTheme.surfaceColor) {
                                editingColor = "surface"
                                showColorPicker = true
                            }
                        }
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = when (editingColor) {
                "primary" -> selectedTheme.primaryColor
                "accent" -> selectedTheme.accentColor
                "background" -> selectedTheme.backgroundColor
                "surface" -> selectedTheme.surfaceColor
                else -> "#FFFFFF"
            },
            onColorSelected = { color ->
                val updated = when (editingColor) {
                    "primary" -> selectedTheme.copy(primaryColor = color)
                    "accent" -> selectedTheme.copy(accentColor = color)
                    "background" -> selectedTheme.copy(
                        backgroundColor = color,
                        originalBackgroundColor = color
                    )
                    "surface" -> selectedTheme.copy(
                        surfaceColor = color,
                        originalSurfaceColor = color
                    )
                    else -> selectedTheme
                }
                selectedTheme = updated
                ThemeManager.saveTheme(context, updated)
                onThemeChanged(updated)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

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

@Composable
fun ColorPickerDialog(
    initialColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val presetColors = listOf(
        "#651FFF", "#0091EA", "#00C853", "#FF6D00", "#EC407A", "#D500F9",
        "#FFFFFF", "#EEEEEE", "#B0B0B0", "#1A1A1A", "#050505", "#000000",
        "#FF5252", "#FF6E40", "#FFAB00", "#FFD600", "#AEEA00", "#00E676",
        "#00E5FF", "#00B0FF", "#2979FF", "#3D5AFE", "#651FFF", "#D500F9"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите цвет") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presetColors) { color ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ThemeManager.parseColor(color))
                            .border(
                                if (color == initialColor) 2.dp else 1.dp,
                                if (color == initialColor) Color.White else Color.White.copy(alpha = 0.3f),
                                CircleShape
                            )
                            .clickable { onColorSelected(color) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}