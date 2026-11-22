package com.voicerpg.engine.model

/**
 * Categorical resonance tiers supporting up to +200% Damage Multiplier (3.0x Base Damage).
 */
enum class ResonanceTier(
    val title: String,
    val minBonusPercent: Int,
    val maxBonusPercent: Int,
    val particleMultiplier: Float,
    val baseParticleCount: Int
) {
    BASIC("Basic Chant", 0, 15, 1.0f, 40),
    ADEPT("Adept Resonance", 20, 50, 1.6f, 90),
    MASTER("Master Incantation", 55, 95, 2.4f, 180),
    MYTHIC("Mythic Logos", 100, 150, 3.2f, 300),
    TRANSCENDENTAL("Transcendental Logos", 155, 200, 4.5f, 450);

    companion object {
        fun fromBonusPercent(bonusPercent: Int): ResonanceTier = when {
            bonusPercent >= 155 -> TRANSCENDENTAL
            bonusPercent >= 100 -> MYTHIC
            bonusPercent >= 55 -> MASTER
            bonusPercent >= 20 -> ADEPT
            else -> BASIC
        }
    }
}

/**
 * Result of evaluating an incantation via the Incantation Resonance Engine.
 * Supports up to +200% damage multiplier graded across thematic lexicon, lexical cadence,
 * acoustic vocal volume projection, pitch inflection & dynamics, and novelty.
 */
data class ResonanceResult(
    val rawText: String,
    val score: Float, // Normalized 0.0 to 1.0
    val bonusPercent: Int, // 0% to 200%
    val tier: ResonanceTier,
    val damageMultiplier: Float, // 1.0x to 3.0x (up to +200% bonus!)
    val particleCount: Int, // 40 to 450+ particles
    val matchedThematicRoots: List<String>,
    val syllableCount: Int,
    val acousticProfile: AcousticProfile = AcousticProfile(),
    val isNovel: Boolean = true,
    val repetitionDecayApplied: Float = 1.0f
) {
    val bonusPercentText: String get() = "+$bonusPercent%"
}
