package com.voicerpg.engine

import org.junit.Rule
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.GameScreen
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.SpeakerSide
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StoryDialogueTest {

    /** Mechanics test: runs on the engine's frozen template content, whatever game it's in. */
    @get:Rule val templateFixture = TemplateFixture()

    private lateinit var storyViewModel: StoryViewModel
    private lateinit var dummySpeech: SpeechManager
    private lateinit var dummyNarrator: CombatNarrator

    @Before
    fun setUp() {
        dummySpeech = SpeechManager()
        dummyNarrator = CombatNarrator()
        val testScope = CoroutineScope(Dispatchers.Default)
        storyViewModel = StoryViewModel(
            speechManager = dummySpeech,
            combatNarrator = dummyNarrator,
            scopeOverride = testScope
        )
        storyViewModel.startNewGame(com.voicerpg.engine.model.PlayerCustomization(name = "Kaelen"))
    }

    @After
    fun tearDown() {
        storyViewModel.cancelPendingAutoAdvance()
    }

    @Test
    fun testTemplateInitialStoryState() {
        val state = storyViewModel.state.value
        assertEquals("scene_proving_grounds", state.currentScene.id)
        assertEquals("The Proving Grounds", state.currentScene.name)
        assertEquals("template_intro", state.currentNode.id)
        assertEquals(SpeakerSide.CENTER_NARRATOR, state.currentNode.side)
        assertTrue(state.currentNode.choices.isEmpty())
        assertEquals(GameScreen.STORY_EXPLORATION, state.gameScreen)
    }

    @Test
    fun testTemplateIntroAdvancesToChoiceHub() {
        storyViewModel.advanceDialogue()
        var state = storyViewModel.state.value
        assertEquals("template_hero_greeting", state.currentNode.id)
        assertEquals("hero", state.currentNode.speaker.id)
        assertEquals("Kaelen", state.currentNode.speaker.name)
        assertEquals(SpeakerSide.LEFT, state.currentNode.side)

        storyViewModel.advanceDialogue()
        state = storyViewModel.state.value
        assertEquals("template_choice_hub", state.currentNode.id)
        assertEquals(5, state.currentNode.choices.size)
        assertFalse(storyViewModel.canAdvanceDialogue())
    }

    @Test
    fun testTemplateVoiceKeywordsEliminateCompletedHubChoices() {
        storyViewModel.advanceDialogue()
        storyViewModel.advanceDialogue()
        assertEquals("template_choice_hub", storyViewModel.state.value.currentNode.id)

        storyViewModel.handleStoryVoiceInput("lore")
        var state = storyViewModel.state.value
        assertEquals("template_branch_history", state.currentNode.id)
        assertTrue(state.narrativeFlags["flag_template_history_read"] == true)

        storyViewModel.advanceDialogue()
        assertEquals("template_choice_hub", storyViewModel.state.value.currentNode.id)

        storyViewModel.handleStoryVoiceInput("advice")
        state = storyViewModel.state.value
        assertEquals("template_branch_counsel", state.currentNode.id)
        assertTrue(state.narrativeFlags["flag_template_counsel_received"] == true)

        storyViewModel.advanceDialogue()
        state = storyViewModel.state.value
        assertEquals("template_choice_hub", state.currentNode.id)
        assertTrue(state.narrativeFlags["flag_template_history_read"] == true)
        assertTrue(state.narrativeFlags["flag_template_counsel_received"] == true)
        assertEquals(5, state.currentNode.choices.size)
        assertFalse(storyViewModel.canAdvanceDialogue())

        val historyChoice = state.currentNode.choices.first { it.id == "choice_history" }
        storyViewModel.selectChoice(historyChoice)
        assertEquals("template_choice_hub", storyViewModel.state.value.currentNode.id)

        val counselChoice = storyViewModel.state.value.currentNode.choices.first { it.id == "choice_counsel" }
        storyViewModel.selectChoice(counselChoice)
        assertEquals("template_choice_hub", storyViewModel.state.value.currentNode.id)

        storyViewModel.handleStoryVoiceInput("lore")
        assertEquals("template_choice_hub", storyViewModel.state.value.currentNode.id)

        storyViewModel.selectChoice(
            storyViewModel.state.value.currentNode.choices.first { it.id == "choice_solo_battle" }
        )
        assertEquals("template_trigger_solo_combat", storyViewModel.state.value.currentNode.id)
    }

    @Test
    fun testTemplateEchoHallSceneSwitchAndRestHealFlow() {
        storyViewModel.advanceDialogue()
        storyViewModel.advanceDialogue()
        val hubChoices = storyViewModel.state.value.currentNode.choices
        storyViewModel.selectChoice(hubChoices.first { it.id == "choice_echo_hall" })

        var state = storyViewModel.state.value
        assertEquals("template_to_echo_hall", state.currentNode.id)
        assertEquals("scene_echo_hall", state.currentScene.id)

        storyViewModel.advanceDialogue()
        state = storyViewModel.state.value
        assertEquals("hall_intro", state.currentNode.id)
        assertEquals("scene_echo_hall", state.currentScene.id)
        assertEquals("The Echo Hall", state.currentScene.name)

        storyViewModel.advanceDialogue()
        assertEquals("hall_hub", storyViewModel.state.value.currentNode.id)

        val damagedParty = storyViewModel.state.value.partyStats.map { member ->
            PartyMember(
                id = member.id,
                name = member.name,
                loreClass = member.loreClass,
                currentHp = 10,
                maxHp = member.maxHp,
                currentMp = 5,
                maxMp = member.maxMp,
                speed = member.speed,
                spells = emptyList()
            )
        }
        storyViewModel.updatePartyStatsFromCombat(damagedParty)
        assertEquals(10, storyViewModel.state.value.partyStats.first().currentHp)

        val restChoice = storyViewModel.state.value.currentNode.choices.first { it.id == "choice_rest" }
        storyViewModel.selectChoice(restChoice)
        state = storyViewModel.state.value
        assertEquals("hall_rest", state.currentNode.id)
        assertTrue(state.narrativeFlags["hall_rest_complete"] == true)
        val hero = state.partyStats.first()
        assertEquals(hero.maxHp, hero.currentHp)
        assertEquals(hero.maxMp, hero.currentMp)

        storyViewModel.advanceDialogue()
        state = storyViewModel.state.value
        assertEquals("hall_hub", state.currentNode.id)

        val completedRestChoice = state.currentNode.choices.first { it.id == "choice_rest" }
        storyViewModel.selectChoice(completedRestChoice)
        assertEquals("hall_hub", storyViewModel.state.value.currentNode.id)

        storyViewModel.advanceDialogue()
        assertEquals("hall_trigger_boss", storyViewModel.state.value.currentNode.id)
    }

    @Test
    fun testTemplateBossBattlePendingAndHubCompletionRedirect() {
        storyViewModel.advanceDialogue()
        storyViewModel.advanceDialogue()
        val hubChoices = storyViewModel.state.value.currentNode.choices
        storyViewModel.selectChoice(hubChoices.first { it.id == "choice_echo_hall" })
        storyViewModel.advanceDialogue()
        storyViewModel.advanceDialogue()
        assertEquals("hall_hub", storyViewModel.state.value.currentNode.id)

        storyViewModel.selectChoice(
            storyViewModel.state.value.currentNode.choices.first { it.id == "choice_rest" }
        )
        storyViewModel.advanceDialogue()
        assertTrue(storyViewModel.state.value.narrativeFlags["hall_rest_complete"] == true)
        assertEquals("hall_hub", storyViewModel.state.value.currentNode.id)

        storyViewModel.selectChoice(
            storyViewModel.state.value.currentNode.choices.first { it.id == "choice_boss" }
        )
        var state = storyViewModel.state.value
        assertEquals("hall_trigger_boss", state.currentNode.id)
        assertEquals("template_boss_battle", state.currentNode.triggerBattleEncounterId)
        assertEquals(GameScreen.STORY_EXPLORATION, state.gameScreen)
        assertNull(state.activeEncounter)
        assertNotNull(GameContent.encounterById("template_boss_battle"))

        storyViewModel.advanceDialogue()
        state = storyViewModel.state.value
        assertEquals(GameScreen.COMBAT_ARENA, state.gameScreen)
        assertEquals("template_boss_battle", state.activeEncounter?.id)

        storyViewModel.onCombatVictory()
        state = storyViewModel.state.value
        assertEquals(GameScreen.STORY_EXPLORATION, state.gameScreen)
        assertNull(state.activeEncounter)
        assertEquals("hall_victory", state.currentNode.id)
        assertEquals("hero", state.currentNode.speaker.id)
        assertTrue(state.narrativeFlags["hall_boss_complete"] == true)

        storyViewModel.advanceDialogue()
        state = storyViewModel.state.value
        assertEquals("hall_conclusion", state.currentNode.id)
        assertTrue(state.narrativeFlags[StoryViewModel.GAME_COMPLETED_FLAG] == true)
    }

    @Test
    fun testTemplateSoloBattlePendingAndPostVictoryFlow() {
        storyViewModel.advanceDialogue()
        storyViewModel.advanceDialogue()
        val hub = storyViewModel.state.value.currentNode
        assertEquals(5, hub.choices.size)

        storyViewModel.selectChoice(hub.choices.first { it.id == "choice_solo_battle" })
        var state = storyViewModel.state.value
        assertEquals("template_trigger_solo_combat", state.currentNode.id)
        assertEquals("template_solo_battle", state.currentNode.triggerBattleEncounterId)
        assertEquals(GameScreen.STORY_EXPLORATION, state.gameScreen)
        assertNull(state.activeEncounter)

        storyViewModel.advanceDialogue()
        state = storyViewModel.state.value
        assertEquals(GameScreen.COMBAT_ARENA, state.gameScreen)
        assertEquals("template_solo_battle", state.activeEncounter?.id)
        assertEquals(1, state.activeEncounter?.initialParty?.size)
        assertEquals("hero", state.activeEncounter?.initialParty?.first()?.id)

        storyViewModel.onCombatVictory()
        state = storyViewModel.state.value
        assertEquals(GameScreen.STORY_EXPLORATION, state.gameScreen)
        assertNull(state.activeEncounter)
        assertEquals("template_post_victory", state.currentNode.id)
        assertEquals("hero", state.currentNode.speaker.id)
        assertTrue(state.defeatedEncounters.contains("template_solo_battle"))

        storyViewModel.advanceDialogue()
        assertEquals("template_conclusion", storyViewModel.state.value.currentNode.id)
        storyViewModel.advanceDialogue()
        assertEquals("template_choice_hub", storyViewModel.state.value.currentNode.id)

        storyViewModel.selectChoice(
            storyViewModel.state.value.currentNode.choices.first { it.id == "choice_trio_battle" }
        )
        state = storyViewModel.state.value
        assertEquals("template_trigger_trio_combat", state.currentNode.id)
        assertEquals("template_trio_battle", state.currentNode.triggerBattleEncounterId)
        assertNotNull(GameContent.encounterById("template_trio_battle"))
    }

    @Test
    fun testOpenAndReturnFromAudioSetup() {
        assertEquals(GameScreen.STORY_EXPLORATION, storyViewModel.state.value.gameScreen)
        assertNull(storyViewModel.state.value.previousScreen)

        // Open audio setup from in-game options
        storyViewModel.openAudioSetup()
        assertEquals(GameScreen.AUDIO_SETUP, storyViewModel.state.value.gameScreen)
        assertEquals(GameScreen.STORY_EXPLORATION, storyViewModel.state.value.previousScreen)

        // Return from audio setup back to game
        storyViewModel.returnFromAudioSetup()
        assertEquals(GameScreen.STORY_EXPLORATION, storyViewModel.state.value.gameScreen)
        assertNull(storyViewModel.state.value.previousScreen)
    }

    @Test
    fun testVoiceCommandOpensAudioSetup() {
        assertEquals(GameScreen.STORY_EXPLORATION, storyViewModel.state.value.gameScreen)

        // Utter voice command to assign voices
        storyViewModel.handleStoryVoiceInput("assign voices")
        assertEquals(GameScreen.AUDIO_SETUP, storyViewModel.state.value.gameScreen)
        assertEquals(GameScreen.STORY_EXPLORATION, storyViewModel.state.value.previousScreen)

        // Return
        storyViewModel.returnFromAudioSetup()
        assertEquals(GameScreen.STORY_EXPLORATION, storyViewModel.state.value.gameScreen)
    }

    @Test
    fun testBacklogOpenCloseAndVoiceCommands() {
        assertFalse(storyViewModel.state.value.isBacklogOpen)

        storyViewModel.handleStoryVoiceInput("log")
        assertTrue(storyViewModel.state.value.isBacklogOpen)

        storyViewModel.handleStoryVoiceInput("close")
        assertFalse(storyViewModel.state.value.isBacklogOpen)

        storyViewModel.handleStoryVoiceInput("history")
        assertTrue(storyViewModel.state.value.isBacklogOpen)

        storyViewModel.closeBacklog()
        assertFalse(storyViewModel.state.value.isBacklogOpen)
    }

    @Test
    fun testFastForwardControls() {
        assertFalse(storyViewModel.state.value.isFastForwarding)

        storyViewModel.startFastForward()
        assertTrue(storyViewModel.state.value.isFastForwarding)

        storyViewModel.stopFastForward()
        assertFalse(storyViewModel.state.value.isFastForwarding)

        storyViewModel.startNewGame(com.voicerpg.engine.model.PlayerCustomization(name = "Kaelen"))
        storyViewModel.handleStoryVoiceInput("skip")
        assertTrue(storyViewModel.state.value.isFastForwarding)

        storyViewModel.handleStoryVoiceInput("stop")
        assertFalse(storyViewModel.state.value.isFastForwarding)
    }

    @Test
    fun testNewGameFlowAudioSetupToCharacterCreation() {
        storyViewModel.returnToTitle()
        assertEquals(GameScreen.TITLE, storyViewModel.state.value.gameScreen)
        assertFalse(storyViewModel.state.value.isNewGameFlow)

        // User taps NEW GAME
        storyViewModel.startNewGameFlow()
        assertEquals(GameScreen.AUDIO_SETUP, storyViewModel.state.value.gameScreen)
        assertEquals(GameScreen.TITLE, storyViewModel.state.value.previousScreen)
        assertTrue(storyViewModel.state.value.isNewGameFlow)

        // Audio Setup "PROCEED" advances to Character Creation
        storyViewModel.proceedToCharacterCreation()
        assertEquals(GameScreen.CHARACTER_CREATION, storyViewModel.state.value.gameScreen)
        assertEquals(GameScreen.AUDIO_SETUP, storyViewModel.state.value.previousScreen)
        assertTrue(storyViewModel.state.value.isNewGameFlow)

        // Character Creation "EMBARK" starts new game
        storyViewModel.startNewGame(com.voicerpg.engine.model.PlayerCustomization(name = "Rowan"))
        assertEquals(GameScreen.STORY_EXPLORATION, storyViewModel.state.value.gameScreen)
        assertEquals("Rowan", storyViewModel.state.value.player.name)
        assertFalse(storyViewModel.state.value.isNewGameFlow)
    }

    @Test
    fun testAudioSetupFromTitleAndReturn() {
        storyViewModel.returnToTitle()
        assertEquals(GameScreen.TITLE, storyViewModel.state.value.gameScreen)

        // User taps AUDIO SETUP from Title screen
        storyViewModel.openAudioSetup()
        assertEquals(GameScreen.AUDIO_SETUP, storyViewModel.state.value.gameScreen)
        assertEquals(GameScreen.TITLE, storyViewModel.state.value.previousScreen)
        assertFalse(storyViewModel.state.value.isNewGameFlow)

        // Returning from Audio Setup goes back to Title
        storyViewModel.returnFromAudioSetup()
        assertEquals(GameScreen.TITLE, storyViewModel.state.value.gameScreen)
        assertFalse(storyViewModel.state.value.isNewGameFlow)
    }
}

