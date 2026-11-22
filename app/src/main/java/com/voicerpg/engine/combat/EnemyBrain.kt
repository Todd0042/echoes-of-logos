package com.voicerpg.engine.combat

import kotlin.random.Random

/**
 * Enemy move selection: weighted random over active moveset, gated by per-move
 * cooldowns and by boss HP phase unlocks (replaces the old "one basic attack" AI).
 */
object EnemyBrain {

    fun availableMoves(moveset: Moveset, hpRatio: Float, cooldowns: Map<String, Int>): List<EnemyMove> {
        val phaseMoves = moveset.phases.filter { hpRatio <= it.hpBelow }.flatMap { it.addMoves }
        val pool = (moveset.moves + phaseMoves).distinctBy { it.name }
        val usable = pool.filter { (cooldowns[it.name] ?: 0) <= 0 }
        return usable.ifEmpty { pool.filter { it.kind == "BASIC" }.ifEmpty { listOf(MovesetTable.FALLBACK_BASIC) } }
    }

    fun pickMove(
        moveset: Moveset,
        hpRatio: Float,
        cooldowns: Map<String, Int>,
        random: Random = Random.Default
    ): EnemyMove {
        val usable = availableMoves(moveset, hpRatio, cooldowns)
        val total = usable.sumOf { it.weight.coerceAtLeast(1).toLong() }
        if (total <= 0L) return usable.first()
        var roll = (random.nextLong(total) + 1)
        for (m in usable) {
            roll -= m.weight.coerceAtLeast(1)
            if (roll <= 0L) return m
        }
        return usable.last()
    }

    /** After an enemy acts, tick down cooldowns then set the used move's cooldown. */
    fun advanceCooldowns(cooldowns: Map<String, Int>, usedMove: EnemyMove): Map<String, Int> {
        val decayed = cooldowns.mapValues { (_, c) -> (c - 1).coerceAtLeast(0) }.toMutableMap()
        if (usedMove.cooldownTurns > 0) decayed[usedMove.name] = usedMove.cooldownTurns
        return decayed
    }

    fun phaseAnnouncement(moveset: Moveset, previousHpRatio: Float, hpRatio: Float): PhaseRule? =
        moveset.phases.firstOrNull { previousHpRatio > it.hpBelow && hpRatio <= it.hpBelow }
}
