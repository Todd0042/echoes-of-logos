package com.voicerpg.engine.story

import com.voicerpg.engine.engine.StoryScript
import com.voicerpg.engine.model.DialogueChoice
import com.voicerpg.engine.model.DialogueNode

/**
 * Which of a node's choices are done or locked, from the story state alone. Shared by the story (StoryViewModel)
 * and everything that replays it (StoryRouteWalker: warps, the battle simulator), so they all see the same story.
 */
object StoryChoiceRules {

    /** How far a hub's branch is followed when checking whether it was played. */
    const val MAX_BRANCH_LENGTH = 40

    /**
     * Where a hub sends the player: once every flag its `hubCompletion` requires is set, on to the completion node,
     * but not while an open choice of its own (e.g. an unlocked battle) is still waiting to be chosen. Follows
     * chained hubs.
     */
    fun effectiveHub(node: DialogueNode, flags: Map<String, Boolean>, defeated: List<String>, visited: Set<String>): DialogueNode {
        var effective = node
        var guard = 0
        while (guard++ < 16) {
            val completion = effective.hubCompletion ?: break
            if (completion.requiredFlags.isEmpty() || !completion.requiredFlags.all { flags[it] == true }) break
            val hasPendingChoice = effective.choices.any { choice ->
                !isChoiceCompleted(choice, flags, defeated, effective, visited) && !isChoiceLocked(choice, effective, flags) &&
                    choice.nextNodeId != completion.redirectToNodeId
            }
            if (hasPendingChoice) break
            val redirect = StoryScript.ALL_NODES[completion.redirectToNodeId]
            if (redirect == null || redirect.id == effective.id) break
            effective = redirect
        }
        return effective
    }

    /** The choices of [node] still open: not done and not locked. */
    fun eligible(node: DialogueNode, flags: Map<String, Boolean>, defeated: List<String>, visited: Set<String>): List<DialogueChoice> =
        node.choices.filter { !isChoiceCompleted(it, flags, defeated, node, visited) && !isChoiceLocked(it, node, flags) }

    /**
     * Whether [choice] (an option of [node]) is done: its completion flag is set; or, among a real hub's options (a
     * node with `hubCompletion`), its battle is won or the player has played its branch and the branch has nothing
     * left to choose. A branch is a run of plain
     * nodes leading back to [node]: played once its first node was visited or a flag it sets is set. A branch
     * into a sub-hub is played once that sub-hub is finished (all its required flags set).
     */
    fun isChoiceCompleted(
        choice: DialogueChoice,
        flags: Map<String, Boolean>,
        defeated: List<String>,
        node: DialogueNode?,
        visited: Set<String>
    ): Boolean {
        if (choice.completionFlag != null && flags[choice.completionFlag] == true) {
            return true
        }
        // Beyond its own flag, a choice is a task to tick off only among a real hub's options (a node with
        // hubCompletion): a plain fork's options aren't, and a battle a story repeats (each day's rush) stays open.
        val inHub = node == null || node.hubCompletion != null
        val target = StoryScript.ALL_NODES[choice.nextNodeId]
        if (inHub && target?.triggerBattleEncounterId != null && defeated.contains(target.triggerBattleEncounterId)) {
            return true
        }
        return node?.hubCompletion != null && isBranchPlayed(choice, node, flags, visited)
    }

    private fun isBranchPlayed(choice: DialogueChoice, hub: DialogueNode, flags: Map<String, Boolean>, visited: Set<String>): Boolean {
        val path = mutableListOf<DialogueNode>()
        var id: String? = choice.nextNodeId
        repeat(MAX_BRANCH_LENGTH) {
            val n = StoryScript.ALL_NODES[id] ?: return false
            if (n.id == hub.id) {
                // Back at the hub with nothing chosen on the way: played if the player went down it.
                return path.isNotEmpty() && (path.first().id in visited || path.any { p -> p.setFlagOnEnter?.let { flags[it] == true } == true })
            }
            if (n.choices.isNotEmpty()) {
                val sub = n.hubCompletion ?: return false
                return n.id in visited && sub.requiredFlags.isNotEmpty() && sub.requiredFlags.all { flags[it] == true }
            }
            if (n.triggerBattleEncounterId != null || n.triggerMiniGameId != null || n.changeSceneId != null) return false
            path += n
            id = n.nextNodeIdFor(flags)
        }
        return false
    }

    fun isChoiceLocked(choice: DialogueChoice, node: DialogueNode, flags: Map<String, Boolean>): Boolean {
        // 1. Explicit choice required flags
        if (choice.requiredFlags.isNotEmpty() && !choice.requiredFlags.all { flags[it] == true }) {
            return true
        }
        // 2. Hub gating: In a hub node (declaring hubCompletion), all dialogue options
        // (choices with completionFlag that do not trigger battle) must be completed
        // before advancing choices (e.g. battles, scene transitions) are unlocked.
        // A way on (a battle, a new scene, or no completion flag) is never one of the dialogue choices it waits for,
        // so it can't wait on itself (a camp's "rest and move on" with its own flag used to lock the whole node).
        val completion = node.hubCompletion
        fun isWayOn(c: DialogueChoice): Boolean {
            val target = StoryScript.ALL_NODES[c.nextNodeId]
            return c.completionFlag == null || target?.triggerBattleEncounterId != null || target?.changeSceneId != null
        }

        if (completion != null && isWayOn(choice)) {
            val dialogueChoices = node.choices.filterNot(::isWayOn)
            if (dialogueChoices.isNotEmpty()) {
                val allDialogueDone = dialogueChoices.all { flags[it.completionFlag] == true }
                if (!allDialogueDone) {
                    return true
                }
            }
        }
        return false
    }

}
