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

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.gson.Gson
import java.util.concurrent.TimeUnit
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import org.jsoup.Jsoup
import retrofit2.Response
import okhttp3.ResponseBody
import java.io.File

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.annotations.SerializedName
import coil.compose.AsyncImage
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.apply
import kotlin.compareTo

const val APP_VERSION = "1.2.5"

data class AutoRefundSettings(
    val enabled: Boolean = false,
    val maxPrice: Double = 10.0,
    val triggerStars: List<Int> = listOf(1, 2, 3),
    val sendMessage: Boolean = true,
    val messageText: String = "Мне жаль, что у Вас возникли проблемы. Обращайтесь ещё 🤝",
    val imageUri: String? = null,
    val imageFirst: Boolean = true
)

data class OrderConfirmSettings(
    val enabled: Boolean = false,
    val text: String = "Спасибо за заказ, \$username! 🤝\nБуду очень благодарен, если оставите отзыв. Это очень поможет мне! 😊",
    val imageUri: String? = null,
    val imageFirst: Boolean = true
)


data class ReadMarkSettings(
    val markAfterAutoReply: Boolean = true,
    val markAfterGreeting: Boolean = true,
    val markAfterBusyReply: Boolean = false,
    val markAfterReviewReply: Boolean = false,
    val markAfterOrderConfirm: Boolean = false,
    val markAfterAutoRefund: Boolean = false,
    val markAfterOrderReminder: Boolean = false,
    val markAfterBonusMessage: Boolean = false,
    val markAfterManualReply: Boolean = true,
    val neverMarkSystemEvents: Boolean = true
)


data class AiVarPermissions(
    
    val allowChatHistory: Boolean = true,
    val allowUsername: Boolean = true,
    val allowChatId: Boolean = false,
    val allowOrderId: Boolean = false,
    val allowLotName: Boolean = false,
    val allowMessageText: Boolean = true,
    
    val allowReviewText: Boolean = true,   
    val allowReviewStars: Boolean = true   
)

data class ChatItem(val id: String, val username: String, val lastMessage: String, val isUnread: Boolean, val avatarUrl: String, val date: String, val activeLot: String? = null)

data class MessageItem(
    val id: String,
    val author: String,
    val text: String,
    val isMe: Boolean,
    val time: String,
    val imageUrl: String? = null,
    val badge: String? = null,
    
    
    val authorId: String? = null,
    val authorAvatarUrl: String? = null
)

data class AutoResponseCommand(
    val trigger: String,
    val response: String,
    val exactMatch: Boolean,
    val imageUri: String? = null,
    val imageFirst: Boolean = true,
    
    val caseSensitive: Boolean = false,
    
    
    val callMode: Boolean = false,
    
    
    @SerializedName("callAutoReplyText") val callAutoReplyTextRaw: String? = null,
    
    
    @SerializedName("callStyle") val callStyle: String = "notification"
) {
    val callAutoReplyTextOrDefault: String
        get() = callAutoReplyTextRaw?.takeIf { it.isNotBlank() }
            ?: "Зову хозяина, он ответит в течение часа."
}

data class MessageTemplate(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val text: String,
    val imageUri: String? = null,
    val imageFirst: Boolean = true
)

data class TemplateSettings(
    val sendImmediately: Boolean = false
)

data class ReviewReplySettings(
    val enabled: Boolean = false,
    val useAi: Boolean = false,
    val aiLength: Int = 5,
    val aiFallbackText: String = "Спасибо за отзыв! 🤝",
    val manualTemplates: Map<Int, String> = (1..5).associateWith { "Спасибо за отзыв!" },
    val disabledStars: List<Int> = emptyList(),

    val aiIncludeLotName: Boolean = true,
    val aiIncludeDate: Boolean = false,
    val aiWritingStyle: String = "",
    val aiCustomInstruction: String = ""
)

data class GreetingSettings(
    val enabled: Boolean,
    val text: String,
    val cooldownHours: Int,
    val ignoreSystemMessages: Boolean,
    val imageUri: String? = null,
    val imageFirst: Boolean = true
)

data class UpdateInfo(val hasUpdate: Boolean, val newVersion: String, val htmlUrl: String)

data class OrderDetails(
    val id: String,
    val status: String,
    val gameTitle: String,
    val shortDesc: String,
    val price: String,
    val buyerName: String,
    val buyerAvatar: String,
    val canRefund: Boolean,
    val canConfirm: Boolean,
    val hasReview: Boolean,
    val reviewRating: Int,
    val reviewText: String,
    val sellerReply: String,
    val params: Map<String, String>,
    val hasAutoDelivery: Boolean = false,
    val lotId: String? = null,
    val isBuyer: Boolean = false,
    val buyerId: String = ""
)

data class ChatInfo(
    val lookingAtLink: String?,
    val lookingAtName: String?,
    val registrationDate: String?,
    val language: String?,
    val avatarUrl: String? = null,
    val userStatus: String? = null
)

data class BanInfo(val reason: String)

object LogManager {
    private val _logs = MutableStateFlow<List<Pair<String, Boolean>>>(listOf(Pair("Система готова. v$APP_VERSION", false)))
    val logs = _logs.asStateFlow()

    var debugEnabled: Boolean = true

    fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMsg = "[$time] $msg"
        val currentList = _logs.value.toMutableList()

        if (currentList.size > 5000) currentList.removeAt(currentList.lastIndex)
        currentList.add(0, Pair(logMsg, false))
        _logs.value = currentList
        Log.d("FPC_LOG", msg)
    }

    fun addLogDebug(msg: String) {
        if (!debugEnabled) return
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMsg = "[$time] [DEBUG] $msg"
        val currentList = _logs.value.toMutableList()

        if (currentList.size > 5000) currentList.removeAt(currentList.lastIndex)
        currentList.add(0, Pair(logMsg, true))
        _logs.value = currentList
        Log.d("FPC_DBG", msg)
    }

    fun maskSensitive(value: String): String {
        if (value.length <= 5) return value
        return value.take(4) + "..." + value.last()
    }

    fun saveLogsToFile(context: Context): String {
        val fileName = "funpay_logs_${System.currentTimeMillis()}.txt"
        val content = _logs.value
            .filter { !it.second || debugEnabled }
            .map { it.first }
            .reversed()
            .joinToString("\n\n")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { stream -> stream.write(content.toByteArray()) }
                    "Файл в Загрузках: $fileName"
                } ?: "Ошибка создания"
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                file.writeText(content)
                "Файл: ${file.absolutePath}"
            }
        } catch (e: Exception) { "Ошибка: ${e.message}" }
    }
}

class FunPayRepository(private val context: Context) {

    val prefs = context.getSharedPreferences("funpay_prefs", Context.MODE_PRIVATE)
    val banInfo = MutableStateFlow<BanInfo?>(null)
    private val extraCookies = mutableMapOf<String, String>()
    private val recentCommandKeys = mutableMapOf<String, Long>()
    private var lastCommandKeyCleanup = 0L
    private val gson = Gson()
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
    private var phpsessid: String = ""
    private val nodeToGameMap = mutableMapOf<Int, Int>()
    private var lastCsrfFetchTime = 0L
    private var cachedCsrf: Pair<String, String>? = null
    private val CSRF_CACHE_DURATION = 30_000L
    internal val knownUnreadChats = mutableSetOf<String>()

