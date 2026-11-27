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
import com.voicerpg.engine.model.EncounterDefinition
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.testing.TemplateFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * E5: Targetable heals and mana restores (`"target": "ally"`), and percentage-of-max healing (`healPctOfMax`)
 * scaled by resonance.
 */
class HealsAndRolesTest {

    @get:Rule val templateFixture = TemplateFixture { dir -> addTargetableSpells(dir) }

    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val combatVms = mutableListOf<com.voicerpg.engine.viewmodel.CombatViewModel>()

    @After
    fun tearDown() {
        combatVms.forEach { it.cleanup() }
        vmScope.cancel()
    }

    private fun startBattle(): com.voicerpg.engine.viewmodel.CombatViewModel {
        val narrator = CombatNarrator().apply { lineSinkForTesting = { _, _, done -> done() } }
        val vm = com.voicerpg.engine.viewmodel.CombatViewModel(
            SpeechManager(),
            combatNarrator = narrator,
            resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = CombatTiming.INSTANT,
            random = kotlin.random.Random(1),
            scopeOverride = vmScope
        ).also { combatVms += it }
        vm.applyPlayerCustomization(PlayerCustomization(name = "Hero", heroClassId = "elementalist"))
        val drone = GameContent.enemyTemplates.getValue("training_drone").toEnemy(instanceId = "d1", nameOverride = "Drone")
        val hero = GameContent.characterById("hero")!!.toPartyMember()!!.let {
            it.copy(currentHp = 100, maxHp = 200, currentMp = 20, maxMp = 100)
        }
        val warden = GameContent.characterById("warden")!!.toPartyMember()!!.let {
            it.copy(currentHp = 100, maxHp = 400, currentMp = 10, maxMp = 100)
        }
        vm.startEncounter(
            EncounterDefinition(
                id = "t_e5",
                name = "t_e5",
                environment = BattleEnvironment.FOREST,
                enemies = listOf(drone.copy(currentHp = 90_000, maxHp = 90_000, speed = 0)),
                initialParty = listOf(hero, warden)
            )
        )
        return vm
    }

    private fun waitTurnComplete(vm: com.voicerpg.engine.viewmodel.CombatViewModel, check: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end && !check()) Thread.sleep(10)
        assertTrue("expected state change within timeout", check())
    }

    @Test
    fun `target ally heal heals named ally, or caster when unnamed or enemy targeted`() {
        val vm = startBattle()
        val adept = AcousticProfile()

        // 1. Named ally -> heals Warden by 20% * resonance (>= 80 HP)
        vm.setPlayerInputPhaseForTesting("hero")
        vm.submitTypedChant("mend ally warden", adept)
        waitTurnComplete(vm) { vm.state.value.party.first { it.id == "warden" }.currentHp > 100 }
        val wardenHpAfter = vm.state.value.party.first { it.id == "warden" }.currentHp
        assertTrue("warden healed by % of max: $wardenHpAfter", wardenHpAfter >= 180)
        assertEquals("hero untouched", 100, vm.state.value.party.first { it.id == "hero" }.currentHp)

        // 2. Unnamed (or enemy targeted) -> heals caster (Hero)
        vm.setPlayerInputPhaseForTesting("hero")
        vm.submitTypedChant("mend ally drone", adept)
        waitTurnComplete(vm) { vm.state.value.party.first { it.id == "hero" }.currentHp > 100 }
        assertTrue("hero healed self when enemy targeted", vm.state.value.party.first { it.id == "hero" }.currentHp >= 140)
    }

    @Test
    fun `target ally mana restore restores named ally MP, and party mana restore restores all`() {
        val vm = startBattle()
        val adept = AcousticProfile()

        // 1. Targetable mana restore on Warden
        vm.setPlayerInputPhaseForTesting("warden")
        vm.submitTypedChant("restore spirit hero", adept)
        waitTurnComplete(vm) { vm.state.value.party.first { it.id == "hero" }.currentMp > 20 }
        assertEquals(70, vm.state.value.party.first { it.id == "hero" }.currentMp)
        assertEquals(10, vm.state.value.party.first { it.id == "warden" }.currentMp)

        // 2. Party-wide mana restore
        vm.setPlayerInputPhaseForTesting("warden")
        vm.submitTypedChant("party pulse", adept)
        waitTurnComplete(vm) { vm.state.value.party.first { it.id == "warden" }.currentMp > 10 }
        assertEquals(95, vm.state.value.party.first { it.id == "hero" }.currentMp)
        assertEquals(35, vm.state.value.party.first { it.id == "warden" }.currentMp)
    }

    companion object {
        fun addTargetableSpells(dir: File) {
            fun edit(path: String, f: (JsonObject) -> Unit) {
                val file = File(dir, path)
                val json = JsonParser.parseString(file.readText()).asJsonObject
                f(json); file.writeText(json.toString())
            }
            edit("spells/spells.json") { root ->
                val spells = root.getAsJsonArray("spells")
                spells.add(JsonObject().apply {
                    addProperty("id", "mend_ally"); addProperty("name", "Mend Ally"); addProperty("school", "HOLY")
                    addProperty("isHeal", true); addProperty("healPctOfMax", 0.20f); addProperty("target", "ally")
                    add("aliases", JsonArray().apply { add("mend ally") })
                })
                spells.add(JsonObject().apply {
                    addProperty("id", "restore_spirit"); addProperty("name", "Restore Spirit"); addProperty("school", "ARCANE")
                    addProperty("manaRestorePct", 0.50f); addProperty("target", "ally")
                    add("aliases", JsonArray().apply { add("restore spirit") })
                })
                spells.add(JsonObject().apply {
                    addProperty("id", "party_pulse"); addProperty("name", "Party Pulse"); addProperty("school", "ARCANE")
                    addProperty("manaRestorePct", 0.25f); addProperty("hitsAll", true)
                    add("aliases", JsonArray().apply { add("party pulse") })
                })
            }
            edit("classes/elementalist.json") { c ->
                c.add("starterSpellIds", JsonArray().apply { add("fireball"); add("mend_ally"); add("attune") })
            }
            File(dir, "characters/warden.json").writeText(JsonObject().apply {
                addProperty("id", "warden"); addProperty("role", "npc"); addProperty("name", "Warden")
                add("combat", JsonObject().apply {
                    addProperty("maxHp", 400); addProperty("maxMp", 100); addProperty("speed", 0)
                    add("spellIds", JsonArray().apply { add("power_strike"); add("restore_spirit"); add("party_pulse") })
                })
            }.toString())
        }
    }
}
