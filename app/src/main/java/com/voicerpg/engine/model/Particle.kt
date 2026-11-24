package com.voicerpg.engine.model

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.random.Random

/**
 * High-performance 2D particle definition for retro spell animations.
 */
data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    val initialSize: Float,
    var alpha: Float = 1.0f,
    var life: Float = 1.0f,
    val decay: Float = Random.nextFloat() * 0.02f + 0.015f,
    val color: Color,
    val gravity: Float = 0f,
    val drag: Float = 0.98f,
    val isSquarePixel: Boolean = true
) {
    val isDead: Boolean get() = life <= 0f || alpha <= 0f || size <= 0.5f

    /**
     * Advance by [dtSeconds] of arena time. Velocity, gravity, drag and decay are tuned per 60 Hz frame;
     * a step is that many 60 Hz frames, so particles move the same at any frame rate.
     */
    fun update(dtSeconds: Float) {
        val frames = dtSeconds * 60f
        if (frames <= 0f) return
        val dragFactor = drag.pow(frames)
        x += vx * frames
        y += vy * frames
        vx *= dragFactor
        vy = (vy * dragFactor) + gravity * frames
        life -= decay * frames
        alpha = life.coerceIn(0f, 1f)
        size = (initialSize * (life * 0.8f + 0.2f)).coerceAtLeast(0f)
    }
}
