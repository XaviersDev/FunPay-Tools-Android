

package ru.allisighs.funpaytools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*






enum class PremiumFeature(
    val displayName: String,
    val description: String,
    val emoji: String
) {
    LOTS(
        "Мои лоты",
        "Редактирование лотов, массовое включение и выключение",
        "📦"
    ),
    MULTI_ACCOUNT(
        "Мультиаккаунт",
        "Добавление второго и последующих аккаунтов FunPay",
        "👥"
    ),
    WIDGET_PROFILE(
        "Виджет Профиль",
        "Аватарка, рейтинг и ник на рабочем столе",
        "🪟"
    ),
    RMTHUB(
        "RMTHub",
        "Глобальный поиск по базе пользователей",
        "🔧"
    ),
    REVIEW_AI(
        "AI-режим ответа на отзывы",
        "Генерация ответов через нейросеть. Шаблоны работают бесплатно.",
        "🤖"
    ),
    AUTO_REFUND(
        "Авто-возврат",
        "Автоматический возврат средств по заданным условиям",
        "💸"
    ),
    REVIEW_REQUEST(
        "Просьба отзыва",
        "Автоматическая просьба оставить отзыв после подтверждения заказа",
        "⭐"
    ),
    XD_DUMPER(
        "XD Dumper",
        "Авто-демпинг цен конкурентов",
        "📉"
    )
}




private val PREMIUM_ROUTES: Map<String, PremiumFeature> = mapOf(
    "lots"             to PremiumFeature.LOTS,
    "lot_edit"         to PremiumFeature.LOTS,
    "rmthub_search"    to PremiumFeature.RMTHUB,
    "widgets_settings" to PremiumFeature.WIDGET_PROFILE,
    "accounts"         to PremiumFeature.MULTI_ACCOUNT
)






data class LicenseData(
    val key: String = "",
    val deviceId: String = "",
    val activatedAt: Long = 0L,
    val expiresAt: Long = 0L,
    val isRevoked: Boolean = false,
    val purchasedBy: Long = 0L
)

sealed class LicenseState {
    data class Active(val expiresAt: Long, val key: String) : LicenseState()
    object None    : LicenseState()
    object Revoked : LicenseState()
}






object LicenseManager {

    private const val PREFS        = "fpt_license"
    private const val K_KEY        = "saved_key"
    private const val K_EXPIRES    = "expires_at"
    private const val K_REVOKED    = "is_revoked"
    private const val K_LAST_CHECK = "last_checked"
    private const val K_AD_PREFIX  = "ad_unlock_"
    private const val K_AI_COUNT   = "ai_click_count"
    private const val K_AI_DATE    = "ai_click_date"
    private const val K_AI_BONUS   = "ai_ad_bonus"
    private const val K_DEVICE_ID  = "device_id"

    private const val AD_DURATION_MS = 48L * 3_600_000L
    private const val CACHE_VALID_MS  = 6L * 3_600_000L

    const val AI_FREE_DAILY = 25
    const val AI_AD_BONUS   = 25

    private var ctx: Context? = null

    
    
    var licenseState: LicenseState by mutableStateOf(LicenseState.None)
        private set

    var currentKey: String by mutableStateOf("")
        private set


    fun init(context: Context) {
        ctx = context.applicationContext
        loadCache()        
        refreshFirebase()  
    }

    
    fun hasAccess(feature: PremiumFeature): Boolean =
        isProActive() || isAdUnlocked(feature)

    fun isProActive(): Boolean {
        val s = licenseState as? LicenseState.Active ?: return false
        return s.expiresAt == 0L || System.currentTimeMillis() < s.expiresAt
    }

    
    fun isAdUnlocked(f: PremiumFeature): Boolean {
        val c = ctx ?: return false   
        val t = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(K_AD_PREFIX + f.name, 0L)
        return t > 0 && System.currentTimeMillis() < t
    }

    fun adUnlockHoursLeft(f: PremiumFeature): Long {
        val c = ctx ?: return 0L
        val rem = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(K_AD_PREFIX + f.name, 0L) - System.currentTimeMillis()
        return if (rem > 0) rem / 3_600_000L else 0L
    }

