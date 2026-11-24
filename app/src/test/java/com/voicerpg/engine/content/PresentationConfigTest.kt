package com.voicerpg.engine.content

import com.voicerpg.engine.testing.TemplateFixture
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * `combat/presentation.json` is optional: a game without it gets neutral defaults (AGENTS.md "Opt-In
 * Features & Compatibility"), and a broken or out-of-range file can never break a battle.
 */
class PresentationConfigTest {

    @get:Rule val templateFixture = TemplateFixture()
    @get:Rule val tmp = TemporaryFolder()

    /** The frozen template pack, plus (or without) a presentation file. */
    private fun packWith(json: String?): PresentationConfig {
        val root = tmp.newFolder()
        TemplateFixture.dir().copyRecursively(root)
        File(root, "combat/presentation.json").delete()
        if (json != null) File(root, "combat").apply { mkdirs() }.resolve("presentation.json").writeText(json)
        return GameContentLoader(FileContentSource(root)).load().presentation
    }

    @Test
    fun `a game without presentation json gets the defaults`() {
        assertEquals(PresentationConfig(), GameContent.presentation)
        assertEquals(PresentationConfig(), packWith(null))
        assertEquals(1.0f, PresentationConfig().intensity, 0f)
    }

    @Test
    fun `a game can turn its battle effects up or down`() {
        assertEquals(0.6f, packWith("""{ "intensity": 0.6 }""").intensity, 0.0001f)
        assertEquals(1.4f, packWith("""{ "intensity": 1.4, "someFutureField": "ignored" }""").intensity, 0.0001f)
    }

    @Test
    fun `out of range or broken files fall back safely`() {
        assertEquals(PresentationConfig.MAX_INTENSITY, packWith("""{ "intensity": 9 }""").intensity, 0f)
        assertEquals(0f, packWith("""{ "intensity": -2 }""").intensity, 0f)
        assertEquals(PresentationConfig(), packWith("""{ "intensity": "loud" }"""))
        assertEquals(PresentationConfig(), packWith("""{ "intensity": """))
        assertEquals(PresentationConfig(), packWith("""{}"""))
    }
}
