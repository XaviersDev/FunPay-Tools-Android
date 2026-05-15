package ru.allisighs.funpaytools

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs





data class DumperLotConfig(
    val id: String = UUID.randomUUID().toString(),
    val lotId: String = "",
    val categoryId: String = "",
    val isChip: Boolean = false,
    val enabled: Boolean = false,
    val displayName: String = "",
    val dumpMode: String = "standard", 
    val pinnedPosition: Int = 1,
    val priceMin: Double = 1.0,
    val priceMax: Double = 99999.0,
    val priceStep: Double = 1.0,
    val priceDivider: Double = 1.0,
    val priceMarkupPct: Double = 0.0,
    val useNetPrice: Boolean = false,
    val priceInForeign: Boolean = false,
    val keywords: String = "",
    val categoryFilters: Map<String, List<String>> = emptyMap(),
    val ratingMin: Int = 0,
    val positionMax: Int = 999,
    val ignoreZeroRating: Boolean = false,
    val ignoreFriends: Boolean = false,
    val onlyCompetitors: Boolean = false,
    val onlineOnly: Boolean = false,
    val autoRaise: Boolean = true,
    val aggressiveMode: Boolean = false,
    val fastPriceCheck: Boolean = false,
    val fastPriceCheckTop: Int = 10,
    val enforceMinProfit: Boolean = false,
    val updateInterval: Int = 5,
    val subscriptionType: String = "any",
    val subscriptionPeriod: String = "any",
    val matchAllMethods: Boolean = false,
    val sortOrder: Int = 0
)

data class DumperSettings(
    val enabled: Boolean = false,
    val exchangeRateSource: String = "manual",
    val exchangeRate: Double = 1.0,
    val cryptobotToken: String = "",
    val cryptobotAsset: String = "USDT",
    val friendsGlobal: List<String> = emptyList(),
    val competitorsGlobal: List<String> = emptyList(),
    val competitorsDisabled: List<String> = emptyList(),
    val lots: List<DumperLotConfig> = emptyList()
)





enum class MarketAction { LOWER, RAISE, HOLD, ERROR, FIELD_ERROR }

