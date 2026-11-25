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

/**
 * The backdrop art moves with the stage: the art's painted horizon (where its floor starts) is drawn exactly on
 * the stage horizon at any arena size, so a shorter arena (taller console) can't slide the painted ground out
 * from under the combatants. The crop stays inside the picture and keeps the arena's aspect (no stretching).
 */
class BackdropFitTest {

    private val arenas = listOf(1080f to 1240f, 1080f to 760f, 1080f to 520f, 2400f to 700f, 1600f to 1800f)
    private val pictures = listOf(1376 to 768, 432 to 768)
    private val artHorizons = listOf(0.45f, 0.53f, 0.58f, 0.66f)

    @Test
    fun `the painted horizon lands on the stage horizon, crop inside the picture, no stretching`() {
        for ((w, h) in arenas) for ((sw, sh) in pictures) for (hz in artHorizons) {
            val crop = BattleStage.backdropCrop(sw, sh, hz, w, h)
            val where = "arena ${w}x$h picture ${sw}x$sh horizon $hz: $crop"
            assertTrue(where, crop.left >= -0.5f && crop.top >= -0.5f)
            assertTrue(where, crop.left + crop.width <= sw + 0.5f && crop.top + crop.height <= sh + 0.5f)
            assertEquals(where, w / h, crop.width / crop.height, 0.01f)
            val paintedHorizonOnScreen = (hz * sh - crop.top) / crop.height * h
            assertEquals(where, BattleStage.horizonY(h), paintedHorizonOnScreen, 1f)
        }
    }

    @Test
    fun `toggling the console keeps the art and the stage together`() {
        // Same picture, arena shrinks from the normal console to the developer console.
        for (h in listOf(1240f, 760f)) {
            val crop = BattleStage.backdropCrop(1376, 768, 0.53f, 1080f, h)
            assertEquals(BattleStage.horizonY(h), (0.53f * 768 - crop.top) / crop.height * h, 1f)
        }
    }
}
