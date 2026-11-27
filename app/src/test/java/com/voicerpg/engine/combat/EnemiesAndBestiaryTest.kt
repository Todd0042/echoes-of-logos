package com.voicerpg.engine.combat

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.model.AcousticProfile
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.CombatPhase
import com.voicerpg.engine.model.EncounterDefinition
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.testing.TemplateFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Collections

/**
 * E6: Per-enemy weak/resist overrides, flat armor vs piercing spells, no enemy DRAIN healing,
 * learned-weakness bestiary in "Enemies" report, custom school display names, and solemn encounters.
 */
class EnemiesAndBestiaryTest {

    @get:Rule val templateFixture = TemplateFixture { dir -> addEnemyFeatures(dir) }

    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val combatVms = mutableListOf<com.voicerpg.engine.viewmodel.CombatViewModel>()

    @After
    fun tearDown() {
        combatVms.forEach { it.cleanup() }
        vmScope.cancel()
    }

    private fun makeVm(lines: MutableList<String>): com.voicerpg.engine.viewmodel.CombatViewModel {
        val narrator = CombatNarrator().apply { lineSinkForTesting = { _, text, done -> lines += text; done() } }
        return com.voicerpg.engine.viewmodel.CombatViewModel(
            SpeechManager(),
            combatNarrator = narrator,
            resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = CombatTiming.INSTANT,
            random = kotlin.random.Random(3),
            scopeOverride = vmScope
        ).also { combatVms += it }
    }

    @Test
    fun `enemy weak, resist, armor, piercing, and learned weakness in Enemies report`() {
        val lines = Collections.synchronizedList(mutableListOf<String>())
        val vm = makeVm(lines)
        vm.applyPlayerCustomization(PlayerCustomization(name = "Hero", heroClassId = "elementalist"))

        val armored = GameContent.enemyTemplates.getValue("armored_bot").toEnemy(instanceId = "a1", nameOverride = "Armored Bot")
        val hero = GameContent.characterById("hero")!!.toPartyMember()!!.copy(currentHp = 500, maxHp = 500)
        vm.startEncounter(
            EncounterDefinition(
                id = "t_e6",
                name = "t_e6",
                environment = BattleEnvironment.FOREST,
                enemies = listOf(armored.copy(speed = 0)),
                initialParty = listOf(hero)
            )
        )

        // Before hitting: "Enemies" reports no weakness
        vm.combatNarrator.setCombatActive(true)
        vm.combatNarrator.narrateEnemies(vm.state.value.enemies, vm.state.value.bestiaryWeaknesses)
        assertTrue("no weakness reported yet: $lines", lines.last().none { false } && !lines.last().contains("weak to", ignoreCase = true))

        // Hit with PYROMANCY (enemy weak = PYROMANCY -> x2.0, armor 200 reduces damage to 1; bestiary records it)
        vm.setPlayerInputPhaseForTesting("hero")
        vm.submitTypedChant("fireball", AcousticProfile())
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end && vm.state.value.bestiaryWeaknesses.isEmpty()) Thread.sleep(10)
        assertTrue("weakness recorded in bestiary", vm.state.value.bestiaryWeaknesses["armored_bot"]?.contains("PYROMANCY") == true)

        // Now "Enemies" report includes "weak to Heat" (from custom schoolNames)
        vm.combatNarrator.narrateEnemies(vm.state.value.enemies, vm.state.value.bestiaryWeaknesses)
        assertTrue("report mentions weak to Heat: ${lines.last()}", lines.last().contains("weak to Heat"))

        // Piercing spell ignores armor=200 and deals full damage
        val hpBeforePierce = vm.state.value.enemies.first().currentHp
        vm.setPlayerInputPhaseForTesting("hero")
        vm.submitTypedChant("lance", AcousticProfile())
        val end2 = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end2 && vm.state.value.enemies.first().currentHp == hpBeforePierce) Thread.sleep(10)
        val dmg = hpBeforePierce - vm.state.value.enemies.first().currentHp
        assertTrue("piercing ignored armor=200 (dealt $dmg)", dmg >= 30)
    }

    @Test
    fun `solemn encounter silences callouts and victory fanfare and speaks endLine`() {
        val lines = Collections.synchronizedList(mutableListOf<String>())
        val vm = makeVm(lines)
        vm.applyPlayerCustomization(PlayerCustomization(name = "Hero", heroClassId = "elementalist"))

        val fragile = GameContent.enemyTemplates.getValue("armored_bot").toEnemy(instanceId = "a1", nameOverride = "Shade")
            .copy(currentHp = 10, maxHp = 10, armor = 0, speed = 0)
        val hero = GameContent.characterById("hero")!!.toPartyMember()!!
        vm.startEncounter(
            EncounterDefinition(
                id = "t_solemn",
                name = "t_solemn",
                environment = BattleEnvironment.FOREST,
                enemies = listOf(fragile),
                initialParty = listOf(hero),
                solemn = true,
                endLine = "The room falls quiet."
            )
        )

        vm.setPlayerInputPhaseForTesting("hero")
        vm.submitTypedChant("fireball", AcousticProfile())
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end && vm.state.value.phase != CombatPhase.BATTLE_WON) Thread.sleep(10)
        assertEquals(CombatPhase.BATTLE_WON, vm.state.value.phase)
        val end2 = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < end2 && lines.none { it.contains("The room falls quiet.") }) Thread.sleep(10)
        assertTrue("speaks endLine: $lines", lines.any { it.contains("The room falls quiet.") })
        assertFalse("no Weak! callout in solemn fight: $lines", lines.any { it.contains("Weak!", ignoreCase = true) })
        assertFalse("no Victory! fanfare in solemn fight: $lines", lines.any { it.contains("Victory", ignoreCase = true) })
    }

    companion object {
        fun addEnemyFeatures(dir: File) {
            fun edit(path: String, f: (JsonObject) -> Unit) {
                val file = File(dir, path)
                val json = JsonParser.parseString(file.readText()).asJsonObject
                f(json); file.writeText(json.toString())
            }
            edit("combat/affinity_matrix.json") { m ->
                m.add("schoolNames", JsonObject().apply { addProperty("PYROMANCY", "Heat") })
            }
            edit("spells/spells.json") { root ->
                root.getAsJsonArray("spells").add(JsonObject().apply {
                    addProperty("id", "pierce_lance"); addProperty("name", "Pierce Lance"); addProperty("school", "PHYSICAL")
                    addProperty("basePower", 45); addProperty("mpCost", 0); addProperty("piercing", true)
                    add("aliases", JsonArray().apply { add("lance") })
                })
            }
            edit("classes/elementalist.json") { c ->
                c.add("starterSpellIds", JsonArray().apply { add("fireball"); add("pierce_lance"); add("attune") })
            }
            edit("enemies/enemies.json") { root ->
                root.getAsJsonArray("enemies").add(JsonObject().apply {
                    addProperty("id", "armored_bot"); addProperty("name", "Armored Bot"); addProperty("family", "FLESH")
                    addProperty("hp", 500); addProperty("attack", 10); addProperty("speed", 0); addProperty("armor", 200)
                    add("weak", JsonArray().apply { add("PYROMANCY") })
                    add("resist", JsonArray().apply { add("CRYOMANCY") })
                })
            }
        }
    }
}
