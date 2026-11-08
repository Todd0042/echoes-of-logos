package com.voicerpg.engine.model

import androidx.compose.ui.graphics.Color
import com.voicerpg.engine.combat.StatusInstance

enum class SpellSchool(val displayName: String, val themeColor: Color) {
    PYROMANCY("Pyromancy", Color(0xFFFF5722)),
    CRYOMANCY("Cryomancy", Color(0xFF00E5FF)),
    ELECTROMANCY("Electromancy", Color(0xFFE040FB)),
    NATURE("Nature", Color(0xFF66BB6A)),
    HOLY("Holy Restoration", Color(0xFFFFD700)),
    SHADOW("Shadow Arts", Color(0xFF7C4DFF)),
    PHYSICAL("Martial", Color(0xFFCFD8DC))
}

enum class SpellCategory(
    val label: String,
    val icon: String,
    val displayName: String,
    val tagColor: Color,
    val tagBg: Color
) {
    ATTACK("ATK", "⚔️", "Attack", Color(0xFFFF5252), Color(0x33FF5252)),
    AOE_ATTACK("AOE", "💥", "AoE Attack", Color(0xFFFF7043), Color(0x33FF7043)),
    HEAL("HEAL", "💚", "Heal", Color(0xFF69F0AE), Color(0x3369F0AE)),
    DEFENSE("DEF", "🛡️", "Defensive", Color(0xFF64B5F6), Color(0x3364B5F6)),
    MANA("MANA", "✨", "Mana Restore", Color(0xFF40C4FF), Color(0x3340C4FF))
}

data class SpellGraphics(
    val effectType: String = "PROJECTILE", // PROJECTILE, SLASH, PILLAR, BURST, WAVE
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val particleCount: Int = 16,
    val screenShake: Float = 0f
)

data class Spell(
    val id: String,
    val name: String,
    val school: SpellSchool,
    val basePower: Int,
    val mpCost: Int,
    val isHeal: Boolean = false,
    val hitsAll: Boolean = false,
    val description: String,
    val exampleChant: String,
    val status: String? = null,
    val lifesteal: Boolean = false,
    val isGuard: Boolean = false,
    val manaRestorePct: Float = 0f,
    val aliases: List<String> = emptyList(),
    val graphics: SpellGraphics? = null,
    val cleansesDebuffs: Boolean = false
) {
    val isCleanse: Boolean get() = cleansesDebuffs || status.equals("CLEANSE", ignoreCase = true) || status.equals("DISPEL", ignoreCase = true)

    val category: SpellCategory
        get() = when {
            isHeal -> SpellCategory.HEAL
            manaRestorePct > 0f -> SpellCategory.MANA
            isGuard || (basePower == 0 && !hitsAll) -> SpellCategory.DEFENSE
            hitsAll -> SpellCategory.AOE_ATTACK
            else -> SpellCategory.ATTACK
        }
}

enum class CharacterStance {
    READY,
    CASTING,
    DAMAGED,
    DEAD,
    VICTORY
}

data class PartyMember(
    val id: String,
    val name: String,
    val loreClass: String,
    val currentHp: Int,
    val maxHp: Int,
    val currentMp: Int,
    val maxMp: Int,
    val spells: List<Spell>,
    val stance: CharacterStance = CharacterStance.READY,
    val avatarTint: Color = Color(0xFF4FC3F7),
    val speed: Int = 60,
    val atbGauge: Float = 0f,
    val level: Int = 1,
    val xp: Int = 0,
    val defense: Int = 0,
    val statuses: List<StatusInstance> = emptyList(),
    val isGuarding: Boolean = false,
    val isGhost: Boolean = false,
    val canAttack: Boolean = true,
    val isUntargetable: Boolean = false,
    val spriteAlpha: Float? = null
) {
    val isAlive: Boolean get() = (isGhost || currentHp > 0) && stance != CharacterStance.DEAD
    val isTurnReady: Boolean get() = isAlive && atbGauge >= 1.0f
    val hpRatio: Float get() = if (isGhost) 1.0f else if (isAlive) (currentHp.toFloat() / maxHp).coerceIn(0f, 1f) else 0f
    val mpRatio: Float get() = if (isGhost) 1.0f else if (isAlive) (currentMp.toFloat() / maxMp).coerceIn(0f, 1f) else 0f
    val atbRatio: Float get() = if (isAlive) atbGauge.coerceIn(0f, 1f) else 0f
}