data class FeedEvent(
    val lotId: String,
    val lotName: String,
    val action: MarketAction,
    val fromPrice: Double,
    val toPrice: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String = ""
) {
    val timeFormatted: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

object LiveFeedManager {
    private val _events = MutableStateFlow<List<FeedEvent>>(emptyList())
    val events: StateFlow<List<FeedEvent>> = _events.asStateFlow()

    private val lastHoldPush = mutableMapOf<String, Long>()

    fun push(event: FeedEvent) {
        
        if (event.action == MarketAction.HOLD) {
            val last = lastHoldPush[event.lotId] ?: 0L
            if (System.currentTimeMillis() - last < 60_000L) return
            lastHoldPush[event.lotId] = System.currentTimeMillis()
        }

        val current = _events.value.toMutableList()
        current.add(0, event)
        if (current.size > 200) current.removeAt(current.lastIndex)
        _events.value = current
    }

    fun clear() {
        _events.value = emptyList()
    }
}





object XDDumperEngine {
    private val gson = Gson()
    private val commissionCache = mutableMapOf<String, Pair<Double, Long>>()
    private val priceCache = mutableMapOf<String, Pair<Double, Long>>()
    private val filterCache = mutableMapOf<String, Pair<Map<String, String>, Long>>()
    private const val CACHE_TTL = 5 * 60 * 1000L

    fun getSettings(context: Context): DumperSettings {
        val prefs = context.getSharedPreferences("funpay_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("dumper_settings_v2", null)
        return try {
            if (json != null) gson.fromJson(json, DumperSettings::class.java) else DumperSettings()
        } catch (e: Exception) { DumperSettings() }
    }

    fun saveSettings(context: Context, settings: DumperSettings) {
        val prefs = context.getSharedPreferences("funpay_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("dumper_settings_v2", gson.toJson(settings)).apply()
    }

    private fun sendAlertNotification(context: Context, lotId: String, title: String, message: String) {
        try {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "xd_dumper_alerts"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Ошибки XD Dumper", NotificationManager.IMPORTANCE_HIGH)
                mgr.createNotificationChannel(channel)
            }
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, channelId)
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(context)
            }
            builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(android.app.Notification.BigTextStyle().bigText(message))
                .setAutoCancel(true)
            mgr.notify(lotId.hashCode(), builder.build())
        } catch (e: Exception) { e.printStackTrace() }
    }

    suspend fun runCycle(repository: FunPayRepository, context: Context) {
        val settings = getSettings(context)
        if (!settings.enabled || settings.lots.isEmpty() || !LicenseManager.isProActive()) return

        updateExchangeRate(repository, context, settings)

        for (lotConfig in settings.lots.filter { it.enabled && it.lotId.isNotBlank() && it.categoryId.isNotBlank() }) {
            try {
                processLot(repository, context, settings, lotConfig)
            } catch (e: Exception) {
                LogManager.addLog("❌ XD Dumper Ошибка (Лот ${lotConfig.lotId}): ${e.message}")
                LiveFeedManager.push(FeedEvent(lotConfig.lotId, lotConfig.displayName, MarketAction.ERROR, 0.0, 0.0, message = e.message ?: "Error"))
            }
        }
    }

    private data class CompetitorData(
        val element: org.jsoup.nodes.Element,
        val sellerName: String,
        val lotId: String,
        val basePrice: Double,
        val position: Int
    )

    private suspend fun processLot(repo: FunPayRepository, context: Context, global: DumperSettings, config: DumperLotConfig) = withContext(Dispatchers.IO) {
        val commission = getCategoryCommission(repo, config.categoryId)
        val activeAcc = repo.getActiveAccount() ?: return@withContext
        val keywords = config.keywords.split("|").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        val autoLotFilterFields = if (config.matchAllMethods) getLotFilterFields(repo, config.lotId, config.categoryId, config.isChip) else emptyMap()

        val maxAttempts = if (config.aggressiveMode) 3 else 1
        val reqUrl = if (config.isChip) "https://funpay.com/chips/${config.categoryId}/" else "https://funpay.com/lots/${config.categoryId}/"

        for (attempt in 1..maxAttempts) {
            val req = Request.Builder().url(reqUrl).header("Cookie", repo.getCookieString()).header("User-Agent", "Mozilla/5.0").build()
            val html = repo.repoClient.newCall(req).execute().body?.string() ?: return@withContext
            val doc = Jsoup.parse(html)

            val myLotItem = doc.select("a.tc-item").firstOrNull { it.attr("href").contains("id=${config.lotId}") || it.attr("href").contains("offer=${config.lotId}") }
            val currentPriceWithComm = myLotItem?.select(".tc-price")?.attr("data-s")?.toDoubleOrNull() ?: return@withContext

            var position = 0
            val items = if (config.dumpMode == "pinned") doc.select("a.tc-item.offer-promo") else doc.select("a.tc-item")
            val rawCompetitors = mutableListOf<CompetitorData>()

            for (item in items) {
                val sellerName = item.select(".media-user-name").text().trim()
                if (sellerName == activeAcc.username) continue

                
                if (config.ignoreFriends && global.friendsGlobal.contains(sellerName)) continue
                if (config.onlyCompetitors && (!global.competitorsGlobal.contains(sellerName) || global.competitorsDisabled.contains(sellerName))) continue
                val isOnline = item.attr("data-online") == "1"
                if (config.onlineOnly && !isOnline) continue

                
                if (!isMatchingManualFilters(item, config.categoryFilters)) continue

                
                if (config.matchAllMethods && !isMatchingAutoFilters(item, autoLotFilterFields)) continue

                val desc = item.select(".tc-desc-text").text().trim().lowercase()

                
                if (config.subscriptionType != "any" || config.subscriptionPeriod != "any") {
                    if (!checkSubscriptionFilters(desc, config)) continue
                }

                
                if (keywords.isNotEmpty()) {
                    val descForKeyword = if (config.matchAllMethods && desc.contains(",")) desc.substringBeforeLast(",").trim() else desc
                    if (keywords.none { descForKeyword.contains(it) }) continue
                }

                
                val starsCount = item.select(".rating-stars i.fas").size
                if (starsCount == 0 && config.ignoreZeroRating) continue
                if (starsCount < config.ratingMin) continue

                position++
                if (position > config.positionMax) continue

                val compLotId = item.attr("href").substringAfter("id=").substringBefore("&")
                val basePrice = item.select(".tc-price").attr("data-s").toDoubleOrNull() ?: 9999999.0

                rawCompetitors.add(CompetitorData(item, sellerName, compLotId, basePrice, position))
            }

            if (rawCompetitors.isEmpty()) {
                LiveFeedManager.push(FeedEvent(config.lotId, config.displayName.ifEmpty { config.lotId }, MarketAction.HOLD, currentPriceWithComm, currentPriceWithComm, message = "Нет конкурентов"))
                return@withContext
            }

            
            val competitorsPrices = mutableListOf<Double>()
            val rate = if (config.priceInForeign) global.exchangeRate else 1.0
            val effMin = if (config.useNetPrice) config.priceMin * rate * commission else config.priceMin * rate
            val effMax = if (config.useNetPrice) config.priceMax * rate * commission else config.priceMax * rate

            val sortedRaw = rawCompetitors.sortedBy { it.basePrice }
            for ((index, comp) in sortedRaw.withIndex()) {
                val finalPrice = if (config.fastPriceCheck && index < config.fastPriceCheckTop) {
                    fetchRealLotPrice(repo, comp.lotId) ?: comp.basePrice
                } else {
                    comp.basePrice
                }

                if (finalPrice in effMin..(effMax + config.priceStep)) {
                    competitorsPrices.add(finalPrice)
                }
            }

            if (competitorsPrices.isEmpty()) {
                LiveFeedManager.push(FeedEvent(config.lotId, config.displayName.ifEmpty { config.lotId }, MarketAction.HOLD, currentPriceWithComm, currentPriceWithComm, message = "Все конкуренты вне лимитов"))
                return@withContext
            }

            val minCompPrice = competitorsPrices.minOrNull() ?: return@withContext

            var rawTarget = minCompPrice * (1.0 + config.priceMarkupPct / 100.0) - config.priceStep
            if (config.dumpMode == "pinned" && competitorsPrices.size >= config.pinnedPosition) {
                rawTarget = competitorsPrices[config.pinnedPosition - 1] * (1.0 + config.priceMarkupPct / 100.0) - 0.01
            }

            if (config.priceDivider > 1.0) rawTarget -= (rawTarget % config.priceDivider)
            var targetPrice = rawTarget.coerceIn(effMin, effMax)

            if (config.enforceMinProfit) {
                val profit = (targetPrice / commission) - (targetPrice / commission * (1 - commission))
                if (targetPrice / commission < 1.0) targetPrice = commission * 1.01
            }
            if (targetPrice < 1.0) targetPrice = 1.0

            when {
                targetPrice < currentPriceWithComm - 0.01 -> {
                    if (updateLotPrice(repo, context, config.lotId, targetPrice, commission, config.isChip)) {
                        LogManager.addLog("📉 XD Dumper: Лот ${config.lotId} снижен → ${"%.2f".format(targetPrice)}₽")
                        LiveFeedManager.push(FeedEvent(config.lotId, config.displayName.ifEmpty { config.lotId }, MarketAction.LOWER, currentPriceWithComm, targetPrice, message = "Цена снижена"))
                    }
                    if (config.aggressiveMode && attempt < maxAttempts) { delay(2000); continue }
                }
                targetPrice > currentPriceWithComm + 0.01 && config.autoRaise -> {
                    val diff = targetPrice - currentPriceWithComm
                    val percent = if (currentPriceWithComm > 0) (diff / currentPriceWithComm) * 100 else 100.0
                    if (targetPrice < minCompPrice && diff <= 100.0 && percent <= 15.0) {
                        if (updateLotPrice(repo, context, config.lotId, targetPrice, commission, config.isChip)) {
                            LogManager.addLog("📈 XD Dumper: Лот ${config.lotId} поднят → ${"%.2f".format(targetPrice)}₽")
                            LiveFeedManager.push(FeedEvent(config.lotId, config.displayName.ifEmpty { config.lotId }, MarketAction.RAISE, currentPriceWithComm, targetPrice, message = "Цена повышена"))
                        }
                    } else {
                        LiveFeedManager.push(FeedEvent(config.lotId, config.displayName.ifEmpty { config.lotId }, MarketAction.HOLD, currentPriceWithComm, currentPriceWithComm, message = "Слишком большой разрыв, холд"))
                    }
                }
                else -> {
                    LiveFeedManager.push(FeedEvent(config.lotId, config.displayName.ifEmpty { config.lotId }, MarketAction.HOLD, currentPriceWithComm, currentPriceWithComm, message = "Цена актуальна"))
                }
            }
            break
        }
    }

    private fun checkSubscriptionFilters(desc: String, config: DumperLotConfig): Boolean {
        if (config.subscriptionType != "any") {
            val kwList = when (config.subscriptionType) {
                "individual" -> listOf("individual", "индивидуальн")
                "duo" -> listOf("duo", "дуо")
                "family" -> listOf("family", "семья", "семейн")
                "student" -> listOf("student", "студент")
                else -> emptyList()
            }
            if (kwList.none { it in desc }) return false
        }
        if (config.subscriptionPeriod != "any") {
            val kwList = when (config.subscriptionPeriod) {
                "1" -> listOf("1 мес", "1 month", "one month", "1month")
                "3" -> listOf("3 мес", "3 month", "3month")
                "6" -> listOf("6 мес", "6 month", "6month")
                "12" -> listOf("12 мес", "год", "year", "annual")
                else -> emptyList()
            }
            if (kwList.none { it in desc }) return false
        }
        return true
    }

    private fun isMatchingManualFilters(item: org.jsoup.nodes.Element, categoryFilters: Map<String, List<String>>): Boolean {
        if (categoryFilters.isEmpty()) return true
        val attrs = item.attributes().associate { it.key to it.value.lowercase() }

        for ((filterId, allowedValues) in categoryFilters) {
            if (allowedValues.isEmpty()) continue
            val attrKey = if (filterId.startsWith("f-")) "data-$filterId" else "data-f-$filterId"
            val itemVal = attrs[attrKey] ?: ""
            if (allowedValues.none { it.lowercase() == itemVal }) return false
        }
        return true
    }

    private fun isMatchingAutoFilters(item: org.jsoup.nodes.Element, lotFilterFields: Map<String, String>): Boolean {
        if (lotFilterFields.isEmpty()) return true
        val itemQuantityRaw = item.attr("data-f-quantity").trim().lowercase()
        val isOtherQuantity = itemQuantityRaw == "другое количество"

        for ((key, lotValue) in lotFilterFields) {
            if (key == "f-quantity2") continue
            if (key == "f-method" || key == "f-type" || key.contains("способ")) continue

            val itemValue = if (key == "f-quantity" && isOtherQuantity) item.attr("data-f-quantity2").trim().lowercase()
            else item.attr("data-$key").trim().lowercase()

            if (itemValue.isEmpty()) continue
            val lotNum = lotValue.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull()
            val itemNum = itemValue.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull()

            if (lotNum != null && itemNum != null) {
                val tolerance = maxOf(lotNum * 0.01, 1.0)
                if (abs(itemNum - lotNum) > tolerance) return false
                continue
            }
            if (itemValue != lotValue) return false
        }
        return true
    }

    private suspend fun getLotFilterFields(repo: FunPayRepository, lotId: String, categoryId: String, isChip: Boolean): Map<String, String> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        filterCache[lotId]?.let { if (now - it.second < CACHE_TTL) return@withContext it.first }
        try {
            val reqUrl = if (isChip) "https://funpay.com/chips/$categoryId/" else "https://funpay.com/lots/$categoryId/"
            val req = Request.Builder().url(reqUrl).header("Cookie", repo.getCookieString()).build()
            val html = repo.repoClient.newCall(req).execute().body?.string() ?: return@withContext emptyMap()
            val doc = Jsoup.parse(html)
            val myItem = doc.select("a.tc-item").firstOrNull { it.attr("href").contains("id=$lotId") || it.attr("href").contains("offer=$lotId") }
                ?: return@withContext emptyMap()

            val fields = myItem.attributes().filter { it.key.startsWith("data-f-") }.associate { it.key.removePrefix("data-") to it.value.trim().lowercase() }.filter { it.value.isNotEmpty() }
            filterCache[lotId] = Pair(fields, now)
            fields
        } catch (e: Exception) { emptyMap() }
    }

    private suspend fun updateLotPrice(repo: FunPayRepository, context: Context, lotId: String, targetPrice: Double, commission: Double, isChip: Boolean): Boolean {
        var priceNoComm = targetPrice / commission
        if (priceNoComm < 1.0) priceNoComm = 1.0

        val editUrl = if(isChip) "https://funpay.com/chips/offerEdit?offer=$lotId" else "https://funpay.com/lots/offerEdit?offer=$lotId"
        val req = Request.Builder().url(editUrl).header("Cookie", repo.getCookieString()).build()
        val html = repo.repoClient.newCall(req).execute().body?.string() ?: return false
        val doc = Jsoup.parse(html)

        val formBuilder = FormBody.Builder()
        var csrf = ""

        doc.select("input[type=hidden]").forEach { el ->
            val name = el.attr("name")
            val value = el.attr("value")
            if (name.isNotEmpty()) {
                formBuilder.add(name, value)
                if (name == "csrf_token") csrf = value
            }
        }

        
        doc.select("select").forEach { el ->
            val name = el.attr("name")
            var selOpt = el.select("option[selected]").first()
            if (selOpt == null || selOpt.attr("value") == "0" || selOpt.attr("value") == "") {
                selOpt = el.select("option:not([value='0']):not([value=''])").first() 
            }
            if (name.isNotEmpty() && selOpt != null) formBuilder.add(name, selOpt.attr("value"))
        }

        doc.select("div.lot-field[data-id]").forEach { div ->
            val fid = div.attr("data-id")
            var btn = div.select("button.active, button.btn-primary").first()
            if (btn == null) {
                btn = div.select("button.btn").first() 
            }
            if (btn != null) formBuilder.add("fields[$fid]", btn.attr("value").ifEmpty { btn.text() })
        }

        formBuilder.add("price", String.format(Locale.US, "%.2f", priceNoComm))
        formBuilder.add("offer_id", lotId)
        formBuilder.add("active", "on")

        if (csrf.isEmpty()) return false

        val saveUrl = if(isChip) "https://funpay.com/chips/saveOffers" else "https://funpay.com/lots/offerSave"
        val saveReq = Request.Builder().url(saveUrl)
            .header("Cookie", repo.getCookieString())
            .header("X-Requested-With", "XMLHttpRequest")
            .post(formBuilder.build()).build()

        val resp = repo.repoClient.newCall(saveReq).execute().body?.string() ?: return false
        val json = try { JSONObject(resp) } catch (e:Exception) { JSONObject() }

        if (json.optBoolean("error", false)) {
            val errorMsg = json.optString("msg")
            LogManager.addLog("❌ Ошибка сохранения лота $lotId: $errorMsg")

            if (errorMsg.contains("Заполните", ignoreCase = true) || errorMsg.contains("required", ignoreCase = true)) {
                LogManager.addLog("⛔ Лот $lotId деактивирован. Требуется ручное вмешательство, автозаполнение не справилось.")
                sendAlertNotification(context, lotId, "XD Dumper: Ошибка Лота", "Лот $lotId отключен! FunPay требует заполнить новые обязательные поля.")

                val settings = getSettings(context)
                val newLots = settings.lots.map { if (it.lotId == lotId) it.copy(enabled = false) else it }
                saveSettings(context, settings.copy(lots = newLots))

                LiveFeedManager.push(FeedEvent(lotId, "ID: $lotId", MarketAction.FIELD_ERROR, 0.0, 0.0, message = "Отключен: $errorMsg"))
            }
            return false
        }
        return true
    }

    private suspend fun getCategoryCommission(repo: FunPayRepository, nodeId: String): Double {
        val now = System.currentTimeMillis()
        commissionCache[nodeId]?.let { if (now - it.second < CACHE_TTL) return it.first }
        try {
            val form = FormBody.Builder().add("nodeId", nodeId).add("price", "1000").build()
            val req = Request.Builder().url("https://funpay.com/lots/calc").post(form).header("Cookie", repo.getCookieString()).header("X-Requested-With", "XMLHttpRequest").build()
            val raw = repo.repoClient.newCall(req).execute().body?.string() ?: return 1.0
            val json = JSONObject(raw)
            val methods = json.optJSONArray("methods") ?: return 1.0
            var minP = Double.MAX_VALUE
            for (i in 0 until methods.length()) {
                val m = methods.getJSONObject(i)
                val p = m.optString("price").replace(" ", "").replace(",", ".").toDoubleOrNull()
                if (p != null && p > 0 && p < minP) minP = p
            }
            val coef = if (minP != Double.MAX_VALUE) minP / 1000.0 else 1.0
            commissionCache[nodeId] = Pair(coef, now)
            return coef
        } catch (e: Exception) { return 1.0 }
    }

    private suspend fun fetchRealLotPrice(repo: FunPayRepository, lotId: String): Double? {
        val now = System.currentTimeMillis()
        priceCache[lotId]?.let { if (now - it.second < CACHE_TTL) return it.first }
        try {
            
            val req = Request.Builder().url("https://funpay.com/lots/offer?id=$lotId").header("Cookie", repo.getCookieString()).build()
            val html = repo.repoClient.newCall(req).execute().body?.string() ?: return null
            val doc = Jsoup.parse(html)
            val prices = mutableListOf<Double>()
            doc.select("select[name=method] option").forEach { opt ->
                if (opt.attr("data-cy") == "rub") { 
                    val factors = opt.attr("data-factors").split(",")
                    if (factors.size >= 2) {
                        val base = factors[0].toDoubleOrNull() ?: 0.0
                        val comm = factors[1].toDoubleOrNull() ?: 0.0
                        if (base > 0 && comm > 0) prices.add(base * comm)
                    }
                }
            }
            val price = prices.minOrNull() ?: return null
            priceCache[lotId] = Pair(price, now)
            return price
        } catch (e: Exception) { return null }
    }

    suspend fun fetchFunPayRateNow(repo: FunPayRepository): Double? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://funpay.com/account/balance").header("Cookie", repo.getCookieString()).build()
            val html = repo.repoClient.newCall(req).execute().body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html)
            val withdrawBox = doc.select("div.withdraw-box").first() ?: return@withContext null
            val dataJson = withdrawBox.attr("data-data")
            if (dataJson.isEmpty()) return@withContext null

            val json = JSONObject(dataJson)
            val channels = json.optJSONObject("currencies")?.optJSONObject("rub")?.optJSONArray("channels") ?: return@withContext null

            for (i in 0 until channels.length()) {
                val ch = channels.getJSONObject(i)
                if (ch.optString("extCurrency") == "usdt_trc") {
                    val feeInfo = ch.optString("feeInfo", "")
                    val match = Regex("""курс\s*([\d\.]+)""").find(feeInfo)
                    if (match != null) {
                        return@withContext match.groupValues[1].toDoubleOrNull()
                    }
                }
            }
            null
        } catch (e: Exception) { null }
    }

    private suspend fun updateExchangeRate(repo: FunPayRepository, context: Context, settings: DumperSettings) {
        if (settings.exchangeRateSource == "manual") return
        try {
            if (settings.exchangeRateSource == "cryptobot" && settings.cryptobotToken.isNotEmpty()) {
                val req = Request.Builder().url("https://pay.crypt.bot/api/getExchangeRates").header("Crypto-Pay-API-Token", settings.cryptobotToken).build()
                val json = JSONObject(repo.repoClient.newCall(req).execute().body?.string() ?: return)
                val rates = json.optJSONArray("result") ?: return
                for (i in 0 until rates.length()) {
                    val r = rates.getJSONObject(i)
                    if (r.optString("source") == settings.cryptobotAsset && r.optString("target") == "RUB") {
                        val rate = r.optDouble("rate", 1.0)
                        saveSettings(context, settings.copy(exchangeRate = rate))
                        return
                    }
                }
            } else if (settings.exchangeRateSource == "funpay") {
                val parsedRate = fetchFunPayRateNow(repo)
                if (parsedRate != null) saveSettings(context, settings.copy(exchangeRate = parsedRate))
            }
        } catch (e: Exception) { }
    }

    suspend fun fetchCategoryInfoByLotId(repo: FunPayRepository, lotId: String): Pair<String, Boolean>? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://funpay.com/lots/offer?id=$lotId").header("Cookie", repo.getCookieString()).build()
            val resp = repo.repoClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html)
            val backLink = doc.select("a.js-back-link").attr("href")
            val isChip = backLink.contains("/chips/")
            val catId = backLink.split("/").filter { it.isNotEmpty() }.lastOrNull()
            if (catId != null && catId.all { it.isDigit() }) {
                return@withContext Pair(catId, isChip)
            }
            null
        } catch (e: Exception) { null }
    }

    suspend fun fetchCategoryFilters(repo: FunPayRepository, categoryId: String, isChip: Boolean): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val reqUrl = if (isChip) "https://funpay.com/chips/$categoryId/" else "https://funpay.com/lots/$categoryId/"
            val req = Request.Builder().url(reqUrl).header("Cookie", repo.getCookieString()).build()
            val html = repo.repoClient.newCall(req).execute().body?.string() ?: return@withContext emptyList()
            val doc = Jsoup.parse(html)

            val fieldsDiv = doc.select("div.lot-fields[data-fields]").first() ?: return@withContext emptyList()
            val fieldsJson = JSONArray(fieldsDiv.attr("data-fields"))
            val resultList = mutableListOf<Map<String, Any>>()

            for (i in 0 until fieldsJson.length()) {
                val fieldObj = fieldsJson.getJSONObject(i)
                val fId = fieldObj.optString("id")
                val fType = fieldObj.optInt("type")

                val fieldHtmlDiv = fieldsDiv.select("div.lot-field[data-id=$fId]").first() ?: continue
                val title = fieldHtmlDiv.select("div.tc-item-label").text().trim().ifEmpty { fId }

                val options = mutableListOf<Pair<String, String>>()
                if (fType == 4) {
                    fieldHtmlDiv.select("button.btn").forEach { btn ->
                        val value = btn.attr("value")
                        if (value.isNotEmpty()) options.add(value to btn.text().trim())
                    }
                } else if (fType == 5) {
                    fieldHtmlDiv.select("select option").forEach { opt ->
                        val value = opt.attr("value")
                        if (value.isNotEmpty() && value != "0") options.add(value to opt.text().trim())
                    }
                }

                if (options.isNotEmpty()) {
                    resultList.add(mapOf("id" to fId, "title" to title, "options" to options))
                }
            }
            return@withContext resultList
        } catch (e: Exception) { emptyList() }
    }
}





