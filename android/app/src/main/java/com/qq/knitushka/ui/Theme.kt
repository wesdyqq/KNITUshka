package com.qq.knitushka.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val KnituRed = Color(0xFF862B3C)
internal val KnituBeige = Color(0xFFFAF7F2)

private val Light = lightColorScheme(
    primary = KnituRed, onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E3E4), onPrimaryContainer = Color(0xFF4B1923),
    secondary = Color(0xFF665C57), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE8E1), onSecondaryContainer = Color(0xFF403A35),
    background = KnituBeige, onBackground = Color(0xFF292522),
    surface = Color(0xFFFFFCF8), onSurface = Color(0xFF292522),
    surfaceVariant = Color(0xFFF0EBE4), onSurfaceVariant = Color(0xFF6B625C),
    outline = Color(0xFF877B73), outlineVariant = Color(0xFFE0D8CF),
    error = Color(0xFF7A4600), onError = Color.White,
    errorContainer = Color(0xFFFFEDD3), onErrorContainer = Color(0xFF583000),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFF1ADB9), onPrimary = Color(0xFF4D1825),
    primaryContainer = Color(0xFF512B34), onPrimaryContainer = Color(0xFFFBDDE3),
    secondary = Color(0xFFD0C3B9), onSecondary = Color(0xFF382F29),
    secondaryContainer = Color(0xFF39332F), onSecondaryContainer = Color(0xFFECE1D7),
    background = Color(0xFF1B1918), onBackground = Color(0xFFF1E9E1),
    surface = Color(0xFF242120), onSurface = Color(0xFFF1E9E1),
    surfaceVariant = Color(0xFF302B28), onSurfaceVariant = Color(0xFFC5B9B0),
    outline = Color(0xFF9B8D84), outlineVariant = Color(0xFF4A413B),
    error = Color(0xFFFFBD72), onError = Color(0xFF422400),
    errorContainer = Color(0xFF503417), onErrorContainer = Color(0xFFFFDDB5),
)

@Composable
fun KnituTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp),
        headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 25.sp),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    ), content = content)
}
