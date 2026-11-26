package com.voicerpg.engine.story

import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A hub option the player has played, whose branch has nothing left to choose, is complete: greyed out, skipped
 * by auto-play, and no longer holding back the hub's move on. The branch can mark itself with its own flag
 * (`setFlagOnEnter`, as Echoes chapter 7 does), with no flag at all (it was visited), or lead into a sub-hub (it's
 * complete once that sub-hub is finished).
 *
 * Played with the real view model through the hub the way Pure Story Mode plays it.
 */
class HubBranchCompletionTest {

    private lateinit var pack: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var vm: StoryViewModel

    @Before
    fun setUp() {
        pack = Files.createTempDirectory("branch-pack").toFile()
        TemplateFixture.dir().copyRecursively(pack)
        val file = File(pack, "story/nodes.json")
        val json = com.google.gson.JsonParser.parseString(file.readText()).asJsonObject
        com.google.gson.JsonParser.parseString(NODES).asJsonArray.forEach { json.getAsJsonArray("nodes").add(it) }
        file.writeText(json.toString())
        GameContent.reset()
        GameContent.initializeFromDirectory(pack)
        vm = StoryViewModel(SpeechManager(), CombatNarrator(), scopeOverride = scope)
        vm.startNewGame(PlayerCustomization(name = "Auto"))
        vm.debugWarpToChapter("qq_hub")
    }

    @After
    fun tearDown() {
        scope.cancel()
        GameContent.reset()
        pack.deleteRecursively()
    }

    private fun hub() = GameContent.nodes.getValue("qq_hub")
    private fun complete(choiceId: String) = vm.isChoiceCompleted(hub().choices.first { it.id == choiceId }, node = hub())

    @Test
    fun `auto-play plays every branch once, marks each complete, and the hub moves on`() {
        assertEquals("qq_hub", vm.state.value.currentNode.id)
        hub().choices.forEach { assertFalse("${it.id} open at first", complete(it.id)) }

        val taken = mutableListOf<String>()
        var steps = 0
        while (vm.state.value.currentNode.id != "qq_onward" && steps++ < 60) {
            val node = vm.state.value.currentNode
            val pick = if (node.choices.isNotEmpty()) vm.pickStoryModeChoice(node) else null
            if (pick != null) {
                if (node.id == "qq_hub") taken += pick.id
                vm.selectChoice(pick)
            } else {
                vm.advanceDialogue()
            }
        }
        assertEquals("the hub moved on", "qq_onward", vm.state.value.currentNode.id)
        assertEquals("each branch played exactly once: $taken", setOf("flagged_a", "flagged_b", "unflagged", "sub"), taken.toSet())
        assertEquals("no branch repeated: $taken", 4, taken.size)
        hub().choices.forEach { assertTrue("${it.id} shows complete", complete(it.id)) }
    }

    @Test
    fun `a branch into a sub-hub is complete only once the sub-hub is finished`() {
        vm.selectChoice(hub().choices.first { it.id == "sub" })
        assertEquals("qq_sub_hub", vm.state.value.currentNode.id)
        assertFalse("the sub-hub isn't finished: the hub option stays open", complete("sub"))
        vm.selectChoice(GameContent.nodes.getValue("qq_sub_hub").choices.first())
        vm.advanceDialogue() // back through the sub-hub, which redirects home when finished
        assertTrue(complete("sub"))
    }

    private companion object {
        val NODES = """[
          {"id":"qq_hub","speakerId":"narrator","side":"CENTER_NARRATOR","text":"What will you do next?",
           "hubCompletion":{"requiredFlags":["flag_a","flag_b"],"redirectToNodeId":"qq_onward"},
           "choices":[
             {"id":"flagged_a","text":"Tune the fork","voiceKeywords":["fork"],"nextNodeId":"qq_a"},
             {"id":"flagged_b","text":"Read the stele","voiceKeywords":["stele"],"nextNodeId":"qq_b"},
             {"id":"unflagged","text":"Look around","voiceKeywords":["look"],"nextNodeId":"qq_look"},
             {"id":"sub","text":"Talk to Cedric","voiceKeywords":["cedric"],"nextNodeId":"qq_sub_hub"}]},
          {"id":"qq_a","speakerId":"narrator","side":"CENTER_NARRATOR","text":"The fork hums.","setFlagOnEnter":"flag_a","nextNodeId":"qq_a2"},
          {"id":"qq_a2","speakerId":"narrator","side":"CENTER_NARRATOR","text":"It settles.","nextNodeId":"qq_hub"},
          {"id":"qq_b","speakerId":"narrator","side":"CENTER_NARRATOR","text":"Runes glow.","setFlagOnEnter":"flag_b","nextNodeId":"qq_hub"},
          {"id":"qq_look","speakerId":"narrator","side":"CENTER_NARRATOR","text":"Dripping water.","nextNodeId":"qq_hub"},
          {"id":"qq_sub_hub","speakerId":"narrator","side":"CENTER_NARRATOR","text":"Cedric waits.",
           "hubCompletion":{"requiredFlags":["flag_sub"],"redirectToNodeId":"qq_hub"},
           "choices":[
             {"id":"ask","text":"Ask about the oath","voiceKeywords":["oath"],"nextNodeId":"qq_ask","completionFlag":"flag_sub"},
             {"id":"leave","text":"Leave him be","voiceKeywords":["leave"],"nextNodeId":"qq_hub"}]},
          {"id":"qq_ask","speakerId":"narrator","side":"CENTER_NARRATOR","text":"He answers.","nextNodeId":"qq_sub_hub"},
          {"id":"qq_onward","speakerId":"narrator","side":"CENTER_NARRATOR","text":"Onward."}
        ]"""
    }
}
