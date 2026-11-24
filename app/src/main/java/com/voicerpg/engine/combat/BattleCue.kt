package com.voicerpg.engine.combat

import com.voicerpg.engine.model.ResonanceResult
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.model.SpellGraphics
import com.voicerpg.engine.model.SpellSchool

/**
 * What combat logic decided, announced in order for the presentation layer to play.
 *
 * `CombatViewModel` decides outcomes and emits cues; `BattleFxDirector` (ui/fx) turns them into
 * projectiles, particles and shake. Logic never draws, and the director never changes game state. Every
 * wait between cues comes from [CombatTiming], so tests can assert the cue order with no rendering and
 * with no real time passing.
 *
 * Cues name combatants by id; the director resolves ids to screen positions itself.
 */
sealed interface BattleCue {

    /** A battle (or a restart) begins: the arena is cleared of leftover effects. */
    data class BattleStart(val encounterId: String?) : BattleCue

    /** A hero's voice was scored. Held on screen before the spell flies ([CombatTiming.holdMs]). */
    data class ResonanceReveal(val casterId: String, val resonance: ResonanceResult, val showBanner: Boolean) : BattleCue

    /** An enemy chose its move. Buffs and summons hold the stage for a moment; attacks follow at once. */
    data class EnemyMove(val enemyId: String, val moveName: String, val kind: String, val isBossUltimate: Boolean) : BattleCue

    /** An enemy crossed a health threshold and changes tactics. */
    data class PhaseShift(val enemyId: String, val note: String) : BattleCue

    /**
     * A spell or move leaves its caster toward [targetIds] and flies for [travelMs] (taken from
     * [CombatTiming.travelMs], so the projectile lands exactly when the [Impact] is announced).
     * [particleCount] is the whole burst; an area release splits it across the targets.
     */
    data class Release(
        val casterId: String,
        val fromParty: Boolean,
        val targetIds: List<String>,
        val school: SpellSchool,
        val style: ReleaseStyle,
        val tier: ResonanceTier = ResonanceTier.BASIC,
        val bonusPercent: Int = 0,
        val particleCount: Int,
        val graphics: SpellGraphics? = null,
        val travelMs: Long
    ) : BattleCue

    enum class ReleaseStyle { PROJECTILE, HEAL, ATTUNE }

    /**
     * A release resolved: every target it touched, at once (an area spell lands on all foes together).
     * [shake] is the content's requested arena shake for this blow; 0 for none.
     */
    data class Impact(
        val sourceId: String,
        val fromParty: Boolean,
        val hits: List<Hit>,
        val tier: ResonanceTier = ResonanceTier.BASIC,
        val shake: Float = 0f
    ) : BattleCue

    data class Hit(val targetId: String, val amount: Int, val affinity: Float = 1f, val kind: HitKind = HitKind.DAMAGE)

    enum class HitKind { DAMAGE, HEAL, MANA }

    /** A combatant fell (announced right after the [Impact] that felled it). */
    data class Death(val combatantId: String, val isEnemy: Boolean) : BattleCue

    /** A party member braced instead of casting. */
    data class Guard(val memberId: String) : BattleCue

    /** Enemies joined mid-fight. */
    data class Reinforcements(val enemyIds: List<String>) : BattleCue

    /** The acting combatant's turn is over; the next one may start after its hold. */
    data class TurnEnd(val actorId: String, val fromParty: Boolean) : BattleCue

    data class BattleEnd(val victory: Boolean) : BattleCue
}
