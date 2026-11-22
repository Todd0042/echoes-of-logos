package com.voicerpg.engine.story

import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.SaveManager
import com.voicerpg.engine.model.GameScreen
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.ui.creation.SelfiePortraitProcessor
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class MultiSlotHeroIdentityTest {

    private lateinit var saveManager: SaveManager

    @Before
    fun setup() {
        val assetsDir = listOf(
            File("app/src/main/assets/game"),
            File("src/main/assets/game"),
            File("assets/game")
        ).firstOrNull { it.isDirectory }
        assertNotNull("Assets directory should be found", assetsDir)
        GameContent.reset()
        GameContent.initializeFromDirectory(assetsDir!!)
        saveManager = SaveManager()
    }

    private fun createViewModel(): StoryViewModel {
        val speechManager = SpeechManager()
        val combatNarrator = CombatNarrator()
        val testScope = CoroutineScope(Dispatchers.Default)
        return StoryViewModel(
            saveManager = saveManager,
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            scopeOverride = testScope
        )
    }

    @Test
    fun testSlotPortraitFilenamesAreDistinct() {
        assertEquals("custom_hero_portrait_slot_1.jpg", SelfiePortraitProcessor.getCustomPortraitFilename(1))
        assertEquals("custom_hero_portrait_slot_2.jpg", SelfiePortraitProcessor.getCustomPortraitFilename(2))
        assertEquals("custom_hero_portrait_slot_3.jpg", SelfiePortraitProcessor.getCustomPortraitFilename(3))
        assertEquals("custom_hero_portrait_story.jpg", SelfiePortraitProcessor.getCustomPortraitFilename(SaveManager.STORY_MODE_SLOT))
    }

    @Test
    fun testFourSavesMaintainIndependentHeroIdentities() {
        val vm = createViewModel()

        // 1. Create save in Slot 1
        val player1 = PlayerCustomization(
            name = "HeroOne",
            title = "Paladin",
            customPortraitAsset = SelfiePortraitProcessor.getCustomPortraitFilename(1)
        )
        vm.startNewGame(player1, slot = 1)
        assertEquals("HeroOne", vm.state.value.player.name)
        assertEquals("custom_hero_portrait_slot_1.jpg", vm.state.value.player.customPortraitAsset)

        // 2. Create save in Slot 2
        val player2 = PlayerCustomization(
            name = "HeroTwo",
            title = "Mage",
            customPortraitAsset = SelfiePortraitProcessor.getCustomPortraitFilename(2)
        )
        vm.startNewGame(player2, slot = 2)
        assertEquals("HeroTwo", vm.state.value.player.name)
        assertEquals("custom_hero_portrait_slot_2.jpg", vm.state.value.player.customPortraitAsset)

        // 3. Create save in Slot 3
        val player3 = PlayerCustomization(
            name = "HeroThree",
            title = "Rogue",
            customPortraitAsset = null // Default portrait
        )
        vm.startNewGame(player3, slot = 3)
        assertEquals("HeroThree", vm.state.value.player.name)
        assertEquals(null, vm.state.value.player.customPortraitAsset)

        // 4. Create save in Story Mode Slot (0)
        val playerStory = PlayerCustomization(
            name = "HeroStory",
            title = "Chronicle Seeker",
            customPortraitAsset = SelfiePortraitProcessor.getCustomPortraitFilename(SaveManager.STORY_MODE_SLOT)
        )
        vm.startNewGame(playerStory, slot = SaveManager.STORY_MODE_SLOT)
        assertEquals("HeroStory", vm.state.value.player.name)
        assertTrue("Story mode slot must be marked pure story mode", vm.state.value.isPureStoryMode)
        assertEquals("custom_hero_portrait_story.jpg", vm.state.value.player.customPortraitAsset)

        // Verify each slot loads independently without contamination
        vm.continueGame(1)
        assertEquals(1, vm.state.value.currentSlot)
        assertEquals("HeroOne", vm.state.value.player.name)
        assertFalse(vm.state.value.isPureStoryMode)
        assertEquals("custom_hero_portrait_slot_1.jpg", vm.state.value.player.customPortraitAsset)

        vm.continueGame(2)
        assertEquals(2, vm.state.value.currentSlot)
        assertEquals("HeroTwo", vm.state.value.player.name)
        assertFalse(vm.state.value.isPureStoryMode)
        assertEquals("custom_hero_portrait_slot_2.jpg", vm.state.value.player.customPortraitAsset)

        vm.continueGame(3)
        assertEquals(3, vm.state.value.currentSlot)
        assertEquals("HeroThree", vm.state.value.player.name)
        assertFalse(vm.state.value.isPureStoryMode)
        assertEquals(null, vm.state.value.player.customPortraitAsset)

        vm.continueGame(SaveManager.STORY_MODE_SLOT)
        assertEquals(SaveManager.STORY_MODE_SLOT, vm.state.value.currentSlot)
        assertEquals("HeroStory", vm.state.value.player.name)
        assertTrue(vm.state.value.isPureStoryMode)
        assertEquals("custom_hero_portrait_story.jpg", vm.state.value.player.customPortraitAsset)
    }

    @Test
    fun testPureStoryModeOpensCharacterCreationWhenStartingFresh() {
        val vm = createViewModel()

        // Starting fresh pure story mode without existing save
        vm.startPureStoryMode(fresh = true)

        assertEquals("Must navigate to CHARACTER_CREATION screen", GameScreen.CHARACTER_CREATION, vm.state.value.gameScreen)
        assertEquals("Target slot must be STORY_MODE_SLOT", SaveManager.STORY_MODE_SLOT, vm.state.value.currentSlot)
        assertTrue("Must be flagged as pure story mode", vm.state.value.isPureStoryMode)
        assertTrue("Must be in new game flow", vm.state.value.isNewGameFlow)

        // Player confirms their custom character in Pure Story Mode
        val customStoryHero = PlayerCustomization(
            name = "HeroWanderer",
            title = "Chronicle Seeker",
            customPortraitAsset = SelfiePortraitProcessor.getCustomPortraitFilename(SaveManager.STORY_MODE_SLOT)
        )
        vm.startNewGame(customStoryHero, slot = SaveManager.STORY_MODE_SLOT)

        assertEquals("Must navigate to STORY_EXPLORATION", GameScreen.STORY_EXPLORATION, vm.state.value.gameScreen)
        assertEquals("HeroWanderer", vm.state.value.player.name)
        assertEquals(SaveManager.STORY_MODE_SLOT, vm.state.value.currentSlot)
        assertTrue("Pure story mode must remain active after character creation", vm.state.value.isPureStoryMode)

        // Now test restarting pure story mode when an existing save exists
        vm.startPureStoryMode(fresh = true)
        assertEquals("Starting fresh must bring user back to CHARACTER_CREATION", GameScreen.CHARACTER_CREATION, vm.state.value.gameScreen)
        assertEquals(SaveManager.STORY_MODE_SLOT, vm.state.value.currentSlot)
        assertTrue(vm.state.value.isPureStoryMode)
    }

    @Test
    fun testPureStoryModeResumesWithoutCharacterCreationWhenNotFresh() {
        val vm = createViewModel()

        // Create an existing story mode save
        val player = PlayerCustomization(name = "AutoHero", title = "Seeker")
        vm.startNewGame(player, slot = SaveManager.STORY_MODE_SLOT)
        assertEquals(GameScreen.STORY_EXPLORATION, vm.state.value.gameScreen)

        // Return to Title
        vm.returnToTitle()
        assertEquals(GameScreen.TITLE, vm.state.value.gameScreen)

        // Resume pure story mode without fresh flag
        vm.startPureStoryMode(fresh = false)
        assertEquals("Resuming existing story mode must enter STORY_EXPLORATION directly", GameScreen.STORY_EXPLORATION, vm.state.value.gameScreen)
        assertEquals("AutoHero", vm.state.value.player.name)
        assertTrue(vm.state.value.isPureStoryMode)
    }
}
