package com.expensetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.expensetracker.R

// Pinch typography — three faces, each doing one job:
//  - Bricolage Grotesque (variable): expressive display face for headlines
//    and hero numbers. Used with restraint, at heavy weights only.
//  - Manrope (variable): the everyday body/UI face — warm, rounded, legible
//    at small sizes.
//  - Space Mono: tabular figures for money amounts specifically (see
//    AmountFont below) — precision over warmth wherever a number is a fact.

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, opticalSize: Int) = Font(
    resId = R.font.bricolage_grotesque,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(100f),
        FontVariation.Setting("opsz", opticalSize.toFloat()),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    resId = R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val DisplayFont = FontFamily(
    bricolage(800, opticalSize = 96),
    bricolage(700, opticalSize = 48),
    bricolage(600, opticalSize = 24),
)

val BodyFont = FontFamily(
    manrope(700),
    manrope(600),
    manrope(500),
    manrope(400),
)

/** Tabular-figure face for money amounts — apply via `.copy(fontFamily = MonoFont)`. */
val MonoFont = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight(800),
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight(800),
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1.0).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight(800),
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight(700),
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight(700),
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight(600),
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight(700),
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight(600),
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight(500),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight(400),
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight(400),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight(400),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight(600),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight(500),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight(500),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    ),
)

