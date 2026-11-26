package com.voicerpg.engine

import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Hub gating with the engine's real rule (StoryViewModel.isChoiceLocked), on a small story added to the template
 * pack. In a hub (a node with `hubCompletion`) the dialogue choices (a completion flag, staying in the scene, no
 * battle) are open from the start; the choices that move the story on (a battle, a new scene, or no completion flag
 * at all) wait until every dialogue choice is done. A way on is never one of the dialogue choices itself, so it
 * can't wait on itself: the camp pattern (an entry with "sit by the fire" and "rest and move on") is open at once.
 */
class HubChoiceGatingTest {

    private lateinit var pack: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var vm: StoryViewModel

    @Before
    fun setUp() {
        pack = Files.createTempDirectory("hub-pack").toFile()
        TemplateFixture.dir().copyRecursively(pack)
        val file = File(pack, "story/nodes.json")
        val json = com.google.gson.JsonParser.parseString(file.readText()).asJsonObject
        val extra = com.google.gson.JsonParser.parseString(NODES).asJsonArray
        extra.forEach { json.getAsJsonArray("nodes").add(it) }
        file.writeText(json.toString())
        GameContent.reset()
        GameContent.initializeFromDirectory(pack)
        vm = StoryViewModel(SpeechManager(), CombatNarrator(), scopeOverride = scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        GameContent.reset()
        pack.deleteRecursively()
    }

    private fun node(id: String) = GameContent.nodes.getValue(id)
    private fun locked(nodeId: String, choiceId: String, vararg flags: String) =
        node(nodeId).let { n -> vm.isChoiceLocked(n.choices.first { it.id == choiceId }, n, flags.associateWith { true }) }

    @Test
    fun `a hub opens its ways on only once every dialogue choice is done`() {
        assertFalse(locked("qq_hub", "talk_a"))
        assertFalse(locked("qq_hub", "talk_b"))
        assertTrue("the battle waits", locked("qq_hub", "fight"))
        assertTrue("so does leaving the scene", locked("qq_hub", "leave"))
        assertTrue("one talk isn't enough", locked("qq_hub", "fight", "flag_a"))
        assertFalse(locked("qq_hub", "fight", "flag_a", "flag_b"))
        assertFalse(locked("qq_hub", "leave", "flag_a", "flag_b"))
    }

    @Test
    fun `a camp entry with 'sit by the fire' and 'rest and move on' is open at once`() {
        // The Echoes camp pattern that locked both choices: "rest" has a completion flag AND leaves the scene.
        assertFalse("sit by the fire", locked("qq_camp", "sit"))
        assertFalse("rest and move on", locked("qq_camp", "rest"))
    }

    @Test
    fun `a skip-ahead with its own flag waits for the talks, not for itself`() {
        assertFalse(locked("qq_skip_hub", "talk_a"))
        assertTrue("before the talk", locked("qq_skip_hub", "skip"))
        assertFalse("after the talk the skip opens (it never waits on its own flag)", locked("qq_skip_hub", "skip", "flag_a"))
    }

    private companion object {
        val NODES = """[
          {"id":"qq_hub","speakerId":"narrator","side":"CENTER_NARRATOR","text":"Hub.",
           "hubCompletion":{"requiredFlags":["flag_a","flag_b"],"redirectToNodeId":"qq_done"},
           "choices":[
             {"id":"talk_a","text":"Talk A","voiceKeywords":["a"],"nextNodeId":"qq_talk_a","completionFlag":"flag_a"},
             {"id":"talk_b","text":"Talk B","voiceKeywords":["b"],"nextNodeId":"qq_talk_b","completionFlag":"flag_b"},
             {"id":"fight","text":"Fight","voiceKeywords":["fight"],"nextNodeId":"qq_battle","completionFlag":"flag_fight"},
             {"id":"leave","text":"Leave","voiceKeywords":["leave"],"nextNodeId":"qq_next_scene"}]},
          {"id":"qq_camp","speakerId":"narrator","side":"CENTER_NARRATOR","text":"Camp.",
           "hubCompletion":{"requiredFlags":["flag_a","flag_b"],"redirectToNodeId":"qq_done"},
           "choices":[
             {"id":"sit","text":"Sit by the fire","voiceKeywords":["sit"],"nextNodeId":"qq_hub"},
             {"id":"rest","text":"Rest and move on","voiceKeywords":["rest"],"nextNodeId":"qq_next_scene","completionFlag":"flag_rest"}]},
          {"id":"qq_skip_hub","speakerId":"narrator","side":"CENTER_NARRATOR","text":"Hub with a skip.",
           "hubCompletion":{"requiredFlags":["flag_a"],"redirectToNodeId":"qq_done"},
           "choices":[
             {"id":"talk_a","text":"Talk A","voiceKeywords":["a"],"nextNodeId":"qq_talk_a","completionFlag":"flag_a"},
             {"id":"skip","text":"Skip ahead","voiceKeywords":["skip"],"nextNodeId":"qq_next_scene","completionFlag":"flag_skip"}]},
          {"id":"qq_talk_a","speakerId":"narrator","side":"CENTER_NARRATOR","text":"A.","nextNodeId":"qq_hub"},
          {"id":"qq_talk_b","speakerId":"narrator","side":"CENTER_NARRATOR","text":"B.","nextNodeId":"qq_hub"},
          {"id":"qq_battle","speakerId":"narrator","side":"CENTER_NARRATOR","text":"Battle.","triggerBattleEncounterId":"template_solo_battle","nextNodeId":"qq_done"},
          {"id":"qq_next_scene","speakerId":"narrator","side":"CENTER_NARRATOR","text":"Elsewhere.","changeSceneId":"scene_proving_grounds","nextNodeId":"qq_done"},
          {"id":"qq_done","speakerId":"narrator","side":"CENTER_NARRATOR","text":"Done."}
        ]"""
    }
}
