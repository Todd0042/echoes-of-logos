package com.voicerpg.engine.combat

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.content.SpriteContract
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.EncounterDefinition
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.ui.sprites.SpriteLibrary
import com.voicerpg.engine.viewmodel.CombatViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * A character changes costume at a story moment (`costumes` in its character file): once the flag is set, it fights
 * with that sprite; before, its own. The latest costume whose flag is set wins. The sprite contract checks costume
 * sprites like any fighter's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CostumesTest {

    @get:Rule val templateFixture = TemplateFixture { dir -> addCostumes(dir) }

    private fun heroSpriteKey(flags: Map<String, Boolean>): String {
        val scope = TestScope(StandardTestDispatcher())
        val vm = CombatViewModel(SpeechManager(), combatNarrator = CombatNarrator(), resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = CombatTiming.INSTANT, random = kotlin.random.Random(1), scopeOverride = scope)
        try {
            vm.applyStoryFlags(flags)
            vm.applyPlayerCustomization(PlayerCustomization(name = "Aethel", heroClassId = "elementalist"))
            val drone = GameContent.enemyTemplates.getValue("training_drone").toEnemy(instanceId = "d", nameOverride = "Drone")
            vm.startEncounter(EncounterDefinition(id = "c", name = "c", environment = BattleEnvironment.FOREST, enemies = listOf(drone),
                initialParty = listOfNotNull(GameContent.characterById("hero")?.toPartyMember())))
            scope.testScheduler.runCurrent()
            return SpriteLibrary.keyFor(vm.state.value.party.first { it.id == "hero" })
        } finally { vm.cleanup(); scope.cancel() }
    }

    @Test
    fun `a costume follows the story`() {
        assertEquals("hero", heroSpriteKey(emptyMap()))
        assertEquals("hero_shop", heroSpriteKey(mapOf("t_shop" to true)))
        assertEquals("the latest costume wins", "hero_boss", heroSpriteKey(mapOf("t_shop" to true, "t_promoted" to true)))
    }

    @Test
    fun `the sprite contract checks costume sprites`() {
        val report = SpriteContract.check(GameContent.pack)
        assertTrue("costume sprites are checked: ${report.format()}", report.format().contains("hero_shop"))
    }

    companion object {
        fun addCostumes(dir: File) {
            val f = File(dir, "characters/hero.json")
            val c = JsonParser.parseString(f.readText()).asJsonObject
            c.add("costumes", JsonArray().apply {
                add(JsonObject().apply { addProperty("flag", "t_shop"); addProperty("spriteId", "hero_shop") })
                add(JsonObject().apply { addProperty("flag", "t_promoted"); addProperty("spriteId", "hero_boss") })
            })
            f.writeText(c.toString())
        }
    }
}
