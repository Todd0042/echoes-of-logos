package com.voicerpg.engine.story

import com.voicerpg.engine.model.DialogueNode
import com.voicerpg.engine.model.DialogueSpeaker
import com.voicerpg.engine.model.FlagRoute
import com.voicerpg.engine.model.SpeakerSide
import org.junit.Assert.assertEquals
import org.junit.Test

/** DialogueNode.flagRoutes: automatic branching on story flags, with nextNodeId as the fallback. */
class FlagRoutesTest {

    private val branch = DialogueNode(
        id = "branch", speaker = DialogueSpeaker.NARRATOR, side = SpeakerSide.CENTER_NARRATOR,
        text = "Report to your station.", nextNodeId = "default_path",
        flagRoutes = listOf(
            FlagRoute(listOf("role_cashier"), "cashier_path"),
            FlagRoute(listOf("role_unloader", "night_shift"), "night_unloader_path"),
            FlagRoute(listOf("role_unloader"), "unloader_path"),
        )
    )

    @Test fun noFlagsFallsBackToNextNode() = assertEquals("default_path", branch.nextNodeIdFor(emptyMap()))

    @Test fun matchingFlagPicksItsRoute() = assertEquals("cashier_path", branch.nextNodeIdFor(mapOf("role_cashier" to true)))

    @Test fun firstMatchingRouteWins() {
        assertEquals("night_unloader_path", branch.nextNodeIdFor(mapOf("role_unloader" to true, "night_shift" to true)))
        assertEquals("unloader_path", branch.nextNodeIdFor(mapOf("role_unloader" to true)))
    }

    @Test fun aFlagSetToFalseDoesNotMatch() = assertEquals("default_path", branch.nextNodeIdFor(mapOf("role_cashier" to false)))

    @Test fun allNextNodeIdsListsEveryTarget() =
        assertEquals(listOf("cashier_path", "night_unloader_path", "unloader_path", "default_path"), branch.allNextNodeIds)
}
