package ru.allisighs.funpaytools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ============================================================================
 *  Общий чат FunPay Tools — тот же, что во вкладке расширения.
 *  API: POST https://fpt-chat.starobinskiy01.workers.dev, поле "action":
 *    start → {code}; poll {code} → {token}; fetch {since} → {messages}; send {...}
 *  Вход — один раз через @FPToolsBot. Читать можно без входа.
 *  Удалённый выключатель: public-chat.json в репозитории расширения.
 * ==========================================================================*/

data class GlobalChatMessage(
    val id: String,
    val ts: Long,
    val nick: String,
    val avatar: String,
    val url: String,
    val text: String,
    val pro: String = ""
)

data class GlobalChatConfig(
    val active: Boolean = true,
    val display: Boolean = true,
    val disabledMessage: String = "Общий чат временно недоступен. Ожидайте."
)

data class GcResult(val ok: Boolean, val status: Int, val json: JSONObject)

object GlobalChatApi {
    const val WORKER = "https://fpt-chat.starobinskiy01.workers.dev"
    const val BOT = "FPToolsBot"
    const val MAX_LEN = 300
    private const val PREFS = "global_chat"
    private const val CFG_TTL_MS = 16L * 60 * 1000
    private val CFG_URLS = listOf(
        "https://cdn.jsdelivr.net/gh/XaviersDev/FunPay-Tools@main/public-chat.json",
        "https://raw.githubusercontent.com/XaviersDev/FunPay-Tools/main/public-chat.json"
    )

    @Volatile var config: GlobalChatConfig = GlobalChatConfig()
        private set

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun token(c: Context): String? = prefs(c).getString("token", null)?.takeIf { it.isNotBlank() }
    fun saveToken(c: Context, t: String?) {
        prefs(c).edit().apply { if (t == null) remove("token") else putString("token", t) }.apply()
    }

    /** Кэшированный конфиг (для решения, показывать ли вкладку, без сети). */
    fun cachedConfig(c: Context): GlobalChatConfig {
        val json = prefs(c).getString("cfg", null) ?: return config
        return try { parseConfig(JSONObject(json)).also { config = it } } catch (_: Exception) { config }
    }

    private fun parseConfig(o: JSONObject) = GlobalChatConfig(
        active = o.optBoolean("active", true),
        display = o.optBoolean("display", true),
        disabledMessage = o.optString("disabledMessage").ifBlank { GlobalChatConfig().disabledMessage }
    )

    suspend fun refreshConfig(c: Context, force: Boolean = false): GlobalChatConfig = withContext(Dispatchers.IO) {
        val p = prefs(c)
        val ts = p.getLong("cfg_ts", 0L)
        if (!force && System.currentTimeMillis() - ts < CFG_TTL_MS) return@withContext cachedConfig(c)
        for (base in CFG_URLS) {
            try {
                val req = Request.Builder().url("$base?t=${System.currentTimeMillis()}").build()
                ApiFactory.shared.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use
                    val body = r.body?.string().orEmpty()
                    val cfg = parseConfig(JSONObject(body))
                    p.edit().putString("cfg", body).putLong("cfg_ts", System.currentTimeMillis()).apply()
                    config = cfg
                    return@withContext cfg
                }
            } catch (_: Exception) {}
        }
        cachedConfig(c)
    }

    suspend fun call(payload: JSONObject): GcResult = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(WORKER)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        ApiFactory.shared.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
            GcResult(r.isSuccessful, r.code, json)
        }
    }

    suspend fun fetch(since: Long): List<GlobalChatMessage> {
        val res = call(JSONObject().put("action", "fetch").put("since", since))
        val arr = res.json.optJSONArray("messages") ?: return emptyList()
        val out = ArrayList<GlobalChatMessage>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            out.add(
                GlobalChatMessage(
                    id = id,
                    ts = o.optLong("ts"),
                    nick = o.optString("nick", "FunPay user"),
                    avatar = o.optString("avatar"),
                    url = o.optString("url"),
                    text = o.optString("text"),
                    pro = o.optString("pro")
                )
            )
        }
        return out
    }
}

