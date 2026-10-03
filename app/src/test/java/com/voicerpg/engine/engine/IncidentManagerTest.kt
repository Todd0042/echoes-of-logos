package com.voicerpg.engine.engine

import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.IncidentCondition
import com.voicerpg.engine.model.IncidentConditionType
import com.voicerpg.engine.model.IncidentDefinition
import com.voicerpg.engine.model.IncidentWeightMultiplier
import com.voicerpg.engine.viewmodel.StoryState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentManagerTest {

    @Test
    fun testEvaluateConditionFlagsAndMetrics() {
        val state = StoryState(
            narrativeFlags = mapOf("flag_active" to true),
            dynamicMetrics = mapOf("metric_heat" to 75f, "metric_morale" to 30f)
        )

        // Flag checks
        assertTrue(IncidentManager.evaluateCondition(IncidentCondition(IncidentConditionType.HAS_FLAG, flag = "flag_active"), state))
        assertFalse(IncidentManager.evaluateCondition(IncidentCondition(IncidentConditionType.HAS_FLAG, flag = "missing_flag"), state))
        assertTrue(IncidentManager.evaluateCondition(IncidentCondition(IncidentConditionType.NOT_FLAG, flag = "missing_flag"), state))

        // Metric checks
        assertTrue(IncidentManager.evaluateCondition(IncidentCondition(IncidentConditionType.METRIC_GREATER_THAN, metricId = "metric_heat", threshold = 50f), state))
        assertFalse(IncidentManager.evaluateCondition(IncidentCondition(IncidentConditionType.METRIC_GREATER_THAN, metricId = "metric_heat", threshold = 80f), state))
        assertTrue(IncidentManager.evaluateCondition(IncidentCondition(IncidentConditionType.METRIC_LESS_THAN, metricId = "metric_morale", threshold = 50f), state))
    }

    @Test
    fun testWeightMultipliersScaleCorrectly() {
        val incident = IncidentDefinition(
            id = "test_event",
            title = "Test Incident",
            baseWeight = 10f,
            conditions = listOf(
                IncidentCondition(IncidentConditionType.METRIC_GREATER_THAN, metricId = "risk", threshold = 20f)
            ),
            weightMultipliers = listOf(
                IncidentWeightMultiplier(metricId = "risk", threshold = 50f, multiplier = 2.0f)
            )
        )

        // Condition fails
        val lowState = StoryState(dynamicMetrics = mapOf("risk" to 10f))
        assertEquals(0f, IncidentManager.calculateWeight(incident, lowState), 0.01f)

        // Condition passes without multiplier
        val midState = StoryState(dynamicMetrics = mapOf("risk" to 30f))
        assertEquals(10f, IncidentManager.calculateWeight(incident, midState), 0.01f)

        // Condition passes with multiplier
        val highState = StoryState(dynamicMetrics = mapOf("risk" to 60f))
        assertEquals(20f, IncidentManager.calculateWeight(incident, highState), 0.01f)
    }

    @Test
    fun testGracefulHandlingWhenIncidentsEmptyOrPresent() {
        val metrics = GameContent.metrics
        val incidents = GameContent.incidents
        assertNotNull(metrics)
        assertNotNull(incidents)

        val state = StoryState(
            narrativeFlags = mapOf("flag_sample" to true),
            dynamicMetrics = mapOf("metric_a" to 75f)
        )

        if (incidents.isEmpty()) {
            val selected = IncidentManager.selectRandomIncident(state, Random(42))
            assertNull("When incidents are empty, selectRandomIncident must return null without error", selected)
        } else {
            val selected = IncidentManager.selectRandomIncident(state, Random(42))
            // May or may not match depending on conditions, but must not throw
        }
    }
}
