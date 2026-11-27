package com.voicerpg.engine.content

import android.content.Context
import android.content.res.AssetManager
import androidx.compose.ui.graphics.Color
import java.util.Locale
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.voicerpg.engine.combat.EnemyMove
import com.voicerpg.engine.combat.Moveset
import com.voicerpg.engine.combat.PhaseRule
import com.voicerpg.engine.combat.StatusDef
import com.voicerpg.engine.combat.StatusTier
import com.voicerpg.engine.model.SpellGraphics
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.DialogueChoice
import com.voicerpg.engine.model.DialogueNode
import com.voicerpg.engine.model.FlagRoute
import com.voicerpg.engine.model.DialogueSpeaker
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.EncounterDefinition
import com.voicerpg.engine.model.BossPhaseRule
import com.voicerpg.engine.model.MidBattleJoin
import com.voicerpg.engine.model.HubCompletion
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.SavedCharacterStats
import com.voicerpg.engine.model.SelfieFilterConfig
import com.voicerpg.engine.model.SpeakerSide
import com.voicerpg.engine.model.Spell
import com.voicerpg.engine.model.SpellSchool
import com.voicerpg.engine.model.StoryScene
import com.voicerpg.engine.model.MetricDefinition
import com.voicerpg.engine.model.IncidentDefinition
import com.voicerpg.engine.model.IncidentCondition
import com.voicerpg.engine.model.IncidentConditionType
import com.voicerpg.engine.model.IncidentWeightMultiplier
import com.voicerpg.engine.model.MiniGameConfig
import com.voicerpg.engine.model.MiniGameOutcomeTier
import com.voicerpg.engine.ui.sprites.SpriteFrame
import java.io.File

// =============================================================================
// JSON SCHEMA DTOs (assets/game/**)
// Every field has a default so Gson tolerates missing keys in content files.
// =============================================================================

data class ManifestDto(
    /** 1 = the game opts in to the sprite contract (docs/SPRITE_PLAN.md); a release build checks it regardless. */
    val spriteContract: Int = 0,
    /** "mana" (default): spells cost MP and fighters attune. "none": no MP, no costs, no attune anywhere. */
    val combatResource: String = "mana",
    val gameTitle: String = "VoiceRPG Engine",
    val gameSubtitle: String = "",
    val worldName: String = "the realm",
    val initialSceneId: String = "",
    val initialNodeId: String = "",
    val defaultEncounterId: String = "",
    val startingAchievementId: String = "QUEST_BEGUN",
    val victoryLine: String = "Victory! The battle is won.",
    val defeatLine: String = "Defeat. Your voice fades.",
    /** Battle result overlay: titles and the continue-story button labels. */
    val victoryTitle: String = "⚔️ VICTORY ⚔️",
    val defeatTitle: String = "💀 DEFEAT 💀",
    val victoryContinueLabel: String = "CONTINUE STORY ➔",
    val defeatContinueLabel: String = "PRESS ON ➔",
    val titleBackgroundAsset: String? = null,
    val defaultHeroName: String = "Hero",
    val defaultHeroTitle: String = "Adventurer",
    val quickNames: List<String>? = null,
    val characterTitles: List<String>? = null,
    val creationTitle: String? = null,
    val creationSubtitle: String? = null,
    val creationEmbarkPrompt: String = "AWAKEN ➔",
    val creationVoiceKeywords: List<String>? = null,
    val defaultHeroPortrait: String? = null,
    val tutorialCompletionText: String = "",
    val voicePermissionTitle: String = "",
    val voicePermissionSubtitle: String = "",
    val voicePermissionPrompt: String = "",
    val resonance: ResonanceProfileDto? = null,
    val ttsReplacements: Map<String, String>? = null,
    val selfieConfig: SelfieFilterConfig? = null,
    val allowClassSelectionInCreation: Boolean? = null,
    val creationNameLabel: String? = null,
    val creationAestheticsLabel: String? = null,
    val creationStatusSubtitle: String? = null,
    val campaignTracks: List<com.voicerpg.engine.model.CampaignTrackDef>? = null,
    /** "custom" (name, class, portrait) or "preset_select" (pick one of the campaign tracks' heroes). */
    val creationMode: String? = null,
    /** The game's default music (asset paths); unset = the engine's audio/music/bgm_{title,exploration,combat}.ogg. */
    val titleMusic: String? = null,
    val explorationMusic: String? = null,
    val combatMusic: String? = null
)

data class ResonanceProfileDto(
    val mode: String = "CLASSIC",
    val topDamageAccessible: Boolean = true,
    val conciseCommandBonus: Boolean = false,
    val singleRootMaxBonus: Boolean = false,
    val bypassPoeticRequirement: Boolean = false,
    val urgencyBonus: Float = 0f,
    val acousticSensitivity: Float = 1.0f,
    val tierChants: Map<String, String>? = null
) {
    fun toProfile(): ResonanceProfile {
        val isSurv = mode.equals("SURVIVAL", ignoreCase = true)
        return ResonanceProfile(
            mode = if (isSurv) "SURVIVAL" else mode.uppercase(Locale.ROOT),
            topDamageAccessible = topDamageAccessible,
            conciseCommandBonus = conciseCommandBonus || isSurv,
            singleRootMaxBonus = singleRootMaxBonus || isSurv,
            bypassPoeticRequirement = bypassPoeticRequirement || isSurv,
            urgencyBonus = if (urgencyBonus > 0f) urgencyBonus else if (isSurv) 12f else 0f,
            acousticSensitivity = if (acousticSensitivity != 1.0f) acousticSensitivity else if (isSurv) 1.25f else 1.0f,
            tierChants = tierChants ?: emptyMap()
        )
    }
}

data class CharacterCombatDto(
    val maxHp: Int = 100,
    val maxMp: Int = 50,
    val speed: Int = 50,
    val defense: Int = 0,
    val avatarTint: String = "#4FC3F7",
    val startAtb: Float = 0f,
    val isGhost: Boolean = false,
    val canAttack: Boolean = true,
    val isUntargetable: Boolean = false,
    val spriteAlpha: Float? = null,
    val spellIds: List<String>? = null,
    val spells: List<SpellDto>? = null,
    /** Portrait panel for MYTHIC+ casts: { portrait, color, quote }. */
    val cutIn: com.voicerpg.engine.model.CutInSpec? = null
)

data class SpellGrantDto(
    val flag: String = "",
    val spellIds: List<String>? = null
)

data class SpriteFramesDto(
    @SerializedName(value = "idle_upright", alternate = ["idleUpright"])
    val idle_upright: List<String>? = null,
    @SerializedName(value = "idle_crouch", alternate = ["idleCrouch"])
    val idle_crouch: List<String>? = null,
    @SerializedName(value = "action_cast", alternate = ["actionCast"])
    val action_cast: List<String>? = null,
    val damaged: List<String>? = null,
    /** Optional frames (fall back to action_cast / damaged / damaged). */
    val attack: List<String>? = null,
    val hurt: List<String>? = null,
    val death: List<String>? = null
)

data class SpriteDefinitionDto(
    val palette: Map<String, String>? = null,
    val frames: SpriteFramesDto? = null
)

data class EnemySpritesFileDto(
    val sprites: Map<String, SpriteDefinitionDto>? = null
)

data class CharacterDto(
    val id: String = "",
    val role: String = "npc",
    val name: String = "",
    val title: String = "",
    val gender: String? = null,
    val portraitAsset: String? = null,
    val spriteId: String = "template_hero",
    val sprite: SpriteDefinitionDto? = null,
    val themeColor: String = "#FFFFFF",
    val ttsPitch: Float = 1.0f,
    val samplePhrase: String? = null,
    val preferredVoiceId: String? = null,
    val allowedVoiceRegions: List<String>? = null,
    val voiceAliases: List<String>? = null,
    /** Clips played when this character casts in battle (asset paths); one at random. */
    val soundBites: List<String>? = null,
    /**
     * Spoken in this character's voice when, on their turn, the player calls another hero's spell.
     * {owner} = that hero, {active} = this character. Without it the narrator explains whose turn it is.
     */
    val outOfTurnLine: String? = null,
    val loreClass: String = "Adventurer",
    val combat: CharacterCombatDto? = null,
    val spells: List<SpellDto>? = null,
    val joinWhenFlag: String? = null,
    val spellGrants: List<SpellGrantDto>? = null
)

data class ClassDto(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val startingHp: Int = 200,
    val startingMp: Int = 100,
    val startingSpeed: Int = 60,
    val startingBaseAttack: Int = 20,
    val preferredSchool: String = "PHYSICAL",
    val starterSpellIds: List<String>? = null,
    val voiceKeywords: List<String>? = null,
    val portraitAsset: String? = null,
    /** Spells the class learns as the story goes on (`{ "flag": ..., "spellIds": [...] }`); see [SpellKit]. */
    val spellGrants: List<SpellGrantDto>? = null
)

data class SpellGraphicsDto(
    val effectType: String? = null,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val particleCount: Int? = null,
    val screenShake: Float? = null
) {
    fun toSpellGraphics(): SpellGraphics = SpellGraphics(
        effectType = effectType ?: "PROJECTILE",
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        particleCount = particleCount ?: 16,
        screenShake = screenShake ?: 0f
    )
}

data class SpellDto(
    val id: String = "",
    val name: String = "",
    /** What the spell callout says when [name] is too long to call out ("Rent Freeze!"). */
    val calloutName: String? = null,
    val school: String = "PHYSICAL",
    val basePower: Int = 0,
    val mpCost: Int = 0,
    val isHeal: Boolean = false,
    val hitsAll: Boolean = false,
    val description: String = "",
    val exampleChant: String = "",
    val status: String? = null,
    val lifesteal: Boolean = false,
    val isGuard: Boolean = false,
    val manaRestorePct: Float = 0f,
    val aliases: List<String>? = null,
    val graphics: SpellGraphicsDto? = null,
    val cleansesDebuffs: Boolean = false,
    /** Optional chants per resonance tier ("BASIC".."TRANSCENDENTAL") used by hands-free presets. */
    val presetChants: Map<String, String>? = null,
    /** Optional recorded clips (asset paths, e.g. audio/quotes/x.ogg); one plays at random. */
    val soundBites: List<String>? = null
)

data class SpellFileDto(val spells: List<SpellDto>? = null)

