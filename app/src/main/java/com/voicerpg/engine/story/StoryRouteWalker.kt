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
    /** A hub is revisited once per option; more than this means the walk is going round in circles. */
    private const val MAX_VISITS = 40

    /** One way through the game: a campaign track, a route flag (e.g. a department) or a hero class. */
    data class RouteSpec(val label: String, val player: PlayerCustomization, val routeFlag: String? = null)

    /** A story fight on a route: the state of the story when it starts, and how far along the route it is. */
    data class RouteBattle(
        val encounterId: String,
        val nodeId: String,
        val flags: Map<String, Boolean>,
        val player: PlayerCustomization,
        val partyStats: List<SavedCharacterStats>,
        /** Position along the route, 0 (start) .. 1 (end). */
        val progress: Float
    )

    /**
     * Every way through the game: each campaign track (with its preset hero), else each route choice (e.g. each
     * department), else each hero class the player can pick, else the one story.
     */
    fun routes(): List<RouteSpec> {
        val tracks = GameContent.manifest.campaignTracks
        if (tracks.size > 1) {
            return tracks.map { t -> RouteSpec(t.displayName.ifBlank { t.id }, t.hero?.toCustomization(t, PlayerCustomization(campaignTrackId = t.id)) ?: PlayerCustomization(campaignTrackId = t.id)) }
        }
        val routeChoices = availableRouteChoices()
        if (routeChoices.isNotEmpty()) {
            return routeChoices.map { (flag, _) -> RouteSpec(flag, PlayerCustomization(name = "Hero"), routeFlag = flag) }
        }
        val classes = GameContent.classes
        if (classes.size > 1) {
            return classes.map { c -> RouteSpec(c.id, PlayerCustomization(name = "Hero", heroClassId = c.id)) }
        }
        return listOf(RouteSpec("story", PlayerCustomization(name = "Hero", heroClassId = classes.firstOrNull()?.id ?: "")))
    }

    /** The story fights on [route] in play order, each with the flags, hero and party it starts with. */
    fun battlesOn(route: RouteSpec): List<RouteBattle> {
        val walked = walk(route.player, emptyMap(), stopAtNodeId = null, routeFlagOverride = route.routeFlag)
        val total = walked.size.coerceAtLeast(1)
        return walked.mapIndexedNotNull { i, step ->
            val encounterId = step.node.triggerBattleEncounterId ?: return@mapIndexedNotNull null
            val player = route.player.copy(heroClassId = step.heroClassId ?: route.player.heroClassId)
            RouteBattle(encounterId, step.node.id, step.flags, player, buildPartyStats(player, step.flags), i.toFloat() / (total - 1).coerceAtLeast(1))
        }.distinctBy { it.encounterId }
    }

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
        val defeatedEncounters: List<String>,
        /** A hero class a story choice assigned on the way (e.g. a department), if any. */
        val heroClassId: String? = null
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
        var assignedClass: String? = null

        var node: DialogueNode? = start
        var count = 0
        while (node != null && count++ < MAX_STEPS) {
            val visits = (visitCounts[node.id] ?: 0) + 1
            visitCounts[node.id] = visits
            if (visits > MAX_VISITS) break

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
                defeatedEncounters = defeated.toList(),
                heroClassId = assignedClass
            )
            if (stopAtNodeId != null && node.id == stopAtNodeId) break

            // The story's own hub redirect (StoryChoiceRules), so the walk sees the choices a player sees.
            val effectiveHub = StoryChoiceRules.effectiveHub(node, flags, defeated.toList(), visited)
            if (effectiveHub.id != node.id) {
                node = effectiveHub
                continue
            }

            if (node.choices.isNotEmpty()) {
                // The story's own rules (done and locked choices, played hub branches), so a replay sees what a
                // player sees; a hub's unfinished options first, as Pure Story Mode plays it.
                val eligible = StoryChoiceRules.eligible(node, flags, defeated.toList(), visited)
                val routeChoice = if (prefix.isNotBlank() && chosenRouteFlag != null) eligible.firstOrNull { it.completionFlag == chosenRouteFlag } else null
                // A branch into a battle not fought yet comes first (a player can take every fight a hub offers before
                // finishing it; the balance simulator must see them all), then the hub's unfinished options.
                fun leadsToNewBattle(c: com.voicerpg.engine.model.DialogueChoice): Boolean {
                    var id: String? = c.nextNodeId
                    repeat(6) {
                        val n = GameContent.nodes[id] ?: return false
                        n.triggerBattleEncounterId?.let { return it !in defeated }
                        if (n.choices.isNotEmpty()) return false
                        id = n.nextNodeIdFor(flags)
                    }
                    return false
                }
                val picked = routeChoice
                    ?: eligible.firstOrNull(::leadsToNewBattle)
                    ?: eligible.firstOrNull { it.completionFlag != null && flags[it.completionFlag] != true && (prefix.isBlank() || !it.completionFlag.startsWith(prefix)) }
                    ?: eligible.firstOrNull()
                if (picked == null) {
                    // Nothing left to choose here: the story moves on by the node's own next step, as advancing does.
                    node = node.nextNodeIdFor(flags)?.let { GameContent.nodes[it] } ?: break
                    continue
                }
                decisions += picked.id
                picked.completionFlag?.let { flags[it] = true }
                picked.assignHeroClassId?.takeIf { it.isNotBlank() }?.let { assignedClass = it }
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
