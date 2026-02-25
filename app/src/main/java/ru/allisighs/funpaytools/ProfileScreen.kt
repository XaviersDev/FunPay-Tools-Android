package ru.allisighs.funpaytools

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    val activeAccount = repository.getActiveAccount()
    val accountKey = activeAccount?.id ?: "none"

    LaunchedEffect(accountKey) {
        isLoading = true
        try {
            profile = repository.getSelfProfile()
        } catch (e: Exception) {
            profile = null
        } finally {
            isLoading = false
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
                Text(
                    "Не удалось загрузить профиль",
                    color = ThemeManager.parseColor(theme.textSecondaryColor)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                profile = repository.getSelfProfile()
                            } catch (e: Exception) {
                                profile = null
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ThemeManager.parseColor(theme.accentColor)
                    )
                ) {
                    Text("Повторить")
                }
            }
        } else {
            val user = profile!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                PremiumTariffButton(onClick = onOpenTariffs)

                MyLotsButton(onClick = onOpenLots, theme = theme)


                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = ThemeManager.parseColor(theme.surfaceColor)
                    ),
                    shape = RoundedCornerShape(theme.borderRadius.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.3f), CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset(x = (-4).dp, y = (-4).dp)
                                    .clip(CircleShape)
                                    .background(if (user.isOnline) Color(0xFF00C853) else Color.Gray)
                                    .border(3.dp, ThemeManager.parseColor(theme.surfaceColor), CircleShape)
                            )
                        }

                        Text(
                            user.username,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = ThemeManager.parseColor(theme.textPrimaryColor),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Text(
                            "ID: ${user.id}",
                            fontSize = 14.sp,
                            color = ThemeManager.parseColor(theme.textSecondaryColor),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Surface(
                            color = ThemeManager.parseColor(theme.backgroundColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "На сайте с ${user.registeredDate}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 12.sp,
                                color = ThemeManager.parseColor(theme.textSecondaryColor)
                            )
                        }
                    }
                }

                AnimatedBalanceCard(user.totalBalance, theme)

                DonateEasterEggButton(theme = theme)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = ThemeManager.parseColor(theme.surfaceColor)
                    ),
                    shape = RoundedCornerShape(theme.borderRadius.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Статистика аккаунта",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ThemeManager.parseColor(theme.textPrimaryColor)
                        )

                        HorizontalDivider(
                            color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.2f),
                            thickness = 1.dp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Assignment,
                                    contentDescription = null,
                                    tint = ThemeManager.parseColor(theme.accentColor),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Незавершённых заказов:",
                                    fontSize = 14.sp,
                                    color = ThemeManager.parseColor(theme.textPrimaryColor)
                                )
                            }
                            Text(
                                "${user.activeSales}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeManager.parseColor(theme.accentColor)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB71C1C)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Logout, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выйти из аккаунта")
                }
            }
        }
    }
}

