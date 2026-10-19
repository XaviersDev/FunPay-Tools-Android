package ru.allisighs.funpaytools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/* ============================================================================
 *  Стикеры FunPay Tools: свои паки лежат в репозитории на GitHub.
 *
 *  stickers/index.json                 — список паков
 *  stickers/<pack>/<file>.webp         — стикер 512px
 *  stickers/<pack>/t/<file>.webp       — превью 160px (грузится первым, как в Telegram)
 *
 *  index.json:
 *  { "version": 1, "packs": [ { "id": "cats", "title": "Котики", "icon": "01.webp",
 *      "stickers": [ { "file": "01.webp", "emoji": "😺" } ] } ] }
 *
 *  Список берётся с raw.githubusercontent.com (свежий), при ошибке с jsDelivr.
 *  Картинки грузятся с того же хоста, что ответил, и кешируются Coil на диске.
 *  В FunPay стикер уходит обычной картинкой PNG.
 * ==========================================================================*/

/** Иконка «стикер»: квадратик с загнутым уголком и мордочкой, как в Telegram (в Material Icons такой нет). */
val StickerIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    val stroke = androidx.compose.ui.graphics.SolidColor(Color.Black)
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "Sticker", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        // Контур со срезанным правым нижним углом
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
                "M7,3.5H17A3.5,3.5 0 0 1 20.5,7V13.2L13.2,20.5H7A3.5,3.5 0 0 1 3.5,17V7A3.5,3.5 0 0 1 7,3.5Z"
            ).toNodes(),
            stroke = stroke, strokeLineWidth = 1.8f,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        )
        // Загнутый уголок
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
                "M20.5,13.2H16.4A3.2,3.2 0 0 0 13.2,16.4V20.5"
            ).toNodes(),
            stroke = stroke, strokeLineWidth = 1.8f,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        // Глазки
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
                "M9.2,9.6m-1.15,0a1.15,1.15 0 1 0 2.3,0a1.15,1.15 0 1 0 -2.3,0M14.8,9.6m-1.15,0a1.15,1.15 0 1 0 2.3,0a1.15,1.15 0 1 0 -2.3,0"
            ).toNodes(),
            fill = stroke
        )
        // Улыбка
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M8.4,13.2Q10.4,15.4 12.6,14.2").toNodes(),
            stroke = stroke, strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }.build()
}

data class StickerItem(val file: String, val emoji: String = "")

data class StickerPack(
    val id: String,
    val title: String,
    val icon: String?,
    val hasThumbs: Boolean,
    val stickers: List<StickerItem>
)

object StickerStore {
    const val REPO = "XaviersDev/FunPay-Tools-Android"
    const val BRANCH = "main"
    const val DIR = "stickers"

    private const val RAW = "https://raw.githubusercontent.com/$REPO/$BRANCH/$DIR/"
    private const val CDN = "https://cdn.jsdelivr.net/gh/$REPO@$BRANCH/$DIR/"

    private const val PREFS = "fpt_stickers"
    private const val KEY_MANIFEST = "manifest"
    private const val KEY_BASE = "base"
    private const val KEY_TS = "ts"
    private const val KEY_RECENT = "recent"
    private const val KEY_PANEL_H = "panel_h"
    private const val TTL_MS = 30L * 60 * 1000
    private const val MAX_RECENT = 24

    private val _packs = MutableStateFlow<List<StickerPack>>(emptyList())
    val packs: StateFlow<List<StickerPack>> = _packs.asStateFlow()

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    /** null — ещё грузим, true — есть данные, false — ошибка и пусто */
    private val _state = MutableStateFlow<Boolean?>(null)
    val state: StateFlow<Boolean?> = _state.asStateFlow()

    @Volatile private var base: String = RAW
    @Volatile private var loadedFromPrefs = false
    private val mutex = Mutex()

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun thumbUrl(pack: StickerPack, item: StickerItem): String =
        if (pack.hasThumbs) "$base${pack.id}/t/${item.file}" else fullUrl(pack, item)

    fun fullUrl(pack: StickerPack, item: StickerItem): String = "$base${pack.id}/${item.file}"

