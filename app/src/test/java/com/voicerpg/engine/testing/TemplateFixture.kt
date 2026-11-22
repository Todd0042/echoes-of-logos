package com.voicerpg.engine.testing

import com.voicerpg.engine.content.GameContent
import org.junit.rules.ExternalResource
import java.io.File

/**
 * Engine tests come in two kinds:
 *
 *  - **Contract tests** run on the game's own content (whatever is in app/src/main/assets/game) and
 *    must pass for every game: story graph integrity, asset wiring, chant calibration of the game's
 *    spells, and so on. They use GameContent as loaded by default.
 *
 *  - **Mechanics tests** exercise engine behaviour on known data (a specific encounter, hero, spell or
 *    story node). They must not depend on which game they run in, so they load this frozen fixture:
 *    a copy of the engine's template content kept under app/src/test/fixtures/template-pack/game.
 *
 * Use it as a rule; it wraps @Before/@After, so the fixture is loaded before any setup runs and the
 * game's content is restored afterwards:
 *
 *     @get:Rule val templateFixture = TemplateFixture()
 *
 * The fixture is engine-owned and synced to every game with the engine tests. Never edit it to make a
 * game's test pass: game-specific tests belong in the game's own test package (com.voicerpg.game).
 */
class TemplateFixture : ExternalResource() {
    override fun before() {
        GameContent.reset()
        GameContent.initializeFromDirectory(dir())
    }

    override fun after() {
        GameContent.reset() // the next test class loads the game's own content again
    }

    companion object {
        fun dir(): File {
            val candidates = listOf(
                File("src/test/fixtures/template-pack/game"),
                File("app/src/test/fixtures/template-pack/game"),
            )
            return candidates.firstOrNull { it.isDirectory }
                ?: error("Template fixture not found (looked in ${candidates.joinToString { it.absolutePath }})")
        }
    }
}
