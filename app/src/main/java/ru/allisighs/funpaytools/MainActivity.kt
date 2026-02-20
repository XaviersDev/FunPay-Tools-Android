
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

import android.Manifest
import android.app.Activity
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

data class ParsedMessage(
    val id: String,
    val author: String,
    val text: String,
    val isMe: Boolean,
    val time: String,
    val imageUrl: String? = null,
    val isSystem: Boolean = false,
    val isAdmin: Boolean = false,
    val badge: String? = null,
    val links: List<MessageLink> = emptyList()
)

data class MessageLink(
    val text: String,
    val url: String,
    val type: LinkType
)

enum class LinkType {
    ORDER, USER, EXTERNAL
}

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseDatabase.getInstance().setPersistenceEnabled(false)
        Stats.init(this)
        Stats.setOnline()
        Ads.init(this)
        LicenseManager.init(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val repository = FunPayRepository(this)
        val startDest = if (repository.hasAuth()) "dashboard" else "welcome"

        setContent {
            var currentTheme by remember { mutableStateOf(ThemeManager.loadTheme(this)) }

            MaterialTheme(colorScheme = DarkColorScheme) {
                FunPayToolsApp(startDest, repository, currentTheme) { newTheme ->
                    currentTheme = newTheme
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Stats.setOffline()
    }
}



@Composable
fun FunPayToolsApp(
    startDestination: String,
    repository: FunPayRepository,
    currentTheme: AppTheme,
    onThemeChanged: (AppTheme) -> Unit
) {
    val navController = rememberNavController()

    PremiumInterceptor(navController, currentTheme)
    var showSplash by remember { mutableStateOf(true) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        if (showSplash) {
            SplashScreen(onTimeout = { showSplash = false }, theme = currentTheme)
        } else {
            Box(modifier = Modifier.fillMaxSize().background(AppGradient)) {
                NavHost(navController = navController, startDestination = startDestination) {
                    composable("welcome") { WelcomeScreen(navController) }
                    composable("backups") {
                        BackupsScreen(
                            navController = navController,
                            currentTheme = currentTheme,
                            repository = repository,
                            onThemeChanged = { newTheme ->
                                onThemeChanged(newTheme)
                                ThemeManager.saveTheme(context, newTheme)
                            }
                        )
                    }
                    composable("rmthub_search") {
                        RMTHubScreen(navController, currentTheme)
                    }
                    composable("permissions") {
                        AppScaffold("Доступы", navController, currentTheme) { PermissionsScreen(navController, repository) }
                    }
                    composable("auth_method") {
                        AppScaffold("Вход", navController, currentTheme) { AuthMethodScreen(navController) }
                    }
                    composable("manual_login") {
                        AppScaffold("Через Golden Key", navController, currentTheme) { ManualLoginScreen(navController, repository) }
                    }
                    composable("web_login") {
                        AppScaffold("Через браузер", navController, currentTheme) { WebLoginScreen(navController, repository) }
                    }
                    composable("dashboard") { DashboardScreen(navController, repository, currentTheme, onThemeChanged) }

                    composable("donations") {
                        AppScaffold("Тарифы", navController, currentTheme) {
                            DonationScreen(currentTheme)
                        }
                    }

                    composable("lots") {
                        LotsScreen(
                            navController = navController,
                            repository = repository,
                            theme = currentTheme
                        )
                    }

                    composable("lot_edit/{lotId}") { backStackEntry ->
                        val lotId = backStackEntry.arguments?.getString("lotId") ?: ""
                        LotEditScreen(
                            lotId = lotId,
                            navController = navController,
                            repository = repository,
                            theme = currentTheme
                        )
                    }

                    composable("accounts") {
                        AccountsSelectorScreen(
                            navController = navController,
                            repository = repository,
                            currentTheme = currentTheme
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            navController = navController,
                            repository = repository,
                            currentTheme = currentTheme,
                            onThemeChange = { newTheme ->
                                onThemeChanged(newTheme)
                                ThemeManager.saveTheme(context, newTheme)
                            }
                        )
                    }

                    composable("theme_selector") {
                        ThemeSelectionScreen(
                            navController = navController,
                            currentTheme = currentTheme,
                            onThemeSelected = { newTheme ->
                                onThemeChanged(newTheme)
                                ThemeManager.saveTheme(context, newTheme)
                                navController.navigateUp()
                            }
                        )
                    }

                    composable("theme_customization") {
                        ThemeCustomizationScreen(
                            currentTheme = currentTheme,
                            onThemeChanged = { newTheme ->
                                onThemeChanged(newTheme)
                                ThemeManager.saveTheme(context, newTheme)
                            },
                            onBack = { navController.navigateUp() }
                        )
                    }

                    composable("widgets_settings") {
                        WidgetsScreen(
                            navController = navController,
                            currentTheme = currentTheme
                        )
                    }

                    composable("chat/{chatId}/{username}") { backStackEntry ->
                        val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                        val username = backStackEntry.arguments?.getString("username") ?: ""

                        AppScaffold(username, navController, currentTheme) {
                            ChatDetailScreen(chatId, username, repository, currentTheme, navController)
                        }
                    }

                    composable("order/{orderId}") { backStackEntry ->
                        val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                        AppScaffold("Заказ #$orderId", navController, currentTheme) { OrderScreen(orderId, repository, currentTheme) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(title: String, navController: NavController, theme: AppTheme, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title, color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ThemeManager.parseColor(theme.textPrimaryColor))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) { content() }
    }
}

@Composable
fun WelcomeScreen(navController: NavController) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(100.dp).background(PurpleAccent.copy(alpha = 0.3f), CircleShape))
                        Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "Logo", modifier = Modifier.size(64.dp), tint = PurpleAccent)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Text("FunPay Tools", fontSize = 36.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Ваш помощник для FunPay", fontSize = 16.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(48.dp))
                    Button(onClick = { navController.navigate("permissions") }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)) { Text("Начать") }
                }
            }
        }
    }
}

@Composable
fun PermissionsScreen(navController: NavController, repository: FunPayRepository) {
    val context = LocalContext.current
    val hasAuth = repository.hasAuth()
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    val isBatteryOptimizationDisabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(
            text = "КРИТИЧЕСКИ ВАЖНО",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = Color.Red
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Система убивает приложения при выключенном экране. Чтобы автоподнятие и автоответ работали 24/7, вы ОБЯЗАНЫ настроить это вручную.",
            fontSize = 14.sp,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier
            .liquidGlass()
            .clickable {
                openBatterySettings(context)
            }
            .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isBatteryOptimizationDisabled) Icons.Default.BatteryAlert else Icons.Default.BatteryAlert,
                    null,
                    tint = if (isBatteryOptimizationDisabled) Color.Yellow else Color(0xFFFF9800),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Оптимизация батареи",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Нажмите → выберите 'Не оптимизировать' или 'Нет ограничений', или перейдите в 'Использование батареи', отключите оптимизацию батареи, разрешите приложению работать в фоне. На каждом ОС названия настроек выглядит по-разному.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier
            .liquidGlass()
            .clickable {
                openAutoStartSettings(context)
            }
            .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SettingsPower,
                    null,
                    tint = PurpleAccent,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Автозапуск",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Включите для FunPay Tools",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E).copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "📋 Инструкция по производителям:",
                    color = Color(0xFF90CAF9),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))

                val manufacturer = Build.MANUFACTURER.lowercase()
                val instructions = when {
                    manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
                        "Xiaomi/MIUI:\n• Батарея → Нет ограничений\n• Контроль активности → Нет ограничений\n• Автозапуск → Включить"
                    manufacturer.contains("oneplus") || manufacturer.contains("oplus") ->
                        "OnePlus/OxygenOS:\n• Настройки батареи → Не оптимизировать\n• Фоновая активность → Неограниченный доступ\n• Автозапуск → Включить"
                    manufacturer.contains("oppo") || manufacturer.contains("realme") ->
                        "Oppo/Realme:\n• Батарея → Не оптимизировать\n• Автозапуск → Включить"
                    manufacturer.contains("samsung") ->
                        "Samsung:\n• Батарея → Не ограничивать\n• Спящий режим → Удалить из списка\n• Автозапуск → Включить"
                    manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                        "Huawei/Honor:\n• Диспетчер приложений → Защищённые\n• Батарея → Игнорировать оптимизацию"
                    manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
                        "Vivo/iQOO:\n• Батарея → Высокое энергопотребление → Включить\n• Автозапуск → Включить"
                    else ->
                        "Общие настройки:\n• Оптимизация батареи → Не оптимизировать\n• Фоновая работа → Разрешить\n• Автозапуск → Включить"
                }

                Text(
                    instructions,
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (hasAuth) {
                    navController.popBackStack()
                } else {
                    navController.navigate("auth_method")
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Я всё настроил, Продолжить")
        }
    }
}

fun openBatterySettings(context: Context) {
    val manufacturer = Build.MANUFACTURER.lowercase()

    val intents = when {
        
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> listOf(
            Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity")),
            Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")),
            Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").addCategory(Intent.CATEGORY_DEFAULT),
            Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.PowerSaverSettingsActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.APP_PERM_EDITOR").addCategory(Intent.CATEGORY_DEFAULT),
            Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.PowerRankActivity")),
            Intent().setComponent(ComponentName("com.xiaomi.smarthome", "com.xiaomi.smarthome.settings.SettingsActivity"))
        )

        
        manufacturer.contains("oneplus") || manufacturer.contains("oplus") -> listOf(
            Intent().setComponent(ComponentName("com.oplus.battery", "com.oplus.battery.OplusBatteryOptimizeActivity")),
            Intent().setComponent(ComponentName("com.oplus.battery", "com.oplus.battery.optimizeapp.BatteryOptimizeAppListActivity")),
            Intent().apply { action = "android.settings.APP_BATTERY_SETTINGS"; data = Uri.parse("package:${context.packageName}") },
            Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")),
            Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
            Intent().setComponent(ComponentName("com.oneplus.oppoguardelf", "com.oneplus.oppoguardelf.powermonitor.PowerMonitorActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$HighPowerApplicationsActivity"))
        )

        
        manufacturer.contains("oppo") || manufacturer.contains("realme") -> listOf(
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.appmanager.AppManagerActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity")),
            Intent().setComponent(ComponentName("com.realme.gamespace", "com.realme.gamespace.batterycenter.BatteryCenterActivity"))
        )

        
        manufacturer.contains("samsung") -> listOf(
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLELISTACTIVITY").putExtra("batteryOptimization", true),
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.usage.SleepingAppsActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.usage.DeepSleepingAppsActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.usage.NeverSleepingAppsActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.game.gamehome", "com.samsung.android.game.gamehome.gametools.settings.GameBoosterSettingsActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"))
        )

        
        manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.power.ui.HwPowerManagerActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.mainscreen.MainScreenActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.MainActivity"))
        )

        
        manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
            Intent().setComponent(ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(ComponentName("com.vivo.abe", "com.vivo.abe.BatteryOptimizeActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")),
            Intent().setComponent(ComponentName("com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity"))
        )

        
        manufacturer.contains("asus") -> listOf(
            Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")),
            Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")),
            Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings")),
            Intent().setComponent(ComponentName("com.asus.gamebox", "com.asus.gamebox.settings.SettingsActivity")),
            Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity"))
        )

        
        manufacturer.contains("nokia") || manufacturer.contains("hmd") -> listOf(
            Intent().setComponent(ComponentName("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity")),
            Intent().setComponent(ComponentName("com.nokia.battery", "com.nokia.battery.BatteryActivity")),
            Intent().setComponent(ComponentName("com.evenwell.batteryprotect", "com.evenwell.batteryprotect.setting.BatteryProtectSettings")),
            Intent().setComponent(ComponentName("com.evenwell.emm", "com.evenwell.emm.ui.MainActivity"))
        )

        
        manufacturer.contains("lenovo") || manufacturer.contains("motorola") || manufacturer.contains("moto") -> listOf(
            Intent().setComponent(ComponentName("com.motorola.batterycare", "com.motorola.batterycare.MainActivity")),
            Intent().setComponent(ComponentName("com.motorola.actions", "com.motorola.actions.backgroundapp.BackgroundAppActivity")),
            Intent().setComponent(ComponentName("com.lenovo.security", "com.lenovo.security.purebackground.PureBackgroundActivity")),
            Intent().setComponent(ComponentName("com.lenovo.powersetting", "com.lenovo.powersetting.ui.Settings\$HighPowerApplicationsActivity"))
        )

        
        manufacturer.contains("meizu") -> listOf(
            Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC")),
            Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.SecurityMainActivity")),
            Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.powerui.PowerAppPermissionActivity"))
        )

        
        manufacturer.contains("lg") || manufacturer.contains("lge") -> listOf(
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$HighPowerApplicationsActivity")),
            Intent().setComponent(ComponentName("com.lge.systemservice", "com.lge.systemservice.BatteryActivity"))
        )

        
        manufacturer.contains("htc") -> listOf(
            Intent().setComponent(ComponentName("com.htc.pitroad", "com.htc.pitroad.landingpage.activity.LandingPageActivity")),
            Intent().setComponent(ComponentName("com.htc.settings.power", "com.htc.settings.power.PowerSettingsActivity"))
        )

        
        manufacturer.contains("sony") -> listOf(
            Intent().setComponent(ComponentName("com.sonymobile.cta", "com.sonymobile.cta.SomcCTAMainActivity")),
            Intent().setComponent(ComponentName("com.sonymobile.settings.stamina", "com.sonymobile.settings.stamina.StaminaActivity"))
        )

        
        manufacturer.contains("zte") -> listOf(
            Intent().setComponent(ComponentName("com.zte.powersavemode", "com.zte.powersavemode.PowerSaveSettingsActivity")),
            Intent().setComponent(ComponentName("com.zte.heartyservice", "com.zte.heartyservice.autorun.AppAutoRunManager"))
        )

        
        manufacturer.contains("google") -> listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") }
        )

        
        manufacturer.contains("nothing") -> listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )

        
        manufacturer.contains("tecno") || manufacturer.contains("infinix") || manufacturer.contains("itel") -> listOf(
            Intent().setComponent(ComponentName("com.transsion.systemmanager", "com.transsion.systemmanager.background.BackgroundManagerActivity")),
            Intent().setComponent(ComponentName("com.itel.powersaver", "com.itel.powersaver.PowerSaverActivity"))
        )

        
        manufacturer.contains("blackview") -> listOf(
            Intent().setComponent(ComponentName("com.dv.powermanager", "com.dv.powermanager.PowerManagerActivity")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )

        
        manufacturer.contains("ulefone") || manufacturer.contains("doogee") || manufacturer.contains("oukitel") -> listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )

        
        manufacturer.contains("fairphone") -> listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )

        
        else -> listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") },
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    var success = false
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            success = true

            val message = when {
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
                    "Найдите FunPay Tools → Батарея → Нет ограничений"
                manufacturer.contains("oneplus") || manufacturer.contains("oplus") ->
                    "Выберите 'Не оптимизировать' или 'Неограниченный доступ'"
                manufacturer.contains("samsung") ->
                    "Снимите все ограничения и удалите из 'Спящих приложений'"
                manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                    "Добавьте в 'Защищённые приложения'"
                manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
                    "Включите 'Высокое энергопотребление'"
                else ->
                    "Найдите FunPay Tools и выберите 'Не оптимизировать' или 'Нет ограничений'"
            }

            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            return
        } catch (e: Exception) {
            
        }
    }

    if (!success) {
        Toast.makeText(context, "Открываем общие настройки. Найдите 'Батарея' → 'Оптимизация' → FunPay Tools", Toast.LENGTH_LONG).show()
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть настройки: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

fun openAutoStartSettings(context: Context) {
    val manufacturer = Build.MANUFACTURER.lowercase()

    val intents = when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.xiaomi.smarthome", "com.xiaomi.smarthome.settings.SettingsActivity"))
        )

        manufacturer.contains("oneplus") || manufacturer.contains("oplus") -> listOf(
            Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"))
        )

        manufacturer.contains("oppo") || manufacturer.contains("realme") -> listOf(
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"))
        )

        manufacturer.contains("samsung") -> listOf(
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"))
        )

        manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
        )

        manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"))
        )

        manufacturer.contains("asus") -> listOf(
            Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"))
        )

        manufacturer.contains("meizu") -> listOf(
            Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC"))
        )

        else -> emptyList()
    }

    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, "Включите переключатель для FunPay Tools", Toast.LENGTH_LONG).show()
            return
        } catch (e: Exception) {
            
        }
    }

    Toast.makeText(context, "На вашем устройстве нет отдельных настроек автозапуска (это нормально для чистого Android)", Toast.LENGTH_LONG).show()
}