data class EnemyMoveDto(
    val name: String = "",
    val school: String = "PHYSICAL",
    val kind: String = "BASIC",
    val powerMult: Float = 1.0f,
    val targetRule: String = "SINGLE_RANDOM",
    val applyStatus: String? = null,
    val cooldownTurns: Int = 0,
    val weight: Int = 1,
    val summon: String? = null,
    val summonCount: Int = 0,
    val graphics: SpellGraphicsDto? = null,
    /** Optional recorded clips (asset paths, e.g. audio/quotes/x.ogg); one plays at random. */
    val soundBites: List<String>? = null,
    val titleCard: Boolean? = null,
    val windupMs: Long? = null,
    val intentIcon: String? = null
) {
    fun toEnemyMove(): EnemyMove = EnemyMove(
        name = name.ifBlank { "Attack" },
        school = school,
        kind = kind,
        powerMult = powerMult,
        targetRule = targetRule,
        applyStatus = applyStatus,
        cooldownTurns = cooldownTurns,
        weight = weight,
        summon = summon,
        summonCount = summonCount,
        graphics = graphics?.toSpellGraphics(),
        soundBites = soundBites.orEmpty().filter { it.isNotBlank() },
        titleCard = titleCard,
        windupMs = windupMs?.coerceIn(0L, 3000L),
        intentIcon = intentIcon?.takeIf { it.isNotBlank() }
    )
}

data class PhaseRuleDto(
    val hpBelow: Float = 0.5f,
    val addMoves: List<EnemyMoveDto>? = null,
    val note: String = ""
) {
    fun toPhaseRule(): PhaseRule = PhaseRule(
        hpBelow = hpBelow,
        addMoves = addMoves?.map { it.toEnemyMove() } ?: emptyList(),
        note = note
    )
}

data class MovesetDto(
    val moves: List<EnemyMoveDto>? = null,
    val phases: List<PhaseRuleDto>? = null
) {
    fun toMoveset(): Moveset = Moveset(
        moves = moves?.map { it.toEnemyMove() } ?: emptyList(),
        phases = phases?.map { it.toPhaseRule() } ?: emptyList()
    )
}

data class EnemySpellsFileDto(
    val movesets: Map<String, MovesetDto>? = null
)

data class EnemyDto(
    val id: String = "",
    val name: String = "",
    /** How combat speech names it ("Orc" for "Blighted Orc"); blank = the last word of [name]. */
    val shortName: String? = null,
    val subtitle: String = "",
    val family: String = "FLESH",
    val selfElement: String = "",
    val hp: Int = 100,
    val attack: Int = 10,
    val defense: Int = 0,
    val speed: Int = 50,
    val movesetId: String = "",
    val isBoss: Boolean = false,
    val spriteId: String = "",
    val spriteTint: String = "#E57373",
    /** Extra words that target this enemy by voice ("warrior" for an orc), besides its name and subtitle. */
    val voiceAliases: List<String>? = null,
    /** Optional recorded clips (asset paths, e.g. audio/quotes/x.ogg); one plays at random. */
    val soundBites: List<String>? = null,
    /** Sprite size multiplier; bosses default to 2.0. */
    val scale: Float? = null,
    val taunts: List<String>? = null,
    /** flinch | stagger | comic_spin */
    val hitReaction: String? = null,
    val cutIn: com.voicerpg.engine.model.CutInSpec? = null
)

data class EnemyFileDto(val enemies: List<EnemyDto>? = null)

data class EncounterEnemyDto(
    val templateId: String = "",
    val id: String? = null,
    val name: String? = null,
    val subtitle: String? = null,
    val hp: Int? = null,
    val attack: Int? = null,
    val defense: Int? = null,
    val speed: Int? = null,
    val isTargeted: Boolean = false,
    val atbGauge: Float = 0f
)

data class BossPhaseDto(
    val enemyId: String = "",
    val hpBelow: Float = 0.5f,
    val bannerText: String = "",
    val narrationText: String = "",
    val reducePartyToHpPct: Float? = null,
    val executeBelowHpPct: Float? = null,
    val summonEnemyId: String? = null,
    val summonCount: Int = 0
)

data class MidBattleJoinDto(
    val characterId: String = "",
    val bannerText: String = "",
    val announcementText: String = "",
    val afterEnemyActions: Int = 1
)

data class EncounterDto(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val environment: String = "FOREST",
    val environmentId: String? = null,
    val party: List<String>? = null,
    val enemies: List<EncounterEnemyDto>? = null,
    val bossPhases: List<BossPhaseDto>? = null,
    val midBattleJoin: MidBattleJoinDto? = null,
    /** A second music track for when this fight turns desperate (crossfaded in). */
    val intensityTrack: String? = null
)

data class EncounterFileDto(val encounters: List<EncounterDto>? = null)

data class SceneDto(
    val id: String = "",
    val name: String = "",
    val chapterTitle: String = "",
    val backgroundAsset: String = "",
    val initialNodeId: String = "",
    val ambientDescription: String = "",
    val musicAsset: String = "audio/music/bgm_exploration.ogg",
    val chapterIndex: Int = 0,
    val actNumber: Int = 1,
    val actTitle: String = "Act I",
    val milestoneTitle: String = "",
    val milestoneLocation: String = "",
    val milestoneSummary: String = "",
    val milestoneIcon: String = "✨",
    val defaultObjective: String = ""
)

data class SceneFileDto(val scenes: List<SceneDto>? = null)

data class ChoiceDto(
    val id: String = "",
    val text: String = "",
    val voiceKeywords: List<String>? = null,
    val nextNodeId: String = "",
    val completionFlag: String? = null,
    val requiredFlags: List<String>? = null,
    val metricDeltas: Map<String, Float>? = null,
    val assignHeroClassId: String? = null,
    val triggerMiniGameId: String? = null
)

data class HubCompletionDto(
    val requiredFlags: List<String>? = null,
    val redirectToNodeId: String = ""
)

data class FlagRouteDto(
    val requiredFlags: List<String>? = null,
    val nextNodeId: String = ""
)

data class NodeDto(
    /** Optional automatic branching on story flags (see DialogueNode.flagRoutes). */
    val flagRoutes: List<FlagRouteDto>? = null,
    val id: String = "",
    val speakerId: String = "narrator",
    val side: String = "CENTER_NARRATOR",
    val text: String = "",
    val revisitText: String? = null,
    val choices: List<ChoiceDto>? = null,
    val nextNodeId: String? = null,
    val triggerBattleEncounterId: String? = null,
    val triggerMiniGameId: String? = null,
    val changeSceneId: String? = null,
    val setFlagOnEnter: String? = null,
    val hubCompletion: HubCompletionDto? = null,
    val healPartyOnEnter: String? = null,
    val metricDeltas: Map<String, Float>? = null,
    val metricOverrides: Map<String, Float>? = null,
    val assignHeroClassId: String? = null,
    val allowIncidents: Boolean? = null,
    val blacklistIncidents: Boolean? = null
)

data class NodeFileDto(val nodes: List<NodeDto>? = null)

data class MetricDto(
    val id: String = "",
    val label: String = "",
    val icon: String = "",
    val minValue: Float = 0f,
    val maxValue: Float = 100f,
    val initialValue: Float = 50f,
    val isDangerHigh: Boolean = false,
    val displayColor: String? = null
) {
    fun toDefinition(): MetricDefinition = MetricDefinition(
        id = id,
        label = label,
        icon = icon,
        minValue = minValue,
        maxValue = maxValue,
        initialValue = initialValue,
        isDangerHigh = isDangerHigh,
        displayColor = displayColor
    )
}

data class MetricFileDto(val metrics: List<MetricDto>? = null)

data class IncidentConditionDto(
    val type: String = "HAS_FLAG",
    val flag: String? = null,
    val metricId: String? = null,
    val threshold: Float = 0f,
    val nodeId: String? = null
) {
    fun toCondition(): IncidentCondition = IncidentCondition(
        type = runCatching { IncidentConditionType.valueOf(type) }.getOrDefault(IncidentConditionType.HAS_FLAG),
        flag = flag,
        metricId = metricId,
        threshold = threshold,
        nodeId = nodeId
    )
}

data class IncidentMultiplierDto(
    val metricId: String = "",
    val threshold: Float = 0f,
    val multiplier: Float = 1f
) {
    fun toMultiplier(): IncidentWeightMultiplier = IncidentWeightMultiplier(
        metricId = metricId,
        threshold = threshold,
        multiplier = multiplier
    )
}

data class IncidentDto(
    val id: String = "",
    val title: String = "",
    val baseWeight: Float = 10f,
    val conditions: List<IncidentConditionDto>? = null,
    val weightMultipliers: List<IncidentMultiplierDto>? = null,
    val triggerNodeId: String? = null,
    val triggerEncounterId: String? = null,
    val oneTimeOnly: Boolean = false,
    val cooldownTurns: Int = 0
) {
    fun toDefinition(): IncidentDefinition = IncidentDefinition(
        id = id,
        title = title,
        baseWeight = baseWeight,
        conditions = (conditions ?: emptyList()).map { it.toCondition() },
        weightMultipliers = (weightMultipliers ?: emptyList()).map { it.toMultiplier() },
        triggerNodeId = triggerNodeId,
        triggerEncounterId = triggerEncounterId,
        oneTimeOnly = oneTimeOnly,
        cooldownTurns = cooldownTurns
    )
}

data class IncidentFileDto(val incidents: List<IncidentDto>? = null)

data class MiniGameDto(
    val id: String = "",
    val title: String = "",
    val department: String = "",
    val description: String = "",
    val durationSeconds: Int = 45,
    val targetSpeed: Float = 1.0f,
    val errorPenalty: Float = 10f,
    val quota: Int = 20,
    val metricDeltasOnTier: Map<String, Map<String, Float>>? = null,
    val blurbOnTier: Map<String, String>? = null
) {
    fun toConfig(): MiniGameConfig {
        val parsedDeltas = (metricDeltasOnTier ?: emptyMap()).mapNotNull { (tierStr, deltas) ->
            runCatching { MiniGameOutcomeTier.valueOf(tierStr.uppercase()) }.getOrNull()?.let { tier ->
                tier to deltas
            }
        }.toMap()

        val parsedBlurbs = (blurbOnTier ?: emptyMap()).mapNotNull { (tierStr, blurb) ->
            runCatching { MiniGameOutcomeTier.valueOf(tierStr.uppercase()) }.getOrNull()?.let { tier ->
                tier to blurb
            }
        }.toMap()

        return MiniGameConfig(
            id = id,
            title = title,
            department = department,
            description = description,
            durationSeconds = durationSeconds,
            targetSpeed = targetSpeed,
            errorPenalty = errorPenalty,
            quota = quota,
            metricDeltasOnTier = parsedDeltas,
            blurbOnTier = parsedBlurbs
        )
    }
}

