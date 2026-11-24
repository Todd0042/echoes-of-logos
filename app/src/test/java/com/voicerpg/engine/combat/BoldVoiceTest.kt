package com.voicerpg.engine.combat

import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.engine.SpellChantPresets
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.PartyMember
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections
import kotlin.random.Random

/**
 * M5 mechanics, "reward bold voices": a strong chant on a weakness staggers its target (once per enemy
 * per round); a MYTHIC+ hit breaks a charging boss ultimate; consecutive strong casts build a streak
 * that changes looks and sound only, never damage (invariant I-1: resonance is the only multiplier).
 */
class BoldVoiceTest {

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

    private fun caster() = PartyMember(
        id = "hero", name = "Aethel", loreClass = "Elementalist", currentHp = 500, maxHp = 500,
        currentMp = 9999, maxMp = 9999, spells = listOfNotNull(GameContent.spellById("flame_spark")), speed = 0
    )

    /**
     * Enemies that never act (speed 0) and can't fall. CONSTRUCT is neutral to fire; VERDANT is weak to it.
     * The strongest preset chants sweep every foe, so assertions always look at the intended target.
     */
    private fun foe(id: String, family: String = "CONSTRUCT", atb: Float = 0.6f, extra: (Enemy) -> Enemy = { it }) = extra(
        Enemy(id = id, name = "Qx${id.uppercase()}", subtitle = "Qz", currentHp = 99_999, maxHp = 99_999, baseAttack = 1,
            speed = 0, family = family, atbGauge = atb)
    )

