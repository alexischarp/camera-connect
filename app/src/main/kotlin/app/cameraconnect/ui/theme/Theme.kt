package app.cameraconnect.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Charcoal = Color(0xFF17191C)
val Graphite = Color(0xFF2B2E33)
val Ivory = Color(0xFFF3F1EB)
val SignalOrange = Color(0xFFF36B45)

// Intentionally fixed: wallpaper colors must not replace the photographic identity.
private val PrecisionColors = darkColorScheme(
    primary = SignalOrange, onPrimary = Charcoal,
    primaryContainer = Color(0xFF613022), onPrimaryContainer = Ivory,
    secondary = Ivory, onSecondary = Charcoal,
    secondaryContainer = Graphite, onSecondaryContainer = Ivory,
    tertiary = Color(0xFFA8C6AE), onTertiary = Color(0xFF152B1C),
    tertiaryContainer = Color(0xFF293B2E), onTertiaryContainer = Ivory,
    background = Charcoal, onBackground = Ivory,
    surface = Charcoal, onSurface = Ivory,
    surfaceVariant = Graphite, onSurfaceVariant = Color(0xFFC3C1BC),
    surfaceTint = Color.Transparent,
    surfaceDim = Charcoal, surfaceBright = Color(0xFF373A40),
    surfaceContainerLowest = Color(0xFF111315),
    surfaceContainerLow = Color(0xFF202226), surfaceContainer = Graphite,
    surfaceContainerHigh = Color(0xFF34373D), surfaceContainerHighest = Color(0xFF3E4147),
    outline = Color(0xFF92938F), outlineVariant = Color(0xFF484B50),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF6D2926), onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Ivory, inverseOnSurface = Charcoal, inversePrimary = Color(0xFF9B351B),
)

// Android's native sans is bundled, supports French and respects font scaling.
private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight,
    fontSize = size.sp, lineHeight = height.sp,
)

private val PrecisionTypography = Typography(
    displayLarge = type(48, 56, FontWeight.Bold), displayMedium = type(40, 48, FontWeight.Bold),
    displaySmall = type(36, 44, FontWeight.Bold), headlineLarge = type(32, 40, FontWeight.Bold),
    headlineMedium = type(28, 36, FontWeight.Bold), headlineSmall = type(24, 32, FontWeight.Bold),
    titleLarge = type(22, 28, FontWeight.Bold), titleMedium = type(18, 26, FontWeight.SemiBold),
    titleSmall = type(16, 24, FontWeight.SemiBold),
    bodyLarge = type(16, 24), bodyMedium = type(14, 22), bodySmall = type(12, 18),
    labelLarge = type(14, 20, FontWeight.SemiBold), labelMedium = type(12, 16, FontWeight.Medium),
    labelSmall = type(11, 16, FontWeight.Medium),
)

@Composable
fun CameraConnectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PrecisionColors, typography = PrecisionTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
