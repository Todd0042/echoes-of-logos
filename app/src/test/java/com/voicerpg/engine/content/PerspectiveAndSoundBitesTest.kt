package com.voicerpg.engine.content

import com.voicerpg.engine.model.AuraColor
import com.voicerpg.engine.model.PlayerCustomization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Loader and model checks for perspective games (campaign tracks with preset heroes, creationMode
 * "preset_select") and recorded sound bites on characters, spells, enemies and enemy moves.
 */
class PerspectiveAndSoundBitesTest {

    // The loader swaps a pack without a story for its emergency pack, so every test pack gets a tiny one.
    private val story = mapOf(
        "story/scenes.json" to """{ "scenes": [ { "id": "s", "name": "S", "initialNodeId": "n" } ] }""",
        "story/nodes.json" to """{ "nodes": [ { "id": "n", "text": "Hi.", "setFlagOnEnter": "game_completed" } ] }"""
    )

    private fun packOf(content: Map<String, String>): GameContentPack {
        val files = story + content
        val source = object : ContentSource {
            override fun readText(path: String): String? = files[path]
            override fun listFiles(dir: String): List<String> =
                files.keys.filter { it.startsWith("$dir/") }.map { it.removePrefix("$dir/") }.sorted()
        }
        return GameContentLoader(source).load()
    }

    private val manifest = """
        {
          "gameTitle": "Two Sides",
          "creationMode": "preset_select",
          "campaignTracks": [
            { "id": "track_a", "displayName": "Red", "startNodeId": "a1", "startSceneId": "s_a",
              "hero": { "name": "Captain Red", "title": "The Bold", "heroClassId": "brawler",
                        "portraitAsset": "portraits/red.jpg", "auraColor": "solar_gold",
                        "soundBites": ["audio/quotes/red.ogg"], "featuredMoves": ["Big Punch"] } },
            { "id": "track_b", "displayName": "Blue", "startNodeId": "b1", "startSceneId": "s_b" }
          ]
        }
    """.trimIndent()

    @Test
    fun presetSelectAndTrackHeroesLoad() {
        val pack = packOf(mapOf("manifest.json" to manifest))
        assertTrue(pack.manifest.isPresetSelect)
        val red = pack.manifest.campaignTracks.first { it.id == "track_a" }.hero!!
        assertEquals("Captain Red", red.name)
        assertEquals(listOf("audio/quotes/red.ogg"), red.soundBites)
        assertEquals(listOf("Big Punch"), red.featuredMoves)
        assertNull("a track may have no preset hero", pack.manifest.campaignTracks.first { it.id == "track_b" }.hero)
    }

    @Test
    fun customCreationIsTheDefault() {
        val pack = packOf(mapOf("manifest.json" to """{ "gameTitle": "Solo" }"""))
        assertEquals("custom", pack.manifest.creationMode)
        assertFalse(pack.manifest.isPresetSelect)
    }

    @Test
    fun presetSelectNeedsAtLeastOneHero() {
        val pack = packOf(mapOf("manifest.json" to """{ "creationMode": "preset_select", "campaignTracks": [ { "id": "t" } ] }"""))
        assertFalse("no track has a hero, so creation stays custom", pack.manifest.isPresetSelect)
    }

    @Test
    fun choosingAPresetSetsHeroAndTrack() {
        val pack = packOf(mapOf("manifest.json" to manifest))
        val track = pack.manifest.campaignTracks.first { it.id == "track_a" }
        val fallback = PlayerCustomization(name = "Hero", title = "Nobody", heroClassId = "default", campaignTrackId = "track_b")
        val player = track.hero!!.toCustomization(track, fallback)
        assertEquals("Captain Red", player.name)
        assertEquals("The Bold", player.title)
        assertEquals("brawler", player.heroClassId)
        assertEquals("portraits/red.jpg", player.customPortraitAsset)
        assertEquals(AuraColor.SOLAR_GOLD, player.auraColor)
        assertEquals("track_a", player.campaignTrackId)
    }

    @Test
    fun blankPresetFieldsKeepTheFallback() {
        val pack = packOf(mapOf("manifest.json" to """{ "campaignTracks": [ { "id": "t", "hero": { "name": "Solo" } } ] }"""))
        val track = pack.manifest.campaignTracks.first()
        val fallback = PlayerCustomization(name = "Hero", title = "Wanderer", heroClassId = "mage")
        val player = track.hero!!.toCustomization(track, fallback)
        assertEquals("Solo", player.name)
        assertEquals("Wanderer", player.title)
        assertEquals("mage", player.heroClassId)
        assertEquals(fallback.auraColor, player.auraColor)
    }

    @Test
    fun soundBitesLoadOnCharactersSpellsEnemiesAndMoves() {
        val pack = packOf(
            mapOf(
                "manifest.json" to """{ "gameTitle": "Bites" }""",
                "characters/red.json" to """{ "id": "red", "role": "hero", "name": "Red", "soundBites": ["audio/quotes/red1.ogg", " "],
                    "outOfTurnLine": "That's {owner}'s move, I'm {active}!" }""",
                "spells/spells.json" to """{ "spells": [ { "id": "zap", "name": "Zap", "school": "ELECTROMANCY", "soundBites": ["audio/quotes/zap.ogg"] } ] }""",
                "enemies/enemies.json" to """{ "enemies": [ { "id": "grunt", "name": "Grunt", "soundBites": ["audio/quotes/grunt.ogg"] } ] }""",
                "enemies/enemy_spells.json" to """{ "movesets": { "grunt_moves": { "moves": [ { "name": "Shove", "soundBites": ["audio/quotes/shove.ogg"] } ] } } }"""
            )
        )
        assertEquals("blank entries are dropped", listOf("audio/quotes/red1.ogg"), pack.characters.getValue("red").soundBites)
        assertEquals("That's {owner}'s move, I'm {active}!", pack.characters.getValue("red").outOfTurnLine)
        assertEquals(listOf("audio/quotes/zap.ogg"), pack.spells.getValue("zap").soundBites)
        assertEquals(listOf("audio/quotes/grunt.ogg"), pack.enemyTemplates.getValue("grunt").toEnemy().soundBites)
        assertEquals(listOf("audio/quotes/shove.ogg"), pack.enemyMovesets.getValue("grunt_moves").moves.first().soundBites)
    }
}