@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XDDumperMainScreen(navController: NavController, repository: FunPayRepository, theme: AppTheme) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(XDDumperEngine.getSettings(context)) }
    val scope = rememberCoroutineScope()
    var isFetchingRate by remember { mutableStateOf(false) }

    var newFriendName by remember { mutableStateOf("") }
    var newCompetitorName by remember { mutableStateOf("") }

    var selectedTab by remember { mutableStateOf(0) }
    var showBulkDialog by remember { mutableStateOf(false) }

    fun save(newSettings: DumperSettings) {
        settings = newSettings
        XDDumperEngine.saveSettings(context, newSettings)
    }

    if (showBulkDialog) {
        var markupInput by remember { mutableStateOf("") }
        var applyForeign by remember { mutableStateOf(false) }
        var isForeignEnabled by remember { mutableStateOf(false) }
        var targetType by remember { mutableStateOf(0) } 
        var catInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showBulkDialog = false },
            title = { Text("Массовые действия", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Выберите, к каким лотам применить изменения:", fontSize = 13.sp)
                    Row {
                        RadioButton(selected = targetType == 0, onClick = { targetType = 0 })
                        Text("Ко всем добавленным лотам", modifier = Modifier.align(Alignment.CenterVertically))
                    }
                    Row {
                        RadioButton(selected = targetType == 1, onClick = { targetType = 1 })
                        Text("К лотам категории:", modifier = Modifier.align(Alignment.CenterVertically))
                    }
                    if (targetType == 1) {
                        OutlinedTextField(
                            value = catInput,
                            onValueChange = { catInput = it },
                            label = { Text("ID Категории") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    Divider(Modifier.padding(vertical = 4.dp))

                    OutlinedTextField(
                        value = markupInput,
                        onValueChange = { markupInput = it },
                        label = { Text("Новая наценка (%)") },
                        placeholder = { Text("Оставить без изменений") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = applyForeign, onCheckedChange = { applyForeign = it })
                        Text("Изменить 'Цены в USDT'?", fontSize = 13.sp)
                    }
                    if (applyForeign) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 32.dp)) {
                            Switch(checked = isForeignEnabled, onCheckedChange = { isForeignEnabled = it })
                            Text(if (isForeignEnabled) "Включить везде" else "Выключить везде", fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newLots = settings.lots.map { lot ->
                        if (targetType == 0 || lot.categoryId == catInput) {
                            lot.copy(
                                priceMarkupPct = markupInput.toDoubleOrNull() ?: lot.priceMarkupPct,
                                priceInForeign = if (applyForeign) isForeignEnabled else lot.priceInForeign
                            )
                        } else lot
                    }
                    save(settings.copy(lots = newLots))
                    showBulkDialog = false
                    Toast.makeText(context, "Массовое обновление завершено!", Toast.LENGTH_SHORT).show()
                }) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDialog = false }) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        containerColor = ThemeManager.parseColor(theme.backgroundColor),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("XD Dumper PRO", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ThemeManager.parseColor(theme.textPrimaryColor))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
                )
                TabRow(selectedTabIndex = selectedTab, containerColor = ThemeManager.parseColor(theme.surfaceColor), contentColor = ThemeManager.parseColor(theme.accentColor)) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Настройки") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Живая лента") })
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { navController.navigate("xd_dumper_edit/new") },
                    containerColor = ThemeManager.parseColor(theme.accentColor)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White)
                }
            } else {
                FloatingActionButton(
                    onClick = { LiveFeedManager.clear() },
                    containerColor = ThemeManager.parseColor(theme.surfaceColor)
                ) {
                    Icon(Icons.Default.Delete, "Очистить ленту", tint = Color.White)
                }
            }
        }
    ) { padding ->

        if (selectedTab == 1) {
            val feedEvents by LiveFeedManager.events.collectAsState()

            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Spacer(Modifier.height(8.dp)) }

                if (feedEvents.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Лента пуста. Ожидайте операций демпинга...", color = ThemeManager.parseColor(theme.textSecondaryColor), textAlign = TextAlign.Center)
                        }
                    }
                }

                items(feedEvents) { ev ->
                    val color = when(ev.action) {
                        MarketAction.LOWER -> Color(0xFF22C55E) 
                        MarketAction.RAISE -> Color(0xFF3B82F6) 
                        MarketAction.HOLD -> Color.Gray
                        MarketAction.ERROR, MarketAction.FIELD_ERROR -> Color(0xFFEF4444) 
                    }
                    val icon = when(ev.action) {
                        MarketAction.LOWER -> Icons.Default.TrendingDown
                        MarketAction.RAISE -> Icons.Default.TrendingUp
                        MarketAction.HOLD -> Icons.Default.Remove
                        MarketAction.ERROR, MarketAction.FIELD_ERROR -> Icons.Default.Warning
                    }

                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(ev.lotName.take(20), fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 14.sp)
                                    Text(ev.timeFormatted, color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp)
                                }
                                Text(ev.message, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                if (ev.action == MarketAction.LOWER || ev.action == MarketAction.RAISE) {
                                    Text("${ev.fromPrice} ₽ → ${ev.toPrice} ₽", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).background(ThemeManager.parseColor(theme.accentColor).copy(0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TrendingDown, null, tint = ThemeManager.parseColor(theme.accentColor))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Главный рубильник", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 16.sp)
                            Text("Включает или выключает демпер полностью для всех лотов.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                        }
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { save(settings.copy(enabled = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = ThemeManager.parseColor(theme.accentColor), checkedTrackColor = ThemeManager.parseColor(theme.accentColor).copy(0.5f))
                        )
                    }
                }
            }

            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CurrencyExchange, null, tint = ThemeManager.parseColor(theme.accentColor))
                            Spacer(Modifier.width(8.dp))
                            Text("Курс валют", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Используется, если у лота включена опция «Цены в USDT». Бот будет умножать твои минимальные рамки на этот курс.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp, lineHeight = 14.sp)
                        Spacer(Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.exchangeRateSource == "manual",
                                onClick = { save(settings.copy(exchangeRateSource = "manual")) },
                                label = { Text("Вручную") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemeManager.parseColor(theme.accentColor))
                            )
                            FilterChip(
                                selected = settings.exchangeRateSource == "funpay",
                                onClick = { save(settings.copy(exchangeRateSource = "funpay")) },
                                label = { Text("Сайт FunPay") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemeManager.parseColor(theme.accentColor))
                            )
                            FilterChip(
                                selected = settings.exchangeRateSource == "cryptobot",
                                onClick = { save(settings.copy(exchangeRateSource = "cryptobot")) },
                                label = { Text("CryptoBot API") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemeManager.parseColor(theme.accentColor))
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        when (settings.exchangeRateSource) {
                            "manual" -> {
                                OutlinedTextField(
                                    value = settings.exchangeRate.toString(),
                                    onValueChange = { save(settings.copy(exchangeRate = it.toDoubleOrNull() ?: 1.0)) },
                                    label = { Text("Ваш курс (1 USDT = ? ₽)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor))
                                )
                            }
                            "funpay" -> {
                                Text("Бот сам заходит в раздел Финансы и берет актуальный внутренний курс вывода USDT TRC-20 с ФанПея.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        if (isFetchingRate) return@OutlinedButton
                                        scope.launch {
                                            isFetchingRate = true
                                            val parsedRate = XDDumperEngine.fetchFunPayRateNow(repository)
                                            if (parsedRate != null) {
                                                save(settings.copy(exchangeRate = parsedRate))
                                                Toast.makeText(context, "Курс обновлен: $parsedRate", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Не удалось получить курс", Toast.LENGTH_SHORT).show()
                                            }
                                            isFetchingRate = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isFetchingRate) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ThemeManager.parseColor(theme.accentColor), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp), tint = ThemeManager.parseColor(theme.accentColor))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Обновить курс сейчас", color = ThemeManager.parseColor(theme.textPrimaryColor))
                                    }
                                }
                            }
                            "cryptobot" -> {
                                OutlinedTextField(
                                    value = settings.cryptobotToken,
                                    onValueChange = { save(settings.copy(cryptobotToken = it)) },
                                    label = { Text("CryptoBot API Token") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor))
                                )
                            }
                        }

                        if (settings.exchangeRateSource != "manual") {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Текущий спарсенный курс: ", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 13.sp)
                                Text("${settings.exchangeRate} ₽", color = ThemeManager.parseColor(theme.accentColor), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, null, tint = Color(0xFFE91E63))
                            Spacer(Modifier.width(8.dp))
                            Text("Друзья (Игнор лист)", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
                        }
                        Text("Люди из этого списка не будут демпиться, если в настройках лота включен 'Игнор друзей'.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newFriendName,
                                onValueChange = { newFriendName = it },
                                label = { Text("Никнейм на FunPay") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor))
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (newFriendName.isNotBlank() && !settings.friendsGlobal.contains(newFriendName)) {
                                        val list = settings.friendsGlobal.toMutableList()
                                        list.add(newFriendName.trim())
                                        save(settings.copy(friendsGlobal = list))
                                        newFriendName = ""
                                    }
                                },
                                modifier = Modifier.background(ThemeManager.parseColor(theme.accentColor), CircleShape)
                            ) { Icon(Icons.Default.Add, null, tint = Color.White) }
                        }

                        if (settings.friendsGlobal.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                settings.friendsGlobal.forEach { friend ->
                                    Row(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(0.2f), RoundedCornerShape(8.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(friend, color = ThemeManager.parseColor(theme.textPrimaryColor), modifier = Modifier.weight(1f), fontSize = 13.sp)
                                        IconButton(onClick = {
                                            val list = settings.friendsGlobal.toMutableList()
                                            list.remove(friend)
                                            save(settings.copy(friendsGlobal = list))
                                        }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GpsFixed, null, tint = Color(0xFF2196F3))
                            Spacer(Modifier.width(8.dp))
                            Text("Конкуренты (Цели)", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
                        }
                        Text("Если в лоте включено 'Только конкуренты', бот будет демпить ТОЛЬКО людей из этого списка.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newCompetitorName,
                                onValueChange = { newCompetitorName = it },
                                label = { Text("Никнейм конкурента") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor))
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (newCompetitorName.isNotBlank() && !settings.competitorsGlobal.contains(newCompetitorName)) {
                                        val list = settings.competitorsGlobal.toMutableList()
                                        list.add(newCompetitorName.trim())
                                        save(settings.copy(competitorsGlobal = list))
                                        newCompetitorName = ""
                                    }
                                },
                                modifier = Modifier.background(ThemeManager.parseColor(theme.accentColor), CircleShape)
                            ) { Icon(Icons.Default.Add, null, tint = Color.White) }
                        }

                        if (settings.competitorsGlobal.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                settings.competitorsGlobal.forEach { comp ->
                                    val isDisabled = settings.competitorsDisabled.contains(comp)
                                    Row(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(0.2f), RoundedCornerShape(8.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(comp, color = if (isDisabled) ThemeManager.parseColor(theme.textSecondaryColor) else ThemeManager.parseColor(theme.textPrimaryColor), modifier = Modifier.weight(1f), fontSize = 13.sp, textDecoration = if (isDisabled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)

                                        IconButton(onClick = {
                                            val list = settings.competitorsDisabled.toMutableList()
                                            if (isDisabled) list.remove(comp) else list.add(comp)
                                            save(settings.copy(competitorsDisabled = list))
                                        }, modifier = Modifier.size(24.dp)) {
                                            Icon(if (isDisabled) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = if (isDisabled) Color.Green else Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(onClick = {
                                            val cList = settings.competitorsGlobal.toMutableList()
                                            val dList = settings.competitorsDisabled.toMutableList()
                                            cList.remove(comp)
                                            dList.remove(comp)
                                            save(settings.copy(competitorsGlobal = cList, competitorsDisabled = dList))
                                        }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Настроенные лоты (${settings.lots.size})", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textSecondaryColor))
                    TextButton(onClick = { showBulkDialog = true }) {
                        Text("Масс. действия", fontSize = 12.sp, color = ThemeManager.parseColor(theme.accentColor))
                    }
                }
            }

            if (settings.lots.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Нет лотов. Нажми кнопку + чтобы добавить первый.", color = ThemeManager.parseColor(theme.textSecondaryColor), textAlign = TextAlign.Center)
                    }
                }
            }

            items(settings.lots) { lot ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate("xd_dumper_edit/${lot.id}") },
                    colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (lot.enabled) ThemeManager.parseColor(theme.accentColor).copy(0.5f) else Color.Transparent)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(lot.displayName.ifEmpty { "Лот ID: ${lot.lotId}" }, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Мин: ${lot.priceMin} | Шаг: ${lot.priceStep}", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                            Text(if (lot.dumpMode == "pinned") "📌 Демп закрепа" else "📉 Быть самым дешевым", color = ThemeManager.parseColor(theme.accentColor), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = lot.enabled,
                            onCheckedChange = { isEnabled ->
                                val newLots = settings.lots.map { if (it.id == lot.id) it.copy(enabled = isEnabled) else it }
                                save(settings.copy(lots = newLots))
                            },
                            modifier = Modifier.scale(0.8f),
                            colors = SwitchDefaults.colors(checkedThumbColor = ThemeManager.parseColor(theme.accentColor))
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XDDumperLotEditScreen(lotId: String, navController: NavController, repository: FunPayRepository, theme: AppTheme) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(XDDumperEngine.getSettings(context)) }
    var lotConfig by remember {
        mutableStateOf(
            if (lotId == "new") DumperLotConfig()
            else settings.lots.find { it.id == lotId } ?: DumperLotConfig()
        )
    }

    var isFetchingCategory by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    fun save() {
        if (lotConfig.lotId.isBlank()) {
            Toast.makeText(context, "Введите ID лота!", Toast.LENGTH_SHORT).show()
            return
        }
        if (lotConfig.categoryId.isBlank()) {
            Toast.makeText(context, "Подождите загрузки категории лота...", Toast.LENGTH_SHORT).show()
            return
        }

        val newList = settings.lots.filter { it.id != lotConfig.id }.toMutableList()
        newList.add(lotConfig)
        settings = settings.copy(lots = newList)
        XDDumperEngine.saveSettings(context, settings)
        Toast.makeText(context, "Лот сохранен", Toast.LENGTH_SHORT).show()
        navController.popBackStack()
    }

    fun delete() {
        val newList = settings.lots.filter { it.id != lotConfig.id }
        settings = settings.copy(lots = newList)
        XDDumperEngine.saveSettings(context, settings)
        Toast.makeText(context, "Лот удален", Toast.LENGTH_SHORT).show()
        navController.popBackStack()
    }

    @Composable
    fun SettingSectionTitle(title: String, desc: String) {
        Spacer(Modifier.height(16.dp))
        Text(title, color = ThemeManager.parseColor(theme.accentColor), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(desc, color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
    }

    @Composable
    fun ToggleSetting(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(desc, color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp, lineHeight = 14.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = ThemeManager.parseColor(theme.accentColor))
            )
        }
    }

    if (showFilterDialog) {
        CategoryFiltersDialog(
            categoryId = lotConfig.categoryId,
            isChip = lotConfig.isChip,
            currentFilters = lotConfig.categoryFilters,
            repository = repository,
            theme = theme,
            onDismiss = { showFilterDialog = false },
            onSave = { newFilters ->
                lotConfig = lotConfig.copy(categoryFilters = newFilters)
                showFilterDialog = false
            }
        )
    }

    Scaffold(
        containerColor = ThemeManager.parseColor(theme.backgroundColor),
        topBar = {
            TopAppBar(
                title = { Text(if (lotId == "new") "Новый лот" else "Настройка лота", color = ThemeManager.parseColor(theme.textPrimaryColor)) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ThemeManager.parseColor(theme.textPrimaryColor)) } },
                actions = {
                    if (lotId != "new") {
                        IconButton(onClick = {
                            val json = Gson().toJson(lotConfig)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Lot Config", json))
                            Toast.makeText(context, "Настройки скопированы!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, "Копировать", tint = ThemeManager.parseColor(theme.textPrimaryColor))
                        }
                        IconButton(onClick = ::delete) { Icon(Icons.Default.Delete, "Удалить", tint = Color(0xFFEF5350)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ThemeManager.parseColor(theme.surfaceColor))
            )
        },
        bottomBar = {
            Surface(color = ThemeManager.parseColor(theme.surfaceColor), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = ::save,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                ) { Text("Сохранить лот", fontWeight = FontWeight.Bold) }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {

            
            SettingSectionTitle("ОСНОВНЫЕ ДАННЫЕ", "Привязка бота к конкретному товару на FunPay.")
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = lotConfig.lotId,
                        onValueChange = { newId ->
                            lotConfig = lotConfig.copy(lotId = newId)
                            if (newId.length >= 6) {
                                isFetchingCategory = true
                                scope.launch {
                                    val catInfo = XDDumperEngine.fetchCategoryInfoByLotId(repository, newId)
                                    if (catInfo != null) {
                                        lotConfig = lotConfig.copy(categoryId = catInfo.first, isChip = catInfo.second)
                                    }
                                    isFetchingCategory = false
                                }
                            }
                        },
                        label = { Text("ID Лота (цифры из ссылки)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor))
                    )
                    Spacer(Modifier.height(8.dp))
                    if (isFetchingCategory) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ThemeManager.parseColor(theme.accentColor), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Ищем категорию лота...", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                        }
                    } else if (lotConfig.categoryId.isNotEmpty()) {
                        Text("✅ Категория найдена (ID: ${lotConfig.categoryId}, Тип: ${if(lotConfig.isChip) "Валюта" else "Предмет"})", color = Color(0xFF4CAF50), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lotConfig.displayName,
                        onValueChange = { lotConfig = lotConfig.copy(displayName = it) },
                        label = { Text("Понятное название (для себя)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor))
                    )
                }
            }

            
            SettingSectionTitle("РЕЖИМ РАБОТЫ", "С кем мы будем воевать за место под солнцем.")
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = lotConfig.dumpMode == "standard",
                            onClick = { lotConfig = lotConfig.copy(dumpMode = "standard") },
                            label = { Text("Обычно") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemeManager.parseColor(theme.accentColor))
                        )
                        FilterChip(
                            selected = lotConfig.dumpMode == "pinned",
                            onClick = { lotConfig = lotConfig.copy(dumpMode = "pinned") },
                            label = { Text("Закреп") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemeManager.parseColor(theme.accentColor))
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (lotConfig.dumpMode == "standard") {
                        Text("Бот автоматически находит самого дешевого конкурента и ставит цену на шаг ниже. Цель - быть самым дешевым во всей категории.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                    } else {
                        Text("Бот игнорирует обычных продавцов и воюет только с теми, кто в закрепе.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                    }

                    AnimatedVisibility (visible = lotConfig.dumpMode == "pinned", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = lotConfig.pinnedPosition.toString(),
                                onValueChange = { lotConfig = lotConfig.copy(pinnedPosition = it.toIntOrNull() ?: 1) },
                                label = { Text("Держаться ниже позиции N (1 = под первым)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor))
                            )
                        }
                    }
                }
            }

            
            SettingSectionTitle("ГРАНИЦЫ И СКИДКИ", "Настройки цен, шага и защиты от слива в ноль.")
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = lotConfig.priceMin.toString(), onValueChange = { lotConfig = lotConfig.copy(priceMin = it.toDoubleOrNull() ?: 1.0) }, label = { Text("Минималка") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor)))
                        OutlinedTextField(value = lotConfig.priceMax.toString(), onValueChange = { lotConfig = lotConfig.copy(priceMax = it.toDoubleOrNull() ?: 99999.0) }, label = { Text("Максималка") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor)))
                    }
                    Text("Бот НИКОГДА не опустит цену ниже минималки и не поднимет выше максималки.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = lotConfig.priceStep.toString(), onValueChange = { lotConfig = lotConfig.copy(priceStep = it.toDoubleOrNull() ?: 1.0) }, label = { Text("Шаг демпинга") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor)))
                        OutlinedTextField(value = lotConfig.priceDivider.toString(), onValueChange = { lotConfig = lotConfig.copy(priceDivider = it.toDoubleOrNull() ?: 0.0) }, label = { Text("Округление") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor)))
                    }
                    Text("Шаг: на сколько рублей перебивать конкурента. Округление: цена будет кратна этому числу (например 10 -> 140, 150).", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

                    OutlinedTextField(value = lotConfig.priceMarkupPct.toString(), onValueChange = { lotConfig = lotConfig.copy(priceMarkupPct = it.toDoubleOrNull() ?: 0.0) }, label = { Text("Наценка от конкурента (%)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor)))
                    Text("Если вписать 10, бот будет держать твою цену на 10% ДОРОЖЕ самого дешевого конкурента.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.2f))

                    ToggleSetting("NET-цена (Чистая прибыль)", "Мин/Макс трактуются как сумма ПОСЛЕ комиссии FunPay. То есть ты указываешь, сколько хочешь получить чистыми на баланс.", lotConfig.useNetPrice) { lotConfig = lotConfig.copy(useNetPrice = it) }
                    ToggleSetting("Защита от убытка (1₽ профита)", "Запрещает боту опускать цену, если после комиссии ФанПея чистая прибыль составит менее 1 рубля.", lotConfig.enforceMinProfit) { lotConfig = lotConfig.copy(enforceMinProfit = it) }
                    ToggleSetting("Цены в иностр. валюте (USDT)", "Бот будет умножать Мин/Макс на курс валюты из общих настроек (например, 10$ -> 950₽).", lotConfig.priceInForeign) { lotConfig = lotConfig.copy(priceInForeign = it) }
                }
            }

            
            SettingSectionTitle("ДИНАМИЧЕСКИЕ ФИЛЬТРЫ", "Выбор Сервера, ОС и прочих галочек с сайта.")
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Можно выбрать несколько значений. Конкурент пройдет, если у него совпадает хотя бы одно.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))

                    val activeFiltersCount = lotConfig.categoryFilters.values.sumOf { it.size }
                    Button(
                        onClick = {
                            if (lotConfig.categoryId.isBlank()) {
                                Toast.makeText(context, "Сначала введите ID лота и дождитесь определения категории!", Toast.LENGTH_SHORT).show()
                            } else {
                                showFilterDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor).copy(0.2f), contentColor = ThemeManager.parseColor(theme.accentColor))
                    ) {
                        Icon(Icons.Default.FilterAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Настроить фильтры (${activeFiltersCount})")
                    }
                }
            }

            
            SettingSectionTitle("ПОДПИСКИ (ДЛЯ SPOTIFY/TG)", "Для обычных товаров оставьте 'Любой'.")
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Тип подписки", fontSize = 13.sp, color = ThemeManager.parseColor(theme.textPrimaryColor))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("any" to "Любой", "individual" to "Индивид.", "duo" to "Дуо", "family" to "Семья", "student" to "Студент").forEach { (k, v) ->
                            FilterChip(selected = lotConfig.subscriptionType == k, onClick = { lotConfig = lotConfig.copy(subscriptionType = k) }, label = { Text(v) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemeManager.parseColor(theme.accentColor)))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Срок (месяцы)", fontSize = 13.sp, color = ThemeManager.parseColor(theme.textPrimaryColor))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("any" to "Любой", "1" to "1м", "3" to "3м", "6" to "6м", "12" to "Год").forEach { (k, v) ->
                            FilterChip(selected = lotConfig.subscriptionPeriod == k, onClick = { lotConfig = lotConfig.copy(subscriptionPeriod = k) }, label = { Text(v) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemeManager.parseColor(theme.accentColor)))
                        }
                    }
                    Text("Бот будет искать в тексте конкурентов слова '1 мес', 'год', 'family' и отсеивать лишних.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }

            
            SettingSectionTitle("ФИЛЬТРАЦИЯ И СЛЕПОТА", "Кого из конкурентов не замечать.")
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(value = lotConfig.keywords, onValueChange = { lotConfig = lotConfig.copy(keywords = it) }, label = { Text("Слова в описании (через |)") }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Discord | Nitro | Full")}, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor)))
                    Text("Бот будет демпить ТОЛЬКО тех конкурентов, у которых в описании есть хоть одно из этих слов. Пусто = бьем всех.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = lotConfig.ratingMin.toString(), onValueChange = { lotConfig = lotConfig.copy(ratingMin = it.toIntOrNull() ?: 0) }, label = { Text("Мин. звёзд (0-5)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor)))
                        OutlinedTextField(value = lotConfig.positionMax.toString(), onValueChange = { lotConfig = lotConfig.copy(positionMax = it.toIntOrNull() ?: 999) }, label = { Text("Игнор ниже N места") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor)))
                    }
                    Text("Можно игнорировать конкурентов с плохим рейтингом или тех, кто висит в самом низу списка.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.2f))

                    ToggleSetting("Игнор нулевого рейтинга", "Не демпить новичков без отзывов.", lotConfig.ignoreZeroRating) { lotConfig = lotConfig.copy(ignoreZeroRating = it) }
                    ToggleSetting("Игнор друзей", "Не демпить людей из списка 'Друзья' (в глобальных настройках).", lotConfig.ignoreFriends) { lotConfig = lotConfig.copy(ignoreFriends = it) }
                    ToggleSetting("Только конкуренты", "Демпить ИСКЛЮЧИТЕЛЬНО тех, кто добавлен в список 'Конкуренты'.", lotConfig.onlyCompetitors) { lotConfig = lotConfig.copy(onlyCompetitors = it) }
                    ToggleSetting("Только онлайн продавцы", "Игнорировать тех, кто спит (горит серая точка).", lotConfig.onlineOnly) { lotConfig = lotConfig.copy(onlineOnly = it) }
                    ToggleSetting("Строгое совпадение полей", "Искать конкурентов ТОЛЬКО с точно такими же галочками (Сервер, Платформа и т.д.), как в твоем лоте. Авто-режим.", lotConfig.matchAllMethods) { lotConfig = lotConfig.copy(matchAllMethods = it) }
                }
            }

            
            SettingSectionTitle("АГРЕССИЯ И ОБХОДЫ", "Настройки скорости и точности парсинга.")
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    ToggleSetting("Авто-повышение", "Если конкурент ушел или поднял цену, бот аккуратно повысит твою цену вслед за ним (чтобы ты больше заработал).", lotConfig.autoRaise) { lotConfig = lotConfig.copy(autoRaise = it) }
                    ToggleSetting("Агрессивный режим (x3 попытки)", "Бот делает 3 проверки рынка подряд за один проход. Мгновенно давит тех, кто юзает других ботов.", lotConfig.aggressiveMode) { lotConfig = lotConfig.copy(aggressiveMode = it) }

                    ToggleSetting("Fast Price Check (FPC)", "Переходит напрямую на страницу конкурента и считает точную цену с комиссией. Очень точно, но жрет батарею.", lotConfig.fastPriceCheck) { lotConfig = lotConfig.copy(fastPriceCheck = it) }

                    AnimatedVisibility (visible = lotConfig.fastPriceCheck, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = lotConfig.fastPriceCheckTop.toString(), onValueChange = { lotConfig = lotConfig.copy(fastPriceCheckTop = it.toIntOrNull() ?: 5) }, label = { Text("Считать точную цену у Топ-N человек") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ThemeManager.parseColor(theme.accentColor), focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor), unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor)))
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Интервал обновления: ${lotConfig.updateInterval} сек", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Slider(value = lotConfig.updateInterval.toFloat(), onValueChange = { lotConfig = lotConfig.copy(updateInterval = it.toInt().coerceAtLeast(1)) }, valueRange = 1f..300f, steps = 298, colors = SliderDefaults.colors(thumbColor = ThemeManager.parseColor(theme.accentColor), activeTrackColor = ThemeManager.parseColor(theme.accentColor)))
                    Text("Меньше интервал = быстрее демпинг, но выше шанс бана по IP от FunPay.", color = Color(0xFFFF9800), fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}


