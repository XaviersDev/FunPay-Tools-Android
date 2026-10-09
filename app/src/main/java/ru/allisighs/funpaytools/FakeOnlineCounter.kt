
package ru.allisighs.funpaytools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random


/**
 * Читателям этого кода, хочу сказать, почему я это сделал.
 *   25 мая злой человек полностью испортил сервер Firebase, через который парсился онлайн
 *   Там было несколько гигабайтов, из-за чего онлайн грузился с более 127к людьми и нагружало трафик людям
 * Я решил сделать фейковый, но плюс минус реалистичный счётчик онлайна, чтобы люди не чувствовали себя пусто.
 */

object FakeOnlineCounter {

    private fun rangeForHour(hour: Int): Pair<Int, Int> = when (hour) {
        in 4..7   -> 600 to 1000          // раннее утро
        in 8..11  -> 2000 to 6500         // утро
        in 12..17 -> 12000 to 13000       // день
        in 18..23 -> 13000 to 15000       // вечер
        else      -> 6000 to 9000         // 00..03 — ночь
    }

    private fun baseTarget(unixSec: Long, lo: Int, hi: Int): Int {
        val mid = (lo + hi) / 2.0
        val amp = (hi - lo) / 2.0
        val s1 = sin(unixSec / 137.0)
        val s2 = sin(unixSec / 311.0 + 1.7)
        val s3 = sin(unixSec / 53.0 + 0.4)
        val norm = (s1 * 0.55 + s2 * 0.30 + s3 * 0.15) // в районе -1..+1
        return (mid + norm * amp).roundToInt().coerceIn(lo, hi)
    }

    private fun jitter(): Int {
        return if (Random.nextInt(100) < 80) {
            val v = Random.nextInt(1, 7)
            if (Random.nextBoolean()) v else -v
        } else {
            // крупный толчок
            val v = Random.nextInt(20, 151)
            if (Random.nextBoolean()) v else -v
        }
    }

    private fun currentMoscowHour(): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"))
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    private val _online = MutableStateFlow(0)
    val online: StateFlow<Int> = _online.asStateFlow()
    fun tick(): Int {
        val now = System.currentTimeMillis() / 1000L
        val hour = currentMoscowHour()
        val (lo, hi) = rangeForHour(hour)
        val base = baseTarget(now, lo, hi)
        val value = (base + jitter()).coerceIn(lo, hi)
        _online.value = value
        return value
    }
    fun getNow(): Int = tick()
}