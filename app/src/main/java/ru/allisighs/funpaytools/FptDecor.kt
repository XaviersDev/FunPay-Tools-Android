package ru.allisighs.funpaytools

import coil.compose.AsyncImage
import java.io.File
import okhttp3.Request
import org.json.JSONObject
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Рамки для аватарок и баннеры профиля FunPay Tools (как в расширении и FunPay Tools Desktop).
 * Кто что носит: decor.json на GitHub сайта (его пишет воркер fpt-promo). Картинки: funpay.tools/decor/.
 */
object FptDecor {
    const val BASE = "https://funpay.tools/decor/"

    data class Frame(val id: Int, val name: String, val file: String)
    data class Banner(val id: Int, val name: String, val file: String, val ar: Float)
    /** Доля аватарки от размера картинки рамки и сдвиг центра (замерено по картинкам). */
    data class Ring(val k: Float, val dx: Float, val dy: Float)

    val FRAMES = listOf(
        Frame(1, "Красный неон", "frames/f01.webp"), Frame(2, "Голубой неон", "frames/f02.webp"),
        Frame(3, "Фиолетовый неон", "frames/f03.webp"), Frame(4, "Оранжевый неон", "frames/f04.webp"),
        Frame(5, "Неоновый градиент", "frames/f05.webp"), Frame(6, "Серебряная орбита", "frames/f06.webp"),
        Frame(7, "Солнечная вспышка", "frames/f07.webp"), Frame(8, "Нимб", "frames/f08.webp"),
        Frame(9, "Молния", "frames/f09.webp"), Frame(10, "Чёрные розы", "frames/f10.webp"),
        Frame(11, "Белые розы", "frames/f11.webp"), Frame(12, "Фиолетовые розы", "frames/f12.webp"),
        Frame(13, "Лисичка", "frames/f13.webp"), Frame(14, "Чёрная кошка", "frames/f14.webp"),
        Frame(15, "Белая кошка", "frames/f15.webp"), Frame(16, "Бирюзовый вихрь", "frames/f16.webp"),
        Frame(17, "Листва", "frames/f17.webp"), Frame(18, "Полумесяц", "frames/f18.webp"),
        Frame(19, "Крылья", "frames/f19.webp"), Frame(20, "Клыки", "frames/f20.webp"),
        Frame(21, "Бабочки", "frames/f21.webp"), Frame(22, "Галактика", "frames/f22.webp"),
        Frame(23, "Звёздная орбита", "frames/f23.webp"), Frame(24, "Планета", "frames/f24.webp"),
        Frame(25, "Комета", "frames/f25.webp"), Frame(26, "Сакура", "frames/f26.webp"),
        Frame(27, "Ветка с луной", "frames/f27.webp"), Frame(28, "Талисман", "frames/f28.webp"),
        Frame(29, "Лёд", "frames/f29.webp"), Frame(30, "Пламя", "frames/f30.webp"),
    )

    val BANNERS = listOf(
        Banner(1, "Белые розы", "banners/b01.webp", 13.68f), Banner(2, "Красный фонарь", "banners/b02.webp", 13.30f),
        Banner(3, "Мотылёк", "banners/b03.webp", 13.49f), Banner(4, "Лесное озеро", "banners/b04.webp", 13.12f),
        Banner(5, "Ночное небо", "banners/b05.webp", 13.12f), Banner(6, "Чёрные розы", "banners/b06.webp", 13.12f),
        Banner(7, "Фиолетовая волна", "banners/b07.webp", 13.49f), Banner(8, "Красная сакура", "banners/b08.webp", 14.49f),
        Banner(9, "Светлячки", "banners/b09.webp", 14.49f), Banner(10, "Розовая сакура", "banners/b10.webp", 13.87f),
        Banner(11, "Пламя", "banners/b11.webp", 14.71f), Banner(12, "Витраж", "banners/b12.webp", 14.28f),
        Banner(14, "Паутина", "banners/b14.webp", 13.87f), Banner(15, "Неоновые лучи", "banners/b15.webp", 13.49f),
        Banner(16, "Бирюзовая сеть", "banners/b16.webp", 13.68f), Banner(17, "Кровавая луна", "banners/b17.webp", 13.04f),
        Banner(18, "Глаза", "banners/b18.webp", 13.19f), Banner(19, "Сердце", "banners/b19.webp", 13.69f),
        Banner(20, "Лунный замок", "banners/b20.webp", 14.60f), Banner(21, "Вороны", "banners/b21.webp", 15.87f),
        Banner(22, "Призрачная роза", "banners/b22.webp", 16.34f), Banner(23, "Бабочки", "banners/b23.webp", 16.34f),
        Banner(24, "Руки", "banners/b24.webp", 15.42f), Banner(25, "Снежный барс", "banners/b25.webp", 16.34f),
        Banner(26, "Алый взгляд", "banners/b26.webp", 15.64f), Banner(27, "Белый волк", "banners/b27.webp", 14.80f),
        Banner(28, "Когти", "banners/b28.webp", 13.52f),
    )

