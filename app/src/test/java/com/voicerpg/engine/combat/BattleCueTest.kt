package com.voicerpg.engine.combat

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

/**
 * Mechanics test: combat logic announces what happened as an ordered stream of [BattleCue]s, and every
 * wait between them comes from the [CombatTiming] table. Runs on the frozen template fixture.
 */
class BattleCueTest {

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

    private fun newViewModel(timing: CombatTiming = CombatTiming.INSTANT): CombatViewModel =
        CombatViewModel(
            speechManager = SpeechManager(),
            resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = timing,
            scopeOverride = vmScope
        ).also { viewModels += it }

    private data class Seen(val cue: BattleCue, val atMs: Long)

    private fun record(vm: CombatViewModel): MutableList<Seen> {
        val seen = Collections.synchronizedList(mutableListOf<Seen>())
        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.cues.collect { seen.add(Seen(it, System.currentTimeMillis())) }
        }
        return seen
    }

    /** Settle pattern (AGENTS.md §5): poll for the state we wait on, never spin the event loop. */
    private fun waitFor(timeoutMs: Long = 5000, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (cond()) return
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for $what")
    }

    private fun List<Seen>.cues(): List<BattleCue> = synchronized(this) { map { it.cue } }
    private fun List<Seen>.names(): List<String> = cues().map { it::class.simpleName ?: "?" }

    private fun elementalist(hp: Int = 240, atb: Float = 0f) = PartyMember(
        id = "hero", name = "Aethel", loreClass = "Elementalist",
        currentHp = hp, maxHp = 240, currentMp = 160, maxMp = 160,
        spells = GameContent.spellsForClass("elementalist"), speed = 50, atbGauge = atb
    )

    private fun healer() = PartyMember(
        id = "cedric", name = "Sir Cedric", loreClass = "Templar",
        currentHp = 120, maxHp = 420, currentMp = 90, maxMp = 90,
        spells = listOfNotNull(GameContent.spellById("mending_touch"), GameContent.spellById("attune")),
        speed = 40
    )

    private fun foe(id: String, hp: Int = 200, atb: Float = 0f) =
        Enemy(id = id, name = "Foe $id", subtitle = "Vanguard", currentHp = hp, maxHp = 200, baseAttack = 20, speed = 50, atbGauge = atb)

    @Test
    fun `a killing cast plays reveal, release, impact, death, then victory`() {
        val vm = newViewModel()
        val seen = record(vm)
        vm.startEncounter(listOf(elementalist()), listOf(foe("archer", hp = 1)), BattleEnvironment.DUNGEON)
        vm.setPlayerInputPhaseForTesting("hero")

        vm.processIncantation("Fireball archer")
        waitFor(what = "victory") { vm.state.value.phase == CombatPhase.BATTLE_WON }
        waitFor(what = "BattleEnd cue") { seen.cues().any { it is BattleCue.BattleEnd } }

        assertEquals(
            listOf("BattleStart", "Release", "ResonanceReveal", "Impact", "Death", "BattleEnd"),
            seen.names()
        )
        val cues = seen.cues()
        val release = cues.filterIsInstance<BattleCue.Release>().single()
        assertEquals("hero", release.casterId)
        assertTrue(release.fromParty)
        assertEquals(listOf("archer"), release.targetIds)
        val impact = cues.filterIsInstance<BattleCue.Impact>().single()
        assertTrue(impact.fromParty)
        assertEquals("archer", impact.hits.single().targetId)
        assertTrue("damage lands", impact.hits.single().amount > 0)
        assertEquals(BattleCue.Death("archer", isEnemy = true), cues.filterIsInstance<BattleCue.Death>().single())
        assertEquals(BattleCue.BattleEnd(victory = true), cues.last())
    }

    @Test
    fun `an area spell releases one projectile per foe and resolves every hit in one impact`() {
        val vm = newViewModel()
        val seen = record(vm)
        vm.startEncounter(listOf(elementalist()), listOf(foe("orc"), foe("archer"), foe("shaman")), BattleEnvironment.DUNGEON)
        vm.setPlayerInputPhaseForTesting("hero")

        vm.processIncantation("Chain lightning all foes")
        waitFor(what = "hero turn end") { seen.cues().any { it is BattleCue.TurnEnd } }

        val cues = seen.cues()
        val heroTurn = cues.dropWhile { it !is BattleCue.Release }.takeWhile { it !is BattleCue.TurnEnd } +
            cues.first { it is BattleCue.TurnEnd }
        assertEquals(listOf("Release", "ResonanceReveal", "Impact", "TurnEnd"), heroTurn.map { it::class.simpleName })
        assertEquals(listOf("orc", "archer", "shaman"), cues.filterIsInstance<BattleCue.Release>().single().targetIds)
        assertEquals(listOf("orc", "archer", "shaman"), cues.filterIsInstance<BattleCue.Impact>().single().hits.map { it.targetId })
        assertTrue("nobody fell", cues.none { it is BattleCue.Death })
        assertEquals(BattleCue.TurnEnd("hero", fromParty = true), heroTurn.last())
    }

    @Test
    fun `a heal and a breath turn both resolve as impacts on the party`() {
        val vm = newViewModel()
        val seen = record(vm)
        vm.startEncounter(listOf(elementalist(hp = 100), healer()), listOf(foe("orc")), BattleEnvironment.DUNGEON)
        vm.setPlayerInputPhaseForTesting("cedric")

        vm.processIncantation("Mending touch Aethel")
        waitFor(what = "heal turn end") { seen.cues().any { it is BattleCue.TurnEnd } }
        val heal = seen.cues().filterIsInstance<BattleCue.Release>().single()
        assertEquals(BattleCue.ReleaseStyle.HEAL, heal.style)
        val healHit = seen.cues().filterIsInstance<BattleCue.Impact>().single().hits.single()
        assertEquals(BattleCue.HitKind.HEAL, healHit.kind)
        assertEquals("hero", healHit.targetId)
        assertTrue(healHit.amount > 0)

        seen.clear()
        vm.setPlayerInputPhaseForTesting("cedric")
        vm.processIncantation("attune")
        waitFor(what = "breath turn end") { seen.cues().any { it is BattleCue.TurnEnd } }
        assertEquals(BattleCue.ReleaseStyle.ATTUNE, seen.cues().filterIsInstance<BattleCue.Release>().single().style)
        assertEquals(BattleCue.HitKind.MANA, seen.cues().filterIsInstance<BattleCue.Impact>().single().hits.single().kind)
    }

    @Test
    fun `an enemy turn announces its move, fires at the party, and ends its turn`() {
        val vm = newViewModel()
        val seen = record(vm)
        val sniper = GameContent.enemyTemplates.getValue("template_sniper").toEnemy(instanceId = "sniper", atbGauge = 0.99f)
        vm.startEncounter(listOf(elementalist(atb = 0f)), listOf(sniper), BattleEnvironment.DUNGEON)

        waitFor(what = "enemy turn end") { seen.cues().any { it is BattleCue.TurnEnd } }

        val turn = seen.cues().drop(1).takeWhile { it !is BattleCue.TurnEnd } + seen.cues().first { it is BattleCue.TurnEnd }
        assertEquals(listOf("Telegraph", "EnemyMove", "TitleCard", "Release", "Impact", "TurnEnd"), turn.map { it::class.simpleName })
        assertEquals("used the telegraphed move", turn.filterIsInstance<BattleCue.Telegraph>().single().moveName,
            turn.filterIsInstance<BattleCue.EnemyMove>().single().moveName)
        val release = turn.filterIsInstance<BattleCue.Release>().single()
        assertEquals("sniper", release.casterId)
        assertTrue("enemies fire from the right", !release.fromParty)
        assertEquals(listOf("hero"), release.targetIds)
        assertTrue(!turn.filterIsInstance<BattleCue.Impact>().single().fromParty)
        assertEquals(BattleCue.TurnEnd("sniper", fromParty = false), turn.last())
    }

    @Test
    fun `losing the last hero ends the battle in defeat after the killing blow`() {
        val vm = newViewModel()
        val seen = record(vm)
        val sniper = GameContent.enemyTemplates.getValue("template_sniper").toEnemy(instanceId = "sniper", atbGauge = 0.99f)
        vm.startEncounter(listOf(elementalist(hp = 1)), listOf(sniper), BattleEnvironment.DUNGEON)

        waitFor(what = "defeat") { vm.state.value.phase == CombatPhase.BATTLE_LOST }
        waitFor(what = "BattleEnd cue") { seen.cues().any { it is BattleCue.BattleEnd } }

        val tail = seen.names().dropWhile { it != "Impact" }
        assertEquals(listOf("Impact", "Death", "BattleEnd"), tail)
        assertEquals(BattleCue.Death("hero", isEnemy = false), seen.cues().filterIsInstance<BattleCue.Death>().single())
        assertEquals(BattleCue.BattleEnd(victory = false), seen.cues().last())
    }

    @Test
    fun `while the active hero is heard, the charge follows the microphone`() {
        val vm = newViewModel()
        val seen = record(vm)
        vm.startEncounter(listOf(elementalist()), listOf(foe("orc")), BattleEnvironment.DUNGEON)
        vm.setPlayerInputPhaseForTesting("hero")

        vm.speechManager.setSpeechStateForTesting(com.voicerpg.engine.audio.SpeechState.Listening)
        waitFor(what = "charge start") { seen.cues().any { it is BattleCue.ChargeStart } }
        assertEquals(BattleCue.ChargeStart("hero", typed = false), seen.cues().filterIsInstance<BattleCue.ChargeStart>().single())

        vm.speechManager.setRmsLevelForTesting(0.6f)
        waitFor(what = "charge level") { seen.cues().any { it is BattleCue.ChargeLevel && it.level == 0.6f } }

        vm.speechManager.setSpeechStateForTesting(com.voicerpg.engine.audio.SpeechState.Idle)
        waitFor(what = "charge end") { seen.cues().any { it is BattleCue.ChargeEnd } }
        assertEquals(BattleCue.ChargeEnd("hero"), seen.cues().last())
    }

    @Test
    fun `a typed chant plays a short simulated charge before the spell flies`() {
        val timing = CombatTiming.DEFAULT
        val vm = newViewModel(timing)
        val seen = record(vm)
        vm.startEncounter(listOf(elementalist()), listOf(foe("orc")), BattleEnvironment.DUNGEON)
        vm.setPlayerInputPhaseForTesting("hero")

        vm.submitTypedChant("Fireball orc")
        waitFor(timeoutMs = 8000, what = "turn end") { seen.cues().any { it is BattleCue.TurnEnd } }

        val names = seen.names().dropWhile { it != "ChargeStart" }
        assertEquals(listOf("ChargeStart", "ChargeEnd", "Release", "ResonanceReveal", "Impact", "TurnEnd"), names)
        val start = synchronized(seen) { seen.first { it.cue is BattleCue.ChargeStart } }
        val end = synchronized(seen) { seen.first { it.cue is BattleCue.ChargeEnd } }
        assertEquals(BattleCue.ChargeStart("hero", typed = true), start.cue)
        assertTrue("the simulated charge holds", end.atMs - start.atMs >= timing.holdMs(start.cue, pocket = false) - 150)
    }

    @Test
    fun `the waits between cues come from the timing table`() {
        val timing = CombatTiming.DEFAULT
        val vm = newViewModel(timing)
        val seen = record(vm)
        vm.startEncounter(listOf(elementalist()), listOf(foe("orc", hp = 200)), BattleEnvironment.DUNGEON)
        vm.setPlayerInputPhaseForTesting("hero")

        vm.processIncantation("Fireball orc")
        waitFor(timeoutMs = 8000, what = "hero turn end") { seen.cues().any { it is BattleCue.TurnEnd } }

        fun at(name: String) = synchronized(seen) { seen.first { it.cue::class.simpleName == name } }
        val release = at("Release")
        val reveal = at("ResonanceReveal")
        val impact = at("Impact")
        val end = at("TurnEnd")
        val tolerance = 150L
        val travel = (release.cue as BattleCue.Release).travelMs
        assertEquals("projectiles fly for the table's travel time",
            timing.travelMs(fromParty = true, isBossUltimate = false, tier = (release.cue as BattleCue.Release).tier), travel)
        assertTrue("the voice is revealed as the spell lands, not before it flies", reveal.atMs - release.atMs >= travel - tolerance)
        assertTrue("reveal and impact are one moment", impact.atMs - reveal.atMs < tolerance)
        assertTrue("the result is held before the turn ends", end.atMs - impact.atMs >= timing.holdMs(impact.cue, pocket = false) - tolerance)
        assertTrue("no pre-hit banner wait: cast to impact is just the flight", impact.atMs - release.atMs < travel + 300)

        // The same cast with the instant table (tests, and later pocket fast-forward) takes no time at all.
        val fast = newViewModel(CombatTiming.INSTANT)
        val fastSeen = record(fast)
        fast.startEncounter(listOf(elementalist()), listOf(foe("orc", hp = 200)), BattleEnvironment.DUNGEON)
        fast.setPlayerInputPhaseForTesting("hero")
        val started = System.currentTimeMillis()
        fast.processIncantation("Fireball orc")
        waitFor(what = "instant turn end") { fastSeen.cues().any { it is BattleCue.TurnEnd } }
        assertTrue("instant timing resolves at once", System.currentTimeMillis() - started < 600)
    }
}
