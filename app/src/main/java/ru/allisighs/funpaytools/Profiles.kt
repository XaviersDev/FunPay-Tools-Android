package ru.allisighs.funpaytools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup

data class FunPayUserProfile(
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val isOnline: Boolean,
    val registrationDate: String?,
    val ratingValue: String?,
    val reviewCount: String?,
    val reviewCountShort: String?,
    val offerGroups: List<ProfileOfferGroup>,
    val reviews: List<ProfileReview>,
    val continueToken: String? 
)

data class ProfileOfferGroup(
    val categoryName: String,
    val categoryUrl: String,
    val offers: List<ProfileOffer>
)

data class ProfileOffer(
    val id: String,
    val title: String,
    val price: String,
    val server: String?,
    val url: String
)

data class ProfileReview(
    val rating: Int,
    val text: String,
    val date: String,
    val detail: String?,
    val sellerReply: String? = null
)

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val profile: FunPayUserProfile) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}


enum class SubmitState {
    IDLE, LOADING, SUCCESS, ERROR
}

private suspend fun FunPayRepository.resolveUserIdFromNode(nodeId: String): String {
    return withContext(Dispatchers.IO) {
        val cookie = "golden_key=${getGoldenKey()}; PHPSESSID=${getPhpSessionId()}"
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://funpay.com/chat/?node=$nodeId")
            .header("Cookie", cookie)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        val html = client.newCall(request).execute().body?.string() ?: return@withContext ""
        val doc = Jsoup.parse(html)

        val chatEl = doc.select("[data-name][data-user]").firstOrNull()
            ?: doc.select("[data-name]").firstOrNull()

        val dataName = chatEl?.attr("data-name") ?: ""
        val myUserId = chatEl?.attr("data-user")?.trim() ?: ""

        val ids = Regex("""\d+""").findAll(dataName).map { it.value }.toList()
        ids.firstOrNull { it != myUserId && it.isNotEmpty() } ?: ""
    }
}


private suspend fun FunPayRepository.fetchMoreReviews(userId: String, continueToken: String): Pair<List<ProfileReview>, String?> {
    return withContext(Dispatchers.IO) {
        val cookie = "golden_key=${getGoldenKey()}; PHPSESSID=${getPhpSessionId()}"
        val client = OkHttpClient()

        val requestBody = FormBody.Builder()
            .add("user_id", userId)
            .add("continue", continueToken)
            .add("filter", "")
            .build()

        val request = Request.Builder()
            .url("https://funpay.com/users/reviews")
            .header("Cookie", cookie)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("X-Requested-With", "XMLHttpRequest") 
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return@withContext Pair(emptyList(), null)

        val doc = Jsoup.parse(html)
        val newReviews = parseReviewsFromHtml(html)
        
        val nextToken = doc.select("input[name=continue]").firstOrNull()?.attr("value")?.ifEmpty { null }

        Pair(newReviews, nextToken)
    }
}