@Composable
fun CategoryFiltersDialog(
    categoryId: String,
    isChip: Boolean,
    currentFilters: Map<String, List<String>>,
    repository: FunPayRepository,
    theme: AppTheme,
    onDismiss: () -> Unit,
    onSave: (Map<String, List<String>>) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var availableFields by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var selectedFilters by remember { mutableStateOf(currentFilters.toMutableMap()) }

    LaunchedEffect(categoryId) {
        availableFields = XDDumperEngine.fetchCategoryFilters(repository, categoryId, isChip)
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Фильтры категории", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))

                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ThemeManager.parseColor(theme.accentColor))
                    }
                } else if (availableFields.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("В этой категории нет фильтров.", color = ThemeManager.parseColor(theme.textSecondaryColor))
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(availableFields) { field ->
                            val fId = field["id"] as String
                            val fTitle = field["title"] as String
                            @Suppress("UNCHECKED_CAST")
                            val options = field["options"] as List<Pair<String, String>>

                            Text(fTitle, color = ThemeManager.parseColor(theme.accentColor), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                options.forEach { (optValue, optLabel) ->
                                    val isSelected = selectedFilters[fId]?.contains(optValue) == true
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val currentList = selectedFilters[fId]?.toMutableList() ?: mutableListOf()
                                            if (isSelected) currentList.remove(optValue) else currentList.add(optValue)
                                            if (currentList.isEmpty()) selectedFilters.remove(fId) else selectedFilters[fId] = currentList
                                            
                                            selectedFilters = selectedFilters.toMutableMap()
                                        },
                                        label = { Text(optLabel) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemeManager.parseColor(theme.accentColor))
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.backgroundColor))) { Text("Отмена") }
                    Button(onClick = { onSave(selectedFilters) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))) { Text("Сохранить") }
                }
            }
        }
    }
}