data class Enemy(
    val id: String,
    val name: String,
    val subtitle: String,
    val currentHp: Int,
    val maxHp: Int,
    val baseAttack: Int,
    val isBoss: Boolean = false,
    val isTargeted: Boolean = false,
    val isDamagedFlash: Boolean = false,
    val spriteTint: Color = Color(0xFFE57373),
    val spriteId: String = "",
    val speed: Int = 50,
    val atbGauge: Float = 0f,
    val family: String = "FLESH",
    val selfElement: String = "",
    val defense: Int = 0,
    val xpReward: Int = 60,
    val movesetId: String = "",
    val statuses: List<StatusInstance> = emptyList(),
    val moveCooldowns: Map<String, Int> = emptyMap()
) {
    val isAlive: Boolean get() = currentHp > 0
    val isTurnReady: Boolean get() = isAlive && atbGauge >= 1.0f
    val hpRatio: Float get() = if (isAlive) (currentHp.toFloat() / maxHp).coerceIn(0f, 1f) else 0f
    val atbRatio: Float get() = if (isAlive) atbGauge.coerceIn(0f, 1f) else 0f
}

enum class TargetSelection {
    FIRST_ALIVE_ENEMY,
    SPECIFIC_ENEMY,
    ORC,
    ARCHER,
    SHAMAN,
    ALL_ENEMIES,
    PARTY_LOWEST,
    SELF,
    SPECIFIC_HERO,
    HERO,
    CEDRIC,
    LYRA,
    ZEPHYR,
    MARCO,
    VANCE,
    KASH,
    KENNEDY
}

enum class MetaCommand {
    NONE,
    STATUS_REPORT,
    CHECK_ENEMIES,
    CHECK_PARTY,
    TOGGLE_EYES_FREE,
    ENABLE_EYES_FREE,
    DISABLE_EYES_FREE,
    UNLOCK_SCREEN,
    LOCK_SCREEN,
    TOGGLE_AUTO_LISTEN,
    TOGGLE_NARRATION,
    TOGGLE_READ_CHOICES,
    TOGGLE_SPEAKER_ATTRIBUTION,
    TOGGLE_MUSIC,
    OPEN_OPTIONS,
    CLOSE_OPTIONS,
    OPEN_BACKLOG,
    CLOSE_BACKLOG,
    FAST_FORWARD,
    STOP_FAST_FORWARD,
    STORY_RECAP,
    HELP,
    SPEED_UP_NARRATION,
    SLOW_DOWN_NARRATION,
    RESET_NARRATION_SPEED
}

data class ParsedIntent(
    val spell: Spell,
    val target: TargetSelection,
    val rawUtterance: String,
    val targetEnemyId: String? = null,
    val targetHeroId: String? = null,
    val metaCommand: MetaCommand = MetaCommand.NONE,
    val isWrongCharacterSpell: Boolean = false,
    val wrongCharacterOwnerId: String? = null,
    val wrongCharacterOwnerName: String? = null
)

/**
 * One-shot scripted boss phase, declared per encounter in data files. When the
 * referenced enemy's HP ratio drops below [hpBelow] (and the rule has not fired
 * yet), the engine announces [bannerText]/[narrationText], optionally crushes the
 * party toward [reducePartyToHpPct] (executing anyone under [executeBelowHpPct]),
 * and optionally summons [summonCount] instances of [summonEnemyId].
 */
data class BossPhaseRule(
    val enemyId: String,
    val hpBelow: Float,
    val bannerText: String = "",
    val narrationText: String = "",
    val reducePartyToHpPct: Float? = null,
    val executeBelowHpPct: Float? = null,
    val summonEnemyId: String? = null,
    val summonCount: Int = 0
)

/** Scripted mid-battle party addition, declared per encounter in data files. */
data class MidBattleJoin(
    val characterId: String,
    val bannerText: String = "",
    val announcementText: String = "",
    val afterEnemyActions: Int = 1
)

data class EncounterDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val environment: BattleEnvironment,
    val environmentId: String = "",
    val enemies: List<Enemy>,
    val initialParty: List<PartyMember>? = null,
    val bossPhases: List<BossPhaseRule> = emptyList(),
    val midBattleJoin: MidBattleJoin? = null
)

data class FloatingCombatText(
    val id: Long = System.nanoTime(),
    val text: String,
    val color: Color,
    val startX: Float,
    val startY: Float,
    val isCrit: Boolean = false,
    val isHeal: Boolean = false
)

enum class CombatantFaction {
    NONE,
    HERO,
    ENEMY
}

enum class CombatPhase {
    INACTIVE,
    ATB_WAITING,
    PLAYER_INPUT,
    INCANTATION_RESOLVING,
    SPELL_VFX_PLAYING,
    ENEMY_ACTIONS,
    BATTLE_WON,
    BATTLE_LOST
}
