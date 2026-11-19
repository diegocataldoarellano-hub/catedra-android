package com.catedra.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.catedra.app.data.model.AppThemeMode

data class CatedraPalette(
    val isDark: Boolean,
    val background: Color,
    val chatCanvas: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val topBar: Color,
    val accent: Color,
    val accentSoft: Color,
    val bubbleOutgoing: Color,
    val bubbleIncoming: Color,
    val bubbleBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val citationBg: Color,
    val citationText: Color,
    val nvidiaGreen: Color,
    val success: Color,
    val warning: Color,
    val danger: Color
)

private val TelegramLightPalette = CatedraPalette(
    isDark = false,
    background = Color(0xFFFFFFFF),
    chatCanvas = Color(0xFFF0F4F8),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF6F8FA),
    topBar = Color(0xFFFFFFFF),
    accent = Color(0xFF2AABEE),
    accentSoft = Color(0xFFE7F5FD),
    bubbleOutgoing = Color(0xFFEFFDDE),     // Clásico verde suave minimalista Telegram
    bubbleIncoming = Color(0xFFFFFFFF),
    bubbleBorder = Color(0xFFE2E8EE),
    textPrimary = Color(0xFF14181F),
    textSecondary = Color(0xFF546270),
    textMuted = Color(0xFF8A96A3),
    divider = Color(0xFFEAECEF),
    citationBg = Color(0xFFE8F4FD),
    citationText = Color(0xFF1A78C2),
    nvidiaGreen = Color(0xFF5FA300),
    success = Color(0xFF2E9E5B),
    warning = Color(0xFFD9822B),
    danger = Color(0xFFD94A4A)
)

private val TelegramDarkPalette = CatedraPalette(
    isDark = true,
    background = Color(0xFF17212B),
    chatCanvas = Color(0xFF0E1621),
    surface = Color(0xFF17212B),
    surfaceElevated = Color(0xFF1F2C3A),
    topBar = Color(0xFF17212B),
    accent = Color(0xFF5EB5F7),
    accentSoft = Color(0xFF1E3448),
    bubbleOutgoing = Color(0xFF2B5278),
    bubbleIncoming = Color(0xFF182533),
    bubbleBorder = Color(0xFF233242),
    textPrimary = Color(0xFFF3F6F9),
    textSecondary = Color(0xFF9EB1C3),
    textMuted = Color(0xFF6C7E8F),
    divider = Color(0xFF222E3A),
    citationBg = Color(0xFF1F364D),
    citationText = Color(0xFF7AC1FA),
    nvidiaGreen = Color(0xFF76B900),
    success = Color(0xFF4CC37A),
    warning = Color(0xFFE8A44A),
    danger = Color(0xFFE56B6F)
)

private val TelegramAmoledPalette = TelegramDarkPalette.copy(
    background = Color(0xFF000000),
    chatCanvas = Color(0xFF000000),
    surface = Color(0xFF0A0E12),
    surfaceElevated = Color(0xFF131A22),
    topBar = Color(0xFF0A0E12),
    bubbleOutgoing = Color(0xFF1E3A54),
    bubbleIncoming = Color(0xFF121820),
    bubbleBorder = Color(0xFF1D2733),
    divider = Color(0xFF171E26)
)

val LocalCatedraColors = staticCompositionLocalOf { TelegramLightPalette }

private val CatedraTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.5.sp, lineHeight = 22.5.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun CatedraTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val palette = when (themeMode) {
        AppThemeMode.LIGHT -> TelegramLightPalette
        AppThemeMode.DARK -> TelegramDarkPalette
        AppThemeMode.AMOLED -> TelegramAmoledPalette
        AppThemeMode.SYSTEM -> if (systemDark) TelegramDarkPalette else TelegramLightPalette
    }

    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !palette.isDark
                controller.isAppearanceLightNavigationBars = !palette.isDark
            }
        }
    }

    CompositionLocalProvider(LocalCatedraColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CatedraTypography,
            content = content
        )
    }
}
