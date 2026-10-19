package com.sameerasw.essentials.island.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

object IslandMotion {
    const val DAMPING = 0.86f
    const val STIFFNESS = 260f

    const val EXPAND_MS = 460
    private val ExpandEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val size: FiniteAnimationSpec<IntSize> = tween(EXPAND_MS, easing = ExpandEasing)
    const val COMPACT_DAMPING = 0.78f
    const val COMPACT_STIFFNESS = 230f
    const val COMPACT_MS = 380
    val compactSize: FiniteAnimationSpec<IntSize> = tween(COMPACT_MS, easing = ExpandEasing)
    val compactOffset: FiniteAnimationSpec<IntOffset> = tween(COMPACT_MS, easing = ExpandEasing)
    fun <T> compactFloat(): FiniteAnimationSpec<T> = spring(COMPACT_DAMPING, COMPACT_STIFFNESS)
    const val COLLAPSE_MS = 480
    private val CollapseEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
    val collapseSize: FiniteAnimationSpec<IntSize> = spring(0.72f, 320f, IntSize(1, 1))
    val offset: FiniteAnimationSpec<IntOffset> = spring(Spring.DampingRatioNoBouncy, 300f, IntOffset(1, 1))
    fun <T> float(): FiniteAnimationSpec<T> = spring(DAMPING, STIFFNESS)
    fun <T> collapseFloat(): FiniteAnimationSpec<T> = tween(COLLAPSE_MS, easing = CollapseEasing)
    fun collapseProgress(): FiniteAnimationSpec<Float> = tween(COLLAPSE_MS, easing = CollapseEasing)

    const val HIDE_BUBBLE_MS = 320
    fun hideBubble(): FiniteAnimationSpec<Float> = tween(HIDE_BUBBLE_MS, easing = ExpandEasing)

    const val SQUASH_MS = 560
    private val SquashEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    fun squash(): FiniteAnimationSpec<Float> = tween(SQUASH_MS, easing = SquashEasing)

    fun release(): FiniteAnimationSpec<Float> = spring(Spring.DampingRatioNoBouncy, 380f)

    fun fling(): FiniteAnimationSpec<Float> = spring(Spring.DampingRatioNoBouncy, 700f)
    fun <T> settle(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)

    fun <T> contentIn(): FiniteAnimationSpec<T> = tween(durationMillis = 280, delayMillis = 90)
    fun <T> contentOut(): FiniteAnimationSpec<T> = tween(durationMillis = 150)

    const val CONTENT_SCALE = 0.06f
    fun contentSpring(): FiniteAnimationSpec<Float> = spring(0.9f, 300f)

    fun <T> smoothSpatial(): FiniteAnimationSpec<T> = tween(EXPAND_MS, easing = ExpandEasing)
    fun <T> expressiveFastSpatial(): FiniteAnimationSpec<T> = spring(0.7f, 650f)
    fun <T> expressiveDefaultSpatial(): FiniteAnimationSpec<T> = spring(0.82f, 300f)
    fun <T> expressiveSlowSpatial(): FiniteAnimationSpec<T> = spring(0.82f, 160f)
    fun <T> expressiveFastEffects(): FiniteAnimationSpec<T> = spring(1f, 3000f)
    fun <T> expressiveDefaultEffects(): FiniteAnimationSpec<T> = spring(1f, 1300f)
    fun <T> expressiveSlowEffects(): FiniteAnimationSpec<T> = spring(1f, 650f)

    const val COLLAPSE_THRESHOLD = 0.3f
    const val DISMISS_THRESHOLD = 0.3f
}
