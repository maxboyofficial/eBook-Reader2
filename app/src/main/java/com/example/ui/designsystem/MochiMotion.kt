package com.example.ui.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * Thin design-system motion layer inspired by Material 3 Expressive MotionScheme.
 * Degrades springs cleanly to fast linear/fade transitions when [reduceMotion] is true
 * (or when the E-Ink theme is active).
 */
@Immutable
data class MochiMotionTokens(
    val reduceMotion: Boolean
) {
    fun <T> spatialFast(): FiniteAnimationSpec<T> = if (reduceMotion) {
        tween(durationMillis = 1)
    } else {
        spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
    }

    fun <T> spatialDefault(): FiniteAnimationSpec<T> = if (reduceMotion) {
        tween(durationMillis = 1)
    } else {
        spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)
    }

    fun <T> spatialSlow(): FiniteAnimationSpec<T> = if (reduceMotion) {
        tween(durationMillis = 1)
    } else {
        spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)
    }

    fun <T> effectsDefault(): FiniteAnimationSpec<T> = if (reduceMotion) {
        tween(durationMillis = 1)
    } else {
        tween(durationMillis = 200)
    }

    fun dpSpring(): FiniteAnimationSpec<Dp> = spatialDefault()
    fun offsetSpring(): FiniteAnimationSpec<IntOffset> = spatialFast()
}

val LocalMochiMotion = staticCompositionLocalOf { MochiMotionTokens(reduceMotion = false) }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.mochiSharedBookBounds(key: String?): Modifier {
    if (key.isNullOrBlank()) return this
    val motion = LocalMochiMotion.current
    if (motion.reduceMotion) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animScope = LocalAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@mochiSharedBookBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animScope,
            boundsTransform = { _, _ -> motion.spatialDefault() }
        )
    }
}

