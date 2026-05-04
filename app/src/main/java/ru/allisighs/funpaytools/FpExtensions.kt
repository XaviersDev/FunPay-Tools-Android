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

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/* -------------------------------------------------------------------------
 * 1. ПЕРЕМЕННЫЕ / ПЛЕЙСХОЛДЕРЫ (как в Cardinal).
 *
 * Использование:
 *    val text = FpPlaceholders.applyChat(raw, chat)
 *    val text = FpPlaceholders.applyOrder(raw, orderCtx)
 *    val text = FpPlaceholders.applyCombined(raw, chat, orderCtx)
 *
 * Поддерживаются $username, $chat_name, $chat_id, $date, $time,
 * $full_date, $full_time, $date_text, $order_id, $order_link,
 * $lot_name, $order_desc, $order_title, $order_params,
 * $order_desc_and_params, $order_desc_or_params, $category,
 * $category_fullname, $game, $message_text.
 *
 * Замечание: если плейсхолдер не имеет значения (например, $order_id при
 * обычной команде автоответа), он остаётся пустой строкой — так же, как в
 * Cardinal, чтобы текст не ломался в UI.
 * -------------------------------------------------------------------------
 */
object FpPlaceholders {

    /** Контекст заказа для подстановки. Все поля опциональны. */
    data class OrderCtx(
        val orderId: String? = null,
        val lotName: String? = null,
        val orderDesc: String? = null,
        val orderParams: String? = null,
        val category: String? = null,
        val categoryFullname: String? = null,
        val game: String? = null,
        val buyerUsername: String? = null
    )

    private fun monthNameRu(month: Int): String = when (month) {
        Calendar.JANUARY -> "января"
        Calendar.FEBRUARY -> "февраля"
        Calendar.MARCH -> "марта"
        Calendar.APRIL -> "апреля"
        Calendar.MAY -> "мая"
        Calendar.JUNE -> "июня"
        Calendar.JULY -> "июля"
        Calendar.AUGUST -> "августа"
        Calendar.SEPTEMBER -> "сентября"
        Calendar.OCTOBER -> "октября"
        Calendar.NOVEMBER -> "ноября"
        Calendar.DECEMBER -> "декабря"
        else -> ""
    }

    /**
     * Применяет плейсхолдеры, связанные с чатом (username, chat_id, message_text, date/time).
     * orderCtx можно не передавать — тогда order-переменные станут пустыми.
     */
    fun applyChat(
        template: String,
        chat: ChatItem,
        orderCtx: OrderCtx? = null,
        incomingText: String? = null
    ): String {
        if (template.isBlank()) return template
        return applyInternal(
            text = template,
            username = chat.username,
            chatId = chat.id,
            chatName = chat.username,
            messageText = incomingText ?: chat.lastMessage,
            order = orderCtx
        )
    }

    /**
     * Применяет плейсхолдеры, связанные с заказом (order_id, lot_name, game...).
     * Если нужно и имя покупателя — передать его через orderCtx.buyerUsername.
     */
    fun applyOrder(template: String, orderCtx: OrderCtx): String {
        if (template.isBlank()) return template
        return applyInternal(
            text = template,
            username = orderCtx.buyerUsername ?: "",
            chatId = "",
            chatName = orderCtx.buyerUsername ?: "",
            messageText = "",
            order = orderCtx
        )
    }

    /** Комбинированная версия — когда у нас есть и чат, и заказ (отзыв, подтверждение заказа). */
    fun applyCombined(
        template: String,
        chat: ChatItem?,
        orderCtx: OrderCtx?,
        incomingText: String? = null
    ): String {
        if (template.isBlank()) return template
        return applyInternal(
            text = template,
            username = orderCtx?.buyerUsername ?: chat?.username ?: "",
            chatId = chat?.id ?: "",
            chatName = chat?.username ?: orderCtx?.buyerUsername ?: "",
            messageText = incomingText ?: chat?.lastMessage ?: "",
            order = orderCtx
        )
    }