data class MiniGameFileDto(val minigames: List<MiniGameDto>? = null)

data class EnvironmentDefinitionDto(
    val id: String = "",
    val displayName: String = "",
    val icon: String = "",
    val loreLocation: String = "",
    val subtitle: String = "",
    val ambientThemeColor: String = "#FFFFFF",
    val backgroundAsset: String = "",
    val aliases: List<String>? = null,
    val overlayType: String = "FOREST",
    /** Where the backdrop picture's floor starts, as a fraction of its height (the `_p` variant has its own). */
    val artHorizon: Float? = null,
    val artHorizonPortrait: Float? = null
)

data class EnvironmentFileDto(
    val environments: List<EnvironmentDefinitionDto>? = null
)

data class StatusTierDto(
    val turns: Int = 1,
    val potPct: Float = 0.1f,
    val maxStacks: Int = 1
)

data class StatusDefDto(
    val kind: String = "DOT",
    val tiers: Map<String, StatusTierDto>? = null,
    val resist: Map<String, String>? = null
)

data class StatusCatalogFileDto(
    val statuses: Map<String, StatusDefDto>? = null
)

data class AffinityMatrixFileDto(
    val floor: Float = 0.8f,
    val ceiling: Float = 2.0f,
    val sameElementImpairment: Float = 0.8f,
    val matrix: Map<String, Map<String, Float>>? = null,
    val narrations: Map<String, String>? = null
)

data class ThesaurusFileDto(
    val schools: Map<String, List<String>>? = null
)

data class TargetingDictionaryDto(
    val ordinals: Map<String, List<String>>? = null,
    val positionals: Map<String, List<String>>? = null,
    val groups: Map<String, List<String>>? = null
)

data class CommandCatalogDto(
    val locale: String = "en",
    val metaCommands: Map<String, List<String>>? = null,
    val targeting: TargetingDictionaryDto? = null,
    val actions: Map<String, List<String>>? = null,
    val progressionPhrases: List<String>? = null,
    val continueStoryPhrases: List<String>? = null,
    val restartBattlePhrases: List<String>? = null,
    val systemConfirmations: Map<String, String>? = null
) {
    fun toCatalog(): com.voicerpg.engine.model.CommandCatalog {
        val resolvedMeta = metaCommands?.mapNotNull { (key, phrases) ->
            runCatching { com.voicerpg.engine.model.MetaCommand.valueOf(key) }.getOrNull()?.let { it to phrases }
        }?.toMap() ?: emptyMap()
        val resolvedOrdinals = targeting?.ordinals?.mapNotNull { (key, phrases) ->
            key.toIntOrNull()?.let { it to phrases }
        }?.toMap() ?: emptyMap()
        return com.voicerpg.engine.model.CommandCatalog(
            locale = locale,
            metaCommands = resolvedMeta,
            ordinals = resolvedOrdinals,
            positionals = targeting?.positionals ?: emptyMap(),
            groups = targeting?.groups ?: emptyMap(),
            actions = actions ?: emptyMap(),
            progressionPhrases = progressionPhrases ?: emptyList(),
            continueStoryPhrases = continueStoryPhrases ?: emptyList(),
            restartBattlePhrases = restartBattlePhrases ?: emptyList(),
            systemConfirmations = systemConfirmations ?: emptyMap()
        )
    }
}

// =============================================================================
// RESOLVED ENGINE MODELS
// =============================================================================

data class GameManifest(
    val spriteContract: Int = 0,
    /** "mana" or "none" (see ManifestDto.combatResource). */
    val combatResource: String = "mana",
    val gameTitle: String,
    val gameSubtitle: String,
    val worldName: String = "the realm",
    val initialSceneId: String,
    val initialNodeId: String,
    val defaultEncounterId: String,
    val startingAchievementId: String,
    val victoryLine: String,
    val defeatLine: String,
    val victoryTitle: String = "⚔️ VICTORY ⚔️",
    val defeatTitle: String = "💀 DEFEAT 💀",
    val victoryContinueLabel: String = "CONTINUE STORY ➔",
    val defeatContinueLabel: String = "PRESS ON ➔",
    val titleBackgroundAsset: String? = null,
    val defaultHeroName: String = "Hero",
    val defaultHeroTitle: String = "Adventurer",
    val quickNames: List<String> = emptyList(),
    val characterTitles: List<String> = emptyList(),
    val creationTitle: String? = null,
    val creationSubtitle: String? = null,
    val creationEmbarkPrompt: String = "AWAKEN ➔",
    val creationVoiceKeywords: List<String> = emptyList(),
    val defaultHeroPortrait: String? = null,
    val tutorialCompletionText: String = "",
    val voicePermissionTitle: String = "",
    val voicePermissionSubtitle: String = "",
    val voicePermissionPrompt: String = "",
    val resonanceProfile: ResonanceProfile = ResonanceProfile.CLASSIC,
    val ttsReplacements: Map<String, String> = emptyMap(),
    val selfieConfig: SelfieFilterConfig = SelfieFilterConfig(),
    val allowClassSelectionInCreation: Boolean = true,
    val creationNameLabel: String? = null,
    val creationAestheticsLabel: String? = null,
    val creationStatusSubtitle: String? = null,
    val campaignTracks: List<com.voicerpg.engine.model.CampaignTrackDef> = emptyList(),
    val creationMode: String = "custom",
    val titleMusic: String? = null,
    val explorationMusic: String? = null,
    val combatMusic: String? = null
) {
    /** Character creation is a pick between the campaign tracks' preset heroes. */
    val isPresetSelect: Boolean get() = creationMode.equals("preset_select", ignoreCase = true) && campaignTracks.any { it.hero != null }

    fun resolveDefaultHeroName(): String = resolveTemplateTokens(defaultHeroName)

    companion object {
        fun resolveTemplateTokens(template: String): String {
            if (!template.contains("{random")) return template
            val regex = Regex("""\{random(?::?(\d+))?\}""")
            return regex.replace(template) { match ->
                val digits = match.groupValues[1].toIntOrNull() ?: 9
                val count = digits.coerceIn(1, 18)
                val first = kotlin.random.Random.nextInt(1, 10).toString()
                val rest = (1 until count).map { kotlin.random.Random.nextInt(0, 10) }.joinToString("")
                first + rest
            }
        }
    }
}

data class ResonanceProfile(
    val mode: String = "CLASSIC",
    val topDamageAccessible: Boolean = true,
    val conciseCommandBonus: Boolean = false,
    val singleRootMaxBonus: Boolean = false,
    val bypassPoeticRequirement: Boolean = false,
    val urgencyBonus: Float = 0f,
    val acousticSensitivity: Float = 1.0f,
    val tierChants: Map<String, String> = emptyMap()
) {
    val isSurvival: Boolean get() = mode == "SURVIVAL"

    companion object {
        val CLASSIC = ResonanceProfile(mode = "CLASSIC")
        val SURVIVAL = ResonanceProfile(
            mode = "SURVIVAL",
            topDamageAccessible = true,
            conciseCommandBonus = true,
            singleRootMaxBonus = true,
            bypassPoeticRequirement = true,
            urgencyBonus = 12f,
            acousticSensitivity = 1.25f,
            tierChants = mapOf(
                "BASIC" to "{name}",
                "ADEPT" to "Get it done: {name}!",
                "MASTER" to "Hold on! Push through: {name}!",
                "MYTHIC" to "We have to survive this shift: {name} now!",
                "TRANSCENDENTAL" to "Dig deep, push through the grind, and survive: {name}!"
            )
        )
    }
}

data class EnvironmentDefinition(
    val id: String,
    val displayName: String,
    val icon: String,
    val loreLocation: String,
    val subtitle: String,
    val ambientThemeColor: Color,
    val backgroundAsset: String,
    val aliases: List<String> = emptyList(),
    val overlayType: String = "FOREST",
    /** Painted horizon of the backdrop picture (fraction of its height); aligned with the battle stage's. */
    val artHorizon: Float? = null,
    val artHorizonPortrait: Float? = null
) {
    fun artHorizonFor(portraitVariant: Boolean): Float =
        (if (portraitVariant) artHorizonPortrait ?: artHorizon else artHorizon)
            ?: com.voicerpg.engine.ui.combat.BattleStage.DEFAULT_ART_HORIZON
}

data class AffinityMatrix(
    val floor: Float,
    val ceiling: Float,
    val sameElementImpairment: Float,
    val matrix: Map<String, Map<String, Float>>,
    val narrations: Map<String, String>
)

/** A playable/voice hero class archetype loaded from assets/game/classes/<name>.json. */
data class HeroClassDef(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val startingHp: Int,
    val startingMp: Int,
    val startingSpeed: Int,
    val startingBaseAttack: Int,
    val preferredSchool: SpellSchool,
    val starterSpellIds: List<String>,
    val voiceKeywords: List<String>,
    val portraitAsset: String? = null,
    val spellGrants: List<SpellGrant> = emptyList()
)

enum class CharacterRole { HERO, NPC, NARRATOR }

data class SpellGrant(val flag: String, val spellIds: List<String>)

/**
 * A fighter's spells across the whole story: the [starting] kit, then each grant, learned when its story flag is
 * set. Only additions: nothing is replaced. A fighter joining late knows every grant whose flag is already set.
 */
data class SpellKit(val starting: List<Spell>, val grants: List<Pair<String, List<Spell>>>) {
    /** What the fighter knows with these story [flags] set. */
    fun known(flags: Map<String, Boolean>): List<Spell> =
        (starting + grants.filter { (flag, _) -> flag.isNotBlank() && flags[flag] == true }.flatMap { it.second }).distinctBy { it.id }

    /** Every spell the kit ever holds. */
    val all: List<Spell> get() = (starting + grants.flatMap { it.second }).distinctBy { it.id }
}

data class CharacterCombat(
    val maxHp: Int,
    val maxMp: Int,
    val speed: Int,
    val defense: Int,
    val avatarTint: Color,
    val startAtb: Float,
    val spells: List<Spell>,
    val isGhost: Boolean = false,
    val canAttack: Boolean = true,
    val isUntargetable: Boolean = false,
    val spriteAlpha: Float? = null,
    val cutIn: com.voicerpg.engine.model.CutInSpec? = null
)

