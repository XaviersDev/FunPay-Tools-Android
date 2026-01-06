package ru.allisighs.funpaytools

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.jsoup.Jsoup
import retrofit2.Response
import okhttp3.ResponseBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ChatItem(val id: String, val username: String, val lastMessage: String, val isUnread: Boolean, val userId: String, val date: String)
data class MessageItem(val id: String, val author: String, val text: String, val isMe: Boolean, val time: String)
data class AutoResponseCommand(val trigger: String, val response: String, val exactMatch: Boolean)
data class ReviewReplyTemplate(val star: Int, val text: String, val enabled: Boolean)
data class GreetingSettings(
    val enabled: Boolean,
    val text: String,
    val cooldownHours: Int,
    val ignoreSystemMessages: Boolean
)

object LogManager {
    private val _logs = MutableStateFlow<List<String>>(listOf("Система готова."))
    val logs = _logs.asStateFlow()

    fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logMsg = "[$time] $msg"
        val currentList = _logs.value.toMutableList()
        if (currentList.size > 5000) currentList.removeLast()
        currentList.add(0, logMsg)
        _logs.value = currentList
        Log.d("FPC_LOG", msg)
    }

    fun saveLogsToFile(context: Context): String {
        val fileName = "funpay_logs_${System.currentTimeMillis()}.txt"
        val content = _logs.value.reversed().joinToString("\n\n")
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

class FunPayRepository(context: Context) {
    private val prefs = context.getSharedPreferences("fp_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
    private var phpsessid: String = ""
    private val nodeToGameMap = mutableMapOf<Int, Int>()

    fun saveGoldenKey(key: String) = prefs.edit().putString("golden_key", key).apply()
    fun getGoldenKey(): String? = prefs.getString("golden_key", null)
    fun hasAuth(): Boolean = !getGoldenKey().isNullOrBlank()

    fun setSetting(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun getSetting(key: String): Boolean = prefs.getBoolean(key, false)
    fun setRaiseInterval(minutes: Int) = prefs.edit().putInt("raise_interval", minutes).apply()
    fun getRaiseInterval(): Int = prefs.getInt("raise_interval", 15)

    fun saveCommands(commands: List<AutoResponseCommand>) = prefs.edit().putString("auto_commands", gson.toJson(commands)).apply()
    fun getCommands(): List<AutoResponseCommand> {
        val json = prefs.getString("auto_commands", "[]")
        return gson.fromJson(json, object : TypeToken<List<AutoResponseCommand>>() {}.type)
    }

    fun saveReviewTemplates(templates: List<ReviewReplyTemplate>) = prefs.edit().putString("review_templates", gson.toJson(templates)).apply()
    fun getReviewTemplates(): List<ReviewReplyTemplate> {
        val json = prefs.getString("review_templates", null) ?: return (1..5).map { ReviewReplyTemplate(it, "Спасибо за отзыв!", false) }
        return gson.fromJson(json, object : TypeToken<List<ReviewReplyTemplate>>() {}.type)
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

    private fun getCookieString(): String {
        val gk = getGoldenKey() ?: ""
        return if (phpsessid.isNotEmpty()) "golden_key=$gk; PHPSESSID=$phpsessid" else "golden_key=$gk"
    }

    private fun readBodySilent(response: Response<ResponseBody>): String {
        return response.body()?.string() ?: response.errorBody()?.string() ?: ""
    }

    private fun isCloudflare(html: String): Boolean = html.contains("Just a moment") || html.contains("cloudflare") || html.contains("challenge-platform")

    private fun updateSession(response: Response<*>) {
        val headers = response.headers()
        val cookies = headers.values("Set-Cookie")
        for (cookie in cookies) {
            if (cookie.contains("PHPSESSID")) {
                val newSessid = cookie.split(";")[0].split("=")[1]
                if (newSessid != phpsessid) phpsessid = newSessid
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
        } catch (e: Exception) { }
    }

    suspend fun getCsrfAndId(): Pair<String, String>? {
        val key = getGoldenKey() ?: return null
        return try {
            val response = RetrofitInstance.api.getMainPage(getCookieString(), userAgent)
            updateSession(response)
            val html = readBodySilent(response)
            if (isCloudflare(html)) {
                LogManager.addLog("⛔ CF BLOCK (Auth)")
                return null
            }
            val doc = Jsoup.parse(html)
            val appDataStr = doc.select("body").attr("data-app-data")
            if (appDataStr.isEmpty()) {
                LogManager.addLog("❌ Ошибка Auth: нет data-app-data")
                return null
            }
            val json = JSONObject(appDataStr)
            Pair(json.getString("csrf-token"), json.getString("userId"))
        } catch (e: Exception) { null }
    }

    suspend fun sendMessage(nodeId: String, text: String): Boolean {
        val appData = getCsrfAndId() ?: return false
        val (csrf, _) = appData
        return try {
            LogManager.addLog("📤 Отправка ($nodeId): '$text'")
            val requestJson = JSONObject()
            requestJson.put("action", "chat_message")
            val dataJson = JSONObject()
            dataJson.put("node", nodeId)
            dataJson.put("last_message", -1)
            dataJson.put("content", text)
            requestJson.put("data", dataJson)

            val resp = RetrofitInstance.api.runnerSend(
                cookie = getCookieString(), userAgent = userAgent, request = requestJson.toString(), csrfToken = csrf, objects = "[]"
            )
            updateSession(resp)
            val body = readBodySilent(resp)
            val json = JSONObject(body)
            !json.optBoolean("error", false)
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка отправки: ${e.message}")
            false
        }
    }

    suspend fun raiseAllLots() {
        if (!getSetting("auto_raise")) return
        val lastRun = prefs.getLong("last_raise_time", 0)
        val interval = getRaiseInterval() * 60 * 1000L
        if (System.currentTimeMillis() - lastRun < interval) return

        try {
            if (nodeToGameMap.isEmpty()) updateGameData()
            val (csrf, userId) = getCsrfAndId() ?: return

            LogManager.addLog("♻️ Поднятие...")
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
                            val json2 = JSONObject(readBodySilent(resp2))
                            if (!json2.optBoolean("error")) raisedCount++
                        }
                    } else {
                        if (!json1.optBoolean("error")) raisedCount++
                    }
                    kotlinx.coroutines.delay(1500)
                }
            }
            if (raisedCount > 0) LogManager.addLog("🏁 Поднято: $raisedCount")
            prefs.edit().putLong("last_raise_time", System.currentTimeMillis()).apply()

        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка поднятия: ${e.message}")
        }
    }

    suspend fun getChats(): List<ChatItem> {
        val (csrf, userId) = getCsrfAndId() ?: return emptyList()
        return try {
            val objects = "[{\"type\":\"chat_bookmarks\",\"id\":\"$userId\",\"tag\":\"00000000\",\"data\":false}]"
            val response = RetrofitInstance.api.runnerGet(cookie = getCookieString(), userAgent = userAgent, objects = objects, request = "false", csrfToken = csrf)
            updateSession(response)
            val jsonStr = readBodySilent(response)
            val json = JSONObject(jsonStr)
            val objectsArray = json.getJSONArray("objects")
            val list = mutableListOf<ChatItem>()
            for (i in 0 until objectsArray.length()) {
                val obj = objectsArray.getJSONObject(i)
                if (obj.getString("type") == "chat_bookmarks") {
                    val data = obj.getJSONObject("data")
                    val html = data.getString("html")
                    val doc = Jsoup.parse(html)
                    doc.select("a.contact-item").forEach { item ->
                        list.add(ChatItem(
                            id = item.attr("data-id"),
                            username = item.select("div.media-user-name").text(),
                            lastMessage = item.select("div.contact-item-message").text(),
                            isUnread = item.hasClass("unread"),
                            userId = userId,
                            date = item.select("div.contact-item-time").text()
                        ))
                    }
                }
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getChatHistory(nodeId: String): List<MessageItem> {
        return try {
            val (_, userId) = getCsrfAndId() ?: return emptyList()
            val response = RetrofitInstance.api.getChatHistory(cookie = getCookieString(), userAgent = userAgent, nodeId = nodeId)
            updateSession(response)
            val jsonStr = readBodySilent(response)
            if (jsonStr.isEmpty()) return emptyList()
            val json = JSONObject(jsonStr)
            if (!json.has("chat")) return emptyList()
            val messagesArray = json.getJSONObject("chat").getJSONArray("messages")
            val messages = mutableListOf<MessageItem>()
            for (i in 0 until messagesArray.length()) {
                val msgObj = messagesArray.getJSONObject(i)
                val html = msgObj.getString("html")
                val doc = Jsoup.parse(html)
                val msgNode = doc.select(".chat-msg-item").first()
                if (msgNode != null) {
                    val id = msgNode.attr("id")
                    val authorNode = msgNode.select(".media-user-name a")
                    val author = if (authorNode.isNotEmpty()) authorNode.text() else "FunPay"
                    val text = msgNode.select(".chat-msg-text").text()
                    val time = msgNode.select(".chat-msg-date").text()
                    val isMe = if (author.isEmpty() || author == "FunPay") false else {
                        msgNode.hasClass("message-own") || msgNode.hasClass("chat-msg-out")
                    }
                    messages.add(MessageItem(id, author, text, isMe, time))
                }
            }
            messages
        } catch (e: Exception) { emptyList() }
    }

    suspend fun checkAutoResponse(cachedChats: List<ChatItem>? = null) {
        if (!getSetting("auto_response") && !getSetting("auto_review_reply")) {
            return
        }

        try {
            val chats = cachedChats ?: getChats()
            val commands = getCommands()
            val (csrf, userId) = getCsrfAndId() ?: return

            val unreadChats = chats.filter { it.isUnread }

            for (chat in unreadChats) {
                val lastMessageText = chat.lastMessage.trim()
                if (lastMessageText.isEmpty()) continue

                val lowerText = lastMessageText.lowercase(Locale.getDefault())

                if (getSetting("auto_review_reply") &&
                    (lowerText.contains("написал отзыв к заказу") || lowerText.contains("изменил отзыв к заказу"))) {

                    val orderIdMatch = Regex("#([a-zA-Z0-9]+)").find(lastMessageText)
                    if (orderIdMatch != null) {
                        val orderId = orderIdMatch.groupValues[1]
                        LogManager.addLog("⭐ Обнаружен отзыв, заказ $orderId")
                        handleReview(orderId, chat.username, csrf, userId)
                    }
                }

                if (getSetting("auto_response")) {
                    val cmd = commands.find {
                        val trigger = it.trigger.trim().lowercase(Locale.getDefault())
                        if (it.exactMatch) lowerText == trigger else lowerText.contains(trigger)
                    }

                    if (cmd != null) {
                        LogManager.addLog("🎯 Триггер '${cmd.trigger}'")
                        sendMessage(chat.id, cmd.response)
                        kotlinx.coroutines.delay(1500)
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
            if (!chat.isUnread) continue

            val lastMsgLower = chat.lastMessage.lowercase()

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
            val response = RetrofitInstance.api.getOrder(orderId, getCookieString(), userAgent)
            updateSession(response)
            val html = readBodySilent(response)
            if (html.isEmpty()) return

            val doc = Jsoup.parse(html)

            val ratingElement = doc.select(".rating div").first()
            val className = ratingElement?.className() ?: ""
            val stars = className.filter { it.isDigit() }.toIntOrNull()

            if (stars == null) {
                LogManager.addLog("⚠️ Не удалось узнать оценку ($orderId)")
                return
            }

            var lotName = "Товар"
            val paramItems = doc.select(".param-item")
            for (item in paramItems) {
                val header = item.select("h5").text().lowercase()
                if (header.contains("краткое описание") || header.contains("short description")) {
                    lotName = item.select("div").text()
                    break
                }
            }
            if (lotName == "Товар") {
                val headerName = doc.select(".order-desc div").first()?.text()
                if (!headerName.isNullOrEmpty()) lotName = headerName
            }

            lotName = lotName.replace("Краткое описание ", "", ignoreCase = true)
                .replace("Short description ", "", ignoreCase = true)
                .trim()

            LogManager.addLog("⭐ Оценка: $stars, Лот: '$lotName'")

            val templates = getReviewTemplates()
            val template = templates.find { it.star == stars }

            if (template != null && template.enabled && template.text.isNotEmpty()) {
                val replyText = template.text
                    .replace("\$username", buyerName)
                    .replace("\$order_id", orderId)
                    .replace("\$lot_name", lotName)

                val replyResponse = RetrofitInstance.api.replyToReview(
                    cookie = getCookieString(),
                    userAgent = userAgent,
                    csrfToken = csrf,
                    orderId = orderId,
                    text = replyText,
                    rating = 5,
                    authorId = userId
                )

                val jsonStr = readBodySilent(replyResponse)
                val jsonResponse = try { JSONObject(jsonStr) } catch (e: Exception) { JSONObject() }

                val isSuccess = replyResponse.isSuccessful &&
                        !jsonResponse.optBoolean("error") &&
                        jsonResponse.optInt("error") != 1

                if (isSuccess) {
                    LogManager.addLog("✅ Ответ отправлен ($stars*)")
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
}