    private fun applyInternal(
        text: String,
        username: String,
        chatId: String,
        chatName: String,
        messageText: String,
        order: OrderCtx?
    ): String {
        val now = Date()
        val cal = Calendar.getInstance().apply { time = now }
        val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now)
        val dateText = "${cal.get(Calendar.DAY_OF_MONTH)} ${monthNameRu(cal.get(Calendar.MONTH))}"
        val fullDateText = "$dateText ${cal.get(Calendar.YEAR)} года"
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val fullTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

        val orderId = order?.orderId.orEmpty()
        val lotName = order?.lotName.orEmpty()
        val desc = order?.orderDesc.orEmpty()
        val params = order?.orderParams.orEmpty()
        val category = order?.category.orEmpty()
        val categoryFullname = order?.categoryFullname.orEmpty()
        val game = order?.game.orEmpty()

        val descAndParams = when {
            desc.isNotBlank() && params.isNotBlank() -> "$desc, $params"
            else -> "$desc$params"
        }
        val descOrParams = if (desc.isNotBlank()) desc else params

        // Порядок замен важен: длинные ключи раньше коротких, чтобы $full_date
        // не заменялся как $date + "_text".
        val replacements = linkedMapOf(
            "\$order_desc_and_params" to descAndParams,
            "\$order_desc_or_params" to descOrParams,
            "\$category_fullname" to categoryFullname,
            "\$full_date_text" to fullDateText,
            "\$full_date" to fullDateText,
            "\$date_text" to dateText,
            "\$full_time" to fullTime,
            "\$order_desc" to desc,
            "\$order_title" to desc,
            "\$order_params" to params,
            "\$order_link" to if (orderId.isNotBlank()) "https://funpay.com/orders/$orderId/" else "",
            "\$order_id" to orderId,
            "\$lot_name" to lotName,
            "\$message_text" to messageText,
            "\$chat_name" to chatName,
            "\$chat_id" to chatId,
            "\$category" to category,
            "\$username" to username,
            "\$game" to game,
            "\$date" to date,
            "\$time" to time
        )

        var out = text
        for ((k, v) in replacements) {
            if (out.contains(k)) out = out.replace(k, v)
        }
        return out
    }

    /** Короткая справка для tooltip'ов в UI. */
    val AVAILABLE_HELP_TEXT: String = buildString {
        append("Переменные:\n")
        append("• \$username — имя собеседника/покупателя\n")
        append("• \$chat_name — альтернатива имени собеседника\n")
        append("• \$chat_id — ID чата\n")
        append("• \$message_text — текст последнего сообщения\n")
        append("• \$date — дата (25.02.2026), \$time — время (14:48)\n")
        append("• \$full_date / \$date_text — «10 января 2026 года» / «10 января»\n")
        append("• \$full_time — 14:48:52\n")
        append("• \$order_id — ID заказа, \$order_link — ссылка\n")
        append("• \$lot_name — название лота\n")
        append("• \$order_desc / \$order_params / \$order_desc_and_params\n")
        append("• \$category — подкатегория, \$category_fullname, \$game")
    }
}

/* -------------------------------------------------------------------------
 * 2. УМНОЕ АВТОПОДНЯТИЕ: парсер "Подождите N минут/секунд/часов"
 *    (перенос из FunPayAPI/common/utils.py → parse_wait_time).
 *
 *    Плюс persistent-хранилище "до какого времени game X поднимать нельзя".
 * -------------------------------------------------------------------------
 */
object SmartRaise {

