package com.expensetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PinchTeal,
    onPrimary = Color.White,
    primaryContainer = MistTeal,
    onPrimaryContainer = DeepTeal,
    secondary = InkSoft,
    onSecondary = Color.White,
    secondaryContainer = MintGlow,
    onSecondaryContainer = DeepTeal,
    tertiary = Coral,
    onTertiary = Color.White,
    tertiaryContainer = CoralSoft,
    onTertiaryContainer = Coral,
    background = WarmGray,
    onBackground = Ink,
    surface = SurfaceElevatedLight,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1EFEA),
    onSurfaceVariant = InkSoft,
    outline = WarmGrayBorder,
    outlineVariant = WarmGrayMid,
    error = Coral,
    onError = Color.White,
    errorContainer = CoralSoft,
    onErrorContainer = Coral,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF48C9A3),
    onPrimary = Color(0xFF03382D),
    primaryContainer = DarkTealSurface,
    onPrimaryContainer = MistTeal,
    secondary = Color(0xFFA1A7A4),
    onSecondary = Color(0xFF1E2120),
    secondaryContainer = Color(0xFF262C2A),
    onSecondaryContainer = Color(0xFFE2DFDA),
    tertiary = Color(0xFFFF7A59),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF472218),
    onTertiaryContainer = Color(0xFFFDECE5),
    background = Color(0xFF131514),
    onBackground = Color(0xFFEBE8E3),
    surface = Color(0xFF1B1E1D),
    onSurface = Color(0xFFEBE8E3),
    surfaceVariant = Color(0xFF242726),
    onSurfaceVariant = Color(0xFFA1A7A4),
    outline = Color(0xFF383C3A),
    outlineVariant = Color(0xFF292D2B),
    error = Color(0xFFFF7A59),
    onError = Color.White,
    errorContainer = Color(0xFF472218),
    onErrorContainer = Color(0xFFFDECE5),
)

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}

