package com.planr.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Accent = Color(0xFF7C9CFF)
val AccentSoft = Color(0xFF2A3354)
val Danger = Color(0xFFFF6B6B)
val Success = Color(0xFF5BD6A0)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0B1230),
    primaryContainer = AccentSoft,
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
    error = Danger,
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

@Composable
fun PlanrTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = PlanrType, content = content)
}
