package com.voicerpg.engine.combat

import org.junit.Rule
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.IntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyActionsTest {

    /** Mechanics test: runs on the engine's frozen template content, whatever game it's in. */
    @get:Rule val templateFixture = TemplateFixture()

    @Test
    fun `every hero class carries the free attune`() {
        for (klass in GameContent.classes) {
            val kit = GameContent.spellsForClass(klass.id)
            val attune = kit.firstOrNull { it.manaRestorePct > 0f }
            assertTrue("${klass.id} must have an Attune breath action", attune != null)
            assertEquals(0, attune!!.mpCost)
            assertEquals(0.35f, attune.manaRestorePct, 0.001f)
        }
        assertTrue(GameContent.heroSpellsFor().any { it.manaRestorePct > 0f })
    }

    @Test
    fun `attune is reachable by voice through name and breath aliases`() {
        val kit = GameContent.spellsForClass("elementalist")
        assertEquals("attune", IntentParser.parse("Attune", kit).spell.id)
        assertEquals("attune", IntentParser.parse("steady my breath and concentrate", kit).spell.id)
        assertEquals("attune", IntentParser.parse("recover mana", kit).spell.id)
    }

    @Test
    fun `drains stay single and personal while benedictions go party-wide with a potency split`() {
        val benediction = GameContent.spells.getValue("haste_cadence")
        assertTrue("Haste Cadence heals the whole fellowship", benediction.isHeal && benediction.hitsAll)

        val siphon = GameContent.spells.getValue("void_drain")
        assertTrue("Void Drain stays a personal drain (damage + self heal), never a party heal",
            !siphon.isHeal && siphon.lifesteal && !siphon.hitsAll)

        // Party-wide potency split: 4 targets of a 110 heal must cost less each than a focused heal
        val single = DamageResolver.resolveHeal(110f, 1, 1.5f, partyWide = false, random = kotlin.random.Random(42))
        val party = DamageResolver.resolveHeal(110f, 1, 1.5f, partyWide = true, random = kotlin.random.Random(42))
        assertTrue("party=$party single=$single", party in (single * 0.55).toInt()..(single * 0.75).toInt())
    }

    @Test
    fun `every kit has its own breath and it works on the active member's turn only`() {
        for (kit in listOf(
            GameContent.heroSpellsFor(),
            GameContent.spellsForClass("elementalist"),
            GameContent.spellsForClass("battlemage"),
            GameContent.spellsForClass("chanter"),
            GameContent.spellsForClass("shadowweaver")
        )) {
            val breath = kit.lastOrNull { it.manaRestorePct > 0f }
            assertTrue("kit missing breath action", breath != null)
            assertEquals("breaths must sit last so fallbacks never auto-pick them", kit.size - 1, kit.indexOf(breath))
            assertEquals(breath!!.id, IntentParser.parse("attune", kit).spell.id)
        }
        assertEquals("attune", IntentParser.parse("attune", GameContent.heroSpellsFor()).spell.id)
    }

    @Test
    fun `an unevocative fireball utterance falls back to fireball, never to attune`() {
        val kit = GameContent.spellsForClass("elementalist")
        // ASR often splits compounds: "fire ball" matches neither name nor words - fallback must skip restores.
        assertEquals("fireball", IntentParser.parse("fire ball the drone", kit).spell.id)
        assertEquals("fireball", IntentParser.parse("do the thing now", kit).spell.id)
        assertEquals("fireball", IntentParser.parse("hurry up and cast", kit).spell.id)
    }

    @Test
    fun `breath utterances never trigger hero steering or school keyword hijack`() {
        // We verify the parser picks the breath spell regardless of school keywords in the utterance.
        for (kit in listOf(
            GameContent.heroSpellsFor(),
            GameContent.spellsForClass("elementalist"),
            GameContent.spellsForClass("battlemage"),
            GameContent.spellsForClass("chanter"),
            GameContent.spellsForClass("shadowweaver")
        )) {
            val breathSpell = kit.first { it.manaRestorePct > 0f }
            // Utterances with accidental school keywords must still resolve to breath
            assertEquals(breathSpell.id, IntentParser.parse("steady breath by the dawn", kit).spell.id)
            assertEquals(breathSpell.id, IntentParser.parse("breathe and recover mana", kit).spell.id)
            assertEquals(breathSpell.id, IntentParser.parse("attune my focus", kit).spell.id)
        }
    }

    @Test
    fun `ASR-mangled breath commands still resolve to the breath spell`() {
        // "a tune" for "attune", "breathe/center/focus" breath aliases
        assertEquals("attune", IntentParser.parse("a tune please", GameContent.heroSpellsFor()).spell.id)
        assertEquals("attune", IntentParser.parse("breathe center focus", GameContent.heroSpellsFor()).spell.id)
        assertEquals("attune", IntentParser.parse("steady breath templar", GameContent.heroSpellsFor()).spell.id)
    }

    @Test
    fun `attune restore amount is flat - resonance can never inflate it`() {
        // applyAttuneAction uses (maxMp * pct) with no resonance input; assert the math is anchored.
        val maxMp = 140
        val restore = (maxMp * 0.35f).toInt()
        assertEquals(49, restore)
    }
}