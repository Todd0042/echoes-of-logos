package com.voicerpg.engine.ui.fx

import androidx.compose.ui.geometry.Offset
import com.voicerpg.engine.combat.BattleCue
import com.voicerpg.engine.content.PresentationConfig
import com.voicerpg.engine.model.Particle
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.model.SpellSchool
import androidx.compose.ui.graphics.Color
import com.voicerpg.engine.ui.vfx.SpellVfxEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Mechanics test for the presentation layer: the FX clock runs on frame TIME, so effects look the same
 * at 60 and 120 Hz; hit-stop and slow motion stop or slow the whole arena; the director turns cues into
 * projectiles, particles and a directional arena shake, honouring the game's intensity and Reduce motion.
 */
class BattleFxTest {

    private val frame60 = 1_000_000_000L / 60
    private val frame120 = 1_000_000_000L / 120

    /** Runs [frames] frames of [frameNanos] through [clock], feeding each step to [onStep]. */
    private fun run(clock: FxClock, frameNanos: Long, frames: Int, startNanos: Long = 0L, onStep: (FxClock.Step) -> Unit): Long {
        var now = startNanos
        clock.tick(now)
        repeat(frames) {
            now += frameNanos
            onStep(clock.tick(now))
        }
        return now
    }

    private fun travelSeconds(frameNanos: Long): Float {
        val clock = FxClock()
        val engine = SpellVfxEngine()
        var hitAt = -1f
        var elapsed = 0f
        engine.launch(0f, 0f, 100f, 0f, SpellSchool.PYROMANCY, isHeal = false, durationMs = 420) { hitAt = elapsed }
        run(clock, frameNanos, frames = 200) { step ->
            elapsed += step.fxSeconds
            engine.update(step.fxSeconds)
        }
        return hitAt
    }

    @Test
    fun `a projectile takes the same time to land at 60 and 120 frames per second`() {
        val at60 = travelSeconds(frame60)
        val at120 = travelSeconds(frame120)
        assertEquals("60 Hz lands on its 420 ms schedule", 0.42f, at60, 1f / 60)
        assertEquals("120 Hz lands on the same schedule", at60, at120, 1f / 60)
    }

    private fun particleAfterHalfSecond(frameNanos: Long): Particle {
        val p = Particle(x = 0f, y = 0f, vx = 6f, vy = -4f, size = 8f, initialSize = 8f, decay = 0.01f,
            color = Color.White, gravity = 0.2f, drag = 0.96f)
        val clock = FxClock()
        run(clock, frameNanos, frames = (500_000_000L / frameNanos).toInt()) { p.update(it.fxSeconds) }
        return p
    }

    @Test
    fun `particles drift, fall and fade the same at 60 and 120 frames per second`() {
        val a = particleAfterHalfSecond(frame60)
        val b = particleAfterHalfSecond(frame120)
        assertTrue("moved", a.x > 40f)
        assertEquals("same distance", a.x, b.x, a.x * 0.04f)
        assertEquals("same fall", a.y, b.y, abs(a.y) * 0.06f + 1f)
        assertEquals("same fade", a.life, b.life, 0.02f)
    }

    @Test
    fun `hit-stop freezes the arena while real time runs on, then it resumes`() {
        val clock = FxClock()
        val engine = SpellVfxEngine()
        engine.launch(0f, 0f, 100f, 0f, SpellSchool.CRYOMANCY, isHeal = false, durationMs = 1000) {}
        var now = run(clock, frame60, frames = 6) { engine.update(it.fxSeconds) }
        val before = engine.projectiles.single().progress

        clock.hitStop(100)
        var real = 0f
        now = run(clock, frame60, frames = 5, startNanos = now) { step ->
            real += step.realSeconds
            engine.update(step.fxSeconds)
        }
        assertEquals("frozen during the stop", before, engine.projectiles.single().progress, 0.0001f)
        assertTrue("real time still passes (shake keeps moving)", real > 0.07f)

        run(clock, frame60, frames = 6, startNanos = now) { engine.update(it.fxSeconds) }
        assertTrue("moves again afterwards", engine.projectiles.single().progress > before + 0.05f)
    }

    @Test
    fun `slow motion runs the arena at its time scale and a long pause never jumps ahead`() {
        val clock = FxClock()
        clock.slowMotion(scale = 0.3f, durationMs = 10_000)
        var fx = 0f
        var real = 0f
        val now = run(clock, frame60, frames = 60) { fx += it.fxSeconds; real += it.realSeconds }
        assertEquals(real * 0.3f, fx, 0.01f)

        // The app was in the background for two seconds: one step, capped.
        val step = clock.tick(now + 2_000_000_000L)
        assertTrue("capped at ${FxClock.MAX_STEP_SECONDS}s", step.realSeconds <= FxClock.MAX_STEP_SECONDS)
    }

