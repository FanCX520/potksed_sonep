package com.example.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset

/**
 * Apple-style non-linear physics curves tuned for Mali-400/680 GPU & 1GB RAM (smooth < 320ms, zero blur).
 */
object PenMotion {
    val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val AppleSheetEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    fun <T> springSnappy() = spring<T>(
        dampingRatio = 0.78f,
        stiffness = 380f
    )

    fun <T> springGentle() = spring<T>(
        dampingRatio = 0.82f,
        stiffness = 280f
    )

    fun intOffsetSpring() = spring<IntOffset>(
        dampingRatio = 0.80f,
        stiffness = 360f
    )

    fun overlayEnter(fromTop: Boolean = false): EnterTransition =
        fadeIn(tween(220, easing = EaseOutExpo)) +
            scaleIn(
                initialScale = 0.93f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f)
            ) +
            slideInVertically(
                initialOffsetY = { if (fromTop) -it / 4 else it / 5 },
                animationSpec = intOffsetSpring()
            )

    fun overlayExit(toTop: Boolean = false): ExitTransition =
        fadeOut(tween(170, easing = EaseOutExpo)) +
            scaleOut(targetScale = 0.95f, animationSpec = tween(170, easing = EaseOutExpo)) +
            slideOutVertically(
                targetOffsetY = { if (toTop) -it / 5 else it / 6 },
                animationSpec = tween(180, easing = EaseOutExpo)
            )

    fun verticalScreenTransition(slideUpEnter: Boolean): ContentTransform {
        val enter = slideInVertically(
            initialOffsetY = { if (slideUpEnter) it / 2 else -it / 2 },
            animationSpec = intOffsetSpring()
        ) + scaleIn(
            initialScale = 0.94f,
            animationSpec = spring(dampingRatio = 0.80f, stiffness = 340f)
        ) + fadeIn(tween(240, easing = EaseOutExpo))

        val exit = slideOutVertically(
            targetOffsetY = { if (slideUpEnter) -it / 5 else it / 5 },
            animationSpec = tween(220, easing = EaseOutExpo)
        ) + scaleOut(
            targetScale = 0.96f,
            animationSpec = tween(220, easing = EaseOutExpo)
        ) + fadeOut(tween(180))

        return enter togetherWith exit
    }

    fun horizontalPaneTransition(forward: Boolean): ContentTransform {
        val enter = slideInHorizontally(
            initialOffsetX = { if (forward) it / 2 else -it / 2 },
            animationSpec = intOffsetSpring()
        ) + scaleIn(
            initialScale = 0.95f,
            animationSpec = spring(dampingRatio = 0.80f, stiffness = 380f)
        ) + fadeIn(tween(200, easing = EaseOutExpo))

        val exit = slideOutHorizontally(
            targetOffsetX = { if (forward) -it / 3 else it / 3 },
            animationSpec = tween(180, easing = EaseOutExpo)
        ) + fadeOut(tween(150))

        return enter togetherWith exit
    }

    fun horizontalPaneTransition(direction: Int): ContentTransform =
        horizontalPaneTransition(forward = direction >= 0)
}

/**
 * Tactile spring bounce scale when pressed (1.0f -> 0.96f -> 1.0f) without intercepting normal clickable events.
 */
fun Modifier.springPressScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f
): Modifier = composed {
    if (!enabled) return@composed this
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 480f
        ),
        label = "pen_press_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
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
 * Tactile spring bounce scale + click handler using MutableInteractionSource so both touch and semantics clicks work cleanly.
 */
fun Modifier.springPressScale(
    pressedScale: Float = 0.96f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 480f
        ),
        label = "pen_click_spring_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
