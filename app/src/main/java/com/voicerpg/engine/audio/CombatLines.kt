package com.voicerpg.engine.audio

import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.ResonanceTier

/**
 * What combat says, and which setting it belongs to. Combat waits for every line it speaks, so every line is short:
 * names are shortened ("the Blighted Orc" → "Orc"), numbers stand alone, and nothing is said twice.
 */
enum class CombatSpeech {
    /** The spell and its resonance ("Fireball! Master!"), and the one-word result of the cast (Weak!, Broken!). */
    CALLOUT,

    /** Everything else about the fight: damage, heals, enemy moves and hits, statuses, guards. */
    REPORT,

    /** Reports an eyes-free player can't do without (whose turn, a hero falling, a boss charging its ultimate). */
    ESSENTIAL,

    /** Answers to the player (out of mana, not your turn, options) and the result of the battle: always spoken. */
    FEEDBACK
}

/**
 * Combat's spoken lines. Each is a template with `{placeholders}`; a game rewords any of them in
 * `combat/presentation.json` under `"speechLines"` (same keys), e.g. `{"hit": "{target} takes {amount}!"}`.
 */
object CombatLines {

    val DEFAULTS: Map<String, String> = mapOf(
        "callout" to "{spell}! {tier}!",
        "tag_weak" to "Weak!",
        "tag_resist" to "Resisted.",
        "tag_broken" to "Broken!",
        "tag_streak" to "{n} in a row!",
        "hit" to "{target}, {amount}.",
        "hits" to "{list}.",
        "defeated" to "{target} down.",
        "defeated_many" to "{list} down.",
        "heal" to "{target} heals {amount}.",
        "heals" to "Healed {list}.",
        "cleanse" to "{target} cleansed.",
        "empower" to "{spell} on {target}.",
        "mana" to "{target}, {amount} mana.",
        "guard" to "{hero} guards.",
        "enemy_move" to "{enemy}: {move}!",
        "enemy_hit" to "{target}, {amount}.",
        "status" to "{status}.",
        "fallen" to "{target} falls!",
        "tick" to "{target} takes {amount}.",
        "stunned" to "{target} stunned.",
        "turn" to "{hero}.",
        "turn_target" to "{hero}. Target: {target}.",
        "ready" to "{hero}, ready.",
        "phase" to "{enemy} changes tactics!",
        "ult_charge" to "{enemy} charges {move}!",
        "ult_release" to "{enemy}: {move}!",
        "reinforcements" to "{n} more arrive!",
        "taunt" to "{enemy}: {line}",
        "victory" to "Victory!",
        "defeat" to "Defeat.",
        "no_mana" to "Not enough mana.",
        "no_mana_attune" to "Not enough mana. Say attune.",
        "not_learned" to "{hero} hasn't learned {spell} yet."
    )

    /** The most words a line of each kind may have once filled in (checked against every game's content). */
    val WORD_LIMITS: Map<CombatSpeech, Int> = mapOf(
        CombatSpeech.CALLOUT to 6,
        CombatSpeech.REPORT to 8,
        CombatSpeech.ESSENTIAL to 8,
        CombatSpeech.FEEDBACK to 12
    )

    fun line(key: String, vararg values: Pair<String, Any>): String {
        val template = GameContent.presentation.speechLines[key]?.takeIf { it.isNotBlank() } ?: DEFAULTS.getValue(key)
        return values.fold(template) { acc, (k, v) -> acc.replace("{$k}", v.toString()) }.trim()
    }

    /** "Fireball! Master!" */
    fun callout(spell: String, tier: ResonanceTier) = line("callout", "spell" to spell, "tier" to tierWord(tier))

    /** "Weak! Broken! 3 in a row!" or blank. */
    fun tags(weak: Boolean, resisted: Boolean, broken: Boolean, streak: Int): String = listOfNotNull(
        if (weak) line("tag_weak") else null,
        if (resisted && !weak) line("tag_resist") else null,
        if (broken) line("tag_broken") else null,
        if (streak >= 2) line("tag_streak", "n" to streak) else null
    ).joinToString(" ")

    /** "Orc, 120. Orc down." / "Orc 120, Imp 80. Imp down." */
    fun damage(hits: List<Pair<String, Int>>, defeated: List<String>): String {
        val hitPart = when (hits.size) {
            0 -> ""
            1 -> line("hit", "target" to hits[0].first, "amount" to hits[0].second)
            else -> line("hits", "list" to hits.joinToString(", ") { "${it.first} ${it.second}" })
        }
        val downPart = when (defeated.size) {
            0 -> ""
            1 -> line("defeated", "target" to defeated[0])
            else -> line("defeated_many", "list" to defeated.joinToString(", "))
        }
        return "$hitPart $downPart".trim()
    }

    fun heals(heals: List<Pair<String, Int>>): String = when (heals.size) {
        0 -> ""
        1 -> line("heal", "target" to heals[0].first, "amount" to heals[0].second)
        else -> line("heals", "list" to heals.joinToString(", ") { "${it.first} ${it.second}" })
    }

    fun enemyHit(target: String, amount: Int, status: String?): String =
        (line("enemy_hit", "target" to target, "amount" to amount) + (status?.let { " " + line("status", "status" to it) } ?: "")).trim()

    /** BASIC → "Basic". */
    fun tierWord(tier: ResonanceTier): String = tier.name.lowercase().replaceFirstChar { it.uppercase() }

    /**
     * A name as combat says it: an enemy's own `shortName` when the content gives one, otherwise the last word of
     * the name without a parenthesised epithet or a leading "The" ("Grand Inquisitor Malakor" → "Malakor",
     * "Sir Cedric" → "Cedric", "The Freight Wall" → "Wall").
     */
    fun shortName(name: String, explicit: String? = null): String {
        explicit?.takeIf { it.isNotBlank() }?.let { return it }
        val plain = name.substringBefore("(").trim().removePrefix("The ").trim()
        return plain.split(' ').lastOrNull { it.isNotBlank() } ?: name
    }

    fun words(text: String): Int = text.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }
}
