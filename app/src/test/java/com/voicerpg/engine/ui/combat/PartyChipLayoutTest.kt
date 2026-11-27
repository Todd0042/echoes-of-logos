package com.voicerpg.engine.ui.combat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The party chips in the console: up to four use the full chip (name, HP number, gauges); five or six switch to a
 * compact chip (short name, HP bar, MP bar only for a fighter with mana, turn gauge) so all six fit a 360 dp row
 * without scrolling.
 */
class PartyChipLayoutTest {

    @Test
    fun `six compact chips fit a 360 dp row without scrolling`() {
        for (n in 5..6) {
            val layout = BattleHud.chipLayout(n, rowWidthDp = 360f)
            assertTrue("$n chips compact", layout.compact)
            assertFalse("$n chips: no scrolling", layout.scrolls)
            val used = n * layout.chipWidthDp + (n - 1) * layout.spacingDp
            assertTrue("$n chips use $used dp", used <= 360f + 0.01f)
            assertTrue(layout.chipWidthDp >= BattleHud.COMPACT_CHIP_MIN_DP)
        }
    }

    @Test
    fun `the console's narrower chip row still holds six`() {
        // A 360 dp phone: the console's padding and its listen and chime toggles leave the chips about 240 dp.
        val layout = BattleHud.chipLayout(6, rowWidthDp = 240f)
        assertFalse(layout.scrolls)
        assertTrue(6 * layout.chipWidthDp + 5 * layout.spacingDp <= 240f + 0.01f)
    }

    @Test
    fun `small parties keep the full chip`() {
        for (n in 1..4) assertFalse(BattleHud.chipLayout(n, rowWidthDp = 360f).compact)
    }

    @Test
    fun `a compact chip's name fits its width`() {
        val layout = BattleHud.chipLayout(6, rowWidthDp = 240f)
        for (name in listOf("Sir Cedric", "Commander America", "Aethel", "Al", "The Night Manager", "Q")) {
            val short = BattleHud.shortName(name)
            assertTrue("'$short' from '$name'", short.isNotBlank() && short.length <= BattleHud.SHORT_NAME_CHARS)
            assertTrue("'$short' is wider than the chip", BattleHud.compactNameWidthDp(short) <= layout.chipWidthDp - 2 * BattleHud.COMPACT_CHIP_PADDING_DP)
        }
        assertEquals("Cedric", BattleHud.shortName("Sir Cedric"))
        assertEquals("Aethel", BattleHud.shortName("Aethel"))
    }
}
