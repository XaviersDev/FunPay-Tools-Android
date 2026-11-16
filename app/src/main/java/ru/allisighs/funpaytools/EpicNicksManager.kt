package ru.allisighs.funpaytools

import android.util.Base64
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject


data class EpicNickConfig(
    val c1: String,
    val c2: String,
    val c3: String? = null,
    val ang: Float = 180f,
    val scl: Float = 100f,
    val spd: Float = 3f,
    val an: List<String> = emptyList(),
    val ov: String = "none",
    val pc: String? = null
)

object EpicNicksManager {
    val nicksMap = mutableStateMapOf<String, EpicNickConfig>()
    private var isLoaded = false

    /** Разбор ключа FPT-STYLE-... (тот же формат, что в генераторе на funpay.tools/donate). */
    fun decodeKey(key: String): EpicNickConfig? {
        val k = key.trim()
        if (!k.startsWith("FPT-STYLE-")) return null
        return try {
            val decoded = String(Base64.decode(k.removePrefix("FPT-STYLE-"), Base64.DEFAULT))
            Gson().fromJson(decoded, EpicNickConfig::class.java)
        } catch (e: Exception) { null }
    }

    /** Свой только что купленный или изменённый стиль виден сразу, не дожидаясь обновления списка. */
    fun setLocal(nick: String, key: String) {
        val cfg = decodeKey(key) ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post { nicksMap[nick] = cfg }
    }

    fun reload() {
        isLoaded = false
        init()
    }

    fun init() {
        if (isLoaded) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                
                var request = Request.Builder().url("https://funpay.tools/api/donaters/").build()
                var response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    request = Request.Builder().url("https://raw.githubusercontent.com/XaviersDev/FunPayTools-Site/main/donaters.json").build()
                    response = client.newCall(request).execute()
                }

                val jsonStr = response.body?.string() ?: return@launch
                val jsonObj = JSONObject(jsonStr)

                val gson = Gson()
                val newMap = mutableMapOf<String, EpicNickConfig>()

                jsonObj.keys().forEach { nick ->
                    val key = jsonObj.getString(nick)
                    if (key.startsWith("FPT-STYLE-")) {
                        try {
                            val base64Str = key.removePrefix("FPT-STYLE-")
                            val decoded = String(Base64.decode(base64Str, Base64.DEFAULT))
                            val cfg = gson.fromJson(decoded, EpicNickConfig::class.java)
                            newMap[nick] = cfg
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                
                nicksMap.clear()
                nicksMap.putAll(newMap)
                isLoaded = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}