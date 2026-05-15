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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt





private fun parseFunPayDateToMs(dateStr: String): Long {
    try {
        val now = Calendar.getInstance()
        val str = dateStr.lowercase().trim()
        val timePart = Regex("(\\d{1,2}):(\\d{2})").find(str)
        val h = timePart?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val m = timePart?.groupValues?.get(2)?.toIntOrNull() ?: 0

        if (str.contains("сегодня")) {
            now.set(Calendar.HOUR_OF_DAY, h); now.set(Calendar.MINUTE, m); now.set(Calendar.SECOND, 0)
            return now.timeInMillis
        }
        if (str.contains("вчера")) {
            now.add(Calendar.DAY_OF_YEAR, -1); now.set(Calendar.HOUR_OF_DAY, h); now.set(Calendar.MINUTE, m); now.set(Calendar.SECOND, 0)
            return now.timeInMillis
        }
        val months = listOf("янв","фев","мар","апр","мая","июн","июл","авг","сен","окт","ноя","дек")
        var monthIdx = -1
        for (i in months.indices) { if (str.contains(months[i])) { monthIdx = i; break } }
        val dayMatch = Regex("(\\d{1,2})\\s+[а-я]+").find(str)
        val d = dayMatch?.groupValues?.get(1)?.toIntOrNull() ?: now.get(Calendar.DAY_OF_MONTH)
        val yearMatch = Regex("(\\d{4})").find(str)
        val y = yearMatch?.groupValues?.get(1)?.toIntOrNull() ?: now.get(Calendar.YEAR)
        if (monthIdx != -1) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, monthIdx); cal.set(Calendar.DAY_OF_MONTH, d)
            cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m); cal.set(Calendar.SECOND, 0)
            return cal.timeInMillis
        }
    } catch (e: Exception) {}
    return 0L
}

private fun formatTimeLeft(ms: Long): String {
    val totalMins = max(0L, ms / 60000)
    val h = totalMins / 60; val m = totalMins % 60
    return if (h > 0) "$h ч $m мин" else "$m мин"
}





data class SystemFontInfo(
    val name: String,       
    val typeface: Typeface  
)

fun getSystemFonts(): List<SystemFontInfo> {
    val result = mutableListOf<SystemFontInfo>()

    
    result += SystemFontInfo("Default", Typeface.DEFAULT)
    result += SystemFontInfo("Default Bold", Typeface.DEFAULT_BOLD)
    result += SystemFontInfo("Monospace", Typeface.MONOSPACE)
    result += SystemFontInfo("Serif", Typeface.SERIF)
    result += SystemFontInfo("Sans-Serif", Typeface.SANS_SERIF)

    
    try {
        val fontsDir = File("/system/fonts")
        if (fontsDir.exists() && fontsDir.isDirectory) {
            fontsDir.listFiles()
                ?.filter { it.extension.lowercase() in listOf("ttf", "otf") }
                ?.sortedBy { it.nameWithoutExtension }
                ?.forEach { fontFile ->
                    try {
                        val tf = Typeface.createFromFile(fontFile)
                        val displayName = fontFile.nameWithoutExtension
                            .replace("-", " ")
                            .replace("_", " ")
                        result += SystemFontInfo(displayName, tf)
                    } catch (e: Exception) { /* пропустить если шрифт кривой */ }
                }
        }
    } catch (e: Exception) { /* fallback */ }

    
    val extraPaths = listOf("/system/font", "/data/fonts", "/vendor/fonts")
    for (path in extraPaths) {
        try {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()
                    ?.filter { it.extension.lowercase() in listOf("ttf", "otf") }
                    ?.forEach { fontFile ->
                        try {
                            val tf = Typeface.createFromFile(fontFile)
                            val displayName = fontFile.nameWithoutExtension.replace("-", " ").replace("_", " ")
                            if (result.none { it.name == displayName }) {
                                result += SystemFontInfo(displayName, tf)
                            }
                        } catch (e: Exception) { }
                    }
            }
        } catch (e: Exception) { }
    }

    return result.distinctBy { it.name }
}





data class DynAvatarElement(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: String, 
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 60f,
    val rotation: Float = 0f,
    val alpha: Float = 1f,
    val shadowRadius: Float = 5f,
    val fontStyle: String = "BOLD",
    val fontName: String = "Montserrat", 
    val colorHex: String = "#FFFFFF",
    val text: String = "",
    val imageUri: String? = null,
    
    val glassMorphEnabled: Boolean = false,
    val glassAlpha: Float = 0.5f,       
    val glassBorderAlpha: Float = 0.5f, 
    val glassColorHex: String = "#000000", 
    val glassCornerRadius: Float = 20f, 
    val glassBlurRadius: Float = 15f    
)

data class AutoGoalSettings(
    val enabled: Boolean = false,
    val currentGoal: Int = 0,
    val step: Int = 50,           
    val lastKnownReviews: Int = 0
)

data class DynamicAvatarSettings(
    val enabled: Boolean = false,
    val updateIntervalMinutes: Int = 5,
    val backgroundType: String = "PROFILE",
    val baseImageUri: String? = null,
    val backgroundColorHex: String = "#222222",
    val backgroundDim: Float = 0f,
    val elements: List<DynAvatarElement> = emptyList(),
    val autoGoal: AutoGoalSettings = AutoGoalSettings(),
    
    val cachedReviewCount: Int = -1
)





private fun resolveAvatarText(el: DynAvatarElement, profile: UserProfile?, autoGoal: AutoGoalSettings?): String {
    val safeText = el.text ?: ""
    return when (el.type) {
        "TIME" -> {
            
            try { SimpleDateFormat(safeText.ifBlank { "HH:mm" }, Locale.getDefault()).format(Date()) }
            catch (e: Exception) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
        }
        "STATS" -> {
            val reviews = profile?.reviewCount ?: 0
            val goalText = if (autoGoal != null && autoGoal.enabled && autoGoal.currentGoal > 0) {
                " / ${autoGoal.currentGoal}"
            } else ""
            (safeText.ifBlank { "★ {rating} ({reviews}{goal})" })
                .replace("{rating}", profile?.rating?.toString() ?: "5.0")
                .replace("{reviews}", reviews.toString())
                .replace("{goal}", goalText)
        }
        else -> safeText
    }
}





object DynamicAvatarManager {
    private var job: Job? = null

    fun startOrUpdate(context: Context, repository: FunPayRepository) {
        val prefs = context.getSharedPreferences("dynamic_avatar_prefs", Context.MODE_PRIVATE)
        val settingsJson = prefs.getString("settings", null)
        val settings = try { Gson().fromJson(settingsJson, DynamicAvatarSettings::class.java) } catch (e: Exception) { null }
        job?.cancel()
        if (settings?.enabled == true) {
            job = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    try {
                        val profile = repository.getSelfProfileUpdated()
                        val currentReviews = profile?.reviewCount ?: -1

                        
                        val freshJson = prefs.getString("settings", null)
                        var freshSettings = try {
                            val parsed = Gson().fromJson(freshJson, DynamicAvatarSettings::class.java)
                            parsed?.copy(autoGoal = parsed.autoGoal ?: AutoGoalSettings()) ?: settings
                        } catch (e: Exception) { settings }

                        val needsUpdate = (currentReviews == -1) ||
                                (freshSettings.cachedReviewCount == -1) ||
                                (currentReviews != freshSettings.cachedReviewCount)

                        if (needsUpdate) {
                            
                            var autoGoal = freshSettings.autoGoal
                            if (autoGoal.enabled) {
                                
                                if (autoGoal.currentGoal == 0 || currentReviews >= autoGoal.currentGoal) {
                                    val newGoal = if (currentReviews > 0) {
                                        ((currentReviews / autoGoal.step) + 1) * autoGoal.step
                                    } else {
                                        autoGoal.step
                                    }
                                    autoGoal = autoGoal.copy(
                                        currentGoal = newGoal,
                                        lastKnownReviews = currentReviews
                                    )
                                }
                            }

                            freshSettings = freshSettings.copy(
                                cachedReviewCount = currentReviews,
                                autoGoal = autoGoal
                            )
                            prefs.edit().putString("settings", Gson().toJson(freshSettings)).apply()

                            val bitmap = generateAvatarBitmap(context, freshSettings, profile, repository, drawElements = true)
                            uploadAvatar(repository, bitmap)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(settings.updateIntervalMinutes.coerceAtLeast(1) * 60 * 1000L)
                }
            }
        }
    }

