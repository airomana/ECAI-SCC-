package com.example.eldercareai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 适老化字体配置
 * 为老年人设计的超大字号系统，确保老人看得清
 */
val ElderTypography = Typography(
    // 正文字体：放大到 24sp
    bodyLarge = TextStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.5.sp
    ),
    bodySmall = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    
    // 标题字体：放大到 32sp
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 32.sp
    ),
    
    // 按钮文字：超大字号
    labelLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )
)