    /** Every roll lands the same (no variance between casts), so equal inputs mean equal damage. */
    private object SameRoll : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }

    private fun battle(enemies: List<Enemy>, random: Random = Random.Default): Pair<CombatViewModel, MutableList<BattleCue>> {
        val vm = CombatViewModel(SpeechManager(), resonanceEngine = ResonanceEngine(NoveltyCache()), timing = CombatTiming.INSTANT,
            random = random, scopeOverride = vmScope)
            .also { viewModels += it }
        val cues = Collections.synchronizedList(mutableListOf<BattleCue>())
        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.cues.collect { cues += it } }
        vm.startEncounter(listOf(caster()), enemies, BattleEnvironment.FOREST)
        return vm to cues
    }

    private fun waitFor(timeoutMs: Long = 5000, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (cond()) return
            Thread.sleep(15)
        }
        throw AssertionError("Timed out waiting for $what")
    }

    private fun List<BattleCue>.snapshot() = synchronized(this) { toList() }

    /** One cast at [tier] by the hero at [target]; returns the cues of that turn. */
    private fun cast(vm: CombatViewModel, cues: MutableList<BattleCue>, tier: ResonanceTier, target: String): List<BattleCue> {
        val before = cues.snapshot().size
        val spell = GameContent.spellById("flame_spark")!!
        val preset = SpellChantPresets.getPreset(spell, tier)
        // The exact preset chant (extra words would change its score); the target is the marked enemy.
        vm.selectEnemy(vm.state.value.enemies.first { it.id == target })
        vm.setPlayerInputPhaseForTesting("hero")
        vm.submitTypedChant(preset.chantText, preset.acousticProfile)
        waitFor(what = "turn end") { cues.snapshot().drop(before).any { it is BattleCue.TurnEnd } }
        return cues.snapshot().drop(before)
    }

    private fun List<BattleCue>.impact() = filterIsInstance<BattleCue.Impact>().single()
    private fun List<BattleCue>.hitOn(id: String) = impact().hits.first { it.targetId == id }
    private fun List<BattleCue>.staggersOn(id: String) = filterIsInstance<BattleCue.Stagger>().filter { it.enemyId == id }

    @Test
    fun `a master chant on a weakness knocks the target's gauge back, once per round`() {
        val (vm, cues) = battle(listOf(foe("ivy", family = "VERDANT"), foe("moss", family = "VERDANT"), foe("fern")))
        val turn = cast(vm, cues, ResonanceTier.MASTER, target = "ivy")
        assertEquals(ResonanceTier.MASTER, turn.impact().tier)
        assertTrue("a weakness", turn.hitOn("ivy").affinity >= 1.5f)
        assertEquals(listOf(BattleCue.Stagger("ivy", interruptedMove = null)), turn.staggersOn("ivy"))
        assertEquals(0.25f, vm.state.value.enemies.first { it.id == "ivy" }.atbGauge, 0.001f)

        val again = cast(vm, cues, ResonanceTier.MASTER, target = "ivy")
        assertTrue("the cap: one stagger per enemy per round", again.staggersOn("ivy").isEmpty())

        val weak = cast(vm, cues, ResonanceTier.ADEPT, target = "moss")
        assertTrue("an ADEPT chant is not bold enough", weak.staggersOn("moss").isEmpty())
        val (vm2, cues2) = battle(listOf(foe("rock"), foe("qqone"), foe("qqtwo")))
        assertTrue("no weakness, no stagger", cast(vm2, cues2, ResonanceTier.TRANSCENDENTAL, target = "rock").none { it is BattleCue.Stagger })
    }

    @Test
    fun `a mythic hit breaks a charging boss ultimate`() {
        fun chargingBoss() = foe("boss") { it.copy(isBoss = true, plannedMove = "Cataclysm", ultimateChargeLeft = 1) }

        val (vm, cues) = battle(listOf(chargingBoss(), foe("qqone")))
        val adept = cast(vm, cues, ResonanceTier.ADEPT, target = "boss")
        assertTrue("an ADEPT hit can't break it", adept.staggersOn("boss").isEmpty())
        assertEquals("Cataclysm", vm.state.value.enemies.first { it.id == "boss" }.plannedMove)

        val mythic = cast(vm, cues, ResonanceTier.MYTHIC, target = "boss")
        assertEquals(listOf(BattleCue.Stagger("boss", interruptedMove = "Cataclysm")), mythic.staggersOn("boss"))
        assertTrue(mythic.contains(BattleCue.TitleCard("BROKEN!", fromParty = true)))
        val boss = vm.state.value.enemies.first { it.id == "boss" }
        assertNull("the ultimate is gone", boss.plannedMove)
        assertEquals(0, boss.ultimateChargeLeft)
    }

    @Test
    fun `strong casts in a row build a streak that a weak cast resets`() {
        val (vm, cues) = battle(listOf(foe("ivy"), foe("qqone"), foe("qqtwo")))
        cast(vm, cues, ResonanceTier.ADEPT, "ivy")
        val second = cast(vm, cues, ResonanceTier.MASTER, "ivy")
        assertEquals(BattleCue.Streak(2), second.filterIsInstance<BattleCue.Streak>().single())
        val third = cast(vm, cues, ResonanceTier.MYTHIC, "ivy")
        assertEquals(BattleCue.Streak(3), third.filterIsInstance<BattleCue.Streak>().single())
        val broken = cast(vm, cues, ResonanceTier.BASIC, "ivy")
        assertEquals("a basic chant ends it", BattleCue.Streak(0), broken.filterIsInstance<BattleCue.Streak>().single())
    }

    @Test
    fun `the streak never changes damage, at any tier`() {
        for (tier in ResonanceTier.entries) {
            // CONSTRUCT is neutral to fire, so no stagger changes the target between casts.
            val (vm, cues) = battle(listOf(foe("ivy"), foe("qqone"), foe("qqtwo")), random = SameRoll)
            val fresh = cast(vm, cues, tier, "ivy").hitOn("ivy").amount
            repeat(3) { cast(vm, cues, ResonanceTier.MASTER, "ivy") }
            val onStreak = cast(vm, cues, tier, "ivy")
            assertTrue("a streak was running", cues.snapshot().any { it is BattleCue.Streak && it.count >= 3 })
            assertEquals("$tier: same damage with or without a streak", fresh, onStreak.hitOn("ivy").amount)
        }
    }
}
