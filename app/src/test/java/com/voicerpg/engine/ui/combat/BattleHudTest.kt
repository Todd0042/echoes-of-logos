package com.voicerpg.engine.ui.combat

import androidx.compose.ui.geometry.Offset
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.combat.BattleCue
import com.voicerpg.engine.combat.CombatTiming
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.engine.SpellChantPresets
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.ui.fx.BattleFxDirector
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections

/**
 * The battle HUD keeps the arena clear: the party's HP, MP and turn gauges live in chips in the console, a
 * boss's health and ultimate charge in a bar across the top of the arena, and a regular enemy shows a thin
 * health sliver only while it is targeted or for a moment after it is hit or healed.
 *
 * Played through a real battle: the duo against a boss and two minions; Aethel strikes the targeted minion,
 * the player retargets, the boss starts charging its ultimate, Cedric falls.
 */
class BattleHudTest {

    @get:Rule val templateFixture = TemplateFixture()

    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val viewModels = mutableListOf<CombatViewModel>()

    @After
    fun tearDown() {
        viewModels.forEach { it.cleanup() }
        collectorScope.cancel()
        vmScope.cancel()
    }

    private fun waitFor(timeoutMs: Long = 15_000, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (cond()) return
            Thread.sleep(15)
        }
        throw AssertionError("Timed out waiting for $what")
    }

    private val frame = 1_000_000_000L / 60
    private var now = 1_000_000_000L

    private fun BattleFxDirector.runFor(seconds: Float) {
        frame(now)
        repeat((seconds * 60).toInt()) { now += frame; frame(now) }
    }

    private fun aethel() = PartyMember(id = "hero", name = "Aethel", loreClass = "Elementalist", currentHp = 300, maxHp = 300,
        currentMp = 80, maxMp = 80, spells = listOfNotNull(GameContent.spellById("flame_spark")), speed = 0)

    private fun cedric() = PartyMember(id = "cedric", name = "Sir Cedric", loreClass = "Templar", currentHp = 400, maxHp = 400,
        currentMp = 60, maxMp = 60, spells = listOfNotNull(GameContent.spellById("mending_touch")), speed = 0)

    private fun minion(id: String) = Enemy(id = id, name = id.replaceFirstChar { it.uppercase() }, subtitle = "Qz",
        currentHp = 9_000, maxHp = 9_000, baseAttack = 1, speed = 0, family = "CONSTRUCT")

    private fun boss() = Enemy(id = "qqboss", name = "Qxlord", subtitle = "Qz", currentHp = 50_000, maxHp = 50_000,
        baseAttack = 1, speed = 0, isBoss = true, family = "CONSTRUCT")

    @Test
    fun `party gauges in the console, boss bar on top, enemy slivers only when they matter`() {
        val vm = CombatViewModel(SpeechManager(), resonanceEngine = ResonanceEngine(NoveltyCache()), timing = CombatTiming.INSTANT,
            random = kotlin.random.Random(5), scopeOverride = vmScope).also { viewModels += it }
        val cues = Collections.synchronizedList(mutableListOf<BattleCue>())
        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.cues.collect { cues += it } }
        val director = BattleFxDirector(positionOf = { Offset.Zero })
        vm.startEncounter(listOf(aethel(), cedric()), listOf(boss(), minion("qqone"), minion("qqtwo")), BattleEnvironment.FOREST)

        // The party: one chip per hero, in party order, the acting hero marked.
        vm.setPlayerInputPhaseForTesting("hero")
        var chips = BattleHud.partyChips(vm.state.value)
        assertEquals(listOf("hero", "cedric"), chips.map { it.id })
        assertTrue(chips.first { it.id == "hero" }.isActive)
        assertFalse(chips.first { it.id == "cedric" }.isActive)
        assertEquals(80, chips.first { it.id == "hero" }.mp)

        // The boss has its bar on top and never a sliver; minions show nothing until they matter.
        var bars = BattleHud.bossBars(vm.state.value, director::fxOf)
        assertEquals(listOf("qqboss"), bars.map { it.id })
        assertEquals(1f, bars.single().hpRatio, 0.001f)
        fun enemy(id: String) = vm.state.value.enemies.first { it.id == id }
        fun sliver(id: String) = BattleHud.sliverAlpha(enemy(id), director.fxOf(id))
        assertEquals(0f, sliver("qqboss"), 0f)

        vm.selectEnemy(enemy("qqone"))
        assertEquals("targeted: shown", 1f, sliver("qqone"), 0f)
        assertEquals("not targeted, untouched: hidden", 0f, sliver("qqtwo"), 0f)

        // Aethel strikes the targeted minion; the cues play into the director as on screen.
        val preset = SpellChantPresets.getPreset(GameContent.spellById("flame_spark")!!, ResonanceTier.ADEPT)
        vm.setPlayerInputPhaseForTesting("hero")
        vm.submitTypedChant(preset.chantText, preset.acousticProfile)
        waitFor(what = "Aethel's turn to end") { synchronized(cues) { cues.any { it is BattleCue.TurnEnd } } }
        synchronized(cues) { cues.toList() }.forEach(director::play)
        director.runFor(0.1f)
        assertTrue("the strike cost mana, and the chip shows it", BattleHud.partyChips(vm.state.value).first { it.id == "hero" }.mp < 80)
        assertTrue("the minion took the blow", enemy("qqone").currentHp < 9_000)

        // Retarget: the struck minion keeps its sliver for a moment, then it fades; the new target shows.
        vm.selectEnemy(enemy("qqtwo"))
        assertEquals("just hit: still shown", 1f, sliver("qqone"), 0.01f)
        assertEquals(1f, sliver("qqtwo"), 0f)
        director.runFor(3.5f)
        assertEquals("a few seconds later it has faded", 0f, sliver("qqone"), 0.01f)
        assertEquals("the target stays shown", 1f, sliver("qqtwo"), 0f)

        // A heal shows the sliver too (an enemy healer's work is worth seeing).
        director.play(BattleCue.Impact("qqtwo", fromParty = false, hits = listOf(BattleCue.Hit("qqone", 50, kind = BattleCue.HitKind.HEAL))))
        director.runFor(0.1f)
        assertTrue(sliver("qqone") > 0.9f)

        // The boss charges its ultimate: the boss bar carries the charge.
        director.play(BattleCue.UltimateCharge("qqboss", "Qxdoom", turnsLeft = 1, totalTurns = 2))
        bars = BattleHud.bossBars(vm.state.value, director::fxOf)
        assertTrue("charging: ${bars.single().ultimateCharge}", (bars.single().ultimateCharge ?: 0f) > 0.5f)

        // Cedric falls: his chip says so (the field no longer carries a badge).
        vm.setPartyForTesting(vm.state.value.party.map { if (it.id == "cedric") it.copy(currentHp = 0) else it })
        chips = BattleHud.partyChips(vm.state.value)
        assertTrue(chips.first { it.id == "cedric" }.isFallen)
        assertEquals(0f, chips.first { it.id == "cedric" }.hpRatio, 0f)
    }
}
