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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


object WidgetManager {
    private const val PREFS_NAME = "widget_prefs"
    private val gson = com.google.gson.Gson()

    fun getTransparency(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat("transparency", 0.8f)
    }

    fun setTransparency(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat("transparency", value).apply()
        updateAllWidgets(context)
    }

    fun saveProfileCache(context: Context, profile: UserProfile) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("cached_profile", gson.toJson(profile)).apply()
    }

    fun getProfileCache(context: Context): UserProfile? {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("cached_profile", null) ?: return null
        return try {
            gson.fromJson(json, UserProfile::class.java)
        } catch (e: Exception) { null }
    }

    fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val balanceIds = appWidgetManager.getAppWidgetIds(ComponentName(context, BalanceWidget::class.java))
        if (balanceIds.isNotEmpty()) {
            context.sendBroadcast(Intent(context, BalanceWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, balanceIds)
            })
        }

        val profileIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ProfileWidget::class.java))
        if (profileIds.isNotEmpty()) {
            context.sendBroadcast(Intent(context, ProfileWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, profileIds)
            })
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsScreen(
    navController: NavController,
    currentTheme: AppTheme
) {
    val context = LocalContext.current
    var transparency by remember { mutableFloatStateOf(WidgetManager.getTransparency(context)) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Виджеты",
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Назад",
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black 
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color(0xFF1A237E), Color(0xFF000000))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    
                    Card(
                        modifier = Modifier
                            .width(200.dp)
                            .height(100.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ThemeManager.parseColor(currentTheme.surfaceColor).copy(alpha = transparency)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "15 420 ₽",
                                color = ThemeManager.parseColor(currentTheme.accentColor),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "FunPay Balance",
                                color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = ThemeManager.parseColor(currentTheme.surfaceColor)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Прозрачность фона",
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = transparency,
                        onValueChange = {
                            transparency = it
                            context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                                .edit().putFloat("transparency", it).apply()
                        },
                        onValueChangeFinished = {
                            WidgetManager.updateAllWidgets(context)
                        },
                        valueRange = 0.2f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = ThemeManager.parseColor(currentTheme.accentColor),
                            activeTrackColor = ThemeManager.parseColor(currentTheme.accentColor)
                        )
                    )
                    Text(
                        "${(transparency * 100).toInt()}%",
                        color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appWidgetManager = AppWidgetManager.getInstance(context)

                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    Text(
                        "Добавить на главный экран",
                        color = ThemeManager.parseColor(currentTheme.textPrimaryColor),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PinWidgetButton(
                            title = "Баланс",
                            icon = Icons.Default.Widgets,
                            theme = currentTheme,
                            modifier = Modifier.weight(1f)
                        ) {
                            val provider = ComponentName(context, BalanceWidget::class.java)
                            appWidgetManager.requestPinAppWidget(provider, null, null)
                        }

                        PinWidgetButton(
                            title = "Профиль",
                            icon = Icons.Default.Add,
                            theme = currentTheme,
                            modifier = Modifier.weight(1f)
                        ) {
                            val provider = ComponentName(context, ProfileWidget::class.java)
                            appWidgetManager.requestPinAppWidget(provider, null, null)
                        }
                    }
                }
            }

            Text(
                "Совет: Виджеты обновляются автоматически раз в 30 минут или при открытии приложения. Данные берутся из кэша.",
                color = ThemeManager.parseColor(currentTheme.textSecondaryColor),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun PinWidgetButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: AppTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = ThemeManager.parseColor(theme.surfaceColor).copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = ThemeManager.parseColor(theme.accentColor),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                color = ThemeManager.parseColor(theme.textPrimaryColor),
                fontWeight = FontWeight.Medium
            )
        }
    }
}



