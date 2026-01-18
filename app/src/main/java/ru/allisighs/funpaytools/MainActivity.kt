package ru.allisighs.funpaytools

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.ui.draw.scale
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.CookieManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val repository = FunPayRepository(this)
        val startDest = if (repository.hasAuth()) "dashboard" else "welcome"

        setContent {
            MaterialTheme(colorScheme = DarkColorScheme) {
                FunPayToolsApp(startDest, repository)
            }
        }
    }
}

@Composable
fun FunPayToolsApp(startDestination: String, repository: FunPayRepository) {
    val navController = rememberNavController()
    var showSplash by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showSplash) {
            SplashScreen(onTimeout = { showSplash = false })
        } else {
            Box(modifier = Modifier.fillMaxSize().background(AppGradient)) {
                NavHost(navController = navController, startDestination = startDestination) {
                    composable("welcome") { WelcomeScreen(navController) }
                    composable("permissions") {
                        AppScaffold("Доступы", navController) { PermissionsScreen(navController, repository) }
                    }
                    composable("auth_method") {
                        AppScaffold("Вход", navController) { AuthMethodScreen(navController) }
                    }
                    composable("manual_login") {
                        AppScaffold("Через Golden Key", navController) { ManualLoginScreen(navController, repository) }
                    }
                    composable("web_login") {
                        AppScaffold("Через браузер", navController) { WebLoginScreen(navController, repository) }
                    }
                    composable("dashboard") { DashboardScreen(navController, repository) }
                    composable("settings") {
                        AppScaffold("Настройки", navController) { SettingsScreen(navController, repository) }
                    }
                    composable("chat/{chatId}/{username}") { backStackEntry ->
                        val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                        val username = backStackEntry.arguments?.getString("username") ?: ""
                        AppScaffold(username, navController) { ChatDetailScreen(chatId, repository) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(title: String, navController: NavController, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                    Text("v$APP_VERSION", fontSize = 14.sp, color = TextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(64.dp))
            Button(onClick = { navController.navigate("permissions") }, colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Войти", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun PermissionsScreen(navController: NavController, repository: FunPayRepository) {
    val context = LocalContext.current
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isIgnoringBattery by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasAuth = remember { repository.hasAuth() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Фоновая работа", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("Чтобы бот не засыпал, нужно выдать разрешения.", fontSize = 14.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.liquidGlass().clickable {
            if (!isIgnoringBattery) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${context.packageName}")
                try { context.startActivity(intent) } catch (_: Exception) {}
            }
        }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isIgnoringBattery) Icons.Default.CheckCircle else Icons.Default.BatteryAlert, null, tint = if (isIgnoringBattery) Color(0xFF00E676) else Color.Red)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Без ограничений", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(if (isIgnoringBattery) "Разрешено" else "Нажмите, чтобы разрешить", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.liquidGlass().clickable {
            openAutoStartSettings(context)
        }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SettingsPower, null, tint = PurpleAccent)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Автозапуск", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Нажмите для настройки (Xiaomi, Vivo...)", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (hasAuth) {
                    navController.popBackStack()
                } else {
                    navController.navigate("auth_method")
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (isIgnoringBattery) PurpleAccent else Color.Gray),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (hasAuth) "Готово" else "Продолжить")
        }
    }
}

fun openAutoStartSettings(context: Context) {
    val intents = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        Intent(Settings.ACTION_SETTINGS)
    )

    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) { }
    }
    Toast.makeText(context, "Настройки не найдены автоматически", Toast.LENGTH_SHORT).show()
}

