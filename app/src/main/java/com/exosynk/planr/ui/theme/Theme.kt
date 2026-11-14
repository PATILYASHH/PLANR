package com.exosynk.planr.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.data.ThemeMode

/** Brand accent; follows the active scheme (light, dark or Material You). */
val Accent: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

/** Soft accent surface (chips, rings, highlights). */
val AccentSoft: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer

val Danger = Color(0xFFFF6B6B)
val Success = Color(0xFF5BD6A0)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7C9CFF),
    onPrimary = Color(0xFF0B1230),
    primaryContainer = Color(0xFF2A3354),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFFB8C3E6),
    background = Color(0xFF0E0F13),
    onBackground = Color(0xFFE7E8EE),
    surface = Color(0xFF0E0F13),
    onSurface = Color(0xFFE7E8EE),
    surfaceVariant = Color(0xFF1A1C23),
    onSurfaceVariant = Color(0xFFA6A9B6),
    surfaceContainer = Color(0xFF16181F),
    surfaceContainerHigh = Color(0xFF1D2029),
    outline = Color(0xFF3A3D4A),
    error = Danger,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3F5BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF0B1A5C),
    secondary = Color(0xFF55607F),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF16181F),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF16181F),
    surfaceVariant = Color(0xFFE6E9F2),
    onSurfaceVariant = Color(0xFF5A5F70),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFECEFF6),
    outline = Color(0xFFC4C8D6),
    error = Color(0xFFD93A3A),
)

private val PlanrType = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

/** Whether PLANR is currently dark, from Settings → Appearance (or the system). */
@Composable
fun planrIsDark(): Boolean {
    val settings by PlanrApp.instance.settings.state.collectAsState()
    return when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

/**
 * @param forceDark the lock, intercept and ringing screens are always dark (they
 *   sit on black so they read at night and over the lock screen).
 */
@Composable
fun PlanrTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val settings by PlanrApp.instance.settings.state.collectAsState()
    val dark = forceDark || planrIsDark()
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, typography = PlanrType, content = content)
}

/** True when the current scheme is light (for colours that need light/dark variants). */
@Composable
@ReadOnlyComposable
fun isLightScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f