/**
 * Content-agnostic invariants over the active game's story graph. These pass for ANY
 * JSON content under assets/game/story/, proving the engine's data wiring is sound.
 */
class StoryGraphSchemaTest {

    @Test
    fun everyStoryLinkResolves() {
        assertTrue("Game content must ship at least one story node", GameContent.nodes.isNotEmpty())
        for (node in GameContent.nodes.values) {
            node.nextNodeId?.let { next ->
                assertTrue(
                    "Node '${node.id}' nextNodeId '$next' does not resolve",
                    GameContent.nodes.containsKey(next)
                )
            }
            for (choice in node.choices) {
                assertTrue(
                    "Choice '${choice.id}' in node '${node.id}' nextNodeId '${choice.nextNodeId}' does not resolve",
                    GameContent.nodes.containsKey(choice.nextNodeId)
                )
            }
            node.hubCompletion?.let { hub ->
                assertTrue(
                    "Hub '${node.id}' redirectToNodeId '${hub.redirectToNodeId}' does not resolve",
                    GameContent.nodes.containsKey(hub.redirectToNodeId)
                )
            }
            node.changeSceneId?.let { sceneId ->
                assertTrue(
                    "Node '${node.id}' changeSceneId '$sceneId' does not resolve",
                    GameContent.scenes.containsKey(sceneId)
                )
            }
        }
    }

