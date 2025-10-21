package com.samsung.health.hrdatatransfer.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors

val Teal200 = Color(0xFF03DAC5)
val Red400 = Color(0xFFCF6679)
val PrimaryGreen = Color(0xFF96be25)
val PrimaryVariantRed = Color(0xFFbe4d25)
val LightGrey = Color(0xFFAAAAAA)

internal val wearColorPalette: Colors = Colors(
    primary = PrimaryGreen,
    primaryVariant = PrimaryVariantRed,
    secondary = Teal200,
    error = Red400,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onError = Color.Black
)
