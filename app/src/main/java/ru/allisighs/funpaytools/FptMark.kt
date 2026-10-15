package ru.allisighs.funpaytools

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Метка FunPay Tools в сообщениях — то же, что content/features/fpt_identifier.js в расширении.
 *
 * В конец своего сообщения через пробел дописываются 3 невидимых символа нулевой ширины
 * (​‍‌). Человек их не видит, а расширение и приложение у собеседника
 * узнают по ним, что он тоже сидит с FunPay Tools.
 *
 * Условия вставки совпадают с shouldInject() из расширения посимвольно, чтобы не ломать
 * ключи, логины, ссылки и команды ботам.
 */
object FptMark {
    const val SIGNATURE = "​‍‌"

    private const val PREFS = "fpt_mark"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_USERS = "users"
    private const val KEY_DISMISSED = "dismissed"

    private val linkRe = Regex("(https?://|www\\.|ftp://)", RegexOption.IGNORE_CASE)
    private val funpayRe = Regex("funpay\\.com", RegexOption.IGNORE_CASE)
    private val winPathRe = Regex("[A-Za-z]:\\\\")
    private val unixPathRe = Regex("^/[a-z]", RegexOption.IGNORE_CASE)
    private val zeroWidthRe = Regex("[​‌‍﻿]")
    private val latinRe = Regex("[A-Za-z]")
    private val blockedFirstChars = setOf('/', '.', '!', '+', '№', '\\', '"', ':', '(', ')', '?', '#')
    /** Подпись вместе с пробелом перед ней (пробел мог съесть trim у FunPay). */
    private val signatureRe = Regex(" ?" + SIGNATURE)

    private val _users = MutableStateFlow<Set<String>>(emptySet())
    /** ID пользователей FunPay, у которых замечена метка. Живой поток, чтобы плашка появлялась сразу. */
    val users: StateFlow<Set<String>> = _users.asStateFlow()

    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    val dismissed: StateFlow<Set<String>> = _dismissed.asStateFlow()

    @Volatile private var enabledCache: Boolean = true
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext != null) return
        val c = context.applicationContext ?: context
        appContext = c
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabledCache = p.getBoolean(KEY_ENABLED, true)
        _users.value = p.getStringSet(KEY_USERS, emptySet())?.toSet() ?: emptySet()
        _dismissed.value = p.getStringSet(KEY_DISMISSED, emptySet())?.toSet() ?: emptySet()
    }

    fun isEnabled(context: Context? = null): Boolean {
        context?.let { init(it) }
        return enabledCache
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        init(context)
        enabledCache = enabled
        prefs()?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
    }

    /** Порт shouldInject(text) из расширения. */
    fun shouldInject(text: String?): Boolean {
        if (text == null || text.trim().length < 4) return false
        if (linkRe.containsMatchIn(text)) return false
        if (funpayRe.containsMatchIn(text)) return false
        if (winPathRe.containsMatchIn(text) || unixPathRe.containsMatchIn(text)) return false
        if (zeroWidthRe.containsMatchIn(text)) return false
        val first = text.trimStart().firstOrNull() ?: return false
        if (first in blockedFirstChars) return false
        if (latinRe.findAll(text).count() > 24) return false
        return true
    }

    /** Добавляет подпись к тексту, если метка включена и текст подходит. */
    fun sign(text: String): String {
        if (!enabledCache || !shouldInject(text)) return text
        if (text.endsWith(SIGNATURE)) return text
        return (if (text.endsWith(" ")) text else "$text ") + SIGNATURE
    }

    fun has(text: String?): Boolean = text != null && text.contains(SIGNATURE)

    /** Убирает подпись из текста, чтобы она не попадала в копирование, автоответы и сравнения. */
    fun strip(text: String): String {
        if (!text.contains(SIGNATURE)) return text
        return text.replace(signatureRe, "").trimEnd()
    }

    /** Запоминает, что пользователь пишет с FunPay Tools. Вызывается при разборе входящих сообщений. */
    fun markUser(userId: String?) {
        val id = userId?.trim().orEmpty()
        if (id.isEmpty() || id == "0" || id.any { !it.isDigit() }) return
        if (id in _users.value) return
        val next = _users.value + id
        _users.value = next
        prefs()?.edit()?.putStringSet(KEY_USERS, next)?.apply()
    }

    fun isFptUser(userId: String?): Boolean = !userId.isNullOrBlank() && userId in _users.value

    fun dismissBanner(userId: String) {
        if (userId.isBlank()) return
        val next = _dismissed.value + userId
        _dismissed.value = next
        prefs()?.edit()?.putStringSet(KEY_DISMISSED, next)?.apply()
    }

    /** Для тестов: сброс состояния без контекста. */
    internal fun resetForTest() {
        appContext = null
        enabledCache = true
        _users.value = emptySet()
        _dismissed.value = emptySet()
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
