package com.voicerpg.engine.story

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * A campaign track whose hero is one of the game's characters (`hero.characterId`): that track's party starts with
 * the character, once, instead of a class-based hero standing beside them. A track without one keeps the hero.
 */
class TrackHeroCharacterTest {

    @get:Rule val templateFixture = TemplateFixture { dir -> addTracks(dir) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.cancel()

    private fun startOn(trackId: String): StoryViewModel {
        val track = GameContent.manifest.campaignTracks.first { it.id == trackId }
        val player = track.hero?.toCustomization(track, PlayerCustomization(campaignTrackId = trackId)) ?: PlayerCustomization(name = "Aethel", heroClassId = "elementalist", campaignTrackId = trackId)
        return StoryViewModel(SpeechManager(), CombatNarrator(), scopeOverride = scope).also { it.startNewGame(player) }
    }

    @Test
    fun `a track told by a character starts with that character, once`() {
        assertEquals(listOf("warden"), startOn("track_b").state.value.partyStats.map { it.id })
        assertEquals(listOf("hero"), startOn("track_a").state.value.partyStats.map { it.id })
        val route = StoryRouteWalker.routes().first { it.player.campaignTrackId == "track_b" }
        StoryRouteWalker.battlesOn(route).forEach { b ->
            assertEquals("no class hero beside the character", 0, b.partyStats.count { it.id == "hero" })
        }
    }

    companion object {
        fun addTracks(dir: File) {
            File(dir, "characters/warden.json").writeText(JsonObject().apply {
                addProperty("id", "warden"); addProperty("role", "npc"); addProperty("name", "Warden")
                add("combat", JsonObject().apply {
                    addProperty("maxHp", 300); addProperty("maxMp", 100); addProperty("speed", 50)
                    add("spellIds", JsonArray().apply { add("power_strike") })
                })
                addProperty("joinWhenFlag", "t_warden_joined")
            }.toString())
            val f = File(dir, "manifest.json")
            val m = JsonParser.parseString(f.readText()).asJsonObject
            val start = m.get("initialNodeId").asString
            val scene = m.get("initialSceneId").asString
            m.add("campaignTracks", JsonParser.parseString("""[
                {"id":"track_a","displayName":"A","startNodeId":"$start","startSceneId":"$scene"},
                {"id":"track_b","displayName":"B","startNodeId":"$start","startSceneId":"$scene",
                 "hero":{"name":"Warden","characterId":"warden"}}]"""))
            f.writeText(m.toString())
        }
    }
}
