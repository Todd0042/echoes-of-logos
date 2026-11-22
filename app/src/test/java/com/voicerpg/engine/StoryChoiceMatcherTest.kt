package com.voicerpg.engine

import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.engine.StoryChoiceMatcher
import com.voicerpg.engine.engine.StoryScript
import com.voicerpg.engine.model.CombatPhase
import com.voicerpg.engine.model.DialogueChoice
import com.voicerpg.engine.viewmodel.CombatViewModel
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StoryChoiceMatcherTest {

    private lateinit var dummySpeech: SpeechManager
    private lateinit var dummyNarrator: CombatNarrator
    private lateinit var combatViewModel: CombatViewModel
    private lateinit var storyViewModel: StoryViewModel

    @Before
    fun setUp() {
        dummySpeech = SpeechManager()
        dummyNarrator = CombatNarrator()
        val scope = CoroutineScope(Dispatchers.Default)
        combatViewModel = CombatViewModel(
            speechManager = dummySpeech,
            combatNarrator = dummyNarrator,
            scopeOverride = scope
        )
        storyViewModel = StoryViewModel(
            speechManager = dummySpeech,
            combatNarrator = dummyNarrator,
            scopeOverride = scope
        )
    }

    @Test
    fun testNerdyRoleplayUtterancesForStoryChoices() {
        val nodeChoices = listOf(
            DialogueChoice("c1_look", "Examine the cold fireplace", listOf("fireplace", "hearth", "examine", "cold"), "cottage_fireplace"),
            DialogueChoice("c1_window", "Look out the window", listOf("window", "look", "outside", "pines"), "cottage_window"),
            DialogueChoice("c1_speak", "Try to speak", listOf("speak", "voice", "talk", "hello"), "cottage_voice")
        )

        // 1. Loud and nerdy fireplace chant/inspection
        val nerdyFireplace = "By the fading embers of the hearth, I shall inspect the cold fireplace!"
        val matched1 = StoryChoiceMatcher.matchChoice(nerdyFireplace, nodeChoices)
        assertNotNull(matched1)
        assertEquals("c1_look", matched1?.id)

        // 2. Thematic roleplay window observation
        val nerdyWindow = "I step toward the frost-covered window pane and gaze outside into the pines"
        val matched2 = StoryChoiceMatcher.matchChoice(nerdyWindow, nodeChoices)
        assertNotNull(matched2)
        assertEquals("c1_window", matched2?.id)

        // 3. Spoken voice attempt
        val nerdySpeak = "I clear my throat and try to speak aloud into the eerie silence"
        val matched3 = StoryChoiceMatcher.matchChoice(nerdySpeak, nodeChoices)
        assertNotNull(matched3)
        assertEquals("c1_speak", matched3?.id)
    }

    @Test
    fun testSpellChantInStoryModeMatchesSpellChoice() {
        val nodeChoices = listOf(
            DialogueChoice("c2_chant", "Chant 'Fireball!' to test your flame", listOf("fireball", "flame", "fire", "chant", "test"), "cottage_flame_test"),
            DialogueChoice("c2_outside", "Hurry outside into the village", listOf("outside", "village", "hurry", "leave", "step"), "cottage_to_village")
        )

        // Nerdy dramatic spell incantation
        val spellUtterance = "I summon the primordial incandescent wrath of the elements, Fireball!"
        val matched = StoryChoiceMatcher.matchChoice(spellUtterance, nodeChoices)
        assertNotNull(matched)
        assertEquals("c2_chant", matched?.id)
    }

    @Test
    fun testBattleConclusionIntentParsing() {
        // Continue Story / Commence Story variations
        assertEquals(
            StoryChoiceMatcher.ConclusionAction.CONTINUE_STORY,
            StoryChoiceMatcher.parseBattleConclusionIntent("commence story")
        )
        assertEquals(
            StoryChoiceMatcher.ConclusionAction.CONTINUE_STORY,
            StoryChoiceMatcher.parseBattleConclusionIntent("Glory awaits, let us commence the story and advance!")
        )
        assertEquals(
            StoryChoiceMatcher.ConclusionAction.CONTINUE_STORY,
            StoryChoiceMatcher.parseBattleConclusionIntent("Onward to victory and the next chapter!")
        )
        assertEquals(
            StoryChoiceMatcher.ConclusionAction.CONTINUE_STORY,
            StoryChoiceMatcher.parseBattleConclusionIntent("We press forward into the ruins!")
        )

        // Restart Battle / Rematch variations
        assertEquals(
            StoryChoiceMatcher.ConclusionAction.RESTART_BATTLE,
            StoryChoiceMatcher.parseBattleConclusionIntent("restart battle")
        )
        assertEquals(
            StoryChoiceMatcher.ConclusionAction.RESTART_BATTLE,
            StoryChoiceMatcher.parseBattleConclusionIntent("I shall not falter, run it back!")
        )
        assertEquals(
            StoryChoiceMatcher.ConclusionAction.RESTART_BATTLE,
            StoryChoiceMatcher.parseBattleConclusionIntent("Arise! We fight again!")
        )
        assertEquals(
            StoryChoiceMatcher.ConclusionAction.RESTART_BATTLE,
            StoryChoiceMatcher.parseBattleConclusionIntent("Rematch these shadow fiends once more!")
        )
    }

    @Test
    fun testCombatViewModelVictoryVoiceContinue() {
        var storyContinued = false
        combatViewModel.onContinueStory = {
            storyContinued = true
        }

        // Put combat into BATTLE_WON phase
        val wonField = combatViewModel.javaClass.getDeclaredField("_state")
        wonField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = wonField.get(combatViewModel) as kotlinx.coroutines.flow.MutableStateFlow<com.voicerpg.engine.viewmodel.CombatState>
        stateFlow.value = stateFlow.value.copy(phase = CombatPhase.BATTLE_WON)

        // Process incantation "Commence story"
        combatViewModel.processIncantation("Commence story")
        assertTrue("onContinueStory should be triggered by voice in BATTLE_WON", storyContinued)
    }

    @Test
    fun testCombatViewModelDefeatVoiceRestart() {
        // Put combat into BATTLE_LOST phase
        val wonField = combatViewModel.javaClass.getDeclaredField("_state")
        wonField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = wonField.get(combatViewModel) as kotlinx.coroutines.flow.MutableStateFlow<com.voicerpg.engine.viewmodel.CombatState>
        stateFlow.value = stateFlow.value.copy(phase = CombatPhase.BATTLE_LOST)

        // Process incantation "Restart battle"
        combatViewModel.processIncantation("Restart battle")
        assertEquals(CombatPhase.ATB_WAITING, combatViewModel.state.value.phase)
    }

    @Test
    fun testProgressionUtterancesInNonBranchingStoryNodes() {
        assertTrue(StoryChoiceMatcher.isProgressionUtterance("next"))
        assertTrue(StoryChoiceMatcher.isProgressionUtterance("continue"))
        assertTrue(StoryChoiceMatcher.isProgressionUtterance("onward"))
        assertTrue(StoryChoiceMatcher.isProgressionUtterance("lead the way"))
        assertTrue(StoryChoiceMatcher.isProgressionUtterance("tell me more"))
        assertTrue(StoryChoiceMatcher.isProgressionUtterance("what happened next"))
        assertTrue(StoryChoiceMatcher.isProgressionUtterance("we ride to battle"))
        assertTrue(StoryChoiceMatcher.isProgressionUtterance("I hear you, Sir Cedric, let us proceed"))
    }
}
