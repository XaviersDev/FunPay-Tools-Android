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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════
// Из GameChatsRegistry.kt
// ═══════════════════════════════════════════════════════════════════════════

data class GameChat(
    @SerializedName("name") val name: String = "",
    @SerializedName("chat_url") val chatUrl: String = "",
    @SerializedName("source_url") val sourceUrl: String = ""
) {
    val nodeId: String
        get() = Regex("node=([^&\\s]+)").find(chatUrl)?.groupValues?.get(1) ?: ""

    /** Короткое имя без суффикса-категории, для поиска. Напр. "Minecraft" из "Minecraft game pass". */
    val baseName: String
        get() {
            val cat = category
            return if (cat.isNotEmpty() && name.endsWith(" $cat", ignoreCase = true)) {
                name.dropLast(cat.length + 1).trim()
            } else name
        }

    /** Категория: "предметы"/"услуги"/"буст"/"активации"/"прокачка"/"game pass" и т.д. */
    val category: String
        get() = detectCategory(name)

    /** Верхнеуровневая группа для табов: предметы / услуги / буст / прочее. */
    val group: GameChatGroup
        get() = when (category.lowercase(Locale.ROOT)) {
            "предметы" -> GameChatGroup.ITEMS
            "услуги" -> GameChatGroup.SERVICES
            "буст" -> GameChatGroup.BOOST
            else -> GameChatGroup.OTHER
        }

    companion object {
        /** Определяем категорию по последнему «смысловому» слову в названии. */
        fun detectCategory(name: String): String {
            val known = listOf(
                "предметы", "услуги", "буст", "прокачка", "активации",
                "скины", "руны", "промокоды", "пополнение"
            )
            val lower = name.lowercase(Locale.ROOT)
            for (kw in known) {
                if (lower.endsWith(" $kw")) return kw
            }
            // составные: "game pass", "battle royale", "prime", "vhs", "pve", "play", "premium", "кубков", "карты", "(прочие)", "прочее"
            val tails = listOf(
                "game pass", "battle royale", "prime", "vhs", "pve", "play",
                "premium", "кубков", "карты", "прочее", "(прочие)"
            )
            for (t in tails) {
                if (lower.endsWith(" $t")) return t
            }
            return ""
        }
    }
}

enum class GameChatGroup(val title: String) {
    ALL("Все"),
    ITEMS("Предметы"),
    SERVICES("Услуги"),
    BOOST("Буст"),
    OTHER("Прочее")
}

/**
 * Реестр игровых чатов. Лениво грузит JSON один раз, держит в памяти.
 * Потокобезопасный (synchronized на lazy-init).
 */
object GameChatsRegistry {
    private const val ASSET_FILENAME = "funpay_chats.json"
    private val gson = Gson()

    @Volatile
    private var cached: List<GameChat>? = null

