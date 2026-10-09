package ru.allisighs.funpaytools

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import coil.compose.AsyncImage
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

data class Lot(
    val id: String,
    val title: String,
    val nodeId: String,
    val categoryName: String,
    val price: Double? = null,
    val currency: String? = null,
    val amount: Int? = null,
    val server: String? = null,
    val side: String? = null,
    var isActive: Boolean = true,
    val hasAutoDelivery: Boolean = false
)

data class LotFieldCondition(
    /** Имя поля формы, например "fields[type]". */
    val fieldName: String,
    /** Значения (без учёта регистра), при которых поле показывается. */
    val values: List<String>
)

data class LotField(
    val name: String,
    /** text | number | textarea | select | checkbox | hidden */
    val type: String,
    val value: String,
    val label: String = "",
    val options: List<Pair<String, String>> = emptyList(),
    val locale: String? = null,
    val conditions: List<LotFieldCondition> = emptyList(),
    val hint: String = "",
    val placeholder: String = "",
    /** У чекбокса есть hidden-двойник с тем же name — при выключении шлём пустое значение. */
    val sendEmptyWhenOff: Boolean = false
)

data class LotImage(
    val fileId: String,
    val thumbnailUrl: String,
    val fullUrl: String
)

data class BuyerPriceRow(
    val method: String,
    val ratio: Double,
    val currencySymbol: String
)

data class LotFieldsData(
    val fields: Map<String, LotField>,
    val currency: String,
    val csrfToken: String,
    val activeCookies: String,
    val imageUrls: List<String> = emptyList(),
    val images: List<LotImage> = emptyList(),
    val buyerPriceRows: List<BuyerPriceRow> = emptyList(),
    val initialSellerPrice: Double = 0.0,
    /** Количество в БД FunPay (data-offer). 0 → лот фактически неактивен. */
    val dbAmount: Int? = null
)

sealed class LotsUiState {
    object Loading : LotsUiState()
    data class Success(val lots: List<Lot>) : LotsUiState()
    data class Error(val message: String) : LotsUiState()
}

sealed class LotEditUiState {
    object Loading : LotEditUiState()
    data class Success(val fieldsData: LotFieldsData) : LotEditUiState()
    data class Error(val message: String) : LotEditUiState()
}

class InactiveLotsStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("inactive_lots_db", Context.MODE_PRIVATE)

    fun saveLot(lot: Lot) {
        val json = JSONObject().apply {
            put("id", lot.id)
            put("title", lot.title)
            put("nodeId", lot.nodeId)
            put("categoryName", lot.categoryName)
            put("price", lot.price ?: 0.0)
            put("currency", lot.currency ?: "")
            put("amount", lot.amount ?: 0)
            put("server", lot.server ?: "")
            put("side", lot.side ?: "")
            put("hasAutoDelivery", lot.hasAutoDelivery)
        }
        prefs.edit().putString(lot.id, json.toString()).apply()
    }

    fun removeLot(lotId: String) {
        prefs.edit().remove(lotId).apply()
    }

    fun getInactiveLots(): List<Lot> {
        val lots = mutableListOf<Lot>()
        prefs.all.forEach { (_, value) ->
            try {
                if (value is String) {
                    val json = JSONObject(value)
                    lots.add(Lot(
                        id = json.getString("id"),
                        title = json.getString("title"),
                        nodeId = json.getString("nodeId"),
                        categoryName = json.getString("categoryName"),
                        price = json.optDouble("price").takeIf { it != 0.0 },
                        currency = json.optString("currency").takeIf { it.isNotEmpty() },
                        amount = json.optInt("amount").takeIf { it != 0 },
                        server = json.optString("server").takeIf { it.isNotEmpty() },
                        side = json.optString("side").takeIf { it.isNotEmpty() },
                        isActive = false,
                        hasAutoDelivery = json.optBoolean("hasAutoDelivery")
                    ))
                }
            } catch (e: Exception) { }
        }
        return lots
    }
}

class LotsViewModel(
    private val repository: FunPayRepository,
    private val storage: InactiveLotsStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow<LotsUiState>(LotsUiState.Loading)
    val uiState: StateFlow<LotsUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    
    var loadedForAccountId: String? = null
        private set

    init {
        loadLots()
    }

    fun loadLots() {
        loadedForAccountId = repository.getActiveAccount()?.id
        viewModelScope.launch {
            _uiState.value = LotsUiState.Loading
            try {
                val serverLots = repository.getMyLots()
                val localInactiveLots = storage.getInactiveLots()

                val mergedLots = serverLots.toMutableList()
                localInactiveLots.forEach { localLot ->
                    if (mergedLots.none { it.id == localLot.id }) {
                        mergedLots.add(localLot)
                    }
                }

                _uiState.value = LotsUiState.Success(mergedLots.sortedByDescending { it.id.toLongOrNull() ?: 0L })
            } catch (e: Exception) {
                _uiState.value = LotsUiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSelectionMode() {
        _selectionMode.value = !_selectionMode.value
        if (!_selectionMode.value) {
            _selectedIds.value = emptySet()
        }
    }

    fun toggleSelection(lotId: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(lotId)) {
            current.remove(lotId)
        } else {
            current.add(lotId)
        }
        _selectedIds.value = current
    }

    fun selectAll(filteredLots: List<Lot>) {
        if (_selectedIds.value.size == filteredLots.size) {
            _selectedIds.value = emptySet()
        } else {
            _selectedIds.value = filteredLots.map { it.id }.toSet()
        }
    }

    fun deleteLot(lotId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteLot(lotId)
            if (result) {
                storage.removeLot(lotId)
                loadLots()
            }
            onComplete(result)
        }
    }

    fun toggleLotStatus(lotId: String, currentStatus: Boolean, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is LotsUiState.Success) {
                val lot = currentState.lots.find { it.id == lotId }
                if (lot != null) {
                    val targetActive = !currentStatus

                    val (success, error) = repository.toggleLotStatus(lotId)

                    if (success) {
                        if (targetActive) {
                            storage.removeLot(lotId)
                        } else {
                            val updatedLot = lot.copy(isActive = false)
                            storage.saveLot(updatedLot)
                        }

                        val updatedList = currentState.lots.map {
                            if (it.id == lotId) it.copy(isActive = targetActive) else it
                        }
                        _uiState.value = LotsUiState.Success(updatedList)
                    }
                    onComplete(success, error)
                }
            }
        }
    }

    fun bulkToggleStatus(enable: Boolean, onComplete: () -> Unit) {
        viewModelScope.launch {
            val ids = _selectedIds.value
            if (ids.isEmpty()) return@launch

            val jobs = ids.map { id ->
                async {
                    val (success, _) = repository.toggleLotStatus(id, forceState = enable)
                    if (success) {
                        val currentState = _uiState.value
                        if (currentState is LotsUiState.Success) {
                            val lot = currentState.lots.find { it.id == id }
                            if (lot != null) {
                                if (enable) {
                                    storage.removeLot(id)
                                } else {
                                    storage.saveLot(lot.copy(isActive = false))
                                }
                            }
                        }
                    }
                    success
                }
            }
            jobs.awaitAll()
            _selectionMode.value = false
            _selectedIds.value = emptySet()
            loadLots()
            onComplete()
        }
    }

    fun copyLot(lotId: String, targetNodeId: String? = null, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val (success, error) = repository.copyLot(lotId, targetNodeId)
            onComplete(success, error)
            if (success) loadLots()
        }
    }
}

