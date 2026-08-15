package dev.xichen.crossfitlog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Chalk = Color(0xFFF2F2F7)
private val Paper = Color(0xFFFFFFFF)
private val Ink = Color(0xFF161616)
private val MutedInk = Color(0xFF6D6D72)
private val SteelBlue = Color(0xFF527DA8)

private val LightColors = lightColorScheme(
    primary = SteelBlue, onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF2FA), onPrimaryContainer = Color(0xFF244664),
    secondary = Color(0xFF697583), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EDF2), onSecondaryContainer = Color(0xFF232D36),
    background = Chalk, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFF7F7FA), onSurfaceVariant = MutedInk,
    outline = Color(0xFF85858B), outlineVariant = Color(0xFFE0E0E5),
    error = Color(0xFF725487), onError = Color.White,
    errorContainer = Color(0xFFF3E3FA), onErrorContainer = Color(0xFF2D173B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82ADD7), onPrimary = Color(0xFF17334C),
    primaryContainer = Color(0xFF1D2B3A), onPrimaryContainer = Color(0xFFBBD9F5),
    secondary = Color(0xFFB8C3CE), onSecondary = Color(0xFF26313A),
    secondaryContainer = Color(0xFF343E48), onSecondaryContainer = Color(0xFFD4DEE7),
    background = Color(0xFF0D0D0F), onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF1C1C1E), onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF262629), onSurfaceVariant = Color(0xFFA2A2A8),
    outline = Color(0xFF8E8E94), outlineVariant = Color(0xFF3B3B40),
    error = Color(0xFFDAB9E9), onError = Color(0xFF40204F),
    errorContainer = Color(0xFF573568), onErrorContainer = Color(0xFFF3E3FA),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = .7.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun CrossFitLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
