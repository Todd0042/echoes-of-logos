package com.voicerpg.engine.content

import com.voicerpg.engine.testing.TemplateFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Loader checks against known values in the frozen template fixture (aliases, embedded spell graphics,
 * optional configs that the template turns on). The game-content versions of these checks, which work
 * on any game, live in EngineDecoupledAssetsTest and SelfieFilterConfigTest.
 */
class TemplateFixtureContentTest {

    @get:Rule val templateFixture = TemplateFixture()

    @Test
    fun environmentAliasesResolve() {
        assertEquals("dungeon", GameContent.environmentById("vaults")?.id)
        assertEquals("forest", GameContent.environmentById("FOREST")?.id)
    }

    @Test
    fun heroEmbeddedSpellCarriesGraphics() {
        val powerStrike = GameContent.characterById("hero")?.combat?.spells?.firstOrNull { it.id == "power_strike" }
        assertNotNull("Template hero must have power_strike", powerStrike)
        assertNotNull("power_strike must load its graphics block", powerStrike?.graphics)
    }

    @Test
    fun optionalSelfieConfigLoadsWhenDeclared() {
        val config = GameContent.selfieConfig
        assertTrue("Template enables selfie portraits", config.isEnabled)
        assertNotNull("Eye effect loads", config.eyeEffect)
        assertNotNull("Color grade loads", config.colorGrade)
        assertNotNull("Scanlines load", config.scanlines)
    }

    @Test
    fun templateMovesetsAndSpritesLoad() {
        val bruiser = GameContent.movesetById("construct_bruiser")
        assertNotNull("construct_bruiser moveset must exist", bruiser)
        assertTrue("construct_bruiser moveset must have moves", bruiser!!.moves.isNotEmpty())
        val dummy = GameContent.enemySpriteById("practice_dummy")
        assertNotNull("practice_dummy sprite must exist", dummy)
        assertTrue("practice_dummy must have idleUpright frame", dummy!!.idleUpright.isNotEmpty())
    }
}
