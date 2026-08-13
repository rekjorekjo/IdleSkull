package com.idleskull.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Bone = Color(0xFFF0EDE4)
private val Ink = Color(0xFF171717)
private val PaperMuted = Color(0xFFD7D2C6)
private val DarkBone = Color(0xFFE8E5DC)
private val DarkBg = Color(0xFF101010)
private val DarkPanel = Color(0xFF1B1B1B)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Bone,
    background = Bone,
    onBackground = Ink,
    surface = Color(0xFFF8F5ED),
    onSurface = Ink,
    surfaceVariant = PaperMuted,
    onSurfaceVariant = Color(0xFF55524B),
    outline = Ink,
)

private val DarkColors = darkColorScheme(
    primary = DarkBone,
    onPrimary = DarkBg,
    background = DarkBg,
    onBackground = DarkBone,
    surface = DarkPanel,
    onSurface = DarkBone,
    surfaceVariant = Color(0xFF252525),
    onSurfaceVariant = Color(0xFFBDB9AF),
    outline = DarkBone,
)

@Composable
fun IdleSkullTheme(darkMode: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkMode) DarkColors else LightColors,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 48.sp),
            headlineLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 26.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp),
        ),
        content = content,
    )
}
