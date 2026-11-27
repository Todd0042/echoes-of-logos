package com.voicerpg.engine.story

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.voicerpg.engine.audio.CombatLines
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.combat.BattleCue
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Collections

/**
 * Spells come from the story: a fighter knows their starting kit plus every grant whose story flag is set.
 *
 * A playthrough of the template with a class grant, one companion who joins before a milestone (and learns at it,
 * announced) and one who joins after it (and arrives already knowing it, not announced); then a battle where each
 * fighter has exactly the spells the story gave them, and asking for a spell not learned yet is answered without
 * costing the turn.
 */
class SpellsFromTheStoryTest {

    @get:Rule val templateFixture = TemplateFixture { dir -> addStoryProgression(dir) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val combatVms = mutableListOf<CombatViewModel>()

    @After
    fun tearDown() {
        combatVms.forEach { it.cleanup() }
        scope.cancel(); vmScope.cancel()
    }

    private fun story(): StoryViewModel = StoryViewModel(SpeechManager(), CombatNarrator(), scopeOverride = scope).also {
        it.startNewGame(PlayerCustomization(name = "Aethel", heroClassId = "elementalist"))
    }

    private fun StoryViewModel.goTo(nodeId: String) = debugWarpToChapter(nodeId)

    private fun known(story: StoryViewModel, id: String) =
        GameContent.knownSpells(id, story.state.value.player.heroClassId, story.state.value.narrativeFlags).map { it.id }

    @Test
    fun `a companion learns at a milestone (announced) and a late joiner arrives knowing it (not announced)`() {
        val story = story()
        assertEquals("the class's starting kit", listOf("fireball", "attune"), known(story, "hero"))

        story.goTo(JOIN_WARDEN)
        assertTrue("the warden joined", story.state.value.partyStats.any { it.id == "warden" })
        assertEquals(listOf("power_strike"), known(story, "warden"))
        assertTrue("joining announces nothing", story.state.value.spellsLearned.isEmpty())

        story.goTo(MILESTONE)
        assertEquals(listOf("fireball", "attune", "frost_spike"), known(story, "hero"))
        assertEquals(listOf("power_strike", "chain_lightning"), known(story, "warden"))
        val announced = story.state.value.spellsLearned
        assertTrue("hero's new spell is announced: $announced", announced.any { it.contains("Aethel") && it.contains("Frost Spike") })
        assertTrue("warden's new spell is announced: $announced", announced.any { it.contains("Warden") && it.contains("Chain Lightning") })

        story.goTo(JOIN_SCOUT)
        assertEquals("the late joiner arrives with the milestone already passed",
            listOf("power_strike", "flame_spark"), known(story, "scout"))
        assertTrue("arriving with it is not announced: ${story.state.value.spellsLearned}",
            story.state.value.spellsLearned.none { it.contains("Scout") })

        story.goTo(PLAIN_NODE)
        assertTrue("an announcement is shown once", story.state.value.spellsLearned.isEmpty())
    }

    @Test
    fun `in battle each fighter has exactly the spells the story gave them`() {
        val story = story()
        story.goTo(JOIN_WARDEN); story.goTo(MILESTONE)

        val lines = Collections.synchronizedList(mutableListOf<String>())
        val narrator = CombatNarrator().apply { lineSinkForTesting = { _, text, done -> lines += text; done() } }
        val vm = CombatViewModel(SpeechManager(), combatNarrator = narrator, resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = CombatTiming.INSTANT, random = kotlin.random.Random(2), scopeOverride = vmScope).also { combatVms += it }
        val cues = Collections.synchronizedList(mutableListOf<BattleCue>())
        scope.launch(start = CoroutineStart.UNDISPATCHED) { vm.cues.collect { cues += it } }

        vm.applyPlayerCustomization(story.state.value.player, story.state.value.partyStats)
        vm.applyStoryFlags(story.state.value.narrativeFlags)
        vm.applySavedStats(story.state.value.partyStats)
        val drone = GameContent.enemyTemplates.getValue("training_drone").toEnemy(instanceId = "d1", nameOverride = "Drone")
        vm.startEncounter(EncounterDefinition(id = "t", name = "t", environment = BattleEnvironment.FOREST,
            enemies = listOf(drone.copy(currentHp = 90_000, maxHp = 90_000, speed = 0)),
            initialParty = listOfNotNull(GameContent.characterById("hero")?.toPartyMember(), GameContent.characterById("warden")?.toPartyMember())))

        val party = vm.state.value.party
        assertEquals(listOf("fireball", "attune", "frost_spike"), party.first { it.id == "hero" }.spells.map { it.id })
        assertEquals(listOf("power_strike", "chain_lightning"), party.first { it.id == "warden" }.spells.map { it.id })

        // Thunder Cleave is the warden's trial spell, not learned yet and known by nobody in the party: answered,
        // and still the warden's turn. (A spell another fighter knows is routed to them instead.)
        vm.setPlayerInputPhaseForTesting("warden")
        vm.submitTypedChant("thunder cleave")
        val end = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < end && lines.none { it.contains("Thunder") }) Thread.sleep(10)
        assertTrue("told it isn't learned yet: $lines", lines.any { it == CombatLines.line("not_learned", "hero" to "Warden", "spell" to GameContent.spellById("thunder_cleave")!!.name) })
        assertFalse("nothing was cast", cues.any { it is BattleCue.Impact && it.fromParty })
        assertEquals("warden", vm.state.value.activePartyMember?.id)
    }

