package com.voicerpg.engine.combat

/**
 * Pure combat enums. Intentionally independent of Android/Compose models.
 * Names match model.SpellSchool / family tags so mapping is valueOf(name).
 */
enum class School(val displayName: String) {
    PYROMANCY("Pyromancy"),
    CRYOMANCY("Cryomancy"),
    ELECTROMANCY("Electromancy"),
    NATURE("Nature"),
    HOLY("Holy"),
    SHADOW("Shadow"),
    PHYSICAL("Physical");

    companion object {
        fun fromNameOrNull(name: String?): School? = if (name == null) null else entries.firstOrNull { it.name == name }
    }
}

enum class EnemyFamily {
    FLESH, UNDEAD, CONSTRUCT, VERDANT, VOID, RADIANT, SILENCE;

    companion object {
        fun fromNameOrNull(name: String?): EnemyFamily? = if (name == null) null else entries.firstOrNull { it.name == name }
    }
}

enum class StatusId {
    BURN, CHILL, FREEZE, ROOT, OVERLOAD, POISON, BLEED, CORRODE, WEAKEN, BLESS, GUARD;

    companion object {
        fun fromNameOrNull(name: String?): StatusId? = if (name == null) null else entries.firstOrNull { it.name == name }
    }
}

enum class MoveKind { BASIC, HEAVY, AOE, DRAIN, SUMMON, BUFF }

enum class MoveTargetRule { SINGLE_RANDOM, SINGLE_LOWEST_HP, SINGLE_MARKED, PARTY_AOE, SELF }
