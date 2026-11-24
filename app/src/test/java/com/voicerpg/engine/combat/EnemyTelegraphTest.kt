package com.voicerpg.engine.combat

import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.PartyMember
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections
import kotlin.random.Random

/**
 * M4 mechanics: an enemy picks its move when its gauge reaches 75% and announces it (intent icon); it then
 * uses exactly that move. Boss ultimates charge for at least one of the boss's turns first. Named moves
 * get a title card. With a seeded RNG the plans are deterministic. Runs on the frozen template fixture.
 */
class EnemyTelegraphTest {

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

    /** A hero who never gets a turn (speed 0) and can't fall, so the enemy acts again and again. */
    private fun bystander() = PartyMember(
        id = "hero", name = "Aethel", loreClass = "Elementalist", currentHp = 99_999, maxHp = 99_999,
        currentMp = 10, maxMp = 10, spells = GameContent.spellsForClass("elementalist"), speed = 0
    )

    private fun battle(seed: Int, enemyTemplate: String, instance: String): Pair<CombatViewModel, MutableList<BattleCue>> {
        val vm = CombatViewModel(
            speechManager = SpeechManager(),
            resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = CombatTiming.INSTANT,
            random = Random(seed),
            scopeOverride = vmScope
        ).also { viewModels += it }
        val cues = Collections.synchronizedList(mutableListOf<BattleCue>())
        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.cues.collect { cues += it } }
        val enemy = GameContent.enemyTemplates.getValue(enemyTemplate).toEnemy(instanceId = instance, hpOverride = 99_999, speedOverride = 1000)
        vm.startEncounter(listOf(bystander()), listOf(enemy), BattleEnvironment.DUNGEON)
        return vm to cues
    }

    private fun waitFor(timeoutMs: Long = 15_000, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (cond()) return
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for $what")
    }

    private fun List<BattleCue>.snapshot(): List<BattleCue> = synchronized(this) { toList() }

    @Test
    fun `an enemy announces its move at three quarters and then uses exactly that move`() {
        val (_, cues) = battle(seed = 1, enemyTemplate = "template_sniper", instance = "sniper")
        waitFor(what = "three sniper turns") { cues.snapshot().count { it is BattleCue.EnemyMove } >= 3 }

        val seen = cues.snapshot()
        val moves = seen.filterIsInstance<BattleCue.EnemyMove>()
        for (move in moves.take(3)) {
            val i = seen.indexOf(move)
            val plan = seen.subList(0, i).filterIsInstance<BattleCue.Telegraph>().last()
            assertEquals("used the move it announced", plan.moveName, move.moveName)
            assertTrue("intent shown", plan.intent.icon.isNotBlank())
        }
    }

    @Test
    fun `with the same seed, enemies plan the same moves`() {
        fun plans(seed: Int): List<String> {
            val (_, cues) = battle(seed = seed, enemyTemplate = "template_boss", instance = "boss")
            waitFor(what = "six plans") { cues.snapshot().count { it is BattleCue.Telegraph } >= 6 }
            return cues.snapshot().filterIsInstance<BattleCue.Telegraph>().take(6).map { it.moveName }
        }
        assertEquals(plans(seed = 42), plans(seed = 42))
    }

    @Test
    fun `a boss ultimate always charges for a turn before it fires, under a title card`() {
        val (_, cues) = battle(seed = 3, enemyTemplate = "template_boss", instance = "boss")
        waitFor(timeoutMs = 30_000, what = "the boss's ultimate") {
            cues.snapshot().any { it is BattleCue.EnemyMove && it.isBossUltimate }
        }
        val seen = cues.snapshot()
        val ultimate = seen.first { it is BattleCue.EnemyMove && it.isBossUltimate } as BattleCue.EnemyMove
        val before = seen.subList(0, seen.indexOf(ultimate))
        val plan = before.filterIsInstance<BattleCue.Telegraph>().last()
        assertEquals(ultimate.moveName, plan.moveName)
        assertEquals(Intent.ULTIMATE, plan.intent)

        val charges = before.drop(before.indexOf(plan)).filterIsInstance<BattleCue.UltimateCharge>()
        assertTrue("charged for at least one turn first", charges.isNotEmpty())
        assertEquals(ultimate.moveName, charges.first().moveName)
        val chargeTurn = before.drop(before.indexOf(charges.first())).takeWhile { it !is BattleCue.TurnEnd }
        assertTrue("a charge turn deals no damage", chargeTurn.none { it is BattleCue.Impact })

        val after = seen.drop(seen.indexOf(ultimate))
        assertEquals("the ultimate is named on screen", BattleCue.TitleCard(ultimate.moveName, fromParty = false), after[1])
    }
}
