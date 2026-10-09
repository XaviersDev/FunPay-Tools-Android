package ru.allisighs.funpaytools

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.security.MessageDigest

/*
 * ============================================================================
 *  Сессия FunPay: куки, golden_seal, восстановление авторизации.
 *
 *  Почему это вынесено отдельно:
 *   1. Раньше PHPSESSID лежал в ОДНОМ глобальном ключе prefs "phpsessid" на все
 *      аккаунты, а прочие куки (в т.ч. golden_seal) жили только в памяти
 *      конкретного экземпляра FunPayRepository. Сервис, экран лотов и UI
 *      создают разные экземпляры → у каждого свой набор кук, сессии разных
 *      аккаунтов перемешивались, и FunPay отдавал гостевую страницу
 *      (userId = 0 → "Unknown" в профиле).
 *   2. FunPay выдаёт куку golden_seal (Set-Cookie на POST /runner/ с объектом
 *      chat_counter, живёт ~7 дней). Без неё nginx отвечает 428 на
 *      /lots/offerSave и /lots/raise. OkHttp по умолчанию проходит редиректы
 *      молча, и Set-Cookie из промежуточных ответов терялись.
 *
 *  Теперь:
 *   - FpCookieStore хранит куки на диске ОТДЕЛЬНО для каждого golden_key.
 *   - FpCookieCaptureInterceptor (network) ловит Set-Cookie с КАЖДОГО ответа,
 *     включая редиректы.
 *   - FpSessionInterceptor (application) подставляет актуальные куки в каждый
 *     запрос к funpay.com (даже если вызывающий код собрал строку
 *     "golden_key=..; PHPSESSID=.." руками) и при 428 обновляет golden_seal
 *     и повторяет запрос.
 * ============================================================================
 */

data class StoredCookie(
    val name: String = "",
    val value: String = "",
    /** 0 = сессионная кука без срока. */
    val expiresAt: Long = 0L,
    val setAt: Long = System.currentTimeMillis()
)

object FpCookieStore {
    private const val PREFS = "fp_cookie_store_v1"
    private val gson = Gson()
    private val lock = Any()
    private val cache = HashMap<String, MutableMap<String, StoredCookie>>()
    @Volatile private var prefs: SharedPreferences? = null

    /** Куки, которые никогда не храним/не подставляем из стора. */
    private val IGNORED = setOf("golden_key", "fav_games")

