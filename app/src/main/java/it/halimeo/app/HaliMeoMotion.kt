package it.halimeo.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize

/**
 * Shared motion tokens for HaliMeo.
 *
 * The timings follow a short, spatial-first motion language: controls respond immediately,
 * while expanding content gets enough time to remain readable. The interaction patterns are
 * adapted independently from the ideas used in Morphe Manager; no Morphe source is copied.
 */
internal object HaliMeoMotion {
    private val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    val contentSizeSpring: SpringSpec<IntSize> =
        spring(dampingRatio = 0.88f, stiffness = 520f)

    fun sectionEnter(reduceMotion: Boolean): EnterTransition =
        if (reduceMotion) {
            EnterTransition.None
        } else {
            expandVertically(
                animationSpec = tween(durationMillis = 210, easing = easing),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(durationMillis = 145, easing = easing))
        }

    fun sectionExit(reduceMotion: Boolean): ExitTransition =
        if (reduceMotion) {
            ExitTransition.None
        } else {
            shrinkVertically(
                animationSpec = tween(durationMillis = 170, easing = easing),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(durationMillis = 115, easing = easing))
        }
}

/**
 * A small, shared press response. The animated scale is read in the graphics layer, so
 * intermediate frames do not recompose the card or button that is being pressed.
 */
@Composable
internal fun Modifier.haliMeoPressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    reduceMotion: Boolean = false,
    pressedScale: Float = 0.975f,
    label: String = "haliMeoPress"
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && !reduceMotion && pressed) pressedScale else 1f,
        animationSpec = if (reduceMotion) tween(durationMillis = 0)
        else spring(dampingRatio = 0.82f, stiffness = 700f),
        label = label
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
