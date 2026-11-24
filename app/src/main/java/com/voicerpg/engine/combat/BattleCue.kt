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

    /**
     * The active hero is charging a chant: while the microphone hears them ([typed] false: the level
     * follows the voice through [ChargeLevel]), or a short simulated charge for a typed chant.
     */
    data class ChargeStart(val casterId: String, val typed: Boolean) : BattleCue

    /** Live voice level 0..1 while charging. */
    data class ChargeLevel(val casterId: String, val level: Float) : BattleCue

    data class ChargeEnd(val casterId: String) : BattleCue

    /** A hero's voice was scored; revealed together with the [Impact] it powers. */
    data class ResonanceReveal(val casterId: String, val resonance: ResonanceResult, val showBanner: Boolean) : BattleCue

    /** An enemy's gauge reached 75%: it picked its next move and shows its [intent]. */
    data class Telegraph(val enemyId: String, val moveName: String, val intent: Intent, val icon: String = intent.icon) : BattleCue

    /** A boss spends this turn charging its ultimate; [turnsLeft] more charge turns before it fires. */
    data class UltimateCharge(val enemyId: String, val moveName: String, val turnsLeft: Int, val totalTurns: Int) : BattleCue

    /**
     * An enemy uses its move: it winds up for [windupMs] (leaning in, glowing in its school's colour),
     * then attacks. Buffs and summons hold the stage for a moment instead.
     */
    data class EnemyMove(
        val enemyId: String,
        val moveName: String,
        val kind: String,
        val isBossUltimate: Boolean,
        val school: SpellSchool = SpellSchool.PHYSICAL,
        val windupMs: Long = 0
    ) : BattleCue

    /**
     * A portrait panel slides across ([portrait] asset, or the sprite when null) in [color] with an optional
     * [quote], while the arena slows behind it: a hero's MYTHIC+ cast, or a boss ultimate.
     */
    data class CutIn(
        val characterId: String,
        val name: String,
        val tier: ResonanceTier,
        val portrait: String?,
        val color: androidx.compose.ui.graphics.Color?,
        val quote: String?,
        val isEnemy: Boolean
    ) : BattleCue

    /** A named move announced on screen, in the game's title-card style. */
    data class TitleCard(val text: String, val fromParty: Boolean) : BattleCue

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
        val shake: Float = 0f,
        /** A damage-over-time tick: a number, no freeze or knockback. */
        val isTick: Boolean = false
    ) : BattleCue

    data class Hit(val targetId: String, val amount: Int, val affinity: Float = 1f, val kind: HitKind = HitKind.DAMAGE)

    enum class HitKind { DAMAGE, HEAL, MANA }

    /**
     * A bold chant rocked an enemy: its gauge was pushed back. [interruptedMove] names the boss ultimate it
     * broke, if any.
     */
    data class Stagger(val enemyId: String, val interruptedMove: String?) : BattleCue

    /** Consecutive strong casts: [count] ≥ 2 while it runs, 0 when it ends. Looks and sound only. */
    data class Streak(val count: Int) : BattleCue

    /** An enemy shouts a line (enemies.json `taunts`) as it telegraphs. */
    data class Taunt(val enemyId: String, val line: String) : BattleCue

    /** A combatant fell (announced right after the [Impact] that felled it). */
    data class Death(val combatantId: String, val isEnemy: Boolean) : BattleCue

    /** A party member braced instead of casting. */
    data class Guard(val memberId: String) : BattleCue

    /** Enemies joined mid-fight. */
    data class Reinforcements(val enemyIds: List<String>) : BattleCue

    /** The acting combatant's turn is over; the next one may start after its hold. */
    data class TurnEnd(val actorId: String, val fromParty: Boolean) : BattleCue

    /** The battle music's intensity layer comes in ([on]) or goes out. */
    data class MusicIntensity(val on: Boolean) : BattleCue

    data class BattleEnd(val victory: Boolean) : BattleCue
}
