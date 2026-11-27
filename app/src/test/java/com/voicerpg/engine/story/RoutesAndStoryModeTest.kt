package com.voicerpg.engine.story

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.combat.CombatTiming
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.EncounterDefinition
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.viewmodel.CombatViewModel
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * E8 (route-locked checkpoints and in-memory story replay on warp) and
 * E9 (Pure Story Mode picks highest expected damage / known weakness / AoE and heals only near death).
 */
class RoutesAndStoryModeTest {

    @get:Rule val templateFixture = TemplateFixture { dir -> addRouteGraph(dir) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val combatVms = mutableListOf<CombatViewModel>()

    @After
    fun tearDown() {
        combatVms.forEach { it.cleanup() }
        scope.cancel(); vmScope.cancel()
    }

    @Test
    fun `warp replays the selected route in memory and never visits another route`() {
        val story = StoryViewModel(SpeechManager(), CombatNarrator(), scopeOverride = scope).apply {
            startNewGame(PlayerCustomization(name = "Hero", heroClassId = "elementalist"))
        }

        val choices = StoryRouteWalker.availableRouteChoices()
        assertEquals(2, choices.size)
        assertTrue(StoryRouteWalker.needsRouteChoiceBeforeWarp("n_alpha_2", emptyMap()))

        val alphaTargets = StoryRouteWalker.checkpointsFor(story.state.value.player, emptyMap(), routeFlagOverride = "role_alpha")
        assertTrue("alpha checkpoints include Alpha End: $alphaTargets", alphaTargets.any { it.introNodeId == "n_alpha_2" })
        assertFalse("alpha never lists Beta", alphaTargets.any { it.introNodeId == "n_beta_2" })

        story.debugWarpToChapter("n_alpha_2", routeFlagOverride = "role_alpha")
        val s = story.state.value
        assertEquals("n_alpha_2", s.currentNode.id)
        assertTrue("route flag set", s.narrativeFlags["role_alpha"] == true)
        assertTrue("alpha milestone flag set", s.narrativeFlags["alpha_joined"] == true)
        assertFalse("beta flag never set", s.narrativeFlags["role_beta"] == true)
        assertTrue("alpha encounter marked defeated", s.defeatedEncounters.contains("template_skirmish"))
    }

    @Test
    fun `pure story mode picks AoE on groups and uses x1_8 resonance`() {
        val narrator = CombatNarrator().apply { lineSinkForTesting = { _, _, done -> done() } }
        val vm = CombatViewModel(
            SpeechManager(),
            combatNarrator = narrator,
            resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = CombatTiming.INSTANT,
            random = kotlin.random.Random(4),
            scopeOverride = vmScope
        ).also { combatVms += it }
        vm.applyPlayerCustomization(PlayerCustomization(name = "Hero", heroClassId = "elementalist"))
        vm.setPureStoryMode(true)

        val drone = GameContent.enemyTemplates.getValue("training_drone")
        val e1 = drone.toEnemy(instanceId = "d1", nameOverride = "Drone 1").copy(currentHp = 500, maxHp = 500, speed = 0)
        val e2 = drone.toEnemy(instanceId = "d2", nameOverride = "Drone 2").copy(currentHp = 500, maxHp = 500, speed = 0)
        val hero = GameContent.characterById("hero")!!.toPartyMember()!!.let { m ->
            val aoe = GameContent.spellById("chain_lightning")!!
            val single = GameContent.spellById("fireball")!!
            m.copy(currentHp = 200, maxHp = 200, currentMp = 200, maxMp = 200, spells = listOf(single, aoe))
        }
        vm.startEncounter(EncounterDefinition(id = "t_e9", name = "t_e9", environment = BattleEnvironment.FOREST, enemies = listOf(e1, e2), initialParty = listOf(hero)))
        vm.setPlayerInputPhaseForTesting("hero")

        val picked = vm.triggerAutoHeroAction(vm.state.value.party.first())
        assertEquals("chain_lightning", picked?.id)
        val end = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < end && vm.state.value.lastResonance == null) Thread.sleep(10)
        assertEquals(1.8f, vm.state.value.lastResonance?.damageMultiplier ?: 0f, 0.01f)
    }

    companion object {
        fun addRouteGraph(dir: File) {
            fun edit(path: String, f: (JsonObject) -> Unit) {
                val file = File(dir, path)
                val json = JsonParser.parseString(file.readText()).asJsonObject
                f(json); file.writeText(json.toString())
            }
            edit("manifest.json") { m -> m.addProperty("routeFlagPrefix", "role_") }
            edit("story/nodes.json") { d ->
                val nodes = d.getAsJsonArray("nodes")
                nodes.add(JsonObject().apply {
                    addProperty("id", "n_interview"); addProperty("speakerId", "narrator"); addProperty("text", "Pick a route.")
                    add("choices", JsonArray().apply {
                        add(JsonObject().apply { addProperty("id", "c_a"); addProperty("text", "Alpha"); addProperty("completionFlag", "role_alpha"); addProperty("nextNodeId", "n_alpha_1") })
                        add(JsonObject().apply { addProperty("id", "c_b"); addProperty("text", "Beta"); addProperty("completionFlag", "role_beta"); addProperty("nextNodeId", "n_beta_1") })
                    })
                })
                nodes.add(JsonObject().apply {
                    addProperty("id", "n_alpha_1"); addProperty("speakerId", "narrator"); addProperty("text", "Alpha 1")
                    addProperty("setFlagOnEnter", "alpha_joined"); addProperty("triggerBattleEncounterId", "template_skirmish"); addProperty("nextNodeId", "n_alpha_2")
                })
                nodes.add(JsonObject().apply {
                    addProperty("id", "n_alpha_2"); addProperty("speakerId", "narrator"); addProperty("text", "Alpha 2")
                    addProperty("checkpoint", "Alpha End")
                })
                nodes.add(JsonObject().apply {
                    addProperty("id", "n_beta_1"); addProperty("speakerId", "narrator"); addProperty("text", "Beta 1")
                    addProperty("nextNodeId", "n_beta_2")
                })
                nodes.add(JsonObject().apply {
                    addProperty("id", "n_beta_2"); addProperty("speakerId", "narrator"); addProperty("text", "Beta 2")
                    addProperty("checkpoint", "Beta End")
                })
                // Link the template's initial node to n_interview if it has a simple next
                nodes.get(0).asJsonObject.addProperty("nextNodeId", "n_interview")
            }
        }
    }
}
