package com.voicerpg.engine

import org.junit.Rule
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.StoryRecapEngine
import com.voicerpg.engine.model.SavedCharacterStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryRecapEngineTest {

    /** Mechanics test: runs on the engine's frozen template content, whatever game it's in. */
    @get:Rule val templateFixture = TemplateFixture()

    private fun scene(id: String) = GameContent.scenes.getValue(id)
    private fun node(id: String) = GameContent.nodes.getValue(id)

    private val heroParty = listOf(
        SavedCharacterStats(
            id = "hero",
            name = "Template Hero",
            loreClass = "Spellblade",
            currentHp = 240,
            maxHp = 240,
            currentMp = 140,
            maxMp = 140,
            speed = 70
        )
    )

    @Test
    fun testCurrentSceneRecapSpansAllScenesInChapterOrder() {
        val recap = StoryRecapEngine.buildRecap(scene("scene_echo_hall"), node("hall_intro"), emptyMap(), heroParty)

        assertEquals(2, recap.milestones.size)
        assertEquals(0, recap.milestones[0].chapterIndex)
        assertEquals(1, recap.milestones[1].chapterIndex)
        assertFalse(recap.milestones[0].isCurrent)
        assertTrue(recap.milestones[1].isCurrent)
        assertEquals("The Proving Grounds", recap.milestones[0].location)
        assertEquals("The Echo Hall", recap.milestones[1].location)
    }

    @Test
    fun testFirstSceneRecapMarksItsOnlyMilestoneCurrent() {
        val recap = StoryRecapEngine.buildRecap(scene("scene_proving_grounds"), node("template_intro"), emptyMap(), heroParty)

        assertEquals("Act I: The Template Sandbox", recap.currentActTitle)
        assertEquals("Sandbox: The Voice Sanctuary", recap.currentChapterTitle)
        assertEquals("The Proving Grounds", recap.currentSceneName)
        assertEquals(1, recap.milestones.size)
        assertTrue(recap.milestones[0].isCurrent)
        assertEquals(
            "Speak with the chronicle, learn the mechanics, and run the combat trials.",
            recap.activeObjective
        )
    }

    @Test
    fun testFellowshipRosterAndSpeakersContainHeroAndNarrator() {
        assertEquals(listOf("Template Hero (Spellblade)"), StoryRecapEngine.getPartyRoster(heroParty))
        assertEquals(listOf("Template Hero (Spellblade)"), StoryRecapEngine.getPartyRoster(emptyList()))

        assertTrue(GameContent.allSpeakers.any { it.id == "narrator" })
        assertTrue(GameContent.allSpeakers.any { it.id == "hero" })

        val recap = StoryRecapEngine.buildRecap(scene("scene_echo_hall"), node("hall_intro"), emptyMap(), heroParty)
        assertEquals(listOf("Template Hero (Spellblade)"), recap.partyRoster)
        assertEquals(recap.partyRoster, recap.fellowshipRoster)
        assertFalse(recap.partyRoster.any { it.contains("Narrator") })
    }

    @Test
    fun testSpokenRecapForCurrentSceneContainsChapterTitleAndObjective() {
        val recap = StoryRecapEngine.buildRecap(scene("scene_echo_hall"), node("hall_intro"), emptyMap(), heroParty)

        assertTrue(recap.spokenRecap.contains("Chronicle recap."))
        assertTrue(recap.spokenRecap.contains("Chapter 1: The Guardian Trial"))
        assertTrue(recap.spokenRecap.contains("at The Echo Hall"))
        assertTrue(
            recap.spokenRecap.contains(
                "Current objective: Rest to restore the party, then defeat the Proving Guardian."
            )
        )
        assertTrue(recap.spokenRecap.contains("What is your command?"))
        assertTrue(recap.activeObjective.contains("Proving Guardian"))
    }

    @Test
    fun testSpokenRecapCountsPreviousMilestoneAsPreviouslyOnYourQuest() {
        val hallRecap = StoryRecapEngine.buildRecap(scene("scene_echo_hall"), node("hall_intro"), emptyMap(), heroParty)
        assertTrue(hallRecap.spokenRecap.contains("Previously on your quest:"))
        assertTrue(hallRecap.spokenRecap.contains("learned to speak commands aloud"))

        val introRecap = StoryRecapEngine.buildRecap(scene("scene_proving_grounds"), node("template_intro"), emptyMap(), heroParty)
        assertTrue(introRecap.spokenRecap.contains("Your quest has just begun."))
        assertFalse(introRecap.spokenRecap.contains("Previously on your quest:"))
    }
}
