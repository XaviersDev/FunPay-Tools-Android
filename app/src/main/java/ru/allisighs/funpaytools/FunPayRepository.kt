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

data class ChatItem(val id: String, val username: String, val lastMessage: String, val isUnread: Boolean, val avatarUrl: String, val date: String, val activeLot: String? = null)

data class MessageItem(
    val id: String,
    val author: String,
    val text: String,
    val isMe: Boolean,
    val time: String,
    val imageUrl: String? = null,
    val badge: String? = null
)

data class AutoResponseCommand(
    val trigger: String,
    val response: String,
    val exactMatch: Boolean,
    val imageUri: String? = null,
    val imageFirst: Boolean = true
)

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
    val hasReview: Boolean,
    val reviewRating: Int,
    val reviewText: String,
    val sellerReply: String,
    val params: Map<String, String>
)

data class ChatInfo(
    val lookingAtLink: String?,
    val lookingAtName: String?,
    val registrationDate: String?,
    val language: String?,
    val avatarUrl: String? = null,
    val userStatus: String? = null
)

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
    private var lastBusyInitAt = 0L
    internal val knownUnreadChats = mutableSetOf<String>()


    class FunPayCookieJar : okhttp3.CookieJar {
        private val cookieStore = HashMap<String, List<okhttp3.Cookie>>()

        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {

            cookieStore[url.host] = cookies


            cookies.forEach {
                if (it.name == "PHPSESSID") {
                    android.util.Log.d("COOKIE_JAR", "Поймана сессия: ${it.value}")
                }
            }
        }

        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
            return cookieStore[url.host] ?: ArrayList()
        }


        fun addManualCookie(url: String, name: String, value: String) {
            val httpUrl = url.toHttpUrlOrNull() ?: return
            val cookie = okhttp3.Cookie.Builder()
                .domain(httpUrl.host)
                .path("/")
                .name(name)
                .value(value)
                .httpOnly()
                .secure()
                .build()

            val current = cookieStore[httpUrl.host]?.toMutableList() ?: mutableListOf()

            current.removeAll { it.name == name }
            current.add(cookie)
            cookieStore[httpUrl.host] = current
        }

        fun getCookieValue(name: String): String? {

            return cookieStore.values.flatten().find { it.name == name }?.value
        }
    }

    companion object {
        val lastOutgoingMessages = mutableMapOf<String, String>()

        private const val PREFS_NAME = "funpay_prefs"
        private const val KEY_ACCOUNTS_DATA = "accounts_data"
        private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
    }

    val rawChatResponses = mutableMapOf<String, String>()

    val cookieJar = FunPayCookieJar()
    val repoClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    init {

        val savedGk = prefs.getString("golden_key", "") ?: ""
        val savedSess = prefs.getString("phpsessid", "") ?: ""

        if (savedGk.isNotEmpty()) {
            cookieJar.addManualCookie("https://funpay.com", "golden_key", savedGk)
        }
        if (savedSess.isNotEmpty()) {
            cookieJar.addManualCookie("https://funpay.com", "PHPSESSID", savedSess)
            phpsessid = savedSess
        }


        val activeAcc = getActiveAccount()
        if (activeAcc != null && activeAcc.phpSessionId.isNotEmpty()) {
            if (phpsessid.isEmpty()) phpsessid = activeAcc.phpSessionId
            cookieJar.addManualCookie("https://funpay.com", "golden_key", activeAcc.goldenKey)
            cookieJar.addManualCookie("https://funpay.com", "PHPSESSID", activeAcc.phpSessionId)
        }
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
    fun setRaiseInterval(minutes: Int) = prefs.edit().putInt("raise_interval", minutes).apply()
    fun getRaiseInterval(): Int = prefs.getInt("raise_interval", 15)

    fun saveCommands(commands: List<AutoResponseCommand>) = prefs.edit().putString("auto_commands", gson.toJson(commands)).apply()
    fun getCommands(): List<AutoResponseCommand> {
        val json = prefs.getString("auto_commands", "[]")
        return gson.fromJson(json, object : TypeToken<List<AutoResponseCommand>>() {}.type)
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
                            sendWithOptionalImage(chat.id, settings.messageText, settings.imageUri, settings.imageFirst)
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

                val combinedUserPrompt = """
Ты — дружелюбный продавец на игровой бирже FunPay.
Покупатель оставил отзыв с оценкой $stars из 5 на товар "$lotName".
Текст отзыва: "$reviewText"
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


    /**
     * Копирует picker URI (content://media/picker/...) в filesDir приложения,
     * чтобы сервис мог читать файл позже без временных прав пикера.
     * Возвращает путь к локальной копии (file:
     */
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

            val lastSelf = FunPayRepository.lastOutgoingMessages[chat.id]
            if (lastSelf != null) {
                val cleanIncoming = chat.lastMessage.replace("...", "").trim().lowercase()
                val cleanSelf = lastSelf.trim().lowercase()
                if (cleanSelf.contains(cleanIncoming) || cleanIncoming.contains(cleanSelf)) {
                    continue
                }
            }

            if (confirmPhrases.any { lastMsgLower.contains(it) }) {
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
                                            sendWithOptionalImage(chat.id, autoRefundSettings.messageText, autoRefundSettings.imageUri, autoRefundSettings.imageFirst)
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

                    val finalText = settings.text
                        .replace("\$username", chat.username)
                        .replace("\$order_id", orderId)
                        .replace("\$chat_name", chat.username)

                    sendWithOptionalImage(chat.id, finalText, settings.imageUri, settings.imageFirst)
                }

                markEventAsProcessed(chat.id, orderId, "confirm")
                kotlinx.coroutines.delay(1500)
            }
        }
    }

    /**
     * Отправляет сообщение с опциональной картинкой.
     * Если imageUri != null — загружает картинку и отправляет в нужном порядке.
     * Возвращает true если хотя бы одна часть ушла успешно.
     *
     * FIX: сразу помечаем nodeId как "__image__" в lastOutgoingMessages ДО upload,
     * чтобы сервис не выбросил оптимистичный MessageItem при первой отправке.
     */
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
            }
            success
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка отправки: ${e.message}")
            false
        }
    }


    suspend fun rewriteMessage(text: String, contextHistory: String): String? {
        return try {
            val systemPrompt = "You are a text editing model. Follow user instructions precisely."

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

--- ИСТОРИЯ ПЕРЕПИСКИ ---
$contextHistory
--- КОНЕЦ ИСТОРИИ ---

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

        val lastRun = prefs.getLong("last_raise_time", 0)
        val multiplier = getDelayMultiplier()
        val baseInterval = getRaiseInterval()
        val interval = (baseInterval * multiplier).toLong() * 60 * 1000L

        if (System.currentTimeMillis() - lastRun < interval) return

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

            for (div in gameDivs) {
                val subcategoryLink = div.select("h3 a").attr("href")
                if (subcategoryLink.contains("chips")) continue

                val nodeId = subcategoryLink.split("/").filter { it.isNotEmpty() }.lastOrNull()?.toIntOrNull()
                var gameId = div.attr("data-game-id").toIntOrNull()
                if (gameId == null && nodeId != null) gameId = nodeToGameMap[nodeId]

                if (gameId != null && nodeId != null) {
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

                            val json2 = JSONObject(readBodySilent(resp2))
                            if (!json2.optBoolean("error")) raisedCount++
                        }
                    } else {
                        delay(1000)
                        val respRetry = RetrofitInstance.api.raiseLotInitial(
                            cookie = getCookieString(), userAgent = userAgent, gameId = gameId, nodeId = nodeId
                        )
                        updateSession(respRetry)

                        if (!json1.optBoolean("error")) raisedCount++
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
            if (raisedCount > 0) LogManager.addLog("🏁 Подняты все $raisedCount")
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

            for (itemDiv in messageElements) {
                val idStr = itemDiv.attr("id").removePrefix("message-")
                if (idStr.isEmpty()) continue

                val authorLink = itemDiv.selectFirst(".chat-msg-author-link")
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
                        badge = badge
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

            for (i in 0 until messagesArray.length()) {
                val msgObj = messagesArray.getJSONObject(i)
                val html = msgObj.optString("html")
                val rawAuthorId = msgObj.optString("author")

                val doc = Jsoup.parseBodyFragment(html)
                val itemDiv = doc.selectFirst(".chat-msg-item") ?: continue

                val idStr = itemDiv.attr("id").removePrefix("message-").ifEmpty { msgObj.optString("id") }

                val authorLink = itemDiv.selectFirst(".chat-msg-author-link")
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
                        badge = badge
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

                doc.select(".param-item").forEach { item ->
                    val label = item.select("h5").text()
                    if (label.contains("Действия")) return@forEach

                    val value = item.select("div").last()?.text() ?: ""

                    if (label.contains("Игра")) gameTitle = value
                    if (label.contains("Краткое описание")) shortDesc = value
                    if (label.contains("Сумма")) price = item.select("span.h1").text() + " " + item.select("strong").last()?.text()

                    if (label.isNotEmpty() && value.isNotEmpty()) {
                        params[label] = value
                    }
                }

                val buyerName = doc.select(".chat-float .media-user-name a").first()?.text() ?: "Unknown"
                var buyerAvatar = doc.select(".chat-float .media-left img").attr("src")
                if (buyerAvatar.startsWith("/")) buyerAvatar = "https://funpay.com$buyerAvatar"

                val canRefund = doc.select("button.btn-refund").isNotEmpty()


                val reviewItem = doc.select(".review-item").first()
                val reviewText = doc.select(".review-item-text").text()


                val hasReview = reviewItem != null && reviewText.isNotEmpty()

                var reviewRating = 0
                var sellerReply = ""

                if (hasReview) {
                    reviewRating = doc.select(".review-item-rating .rating div").attr("class").filter { it.isDigit() }.toIntOrNull() ?: 0


                    val replyDiv = doc.select(".review-item-answer").first()
                    if (replyDiv != null) {

                        replyDiv.select(".review-controls")?.remove()
                        sellerReply = replyDiv.text().trim()
                    }
                }

                OrderDetails(
                    id = id,
                    status = status,
                    gameTitle = gameTitle,
                    shortDesc = shortDesc,
                    price = price,
                    buyerName = buyerName,
                    buyerAvatar = buyerAvatar,
                    canRefund = canRefund,
                    hasReview = hasReview,
                    reviewRating = reviewRating,
                    reviewText = reviewText,
                    sellerReply = sellerReply,
                    params = params
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

    suspend fun checkBusyModeReplies(chats: List<ChatItem>, settings: BusyModeSettings) {
        val cache = getBusyCache()
        val currentTime = System.currentTimeMillis()
        val cooldownMs = settings.cooldownMinutes * 60 * 1000L
        val enabledAt = settings.enabledAt
        var cacheChanged = false

        val systemPhrases = listOf(
            "оплатил заказ", "paid for order", "сплатив замовлення",
            "подтвердил успешное", "confirmed that order", "підтвердив успішне",
            "написал отзыв", "given feedback", "написав відгук",
            "изменил отзыв", "edited their feedback", "змінив відгук",
            "вернул деньги", "refunded", "повернув гроші",
            "открыт повторно", "reopened", "відкрито повторно"
        )

        for (chat in chats) {
            if (!chat.isUnread) continue

            val lastMsgLower = chat.lastMessage.lowercase(Locale.getDefault())

            val lastSelf = FunPayRepository.lastOutgoingMessages[chat.id]
            if (lastSelf != null) {
                val cleanIncoming = chat.lastMessage.replace("...", "").trim().lowercase(Locale.getDefault())
                val cleanSelf = lastSelf.trim().lowercase(Locale.getDefault())
                if (cleanSelf.contains(cleanIncoming) || cleanIncoming.contains(cleanSelf)) continue
            }


            if (enabledAt > 0L && lastBusyInitAt != enabledAt) {
                chats.forEach { chat ->
                    if (!cache.containsKey(chat.id)) cache[chat.id] = enabledAt
                }
                saveBusyCache(cache)
                lastBusyInitAt = enabledAt
                return
            }
            if (cache.isEmpty() && chats.isNotEmpty()) {
                chats.forEach { chat -> cache[chat.id] = currentTime }
                saveBusyCache(cache)
                return
            }

            val isNewOrder = lastMsgLower.contains("оплатил заказ") ||
                    lastMsgLower.contains("paid for order") ||
                    lastMsgLower.contains("сплатив замовлення")

            if (isNewOrder && settings.autoRefund) {
                val orderIdMatch = Regex("#([a-zA-Z0-9]+)").find(chat.lastMessage)
                val orderId = orderIdMatch?.groupValues?.get(1)
                if (orderId != null && !wasEventProcessed(chat.id, orderId, "busy_refund")) {
                    LogManager.addLog("🛑 Авто-отмена заказа $orderId")
                    val success = refundOrder(orderId)
                    if (success && settings.autoRefundMessage && settings.message.isNotBlank()) {
                        sendWithOptionalImage(chat.id, settings.message, settings.imageUri, settings.imageFirst)
                    }
                    markEventAsProcessed(chat.id, orderId, "busy_refund")
                    continue
                }
            }

            if (systemPhrases.any { lastMsgLower.contains(it) } || chat.lastMessage.startsWith("####")) continue

            val lastReplyTime = cache[chat.id] ?: 0L
            if (lastReplyTime > 0L) {
                if (settings.cooldownMinutes == 0) continue
                if (currentTime - lastReplyTime < cooldownMs) continue
            }

            if (settings.message.isNotBlank() || settings.imageUri != null) {
                LogManager.addLog("🛑 Режим занятости: ответ для ${chat.username}")
                val success = sendWithOptionalImage(chat.id, settings.message, settings.imageUri, settings.imageFirst)
                if (success) {
                    cache[chat.id] = currentTime
                    cacheChanged = true
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

                    val incomingText = lastMessageText.replace("\n", "").trim().lowercase(Locale.getDefault())

                    val cmd = commands.find { commandObj ->
                        val trigger = commandObj.trigger.trim().lowercase(Locale.getDefault())
                        if (commandObj.exactMatch) incomingText == trigger
                        else incomingText == trigger || incomingText.contains(trigger)
                    }

                    if (cmd != null) {
                        val now = System.currentTimeMillis()




                        val messageId: String
                        try {
                            val history = getChatHistory(chat.id)
                            val lastIncoming = history.lastOrNull { !it.isMe }
                            if (lastIncoming == null) continue

                            val trigger = cmd.trigger.trim().lowercase(Locale.getDefault())
                            val incomingNorm = lastIncoming.text.trim().lowercase(Locale.getDefault())
                            val triggerMatches = if (cmd.exactMatch) incomingNorm == trigger
                            else incomingNorm == trigger || incomingNorm.contains(trigger)

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

                        LogManager.addLog("🎯 Команда '${cmd.trigger}' от ${chat.username}")
                        val success = sendWithOptionalImage(chat.id, cmd.response, cmd.imageUri, cmd.imageFirst)

                        if (success) {
                            recentCommandKeys[messageId] = now
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




            val greetingText = settings.text
                .replace("\$username", chat.username)
                .replace("\$chat_name", chat.username)

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
                    val todayDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                    finalReplyText = finalReplyText!!
                        .replace("\$username", buyerName)
                        .replace("\$order_id", orderId)
                        .replace("\$lot_name", lotName)
                        .replace("\$date", todayDate)
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

                finalReplyText = templateText
                    .replace("\$username", buyerName)
                    .replace("\$order_id", orderId)
                    .replace("\$lot_name", lotName)
                    .replace("\$date", SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()))
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
8. Твой ответ — это ТОЛЬКО готовый текст. Без кавычек, заголовков или объяснений.$customLine

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
                item.attr("href").contains("id=$lotId")
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
                // Ищем только по /orders/XXXXXXXX/ — реальный формат FunPay (8 символов верхний регистр+цифры)
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
        // Всегда сохраняем lastRunAt — иначе цикл повторяется каждые 7 сек при 0 заказов
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
        // Минимальный интервал 1 час, даже если autoIntervalHours = 0
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