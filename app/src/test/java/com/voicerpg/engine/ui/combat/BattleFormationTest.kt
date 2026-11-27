package com.voicerpg.engine.ui.combat

import com.voicerpg.engine.content.SheetFrame
import com.voicerpg.engine.content.SheetSlice
import com.voicerpg.engine.content.SpriteSheet
import com.voicerpg.engine.ui.sprites.SheetSprite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The battle formation (docs/COMBAT_PROGRESSION_PLAN.md §3.7) on synthetic sprites: fighters never shrink to fit,
 * they stand in staggered depth rows (4 = 2+2, 5 = 2+2+1, 6 = 2+2+2; 1-3 the diagonal), overlapping a little as
 * if standing in front of each other but never hiding a head; the acting fighter is drawn on top.
 */
class BattleFormationTest {

    private val density = 2.75f

    /** A side-view Aseprite sheet laid out like the sprite plan's: `head` and `feet` slices at the plan's spots. */
    private fun sheet(id: String, cell: Int, withHead: Boolean = true): SheetSprite {
        val u = cell / 64f
        val slices = buildMap {
            put("feet", SheetSlice("feet", (16 * u).toInt(), (55 * u).toInt(), (32 * u).toInt(), 4, (32 * u).toInt(), (55 * u).toInt() + 4))
            if (withHead) put("head", SheetSlice("head", (21 * u).toInt(), (8 * u).toInt(), (21 * u).toInt(), 6, 0, 0))
        }
        val frame = SheetFrame(0, 0, cell, cell, 0, 0, 100)
        return SheetSprite(id, SpriteSheet(id, "sheets/$id.png", cell, cell, listOf(frame), emptyMap(), slices))
    }

    private fun party(n: Int, cell: Int = 64) = (1..n).map { i ->
        BattleFormation.figureOf("p$i", sheet("p$i", cell), BattleFormation.PARTY_PIXEL_DP, density, flipped = false)
    }

    private fun check(figures: List<BattleFormation.Figure>, stage: BattleFormation.Stage, activeId: String?, front: List<String>? = null): BattleFormation.Formation {
        val formation = BattleFormation.place(figures, stage, activeId, front)
        val bad = FormationOracle.violations(figures, formation, stage, activeId)
        assertTrue("${figures.size} fighters, ${stage.width}x${stage.height}, active $activeId:\n${bad.joinToString("\n")}", bad.isEmpty())
        return formation
    }

    @Test
    fun `one to six fighters keep their full size at every arena size, every head visible`() {
        for ((w, h) in FormationOracle.ARENAS) for (n in 1..6) {
            val figures = party(n)
            val stage = FormationOracle.stage(w, h, density, facingRight = true)
            for (active in listOf<String?>(null) + figures.map { it.id }) {
                val f = check(figures, stage, active)
                assertTrue("$n at ${w}x$h fits without the last resort", f.fits)
                assertEquals("$n at ${w}x$h: no shrinking", 1f, f.scale, 0.0001f)
            }
        }
    }

    @Test
    fun `rows - 1 to 3 a diagonal, 4 is 2+2, 5 is 2+2+1, 6 is 2+2+2, back rows about 8 percent smaller`() {
        assertEquals(listOf(1), BattleFormation.rowSizes(1))
        assertEquals(listOf(1, 1), BattleFormation.rowSizes(2))
        assertEquals(listOf(1, 1, 1), BattleFormation.rowSizes(3))
        assertEquals(listOf(2, 2), BattleFormation.rowSizes(4))
        assertEquals(listOf(2, 2, 1), BattleFormation.rowSizes(5))
        assertEquals(listOf(2, 2, 2), BattleFormation.rowSizes(6))

        val stage = FormationOracle.stage(360f, 300f, density, facingRight = true)
        for (n in 4..6) {
            val f = BattleFormation.place(party(n), stage)
            val byRow = f.placements.groupBy { it.row }
            assertEquals(BattleFormation.rowSizes(n), byRow.keys.sorted().map { byRow.getValue(it).size })
            assertTrue(byRow.getValue(0).all { it.scale == 1f })
            assertEquals(BattleFormation.BACK_ROW_SCALE, byRow.getValue(byRow.keys.max()).first().scale, 0.001f)
            // Deeper rows stand further back on the floor (higher up) and draw first.
            for (r in 1 until byRow.size) {
                assertTrue(byRow.getValue(r).all { b -> byRow.getValue(r - 1).all { a -> b.feetY < a.feetY && b.z < a.z } })
            }
        }
    }

    @Test
    fun `each row is shifted half a slot so the heads behind show between the fighters in front`() {
        for ((w, h) in FormationOracle.ARENAS) for (n in 4..6) {
            val stage = FormationOracle.stage(w, h, density, facingRight = true)
            val f = BattleFormation.place(party(n), stage)
            val front = f.placements.filter { it.row == 0 }.sortedBy { it.x }
            val slot = front[1].x - front[0].x
            assertTrue("in-row gap $slot", slot > 0f)
            for (r in 1..f.placements.maxOf { it.row }) {
                val row = f.placements.filter { it.row == r }.sortedByDescending { it.x }
                val prev = f.placements.filter { it.row == r - 1 }.sortedByDescending { it.x }
                assertEquals("row $r of $n at ${w}x$h is half a slot behind", slot / 2f, prev[0].x - row[0].x, 1f)
            }
        }
    }

