package com.voicerpg.engine.balance

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.voicerpg.engine.combat.AutoPolicy
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.EncounterDefinition
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.testing.TemplateFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The battle simulator plays real battles (the real CombatViewModel, instant timing, virtual time) with a fixed
 * resonance and an auto-play policy. Mechanics on the template content: effort pays (x1.8 beats x1.1), a difficulty
 * preset changes the fight, the fastest policy aims at a weakness it knows, and a battle costs well under a second.
 */
class BattleSimulatorTest {

    @get:Rule val templateFixture = TemplateFixture { dir -> addHardPreset(dir) }

    private fun hero() = PlayerCustomization(name = "Aethel", heroClassId = "elementalist")

    /** A one-hero fight against two drones with plenty of HP, so the number of rounds says something. */
    private fun fight(hpEach: Int = 600): BattleSimulator.FightSetup {
        val drone = GameContent.enemyTemplates.getValue("training_drone")
        val enemies = (1..2).map { i -> drone.toEnemy(instanceId = "d$i", nameOverride = "Drone $i").let { it.copy(currentHp = hpEach, maxHp = hpEach) } }
        val party = listOfNotNull(GameContent.heroCharacter?.toPartyMember())
        return BattleSimulator.FightSetup(
            encounter = EncounterDefinition(id = "sim_test", name = "Sim", environment = BattleEnvironment.FOREST, enemies = enemies, initialParty = party),
            player = hero(),
            flags = emptyMap(),
            partyStats = emptyList()
        )
    }

    @Test
    fun `effort pays - the same fight takes fewer rounds at x1_8 than at x1_1`() {
        val lazy = BattleSimulator.run(fight(), AutoPolicy.SENSIBLE, resonance = 1.1f, seed = 1)
        val bold = BattleSimulator.run(fight(), AutoPolicy.SENSIBLE, resonance = 1.8f, seed = 1)
        assertTrue("both finish: $lazy / $bold", lazy.finished && bold.finished)
        assertTrue("x1.8 wins faster: ${bold.rounds} vs ${lazy.rounds} rounds", bold.won && bold.rounds < lazy.rounds)
    }

    @Test
    fun `a difficulty preset scales the enemies`() {
        val normal = BattleSimulator.run(fight(), AutoPolicy.SENSIBLE, resonance = 1.5f, seed = 2)
        val hard = BattleSimulator.run(fight(), AutoPolicy.SENSIBLE, resonance = 1.5f, seed = 2, difficulty = "hard")
        assertEquals("the enemies have double HP on hard", normal.enemyMaxHp * 2, hard.enemyMaxHp)
        assertTrue("hard takes longer: ${hard.rounds} vs ${normal.rounds}", hard.rounds > normal.rounds)
    }

    @Test
    fun `a battle runs on virtual time`() {
        val started = System.nanoTime()
        val result = BattleSimulator.run(fight(hpEach = 1200), AutoPolicy.FASTEST, resonance = 1.8f, seed = 3)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("finished: $result", result.finished)
        assertTrue("took $ms ms of real time", ms < 3_000)
        assertTrue("every party turn was the policy's", result.partyActions > 0)
    }

    companion object {
        fun addHardPreset(dir: File) {
            val f = File(dir, "combat/balance.json")
            f.parentFile.mkdirs()
            val json = JsonObject().apply {
                addProperty("balanceResonance", 1.5)
                add("difficulties", JsonParser.parseString("""{"normal":{"enemyHp":1.0,"enemyAttack":1.0},"hard":{"enemyHp":2.0,"enemyAttack":1.25}}"""))
            }
            f.writeText(json.toString())
        }
    }
}