    /**
     * Парсит строку-ответ FunPay ("Подождите 15 минут.", "Please wait 32 seconds", ...)
     * и возвращает рекомендуемое ожидание в секундах.
     *
     * Совпадает поведением с Cardinal:
     *   - секунды → как есть;
     *   - минуты → (N-1) * 60 (чуть раньше разрешает повторную попытку);
     *   - часы → (N-0.5) * 3600;
     *   - если ничего не распознано → 10.
     */
    fun parseWaitSeconds(message: String?): Long {
        if (message.isNullOrBlank()) return 10L
        val lower = message.lowercase(Locale.getDefault())
        val digits = message.filter { it.isDigit() }
        val n = digits.toIntOrNull()

        return when {
            "секунд" in lower || "second" in lower -> (n ?: 2).toLong()
            "минут" in lower || "хвилин" in lower || "minute" in lower ->
                ((n ?: 2) - 1).coerceAtLeast(1) * 60L
            "час" in lower || "годин" in lower || "hour" in lower ->
                (((n ?: 1).toDouble() - 0.5) * 3600.0).toLong().coerceAtLeast(60L)
            else -> 10L
        }
    }

    private const val PREFS_KEY = "smart_raise_next_at_v1"

    /** Map<gameId, epochMillis> из prefs. */
    private fun read(prefs: SharedPreferences): MutableMap<Int, Long> {
        val json = prefs.getString(PREFS_KEY, null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<Int, Long>()
            obj.keys().forEach { k ->
                k.toIntOrNull()?.let { gameId -> map[gameId] = obj.optLong(k, 0L) }
            }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun write(prefs: SharedPreferences, map: Map<Int, Long>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k.toString(), v) }
        prefs.edit().putString(PREFS_KEY, obj.toString()).apply()
    }

    /** true, если для указанной игры ещё не прошло ожидание. */
    fun isCoolingDown(prefs: SharedPreferences, gameId: Int): Boolean {
        val ts = read(prefs)[gameId] ?: return false
        return System.currentTimeMillis() < ts
    }