    @Test
    fun everyChoiceCarriesAtLeastOneVoiceKeyword() {
        for (node in GameContent.nodes.values) {
            for (choice in node.choices) {
                assertTrue(
                    "Choice '${choice.id}' in node '${node.id}' has no non-blank voiceKeyword",
                    choice.voiceKeywords.any { it.isNotBlank() }
                )
            }
        }
    }

    @Test
    fun everyBattleTriggerResolvesToAnEncounter() {
        for (node in GameContent.nodes.values) {
            node.triggerBattleEncounterId?.let { encounterId ->
                assertTrue(
                    "Node '${node.id}' triggerBattleEncounterId '$encounterId' does not resolve",
                    GameContent.encounters.containsKey(encounterId)
                )
            }
        }
    }

    @Test
    fun everySceneInitialNodeResolvesToANode() {
        assertTrue("Game content must ship at least one scene", GameContent.scenes.isNotEmpty())
        for (scene in GameContent.scenes.values) {
            assertTrue(
                "Scene '${scene.id}' initialNodeId '${scene.initialNodeId}' does not resolve",
                GameContent.nodes.containsKey(scene.initialNodeId)
            )
        }
    }

    @Test
    fun everyFlagNameIsNonBlank() {
        for (node in GameContent.nodes.values) {
            node.setFlagOnEnter?.let { flag ->
                assertTrue("Node '${node.id}' has a blank setFlagOnEnter", flag.isNotBlank())
            }
            node.hubCompletion?.requiredFlags?.forEach { flag ->
                assertTrue("Hub '${node.id}' has a blank requiredFlag", flag.isNotBlank())
            }
            for (choice in node.choices) {
                choice.completionFlag?.let { flag ->
                    assertTrue("Choice '${choice.id}' has a blank completionFlag", flag.isNotBlank())
                }
            }
        }
    }

