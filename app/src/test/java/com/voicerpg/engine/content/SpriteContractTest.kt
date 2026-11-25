package com.voicerpg.engine.content

import com.voicerpg.engine.testing.AsepriteFixture
import com.voicerpg.engine.testing.AsepriteFixture.BOSS
import com.voicerpg.engine.testing.AsepriteFixture.ENEMY
import com.voicerpg.engine.testing.AsepriteFixture.EVENTS
import com.voicerpg.engine.testing.AsepriteFixture.PARTY
import com.voicerpg.engine.testing.TemplateFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The sprite contract (docs/SPRITE_PLAN.md §3): every party fighter, enemy and boss has a 64 px sheet (bosses
 * 64, 96 or 128) with a `feet` slice and its role's required animations and event frames, and every boss has a
 * sprite of its own. It is enforced when the game opts in (`manifest.json` `"spriteContract": 1`) and always for
 * a release build, so a game whose sprites fall short can't be released.
 *
 * A game's art arriving piece by piece: no sheets at all, then a first pass with the typical mistakes, then fixed.
 */
class SpriteContractTest {

    private lateinit var pack: File
    private val sheets get() = File(pack, "sprites/sheets")

    @Before
    fun setUp() {
        pack = Files.createTempDirectory("contract-pack").toFile()
        TemplateFixture.dir().copyRecursively(pack)
        // The game opts in; its boss gets a sprite id of its own, which a minion has wrongly borrowed.
        val manifest = File(pack, "manifest.json")
        manifest.writeText(manifest.readText().replaceFirst("{", "{\n  \"spriteContract\": 1,"))
        setSpriteId("template_boss", "qqlord")
        setSpriteId("template_minion", "qqlord")
        sheets.mkdirs()
    }

    @After
    fun tearDown() {
        pack.deleteRecursively()
    }

    private fun setSpriteId(enemyId: String, spriteId: String) {
        val file = File(pack, "enemies/enemies.json")
        val json = com.google.gson.JsonParser.parseString(file.readText()).asJsonObject
        json.getAsJsonArray("enemies").map { it.asJsonObject }.first { it.get("id").asString == enemyId }.addProperty("spriteId", spriteId)
        file.writeText(json.toString())
    }

    private fun load() = GameContentLoader(FileContentSource(pack)).load()

    private fun sheet(name: String, tags: List<String>, cell: Int = 64, events: Map<String, String> = EVENTS, feet: Boolean = true) =
        File(sheets, "$name.json").writeText(
            AsepriteFixture.export(name, cell, AsepriteFixture.tags(tags, events = events), feet = if (feet) cell / 2 to cell - 4 else null)
        )

    @Test
    fun `no art yet, a first pass with mistakes, then a pack that passes`() {
        val content = load()
        assertTrue("the game opted in", SpriteContract.isEnforced(content.manifest, releaseBuild = false))

        // 1. Nothing drawn: every fighter is reported, by name.
        var report = SpriteContract.check(content)
        val missing = report.violations.filter { it.problem == "no sprite sheet" }.map { it.combatant }.toSet()
        assertEquals(setOf("hero", "training_drone", "template_brute", "template_sniper", "template_caster", "template_boss",
            "template_minion", "practice_dummy"), missing)

        // 2. A first pass: the usual mistakes.
        sheet("hero", PARTY, events = mapOf("attack" to "impact=2"))      // cast without its release frame
        sheet("template_brute", ENEMY)
        sheet("template_sniper", ENEMY - "hurt")                         // a tag missing
        sheet("template_caster", ENEMY, cell = 48)                        // the wrong cell size
        sheet("practice_dummy", ENEMY, feet = false)                      // no feet slice
        sheet("qqlord", BOSS - "ultimate", cell = 96)                     // a boss without its ultimate
        report = SpriteContract.check(load())
        assertEquals(
            listOf(
                "hero (hero): cast has no release event",
                "template_sniper (template_sniper): missing animation hurt",
                "template_caster (template_caster): frames are 48x48, must be 64x64",
                "template_boss (qqlord): missing animation ultimate",
                "template_boss (qqlord): shares its sprite with template_minion (every boss needs its own)",
                "template_minion (qqlord): frames are 96x96, must be 64x64", // a regular enemy on a boss-sized sheet
                "practice_dummy (practice_dummy): no feet slice"
            ).sorted(),
            report.violations.map { it.toString() }.sorted()
        )
        assertFalse(report.passes)
        assertTrue("the report names each problem", report.format().contains("missing animation hurt"))

        // 3. Fixed: the minion becomes a recoloured brute, and every sheet is complete.
        File(pack, "sprites/sprites.json").writeText("""{ "sprites": { "ember_brute": { "base": "template_brute", "palette": { "#3A3A3A": "#8A2B1A" } } } }""")
        setSpriteId("template_minion", "ember_brute")
        sheet("hero", PARTY)
        sheet("template_sniper", ENEMY)
        sheet("template_caster", ENEMY)
        sheet("practice_dummy", ENEMY)
        sheet("qqlord", BOSS, cell = 96)
        report = SpriteContract.check(load())
        assertEquals(emptyList<String>(), report.violations.map { it.toString() })
        assertTrue(report.passes)
        assertEquals("1 party fighter and 7 enemies checked", 8, report.checked)
    }

    @Test
    fun `without the opt-in only a release build enforces it`() {
        val manifest = File(pack, "manifest.json")
        manifest.writeText(manifest.readText().replace("\"spriteContract\": 1,", ""))
        val content = load()
        assertFalse(SpriteContract.isEnforced(content.manifest, releaseBuild = false))
        assertTrue("a release build always checks", SpriteContract.isEnforced(content.manifest, releaseBuild = true))
    }

    @Test
    fun `a family waiting for its art plays a placeholder in battle, but the contract still wants the real sheet`() {
        // The game maps its enemies to families; the orc family isn't drawn yet, so it stands in as the brute.
        sheet("hero", PARTY)
        sheet("template_brute", ENEMY)
        sheet("template_sniper", ENEMY)
        sheet("template_caster", ENEMY)
        sheet("practice_dummy", ENEMY)
        sheet("qqlord", BOSS, cell = 96)
        File(pack, "sprites/sprites.json").writeText("""{ "sprites": {
            "orc": { "placeholder": "template_brute" },
            "ember_orc": { "base": "orc", "palette": { "#3A3A3A": "#8A2B1A" } }
        } }""")
        setSpriteId("template_minion", "ember_orc")
        var content = load()
        val stand = content.resolveSheet("ember_orc")!!
        assertEquals("in battle: the placeholder's sheet", "template_brute", stand.sheet.id)
        assertTrue(stand.isPlaceholder)
        assertEquals(
            listOf("template_minion (ember_orc): no sprite sheet yet (placeholder: template_brute)"),
            SpriteContract.check(content).violations.map { it.toString() }
        )

        // The art arrives: the family's own sheet wins at once, and its variant follows.
        sheet("orc", ENEMY)
        content = load()
        val real = content.resolveSheet("ember_orc")!!
        assertEquals("orc", real.sheet.id)
        assertFalse(real.isPlaceholder)
        assertTrue(SpriteContract.check(content).passes)
    }
}
