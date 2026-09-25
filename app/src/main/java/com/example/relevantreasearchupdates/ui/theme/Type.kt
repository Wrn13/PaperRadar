package com.example.relevantreasearchupdates.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val DefaultTypography = Typography()

val Typography = DefaultTypography.copy(
    headlineSmall = DefaultTypography.headlineSmall.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold
    ),
    titleLarge = DefaultTypography.titleLarge.copy(
        fontWeight = FontWeight.Bold
    ),
    titleMedium = DefaultTypography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold
    ),
    labelLarge = DefaultTypography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold
    )
)