    suspend fun generateAvatarBitmap(
        context: Context,
        settings: DynamicAvatarSettings,
        profile: UserProfile?,
        repository: FunPayRepository? = null,
        drawElements: Boolean = true
    ): Bitmap = withContext(Dispatchers.IO) {
        val size = 500
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.parseColor(settings.backgroundColorHex ?: "#222222"))

        if (settings.backgroundType != "COLOR") {
            var baseBitmap: Bitmap? = null
            try {
                if (settings.backgroundType == "IMAGE" && settings.baseImageUri != null) {
                    val inputStream = context.contentResolver.openInputStream(Uri.parse(settings.baseImageUri))
                    baseBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                } else if (settings.backgroundType == "PROFILE" && profile?.avatarUrl != null && repository != null) {
                    val req = Request.Builder().url(profile.avatarUrl).build()
                    val resp = repository.repoClient.newCall(req).execute()
                    baseBitmap = BitmapFactory.decodeStream(resp.body?.byteStream())
                }
            } catch (e: Exception) { e.printStackTrace() }

            if (baseBitmap != null) {
                val minDim = min(baseBitmap.width, baseBitmap.height).toFloat()
                val scale = size.toFloat() / minDim
                val matrix = android.graphics.Matrix()
                matrix.postScale(scale, scale)
                matrix.postTranslate((size - baseBitmap.width * scale) / 2f, (size - baseBitmap.height * scale) / 2f)
                canvas.drawBitmap(baseBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }

        if (settings.backgroundDim > 0f) {
            val alpha = (settings.backgroundDim * 255).toInt().coerceIn(0, 255)
            canvas.drawARGB(alpha, 0, 0, 0)
        }

        if (drawElements) {
            val paint = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER }
            
            val fontCache = mutableMapOf<String, Typeface>()

            settings.elements.forEach { el ->
                try {
                    canvas.save()
                    val centerX = el.x * size
                    val centerY = el.y * size
                    canvas.translate(centerX, centerY)
                    canvas.rotate(el.rotation)

                    if (el.type != "STICKER") {
                        paint.textSize = el.size
                        try { paint.color = android.graphics.Color.parseColor(el.colorHex ?: "#FFFFFF") } catch (e: Exception) { paint.color = android.graphics.Color.WHITE }
                        paint.alpha = (el.alpha * 255).toInt().coerceIn(0, 255)

                        
                        val tf = fontCache.getOrPut(el.fontName ?: "Default Bold") {
                            resolveFontByName(el.fontName ?: "Default Bold", el.fontStyle ?: "BOLD")
                        }
                        paint.typeface = tf

                        if (el.shadowRadius > 0f) paint.setShadowLayer(el.shadowRadius, 0f, 2f, android.graphics.Color.parseColor("#B3000000"))
                        else paint.clearShadowLayer()

                        val textToDraw = resolveAvatarText(el, profile, settings.autoGoal)
                        if (textToDraw.isNotBlank()) {
                            val lines = textToDraw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                            if (lines.isEmpty()) return@forEach

                            val lineHeight = -paint.ascent() + paint.descent()
                            val textHeight = lines.size * lineHeight
                            val maxWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f

                            if (el.glassMorphEnabled) {
                                
                                val padX = 24f
                                val padY = 12f

                                val glassRect = android.graphics.RectF(
                                    -maxWidth / 2f - padX,
                                    -textHeight / 2f - padY,
                                    maxWidth / 2f + padX,
                                    textHeight / 2f + padY
                                )

                                
                                val shadowPaint = Paint().apply {
                                    isAntiAlias = true
                                    color = android.graphics.Color.BLACK
                                    alpha = (el.glassAlpha * 120).toInt().coerceIn(0, 255)
                                    maskFilter = android.graphics.BlurMaskFilter(20f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                                }
                                canvas.drawRoundRect(glassRect, el.glassCornerRadius, el.glassCornerRadius, shadowPaint)

                                val glassPaint = Paint().apply {
                                    isAntiAlias = true
                                    try { color = android.graphics.Color.parseColor(el.glassColorHex ?: "#000000") } catch (e: Exception) { color = android.graphics.Color.BLACK }
                                    alpha = (el.glassAlpha * 255).toInt().coerceIn(0, 255)
                                }
                                canvas.drawRoundRect(glassRect, el.glassCornerRadius, el.glassCornerRadius, glassPaint)

                                val borderPaint = Paint().apply {
                                    isAntiAlias = true
                                    style = Paint.Style.STROKE
                                    strokeWidth = 2f
                                    try { color = android.graphics.Color.parseColor(el.glassColorHex ?: "#000000") } catch (e: Exception) { color = android.graphics.Color.BLACK }
                                    alpha = (el.glassBorderAlpha * 255).toInt().coerceIn(0, 255)
                                }
                                canvas.drawRoundRect(glassRect, el.glassCornerRadius, el.glassCornerRadius, borderPaint)
                            }

                            val startY = -textHeight / 2f - paint.ascent()
                            lines.forEachIndexed { index, line ->
                                canvas.drawText(line, 0f, startY + index * lineHeight, paint)
                            }
                        }
                    } else {
                        el.imageUri?.let { uri ->
                            val inputStream = context.contentResolver.openInputStream(Uri.parse(uri))
                            val stickerBmp = BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()
                            if (stickerBmp != null) {
                                val targetW = (size * (el.size / 100f)).toInt().coerceAtLeast(10)
                                val targetH = (stickerBmp.height * (targetW.toFloat() / stickerBmp.width)).toInt().coerceAtLeast(10)
                                val scaled = Bitmap.createScaledBitmap(stickerBmp, targetW, targetH, true)
                                val stickerPaint = Paint().apply { alpha = (el.alpha * 255).toInt().coerceIn(0, 255) }
                                canvas.drawBitmap(scaled, -targetW / 2f, -targetH / 2f, stickerPaint)
                            }
                        }
                    }
                    canvas.restore()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        return@withContext bitmap
    }

    suspend fun uploadAvatar(repository: FunPayRepository, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        val reqFile = byteArray.toRequestBody("image/png".toMediaTypeOrNull())
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", "avatar.png", reqFile).addFormDataPart("file_id", "0").build()
        val req = Request.Builder().url("https://funpay.com/file/avatar").header("Cookie", repository.getCookieString()).header("x-requested-with", "XMLHttpRequest").post(body).build()
        val response = repository.repoClient.newCall(req).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
    }
}


private fun resolveFontByName(fontName: String, fontStyle: String): Typeface {
    
    val paths = listOf("/system/fonts", "/system/font", "/vendor/fonts", "/data/fonts")
    for (path in paths) {
        try {
            val dir = File(path)
            if (!dir.exists()) continue
            val file = dir.listFiles()?.firstOrNull { f ->
                val n = f.nameWithoutExtension.replace("-", " ").replace("_", " ")
                n.equals(fontName, ignoreCase = true)
            }
            if (file != null) return Typeface.createFromFile(file)
        } catch (e: Exception) { }
    }
    
    return when (fontStyle) {
        "NORMAL" -> Typeface.DEFAULT
        "MONO" -> Typeface.MONOSPACE
        "SERIF" -> Typeface.SERIF
        else -> Typeface.DEFAULT_BOLD
    }
}

private fun copyUriToInternalStorage(context: Context, uri: Uri, fileName: String): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = java.io.File(context.filesDir, fileName)
        val outputStream = java.io.FileOutputStream(file)
        inputStream.copyTo(outputStream)
        inputStream.close(); outputStream.close()
        Uri.fromFile(file).toString()
    } catch (e: Exception) { e.printStackTrace(); null }
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
    val context = LocalContext.current
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var localSales by remember { mutableStateOf(repository.cachedSales) }
    var showDynamicAvatarDialog by remember { mutableStateOf(false) }
    val activeAccount = repository.getActiveAccount()
    val accountKey = activeAccount?.id ?: "none"
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        DynamicAvatarManager.startOrUpdate(context, repository)
        while (true) { delay(60000); currentTime = System.currentTimeMillis() }
    }

    LaunchedEffect(accountKey) {
        isLoading = true
        try { profile = repository.getSelfProfileUpdated() } catch (e: Exception) { profile = null } finally { isLoading = false }

        if (!repository.isSalesFullLoaded) {
            withContext(Dispatchers.IO) {
                var token: String? = null
                val buf = repository.cachedSales.toMutableList()
                while (true) {
                    val result = try { repository.fetchSalesPage(token) } catch (e: Exception) { null }
                    if (result == null) break
                    val page = result.first; val next = result.second
                    val existingIds = buf.map { it.orderId }.toSet()
                    val newOrders = page.filter { it.orderId !in existingIds }
                    if (newOrders.isEmpty() && page.isNotEmpty()) { repository.isSalesFullLoaded = true; break }
                    buf.addAll(newOrders); token = next
                    val newList = buf.toList(); repository.cachedSales = newList; localSales = newList
                    if (next == null) { repository.isSalesFullLoaded = true; break }
                    delay(400)
                }
            }
        } else { localSales = repository.cachedSales }
    }

    if (showDynamicAvatarDialog) {
        DynamicAvatarDialog(
            repository = repository, theme = theme, profile = profile,
            onDismiss = { showDynamicAvatarDialog = false },
            onProfileUpdateNeeded = { scope.launch { try { profile = repository.getSelfProfileUpdated() } catch (e: Exception) {} } }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = ThemeManager.parseColor(theme.accentColor))
        } else if (profile == null) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ErrorOutline, null, tint = ThemeManager.parseColor(theme.textSecondaryColor), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Не удалось загрузить профиль", color = ThemeManager.parseColor(theme.textSecondaryColor))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { scope.launch { isLoading = true; try { profile = repository.getSelfProfileUpdated() } catch (e: Exception) { profile = null } finally { isLoading = false } } }, colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))) { Text("Повторить") }
            }
        } else {
            val user = profile!!
            val totalBalanceVal = user.totalBalance.replace(Regex("[^0-9.,]"), "").replace(",", ".").toDoubleOrNull() ?: 0.0
            val frozenSales = localSales.filter { it.status == "closed" }.mapNotNull { sale ->
                val saleTime = parseFunPayDateToMs(sale.date)
                if (saleTime > 0) { val unlockTime = saleTime + 48L * 3600L * 1000L; if (currentTime < unlockTime) Pair(sale, unlockTime) else null } else null
            }.sortedBy { it.second }
            val frozenSum = frozenSales.sumOf { it.first.priceValue }
            val activeSum = max(0.0, totalBalanceVal - frozenSum)
            val nextUnlock = frozenSales.firstOrNull()

            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)), shape = RoundedCornerShape(theme.borderRadius.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            AsyncImage(model = user.avatarUrl, contentDescription = null, modifier = Modifier.size(72.dp).clip(CircleShape).border(2.dp, ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.4f), CircleShape), contentScale = ContentScale.Crop)
                            Box(modifier = Modifier.size(16.dp).align(Alignment.BottomEnd).offset(x = (-6).dp, y = (-4).dp).clip(CircleShape).background(if (user.isOnline) Color(0xFF00C853) else Color.Gray).border(2.dp, ThemeManager.parseColor(theme.surfaceColor), CircleShape))
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.username, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${user.rating} (${user.reviewCount} отзывов)", fontSize = 12.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(user.registeredDate, fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.8f))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.1f)).clickable { showDynamicAvatarDialog = true }, contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.Palette, contentDescription = "Кастомизация аватарки", tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(22.dp))
                        }
                    }
                }

                DetailedBalanceCard(totalBalance = user.totalBalance, totalVal = totalBalanceVal, activeSum = activeSum, frozenSum = frozenSum, nextUnlock = nextUnlock, currentTime = currentTime, theme = theme)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionGridItem(modifier = Modifier.weight(1f), title = "Тарифы", subtitle = "Доступ к PRO", icon = Icons.Default.Diamond, iconTint = Color(0xFFFFD700), bgGradient = listOf(Color(0xFF3E2723), Color(0xFF261A15)), theme = theme, onClick = onOpenTariffs)
                    ActionGridItem(modifier = Modifier.weight(1f), title = "Мои лоты", subtitle = "Управление", icon = Icons.Default.Inventory, iconTint = ThemeManager.parseColor(theme.accentColor), bgGradient = listOf(ThemeManager.parseColor(theme.surfaceColor), ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.8f)), theme = theme, onClick = onOpenLots)
                }

                DonateEasterEggButton(theme = theme)

                Text("Статистика", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), modifier = Modifier.padding(top = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatMiniCard(modifier = Modifier.weight(1f), title = "Продажи", value = "${user.activeSales}", icon = Icons.Default.TrendingUp, theme = theme)
                    StatMiniCard(modifier = Modifier.weight(1f), title = "Покупки", value = "${user.activePurchases}", icon = Icons.Default.ShoppingCart, theme = theme)
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(42.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.15f)), shape = RoundedCornerShape(12.dp), elevation = ButtonDefaults.buttonElevation(0.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выйти из аккаунта", color = Color(0xFFE53935), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}





@Composable
fun DynamicAvatarDialog(
    repository: FunPayRepository,
    theme: AppTheme,
    profile: UserProfile?,
    onDismiss: () -> Unit,
    onProfileUpdateNeeded: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("dynamic_avatar_prefs", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    var settings by remember {
        mutableStateOf(
            try {
                val json = prefs.getString("settings", null)
                val loaded = gson.fromJson(json, DynamicAvatarSettings::class.java)
                if (loaded != null) {
                    
                    loaded.copy(
                        backgroundType = loaded.backgroundType ?: "PROFILE",
                        backgroundColorHex = loaded.backgroundColorHex ?: "#222222",
                        autoGoal = loaded.autoGoal ?: AutoGoalSettings(),
                        elements = loaded.elements?.map { el ->
                            el.copy(
                                id = el.id ?: java.util.UUID.randomUUID().toString(),
                                type = el.type ?: "TEXT",
                                fontName = el.fontName ?: "Montserrat",
                                fontStyle = el.fontStyle ?: "BOLD",
                                colorHex = el.colorHex ?: "#FFFFFF",
                                text = el.text ?: "",
                                glassColorHex = el.glassColorHex ?: "#000000",
                                
                                glassCornerRadius = if (el.glassCornerRadius == 0f) 20f else el.glassCornerRadius,
                                glassAlpha = if (el.glassAlpha == 0f) 0.5f else el.glassAlpha
                            )
                        } ?: emptyList()
                    )
                } else DynamicAvatarSettings()
            } catch (e: Exception) { DynamicAvatarSettings() }
        )
    }

    var selectedElementId by remember { mutableStateOf<String?>(null) }
    var previewBackgroundBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val stickerBitmaps = remember { mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap>() }
    var isGenerating by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var expandDynamicSettings by remember { mutableStateOf(false) }
    var showFontPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    
    val systemFonts = remember { mutableStateOf<List<SystemFontInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val fonts = getSystemFonts()
            withContext(Dispatchers.Main) { systemFonts.value = fonts }
        }
    }

    LaunchedEffect(settings.backgroundType, settings.baseImageUri, settings.backgroundColorHex, settings.backgroundDim) {
        isGenerating = true
        try {
            val bmp = DynamicAvatarManager.generateAvatarBitmap(context, settings, profile, repository, drawElements = false)
            previewBackgroundBitmap = bmp.asImageBitmap()
        } catch (e: Exception) { e.printStackTrace() }
        isGenerating = false
    }

    LaunchedEffect(settings.elements) {
        settings.elements.filter { it.type == "STICKER" && it.imageUri != null }.forEach { el ->
            if (!stickerBitmaps.containsKey(el.id)) {
                withContext(Dispatchers.IO) {
                    try {
                        val stream = context.contentResolver.openInputStream(Uri.parse(el.imageUri!!))
                        val bmp = BitmapFactory.decodeStream(stream); stream?.close()
                        if (bmp != null) stickerBitmaps[el.id] = bmp.asImageBitmap()
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    val staticAvatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isUploading = true
            scope.launch {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(stream); stream?.close()
                    if (bmp != null) {
                        DynamicAvatarManager.uploadAvatar(repository, bmp)
                        android.widget.Toast.makeText(context, "Обычная аватарка обновлена!", android.widget.Toast.LENGTH_SHORT).show()
                        onProfileUpdateNeeded(); onDismiss()
                    }
                } catch (e: Exception) { e.printStackTrace() }
                isUploading = false
            }
        }
    }

    val bgImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val localUri = copyUriToInternalStorage(context, uri, "bg_avatar.png")
                if (localUri != null) withContext(Dispatchers.Main) { settings = settings.copy(backgroundType = "IMAGE", baseImageUri = localUri) }
            }
        }
    }

    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val uuid = java.util.UUID.randomUUID().toString()
                val localUri = copyUriToInternalStorage(context, uri, "sticker_$uuid.png")
                if (localUri != null) withContext(Dispatchers.Main) {
                    val newEl = DynAvatarElement(id = uuid, type = "STICKER", imageUri = localUri, size = 40f)
                    settings = settings.copy(elements = settings.elements + newEl)
                    selectedElementId = newEl.id
                }
            }
        }
    }

    fun saveSettings() {
        prefs.edit().putString("settings", gson.toJson(settings)).apply()
        DynamicAvatarManager.startOrUpdate(context, repository)
    }

    
    
    val selectedEl = settings.elements.find { it.id == selectedElementId }
    if (showFontPicker && selectedEl != null) {
        Dialog(onDismissRequest = { showFontPicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(
                modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f),
                colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
                shape = RoundedCornerShape(theme.borderRadius.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Выбор шрифта", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ThemeManager.parseColor(theme.textPrimaryColor), modifier = Modifier.weight(1f))
                        IconButton(onClick = { showFontPicker = false }) { Icon(Icons.Default.Close, null) }
                    }
                    Spacer(Modifier.height(8.dp))

                    if (systemFonts.value.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ThemeManager.parseColor(theme.accentColor))
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(systemFonts.value) { fontInfo ->
                                val isSelected = (selectedEl.fontName == fontInfo.name)
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        settings = settings.copy(elements = settings.elements.map { el ->
                                            if (el.id == selectedEl.id) el.copy(fontName = fontInfo.name) else el
                                        })
                                        showFontPicker = false
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.15f)
                                        else ThemeManager.parseColor(theme.surfaceColor)
                                    ),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, ThemeManager.parseColor(theme.accentColor)) else null,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                        
                                        Text(
                                            text = fontInfo.name,
                                            fontSize = 11.sp,
                                            color = ThemeManager.parseColor(theme.textSecondaryColor),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        
                                        androidx.compose.foundation.Canvas(
                                            modifier = Modifier.fillMaxWidth().height(36.dp)
                                        ) {
                                            val paint = android.graphics.Paint().apply {
                                                isAntiAlias = true
                                                textSize = 26.sp.toPx()
                                                typeface = fontInfo.typeface
                                                color = if (isSelected)
                                                    android.graphics.Color.parseColor(theme.accentColor)
                                                else
                                                    android.graphics.Color.parseColor(theme.textPrimaryColor)
                                                textAlign = android.graphics.Paint.Align.LEFT
                                            }
                                            drawContext.canvas.nativeCanvas.drawText(
                                                "FunPay Tools лучший",
                                                0f,
                                                size.height * 0.75f,
                                                paint
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

    
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(0.94f)
                    .then(if (expandDynamicSettings) Modifier.fillMaxHeight(0.94f) else Modifier.heightIn(max = 480.dp))
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor)),
                shape = RoundedCornerShape(theme.borderRadius.dp)
            ) {
                Column(modifier = Modifier.then(if (expandDynamicSettings) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Настройка аватарки", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 18.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = ThemeManager.parseColor(theme.textPrimaryColor)) }
                    }

                    LazyColumn(
                        modifier = if (expandDynamicSettings) Modifier.weight(1f) else Modifier,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(model = profile?.avatarUrl, contentDescription = null, modifier = Modifier.size(100.dp).clip(CircleShape).border(2.dp, ThemeManager.parseColor(theme.accentColor), CircleShape), contentScale = ContentScale.Crop)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { staticAvatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor)), modifier = Modifier.fillMaxWidth()) {
                                    if (isUploading && !expandDynamicSettings) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                    else Text("Загрузить новую из галереи")
                                }
                            }
                        }

                        item {
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { expandDynamicSettings = !expandDynamicSettings }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Динамическая аватарка PRO", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold)
                                    Text("Мощный редактор слоёв", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                                }
                                Icon(imageVector = if (expandDynamicSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = ThemeManager.parseColor(theme.accentColor))
                            }
                        }

                        if (expandDynamicSettings) {
                            
                            item {
                                Card(modifier = Modifier.fillMaxWidth().animateContentSize(), colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.05f)), border = androidx.compose.foundation.BorderStroke(1.dp, ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.3f))) {
                                    Column(Modifier.padding(14.dp).fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Живая аватарка", color = ThemeManager.parseColor(theme.accentColor), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                Spacer(Modifier.height(2.dp))
                                                Text("Время идёт как настоящее, отзывы синхронизируются с FunPay. Аватарка обновляется только при изменении данных.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp, lineHeight = 16.sp)
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Switch(checked = settings.enabled, onCheckedChange = { settings = settings.copy(enabled = it); saveSettings() }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ThemeManager.parseColor(theme.accentColor)))
                                        }
                                        if (settings.enabled) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.15f))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Интервал проверки:", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 13.sp)
                                                Text("${settings.updateIntervalMinutes} мин", color = ThemeManager.parseColor(theme.accentColor), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Slider(value = settings.updateIntervalMinutes.toFloat(), onValueChange = { settings = settings.copy(updateIntervalMinutes = it.toInt()) }, onValueChangeFinished = { saveSettings() }, valueRange = 1f..60f, modifier = Modifier.fillMaxWidth(), colors = SliderDefaults.colors(thumbColor = ThemeManager.parseColor(theme.accentColor), activeTrackColor = ThemeManager.parseColor(theme.accentColor), inactiveTrackColor = ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.2f)))
                                            Text("Аватарка обновится только если изменились отзывы (или идёт время).", color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.8f), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            
                            item {
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.12f)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f)), shape = RoundedCornerShape(12.dp)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text("🎯 Автоцели по отзывам", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                Spacer(Modifier.height(2.dp))
                                                if (settings.autoGoal.enabled) {
                                                    val reviews = profile?.reviewCount ?: settings.autoGoal.lastKnownReviews
                                                    val goal = settings.autoGoal.currentGoal
                                                    Text("Отзывы: $reviews   Цель: $goal", color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                    val progress = if (goal > 0) (reviews.toFloat() / goal).coerceIn(0f, 1f) else 0f
                                                    Spacer(Modifier.height(6.dp))
                                                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Color(0xFF4CAF50), trackColor = Color(0xFF4CAF50).copy(alpha = 0.2f))
                                                    Spacer(Modifier.height(4.dp))
                                                    Text("До цели: ${max(0, goal - reviews)} отзывов", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp)
                                                } else {
                                                    Text("Цель генерируется автоматически кратно выбранному шагу.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                                                }
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Switch(
                                                checked = settings.autoGoal.enabled,
                                                onCheckedChange = { enabled ->
                                                    val reviews = profile?.reviewCount ?: 0
                                                    val step = settings.autoGoal.step
                                                    
                                                    val newGoal = if (enabled && reviews > 0) ((reviews / step) + 1) * step else settings.autoGoal.currentGoal
                                                    settings = settings.copy(autoGoal = settings.autoGoal.copy(
                                                        enabled = enabled,
                                                        currentGoal = newGoal,
                                                        lastKnownReviews = reviews
                                                    ))
                                                    saveSettings()
                                                },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF4CAF50))
                                            )
                                        }

                                        if (settings.autoGoal.enabled) {
                                            Spacer(Modifier.height(12.dp))
                                            Text("Шаг цели (прибавляется при достижении):", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 12.sp)
                                            Spacer(Modifier.height(6.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                                listOf(50, 100, 200, 300).forEach { step ->
                                                    val isActive = settings.autoGoal.step == step
                                                    Button(
                                                        onClick = {
                                                            
                                                            val reviews = profile?.reviewCount ?: 0
                                                            val newGoal = if (reviews > 0) ((reviews / step) + 1) * step else step
                                                            settings = settings.copy(autoGoal = settings.autoGoal.copy(step = step, currentGoal = newGoal))
                                                            saveSettings()
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        colors = ButtonDefaults.buttonColors(containerColor = if (isActive) Color(0xFF4CAF50) else Color(0xFF4CAF50).copy(alpha = 0.15f)),
                                                        contentPadding = PaddingValues(0.dp),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text("+$step", fontSize = 12.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, color = if (isActive) Color.White else Color(0xFF4CAF50))
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Text("Добавь элемент «Отзывы» на холст - он автоматически отобразит цель.", color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.7f), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            
                            item {
                                Text("Холст", fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                                Spacer(Modifier.height(4.dp))
                                AvatarCanvasEditor(
                                    settings = settings, profile = profile,
                                    backgroundBitmap = previewBackgroundBitmap,
                                    stickerBitmaps = stickerBitmaps, isGenerating = isGenerating,
                                    selectedId = selectedElementId,
                                    accentColor = ThemeManager.parseColor(theme.accentColor),
                                    onSelect = { selectedElementId = it },
                                    onUpdateSettings = { settings = it }
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center) {
                                    AssistChip(onClick = {
                                        val newEl = DynAvatarElement(id = java.util.UUID.randomUUID().toString(), type = "STATS", text = "★ {reviews}{goal}", x = 0.5f, y = 0.7f, size = 40f)
                                        settings = settings.copy(elements = settings.elements + newEl); selectedElementId = newEl.id
                                    }, label = { Text("+ Отзывы") })
                                    AssistChip(onClick = {
                                        val newEl = DynAvatarElement(id = java.util.UUID.randomUUID().toString(), type = "TEXT", text = "Six Seven", x = 0.5f, y = 0.5f, size = 50f)
                                        settings = settings.copy(elements = settings.elements + newEl); selectedElementId = newEl.id
                                    }, label = { Text("+ Текст") })
                                    AssistChip(onClick = { stickerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, label = { Text("+ Стикер") })
                                }
                            }

                            
                            item {
                                Text("Режим фона", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(onClick = { settings = settings.copy(backgroundType = "PROFILE") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (settings.backgroundType == "PROFILE") ThemeManager.parseColor(theme.accentColor) else Color.DarkGray), contentPadding = PaddingValues(0.dp)) { Text("С FunPay", fontSize = 11.sp) }
                                    Button(onClick = { bgImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (settings.backgroundType == "IMAGE") ThemeManager.parseColor(theme.accentColor) else Color.DarkGray), contentPadding = PaddingValues(0.dp)) { Text("Картинка", fontSize = 11.sp) }
                                    Button(onClick = { settings = settings.copy(backgroundType = "COLOR") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (settings.backgroundType == "COLOR") ThemeManager.parseColor(theme.accentColor) else Color.DarkGray), contentPadding = PaddingValues(0.dp)) { Text("Цвет", fontSize = 11.sp) }
                                }
                                if (settings.backgroundType == "COLOR") {
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        listOf("#222222","#FFFFFF","#FFC107","#4CAF50","#2196F3","#F44336","#9C27B0").forEach { hex ->
                                            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(hex))).border(2.dp, if (settings.backgroundColorHex == hex) ThemeManager.parseColor(theme.accentColor) else Color.Transparent, CircleShape).clickable { settings = settings.copy(backgroundColorHex = hex) })
                                        }
                                    }
                                }
                                Text("Затемнение фона", fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor), modifier = Modifier.padding(top = 8.dp))
                                Slider(value = settings.backgroundDim, onValueChange = { settings = settings.copy(backgroundDim = it) })
                            }

                            
                            val selEl = settings.elements.find { it.id == selectedElementId }
                            if (selEl != null) {
                                val index = settings.elements.indexOf(selEl)
                                item {
                                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.3f)), border = androidx.compose.foundation.BorderStroke(1.dp, ThemeManager.parseColor(theme.accentColor))) {
                                        Column(Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Слой: ${selEl.type}", color = ThemeManager.parseColor(theme.accentColor), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                IconButton(onClick = { if (index > 0) { val ml = settings.elements.toMutableList(); java.util.Collections.swap(ml, index, index-1); settings = settings.copy(elements = ml.toList()) } }, enabled = index > 0, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Ниже") }
                                                IconButton(onClick = { if (index < settings.elements.lastIndex) { val ml = settings.elements.toMutableList(); java.util.Collections.swap(ml, index, index+1); settings = settings.copy(elements = ml.toList()) } }, enabled = index < settings.elements.lastIndex, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Выше") }
                                                IconButton(onClick = { settings = settings.copy(elements = settings.elements.filter { it.id != selEl.id }); selectedElementId = null }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                                            }

                                            if (selEl.type == "TEXT" || selEl.type == "TIME" || selEl.type == "STATS") {
                                                
                                                if (selEl.type == "TIME") {
                                                    Text("Формат времени:", fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                                                    Spacer(Modifier.height(4.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                                        listOf("HH:mm" to "24ч", "hh:mm a" to "12ч AM/PM", "HH:mm:ss" to "С сек", "h:mm" to "Без 0").forEach { (fmt, label) ->
                                                            val isSelected = selEl.text == fmt
                                                            OutlinedButton(
                                                                onClick = { settings = settings.copy(elements = settings.elements.map { el -> if (el.id == selEl.id) el.copy(text = fmt) else el }) },
                                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isSelected) ThemeManager.parseColor(theme.accentColor) else Color.Gray),
                                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ThemeManager.parseColor(theme.accentColor) else Color.Gray.copy(alpha = 0.4f)),
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                            ) { Text(label, fontSize = 11.sp) }
                                                        }
                                                    }
                                                    Text("Значение времени генерируется автоматически и не редактируется.", fontSize = 10.sp, color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.6f))
                                                }

                                                if (selEl.type == "STATS") {
                                                    Text("Теги: {reviews} — кол-во отзывов, {goal} — цель, {rating} — рейтинг", fontSize = 10.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                                                    Spacer(Modifier.height(4.dp))
                                                    OutlinedTextField(value = selEl.text ?: "", onValueChange = { newT -> settings = settings.copy(elements = settings.elements.map { el -> if (el.id == selEl.id) el.copy(text = newT) else el }) }, label = { Text("Шаблон") }, modifier = Modifier.fillMaxWidth().height(56.dp), singleLine = false)
                                                }

                                                if (selEl.type == "TEXT") {
                                                    OutlinedTextField(value = selEl.text ?: "", onValueChange = { newT -> settings = settings.copy(elements = settings.elements.map { el -> if (el.id == selEl.id) el.copy(text = newT) else el }) }, label = { Text("Текст") }, modifier = Modifier.fillMaxWidth().height(56.dp), singleLine = false)
                                                }

                                                Spacer(Modifier.height(8.dp))

                                                
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                                    listOf("#FFFFFF","#000000","#FFC107","#4CAF50","#2196F3","#F44336","#9C27B0","#E91E63").forEach { hex ->
                                                        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(hex))).border(2.dp, if (selEl.colorHex == hex) ThemeManager.parseColor(theme.accentColor) else Color.Transparent, CircleShape).clickable { settings = settings.copy(elements = settings.elements.map { el -> if (el.id == selEl.id) el.copy(colorHex = hex) else el }) })
                                                    }
                                                }

                                                Spacer(Modifier.height(8.dp))

                                                
                                                val safeFontName = selEl.fontName ?: "Default Bold" 
                                                OutlinedButton(
                                                    onClick = { showFontPicker = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.6f))
                                                ) {
                                                    Icon(Icons.Default.FontDownload, null, modifier = Modifier.size(16.dp), tint = ThemeManager.parseColor(theme.accentColor))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Шрифт: ${safeFontName.take(24)}${if (safeFontName.length > 24) "…" else ""}", color = ThemeManager.parseColor(theme.accentColor), fontSize = 13.sp)
                                                    Spacer(Modifier.weight(1f))
                                                    Icon(Icons.Default.ChevronRight, null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(16.dp))
                                                }

                                                Spacer(Modifier.height(4.dp))
                                                Text("Тень текста", fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                                                Slider(value = selEl.shadowRadius, valueRange = 0f..20f, onValueChange = { ns -> settings = settings.copy(elements = settings.elements.map { el -> if (el.id == selEl.id) el.copy(shadowRadius = ns) else el }) })

                                                
                                                Spacer(Modifier.height(8.dp))
                                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), shape = RoundedCornerShape(10.dp)) {
                                                    Column(Modifier.padding(12.dp)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("Фон", fontSize = 13.sp, color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                                            Switch(
                                                                checked = selEl.glassMorphEnabled,
                                                                onCheckedChange = { v -> settings = settings.copy(elements = settings.elements.map { el -> if (el.id == selEl.id) el.copy(glassMorphEnabled = v) else el }) },
                                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ThemeManager.parseColor(theme.accentColor))
                                                            )
                                                        }
                                                        if (selEl.glassMorphEnabled) {
                                                            Spacer(Modifier.height(8.dp))
                                                            Text("Цвет фона:", fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                                                            Spacer(Modifier.height(4.dp))
                                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                                                listOf("#FFFFFF","#000000","#2196F3","#4CAF50","#FF5722","#9C27B0").forEach { hex ->
                                                                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(hex))).border(2.dp, if (selEl.glassColorHex == hex) ThemeManager.parseColor(theme.accentColor) else Color.Transparent, CircleShape).clickable { settings = settings.copy(elements = settings.elements.map { el -> if (el.id == selEl.id) el.copy(glassColorHex = hex) else el }) })
                                                                }
                                                            }
                                                            Spacer(Modifier.height(6.dp))
                                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                                Text("Прозрачность заливки:", fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                                                                Text("${(selEl.glassAlpha * 100).toInt()}%", fontSize = 11.sp, color = ThemeManager.parseColor(theme.accentColor))
                                                            }
                                                            Slider(value = selEl.glassAlpha, onValueChange = { v -> settings = settings.copy(elements = settings.elements.map { el -> if (el.id == selEl.id) el.copy(glassAlpha = v) else el }) })
                                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                                Text("Яркость контура:", fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                                                                Text("${(selEl.glassBorderAlpha * 100).toInt()}%", fontSize = 11.sp, color = ThemeManager.parseColor(theme.accentColor))
                                                            }
                                                            Slider(value = selEl.glassBorderAlpha, onValueChange = { v -> settings = settings.copy(elements = settings.elements.map { el -> if (el.id == selEl.id) el.copy(glassBorderAlpha = v) else el }) })
                                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                                Text("Скругление углов:", fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                                                                Text("${selEl.glassCornerRadius.toInt()}px", fontSize = 11.sp, color = ThemeManager.parseColor(theme.accentColor))
                                                            }
                                                            Slider(value = selEl.glassCornerRadius, valueRange = 0f..50f, onValueChange = { v -> settings = settings.copy(elements = settings.elements.map { el -> if (el.id == selEl.id) el.copy(glassCornerRadius = v) else el }) })
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(4.dp))
                                            Text("Прозрачность слоя", fontSize = 11.sp, color = ThemeManager.parseColor(theme.textSecondaryColor))
                                            Slider(value = selEl.alpha, onValueChange = { na -> settings = settings.copy(elements = settings.elements.map { el -> if (el.id == selEl.id) el.copy(alpha = na) else el }) })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (expandDynamicSettings) {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                saveSettings()
                                if (isUploading) return@Button
                                isUploading = true
                                scope.launch {
                                    try {
                                        val bmp = DynamicAvatarManager.generateAvatarBitmap(context, settings, profile, repository, drawElements = true)
                                        DynamicAvatarManager.uploadAvatar(repository, bmp)
                                        android.widget.Toast.makeText(context, "Динамическая аватарка установлена!", android.widget.Toast.LENGTH_SHORT).show()
                                        onProfileUpdateNeeded(); onDismiss()
                                    } catch (e: Exception) { android.widget.Toast.makeText(context, "Ошибка: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() }
                                    isUploading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))
                        ) {
                            if (isUploading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            else Text("Сохранить и Применить", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}





@Composable
fun AvatarCanvasEditor(
    settings: DynamicAvatarSettings,
    profile: UserProfile?,
    backgroundBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    stickerBitmaps: Map<String, androidx.compose.ui.graphics.ImageBitmap>,
    isGenerating: Boolean,
    selectedId: String?,
    accentColor: Color,
    onSelect: (String?) -> Unit,
    onUpdateSettings: (DynamicAvatarSettings) -> Unit
) {
    var snapX by remember { mutableStateOf(false) }
    var snapY by remember { mutableStateOf(false) }
    var activeDragMode by remember { mutableStateOf("NONE") }
    var initialTouchX by remember { mutableFloatStateOf(0f) }
    var initialTouchY by remember { mutableFloatStateOf(0f) }
    var initialElX by remember { mutableFloatStateOf(0f) }
    var initialElY by remember { mutableFloatStateOf(0f) }
    var initialElSize by remember { mutableFloatStateOf(0f) }
    var initialElRot by remember { mutableFloatStateOf(0f) }

    val fontCache = remember { java.util.concurrent.ConcurrentHashMap<String, Typeface>() }

    fun getElementBounds(el: DynAvatarElement, cw: Float, ch: Float): Pair<Float, Float> {
        val uiScale = cw / 500f
        return if (el.type == "STICKER") {
            val bmp = stickerBitmaps[el.id]
            val w = cw * (el.size / 100f)
            val h = if (bmp != null) w * (bmp.height.toFloat() / bmp.width.toFloat()) else w
            Pair(w, h)
        } else {
            val tf = fontCache.getOrPut(el.fontName ?: "Default Bold") { resolveFontByName(el.fontName ?: "Default Bold", el.fontStyle ?: "BOLD") }
            val paint = android.graphics.Paint().apply { textSize = el.size * uiScale; typeface = tf }
            val textStr = resolveAvatarText(el, profile, settings.autoGoal)
            val w = paint.measureText(textStr) + (24f * uiScale)
            val h = (paint.descent() - paint.ascent()) + (16f * uiScale)
            Pair(w, h)
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.Black)
            .pointerInput(settings, selectedId, stickerBitmaps) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: continue
                        val touchX = change.position.x; val touchY = change.position.y
                        val cw = size.width.toFloat(); val ch = size.height.toFloat()

                        if (change.pressed && !change.previousPressed) {
                            var hitMode = "NONE"; var hitId: String? = null

                            if (selectedId != null) {
                                val selEl = settings.elements.find { it.id == selectedId }
                                if (selEl != null) {
                                    val cx = selEl.x * cw; val cy = selEl.y * ch
                                    val bounds = getElementBounds(selEl, cw, ch)
                                    val bw = bounds.first; val bh = bounds.second
                                    val dx = touchX - cx; val dy = touchY - cy
                                    val angleRad = Math.toRadians(-selEl.rotation.toDouble())
                                    val localX = (dx * Math.cos(angleRad) - dy * Math.sin(angleRad)).toFloat()
                                    val localY = (dx * Math.sin(angleRad) + dy * Math.cos(angleRad)).toFloat()
                                    val btnRadius = 50f

                                    when {
                                        abs(localX - 0f) < btnRadius && abs(localY - (-bh / 2 - 14f)) < btnRadius -> { hitMode = "RESET_ROT"; hitId = selectedId }
                                        abs(localX - (bw / 2 + 10f)) < btnRadius && abs(localY - (bh / 2 + 10f)) < btnRadius -> {
                                            hitMode = "SCALE_ROTATE"; hitId = selectedId
                                            initialTouchX = touchX; initialTouchY = touchY
                                            initialElSize = selEl.size; initialElRot = selEl.rotation
                                        }
                                        localX in -bw/2f..bw/2f && localY in -bh/2f..bh/2f -> {
                                            hitMode = "MOVE"; hitId = selectedId
                                            initialTouchX = touchX; initialTouchY = touchY
                                            initialElX = selEl.x; initialElY = selEl.y
                                        }
                                    }
                                }
                            }

                            if (hitMode == "NONE") {
                                for (el in settings.elements.reversed()) {
                                    val cx = el.x * cw; val cy = el.y * ch
                                    val bounds = getElementBounds(el, cw, ch)
                                    val dx = touchX - cx; val dy = touchY - cy
                                    val angleRad = Math.toRadians(-el.rotation.toDouble())
                                    val localX = (dx * Math.cos(angleRad) - dy * Math.sin(angleRad)).toFloat()
                                    val localY = (dx * Math.sin(angleRad) + dy * Math.cos(angleRad)).toFloat()
                                    if (localX in -bounds.first/2f..bounds.first/2f && localY in -bounds.second/2f..bounds.second/2f) {
                                        hitMode = "MOVE"; hitId = el.id
                                        initialTouchX = touchX; initialTouchY = touchY
                                        initialElX = el.x; initialElY = el.y
                                        break
                                    }
                                }
                            }

                            when {
                                hitMode == "RESET_ROT" && hitId != null -> {
                                    onUpdateSettings(settings.copy(elements = settings.elements.map { if (it.id == hitId) it.copy(rotation = 0f) else it }))
                                    change.consume()
                                }
                                hitMode != "NONE" && hitId != null -> {
                                    onSelect(hitId); activeDragMode = hitMode; change.consume()
                                }
                                else -> onSelect(null)
                            }
                        }
                        else if (change.pressed && change.previousPressed && activeDragMode != "NONE") {
                            change.consume()
                            val el = settings.elements.find { it.id == selectedId }
                            if (el != null) {
                                if (activeDragMode == "MOVE") {
                                    val deltaX = (touchX - initialTouchX) / cw; val deltaY = (touchY - initialTouchY) / ch
                                    var newX = (initialElX + deltaX).coerceIn(0f, 1f); var newY = (initialElY + deltaY).coerceIn(0f, 1f)
                                    val snapThreshold = 0.025f
                                    snapX = abs(newX - 0.5f) < snapThreshold; snapY = abs(newY - 0.5f) < snapThreshold
                                    if (snapX) newX = 0.5f; if (snapY) newY = 0.5f
                                    onUpdateSettings(settings.copy(elements = settings.elements.map { if (it.id == selectedId) it.copy(x = newX, y = newY) else it }))
                                } else if (activeDragMode == "SCALE_ROTATE") {
                                    val cx = el.x * cw; val cy = el.y * ch
                                    val initDist = sqrt((initialTouchX-cx)*(initialTouchX-cx)+(initialTouchY-cy)*(initialTouchY-cy))
                                    val currDist = sqrt((touchX-cx)*(touchX-cx)+(touchY-cy)*(touchY-cy))
                                    val initAngle = Math.toDegrees(kotlin.math.atan2((initialTouchY-cy).toDouble(),(initialTouchX-cx).toDouble())).toFloat()
                                    val currAngle = Math.toDegrees(kotlin.math.atan2((touchY-cy).toDouble(),(touchX-cx).toDouble())).toFloat()
                                    if (initDist > 10f) {
                                        val scaleFactor = currDist / initDist
                                        val newSize = (initialElSize * scaleFactor).coerceIn(10f, 300f)
                                        val newRot = (initialElRot + (currAngle - initAngle)) % 360f
                                        onUpdateSettings(settings.copy(elements = settings.elements.map { if (it.id == selectedId) it.copy(size = newSize, rotation = newRot) else it }))
                                    }
                                }
                            }
                        }
                        else if (!change.pressed && change.previousPressed) {
                            activeDragMode = "NONE"; snapX = false; snapY = false
                        }
                    }
                }
            }
    ) {
        if (backgroundBitmap != null) {
            Image(bitmap = backgroundBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }

        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val nc = drawContext.canvas.nativeCanvas
            val uiScale = size.width / 500f

            settings.elements.forEach { el ->
                val cx = el.x * size.width; val cy = el.y * size.height

                if (el.type != "STICKER") {
                    val tf = fontCache.getOrPut(el.fontName ?: "Default Bold") {
                        resolveFontByName(el.fontName ?: "Default Bold", el.fontStyle ?: "BOLD")
                    }
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
                        textSize = el.size * uiScale; typeface = tf
                        try { color = android.graphics.Color.parseColor(el.colorHex ?: "#FFFFFF") } catch (e: Exception) { color = android.graphics.Color.WHITE }
                        alpha = (el.alpha * 255).toInt().coerceIn(0, 255)
                        if (el.shadowRadius > 0f) setShadowLayer(el.shadowRadius * uiScale, 0f, 2f * uiScale, android.graphics.Color.parseColor("#B3000000"))
                    }

                    val textToDraw = resolveAvatarText(el, profile, settings.autoGoal)
                    if (textToDraw.isNotBlank()) {
                        val lines = textToDraw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                        if (lines.isEmpty()) return@forEach

                        val lineHeight = -paint.ascent() + paint.descent()
                        val textHeight = lines.size * lineHeight
                        val maxWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f

                        val saved = nc.save()
                        nc.translate(cx, cy); nc.rotate(el.rotation)

                        if (el.glassMorphEnabled) {
                            val padX = 24f * uiScale
                            val padY = 12f * uiScale

                            val glassRect = android.graphics.RectF(
                                -maxWidth / 2f - padX,
                                -textHeight / 2f - padY,
                                maxWidth / 2f + padX,
                                textHeight / 2f + padY
                            )

                            val shadowPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                color = android.graphics.Color.BLACK
                                alpha = (el.glassAlpha * 120).toInt().coerceIn(0, 255)
                                maskFilter = android.graphics.BlurMaskFilter(20f * uiScale, android.graphics.BlurMaskFilter.Blur.NORMAL)
                            }
                            nc.drawRoundRect(glassRect, el.glassCornerRadius * uiScale, el.glassCornerRadius * uiScale, shadowPaint)

                            val glassPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                try { color = android.graphics.Color.parseColor(el.glassColorHex ?: "#000000") } catch (e: Exception) { color = android.graphics.Color.BLACK }
                                alpha = (el.glassAlpha * 255).toInt().coerceIn(0, 255)
                            }
                            nc.drawRoundRect(glassRect, el.glassCornerRadius * uiScale, el.glassCornerRadius * uiScale, glassPaint)

                            val borderPaint = android.graphics.Paint().apply {
                                isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = 2f * uiScale
                                try { color = android.graphics.Color.parseColor(el.glassColorHex ?: "#000000") } catch (e: Exception) { color = android.graphics.Color.BLACK }
                                alpha = (el.glassBorderAlpha * 255).toInt().coerceIn(0, 255)
                            }
                            nc.drawRoundRect(glassRect, el.glassCornerRadius * uiScale, el.glassCornerRadius * uiScale, borderPaint)
                        }

                        val startY = -textHeight / 2f - paint.ascent()
                        lines.forEachIndexed { index, line ->
                            nc.drawText(line, 0f, startY + index * lineHeight, paint)
                        }
                        nc.restoreToCount(saved)
                    }
                } else {
                    val bmp = stickerBitmaps[el.id]
                    if (bmp != null) {
                        val targetW = (size.width * (el.size / 100f)).toInt().coerceAtLeast(10)
                        val targetH = (bmp.height * (targetW.toFloat() / bmp.width)).toInt().coerceAtLeast(10)
                        val saved = nc.save()
                        nc.translate(cx, cy); nc.rotate(el.rotation)
                        drawImage(image = bmp, dstOffset = androidx.compose.ui.unit.IntOffset(-targetW/2, -targetH/2), dstSize = androidx.compose.ui.unit.IntSize(targetW, targetH), alpha = el.alpha)
                        nc.restoreToCount(saved)
                    }
                }
            }

            
            if (activeDragMode == "MOVE") {
                val guideColor = Color(0xFF00E5FF).copy(alpha = 0.8f)
                if (snapX) drawLine(color = guideColor, start = Offset(size.width/2, 0f), end = Offset(size.width/2, size.height), strokeWidth = 2f)
                if (snapY) drawLine(color = guideColor, start = Offset(0f, size.height/2), end = Offset(size.width, size.height/2), strokeWidth = 2f)
            }

            
            val selEl = settings.elements.find { it.id == selectedId }
            if (selEl != null) {
                val cx = selEl.x * size.width; val cy = selEl.y * size.height
                val bounds = getElementBounds(selEl, size.width, size.height)
                val bw = bounds.first; val bh = bounds.second
                withTransform({ translate(cx, cy); rotate(selEl.rotation, pivot = Offset.Zero) }) {
                    drawRect(color = Color.White.copy(alpha = 0.8f), topLeft = Offset(-bw/2, -bh/2), size = Size(bw, bh), style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)))
                    drawCircle(color = accentColor, radius = 16f, center = Offset(0f, -bh/2 - 14f))
                    drawLine(color = Color.White, start = Offset(0f, -bh/2 - 20f), end = Offset(0f, -bh/2 - 8f), strokeWidth = 3f)
                    drawCircle(color = accentColor, radius = 18f, center = Offset(bw/2 + 10f, bh/2 + 10f))
                    drawLine(color = Color.White, start = Offset(bw/2 + 4f, bh/2 + 4f), end = Offset(bw/2 + 16f, bh/2 + 16f), strokeWidth = 4f)
                }
            }
        }

        if (isGenerating) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = accentColor)
        }
    }
}