abstract class FunPayBaseWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val repository = FunPayRepository(context)
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            
            val profile = try {
                repository.getSelfProfile()
            } catch (e: Exception) {
                null
            }

            val theme = ThemeManager.loadTheme(context)
            val transparency = WidgetManager.getTransparency(context)

            
            val surfaceColorInt = android.graphics.Color.parseColor(theme.surfaceColor)
            val alpha = (transparency * 255).toInt()
            val backgroundColor = (alpha shl 24) or (surfaceColorInt and 0x00FFFFFF)

            
            val accentColor = android.graphics.Color.parseColor(theme.accentColor)
            val textColor = android.graphics.Color.parseColor(theme.textPrimaryColor)
            val secColor = android.graphics.Color.parseColor(theme.textSecondaryColor)

            withContext(Dispatchers.Main) {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(
                        context,
                        appWidgetManager,
                        appWidgetId,
                        profile,
                        backgroundColor,
                        accentColor,
                        textColor,
                        secColor
                    )
                }
            }
        }
    }

    protected fun createBgBitmap(bgColor: Int, context: Context): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (400 * density).toInt()
        val h = (200 * density).toInt()
        val radius = 16f * density
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)
        return bmp
    }

    abstract suspend fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        profile: UserProfile?,
        bgColor: Int,
        accentColor: Int,
        textColor: Int,
        secColor: Int
    )

    protected fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    
    protected suspend fun loadCircularBitmap(context: Context, url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request).drawable?.toBitmap()

                result?.let { bitmap ->
                    val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(output)
                    val color = -0xbdbdbe
                    val paint = Paint()
                    val rect = Rect(0, 0, bitmap.width, bitmap.height)
                    val rectF = RectF(rect)

                    paint.isAntiAlias = true
                    canvas.drawARGB(0, 0, 0, 0)
                    paint.color = color
                    canvas.drawOval(rectF, paint)

                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                    canvas.drawBitmap(bitmap, rect, rect, paint)
                    output
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}


class BalanceWidget : FunPayBaseWidget() {
    override suspend fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        profile: UserProfile?,
        bgColor: Int,
        accentColor: Int,
        textColor: Int,
        secColor: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_balance)

        
        
        
        views.setImageViewBitmap(R.id.widget_background, createBgBitmap(bgColor, context))

        if (profile != null) {
            views.setTextViewText(R.id.widget_balance_text, profile.totalBalance)
            views.setTextColor(R.id.widget_balance_text, accentColor)

            views.setTextViewText(R.id.widget_title_text, profile.username)
            views.setTextColor(R.id.widget_title_text, secColor)
        } else {
            views.setTextViewText(R.id.widget_balance_text, "---")
            views.setTextColor(R.id.widget_balance_text, textColor)
            views.setTextViewText(R.id.widget_title_text, "Бомж")
        }

        views.setOnClickPendingIntent(R.id.widget_root, getPendingIntent(context))
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}


class ProfileWidget : FunPayBaseWidget() {
    override suspend fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        profile: UserProfile?,
        bgColor: Int,
        accentColor: Int,
        textColor: Int,
        secColor: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_profile)

        views.setImageViewBitmap(R.id.widget_background, createBgBitmap(bgColor, context))

        if (profile != null) {
            views.setTextViewText(R.id.widget_username, profile.username)
            views.setTextColor(R.id.widget_username, textColor)

            views.setTextViewText(R.id.widget_balance, profile.totalBalance)
            views.setTextColor(R.id.widget_balance, accentColor)

            views.setTextViewText(R.id.widget_rating, "★ ${profile.rating} (${profile.reviewCount})")
            views.setTextColor(R.id.widget_rating, secColor)

            
            val avatar = loadCircularBitmap(context, profile.avatarUrl)
            if (avatar != null) {
                views.setImageViewBitmap(R.id.widget_avatar, avatar)
            } else {
                views.setImageViewResource(R.id.widget_avatar, android.R.drawable.sym_def_app_icon)
            }
        } else {
            views.setTextViewText(R.id.widget_username, "FunPay Tools")
            views.setTextViewText(R.id.widget_balance, "Загрузка...")
        }

        views.setOnClickPendingIntent(R.id.widget_root, getPendingIntent(context))
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}