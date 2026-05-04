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

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ── Модели ───────────────────────────────────────────────────────────────────

/**
 * Одна "игра" / назначение публикации. По сути — это пара (название, nodeId общего чата).
 * nodeId парсится из URL вида https://funpay.com/chat/?node=XXX
 */
data class ConcurentGame(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Общий чат",
    val nodeId: String = "flood",
    val url: String = "https://funpay.com/chat/?node=flood",
    val enabled: Boolean = true
)

/** Одно рекламное сообщение (текст). Плейсхолдеры поддерживаются (см. processTemplateVariables). */
data class ConcurentMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val enabled: Boolean = true,
    // Если список пустой — сообщение идёт во ВСЕ активные чаты (старое поведение).
    // Если заполнен — только в указанные (по game.id).
    val targetGameIds: List<String> = emptyList()
)

/** Статистика: кольцевой буфер событий за сутки, чтобы рисовать график/счётчик. */
data class ConcurentStatEvent(
    val timestamp: Long,
    val success: Boolean,
    val nodeId: String
)

/** Главные настройки Concurent. Всё хранится как JSON в funpay_prefs. */
data class ConcurentSettings(
    val enabled: Boolean = false,

    // базовый интервал в секундах между публикациями (минимум 10)
    val intervalSeconds: Int = 1800,

    // ±% случайного джиттера к интервалу (0..50). Делает паттерн "человечнее".
    val jitterPercent: Int = 15,

    // если true — пропускаем публикацию когда включён Busy Mode
    val pauseOnBusy: Boolean = true,

    // если true — пропускаем публикацию, если по расписанию занятости сейчас "занят"
    val pauseOnSchedule: Boolean = false,

    // тихие часы: не постить в диапазоне [quietFrom; quietTo), если enabled
    val quietHoursEnabled: Boolean = false,
    val quietFromHour: Int = 2,
    val quietToHour: Int = 7,

    // пройтись по сообщениям: "sequence" (по порядку) или "random"
    val messageMode: String = "sequence",

    // пройтись по играм: "sequence" (после каждого круга сообщений), "random" (каждая публикация — рандом),
    // "each_post" (каждый пост — следующая игра, сообщения идут параллельно)
    val gameMode: String = "sequence",

    // AI-рерайт сообщения перед отправкой (использует существующий repository.rewriteMessage)
    val aiRewriteEnabled: Boolean = false,

    // стоп после N публикаций (0 = без ограничения)
    val stopAfterCount: Int = 0,

    // текущее состояние (двигается движком)
    val currentMessageIndex: Int = 0,
    val currentGameIndex: Int = 0,
    val postsSinceEnable: Int = 0,
    val lastPostAt: Long = 0L,
    val nextPostAt: Long = 0L,

    // данные
    val messages: List<ConcurentMessage> = listOf(
        ConcurentMessage(text = "🔥 Лучшие цены на FunPay — пишите в ЛС!")
    ),
    val games: List<ConcurentGame> = listOf(
        ConcurentGame(name = "Общий чат", nodeId = "flood", url = "https://funpay.com/chat/?node=flood")
    ),

    // история (храним последние 200 событий — этого хватает для суточной статистики)
    val stats: List<ConcurentStatEvent> = emptyList()
)

// ── Менеджер ─────────────────────────────────────────────────────────────────

object ConcurentManager {
    private const val PREFS_NAME = "funpay_prefs"
    private const val KEY = "concurent_settings_v2"
    private val gson = Gson()

    // Минимум/максимум интервала (сек). Ниже 10 сек — это мгновенный бан, блокируем.
    const val MIN_INTERVAL = 10
    const val MAX_INTERVAL = 24 * 3600

    // Порог "безопасного" интервала — ниже этого показываем красное предупреждение.
    const val SAFE_INTERVAL = 1800 // 30 минут

