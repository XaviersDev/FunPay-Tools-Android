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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
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

const val APP_VERSION = "1.0"

data class ChatItem(val id: String, val username: String, val lastMessage: String, val isUnread: Boolean, val userId: String, val date: String)
data class MessageItem(val id: String, val author: String, val text: String, val isMe: Boolean, val time: String, val imageUrl: String? = null)
data class AutoResponseCommand(val trigger: String, val response: String, val exactMatch: Boolean)
data class ReviewReplyTemplate(val star: Int, val text: String, val enabled: Boolean)
data class GreetingSettings(
    val enabled: Boolean,
    val text: String,
    val cooldownHours: Int,
    val ignoreSystemMessages: Boolean
)
data class UpdateInfo(val hasUpdate: Boolean, val newVersion: String, val htmlUrl: String)

object LogManager {
    private val _logs = MutableStateFlow<List<String>>(listOf("Система готова. v$APP_VERSION"))
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

class FunPayRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("fp_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
    private var phpsessid: String = ""
    private val nodeToGameMap = mutableMapOf<Int, Int>()

    var lastOutgoingMessage: String = ""

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

                val tagName = json.getString("tag_name").replace("v", "")
                val htmlUrl = json.getString("html_url")

                if (tagName != APP_VERSION) {
                    UpdateInfo(true, tagName, htmlUrl)
                } else {
                    UpdateInfo(false, tagName, htmlUrl)
                }
            } catch (e: Exception) {
                LogManager.addLog("⚠️ Ошибка проверки обновлений: ${e.message}")
                null
            }
        }
    }

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
            } else {
                null
            }
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка загрузки фото: ${e.message}")
            null
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
                lastOutgoingMessage = text
            }
            success
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка отправки: ${e.message}")
            false
        }
    }

    suspend fun rewriteMessage(text: String, contextHistory: String): String? {
        return try {
            // МЕНЯЕМ ПОДХОД: Инструкции отправляем не в System (сервер его может игнорировать),
            // а заворачиваем прямо в User Message, как это сделано в расширении FunPay Tools.

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
                return json.optString("response").trim()
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

    suspend fun getChatHistory(chatId: String): List<MessageItem> {
        val (csrf, userId) = getCsrfAndId() ?: return emptyList()

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
                LogManager.addLog("⚠️ Не удалось определить nodeName для $chatId")
            }
        }

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

            val response = RetrofitInstance.api.runnerGet(
                cookie = getCookieString(),
                userAgent = userAgent,
                objects = objectsPayload,
                request = "false",
                csrfToken = csrf
            )

            updateSession(response)
            val jsonStr = readBodySilent(response)
            val json = JSONObject(jsonStr)

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

                            val textElement = doc.select("div.chat-msg-text").first()
                            var text = textElement?.text() ?: ""

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
                                messages.add(MessageItem(id, author, text, isMe, time, imageUrl))
                            }
                        } catch (e: Exception) { }
                    }
                    return messages
                }
            }
        } catch (e: Exception) {
            LogManager.addLog("❌ Ошибка Runner: ${e.message}")
        }

        return emptyList()
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

            LogManager.addLog("⭐ Оценка: $stars, Лот: '$lotName'")

            val templates = getReviewTemplates()
            val template = templates.find { it.star == stars }

            if (template != null && template.enabled && template.text.isNotEmpty()) {
                val replyText = template.text
                    .replace("\$username", buyerName)
                    .replace("\$order_id", orderId)
                    .replace("\$lot_name", lotName)

                LogManager.addLog("📤 Ответ на отзыв: '$replyText'")

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