package ru.allisighs.funpaytools

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/* ============================================================================
 *  «Новый чат» — написать любому пользователю FunPay по нику, ID или ссылке.
 *  Как в расширении (new_chat_starter.js):
 *   1) ник → ID через fptools-ai-server /api/rmthub;
 *   2) node чата = users-{меньший ID}-{больший ID};
 *   3) FunPay сам создаёт диалог при открытии такого чата.
 * ==========================================================================*/

data class FoundUser(
    val id: String,
    val username: String,
    val avatar: String?,
    val reviews: Int?,
    val status: String?
)

object NewChatApi {
    private const val BASE = "https://fptools-ai-server.vercel.app/api"

    /** users-{min}-{max} */
    fun buildNode(theirId: String, myId: String): String? {
        val a = theirId.toLongOrNull() ?: return null
        val b = myId.toLongOrNull() ?: return null
        return "users-${minOf(a, b)}-${maxOf(a, b)}"
    }

    private fun get(url: String): JSONObject? {
        val req = Request.Builder().url(url).header("User-Agent", "FunPayToolsApp").build()
        return ApiFactory.shared.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            try { JSONObject(r.body?.string().orEmpty()) } catch (_: Exception) { null }
        }
    }

    suspend fun findByName(username: String): FoundUser? = withContext(Dispatchers.IO) {
        val json = try { get("$BASE/rmthub?username=${Uri.encode(username)}") } catch (_: Exception) { null } ?: return@withContext null
        val u = json.optJSONObject("user") ?: return@withContext null
        val id = u.opt("id")?.toString().orEmpty()
        if (id.isBlank()) return@withContext null
        FoundUser(
            id = id,
            username = u.optString("username", username),
            avatar = u.optString("profile_image_url").ifBlank { null },
            reviews = if (u.has("reviews_number")) u.optInt("reviews_number") else null,
            status = u.optString("status").ifBlank { null }
        )
    }

    suspend fun avatarById(id: String): String? = withContext(Dispatchers.IO) {
        try { get("$BASE/avatar?user_id=$id")?.optString("avatar")?.ifBlank { null } } catch (_: Exception) { null }
    }

    /** Ник, ID или ссылка вида funpay.com/users/123/ */
    fun extractId(input: String): String? {
        val t = input.trim()
        Regex("""/users/(\d+)""").find(t)?.let { return it.groupValues[1] }
        if (t.all { it.isDigit() } && t.length in 2..12) return t
        return null
    }
}

@Composable
fun NewChatDialog(
    repository: FunPayRepository,
    navController: NavController,
    theme: AppTheme,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val accent = ThemeManager.parseColor(theme.accentColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)
    val surface = ThemeManager.dialogSurface(theme)

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<FoundUser?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: Exception) {} }

    // Поиск с задержкой 1 с, как в расширении (не спамим API на каждую букву)
    LaunchedEffect(query) {
        found = null
        error = null
        val q = query.trim()
        if (q.length < 2) { searching = false; return@LaunchedEffect }
        searching = true
        delay(1000)
        val directId = NewChatApi.extractId(q)
        found = if (directId != null) {
            FoundUser(directId, "ID $directId", NewChatApi.avatarById(directId), null, null)
        } else {
            NewChatApi.findByName(q)
        }
        if (found == null) error = "Пользователь «$q» не найден. Проверьте ник (с учётом регистра) или вставьте ссылку на профиль."
        searching = false
    }

    fun open(user: FoundUser) {
        if (opening) return
        opening = true
        scope.launch {
            val myId = repository.getCsrfAndId()?.second?.takeIf { it != "0" }
                ?: repository.getActiveAccount()?.userId?.takeIf { it.isNotBlank() }
            val node = myId?.let { NewChatApi.buildNode(user.id, it) }
            opening = false
            when {
                node == null -> error = "Не удалось определить ваш ID — проверьте вход в аккаунт."
                user.id == myId -> error = "Это вы :)"
                else -> {
                    onDismiss()
                    navController.navigate("chat/$node/${Uri.encode(user.username)}")
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(surface)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Новый чат", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = textPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = textSecondary) }
            }
            Text(
                "Введите ник продавца/покупателя, его ID или ссылку на профиль FunPay.",
                fontSize = 13.sp, color = textSecondary
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Ник, ID или ссылка") },
                leadingIcon = { Icon(Icons.Default.PersonSearch, null, tint = accent) },
                trailingIcon = {
                    if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    cursorColor = accent
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            Spacer(Modifier.height(12.dp))

            found?.let { u ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.08f))
                        .clickable { open(u) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = u.avatar ?: "https://funpay.com/img/layout/avatar.png",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(46.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        EpicNicknameText(
                            text = u.username,
                            style = LocalTextStyle.current.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                        )
                        val sub = listOfNotNull(
                            u.reviews?.let { "$it отзывов" },
                            u.status?.let { if (it.equals("Online", true)) "онлайн" else null }
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = { open(u) },
                        enabled = !opening,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text("Написать") }
                }
            }
            error?.let {
                Text(it, fontSize = 13.sp, color = Color(0xFFE53935), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
