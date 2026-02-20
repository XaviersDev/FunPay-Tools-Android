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
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson

data class AppTheme(
    val name: String,
    val primaryColor: String,
    val secondaryColor: String,
    val backgroundColor: String,
    val surfaceColor: String,
    val textPrimaryColor: String,
    val textSecondaryColor: String,
    val accentColor: String,
    val isAmoled: Boolean = false,
    val containerOpacity: Float = 0.9f,
    val borderRadius: Int = 16,
    val originalBackgroundColor: String = "#050505",
    val originalSurfaceColor: String = "#1A1A1A"
)

object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_CURRENT_THEME = "current_theme"
    private const val KEY_AMOLED_MODE = "amoled_mode"

    val defaultThemes = listOf(
        AppTheme(
            name = "Purple Dream",
            primaryColor = "#651FFF",
            secondaryColor = "#311B92",
            backgroundColor = "#050505",
            surfaceColor = "#1A1A1A",
            textPrimaryColor = "#EEEEEE",
            textSecondaryColor = "#B0B0B0",
            accentColor = "#651FFF",
            originalBackgroundColor = "#050505",
            originalSurfaceColor = "#1A1A1A"
        ),
        AppTheme(
            name = "Ocean Blue",
            primaryColor = "#0091EA",
            secondaryColor = "#01579B",
            backgroundColor = "#000510",
            surfaceColor = "#0A1929",
            textPrimaryColor = "#E3F2FD",
            textSecondaryColor = "#90CAF9",
            accentColor = "#00B0FF",
            originalBackgroundColor = "#000510",
            originalSurfaceColor = "#0A1929"
        ),
        AppTheme(
            name = "Emerald",
            primaryColor = "#00C853",
            secondaryColor = "#1B5E20",
            backgroundColor = "#001505",
            surfaceColor = "#0D2818",
            textPrimaryColor = "#E8F5E9",
            textSecondaryColor = "#A5D6A7",
            accentColor = "#00E676",
            originalBackgroundColor = "#001505",
            originalSurfaceColor = "#0D2818"
        ),
        AppTheme(
            name = "Sunset Orange",
            primaryColor = "#FF6D00",
            secondaryColor = "#BF360C",
            backgroundColor = "#150500",
            surfaceColor = "#2E1A0D",
            textPrimaryColor = "#FFF3E0",
            textSecondaryColor = "#FFCC80",
            accentColor = "#FF9100",
            originalBackgroundColor = "#150500",
            originalSurfaceColor = "#2E1A0D"
        ),
        AppTheme(
            name = "Rose Gold",
            primaryColor = "#EC407A",
            secondaryColor = "#880E4F",
            backgroundColor = "#15000A",
            surfaceColor = "#2A0D1A",
            textPrimaryColor = "#FCE4EC",
            textSecondaryColor = "#F48FB1",
            accentColor = "#F50057",
            originalBackgroundColor = "#15000A",
            originalSurfaceColor = "#2A0D1A"
        ),
        AppTheme(
            name = "Cyber Violet",
            primaryColor = "#D500F9",
            secondaryColor = "#6A1B9A",
            backgroundColor = "#0D000F",
            surfaceColor = "#1F0A2E",
            textPrimaryColor = "#F3E5F5",
            textSecondaryColor = "#CE93D8",
            accentColor = "#E040FB",
            originalBackgroundColor = "#0D000F",
            originalSurfaceColor = "#1F0A2E"
        )
    )

    fun saveTheme(context: Context, theme: AppTheme) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(theme)
        prefs.edit().putString(KEY_CURRENT_THEME, json).apply()
    }

    fun loadTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CURRENT_THEME, null)
        return if (json != null) {
            try {
                Gson().fromJson(json, AppTheme::class.java)
            } catch (e: Exception) {
                defaultThemes[0]
            }
        } else {
            defaultThemes[0]
        }
    }

    fun setAmoledMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AMOLED_MODE, enabled).apply()
    }

    fun isAmoledMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AMOLED_MODE, false)
    }

    fun parseColor(hexColor: String): Color {
        return try {
            Color(android.graphics.Color.parseColor(hexColor))
        } catch (e: Exception) {
            Color.White
        }
    }
}