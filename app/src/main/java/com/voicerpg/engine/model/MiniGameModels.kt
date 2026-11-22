package com.voicerpg.engine.model

enum class MiniGameOutcomeTier {
    FLAWLESS,
    SOLID,
    CLUMSY,
    DISASTER
}

data class MiniGameOutcome(
    val tier: MiniGameOutcomeTier,
    val score: Float,
    val metricDeltas: Map<String, Float> = emptyMap(),
    val narrativeBlurb: String = "",
    val completionFlag: String? = null
)

data class MiniGameConfig(
    val id: String,
    val title: String,
    val department: String = "",
    val description: String = "",
    val durationSeconds: Int = 45,
    val targetSpeed: Float = 1.0f,
    val errorPenalty: Float = 10f,
    val quota: Int = 20,
    val metricDeltasOnTier: Map<MiniGameOutcomeTier, Map<String, Float>> = emptyMap(),
    val blurbOnTier: Map<MiniGameOutcomeTier, String> = emptyMap()
)