    /** Публичный геттер: берёт из кэша или грузит из assets. Никогда не кидает — возвращает пустой список при любой ошибке. */
    fun getAll(context: Context): List<GameChat> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = try {
                val json = context.applicationContext.assets.open(ASSET_FILENAME).use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                }
                val type = object : TypeToken<List<GameChat>>() {}.type
                val parsed: List<GameChat> = gson.fromJson(json, type) ?: emptyList()
                // Отсекаем мусор и дубликаты по nodeId
                parsed
                    .filter { it.nodeId.isNotBlank() && it.name.isNotBlank() }
                    .distinctBy { it.nodeId }
                    .sortedBy { it.name.lowercase(Locale.ROOT) }
            } catch (e: Exception) {
                LogManager.addLog("❌ GameChatsRegistry: не смог прочитать assets/$ASSET_FILENAME: ${e.message}")
                emptyList()
            }
            cached = loaded
            return loaded
        }
    }

    /** Поиск с учётом транслитерации в обе стороны + без регистра. */
    fun search(context: Context, query: String, group: GameChatGroup = GameChatGroup.ALL): List<GameChat> {
        val all = getAll(context)
        val base = if (group == GameChatGroup.ALL) all else all.filter { it.group == group }
        val q = query.trim()
        if (q.isEmpty()) return base

        val qLower = q.lowercase(Locale.ROOT)
        val qTranslit = translit(qLower)
        val qReverse = reverseTranslit(qLower)

        return base.filter { game ->
            val name = game.name.lowercase(Locale.ROOT)
            val baseName = game.baseName.lowercase(Locale.ROOT)
            val nameTranslit = translit(name)
            val nameReverse = reverseTranslit(name)

            name.contains(qLower) ||
                    baseName.contains(qLower) ||
                    nameTranslit.contains(qLower) ||
                    nameTranslit.contains(qTranslit) ||
                    nameReverse.contains(qLower) ||
                    nameReverse.contains(qReverse) ||
                    name.contains(qTranslit) ||
                    name.contains(qReverse)
        }
    }

    /** Группировка по [GameChatGroup] для показа "разделами" при пустом поиске. */
    fun grouped(context: Context): Map<GameChatGroup, List<GameChat>> {
        val all = getAll(context)
        return GameChatGroup.values()
            .filter { it != GameChatGroup.ALL }
            .associateWith { g -> all.filter { it.group == g } }
    }

    /** Для быстрой подсветки "есть или нет такой nodeId в каталоге". */
    fun byNodeId(context: Context, nodeId: String): GameChat? =
        getAll(context).firstOrNull { it.nodeId == nodeId }

    /**
     * Системный ли это чат (общий чат FunPay / игровой чат).
     * У таких "чатов" нет профиля — открывать profile/... бессмысленно.
     */
    fun isSystemChatId(id: String): Boolean {
        return id == "flood" || id.startsWith("game-")
    }

    // ── Транслит ────────────────────────────────────────────────────────────

    private val ruToEn: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
        'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k",
        'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    // упрощённая обратная: en-буквы → ru. Однозначных соответствий нет, делаем "лучше что-то".
    private val enToRu: Map<String, String> = linkedMapOf(
        "sch" to "щ", "shch" to "щ", "zh" to "ж", "ts" to "ц", "ch" to "ч",
        "sh" to "ш", "yu" to "ю", "ya" to "я", "yo" to "ё", "kh" to "х",
        "a" to "а", "b" to "б", "v" to "в", "g" to "г", "d" to "д", "e" to "е",
        "z" to "з", "i" to "и", "y" to "й", "k" to "к", "l" to "л", "m" to "м",
        "n" to "н", "o" to "о", "p" to "п", "r" to "р", "s" to "с", "t" to "т",
        "u" to "у", "f" to "ф", "h" to "х", "c" to "к", "j" to "ж", "w" to "в",
        "x" to "кс", "q" to "к"
    )

    private fun translit(ru: String): String {
        val sb = StringBuilder(ru.length)
        for (c in ru) {
            val mapped = ruToEn[c]
            if (mapped != null) sb.append(mapped) else sb.append(c)
        }
        return sb.toString()
    }

    private fun reverseTranslit(en: String): String {
        var s = en
        for ((from, to) in enToRu) {
            s = s.replace(from, to)
        }
        return s
    }
}
// ═══════════════════════════════════════════════════════════════════════════
// Из GameChatsUi.kt
// ═══════════════════════════════════════════════════════════════════════════

enum class ChatListMode { PERSONAL, GAMES }

/**
 * Сегмент-контрол Личные/Игровые. Компактный, встраивается над ChatFolderTabs.
 * Анимированный подсвеченный indicator.
 */
