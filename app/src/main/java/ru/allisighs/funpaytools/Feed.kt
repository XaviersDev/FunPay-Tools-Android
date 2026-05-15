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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup


data class FeedReviewItem(
    val id: String,
    val sellerName: String,
    val sellerUrl: String,
    val reviewerName: String,
    val reviewText: String,
    val rating: Int,
    val timeAgo: String,
    val gameName: String,
    val price: String,
    val sellerResponse: String,
    val reactions: Map<String, Int>
)


@Composable
fun FeedScreen(
    navController: NavController,
    repository: FunPayRepository,
    theme: AppTheme
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var feedItems by remember { mutableStateOf<List<FeedReviewItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }

    
    var isDarkTheme by remember { mutableStateOf(false) }

    
    fun loadFeed() {
        scope.launch {
            isLoading = true
            errorMsg = null
            val result = fetchFeedFromBackend()
            result.onSuccess {
                feedItems = it
            }.onFailure {
                errorMsg = it.message ?: "Ошибка сети"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadFeed() }

    if (showAddDialog) {
        AddReviewToFeedDialog(
            isDarkTheme = isDarkTheme,
            repository = repository,
            onDismiss = { showAddDialog = false },
            onReviewAdded = {
                showAddDialog = false
                loadFeed()
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent 
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {

            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ThemeManager.parseColor(theme.textPrimaryColor))
                }

                Spacer(Modifier.weight(1f))

                
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить", tint = ThemeManager.parseColor(theme.accentColor))
                }

                
                IconButton(onClick = { loadFeed() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = ThemeManager.parseColor(theme.textPrimaryColor))
                }

                
                IconButton(onClick = { isDarkTheme = !isDarkTheme }) {
                    Icon(
                        if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Сменить тему",
                        tint = ThemeManager.parseColor(theme.textPrimaryColor)
                    )
                }
            }

            
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = ThemeManager.parseColor(theme.accentColor))
                    }
                    errorMsg != null -> {
                        Column(modifier = Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(errorMsg!!, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 14.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { loadFeed() }, colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))) {
                                Text("Повторить")
                            }
                        }
                    }
                    feedItems.isEmpty() -> {
                        Text("Лента пока пуста. Станьте первым!", color = ThemeManager.parseColor(theme.textSecondaryColor), modifier = Modifier.align(Alignment.Center))
                    }
                    else -> {
                        
                        val pagerState = rememberPagerState(pageCount = { feedItems.size })

                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val item = feedItems[page]

                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                FeedReviewScreenshotCard(item, isDarkTheme, context) { emoji ->
                                    
                                    val newReactions = item.reactions.toMutableMap()
                                    val currentCount = newReactions[emoji] ?: 0
                                    newReactions[emoji] = currentCount + 1

                                    val updatedItem = item.copy(reactions = newReactions)
                                    feedItems = feedItems.map { if (it.id == item.id) updatedItem else it }

                                    
                                    scope.launch {
                                        val success = reactToReviewOnBackend(item.id, emoji, context, repository)
                                        if (!success) {
                                            
                                            val revertedReactions = item.reactions.toMutableMap()
                                            revertedReactions[emoji] = currentCount
                                            val revertedItem = item.copy(reactions = revertedReactions)
                                            feedItems = feedItems.map { if (it.id == item.id) revertedItem else it }
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
}


@Composable
fun FeedReviewScreenshotCard(
    review: FeedReviewItem,
    isDarkTheme: Boolean,
    context: Context,
    onReact: (String) -> Unit
) {
    val cardBg = if (isDarkTheme) Color(0xFF1A1A1A) else Color.White
    val textPrimary = if (isDarkTheme) Color(0xFFE0E0E0) else Color(0xFF333333)
    val textSecondary = if (isDarkTheme) Color(0xFFAAAAAA) else Color(0xFF888888)
    val linkColor = if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF288CD7)
    val replyBg = if (isDarkTheme) Color(0xFF242424) else Color(0xFFF5F5F5)

    Card(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            
            Row(verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = "https://funpay.com/img/layout/avatar.png",
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        review.reviewerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = linkColor
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        review.timeAgo,
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                    if (review.gameName.isNotEmpty() || review.price.isNotEmpty()) {
                        Text(
                            "${review.gameName}, ${review.price}",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                }
                
                Row {
                    val starColor = when (review.rating) {
                        5 -> Color(0xFFF6A821); 4 -> Color(0xFFF6A821); 3 -> Color(0xFFFFC107); 2 -> Color(0xFFFF9800); else -> Color(0xFFF44336)
                    }
                    repeat(5) { i ->
                        Text(
                            text = if (i < review.rating) "★" else "☆",
                            color = if (i < review.rating) starColor else textSecondary.copy(alpha = 0.5f),
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            
            Text(
                review.reviewText.replace("<br>", "\n"),
                color = textPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            
            if (review.sellerResponse.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Ответ продавца", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(replyBg)
                        .padding(12.dp)
                ) {
                    Text(
                        review.sellerResponse.replace("<br>", "\n"),
                        color = textPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = textSecondary.copy(alpha = 0.2f))
            Spacer(Modifier.height(12.dp))

            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Продавец: ${review.sellerName}",
                    color = linkColor,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(review.sellerUrl))) } catch (_: Exception) {}
                    }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val emojis = listOf("😂", "💖", "💩")
                    emojis.forEach { emoji ->
                        val count = review.reactions[emoji] ?: 0
                        Surface(
                            color = if (isDarkTheme) Color(0xFF333333) else Color(0xFFEEEEEE),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { onReact(emoji) }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(emoji, fontSize = 14.sp)
                                if (count > 0) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(count.toString(), color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReviewToFeedDialog(
    isDarkTheme: Boolean,
    repository: FunPayRepository,
    onDismiss: () -> Unit,
    onReviewAdded: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val dialogBg = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val textPrimary = if (isDarkTheme) Color.White else Color.Black
    val textSecondary = if (isDarkTheme) Color(0xFFAAAAAA) else Color(0xFF757575)
    val accentColor = Color(0xFF2196F3)

    var step by remember { mutableIntStateOf(1) } 

    var searchUsername by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var foundUserId by remember { mutableStateOf("") }
    var foundUserName by remember { mutableStateOf("") }
    var loadedReviews by remember { mutableStateOf<List<ProfileReview>>(emptyList()) }
    var reviewContinueToken by remember { mutableStateOf<String?>(null) }
    var isLoadingReviews by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            colors = CardDefaults.cardColors(containerColor = dialogBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (step == 2 && !isSubmitting) {
                        IconButton(onClick = { step = 1; loadedReviews = emptyList() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textPrimary)
                        }
                    }
                    Text(
                        if (step == 1) "Добавить в ленту" else "Выберите отзыв",
                        fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = textPrimary)
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (step == 1) {
                    
                    Text("Чей отзыв вы хотите добавить?", color = textPrimary, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = searchUsername,
                        onValueChange = { searchUsername = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Никнейм продавца на FunPay", color = textSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        )
                    )
                    if (searchError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(searchError!!, color = Color.Red, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Поиск работает через базу данных RMTHub.com", color = textSecondary.copy(0.6f), fontSize = 10.sp)

                    Spacer(Modifier.weight(1f))

                    Button(
                        onClick = {
                            if (searchUsername.isNotBlank()) {
                                isSearching = true
                                searchError = null
                                focusManager.clearFocus()
                                scope.launch {
                                    val rmtRes = fetchRMTHubStats(searchUsername.trim())
                                    if (rmtRes.first != null) {
                                        foundUserId = rmtRes.first!!.user.id.toString()
                                        foundUserName = rmtRes.first!!.user.username

                                        val firstPageRes = fetchFeedFirstPageReviews(repository, foundUserId)
                                        if (firstPageRes.first.isNotEmpty()) {
                                            loadedReviews = firstPageRes.first
                                            reviewContinueToken = firstPageRes.second
                                            step = 2
                                        } else {
                                            searchError = "У этого продавца нет отзывов."
                                        }
                                    } else {
                                        searchError = rmtRes.second ?: "Пользователь не найден"
                                    }
                                    isSearching = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !isSearching && searchUsername.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        else Text("Найти отзывы продавца", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                else if (step == 2) {
                    
                    Text("Отзывы продавца $foundUserName:", color = textPrimary, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (isSubmitting) {
                            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = accentColor)
                                Spacer(Modifier.height(16.dp))
                                Text("Отправляем в ленту...", color = textPrimary)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(loadedReviews) { review ->
                                    FeedSelectableReviewCard(review, isDarkTheme) {
                                        isSubmitting = true
                                        scope.launch {
                                            val res = submitReviewToFeedBackend(
                                                context = context,
                                                sellerId = foundUserId,
                                                sellerName = foundUserName,
                                                review = review
                                            )
                                            if (res.isSuccess) {
                                                Toast.makeText(context, "Шедевр добавлен в ленту!", Toast.LENGTH_SHORT).show()
                                                onReviewAdded()
                                            } else {
                                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Ошибка", Toast.LENGTH_LONG).show()
                                                isSubmitting = false
                                            }
                                        }
                                    }
                                }

                                if (reviewContinueToken != null) {
                                    item {
                                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                            if (isLoadingReviews) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = accentColor)
                                            } else {
                                                OutlinedButton(
                                                    onClick = {
                                                        isLoadingReviews = true
                                                        scope.launch {
                                                            val nextRes = fetchFeedNextPageReviews(repository, foundUserId, reviewContinueToken!!)
                                                            if (nextRes.first.isNotEmpty()) {
                                                                loadedReviews = loadedReviews + nextRes.first
                                                            }
                                                            reviewContinueToken = nextRes.second
                                                            isLoadingReviews = false
                                                        }
                                                    },
                                                    border = BorderStroke(1.dp, accentColor.copy(0.5f))
                                                ) {
                                                    Text("Загрузить более старые", color = accentColor)
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
        }
    }
}

@Composable
fun FeedSelectableReviewCard(review: ProfileReview, isDarkTheme: Boolean, onClick: () -> Unit) {
    val bg = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFF5F5F5)
    val textPrimary = if (isDarkTheme) Color.White else Color.Black
    val textSecondary = if (isDarkTheme) Color(0xFFAAAAAA) else Color(0xFF888888)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, textSecondary.copy(0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val starColor = when (review.rating) {
                    5 -> Color(0xFFF6A821); 4 -> Color(0xFFF6A821); 3 -> Color(0xFFFFC107); 2 -> Color(0xFFFF9800); else -> Color(0xFFF44336)
                }
                repeat(5) { i -> Icon(if (i < review.rating) Icons.Default.Star else Icons.Default.StarBorder, null, tint = starColor, modifier = Modifier.size(12.dp)) }
                Spacer(Modifier.width(8.dp))
                Text(review.date, fontSize = 10.sp, color = textSecondary)
            }
            if (review.detail != null) {
                Spacer(Modifier.height(4.dp))
                Text(review.detail, fontSize = 10.sp, color = Color(0xFF2196F3))
            }
            Spacer(Modifier.height(6.dp))
            Text(review.text.ifEmpty { "Без текста" }, fontSize = 13.sp, color = textPrimary, maxLines = 4, overflow = TextOverflow.Ellipsis)

            if (review.sellerReply != null) {
                Spacer(Modifier.height(8.dp))
                Text("Ответ:", color = textSecondary, fontSize = 10.sp)
                Text(review.sellerReply, fontSize = 11.sp, color = textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}






private suspend fun fetchFeedFromBackend(): Result<List<FeedReviewItem>> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url("https://funpay.tools/api/reviews").build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Пустой ответ от сервера")

            if (!response.isSuccessful) throw Exception("Ошибка сервера: ${response.code}")

            val jsonArray = JSONArray(body)
            val list = mutableListOf<FeedReviewItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                
                val reactionsMap = mutableMapOf<String, Int>()
                if (obj.has("reactions") && !obj.isNull("reactions")) {
                    val reactionsArr = obj.getJSONArray("reactions")
                    for (j in 0 until reactionsArr.length()) {
                        val emoji = reactionsArr.getJSONObject(j).optString("emoji", "")
                        if (emoji.isNotEmpty()) reactionsMap[emoji] = (reactionsMap[emoji] ?: 0) + 1
                    }
                }

                list.add(FeedReviewItem(
                    id = obj.optString("id", ""),
                    sellerName = obj.optString("seller_name", "Продавец"),
                    sellerUrl = obj.optString("seller_url", ""),
                    reviewerName = obj.optString("reviewer_name", "Аноним"),
                    reviewText = obj.optString("review_text", ""),
                    rating = obj.optInt("rating", 5),
                    timeAgo = obj.optString("time_ago", "Недавно"),
                    gameName = obj.optString("game_name", ""),
                    price = obj.optString("price", ""),
                    sellerResponse = obj.optString("seller_response", ""),
                    reactions = reactionsMap
                ))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private suspend fun submitReviewToFeedBackend(context: Context, sellerId: String, sellerName: String, review: ProfileReview): Result<String> {
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
                put("seller_name", sellerName)
                put("seller_url", "https://funpay.com/users/$sellerId/")
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

            val response = OkHttpClient().newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Result.success("Успех")
            } else {
                val errorMsg = try { JSONObject(responseBody).getString("error") } catch (e: Exception) { "Ошибка ${response.code}" }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Сеть: ${e.message}"))
        }
    }
}


private suspend fun reactToReviewOnBackend(
    reviewId: String,
    emoji: String,
    context: Context,
    repository: FunPayRepository
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"

            
            val myProfile = try { repository.getSelfProfileUpdated() } catch (e: Exception) { null }

            
            val myId = myProfile?.id ?: repository.getMyUserId()
            val myName = myProfile?.username ?: "Аноним"
            val myUrl = if (myId.isNotEmpty() && myId != "0") "https://funpay.com/users/$myId/" else "#"

            val json = JSONObject().apply {
                put("emoji", emoji)
                put("device_id", androidId)
                put("review_id", reviewId)
                put("user_name", myName)
                put("user_url", myUrl)
            }

            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://funpay.tools/api/react")
                .post(body)
                .build()

            OkHttpClient().newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}


private suspend fun fetchFeedFirstPageReviews(repository: FunPayRepository, userId: String): Pair<List<ProfileReview>, String?> {
    return withContext(Dispatchers.IO) {
        try {
            val cookie = "golden_key=${repository.getGoldenKey()}; PHPSESSID=${repository.getPhpSessionId()}"
            val request = Request.Builder()
                .url("https://funpay.com/users/$userId/")
                .header("Cookie", cookie)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val html = OkHttpClient().newCall(request).execute().body?.string() ?: return@withContext Pair(emptyList(), null)
            val doc = Jsoup.parse(html)
            val continueToken = doc.select("input[name=continue]").firstOrNull()?.attr("value")?.ifEmpty { null }
            Pair(feedParseReviewsFromHtml(html), continueToken)
        } catch (e: Exception) {
            Pair(emptyList(), null)
        }
    }
}

private suspend fun fetchFeedNextPageReviews(repository: FunPayRepository, userId: String, continueToken: String): Pair<List<ProfileReview>, String?> {
    return withContext(Dispatchers.IO) {
        try {
            val cookie = "golden_key=${repository.getGoldenKey()}; PHPSESSID=${repository.getPhpSessionId()}"
            val requestBody = FormBody.Builder().add("user_id", userId).add("continue", continueToken).add("filter", "").build()
            val request = Request.Builder()
                .url("https://funpay.com/users/reviews")
                .header("Cookie", cookie)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("X-Requested-With", "XMLHttpRequest")
                .post(requestBody)
                .build()

            val html = OkHttpClient().newCall(request).execute().body?.string() ?: return@withContext Pair(emptyList(), null)
            val doc = Jsoup.parse(html)
            val nextToken = doc.select("input[name=continue]").firstOrNull()?.attr("value")?.ifEmpty { null }
            Pair(feedParseReviewsFromHtml(html), nextToken)
        } catch (e: Exception) {
            Pair(emptyList(), null)
        }
    }
}

private fun feedParseReviewsFromHtml(html: String): List<ProfileReview> {
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