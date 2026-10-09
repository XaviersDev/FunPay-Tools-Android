package ru.allisighs.funpaytools

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

/* ============================================================================
 *  Даты сообщений FunPay.
 *  В title у .chat-msg-date FunPay пишет "9 октября, 11:16:08" (год — только
 *  если не текущий), в тексте — "11:16:08" или "07.10.26". Новые локальные
 *  сообщения имеют время "HH:mm".
 * ==========================================================================*/
object ChatDates {
    private val ruMonths = listOf(
        "январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр"
    )
    private val enMonths = listOf(
        "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
    )
    private val ruGenitive = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    /** dayKey = yyyy*10000 + MM*100 + dd (MM 1..12) */
    data class MsgDate(val dayKey: Int?, val hhmm: String)

    private fun todayKey(offsetDays: Int = 0): Int {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, offsetDays)
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }

    private fun monthIndex(word: String): Int? {
        val w = word.lowercase().trim('.', ',')
        if (w.isEmpty()) return null
        // "мая"/"май" — коротко, проверяем отдельно, чтобы не путать с "март"
        if (w == "мая" || w == "май") return 4
        ruMonths.forEachIndexed { i, p -> if (i != 4 && w.startsWith(p)) return i }
        enMonths.forEachIndexed { i, p -> if (w.startsWith(p)) return i }
        return null
    }

    fun parse(raw: String?): MsgDate {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return MsgDate(null, "")
        val time = Regex("""(\d{1,2}):(\d{2})""").find(s)?.let { m ->
            m.groupValues[1].padStart(2, '0') + ":" + m.groupValues[2]
        } ?: ""
        val lower = s.lowercase()

        if (lower.startsWith("сегодня") || lower.startsWith("today")) return MsgDate(todayKey(), time)
        if (lower.startsWith("вчера") || lower.startsWith("yesterday")) return MsgDate(todayKey(-1), time)

        // dd.MM.yy или dd.MM.yyyy
        Regex("""(\d{1,2})\.(\d{1,2})\.(\d{2,4})""").find(s)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            var y = m.groupValues[3].toInt()
            if (y < 100) y += 2000
            return MsgDate(y * 10000 + mo * 100 + d, time)
        }

        // "9 октября, 11:16:08" / "9 октября 2025, 11:16" / "October 9, 11:16:08"
        val datePart = s.substringBefore(",").trim()
        val tokens = datePart.split(Regex("""\s+""")).filter { it.isNotBlank() }
        var day: Int? = null
        var month: Int? = null
        var year: Int? = null
        for (t in tokens) {
            val n = t.trim(',', '.').toIntOrNull()
            when {
                n != null && n in 1..31 && day == null -> day = n
                n != null && n >= 1900 -> year = n
                n == null && month == null -> month = monthIndex(t)
            }
        }
        // год может стоять после запятой: "October 9, 2025, 11:16"
        if (year == null) {
            Regex(""",\s*(\d{4})\b""").find(s)?.let { year = it.groupValues[1].toIntOrNull() }
        }
        if (day != null && month != null) {
            val now = Calendar.getInstance()
            var y = year ?: now.get(Calendar.YEAR)
            val key = y * 10000 + (month!! + 1) * 100 + day!!
            // без года и "в будущем" → это прошлый год
            if (year == null && key > todayKey()) y -= 1
            return MsgDate(y * 10000 + (month!! + 1) * 100 + day!!, time)
        }

        // только время → сегодня
        if (time.isNotEmpty() && Regex("""^\d{1,2}:\d{2}(:\d{2})?$""").matches(s)) return MsgDate(todayKey(), time)
        return MsgDate(null, time)
    }

    fun label(dayKey: Int): String {
        if (dayKey == todayKey()) return "Сегодня"
        if (dayKey == todayKey(-1)) return "Вчера"
        val y = dayKey / 10000
        val m = (dayKey / 100) % 100
        val d = dayKey % 100
        val month = ruGenitive.getOrElse(m - 1) { "" }
        val curYear = Calendar.getInstance().get(Calendar.YEAR)
        return if (y == curYear) "$d $month" else "$d $month $y"
    }
}

