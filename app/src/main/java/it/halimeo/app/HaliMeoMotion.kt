package it.halimeo.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize

/**
 * Shared motion tokens for HaliMeo.
 *
 * Motion is inspired by the spatial transitions and settling list movement used in Morphe
 * Manager, adapted to HaliMeo's own navigation and visual language.
 */
internal object HaliMeoMotion {
    private val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    private const val SHARED_AXIS_DURATION = 300
    private const val SHARED_AXIS_FADE_OUT = 90
    private const val SHARED_AXIS_FADE_IN = SHARED_AXIS_DURATION - SHARED_AXIS_FADE_OUT
    private const val SHARED_AXIS_Z_BELOW = 0.8f
    private const val SHARED_AXIS_Z_ABOVE = 1.1f

    private val sharedAxisScale = tween<Float>(
        durationMillis = SHARED_AXIS_DURATION,
        easing = FastOutSlowInEasing
    )

    private val sharedAxisFadeIn =
        fadeIn(tween(SHARED_AXIS_FADE_IN, delayMillis = SHARED_AXIS_FADE_OUT, easing = LinearOutSlowInEasing))
    private val sharedAxisFadeOut =
        fadeOut(tween(SHARED_AXIS_FADE_OUT, easing = FastOutLinearInEasing))

    /** Responsive page movement with a restrained rebound instead of a rigid stop. */
    val pageSpring: SpringSpec<Float> =
        spring(dampingRatio = 0.78f, stiffness = 520f)

    /** The pill has a slightly softer landing so it feels attached to the finger. */
    val navigationPillSpring: SpringSpec<Float> =
        spring(dampingRatio = 0.72f, stiffness = 560f)

    val contentSizeSpring: SpringSpec<IntSize> =
        spring(dampingRatio = 0.80f, stiffness = 480f)

    /** Morphe-style list placement, tuned for a more visible but controlled settle. */
    fun <T> listSpring(dampingRatio: Float = 0.76f): SpringSpec<T> =
        spring(stiffness = 460f, dampingRatio = dampingRatio)

    /** More visible shared-axis transition for full-screen diary destinations. */
    fun detailEnter(forward: Boolean): EnterTransition =
        androidx.compose.animation.slideInHorizontally(
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 520f),
            initialOffsetX = { width -> if (forward) width / 7 else -width / 7 }
        ) + scaleIn(
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 560f),
            initialScale = 0.965f
        ) + fadeIn(tween(durationMillis = 160, easing = LinearOutSlowInEasing))

    fun detailExit(forward: Boolean): ExitTransition =
        androidx.compose.animation.slideOutHorizontally(
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f),
            targetOffsetX = { width -> if (forward) -width / 10 else width / 10 }
        ) + scaleOut(
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 560f),
            targetScale = 0.985f
        ) + fadeOut(tween(durationMillis = 125, easing = FastOutLinearInEasing))

    /** Shared Z-axis transition for screens that move deeper into the navigation stack. */
    fun sharedAxisZEnter(forward: Boolean): EnterTransition =
        scaleIn(
            animationSpec = sharedAxisScale,
            initialScale = if (forward) SHARED_AXIS_Z_BELOW else SHARED_AXIS_Z_ABOVE
        ) + sharedAxisFadeIn

    fun sharedAxisZExit(forward: Boolean): ExitTransition =
        scaleOut(
            animationSpec = sharedAxisScale,
            targetScale = if (forward) SHARED_AXIS_Z_ABOVE else SHARED_AXIS_Z_BELOW
        ) + sharedAxisFadeOut

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
 * Navigate at most one pager page at a time. A distant destination is first positioned
 * next to the current page, so intermediate screens are not animated across the viewport.
 */
internal suspend fun PagerState.turnToPage(page: Int) {
    val distance = page - currentPage
    when {
        distance > 1 -> scrollToPage(page - 1)
        distance < -1 -> scrollToPage(page + 1)
    }
    animateScrollToPage(page, animationSpec = HaliMeoMotion.pageSpring)
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
