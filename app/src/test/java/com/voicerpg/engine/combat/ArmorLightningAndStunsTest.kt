package com.voicerpg.engine.combat

import com.google.gson.JsonParser
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.CombatPhase
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.Spell
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.viewmodel.CombatViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Armour is for blows: it takes damage off physical hits only, lightning hits an armoured foe harder (it conducts),
 * and other magic ignores it. An enemy's `neutral` schools hit it at x1.0 whatever its family and element. A stun
 * leaves a hero, like an enemy, immune to stuns for a few of their turns.
 *
 * Real battles on virtual time (template content, plus a "stunner" moveset that stuns every turn).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArmorLightningAndStunsTest {

    @get:Rule val templateFixture = TemplateFixture { dir -> addStunner(dir) }

    private fun spell(id: String) = GameContent.spellById(id)!!

    /** The first hit [spell] lands on [foe], cast at a fixed x1.5 (same seed, so the same rolls each time). */
    private fun firstHit(spell: Spell, foe: Enemy): BattleCue.Hit {
        val scope = TestScope(StandardTestDispatcher())
        val narrator = CombatNarrator().apply { setSpellCalloutsEnabled(false); setBattleReportsEnabled(false); lineSinkForTesting = { _, _, d -> d() } }
        val vm = CombatViewModel(SpeechManager(), combatNarrator = narrator, resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = CombatTiming.INSTANT, random = kotlin.random.Random(11), scopeOverride = scope)
        var hit: BattleCue.Hit? = null
        scope.backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.cues.collect { if (hit == null && it is BattleCue.Impact && it.fromParty && !it.isTick) hit = it.hits.first() }
        }
        val hero = PartyMember(id = "hero", name = "Aethel", loreClass = "Test", currentHp = 9_000, maxHp = 9_000,
            currentMp = 999, maxMp = 999, spells = listOf(spell.copy(status = null)), speed = 200)
        try {
            vm.setPureStoryMode(true); vm.configureAutoPlay(AutoPolicy.SPAM, 1.5f)
            vm.startEncounter(listOf(hero), listOf(foe), BattleEnvironment.FOREST)
            var steps = 0
            while (hit == null && steps++ < 300) { scope.testScheduler.advanceTimeBy(200); scope.testScheduler.runCurrent() }
            return hit ?: error("no hit landed")
        } finally { vm.cleanup(); scope.cancel() }
    }

    private fun foe(armor: Int = 0, family: String = "FLESH", self: String = "", neutral: List<String> = emptyList()) =
        Enemy(id = "foe", name = "Qxorc", subtitle = "", currentHp = 900_000, maxHp = 900_000, baseAttack = 1, speed = 0,
            family = family, selfElement = self, movesetId = "flesh_bruiser", armor = armor, neutral = neutral)

    @Test
    fun `armour takes damage off blows only, and lightning hits armour harder`() {
        val blow = spell("power_strike")        // PHYSICAL
        val fire = spell("flame_spark")         // PYROMANCY
        val bolt = spell("chain_lightning").copy(hitsAll = false)  // ELECTROMANCY
        assertEquals("a blow loses the armour", firstHit(blow, foe()).amount - 30, firstHit(blow, foe(armor = 30)).amount)
        assertEquals("fire ignores armour", firstHit(fire, foe()).amount, firstHit(fire, foe(armor = 30)).amount)
        val bare = firstHit(bolt, foe()).amount
        val plated = firstHit(bolt, foe(armor = 30)).amount
        assertTrue("lightning conducts through armour: $plated vs $bare", plated >= (bare * 1.2f).toInt())
    }

    @Test
    fun `a neutral school hits at x1_0 whatever the family and element`() {
        val shade = spell("shadow_spike")
        // A Void creature of Shadow resists shadow twice over (family x0.8, own element x0.8) ...
        assertTrue(firstHit(shade, foe(family = "VOID", self = "SHADOW")).affinity < 1f)
        // ... unless the content says shadow is neutral for it.
        assertEquals(1f, firstHit(shade, foe(family = "VOID", self = "SHADOW", neutral = listOf("SHADOW"))).affinity, 0.001f)
    }

    @Test
    fun `a hero stunned every turn still gets to act`() {
        val scope = TestScope(StandardTestDispatcher())
        val narrator = CombatNarrator().apply { setSpellCalloutsEnabled(false); setBattleReportsEnabled(false); lineSinkForTesting = { _, _, d -> d() } }
        val vm = CombatViewModel(SpeechManager(), combatNarrator = narrator, resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = CombatTiming.INSTANT, random = kotlin.random.Random(3), scopeOverride = scope)
        var heroActions = 0
        vm.autoActionListener = { _, _ -> heroActions++ }
        val hero = PartyMember(id = "hero", name = "Aethel", loreClass = "Test", currentHp = 900_000, maxHp = 900_000,
            currentMp = 999, maxMp = 999, spells = listOf(spell("power_strike")), speed = 60)
        val stunner = Enemy(id = "s", name = "Stunner", subtitle = "", currentHp = 900_000, maxHp = 900_000, baseAttack = 1,
            speed = 120, family = "FLESH", movesetId = "stunner")
        try {
            vm.setPureStoryMode(true); vm.configureAutoPlay(AutoPolicy.SPAM, 1.5f)
            vm.startEncounter(listOf(hero), listOf(stunner), BattleEnvironment.FOREST)
            repeat(600) { scope.testScheduler.advanceTimeBy(200); scope.testScheduler.runCurrent() }
            assertTrue("still fighting", vm.state.value.phase != CombatPhase.BATTLE_WON && vm.state.value.phase != CombatPhase.BATTLE_LOST)
            assertTrue("the hero still acted: $heroActions times", heroActions >= 8)
        } finally { vm.cleanup(); scope.cancel() }
    }

    companion object {
        fun addStunner(dir: File) {
            val f = File(dir, "enemies/enemy_spells.json")
            val json = JsonParser.parseString(f.readText()).asJsonObject
            json.getAsJsonObject("movesets").add("stunner", JsonParser.parseString(
                """{"moves":[{"name":"Static Grip","school":"ELECTROMANCY","kind":"BASIC","powerMult":0.1,"targetRule":"SINGLE_RANDOM","applyStatus":"OVERLOAD","weight":10}]}"""))
            f.writeText(json.toString())
        }
    }
}
