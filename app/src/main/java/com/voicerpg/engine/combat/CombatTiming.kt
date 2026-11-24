package com.voicerpg.engine.combat

import com.voicerpg.engine.model.ResonanceTier
import kotlin.math.roundToLong

/**
 * Every wait in a combat turn, in one table: how long each [BattleCue] holds the stage before combat
 * logic moves on. `CombatViewModel` never picks a duration itself.
 *
 * Pocket (eyes-free) mode keeps its audio-paced values: there, most of a turn's length is the narration,
 * which the view model waits on separately, so the holds after it are short.
 *
 * [scale] multiplies everything: [INSTANT] (0) lets tests run whole battles without real time passing.
 */
data class CombatTiming(val scale: Float = 1f) {

    /** How long [cue] holds before logic continues. */
    fun holdMs(cue: BattleCue, pocket: Boolean): Long {
        // A release holds for exactly the flight time it was launched with (already scaled).
        if (cue is BattleCue.Release) return cue.travelMs
        return scaled(when (cue) {
            is BattleCue.ResonanceReveal -> when {
                !cue.showBanner -> 250
                cue.resonance.tier == ResonanceTier.TRANSCENDENTAL -> 1200
                else -> 1000
            }
            is BattleCue.Impact -> if (cue.fromParty) (if (pocket) 300 else 700) else 300
            is BattleCue.PhaseShift -> 400
            // Buffs, summons and guards are followed by their TurnEnd hold too; these are the extra pause.
            is BattleCue.EnemyMove -> when (cue.kind) {
                "SUMMON" -> if (pocket) 0 else 450
                "BUFF" -> if (pocket) 0 else 250
                else -> 0
            }
            is BattleCue.Guard -> if (pocket) 0 else 100
            is BattleCue.TurnEnd -> if (cue.fromParty) 200 else if (pocket) 300 else 250
            is BattleCue.Release,
            is BattleCue.BattleStart,
            is BattleCue.Death,
            is BattleCue.Reinforcements,
            is BattleCue.BattleEnd -> 0
        })
    }

    /** Projectile flight from caster to target. */
    fun travelMs(fromParty: Boolean, isBossUltimate: Boolean): Long =
        scaled(if (fromParty) 420 else if (isBossUltimate) 550 else 350)

    /** The resonance banner's exit animation, after its [BattleCue.ResonanceReveal] hold. */
    val bannerExitMs: Long get() = scaled(220)

    private fun scaled(ms: Int): Long = (ms * scale).roundToLong()

    companion object {
        val DEFAULT = CombatTiming()
        val INSTANT = CombatTiming(scale = 0f)
    }
}
