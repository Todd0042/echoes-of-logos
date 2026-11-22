package com.voicerpg.engine.engine

import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.DialogueNode
import com.voicerpg.engine.model.DialogueSpeaker
import com.voicerpg.engine.model.StoryScene

/**
 * Story content access facade. ALL scenes, dialogue nodes, and speakers are
 * loaded from the active game's data files (assets/game/story/<name>.json and
 * assets/game/characters/<name>.json) through [GameContent]. The engine itself
 * contains no hardcoded storylines, chapters, or characters.
 */
object StoryScript {

    val ALL_SCENES: Map<String, StoryScene> get() = GameContent.scenes

    val ALL_NODES: Map<String, DialogueNode> get() = GameContent.nodes

    val ALL_SPEAKERS: List<DialogueSpeaker> get() = GameContent.allSpeakers

    val INITIAL_SCENE: StoryScene get() = GameContent.initialScene

    val INITIAL_NODE: DialogueNode get() = GameContent.initialNode
}
