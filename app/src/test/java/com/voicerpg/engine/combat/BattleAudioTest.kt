package com.voicerpg.engine.combat

import com.voicerpg.engine.audio.ImpactSound
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.SpellSchool
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections

/**
 * M8: each school has its own impact sound; the battle music's intensity layer comes in when the party is
 * low (with hysteresis, so it doesn't flicker) and stays once a boss changes phase.
 */
class BattleAudioTest {

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

    @Test
    fun `every school sounds different on impact`() {
        val sounds = SpellSchool.entries.map { ImpactSound.forSchool(it) }
        assertEquals("one distinct impact set per school", SpellSchool.entries.size, sounds.toSet().size)
        assertEquals(ImpactSound.FIRE_CRACKLE, ImpactSound.forSchool(SpellSchool.PYROMANCY))
        assertEquals(ImpactSound.ICE_SHATTER, ImpactSound.forSchool(SpellSchool.CRYOMANCY))
        assertEquals(ImpactSound.PHYSICAL_THUD, ImpactSound.forSchool(SpellSchool.PHYSICAL))
    }

    @Test
    fun `the intensity rule comes in low, leaves only well above the line, and a boss phase latches it`() {
        val t = 0.3f
        assertTrue(MusicIntensityRule.next(current = false, partyHpRatio = 0.25f, threshold = t, bossPhaseShifted = false))
        assertTrue("no flicker just above the line", MusicIntensityRule.next(current = true, partyHpRatio = 0.33f, threshold = t, bossPhaseShifted = false))
        assertFalse(MusicIntensityRule.next(current = true, partyHpRatio = 0.45f, threshold = t, bossPhaseShifted = false))
        assertFalse(MusicIntensityRule.next(current = false, partyHpRatio = 0.33f, threshold = t, bossPhaseShifted = false))
        assertTrue("a boss phase change keeps it on", MusicIntensityRule.next(current = true, partyHpRatio = 0.9f, threshold = t, bossPhaseShifted = true))
    }

    @Test
    fun `a battle that starts badly brings the intensity in, and a heal takes it out`() {
        val vm = CombatViewModel(SpeechManager(), resonanceEngine = ResonanceEngine(NoveltyCache()), timing = CombatTiming.INSTANT, scopeOverride = vmScope)
            .also { viewModels += it }
        val cues = Collections.synchronizedList(mutableListOf<BattleCue>())
        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.cues.collect { cues += it } }
        val hurt = PartyMember(id = "hero", name = "Aethel", loreClass = "Elementalist", currentHp = 20, maxHp = 100,
            currentMp = 50, maxMp = 50, spells = emptyList(), speed = 0)
        val healer = PartyMember(id = "cedric", name = "Sir Cedric", loreClass = "Templar", currentHp = 100, maxHp = 100,
            currentMp = 90, maxMp = 90, spells = listOfNotNull(GameContent.spellById("mending_touch")), speed = 0)
        val idle = Enemy(id = "qqidle", name = "Qxidle", subtitle = "Qz", currentHp = 999, maxHp = 999, baseAttack = 1, speed = 0)
        vm.startEncounter(listOf(hurt, healer), listOf(idle), BattleEnvironment.FOREST)

        fun intensity() = synchronized(cues) { cues.filterIsInstance<BattleCue.MusicIntensity>().map { it.on } }
        val end = System.currentTimeMillis() + 3000
        while (intensity().isEmpty() && System.currentTimeMillis() < end) Thread.sleep(15)
        assertEquals("60% party health: no", emptyList<Boolean>(), intensity())

        // Party at 60% (120/200) is above 30%: nothing yet. Knock the hero down by a status tick-free route:
        vm.setPartyForTesting(listOf(hurt.copy(currentHp = 5), healer.copy(currentHp = 40)))
        vm.setPlayerInputPhaseForTesting("cedric")
        vm.processIncantation("Mending touch Aethel")
        val end2 = System.currentTimeMillis() + 5000
        while (synchronized(cues) { cues.none { it is BattleCue.TurnEnd } } && System.currentTimeMillis() < end2) Thread.sleep(15)
        val heal = synchronized(cues) { cues.filterIsInstance<BattleCue.Impact>().single() }.hits.single().amount
        val after = (5 + heal + 40) / 200f
        if (after >= 0.4f) {
            assertEquals("down at 22% it came in, the heal to ${(after * 100).toInt()}% took it out", listOf(true, false), intensity())
        } else {
            assertEquals("still low after the heal: it stays", listOf(true), intensity())
        }
    }
}
