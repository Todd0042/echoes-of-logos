package com.voicerpg.engine.ui.fx

/**
 * The battle arena's clock. It advances on frame TIME (not "one step per frame"), so effects move at the
 * same speed on 60 Hz and 120 Hz screens.
 *
 * Two time streams come out of each [tick]:
 *  - [Step.realSeconds]: wall time, for things that must keep moving during a freeze (the arena shake).
 *  - [Step.fxSeconds]: arena time, which [hitStop] stops and [slowMotion] slows. Projectiles, particles
 *    and sprite animation all read it, so one freeze stops the whole arena together.
 */
class FxClock {

    data class Step(val realSeconds: Float, val fxSeconds: Float)

    private var lastNanos = -1L
    private var stopRemaining = 0f
    private var slowRemaining = 0f
    private var slowScale = 1f

    /** Arena speed right now: 0 during a hit-stop, the slow-motion scale during slow motion, else 1. */
    val timeScale: Float get() = if (stopRemaining > 0f) 0f else if (slowRemaining > 0f) slowScale else 1f

    /** Advance to [frameNanos] (a frame timestamp). The first call only sets the origin. */
    fun tick(frameNanos: Long): Step {
        if (lastNanos < 0L) {
            lastNanos = frameNanos
            return Step(0f, 0f)
        }
        // A long gap (app resumed, a dropped run of frames) is one capped step, never a jump.
        val real = ((frameNanos - lastNanos) / 1e9f).coerceIn(0f, MAX_STEP_SECONDS)
        lastNanos = frameNanos

        var remaining = real
        var fx = 0f
        if (stopRemaining > 0f) {
            val frozen = minOf(remaining, stopRemaining)
            stopRemaining -= frozen
            remaining -= frozen
        }
        if (remaining > 0f && slowRemaining > 0f) {
            val slow = minOf(remaining, slowRemaining)
            slowRemaining -= slow
            remaining -= slow
            fx += slow * slowScale
        }
        fx += remaining
        return Step(real, fx)
    }

    /** Freeze arena time for [durationMs] (a longer request extends the freeze; a shorter one doesn't cut it). */
    fun hitStop(durationMs: Long) {
        stopRemaining = maxOf(stopRemaining, durationMs / 1000f)
    }

    /** Run arena time at [scale] for [durationMs]. */
    fun slowMotion(scale: Float, durationMs: Long) {
        slowScale = scale.coerceIn(0f, 1f)
        slowRemaining = maxOf(slowRemaining, durationMs / 1000f)
    }

    /** Forget freezes and the frame origin (a new battle). */
    fun reset() {
        lastNanos = -1L
        stopRemaining = 0f
        slowRemaining = 0f
        slowScale = 1f
    }

    companion object {
        const val MAX_STEP_SECONDS = 0.05f
    }
}
