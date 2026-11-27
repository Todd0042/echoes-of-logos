package com.voicerpg.engine.balance

import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.story.StoryRouteWalker

/**
 * The balance targets of the combat progression plan (E10) and how a story fight is classified, shared by the
 * contract (BalanceContractTest) and the tuner (BalanceTunerTest).
 */
object BalanceTargets {

    /** Rounds to win and safety margin (rounds to wipe / rounds to win) for the sensible player at the balance point. */
    enum class Kind(val label: String, val rounds: IntRange, val margin: ClosedFloatingPointRange<Float>) {
        EARLY("early", 2..5, 2.0f..4.5f),
        MID("mid", 3..6, 1.4f..3.0f),
        LATE("late", 3..7, 1.15f..2.2f),
        BOSS_MID("boss", 5..11, 1.1f..2.0f),
        BOSS_LATE("late boss", 5..11, 1.05f..1.6f),
        FINALE("finale", 9..15, 1.0f..1.4f);

        /** The middle of the ranges: what the tuner aims for. */
        val targetRounds: Float get() = (rounds.first + rounds.last) / 2f
        val targetMargin: Float get() = (margin.start + margin.endInclusive) / 2f
    }

    /** A story fight on a route, ready to simulate. */
    data class RouteFight(val route: String, val encounterId: String, val kind: Kind, val setup: BattleSimulator.FightSetup)

    /** Every story fight on every route, classified by where it falls on its route (early/mid/late, boss, finale). */
    fun routeFights(): List<RouteFight> = StoryRouteWalker.routes().flatMap { route ->
        val battles = StoryRouteWalker.battlesOn(route).filter { GameContent.encounterById(it.encounterId) != null }
        val lastBoss = battles.lastOrNull { b -> GameContent.encounterById(b.encounterId)!!.enemies.any { it.isBoss } }
        battles.map { b ->
            val encounter = GameContent.encounterById(b.encounterId)!!
            val isBoss = encounter.enemies.any { it.isBoss }
            val kind = when {
                b == lastBoss -> Kind.FINALE
                isBoss && b.progress >= 0.66f -> Kind.BOSS_LATE
                isBoss -> Kind.BOSS_MID
                b.progress < 0.33f -> Kind.EARLY
                b.progress < 0.66f -> Kind.MID
                else -> Kind.LATE
            }
            RouteFight(route.label, b.encounterId, kind, BattleSimulator.FightSetup(encounter, b.player, b.flags, b.partyStats))
        }
    }
}