private fun parseReviewsFromHtml(html: String): List<ProfileReview> {
    val doc = Jsoup.parse(html)
    return doc.select(".review-container").mapNotNull { reviewEl ->
        val ratingDiv = reviewEl.select(".review-item-rating .rating > div").firstOrNull()
        val ratingNum = Regex("rating(\\d)").find(ratingDiv?.className() ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 5
        val text = reviewEl.select(".review-item-text").text().trim()
        val date = reviewEl.select(".review-item-date").text().trim()
        val detail = reviewEl.select(".review-item-detail").text().trim().ifEmpty { null }
        val sellerReply = reviewEl.select(".review-item-answer div").text().trim().ifEmpty { null }

        if (text.isNotEmpty() || date.isNotEmpty()) ProfileReview(ratingNum, text, date, detail, sellerReply) else null
    }
}

suspend fun FunPayRepository.getUserProfile(userId: String): FunPayUserProfile {
    return withContext(Dispatchers.IO) {
        val cookie = "golden_key=${getGoldenKey()}; PHPSESSID=${getPhpSessionId()}"
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://funpay.com/users/$userId/")
            .header("Cookie", cookie)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        val html = client.newCall(request).execute().body?.string()
            ?: throw Exception("Не удалось загрузить профиль")

        val doc = Jsoup.parse(html)

        val username = doc.select("h1 > span").firstOrNull()?.text()?.trim()
            ?: doc.select("h1").text().trim()

        val avatarEl = doc.select(".avatar-photo").firstOrNull()
        val avatarStyle = avatarEl?.attr("style") ?: ""
        val avatarUrl = Regex("""url\((['"]?)([^'")\s]+)\1\)""").find(avatarStyle)
            ?.groupValues?.get(2)
            ?.let { if (it.startsWith("http")) it else "https://funpay.com$it" }
            ?.trim()

        val isOnline = doc.select(".media-user-status").text().contains("Онлайн", true)

        val registrationDate = doc.select(".param-item .text-nowrap").firstOrNull()
            ?.text()?.trim()?.lines()?.firstOrNull()?.trim()

        val ratingValue = doc.select(".rating-value .big").first()?.text()?.trim()?.ifEmpty { null }
        val reviewCountRaw = doc.select(".rating-full-count a").text().trim()
        val reviewCountShort = reviewCountRaw.replace("\n", " ").replace("\\s+".toRegex(), " ").ifEmpty { null }

        val offerGroups = mutableListOf<ProfileOfferGroup>()
        doc.select(".offer").forEach { offerEl ->
            val categoryLink = offerEl.select(".offer-list-title a").firstOrNull() ?: return@forEach
            val categoryName = categoryLink.text().trim()
            val rawCategoryHref = categoryLink.attr("href")
            val categoryUrl = if (rawCategoryHref.startsWith("http")) rawCategoryHref else "https://funpay.com$rawCategoryHref"

            val offers = offerEl.select("a.tc-item").mapNotNull { row ->
                val href = row.attr("href")
                val id = Regex("""id=(\d+)""").find(href)?.groupValues?.get(1) ?: return@mapNotNull null
                val title = row.select(".tc-desc-text").text().trim().ifEmpty { "Без названия" }
                val price = row.select(".tc-price").text().trim()
                val server = row.select(".tc-server").text().trim().ifEmpty { null }
                val offerUrl = if (href.startsWith("http")) href else "https://funpay.com$href"
                ProfileOffer(id, title, price, server, offerUrl)
            }

            if (offers.isNotEmpty()) offerGroups.add(ProfileOfferGroup(categoryName, categoryUrl, offers))
        }

        val reviews = parseReviewsFromHtml(html)

        
        val continueToken = doc.select("input[name=continue]").firstOrNull()?.attr("value")?.ifEmpty { null }

        FunPayUserProfile(
            userId = userId,
            username = username,
            avatarUrl = avatarUrl,
            isOnline = isOnline,
            registrationDate = registrationDate,
            ratingValue = ratingValue,
            reviewCount = reviewCountRaw,
            reviewCountShort = reviewCountShort,
            offerGroups = offerGroups,
            reviews = reviews,
            continueToken = continueToken
        )
    }
}




private suspend fun sendReviewToFeed(
    context: Context,
    profile: FunPayUserProfile,
    review: ProfileReview
): Result<String> {
    return withContext(Dispatchers.IO) {
        try {
            var gameName = ""
            var price = ""
            val detailStr = review.detail ?: ""
            val lastCommaIdx = detailStr.lastIndexOf(',')

            if (lastCommaIdx != -1) {
                gameName = detailStr.substring(0, lastCommaIdx).trim()
                price = detailStr.substring(lastCommaIdx + 1).trim()
            } else {
                gameName = detailStr
            }

            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"

            val json = JSONObject().apply {
                put("seller_name", profile.username)
                put("seller_url", "https://funpay.com/users/${profile.userId}/")
                put("reviewer_name", "Аноним")
                put("review_text", review.text)
                put("rating", review.rating)
                put("device_id", androidId)
                put("time_ago", review.date)
                put("game_name", gameName)
                put("price", price)
                put("seller_response", review.sellerReply ?: "")
            }

            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://funpay.tools/api/reviews")
                .header("Authorization", "Bearer fptoolsdim")
                .post(body)
                .build()

            val client = OkHttpClient()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Result.success("Отзыв успешно добавлен в ленту!")
            } else {
                val errorMsg = try {
                    JSONObject(responseBody).getString("error")
                } catch (e: Exception) {
                    "Ошибка сервера: ${response.code}"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка сети: ${e.message}"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    nodeId: String,
    username: String,
    navController: NavController,
    repository: FunPayRepository,
    theme: AppTheme
) {
    val context = LocalContext.current
    var uiState by remember { mutableStateOf<ProfileUiState>(ProfileUiState.Loading) }
    var resolvedUserId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val allLabels = remember { ChatFolderManager.getLabels(context) }
    val chatLabels = remember { ChatFolderManager.getChatLabels(context) }
    val assignedLabels = remember(nodeId, allLabels, chatLabels) {
        val labelIds = chatLabels[nodeId] ?: emptyList()
        allLabels.filter { it.id in labelIds }
    }

    LaunchedEffect(nodeId) {
        uiState = ProfileUiState.Loading
        try {
            val userId = repository.resolveUserIdFromNode(nodeId)
            if (userId.isEmpty()) throw Exception("Не удалось определить ID пользователя")
            resolvedUserId = userId
            val profile = repository.getUserProfile(userId)
            uiState = ProfileUiState.Success(profile)
        } catch (e: Exception) {
            uiState = ProfileUiState.Error(e.message ?: "Ошибка загрузки профиля")
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(username, color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ThemeManager.parseColor(theme.accentColor))
                    }
                },
                actions = {
                    if (resolvedUserId.isNotEmpty()) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://funpay.com/users/$resolvedUserId/"))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.OpenInBrowser, null, tint = ThemeManager.parseColor(theme.accentColor))
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
                is ProfileUiState.Loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = ThemeManager.parseColor(theme.accentColor))
                }
                is ProfileUiState.Error -> {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(state.message, color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                uiState = ProfileUiState.Loading
                                scope.launch {
                                    try {
                                        val userId = repository.resolveUserIdFromNode(nodeId)
                                        if (userId.isEmpty()) throw Exception("Не удалось определить ID пользователя")
                                        resolvedUserId = userId
                                        uiState = ProfileUiState.Success(repository.getUserProfile(userId))
                                    } catch (e: Exception) {
                                        uiState = ProfileUiState.Error(e.message ?: "Ошибка")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                        ) { Text("Повторить") }
                    }
                }
                is ProfileUiState.Success -> {
                    ProfileContent(state.profile, assignedLabels, theme, repository, scope, context, navController)
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(
    profile: FunPayUserProfile,
    assignedLabels: List<ChatLabel>,
    theme: AppTheme,
    repository: FunPayRepository,
    scope: CoroutineScope,
    context: Context,
    navController: NavController
) {
    var selectedTab by remember { mutableStateOf(0) }
    var reviews by remember { mutableStateOf(profile.reviews) }

    
    var currentToken by remember { mutableStateOf(profile.continueToken) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var noMoreReviews by remember { mutableStateOf(profile.continueToken == null) }

    val tabs = buildList {
        add("Предложения")
        add("Отзывы (${reviews.size}${if (!noMoreReviews) "+" else ""})")
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ProfileHeader(profile, assignedLabels, theme, context) }

        item {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = ThemeManager.parseColor(theme.surfaceColor),
                contentColor = ThemeManager.parseColor(theme.accentColor),
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 13.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        }

        when (selectedTab) {
            0 -> {
                if (profile.offerGroups.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Text("Нет предложений", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 14.sp)
                        }
                    }
                } else {
                    profile.offerGroups.forEach { group ->
                        item(key = "group_${group.categoryUrl}") {
                            Text(group.categoryName, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = ThemeManager.parseColor(theme.textSecondaryColor),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        items(group.offers, key = { "offer_${it.id}" }) { offer -> OfferRow(offer, theme, context, navController) }
                    }
                }
            }
            1 -> {
                if (reviews.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Text("Нет отзывов", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 14.sp)
                        }
                    }
                } else {
                    itemsIndexed(reviews, key = { index, review -> "review_${index}_${review.date}_${review.text.take(20)}" }) { _, review ->
                        ReviewCard(review, profile, theme, context, scope)
                    }

                    if (!noMoreReviews) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = ThemeManager.parseColor(theme.accentColor),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            if (currentToken == null) return@OutlinedButton
                                            isLoadingMore = true

                                            scope.launch {
                                                try {
                                                    
                                                    val (newReviews, nextToken) = repository.fetchMoreReviews(profile.userId, currentToken!!)

                                                    if (newReviews.isNotEmpty()) {
                                                        reviews = reviews + newReviews
                                                    }

                                                    currentToken = nextToken
                                                    if (nextToken == null || newReviews.isEmpty()) {
                                                        noMoreReviews = true
                                                    }
                                                } catch (_: Exception) {
                                                    noMoreReviews = true
                                                } finally {
                                                    isLoadingMore = false
                                                }
                                            }
                                        },
                                        border = BorderStroke(1.dp, ThemeManager.parseColor(theme.accentColor).copy(0.5f)),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text("Загрузить ещё", color = ThemeManager.parseColor(theme.accentColor), fontSize = 13.sp)
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
private fun ProfileHeader(
    profile: FunPayUserProfile,
    assignedLabels: List<ChatLabel>,
    theme: AppTheme,
    context: Context
) {
    val onlineColor by animateColorAsState(
        if (profile.isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
        animationSpec = tween(500), label = "online"
    )

    Surface(modifier = Modifier.fillMaxWidth(), color = ThemeManager.parseColor(theme.surfaceColor)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box {
                    AsyncImage(
                        model = profile.avatarUrl ?: "https://funpay.com/img/layout/avatar.png",
                        contentDescription = null,
                        modifier = Modifier.size(80.dp).clip(CircleShape)
                            .border(2.dp, ThemeManager.parseColor(theme.accentColor).copy(0.4f), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier.size(16.dp).clip(CircleShape).background(onlineColor)
                            .border(2.dp, ThemeManager.parseColor(theme.surfaceColor), CircleShape)
                            .align(Alignment.BottomEnd)
                    )
                }

                Column(Modifier.weight(1f)) {
                    EpicNicknameText(
                        text = profile.username,
                        style = LocalTextStyle.current.copy(
                            fontSize = 22.sp,
                            color = ThemeManager.parseColor(theme.textPrimaryColor)
                        )
                    )
                    Text(if (profile.isOnline) "Онлайн" else "Офлайн", fontSize = 13.sp, color = onlineColor)
                    if (profile.registrationDate != null) {
                        Text(profile.registrationDate, fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                    }
                }
            }

            if (assignedLabels.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(assignedLabels) { label -> LabelChip(label = label, theme = theme) }
                }
            }

            if (profile.ratingValue != null || profile.reviewCountShort != null) {
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (profile.ratingValue != null) {
                        StatChip(Icons.Default.Star, "${profile.ratingValue}/5", "Рейтинг", theme, Color(0xFFFFC107), Modifier.weight(1f))
                    }
                    if (profile.reviewCountShort != null) {
                        StatChip(Icons.Default.RateReview, profile.reviewCountShort, "Отзывы", theme, ThemeManager.parseColor(theme.accentColor), Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://funpay.com/users/${profile.userId}/"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Открыть в браузере")
            }
        }
    }
}

@Composable
private fun LabelChip(label: ChatLabel, theme: AppTheme) {
    val bgColor = try { Color(android.graphics.Color.parseColor(label.color)) }
    catch (e: Exception) { ThemeManager.parseColor(theme.accentColor) }

    Surface(shape = RoundedCornerShape(20.dp), color = bgColor.copy(alpha = 0.15f), border = BorderStroke(1.dp, bgColor.copy(alpha = 0.5f))) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(bgColor))
            Text(label.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = bgColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, theme: AppTheme, iconTint: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp),
        color = ThemeManager.parseColor(theme.backgroundColor).copy(alpha = 0.5f),
        border = BorderStroke(1.dp, ThemeManager.parseColor(theme.textSecondaryColor).copy(0.15f))) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            Column {
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, fontSize = 10.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
            }
        }
    }
}

@Composable
private fun OfferRow(offer: ProfileOffer, theme: AppTheme, context: Context, navController: NavController) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clickable {
            val lotId = Regex("[?&]id=([A-Za-z0-9\\-]+)").find(offer.url)?.groupValues?.get(1)
            if (!lotId.isNullOrBlank()) {
                navController.navigate("lot/$lotId")
            } else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(offer.url))
                context.startActivity(intent)
            }
        },
        shape = RoundedCornerShape(10.dp),
        color = ThemeManager.parseColor(theme.surfaceColor)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(offer.title, fontSize = 13.sp, color = ThemeManager.parseColor(theme.textPrimaryColor), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                if (offer.server != null) {
                    Text(offer.server, fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor), modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(offer.price, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.accentColor))

            IconButton(
                onClick = {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(offer.url))) } catch (_: Exception) {}
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.OpenInBrowser, null,
                    tint = ThemeManager.parseColor(theme.textSecondaryColor),
                    modifier = Modifier.size(16.dp))
            }
            Icon(Icons.Default.ChevronRight, null, tint = ThemeManager.parseColor(theme.textSecondaryColor), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ReviewCard(
    review: ProfileReview,
    profile: FunPayUserProfile,
    theme: AppTheme,
    context: Context,
    scope: CoroutineScope
) {
    
    var clickCount by remember { mutableIntStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }
    var submitState by remember { mutableStateOf(SubmitState.IDLE) }
    var errorMsg by remember { mutableStateOf("") }

    val starColor = when (review.rating) {
        5 -> Color(0xFF4CAF50); 4 -> Color(0xFF8BC34A); 3 -> Color(0xFFFFC107); 2 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                if (submitState != SubmitState.LOADING) {
                    showDialog = false
                    clickCount = 0
                    submitState = SubmitState.IDLE
                }
            },
            title = {
                Text(
                    text = when (submitState) {
                        SubmitState.IDLE -> "Смешной отзыв?"
                        SubmitState.LOADING -> "Отправка..."
                        SubmitState.SUCCESS -> "Готово!"
                        SubmitState.ERROR -> "Ошибка"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                when (submitState) {
                    SubmitState.IDLE -> Text("Добавить этот отзыв в публичную ленту смешных отзывов на сайт funpay.tools/feed ?\n\n(Только 1 отзыв в день)")
                    SubmitState.LOADING -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ThemeManager.parseColor(theme.accentColor))
                        }
                    }
                    SubmitState.SUCCESS -> Text("Отзыв успешно добавлен в публичную ленту! Вы можете посмотреть его прямо сейчас.")
                    SubmitState.ERROR -> Text(errorMsg)
                }
            },
            confirmButton = {
                if (submitState == SubmitState.IDLE) {
                    TextButton(
                        onClick = {
                            submitState = SubmitState.LOADING
                            scope.launch {
                                val result = sendReviewToFeed(context, profile, review)
                                withContext(Dispatchers.Main) {
                                    if (result.isSuccess) {
                                        submitState = SubmitState.SUCCESS
                                    } else {
                                        errorMsg = result.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                                        submitState = SubmitState.ERROR
                                    }
                                }
                            }
                        }
                    ) { Text("Добавить", color = ThemeManager.parseColor(theme.accentColor)) }
                } else if (submitState == SubmitState.SUCCESS) {
                    Button(
                        onClick = {
                            showDialog = false
                            clickCount = 0
                            submitState = SubmitState.IDLE
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://funpay.tools/feed"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                    ) { Text("Открыть сайт") }
                }
            },
            dismissButton = {
                if (submitState == SubmitState.IDLE || submitState == SubmitState.ERROR) {
                    TextButton(onClick = {
                        showDialog = false
                        clickCount = 0
                        submitState = SubmitState.IDLE
                    }) { Text("Закрыть", color = ThemeManager.parseColor(theme.textSecondaryColor)) }
                }
            },
            containerColor = ThemeManager.parseColor(theme.surfaceColor),
            titleContentColor = ThemeManager.parseColor(theme.textPrimaryColor),
            textContentColor = ThemeManager.parseColor(theme.textSecondaryColor)
        )
    }

    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp), color = ThemeManager.parseColor(theme.surfaceColor)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { i ->
                        Icon(
                            if (i < review.rating) Icons.Default.Star else Icons.Default.StarBorder, null,
                            tint = if (i < review.rating) starColor else ThemeManager.parseColor(theme.textSecondaryColor).copy(0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(review.date, fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))

                    IconButton(
                        onClick = {
                            clickCount++
                            if (clickCount >= 2) {
                                showDialog = true
                            } else {
                                Toast.makeText(context, "Нажмите ещё раз, чтобы добавить в ленту", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mood,
                            contentDescription = "В ленту",
                            tint = ThemeManager.parseColor(theme.accentColor),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (review.detail != null) {
                Text(review.detail, fontSize = 11.sp, color = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.padding(top = 4.dp))
            }
            if (review.text.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(review.text, fontSize = 13.sp, color = ThemeManager.parseColor(theme.textPrimaryColor), lineHeight = 18.sp)
            }

            if (review.sellerReply != null) {
                Spacer(Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Ответ продавца:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.accentColor))
                        Spacer(Modifier.height(4.dp))
                        Text(review.sellerReply, fontSize = 12.sp, color = ThemeManager.parseColor(theme.textPrimaryColor), lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}