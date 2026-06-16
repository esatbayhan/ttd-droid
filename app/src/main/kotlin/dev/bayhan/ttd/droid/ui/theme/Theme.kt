package dev.bayhan.ttd.droid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006A4E),
    secondary = Color(0xFF4E6A5E),
    tertiary = Color(0xFF3C6472),
    surface = Color(0xFFF8FBF6),
    background = Color(0xFFF8FBF6),
    onSurface = Color(0xFF191C1A),
    onBackground = Color(0xFF191C1A),
    onPrimary = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF414941),
    outlineVariant = Color(0xFFC1C9C1)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7AD6B0),
    secondary = Color(0xFFB6CFC2),
    tertiary = Color(0xFFA6CEDD),
    surface = Color(0xFF191C1A),
    background = Color(0xFF191C1A),
    onSurface = Color(0xFFE1E3DF),
    onBackground = Color(0xFFE1E3DF),
    onPrimary = Color(0xFF003825),
    onSurfaceVariant = Color(0xFFC1C9C1),
    outlineVariant = Color(0xFF414941)
)

@Composable
fun TtdDroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp)
    )
    MaterialTheme(colorScheme = colorScheme, shapes = shapes, content = content)
}
