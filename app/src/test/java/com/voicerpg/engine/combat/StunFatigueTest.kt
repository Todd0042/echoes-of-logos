package com.voicerpg.engine.combat

import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.CombatPhase
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.viewmodel.CombatViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Control buys time, it doesn't win the fight: a stun (overload, freeze) leaves the enemy immune to stuns for a few
 * of its turns, so a party that stuns every turn still faces the enemy; stacked slows have a floor.
 *
 * A real battle on virtual time: a hero casts a stunning area spell every turn at Master resonance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StunFatigueTest {

    @get:Rule val templateFixture = TemplateFixture()

    @Test
    fun `an enemy stunned every turn still gets to act`() {
        val scope = TestScope(StandardTestDispatcher())
        val narrator = CombatNarrator().apply { setSpellCalloutsEnabled(false); setBattleReportsEnabled(false); lineSinkForTesting = { _, _, done -> done() } }
        val vm = CombatViewModel(SpeechManager(), combatNarrator = narrator, resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = CombatTiming.INSTANT, random = kotlin.random.Random(9), scopeOverride = scope)
        var enemyAttacks = 0
        scope.backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.cues.collect { if (it is BattleCue.Impact && !it.fromParty && !it.isTick) enemyAttacks++ }
        }
        val cleave = GameContent.spellById("thunder_cleave")!!
        val hero = PartyMember(id = "hero", name = "Aethel", loreClass = "Battlemage", currentHp = 90_000, maxHp = 90_000,
            currentMp = 9_999, maxMp = 9_999, spells = listOf(cleave), speed = 60)
        val foes = (1..2).map { Enemy(id = "f$it", name = "Qxorc $it", subtitle = "", currentHp = 900_000, maxHp = 900_000,
            baseAttack = 5, speed = 60, family = "FLESH", movesetId = "flesh_bruiser") }
        try {
            vm.setPureStoryMode(true)
            vm.configureAutoPlay(AutoPolicy.SPAM, 1.8f)
            vm.startEncounter(listOf(hero), foes, BattleEnvironment.FOREST)
            // About 30 hero turns of non-stop stunning.
            repeat(600) { scope.testScheduler.advanceTimeBy(200); scope.testScheduler.runCurrent() }
            assertTrue("the battle is still going", vm.state.value.phase != CombatPhase.BATTLE_WON && vm.state.value.phase != CombatPhase.BATTLE_LOST)
            assertTrue("the stunned enemies still attacked: $enemyAttacks times", enemyAttacks >= 8)
        } finally {
            vm.cleanup(); scope.cancel()
        }
    }

    @Test
    fun `stacked slows have a floor`() {
        val chill = StatusInstance(StatusId.CHILL, turnsLeft = 3, potPct = 0.5f)
        val root = StatusInstance(StatusId.ROOT, turnsLeft = 3, potPct = 0.8f)
        assertTrue(StatusSystem.speedMult(listOf(chill, root)) >= StatusSystem.SLOW_FLOOR - 1e-4f)
    }
}