class LotsViewModelFactory(
    private val repository: FunPayRepository,
    private val storage: InactiveLotsStorage
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LotsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LotsViewModel(repository, storage) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

suspend fun FunPayRepository.getMyLots(): List<Lot> {
    return withContext(Dispatchers.IO) {
        try {
            val (_, userId) = getCsrfAndId() ?: return@withContext emptyList()

            val response = api.getUserProfile(userId, getCookieString(), FpSession.UA)
            val html = response.body()?.string() ?: return@withContext emptyList()
            val doc = Jsoup.parse(html)

            val lots = mutableListOf<Lot>()
            doc.select(".offer").forEach { offerBlock ->
                try {
                    val categoryLink = offerBlock.select(".offer-list-title a").firstOrNull() ?: return@forEach
                    val categoryName = categoryLink.text().trim()
                    val categoryHref = categoryLink.attr("href")

                    val parsedCat = LotUrlParser.parse(categoryHref)
                    val nodeId = parsedCat?.nodeId
                        ?: parsedCat?.id?.takeIf { it.all { ch -> ch.isDigit() } }
                        ?: Regex("""/(?:lots|chips)/(\d+)""")
                            .find(categoryHref)?.groupValues?.get(1)
                        ?: return@forEach

                    offerBlock.select("a.tc-item").forEach { row ->
                        val href = row.attr("href")
                        val parsed = LotUrlParser.parse(href)

                        val id = parsed?.id
                            ?: Regex("""(?:offer=|id=)([0-9A-Za-z\-]+)""")
                                .find(href)?.groupValues?.get(1)
                            ?: return@forEach

                        val title = row.select(".tc-desc-text").text().trim().ifEmpty { "Без названия" }
                        val priceDiv = row.select(".tc-price").firstOrNull()
                        val price = priceDiv?.attr("data-s")?.toDoubleOrNull()
                        val currency = priceDiv?.select(".unit")?.text()
                        val server = row.select(".tc-server").text().trim().ifEmpty { null }
                        val side = row.select(".tc-side").text().trim().ifEmpty { null }
                        val amount = row.select(".tc-amount").text().replace(" ", "").replace(" ", "").toIntOrNull()
                        val isActive = !row.classNames().contains("warning")
                        val hasAutoDelivery = row.select(".auto-dlv-icon").isNotEmpty()

                        lots.add(Lot(id, title, nodeId, categoryName, price, currency,
                            amount, server, side, isActive, hasAutoDelivery))
                    }
                } catch (e: Exception) { }
            }
            lots
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/* ============================================================================
 *  Разбор формы редактирования лота (lots/offerEdit).
 *
 *  Логика повторяет FunPay Cardinal (Account.get_lot_fields):
 *   - берём ТОЛЬКО поля внутри form.form-offer-editor;
 *   - textarea читаем с переносами строк (wholeText), иначе описание и
 *     товары автовыдачи склеивались в одну строку;
 *   - "Наличие" (amount) — обычное редактируемое поле;
 *   - условные поля из data-fields (например "Тип реакции" появляется только
 *     при "Тип услуги = Реакции") показываются/отправляются по условию;
 *   - чекбоксы (Активное, Деактивировать после продажи, Автовыдача) — видимые.
 * ==========================================================================*/

private fun parseFieldConditions(form: org.jsoup.nodes.Element): Map<String, List<LotFieldCondition>> {
    val result = mutableMapOf<String, List<LotFieldCondition>>()
    form.select("div.lot-fields[data-fields]").forEach { box ->
        try {
            val arr = JSONArray(box.attr("data-fields"))
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val conds = obj.optJSONArray("conditions") ?: continue
                val list = mutableListOf<LotFieldCondition>()
                for (j in 0 until conds.length()) {
                    val c = conds.optJSONObject(j) ?: continue
                    val values = mutableListOf<String>()
                    val l = c.optJSONArray("list")
                    if (l != null) for (k in 0 until l.length()) values.add(l.optString(k))
                    list.add(LotFieldCondition("fields[${c.optString("id")}]", values))
                }
                if (id.isNotEmpty() && list.isNotEmpty()) result[id] = list
            }
        } catch (_: Exception) {}
    }
    return result
}

private fun localeOf(group: org.jsoup.nodes.Element?, name: String): String? = when {
    group?.attr("data-locale")?.isNotEmpty() == true -> group.attr("data-locale")
    name.endsWith("[ru]") -> "ru"
    name.endsWith("[en]") -> "en"
    else -> null
}

fun parseLotEditForm(html: String, lotId: String): LotFieldsData {
    val doc = Jsoup.parse(html)

    doc.selectFirst("p.lead")?.let { lead ->
        val txt = lead.text().trim()
        if (txt.isNotEmpty() && doc.selectFirst("form.form-offer-editor") == null) throw Exception(txt)
    }

    val form = doc.selectFirst("form.form-offer-editor")
        ?: throw Exception(
            if (html.contains("account/login") || html.contains("Необходимо авторизоваться"))
                "FunPay не авторизовал запрос. Войдите в аккаунт заново."
            else "Форма редактирования лота не найдена"
        )

    var csrfToken = form.selectFirst("input[name=csrf_token]")?.attr("value").orEmpty()
    if (csrfToken.isEmpty()) {
        try {
            val appData = doc.select("body").attr("data-app-data")
            if (appData.isNotEmpty()) csrfToken = JSONObject(appData).optString("csrf-token")
        } catch (_: Exception) {}
    }
    if (csrfToken.isEmpty()) throw Exception("CSRF токен не найден")

    val conditionsById = parseFieldConditions(form)
    val fields = LinkedHashMap<String, LotField>()
    val hiddenTwins = mutableSetOf<String>()

    for (el in form.select("input[name], textarea[name], select[name]")) {
        val name = el.attr("name")
        if (name.isEmpty() || name == "csrf_token" || name == "query" || name.startsWith("cc-option")) continue

        val group = el.parents().firstOrNull { it.hasClass("form-group") || it.hasClass("lot-field") }
        val fieldId = el.parents().firstOrNull { it.hasClass("lot-field") && it.hasAttr("data-id") }?.attr("data-id")
        val conds = fieldId?.let { conditionsById[it] }.orEmpty()
        val locale = localeOf(group, name)
        val rawLabel = (group?.selectFirst("label.control-label") ?: group?.selectFirst("label"))?.text()?.trim().orEmpty()
        val hint = group?.selectFirst(".help-block")?.text()?.trim().orEmpty()

        when (el.tagName()) {
            "textarea" -> {
                // wholeText сохраняет переносы строк (text() их схлопывает)
                val value = el.wholeText().removePrefix("\r\n").removePrefix("\n")
                fields[name] = LotField(name, "textarea", value, rawLabel.ifEmpty { name }, locale = locale,
                    conditions = conds, hint = hint)
            }
            "select" -> {
                val options = el.select("option").map { it.attr("value") to it.text().replace(' ', ' ').trim() }
                val value = el.selectFirst("option[selected]")?.attr("value").orEmpty()
                fields[name] = LotField(name, "select", value, rawLabel.ifEmpty { name }, options, locale,
                    conditions = conds, hint = hint)
            }
            else -> {
                when (val type = el.attr("type").lowercase().ifEmpty { "text" }) {
                    "hidden" -> {
                        if (fields.containsKey(name)) continue
                        if (form.select("input[type=checkbox][name=\"$name\"]").isNotEmpty()) {
                            // <input type=hidden name=X value=""> + чекбокс X — как в браузере
                            hiddenTwins.add(name)
                            continue
                        }
                        fields[name] = LotField(name, "hidden", el.attr("value"), name, locale = locale, conditions = conds)
                    }
                    "checkbox" -> {
                        val label = el.parent()?.takeIf { it.tagName() == "label" }?.text()?.trim()
                            ?: rawLabel.ifEmpty { name }
                        fields[name] = LotField(name, "checkbox", if (el.hasAttr("checked")) "on" else "",
                            label, locale = locale, conditions = conds, hint = hint,
                            sendEmptyWhenOff = name in hiddenTwins)
                    }
                    "radio" -> {
                        val existing = fields[name]
                        val opt = el.attr("value") to (el.parent()?.text()?.trim()?.ifEmpty { null } ?: el.attr("value"))
                        val checkedVal = if (el.hasAttr("checked")) el.attr("value") else null
                        fields[name] = if (existing == null) {
                            LotField(name, "select", checkedVal.orEmpty(), rawLabel.ifEmpty { name }, listOf(opt),
                                locale, conditions = conds, hint = hint)
                        } else {
                            existing.copy(
                                options = existing.options + opt,
                                value = checkedVal ?: existing.value
                            )
                        }
                    }
                    else -> {
                        val isNumber = name == "price" || name == "amount" ||
                                el.attr("inputmode") == "decimal" || type == "number"
                        val label = when (name) {
                            "amount" -> rawLabel.ifEmpty { "Наличие" }
                            "price" -> rawLabel.ifEmpty { "Цена" }
                            else -> rawLabel.ifEmpty { name }
                        }
                        fields[name] = LotField(name, if (isNumber) "number" else "text", el.attr("value"),
                            label, locale = locale, conditions = conds, hint = hint,
                            placeholder = el.attr("placeholder"))
                    }
                }
            }
        }
    }

    if (fields["offer_id"]?.value.isNullOrEmpty() && lotId != "0") {
        fields["offer_id"] = LotField("offer_id", "hidden", lotId, "offer_id")
    }

    val currency = form.selectFirst(".form-control-feedback")?.text()?.trim().orEmpty()
    val initialSellerPrice = fields["price"]?.value?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
    val buyerPriceRows = mutableListOf<BuyerPriceRow>()
    if (initialSellerPrice > 0.0) {
        form.select(".js-calc-table-body tr").forEach { row ->
            val method = row.select("th").text().trim()
            val priceText = row.select("td").text().trim()
            val currencySymbol = priceText.replace(Regex("[\\d.,\\s ]"), "").trim()
            val buyerPrice = priceText.replace(Regex("[^\\d.,]"), "").replace(",", ".").toDoubleOrNull()
            if (method.isNotEmpty() && buyerPrice != null && buyerPrice > 0.0) {
                buyerPriceRows.add(BuyerPriceRow(method, buyerPrice / initialSellerPrice, currencySymbol))
            }
        }
    }

    val imageUrls = mutableListOf<String>()
    val images = mutableListOf<LotImage>()
    form.select("li.attachments-item[data-file-id]").forEach { li ->
        val fileId = li.attr("data-file-id")
        if (fileId.isEmpty()) return@forEach
        val aThumb = li.selectFirst("a.attachments-thumb") ?: return@forEach
        val fullUrl = aThumb.attr("href")
        val thumbUrl = Regex("""url\(([^)]+)\)""").find(aThumb.attr("style"))?.groupValues?.get(1)
            ?.trim()?.trim('"', '\'') ?: fullUrl
        if (fullUrl.isNotEmpty()) {
            images.add(LotImage(fileId, thumbUrl, fullUrl))
            imageUrls.add(fullUrl)
        }
    }

    val dbAmount = try {
        JSONObject(form.attr("data-offer").ifEmpty { "{}" }).opt("amount")?.toString()?.toIntOrNull()
    } catch (_: Exception) { null }

    return LotFieldsData(
        fields = fields,
        currency = currency,
        csrfToken = csrfToken,
        activeCookies = "",
        imageUrls = imageUrls,
        images = images,
        buyerPriceRows = buyerPriceRows,
        initialSellerPrice = initialSellerPrice,
        dbAmount = dbAmount
    )
}

private suspend fun FunPayRepository.fetchOfferEditHtml(query: String): String = withContext(Dispatchers.IO) {
    getCsrfAndId() // гарантирует живую сессию + golden_seal
    val request = Request.Builder()
        .url("https://funpay.com/lots/offerEdit?$query")
        .header("Cookie", getCookieString())
        .header("User-Agent", FpSession.UA)
        .header("Referer", "https://funpay.com/")
        .build()
    repoClient.newCall(request).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (resp.code == 403 || resp.code == 401) {
            throw Exception("FunPay не авторизовал запрос (HTTP ${resp.code}). Войдите в аккаунт заново.")
        }
        if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}: не удалось загрузить форму лота")
        body
    }
}

suspend fun FunPayRepository.getLotFields(lotId: String): LotFieldsData {
    val html = fetchOfferEditHtml("offer=$lotId")
    return parseLotEditForm(html, lotId).copy(activeCookies = getCookieString())
}

suspend fun FunPayRepository.getNewLotFields(nodeId: String): LotFieldsData {
    val html = fetchOfferEditHtml("node=$nodeId")
    return parseLotEditForm(html, "0").copy(activeCookies = getCookieString())
}

/** Активно ли поле с учётом условий data-fields (например "Тип реакции" только для "Реакции"). */
fun LotField.isActiveFor(values: Map<String, String>): Boolean {
    if (conditions.isEmpty()) return true
    return conditions.all { cond ->
        val current = values[cond.fieldName].orEmpty().trim().lowercase()
        cond.values.any { it.trim().lowercase() == current }
    }
}

/**
 * Собирает тело offerSave ровно так, как его отправил бы браузер:
 * неактивные условные поля не отправляются, выключенные чекбоксы — пропускаются
 * (или отправляются пустыми, если у них есть hidden-двойник).
 */
fun buildOfferSaveForm(data: LotFieldsData, overrides: Map<String, String>): List<Pair<String, String>> {
    val values = LinkedHashMap<String, String>()
    data.fields.forEach { (name, f) -> values[name] = f.value }
    values.putAll(overrides)

    val out = mutableListOf<Pair<String, String>>()
    out.add("csrf_token" to data.csrfToken)
    val seen = mutableSetOf("csrf_token")

    for ((name, field) in data.fields) {
        if (!field.isActiveFor(values)) continue
        val v = values[name].orEmpty()
        when (field.type) {
            "checkbox" -> {
                if (field.sendEmptyWhenOff) out.add(name to "")
                if (v == "on" || v == "true" || v == "1") out.add(name to "on")
            }
            "number" -> out.add(name to v.replace(',', '.').replace(" ", "").replace(" ", ""))
            else -> out.add(name to v)
        }
        seen.add(name)
    }
    // Поля, которых не было в форме, но их явно передали (например offer_id=0 при копировании)
    for ((name, v) in overrides) {
        if (name !in seen && data.fields[name] == null) out.add(name to v)
    }
    return out
}

data class OfferSaveResult(val ok: Boolean, val error: String? = null)

suspend fun FunPayRepository.postOfferSave(form: List<Pair<String, String>>, referer: String): OfferSaveResult =
    withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            val request = Request.Builder()
                .url("https://funpay.com/lots/offerSave")
                .post(body)
                .header("Cookie", getCookieString())
                .header("User-Agent", FpSession.UA)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Origin", "https://funpay.com")
                .header("Referer", referer)
                .build()

            repoClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.code == 428) {
                    return@withContext OfferSaveResult(false,
                        "HTTP 428: FunPay не выдал golden_seal. Откройте «Аккаунты» → «Войти заново» через сайт FunPay и повторите.")
                }
                if (!response.isSuccessful) {
                    return@withContext OfferSaveResult(false, "HTTP ${response.code}: ${text.take(200)}")
                }
                val json = try { JSONObject(text) } catch (_: Exception) { null }
                    ?: return@withContext OfferSaveResult(true)

                val errorFlag = json.opt("error")
                val hasError = when (errorFlag) {
                    is Boolean -> errorFlag
                    is Number -> errorFlag.toInt() != 0
                    is String -> errorFlag.isNotBlank() && errorFlag != "0" && errorFlag != "false"
                    else -> false
                }
                val errorsArr = json.optJSONArray("errors")
                if (!hasError && (errorsArr == null || errorsArr.length() == 0)) {
                    return@withContext OfferSaveResult(true)
                }

                val parts = mutableListOf<String>()
                json.optString("msg").takeIf { it.isNotBlank() }?.let { parts.add(it) }
                if (errorsArr != null) {
                    for (i in 0 until errorsArr.length()) {
                        val pair = errorsArr.optJSONArray(i)
                        if (pair != null && pair.length() >= 2) parts.add("${pair.optString(0)}: ${pair.optString(1)}")
                        else errorsArr.optString(i).takeIf { it.isNotBlank() }?.let { parts.add(it) }
                    }
                }
                if (parts.isEmpty() && errorFlag is String && errorFlag.isNotBlank()) parts.add(errorFlag)
                OfferSaveResult(false, parts.joinToString("\n").ifEmpty { "Ошибка FunPay: ${text.take(200)}" })
            }
        } catch (e: Exception) {
            OfferSaveResult(false, e.message ?: "Неизвестная ошибка")
        }
    }

