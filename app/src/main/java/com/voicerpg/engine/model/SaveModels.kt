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
    val voiceKeywords: List<String> = emptyList(),
    /**
     * A fixed protagonist for this track (perspective games: each track is told by its own hero).
     * With manifest creationMode "preset_select", character creation becomes a pick between the tracks'
     * heroes; choosing one sets the hero and the track together.
     */
    val hero: TrackHeroPreset? = null
)

/** The protagonist a campaign track starts with, and how its select card presents them. All optional. */
data class TrackHeroPreset(
    val name: String = "",
    val title: String = "",
    val heroClassId: String = "",
    val portraitAsset: String? = null,
    /** AuraColor name, e.g. SOLAR_GOLD. */
    val auraColor: String? = null,
    // Select card flavour
    val alias: String = "",
    val slogan: String = "",
    val playstyle: String = "",
    val featuredMoves: List<String> = emptyList(),
    val accentColor: String = "#FFD700",
    val secondaryColor: String = "#B71C1C",
    /** Clips played when the hero is picked; [soundBiteLabel] captions them on the card. */
    val soundBites: List<String> = emptyList(),
    val soundBiteLabel: String = ""
) {
    /** The player this preset produces on [track], with [fallback] filling anything the preset leaves out. */
    fun toCustomization(track: CampaignTrackDef, fallback: PlayerCustomization): PlayerCustomization = fallback.copy(
        name = name.ifBlank { fallback.name },
        title = title.ifBlank { fallback.title },
        heroClassId = heroClassId.ifBlank { fallback.heroClassId },
        auraColor = auraColor?.let { a -> AuraColor.entries.firstOrNull { it.name.equals(a, ignoreCase = true) } } ?: fallback.auraColor,
        customPortraitAsset = portraitAsset ?: fallback.customPortraitAsset,
        campaignTrackId = track.id
    )
}

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
                spellIds = member.spells.map { it.id }
            )
        }
    }
}

data class GameSaveData(
    /** Format version; saves older than [CURRENT_VERSION] are cleared on load (see SaveManager). */
    val saveVersion: Int = CURRENT_VERSION,
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
    /** Before the split into callouts and reports: the single combat narration toggle (old saves fall back on it). */
    val isCombatNarrationEnabled: Boolean = true,
    /** Options → Spell callouts; null in a save from before the split. */
    val isSpellCalloutsEnabled: Boolean? = null,
    /** Options → Battle reports; null in a save from before the split. */
    val isBattleReportsEnabled: Boolean? = null,
    val isPocketGuardEnabled: Boolean = true,
    val isReadChoicesEnabled: Boolean = true,
    val isAutoReadHubChoices: Boolean = true,
    val speechRate: Float = 1.05f,
    val isCharacterPitchEnabled: Boolean = true,
    val isSpeakerAttributionEnabled: Boolean = true,
    val isMusicEnabled: Boolean = true,
    val musicVolume: Float = 0.55f,
    val isReduceMotion: Boolean = false,
    val isReduceFlashing: Boolean = false,

    // 7. Per-speaker TTS voice assignments (installed Android voice model names keyed by speaker id)
    val voiceAssignments: Map<String, String> = emptyMap(),

    // 8. Dynamic Simulation & Metric Tracking (Data-driven and opt-in per game)
    val dynamicMetrics: Map<String, Float> = emptyMap(),

    // 9. Multilingual Localization Options
    val selectedLanguage: String = "en",
    val selectedRegion: String = ""
) {
    /** The saved spell callouts setting; a save from before the split uses its single combat narration toggle. */
    val spellCallouts: Boolean get() = isSpellCalloutsEnabled ?: isCombatNarrationEnabled

    /** The saved battle reports setting; a save from before the split uses its single combat narration toggle. */
    val battleReports: Boolean get() = isBattleReportsEnabled ?: isCombatNarrationEnabled

    companion object {
        /**
         * 2: levels and XP removed, spells worked out from story flags. Older saves (1, or 0 when the field is
         * missing) are cleared rather than converted.
         */
        const val CURRENT_VERSION = 2
    }
}
