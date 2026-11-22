package com.voicerpg.engine.engine

import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.DialogueNode
import com.voicerpg.engine.model.SavedCharacterStats
import com.voicerpg.engine.model.StoryScene

data class QuestMilestone(
    val chapterIndex: Int,
    val actNumber: Int,
    val title: String,
    val location: String,
    val summary: String,
    val icon: String = "✨",
    val isCurrent: Boolean = false
)

data class QuestRecap(
    val currentActTitle: String,
    val currentChapterTitle: String,
    val currentSceneName: String,
    val activeObjective: String,
    val partyRoster: List<String>,
    val milestones: List<QuestMilestone>,
    val spokenRecap: String
) {
    /** Legacy alias kept for callers/tests that read the roster by its old name. */
    val fellowshipRoster: List<String> get() = partyRoster
}

/**
 * Generates an episodic "Previously on your quest..." narrative recap chronicling the
 * major milestones achieved up to the player's active position in the story.
 * Fully data-driven: chapters, acts, milestones, and objectives come from the scene
 * metadata declared in assets/game/story/scenes.json — no story content is hardcoded.
 */
object StoryRecapEngine {

    fun getChapterIndex(scene: StoryScene, node: DialogueNode): Int = scene.chapterIndex

    fun getActNumber(scene: StoryScene): Int = scene.actNumber

    fun getActTitle(scene: StoryScene): String = scene.actTitle

    fun getActiveObjective(scene: StoryScene, node: DialogueNode, flags: Map<String, Boolean>): String {
        if (node.triggerBattleEncounterId != null) {
            return "Defeat the enemies threatening the party!"
        }
        return scene.defaultObjective.ifBlank { "Proceed along the path ahead." }
    }

    fun getPartyRoster(partyStats: List<SavedCharacterStats>): List<String> {
        if (partyStats.isNotEmpty()) {
            return partyStats.map { "${it.name} (${it.loreClass})" }
        }
        val hero = GameContent.pack.heroCharacter
        return listOf("${hero?.speaker?.name ?: "Hero"} (${hero?.loreClass ?: "Adventurer"})")
    }

    fun buildRecap(
        scene: StoryScene,
        node: DialogueNode,
        flags: Map<String, Boolean>,
        partyStats: List<SavedCharacterStats>
    ): QuestRecap {
        val currentIndex = getChapterIndex(scene, node)
        val actTitle = getActTitle(scene)
        val objective = getActiveObjective(scene, node, flags)
        val roster = getPartyRoster(partyStats)

        val milestoneScenes = GameContent.scenes.values
            .filter { it.chapterIndex <= currentIndex && it.milestoneSummary.isNotBlank() }
            .distinctBy { it.chapterIndex }
            .sortedBy { it.chapterIndex }

        val milestones = milestoneScenes.map { milestoneScene ->
            val isCurrent = milestoneScene.chapterIndex == currentIndex
            QuestMilestone(
                chapterIndex = milestoneScene.chapterIndex,
                actNumber = milestoneScene.actNumber,
                title = milestoneScene.milestoneTitle.ifBlank { milestoneScene.chapterTitle },
                location = if (isCurrent) scene.name else milestoneScene.milestoneLocation.ifBlank { milestoneScene.name },
                summary = milestoneScene.milestoneSummary,
                icon = milestoneScene.milestoneIcon,
                isCurrent = isCurrent
            )
        }

        val spoken = generateSpokenRecap(
            chapterTitle = scene.chapterTitle,
            sceneName = scene.name,
            objective = objective,
            roster = roster,
            milestones = milestones
        )

        return QuestRecap(
            currentActTitle = actTitle,
            currentChapterTitle = scene.chapterTitle,
            currentSceneName = scene.name,
            activeObjective = objective,
            partyRoster = roster,
            milestones = milestones,
            spokenRecap = spoken
        )
    }

    private fun generateSpokenRecap(
        chapterTitle: String,
        sceneName: String,
        objective: String,
        roster: List<String>,
        milestones: List<QuestMilestone>
    ): String {
        val previousMilestone = milestones.filter { !it.isCurrent }.lastOrNull()
        val milestoneSentence = if (previousMilestone != null) {
            "Previously on your quest: ${previousMilestone.summary}"
        } else {
            "Your quest has just begun."
        }

        val companionText = if (roster.size > 1) {
            "Standing with you: " + roster.drop(1).joinToString(", ") + "."
        } else {
            "You journey alone."
        }

        return "Chronicle recap. You are in $chapterTitle, at $sceneName. " +
            "$milestoneSentence " +
            "$companionText " +
            "Current objective: $objective. " +
            "What is your command?"
    }
}