data class SpriteDefinition(
    val palette: Map<Char, Color>,
    val idleUpright: List<String>,
    val idleCrouch: List<String>,
    val actionCast: List<String>,
    val damaged: List<String>,
    val attack: List<String>? = null,
    val hurt: List<String>? = null,
    val death: List<String>? = null
) {
    fun getFrame(frame: SpriteFrame): List<String> = when (frame) {
        SpriteFrame.IDLE_UPRIGHT -> idleUpright
        SpriteFrame.IDLE_CROUCH -> idleCrouch
        SpriteFrame.ACTION_CAST -> actionCast
        SpriteFrame.DAMAGED -> damaged
        SpriteFrame.ATTACK -> attack ?: actionCast
        SpriteFrame.HURT -> hurt ?: damaged
        SpriteFrame.DEATH -> death ?: damaged
    }
}

/** A fully resolved character: dialogue speaker + combat identity, all from one data file. */
data class GameCharacter(
    val id: String,
    val role: CharacterRole,
    val speaker: DialogueSpeaker,
    val loreClass: String,
    val spriteId: String,
    val voiceAliases: List<String>,
    val preferredVoiceId: String?,
    val combat: CharacterCombat?,
    val joinWhenFlag: String?,
    val spellGrants: List<SpellGrant>,
    val gender: String? = null,
    val allowedVoiceRegions: List<String> = emptyList(),
    val sprite: SpriteDefinition? = null,
    val soundBites: List<String> = emptyList(),
    val outOfTurnLine: String? = null
) {
    fun toPartyMember(atbOverride: Float? = null): PartyMember? {
        val c = combat ?: return null
        return PartyMember(
            id = id,
            name = speaker.name,
            loreClass = loreClass,
            currentHp = c.maxHp,
            maxHp = c.maxHp,
            currentMp = c.maxMp,
            maxMp = c.maxMp,
            spells = c.spells,
            avatarTint = c.avatarTint,
            speed = c.speed,
            defense = c.defense,
            atbGauge = atbOverride ?: c.startAtb,
            isGhost = c.isGhost,
            canAttack = c.canAttack,
            isUntargetable = c.isUntargetable,
            spriteAlpha = c.spriteAlpha
        )
    }

    fun toSavedStats(): SavedCharacterStats? {
        val c = combat ?: return null
        return SavedCharacterStats(
            id = id,
            name = speaker.name,
            loreClass = loreClass,
            currentHp = c.maxHp,
            maxHp = c.maxHp,
            currentMp = c.maxMp,
            maxMp = c.maxMp,
            speed = c.speed
        )
    }
}

/** Reusable enemy definition loaded from assets/game/enemies/enemies.json. */
data class EnemyTemplate(
    val id: String,
    val name: String,
    val subtitle: String,
    val family: String,
    val selfElement: String,
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val movesetId: String,
    val isBoss: Boolean,
    val spriteId: String,
    val spriteTint: Color,
    val voiceAliases: List<String> = emptyList(),
    val soundBites: List<String> = emptyList(),
    val scale: Float = if (isBoss) com.voicerpg.engine.model.Enemy.DEFAULT_BOSS_SCALE else 1.0f,
    val taunts: List<String> = emptyList(),
    val hitReaction: com.voicerpg.engine.model.HitReaction = com.voicerpg.engine.model.HitReaction.FLINCH,
    val cutIn: com.voicerpg.engine.model.CutInSpec? = null,
    val shortName: String = ""
) {
    fun toEnemy(
        instanceId: String? = null,
        nameOverride: String? = null,
        subtitleOverride: String? = null,
        hpOverride: Int? = null,
        attackOverride: Int? = null,
        defenseOverride: Int? = null,
        speedOverride: Int? = null,
        isTargeted: Boolean = false,
        atbGauge: Float = 0f
    ): Enemy {
        val hp = hpOverride ?: this.hp
        return Enemy(
            id = instanceId ?: id,
            name = nameOverride ?: name,
            subtitle = subtitleOverride ?: subtitle,
            currentHp = hp,
            maxHp = hp,
            baseAttack = attackOverride ?: attack,
            defense = defenseOverride ?: defense,
            speed = speedOverride ?: speed,
            isBoss = isBoss,
            isTargeted = isTargeted,
            spriteTint = spriteTint,
            spriteId = spriteId,
            family = family,
            selfElement = selfElement,
            movesetId = movesetId,
            atbGauge = atbGauge,
            voiceAliases = voiceAliases,
            soundBites = soundBites,
            scale = scale,
            taunts = taunts,
            hitReaction = hitReaction,
            cutIn = cutIn,
            shortName = if (nameOverride != null) "" else shortName
        )
    }
}

// =============================================================================
// CONTENT PACK & LOADER
// =============================================================================

/** Immutable, fully cross-referenced bundle of a game's data files. */
class GameContentPack(
    val manifest: GameManifest,
    val characters: Map<String, GameCharacter>,
    val classes: List<HeroClassDef>,
    val spells: Map<String, Spell>,
    val enemyTemplates: Map<String, EnemyTemplate>,
    val encounters: Map<String, EncounterDefinition>,
    val scenes: Map<String, StoryScene>,
    val nodes: Map<String, DialogueNode>,
    val narrator: DialogueSpeaker,
    val enemySprites: Map<String, SpriteDefinition> = emptyMap(),
    val enemyMovesets: Map<String, Moveset> = emptyMap(),
    val environments: Map<String, EnvironmentDefinition> = emptyMap(),
    val statusCatalog: Map<String, StatusDef> = emptyMap(),
    val affinityMatrix: AffinityMatrix? = null,
    val presentation: PresentationConfig = PresentationConfig(),
    val thesaurusRoots: Map<SpellSchool, List<String>> = emptyMap(),
    val metrics: Map<String, MetricDefinition> = emptyMap(),
    val incidents: Map<String, IncidentDefinition> = emptyMap(),
    val minigames: Map<String, MiniGameConfig> = emptyMap(),
    val commandCatalogs: Map<String, com.voicerpg.engine.model.CommandCatalog> = emptyMap(),
    /** Aseprite battle sheets by sprite id (`sprites/sheets/`), and per-sprite settings and variants. */
    val spriteSheets: Map<String, SpriteSheet> = emptyMap(),
    val spriteSettings: Map<String, SpriteSetting> = emptyMap()
) {
    /**
     * The sheet a sprite key draws: a variant of a sheet, the sheet itself, or the sheet named by the
     * character or enemy template with that id. Null when only a legacy text grid (or nothing) exists.
     */
    fun resolveSheet(key: String): ResolvedSheet? {
        direct(key)?.let { return it }
        characters[key]?.spriteId?.takeIf { it.isNotBlank() && it != key }?.let { id -> direct(id)?.let { return it } }
        enemyTemplates[key]?.spriteId?.takeIf { it.isNotBlank() && it != key }?.let { id -> direct(id)?.let { return it } }
        return null
    }

    /** Own sheet, then a variant's base, then a placeholder (each resolved the same way; cycles stop). */
    private fun direct(key: String, seen: Set<String> = emptySet()): ResolvedSheet? {
        if (key in seen) return null
        val setting = spriteSettings[key]
        spriteSheets[key]?.let { return ResolvedSheet(key, it, setting) }
        setting?.base?.let { base ->
            val b = direct(base, seen + key) ?: return null
            return ResolvedSheet(key, b.sheet, setting, b.isPlaceholder, b.missing)
        }
        setting?.placeholder?.let { stand ->
            val p = direct(stand, seen + key) ?: return null
            return ResolvedSheet(key, p.sheet, setting, isPlaceholder = true, missing = key)
        }
        return null
    }

    val heroCharacter: GameCharacter? = characters.values.firstOrNull { it.role == CharacterRole.HERO }

    /** All speakers that can hold a TTS voice binding (narrator first). */
    val allSpeakers: List<DialogueSpeaker> =
        (listOf(narrator) + characters.values.filter { it.role != CharacterRole.NARRATOR }.map { it.speaker })
            .distinctBy { it.id }

    val initialScene: StoryScene =
        scenes[manifest.initialSceneId] ?: scenes.values.firstOrNull() ?: FALLBACK_SCENE
    val initialNode: DialogueNode =
        nodes[manifest.initialNodeId] ?: nodes[initialScene.initialNodeId] ?: nodes.values.firstOrNull() ?: FALLBACK_NODE

    val defaultEncounter: EncounterDefinition? =
        encounters[manifest.defaultEncounterId] ?: encounters.values.firstOrNull()

    /** Per-speaker first-try TTS voice pack preferences declared in the character files. */
    val preferredVoiceIds: Map<String, String> =
        characters.values.mapNotNull { c -> c.preferredVoiceId?.let { c.id to it } }.toMap()

    fun speakerById(id: String?): DialogueSpeaker? =
        if (id == null) null else if (id == narrator.id) narrator else characters[id]?.speaker

    fun characterById(id: String?): GameCharacter? = if (id == null) null else characters[id]

    fun classById(id: String?): HeroClassDef =
        classes.firstOrNull { it.id == id } ?: classes.firstOrNull() ?: FALLBACK_CLASS

    fun encounterById(id: String?): EncounterDefinition? = if (id == null) null else encounters[id]

    fun minigameById(id: String?): MiniGameConfig? = if (id == null) null else minigames[id]

    fun environmentById(id: String?): EnvironmentDefinition? {
        if (id == null) return null
        environments[id]?.let { return it }
        return environments.values.firstOrNull { env ->
            env.id.equals(id, ignoreCase = true) || env.aliases.any { it.equals(id, ignoreCase = true) }
        }
    }

    fun spellById(id: String?): Spell? = if (id == null) null else spells[id]

    fun spellsForClass(classId: String?): List<Spell> =
        classById(classId).starterSpellIds.mapNotNull { spells[it] }

    /**
     * A fighter's [SpellKit]. The hero with a declared class ([heroClassId]) uses that class's starting spells and
     * grants; anyone else (and a hero without a class) their character file's combat spells and grants.
     */
    fun spellKitFor(memberId: String, heroClassId: String? = null): SpellKit {
        val heroClass = if (memberId == "hero" && !heroClassId.isNullOrBlank()) classes.firstOrNull { it.id.equals(heroClassId, ignoreCase = true) } else null
        if (heroClass != null) {
            return SpellKit(
                starting = heroClass.starterSpellIds.mapNotNull { spells[it] },
                grants = heroClass.spellGrants.map { g -> g.flag to g.spellIds.mapNotNull { spells[it] } }
            )
        }
        val character = characters[memberId] ?: (if (memberId == "hero") heroCharacter else null)
        return SpellKit(
            starting = character?.combat?.spells ?: emptyList(),
            grants = character?.spellGrants?.map { g -> g.flag to g.spellIds.mapNotNull { spells[it] } } ?: emptyList()
        )
    }

    /** The spells [memberId] knows with these story [flags] set (see [SpellKit]). */
    fun knownSpells(memberId: String, heroClassId: String?, flags: Map<String, Boolean>): List<Spell> =
        spellKitFor(memberId, heroClassId).known(flags)

    fun enemyTemplateById(id: String?): EnemyTemplate? = if (id == null) null else enemyTemplates[id]

    fun enemySpriteById(id: String?): SpriteDefinition? = if (id == null) null else enemySprites[id]

    fun movesetById(id: String?): Moveset? = if (id == null) null else enemyMovesets[id]

    fun voiceAliasesFor(memberId: String?): List<String> = characterById(memberId)?.voiceAliases ?: emptyList()

    /** Spells of the named character, falling back to the hero's loadout. */
    fun heroSpellsFor(memberId: String = "hero"): List<Spell> =
        characters[memberId]?.combat?.spells ?: heroCharacter?.combat?.spells ?: emptyList()

    /** Spells of a named companion character. */
    fun companionSpellsFor(memberId: String): List<Spell> =
        characters[memberId]?.combat?.spells ?: emptyList()

    /** Starter loadout for a companion: its full spell list from the character data file. */
    fun companionStarterSpellsFor(memberId: String): List<Spell> = companionSpellsFor(memberId)

    /** The free breath/attune discipline a character can cast to restore mana. */
    fun breathSpellFor(memberId: String): Spell? =
        characters[memberId]?.combat?.spells?.firstOrNull { it.manaRestorePct > 0f }

    /** Resolves a character to a combat-ready party member (null when the character has no combat stats). */
    fun companionMember(memberId: String): PartyMember? = characters[memberId]?.toPartyMember()

    /** Data-driven starter party: the template hero plus the first combat-capable NPC. */
    fun createDuoParty(): List<PartyMember> = listOfNotNull(
        heroCharacter?.toPartyMember(),
        characters.values
            .firstOrNull { it.role != CharacterRole.HERO && it.combat != null }
            ?.toPartyMember()
    )

    /** Generic summoned-minion factory used by enemy SUMMON moves. */
    fun createMinion(idSuffix: String, name: String = "Template Minion", subtitle: String = "Minion", hp: Int = 150): Enemy {
        val template = enemyTemplates["template_minion"]
        return template?.toEnemy(
            instanceId = "minion_$idSuffix",
            nameOverride = name,
            subtitleOverride = subtitle,
            hpOverride = hp,
            isTargeted = false,
            atbGauge = 0f
        ) ?: Enemy(
            id = "minion_$idSuffix",
            name = name,
            subtitle = subtitle,
            currentHp = hp,
            maxHp = hp,
            baseAttack = 18,
            isTargeted = false,
            spriteTint = Color(0xFF80CBC4),
            speed = 55,
            atbGauge = 0f
        )
    }

    companion object {
        val FALLBACK_SCENE = StoryScene(
            id = "fallback_scene",
            name = "Template Chamber",
            chapterTitle = "Sandbox",
            backgroundAsset = "story/template_scene.jpg",
            initialNodeId = "fallback_node",
            ambientDescription = "A quiet chamber awaiting your story."
        )
        val FALLBACK_NODE = DialogueNode(
            id = "fallback_node",
            speaker = DialogueSpeaker.NARRATOR,
            side = SpeakerSide.CENTER_NARRATOR,
            text = "No story content is loaded. Add scenes and nodes under assets/game/story/."
        )
        val FALLBACK_CLASS = HeroClassDef(
            id = "adventurer",
            title = "Adventurer",
            subtitle = "Jack of All Trades",
            description = "A balanced template class.",
            startingHp = 240,
            startingMp = 120,
            startingSpeed = 65,
            startingBaseAttack = 22,
            preferredSchool = SpellSchool.PHYSICAL,
            starterSpellIds = emptyList(),
            voiceKeywords = listOf("adventurer", "hero"),
            portraitAsset = null
        )
    }
}

