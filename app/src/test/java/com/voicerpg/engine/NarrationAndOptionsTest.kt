package com.voicerpg.engine

import androidx.compose.ui.graphics.Color
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.audio.SpeechState
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.IntentParser
import com.voicerpg.engine.engine.SaveManager
import com.voicerpg.engine.model.DialogueChoice
import com.voicerpg.engine.model.DialogueSpeaker
import com.voicerpg.engine.model.GameScreen
import com.voicerpg.engine.model.MetaCommand
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NarrationAndOptionsTest {

    private val alphaFixture = DialogueSpeaker(
        id = "alpha",
        name = "Alpha Scout",
        title = "Field Operative",
        portraitAsset = null,
        themeColor = Color(0xFFFFD54F)
    )
    private val betaFixture = DialogueSpeaker(
        id = "beta",
        name = "Beta Mage",
        title = "Elemental Adept",
        portraitAsset = null,
        themeColor = Color(0xFF4FC3F7)
    )
    private val gammaFixture = DialogueSpeaker(
        id = "gamma",
        name = "Gamma Warden",
        title = "Grove Sentinel",
        portraitAsset = null,
        themeColor = Color(0xFFA5D6A7)
    )
    private val deltaFixture = DialogueSpeaker(
        id = "delta",
        name = "Delta Blade",
        title = "Shadow Operative",
        portraitAsset = null,
        themeColor = Color(0xFFCE93D8)
    )
    private val epsilonFixture = DialogueSpeaker(
        id = "epsilon",
        name = "Epsilon Inquisitor",
        title = "The Quiet Crown",
        portraitAsset = null,
        themeColor = Color(0xFFEF5350)
    )

    @org.junit.Test
    fun speakerAttributionToggleMetaCommandRecognized() {
        val sayHi = com.voicerpg.engine.engine.IntentParser.parse("toggle speaker names", emptyList())
        org.junit.Assert.assertEquals(com.voicerpg.engine.model.MetaCommand.TOGGLE_SPEAKER_ATTRIBUTION, sayHi.metaCommand)
        val who = com.voicerpg.engine.engine.IntentParser.parse("who is speaking", emptyList())
        org.junit.Assert.assertEquals(com.voicerpg.engine.model.MetaCommand.TOGGLE_SPEAKER_ATTRIBUTION, who.metaCommand)
        val plain = com.voicerpg.engine.engine.IntentParser.parse("fireball the orc", emptyList())
        org.junit.Assert.assertEquals(com.voicerpg.engine.model.MetaCommand.NONE, plain.metaCommand)
    }


    private lateinit var narrator: CombatNarrator
    private lateinit var speechManager: SpeechManager
    private lateinit var saveManager: SaveManager
    private lateinit var storyViewModel: StoryViewModel

    @Before
    fun setUp() {
        narrator = CombatNarrator(context = null)
        speechManager = SpeechManager(context = null)
        saveManager = SaveManager(context = null)
        val testScope = CoroutineScope(Dispatchers.Default)
        storyViewModel = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = narrator,
            saveManager = saveManager,
            scopeOverride = testScope
        )
        storyViewModel.startNewGame(PlayerCustomization(name = "Kaelen"))
    }

    @Test
    fun testIntentParserRecognizesNarrationMetaCommands() {
        val intentNarration = IntentParser.parse("Please toggle narration now")
        assertEquals(MetaCommand.TOGGLE_NARRATION, intentNarration.metaCommand)

        val intentChoices = IntentParser.parse("read choices aloud")
        assertEquals(MetaCommand.TOGGLE_READ_CHOICES, intentChoices.metaCommand)

        val intentOptions = IntentParser.parse("open options")
        assertEquals(MetaCommand.OPEN_OPTIONS, intentOptions.metaCommand)

        val intentClose = IntentParser.parse("close options")
        assertEquals(MetaCommand.CLOSE_OPTIONS, intentClose.metaCommand)
    }

    @Test
    fun testCombatNarratorToggles() {
        // Narration toggle
        assertTrue(narrator.isNarrationEnabled.value)
        narrator.toggleNarration()
        assertFalse(narrator.isNarrationEnabled.value)
        narrator.setNarrationEnabled(true)
        assertTrue(narrator.isNarrationEnabled.value)

        // Read choices toggle
        assertTrue(narrator.isReadChoicesEnabled.value)
        narrator.toggleReadChoices()
        assertFalse(narrator.isReadChoicesEnabled.value)
        narrator.setReadChoicesEnabled(true)
        assertTrue(narrator.isReadChoicesEnabled.value)

        // Speech rate
        narrator.setSpeechRate(1.25f)
        assertEquals(1.25f, narrator.speechRate.value, 0.01f)

        // Character pitch
        assertTrue(narrator.isCharacterPitchEnabled.value)
        narrator.setCharacterPitchEnabled(false)
        assertFalse(narrator.isCharacterPitchEnabled.value)
    }

    @Test
    fun testCombatNarratorMultiVoiceConfiguration() {
        // When running in headless context, installed voices default safely to empty
        assertTrue(narrator.getInstalledVoices().isEmpty())
        assertEquals(0, narrator.availableVoiceCount.value)

        // Verifying speaker voice mapping getters return gracefully without throwing
        val alphaVoice = narrator.getVoiceForSpeaker(alphaFixture)
        val betaVoice = narrator.getVoiceForSpeaker(betaFixture)
        val narratorVoice = narrator.getVoiceForSpeaker(DialogueSpeaker.NARRATOR)

        // Without hardware TTS engine, speaker voices are null
        assertEquals(null, alphaVoice)
        assertEquals(null, betaVoice)
        assertEquals(null, narratorVoice)
    }

    @Test
    fun testCombatNarratorPreferredVoiceIdsConfiguredForAllSpeakers() {
        val preferred = CombatNarrator.PREFERRED_VOICE_IDS
        assertEquals(GameContent.preferredVoiceIds, preferred)
        assertEquals("en-gb-x-rjs-local", preferred["narrator"])
        for (speaker in GameContent.allSpeakers) {
            assertEquals(
                speaker.id,
                GameContent.characterById(speaker.id)?.preferredVoiceId,
                preferred[speaker.id]
            )
        }
    }

    @Test
    fun testResolvePreferredVoiceNameRespectsPersistedThenPreferredThenHeuristic() {
        val speakerId = "fixture_speaker"
        val preferred = mapOf(speakerId to "voice-preferred-local")
        val installed = setOf(
            "voice-preferred-local",
            "voice-persisted-local",
            "voice-heuristic-local"
        )

        // Heuristic alone is used when neither a persisted override nor the preferred pack is installed
        assertEquals(
            "voice-heuristic-local",
            CombatNarrator.resolvePreferredVoiceName(
                speakerId, "voice-heuristic-local",
                setOf("voice-heuristic-local"), emptyMap(), preferred
            )
        )

        // First-try preferred pack overrides the heuristic when its voice model is installed
        assertEquals(
            "voice-preferred-local",
            CombatNarrator.resolvePreferredVoiceName(
                speakerId, "voice-heuristic-local", installed, emptyMap(), preferred
            )
        )

        // Persisted player override wins over both when its voice is still installed
        assertEquals(
            "voice-persisted-local",
            CombatNarrator.resolvePreferredVoiceName(
                speakerId, "voice-heuristic-local", installed,
                mapOf(speakerId to "voice-persisted-local"), preferred
            )
        )

        // Persisted override to a no-longer-installed voice falls back to the preferred pack
        assertEquals(
            "voice-preferred-local",
            CombatNarrator.resolvePreferredVoiceName(
                speakerId, "voice-heuristic-local", installed,
                mapOf(speakerId to "voice-gone-local"), preferred
            )
        )
    }

    @Test
    fun testVoiceAssignmentsStayPendingHeadlessAndEmptySnapshot() {
        // Headless JVM has no TTS engine: assignments are held in reserve for onInit/refresh,
        // and the snapped assignments map is empty because no real voice models exist.
        val assignments = mapOf(
            alphaFixture.id to "en-gb-x-gbd-local",
            betaFixture.id to "en-gb-x-gba-local",
            gammaFixture.id to "en-us-x-tpf-local",
            deltaFixture.id to "en-us-x-tpc-local",
            epsilonFixture.id to "en-us-x-tpd-local",
            DialogueSpeaker.NARRATOR.id to "en-gb-x-rjs-local"
        )
        narrator.setVoiceAssignments(assignments)
        narrator.refreshInstalledVoices()

        assertTrue(narrator.getVoiceAssignments().isEmpty())

        // Persisting over a fresh save must not crash and the load path stays stable
        storyViewModel.persistCurrentState()
        val save = saveManager.load()
        assertNotNull(save)
        assertTrue(save!!.voiceAssignments.isEmpty())
    }

    @Test
    fun testNarrateDialogueInvokesCompletionCallbackEvenWhenMuted() {
        narrator.setNarrationEnabled(false)
        narrator.setEyesFreeMode(false)

        var completed = false
        val testChoices = listOf(
            DialogueChoice("1", "Look outside", emptyList(), "next_node")
        )
        narrator.narrateDialogue(alphaFixture, "Halt!", testChoices) {
            completed = true
        }

        assertTrue(completed)
    }

    @Test
    fun testVoiceCommandsInStoryViewModelToggleSettingsAndPersist() {
        // Initial state
        assertTrue(narrator.isNarrationEnabled.value)
        assertTrue(narrator.isReadChoicesEnabled.value)

        // Turn off narration via voice command
        storyViewModel.handleStoryVoiceInput("toggle narration")
        assertFalse(narrator.isNarrationEnabled.value)

        // Verify save data updated
        var save = saveManager.load()
        assertNotNull(save)
        assertFalse(save!!.isNarrationEnabled)

        // Turn off reading choices via voice command
        storyViewModel.handleStoryVoiceInput("toggle choices")
        assertFalse(narrator.isReadChoicesEnabled.value)

        save = saveManager.load()
        assertNotNull(save)
        assertFalse(save!!.isReadChoicesEnabled)
    }

    @Test
    fun testOpenAndCloseOptionsCallbacks() {
        var openCalled = false
        var closeCalled = false
        storyViewModel.onOpenOptions = { openCalled = true }
        storyViewModel.onCloseOptions = { closeCalled = true }

        storyViewModel.handleStoryVoiceInput("open options")
        assertTrue(openCalled)

        storyViewModel.handleStoryVoiceInput("close options")
        assertTrue(closeCalled)
    }

    @Test
    fun testNarrationSettingsPersistenceRoundTrip() {
        val custom = PlayerCustomization(name = "Rowan")
        val initialSave = saveManager.createInitialSave(custom)

        val updatedSave = initialSave.copy(
            isNarrationEnabled = false,
            isReadChoicesEnabled = true,
            speechRate = 1.30f,
            isCharacterPitchEnabled = false
        )
        saveManager.save(updatedSave)

        val loaded = saveManager.load()
        assertNotNull(loaded)
        assertFalse(loaded!!.isNarrationEnabled)
        assertTrue(loaded.isReadChoicesEnabled)
        assertEquals(1.30f, loaded.speechRate, 0.01f)
        assertFalse(loaded.isCharacterPitchEnabled)
    }

    @Test
    fun testHandsFreeAutoListenSessionStateAndCancellation() {
        assertFalse(speechManager.isAutoListen.value)
        assertFalse(speechManager.isSessionActive)

        speechManager.setAutoListen(true)
        assertTrue(speechManager.isAutoListen.value)

        var recognizedText = ""
        speechManager.startListening { result ->
            recognizedText = result
        }

        // In headless unit test with context = null, isAvailable is false and SpeechState.Error is set
        assertFalse(speechManager.isAvailable)
        assertTrue(speechManager.speechState.value is SpeechState.Error)

        // Cancel cleanly resets to Idle
        speechManager.cancel()
        assertFalse(speechManager.isSessionActive)
        assertEquals(SpeechState.Idle, speechManager.speechState.value)
        assertEquals("", recognizedText)
    }

    @Test
    fun testCombatNarratorDialoguePreservesVoiceAndUtteranceTracking() {
        var callbackFired = false
        narrator.narrateDialogue(
            speaker = alphaFixture,
            text = "By the holy light, we stand firm!",
            choices = emptyList()
        ) {
            callbackFired = true
        }

        // Without initialized engine in unit tests, callback executes cleanly via fallback
        assertTrue(callbackFired)

        // Stop cleanly resets active utterance
        narrator.stop()
        assertFalse(narrator.isSpeaking.value)
    }

    @Test
    fun testAudioSetupScreenAndCompanionVoiceState() {
        // Verify installedVoiceCount flow exists and starts at 0 in headless test
        assertEquals(0, narrator.installedVoiceCount.value)
        assertEquals(0, narrator.availableVoiceCount.value)

        // Rescan / refresh voices runs gracefully
        narrator.refreshInstalledVoices()
        assertEquals(0, narrator.installedVoiceCount.value)

        // Verify speaker mapping returns null safely without throwing
        assertEquals(null, narrator.getVoiceForSpeaker(deltaFixture))
        assertEquals(null, narrator.getVoiceForSpeaker(epsilonFixture))
        assertEquals(null, narrator.getVoiceForSpeaker(gammaFixture))

        // Verify fresh story state begins at TITLE
        val freshVm = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = narrator,
            saveManager = SaveManager(context = null),
            scopeOverride = CoroutineScope(Dispatchers.Default)
        )
        assertEquals(GameScreen.TITLE, freshVm.state.value.gameScreen)

        // Starting new game flow routes to AUDIO_SETUP then CHARACTER_CREATION
        freshVm.startNewGameFlow()
        assertEquals(GameScreen.AUDIO_SETUP, freshVm.state.value.gameScreen)
        freshVm.proceedToCharacterCreation()
        assertEquals(GameScreen.CHARACTER_CREATION, freshVm.state.value.gameScreen)

        // Reset returns to TITLE
        freshVm.resetGame()
        assertEquals(GameScreen.TITLE, freshVm.state.value.gameScreen)
    }

    @Test
    fun testCombatNarrationAndPocketGuardDecoupling() {
        // Default states
        assertTrue(narrator.isCombatNarrationEnabled.value)
        assertTrue(narrator.isPocketGuardEnabled.value)

        // Turn off combat narration
        narrator.setCombatNarrationEnabled(false)
        assertFalse(narrator.isCombatNarrationEnabled.value)

        // Turn off pocket touch guard
        narrator.setPocketGuardEnabled(false)
        assertFalse(narrator.isPocketGuardEnabled.value)
        assertFalse(narrator.isPocketGuardLocked.value)

        // Enabling eyes-free mode when pocket guard is disabled does not lock the screen guard
        narrator.setEyesFreeMode(true)
        assertTrue(narrator.isEyesFreeMode.value)
        assertFalse(narrator.isPocketGuardLocked.value)

        // Enabling pocket guard and locking locks the guard
        narrator.setPocketGuardEnabled(true)
        narrator.lockPocketGuard()
        assertTrue(narrator.isPocketGuardLocked.value)

        // Verify persistence via StoryViewModel
        storyViewModel.persistCurrentState()
        val save = saveManager.load()
        assertNotNull(save)
        assertTrue(save!!.isPocketGuardEnabled)
        assertFalse(save.isCombatNarrationEnabled)

        // Test restoring from save
        val freshNarrator = CombatNarrator(context = null)
        val freshVm = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = freshNarrator,
            saveManager = saveManager,
            scopeOverride = CoroutineScope(Dispatchers.Default)
        )
        assertFalse(freshNarrator.isCombatNarrationEnabled.value)
        assertTrue(freshNarrator.isPocketGuardEnabled.value)
    }

    @Test
    fun testNarrationSpeedVoiceCommandsParsing() {
        assertEquals(MetaCommand.SPEED_UP_NARRATION, IntentParser.parse("faster narration").metaCommand)
        assertEquals(MetaCommand.SPEED_UP_NARRATION, IntentParser.parse("speed up narration").metaCommand)
        assertEquals(MetaCommand.SPEED_UP_NARRATION, IntentParser.parse("speak faster").metaCommand)

        assertEquals(MetaCommand.SLOW_DOWN_NARRATION, IntentParser.parse("slower narration").metaCommand)
        assertEquals(MetaCommand.SLOW_DOWN_NARRATION, IntentParser.parse("slow down narration").metaCommand)
        assertEquals(MetaCommand.SLOW_DOWN_NARRATION, IntentParser.parse("speak slower").metaCommand)

        assertEquals(MetaCommand.RESET_NARRATION_SPEED, IntentParser.parse("normal narration").metaCommand)
        assertEquals(MetaCommand.RESET_NARRATION_SPEED, IntentParser.parse("normal speed").metaCommand)
        assertEquals(MetaCommand.RESET_NARRATION_SPEED, IntentParser.parse("reset narration speed").metaCommand)
    }

    @Test
    fun testNarrationSpeedPresetsAndVoiceStepping() {
        narrator.setSpeechRate(1.05f)
        assertEquals(1.05f, narrator.speechRate.value, 0.01f)

        // Step up
        storyViewModel.handleStoryVoiceInput("faster narration")
        assertEquals(1.25f, narrator.speechRate.value, 0.01f)

        storyViewModel.handleStoryVoiceInput("faster narration")
        assertEquals(1.50f, narrator.speechRate.value, 0.01f)

        // Step down
        storyViewModel.handleStoryVoiceInput("slower narration")
        assertEquals(1.25f, narrator.speechRate.value, 0.01f)

        storyViewModel.handleStoryVoiceInput("slower narration")
        assertEquals(1.05f, narrator.speechRate.value, 0.01f)

        storyViewModel.handleStoryVoiceInput("slower narration")
        assertEquals(0.85f, narrator.speechRate.value, 0.01f)

        // Reset
        storyViewModel.handleStoryVoiceInput("normal narration")
        assertEquals(1.05f, narrator.speechRate.value, 0.01f)
    }
}


