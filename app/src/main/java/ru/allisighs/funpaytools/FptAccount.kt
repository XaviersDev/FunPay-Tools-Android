package ru.allisighs.funpaytools

import android.content.Context
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Аккаунт FunPay Tools: вход через Telegram-бота, PRO, цветные ники, рамки и баннеры.
 * Тот же сервер, что у вкладки «Аккаунт» в расширении и в FunPay Tools Desktop
 * (воркер fpt-promo, задания выполняет бот @FPToolsBot).
 */
object FptAccountApi {
    const val API = "https://fpt-promo.starobinskiy01.workers.dev"
    const val STYLE_GENERATOR = "https://funpay.tools/donate"
    private const val PREFS = "fpt_account"

    /** Только для офлайн-рендера экрана в тестах. */
    var previewProfile: JSONObject? = null

    private val prefs get() = AppContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var tgSession: String
        get() = prefs.getString("tg_session", "") ?: ""
        set(v) { prefs.edit().putString("tg_session", v).apply() }
    var tgLabel: String
        get() = prefs.getString("tg_label", "") ?: ""
        set(v) { prefs.edit().putString("tg_label", v).apply() }

    /** Подтверждённая сессия аккаунта FunPay: (uid, token). */
    fun promoSession(uid: String): String? = if (prefs.getString("promo_uid", null) == uid) prefs.getString("promo_token", null) else null
    fun savePromoSession(uid: String, token: String) { prefs.edit().putString("promo_uid", uid).putString("promo_token", token).apply() }
    fun forgetPromoSession() { prefs.edit().remove("promo_uid").remove("promo_token").apply() }

    fun api(path: String, body: JSONObject): JSONObject = try {
        val req = Request.Builder().url(API + path)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val resp = ApiFactory.shared.newBuilder().readTimeout(30, TimeUnit.SECONDS).build().newCall(req).execute()
        val text = resp.body?.string().orEmpty()
        val v = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
        if (!resp.isSuccessful && !v.has("error")) v.put("error", "HTTP_${resp.code}")
        v
    } catch (e: Exception) {
        LogManager.addLogDebug("fpt-promo $path: ${e.message}")
        JSONObject().put("error", "NET")
    }

    fun ok(v: JSONObject) = v.optBoolean("ok", false)
    fun err(v: JSONObject) = v.optString("error", "UNKNOWN").ifEmpty { "UNKNOWN" }

    /** Задание боту через воркер: создать и дождаться ответа (до 40 секунд). */
    suspend fun job(kind: String, payload: JSONObject = JSONObject(), session: String = tgSession): Result<JSONObject> = withContext(Dispatchers.IO) {
        val c = api("/job", JSONObject().put("session", session).put("type", kind).put("payload", payload))
        if (!ok(c)) return@withContext Result.failure(Exception(err(c)))
        val id = c.opt("id")
        val deadline = System.currentTimeMillis() + 40_000
        while (System.currentTimeMillis() < deadline) {
            delay(1200)
            val r = api("/job/result", JSONObject().put("session", session).put("id", id))
            if (ok(r) && r.optBoolean("pending", false)) continue
            if (!ok(r)) return@withContext Result.failure(Exception(err(r)))
            val res = r.optJSONObject("result") ?: JSONObject()
            if (!res.optBoolean("ok", false)) return@withContext Result.failure(Exception(res.optString("error", "JOB_FAILED")))
            return@withContext Result.success(res)
        }
        Result.failure(Exception("BOT_TIMEOUT"))
    }

