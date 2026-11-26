package com.voicerpg.engine.story

import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.model.GameScreen
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract test (the game's own story): Pure Story Mode can play the whole game. A new game is played with the real
 * view model, choosing exactly as auto-play does (pickStoryModeChoice), winning every battle and skipping every
 * mini-game, until the story ends. It fails when the playthrough loops (a hub keeps offering a branch it has
 * already played, so auto-play goes round it again and again) or stops (nothing left to choose, nowhere to go).
 * Three playthroughs, since auto-play picks at random among the open choices.
 */
class StoryAutoPlayReachesTheEndTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.cancel()

    private fun playthrough(run: Int): String? {
        val vm = StoryViewModel(SpeechManager(), CombatNarrator(), scopeOverride = scope)
        vm.startNewGame(PlayerCustomization(name = "Auto"))
        val visits = HashMap<String, Int>()
        val trail = ArrayDeque<String>()
        repeat(MAX_STEPS) {
            val s = vm.state.value
            when (s.gameScreen) {
                GameScreen.COMBAT_ARENA -> { vm.onCombatVictory(); return@repeat }
                GameScreen.MINI_GAME -> { vm.onSkipMiniGame(); return@repeat }
                GameScreen.STORY_EXPLORATION -> Unit
                else -> return null // the story ended (back to the title)
            }
            val node = s.currentNode
            if (node.setFlagOnEnter == "game_completed") return null
            val n = (visits[node.id] ?: 0) + 1
            visits[node.id] = n
            trail.addLast(node.id); if (trail.size > 12) trail.removeFirst()
            if (n > MAX_VISITS) {
                return "run $run loops at '${node.id}' (visited $n times). Last nodes: ${trail.joinToString(" → ")}"
            }
            val before = Triple(node.id, s.gameScreen, s.narrativeFlags.size)
            val pick = if (node.choices.isNotEmpty()) vm.pickStoryModeChoice(node) else null
            if (pick != null) vm.selectChoice(pick) else vm.advanceDialogue()
            val after = vm.state.value
            if (Triple(after.currentNode.id, after.gameScreen, after.narrativeFlags.size) == before) {
                if (node.choices.isEmpty() && node.nextNodeIdFor(after.narrativeFlags) == null && node.triggerBattleEncounterId == null &&
                    node.triggerMiniGameId == null
                ) return null // a terminal node: the end of the story
                return "run $run stops at '${node.id}': no open choice and the story doesn't move " +
                    "(choices: ${node.choices.joinToString { c ->
                        c.id + when {
                            vm.isChoiceCompleted(c, node = node) -> " [done]"
                            vm.isChoiceLocked(c, node, after.narrativeFlags) -> " [locked]"
                            else -> " [open]"
                        }
                    }}; battles won: ${after.defeatedEncounters}). Last nodes: ${trail.joinToString(" → ")}"
            }
        }
        return "run $run didn't reach the end in $MAX_STEPS steps"
    }

    @Test
    fun `pure story mode plays the whole story without looping or stopping`() {
        val problems = (1..RUNS).mapNotNull(::playthrough)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        const val RUNS = 3
        const val MAX_STEPS = 30_000
        const val MAX_VISITS = 40
    }
}
