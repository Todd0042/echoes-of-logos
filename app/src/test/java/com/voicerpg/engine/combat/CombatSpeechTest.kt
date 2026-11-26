package com.voicerpg.engine.combat

import com.voicerpg.engine.audio.CombatLines
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.CombatSpeech
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Combat speech in two settings: spell callouts ("Fireball! Master!", the cast's one-word result) and battle
 * reports (damage, enemy moves and hits, turns). Combat waits for every line it speaks, so nothing is cut off;
 * a setting that's off costs no time at all; pocket mode keeps the essential reports even with reports off.
 *
 * Real battles, with a stand-in voice that records each line and "speaks" it at 40 ms a word.
 */
class CombatSpeechTest {

    @get:Rule val templateFixture = TemplateFixture()

    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val voice = Executors.newSingleThreadScheduledExecutor()
    private val viewModels = mutableListOf<CombatViewModel>()
    private val t0 = System.nanoTime()
    private fun now() = (System.nanoTime() - t0) / 1_000_000

    private data class Line(val kind: CombatSpeech, val text: String, val start: Long, var end: Long = -1)
    private data class Stamp(val cue: BattleCue, val at: Long)

    @After
    fun tearDown() {
        viewModels.forEach { it.cleanup() }
        collectorScope.cancel()
        vmScope.cancel()
        voice.shutdownNow()
    }

    private fun battle(narrator: CombatNarrator, party: List<PartyMember>, enemies: List<Enemy>): Triple<CombatViewModel, MutableList<Line>, MutableList<Stamp>> {
        val lines = Collections.synchronizedList(mutableListOf<Line>())
        narrator.lineSinkForTesting = { kind, text, done ->
            val line = Line(kind, text, now()).also { lines += it }
            voice.schedule({ line.end = now(); done() }, CombatLines.words(text) * MS_PER_WORD, TimeUnit.MILLISECONDS)
        }
        val vm = CombatViewModel(SpeechManager(), combatNarrator = narrator, resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = CombatTiming.INSTANT, random = kotlin.random.Random(4), scopeOverride = vmScope).also { viewModels += it }
        val cues = Collections.synchronizedList(mutableListOf<Stamp>())
        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.cues.collect { cues += Stamp(it, now()) } }
        vm.startEncounter(party, enemies, BattleEnvironment.FOREST)
        return Triple(vm, lines, cues)
    }

    private fun waitFor(timeoutMs: Long = 20_000, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(10) }
        throw AssertionError("Timed out waiting for $what")
    }

    private fun hero() = PartyMember(id = "hero", name = "Aethel", loreClass = "Elementalist", currentHp = 9_000, maxHp = 9_000,
        currentMp = 999, maxMp = 999, spells = listOfNotNull(GameContent.spellById("flame_spark")), speed = 0)

    private fun foe(hp: Int = 50_000, speed: Int = 0) = Enemy(id = "qqfoe", name = "Blighted Qxorc", subtitle = "Qz", currentHp = hp, maxHp = hp,
        baseAttack = 5, speed = speed, family = "CONSTRUCT", movesetId = "flesh_bruiser")

    private fun cast(vm: CombatViewModel, tier: ResonanceTier) {
        val preset = SpellChantPresets.getPreset(GameContent.spellById("flame_spark")!!, tier)
        vm.setPlayerInputPhaseForTesting("hero")
        vm.submitTypedChant(preset.chantText, preset.acousticProfile)
    }

    @JvmName("linesSnapshot") private fun List<Line>.snapshot() = synchronized(this) { toList() }
    @JvmName("stampsSnapshot") private fun List<Stamp>.snapshot() = synchronized(this) { toList() }

    private fun assertNoOverlap(lines: List<Line>) {
        lines.zipWithNext().forEach { (a, b) ->
            assertTrue("\"${b.text}\" started at ${b.start} before \"${a.text}\" ended at ${a.end}", a.end in 0..b.start)
        }
    }

    @Test
    fun `with callouts only the spell and its tier are said before it flies, and reports cost no time`() {
        val narrator = CombatNarrator().apply { setSpellCalloutsEnabled(true); setBattleReportsEnabled(false) }
        val (vm, lines, cues) = battle(narrator, listOf(hero()), listOf(foe()))
        cast(vm, ResonanceTier.MASTER)
        waitFor(what = "the turn to end") { cues.snapshot().any { it.cue is BattleCue.TurnEnd } }

        val said = lines.snapshot()
        println(said.joinToString("\n") { "[${it.kind}] ${it.text}  (${it.start}-${it.end} ms)" })
        assertEquals("only callouts: $said", setOf(CombatSpeech.CALLOUT), said.map { it.kind }.toSet())
        val spell = GameContent.spellById("flame_spark")!!.name
        assertEquals(CombatLines.callout(spell, ResonanceTier.MASTER), said.first().text)
        assertNoOverlap(said)
        val release = cues.snapshot().first { it.cue is BattleCue.Release }
        assertTrue("the spell flies once its callout is spoken", release.at >= said.first().end)
        val turnEnd = cues.snapshot().first { it.cue is BattleCue.TurnEnd }
        val spoken = said.sumOf { it.end - it.start }
        assertTrue("the turn took about as long as its callouts: ${turnEnd.at - said.first().start} ms for $spoken ms of speech",
            turnEnd.at - said.first().start < spoken + 1_500)
    }

    @Test
    fun `with both on the callout comes before the spell flies, the report after it lands, then the enemy's move and hit, never overlapping`() {
        val narrator = CombatNarrator().apply { setSpellCalloutsEnabled(true); setBattleReportsEnabled(true) }
        val (vm, lines, cues) = battle(narrator, listOf(hero()), listOf(foe(speed = 200)))
        cast(vm, ResonanceTier.ADEPT)
        waitFor(what = "the enemy's hit") {
            lines.snapshot().any { it.kind == CombatSpeech.REPORT && it.text.startsWith("Aethel,") } && lines.snapshot().last().end >= 0
        }

        val said = lines.snapshot()
        println(said.joinToString("\n") { "[${it.kind}] ${it.text}  (${it.start}-${it.end} ms)" })
        assertNoOverlap(said)
        val impact = cues.snapshot().first { it.cue is BattleCue.Impact && (it.cue as BattleCue.Impact).fromParty }
        val hit = (impact.cue as BattleCue.Impact).hits.single()
        val report = said.first { it.kind == CombatSpeech.REPORT }
        assertEquals("the hero's blow, short", CombatLines.damage(listOf("Qxorc" to hit.amount), emptyList()), report.text)
        assertTrue("reported after it lands", report.start >= impact.at)
        val heroTurnEnd = cues.snapshot().first { it.cue is BattleCue.TurnEnd && (it.cue as BattleCue.TurnEnd).fromParty }
        assertTrue("the turn waits for its report", heroTurnEnd.at >= report.end)

        val move = said.first { it.kind == CombatSpeech.REPORT && it.text.startsWith("Qxorc:") }
        val enemyHit = said.first { it.kind == CombatSpeech.REPORT && it.text.startsWith("Aethel,") }
        assertTrue("the move is named, then its hit reported", move.start < enemyHit.start)
        said.forEach { assertTrue("\"${it.text}\" is short", CombatLines.words(it.text) <= CombatLines.WORD_LIMITS.getValue(it.kind)) }
    }

    @Test
    fun `pocket mode with reports off keeps the essentials, like who falls and the result`() {
        val narrator = CombatNarrator().apply { setSpellCalloutsEnabled(true); setBattleReportsEnabled(false); setEyesFreeMode(true, lockGuard = false) }
        val (vm, lines, cues) = battle(narrator, listOf(hero()), listOf(foe(hp = 1)))
        cast(vm, ResonanceTier.ADEPT)
        waitFor(what = "the battle to end") { cues.snapshot().any { it.cue is BattleCue.BattleEnd } && lines.snapshot().any { it.kind == CombatSpeech.FEEDBACK } }

        val said = lines.snapshot()
        println(said.joinToString("\n") { "[${it.kind}] ${it.text}  (${it.start}-${it.end} ms)" })
        assertTrue("no plain reports: $said", said.none { it.kind == CombatSpeech.REPORT })
        assertTrue("the foe going down is still said: $said", said.any { it.kind == CombatSpeech.ESSENTIAL && it.text == CombatLines.damage(emptyList(), listOf("Qxorc")) })
        assertTrue("and the result: $said", said.any { it.text == CombatLines.line("victory") })
        assertNoOverlap(said)
    }

    private companion object {
        const val MS_PER_WORD = 40L
    }
}
