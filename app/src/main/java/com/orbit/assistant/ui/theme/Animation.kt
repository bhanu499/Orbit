package com.orbit.assistant.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntSize

object OrbitMotion {
    const val FAST_MS = 180
    const val STANDARD_MS = 280
    const val EMPHASIZED_MS = 400

    fun <T> standardTween() = tween<T>(
        durationMillis = STANDARD_MS,
        easing = FastOutSlowInEasing
    )

    fun <T> fastTween() = tween<T>(
        durationMillis = FAST_MS,
        easing = FastOutSlowInEasing
    )

    val expandSpring = spring<IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
