package com.voicerpg.engine.content

import com.voicerpg.engine.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Release gate (contract test, on the game's own content): the game's battle sprites meet the sprite contract
 * (docs/SPRITE_PLAN.md §3). It fails when the game has opted in (`"spriteContract": 1`) or when the tests run
 * for a release build (`testReleaseUnitTest`, which each game's release build depends on), so a game whose
 * sprites fall short can't be released. Otherwise it only writes the coverage report:
 * `app/build/reports/sprite-contract.txt` (see scripts/sprite_coverage.sh).
 */
class GameSpriteContractTest {

    @Test
    fun `the game's battle sprites meet the contract before it can be released`() {
        val report = SpriteContract.check(GameContent.pack)
        val text = report.format()
        runCatching {
            val dir = listOf(File("build/reports"), File("app/build/reports")).firstOrNull { it.parentFile?.isDirectory == true } ?: File("build/reports")
            dir.mkdirs()
            File(dir, "sprite-contract.txt").writeText(text)
        }
        println(text)
        val releaseBuild = !BuildConfig.DEBUG
        if (SpriteContract.isEnforced(GameContent.manifest, releaseBuild)) {
            val why = if (releaseBuild) "a release build" else "the game opted in (spriteContract: 1)"
            assertTrue("Sprite contract enforced by $why:\n$text", report.passes)
        }
    }
}
