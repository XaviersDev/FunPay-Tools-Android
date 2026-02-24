/*
 * ПРАВИЛЬНЫЙ SettingsScreen.kt - БЕЗ КРАШЕЙ
 *
 * ВАЖНО: Удали СТАРЫЙ SettingsScreen из MainActivity.kt!
 * Оставь только ЭТОТ файл
 */

package ru.allisighs.funpaytools

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    repository: FunPayRepository,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showGoldenKeyDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Настройки",
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Назад",
                            tint = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ThemeManager.parseColor(currentTheme.surfaceColor)
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                
                item {
                    SectionHeader(
                        title = "Аккаунт",
                        icon = Icons.Default.Person,
                        theme = currentTheme
                    )
                }

                item {
                    SettingsCard(
                        theme = currentTheme,
                        content = {
                            ModernSettingsItem(
                                title = "Сменить Golden Key",
                                description = "Обновить токен авторизации",
                                icon = Icons.Default.Key,
                                iconColor = ThemeManager.parseColor("#FFD700"),
                                theme = currentTheme,
                                onClick = {
                                    
                                    navController.navigate("auth_method")
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = ThemeManager.parseColor(currentTheme.textSecondaryColor)
                                    .copy(alpha = 0.1f)
                            )

                            ModernSettingsItem(
                                title = "Управление аккаунтами",
                                description = "Добавить или удалить аккаунты",
                                icon = Icons.Default.AccountCircle,
                                iconColor = ThemeManager.parseColor(currentTheme.accentColor),
                                theme = currentTheme,
                                onClick = {
                                    
                                    navController.navigate("accounts")
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = ThemeManager.parseColor(currentTheme.textSecondaryColor).copy(alpha = 0.1f)
                            )

                            ModernSettingsItem(
                                title = if (LicenseManager.isProActive()) "💎 PRO активен" else "🔑 Лицензия и тарифы",
                                description = if (LicenseManager.isProActive()) "Управление подпиской" else "Получить доступ к PRO функциям",
                                icon = Icons.Default.Stars,
                                iconColor = Color(0xFFFFD700),
                                theme = currentTheme,
                                onClick = {
                                    navController.navigate("donations")
                                }
                            )
                        }
                    )
                }

                
                item {
                    SectionHeader(
                        title = "Персонализация",
                        icon = Icons.Default.Palette,
                        theme = currentTheme
                    )
                }

                item {
                    SettingsCard(
                        theme = currentTheme,
                        content = {
                            ModernSettingsItem(
                                title = "Выбрать тему",
                                description = "Готовые цветовые схемы",
                                icon = Icons.Default.ColorLens,
                                iconColor = ThemeManager.parseColor("#E91E63"),
                                theme = currentTheme,
                                onClick = {
                                    
                                    navController.navigate("theme_selector")
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = ThemeManager.parseColor(currentTheme.textSecondaryColor)
                                    .copy(alpha = 0.1f)
                            )
                            ModernSettingsItem(
                                title = "Кастомизация темы",
                                description = "Создать уникальный дизайн",
                                icon = Icons.Default.Edit,
                                iconColor = ThemeManager.parseColor("#9C27B0"),
                                theme = currentTheme,
                                onClick = {
                                    
                                    navController.navigate("theme_customization")
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = ThemeManager.parseColor(currentTheme.textSecondaryColor)
                                    .copy(alpha = 0.1f)
                            )
                            ModernSettingsItem(
                                title = "Виджеты",
                                description = "Настройка виджетов рабочего стола",
                                icon = Icons.Default.Widgets,
                                iconColor = ThemeManager.parseColor("#00E676"),
                                theme = currentTheme,
                                onClick = {
                                    navController.navigate("widgets_settings")
                                }
                            )
                        }
                    )
                }

                
                item {
                    SectionHeader(
                        title = "Данные",
                        icon = Icons.Default.Storage,
                        theme = currentTheme
                    )
                }

                item {
                    SettingsCard(
                        theme = currentTheme,
                        content = {
                            ModernSettingsItem(
                                title = "Бэкапы и экспорт",
                                description = "Сохранить или восстановить данные",
                                icon = Icons.Default.Backup,
                                iconColor = ThemeManager.parseColor("#00BCD4"),
                                theme = currentTheme,
                                showBadge = true,
                                badgeText = "NEW",
                                onClick = {
                                    navController.navigate("backups")
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = ThemeManager.parseColor(currentTheme.textSecondaryColor)
                                    .copy(alpha = 0.1f)
                            )

                            ModernSettingsItem(
                                title = "Очистить данные",
                                description = "Удалить все настройки и аккаунты",
                                icon = Icons.Default.DeleteForever,
                                iconColor = ThemeManager.parseColor("#F44336"),
                                theme = currentTheme,
                                onClick = { showDeleteConfirmDialog = true }
                            )
                        }
                    )
                }

                
                item {
                    SectionHeader(
                        title = "Информация",
                        icon = Icons.Default.Info,
                        theme = currentTheme
                    )
                }

                item {
                    SettingsCard(
                        theme = currentTheme,
                        content = {
                            ModernSettingsItem(
                                title = "Поддержка",
                                description = "Связаться с разработчиком",
                                icon = Icons.Default.Support,
                                iconColor = ThemeManager.parseColor("#4CAF50"),
                                theme = currentTheme,
                                onClick = {
                                    
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/AlliSighs"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Не удалось открыть Telegram",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = ThemeManager.parseColor(currentTheme.textSecondaryColor)
                                    .copy(alpha = 0.1f)
                            )

                            InfoRow(
                                label = "Версия приложения",
                                value = "1.2.4",
                                theme = currentTheme
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = ThemeManager.parseColor(currentTheme.textSecondaryColor)
                                    .copy(alpha = 0.1f)
                            )

                            InfoRow(
                                label = "Разработчик",
                                value = "XaviersDev",
                                theme = currentTheme
                            )
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            
            AnimatedVisibility(
                visible = showSuccessMessage,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                SuccessMessage(
                    message = successMessage,
                    accentColor = ThemeManager.parseColor(currentTheme.accentColor),
                    onDismiss = { showSuccessMessage = false }
                )
            }
        }
    }

    
    if (showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            currentTheme = currentTheme,
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                scope.launch {
                    
                    repository.clearAllData()

                    
                    val defaultTheme = ThemeManager.defaultThemes[0]
                    ThemeManager.saveTheme(context, defaultTheme)
                    onThemeChange(defaultTheme)

                    
                    showDeleteConfirmDialog = false

                    
                    navController.navigate("welcome") {
                        
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        )
    }

    
    LaunchedEffect(showSuccessMessage) {
        if (showSuccessMessage) {
            kotlinx.coroutines.delay(3000)
            showSuccessMessage = false
        }
    }
}





@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    theme: AppTheme
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = ThemeManager.parseColor(theme.accentColor),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = ThemeManager.parseColor(theme.accentColor),
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun SettingsCard(
    theme: AppTheme,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor)
                .copy(alpha = theme.containerOpacity)
        ),
        shape = RoundedCornerShape(theme.borderRadius.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun ModernSettingsItem(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    theme: AppTheme,
    showBadge: Boolean = false,
    badgeText: String = "",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            iconColor.copy(alpha = 0.25f),
                            iconColor.copy(alpha = 0.1f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = ThemeManager.parseColor(theme.textPrimaryColor)
                )
                if (showBadge) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(badgeText, iconColor)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                description,
                fontSize = 13.sp,
                color = ThemeManager.parseColor(theme.textSecondaryColor),
                lineHeight = 18.sp
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    theme: AppTheme
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = ThemeManager.parseColor(theme.textSecondaryColor)
        )
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = ThemeManager.parseColor(theme.textPrimaryColor)
        )
    }
}

@Composable
private fun SuccessMessage(
    message: String,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                message,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    currentTheme: AppTheme,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = ThemeManager.parseColor("#F44336"),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Удалить все данные?",
                fontWeight = FontWeight.Bold,
                color = ThemeManager.parseColor(currentTheme.textPrimaryColor)
            )
        },
        text = {
            Text(
                "Это действие удалит ВСЕ аккаунты, настройки, темы и сообщения. Данные невозможно восстановить. Рекомендуем создать бэкап перед удалением.",
                color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThemeManager.parseColor("#F44336")
                )
            ) {
                Text("Удалить всё", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Отмена",
                    color = ThemeManager.parseColor(currentTheme.textSecondaryColor)
                )
            }
        },
        containerColor = ThemeManager.parseColor(currentTheme.surfaceColor)
    )
}