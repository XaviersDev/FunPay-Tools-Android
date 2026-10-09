package ru.allisighs.funpaytools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Полноэкранный экран "сессия слетела".
 * Показывается, когда FunPay отдаёт гостевую страницу даже на новую сессию по golden_key
 * (раньше в этом случае профиль молча превращался в "Unknown / 0.0 / неизвестного").
 */
@Composable
fun SessionLostOverlay(
    info: SessionLostInfo,
    theme: AppTheme,
    onReloginWeb: () -> Unit,
    onReloginGoldenKey: () -> Unit,
    onRetry: suspend () -> Boolean,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var retryFailed by remember { mutableStateOf(false) }

    val bg = ThemeManager.parseColorOpaque(theme.originalBackgroundColor.ifBlank { theme.backgroundColor })
    val surface = ThemeManager.dialogSurface(theme)
    val accent = ThemeManager.parseColor(theme.accentColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)
    val goldenFirst = info.repeatedAfterRelogin

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            // перехватываем тапы, чтобы под оверлеем ничего не нажималось
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.WifiOff, null, tint = accent, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Сессия FunPay слетела",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textPrimary, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            val who = info.username?.takeIf { it.isNotBlank() && it != "Unknown" }
            Text(
                buildString {
                    append("FunPay перестал принимать вход")
                    if (who != null) append(" для аккаунта «$who»")
                    append(". Без повторного входа приложение не видит чаты, лоты и баланс.")
                },
                fontSize = 14.sp, color = textSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp
            )

            if (goldenFirst) {
                Spacer(Modifier.height(16.dp))
                Surface(color = Color(0xFFFFB300).copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp)) {
                    Text(
                        "Вход через сайт не помог — сессия слетела снова в течение 2 минут. " +
                                "Попробуйте войти через golden_key: скопируйте его из браузера, где вы залогинены на FunPay.",
                        modifier = Modifier.padding(14.dp),
                        fontSize = 13.sp, color = textPrimary, lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            val primaryColors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
            val secondaryColors = ButtonDefaults.buttonColors(containerColor = surface, contentColor = textPrimary)

            Button(
                onClick = if (goldenFirst) onReloginGoldenKey else onReloginWeb,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = primaryColors
            ) {
                Icon(if (goldenFirst) Icons.Default.Key else Icons.Default.Login, null)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (goldenFirst) "Войти через golden_key" else "Войти в аккаунт заново",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = if (goldenFirst) onReloginWeb else onReloginGoldenKey,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = secondaryColors
            ) {
                Icon(if (goldenFirst) Icons.Default.Login else Icons.Default.Key, null, tint = accent)
                Spacer(Modifier.width(10.dp))
                Text(if (goldenFirst) "Войти через сайт FunPay" else "Войти через golden_key", fontSize = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = {
                    if (checking) return@TextButton
                    checking = true
                    retryFailed = false
                    scope.launch {
                        val ok = try { onRetry() } catch (_: Exception) { false }
                        checking = false
                        retryFailed = !ok
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (checking) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                } else {
                    Icon(Icons.Default.Refresh, null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Проверить ещё раз", color = accent)
            }
            AnimatedVisibility(retryFailed, enter = fadeIn() + scaleIn(initialScale = 0.95f), exit = fadeOut()) {
                Text(
                    "FunPay всё ещё не пускает. Войдите заново.",
                    color = Color(0xFFE53935), fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            TextButton(onClick = onDismiss) {
                Text("Скрыть", color = textSecondary, fontSize = 12.sp)
            }
        }
    }
}
