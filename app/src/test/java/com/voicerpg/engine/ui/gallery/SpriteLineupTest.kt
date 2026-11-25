package com.voicerpg.engine.ui.gallery

import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.testing.AsepriteFixture
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.ui.sprites.AnimTag
import com.voicerpg.engine.ui.sprites.SpriteLibrary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The sprite lineup (gallery → SPRITES): every battle sprite the game uses, once each, grouped party / bosses /
 * enemies, with who uses it and what the contract still wants, all playing the chosen animation in step on one
 * clock so heights, proportions and timing can be compared side by side.
 *
 * On a game mid-way through its art: the hero and the boss drawn, a variant, and enemies still on text grids.
 */
class SpriteLineupTest {

    private lateinit var pack: File

    @Before
    fun setUp() {
        pack = Files.createTempDirectory("lineup-pack").toFile()
        TemplateFixture.dir().copyRecursively(pack)
        val sheets = File(pack, "sprites/sheets").apply { mkdirs() }
        File(sheets, "hero.json").writeText(AsepriteFixture.export("hero", 64,
            AsepriteFixture.tags(AsepriteFixture.PARTY, frames = 4, events = AsepriteFixture.EVENTS)))
        File(sheets, "qqlord.json").writeText(AsepriteFixture.export("qqlord", 96,
            AsepriteFixture.tags(AsepriteFixture.BOSS, frames = 3, events = AsepriteFixture.EVENTS) ))
        File(pack, "sprites/sprites.json").writeText("""{ "sprites": { "ember_brute": { "base": "qqlord", "palette": { "#3A3A3A": "#8A2B1A" } } } }""")
        val file = File(pack, "enemies/enemies.json")
        val json = com.google.gson.JsonParser.parseString(file.readText()).asJsonObject
        json.getAsJsonArray("enemies").map { it.asJsonObject }.forEach {
            when (it.get("id").asString) {
                "template_boss" -> it.addProperty("spriteId", "qqlord")
                "template_minion" -> it.addProperty("spriteId", "ember_brute")
            }
        }
        file.writeText(json.toString())
        GameContent.reset()
        GameContent.initializeFromDirectory(pack)
    }

    @After
    fun tearDown() {
        GameContent.reset()
        pack.deleteRecursively()
    }

    @Test
    fun `one entry per sprite, grouped, with its users and its contract status`() {
        val entries = SpriteLineup.entries(GameContent.pack)
        assertEquals("party first, then bosses, then enemies",
            listOf(SpriteLineup.Group.PARTY, SpriteLineup.Group.BOSS), entries.map { it.group }.distinct().take(2))

        val hero = entries.single { it.key == "hero" }
        assertEquals(SpriteLineup.Group.PARTY, hero.group)
        assertTrue("drawn and complete: ${hero.problems}", hero.problems.isEmpty() && hero.hasSheet)

        val lord = entries.single { it.key == "qqlord" }
        assertEquals(SpriteLineup.Group.BOSS, lord.group)
        assertEquals(listOf("template_boss"), lord.usedBy)

        val brute = entries.single { it.key == "template_brute" }
        assertEquals(SpriteLineup.Group.ENEMY, brute.group)
        assertEquals("the enemies sharing the brute's grid", listOf("template_brute", "training_drone"), brute.usedBy)
        assertTrue("still a text grid", !brute.hasSheet && brute.problems.contains("no sprite sheet"))

        val ember = entries.single { it.key == "ember_brute" }
        assertTrue("a variant counts as drawn", ember.hasSheet)
        assertEquals("but a regular enemy on a boss-sized sheet is flagged", listOf("frames are 96x96, must be 64x64"), ember.problems)

        assertEquals("each sprite once", entries.size, entries.map { it.key }.toSet().size)
        assertEquals("filter: bosses", listOf("qqlord"), SpriteLineup.filter(entries, SpriteLineup.Group.BOSS).map { it.key })
    }

    @Test
    fun `every sprite plays the chosen animation in step on one clock`() {
        val hero = SpriteLibrary.source("hero")
        val attack = hero.clip(AnimTag.ATTACK)                     // 4 frames x 100 ms
        assertEquals(attack.frames[0], SpriteLineup.frameAt(attack, 0f))
        assertEquals(attack.frames[2], SpriteLineup.frameAt(attack, 250f))
        assertEquals("one-shots loop in the lineup", attack.frames[1], SpriteLineup.frameAt(attack, 400f + 150f))

        val grid = SpriteLibrary.source("template_brute")
        assertEquals("a sprite without the animation shows its fallback", "idle", grid.clip(AnimTag.VICTORY).name)
        assertEquals("the hero's victory is its own", "victory", hero.clip(AnimTag.VICTORY).name)
        assertTrue("the lineup's animation choices cover the party and boss contracts",
            SpriteLineup.ANIMATIONS.map { it.key }.containsAll(listOf("idle", "ready", "charge", "cast", "attack", "hurt", "ko", "victory", "windup", "charge_ultimate", "ultimate")))
    }
}
