package com.voicerpg.engine

import org.junit.Rule
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.IntentParser
import com.voicerpg.engine.engine.SaveManager
import com.voicerpg.engine.engine.StoryScript
import com.voicerpg.engine.model.AuraColor
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.GameSaveData
import com.voicerpg.engine.model.GameScreen
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.model.SavedCharacterStats
import com.voicerpg.engine.viewmodel.CombatViewModel
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveSystemTest {

    /** Mechanics test: runs on the engine's frozen template content, whatever game it's in. */
    @get:Rule val templateFixture = TemplateFixture()

    private lateinit var saveManager: SaveManager
    private lateinit var speechManager: SpeechManager
    private lateinit var combatNarrator: CombatNarrator

    @Before
    fun setUp() {
        saveManager = SaveManager(context = null) // Headless in-memory persistence
        speechManager = SpeechManager()
        combatNarrator = CombatNarrator()
    }

    @Test
    fun testInitialSaveCreationForDifferentClasses() {
        // Elementalist
        val elemCustom = PlayerCustomization(
            name = "Lyra",
            heroClassId = "elementalist",
            title = "Acolyte of Embers",
            auraColor = AuraColor.CRIMSON_PYRE
        )
        val elemSave = saveManager.createInitialSave(elemCustom)
        assertEquals("Lyra", elemSave.player.name)
        assertEquals("elementalist", elemSave.player.heroClassId)
        assertEquals(240, elemSave.partyStats.first().maxHp)
        assertEquals(160, elemSave.partyStats.first().maxMp)
        assertTrue(elemSave.partyStats.first().spellIds.contains("fireball"))

        // Battlemage
        val battleCustom = PlayerCustomization(
            name = "Thorin",
            heroClassId = "battlemage",
            title = "Runeguard",
            auraColor = AuraColor.SOLAR_GOLD
        )
        val battleSave = saveManager.createInitialSave(battleCustom)
        assertEquals(320, battleSave.partyStats.first().maxHp)
        assertEquals(110, battleSave.partyStats.first().maxMp)
        assertTrue(battleSave.partyStats.first().spellIds.contains("flame_strike"))

        // Chanter
        val chanterCustom = PlayerCustomization(
            name = "Aria",
            heroClassId = "chanter",
            title = "Voice of the Grove",
            auraColor = AuraColor.EMERALD_GROVE
        )
        val chanterSave = saveManager.createInitialSave(chanterCustom)
        assertEquals(220, chanterSave.partyStats.first().maxHp)
        assertEquals(180, chanterSave.partyStats.first().maxMp)
        assertTrue(chanterSave.partyStats.first().spellIds.contains("temporal_stasis"))

        // Shadowweaver
        val shadowCustom = PlayerCustomization(
            name = "Vesper",
            heroClassId = "shadowweaver",
            title = "Voidstrider",
            auraColor = AuraColor.AMETHYST_VOID
        )
        val shadowSave = saveManager.createInitialSave(shadowCustom)
        assertEquals(230, shadowSave.partyStats.first().maxHp)
        assertEquals(140, shadowSave.partyStats.first().maxMp)
        assertTrue(shadowSave.partyStats.first().spellIds.contains("shadow_spike"))
    }

    @Test
    fun testSaveAndLoadRoundTripSerialization() {
        val custom = PlayerCustomization(
            name = "Zephyr",
            heroClassId = "elementalist",
            title = "Stormcaller",
            auraColor = AuraColor.SAPPHIRE_FROST
        )
        val initialSave = saveManager.createInitialSave(custom)

        val updatedSave = initialSave.copy(
            currentSceneId = "scene_crossroads",
            currentNodeId = "crossroads_cedric_first",
            decisionsMade = listOf("c1_speak", "c_village_help", "c_crossroads_join"),
            narrativeFlags = mapOf("met_cedric" to true, "cleared_prologue" to true),
            defeatedEncounters = listOf("enc_prologue_solo"),
            achievements = listOf("first_step", "voice_awakened", "shadow_slayer"),
            isEyesFreeMode = true
        )

        val saveResult = saveManager.save(updatedSave)
        assertTrue(saveResult)
        assertTrue(saveManager.hasSave())

        val loaded = saveManager.load()
        assertNotNull(loaded)
        assertEquals("Zephyr", loaded!!.player.name)
        assertEquals("elementalist", loaded.player.heroClassId)
        assertEquals(AuraColor.SAPPHIRE_FROST, loaded.player.auraColor)
        assertEquals("scene_crossroads", loaded.currentSceneId)
        assertEquals("crossroads_cedric_first", loaded.currentNodeId)
        assertEquals(3, loaded.decisionsMade.size)
        assertEquals("c_crossroads_join", loaded.decisionsMade.last())
        assertEquals(true, loaded.narrativeFlags["met_cedric"])
        assertEquals(true, loaded.narrativeFlags["cleared_prologue"])
        assertEquals(1, loaded.defeatedEncounters.size)
        assertEquals("enc_prologue_solo", loaded.defeatedEncounters.first())
        assertEquals(3, loaded.achievements.size)
        assertTrue(loaded.isEyesFreeMode)
    }

    @Test
    fun testVoiceAssignmentsRoundTripSerialization() {
        val save = saveManager.createInitialSave(PlayerCustomization(name = "Lyra"))
        val withVoices = save.copy(
            voiceAssignments = mapOf(
                "cedric" to "en-gb-x-gbd-local",
                "aethel" to "en-gb-x-gba-local",
                "lyra" to "en-us-x-tpf-local",
                "zephyr" to "en-us-x-tpc-local",
                "malakor" to "en-us-x-tpd-local",
                "narrator" to "en-gb-x-rjs-local"
            )
        )

        assertTrue(saveManager.save(withVoices))

        val loaded = saveManager.load()
        assertNotNull(loaded)
        assertEquals("en-gb-x-gbd-local", loaded!!.voiceAssignments["cedric"])
        assertEquals("en-gb-x-gba-local", loaded.voiceAssignments["aethel"])
        assertEquals("en-us-x-tpf-local", loaded.voiceAssignments["lyra"])
        assertEquals("en-us-x-tpc-local", loaded.voiceAssignments["zephyr"])
        assertEquals("en-us-x-tpd-local", loaded.voiceAssignments["malakor"])
        assertEquals("en-gb-x-rjs-local", loaded.voiceAssignments["narrator"])
        assertEquals(6, loaded.voiceAssignments.size)
    }

    @Test
    fun testDeleteSave() {
        val custom = PlayerCustomization(name = "Kael")
        val save = saveManager.createInitialSave(custom)
        saveManager.save(save)
        assertTrue(saveManager.hasSave())

        val deleted = saveManager.deleteSave()
        assertTrue(deleted)
        assertFalse(saveManager.hasSave())
        assertNull(saveManager.load())
    }

    @Test
    fun testStoryViewModelFirstLaunchRoutesToAudioSetupAndProceedsToCharacterCreation() {
        val testScope = CoroutineScope(Dispatchers.Default)
        val storyVm = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            saveManager = saveManager,
            scopeOverride = testScope
        )

        assertEquals(GameScreen.TITLE, storyVm.state.value.gameScreen)
        assertFalse(storyVm.state.value.hasExistingSave)

        storyVm.startNewGameFlow()
        assertEquals(GameScreen.AUDIO_SETUP, storyVm.state.value.gameScreen)
        storyVm.proceedToCharacterCreation()
        assertEquals(GameScreen.CHARACTER_CREATION, storyVm.state.value.gameScreen)
    }

    @Test
    fun testStoryViewModelCharacterCreationAndStateProgression() {
        val testScope = CoroutineScope(Dispatchers.Default)
        val storyVm = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            saveManager = saveManager,
            scopeOverride = testScope
        )

        // Player customizes hero and starts game
        val custom = PlayerCustomization(
            name = "Rowan",
            heroClassId = "battlemage",
            title = "Acolyte of Embers",
            auraColor = AuraColor.CRIMSON_PYRE
        )
        storyVm.startNewGame(custom)

        assertEquals(GameScreen.STORY_EXPLORATION, storyVm.state.value.gameScreen)
        assertEquals("scene_proving_grounds", storyVm.state.value.currentScene.id)
        assertEquals("template_intro", storyVm.state.value.currentNode.id)
        assertEquals("Rowan", storyVm.state.value.player.name)

        // Advance to the choice hub, then make decision 1: "Ask about the history"
        storyVm.advanceDialogue()
        storyVm.advanceDialogue()
        val speakChoice = storyVm.state.value.currentNode.choices.first { it.id == "choice_history" }
        storyVm.selectChoice(speakChoice)

        assertEquals("template_branch_history", storyVm.state.value.currentNode.id)
        assertEquals(listOf("choice_history"), storyVm.state.value.decisionsMade)

        // Verify save was written to disk/memory
        val autoSavedData = saveManager.load()
        assertNotNull(autoSavedData)
        assertEquals("template_branch_history", autoSavedData!!.currentNodeId)
        assertEquals(listOf("choice_history"), autoSavedData.decisionsMade)
        assertEquals("Rowan", autoSavedData.player.name)
    }

    @Test
    fun testStateRestorationOnAppReload() {
        val testScope = CoroutineScope(Dispatchers.Default)

        // Session 1: Play and progress
        val storyVm1 = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            saveManager = saveManager,
            scopeOverride = testScope
        )

        val custom = PlayerCustomization(
            name = "Elora",
            heroClassId = "chanter",
            title = "Voice of the Grove",
            auraColor = AuraColor.EMERALD_GROVE
        )
        storyVm1.startNewGame(custom)

        // Advance to the choice hub, then make a choice
        storyVm1.advanceDialogue()
        storyVm1.advanceDialogue()
        val choice = storyVm1.state.value.currentNode.choices.first { it.id == "choice_history" }
        storyVm1.selectChoice(choice)

        // Advance to the Echo Hall scene via its transition node
        val hallTransNode = StoryScript.ALL_NODES["template_to_echo_hall"]!!
        val jumpChoice = com.voicerpg.engine.model.DialogueChoice("jump", "Proceed to the Echo Hall", emptyList(), hallTransNode.id)
        storyVm1.selectChoice(jumpChoice)
        storyVm1.advanceDialogue()

        assertEquals("scene_echo_hall", storyVm1.state.value.currentScene.id)
        assertEquals("hall_intro", storyVm1.state.value.currentNode.id)
        assertEquals(listOf("choice_history", "jump"), storyVm1.state.value.decisionsMade)

        // Session 2: Fresh launch of the app with existing save
        val storyVm2 = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            saveManager = saveManager,
            scopeOverride = testScope
        )

        // Opens at Title Screen with save detected, ready to continue!
        assertEquals(GameScreen.TITLE, storyVm2.state.value.gameScreen)
        assertTrue(storyVm2.state.value.hasExistingSave)
        assertNotNull(storyVm2.state.value.saveSummary)
        assertEquals("Elora", storyVm2.state.value.saveSummary?.heroName)
        assertEquals(1, storyVm2.state.value.saveSummary?.partySize)

        // Continue chronicle resumes directly into story
        storyVm2.continueGame()
        assertEquals(GameScreen.STORY_EXPLORATION, storyVm2.state.value.gameScreen)
        assertEquals("scene_echo_hall", storyVm2.state.value.currentScene.id)
        assertEquals("hall_intro", storyVm2.state.value.currentNode.id)
        assertEquals("Elora", storyVm2.state.value.player.name)
        assertEquals("chanter", storyVm2.state.value.player.heroClassId)
        assertEquals(listOf("choice_history", "jump"), storyVm2.state.value.decisionsMade)

        // Return to title persists and transitions back to Title Screen
        storyVm2.returnToTitle()
        assertEquals(GameScreen.TITLE, storyVm2.state.value.gameScreen)
        assertTrue(storyVm2.state.value.hasExistingSave)
    }

    @Test
    fun testReduceMotionAndFlashingSurviveAnAppReload() {
        val testScope = CoroutineScope(Dispatchers.Default)
        val motion1 = com.voicerpg.engine.engine.MotionSettings()
        val storyVm1 = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            saveManager = saveManager,
            motionSettings = motion1,
            scopeOverride = testScope
        )
        storyVm1.startNewGame(PlayerCustomization(name = "Elora", heroClassId = "chanter"))
        assertFalse("off by default", motion1.reduceMotion.value)

        // Options -> Reduce Motion, as MainActivity wires it: toggle, then persist.
        motion1.toggleReduceMotion()
        motion1.toggleReduceFlashing()
        storyVm1.persistCurrentState()

        // Fresh launch: new settings object, same save.
        val motion2 = com.voicerpg.engine.engine.MotionSettings()
        val storyVm2 = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            saveManager = saveManager,
            motionSettings = motion2,
            scopeOverride = testScope
        )
        assertTrue("restored on launch", motion2.reduceMotion.value)
        assertTrue("reduce flashing too", motion2.reduceFlashing.value)
        storyVm2.continueGame()
        assertTrue("and kept when the game continues", motion2.reduceMotion.value)

        motion2.toggleReduceMotion()
        storyVm2.persistCurrentState()
        assertFalse(saveManager.load()!!.isReduceMotion)
    }

    @Test
    fun testCombatVictoryUpdatesAccomplishmentsAndPersists() {
        val testScope = CoroutineScope(Dispatchers.Default)
        val storyVm = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            saveManager = saveManager,
            scopeOverride = testScope
        )
        storyVm.startNewGame(PlayerCustomization(name = "Valen"))

        // Trigger encounter
        storyVm.triggerEncounter(GameContent.defaultEncounter!!.id)
        assertEquals(GameScreen.COMBAT_ARENA, storyVm.state.value.gameScreen)

        // Win encounter
        storyVm.onCombatVictory()
        assertEquals(GameScreen.STORY_EXPLORATION, storyVm.state.value.gameScreen)
        assertTrue(storyVm.state.value.defeatedEncounters.contains(GameContent.defaultEncounter!!.id))

        // Check save
        val save = saveManager.load()
        assertNotNull(save)
        assertTrue(save!!.defeatedEncounters.contains(GameContent.defaultEncounter!!.id))
    }

    @Test
    fun testCombatViewModelCustomHeroIntegration() {
        val combatVm = CombatViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            scopeOverride = CoroutineScope(Dispatchers.Default)
        )
        val custom = PlayerCustomization(
            name = "Kaelen",
            heroClassId = "shadowweaver",
            title = "Voidstrider",
            auraColor = AuraColor.AMETHYST_VOID
        )

        combatVm.applyPlayerCustomization(custom)

        val hero = combatVm.state.value.party.first()
        assertEquals("Kaelen", hero.name)
        assertEquals(230, hero.maxHp)
        assertEquals(140, hero.maxMp)
        assertTrue(hero.spells.any { it.id == "shadow_spike" })

        // Verify stats serialization from party member
        val savedHero = SavedCharacterStats.fromPartyMember(hero)
        assertEquals("Kaelen", savedHero.name)
        assertEquals(230, savedHero.maxHp)
        assertEquals(140, savedHero.maxMp)
        assertEquals(hero.spells.size, savedHero.spellIds.size)
    }

    @Test
    fun testVoiceIncantationMatchesCustomHeroName() {
        val combatVm = CombatViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            scopeOverride = CoroutineScope(Dispatchers.Default)
        )
        val custom = PlayerCustomization(
            name = "Ignis",
            heroClassId = "battlemage"
        )
        combatVm.applyPlayerCustomization(custom)

        val hero = combatVm.state.value.party.first()
        assertEquals("Ignis", hero.name)

        val orc = Enemy(
            id = "orc",
            name = "Orc",
            subtitle = "Warband Berserker",
            currentHp = 100,
            maxHp = 100,
            baseAttack = 15
        )
        val parsed = IntentParser.parse(
            utterance = "Flame strike the orc with fury!",
            availableSpells = hero.spells,
            activeEnemies = listOf(orc),
            party = combatVm.state.value.party
        )

        assertEquals("flame_strike", parsed.spell.id)
        assertEquals("orc", parsed.targetEnemyId)
    }

    @Test
    fun testMultiSlotIndependentSaveAndLoad() {
        val hero1 = PlayerCustomization(name = "Aethel", heroClassId = "elementalist")
        val hero2 = PlayerCustomization(name = "Cedric", heroClassId = "battlemage")
        val hero3 = PlayerCustomization(name = "Lyra", heroClassId = "chanter")

        saveManager.createInitialSave(hero1, slot = 1)
        saveManager.createInitialSave(hero2, slot = 2)
        saveManager.createInitialSave(hero3, slot = 3)

        assertTrue(saveManager.hasSave(1))
        assertTrue(saveManager.hasSave(2))
        assertTrue(saveManager.hasSave(3))

        val loaded1 = saveManager.load(1)
        val loaded2 = saveManager.load(2)
        val loaded3 = saveManager.load(3)

        assertNotNull(loaded1)
        assertNotNull(loaded2)
        assertNotNull(loaded3)

        assertEquals("Aethel", loaded1!!.player.name)
        assertEquals("elementalist", loaded1.player.heroClassId)

        assertEquals("Cedric", loaded2!!.player.name)
        assertEquals("battlemage", loaded2.player.heroClassId)

        assertEquals("Lyra", loaded3!!.player.name)
        assertEquals("chanter", loaded3.player.heroClassId)
    }

    @Test
    fun testMultiSlotDeletionAndSummary() {
        val hero1 = PlayerCustomization(name = "Sol", heroClassId = "battlemage")
        val hero2 = PlayerCustomization(name = "Luna", heroClassId = "shadowweaver")

        saveManager.createInitialSave(hero1, slot = 1)
        saveManager.createInitialSave(hero2, slot = 2)

        val slotsBefore = saveManager.getAllSlotInfos()
        assertEquals(3, slotsBefore.size)
        assertFalse(slotsBefore[0].isEmpty)
        assertFalse(slotsBefore[1].isEmpty)
        assertTrue(slotsBefore[2].isEmpty)

        // Delete Slot 2
        assertTrue(saveManager.deleteSave(slot = 2))
        assertFalse(saveManager.hasSave(2))
        assertTrue(saveManager.hasSave(1))

        val slotsAfter = saveManager.getAllSlotInfos()
        assertEquals(3, slotsAfter.size)
        assertFalse(slotsAfter[0].isEmpty)
        assertTrue(slotsAfter[1].isEmpty)
        assertTrue(slotsAfter[2].isEmpty)
    }

    @Test
    fun testStoryViewModelMultiSlotFlow() {
        val testScope = CoroutineScope(Dispatchers.Default)
        val storyVm = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            saveManager = saveManager,
            scopeOverride = testScope
        )

        // Start in Slot 2
        val heroCustom = PlayerCustomization(name = "Kael", heroClassId = "elementalist")
        storyVm.startNewGame(heroCustom, slot = 2)

        assertEquals(2, storyVm.state.value.currentSlot)
        assertEquals("Kael", storyVm.state.value.player.name)
        assertTrue(storyVm.state.value.hasExistingSave)

        // Switch to Slot 1 (which is empty)
        storyVm.selectSlot(1)
        assertEquals(1, storyVm.state.value.currentSlot)
        assertFalse(storyVm.state.value.hasExistingSave)
        assertNull(storyVm.state.value.saveSummary)

        // Switch back to Slot 2 (which has Kael)
        storyVm.selectSlot(2)
        assertEquals(2, storyVm.state.value.currentSlot)
        assertTrue(storyVm.state.value.hasExistingSave)
        assertEquals("Kael", storyVm.state.value.saveSummary?.heroName)
    }

    @Test
    fun testSlotBoundaryClamping() {
        // Slot 0 is reserved for Pure Story Mode
        saveManager.currentSlot = SaveManager.STORY_MODE_SLOT
        assertEquals(SaveManager.STORY_MODE_SLOT, saveManager.currentSlot)

        // Negative numbers clamp to slot 1
        saveManager.currentSlot = -1
        assertEquals(1, saveManager.currentSlot)

        // Numbers exceeding MAX_SLOTS clamp to MAX_SLOTS
        saveManager.currentSlot = 99
        assertEquals(SaveManager.MAX_SLOTS, saveManager.currentSlot)
    }

    @Test
    fun testPureStoryModeDedicatedSlotIsolation() {
        val testScope = CoroutineScope(Dispatchers.Default)

        // Setup campaign saves in slot 1 and 2
        val custom1 = PlayerCustomization(name = "CampaignHero1", heroClassId = "elementalist")
        val custom2 = PlayerCustomization(name = "CampaignHero2", heroClassId = "battlemage")
        val save1 = saveManager.createInitialSave(custom1, slot = 1).copy(saveTimestamp = 1000L)
        saveManager.save(save1, slot = 1, updateTimestamp = false)
        val save2 = saveManager.createInitialSave(custom2, slot = 2).copy(saveTimestamp = 2000L)
        saveManager.save(save2, slot = 2, updateTimestamp = false)

        val storyVm = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            saveManager = saveManager,
            scopeOverride = testScope
        )

        // Slot 2 has a newer timestamp, so it should be the active campaign slot initially
        assertEquals(2, storyVm.state.value.currentSlot)
        assertEquals("CampaignHero2", storyVm.state.value.saveSummary?.heroName)

        // Start Pure Story Mode
        storyVm.startPureStoryMode(fresh = true)
        assertTrue(storyVm.state.value.isPureStoryMode)
        assertEquals(SaveManager.STORY_MODE_SLOT, storyVm.state.value.currentSlot)
        assertTrue(saveManager.hasSave(SaveManager.STORY_MODE_SLOT))

        // Ensure campaign saves in slot 1 and slot 2 are pristine
        val slot1Data = saveManager.load(1)
        val slot2Data = saveManager.load(2)
        assertNotNull(slot1Data)
        assertNotNull(slot2Data)
        assertEquals("CampaignHero1", slot1Data?.player?.name)
        assertEquals("CampaignHero2", slot2Data?.player?.name)

        // Check Story Mode summary
        val storySummary = storyVm.getStoryModeSaveSummary()
        assertNotNull(storySummary)
        assertEquals(SaveManager.STORY_MODE_SLOT, storySummary?.slotIndex)

        // Return to title screen
        storyVm.returnToTitle()
        assertFalse(storyVm.state.value.isPureStoryMode)
        // Should have restored back to the most recently saved campaign slot (slot 2)
        assertEquals(2, storyVm.state.value.currentSlot)
        assertEquals("CampaignHero2", storyVm.state.value.saveSummary?.heroName)

        // Archives list must only contain slots 1..3, never slot 0
        val archives = saveManager.getAllSlotInfos()
        assertEquals(3, archives.size)
        assertEquals(listOf(1, 2, 3), archives.map { it.slotIndex })
    }

    @Test
    fun testAutoSelectsMostRecentlySavedCampaignSlot() {
        val testScope = CoroutineScope(Dispatchers.Default)

        // Create save 1 earlier
        val save1 = saveManager.createInitialSave(
            PlayerCustomization(name = "OldHero"),
            slot = 1
        ).copy(saveTimestamp = 1000L)
        saveManager.save(save1, slot = 1, updateTimestamp = false)

        // Create save 3 later
        val save3 = saveManager.createInitialSave(
            PlayerCustomization(name = "RecentHero"),
            slot = 3
        ).copy(saveTimestamp = 5000L)
        saveManager.save(save3, slot = 3, updateTimestamp = false)

        // Boot StoryViewModel
        val storyVm = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            saveManager = saveManager,
            scopeOverride = testScope
        )

        // Should automatically choose slot 3 because its timestamp is newer
        assertEquals(3, storyVm.state.value.currentSlot)
        assertEquals("RecentHero", storyVm.state.value.saveSummary?.heroName)
    }
}
