package com.brbrs.koto.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Standard glass card. Dark: subtle white glass. Light: pass tint = Color.Black for a soft gray glass. */
fun Modifier.glassCard(
    cornerRadius: Dp = 20.dp,
    bgAlpha: Float = 0.07f,
    borderAlpha: Float = 0.10f,
    tint: Color = Color.White,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(tint.copy(alpha = bgAlpha))
        .border(1.dp, tint.copy(alpha = borderAlpha), shape)
}

/** Accent-tinted glass, for highlighted surfaces (due-soon tiles, the active stack). */
fun Modifier.glassCardPrimary(
    cornerRadius: Dp = 20.dp,
    tint: Color = SteelPrimary,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(tint.copy(alpha = 0.12f))
        .border(1.5.dp, tint.copy(alpha = 0.30f), shape)
}

/** Light mode surface card: near-white with a soft steel-tinted shadow. */
fun Modifier.lightCard(cornerRadius: Dp = 20.dp, elevated: Boolean = false): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .shadow(
            elevation    = if (elevated) 4.dp else 2.dp,
            shape        = shape,
            ambientColor = Color(0x282E7A96),
            spotColor    = Color(0x142E7A96),
        )
        .clip(shape)
        .background(LightSurface)
        .border(1.dp, LightBorderSoft, shape)
}

/** Picks the right card style for the current mode. */
fun Modifier.kotoCard(isDark: Boolean, cornerRadius: Dp = 18.dp): Modifier = if (isDark) {
    val shape = RoundedCornerShape(cornerRadius)
    this.clip(shape)
        .background(Color.White.copy(alpha = 0.06f))
        .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
} else {
    this.lightCard(cornerRadius = cornerRadius)
}

/** Pill chip -- stack tabs, filters, theme options. */
fun Modifier.kotoChip(
    isDark: Boolean,
    selected: Boolean,
    selectedColor: Color = if (isDark) SteelPrimary else SteelLight,
): Modifier {
    val shape = RoundedCornerShape(50.dp)
    return if (selected) {
        this.clip(shape)
            .background(selectedColor.copy(alpha = if (isDark) 0.22f else 0.14f))
            .border(1.5.dp, selectedColor.copy(alpha = if (isDark) 0.45f else 0.35f), shape)
    } else {
        this.clip(shape)
            .background(if (isDark) Color.White.copy(alpha = 0.06f) else SteelLight.copy(alpha = 0.06f))
            .border(1.dp, if (isDark) Color.White.copy(alpha = 0.10f) else SteelLight.copy(alpha = 0.22f), shape)
    }
}
