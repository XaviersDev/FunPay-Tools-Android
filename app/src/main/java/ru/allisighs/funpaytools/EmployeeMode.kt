package ru.allisighs.funpaytools

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Режим сотрудника: в приложении остаются чаты, лоты и продажи, а автоматизация
 * (автоответы, автовыдача, автоподнятие, демпер, рассылки и т.д.) на этом телефоне не запускается,
 * чтобы не дублировать то, что уже работает у владельца магазина.
 * Хранится только на устройстве, сервер не нужен.
 */
object EmployeeMode {
    private const val PREFS = "fpt_employee"
    private const val KEY = "enabled"

    /** Текущее значение для интерфейса (обновляется сразу при переключении). */
    val state = mutableStateOf(false)
    private var loaded = false

    fun isOn(context: Context): Boolean {
        if (!loaded) {
            state.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
            loaded = true
        }
        return state.value
    }

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        state.value = on
        loaded = true
        LogManager.addLog(if (on) "👤 Режим сотрудника включён: автоматизация на этом устройстве остановлена" else "👤 Режим сотрудника выключен")
    }
}
