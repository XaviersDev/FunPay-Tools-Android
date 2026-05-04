package ru.allisighs.funpaytools

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.Calendar
import kotlin.math.max


private fun parseFunPayDateToMs(dateStr: String): Long {
    try {
        val now = Calendar.getInstance()
        val str = dateStr.lowercase().trim()
        val timePart = Regex("(\\d{1,2}):(\\d{2})").find(str)
        val h = timePart?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val m = timePart?.groupValues?.get(2)?.toIntOrNull() ?: 0

        if (str.contains("сегодня")) {
            now.set(Calendar.HOUR_OF_DAY, h)
            now.set(Calendar.MINUTE, m)
            now.set(Calendar.SECOND, 0)
            return now.timeInMillis
        }
        if (str.contains("вчера")) {
            now.add(Calendar.DAY_OF_YEAR, -1)
            now.set(Calendar.HOUR_OF_DAY, h)
            now.set(Calendar.MINUTE, m)
            now.set(Calendar.SECOND, 0)
            return now.timeInMillis
        }

        val months = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
        var monthIdx = -1
        for (i in months.indices) {
            if (str.contains(months[i])) {
                monthIdx = i
                break
            }
        }

        val dayMatch = Regex("(\\d{1,2})\\s+[а-я]+").find(str)
        val d = dayMatch?.groupValues?.get(1)?.toIntOrNull() ?: now.get(Calendar.DAY_OF_MONTH)

        val yearMatch = Regex("(\\d{4})").find(str)
        val y = yearMatch?.groupValues?.get(1)?.toIntOrNull() ?: now.get(Calendar.YEAR)

        if (monthIdx != -1) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, y)
            cal.set(Calendar.MONTH, monthIdx)
            cal.set(Calendar.DAY_OF_MONTH, d)
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, m)
            cal.set(Calendar.SECOND, 0)
            return cal.timeInMillis
        }
    } catch (e: Exception) {}
    return 0L
}

private fun formatTimeLeft(ms: Long): String {
    val totalMins = max(0L, ms / 60000)
    val h = totalMins / 60
    val m = totalMins % 60
    return if (h > 0) "$h ч $m мин" else "$m мин"
}