suspend fun FunPayRepository.saveLotFields(
    lotId: String,
    data: LotFieldsData,
    values: Map<String, String>
): Pair<Boolean, String?> {
    val form = buildOfferSaveForm(data, values)
    val referer = if (lotId == "0")
        "https://funpay.com/lots/offerEdit?node=${values["node_id"] ?: data.fields["node_id"]?.value.orEmpty()}"
    else "https://funpay.com/lots/offerEdit?offer=$lotId"
    val result = postOfferSave(form, referer)
    return Pair(result.ok, result.error)
}

/**
 * Старая сигнатура (используется плагинами). Поля перечитываются с FunPay,
 * а переданные значения накладываются поверх — так в форму попадают все
 * обязательные поля, даже если вызывающий код передал не всё.
 */
suspend fun FunPayRepository.saveLot(
    lotId: String,
    fieldsData: Map<String, String>,
    csrfToken: String,
    cookies: String
): Pair<Boolean, String?> {
    return try {
        val fresh = if (lotId == "0") {
            val node = fieldsData["node_id"] ?: return Pair(false, "Не указан node_id")
            getNewLotFields(node)
        } else getLotFields(lotId)
        saveLotFields(lotId, fresh, fieldsData.filterKeys { it != "csrf_token" })
    } catch (e: Exception) {
        Pair(false, e.message ?: "Неизвестная ошибка")
    }
}

