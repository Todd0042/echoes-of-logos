package com.voicerpg.engine.combat

import com.voicerpg.engine.model.ResonanceTier
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M9 pacing targets (docs/COMBAT_FEEL_PLAN.md): a Basic turn runs from release to the next input in at
 * most 1.6 s; a Transcendental turn, with its cut-in, slow motion and banner, takes about 3.5 s. Stronger
 * voices always hold the stage at least as long as weaker ones. Pocket mode stays short (its narration is
 * the pacing).
 */
class CombatPacingTest {

    private val t = CombatTiming.DEFAULT

    /** Release to next input for a hero cast at [tier], from the table alone. */
    private fun heroTurnMs(tier: ResonanceTier, pocket: Boolean = false): Long {
        val release = BattleCue.Release("hero", true, listOf("foe"), com.voicerpg.engine.model.SpellSchool.PYROMANCY,
            BattleCue.ReleaseStyle.PROJECTILE, tier = tier, particleCount = 40,
            travelMs = t.travelMs(fromParty = true, isBossUltimate = false, tier = tier))
        val impact = BattleCue.Impact("hero", true, listOf(BattleCue.Hit("foe", 50)), tier = tier)
        val cutIn = if (tier >= ResonanceTier.MYTHIC) t.holdMs(BattleCue.CutIn("hero", "A", tier, null, null, null, false), pocket) else 0L
        return cutIn + t.holdMs(release, pocket) + t.holdMs(impact, pocket) + t.holdMs(BattleCue.TurnEnd("hero", true), pocket)
    }

    @Test
    fun `a basic turn is snappy`() {
        assertTrue("${heroTurnMs(ResonanceTier.BASIC)} ms", heroTurnMs(ResonanceTier.BASIC) <= 1400) // + render/transition overhead <= 1.6 s
    }

    @Test
    fun `a transcendental turn lingers for about three and a half seconds`() {
        val ms = heroTurnMs(ResonanceTier.TRANSCENDENTAL)
        assertTrue("$ms ms", ms in 3000..3700)
    }

    @Test
    fun `stronger voices hold the stage at least as long, and pocket mode stays brisk`() {
        val turns = ResonanceTier.entries.map { heroTurnMs(it) }
        assertTrue("$turns", turns.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(heroTurnMs(ResonanceTier.TRANSCENDENTAL, pocket = true) < heroTurnMs(ResonanceTier.TRANSCENDENTAL))
    }
}
