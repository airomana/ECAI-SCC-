package com.example.eldercareai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * 适老化主题配置
 * 使用高对比度颜色和超大字号
 */

private val ElderLightColorScheme = lightColorScheme(
    primary = ElderPrimary,
    onPrimary = ElderOnPrimary,
    secondary = ElderSecondary,
    onSecondary = ElderOnSecondary,
    background = ElderBackground,
    onBackground = ElderOnBackground,
    surface = ElderSurface,
    onSurface = ElderOnSurface,
    error = ElderError,
    onError = ElderOnError
)

private val ElderDarkColorScheme = darkColorScheme(
    primary = ElderPrimary,
    onPrimary = ElderOnPrimary,
    secondary = ElderSecondary,
    onSecondary = ElderOnSecondary
)

@Composable
fun ElderCareAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        ElderDarkColorScheme
    } else {
        ElderLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ElderTypography,
        content = content
    )
}
