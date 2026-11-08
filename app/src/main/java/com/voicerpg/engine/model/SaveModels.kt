package com.voicerpg.engine.model

import com.voicerpg.engine.content.GameContent

enum class AuraColor(
    val id: String,
    val displayName: String,
    val hexColor: String,
    val description: String
) {
    SAPPHIRE_FROST("frost", "Sapphire Frost", "#00E5FF", "Cool cyan luminescence"),
    SOLAR_GOLD("gold", "Solar Gold", "#FFD700", "Radiant dawn brilliance"),
    CRIMSON_PYRE("fire", "Crimson Pyre", "#FF5252", "Fierce incandescent flame"),
    EMERALD_GROVE("verdant", "Emerald Grove", "#69F0AE", "Gentle living earth"),
    AMETHYST_VOID("shadow", "Amethyst Void", "#E040FB", "Mysterious umbral frequency")
}

/**
 * Data-driven storyline track definition (e.g. Track A, Track B).
 * Games with multiple storylines or perspectives configure these in manifest or GameContent.
 */
data class CampaignTrackDef(
    val id: String = "track_a",
    val displayName: String = "Main Campaign",
    val subtitle: String = "",
    val startNodeId: String = "",
    val startSceneId: String = "",
    val rosterDescription: String = "",
    val badgeText: String = "1P",
    val voiceKeywords: List<String> = emptyList()
)

/**
 * Player-created hero identity. Hero classes are data-driven: [heroClassId]
 * resolves against assets/game/classes/<name>.json via the content registry.
 */
data class PlayerCustomization(
    val name: String = "Hero",
    val heroClassId: String = "",
    val title: String = "The Awakened Hero",
    val auraColor: AuraColor = AuraColor.SAPPHIRE_FROST,
    val voiceAffinityBonusSchool: SpellSchool = SpellSchool.PHYSICAL,
    val customPortraitAsset: String? = null,
    val campaignTrackId: String = "track_a"
) {
    val heroClass get() = GameContent.classById(heroClassId)
}

data class SavedCharacterStats(
    val id: String,
    val name: String,
    val loreClass: String,
    val currentHp: Int,
    val maxHp: Int,
    val currentMp: Int,
    val maxMp: Int,
    val speed: Int,
    val level: Int = 1,
    val xp: Int = 0,
    val spellIds: List<String> = emptyList()
) {
    companion object {
        fun fromPartyMember(member: PartyMember): SavedCharacterStats {
            return SavedCharacterStats(
                id = member.id,
                name = member.name,
                loreClass = member.loreClass,
                currentHp = member.currentHp,
                maxHp = member.maxHp,
                currentMp = member.currentMp,
                maxMp = member.maxMp,
                speed = member.speed,
                level = member.level,
                xp = member.xp,
                spellIds = member.spells.map { it.id }
            )
        }
    }
}

data class GameSaveData(
    val saveVersion: Int = 1,
    val saveTimestamp: Long = System.currentTimeMillis(),
    val totalPlaytimeSeconds: Long = 0L,

    // 1. Player Profile & Initial Loadout
    val player: PlayerCustomization = PlayerCustomization(),

    // 2. Story Progress & Current Scene/Dialogue (empty = resolve to manifest initial values)
    val currentSceneId: String = "",
    val currentNodeId: String = "",

    // 3. Complete Decision History (every choice ever selected by player)
    val decisionsMade: List<String> = emptyList(),
    val narrativeFlags: Map<String, Boolean> = emptyMap(),
    val visitedNodeIds: List<String> = emptyList(),
    val recentDialogueLog: List<DialogueLogEntry> = emptyList(),

    // 4. Current Stats for all Party Members
    val partyStats: List<SavedCharacterStats> = emptyList(),

    // 5. Accomplishments & Milestones
    val defeatedEncounters: List<String> = emptyList(),
    val unlockedCompanions: List<String> = listOf("hero"),
    val achievements: List<String> = emptyList(),
    val totalDamageDealt: Long = 0L,
    val totalBattlesWon: Int = 0,
    val highestResonanceTier: String = "BASIC",

    // 6. User Accessibility & Audio Options
    val isEyesFreeMode: Boolean = false,
    val isAutoListen: Boolean = false,
    val isAutoPlayEnabled: Boolean = false,
    val isChimeMuted: Boolean = true,
    val isNarrationEnabled: Boolean = true,
    val isCombatNarrationEnabled: Boolean = true,
    val isPocketGuardEnabled: Boolean = true,
    val isReadChoicesEnabled: Boolean = true,
    val isAutoReadHubChoices: Boolean = true,
    val speechRate: Float = 1.05f,
    val isCharacterPitchEnabled: Boolean = true,
    val isSpeakerAttributionEnabled: Boolean = true,
    val isMusicEnabled: Boolean = true,
    val musicVolume: Float = 0.55f,

    // 7. Per-speaker TTS voice assignments (installed Android voice model names keyed by speaker id)
    val voiceAssignments: Map<String, String> = emptyMap(),

    // 8. Dynamic Simulation & Metric Tracking (Data-driven and opt-in per game)
    val dynamicMetrics: Map<String, Float> = emptyMap(),

    // 9. Multilingual Localization Options
    val selectedLanguage: String = "en",
    val selectedRegion: String = ""
)
