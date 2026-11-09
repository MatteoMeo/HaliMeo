package it.halimeo.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaliMeoVisualTokensTest {
    @Test
    fun darkGlassSurfacesUseAccentWithoutReplacingMidnightBase() {
        val accent = Color(0xFF9B78FF)
        val tokens = HaliMeoVisualTokens.create(accent, dark = true, reduceEffects = false)

        assertNotEquals(Color(0xFF090C12), tokens.surface)
        assertTrue(tokens.surface.red < 0.20f)
        assertTrue(tokens.surface.blue > tokens.surface.red)
        assertTrue(tokens.outline.alpha > 0f)
    }

    @Test
    fun strongSurfaceHasMoreAccentPresenceThanStandardSurface() {
        val tokens = HaliMeoVisualTokens.create(Color(0xFF9B78FF), dark = true, reduceEffects = false)

        assertTrue(tokens.strongSurface.blue > tokens.surface.blue)
        assertTrue(tokens.strongSurface.red > tokens.surface.red)
    }

    @Test
    fun reducedEffectsKeepGlassVisibleButMoreSubtle() {
        val normal = HaliMeoVisualTokens.create(Color(0xFF9B78FF), dark = true, reduceEffects = false)
        val reduced = HaliMeoVisualTokens.create(Color(0xFF9B78FF), dark = true, reduceEffects = true)

        assertTrue(reduced.surface.blue < normal.surface.blue)
        assertTrue(reduced.strongSurface.blue < normal.strongSurface.blue)
        assertTrue(reduced.outline.alpha < normal.outline.alpha)
    }

    @Test
    fun darkGlassSurfaceStaysNeutralAcrossDifferentThemeAccents() {
        val violet = HaliMeoVisualTokens.create(Color(0xFF9B78FF), dark = true, reduceEffects = false)
        val cyan = HaliMeoVisualTokens.create(Color(0xFF35C8E8), dark = true, reduceEffects = false)

        assertTrue(kotlin.math.abs(violet.strongSurface.red - cyan.strongSurface.red) < 0.01f)
        assertTrue(kotlin.math.abs(violet.strongSurface.green - cyan.strongSurface.green) < 0.01f)
        assertTrue(kotlin.math.abs(violet.strongSurface.blue - cyan.strongSurface.blue) < 0.01f)
        assertTrue(violet.strongSurface.red < 0.20f)
        assertTrue(violet.strongSurface.green < 0.22f)
    }

    @Test
    fun lightPaletteRetainsReadableNeutralSurfaces() {
        val tokens = HaliMeoVisualTokens.create(Color(0xFF527DFF), dark = false, reduceEffects = false)

        assertTrue(tokens.surface.red > 0.85f)
        assertTrue(tokens.surface.green > 0.85f)
        assertTrue(tokens.onSurface.red < 0.20f)
    }
}