data class ChatRow(
    val key: String,
    val msg: ParsedMessage? = null,
    val dateLabel: String? = null,
    val dayKey: Int? = null,
    val first: Boolean = true,
    val last: Boolean = true,
    /** Для ответа: на какое сообщение отвечали и кто его автор. */
    val replyTargetId: String? = null,
    val replyAuthor: String? = null,
    /** Особая строка-карточка «о собеседнике» в начале чата. */
    val isInfoCard: Boolean = false
)

private fun normQuote(s: String) = s.replace(Regex("\\s+"), " ").trim().lowercase()

/** Ищем, на какое сообщение отвечали (как в расширении: точное совпадение, затем по началу). */
fun findQuotedMessage(list: List<ParsedMessage>, fromIndex: Int, quote: String): ParsedMessage? {
    val q = normQuote(quote).removeSuffix("…").trim()
    if (q.isEmpty()) return null
    var partial: ParsedMessage? = null
    for (i in fromIndex - 1 downTo 0) {
        val m = list[i]
        val t = normQuote(m.text.ifEmpty { if (m.imageUrl != null) "изображение" else "" })
        if (t.isEmpty()) continue
        if (t == q) return m
        if (partial == null && (t.startsWith(q) || q.startsWith(t))) partial = m
    }
    return partial
}

/** Сообщения + плашки дат; first/last — границы группы подряд идущих сообщений одного автора. */
fun buildChatRows(all: List<ParsedMessage>): List<ChatRow> {
    val list = all.distinctBy { it.id }
    val days = list.map { ChatDates.parse(it.time).dayKey }
    val filledDays = ArrayList<Int?>(days.size)
    var lastKnown: Int? = null
    for (d in days) { if (d != null) lastKnown = d; filledDays.add(d ?: lastKnown) }
    fun sameGroup(a: ParsedMessage, b: ParsedMessage): Boolean =
        !a.isSystem && !b.isSystem && a.isMe == b.isMe &&
                (a.isMe || (a.authorUserId ?: a.author) == (b.authorUserId ?: b.author))
    val out = ArrayList<ChatRow>(list.size + 8)
    for (i in list.indices) {
        val m = list[i]
        val day = filledDays[i]
        val prevDay = if (i > 0) filledDays[i - 1] else null
        val newDay = day != null && (i == 0 || day != prevDay)
        if (newDay) out.add(ChatRow(key = "d_${day}_${m.id}", dateLabel = ChatDates.label(day!!), dayKey = day))
        val prev = list.getOrNull(i - 1)
        val next = list.getOrNull(i + 1)
        val nextDay = filledDays.getOrNull(i + 1)
        val first = newDay || prev == null || !sameGroup(prev, m)
        val last = next == null || nextDay != day || !sameGroup(m, next)
        var replyTarget: ParsedMessage? = null
        if (m.replyQuote != null) replyTarget = findQuotedMessage(list, i, m.replyQuote)
        val replyAuthor = when {
            m.replyQuote == null -> null
            replyTarget == null -> "Ответ"
            replyTarget.isMe -> "Вы"
            else -> replyTarget.author.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Собеседник"
        }
        out.add(ChatRow(key = "m_${m.id}", msg = m, dayKey = day, first = first, last = last,
            replyTargetId = replyTarget?.id, replyAuthor = replyAuthor))
    }
    return out
}

/** Цвета чата в стиле Telegram, вычисленные из темы. */
data class ChatPalette(
    val isLight: Boolean,
    val outBubble: Color,
    val inBubble: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val link: Color,
    val selection: Color,
    val chip: Color,
    val chipText: Color,
    val serviceBg: Color,
    val serviceText: Color,
    val serviceLink: Color,
    val serviceBorder: Color
)

