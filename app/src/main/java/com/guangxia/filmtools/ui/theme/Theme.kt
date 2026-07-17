package com.guangxia.filmtools.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

val Carbon = Color(0xFF000000)
val Panel = Color(0xFF1C1C1E)
val PanelRaised = Color(0xFF2C2C2E)
val PanelSoft = Color(0xFF3A3A3C)
val Paper = Color(0xFFF2F2F7)
// Secondary copy must remain readable on both instrument surfaces in bright daylight.
val Muted = Color(0xFFA6A6AC)
val Danger = Color(0xFFFF453A)

val AppAccent = Color(0xFF88A9AE)
val MeterAccent = AppAccent
val FlashAccent = AppAccent
val FilmAccent = AppAccent

// Kept as a compatibility alias for a few platform-level controls.
val Amber = MeterAccent
val AmberSoft = Color(0xFFA8C1C4)

val InstrumentShape = RoundedCornerShape(16.dp)
val ControlShape = RoundedCornerShape(12.dp)
val RecessShape = RoundedCornerShape(8.dp)

val LocalToolAccent = staticCompositionLocalOf { MeterAccent }

@Composable
fun ToolAccent(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalToolAccent provides color) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(primary = color, onPrimary = Carbon),
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}

private val colors = darkColorScheme(
    primary = MeterAccent,
    onPrimary = Carbon,
    secondary = FlashAccent,
    background = Carbon,
    onBackground = Paper,
    surface = Panel,
    onSurface = Paper,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = Muted,
    error = Danger,
)

@Composable
fun GuangXiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 50.sp, lineHeight = 56.sp, letterSpacing = (-1).sp),
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.7).sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 24.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = ControlShape,
            large = InstrumentShape,
            extraLarge = RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
