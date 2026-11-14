package com.brbrs.koto.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brbrs.koto.R

// Palette sampled from the Koto icon: a steel-blue bokeh over near-black navy, with the
// pale gray-blue highlight that lifts its top-left corner.

// ── Dark palette ──────────────────────────────────────────────────────────────
val NavyDeep     = Color(0xFF08121A)
val NavyMid      = Color(0xFF0E1C26)
val NavySurface  = Color(0xFF152633)
val NavySurface2 = Color(0xFF1D3242)
val GlassBorder  = Color(0x22FFFFFF)
val GlowSteel    = Color(0x2E2E7A96)   // ambient glow tint behind the header

// ── Steel accent (Koto) ───────────────────────────────────────────────────────
val SteelPrimary = Color(0xFF6FA8C0)  // pops on dark
val SteelDeep    = Color(0xFF2E7A96)  // sampled straight from the icon
val SteelLight   = Color(0xFF14607C)  // light-mode primary (AA on light surfaces)
val MistSecondary = Color(0xFFA9BCC7) // the pale corner highlight, for secondary accents

/** The active accent for the current mode -- dark: [SteelPrimary], light: [SteelLight]. */
val AccentPrimary = SteelPrimary

// ── Semantic ──────────────────────────────────────────────────────────────────
val SlateText = Color(0xFF93A6B4)
val White     = Color(0xFFF1F5F9)
val ErrorRed  = Color(0xFFEF5350)
val GreenOk   = Color(0xFF4CAF78)
val AmberWarn = Color(0xFFFFB300)

// Priority colors, mapped onto the RFC 5545 1-9 scale Nextcloud Tasks also uses.
val PriorityHigh   = Color(0xFFE2574C)  // 1-3
val PriorityMedium = Color(0xFFE8A33D)  // 4-6
val PriorityLow    = Color(0xFF7FA7B8)  // 7-9

// ── Light palette ─────────────────────────────────────────────────────────────
val LightBg         = Color(0xFFEEF3F6)
val LightSurface    = Color(0xFFFAFCFD)
val LightSurface2   = Color(0xFFE2EBF0)
val LightSurface3   = Color(0xFFD2E0E7)
val LightBorderSoft = Color(0x2214607C)
val LightBorderMed  = Color(0x3314607C)
val LightText       = Color(0xFF0C1A21)
val LightTextDim    = Color(0xFF475C66)
val LightGlow       = Color(0x2A2E7A96)

val LocalIsDark = compositionLocalOf { true }
val LocalIsOled = compositionLocalOf { false }

// ── Shapes ────────────────────────────────────────────────────────────────────
val CardShape = RoundedCornerShape(18.dp)
val FabShape  = RoundedCornerShape(20.dp)

// ── Color schemes ─────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary              = SteelPrimary,
    onPrimary            = NavyDeep,
    primaryContainer     = Color(0xFF123241),
    onPrimaryContainer   = Color(0xFFBFE2EF),
    secondary            = MistSecondary,
    onSecondary          = NavyDeep,
    secondaryContainer   = Color(0xFF26323A),
    onSecondaryContainer = Color(0xFFD7E3EA),
    tertiary             = AmberWarn,
    onTertiary           = NavyDeep,
    background           = NavyDeep,
    onBackground         = White,
    surface              = NavyMid,
    onSurface            = White,
    surfaceVariant       = NavySurface,
    onSurfaceVariant     = SlateText,
    surfaceContainer     = NavySurface2,
    surfaceContainerHigh = NavySurface2,
    outline              = GlassBorder,
    outlineVariant       = Color(0x12FFFFFF),
    error                = ErrorRed,
    scrim                = Color(0x99000000),
)

private val LightColorScheme = lightColorScheme(
    primary              = SteelLight,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFCCE7F0),
    onPrimaryContainer   = Color(0xFF002632),
    secondary            = Color(0xFF4F6673),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFDCE7ED),
    onSecondaryContainer = Color(0xFF17262E),
    tertiary             = Color(0xFFB26A00),
    onTertiary           = Color.White,
    background           = LightBg,
    onBackground         = LightText,
    surface              = LightSurface,
    onSurface            = LightText,
    surfaceVariant       = LightSurface2,
    onSurfaceVariant     = LightTextDim,
    surfaceContainer     = LightSurface3,
    surfaceContainerHigh = LightSurface,
    outline              = LightBorderMed,
    outlineVariant       = LightBorderSoft,
    error                = Color(0xFFB00020),
    scrim                = Color(0x66000000),
)

// True-black OLED variant -- same accents, pure-black surfaces.
private val OledColorScheme = DarkColorScheme.copy(
    background       = Color.Black,
    surface          = Color.Black,
    surfaceVariant   = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
)

// ── Font families ─────────────────────────────────────────────────────────────

