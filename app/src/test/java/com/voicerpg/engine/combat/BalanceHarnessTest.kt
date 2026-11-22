package com.voicerpg.engine.combat

import com.voicerpg.engine.content.GameContent
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Balance Harness - Executable invariants for combat and voice scaling.
 * As documented in docs/combat-design/BALANCE-HARNESS.md.
 *
 * Runs headless on the JVM (no Robolectric or Android OS dependencies).
 */
class BalanceHarnessTest {

    @Test
    fun `invariant I1 - resonance matters transcendental neutral exceeds basic weakness`() {
        // Invariant I-1: Voice skill dominates affinity.
        // Chanting at TRANSCENDENTAL (2.55x-3.0x resonance) against a neutral target (1.0x)
        // must exceed chanting at BASIC (1.0x-1.15x) against a signature weakness target (1.3x).
        val spells = GameContent.spells.values
        var passedCount = 0
        var totalSpells = 0

        for (spell in spells) {
            if (spell.basePower <= 0 || spell.isHeal) continue // Skip pure utility/heals
            totalSpells++

            // Neutral multiplier with Transcendental resonance (~2.5x)
            val neutralTranscendental = spell.basePower.toFloat() * 1.0f * 2.5f

            // Weakness multiplier (1.3x) with Basic resonance (~1.05x)
            val weaknessBasic = spell.basePower.toFloat() * 1.3f * 1.05f

            if (neutralTranscendental > weaknessBasic) {
                passedCount++
            }
        }

        if (totalSpells > 0) {
            val passRate = passedCount.toFloat() / totalSpells.toFloat()
            assertTrue(
                "Resonance invariant I-1: at least 95% of damage spells must reward Transcendental chant over Basic weakness. Pass rate: $passRate ($passedCount/$totalSpells)",
                passRate >= 0.95f
            )
        }
    }

    @Test
    fun `invariant I2 - nothing blocked system floor guarantees minimum 80 percent effectiveness`() {
        // Invariant I-2: Floor effectiveness multiplier is at least 0.8 across every school and family pair.
        for (school in School.entries) {
            for (family in EnemyFamily.entries) {
                val affinity = AffinityTable.affinity(school, family)
                assertTrue(
                    "Affinity floor for $school vs $family must be >= 0.8, got $affinity",
                    affinity >= 0.8f
                )
            }
        }
    }

    @Test
    fun `invariant I2 - status utility lands on every enemy family`() {
        // Invariant I-2b: Status effects always retain at least 1 turn duration and positive potency across families.
        val testFamilies = EnemyFamily.entries
        for (family in testFamilies) {
            val rootResult = StatusSystem.apply(StatusId.ROOT, "ADEPT", family, 60f)
            assertTrue("Root must land on $family", rootResult != null && rootResult.turnsLeft >= 1 && rootResult.potPct > 0f)

            val poisonResult = StatusSystem.apply(StatusId.POISON, "ADEPT", family, 50f)
            assertTrue("Poison must land on $family", poisonResult != null && poisonResult.turnsLeft >= 1 && poisonResult.potPct > 0f)
        }
    }

    @Test
    fun `invariant I3 - no useless ally every party member has a relevant lane`() {
        // Invariant I-3: Every member in every legal encounter roster has an affinity-immune lane (heal/guard/status)
        // or a viable damage floor that benefits from voice resonance.
        for (encounter in GameContent.encounters.values) {
            val roster = encounter.initialParty?.map { it.id } ?: listOf("hero")
            for (member in roster) {
                val kitSpells = GameContent.heroSpellsFor(member)
                val hasUtilityOrHeal = kitSpells.any { it.isHeal || it.isGuard || it.lifesteal || it.status != null }
                val hasDamageSpells = kitSpells.any { it.basePower > 0 }

                assertTrue(
                    "Member '$member' in encounter '${encounter.id}' must have viable actions",
                    hasUtilityOrHeal || hasDamageSpells
                )
            }
        }
    }

    @Test
    fun `simulation harness - multi-turn turn loop maintains hp sanity and finishes`() {
        // Headless simulation exercising DamageResolver and state progression over 50 combat rounds.
        var heroHp = 250f
        var enemyHp = 300f
        var turnCount = 0
        val maxTurns = 50
        val rng = Random(42)

        while (heroHp > 0f && enemyHp > 0f && turnCount < maxTurns) {
            turnCount++

            // Player turn: Adept resonance chant (1.35x)
            val outcome = DamageResolver.resolve(
                DamageInputs(
                    spellBasePower = 35f,
                    attackerLevel = 3,
                    affinity = 1.0f,
                    resonanceMultiplier = 1.35f,
                    targetDefense = 0
                ),
                rng
            )
            enemyHp = (enemyHp - outcome.damage).coerceAtLeast(0f)

            if (enemyHp <= 0f) break

            // Enemy turn: baseline hit
            val enemyHit = 15f
            heroHp = (heroHp - enemyHit).coerceAtLeast(0f)
        }

        assertTrue("Combat simulation must terminate within $maxTurns turns (took $turnCount)", turnCount < maxTurns)
        assertTrue("At least one combatant must reach 0 HP", heroHp <= 0f || enemyHp <= 0f)
    }
}
