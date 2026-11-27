package com.voicerpg.engine.story

import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.DialogueNode
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.model.SavedCharacterStats
import com.voicerpg.engine.model.StoryScene
import com.voicerpg.engine.viewmodel.StoryViewModel

/**
 * Walks a game's story graph in play order for a single route (E8).
 *
 * A route is determined either by [PlayerCustomization.campaignTrackId] (when `manifest.campaignTracks` has
 * multiple tracks) or by a route flag matching `manifest.routeFlagPrefix` (e.g. `role_*`).
 */
object StoryRouteWalker {

    data class ReplayedRouteState(
        val scene: StoryScene,
        val node: DialogueNode,
        val flags: Map<String, Boolean>,
        val visitedNodeIds: Set<String>,
        val decisionsMade: List<String>,
        val defeatedEncounters: List<String>,
        val partyStats: List<SavedCharacterStats>
    )

    private const val MAX_STEPS = 2500

    fun startNodeFor(player: PlayerCustomization): DialogueNode {
        val tracks = GameContent.manifest.campaignTracks
        if (tracks.size > 1) {
            val track = tracks.firstOrNull { it.id.equals(player.campaignTrackId, ignoreCase = true) }
                ?: tracks.first()
            val startNode = GameContent.nodes[track.startNodeId]
            if (startNode != null) return startNode
        }
        return GameContent.initialNode
    }

    fun activeRouteFlag(flags: Map<String, Boolean>): String? {
        val prefix = GameContent.manifest.routeFlagPrefix
        if (prefix.isBlank()) return null
        return flags.entries.firstOrNull { it.value && it.key.startsWith(prefix) }?.key
    }

    /** Available route choices when `routeFlagPrefix` is configured (e.g. departments). */
    fun availableRouteChoices(): List<Pair<String, String>> {
        val prefix = GameContent.manifest.routeFlagPrefix
        if (prefix.isBlank()) return emptyList()
        val found = linkedMapOf<String, String>()
        GameContent.nodes.values.forEach { node ->
            node.choices.forEach { ch ->
                val flag = ch.completionFlag
                if (flag != null && flag.startsWith(prefix) && flag !in found) {
                    found[flag] = ch.text
                }
            }
        }
        return found.entries.map { it.key to it.value }
    }

    fun needsRouteChoiceBeforeWarp(targetNodeId: String, currentFlags: Map<String, Boolean>): Boolean {
        val prefix = GameContent.manifest.routeFlagPrefix
        if (prefix.isBlank()) return false
        if (activeRouteFlag(currentFlags) != null) return false
        // Check if targetNodeId is reached before any node with a routeFlagPrefix choice.
        var cur: DialogueNode? = GameContent.initialNode
        val seen = mutableSetOf<String>()
        var steps = 0
        while (cur != null && steps++ < MAX_STEPS) {
            if (cur.id == targetNodeId) return false
            if (cur.choices.any { it.completionFlag?.startsWith(prefix) == true }) return true
            if (!seen.add(cur.id)) break
            val nextId = cur.nextNodeIdFor(currentFlags) ?: cur.choices.firstOrNull()?.nextNodeId ?: break
            cur = GameContent.nodes[nextId]
        }
        return true
    }

    fun checkpointsFor(
        player: PlayerCustomization,
        currentFlags: Map<String, Boolean>,
        routeFlagOverride: String? = null
    ): List<StoryViewModel.DebugChapterTarget> {
        val walked = walk(player, currentFlags, stopAtNodeId = null, routeFlagOverride = routeFlagOverride)
        val explicit = walked.filter { it.node.checkpoint.isNotBlank() }
            .distinctBy { it.node.id }
            .mapIndexed { idx, step ->
                StoryViewModel.DebugChapterTarget(step.node.checkpoint, step.node.id, idx)
            }
        if (explicit.isNotEmpty()) return explicit

        val sceneChanges = mutableListOf<StoryViewModel.DebugChapterTarget>()
        var lastSceneId: String? = null
        walked.forEachIndexed { idx, step ->
            if (step.scene.id != lastSceneId) {
                lastSceneId = step.scene.id
                if (sceneChanges.none { it.introNodeId == step.node.id }) {
                    sceneChanges += StoryViewModel.DebugChapterTarget(
                        label = step.scene.chapterTitle.ifBlank { step.scene.id },
                        introNodeId = step.node.id,
                        chapterIndex = step.scene.chapterIndex
                    )
                }
            }
        }
        return sceneChanges.ifEmpty { StoryViewModel.DEBUG_CHAPTER_TARGETS }
    }

