package com.voicerpg.engine.combat

/**
 * When the battle music's intensity layer plays (M8): below [threshold] party health, until health is
 * back [HYSTERESIS] above it (no flicker at the line); a boss phase change keeps it on for the fight.
 */
object MusicIntensityRule {
    const val HYSTERESIS = 0.1f

    fun next(current: Boolean, partyHpRatio: Float, threshold: Float, bossPhaseShifted: Boolean): Boolean = when {
        bossPhaseShifted -> true
        partyHpRatio < threshold -> true
        partyHpRatio >= threshold + HYSTERESIS -> false
        else -> current
    }
}
