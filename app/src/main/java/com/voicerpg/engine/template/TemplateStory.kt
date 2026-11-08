package com.voicerpg.engine.template

import androidx.compose.ui.graphics.Color
import com.voicerpg.engine.model.DialogueChoice
import com.voicerpg.engine.model.DialogueNode
import com.voicerpg.engine.model.DialogueSpeaker
import com.voicerpg.engine.model.SpeakerSide
import com.voicerpg.engine.model.StoryScene

/**
 * ========================================================================
 * CREATOR TEMPLATE: Story Scenes, Linear Narration & Choice Hubs
 * ========================================================================
 * Demonstrates:
 * 1. StoryScene: Atmospheric background graphic + background music asset.
 * 2. Linear Narration: Chronicle storytelling spoken aloud via TTS.
 * 3. Character Dialogue: Dialogue with custom portrait, title, and speech pitch.
 * 4. Narration with Choices: Interactive choice hub with speech triggers and auto-elimination flags.
 * 5. Combat Transition: Triggering battle encounters directly from narrative nodes.
 * 6. Post-Battle Continuation: Victory dialogue node returning to the narrative flow.
 */
object TemplateStory {

    // --- Custom Speaker Definition ---
    val TEMPLATE_MENTOR = DialogueSpeaker(
        id = "template_mentor",
        name = "Grandmaster Vael",
        title = "Chamber Warden",
        portraitAsset = "portraits/template_hero.jpg",
        themeColor = Color(0xFFFFB74D),
        ttsPitch = 0.85f
    )

    // --- Scene Definition ---
    val TEMPLATE_SCENE = StoryScene(
        id = "template_proving_grounds",
        name = "The Proving Grounds",
        chapterTitle = "Sandbox: The Voice Sanctuary",
        backgroundAsset = "environments/cave_bg.jpg",
        initialNodeId = "template_linear_narration",
        ambientDescription = "An ancient vaulted chamber where stone glyphs hum in resonance with human incantations.",
        musicAsset = "audio/music/bgm_exploration.ogg"
    )

    // --- 1. Linear Narration Template ---
    val NODE_LINEAR_NARRATION = DialogueNode(
        id = "template_linear_narration",
        speaker = DialogueSpeaker.NARRATOR,
        side = SpeakerSide.CENTER_NARRATOR,
        text = "Deep beneath the citadel, the stone floor hums with forgotten frequency. Grandmaster Vael stands before the proving pedestal, awaiting your vocal incantation.",
        nextNodeId = "template_mentor_greeting"
    )

    val NODE_MENTOR_GREETING = DialogueNode(
        id = "template_mentor_greeting",
        speaker = TEMPLATE_MENTOR,
        side = SpeakerSide.LEFT,
        text = "Welcome, apprentice. In this sanctuary, your spoken voice is both blade and aegis. Inquire of the ancient ways, or step forward into combat.",
        nextNodeId = "template_choice_hub"
    )

    // --- 2. Narration with Choices Template (Hub with Choice Elimination) ---
    val NODE_CHOICE_HUB = DialogueNode(
        id = "template_choice_hub",
        speaker = TEMPLATE_MENTOR,
        side = SpeakerSide.LEFT,
        text = "What is your command? Speak a choice aloud or tap the screen.",
        choices = listOf(
            DialogueChoice(
                id = "choice_history",
                text = "Ask about the history of the Proving Grounds.",
                voiceKeywords = listOf("history", "grounds", "lore", "past", "ancient"),
                nextNodeId = "template_branch_history",
                completionFlag = "flag_template_history_read"
            ),
            DialogueChoice(
                id = "choice_blessing",
                text = "Receive the Grandmaster's tactical counsel.",
                voiceKeywords = listOf("blessing", "counsel", "advice", "tactical", "help"),
                nextNodeId = "template_branch_blessing",
                completionFlag = "flag_template_blessing_received"
            ),
            DialogueChoice(
                id = "choice_solo_battle",
                text = "Begin Single-Enemy Combat Trial (1 Drone).",
                voiceKeywords = listOf("single", "solo", "one enemy", "drone", "first trial"),
                nextNodeId = "template_trigger_solo_combat"
            ),
            DialogueChoice(
                id = "choice_trio_battle",
                text = "Begin Squad Combat Trial (3 Drones).",
                voiceKeywords = listOf("squad", "trio", "three enemies", "group", "second trial"),
                nextNodeId = "template_trigger_trio_combat"
            )
        )
    )

