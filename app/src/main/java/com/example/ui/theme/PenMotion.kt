package com.example.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset

/**
 * Hardware-tuned motion profiles for Android 5.0.2 / MT6580 Mali-400 GPU (1GB RAM).
 * - FAST (default): crisp 150-190ms GPU-composited EaseOutExpo translation/fade without expensive multi-layer scale/clip passes.
 * - SPRING: Apple-style non-linear spring physics for higher-spec devices.
 * - NONE: instant zero-latency switching for ultra power-saving.
 */
enum class PenMotionTier(val id: String, val label: String) {
    FAST("fast", "极速流畅"),
    SPRING("spring", "轻盈弹簧"),
    NONE("none", "零动效");

    companion object {
        fun fromId(id: String): PenMotionTier = entries.firstOrNull { it.id == id } ?: FAST
    }
}

object PenMotion {
    val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val AppleSheetEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    fun <T> springSnappy() = spring<T>(
        dampingRatio = 0.82f,
        stiffness = 460f
    )

    fun <T> springGentle() = spring<T>(
        dampingRatio = 0.86f,
        stiffness = 340f
    )

    fun intOffsetSpring() = spring<IntOffset>(
        dampingRatio = 0.85f,
        stiffness = 440f
    )

    fun overlayEnter(fromTop: Boolean = false, tier: PenMotionTier = PenMotionTier.FAST): EnterTransition = when (tier) {
        PenMotionTier.NONE -> EnterTransition.None
        PenMotionTier.FAST -> fadeIn(tween(145, easing = EaseOutExpo)) +
            slideInVertically(
                initialOffsetY = { if (fromTop) -it / 7 else it / 8 },
                animationSpec = tween(165, easing = EaseOutExpo)
            )
        PenMotionTier.SPRING -> fadeIn(tween(180, easing = EaseOutExpo)) +
            scaleIn(
                initialScale = 0.95f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
            ) +
            slideInVertically(
                initialOffsetY = { if (fromTop) -it / 5 else it / 6 },
                animationSpec = intOffsetSpring()
            )
    }

    fun overlayExit(toTop: Boolean = false, tier: PenMotionTier = PenMotionTier.FAST): ExitTransition = when (tier) {
        PenMotionTier.NONE -> ExitTransition.None
        PenMotionTier.FAST -> fadeOut(tween(115, easing = EaseOutExpo))
        PenMotionTier.SPRING -> fadeOut(tween(140, easing = EaseOutExpo)) +
            slideOutVertically(
                targetOffsetY = { if (toTop) -it / 6 else it / 7 },
                animationSpec = tween(150, easing = EaseOutExpo)
            )
    }

    fun verticalScreenTransition(
        slideUpEnter: Boolean,
        tier: PenMotionTier = PenMotionTier.FAST
    ): ContentTransform = when (tier) {
        PenMotionTier.NONE -> EnterTransition.None togetherWith ExitTransition.None
        PenMotionTier.FAST -> {
            val enter = slideInVertically(
                initialOffsetY = { if (slideUpEnter) it / 4 else -it / 4 },
                animationSpec = tween(180, easing = EaseOutExpo)
            ) + fadeIn(tween(150, easing = EaseOutExpo))
            val exit = fadeOut(tween(120, easing = EaseOutExpo))
            enter togetherWith exit
        }
        PenMotionTier.SPRING -> {
            val enter = slideInVertically(
                initialOffsetY = { if (slideUpEnter) it / 3 else -it / 3 },
                animationSpec = intOffsetSpring()
            ) + fadeIn(tween(180, easing = EaseOutExpo))
            val exit = slideOutVertically(
                targetOffsetY = { if (slideUpEnter) -it / 6 else it / 6 },
                animationSpec = tween(160, easing = EaseOutExpo)
            ) + fadeOut(tween(140))
            enter togetherWith exit
        }
    }

    fun screenTransform(tier: PenMotionTier = PenMotionTier.FAST): ContentTransform =
        overlayEnter(tier = tier) togetherWith overlayExit(tier = tier)

    fun horizontalPaneTransition(
        forward: Boolean,
        tier: PenMotionTier = PenMotionTier.FAST
    ): ContentTransform = when (tier) {
        PenMotionTier.NONE -> EnterTransition.None togetherWith ExitTransition.None
        PenMotionTier.FAST -> {
            val enter = slideInHorizontally(
                initialOffsetX = { if (forward) it / 3 else -it / 3 },
                animationSpec = tween(160, easing = EaseOutExpo)
            ) + fadeIn(tween(140, easing = EaseOutExpo))
            val exit = fadeOut(tween(110))
            enter togetherWith exit
        }
        PenMotionTier.SPRING -> {
            val enter = slideInHorizontally(
                initialOffsetX = { if (forward) it / 2 else -it / 2 },
                animationSpec = intOffsetSpring()
            ) + fadeIn(tween(170, easing = EaseOutExpo))
            val exit = slideOutHorizontally(
                targetOffsetX = { if (forward) -it / 4 else it / 4 },
                animationSpec = tween(150, easing = EaseOutExpo)
            ) + fadeOut(tween(130))
            enter togetherWith exit
        }
    }

    fun horizontalPaneTransition(direction: Int, tier: PenMotionTier = PenMotionTier.FAST): ContentTransform =
        horizontalPaneTransition(forward = direction >= 0, tier = tier)
}

/**
 * Lightweight GPU-layer press scale without deprecated Modifier.composed sub-composition overhead.
 * Zero-cost no-op under NONE tier or zeroOverdraw mode (no coroutine, no state, no graphicsLayer).
 */
@Composable
fun Modifier.springPressScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f
): Modifier {
    val tier = LocalPenMotionTier.current
    val zeroOverdraw = LocalZeroOverdraw.current
    if (!enabled || tier == PenMotionTier.NONE || zeroOverdraw) return this
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (tier == PenMotionTier.FAST) {
            tween(durationMillis = 85, easing = PenMotion.EaseOutExpo)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 520f)
        },
        label = "pen_press_scale"
    )
    return this
        .graphicsLayer {
            if (scale != 1f) {
                scaleX = scale
                scaleY = scale
            }
        }
        .pointerInput(enabled, tier) {
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
        }
}

/**
 * Hardware-tuned press scale + click handler without deprecated Modifier.composed overhead.
 */
@Composable
fun Modifier.springPressScale(
    pressedScale: Float = 0.96f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val tier = LocalPenMotionTier.current
    val zeroOverdraw = LocalZeroOverdraw.current
    if (!enabled || tier == PenMotionTier.NONE || zeroOverdraw) {
        return this.clickable(enabled = enabled, onClick = onClick)
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = when (tier) {
            PenMotionTier.NONE -> snap()
            PenMotionTier.FAST -> tween(durationMillis = 80, easing = PenMotion.EaseOutExpo)
            PenMotionTier.SPRING -> spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 520f)
        },
        label = "pen_click_spring_scale"
    )
    return this
        .graphicsLayer {
            if (scale != 1f) {
                scaleX = scale
                scaleY = scale
            }
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