    /** Сколько ещё ждать до разрешённого подъёма (в мс). 0 — можно поднимать. */
    fun msUntilAllowed(prefs: SharedPreferences, gameId: Int): Long {
        val ts = read(prefs)[gameId] ?: return 0L
        return (ts - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** Выставляет cooldown для игры на N секунд вперёд. */
    fun setCooldown(prefs: SharedPreferences, gameId: Int, waitSeconds: Long) {
        val current = read(prefs)
        current[gameId] = System.currentTimeMillis() + waitSeconds * 1000L
        write(prefs, current)
    }

    /** Очистить КД (например, после успешного подъёма). */
    fun clearCooldown(prefs: SharedPreferences, gameId: Int) {
        val current = read(prefs)
        if (current.remove(gameId) != null) write(prefs, current)
    }

    /** Минимальный next-raise среди всех игр (для UI / выбора sleep в сервисе). */
    fun minNextAt(prefs: SharedPreferences): Long? =
        read(prefs).values.minOrNull()
}

/* -------------------------------------------------------------------------
 * 3. АВТОБОНУС ЗА ОТЗЫВ — самостоятельная фича.
 *
 *    Каждое «правило» (FeedbackBonusRule) — это один тип бонуса со своим
 *    набором условий: минимум звёзд, текст + картинка, фильтры по лоту.
 *
 *    Фильтры по лоту:
 *      - matchLotIds — список ID лотов (для обычных lots/offer?id=22238017
 *        это «22238017», для чипов — составной id «X-Y-Z-W-N»);
 *      - matchLotNameContains — подстрока в названии лота (case-insensitive);
 *      - matchDescriptionCode — спец-код в описании лота (тоже case-insensitive);
 *      - если все три списка пусты → правило срабатывает на любой лот.
 *
 *    При пересечении условий нескольких правил — применяется первое подходящее
 *    по списку (порядок задаёт пользователь в UI). Дедуп по orderId — общий
 *    для всех правил, чтобы не отправлять два разных бонуса на один заказ.
 * -------------------------------------------------------------------------
 */
data class FeedbackBonusRule(
    @SerializedName("id") val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Новый бонус",
    val enabled: Boolean = true,
    /** Минимальное количество звёзд для выдачи бонуса. */
    @SerializedName("minStars") val minStars: Int = 5,
    /** Сообщение-бонус (поддерживает плейсхолдеры $username, $order_id, $lot_name, $date). */
    val text: String = "Спасибо за отзыв, \$username! 🎁 Держи бонус: промокод XYZ на следующую покупку.",
    val imageUri: String? = null,
    val imageFirst: Boolean = true,
    /** Отправлять бонус только при отсутствии уже отправленного для этого order_id (глобально). */
    val oncePerOrder: Boolean = true,
    /** Фильтр: список ID лотов (обычных или компаундных-чиповых). Пусто = игнорировать фильтр. */
    val matchLotIds: List<String> = emptyList(),
    /** Фильтр: подстрока в названии лота. Пусто = игнорировать. */
    val matchLotNameContains: List<String> = emptyList(),
    /** Фильтр: спец-код/маркер внутри описания лота (case-insensitive). Пусто = игнорировать. */
    val matchDescriptionCode: List<String> = emptyList()
)

/**
 * Верхнеуровневый контейнер для всех бонусных правил + общий enable-флаг.
 */
data class FeedbackBonusSettings(
    val enabled: Boolean = false,
    val rules: List<FeedbackBonusRule> = emptyList()
)

/* -------------------------------------------------------------------------
 * 4. ПАРСЕР ССЫЛОК НА ЛОТЫ (разные типы URL FunPay).
 *
 *  Поддерживаются:
 *    https://funpay.com/lots/offer?id=22238017
 *    https://funpay.com/chips/offer?id=3934718-168-110-2842-0
 *    https://funpay.com/lots/168/                (только категория)
 *    https://funpay.com/chips/168/               (только категория)
 *    https://funpay.com/lots/lot-name/           (категория с именем)
 *    https://funpay.com/lots/offerEdit?offer=22238017
 *    href="/lots/offer?id=...", href="/chips/offer?id=..."
 * -------------------------------------------------------------------------
 */
object LotUrlParser {

    data class LotRef(
        /** Тип лота: "lot" для обычных, "chip" для чипов (chips/offer?id=...). */
        val kind: String,
        /** Основной ID (для обычного лота — число, для chip — составной X-Y-Z-W-N). */
        val id: String,
        /** Чистый числовой ID (для обычного лота — его id, для chip — sellerId из префикса). */
        val numericId: String?,
        /** ID узла/категории (для lot — обычно неизвестен без getMyLots; для chip — game/110 и т.д.). */
        val nodeId: String? = null,
        /** Человекочитаемое название категории, если удалось распарсить из URL (для /lots/168/ — нет имени). */
        val categoryHint: String? = null
    )

    /**
     * Извлекает ID лота/чипа из произвольного URL или href.
     * Возвращает null, если это вообще не ссылка на лот.
     */
    fun parse(urlOrHref: String?): LotRef? {
        val raw = (urlOrHref ?: "").trim().ifEmpty { return null }

        // Абсолютная или относительная — нормализуем.
        val path = raw
            .removePrefix("https://")
            .removePrefix("http://")
            .let { if (it.startsWith("funpay.com")) it.removePrefix("funpay.com") else it }

        // 1. chip: /chips/offer?id=3934718-168-110-2842-0
        Regex("""/chips/offer\?id=([0-9]+(?:-[0-9]+){3,4})""").find(path)?.let {
            val compound = it.groupValues[1]
            val parts = compound.split("-")
            // Формат: sellerId-gameId-chipId-server-side
            val numericSellerId = parts.getOrNull(0)
            val gameId = parts.getOrNull(1)
            return LotRef(
                kind = "chip",
                id = compound,
                numericId = numericSellerId,
                nodeId = gameId,
                categoryHint = null
            )
        }

        // 2. lot: /lots/offer?id=22238017 или /lots/offerEdit?offer=22238017
        Regex("""/lots/(?:offer|offerEdit)\??[^ \t]*?(?:\?|&)(?:id|offer)=([0-9]+)""").find(path)?.let {
            val id = it.groupValues[1]
            return LotRef(kind = "lot", id = id, numericId = id)
        }
        // Отдельный вариант — более широкий fallback на случай нестандартных URL.
        Regex("""(?:id|offer)=([0-9]+)""").find(path)?.let { match ->
            if ("/lots/" in path || "/chips/" in path) {
                val id = match.groupValues[1]
                val kind = if ("/chips/" in path) "chip" else "lot"
                return LotRef(kind = kind, id = id, numericId = id)
            }
        }

        // 3. Категория без имени: /lots/168/ или /chips/168/
        Regex("""/(lots|chips)/(\d+)/?$""").find(path)?.let {
            val kind = if (it.groupValues[1] == "chips") "chip" else "lot"
            val nodeId = it.groupValues[2]
            return LotRef(kind = kind, id = nodeId, numericId = nodeId, nodeId = nodeId, categoryHint = null)
        }

        // 4. Категория с именем: /lots/lot-name/ — возвращаем categoryHint.
        Regex("""/(lots|chips)/([a-z0-9_-]+)/?$""", RegexOption.IGNORE_CASE).find(path)?.let {
            val kind = if (it.groupValues[1] == "chips") "chip" else "lot"
            val name = it.groupValues[2]
            if (name.any { ch -> !ch.isDigit() }) {
                // name содержит буквы — это именованная категория.
                return LotRef(kind = kind, id = name, numericId = null, nodeId = null, categoryHint = name)
            }
        }

        return null
    }
}

/* -------------------------------------------------------------------------
 * 5. АВТОПЕРЕВОД RU→EN (Google Translate free-endpoint + защита эмодзи/
 *    спецсимволов).
 *
 *    Проблема: публичный Google-хак (translate.googleapis.com/translate_a/single)
 *    часто:
 *      - схлопывает последовательные эмодзи в одно,
 *      - теряет ASCII-арты, разделители типа • ⚡ ★,
 *      - "нормализует" пробелы/переводы строк.
 *
 *    Решение: перед отправкой заменяем все "хрупкие" символы на плейсхолдеры
 *    вида 【【0】】, 【【1】】… которые Google не трогает. После получения
 *    перевода — возвращаем символы на место.
 *
 *    Эта реализация защищает ВСЕ не-ASCII символы, кроме кириллицы, что
 *    покрывает эмодзи, китайский/арабский, разделители и всевозможные
 *    псевдографические символы.
 * -------------------------------------------------------------------------
 */
object FpTranslate {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Возвращает пару (подготовленный текст, список извлечённых спец-токенов).
     * Спецсимволами считаем:
     *   - эмодзи / всё > U+007F, не относящееся к кириллице;
     *   - одинокие \r\n не трогаем (их Google обрабатывает нормально).
     */
    internal data class Protected(val safeText: String, val tokens: List<String>)

    private fun protectSpecials(input: String): Protected {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            val charCount = Character.charCount(cp)

            val isAscii = cp <= 0x7F
            val isCyrillic = cp in 0x0400..0x04FF || cp in 0x0500..0x052F
            // Защищаем всё, что не ASCII и не кириллица — это и эмодзи,
            // и псевдографика, и нестандартные знаки препинания (», «, —, …).
            val shouldProtect = !isAscii && !isCyrillic

            if (shouldProtect) {
                // Склеиваем подряд идущие защищаемые кодпоинты в один токен —
                // так эмодзи-составные (флаги, ZWJ-sequences) не распадаются.
                val start = i
                var j = i
                while (j < input.length) {
                    val cpj = input.codePointAt(j)
                    val ccj = Character.charCount(cpj)
                    val asciiJ = cpj <= 0x7F
                    val cyrJ = cpj in 0x0400..0x04FF || cpj in 0x0500..0x052F
                    val protJ = !asciiJ && !cyrJ
                    if (!protJ) break
                    j += ccj
                }
                val token = input.substring(start, j)
                tokens.add(token)
                // Плейсхолдер должен быть:
                //  1) стабильным (Google не меняет),
                //  2) уникальным (не встречается в тексте).
                // Формат «⟦N⟧» — но сам ⟦ не-ASCII; чтобы не ломать защиту,
                // используем чисто ASCII: "X_Z{N}_X" — ни один переводчик не тронет.
                sb.append("X_Z").append(tokens.size - 1).append("_X")
                i = j
            } else {
                sb.appendCodePoint(cp)
                i += charCount
            }
        }
        return Protected(sb.toString(), tokens)
    }

    private fun restoreSpecials(translated: String, tokens: List<String>): String {
        if (tokens.isEmpty()) return translated
        var out = translated
        // Проходим В ОБРАТНОМ порядке — чтобы X_Z10_X не переписался случайно
        // при замене X_Z1_X, если номера пересекаются.
        for (idx in tokens.indices.reversed()) {
            val placeholder = "X_Z${idx}_X"
            out = out.replace(placeholder, tokens[idx])
            // Google может разделить пробелами: "X_ Z1 _X" — страхуемся.
            val alt = Regex("""X[_\s]*Z\s*$idx\s*_?X""")
            out = alt.replace(out, tokens[idx])
        }
        return out
    }

    /**
     * Переводит текст RU → EN через публичный endpoint Google Translate.
     * Возвращает null при ошибке сети. Сохраняет эмодзи и спецсимволы.
     *
     * Endpoint: https://translate.googleapis.com/translate_a/single?client=gtx&sl=ru&tl=en&dt=t&q=...
     */
    suspend fun translateRuToEn(text: String): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        val protected = protectSpecials(text)
        val encoded = URLEncoder.encode(protected.safeText, "UTF-8")

        // Google ограничивает длину query ~5000 символов. Разбиваем при необходимости.
        val chunks = if (protected.safeText.length > 4500) {
            splitByParagraphs(protected.safeText, 4500)
        } else listOf(protected.safeText)

        val pieces = mutableListOf<String>()
        for (chunk in chunks) {
            val e = URLEncoder.encode(chunk, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=ru&tl=en&dt=t&q=$e"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .build()
            try {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body?.string() ?: return@withContext null
                    val arr = JSONArray(body)
                    val first = arr.optJSONArray(0) ?: return@withContext null
                    val buf = StringBuilder()
                    for (k in 0 until first.length()) {
                        val line = first.optJSONArray(k) ?: continue
                        val piece = line.optString(0)
                        buf.append(piece)
                    }
                    pieces.add(buf.toString())
                }
            } catch (e: Exception) {
                return@withContext null
            }
        }

        val joined = pieces.joinToString("")
        restoreSpecials(joined, protected.tokens)
    }