@Composable
fun rememberChatPalette(theme: AppTheme): ChatPalette = remember(theme) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val surfaceOpaque = ThemeManager.dialogSurface(theme)
    val bg = ThemeManager.parseColorOpaque(
        if (theme.backgroundColor.equals("#00000000", true)) theme.originalBackgroundColor else theme.backgroundColor
    )
    val isLight = bg.luminance() > 0.5f
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    ChatPalette(
        isLight = isLight,
        outBubble = lerp(surfaceOpaque, accent, if (isLight) 0.17f else 0.42f),
        inBubble = if (isLight) Color.White else surface,
        text = ThemeManager.parseColor(theme.textPrimaryColor),
        secondary = ThemeManager.parseColor(theme.textSecondaryColor),
        accent = accent,
        link = if (isLight) lerp(accent, Color.Black, 0.15f) else lerp(accent, Color.White, 0.35f),
        selection = accent.copy(alpha = if (isLight) 0.12f else 0.18f),
        chip = if (isLight) Color(0x66556677) else Color(0x99000000),
        chipText = Color.White,
        // Оповещения FunPay: лёгкая заливка в цвет акцента вместо серой плашки
        serviceBg = if (isLight) lerp(Color.White, accent, 0.07f) else lerp(surfaceOpaque, accent, 0.16f),
        serviceText = ThemeManager.parseColor(theme.textPrimaryColor),
        serviceLink = if (isLight) lerp(accent, Color.Black, 0.1f) else lerp(accent, Color.White, 0.3f),
        serviceBorder = accent.copy(alpha = if (isLight) 0.16f else 0.22f)
    )
}

/** Плашка даты между сообщениями и плавающая дата сверху. */
@Composable
fun ChatDateChip(text: String, palette: ChatPalette, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(palette.chip)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            color = palette.chipText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Жесты выделения как в Telegram — вешается на LazyColumn:
 *  - долгое нажатие В ЛЮБОМ месте сообщения (в т.ч. на тексте) выделяет его;
 *  - не отпуская палец, ведите вверх/вниз — выделяются соседние сообщения,
 *    у краёв список сам прокручивается;
 *  - в режиме выделения обычный тап переключает сообщение.
 *
 *  Используется проход PointerEventPass.Initial, поэтому вложенные детекторы
 *  (ссылки в тексте, свайп-ответ) больше не "съедают" долгое нажатие.
 */
fun Modifier.telegramSelectionGestures(
    listState: LazyListState,
    keyToMessageId: (Any) -> String?,
    isSelectionMode: () -> Boolean,
    onPressStart: (String?) -> Unit,
    onLongPress: (String) -> Unit,
    onDragOver: (String) -> Unit,
    onDragEnd: () -> Unit,
    onTapInSelection: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
): Modifier = this.pointerInput(listState) {
    fun messageAt(y: Float): String? {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size } ?: return null
        return keyToMessageId(item.key)
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startId = messageAt(down.position.y)
        val slop = viewConfiguration.touchSlop
        var released = false
        var moved = false

        // Небольшая задержка перед "вдавливанием", чтобы не мигало при обычном скролле
        val pressJob: Job = scope.launch {
            delay(110)
            onPressStart(startId)
        }

        val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == down.id } ?: run { released = true; return@withTimeoutOrNull false }
                if (!c.pressed) {
                    released = true
                    // Тап в режиме выделения — переключаем сообщение (и не открываем ссылки)
                    if (isSelectionMode() && startId != null) {
                        c.consume()
                        onTapInSelection(startId)
                    }
                    return@withTimeoutOrNull false
                }
                if ((c.position - down.position).getDistance() > slop) {
                    moved = true
                    return@withTimeoutOrNull false
                }
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } == null

        pressJob.cancel()
        if (!longPressed || startId == null) {
            onPressStart(null)
            return@awaitEachGesture
        }

        onLongPress(startId)

        // Перетаскивание: выделяем всё, над чем проходит палец; у краёв — автоскролл
        var pointerY = down.position.y
        var lastOver: String? = startId
        val edge = 72.dp.toPx()
        val autoScroll = scope.launch {
            while (isActive) {
                val h = listState.layoutInfo.viewportSize.height.toFloat()
                val speed = when {
                    pointerY < edge -> -((edge - pointerY) / edge).coerceIn(0f, 1f) * 28f
                    pointerY > h - edge -> ((pointerY - (h - edge)) / edge).coerceIn(0f, 1f) * 28f
                    else -> 0f
                }
                if (speed != 0f) {
                    listState.scrollBy(speed)
                    messageAt(pointerY)?.let { id -> if (id != lastOver) { lastOver = id; onDragOver(id) } }
                }
                delay(16)
            }
        }
        try {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                c.consume()
                if (!c.pressed) break
                pointerY = c.position.y
                val id = messageAt(pointerY)
                if (id != null && id != lastOver) {
                    lastOver = id
                    onDragOver(id)
                }
            }
        } finally {
            autoScroll.cancel()
            onPressStart(null)
            onDragEnd()
        }
    }
}