@Composable
fun AuthMethodScreen(navController: NavController) {
    val context = LocalContext.current
    val backupManager = remember { BackupManager(context) }
    val repository = remember { FunPayRepository(context) }

    
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
                    
                    backupManager.applyBackup(
                        backup = backup,
                        repository = repository,
                        context = context,
                        onThemeChanged = { /* Тема обновится сама при навигации */ }
                    ).onSuccess {
                        Toast.makeText(context, "Бэкап восстановлен! Входим...", Toast.LENGTH_SHORT).show()
                        navController.navigate("dashboard") {
                            popUpTo(0) { inclusive = true }
                        }
                    }.onFailure {
                        Toast.makeText(context, "Ошибка применения: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }.onFailure {
                    Toast.makeText(context, "Неверный формат файла", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка чтения: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E).copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF66BB6A), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Спасибо, что выбрали FunPay Tools, Дальше - больше.",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }

        AuthCard(Icons.Default.Key, "Ввести ключ", "Golden Key") {
            navController.navigate("manual_login")
        }

        Spacer(modifier = Modifier.height(16.dp))

        AuthCard(Icons.Default.Public, "Добавить аккаунт", "Войти через сайт FunPay") {
            navController.navigate("web_login")
        }

        Spacer(modifier = Modifier.height(16.dp))

        
        AuthCard(Icons.Default.UploadFile, "Войти через файл импорта", "Если у вас есть сохраненный бэкап приложения .json") {
            importLauncher.launch(arrayOf("application/json"))
        }
    }
}
@Composable
fun AuthCard(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().liquidGlass().clickable(onClick = onClick).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = PurpleAccent, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(desc, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
fun ManualLoginScreen(navController: NavController, repository: FunPayRepository) {
    var key by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Golden Key") })
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            if (key.isNotBlank()) {
                repository.saveGoldenKey(key)
                navController.navigate("dashboard") { popUpTo("welcome") { inclusive = true } }
            }
        }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)) { Text("Войти") }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(navController: NavController, repository: FunPayRepository) {
    val context = LocalContext.current
    var webView: WebView? = null
    var hasGoldenKey by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }

    
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    
    LaunchedEffect(Unit) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webView = this

                    
                    settings.apply {
                        
                        javaScriptEnabled = true

                        
                        domStorageEnabled = true
                        databaseEnabled = true

                        
                        useWideViewPort = true
                        loadWithOverviewMode = true

                        
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(false)

                        
                        cacheMode = WebSettings.LOAD_DEFAULT

                        
                        
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"

                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                    }

                    
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)

                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                    }

                    
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            currentUrl = url ?: ""

                            
                            val cookies = CookieManager.getInstance().getCookie(url)
                            hasGoldenKey = cookies?.contains("golden_key=") == true
                        }

                        
                        
                        
                    }

                    
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            
                        }
                    }

                    
                    loadUrl("https://funpay.com/account/login")
                }
            }
        )

        
        Card(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .fillMaxWidth(0.9f)
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                },
            colors = CardDefaults.cardColors(
                containerColor = ThemeManager.parseColor(ThemeManager.loadTheme(context).surfaceColor).copy(alpha = 0.95f)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Добавление аккаунта",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ThemeManager.parseColor(ThemeManager.loadTheme(context).textPrimaryColor)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Войдите в нужный аккаунт\n2. Пройдите капчу и 2FA если требуется\n3. Дождитесь полной загрузки страницы\n4. Нажмите кнопку внизу\n\n(Это окно можно двигать)",
                    fontSize = 13.sp,
                    color = ThemeManager.parseColor(ThemeManager.loadTheme(context).textSecondaryColor),
                    lineHeight = 18.sp
                )
            }
        }


        if (hasGoldenKey) {
            FloatingActionButton(
                onClick = {
                    val cookies = CookieManager.getInstance().getCookie(currentUrl)
                    if (cookies != null && cookies.contains("golden_key=")) {
                        val keyMatch = cookies.split(";").find { it.trim().startsWith("golden_key=") }
                        if (keyMatch != null) {
                            val goldenKey = keyMatch.substringAfter("golden_key=").trim()
                            if (goldenKey.isNotEmpty()) {
                                val sessionMatch = cookies.split(";").find { it.trim().startsWith("PHPSESSID=") }
                                val phpSessionId = sessionMatch?.substringAfter("PHPSESSID=")?.trim() ?: ""

                                
                                repository.addAccountFromWebLogin(goldenKey, phpSessionId)

                                
                                val allAccounts = repository.getAllAccounts()

                                Toast.makeText(context, "Аккаунт добавлен!", Toast.LENGTH_SHORT).show()

                                
                                if (allAccounts.isNotEmpty()) {
                                    
                                    
                                    if (allAccounts.size == 1) {
                                        navController.navigate("dashboard") {
                                            popUpTo(0) { inclusive = true } 
                                        }
                                    } else {
                                        
                                        navController.navigate("accounts") {
                                            popUpTo("accounts") { inclusive = true }
                                        }
                                    }
                                } else {
                                    
                                    Toast.makeText(context, "Ошибка сохранения!", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "Golden Key не найден. Попробуйте войти снова.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                containerColor = ThemeManager.parseColor(ThemeManager.loadTheme(context).accentColor),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Добавить этот аккаунт", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, repository: FunPayRepository, currentTheme: AppTheme, onThemeChanged: (AppTheme) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var chats by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    val logs by LogManager.logs.collectAsState()
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showThemeCustomization by remember { mutableStateOf(false) }

    
    var isLoadingChats by remember { mutableStateOf(true) }
    var hasLoadedOnce by remember { mutableStateOf(false) }

    
    val activeAccount = repository.getActiveAccount()
    val accountKey = activeAccount?.id ?: "none"

    DisposableEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(Intent(context, FunPayService::class.java))
        } else {
            context.startService(Intent(context, FunPayService::class.java))
        }

        onDispose { }
    }

    LaunchedEffect(Unit) {
        launch {
            updateInfo = repository.checkForUpdates()
        }
    }

    
    LaunchedEffect(accountKey) {
        
        isLoadingChats = true
        chats = emptyList()

        while (true) {
            try {
                chats = repository.getChats()
                if (!hasLoadedOnce) {
                    hasLoadedOnce = true
                }
                isLoadingChats = false
            } catch (e: Exception) {
                LogManager.addLog("❌ Ошибка загрузки чатов: ${e.message}")
                isLoadingChats = false
            }
            delay(5000)
        }
    }

    if (showThemeCustomization) {
        ThemeCustomizationScreen(
            currentTheme = currentTheme,
            onThemeChanged = onThemeChanged,
            onBack = { showThemeCustomization = false }
        )
    } else {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("FunPay Tools", color = ThemeManager.parseColor(currentTheme.textPrimaryColor), fontWeight = FontWeight.Bold) },
                    actions = {
                        
                        IconButton(onClick = {
                            scope.launch {
                                val unreadChats = chats.filter { it.isUnread }
                                if (unreadChats.isNotEmpty()) {
                                    Toast.makeText(context, "Читаю ${unreadChats.size} чатов...", Toast.LENGTH_SHORT).show()

                                    unreadChats.forEach { chat ->
                                        launch {
                                            repository.getChatHistory(chat.id)
                                        }
                                        delay(385)
                                    }

                                    chats = chats.map { it.copy(isUnread = false) }
                                    Toast.makeText(context, "Все чаты прочитаны!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Нет непрочитанных чатов", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "ReadAll",
                                tint = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                            )
                        }

                        
                        IconButton(onClick = { showThemeCustomization = true }) {
                            Icon(
                                imageVector = Icons.Default.ColorLens,
                                contentDescription = "Theme",
                                tint = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                            )
                        }

                        
                        IconButton(onClick = { navController.navigate("accounts") }) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Accounts",
                                tint = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                            )
                        }

                        
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/fptools"))
                            context.startActivity(intent)
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Telegram",
                                tint = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                            )
                        }

                        
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, "Settings", tint = ThemeManager.parseColor(currentTheme.textPrimaryColor))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ThemeManager.parseColor(currentTheme.surfaceColor))
                )
            },
            bottomBar = {
                NavigationBar(containerColor = ThemeManager.parseColor(currentTheme.surfaceColor)) {
                    listOf(
                        Triple(0, Icons.Default.Chat, "Чаты"),
                        Triple(1, Icons.Default.Tune, "Управление"),
                        Triple(2, Icons.Default.Person, "Профиль"),
                        Triple(3, Icons.Default.Support, "Поддержка"),
                        Triple(4, Icons.Default.Terminal, "Консоль")
                    ).forEach { (index, icon, label) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icon, null) },
                            label = {
                                Text(label, maxLines = 1, fontSize = 9.sp, softWrap = false)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ThemeManager.parseColor(currentTheme.accentColor),
                                selectedTextColor = ThemeManager.parseColor(currentTheme.accentColor),
                                indicatorColor = ThemeManager.parseColor(currentTheme.accentColor).copy(alpha = 0.2f),
                                unselectedIconColor = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                                unselectedTextColor = ThemeManager.parseColor(currentTheme.textSecondaryColor)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (updateInfo?.hasUpdate == true) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ThemeManager.parseColor(currentTheme.accentColor))
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo!!.htmlUrl))
                                context.startActivity(intent)
                            }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Доступна новая версия ${updateInfo!!.newVersion}! Нажмите, чтобы скачать.", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                when (selectedTab) {

                    0 -> ChatListView(chats, isLoadingChats && !hasLoadedOnce, navController, currentTheme)
                    1 -> ControlView(navController, repository, currentTheme)

                    2 -> ProfileScreen(
                        repository = repository,
                        theme = currentTheme,
                        onOpenTariffs = { navController.navigate("donations") },
                        onOpenLots = { navController.navigate("lots") },  
                        onLogout = {
                            navController.navigate("welcome") { popUpTo(0) }
                        }
                    )
                    3 -> SupportScreenView(repository, currentTheme) { selectedTab = 0 }
                    4 -> ConsoleView(logs, currentTheme, navController)
                }
            }
        }
    }
}

