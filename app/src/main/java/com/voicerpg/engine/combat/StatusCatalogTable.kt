package com.voicerpg.engine.combat

import com.voicerpg.engine.content.GameContent

// GENERATED from docs/combat-design/data/status-catalog.json - regenerate, do not hand-edit.
data class StatusTier(val turns: Int, val potPct: Float, val maxStacks: Int)
data class StatusDef(val kind: String, val tiers: Map<String, StatusTier>, val resist: Map<String, String>)

object StatusCatalogTable {
    const val RESIST_NORMAL = 1.0f
    const val RESIST_MILD = 0.6f
    const val RESIST_STRONG = 0.35f
    const val LANDING_MIN_TURNS = 1
    const val LANDING_MIN_POTENCY_FRACTION = 0.25f

    val DEFS: Map<String, StatusDef>
        get() {
            val contentCatalog = GameContent.statusCatalog
            return if (contentCatalog.isNotEmpty()) contentCatalog else FALLBACK_DEFS
        }

    val FALLBACK_DEFS: Map<String, StatusDef> = mapOf(
        "BURN" to StatusDef(kind = "DOT", tiers = mapOf("BASIC" to StatusTier(1, 0.1f, 1), "ADEPT" to StatusTier(2, 0.12f, 2), "MASTER" to StatusTier(3, 0.15f, 3), "MYTHIC" to StatusTier(4, 0.18f, 4), "TRANSCENDENTAL" to StatusTier(5, 0.22f, 5)), resist = mapOf("UNDEAD" to "strong", "CONSTRUCT" to "strong", "VERDANT" to "normal", "FLESH" to "normal", "VOID" to "mild", "RADIANT" to "mild", "SILENCE" to "normal")),
        "CHILL" to StatusDef(kind = "ATB_SLOW", tiers = mapOf("BASIC" to StatusTier(1, 0.25f, 1), "ADEPT" to StatusTier(2, 0.3f, 1), "MASTER" to StatusTier(2, 0.4f, 1), "MYTHIC" to StatusTier(3, 0.45f, 1), "TRANSCENDENTAL" to StatusTier(3, 0.5f, 1)), resist = mapOf("UNDEAD" to "strong", "CONSTRUCT" to "mild", "VERDANT" to "normal", "FLESH" to "normal", "VOID" to "normal", "RADIANT" to "normal", "SILENCE" to "normal")),
        "OVERLOAD" to StatusDef(kind = "STUN", tiers = mapOf("BASIC" to StatusTier(0, 0.2f, 1), "ADEPT" to StatusTier(0, 0.2f, 1), "MASTER" to StatusTier(1, 0.2f, 1), "MYTHIC" to StatusTier(1, 0.2f, 1), "TRANSCENDENTAL" to StatusTier(2, 0.2f, 1)), resist = mapOf("VERDANT" to "strong", "UNDEAD" to "strong", "CONSTRUCT" to "normal", "FLESH" to "normal", "VOID" to "mild", "RADIANT" to "mild", "SILENCE" to "mild")),
        "ROOT" to StatusDef(kind = "ATB_SLOW", tiers = mapOf("BASIC" to StatusTier(1, 0.35f, 1), "ADEPT" to StatusTier(2, 0.45f, 1), "MASTER" to StatusTier(2, 0.6f, 1), "MYTHIC" to StatusTier(3, 0.7f, 1), "TRANSCENDENTAL" to StatusTier(3, 0.8f, 1)), resist = mapOf("CONSTRUCT" to "strong", "UNDEAD" to "strong", "VOID" to "strong", "VERDANT" to "mild", "FLESH" to "normal", "RADIANT" to "normal", "SILENCE" to "mild")),
        "POISON" to StatusDef(kind = "DOT", tiers = mapOf("BASIC" to StatusTier(2, 0.08f, 1), "ADEPT" to StatusTier(2, 0.1f, 2), "MASTER" to StatusTier(3, 0.12f, 3), "MYTHIC" to StatusTier(3, 0.14f, 4), "TRANSCENDENTAL" to StatusTier(4, 0.16f, 5)), resist = mapOf("UNDEAD" to "strong", "CONSTRUCT" to "strong", "VERDANT" to "mild", "FLESH" to "normal", "VOID" to "mild", "RADIANT" to "normal", "SILENCE" to "strong")),
        "CORRODE" to StatusDef(kind = "DEBUFF_DEF", tiers = mapOf("BASIC" to StatusTier(1, 0.2f, 1), "ADEPT" to StatusTier(2, 0.2f, 1), "MASTER" to StatusTier(2, 0.2f, 1), "MYTHIC" to StatusTier(3, 0.2f, 1), "TRANSCENDENTAL" to StatusTier(3, 0.2f, 1)), resist = mapOf("CONSTRUCT" to "normal", "RADIANT" to "strong", "UNDEAD" to "mild", "VERDANT" to "normal", "FLESH" to "normal", "VOID" to "mild", "SILENCE" to "normal")),
        "WEAKEN" to StatusDef(kind = "DEBUFF_ATK", tiers = mapOf("BASIC" to StatusTier(1, 0.2f, 1), "ADEPT" to StatusTier(2, 0.2f, 1), "MASTER" to StatusTier(2, 0.2f, 1), "MYTHIC" to StatusTier(3, 0.2f, 1), "TRANSCENDENTAL" to StatusTier(3, 0.2f, 1)), resist = mapOf("all" to "normal")),
        "BLEED" to StatusDef(kind = "DOT", tiers = mapOf("BASIC" to StatusTier(1, 0.08f, 1), "ADEPT" to StatusTier(2, 0.1f, 1), "MASTER" to StatusTier(2, 0.12f, 1), "MYTHIC" to StatusTier(3, 0.14f, 1), "TRANSCENDENTAL" to StatusTier(3, 0.16f, 1)), resist = mapOf("UNDEAD" to "strong", "CONSTRUCT" to "strong", "VOID" to "strong", "VERDANT" to "mild", "FLESH" to "normal", "RADIANT" to "normal", "SILENCE" to "mild")),
        "BLESS" to StatusDef(kind = "BUFF", tiers = mapOf("BASIC" to StatusTier(2, 0.2f, 1), "ADEPT" to StatusTier(2, 0.2f, 1), "MASTER" to StatusTier(3, 0.2f, 1), "MYTHIC" to StatusTier(3, 0.2f, 1), "TRANSCENDENTAL" to StatusTier(4, 0.2f, 1)), resist = mapOf("none" to "normal")),
        "GUARD" to StatusDef(kind = "BUFF_DEF", tiers = mapOf("BASIC" to StatusTier(1, 0.2f, 1), "ADEPT" to StatusTier(1, 0.2f, 1), "MASTER" to StatusTier(2, 0.2f, 1), "MYTHIC" to StatusTier(2, 0.2f, 1), "TRANSCENDENTAL" to StatusTier(2, 0.2f, 1)), resist = mapOf("none" to "normal")),
    )
}