@Composable
fun AuthMethodScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        AuthCard(Icons.Default.Key, "Ввести ключ", "Golden Key") { navController.navigate("manual_login") }
        Spacer(modifier = Modifier.height(16.dp))
        AuthCard(Icons.Default.Public, "Войти через Web", "Сайт FunPay") { navController.navigate("web_login") }
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
            if (key.length == 32) { repository.saveGoldenKey(key); navController.navigate("dashboard") { popUpTo("welcome") { inclusive = true } } }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)) { Text("Войти") }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(navController: NavController, repository: FunPayRepository) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFB00020)).padding(8.dp)) {
            Text("Вход через Google не работает в приложении! Используйте другие методы.", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
        }
        AndroidView(factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val cookies = CookieManager.getInstance().getCookie(url)
                        if (cookies?.contains("golden_key") == true) {
                            val key = cookies.split(";").find { it.trim().startsWith("golden_key") }?.split("=")?.get(1)
                            if (key != null) { repository.saveGoldenKey(key); navController.navigate("dashboard") { popUpTo("welcome") { inclusive = true } } }
                        }
                    }
                }
                loadUrl("https://funpay.com/account/login")
            }
        }, modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, repository: FunPayRepository) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val logs by LogManager.logs.collectAsState()
    var chats by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(Intent(context, FunPayService::class.java))
        }

        launch {
            val info = repository.checkForUpdates()
            if (info != null && info.hasUpdate) {
                updateInfo = info
            }
        }

        while (true) {
            chats = repository.getChats()
            delay(5000)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("FunPay Tools", color = TextPrimary, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/fptools"))
                        context.startActivity(intent)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Telegram",
                            tint = TextPrimary
                        )
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, "Settings", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0A0A0A)) {
                listOf(Triple(0, Icons.Default.Chat, "Чаты"), Triple(1, Icons.Default.Tune, "Управление"), Triple(2, Icons.Default.Terminal, "Консоль")).forEach { (index, icon, label) ->
                    NavigationBarItem(selected = selectedTab == index, onClick = { selectedTab = index }, icon = { Icon(icon, null) }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = PurpleAccent, selectedTextColor = PurpleAccent, indicatorColor = PurpleAccent.copy(alpha = 0.2f), unselectedIconColor = TextSecondary, unselectedTextColor = TextSecondary))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (updateInfo != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PurpleAccent)
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo!!.htmlUrl))
                            context.startActivity(intent)
                        }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Доступна новая версия: ${updateInfo!!.newVersion}!", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> ChatsList(chats, navController, repository)
                    1 -> ControlScreen(repository)
                    2 -> ConsoleView(logs)
                }
            }
        }
    }
}

@Composable
fun ControlScreen(repository: FunPayRepository) {
    var autoRaise by remember { mutableStateOf(repository.getSetting("auto_raise")) }
    var autoResponse by remember { mutableStateOf(repository.getSetting("auto_response")) }
    var autoReviewReply by remember { mutableStateOf(repository.getSetting("auto_review_reply")) }
    var pushNotifications by remember { mutableStateOf(repository.getSetting("push_notifications")) }

    var showCommandsDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }

    var greetingSettings by remember { mutableStateOf(repository.getGreetingSettings()) }
    var showGreetingDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Column(modifier = Modifier.liquidGlass().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Push уведомления", color = TextPrimary, fontWeight = FontWeight.Bold); Text("Отдельные уведомления о сообщениях", color = TextSecondary, fontSize = 12.sp) }
                Switch(checked = pushNotifications, onCheckedChange = { pushNotifications = it; repository.setSetting("push_notifications", it) })
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.liquidGlass().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Автоподнятие", color = TextPrimary, fontWeight = FontWeight.Bold); Text("Поднимает лоты", color = TextSecondary, fontSize = 12.sp) }
                Switch(checked = autoRaise, onCheckedChange = { autoRaise = it; repository.setSetting("auto_raise", it) })
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.liquidGlass().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Автоответчик", color = TextPrimary, fontWeight = FontWeight.Bold); Text("Ответы на команды", color = TextSecondary, fontSize = 12.sp) }
                Switch(checked = autoResponse, onCheckedChange = { autoResponse = it; repository.setSetting("auto_response", it) })
            }
            if (autoResponse) { Spacer(modifier = Modifier.height(8.dp)); Button(onClick = { showCommandsDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = GlassWhite), modifier = Modifier.fillMaxWidth()) { Text("Настроить команды", color = TextPrimary) } }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.liquidGlass().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Ответ на отзывы", color = TextPrimary, fontWeight = FontWeight.Bold); Text("Автоответ при новых отзывах", color = TextSecondary, fontSize = 12.sp) }
                Switch(checked = autoReviewReply, onCheckedChange = { autoReviewReply = it; repository.setSetting("auto_review_reply", it) })
            }
            if (autoReviewReply) { Spacer(modifier = Modifier.height(8.dp)); Button(onClick = { showReviewDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = GlassWhite), modifier = Modifier.fillMaxWidth()) { Text("Настроить шаблоны", color = TextPrimary) } }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.liquidGlass().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Автоприветствие", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Новым клиентам", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = greetingSettings.enabled,
                    onCheckedChange = {
                        val newSettings = greetingSettings.copy(enabled = it)
                        greetingSettings = newSettings
                        repository.saveGreetingSettings(newSettings)
                    }
                )
            }
            if (greetingSettings.enabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showGreetingDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GlassWhite),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Настроить текст", color = TextPrimary)
                }
            }
        }
    }
    if (showCommandsDialog) CommandsDialog(repository) { showCommandsDialog = false }
    if (showReviewDialog) ReviewsDialog(repository) { showReviewDialog = false }
    if (showGreetingDialog) GreetingDialog(repository, greetingSettings) {
        showGreetingDialog = false
        greetingSettings = repository.getGreetingSettings()
    }
}

