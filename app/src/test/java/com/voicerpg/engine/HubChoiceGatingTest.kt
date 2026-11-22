package com.voicerpg.engine

import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.DialogueChoice
import com.voicerpg.engine.model.DialogueNode
import com.voicerpg.engine.model.DialogueSpeaker
import com.voicerpg.engine.model.HubCompletion
import com.voicerpg.engine.model.SpeakerSide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HubChoiceGatingTest {

    private fun isChoiceLocked(choice: DialogueChoice, node: DialogueNode, flags: Map<String, Boolean>): Boolean {
        if (choice.requiredFlags.isNotEmpty() && !choice.requiredFlags.all { flags[it] == true }) {
            return true
        }
        val completion = node.hubCompletion
        if (completion != null && completion.requiredFlags.isNotEmpty()) {
            val isHubDialogueChoice = choice.completionFlag != null && completion.requiredFlags.contains(choice.completionFlag)
            if (!isHubDialogueChoice) {
                val allDialogueDone = completion.requiredFlags.all { flags[it] == true }
                if (!allDialogueDone) {
                    return true
                }
            }
        }
        return false
    }

    @Test
    fun testHubAdvancingChoiceLockedUntilAllDialoguePlayed() {
        val hub = DialogueNode(
            id = "test_hub",
            speaker = DialogueSpeaker.NARRATOR,
            side = SpeakerSide.CENTER_NARRATOR,
            text = "Welcome to the hub.",
            hubCompletion = HubCompletion(
                requiredFlags = listOf("flag_a", "flag_b"),
                redirectToNodeId = "next_chapter"
            ),
            choices = listOf(
                DialogueChoice(id = "c_a", text = "Option A", voiceKeywords = listOf("a"), nextNodeId = "branch_a", completionFlag = "flag_a"),
                DialogueChoice(id = "c_b", text = "Option B", voiceKeywords = listOf("b"), nextNodeId = "branch_b", completionFlag = "flag_b"),
                DialogueChoice(id = "c_battle", text = "Engage Boss", voiceKeywords = listOf("boss"), nextNodeId = "boss_battle", completionFlag = "flag_boss")
            )
        )

        val choiceA = hub.choices[0]
        val choiceB = hub.choices[1]
        val choiceBattle = hub.choices[2]

        val flags0 = emptyMap<String, Boolean>()
        // Dialogue choices are unlocked on entry
        assertFalse("Choice A should be unlocked", isChoiceLocked(choiceA, hub, flags0))
        assertFalse("Choice B should be unlocked", isChoiceLocked(choiceB, hub, flags0))
        // Battle choice is locked on entry
        assertTrue("Battle choice must be locked before any dialogue", isChoiceLocked(choiceBattle, hub, flags0))

        // Complete 1 of 2 dialogue options
        val flags1 = mapOf("flag_a" to true)
        assertFalse("Choice B should still be unlocked", isChoiceLocked(choiceB, hub, flags1))
        assertTrue("Battle choice must remain locked until ALL dialogue options complete", isChoiceLocked(choiceBattle, hub, flags1))

        // Complete both dialogue options
        val flags2 = mapOf("flag_a" to true, "flag_b" to true)
        assertFalse("Battle choice must be UNLOCKED once all dialogue options are complete", isChoiceLocked(choiceBattle, hub, flags2))
    }
}