    val RING = listOf(
        Ring(0.682f, 0.023f, 0.059f), Ring(0.674f, 0.012f, 0.055f), Ring(0.682f, 0.004f, 0.059f), Ring(0.682f, 0.004f, 0.059f),
        Ring(0.682f, 0.004f, 0.059f), Ring(0.728f, -0.004f, 0.043f), Ring(0.713f, 0.027f, 0.027f), Ring(0.728f, 0.016f, 0.016f),
        Ring(0.705f, 0.004f, 0.012f), Ring(0.713f, 0.012f, 0.012f), Ring(0.721f, 0.008f, 0.016f), Ring(0.713f, -0.020f, 0.004f),
        Ring(0.658f, 0.012f, 0.031f), Ring(0.674f, 0.008f, 0.031f), Ring(0.674f, 0.000f, 0.031f), Ring(0.682f, -0.016f, 0.008f),
        Ring(0.713f, -0.004f, 0.031f), Ring(0.680f, 0.000f, 0.030f), Ring(0.585f, 0.000f, -0.053f), Ring(0.658f, -0.008f, 0.008f),
        Ring(0.705f, 0.004f, 0.000f), Ring(0.666f, 0.008f, 0.012f), Ring(0.682f, 0.016f, 0.035f), Ring(0.705f, -0.027f, 0.016f),
        Ring(0.713f, 0.027f, -0.055f), Ring(0.713f, 0.027f, -0.035f), Ring(0.744f, -0.012f, -0.047f), Ring(0.760f, -0.012f, -0.055f),
        Ring(0.705f, -0.020f, -0.055f), Ring(0.705f, 0.020f, -0.051f),
    )

    private val URLS = listOf(
        "https://raw.githubusercontent.com/XaviersDev/FunPayTools-Site/main/decor.json",
        "https://cdn.jsdelivr.net/gh/XaviersDev/FunPayTools-Site@main/decor.json",
    )

    private data class Entry(val n: String, val f: Int, val b: Int, val fu: Long?, val bu: Long?)

    private var map: Map<String, Entry> = emptyMap()
    private var local: Triple<String, Pair<Int, Int>, Long>? = null
    private var loaded = false

    /** Меняется при каждой загрузке: composable-ы перерисовываются. */
    val version = mutableIntStateOf(0)

    fun frameUrl(id: Int): String = FRAMES.firstOrNull { it.id == id }?.let { BASE + it.file } ?: ""
    fun bannerUrl(id: Int): String = BANNERS.firstOrNull { it.id == id }?.let { BASE + it.file } ?: ""
    fun bannerAr(id: Int): Float = BANNERS.firstOrNull { it.id == id }?.ar ?: 14f
    fun ring(id: Int): Ring? = RING.getOrNull(id - 1)

    private val cacheFile: File get() = File(PlatformBridge.filesDir, "decor.json")