@Composable
fun CommandsDialog(repository: FunPayRepository, onDismiss: () -> Unit) {
    var commands by remember { mutableStateOf(repository.getCommands()) }
    var trigger by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Команды", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(commands) { cmd ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) { Text(cmd.trigger, color = PurpleAccent, fontWeight = FontWeight.Bold); Text(cmd.response, color = TextSecondary, maxLines = 1) }
                            IconButton(onClick = { commands = commands - cmd; repository.saveCommands(commands) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        }
                        HorizontalDivider(color = GlassBorder)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = trigger, onValueChange = { trigger = it }, label = { Text("Триггер") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = response, onValueChange = { response = it }, label = { Text("Ответ") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { if (trigger.isNotEmpty() && response.isNotEmpty()) { commands = commands + AutoResponseCommand(trigger, response, false); repository.saveCommands(commands); trigger = ""; response = "" } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)) { Text("Добавить") }
            }
        }
    }
}

@Composable
fun ReviewsDialog(repository: FunPayRepository, onDismiss: () -> Unit) {
    var templates by remember { mutableStateOf(repository.getReviewTemplates()) }
    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Шаблоны отзывов", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                Text("\$username, \$order_id, \$lot_name", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
                HorizontalDivider(color = GlassBorder)
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(templates) { template ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("⭐".repeat(template.star), fontSize = 14.sp, color = Color(0xFFFFC107))
                                Switch(
                                    checked = template.enabled,
                                    onCheckedChange = { isEnabled ->
                                        templates = templates.map {
                                            if (it.star == template.star) it.copy(enabled = isEnabled) else it
                                        }
                                    },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                            OutlinedTextField(value = template.text, onValueChange = { newText ->
                                templates = templates.map { if (it.star == template.star) it.copy(text = newText) else it }
                            }, label = { Text("Ответ") }, modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { repository.saveReviewTemplates(templates); onDismiss() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)) { Text("Сохранить") }
            }
        }
    }
}

@Composable
fun GreetingDialog(repository: FunPayRepository, currentSettings: GreetingSettings, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(currentSettings.text) }
    var cooldown by remember { mutableStateOf(currentSettings.cooldownHours.toString()) }
    var ignoreSystem by remember { mutableStateOf(currentSettings.ignoreSystemMessages) }

    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Настройки приветствия", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Текст") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                Text("Переменные: \$username", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = cooldown,
                    onValueChange = { if (it.all { char -> char.isDigit() }) cooldown = it },
                    label = { Text("Не повторять приветствие (часов)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                Text(
                    text = "Если этот же человек напишет снова в течение этого времени, бот промолчит, чтобы не здороваться каждый раз.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Switch(checked = ignoreSystem, onCheckedChange = { ignoreSystem = it }, modifier = Modifier.scale(0.8f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Игнорировать системные", color = TextPrimary, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        repository.saveGreetingSettings(
                            GreetingSettings(
                                enabled = true,
                                text = text,
                                cooldownHours = cooldown.toIntOrNull() ?: 48,
                                ignoreSystemMessages = ignoreSystem
                            )
                        )
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                ) { Text("Сохранить") }
            }
        }
    }
}

