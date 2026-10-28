package it.halimeo.app

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi

internal val LocalExpressiveFontPulse = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Google Sans Flex with animated width and roundness axes.
 * Google Sans Flex exposes wdth (25..151) and ROND (0..100).
 */
@OptIn(ExperimentalTextApi::class)
@Composable
internal fun rememberHaliMeoTypography(width: Float, roundness: Float): Typography {
    val family = remember(width, roundness) {
        val weights = (1..10).map { it * 100 }
        FontFamily(
            *weights.map { value ->
                val weight = FontWeight(value)
                Font(
                    resId = R.font.google_sans_flex,
                    weight = weight,
                    style = FontStyle.Normal,
                    variationSettings = FontVariation.Settings(
                        weight,
                        FontStyle.Normal,
                        FontVariation.width(width.coerceIn(25f, 151f)),
                        FontVariation.Setting("ROND", roundness.coerceIn(0f, 100f))
                    )
                )
            }.toTypedArray()
        )
    }
    return remember(family) { Typography(fontFamily = family) }
}