@Composable
fun ProfileScreen(
    repository: FunPayRepository,
    theme: AppTheme,
    onOpenTariffs: () -> Unit,
    onOpenLots: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    
    var localSales by remember { mutableStateOf(repository.cachedSales) }

    val activeAccount = repository.getActiveAccount()
    val accountKey = activeAccount?.id ?: "none"

    
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60000)
            currentTime = System.currentTimeMillis()
        }
    }

    LaunchedEffect(accountKey) {
        isLoading = true
        try {
            profile = repository.getSelfProfileUpdated()
        } catch (e: Exception) {
            profile = null
        } finally {
            isLoading = false
        }

        
        if (!repository.isSalesFullLoaded) {
            withContext(Dispatchers.IO) {
                var token: String? = null
                
                val buf = repository.cachedSales.toMutableList()

                while (true) {
                    val result = try { repository.fetchSalesPage(token) } catch (e: Exception) { null }
                    if (result == null) break

                    val page = result.first
                    val next = result.second

                    val existingIds = buf.map { it.orderId }.toSet()
                    val newOrders = page.filter { it.orderId !in existingIds }

                    
                    if (newOrders.isEmpty() && page.isNotEmpty()) {
                        repository.isSalesFullLoaded = true
                        break
                    }

                    buf.addAll(newOrders)
                    token = next

                    
                    val newList = buf.toList()
                    repository.cachedSales = newList
                    localSales = newList

                    
                    if (next == null) {
                        repository.isSalesFullLoaded = true
                        break
                    }

                    delay(400) 
                }
            }
        } else {
            
            localSales = repository.cachedSales
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = ThemeManager.parseColor(theme.accentColor)
            )
        } else if (profile == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = ThemeManager.parseColor(theme.textSecondaryColor), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Не удалось загрузить профиль", color = ThemeManager.parseColor(theme.textSecondaryColor))
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try { profile = repository.getSelfProfileUpdated() } catch (e: Exception) { profile = null } finally { isLoading = false }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                ) { Text("Повторить") }
            }
        } else {
            val user = profile!!

            
            val totalBalanceVal = user.totalBalance.replace(Regex("[^0-9.,]"), "").replace(",", ".").toDoubleOrNull() ?: 0.0

            val frozenSales = localSales
                .filter { it.status == "closed" }
                .mapNotNull { sale ->
                    val saleTime = parseFunPayDateToMs(sale.date)
                    if (saleTime > 0) {
                        val unlockTime = saleTime + 48L * 3600L * 1000L
                        if (currentTime < unlockTime) Pair(sale, unlockTime) else null
                    } else null
                }
                .sortedBy { it.second }

            val frozenSum = frozenSales.sumOf { it.first.priceValue }
            val activeSum = max(0.0, totalBalanceVal - frozenSum)
            val nextUnlock = frozenSales.firstOrNull()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
                    shape = RoundedCornerShape(theme.borderRadius.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.4f), CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset(x = (-2).dp, y = (-2).dp)
                                    .clip(CircleShape)
                                    .background(if (user.isOnline) Color(0xFF00C853) else Color.Gray)
                                    .border(2.dp, ThemeManager.parseColor(theme.surfaceColor), CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                user.username,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeManager.parseColor(theme.textPrimaryColor),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${user.rating} (${user.reviewCount} отзывов)", fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(user.registeredDate, fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.8f))
                        }
                    }
                }

                
                DetailedBalanceCard(
                    totalBalance = user.totalBalance,
                    totalVal = totalBalanceVal,
                    activeSum = activeSum,
                    frozenSum = frozenSum,
                    nextUnlock = nextUnlock,
                    currentTime = currentTime,
                    theme = theme
                )

                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionGridItem(
                        modifier = Modifier.weight(1f),
                        title = "Тарифы",
                        subtitle = "Доступ к PRO",
                        icon = Icons.Default.Diamond,
                        iconTint = Color(0xFFFFD700),
                        bgGradient = listOf(Color(0xFF3E2723), Color(0xFF261A15)),
                        theme = theme,
                        onClick = onOpenTariffs
                    )
                    ActionGridItem(
                        modifier = Modifier.weight(1f),
                        title = "Мои лоты",
                        subtitle = "Управление",
                        icon = Icons.Default.Inventory,
                        iconTint = ThemeManager.parseColor(theme.accentColor),
                        bgGradient = listOf(ThemeManager.parseColor(theme.surfaceColor), ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.8f)),
                        theme = theme,
                        onClick = onOpenLots
                    )
                }

                
                DonateEasterEggButton(theme = theme)

                
                Text(
                    "Статистика",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.parseColor(theme.textPrimaryColor),
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatMiniCard(
                        modifier = Modifier.weight(1f),
                        title = "Продажи",
                        value = "${user.activeSales}",
                        icon = Icons.Default.TrendingUp,
                        theme = theme
                    )
                    StatMiniCard(
                        modifier = Modifier.weight(1f),
                        title = "Покупки",
                        value = "${user.activePurchases}",
                        icon = Icons.Default.ShoppingCart,
                        theme = theme
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выйти из аккаунта", color = Color(0xFFE53935), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun DetailedBalanceCard(
    totalBalance: String,
    totalVal: Double,
    activeSum: Double,
    frozenSum: Double,
    nextUnlock: Pair<FunPayRepository.SaleItem, Long>?,
    currentTime: Long,
    theme: AppTheme
) {
    val accentColor = ThemeManager.parseColor(theme.accentColor)
    val surfaceColor = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(theme.borderRadius.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Общий баланс", fontSize = 14.sp, color = textSecondary)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (totalVal <= 0.0) "0.00 ₽" else totalBalance,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (totalVal <= 0.0) textSecondary else textPrimary,
                letterSpacing = 0.5.sp
            )

            if (totalVal > 0.0) {
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                            Spacer(Modifier.width(6.dp))
                            Text("Доступно", fontSize = 12.sp, color = textSecondary)
                        }
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f ₽", activeSum),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFA000)))
                            Spacer(Modifier.width(6.dp))
                            Text("В заморозке", fontSize = 12.sp, color = textSecondary)
                        }
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f ₽", frozenSum),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (nextUnlock != null) {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA000).copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.HourglassEmpty, null, tint = Color(0xFFFFA000), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Ближайшая разморозка: ~${String.format(java.util.Locale.US, "%.2f ₽", nextUnlock.first.priceValue)}",
                                    fontSize = 12.sp, color = textPrimary, fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Через ${formatTimeLeft(nextUnlock.second - currentTime)}",
                                    fontSize = 11.sp, color = Color(0xFFFFA000)
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("У вас пока нет средств на балансе. Время сделать пару продаж! 🚀", fontSize = 12.sp, color = textSecondary, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun ActionGridItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    bgGradient: List<Color>,
    theme: AppTheme,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp) 
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, iconTint.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(colors = bgGradient))
        ) {
            
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.05f),
                modifier = Modifier
                    .size(76.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 16.dp, y = 16.dp)
                    .rotate(-15f)
            )

            Column(modifier = Modifier.padding(14.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(title, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 14.sp)
                Text(subtitle, color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.9f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun StatMiniCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    theme: AppTheme
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 16.sp)
                Text(title, color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp)
            }
        }
    }
}

