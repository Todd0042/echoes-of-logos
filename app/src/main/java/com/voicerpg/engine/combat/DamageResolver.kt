package com.voicerpg.engine.combat

import kotlin.math.max
import kotlin.random.Random

/**
 * Approved damage model (SPEC §2): affinity lives ONLY on base damage, the resonance
 * multiplier (1.0-3.0) is pure voice and is NEVER discounted by a matchup.
 * Nothing is immune: the engine floors effectiveness at 0.8 below any table lookup.
 */
data class DamageInputs(
    val spellBasePower: Float,
    val attackerLevel: Int = 1,
    val affinity: Float = 1.0f,
    val resonanceMultiplier: Float = 1.0f,
    val targetDefense: Int = 0,
    val defenseMultiplier: Float = 1.0f,
    val targetGuarding: Boolean = false,
    val attackerWeakened: Boolean = false
)

data class ResolvedStrike(
    val damage: Int,
    val affinity: Float,
    val isCrit: Boolean,
    val effectiveness: String
)

object DamageResolver {
    const val LEVEL_POTENCY_PER_LEVEL = 0.03f
    const val CRIT_CHANCE = 0.08f
    const val CRIT_MULTIPLIER = 1.5f
    const val VARIANCE_MIN = 0.9f
    const val VARIANCE_MAX = 1.1f
    const val GUARD_MULTIPLIER = 0.5f
    const val WEAKEN_MULTIPLIER = 0.75f
    /** Party-wide heals share potency so AoE kindness never eclipses a focused single heal. */
    const val PARTY_HEAL_SPLIT = 0.65f

    fun levelPower(basePower: Float, level: Int): Float =
        basePower * (1f + LEVEL_POTENCY_PER_LEVEL * (level - 1).coerceAtLeast(0))

    fun resolve(inputs: DamageInputs, random: Random = Random.Default): ResolvedStrike {
        val affinity = inputs.affinity.coerceIn(AffinityTable.FLOOR, 2.2f)
        val base = levelPower(inputs.spellBasePower, inputs.attackerLevel) * affinity
        val crit = random.nextFloat() < CRIT_CHANCE
        val variance = VARIANCE_MIN + random.nextFloat() * (VARIANCE_MAX - VARIANCE_MIN)
        var dmg = base * inputs.resonanceMultiplier.coerceAtLeast(1.0f) * variance
        if (crit) dmg *= CRIT_MULTIPLIER
        if (inputs.attackerWeakened) dmg *= WEAKEN_MULTIPLIER
        dmg -= inputs.targetDefense * inputs.defenseMultiplier
        if (inputs.targetGuarding) dmg *= GUARD_MULTIPLIER
        val finalDamage = max(1, dmg.toInt())
        return ResolvedStrike(
            damage = finalDamage,
            affinity = affinity,
            isCrit = crit,
            effectiveness = when {
                affinity >= 1.5f -> "SUPER_EFFECTIVE"
                affinity > 1.05f -> "EFFECTIVE"
                affinity < 0.95f -> "IMPAIRED"
                else -> "NEUTRAL"
            }
        )
    }

    fun resolveHeal(
        spellBasePower: Float,
        attackerLevel: Int,
        resonanceMultiplier: Float,
        partyWide: Boolean = false,
        random: Random = Random.Default
    ): Int {
        val variance = VARIANCE_MIN + random.nextFloat() * (VARIANCE_MAX - VARIANCE_MIN)
        val split = if (partyWide) PARTY_HEAL_SPLIT else 1.0f
        val amount = levelPower(spellBasePower, attackerLevel) * resonanceMultiplier.coerceAtLeast(1.0f) * variance * split
        return max(1, amount.toInt())
    }

    /** Global enemy damage tuning factor: minor increment to make combat threats sharper. */
    const val ENEMY_DAMAGE_TUNING = 1.15f

    /** Enemy attack vs party member: affinity-immune mitigation (guard), shallow variance. */
    fun resolveEnemyStrike(
        enemyAttack: Int,
        movePowerMult: Float,
        defenderGuarding: Boolean,
        defenderStatusMitigation: Float = 1.0f,
        random: Random = Random.Default
    ): Int {
        val variance = VARIANCE_MIN + random.nextFloat() * (VARIANCE_MAX - VARIANCE_MIN)
        var dmg = enemyAttack * movePowerMult * variance * defenderStatusMitigation.coerceIn(0.5f, 1.5f) * ENEMY_DAMAGE_TUNING
        if (defenderGuarding) dmg *= GUARD_MULTIPLIER
        return max(1, dmg.toInt())
    }

    /** Absolute ceiling of the whole model: 2.2 base-affinity x 3.0 transcendental voice. */
    const val THEORETICAL_MAX_MULTIPLIER = 6.6f

}