    companion object {
        val lastOutgoingMessages = mutableMapOf<String, String>()

        private const val PREFS_NAME = "funpay_prefs"
        private const val KEY_ACCOUNTS_DATA = "accounts_data"
        private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"

        
        
        
        
        private const val KEY_LAST_OUTGOING = "last_outgoing_messages_v1"
        private val outgoingGson = Gson()

        fun loadOutgoingFromPrefs(context: Context) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val json = prefs.getString(KEY_LAST_OUTGOING, null) ?: return
                val type = object : TypeToken<Map<String, String>>() {}.type
                val loaded: Map<String, String>? = outgoingGson.fromJson(json, type)
                if (!loaded.isNullOrEmpty()) {
                    synchronized(lastOutgoingMessages) {
                        loaded.forEach { (k, v) -> lastOutgoingMessages.putIfAbsent(k, v) }
                    }
                }
            } catch (_: Exception) {}
        }

        fun persistOutgoing(context: Context) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val snapshot: Map<String, String> = synchronized(lastOutgoingMessages) {
                    HashMap(lastOutgoingMessages)
                }
                prefs.edit().putString(KEY_LAST_OUTGOING, outgoingGson.toJson(snapshot)).apply()
            } catch (_: Exception) {}
        }
    }

    val rawChatResponses = mutableMapOf<String, String>()

    val repoClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    init {

        val savedSess = prefs.getString("phpsessid", "") ?: ""

        if (savedSess.isNotEmpty()) {
            phpsessid = savedSess
        }

        val activeAcc = getActiveAccount()
        if (activeAcc != null && activeAcc.phpSessionId.isNotEmpty()) {
            if (phpsessid.isEmpty()) phpsessid = activeAcc.phpSessionId
        }

        
        
        
        loadOutgoingFromPrefs(context)
    }

    private data class ProcessedEvent(
        val chatId: String,
        val orderId: String,
        val eventType: String,
        val timestamp: Long
    )



    private val processedEventsMemory = mutableMapOf<String, MutableSet<String>>()

    private fun wasEventProcessed(chatId: String, eventId: String, eventType: String): Boolean {
        val key = "$chatId:$eventId:$eventType"


        if (processedEventsMemory.getOrPut(chatId) { mutableSetOf() }.contains(key)) {
            return true
        }


        val cacheKey = "processed_event_$key"
        val timestamp = prefs.getLong(cacheKey, 0L)

        if (timestamp > 0) {

            processedEventsMemory.getOrPut(chatId) { mutableSetOf() }.add(key)
            return true
        }

        return false
    }

    private fun markEventAsProcessed(chatId: String, eventId: String, eventType: String) {
        val key = "$chatId:$eventId:$eventType"


        processedEventsMemory.getOrPut(chatId) { mutableSetOf() }.add(key)


        val cacheKey = "processed_event_$key"
        prefs.edit().putLong(cacheKey, System.currentTimeMillis()).apply()
    }

    fun cleanupOldProcessedEvents() {
        val currentTime = System.currentTimeMillis()
        val maxAge = 7 * 24 * 60 * 60 * 1000L

        val allPrefs = prefs.all
        val keysToRemove = mutableListOf<String>()

        for ((key, value) in allPrefs) {
            if (key.startsWith("processed_event_") && value is Long) {
                if (currentTime - value > maxAge) {
                    keysToRemove.add(key)
                }
            }
        }

        if (keysToRemove.isNotEmpty()) {
            val editor = prefs.edit()
            keysToRemove.forEach { editor.remove(it) }
            editor.apply()
            LogManager.addLogDebug("🧹 Очищено ${keysToRemove.size} старых событий")
        }
    }




    fun getAccountsData(): AccountsData {

        val json = prefs.getString(KEY_ACCOUNTS_DATA, null)
        return if (json != null) {
            try {
                gson.fromJson(json, AccountsData::class.java)
            } catch (e: Exception) {
                AccountsData()
            }
        } else {

            val oldKey = prefs.getString("golden_key", null)
            if (oldKey != null && oldKey.isNotEmpty()) {
                val account = Account(
                    goldenKey = oldKey,
                    isActive = true
                )
                AccountsData(
                    accounts = listOf(account),
                    activeAccountId = account.id
                )
            } else {
                AccountsData()
            }
        }
    }

    fun saveAccountsData(accountsData: AccountsData) {

        val json = gson.toJson(accountsData)
        prefs.edit().putString(KEY_ACCOUNTS_DATA, json).apply()
    }

    fun getAllAccounts(): List<Account> {
        return getAccountsData().accounts
    }

    suspend fun updateGoldenKey(newKey: String) {
        val activeAccount = getActiveAccount()
        if (activeAccount != null) {
            val updatedAccount = activeAccount.copy(goldenKey = newKey)
            val allAccounts = getAllAccounts().toMutableList()
            val index = allAccounts.indexOfFirst { it.id == activeAccount.id }
            if (index != -1) {
                allAccounts[index] = updatedAccount
                saveAccounts(allAccounts)

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, updatedAccount.id).apply()
            }
        }
    }

    private fun saveAccounts(accounts: List<Account>) {
        val currentActiveId = getAccountsData().activeAccountId
        val accountsData = AccountsData(
            accounts = accounts,
            activeAccountId = currentActiveId
        )
        saveAccountsData(accountsData)
    }

    fun clearAllData() {
        banInfo.value = null
        phpsessid = ""
        cachedCsrf = null
        lastCsrfFetchTime = 0L
        nodeToGameMap.clear()
        FunPayRepository.lastOutgoingMessages.clear()

        processedEventsMemory.clear()


        val prefsToClear = listOf(
            "fp_prefs",
            "funpay_prefs",
            "theme_prefs",
            "accounts_data",
            "settings_prefs",
            "auto_commands",
            "message_templates",
            "greeting_settings",
            "review_reply_settings_v3",
            "auto_refund_settings",
            "order_confirm_settings",
            "template_settings"
        )

        prefsToClear.forEach { name ->
            try {
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            context.cacheDir.deleteRecursively()
            context.filesDir.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        LogManager.addLog("🧹 Все данные приложения полностью очищены")
    }
    fun getActiveAccount(): Account? {
        val data = getAccountsData()
        return data.accounts.find { it.id == data.activeAccountId }
    }

    fun setActiveAccount(accountId: String) {
        banInfo.value = null
        val data = getAccountsData()
        val updatedAccounts = data.accounts.map { account ->
            account.copy(isActive = account.id == accountId)
        }
        saveAccountsData(
            data.copy(
                accounts = updatedAccounts,
                activeAccountId = accountId
            )
        )
        cachedCsrf = null
        lastCsrfFetchTime = 0L
        phpsessid = getActiveAccount()?.phpSessionId ?: ""
    }

    fun addAccountFromWebLogin(goldenKey: String, phpSessionId: String) {
        val data = getAccountsData()


        val existingAccount = data.accounts.find { it.goldenKey == goldenKey }
        if (existingAccount != null) {

            setActiveAccount(existingAccount.id)
            return
        }


        val newAccount = Account(
            goldenKey = goldenKey,
            phpSessionId = phpSessionId,
            isActive = data.accounts.isEmpty()
        )

        val updatedAccounts = data.accounts.map { it.copy(isActive = false) } + newAccount

        saveAccountsData(
            AccountsData(
                accounts = updatedAccounts,
                activeAccountId = if (data.accounts.isEmpty()) newAccount.id else data.activeAccountId
            )
        )


        kotlinx.coroutines.GlobalScope.launch {
            try {
                loadAccountProfile(newAccount.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun loadAccountProfile(accountId: String) {
        val data = getAccountsData()
        val account = data.accounts.find { it.id == accountId } ?: return

        try {
            val cookie = "golden_key=${account.goldenKey}; PHPSESSID=${account.phpSessionId}"


            val mainResponse = RetrofitInstance.api.getMainPage(cookie, userAgent)
            val html = mainResponse.body()?.string() ?: return

            val doc = Jsoup.parse(html)
            val appDataStr = doc.select("body").attr("data-app-data")

            var userId = account.userId


            if (appDataStr.isNotEmpty()) {
                try {
                    val json = JSONObject(appDataStr)
                    userId = json.optString("userId")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }


            if (userId.isEmpty() || userId == "0") {
                val userIdMatch = Regex("data-href=\"https://funpay\\.com/users/(\\d+)/\"").find(html)
                userId = userIdMatch?.groupValues?.get(1) ?: ""
            }

            if (userId.isEmpty()) return


            val profileResponse = RetrofitInstance.api.getUserProfile(userId, cookie, userAgent)
            val profileHtml = profileResponse.body()?.string() ?: ""
            val profileDoc = Jsoup.parse(profileHtml)


            val username = profileDoc.select(".user-link-dropdown .user-link-name").first()?.text()?.trim()
                ?: profileDoc.select("div.media-user-name").first()?.text()
                    ?.replace("Online", "", ignoreCase = true)
                    ?.replace("Онлайн", "", ignoreCase = true)
                    ?.trim()
                ?: "Unknown"


            var avatarUrl = "https://funpay.com/img/layout/avatar.png"
            val avatarStyle = profileDoc.select(".avatar-photo, .profile-photo").attr("style")

            if (avatarStyle.contains("url(")) {
                avatarUrl = avatarStyle
                    .substringAfter("url(")
                    .substringBefore(")")
                    .replace("\"", "")
                    .replace("'", "")

                if (avatarUrl.startsWith("/")) {
                    avatarUrl = "https://funpay.com$avatarUrl"
                }
            }


            val updatedAccounts = data.accounts.map {
                if (it.id == accountId) {
                    it.copy(
                        username = username,
                        userId = userId,
                        avatarUrl = avatarUrl
                    )
                } else {
                    it
                }
            }
            saveAccountsData(data.copy(accounts = updatedAccounts))

        } catch (e: Exception) {
            e.printStackTrace()
            LogManager.addLog("❌ Ошибка загрузки профиля: ${e.message}")
        }
    }

    fun deleteAccount(accountId: String) {
        val data = getAccountsData()
        val updatedAccounts = data.accounts.filter { it.id != accountId }


        val newActiveId = if (data.activeAccountId == accountId) {
            updatedAccounts.firstOrNull()?.id
        } else {
            data.activeAccountId
        }

        saveAccountsData(
            AccountsData(
                accounts = updatedAccounts,
                activeAccountId = newActiveId
            )
        )
    }

    suspend fun checkForUpdates(): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.github.com/repos/XaviersDev/FunPay-Tools-Android/releases/latest")
                    .header("User-Agent", "FunPayToolsApp")
                    .build()

                val response = client.newCall(request).execute()
                val jsonStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(jsonStr)


                if (!json.has("tag_name")) return@withContext null

                val tagName = json.getString("tag_name").trim().lowercase().removePrefix("v")
                val htmlUrl = json.optString("html_url", "https://github.com/XaviersDev/FunPay-Tools-Android/releases")

                val isNewer = isNewerVersion(remote = tagName, current = APP_VERSION)
                UpdateInfo(isNewer, tagName, htmlUrl)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        return try {
            val r = remote.split(".").map { it.trim().toInt() }
            val c = current.split(".").map { it.trim().toInt() }
            val maxLen = maxOf(r.size, c.size)
            for (i in 0 until maxLen) {
                val rv = r.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (rv > cv) return true
                if (rv < cv) return false
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    fun saveGoldenKey(key: String) {
        if (key.isNotEmpty()) {
            addAccountFromWebLogin(key, "")
        }
    }
    fun getGoldenKey(): String? {
        return getActiveAccount()?.goldenKey
    }
    fun hasAuth(): Boolean {
        return getActiveAccount() != null
    }


    fun getPhpSessionId(): String {
        return getActiveAccount()?.phpSessionId ?: ""
    }


    fun getDelayMultiplier(): Float {
        val accountsCount = getAllAccounts().size
        return if (accountsCount > 1) 2.0f else 1.0f
    }

    fun setSetting(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun getSetting(key: String): Boolean = prefs.getBoolean(key, false)

    
    fun getReadMarkSettings(): ReadMarkSettings {
        val json = prefs.getString("read_mark_settings", null) ?: return ReadMarkSettings()
        return try { gson.fromJson(json, ReadMarkSettings::class.java) } catch (_: Exception) { ReadMarkSettings() }
    }
    fun saveReadMarkSettings(s: ReadMarkSettings) =
        prefs.edit().putString("read_mark_settings", gson.toJson(s)).apply()

    
    fun getAiVarPermissions(): AiVarPermissions {
        val json = prefs.getString("ai_var_permissions", null) ?: return AiVarPermissions()
        return try { gson.fromJson(json, AiVarPermissions::class.java) } catch (_: Exception) { AiVarPermissions() }
    }
    fun saveAiVarPermissions(s: AiVarPermissions) =
        prefs.edit().putString("ai_var_permissions", gson.toJson(s)).apply()

    
    suspend fun markChatAsRead(nodeId: String) {
        try {
            val (csrf, _) = getCsrfAndId() ?: return
            val objects = "[{\"type\":\"chat_node\",\"id\":\"$nodeId\",\"tag\":\"00000000\"," +
                    "\"data\":{\"node\":\"$nodeId\",\"last_message\":-1,\"content\":\"\"}}]"
            RetrofitInstance.api.runnerGet(
                cookie = getCookieString(),
                userAgent = userAgent,
                objects = objects,
                request = "false",
                csrfToken = csrf
            )
            LogManager.addLogDebug("👁 Чат $nodeId → прочитан")
        } catch (e: Exception) {
            LogManager.addLogDebug("⚠️ markChatAsRead($nodeId): ${e.message}")
        }
    }

    
    suspend fun pingOnlineStatus() {
        try {
            val cookie = getCookieString()
            if (cookie.isBlank()) return
            val resp = RetrofitInstance.api.getMainPage(cookie, userAgent)
            updateSession(resp)
            LogManager.addLogDebug("🟢 Онлайн-пинг OK")
        } catch (e: Exception) {
            LogManager.addLogDebug("⚠️ Онлайн-пинг: ${e.message}")
        }
    }

    fun setRaiseInterval(minutes: Int) = prefs.edit().putInt("raise_interval", minutes).apply()
    fun getRaiseInterval(): Int = prefs.getInt("raise_interval", 15)

    fun saveCommands(commands: List<AutoResponseCommand>) = prefs.edit().putString("auto_commands", gson.toJson(commands)).apply()
    fun getCommands(): List<AutoResponseCommand> {
        val json = prefs.getString("auto_commands", "[]")
        return try {
            val raw: List<AutoResponseCommand>? = gson.fromJson(json, object : TypeToken<List<AutoResponseCommand>>() {}.type)
            raw ?: emptyList()
        } catch (e: Exception) {
            LogManager.addLogDebug("⚠️ getCommands parse error: ${e.message}")
            emptyList()
        }
    }

    fun saveMessageTemplates(templates: List<MessageTemplate>) = prefs.edit().putString("message_templates", gson.toJson(templates)).apply()
    fun getMessageTemplates(): List<MessageTemplate> {
        val json = prefs.getString("message_templates", "[]")
        return try {
            gson.fromJson(json, object : TypeToken<List<MessageTemplate>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTemplateSettings(settings: TemplateSettings) = prefs.edit().putString("template_settings", gson.toJson(settings)).apply()
    fun getTemplateSettings(): TemplateSettings {
        val json = prefs.getString("template_settings", null)
        return if (json != null) {
            try {
                gson.fromJson(json, TemplateSettings::class.java)
            } catch (e: Exception) {
                TemplateSettings()
            }
        } else {
            TemplateSettings()
        }
    }

    fun saveReviewReplySettings(settings: ReviewReplySettings) = prefs.edit().putString("review_reply_settings_v3", gson.toJson(settings)).apply()

    fun getReviewReplySettings(): ReviewReplySettings {
        val json = prefs.getString("review_reply_settings_v3", null) ?: return ReviewReplySettings(false)
        return gson.fromJson(json, ReviewReplySettings::class.java)
    }

    fun saveGreetingSettings(settings: GreetingSettings) =
        prefs.edit().putString("greeting_settings", gson.toJson(settings)).apply()

    fun getGreetingSettings(): GreetingSettings {
        val json = prefs.getString("greeting_settings", null)
        return if (json != null) {
            gson.fromJson(json, GreetingSettings::class.java)
        } else {
            GreetingSettings(false, "Привет, \$username! Я скоро отвечу.", 48, true)
        }
    }

    private fun getGreetedCacheKey(): String {
        val accountId = getActiveAccount()?.id ?: "default"
        return "greeted_cache_$accountId"
    }

    private fun getGreetedCache(): MutableMap<String, Long> {
        return try {
            val json = prefs.getString(getGreetedCacheKey(), "{}") ?: "{}"
            val type = object : TypeToken<MutableMap<String, Long>>() {}.type
            gson.fromJson<MutableMap<String, Long>>(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveGreetedCache(cache: Map<String, Long>) {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L
        val cleaned = cache.filter { it.value > cutoff }
        prefs.edit().putString(getGreetedCacheKey(), gson.toJson(cleaned)).apply()
    }

    fun markChatGreeted(chatId: String) {
        val cache = getGreetedCache()
        cache[chatId] = System.currentTimeMillis()
        saveGreetedCache(cache)
    }



    fun saveAutoRefundSettings(settings: AutoRefundSettings) =
        prefs.edit().putString("auto_refund_settings", gson.toJson(settings)).apply()

    fun getAutoRefundSettings(): AutoRefundSettings {
        val json = prefs.getString("auto_refund_settings", null)
        return if (json != null) {
            gson.fromJson(json, AutoRefundSettings::class.java)
        } else {
            AutoRefundSettings()
        }
    }

    suspend fun checkAutoRefund(chats: List<ChatItem>) {
        val settings = getAutoRefundSettings()
        if (!settings.enabled) return

        val reviewPhrases = listOf(
            "написал отзыв к заказу", "has given feedback to the order", "написав відгук до замовлення",
            "изменил отзыв к заказу", "has edited their feedback to the order", "змінив відгук до замовлення"
        )

        for (chat in chats) {
            val lastMsgLower = chat.lastMessage.lowercase()

            val lastSelf = FunPayRepository.lastOutgoingMessages[chat.id]
            if (lastSelf != null) {
                val cleanIncoming = chat.lastMessage.replace("...", "").trim().lowercase()
                val cleanSelf = lastSelf.trim().lowercase()

                if (cleanSelf.contains(cleanIncoming) || cleanIncoming.contains(cleanSelf)) {
                    continue
                }
            }

            if (reviewPhrases.any { lastMsgLower.contains(it) }) {
                val orderIdMatch = Regex("#([a-zA-Z0-9]+)").find(chat.lastMessage)
                val orderId = orderIdMatch?.groupValues?.get(1) ?: continue


                val messageHash = "${chat.lastMessage.hashCode()}"
                if (wasEventProcessed(chat.id, messageHash, "refund_check")) {
                    continue
                }

                val isEdited = lastMsgLower.contains("изменил") || lastMsgLower.contains("edited")
                if (isEdited) {
                    LogManager.addLogDebug("🔍 Обработка измененного отзыва для #$orderId")
                } else {
                    LogManager.addLogDebug("🔍 Обработка нового отзыва для #$orderId")
                }

                LogManager.addLog("💸 Проверка авто-возврата для заказа #$orderId...")

                val orderDetails = getOrderDetails(orderId)
                if (orderDetails == null) {
                    LogManager.addLog("❌ Не удалось получить детали заказа #$orderId")
                    markEventAsProcessed(chat.id, messageHash, "refund_check")
                    continue
                }

                val refundCacheKey = "${orderId}_${orderDetails.reviewRating}"

                if (wasEventProcessed(chat.id, refundCacheKey, "refund_done")) {
                    LogManager.addLogDebug("⏭️ Возврат для #$orderId (${orderDetails.reviewRating}*) уже выполнен")
                    markEventAsProcessed(chat.id, messageHash, "refund_check")
                    continue
                }

                if (!orderDetails.canRefund) {
                    LogManager.addLog("ℹ️ Возврат уже выполнен для #$orderId или недоступен")
                    markEventAsProcessed(chat.id, refundCacheKey, "refund_done")
                    markEventAsProcessed(chat.id, messageHash, "refund_check")
                    continue
                }

                val priceVal = orderDetails.price.replace(Regex("[^0-9,.]"), "").replace(",", ".").toDoubleOrNull() ?: 0.0

                if (priceVal <= settings.maxPrice &&
                    settings.triggerStars.contains(orderDetails.reviewRating)
                ) {
                    LogManager.addLog("✅ Условия выполнены (${orderDetails.reviewRating}*, цена $priceVal). Возврат...")

                    val success = refundOrder(orderId)
                    if (success) {
                        LogManager.addLog("💸 Средства успешно возвращены (#$orderId)")
                        markEventAsProcessed(chat.id, refundCacheKey, "refund_done")

                        if (settings.sendMessage) {
                            val refundMsg = FpPlaceholders.applyCombined(
                                settings.messageText,
                                chat,
                                FpPlaceholders.OrderCtx(orderId = orderId, buyerUsername = chat.username)
                            )
                            sendWithOptionalImage(chat.id, refundMsg, settings.imageUri, settings.imageFirst)
                            if (getReadMarkSettings().markAfterAutoRefund) markChatAsRead(chat.id)
                            
                            markChatGreeted(chat.id)
                        }
                    } else {
                        LogManager.addLog("❌ Ошибка при выполнении возврата")
                    }
                } else {
                    LogManager.addLog("ℹ️ Возврат не требуется (Цена: $priceVal, Оценка: ${orderDetails.reviewRating}*)")
                    markEventAsProcessed(chat.id, refundCacheKey, "refund_done")
                }


                markEventAsProcessed(chat.id, messageHash, "refund_check")

                kotlinx.coroutines.delay(1000)
            }
        }
    }

    suspend fun checkReviewReplies(chats: List<ChatItem>) {
        val settings = getReviewReplySettings()
        if (!settings.enabled) return

        val reviewPhrases = listOf(
            "написал отзыв к заказу", "has given feedback to the order", "написав відгук до замовлення",
            "изменил отзыв к заказу", "has edited their feedback to the order", "змінив відгук до замовлення"
        )

        for (chat in chats) {
            val lastMsgLower = chat.lastMessage.lowercase()

            val lastSelf = FunPayRepository.lastOutgoingMessages[chat.id]
            if (lastSelf != null) {
                val cleanIncoming = chat.lastMessage.replace("...", "").trim().lowercase()
                val cleanSelf = lastSelf.trim().lowercase()

                if (cleanSelf.contains(cleanIncoming) || cleanIncoming.contains(cleanSelf)) {
                    continue
                }
            }

            if (reviewPhrases.any { lastMsgLower.contains(it) }) {
                val orderIdMatch = Regex("#([a-zA-Z0-9]+)").find(chat.lastMessage)
                val orderId = orderIdMatch?.groupValues?.get(1) ?: continue

                val reviewEventKey = "${chat.lastMessage.hashCode()}"

                if (wasEventProcessed(chat.id, reviewEventKey, "review_reply")) {
                    continue
                }

                LogManager.addLog("⭐ Обнаружен новый отзыв к заказу #$orderId...")

                val response = RetrofitInstance.api.getOrder(orderId, getCookieString(), userAgent)
                updateSession(response)
                val html = readBodySilent(response)
                if (html.isEmpty()) {
                    LogManager.addLog("❌ Не удалось получить страницу заказа #$orderId")
                    markEventAsProcessed(chat.id, reviewEventKey, "review_reply")
                    continue
                }

                val doc = Jsoup.parse(html)

                val reviewAuthorId = doc.select(".review-item-row[data-row='review']").attr("data-author")
                val activeProfile = getActiveAccount()
                if (activeProfile != null && reviewAuthorId == activeProfile.userId) {
                    LogManager.addLogDebug("⏭️ Отзыв #$orderId — ты покупатель, пропускаем")
                    markEventAsProcessed(chat.id, reviewEventKey, "review_reply")
                    continue
                }

                val sellerReply = doc.select(".review-reply").text()

                val ratingElement = doc.select(".rating div").first()
                val stars = if (ratingElement != null) {
                    val ratingClass = ratingElement.className()
                    ratingClass.filter { it.isDigit() }.toIntOrNull() ?: 0
                } else {
                    0
                }

                if (stars == 0) {
                    LogManager.addLog("ℹ️ Отзыв без рейтинга, пропускаем")
                    markEventAsProcessed(chat.id, reviewEventKey, "review_reply")
                    continue
                }

                if (settings.disabledStars.contains(stars)) {
                    LogManager.addLog("🔇 Ответ на $stars* отключен в настройках")
                    markEventAsProcessed(chat.id, reviewEventKey, "review_reply")
                    continue
                }

                val reviewText = doc.select(".review-item-text").text()

                var lotName = "Товар"
                val paramItems = doc.select(".param-item")
                for (item in paramItems) {
                    val headerObj = item.select("h5")
                    if (headerObj.isEmpty()) continue

                    val headerText = headerObj.text()
                    val headerTextLower = headerText.lowercase()

                    if (headerTextLower.contains("краткое описание") || headerTextLower.contains("short description")) {
                        val fullText = item.text()
                        if (fullText.startsWith(headerText, ignoreCase = true)) {
                            val temp = fullText.substring(headerText.length).trim()
                            if (temp.isNotEmpty()) lotName = temp
                        } else {
                            val temp = fullText.replace(headerText, "", ignoreCase = true).trim()
                            if (temp.isNotEmpty()) lotName = temp
                        }
                        break
                    }
                }
                if (lotName == "Товар") {
                    val headerName = doc.select(".order-desc div").first()?.text()
                    if (!headerName.isNullOrEmpty()) lotName = headerName
                }

                if (lotName.startsWith(":") || lotName.startsWith("-")) {
                    lotName = lotName.substring(1).trim()
                }
                if (lotName.length > 100) {
                    lotName = lotName.take(100) + "..."
                }

                LogManager.addLog("⭐ Рейтинг: $stars*, используем ${if (settings.useAi) "AI" else "шаблон"}")

                val replyText = if (settings.useAi) {
                    val aiResponse = generateAiReviewReply(
                        lotName = lotName,
                        reviewText = reviewText,
                        stars = stars,
                        sentenceCount = settings.aiLength,
                        settings = settings
                    )

                    if (aiResponse != null) {
                        LogManager.addLog("🤖 AI ответ сгенерирован")
                        aiResponse
                    } else {
                        LogManager.addLog("⚠️ AI не сработал, используем fallback")
                        settings.aiFallbackText
                    }
                } else {
                    settings.manualTemplates[stars] ?: "Спасибо за отзыв!"
                }

                val todayDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                val finalReplyText = replyText
                    .replace("\$username", chat.username)
                    .replace("\$order_id", orderId)
                    .replace("\$chat_name", chat.username)
                    .replace("\$lot_name", lotName)
                    .replace("\$date", todayDate)

                val formattedReply = formatReviewReply(finalReplyText)

                val success = replyToReview(orderId, formattedReply, stars)
                if (success) {
                    LogManager.addLog("✅ Ответ на отзыв #$orderId отправлен")
                    markEventAsProcessed(chat.id, reviewEventKey, "review_reply")
                    if (getReadMarkSettings().markAfterReviewReply) markChatAsRead(chat.id)
                } else {
                    LogManager.addLog("❌ Не удалось отправить ответ на отзыв")
                }

                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun formatReviewReply(text: String): String {
        var result = text.take(1000)

        if (result.length > 999) {
            val lastPunct = maxOf(
                result.take(999).lastIndexOf('.'),
                result.take(999).lastIndexOf('!'),
                result.take(999).lastIndexOf('\n')
            )
            if (lastPunct > 0 && lastPunct < 999) {
                result = result.take(lastPunct) + "🦦"
            } else {
                result = result.take(999)
            }
        }

        val lines = result.split('\n')
        if (lines.size > 10) {
            result = lines.takeLast(10).joinToString("\n")
        }

        return result.trim()
    }

    private suspend fun generateAiReviewReply(lotName: String, reviewText: String, stars: Int, sentenceCount: Int, settings: ReviewReplySettings): String? {
        return try {
            withContext(Dispatchers.IO) {
                val systemPrompt = "You are a professional review response generator for FunPay marketplace sellers."

                val starEmoji = "⭐".repeat(stars)

                val lengthGuide = when (sentenceCount) {
                    1 -> "1 короткое предложение"
                    2 -> "2 коротких предложения"
                    in 3..4 -> "$sentenceCount предложения (средняя длина)"
                    5 -> "3-4 предложения (развёрнутый и дружелюбный ответ)"
                    in 6..7 -> "$sentenceCount предложений (подробный ответ с эмоциями)"
                    in 8..9 -> "$sentenceCount предложений (очень развёрнутый, эмоциональный ответ)"
                    else -> "$sentenceCount предложений (максимально подробный, креативный и живой ответ)"
                }

                val styleLine = if (settings.aiWritingStyle.isNotBlank())
                    "СТИЛЬ НАПИСАНИЯ: ${settings.aiWritingStyle.trim()}."
                else ""

                val lotNameLine = if (settings.aiIncludeLotName)
                    "2. ОБЯЗАТЕЛЬНО УПОМЯНИ товар \"$lotName\" или его суть в своём ответе."
                else
                    "2. НЕ упоминай конкретное название товара, пиши в общих словах."

                val dateLine = if (settings.aiIncludeDate) {
                    val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                    "   Можешь органично упомянуть дату покупки/отзыва: $today."
                } else ""

                val customLine = if (settings.aiCustomInstruction.isNotBlank())
                    "\n--- ДОПОЛНИТЕЛЬНЫЕ ИНСТРУКЦИИ ОТ ПРОДАВЦА ---\n${settings.aiCustomInstruction.trim()}"
                else ""

                val aiPerms = getAiVarPermissions()
                val reviewStarsLine = if (aiPerms.allowReviewStars) "с оценкой $stars из 5" else ""
                val reviewTextLine = if (aiPerms.allowReviewText && reviewText.isNotBlank()) "Текст отзыва: \"$reviewText\"" else ""

                val combinedUserPrompt = """
Ты — дружелюбный продавец на игровой бирже FunPay.
Покупатель оставил отзыв${if (reviewStarsLine.isNotEmpty()) " $reviewStarsLine" else ""} на товар "$lotName".
${if (reviewTextLine.isNotEmpty()) "$reviewTextLine\n" else ""}
                    ${if (styleLine.isNotBlank()) "\n$styleLine" else ""}
                    Твоя задача — написать тёплый, искренний ответ на этот отзыв.

                    --- ВАЖНЫЕ ПРАВИЛА ---
                    1. ДЛИНА: Твой ответ должен быть примерно $lengthGuide.
                    $lotNameLine${if (dateLine.isNotBlank()) "\n$dateLine" else ""}
                    3. ИСПОЛЬЗУЙ эмодзи уместно (😊, 👍, 🎉, ✨, 💜, 🔥) для живости.
                    4. ПОБЛАГОДАРИ покупателя за отзыв и/или покупку.
                    5. ПОДСТРАИВАЙСЯ под тон и настроение отзыва:
                    - 5 звёзд: радость, благодарность, можешь добавить что-то личное
                            - 4 звезды: благодарность, признание, что можно улучшить
                    - 3 звезды: благодарность, готовность исправить
                            - 1-2 звезды: извинения, готовность помочь
                            6. ИЗБЕГАЙ шаблонных фраз типа "Обращайтесь ещё" или "Буду рад помочь снова".
                    7. ПИШИ естественно, как живой человек, а не робот.
                    8. НЕ используй Markdown, жирный текст или курсив.
                    9. Твой ответ — это ТОЛЬКО готовый текст. Без кавычек, без заголовков, без пояснений.$customLine

                    ГОТОВЫЙ ТЕКСТ ОТВЕТА:
                    """.trimIndent()

                val messages = JSONArray()
                val sysMsg = JSONObject().put("role", "system").put("content", systemPrompt)
                val userMsg = JSONObject().put("role", "user").put("content", combinedUserPrompt)
                messages.put(sysMsg)
                messages.put(userMsg)

                val payload = JSONObject()
                payload.put("messages", messages)
                payload.put("modelName", "ChatGPT 4o")
                payload.put("currentPagePath", "/chatgpt-4o")

                val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())

                val response = RetrofitInstance.api.rewriteText(
                    auth = "Bearer fptoolsdim",
                    body = body
                )

                if (response.isSuccessful) {
                    val jsonStr = response.body()?.string()
                    if (jsonStr.isNullOrEmpty()) {
                        LogManager.addLog("❌ AI вернул пустой ответ")
                        return@withContext null
                    }
                    val json = JSONObject(jsonStr)
                    val reply = json.optString("response", "").trim().replace("\"", "")
                    if (reply.isEmpty()) {
                        LogManager.addLog("❌ AI не вернул поле 'response'")
                        return@withContext null
                    }
                    reply
                } else {
                    LogManager.addLog("❌ AI API ошибка: ${response.code()}")
                    null
                }
            }
        } catch (e: Exception) {
            LogManager.addLog("❌ AI ошибка: ${e.message}")
            null
        }
    }


    suspend fun replyToReview(orderId: String, text: String, stars: Int): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val appData = getCsrfAndId() ?: return@withContext false
                val (csrf, userId) = appData

                if (userId.isEmpty()) {
                    LogManager.addLog("❌ User ID не найден")
                    return@withContext false
                }

                val response = RetrofitInstance.api.replyToReview(
                    cookie = getCookieString(),
                    userAgent = userAgent,
                    csrfToken = csrf,
                    orderId = orderId,
                    text = text,
                    rating = stars,
                    authorId = userId
                )

                updateSession(response)

                if (response.isSuccessful) {
                    val body = readBodySilent(response)
                    val json = JSONObject(body)

                    if (json.has("content")) {
                        LogManager.addLog("✅ FunPay подтвердил отправку ответа")
                        true
                    } else {
                        LogManager.addLog("⚠️ Неожиданный ответ от FunPay")
                        false
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    LogManager.addLog("❌ Ошибка API (${response.code()}): $errorBody")
                    false
                }
            }
        } catch (e: Exception) {
            LogManager.addLog("❌ Исключение при отправке ответа: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun getCookieString(): String {
        val sb = StringBuilder()


        val gk = getGoldenKey() ?: ""
        sb.append("golden_key=$gk")


        if (phpsessid.isNotEmpty()) {
            sb.append("; PHPSESSID=$phpsessid")
        }


        synchronized(extraCookies) {
            for ((k, v) in extraCookies) {

                if (k != "golden_key" && k != "PHPSESSID") {
                    sb.append("; $k=$v")
                }
            }
        }

        return sb.toString()
    }

    private fun readBodySilent(response: Response<ResponseBody>): String {
        return response.body()?.string() ?: response.errorBody()?.string() ?: ""
    }

    private fun isCloudflare(html: String): Boolean = html.contains("Just a moment") || html.contains("cloudflare") || html.contains("challenge-platform")

    fun updateSession(response: Response<*>) {
        val headers = response.headers()
        val cookies = headers.values("Set-Cookie")

        for (cookie in cookies) {
            try {

                val parts = cookie.split(";")[0].split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()

                    if (key == "PHPSESSID") {
                        if (value != phpsessid) {
                            phpsessid = value
                            prefs.edit().putString("phpsessid", phpsessid).apply()
                            LogManager.addLogDebug("🍪 Сессия обновлена: $phpsessid")
                        }
                    } else if (key != "golden_key") {

                        extraCookies[key] = value
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateSession(response: okhttp3.Response) {
        val headers = response.headers
        val cookies = headers.values("Set-Cookie")

        for (cookie in cookies) {
            try {

                val parts = cookie.split(";")[0].split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()

                    if (key == "PHPSESSID") {
                        if (value != phpsessid) {
                            phpsessid = value
                            prefs.edit().putString("phpsessid", phpsessid).apply()
                            LogManager.addLogDebug("🍪 Сессия обновлена: $phpsessid")
                        }
                    } else if (key != "golden_key") {

                        extraCookies[key] = value
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun updateGameData() {
        try {
            val response = RetrofitInstance.api.getMainPage(getCookieString(), userAgent)
            updateSession(response)
            val html = readBodySilent(response)
            if (isCloudflare(html)) return
            val doc = Jsoup.parse(html)
            val gameItems = doc.select(".promo-game-item")
            for (item in gameItems) {
                val gameId = item.selectFirst(".game-title")?.attr("data-id")?.toIntOrNull() ?: continue
                val links = item.select("ul.list-inline li a")
                for (link in links) {
                    val href = link.attr("href")
                    if (href.contains("/lots/") || href.contains("/chips/")) {
                        href.split("/").filter { it.isNotEmpty() }.lastOrNull()?.toIntOrNull()?.let { nodeToGameMap[it] = gameId }
                    }
                }
            }
            LogManager.addLog("✅ База игр: ${nodeToGameMap.size} шт.")
        } catch (e: Exception) {
            LogManager.addLog("Ошибка синхронизации игр: ${e.message}")
        }
    }


    suspend fun getCsrfAndId(): Pair<String, String>? {
        val currentTime = System.currentTimeMillis()
        if (cachedCsrf != null && currentTime - lastCsrfFetchTime < CSRF_CACHE_DURATION) {
            return cachedCsrf
        }

        val key = getGoldenKey() ?: return null

        repeat(3) { attempt ->
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitInstance.api.getMainPage(getCookieString(), userAgent)
                }

                updateSession(response)
                val html = readBodySilent(response)

                
                if (html.contains("account-blocked-box")) {
                    val doc = Jsoup.parse(html)
                    val reason = doc.select(".account-blocked-box p").joinToString("\n\n") { it.text() }
                    banInfo.value = BanInfo(reason.ifBlank { "Аккаунт заблокирован администрацией FunPay." })
                    LogManager.addLog("⛔ Аккаунт заблокирован!")
                    return null
                } else {
                    banInfo.value = null
                }
                


                if (isCloudflare(html)) {
                    LogManager.addLogDebug("⛔ CF BLOCK (Auth) - попытка ${attempt + 1}")
                    if (attempt < 2) {
                        delay(2000L * (attempt + 1))
                        return@repeat
                    }
                    return null
                }

                val doc = Jsoup.parse(html)
                val appDataStr = doc.select("body").attr("data-app-data")

                if (appDataStr.isEmpty()) {
                    LogManager.addLogDebug("❌ Ошибка Auth: нет data-app-data - попытка ${attempt + 1}")
                    if (attempt < 2) {
                        delay(2000L * (attempt + 1))
                        return@repeat
                    }
                    return null
                }

                val json = JSONObject(appDataStr)
                val csrf = json.getString("csrf-token")
                val userId = json.getString("userId")

                val result = Pair(csrf, userId)
                cachedCsrf = result
                lastCsrfFetchTime = currentTime
                
                prefs.edit().putString("cached_user_id", userId).apply()

                return result

            } catch (e: java.net.SocketTimeoutException) {

                LogManager.addLogDebug("⏱️ Timeout на попытке ${attempt + 1}")
                if (attempt < 2) {
                    delay(3000L * (attempt + 1))
                }
            } catch (e: Exception) {

                val isNetworkError = e is java.net.UnknownHostException || e is java.net.ConnectException
                if (isNetworkError) {
                    if (attempt == 0) LogManager.addLogDebug("📶 Ожидание сети (ошибка DNS)...")
                } else {
                    LogManager.addLogDebug("❌ Ошибка сети: ${e.message}")
                }

                if (attempt < 2) {
                    delay(2000L * (attempt + 1))
                }
            }
        }

        return null
    }

    fun copyPickedImageToStorage(uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val ext = when (contentResolver.getType(uri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
            val destFile = File(context.filesDir, "autoimg_${System.currentTimeMillis()}.$ext")
            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            context.filesDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("autoimg_") && f.absolutePath != destFile.absolutePath) f.delete()
            }
            val resultUri = android.net.Uri.fromFile(destFile).toString()
            LogManager.addLogDebug("✅ Картинка скопирована: $resultUri")
            resultUri
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка копирования фото: ${e.message}")
            null
        }
    }

    suspend fun uploadImage(uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver

            val resolvedUri = when {
                uri.scheme == "file" -> {

                    val f = File(uri.path ?: "")
                    if (!f.exists()) {
                        LogManager.addLog("❌ Картинка не найдена на диске: ${uri.path}")
                        LogManager.addLog("ℹ️ Зайдите в настройки команды/автоответа и выберите картинку заново — старый файл был удалён")
                        return null
                    }
                    uri
                }
                uri.scheme == "content" -> {
                    try {
                        contentResolver.openInputStream(uri)?.close()
                        uri
                    } catch (e: SecurityException) {
                        LogManager.addLog("❌ Нет доступа к картинке: ${uri}")
                        LogManager.addLog("ℹ️ Это временный URI от галереи — он истёк. Зайдите в настройки команды/автоответа и выберите картинку заново")
                        return null
                    }
                }
                else -> {
                    LogManager.addLog("❌ Неизвестная схема URI картинки: ${uri.scheme}")
                    return null
                }
            }

            val mimeType = contentResolver.getType(resolvedUri) ?: "image/jpeg"
            val inputStream = contentResolver.openInputStream(resolvedUri)
            if (inputStream == null) {
                LogManager.addLog("❌ Не удалось открыть файл картинки: $resolvedUri")
                LogManager.addLog("ℹ️ Зайдите в настройки команды/автоответа и выберите картинку заново")
                return null
            }

            val file = File(context.cacheDir, "upload_image.jpg")
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            LogManager.addLogDebug("📤 Загружаю картинку: ${file.length()} байт, тип: $mimeType")

            val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val fileIdBody = "0".toRequestBody("text/plain".toMediaTypeOrNull())

            val response = RetrofitInstance.api.uploadChatImage(
                cookie = getCookieString(),
                userAgent = userAgent,
                file = body,
                fileId = fileIdBody
            )
            updateSession(response)
            val jsonStr = readBodySilent(response)

            if (jsonStr.isEmpty()) {
                LogManager.addLog("❌ Сервер не ответил на загрузку картинки (пустой ответ)")
                return null
            }

            val json = JSONObject(jsonStr)

            when {
                json.has("fileId") -> json.getString("fileId")
                json.has("url") -> json.getString("url")
                json.optBoolean("error", false) -> {
                    LogManager.addLog("❌ FunPay отклонил картинку: ${json.optString("msg", jsonStr)}")
                    null
                }
                else -> {
                    LogManager.addLog("❌ Неожиданный ответ при загрузке картинки: $jsonStr")
                    null
                }
            }
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка загрузки картинки: ${e.javaClass.simpleName} — ${e.message}")
            null
        }
    }

    suspend fun downloadAndSaveImage(imageUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(imageUrl).build()
            val response = client.newCall(request).execute()
            val inputStream = response.body?.byteStream() ?: return@withContext false

            val fileName = "funpay_${System.currentTimeMillis()}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        inputStream.copyTo(out)
                    }
                    return@withContext true
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    inputStream.copyTo(out)
                }
                return@withContext true
            }
            false
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка сохранения фото: ${e.message}")
            false
        }
    }

    fun saveOrderConfirmSettings(settings: OrderConfirmSettings) =
        prefs.edit().putString("order_confirm_settings", gson.toJson(settings)).apply()

    fun getOrderConfirmSettings(): OrderConfirmSettings {
        val json = prefs.getString("order_confirm_settings", null)
        return if (json != null) {
            gson.fromJson(json, OrderConfirmSettings::class.java)
        } else {
            OrderConfirmSettings()
        }
    }

    suspend fun checkOrderConfirmations(chats: List<ChatItem>) {
        val settings = getOrderConfirmSettings()
        if (!settings.enabled) return

        val confirmPhrases = listOf(
            "подтвердил успешное выполнение заказа",
            "confirmed that order",
            "підтвердив успішне виконання замовлення"
        )

        for (chat in chats) {
            val lastMsgLower = chat.lastMessage.lowercase()
            val isConfirmEvent = confirmPhrases.any { lastMsgLower.contains(it) }

            
            
            
            
            
            if (!isConfirmEvent) {
                val lastSelf = FunPayRepository.lastOutgoingMessages[chat.id]
                if (lastSelf != null) {
                    val cleanIncoming = chat.lastMessage.replace("...", "").trim().lowercase()
                    val cleanSelf = lastSelf.trim().lowercase()
                    
                    
                    val minLen = minOf(cleanSelf.length, cleanIncoming.length)
                    if (minLen >= 15 && (cleanSelf.contains(cleanIncoming) || cleanIncoming.contains(cleanSelf))) {
                        continue
                    }
                }
            }

            
            
            
            
            
            val shortTriggers = listOf(
                Regex("""(?i)покупател[ьи]\s+[^\s]+\s+подтвердил"""),
                Regex("""(?i)buyer\s+[^\s]+\s+confirmed"""),
                Regex("""(?i)покупець\s+[^\s]+\s+підтвердив""")
            )
            val looksLikeConfirm = isConfirmEvent ||
                    (shortTriggers.any { it.containsMatchIn(chat.lastMessage) } &&
                            Regex("#[a-zA-Z0-9]+").containsMatchIn(chat.lastMessage))

            if (looksLikeConfirm) {
                val orderIdMatch = Regex("#([a-zA-Z0-9]+)").find(chat.lastMessage)
                val orderId = orderIdMatch?.groupValues?.get(1) ?: continue

                if (wasEventProcessed(chat.id, orderId, "confirm")) {
                    continue
                }

                val activeAccount = getActiveAccount()
                if (activeAccount != null) {
                    val buyerPrefix = "покупатель ${activeAccount.username.lowercase()}"
                    if (lastMsgLower.contains(buyerPrefix)) {
                        LogManager.addLogDebug("⏭️ Подтверждение заказа #$orderId — ты покупатель, пропускаем")
                        markEventAsProcessed(chat.id, orderId, "confirm")
                        continue
                    }
                }

                LogManager.addLog("✅ Обнаружено подтверждение заказа #$orderId от ${chat.username}")

                var hasReviewInChat = false
                try {
                    val history = getChatHistory(chat.id)
                    val reviewPhrases = listOf(
                        "написал отзыв к заказу #$orderId",
                        "has given feedback for order #$orderId",
                        "написав відгук до замовлення #$orderId"
                    )

                    for (msg in history.takeLast(20)) {
                        val msgLower = msg.text.lowercase()
                        if (reviewPhrases.any { msgLower.contains(it.lowercase()) }) {
                            hasReviewInChat = true
                            LogManager.addLog("📝 В истории найден отзыв к заказу #$orderId")
                            break
                        }
                    }
                } catch (e: Exception) {
                    LogManager.addLog("⚠️ Ошибка проверки истории: ${e.message}")
                }

                if (hasReviewInChat) {
                    LogManager.addLog("📝 Отзыв оставлен, проверяем автовозврат и ответ...")

                    val autoRefundSettings = getAutoRefundSettings()
                    if (autoRefundSettings.enabled) {
                        val refundCacheKey = "${orderId}_confirm_refund"
                        if (!wasEventProcessed(chat.id, refundCacheKey, "refund_done")) {
                            val orderDetails = getOrderDetails(orderId)
                            if (orderDetails != null && orderDetails.canRefund) {
                                val priceVal = orderDetails.price
                                    .replace(Regex("[^0-9,.]"), "")
                                    .replace(",", ".")
                                    .toDoubleOrNull() ?: 0.0
                                if (priceVal <= autoRefundSettings.maxPrice &&
                                    autoRefundSettings.triggerStars.contains(orderDetails.reviewRating)
                                ) {
                                    LogManager.addLog("💸 Авто-возврат при подтверждении (#$orderId, ${orderDetails.reviewRating}*, $priceVal)")
                                    val success = refundOrder(orderId)
                                    if (success) {
                                        markEventAsProcessed(chat.id, refundCacheKey, "refund_done")
                                        if (autoRefundSettings.sendMessage) {
                                            val refundMsg = FpPlaceholders.applyCombined(
                                                autoRefundSettings.messageText,
                                                chat,
                                                FpPlaceholders.OrderCtx(orderId = orderId, buyerUsername = chat.username)
                                            )
                                            sendWithOptionalImage(chat.id, refundMsg, autoRefundSettings.imageUri, autoRefundSettings.imageFirst)
                                            if (getReadMarkSettings().markAfterAutoRefund) markChatAsRead(chat.id)
                                        }
                                    }
                                } else {
                                    LogManager.addLog("ℹ️ Возврат при подтверждении не нужен (${orderDetails.reviewRating}*, $priceVal)")
                                    markEventAsProcessed(chat.id, refundCacheKey, "refund_done")
                                }
                            }
                        }
                    }

                    val (csrf, userId) = getCsrfAndId() ?: continue
                    handleReview(orderId, chat.username, csrf, userId)
                } else {
                    LogManager.addLog("💬 Отзыва нет, отправляем просьбу оставить отзыв...")

                    val finalText = FpPlaceholders.applyCombined(
                        settings.text,
                        chat,
                        FpPlaceholders.OrderCtx(
                            orderId = orderId,
                            buyerUsername = chat.username
                        )
                    )

                    sendWithOptionalImage(chat.id, finalText, settings.imageUri, settings.imageFirst)
                    if (getReadMarkSettings().markAfterOrderConfirm) markChatAsRead(chat.id)

                    
                    
                    
                    
                    
                    markChatGreeted(chat.id)
                }

                markEventAsProcessed(chat.id, orderId, "confirm")
                kotlinx.coroutines.delay(1500)
            }
        }
    }

    suspend fun sendWithOptionalImage(
        nodeId: String,
        text: String,
        imageUri: String?,
        imageFirst: Boolean
    ): Boolean {
        if (imageUri == null) {
            return if (text.isNotBlank()) sendMessage(nodeId, text) else false
        }

        lastOutgoingMessages[nodeId] = "__image__"
        persistOutgoing(context)
        val imgId = try {
            uploadImage(android.net.Uri.parse(imageUri))
        } catch (e: Exception) {
            LogManager.addLog("❌ Не удалось загрузить картинку: ${e.javaClass.simpleName} — ${e.message}")
            null
        }

        if (imgId == null && text.isBlank()) {
            LogManager.addLog("⛔ Картинка не загрузилась и текст пустой — сообщение не отправлено")
            return false
        }
        if (imgId == null) {
            LogManager.addLog("⚠️ Картинка не загрузилась — отправляю только текст")
        }

        return if (imageFirst) {
            var ok = false
            if (imgId != null) ok = sendMessage(nodeId, "", imgId)
            if (text.isNotBlank()) { kotlinx.coroutines.delay(600); ok = sendMessage(nodeId, text) || ok }
            ok
        } else {
            var ok = false
            if (text.isNotBlank()) ok = sendMessage(nodeId, text)
            if (imgId != null) { kotlinx.coroutines.delay(600); ok = sendMessage(nodeId, "", imgId) || ok }
            ok
        }
    }

    suspend fun sendMessage(nodeId: String, text: String, imageId: String? = null): Boolean {
        val appData = getCsrfAndId() ?: return false
        val (csrf, _) = appData
        return try {
            LogManager.addLog("📤 Отправка ($nodeId): '${if(imageId != null) "IMAGE $imageId" else text}'")
            val requestJson = JSONObject()
            requestJson.put("action", "chat_message")
            val dataJson = JSONObject()
            dataJson.put("node", nodeId)
            dataJson.put("last_message", -1)

            if (imageId != null) {
                dataJson.put("image_id", imageId)
                dataJson.put("content", "")
            } else {
                dataJson.put("content", text)
            }

            requestJson.put("data", dataJson)

            val resp = RetrofitInstance.api.runnerSend(
                cookie = getCookieString(), userAgent = userAgent, request = requestJson.toString(), csrfToken = csrf, objects = "[]"
            )
            updateSession(resp)
            val body = readBodySilent(resp)
            val json = JSONObject(body)

            val success = !json.optBoolean("error", false)
            if (success && text.isNotEmpty()) {
                lastOutgoingMessages[nodeId] = text.trim()
                persistOutgoing(context)
            }
            success
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка отправки: ${e.message}")
            false
        }
    }


    suspend fun rewriteMessage(
        text: String,
        contextHistory: String,
        chat: ChatItem? = null,
        orderCtx: FpPlaceholders.OrderCtx? = null
    ): String? {
        return try {
            val perms = getAiVarPermissions()
            val systemPrompt = "You are a text editing model. Follow user instructions precisely."

            val contextBlock = buildString {
                if (perms.allowChatHistory && contextHistory.isNotBlank()) {
                    append("\n--- ИСТОРИЯ ПЕРЕПИСКИ ---\n")
                    append(contextHistory)
                    append("\n--- КОНЕЦ ИСТОРИИ ---\n")
                }
                if (perms.allowUsername && chat != null) append("\nИмя покупателя: ${chat.username}")
                if (perms.allowChatId && chat != null) append("\nID чата: ${chat.id}")
                if (perms.allowOrderId && orderCtx?.orderId != null) append("\nID заказа: ${orderCtx.orderId}")
                if (perms.allowLotName && orderCtx?.lotName != null) append("\nЛот: ${orderCtx.lotName}")
                if (perms.allowMessageText && chat != null) append("\nПоследнее сообщение покупателя: ${chat.lastMessage}")
            }

            val combinedUserPrompt = """
                    Ты — ИИ-ассистент, который помогает продавцу на FunPay. Твоя задача — переписать его черновик сообщения, сохранив основной смысл, но сделав его вежливым, профессиональным и четким.

                    --- ОСНОВНЫЕ ПРАВИЛА ---
                    1.  СОХРАНЯЙ СМЫСЛ: Твой ответ должен передавать ТОТ ЖЕ САМЫЙ смысл, что и черновик продавца. Не добавляй новые идеи, вопросы или предложения от себя.
                    2.  БУДЬ КРАТОК: Ответ должен быть настолько же коротким, насколько позволяет исходное сообщение. Не пиши длинные тексты, если черновик короткий.
                    3.  ДЕЙСТВУЙ ОТ ЛИЦА ПРОДАВЦА: Всегда пиши от имени продавца.
                    4.  УЧИТЫВАЙ КОНТЕКСТ: Изучи историю переписки, чтобы твой ответ был уместен.
                    5.  СТИЛЬ: Используй вежливый, но уверенный тон.
                    6.  НИКАКИХ ЛИШНИХ СЛОВ: Не добавляй стандартные фразы вроде "Здравствуйте", если их не было в исходном черновике или они неуместны.
                    7.  ТОЛЬКО ТЕКСТ: Твой итоговый ответ — это ТОЛЬКО готовый текст сообщения. Без кавычек, заголовков (типа "Вот ваш текст") или объяснений.
                    $contextBlock

                    ЧЕРНОВИК МОЕГО СООБЩЕНИЯ (от продавца): "$text"

                    ПЕРЕПИШИ МОЙ ЧЕРНОВИК, СТРОГО СЛЕДУЯ ВСЕМ ПРАВИЛАМ.
                    ГОТОВЫЙ ТЕКСТ:
                    """.trimIndent()

            val messages = JSONArray()
            val sysMsg = JSONObject().put("role", "system").put("content", systemPrompt)
            val userMsg = JSONObject().put("role", "user").put("content", combinedUserPrompt)
            messages.put(sysMsg)
            messages.put(userMsg)

            val payload = JSONObject()
            payload.put("messages", messages)
            payload.put("modelName", "ChatGPT 4o")
            payload.put("currentPagePath", "/chatgpt-4o")

            val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())


            val response = RetrofitInstance.api.rewriteText(
                auth = "Bearer fptoolsdim",
                body = body
            )

            if (response.isSuccessful) {
                val jsonStr = response.body()?.string() ?: return null
                val json = JSONObject(jsonStr)
                return json.optString("response", json.optString("result")).trim()
            } else {
                LogManager.addLog("❌ Ошибка AI: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка AI запроса: ${e.message}")
            null
        }
    }


    suspend fun raiseAllLots() {
        if (!getSetting("raise_enabled")) return

        val smartMode = isSmartRaiseEnabled()
        val lastRun = prefs.getLong("last_raise_time", 0)
        val multiplier = getDelayMultiplier()
        val baseInterval = getRaiseInterval()
        val fixedInterval = (baseInterval * multiplier).toLong() * 60 * 1000L

        
        
        
        if (!smartMode) {
            if (System.currentTimeMillis() - lastRun < fixedInterval) return
        } else {
            
            
            if (System.currentTimeMillis() - lastRun < 20_000L) return
            
            val minNextAt = SmartRaise.minNextAt(prefs)
            if (minNextAt != null && System.currentTimeMillis() < minNextAt) return
        }

        try {
            if (nodeToGameMap.isEmpty()) updateGameData()
            val authData = getCsrfAndId() ?: return
            val userId = authData.second

            val profileResponse = RetrofitInstance.api.getUserProfile(userId, getCookieString(), userAgent)
            updateSession(profileResponse)
            val html = readBodySilent(profileResponse)

            if (isCloudflare(html)) {
                LogManager.addLog("⛔ CF Block (Profile)")
                return
            }

            val doc = Jsoup.parse(html)
            val gameDivs = doc.select("div.offer-list-title-container")
            var raisedCount = 0
            var skippedCooldown = 0

            for (div in gameDivs) {
                val subcategoryLink = div.select("h3 a").attr("href")
                if (subcategoryLink.contains("chips")) continue

                val nodeId = subcategoryLink.split("/").filter { it.isNotEmpty() }.lastOrNull()?.toIntOrNull()
                var gameId = div.attr("data-game-id").toIntOrNull()
                if (gameId == null && nodeId != null) gameId = nodeToGameMap[nodeId]

                if (gameId != null && nodeId != null) {

                    
                    if (smartMode && SmartRaise.isCoolingDown(prefs, gameId)) {
                        skippedCooldown++
                        continue
                    }

                    val resp1 = RetrofitInstance.api.raiseLotInitial(
                        cookie = getCookieString(), userAgent = userAgent, gameId = gameId, nodeId = nodeId
                    )
                    updateSession(resp1)
                    val jsonStr1 = readBodySilent(resp1)
                    val json1 = JSONObject(jsonStr1)

                    if (json1.has("modal")) {
                        val modalHtml = json1.getString("modal")
                        val pattern = Pattern.compile("value=\"(.*?)\"")
                        val matcher = pattern.matcher(modalHtml)
                        val nodeIds = mutableListOf<Int>()
                        while (matcher.find()) { matcher.group(1)?.toIntOrNull()?.let { nodeIds.add(it) } }

                        if (nodeIds.isNotEmpty()) {
                            val resp2 = RetrofitInstance.api.raiseLotCommit(
                                cookie = getCookieString(), userAgent = userAgent, gameId = gameId, nodeId = nodeId, nodeIds = nodeIds
                            )
                            updateSession(resp2)

                            
                            delay(1000)

                            val resp3 = RetrofitInstance.api.raiseLotCommit(
                                cookie = getCookieString(), userAgent = userAgent, gameId = gameId, nodeId = nodeId, nodeIds = nodeIds
                            )
                            updateSession(resp3)

                            val jsonStr2 = readBodySilent(resp2)
                            val jsonStr3 = readBodySilent(resp3)
                            val json2 = try { JSONObject(jsonStr2) } catch (_: Exception) { JSONObject() }
                            val json3 = try { JSONObject(jsonStr3) } catch (_: Exception) { JSONObject() }

                            
                            val combinedMsg = listOf(json2.optString("msg"), json3.optString("msg"))
                                .firstOrNull { it.isNotBlank() }
                            if (smartMode && !combinedMsg.isNullOrBlank() &&
                                (combinedMsg.contains("Подождите", true) ||
                                        combinedMsg.contains("Please wait", true) ||
                                        combinedMsg.contains("Зачекайте", true) ||
                                        combinedMsg.contains("Почекайте", true))
                            ) {
                                val waitSec = SmartRaise.parseWaitSeconds(combinedMsg)
                                SmartRaise.setCooldown(prefs, gameId, waitSec)
                                LogManager.addLog("⏳ Smart raise: '$combinedMsg' → ждём ${waitSec}с для game $gameId")
                            } else if (!json2.optBoolean("error")) {
                                raisedCount++
                                
                                
                                if (smartMode) SmartRaise.setCooldown(prefs, gameId, 4L * 3600L)
                            }
                        }
                    } else {
                        
                        val msg1 = json1.optString("msg")
                        if (smartMode && !msg1.isNullOrBlank() &&
                            (msg1.contains("Подождите", true) ||
                                    msg1.contains("Please wait", true) ||
                                    msg1.contains("Зачекайте", true))
                        ) {
                            val waitSec = SmartRaise.parseWaitSeconds(msg1)
                            SmartRaise.setCooldown(prefs, gameId, waitSec)
                            LogManager.addLog("⏳ Smart raise: '$msg1' → ждём ${waitSec}с для game $gameId")
                        } else {
                            delay(1000)
                            val respRetry = RetrofitInstance.api.raiseLotInitial(
                                cookie = getCookieString(), userAgent = userAgent, gameId = gameId, nodeId = nodeId
                            )
                            updateSession(respRetry)

                            if (!json1.optBoolean("error")) {
                                raisedCount++
                                if (smartMode) SmartRaise.setCooldown(prefs, gameId, 4L * 3600L)
                            }
                        }
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
            if (raisedCount > 0) LogManager.addLog("🏁 Подняты все $raisedCount" +
                    if (skippedCooldown > 0) " (пропущено по КД: $skippedCooldown)" else "")
            prefs.edit().putLong("last_raise_time", System.currentTimeMillis()).apply()

        } catch (e: Exception) {
            LogManager.addLog("❌ ОШИБКА: ${e.message}")
        }
    }


    suspend fun getChats(): List<ChatItem> {
        return try {
            val authData = getCsrfAndId()
            if (authData == null) {
                return emptyList()
            }

            val (csrf, userId) = authData

            repeat(2) { attempt ->
                try {
                    val objects = "[{\"type\":\"chat_bookmarks\",\"id\":\"$userId\",\"tag\":\"00000000\",\"data\":false}]"

                    val response = withContext(Dispatchers.IO) {
                        RetrofitInstance.api.runnerGet(
                            cookie = getCookieString(),
                            userAgent = userAgent,
                            objects = objects,
                            request = "false",
                            csrfToken = csrf
                        )
                    }

                    updateSession(response)
                    val jsonStr = readBodySilent(response)

                    
                    if (jsonStr.contains("account-blocked-box")) {
                        val doc = Jsoup.parse(jsonStr)
                        val reason = doc.select(".account-blocked-box p").joinToString("\n\n") { it.text() }
                        banInfo.value = BanInfo(reason.ifBlank { "Аккаунт заблокирован администрацией FunPay." })
                        return emptyList()
                    } else {
                        banInfo.value = null
                    }

                    if (jsonStr.isEmpty() || jsonStr == "null") {
                        if (attempt < 1) {
                            delay(2000)
                            return@repeat
                        }
                        return emptyList()
                    }

                    val json = JSONObject(jsonStr)

                    if (json.optBoolean("error", false)) {
                        LogManager.addLog("❌ Runner вернул ошибку, если это повторится, покажите разработчику: ${json.optString("msg", "unknown")}")
                        if (attempt < 1) {
                            delay(2000)
                            cachedCsrf = null
                            return@repeat
                        }
                        return emptyList()
                    }

                    val objectsArray = json.getJSONArray("objects")
                    val list = mutableListOf<ChatItem>()

                    for (i in 0 until objectsArray.length()) {
                        val obj = objectsArray.getJSONObject(i)
                        if (obj.getString("type") == "chat_bookmarks") {
                            val data = obj.getJSONObject("data")
                            val html = data.getString("html")
                            val doc = Jsoup.parse(html)

                            doc.select("a.contact-item").forEach { item ->
                                var photoUrl = "https://funpay.com/img/layout/avatar.png"
                                val style = item.select("div.avatar-photo").attr("style")

                                if (style.contains("url(")) {
                                    photoUrl = style.substringAfter("url(").substringBefore(")").replace("\"", "").replace("'", "")
                                    if (photoUrl.startsWith("/")) photoUrl = "https://funpay.com$photoUrl"
                                }

                                val chatId = item.attr("data-id")
                                val lastMsgText = item.select("div.contact-item-message").text()
                                var isUnread = item.hasClass("unread")


                                val lastSent = lastOutgoingMessages[chatId]
                                if (lastSent != null && lastSent != "__image__") {

                                    val isTruncated = lastMsgText.endsWith("...")
                                    val cleanMsg = lastMsgText.replace("...", "").trim().lowercase()
                                    val cleanSent = lastSent.trim().lowercase()
                                    if (cleanSent == cleanMsg || (isTruncated && cleanSent.startsWith(cleanMsg))) {
                                        isUnread = false
                                    }
                                }

                                list.add(ChatItem(
                                    id = chatId,
                                    username = item.select("div.media-user-name").text(),
                                    lastMessage = lastMsgText,
                                    isUnread = isUnread,
                                    avatarUrl = photoUrl,
                                    date = item.select("div.contact-item-time").text()
                                ))
                            }
                        }
                    }

                    return list

                } catch (e: java.net.SocketTimeoutException) {
                    if (attempt < 1) {
                        delay(3000)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (attempt < 1) {
                        delay(2000)
                    }
                }
            }

            emptyList()

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getChatHistory(chatId: String): List<MessageItem> = withContext(Dispatchers.IO) {
        val authData = getCsrfAndId() ?: return@withContext emptyList()
        val (_, myUserId) = authData

        try {
            val url = "https://funpay.com/chat/?node=$chatId"
            val response = RetrofitInstance.api.getChatPage(url, getCookieString(), userAgent)
            updateSession(response)
            val html = readBodySilent(response)

            if (html.isEmpty()) return@withContext emptyList()

            val doc = Jsoup.parse(html)
            val messageElements = doc.select(".chat-message-list .chat-msg-item")

            val resultList = mutableListOf<MessageItem>()
            var currentAuthor = "Unknown"
            var currentAuthorId = ""
            var currentTime = ""
            var currentAvatarUrl: String? = null

            for (itemDiv in messageElements) {
                val idStr = itemDiv.attr("id").removePrefix("message-")
                if (idStr.isEmpty()) continue

                val authorLink = itemDiv.selectFirst(".chat-msg-author-link")
                
                
                val avatarInItem = itemDiv.selectFirst(".media-left .avatar-photo img")
                if (avatarInItem != null) {
                    var src = avatarInItem.attr("src")
                    if (src.isNotEmpty()) {
                        if (src.startsWith("/")) src = "https://funpay.com$src"
                        currentAvatarUrl = src
                    }
                }

                if (authorLink != null) {
                    currentAuthor = authorLink.text().trim()
                    currentAuthorId = authorLink.attr("href").substringAfter("/users/").substringBefore("/").trim()
                } else {
                    val mediaUserName = itemDiv.selectFirst(".media-user-name")
                    if (mediaUserName != null) {
                        val textName = mediaUserName.ownText().trim()
                        if (textName.isNotEmpty()) {
                            currentAuthor = textName
                            currentAuthorId = "0"
                        }
                    }
                }

                val dateEl = itemDiv.selectFirst(".chat-msg-date")
                if (dateEl != null) {
                    currentTime = dateEl.attr("title").ifEmpty { dateEl.text() }
                }

                val text = itemDiv.select(".chat-msg-text").text().trim()

                var imageUrl: String? = null
                val imgLink = itemDiv.selectFirst(".chat-img-link")
                if (imgLink != null) {
                    imageUrl = imgLink.attr("href")
                    if (imageUrl.startsWith("/")) imageUrl = "https://funpay.com$imageUrl"
                }

                val badge = itemDiv.selectFirst(".chat-msg-author-label")?.text()

                val isMe = (currentAuthorId == myUserId)

                if (text.isNotEmpty() || imageUrl != null) {
                    resultList.add(MessageItem(
                        id = idStr,
                        author = currentAuthor,
                        text = text,
                        isMe = isMe,
                        time = currentTime,
                        imageUrl = imageUrl,
                        badge = badge,
                        authorId = currentAuthorId.ifBlank { null },
                        authorAvatarUrl = currentAvatarUrl
                    ))
                }
            }

            return@withContext resultList
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun fetchOlderMessages(chatId: String, beforeMessageId: String): List<MessageItem> = withContext(Dispatchers.IO) {
        val authData = getCsrfAndId() ?: return@withContext emptyList()
        val (_, myUserId) = authData

        var nodeName = chatId
        if (!chatId.startsWith("users-")) {
            try {
                val url = "https://funpay.com/chat/history?node=$chatId&last_message=0"
                val resp = RetrofitInstance.api.getChatHistory(url, getCookieString(), userAgent, "XMLHttpRequest")
                val jsonStr = readBodySilent(resp)
                if (jsonStr.isNotEmpty()) {
                    val json = JSONObject(jsonStr)
                    val correctName = json.optJSONObject("chat")?.optJSONObject("node")?.optString("name")
                    if (!correctName.isNullOrEmpty() && correctName.startsWith("users-")) {
                        nodeName = correctName
                    }
                }
            } catch (e: Exception) { }
        }

        try {
            val url = "https://funpay.com/chat/history?node=$nodeName&last_message=$beforeMessageId"
            val response = RetrofitInstance.api.getChatHistory(url, getCookieString(), userAgent, "XMLHttpRequest")
            updateSession(response)
            val jsonStr = readBodySilent(response)

            if (jsonStr.isEmpty() || jsonStr == "null") return@withContext emptyList()

            val json = JSONObject(jsonStr)
            if (json.optBoolean("error", false)) return@withContext emptyList()

            val messagesArray = json.optJSONObject("chat")?.optJSONArray("messages") ?: return@withContext emptyList()

            val resultList = mutableListOf<MessageItem>()
            var currentAuthor = "Unknown"
            var currentAuthorId = ""
            var currentTime = ""
            var currentAvatarUrl: String? = null

            for (i in 0 until messagesArray.length()) {
                val msgObj = messagesArray.getJSONObject(i)
                val html = msgObj.optString("html")
                val rawAuthorId = msgObj.optString("author")

                val doc = Jsoup.parseBodyFragment(html)
                val itemDiv = doc.selectFirst(".chat-msg-item") ?: continue

                val idStr = itemDiv.attr("id").removePrefix("message-").ifEmpty { msgObj.optString("id") }

                val authorLink = itemDiv.selectFirst(".chat-msg-author-link")
                val avatarInItem = itemDiv.selectFirst(".media-left .avatar-photo img")
                if (avatarInItem != null) {
                    var src = avatarInItem.attr("src")
                    if (src.isNotEmpty()) {
                        if (src.startsWith("/")) src = "https://funpay.com$src"
                        currentAvatarUrl = src
                    }
                }

                if (authorLink != null) {
                    currentAuthor = authorLink.text().trim()
                    currentAuthorId = authorLink.attr("href").substringAfter("/users/").substringBefore("/").trim()
                } else if (rawAuthorId.isNotEmpty() && rawAuthorId != "0") {
                    currentAuthorId = rawAuthorId
                } else {
                    val mediaUserName = itemDiv.selectFirst(".media-user-name")
                    if (mediaUserName != null) {
                        val textName = mediaUserName.ownText().trim()
                        if (textName.isNotEmpty()) {
                            currentAuthor = textName
                            currentAuthorId = "0"
                        }
                    }
                }

                val dateEl = itemDiv.selectFirst(".chat-msg-date")
                if (dateEl != null) {
                    currentTime = dateEl.attr("title").ifEmpty { dateEl.text() }
                }

                val text = itemDiv.select(".chat-msg-text").text().trim()

                var imageUrl: String? = null
                val imgLink = itemDiv.selectFirst(".chat-img-link")
                if (imgLink != null) {
                    imageUrl = imgLink.attr("href")
                    if (imageUrl.startsWith("/")) imageUrl = "https://funpay.com$imageUrl"
                }

                val badge = itemDiv.selectFirst(".chat-msg-author-label")?.text()

                val isMe = (currentAuthorId == myUserId)

                if (text.isNotEmpty() || imageUrl != null) {
                    resultList.add(MessageItem(
                        id = idStr,
                        author = currentAuthor,
                        text = text,
                        isMe = isMe,
                        time = currentTime,
                        imageUrl = imageUrl,
                        badge = badge,
                        authorId = currentAuthorId.ifBlank { null },
                        authorAvatarUrl = currentAvatarUrl
                    ))
                }
            }

            return@withContext resultList
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun getChatInfo(chatId: String): ChatInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://funpay.com/chat/?node=$chatId"
                val response = RetrofitInstance.api.getChatPage(url, getCookieString(), userAgent)
                val html = readBodySilent(response)
                if (html.isEmpty()) return@withContext null

                val doc = Jsoup.parse(html)

                var lookingAtLink: String? = null
                var lookingAtName: String? = null
                var registrationDate: String? = null
                var language: String? = null

                val mobilePanel = doc.select(".chat-panel-mobile a").first()
                if (mobilePanel != null) {
                    lookingAtLink = mobilePanel.attr("href")
                    lookingAtName = mobilePanel.text()
                } else {
                    val paramItem = doc.select(".param-item.chat-panel[data-type='c-p-u'] div a").first()
                    if (paramItem != null) {
                        lookingAtLink = paramItem.attr("href")
                        lookingAtName = paramItem.text()
                    }
                }

                if (lookingAtLink != null && !lookingAtLink.startsWith("http")) {
                    lookingAtLink = "https://funpay.com$lookingAtLink"
                }

                val regParam = doc.select(".param-item:contains(Дата регистрации) div").first()
                if (regParam != null) {
                    registrationDate = regParam.wholeText().replace("\n", " ").trim()
                }

                val langParam = doc.select(".param-item:contains(Язык) div").first()
                if (langParam != null) {
                    language = langParam.text().trim()
                }

                val avatarUrl = doc.select(".chat-header .media-left img").firstOrNull()?.attr("src")?.let {
                    if (it.startsWith("http")) it else "https://funpay.com$it"
                }

                val userStatus = doc.select(".chat-header .media-user-status").text().trim().ifEmpty { null }

                ChatInfo(lookingAtLink, lookingAtName, registrationDate, language, avatarUrl, userStatus)
            } catch (e: Exception) {
                null
            }
        }
    }


    suspend fun getOrderDetails(orderId: String): OrderDetails? {
        return withContext(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.api.getOrder(orderId, getCookieString(), userAgent)
                updateSession(response)
                val html = readBodySilent(response)
                val doc = Jsoup.parse(html)

                val id = doc.select("h1.page-header").text().substringAfter("#").substringBefore(" ").trim()
                val status = doc.select(".page-header .text-success").text().ifEmpty {
                    doc.select(".page-header span").last()?.text() ?: ""
                }

                var gameTitle = ""
                var shortDesc = ""
                var price = ""
                val params = mutableMapOf<String, String>()
                var hasAutoDelivery = false

                
                
                val paidProductHeaders = listOf(
                    "Оплаченный товар", "Оплаченные товары",
                    "Оплачений товар", "Оплачені товари",
                    "Paid product", "Paid products"
                )

                doc.select(".param-item").forEach { item ->
                    val label = item.select("h5").text()
                    if (label.contains("Действия")) return@forEach

                    val value = item.select("div").last()?.text() ?: ""

                    if (label.contains("Игра")) gameTitle = value
                    if (label.contains("Краткое описание")) shortDesc = value
                    if (label.contains("Сумма")) price = item.select("span.h1").text() + " " + item.select("strong").last()?.text()

                    if (paidProductHeaders.any { label.equals(it, ignoreCase = true) || label.contains(it, ignoreCase = true) }) {
                        if (item.select("span.secret-placeholder").isNotEmpty()) {
                            hasAutoDelivery = true
                        }
                    }

                    if (label.isNotEmpty() && value.isNotEmpty()) {
                        params[label] = value
                    }
                }

                val buyerName = doc.select(".chat-float .media-user-name a").first()?.text() ?: "Unknown"
                var buyerAvatar = doc.select(".chat-float .media-left img").attr("src")
                if (buyerAvatar.startsWith("/")) buyerAvatar = "https://funpay.com$buyerAvatar"

                val canRefund = doc.select("button.btn-refund").isNotEmpty()
                val canConfirm = doc.select("button.btn-complete").isNotEmpty()


                
                
                
                
                val reviewContainer = doc.select(".review-container[data-order=$id]").first()

                val reviewTextRaw = reviewContainer
                    ?.select(".review-item-text")?.first()?.text()?.trim().orEmpty()

                
                val ratingAttr = reviewContainer?.attr("data-rating")?.toIntOrNull() ?: 0
                val ratingFromClass = reviewContainer
                    ?.select(".review-item-rating .rating div")?.first()
                    ?.className()?.filter { it.isDigit() }?.toIntOrNull() ?: 0

                val reviewRating = if (ratingAttr > 0) ratingAttr else ratingFromClass

                
                val hasReview = reviewContainer != null && reviewRating > 0

                val reviewText = if (hasReview) reviewTextRaw else ""

                var sellerReply = ""
                if (hasReview) {
                    val replyDiv = reviewContainer?.select(".review-item-answer")?.first()
                    if (replyDiv != null) {
                        replyDiv.select(".review-controls").remove()
                        sellerReply = replyDiv.text().trim()
                    }
                }

                val parsedLotId = doc.select("a[href*=/lots/offer], a[href*=/chips/offer]")
                    .firstNotNullOfOrNull { LotUrlParser.parse(it.attr("href"))?.id }

                
                var isBuyer = false
                if (canConfirm) isBuyer = true 
                if (doc.select(".order-review h5, .review-list h5").text().contains("для продавца", ignoreCase = true)) isBuyer = true
                if (doc.select(".js-balance-pay").isNotEmpty()) isBuyer = true
                if (doc.select(".param-item h5").any { it.text().contains("Продавец", ignoreCase = true) || it.text().contains("Seller", ignoreCase = true) }) isBuyer = true

                val chatPartnerHref = doc.select(".chat-float .media-user-name a").first()?.attr("href").orEmpty()
                val chatPartnerId = Regex("/users/(\\d+)").find(chatPartnerHref)?.groupValues?.get(1).orEmpty()

                OrderDetails(
                    id = id,
                    status = status,
                    gameTitle = gameTitle,
                    shortDesc = shortDesc,
                    price = price,
                    buyerName = buyerName,
                    buyerAvatar = buyerAvatar,
                    canRefund = canRefund,
                    canConfirm = canConfirm, 
                    hasReview = hasReview,
                    reviewRating = reviewRating,
                    reviewText = reviewText,
                    sellerReply = sellerReply,
                    params = params,
                    hasAutoDelivery = hasAutoDelivery,
                    lotId = parsedLotId,
                    isBuyer = isBuyer,
                    buyerId = chatPartnerId
                )
            } catch (e: Exception) {
                LogManager.addLog("❌ Ошибка загрузки заказа $orderId: ${e.message}")
                null
            }
        }
    }

    suspend fun refundOrder(orderId: String): Boolean {
        val appData = getCsrfAndId() ?: return false
        val (csrf, _) = appData
        return try {
            LogManager.addLog("💸 Возврат средств по заказу $orderId...")
            val response = RetrofitInstance.api.refundOrder(
                cookie = getCookieString(),
                userAgent = userAgent,
                csrfToken = csrf,
                orderId = orderId
            )
            updateSession(response)
            response.isSuccessful
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка возврата: ${e.message}")
            false
        }
    }



    suspend fun deleteReviewReply(orderId: String): Boolean {
        val appData = getCsrfAndId() ?: return false
        val (csrf, userId) = appData
        return try {
            LogManager.addLog("🗑️ Удаление ответа на отзыв (заказ $orderId)...")
            val response = RetrofitInstance.api.deleteReviewReply(
                cookie = getCookieString(),
                userAgent = userAgent,
                csrfToken = csrf,
                orderId = orderId,
                authorId = userId
            )
            updateSession(response)
            response.isSuccessful
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка удаления ответа: ${e.message}")
            false
        }
    }

    fun getMyUserId(): String =
        cachedCsrf?.second?.takeIf { it.isNotEmpty() }
            ?: prefs.getString("cached_user_id", "").orEmpty()

    suspend fun writeReview(orderId: String, text: String, rating: Int): Boolean =
        replyToReview(orderId, text, rating.coerceIn(1, 5))

    suspend fun deleteMyReview(orderId: String): Boolean = deleteReviewReply(orderId)

    var cachedSales: List<SaleItem> = emptyList()
    var isSalesFullLoaded: Boolean = false

    data class OrderSummaryItem(
        val orderId: String,
        val title: String,
        val price: String,
        val partnerName: String,
        val date: String,
        val status: String
    )

    suspend fun getOrdersWithBuyer(buyerName: String, isSales: Boolean = true): List<OrderSummaryItem> =
        withContext(Dispatchers.IO) {
            try {
                val safeName = buyerName.trim()
                val encodedName = java.net.URLEncoder.encode(safeName, "UTF-8").replace("+", "%20")
                val url = if (isSales) {
                    "https://funpay.com/orders/trade?buyer=$encodedName"
                } else {
                    "https://funpay.com/purchases/"
                }
                val req = Request.Builder()
                    .url(url)
                    .header("Cookie", getCookieString())
                    .header("User-Agent", userAgent)
                    .build()
                val response = repoClient.newCall(req).execute()
                updateSession(response)
                val html = response.body?.string() ?: throw Exception("Пустой ответ от сервера")

                if (isCloudflare(html)) throw Exception("Cloudflare защита (повторите позже)")
                if (html.contains("\"isLoggedIn\":false") || html.contains("Войти</button>")) throw Exception("Необходима авторизация")

                val doc = Jsoup.parse(html)
                val result = mutableListOf<OrderSummaryItem>()
                doc.select("a.tc-item").forEach { row ->
                    val href = row.attr("href")
                    val orderId = Regex("/orders/([A-Z0-9]+)").find(href)?.groupValues?.get(1) ?: return@forEach
                    val title = row.select(".order-desc div").firstOrNull()?.text()?.trim()
                        ?.ifEmpty { row.select(".tc-desc-text").text().trim() }
                        ?: row.select(".tc-desc-text, .tc-title").text().trim()
                    val price = row.select(".tc-price").text().trim()
                    val partner = row.select(".media-user-name").text().trim()
                    val date = row.select(".tc-date-time").text().trim()
                    val status = row.select(".tc-status span, .badge").text().trim()

                    if (isSales || partner.contains(safeName, ignoreCase = true) || safeName.isEmpty()) {
                        result.add(OrderSummaryItem(orderId, title, price, partner, date, status))
                    }
                }
                result
            } catch (e: Exception) {
                LogManager.addLogDebug("getOrdersWithBuyer failed: ${e.message}")
                throw e
            }
        }

    data class SaleItem(
        val orderId: String,
        val title: String,        
        val description: String,  
        val price: String,        
        val priceValue: Double,   
        val buyerName: String,
        val buyerAvatar: String,
        val buyerProfileUrl: String,
        val date: String,
        val status: String,       
        val statusText: String,   
        val game: String,         
        val category: String      
    )

    data class SalesStats(
        val totalRevenue: Double,
        val totalOrders: Int,
        val closedOrders: Int,
        val pendingOrders: Int,
        val refundedOrders: Int,
        val uniqueBuyers: Int,
        val avgCheck: Double,
        val topBuyer: String,
        val topSale: Double,
        val popularProduct: String,
        val popularCategory: String,
        val unconfirmedRevenue: Double,
        val unconfirmedCount: Int
    )

    suspend fun fetchSalesPage(continueToken: String? = null): Pair<List<SaleItem>, String?> =
        withContext(Dispatchers.IO) {
            try {
                val reqBuilder = Request.Builder()
                    .url("https://funpay.com/orders/trade")
                    .header("Cookie", getCookieString())
                    .header("User-Agent", userAgent)

                
                if (continueToken != null) {
                    val formBody = FormBody.Builder()
                        .add("continue", continueToken)
                        .build()
                    reqBuilder.post(formBody)
                    reqBuilder.header("X-Requested-With", "XMLHttpRequest")
                    reqBuilder.header("Accept", "*/*")
                } else {
                    
                    reqBuilder.get()
                }

                val html = repoClient.newCall(reqBuilder.build()).execute().body?.string() ?: return@withContext Pair(emptyList(), null)
                val doc = Jsoup.parse(html)

                val items = mutableListOf<SaleItem>()
                doc.select("a.tc-item").forEach { row ->
                    val href = row.attr("href")
                    val orderId = Regex("/orders/([A-Z0-9]+)").find(href)?.groupValues?.get(1) ?: return@forEach

                    val descEl = row.select(".order-desc div").firstOrNull()
                    val description = descEl?.text()?.trim().orEmpty()
                    val gameAndCat = row.select(".order-desc .text-muted").text().trim()
                    val gameCatParts = gameAndCat.split(", ", limit = 2)
                    val game = gameCatParts.getOrNull(0).orEmpty().trim()
                    val category = gameCatParts.getOrNull(1).orEmpty().trim()
                    val title = if (game.isNotEmpty()) gameAndCat else description

                    val priceText = row.select(".tc-price").text().trim()
                    val priceValue = priceText.replace(Regex("[^0-9.,]"), "")
                        .replace(",", ".").toDoubleOrNull() ?: 0.0

                    val buyerName = row.select(".media-user-name").text().trim()
                    val buyerHref = row.select(".media-user-name [data-href], .avatar-photo").attr("data-href")
                    val buyerBgStyle = row.select(".avatar-photo").attr("style")
                    val buyerAvatar = Regex("url\\(([^)]+)\\)").find(buyerBgStyle)?.groupValues?.get(1)
                        ?.trim('\'', '"').orEmpty()
                        .let { if (it.startsWith("/")) "https://funpay.com$it" else it }

                    val date = row.select(".tc-date-time").text().trim()

                    val statusClass = row.attr("class")
                    val statusTextEl = row.select(".tc-status").text().trim()
                    val status = when {
                        statusClass.contains("warning") || statusTextEl.contains("Возврат") -> "refunded"
                        row.select(".tc-status.text-success").isNotEmpty() -> "closed"
                        row.select(".tc-status.text-primary").isNotEmpty() -> "paid"
                        else -> "closed"
                    }

                    items.add(SaleItem(
                        orderId = orderId,
                        title = title.ifEmpty { description.take(60) },
                        description = description,
                        price = priceText,
                        priceValue = priceValue,
                        buyerName = buyerName,
                        buyerAvatar = buyerAvatar,
                        buyerProfileUrl = buyerHref,
                        date = date,
                        status = status,
                        statusText = statusTextEl.ifEmpty { if (status == "closed") "Закрыт" else if (status == "paid") "Оплачен" else "Возврат" },
                        game = game,
                        category = category
                    ))
                }

                
                val nextToken = doc.select("input[name=continue]").attr("value")
                    .takeIf { it.isNotEmpty() }

                Pair(items, nextToken)
            } catch (e: Exception) {
                LogManager.addLogDebug("fetchSalesPage failed: ${e.message}")
                Pair(emptyList(), null)
            }
        }

    fun computeSalesStats(sales: List<SaleItem>): SalesStats {
        val closed   = sales.filter { it.status == "closed" }
        val pending  = sales.filter { it.status == "paid" }
        val refunded = sales.filter { it.status == "refunded" }

        val totalRevenue = closed.sumOf { it.priceValue }
        val unconfirmedRevenue = pending.sumOf { it.priceValue }

        val buyerCounts = sales.filter { it.status != "refunded" }
            .groupBy { it.buyerName }
        val topBuyer = buyerCounts.maxByOrNull { it.value.size }?.key.orEmpty()

        val topSale = closed.maxOfOrNull { it.priceValue } ?: 0.0

        val productCounts = closed.groupBy { it.description.take(80) }
        val popularProduct = productCounts.maxByOrNull { it.value.size }?.key.orEmpty()

        val catCounts = closed.filter { it.game.isNotEmpty() }
            .groupBy { "${it.game}, ${it.category}" }
        val popularCategory = catCounts.maxByOrNull { it.value.size }?.key.orEmpty()

        return SalesStats(
            totalRevenue = totalRevenue,
            totalOrders = sales.size,
            closedOrders = closed.size,
            pendingOrders = pending.size,
            refundedOrders = refunded.size,
            uniqueBuyers = buyerCounts.size,
            avgCheck = if (closed.isNotEmpty()) totalRevenue / closed.size else 0.0,
            topBuyer = topBuyer,
            topSale = topSale,
            popularProduct = popularProduct,
            popularCategory = popularCategory,
            unconfirmedRevenue = unconfirmedRevenue,
            unconfirmedCount = pending.size
        )
    }

    suspend fun checkBusyModeReplies(chats: List<ChatItem>, settings: BusyModeSettings) {
        val cache = getBusyCache()
        val currentTime = System.currentTimeMillis()
        val cooldownMs = settings.cooldownMinutes * 60 * 1000L
        val enabledAt = settings.enabledAt

        
        val lastBusyInitAt = prefs.getLong("busy_init_at", 0L)

        
        
        
        if (enabledAt > 0L && lastBusyInitAt != enabledAt) {
            chats.forEach { c ->
                if (!cache.containsKey(c.id)) cache[c.id] = enabledAt
            }
            saveBusyCache(cache)
            prefs.edit().putLong("busy_init_at", enabledAt).apply()
            return
        }
        if (cache.isEmpty() && chats.isNotEmpty()) {
            chats.forEach { c -> cache[c.id] = currentTime }
            saveBusyCache(cache)
            return
        }

        
        
        
        val systemPhrases = listOf(
            
            "оплатил заказ", "paid for order", "сплатив замовлення",
            
            "подтвердил успешное", "confirmed that order", "підтвердив успішне",
            
            "написал отзыв", "has given feedback", "given feedback", "написав відгук",
            "изменил отзыв", "has edited their feedback", "edited their feedback", "змінив відгук",
            
            "вернул деньги", "refunded", "повернув гроші",
            "возврат", "refund", "повернення",
            
            "открыт повторно", "reopened", "відкрито повторно",
            
            "открыл спор", "opened dispute", "відкрив суперечку",
            "закрыл спор", "closed dispute", "закрив суперечку",
            
            "продлил срок", "extended", "продовжив термін",
            
            "статус заказа", "order status", "статус замовлення",
            "заказ отклонён", "заказ отклонен", "order rejected", "замовлення відхилено",
            "заказ закрыт", "order closed", "замовлення закрито",
            
            "the order", "by the seller", "by the buyer"
        )

        var cacheChanged = false

        for (chat in chats) {
            if (!chat.isUnread) continue

            val lastMsgLower = chat.lastMessage.lowercase(Locale.getDefault())

            
            
            
            val isNewOrder = lastMsgLower.contains("оплатил заказ") ||
                    lastMsgLower.contains("paid for order") ||
                    lastMsgLower.contains("сплатив замовлення")

            val isOtherSystem = !isNewOrder &&
                    (systemPhrases.any { lastMsgLower.contains(it) } || chat.lastMessage.startsWith("####"))
            if (isOtherSystem) continue

            
            
            
            
            val lastSelf = FunPayRepository.lastOutgoingMessages[chat.id]
            if (lastSelf != null && lastSelf != "__image__") {
                val cleanIncoming = chat.lastMessage.replace("...", "").trim().lowercase(Locale.getDefault())
                val cleanSelf = lastSelf.trim().lowercase(Locale.getDefault())
                val isEcho = when {
                    cleanIncoming.isEmpty() || cleanSelf.isEmpty() -> false
                    cleanIncoming == cleanSelf -> true
                    
                    chat.lastMessage.endsWith("...") && cleanSelf.startsWith(cleanIncoming) && cleanIncoming.length >= 5 -> true
                    
                    (cleanSelf.contains(cleanIncoming) || cleanIncoming.contains(cleanSelf)) &&
                            minOf(cleanSelf.length, cleanIncoming.length) >= 15 -> true
                    else -> false
                }
                if (isEcho) continue
            }

            
            if (isNewOrder) {
                val orderIdMatch = Regex("#([a-zA-Z0-9]+)").find(chat.lastMessage)
                val orderId = orderIdMatch?.groupValues?.get(1)
                if (orderId != null && !wasEventProcessed(chat.id, orderId, "busy_refund")) {

                    
                    
                    val orderDetails = try { getOrderDetails(orderId) } catch (_: Exception) { null }
                    val isAutoDelivery = orderDetails?.hasAutoDelivery == true

                    if (isAutoDelivery && settings.skipRefundIfAutoDelivery) {
                        
                        LogManager.addLog("✅ Заказ #$orderId с автовыдачей — возврат пропущен (занятость)")
                        if (settings.autoDeliveryMessageEnabled && settings.autoDeliveryMessage.isNotBlank()) {
                            val msg = FpPlaceholders.applyCombined(
                                settings.autoDeliveryMessage,
                                chat,
                                FpPlaceholders.OrderCtx(orderId = orderId, buyerUsername = chat.username)
                            )
                            sendWithOptionalImage(
                                chat.id,
                                msg,
                                settings.imageUri,
                                settings.imageFirst
                            )
                            cache[chat.id] = currentTime
                            cacheChanged = true
                        }
                        markEventAsProcessed(chat.id, orderId, "busy_refund")
                        continue
                    }

                    
                    if (settings.autoRefund) {
                        LogManager.addLog("🛑 Авто-отмена заказа $orderId (режим занятости)")
                        val success = refundOrder(orderId)
                        if (success && settings.autoRefundMessage && settings.message.isNotBlank()) {
                            val msg = FpPlaceholders.applyCombined(
                                settings.message,
                                chat,
                                FpPlaceholders.OrderCtx(orderId = orderId, buyerUsername = chat.username)
                            )
                            sendWithOptionalImage(chat.id, msg, settings.imageUri, settings.imageFirst)
                        }
                        markEventAsProcessed(chat.id, orderId, "busy_refund")
                        continue
                    }

                    
                    
                    markEventAsProcessed(chat.id, orderId, "busy_refund")
                    continue
                }
                
                continue
            }

            
            val lastReplyTime = cache[chat.id] ?: 0L
            if (lastReplyTime > 0L) {
                if (settings.cooldownMinutes == 0) continue
                if (currentTime - lastReplyTime < cooldownMs) continue
            }

            
            if (settings.message.isNotBlank() || settings.imageUri != null) {
                LogManager.addLog("🛑 Режим занятости: ответ для ${chat.username}")
                val formattedBusyMsg = FpPlaceholders.applyChat(settings.message, chat)
                val success = sendWithOptionalImage(chat.id, formattedBusyMsg, settings.imageUri, settings.imageFirst)
                if (success) {
                    cache[chat.id] = currentTime
                    cacheChanged = true
                    if (getReadMarkSettings().markAfterBusyReply) markChatAsRead(chat.id)
                    kotlinx.coroutines.delay(1500)
                }
            }
        }

        if (cacheChanged) saveBusyCache(cache)
    }

    private fun saveBusyCache(cache: Map<String, Long>) =
        prefs.edit().putString("busy_cache", gson.toJson(cache)).apply()

    private fun getBusyCache(): MutableMap<String, Long> {
        val json = prefs.getString("busy_cache", "{}")
        val type = object : TypeToken<MutableMap<String, Long>>() {}.type
        return try { gson.fromJson(json, type) ?: mutableMapOf() } catch (e: Exception) { mutableMapOf() }
    }

    suspend fun checkAutoResponse(cachedChats: List<ChatItem>? = null) {
        if (!getSetting("auto_response") && !getSetting("auto_review_reply")) {
            return
        }

        try {
            val chats = cachedChats ?: getChats()
            val commands = getCommands()
            val (csrf, userId) = getCsrfAndId() ?: return

            for (chat in chats) {
                val lastMessageText = chat.lastMessage.trim()
                if (lastMessageText.isEmpty()) continue

                val lowerText = lastMessageText.lowercase(Locale.getDefault())


                val lastSelf = FunPayRepository.lastOutgoingMessages[chat.id]
                var isSelfMessage = false
                if (lastSelf != null) {
                    val cleanIncoming = lastMessageText.replace("...", "").trim()
                    val cleanSelf = lastSelf.trim()

                    if (cleanIncoming.equals(cleanSelf, ignoreCase = true)) {
                        isSelfMessage = true
                    }
                }

                if (isSelfMessage) continue


                if (getSetting("auto_review_reply") &&
                    (lowerText.contains("написал отзыв к заказу") ||
                            lowerText.contains("изменил отзыв к заказу") ||
                            lowerText.contains("has given feedback") ||
                            lowerText.contains("has edited their feedback"))) {

                    val orderIdMatch = Regex("#([a-zA-Z0-9]+)").find(lastMessageText)
                    if (orderIdMatch != null) {
                        val orderId = orderIdMatch.groupValues[1]
                        val isEdited = lowerText.contains("изменил") || lowerText.contains("edited")

                        if (!isEdited) {
                            val reviewEventKey = "${lastMessageText.hashCode()}"

                            if (wasEventProcessed(chat.id, reviewEventKey, "review")) {
                                continue
                            }

                            var isNewAfterDelete = false

                            try {
                                val history = getChatHistory(chat.id)
                                val recentMessages = history.takeLast(5)

                                val deletePhrases = listOf(
                                    "удалил отзыв к заказу", "deleted their feedback to the order", "видалив відгук до замовлення"
                                )

                                for (i in recentMessages.indices.reversed()) {
                                    val msg = recentMessages[i]
                                    val msgLower = msg.text.lowercase()

                                    if (msgLower.contains(chat.lastMessage.lowercase())) {
                                        if (i > 0) {
                                            val prevMsg = recentMessages[i - 1]
                                            val prevMsgLower = prevMsg.text.lowercase()

                                            if (deletePhrases.any { prevMsgLower.contains(it) } &&
                                                prevMsgLower.contains(orderId.lowercase())) {
                                                isNewAfterDelete = true
                                            }
                                        }
                                        break
                                    }
                                }
                            } catch (e: Exception) {

                            }

                            if (!isNewAfterDelete) {
                                markEventAsProcessed(chat.id, reviewEventKey, "review")
                                continue
                            }
                        }

                        LogManager.addLog("⭐ Обнаружен отзыв, заказ $orderId")
                        handleReview(orderId, chat.username, csrf, userId)
                        kotlinx.coroutines.delay(1500)
                    }
                }


                if (getSetting("auto_response")) {

                    val systemPhrases = listOf(
                        "оплатил заказ", "paid for order", "сплатив замовлення",
                        "подтвердил успешное", "confirmed that order", "підтвердив успішне",
                        "написал отзыв", "given feedback", "написав відгук",
                        "изменил отзыв", "edited their feedback", "змінив відгук",
                        "удалил отзыв", "deleted", "видалив відгук",
                        "вернул деньги", "refunded", "повернув гроші",
                        "ответил на отзыв", "replied to", "відповів на відгук",
                        "не забудьте", "don't forget", "теперь вы можете", "you can switch"
                    )

                    if (systemPhrases.any { lowerText.contains(it) }) {
                        continue
                    }

                    val incomingRaw = lastMessageText.replace("\n", "").trim()
                    val incomingLower = incomingRaw.lowercase(Locale.getDefault())

                    val cmd = commands.find { commandObj ->
                        val triggerRaw = commandObj.trigger.trim()
                        if (commandObj.caseSensitive) {
                            if (commandObj.exactMatch) incomingRaw == triggerRaw
                            else incomingRaw == triggerRaw || incomingRaw.contains(triggerRaw)
                        } else {
                            val trigger = triggerRaw.lowercase(Locale.getDefault())
                            if (commandObj.exactMatch) incomingLower == trigger
                            else incomingLower == trigger || incomingLower.contains(trigger)
                        }
                    }

                    if (cmd != null) {
                        val now = System.currentTimeMillis()




                        val messageId: String
                        try {
                            val history = getChatHistory(chat.id)
                            val lastIncoming = history.lastOrNull { !it.isMe }
                            if (lastIncoming == null) continue

                            val triggerRaw = cmd.trigger.trim()
                            val incomingNormRaw = lastIncoming.text.trim()
                            val triggerMatches = if (cmd.caseSensitive) {
                                if (cmd.exactMatch) incomingNormRaw == triggerRaw
                                else incomingNormRaw == triggerRaw || incomingNormRaw.contains(triggerRaw)
                            } else {
                                val trigger = triggerRaw.lowercase(Locale.getDefault())
                                val incomingNorm = incomingNormRaw.lowercase(Locale.getDefault())
                                if (cmd.exactMatch) incomingNorm == trigger
                                else incomingNorm == trigger || incomingNorm.contains(trigger)
                            }

                            if (!triggerMatches) continue
                            messageId = lastIncoming.id
                        } catch (e: Exception) {
                            continue
                        }


                        if (now - lastCommandKeyCleanup > 10 * 60 * 1000L) {
                            recentCommandKeys.entries.removeAll { now - it.value > 10 * 60 * 1000L }
                            lastCommandKeyCleanup = now
                        }
                        if (recentCommandKeys.containsKey(messageId)) continue

                        
                        if (cmd.callMode) {
                            LogManager.addLog("📞 Вызов продавца по команде '${cmd.trigger}' от ${chat.username}")
                            
                            
                            
                            val resolvedAvatar = chat.avatarUrl
                                .takeIf { it.isNotBlank() && !it.endsWith("/img/layout/avatar.png") }
                            try {
                                CallNotificationManager.showCallNotification(
                                    context = context,
                                    chatId = chat.id,
                                    username = chat.username,
                                    avatarUrl = resolvedAvatar,
                                    triggerPhrase = cmd.trigger,
                                    messageText = lastMessageText,
                                    style = cmd.callStyle
                                )
                            } catch (e: Exception) {
                                LogManager.addLog("⚠️ Не удалось показать вызов: ${e.message}")
                            }
                            
                            val autoReply = cmd.callAutoReplyTextOrDefault
                            if (autoReply.isNotBlank()) {
                                val formattedAutoReply = FpPlaceholders.applyChat(autoReply, chat, incomingText = lastMessageText)
                                sendWithOptionalImage(chat.id, formattedAutoReply, cmd.imageUri, cmd.imageFirst)
                                if (getReadMarkSettings().markAfterAutoReply) markChatAsRead(chat.id)
                            }
                            recentCommandKeys[messageId] = now
                            kotlinx.coroutines.delay(1500)
                            continue
                        }

                        LogManager.addLog("🎯 Команда '${cmd.trigger}' от ${chat.username}")
                        val formattedResponse = FpPlaceholders.applyChat(cmd.response, chat, incomingText = lastMessageText)
                        val success = sendWithOptionalImage(chat.id, formattedResponse, cmd.imageUri, cmd.imageFirst)

                        if (success) {
                            recentCommandKeys[messageId] = now
                            if (getReadMarkSettings().markAfterAutoReply) markChatAsRead(chat.id)
                            kotlinx.coroutines.delay(1500)
                        }
                    }

                }
            }
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка checkAutoResponse: ${e.message}")
        }
    }

    suspend fun checkGreetings(chats: List<ChatItem>) {
        val settings = getGreetingSettings()
        if (!settings.enabled) return

        val cache = getGreetedCache()
        val currentTime = System.currentTimeMillis()
        val cooldownMs = settings.cooldownHours * 60 * 60 * 1000L
        var cacheChanged = false



        if (cache.isEmpty() && chats.isNotEmpty()) {
            LogManager.addLog("👋 Автоприветствие: первый запуск, инициализируем кэш (${chats.size} чатов)")
            chats.forEach { cache[it.id] = currentTime }
            saveGreetedCache(cache)
            return
        }

        val systemPhrases = listOf(
            "оплатил заказ", "paid for order", "сплатив замовлення",
            "подтвердил успешное выполнение", "confirmed that order", "підтвердив успішне виконання",
            "написал отзыв", "given feedback", "написав відгук",
            "изменил отзыв", "edited their feedback", "змінив відгук",
            "вернул деньги", "refunded", "повернув гроші",
            "открыт повторно", "reopened", "відкрито повторно",
            "теперь вы можете перейти", "you can switch to", "тепер ви можете перейти"
        )

        for (chat in chats) {

            if (!chat.isUnread) continue

            val lastMsgLower = chat.lastMessage.lowercase()

            val lastSelf = FunPayRepository.lastOutgoingMessages[chat.id]
            if (lastSelf != null) {
                val cleanIncoming = chat.lastMessage.replace("...", "").trim().lowercase()
                val cleanSelf = lastSelf.trim().lowercase()
                if (cleanSelf.contains(cleanIncoming) || cleanIncoming.contains(cleanSelf)) continue
            }

            if (settings.ignoreSystemMessages) {
                if (systemPhrases.any { lastMsgLower.contains(it) }) continue
                if (chat.lastMessage.startsWith("####")) continue
            }

            val lastGreetTime = cache[chat.id] ?: 0L
            if (lastGreetTime > 0L) {
                if (settings.cooldownHours == 0) continue
                if (currentTime - lastGreetTime < cooldownMs) continue
            }




            val greetingText = FpPlaceholders.applyChat(settings.text, chat)

            val lastMsgClean = chat.lastMessage.replace("...", "").trim()
            val greetClean = greetingText.trim()
            if (lastMsgClean.equals(greetClean, ignoreCase = true) ||
                greetClean.startsWith(lastMsgClean, ignoreCase = true)) {

                continue
            }

            LogManager.addLog("👋 Приветствие для ${chat.username}")

            val success = sendWithOptionalImage(chat.id, greetingText, settings.imageUri, settings.imageFirst)

            if (success) {
                cache[chat.id] = currentTime
                cacheChanged = true
                if (getReadMarkSettings().markAfterGreeting) markChatAsRead(chat.id)
                kotlinx.coroutines.delay(1500)
            }
        }

        if (cacheChanged) saveGreetedCache(cache)
    }

    private suspend fun handleReview(orderId: String, buyerName: String, csrf: String, userId: String) {
        try {
            val settings = getReviewReplySettings()
            if (!settings.enabled) return

            val response = RetrofitInstance.api.getOrder(orderId, getCookieString(), userAgent)
            updateSession(response)
            val html = readBodySilent(response)
            if (html.isEmpty()) return

            val doc = Jsoup.parse(html)

            val reviewAuthorId = doc.select(".review-item-row[data-row='review']").attr("data-author")
            val activeAccount = getActiveAccount()
            if (activeAccount != null && reviewAuthorId == activeAccount.userId) {
                LogManager.addLogDebug("⏭️ handleReview: ты покупатель, пропускаем")
                return
            }

            val ratingElement = doc.select(".rating div").first()
            if (ratingElement == null) {
                LogManager.addLog("⚠️ Не удалось узнать оценку ($orderId)")
                return
            }

            val ratingClass = ratingElement.className()
            val stars = ratingClass.filter { it.isDigit() }.toIntOrNull() ?: 5

            val reviewText = doc.select(".review-item-text").text()

            var lotName = "Товар"
            val paramItems = doc.select(".param-item")
            for (item in paramItems) {
                val headerObj = item.select("h5")
                if (headerObj.isEmpty()) continue

                val headerText = headerObj.text()
                val headerTextLower = headerText.lowercase()

                if (headerTextLower.contains("краткое описание") || headerTextLower.contains("short description")) {
                    val fullText = item.text()
                    if (fullText.startsWith(headerText, ignoreCase = true)) {
                        val temp = fullText.substring(headerText.length).trim()
                        if (temp.isNotEmpty()) lotName = temp
                    } else {
                        val temp = fullText.replace(headerText, "", ignoreCase = true).trim()
                        if (temp.isNotEmpty()) lotName = temp
                    }
                    break
                }
            }
            if (lotName == "Товар") {
                val headerName = doc.select(".order-desc div").first()?.text()
                if (!headerName.isNullOrEmpty()) lotName = headerName
            }

            if (lotName.startsWith(":") || lotName.startsWith("-")) {
                lotName = lotName.substring(1).trim()
            }
            if (lotName.length > 100) {
                lotName = lotName.take(100) + "..."
            }

            var finalReplyText: String? = null

            if (settings.useAi) {
                LogManager.addLog("🤖 Генерация ответа AI ($orderId)...")

                repeat(3) {
                    if (finalReplyText == null) {
                        finalReplyText = generateReviewReply(lotName, reviewText, stars, settings.aiLength, settings)
                        if (finalReplyText == null) delay(1000)
                    }
                }

                if (finalReplyText == null) {
                    LogManager.addLog("⚠️ AI не ответил, использую Fallback текст")
                    finalReplyText = settings.aiFallbackText
                }

                if (finalReplyText != null) {
                    
                    
                    
                    finalReplyText = FpPlaceholders.applyOrder(
                        finalReplyText!!,
                        FpPlaceholders.OrderCtx(
                            orderId = orderId,
                            lotName = lotName,
                            buyerUsername = buyerName
                        )
                    )
                }
            } else {
                if (settings.disabledStars.contains(stars)) {
                    LogManager.addLog("ℹ️ Ответ на $stars* отключен в настройках")
                    return
                }

                LogManager.addLog("📝 Подбор шаблона ($stars*) для $orderId...")
                val templateText = settings.manualTemplates[stars] ?: ""

                if (templateText.isBlank()) {
                    LogManager.addLog("ℹ️ Шаблон для $stars* пустой, пропускаю")
                    return
                }

                finalReplyText = FpPlaceholders.applyOrder(
                    templateText,
                    FpPlaceholders.OrderCtx(
                        orderId = orderId,
                        lotName = lotName,
                        buyerUsername = buyerName
                    )
                )
            }

            if (finalReplyText != null && finalReplyText!!.isNotEmpty()) {
                LogManager.addLog("📤 Отправка ответа: '$finalReplyText'")

                val replyResponse = RetrofitInstance.api.replyToReview(
                    cookie = getCookieString(),
                    userAgent = userAgent,
                    csrfToken = csrf,
                    orderId = orderId,
                    text = finalReplyText!!,
                    rating = 5,
                    authorId = userId
                )

                val jsonStr = readBodySilent(replyResponse)
                val jsonResponse = try { JSONObject(jsonStr) } catch (e: Exception) { JSONObject() }

                val isSuccess = replyResponse.isSuccessful &&
                        !jsonResponse.optBoolean("error") &&
                        jsonResponse.optInt("error") != 1

                if (isSuccess) {
                    LogManager.addLog("✅ Ответ отправлен")

                    
                    try {
                        
                        
                        
                        val orderDetails = try { getOrderDetails(orderId) } catch (_: Exception) { null }
                        val rule = pickFeedbackBonusRule(
                            stars = stars,
                            lotId = orderDetails?.lotId,
                            lotName = orderDetails?.shortDesc ?: lotName,
                            lotDescription = orderDetails?.shortDesc
                        )

                        if (rule != null && (rule.text.isNotBlank() || rule.imageUri != null)) {
                            if (rule.oncePerOrder && wasFeedbackBonusSent(orderId)) {
                                LogManager.addLogDebug("🎁 Бонус для #$orderId уже отправлялся — пропуск")
                            } else {
                                val chats = try { getChats() } catch (_: Exception) { emptyList() }
                                val targetChat = chats.firstOrNull { c ->
                                    c.username.equals(buyerName, ignoreCase = true)
                                } ?: chats.firstOrNull { c ->
                                    c.lastMessage.contains("#$orderId", ignoreCase = true)
                                }
                                if (targetChat != null) {
                                    val bonusText = FpPlaceholders.applyCombined(
                                        rule.text,
                                        targetChat,
                                        FpPlaceholders.OrderCtx(
                                            orderId = orderId,
                                            lotName = orderDetails?.shortDesc ?: lotName,
                                            buyerUsername = buyerName
                                        )
                                    )
                                    LogManager.addLog("🎁 Бонус '${rule.name}' → ${targetChat.username} (#$orderId)")
                                    kotlinx.coroutines.delay(1200)
                                    val ok = sendWithOptionalImage(
                                        targetChat.id,
                                        bonusText,
                                        rule.imageUri,
                                        rule.imageFirst
                                    )
                                    if (ok) {
                                        markFeedbackBonusSent(orderId)
                                        LogManager.addLog("✅ Бонус за отзыв отправлен")
                                        if (getReadMarkSettings().markAfterBonusMessage) markChatAsRead(targetChat.id)
                                    }
                                } else {
                                    LogManager.addLogDebug("🎁 Не нашли чат для бонуса (#$orderId)")
                                }
                            }
                        } else {
                            LogManager.addLogDebug("🎁 Ни одно правило бонуса не подошло (stars=$stars, lot='$lotName')")
                        }
                    } catch (e: Exception) {
                        LogManager.addLog("⚠️ Ошибка отправки бонуса: ${e.message}")
                    }
                } else {
                    val errorMsg = jsonResponse.optString("msg").ifEmpty {
                        jsonResponse.optString("message")
                    }.ifEmpty { jsonStr }
                    LogManager.addLog("❌ Ошибка FunPay (${replyResponse.code()}): $errorMsg")
                }
            }

        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка handleReview: ${e.message}")
        }
    }

    suspend fun generateReviewReply(lotName: String, reviewText: String, stars: Int, strength: Int, settings: ReviewReplySettings = ReviewReplySettings()): String? {
        return try {
            val lengthInstruction = when {
                strength <= 2 -> "2. Ответ должен быть ОЧЕНЬ коротким (1-2 предложения)."
                strength <= 4 -> "2. Ответ должен быть кратким, но емким (2-3 предложения)."
                strength <= 7 -> "2. Ответ должен быть развёрнутым (3-4 предложения), дружелюбным и эмоциональным."
                strength <= 9 -> "2. Ответ должен быть очень подробным (4-5 предложений), выражать искреннюю радость и благодарность."
                else -> "2. Ответ должен быть максимально развёрнутым и креативным (5-6 предложений), очень живым, эмоциональным и запоминающимся."
            }

            val lotNameInstruction = if (settings.aiIncludeLotName)
                "1. ОБЯЗАТЕЛЬНО упомяни название товара \"$lotName\" или его суть."
            else
                "1. НЕ упоминай конкретное название товара, пиши в общих словах."

            val dateLine = if (settings.aiIncludeDate) {
                val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                "   Можешь органично упомянуть дату: $today."
            } else ""

            val styleLine = if (settings.aiWritingStyle.isNotBlank())
                "СТИЛЬ: ${settings.aiWritingStyle.trim()}."
            else ""

            val customLine = if (settings.aiCustomInstruction.isNotBlank())
                "\n--- ДОПОЛНИТЕЛЬНЫЕ ИНСТРУКЦИИ ---\n${settings.aiCustomInstruction.trim()}"
            else ""

            val systemPrompt = "You are a text editing model. Follow user instructions precisely."
            val combinedUserPrompt = """
                    Ты — вежливый и дружелюбный продавец на игровой бирже FunPay.
                    Покупатель оставил отзыв с оценкой $stars из 5 на твой товар "$lotName".
                    Текст отзыва: "$reviewText"
                    ${if (styleLine.isNotBlank()) "\n$styleLine" else ""}
                    Твоя задача — написать позитивный и благодарный ответ на этот отзыв.

                    --- ПРАВИЛА ---
                    $lotNameInstruction
                    $lengthInstruction${if (dateLine.isNotBlank()) "\n$dateLine" else ""}
                    3. Используй дружелюбный тон и уместные эмодзи (например, 😊, 👍, 🎉, ✨, 💜, 🔥).
                    4. Поблагодари покупателя за отзыв и/или покупку.
                    5. НЕ используй шаблонные фразы типа "Обращайтесь ещё" или "Буду рад помочь".
                    6. Пиши живо и естественно, как настоящий человек.
                    7. НЕ используй Markdown, жирный текст или курсив.
                    8. Твой ответ — это ТОЛЬКО готовый текст. Без кавычек, заголовков или объяснений.

                    --- ПЕРЕМЕННЫЕ, КОТОРЫЕ МОЖЕШЬ ВСТАВИТЬ В ОТВЕТ ---
                            При необходимости можешь использовать плейсхолдеры — они автоматически подставятся
                    перед отправкой покупателю. НЕ обязан их использовать, но если уместно — вплетай:
                    ${'$'}username — имя покупателя
                    ${'$'}order_id — номер заказа
                    ${'$'}lot_name — название товара
                    ${'$'}date — сегодняшняя дата (DD.MM.YYYY)
                    ${'$'}time — текущее время (HH:MM)
                    ${'$'}full_date — «25 февраля 2026 года»
                    Пример уместного использования: «Спасибо за отзыв, ${'$'}username!» вместо реального имени.
                    Пример неуместного: вставлять ${'$'}order_id в приветственную часть — там он лишний.$customLine

                    ГОТОВЫЙ ТЕКСТ ОТВЕТА:
                    """.trimIndent()

            val messages = JSONArray()
            val sysMsg = JSONObject().put("role", "system").put("content", systemPrompt)
            val userMsg = JSONObject().put("role", "user").put("content", combinedUserPrompt)
            messages.put(sysMsg)
            messages.put(userMsg)

            val payload = JSONObject()
            payload.put("messages", messages)
            payload.put("modelName", "ChatGPT 4o")
            payload.put("currentPagePath", "/chatgpt-4o")

            val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())

            val response = RetrofitInstance.api.rewriteText(
                auth = "Bearer fptoolsdim",
                body = body
            )

            if (response.isSuccessful) {
                val jsonStr = response.body()?.string() ?: return null
                val json = JSONObject(jsonStr)
                json.optString("response").trim().replace("\"", "")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }




    private val dumperLastUpdateMap = mutableMapOf<String, Long>()
    private val dumperCommissionCache = mutableMapOf<String, Pair<Double, Long>>()
    private val dumperPriceCache = mutableMapOf<String, Pair<Double, Long>>()

    private val dumperLotFilterCache = mutableMapOf<String, Pair<Map<String, String>, Long>>()

    private val COMMISSION_CACHE_TTL = 5 * 60 * 1000L
    private val PRICE_CACHE_TTL      = 15 * 1000L
    private val LOT_FILTER_CACHE_TTL = 10 * 60 * 1000L

    fun saveDumperSettings(settings: DumperSettings) =
        prefs.edit().putString("dumper_settings", gson.toJson(settings)).apply()

    fun getDumperSettings(): DumperSettings {
        val json = prefs.getString("dumper_settings", null)
        return if (json != null) {
            try { gson.fromJson(json, DumperSettings::class.java) } catch (e: Exception) { DumperSettings() }
        } else DumperSettings()
    }

    suspend fun runDumperCycle() {
        val settings = getDumperSettings()
        if (!settings.enabled || settings.lots.isEmpty()) return
        if (!LicenseManager.isProActive()) return

        val currentTime = System.currentTimeMillis()

        for (lotConfig in settings.lots) {
            if (!lotConfig.enabled || lotConfig.lotId.isBlank() || lotConfig.categoryId.isBlank()) continue

            val lastUpdate = dumperLastUpdateMap[lotConfig.id] ?: 0L
            if (currentTime - lastUpdate < (lotConfig.updateInterval * 1000L)) continue

            try {
                processDumperLot(lotConfig)
                dumperLastUpdateMap[lotConfig.id] = System.currentTimeMillis()
            } catch (e: Exception) {
                LogManager.addLog("❌ XD Dumper Ошибка (Лот ${lotConfig.lotId}): ${e.message}")
            }
        }
    }

    private suspend fun getCategoryCommission(nodeId: String): Double = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dumperCommissionCache[nodeId]?.let { (coef, ts) ->
            if (now - ts < COMMISSION_CACHE_TTL) return@withContext coef
        }

        return@withContext try {
            val form = FormBody.Builder()
                .add("nodeId", nodeId)
                .add("price", "1000")
                .build()
            val req = Request.Builder()
                .url("https://funpay.com/lots/calc")
                .post(form)
                .header("Cookie", getCookieString())
                .header("X-Requested-With", "XMLHttpRequest")
                .header("User-Agent", userAgent)
                .build()

            val rawBody = repoClient.newCall(req).execute().body?.string() ?: "{}"
            val json = JSONObject(rawBody)

            if (json.optInt("error") == 1) {
                LogManager.addLog("XD Dumper: комиссия ошибка авторизации, не кэширую")
                return@withContext 1.0
            }

            val methods = json.optJSONArray("methods")
            var coef = 1.0
            if (methods != null && methods.length() > 0) {
                var minPrice = Double.MAX_VALUE
                for (i in 0 until methods.length()) {
                    val m = methods.getJSONObject(i)
                    val unit = m.optString("unit")


                    if (unit != "₽" && unit.lowercase() != "rub") continue

                    val price = m.optString("price").replace(" ", "").replace(",", ".").toDoubleOrNull()
                    if (price != null && price > 0 && price < minPrice) minPrice = price
                }
                if (minPrice != Double.MAX_VALUE) coef = minPrice / 1000.0
            }
            LogManager.addLog("XD Dumper: комиссия nodeId=$nodeId → ${"%.4f".format(coef)}")
            dumperCommissionCache[nodeId] = Pair(coef, now)
            coef
        } catch (e: Exception) {
            LogManager.addLog("XD Dumper: ошибка комиссии: ${e.message}")
            1.0
        }
    }

    private suspend fun fetchRealLotPrice(lotId: String): Double? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dumperPriceCache[lotId]?.let { (price, ts) ->
            if (now - ts < PRICE_CACHE_TTL) return@withContext price
        }

        return@withContext try {
            val req = Request.Builder()
                .url("https://funpay.com/lots/offer?id=$lotId")
                .header("Cookie", getCookieString())
                .header("User-Agent", userAgent)
                .build()
            val html = repoClient.newCall(req).execute().body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html)

            val prices = mutableListOf<Double>()
            doc.select("select[name=method] option").forEach { opt ->
                if (opt.attr("data-cy") == "rub") {
                    val factors = opt.attr("data-factors").split(",")
                    if (factors.size >= 2) {
                        val base = factors[0].toDoubleOrNull() ?: 0.0
                        val comm = factors[1].toDoubleOrNull() ?: 0.0
                        if (base > 0 && comm > 0) prices.add(base * comm)
                    }
                }
            }

            val price = prices.minOrNull() ?: return@withContext null
            dumperPriceCache[lotId] = Pair(price, now)
            price
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getLotFilterFields(lotId: String, categoryId: String): Map<String, String> {
        val now = System.currentTimeMillis()
        dumperLotFilterCache[lotId]?.let { (fields, ts) ->
            if (now - ts < LOT_FILTER_CACHE_TTL) return fields
        }
        return try {
            val req = Request.Builder()
                .url("https://funpay.com/lots/$categoryId/")
                .header("Cookie", getCookieString())
                .header("User-Agent", userAgent)
                .build()
            val html = repoClient.newCall(req).execute().body?.string() ?: return emptyMap()
            val doc = Jsoup.parse(html)


            val myItem = doc.select("a.tc-item").firstOrNull { item ->
                val href = item.attr("href")
                
                
                
                href.contains("id=$lotId") || href.contains("offer=$lotId")
            }

            if (myItem == null) {
                LogManager.addLogDebug("XD Dumper: лот $lotId не найден на странице категории — фильтрация по f-* недоступна")
                return emptyMap()
            }


            val filterFields = myItem.attributes()
                .filter { it.key.startsWith("data-f-") }
                .associate { attr ->
                    val key = attr.key.removePrefix("data-")
                    key to attr.value.trim().lowercase()
                }
                .filter { (_, v) -> v.isNotEmpty() }

            dumperLotFilterCache[lotId] = Pair(filterFields, now)
            LogManager.addLogDebug("XD Dumper: фильтры лота $lotId: $filterFields")
            filterFields
        } catch (e: Exception) {
            LogManager.addLogDebug("XD Dumper: ошибка при загрузке фильтров лота $lotId: ${e.message}")
            emptyMap()
        }
    }

    private fun isMatchingCompetitor(item: org.jsoup.nodes.Element, lotFilterFields: Map<String, String>, matchAllMethods: Boolean = true): Boolean {
        if (lotFilterFields.isEmpty()) return true

        val itemQuantityRaw = item.attr("data-f-quantity").trim().lowercase()
        val isOtherQuantity = itemQuantityRaw == "другое количество"

        for ((key, lotValue) in lotFilterFields) {
            if (key == "f-quantity2") continue

            if (matchAllMethods && (key == "f-method" || key == "f-type" || key.contains("способ"))) continue

            val itemValue = if (key == "f-quantity" && isOtherQuantity) {
                item.attr("data-f-quantity2").trim().lowercase()
            } else {
                item.attr("data-$key").trim().lowercase()
            }

            if (itemValue.isEmpty()) continue

            val lotNum = lotValue.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull()
            val itemNum = itemValue.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull()

            if (lotNum != null && itemNum != null) {
                val tolerance = maxOf(lotNum * 0.01, 1.0)
                if (kotlin.math.abs(itemNum - lotNum) > tolerance) return false
                continue
            }

            if (itemValue != lotValue) return false
        }
        return true
    }

    private suspend fun processDumperLot(config: DumperLotConfig) = withContext(Dispatchers.IO) {
        val myLots = getMyLots()
        val myCurrentLot = myLots.find { it.id == config.lotId }

        if (myCurrentLot == null || !myCurrentLot.isActive) {
            LogManager.addLogDebug("XD Dumper: Лот ${config.lotId} не найден или неактивен.")
            return@withContext
        }

        val commission = getCategoryCommission(config.categoryId)


        val activeProfile = getActiveAccount() ?: return@withContext
        val keywords = config.keywords.split("|").map { it.trim().lowercase() }.filter { it.isNotEmpty() }


        val lotFilterFields = getLotFilterFields(config.lotId, config.categoryId)

        val maxAttempts = if (config.aggressiveMode) 3 else 1

        for (attempt in 1..maxAttempts) {

            val req = Request.Builder()
                .url("https://funpay.com/lots/${config.categoryId}/")
                .header("Cookie", getCookieString())
                .header("User-Agent", userAgent)
                .build()
            val html = repoClient.newCall(req).execute().body?.string() ?: return@withContext
            val doc = Jsoup.parse(html)

            val competitors = mutableListOf<Double>()
            var position = 0


            val myLotItem = doc.select("a.tc-item").firstOrNull { it.attr("href").contains("id=${config.lotId}") }
            val currentPriceWithComm = myLotItem?.select(".tc-price")?.attr("data-s")?.toDoubleOrNull()
                ?: (myCurrentLot.price ?: 0.0) * commission

            doc.select("a.tc-item").forEach { item ->
                val sellerName = item.select(".media-user-name").text().trim()
                if (sellerName == activeProfile.username) return@forEach

                if (!isMatchingCompetitor(item, lotFilterFields, config.matchAllMethods)) return@forEach


                if (keywords.isNotEmpty()) {
                    val desc = item.select(".tc-desc-text").text().trim().lowercase()

                    val descForKeyword = if (config.matchAllMethods && desc.contains(",")) {
                        desc.substringBeforeLast(",").trim()
                    } else desc
                    if (keywords.none { descForKeyword.contains(it) }) {
                        LogManager.addLogDebug("XD Dumper: лот ${item.attr("href").substringAfter("id=").substringBefore("&")} отфильтрован keywords. desc='$desc' keywords=$keywords")
                        return@forEach
                    }
                }

                val starsCount = item.select(".rating-stars i.fas").size
                if (starsCount == 0 && config.ignoreZeroRating) return@forEach
                if (starsCount < config.ratingMin) return@forEach


                position++
                if (position > config.positionMax) return@forEach

                val competitorLotId = item.attr("href").substringAfter("id=").substringBefore("&")

                val finalPrice: Double = if (config.fastPriceCheck) {
                    fetchRealLotPrice(competitorLotId) ?: 9999999.0
                } else {
                    item.select(".tc-price").attr("data-s").toDoubleOrNull() ?: 9999999.0
                }

                if (finalPrice in config.priceMin..(config.priceMax + config.priceStep)) {
                    competitors.add(finalPrice)
                }
            }

            if (competitors.isEmpty()) {
                LogManager.addLogDebug("XD Dumper: Лот ${config.lotId} — конкурентов не найдено.")
                return@withContext
            }

            val minCompetitorPrice = competitors.minOrNull() ?: return@withContext


            var targetPrice = minCompetitorPrice - config.priceStep
            if (config.priceDivider > 1.0) {
                targetPrice -= (targetPrice % config.priceDivider)
            }
            targetPrice = targetPrice.coerceIn(config.priceMin, config.priceMax)
            if (targetPrice < 1.0) targetPrice = 1.0

            when {
                targetPrice < currentPriceWithComm - 0.01 -> {

                    updateLotPrice(config.lotId, targetPrice, commission)
                    LogManager.addLog("📈 XD Dumper: Лот ${config.lotId} поднят → ${"%.2f".format(targetPrice)}₽ | Мин. конкурент: ${"%.2f".format(minCompetitorPrice)}₽ | Текущая: ${"%.2f".format(currentPriceWithComm)}₽")

                    if (config.aggressiveMode && attempt < maxAttempts) {
                        delay(2000)
                        continue
                    }
                }
                targetPrice > currentPriceWithComm + 0.01 && config.autoRaise -> {

                    val diff = targetPrice - currentPriceWithComm
                    val percent = if (currentPriceWithComm > 0) (diff / currentPriceWithComm) * 100 else 100.0

                    if (targetPrice < minCompetitorPrice && diff <= 100.0 && percent <= 10.0) {
                        updateLotPrice(config.lotId, targetPrice, commission)
                        LogManager.addLog("📈 XD Dumper: Лот ${config.lotId} поднят → ${"%.2f".format(targetPrice)}₽")
                    } else {
                        LogManager.addLogDebug("XD Dumper: Лот ${config.lotId} — разрыв цен слишком большой, держим. (diff=${"%.2f".format(diff)}₽ / ${"%.1f".format(percent)}%)")
                    }
                }
                else -> {
                    LogManager.addLogDebug("XD Dumper: Лот ${config.lotId} — цена актуальна (${"%.2f".format(currentPriceWithComm)}₽).")
                }
            }
            break
        }
    }

    private suspend fun updateLotPrice(lotId: String, targetPriceForBuyer: Double, commission: Double) {

        var priceWithoutComm = targetPriceForBuyer / commission
        if (priceWithoutComm < 1.0) priceWithoutComm = 1.0

        val fieldsData = getLotFields(lotId)
        val allFields = fieldsData.fields.mapValues { it.value.value }.toMutableMap()
        allFields["price"] = String.format(Locale.US, "%.2f", priceWithoutComm)

        saveLot(lotId, allFields, fieldsData.csrfToken, fieldsData.activeCookies)
    }

    fun getAutoTicketSettings(): AutoTicketSettings {
        val json = prefs.getString("auto_ticket_settings", null) ?: return AutoTicketSettings()
        return try { gson.fromJson(json, AutoTicketSettings::class.java) } catch (e: Exception) { AutoTicketSettings() }
    }

    fun saveAutoTicketSettings(settings: AutoTicketSettings) =
        prefs.edit().putString("auto_ticket_settings", gson.toJson(settings)).apply()

    suspend fun getUnconfirmedOrders(ageHours: Int, alreadySent: Set<String>): List<String> = withContext(Dispatchers.IO) {
        try {
            val result = mutableListOf<String>()
            val req = Request.Builder()
                .url("https://funpay.com/orders/trade?state=paid")
                .header("Cookie", getCookieString())
                .header("User-Agent", userAgent)
                .build()
            val html = repoClient.newCall(req).execute().body?.string() ?: run {
                LogManager.addLog("❌ AutoTicket: пустой ответ от страницы заказов")
                return@withContext emptyList()
            }
            LogManager.addLogDebug("AutoTicket: страница заказов загружена (${html.length} байт)")
            val doc = Jsoup.parse(html)
            doc.select("a.tc-item, .orders-table a[href*='/orders/']").forEach { item ->
                val href = item.attr("href")
                val orderId = Regex("/orders/([A-Za-z0-9]+)").find(href)?.groupValues?.get(1)
                    ?: item.select(".tc-id, .order-id").text().replace("#", "").trim()
                if (orderId.isNotBlank() && orderId !in alreadySent) result.add(orderId)
            }
            if (result.isEmpty()) {
                LogManager.addLogDebug("AutoTicket: основной парсер не нашёл заказов, пробуем резервный")
                
                Regex("/orders/([A-Z0-9]{8})/").findAll(html).forEach { m ->
                    val id = m.groupValues[1]
                    if (id !in alreadySent && id !in result) result.add(id)
                }
            }
            LogManager.addLog("🎫 AutoTicket: найдено ${result.size} неподтверждённых заказов")
            result
        } catch (e: Exception) {
            LogManager.addLog("❌ AutoTicket: ошибка получения заказов: ${e.message}")
            emptyList()
        }
    }

    suspend fun sendAutoSupportTickets(): AutoTicketResult = withContext(Dispatchers.IO) {
        val settings = getAutoTicketSettings()
        val alreadySent = settings.sentOrderIds.toSet()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val ticketsToday = if (settings.todayDate == today) settings.ticketsToday else 0
        val remaining = settings.dailyLimit - ticketsToday
        if (remaining <= 0) {
            val msg = "⛔ AutoTicket: дневной лимит (${settings.dailyLimit}) исчерпан"
            LogManager.addLog(msg)
            return@withContext AutoTicketResult(0, 0, msg)
        }
        val orderIds = getUnconfirmedOrders(settings.orderAgeHours, alreadySent)
        if (orderIds.isEmpty()) {
            LogManager.addLog("ℹ️ AutoTicket: нет заказов для отправки в ТП")
            return@withContext AutoTicketResult(0, 0, null)
        }
        val support = FunPaySupport(this@FunPayRepository)
        val initOk = try { support.init() } catch (e: Exception) { false }
        if (!initOk) {
            val msg = "❌ AutoTicket: не удалось подключиться к support.funpay.com"
            LogManager.addLog(msg)
            return@withContext AutoTicketResult(0, 0, msg)
        }
        val username = getActiveAccount()?.username ?: ""
        val chunks = orderIds.chunked(settings.maxOrdersPerTicket)
        var ticketsCreated = 0
        val sentIds = mutableListOf<String>()
        var lastError: String? = null
        for ((index, chunk) in chunks.withIndex()) {
            if (ticketsCreated >= remaining) {
                LogManager.addLog("⛔ AutoTicket: достигнут дневной лимит ($remaining тикетов)")
                break
            }
            if (index > 0) delay(2500)
            val idsStr = chunk.joinToString(", ")
            val message = "Здравствуйте! Прошу подтвердить заказы, ожидающие подтверждения: $idsStr. С уважением, $username!"
            val fieldValues = mapOf(
                "ticket[fields][1]" to username,
                "ticket[fields][2]" to idsStr,
                "ticket[fields][3]" to "2",
                "ticket[fields][5]" to "201"
            )
            try {
                val ticketId = support.createTicket("1", fieldValues, message)
                LogManager.addLog("✅ AutoTicket: тикет ${ticketId ?: "(без ID)"} создан для ${chunk.size} заказов ($idsStr)")
                sentIds.addAll(chunk)
                ticketsCreated++
            } catch (e: Exception) {
                val errMsg = e.message ?: "неизвестная ошибка"
                when {
                    errMsg.contains("лимит", ignoreCase = true) ||
                            errMsg.contains("1 запрос", ignoreCase = true) ||
                            errMsg.contains("limit", ignoreCase = true) -> {
                        LogManager.addLog("⛔ AutoTicket: FunPay вернул лимит — «$errMsg»")
                        LogManager.addLog("ℹ️ AutoTicket: FunPay разрешает только 1 тикет на подтверждение заказа в день")
                        lastError = errMsg
                        break
                    }
                    errMsg.contains("авторизац", ignoreCase = true) ||
                            errMsg.contains("401") || errMsg.contains("403") -> {
                        LogManager.addLog("❌ AutoTicket: ошибка авторизации в ТП — «$errMsg»")
                        lastError = errMsg
                        break
                    }
                    else -> {
                        LogManager.addLog("❌ AutoTicket: ошибка создания тикета — «$errMsg»")
                        lastError = errMsg
                    }
                }
            }
        }
        
        saveAutoTicketSettings(settings.copy(
            sentOrderIds = (alreadySent + sentIds).toList(),
            ticketsToday = ticketsToday + ticketsCreated,
            todayDate = today,
            lastRunAt = System.currentTimeMillis()
        ))
        AutoTicketResult(ticketsCreated, sentIds.size, lastError)
    }

    suspend fun checkAutoTicketCycle() {
        val settings = getAutoTicketSettings()
        if (!settings.enabled) return
        
        val intervalMs = maxOf(settings.autoIntervalHours, 1) * 3600_000L
        if (System.currentTimeMillis() - settings.lastRunAt < intervalMs) return
        LogManager.addLog("🤖 AutoTicket: запуск авто-цикла")
        val result = sendAutoSupportTickets()
        if (result.ticketsCreated > 0) {
            LogManager.addLog("✅ AutoTicket авто-цикл: ${result.ticketsCreated} тикетов, ${result.ordersProcessed} заказов")
        }
    }

    fun getOrderReminderSettings(): OrderReminderSettings {
        val json = prefs.getString("order_reminder_settings", null) ?: return OrderReminderSettings()
        return try { gson.fromJson(json, OrderReminderSettings::class.java) } catch (e: Exception) { OrderReminderSettings() }
    }

    fun saveOrderReminderSettings(settings: OrderReminderSettings) =
        prefs.edit().putString("order_reminder_settings", gson.toJson(settings)).apply()

    suspend fun translateLotDescriptionRuToEn(ruText: String): String? {
        if (ruText.isBlank()) return ruText
        return try {
            FpTranslate.translateRuToEn(ruText)
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка перевода: ${e.message}")
            null
        }
    }

    fun getPendingReminders(): List<PendingOrderReminder> {
        val json = prefs.getString("pending_order_reminders", "[]") ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<PendingOrderReminder>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun savePendingReminders(reminders: List<PendingOrderReminder>) =
        prefs.edit().putString("pending_order_reminders", gson.toJson(reminders)).apply()

    suspend fun checkOrderReminders(chats: List<ChatItem>) {
        val settings = getOrderReminderSettings()
        if (!settings.enabled) return
        val currentTime = System.currentTimeMillis()
        val newOrderPhrases = listOf("оплатил заказ", "paid for order", "сплатив замовлення")
        val pendingReminders = getPendingReminders().toMutableList()
        val existingOrderIds = pendingReminders.map { it.orderId }.toSet()
        for (chat in chats) {
            val lastMsgLower = chat.lastMessage.lowercase()
            if (!newOrderPhrases.any { lastMsgLower.contains(it) }) continue
            val orderId = Regex("#([A-Za-z0-9]+)").find(chat.lastMessage)?.groupValues?.get(1) ?: continue
            if (orderId in existingOrderIds) continue
            if (wasEventProcessed(chat.id, orderId, "reminder_scheduled")) continue
            val remindAt = currentTime + settings.delayHours * 3600_000L
            pendingReminders.add(PendingOrderReminder(
                orderId = orderId, chatId = chat.id, buyerName = chat.username,
                placedAt = currentTime, remindAt = remindAt
            ))
            markEventAsProcessed(chat.id, orderId, "reminder_scheduled")
            LogManager.addLogDebug("⏰ Reminder: запланировано для #$orderId (${chat.username}) через ${settings.delayHours}ч")
        }
        savePendingReminders(pendingReminders)
        val toRemove = mutableListOf<PendingOrderReminder>()
        for (reminder in pendingReminders) {
            if (currentTime < reminder.remindAt) continue
            if (wasEventProcessed(reminder.chatId, reminder.orderId, "confirm")) {
                LogManager.addLogDebug("⏭️ Reminder: #${reminder.orderId} уже подтверждён, пропускаем")
                toRemove.add(reminder)
                continue
            }
            if (wasEventProcessed(reminder.chatId, reminder.orderId, "reminder_sent")) {
                toRemove.add(reminder)
                continue
            }
            val finalText = settings.message
                .replace("\$username", reminder.buyerName)
                .replace("\$order_id", reminder.orderId)
            LogManager.addLog("⏰ Reminder: отправляем напоминание ${reminder.buyerName} (#${reminder.orderId})")
            val success = sendMessage(reminder.chatId, finalText)
            if (success) {
                markEventAsProcessed(reminder.chatId, reminder.orderId, "reminder_sent")
                LogManager.addLog("✅ Reminder: напоминание отправлено (#${reminder.orderId})")
                if (getReadMarkSettings().markAfterOrderReminder) markChatAsRead(reminder.chatId)
                toRemove.add(reminder)
            } else {
                LogManager.addLog("❌ Reminder: не удалось отправить напоминание (#${reminder.orderId})")
            }
            delay(1200)
        }
        if (toRemove.isNotEmpty()) {
            savePendingReminders(pendingReminders.filterNot { it in toRemove })
        }
    }
}

data class UserProfile(
    val id: String,
    val username: String,
    val avatarUrl: String,
    val isOnline: Boolean,
    val totalBalance: String,
    val activeSales: Int,
    val activePurchases: Int,
    val rating: Double,
    val reviewCount: Int,
    val registeredDate: String
)

suspend fun FunPayRepository.getSelfProfile(): UserProfile? {
    return withContext(Dispatchers.IO) {
        try {
            val (csrf, userId) = getCsrfAndId() ?: return@withContext null

            val mainResponse = RetrofitInstance.api.getMainPage(getGoldenKey()?.let { "golden_key=$it; PHPSESSID=${getPhpSessionId()}" } ?: "", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
            val mainHtml = mainResponse.body()?.string() ?: ""
            val mainDoc = Jsoup.parse(mainHtml)

            val balanceText = mainDoc.select(".badge-balance").text()
            val activeSales = mainDoc.select(".badge-trade").text().toIntOrNull() ?: 0
            val activePurchases = mainDoc.select(".badge-orders").text().toIntOrNull() ?: 0

            val profileResponse = RetrofitInstance.api.getUserProfile(userId, getGoldenKey()?.let { "golden_key=$it; PHPSESSID=${getPhpSessionId()}" } ?: "", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
            val profileHtml = profileResponse.body()?.string() ?: ""
            val profileDoc = Jsoup.parse(profileHtml)

            val username = profileDoc.select(".user-link-dropdown .user-link-name").first()?.text()?.trim()
                ?: profileDoc.select("div.media-user-name").first()?.text()
                    ?.replace("Online", "")?.replace("Онлайн", "")?.trim()
                ?: "Unknown"

            val statusText = profileDoc.select(".media-user-status").text()
            val isOnline = statusText.lowercase().contains("онлайн") || statusText.lowercase().contains("online")

            var avatarUrl = "https://funpay.com/img/layout/avatar.png"
            val avatarStyle = profileDoc.select(".avatar-photo, .profile-photo").attr("style")

            if (avatarStyle.contains("url(")) {
                avatarUrl = avatarStyle
                    .substringAfter("url(")
                    .substringBefore(")")
                    .replace("\"", "")
                    .replace("'", "")
                if (avatarUrl.startsWith("/")) avatarUrl = "https://funpay.com$avatarUrl"
            }

            val rating = profileDoc.select(".rating-value .big").first()
                ?.text()?.toDoubleOrNull() ?: 0.0

            val reviewsCount = profileDoc.select(".rating-full-count a").first()
                ?.text()
                ?.filter { it.isDigit() }
                ?.toIntOrNull() ?: 0

            val regDate = profileDoc.select(".text-nowrap").lastOrNull()?.text() ?: ""

            UserProfile(
                id = userId,
                username = username,
                avatarUrl = avatarUrl,
                isOnline = isOnline,
                totalBalance = balanceText,
                activeSales = activeSales,
                activePurchases = activePurchases,
                rating = rating,
                reviewCount = reviewsCount,
                registeredDate = regDate
            )
        } catch (e: Exception) {
            null
        }
    }
}

object CallNotificationManager {
    private const val CHANNEL_ID = "fp_call_channel_v1"
    private const val CHANNEL_NAME = "Вызов продавца"

    private fun ensureChannel(context: android.content.Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return

        val soundUri = try {
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
        } catch (_: Exception) {
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        }
        val audioAttrs = android.media.AudioAttributes.Builder()
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build()

        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Срочные вызовы продавца через команды в чате"
            setSound(soundUri, audioAttrs)
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        mgr.createNotificationChannel(channel)
    }

    fun showCallNotification(
        context: android.content.Context,
        chatId: String,
        username: String,
        avatarUrl: String?,
        triggerPhrase: String,
        messageText: String,
        style: String = "notification"
    ) {
        ensureChannel(context)

        val notifId = ("call_$chatId").hashCode()

        
        val openIntent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("chat_id", chatId)
            putExtra("chat_username", username)
            putExtra("from_call_notification", true)
        }
        val openPending = android.app.PendingIntent.getActivity(
            context, notifId, openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        )

        
        val dismissIntent = android.content.Intent(context, CallDismissReceiver::class.java).apply {
            putExtra("call_notification_id", notifId)
        }
        val dismissPending = android.app.PendingIntent.getBroadcast(
            context, notifId + 1, dismissIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        )

        
        val isFullscreen = style == "fullscreen"
        val fullscreenIntent = android.content.Intent(context, IncomingCallActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    android.content.Intent.FLAG_ACTIVITY_NO_HISTORY
            putExtra("chat_id", chatId)
            putExtra("chat_username", username)
            putExtra("trigger_phrase", triggerPhrase)
            putExtra("message_text", messageText)
            putExtra("avatar_url", avatarUrl)
            putExtra("notification_id", notifId)
        }
        val fullscreenPending = android.app.PendingIntent.getActivity(
            context, notifId + 2, fullscreenIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        )

        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }

        val title = "📞 Вызов от $username"
        val subtitle = "Команда «$triggerPhrase». Сообщение: $messageText"

        builder
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(android.app.Notification.BigTextStyle().bigText(subtitle))
            .setContentIntent(if (isFullscreen) fullscreenPending else openPending)
            .setAutoCancel(true)
            .setOngoing(isFullscreen)
            .addAction(
                android.R.drawable.sym_action_call,
                "Ответить",
                openPending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Отклонить",
                dismissPending
            )

        if (isFullscreen) {
            
            builder.setFullScreenIntent(fullscreenPending, true)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(android.app.Notification.CATEGORY_CALL)
            builder.setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.setPriority(android.app.Notification.PRIORITY_MAX)
            @Suppress("DEPRECATION")
            builder.setDefaults(android.app.Notification.DEFAULT_ALL)
        }

        try {
            context.getSystemService(android.app.NotificationManager::class.java)
                ?.notify(notifId, builder.build())
        } catch (_: Exception) {}
    }
}

class CallDismissReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
        val id = intent?.getIntExtra("call_notification_id", -1) ?: -1
        if (id == -1 || context == null) return
        try {
            context.getSystemService(android.app.NotificationManager::class.java)?.cancel(id)
        } catch (_: Exception) {}
    }
}
class IncomingCallActivity : androidx.activity.ComponentActivity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        val chatId = intent.getStringExtra("chat_id") ?: ""
        val username = intent.getStringExtra("chat_username") ?: "Покупатель"
        val triggerPhrase = intent.getStringExtra("trigger_phrase") ?: ""
        val messageText = intent.getStringExtra("message_text") ?: ""
        val avatarUrl = intent.getStringExtra("avatar_url")
        val notifId = intent.getIntExtra("notification_id", -1)

        
        val ringtone: android.media.Ringtone? = try {
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            android.media.RingtoneManager.getRingtone(applicationContext, uri)?.also { it.play() }
        } catch (_: Exception) { null }

        
        val vibrator = try {
            getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        } catch (_: Exception) { null }
        try {
            val pattern = longArrayOf(0, 800, 800, 800, 800, 800, 800)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}

        fun stopSignaling() {
            try { ringtone?.stop() } catch (_: Exception) {}
            try { vibrator?.cancel() } catch (_: Exception) {}
        }

        
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                IncomingCallScreen(
                    username = username,
                    avatarUrl = avatarUrl,
                    triggerPhrase = triggerPhrase,
                    messageText = messageText,
                    onAccept = {
                        stopSignaling()
                        try {
                            if (notifId != -1) {
                                getSystemService(android.app.NotificationManager::class.java)?.cancel(notifId)
                            }
                        } catch (_: Exception) {}
                        val openIntent = android.content.Intent(this, MainActivity::class.java).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("chat_id", chatId)
                            putExtra("chat_username", username)
                            putExtra("from_call_notification", true)
                        }
                        startActivity(openIntent)
                        finish()
                    },
                    onReject = {
                        stopSignaling()
                        try {
                            if (notifId != -1) {
                                getSystemService(android.app.NotificationManager::class.java)?.cancel(notifId)
                            }
                        } catch (_: Exception) {}
                        finish()
                    }
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun IncomingCallScreen(
    username: String,
    avatarUrl: String?,
    triggerPhrase: String,
    messageText: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F1419)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Вызов продавца",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Команда «$triggerPhrase»",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        username.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                username,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (messageText.isNotBlank()) {
                Text(
                    "«${messageText.take(120)}${if (messageText.length > 120) "…" else ""}»",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            
            Spacer(modifier = Modifier.weight(1f))

            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onReject,
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFFFF3B30), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Сбросить",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Сбросить",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }

                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFF34C759), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Ответить",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Ответить",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }
            }

            
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

data class PaymentMethod(
    val id: String,          
    val title: String,       
    val priceStr: String,    
    val priceValue: Double,  
    val unit: String         
)

data class LotPreview(
    val offerId: String,
    val title: String,
    val shortDesc: String,
    val priceText: String,
    val priceGuard: String,
    val csrfToken: String,
    val type: String,
    val sellerId: String,
    val sellerName: String,
    val sellerAvatar: String,
    val params: List<Pair<String, String>>,
    val paymentMethods: List<PaymentMethod>,
    val isAvailable: Boolean,
    val unavailableReason: String? = null,
    val balanceRub: Double = 0.0,
    val balanceUsd: Double = 0.0
)

data class DraftOrder(
    val csrfToken: String,
    val type: String,
    val method: String,
    val gate: String,
    val offerId: String,
    val priceGuard: String,
    val amount: String,
    val player: String,
    val deductedFromBalance: String,
    val leftToPay: String,
    val title: String
)

sealed class OrderNewResult {
    data class Redirect(val orderUrl: String, val orderId: String) : OrderNewResult()
    data class Error(val message: String) : OrderNewResult()
}

suspend fun FunPayRepository.fetchLotPreview(offerId: String): LotPreview? =
    withContext(Dispatchers.IO) {
        try {
            val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
            val resp = RetrofitInstance.api.getLotPage(offerId, getCookieString(), ua)
            val html = resp.body()?.string().orEmpty()
            if (html.isBlank()) return@withContext null

            val doc = Jsoup.parse(html)
            val form = doc.select("form[action\$=orders/new]").first()

            val csrf = form?.select("input[name=csrf_token]")?.attr("value").orEmpty()
            val pg = form?.select("input[name=price_guard]")?.attr("value").orEmpty()
            val type = form?.select("input[name=type]")?.attr("value").orEmpty().ifEmpty { "lot" }

            val title = doc.select(".page-header h1").first()?.text()?.trim().orEmpty()
            val shortDesc = doc.select(".param-list .param-item")
                .firstOrNull { it.select("h5").text().contains("Краткое описание") }
                ?.select("div")?.last()?.text()?.trim().orEmpty()

            val priceText = doc.select(".payment-value").firstOrNull()?.text()?.trim().orEmpty()

            val params = doc.select(".param-list .param-item").mapNotNull { item ->
                val k = item.select("h5").first()?.text()?.trim() ?: return@mapNotNull null
                val v = item.select("div.text-bold, > div:not(h5)").first()?.text()?.trim()
                    ?: return@mapNotNull null

                val lowerK = k.lowercase()
                if (lowerK.contains("с вашего баланса") ||
                    lowerK.contains("останется оплатить") ||
                    lowerK.contains("с баланса спишется") ||
                    lowerK.contains("deducted from your balance") ||
                    lowerK.contains("left to pay")
                ) {
                    return@mapNotNull null
                }

                if (k.isEmpty() || v.isEmpty() || v == k) null else k to v
            }

            val selectEl = form?.select("select[name=method]")?.first()
            val balanceRub = selectEl?.attr("data-balance-rub")?.toDoubleOrNull() ?: 0.0
            val balanceUsd = selectEl?.attr("data-balance-usd")?.toDoubleOrNull() ?: 0.0

            val methods = form?.select("select[name=method] option")
                ?.mapNotNull { opt ->
                    val mid = opt.attr("value")
                    if (mid.isEmpty() || mid == "0") return@mapNotNull null

                    val mUnit = opt.attr("data-unit")
                    val factorsStr = opt.attr("data-factors")
                    val factors = factorsStr.split(",")
                    val basePrice  = factors.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                    val multiplier = factors.getOrNull(1)?.toDoubleOrNull() ?: 1.0
                    val mPriceValue = if (basePrice > 0.0) basePrice * multiplier else 0.0

                    val contentHtml = opt.attr("data-content")
                    val cDoc = if (contentHtml.isNotEmpty()) Jsoup.parseBodyFragment(contentHtml) else null
                    val mTitle = cDoc?.select(".payment-title")?.text()?.trim() ?: opt.text().trim()
                    val mPriceStr = cDoc?.select(".payment-value")?.text()?.trim()
                        ?: if (mPriceValue > 0.0) String.format(java.util.Locale.US, "%.2f %s", mPriceValue, mUnit) else ""

                    if (mPriceValue <= 0.0) return@mapNotNull null

                    PaymentMethod(
                        id = mid,
                        title = mTitle.ifEmpty { "Метод #$mid" },
                        priceStr = mPriceStr.ifEmpty { String.format(java.util.Locale.US, "%.2f %s", mPriceValue, mUnit) },
                        priceValue = mPriceValue,
                        unit = mUnit
                    )
                }.orEmpty()

            val chatHeader = doc.select(".chat .media-user-name a, .param-item-chat .media-user-name a, .offer-chat .media-user-name a").first()
            var sellerName = chatHeader?.text()?.trim().orEmpty()
            var sellerHref = chatHeader?.attr("href").orEmpty()

            if (sellerName.isEmpty()) {
                val altBlock = doc.select(".offer-seller .media-user-name a, .seller-block .media-user-name a, .page-content-col .media-user-name a, aside .media-user-name a").first()
                sellerName = altBlock?.text()?.trim().orEmpty()
                sellerHref = altBlock?.attr("href").orEmpty()
            }

            val sellerId = Regex("/users/(\\d+)").find(sellerHref)?.groupValues?.getOrNull(1).orEmpty()

            var sellerAvatar = doc.select(".chat .media-left img, .offer-chat .media-left img").attr("src")
            if (sellerAvatar.isEmpty()) {
                sellerAvatar = doc.select(".offer-seller .media-left img, .seller-block img, aside .media-left img").attr("src")
            }
            if (sellerAvatar.startsWith("/")) sellerAvatar = "https://funpay.com$sellerAvatar"
            if (sellerAvatar.isBlank()) sellerAvatar = "https://funpay.com/img/layout/avatar.png"

            val isAvailable = form != null && pg.isNotEmpty() && csrf.isNotEmpty() && methods.isNotEmpty()
            val unavailable = when {
                form == null -> "Форма покупки недоступна (возможно, продавец в оффлайне)"
                methods.isEmpty() -> "Нет доступных способов оплаты"
                else -> null
            }

            LotPreview(
                offerId = offerId, title = title.ifEmpty { "Лот #$offerId" }, shortDesc = shortDesc,
                priceText = priceText, priceGuard = pg, csrfToken = csrf, type = type,
                sellerId = sellerId, sellerName = sellerName, sellerAvatar = sellerAvatar,
                params = params, paymentMethods = methods, isAvailable = isAvailable,
                unavailableReason = unavailable, balanceRub = balanceRub, balanceUsd = balanceUsd
            )
        } catch (e: Exception) {
            LogManager.addLogDebug("fetchLotPreview failed: ${e.message}")
            null
        }
    }

suspend fun FunPayRepository.checkoutDraftOrder(
    lot: LotPreview,
    methodId: String,
    amount: Int,
    sum: Double
): Result<DraftOrder> = withContext(Dispatchers.IO) {
    try {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
        val sumStr = String.format(Locale.US, "%.2f", sum)

        
        val formAjax = FormBody.Builder()
            .add("csrf_token", lot.csrfToken)
            .add("type", lot.type)
            .add("preview", "1")
            .add("offer_id", lot.offerId)
            .add("price_guard", lot.priceGuard)
            .add("username", "")
            .add("method", methodId)
            .add("amount", amount.toString())
            .add("sum", sumStr)
            .build()

        val reqAjax = Request.Builder()
            .url("https://funpay.com/orders/new")
            .post(formAjax)
            .header("Cookie", getCookieString())
            .header("User-Agent", ua)
            .header("X-Requested-With", "XMLHttpRequest") 
            .build()

        val respAjax = repoClient.newCall(reqAjax).execute()
        val rawAjax = respAjax.body?.string().orEmpty()

        val json = try { JSONObject(rawAjax) } catch (e: Exception) { JSONObject() }
        if (json.optBoolean("error", false)) {
            val errorMsg = json.optString("msg", "Ошибка валидации цены FunPay")
            return@withContext Result.failure(Exception(errorMsg))
        }

        
        val formFull = FormBody.Builder()
            .add("csrf_token", lot.csrfToken)
            .add("type", lot.type)
            .add("preview", "1")
            .add("offer_id", lot.offerId)
            .add("price_guard", lot.priceGuard)
            .add("username", "")
            .add("method", methodId)
            .add("amount", amount.toString())
            .add("sum", sumStr)
            .build()

        val reqFull = Request.Builder()
            .url("https://funpay.com/orders/new")
            .post(formFull)
            .header("Cookie", getCookieString())
            .header("User-Agent", ua)
            .header("Referer", "https://funpay.com/lots/offer?id=${lot.offerId}")
            .build()

        val respFull = repoClient.newCall(reqFull).execute()
        updateSession(respFull)
        val html = respFull.body?.string().orEmpty()

        val doc = Jsoup.parse(html)
        val paymentForm = doc.select("form.js-form-payment").first()

        if (paymentForm == null) {
            val errorAlert = doc.select(".alert-danger, .error-message, .help-block").text()
            return@withContext Result.failure(Exception(errorAlert.ifBlank { "Не удалось загрузить страницу подтверждения." }))
        }

        val csrf = paymentForm.select("input[name=csrf_token]").attr("value")
        val type = paymentForm.select("input[name=type]").attr("value")
        val method = paymentForm.select("input[name=method]").attr("value")
        val gate = paymentForm.select("input[name=gate]").attr("value")
        val offer = paymentForm.select("input[name=offer_id]").attr("value")
        val pg = paymentForm.select("input[name=price_guard]").attr("value")
        val amt = paymentForm.select("input[name=amount]").attr("value")
        val player = paymentForm.select("input[name=player]").attr("value")

        val deducted = doc.select(".js-balance-pay").first()?.text() ?: "0.00"
        val leftToPay = doc.select(".js-payment-sum").first()?.text() ?: "0.00"
        val unit = doc.select(".js-balance-unit").first()?.text() ?: "₽"

        Result.success(DraftOrder(
            csrfToken = csrf, type = type, method = method, gate = gate,
            offerId = offer, priceGuard = pg, amount = amt, player = player,
            deductedFromBalance = "$deducted $unit", leftToPay = "$leftToPay $unit",
            title = doc.select(".page-header").text().trim()
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend fun FunPayRepository.payDraftOrder(draft: DraftOrder): Result<OrderNewResult> = withContext(Dispatchers.IO) {
    try {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
        val form = FormBody.Builder()
            .add("csrf_token", draft.csrfToken)
            .add("type", draft.type)
            .add("method", draft.method)
            .add("gate", draft.gate)
            .add("offer_id", draft.offerId)
            .add("price_guard", draft.priceGuard)
            .add("amount", draft.amount)
            .add("player", draft.player)
            .build()

        val req = Request.Builder()
            .url("https://funpay.com/orders/new")
            .post(form)
            .header("Cookie", getCookieString())
            .header("User-Agent", ua)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        val resp = repoClient.newCall(req).execute()
        updateSession(resp)
        val raw = resp.body?.string().orEmpty()

        val json = try { JSONObject(raw) } catch (e: Exception) { return@withContext Result.failure(Exception("Неверный ответ сервера: $raw")) }

        if (json.optBoolean("error", false)) {
            return@withContext Result.failure(Exception(json.optString("msg", "Ошибка оплаты")))
        }

        
        val formHtml = json.optString("form", "")
        if (formHtml.isNotBlank()) {
            val actionMatch = Regex("action=\"([^\"]+)\"").find(formHtml)
            if (actionMatch != null) {
                val actionUrl = actionMatch.groupValues[1]
                val oidMatch = Regex("/orders/([A-Za-z0-9]+)").find(actionUrl)
                val oid = oidMatch?.groupValues?.getOrNull(1).orEmpty()
                return@withContext Result.success(OrderNewResult.Redirect(actionUrl, oid))
            }
        }

        
        val url = json.optString("url", "")
        if (url.isNotBlank()) {
            val oid = Regex("/orders/([A-Za-z0-9]+)").find(url)?.groupValues?.getOrNull(1).orEmpty()
            return@withContext Result.success(OrderNewResult.Redirect(url, oid))
        }

        Result.failure(Exception("FunPay не вернул ссылку. Ответ: $raw"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend fun FunPayRepository.confirmOrder(orderId: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val appData = getCsrfAndId() ?: return@withContext false
        val form = FormBody.Builder()
            .add("csrf_token", appData.first)
            .add("id", orderId)
            .build()

        val req = Request.Builder()
            .url("https://funpay.com/orders/complete")
            .post(form)
            .header("Cookie", getCookieString())
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        val response = repoClient.newCall(req).execute()
        updateSession(response)
        response.isSuccessful
    } catch (e: Exception) {
        false
    }
}