    companion object {
        const val JOIN_WARDEN = "t_join_warden"
        const val MILESTONE = "t_milestone"
        const val JOIN_SCOUT = "t_join_scout"
        const val PLAIN_NODE = "t_plain"

        /** Adds a class grant, two companions and four story nodes that set their flags to a copy of the template. */
        fun addStoryProgression(dir: File) {
            fun edit(path: String, f: (JsonObject) -> Unit) {
                val file = File(dir, path)
                val json = JsonParser.parseString(file.readText()).asJsonObject
                f(json); file.writeText(json.toString())
            }
            fun grants(vararg pairs: Pair<String, List<String>>) = JsonArray().apply {
                pairs.forEach { (flag, ids) -> add(JsonObject().apply { addProperty("flag", flag); add("spellIds", JsonArray().apply { ids.forEach(::add) }) }) }
            }
            edit("classes/elementalist.json") { c ->
                c.add("starterSpellIds", JsonArray().apply { add("fireball"); add("attune") })
                c.add("spellGrants", grants("t_milestone_passed" to listOf("frost_spike")))
            }
            fun companion(id: String, name: String, joinFlag: String, grantList: JsonArray) = JsonObject().apply {
                addProperty("id", id); addProperty("role", "npc"); addProperty("name", name)
                add("combat", JsonObject().apply {
                    addProperty("maxHp", 300); addProperty("maxMp", 100); addProperty("speed", 0)
                    add("spellIds", JsonArray().apply { add("power_strike") })
                })
                addProperty("joinWhenFlag", joinFlag)
                add("spellGrants", grantList)
            }
            File(dir, "characters/warden.json").writeText(companion("warden", "Warden", "t_warden_joined",
                grants("t_milestone_passed" to listOf("chain_lightning"), "t_warden_trial" to listOf("thunder_cleave"))).toString())
            File(dir, "characters/scout.json").writeText(companion("scout", "Scout", "t_scout_joined",
                grants("t_milestone_passed" to listOf("flame_spark"))).toString())
            edit("story/nodes.json") { d ->
                val nodes = d.getAsJsonArray("nodes")
                listOf(JOIN_WARDEN to "t_warden_joined", MILESTONE to "t_milestone_passed", JOIN_SCOUT to "t_scout_joined", PLAIN_NODE to null).forEach { (id, flag) ->
                    nodes.add(JsonObject().apply {
                        addProperty("id", id); addProperty("speakerId", "narrator"); addProperty("text", "Test node $id.")
                        if (flag != null) addProperty("setFlagOnEnter", flag)
                    })
                }
            }
        }
    }
}
