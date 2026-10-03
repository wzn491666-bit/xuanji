package com.xuanji.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

val Gray950 = Color(0xFF131416)
val Gray900 = Color(0xFF1C1D20)
val Gray800 = Color(0xFF292A2E)
val Gray700 = Color(0xFF45464C)
val Gray400 = Color(0xFF8B8D95)
val Gray300 = Color(0xFFB5B7C0)
val Gray100 = Color(0xFFE9EAEE)
val Gray50 = Color(0xFFF7F8FA)
val LightBg = Color.White
val LightSurface = Color.White
val LightBorder = Color(0xFFE3E4E8)
val LightTextPrimary = Color(0xFF202126)
val LightTextSecondary = Color(0xFF666973)
val AccentLight = Color(0xFF13765E)
val AccentDark = Color(0xFF84DBBC)
val AccentContainerDark = Color(0xFF183E32)
val AccentContainerLight = Color(0xFFE1F3EA)

private val darkSurface: Boolean @Composable get() = MaterialTheme.colorScheme.surface.luminance() < 0.5f
val Success: Color @Composable get() = if (darkSurface) Color(0xFF84DBAC) else Color(0xFF237348)
val Warning: Color @Composable get() = if (darkSurface) Color(0xFFE9BE75) else Color(0xFF886016)
val Danger: Color @Composable get() = MaterialTheme.colorScheme.error
val Info: Color @Composable get() = MaterialTheme.colorScheme.secondary

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF79B8FF), onPrimary = Color(0xFF062C59),
    primaryContainer = Color(0xFF193754), onPrimaryContainer = Color(0xFFD5E9FF),
    secondary = Color(0xFF72DFC7), onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF123E36), onSecondaryContainer = Color(0xFFA0F3DE),
    tertiary = Color(0xFFFFAA9B), onTertiary = Color(0xFF571F16),
    tertiaryContainer = Color(0xFF49251F), onTertiaryContainer = Color(0xFFFFD9D0),
    background = Gray950, onBackground = Gray100,
    surface = Gray950, onSurface = Gray100,
    surfaceVariant = Gray800, onSurfaceVariant = Gray300,
    surfaceDim = Color(0xFF101113), surfaceBright = Color(0xFF35363B),
    surfaceContainerLowest = Color(0xFF0E0F11), surfaceContainerLow = Color(0xFF191A1D),
    surfaceContainer = Gray900, surfaceContainerHigh = Color(0xFF232429), surfaceContainerHighest = Gray800,
    surfaceTint = Color.Transparent,
    inverseSurface = Gray100, inverseOnSurface = Gray900, inversePrimary = AccentLight,
    outline = Color(0xFF777A84), outlineVariant = Color(0xFF34363C),
    error = Color(0xFFFFA6A4), onError = Color(0xFF5B1419),
    errorContainer = Color(0xFF482024), onErrorContainer = Color(0xFFFFDADB),
    scrim = Color.Black,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF176BD9), onPrimary = Color.White,
    primaryContainer = Color(0xFFE9F2FF), onPrimaryContainer = Color(0xFF15477F),
    secondary = Color(0xFF007D69), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF7EF), onSecondaryContainer = Color(0xFF005445),
    tertiary = Color(0xFFC44D36), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFECE5), onTertiaryContainer = Color(0xFF8B3322),
    background = LightBg, onBackground = LightTextPrimary,
    surface = LightSurface, onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFF0F1F4), onSurfaceVariant = LightTextSecondary,
    surfaceDim = Color(0xFFE2E3E8), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F8F9),
    surfaceContainer = Color(0xFFF3F4F6), surfaceContainerHigh = Color(0xFFEEEFF2), surfaceContainerHighest = Color(0xFFE7E8EC),
    surfaceTint = Color.Transparent,
    inverseSurface = Gray800, inverseOnSurface = Gray50, inversePrimary = AccentDark,
    outline = Color(0xFF858894), outlineVariant = LightBorder,
    error = Color(0xFFAD3039), onError = Color.White,
    errorContainer = Color(0xFFFFECEC), onErrorContainer = Color(0xFF81212A),
    scrim = Color.Black,
)

@Composable
fun XuanjiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = XuanjiTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
