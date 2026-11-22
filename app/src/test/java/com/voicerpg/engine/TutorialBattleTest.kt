package com.voicerpg.engine

import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.engine.TutorialBattleContent
import com.voicerpg.engine.engine.TutorialBattleStep
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.viewmodel.ModalCardType
import com.voicerpg.engine.viewmodel.TutorialBattleViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TutorialBattleTest {

    private lateinit var viewModel: TutorialBattleViewModel
    private lateinit var dummySpeech: SpeechManager
    private lateinit var dummyNarrator: CombatNarrator
    private val testScope = CoroutineScope(Dispatchers.Default)

    private val heroId: String
        get() = TutorialBattleContent.CHARACTERS.first().id

    @Before
    fun setUp() {
        dummySpeech = SpeechManager()
        dummyNarrator = CombatNarrator()
        viewModel = TutorialBattleViewModel(
            speechManager = dummySpeech,
            combatNarrator = dummyNarrator,
            scopeOverride = testScope
        )
    }

    /** Run [trigger], then wait until the state advances out of the pre-trigger snapshot,
     *  the animation/listening settles, and a modal is showing again. */
    private suspend fun TutorialBattleViewModel.waitForChangeAndIdle(trigger: (TutorialBattleViewModel) -> Unit) {
        val before = state.value
        trigger(this)
        var waited = 0
        while (waited < 8000) {
            val s = state.value
            if (s !== before && !s.isAnimating && !s.isListening && s.isModalVisible) return
            delay(20)
            waited += 20
        }
    }

    private suspend fun TutorialBattleViewModel.waitForChangeAndIdle() {
        waitForChangeAndIdle { it.advanceModal() }
    }

    /** Advance one modal after its animation (if any) settles. Practice casts are cast hands-free. */
    private suspend fun TutorialBattleViewModel.advanceAndSettle() {
        val cur = state.value.currentStep
        if (cur is TutorialBattleStep.PracticeCast && state.value.modalType != ModalCardType.CAST_RESULT) {
            waitForChangeAndIdle { vm -> vm.castSpell(cur.spellName) }
            return
        }
        waitForChangeAndIdle()
    }

    private suspend fun TutorialBattleViewModel.fastForwardUntil(predicate: (TutorialBattleStep) -> Boolean) {
        while (!predicate(state.value.currentStep)) {
            advanceAndSettle()
        }
    }

    private fun TutorialBattleStep.practiceSpellName(): String =
        (this as TutorialBattleStep.PracticeCast).spellName

    @Test
    fun testInitialTutorialState() {
        val state = viewModel.state.value
        assertEquals(0, state.stepIndex)
        assertTrue(state.isModalVisible)
        assertEquals(ModalCardType.NARRATOR_INTRO, state.modalType)
        assertEquals(1, state.party.size)
        assertEquals(heroId, state.party[0].id)
        assertEquals(3, state.enemies.size)
        assertEquals("dummy_alpha", state.enemies[0].id)
        assertEquals("dummy_beta", state.enemies[1].id)
        assertEquals("dummy_gamma", state.enemies[2].id)
        assertEquals("dummy_alpha", state.targetedEnemyId)
        assertEquals(heroId, state.activePartyMemberId)
    }

    @Test
    fun testStepProgressionAndHeroJoin() {
        // Step 0: Intro 1
        assertEquals(0, viewModel.state.value.stepIndex)
        viewModel.advanceModal()

        // Step 1: Intro 2
        assertEquals(1, viewModel.state.value.stepIndex)
        viewModel.advanceModal()

        // Step 2: Hero Join
        assertEquals(2, viewModel.state.value.stepIndex)
        assertEquals(ModalCardType.CHARACTER_JOIN, viewModel.state.value.modalType)
        assertEquals(1, viewModel.state.value.party.size)
        viewModel.advanceModal()

        // Step 3: Hero Spell Overview
        assertEquals(3, viewModel.state.value.stepIndex)
        assertEquals(ModalCardType.SPELL_OVERVIEW, viewModel.state.value.modalType)
        viewModel.advanceModal()

        // Step 4: First Practice Cast
        assertEquals(4, viewModel.state.value.stepIndex)
        assertEquals(ModalCardType.PRACTICE_CAST_PROMPT, viewModel.state.value.modalType)
        assertTrue(viewModel.state.value.currentStep is TutorialBattleStep.PracticeCast)
    }

    @Test
    fun testPracticeCastExecutionAndDamage() = runBlocking {
        // Fast-forward to the first Practice Cast
        viewModel.fastForwardUntil { it is TutorialBattleStep.PracticeCast }

        val initialDummyHp = viewModel.state.value.enemies.first { it.id == "dummy_alpha" }.currentHp
        val initialHeroMp = viewModel.state.value.party.first { it.id == heroId }.currentMp
        val spellName = viewModel.state.value.currentStep.practiceSpellName()
        val mpCost = TutorialBattleContent.getCharacter(heroId)!!.spells
            .first { it.name.equals(spellName, ignoreCase = true) }.mpCost

        viewModel.waitForChangeAndIdle { it.castSpell(spellName) }

        val updatedState = viewModel.state.value
        val damagedDummy = updatedState.enemies.first { it.id == "dummy_alpha" }
        val updatedHero = updatedState.party.first { it.id == heroId }

        assertTrue("Dummy HP should be reduced", damagedDummy.currentHp < initialDummyHp)
        assertEquals(initialHeroMp - mpCost, updatedHero.currentMp)
        assertEquals(ModalCardType.CAST_RESULT, updatedState.modalType)
        assertTrue(updatedState.isModalVisible)
        assertNotNull(updatedState.lastCastRecord)
    }

    @Test
    fun testAutoActionBreathRestoresMana() = runBlocking {
        viewModel.fastForwardUntil { it is TutorialBattleStep.AutoAction }

        assertTrue(viewModel.state.value.currentStep is TutorialBattleStep.AutoAction)

        // Execute auto action
        viewModel.waitForChangeAndIdle()

        val state = viewModel.state.value
        assertEquals(ModalCardType.ACTION_RESULT, state.modalType)
        val hero = state.party.first { it.id == heroId }
        assertEquals(hero.maxMp, hero.currentMp)
    }

    @Test
    fun testResonanceComparisonDemo() = runBlocking {
        // Navigate to Resonance Demo Step 1 (simple cast)
        viewModel.fastForwardUntil {
            it is TutorialBattleStep.PracticeCast && it.isResonanceDemo && it.resonanceDemoIndex == 0
        }

        val step0 = viewModel.state.value.currentStep as TutorialBattleStep.PracticeCast
        assertTrue(step0.isResonanceDemo)
        assertEquals(0, step0.resonanceDemoIndex)

        // Cast simple spell name
        viewModel.waitForChangeAndIdle { it.castSpell(step0.spellName) }

        val simpleRecord = viewModel.state.value.lastCastRecord
        assertNotNull(simpleRecord)
        assertEquals(ResonanceTier.BASIC, simpleRecord!!.resonanceTier)
        assertEquals(0, simpleRecord.bonusPercent)
        val simpleDamage = simpleRecord.damage

        // Advance to elaborate cast (resonance demo step 2)
        viewModel.waitForChangeAndIdle()
        val step1 = viewModel.state.value.currentStep as? TutorialBattleStep.PracticeCast
        assertTrue(step1 is TutorialBattleStep.PracticeCast && step1.resonanceDemoIndex == 1)

        // Cast elaborate chant
        viewModel.waitForChangeAndIdle { it.castSpell("O primordial flame of creation, incinerate this practice target!") }

        val elaborateRecord = viewModel.state.value.lastCastRecord
        assertNotNull(elaborateRecord)
        assertEquals(ResonanceTier.MASTER, elaborateRecord!!.resonanceTier)
        assertEquals(60, elaborateRecord.bonusPercent)
        val elaborateDamage = elaborateRecord.damage

        assertTrue(
            "Elaborate chant ($elaborateDamage dmg) must deal significantly more damage than simple chant ($simpleDamage dmg)",
            elaborateDamage > simpleDamage
        )
    }

    @Test
    fun testEnemyCounterAttackDemo() = runBlocking {
        // Fast-forward to the EnemyAttackDemo step (generated by buildSteps, found dynamically)
        viewModel.fastForwardUntil { it is TutorialBattleStep.EnemyAttackDemo }

        val initialTargetHp = viewModel.state.value.party.first().currentHp
        assertEquals(ModalCardType.ENEMY_ATTACK_INTRO, viewModel.state.value.modalType)

        // Execute the demo (advance from intro, let the animation play out)
        viewModel.advanceAndSettle()

        val state = viewModel.state.value
        assertEquals(ModalCardType.ENEMY_ATTACK_RESULT, state.modalType)
        val targetHero = state.party.first { it.id == heroId }
        assertTrue(
            "Hero should have taken counterattack damage",
            targetHero.currentHp < initialTargetHp
        )
    }

    @Test
    fun testFullResetTutorial() {
        // Advance a few steps through the modal sequence
        for (i in 0 until 10) {
            viewModel.advanceModal()
        }
        assertTrue(viewModel.state.value.stepIndex > 0)

        viewModel.resetTutorial()

        val resetState = viewModel.state.value
        assertEquals(0, resetState.stepIndex)
        assertEquals(1, resetState.party.size)
        assertEquals(heroId, resetState.party[0].id)
        assertEquals(3, resetState.enemies.size)
        assertEquals(ModalCardType.NARRATOR_INTRO, resetState.modalType)
    }
}