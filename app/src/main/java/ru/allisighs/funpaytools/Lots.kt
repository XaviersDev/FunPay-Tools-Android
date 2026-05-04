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

data class LotField(
    val name: String,
    val type: String,
    val value: String,
    val label: String = "",
    val options: List<Pair<String, String>> = emptyList(),
    val locale: String? = null
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
    val initialSellerPrice: Double = 0.0
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

    // ID аккаунта, для которого загружены лоты
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
            val cookie = "golden_key=${getGoldenKey()}; PHPSESSID=${getPhpSessionId()}"

            val response = RetrofitInstance.api.getUserProfile(userId, cookie, "Mozilla/5.0")
            val html = response.body()?.string() ?: return@withContext emptyList()
            val doc = Jsoup.parse(html)

            val lots = mutableListOf<Lot>()
            doc.select(".offer").forEach { offerBlock ->
                try {
                    val categoryLink = offerBlock.select(".offer-list-title a").firstOrNull() ?: return@forEach
                    val categoryName = categoryLink.text().trim()
                    val categoryHref = categoryLink.attr("href")

                    // Сначала пытаемся достать nodeId через LotUrlParser — он понимает
                    // и /lots/168/, и /lots/lot-name/ (именованные категории), и
                    // /chips/168/; если не получилось — fallback на старый regex.
                    val parsedCat = LotUrlParser.parse(categoryHref)
                    val nodeId = parsedCat?.nodeId
                        ?: parsedCat?.id?.takeIf { it.all { ch -> ch.isDigit() } }
                        ?: Regex("""/(?:lots|chips)/(\d+)""")
                            .find(categoryHref)?.groupValues?.get(1)
                        ?: return@forEach

                    offerBlock.select("a.tc-item").forEach { row ->
                        val href = row.attr("href")
                        val parsed = LotUrlParser.parse(href)

                        // Правильный ID:
                        //   - для обычного лота (/lots/offer?id=22238017) → "22238017";
                        //   - для чипа (/chips/offer?id=3934718-168-110-2842-0) → весь компаунд;
                        //   - для edit-URL (/lots/offerEdit?offer=...) тоже ок.
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
                        val amount = row.select(".tc-amount").text().replace(" ", "").toIntOrNull()
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

suspend fun FunPayRepository.getLotFields(lotId: String): LotFieldsData {
    return withContext(Dispatchers.IO) {
        val currentCookie = "golden_key=${getGoldenKey()}; PHPSESSID=${getPhpSessionId()}"

        val response = RetrofitInstance.api.getChatPage(
            "https://funpay.com/lots/offerEdit?offer=$lotId",
            currentCookie,
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
        )

        var freshCookies = currentCookie
        val setCookies = response.headers().values("Set-Cookie")
        if (setCookies.isNotEmpty()) {
            val newPhpsessid = setCookies.find { it.startsWith("PHPSESSID") }?.split(";")?.first()
            if (newPhpsessid != null) {
                freshCookies = "golden_key=${getGoldenKey()}; $newPhpsessid"
            }
        }

        val html = response.body()?.string() ?: throw Exception("Не удалось загрузить форму")
        val doc = Jsoup.parse(html)

        var csrfToken = ""
        try {
            val appDataStr = doc.select("body").attr("data-app-data")
            if (appDataStr.isNotEmpty()) {
                val json = JSONObject(appDataStr)
                csrfToken = json.optString("csrf-token")
            }
        } catch (e: Exception) { }

        if (csrfToken.isEmpty()) {
            csrfToken = doc.select("input[name='csrf_token']").first()?.attr("value") ?: ""
        }

        if (csrfToken.isEmpty()) throw Exception("CSRF токен не найден")

        val fields = mutableMapOf<String, LotField>()
        val seenNames = mutableSetOf<String>()

        doc.select("input[name]").forEach { input ->
            val name = input.attr("name")
            if (name.isEmpty() || name == "query" || name.startsWith("cc-option") || name == "csrf_token") return@forEach

            if (name == "amount") {
                fields[name] = LotField(name, "hidden", input.attr("value") ?: "", locale = null)
                return@forEach
            }

            val inputType = input.attr("type").ifEmpty { "text" }
            val formGroup = input.parents().firstOrNull { it.hasClass("form-group") }
            val label = formGroup?.select("label")?.text() ?: name

            if (inputType == "checkbox" &&
                (label.contains("Активное", ignoreCase = true) && label.contains("Деактивировать", ignoreCase = true) ||
                        label.contains("деактивировать после продажи", ignoreCase = true))) {
                fields[name] = LotField(name, "hidden", if (input.hasAttr("checked")) "on" else "", locale = null)
                return@forEach
            }

            if (inputType != "hidden" && seenNames.contains(name)) {
                return@forEach
            }
            seenNames.add(name)

            val locale = when {
                formGroup?.attr("data-locale")?.isNotEmpty() == true -> formGroup.attr("data-locale")
                name.contains("[ru]") -> "ru"
                name.contains("[en]") -> "en"
                else -> null
            }

            fields[name] = when (inputType) {
                "checkbox" -> LotField(name, "checkbox",
                    if (input.hasAttr("checked")) "on" else "", label, locale = locale)
                "hidden" -> LotField(name, "hidden", input.attr("value") ?: "", locale = locale)
                "radio" -> LotField(name, "radio",
                    if (input.hasAttr("checked")) input.attr("value") else "", label, locale = locale)
                else -> LotField(name, "text", input.attr("value") ?: "", label, locale = locale)
            }
        }

        doc.select("textarea[name]").forEach { textarea ->
            val name = textarea.attr("name")
            if (name.isEmpty() || seenNames.contains(name)) return@forEach
            seenNames.add(name)

            val formGroup = textarea.parents().firstOrNull { it.hasClass("form-group") }
            val label = formGroup?.select("label")?.text() ?: name

            val locale = when {
                formGroup?.attr("data-locale")?.isNotEmpty() == true -> formGroup.attr("data-locale")
                name.contains("[ru]") -> "ru"
                name.contains("[en]") -> "en"
                else -> null
            }

            fields[name] = LotField(name, "textarea", textarea.text(), label, locale = locale)
        }

        doc.select("select[name]").forEach { select ->
            val name = select.attr("name")
            if (name.isEmpty() || seenNames.contains(name)) return@forEach
            seenNames.add(name)

            val formGroup = select.parents().firstOrNull { it.hasClass("form-group") }
            if (formGroup?.hasClass("hidden") == true) return@forEach

            val label = formGroup?.select("label")?.text() ?: name
            val options = select.select("option").map { it.attr("value") to it.text() }
            val value = select.select("option[selected]").attr("value")

            val locale = when {
                formGroup?.attr("data-locale")?.isNotEmpty() == true -> formGroup.attr("data-locale")
                name.contains("[ru]") -> "ru"
                name.contains("[en]") -> "en"
                else -> null
            }

            fields[name] = LotField(name, "select", value, label, options, locale)
        }

        // Если node_id не нашли в hidden inputs — вытаскиваем из ссылки "Назад" на странице
        if (!fields.containsKey("node_id") || fields["node_id"]?.value.isNullOrEmpty()) {
            val backLink = doc.select("a.js-back-link, a[href*='/lots/'][href$='/trade']").firstOrNull()
                ?: doc.select("a[href*='/lots/']").firstOrNull { it.attr("href").matches(Regex(".*/lots/\\d+/.*")) }
            val nodeIdFromUrl = backLink?.attr("href")?.let {
                Regex("""/(?:lots|chips)/(\d+)/""").find(it)?.groupValues?.get(1)
            }
            if (!nodeIdFromUrl.isNullOrEmpty()) {
                fields["node_id"] = LotField("node_id", "hidden", nodeIdFromUrl, locale = null)
            }
        }

        // Если game не нашли — вытаскиваем из data-game атрибута showcase на странице
        if (!fields.containsKey("game") || fields["game"]?.value.isNullOrEmpty()) {
            val gameId = doc.select("[data-game]").firstOrNull()?.attr("data-game")
            if (!gameId.isNullOrEmpty()) {
                fields["game"] = LotField("game", "hidden", gameId, locale = null)
            }
        }

        val currency = doc.select(".form-control-feedback").text()

        val initialSellerPrice = doc.select("input[name='price']").attr("value").toDoubleOrNull() ?: 0.0
        val buyerPriceRows = mutableListOf<BuyerPriceRow>()
        if (initialSellerPrice > 0.0) {
            doc.select(".js-calc-table-body tr").forEach { row ->
                val method = row.select("th").text().trim()
                val priceText = row.select("td").text().trim()
                val currencySymbol = priceText.replace(Regex("[\\d.,\\s]"), "").trim()
                val buyerPrice = priceText.replace(Regex("[^\\d.,]"), "").replace(",", ".").toDoubleOrNull()
                if (method.isNotEmpty() && buyerPrice != null && buyerPrice > 0.0) {
                    buyerPriceRows.add(BuyerPriceRow(method, buyerPrice / initialSellerPrice, currencySymbol))
                }
            }
        }

        val imageUrls = mutableListOf<String>()
        val images = mutableListOf<LotImage>()

        doc.select("li.attachments-item[data-file-id]").forEach { li ->
            val fileId = li.attr("data-file-id")
            if (fileId.isEmpty()) return@forEach
            val aThumb = li.selectFirst("a.attachments-thumb") ?: return@forEach
            val fullUrl = aThumb.attr("href")
            val styleAttr = aThumb.attr("style")
            val thumbUrl = Regex("""url\(([^)]+)\)""").find(styleAttr)?.groupValues?.get(1)?.trim() ?: fullUrl
            if (fullUrl.isNotEmpty()) {
                images.add(LotImage(fileId, thumbUrl, fullUrl))
                imageUrls.add(fullUrl)
            }
        }

        if (images.isEmpty()) {
            doc.select("a.field-image-thumb, a.attachments-thumb").forEach { a ->
                val href = a.attr("href")
                if (href.isNotEmpty()) imageUrls.add(href)
            }
        }

        if (imageUrls.isEmpty()) {
            doc.select(".field-images img, .offer-images img").forEach { img ->
                val src = img.attr("src")
                if (src.isNotEmpty()) imageUrls.add(
                    if (src.startsWith("http")) src else "https://funpay.com$src"
                )
            }
        }

        LotFieldsData(fields, currency, csrfToken, freshCookies, imageUrls, images, buyerPriceRows, initialSellerPrice)
    }
}

suspend fun FunPayRepository.saveLot(
    lotId: String,
    fieldsData: Map<String, String>,
    csrfToken: String,
    cookies: String
): Pair<Boolean, String?> {
    return withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder().apply {
                add("csrf_token", csrfToken)

                fieldsData.forEach { (name, value) ->
                    when (name) {
                        "csrf_token" -> {}
                        "location" -> add(name, if (value.isNullOrEmpty()) "trade" else value)
                        "active" -> if (value == "on") add(name, value)
                        else -> add(name, value)
                    }
                }

                if (!fieldsData.containsKey("location")) add("location", "trade")
                if (!fieldsData.containsKey("secrets")) add("secrets", "")
                if (!fieldsData.containsKey("fields[images]")) add("fields[images]", "")
            }.build()

            val refererUrl = if(lotId == "0")
                "https://funpay.com/lots/offerEdit?node=${fieldsData["node_id"]}"
            else
                "https://funpay.com/lots/offerEdit?offer=$lotId"

            val request = Request.Builder()
                .url("https://funpay.com/lots/offerSave")
                .post(formBody)
                .header("Cookie", cookies)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", "https://funpay.com")
                .header("Referer", refererUrl)
                .build()

            val response = OkHttpClient().newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful) {
                return@withContext Pair(false, "HTTP ${response.code}: ${body?.take(200)}")
            }

            if (body?.contains("\"done\":true") == true || body?.contains("\"done\": true") == true) {
                return@withContext Pair(true, null)
            }

            if (body?.contains("\"error\"") == true || body?.contains("error") == true) {
                val msgMatch = Regex(""""msg"\s*:\s*"([^"]+)"""").find(body)
                val error = msgMatch?.groupValues?.get(1) ?: "Ошибка: $body"
                return@withContext Pair(false, error)
            }

            Pair(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Неизвестная ошибка")
        }
    }
}


suspend fun FunPayRepository.deleteLot(lotId: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            // Берём CSRF из формы редактирования лота — как Cardinal
            val fieldsData = getLotFields(lotId)
            val csrf = fieldsData.csrfToken
            val cookie = fieldsData.activeCookies

            val formBody = FormBody.Builder()
                .add("csrf_token", csrf)
                .add("offer_id", lotId)
                .add("deleted", "1")
                .build()

            val request = Request.Builder()
                .url("https://funpay.com/lots/offerSave")
                .post(formBody)
                .header("Cookie", cookie)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://funpay.com/lots/offerEdit?offer=$lotId")
                .build()

            val response = OkHttpClient().newCall(request).execute()
            val body = response.body?.string()

            body?.contains("\"done\":true") == true || body?.contains("\"done\": true") == true
                    || response.isSuccessful
        } catch (e: Exception) {
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
        try {
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
                .header("Cookie", cookies)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", "https://funpay.com")
                .header("Referer", "https://funpay.com/lots/offerEdit")
                .build()

            val response = OkHttpClient().newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            if (json.has("error") && json.optInt("error") == 1) {
                throw Exception(json.optString("msg", "Ошибка загрузки изображения"))
            }
            json.optString("fileId").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
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
            val fieldsData = getLotFields(lotId)

            val currentActive = fieldsData.fields["active"]?.value == "on"
            val updatedFields = fieldsData.fields.mapValues { it.value.value }.toMutableMap()

            val shouldBeActive = forceState ?: !currentActive

            if (shouldBeActive) {
                updatedFields["active"] = "on"
            } else {
                updatedFields.remove("active")
            }

            // Если node_id не вытащился из формы — берём из getMyLots() как надёжный fallback
            if (updatedFields["node_id"].isNullOrEmpty()) {
                val lot = getMyLots().find { it.id == lotId }
                if (lot != null) updatedFields["node_id"] = lot.nodeId
            }

            saveLot(lotId, updatedFields, fieldsData.csrfToken, fieldsData.activeCookies)
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
            val originalData = getLotFields(lotId)
            val originalFields = originalData.fields.mapValues { it.value.value }.toMutableMap()

            var currentCookie = originalData.activeCookies
            var currentCsrf = originalData.csrfToken

            // Берём node_id из полей формы; если нет — из getMyLots() как fallback
            val nodeIdFromForm = originalFields["node_id"]?.takeIf { it.isNotEmpty() }
            val nodeId = targetNodeId
                ?: nodeIdFromForm
                ?: getMyLots().find { it.id == lotId }?.nodeId
                ?: return@withContext Pair(false, "Не удалось определить категорию")

            val finalFields = if (targetNodeId != null && targetNodeId != originalFields["node_id"]) {
                val newCategoryResponse = RetrofitInstance.api.getChatPage(
                    "https://funpay.com/lots/offerEdit?node=$targetNodeId",
                    currentCookie,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
                )

                val setCookies = newCategoryResponse.headers().values("Set-Cookie")
                if (setCookies.isNotEmpty()) {
                    val newPhpsessid = setCookies.find { it.startsWith("PHPSESSID") }?.split(";")?.first()
                    if (newPhpsessid != null) {
                        currentCookie = "golden_key=${getGoldenKey()}; $newPhpsessid"
                    }
                }

                val newCategoryHtml = newCategoryResponse.body()?.string() ?: throw Exception("Не удалось загрузить форму категории")
                val newCategoryDoc = Jsoup.parse(newCategoryHtml)

                currentCsrf = newCategoryDoc.select("input[name='csrf_token']").first()?.attr("value") ?: currentCsrf

                val newFields = mutableMapOf<String, String>()
                newCategoryDoc.select("input[name], textarea[name], select[name]").forEach { element ->
                    val name = element.attr("name")
                    if (name.isNotEmpty() && name != "query" && !name.startsWith("cc-option")) {
                        newFields[name] = element.attr("value") ?: element.text()
                    }
                }

                if (originalFields["fields[summary][ru]"]?.isNotEmpty() == true) newFields["fields[summary][ru]"] = originalFields["fields[summary][ru]"]!!
                if (originalFields["fields[summary][en]"]?.isNotEmpty() == true) newFields["fields[summary][en]"] = originalFields["fields[summary][en]"]!!
                if (originalFields["fields[desc][ru]"]?.isNotEmpty() == true) newFields["fields[desc][ru]"] = originalFields["fields[desc][ru]"]!!
                if (originalFields["fields[desc][en]"]?.isNotEmpty() == true) newFields["fields[desc][en]"] = originalFields["fields[desc][en]"]!!
                if (originalFields["price"]?.isNotEmpty() == true) newFields["price"] = originalFields["price"]!!
                if (originalFields["amount"]?.isNotEmpty() == true) newFields["amount"] = originalFields["amount"]!!

                newFields
            } else {
                originalFields
            }

            val fieldsToSave = finalFields.toMutableMap()
            fieldsToSave["offer_id"] = "0"
            fieldsToSave["node_id"] = nodeId
            fieldsToSave["active"] = "on"

            saveLot("0", fieldsToSave, currentCsrf, currentCookie)

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

    // Перезагружаем лоты при возврате на экран если аккаунт сменился
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
                                            field.label.contains("*") &&
                                            fieldValues[name].isNullOrBlank()
                                }

                                if (missingFields.isNotEmpty()) {
                                    errorMessage = "Заполните обязательные поля: ${missingFields.values.joinToString(", ") { it.label.replace("*", "").trim() }}"
                                    isSaving = false
                                    return@launch
                                }

                                val allFields = data.fields.mapValues { it.value.value }.toMutableMap()
                                allFields.putAll(fieldValues)

                                val (ok, error) = repository.saveLot(lotId, allFields, data.csrfToken, data.activeCookies)
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

                        val ruFields = state.fieldsData.fields.filter { it.value.locale == "ru" }
                        val enFields = state.fieldsData.fields.filter { it.value.locale == "en" }
                        val commonFields = state.fieldsData.fields.filter { it.value.locale == null && it.value.type != "hidden" }

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

                            // Кнопка автоперевода RU→EN: берёт все значения RU-полей,
                            // переводит через Google Translate с защитой эмодзи/
                            // спецсимволов (см. FpTranslate в FpExtensions.kt) и
                            // подставляет в соответствующие EN-поля.
                            //
                            // RU и EN поля у FunPay называются одинаково, отличаются только
                            // суффиксом locale ([ru]/[en]); а также у некоторых полей
                            // совсем нет суффикса, но их locale всё равно отмечен в
                            // LotField.locale. Мэппинг строим по имени БЕЗ суффикса.
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

                                                    // Пытаемся найти EN-пару несколькими способами:
                                                    //   1) прямая замена [ru]→[en], _ru→_en, -ru→-en;
                                                    //   2) совпадение по «ядру имени» без суффикса локали.
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
                                                    // Автоматически переключаемся на EN-таб, чтобы
                                                    // пользователь увидел результат.
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
    when (field.type) {
        "text" -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.label) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                    unfocusedBorderColor = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.3f)
                ),
                shape = RoundedCornerShape(theme.borderRadius.dp)
            )
        }
        "textarea" -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(field.label) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                    unfocusedBorderColor = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.3f)
                ),
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
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ThemeManager.parseColor(theme.accentColor),
                        unfocusedBorderColor = ThemeManager.parseColor(theme.textSecondaryColor).copy(0.3f)
                    )
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    field.options.forEach { (optValue, optLabel) ->
                        DropdownMenuItem({ Text(optLabel) }, {
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