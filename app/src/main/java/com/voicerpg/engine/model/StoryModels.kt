package com.voicerpg.engine.model

import androidx.compose.ui.graphics.Color
import com.voicerpg.engine.content.GameContent

enum class SpeakerSide {
    LEFT,
    RIGHT,
    CENTER_NARRATOR
}

/**
 * A dialogue speaker bound to a character data file (assets/game/characters/<name>.json).
 * The engine never hardcodes game characters: every speaker is resolved from content
 * files by id (story nodes reference speakerId, saves store per-speaker voice assignments,
 * and the audio setup screen binds installed TTS voices to these ids).
 */
data class DialogueSpeaker(
    val id: String,
    val name: String,
    val title: String,
    val portraitAsset: String?,
    val themeColor: Color,
    val ttsPitch: Float = 1.0f,
    val samplePhrase: String? = null,
    val gender: String? = null,
    val allowedVoiceRegions: List<String> = emptyList()
) {
    companion object {
        /**
         * Engine built-in fallback narrator, used only until the active game's content
         * files are loaded. Games should ship their own narrator definition in
         * assets/game/characters/narrator.json.
         */
        val NARRATOR = DialogueSpeaker(
            id = "narrator",
            name = "Narrator",
            title = "Chronicle",
            portraitAsset = "portraits/template_narrator.jpg",
            themeColor = Color(0xFFFFD700),
            ttsPitch = 1.0f,
            samplePhrase = "The chronicle unfolds with every step."
        )
    }

    val isNarrator: Boolean get() = id == NARRATOR.id

    fun effectivePortraitAsset(seed: Any? = null): String? = portraitAsset
}

data class DialogueChoice(
    val id: String,
    val text: String,
    val voiceKeywords: List<String>,
    val nextNodeId: String,
    val completionFlag: String? = null,
    val requiredFlags: List<String> = emptyList(),
    val metricDeltas: Map<String, Float> = emptyMap(),
    val assignHeroClassId: String? = null,
    val triggerMiniGameId: String? = null
)

/**
 * Generic hub auto-redirect: when every flag in [requiredFlags] is set, entering the
 * hub node transparently reroutes to [redirectToNodeId] (choice-elimination endgame).
 */
data class HubCompletion(
    val requiredFlags: List<String>,
    val redirectToNodeId: String
)

data class DialogueNode(
    val id: String,
    val speaker: DialogueSpeaker,
    val side: SpeakerSide,
    val text: String,
    val revisitText: String? = null,
    val choices: List<DialogueChoice> = emptyList(),
    val nextNodeId: String? = null,
    val triggerBattleEncounterId: String? = null,
    val triggerMiniGameId: String? = null,
    val changeSceneId: String? = null,
    val setFlagOnEnter: String? = null,
    val hubCompletion: HubCompletion? = null,
    /** "half" or "full": restores party HP/MP when the node is entered. */
    val healPartyOnEnter: String? = null,
    val metricDeltas: Map<String, Float> = emptyMap(),
    val metricOverrides: Map<String, Float> = emptyMap(),
    val assignHeroClassId: String? = null,
    val allowIncidents: Boolean = true,
    val blacklistIncidents: Boolean = false
)

data class StoryScene(
    val id: String,
    val name: String,
    val chapterTitle: String,
    val backgroundAsset: String,
    val initialNodeId: String,
    val ambientDescription: String,
    val musicAsset: String = "audio/music/bgm_exploration.ogg",
    // --- Recap / chapter-warp metadata (data-driven; no engine hardcoding) ---
    val chapterIndex: Int = 0,
    val actNumber: Int = 1,
    val actTitle: String = "Act I",
    val milestoneTitle: String = "",
    val milestoneLocation: String = "",
    val milestoneSummary: String = "",
    val milestoneIcon: String = "✨",
    val defaultObjective: String = ""
)

enum class GameScreen {
    LANGUAGE_SELECTION,
    DIALECT_SELECTION,
    LANGUAGE_DOWNLOAD_STANDBY,
    TITLE,
    TUTORIAL,
    AUDIO_SETUP,
    CHARACTER_CREATION,
    STORY_EXPLORATION,
    COMBAT_ARENA,
    MINI_GAME,
    ASSET_VIEWER
}

data class SaveSummary(
    val slotIndex: Int = 1,
    val heroName: String,
    val heroClassTitle: String,
    val chapterTitle: String,
    val sceneName: String,
    val partySize: Int,
    val timestamp: Long,
    val dynamicMetrics: Map<String, Float> = emptyMap()
)

data class SaveSlotInfo(
    val slotIndex: Int,
    val summary: SaveSummary?
) {
    val isEmpty: Boolean get() = summary == null
}

data class DialogueLogEntry(
    val speakerId: String,
    val speakerName: String,
    val speakerTitle: String,
    val text: String,
    val sceneName: String,
    val isNarrator: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun speakerThemeColor(): Color {
        val speaker = GameContent.speakerById(speakerId)
        return speaker?.themeColor ?: if (isNarrator) Color(0xFFFFD700) else Color.White
    }

    fun speakerPortraitAsset(): String? {
        return GameContent.speakerById(speakerId)?.portraitAsset
    }
}