    fun errText(code: String?): String {
        val c = code ?: "UNKNOWN"
        val t = when (c) {
            "NET" -> "Нет связи с сервером FunPay Tools. Проверь интернет или прокси."
            "PAYMENT_METHOD_UNAVAILABLE" -> "Этот способ оплаты сейчас недоступен, выбери другой."
            "NEED_PAYMENT" -> "Сначала оплати ник: бесплатный ник положен только с PRO и только первый."
            "NICK_TAKEN" -> "Этот ник уже украшен другим человеком. Напиши в поддержку @AlliSighs."
            "INVOICE_NOT_FOUND" -> "Счёт не найден, создай новый."
            "NOT_YOUR_NICK" -> "Этот ник записан не на твой Telegram."
            "TOO_FAST" -> "Слишком быстро, подожди секунду."
            "BOT_TIMEOUT" -> "Бот не ответил. Возможно, он перезапускается, попробуй через минуту."
            "NO_SESSION" -> "Нужно заново подтвердить аккаунт FunPay."
            "NO_TG_SESSION" -> "Вход через Telegram устарел, войди ещё раз."
            "BAD_KEY" -> "Ключ не похож на FPT-STYLE. Скопируй его в генераторе ещё раз."
            "TOO_MANY_CHANGES" -> "Стиль меняли слишком часто."
            "NEED_FRAME" -> "Рамка не куплена и PRO нет."
            "NEED_BANNER" -> "Баннер не куплен и PRO нет."
            "VERIFY_TIMEOUT" -> "FunPay долго не показывает проверочный лот. Попробуй через минуту."
            "LOGIN_TIMEOUT" -> "Не дождался подтверждения в боте. Попробуй ещё раз."
            else -> ""
        }
        if (t.isNotEmpty()) return t
        if (c.startsWith("DONATERS_")) return "Сервер ников не принял стиль: ${c.removePrefix("DONATERS_")}"
        return if (c.length > 40 || c.contains(' ')) c else "Ошибка: $c"
    }

    fun productName(p: String) = when (p) {
        "1m" -> "PRO на 1 месяц"
        "3m" -> "PRO на 3 месяца"
        "forever" -> "PRO навсегда"
        "nick" -> "Цветной ник навсегда"
        "frame" -> "Рамка для аватарки навсегда"
        "banner" -> "Баннер навсегда"
        else -> "Покупка"
    }

    // ------------------------------------------------------------ вход через Telegram

