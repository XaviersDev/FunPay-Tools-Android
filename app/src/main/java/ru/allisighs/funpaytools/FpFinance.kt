package ru.allisighs.funpaytools

import org.jsoup.Jsoup
import java.util.Calendar
import java.util.TimeZone

/**
 * Даты FunPay: «сегодня, 14:30», «вчера, 10:15», «15 октября, 12:00», «15 октября 2024, 12:00».
 * FunPay пишет время по Москве, поэтому считаем в Europe/Moscow.
 */
object FpDates {
    private val moscow: TimeZone = TimeZone.getTimeZone("Europe/Moscow")
    private val months = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    private val monthsEn = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val timeRe = Regex("(\\d{1,2}):(\\d{2})")
    private val dayRe = Regex("(\\d{1,2})\\s+\\p{L}+")
    private val yearRe = Regex("\\b(\\d{4})\\b")

    fun parse(text: String?, now: Long = System.currentTimeMillis()): Long? {
        val s = text?.lowercase()?.trim().orEmpty()
        if (s.isEmpty()) return null
        val time = timeRe.find(s)
        val h = time?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val m = time?.groupValues?.get(2)?.toIntOrNull() ?: 0
        val cal = Calendar.getInstance(moscow).apply { timeInMillis = now }
        fun at(c: Calendar): Long {
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        when {
            s.contains("сегодня") || s.contains("today") -> return at(cal)
            s.contains("вчера") || s.contains("yesterday") -> { cal.add(Calendar.DAY_OF_YEAR, -1); return at(cal) }
        }
        var monthIdx = months.indexOfFirst { s.contains(it) }
        if (monthIdx < 0) monthIdx = monthsEn.indexOfFirst { s.contains(it) }
        if (monthIdx < 0) return null
        val day = dayRe.find(s)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("\\p{L}+\\s+(\\d{1,2})").find(s)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val explicitYear = yearRe.find(s)?.groupValues?.get(1)?.toIntOrNull()
        val c = Calendar.getInstance(moscow).apply {
            timeInMillis = now
            set(Calendar.YEAR, explicitYear ?: get(Calendar.YEAR))
            set(Calendar.MONTH, monthIdx)
            set(Calendar.DAY_OF_MONTH, day)
        }
        var ts = at(c)
        // «28 декабря» в январе — это прошлый год
        if (explicitYear == null && ts > now + 36L * 3600_000L) {
            c.add(Calendar.YEAR, -1); ts = c.timeInMillis
        }
        return ts
    }
}

/** Операция со страницы «Финансы» (users/transactions). */
data class FpTransaction(
    val id: String,
    val title: String,
    /** complete, waiting, cancel */
    val status: String,
    /** order, withdraw, withdraw_cancel, payment, other */
    val type: String,
    /** со знаком: приход > 0, расход < 0 */
    val amount: Double,
    val currency: String,
    val dateText: String,
    val timestamp: Long?,
    val payment: String? = null
)

/** Деньги в заморозке по данным «Финансов». */
data class FrozenFunds(
    val byCurrency: Map<String, Double>,
    /** операции в ожидании и примерное время разморозки, по возрастанию */
    val items: List<Pair<FpTransaction, Long>>
) {
    fun sum(currency: String) = byCurrency[currency] ?: 0.0
    fun next(currency: String) = items.firstOrNull { it.first.currency == currency }
}

object FpFinance {
    /** Сколько FunPay держит деньги после завершения заказа (оценка для «Ближайшей разморозки»). */
    const val HOLD_MS = 48L * 3600_000L

    private val numRe = Regex("[^0-9.,]")

    fun parseAmount(raw: String): Pair<Double, String> {
        val t = raw.replace(' ', ' ').trim()
        val negative = t.startsWith("−") || t.startsWith("-") || t.startsWith("–")
        val currency = when {
            t.contains("₽") || t.contains("руб", true) -> "₽"
            t.contains("$") -> "$"
            t.contains("€") -> "€"
            else -> "₽"
        }
        val digits = t.replace(" ", "").replace(numRe, "").replace(',', '.')
        // если вдруг осталось несколько точек (разделители тысяч), оставляем последнюю как дробную
        val normalized = digits.count { it == '.' }.let { dots ->
            if (dots <= 1) digits else digits.substringBeforeLast('.').replace(".", "") + "." + digits.substringAfterLast('.')
        }
        val v = normalized.toDoubleOrNull() ?: 0.0
        return (if (negative) -v else v) to currency
    }

