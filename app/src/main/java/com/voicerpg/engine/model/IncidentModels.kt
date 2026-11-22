package com.voicerpg.engine.model

enum class IncidentConditionType {
    HAS_FLAG,
    NOT_FLAG,
    METRIC_GREATER_THAN,
    METRIC_LESS_THAN,
    NODE_VISITED,
    NODE_NOT_VISITED
}

data class IncidentCondition(
    val type: IncidentConditionType,
    val flag: String? = null,
    val metricId: String? = null,
    val threshold: Float = 0f,
    val nodeId: String? = null
)

data class IncidentWeightMultiplier(
    val metricId: String,
    val threshold: Float,
    val multiplier: Float
)

data class IncidentDefinition(
    val id: String,
    val title: String,
    val baseWeight: Float = 10f,
    val conditions: List<IncidentCondition> = emptyList(),
    val weightMultipliers: List<IncidentWeightMultiplier> = emptyList(),
    val triggerNodeId: String? = null,
    val triggerEncounterId: String? = null,
    val oneTimeOnly: Boolean = false,
    val cooldownTurns: Int = 0,
    val applicableTracks: List<String> = emptyList()
)