    private val FUNPAY_URL = "https://funpay.com/".toHttpUrl()

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(lock) {
                if (prefs == null) {
                    prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                }
            }
        }
    }

    fun keyFor(goldenKey: String?): String {
        if (goldenKey.isNullOrBlank()) return ""
        return try {
            val md = MessageDigest.getInstance("SHA-1").digest(goldenKey.trim().toByteArray())
            md.joinToString("") { "%02x".format(it) }.take(20)
        } catch (_: Exception) {
            goldenKey.trim().hashCode().toString()
        }
    }

    private fun load(key: String): MutableMap<String, StoredCookie> {
        cache[key]?.let { return it }
        val p = prefs
        val map: MutableMap<String, StoredCookie> = try {
            val json = p?.getString("c_$key", null)
            if (json.isNullOrBlank()) mutableMapOf()
            else {
                val type = object : TypeToken<Map<String, StoredCookie>>() {}.type
                val parsed: Map<String, StoredCookie>? = gson.fromJson(json, type)
                parsed?.filterValues { it.name.isNotBlank() }?.toMutableMap() ?: mutableMapOf()
            }
        } catch (_: Exception) {
            mutableMapOf()
        }
        cache[key] = map
        return map
    }

    private fun persist(key: String, map: Map<String, StoredCookie>) {
        try {
            prefs?.edit()?.putString("c_$key", gson.toJson(map))?.apply()
        } catch (_: Exception) {}
    }

    private fun isAlive(c: StoredCookie, now: Long = System.currentTimeMillis()): Boolean =
        c.value.isNotEmpty() && c.value != "deleted" && (c.expiresAt == 0L || c.expiresAt > now)

    fun get(goldenKey: String?, name: String): String? {
        val key = keyFor(goldenKey)
        if (key.isEmpty()) return null
        synchronized(lock) {
            val c = load(key)[name] ?: return null
            return if (isAlive(c)) c.value else null
        }
    }

    fun getCookie(goldenKey: String?, name: String): StoredCookie? {
        val key = keyFor(goldenKey)
        if (key.isEmpty()) return null
        synchronized(lock) {
            val c = load(key)[name] ?: return null
            return if (isAlive(c)) c else null
        }
    }

    fun put(goldenKey: String?, name: String, value: String, expiresAt: Long = 0L) {
        val key = keyFor(goldenKey)
        if (key.isEmpty() || name.isBlank() || name in IGNORED) return
        synchronized(lock) {
            val map = load(key)
            val old = map[name]
            if (value.isEmpty() || value == "deleted" || (expiresAt in 1..System.currentTimeMillis())) {
                if (old != null) {
                    map.remove(name)
                    persist(key, map)
                }
                return
            }
            if (old != null && old.value == value && old.expiresAt == expiresAt) return
            map[name] = StoredCookie(name, value, expiresAt)
            persist(key, map)
        }
    }

    fun remove(goldenKey: String?, name: String) {
        val key = keyFor(goldenKey)
        if (key.isEmpty()) return
        synchronized(lock) {
            val map = load(key)
            if (map.remove(name) != null) persist(key, map)
        }
    }

    fun clear(goldenKey: String?) {
        val key = keyFor(goldenKey)
        if (key.isEmpty()) return
        synchronized(lock) {
            cache[key] = mutableMapOf()
            try { prefs?.edit()?.remove("c_$key")?.apply() } catch (_: Exception) {}
        }
    }

    fun wipeAll() {
        synchronized(lock) {
            cache.clear()
            try { prefs?.edit()?.clear()?.apply() } catch (_: Exception) {}
        }
    }

    /** Все живые куки аккаунта (без golden_key). */
    fun all(goldenKey: String?): Map<String, String> {
        val key = keyFor(goldenKey)
        if (key.isEmpty()) return emptyMap()
        synchronized(lock) {
            val now = System.currentTimeMillis()
            return load(key).values.filter { isAlive(it, now) }.associate { it.name to it.value }
        }
    }

    /** Разбирает заголовки Set-Cookie и сохраняет их для данного golden_key. */
    fun handleSetCookie(goldenKey: String?, setCookieHeaders: List<String>) {
        if (goldenKey.isNullOrBlank() || setCookieHeaders.isEmpty()) return
        for (raw in setCookieHeaders) {
            try {
                val c = Cookie.parse(FUNPAY_URL, raw)
                if (c == null) {
                    // Fallback: name=value без атрибутов
                    val nv = raw.substringBefore(";").split("=", limit = 2)
                    if (nv.size == 2) put(goldenKey, nv[0].trim(), nv[1].trim(), 0L)
                    continue
                }
                if (c.name == "golden_key") continue
                val expires = if (c.persistent) c.expiresAt else 0L
                put(goldenKey, c.name, c.value, expires)
            } catch (_: Exception) {}
        }
    }

    /**
     * Импорт кук из WebView (CookieManager.getCookie отдаёт только name=value).
     * golden_seal без срока считаем живым неделю — как его выдаёт FunPay.
     */
    fun importFromCookieHeader(goldenKey: String?, cookieHeader: String?) {
        if (goldenKey.isNullOrBlank() || cookieHeader.isNullOrBlank()) return
        cookieHeader.split(";").forEach { part ->
            val nv = part.trim().split("=", limit = 2)
            if (nv.size != 2) return@forEach
            val name = nv[0].trim()
            val value = nv[1].trim()
            if (name.isEmpty() || name in IGNORED) return@forEach
            val exp = if (name == "golden_seal") System.currentTimeMillis() + 6L * 24 * 3600 * 1000 else 0L
            put(goldenKey, name, value, exp)
        }
    }

    /**
     * Собирает заголовок Cookie для запроса к FunPay.
     * @param includeSession false → без PHPSESSID (FunPay создаст новую сессию по golden_key,
     *        ровно так делает FunPay Cardinal при обновлении сессии).
     */
    fun buildCookieHeader(goldenKey: String?, includeSession: Boolean = true): String {
        val gk = goldenKey?.trim().orEmpty()
        val sb = StringBuilder()
        sb.append("golden_key=").append(gk)
        val cookies = all(gk)
        if (!cookies.containsKey("cookie_prefs")) sb.append("; cookie_prefs=1")
        for ((k, v) in cookies) {
            if (k == "PHPSESSID" && !includeSession) continue
            sb.append("; ").append(k).append('=').append(v)
        }
        return sb.toString()
    }

    fun parseCookieHeader(header: String?): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        header?.split(";")?.forEach { part ->
            val nv = part.trim().split("=", limit = 2)
            if (nv.size == 2 && nv[0].isNotBlank()) out[nv[0].trim()] = nv[1].trim()
        }
        return out
    }
}