    fun applyAdUnlock(f: PremiumFeature) {
        prefs()?.edit()
            ?.putLong(K_AD_PREFIX + f.name, System.currentTimeMillis() + AD_DURATION_MS)
            ?.apply()
    }

    
    
    
    
    fun consumeAiClick(): Boolean {
        val p = prefs() ?: return true  
        val today = todayStr()
        if (p.getString(K_AI_DATE, "") != today) {
            
            p.edit().putString(K_AI_DATE, today).putInt(K_AI_COUNT, 0).apply()
        }
        val used  = p.getInt(K_AI_COUNT, 0)
        val bonus = p.getInt(K_AI_BONUS, 0)
        val limit = AI_FREE_DAILY + bonus
        if (used >= limit) return false
        p.edit().putInt(K_AI_COUNT, used + 1).apply()
        return true
    }

    fun aiClicksLeft(): Int {
        val p = prefs() ?: return AI_FREE_DAILY
        val today = todayStr()
        if (p.getString(K_AI_DATE, "") != today) return AI_FREE_DAILY + p.getInt(K_AI_BONUS, 0)
        val used  = p.getInt(K_AI_COUNT, 0)
        val bonus = p.getInt(K_AI_BONUS, 0)
        return maxOf(0, AI_FREE_DAILY + bonus - used)
    }

    fun applyAiAdBonus() {
        val p = prefs() ?: return
        p.edit().putInt(K_AI_BONUS, p.getInt(K_AI_BONUS, 0) + AI_AD_BONUS).apply()
    }

    
    fun deviceId(): String {
        val p  = prefs() ?: return java.util.UUID.randomUUID().toString()
        val cached = p.getString(K_DEVICE_ID, null)
        if (cached != null) return cached
        val id = Settings.Secure.getString(ctx?.contentResolver, Settings.Secure.ANDROID_ID)
            ?: java.util.UUID.randomUUID().toString()
        p.edit().putString(K_DEVICE_ID, id).apply()
        return id
    }

    fun activateKey(key: String, onResult: (Result<Long>) -> Unit) {
        val url = "https://funpay-tools-default-rtdb.firebaseio.com/licenses/$key.json"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000

                val response = connection.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(response)

                val isRevoked   = json.optBoolean("isRevoked", false)
                val expiresAt   = json.optLong("expiresAt", 0L)
                val deviceId    = json.optString("deviceId", "")

                withContext(Dispatchers.Main) {
                    when {
                        isRevoked ->
                            onResult(Result.failure(Exception("Ключ заблокирован")))
                        expiresAt > 0 && System.currentTimeMillis() > expiresAt ->
                            onResult(Result.failure(Exception("Срок ключа истёк")))
                        deviceId.isNotEmpty() && deviceId != deviceId() ->
                            onResult(Result.failure(Exception("Ключ привязан к другому устройству")))
                        else -> {
                            // Записываем deviceId через PATCH
                            patchDeviceId(key)
                            saveCache(key, expiresAt, false)
                            licenseState = LicenseState.Active(expiresAt, key)
                            currentKey   = key
                            onResult(Result.success(expiresAt))
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (loadCache()) onResult(Result.success(
                        (licenseState as? LicenseState.Active)?.expiresAt ?: 0L
                    ))
                    else onResult(Result.failure(Exception("Нет соединения")))
                }
            }
        }
    }

    private fun patchDeviceId(key: String) {
        try {
            val url = "https://funpay-tools-default-rtdb.firebaseio.com/licenses/$key.json"
            val body = """{"deviceId":"${deviceId()}","activatedAt":${System.currentTimeMillis()}}"""
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "PATCH"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.write(body.toByteArray())
            connection.responseCode
        } catch (_: Exception) {}
    }

