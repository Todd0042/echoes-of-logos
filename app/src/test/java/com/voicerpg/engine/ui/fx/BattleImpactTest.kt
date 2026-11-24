package com.voicerpg.engine.ui.fx

import androidx.compose.ui.geometry.Offset
import com.voicerpg.engine.combat.BattleCue
import com.voicerpg.engine.content.PresentationConfig
import com.voicerpg.engine.model.ResonanceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M2 (impact and numbers), presentation side: an Impact cue freezes the arena for a moment scaled by
 * tier, flashes and knocks back each target, and pops a labelled damage number per hit; area hits
 * stagger. Transcendental hits add slow motion, a zoom punch and a full-screen flash, which the comfort
 * options remove.
 */
class BattleImpactTest {

    private val frame60 = 1_000_000_000L / 60
    private val positions = mapOf("hero" to Offset(200f, 600f), "orc" to Offset(900f, 500f), "imp" to Offset(900f, 700f), "bat" to Offset(950f, 400f))

    private fun director(reduceMotion: Boolean = false, reduceFlashing: Boolean = false) =
        BattleFxDirector(positionOf = { positions.getValue(it) }).also {
            it.presentation = PresentationConfig()
            it.reduceMotion = reduceMotion
            it.reduceFlashing = reduceFlashing
        }

    private fun heroHit(tier: ResonanceTier, vararg hits: BattleCue.Hit) =
        BattleCue.Impact(sourceId = "hero", fromParty = true, hits = hits.toList(), tier = tier)

    /** Runs [seconds] of 60 Hz frames, calling [each] after every frame. */
    private fun BattleFxDirector.runFor(seconds: Float, each: (Int) -> Unit = {}) {
        var now = 1_000_000_000L
        frame(now)
        repeat((seconds * 60).toInt()) { i ->
            now += frame60
            frame(now)
            each(i)
        }
    }

    @Test
    fun `stronger voices freeze the arena longer on impact`() {
        fun stopMs(tier: ResonanceTier, reduceMotion: Boolean = false): Long =
            director(reduceMotion = reduceMotion).hitStopMsFor(heroHit(tier, BattleCue.Hit("orc", 40)))
        val basic = stopMs(ResonanceTier.BASIC)
        val transcendent = stopMs(ResonanceTier.TRANSCENDENTAL)
        assertTrue("60-180 ms: $basic..$transcendent", basic in 50..70 && transcendent in 170..190)
        assertTrue(ResonanceTier.entries.zipWithNext().all { (a, b) -> stopMs(a) <= stopMs(b) })
        assertTrue("reduce motion keeps it short", stopMs(ResonanceTier.TRANSCENDENTAL, reduceMotion = true) <= 60)

        val d = director()
        d.play(heroHit(ResonanceTier.MASTER, BattleCue.Hit("orc", 40)))
        assertEquals("frozen right after the blow", 0f, d.clock.timeScale, 0f)
    }

    @Test
    fun `the target flashes white and is knocked away from the blow, then springs back`() {
        val d = director()
        d.play(heroHit(ResonanceTier.ADEPT, BattleCue.Hit("orc", 40)))
        val orc = d.fxOf("orc")
        assertTrue("flash starts at full", orc.flash > 0.9f)
        var maxPush = 0f
        d.runFor(0.8f) { maxPush = maxOf(maxPush, orc.knockback) }
        assertTrue("pushed away from the party side, 8-14 dp: $maxPush", maxPush in 7f..15f)
        assertEquals("springs back", 0f, orc.knockback, 0.5f)
        assertEquals("flash fades", 0f, orc.flash, 0.01f)

        val e = director()
        e.play(BattleCue.Impact("orc", fromParty = false, hits = listOf(BattleCue.Hit("hero", 30))))
        var minPush = 0f
        e.runFor(0.4f) { minPush = minOf(minPush, e.fxOf("hero").knockback) }
        assertTrue("an enemy's blow pushes the hero toward the left", minPush < -5f)
    }

    @Test
    fun `each hit pops a labelled number, and area hits stagger`() {
        val d = director()
        d.play(heroHit(ResonanceTier.MYTHIC,
            BattleCue.Hit("orc", 120, affinity = 1.6f),
            BattleCue.Hit("imp", 40, affinity = 0.5f),
            BattleCue.Hit("bat", 80)))
        assertEquals("the first lands at once", 1, d.numbers.size)
        d.runFor(0.2f)
        assertEquals(3, d.numbers.size)
        val byTarget = d.numbers.associateBy { it.targetId }
        assertEquals("-120", byTarget.getValue("orc").text)
        assertEquals("WEAK!", byTarget.getValue("orc").label)
        assertEquals("RESIST", byTarget.getValue("imp").label)
        assertEquals("MYTHIC!", byTarget.getValue("bat").label)
        assertTrue("staggered by 60 ms", byTarget.getValue("imp").bornAt - byTarget.getValue("orc").bornAt in 0.05f..0.08f)
        assertTrue("numbers pop large then settle", d.numbers.first().scaleAt(0f) > 1.4f && d.numbers.first().scaleAt(0.4f) in 0.95f..1.1f)

        val heal = director()
        heal.play(BattleCue.Impact("hero", true, listOf(BattleCue.Hit("hero", 50, kind = BattleCue.HitKind.HEAL), BattleCue.Hit("hero", 20, kind = BattleCue.HitKind.MANA))))
        heal.runFor(0.2f)
        assertEquals(listOf("+50", "+20 MP"), heal.numbers.map { it.text })

        d.runFor(2f)
        assertTrue("numbers clear away", d.numbers.isEmpty())
    }

    @Test
    fun `a transcendental hit slows time, punches the zoom and flashes the screen`() {
        val d = director()
        d.play(heroHit(ResonanceTier.TRANSCENDENTAL, BattleCue.Hit("orc", 300)))
        var peakZoom = 1f
        var peakFlash = 0f
        var sawSlowMo = false
        d.runFor(1f) {
            peakZoom = maxOf(peakZoom, d.zoom)
            peakFlash = maxOf(peakFlash, d.screenFlash)
            if (d.clock.timeScale in 0.2f..0.4f) sawSlowMo = true
        }
        assertTrue("zoom 1.0 -> 1.06 -> 1.0: $peakZoom", peakZoom in 1.04f..1.07f)
        assertEquals(1f, d.zoom, 0.005f)
        assertTrue(peakFlash > 0.5f)
        assertTrue(sawSlowMo)

        val calm = director(reduceMotion = true, reduceFlashing = true)
        calm.play(heroHit(ResonanceTier.TRANSCENDENTAL, BattleCue.Hit("orc", 300)))
        var calmZoom = 1f
        var calmFlash = 0f
        calm.runFor(1f) { calmZoom = maxOf(calmZoom, calm.zoom); calmFlash = maxOf(calmFlash, calm.screenFlash) }
        assertEquals("reduce motion: no zoom", 1f, calmZoom, 0f)
        assertEquals("reduce flashing: no full-screen flash", 0f, calmFlash, 0f)
    }

    @Test
    fun `full-screen flashes never exceed three a second`() {
        val d = director()
        var flashes = 0
        var lit = false
        d.runFor(1f) { i ->
            if (i % 6 == 0) d.play(heroHit(ResonanceTier.TRANSCENDENTAL, BattleCue.Hit("orc", 300))) // every 100 ms
            val on = d.screenFlash > 0.5f
            if (on && !lit) flashes++
            lit = on
        }
        assertTrue("$flashes flashes in a second", flashes <= 3)
    }
}
