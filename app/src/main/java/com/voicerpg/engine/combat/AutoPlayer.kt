package com.voicerpg.engine.combat

import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.Spell

/**
 * How an automatically played hero chooses (Pure Story Mode, and the battle simulator).
 *
 *  - [FASTEST]: Pure Story Mode. The most damage this turn (known weaknesses, area spells on groups); a heal only
 *    to stop a fighter falling.
 *  - [SENSIBLE]: a player who pays attention. Reads known weaknesses and groups, heals and restores mana when it
 *    matters, otherwise attacks. The difficulty targets are measured with this player.
 *  - [SPAM]: a player who always says their highest-power spell. Fights should be clearly worse for them.
 */
enum class AutoPolicy { FASTEST, SENSIBLE, SPAM }

object AutoPlayer {

    /** What to cast and the words to say it with ("Fireball Orc", "Mending Word Lyra", "Shockwave all"). */
    data class Choice(val spell: Spell, val chant: String)

    /**
     * The [hero]'s move. [knownWeak] gives the schools an enemy is known to be weak to (the bestiary, learned by
     * hitting); [usesMana] is whether the game has mana at all.
     */
    fun choose(
        policy: AutoPolicy,
        hero: PartyMember,
        party: List<PartyMember>,
        enemies: List<Enemy>,
        knownWeak: (Enemy) -> Set<String>,
        usesMana: Boolean
    ): Choice? {
        val foes = enemies.filter { it.isAlive }
        val allies = party.filter { it.isAlive && !it.isGhost }
        val affordable = hero.spells.filter { !usesMana || it.mpCost <= hero.currentMp }
        val heals = affordable.filter { it.isHeal }
        val restores = affordable.filter { it.manaRestorePct > 0f && !it.isHeal }
        val attacks = affordable.filter { it.basePower > 0 && !it.isHeal && !it.isGuard && it.manaRestorePct == 0f && !it.cleansesDebuffs }
        val canAttack = hero.canAttack && !hero.isGhost && attacks.isNotEmpty()
        val lowest = allies.minByOrNull { it.hpRatio }

        fun mult(spell: Spell, enemy: Enemy): Float = if (spell.school.name in knownWeak(enemy)) 2.0f else 1.0f
        fun value(spell: Spell): Double =
            if (spell.hitsAll) foes.sumOf { (spell.basePower * mult(spell, it)).toDouble() }
            else foes.maxOfOrNull { (spell.basePower * mult(spell, it)).toDouble() } ?: 0.0
        fun bestTarget(spell: Spell): Enemy? =
            foes.maxWithOrNull(compareBy<Enemy> { mult(spell, it) }.thenBy { -it.currentHp })
        fun attack(spell: Spell): Choice =
            if (spell.hitsAll) Choice(spell, "${spell.name} all")
            else Choice(spell, bestTarget(spell)?.let { "${spell.name} ${it.name}" } ?: spell.name)
        fun healChoice(threshold: Float): Choice? {
            val wounded = allies.filter { it.hpRatio < threshold }
            if (wounded.isEmpty() || heals.isEmpty()) return null
            val party = heals.filter { it.hitsAll }
            val single = heals.filterNot { it.hitsAll }
            fun strength(s: Spell) = if (s.healPctOfMax > 0f) s.healPctOfMax * 500f else s.basePower.toFloat()
            return if (wounded.size >= 2 && party.isNotEmpty()) party.maxByOrNull(::strength)!!.let { Choice(it, "${it.name} all") }
            else (single.maxByOrNull(::strength) ?: party.maxByOrNull(::strength))?.let { s ->
                Choice(s, if (s.hitsAll) "${s.name} all" else "${s.name} ${wounded.minByOrNull { it.hpRatio }!!.name}")
            }
        }
        fun restoreChoice(): Choice? {
            if (!usesMana || restores.isEmpty()) return null
            val cheapest = hero.spells.filter { it.basePower > 0 && !it.isHeal }.minOfOrNull { it.mpCost } ?: 0
            if (hero.currentMp >= cheapest) return null
            val s = restores.first()
            return Choice(s, if (s.hitsAll) "${s.name} all" else s.name)
        }

        // A finishing blow: when one cast (at a middling x1.5) can end the fight, attack instead of healing.
        val finisher = attacks.filter { s ->
            val dmg = if (s.hitsAll) foes.map { f -> s.basePower * mult(s, f) * 1.5f } else listOf(s.basePower * (foes.maxOfOrNull { mult(s, it) } ?: 1f) * 1.5f)
            if (s.hitsAll) foes.zip(dmg).all { (f, d) -> d >= f.currentHp } else foes.size == 1 && dmg.first() >= foes.first().currentHp
        }.maxByOrNull(::value)
        val solo = allies.size <= 1

        if (!canAttack) {
            // A support fighter: heal whoever needs it, otherwise any useful non-attack.
            return healChoice(0.7f)
                ?: restoreChoice()
                ?: affordable.firstOrNull { it.isGuard || it.cleansesDebuffs }?.let { Choice(it, "${it.name} all") }
                ?: affordable.firstOrNull()?.let { Choice(it, it.name) }
        }
        return when (policy) {
            AutoPolicy.FASTEST ->
                finisher?.let(::attack)
                    ?: (if ((lowest?.hpRatio ?: 1f) < (if (solo) 0.35f else 0.25f)) healChoice(if (solo) 0.35f else 0.25f) else null)
                    ?: attacks.maxByOrNull(::value)?.let(::attack)
                    ?: restoreChoice()
            AutoPolicy.SENSIBLE ->
                finisher?.let(::attack)
                    ?: healChoice(0.4f)
                    ?: restoreChoice()
                    ?: attacks.maxByOrNull(::value)?.let(::attack)
            AutoPolicy.SPAM ->
                restoreChoice()
                    ?: attacks.maxByOrNull { it.basePower }?.let { s ->
                        if (s.hitsAll) Choice(s, "${s.name} all") else Choice(s, foes.firstOrNull()?.let { "${s.name} ${it.name}" } ?: s.name)
                    }
        }
    }
}
