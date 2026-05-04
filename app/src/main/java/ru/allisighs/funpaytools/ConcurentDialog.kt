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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Диалог настроек Concurent — рекламные публикации в общих чатах.
 *
 * @param onOpenBrowser коллбэк — открыть встроенный браузер в режиме "Выбор чата".
 *                     Браузер потом вернёт результат через [onBrowserResultConsume].
 * @param pendingBrowserResult результат из браузера (nodeId, name) если есть — диалог сам его подхватит.
 * @param onBrowserResultConsume вызывается когда диалог использовал результат.
 */
@Composable
fun ConcurentDialog(
    repository: FunPayRepository,
    theme: AppTheme,
    onDismiss: () -> Unit,
    onOpenBrowser: () -> Unit = {},
    pendingBrowserResult: Pair<String, String>? = null,
    onBrowserResultConsume: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val accent = ThemeManager.parseColor(theme.accentColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val bg = ThemeManager.parseColor(theme.backgroundColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)

    var settings by remember { mutableStateOf(ConcurentManager.getSettings(context)) }

    // UI-state
    var tab by remember { mutableIntStateOf(0) } // 0 сообщения, 1 игры, 2 настройки, 3 статистика
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var editingMessageText by remember { mutableStateOf("") }
    var showAddMessage by remember { mutableStateOf(false) }
    var newMessageText by remember { mutableStateOf("") }

    var editingGameId by remember { mutableStateOf<String?>(null) }
    var editingGameName by remember { mutableStateOf("") }
    var editingGameUrl by remember { mutableStateOf("") }
    var showAddGame by remember { mutableStateOf(false) }
    var newGameUrl by remember { mutableStateOf("") }
    var newGameName by remember { mutableStateOf("") }

    var showImportDialog by remember { mutableStateOf(false) }
    var importJson by remember { mutableStateOf("") }

    fun persist(s: ConcurentSettings) {
        settings = s
        ConcurentManager.saveSettings(context, s)
    }

    // Подхватываем результат от встроенного браузера (если нам вернули nodeId+name)
    LaunchedEffect(pendingBrowserResult) {
        if (pendingBrowserResult != null) {
            val (nodeId, name) = pendingBrowserResult
            val url = ConcurentManager.buildUrl(nodeId)
            val newGame = ConcurentGame(name = name.ifBlank { "Чат $nodeId" }, nodeId = nodeId, url = url)
            // Не дублируем
            if (settings.games.none { it.nodeId == nodeId }) {
                persist(settings.copy(games = settings.games + newGame))
                Toast.makeText(context, "Добавлено: ${newGame.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Этот чат уже есть в списке", Toast.LENGTH_SHORT).show()
            }
            onBrowserResultConsume()
        }
    }

    // ── ДИАЛОГ ИМПОРТА ────────────────────────────────────────────────────
    if (showImportDialog) {
        Dialog(onDismissRequest = { showImportDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Импорт настроек из расширения", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Вставьте JSON из chrome.storage.local (concurentSettings)", color = textSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = importJson, onValueChange = { importJson = it },
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        placeholder = { Text("{\"interval\": 30, \"messages\": [...], \"games\": [...]}", color = textSecondary, fontSize = 11.sp) },
                        colors = fpOutlinedColors(accent, textPrimary, textSecondary)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showImportDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Отмена", color = textSecondary)
                        }
                        Button(
                            onClick = {
                                val ok = ConcurentManager.importFromExtensionJson(context, importJson)
                                if (ok) {
                                    settings = ConcurentManager.getSettings(context)
                                    Toast.makeText(context, "Импортировано ✅", Toast.LENGTH_SHORT).show()
                                    showImportDialog = false
                                    importJson = ""
                                } else {
                                    Toast.makeText(context, "Неверный JSON ❌", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) { Text("Импортировать", color = Color.White) }
                    }
                }
            }
        }
    }

    // ── ОСНОВНОЙ ДИАЛОГ ──────────────────────────────────────────────────
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
            colors = CardDefaults.cardColors(containerColor = surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {

                // Заголовок + главный переключатель
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, null, tint = accent, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Concurent", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 18.sp)
                        Text("Автопубликации в общих чатах", color = textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { on ->
                            val next = if (on) {
                                settings.copy(
                                    enabled = true,
                                    postsSinceEnable = 0,
                                    currentMessageIndex = 0,
                                    currentGameIndex = 0,
                                    lastPostAt = 0L,
                                    nextPostAt = System.currentTimeMillis() + settings.intervalSeconds * 1000L
                                )
                            } else settings.copy(enabled = false)
                            persist(next)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accent,
                            checkedTrackColor = accent.copy(alpha = 0.4f)
                        )
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = textPrimary)
                    }
                }

                // Статус-плашка
                Spacer(Modifier.height(10.dp))
                ConcurentStatusBar(settings, accent, surface, textPrimary, textSecondary)

                // Предупреждение о малом интервале
                if (settings.intervalSeconds < ConcurentManager.SAFE_INTERVAL) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.15f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Интервал меньше 30 минут — повышенный риск бана FunPay. На свой страх и риск.",
                            color = Color(0xFFE53935), fontSize = 11.sp, lineHeight = 14.sp
                        )
                    }
                }

                // Табы
                Spacer(Modifier.height(12.dp))
                ConcurentTabs(tab, theme) { tab = it }
                Spacer(Modifier.height(12.dp))

                // Контент выбранного таба
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> ConcurentMessagesTab(
                            settings = settings,
                            theme = theme,
                            onPersist = ::persist,
                            showAddMessage = showAddMessage,
                            setShowAddMessage = { showAddMessage = it },
                            newMessageText = newMessageText,
                            setNewMessageText = { newMessageText = it },
                            editingMessageId = editingMessageId,
                            setEditingMessageId = { editingMessageId = it },
                            editingMessageText = editingMessageText,
                            setEditingMessageText = { editingMessageText = it }
                        )
                        1 -> ConcurentGamesTab(
                            settings = settings,
                            theme = theme,
                            onPersist = ::persist,
                            onOpenBrowser = onOpenBrowser,
                            showAddGame = showAddGame,
                            setShowAddGame = { showAddGame = it },
                            newGameUrl = newGameUrl,
                            setNewGameUrl = { newGameUrl = it },
                            newGameName = newGameName,
                            setNewGameName = { newGameName = it },
                            editingGameId = editingGameId,
                            setEditingGameId = { editingGameId = it },
                            editingGameName = editingGameName,
                            setEditingGameName = { editingGameName = it },
                            editingGameUrl = editingGameUrl,
                            setEditingGameUrl = { editingGameUrl = it }
                        )
                        2 -> ConcurentOptionsTab(
                            settings = settings,
                            theme = theme,
                            onPersist = ::persist
                        )
                        3 -> ConcurentStatsTab(
                            settings = settings,
                            theme = theme,
                            onImport = { showImportDialog = true },
                            onExport = {
                                val json = ConcurentManager.exportToExtensionJson(context)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("concurent", json))
                                Toast.makeText(context, "JSON скопирован в буфер", Toast.LENGTH_SHORT).show()
                            },
                            onResetStats = {
                                persist(settings.copy(stats = emptyList(), postsSinceEnable = 0))
                                Toast.makeText(context, "Статистика очищена", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── STATUS BAR ──────────────────────────────────────────────────────────────

@Composable
private fun ConcurentStatusBar(
    s: ConcurentSettings,
    accent: Color,
    surface: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    // Тикаем раз в секунду, пока диалог открыт и Concurent включён.
    // Без этого System.currentTimeMillis() читался один раз на рекомпозиции и
    // таймер "замораживался" — обновлялся лишь при переключении вкладок.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.enabled, s.nextPostAt) {
        while (s.enabled && s.nextPostAt > 0L) {
            delay(1000)
            tick++
        }
    }

    val now = remember(tick) { System.currentTimeMillis() }
    val secondsLeft = if (!s.enabled || s.nextPostAt == 0L) 0
    else ((s.nextPostAt - now) / 1000).coerceAtLeast(0).toInt()

    val (ok, fail) = ConcurentManager.getDayStats(s)

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(surface.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (!s.enabled) "Выключено"
                else if (secondsLeft > 0) "Следующая: ${formatSeconds(secondsLeft)}"
                else "Готов к публикации",
                color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
            Text(
                "За 24ч: ✅ $ok  ❌ $fail  •  всего в сессии: ${s.postsSinceEnable}",
                color = textSecondary, fontSize = 11.sp
            )
        }
        Icon(
            if (s.enabled) Icons.Default.PlayArrow else Icons.Default.Pause,
            null, tint = accent, modifier = Modifier.size(20.dp)
        )
    }
}

