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
    val defaults = Material3TypographyDefaults.typography
    return remember(family, defaults) {
        Typography(
            displayLarge = defaults.displayLarge.copy(fontFamily = family),
            displayMedium = defaults.displayMedium.copy(fontFamily = family),
            displaySmall = defaults.displaySmall.copy(fontFamily = family),
            headlineLarge = defaults.headlineLarge.copy(fontFamily = family),
            headlineMedium = defaults.headlineMedium.copy(fontFamily = family),
            headlineSmall = defaults.headlineSmall.copy(fontFamily = family),
            titleLarge = defaults.titleLarge.copy(fontFamily = family),
            titleMedium = defaults.titleMedium.copy(fontFamily = family),
            titleSmall = defaults.titleSmall.copy(fontFamily = family),
            bodyLarge = defaults.bodyLarge.copy(fontFamily = family),
            bodyMedium = defaults.bodyMedium.copy(fontFamily = family),
            bodySmall = defaults.bodySmall.copy(fontFamily = family),
            labelLarge = defaults.labelLarge.copy(fontFamily = family),
            labelMedium = defaults.labelMedium.copy(fontFamily = family),
            labelSmall = defaults.labelSmall.copy(fontFamily = family),
            displayLargeEmphasized = defaults.displayLargeEmphasized.copy(fontFamily = family),
            displayMediumEmphasized = defaults.displayMediumEmphasized.copy(fontFamily = family),
            displaySmallEmphasized = defaults.displaySmallEmphasized.copy(fontFamily = family),
            headlineLargeEmphasized = defaults.headlineLargeEmphasized.copy(fontFamily = family),
            headlineMediumEmphasized = defaults.headlineMediumEmphasized.copy(fontFamily = family),
            headlineSmallEmphasized = defaults.headlineSmallEmphasized.copy(fontFamily = family),
            titleLargeEmphasized = defaults.titleLargeEmphasized.copy(fontFamily = family),
            titleMediumEmphasized = defaults.titleMediumEmphasized.copy(fontFamily = family),
            titleSmallEmphasized = defaults.titleSmallEmphasized.copy(fontFamily = family),
            bodyLargeEmphasized = defaults.bodyLargeEmphasized.copy(fontFamily = family),
            bodyMediumEmphasized = defaults.bodyMediumEmphasized.copy(fontFamily = family),
            bodySmallEmphasized = defaults.bodySmallEmphasized.copy(fontFamily = family),
            labelLargeEmphasized = defaults.labelLargeEmphasized.copy(fontFamily = family),
            labelMediumEmphasized = defaults.labelMediumEmphasized.copy(fontFamily = family),
            labelSmallEmphasized = defaults.labelSmallEmphasized.copy(fontFamily = family),
            tokens = defaults.tokens
        )
    }
}