suspend fun FunPayRepository.deleteLot(lotId: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val data = getLotFields(lotId)
            val form = listOf(
                "csrf_token" to data.csrfToken,
                "offer_id" to lotId,
                "deleted" to "1"
            )
            val res = postOfferSave(form, "https://funpay.com/lots/offerEdit?offer=$lotId")
            if (!res.ok) LogManager.addLog("❌ Удаление лота $lotId: ${res.error}")
            res.ok
        } catch (e: Exception) {
            LogManager.addLog("❌ Удаление лота $lotId: ${e.message}")
            false
        }
    }
}

suspend fun FunPayRepository.uploadImageToFunPay(
    imageBytes: ByteArray,
    mimeType: String,
    csrfToken: String,
    cookies: String
): String? {
    return withContext(Dispatchers.IO) {
        val processedBytes = resizeImageIfNeeded(imageBytes)

        val fileName = if (mimeType.contains("png")) "image.png" else "image.jpg"
        val requestBody = processedBytes.toRequestBody("image/jpeg".toMediaType())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("csrf_token", csrfToken)
            .addFormDataPart("file", fileName, requestBody)
            .build()

        val request = Request.Builder()
            .url("https://funpay.com/file/addOfferImage")
            .post(multipartBody)
            .header("Cookie", getCookieString())
            .header("User-Agent", FpSession.UA)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", "https://funpay.com")
            .header("Referer", "https://funpay.com/lots/offerEdit")
            .build()

        repoClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${body.take(150)}")
            val json = JSONObject(body)
            if (json.has("error") && json.optInt("error") == 1) {
                throw Exception(json.optString("msg", "Ошибка загрузки изображения"))
            }
            json.optString("fileId").takeIf { it.isNotEmpty() }
        }
    }
}

fun resizeImageIfNeeded(imageBytes: ByteArray): ByteArray {
    val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        ?: return imageBytes

    val maxSize = 4100
    val minSize = 10

    val w = bitmap.width
    val h = bitmap.height

    if (w in minSize..maxSize && h in minSize..maxSize) {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
        return out.toByteArray()
    }

    val scale = if (w > maxSize || h > maxSize) {
        minOf(maxSize.toFloat() / w, maxSize.toFloat() / h)
    } else {
        maxOf(minSize.toFloat() / w, minSize.toFloat() / h)
    }

    val newW = (w * scale).toInt().coerceIn(minSize, maxSize)
    val newH = (h * scale).toInt().coerceIn(minSize, maxSize)

    val resized = android.graphics.Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    val out = java.io.ByteArrayOutputStream()
    resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
    bitmap.recycle()
    resized.recycle()
    return out.toByteArray()
}

suspend fun FunPayRepository.toggleLotStatus(lotId: String, forceState: Boolean? = null): Pair<Boolean, String?> {
    return withContext(Dispatchers.IO) {
        try {
            val data = getLotFields(lotId)
            val currentActive = data.fields["active"]?.value == "on" && data.dbAmount != 0
            val shouldBeActive = forceState ?: !currentActive

            val overrides = mutableMapOf("active" to if (shouldBeActive) "on" else "")
            if (data.fields["node_id"]?.value.isNullOrEmpty()) {
                getMyLots().find { it.id == lotId }?.let { overrides["node_id"] = it.nodeId }
            }
            val res = saveLotFields(lotId, data, overrides)
            if (res.first) {
                LogManager.addLog("✅ Лот $lotId ${if (shouldBeActive) "активирован" else "деактивирован"}")
            } else {
                LogManager.addLog("❌ Лот $lotId: ${res.second}")
            }
            res
        } catch (e: Exception) {
            Pair(false, e.message ?: "Ошибка переключения статуса")
        }
    }
}