@Composable
fun ChatListView(
    chats: List<ChatItem>,
    isLoading: Boolean,
    navController: NavController,
    theme: AppTheme
) {
    val context = LocalContext.current
    var folders by remember { mutableStateOf(ChatFolderManager.getFolders(context)) }
    var labels by remember { mutableStateOf(ChatFolderManager.getLabels(context)) }
    var chatLabels by remember { mutableStateOf(ChatFolderManager.getChatLabels(context)) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var showManageFolders by remember { mutableStateOf(false) }
    var showManageLabels by remember { mutableStateOf(false) }

    val allFolderIds = remember(folders) { listOf(null) + folders.map { it.id } }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { allFolderIds.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        val id = allFolderIds.getOrNull(pagerState.currentPage)
        if (selectedFolderId != id) {
            selectedFolderId = id
        }
    }

    LaunchedEffect(selectedFolderId, folders) {
        val idx = allFolderIds.indexOf(selectedFolderId)
        if (idx >= 0 && pagerState.currentPage != idx) {
            pagerState.scrollToPage(idx)
        } else if (idx == -1) {
            selectedFolderId = null
        }
    }

    if (showManageFolders) {
        ManageFoldersDialog(
            folders = folders,
            onFoldersChanged = { folders = it; ChatFolderManager.saveFolders(context, it) },
            onDismiss = { showManageFolders = false },
            theme = theme
        )
    }

    if (showManageLabels) {
        ManageLabelsDialog(
            labels = labels,
            onLabelsChanged = { labels = it; ChatFolderManager.saveLabels(context, it) },
            onDismiss = { showManageLabels = false },
            theme = theme
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatFolderTabs(
            folders = folders,
            selectedFolderId = selectedFolderId,
            onFolderSelected = { selectedFolderId = it },
            onManageFolders = { showManageFolders = true },
            theme = theme
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageFolderId = allFolderIds.getOrNull(page)
            val filtered = remember(chats, pageFolderId, folders, chatLabels) {
                when (pageFolderId) {
                    null -> chats
                    "preset_unread" -> chats.filter { it.isUnread }
                    "preset_labeled" -> chats.filter { chatLabels[it.id]?.isNotEmpty() == true }
                    "preset_archived" -> chats.filter {
                        folders.firstOrNull { f -> f.id == "preset_archived" }?.chatIds?.contains(it.id) == true
                    }
                    else -> chats.filter {
                        folders.firstOrNull { f -> f.id == pageFolderId }?.chatIds?.contains(it.id) == true
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = ThemeManager.parseColor(theme.accentColor),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Загрузка чатов...", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 14.sp)
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ChatBubbleOutline, null, tint = ThemeManager.parseColor(theme.textSecondaryColor), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (pageFolderId != null) "Нет чатов в этой папке" else "Нет чатов",
                            color = ThemeManager.parseColor(theme.textSecondaryColor)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { chat ->
                        ChatItemView(
                            chat = chat,
                            navController = navController,
                            theme = theme,
                            labels = labels,
                            chatLabels = chatLabels,
                            folders = folders,
                            onChatLabelsChanged = { chatLabels = it; ChatFolderManager.saveChatLabels(context, it) },
                            onFoldersChanged = { folders = it; ChatFolderManager.saveFolders(context, it) },
                            onManageFolders = { showManageFolders = true },
                            onManageLabels = { showManageLabels = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatItemView(
    chat: ChatItem,
    navController: NavController,
    theme: AppTheme,
    labels: List<ChatLabel> = emptyList(),
    chatLabels: Map<String, List<String>> = emptyMap(),
    folders: List<ChatFolder> = emptyList(),
    onChatLabelsChanged: (Map<String, List<String>>) -> Unit = {},
    onFoldersChanged: (List<ChatFolder>) -> Unit = {},
    onManageFolders: () -> Unit = {},
    onManageLabels: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val assignedLabels = labels.filter { label ->
        chatLabels[chat.id]?.contains(label.id) == true
    }
    val isArchived = folders.find { it.id == "preset_archived" }?.chatIds?.contains(chat.id) == true

    var labelTooltip by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun showTooltip(name: String) {
        labelTooltip = name
        scope.launch {
            delay(1500)
            labelTooltip = null
        }
    }

    if (showMenu) {
        ChatItemMenu(
            chatId = chat.id,
            labels = labels,
            chatLabels = chatLabels,
            folders = folders,
            onAddLabel = { labelId ->
                val cur = (chatLabels[chat.id] ?: emptyList()).toMutableList()
                if (cur.contains(labelId)) cur.remove(labelId) else cur.add(labelId)
                onChatLabelsChanged(chatLabels + (chat.id to cur))
            },
            onAddToFolder = { fid ->
                onFoldersChanged(folders.map { f ->
                    if (f.id != fid) f else {
                        val ids = f.chatIds.toMutableList()
                        if (ids.contains(chat.id)) ids.remove(chat.id) else ids.add(chat.id)
                        f.copy(chatIds = ids)
                    }
                })
            },
            onArchiveToggle = {
                val archiveFolder = folders.find { it.id == "preset_archived" }
                if (archiveFolder == null) {
                    val newFolder = presetFolderDefs.find { it.id == "preset_archived" }!!
                        .copy(chatIds = listOf(chat.id))
                    onFoldersChanged(folders + newFolder)
                } else {
                    onFoldersChanged(folders.map { f ->
                        if (f.id != "preset_archived") f else {
                            val ids = f.chatIds.toMutableList()
                            if (ids.contains(chat.id)) ids.remove(chat.id) else ids.add(chat.id)
                            f.copy(chatIds = ids)
                        }
                    })
                }
            },
            isArchived = isArchived,
            onCreateFolder = { onManageFolders() },
            onManageLabels = { onManageLabels() },
            onDismiss = { showMenu = false },
            theme = theme
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate("chat/${chat.id}/${chat.username}") },
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity)
        ),
        shape = RoundedCornerShape(theme.borderRadius.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomCenter) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(48.dp)
                ) {
                    AsyncImage(
                        model = chat.avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Gray)
                            .clickable {
                                if (assignedLabels.isNotEmpty()) showTooltip(assignedLabels.first().name)
                            },
                        contentScale = ContentScale.Crop
                    )
                    if (assignedLabels.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            assignedLabels.take(4).forEachIndexed { index, label ->
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(ThemeManager.parseColor(label.color))
                                        .clickable { showTooltip(label.name) }
                                )
                                if (index < assignedLabels.take(4).lastIndex) Spacer(Modifier.width(2.dp))
                            }
                        }
                    }
                }
                // еще один аутизм
                if (labelTooltip != null) {
                    Box(
                        modifier = Modifier
                            .offset(y = (-54).dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(labelTooltip!!, color = Color.White, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chat.username,
                        fontWeight = FontWeight.Bold,
                        color = ThemeManager.parseColor(theme.textPrimaryColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Text(
                    chat.lastMessage,
                    color = ThemeManager.parseColor(theme.textSecondaryColor),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.date, fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                    Spacer(Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable { showMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            null,
                            tint = ThemeManager.parseColor(theme.textSecondaryColor),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                if (chat.isUnread) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ThemeManager.parseColor(theme.accentColor), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun ControlView(navController: NavController, repository: FunPayRepository, theme: AppTheme) {
    val context = LocalContext.current

    var showDialog by remember { mutableStateOf<String?>(null) }
    var autoResponse by remember { mutableStateOf(repository.getSetting("auto_response")) }
    var pushNotifications by remember { mutableStateOf(repository.getSetting("push_notifications")) }
    var raiseEnabled by remember { mutableStateOf(repository.getSetting("raise_enabled")) }
    var raiseInterval by remember { mutableIntStateOf(repository.getRaiseInterval()) }
    var confirmSettings by remember { mutableStateOf(repository.getOrderConfirmSettings()) }
    var reviewSettings by remember { mutableStateOf(repository.getReviewReplySettings()) }
    var refundSettings by remember { mutableStateOf(repository.getAutoRefundSettings()) }
    var greetingSettings by remember { mutableStateOf(repository.getGreetingSettings()) }
    var autoStartOnBoot by remember { mutableStateOf(repository.getSetting("auto_start_on_boot")) }
    var showReviewAiGate by remember { mutableStateOf(false) }
    var busySettings by remember { mutableStateOf(ChatFolderManager.getBusyMode(context)) }
    var showBusyDialog by remember { mutableStateOf(false) }
    var dumperSettings by remember { mutableStateOf(repository.getDumperSettings()) }

    fun isAllowed(f: String) = !busySettings.enabled || when (f) {
        "raise" -> busySettings.keepRaise
        "autoResp" -> busySettings.keepAutoResponse
        "greeting" -> busySettings.keepGreeting
        else -> false
    }

    if (showReviewAiGate) {
        PremiumDialog(
            feature = PremiumFeature.REVIEW_AI,
            theme = theme,
            onDismiss = { showReviewAiGate = false },
            onUnlocked = { showReviewAiGate = false }
        )
    }

    if (showBusyDialog) {
        BusyModeDialog(
            settings = busySettings,
            onSave = { busySettings = it; ChatFolderManager.saveBusyMode(context, it) },
            onDismiss = { showBusyDialog = false },
            theme = theme
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { navController.navigate("rmthub_search") },
                colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity)),
                shape = RoundedCornerShape(theme.borderRadius.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFF288CD7), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Глобальный поиск пользователей", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
                        Text("Поиск всех пользователей FunPay по нику через API RMTHub.com", fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = ThemeManager.parseColor(theme.textSecondaryColor))
                }
            }
        }

        item {
            SettingCard("Push-уведомления", "Уведомления о новых сообщениях", pushNotifications, Icons.Default.Notifications, theme) {
                pushNotifications = !pushNotifications
                repository.setSetting("push_notifications", pushNotifications)
            }
        }

        item {
            BusyModeCard(
                settings = busySettings,
                onToggle = {
                    val nowEnabled = !busySettings.enabled
                    val u = busySettings.copy(
                        enabled = nowEnabled,
                        enabledAt = if (nowEnabled) System.currentTimeMillis() else 0L
                    )
                    busySettings = u
                    ChatFolderManager.saveBusyMode(context, u)
                },
                onConfigure = { showBusyDialog = true },
                theme = theme
            )
        }

        item {
            SettingCard("Автозапуск после перезагрузки", "Автоматически запускать сервис после включения телефона", autoStartOnBoot, Icons.Default.Power, theme) {
                autoStartOnBoot = !autoStartOnBoot
                repository.setSetting("auto_start_on_boot", autoStartOnBoot)
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().alpha(if (!isAllowed("raise")) 0.4f else 1f)) {
                Column {
                    SettingCard("Автоподнятие", "Поднимать лоты автоматически", raiseEnabled, Icons.Default.TrendingUp, theme) {
                        if (isAllowed("raise")) {
                            raiseEnabled = !raiseEnabled
                            repository.setSetting("raise_enabled", raiseEnabled)
                        }
                    }
                    if (raiseEnabled) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("Интервал: $raiseInterval мин", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 14.sp)
                            Slider(
                                value = raiseInterval.toFloat(),
                                onValueChange = { raiseInterval = it.toInt() },
                                valueRange = 5f..60f,
                                steps = 10,
                                onValueChangeFinished = { repository.setRaiseInterval(raiseInterval) },
                                colors = SliderDefaults.colors(thumbColor = ThemeManager.parseColor(theme.accentColor), activeTrackColor = ThemeManager.parseColor(theme.accentColor))
                            )
                        }
                    }
                }
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().alpha(if (busySettings.enabled) 0.4f else 1f)) {
                Column {
                    SettingCard("Автоответ на отзывы", "Автоматически отвечать на отзывы", reviewSettings.enabled, Icons.Default.Star, theme) {
                        if (!busySettings.enabled) {
                            val newSettings = reviewSettings.copy(enabled = !reviewSettings.enabled)
                            reviewSettings = newSettings
                            repository.saveReviewReplySettings(newSettings)
                        }
                    }
                    if (reviewSettings.enabled) {
                        Button(
                            onClick = { if (!busySettings.enabled) showDialog = "review_settings" },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (reviewSettings.useAi) Icon(Icons.Default.AutoAwesome, null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(16.dp))
                                else Icon(Icons.Default.List, null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Настроить", color = ThemeManager.parseColor(theme.textPrimaryColor))
                            }
                        }
                    }
                }
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().alpha(if (!isAllowed("autoResp")) 0.4f else 1f)) {
                Column {
                    SettingCard("Автоответ", "Быстрые ответы на сообщения", autoResponse, Icons.Default.AutoAwesome, theme) {
                        if (isAllowed("autoResp")) {
                            autoResponse = !autoResponse
                            repository.setSetting("auto_response", autoResponse)
                        }
                    }
                    if (autoResponse) {
                        Button(
                            onClick = { if (isAllowed("autoResp")) showDialog = "commands" },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
                        ) { Text("Настроить команды", color = ThemeManager.parseColor(theme.textPrimaryColor)) }
                    }
                }
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().alpha(if (!isAllowed("greeting")) 0.4f else 1f)) {
                Column {
                    SettingCard("Приветствие", "Автоматически здороваться с покупателями", greetingSettings.enabled, Icons.Default.WavingHand, theme) {
                        if (isAllowed("greeting")) {
                            greetingSettings = greetingSettings.copy(enabled = !greetingSettings.enabled)
                            repository.saveGreetingSettings(greetingSettings)
                        }
                    }
                    if (greetingSettings.enabled) {
                        Button(
                            onClick = { if (isAllowed("greeting")) showDialog = "greeting" },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
                        ) { Text("Настроить приветствие", color = ThemeManager.parseColor(theme.textPrimaryColor)) }
                    }
                }
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().alpha(if (busySettings.enabled) 0.4f else 1f)) {
                Column {
                    PremiumSettingCard("Авто-возврат", "Возврат денег при плохом отзыве на дешевый товар", refundSettings.enabled, Icons.Default.MoneyOff, theme, feature = PremiumFeature.AUTO_REFUND) {
                        if (!busySettings.enabled) {
                            val newSettings = refundSettings.copy(enabled = !refundSettings.enabled)
                            refundSettings = newSettings
                            repository.saveAutoRefundSettings(newSettings)
                        }
                    }
                    if (refundSettings.enabled) {
                        Button(
                            onClick = { if (!busySettings.enabled) showDialog = "refund_settings" },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
                        ) { Text("Настроить условия", color = ThemeManager.parseColor(theme.textPrimaryColor)) }
                    }
                }
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().alpha(if (busySettings.enabled) 0.4f else 1f)) {
                Column {
                    StrictProOnlySettingCard(
                        title = "XD Dumper",
                        subtitle = "Авто-демпинг цен конкурентов",
                        checked = dumperSettings.enabled,
                        icon = Icons.Default.TrendingDown,
                        theme = theme,
                        onCheckedChange = {
                            if (!busySettings.enabled) {
                                dumperSettings = dumperSettings.copy(enabled = it)
                                repository.saveDumperSettings(dumperSettings)
                            }
                        },
                        onProRequired = {
                            navController.navigate("donations")
                            Toast.makeText(context, "Эта функция доступна только в PRO версии!", Toast.LENGTH_SHORT).show()
                        }
                    )

                    if (dumperSettings.enabled && LicenseManager.isProActive()) {
                        Button(
                            onClick = { if (!busySettings.enabled) showDialog = "dumper_settings" },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
                        ) { Text("Настроить лоты для демпинга", color = ThemeManager.parseColor(theme.textPrimaryColor)) }
                    }
                }
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().alpha(if (busySettings.enabled) 0.4f else 1f)) {
                Column {
                    PremiumSettingCard("Просьба отзыва", "Писать сообщение после подтверждения заказа", confirmSettings.enabled, Icons.Default.ThumbUp, theme, feature = PremiumFeature.REVIEW_REQUEST) {
                        if (!busySettings.enabled) {
                            val newSettings = confirmSettings.copy(enabled = !confirmSettings.enabled)
                            confirmSettings = newSettings
                            repository.saveOrderConfirmSettings(newSettings)
                        }
                    }
                    if (confirmSettings.enabled) {
                        Button(
                            onClick = { if (!busySettings.enabled) showDialog = "confirm_settings" },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
                        ) { Text("Настроить текст", color = ThemeManager.parseColor(theme.textPrimaryColor)) }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity)),
                shape = RoundedCornerShape(theme.borderRadius.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ShortText, null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Шаблоны сообщений", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), modifier = Modifier.weight(1f))
                        Button(onClick = { showDialog = "templates" }, colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))) {
                            Text("Настроить", color = ThemeManager.parseColor(theme.textPrimaryColor))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Быстрая вставка готовых фраз в чате", fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                }
            }
        }
    }

    when (showDialog) {
        "commands" -> CommandsDialog(repository, theme) { showDialog = null }
        "greeting" -> GreetingDialog(repository, greetingSettings, theme) {
            greetingSettings = it
            repository.saveGreetingSettings(it)
            showDialog = null
        }
        "confirm_settings" -> OrderConfirmDialog(repository, theme) {
            showDialog = null
            confirmSettings = repository.getOrderConfirmSettings()
        }
        "review_settings" -> ReviewSettingsDialog(
            repository = repository,
            theme = theme,
            onNeedsPro = { showDialog = null; showReviewAiGate = true },
            onDismiss = { showDialog = null; reviewSettings = repository.getReviewReplySettings() }
        )
        "refund_settings" -> AutoRefundDialog(repository, theme) {
            showDialog = null
            refundSettings = repository.getAutoRefundSettings()
        }
        "dumper_settings" -> DumperSettingsDialog(repository, theme) { showDialog = null; dumperSettings = repository.getDumperSettings() }
        "templates" -> TemplatesDialog(repository, theme) { showDialog = null }
    }
}

@Composable
fun PremiumSettingCard(
    title: String,
    subtitle: String,
    checked: Boolean,                                      
    icon: ImageVector,
    theme: AppTheme,                                       
    feature: PremiumFeature,                               
    onCheckedChange: (Boolean) -> Unit                     
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(theme.borderRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor)
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ThemeManager.parseColor(theme.accentColor),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = ThemeManager.parseColor(theme.textPrimaryColor)
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = ThemeManager.parseColor(theme.textSecondaryColor),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            
            PremiumSwitch(
                feature = feature,
                checked = checked,
                theme = theme,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun AutoRefundDialog(repository: FunPayRepository, theme: AppTheme, onDismiss: () -> Unit) {
    var settings by remember { mutableStateOf(repository.getAutoRefundSettings()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(theme.borderRadius.dp),
            modifier = Modifier.heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Настройка Авто-возврата", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Максимальная сумма заказа:", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 14.sp)
                OutlinedTextField(
                    value = settings.maxPrice.toString(),
                    onValueChange = {
                        if (it.all { char -> char.isDigit() || char == '.' }) {
                            settings = settings.copy(maxPrice = it.toDoubleOrNull() ?: 0.0)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                        unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                        focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                        cursorColor = ThemeManager.parseColor(theme.accentColor)
                    )
                )
                Text("Если цена выше этой суммы, возврат не сработает.", fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))

                Spacer(modifier = Modifier.height(16.dp))

                Text("Триггеры (оценка отзыва):", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 14.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..5).forEach { star ->
                        val isSelected = settings.triggerStars.contains(star)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newList = settings.triggerStars.toMutableList()
                                if (isSelected) newList.remove(star) else newList.add(star)
                                settings = settings.copy(triggerStars = newList)
                            },
                            label = { Text("$star★") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ThemeManager.parseColor(theme.accentColor),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.sendMessage,
                        onCheckedChange = { settings = settings.copy(sendMessage = it) },
                        colors = CheckboxDefaults.colors(checkedColor = ThemeManager.parseColor(theme.accentColor))
                    )
                    Text("Отправлять сообщение", color = ThemeManager.parseColor(theme.textPrimaryColor))
                }

                if (settings.sendMessage) {
                    OutlinedTextField(
                        value = settings.messageText,
                        onValueChange = { settings = settings.copy(messageText = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Текст сообщения") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                            unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                            focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                            cursorColor = ThemeManager.parseColor(theme.accentColor)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        repository.saveAutoRefundSettings(settings)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                ) {
                    Text("Сохранить")
                }
            }
        }
    }
}

@Composable
fun SettingCard(title: String, desc: String, enabled: Boolean, icon: ImageVector, theme: AppTheme, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity)
        ),
        shape = RoundedCornerShape(theme.borderRadius.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (enabled) ThemeManager.parseColor(theme.accentColor) else ThemeManager.parseColor(theme.textSecondaryColor), modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
                Text(desc, fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
            }
            Switch(checked = enabled, onCheckedChange = { onToggle() }, colors = SwitchDefaults.colors(
                checkedThumbColor = ThemeManager.parseColor(theme.accentColor),
                checkedTrackColor = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.5f)
            ))
        }
    }
}

@Composable
fun CommandsDialog(repository: FunPayRepository, theme: AppTheme, onDismiss: () -> Unit) {
    var commands by remember { mutableStateOf(repository.getCommands()) }
    var newTrigger by remember { mutableStateOf("") }
    var newResponse by remember { mutableStateOf("") }
    var exactMatch by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(theme.borderRadius.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Команды автоответа", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = newTrigger, onValueChange = { newTrigger = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Триггер") })
                OutlinedTextField(value = newResponse, onValueChange = { newResponse = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Ответ") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = exactMatch, onCheckedChange = { exactMatch = it })
                    Text("Точное совпадение", color = ThemeManager.parseColor(theme.textPrimaryColor))
                }
                Button(onClick = {
                    if (newTrigger.isNotBlank() && newResponse.isNotBlank()) {
                        commands = commands + AutoResponseCommand(newTrigger.trim(), newResponse.trim(), exactMatch)
                        repository.saveCommands(commands)
                        newTrigger = ""
                        newResponse = ""
                        exactMatch = false
                    }
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))) { Text("Добавить") }
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(commands) { cmd ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("'${cmd.trigger}'", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(cmd.response, color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 10.sp, maxLines = 1)
                            }
                            IconButton(onClick = {
                                commands = commands.filter { it != cmd }
                                repository.saveCommands(commands)
                            }) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red)
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))) { Text("Закрыть", color = ThemeManager.parseColor(theme.textPrimaryColor)) }
            }
        }
    }
}

