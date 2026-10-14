package com.example.trackly.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.trackly.domain.model.AccentTheme

/** Semantic colours that Material 3 has no slot for. */
@Immutable
data class ExtraColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val streak: Color,
    val streakContainer: Color,
    val warning: Color,
    val premium: Color,
    val premiumContainer: Color,
    val isDark: Boolean,
)

private val LightExtras = ExtraColors(
    success = Color(0xFF15803D),
    onSuccess = Color.White,
    successContainer = Color(0xFFDCFCE7),
    streak = Color(0xFFC2410C),
    streakContainer = Color(0xFFFFEDD5),
    warning = Color(0xFFB45309),
    premium = Color(0xFFB45309),
    premiumContainer = Color(0xFFFEF3C7),
    isDark = false,
)

private val DarkExtras = ExtraColors(
    success = Color(0xFF86EFAC),
    onSuccess = Color(0xFF052E16),
    successContainer = Color(0xFF14532D),
    streak = Color(0xFFFDBA74),
    streakContainer = Color(0xFF7C2D12),
    warning = Color(0xFFFCD34D),
    premium = Color(0xFFFCD34D),
    premiumContainer = Color(0xFF713F12),
    isDark = true,
)

val LocalExtraColors = staticCompositionLocalOf { LightExtras }

val TracklyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Horizontal gutter of every screen. */
    val screen = 16.dp

    /** Minimum touch target (Material / WCAG guidance). */
    val touchTarget = 48.dp
}

private fun lightScheme(accent: AccentTheme): ColorScheme {
    val a = accent.colors()
    return lightColorScheme(
        primary = a.primary,
        onPrimary = Color.White,
        primaryContainer = a.primaryContainer,
        onPrimaryContainer = a.onPrimaryContainer,
        secondary = a.primary,
        onSecondary = Color.White,
        secondaryContainer = a.primaryContainer,
        onSecondaryContainer = a.onPrimaryContainer,
        tertiary = a.primary,
        onTertiary = Color.White,
        tertiaryContainer = a.primaryContainer,
        onTertiaryContainer = a.onPrimaryContainer,
        background = Neutrals.LightBackground,
        onBackground = Neutrals.LightOnBackground,
        surface = Neutrals.LightBackground,
        onSurface = Neutrals.LightOnBackground,
        surfaceVariant = Neutrals.LightSurfaceVariant,
        onSurfaceVariant = Neutrals.LightOnSurfaceVariant,
        surfaceTint = a.primary,
        outline = Neutrals.LightOutline,
        outlineVariant = Neutrals.LightOutlineVariant,
        error = Neutrals.LightError,
        onError = Color.White,
        errorContainer = Neutrals.LightErrorContainer,
        onErrorContainer = Neutrals.LightOnErrorContainer,
        inverseSurface = Neutrals.LightInverseSurface,
        inverseOnSurface = Neutrals.LightInverseOnSurface,
        inversePrimary = a.darkPrimary,
        surfaceContainerLowest = Neutrals.LightContainerLowest,
        surfaceContainerLow = Neutrals.LightContainerLow,
        surfaceContainer = Neutrals.LightContainer,
        surfaceContainerHigh = Neutrals.LightContainerHigh,
        surfaceContainerHighest = Neutrals.LightContainerHighest,
    )
}

private fun darkScheme(accent: AccentTheme): ColorScheme {
    val a = accent.colors()
    return darkColorScheme(
        primary = a.darkPrimary,
        onPrimary = a.darkOnPrimary,
        primaryContainer = a.darkPrimaryContainer,
        onPrimaryContainer = a.darkOnPrimaryContainer,
        secondary = a.darkPrimary,
        onSecondary = a.darkOnPrimary,
        secondaryContainer = a.darkPrimaryContainer,
        onSecondaryContainer = a.darkOnPrimaryContainer,
        tertiary = a.darkPrimary,
        onTertiary = a.darkOnPrimary,
        tertiaryContainer = a.darkPrimaryContainer,
        onTertiaryContainer = a.darkOnPrimaryContainer,
        background = Neutrals.DarkBackground,
        onBackground = Neutrals.DarkOnBackground,
        surface = Neutrals.DarkBackground,
        onSurface = Neutrals.DarkOnBackground,
        surfaceVariant = Neutrals.DarkSurfaceVariant,
        onSurfaceVariant = Neutrals.DarkOnSurfaceVariant,
        surfaceTint = a.darkPrimary,
        outline = Neutrals.DarkOutline,
        outlineVariant = Neutrals.DarkOutlineVariant,
        error = Neutrals.DarkError,
        onError = Color(0xFF601410),
        errorContainer = Neutrals.DarkErrorContainer,
        onErrorContainer = Neutrals.DarkOnErrorContainer,
        inverseSurface = Neutrals.DarkInverseSurface,
        inverseOnSurface = Neutrals.DarkInverseOnSurface,
        inversePrimary = a.primary,
        surfaceContainerLowest = Neutrals.DarkContainerLowest,
        surfaceContainerLow = Neutrals.DarkContainerLow,
        surfaceContainer = Neutrals.DarkContainer,
        surfaceContainerHigh = Neutrals.DarkContainerHigh,
        surfaceContainerHighest = Neutrals.DarkContainerHighest,
    )
}

@Composable
fun TracklyTheme(
    darkTheme: Boolean,
    accent: AccentTheme = AccentTheme.INDIGO,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) darkScheme(accent) else lightScheme(accent)
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TracklyTypography,
            shapes = TracklyShapes,
            content = content,
        )
    }
}

object TracklyTheme {
    val extras: ExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtraColors.current
}

// region Habit colour helpers

/** A habit's colour made readable on the current background (lightened in dark mode). */
fun Color.habitAccent(isDark: Boolean): Color = if (isDark) lerp(this, Color.White, 0.22f) else this

/** Soft card background tinted with the habit's colour; [emphasis] (0..1) strengthens the tint. */
fun Color.habitContainer(base: Color, isDark: Boolean, emphasis: Float = 0f): Color =
    lerp(base, this, (if (isDark) 0.20f else 0.13f) + emphasis.coerceIn(0f, 1f) * 0.10f)

// endregion