    fun typeOf(title: String): String {
        val t = title.trim()
        return when {
            t.startsWith("Заказ") || t.startsWith("Order") -> "order"
            t.startsWith("Отмена вывода") -> "withdraw_cancel"
            t.startsWith("Вывод денег") || t.startsWith("Withdraw") -> "withdraw"
            t.startsWith("Пополнение баланса") || t.startsWith("Deposit") -> "payment"
            else -> "other"
        }
    }

    /**
     * Статус операции: по классам строки и вложенных элементов, а если их нет, по тексту статуса
     * (FunPay помечает строки transaction-status-waiting / -cancel / -complete, но не всегда).
     */
    private fun statusOf(el: org.jsoup.nodes.Element): String {
        val classes = ArrayList<String>()
        classes.addAll(el.classNames().map { it.lowercase() })
        val texts = StringBuilder()
        for (e in el.select(".tc-status, [class*=status]")) {
            classes.addAll(e.classNames().map { it.lowercase() })
            texts.append(e.text().lowercase()).append(' ')
        }
        fun has(n: String) = classes.any { it.contains("status") && it.contains(n) }
        val t = texts.toString()
        return when {
            has("cancel") || has("refund") || t.contains("отмен") || t.contains("cancel") -> "cancel"
            has("wait") || has("pending") || has("process") || has("hold") ||
                t.contains("ожида") || t.contains("обработ") || t.contains("заморож") || t.contains("waiting") || t.contains("pending") -> "waiting"
            else -> "complete"
        }
    }

    /** Разбор кусочка HTML из POST users/transactions. Возвращает операции и токен следующей страницы. */
    fun parsePage(html: String, now: Long = System.currentTimeMillis()): Pair<List<FpTransaction>, String?> {
        val doc = Jsoup.parse(html)
        val list = doc.select(".tc-item[data-transaction]").mapNotNull { el ->
            val id = el.attr("data-transaction").trim()
            if (id.isEmpty()) return@mapNotNull null
            val status = statusOf(el)
            val title = el.selectFirst(".tc-title")?.text()?.trim().orEmpty()
            val (amount, currency) = parseAmount(el.selectFirst(".tc-price")?.text().orEmpty())
            val dateText = el.selectFirst(".tc-date-time")?.text()?.trim().orEmpty()
            FpTransaction(
                id = id,
                title = title,
                status = status,
                type = typeOf(title),
                amount = amount,
                currency = currency,
                dateText = dateText,
                timestamp = FpDates.parse(dateText, now),
                payment = el.selectFirst(".tc-payment-number")?.text()?.trim()?.ifEmpty { null }
            )
        }
        val cont = doc.selectFirst("input[name=continue]")?.attr("value")?.trim()?.ifEmpty { null }
        return list to cont
    }

    /**
     * Заморозка как в FunPay Tools Desktop: FunPay сразу показывает приход за заказ как выполненный,
     * но держит деньги 48 часов. Поэтому считаем всё, что FunPay пометил как ожидающее,
     * плюс приходы за заказы за последние 48 часов.
     */
    fun frozen(transactions: Collection<FpTransaction>, now: Long = System.currentTimeMillis()): FrozenFunds {
        val held = transactions.filter { tx ->
            if (tx.amount <= 0 || tx.status == "cancel") return@filter false
            val ts = tx.timestamp
            val recent = ts != null && now - ts < HOLD_MS
            val isOrder = tx.type == "order" || tx.title.contains("заказ", ignoreCase = true) || Regex("#[A-Z0-9]{6,10}").containsMatchIn(tx.title)
            tx.status == "waiting" || (recent && isOrder)
        }
        val byCurrency = held.groupBy { it.currency }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val items = held.map { tx -> tx to maxOf((tx.timestamp ?: now) + HOLD_MS, now) }.sortedBy { it.second }
        return FrozenFunds(byCurrency, items)
    }

    /** Запасная оценка по закрытым продажам за 48 часов; из двух оценок берём большую (как в десктопе). */
    fun mergeWithSales(fi: FrozenFunds?, salesHeld: List<Pair<Double, Long>>, currency: String): Pair<Double, Pair<Double, Long>?> {
        val fromTx = fi?.sum(currency) ?: 0.0
        val fromSales = salesHeld.sumOf { it.first }
        return if (fi != null && fromTx >= fromSales) {
            fromTx to fi.next(currency)?.let { it.first.amount to it.second }
        } else {
            fromSales to salesHeld.minByOrNull { it.second }
        }
    }
}
