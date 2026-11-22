package com.voicerpg.engine.engine

import com.voicerpg.engine.model.MiniGameConfig
import com.voicerpg.engine.model.MiniGameOutcome
import com.voicerpg.engine.model.MiniGameOutcomeTier
import kotlin.random.Random

object MiniGameSimulator {

    fun simulate(
        config: MiniGameConfig,
        skillModifier: Float = 1.0f,
        fatigueModifier: Float = 1.0f,
        random: Random = Random.Default
    ): MiniGameOutcome {
        val safeFatigue = if (fatigueModifier <= 0f) 1.0f else fatigueModifier
        val baseRoll = random.nextFloat() * 40f + 60f // 60 to 100 base
        val scaledScore = (baseRoll * skillModifier / safeFatigue).coerceIn(0f, 100f)

        val tier = when {
            scaledScore >= 90f -> MiniGameOutcomeTier.FLAWLESS
            scaledScore >= 70f -> MiniGameOutcomeTier.SOLID
            scaledScore >= 45f -> MiniGameOutcomeTier.CLUMSY
            else -> MiniGameOutcomeTier.DISASTER
        }

        return MiniGameOutcome(
            tier = tier,
            score = scaledScore,
            metricDeltas = config.metricDeltasOnTier[tier] ?: emptyMap(),
            narrativeBlurb = config.blurbOnTier[tier] ?: ""
        )
    }

    /**
     * Compute tier and outcome from actual interactive gameplay score (0 - 100).
     */
    fun outcomeFromScore(
        config: MiniGameConfig,
        score: Float
    ): MiniGameOutcome {
        val clampedScore = score.coerceIn(0f, 100f)
        val tier = when {
            clampedScore >= 90f -> MiniGameOutcomeTier.FLAWLESS
            clampedScore >= 70f -> MiniGameOutcomeTier.SOLID
            clampedScore >= 45f -> MiniGameOutcomeTier.CLUMSY
            else -> MiniGameOutcomeTier.DISASTER
        }
        return MiniGameOutcome(
            tier = tier,
            score = clampedScore,
            metricDeltas = config.metricDeltasOnTier[tier] ?: emptyMap(),
            narrativeBlurb = config.blurbOnTier[tier] ?: ""
        )
    }
}