private enum class DonateDialogStep {
    NONE,
    FAKE_LOADING,
    CANCEL_CONFIRM,
    REALLY_SURE,
    CONFESSION
}

@Composable
fun DonateEasterEggButton(theme: AppTheme) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var step by remember { mutableStateOf(DonateDialogStep.NONE) }

    OutlinedButton(
        onClick = {
            step = DonateDialogStep.FAKE_LOADING
            scope.launch {
                delay(3000)
                if (step == DonateDialogStep.FAKE_LOADING) {
                    step = DonateDialogStep.CANCEL_CONFIRM
                }
            }
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF6B35).copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFFFF6B35).copy(alpha = 0.05f),
            contentColor = Color(0xFFFF6B35)
        ),
        enabled = step == DonateDialogStep.NONE
    ) {
        if (step == DonateDialogStep.FAKE_LOADING) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFFFF6B35))
            Spacer(Modifier.width(8.dp))
            Text("Выполняется вывод 500 ₽...", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        } else {
            Icon(Icons.Default.VolunteerActivism, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Вывести 500 ₽ на донат разработчику", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }

    if (step == DonateDialogStep.CANCEL_CONFIRM) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = ThemeManager.parseColor(theme.surfaceColor),
            title = { Text("⚠️ Вывод средств", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Запрос на вывод 500 ₽ принят и поставлен в очередь обработки.", color = ThemeManager.parseColor(theme.textPrimaryColor))
                    Text("Средства будут выведены на личную карту разработчика.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Вы уверены, что хотите продолжить? Отменить вывод?", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Medium)
                }
            },
            confirmButton = {
                TextButton(onClick = { step = DonateDialogStep.REALLY_SURE }) { Text("Нет, продолжить", color = Color(0xFFFF6B35)) }
            },
            dismissButton = {
                TextButton(onClick = { step = DonateDialogStep.CONFESSION }) { Text("Да, отменить", color = ThemeManager.parseColor(theme.textSecondaryColor)) }
            }
        )
    }

    if (step == DonateDialogStep.REALLY_SURE) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = ThemeManager.parseColor(theme.surfaceColor),
            title = { Text("‼️ Подождите", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFFFF6B35)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Вы действительно хотите перевести 500 ₽ разработчику?", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold)
                    Text("Сумма: 500 ₽\nПолучатель: 4441********7711\nСрок зачисления: 47 часов и 59 минут\nОтмена после подтверждения: невозможна", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Вы точно уверены?", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Medium)
                }
            },
            confirmButton = {
                Button(onClick = { step = DonateDialogStep.CONFESSION }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))) { Text("Да, подтверждаю", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { step = DonateDialogStep.CONFESSION }) { Text("Нет, отменить", color = ThemeManager.parseColor(theme.textSecondaryColor)) }
            }
        )
    }

    if (step == DonateDialogStep.CONFESSION) {
        AlertDialog(
            onDismissRequest = { step = DonateDialogStep.NONE },
            containerColor = ThemeManager.parseColor(theme.surfaceColor),
            title = { Text("😅 Ладно, признаёмся...", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Никакие деньги никуда не ушли и не уйдут. Это была шутка 🙃", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Medium)
                    Text("Но если вы реально хотите помочь развитию FunPay Tools и делать его лучше вместе с нами - рассмотрите лицензию Pro.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 13.sp)
                    Text("Оплата принимается картами СНГ и криптовалютой.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/fptoolsbot"))
                        context.startActivity(intent)
                        step = DonateDialogStep.NONE
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Узнать про Pro →", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { step = DonateDialogStep.NONE }) { Text("Закрыть", color = ThemeManager.parseColor(theme.textSecondaryColor)) }
            }
        )
    }
}