/**
 * Пузырь сообщения в стиле Telegram.
 * Время — внутри пузыря справа снизу, хвостик у последнего сообщения в группе,
 * имя/аватар — только в общих чатах (где пишут разные люди).
 */
@Composable
fun TelegramMessageBubble(
    message: ParsedMessage,
    palette: ChatPalette,
    theme: AppTheme,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    showAuthor: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isPressed: Boolean,
    pulseKey: Int,
    isSearchHighlight: Boolean,
    searchQuery: String,
    translatedText: String?,
    onSwipeReply: () -> Unit,
    onLinkClick: (MessageLink) -> Unit,
    onImageClick: (String) -> Unit,
    onProfileClick: (String, String) -> Unit,
    otherUserId: String,
    otherUsername: String,
    replyAuthor: String? = null,
    onReplyQuoteClick: (() -> Unit)? = null,
    isFlashing: Boolean = false
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // "Вдавливание" при удержании + пружинка при выделении
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "press"
    )
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey > 0) {
            pulse.snapTo(0.94f)
            pulse.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
    }
    val rowBg by animateColorAsState(
        targetValue = when {
            isSelected -> palette.selection
            isFlashing -> palette.accent.copy(alpha = 0.22f)
            isSearchHighlight -> Color(0xFFFFEB3B).copy(alpha = 0.14f)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "rowBg"
    )

    // свайп влево — ответ
    var swipeOffset by remember(message.id) { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(swipeOffset, spring(stiffness = Spring.StiffnessMediumLow), label = "swipe")
    val swipeTriggerPx = with(density) { 64.dp.toPx() }
    val swipeMaxPx = with(density) { 110.dp.toPx() }
    val swipeReplyState = rememberUpdatedState(onSwipeReply)
    var swipeHapticDone by remember(message.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(top = if (isFirstInGroup) 5.dp else 1.5.dp, bottom = if (isLastInGroup) 1.dp else 0.dp)
            .pointerInput(message.id, isSelectionMode) {
                if (isSelectionMode) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var totalDx = 0f
                    var totalDy = 0f
                    var locked = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (swipeOffset < -swipeTriggerPx) swipeReplyState.value()
                            swipeOffset = 0f
                            swipeHapticDone = false
                            break
                        }
                        if (change.isConsumed && !locked) { swipeOffset = 0f; break }
                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y
                        totalDx += dx
                        totalDy += dy
                        if (!locked) {
                            if (abs(totalDy) > 12f && abs(totalDy) > abs(totalDx)) break
                            if (totalDx < -12f && abs(totalDx) > abs(totalDy) * 1.3f) locked = true
                        }
                        if (locked) {
                            swipeOffset = (swipeOffset + dx).coerceIn(-swipeMaxPx, 0f)
                            if (!swipeHapticDone && swipeOffset < -swipeTriggerPx) {
                                swipeHapticDone = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            change.consume()
                        }
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кружок выбора выезжает слева, как в Telegram
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = expandHorizontally(tween(200)) + fadeIn(tween(200)),
            exit = shrinkHorizontally(tween(180)) + fadeOut(tween(150))
        ) {
            Box(Modifier.padding(start = 10.dp, end = 2.dp), contentAlignment = Alignment.Center) {
                val circleColor by animateColorAsState(
                    if (isSelected) palette.accent else Color.Transparent, tween(150), label = "chk"
                )
                val checkScale by animateFloatAsState(
                    if (isSelected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "chkScale"
                )
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(circleColor)
                        .border(1.5.dp, if (isSelected) palette.accent else palette.secondary.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check, null, tint = Color.White,
                        modifier = Modifier.size(15.dp).graphicsLayer { scaleX = checkScale; scaleY = checkScale; alpha = checkScale.coerceIn(0f, 1f) }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .offset { IntOffset(animatedOffset.toInt(), 0) }
        ) {
            // иконка ответа, проявляется при свайпе
            if (animatedOffset < -8f) {
                val p = (-animatedOffset / swipeTriggerPx).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .offset { IntOffset((-animatedOffset).toInt() - with(density) { 36.dp.roundToPx() }, 0) }
                        .size(30.dp)
                        .graphicsLayer { alpha = p; scaleX = 0.6f + 0.4f * p; scaleY = 0.6f + 0.4f * p }
                        .clip(CircleShape)
                        .background(palette.chip),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Reply, null, tint = Color.White, modifier = Modifier.size(17.dp))
                }
            }

            val isStaff = message.badge?.let { it.contains("поддержка", true) || it.contains("арбитраж", true) || it.contains("support", true) || it.contains("arbitr", true) } == true
            if (message.isSystem && !isStaff) {
                // Оповещения FunPay — как служебные сообщения Telegram: по центру, мягкая плашка
                ServiceMessage(message, palette, onLinkClick, Modifier.graphicsLayer {
                    val sc = pressScale * pulse.value
                    scaleX = sc; scaleY = sc
                })
            } else {
                BubbleContent(
                    message = message,
                    badgeLabel = if (isStaff) message.badge else null,
                    palette = palette,
                    isFirstInGroup = isFirstInGroup,
                    isLastInGroup = isLastInGroup,
                    showAuthor = showAuthor,
                    scale = pressScale * pulse.value,
                    isSearchHighlight = isSearchHighlight,
                    searchQuery = searchQuery,
                    translatedText = translatedText,
                    onLinkClick = onLinkClick,
                    onImageClick = { if (!isSelectionMode) onImageClick(it) },
                    onProfileClick = onProfileClick,
                    otherUserId = otherUserId,
                    otherUsername = otherUsername,
                    replyAuthor = replyAuthor,
                    onReplyQuoteClick = if (isSelectionMode) null else onReplyQuoteClick
                )
            }
        }
    }
}

@Composable
private fun BubbleContent(
    message: ParsedMessage,
    badgeLabel: String? = null,
    palette: ChatPalette,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    showAuthor: Boolean,
    scale: Float,
    isSearchHighlight: Boolean,
    searchQuery: String,
    translatedText: String?,
    onLinkClick: (MessageLink) -> Unit,
    onImageClick: (String) -> Unit,
    onProfileClick: (String, String) -> Unit,
    otherUserId: String,
    otherUsername: String,
    replyAuthor: String? = null,
    onReplyQuoteClick: (() -> Unit)? = null
) {
    val isMe = message.isMe
    val big = 18.dp
    val small = 6.dp
    val tail = 4.dp
    val shape = if (isMe) RoundedCornerShape(
        topStart = big, bottomStart = big,
        topEnd = if (isFirstInGroup) big else small,
        bottomEnd = if (isLastInGroup) tail else small
    ) else RoundedCornerShape(
        topEnd = big, bottomEnd = big,
        topStart = if (isFirstInGroup) big else small,
        bottomStart = if (isLastInGroup) tail else small
    )
    val profileUserId = if (!isMe) (message.authorUserId ?: otherUserId) else ""
    val authorName = when {
        isMe -> ""
        message.author.isNotBlank() && message.author != "Unknown" -> message.author
        else -> otherUsername
    }
    val hhmm = remember(message.time) { ChatDates.parse(message.time).hhmm.ifEmpty { message.time.takeLast(5) } }
    val isLong = message.text.length > 700
    var expanded by remember(message.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe && showAuthor) {
            if (isLastInGroup) {
                AsyncImage(
                    model = message.authorAvatarUrl ?: "https://funpay.com/img/layout/avatar.png",
                    contentDescription = null,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(enabled = profileUserId.isNotEmpty()) { onProfileClick(profileUserId, authorName) },
                    contentScale = ContentScale.Crop
                )
            } else {
                Spacer(Modifier.width(34.dp))
            }
            Spacer(Modifier.width(6.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (isMe) 1f else 0f, 0.5f)
                }
                .clip(shape)
                .background(if (isMe) palette.outBubble else palette.inBubble)
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
        ) {
            if (badgeLabel != null && isFirstInGroup && !isMe) {
                Row(
                    Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, null, tint = Color(0xFF43A047), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${authorName.ifBlank { "FunPay" }} · ${badgeLabel.replaceFirstChar { it.uppercase() }}",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF43A047)
                    )
                }
            } else if (showAuthor && isFirstInGroup && !isMe && authorName.isNotBlank()) {
                Box(
                    Modifier
                        .padding(start = 10.dp, end = 10.dp, top = 6.dp)
                        .clickable(enabled = profileUserId.isNotEmpty()) { onProfileClick(profileUserId, authorName) }
                ) {
                    EpicNicknameText(
                        text = authorName,
                        style = LocalTextStyle.current.copy(
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = nameColorFor(profileUserId.ifEmpty { authorName })
                        )
                    )
                }
            }

            // Цитата ответа — как в Telegram: полоска, имя, текст в одну-две строки
            if (message.replyQuote != null) {
                val quoteAccent = if (isMe) palette.link else palette.accent
                Row(
                    modifier = Modifier
                        .padding(start = 6.dp, end = 6.dp, top = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(quoteAccent.copy(alpha = 0.12f))
                        .clickable(enabled = onReplyQuoteClick != null) { onReplyQuoteClick?.invoke() }
                        .height(IntrinsicSize.Min)
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(quoteAccent))
                    Column(Modifier.padding(start = 7.dp, end = 9.dp, top = 4.dp, bottom = 4.dp)) {
                        Text(
                            replyAuthor ?: "Ответ",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = quoteAccent,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            message.replyQuote.ifBlank { "Сообщение" },
                            fontSize = 13.sp, color = palette.text.copy(alpha = 0.85f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp
                        )
                    }
                }
            }

            if (message.imageUrl != null) {
                AsyncImage(
                    model = message.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(3.dp)
                        .widthIn(min = 180.dp, max = 294.dp)
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .clickable { onImageClick(message.imageUrl) },
                    contentScale = ContentScale.Crop
                )
            }

            if (message.text.isNotEmpty()) {
                Box(Modifier.padding(start = 10.dp, end = 9.dp, top = if (message.imageUrl != null) 2.dp else 6.dp, bottom = 5.dp)) {
                    val timePad = remember(hhmm) { "  " + hhmm + " " }
                    if (isSearchHighlight && searchQuery.isNotEmpty()) {
                        HighlightedText(
                            text = message.text + "\u2003\u2003\u2003\u2003",
                            highlight = searchQuery,
                            textColor = palette.text,
                            highlightColor = Color(0xFFFFEB3B),
                            maxLines = if (isLong && !expanded) 14 else Int.MAX_VALUE
                        )
                    } else {
                        MessageTextWithLinks(
                            text = message.text,
                            links = message.links,
                            textColor = palette.text,
                            linkColor = palette.link,
                            selectionKey = 0,
                            onLinkClick = onLinkClick,
                            maxLines = if (isLong && !expanded) 14 else Int.MAX_VALUE,
                            selectionEnabled = false,
                            trailingSpacer = timePad,
                            fontSize = 15.5f
                        )
                    }
                    Text(
                        hhmm,
                        modifier = Modifier.align(Alignment.BottomEnd),
                        fontSize = 11.sp,
                        color = palette.secondary.copy(alpha = 0.85f)
                    )
                }
                if (isLong) {
                    Text(
                        if (expanded) "Свернуть" else "Читать ещё",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.link,
                        modifier = Modifier
                            .padding(start = 10.dp, bottom = 6.dp)
                            .clickable { expanded = !expanded }
                    )
                }
                if (translatedText != null) {
                    HorizontalDivider(
                        color = palette.accent.copy(alpha = 0.2f),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    Text(
                        text = "🌐 $translatedText",
                        fontSize = 13.sp,
                        color = palette.link,
                        lineHeight = 18.sp,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            } else if (message.imageUrl != null && hhmm.isNotEmpty()) {
                Text(
                    hhmm,
                    modifier = Modifier.align(Alignment.End).padding(end = 9.dp, bottom = 4.dp),
                    fontSize = 11.sp,
                    color = palette.secondary.copy(alpha = 0.85f)
                )
            }
        }
    }
}

private val nameColors = listOf(
    Color(0xFFE17076), Color(0xFF7BC862), Color(0xFFE5CA77), Color(0xFF65AADD),
    Color(0xFFA695E7), Color(0xFFEE7AAE), Color(0xFF6EC9CB), Color(0xFFFAA774)
)

private fun nameColorFor(seed: String): Color = nameColors[abs(seed.hashCode()) % nameColors.size]

@Composable
private fun SystemMessageCard(
    message: ParsedMessage,
    palette: ChatPalette,
    theme: AppTheme,
    onLinkClick: (MessageLink) -> Unit,
    modifier: Modifier
) {
    val isSupport = message.badge?.contains("поддержка", true) == true ||
            message.badge?.contains("арбитраж", true) == true
    val badgeColor = if (isSupport) Color(0xFF43A047) else palette.accent
    val hhmm = remember(message.time) { ChatDates.parse(message.time).hhmm }
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isSupport) Color(0xFF1B5E20).copy(alpha = if (palette.isLight) 0.10f else 0.45f)
                    else palette.inBubble.copy(alpha = if (palette.isLight) 0.9f else 0.75f)
                )
                .border(1.dp, badgeColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (isSupport) Icons.Default.Security else Icons.Default.Info, null, tint = badgeColor, modifier = Modifier.size(15.dp))
                Text(
                    message.badge?.uppercase() ?: if (message.author == "FunPay") "FUNPAY" else "СИСТЕМА",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = badgeColor, letterSpacing = 0.8.sp
                )
                Spacer(Modifier.weight(1f, fill = false))
                if (hhmm.isNotEmpty()) Text(hhmm, fontSize = 11.sp, color = palette.secondary)
            }
            Spacer(Modifier.height(5.dp))
            MessageTextWithLinks(
                text = message.text, links = message.links,
                textColor = palette.text, linkColor = badgeColor,
                selectionKey = 0, onLinkClick = onLinkClick, selectionEnabled = false
            )
        }
    }
}