@Composable
fun PremiumTariffButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFD700), Color(0xFFFFA000), Color(0xFFFFD700))
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2E2E2E),
                                Color(0xFF3E3E3E)
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Diamond,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Тарифы",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700),
                                fontSize = 16.sp
                            )
                            Text(
                                "Получить уважение +",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFFFD700)
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedBalanceCard(balance: String, theme: AppTheme) {
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_waves")


    val wave1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1"
    )


    val wave2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave2"
    )


    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val accentColor = ThemeManager.parseColor(theme.accentColor)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor)
        ),
        shape = RoundedCornerShape(theme.borderRadius.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {

                    val width = size.width
                    val height = size.height


                    val linearBrush = Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.05f),
                            accentColor.copy(alpha = glowAlpha),
                            accentColor.copy(alpha = 0.05f)
                        ),
                        start = Offset(width * wave1Offset, 0f),
                        end = Offset(width * (1 - wave2Offset), height),
                        tileMode = androidx.compose.ui.graphics.TileMode.Mirror
                    )


                    val radialBrush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = Offset(
                            x = width * wave2Offset,
                            y = height * (1 - wave1Offset)
                        ),
                        radius = width * 0.6f
                    )

                    onDrawBehind {

                        drawRect(linearBrush)
                        drawRect(radialBrush)
                    }
                },
            contentAlignment = Alignment.Center
        ) {

            Column(
                modifier = Modifier.padding(vertical = 40.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(44.dp)
                )

                Text(
                    "Баланс",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = ThemeManager.parseColor(theme.textSecondaryColor),
                    letterSpacing = 1.2.sp
                )

                Text(
                    balance,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun MyLotsButton(onClick: () -> Unit, theme: AppTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.5f)
            )
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                ThemeManager.parseColor(theme.surfaceColor),
                                ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Inventory,
                                contentDescription = null,
                                tint = ThemeManager.parseColor(theme.accentColor),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Мои лоты",
                                fontWeight = FontWeight.Bold,
                                color = ThemeManager.parseColor(theme.textPrimaryColor),
                                fontSize = 16.sp
                            )
                            Text(
                                "Управление товарами",
                                fontSize = 12.sp,
                                color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.7f)
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = ThemeManager.parseColor(theme.accentColor)
                    )
                }
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color(0xFFFF6B35).copy(alpha = 0.7f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color(0xFFFF6B35)
        ),
        enabled = step == DonateDialogStep.NONE
    ) {
        if (step == DonateDialogStep.FAKE_LOADING) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color(0xFFFF6B35)
            )
            Spacer(Modifier.width(8.dp))
            Text("Выполняется вывод 500 ₽...", fontSize = 13.sp)
        } else {
            Icon(Icons.Default.VolunteerActivism, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Вывести 500 ₽ на донат разработчику", fontSize = 13.sp)
        }
    }

    
    if (step == DonateDialogStep.CANCEL_CONFIRM) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = ThemeManager.parseColor(theme.surfaceColor),
            title = {
                Text(
                    "⚠️ Вывод средств",
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.parseColor(theme.textPrimaryColor)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Запрос на вывод 500 ₽ принят и поставлен в очередь обработки.",
                        color = ThemeManager.parseColor(theme.textPrimaryColor)
                    )
                    Text(
                        "Средства будут выведены на личную карту разработчика.",
                        color = ThemeManager.parseColor(theme.textSecondaryColor),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Вы уверены, что хотите продолжить? Отменить вывод?",
                        color = ThemeManager.parseColor(theme.textPrimaryColor),
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { step = DonateDialogStep.REALLY_SURE }) {
                    Text("Нет, продолжить вывод", color = Color(0xFFFF6B35))
                }
            },
            dismissButton = {
                TextButton(onClick = { step = DonateDialogStep.CONFESSION }) {
                    Text("Да, отменить", color = ThemeManager.parseColor(theme.textSecondaryColor))
                }
            }
        )
    }

    
    if (step == DonateDialogStep.REALLY_SURE) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = ThemeManager.parseColor(theme.surfaceColor),
            title = {
                Text(
                    "‼️ Подождите",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Color(0xFFFF6B35)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Вы действительно хотите перевести 500 ₽ разработчику?",
                        color = ThemeManager.parseColor(theme.textPrimaryColor),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Сумма: 500 ₽\nПолучатель: 4441********7711\nСрок зачисления: 47 часов и 59 минут\nОтмена после подтверждения: невозможна",
                        color = ThemeManager.parseColor(theme.textSecondaryColor),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Вы точно уверены?",
                        color = ThemeManager.parseColor(theme.textPrimaryColor),
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { step = DonateDialogStep.CONFESSION },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
                ) {
                    Text("Да, подтверждаю", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { step = DonateDialogStep.CONFESSION }) {
                    Text("Нет, отменить", color = ThemeManager.parseColor(theme.textSecondaryColor))
                }
            }
        )
    }

    
    if (step == DonateDialogStep.CONFESSION) {
        AlertDialog(
            onDismissRequest = { step = DonateDialogStep.NONE },
            containerColor = ThemeManager.parseColor(theme.surfaceColor),
            title = {
                Text(
                    "😅 Ладно, признаёмся...",
                    fontWeight = FontWeight.Bold,
                    color = ThemeManager.parseColor(theme.textPrimaryColor)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Никакие деньги никуда не ушли и не уйдут. Это была шутка 🙃",
                        color = ThemeManager.parseColor(theme.textPrimaryColor),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Но если вы реально хотите помочь развитию FunPay Tools и делать его лучше вместе с нами - рассмотрите лицензию Pro.",
                        color = ThemeManager.parseColor(theme.textSecondaryColor),
                        fontSize = 13.sp
                    )
                    Text(
                        "Оплата принимается картами СНГ и криптовалютой.",
                        color = ThemeManager.parseColor(theme.textSecondaryColor),
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/fptoolsbot"))
                        context.startActivity(intent)
                        step = DonateDialogStep.NONE
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ThemeManager.parseColor(theme.accentColor)
                    )
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Узнать про Pro →", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { step = DonateDialogStep.NONE }) {
                    Text("Закрыть", color = ThemeManager.parseColor(theme.textSecondaryColor))
                }
            }
        )
    }
}