/**
 * Network-интерцептор: видит каждый реальный ответ (включая 301/302),
 * поэтому ни одна Set-Cookie от FunPay больше не теряется.
 */
class FpCookieCaptureInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        try {
            if (request.url.host.endsWith("funpay.com")) {
                val gk = FpCookieStore.parseCookieHeader(request.header("Cookie"))["golden_key"]
                val setCookies = response.headers("Set-Cookie")
                if (!gk.isNullOrBlank() && setCookies.isNotEmpty()) {
                    FpCookieStore.handleSetCookie(gk, setCookies)
                }
            }
        } catch (_: Exception) {}
        return response
    }
}

/**
 * Application-интерцептор:
 *  - подменяет куки в запросе на актуальные из стора (по golden_key из заголовка);
 *  - при 428 (нет/протух golden_seal) обновляет golden_seal и повторяет запрос один раз.
 *
 *  Заголовок "X-Fp-No-Session: 1" — отправить запрос без PHPSESSID (новая сессия).
 */
class FpSessionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!original.url.host.endsWith("funpay.com")) return chain.proceed(original)

        val noSession = original.header(FpSession.HEADER_NO_SESSION) == "1"
        val base = original.newBuilder().removeHeader(FpSession.HEADER_NO_SESSION).build()
        val gk = FpCookieStore.parseCookieHeader(base.header("Cookie"))["golden_key"]
        if (gk.isNullOrBlank()) return chain.proceed(base)

        var response = chain.proceed(withStoreCookies(base, gk, noSession))

        if (response.code == 428) {
            response.close()
            FpSession.log("🔏 FunPay вернул 428 — обновляю golden_seal и повторяю запрос")
            try {
                chain.proceed(FpSession.buildSealRefreshRequest(base, gk)).close()
            } catch (_: Exception) {}
            FpSession.lastSealRefreshAt[FpCookieStore.keyFor(gk)] = System.currentTimeMillis()
            response = chain.proceed(withStoreCookies(base, gk, noSession))
            if (response.code == 428) {
                FpSession.sealProblem.value = true
                FpSession.log("⛔ FunPay всё ещё отвечает 428: golden_seal не выдан. Перезайдите в аккаунт через сайт.")
            } else {
                FpSession.sealProblem.value = false
            }
        }
        return response
    }

    private fun withStoreCookies(req: Request, gk: String, noSession: Boolean): Request {
        val header = FpCookieStore.parseCookieHeader(req.header("Cookie"))
        val store = FpCookieStore.all(gk)
        // Значения из стора всегда свежее, чем то, что собрал вызывающий код руками.
        for ((k, v) in store) header[k] = v
        if (!header.containsKey("cookie_prefs")) header["cookie_prefs"] = "1"
        if (noSession) header.remove("PHPSESSID")
        // golden_key — первым, как в браузере
        val sb = StringBuilder("golden_key=").append(gk)
        for ((k, v) in header) {
            if (k == "golden_key") continue
            sb.append("; ").append(k).append('=').append(v)
        }
        return req.newBuilder().header("Cookie", sb.toString()).build()
    }
}