    fun iconUrl(pack: StickerPack): String? {
        val icon = pack.icon ?: pack.stickers.firstOrNull()?.file ?: return null
        return if (pack.hasThumbs) "$base${pack.id}/t/$icon" else "$base${pack.id}/$icon"
    }

    fun key(pack: StickerPack, item: StickerItem) = "${pack.id}/${item.file}"

    fun find(key: String): Pair<StickerPack, StickerItem>? {
        val packId = key.substringBefore('/')
        val file = key.substringAfter('/')
        val pack = _packs.value.firstOrNull { it.id == packId } ?: return null
        val item = pack.stickers.firstOrNull { it.file == file } ?: return null
        return pack to item
    }

    /** Высота панели = высота клавиатуры в прошлый раз, чтобы переключение было без прыжка. */
    fun panelHeightDp(c: Context): Int = prefs(c).getInt(KEY_PANEL_H, 0)
    fun savePanelHeightDp(c: Context, dp: Int) {
        if (dp in 180..520 && dp != panelHeightDp(c)) prefs(c).edit().putInt(KEY_PANEL_H, dp).apply()
    }

    fun loadCached(c: Context) {
        if (loadedFromPrefs) return
        loadedFromPrefs = true
        val p = prefs(c)
        base = p.getString(KEY_BASE, RAW) ?: RAW
        _recent.value = p.getString(KEY_RECENT, "").orEmpty().split('\n').filter { it.isNotBlank() }
        p.getString(KEY_MANIFEST, null)?.let { json ->
            parse(json)?.let { _packs.value = it; _state.value = true }
        }
    }

    /** Обновляет список паков. Без force — не чаще раза в 30 минут. */
    suspend fun refresh(c: Context, force: Boolean = false) = mutex.withLock {
        loadCached(c)
        val p = prefs(c)
        if (!force && _packs.value.isNotEmpty() && System.currentTimeMillis() - p.getLong(KEY_TS, 0) < TTL_MS) return@withLock
        if (_packs.value.isEmpty()) _state.value = null
        val ok = withContext(Dispatchers.IO) {
            for (b in listOf(RAW, CDN)) {
                try {
                    val req = Request.Builder().url("${b}index.json?t=${System.currentTimeMillis() / 60000}").build()
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) return@use
                        val body = r.body?.string().orEmpty()
                        val parsed = parse(body) ?: return@use
                        base = b
                        _packs.value = parsed
                        p.edit()
                            .putString(KEY_MANIFEST, body)
                            .putString(KEY_BASE, b)
                            .putLong(KEY_TS, System.currentTimeMillis())
                            .apply()
                        return@withContext true
                    }
                } catch (_: Exception) {}
            }
            false
        }
        _state.value = ok || _packs.value.isNotEmpty()
    }

    internal fun parse(json: String): List<StickerPack>? = try {
        val root = JSONObject(json)
        val arr = root.optJSONArray("packs")
        if (arr == null) null else {
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").trim().trim('/')
                if (id.isEmpty()) return@mapNotNull null
                val st = o.optJSONArray("stickers") ?: return@mapNotNull null
                val items = (0 until st.length()).mapNotNull { j ->
                    when (val v = st.opt(j)) {
                        is JSONObject -> v.optString("file").takeIf { it.isNotBlank() }?.let { StickerItem(it, v.optString("emoji")) }
                        is String -> v.takeIf { it.isNotBlank() }?.let { StickerItem(it) }
                        else -> null
                    }
                }
                if (items.isEmpty()) null
                else StickerPack(
                    id = id,
                    title = o.optString("title").ifBlank { id },
                    icon = o.optString("icon").takeIf { it.isNotBlank() },
                    hasThumbs = o.optBoolean("thumbs", true),
                    stickers = items
                )
            }
        }
    } catch (_: Exception) { null }

    /** Для тестов: свои паки и адрес без сети. */
    internal fun setForTest(baseUrl: String, list: List<StickerPack>, recentKeys: List<String> = emptyList()) {
        base = baseUrl
        loadedFromPrefs = true
        _packs.value = list
        _recent.value = recentKeys
        _state.value = true
    }

    fun addRecent(c: Context, pack: StickerPack, item: StickerItem) {
        val k = key(pack, item)
        val next = (listOf(k) + _recent.value.filter { it != k }).take(MAX_RECENT)
        _recent.value = next
        prefs(c).edit().putString(KEY_RECENT, next.joinToString("\n")).apply()
    }

    /** Заранее кладёт в кеш иконки паков и превью первых стикеров, чтобы панель открывалась мгновенно. */
    fun prefetch(c: Context, thumbPx: Int) {
        val loader = c.imageLoader
        val urls = buildList {
            _packs.value.forEach { p -> iconUrl(p)?.let { add(it) } }
            _recent.value.mapNotNull { find(it) }.forEach { (p, s) -> add(thumbUrl(p, s)) }
            _packs.value.take(2).forEach { p -> p.stickers.take(24).forEach { add(thumbUrl(p, it)) } }
        }.distinct()
        urls.forEach { u ->
            loader.enqueue(ImageRequest.Builder(c).data(u).size(thumbPx).memoryCacheKey(u + "#t").build())
        }
    }

    /** Готовит PNG для отправки в FunPay (прозрачность сохраняется). */
    suspend fun preparePng(c: Context, pack: StickerPack, item: StickerItem): File? = withContext(Dispatchers.IO) {
        try {
            val req = ImageRequest.Builder(c)
                .data(fullUrl(pack, item))
                .allowHardware(false)
                .size(512)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            val res = c.imageLoader.execute(req) as? SuccessResult ?: return@withContext null
            val bmp: Bitmap = (res.drawable as? BitmapDrawable)?.bitmap ?: res.drawable.toBitmap()
            val safe = (pack.id + "_" + item.file.substringBeforeLast('.')).replace(Regex("[^A-Za-z0-9_-]"), "_")
            val out = File(c.cacheDir, "sticker_$safe.png")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            out
        } catch (_: Exception) { null }
    }
}