    // Sub-Branch: History
    val NODE_BRANCH_HISTORY = DialogueNode(
        id = "template_branch_history",
        speaker = TEMPLATE_MENTOR,
        side = SpeakerSide.LEFT,
        text = "Centuries ago, the first invocators discovered that acoustic clarity directly amplifies elemental force. Every crisp syllable shatters enemy armor.",
        setFlagOnEnter = "flag_template_history_read",
        nextNodeId = "template_choice_hub"
    )

    // Sub-Branch: Tactical Blessing
    val NODE_BRANCH_BLESSING = DialogueNode(
        id = "template_branch_blessing",
        speaker = TEMPLATE_MENTOR,
        side = SpeakerSide.LEFT,
        text = "Remember: your Flame Spark hits ice-attuned automatons for double damage, while Power Strike penetrates physical shielding.",
        setFlagOnEnter = "flag_template_blessing_received",
        nextNodeId = "template_choice_hub"
    )

    // --- 3. Combat Triggers ---
    val NODE_TRIGGER_SOLO_COMBAT = DialogueNode(
        id = "template_trigger_solo_combat",
        speaker = DialogueSpeaker.NARRATOR,
        side = SpeakerSide.CENTER_NARRATOR,
        text = "A single training drone hums to life, gears spinning as it targets the fellowship!",
        triggerBattleEncounterId = TemplateEncounters.ENCOUNTER_SOLO_ID,
        nextNodeId = "template_post_victory"
    )

    val NODE_TRIGGER_TRIO_COMBAT = DialogueNode(
        id = "template_trigger_trio_combat",
        speaker = DialogueSpeaker.NARRATOR,
        side = SpeakerSide.CENTER_NARRATOR,
        text = "Three automated drones deploy in synchronized combat formation!",
        triggerBattleEncounterId = TemplateEncounters.ENCOUNTER_TRIO_ID,
        nextNodeId = "template_post_victory"
    )

    // --- 4. Post-Battle Victory Continuation ---
    val NODE_POST_VICTORY = DialogueNode(
        id = "template_post_victory",
        speaker = TEMPLATE_MENTOR,
        side = SpeakerSide.LEFT,
        text = "Outstanding performance! Your incantations were resolute and your timing flawless. The Proving Grounds yield to your voice.",
        nextNodeId = "template_conclusion"
    )

    val NODE_CONCLUSION = DialogueNode(
        id = "template_conclusion",
        speaker = DialogueSpeaker.NARRATOR,
        side = SpeakerSide.CENTER_NARRATOR,
        text = "The trial is complete. You now possess the blueprint to create new chapters, heroes, spells, and battles. Returning to the Proving Grounds hub.",
        nextNodeId = "template_choice_hub"
    )

    val ALL_TEMPLATE_NODES = mapOf(
        NODE_LINEAR_NARRATION.id to NODE_LINEAR_NARRATION,
        NODE_MENTOR_GREETING.id to NODE_MENTOR_GREETING,
        NODE_CHOICE_HUB.id to NODE_CHOICE_HUB,
        NODE_BRANCH_HISTORY.id to NODE_BRANCH_HISTORY,
        NODE_BRANCH_BLESSING.id to NODE_BRANCH_BLESSING,
        NODE_TRIGGER_SOLO_COMBAT.id to NODE_TRIGGER_SOLO_COMBAT,
        NODE_TRIGGER_TRIO_COMBAT.id to NODE_TRIGGER_TRIO_COMBAT,
        NODE_POST_VICTORY.id to NODE_POST_VICTORY,
        NODE_CONCLUSION.id to NODE_CONCLUSION
    )
}