data class SessionLostInfo(
    val goldenKeyHash: String,
    val accountId: String?,
    val username: String?,
    val since: Long,
    /** Сессия слетела повторно вскоре (≤ 2 мин) после повторного входа через сайт. */
    val repeatedAfterRelogin: Boolean
)

object FpSession {
    const val HEADER_NO_SESSION = "X-Fp-No-Session"
    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"

    /** Обновлять сессию с нуля (без PHPSESSID) не реже, чем раз в столько мс — как Cardinal. */
    const val FRESH_SESSION_INTERVAL = 50L * 60 * 1000
    /** Через сколько после перелогина повторный вылет считаем "не помогло, нужен golden_key". */
    const val RELOGIN_WINDOW_MS = 2L * 60 * 1000

    val lastSealRefreshAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** keyFor(golden_key) → userId FunPay (нужен для объекта chat_counter). */
    val knownUserIds = java.util.concurrent.ConcurrentHashMap<String, String>()
    val lastFreshSessionAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val _sessionLost = MutableStateFlow<SessionLostInfo?>(null)
    val sessionLost: StateFlow<SessionLostInfo?> = _sessionLost.asStateFlow()

    /** true — FunPay даже после обновления не выдал golden_seal (видно 428). */
    val sealProblem = MutableStateFlow(false)

    private const val PREFS = "fp_session_meta"

    fun log(msg: String) {
        try { LogManager.addLog(msg) } catch (_: Exception) {}
    }

    fun buildSealRefreshRequest(base: Request, gk: String): Request {
        val uid = knownUserIds[FpCookieStore.keyFor(gk)] ?: "0"
        val tag = (1..8).map { "0123456789abcdef".random() }.joinToString("")
        val objects = "[{\"type\":\"chat_counter\",\"id\":\"$uid\",\"tag\":\"$tag\",\"data\":false}]"
        val body = FormBody.Builder()
            .add("objects", objects)
            .add("request", "false")
            .build()
        val cookie = FpCookieStore.buildCookieHeader(gk, includeSession = true)
        return Request.Builder()
            .url("https://funpay.com/runner/")
            .post(body)
            .header("Cookie", cookie)
            .header("User-Agent", base.header("User-Agent") ?: UA)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Origin", "https://funpay.com")
            .header("Referer", "https://funpay.com/")
            .build()
    }

    fun markRelogin(context: Context, goldenKey: String?) {
        val key = FpCookieStore.keyFor(goldenKey)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("relogin_at", System.currentTimeMillis())
            .putString("relogin_key", key)
            .apply()
        _sessionLost.value = null
        sealProblem.value = false
    }

    fun markAuthorized(goldenKey: String?) {
        val cur = _sessionLost.value ?: return
        if (cur.goldenKeyHash == FpCookieStore.keyFor(goldenKey)) _sessionLost.value = null
    }

    fun markLost(context: Context, goldenKey: String?, account: Account?) {
        val key = FpCookieStore.keyFor(goldenKey)
        if (key.isEmpty()) return
        val cur = _sessionLost.value
        if (cur != null && cur.goldenKeyHash == key) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val reloginAt = p.getLong("relogin_at", 0L)
        val repeated = p.getString("relogin_key", null) == key &&
                System.currentTimeMillis() - reloginAt <= RELOGIN_WINDOW_MS
        _sessionLost.value = SessionLostInfo(
            goldenKeyHash = key,
            accountId = account?.id,
            username = account?.username,
            since = System.currentTimeMillis(),
            repeatedAfterRelogin = repeated
        )
        log(if (repeated) "⛔ Сессия снова слетела сразу после входа — войдите через golden_key"
            else "⛔ FunPay не принимает сессию (гостевая страница). Нужен повторный вход.")
    }

    fun dismiss() { _sessionLost.value = null }

    /** Аккаунт, для которого пользователь нажал "Войти заново" — новый вход обновит именно его. */
    fun setPendingRelogin(context: Context, accountId: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("pending_relogin_account", accountId).apply()
    }

    fun takePendingRelogin(context: Context): String? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getString("pending_relogin_account", null)
        if (id != null) p.edit().remove("pending_relogin_account").apply()
        return id
    }
}