    /**
     * Разбивает длинный текст на куски по ≤ maxLen символов, стараясь резать
     * по двойным переводам строк / одиночным переводам / пробелам.
     */
    private fun splitByParagraphs(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val out = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val end = (i + maxLen).coerceAtMost(text.length)
            var cut = end
            if (end < text.length) {
                val candidates = intArrayOf(
                    text.lastIndexOf("\n\n", end),
                    text.lastIndexOf("\n", end),
                    text.lastIndexOf(". ", end),
                    text.lastIndexOf(" ", end)
                )
                cut = candidates.filter { it > i }.maxOrNull() ?: end
            }
            out.add(text.substring(i, cut))
            i = cut
        }
        return out
    }
}

/* -------------------------------------------------------------------------
 * 6. SharedPreferences helpers для FeedbackBonusSettings.
 *    (Функции верхнего уровня, удобно вызывать из repository и из UI.)
 * -------------------------------------------------------------------------
 */
private const val FEEDBACK_BONUS_PREF_KEY = "feedback_bonus_settings_v1"
private const val FEEDBACK_BONUS_SENT_KEY = "feedback_bonus_sent_orders"

fun FunPayRepository.getFeedbackBonusSettings(): FeedbackBonusSettings {
    val json = prefs.getString(FEEDBACK_BONUS_PREF_KEY, null) ?: return FeedbackBonusSettings()
    return try {
        com.google.gson.Gson().fromJson(json, FeedbackBonusSettings::class.java) ?: FeedbackBonusSettings()
    } catch (e: Exception) {
        FeedbackBonusSettings()
    }
}