suspend fun FunPayRepository.copyLot(
    lotId: String,
    targetNodeId: String? = null
): Pair<Boolean, String?> {
    return withContext(Dispatchers.IO) {
        try {
            val original = getLotFields(lotId)
            val originalValues = original.fields.mapValues { it.value.value }

            val sourceNode = originalValues["node_id"]?.takeIf { it.isNotEmpty() }
                ?: getMyLots().find { it.id == lotId }?.nodeId
            val nodeId = targetNodeId ?: sourceNode
                ?: return@withContext Pair(false, "Не удалось определить категорию")

            // Пустая форма нужной категории — в ней правильный набор полей.
            val blank = getNewLotFields(nodeId)
            val values = mutableMapOf<String, String>()
            for ((name, field) in blank.fields) {
                val src = originalValues[name]
                values[name] = when {
                    name == "offer_id" -> "0"
                    name == "node_id" -> nodeId
                    name == "deleted" -> ""
                    src == null -> field.value
                    field.type == "select" && field.options.none { it.first == src } -> field.value
                    else -> src
                }
            }
            values["offer_id"] = "0"
            values["node_id"] = nodeId
            if (blank.fields.containsKey("active")) values["active"] = "on"

            saveLotFields("0", blank, values)
        } catch (e: Exception) {
            Pair(false, e.message ?: "Неизвестная ошибка копирования")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotsScreen(navController: NavController, repository: FunPayRepository, theme: AppTheme) {
    val context = LocalContext.current
    val storage = remember { InactiveLotsStorage(context) }
    val viewModel: LotsViewModel = viewModel(
        factory = LotsViewModelFactory(repository, storage)
    )
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()

    
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val currentAccountId = repository.getActiveAccount()?.id
            if (viewModel.loadedForAccountId != currentAccountId) {
                viewModel.loadLots()
            }
        }
    }

    var showDeleteDialog by remember { mutableStateOf<Lot?>(null) }
    var showCopyDialog by remember { mutableStateOf<Lot?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var filterActive by remember { mutableStateOf<Boolean?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCopying by remember { mutableStateOf(false) }
    var isBulkAction by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} выбрано") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (uiState is LotsUiState.Success) {
                                val lots = (uiState as LotsUiState.Success).lots
                                val filtered = lots.filter {
                                    (searchQuery.isEmpty() || it.title.contains(searchQuery, true) ||
                                            it.categoryName.contains(searchQuery, true)) &&
                                            (filterActive == null || it.isActive == filterActive)
                                }
                                viewModel.selectAll(filtered)
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ThemeManager.parseColor(theme.accentColor),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("Мои лоты") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                            Icon(Icons.Default.Checklist, contentDescription = null)
                        }
                        IconButton(onClick = { showFilterMenu = !showFilterMenu }) {
                            Icon(if (filterActive != null) Icons.Default.FilterAlt
                            else Icons.Default.FilterList, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ThemeManager.parseColor(theme.surfaceColor),
                        titleContentColor = ThemeManager.parseColor(theme.textPrimaryColor),
                        navigationIconContentColor = ThemeManager.parseColor(theme.accentColor),
                        actionIconContentColor = ThemeManager.parseColor(theme.accentColor)
                    )
                )
            }
        },
        bottomBar = {
            if (selectionMode) {
                BottomAppBar(
                    containerColor = ThemeManager.parseColor(theme.surfaceColor),
                    contentColor = ThemeManager.parseColor(theme.accentColor)
                ) {
                    if (isBulkAction) {
                        Box(Modifier.fillMaxWidth(), Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                            Button(
                                onClick = {
                                    isBulkAction = true
                                    viewModel.bulkToggleStatus(true) { isBulkAction = false }
                                },
                                colors = ButtonDefaults.buttonColors(ThemeManager.parseColor(theme.accentColor)),
                                enabled = selectedIds.isNotEmpty()
                            ) {
                                Text("Включить")
                            }
                            Button(
                                onClick = {
                                    isBulkAction = true
                                    viewModel.bulkToggleStatus(false) { isBulkAction = false }
                                },
                                colors = ButtonDefaults.buttonColors(Color.Gray),
                                enabled = selectedIds.isNotEmpty()
                            ) {
                                Text("Выключить")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            errorMessage?.let { error ->
                Surface(
                    Modifier.fillMaxWidth().padding(16.dp),
                    color = Color.Red.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(error, color = Color.Red, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { errorMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (filterActive == false) {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D))
                ) {
                    Text(
                        "⚠️ В этой вкладке отображаются только те отключенные лоты, которые вы отключили через это приложение на этом устройстве. Лоты, отключенные на сайте или других устройствах, здесь могут не появиться.",
                        fontSize = 12.sp,
                        color = Color(0xFFE65100),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (showFilterMenu) {
                FilterMenu(filterActive, theme, { filterActive = it }, { showFilterMenu = false })
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Поиск...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                    unfocusedBorderColor = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.3f)
                ),
                shape = RoundedCornerShape(theme.borderRadius.dp)
            )

            when (val state = uiState) {
                is LotsUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = ThemeManager.parseColor(theme.accentColor))
                    }
                }
                is LotsUiState.Error -> {
                    ErrorView(state.message, theme) { viewModel.loadLots() }
                }
                is LotsUiState.Success -> {
                    val filtered = state.lots.filter {
                        (searchQuery.isEmpty() || it.title.contains(searchQuery, true) ||
                                it.categoryName.contains(searchQuery, true)) &&
                                (filterActive == null || it.isActive == filterActive)
                    }

                    if (filtered.isEmpty()) {
                        EmptyLotsView(theme, state.lots.isNotEmpty())
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filtered, key = { it.id }) { lot ->
                                LotCard(
                                    lot = lot,
                                    theme = theme,
                                    selectionMode = selectionMode,
                                    isSelected = selectedIds.contains(lot.id),
                                    onSelect = { viewModel.toggleSelection(lot.id) },
                                    onClick = {
                                        if (selectionMode) {
                                            viewModel.toggleSelection(lot.id)
                                        } else {
                                            navController.navigate("lot_edit/${lot.id}")
                                        }
                                    },
                                    onCopy = { showCopyDialog = lot },
                                    onToggle = {
                                        viewModel.toggleLotStatus(lot.id, lot.isActive) { _, err ->
                                            if (err != null) errorMessage = err
                                        }
                                    },
                                    onDelete = { showDeleteDialog = lot }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { lot ->
        DeleteLotDialog(lot, theme,
            { viewModel.deleteLot(lot.id) { if (it) showDeleteDialog = null } },
            { showDeleteDialog = null })
    }

    showCopyDialog?.let { lot ->
        CopyLotDialog(lot, theme, isCopying,
            onCopySameCategory = {
                isCopying = true
                viewModel.copyLot(lot.id, null) { success, error ->
                    isCopying = false
                    if (success) {
                        showCopyDialog = null
                    } else {
                        errorMessage = error ?: "Ошибка копирования"
                    }
                }
            },
            onCopyToCategory = { targetNodeId ->
                isCopying = true
                viewModel.copyLot(lot.id, targetNodeId) { success, error ->
                    isCopying = false
                    if (success) {
                        showCopyDialog = null
                    } else {
                        errorMessage = error ?: "Ошибка копирования"
                    }
                }
            },
            onDismiss = { showCopyDialog = null }
        )
    }
}

@Composable
fun FilterMenu(current: Boolean?, theme: AppTheme, onChange: (Boolean?) -> Unit, onDismiss: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = ThemeManager.parseColor(theme.surfaceColor),
        shape = RoundedCornerShape(theme.borderRadius.dp),
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Фильтры", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                FilterChip(current == null, { onChange(null); onDismiss() }, { Text("Все") }, Modifier.weight(1f))
                FilterChip(current == true, { onChange(true); onDismiss() }, { Text("Активные") }, Modifier.weight(1f))
                FilterChip(current == false, { onChange(false); onDismiss() }, { Text("Неактивные") }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun LotCard(
    lot: Lot,
    theme: AppTheme,
    selectionMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val cardColor = if (isSelected) ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.1f)
    else ThemeManager.parseColor(theme.surfaceColor)

    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(if (isSelected) Modifier.border(2.dp, ThemeManager.parseColor(theme.accentColor), RoundedCornerShape(theme.borderRadius.dp)) else Modifier),
        colors = CardDefaults.cardColors(cardColor),
        shape = RoundedCornerShape(theme.borderRadius.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(16.dp)) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelect() },
                    colors = CheckboxDefaults.colors(ThemeManager.parseColor(theme.accentColor))
                )
                Spacer(Modifier.width(8.dp))
            }

            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(lot.title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = ThemeManager.parseColor(theme.textPrimaryColor),
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (lot.hasAutoDelivery) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = ThemeManager.parseColor(theme.accentColor).copy(0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("AUTO", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = ThemeManager.parseColor(theme.accentColor))
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(lot.categoryName, fontSize = 13.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                        Text("ID: ${lot.id}", fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.6f))
                    }

                    lot.price?.let { price ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$price ${lot.currency ?: ""}", fontSize = 18.sp,
                                fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.accentColor))
                            if (!lot.hasAutoDelivery) {
                                lot.amount?.let { Text("× $it", fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor)) }
                            }
                        }
                    }
                }

                if (!lot.server.isNullOrEmpty() || !lot.side.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        lot.server?.let { Chip(it, theme) }
                        lot.side?.let { Chip(it, theme) }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.2f))
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { if (!selectionMode) onToggle() }) {
                        Switch(
                            checked = lot.isActive,
                            onCheckedChange = { if (!selectionMode) onToggle() },
                            modifier = Modifier.scale(0.8f),
                            enabled = !selectionMode,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ThemeManager.parseColor(theme.accentColor),
                                checkedTrackColor = ThemeManager.parseColor(theme.accentColor).copy(0.5f)
                            )
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (lot.isActive) "Активен" else "Неактивен", fontSize = 13.sp,
                            color = ThemeManager.parseColor(theme.textSecondaryColor))
                    }

                    if (!selectionMode) {
                        Row {
                            IconButton(onCopy, Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Копировать",
                                    modifier = Modifier.size(20.dp),
                                    tint = ThemeManager.parseColor(theme.accentColor)
                                )
                            }
                            IconButton(onClick, Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = ThemeManager.parseColor(theme.accentColor)
                                )
                            }
                            IconButton(onDelete, Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.Red
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, theme: AppTheme) {
    Surface(color = ThemeManager.parseColor(theme.backgroundColor), shape = RoundedCornerShape(6.dp)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
    }
}

