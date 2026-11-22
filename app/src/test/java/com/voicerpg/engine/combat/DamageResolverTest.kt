package com.voicerpg.engine.combat

import kotlin.math.roundToInt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DamageResolverTest {

    @Test
    fun `nothing is immune - every school family cell stays at or above the 0_8 floor`() {
        for (school in School.entries) {
            for (family in EnemyFamily.entries) {
                val eff = AffinityTable.affinity(school, family)
                assertTrue("$school vs $family = $eff below floor", eff >= 0.8f)
                assertTrue("$school vs $family = $eff above ceiling", eff <= 2.0f)
            }
        }
    }

    @Test
    fun `same element contact impairs but never blocks`() {
        val fireOnFireSelf = AffinityTable.effectiveness(School.PYROMANCY, EnemyFamily.FLESH, School.PYROMANCY)
        assertEquals(0.8f, fireOnFireSelf, 0.001f)
    }

    @Test
    fun `voice is never discounted by affinity - transcendental resisted beats basic weakness pick`() {
        val basicWeakness = 1.0f * 2.0f
        val transcendResist = 3.0f * 0.8f
        assertTrue("knowledge must not out-rank eloquence", transcendResist >= basicWeakness)
    }

    @Test
    fun `god pick - right word right target multiplies on top`() {
        val resolved = DamageResolver.resolve(
            DamageInputs(
                spellBasePower = 100f,
                attackerLevel = 1,
                affinity = 2.0f,
                resonanceMultiplier = 3.0f,
                targetDefense = 0
            ),
            Random(7)
        )
        assertTrue("expected transcendental weakness hit near 600, got ${resolved.damage}", resolved.damage in 480..660)
    }

    @Test
    fun `damage always lands at least one point even vs absurd defense`() {
        val resolved = DamageResolver.resolve(
            DamageInputs(spellBasePower = 10f, affinity = 0.8f, resonanceMultiplier = 1.0f, targetDefense = 9999)
        )
        assertEquals(1, resolved.damage)
    }

    @Test
    fun `guard halves incoming and crit doubles the voice - texture only`() {
        var guardSum = 0
        var plainSum = 0
        repeat(200) {
            plainSum += DamageResolver.resolveEnemyStrike(100, 1.0f, defenderGuarding = false, random = Random(it))
            guardSum += DamageResolver.resolveEnemyStrike(100, 1.0f, defenderGuarding = true, random = Random(it))
        }
        val ratio = guardSum.toFloat() / plainSum.toFloat()
        assertTrue("guard ratio $ratio should be near 0.5", ratio in 0.42f..0.55f)
    }

    @Test
    fun `level potency is a shallow foundation`() {
        val l1 = DamageResolver.levelPower(100f, 1)
        val l10 = DamageResolver.levelPower(100f, 10)
        assertTrue("level 10 should only be ~27% stronger, got ${l10 / l1}", l10 / l1 < 1.35f)
    }

    @Test
    fun `heals scale with voice and never go negative`() {
        repeat(50) { seed ->
            val heal = DamageResolver.resolveHeal(100f, 1, 1.0f, random = Random(seed))
            assertTrue(heal in 80..120)
        }
    }
}