    private fun refreshFirebase() {
        val p     = prefs() ?: return
        val saved = p.getString(K_KEY, null)
            ?: run { licenseState = LicenseState.None; return }

        FirebaseDatabase.getInstance()
            .getReference("licenses").child(saved)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) { clearLicense(); return@addOnSuccessListener }
                val d = snap.getValue(LicenseData::class.java) ?: return@addOnSuccessListener
                prefs()?.edit()?.putLong(K_LAST_CHECK, System.currentTimeMillis())?.apply()
                if (d.isRevoked) {
                    clearLicense()
                    licenseState = LicenseState.Revoked
                    return@addOnSuccessListener
                }
                saveCache(saved, d.expiresAt, false)
                licenseState = if (d.expiresAt == 0L || System.currentTimeMillis() < d.expiresAt)
                    LicenseState.Active(d.expiresAt, saved) else LicenseState.None
                currentKey = saved
            }
            .addOnFailureListener { }
    }

    private fun loadCache(): Boolean {
        val p   = prefs() ?: run { licenseState = LicenseState.None; return false }
        val key = p.getString(K_KEY, null)?.takeIf { it.isNotBlank() }
            ?: run { licenseState = LicenseState.None; return false }
        val rev = p.getBoolean(K_REVOKED, false)
        val exp = p.getLong(K_EXPIRES, 0L)
        currentKey   = key
        licenseState = when {
            rev                                           -> LicenseState.Revoked
            exp == 0L || System.currentTimeMillis() < exp -> LicenseState.Active(exp, key)
            else                                          -> LicenseState.None
        }
        return licenseState is LicenseState.Active
    }

    private fun saveCache(key: String, expiresAt: Long, revoked: Boolean) =
        prefs()?.edit()
            ?.putString(K_KEY, key)
            ?.putLong(K_EXPIRES, expiresAt)
            ?.putBoolean(K_REVOKED, revoked)
            ?.apply()

    fun clearLicense() {
        prefs()?.edit()
            ?.remove(K_KEY)
            ?.putLong(K_EXPIRES, 0L)
            ?.putBoolean(K_REVOKED, false)
            ?.apply()
        licenseState = LicenseState.None
        currentKey   = ""
    }

    fun formatExpiry(ts: Long): String =
        if (ts <= 0L) "Бессрочно"
        else java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(ts))

    private fun prefs() = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun todayStr() =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
}