@Composable
fun GreetingDialog(repository: FunPayRepository, settings: GreetingSettings, theme: AppTheme, onSave: (GreetingSettings) -> Unit) {
    var text by remember { mutableStateOf(settings.text) }
    var cooldown by remember { mutableIntStateOf(settings.cooldownHours) }
    var ignoreSystem by remember { mutableStateOf(settings.ignoreSystemMessages) }

    Dialog(onDismissRequest = {}) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(theme.borderRadius.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Настройка приветствия", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Текст") }, maxLines = 3)
                Text("Переменные: \$username, \$chat_name", fontSize = 10.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Кулдаун: $cooldown ч", color = ThemeManager.parseColor(theme.textPrimaryColor))
                Slider(value = cooldown.toFloat(), onValueChange = { cooldown = it.toInt() }, valueRange = 1f..168f, steps = 23, colors = SliderDefaults.colors(
                    thumbColor = ThemeManager.parseColor(theme.accentColor),
                    activeTrackColor = ThemeManager.parseColor(theme.accentColor)
                ))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ignoreSystem, onCheckedChange = { ignoreSystem = it })
                    Text("Игнорировать системные сообщения", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 12.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSave(GreetingSettings(false, text, cooldown, ignoreSystem)) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
                    ) {
                        Text("Закрыть", color = ThemeManager.parseColor(theme.textPrimaryColor))
                    }
                    Button(
                        onClick = { onSave(GreetingSettings(true, text, cooldown, ignoreSystem)) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewSettingsDialog(
    repository: FunPayRepository,
    theme: AppTheme,
    onNeedsPro: () -> Unit,
    onDismiss: () -> Unit
) {
    var settings by remember { mutableStateOf(repository.getReviewReplySettings()) }
    var selectedTab by remember { mutableIntStateOf(if (settings.useAi && LicenseManager.hasAccess(PremiumFeature.REVIEW_AI)) 0 else 1) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(theme.borderRadius.dp),
            modifier = Modifier.heightIn(max = 650.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Настройка ответов на отзывы", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(0.2f), RoundedCornerShape(8.dp)).padding(4.dp)) {
                    Button(
                        onClick = {
                            if (!LicenseManager.hasAccess(PremiumFeature.REVIEW_AI)) {
                                onNeedsPro()
                            } else {
                                selectedTab = 0
                                settings = settings.copy(useAi = true)
                                repository.saveReviewReplySettings(settings)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 0) ThemeManager.parseColor(theme.accentColor) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("AI")
                            if (!LicenseManager.hasAccess(PremiumFeature.REVIEW_AI)) {
                                Spacer(Modifier.width(4.dp))
                                LockBadge(feature = PremiumFeature.REVIEW_AI, theme = theme)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            selectedTab = 1
                            settings = settings.copy(useAi = false)
                            repository.saveReviewReplySettings(settings)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 1) ThemeManager.parseColor(theme.accentColor) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.List, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Шаблоны")
                        }
                    }
                }


                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {

                    Text("Сила ответа: ${settings.aiLength}", color = ThemeManager.parseColor(theme.textPrimaryColor))
                    Text(
                        text = when {
                            settings.aiLength <= 2 -> "Очень кратко и по делу"
                            settings.aiLength == 5 -> "Стандартный, дружелюбный ответ"
                            settings.aiLength >= 9 -> "Максимально развернутый и креативный"
                            else -> "Средняя длина"
                        },
                        fontSize = 12.sp,
                        color = ThemeManager.parseColor(theme.textSecondaryColor)
                    )

                    Slider(
                        value = settings.aiLength.toFloat(),
                        onValueChange = {
                            val newLength = it.toInt()
                            settings = settings.copy(aiLength = newLength)
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        onValueChangeFinished = {
                            repository.saveReviewReplySettings(settings)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = ThemeManager.parseColor(theme.accentColor),
                            activeTrackColor = ThemeManager.parseColor(theme.accentColor)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Текст при сбое AI:", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 12.sp)
                    OutlinedTextField(
                        value = settings.aiFallbackText,
                        onValueChange = {
                            settings = settings.copy(aiFallbackText = it)
                            repository.saveReviewReplySettings(settings)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Спасибо за отзыв!") }
                    )
                    Text("Если AI не ответит 3 раза, будет отправлен этот текст. Переменные поддерживаются.", fontSize = 10.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                } else {

                    LazyColumn(modifier = Modifier.weight(1f, false)) {
                        items(5) { i ->
                            val stars = 5 - i
                            val text = settings.manualTemplates[stars] ?: ""
                            val isEnabled = !settings.disabledStars.contains(stars)

                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("${"⭐".repeat(stars)}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { enabled ->
                                            val currentDisabled = settings.disabledStars.toMutableList()
                                            if (enabled) currentDisabled.remove(stars) else if (!currentDisabled.contains(stars)) currentDisabled.add(stars)
                                            settings = settings.copy(disabledStars = currentDisabled)
                                            repository.saveReviewReplySettings(settings)
                                        },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }

                                if (isEnabled) {
                                    OutlinedTextField(
                                        value = text,
                                        onValueChange = { newText ->
                                            val newMap = settings.manualTemplates.toMutableMap()
                                            newMap[stars] = newText
                                            settings = settings.copy(manualTemplates = newMap)
                                            repository.saveReviewReplySettings(settings)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 2,
                                        placeholder = { Text("Текст ответа...") }
                                    )
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(0.1f))
                        }
                    }
                    Text("Переменные: \$username, \$order_id, \$lot_name", fontSize = 10.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))) { Text("Закрыть", color = ThemeManager.parseColor(theme.textPrimaryColor)) }
            }
        }
    }
}

@Composable
fun ChatDetailScreen(chatId: String, username: String, repository: FunPayRepository, theme: AppTheme, navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var messages by remember { mutableStateOf<List<MessageItem>>(emptyList()) }
    var parsedMessages by remember { mutableStateOf<List<ParsedMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isAiProcessing by remember { mutableStateOf(false) }
    var showAiLimit by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var showImageDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var previousMessageCount by remember { mutableIntStateOf(0) }
    var selectionKey by remember { mutableIntStateOf(0) }
    var chatInfo by remember { mutableStateOf<ChatInfo?>(null) }

    
    var showTemplatesMenu by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            selectedImageUri = it
            showImageDialog = true
        }
    }

    if (showAiLimit) {
        AiLimitDialog(theme = theme, onDismiss = { showAiLimit = false }) {
            showAiLimit = false
        }
    }

    LaunchedEffect(chatId) {
        scope.launch {
            chatInfo = repository.getChatInfo(chatId)
        }
        while (true) {
            messages = repository.getChatHistory(chatId)
            parsedMessages = parseMessagesFromRepository(messages, repository)

            if (messages.size > previousMessageCount) {
                if (messages.isNotEmpty()) {
                    listState.scrollToItem(messages.lastIndex)
                }
                previousMessageCount = messages.size
            }

            delay(3000)
        }
    }

    if (fullScreenImageUrl != null) {
        FullScreenImageDialog(
            imageUrl = fullScreenImageUrl!!,
            repository = repository,
            theme = theme,
            onDismiss = { fullScreenImageUrl = null }
        )
    }

    if (showImageDialog && selectedImageUri != null) {
        Dialog(onDismissRequest = { showImageDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
                shape = RoundedCornerShape(theme.borderRadius.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Отправить изображение?", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showImageDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
                        ) {
                            Text("Отмена", color = ThemeManager.parseColor(theme.textPrimaryColor))
                        }
                        Button(
                            onClick = {

                                FunPayRepository.lastOutgoingMessages[chatId] = "__image__"
                                scope.launch {
                                    val fileId = repository.uploadImage(selectedImageUri!!)
                                    if (fileId != null) {
                                        repository.sendMessage(chatId, "", fileId)
                                    }
                                    showImageDialog = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                        ) {
                            Text("Отправить")
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeManager.parseColor(theme.backgroundColor))
            .imePadding()
            .pointerInput(Unit) {
                detectTapGestures {
                    selectionKey++
                    focusManager.clearFocus()
                }
            }
    ) {
        
        if (chatInfo != null) {
            val info = chatInfo!!
            if (info.lookingAtName != null || info.registrationDate != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        if (info.lookingAtName != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.lookingAtLink))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Visibility, null, tint = ThemeManager.parseColor(theme.textSecondaryColor), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Смотрит: ${info.lookingAtName}",
                                    color = ThemeManager.parseColor(theme.accentColor),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                )
                            }
                        }

                        if (info.registrationDate != null) {
                            if (info.lookingAtName != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Регистрация: ${info.registrationDate}",
                                    color = ThemeManager.parseColor(theme.textSecondaryColor),
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (info.language?.contains("English", true) == true || info.language?.contains("Английский", true) == true) {
                                    Text("🇺🇸", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(parsedMessages, key = { it.id }) { msg ->
                OptimizedMessageBubble(
                    message = msg,
                    theme = theme,
                    selectionKey = selectionKey,
                    onLinkClick = { link ->
                        when (link.type) {
                            LinkType.ORDER -> {
                                val orderId = link.url.substringAfter("orders/").substringBefore("/")
                                if (orderId.isNotEmpty()) {
                                    navController.navigate("order/$orderId")
                                }
                            }
                            LinkType.USER -> {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            LinkType.EXTERNAL -> {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onImageClick = { url ->
                        fullScreenImageUrl = url
                    }
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Attach", tint = ThemeManager.parseColor(theme.textPrimaryColor))
                }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Сообщение...", color = ThemeManager.parseColor(theme.textSecondaryColor)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                            unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                            focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                            cursorColor = ThemeManager.parseColor(theme.accentColor)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                
                                IconButton(onClick = { showTemplatesMenu = true }) {
                                    Icon(
                                        Icons.Default.ShortText,
                                        contentDescription = "Templates",
                                        tint = ThemeManager.parseColor(theme.accentColor)
                                    )
                                }


                                IconButton(onClick = {
                                    if (inputText.isNotBlank() && !isAiProcessing) {
                                        if (!LicenseManager.consumeAiClick()) {
                                            showAiLimit = true
                                            return@IconButton
                                        }
                                        isAiProcessing = true
                                        scope.launch {
                                            val contextHistory = messages.takeLast(10).joinToString("\n") {
                                                "${if(it.isMe) "Продавец" else "Покупатель"}: ${it.text}"
                                            }
                                            val rewrited = repository.rewriteMessage(inputText, contextHistory)
                                            if (!rewrited.isNullOrEmpty()) {
                                                inputText = rewrited
                                            }
                                            isAiProcessing = false
                                        }
                                    }
                                }) {
                                    if (isAiProcessing) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ThemeManager.parseColor(theme.accentColor))
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Rewrite", tint = ThemeManager.parseColor(theme.accentColor))
                                    }
                                }
                            }
                        }
                    )

                    DropdownMenu(
                        expanded = showTemplatesMenu,
                        onDismissRequest = { showTemplatesMenu = false },
                        modifier = Modifier.background(ThemeManager.parseColor(theme.surfaceColor))
                    ) {
                        val templates = repository.getMessageTemplates()
                        val templateSettings = repository.getTemplateSettings()

                        if (templates.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Нет шаблонов", color = ThemeManager.parseColor(theme.textSecondaryColor)) },
                                onClick = { showTemplatesMenu = false }
                            )
                        } else {
                            templates.forEach { template ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                template.name,
                                                color = ThemeManager.parseColor(theme.textPrimaryColor),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                template.text,
                                                color = ThemeManager.parseColor(theme.textSecondaryColor),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    onClick = {
                                        
                                        
                                        
                                        val finalText = processTemplateVariables(template.text, username)

                                        if (templateSettings.sendImmediately) {
                                            
                                            val newMessage = MessageItem(
                                                id = System.currentTimeMillis().toString(),
                                                author = "Вы",
                                                text = finalText, 
                                                isMe = true,
                                                time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                                                imageUrl = null
                                            )
                                            messages = messages + newMessage
                                            parsedMessages = parseMessagesFromRepository(messages, repository)
                                            previousMessageCount = messages.size
                                            scope.launch { listState.scrollToItem(messages.lastIndex) }


                                            FunPayRepository.lastOutgoingMessages[chatId] = finalText.trim()
                                            scope.launch {
                                                repository.sendMessage(chatId, finalText)
                                            }
                                        } else {
                                            
                                            inputText = finalText
                                        }
                                        showTemplatesMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = {
                    if (inputText.isNotBlank()) {
                        val textToSend = inputText
                        inputText = ""

                        val newMessage = MessageItem(
                            id = System.currentTimeMillis().toString(),
                            author = "Вы",
                            text = textToSend,
                            isMe = true,
                            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                            imageUrl = null
                        )
                        messages = messages + newMessage
                        parsedMessages = parseMessagesFromRepository(messages, repository)
                        previousMessageCount = messages.size
                        scope.launch { listState.scrollToItem(messages.lastIndex) }

                        scope.launch {
                            repository.sendMessage(chatId, textToSend)
                        }
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = ThemeManager.parseColor(theme.accentColor))
                }
            }
        }
    }
}

@Composable
fun FullScreenImageDialog(imageUrl: String, repository: FunPayRepository, theme: AppTheme, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var showCopyMenu by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onDismiss() },
                contentScale = ContentScale.Fit
            )

            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding() 
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }

            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
                    .navigationBarsPadding() 
                    .padding(bottom = 48.dp, top = 24.dp) 
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    
                    Box {
                        Button(
                            onClick = { showCopyMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
                            enabled = !isDownloading
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Копировать", color = ThemeManager.parseColor(theme.textPrimaryColor))
                        }

                        DropdownMenu(
                            expanded = showCopyMenu,
                            onDismissRequest = { showCopyMenu = false },
                            modifier = Modifier.background(ThemeManager.parseColor(theme.surfaceColor))
                        ) {
                            
                            DropdownMenuItem(
                                text = { Text("Ссылку", color = ThemeManager.parseColor(theme.textPrimaryColor)) },
                                onClick = {
                                    val clip = ClipData.newPlainText("Image URL", imageUrl)
                                    clipboardManager.setPrimaryClip(clip)
                                    Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                                    showCopyMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Link, null, tint = ThemeManager.parseColor(theme.accentColor))
                                }
                            )
                            
                            DropdownMenuItem(
                                text = { Text("Изображение", color = ThemeManager.parseColor(theme.textPrimaryColor)) },
                                onClick = {
                                    showCopyMenu = false
                                    isDownloading = true
                                    scope.launch {
                                        try {
                                            
                                            val loader = ImageLoader(context)
                                            val request = ImageRequest.Builder(context)
                                                .data(imageUrl)
                                                .allowHardware(false) 
                                                .build()
                                            val result = loader.execute(request)
                                            val drawable = result.drawable

                                            if (drawable != null) {
                                                val bitmap = (drawable as BitmapDrawable).bitmap

                                                
                                                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                    val contentValues = ContentValues().apply {
                                                        put(MediaStore.Images.Media.DISPLAY_NAME, "temp_copy_${System.currentTimeMillis()}.jpg")
                                                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                                                    }
                                                    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                                                } else {
                                                    val path = MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "Temp Copy", null)
                                                    Uri.parse(path)
                                                }

                                                if (uri != null) {
                                                    
                                                    context.contentResolver.openOutputStream(uri)?.use { out ->
                                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                                                    }

                                                    val clip = ClipData.newUri(context.contentResolver, "Image", uri)
                                                    clipboardManager.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Изображение скопировано", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Ошибка создания URI", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isDownloading = false
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Image, null, tint = ThemeManager.parseColor(theme.accentColor))
                                }
                            )
                        }
                    }

                    
                    Button(
                        onClick = {
                            scope.launch {
                                isDownloading = true
                                val success = repository.downloadAndSaveImage(imageUrl)
                                Toast.makeText(
                                    context,
                                    if (success) "Сохранено в галерею" else "Ошибка сохранения",
                                    Toast.LENGTH_SHORT
                                ).show()
                                isDownloading = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor)),
                        enabled = !isDownloading
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Сохранить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OptimizedMessageBubble(
    message: ParsedMessage,
    theme: AppTheme,
    selectionKey: Int,
    onLinkClick: (MessageLink) -> Unit,
    onImageClick: (String) -> Unit
) {
    if (message.isSystem) {
        
        val isSupport = message.badge?.contains("поддержка", true) == true || message.badge?.contains("арбитраж", true) == true
        val badgeColor = if (isSupport) Color(0xFF66BB6A) else ThemeManager.parseColor(theme.accentColor)
        val containerColor = if (isSupport) Color(0xFF1B5E20).copy(alpha = 0.6f) else ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.5f)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = containerColor
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isSupport) Icons.Default.Security else Icons.Default.Info,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = message.badge?.uppercase() ?: if (message.author == "FunPay") "FUNPAY" else "СИСТЕМА",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                MessageTextWithLinks(
                    text = message.text,
                    links = message.links,
                    textColor = ThemeManager.parseColor(theme.textPrimaryColor),
                    linkColor = badgeColor,
                    selectionKey = selectionKey,
                    onLinkClick = onLinkClick
                )
                if (message.time.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = message.time,
                        fontSize = 10.sp,
                        color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.7f)
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isMe) Arrangement.End else Arrangement.Start
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (message.isMe)
                        ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.2f)
                    else
                        ThemeManager.parseColor(theme.surfaceColor)
                ),
                shape = RoundedCornerShape(
                    topStart = if (message.isMe) 16.dp else 4.dp,
                    topEnd = if (message.isMe) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    if (message.imageUrl != null) {
                        AsyncImage(
                            model = message.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick(message.imageUrl) },
                            contentScale = ContentScale.Crop
                        )
                        if (message.text.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (message.text.isNotEmpty()) {
                        MessageTextWithLinks(
                            text = message.text,
                            links = message.links,
                            textColor = ThemeManager.parseColor(theme.textPrimaryColor),
                            linkColor = ThemeManager.parseColor(theme.accentColor),
                            selectionKey = selectionKey,
                            onLinkClick = onLinkClick
                        )
                    }
                    if (message.time.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = message.time,
                            fontSize = 10.sp,
                            color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageTextWithLinks(
    text: String,
    links: List<MessageLink>,
    textColor: Color,
    linkColor: Color,
    selectionKey: Int,
    onLinkClick: (MessageLink) -> Unit
) {
    val annotatedString = buildAnnotatedString {
        if (links.isEmpty()) {
            append(text)
        } else {
            var currentIndex = 0
            links.sortedBy { text.indexOf(it.text, currentIndex) }.forEach { link ->
                val linkIndex = text.indexOf(link.text, currentIndex)
                if (linkIndex >= currentIndex) {
                    if (linkIndex > currentIndex) {
                        append(text.substring(currentIndex, linkIndex))
                    }
                    pushStringAnnotation(tag = "LINK", annotation = link.url)
                    withStyle(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(link.text)
                    }
                    pop()
                    currentIndex = linkIndex + link.text.length
                }
            }
            if (currentIndex < text.length) {
                append(text.substring(currentIndex))
            }
        }
    }

    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    key(selectionKey) {
        SelectionContainer {
            Text(
                text = annotatedString,
                style = LocalTextStyle.current.copy(
                    fontSize = 14.sp,
                    color = textColor,
                    lineHeight = 20.sp
                ),
                onTextLayout = { layoutResult.value = it },
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        layoutResult.value?.let { layout ->
                            val position = layout.getOffsetForPosition(offset)
                            annotatedString.getStringAnnotations(tag = "LINK", start = position, end = position)
                                .firstOrNull()?.let { annotation ->
                                    links.find { it.url == annotation.item }?.let(onLinkClick)
                                }
                        }
                    }
                }
            )
        }
    }
}

fun parseMessagesFromRepository(messages: List<MessageItem>, repository: FunPayRepository): List<ParsedMessage> {
    return messages.map { msg ->
        val isSystemMsg = msg.badge != null || msg.author == "FunPay"

        val isAdminMsg = msg.badge == "поддержка" || msg.badge == "арбитраж"

        val links = mutableListOf<MessageLink>()

        val orderRegex = Regex("(?i)(заказ[а-яА-Я]*\\s*#([A-Z0-9]+))")
        orderRegex.findAll(msg.text).forEach { match ->
            val fullMatch = match.groupValues[1]
            val orderId = match.groupValues[2]
            links.add(MessageLink(fullMatch, "https://funpay.com/orders/$orderId/", LinkType.ORDER))
        }

        val urlRegex = Regex("https?://[^\\s]+")
        urlRegex.findAll(msg.text).forEach { match ->
            val url = match.value
            val linkType = when {
                url.contains("/orders/") -> LinkType.ORDER
                url.contains("/users/") -> LinkType.USER
                else -> LinkType.EXTERNAL
            }
            links.add(MessageLink(url, url, linkType))
        }

        ParsedMessage(
            id = msg.id,
            author = msg.author,
            text = msg.text,
            isMe = msg.isMe,
            time = msg.time,
            imageUrl = msg.imageUrl,
            isSystem = isSystemMsg,
            isAdmin = isAdminMsg,
            badge = msg.badge,
            links = links
        )
    }
}

@Composable
fun ConsoleView(logs: List<Pair<String, Boolean>>, theme: AppTheme, navController: NavController) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var onlineCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Stats.getOnlineCount { count -> onlineCount = count }
            } else {
                onlineCount = 0
            }
            delay(15000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(theme.borderRadius.dp))
                .background(ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.2f))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👥 Онлайн:", fontSize = 14.sp, color = ThemeManager.parseColor(theme.textPrimaryColor))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$onlineCount",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.parseColor(theme.accentColor)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = Color.Red) }
                Spacer(modifier = Modifier.width(4.dp))
                Text("LIVE", fontSize = 10.sp, color = Color.Red, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { navController.navigate("donations") },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(theme.borderRadius.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.2f)
            )
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (LicenseManager.isProActive()) "💎 PRO активен" else "🔑 Активировать PRO",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(theme.borderRadius.dp))
                .background(ThemeManager.parseColor(theme.surfaceColor))
                .padding(12.dp),
            reverseLayout = false
        ) {
            items(logs) { (log, isError) ->
                Text(
                    text = log,
                    color = if (isError) Color(0xFFB01818) else ThemeManager.parseColor(theme.textPrimaryColor),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    try {
                        val fullLog = logs.take(500).joinToString("\n\n") { it.first }
                        val clip = ClipData.newPlainText("FunPay Logs", fullLog)
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "Скопировано (последние 500)", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) { Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show() }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
            ) { Text("КОПИРОВАТЬ", fontSize = 12.sp) }
            Button(
                onClick = {
                    val path = LogManager.saveLogsToFile(context)
                    Toast.makeText(context, path, Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
            ) { Text("В ФАЙЛ", fontSize = 12.sp) }
        }
    }
}

@Composable
fun OrderScreen(orderId: String, repository: FunPayRepository, theme: AppTheme) {
    val scope = rememberCoroutineScope()
    var orderDetails by remember { mutableStateOf<OrderDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showRefundDialog by remember { mutableStateOf(false) }
    var showReviewReplyDialog by remember { mutableStateOf(false) }
    var showDeleteReplyDialog by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }

    LaunchedEffect(orderId) {
        isLoading = true
        orderDetails = repository.getOrderDetails(orderId)
        isLoading = false

        if (orderDetails?.hasReview == true && orderDetails?.sellerReply?.isNotEmpty() == true) {
            replyText = orderDetails!!.sellerReply
        }
    }

    if (showRefundDialog) {
        AlertDialog(
            onDismissRequest = { showRefundDialog = false },
            title = { Text("Возврат средств", color = Color.Red) },
            text = { Text("Вы уверены, что хотите вернуть деньги покупателю? Это действие необратимо.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val success = repository.refundOrder(orderId)
                            if (success) {
                                orderDetails = repository.getOrderDetails(orderId)
                            }
                            showRefundDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Вернуть деньги")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRefundDialog = false }) { Text("Отмена") }
            },
            containerColor = ThemeManager.parseColor(theme.surfaceColor)
        )
    }

    if (showReviewReplyDialog) {
        AlertDialog(
            onDismissRequest = { showReviewReplyDialog = false },
            title = { Text(if (orderDetails?.sellerReply?.isNotEmpty() == true) "Изменить ответ" else "Ответить на отзыв", color = ThemeManager.parseColor(theme.textPrimaryColor)) },
            text = {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ваш ответ") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val success = repository.replyToReview(orderId, replyText, 5)
                            if (success) {
                                orderDetails = repository.getOrderDetails(orderId)
                            }
                            showReviewReplyDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                ) {
                    Text("Отправить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReviewReplyDialog = false }) { Text("Отмена") }
            },
            containerColor = ThemeManager.parseColor(theme.surfaceColor)
        )
    }

    if (showDeleteReplyDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteReplyDialog = false },
            title = { Text("Удалить ответ?", color = Color.Red) },
            text = { Text("Вы уверены, что хотите удалить свой ответ на отзыв?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val success = repository.deleteReviewReply(orderId)
                            if (success) {
                                orderDetails = repository.getOrderDetails(orderId)
                            }
                            showDeleteReplyDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteReplyDialog = false }) { Text("Отмена") }
            },
            containerColor = ThemeManager.parseColor(theme.surfaceColor)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = ThemeManager.parseColor(theme.accentColor))
        } else if (orderDetails == null) {
            Text("Не удалось загрузить заказ", modifier = Modifier.align(Alignment.Center), color = ThemeManager.parseColor(theme.textSecondaryColor))
        } else {
            val order = orderDetails!!
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
                    shape = RoundedCornerShape(theme.borderRadius.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        
                        Text(
                            text = order.status,
                            color = if (order.status.contains("Закрыт")) Color.Green else Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = order.gameTitle,
                                color = ThemeManager.parseColor(theme.accentColor),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                modifier = Modifier.weight(1f).padding(end = 8.dp) 
                            )
                            Text(
                                text = order.price,
                                color = ThemeManager.parseColor(theme.textPrimaryColor),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(order.shortDesc, color = ThemeManager.parseColor(theme.textPrimaryColor))

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = order.buyerAvatar,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp).clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Покупатель: ${order.buyerName}", color = ThemeManager.parseColor(theme.textSecondaryColor))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                order.params.forEach { (key, value) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(key, modifier = Modifier.weight(1f), color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                        Text(value, modifier = Modifier.weight(2f), color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 12.sp)
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (order.hasReview) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(theme.borderRadius.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Отзыв покупателя (${order.reviewRating}*)", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold)
                            Text(order.reviewText, color = ThemeManager.parseColor(theme.textPrimaryColor), fontStyle = FontStyle.Italic)

                            if (order.sellerReply.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Ваш ответ:", color = ThemeManager.parseColor(theme.accentColor), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(order.sellerReply, color = ThemeManager.parseColor(theme.textPrimaryColor))
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { showReviewReplyDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
                                ) {
                                    Text(if (order.sellerReply.isNotEmpty()) "Изменить" else "Ответить")
                                }
                                if (order.sellerReply.isNotEmpty()) {
                                    Button(
                                        onClick = { showDeleteReplyDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                                    ) {
                                        Text("Удалить")
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (order.canRefund) {
                    Button(
                        onClick = { showRefundDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Вернуть деньги", color = Color.White)
                    }
                }
            }
        }
    }
}


@Composable
fun TemplatesDialog(repository: FunPayRepository, theme: AppTheme, onDismiss: () -> Unit) {
    var templates by remember { mutableStateOf(repository.getMessageTemplates()) }
    var templateSettings by remember { mutableStateOf(repository.getTemplateSettings()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newTemplateName by remember { mutableStateOf("") }
    var newTemplateText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(theme.borderRadius.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Шаблоны сообщений",
                        fontWeight = FontWeight.Bold,
                        color = ThemeManager.parseColor(theme.textPrimaryColor),
                        fontSize = 20.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = ThemeManager.parseColor(theme.textPrimaryColor))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().background(
                        ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    ).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Отправлять сразу",
                            color = ThemeManager.parseColor(theme.textPrimaryColor),
                            fontSize = 14.sp
                        )
                        Text(
                            "При нажатии сразу отправлять, не вставляя в поле ввода",
                            color = ThemeManager.parseColor(theme.textSecondaryColor),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = templateSettings.sendImmediately,
                        onCheckedChange = {
                            templateSettings = templateSettings.copy(sendImmediately = it)
                            repository.saveTemplateSettings(templateSettings)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ThemeManager.parseColor(theme.accentColor),
                            checkedTrackColor = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить шаблон")
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (templates.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ShortText,
                                null,
                                modifier = Modifier.size(64.dp),
                                tint = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Нет шаблонов",
                                color = ThemeManager.parseColor(theme.textSecondaryColor),
                                fontSize = 16.sp
                            )
                            Text(
                                "Добавьте первый шаблон для быстрого общения",
                                color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(templates) { template ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            template.name,
                                            color = ThemeManager.parseColor(theme.textPrimaryColor),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            template.text,
                                            color = ThemeManager.parseColor(theme.textSecondaryColor),
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            templates = templates.filter { it.id != template.id }
                                            repository.saveMessageTemplates(templates)
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Новый шаблон", color = ThemeManager.parseColor(theme.textPrimaryColor)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTemplateName,
                        onValueChange = { newTemplateName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                            unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                            focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                            cursorColor = ThemeManager.parseColor(theme.accentColor)
                        ),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newTemplateText,
                        onValueChange = { newTemplateText = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        label = { Text("Текст сообщения") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                            unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                            focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                            cursorColor = ThemeManager.parseColor(theme.accentColor)
                        ),
                        maxLines = 5
                    )

                    
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Переменные:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ThemeManager.parseColor(theme.textSecondaryColor)
                    )
                    Text(
                        text = "\$username — ник собеседника\n\$welcome — доброе утро/день/вечер\n\$date — текущая дата и время",
                        fontSize = 11.sp,
                        color = ThemeManager.parseColor(theme.textSecondaryColor),
                        lineHeight = 14.sp
                    )
                    
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTemplateName.isNotBlank() && newTemplateText.isNotBlank()) {
                            val newTemplate = MessageTemplate(
                                name = newTemplateName.trim(),
                                text = newTemplateText.trim()
                            )
                            templates = templates + newTemplate
                            repository.saveMessageTemplates(templates)
                            newTemplateName = ""
                            newTemplateText = ""
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ThemeManager.parseColor(theme.accentColor)
                    )
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Отмена", color = ThemeManager.parseColor(theme.textSecondaryColor))
                }
            },
            containerColor = ThemeManager.parseColor(theme.surfaceColor)
        )
    }
}

@Composable
fun OrderConfirmDialog(repository: FunPayRepository, theme: AppTheme, onDismiss: () -> Unit) {
    var settings by remember { mutableStateOf(repository.getOrderConfirmSettings()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(theme.borderRadius.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Настройка просьбы отзыва", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Текст сообщения:", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 14.sp)
                OutlinedTextField(
                    value = settings.text,
                    onValueChange = { settings = settings.copy(text = it) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                        unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
                        focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                        cursorColor = ThemeManager.parseColor(theme.accentColor)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Переменные: \$username (имя покупателя), \$order_id (номер заказа).",
                    fontSize = 11.sp,
                    color = ThemeManager.parseColor(theme.textSecondaryColor)
                )

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        repository.saveOrderConfirmSettings(settings)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                ) {
                    Text("Сохранить")
                }
            }
        }
    }
}

fun processTemplateVariables(text: String, username: String): String {
    var processed = text

    
    
    processed = processed.replace("\$username", username)

    
    if (processed.contains("\$date")) {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault())
        processed = processed.replace("\$date", dateFormat.format(Date()))
    }

    
    if (processed.contains("\$welcome")) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..23 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        processed = processed.replace("\$welcome", greeting)
    }

    return processed
}



@Composable
fun DonationScreen(theme: AppTheme) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)
        Toast.makeText(context, "$label скопирован", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity)
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                border = BorderStroke(
                    1.5.dp,
                    if (LicenseManager.isProActive()) Color(0xFF4CAF50).copy(alpha = 0.5f)
                    else ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (LicenseManager.isProActive()) "💎" else "🔒",
                                fontSize = 32.sp
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    if (LicenseManager.isProActive()) "PRO активен" else "Бесплатный план",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = ThemeManager.parseColor(theme.textPrimaryColor)
                                )
                                val state = LicenseManager.licenseState
                                if (LicenseManager.isProActive() && state is LicenseState.Active) {
                                    Text(
                                        "До ${LicenseManager.formatExpiry(state.expiresAt)}",
                                        fontSize = 13.sp,
                                        color = Color(0xFF81C784)
                                    )
                                } else if (!LicenseManager.isProActive()) {
                                    Text(
                                        "Лоты, виджеты и AI недоступны",
                                        fontSize = 13.sp,
                                        color = ThemeManager.parseColor(theme.textSecondaryColor)
                                    )
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (LicenseManager.isProActive())
                                Color(0xFF4CAF50).copy(alpha = 0.15f)
                            else
                                Color.Red.copy(alpha = 0.1f)
                        ) {
                            Text(
                                if (LicenseManager.isProActive()) "ACTIVE" else "FREE",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (LicenseManager.isProActive()) Color(0xFF4CAF50) else Color(0xFFEF5350)
                            )
                        }
                    }

                    if (!LicenseManager.isProActive()) {
                        var keyInput   by remember { mutableStateOf("") }
                        var keyError   by remember { mutableStateOf<String?>(null) }
                        var keySuccess by remember { mutableStateOf(false) }
                        var activating by remember { mutableStateOf(false) }

                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.1f))
                        Spacer(Modifier.height(20.dp))

                        Text(
                            "Есть ключ активации?",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = ThemeManager.parseColor(theme.textPrimaryColor)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Введи ключ вида FPT-XXXX-XXXX, полученный в боте @FPToolsBot",
                            fontSize = 13.sp,
                            color = ThemeManager.parseColor(theme.textSecondaryColor),
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(14.dp))

                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = {
                                keyInput   = it.uppercase().trim()
                                keyError   = null
                                keySuccess = false
                            },
                            label           = { Text("Лицензионный ключ") },
                            placeholder     = { Text("FPT-XXXX-XXXX") },
                            modifier        = Modifier.fillMaxWidth(),
                            singleLine      = true,
                            isError         = keyError != null,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = ThemeManager.parseColor(theme.accentColor),
                                unfocusedBorderColor = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.3f),
                                focusedTextColor     = ThemeManager.parseColor(theme.textPrimaryColor),
                                unfocusedTextColor   = ThemeManager.parseColor(theme.textPrimaryColor),
                                cursorColor          = ThemeManager.parseColor(theme.accentColor)
                            ),
                            shape = RoundedCornerShape(theme.borderRadius.dp),
                            trailingIcon = {
                                if (keySuccess) Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50)
                                )
                            },
                            supportingText = {
                                keyError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (keyInput.length < 8) { keyError = "Введите корректный ключ"; return@Button }
                                activating = true; keyError = null
                                LicenseManager.activateKey(keyInput) { res ->
                                    activating = false
                                    res.fold(
                                        onSuccess = { keySuccess = true },
                                        onFailure = { e -> keyError = e.message }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled  = !activating && keyInput.isNotEmpty(),
                            shape    = RoundedCornerShape(theme.borderRadius.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = ThemeManager.parseColor(theme.accentColor)
                            )
                        ) {
                            if (activating) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Активировать ключ", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/FPToolsBot")))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(theme.borderRadius.dp),
                            border = BorderStroke(1.dp, ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ThemeManager.parseColor(theme.accentColor)
                            )
                        ) {
                            Text("Купить PRO в @FPToolsBot", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }

                    if (LicenseManager.isProActive()) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Ключ: ${LicenseManager.currentKey.take(12)}...",
                                fontSize = 12.sp,
                                color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace
                            )
                            TextButton(
                                onClick = { LicenseManager.clearLicense() },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Отвязать устройство", fontSize = 12.sp, color = Color.Red.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }

        item {
            var expanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                colors = CardDefaults.cardColors(
                    containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = ThemeManager.parseColor(theme.accentColor),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Мой доступ к функциям",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = ThemeManager.parseColor(theme.textPrimaryColor)
                                )
                                Text(
                                    if (LicenseManager.isProActive()) "PRO — всё открыто навсегда"
                                    else "Нажми, чтобы увидеть статус каждой функции",
                                    fontSize = 12.sp,
                                    color = ThemeManager.parseColor(theme.textSecondaryColor)
                                )
                            }
                        }
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.6f)
                        )
                    }

                    AnimatedVisibility(visible = expanded, enter = fadeIn()) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            HorizontalDivider(
                                color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.1f),
                                modifier = Modifier.padding(bottom = 14.dp)
                            )
                            PremiumFeature.entries.forEach { feature ->
                                val isPro    = LicenseManager.isProActive()
                                val adHours  = LicenseManager.adUnlockHoursLeft(feature)
                                val adActive = LicenseManager.isAdUnlocked(feature)

                                val (statusText, statusColor, bgColor) = when {
                                    isPro     -> Triple("∞ PRO", Color(0xFF4CAF50), Color(0xFF4CAF50).copy(alpha = 0.1f))
                                    feature == PremiumFeature.XD_DUMPER -> Triple("Только PRO", Color(0xFFEF5350), Color.Red.copy(alpha = 0.07f)) // Исключение для демпера
                                    adActive  -> Triple("${adHours}ч осталось", Color(0xFF42A5F5), Color(0xFF1565C0).copy(alpha = 0.12f))
                                    else      -> Triple("Закрыто", Color(0xFFEF5350), Color.Red.copy(alpha = 0.07f))
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bgColor)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(feature.emoji, fontSize = 18.sp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            feature.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = ThemeManager.parseColor(theme.textPrimaryColor)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color.Transparent
                                    ) {
                                        Text(
                                            statusText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(
                                color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.1f)
                            )
                            Spacer(Modifier.height(10.dp))
                            if (!LicenseManager.isProActive()) {
                                Text(
                                    "💡 «Закрыто» означает только то, что закрыт доступ к настройкам этой функции - включить, выключить или изменить. Если функция уже была включена, она продолжает работать в фоне как обычно. Чтобы снова получить доступ к настройкам - просто посмотри рекламу, либо поддержи разработчика и купи PRO",
                                    fontSize = 11.sp,
                                    color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.55f),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = ThemeManager.parseColor(theme.accentColor),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Поддержка разработки",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = ThemeManager.parseColor(theme.textPrimaryColor),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Помогите проекту стать лучше",
                    fontSize = 14.sp,
                    color = ThemeManager.parseColor(theme.textSecondaryColor),
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Computer,
                                contentDescription = null,
                                tint = ThemeManager.parseColor(theme.accentColor),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Моя рабочая станция",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeManager.parseColor(theme.textPrimaryColor)
                            )
                            Text(
                                "Lenovo 2014 года",
                                fontSize = 13.sp,
                                color = ThemeManager.parseColor(theme.textSecondaryColor)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Я - независимый разработчик AlliSighs. Создаю качественный софт на старом ноутбуке 2014 года. Ваша поддержка поможет обновить железо и делать еще больше крутых функций!",
                        color = ThemeManager.parseColor(theme.textSecondaryColor),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("System Model: Lenovo 20AV005HMS", color = Color(0xFF00E676), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("CPU: Intel Core i5-4200M @ 2.60GHz", color = Color(0xFF00E676), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("RAM: 3 993 MB (4GB)", color = Color(0xFFFF5252), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text("OS: Windows 10 Pro", color = Color(0xFF00E676), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { openUrl("https://allisighs.github.io/pages/portfolio/") },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.15f),
                            contentColor   = ThemeManager.parseColor(theme.accentColor)
                        ),
                        shape = RoundedCornerShape(theme.borderRadius.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Посмотреть портфолио", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ImprovedTariffCard(
                    title = "Наш канал",
                    price = "Бесплатно",
                    desc = "Новости и обновления FunPay Tools",
                    gradient = listOf(Color(0xFF4B20B9), Color(0xFF8200B5)),
                    icon = Icons.AutoMirrored.Filled.Send,
                    theme = theme,
                    modifier = Modifier.weight(1f),
                    onClick = { openUrl("https://t.me/fptools") }
                )
                ImprovedTariffCard(
                    title = "Уважение+",
                    price = "Любая сумма",
                    desc = "CryptoBot счёт",
                    gradient = listOf(Color(0xFF00B0FF), Color(0xFF0091EA)),
                    icon = Icons.Default.Diamond,
                    theme = theme,
                    modifier = Modifier.weight(1f),
                    onClick = { openUrl("http://t.me/send?start=IVTSEqtWdcPG") }
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { openUrl("https://t.me/AlliSighs") },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF229ED9).copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).background(Color(0xFF229ED9).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color(0xFF229ED9), modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Связаться в Telegram", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ThemeManager.parseColor(theme.textPrimaryColor))
                        Text("@AlliSighs", fontSize = 13.sp, color = Color(0xFF229ED9), fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.5f))
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Payment, contentDescription = null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Реквизиты для перевода", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ThemeManager.parseColor(theme.textPrimaryColor))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    ImprovedCopyableField(
                        label = "USDT (TON)",
                        value = "UQCC0H4FZyfWg4bwM3yvHtNsoGgytM18us37sJPUfz4-jXE4",
                        icon = Icons.Default.CurrencyBitcoin,
                        theme = theme,
                        onCopy = { copyToClipboard("USDT адрес", it) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ImprovedCopyableField(
                        label = "Monobank (UAH)",
                        value = "4441 1144 0934 7711",
                        icon = Icons.Default.CreditCard,
                        theme = theme,
                        onCopy = { copyToClipboard("Номер карты", it) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.2f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openUrl("https://funpay.com/users/6402834/") }
                            .background(ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Мой профиль FunPay", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = ThemeManager.parseColor(theme.textPrimaryColor))
                            Text("Открыть в браузере", fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF1744), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Спасибо за вашу поддержку!", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ThemeManager.parseColor(theme.textPrimaryColor), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Вы помогаете проекту развиваться ❤️", fontSize = 13.sp, color = ThemeManager.parseColor(theme.textSecondaryColor), textAlign = TextAlign.Center)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}


@Composable
fun ImprovedTariffCard(
    title: String,
    price: String,
    desc: String,
    gradient: List<Color>,
    icon: ImageVector,
    theme: AppTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(180.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        ),
        border = BorderStroke(
            2.dp,
            Brush.verticalGradient(gradient)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.verticalGradient(
                                gradient.map { it.copy(alpha = 0.15f) }
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = gradient[0],
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ThemeManager.parseColor(theme.textPrimaryColor),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                
                Text(
                    price,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = gradient[0],
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                
                Text(
                    desc,
                    fontSize = 12.sp,
                    color = ThemeManager.parseColor(theme.textSecondaryColor),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }

            
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                tint = gradient[0].copy(alpha = 0.3f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp)
            )
        }
    }
}


@Composable
fun ImprovedCopyableField(
    label: String,
    value: String,
    icon: ImageVector,
    theme: AppTheme,
    onCopy: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy(value) },
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = ThemeManager.parseColor(theme.accentColor),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ThemeManager.parseColor(theme.textSecondaryColor),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ThemeManager.parseColor(theme.textPrimaryColor),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Копировать",
                    tint = ThemeManager.parseColor(theme.accentColor),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

data class ChatLabel(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("name") val name: String = "",
    @SerializedName("color") val color: String = "#7C4DFF"
)

data class ChatFolder(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("name") val name: String = "",
    @SerializedName("chatIds") val chatIds: List<String> = emptyList(),
    @SerializedName("isPreset") val isPreset: Boolean = false,
    @SerializedName("presetType") val presetType: String? = null
)

data class BusyModeSettings(
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("message") val message: String = "Я сейчас занят, отвечу позже",
    @SerializedName("cooldownMinutes") val cooldownMinutes: Int = 60,
    @SerializedName("autoRefund") val autoRefund: Boolean = false,
    @SerializedName("autoRefundMessage") val autoRefundMessage: Boolean = false,
    @SerializedName("keepRaise") val keepRaise: Boolean = false,
    @SerializedName("keepAutoResponse") val keepAutoResponse: Boolean = false,
    @SerializedName("keepGreeting") val keepGreeting: Boolean = false,
    @SerializedName("enabledAt") val enabledAt: Long = 0L
)

object ChatFolderManager {
    private const val PREFS = "chat_folders"
    private val gson = Gson()

    fun getLabels(context: Context): List<ChatLabel> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("labels", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<ChatLabel>>() {}.type) ?: emptyList() } catch (e: Exception) { emptyList() }
    }
    fun saveLabels(context: Context, v: List<ChatLabel>) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("labels", gson.toJson(v)).apply()

    fun getFolders(context: Context): List<ChatFolder> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("folders", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<ChatFolder>>() {}.type) ?: emptyList() } catch (e: Exception) { emptyList() }
    }
    fun saveFolders(context: Context, v: List<ChatFolder>) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("folders", gson.toJson(v)).apply()

    fun getChatLabels(context: Context): Map<String, List<String>> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("chat_labels", null) ?: return emptyMap()
        return try { gson.fromJson(json, object : TypeToken<Map<String, List<String>>>() {}.type) ?: emptyMap() } catch (e: Exception) { emptyMap() }
    }
    fun saveChatLabels(context: Context, v: Map<String, List<String>>) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("chat_labels", gson.toJson(v)).apply()

    fun getBusyMode(context: Context): BusyModeSettings {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("busy_mode", null) ?: return BusyModeSettings()
        return try { gson.fromJson(json, BusyModeSettings::class.java) ?: BusyModeSettings() } catch (e: Exception) { BusyModeSettings() }
    }
    fun saveBusyMode(context: Context, v: BusyModeSettings) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("busy_mode", gson.toJson(v)).apply()
}

