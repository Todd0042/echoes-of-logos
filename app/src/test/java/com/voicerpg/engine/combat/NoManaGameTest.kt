package com.voicerpg.engine.combat

import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.engine.SpellChantPresets
import com.voicerpg.engine.engine.TutorialBattleContent
import com.voicerpg.engine.engine.TutorialBattleStep
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.viewmodel.CombatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Collections

/**
 * A game without mana (`"combatResource": "none"` in its manifest): nobody has MP, nothing costs anything, there is
 * no attune, and nothing on screen or in speech mentions mana. The same template content with mana keeps it.
 *
 * Real battles on a copy of the engine's template content with the manifest switched, and a stand-in voice that
 * records every spoken line.
 */
class NoManaGameTest {

    @get:Rule val templateFixture = TemplateFixture { dir -> setCombatResource(dir, "none") }

    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val viewModels = mutableListOf<CombatViewModel>()

    @After
    fun tearDown() {
        viewModels.forEach { it.cleanup() }
        collectorScope.cancel()
        vmScope.cancel()
    }

    private fun waitFor(what: String, timeoutMs: Long = 20_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(10) }
        throw AssertionError("Timed out waiting for $what")
    }

    @Test
    fun `nobody has mana, no spell costs any and there is no attune`() {
        assertFalse(GameContent.usesMana)
        val hero = GameContent.heroCharacter!!.toPartyMember()!!
        assertEquals("hero MP", 0, hero.maxMp)
        assertTrue("no mana-restore spell in the kit: ${hero.spells.map { it.id }}", hero.spells.none { it.manaRestorePct > 0f })
        assertTrue("every spell is free", hero.spells.all { it.mpCost == 0 })
        assertTrue("no attune in the catalogue", GameContent.spells.values.none { it.manaRestorePct > 0f })
        assertNull(GameContent.breathSpellFor("hero"))
        GameContent.classes.forEach { c ->
            assertEquals("${c.id} starting MP", 0, c.startingMp)
            assertTrue("${c.id} kit has no attune", GameContent.spellsForClass(c.id).none { it.manaRestorePct > 0f })
        }
        assertTrue("the tutorial has no mana step",
            TutorialBattleContent.STEPS.none { it is TutorialBattleStep.AutoAction && it.actionText.contains("mana", ignoreCase = true) })
    }

    @Test
    fun `the strongest spell can be cast turn after turn and nothing mentions mana`() {
        val lines = Collections.synchronizedList(mutableListOf<String>())
        val narrator = CombatNarrator().apply {
            setSpellCalloutsEnabled(true); setBattleReportsEnabled(true)
            lineSinkForTesting = { _, text, done -> lines += text; done() }
        }
        val vm = CombatViewModel(SpeechManager(), combatNarrator = narrator, resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = CombatTiming.INSTANT, random = kotlin.random.Random(5), scopeOverride = vmScope).also { viewModels += it }
        val cues = Collections.synchronizedList(mutableListOf<BattleCue>())
        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.cues.collect { cues += it } }
        val hero = GameContent.heroCharacter!!.toPartyMember()!!.copy(currentHp = 9_000, maxHp = 9_000, speed = 0)
        val foe = Enemy(id = "qqfoe", name = "Blighted Qxorc", subtitle = "Qz", currentHp = 90_000, maxHp = 90_000,
            baseAttack = 1, speed = 0, family = "CONSTRUCT", movesetId = "flesh_bruiser")
        vm.startEncounter(listOf(hero), listOf(foe), BattleEnvironment.FOREST)

        // flame_spark cost 15 MP in the template; with no mana, five in a row all go off.
        val spell = GameContent.spellById("flame_spark")!!
        val preset = SpellChantPresets.getPreset(spell, ResonanceTier.ADEPT)
        repeat(5) { i ->
            val before = cues.count { it is BattleCue.Impact && it.fromParty }
            vm.setPlayerInputPhaseForTesting("hero")
            vm.submitTypedChant(preset.chantText, preset.acousticProfile)
            waitFor("cast ${i + 1} to land") { cues.count { it is BattleCue.Impact && it.fromParty } > before }
            waitFor("cast ${i + 1} to settle") {
                vm.state.value.phase != com.voicerpg.engine.model.CombatPhase.INCANTATION_RESOLVING &&
                    vm.state.value.phase != com.voicerpg.engine.model.CombatPhase.SPELL_VFX_PLAYING
            }
        }
        // "Focus" is an attune word in mana games; here it is not swallowed as one.
        vm.setPlayerInputPhaseForTesting("hero")
        vm.submitTypedChant("focus", preset.acousticProfile)

        val said = lines.toList()
        assertTrue("nothing about mana: $said", said.none { it.contains("mana", ignoreCase = true) || it.contains("attune", ignoreCase = true) })
        assertTrue("no mana on screen", vm.state.value.floatingTexts.none { it.text.contains("mana", ignoreCase = true) || it.text.contains("MP") })
        assertEquals(0, vm.state.value.party.single().maxMp)
    }

    companion object {
        fun setCombatResource(dir: File, value: String) {
            val f = File(dir, "manifest.json")
            val json = com.google.gson.JsonParser.parseString(f.readText()).asJsonObject
            json.addProperty("combatResource", value)
            f.writeText(json.toString())
        }
    }
}