/** Окно "Выделить текст" — свободное выделение фрагмента сообщения. */
@Composable
fun SelectMessageTextDialog(text: String, theme: AppTheme, onCopyAll: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(ThemeManager.dialogSurface(theme))
                .padding(18.dp)
        ) {
            Text(
                "Выделите нужный фрагмент",
                fontWeight = FontWeight.Bold, fontSize = 16.sp,
                color = ThemeManager.parseColor(theme.textPrimaryColor)
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(
                        text,
                        fontSize = 16.sp, lineHeight = 22.sp,
                        color = ThemeManager.parseColor(theme.textPrimaryColor)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Закрыть", color = ThemeManager.parseColor(theme.textSecondaryColor))
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = onCopyAll,
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor), contentColor = Color.White)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Копировать всё")
                }
            }
        }
    }
}

/** Плавающая дата сверху и кнопка "вниз" поверх списка сообщений. */
@Composable
fun BoxScope.ChatFloatingOverlays(
    showDate: Boolean,
    dateText: String,
    showScrollDown: Boolean,
    palette: ChatPalette,
    theme: AppTheme,
    onScrollDown: () -> Unit
) {
    AnimatedVisibility(
        visible = showDate,
        enter = fadeIn(tween(150)) + slideInVertically(tween(180)) { -it / 2 },
        exit = fadeOut(tween(300)),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp)
    ) {
        ChatDateChip(dateText, palette)
    }
    AnimatedVisibility(
        visible = showScrollDown,
        enter = fadeIn() + scaleIn(initialScale = 0.7f),
        exit = fadeOut() + scaleOut(targetScale = 0.7f),
        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(ThemeManager.dialogSurface(theme))
                .clickable { onScrollDown() },
            contentAlignment = Alignment.Center
        ) {
            Icon(androidx.compose.material.icons.Icons.Default.KeyboardArrowDown, null, tint = palette.accent)
        }
    }
}