val DMSerifDisplayFamily = FontFamily(
    Font(R.font.dm_serif_display_regular, FontWeight.Normal),
)

val InterTightFamily = FontFamily(
    Font(R.font.inter_tight_thin,        FontWeight.Thin),
    Font(R.font.inter_tight_extra_light, FontWeight.ExtraLight),
    Font(R.font.inter_tight_light,       FontWeight.Light),
    Font(R.font.inter_tight_regular,     FontWeight.Normal),
    Font(R.font.inter_tight_medium,      FontWeight.Medium),
    Font(R.font.inter_tight_semi_bold,   FontWeight.SemiBold),
    Font(R.font.inter_tight_bold,        FontWeight.Bold),
    Font(R.font.inter_tight_extra_bold,  FontWeight.ExtraBold),
    Font(R.font.inter_tight_black,       FontWeight.Black),
)

// ── Typography ────────────────────────────────────────────────────────────────

fun buildTypography(displayFont: FontFamily, bodyFont: FontFamily) = Typography(
    displayLarge   = TextStyle(fontFamily = displayFont, fontSize = 44.sp, fontWeight = FontWeight.Normal,   lineHeight = 52.sp, letterSpacing = (-0.5).sp),
    headlineLarge  = TextStyle(fontFamily = displayFont, fontSize = 34.sp, fontWeight = FontWeight.Normal,   lineHeight = 42.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = displayFont, fontSize = 26.sp, fontWeight = FontWeight.Normal,   lineHeight = 34.sp, letterSpacing = (-0.2).sp),
    headlineSmall  = TextStyle(fontFamily = displayFont, fontSize = 22.sp, fontWeight = FontWeight.Normal,   lineHeight = 30.sp),
    titleLarge     = TextStyle(fontFamily = bodyFont,    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium    = TextStyle(fontFamily = bodyFont,    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp),
    titleSmall     = TextStyle(fontFamily = bodyFont,    fontSize = 13.sp, fontWeight = FontWeight.Medium,   letterSpacing = (-0.1).sp),
    bodyLarge      = TextStyle(fontFamily = bodyFont,    fontSize = 15.sp, fontWeight = FontWeight.Normal,   lineHeight = 24.sp, letterSpacing = (-0.1).sp),
    bodyMedium     = TextStyle(fontFamily = bodyFont,    fontSize = 13.sp, fontWeight = FontWeight.Normal,   lineHeight = 20.sp, letterSpacing = (-0.05).sp),
    bodySmall      = TextStyle(fontFamily = bodyFont,    fontSize = 11.sp, fontWeight = FontWeight.Normal,   lineHeight = 16.sp),
    labelLarge     = TextStyle(fontFamily = bodyFont,    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.sp),
    labelMedium    = TextStyle(fontFamily = bodyFont,    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.sp),
    labelSmall     = TextStyle(fontFamily = bodyFont,    fontSize = 10.sp, fontWeight = FontWeight.Medium,   letterSpacing = 0.08.sp),
)

val AppTypography = buildTypography(FontFamily.Serif, FontFamily.SansSerif)
val AppTypographyPaired = buildTypography(DMSerifDisplayFamily, InterTightFamily)

fun scaledTypography(scale: Float, useCustomFont: Boolean): Typography {
    val base = if (useCustomFont) AppTypographyPaired else AppTypography
    fun TextStyle.scaled() = copy(
        fontSize   = fontSize * scale,
        lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight * scale else lineHeight,
    )
    return Typography(
        displayLarge   = base.displayLarge.scaled(),
        headlineLarge  = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall  = base.headlineSmall.scaled(),
        titleLarge     = base.titleLarge.scaled(),
        titleMedium    = base.titleMedium.scaled(),
        titleSmall     = base.titleSmall.scaled(),
        bodyLarge      = base.bodyLarge.scaled(),
        bodyMedium     = base.bodyMedium.scaled(),
        bodySmall      = base.bodySmall.scaled(),
        labelLarge     = base.labelLarge.scaled(),
        labelMedium    = base.labelMedium.scaled(),
        labelSmall     = base.labelSmall.scaled(),
    )
}

@Composable
fun KotoTheme(
    isDark: Boolean = true,
    isOled: Boolean = false,
    textScale: Float = 1.0f,
    useCustomFont: Boolean = true,
    content: @Composable () -> Unit,
) {
    val typography = when {
        textScale != 1.0f -> scaledTypography(textScale, useCustomFont)
        useCustomFont     -> AppTypographyPaired
        else              -> AppTypography
    }
    val colorScheme = when {
        isOled -> OledColorScheme
        isDark -> DarkColorScheme
        else   -> LightColorScheme
    }
    CompositionLocalProvider(LocalIsDark provides isDark, LocalIsOled provides isOled) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}