@Composable
fun DetailedBalanceCard(totalBalance: String, totalVal: Double, activeSum: Double, frozenSum: Double, nextUnlock: Pair<FunPayRepository.SaleItem, Long>?, currentTime: Long, theme: AppTheme) {
    val accentColor = ThemeManager.parseColor(theme.accentColor)
    val surfaceColor = ThemeManager.parseColor(theme.surfaceColor)
    val textPrimary = ThemeManager.parseColor(theme.textPrimaryColor)
    val textSecondary = ThemeManager.parseColor(theme.textSecondaryColor)

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = surfaceColor), shape = RoundedCornerShape(theme.borderRadius.dp), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Общий баланс", fontSize = 14.sp, color = textSecondary)
            }
            Spacer(Modifier.height(4.dp))
            Text(text = if (totalVal <= 0.0) "0.00 ₽" else totalBalance, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = if (totalVal <= 0.0) textSecondary else textPrimary, letterSpacing = 0.5.sp)
            if (totalVal > 0.0) {
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50))); Spacer(Modifier.width(6.dp)); Text("Доступно", fontSize = 12.sp, color = textSecondary) }
                        Text(text = String.format(java.util.Locale.US, "%.2f ₽", activeSum), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary, modifier = Modifier.padding(top = 4.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFA000))); Spacer(Modifier.width(6.dp)); Text("В заморозке", fontSize = 12.sp, color = textSecondary) }
                        Text(text = String.format(java.util.Locale.US, "%.2f ₽", frozenSum), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (nextUnlock != null) {
                    Spacer(Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA000).copy(alpha = 0.1f)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HourglassEmpty, null, tint = Color(0xFFFFA000), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Ближайшая разморозка: ~${String.format(java.util.Locale.US, "%.2f ₽", nextUnlock.first.priceValue)}", fontSize = 12.sp, color = textPrimary, fontWeight = FontWeight.Medium)
                                Text("Через ${formatTimeLeft(nextUnlock.second - currentTime)}", fontSize = 11.sp, color = Color(0xFFFFA000))
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
fun ActionGridItem(modifier: Modifier = Modifier, title: String, subtitle: String, icon: ImageVector, iconTint: Color, bgGradient: List<Color>, theme: AppTheme, onClick: () -> Unit) {
    Card(modifier = modifier.height(100.dp).clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, iconTint.copy(alpha = 0.2f)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(colors = bgGradient))) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White.copy(alpha = 0.05f), modifier = Modifier.size(76.dp).align(Alignment.BottomEnd).offset(x = 16.dp, y = 16.dp).rotate(-15f))
            Column(modifier = Modifier.padding(14.dp)) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.weight(1f))
                Text(title, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 14.sp)
                Text(subtitle, color = ThemeManager.parseColor(theme.textSecondaryColor).copy(alpha = 0.9f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun StatMiniCard(modifier: Modifier = Modifier, title: String, value: String, icon: ImageVector, theme: AppTheme) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.6f)), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(ThemeManager.parseColor(theme.accentColor).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = ThemeManager.parseColor(theme.accentColor), modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Column { Text(value, fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor), fontSize = 16.sp); Text(title, color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 11.sp) }
        }
    }
}