    // ---- director --------------------------------------------------------------------------------------

    private val positions = mapOf("hero" to Offset(200f, 600f), "cedric" to Offset(200f, 800f), "orc" to Offset(900f, 500f), "imp" to Offset(900f, 700f))

    private fun director(intensity: Float = 1f, reduceMotion: Boolean = false) =
        BattleFxDirector(positionOf = { positions.getValue(it) }).also {
            it.presentation = PresentationConfig(intensity = intensity)
            it.reduceMotion = reduceMotion
        }

    private fun release(targets: List<String>, style: BattleCue.ReleaseStyle = BattleCue.ReleaseStyle.PROJECTILE, caster: String = "hero") =
        BattleCue.Release(
            casterId = caster, fromParty = caster == "hero" || caster == "cedric", targetIds = targets,
            school = SpellSchool.PYROMANCY, style = style, tier = ResonanceTier.ADEPT, bonusPercent = 30,
            particleCount = 90, graphics = null, travelMs = 420
        )

    private fun impact(fromParty: Boolean, shake: Float) = BattleCue.Impact(
        sourceId = if (fromParty) "hero" else "orc", fromParty = fromParty, tier = ResonanceTier.ADEPT, shake = shake,
        hits = listOf(BattleCue.Hit(if (fromParty) "orc" else "hero", 40))
    )

    /** Frames the director for [seconds] at 60 Hz and returns every shake offset it produced. */
    private fun BattleFxDirector.shakeTrace(seconds: Float): List<Offset> {
        val out = mutableListOf<Offset>()
        var now = 1_000_000_000L
        frame(now)
        repeat((seconds * 60).toInt()) {
            now += frame60
            frame(now)
            out += shake.offset
        }
        return out
    }

    @Test
    fun `a release launches one projectile from the caster to each target, and particles burst on arrival`() {
        val d = director()
        d.play(release(listOf("orc", "imp")))
        val shots = d.projectiles.projectiles.toList()
        assertEquals(2, shots.size)
        shots.forEach { assertEquals(Offset(200f, 600f), Offset(it.startX, it.startY)) }
        assertEquals(listOf(Offset(900f, 500f), Offset(900f, 700f)), shots.map { Offset(it.targetX, it.targetY) })
        assertTrue("nothing has burst yet", d.particles.particles.isEmpty())

        d.shakeTrace(0.5f) // frames past the 420 ms flight
        assertTrue("both landed", d.projectiles.projectiles.isEmpty())
        assertTrue("and burst into particles", d.particles.particles.isNotEmpty())
    }

    @Test
    fun `a breath turn glows on the caster with no projectile, and a new battle clears the arena`() {
        val d = director()
        d.play(release(listOf("hero"), style = BattleCue.ReleaseStyle.ATTUNE))
        assertTrue(d.projectiles.projectiles.isEmpty())
        assertTrue(d.particles.particles.isNotEmpty())
        assertTrue(d.particles.particles.all { abs(it.x - 200f) < 20f && abs(it.y - 600f) < 20f })

        d.play(release(listOf("orc")))
        d.play(BattleCue.BattleStart(encounterId = null))
        assertTrue(d.projectiles.projectiles.isEmpty())
        assertTrue(d.particles.particles.isEmpty())
    }

    @Test
    fun `the arena shakes in the direction of the hit and settles`() {
        val heroHit = director().apply { play(impact(fromParty = true, shake = 14f)) }.shakeTrace(0.6f)
        assertTrue("a hero's hit knocks the arena toward the enemy side first", heroHit.first().x > 0f)
        assertTrue("and settles", heroHit.last() == Offset.Zero)

        val enemyHit = director().apply { play(impact(fromParty = false, shake = 14f)) }.shakeTrace(0.6f)
        assertTrue("an enemy's hit knocks it toward the party side first", enemyHit.first().x < 0f)

        val peakFull = heroHit.maxOf { abs(it.x) }
        val peakHalf = director(intensity = 0.5f).apply { play(impact(fromParty = true, shake = 14f)) }.shakeTrace(0.6f).maxOf { abs(it.x) }
        assertEquals("the game's intensity scales the shake", peakFull * 0.5f, peakHalf, peakFull * 0.05f)
    }

    @Test
    fun `reduce motion keeps the arena still for every kind of shake`() {
        val d = director(reduceMotion = true)
        d.play(impact(fromParty = true, shake = 20f))
        d.play(BattleCue.EnemyMove(enemyId = "orc", moveName = "Cataclysm", kind = "AOE", isBossUltimate = true))
        d.play(BattleCue.Reinforcements(listOf("imp")))
        assertTrue(d.shakeTrace(0.6f).all { it == Offset.Zero })
    }
}
