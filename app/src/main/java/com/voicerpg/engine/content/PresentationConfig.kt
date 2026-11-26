package com.voicerpg.engine.content

import com.voicerpg.engine.model.ResonanceTier

/**
 * How a game wants its battles to look, from the optional `combat/presentation.json`. Every field has a
 * neutral default, so a game without the file gets the full effects (AGENTS.md "Opt-In Features &
 * Compatibility"). Schema: docs/COMBAT_FEEL_PLAN.md §1.4. Unknown or out-of-range values fall back.
 */
data class PresentationConfig(
    /** Multiplies shake, hit-stop and knockback strength. 0 = none, 1 = default, up to [MAX_INTENSITY]. */
    val intensity: Float = 1.0f,
    /** How move names are announced: engine-drawn card styles. */
    val titleCardStyle: TitleCardStyle = TitleCardStyle.BANNER,
    val damageNumberStyle: DamageNumberStyle = DamageNumberStyle.POP,
    val labels: Labels = Labels(),
    val cutIn: CutIn = CutIn(),
    val streak: Streak = Streak(),
    val telegraph: Telegraph = Telegraph(),
    val deathEffect: DeathEffect = DeathEffect.SHATTER,
    val music: Music = Music(),
    /** Rewording of combat's spoken lines, by key (audio/CombatLines.DEFAULTS lists the keys and defaults). */
    val speechLines: Map<String, String> = emptyMap()
) {
    /** Words shown on damage numbers; `{tier}` becomes the tier name (MYTHIC). */
    data class Labels(val weak: String = "WEAK!", val resist: String = "RESIST", val crit: String = "{tier}!") {
        fun critFor(tier: ResonanceTier): String = crit.replace("{tier}", tier.name)
    }

    /** A portrait panel for casts at or above [minTier]. */
    data class CutIn(val minTier: ResonanceTier = ResonanceTier.MYTHIC)

    /** Consecutive casts at or above [minTier] build a streak; `{n}` becomes the count. Visual only. */
    data class Streak(val label: String = "RESONANCE x{n}", val minTier: ResonanceTier = ResonanceTier.ADEPT)

    /** Enemy intent icons, and how many of its own turns a boss ultimate charges before it fires. */
    data class Telegraph(val showIntentIcons: Boolean = true, val bossUltimateChargeTurns: Int = 1)

    /** A second music track that fades in below [lowHpThreshold] party health and on boss phase changes. */
    data class Music(val intensityTrack: String? = null, val lowHpThreshold: Float = 0.3f)

    enum class TitleCardStyle { BANNER, HEADLINE, PA_ANNOUNCEMENT, SCROLL }
    enum class DamageNumberStyle { POP, RECEIPT, COMIC }
    enum class DeathEffect { SHATTER, FADE, DISSOLVE }

    companion object {
        const val MAX_INTENSITY = 2.0f
        const val MAX_CHARGE_TURNS = 3
    }
}

/** Raw file shape: nullable so a missing field keeps its default (Gson ignores Kotlin defaults). */
internal data class PresentationFileDto(
    val intensity: Float? = null,
    val titleCardStyle: String? = null,
    val damageNumberStyle: String? = null,
    val labels: LabelsDto? = null,
    val cutIn: CutInDto? = null,
    val streak: StreakDto? = null,
    val telegraph: TelegraphDto? = null,
    val deathEffect: String? = null,
    val music: MusicDto? = null,
    val speechLines: Map<String, String>? = null
) {
    data class LabelsDto(val weak: String? = null, val resist: String? = null, val crit: String? = null)
    data class CutInDto(val minTier: String? = null)
    data class StreakDto(val label: String? = null, val minTier: String? = null)
    data class TelegraphDto(val showIntentIcons: Boolean? = null, val bossUltimateChargeTurns: Int? = null)
    data class MusicDto(val intensityTrack: String? = null, val lowHpThreshold: Float? = null)

    fun toConfig(): PresentationConfig {
        val d = PresentationConfig()
        return PresentationConfig(
            intensity = intensity?.takeIf { it.isFinite() }?.coerceIn(0f, PresentationConfig.MAX_INTENSITY) ?: d.intensity,
            titleCardStyle = enumOr(titleCardStyle, d.titleCardStyle),
            damageNumberStyle = enumOr(damageNumberStyle, d.damageNumberStyle),
            labels = PresentationConfig.Labels(
                weak = labels?.weak?.takeIf { it.isNotBlank() } ?: d.labels.weak,
                resist = labels?.resist?.takeIf { it.isNotBlank() } ?: d.labels.resist,
                crit = labels?.crit?.takeIf { it.isNotBlank() } ?: d.labels.crit
            ),
            cutIn = PresentationConfig.CutIn(minTier = enumOr(cutIn?.minTier, d.cutIn.minTier)),
            streak = PresentationConfig.Streak(
                label = streak?.label?.takeIf { it.isNotBlank() } ?: d.streak.label,
                minTier = enumOr(streak?.minTier, d.streak.minTier)
            ),
            telegraph = PresentationConfig.Telegraph(
                showIntentIcons = telegraph?.showIntentIcons ?: d.telegraph.showIntentIcons,
                bossUltimateChargeTurns = telegraph?.bossUltimateChargeTurns?.coerceIn(0, PresentationConfig.MAX_CHARGE_TURNS)
                    ?: d.telegraph.bossUltimateChargeTurns
            ),
            deathEffect = enumOr(deathEffect, d.deathEffect),
            music = PresentationConfig.Music(
                intensityTrack = music?.intensityTrack?.takeIf { it.isNotBlank() },
                lowHpThreshold = music?.lowHpThreshold?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: d.music.lowHpThreshold
            ),
            speechLines = speechLines.orEmpty().filterValues { it.isNotBlank() }
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(raw: String?, fallback: E): E =
        raw?.let { r -> enumValues<E>().firstOrNull { it.name.equals(r.trim(), ignoreCase = true) } } ?: fallback
}
