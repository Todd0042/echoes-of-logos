package com.voicerpg.engine.combat

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Landed, per-combatant status instance. I-2: a resisted status still LANDS -
 * never potency 0, never 0 turns (landing floor clamps below).
 */
data class StatusInstance(
    val status: StatusId,
    val turnsLeft: Int,
    val potPct: Float,
    val stacks: Int = 1,
    val maxStacks: Int = 1,
    val fromHit: Float = 0f
) {
    val isDot: Boolean get() = status in DOTS
    val isDebuffOnTarget: Boolean get() = status in DEBUFFS

    companion object {
        val DOTS = setOf(StatusId.BURN, StatusId.POISON, StatusId.BLEED)
        val DEBUFFS = setOf(StatusId.BURN, StatusId.POISON, StatusId.BLEED, StatusId.CHILL, StatusId.ROOT, StatusId.OVERLOAD, StatusId.CORRODE, StatusId.WEAKEN)
    }
}

object StatusSystem {

    /**
     * Applies [id] from a hit of [tier] against [family], respecting per-family
     * resistance as IMPAIRMENT ONLY (multiplies potency/duration down, never to zero).
     */
    fun apply(id: StatusId, tier: String, family: EnemyFamily, fromHit: Float): StatusInstance? {
        val def = StatusCatalogTable.DEFS[id.name] ?: return null
        val t = def.tiers[tier] ?: def.tiers["BASIC"] ?: return null
        val resistKey = def.resist[family.name] ?: def.resist["all"] ?: "normal"
        val mult = when (resistKey) {
            "strong" -> StatusCatalogTable.RESIST_STRONG
            "mild" -> StatusCatalogTable.RESIST_MILD
            else -> StatusCatalogTable.RESIST_NORMAL
        }
        val potency = max(t.potPct * mult, t.potPct * StatusCatalogTable.LANDING_MIN_POTENCY_FRACTION)
        val turns = max(
            StatusCatalogTable.LANDING_MIN_TURNS,
            (t.turns * mult).roundToInt().coerceAtLeast(StatusCatalogTable.LANDING_MIN_TURNS)
        )
        return StatusInstance(
            status = id,
            turnsLeft = turns,
            potPct = potency,
            stacks = 1,
            maxStacks = max(1, if (mult < 1f) (t.maxStacks * mult).roundToInt().coerceAtLeast(1) else t.maxStacks),
            fromHit = fromHit
        )
    }

    /** Refresh-merge: the stronger of existing vs incoming stays; the other is discarded. */
    fun merge(existing: List<StatusInstance>, incoming: StatusInstance?): List<StatusInstance> {
        if (incoming == null) return existing
        val prior = existing.firstOrNull { it.status == incoming.status }
        val better = if (prior == null) incoming
        else if (incoming.potPct * incoming.turnsLeft >= prior.potPct * prior.turnsLeft) incoming else prior
        return existing.filterNot { it.status == incoming.status } + better
    }

    fun has(statuses: List<StatusInstance>, id: StatusId): Boolean = statuses.any { it.status == id }

    /**
     * ATB speed multiplier from all active statuses. Immobility is a deep crawl, never a
     * hard 0: statuses only tick at the actor's turn, so a frozen gauge would deadlock.
     */
    fun speedMult(statuses: List<StatusInstance>): Float {
        var m = 1f
        for (s in statuses) m *= when (s.status) {
            StatusId.CHILL -> (1f - s.potPct * 2f).coerceIn(0.4f, 0.8f)
            StatusId.ROOT -> (1f - s.potPct).coerceIn(0.2f, 0.65f)
            StatusId.OVERLOAD -> 0.2f
            StatusId.FREEZE -> 0.15f
            StatusId.BLESS -> 1.25f
            else -> 1f
        }
        return m.coerceAtLeast(0f)
    }

    fun isStunned(statuses: List<StatusInstance>): Boolean =
        statuses.any { it.status == StatusId.OVERLOAD || it.status == StatusId.FREEZE }

    /** Defense multiplier on a statused target (corrode lowers, bless raises). */
    fun defenseMult(statuses: List<StatusInstance>): Float {
        var m = 1f
        for (s in statuses) m *= when (s.status) {
            StatusId.CORRODE -> (1f - s.potPct).coerceIn(0.6f, 1f)
            StatusId.BLESS -> 1.2f
            else -> 1f
        }
        return m
    }

    fun attackDebuffMult(statuses: List<StatusInstance>): Float {
        val w = statuses.firstOrNull { it.status == StatusId.WEAKEN } ?: return 1f
        return (1f - w.potPct).coerceIn(0.5f, 1f)
    }

    fun isWeakened(statuses: List<StatusInstance>): Boolean = has(statuses, StatusId.WEAKEN)

    /** Per-turn DoT tick (escalating stacks). Returns damage dealt; mutates copies. */
    fun tickDot(s: StatusInstance): Pair<Int, StatusInstance> {
        val dmg = max(1, (s.potPct * s.fromHit * s.stacks).toInt())
        val next = s.copy(turnsLeft = s.turnsLeft - 1, stacks = (s.stacks + 1).coerceAtMost(s.maxStacks))
        return dmg to next
    }

    /** Advance all statuses one actor-turn: tick DoTs, decrement durations, drop expired. */
    fun advanceTurn(statuses: List<StatusInstance>): Pair<Int, List<StatusInstance>> {
        var dotDamage = 0
        val updated = statuses.mapNotNull { s ->
            if (s.isDot) {
                val (dmg, next) = tickDot(s)
                dotDamage += dmg
                if (next.turnsLeft > 0) next else null
            } else {
                val left = s.turnsLeft - 1
                if (left > 0) s.copy(turnsLeft = left) else null
            }
        }
        return dotDamage to updated
    }

    fun shortName(id: StatusId): String = when (id) {
        StatusId.BURN -> "burning"
        StatusId.CHILL -> "chilled"
        StatusId.FREEZE -> "frozen"
        StatusId.ROOT -> "entangled"
        StatusId.OVERLOAD -> "overloaded"
        StatusId.POISON -> "poisoned"
        StatusId.BLEED -> "bleeding"
        StatusId.CORRODE -> "corroded"
        StatusId.WEAKEN -> "weakened"
        StatusId.BLESS -> "blessed"
        StatusId.GUARD -> "guarding"
    }
}
