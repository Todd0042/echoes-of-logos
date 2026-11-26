package com.voicerpg.engine.combat

import com.voicerpg.engine.audio.CombatLines
import com.voicerpg.engine.audio.CombatSpeech
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.ResonanceTier
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract test (the game's own content): combat waits for every line it speaks, so every line stays short. Each
 * line is filled in with the game's real names, spells and moves (the longest cases: a Transcendental cast with every
 * tag, a five-digit hit that defeats, a status) and must fit its kind's word limit (CombatLines.WORD_LIMITS).
 * A spell whose name is too long to call out gives a short `calloutName` in spells.json. Taunts and cut-in quotes
 * are the game's own flavour lines and aren't checked.
 */
class CombatLinesLengthTest {

    @Test
    fun `every combat line stays within its word limit with this game's names`() {
        val enemies = GameContent.enemyTemplates.values.map { com.voicerpg.engine.audio.CombatLines.shortName(it.name, it.shortName) }
        val heroes = GameContent.characters.values.filter { it.combat != null }.map { CombatLines.shortName(it.speaker.name) } + "Aethelgard"
        val spells = GameContent.spells.values.map { it.calloutName.ifBlank { it.name } }
        val moves = GameContent.enemyMovesets.values.flatMap { m -> m.moves.map { it.name } }.distinct()
        val longestEnemy = enemies.maxByOrNull { it.length } ?: "Foe"
        val longestHero = heroes.maxByOrNull { it.length } ?: "Hero"
        val status = "Poisoned"

        val lines = mutableListOf<Pair<CombatSpeech, String>>()
        spells.forEach { lines += CombatSpeech.CALLOUT to CombatLines.callout(it, ResonanceTier.TRANSCENDENTAL) }
        lines += CombatSpeech.CALLOUT to CombatLines.tags(weak = true, resisted = false, broken = true, streak = 12)
        enemies.forEach { e ->
            lines += CombatSpeech.REPORT to CombatLines.damage(listOf(e to 99_999), listOf(e))
            lines += CombatSpeech.ESSENTIAL to CombatLines.line("phase", "enemy" to e)
            lines += CombatSpeech.ESSENTIAL to CombatLines.line("turn_target", "hero" to longestHero, "target" to e)
        }
        moves.forEach { m ->
            lines += CombatSpeech.REPORT to CombatLines.line("enemy_move", "enemy" to longestEnemy, "move" to m)
            lines += CombatSpeech.ESSENTIAL to CombatLines.line("ult_charge", "enemy" to longestEnemy, "move" to m)
        }
        heroes.forEach { h ->
            lines += CombatSpeech.REPORT to CombatLines.enemyHit(h, 99_999, status)
            lines += CombatSpeech.ESSENTIAL to CombatLines.line("fallen", "target" to h)
            lines += CombatSpeech.REPORT to CombatLines.heals(listOf(h to 9_999))
            lines += CombatSpeech.REPORT to CombatLines.line("tick", "target" to h, "amount" to 999)
        }
        lines += CombatSpeech.ESSENTIAL to CombatLines.line("reinforcements", "n" to 3)
        lines += CombatSpeech.FEEDBACK to CombatLines.line("no_mana_attune")

        val tooLong = lines.filter { (kind, text) -> CombatLines.words(text) > CombatLines.WORD_LIMITS.getValue(kind) }
            .distinct().map { (kind, text) -> "$kind (${CombatLines.words(text)} words, max ${CombatLines.WORD_LIMITS.getValue(kind)}): $text" }
        assertTrue("Combat lines too long to wait for:\n  " + tooLong.joinToString("\n  "), tooLong.isEmpty())
    }
}
