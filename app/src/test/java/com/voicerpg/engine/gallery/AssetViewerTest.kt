package com.voicerpg.engine.gallery

import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.GameScreen
import com.voicerpg.engine.ui.gallery.AssetCatalog
import com.voicerpg.engine.viewmodel.StoryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssetViewerTest {

    private lateinit var viewModel: StoryViewModel

    @Before
    fun setUp() {
        val speechManager = SpeechManager()
        val combatNarrator = CombatNarrator()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        viewModel = StoryViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            scopeOverride = testScope
        )
    }

    @org.junit.After
    fun tearDown() {
        viewModel.cancelPendingAutoAdvance()
    }

    @Test
    fun testAssetViewerViewModelNavigation() {
        assertEquals(GameScreen.TITLE, viewModel.state.value.gameScreen)

        // Open Asset Viewer
        viewModel.openAssetViewer()
        assertEquals(GameScreen.ASSET_VIEWER, viewModel.state.value.gameScreen)
        assertEquals(GameScreen.TITLE, viewModel.state.value.previousScreen)

        // Return from Asset Viewer
        viewModel.returnFromAssetViewer()
        assertEquals(GameScreen.TITLE, viewModel.state.value.gameScreen)
        assertEquals(null, viewModel.state.value.previousScreen)
    }

    @Test
    fun testAssetViewerVoiceCommandsOnTitle() {
        assertEquals(GameScreen.TITLE, viewModel.state.value.gameScreen)

        val voiceTriggers = listOf(
            "asset viewer",
            "gallery",
            "jukebox",
            "music player",
            "view assets"
        )

        for (phrase in voiceTriggers) {
            viewModel.handleStoryVoiceInput(phrase)
            assertEquals("Phrase '$phrase' should open ASSET_VIEWER", GameScreen.ASSET_VIEWER, viewModel.state.value.gameScreen)

            viewModel.returnFromAssetViewer()
            assertEquals(GameScreen.TITLE, viewModel.state.value.gameScreen)
        }
    }

    @Test
    fun testAssetCatalogFromGameContent() {
        // Verify GameContent provides non-empty speakers, scenes, and classes
        val speakers = GameContent.allSpeakers
        assertTrue("GameContent should provide speakers", speakers.isNotEmpty())

        val scenes = GameContent.scenes
        assertTrue("GameContent should provide scenes", scenes.isNotEmpty())

        val classes = GameContent.classes
        assertTrue("GameContent should provide classes", classes.isNotEmpty())
    }
}