/**
 * НОВАЯ ФУНКЦИЯ ПАРСИНГА ПРОФИЛЯ
 * Названа getSelfProfileUpdated, чтобы не конфликтовать со старым кодом.
 */
suspend fun FunPayRepository.getSelfProfileUpdated(): UserProfile? {
    return withContext(Dispatchers.IO) {
        try {
            val (csrf, userId) = getCsrfAndId() ?: return@withContext null

            val mainResponse = RetrofitInstance.api.getMainPage(getGoldenKey()?.let { "golden_key=$it; PHPSESSID=${getPhpSessionId()}" } ?: "", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
            val mainHtml = mainResponse.body()?.string() ?: ""
            val mainDoc = Jsoup.parse(mainHtml)

            val balanceText = mainDoc.select(".badge-balance").text()
            val activeSales = mainDoc.select(".badge-trade").text().toIntOrNull() ?: 0
            val activePurchases = mainDoc.select(".badge-orders").text().toIntOrNull() ?: 0

            val profileResponse = RetrofitInstance.api.getUserProfile(userId, getGoldenKey()?.let { "golden_key=$it; PHPSESSID=${getPhpSessionId()}" } ?: "", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
            val profileHtml = profileResponse.body()?.string() ?: ""
            val profileDoc = Jsoup.parse(profileHtml)

            val username = profileDoc.select(".user-link-dropdown .user-link-name").first()?.text()?.trim()
                ?: profileDoc.select("div.media-user-name").first()?.text()
                    ?.replace("Online", "")?.replace("Онлайн", "")?.trim()
                ?: "Unknown"

            val statusText = profileDoc.select(".media-user-status").text()
            val isOnline = statusText.lowercase().contains("онлайн") || statusText.lowercase().contains("online")

            var avatarUrl = "https://funpay.com/img/layout/avatar.png"
            val avatarStyle = profileDoc.select(".avatar-photo, .profile-photo").attr("style")

            if (avatarStyle.contains("url(")) {
                avatarUrl = avatarStyle
                    .substringAfter("url(")
                    .substringBefore(")")
                    .replace("\"", "")
                    .replace("'", "")
                if (avatarUrl.startsWith("/")) avatarUrl = "https://funpay.com$avatarUrl"
            }

            val rating = profileDoc.select(".rating-value .big").first()
                ?.text()?.toDoubleOrNull() ?: 0.0

            val reviewsCount = profileDoc.select(".rating-full-count a").first()
                ?.text()
                ?.filter { it.isDigit() }
                ?.toIntOrNull() ?: 0

            
            val regParam = profileDoc.select(".param-item").firstOrNull {
                it.text().contains("Дата регистрации") || it.text().contains("Registration")
            }
            val regDateRaw = regParam?.select(".text-nowrap")?.first()?.text() ?: ""
            val yearMatch = Regex("\\d{4}").find(regDateRaw)?.value
            val regDate = if (yearMatch != null) "На сайте с $yearMatch года" else regDateRaw.substringBefore(",").ifBlank { "неизвестного" }

            UserProfile(
                id = userId,
                username = username,
                avatarUrl = avatarUrl,
                isOnline = isOnline,
                totalBalance = balanceText,
                activeSales = activeSales,
                activePurchases = activePurchases,
                rating = rating,
                reviewCount = reviewsCount,
                registeredDate = regDate
            )
        } catch (e: Exception) {
            null
        }
    }
}