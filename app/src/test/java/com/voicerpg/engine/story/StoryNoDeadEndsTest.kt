package com.voicerpg.engine.story

import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract test (the game's own story): the player can always go on. Walks every node of the game's story with
 * the engine's real choice-locking rules and fails on
 *
 * - a **dead end**: a node whose every choice is locked when the player first arrives, even with every flag the
 *   rest of the story can set already set (a hub whose own way out waits on itself, say);
 * - a choice that can **never unlock**: it requires a flag nothing in the game ever sets.
 *
 * "First arrives" means none of the node's own choices has been taken yet (their completion flags and the hub's
 * required flags are unset); everything else is as generous as the story allows, so a failure here is a
 * structural bug, not a matter of play order.
 */
class StoryNoDeadEndsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `no story node locks every way out, and every required flag can be set`() {
        val nodes = GameContent.nodes
        val settable = nodes.values.mapNotNull { it.setFlagOnEnter }.toSet() +
            nodes.values.flatMap { n -> n.choices.mapNotNull { it.completionFlag } }
        fun canBeSet(flag: String) = flag in settable || flag.startsWith("flag_incident_") || flag == "incident_just_happened"

        val vm = StoryViewModel(SpeechManager(), CombatNarrator(), scopeOverride = scope)
        val deadEnds = mutableListOf<String>()
        val neverUnlocks = mutableListOf<String>()

        for (node in nodes.values.sortedBy { it.id }) {
            if (node.choices.isEmpty()) continue
            val own = node.choices.mapNotNull { it.completionFlag }.toSet() + node.hubCompletion?.requiredFlags.orEmpty()
            val flags = (settable - own).associateWith { true }
            if (node.choices.all { vm.isChoiceLocked(it, node, flags) }) {
                deadEnds += "${node.id}: " + node.choices.joinToString(" | ") { "\"${it.text.take(50)}\"" }
            }
            for (choice in node.choices) {
                choice.requiredFlags.filterNot(::canBeSet).forEach { neverUnlocks += "${node.id} / ${choice.id}: needs '$it', which nothing sets" }
            }
        }
        val report = buildString {
            if (deadEnds.isNotEmpty()) appendLine("Dead ends (every choice locked on arrival):\n  " + deadEnds.joinToString("\n  "))
            if (neverUnlocks.isNotEmpty()) appendLine("Choices that can never unlock:\n  " + neverUnlocks.joinToString("\n  "))
        }
        assertTrue(report, deadEnds.isEmpty() && neverUnlocks.isEmpty())
    }
}
