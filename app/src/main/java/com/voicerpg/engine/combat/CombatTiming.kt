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
            // A typed chant gets a short simulated charge; a spoken one lasts as long as the voice.
            is BattleCue.ChargeStart -> if (cue.typed) 350 else 0
            // The reveal lands with the impact: the impact's hold lets it shine.
            is BattleCue.ResonanceReveal -> 0
            // M9 pacing: a hero's result holds longer the stronger the voice (room for slow motion, zoom,
            // banner); pocket mode keeps it brisk, its narration is the pacing.
            is BattleCue.Impact -> if (!cue.fromParty) 300 else if (pocket) 300 else HERO_IMPACT_HOLD_MS.getValue(cue.tier)
            is BattleCue.PhaseShift -> 400
            // Pocket mode skips the panel (its quote is spoken instead).
            is BattleCue.CutIn -> if (pocket) 0 else 650
            // Buffs, summons and guards are followed by their TurnEnd hold too; these are the extra pause.
            is BattleCue.EnemyMove -> when (cue.kind) {
                "SUMMON" -> if (pocket) 0 else 450
                "BUFF" -> if (pocket) 0 else 250
                // An attack's wind-up (already scaled into the cue, like a release's flight).
                else -> return cue.windupMs
            }
            is BattleCue.Guard -> if (pocket) 0 else 100
            is BattleCue.TurnEnd -> if (cue.fromParty) 200 else if (pocket) 300 else 250
            is BattleCue.Release,
            is BattleCue.Telegraph,
            is BattleCue.Stagger,
            is BattleCue.Taunt,
            is BattleCue.MusicIntensity,
            is BattleCue.Streak,
            is BattleCue.UltimateCharge,
            is BattleCue.TitleCard,
            is BattleCue.ChargeLevel,
            is BattleCue.ChargeEnd,
            is BattleCue.BattleStart,
            is BattleCue.Death,
            is BattleCue.Reinforcements,
            is BattleCue.BattleEnd -> 0
        })
    }

    /** An enemy attack's wind-up: the move's own `windupMs`, else longer for ultimates. */
    fun windupMs(declared: Long?, isBossUltimate: Boolean): Long =
        scaled((declared ?: if (isBossUltimate) 650L else 280L).coerceIn(0L, 3000L).toInt())

    /** Projectile flight from caster to target; a hero's stronger voices fly a little longer. */
    fun travelMs(fromParty: Boolean, isBossUltimate: Boolean, tier: ResonanceTier = ResonanceTier.BASIC): Long =
        scaled(if (fromParty) 380 + 40 * tier.ordinal else if (isBossUltimate) 550 else 350)

    private fun scaled(ms: Int): Long = (ms * scale).roundToLong()

    companion object {
        /** Targets: Basic release → next input ≤ 1.6 s; Transcendental ≈ 3.5 s with its cut-in (CombatPacingTest). */
        private val HERO_IMPACT_HOLD_MS = mapOf(
            ResonanceTier.BASIC to 650, ResonanceTier.ADEPT to 700, ResonanceTier.MASTER to 800,
            ResonanceTier.MYTHIC to 1050, ResonanceTier.TRANSCENDENTAL to 1700
        )

        val DEFAULT = CombatTiming()
        val INSTANT = CombatTiming(scale = 0f)
    }
}
