package com.voicerpg.engine.ui.combat

import com.voicerpg.engine.content.CharacterRole
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.PartyMember
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract test (the game's own content): every encounter's enemy group and every party the game can field
 * stand in formation on the battle stage with their real sprites (sheets with their `head` slices, else text
 * grids) at phone portrait (normal and expanded console), landscape and tablet sizes: every head fully visible with
 * any fighter acting, feet on the floor band, nobody off the arena, the active fighter on top, nobody shrunk. The
 * one exception is the last resort on the smallest arena (portrait with the console expanded): a big group or a
 * boss there may shrink a step, never below [BattleFormation.MIN_SCALE]; those cases are listed in the output.
 */
class BattleFormationContentTest {

    private val densities = listOf(2.0f, 2.75f, 3.5f)

    /** The smallest arena, where the last-resort shrink is allowed. */
    private val smallest = FormationOracle.ARENAS.minBy { it.first * it.second }

    /** The parties a game fields: each encounter's own, and its roster joining one by one (up to six). */
    private fun parties(): List<List<PartyMember>> {
        val roster = GameContent.characters.values
            .sortedBy { if (it.role == CharacterRole.HERO) 0 else 1 }
            .mapNotNull { it.toPartyMember() }
        val grown = (1..minOf(6, roster.size)).map { roster.take(it) }
        val fixed = GameContent.encounters.values.mapNotNull { it.initialParty }
        return (grown + fixed).distinctBy { p -> p.map { it.id } }
    }

    @Test
    fun `every party and enemy group stands in formation, full size, heads visible, at every arena size`() {
        val failures = mutableListOf<String>()
        val shrunk = mutableListOf<String>()
        var checked = 0
        fun judge(what: String, size: Pair<Float, Float>, f: BattleFormation.Formation, bad: List<String>) {
            val line = "$what at ${size.first}x${size.second} dp: fits=${f.fits} scale=${f.scale} ${bad.take(3)}"
            when {
                !f.fits || bad.isNotEmpty() || f.scale < BattleFormation.MIN_SCALE -> failures += line
                f.scale < 1f && size == smallest -> shrunk += line
                f.scale < 1f -> failures += line
            }
        }
        for (density in densities) for ((w, h) in FormationOracle.ARENAS) {
            val partyStage = FormationOracle.stage(w, h, density, facingRight = true)
            for (party in parties()) {
                val figures = party.map { BattleFormation.figureOf(it, density) }
                val front = BattleFormation.partyFrontOrder(party)
                for (active in listOf<String?>(null) + party.map { it.id }) {
                    val f = BattleFormation.place(figures, partyStage, active, front)
                    checked++
                    judge("party ${party.map { it.id }} active $active @${density}x", w to h, f, FormationOracle.violations(figures, f, partyStage, active))
                }
            }
            val enemyStage = FormationOracle.stage(w, h, density, facingRight = false)
            for (encounter in GameContent.encounters.values) {
                val enemies = encounter.enemies
                if (enemies.isEmpty()) continue
                val figures = enemies.map { BattleFormation.figureOf(it, density) }
                val f = BattleFormation.place(figures, enemyStage, activeId = null, frontOrder = BattleFormation.enemyFrontOrder(enemies))
                checked++
                judge("encounter ${encounter.id} (${enemies.size}) @${density}x", w to h, f, FormationOracle.violations(figures, f, enemyStage, null))
            }
        }
        println("BattleFormationContentTest: $checked formations checked, ${failures.size} failing, ${shrunk.size} shrunk (last resort)")
        shrunk.forEach { println("  last resort: $it") }
        assertTrue("${failures.size} of $checked formations break the stage contract:\n${failures.take(40).joinToString("\n")}", failures.isEmpty())
    }
}