private enum class DonateDialogStep { NONE, FAKE_LOADING, CANCEL_CONFIRM, REALLY_SURE, CONFESSION }

@Composable
fun DonateEasterEggButton(theme: AppTheme) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var step by remember { mutableStateOf(DonateDialogStep.NONE) }

    OutlinedButton(
        onClick = { step = DonateDialogStep.FAKE_LOADING; scope.launch { delay(3000); if (step == DonateDialogStep.FAKE_LOADING) step = DonateDialogStep.CANCEL_CONFIRM } },
        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF6B35).copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFF6B35).copy(alpha = 0.05f), contentColor = Color(0xFFFF6B35)),
        enabled = step == DonateDialogStep.NONE
    ) {
        if (step == DonateDialogStep.FAKE_LOADING) { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFFFF6B35)); Spacer(Modifier.width(8.dp)); Text("Выполняется вывод 500 ₽...", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        else { Icon(Icons.Default.VolunteerActivism, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Вывести 500 ₽ на донат разработчику", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
    }

    if (step == DonateDialogStep.CANCEL_CONFIRM) {
        AlertDialog(onDismissRequest = {}, containerColor = ThemeManager.parseColor(theme.surfaceColor),
            title = { Text("⚠️ Вывод средств", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Запрос на вывод 500 ₽ принят и поставлен в очередь обработки.", color = ThemeManager.parseColor(theme.textPrimaryColor)); Text("Средства будут выведены на личную карту разработчика.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 13.sp); Spacer(Modifier.height(4.dp)); Text("Вы уверены, что хотите продолжить? Отменить вывод?", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Medium) } },
            confirmButton = { TextButton(onClick = { step = DonateDialogStep.REALLY_SURE }) { Text("Нет, продолжить", color = Color(0xFFFF6B35)) } },
            dismissButton = { TextButton(onClick = { step = DonateDialogStep.CONFESSION }) { Text("Да, отменить", color = ThemeManager.parseColor(theme.textSecondaryColor)) } }
        )
    }

    if (step == DonateDialogStep.REALLY_SURE) {
        AlertDialog(onDismissRequest = {}, containerColor = ThemeManager.parseColor(theme.surfaceColor),
            title = { Text("‼️ Подождите", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFFFF6B35)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Вы действительно хотите перевести 500 ₽ разработчику?", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Bold); Text("Сумма: 500 ₽\nПолучатель: 4441********7711\nСрок зачисления: 47 часов и 59 минут\nОтмена после подтверждения: невозможна", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 13.sp); Spacer(Modifier.height(4.dp)); Text("Вы точно уверены?", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Medium) } },
            confirmButton = { Button(onClick = { step = DonateDialogStep.CONFESSION }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))) { Text("Да, подтверждаю", color = Color.White) } },
            dismissButton = { TextButton(onClick = { step = DonateDialogStep.CONFESSION }) { Text("Нет, отменить", color = ThemeManager.parseColor(theme.textSecondaryColor)) } }
        )
    }

    if (step == DonateDialogStep.CONFESSION) {
        AlertDialog(onDismissRequest = { step = DonateDialogStep.NONE }, containerColor = ThemeManager.parseColor(theme.surfaceColor),
            title = { Text("😅 Ладно, признаёмся...", fontWeight = FontWeight.Bold, color = ThemeManager.parseColor(theme.textPrimaryColor)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Никакие деньги никуда не ушли и не уйдут. Это была шутка 🙃", color = ThemeManager.parseColor(theme.textPrimaryColor), fontWeight = FontWeight.Medium); Text("Но если вы реально хотите помочь развитию FunPay Tools — рассмотрите лицензию Pro.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 13.sp); Text("Оплата принимается картами СНГ и криптовалютой.", color = ThemeManager.parseColor(theme.textSecondaryColor), fontSize = 13.sp) } },
            confirmButton = { Button(onClick = { val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/fptoolsbot")); context.startActivity(intent); step = DonateDialogStep.NONE }, colors = ButtonDefaults.buttonColors(containerColor = ThemeManager.parseColor(theme.accentColor))) { Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Узнать про Pro →", color = Color.White) } },
            dismissButton = { TextButton(onClick = { step = DonateDialogStep.NONE }) { Text("Закрыть", color = ThemeManager.parseColor(theme.textSecondaryColor)) } }
        )
    }
}

