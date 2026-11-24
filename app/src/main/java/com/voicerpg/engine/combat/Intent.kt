package com.voicerpg.engine.combat

/**
 * What an enemy has telegraphed it will do next, shown as an icon above it (M4). Derived from the
 * planned move; a move can override the icon with `intentIcon` in enemy_spells.json.
 */
enum class Intent(val icon: String) {
    ATTACK("⚔"),
    AOE("✸"),
    BUFF("⬆"),
    SUMMON("✚"),
    DRAIN("🩸"),
    ULTIMATE("☠");

    companion object {
        fun of(move: EnemyMove, isUltimate: Boolean): Intent = when {
            isUltimate -> ULTIMATE
            move.kind == "SUMMON" -> SUMMON
            move.kind == "BUFF" -> BUFF
            move.kind == "DRAIN" -> DRAIN
            move.kind == "AOE" || move.targetRule == "PARTY_AOE" -> AOE
            else -> ATTACK
        }

        /** A boss move strong enough to be its ultimate (it telegraphs and charges first). */
        fun isUltimate(isBoss: Boolean, move: EnemyMove): Boolean = isBoss && move.powerMult >= ULTIMATE_POWER

        const val ULTIMATE_POWER = 2.0f
    }
}