@Composable
fun AiLimitDialog(
    theme: AppTheme,
    onDismiss: () -> Unit,
    onBonusGranted: () -> Unit
) {
    val context  = LocalContext.current
    val activity = context as? Activity
    var loading  by remember { mutableStateOf(false) }
    var adFailed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier  = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape     = RoundedCornerShape(20.dp),
            colors    = CardDefaults.cardColors(
                containerColor = ThemeManager.parseColor(theme.surfaceColor)
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("✍️", fontSize = 32.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Лимит запросов на сегодня исчерпан",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = ThemeManager.parseColor(theme.textPrimaryColor),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Бесплатно доступно ${LicenseManager.AI_FREE_DAILY} запросов в сутки. " +
                            "Посмотрите рекламу чтобы получить ещё +${LicenseManager.AI_AD_BONUS}.",
                    fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp,
                    color = ThemeManager.parseColor(theme.textSecondaryColor)
                )

                if (adFailed) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Реклама не загрузилась, но запросы добавлены.",
                        fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(20.dp))

                if (activity != null) {
                    Button(
                        onClick = {
                            if (loading) return@Button
                            loading = true
                            Ads.showRewardedAd(activity) { success ->
                                loading = false
                                LicenseManager.applyAiAdBonus()
                                if (!success) {
                                    adFailed = true
                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(2000)
                                        onBonusGranted()
                                    }
                                } else {
                                    onBonusGranted()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape    = RoundedCornerShape(theme.borderRadius.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = ThemeManager.parseColor(theme.accentColor)
                        ),
                        enabled  = !loading
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color    = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (loading) "Загрузка..."
                            else "Смотреть рекламу (+${LicenseManager.AI_AD_BONUS} запросов)",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/FPToolsBot"))
                            )
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Купить PRO - безлимит навсегда",
                        color = ThemeManager.parseColor(theme.accentColor),
                        fontSize = 13.sp
                    )
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Закрыть",
                        color    = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}








@Composable
fun PremiumInterceptor(navController: NavController, theme: AppTheme) {
    var blockedFeature by remember { mutableStateOf<PremiumFeature?>(null) }
    var pendingRoute   by remember { mutableStateOf<String?>(null) }

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val route = destination.route ?: return@OnDestinationChangedListener
            val entry = PREMIUM_ROUTES.entries.firstOrNull { (key, _) ->
                route == key || route.startsWith("$key/") || route.startsWith("$key?")
            } ?: return@OnDestinationChangedListener

            if (LicenseManager.hasAccess(entry.value)) return@OnDestinationChangedListener

            navController.popBackStack()
            blockedFeature = entry.value
            pendingRoute   = route.substringBefore("/").substringBefore("?")
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    blockedFeature?.let { feature ->
        PremiumDialog(
            feature    = feature,
            theme      = theme,
            onDismiss  = { blockedFeature = null; pendingRoute = null },
            onUnlocked = {
                blockedFeature = null
                pendingRoute?.let { navController.navigate(it) }
                pendingRoute = null
            }
        )
    }
}








@Composable
fun PremiumDialog(
    feature: PremiumFeature,
    theme: AppTheme,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val context  = LocalContext.current
    val activity = context as? Activity

    var loadingAd  by remember { mutableStateOf(false) }
    var adFailed   by remember { mutableStateOf(false) }
    var activating by remember { mutableStateOf(false) }
    var keyInput   by remember { mutableStateOf("") }
    var keyError   by remember { mutableStateOf<String?>(null) }
    var showKey    by remember { mutableStateOf(false) }
    var keySuccess by remember { mutableStateOf(false) }

    val adHours = LicenseManager.adUnlockHoursLeft(feature)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier  = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape     = RoundedCornerShape(20.dp),
            colors    = CardDefaults.cardColors(
                containerColor = ThemeManager.parseColor(theme.surfaceColor)
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                
                Text(feature.emoji, fontSize = 30.sp)
                Spacer(Modifier.height(10.dp))

                
                Text(
                    feature.displayName,
                    fontSize    = 17.sp,
                    fontWeight  = FontWeight.SemiBold,
                    color       = ThemeManager.parseColor(theme.textPrimaryColor),
                    textAlign   = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))

                
                Text(
                    feature.description,
                    fontSize    = 13.sp,
                    lineHeight  = 18.sp,
                    textAlign   = TextAlign.Center,
                    color       = ThemeManager.parseColor(theme.textSecondaryColor)
                )

                
                if (adHours > 0) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF00C853).copy(alpha = 0.1f)
                    ) {
                        Text(
                            "Открыто рекламой ещё на $adHours ч",
                            modifier  = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            fontSize  = 12.sp,
                            color     = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                
                Spacer(Modifier.height(12.dp))
                Text(
                    "Если функция включена, она продолжит работать в фоне после 48 часов. " +
                            "Для изменения настроек потребуется снова открыть доступ.",
                    fontSize   = 11.sp,
                    lineHeight = 16.sp,
                    color      = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.65f),
                    textAlign  = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(
                    color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.08f)
                )
                Spacer(Modifier.height(16.dp))

                
                if (activity != null) {
                    Button(
                        onClick = {
                            if (loadingAd) return@Button
                            loadingAd = true; adFailed = false
                            Ads.showRewardedAd(activity) { success ->
                                loadingAd = false
                                LicenseManager.applyAdUnlock(feature)
                                if (!success) {
                                    adFailed = true
                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(2500)
                                        onUnlocked()
                                    }
                                } else {
                                    onUnlocked()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(theme.borderRadius.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = ThemeManager.parseColor(theme.accentColor)
                        ),
                        enabled  = !loadingAd
                    ) {
                        if (loadingAd) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                color       = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Загрузка...")
                        } else {
                            Text("Открыть на 48 часов (реклама)", fontWeight = FontWeight.Medium)
                        }
                    }

                    if (adFailed) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Реклама не загрузилась, доступ открыт.",
                            fontSize  = 11.sp,
                            color     = ThemeManager.parseColor(theme.textSecondaryColor),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/FPToolsBot"))
                            )
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = RoundedCornerShape(theme.borderRadius.dp),
                    border   = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.6f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ThemeManager.parseColor(theme.accentColor)
                    )
                ) {
                    Text("Купить PRO навсегда", fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(6.dp))

                
                AnimatedVisibility(visible = !showKey) {
                    TextButton(
                        onClick  = { showKey = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Ввести ключ",
                            color    = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }

                AnimatedVisibility(visible = showKey, enter = expandVertically() + fadeIn()) {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value         = keyInput,
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
                                unfocusedBorderColor = ThemeManager.parseColor(theme.textSecondaryColor)
                                    .copy(alpha = 0.3f),
                                focusedTextColor     = ThemeManager.parseColor(theme.textPrimaryColor),
                                unfocusedTextColor   = ThemeManager.parseColor(theme.textPrimaryColor),
                                cursorColor          = ThemeManager.parseColor(theme.accentColor),
                            ),
                            shape = RoundedCornerShape(theme.borderRadius.dp),
                            trailingIcon = {
                                if (keySuccess) Icon(
                                    imageVector        = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint               = Color(0xFF4CAF50)
                                )
                            },
                            supportingText = {
                                keyError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (keyInput.length < 8) { keyError = "Введите корректный ключ"; return@Button }
                                activating = true; keyError = null
                                LicenseManager.activateKey(keyInput) { res ->
                                    activating = false
                                    res.fold(
                                        onSuccess = {
                                            keySuccess = true
                                            CoroutineScope(Dispatchers.Main).launch {
                                                delay(600)
                                                onUnlocked()
                                            }
                                        },
                                        onFailure = { e -> keyError = e.message }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            enabled  = !activating && keyInput.isNotEmpty(),
                            shape    = RoundedCornerShape(theme.borderRadius.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = ThemeManager.parseColor(theme.accentColor)
                            )
                        ) {
                            if (activating) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(16.dp),
                                    color       = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("Активировать", fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Закрыть",
                        color    = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}




//




//




@Composable
fun PremiumSwitch(
    feature: PremiumFeature,
    checked: Boolean,
    theme: AppTheme,
    onCheckedChange: (Boolean) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val hasAccess  = LicenseManager.hasAccess(feature)

    Box {
        Switch(
            checked         = checked,
            onCheckedChange = {
                if (hasAccess) onCheckedChange(it) else showDialog = true
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor   = ThemeManager.parseColor(theme.accentColor),
                checkedTrackColor   = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.4f),
                uncheckedThumbColor = if (!hasAccess) Color.Gray.copy(alpha = 0.5f) else Color.White,
                uncheckedTrackColor = Color.Gray.copy(alpha = 0.2f)
            )
        )
        if (!hasAccess) {
            Icon(
                imageVector        = Icons.Default.Lock,
                contentDescription = null,
                tint               = ThemeManager.parseColor(theme.accentColor),
                modifier           = Modifier.size(10.dp).align(Alignment.TopEnd)
            )
        }
    }

    if (showDialog) {
        PremiumDialog(
            feature    = feature,
            theme      = theme,
            onDismiss  = { showDialog = false },
            onUnlocked = { showDialog = false }
        )
    }
}








@Composable
fun ProStatusCard(theme: AppTheme) {
    val context = LocalContext.current
    val isPro   = LicenseManager.isProActive()
    val state   = LicenseManager.licenseState

    var showKey    by remember { mutableStateOf(false) }
    var keyInput   by remember { mutableStateOf("") }
    var activating by remember { mutableStateOf(false) }
    var keyError   by remember { mutableStateOf<String?>(null) }
    var keySuccess by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(theme.borderRadius.dp),
        colors   = CardDefaults.cardColors(Color.Transparent),
        border   = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isPro)
                Color(0xFF4CAF50).copy(alpha = 0.35f)
            else
                ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isPro) "💎" else "🔒", fontSize = 24.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            if (isPro) "PRO активен" else "Бесплатный план",
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp,
                            color      = ThemeManager.parseColor(theme.textPrimaryColor)
                        )
                        if (isPro && state is LicenseState.Active) {
                            Text(
                                "Действует до ${LicenseManager.formatExpiry(state.expiresAt)}",
                                fontSize = 11.sp,
                                color    = Color(0xFF81C784)
                            )
                        } else if (!isPro) {
                            Text(
                                "PRO фичи недоступны",
                                fontSize = 11.sp,
                                color    = ThemeManager.parseColor(theme.textSecondaryColor)
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isPro)
                        Color(0xFF4CAF50).copy(alpha = 0.12f)
                    else
                        Color.Red.copy(alpha = 0.1f)
                ) {
                    Text(
                        if (isPro) "ACTIVE" else "FREE",
                        modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color      = if (isPro) Color(0xFF4CAF50) else Color(0xFFEF5350)
                    )
                }
            }

            
            if (!isPro) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/FPToolsBot"))
                                )
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape    = RoundedCornerShape(theme.borderRadius.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = ThemeManager.parseColor(theme.accentColor)
                        )
                    ) { Text("Купить PRO", fontWeight = FontWeight.Medium, fontSize = 13.sp) }

                    OutlinedButton(
                        onClick  = { showKey = !showKey },
                        modifier = Modifier.height(42.dp),
                        shape    = RoundedCornerShape(theme.borderRadius.dp),
                        border   = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.5f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ThemeManager.parseColor(theme.accentColor)
                        )
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ключ", fontSize = 13.sp)
                    }
                }

                AnimatedVisibility(visible = showKey, enter = expandVertically() + fadeIn()) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value         = keyInput,
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
                                unfocusedBorderColor = ThemeManager.parseColor(theme.textSecondaryColor)
                                    .copy(alpha = 0.3f),
                                focusedTextColor     = ThemeManager.parseColor(theme.textPrimaryColor),
                                unfocusedTextColor   = ThemeManager.parseColor(theme.textPrimaryColor),
                                cursorColor          = ThemeManager.parseColor(theme.accentColor),
                            ),
                            shape = RoundedCornerShape(theme.borderRadius.dp),
                            trailingIcon = {
                                if (keySuccess) Icon(
                                    imageVector        = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint               = Color(0xFF4CAF50)
                                )
                            },
                            supportingText = {
                                keyError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (keyInput.length < 8) { keyError = "Введите корректный ключ"; return@Button }
                                activating = true; keyError = null
                                LicenseManager.activateKey(keyInput) { res ->
                                    activating = false
                                    res.fold(
                                        onSuccess = { keySuccess = true; showKey = false },
                                        onFailure = { e -> keyError = e.message }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            enabled  = !activating && keyInput.isNotEmpty(),
                            shape    = RoundedCornerShape(theme.borderRadius.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = ThemeManager.parseColor(theme.accentColor)
                            )
                        ) {
                            if (activating) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(16.dp),
                                    color       = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("Активировать", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            
            if (isPro) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "Ключ: ${LicenseManager.currentKey.take(12)}...",
                        fontSize   = 11.sp,
                        color      = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace
                    )
                    TextButton(
                        onClick        = { LicenseManager.clearLicense() },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Выйти", fontSize = 11.sp, color = Color.Red.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}








@Composable
fun LockBadge(feature: PremiumFeature, theme: AppTheme) {
    when {
        LicenseManager.isProActive() -> Unit

        LicenseManager.isAdUnlocked(feature) -> {
            val h = LicenseManager.adUnlockHoursLeft(feature)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF1565C0).copy(alpha = 0.2f)
            ) {
                Text(
                    text       = "${h}h",
                    modifier   = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    fontSize   = 10.sp,
                    color      = Color(0xFF42A5F5),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        else -> {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.15f)
            ) {
                Text(
                    text       = "PRO",
                    modifier   = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    fontSize   = 10.sp,
                    color      = ThemeManager.parseColor(theme.accentColor),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}