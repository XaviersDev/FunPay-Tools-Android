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

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class RMTUserStats(
    val user: RMTUser,
    val stats: RMTStatsData
)

data class RMTUser(
    val id: Int,
    val username: String,
    @SerializedName("avatar_photo") val avatarPhoto: String?,
    val banned: Boolean
)

data class RMTStatsData(
    val totalAmount: Double,
    val totalReviews: Int,
    val averagePerReview: Double,
    val gamesPlayed: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RMTHubScreen(
    navController: NavController,
    currentTheme: AppTheme
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<RMTUserStats?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Глобальный поиск",
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ThemeManager.parseColor(currentTheme.surfaceColor)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = ThemeManager.parseColor(currentTheme.surfaceColor)
                ),
                shape = RoundedCornerShape(currentTheme.borderRadius.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Поиск по базе RMTHub",
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Введите точный никнейм пользователя FunPay",
                        color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Никнейм...", color = ThemeManager.parseColor(currentTheme.textSecondaryColor)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (query.isNotBlank()) {
                                focusManager.clearFocus()
                                isLoading = true
                                error = null
                                data = null
                                scope.launch {
                                    val res = fetchRMTHubStats(query)
                                    isLoading = false
                                    if (res.first != null) {
                                        data = res.first
                                    } else {
                                        error = res.second ?: "Пользователь не найден или ошибка сети"
                                    }
                                }
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                            unfocusedTextColor = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                            focusedBorderColor = ThemeManager.parseColor(currentTheme.accentColor),
                            cursorColor = ThemeManager.parseColor(currentTheme.accentColor)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (query.isNotBlank()) {
                                focusManager.clearFocus()
                                isLoading = true
                                error = null
                                data = null
                                scope.launch {
                                    val res = fetchRMTHubStats(query)
                                    isLoading = false
                                    if (res.first != null) {
                                        data = res.first
                                    } else {
                                        error = res.second ?: "Пользователь не найден"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(currentTheme.accentColor)),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Найти")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFB00020).copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error!!,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp
                    )
                }
            }

            if (data != null) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    

                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = ThemeManager.parseColor(currentTheme.surfaceColor).copy(alpha = currentTheme.containerOpacity)
                            ),
                            shape = RoundedCornerShape(currentTheme.borderRadius.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = ThemeManager.parseColor(currentTheme.accentColor),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))

                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        data!!.user.username,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor)
                                    )
                                    Text(
                                        "ID: ${data!!.user.id}",
                                        fontSize = 12.sp,
                                        color = ThemeManager.parseColor(currentTheme.textSecondaryColor)
                                    )
                                    if (data!!.user.banned) {
                                        Text(
                                            "ЗАБЛОКИРОВАН",
                                            color = Color.Red,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                
                                IconButton(
                                    onClick = {
                                        val url = "https://funpay.com/users/${data!!.user.id}/"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew, 
                                        contentDescription = "Открыть профиль FunPay",
                                        tint = ThemeManager.parseColor(currentTheme.accentColor)
                                    )
                                }
                                
                            }
                        }
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatsCard(
                                title = "Общий доход",
                                value = "$${data!!.stats.totalAmount}",
                                icon = Icons.Default.AttachMoney,
                                color = Color(0xFF4CAF50),
                                theme = currentTheme,
                                modifier = Modifier.weight(1f)
                            )
                            StatsCard(
                                title = "Ср. чек",
                                value = "$${data!!.stats.averagePerReview}",
                                icon = Icons.Default.TrendingUp,
                                color = Color(0xFF2196F3),
                                theme = currentTheme,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatsCard(
                                title = "Отзывов",
                                value = "${data!!.stats.totalReviews}",
                                icon = Icons.Default.Star,
                                color = Color(0xFFFFC107),
                                theme = currentTheme,
                                modifier = Modifier.weight(1f)
                            )
                            StatsCard(
                                title = "Игр",
                                value = "${data!!.stats.gamesPlayed}",
                                icon = Icons.Default.Gamepad,
                                color = Color(0xFF9C27B0),
                                theme = currentTheme,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                val url = "https://rmthub.com/ru/funpay/${data!!.user.id}"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF288CD7)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Подробная статистика на RMTHub.com")
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Данные предоставлены сервисом RMTHub",
                            fontSize = 10.sp,
                            color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatsCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    theme: AppTheme,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = theme.containerOpacity)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                fontSize = 12.sp,
                color = ThemeManager.parseColor(theme.textSecondaryColor)
            )
            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ThemeManager.parseColor(theme.textPrimaryColor)
            )
        }
    }
}

suspend fun fetchRMTHubStats(username: String): Pair<RMTUserStats?, String?> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://rmthub.com/api/user/${username}?locale=ru")
                .header("User-Agent", "FunPayToolsApp/Android")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                return@withContext Pair(null, "Ошибка сервера RMTHub: ${response.code}")
            }

            val json = JSONObject(body)

            if (json.has("error")) {
                return@withContext Pair(null, json.optString("error", "Пользователь не найден"))
            }

            if (!json.has("user") || !json.has("stats")) {
                return@withContext Pair(null, "Некорректный ответ от RMTHub")
            }

            val userJson = json.getJSONObject("user")
            val statsJson = json.getJSONObject("stats")

            val user = RMTUser(
                id = userJson.getInt("id"),
                username = userJson.getString("username"),
                avatarPhoto = userJson.optString("avatar_photo", ""),
                banned = userJson.optBoolean("banned", false)
            )

            val stats = RMTStatsData(
                totalAmount = statsJson.optDouble("totalAmount", 0.0),
                totalReviews = statsJson.optInt("totalReviews", 0),
                averagePerReview = statsJson.optDouble("averagePerReview", 0.0),
                gamesPlayed = statsJson.optInt("gamesPlayed", 0)
            )

            return@withContext Pair(RMTUserStats(user, stats), null)

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Pair(null, "Ошибка сети: ${e.message}")
        }
    }
}