private sealed class GcGate {
    object None : GcGate()
    object Starting : GcGate()
    data class Waiting(val code: String, val status: String = "Ожидаю подтверждения…") : GcGate()
    data class Error(val message: String) : GcGate()
}

private val gcTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

@Composable
fun GlobalChatView(navController: NavController, theme: AppTheme, repository: FunPayRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val palette = rememberChatPalette(theme)
    val listState = rememberLazyListState()

    var config by remember { mutableStateOf(GlobalChatApi.cachedConfig(context)) }
    var messages by remember { mutableStateOf<List<GlobalChatMessage>>(emptyList()) }
    var lastTs by remember { mutableLongStateOf(0L) }
    var loaded by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(GlobalChatApi.token(context)) }
    var gate by remember { mutableStateOf<GcGate>(GcGate.None) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    // Выделение и ответы — как в личных чатах
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    val selectionModeState = rememberUpdatedState(isSelectionMode)
    var pressedId by remember { mutableStateOf<String?>(null) }
    var pulseId by remember { mutableStateOf<String?>(null) }
    var pulseCounter by remember { mutableIntStateOf(0) }
    var dragBase by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dragAnchor by remember { mutableStateOf<String?>(null) }
    var dragSelect by remember { mutableStateOf(true) }
    var replyTo by remember { mutableStateOf<ParsedMessage?>(null) }
    var flashId by remember { mutableStateOf<String?>(null) }

    val account = remember { repository.getActiveAccount() }
    val selfName = account?.username?.takeIf { it.isNotBlank() && it != "Unknown" }
    val selfUrl = account?.userId?.takeIf { it.isNotBlank() && it != "0" }?.let { "https://funpay.com/users/$it/" }.orEmpty()
    val selfAvatar = account?.avatarUrl.orEmpty()

    suspend fun pull() {
        try {
            val fresh = GlobalChatApi.fetch(lastTs)
            if (fresh.isNotEmpty()) {
                val merged = (messages + fresh).distinctBy { it.id }.sortedBy { it.ts }.takeLast(500)
                val wasAtBottom = run {
                    val info = listState.layoutInfo
                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
                }
                messages = merged
                lastTs = merged.maxOf { it.ts }
                if (!loaded || wasAtBottom) {
                    delay(30)
                    try { listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        loaded = true
    }

    LaunchedEffect(Unit) {
        config = GlobalChatApi.refreshConfig(context)
        while (isActive) {
            if (config.active) pull()
            delay(6000)
        }
    }

    // Ожидание подтверждения входа в боте
    LaunchedEffect(gate) {
        val g = gate
        if (g is GcGate.Waiting) {
            repeat(160) {
                delay(2500)
                try {
                    val res = GlobalChatApi.call(JSONObject().put("action", "poll").put("code", g.code))
                    val t = res.json.optString("token").takeIf { it.isNotBlank() && it != "null" }
                    if (t != null) {
                        GlobalChatApi.saveToken(context, t)
                        token = t
                        gate = GcGate.None
                        Toast.makeText(context, "Вход в общий чат подтверждён", Toast.LENGTH_SHORT).show()
                        return@LaunchedEffect
                    }
                } catch (_: Exception) {}
            }
            gate = GcGate.Error("Время вышло. Нажмите «Войти в чат» ещё раз.")
        }
    }

    fun openBot(code: String) {
        val tg = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=${GlobalChatApi.BOT}&start=fptchat_$code"))
        try { context.startActivity(tg) } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/${GlobalChatApi.BOT}?start=fptchat_$code")))
            } catch (_: Exception) {}
        }
    }

    fun startLink() {
        gate = GcGate.Starting
        scope.launch {
            try {
                val res = GlobalChatApi.call(JSONObject().put("action", "start"))
                val code = res.json.optString("code")
                if (res.ok && code.isNotBlank()) {
                    gate = GcGate.Waiting(code)
                    openBot(code)
                } else gate = GcGate.Error("Не удалось начать вход. Попробуйте ещё раз.")
            } catch (_: Exception) {
                gate = GcGate.Error("Сеть недоступна.")
            }
        }
    }

    fun send() {
        val typed = input.trim()
        val r = replyTo
        val text = if (r != null && typed.isNotEmpty()) {
            // Цитата в формате расширения, укорачиваем, чтобы влезть в лимит 300 символов
            val room = (GlobalChatApi.MAX_LEN - typed.length - 6).coerceIn(0, 100)
            val q = r.text.replace(Regex("\\s+"), " ").trim().ifEmpty { "Сообщение" }
            val quote = if (q.length > room) q.take((room - 1).coerceAtLeast(1)) + "…" else q
            "╭─ ⤸ $quote\n╰ $typed"
        } else typed
        if (text.isEmpty() || sending) return
        if (text.length > GlobalChatApi.MAX_LEN) {
            status = "Слишком длинное сообщение (максимум ${GlobalChatApi.MAX_LEN} символов)"; statusIsError = true
            return
        }
        val t = token ?: run { gate = GcGate.None; return }
        sending = true
        status = null
        scope.launch {
            try {
                val res = GlobalChatApi.call(
                    JSONObject()
                        .put("action", "send")
                        .put("token", t)
                        .put("nick", selfName ?: "FunPay user")
                        .put("avatar", selfAvatar)
                        .put("url", selfUrl)
                        .put("text", text)
                )
                when {
                    res.ok && res.json.optBoolean("ok", true) -> {
                        input = ""
                        replyTo = null
                        pull()
                        try { listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } catch (_: Exception) {}
                    }
                    res.status == 429 -> {
                        val sec = ((res.json.optLong("wait", 5000L) + 999) / 1000)
                        status = "Подождите $sec сек перед следующим сообщением"; statusIsError = true
                    }
                    res.status == 401 -> {
                        GlobalChatApi.saveToken(context, null)
                        token = null
                        status = "Вход истёк — войдите в чат заново"; statusIsError = true
                    }
                    res.status == 403 -> { status = "Доступ к общему чату ограничен."; statusIsError = true }
                    else -> { status = "Не удалось отправить: ${res.json.optString("error").ifBlank { "ошибка ${res.status}" }}"; statusIsError = true }
                }
            } catch (_: Exception) {
                status = "Сеть недоступна, попробуйте ещё раз"; statusIsError = true
            } finally {
                sending = false
            }
        }
    }

    val parsed = remember(messages, selfName, selfUrl) {
        messages.map { m ->
            val uid = Regex("/users/(\\d+)").find(m.url)?.groupValues?.get(1)
            val (quote, body) = splitReplyText(m.text)
            ParsedMessage(
                id = m.id,
                author = m.nick,
                text = body,
                replyQuote = quote,
                isMe = (selfUrl.isNotEmpty() && m.url.trimEnd('/') == selfUrl.trimEnd('/')) ||
                        (selfName != null && m.nick == selfName),
                time = if (m.ts > 0) gcTimeFormat.format(Date(m.ts)) else "",
                authorUserId = uid,
                authorAvatarUrl = m.avatar.ifBlank { null }
            )
        }
    }
    val rows = remember(parsed) { buildChatRows(parsed) }

    Column(Modifier.fillMaxSize().background(ThemeManager.parseColor(theme.backgroundColor))) {
        if (!config.active) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(config.disabledMessage, color = palette.secondary, textAlign = TextAlign.Center, fontSize = 15.sp)
            }
            return@Column
        }

        androidx.compose.animation.AnimatedVisibility(visible = isSelectionMode) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(ThemeManager.dialogSurface(theme))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedIds = emptySet() }) {
                    Icon(Icons.Default.Close, null, tint = palette.text)
                }
                Text("Выбрано: ${selectedIds.size}", fontWeight = FontWeight.Bold, color = palette.text, modifier = Modifier.weight(1f))
                if (selectedIds.size == 1 && token != null) {
                    IconButton(onClick = {
                        replyTo = parsed.firstOrNull { it.id in selectedIds }
                        selectedIds = emptySet()
                    }) { Icon(Icons.Default.Reply, "Ответить", tint = palette.accent) }
                }
                IconButton(onClick = {
                    val sel = parsed.filter { it.id in selectedIds }
                    val text = if (sel.size == 1) sel.first().text
                    else sel.joinToString("\n\n") { "[${it.author}, ${it.time}]: ${it.text}" }
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("messages", text))
                    Toast.makeText(context, if (sel.size == 1) "Текст скопирован" else "Скопировано ${sel.size} сообщ.", Toast.LENGTH_SHORT).show()
                    selectedIds = emptySet()
                }) { Icon(Icons.Default.ContentCopy, "Копировать", tint = palette.accent) }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!loaded) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = palette.accent)
            } else if (rows.isEmpty()) {
                Text("Сообщений пока нет", color = palette.secondary, modifier = Modifier.align(Alignment.Center))
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .telegramSelectionGestures(
                        listState = listState,
                        keyToMessageId = { k -> (k as? String)?.takeIf { it.startsWith("m_") }?.removePrefix("m_") },
                        isSelectionMode = { selectionModeState.value },
                        onPressStart = { id -> pressedId = id },
                        onLongPress = { id ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pulseId = id; pulseCounter++
                            dragBase = selectedIds
                            dragAnchor = id
                            dragSelect = id !in selectedIds
                            selectedIds = if (dragSelect) selectedIds + id else selectedIds - id
                        },
                        onDragOver = { id ->
                            val a = parsed.indexOfFirst { it.id == dragAnchor }
                            val b = parsed.indexOfFirst { it.id == id }
                            if (a >= 0 && b >= 0) {
                                val range = parsed.subList(minOf(a, b), maxOf(a, b) + 1).map { it.id }.toSet()
                                val ns = if (dragSelect) dragBase + range else dragBase - range
                                if (ns != selectedIds) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedIds = ns
                                }
                            }
                        },
                        onDragEnd = { dragAnchor = null },
                        onTapInSelection = { id ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        },
                        scope = scope
                    ),
                contentPadding = PaddingValues(top = 6.dp, bottom = 8.dp)
            ) {
                items(rows, key = { it.key }) { row ->
                    val msg = row.msg
                    if (msg == null) {
                        ChatDateChip(row.dateLabel.orEmpty(), palette, Modifier.padding(vertical = 8.dp))
                    } else {
                        TelegramMessageBubble(
                            message = msg,
                            palette = palette,
                            theme = theme,
                            isFirstInGroup = row.first,
                            isLastInGroup = row.last,
                            showAuthor = true,
                            isSelected = msg.id in selectedIds,
                            isSelectionMode = isSelectionMode,
                            isPressed = pressedId == msg.id,
                            pulseKey = if (pulseId == msg.id) pulseCounter else 0,
                            isSearchHighlight = false,
                            searchQuery = "",
                            translatedText = null,
                            onSwipeReply = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (token != null) replyTo = msg
                                else Toast.makeText(context, "Чтобы отвечать, войдите в чат", Toast.LENGTH_SHORT).show()
                            },
                            onLinkClick = { link ->
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) } catch (_: Exception) {}
                            },
                            onImageClick = {},
                            onProfileClick = { uid, name ->
                                if (uid.isNotBlank()) navController.navigate("profile/$uid/${Uri.encode(name)}")
                            },
                            otherUserId = "",
                            otherUsername = msg.author,
                            replyAuthor = row.replyAuthor,
                            onReplyQuoteClick = row.replyTargetId?.let { tid ->
                                {
                                    val idx = rows.indexOfFirst { it.msg?.id == tid }
                                    if (idx >= 0) scope.launch {
                                        try { listState.animateScrollToItem(idx, -200) } catch (_: Exception) {}
                                        flashId = tid
                                        delay(900)
                                        if (flashId == tid) flashId = null
                                    }
                                }
                            },
                            isFlashing = flashId == msg.id
                        )
                    }
                }
            }
        }

        // Нижняя панель: вход через бота или поле ввода
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
        ) {
            status?.let {
                Text(
                    it, fontSize = 12.sp,
                    color = if (statusIsError) Color(0xFFE53935) else palette.secondary,
                    modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
                )
            }
            replyTo?.let { r ->
                ReplyComposerBar(
                    author = if (r.isMe) "Вам" else r.author,
                    text = r.text,
                    palette = palette,
                    onClose = { replyTo = null }
                )
                Spacer(Modifier.height(4.dp))
            }
            if (token == null) {
                GlobalChatGate(gate = gate, palette = palette, onStart = { startLink() }, onOpenBot = { openBot(it) })
            } else {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (palette.isLight) Color.White else palette.inBubble)
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (input.isEmpty()) Text("Сообщение в общий чат", color = palette.secondary, fontSize = 16.sp)
                        BasicTextField(
                            value = input,
                            onValueChange = { if (it.length <= GlobalChatApi.MAX_LEN + 20) input = it },
                            textStyle = TextStyle(color = palette.text, fontSize = 16.sp, lineHeight = 21.sp),
                            cursorBrush = SolidColor(palette.accent),
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (input.length > GlobalChatApi.MAX_LEN - 50) {
                            Text(
                                "${GlobalChatApi.MAX_LEN - input.length}",
                                fontSize = 11.sp,
                                color = if (input.length > GlobalChatApi.MAX_LEN) Color(0xFFE53935) else palette.secondary,
                                modifier = Modifier.align(Alignment.BottomEnd)
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    val canSend = input.isNotBlank() && !sending && input.length <= GlobalChatApi.MAX_LEN
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (canSend) palette.accent else palette.accent.copy(alpha = 0.45f))
                            .clickable(enabled = canSend) { send() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sending) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(22.dp).offset(x = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalChatGate(gate: GcGate, palette: ChatPalette, onStart: () -> Unit, onOpenBot: (String) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.serviceBg)
            .border(1.dp, palette.serviceBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (gate) {
            is GcGate.Waiting -> {
                Text("Подтвердите вход в Telegram", fontWeight = FontWeight.SemiBold, color = palette.text, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Откроется @${GlobalChatApi.BOT} — нажмите там «Старт». Если бот не открылся, отправьте ему код:",
                    fontSize = 13.sp, color = palette.secondary, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.accent.copy(alpha = 0.12f))
                        .clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("code", gate.code))
                            Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(gate.code, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.accent, letterSpacing = 2.sp)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ContentCopy, null, tint = palette.accent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onOpenBot(gate.code) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Открыть бота") }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = palette.secondary)
                    Spacer(Modifier.width(6.dp))
                    Text(gate.status, fontSize = 12.sp, color = palette.secondary)
                }
            }
            else -> {
                Icon(Icons.Default.Lock, null, tint = palette.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Писать в общий чат могут только авторизованные пользователи",
                    fontWeight = FontWeight.SemiBold, color = palette.text, fontSize = 14.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Подтвердите вход один раз через Telegram-бота. Читать чат можно и без этого.",
                    fontSize = 12.sp, color = palette.secondary, textAlign = TextAlign.Center
                )
                if (gate is GcGate.Error) {
                    Spacer(Modifier.height(6.dp))
                    Text(gate.message, fontSize = 12.sp, color = Color(0xFFE53935), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onStart,
                    enabled = gate !is GcGate.Starting,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (gate is GcGate.Starting) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Default.Forum, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Войти в чат")
                    }
                }
            }
        }
    }
}
