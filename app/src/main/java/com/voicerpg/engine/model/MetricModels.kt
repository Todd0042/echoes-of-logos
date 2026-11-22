package com.voicerpg.engine.model

/**
 * Generic, data-driven dynamic metric definition (e.g. morale, tension, heat, stability).
 * Entirely decoupled from game themes so any campaign (fantasy, retail, sci-fi) can declare its own HUD gauges.
 */
data class MetricDefinition(
    val id: String,
    val label: String,
    val icon: String = "",
    val minValue: Float = 0f,
    val maxValue: Float = 100f,
    val initialValue: Float = 50f,
    val isDangerHigh: Boolean = false,
    val displayColor: String? = null
)