@Composable
fun EmptyLotsView(theme: AppTheme, hasLots: Boolean) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Inventory, null, Modifier.size(80.dp),
                ThemeManager.parseColor(theme.textSecondaryColor).copy(0.3f))
            Spacer(Modifier.height(16.dp))
            Text(if (hasLots) "Нет результатов" else "Нет лотов",
                fontSize = 18.sp, fontWeight = FontWeight.Medium,
                color = ThemeManager.parseColor(theme.textSecondaryColor))
            Spacer(Modifier.height(8.dp))
            Text(if (hasLots) "Попробуйте изменить фильтры" else "Лоты появятся после создания",
                fontSize = 14.sp, color = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.7f))
        }
    }
}

@Composable
fun ErrorView(message: String, theme: AppTheme, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Error, null, Modifier.size(80.dp), Color.Red.copy(0.5f))
            Spacer(Modifier.height(16.dp))
            Text("Ошибка загрузки", fontSize = 18.sp, fontWeight = FontWeight.Medium,
                color = ThemeManager.parseColor(theme.textSecondaryColor))
            Spacer(Modifier.height(8.dp))
            Text(message, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp),
                color = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.7f))
            Spacer(Modifier.height(24.dp))
            Button(onRetry, colors = ButtonDefaults.buttonColors(ThemeManager.parseColor(theme.accentColor))) {
                Text("Повторить")
            }
        }
    }
}

@Composable
fun DeleteLotDialog(lot: Lot, theme: AppTheme, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismiss,
        title = { Text("Удалить лот?", color = ThemeManager.parseColor(theme.textPrimaryColor)) },
        text = { Text("Вы уверены, что хотите удалить \"${lot.title}\"? Это действие нельзя отменить.",
            color = ThemeManager.parseColor(theme.textSecondaryColor)) },
        confirmButton = {
            Button(onConfirm, colors = ButtonDefaults.buttonColors(Color.Red)) { Text("Удалить") }
        },
        dismissButton = {
            TextButton(onDismiss) { Text("Отмена", color = ThemeManager.parseColor(theme.accentColor)) }
        },
        containerColor = ThemeManager.parseColor(theme.surfaceColor)
    )
}

