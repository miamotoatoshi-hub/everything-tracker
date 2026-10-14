package com.example.trackly.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.trackly.domain.model.AccentTheme

/**
 * Central colour definitions. To re-brand the app, edit the neutrals below or the accent table;
 * everything else in the UI reads colours from MaterialTheme / [TracklyTheme.extras].
 */
internal object Neutrals {
    // Light
    val LightBackground = Color(0xFFF6F7FB)
    val LightOnBackground = Color(0xFF12141C)
    val LightSurfaceVariant = Color(0xFFE9ECF4)
    val LightOnSurfaceVariant = Color(0xFF4A4F5C)
    val LightOutline = Color(0xFF7A8090)
    val LightOutlineVariant = Color(0xFFD5D9E4)
    val LightContainerLowest = Color(0xFFFFFFFF)
    val LightContainerLow = Color(0xFFFBFBFE)
    val LightContainer = Color(0xFFF0F2F8)
    val LightContainerHigh = Color(0xFFEBEEF5)
    val LightContainerHighest = Color(0xFFE5E8F1)
    val LightError = Color(0xFFB3261E)
    val LightErrorContainer = Color(0xFFF9DEDC)
    val LightOnErrorContainer = Color(0xFF410E0B)
    val LightInverseSurface = Color(0xFF2E3039)
    val LightInverseOnSurface = Color(0xFFF1F0F7)

    // Dark
    val DarkBackground = Color(0xFF0E1016)
    val DarkOnBackground = Color(0xFFE6E8F0)
    val DarkSurfaceVariant = Color(0xFF242836)
    val DarkOnSurfaceVariant = Color(0xFFB6BBCA)
    val DarkOutline = Color(0xFF8A90A0)
    val DarkOutlineVariant = Color(0xFF363B4A)
    val DarkContainerLowest = Color(0xFF0A0B10)
    val DarkContainerLow = Color(0xFF14171F)
    val DarkContainer = Color(0xFF181B25)
    val DarkContainerHigh = Color(0xFF1E222E)
    val DarkContainerHighest = Color(0xFF252A37)
    val DarkError = Color(0xFFF2B8B5)
    val DarkErrorContainer = Color(0xFF8C1D18)
    val DarkOnErrorContainer = Color(0xFFF9DEDC)
    val DarkInverseSurface = Color(0xFFE6E8F0)
    val DarkInverseOnSurface = Color(0xFF2E3039)
}

/** Colours of one accent theme in light and dark mode. */
internal data class AccentColors(
    val primary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    /** Swatch shown in the theme picker. */
    val swatch: Color = primary,
)

internal fun AccentTheme.colors(): AccentColors = when (this) {
    AccentTheme.INDIGO -> AccentColors(
        primary = Color(0xFF4F46E5),
        primaryContainer = Color(0xFFE0E0FF),
        onPrimaryContainer = Color(0xFF14126B),
        darkPrimary = Color(0xFFBDBBFF),
        darkOnPrimary = Color(0xFF1F1D80),
        darkPrimaryContainer = Color(0xFF35339E),
        darkOnPrimaryContainer = Color(0xFFE0E0FF),
    )
    AccentTheme.TEAL -> AccentColors(
        primary = Color(0xFF0F766E),
        primaryContainer = Color(0xFFCCFBF1),
        onPrimaryContainer = Color(0xFF042F2E),
        darkPrimary = Color(0xFF5EEAD4),
        darkOnPrimary = Color(0xFF003732),
        darkPrimaryContainer = Color(0xFF115E59),
        darkOnPrimaryContainer = Color(0xFFCCFBF1),
    )
    AccentTheme.SUNSET -> AccentColors(
        primary = Color(0xFFC2410C),
        primaryContainer = Color(0xFFFFEDD5),
        onPrimaryContainer = Color(0xFF431407),
        darkPrimary = Color(0xFFFDBA74),
        darkOnPrimary = Color(0xFF4A1D00),
        darkPrimaryContainer = Color(0xFF9A3412),
        darkOnPrimaryContainer = Color(0xFFFFEDD5),
    )
    AccentTheme.ROSE -> AccentColors(
        primary = Color(0xFFBE185D),
        primaryContainer = Color(0xFFFCE7F3),
        onPrimaryContainer = Color(0xFF500724),
        darkPrimary = Color(0xFFF9A8D4),
        darkOnPrimary = Color(0xFF500724),
        darkPrimaryContainer = Color(0xFF9D174D),
        darkOnPrimaryContainer = Color(0xFFFCE7F3),
    )
    AccentTheme.FOREST -> AccentColors(
        primary = Color(0xFF15803D),
        primaryContainer = Color(0xFFDCFCE7),
        onPrimaryContainer = Color(0xFF052E16),
        darkPrimary = Color(0xFF86EFAC),
        darkOnPrimary = Color(0xFF052E16),
        darkPrimaryContainer = Color(0xFF166534),
        darkOnPrimaryContainer = Color(0xFFDCFCE7),
    )
    AccentTheme.GRAPE -> AccentColors(
        primary = Color(0xFF7E22CE),
        primaryContainer = Color(0xFFF3E8FF),
        onPrimaryContainer = Color(0xFF3B0764),
        darkPrimary = Color(0xFFD8B4FE),
        darkOnPrimary = Color(0xFF3B0764),
        darkPrimaryContainer = Color(0xFF6B21A8),
        darkOnPrimaryContainer = Color(0xFFF3E8FF),
    )
    AccentTheme.OCEAN -> AccentColors(
        primary = Color(0xFF0369A1),
        primaryContainer = Color(0xFFE0F2FE),
        onPrimaryContainer = Color(0xFF082F49),
        darkPrimary = Color(0xFF7DD3FC),
        darkOnPrimary = Color(0xFF082F49),
        darkPrimaryContainer = Color(0xFF075985),
        darkOnPrimaryContainer = Color(0xFFE0F2FE),
    )
    AccentTheme.GRAPHITE -> AccentColors(
        primary = Color(0xFF334155),
        primaryContainer = Color(0xFFE2E8F0),
        onPrimaryContainer = Color(0xFF0F172A),
        darkPrimary = Color(0xFFCBD5E1),
        darkOnPrimary = Color(0xFF0F172A),
        darkPrimaryContainer = Color(0xFF334155),
        darkOnPrimaryContainer = Color(0xFFE2E8F0),
    )
}

/** Swatch colour used by the theme picker. */
fun AccentTheme.swatchColor(): Color = colors().swatch