    fun replayTo(
        targetNodeId: String,
        player: PlayerCustomization,
        currentFlags: Map<String, Boolean>,
        routeFlagOverride: String? = null
    ): ReplayedRouteState? {
        val walked = walk(player, currentFlags, stopAtNodeId = targetNodeId, routeFlagOverride = routeFlagOverride)
        val hit = walked.lastOrNull()?.takeIf { it.node.id == targetNodeId } ?: return null
        val stats = buildPartyStats(player, hit.flags)
        return ReplayedRouteState(
            scene = hit.scene,
            node = hit.node,
            flags = hit.flags,
            visitedNodeIds = hit.visitedNodeIds,
            decisionsMade = hit.decisionsMade,
            defeatedEncounters = hit.defeatedEncounters,
            partyStats = stats
        )
    }

    private data class WalkStep(
        val scene: StoryScene,
        val node: DialogueNode,
        val flags: Map<String, Boolean>,
        val visitedNodeIds: Set<String>,
        val decisionsMade: List<String>,
        val defeatedEncounters: List<String>
    )

    private fun walk(
        player: PlayerCustomization,
        currentFlags: Map<String, Boolean>,
        stopAtNodeId: String?,
        routeFlagOverride: String?
    ): List<WalkStep> {
        val start = startNodeFor(player)
        var currentScene = GameContent.scenes.values.firstOrNull { it.initialNodeId == start.id }
            ?: GameContent.scenes[GameContent.manifest.campaignTracks.firstOrNull { it.id == player.campaignTrackId }?.startSceneId]
            ?: GameContent.initialScene
        val flags = linkedMapOf<String, Boolean>()
        val prefix = GameContent.manifest.routeFlagPrefix
        val chosenRouteFlag = routeFlagOverride ?: activeRouteFlag(currentFlags)

        val visited = linkedSetOf<String>()
        val decisions = mutableListOf<String>()
        val defeated = linkedSetOf<String>()
        val visitCounts = mutableMapOf<String, Int>()
        val steps = mutableListOf<WalkStep>()

        var node: DialogueNode? = start
        var count = 0
        while (node != null && count++ < MAX_STEPS) {
            val visits = (visitCounts[node.id] ?: 0) + 1
            visitCounts[node.id] = visits
            if (visits > 4) break

            node.changeSceneId?.let { scId -> GameContent.scenes[scId]?.let { currentScene = it } }
            node.setFlagOnEnter?.let { flags[it] = true }
            visited += node.id
            node.triggerBattleEncounterId?.let { defeated += it }

            steps += WalkStep(
                scene = currentScene,
                node = node,
                flags = flags.toMap(),
                visitedNodeIds = visited.toSet(),
                decisionsMade = decisions.toList(),
                defeatedEncounters = defeated.toList()
            )
            if (stopAtNodeId != null && node.id == stopAtNodeId) break

            val hubRedirect = node.hubCompletion?.let { hc ->
                if (hc.requiredFlags.all { flags[it] == true }) hc.redirectToNodeId else null
            }
            if (hubRedirect != null) {
                node = GameContent.nodes[hubRedirect]
                continue
            }

            if (node.choices.isNotEmpty()) {
                val eligible = node.choices.filter { ch ->
                    ch.requiredFlags.all { flags[it] == true } &&
                        (ch.completionFlag == null || flags[ch.completionFlag] != true)
                }
                val picked = if (prefix.isNotBlank() && chosenRouteFlag != null) {
                    eligible.firstOrNull { it.completionFlag == chosenRouteFlag } ?: eligible.firstOrNull()
                } else {
                    eligible.firstOrNull()
                } ?: break
                decisions += picked.id
                picked.completionFlag?.let { flags[it] = true }
                node = GameContent.nodes[picked.nextNodeId]
                continue
            }

            val nextId = node.nextNodeIdFor(flags) ?: break
            node = GameContent.nodes[nextId]
        }
        return steps
    }

    private fun buildPartyStats(player: PlayerCustomization, flags: Map<String, Boolean>): List<SavedCharacterStats> {
        val party = mutableListOf<SavedCharacterStats>()
        val heroChar = GameContent.heroCharacter
        val heroClass = GameContent.classById(player.heroClassId)
        val heroHp = heroChar?.combat?.maxHp ?: heroClass.startingHp
        val heroMp = if (GameContent.usesMana) (heroChar?.combat?.maxMp ?: heroClass.startingMp) else 0
        val heroSpeed = heroChar?.combat?.speed ?: heroClass.startingSpeed
        party += SavedCharacterStats(
            id = "hero",
            name = player.name,
            loreClass = heroClass.title,
            currentHp = heroHp,
            maxHp = heroHp,
            currentMp = heroMp,
            maxMp = heroMp,
            speed = heroSpeed
        )
        GameContent.characters.values
            .filter { it.id != "hero" && it.joinWhenFlag != null && flags[it.joinWhenFlag] == true }
            .forEach { c ->
                c.toPartyMember()?.let { m ->
                    party += SavedCharacterStats.fromPartyMember(m)
                }
            }
        return party
    }
}
