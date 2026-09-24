package org.miguelcaldas.nessodroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.miguelcaldas.nessodroid.R

private val NessoColors = lightColorScheme(
    primary = Color(0xFF006C55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF3E9),
    onPrimaryContainer = Color(0xFF003829),
    secondary = Color(0xFF365E9D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EEFA),
    onSecondaryContainer = Color(0xFF193450),
    tertiary = Color(0xFF956200),
    tertiaryContainer = Color(0xFFFFEAC3),
    background = Color(0xFFF6F7F8),
    onBackground = Color(0xFF18252B),
    surface = Color.White,
    onSurface = Color(0xFF18252B),
    surfaceVariant = Color(0xFFE9EEEF),
    onSurfaceVariant = Color(0xFF53646B),
    surfaceContainerLow = Color(0xFFECF1F2),
    surfaceContainerHigh = Color(0xFFE9EEEF),
    outline = Color(0xFF72848C),
    outlineVariant = Color(0xFFD4DEE1),
    error = Color(0xFFB52C39),
    errorContainer = Color(0xFFFFE9EA),
)

@OptIn(ExperimentalTextApi::class)
private val Manrope = FontFamily(
    Font(R.font.manrope, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

private val NessoTypography = Typography(
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
)

@Composable
fun NessoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NessoColors, typography = NessoTypography, shapes = Shapes(small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(8.dp)), content = content)
}