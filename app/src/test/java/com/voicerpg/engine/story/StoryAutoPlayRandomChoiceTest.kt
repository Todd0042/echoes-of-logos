package com.voicerpg.engine.story

import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.model.DialogueChoice
import com.voicerpg.engine.model.DialogueNode
import com.voicerpg.engine.model.DialogueSpeaker
import com.voicerpg.engine.model.SpeakerSide
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryAutoPlayRandomChoiceTest {

    private fun createViewModel(): StoryViewModel {
        val speechManager = SpeechManager()
        val combatNarrator = CombatNarrator()
        val testScope = CoroutineScope(Dispatchers.Default)
        return StoryViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            scopeOverride = testScope
        )
    }

    @Test
    fun testPickStoryModeChoiceSamplesRandomly() {
        val vm = createViewModel()
        val testNode = DialogueNode(
            id = "test_multi_choice",
            speaker = DialogueSpeaker.NARRATOR,
            side = SpeakerSide.LEFT,
            text = "Choose your path:",
            choices = listOf(
                DialogueChoice(id = "choice_a", text = "Option Alpha", voiceKeywords = listOf("alpha"), nextNodeId = "node_a"),
                DialogueChoice(id = "choice_b", text = "Option Beta", voiceKeywords = listOf("beta"), nextNodeId = "node_b"),
                DialogueChoice(id = "choice_c", text = "Option Gamma", voiceKeywords = listOf("gamma"), nextNodeId = "node_c")
            )
        )

        val selectedIds = mutableSetOf<String>()
        repeat(50) {
            val choice = vm.pickStoryModeChoice(testNode)
            assertNotNull(choice)
            selectedIds.add(choice!!.id)
        }

        // Must select more than just the first option across 50 trials
        assertTrue("Expected random distribution across options, but got: $selectedIds", selectedIds.size > 1)
        assertTrue(selectedIds.contains("choice_a") || selectedIds.contains("choice_b") || selectedIds.contains("choice_c"))
    }

    @Test
    fun testPickStoryModeChoiceHubUncompletedRandom() {
        val vm = createViewModel()
        val hubNode = DialogueNode(
            id = "test_hub_node",
            speaker = DialogueSpeaker.NARRATOR,
            side = SpeakerSide.LEFT,
            text = "Select a branch to explore:",
            choices = listOf(
                DialogueChoice(id = "hub_1", text = "Branch 1", voiceKeywords = listOf("one"), nextNodeId = "node_1", completionFlag = "flag_1"),
                DialogueChoice(id = "hub_2", text = "Branch 2", voiceKeywords = listOf("two"), nextNodeId = "node_2", completionFlag = "flag_2"),
                DialogueChoice(id = "hub_3", text = "Branch 3", voiceKeywords = listOf("three"), nextNodeId = "node_3", completionFlag = "flag_3")
            )
        )

        val selectedIds = mutableSetOf<String>()
        repeat(50) {
            val choice = vm.pickStoryModeChoice(hubNode)
            assertNotNull(choice)
            selectedIds.add(choice!!.id)
        }

        // Must pick randomly among uncompleted options rather than defaulting to the first
        assertTrue("Expected random selection among uncompleted hub choices, got: $selectedIds", selectedIds.size > 1)
    }

    @Test
    fun testSoleEligibleChoiceReturnsSoleChoice() {
        val vm = createViewModel()
        val singleNode = DialogueNode(
            id = "test_single_choice",
            speaker = DialogueSpeaker.NARRATOR,
            side = SpeakerSide.LEFT,
            text = "Only one road ahead.",
            choices = listOf(
                DialogueChoice(id = "choice_only", text = "Continue forward", voiceKeywords = listOf("continue"), nextNodeId = "node_next")
            )
        )

        val choice = vm.pickStoryModeChoice(singleNode)
        assertNotNull(choice)
        assertEquals("choice_only", choice?.id)
    }
}
