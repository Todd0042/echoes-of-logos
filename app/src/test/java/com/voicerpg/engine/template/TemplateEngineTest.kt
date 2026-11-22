package com.voicerpg.engine.template

import com.voicerpg.engine.model.SpellCategory
import com.voicerpg.engine.model.SpellSchool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEngineTest {

    @Test
    fun testTemplateHeroInitialization() {
        val hero = TemplateHero.createTemplateAdventurer()
        assertEquals("Adventurer", hero.name)
        assertEquals(120, hero.maxHp)
        assertEquals(50, hero.maxMp)
        assertTrue(hero.isAlive)
        assertEquals(5, hero.spells.size)

        val strike = hero.spells.first { it.id == "template_strike" }
        assertEquals(SpellSchool.PHYSICAL, strike.school)
        assertEquals(SpellCategory.ATTACK, strike.category)
        assertTrue(strike.aliases.contains("strike"))

        val flame = hero.spells.first { it.id == "template_flame_spark" }
        assertEquals(SpellSchool.PYROMANCY, flame.school)
        assertEquals(SpellCategory.ATTACK, flame.category)

        val blizzard = hero.spells.first { it.id == "template_blizzard_burst" }
        assertEquals(SpellCategory.AOE_ATTACK, blizzard.category)
        assertTrue(blizzard.hitsAll)

        val mend = hero.spells.first { it.id == "template_mend" }
        assertEquals(SpellCategory.HEAL, mend.category)
        assertTrue(mend.isHeal)

        val focus = hero.spells.first { it.id == "template_inner_focus" }
        assertEquals(SpellCategory.MANA, focus.category)
        assertEquals(0.35f, focus.manaRestorePct)
    }

    @Test
    fun testTemplateEnemySpawning() {
        val soloEnemy = TemplateEnemy.createTemplateEnemy("drone_test", "Test Drone")
        assertEquals("drone_test", soloEnemy.id)
        assertEquals(100, soloEnemy.maxHp)
        assertTrue(soloEnemy.isAlive)

        val squad = TemplateEnemy.spawnSquad(3, "Combat Drone")
        assertEquals(3, squad.size)
        val ids = squad.map { it.id }.toSet()
        assertEquals(3, ids.size)
        assertTrue(squad.all { it.isAlive })
        assertTrue(squad.all { it.name.startsWith("Combat Drone") })
    }

    @Test
    fun testTemplateEncounters() {
        val solo = TemplateEncounters.TEMPLATE_SOLO_BATTLE
        assertEquals(1, solo.enemies.size)
        assertNotNull(solo.initialParty)
        assertEquals(1, solo.initialParty?.size)

        val trio = TemplateEncounters.TEMPLATE_TRIO_BATTLE
        assertEquals(3, trio.enemies.size)
        assertEquals("drone_vanguard", trio.enemies[0].id)
    }

    @Test
    fun testTemplateStoryGraph() {
        assertEquals("The Proving Grounds", TemplateStory.TEMPLATE_SCENE.name)
        assertEquals(9, TemplateStory.ALL_TEMPLATE_NODES.size)
        val hub = TemplateStory.NODE_CHOICE_HUB
        assertEquals(4, hub.choices.size)
    }
}
