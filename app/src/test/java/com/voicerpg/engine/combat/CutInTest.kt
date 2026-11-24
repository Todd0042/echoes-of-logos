package com.voicerpg.engine.combat

import androidx.compose.ui.geometry.Offset
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.engine.SpellChantPresets
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.CutInSpec
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections

/**
 * M7: a cast at or above the game's cut-in tier (MYTHIC by default) slides the caster's portrait across
 * before the spell flies; a boss with a cut-in gets one for its ultimate. The director shows the panel
 * over slowed arena time.
 */
class CutInTest {

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

    private fun battle(party: List<PartyMember>, enemies: List<Enemy>, seed: Int = 1): Pair<CombatViewModel, MutableList<BattleCue>> {
        val vm = CombatViewModel(SpeechManager(), resonanceEngine = ResonanceEngine(NoveltyCache()), timing = CombatTiming.INSTANT,
            random = kotlin.random.Random(seed), scopeOverride = vmScope).also { viewModels += it }
        val cues = Collections.synchronizedList(mutableListOf<BattleCue>())
        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.cues.collect { cues += it } }
        vm.startEncounter(party, enemies, BattleEnvironment.FOREST)
        return vm to cues
    }

    private fun List<BattleCue>.snapshot() = synchronized(this) { toList() }

    private fun hero() = PartyMember(
        id = "hero", name = "Aethel", loreClass = "Elementalist", currentHp = 500, maxHp = 500,
        currentMp = 9999, maxMp = 9999, spells = listOfNotNull(GameContent.spellById("flame_spark")), speed = 0
    )

    private fun dummy() = Enemy(id = "qqdummy", name = "Qxdummy", subtitle = "Qz", currentHp = 99_999, maxHp = 99_999,
        baseAttack = 1, speed = 0, family = "CONSTRUCT")

    private fun castAt(vm: CombatViewModel, cues: MutableList<BattleCue>, tier: ResonanceTier): List<BattleCue> {
        val before = cues.snapshot().size
        val preset = SpellChantPresets.getPreset(GameContent.spellById("flame_spark")!!, tier)
        vm.setPlayerInputPhaseForTesting("hero")
        vm.submitTypedChant(preset.chantText, preset.acousticProfile)
        waitFor(what = "turn end") { cues.snapshot().drop(before).any { it is BattleCue.TurnEnd } }
        return cues.snapshot().drop(before)
    }

    @Test
    fun `a mythic cast slides the caster's portrait across before the spell flies`() {
        val (vm, cues) = battle(listOf(hero()), listOf(dummy()))
        val turn = castAt(vm, cues, ResonanceTier.MYTHIC)
        val cutIn = turn.filterIsInstance<BattleCue.CutIn>().single()
        assertEquals("hero", cutIn.characterId)
        assertEquals(ResonanceTier.MYTHIC, cutIn.tier)
        assertTrue("before the release", turn.indexOf(cutIn) < turn.indexOfFirst { it is BattleCue.Release })
        val speaker = GameContent.characterById("hero")?.speaker
        if (speaker?.portraitAsset != null) assertEquals("the character's own portrait by default", speaker.portraitAsset, cutIn.portrait)

        val adept = castAt(vm, cues, ResonanceTier.ADEPT)
        assertTrue("below the cut-in tier: none", adept.none { it is BattleCue.CutIn })
    }

    @Test
    fun `a boss with a cut-in gets one for its ultimate`() {
        val spec = CutInSpec(portrait = "portraits/boss.png", color = "#B71C1C", quote = "Kneel!")
        val boss = GameContent.enemyTemplates.getValue("template_boss")
            .toEnemy(instanceId = "boss", hpOverride = 99_999, speedOverride = 1000).copy(cutIn = spec)
        val bystander = hero().copy(currentHp = 99_999, maxHp = 99_999)
        val (_, cues) = battle(listOf(bystander), listOf(boss), seed = 3)
        waitFor(timeoutMs = 30_000, what = "the ultimate") { cues.snapshot().any { it is BattleCue.EnemyMove && it.isBossUltimate } }
        waitFor(what = "its release") {
            val s = cues.snapshot(); val i = s.indexOfFirst { it is BattleCue.EnemyMove && it.isBossUltimate }
            s.drop(i).any { it is BattleCue.Release }
        }
        val s = cues.snapshot()
        val ult = s.indexOfFirst { it is BattleCue.EnemyMove && it.isBossUltimate }
        val turn = s.drop(ult).takeWhile { it !is BattleCue.Release }
        val cutIn = turn.filterIsInstance<BattleCue.CutIn>().single()
        assertEquals("portraits/boss.png", cutIn.portrait)
        assertEquals("Kneel!", cutIn.quote)
        assertTrue(cutIn.isEnemy)
    }

    @Test
    fun `the director shows the panel over slowed time, then clears it`() {
        val d = BattleFxDirector(positionOf = { Offset.Zero })
        d.play(BattleCue.CutIn("hero", "Aethel", ResonanceTier.MYTHIC, portrait = null, color = null, quote = "Now!", isEnemy = false))
        assertNotNull(d.cutIn)
        var now = 1_000_000_000L
        d.frame(now)
        now += 1_000_000_000L / 60
        d.frame(now)
        assertEquals("the arena slows behind it", 0.3f, d.clock.timeScale, 0.01f)
        repeat(60) { now += 1_000_000_000L / 60; d.frame(now) }
        assertNull(d.cutIn)
    }
}
