package com.voicerpg.engine.ui.fx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The battle arena's shake, in dp. Only the arena box moves; the console and status bar stay still.
 *
 * A directional kick swings first toward the side that was hit (a hero's blow pushes toward the enemies,
 * an enemy's toward the party) and decays; an undirected kick (a boss ultimate, reinforcements) is a
 * decaying jitter. A stronger kick replaces a fading one; a weaker one never cuts a strong one short.
 * Runs on real time (FxClock.Step.realSeconds), so it keeps moving through a hit-stop.
 */
class ArenaShake(private val random: Random = Random.Default) {

    var offset: Offset by mutableStateOf(Offset.Zero)
        private set

    private var amplitude = 0f
    private var direction = 0f
    private var elapsed = 0f
    private var phaseX = 0f
    private var phaseY = 0f

    private val envelope: Float get() = amplitude * exp(-elapsed / DECAY_SECONDS)

    /** Start a shake of [magnitude] dp. [direction]: +1 toward the enemy side, -1 toward the party, 0 none. */
    fun kick(magnitude: Float, direction: Float) {
        if (magnitude <= 0f || magnitude < envelope) return
        amplitude = magnitude
        this.direction = direction.coerceIn(-1f, 1f)
        elapsed = 0f
        phaseX = random.nextFloat() * TWO_PI
        phaseY = random.nextFloat() * TWO_PI
    }

    fun step(dtSeconds: Float) {
        if (amplitude == 0f) return
        elapsed += dtSeconds
        val e = envelope
        if (e < SETTLED_DP) {
            clear()
            return
        }
        offset = if (direction != 0f) {
            Offset(
                direction * e * cos(TWO_PI * SWING_HZ * elapsed),
                e * 0.25f * sin(TWO_PI * JITTER_Y_HZ * elapsed + phaseY)
            )
        } else {
            Offset(
                e * sin(TWO_PI * JITTER_X_HZ * elapsed + phaseX),
                e * sin(TWO_PI * JITTER_Y_HZ * elapsed + phaseY)
            )
        }
    }

    fun clear() {
        amplitude = 0f
        elapsed = 0f
        offset = Offset.Zero
    }

    private companion object {
        const val TWO_PI = (2 * PI).toFloat()
        const val DECAY_SECONDS = 0.09f
        const val SETTLED_DP = 0.3f
        const val SWING_HZ = 12f
        const val JITTER_X_HZ = 17f
        const val JITTER_Y_HZ = 23f
    }
}
