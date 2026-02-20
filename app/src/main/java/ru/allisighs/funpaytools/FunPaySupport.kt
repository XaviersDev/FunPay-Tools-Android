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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

data class SupportTicket(
    val id: String,
    val title: String,
    val status: String,
    val lastUpdate: String,
    val unreadCount: Int = 0
)

data class TicketComment(
    val author: String,
    val text: String,
    val timestamp: String,
    val isMyComment: Boolean
)

data class TicketDetails(
    val id: String,
    val title: String,
    val status: String,
    val comments: List<TicketComment>,
    val additionalFields: Map<String, String> = emptyMap()
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
    val condition: String? = null
)

data class TicketFieldOption(
    val value: String,
    val text: String
)

class FunPaySupport(private val repository: FunPayRepository) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val baseUrl = "https://support.funpay.com"

    private var csrfToken: String? = null
    private var userId: String? = null
    private var allCookies = mutableMapOf<String, String>()

    private fun updateCookies(response: Response) {
        response.headers("Set-Cookie").forEach { cookie ->
            val parts = cookie.split(";")[0].split("=", limit = 2)
            if (parts.size == 2) {
                allCookies[parts[0]] = parts[1]
                
            }
        }
    }

    private fun getCookieString(): String {
        val gk = repository.getGoldenKey() ?: ""
        val funpaySession = repository.getPhpSessionId()

        val cookieMap = mutableMapOf<String, String>()
        cookieMap["golden_key"] = gk
        if (funpaySession.isNotEmpty()) {
            cookieMap["PHPSESSID"] = funpaySession
        }

        allCookies.forEach { (key, value) ->
            cookieMap[key] = value
        }

        val result = cookieMap.map { "${it.key}=${it.value}" }.joinToString("; ")
        
        return result
    }

    private suspend fun followRedirects(initialRequest: Request, maxRedirects: Int = 5): Response {
        var request = initialRequest
        var response: Response? = null
        var redirectCount = 0

        while (redirectCount < maxRedirects) {
            response?.close()
            response = client.newCall(request).execute()

            
            updateCookies(response)

            if (response.code !in 300..399) {
                return response
            }

            val location = response.header("Location")
            if (location == null) {
                
                return response
            }

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
            

            if (response.code == 429) {
                
                return@withContext false
            }

            if (html.contains("data-app-config")) {
                val doc = Jsoup.parse(html)
                val appConfig = doc.select("body[data-app-config]").first()?.attr("data-app-config")

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
        } catch (e: Exception) {
            
            
            e.stackTrace.take(5).forEach {
                
            }
            false
        }
    }

    suspend fun getCategories(): List<TicketCategory> = withContext(Dispatchers.IO) {
        try {
            

            val request = Request.Builder()
                .url("$baseUrl/tickets/new")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = followRedirects(request)
            val html = response.body?.string() ?: ""

            val doc = Jsoup.parse(html)
            val categories = mutableListOf<TicketCategory>()

            doc.select("select#ticket_select_form option").forEach { option ->
                val value = option.attr("value")
                val text = option.text().trim()

                if (value.isNotEmpty() && text != "Выберите вариант...") {
                    categories.add(TicketCategory(value, text))
                    
                }
            }

            
            response.close()
            categories
        } catch (e: Exception) {
            
            emptyList()
        }
    }

    suspend fun getCategoryFields(categoryId: String): List<TicketField> = withContext(Dispatchers.IO) {
        try {
            

            val request = Request.Builder()
                .url("$baseUrl/tickets/new/$categoryId")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = followRedirects(request)
            val html = response.body?.string() ?: ""

            val doc = Jsoup.parse(html)
            val fields = mutableListOf<TicketField>()

            // Парсим все input/select/textarea поля
            doc.select("input[name^='ticket[fields]'], select[name^='ticket[fields]'], textarea[name^='ticket[comment]']").forEach { element ->
                val fieldName = element.attr("name")

                // Пропускаем скрытые поля и attachment
                if (fieldName.contains("_token") || fieldName.contains("attachments")) {
                    return@forEach
                }

                // Находим родительский контейнер
                val container = element.closest(".mb-3") ?: element.closest("fieldset") ?: return@forEach

                // Получаем label
                val labelElem = container.select("label[for='${element.attr("id")}']").firstOrNull()
                    ?: container.select("label").firstOrNull()
                    ?: container.select("legend").firstOrNull()

                val labelText = labelElem?.text()?.replace("*", "")?.trim() ?: ""
                val isRequired = labelElem?.hasClass("required") ?: false || labelText.contains("*")

                // Проверяем условие отображения
                val condition = container.attr("data-condition")
                val hasCondition = condition.isNotEmpty()

                when {
                    element.tagName() == "select" -> {
                        val options = element.select("option").mapNotNull { opt ->
                            val value = opt.attr("value")
                            val text = opt.text().trim()
                            if (value.isNotEmpty() && !text.contains("Выберите")) {
                                TicketFieldOption(value, text)
                            } else null
                        }

                        fields.add(TicketField(
                            fieldName,
                            labelText,
                            "select",
                            isRequired,
                            options,
                            if (hasCondition) condition else null
                        ))
                        
                    }

                    element.attr("type") == "radio" -> {
                        // Проверяем, не добавили ли мы уже эту radio группу
                        if (fields.none { it.id == fieldName }) {
                            val fieldset = element.closest("fieldset")
                            val legend = fieldset?.select("legend")?.first()?.text()?.replace("*", "")?.trim() ?: labelText
                            val allRadios = fieldset?.select("input[name='$fieldName']") ?: listOf(element)

                            val options = allRadios.mapNotNull { radio ->
                                val radioId = radio.attr("id")
                                val radioLabel = fieldset?.select("label[for='$radioId']")?.first()?.text()?.replace("*", "")?.trim()
                                val radioValue = radio.attr("value")

                                if (radioLabel != null && radioValue.isNotEmpty()) {
                                    TicketFieldOption(radioValue, radioLabel)
                                } else null
                            }

                            fields.add(TicketField(
                                fieldName,
                                legend,
                                "radio",
                                isRequired,
                                options,
                                if (hasCondition) condition else null
                            ))
                            
                        }
                    }

                    element.tagName() == "textarea" -> {
                        // Пропускаем summernote редактор
                        if (element.hasAttr("data-controller")) {
                            return@forEach
                        }

                        val name = if (fieldName.contains("comment")) "Сообщение" else labelText
                        fields.add(TicketField(
                            fieldName,
                            name,
                            "textarea",
                            false, // Сообщение необязательно
                            emptyList(),
                            if (hasCondition) condition else null
                        ))
                        
                    }

                    else -> {
                        // Обычный input
                        fields.add(TicketField(
                            fieldName,
                            labelText,
                            "text",
                            isRequired,
                            emptyList(),
                            if (hasCondition) condition else null
                        ))
                        
                    }
                }
            }

            
            response.close()
            fields
        } catch (e: Exception) {
            
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getTicketsList(): List<SupportTicket> = withContext(Dispatchers.IO) {
        try {
            

            val request = Request.Builder()
                .url("$baseUrl/tickets")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = followRedirects(request)
            val html = response.body?.string() ?: ""

            

            if (response.code == 429) {
                
                response.close()
                return@withContext emptyList()
            }

            val doc = Jsoup.parse(html)
            val tickets = mutableListOf<SupportTicket>()

            doc.select("a.ticket-item").forEach { item ->
                val href = item.attr("href")
                val id = href.split("/").last()
                val titleText = item.select(".col-12").text().trim()
                val dateText = item.select(".text-secondary").text().trim()
                val statusBadge = item.select(".badge")

                val status = when {
                    statusBadge.hasClass("bg-danger") -> "Открыт"
                    statusBadge.hasClass("bg-success") -> "Закрыт"
                    else -> "Неизвестен"
                }

                tickets.add(SupportTicket(id, titleText, status, dateText, 0))
                
            }

            
            response.close()
            tickets
        } catch (e: Exception) {
            
            emptyList()
        }
    }

    suspend fun getTicketDetails(ticketId: String): TicketDetails? = withContext(Dispatchers.IO) {
        try {
            

            val request = Request.Builder()
                .url("$baseUrl/tickets/$ticketId")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = followRedirects(request)
            val html = response.body?.string() ?: ""

            val doc = Jsoup.parse(html)

            // Парсим тему заявки из breadcrumb
            val breadcrumbActive = doc.select(".breadcrumb-item.active").text()
            val title = breadcrumbActive.replace("Заявка #$ticketId", "").trim()

            val status = if (doc.select(".btn-outline-secondary:contains(Закрыть)").isNotEmpty()) "Открыт" else "Закрыт"

            val comments = mutableListOf<TicketComment>()
            doc.select(".ticket-comment").forEach { comment ->
                val author = comment.select(".username").first()?.text() ?: "Неизвестно"
                val text = comment.select(".comment-text").text()
                val timestamp = comment.select(".comment-username span:nth-child(2)").text()
                val avatarStyle = comment.select(".comment-avatar").attr("style")
                val isMyComment = userId?.let { avatarStyle.contains(it) } == true

                comments.add(TicketComment(author, text, timestamp, isMyComment))
            }

            val additionalFields = mutableMapOf<String, String>()
            doc.select(".ticket-info-panel .row.mb-3").forEach { row ->
                val key = row.select(".fw-semibold").first()?.text()?.trim()
                val value = row.select(".col-xl-8, .col-sm-8").first()?.text()?.trim()

                if (key != null && value != null && key.isNotEmpty() && value.isNotEmpty()) {
                    // Фильтруем стандартные поля
                    if (key !in listOf("Статус", "Создана", "Ответственный")) {
                        additionalFields[key] = value
                        
                    }
                }
            }

            
            response.close()
            TicketDetails(ticketId, title, status, comments, additionalFields)
        } catch (e: Exception) {
            
            null
        }
    }

    suspend fun createTicket(
        categoryId: String,
        fieldValues: Map<String, String>,
        message: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            

            // Получаем CSRF токен со страницы формы
            val detailsRequest = Request.Builder()
                .url("$baseUrl/tickets/new/$categoryId")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val detailsResponse = followRedirects(detailsRequest)
            val html = detailsResponse.body?.string() ?: ""

            val doc = Jsoup.parse(html)
            val tokenInput = doc.select("input[name='ticket[_token]']").first()
            val token = tokenInput?.attr("value") ?: csrfToken ?: ""

            detailsResponse.close()

            val formBuilder = FormBody.Builder()

            // Добавляем значения полей
            fieldValues.forEach { (key, value) ->
                if (value.isNotEmpty()) {
                    formBuilder.add(key, value)
                    
                }
            }

            // Добавляем комментарий
            if (message.isNotBlank()) {
                formBuilder.add("ticket[comment][body_html]", "<p>$message</p>")
                
            }

            formBuilder.add("ticket[comment][attachments]", "")
            formBuilder.add("ticket[_token]", token)

            val request = Request.Builder()
                .url("$baseUrl/tickets/create/$categoryId")
                .post(formBuilder.build())
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = followRedirects(request)
            val body = response.body?.string() ?: ""

            

            val result = when {
                response.code == 200 && body.contains("action") -> {
                    try {
                        val json = JSONObject(body)
                        val ticketId = json.optJSONObject("action")?.optString("url")?.split("/")?.last()
                        if (ticketId != null) {
                            
                        }
                        ticketId
                    } catch (e: Exception) {
                        
                        null
                    }
                }
                response.code in 400..499 -> {
                    try {
                        val json = JSONObject(body)
                        val errorMsg = json.optString("error", "Ошибка клиента")
                        
                        throw Exception(errorMsg)
                    } catch (e: Exception) {
                        if (e.message?.startsWith("Запросы") == true || e.message?.contains("Ошибка") == true) {
                            throw e
                        }
                        
                        throw Exception("Ошибка при создании заявки. Код: ${response.code}")
                    }
                }
                response.code == 500 -> {
                    
                    throw Exception("Ошибка сервера. Попробуйте позже.")
                }
                else -> {
                    
                    throw Exception("Неожиданный ответ сервера. Код: ${response.code}")
                }
            }

            response.close()
            result
        } catch (e: Exception) {
            
            throw e
        }
    }

    suspend fun addComment(ticketId: String, message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            

            val detailsRequest = Request.Builder()
                .url("$baseUrl/tickets/$ticketId")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val detailsResponse = followRedirects(detailsRequest)
            val html = detailsResponse.body?.string() ?: ""

            val doc = Jsoup.parse(html)
            val tokenInput = doc.select("input[name='add_comment[_token]']").first()
            val token = tokenInput?.attr("value")

            detailsResponse.close()

            if (token == null) return@withContext false

            val formBody = FormBody.Builder()
                .add("add_comment[comment][body_html]", "<p>$message</p>")
                .add("add_comment[comment][attachments]", "")
                .add("add_comment[_token]", token)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/tickets/$ticketId/comments/create")
                .post(formBody)
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = followRedirects(request)
            

            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) {
            
            false
        }
    }

    suspend fun closeTicket(ticketId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            

            val detailsRequest = Request.Builder()
                .url("$baseUrl/tickets/$ticketId")
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val detailsResponse = followRedirects(detailsRequest)
            val html = detailsResponse.body?.string() ?: ""

            val doc = Jsoup.parse(html)

            // Ищем правильный CSRF токен из модального окна закрытия
            val closeModal = doc.select("#close-ticket-modal")
            val confirmButton = closeModal.select("button.confirm-button[data-controller='ajax-form']")

            // Получаем токен из общего app-config
            val appConfig = doc.select("body[data-app-config]").first()?.attr("data-app-config")
            var token: String? = null

            if (appConfig != null) {
                try {
                    val json = JSONObject(appConfig)
                    token = json.optString("csrfToken")
                    
                } catch (e: Exception) {
                    
                }
            }

            // Fallback: пробуем найти токен в форме
            if (token == null) {
                val tokenInput = doc.select("input[name='close_ticket[_token]']").first()
                token = tokenInput?.attr("value")
                
            }

            detailsResponse.close()

            if (token == null) {
                
                return@withContext false
            }

            

            val formBody = FormBody.Builder()
                .add("csrf_token", token)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/tickets/$ticketId/close")
                .post(formBody)
                .header("Cookie", getCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = followRedirects(request)
            val responseBody = response.body?.string() ?: ""

            
            if (responseBody.isNotEmpty()) {
                
            }

            val success = response.isSuccessful
            response.close()

            if (success) {
                
            } else {
                
            }

            success
        } catch (e: Exception) {
            
            e.printStackTrace()
            false
        }
    }
}