    private fun parse(body: String): Map<String, Entry>? = try {
        val o = JSONObject(body)
        val out = HashMap<String, Entry>()
        for (k in o.keys()) {
            val e = o.optJSONObject(k) ?: continue
            fun num(key: String): Long? = if (!e.has(key) || e.isNull(key)) null else e.optString(key).trim().toDoubleOrNull()?.toLong()
            out[k] = Entry(e.optString("n"), num("f")?.toInt() ?: 0, num("b")?.toInt() ?: 0, num("fu"), num("bu"))
        }
        out
    } catch (e: Exception) { null }

    /** С диска сразу, потом из сети (и повторно раз в 10 минут). */
    fun init() {
        if (loaded) return
        loaded = true
        try { if (cacheFile.exists()) parse(cacheFile.readText())?.let { map = it; bump() } } catch (_: Exception) {}
        appScope.launch(Dispatchers.IO) {
            while (true) {
                refreshNow()
                delay(10 * 60_000L)
            }
        }
    }

    suspend fun refreshNow() {
        for (u in URLS) {
            try {
                val resp = ApiFactory.shared.newCall(Request.Builder().url("$u?t=${System.currentTimeMillis()}").build()).execute()
                if (!resp.isSuccessful) continue
                val body = resp.body?.string() ?: continue
                val m = parse(body) ?: continue
                try { cacheFile.writeText(body) } catch (_: Exception) {}
                map = m
                bump()
                break
            } catch (_: Exception) {}
        }
    }

    private fun bump() = PlatformBridge.runOnMain { version.intValue++ }

    /** Действующие рамка и баннер пользователя (0, если нет или срок вышел). */
    fun active(uid: String?): Pair<Int, Int> {
        if (uid.isNullOrBlank()) return 0 to 0
        val now = System.currentTimeMillis()
        local?.let { (id, fb, at) -> if (id == uid && now - at < 15 * 60_000L) return fb }
        val e = map[uid] ?: return 0 to 0
        val f = if (e.f > 0 && (e.fu == null || e.fu > now)) e.f else 0
        val b = if (e.b > 0 && (e.bu == null || e.bu > now)) e.b else 0
        return f to b
    }

    /** id на FunPay по нику (в списке чатов FunPay не отдаёт id собеседника, только ник). */
    fun uidByName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        return map.entries.firstOrNull { it.value.n.equals(name, ignoreCase = true) }?.key
    }

    fun setLocal(uid: String, frame: Int, banner: Int) {
        local = Triple(uid, frame to banner, System.currentTimeMillis())
        bump()
    }
}

/**
 * Аватарка с рамкой FunPay Tools: рамка рисуется поверх круга и выходит за его края.
 * uid или ник (для списка чатов, где id собеседника неизвестен).
 */
@Composable
fun FramedAvatar(uid: String?, name: String? = null, size: Dp, frameOverride: Int? = null, content: @Composable BoxScope.() -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = FptDecor.version.intValue
    val id = uid?.takeIf { it.isNotBlank() && it != "0" } ?: FptDecor.uidByName(name)
    val frame = frameOverride ?: FptDecor.active(id).first
    val ring = if (frame > 0) FptDecor.ring(frame) else null
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        content()
        if (ring != null) {
            val frameSize = size / ring.k.coerceAtLeast(0.3f)
            AsyncImage(
                model = FptDecor.frameUrl(frame),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .requiredSize(frameSize)
                    .offset(x = -(frameSize * ring.dx), y = -(frameSize * ring.dy))
            )
        }
    }
}

/** Баннер FunPay Tools в профиле (ничего не рисует, если баннера нет). */
@Composable
fun FptProfileBanner(uid: String?, modifier: Modifier = Modifier, bannerOverride: Int? = null) {
    @Suppress("UNUSED_VARIABLE") val v = FptDecor.version.intValue
    val b = bannerOverride ?: FptDecor.active(uid).second
    if (b <= 0) return
    AsyncImage(
        model = FptDecor.bannerUrl(b),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxWidth().aspectRatio(FptDecor.bannerAr(b)).clip(RoundedCornerShape(8.dp))
    )
}
