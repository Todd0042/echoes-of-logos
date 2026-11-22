package com.voicerpg.engine.minigame

import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.MiniGameSimulator
import com.voicerpg.engine.model.GameScreen
import com.voicerpg.engine.model.MiniGameConfig
import com.voicerpg.engine.model.MiniGameOutcomeTier
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MiniGameEngineTest {

    private fun createViewModel(): StoryViewModel {
        val speechManager = SpeechManager()
        val narrator = CombatNarrator()
        val testScope = CoroutineScope(Dispatchers.Default)
        return StoryViewModel(
            speechManager = speechManager,
            combatNarrator = narrator,
            scopeOverride = testScope
        )
    }

    private fun createSampleConfig(id: String = "test_challenge"): MiniGameConfig {
        return MiniGameConfig(
            id = id,
            title = "Reflex Challenge",
            description = "Hit the timing window precisely.",
            durationSeconds = 15,
            quota = 5,
            metricDeltasOnTier = mapOf(
                MiniGameOutcomeTier.FLAWLESS to mapOf("morale" to 10f),
                MiniGameOutcomeTier.SOLID to mapOf("morale" to 5f),
                MiniGameOutcomeTier.CLUMSY to mapOf("morale" to -2f),
                MiniGameOutcomeTier.DISASTER to mapOf("morale" to -10f)
            ),
            blurbOnTier = mapOf(
                MiniGameOutcomeTier.FLAWLESS to "Pristine execution!",
                MiniGameOutcomeTier.SOLID to "Well done!",
                MiniGameOutcomeTier.CLUMSY to "Shaky performance.",
                MiniGameOutcomeTier.DISASTER to "Total blunder!"
            )
        )
    }

    @Test
    fun testMiniGameSimulatorTierThresholds() {
        val config = createSampleConfig()

        val flawless = MiniGameSimulator.outcomeFromScore(config, 95f)
        assertEquals(MiniGameOutcomeTier.FLAWLESS, flawless.tier)
        assertEquals(95f, flawless.score, 0.001f)
        assertEquals("Pristine execution!", flawless.narrativeBlurb)
        assertEquals(10f, flawless.metricDeltas["morale"] ?: 0f, 0.01f)

        val solid = MiniGameSimulator.outcomeFromScore(config, 75f)
        assertEquals(MiniGameOutcomeTier.SOLID, solid.tier)
        assertEquals("Well done!", solid.narrativeBlurb)
        assertEquals(5f, solid.metricDeltas["morale"] ?: 0f, 0.01f)

        val clumsy = MiniGameSimulator.outcomeFromScore(config, 50f)
        assertEquals(MiniGameOutcomeTier.CLUMSY, clumsy.tier)
        assertEquals("Shaky performance.", clumsy.narrativeBlurb)
        assertEquals(-2f, clumsy.metricDeltas["morale"] ?: 0f, 0.01f)

        val disaster = MiniGameSimulator.outcomeFromScore(config, 20f)
        assertEquals(MiniGameOutcomeTier.DISASTER, disaster.tier)
        assertEquals("Total blunder!", disaster.narrativeBlurb)
        assertEquals(-10f, disaster.metricDeltas["morale"] ?: 0f, 0.01f)
    }

    @Test
    fun testMiniGameSimulatorDistribution() {
        val config = createSampleConfig()
        repeat(30) {
            val outcome = MiniGameSimulator.simulate(
                config = config,
                skillModifier = 1.0f,
                fatigueModifier = 1.0f,
                random = Random(it)
            )
            assertTrue("Score should be between 0 and 100", outcome.score in 0f..100f)
            assertNotNull("Tier must not be null", outcome.tier)
            assertTrue("Narrative blurb must not be blank", outcome.narrativeBlurb.isNotBlank())
        }
    }

    @Test
    fun testTitleScreenMiniGameTestingFlow() {
        val viewModel = createViewModel()
        assertEquals(GameScreen.TITLE, viewModel.state.value.gameScreen)
        assertFalse(viewModel.state.value.isMiniGameTestMenuOpen)

        // Open QA test menu
        viewModel.openMiniGameTestMenu()
        assertTrue(viewModel.state.value.isMiniGameTestMenuOpen)

        // Close QA test menu
        viewModel.closeMiniGameTestMenu()
        assertFalse(viewModel.state.value.isMiniGameTestMenuOpen)

        // Launch mini-game directly from title
        viewModel.triggerMiniGame("test_challenge")
        assertEquals(GameScreen.MINI_GAME, viewModel.state.value.gameScreen)
        assertEquals(GameScreen.TITLE, viewModel.state.value.previousScreen)
        assertNotNull(viewModel.state.value.activeMiniGameConfig)
        assertFalse(viewModel.state.value.isMiniGameTestMenuOpen)

        // Skip / finish mini-game returns cleanly to Title
        viewModel.onSkipMiniGame()
        assertEquals(GameScreen.TITLE, viewModel.state.value.gameScreen)
        assertNull(viewModel.state.value.previousScreen)
        assertNull(viewModel.state.value.activeMiniGameConfig)
        assertFalse(viewModel.state.value.isMiniGameTestMenuOpen)
    }

    @Test
    fun testGracefulOmissionWhenNoMinigames() {
        val viewModel = createViewModel()
        if (GameContent.minigames.isEmpty()) {
            // Voice command for arcade should not crash and menu should remain closed
            viewModel.handleStoryVoiceInput("arcade")
            assertFalse(viewModel.state.value.isMiniGameTestMenuOpen)
        }
    }
}
