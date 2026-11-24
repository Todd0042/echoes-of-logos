package com.voicerpg.engine.content

/**
 * How a game wants its battles to look, from the optional `combat/presentation.json`. Every field has a
 * neutral default, so a game without the file gets the full effects (AGENTS.md "Opt-In Features &
 * Compatibility"). Fields arrive with the effects that use them (docs/COMBAT_FEEL_PLAN.md §1.4).
 */
data class PresentationConfig(
    /** Multiplies shake (and later flash and hit-stop) strength. 0 = none, 1 = default. */
    val intensity: Float = 1.0f
) {
    companion object {
        const val MAX_INTENSITY = 2.0f
    }
}

/** Raw file shape: nullable so a missing field keeps its default (Gson ignores Kotlin defaults). */
internal data class PresentationFileDto(
    val intensity: Float? = null
) {
    fun toConfig(): PresentationConfig {
        val defaults = PresentationConfig()
        return PresentationConfig(
            intensity = intensity?.takeIf { it.isFinite() }?.coerceIn(0f, PresentationConfig.MAX_INTENSITY) ?: defaults.intensity
        )
    }
}