@Composable
fun CopyLotDialog(
    lot: Lot,
    theme: AppTheme,
    isCopying: Boolean,
    onCopySameCategory: () -> Unit,
    onCopyToCategory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCategoryInput by remember { mutableStateOf(false) }
    var categoryId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = if (isCopying) ({}) else onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = ThemeManager.parseColor(theme.accentColor),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Копировать лот", color = ThemeManager.parseColor(theme.textPrimaryColor))
            }
        },
        text = {
            Column {
                Text(
                    "Лот: \"${lot.title}\"",
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.parseColor(theme.textPrimaryColor)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Категория: ${lot.categoryName}",
                    fontSize = 13.sp,
                    color = ThemeManager.parseColor(theme.textSecondaryColor)
                )

                if (showCategoryInput) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = categoryId,
                        onValueChange = { categoryId = it },
                        label = { Text("ID категории") },
                        placeholder = { Text("Например: ${lot.nodeId}") },
                        enabled = !isCopying,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                            unfocusedBorderColor = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.3f)
                        ),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Column {
                if (!showCategoryInput) {
                    Button(
                        onClick = onCopySameCategory,
                        enabled = !isCopying,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ThemeManager.parseColor(theme.accentColor)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCopying) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isCopying) "Копирование..." else "Копировать в ту же категорию")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showCategoryInput = true },
                        enabled = !isCopying,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ThemeManager.parseColor(theme.accentColor)
                        )
                    ) {
                        Text("Копировать в другую категорию")
                    }
                } else {
                    Button(
                        onClick = {
                            if (categoryId.isNotBlank()) {
                                onCopyToCategory(categoryId.trim())
                            }
                        },
                        enabled = !isCopying && categoryId.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ThemeManager.parseColor(theme.accentColor)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCopying) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isCopying) "Копирование..." else "Копировать")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showCategoryInput = false; categoryId = "" },
                        enabled = !isCopying,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ThemeManager.parseColor(theme.textSecondaryColor)
                        )
                    ) {
                        Text("Назад")
                    }
                }

                if (!isCopying) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ThemeManager.parseColor(theme.textSecondaryColor)
                        )
                    ) {
                        Text("Отмена")
                    }
                }
            }
        },
        dismissButton = null,
        containerColor = ThemeManager.parseColor(theme.surfaceColor)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotEditScreen(lotId: String, navController: NavController, repository: FunPayRepository, theme: AppTheme) {
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<LotEditUiState>(LotEditUiState.Loading) }
    var fieldValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var currentImages by remember { mutableStateOf<List<LotImage>>(emptyList()) }
    var isUploadingImage by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isUploadingImage = true
                errorMessage = null
                try {
                    val data = (uiState as? LotEditUiState.Success)?.fieldsData ?: return@launch
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = inputStream?.readBytes() ?: return@launch
                    inputStream.close()
                    val fileId = repository.uploadImageToFunPay(bytes, mimeType, data.csrfToken, data.activeCookies)
                        ?: throw Exception("Не удалось получить ID файла")
                    val newImage = LotImage(fileId, uri.toString(), uri.toString())
                    currentImages = currentImages + newImage
                    fieldValues = fieldValues + ("fields[images]" to currentImages.joinToString(",") { it.fileId })
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Ошибка загрузки фото"
                } finally {
                    isUploadingImage = false
                }
            }
        }
    }

    LaunchedEffect(lotId) {
        uiState = LotEditUiState.Loading
        try {
            val data = repository.getLotFields(lotId)
            fieldValues = data.fields.mapValues { it.value.value }
            currentImages = data.images
            uiState = LotEditUiState.Success(data)
        } catch (e: Exception) {
            uiState = LotEditUiState.Error(e.message ?: "Ошибка загрузки")
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Редактирование") },
                navigationIcon = {
                    IconButton({ navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    if (uiState is LotEditUiState.Success) {
                        IconButton({
                            errorMessage = null
                            scope.launch {
                                isSaving = true
                                val data = (uiState as LotEditUiState.Success).fieldsData

                                val missingFields = data.fields.filter { (name, field) ->
                                    field.type != "hidden" &&
                                            field.isActiveFor(fieldValues) &&
                                            field.label.contains("*") &&
                                            fieldValues[name].isNullOrBlank()
                                }

                                if (missingFields.isNotEmpty()) {
                                    errorMessage = "Заполните обязательные поля: ${missingFields.values.joinToString(", ") { it.label.replace("*", "").trim() }}"
                                    isSaving = false
                                    return@launch
                                }

                                val (ok, error) = repository.saveLotFields(lotId, data, fieldValues)
                                isSaving = false
                                if (ok) {
                                    navController.popBackStack()
                                } else {
                                    errorMessage = error ?: "Ошибка сохранения"
                                }
                            }
                        }, enabled = !isSaving) {
                            if (isSaving) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = ThemeManager.parseColor(theme.accentColor))
                            } else {
                                Icon(Icons.Default.Save, null)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ThemeManager.parseColor(theme.surfaceColor),
                    titleContentColor = ThemeManager.parseColor(theme.textPrimaryColor),
                    navigationIconContentColor = ThemeManager.parseColor(theme.accentColor),
                    actionIconContentColor = ThemeManager.parseColor(theme.accentColor)
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is LotEditUiState.Loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = ThemeManager.parseColor(theme.accentColor))
                }
                is LotEditUiState.Error -> {
                    ErrorView(state.message, theme) {
                        scope.launch {
                            uiState = LotEditUiState.Loading
                            try {
                                val data = repository.getLotFields(lotId)
                                fieldValues = data.fields.mapValues { it.value.value }
                                currentImages = data.images
                                uiState = LotEditUiState.Success(data)
                            } catch (e: Exception) {
                                uiState = LotEditUiState.Error(e.message ?: "Ошибка")
                            }
                        }
                    }
                }
                is LotEditUiState.Success -> {
                    Column(Modifier.fillMaxSize()) {
                        errorMessage?.let { error ->
                            Surface(
                                Modifier.fillMaxWidth().padding(16.dp),
                                color = Color.Red.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(error, color = Color.Red, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { errorMessage = null }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Условные поля (data-fields → conditions) показываем только когда они активны
                        val visible = state.fieldsData.fields.filter {
                            it.value.type != "hidden" && it.value.isActiveFor(fieldValues)
                        }
                        val ruFields = visible.filter { it.value.locale == "ru" }
                        val enFields = visible.filter { it.value.locale == "en" }
                        val commonFields = visible.filter { it.value.locale == null }

                        val hasMultiLang = ruFields.isNotEmpty() || enFields.isNotEmpty()

                        if (hasMultiLang) {

                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(currentImages, key = { it.fileId }) { image ->
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, ThemeManager.parseColor(theme.accentColor).copy(0.3f), RoundedCornerShape(8.dp))
                                    ) {
                                        AsyncImage(
                                            model = image.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        IconButton(
                                            onClick = {
                                                currentImages = currentImages.filter { it.fileId != image.fileId }
                                                fieldValues = fieldValues + ("fields[images]" to currentImages.joinToString(",") { it.fileId })
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(28.dp)
                                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp))
                                        ) {
                                            Icon(Icons.Default.Close, "Удалить", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                item {
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, ThemeManager.parseColor(theme.accentColor).copy(0.5f), RoundedCornerShape(8.dp))
                                            .clickable(enabled = !isUploadingImage) { imagePickerLauncher.launch("image/*") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isUploadingImage) {
                                            CircularProgressIndicator(Modifier.size(32.dp), color = ThemeManager.parseColor(theme.accentColor), strokeWidth = 2.dp)
                                        } else {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.CameraAlt, "Добавить фото", tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(32.dp))
                                                Text("Добавить", fontSize = 10.sp, color = ThemeManager.parseColor(theme.accentColor), textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                }
                            }

                            TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = ThemeManager.parseColor(theme.surfaceColor),
                                contentColor = ThemeManager.parseColor(theme.accentColor)
                            ) {
                                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Общие") })
                                if (ruFields.isNotEmpty()) {
                                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("🇷🇺 Русский") })
                                }
                                if (enFields.isNotEmpty()) {
                                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("🇬🇧 English") })
                                }
                            }

                            
                            
                            
                            
                            //
                            
                            
                            
                            
                            if (ruFields.isNotEmpty() && enFields.isNotEmpty()) {
                                var isTranslating by remember { mutableStateOf(false) }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isTranslating = true
                                            try {
                                                fun stripLocale(n: String) = n
                                                    .replace("[ru]", "[en]")
                                                    .replace("_ru", "_en")
                                                    .replace(Regex("""-ru(\b|_|$)"""), "-en$1")

                                                val updates = mutableMapOf<String, String>()
                                                for ((ruName, ruField) in ruFields) {
                                                    val ruValue = fieldValues[ruName] ?: ruField.value
                                                    if (ruValue.isBlank()) continue

                                                    
                                                    
                                                    
                                                    val enKey1 = stripLocale(ruName)
                                                    val enTarget = when {
                                                        enFields.containsKey(enKey1) -> enKey1
                                                        else -> enFields.keys.firstOrNull { enName ->
                                                            val ruCore = ruName
                                                                .replace("[ru]", "")
                                                                .replace("_ru", "")
                                                                .replace("-ru", "")
                                                            val enCore = enName
                                                                .replace("[en]", "")
                                                                .replace("_en", "")
                                                                .replace("-en", "")
                                                            ruCore == enCore && ruCore.isNotEmpty()
                                                        }
                                                    } ?: continue

                                                    val translated = repository.translateLotDescriptionRuToEn(ruValue)
                                                    if (!translated.isNullOrBlank()) {
                                                        updates[enTarget] = translated
                                                    }
                                                }

                                                if (updates.isNotEmpty()) {
                                                    fieldValues = fieldValues + updates
                                                    Toast.makeText(
                                                        context,
                                                        "Переведено: ${updates.size} полей",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    
                                                    
                                                    selectedTab = 2
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Нечего переводить (RU-поля пусты)",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "Ошибка перевода: ${e.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } finally {
                                                isTranslating = false
                                            }
                                        }
                                    },
                                    enabled = !isTranslating,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.85f)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isTranslating) {
                                        CircularProgressIndicator(
                                            Modifier.size(14.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Перевод…", color = Color.White, fontSize = 13.sp)
                                    } else {
                                        Icon(
                                            Icons.Default.Translate,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Перевести RU→EN (с эмодзи)",
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (!hasMultiLang) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(currentImages, key = { it.fileId }) { image ->
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, ThemeManager.parseColor(theme.accentColor).copy(0.3f), RoundedCornerShape(8.dp))
                                    ) {
                                        AsyncImage(
                                            model = image.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        IconButton(
                                            onClick = {
                                                currentImages = currentImages.filter { it.fileId != image.fileId }
                                                fieldValues = fieldValues + ("fields[images]" to currentImages.joinToString(",") { it.fileId })
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(28.dp)
                                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp))
                                        ) {
                                            Icon(Icons.Default.Close, "Удалить", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                item {
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, ThemeManager.parseColor(theme.accentColor).copy(0.5f), RoundedCornerShape(8.dp))
                                            .clickable(enabled = !isUploadingImage) { imagePickerLauncher.launch("image/*") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isUploadingImage) {
                                            CircularProgressIndicator(Modifier.size(32.dp), color = ThemeManager.parseColor(theme.accentColor), strokeWidth = 2.dp)
                                        } else {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.CameraAlt, "Добавить фото", tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(32.dp))
                                                Text("Добавить", fontSize = 10.sp, color = ThemeManager.parseColor(theme.accentColor), textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val fieldsToShow = when (selectedTab) {
                                1 -> ruFields
                                2 -> enFields
                                else -> commonFields
                            }

                            fieldsToShow.forEach { (name, field) ->
                                item(key = name) {
                                    FieldEditor(name, field, fieldValues[name] ?: "", theme) { newValue ->
                                        fieldValues = fieldValues + (name to newValue)
                                    }
                                }
                                if (name == "price" && state.fieldsData.buyerPriceRows.isNotEmpty()) {
                                    item(key = "buyer_prices") {
                                        BuyerPriceTable(
                                            sellerPriceText = fieldValues["price"] ?: "",
                                            rows = state.fieldsData.buyerPriceRows,
                                            theme = theme
                                        )
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

@Composable
fun BuyerPriceTable(sellerPriceText: String, rows: List<BuyerPriceRow>, theme: AppTheme) {
    val sellerPrice = sellerPriceText.replace(",", ".").toDoubleOrNull()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(theme.borderRadius.dp),
        color = ThemeManager.parseColor(theme.surfaceColor),
        border = BorderStroke(1.dp, ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                "Цена для покупателей",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ThemeManager.parseColor(theme.textSecondaryColor),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (sellerPrice == null || sellerPrice <= 0.0) {
                Text(
                    "Введите цену для расчёта",
                    fontSize = 13.sp,
                    color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.6f)
                )
            } else {
                rows.forEachIndexed { index, row ->
                    val buyerPrice = sellerPrice * row.ratio
                    val formatted = "%.2f %s".format(buyerPrice, row.currencySymbol)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            row.method,
                            fontSize = 13.sp,
                            color = ThemeManager.parseColor(theme.textPrimaryColor),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            formatted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ThemeManager.parseColor(theme.accentColor)
                        )
                    }
                    if (index < rows.lastIndex) {
                        HorizontalDivider(
                            color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.1f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldEditor(name: String, field: LotField, value: String, theme: AppTheme, onValueChange: (String) -> Unit) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
        unfocusedBorderColor = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.3f),
        focusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
        unfocusedTextColor = ThemeManager.parseColor(theme.textPrimaryColor),
        focusedLabelColor = ThemeManager.parseColor(theme.accentColor),
        unfocusedLabelColor = ThemeManager.parseColor(theme.textSecondaryColor),
        cursorColor = ThemeManager.parseColor(theme.accentColor)
    )
    val hint: (@Composable () -> Unit)? = field.hint.takeIf { it.isNotBlank() }?.let { h ->
        { Text(h, fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor)) }
    }
    when (field.type) {
        "text", "number" -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.label) },
                placeholder = field.placeholder.takeIf { it.isNotBlank() }?.let { p -> { Text(p) } },
                supportingText = hint,
                singleLine = true,
                keyboardOptions = if (field.type == "number")
                    androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                else androidx.compose.foundation.text.KeyboardOptions.Default,
                trailingIcon = when (name) {
                    "amount" -> ({ Text("шт.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 13.sp) })
                    else -> null
                },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                shape = RoundedCornerShape(theme.borderRadius.dp)
            )
        }
        "textarea" -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.label) },
                supportingText = hint,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp),
                minLines = 4,
                maxLines = 14,
                colors = fieldColors,
                shape = RoundedCornerShape(theme.borderRadius.dp)
            )
        }
        "select" -> {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                OutlinedTextField(
                    field.options.find { it.first == value }?.second ?: "",
                    {},
                    readOnly = true,
                    label = { Text(field.label) },
                    supportingText = hint,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = fieldColors,
                    shape = RoundedCornerShape(theme.borderRadius.dp)
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    field.options.forEach { (optValue, optLabel) ->
                        DropdownMenuItem({ Text(optLabel.ifBlank { "—" }) }, {
                            onValueChange(optValue)
                            expanded = false
                        })
                    }
                }
            }
        }
        "checkbox" -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    value == "on",
                    { checked -> onValueChange(if (checked) "on" else "") },
                    colors = CheckboxDefaults.colors(ThemeManager.parseColor(theme.accentColor))
                )
                Spacer(Modifier.width(8.dp))
                Text(field.label, color = ThemeManager.parseColor(theme.textPrimaryColor))
            }
        }
    }
}