@Composable
fun ChatModeSegmentedControl(
    mode: ChatListMode,
    onModeChanged: (ChatListMode) -> Unit,
    theme: AppTheme,
    modifier: Modifier = Modifier
) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)

    // Очень тонкая плашка, плотно к топбару (padding сверху 2dp, снизу 0).
    // Внешний фон убран — чтобы не занимала визуальный «блок».
    // Индикатор выбранного — просто подчёркивание accent-цветом под текстом.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        listOf(
            ChatListMode.PERSONAL to "Личные",
            ChatListMode.GAMES to "Игровые"
        ).forEachIndexed { idx, pair ->
            val (m, label) = pair
            val selected = mode == m
            val tc by animateColorAsState(
                if (selected) accent else textSecondary.copy(alpha = 0.7f),
                animationSpec = tween(160), label = "segtc"
            )
            val indicatorAlpha by animateColorAsState(
                if (selected) accent else Color.Transparent,
                animationSpec = tween(160), label = "segind"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onModeChanged(m) }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    label,
                    color = tc,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(1.5.dp)
                        .background(indicatorAlpha, RoundedCornerShape(1.dp))
                )
            }
            if (idx == 0) Spacer(Modifier.width(6.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 2. ТОНКИЙ ПОИСК ДЛЯ ПАПОК (используется и в личных, и в игровых).
//    Невероятно тонкий: иконка-лупа, по тапу разворачивается в inline поле.
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun ThinChatSearch(
    query: String,
    onQueryChanged: (String) -> Unit,
    theme: AppTheme,
    modifier: Modifier = Modifier,
    placeholder: String = "Поиск"
) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)

    var expanded by remember { mutableStateOf(query.isNotEmpty()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        if (expanded) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    Row(
        modifier = modifier.height(26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кнопка-лупа; при раскрытии остаётся слева от поля как "иконка поиска".
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable {
                    if (expanded && query.isEmpty()) expanded = false
                    else expanded = true
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Search, null,
                tint = if (expanded) accent else textSecondary,
                modifier = Modifier.size(15.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(140)) + expandHorizontally(tween(180)),
            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(surface.copy(alpha = 0.55f))
                    .border(
                        width = 0.5.dp,
                        color = textSecondary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .height(24.dp)
                    .padding(horizontal = 6.dp)
            ) {
                Box(modifier = Modifier.width(140.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            placeholder,
                            color = textSecondary.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChanged,
                        singleLine = true,
                        textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Default.Clear, null,
                        tint = textSecondary,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onQueryChanged("") }
                    )
                } else {
                    Icon(
                        Icons.Default.Clear, null,
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { expanded = false }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 3. СПИСОК ИГРОВЫХ ЧАТОВ — основной экран при выборе "Игровые" в сегменте.
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Общая иконка общих чатов FunPay (как у Флудилки). Один URL на весь список.
 */
private const val GENERIC_FUNPAY_CHAT_AVATAR = "https://funpay.com/img/layout/avatar.png"

@Composable
fun GameChatsView(
    navController: NavController,
    theme: AppTheme
) {
    val context = LocalContext.current
    val all = remember { GameChatsRegistry.getAll(context) }

    var selectedGroup by remember { mutableStateOf(GameChatGroup.ALL) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, selectedGroup, all) {
        GameChatsRegistry.search(context, query, selectedGroup)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Верхняя строка: табы групп + лупа-поиск.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameGroupTabs(
                groups = GameChatGroup.values().toList(),
                selected = selectedGroup,
                onSelect = { selectedGroup = it },
                theme = theme,
                modifier = Modifier.weight(1f)
            )
            ThinChatSearch(
                query = query,
                onQueryChanged = { query = it },
                theme = theme,
                modifier = Modifier.padding(end = 10.dp),
                placeholder = "Игра"
            )
        }

        if (all.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ChatBubbleOutline, null,
                        tint = ThemeManager.parseColor(theme.textSecondaryColor),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Каталог игровых чатов не загрузился.\nПроверь assets/funpay_chats.json",
                        color = ThemeManager.parseColor(theme.textSecondaryColor),
                        fontSize = 12.sp
                    )
                }
            }
            return
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isNotEmpty()) "Ничего не нашли по «$query»" else "В этой группе пока пусто",
                    color = ThemeManager.parseColor(theme.textSecondaryColor),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.nodeId }) { game ->
                    GameChatRow(game = game, theme = theme, onOpen = {
                        // Открываем как обычный чат: тот же роут, что и для Flood.
                        // Repository кладёт chatId в URL как node, бэк отдаёт историю.
                        navController.navigate("chat/${game.nodeId}/${game.baseName.ifBlank { game.name }}")
                    })
                }
            }
        }
    }
}

/**
 * Горизонтальная лента групп (Все / Предметы / Услуги / Буст / Прочее).
 * Стиль идентичен ChatFolderTabs, чтобы визуально читалось как «тот же тип элемента».
 */