    @Test
    fun `the formation spreads across the flank on a phone instead of bunching up`() {
        val stage = FormationOracle.stage(360f, 300f, density, facingRight = true)
        val figures = party(6)
        val f = BattleFormation.place(figures, stage)
        val left = figures.indices.minOf { f.placements[it].body(figures[it]).left }
        val right = figures.indices.maxOf { f.placements[it].body(figures[it]).right }
        assertTrue("spans ${right - left} of ${stage.width}", right - left > 0.7f * stage.width)
    }

    @Test
    fun `front row - first fighters by default, or the ids the caller puts in front`() {
        val stage = FormationOracle.stage(360f, 300f, density, facingRight = true)
        val figures = party(5)
        val byDefault = BattleFormation.place(figures, stage)
        assertEquals(setOf("p1", "p2"), byDefault.placements.filter { it.row == 0 }.map { it.id }.toSet())
        val chosen = check(figures, stage, activeId = null, front = listOf("p5", "p3"))
        assertEquals(setOf("p5", "p3"), chosen.placements.filter { it.row == 0 }.map { it.id }.toSet())
        // The first of the front row stands nearest the enemy.
        assertEquals("p5", chosen.placements.filter { it.row == 0 }.maxBy { it.x }.id)
    }

    @Test
    fun `the active fighter is drawn on top wherever it stands`() {
        val stage = FormationOracle.stage(360f, 220f, density, facingRight = true)
        val figures = party(6)
        for (a in figures) {
            val f = check(figures, stage, a.id)
            assertEquals(f.placements.maxOf { it.z }, f.of(a.id)!!.z)
        }
    }

    @Test
    fun `a boss among minions - 64, 96 and 128 pixel sheets mirrored on the enemy flank`() {
        for ((w, h) in FormationOracle.ARENAS) for (n in 1..6) {
            val sprites = (1..n).map { i -> when (i) { 1 -> sheet("boss", 128); 2 -> sheet("lieutenant", 96); else -> sheet("m$i", 64) } }
            val figures = sprites.mapIndexed { i, s -> BattleFormation.figureOf("e$i", s, BattleFormation.ENEMY_PIXEL_DP, density, flipped = true) }
            val stage = FormationOracle.stage(w, h, density, facingRight = false)
            // Bosses (the 96 and 128 px sheets) stand at the back, the biggest last, as the enemy flank's default order
            // puts them: in front they would hide a whole row.
            val front = figures.map { it.id }.drop(2) + figures.map { it.id }.drop(1).take(1) + "e0"
            val f = check(figures, stage, activeId = null, front = front)
            assertTrue("$n at ${w}x$h", f.fits)
            assertEquals("$n at ${w}x$h: no shrinking", 1f, f.scale, 0.0001f)
            // Facing left: the front row is nearest the party (left), the back edge on the right.
            if (n >= 4) assertTrue(f.placements.filter { it.row == 0 }.minOf { it.x } < f.placements.filter { it.row == 1 }.minOf { it.x })
        }
    }

    @Test
    fun `sheets without a head slice protect the top of the body`() {
        val figure = BattleFormation.figureOf("x", sheet("x", 64, withHead = false), BattleFormation.PARTY_PIXEL_DP, density, flipped = false)
        assertEquals(figure.body.top, figure.head.top, 0.01f)
        assertEquals(0.3f * figure.body.height, figure.head.height, 1f)
        val stage = FormationOracle.stage(360f, 220f, density, facingRight = true)
        check(List(6) { i -> figure.copy(id = "x$i") }, stage, activeId = "x3")
    }

    @Test
    fun `too small an arena shrinks as a last resort, never below the floor`() {
        val stage = FormationOracle.stage(220f, 120f, density, facingRight = true)
        val figures = party(6, cell = 96)
        val f = BattleFormation.place(figures, stage, activeId = "p4")
        assertTrue("shrank: ${f.scale}", f.scale < 1f)
        assertTrue("never below the floor: ${f.scale}", f.scale >= BattleFormation.MIN_SCALE - 0.0001f)
        f.placements.forEach { assertTrue(it.scale >= BattleFormation.MIN_SCALE * BattleFormation.BACK_ROW_SCALE - 0.0001f) }
        assertEquals(f.placements.maxOf { it.z }, f.of("p4")!!.z)
        if (f.fits) assertTrue(FormationOracle.violations(figures, f, stage, "p4").isEmpty())

        // A shrink that is enough is taken: one step down from full size where it suffices.
        val roomy = FormationOracle.stage(360f, 300f, density, facingRight = true)
        assertFalse(BattleFormation.place(party(6), roomy).scale < 1f)
    }
}