@Composable
fun ChatFolderTabs(
    folders: List<ChatFolder>,
    selectedFolderId: String?,
    onFolderSelected: (String?) -> Unit,
    onManageFolders: () -> Unit,
    theme: AppTheme
) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val all = listOf(null to "Все") + folders.map { it.id as String? to it.name }

    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(8.dp))
        all.forEach { (fid, fname) ->
            val selected = fid == selectedFolderId
            val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent, label = "tb")
            val tc by animateColorAsState(if (selected) accent else textSecondary, label = "tt")
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable { onFolderSelected(fid) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(fname, fontSize = 13.sp, color = tc, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
            Spacer(Modifier.width(4.dp))
        }
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(surface.copy(alpha = 0.5f)).clickable { onManageFolders() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, null, tint = textSecondary, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
fun BusyModeCard(settings: BusyModeSettings, onToggle: () -> Unit, onConfigure: () -> Unit, theme: AppTheme) {
    val busyColor = Color(0xFFFF5722)
    val cardColor by animateColorAsState(
        if (settings.enabled) busyColor.copy(alpha = 0.15f) else ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity),
        label = "bc"
    )
    val borderColor by animateColorAsState(if (settings.enabled) busyColor else Color.Transparent, label = "bb")
    val cardH by animateDpAsState(if (settings.enabled) 80.dp else 68.dp, label = "bh")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardH)
            .border(if (settings.enabled) 1.5.dp else 0.dp, borderColor, RoundedCornerShape(theme.borderRadius.dp)),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(theme.borderRadius.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DoNotDisturb, null, tint = if (settings.enabled) busyColor else ThemeManager.parseColor(theme.textSecondaryColor), modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Режим занятости", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 15.sp)
                Text(
                    if (settings.enabled) "Активен · функции заморожены" else "Выключен",
                    color = if (settings.enabled) busyColor else ThemeManager.parseColor(theme.textSecondaryColor),
                    fontSize = 12.sp
                )
            }
            if (settings.enabled) {
                IconButton(onClick = onConfigure) {
                    Icon(Icons.Default.Settings, null, tint = busyColor, modifier = Modifier.size(20.dp))
                }
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = busyColor, checkedTrackColor = busyColor.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
fun BusyModeDialog(settings: BusyModeSettings, onSave: (BusyModeSettings) -> Unit, onDismiss: () -> Unit, theme: AppTheme) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)
    var message by remember { mutableStateOf(settings.message) }
    var cooldown by remember { mutableIntStateOf(settings.cooldownMinutes) }
    var autoRefund by remember { mutableStateOf(settings.autoRefund) }
    var autoRefundMessage by remember { mutableStateOf(settings.autoRefundMessage) }
    var keepRaise by remember { mutableStateOf(settings.keepRaise) }
    var keepAutoResp by remember { mutableStateOf(settings.keepAutoResponse) }
    var keepGreeting by remember { mutableStateOf(settings.keepGreeting) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text("Режим занятости", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 16.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Покупатели получат сообщение, другие функции заморожены (кроме выбранных)", color = textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
                item {
                    Text("Сообщение покупателю", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = message, onValueChange = { message = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Я сейчас занят...", color = textSecondary) },
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = textSecondary.copy(alpha = 0.4f), focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                    )
                }
                item {
                    Text("Повторять не чаще: $cooldown мин", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Slider(value = cooldown.toFloat(), onValueChange = { cooldown = it.toInt() }, valueRange = 0f..480f, steps = 47,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                    Text(if (cooldown == 0) "Каждый раз" else "Если покупатель снова пишет, то отправим через $cooldown мин", color = textSecondary, fontSize = 11.sp)
                }
                item {
                    Text("При новом заказе", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    listOf(
                        Triple("Отклонить заказ (вернуть деньги)", autoRefund) { v: Boolean -> autoRefund = v },
                        Triple("Ответить покупателю сообщением", autoRefundMessage) { v: Boolean -> autoRefundMessage = v }
                    ).forEach { (label, checked, onCh) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(checked = checked, onCheckedChange = onCh, colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.4f)))
                        }
                    }
                }
                item {
                    Text("Оставить включёнными", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    listOf(
                        Triple("Автоподнятие", keepRaise) { v: Boolean -> keepRaise = v },
                        Triple("Все автоответы", keepAutoResp) { v: Boolean -> keepAutoResp = v },
                        Triple("Приветствие", keepGreeting) { v: Boolean -> keepGreeting = v },
                    ).forEach { (label, checked, onCh) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(checked = checked, onCheckedChange = onCh, colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.4f)))
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена", color = textSecondary) }
                        Button(
                            onClick = { onSave(BusyModeSettings(settings.enabled, message, cooldown, autoRefund, autoRefundMessage, keepRaise, keepAutoResp, keepGreeting)); onDismiss() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) { Text("Сохранить", color = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatItemMenu(
    chatId: String,
    labels: List<ChatLabel>,
    chatLabels: Map<String, List<String>>,
    folders: List<ChatFolder>,
    onAddLabel: (String) -> Unit,
    onAddToFolder: (String) -> Unit,
    onCreateFolder: () -> Unit,
    onManageLabels: () -> Unit,
    onArchiveToggle: () -> Unit,
    isArchived: Boolean,
    onDismiss: () -> Unit,
    theme: AppTheme
) {
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)
    val accent = ThemeManager.parseColor(theme.accentColor)
    var showLabels by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }

    val currentLabels = chatLabels[chatId] ?: emptyList()

    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(modifier = Modifier.padding(16.dp).widthIn(min = 240.dp)) {
                Text("Чат", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showLabels = !showLabels }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Label, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Метка", color = textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(if (showLabels) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = textSecondary, modifier = Modifier.size(18.dp))
                }
                if (showLabels) {
                    if (labels.isEmpty()) {
                        Text("У вас нет созданных меток", color = textSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 32.dp, bottom = 4.dp))
                    } else {
                        labels.forEach { label ->
                            val hasLabel = currentLabels.contains(label.id)
                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onAddLabel(label.id) }.padding(start = 32.dp, top = 6.dp, bottom = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(ThemeManager.parseColor(label.color)).then(if (hasLabel) Modifier.border(2.dp, Color.White, CircleShape) else Modifier))
                                Spacer(Modifier.width(10.dp))
                                Text(label.name, color = textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (hasLabel) Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onManageLabels(); onDismiss() }.padding(start = 32.dp, top = 6.dp, bottom = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, tint = accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Управление метками", color = accent, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showFolders = !showFolders }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Папка", color = textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(if (showFolders) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = textSecondary, modifier = Modifier.size(18.dp))
                }
                if (showFolders) {
                    folders.filter { !it.isPreset }.forEach { folder ->
                        val inFolder = folder.chatIds.contains(chatId)
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onAddToFolder(folder.id) }.padding(start = 32.dp, top = 6.dp, bottom = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = textSecondary, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(folder.name, color = textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            if (inFolder) Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.size(14.dp))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onCreateFolder(); onDismiss() }.padding(start = 32.dp, top = 6.dp, bottom = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, tint = accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Новая папка", color = accent, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(4.dp))

                // аутизм ебанный
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable { onArchiveToggle(); onDismiss() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                        null,
                        tint = if (isArchived) Color(0xFFF44336) else accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isArchived) "Убрать из архива" else "В архив",
                        color = if (isArchived) Color(0xFFF44336) else textPrimary,
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Закрыть", color = textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

private val labelColors = listOf(
    "#7C4DFF", "#F44336", "#2196F3", "#4CAF50",
    "#FF9800", "#E91E63", "#00BCD4", "#FF5722",
    "#9C27B0", "#3F51B5", "#009688", "#CDDC39",
    "#8BC34A", "#FF6F00", "#0097A7", "#AD1457"
)

private val presetFolderDefs = listOf(
    ChatFolder(id = "preset_unread", name = "Непрочитанные", isPreset = true, presetType = "unread"),
    ChatFolder(id = "preset_labeled", name = "С метками", isPreset = true, presetType = "labeled"),
    ChatFolder(id = "preset_archived", name = "Архив", isPreset = true, presetType = "archived"),
)

@Composable
fun ManageFoldersDialog(
    folders: List<ChatFolder>,
    onFoldersChanged: (List<ChatFolder>) -> Unit,
    onDismiss: () -> Unit,
    theme: AppTheme
) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)
    var newFolder by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text("Готовые группы", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    presetFolderDefs.forEach { preset ->
                        val isAdded = folders.any { it.id == preset.id }
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { onFoldersChanged(if (isAdded) folders.filter { it.id != preset.id } else folders + preset) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(preset.name, color = textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Icon(if (isAdded) Icons.Default.Check else Icons.Default.Add, null, tint = if (isAdded) accent else textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                item {
                    Text("Мои папки", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    val custom = folders.filter { !it.isPreset }
                    if (custom.isEmpty()) Text("Пока нет", color = textSecondary, fontSize = 13.sp)
                    custom.forEach { folder ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(folder.name, color = textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onFoldersChanged(folders.filter { it.id != folder.id }) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFF44336), modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = newFolder, onValueChange = { newFolder = it }, modifier = Modifier.weight(1f),
                            placeholder = { Text("Название папки", color = textSecondary, fontSize = 13.sp) }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = textSecondary.copy(alpha = 0.4f), focusedTextColor = textPrimary, unfocusedTextColor = textPrimary))
                        IconButton(onClick = { if (newFolder.isNotBlank()) { onFoldersChanged(folders + ChatFolder(name = newFolder.trim())); newFolder = "" } }) {
                            Icon(Icons.Default.Add, null, tint = accent)
                        }
                    }
                }

                item {
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                        Text("Готово", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ManageLabelsDialog(
    labels: List<ChatLabel>,
    onLabelsChanged: (List<ChatLabel>) -> Unit,
    onDismiss: () -> Unit,
    theme: AppTheme
) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)
    var newLabel by remember { mutableStateOf("") }


    var pickedColor by remember { mutableStateOf(labelColors.first()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = surface),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text("Метки пользователей", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    if (labels.isEmpty()) Text("Пока нет", color = textSecondary, fontSize = 13.sp)
                    labels.forEach { label ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(ThemeManager.parseColor(label.color)))
                            Spacer(Modifier.width(8.dp))
                            Text(label.name, color = textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onLabelsChanged(labels.filter { it.id != label.id }) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFF44336), modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        labelColors.forEach { hex ->
                            Box(
                                modifier = Modifier.padding(bottom = 6.dp).size(24.dp).clip(CircleShape)
                                    .background(ThemeManager.parseColor(hex))
                                    .then(if (hex == pickedColor) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                                    .clickable { pickedColor = hex }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(ThemeManager.parseColor(pickedColor)))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(value = newLabel, onValueChange = { newLabel = it }, modifier = Modifier.weight(1f),
                            placeholder = { Text("Название метки", color = textSecondary, fontSize = 13.sp) }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = textSecondary.copy(alpha = 0.4f), focusedTextColor = textPrimary, unfocusedTextColor = textPrimary))
                        IconButton(onClick = { if (newLabel.isNotBlank()) { onLabelsChanged(labels + ChatLabel(name = newLabel.trim(), color = pickedColor)); newLabel = "" } }) {
                            Icon(Icons.Default.Add, null, tint = accent)
                        }
                    }
                }

                item {
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                        Text("Готово", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun DumperSettingsDialog(repository: FunPayRepository, theme: AppTheme, onDismiss: () -> Unit) {
    var settings by remember { mutableStateOf(repository.getDumperSettings()) }
    var editingLot by remember { mutableStateOf<DumperLotConfig?>(null) }

    if (editingLot != null) {
        DumperLotEditDialog(
            initialConfig = editingLot!!,
            theme = theme,
            onSave = { updatedLot ->
                val newLots = settings.lots.filter { it.id != updatedLot.id }.toMutableList()
                newLots.add(updatedLot)
                settings = settings.copy(lots = newLots)
                repository.saveDumperSettings(settings)
                editingLot = null
            },
            onDismiss = { editingLot = null }
        )
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
                shape = RoundedCornerShape(theme.borderRadius.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("XD Dumper - Управление", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { editingLot = DumperLotConfig() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Добавить лот")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(modifier = Modifier.weight(1f, false)) {
                        if (settings.lots.isEmpty()) {
                            item { Text("Нет настроенных лотов.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 14.sp) }
                        }
                        items(settings.lots) { lot ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editingLot = lot },
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Лот ID: ${lot.lotId}", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold)
                                        Text("Категория: ${lot.categoryId}", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                                    }
                                    Switch(
                                        checked = lot.enabled,
                                        onCheckedChange = { isEnabled ->
                                            val updated = lot.copy(enabled = isEnabled)
                                            val newLots = settings.lots.map { if (it.id == lot.id) updated else it }
                                            settings = settings.copy(lots = newLots)
                                            repository.saveDumperSettings(settings)
                                        },
                                        modifier = Modifier.scale(0.8f),
                                        colors = SwitchDefaults.colors(checkedThumbColor = ThemeManager.parseColor(theme.accentColor))
                                    )
                                    IconButton(onClick = {
                                        val newLots = settings.lots.filter { it.id != lot.id }
                                        settings = settings.copy(lots = newLots)
                                        repository.saveDumperSettings(settings)
                                    }) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))) { Text("Закрыть", color = ThemeManager.parseColor(theme.textPrimaryColor)) }
                }
            }
        }
    }
}

@Composable
fun DumperLotEditDialog(initialConfig: DumperLotConfig, theme: AppTheme, onSave: (DumperLotConfig) -> Unit, onDismiss: () -> Unit) {
    var config by remember { mutableStateOf(initialConfig) }

    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(theme.borderRadius.dp),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Настройка Лота", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        OutlinedTextField(
                            value = config.lotId, onValueChange = { config = config.copy(lotId = it) },
                            label = { Text("ID вашего Лота (только цифры)") }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = config.categoryId, onValueChange = { config = config.copy(categoryId = it) },
                            label = { Text("ID Категории (node, просим его, ибо парсинг категории с лота жрёт батарею)") }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = config.keywords, onValueChange = { config = config.copy(keywords = it) },
                            label = { Text("Ключевые слова | через ЭТУ линию | яблоко | БРАВЛ ПАСС") }, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = config.priceMin.toString(), onValueChange = { config = config.copy(priceMin = it.toDoubleOrNull() ?: 1.0) },
                                label = { Text("Цена, ниже которой демпер не упадёт") }, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = config.priceMax.toString(), onValueChange = { config = config.copy(priceMax = it.toDoubleOrNull() ?: 99999.0) },
                                label = { Text("Макс. цена") }, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = config.priceStep.toString(), onValueChange = { config = config.copy(priceStep = it.toDoubleOrNull() ?: 1.0) },
                                label = { Text("Шаг (руб)") }, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = config.priceDivider.toString(),
                                onValueChange = { config = config.copy(priceDivider = it.toDoubleOrNull() ?: 0.0) },
                                label = { Text("Делить цену на") }, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = config.fastPriceCheck, onCheckedChange = { config = config.copy(fastPriceCheck = it) })
                            Text("Fast Price Check (глубокий парсинг, жрёт батарею)", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 12.sp)
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = config.ignoreZeroRating, onCheckedChange = { config = config.copy(ignoreZeroRating = it) })
                            Text("Игнор продавцов без нормального рейтинга", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 12.sp)
                        }
                    }
                    item {
                        Text("Интервал обновления: ${config.updateInterval} сек (ставь 10 секунд, если норм батарея)", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 12.sp)
                        Slider(
                            value = config.updateInterval.toFloat(), onValueChange = { config = config.copy(updateInterval = it.toInt()) },
                            valueRange = 10f..300f, steps = 29
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))) { Text("Отмена", color = ThemeManager.parseColor(theme.textPrimaryColor)) }
                    Button(onClick = { onSave(config) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))) { Text("Сохранить") }
                }
            }
        }
    }
}

@Composable
fun StrictProOnlySettingCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    icon: ImageVector,
    theme: AppTheme,
    onCheckedChange: (Boolean) -> Unit,
    onProRequired: () -> Unit
) {
    val isPro = LicenseManager.isProActive()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(theme.borderRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor)
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPro) ThemeManager.parseColor(theme.accentColor) else Color.Gray.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = ThemeManager.parseColor(theme.textPrimaryColor)
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = ThemeManager.parseColor(theme.textSecondaryColor),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                Switch(
                    checked = checked && isPro,
                    onCheckedChange = {
                        if (isPro) onCheckedChange(it) else onProRequired()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ThemeManager.parseColor(theme.accentColor),
                        checkedTrackColor = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.4f),
                        uncheckedThumbColor = if (!isPro) Color.Gray.copy(alpha = 0.5f) else Color.White,
                        uncheckedTrackColor = Color.Gray.copy(alpha = 0.2f)
                    )
                )
                if (!isPro) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "PRO",
                        tint = ThemeManager.parseColor(theme.accentColor),
                        modifier = Modifier.size(10.dp).align(Alignment.TopEnd)
                    )
                }
            }
        }
    }
}