fun FunPayRepository.saveFeedbackBonusSettings(settings: FeedbackBonusSettings) {
    prefs.edit()
        .putString(FEEDBACK_BONUS_PREF_KEY, com.google.gson.Gson().toJson(settings))
        .apply()
}

/**
 * Подбирает первое подходящее правило бонуса под контекст отзыва.
 * Возвращает null, если:
 *   - фича выключена,
 *   - нет ни одного enabled-правила,
 *   - ни одно правило не подошло по фильтрам.
 *
 * @param stars оценка отзыва (1..5)
 * @param lotId ID лота заказа (может быть null — тогда фильтр matchLotIds не сработает)
 * @param lotName название лота
 * @param lotDescription полное описание лота (для matchDescriptionCode)
 */
fun FunPayRepository.pickFeedbackBonusRule(
    stars: Int,
    lotId: String?,
    lotName: String?,
    lotDescription: String?
): FeedbackBonusRule? {
    val settings = getFeedbackBonusSettings()
    if (!settings.enabled) return null

    val lotNameLower = lotName.orEmpty().lowercase(Locale.getDefault())
    val descLower = lotDescription.orEmpty().lowercase(Locale.getDefault())

    return settings.rules.firstOrNull { rule ->
        if (!rule.enabled) return@firstOrNull false
        if (stars < rule.minStars) return@firstOrNull false

        val hasAnyFilter = rule.matchLotIds.isNotEmpty() ||
                rule.matchLotNameContains.isNotEmpty() ||
                rule.matchDescriptionCode.isNotEmpty()

        // Нет фильтров — правило «для всех лотов».
        if (!hasAnyFilter) return@firstOrNull true

        // Иначе — достаточно попадания в любой из непустых фильтров (OR-логика
        // между разными типами фильтров; внутри каждого списка — тоже OR).
        val idMatch = rule.matchLotIds.isNotEmpty() &&
                lotId != null && rule.matchLotIds.any { it.trim() == lotId }

        val nameMatch = rule.matchLotNameContains.isNotEmpty() &&
                rule.matchLotNameContains.any { needle ->
                    needle.isNotBlank() && lotNameLower.contains(needle.trim().lowercase(Locale.getDefault()))
                }

        val descMatch = rule.matchDescriptionCode.isNotEmpty() &&
                rule.matchDescriptionCode.any { needle ->
                    needle.isNotBlank() && descLower.contains(needle.trim().lowercase(Locale.getDefault()))
                }

        idMatch || nameMatch || descMatch
    }
}