    @Test
    fun everyNodeIsReachableFromTheInitialScene() {
        val startId = GameContent.initialScene.initialNodeId
        assertTrue("Initial scene '${GameContent.initialScene.id}' entry node does not resolve", GameContent.nodes.containsKey(startId))

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(startId)
        // Incidents are entry points the engine jumps to on its own (IncidentManager), not story links.
        GameContent.incidents.values.forEach { incident -> incident.triggerNodeId?.let { queue.add(it) } }
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!visited.add(id)) continue
            val node = GameContent.nodes[id] ?: continue
            node.nextNodeId?.let { queue.add(it) }
            node.choices.forEach { queue.add(it.nextNodeId) }
            node.hubCompletion?.let { queue.add(it.redirectToNodeId) }
            node.changeSceneId?.let { sceneId ->
                GameContent.scenes[sceneId]?.let { queue.add(it.initialNodeId) }
            }
        }

        val unreachable = GameContent.nodes.keys - visited
        assertTrue("Unreachable story nodes: $unreachable", unreachable.isEmpty())
    }

    @Test
    fun onlyGameCompletedNodesAreTerminal() {
        val terminals = GameContent.nodes.values.filter { it.nextNodeId == null && it.choices.isEmpty() }
        assertTrue("Expected at least one terminal story node", terminals.isNotEmpty())
        for (node in terminals) {
            assertEquals(
                "Terminal node '${node.id}' must set ${StoryViewModel.GAME_COMPLETED_FLAG}",
                StoryViewModel.GAME_COMPLETED_FLAG,
                node.setFlagOnEnter
            )
        }
    }
}