suspend fun FunPayRepository.getSelfProfileUpdated(): UserProfile? {
    return withContext(Dispatchers.IO) {
        try {
            val (csrf, userId) = getCsrfAndId() ?: return@withContext null
            val mainResponse = api.getMainPage(getGoldenKey()?.let { "golden_key=$it; PHPSESSID=${getPhpSessionId()}" } ?: "", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
            val mainHtml = mainResponse.body()?.string() ?: ""
            val mainDoc = Jsoup.parse(mainHtml)
            val balanceText = mainDoc.select(".badge-balance").text()
            val activeSales = mainDoc.select(".badge-trade").text().toIntOrNull() ?: 0
            val activePurchases = mainDoc.select(".badge-orders").text().toIntOrNull() ?: 0

            val profileResponse = api.getUserProfile(userId, getGoldenKey()?.let { "golden_key=$it; PHPSESSID=${getPhpSessionId()}" } ?: "", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36")
            val profileHtml = profileResponse.body()?.string() ?: ""
            val profileDoc = Jsoup.parse(profileHtml)

            val username = profileDoc.select(".user-link-dropdown .user-link-name").first()?.text()?.trim()
                ?: profileDoc.select("div.media-user-name").first()?.text()?.replace("Online", "")?.replace("Онлайн", "")?.trim()
                ?: "Unknown"

            val statusText = profileDoc.select(".media-user-status").text()
            val isOnline = statusText.lowercase().contains("онлайн") || statusText.lowercase().contains("online")

            var avatarUrl = "https://funpay.com/img/layout/avatar.png"
            val avatarStyle = profileDoc.select(".avatar-photo, .profile-photo").attr("style")
            if (avatarStyle.contains("url(")) {
                avatarUrl = avatarStyle.substringAfter("url(").substringBefore(")").replace("\"", "").replace("'", "")
                if (avatarUrl.startsWith("/")) avatarUrl = "https://funpay.com$avatarUrl"
            }

            val rating = profileDoc.select(".rating-value .big").first()?.text()?.toDoubleOrNull() ?: 0.0
            val reviewsCount = profileDoc.select(".rating-full-count a").first()?.text()?.filter { it.isDigit() }?.toIntOrNull() ?: 0

            val regParam = profileDoc.select(".param-item").firstOrNull { it.text().contains("Дата регистрации") || it.text().contains("Registration") }
            val regDateRaw = regParam?.select(".text-nowrap")?.first()?.text() ?: ""
            val yearMatch = Regex("\\d{4}").find(regDateRaw)?.value
            val regDate = if (yearMatch != null) "На сайте с $yearMatch года" else regDateRaw.substringBefore(",").ifBlank { "неизвестного" }

            UserProfile(id = userId, username = username, avatarUrl = avatarUrl, isOnline = isOnline, totalBalance = balanceText, activeSales = activeSales, activePurchases = activePurchases, rating = rating, reviewCount = reviewsCount, registeredDate = regDate)
        } catch (e: Exception) { null }
    }
}
