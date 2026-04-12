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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup as JsoupParser
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

data class SupportTicket(
    val id: String,
    val title: String,
    val status: String,
    val lastUpdate: String,
    val unreadCount: Int = 0
)

data class TicketAttachment(
    val name: String,
    val url: String,
    val isImage: Boolean
)

data class TicketComment(
    val author: String,
    val text: String,
    val htmlText: String = "",
    val timestamp: String,
    val isMyComment: Boolean,
    val attachments: List<TicketAttachment> = emptyList(),
    val avatarUrl: String = ""
)

data class TicketDetails(
    val id: String,
    val title: String,
    val status: String,
    val comments: List<TicketComment>,
    val additionalFields: Map<String, String> = emptyMap(),
    val createdAt: String = "",
    val responsible: String = "",
    val canReply: Boolean = true
)

data class TicketCategory(
    val id: String,
    val name: String
)

data class TicketField(
    val id: String,
    val name: String,
    val type: String,
    val required: Boolean,
    val options: List<TicketFieldOption> = emptyList(),
    val condition: String? = null,
    val defaultValue: String = ""
)

data class TicketFieldOption(
    val value: String,
    val text: String
)

class FunPaySupport(private val repository: FunPayRepository) {

    // Оригинальный клиент — не трогаем, он работает
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val baseUrl = "https://support.funpay.com"

    private var csrfToken: String? = null
    private var userId: String? = null
    private var allCookies = mutableMapOf<String, String>()

    fun getMyUsername(): String = repository.getActiveAccount()?.username ?: ""

    fun autoFillKnownFields(fields: List<TicketField>): Map<String, String> {
        val username = getMyUsername().ifEmpty { return emptyMap() }
        val result = mutableMapOf<String, String>()
        for (field in fields) {
            val label = field.name.lowercase().trim()
            if (label.contains("ваш ник") || label.contains("логин на funpay") ||
                label.contains("ник на funpay") || label.contains("your nickname") ||
                label.contains("your login on funpay")) {
                result[field.id] = username
            }
        }
        return result
    }

    // ── Оригинальные хелперы — не трогаем ────────────────────────────────────

    private fun updateCookies(response: Response) {
        response.headers("Set-Cookie").forEach { cookie ->
            val parts = cookie.split(";")[0].split("=", limit = 2)
            if (parts.size == 2) allCookies[parts[0]] = parts[1]
        }
    }

    private fun getCookieString(): String {
        val gk = repository.getGoldenKey() ?: ""
        val funpaySession = repository.getPhpSessionId()
        val cookieMap = mutableMapOf<String, String>()
        cookieMap["golden_key"] = gk
        if (funpaySession.isNotEmpty()) cookieMap["PHPSESSID"] = funpaySession
        allCookies.forEach { (key, value) -> cookieMap[key] = value }
        return cookieMap.map { "${it.key}=${it.value}" }.joinToString("; ")
    }

    private suspend fun followRedirects(initialRequest: Request, maxRedirects: Int = 5): Response {
        var request = initialRequest
        var response: Response? = null
        var redirectCount = 0
        while (redirectCount < maxRedirects) {
            response?.close()
            response = client.newCall(request).execute()
            updateCookies(response)
            if (response.code !in 300..399) return response
            val location = response.header("Location") ?: return response
            redirectCount++
            val newUrl = when {
                location.startsWith("http") -> location
                location.startsWith("/") -> "${request.url.scheme}://${request.url.host}$location"
                else -> "${request.url.scheme}://${request.url.host}/${location}"
            }
            request = Request.Builder()
                .url(newUrl)
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
        }
        return response ?: throw Exception("No response after redirects")
    }

