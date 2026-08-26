package com.expensetracker.ui.theme

import androidx.compose.ui.graphics.Color

// Pinch brand palette — calm teal primary, warm coral reserved for debits/alerts,
// warm gray surfaces with elevated card layers.

// Primary teal family
val MistTeal = Color(0xFFDFF3EE)         // light background tint
val MintGlow = Color(0xFFE8F8F4)         // ultra soft pill / chip tint
val PinchTeal = Color(0xFF1B8A6B)        // primary / brand teal (accessible, vibrant)
val DeepTeal = Color(0xFF0A5444)         // text on teal tint / dark primary
val DarkTealSurface = Color(0xFF132B25)  // rich dark container

// Gradients
val GradientTealStart = Color(0xFF1B8A6B)
val GradientTealEnd = Color(0xFF0F5F4B)
val ElectricMint = Color(0xFF3DE0A8)     // vivid highlight stop for hero gradients / glow accents

// Accent — reserved for debits, errors, alerts (carries meaning, not decorative)
val Coral = Color(0xFFDE5228)
val CoralSoft = Color(0xFFFDECE5)        // coral tint for backgrounds
val Amber = Color(0xFFE68A00)
val AmberSoft = Color(0xFFFEF3E0)

// Neutrals — modern warm studio tones
val WarmGray = Color(0xFFF7F6F2)         // overall background
val SurfaceElevatedLight = Color(0xFFFFFFFF) // elevated card background
val SurfaceContainerLight = Color(0xFFFFFFFF)
val SurfaceContainerDark = Color(0xFF222423)
val WarmGrayMid = Color(0xFFEAE7E0)      // dividers, subtle borders
val WarmGrayBorder = Color(0xFFDFDBD1)

// Typography colors
val Ink = Color(0xFF1F2221)              // headings / primary text
val InkSoft = Color(0xFF656B68)          // secondary text / captions
val InkMuted = Color(0xFF919794)         // tertiary / meta text

// Compatibility aliases
val TealPrimary = PinchTeal
val TealPrimaryDark = PinchTeal
val SurfaceLight = WarmGray
val SurfaceDark = Color(0xFF181A19)
val TextPrimaryLight = Ink
val TextPrimaryDark = Color(0xFFF0EFEC)
val TextSecondary = InkSoft
val Emerald = PinchTeal
val Slate = Ink
