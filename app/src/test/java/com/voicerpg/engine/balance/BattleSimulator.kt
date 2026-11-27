package com.voicerpg.engine.balance

import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.combat.AutoPolicy
import com.voicerpg.engine.combat.BattleCue
import com.voicerpg.engine.combat.CombatTiming
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.model.CombatPhase
import com.voicerpg.engine.model.EncounterDefinition
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.model.SavedCharacterStats
import com.voicerpg.engine.viewmodel.CombatViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * Plays one story fight with the real [CombatViewModel] (instant timing) on virtual time: every hero is played
 * automatically by an [AutoPolicy] and every cast lands at a fixed resonance. The battle is set up exactly as the
 * app does it (player customization, story flags, saved stats, then the encounter), so the heroes have the spells
 * and stats the story has given them at that point.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object BattleSimulator {

    data class FightSetup(
        val encounter: EncounterDefinition,
        val player: PlayerCustomization,
        val flags: Map<String, Boolean>,
        val partyStats: List<SavedCharacterStats>
    )

    data class FightResult(
        val finished: Boolean,
        val won: Boolean,
        /** Rounds of the battle (everyone alive acting once is a round). */
        val rounds: Int,
        val partyMaxHp: Int,
        val enemyMaxHp: Int,
        /** All damage the party took, healed or not. */
        val damageTaken: Int,
        val partyActions: Int,
        /** Party turns spent healing or restoring mana. */
        val supportActions: Int,
        /** Times a party member fell. */
        val falls: Int,
        val spellsUsed: Set<String>
    ) {
        /** How many times over the party could have absorbed what it took: (rounds to wipe) / (rounds to win). */
        val safetyMargin: Float get() = if (damageTaken <= 0) 99f else partyMaxHp.toFloat() / damageTaken
        val supportShare: Float get() = if (partyActions == 0) 0f else supportActions.toFloat() / partyActions
    }

    /** Virtual time a fight may take before it counts as stuck (a loop): 30 minutes of battle. */
    private const val CAP_MS = 30 * 60 * 1000L
    private const val STEP_MS = 200L

    fun run(setup: FightSetup, policy: AutoPolicy, resonance: Float, seed: Int, difficulty: String? = null): FightResult {
        val scope = TestScope(StandardTestDispatcher())
        val narrator = CombatNarrator().apply {
            setSpellCalloutsEnabled(false); setBattleReportsEnabled(false)
            lineSinkForTesting = { _, _, done -> done() }
        }
        val vm = CombatViewModel(SpeechManager(), combatNarrator = narrator, resonanceEngine = ResonanceEngine(NoveltyCache()),
            timing = CombatTiming.INSTANT, random = kotlin.random.Random(seed), scopeOverride = scope)
        var damageTaken = 0
        val cueJob = scope.backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.cues.collect { cue ->
                if (cue is BattleCue.Impact && !cue.fromParty) {
                    damageTaken += cue.hits.filter { it.kind == BattleCue.HitKind.DAMAGE }.sumOf { it.amount }
                }
            }
        }
        var actions = 0
        var support = 0
        val used = mutableSetOf<String>()
        vm.autoActionListener = { _, spell ->
            actions++
            used += spell.id
            if (spell.isHeal || spell.manaRestorePct > 0f) support++
        }
        try {
            vm.setPureStoryMode(true)
            vm.configureAutoPlay(policy, resonance)
            vm.difficultyOverride = difficulty
            vm.applyStoryFlags(setup.flags)
            vm.applyPlayerCustomization(setup.player, setup.partyStats)
            vm.applySavedStats(setup.partyStats)
            vm.startEncounter(setup.encounter)
            scope.testScheduler.runCurrent()

            val partyMaxHp = vm.state.value.party.filter { !it.isGhost }.sumOf { it.maxHp }
            val enemyMaxHp = vm.state.value.enemies.sumOf { it.maxHp }
            val alive = vm.state.value.party.associate { it.id to it.isAlive }.toMutableMap()
            var falls = 0
            var elapsed = 0L
            fun over() = vm.state.value.phase == CombatPhase.BATTLE_WON || vm.state.value.phase == CombatPhase.BATTLE_LOST
            while (!over() && elapsed < CAP_MS) {
                scope.testScheduler.advanceTimeBy(STEP_MS)
                scope.testScheduler.runCurrent()
                elapsed += STEP_MS
                vm.state.value.party.forEach { m ->
                    if (alive[m.id] == true && !m.isAlive) falls++
                    alive[m.id] = m.isAlive
                }
            }
            val state = vm.state.value
            return FightResult(
                finished = over(),
                won = state.phase == CombatPhase.BATTLE_WON,
                rounds = state.roundNumber,
                partyMaxHp = partyMaxHp,
                enemyMaxHp = enemyMaxHp,
                damageTaken = damageTaken,
                partyActions = actions,
                supportActions = support,
                falls = falls,
                spellsUsed = used
            )
        } finally {
            cueJob.cancel()
            vm.cleanup()
            scope.cancel()
        }
    }
}