    // ── init — оригинальный ───────────────────────────────────────────────────

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        try {
            allCookies.clear()
            val request = Request.Builder()
                .url("https://funpay.com/support/sso?return_to=%2Ftickets")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val response = followRedirects(request, maxRedirects = 10)
            val html = response.body?.string() ?: ""
            if (response.code == 429) { response.close(); return@withContext false }
            if (html.contains("data-app-config")) {
                val appConfig = Jsoup.parse(html).select("body[data-app-config]").first()?.attr("data-app-config")
                if (appConfig != null) {
                    val json = JSONObject(appConfig)
                    csrfToken = json.optString("csrfToken")
                    userId = json.optString("userId")
                    response.close()
                    return@withContext true
                }
            }
            response.close()
            false
        } catch (e: Exception) { false }
    }

    // ── Категории ─────────────────────────────────────────────────────────────

    suspend fun getCategories(): List<TicketCategory> = withContext(Dispatchers.IO) {
        try {
            val response = followRedirects(Request.Builder()
                .url("$baseUrl/tickets/new")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build())
            val html = response.body?.string() ?: ""
            response.close()
            val categories = mutableListOf<TicketCategory>()
            Jsoup.parse(html).select("select#ticket_select_form option").forEach { opt ->
                val v = opt.attr("value"); val t = opt.text().trim()
                if (v.isNotEmpty() && t != "Выберите вариант...") categories.add(TicketCategory(v, t))
            }
            categories
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getCategoryFields(categoryId: String): List<TicketField> = withContext(Dispatchers.IO) {
        try {
            val response = followRedirects(Request.Builder()
                .url("$baseUrl/tickets/new/$categoryId")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build())
            val html = response.body?.string() ?: ""
            response.close()
            val doc = Jsoup.parse(html)
            val fields = mutableListOf<TicketField>()
            doc.select("input[name^='ticket[fields]'], select[name^='ticket[fields]'], textarea[name^='ticket[comment]']").forEach { element ->
                val fieldName = element.attr("name")
                if (fieldName.contains("_token") || fieldName.contains("attachments")) return@forEach
                val container = element.closest(".mb-3") ?: element.closest("fieldset") ?: return@forEach
                val labelElem = container.select("label[for='${element.attr("id")}']").firstOrNull()
                    ?: container.select("label").firstOrNull()
                    ?: container.select("legend").firstOrNull()
                val labelText = labelElem?.text()?.replace("*", "")?.trim() ?: ""
                val isRequired = labelElem?.hasClass("required") ?: false || labelText.contains("*")
                val condition = container.attr("data-condition")
                val lowerLabel = labelText.lowercase()
                val defaultVal = if (lowerLabel.contains("ник") || lowerLabel.contains("логин") || lowerLabel.contains("nickname")) getMyUsername() else ""
                when {
                    element.tagName() == "select" -> {
                        val options = element.select("option").mapNotNull { opt ->
                            val v = opt.attr("value"); val t = opt.text().trim()
                            if (v.isNotEmpty() && !t.contains("Выберите")) TicketFieldOption(v, t) else null
                        }
                        fields.add(TicketField(fieldName, labelText, "select", isRequired, options, condition.ifEmpty { null }, defaultVal))
                    }
                    element.attr("type") == "radio" -> {
                        if (fields.none { it.id == fieldName }) {
                            val fieldset = element.closest("fieldset")
                            val legend = fieldset?.select("legend")?.first()?.text()?.replace("*", "")?.trim() ?: labelText
                            val allRadios = fieldset?.select("input[name='$fieldName']") ?: listOf(element)
                            val options = allRadios.mapNotNull { radio ->
                                val radioLabel = fieldset?.select("label[for='${radio.attr("id")}']")?.first()?.text()?.replace("*", "")?.trim()
                                val radioValue = radio.attr("value")
                                if (radioLabel != null && radioValue.isNotEmpty()) TicketFieldOption(radioValue, radioLabel) else null
                            }
                            fields.add(TicketField(fieldName, legend, "radio", isRequired, options, condition.ifEmpty { null }, defaultVal))
                        }
                    }
                    element.tagName() == "textarea" -> {
                        if (element.hasAttr("data-controller")) return@forEach
                        val name = if (fieldName.contains("comment")) "Сообщение" else labelText
                        fields.add(TicketField(fieldName, name, "textarea", false, emptyList(), condition.ifEmpty { null }, defaultVal))
                    }
                    else -> fields.add(TicketField(fieldName, labelText, "text", isRequired, emptyList(), condition.ifEmpty { null }, defaultVal))
                }
            }
            fields
        } catch (e: Exception) { emptyList() }
    }

    // ── Список заявок — исправленный парсинг статуса ─────────────────────────

    suspend fun getTicketsList(): List<SupportTicket> = withContext(Dispatchers.IO) {
        try {
            val tickets = mutableListOf<SupportTicket>()
            var page = 1
            while (true) {
                val response = followRedirects(Request.Builder()
                    .url("$baseUrl/tickets?status=all&order=last_answered&page=$page")
                    .header("Cookie", getCookieString())
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build())
                if (response.code == 429) { response.close(); break }
                val html = response.body?.string() ?: ""
                response.close()
                val doc = Jsoup.parse(html)
                doc.select("a.ticket-item").forEach { item ->
                    val id = item.attr("href").trimEnd('/').split("/").last()
                    val titleText = item.select(".col-12.mt-2").text().trim()
                        .ifEmpty { item.select(".col-12").text().trim() }
                    val dateText = item.select(".text-secondary").text().trim()
                    val badge = item.select(".badge").first()
                    val status = when {
                        badge == null -> "Неизвестен"
                        badge.hasClass("bg-danger")  -> "Открыт"
                        badge.hasClass("bg-warning") -> "В ожидании"
                        badge.hasClass("bg-success") -> "Решена"
                        badge.hasClass("bg-secondary") -> "Закрыт"
                        else -> badge.text().trim().ifEmpty { "Неизвестен" }
                    }
                    if (id.isNotBlank()) tickets.add(SupportTicket(id, titleText, status, dateText, 0))
                }
                if (doc.select("a.page-link[rel='next']").isEmpty()) break
                page++
            }
            tickets
        } catch (e: Exception) { emptyList() }
    }

    // ── Детали заявки ─────────────────────────────────────────────────────────

    suspend fun getTicketDetails(ticketId: String): TicketDetails? = withContext(Dispatchers.IO) {
        try {
            val response = followRedirects(Request.Builder()
                .url("$baseUrl/tickets/$ticketId")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build())
            val html = response.body?.string() ?: ""
            response.close()
            val doc = Jsoup.parse(html)
            val title = doc.select(".breadcrumb-item.active").text().replace("Заявка #$ticketId", "").trim()
            val statusBadge = doc.select(".ticket-info-panel .badge").first()
            val status = when {
                statusBadge == null -> if (doc.select(".btn-outline-secondary:contains(Закрыть)").isNotEmpty()) "Открыт" else "Закрыт"
                statusBadge.hasClass("bg-danger")  -> "Открыт"
                statusBadge.hasClass("bg-warning") -> "В ожидании"
                statusBadge.hasClass("bg-success") -> "Решена"
                else -> "Закрыт"
            }
            val comments = mutableListOf<TicketComment>()
            doc.select(".ticket-comment").forEach { comment ->
                val author = comment.select(".username").first()?.text() ?: "Неизвестно"
                val commentTextEl = comment.select(".comment-text").first()
                val htmlText = commentTextEl?.html() ?: ""
                val text = commentTextEl?.text() ?: ""
                val timestamp = (comment.select(".comment-username span:nth-child(2)").first()
                    ?: comment.select(".d-sm-none span:nth-child(2)").first())?.text()?.trim() ?: ""
                val avatarStyle = comment.select(".comment-avatar").attr("style")
                val avatarRaw = Regex("""url\(['\"]?([^'\"\\)]+)['\"]?\)""").find(avatarStyle)
                    ?.groupValues?.getOrNull(1)?.trim() ?: ""
                val avatarUrl = when {
                    avatarRaw.isEmpty() -> ""
                    avatarRaw.startsWith("http") -> avatarRaw
                    else -> "https://funpay.com$avatarRaw"
                }
                val isMyComment = userId?.let { uid -> avatarStyle.contains("/$uid") || avatarStyle.contains("/${uid}.") } == true
                val attachments = comment.select(".attachment-item").mapNotNull { att ->
                    val link = att.select("a.attachment-link").first() ?: return@mapNotNull null
                    val url = link.attr("href").let { if (it.startsWith("http")) it else "$baseUrl$it" }
                    val name = link.text().trim().ifEmpty { link.attr("data-bs-title") }
                    val isImage = att.select(".bi-file-earmark-image").isNotEmpty() ||
                            url.lowercase().let { u -> u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png") || u.endsWith(".gif") || u.endsWith(".webp") }
                    TicketAttachment(name, url, isImage)
                }
                comments.add(TicketComment(author, text, htmlText, timestamp, isMyComment, attachments, avatarUrl))
            }
            val additionalFields = mutableMapOf<String, String>()
            var createdAt = ""
            var responsible = ""
            doc.select(".ticket-info-panel .row.mb-3, .ticket-info-panel .mb-3").forEach { row ->
                val key = row.select(".fw-semibold").first()?.text()?.trim() ?: return@forEach
                val value = when {
                    row.select(".badge").isNotEmpty() -> row.select(".badge").text().trim()
                    else -> row.select(".col-xl-8, .col-sm-8, div:not(.fw-semibold)").last()?.text()?.trim() ?: ""
                }
                if (key.isEmpty() || value.isEmpty()) return@forEach
                when (key) {
                    "Статус" -> {}
                    "Создана" -> createdAt = value
                    "Ответственный" -> responsible = value
                    else -> additionalFields[key] = value
                }
            }
            val canReply = doc.select("textarea[name*='comment'], form[action*='comment'] textarea, textarea.form-control").isNotEmpty()
            TicketDetails(ticketId, title, status, comments, additionalFields, createdAt, responsible, canReply)
        } catch (e: Exception) { null }
    }

    // ── Создание заявки — оригинальный ───────────────────────────────────────

    suspend fun createTicket(categoryId: String, fieldValues: Map<String, String>, message: String): String? = withContext(Dispatchers.IO) {
        try {
            val detailsResponse = followRedirects(Request.Builder()
                .url("$baseUrl/tickets/new/$categoryId")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build())
            val html = detailsResponse.body?.string() ?: ""
            val token = Jsoup.parse(html).select("input[name='ticket[_token]']").first()?.attr("value") ?: csrfToken ?: ""
            detailsResponse.close()
            val formBuilder = FormBody.Builder()
            fieldValues.forEach { (key, value) -> if (value.isNotEmpty()) formBuilder.add(key, value) }
            if (message.isNotBlank()) formBuilder.add("ticket[comment][body_html]", "<p>$message</p>")
            formBuilder.add("ticket[comment][attachments]", "")
            formBuilder.add("ticket[_token]", token)
            val response = followRedirects(Request.Builder()
                .url("$baseUrl/tickets/create/$categoryId")
                .post(formBuilder.build())
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .build())
            val body = response.body?.string() ?: ""
            response.close()
            when {
                response.code == 200 && body.contains("action") -> {
                    try { JSONObject(body).optJSONObject("action")?.optString("url")?.split("/")?.last() }
                    catch (e: Exception) { null }
                }
                response.code in 400..499 -> {
                    try {
                        val msg = JSONObject(body).optString("error", "Ошибка клиента")
                        throw Exception(msg)
                    } catch (e: Exception) { throw Exception("Ошибка при создании заявки. Код: ${response.code}") }
                }
                response.code == 500 -> throw Exception("Ошибка сервера. Попробуйте позже.")
                else -> throw Exception("Неожиданный ответ сервера. Код: ${response.code}")
            }
        } catch (e: Exception) { throw e }
    }

    // ── Комментарий — теперь с парсингом ошибок ──────────────────────────────────
    suspend fun addComment(ticketId: String, message: String, attachmentIds: List<String> = emptyList()): Boolean = withContext(Dispatchers.IO) {
        try {
            val detailsResponse = followRedirects(Request.Builder()
                .url("$baseUrl/tickets/$ticketId")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build())
            val html = detailsResponse.body?.string() ?: ""
            detailsResponse.close()
            val token = JsoupParser.parse(html).select("input[name='add_comment[_token]']").first()?.attr("value")
                ?: throw Exception("Не удалось получить токен формы (попробуйте обновить страницу)")

            val response = followRedirects(Request.Builder()
                .url("$baseUrl/tickets/$ticketId/comments/create")
                .post(FormBody.Builder()
                    .add("add_comment[comment][body_html]", "<p>$message</p>")
                    .add("add_comment[comment][attachments]", attachmentIds.joinToString(","))
                    .add("add_comment[_token]", token)
                    .build())
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$baseUrl/tickets/$ticketId")
                .build())

            val body = response.body?.string() ?: ""
            val code = response.code
            response.close()

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val json = JSONObject(body)
                    json.optString("error").ifEmpty { json.optString("message") }
                } catch (e: Exception) { "Ошибка сервера ($code)" }
                throw Exception(errorMsg.ifEmpty { "Неизвестная ошибка сервера" })
            }

            return@withContext true
        } catch (e: Exception) {
            throw e
        }
    }

    // ── Загрузка вложения — ИСПРАВЛЕН 401 (добавлен csrf_token в тело) ──────────────
    suspend fun uploadAttachment(ticketId: String, fileName: String, mimeType: String, data: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            // Если токена нет, проходим SSO
            if (csrfToken == null) init()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, data.toRequestBody(mimeType.toMediaTypeOrNull()))
                .addFormDataPart("csrf_token", csrfToken ?: "") // ОБЯЗАТЕЛЬНО для саппорта
                .build()

            val response = client.newCall(Request.Builder()
                .url("$baseUrl/attachment/upload")
                .post(requestBody)
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Referer", "$baseUrl/tickets/$ticketId")
                .build()
            ).execute()

            updateCookies(response)
            val body = response.body?.string() ?: ""
            val code = response.code
            response.close()

            if (code == 401 || code == 403) {
                if (init()) return@withContext uploadAttachment(ticketId, fileName, mimeType, data)
                throw Exception("Ошибка авторизации (401)")
            }

            if (!response.isSuccessful) {
                val errorMsg = try { JSONObject(body).optString("error") } catch(e: Exception) { "Ошибка $code" }
                throw Exception(errorMsg.ifEmpty { "Ошибка при загрузке файла" })
            }

            JSONObject(body).optJSONObject("attachment")?.optInt("id")?.toString()
        } catch (e: Exception) {
            throw e
        }
    }

    // ── Закрытие заявки — оригинальный ───────────────────────────────────────

    suspend fun closeTicket(ticketId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val detailsResponse = followRedirects(Request.Builder()
                .url("$baseUrl/tickets/$ticketId")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build())
            val html = detailsResponse.body?.string() ?: ""
            detailsResponse.close()
            val doc = Jsoup.parse(html)
            var token: String? = doc.select("body[data-app-config]").first()?.attr("data-app-config")
                ?.let { try { JSONObject(it).optString("csrfToken").ifEmpty { null } } catch (e: Exception) { null } }
            if (token == null) token = doc.select("input[name='close_ticket[_token]']").first()?.attr("value")
            if (token == null) return@withContext false
            val response = followRedirects(Request.Builder()
                .url("$baseUrl/tickets/$ticketId/close")
                .post(FormBody.Builder().add("csrf_token", token).build())
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .build())
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) { false }
    }

    // ── Настройки уведомлений ─────────────────────────────────────────────────

    suspend fun getNotificationSettings(): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val response = followRedirects(Request.Builder()
                .url("$baseUrl/settings")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build())
            val html = response.body?.string() ?: ""
            response.close()
            val doc = Jsoup.parse(html)
            val checked = doc.select("input[name='subscription[types_1][]']").first()?.hasAttr("checked") ?: false
            val token = doc.select("input[name='subscription[_token]']").first()?.attr("value")
            Pair(checked, token)
        } catch (e: Exception) { Pair(false, null) }
    }

    suspend fun saveNotificationSettings(enabled: Boolean, token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val builder = FormBody.Builder()
            if (enabled) builder.add("subscription[types_1][]", "0")
            builder.add("subscription[_token]", token)
            val response = client.newCall(Request.Builder()
                .url("$baseUrl/settings/save")
                .post(builder.build())
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$baseUrl/settings")
                .build()
            ).execute()
            updateCookies(response)
            val body = response.body?.string() ?: ""
            response.close()
            if (!response.isSuccessful) return@withContext false
            JSONObject(body).optJSONObject("action")?.optString("message")?.isNotEmpty() == true
        } catch (e: Exception) { false }
    }
}