fun FunPayRepository.wasFeedbackBonusSent(orderId: String): Boolean {
    val sentJson = prefs.getString(FEEDBACK_BONUS_SENT_KEY, "[]") ?: "[]"
    return try {
        val arr = JSONArray(sentJson)
        (0 until arr.length()).any { arr.optString(it) == orderId }
    } catch (e: Exception) {
        false
    }
}

fun FunPayRepository.markFeedbackBonusSent(orderId: String) {
    val sentJson = prefs.getString(FEEDBACK_BONUS_SENT_KEY, "[]") ?: "[]"
    val arr = try { JSONArray(sentJson) } catch (e: Exception) { JSONArray() }
    // Ограничиваем до 500 записей, чтобы не пухло бесконечно.
    val list = mutableListOf<String>()
    for (i in 0 until arr.length()) list.add(arr.optString(i))
    if (!list.contains(orderId)) list.add(orderId)
    while (list.size > 500) list.removeAt(0)
    val out = JSONArray()
    list.forEach { out.put(it) }
    prefs.edit().putString(FEEDBACK_BONUS_SENT_KEY, out.toString()).apply()
}

/** Флаг включения умного КД-поднятия (может быть переопределён в настройках). */
fun FunPayRepository.isSmartRaiseEnabled(): Boolean =
    prefs.getBoolean("smart_raise_enabled", true)

fun FunPayRepository.setSmartRaiseEnabled(enabled: Boolean) {
    prefs.edit().putBoolean("smart_raise_enabled", enabled).apply()
}