@Composable
private fun GameGroupTabs(
    groups: List<GameChatGroup>,
    selected: GameChatGroup,
    onSelect: (GameChatGroup) -> Unit,
    theme: AppTheme,
    modifier: Modifier = Modifier
) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(8.dp))
        groups.forEach { g ->
            val isSel = g == selected
            val bg by animateColorAsState(
                if (isSel) accent.copy(alpha = 0.18f) else Color.Transparent,
                label = "ggbg"
            )
            val tc by animateColorAsState(
                if (isSel) accent else textSecondary, label = "ggtc"
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable { onSelect(g) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    g.title,
                    fontSize = 13.sp,
                    color = tc,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

/**
 * Карточка одного игрового чата. Тап по всей карточке — открыть чат внутри приложения.
 * Есть быстрая кнопка «копировать ссылку» справа.
 * Долгий тап → ActionsSheet (копировать ссылку на чат / на категорию).
 */
@Composable
private fun GameChatRow(
    game: GameChat,
    theme: AppTheme,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)
    val accent = ThemeManager.parseColor(theme.accentColor)

    var justCopied by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }

    fun copyChatLink() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("chat_link", game.chatUrl))
        justCopied = true
        Toast.makeText(context, "Ссылка на чат скопирована", Toast.LENGTH_SHORT).show()
        scope.launch { delay(1400); justCopied = false }
    }

    fun copyCategoryLink() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("category_link", game.sourceUrl))
        Toast.makeText(context, "Ссылка на категорию скопирована", Toast.LENGTH_SHORT).show()
    }

    if (showSheet) {
        GameChatActionsDialog(
            game = game,
            theme = theme,
            onOpen = { showSheet = false; onOpen() },
            onCopyChat = { showSheet = false; copyChatLink() },
            onCopyCategory = { showSheet = false; copyCategoryLink() },
            onDismiss = { showSheet = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = surface.copy(alpha = theme.containerOpacity)
        ),
        shape = RoundedCornerShape(theme.borderRadius.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = GENERIC_FUNPAY_CHAT_AVATAR,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    game.baseName.ifBlank { game.name },
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (game.category.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            game.category,
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
            // Быстрая кнопка «скопировать ссылку на чат».
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { copyChatLink() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (justCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    null,
                    tint = if (justCopied) Color(0xFF4CAF50) else textSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
            // Троеточие справа — все действия.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { showSheet = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.OpenInNew, null,
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Мини-диалог "что сделать с чатом": открыть / скопировать ссылку / на категорию.
 * Держу отдельным Dialog, чтобы не притаскивать ModalBottomSheet (API не у всех стабильный).
 */
@Composable
private fun GameChatActionsDialog(
    game: GameChat,
    theme: AppTheme,
    onOpen: () -> Unit,
    onCopyChat: () -> Unit,
    onCopyCategory: () -> Unit,
    onDismiss: () -> Unit
) {
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)
    val accent = ThemeManager.parseColor(theme.accentColor)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    game.name,
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))

                ActionRow(
                    icon = Icons.Default.Chat,
                    title = "Открыть чат",
                    subtitle = "Внутри приложения",
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    tint = accent,
                    onClick = onOpen
                )
                Divider(color = textSecondary.copy(alpha = 0.15f))
                ActionRow(
                    icon = Icons.Default.Link,
                    title = "Копировать ссылку на чат",
                    subtitle = game.chatUrl,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    tint = textPrimary,
                    onClick = onCopyChat
                )
                Divider(color = textSecondary.copy(alpha = 0.15f))
                ActionRow(
                    icon = Icons.Default.OpenInNew,
                    title = "Копировать ссылку на категорию",
                    subtitle = game.sourceUrl,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    tint = textPrimary,
                    onClick = onCopyCategory
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    textPrimary: Color,
    textSecondary: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                color = textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
// ═══════════════════════════════════════════════════════════════════════════
// Из ConcurentGamePicker.kt
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun ConcurentGamePickerDialog(
    theme: AppTheme,
    existingNodeIds: Set<String>,
    onPicked: (GameChat) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accent = ThemeManager.parseColor(theme.accentColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)

    val all = remember { GameChatsRegistry.getAll(context) }
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf(GameChatGroup.ALL) }

    val filtered = remember(query, selectedGroup, all) {
        GameChatsRegistry.search(context, query, selectedGroup)
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Заголовок
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Поиск по каталогу",
                        color = textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${all.size} игр",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Введите название игры или категорию — русский/английский, как угодно",
                    color = textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )

                Spacer(Modifier.height(12.dp))

                // Поле поиска — крупное, сразу в фокусе
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(surface.copy(alpha = 0.55f))
                        .border(
                            width = 1.dp,
                            color = if (query.isNotEmpty()) accent else textSecondary.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Default.Search, null,
                        tint = if (query.isNotEmpty()) accent else textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Например: minecraft, дота, буст",
                                color = textSecondary.copy(alpha = 0.65f),
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = textPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Clear, null,
                            tint = textSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { query = "" }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Табы групп
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GameChatGroup.values().forEach { g ->
                        val isSel = g == selectedGroup
                        val bg by animateColorAsState(
                            if (isSel) accent.copy(alpha = 0.18f) else Color.Transparent,
                            label = "pgbg"
                        )
                        val tc by animateColorAsState(
                            if (isSel) accent else textSecondary, label = "pgtc"
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .clickable { selectedGroup = g }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                g.title,
                                fontSize = 12.sp,
                                color = tc,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Найдено: ${filtered.size}",
                    color = textSecondary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(6.dp))

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isBlank()) "Загрузка каталога..." else "Ничего не нашли по «$query»",
                            color = textSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.nodeId }) { game ->
                            val alreadyAdded = existingNodeIds.contains(game.nodeId)
                            PickerRow(
                                game = game,
                                alreadyAdded = alreadyAdded,
                                accent = accent,
                                surface = surface,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onClick = {
                                    if (!alreadyAdded) {
                                        onPicked(game)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Закрыть", color = textSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    game: GameChat,
    alreadyAdded: Boolean,
    accent: Color,
    surface: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(surface.copy(alpha = if (alreadyAdded) 0.25f else 0.55f))
            .clickable(enabled = !alreadyAdded) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                game.baseName.ifBlank { game.name },
                color = if (alreadyAdded) textSecondary else textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (game.category.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = if (alreadyAdded) 0.08f else 0.18f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        game.category,
                        color = accent.copy(alpha = if (alreadyAdded) 0.6f else 1f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        if (alreadyAdded) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF4CAF50).copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Check, null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Добавлено", color = Color(0xFF4CAF50), fontSize = 10.sp)
                }
            }
        }
    }
}