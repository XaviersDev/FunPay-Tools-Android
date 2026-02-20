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

const val APP_VERSION = "1.2"

data class AutoRefundSettings(
    val enabled: Boolean = false,
    val maxPrice: Double = 10.0,
    val triggerStars: List<Int> = listOf(1, 2, 3),
    val sendMessage: Boolean = true,
    val messageText: String = "Мне жаль, что у Вас возникли проблемы. Обращайтесь ещё 🤝"
)

data class OrderConfirmSettings(
    val enabled: Boolean = false,
    val text: String = "Спасибо за заказ, \$username! 🤝\nБуду очень благодарен, если оставите отзыв. Это очень поможет мне! 😊"
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
data class AutoResponseCommand(val trigger: String, val response: String, val exactMatch: Boolean)


data class MessageTemplate(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val text: String
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
    val disabledStars: List<Int> = emptyList()
)

data class GreetingSettings(
    val enabled: Boolean,
    val text: String,
    val cooldownHours: Int,
    val ignoreSystemMessages: Boolean
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
    val language: String?
)

object LogManager {
    private val _logs = MutableStateFlow<List<Pair<String, Boolean>>>(listOf(Pair("Система готова. v$APP_VERSION", false)))
    val logs = _logs.asStateFlow()

    var debugEnabled: Boolean = false

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

    private val gson = Gson()
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
    private var phpsessid: String = ""
    private val nodeToGameMap = mutableMapOf<Int, Int>()
    private var lastCsrfFetchTime = 0L
    private var cachedCsrf: Pair<String, String>? = null
    private val CSRF_CACHE_DURATION = 30_000L

    
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

    private fun saveGreetedCache(cache: Map<String, Long>) =
        prefs.edit().putString("greeted_cache", gson.toJson(cache)).apply()

    private fun getGreetedCache(): MutableMap<String, Long> {
        val json = prefs.getString("greeted_cache", "{}")
        val type = object : TypeToken<MutableMap<String, Long>>() {}.type
        return gson.fromJson(json, type)
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
                    LogManager.addLogDebug("⏭️ Сообщение уже обработано")
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
                            sendMessage(chat.id, settings.messageText)
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
                    LogManager.addLogDebug("⏭️ Ответ на отзыв уже отправлен")
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

                val sellerReply = doc.select(".review-reply").text()
                if (sellerReply.isNotEmpty() && !sellerReply.contains("textarea")) {
                    LogManager.addLog("ℹ️ На отзыв #$orderId уже есть ответ")
                    markEventAsProcessed(chat.id, reviewEventKey, "review_reply")
                    continue
                }

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
                        sentenceCount = settings.aiLength
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

                val finalReplyText = replyText
                    .replace("\$username", chat.username)
                    .replace("\$order_id", orderId)
                    .replace("\$chat_name", chat.username)
                    .replace("\$lot_name", lotName)

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

    private suspend fun generateAiReviewReply(lotName: String, reviewText: String, stars: Int, sentenceCount: Int): String? {
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

                val combinedUserPrompt = """
Ты — дружелюбный продавец на игровой бирже FunPay.
Покупатель оставил отзыв с оценкой $stars из 5 на товар "$lotName".
Текст отзыва: "$reviewText"

Твоя задача — написать тёплый, искренний ответ на этот отзыв.

--- ВАЖНЫЕ ПРАВИЛА ---
1. ДЛИНА: Твой ответ должен быть примерно $lengthGuide.
2. ОБЯЗАТЕЛЬНО УПОМЯНИ товар "$lotName" или его суть в своём ответе.
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
9. Твой ответ — это ТОЛЬКО готовый текст. Без кавычек, без заголовков, без пояснений.

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
                    LogManager.addLog("⛔ CF BLOCK (Auth) - попытка ${attempt + 1}")
                    if (attempt < 2) {
                        delay(2000L * (attempt + 1))
                        return@repeat
                    }
                    return null
                }

                val doc = Jsoup.parse(html)
                val appDataStr = doc.select("body").attr("data-app-data")

                if (appDataStr.isEmpty()) {
                    LogManager.addLog("❌ Ошибка Auth: нет data-app-data - попытка ${attempt + 1}")
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
                LogManager.addLog("⏱️ Timeout на попытке ${attempt + 1}")
                if (attempt < 2) {
                    delay(3000L * (attempt + 1))
                }
            } catch (e: Exception) {
                LogManager.addLog("❌ getCsrfAndId exception (попытка ${attempt + 1}): ${e.message}")
                if (attempt < 2) {
                    delay(2000L * (attempt + 1))
                }
            }
        }

        return null
    }


    suspend fun uploadImage(uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(context.cacheDir, "upload_image.jpg")
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

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
            val json = JSONObject(jsonStr)

            if (json.has("fileId")) {
                json.getString("fileId")
            } else if (json.has("url")) {

                json.getString("url")
            } else {
                null
            }
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка загрузки фото: ${e.message}")
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
                    
                    LogManager.addLog("📝 Отзыв оставлен, отправляем ответ...")
                    val (csrf, userId) = getCsrfAndId() ?: continue
                    handleReview(orderId, chat.username, csrf, userId)
                } else {
                    
                    LogManager.addLog("💬 Отзыва нет, отправляем просьбу оставить отзыв...")

                    val finalText = settings.text
                        .replace("\$username", chat.username)
                        .replace("\$order_id", orderId)
                        .replace("\$chat_name", chat.username)

                    sendMessage(chat.id, finalText)
                }

                markEventAsProcessed(chat.id, orderId, "confirm")
                kotlinx.coroutines.delay(1500)
            }
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
                                if (lastSent != null) {
                                    val cleanMsg = lastMsgText.replace("...", "").trim()
                                    val cleanSent = lastSent.trim()
                                    if (cleanSent.contains(cleanMsg) || cleanSent == cleanMsg) {
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


    suspend fun getChatHistory(chatId: String): List<MessageItem> {
        val authData = getCsrfAndId()
        if (authData == null) {
            return emptyList()
        }

        val (csrf, userId) = authData

        var nodeName = chatId
        if (!chatId.startsWith("users-")) {
            try {
                val url = "https://funpay.com/chat/history?node=$chatId&last_message=0"
                val resp = RetrofitInstance.api.getChatHistory(url, getCookieString(), userAgent, "XMLHttpRequest")
                val json = JSONObject(readBodySilent(resp))
                val correctName = json.optJSONObject("chat")?.optJSONObject("node")?.optString("name")

                if (!correctName.isNullOrEmpty() && correctName.startsWith("users-")) {
                    nodeName = correctName
                }
            } catch (e: Exception) {
            }
        }

        repeat(2) { attempt ->
            try {
                val objectsPayload = """
                    [
                        {
                            "type": "chat_node",
                            "id": "$nodeName",
                            "tag": "00000000",
                            "data": {
                                "node": "$nodeName",
                                "last_message": -1,
                                "content": ""
                            }
                        }
                    ]
                """.trimIndent()

                val response = withContext(Dispatchers.IO) {
                    RetrofitInstance.api.runnerGet(
                        cookie = getCookieString(),
                        userAgent = userAgent,
                        objects = objectsPayload,
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

                rawChatResponses[chatId] = jsonStr

                val json = JSONObject(jsonStr)

                if (json.optBoolean("error", false)) {
                    LogManager.addLog("❌ История вернула ошибку: ${json.optString("msg")}")
                    if (attempt < 1) {
                        delay(2000)
                        cachedCsrf = null
                        return@repeat
                    }
                    return emptyList()
                }

                val objectsArr = json.optJSONArray("objects") ?: return emptyList()

                for (i in 0 until objectsArr.length()) {
                    val obj = objectsArr.getJSONObject(i)
                    if (obj.optString("type") == "chat_node" && obj.optString("id") == nodeName) {
                        val data = obj.optJSONObject("data") ?: continue
                        val messagesArray = data.optJSONArray("messages") ?: continue

                        val messages = mutableListOf<MessageItem>()
                        for (j in 0 until messagesArray.length()) {
                            try {
                                val msgObj = messagesArray.getJSONObject(j)
                                val html = msgObj.optString("html", "")
                                val doc = Jsoup.parse(html.replace("<br>", "\n"))

                                val id = msgObj.optString("id", "0")
                                val authorId = msgObj.optString("author", "0")

                                val authorElement = doc.select("div.media-user-name a").first()
                                val author = authorElement?.text() ?: "Unknown"

                                val badgeText = doc.select(".chat-msg-author-label").text().ifEmpty { null }

                                val textElement = doc.select("div.chat-msg-text").first()

                                var text = textElement?.wholeText() ?: ""

                                var imageUrl: String? = null
                                val imgLink = doc.select("a.chat-img-link").first()
                                if (imgLink != null) {
                                    imageUrl = imgLink.attr("href")
                                    if (imageUrl.isNotEmpty() && !imageUrl.startsWith("http")) {
                                        imageUrl = "https://funpay.com$imageUrl"
                                    }
                                    if (text.isEmpty()) text = "Фотография"
                                }

                                val timeElement = doc.select("div.chat-msg-date").first()
                                val time = timeElement?.text() ?: ""
                                val isMe = authorId == userId

                                if (text.isNotEmpty() || imageUrl != null) {
                                    messages.add(MessageItem(id, author, text, isMe, time, imageUrl, badgeText))
                                }
                            } catch (e: Exception) { }
                        }

                        return messages
                    }
                }

                return emptyList()

            } catch (e: java.net.SocketTimeoutException) {
                if (attempt < 1) delay(3000)
            } catch (e: Exception) {
                if (attempt < 1) delay(2000)
            }
        }

        return emptyList()
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

                ChatInfo(lookingAtLink, lookingAtName, registrationDate, language)
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

            
            if (enabledAt > 0L) {
                if (!wasEventProcessed(chat.id, enabledAt.toString(), "busy_new_after_enable")) {
                    
                    markEventAsProcessed(chat.id, enabledAt.toString(), "busy_new_after_enable")
                    continue
                }
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
                        sendMessage(chat.id, settings.message)
                    }
                    markEventAsProcessed(chat.id, orderId, "busy_refund")
                    continue
                }
            }

            if (systemPhrases.any { lastMsgLower.contains(it) } || chat.lastMessage.startsWith("####")) continue

            val lastReplyTime = cache[chat.id] ?: 0L
            if (settings.cooldownMinutes > 0 && (currentTime - lastReplyTime < cooldownMs)) continue

            if (settings.message.isNotBlank()) {
                LogManager.addLog("🛑 Режим занятости: ответ для ${chat.username}")
                val success = sendMessage(chat.id, settings.message)
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

                        if (commandObj.exactMatch) {
                            
                            incomingText == trigger
                        } else {
                            
                            incomingText == trigger || incomingText.contains(trigger)
                        }
                    }

                    if (cmd != null) {
                        val commandKey = "${chat.id}_${incomingText.hashCode()}_${cmd.trigger.hashCode()}"

                        if (wasEventProcessed(chat.id, commandKey, "command")) {
                            continue
                        }

                        LogManager.addLog("🎯 Команда '${cmd.trigger}' от ${chat.username}")
                        val success = sendMessage(chat.id, cmd.response)

                        if (success) {
                            markEventAsProcessed(chat.id, commandKey, "command")
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
            val lastMsgLower = chat.lastMessage.lowercase()


            val lastSelf = FunPayRepository.lastOutgoingMessages[chat.id]
            if (lastSelf != null) {
                val cleanIncoming = chat.lastMessage.replace("...", "").trim().lowercase()
                val cleanSelf = lastSelf.trim().lowercase()

                if (cleanSelf.contains(cleanIncoming) || cleanIncoming.contains(cleanSelf)) {
                    continue
                }
            }

            if (settings.ignoreSystemMessages) {
                if (systemPhrases.any { lastMsgLower.contains(it) }) continue
                if (chat.lastMessage.startsWith("####")) continue
            }

            val lastGreetTime = cache[chat.id] ?: 0L
            if (currentTime - lastGreetTime < cooldownMs) continue

            LogManager.addLog("👋 Приветствие для ${chat.username}")

            val greetingText = settings.text
                .replace("\$username", chat.username)
                .replace("\$chat_name", chat.username)

            val success = sendMessage(chat.id, greetingText)

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
                        finalReplyText = generateReviewReply(lotName, reviewText, stars, settings.aiLength)
                        if (finalReplyText == null) delay(1000)
                    }
                }

                if (finalReplyText == null) {
                    LogManager.addLog("⚠️ AI не ответил, использую Fallback текст")
                    finalReplyText = settings.aiFallbackText
                }

                if (finalReplyText != null) {
                    finalReplyText = finalReplyText!!
                        .replace("\$username", buyerName)
                        .replace("\$order_id", orderId)
                        .replace("\$lot_name", lotName)
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

    suspend fun generateReviewReply(lotName: String, reviewText: String, stars: Int, strength: Int): String? {
        return try {
            val lengthInstruction = when {
                strength <= 2 -> "2. Ответ должен быть ОЧЕНЬ коротким (1-2 предложения)."
                strength <= 4 -> "2. Ответ должен быть кратким, но емким (2-3 предложения)."
                strength <= 7 -> "2. Ответ должен быть развёрнутым (3-4 предложения), дружелюбным и эмоциональным."
                strength <= 9 -> "2. Ответ должен быть очень подробным (4-5 предложений), выражать искреннюю радость и благодарность."
                else -> "2. Ответ должен быть максимально развёрнутым и креативным (5-6 предложений), очень живым, эмоциональным и запоминающимся."
            }

            val systemPrompt = "You are a text editing model. Follow user instructions precisely."
            val combinedUserPrompt = """
Ты — вежливый и дружелюбный продавец на игровой бирже FunPay.
Покупатель оставил отзыв с оценкой $stars из 5 на твой товар "$lotName".
Текст отзыва: "$reviewText"

Твоя задача — написать позитивный и благодарный ответ на этот отзыв.

--- ПРАВИЛА ---
1. ОБЯЗАТЕЛЬНО упомяни название товара "$lotName" или его суть.
$lengthInstruction
3. Используй дружелюбный тон и уместные эмодзи (например, 😊, 👍, 🎉, ✨, 💜, 🔥).
4. Поблагодари покупателя за отзыв и/или покупку.
5. НЕ используй шаблонные фразы типа "Обращайтесь ещё" или "Буду рад помочь".
6. Пиши живо и естественно, как настоящий человек.
7. НЕ используй Markdown, жирный текст или курсив.
8. Твой ответ — это ТОЛЬКО готовый текст. Без кавычек, заголовков или объяснений.

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


    // xd dumper


    private val dumperLastUpdateMap = mutableMapOf<String, Long>()

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

    private suspend fun processDumperLot(config: DumperLotConfig) = withContext(Dispatchers.IO) {
        val myLots = getMyLots()
        val myCurrentLot = myLots.find { it.id == config.lotId }

        if (myCurrentLot == null || !myCurrentLot.isActive) {
            LogManager.addLogDebug("XD Dumper: Лот ${config.lotId} не найден или неактивен.")
            return@withContext
        }

        
        val request = Request.Builder()
            .url("https://funpay.com/lots/${config.categoryId}/")
            .header("Cookie", getCookieString())
            .header("User-Agent", userAgent)
            .build()

        val response = repoClient.newCall(request).execute()
        val html = response.body?.string() ?: return@withContext
        val doc = Jsoup.parse(html)

        val activeProfile = getActiveAccount() ?: return@withContext

        
        val competitors = mutableListOf<Double>()
        val keywords = config.keywords.split("|").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        var position = 0

        doc.select("a.tc-item").forEach { item ->
            position++
            if (position > config.positionMax) return@forEach

            val sellerName = item.select(".media-user-name").text().trim()
            if (sellerName == activeProfile.username) return@forEach 

            val desc = item.select(".tc-desc-text").text().trim().lowercase()

            
            if (keywords.isNotEmpty() && keywords.none { desc.contains(it) }) return@forEach

            val starsElements = item.select(".rating-stars i.fas")
            val rating = starsElements.size
            if (rating == 0 && config.ignoreZeroRating) return@forEach
            if (rating < config.ratingMin) return@forEach

            val lotId = item.attr("href").substringAfter("id=").substringBefore("&")

            var finalPrice = 9999999.0

            
            if (config.fastPriceCheck) {
                try {
                    val fpReq = Request.Builder()
                        .url("https://funpay.com/lots/offer?id=$lotId")
                        .header("Cookie", getCookieString())
                        .header("User-Agent", userAgent)
                        .build()
                    val fpResp = repoClient.newCall(fpReq).execute()
                    val fpHtml = fpResp.body?.string() ?: ""
                    val fpDoc = Jsoup.parse(fpHtml)

                    val options = fpDoc.select("select[name=method] option")
                    val prices = mutableListOf<Double>()
                    options.forEach { opt ->
                        if (opt.attr("data-cy") == "rub") {
                            val factors = opt.attr("data-factors").split(",")
                            if (factors.size >= 2) {
                                val base = factors[0].toDoubleOrNull() ?: 0.0
                                val comm = factors[1].toDoubleOrNull() ?: 0.0
                                prices.add(base * comm)
                            }
                        }
                    }
                    if (prices.isNotEmpty()) finalPrice = prices.minOrNull() ?: finalPrice
                } catch (e: Exception) {}
            } else {
                
                val priceAttr = item.select(".tc-price").attr("data-s")
                finalPrice = priceAttr.toDoubleOrNull() ?: 9999999.0
            }

            if (finalPrice in config.priceMin..(config.priceMax + config.priceStep)) {
                competitors.add(finalPrice)
            }
        }

        if (competitors.isEmpty()) return@withContext

        val minCompetitorPrice = competitors.minOrNull() ?: return@withContext

        
        var targetPrice = minCompetitorPrice - config.priceStep
        if (config.priceDivider > 1.0) {
            targetPrice -= (targetPrice % config.priceDivider)
        }

        targetPrice = targetPrice.coerceIn(config.priceMin, config.priceMax)
        if (targetPrice < 1.0) targetPrice = 1.0

        val currentPriceWithComm = myCurrentLot.price ?: 0.0

        
        if (targetPrice < currentPriceWithComm - 0.01) {
            
            updateLotPrice(config.lotId, config.categoryId, targetPrice)
        } else if (targetPrice > currentPriceWithComm + 0.01) {
            
            val diff = targetPrice - currentPriceWithComm
            val percent = if (currentPriceWithComm > 0) (diff / currentPriceWithComm) * 100 else 100.0

            if (targetPrice < minCompetitorPrice && diff <= 100.0 && percent <= 10.0) {
                updateLotPrice(config.lotId, config.categoryId, targetPrice)
                LogManager.addLog("📈 XD Dumper: Поднял цену лота ${config.lotId} до $targetPrice")
            }
        }
    }

    private suspend fun updateLotPrice(lotId: String, nodeId: String, targetPriceForBuyer: Double) {
        
        val calcReqForm = FormBody.Builder()
            .add("nodeId", nodeId)
            .add("price", "1000")
            .build()

        val calcReq = Request.Builder()
            .url("https://funpay.com/lots/calc")
            .post(calcReqForm)
            .header("Cookie", getCookieString())
            .header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", userAgent)
            .build()

        val calcResp = repoClient.newCall(calcReq).execute()
        val calcJson = JSONObject(calcResp.body?.string() ?: "{}")

        var commissionCoef = 1.0
        val methods = calcJson.optJSONArray("methods")
        if (methods != null) {
            for (i in 0 until methods.length()) {
                val m = methods.getJSONObject(i)
                if (m.optString("unit") == "RUB") {
                    val price = m.optString("price").replace(" ", "").toDoubleOrNull() ?: 1000.0
                    val coef = price / 1000.0
                    if (commissionCoef == 1.0 || coef < commissionCoef) commissionCoef = coef
                }
            }
        }

        var priceWithoutComm = targetPriceForBuyer / commissionCoef
        if (priceWithoutComm < 1.0) priceWithoutComm = 1.0

        
        val fieldsData = getLotFields(lotId)
        val allFields = fieldsData.fields.mapValues { it.value.value }.toMutableMap()
        allFields["price"] = String.format(Locale.US, "%.2f", priceWithoutComm)

        saveLot(lotId, allFields, fieldsData.csrfToken, fieldsData.activeCookies)
        LogManager.addLog("📉 XD Dumper: Цена лота $lotId снижена (Конкурент выебан). Цель: $targetPriceForBuyer")
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