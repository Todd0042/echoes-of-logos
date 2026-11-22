package com.voicerpg.engine

import org.junit.Rule
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.SpellSchool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEngineTest {

    /** Mechanics test: runs on the engine's frozen template content, whatever game it's in. */
    @get:Rule val templateFixture = TemplateFixture()

    @Test
    fun testTemplateHeroInitialization() {
        val hero = GameContent.heroCharacter!!.toPartyMember()!!
        assertEquals("Template Hero", hero.name)
        assertEquals(240, hero.maxHp)
        assertEquals(140, hero.maxMp)
        assertTrue(hero.isAlive)
        assertEquals(5, hero.spells.size)

        val strike = hero.spells.first { it.id == "power_strike" }
        assertEquals(SpellSchool.PHYSICAL, strike.school)
        assertTrue(strike.aliases.contains("strike"))

        val flame = hero.spells.first { it.id == "flame_spark" }
        assertEquals(SpellSchool.PYROMANCY, flame.school)
        assertTrue(flame.aliases.contains("spark"))

        val mend = hero.spells.first { it.id == "mending_touch" }
        assertTrue(mend.isHeal)
        assertTrue(mend.aliases.contains("heal"))
    }

    @Test
    fun testTemplateEnemySpawningSingleAndSquad() {
        val soloEnemy = GameContent.enemyTemplates.getValue("training_drone").toEnemy(
            instanceId = "drone_test",
            nameOverride = "Test Drone",
            hpOverride = 100
        )
        assertEquals("drone_test", soloEnemy.id)
        assertEquals(100, soloEnemy.maxHp)
        assertTrue(soloEnemy.isAlive)

        // Test spawning a squad of 3 enemies with unique IDs
        val squad = (1..3).map { idx ->
            GameContent.createMinion("combat_drone_$idx", "Combat Drone $idx")
        }
        assertEquals(3, squad.size)
        val ids = squad.map { it.id }.toSet()
        assertEquals(3, ids.size) // IDs must be unique
        assertTrue(squad.all { it.isAlive })
        assertTrue(squad.all { it.name.startsWith("Combat Drone") })
    }

    @Test
    fun testTemplateEncountersConfigured() {
        val solo = GameContent.encounters.getValue("template_solo_battle")
        assertEquals(1, solo.enemies.size)
        assertNotNull(solo.initialParty)
        assertEquals(1, solo.initialParty?.size)

        val trio = GameContent.encounters.getValue("template_trio_battle")
        assertEquals(3, trio.enemies.size)
        val enemyIds = trio.enemies.map { it.id }.toSet()
        assertEquals(3, enemyIds.size)
    }

    @Test
    fun testTemplateStoryGraphIntegrity() {
        val hub = GameContent.nodes.getValue("template_choice_hub")
        assertTrue(hub.choices.size >= 4)

        // Verify choices have voice keywords and valid next nodes
        hub.choices.forEach { choice ->
            assertTrue(choice.voiceKeywords.isNotEmpty())
            assertTrue(GameContent.nodes.containsKey(choice.nextNodeId))
        }

        // Verify choice elimination flags exist on inquiry branches
        val loreChoice = hub.choices.first { it.id == "choice_history" }
        assertNotNull(loreChoice.completionFlag)

        val counselChoice = hub.choices.first { it.id == "choice_counsel" }
        assertNotNull(counselChoice.completionFlag)

        // Verify combat triggers lead to encounters
        val combatNode = GameContent.nodes.getValue("template_trigger_solo_combat")
        assertEquals("template_solo_battle", combatNode.triggerBattleEncounterId)
        assertEquals("template_post_victory", combatNode.nextNodeId)
    }
}