    fun getSettings(context: Context): ConcurentSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, null) ?: return ConcurentSettings()
        return try {
            gson.fromJson(json, ConcurentSettings::class.java) ?: ConcurentSettings()
        } catch (e: Exception) {
            LogManager.addLogDebug("⚠️ Concurent: ошибка парсинга настроек — сбрасываю. ${e.message}")
            ConcurentSettings()
        }
    }

    fun saveSettings(context: Context, settings: ConcurentSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, gson.toJson(settings)).apply()
    }

    /** Сбрасываем прогресс (индексы, счётчики) — обычно при включении. */
    fun resetProgress(context: Context) {
        val s = getSettings(context)
        saveSettings(context, s.copy(
            currentMessageIndex = 0,
            currentGameIndex = 0,
            postsSinceEnable = 0,
            lastPostAt = 0L,
            nextPostAt = System.currentTimeMillis() + s.intervalSeconds * 1000L
        ))
    }

    /** Пора ли публиковать прямо сейчас? */
    fun shouldPostNow(context: Context, settings: ConcurentSettings): Boolean {
        if (!settings.enabled) return false
        if (settings.messages.none { it.enabled && it.text.isNotBlank() }) return false
        if (settings.games.none { it.enabled && it.nodeId.isNotBlank() }) return false

        // Стоп после N публикаций
        if (settings.stopAfterCount > 0 && settings.postsSinceEnable >= settings.stopAfterCount) {
            return false
        }

        // Тихие часы
        if (settings.quietHoursEnabled && isInQuietHours(settings)) {
            return false
        }

        // Занятость
        if (settings.pauseOnBusy) {
            try {
                val busy = ChatFolderManager.getBusyMode(context)
                if (busy.enabled) return false
            } catch (_: Exception) {}
        }
        if (settings.pauseOnSchedule) {
            try {
                val slot = ChatFolderManager.getActiveScheduleSlot(context)
                if (slot != null) return false
            } catch (_: Exception) {}
        }

        // Время пришло?
        val now = System.currentTimeMillis()
        if (settings.lastPostAt == 0L) return true // первый запуск
        return now >= settings.nextPostAt
    }

    private fun isInQuietHours(s: ConcurentSettings): Boolean {
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val from = s.quietFromHour.coerceIn(0, 23)
        val to = s.quietToHour.coerceIn(0, 23)
        return if (from == to) false
        else if (from < to) hour in from until to
        else hour >= from || hour < to // через полночь (например, 22 → 7)
    }

    /** Считаем время следующей публикации с учётом джиттера. */
    fun scheduleNextPost(settings: ConcurentSettings): Long {
        val base = settings.intervalSeconds.coerceAtLeast(MIN_INTERVAL) * 1000L
        val jitter = settings.jitterPercent.coerceIn(0, 50)
        val offset = if (jitter == 0) 0L else {
            val delta = base * jitter / 100
            Random.nextLong(-delta, delta + 1)
        }
        return System.currentTimeMillis() + max(MIN_INTERVAL * 1000L, base + offset)
    }

    /**
     * Выбираем следующее сообщение и игру согласно режимам ротации.
     * Возвращает (game, message, newMsgIndex, newGameIndex).
     *
     * Новая логика: если у сообщения задан targetGameIds — текущая игра должна быть в этом списке,
     * иначе пропускаем сообщение и берём следующее. Если ни одно подходящее не найдено —
     * берём следующую игру и повторяем.
     */
    fun pickNext(settings: ConcurentSettings): NextPick? {
        val activeMessages = settings.messages.filter { it.enabled && it.text.isNotBlank() }
        val activeGames = settings.games.filter { it.enabled && it.nodeId.isNotBlank() }
        if (activeMessages.isEmpty() || activeGames.isEmpty()) return null

        var msgIdx = settings.currentMessageIndex.coerceAtLeast(0) % activeMessages.size
        var gameIdx = settings.currentGameIndex.coerceAtLeast(0) % activeGames.size

        // Вспомогательная функция: подходит ли сообщение к конкретной игре
        fun messageFitsGame(msg: ConcurentMessage, game: ConcurentGame): Boolean {
            return msg.targetGameIds.isEmpty() || msg.targetGameIds.contains(game.id)
        }

        // Случайный режим: тупо перебором берём валидную пару
        if (settings.messageMode == "random" || settings.gameMode == "random") {
            val shuffledGames = activeGames.shuffled()
            for (game in shuffledGames) {
                val candidates = activeMessages.filter { messageFitsGame(it, game) }
                if (candidates.isNotEmpty()) {
                    val message = if (settings.messageMode == "random") candidates.random()
                    else activeMessages[msgIdx].takeIf { messageFitsGame(it, game) } ?: candidates.first()
                    val actualMsgIdx = activeMessages.indexOf(message)
                    val actualGameIdx = activeGames.indexOf(game)
                    val nextMsgIdx = (actualMsgIdx + 1) % activeMessages.size
                    val nextGameIdx = when (settings.gameMode) {
                        "each_post" -> (actualGameIdx + 1) % activeGames.size
                        "sequence" -> if (nextMsgIdx == 0) (actualGameIdx + 1) % activeGames.size else actualGameIdx
                        else -> actualGameIdx
                    }
                    return NextPick(game, message, nextMsgIdx, nextGameIdx)
                }
            }
            return null
        }

        // Последовательный режим: пытаемся взять текущее сообщение для текущей игры.
        // Если не подходит — прокручиваем сообщения. Если все сообщения для этой игры не подходят —
        // прокручиваем игру. Всего — один круг, чтобы не зациклиться.
        repeat(activeGames.size) { gameAttempt ->
            repeat(activeMessages.size) { msgAttempt ->
                val message = activeMessages[msgIdx]
                val game = activeGames[gameIdx]
                if (messageFitsGame(message, game)) {
                    val nextMsgIdx = (msgIdx + 1) % activeMessages.size
                    val nextGameIdx = when (settings.gameMode) {
                        "each_post" -> (gameIdx + 1) % activeGames.size
                        "sequence" -> if (nextMsgIdx == 0) (gameIdx + 1) % activeGames.size else gameIdx
                        else -> gameIdx
                    }
                    return NextPick(game, message, nextMsgIdx, nextGameIdx)
                }
                msgIdx = (msgIdx + 1) % activeMessages.size
            }
            // В этой игре ни одно сообщение не подходит — идём к следующей
            gameIdx = (gameIdx + 1) % activeGames.size
            msgIdx = 0
        }
        return null
    }

    data class NextPick(
        val game: ConcurentGame,
        val message: ConcurentMessage,
        val newMessageIndex: Int,
        val newGameIndex: Int
    )

    /**
     * Главный шаг движка. Вызывается из FunPayService.startWorkLoop() на каждой итерации.
     * Сам решит, пора ли слать, и отправит через repository.sendMessage.
     */
    suspend fun tick(context: Context, repository: FunPayRepository) {
        val s = getSettings(context)
        if (!s.enabled) return
        if (!shouldPostNow(context, s)) return

        val pick = pickNext(s) ?: run {
            LogManager.addLogDebug("🎯 Concurent: нет активных сообщений/игр")
            return
        }

        // AI-рерайт (если включён) — подставим для вариативности
        val textToSend = if (s.aiRewriteEnabled) {
            try {
                val rewritten = repository.rewriteMessage(
                    text = pick.message.text,
                    contextHistory = "Это рекламное объявление в общем чате FunPay. Перефразируй текст, сохранив смысл и вставки типа \$username. Не добавляй ничего от себя, только перепиши. Длина — не больше оригинала."
                )
                if (!rewritten.isNullOrBlank()) rewritten else pick.message.text
            } catch (e: Exception) {
                LogManager.addLogDebug("⚠️ Concurent AI rewrite: ${e.message}")
                pick.message.text
            }
        } else pick.message.text

        // Подстановка переменных (юзернейм тут неоткуда взять, но {date}/{time} пригодятся)
        val finalText = try {
            processTemplateVariables(textToSend, username = "")
        } catch (_: Exception) { textToSend }

        LogManager.addLog("🎯 Concurent: отправка в «${pick.game.name}» (node=${pick.game.nodeId})")
        val ok = try {
            repository.sendMessage(pick.game.nodeId, finalText)
        } catch (e: Exception) {
            LogManager.addLog("❌ Concurent: ошибка отправки — ${e.message}")
            false
        }

        val now = System.currentTimeMillis()
        val newEvent = ConcurentStatEvent(now, ok, pick.game.nodeId)
        val fresh = getSettings(context) // перечитываем на случай, если UI что-то поменял
        val updated = fresh.copy(
            currentMessageIndex = pick.newMessageIndex,
            currentGameIndex = pick.newGameIndex,
            postsSinceEnable = fresh.postsSinceEnable + 1,
            lastPostAt = now,
            nextPostAt = scheduleNextPost(fresh),
            stats = (fresh.stats + newEvent).takeLast(200)
        )
        saveSettings(context, updated)

        if (ok) {
            LogManager.addLog("✅ Concurent: опубликовано (#${updated.postsSinceEnable})")
        }

        // Между играми даём небольшую паузу, чтобы не лететь подряд при редких условиях
        delay(300)
    }

    /** Быстрая статистика за последние 24ч. */
    fun getDayStats(s: ConcurentSettings): Pair<Int, Int> {
        val dayAgo = System.currentTimeMillis() - 24 * 3600 * 1000L
        val recent = s.stats.filter { it.timestamp >= dayAgo }
        val ok = recent.count { it.success }
        val fail = recent.count { !it.success }
        return ok to fail
    }

    /**
     * Утилита: достать nodeId из:
     *  - "flood"                                   → "flood"
     *  - "https://funpay.com/chat/?node=game-81"   → "game-81"
     *  - "https://funpay.com/lots/1544/"           → "game-1544"  (для lots-страниц)
     *  - "https://funpay.com/chips/210/"           → null         (chip'ы обычно не имеют общего чата)
     */
    fun parseNodeId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        // Уже чистый nodeId
        if (!trimmed.contains("/") && !trimmed.contains("?")) return trimmed
        // chat/?node=XXX
        Regex("node=([^&\\s]+)").find(trimmed)?.let {
            return it.groupValues[1].takeIf { v -> v.isNotBlank() }
        }
        // lots/<id>/ → чат этой категории обычно называется game-<id>
        Regex("/lots/(\\d+)/?").find(trimmed)?.let {
            return "game-${it.groupValues[1]}"
        }
        return null
    }

    /** Утилита: собрать URL из nodeId. */
    fun buildUrl(nodeId: String): String = "https://funpay.com/chat/?node=${nodeId.trim()}"

    /**
     * Импорт настроек из chrome extension (если пользователь руками экспортнул JSON).
     * Принимает строку JSON в формате concurentSettings из оригинального расширения.
     */
    fun importFromExtensionJson(context: Context, json: String): Boolean {
        return try {
            val obj = gson.fromJson<Map<String, Any?>>(json, object : TypeToken<Map<String, Any?>>() {}.type)
                ?: return false
            val intervalMinutes = (obj["interval"] as? Double) ?: 30.0
            val msgsRaw = obj["messages"] as? List<*> ?: emptyList<Any?>()
            val gamesRaw = obj["games"] as? List<*> ?: emptyList<Any?>()

            val msgs = msgsRaw.filterIsInstance<String>().filter { it.isNotBlank() }
                .map { ConcurentMessage(text = it) }
            val games = gamesRaw.mapNotNull { raw ->
                (raw as? Map<*, *>)?.let { g ->
                    val name = g["name"] as? String ?: return@let null
                    val url = g["url"] as? String ?: return@let null
                    val node = parseNodeId(url) ?: return@let null
                    ConcurentGame(name = name, nodeId = node, url = url)
                }
            }

            val current = getSettings(context)
            saveSettings(context, current.copy(
                intervalSeconds = (intervalMinutes * 60).toInt().coerceAtLeast(MIN_INTERVAL),
                messages = if (msgs.isNotEmpty()) msgs else current.messages,
                games = if (games.isNotEmpty()) games else current.games
            ))
            true
        } catch (e: Exception) {
            LogManager.addLogDebug("⚠️ Concurent import error: ${e.message}")
            false
        }
    }

    fun exportToExtensionJson(context: Context): String {
        val s = getSettings(context)
        val map = mapOf(
            "interval" to (s.intervalSeconds / 60.0),
            "messages" to s.messages.filter { it.enabled }.map { it.text },
            "games" to s.games.filter { it.enabled }.map {
                mapOf("name" to it.name, "url" to it.url)
            },
            "enabled" to s.enabled,
            "currentIndex" to s.currentMessageIndex,
            "currentGameIndex" to s.currentGameIndex
        )
        return gson.toJson(map)
    }
}