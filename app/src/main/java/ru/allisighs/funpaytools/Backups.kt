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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

data class BackupData(
    @SerializedName("version")
    val version: Int = 1,

    @SerializedName("created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @SerializedName("app_version")
    val appVersion: String = "1.2",


    @SerializedName("accounts")
    val accounts: AccountsData,


    @SerializedName("theme")
    val theme: AppTheme,


    @SerializedName("settings")
    val settings: FullBackupSettings
)

data class FullBackupSettings(

    @SerializedName("auto_response")
    val autoResponse: Boolean = false,

    @SerializedName("push_notifications")
    val pushNotifications: Boolean = true,

    @SerializedName("raise_enabled")
    val raiseEnabled: Boolean = false,

    @SerializedName("raise_interval")
    val raiseInterval: Int = 30,

    @SerializedName("auto_start_on_boot")
    val autoStartOnBoot: Boolean = false,


    @SerializedName("commands")
    val commands: List<AutoResponseCommand> = emptyList(),


    @SerializedName("greeting_settings")
    val greetingSettings: GreetingSettings = GreetingSettings(false, "", 0, false),


    @SerializedName("review_reply_settings")
    val reviewReplySettings: ReviewReplySettings = ReviewReplySettings(),


    @SerializedName("auto_refund_settings")
    val autoRefundSettings: AutoRefundSettings = AutoRefundSettings(),


    @SerializedName("order_confirm_settings")
    val orderConfirmSettings: OrderConfirmSettings = OrderConfirmSettings(),


    @SerializedName("message_templates")
    val messageTemplates: List<MessageTemplate> = emptyList(),

    @SerializedName("template_settings")
    val templateSettings: TemplateSettings = TemplateSettings(),

    @SerializedName("chat_folders")
    val chatFolders: List<ChatFolder> = emptyList(),

    @SerializedName("chat_labels")
    val chatLabels: List<ChatLabel> = emptyList(),

    @SerializedName("chat_label_assignments")
    val chatLabelAssignments: Map<String, List<String>> = emptyMap(),

    @SerializedName("busy_mode")
    val busyMode: BusyModeSettings = BusyModeSettings(),

    @SerializedName("dumper_settings")
    val dumperSettings: DumperSettings = DumperSettings()
)

class BackupManager(private val context: Context) {
    private val gson = Gson()

    fun createBackup(repository: FunPayRepository, currentTheme: AppTheme): BackupData {
        return BackupData(
            version = 1,
            createdAt = System.currentTimeMillis(),
            appVersion = "1.2",
            accounts = repository.getAccountsData(),
            theme = currentTheme,
            settings = FullBackupSettings(
                autoResponse = repository.getSetting("auto_response"),
                pushNotifications = repository.getSetting("push_notifications"),
                raiseEnabled = repository.getSetting("raise_enabled"),
                raiseInterval = repository.getRaiseInterval(),
                autoStartOnBoot = repository.getSetting("auto_start_on_boot"),
                commands = repository.getCommands(),
                greetingSettings = repository.getGreetingSettings(),
                reviewReplySettings = repository.getReviewReplySettings(),
                autoRefundSettings = repository.getAutoRefundSettings(),
                orderConfirmSettings = repository.getOrderConfirmSettings(),
                messageTemplates = repository.getMessageTemplates(),
                templateSettings = repository.getTemplateSettings(),
                chatFolders = ChatFolderManager.getFolders(context),
                chatLabels = ChatFolderManager.getLabels(context),
                chatLabelAssignments = ChatFolderManager.getChatLabels(context),
                busyMode = ChatFolderManager.getBusyMode(context),
                dumperSettings = repository.getDumperSettings()
            )
        )
    }

    fun exportBackup(backup: BackupData): String {
        return gson.toJson(backup)
    }

    fun importBackup(jsonString: String): Result<BackupData> {
        return try {
            val backup = gson.fromJson(jsonString, BackupData::class.java)
            Result.success(backup)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    fun applyBackup(
        backup: BackupData,
        repository: FunPayRepository,
        context: Context,
        onThemeChanged: (AppTheme) -> Unit
    ): Result<Unit> {
        return try {

            repository.saveAccountsData(backup.accounts)


            ThemeManager.saveTheme(context, backup.theme)
            onThemeChanged(backup.theme)


            val settings = backup.settings

            repository.setSetting("auto_response", settings.autoResponse)
            repository.setSetting("push_notifications", settings.pushNotifications)
            repository.setSetting("raise_enabled", settings.raiseEnabled)
            repository.setRaiseInterval(settings.raiseInterval)
            repository.setSetting("auto_start_on_boot", settings.autoStartOnBoot)

            repository.saveCommands(settings.commands)
            repository.saveGreetingSettings(settings.greetingSettings)
            repository.saveReviewReplySettings(settings.reviewReplySettings)
            repository.saveAutoRefundSettings(settings.autoRefundSettings)
            repository.saveOrderConfirmSettings(settings.orderConfirmSettings)
            repository.saveMessageTemplates(settings.messageTemplates)
            repository.saveTemplateSettings(settings.templateSettings)

            ChatFolderManager.saveFolders(context, settings.chatFolders)
            ChatFolderManager.saveLabels(context, settings.chatLabels)
            ChatFolderManager.saveChatLabels(context, settings.chatLabelAssignments)
            ChatFolderManager.saveBusyMode(context, settings.busyMode)
            repository.saveDumperSettings(settings.dumperSettings)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        return "FunPayTools_Backup_${dateFormat.format(Date())}.json"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(
    navController: NavController,
    currentTheme: AppTheme,

    repository: FunPayRepository,
    onThemeChanged: (AppTheme) -> Unit
) {
    val context = LocalContext.current
    val backupManager = remember { BackupManager(context) }

    var showExportSuccess by remember { mutableStateOf(false) }
    var showImportSuccess by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }


    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            try {

                val backup = backupManager.createBackup(repository, currentTheme)
                val json = backupManager.exportBackup(backup)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }

                isExporting = false
                showExportSuccess = true
            } catch (e: Exception) {
                isExporting = false
                showError = "Ошибка экспорта: ${e.message}"
            }
        } ?: run {
            isExporting = false
        }
    }


    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val json = reader.readText()
                reader.close()

                val result = backupManager.importBackup(json)
                result.onSuccess { backup ->

                    backupManager.applyBackup(backup, repository, context, onThemeChanged).onSuccess {
                        isImporting = false
                        showImportSuccess = true
                    }.onFailure { error ->
                        isImporting = false
                        showError = "Ошибка применения: ${error.message}"
                    }
                }.onFailure { error ->
                    isImporting = false
                    showError = "Неверный формат файла: ${error.message}"
                }
            } catch (e: Exception) {
                isImporting = false
                showError = "Ошибка чтения файла: ${e.message}"
            }
        } ?: run {
            isImporting = false
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Бэкапы и Экспорт",
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        fontWeight = FontWeight.Bold
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                item {
                    Text(
                        "Управление данными",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "Экспортируйте настройки для переноса на другое устройство или делитесь ими с друзьями",
                        fontSize = 14.sp,
                        color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                        lineHeight = 20.sp
                    )
                }


                item {
                    AnimatedBackupCard(
                        title = "Экспорт настроек",
                        description = "Сохранить все настройки, аккаунты и темы в файл",
                        icon = Icons.Default.Upload,
                        accentColor = ThemeManager.parseColor(currentTheme.accentColor),
                        surfaceColor = ThemeManager.parseColor(currentTheme.surfaceColor),
                        textPrimaryColor = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        textSecondaryColor = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                        containerOpacity = currentTheme.containerOpacity,
                        borderRadius = currentTheme.borderRadius,
                        isLoading = isExporting,
                        onClick = {
                            isExporting = true
                            exportLauncher.launch(backupManager.getBackupFileName())
                        }
                    )
                }


                item {
                    AnimatedBackupCard(
                        title = "Импорт настроек",
                        description = "Восстановить настройки из файла бэкапа",
                        icon = Icons.Default.Download,
                        accentColor = ThemeManager.parseColor("#00C853"),
                        surfaceColor = ThemeManager.parseColor(currentTheme.surfaceColor),
                        textPrimaryColor = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        textSecondaryColor = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                        containerOpacity = currentTheme.containerOpacity,
                        borderRadius = currentTheme.borderRadius,
                        isLoading = isImporting,
                        onClick = {
                            isImporting = true
                            importLauncher.launch(arrayOf("application/json"))
                        }
                    )
                }


                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = ThemeManager.parseColor(currentTheme.surfaceColor)
                                .copy(alpha = currentTheme.containerOpacity)
                        ),
                        shape = RoundedCornerShape(currentTheme.borderRadius.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = ThemeManager.parseColor(currentTheme.accentColor).copy(alpha = 0.7f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Что сохраняется?",
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                BackupInfoItem("• Все аккаунты и токены", currentTheme)
                                BackupInfoItem("• Тема и кастомизация", currentTheme)
                                BackupInfoItem("• Настройки уведомлений", currentTheme)
                                BackupInfoItem("• Всё что только можно", currentTheme)
                            }
                        }
                    }
                }


                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = ThemeManager.parseColor("#FF6D00")
                                .copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(currentTheme.borderRadius.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = ThemeManager.parseColor("#FF6D00"),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Безопасность",
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeManager.parseColor("#FF6D00"),
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Файл бэкапа содержит конфиденциальную информацию (токены, API ключи). Храните его в безопасном месте и не передавайте незнакомым людям.",
                                    color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }


            AnimatedVisibility(
                visible = showExportSuccess,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                SuccessSnackbar(
                    message = "Настройки успешно экспортированы!",
                    accentColor = ThemeManager.parseColor(currentTheme.accentColor),
                    onDismiss = { showExportSuccess = false }
                )
            }

            AnimatedVisibility(
                visible = showImportSuccess,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                SuccessSnackbar(
                    message = "Настройки успешно импортированы! Перезапустите приложение.",
                    accentColor = ThemeManager.parseColor("#00C853"),
                    onDismiss = { showImportSuccess = false }
                )
            }

            AnimatedVisibility(
                visible = showError != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                ErrorSnackbar(
                    message = showError ?: "",
                    onDismiss = { showError = null }
                )
            }
        }
    }


    LaunchedEffect(showExportSuccess) {
        if (showExportSuccess) {
            kotlinx.coroutines.delay(3000)
            showExportSuccess = false
        }
    }

    LaunchedEffect(showImportSuccess) {
        if (showImportSuccess) {
            kotlinx.coroutines.delay(4000)
            showImportSuccess = false
        }
    }

    LaunchedEffect(showError) {
        if (showError != null) {
            kotlinx.coroutines.delay(4000)
            showError = null
        }
    }
}

@Composable
private fun AnimatedBackupCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    surfaceColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    containerOpacity: Float,
    borderRadius: Int,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isLoading) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(enabled = !isLoading, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = surfaceColor.copy(alpha = containerOpacity)
        ),
        shape = RoundedCornerShape(borderRadius.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isLoading) 8.dp else 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.3f),
                                accentColor.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = accentColor,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = textPrimaryColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    fontSize = 13.sp,
                    color = textSecondaryColor,
                    lineHeight = 18.sp
                )
            }

            if (!isLoading) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = textSecondaryColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun BackupInfoItem(text: String, theme: AppTheme) {
    Text(
        text,
        fontSize = 13.sp,
        color = ThemeManager.parseColor(theme.textSecondaryColor),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun SuccessSnackbar(
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
private fun ErrorSnackbar(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor("#F44336").copy(alpha = 0.95f)
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
                Icons.Default.Error,
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