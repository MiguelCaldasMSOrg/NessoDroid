package org.miguelcaldas.nessodroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NessoColors = lightColorScheme(
    primary = Color(0xFF006B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA2F2D7),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF735C00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE16B),
    onSecondaryContainer = Color(0xFF231B00),
    background = Color(0xFFF4F6F5),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFFBFDFC),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
)

@Composable
fun NessoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NessoColors, typography = Typography(), content = content)
}