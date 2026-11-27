package com.voicerpg.engine.content

import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.ui.sprites.GridSprite
import com.voicerpg.engine.ui.sprites.SpriteLibrary
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Before a game's sprite sheets arrive, its roster (sprites/sprites.json) points sprite keys at placeholders, and
 * variants at a base. When the placeholder is a legacy text grid, the enemy is drawn with that grid, not the
 * engine's default figure: through a placeholder, and through a variant's base too.
 */
class RosterPlaceholderGridTest {

    @get:Rule val templateFixture = TemplateFixture { dir ->
        File(dir, "sprites").mkdirs()
        File(dir, "sprites/sprites.json").writeText(
            """{"sprites":{"wall_family":{"placeholder":"template_brute"},"rush_variant":{"base":"wall_family"}}}"""
        )
    }

    @Test
    fun `a placeholder or a variant's base draws its text grid`() {
        val grid = GameContent.enemySpriteById("template_brute")!!
        val viaPlaceholder = SpriteLibrary.source("wall_family")
        val viaVariant = SpriteLibrary.source("rush_variant")
        assertTrue(viaPlaceholder is GridSprite && viaVariant is GridSprite)
        assertSame("the placeholder's grid", grid, (viaPlaceholder as GridSprite).definition)
        assertSame("a variant draws its base's placeholder grid", grid, (viaVariant as GridSprite).definition)
    }
}