private fun formatSeconds(s: Int): String {
    if (s < 60) return "${s}с"
    val m = s / 60
    val sec = s % 60
    if (m < 60) return "${m}м ${sec}с"
    val h = m / 60
    return "${h}ч ${m % 60}м"
}

// ── TABS ────────────────────────────────────────────────────────────────────

@Composable
private fun ConcurentTabs(selected: Int, theme: AppTheme, onSelect: (Int) -> Unit) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)

    val tabs = listOf(
        "Тексты" to Icons.Default.ShortText,
        "Чаты" to Icons.Default.Chat,
        "Режим" to Icons.Default.Tune,
        "Отчёт" to Icons.Default.BarChart
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { idx, (title, icon) ->
            val isSel = idx == selected
            FilterChip(
                selected = isSel,
                onClick = { onSelect(idx) },
                label = { Text(title, fontSize = 12.sp) },
                leadingIcon = { Icon(icon, null, modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White,
                    labelColor = textPrimary,
                    iconColor = textSecondary
                )
            )
        }
    }
}

// ── TAB 0: MESSAGES ─────────────────────────────────────────────────────────

@Composable
private fun ConcurentMessagesTab(
    settings: ConcurentSettings,
    theme: AppTheme,
    onPersist: (ConcurentSettings) -> Unit,
    showAddMessage: Boolean,
    setShowAddMessage: (Boolean) -> Unit,
    newMessageText: String,
    setNewMessageText: (String) -> Unit,
    editingMessageId: String?,
    setEditingMessageId: (String?) -> Unit,
    editingMessageText: String,
    setEditingMessageText: (String) -> Unit
) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)

    // State: какое сообщение сейчас редактирует таргет (привязку к чатам)
    var targetingMessageId by remember { mutableStateOf<String?>(null) }

    // Модальный выбор таргетов
    if (targetingMessageId != null) {
        val msg = settings.messages.firstOrNull { it.id == targetingMessageId }
        if (msg != null) {
            ConcurentTargetPickerDialog(
                message = msg,
                games = settings.games,
                theme = theme,
                onSave = { newTargets ->
                    val updated = settings.messages.map {
                        if (it.id == msg.id) it.copy(targetGameIds = newTargets) else it
                    }
                    onPersist(settings.copy(messages = updated))
                    targetingMessageId = null
                },
                onDismiss = { targetingMessageId = null }
            )
        } else {
            targetingMessageId = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Форма добавления
        if (showAddMessage) {
            OutlinedTextField(
                value = newMessageText, onValueChange = setNewMessageText,
                modifier = Modifier.fillMaxWidth().height(110.dp),
                placeholder = { Text("Текст объявления. Поддерживаются \$date, \$time", color = textSecondary, fontSize = 12.sp) },
                colors = fpOutlinedColors(accent, textPrimary, textSecondary)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { setShowAddMessage(false); setNewMessageText("") }, modifier = Modifier.weight(1f)) {
                    Text("Отмена", color = textSecondary)
                }
                Button(
                    onClick = {
                        if (newMessageText.isNotBlank()) {
                            onPersist(settings.copy(messages = settings.messages + ConcurentMessage(text = newMessageText)))
                        }
                        setShowAddMessage(false); setNewMessageText("")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) { Text("Добавить", color = Color.White) }
            }
        } else {
            Button(
                onClick = { setShowAddMessage(true) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Новое объявление")
            }
        }

        Spacer(Modifier.height(10.dp))

        if (settings.messages.isEmpty()) {
            ConcurentEmptyState(icon = Icons.Default.ShortText, text = "Пока нет сообщений", theme = theme)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(settings.messages, key = { it.id }) { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = surface.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            if (editingMessageId == msg.id) {
                                OutlinedTextField(
                                    value = editingMessageText, onValueChange = setEditingMessageText,
                                    modifier = Modifier.fillMaxWidth().height(110.dp),
                                    colors = fpOutlinedColors(accent, textPrimary, textSecondary)
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { setEditingMessageId(null) }, modifier = Modifier.weight(1f)) {
                                        Text("Отмена", color = textSecondary, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = {
                                            val updated = settings.messages.map {
                                                if (it.id == msg.id) it.copy(text = editingMessageText) else it
                                            }
                                            onPersist(settings.copy(messages = updated))
                                            setEditingMessageId(null)
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                                    ) { Text("OK", color = Color.White, fontSize = 12.sp) }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = msg.enabled,
                                        onCheckedChange = { on ->
                                            val updated = settings.messages.map {
                                                if (it.id == msg.id) it.copy(enabled = on) else it
                                            }
                                            onPersist(settings.copy(messages = updated))
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = accent,
                                            checkedTrackColor = accent.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.scale(0.8f)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            msg.text.take(120) + if (msg.text.length > 120) "…" else "",
                                            color = if (msg.enabled) textPrimary else textSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        // Плашка с таргетом: "Во все чаты" или "Только в: name1, name2…"
                                        Spacer(Modifier.height(4.dp))
                                        val targetLabel = if (msg.targetGameIds.isEmpty()) {
                                            "🌐 Во все активные чаты"
                                        } else {
                                            val names = settings.games
                                                .filter { it.id in msg.targetGameIds }
                                                .joinToString(", ") { it.name }
                                                .ifBlank { "нет (битая привязка)" }
                                            "🎯 Только: $names"
                                        }
                                        Text(
                                            targetLabel,
                                            color = textSecondary, fontSize = 10.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable { targetingMessageId = msg.id }
                                        )
                                    }
                                    IconButton(onClick = { targetingMessageId = msg.id }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.FilterAlt, null, tint = accent, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = {
                                        setEditingMessageId(msg.id)
                                        setEditingMessageText(msg.text)
                                    }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Edit, null, tint = accent, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = {
                                        onPersist(settings.copy(messages = settings.messages.filter { it.id != msg.id }))
                                    }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── TAB 1: GAMES (чаты) ─────────────────────────────────────────────────────

@Composable
private fun ConcurentGamesTab(
    settings: ConcurentSettings,
    theme: AppTheme,
    onPersist: (ConcurentSettings) -> Unit,
    onOpenBrowser: () -> Unit,
    showAddGame: Boolean,
    setShowAddGame: (Boolean) -> Unit,
    newGameUrl: String, setNewGameUrl: (String) -> Unit,
    newGameName: String, setNewGameName: (String) -> Unit,
    editingGameId: String?, setEditingGameId: (String?) -> Unit,
    editingGameName: String, setEditingGameName: (String) -> Unit,
    editingGameUrl: String, setEditingGameUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val accent = ThemeManager.parseColor(theme.accentColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)

    // Локальное состояние пикера каталога игр.
    var showPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Три кнопки. Главный flow — "Поиск по каталогу" (яркая, accent).
        // "Браузер" (outlined, тусклая) и "Вручную" (text, самая тусклая) — запасные.
        Row(verticalAlignment = Alignment.CenterVertically) {

            // 1. ОСНОВНАЯ — Поиск по каталогу.
            Button(
                onClick = { showPicker = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Search, null, modifier = Modifier.size(15.dp), tint = Color.White)
                Spacer(Modifier.width(5.dp))
                Text("Поиск по каталогу", fontSize = 11.sp, color = Color.White, maxLines = 1)
            }

            Spacer(Modifier.width(6.dp))

            // 2. Вторичная — Браузер.
            OutlinedButton(
                onClick = onOpenBrowser,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, textSecondary.copy(alpha = 0.35f))
            ) {
                Icon(Icons.Default.Language, null, modifier = Modifier.size(14.dp), tint = textPrimary)
                Spacer(Modifier.width(4.dp))
                Text("Браузер", fontSize = 11.sp, color = textPrimary, maxLines = 1)
            }

            Spacer(Modifier.width(4.dp))

            // 3. Третичная — Вручную.
            TextButton(
                onClick = { setShowAddGame(!showAddGame) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Icon(
                    if (showAddGame) Icons.Default.Close else Icons.Default.Add,
                    null, tint = textSecondary, modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    if (showAddGame) "Отмена" else "Вручную",
                    color = textSecondary, fontSize = 11.sp, maxLines = 1
                )
            }
        }
        // Подсказка под кнопками
        Spacer(Modifier.height(4.dp))
        Text(
            "Поиск по каталогу — самый быстрый способ. Браузер — если чат нестандартный. Вручную — если знаете node.",
            color = textSecondary, fontSize = 10.sp, lineHeight = 13.sp
        )

        // Диалог поиска по каталогу: 124 игровых чата, транслит, группы.
        // Не закрывается сразу после выбора — можно добавить несколько подряд.
        if (showPicker) {
            ConcurentGamePickerDialog(
                theme = theme,
                existingNodeIds = settings.games.map { it.nodeId }.toSet(),
                onPicked = { picked ->
                    val newGame = ConcurentGame(
                        name = picked.name,
                        nodeId = picked.nodeId,
                        url = ConcurentManager.buildUrl(picked.nodeId)
                    )
                    onPersist(settings.copy(games = settings.games + newGame))
                    Toast.makeText(context, "Добавлено: ${picked.name}", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showPicker = false }
            )
        }

        if (showAddGame) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = newGameUrl, onValueChange = setNewGameUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL или node", fontSize = 11.sp) },
                placeholder = { Text("https://funpay.com/chat/?node=...", color = textSecondary, fontSize = 11.sp) },
                singleLine = true,
                colors = fpOutlinedColors(accent, textPrimary, textSecondary)
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = newGameName, onValueChange = setNewGameName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название (для себя)", fontSize = 11.sp) },
                singleLine = true,
                colors = fpOutlinedColors(accent, textPrimary, textSecondary)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { setShowAddGame(false); setNewGameUrl(""); setNewGameName("") },
                    modifier = Modifier.weight(1f)
                ) { Text("Отмена", color = textSecondary) }
                Button(
                    onClick = {
                        val node = ConcurentManager.parseNodeId(newGameUrl)
                        if (node == null) {
                            Toast.makeText(context, "Не вижу node в URL", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (settings.games.any { it.nodeId == node }) {
                            Toast.makeText(context, "Такой чат уже есть", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val game = ConcurentGame(
                            name = newGameName.ifBlank { "Чат $node" },
                            nodeId = node,
                            url = ConcurentManager.buildUrl(node)
                        )
                        onPersist(settings.copy(games = settings.games + game))
                        setShowAddGame(false); setNewGameUrl(""); setNewGameName("")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) { Text("Добавить", color = Color.White) }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (settings.games.isEmpty()) {
            ConcurentEmptyState(icon = Icons.Default.Chat, text = "Нет чатов. Добавьте хотя бы один.", theme = theme)
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(settings.games, key = { it.id }) { game ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = surface.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            if (editingGameId == game.id) {
                                OutlinedTextField(
                                    value = editingGameName, onValueChange = setEditingGameName,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Название", fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = fpOutlinedColors(accent, textPrimary, textSecondary)
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = editingGameUrl, onValueChange = setEditingGameUrl,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("URL", fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = fpOutlinedColors(accent, textPrimary, textSecondary)
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { setEditingGameId(null) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Отмена", color = textSecondary, fontSize = 12.sp) }
                                    Button(
                                        onClick = {
                                            val node = ConcurentManager.parseNodeId(editingGameUrl) ?: return@Button
                                            val updated = settings.games.map {
                                                if (it.id == game.id)
                                                    it.copy(name = editingGameName, nodeId = node, url = ConcurentManager.buildUrl(node))
                                                else it
                                            }
                                            onPersist(settings.copy(games = updated))
                                            setEditingGameId(null)
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                                    ) { Text("OK", color = Color.White, fontSize = 12.sp) }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = game.enabled,
                                        onCheckedChange = { on ->
                                            val updated = settings.games.map {
                                                if (it.id == game.id) it.copy(enabled = on) else it
                                            }
                                            onPersist(settings.copy(games = updated))
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = accent,
                                            checkedTrackColor = accent.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.scale(0.8f)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            game.name,
                                            color = if (game.enabled) textPrimary else textSecondary,
                                            fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "node=${game.nodeId}",
                                            color = textSecondary, fontSize = 10.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = {
                                        setEditingGameId(game.id)
                                        setEditingGameName(game.name)
                                        setEditingGameUrl(game.url)
                                    }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Edit, null, tint = accent, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = {
                                        onPersist(settings.copy(games = settings.games.filter { it.id != game.id }))
                                    }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── TAB 2: OPTIONS ──────────────────────────────────────────────────────────

@Composable
private fun ConcurentOptionsTab(
    settings: ConcurentSettings,
    theme: AppTheme,
    onPersist: (ConcurentSettings) -> Unit
) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Интервал
        Text("Интервал публикации", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text("Сейчас: ${formatSeconds(settings.intervalSeconds)}", color = textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Slider(
            value = settings.intervalSeconds.toFloat(),
            onValueChange = {
                onPersist(settings.copy(intervalSeconds = it.toInt().coerceIn(ConcurentManager.MIN_INTERVAL, ConcurentManager.MAX_INTERVAL)))
            },
            valueRange = ConcurentManager.MIN_INTERVAL.toFloat()..7200f,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
        )
        // Быстрые пресеты
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "30с" to 30,
                "5м" to 300,
                "15м" to 900,
                "30м" to 1800,
                "1ч" to 3600,
                "2ч" to 7200
            ).forEach { (label, sec) ->
                FilterChip(
                    selected = settings.intervalSeconds == sec,
                    onClick = { onPersist(settings.copy(intervalSeconds = sec)) },
                    label = { Text(label, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent,
                        selectedLabelColor = Color.White,
                        labelColor = textPrimary
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Джиттер
        Text("Случайный разброс: ±${settings.jitterPercent}%", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text("Интервал будет плавать вокруг базового — чтобы паттерн не выглядел машинным", color = textSecondary, fontSize = 11.sp, lineHeight = 14.sp)
        Slider(
            value = settings.jitterPercent.toFloat(),
            onValueChange = { onPersist(settings.copy(jitterPercent = it.toInt().coerceIn(0, 50))) },
            valueRange = 0f..50f, steps = 49,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
        )

        Spacer(Modifier.height(12.dp))

        // Тихие часы
        ConcurentSwitchRow(
            title = "Тихие часы",
            desc = "Не постить в указанном диапазоне часов",
            checked = settings.quietHoursEnabled,
            accent = accent, textPrimary = textPrimary, textSecondary = textSecondary,
            onCheckedChange = { onPersist(settings.copy(quietHoursEnabled = it)) }
        )
        if (settings.quietHoursEnabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("С ${settings.quietFromHour}:00 до ${settings.quietToHour}:00", color = textPrimary, fontSize = 12.sp)
            }
            Text("От", color = textSecondary, fontSize = 10.sp)
            Slider(
                value = settings.quietFromHour.toFloat(),
                onValueChange = { onPersist(settings.copy(quietFromHour = it.toInt().coerceIn(0, 23))) },
                valueRange = 0f..23f, steps = 22,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
            )
            Text("До", color = textSecondary, fontSize = 10.sp)
            Slider(
                value = settings.quietToHour.toFloat(),
                onValueChange = { onPersist(settings.copy(quietToHour = it.toInt().coerceIn(0, 23))) },
                valueRange = 0f..23f, steps = 22,
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
            )
        }

        Spacer(Modifier.height(4.dp))

        // Пауза на занятость
        ConcurentSwitchRow(
            title = "Пауза в «Режиме занятости»",
            desc = "Не постить пока включён Busy Mode",
            checked = settings.pauseOnBusy,
            accent = accent, textPrimary = textPrimary, textSecondary = textSecondary,
            onCheckedChange = { onPersist(settings.copy(pauseOnBusy = it)) }
        )
        ConcurentSwitchRow(
            title = "Пауза по расписанию",
            desc = "Не постить в активных слотах расписания занятости",
            checked = settings.pauseOnSchedule,
            accent = accent, textPrimary = textPrimary, textSecondary = textSecondary,
            onCheckedChange = { onPersist(settings.copy(pauseOnSchedule = it)) }
        )

        // AI
        ConcurentSwitchRow(
            title = "AI-рерайт",
            desc = "Каждый раз слегка перефразировать сообщение через нейросеть — ниже шанс попасть под антиспам",
            checked = settings.aiRewriteEnabled,
            accent = accent, textPrimary = textPrimary, textSecondary = textSecondary,
            onCheckedChange = { onPersist(settings.copy(aiRewriteEnabled = it)) }
        )

        Spacer(Modifier.height(10.dp))

        // Режим сообщений
        Text("Порядок сообщений", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = settings.messageMode == "sequence",
                onClick = { onPersist(settings.copy(messageMode = "sequence")) },
                label = { Text("По порядку", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent, selectedLabelColor = Color.White, labelColor = textPrimary
                )
            )
            FilterChip(
                selected = settings.messageMode == "random",
                onClick = { onPersist(settings.copy(messageMode = "random")) },
                label = { Text("Случайно", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent, selectedLabelColor = Color.White, labelColor = textPrimary
                )
            )
        }

        Spacer(Modifier.height(10.dp))

        // Режим игр
        Text("Порядок чатов", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "sequence" to "Пачкой: все сообщения → следующий чат",
                "each_post" to "По очереди: каждая публикация — следующий чат",
                "random" to "Случайный чат каждый раз"
            ).forEach { (mode, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPersist(settings.copy(gameMode = mode)) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.gameMode == mode,
                        onClick = { onPersist(settings.copy(gameMode = mode)) },
                        colors = RadioButtonDefaults.colors(selectedColor = accent)
                    )
                    Text(label, color = textPrimary, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Стоп после N
        Text("Стоп после публикаций: ${if (settings.stopAfterCount == 0) "без ограничения" else settings.stopAfterCount}",
            color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Slider(
            value = settings.stopAfterCount.toFloat(),
            onValueChange = { onPersist(settings.copy(stopAfterCount = it.toInt().coerceIn(0, 500))) },
            valueRange = 0f..500f,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
        )

        Spacer(Modifier.height(20.dp))
    }
}

// ── TAB 3: STATS ────────────────────────────────────────────────────────────

@Composable
private fun ConcurentStatsTab(
    settings: ConcurentSettings,
    theme: AppTheme,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onResetStats: () -> Unit
) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)

    val (ok, fail) = ConcurentManager.getDayStats(settings)
    val total = ok + fail

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Карточки
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConcurentStatCard("✅ Успешно", ok.toString(), accent, surface, textPrimary, textSecondary, Modifier.weight(1f))
            ConcurentStatCard("❌ Ошибки", fail.toString(), Color(0xFFE53935), surface, textPrimary, textSecondary, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConcurentStatCard("📊 За 24ч", total.toString(), accent, surface, textPrimary, textSecondary, Modifier.weight(1f))
            ConcurentStatCard("⏱ В сессии", settings.postsSinceEnable.toString(), accent, surface, textPrimary, textSecondary, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        // Мини-график: просто последние 20 событий с иконками
        Text("Последние публикации", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        if (settings.stats.isEmpty()) {
            Text("Пока ничего не опубликовано", color = textSecondary, fontSize = 12.sp)
        } else {
            val last = settings.stats.takeLast(20).reversed()
            Card(
                colors = CardDefaults.cardColors(containerColor = surface.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    last.forEach { ev ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (ev.success) Icons.Default.Check else Icons.Default.Close,
                                null,
                                tint = if (ev.success) accent else Color(0xFFE53935),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            Text(fmt.format(java.util.Date(ev.timestamp)), color = textPrimary, fontSize = 11.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("node=${ev.nodeId}", color = textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Импорт/экспорт/сброс
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(16.dp), tint = textPrimary)
            Spacer(Modifier.width(6.dp))
            Text("Импорт из приватного расширения (JSON)", color = textPrimary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(16.dp), tint = textPrimary)
            Spacer(Modifier.width(6.dp))
            Text("Экспорт в буфер обмена", color = textPrimary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = onResetStats,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
        ) {
            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = Color(0xFFE53935))
            Spacer(Modifier.width(6.dp))
            Text("Сбросить статистику", fontSize = 12.sp)
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ConcurentStatCard(
    title: String, value: String,
    accent: Color, surface: Color, textPrimary: Color, textSecondary: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = surface.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = textSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Общие утилиты ───────────────────────────────────────────────────────────

@Composable
private fun ConcurentSwitchRow(
    title: String, desc: String, checked: Boolean,
    accent: Color, textPrimary: Color, textSecondary: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = textSecondary, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accent,
                checkedTrackColor = accent.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun ConcurentEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, theme: AppTheme) {
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = textSecondary.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(10.dp))
            Text(text, color = textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun fpOutlinedColors(accent: Color, textPrimary: Color, textSecondary: Color) =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = textPrimary,
        unfocusedTextColor = textPrimary,
        focusedBorderColor = accent,
        unfocusedBorderColor = textSecondary.copy(alpha = 0.4f),
        cursorColor = accent
    )

// ── Диалог выбора таргетов: к каким чатам привязано сообщение ───────────────

@Composable
private fun ConcurentTargetPickerDialog(
    message: ConcurentMessage,
    games: List<ConcurentGame>,
    theme: AppTheme,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = ThemeManager.parseColor(theme.accentColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)

    // "во все чаты" → пустой список. Отдельный чекбокс для этого состояния.
    var allChats by remember { mutableStateOf(message.targetGameIds.isEmpty()) }
    var selected by remember { mutableStateOf(message.targetGameIds.toSet()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Куда отправлять это объявление?", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Выберите конкретные чаты или оставьте «во все» — тогда объявление пойдёт в каждый активный чат по очереди.",
                    color = textSecondary, fontSize = 11.sp, lineHeight = 14.sp
                )
                Spacer(Modifier.height(12.dp))

                // Чекбокс "во все чаты"
                Row(
                    Modifier.fillMaxWidth().clickable {
                        allChats = !allChats
                        if (allChats) selected = emptySet()
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allChats,
                        onCheckedChange = { v ->
                            allChats = v
                            if (v) selected = emptySet()
                        },
                        colors = CheckboxDefaults.colors(checkedColor = accent)
                    )
                    Text("🌐 Во все активные чаты", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                if (!allChats) {
                    Spacer(Modifier.height(8.dp))
                    Text("Только в выбранные чаты:", color = textSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (games.isEmpty()) {
                            item {
                                Text("Нет чатов. Сначала добавьте хотя бы один во вкладке «Чаты».",
                                    color = textSecondary, fontSize = 11.sp,
                                    modifier = Modifier.padding(8.dp))
                            }
                        }
                        items(games, key = { it.id }) { g ->
                            val isSel = selected.contains(g.id)
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selected = if (isSel) selected - g.id else selected + g.id
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSel,
                                    onCheckedChange = { v ->
                                        selected = if (v) selected + g.id else selected - g.id
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = accent)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        g.name, color = textPrimary, fontSize = 13.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "node=${g.nodeId}${if (!g.enabled) " · выключен" else ""}",
                                        color = textSecondary, fontSize = 10.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Отмена", color = textSecondary)
                    }
                    Button(
                        onClick = {
                            onSave(if (allChats) emptyList() else selected.toList())
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) { Text("Сохранить", color = Color.White) }
                }
            }
        }
    }
}