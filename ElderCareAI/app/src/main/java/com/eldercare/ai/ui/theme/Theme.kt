package com.eldercare.ai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 适老化亮色主题
private val ElderLightColorScheme = lightColorScheme(
    primary = ElderPrimary,
    onPrimary = ElderOnPrimary,
    primaryContainer = ElderPrimaryVariant,
    onPrimaryContainer = ElderOnPrimary,
    secondary = ElderSecondary,
    onSecondary = ElderOnSecondary,
    secondaryContainer = ElderSecondaryVariant,
    onSecondaryContainer = ElderOnSecondary,
    tertiary = ElderInfo,
    onTertiary = ElderOnPrimary,
    error = ElderError,
    onError = ElderOnError,
    errorContainer = ElderError,
    onErrorContainer = ElderOnError,
    background = ElderBackground,
    onBackground = ElderOnBackground,
    surface = ElderSurface,
    onSurface = ElderOnSurface,
    surfaceVariant = ElderSurface,
    onSurfaceVariant = ElderOnSurface,
    outline = ElderDisabled,
    outlineVariant = ElderDisabled,
    scrim = ElderOnBackground,
    inverseSurface = ElderOnBackground,
    inverseOnSurface = ElderBackground,
    inversePrimary = ElderPrimary,
    surfaceDim = ElderSurface,
    surfaceBright = ElderBackground,
    surfaceContainerLowest = ElderBackground,
    surfaceContainerLow = ElderSurface,
    surfaceContainer = ElderSurface,
    surfaceContainerHigh = ElderSurface,
    surfaceContainerHighest = ElderSurface,
)

// 适老化暗色主题（可选）
private val ElderDarkColorScheme = darkColorScheme(
    primary = ElderDarkPrimary,
    onPrimary = ElderDarkBackground,
    primaryContainer = ElderDarkPrimary,
    onPrimaryContainer = ElderDarkBackground,
    secondary = ElderDarkSecondary,
    onSecondary = ElderDarkBackground,
    secondaryContainer = ElderDarkSecondary,
    onSecondaryContainer = ElderDarkBackground,
    tertiary = ElderInfo,
    onTertiary = ElderDarkBackground,
    error = ElderError,
    onError = ElderOnError,
    errorContainer = ElderError,
    onErrorContainer = ElderOnError,
    background = ElderDarkBackground,
    onBackground = ElderDarkOnBackground,
    surface = ElderDarkSurface,
    onSurface = ElderDarkOnSurface,
    surfaceVariant = ElderDarkSurface,
    onSurfaceVariant = ElderDarkOnSurface,
    outline = ElderDisabled,
    outlineVariant = ElderDisabled,
    scrim = ElderDarkOnBackground,
    inverseSurface = ElderDarkOnBackground,
    inverseOnSurface = ElderDarkBackground,
    inversePrimary = ElderDarkPrimary,
    surfaceDim = ElderDarkSurface,
    surfaceBright = ElderDarkBackground,
    surfaceContainerLowest = ElderDarkBackground,
    surfaceContainerLow = ElderDarkSurface,
    surfaceContainer = ElderDarkSurface,
    surfaceContainerHigh = ElderDarkSurface,
    surfaceContainerHighest = ElderDarkSurface,
)

@Composable
fun ElderCareAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 对于老年人，建议默认关闭动态颜色，使用固定的高对比度配色
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ElderDarkColorScheme
        else -> ElderLightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ElderTypography,
        content = content
    )
}