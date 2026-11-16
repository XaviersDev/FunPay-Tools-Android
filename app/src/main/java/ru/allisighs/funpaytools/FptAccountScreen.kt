package ru.allisighs.funpaytools

import android.widget.Toast
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class FptPlan(val key: String, val label: String, val price: String, val note: String)
private data class FptNick(val name: String, val paid: Boolean)

@Composable
fun FptAccountScreen(repository: FunPayRepository, theme: AppTheme, navController: NavController) {
    val scope = rememberCoroutineScope()
    val accent = ThemeManager.parseColor(theme.accentColor)
    val text1 = ThemeManager.parseColor(theme.textPrimaryColor)
    val text2 = ThemeManager.parseColor(theme.textSecondaryColor)
    val surface = ThemeManager.parseColor(theme.surfaceColor)
    val shape = RoundedCornerShape(theme.borderRadius.dp)
    val danger = Color(0xFFE53935)

    val me = repository.getActiveAccount()
    val myUid = me?.userId?.takeIf { it.isNotBlank() && it != "0" } ?: ""
    val myName = me?.username.orEmpty()

    var tgSession by remember { mutableStateOf(if (FptAccountApi.previewProfile != null) "preview" else FptAccountApi.tgSession) }
    var tgLabel by remember { mutableStateOf(FptAccountApi.tgLabel) }
    var profile by remember { mutableStateOf<JSONObject?>(FptAccountApi.previewProfile) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgErr by remember { mutableStateOf(false) }
    var decorMsg by remember { mutableStateOf("") }
    var decorErr by remember { mutableStateOf(false) }
    var loginJob by remember { mutableStateOf<Job?>(null) }

    // покупка
    var payProduct by remember { mutableStateOf<String?>(null) }
    var payStep by remember { mutableIntStateOf(0) }
    var payState by remember { mutableStateOf("") }
    var payTitle by remember { mutableStateOf("") }
    var payTid by remember { mutableStateOf<String?>(null) }

    // ники и украшения
    var newNickKey by remember { mutableStateOf("") }
    var styleDialogFor by remember { mutableStateOf<String?>(null) }
    var styleKey by remember { mutableStateOf("") }
    val active = remember(FptDecor.version.intValue) { FptDecor.active(myUid) }
    var pickFrame by remember(active) { mutableIntStateOf(active.first) }
    var pickBanner by remember(active) { mutableIntStateOf(active.second) }
    var proKey by remember { mutableStateOf("") }

    fun say(t: String, err: Boolean = false) { msg = t; msgErr = err }
    fun sayDecor(t: String, err: Boolean = false) { decorMsg = t; decorErr = err }

    fun forgetTg() {
        FptAccountApi.tgSession = ""; FptAccountApi.tgLabel = ""
        tgSession = ""; tgLabel = ""; profile = null; payStep = 0; payProduct = null
    }

    fun reload() {
        if (FptAccountApi.tgSession.isEmpty()) return
        loading = true
        scope.launch {
            val r = FptAccountApi.job("me")
            loading = false
            r.onSuccess { profile = it }
            r.onFailure { e ->
                if (e.message == "NO_TG_SESSION") forgetTg()
                say(FptAccountApi.errText(e.message), true)
            }
        }
    }

    LaunchedEffect(Unit) {
        FptDecor.init()
        if (tgSession.isNotEmpty() && profile == null) reload()
    }

    fun checkPayment(manual: Boolean) {
        val tid = payTid ?: return
        val product = payProduct ?: return
        if (manual) payState = "Проверяю оплату…"
        scope.launch {
            val r = FptAccountApi.job("check", JSONObject().put("tid", tid))
            r.onSuccess { v ->
                when (v.optString("status")) {
                    "paid" -> {
                        payTid = null; payStep = 0; payProduct = null
                        val t = when (product) {
                            "nick" -> "Оплата прошла. Вставь ключ стиля ниже."
                            "frame", "banner" -> "Оплата прошла. Выбери украшение ниже и нажми «Сохранить украшения»."
                            else -> "Оплата прошла. Ключ PRO пришёл в бота @FPToolsBot — вставь его ниже в «Активировать PRO»."
                        }
                        say(t)
                        Toast.makeText(AppContext, t, Toast.LENGTH_LONG).show()
                        reload()
                    }
                    "canceled" -> payState = "Платёж отменён. Создай новый счёт."
                    else -> payState = "Оплата пока не пришла. Проверяю каждые 20 секунд."
                }
            }
            r.onFailure { if (manual) say(FptAccountApi.errText(it.message), true) }
        }
    }

    // автопроверка счёта: каждые 20 секунд, 20 минут
    LaunchedEffect(payTid) {
        if (payTid == null) return@LaunchedEffect
        repeat(60) {
            delay(20_000)
            if (payTid == null) return@LaunchedEffect
            checkPayment(false)
        }
    }

    fun pay(method: String) {
        val product = payProduct ?: return
        busy = true
        payState = "Создаю счёт…"
        scope.launch {
            val r = FptAccountApi.job("pay", JSONObject().put("product", product).put("method", method))
            busy = false
            r.onSuccess { inv ->
                val url = inv.optString("payUrl")
                if (url.isNotEmpty()) PlatformBridge.openUrl(url)
                payTid = inv.optString("tid").ifEmpty { null }
                payTitle = "${FptAccountApi.productName(product)}: ${inv.optString("amount")} ₽"
                payState = "Оплата открылась в браузере. Счёт действует ${inv.optString("expiresIn")}. Как только оплата пройдёт, всё включится само."
                payStep = 2
            }
            r.onFailure { payState = ""; say(FptAccountApi.errText(it.message), true) }
        }
    }

    fun withFunPaySession(decor: Boolean, after: suspend (String) -> Unit) {
        busy = true
        scope.launch {
            val r = FptAccountApi.ensureFunPaySession(repository) { s -> PlatformBridge.runOnMain { if (decor) sayDecor(s) else say(s) } }
            r.onSuccess { after(it) }
            r.onFailure { e ->
                busy = false
                val t = FptAccountApi.errText(e.message)
                if (decor) sayDecor(t, true) else say(t, true)
            }
        }
    }

    fun addNick() {
        val key = newNickKey.trim()
        if (EpicNicksManager.decodeKey(key) == null) { say(FptAccountApi.errText("BAD_KEY"), true); return }
        withFunPaySession(false) { token ->
            say("Ставлю ник…")
            val r = FptAccountApi.job("nick", JSONObject().put("fpSession", token).put("key", key))
            busy = false
            r.onSuccess { v ->
                val name = v.optString("nickname")
                if (name.isNotEmpty()) EpicNicksManager.setLocal(name, key)
                newNickKey = ""
                say("Готово! У тебя ник $name уже светится, у остальных в течение пары минут.")
                reload()
            }
            r.onFailure { e ->
                if (e.message == "NO_SESSION") FptAccountApi.forgetPromoSession()
                say(FptAccountApi.errText(e.message), true)
            }
        }
    }

    fun changeStyle(name: String, key: String) {
        if (EpicNicksManager.decodeKey(key) == null) { say(FptAccountApi.errText("BAD_KEY"), true); return }
        busy = true
        say("Меняю стиль…")
        scope.launch {
            val r = FptAccountApi.job("style", JSONObject().put("nickname", name).put("key", key.trim()))
            busy = false
            r.onSuccess {
                EpicNicksManager.setLocal(name, key.trim())
                say("Стиль ника $name обновлён. У тебя уже видно, у остальных поменяется в течение пары минут.")
            }
            r.onFailure { say(FptAccountApi.errText(it.message), true) }
        }
    }

    val p = profile
    val rights = p?.optJSONObject("decor")
    fun hasRight(k: String): Boolean = rights != null && !rights.isNull(k) && rights.opt(k) != false && rights.optString(k).isNotEmpty() && rights.optString(k) != "false"
    fun rightLabel(k: String) = when (rights?.optString(k)) { "bought" -> "куплено навсегда"; "pro" -> "входит в PRO"; else -> "не куплено" }

    fun saveDecor() {
        if (pickFrame > 0 && !hasRight("frame")) { sayDecor("Рамку сначала нужно купить или взять PRO.", true); return }
        if (pickBanner > 0 && !hasRight("banner")) { sayDecor("Баннер сначала нужно купить или взять PRO.", true); return }
        val f = pickFrame; val b = pickBanner
        withFunPaySession(true) { token ->
            sayDecor("Сохраняю…")
            val r = FptAccountApi.job("decor", JSONObject().put("fpSession", token).put("frame", f).put("banner", b))
            busy = false
            r.onSuccess {
                FptDecor.setLocal(myUid, f, b)
                sayDecor("Готово! У тебя уже видно, у остальных обновится в течение пары минут.")
                appScope.launch { delay(90_000); FptDecor.refreshNow() }
            }
            r.onFailure { e ->
                if (e.message == "NO_SESSION") FptAccountApi.forgetPromoSession()
                sayDecor(FptAccountApi.errText(e.message), true)
            }
        }
    }

    @Composable
    fun Card(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxWidth().clip(shape).background(surface.copy(alpha = theme.containerOpacity)).padding(16.dp)) { content() }
    }

    @Composable
    fun SectionTitle(icon: ImageVector, title: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = text1, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    @Composable
    fun Msg(t: String, err: Boolean) {
        if (t.isNotEmpty()) Text(t, color = if (err) danger else text2, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = text1, unfocusedTextColor = text1, focusedBorderColor = accent,
        unfocusedBorderColor = text2.copy(alpha = 0.3f), cursorColor = accent,
        focusedLabelColor = accent, unfocusedLabelColor = text2
    )
    val btnColors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)

    styleDialogFor?.let { nick ->
        AlertDialog(
            onDismissRequest = { styleDialogFor = null },
            containerColor = ThemeManager.dialogSurface(theme),
            title = { Text("Стиль ника $nick", color = text1) },
            text = {
                Column {
                    Text("Собери стиль в генераторе, скопируй ключ FPT-STYLE и вставь сюда.", color = text2, fontSize = 13.sp)
                    TextButton(onClick = { PlatformBridge.openUrl(FptAccountApi.STYLE_GENERATOR) }) { Text("Открыть генератор стилей", color = accent) }
                    OutlinedTextField(value = styleKey, onValueChange = { styleKey = it }, label = { Text("FPT-STYLE-…") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = { val k = styleKey; styleDialogFor = null; changeStyle(nick, k) }) { Text("Сохранить", color = accent) } },
            dismissButton = { TextButton(onClick = { styleDialogFor = null }) { Text("Отмена", color = text2) } }
        )
    }

    if (payStep == 1 && payProduct != null) {
        AlertDialog(
            onDismissRequest = { payStep = 0; payProduct = null },
            containerColor = ThemeManager.dialogSurface(theme),
            title = { Text("${FptAccountApi.productName(payProduct!!)}: чем оплатить?", color = text1, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("sbp" to "СБП", "card" to "Карта", "crypto" to "Крипта").forEach { (k, label) ->
                        Button(onClick = { pay(k) }, enabled = !busy, colors = btnColors, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
                    if (payState.isNotEmpty()) Text(payState, color = text2, fontSize = 13.sp)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { payStep = 0; payProduct = null }) { Text("Отмена", color = text2) } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- шапка
        item {
            Card {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FramedAvatar(uid = myUid, size = 56.dp, frameOverride = pickFrame) {
                            AsyncImage(model = me?.avatarUrl?.ifEmpty { null } ?: "https://funpay.com/img/layout/avatar.png", contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(CircleShape))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Аккаунт FunPay Tools", color = text2, fontSize = 12.sp)
                            if (myName.isNotEmpty()) EpicNicknameText(text = myName, style = LocalTextStyle.current.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = text1))
                            else Text("Нет аккаунта FunPay", color = text1, fontWeight = FontWeight.Bold)
                        }
                        if (tgSession.isNotEmpty()) IconButton(onClick = { reload() }, enabled = !loading) { Icon(Icons.Default.Refresh, null, tint = text2) }
                    }
                    if (pickBanner > 0) {
                        Spacer(Modifier.height(12.dp))
                        FptProfileBanner(uid = myUid, bannerOverride = pickBanner)
                    }
                }
            }
        }

        // ---------- Telegram
        item {
            Card {
                Column {
                    SectionTitle(Icons.Default.Send, "Telegram")
                    Spacer(Modifier.height(8.dp))
                    if (tgSession.isEmpty()) {
                        Text("Покупки, PRO, цветные ники, рамки и баннеры привязаны к твоему Telegram. Вход через бота @FPToolsBot, пароль не нужен.", color = text2, fontSize = 13.sp, lineHeight = 17.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            enabled = loginJob == null,
                            colors = btnColors,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                say("Открываю бота…")
                                loginJob = scope.launch {
                                    val r = FptAccountApi.telegramLogin({ s -> PlatformBridge.runOnMain { say(s) } }, { false })
                                    loginJob = null
                                    r.onSuccess { (s, l) ->
                                        tgSession = s; tgLabel = l
                                        say("Telegram подключён.")
                                        Toast.makeText(AppContext, "Telegram подключён", Toast.LENGTH_SHORT).show()
                                        reload()
                                    }
                                    r.onFailure { say(FptAccountApi.errText(it.message), true) }
                                }
                            }
                        ) {
                            if (loginJob != null) { CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                            Text(if (loginJob != null) "Жду подтверждения в боте…" else "Войти через Telegram")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tgLabel.ifEmpty { "подключён" }, color = text1, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { FptAccountApi.telegramLogout(); forgetTg(); say("") } }) { Text("Выйти", color = text2) }
                        }
                    }
                    Msg(msg, msgErr)
                }
            }
        }

        if (tgSession.isNotEmpty()) {
            if (loading && p == null) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) } }
            } else if (p == null) {
                item {
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Профиль не загрузился.", color = text2, modifier = Modifier.weight(1f))
                            TextButton(onClick = { reload() }) { Text("Повторить", color = accent) }
                        }
                    }
                }
            } else {
                val proText = when (p.optString("pro")) {
                    "forever" -> "навсегда"
                    "temporary" -> p.optLong("proUntil").takeIf { it > 0 }?.let { "до " + SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it)) } ?: "активна"
                    else -> "нет"
                }
                val nicksArr = p.optJSONArray("nicks") ?: JSONArray()
                val nicks = (0 until nicksArr.length()).mapNotNull { nicksArr.optJSONObject(it) }.map { x ->
                    FptNick(x.optString("nickname"), x.optBoolean("is_paid", x.optInt("is_paid", 0) != 0))
                }
                val meOwned = nicks.any { it.name.equals(myName, ignoreCase = true) }
                val credits = p.optInt("nickCredits", 0)
                val free = p.optBoolean("freeNick", false)
                val canAdd = myName.isNotEmpty() && !meOwned && (free || credits > 0)
                val plans = ArrayList<FptPlan>()
                p.optJSONObject("plans")?.let { pl ->
                    for (k in listOf("1m", "3m", "forever")) pl.optJSONObject(k)?.let { x ->
                        plans.add(FptPlan(k, x.optString("label"), "${x.optString("rub")} ₽", if (k == "forever") "выгоднее всего" else ""))
                    }
                }
                if (!canAdd) {
                    val note = if (nicks.isEmpty()) "навсегда, без подписки. С PRO первый ник бесплатно" else {
                        val first = p.optInt("nickPriceFirst", 0).takeIf { it > 0 } ?: 499
                        val next = p.optInt("nickPriceNext", 0).takeIf { it > 0 } ?: 399
                        "ещё один ник. Первый $first ₽, каждый следующий $next ₽"
                    }
                    plans.add(FptPlan("nick", "Цветной ник", "${p.optString("nickPrice")} ₽", note))
                }
                val prices = p.optJSONObject("decorPrices")
                val priceFrame = prices?.optInt("frame", 0)?.takeIf { it > 0 } ?: 149
                val priceBanner = prices?.optInt("banner", 0)?.takeIf { it > 0 } ?: 199

                // ---------- сводка
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("PRO" to proText, "Цветные ники" to (if (nicks.isEmpty()) "нет" else nicks.size.toString())).forEach { (k, v) ->
                            Box(Modifier.weight(1f).clip(shape).background(surface.copy(alpha = theme.containerOpacity)).padding(14.dp)) {
                                Column {
                                    Text(k, color = text2, fontSize = 12.sp)
                                    Text(v, color = text1, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }

                // ---------- оплата в процессе
                if (payStep == 2) {
                    item {
                        Card {
                            Column {
                                Text(payTitle, color = text1, fontWeight = FontWeight.Bold)
                                Text(payState, color = text2, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { checkPayment(true) }, colors = btnColors) { Text("Я оплатил") }
                                    OutlinedButton(onClick = { payStep = 0; payProduct = null; payTid = null }) { Text("Отмена", color = text2) }
                                }
                            }
                        }
                    }
                }

                // ---------- магазин
                item {
                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle(Icons.Default.Storefront, "Магазин")
                            plans.forEach { pl ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, text2.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(pl.label.ifEmpty { FptAccountApi.productName(pl.key) }, color = text1, fontWeight = FontWeight.SemiBold)
                                        if (pl.note.isNotEmpty()) Text(pl.note, color = text2, fontSize = 12.sp)
                                    }
                                    Button(enabled = !busy && payStep == 0, colors = btnColors, onClick = { payProduct = pl.key; payStep = 1; payState = "" }) { Text(pl.price) }
                                }
                            }
                        }
                    }
                }

                // ---------- активация PRO
                item {
                    Card {
                        Column {
                            SectionTitle(Icons.Default.Key, "Активировать PRO")
                            Text("После покупки ключ приходит в бота @FPToolsBot.", color = text2, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                            OutlinedTextField(value = proKey, onValueChange = { proKey = it }, label = { Text("Ключ PRO") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Button(enabled = proKey.isNotBlank() && !busy, colors = btnColors, modifier = Modifier.fillMaxWidth(), onClick = {
                                busy = true
                                LicenseManager.activateKey(proKey.trim()) { res ->
                                    PlatformBridge.runOnMain {
                                        busy = false
                                        res.onSuccess { proKey = ""; say("PRO активирован.") }
                                        res.onFailure { say(it.message ?: "Ключ не подошёл", true) }
                                    }
                                }
                            }) { Text("Активировать") }
                        }
                    }
                }

                // ---------- цветные ники
                item {
                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle(Icons.Default.AutoAwesome, "Цветные ники")
                            if (nicks.isEmpty()) Text("Пока нет ни одного ника.", color = text2, fontSize = 13.sp)
                            nicks.forEach { n ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        EpicNicknameText(text = n.name, style = LocalTextStyle.current.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = text1))
                                        Text(if (n.paid) "навсегда" else "по подписке PRO", color = text2, fontSize = 12.sp)
                                    }
                                    TextButton(enabled = !busy, onClick = { styleKey = ""; styleDialogFor = n.name }) { Text("Стиль", color = accent) }
                                }
                            }
                            if (canAdd) {
                                Spacer(Modifier.height(4.dp))
                                Text("Добавить ник $myName", color = text1, fontWeight = FontWeight.SemiBold)
                                Text((if (credits > 0) "Ник оплачен." else "С PRO первый ник бесплатно.") + " Собери стиль в генераторе, скопируй ключ и вставь сюда.", color = text2, fontSize = 12.sp, lineHeight = 16.sp)
                                OutlinedTextField(value = newNickKey, onValueChange = { newNickKey = it }, label = { Text("FPT-STYLE-…") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { PlatformBridge.openUrl(FptAccountApi.STYLE_GENERATOR) }) { Text("Генератор стилей", color = accent) }
                                    Spacer(Modifier.weight(1f))
                                    Button(enabled = !busy && newNickKey.isNotBlank(), colors = btnColors, onClick = { addNick() }) { Text("Поставить ник") }
                                }
                            } else if (!meOwned) {
                                TextButton(onClick = { PlatformBridge.openUrl(FptAccountApi.STYLE_GENERATOR) }) { Text("Посмотреть стили в генераторе", color = accent) }
                            }
                        }
                    }
                }

                // ---------- рамки и баннеры
                item {
                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle(Icons.Default.Brush, "Рамка и баннер")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Рамка: ${rightLabel("frame")}", color = text2, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                if (!hasRight("frame")) TextButton(enabled = !busy && payStep == 0, onClick = { payProduct = "frame"; payStep = 1; payState = "" }) { Text("Купить $priceFrame ₽", color = accent, fontSize = 12.sp) }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                item {
                                    DecorTile(selected = pickFrame == 0, accent = accent, onClick = { pickFrame = 0 }) {
                                        Text("Нет", color = text2, fontSize = 12.sp)
                                    }
                                }
                                items(FptDecor.FRAMES) { f ->
                                    DecorTile(selected = pickFrame == f.id, accent = accent, onClick = { pickFrame = f.id }) {
                                        AsyncImage(model = FptDecor.BASE + f.file, contentDescription = f.name, modifier = Modifier.fillMaxSize().padding(4.dp))
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Баннер: ${rightLabel("banner")}", color = text2, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                if (!hasRight("banner")) TextButton(enabled = !busy && payStep == 0, onClick = { payProduct = "banner"; payStep = 1; payState = "" }) { Text("Купить $priceBanner ₽", color = accent, fontSize = 12.sp) }
                            }
                            if (pickBanner > 0) Text("Выбран: " + FptDecor.BANNERS.firstOrNull { it.id == pickBanner }?.name.orEmpty(), color = text1, fontSize = 13.sp)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp))
                                        .border(BorderStroke(if (pickBanner == 0) 2.dp else 1.dp, if (pickBanner == 0) accent else text2.copy(0.2f)), RoundedCornerShape(8.dp))
                                        .clickable { pickBanner = 0 },
                                    contentAlignment = Alignment.Center
                                ) { Text("Без баннера", color = text2, fontSize = 12.sp) }
                                FptDecor.BANNERS.forEach { b ->
                                    AsyncImage(
                                        model = FptDecor.BASE + b.file,
                                        contentDescription = b.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(b.ar).clip(RoundedCornerShape(8.dp))
                                            .border(BorderStroke(if (pickBanner == b.id) 2.dp else 0.dp, if (pickBanner == b.id) accent else Color.Transparent), RoundedCornerShape(8.dp))
                                            .clickable { pickBanner = b.id }
                                    )
                                }
                            }
                            Button(enabled = !busy && myUid.isNotEmpty(), colors = btnColors, modifier = Modifier.fillMaxWidth(), onClick = { saveDecor() }) {
                                if (busy) { CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                                Text("Сохранить украшения")
                            }
                            Text("Для сохранения FunPay Tools один раз подтвердит аккаунт FunPay: создаст и сразу удалит временный лот «FPT Verify».", color = text2, fontSize = 11.sp, lineHeight = 14.sp)
                            Msg(decorMsg, decorErr)
                        }
                    }
                }
            }
        } else {
            item {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle(Icons.Default.Diamond, "Что даёт аккаунт")
                        listOf(
                            "PRO: все закрытые функции без рекламы",
                            "Цветной анимированный ник, который видят все пользователи FunPay Tools",
                            "Рамка для аватарки (30 вариантов) и баннер профиля (27 вариантов)",
                            "Покупки работают в приложении, расширении и FunPay Tools Desktop"
                        ).forEach { Text("• $it", color = text2, fontSize = 13.sp, lineHeight = 17.sp) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DecorTile(selected: Boolean, accent: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else accent.copy(alpha = 0.15f)), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