/** Abstraction over the bytes source so the same loader runs on Android and in JVM tests. */
interface ContentSource {
    fun readText(path: String): String?
    fun listFiles(dir: String): List<String>
}

class AssetContentSource(private val assets: AssetManager, private val root: String) : ContentSource {
    override fun readText(path: String): String? = try {
        assets.open("$root/$path").bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (_: Exception) {
        null
    }

    override fun listFiles(dir: String): List<String> = try {
        assets.list("$root/$dir")?.filter { it.endsWith(".json") }?.sorted() ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

class FileContentSource(private val rootDir: File) : ContentSource {
    override fun readText(path: String): String? {
        val file = File(rootDir, path)
        return if (file.isFile) runCatching { file.readText(Charsets.UTF_8) }.getOrNull() else null
    }

    override fun listFiles(dir: String): List<String> =
        File(rootDir, dir).listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.map { it.name }?.sorted() ?: emptyList()
}

/** Parses the JSON content tree into a resolved [GameContentPack]. */
class GameContentLoader(private val source: ContentSource) {
    /** False for a game without mana: spells load free, fighters with no MP, mana-restore spells left out. */
    private var usesMana = true


    private companion object {
        const val SPRITE_SHEET_DIR = "sprites/sheets"
        /** The content folder inside the APK's assets (sheet PNG paths are given from the assets root). */
        const val CONTENT_ASSET_ROOT = "game"
    }

    private val gson = Gson()

    fun load(): GameContentPack {
        val manifestDto = read("manifest.json", ManifestDto::class.java) ?: ManifestDto()
        usesMana = !manifestDto.combatResource.equals("none", ignoreCase = true)
        val catalogSpells = (read("spells/spells.json", SpellFileDto::class.java)?.spells ?: emptyList())
            .mapNotNull { it.toSpellOrNull() }
            .associateBy { it.id }

        val characterDtos = source.listFiles("characters")
            .mapNotNull { read("characters/$it", CharacterDto::class.java) }
            .filter { it.id.isNotBlank() }

        val characterSpells = characterDtos.flatMap { c ->
            (c.combat?.spells ?: c.spells ?: emptyList()).mapNotNull { it.toSpellOrNull() }
        }.associateBy { it.id }

        val spells = catalogSpells + characterSpells

        val classes = source.listFiles("classes")
            .mapNotNull { read("classes/$it", ClassDto::class.java) }
            .filter { it.id.isNotBlank() }
            .map { it.toDef(spells) }

        val characters = characterDtos
            .map { it.toCharacter(spells) }
            .associateBy { it.id }

        val enemyTemplates = (read("enemies/enemies.json", EnemyFileDto::class.java)?.enemies ?: emptyList())
            .filter { it.id.isNotBlank() }
            .map { it.toTemplate() }
            .associateBy { it.id }

        val encounters = (read("encounters/encounters.json", EncounterFileDto::class.java)?.encounters ?: emptyList())
            .filter { it.id.isNotBlank() }
            .map { it.toDefinition(characters, enemyTemplates) }
            .associateBy { it.id }

        val scenes = (read("story/scenes.json", SceneFileDto::class.java)?.scenes ?: emptyList())
            .filter { it.id.isNotBlank() }
            .map { it.toScene() }
            .associateBy { it.id }

        val speakersBydId = characters.mapValues { it.value.speaker }
        val nodes = (read("story/nodes.json", NodeFileDto::class.java)?.nodes ?: emptyList())
            .filter { it.id.isNotBlank() }
            .map { it.toNode(speakersBydId) }
            .associateBy { it.id }

        val enemySprites = (read("enemies/enemy_sprites.json", EnemySpritesFileDto::class.java)?.sprites ?: emptyMap())
            .mapNotNull { (id, dto) ->
                dto.toSpriteDefinition()?.let { id to it }
            }
            .toMap()

        val spriteSheets = source.listFiles(SPRITE_SHEET_DIR)
            .mapNotNull { file ->
                val id = file.removeSuffix(".json")
                source.readText("$SPRITE_SHEET_DIR/$file")?.let { AsepriteParser.parse(id, it, "$CONTENT_ASSET_ROOT/$SPRITE_SHEET_DIR") }
            }
            .associateBy { it.id }
        val spriteSettings = (read("sprites/sprites.json", SpriteSettingsFileDto::class.java)?.sprites ?: emptyMap())
            .map { (id, dto) -> dto.toSetting(id) }
            .associateBy { it.id }

        val enemyMovesets = (read("enemies/enemy_spells.json", EnemySpellsFileDto::class.java)?.movesets ?: emptyMap())
            .mapValues { it.value.toMoveset() }

        val environments = (read("environments/environments.json", EnvironmentFileDto::class.java)?.environments ?: emptyList())
            .filter { it.id.isNotBlank() }
            .map { it.toDefinition() }
            .associateBy { it.id }

        val statusCatalog = (read("combat/status_catalog.json", StatusCatalogFileDto::class.java)?.statuses ?: emptyMap())
            .mapValues { (_, dto) -> dto.toStatusDef() }

        val affinityMatrixDto = read("combat/affinity_matrix.json", AffinityMatrixFileDto::class.java)
        val affinityMatrix = affinityMatrixDto?.let {
            AffinityMatrix(
                floor = it.floor,
                ceiling = it.ceiling,
                sameElementImpairment = it.sameElementImpairment,
                matrix = it.matrix ?: emptyMap(),
                narrations = it.narrations ?: emptyMap()
            )
        }

        val presentation = read("combat/presentation.json", PresentationFileDto::class.java)?.toConfig() ?: PresentationConfig()

        val thesaurusDto = read("spells/thesaurus.json", ThesaurusFileDto::class.java)
        val thesaurusRoots = thesaurusDto?.schools?.mapNotNull { (schoolStr, words) ->
            runCatching { SpellSchool.valueOf(schoolStr) }.getOrNull()?.let { school ->
                school to words
            }
        }?.toMap() ?: emptyMap()

        val metrics = (read("metrics/metrics.json", MetricFileDto::class.java)?.metrics ?: emptyList())
            .filter { it.id.isNotBlank() }
            .map { it.toDefinition() }
            .associateBy { it.id }

        val incidents = (read("incidents/incidents.json", IncidentFileDto::class.java)?.incidents ?: emptyList())
            .filter { it.id.isNotBlank() }
            .map { it.toDefinition() }
            .associateBy { it.id }

        val minigames = (read("minigames/minigames.json", MiniGameFileDto::class.java)?.minigames ?: emptyList())
            .filter { it.id.isNotBlank() }
            .map { it.toConfig() }
            .associateBy { it.id }

        val commandCatalogs = source.listFiles("commands").mapNotNull { file ->
            read("commands/$file", CommandCatalogDto::class.java)?.toCatalog()
        }.associateBy { it.locale }

        val narrator = characters["narrator"]?.speaker
            ?: characters.values.firstOrNull { it.role == CharacterRole.NARRATOR }?.speaker
            ?: DialogueSpeaker.NARRATOR

        val selfieConfig = read("creation/selfie_filters.json", SelfieFilterConfig::class.java)
            ?: read("selfie_filters.json", SelfieFilterConfig::class.java)
            ?: manifestDto.selfieConfig
            ?: SelfieFilterConfig()

        val pack = GameContentPack(
            manifest = GameManifest(
                spriteContract = manifestDto.spriteContract,
                combatResource = if (manifestDto.combatResource.equals("none", ignoreCase = true)) "none" else "mana",
                gameTitle = manifestDto.gameTitle,
                gameSubtitle = manifestDto.gameSubtitle,
                worldName = manifestDto.worldName,
                initialSceneId = manifestDto.initialSceneId,
                initialNodeId = manifestDto.initialNodeId,
                defaultEncounterId = manifestDto.defaultEncounterId,
                startingAchievementId = manifestDto.startingAchievementId,
                victoryLine = manifestDto.victoryLine,
                defeatLine = manifestDto.defeatLine,
                victoryTitle = manifestDto.victoryTitle,
                defeatTitle = manifestDto.defeatTitle,
                victoryContinueLabel = manifestDto.victoryContinueLabel,
                defeatContinueLabel = manifestDto.defeatContinueLabel,
                titleBackgroundAsset = manifestDto.titleBackgroundAsset,
                defaultHeroName = manifestDto.defaultHeroName,
                defaultHeroTitle = manifestDto.defaultHeroTitle,
                quickNames = manifestDto.quickNames ?: emptyList(),
                characterTitles = manifestDto.characterTitles ?: emptyList(),
                creationTitle = manifestDto.creationTitle,
                creationSubtitle = manifestDto.creationSubtitle,
                creationEmbarkPrompt = manifestDto.creationEmbarkPrompt,
                creationVoiceKeywords = manifestDto.creationVoiceKeywords ?: emptyList(),
                defaultHeroPortrait = manifestDto.defaultHeroPortrait,
                tutorialCompletionText = manifestDto.tutorialCompletionText,
                voicePermissionTitle = manifestDto.voicePermissionTitle,
                voicePermissionSubtitle = manifestDto.voicePermissionSubtitle,
                voicePermissionPrompt = manifestDto.voicePermissionPrompt,
                resonanceProfile = manifestDto.resonance?.toProfile() ?: ResonanceProfile.CLASSIC,
                ttsReplacements = manifestDto.ttsReplacements ?: emptyMap(),
                selfieConfig = selfieConfig,
                allowClassSelectionInCreation = manifestDto.allowClassSelectionInCreation ?: true,
                creationNameLabel = manifestDto.creationNameLabel,
                creationAestheticsLabel = manifestDto.creationAestheticsLabel,
                creationStatusSubtitle = manifestDto.creationStatusSubtitle,
                campaignTracks = manifestDto.campaignTracks ?: emptyList(),
                creationMode = manifestDto.creationMode?.ifBlank { null } ?: "custom",
                titleMusic = manifestDto.titleMusic?.ifBlank { null },
                explorationMusic = manifestDto.explorationMusic?.ifBlank { null },
                combatMusic = manifestDto.combatMusic?.ifBlank { null }
            ),
            characters = characters,
            classes = classes,
            spells = spells,
            enemyTemplates = enemyTemplates,
            encounters = encounters,
            scenes = scenes,
            nodes = nodes,
            narrator = narrator,
            enemySprites = enemySprites,
            enemyMovesets = enemyMovesets,
            environments = environments,
            statusCatalog = statusCatalog,
            affinityMatrix = affinityMatrix,
            presentation = presentation,
            thesaurusRoots = thesaurusRoots,
            metrics = metrics,
            incidents = incidents,
            minigames = minigames,
            commandCatalogs = commandCatalogs,
            spriteSheets = spriteSheets,
            spriteSettings = spriteSettings
        )
        return if (pack.scenes.isEmpty() || pack.nodes.isEmpty()) EmergencyFallback.build() else pack
    }

    private fun <T> read(path: String, type: Class<T>): T? = try {
        source.readText(path)?.let { gson.fromJson(it, type) }
    } catch (_: Exception) {
        null
    }

    /**
     * The spell as this game uses it. Without mana: free, a spell that only restores mana is left out, and one that
     * also does something else (a heal that restores mana too) keeps the rest.
     */
    private fun SpellDto.toSpellOrNull(): Spell? {
        if (usesMana) return toSpell()
        val onlyRestoresMana = manaRestorePct > 0f && !isHeal && basePower == 0 && !isGuard && !cleansesDebuffs && status == null
        return if (onlyRestoresMana) null else toSpell().copy(mpCost = 0, manaRestorePct = 0f)
    }

    private fun SpellDto.toSpell(): Spell = Spell(
        id = id,
        calloutName = calloutName?.trim().orEmpty(),
        name = name.ifBlank { id },
        school = runCatching { SpellSchool.valueOf(school) }.getOrDefault(SpellSchool.PHYSICAL),
        basePower = basePower,
        mpCost = mpCost,
        isHeal = isHeal,
        hitsAll = hitsAll,
        description = description,
        exampleChant = exampleChant.ifBlank { name },
        status = status,
        lifesteal = lifesteal,
        isGuard = isGuard,
        manaRestorePct = manaRestorePct,
        aliases = aliases ?: emptyList(),
        graphics = graphics?.toSpellGraphics(),
        cleansesDebuffs = cleansesDebuffs || status.equals("CLEANSE", ignoreCase = true) || status.equals("DISPEL", ignoreCase = true),
        presetChants = presetChants?.mapKeys { it.key.uppercase() } ?: emptyMap(),
        soundBites = soundBites.orEmpty().filter { it.isNotBlank() }
    )

    private fun ClassDto.toDef(spells: Map<String, Spell>): HeroClassDef = HeroClassDef(
        id = id,
        title = title.ifBlank { id },
        subtitle = subtitle,
        description = description,
        startingHp = startingHp,
        startingMp = if (usesMana) startingMp else 0,
        startingSpeed = startingSpeed,
        startingBaseAttack = startingBaseAttack,
        preferredSchool = runCatching { SpellSchool.valueOf(preferredSchool) }.getOrDefault(SpellSchool.PHYSICAL),
        starterSpellIds = (starterSpellIds ?: emptyList()).filter { it in spells },
        voiceKeywords = voiceKeywords ?: emptyList(),
        portraitAsset = portraitAsset,
        spellGrants = (spellGrants ?: emptyList()).map { g -> SpellGrant(g.flag, (g.spellIds ?: emptyList()).filter { it in spells }) }
    )

    private fun CharacterDto.toCharacter(spells: Map<String, Spell>): GameCharacter {
        val speaker = DialogueSpeaker(
            id = id,
            name = name.ifBlank { id },
            title = title,
            portraitAsset = portraitAsset,
            themeColor = parseHexColor(themeColor, Color.White),
            ttsPitch = ttsPitch,
            samplePhrase = samplePhrase,
            gender = gender,
            allowedVoiceRegions = allowedVoiceRegions ?: emptyList()
        )
        val combat = combat?.let { c ->
            val embeddedSpells = (c.spells ?: this.spells ?: emptyList()).mapNotNull { it.toSpellOrNull() }
            val referencedSpells = (c.spellIds ?: emptyList()).mapNotNull { spells[it] }
            val allSpells = (embeddedSpells + referencedSpells).distinctBy { it.id }
            CharacterCombat(
                maxHp = c.maxHp,
                maxMp = if (usesMana) c.maxMp else 0,
                speed = c.speed,
                defense = c.defense,
                avatarTint = parseHexColor(c.avatarTint, Color(0xFF4FC3F7)),
                startAtb = c.startAtb,
                spells = allSpells,
                isGhost = c.isGhost,
                canAttack = c.canAttack,
                isUntargetable = c.isUntargetable,
                spriteAlpha = c.spriteAlpha,
                cutIn = c.cutIn
            )
        }
        val resolvedSprite = sprite?.toSpriteDefinition()
        return GameCharacter(
            id = id,
            role = when (role.lowercase()) {
                "hero" -> CharacterRole.HERO
                "narrator" -> CharacterRole.NARRATOR
                else -> CharacterRole.NPC
            },
            speaker = speaker,
            loreClass = loreClass,
            spriteId = spriteId,
            voiceAliases = voiceAliases ?: emptyList(),
            preferredVoiceId = preferredVoiceId,
            combat = combat,
            joinWhenFlag = joinWhenFlag,
            spellGrants = (spellGrants ?: emptyList()).map {
                SpellGrant(it.flag, it.spellIds ?: emptyList())
            },
            gender = gender,
            allowedVoiceRegions = allowedVoiceRegions ?: emptyList(),
            sprite = resolvedSprite,
            soundBites = soundBites.orEmpty().filter { it.isNotBlank() },
            outOfTurnLine = outOfTurnLine?.ifBlank { null }
        )
    }

    private fun SpriteDefinitionDto.toSpriteDefinition(): SpriteDefinition? {
        val pal = palette?.mapNotNull { (key, hex) ->
            val char = key.firstOrNull() ?: return@mapNotNull null
            char to parseHexColor(hex, Color.Transparent)
        }?.toMap() ?: emptyMap()

        val f = frames
        val idleUp = f?.idle_upright ?: emptyList()
        if (idleUp.isNotEmpty() && pal.isNotEmpty()) {
            val idleCr = if (!f?.idle_crouch.isNullOrEmpty()) f!!.idle_crouch!! else idleUp
            val actCast = if (!f?.action_cast.isNullOrEmpty()) f!!.action_cast!! else idleUp
            val dmg = if (!f?.damaged.isNullOrEmpty()) f!!.damaged!! else idleCr
            return SpriteDefinition(
                palette = pal,
                idleUpright = idleUp,
                idleCrouch = idleCr,
                actionCast = actCast,
                damaged = dmg,
                attack = f?.attack?.takeIf { it.isNotEmpty() },
                hurt = f?.hurt?.takeIf { it.isNotEmpty() },
                death = f?.death?.takeIf { it.isNotEmpty() }
            )
        }
        return null
    }

    private fun EnemyDto.toTemplate(): EnemyTemplate = EnemyTemplate(
        id = id,
        name = name.ifBlank { id },
        shortName = shortName?.trim().orEmpty(),
        subtitle = subtitle,
        family = family,
        selfElement = selfElement,
        hp = hp,
        attack = attack,
        defense = defense,
        speed = speed,
        movesetId = movesetId,
        isBoss = isBoss,
        spriteId = spriteId,
        spriteTint = parseHexColor(spriteTint, Color(0xFFE57373)),
        voiceAliases = voiceAliases?.filter { it.isNotBlank() } ?: emptyList(),
        soundBites = soundBites.orEmpty().filter { it.isNotBlank() },
        scale = scale?.takeIf { it.isFinite() }?.coerceIn(0.5f, 3f) ?: if (isBoss) 2.0f else 1.0f,
        taunts = taunts.orEmpty().filter { it.isNotBlank() },
        hitReaction = com.voicerpg.engine.model.HitReaction.parse(hitReaction),
        cutIn = cutIn
    )

    private fun EncounterDto.toDefinition(
        characters: Map<String, GameCharacter>,
        enemyTemplates: Map<String, EnemyTemplate>
    ): EncounterDefinition {
        val party = (party ?: emptyList()).mapNotNull { characters[it]?.toPartyMember() }
        val enemies = (enemies ?: emptyList()).mapNotNull { ref ->
            enemyTemplates[ref.templateId]?.toEnemy(
                instanceId = ref.id,
                nameOverride = ref.name,
                subtitleOverride = ref.subtitle,
                hpOverride = ref.hp,
                attackOverride = ref.attack,
                defenseOverride = ref.defense,
                speedOverride = ref.speed,
                isTargeted = ref.isTargeted,
                atbGauge = ref.atbGauge
            )
        }
        val envId = environmentId?.ifBlank { null } ?: environment
        return EncounterDefinition(
            id = id,
            name = name.ifBlank { id },
            description = description,
            environment = runCatching { BattleEnvironment.valueOf(environment.uppercase()) }.getOrDefault(BattleEnvironment.FOREST),
            environmentId = envId,
            enemies = enemies,
            initialParty = party.ifEmpty { null },
            bossPhases = (bossPhases ?: emptyList()).map {
                BossPhaseRule(
                    enemyId = it.enemyId,
                    hpBelow = it.hpBelow,
                    bannerText = it.bannerText,
                    narrationText = it.narrationText,
                    reducePartyToHpPct = it.reducePartyToHpPct,
                    executeBelowHpPct = it.executeBelowHpPct,
                    summonEnemyId = it.summonEnemyId,
                    summonCount = it.summonCount
                )
            },
            midBattleJoin = midBattleJoin?.let {
                MidBattleJoin(
                    characterId = it.characterId,
                    bannerText = it.bannerText,
                    announcementText = it.announcementText,
                    afterEnemyActions = it.afterEnemyActions
                )
            },
            intensityTrack = intensityTrack?.takeIf { it.isNotBlank() }
        )
    }

    private fun SceneDto.toScene(): StoryScene = StoryScene(
        id = id,
        name = name.ifBlank { id },
        chapterTitle = chapterTitle,
        backgroundAsset = backgroundAsset,
        initialNodeId = initialNodeId,
        ambientDescription = ambientDescription,
        musicAsset = musicAsset,
        chapterIndex = chapterIndex,
        actNumber = actNumber,
        actTitle = actTitle,
        milestoneTitle = milestoneTitle.ifBlank { chapterTitle },
        milestoneLocation = milestoneLocation.ifBlank { name },
        milestoneSummary = milestoneSummary,
        milestoneIcon = milestoneIcon,
        defaultObjective = defaultObjective
    )

    private fun NodeDto.toNode(speakers: Map<String, DialogueSpeaker>): DialogueNode = DialogueNode(
        id = id,
        speaker = speakers[speakerId] ?: DialogueSpeaker.NARRATOR,
        side = runCatching { SpeakerSide.valueOf(side) }.getOrDefault(SpeakerSide.CENTER_NARRATOR),
        text = text,
        revisitText = revisitText,
        choices = (choices ?: emptyList()).map {
            DialogueChoice(
                id = it.id,
                text = it.text,
                voiceKeywords = it.voiceKeywords ?: emptyList(),
                nextNodeId = it.nextNodeId,
                completionFlag = it.completionFlag,
                requiredFlags = it.requiredFlags ?: emptyList(),
                metricDeltas = it.metricDeltas ?: emptyMap(),
                assignHeroClassId = it.assignHeroClassId,
                triggerMiniGameId = it.triggerMiniGameId
            )
        },
        nextNodeId = nextNodeId,
        triggerBattleEncounterId = triggerBattleEncounterId,
        triggerMiniGameId = triggerMiniGameId,
        changeSceneId = changeSceneId,
        setFlagOnEnter = setFlagOnEnter,
        hubCompletion = hubCompletion?.let {
            HubCompletion(it.requiredFlags ?: emptyList(), it.redirectToNodeId)
        },
        healPartyOnEnter = healPartyOnEnter,
        metricDeltas = metricDeltas ?: emptyMap(),
        metricOverrides = metricOverrides ?: emptyMap(),
        assignHeroClassId = assignHeroClassId,
        allowIncidents = if (blacklistIncidents == true) false else (allowIncidents ?: true),
        flagRoutes = (flagRoutes ?: emptyList())
            .filter { it.nextNodeId.isNotBlank() }
            .map { FlagRoute(it.requiredFlags ?: emptyList(), it.nextNodeId) }
    )

    private fun EnvironmentDefinitionDto.toDefinition(): EnvironmentDefinition = EnvironmentDefinition(
        id = id,
        displayName = displayName.ifBlank { id },
        icon = icon,
        loreLocation = loreLocation,
        subtitle = subtitle,
        ambientThemeColor = parseHexColor(ambientThemeColor, Color.White),
        backgroundAsset = backgroundAsset,
        aliases = aliases ?: emptyList(),
        overlayType = overlayType,
        artHorizon = artHorizon?.takeIf { it in 0.05f..0.95f },
        artHorizonPortrait = artHorizonPortrait?.takeIf { it in 0.05f..0.95f }
    )

    private fun StatusDefDto.toStatusDef(): StatusDef = StatusDef(
        kind = kind,
        tiers = (tiers ?: emptyMap()).mapValues { (_, t) -> StatusTier(t.turns, t.potPct, t.maxStacks) },
        resist = resist ?: emptyMap()
    )
}

/**
 * Absolute last-resort content used when no data files can be located (e.g. a
 * mispackaged build). Guarantees the engine boots into a playable sandbox state.
 */
object EmergencyFallback {
    fun build(): GameContentPack {
        val narrator = DialogueSpeaker.NARRATOR
        val scene = GameContentPack.FALLBACK_SCENE
        val node = GameContentPack.FALLBACK_NODE
        return GameContentPack(
            manifest = GameManifest(
                gameTitle = "VoiceRPG Engine",
                gameSubtitle = "No content files found",
                worldName = "the realm",
                initialSceneId = scene.id,
                initialNodeId = node.id,
                defaultEncounterId = "",
                startingAchievementId = "QUEST_BEGUN",
                victoryLine = "Victory! The battle is won.",
                defeatLine = "Defeat. Your voice fades.",
                titleBackgroundAsset = null,
                defaultHeroName = "Hero",
                defaultHeroTitle = "Adventurer",
                quickNames = emptyList(),
                characterTitles = emptyList(),
                creationTitle = null,
                creationSubtitle = null,
                creationEmbarkPrompt = "AWAKEN ➔",
                creationVoiceKeywords = emptyList(),
                defaultHeroPortrait = null,
                tutorialCompletionText = "",
                voicePermissionTitle = "",
                voicePermissionSubtitle = "",
                voicePermissionPrompt = "",
                selfieConfig = SelfieFilterConfig()
            ),
            characters = emptyMap(),
            classes = emptyList(),
            spells = emptyMap(),
            enemyTemplates = emptyMap(),
            encounters = emptyMap(),
            scenes = mapOf(scene.id to scene),
            nodes = mapOf(node.id to node),
            narrator = narrator,
            environments = emptyMap(),
            statusCatalog = emptyMap(),
            affinityMatrix = null,
            thesaurusRoots = emptyMap()
        )
    }
}

/**
 * Global access point to the active game's data files.
 *
 * On Android, call [initialize] with a Context (MainActivity does this at boot).
 * In JVM unit tests the pack is loaded directly from the repository's asset
 * directory on disk. All engine systems (story, combat, narration, saves, UI)
 * resolve characters, classes, spells, enemies, encounters, scenes, and nodes
 * through this object — nothing game-specific is hardcoded in engine classes.
 */
object GameContent {

    private const val CONTENT_ROOT = "game"

    @Volatile
    private var current: GameContentPack? = null

    // initialize/initializeFromDirectory/reset take the same lock as the lazy loader in [pack], so a
    // load already in progress on another thread can't finish afterwards and overwrite what they set.
    fun initialize(context: Context) = synchronized(this) {
        current = try {
            GameContentLoader(AssetContentSource(context.assets, CONTENT_ROOT)).load()
        } catch (t: Throwable) {
            android.util.Log.e("GameContent", "Failed to load game content from assets", t)
            loadFromFilesystem() ?: EmergencyFallback.build()
        }
    }

    /** Loads a content pack from a folder (tests, tools). A pack that fails to load is an error, never a silent fallback. */
    fun initializeFromDirectory(dir: File) = synchronized(this) {
        current = try {
            GameContentLoader(FileContentSource(dir)).load()
        } catch (t: Throwable) {
            throw IllegalStateException("Game content in ${dir.absolutePath} failed to load: ${t.message}", t)
        }
    }

    /** Clears the cached pack (used by tests to reload modified content). */
    fun reset() = synchronized(this) {
        current = null
    }

    val pack: GameContentPack
        get() {
            current?.let { return it }
            synchronized(this) {
                current?.let { return it }
                val loaded = loadFromFilesystem() ?: EmergencyFallback.build()
                current = loaded
                return loaded
            }
        }

    private fun loadFromFilesystem(): GameContentPack? {
        val candidates = listOf(
            File("$CONTENT_ROOT"),
            File("src/main/assets/$CONTENT_ROOT"),
            File("app/src/main/assets/$CONTENT_ROOT"),
            File("../app/src/main/assets/$CONTENT_ROOT")
        )
        for (dir in candidates) {
            if (dir.isDirectory) {
                // A content folder that exists but doesn't load is broken content: say so instead of
                // quietly running on the emergency pack (which would let tests pass on the wrong content).
                return try {
                    GameContentLoader(FileContentSource(dir)).load()
                } catch (t: Throwable) {
                    throw IllegalStateException("Game content in ${dir.absolutePath} failed to load: ${t.message}", t)
                }
            }
        }
        return null
    }

    // --- Convenience accessors ---
    val manifest: GameManifest get() = pack.manifest

    /** Whether this game has mana (MP, costs, attune); false when its manifest says `"combatResource": "none"`. */
    val usesMana: Boolean get() = pack.manifest.combatResource != "none"
    val scenes: Map<String, StoryScene> get() = pack.scenes
    val nodes: Map<String, DialogueNode> get() = pack.nodes
    val characters: Map<String, GameCharacter> get() = pack.characters
    val classes: List<HeroClassDef> get() = pack.classes
    val spells: Map<String, Spell> get() = pack.spells
    val encounters: Map<String, EncounterDefinition> get() = pack.encounters
    val enemyTemplates: Map<String, EnemyTemplate> get() = pack.enemyTemplates
    val enemySprites: Map<String, SpriteDefinition> get() = pack.enemySprites
    val spriteSheets: Map<String, SpriteSheet> get() = pack.spriteSheets
    val spriteSettings: Map<String, SpriteSetting> get() = pack.spriteSettings
    val enemyMovesets: Map<String, Moveset> get() = pack.enemyMovesets
    val narrator: DialogueSpeaker get() = pack.narrator
    val heroCharacter: GameCharacter? get() = pack.heroCharacter
    val allSpeakers: List<DialogueSpeaker> get() = pack.allSpeakers
    val initialScene: StoryScene get() = pack.initialScene
    val initialNode: DialogueNode get() = pack.initialNode
    val defaultEncounter: EncounterDefinition? get() = pack.defaultEncounter
    val preferredVoiceIds: Map<String, String> get() = pack.preferredVoiceIds
    val environments: Map<String, EnvironmentDefinition> get() = pack.environments
    val statusCatalog: Map<String, StatusDef> get() = pack.statusCatalog
    val affinityMatrix: AffinityMatrix? get() = pack.affinityMatrix
    val presentation: PresentationConfig get() = pack.presentation
    val thesaurusRoots: Map<SpellSchool, List<String>> get() = pack.thesaurusRoots
    val selfieConfig: SelfieFilterConfig get() = manifest.selfieConfig
    val metrics: Map<String, MetricDefinition> get() = pack.metrics
    val incidents: Map<String, IncidentDefinition> get() = pack.incidents
    val minigames: Map<String, MiniGameConfig> get() = pack.minigames

    fun metricById(id: String?): MetricDefinition? = pack.metrics[id]
    fun incidentById(id: String?): IncidentDefinition? = pack.incidents[id]
    fun minigameById(id: String?): MiniGameConfig? = pack.minigameById(id)

    fun speakerById(id: String?): DialogueSpeaker? = pack.speakerById(id)
    fun characterById(id: String?): GameCharacter? = pack.characterById(id)
    fun classById(id: String?): HeroClassDef = pack.classById(id)
    fun encounterById(id: String?): EncounterDefinition? = pack.encounterById(id)
    fun environmentById(id: String?): EnvironmentDefinition? = pack.environmentById(id)
    fun spellById(id: String?): Spell? = pack.spellById(id)
    fun spellsForClass(classId: String?): List<Spell> = pack.spellsForClass(classId)
    fun spellKitFor(memberId: String, heroClassId: String? = null): SpellKit = pack.spellKitFor(memberId, heroClassId)
    fun knownSpells(memberId: String, heroClassId: String?, flags: Map<String, Boolean>): List<Spell> = pack.knownSpells(memberId, heroClassId, flags)
    fun voiceAliasesFor(memberId: String?): List<String> = pack.voiceAliasesFor(memberId)
    fun enemyTemplateById(id: String?): EnemyTemplate? = pack.enemyTemplateById(id)
    fun enemySpriteById(id: String?): SpriteDefinition? = pack.enemySpriteById(id)
    fun movesetById(id: String?): Moveset? = pack.movesetById(id)
    fun heroSpellsFor(memberId: String = "hero"): List<Spell> = pack.heroSpellsFor(memberId)
    fun companionSpellsFor(memberId: String): List<Spell> = pack.companionSpellsFor(memberId)
    fun companionStarterSpellsFor(memberId: String): List<Spell> = pack.companionStarterSpellsFor(memberId)
    fun breathSpellFor(memberId: String): Spell? = pack.breathSpellFor(memberId)
    fun companionMember(memberId: String): PartyMember? = pack.companionMember(memberId)
    fun createDuoParty(): List<PartyMember> = pack.createDuoParty()
    fun createMinion(idSuffix: String, name: String = "Template Minion", subtitle: String = "Minion", hp: Int = 150): Enemy =
        pack.createMinion(idSuffix, name, subtitle, hp)

    val commandCatalogs: Map<String, com.voicerpg.engine.model.CommandCatalog> get() = pack.commandCatalogs

    fun commandCatalogFor(locale: String): com.voicerpg.engine.model.CommandCatalog {
        return pack.commandCatalogs[locale]
            ?: pack.commandCatalogs["en"]
            ?: DEFAULT_COMMAND_CATALOG
    }

    fun resolveMetaCommand(utterance: String, locale: String = "en"): com.voicerpg.engine.model.MetaCommand {
        val active = commandCatalogFor(locale).resolveMetaCommand(utterance)
        if (active != com.voicerpg.engine.model.MetaCommand.NONE) return active
        if (locale != "en") {
            return commandCatalogFor("en").resolveMetaCommand(utterance)
        }
        return com.voicerpg.engine.model.MetaCommand.NONE
    }

    fun resolveOrdinal(utterance: String, locale: String = "en"): Int {
        val active = commandCatalogFor(locale).resolveOrdinal(utterance)
        if (active != -1) return active
        if (locale != "en") {
            return commandCatalogFor("en").resolveOrdinal(utterance)
        }
        return -1
    }

    fun hasAction(utterance: String, actionName: String, locale: String = "en"): Boolean {
        if (commandCatalogFor(locale).hasAction(utterance, actionName)) return true
        if (locale != "en") {
            return commandCatalogFor("en").hasAction(utterance, actionName)
        }
        return false
    }

    fun hasGroup(utterance: String, groupName: String, locale: String = "en"): Boolean {
        if (commandCatalogFor(locale).hasGroup(utterance, groupName)) return true
        if (locale != "en") {
            return commandCatalogFor("en").hasGroup(utterance, groupName)
        }
        return false
    }

    val DEFAULT_COMMAND_CATALOG = com.voicerpg.engine.model.CommandCatalog(
        locale = "en",
        metaCommands = mapOf(
            com.voicerpg.engine.model.MetaCommand.STATUS_REPORT to listOf("status", "report", "health", "vitals", "how are we", "status report", "check status"),
            com.voicerpg.engine.model.MetaCommand.CHECK_ENEMIES to listOf("enemies", "targets", "foes", "check enemies", "who is left", "enemy report"),
            com.voicerpg.engine.model.MetaCommand.CHECK_PARTY to listOf("party", "team", "friends", "allies", "check party", "companion report"),
            com.voicerpg.engine.model.MetaCommand.OPEN_OPTIONS to listOf("options", "menu", "settings", "open options", "audio options"),
            com.voicerpg.engine.model.MetaCommand.TOGGLE_EYES_FREE to listOf("pocket mode", "screen off", "eyes free", "pocket", "lock screen", "screenless"),
            com.voicerpg.engine.model.MetaCommand.HELP to listOf("help", "what can i say", "commands", "how to play", "tutorial")
        ),
        ordinals = mapOf(
            0 to listOf("first", "one", "1", "left", "leader", "1st"),
            1 to listOf("second", "two", "2", "middle", "2nd"),
            2 to listOf("third", "three", "3", "right", "rear", "3rd"),
            3 to listOf("fourth", "four", "4", "4th"),
            4 to listOf("fifth", "five", "5", "5th"),
            5 to listOf("sixth", "six", "6", "6th")
        ),
        groups = mapOf(
            "self" to listOf("me", "myself", "self"),
            "party" to listOf("party", "team", "everyone", "us", "allies"),
            "all" to listOf("all", "every enemy", "both", "all enemies")
        ),
        actions = mapOf(
            "heal" to listOf("heal", "mend", "restore", "cure", "help"),
            "attack" to listOf("attack", "strike", "hit", "slash", "smite")
        ),
        progressionPhrases = listOf(
            "next", "continue", "proceed", "go on", "forward", "onward", "advance",
            "ahead", "let's go", "lets go", "lead on", "lead the way", "tell me more",
            "what happened", "and then", "yes", "okay", "alright", "sure", "step forward",
            "we will", "indeed", "speak on", "i am listening", "what lies ahead",
            "very well", "make haste", "into the fray", "fight", "attack", "strike"
        ),
        continueStoryPhrases = listOf(
            "commence story", "commence", "story", "continue", "onward", "proceed", "advance",
            "next", "glory", "glory awaits", "forward", "press on", "victory", "venture forth",
            "let's go", "leave", "done", "journey", "march", "we ride", "head out", "explore",
            "continue story", "next chapter", "march onward", "press forward", "lead on",
            "to the next", "venture onward", "awaken"
        ),
        restartBattlePhrases = listOf(
            "restart battle", "restart", "rematch", "retry", "fight again", "again", "redo",
            "once more", "run it back", "try again", "arise", "rebattle", "face them again",
            "challenge again", "not dead yet", "rise again", "strike again", "re-battle",
            "one more time", "restart fight", "reset"
        )
    )
}

/** Parses "#RRGGBB" or "#AARRGGBB" into a Compose color without touching android.graphics. */
fun parseHexColor(hex: String?, fallback: Color = Color.White): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val clean = hex.removePrefix("#").trim()
        val value = clean.toLong(16)
        val argb = when (clean.length) {
            6 -> 0xFF000000L or value
            8 -> value
            else -> return fallback
        }
        Color(argb)
    } catch (_: Exception) {
        fallback
    }
}
