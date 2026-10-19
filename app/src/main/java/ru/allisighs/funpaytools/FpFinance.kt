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

    /** Разбор кусочка HTML из POST users/transactions. Возвращает операции и токен следующей страницы. */
    fun parsePage(html: String, now: Long = System.currentTimeMillis()): Pair<List<FpTransaction>, String?> {
        val doc = Jsoup.parse(html)
        val list = doc.select(".tc-item[data-transaction]").mapNotNull { el ->
            val id = el.attr("data-transaction").trim()
            if (id.isEmpty()) return@mapNotNull null
            val status = when {
                el.hasClass("transaction-status-cancel") -> "cancel"
                el.hasClass("transaction-status-waiting") -> "waiting"
                else -> "complete"
            }
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

    /** Заморозка = приходы со статусом «в ожидании» (продажи и всё остальное, что ещё не на балансе). */
    fun frozen(transactions: Collection<FpTransaction>, now: Long = System.currentTimeMillis()): FrozenFunds {
        val waiting = transactions.filter { it.status == "waiting" && it.amount > 0 }
        val byCurrency = waiting.groupBy { it.currency }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val items = waiting.map { tx -> tx to ((tx.timestamp ?: now) + HOLD_MS) }.sortedBy { it.second }
        return FrozenFunds(byCurrency, items)
    }
}