@Composable
fun ChatsList(chats: List<ChatItem>, navController: NavController, repository: FunPayRepository) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chats) { chat ->
            Box(modifier = Modifier.liquidGlass().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).clickable {
                            navController.navigate("chat/${chat.id}/${chat.username}")
                        }
                    ) {
                        if (chat.isUnread) {
                            Box(modifier = Modifier.size(10.dp).background(PurpleAccent, CircleShape))
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column {
                            Text(chat.username, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(chat.lastMessage, color = if (chat.isUnread) TextPrimary else TextSecondary, fontSize = 14.sp)
                            if (chat.date.isNotEmpty()) Text(chat.date, fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.7f))
                        }
                    }
                    IconButton(onClick = {
                        navController.navigate("chat/${chat.id}/${chat.username}")
                    }) {
                        Icon(Icons.Default.Chat, contentDescription = "Open History", tint = PurpleAccent)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatDetailScreen(chatId: String, repository: FunPayRepository) {
    var messages by remember { mutableStateOf<List<MessageItem>>(emptyList()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isAiProcessing by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    val imageId = repository.uploadImage(uri)
                    if (imageId != null) {
                        repository.sendMessage(chatId, "", imageId)
                    }
                }
            }
        }
    )

    LaunchedEffect(chatId) {
        while (true) {
            val newMessages = repository.getChatHistory(chatId)

            if (newMessages.size != messages.size) {
                messages = newMessages
                if (messages.isNotEmpty()) scope.launch { listState.scrollToItem(messages.lastIndex) }
            }
            delay(6400)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(messages) { msg ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (msg.isMe) PurpleAccent else GlassWhite)
                            .padding(8.dp)
                    ) {
                        Column {
                            if (!msg.isMe) Text(
                                msg.author,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )

                            if (msg.imageUrl != null) {
                                AsyncImage(
                                    model = msg.imageUrl,
                                    contentDescription = "Image",
                                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            if (msg.text.isNotEmpty() && msg.text != "Фотография") {
                                Text(msg.text, color = TextPrimary)
                            }

                            Text(msg.time, fontSize = 9.sp, color = TextSecondary.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.End))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Default.Add, contentDescription = "Attach", tint = TextPrimary)
            }

            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Сообщение...", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,

                        focusedBorderColor = PurpleAccent,
                        unfocusedBorderColor = Color.Transparent,

                        focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),

                        cursorColor = PurpleAccent,
                        selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
                            handleColor = PurpleAccent,
                            backgroundColor = PurpleAccent.copy(alpha = 0.4f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (inputText.isNotBlank() && !isAiProcessing) {
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
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PurpleAccent)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "AI Rewrite", tint = PurpleAccent)
                            }
                        }
                    }
                )
            }

            IconButton(onClick = {
                if (inputText.isNotBlank()) {
                    val textToSend = inputText
                    inputText = ""

                    // Optimistic update
                    val newMessage = MessageItem(
                        id = System.currentTimeMillis().toString(),
                        author = "Вы",
                        text = textToSend,
                        isMe = true,
                        time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                        imageUrl = null
                    )
                    messages = messages + newMessage
                    scope.launch { listState.scrollToItem(messages.lastIndex) }

                    scope.launch {
                        repository.sendMessage(chatId, textToSend)
                    }
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = PurpleAccent)
            }
        }
    }
}

@Composable
fun SettingsScreen(navController: NavController, repository: FunPayRepository) {
    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { navController.navigate("auth_method") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = GlassWhite)) { Text("Сменить Golden Key", color = TextPrimary) }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("permissions") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = GlassWhite)) { Text("Настройки доступа", color = TextPrimary) }
    }
}

@Composable
fun ConsoleView(logs: List<String>) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.weight(1f).liquidGlass().padding(12.dp), reverseLayout = false) {
            items(logs) { log ->
                Text(log, color = Color(0xFFB01818), fontSize = 10.sp, lineHeight = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    try {
                        val fullLog = logs.take(500).joinToString("\n\n")
                        val clip = ClipData.newPlainText("FunPay Logs", fullLog)
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "Скопировано (последние 500)", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) { Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show() }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = GlassWhite)
            ) { Text("КОПИРОВАТЬ", fontSize = 12.sp) }
            Button(
                onClick = {
                    val path = LogManager.saveLogsToFile(context)
                    Toast.makeText(context, path, Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
            ) { Text("В ФАЙЛ", fontSize = 12.sp) }
        }
    }
}