/** Служебное сообщение FunPay ("оплатил заказ", "подтвердил выполнение" и т.д.). */
@Composable
private fun ServiceMessage(
    message: ParsedMessage,
    palette: ChatPalette,
    onLinkClick: (MessageLink) -> Unit,
    modifier: Modifier
) {
    val hhmm = remember(message.time) { ChatDates.parse(message.time).hhmm }
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.serviceBg)
                .border(1.dp, palette.serviceBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MessageTextWithLinks(
                text = message.text,
                links = message.links,
                textColor = palette.serviceText,
                linkColor = palette.serviceLink,
                selectionKey = 0,
                onLinkClick = onLinkClick,
                selectionEnabled = false,
                fontSize = 13f,
                centered = true,
                boldLinks = true
            )
            if (hhmm.isNotEmpty()) {
                Text(hhmm, fontSize = 10.sp, color = palette.secondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/**
 * Карточка «о собеседнике» в начале переписки — как в Telegram
 * (имя, язык, дата регистрации, официальный ли это аккаунт).
 */
@Composable
fun ChatPeerInfoCard(
    name: String,
    isEnglish: Boolean,
    registration: String?,
    isOfficial: Boolean,
    palette: ChatPalette
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.serviceBg)
                .border(1.dp, palette.serviceBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            @Composable
            fun InfoRow(label: String, value: String) {
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 13.sp, color = palette.secondary, modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
                    Spacer(Modifier.width(10.dp))
                    Text(value, fontSize = 13.sp, color = palette.text, fontWeight = FontWeight.Medium)
                }
            }
            InfoRow("Язык", if (isEnglish) "🇺🇸 English" else "🇷🇺 Русский")
            if (!registration.isNullOrBlank()) InfoRow("Регистрация", registration)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isOfficial) Icons.Default.Security else Icons.Default.Info,
                    null,
                    tint = if (isOfficial) Color(0xFF43A047) else palette.secondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (isOfficial) "Официальный аккаунт" else "Неофициальный аккаунт",
                    fontSize = 12.sp,
                    color = if (isOfficial) Color(0xFF43A047) else palette.secondary
                )
            }
        }
    }
}

