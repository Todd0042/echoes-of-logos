package com.voicerpg.engine.combat

import com.voicerpg.engine.audio.CombatLines
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.engine.SaveManager
import com.voicerpg.engine.engine.SpellChantPresets
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.GameSaveData
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.viewmodel.CombatViewModel
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections

/**
 * There are no levels: winning a fight changes nobody's stats and says nothing about levels. Saves from before levels were
 * removed (format version 1) are cleared, and the title screen says so once.
 *
 * Real battles on the engine's template content, with a stand-in voice that records every spoken line.
 */
class NoLevelsTest {

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

    private fun hero() = PartyMember(id = "hero", name = "Aethel", loreClass = "Elementalist", currentHp = 900, maxHp = 900,
        currentMp = 999, maxMp = 999, spells = listOfNotNull(GameContent.spellById("flame_spark")), speed = 0)

    private fun foe(hp: Int) = Enemy(id = "qqfoe", name = "Blighted Qxorc", subtitle = "Qz", currentHp = hp, maxHp = hp,
        baseAttack = 5, speed = 0, family = "CONSTRUCT", movesetId = "flesh_bruiser")

    private fun waitFor(what: String, timeoutMs: Long = 20_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(10) }
        throw AssertionError("Timed out waiting for $what")
    }

    /** Fights [foeHp] with a fixed MASTER cast and returns the battle's view model, its cues and its spoken lines. */
    private fun fight(foeHp: Int, seed: Int): Triple<CombatViewModel, List<BattleCue>, List<String>> {
        val lines = Collections.synchronizedList(mutableListOf<String>())
        val narrator = CombatNarrator().apply {
            setSpellCalloutsEnabled(true); setBattleReportsEnabled(true)
            lineSinkForTesting = { _, text, done -> lines += text; done() }
        }
        val vm = CombatViewModel(SpeechManager(), combatNarrator = narrator, resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = CombatTiming.INSTANT, random = kotlin.random.Random(seed), scopeOverride = vmScope).also { viewModels += it }
        val cues = Collections.synchronizedList(mutableListOf<BattleCue>())
        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.cues.collect { cues += it } }
        vm.startEncounter(listOf(hero()), listOf(foe(foeHp)), BattleEnvironment.FOREST)
        val preset = SpellChantPresets.getPreset(GameContent.spellById("flame_spark")!!, ResonanceTier.MASTER)
        vm.setPlayerInputPhaseForTesting("hero")
        vm.submitTypedChant(preset.chantText, preset.acousticProfile)
        return Triple(vm, cues, lines)
    }

    @Test
    fun `winning a fight changes no stats and says nothing about levels`() {
        val before = hero()
        val (vm, cues, lines) = fight(foeHp = 1, seed = 3)
        waitFor("the victory") { cues.toList().any { it is BattleCue.BattleEnd } && lines.toList().any { it == CombatLines.line("victory") } }

        val after = vm.state.value.party.single()
        assertEquals("max HP", before.maxHp, after.maxHp)
        assertEquals("max MP", before.maxMp, after.maxMp)
        assertEquals("speed", before.speed, after.speed)
        val said = lines.toList()
        assertTrue("no level-up line: $said", said.none { it.contains("level", ignoreCase = true) || it.contains("ascends", ignoreCase = true) })
        assertTrue("no level-up popup", vm.state.value.floatingTexts.none { it.text.contains("LEVEL", ignoreCase = true) })
    }

    @Test
    fun `a save from before levels were removed is cleared once and the title screen says so`() {
        val saves = SaveManager(context = null)
        saves.save(GameSaveData(saveVersion = 1), slot = 2)
        saves.save(GameSaveData(), slot = 3)

        val story = StoryViewModel(SpeechManager(), CombatNarrator(), saveManager = saves, scopeOverride = collectorScope)

        assertNull("the old save is gone", saves.load(2))
        assertFalse(saves.hasSave(2))
        assertNotNull("a current save stays", saves.load(3))
        assertEquals(GameSaveData.CURRENT_VERSION, saves.load(3)!!.saveVersion)
        val notice = story.state.value.startupNotice
        assertNotNull("the title screen tells the player", notice)
        assertTrue(notice!!.contains("cleared", ignoreCase = true))

        story.dismissStartupNotice()
        assertNull("said once", story.state.value.startupNotice)
        assertNull("and not again on the next start",
            StoryViewModel(SpeechManager(), CombatNarrator(), saveManager = saves, scopeOverride = collectorScope).state.value.startupNotice)
    }
}
