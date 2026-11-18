package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PacelyDarkColorScheme = darkColorScheme(
    primary = ElectricOrange,
    onPrimary = TextPrimary,
    primaryContainer = ElectricOrangeContainer,
    onPrimaryContainer = ElectricOrangeBright,
    secondary = WalkCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = WalkCyanContainer,
    onSecondaryContainer = WalkCyan,
    tertiary = GpsEmerald,
    onTertiary = ObsidianBg,
    tertiaryContainer = GpsEmeraldContainer,
    onTertiaryContainer = GpsEmerald,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = SlateElevated,
    surfaceContainerHigh = SlateHighest,
    outline = HairlineBorder,
    outlineVariant = HairlineBorder,
    error = DangerRed,
    onError = TextPrimary,
    errorContainer = DangerRedContainer,
    onErrorContainer = DangerRed
)

val PacelyShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PacelyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PacelyDarkColorScheme,
        typography = PacelyTypography,
        shapes = PacelyShapes,
        content = content
    )
}
