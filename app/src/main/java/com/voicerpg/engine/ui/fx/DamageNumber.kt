package com.voicerpg.engine.ui.fx

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.exp

/**
 * A number popping off a combatant: large at birth, settling with an overshoot, arcing up and to the
 * side, then fading. [bornAt] is director time in seconds; the screen reads [scaleAt], [riseAt] and
 * [alphaAt] with the number's age.
 */
data class DamageNumber(
    val id: Long,
    val targetId: String,
    val text: String,
    val color: Color,
    /** WEAK!, RESIST, MYTHIC!… or null. */
    val label: String?,
    val labelColor: Color,
    val x: Float,
    val y: Float,
    /** Arc direction: +1 drifts right, -1 left. */
    val drift: Float,
    val big: Boolean,
    val bornAt: Float
) {
    fun scaleAt(age: Float): Float = 1f + 0.6f * exp(-age / 0.07f) * cos(age * 22f) * (if (big) 1.25f else 1f)

    /** dp offset from the spawn point: an arc that rises, drifts and drops back slightly (a bounce). */
    fun riseAt(age: Float): Pair<Float, Float> {
        val t = (age / LIFETIME).coerceIn(0f, 1f)
        val up = 46f * (2f * t - t * t)            // rises fast, eases out
        val bounce = if (t > 0.55f) 6f * kotlin.math.sin((t - 0.55f) * 14f) * (1f - t) else 0f
        return (drift * 22f * t) to -(up - bounce)
    }

    fun alphaAt(age: Float): Float = if (age < LIFETIME * 0.7f) 1f else ((LIFETIME - age) / (LIFETIME * 0.3f)).coerceIn(0f, 1f)

    companion object {
        const val LIFETIME = 1.1f
    }
}
