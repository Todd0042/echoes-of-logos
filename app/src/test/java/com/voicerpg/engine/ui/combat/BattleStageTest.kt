package com.voicerpg.engine.ui.combat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Combatants stand on the arena floor (the band from the backdrop's horizon to the bottom edge) whatever
 * the arena's height: a tall console (developer tools, preset chants) makes the arena shorter, and the
 * formation must still keep every pair of feet on the floor instead of stacking up into the sky.
 */
class BattleStageTest {

    private val heights = listOf(
        620f, // phone portrait, normal console
        380f, // phone portrait, developer console + FX bench
        260f, // landscape with developer tools
        900f  // foldable / tablet
    )

    @Test
    fun `every combatant's feet land on the floor, for any formation and arena height`() {
        for (h in heights) for (count in 1..4) {
            val floorTop = BattleStage.horizonY(h)
            val floorBottom = BattleStage.floorBottomY(h, insetPx = 12f)
            for (i in 0 until count) {
                val feet = BattleStage.feetY(i, count, h, insetPx = 12f)
                assertTrue("h=$h n=$count #$i feet $feet below the horizon $floorTop", feet > floorTop)
                assertTrue("h=$h n=$count #$i feet $feet above the bottom $floorBottom", feet <= floorBottom + 0.01f)
            }
        }
    }

    @Test
    fun `the back of the formation stands deeper (higher) than the front, front at the bottom`() {
        for (h in heights) for (count in 2..4) {
            val feet = (0 until count).map { BattleStage.feetY(it, count, h, insetPx = 12f) }
            assertTrue("$feet", feet.zipWithNext().all { (a, b) -> a < b })
            assertEquals(BattleStage.floorBottomY(h, 12f), feet.last(), 0.01f)
        }
    }

    @Test
    fun `the backdrop seam and the stage agree`() {
        assertEquals(0.43f * 500f, BattleStage.horizonY(500f), 0.01f)
    }
}