    /** Открывает бота и ждёт подтверждения (до 5 минут). onStatus — промежуточные сообщения. */
    suspend fun telegramLogin(onStatus: (String) -> Unit, isCancelled: () -> Boolean): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val s0 = api("/tg/start", JSONObject())
        if (!ok(s0)) return@withContext Result.failure(Exception(err(s0)))
        val link = s0.optString("link")
        val token = s0.optString("token")
        PlatformBridge.openUrl(link)
        onStatus("Нажми Start в боте. Если бот попросит подписаться на канал, подпишись и нажми «Войти через Telegram» ещё раз.")
        val deadline = System.currentTimeMillis() + 300_000
        while (System.currentTimeMillis() < deadline) {
            delay(2500)
            if (isCancelled()) return@withContext Result.failure(Exception("CANCELLED"))
            val p = api("/tg/poll", JSONObject().put("token", token))
            val sess = p.optString("session")
            if (ok(p) && sess.isNotEmpty()) {
                val user = p.optJSONObject("user") ?: JSONObject()
                val un = user.optString("username")
                val label = when {
                    un.isNotEmpty() -> "@$un"
                    user.optString("name").isNotEmpty() -> user.optString("name")
                    else -> "подключён"
                }
                tgSession = sess
                tgLabel = label
                return@withContext Result.success(sess to label)
            }
            if (!ok(p) && err(p) != "NOT_YET") return@withContext Result.failure(Exception(err(p)))
        }
        Result.failure(Exception("LOGIN_TIMEOUT"))
    }

    suspend fun telegramLogout() = withContext(Dispatchers.IO) {
        val sess = tgSession
        if (sess.isNotEmpty()) api("/tg/logout", JSONObject().put("session", sess))
        tgSession = ""
        tgLabel = ""
    }

    // ------------------------------------------------------------ подтверждение аккаунта FunPay

    private const val VERIFY_NODE = "2046"

    /**
     * Подтверждённый аккаунт FunPay нужен для ника и украшений: сохранённая сессия или временный лот
     * «FPT Verify код» (раздел «Прочее для прочих игр», цена 1000), который сервер видит в профиле, после чего лот удаляется.
     */
    suspend fun ensureFunPaySession(repo: FunPayRepository, onStatus: (String) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        val uid = repo.getActiveAccount()?.userId?.takeIf { it.isNotBlank() && it != "0" }
            ?: repo.getCsrfAndId()?.second?.takeIf { it.isNotBlank() && it != "0" }
            ?: return@withContext Result.failure(Exception("Сначала войди в аккаунт FunPay."))
        onStatus("Проверяю аккаунт FunPay…")
        promoSession(uid)?.let { saved ->
            val st = api("/status", JSONObject().put("session", saved))
            if (ok(st)) return@withContext Result.success(saved)
        }
        onStatus("Подтверждаю аккаунт FunPay: создаю временный лот…")
        val start = api("/link/start", JSONObject().put("fpid", uid))
        if (!ok(start)) return@withContext Result.failure(Exception(err(start)))
        val code = start.optString("code")
        if (code.isEmpty()) return@withContext Result.failure(Exception("LINK_START"))

        val created = createVerifyLot(repo, code)
        if (created != null) return@withContext Result.failure(Exception("Не получилось создать лот подтверждения: $created"))

        val needle = "FPT Verify $code"
        var offer: String? = null
        repeat(4) {
            if (offer == null) {
                offer = try { repo.getMyLots().firstOrNull { it.title.contains(needle) }?.id } catch (_: Exception) { null }
                if (offer == null) delay(1500)
            }
        }
        val offerId = offer ?: return@withContext Result.failure(Exception("Лот подтверждения не нашёлся в профиле. Если лот «FPT Verify» остался, удали его на FunPay."))

        onStatus("Жду, пока сервер увидит лот…")
        var res: Result<String> = Result.failure(Exception("VERIFY_TIMEOUT"))
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            delay(3000)
            val c = api("/link/confirm", JSONObject().put("fpid", uid).put("offerId", offerId.toLongOrNull() ?: 0L))
            val sess = c.optString("session")
            if (ok(c) && sess.isNotEmpty()) { res = Result.success(sess); break }
            val e = err(c)
            if (!ok(c) && e != "NOT_YET" && e != "UNKNOWN") { res = Result.failure(Exception(e)); break }
        }

        var deleted = false
        for (i in 0 until 3) {
            if (try { repo.deleteLot(offerId) } catch (_: Exception) { false }) { deleted = true; break }
            delay(1500L * (i + 1))
        }
        if (!deleted) Toast.makeText(AppContext, "Лот «FPT Verify» не удалился сам, удали его на FunPay", Toast.LENGTH_LONG).show()
        res.onSuccess { savePromoSession(uid, it) }
        res
    }

    /** null — лот создан, иначе текст ошибки. */
    private suspend fun createVerifyLot(repo: FunPayRepository, code: String): String? {
        val data0 = try { repo.getNewLotFields(VERIFY_NODE) } catch (e: Exception) { return e.message ?: "FunPay не отдал форму лота" }
        val data = data0.copy(fields = data0.fields.filterKeys { it != "deleted" })
        val title = "FPT Verify $code"
        val values = LinkedHashMap<String, String>()
        values["offer_id"] = "0"
        values["node_id"] = VERIFY_NODE
        values["price"] = "1000"
        values["fields[summary][ru]"] = title
        if (data.fields.containsKey("fields[summary][en]")) values["fields[summary][en]"] = title
        values["fields[desc][ru]"] = code
        values["active"] = "on"
        values["amount"] = "1"
        fillRequired(data, values, code, emptyList())
        var last = ""
        repeat(3) {
            val (ok, error) = repo.saveLotFields("0", data, values)
            if (ok) return null
            last = error ?: "FunPay не сохранил лот"
            // FunPay может назвать поле, которое ему не понравилось: заполняем его и пробуем снова
            val named = last.split('\n', ';').mapNotNull { p -> p.substringBefore(':', "").trim().ifEmpty { null } }
            if (named.isEmpty()) return last
            fillRequired(data, values, code, named)
        }
        return last
    }

    private fun fillRequired(data: LotFieldsData, values: MutableMap<String, String>, code: String, force: List<String>) {
        repeat(3) {
            var changed = false
            for ((name, f) in data.fields) {
                if (f.type == "hidden" || f.type == "checkbox") continue
                val cur = values[name] ?: f.value
                if (!f.isActiveFor(data.fields.mapValues { (k, x) -> values[k] ?: x.value })) continue
                val forced = force.any { it == name || name.contains("[$it]") }
                if (cur.isNotBlank() && !forced) continue
                val v = when (f.type) {
                    "select" -> f.options.map { it.first }.firstOrNull { it.isNotEmpty() && it != cur } ?: f.options.map { it.first }.firstOrNull { it.isNotEmpty() }
                    "textarea" -> code
                    else -> "1"
                }
                if (v != null && v != cur) { values[name] = v; changed = true }
            }
            if (!changed) return
        }
    }
}