private data class StickerCell(val pack: StickerPack, val item: StickerItem)
private sealed class GridEntry {
    data class Header(val title: String, val key: String) : GridEntry()
    data class Cell(val cell: StickerCell, val key: String) : GridEntry()
}

/**
 * Панель стикеров как в Telegram: сверху вкладки паков, ниже одна общая сетка с заголовками.
 * Тап — отправить, долгое нажатие — посмотреть крупно.
 */
@Composable
fun StickerPanel(
    height: Dp,
    palette: ChatPalette,
    theme: AppTheme,
    onSend: (StickerPack, StickerItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val packs by StickerStore.packs.collectAsState()
    val recent by StickerStore.recent.collectAsState()
    val state by StickerStore.state.collectAsState()
    val thumbPx = with(density) { 72.dp.roundToPx() }

    LaunchedEffect(Unit) {
        StickerStore.refresh(context)
        StickerStore.prefetch(context, thumbPx)
    }

    val recentCells = remember(recent, packs) {
        recent.mapNotNull { StickerStore.find(it) }.map { StickerCell(it.first, it.second) }
    }
    // Одна сетка: «Недавние» + все паки. Вкладка = индекс заголовка секции.
    val (entries, sectionStarts) = remember(packs, recentCells) {
        val list = ArrayList<GridEntry>()
        val starts = ArrayList<Int>()
        if (recentCells.isNotEmpty()) {
            starts += list.size
            list += GridEntry.Header("Недавние", "h_recent")
            recentCells.forEach { list += GridEntry.Cell(it, "r_" + StickerStore.key(it.pack, it.item)) }
        }
        packs.forEach { p ->
            starts += list.size
            list += GridEntry.Header(p.title, "h_" + p.id)
            p.stickers.forEach { s -> list += GridEntry.Cell(StickerCell(p, s), "s_" + StickerStore.key(p, s)) }
        }
        list to starts
    }
    val hasRecent = recentCells.isNotEmpty()
    val gridState = rememberLazyGridState()
    val tabsState = rememberLazyListState()
    val currentSection by remember(sectionStarts) {
        derivedStateOf {
            val first = gridState.firstVisibleItemIndex
            sectionStarts.indexOfLast { it <= first }.coerceAtLeast(0)
        }
    }
    LaunchedEffect(currentSection) {
        if (sectionStarts.isNotEmpty()) try { tabsState.animateScrollToItem(currentSection.coerceAtMost(sectionStarts.lastIndex)) } catch (_: Exception) {}
    }

    var preview by remember { mutableStateOf<StickerCell?>(null) }
    val panelBg = if (palette.isLight) Color(0xFFF7F8FA) else ThemeManager.dialogSurface(theme)

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(panelBg)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Вкладки паков
            LazyRow(
                state = tabsState,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs: List<StickerPack?> = (if (hasRecent) listOf<StickerPack?>(null) else emptyList()) + packs
                itemsIndexed(tabs, key = { i, p -> p?.id ?: "recent_$i" }) { i, p ->
                    val selected = i == currentSection
                    val bg by animateColorAsState(if (selected) palette.accent.copy(alpha = 0.16f) else Color.Transparent, label = "tab")
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .clickable {
                                val target = sectionStarts.getOrNull(i) ?: return@clickable
                                scope.launch { try { gridState.animateScrollToItem(target) } catch (_: Exception) {} }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (p == null) {
                            Icon(Icons.Default.AccessTime, "Недавние", tint = if (selected) palette.accent else palette.secondary, modifier = Modifier.size(22.dp))
                        } else {
                            val url = StickerStore.iconUrl(p)
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(url).size(thumbPx).memoryCacheKey(url + "#t").crossfade(120).build(),
                                contentDescription = p.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.secondary.copy(alpha = 0.15f)))

            when {
                entries.isEmpty() && state == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(26.dp), color = palette.accent, strokeWidth = 2.dp)
                }
                entries.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (state == false) "Стикеры не загрузились" else "Стикеров пока нет",
                        color = palette.secondary, fontSize = 14.sp
                    )
                    TextButton(onClick = { scope.launch { StickerStore.refresh(context, force = true) } }) {
                        Icon(Icons.Default.Refresh, null, tint = palette.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Повторить", color = palette.accent)
                    }
                }
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(76.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 10.dp)
                ) {
                    items(
                        count = entries.size,
                        key = { entries[it].let { e -> if (e is GridEntry.Header) e.key else (e as GridEntry.Cell).key } },
                        span = { if (entries[it] is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                        contentType = { if (entries[it] is GridEntry.Header) 0 else 1 }
                    ) { idx ->
                        when (val e = entries[idx]) {
                            is GridEntry.Header -> Text(
                                e.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
                            )
                            is GridEntry.Cell -> StickerCellView(
                                cell = e.cell,
                                thumbPx = thumbPx,
                                palette = palette,
                                onTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSend(e.cell.pack, e.cell.item)
                                },
                                onPreview = { show ->
                                    if (show) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    preview = if (show) e.cell else null
                                }
                            )
                        }
                    }
                }
            }
        }

        // Крупный просмотр по долгому нажатию (отпустил палец — закрылся)
        AnimatedVisibility(
            visible = preview != null,
            enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.85f),
            exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.9f),
            modifier = Modifier.matchParentSize()
        ) {
            val p = preview
            Box(
                Modifier.fillMaxSize().background(panelBg.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                if (p != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(StickerStore.fullUrl(p.pack, p.item))
                                .placeholderMemoryCacheKey(StickerStore.thumbUrl(p.pack, p.item) + "#t")
                                .crossfade(150)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(200.dp)
                        )
                        if (p.item.emoji.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(p.item.emoji, fontSize = 26.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerCellView(
    cell: StickerCell,
    thumbPx: Int,
    palette: ChatPalette,
    onTap: () -> Unit,
    onPreview: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "stk")
    val url = StickerStore.thumbUrl(cell.pack, cell.item)
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(thumbPx)
            .memoryCacheKey("$url#t")
            .crossfade(140)
            .build()
    }
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(cell) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                        onPreview(false)
                    },
                    onLongPress = { onPreview(true) },
                    onTap = { onTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) {
            // Мягкая заглушка, пока превью летит из сети
            Box(
                Modifier
                    .fillMaxSize(0.72f)
                    .clip(CircleShape)
                    .background(palette.secondary.copy(alpha = 0.08f))
            )
        }
        AsyncImage(
            model = request,
            contentDescription = cell.item.emoji.ifBlank { null },
            contentScale = ContentScale.Fit,
            onSuccess = { loaded = true },
            modifier = Modifier.fillMaxSize()
        )
    }
}
