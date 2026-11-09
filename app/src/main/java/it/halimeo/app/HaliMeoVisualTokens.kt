package it.halimeo.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Shared color roles for HaliMeo's Intelligent Glass surfaces.
 *
 * Keep the midnight base neutral: the selected accent is a light source and a
 * state color, not a tint that floods every surface.
 */
internal data class HaliMeoVisualTokens(
    val background: Color,
    val surface: Color,
    val strongSurface: Color,
    val elevatedSurface: Color,
    val outline: Color,
    val ambientAccent: Color,
    val ambientSecondary: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color
) {
    companion object {
        fun create(
            accent: Color,
            dark: Boolean,
            reduceEffects: Boolean
        ): HaliMeoVisualTokens {
            if (!dark) {
                val base = Color(0xFFF4F3F9)
                val surfaceAlpha = if (reduceEffects) 0.045f else 0.075f
                return HaliMeoVisualTokens(
                    background = base,
                    surface = accent.copy(alpha = surfaceAlpha).compositeOver(Color(0xF7FFFFFF)),
                    strongSurface = accent.copy(alpha = if (reduceEffects) 0.07f else 0.11f).compositeOver(Color(0xFCFFFFFF)),
                    elevatedSurface = Color(0xFFFFFFFF),
                    outline = accent.copy(alpha = if (reduceEffects) 0.12f else 0.18f),
                    ambientAccent = accent.copy(alpha = if (reduceEffects) 0.10f else 0.16f),
                    ambientSecondary = accent.copy(alpha = if (reduceEffects) 0.045f else 0.075f),
                    onSurface = Color(0xFF252335),
                    onSurfaceVariant = Color(0xFF696579)
                )
            }

            val base = Color(0xFF090C12)
            val surfaceAlpha = if (reduceEffects) 0.10f else 0.16f
            return HaliMeoVisualTokens(
                background = base,
                surface = accent.copy(alpha = surfaceAlpha).compositeOver(Color(0xB9101420)),
                strongSurface = accent.copy(alpha = if (reduceEffects) 0.13f else 0.22f).compositeOver(Color(0xD10D1120)),
                elevatedSurface = Color(0xF21A1D2C),
                outline = accent.copy(alpha = if (reduceEffects) 0.16f else 0.28f),
                ambientAccent = accent.copy(alpha = if (reduceEffects) 0.20f else 0.42f),
                ambientSecondary = accent.copy(alpha = if (reduceEffects) 0.08f else 0.18f),
                onSurface = Color(0xFFF4F3FA),
                onSurfaceVariant = Color(0xFFC5C2D2)
            )
        }
    }
}
