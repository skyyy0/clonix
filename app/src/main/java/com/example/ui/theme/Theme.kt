package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

@Immutable
data class LiquidGlassTokens(
    val isDark: Boolean,
    val backdropTop: Color,
    val backdropBottom: Color,
    val ambientGlowPrimary: Color,
    val ambientGlowSecondary: Color,
    val glassFill: Color,
    val glassFillElevated: Color,
    val glassSpecularTop: Color,
    val glassSpecularBottom: Color,
    val glassNavBarFill: Color,
    val verifiedEmerald: Color,
    val warningAmber: Color,
    val accentTeal: Color
)

val LocalLiquidGlass = staticCompositionLocalOf {
    LiquidGlassTokens(
        isDark = true,
        backdropTop = DarkBackground,
        backdropBottom = DarkBackgroundElevated,
        ambientGlowPrimary = Color.White.copy(alpha = 0.055f),
        ambientGlowSecondary = DarkSecondary.copy(alpha = 0.085f),
        glassFill = Color(0xFF18181D).copy(alpha = 0.78f),
        glassFillElevated = Color(0xFF222229).copy(alpha = 0.88f),
        glassSpecularTop = Color.White.copy(alpha = 0.19f),
        glassSpecularBottom = Color.White.copy(alpha = 0.03f),
        glassNavBarFill = Color(0xFF131317).copy(alpha = 0.90f),
        verifiedEmerald = DarkTertiary,
        warningAmber = Color(0xFFF59E0B),
        accentTeal = DarkSecondary
    )
}

private val ClonixDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline
)

private val ClonixLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline
)

private val ClonixShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ClonixTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val baseScheme: ColorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ClonixDarkColorScheme
        else -> ClonixLightColorScheme
    }

    val animSpec = tween<Color>(durationMillis = 300)
    val animatedScheme = baseScheme.copy(
        background = animateColorAsState(baseScheme.background, animSpec, label = "bg").value,
        surface = animateColorAsState(baseScheme.surface, animSpec, label = "surface").value,
        surfaceVariant = animateColorAsState(baseScheme.surfaceVariant, animSpec, label = "sv").value,
        onBackground = animateColorAsState(baseScheme.onBackground, animSpec, label = "onBg").value,
        onSurface = animateColorAsState(baseScheme.onSurface, animSpec, label = "onSurf").value,
        primary = animateColorAsState(baseScheme.primary, animSpec, label = "primary").value
    )

    val glassTokens = if (darkTheme) {
        LiquidGlassTokens(
            isDark = true,
            backdropTop = DarkBackground,
            backdropBottom = DarkBackgroundElevated,
            ambientGlowPrimary = Color.White.copy(alpha = 0.05f),
            ambientGlowSecondary = DarkSecondary.copy(alpha = 0.08f),
            glassFill = Color(0xFF18181D).copy(alpha = 0.78f),
            glassFillElevated = Color(0xFF222229).copy(alpha = 0.88f),
            glassSpecularTop = Color.White.copy(alpha = 0.19f),
            glassSpecularBottom = Color.White.copy(alpha = 0.03f),
            glassNavBarFill = Color(0xFF141418).copy(alpha = 0.92f),
            verifiedEmerald = DarkTertiary,
            warningAmber = Color(0xFFF59E0B),
            accentTeal = DarkSecondary
        )
    } else {
        LiquidGlassTokens(
            isDark = false,
            backdropTop = LightBackground,
            backdropBottom = LightBackgroundElevated,
            ambientGlowPrimary = Color(0xFF0F766E).copy(alpha = 0.06f),
            ambientGlowSecondary = Color(0xFFD4A373).copy(alpha = 0.08f),
            glassFill = Color.White.copy(alpha = 0.80f),
            glassFillElevated = Color.White.copy(alpha = 0.94f),
            glassSpecularTop = Color.White.copy(alpha = 0.98f),
            glassSpecularBottom = Color(0xFFA1A1AA).copy(alpha = 0.24f),
            glassNavBarFill = Color(0xFFFCFCFA).copy(alpha = 0.92f),
            verifiedEmerald = LightTertiary,
            warningAmber = Color(0xFFD97706),
            accentTeal = LightSecondary
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalLiquidGlass provides glassTokens) {
        MaterialTheme(
            colorScheme = animatedScheme,
            typography = ClonixTypography,
            shapes = ClonixShapes,
            content = content
        )
    }
}
