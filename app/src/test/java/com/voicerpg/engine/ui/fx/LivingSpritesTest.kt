package com.voicerpg.engine.ui.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.voicerpg.engine.combat.BattleCue
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.content.PresentationConfig
import com.voicerpg.engine.model.HitReaction
import com.voicerpg.engine.ui.vfx.ParticleCap
import com.voicerpg.engine.testing.TemplateFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * M6: an enemy's death shatters its sprite into its own pixels (no new art needed), or fades or
 * dissolves as the game chooses; heroes fall as before. Particles are capped. Enemies react to hits in
 * their own style and taunt. Content: boss scale, taunts, hit reactions and optional sprite frames.
 */
class LivingSpritesTest {

    @get:Rule val templateFixture = TemplateFixture()

    private val frame60 = 1_000_000_000L / 60
    private val sprite = listOf(
        "..XX..",
        ".XXXX.",
        "XX..XX"
    ) // 10 opaque pixels
    private val palette = mapOf('X' to Color.Red)

    private fun director(deathEffect: PresentationConfig.DeathEffect = PresentationConfig.DeathEffect.SHATTER) =
        BattleFxDirector(
            positionOf = { if (it == "hero") Offset(200f, 600f) else Offset(900f, 500f) },
            lookOf = { id ->
                CombatantLook(
                    isEnemy = id != "hero",
                    matrix = sprite,
                    palette = palette,
                    flipped = id != "hero",
                    hitReaction = if (id == "clown") HitReaction.COMIC_SPIN else HitReaction.FLINCH
                )
            }
        ).also {
            it.presentation = PresentationConfig(deathEffect = deathEffect)
            it.registerSpriteBounds("orc", Rect(850f, 450f, 910f, 480f))
            it.registerSpriteBounds("hero", Rect(170f, 570f, 230f, 600f))
        }

    private fun BattleFxDirector.runFor(seconds: Float) {
        var now = 1_000_000_000L
        frame(now)
        repeat((seconds * 60).toInt()) { now += frame60; frame(now) }
    }

    @Test
    fun `a fallen enemy shatters into its own pixels, flying away from the blow`() {
        val d = director()
        d.play(BattleCue.Death("orc", isEnemy = true))
        assertTrue("the sprite is gone", d.fxOf("orc").hidden)
        assertEquals("one particle per opaque pixel", 10, d.particles.particles.size)
        assertTrue("from inside the sprite's bounds", d.particles.particles.all { it.x in 840f..920f && it.y in 440f..490f })
        assertTrue("in the sprite's colours", d.particles.particles.all { it.color == Color.Red })
        assertTrue("blown away from the party side", d.particles.particles.all { it.vx > 0f })
        d.runFor(2.5f)
        assertTrue("and gone", d.particles.particles.isEmpty())
    }

    @Test
    fun `heroes fall as before, and other death effects are honoured`() {
        val d = director()
        d.play(BattleCue.Death("hero", isEnemy = false))
        assertTrue(d.particles.particles.isEmpty())
        assertTrue(!d.fxOf("hero").hidden)

        val fade = director(PresentationConfig.DeathEffect.FADE)
        fade.play(BattleCue.Death("orc", isEnemy = true))
        assertTrue("a fade throws no particles", fade.particles.particles.isEmpty())
        fade.runFor(0.3f)
        assertTrue("fading", fade.fxOf("orc").fade in 0.05f..0.95f)
        fade.runFor(1f)
        assertTrue(fade.fxOf("orc").hidden)

        val dissolve = director(PresentationConfig.DeathEffect.DISSOLVE)
        dissolve.play(BattleCue.Death("orc", isEnemy = true))
        assertTrue("dissolving pixels drift upward", dissolve.particles.particles.all { it.vy < 0f && it.gravity <= 0f })
    }

    @Test
    fun `the arena never holds more than 900 particles`() {
        val d = director()
        val big = BattleFxDirector(positionOf = { Offset(900f, 500f) }, lookOf = {
            CombatantLook(isEnemy = true, matrix = List(26) { "X".repeat(20) }, palette = palette, flipped = true)
        })
        repeat(6) { i -> big.registerSpriteBounds("e$i", Rect(800f, 400f, 900f, 520f)); big.play(BattleCue.Death("e$i", isEnemy = true)) }
        assertTrue("${big.particles.particles.size}", big.particles.particles.size <= ParticleCap.MAX_LIVE)
        assertTrue(d.particles.particles.size <= ParticleCap.MAX_LIVE)
    }

    @Test
    fun `enemies react to hits in their own style and taunt`() {
        val d = director()
        d.play(BattleCue.Impact("hero", true, listOf(BattleCue.Hit("clown", 30))))
        var spun = 0f
        var now = 1_000_000_000L
        d.frame(now)
        repeat(30) { now += frame60; d.frame(now); spun = maxOf(spun, kotlin.math.abs(d.fxOf("clown").spin)) }
        assertTrue("a comic spin turns all the way round: $spun", spun > 300f)

        d.play(BattleCue.Taunt("orc", "You'll regret this!"))
        assertEquals("You'll regret this!", d.fxOf("orc").speech)
        d.runFor(3f)
        assertEquals(null, d.fxOf("orc").speech)
    }

    @Test
    fun `content gives bosses their size, enemies taunts and reactions, and sprites optional frames`() {
        val boss = GameContent.enemyTemplates.getValue("template_boss")
        assertEquals("bosses default to double size", 2.0f, boss.scale, 0f)
        assertEquals(1.0f, GameContent.enemyTemplates.getValue("template_brute").scale, 0f)
        assertEquals(2.0f, boss.toEnemy().scale, 0f)
        assertTrue(GameContent.enemyTemplates.values.all { it.hitReaction == HitReaction.FLINCH && it.taunts.isEmpty() })

        val def = GameContent.enemySprites.values.first()
        assertEquals("no attack frame: falls back to casting", def.actionCast, def.getFrame(com.voicerpg.engine.ui.sprites.SpriteFrame.ATTACK))
        assertEquals("no hurt frame: falls back to damaged", def.damaged, def.getFrame(com.voicerpg.engine.ui.sprites.SpriteFrame.HURT))
        assertEquals("no death frame: falls back to damaged", def.damaged, def.getFrame(com.voicerpg.engine.ui.sprites.SpriteFrame.DEATH))
    }
}