/** «11 августа 2024, 19:47 2 года назад» → «11 августа 2024» */
fun shortRegistration(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val date = raw.substringBefore(",").trim()
    return date.ifBlank { raw.trim() }
}

/** Плашка «В ответ …» над полем ввода, как в Telegram. */
@Composable
fun ReplyComposerBar(author: String, text: String, palette: ChatPalette, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (palette.isLight) Color.White else palette.inBubble)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Reply, null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(2.dp).fillMaxHeight().background(palette.accent))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("В ответ $author", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text.ifBlank { "Изображение" }, fontSize = 13.sp, color = palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, null, tint = palette.secondary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Превью последнего сообщения в списке чатов: «╭─ ⤸ цитата ╰ ответ» → «↩ ответ». */
fun chatPreviewText(raw: String): String {
    val m = Regex("╭─\\s*⤸\\s*([\\s\\S]*?)\\s*╰\\s*([\\s\\S]*)$").find(raw) ?: return raw
    return "↩ " + m.groupValues[2].replace(Regex("\\s+"), " ").trim().ifEmpty { "…" }
}

private val REPLY_SPLIT = Regex("^╭─\\s*⤸\\s?([\\s\\S]*?)\\n╰\\s?([\\s\\S]*)$")

/** «╭─ ⤸ цитата\n╰ ответ» → (цитата, ответ); для обычного текста — (null, текст). */
fun splitReplyText(text: String): Pair<String?, String> {
    val m = REPLY_SPLIT.find(text) ?: return null to text
    return m.groupValues[1].trim() to m.groupValues[2]
}
