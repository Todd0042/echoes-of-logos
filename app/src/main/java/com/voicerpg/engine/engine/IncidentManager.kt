package com.voicerpg.engine.engine

import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.IncidentCondition
import com.voicerpg.engine.model.IncidentConditionType
import com.voicerpg.engine.model.IncidentDefinition
import com.voicerpg.engine.viewmodel.StoryState
import kotlin.random.Random

object IncidentManager {

    fun evaluateCondition(condition: IncidentCondition, state: StoryState): Boolean {
        return when (condition.type) {
            IncidentConditionType.HAS_FLAG -> condition.flag?.let { state.narrativeFlags[it] == true } ?: false
            IncidentConditionType.NOT_FLAG -> condition.flag?.let { state.narrativeFlags[it] != true } ?: true
            IncidentConditionType.METRIC_GREATER_THAN -> {
                val value = condition.metricId?.let { state.dynamicMetrics[it] } ?: 0f
                value > condition.threshold
            }
            IncidentConditionType.METRIC_LESS_THAN -> {
                val value = condition.metricId?.let { state.dynamicMetrics[it] } ?: 0f
                value < condition.threshold
            }
            IncidentConditionType.NODE_VISITED -> condition.nodeId?.let { it in state.visitedNodeIds } ?: false
            IncidentConditionType.NODE_NOT_VISITED -> condition.nodeId?.let { it !in state.visitedNodeIds } ?: true
        }
    }

    fun calculateWeight(incident: IncidentDefinition, state: StoryState): Float {
        if (incident.oneTimeOnly && incident.triggerNodeId != null && incident.triggerNodeId in state.visitedNodeIds) {
            return 0f
        }

        if (incident.conditions.isNotEmpty() && !incident.conditions.all { evaluateCondition(it, state) }) {
            return 0f
        }

        var weight = incident.baseWeight
        for (mult in incident.weightMultipliers) {
            val metricVal = state.dynamicMetrics[mult.metricId] ?: 0f
            if (metricVal >= mult.threshold) {
                weight *= mult.multiplier
            }
        }
        return weight
    }

    fun selectRandomIncident(
        state: StoryState,
        random: Random = Random.Default,
        availableIncidents: Collection<IncidentDefinition> = GameContent.incidents.values
    ): IncidentDefinition? {
        if (availableIncidents.isEmpty()) return null

        val weighted = availableIncidents.mapNotNull { inc ->
            val w = calculateWeight(inc, state)
            if (w > 0f) inc to w else null
        }

        if (weighted.isEmpty()) return null

        val totalWeight = weighted.sumOf { it.second.toDouble() }
        if (totalWeight <= 0.0) return null

        val roll = random.nextDouble() * totalWeight
        var accum = 0.0
        for ((inc, w) in weighted) {
            accum += w
            if (roll <= accum) return inc
        }
        return weighted.firstOrNull()?.first
    }
}
