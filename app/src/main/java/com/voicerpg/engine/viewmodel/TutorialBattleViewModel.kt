package com.voicerpg.engine.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.MusicManager
import com.voicerpg.engine.audio.SfxManager
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.engine.TutorialBattleContent
import com.voicerpg.engine.engine.TutorialBattleStep
import com.voicerpg.engine.engine.TutorialCharacterDef
import com.voicerpg.engine.engine.TutorialSpellDef
import com.voicerpg.engine.model.CharacterStance
import com.voicerpg.engine.model.DialogueSpeaker
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.ResonanceResult
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.model.Spell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

data class TutorialFloatingText(
    val id: Long = System.nanoTime(),
    val text: String,
    val color: Color,
    val targetId: String,
    val isCrit: Boolean = false
)

enum class ModalCardType {
    NARRATOR_INTRO,
    CHARACTER_JOIN,
    SPELL_OVERVIEW,
    PRACTICE_CAST_PROMPT,
    CAST_RESULT,
    ACTION_RESULT,
    ENEMY_ATTACK_INTRO,
    ENEMY_ATTACK_RESULT,
    COMPLETION
}

data class TutorialCastRecord(
    val spellName: String,
    val utterance: String,
    val resonanceTier: ResonanceTier,
    val bonusPercent: Int,
    val damage: Int
)

data class TutorialBattleUiState(
    val stepIndex: Int = 0,
    val currentStep: TutorialBattleStep = TutorialBattleContent.STEPS[0],
    val party: List<PartyMember> = emptyList(),
    val activePartyMemberId: String = TutorialBattleContent.CHARACTERS.firstOrNull()?.id ?: "hero",
    val enemies: List<Enemy> = TutorialBattleContent.ENEMIES,
    val targetedEnemyId: String = "dummy_alpha",

    // Centered Modal Dialogue Card
    val isModalVisible: Boolean = true,
    val modalType: ModalCardType = ModalCardType.NARRATOR_INTRO,
    val modalSpeaker: DialogueSpeaker = DialogueSpeaker.NARRATOR,
    val modalTitle: String = "TRAINING ARENA",
    val modalMessage: String = "",
    val modalHintChant: String? = null,
    val modalSpellToCast: TutorialSpellDef? = null,

    // Casting & Animations
    val isAnimating: Boolean = false,
    val castingHeroId: String? = null,
    val activeSpellName: String? = null,
    val lastCastRecord: TutorialCastRecord? = null,
    val resonanceDemoRecords: List<TutorialCastRecord> = emptyList(),
    val floatingTexts: List<TutorialFloatingText> = emptyList(),
    val screenShakeOffsetX: Float = 0f,
    val screenShakeOffsetY: Float = 0f,

    // Voice & Status
    val isListening: Boolean = false,
    val liveTranscript: String = "",
    val isCompleted: Boolean = false,
    val totalSteps: Int = TutorialBattleContent.totalSteps
)

class TutorialBattleViewModel(
    val speechManager: SpeechManager,
    val combatNarrator: CombatNarrator,
    val musicManager: MusicManager? = null,
    scopeOverride: CoroutineScope? = null
) : ViewModel() {

    private val scope = scopeOverride ?: viewModelScope
    private val resonanceEngine = ResonanceEngine()
    private val sfxManager = SfxManager()

    private val _state = MutableStateFlow(createInitialState())
    val state: StateFlow<TutorialBattleUiState> = _state.asStateFlow()

    private var actionJob: Job? = null

    init {
        applyStep(0, shouldNarrate = false)
    }

    fun startTutorial() {
        applyStep(0, shouldNarrate = true)
    }

    private fun createInitialState(): TutorialBattleUiState {
        val firstChar = TutorialBattleContent.CHARACTERS.firstOrNull()?.toPartyMember()
        return TutorialBattleUiState(
            stepIndex = 0,
            currentStep = TutorialBattleContent.STEPS[0],
            party = listOfNotNull(firstChar),
            activePartyMemberId = firstChar?.id ?: "hero",
            enemies = TutorialBattleContent.ENEMIES.map { it.copy() }
        )
    }

    fun resetTutorial() {
        actionJob?.cancel()
        actionJob = null
        val fresh = createInitialState()
        _state.value = fresh
        applyStep(0)
    }

    private fun TutorialCharacterDef.toPartyMember(): PartyMember {
        val spellList = spells.map { s ->
            Spell(
                id = s.name.lowercase().replace(" ", "_"),
                name = s.name,
                school = s.school,
                basePower = s.basePower,
                mpCost = s.mpCost,
                isHeal = s.isHeal,
                hitsAll = s.hitsAll,
                isGuard = s.isGuard,
                manaRestorePct = s.manaRestorePct,
                description = s.description,
                exampleChant = s.name,
                aliases = s.aliases
            )
        }
        return PartyMember(
            id = id,
            name = name,
            loreClass = loreClass,
            currentHp = maxHp,
            maxHp = maxHp,
            currentMp = maxMp,
            maxMp = maxMp,
            spells = spellList,
            stance = CharacterStance.READY,
            avatarTint = avatarTint,
            speed = speed,
            atbGauge = 1.0f
        )
    }

    fun selectEnemy(enemyId: String) {
        _state.value = _state.value.copy(
            targetedEnemyId = enemyId,
            enemies = _state.value.enemies.map {
                it.copy(isTargeted = it.id == enemyId)
            }
        )
    }

    fun advanceModal() {
        val cur = _state.value.currentStep
        when (cur) {
            is TutorialBattleStep.IntroPopup -> {
                goToNextStep()
            }
            is TutorialBattleStep.CharacterJoin -> {
                goToNextStep()
            }
            is TutorialBattleStep.SpellOverview -> {
                goToNextStep()
            }
            is TutorialBattleStep.PracticeCast -> {
                if (_state.value.modalType == ModalCardType.CAST_RESULT) {
                    goToNextStep()
                } else {
                    // Tap to begin casting hands-free
                    _state.value = _state.value.copy(isModalVisible = false)
                    startListening()
                }
            }
            is TutorialBattleStep.AutoAction -> {
                if (_state.value.modalType == ModalCardType.ACTION_RESULT) {
                    goToNextStep()
                } else {
                    executeAutoAction(cur)
                }
            }
            is TutorialBattleStep.EnemyAttackDemo -> {
                if (_state.value.modalType == ModalCardType.ENEMY_ATTACK_RESULT) {
                    goToNextStep()
                } else {
                    executeEnemyAttackDemo(cur)
                }
            }
            is TutorialBattleStep.CompletionPopup -> {
                // Done
            }
        }
    }

    private fun goToNextStep() {
        val nextIdx = _state.value.stepIndex + 1
        if (nextIdx < TutorialBattleContent.STEPS.size) {
            applyStep(nextIdx)
        } else {
            _state.value = _state.value.copy(
                isCompleted = true,
                isModalVisible = true,
                modalType = ModalCardType.COMPLETION,
                modalTitle = "TUTORIAL COMPLETE",
                modalMessage = "You have mastered the fundamentals of vocal incantation and fellowship command! The Logos echoes with your power."
            )
        }
    }

    private fun applyStep(index: Int, shouldNarrate: Boolean = true) {
        val step = TutorialBattleContent.STEPS[index]
        when (step) {
            is TutorialBattleStep.IntroPopup -> {
                _state.value = _state.value.copy(
                    stepIndex = index,
                    currentStep = step,
                    isModalVisible = true,
                    modalType = ModalCardType.NARRATOR_INTRO,
                    modalSpeaker = step.speaker,
                    modalTitle = "TRAINING ARENA",
                    modalMessage = step.text,
                    modalHintChant = null,
                    modalSpellToCast = null
                )
                if (shouldNarrate) {
                    narrate(step.text, step.speaker)
                }
            }
            is TutorialBattleStep.CharacterJoin -> {
                // Add character to party if not present
                val charDef = step.characterDef
                val updatedParty = if (_state.value.party.none { it.id == charDef.id }) {
                    _state.value.party + charDef.toPartyMember()
                } else {
                    _state.value.party
                }
                _state.value = _state.value.copy(
                    stepIndex = index,
                    currentStep = step,
                    party = updatedParty,
                    activePartyMemberId = charDef.id,
                    isModalVisible = true,
                    modalType = ModalCardType.CHARACTER_JOIN,
                    modalSpeaker = charDef.speaker,
                    modalTitle = "FELLOWSHIP JOIN",
                    modalMessage = step.text,
                    modalHintChant = null,
                    modalSpellToCast = null
                )
                narrate(step.text, charDef.speaker)
            }
            is TutorialBattleStep.SpellOverview -> {
                val charDef = step.characterDef
                _state.value = _state.value.copy(
                    stepIndex = index,
                    currentStep = step,
                    activePartyMemberId = charDef.id,
                    isModalVisible = true,
                    modalType = ModalCardType.SPELL_OVERVIEW,
                    modalSpeaker = charDef.speaker,
                    modalTitle = "${charDef.name.uppercase()}'S ARSENAL",
                    modalMessage = step.text,
                    modalHintChant = null,
                    modalSpellToCast = null
                )
                narrate(step.text, charDef.speaker)
            }
            is TutorialBattleStep.PracticeCast -> {
                val charDef = TutorialBattleContent.getCharacter(step.characterId)
                val spellDef = charDef?.spells?.firstOrNull { it.name.equals(step.spellName, ignoreCase = true) }
                _state.value = _state.value.copy(
                    stepIndex = index,
                    currentStep = step,
                    activePartyMemberId = step.characterId,
                    isModalVisible = true,
                    modalType = ModalCardType.PRACTICE_CAST_PROMPT,
                    modalSpeaker = DialogueSpeaker.NARRATOR,
                    modalTitle = if (step.isResonanceDemo) "RESONANCE SYSTEM DEMO" else "PRACTICE INCANTATION",
                    modalMessage = step.promptText,
                    modalHintChant = step.spellName,
                    modalSpellToCast = spellDef
                )
                narrate(step.promptText, DialogueSpeaker.NARRATOR)
            }
            is TutorialBattleStep.AutoAction -> {
                _state.value = _state.value.copy(
                    stepIndex = index,
                    currentStep = step,
                    activePartyMemberId = step.characterId,
                    isModalVisible = true,
                    modalType = ModalCardType.NARRATOR_INTRO,
                    modalSpeaker = DialogueSpeaker.NARRATOR,
                    modalTitle = "FREE BREATH ABILITY",
                    modalMessage = step.actionText,
                    modalHintChant = step.hintChant,
                    modalSpellToCast = null
                )
                narrate(step.actionText, DialogueSpeaker.NARRATOR)
            }
            is TutorialBattleStep.EnemyAttackDemo -> {
                _state.value = _state.value.copy(
                    stepIndex = index,
                    currentStep = step,
                    isModalVisible = true,
                    modalType = ModalCardType.ENEMY_ATTACK_INTRO,
                    modalSpeaker = DialogueSpeaker.NARRATOR,
                    modalTitle = "ENEMY COUNTERATTACK",
                    modalMessage = step.introText,
                    modalHintChant = null,
                    modalSpellToCast = null
                )
                narrate(step.introText, DialogueSpeaker.NARRATOR)
            }
            is TutorialBattleStep.CompletionPopup -> {
                _state.value = _state.value.copy(
                    stepIndex = index,
                    currentStep = step,
                    isCompleted = true,
                    isModalVisible = true,
                    modalType = ModalCardType.COMPLETION,
                    modalSpeaker = DialogueSpeaker.NARRATOR,
                    modalTitle = "TUTORIAL COMPLETE",
                    modalMessage = step.text,
                    modalHintChant = null,
                    modalSpellToCast = null
                )
                narrate(step.text, DialogueSpeaker.NARRATOR)
            }
        }
    }

    private fun narrate(text: String, speaker: DialogueSpeaker) {
        if (combatNarrator.isNarrationEnabled.value) {
            combatNarrator.narrateDialogue(speaker = speaker, text = text, onDone = {
                if (speechManager.isAutoListen.value) {
                    startListening()
                }
            })
        } else if (speechManager.isAutoListen.value) {
            startListening()
        }
    }

    fun castSpell(utterance: String = "") {
        val curStep = _state.value.currentStep
        val spellDef = _state.value.modalSpellToCast
            ?: (curStep as? TutorialBattleStep.PracticeCast)?.let { p ->
                TutorialBattleContent.getCharacter(p.characterId)?.spells?.firstOrNull { it.name.equals(p.spellName, ignoreCase = true) }
            }
            ?: return

        val heroId = _state.value.activePartyMemberId
        actionJob?.cancel()
        actionJob = scope.launch {
            stopListening()
            _state.value = _state.value.copy(
                isModalVisible = false,
                isAnimating = true,
                castingHeroId = heroId,
                activeSpellName = spellDef.name,
                party = _state.value.party.map {
                    if (it.id == heroId) it.copy(stance = CharacterStance.CASTING) else it
                }
            )

            sfxManager.playSpellCast()

            // Resonance calculation
            val isDemo = (curStep as? TutorialBattleStep.PracticeCast)?.isResonanceDemo == true
            val demoIdx = (curStep as? TutorialBattleStep.PracticeCast)?.resonanceDemoIndex ?: 0

            val resonance: ResonanceResult = if (isDemo) {
                if (demoIdx == 0) {
                    // Basic cast: Force BASIC
                    ResonanceResult(
                        rawText = spellDef.name,
                        score = 0.2f,
                        bonusPercent = 0,
                        tier = ResonanceTier.BASIC,
                        damageMultiplier = 1.0f,
                        particleCount = 40,
                        matchedThematicRoots = emptyList(),
                        syllableCount = 2
                    )
                } else {
                    // Elaborate cast: Force MASTER
                    ResonanceResult(
                        rawText = if (utterance.isNotBlank()) utterance else spellDef.name,
                        score = 0.85f,
                        bonusPercent = 60,
                        tier = ResonanceTier.MASTER,
                        damageMultiplier = 1.6f,
                        particleCount = 180,
                        matchedThematicRoots = listOf("sear", "blaze", "inferno"),
                        syllableCount = 12
                    )
                }
            } else {
                val inputToEvaluate = if (utterance.isNotBlank()) utterance else spellDef.name
                resonanceEngine.evaluate(inputToEvaluate, spellDef.school)
            }

            val baseDmg = spellDef.basePower * 2.2f
            val finalDamage = (baseDmg * resonance.damageMultiplier).roundToInt().coerceAtLeast(1)

            delay(600)

            // Impact and damage application
            sfxManager.playHitImpact()

            val targetId = _state.value.targetedEnemyId
            val isAoE = spellDef.hitsAll

            val newFloatingTexts = mutableListOf<TutorialFloatingText>()
            val updatedEnemies = _state.value.enemies.map { enemy ->
                if (isAoE || enemy.id == targetId) {
                    newFloatingTexts.add(
                        TutorialFloatingText(
                            text = "-$finalDamage",
                            color = spellDef.school.themeColor,
                            targetId = enemy.id,
                            isCrit = resonance.tier >= ResonanceTier.MASTER
                        )
                    )
                    enemy.copy(
                        currentHp = (enemy.currentHp - finalDamage).coerceAtLeast(100),
                        isDamagedFlash = true
                    )
                } else {
                    enemy
                }
            }

            // Deduct MP from casting hero
            val updatedParty = _state.value.party.map { hero ->
                if (hero.id == heroId) {
                    hero.copy(
                        currentMp = (hero.currentMp - spellDef.mpCost).coerceAtLeast(0)
                    )
                } else hero
            }

            _state.value = _state.value.copy(
                enemies = updatedEnemies,
                party = updatedParty,
                floatingTexts = _state.value.floatingTexts + newFloatingTexts,
                screenShakeOffsetX = if (resonance.tier >= ResonanceTier.MASTER) 12f else 6f,
                screenShakeOffsetY = if (resonance.tier >= ResonanceTier.MASTER) -10f else -5f
            )

            delay(200)
            _state.value = _state.value.copy(
                screenShakeOffsetX = 0f,
                screenShakeOffsetY = 0f
            )

            delay(800)

            // Reset stances and dummy flash
            val resetEnemies = _state.value.enemies.map { it.copy(isDamagedFlash = false) }
            val resetParty = _state.value.party.map { it.copy(stance = CharacterStance.READY) }

            val castRecord = TutorialCastRecord(
                spellName = spellDef.name,
                utterance = if (utterance.isNotBlank()) utterance else spellDef.name,
                resonanceTier = resonance.tier,
                bonusPercent = resonance.bonusPercent,
                damage = finalDamage
            )

            val updatedDemoRecords = if (isDemo) {
                _state.value.resonanceDemoRecords + castRecord
            } else {
                _state.value.resonanceDemoRecords
            }

            val targetDesc = if (isAoE) "ALL practice dummies" else "Practice Dummy Alpha"
            val resultMessage = if (isDemo && demoIdx == 0) {
                "Cast: '${castRecord.utterance}'\nResonance: ${resonance.tier.name} (Base Multiplier 1.0x)\nDealt $finalDamage damage to $targetDesc."
            } else if (isDemo && demoIdx == 1) {
                val previousDmg = updatedDemoRecords.firstOrNull()?.damage ?: (finalDamage / 1.6f).toInt()
                val diff = finalDamage - previousDmg
                "Cast: '${castRecord.utterance}'\nResonance: ${resonance.tier.name} (+${resonance.bonusPercent}% Power!)\nDealt $finalDamage damage to $targetDesc (+$diff bonus damage!)."
            } else {
                "⚡ ${spellDef.name} struck $targetDesc for $finalDamage damage!\nResonance: ${resonance.tier.name} (${if (resonance.bonusPercent > 0) "+${resonance.bonusPercent}%" else "Normal"})."
            }

            _state.value = _state.value.copy(
                isAnimating = false,
                castingHeroId = null,
                activeSpellName = null,
                enemies = resetEnemies,
                party = resetParty,
                lastCastRecord = castRecord,
                resonanceDemoRecords = updatedDemoRecords,
                floatingTexts = emptyList(),
                isModalVisible = true,
                modalType = ModalCardType.CAST_RESULT,
                modalTitle = "INCANTATION RESULT",
                modalMessage = resultMessage,
                modalHintChant = null,
                modalSpellToCast = null
            )

            narrate(resultMessage, DialogueSpeaker.NARRATOR)
        }
    }

    private fun executeAutoAction(step: TutorialBattleStep.AutoAction) {
        actionJob?.cancel()
        actionJob = scope.launch {
            _state.value = _state.value.copy(
                isModalVisible = false,
                isAnimating = true
            )
            sfxManager.playSpellCast()

            val heroId = step.characterId
            val updatedParty = _state.value.party.map { hero ->
                if (hero.id == heroId) {
                    hero.copy(currentMp = hero.maxMp)
                } else hero
            }

            val floatText = TutorialFloatingText(
                text = "+MANA RESTORED",
                color = Color(0xFF00E5FF),
                targetId = heroId
            )

            _state.value = _state.value.copy(
                party = updatedParty,
                floatingTexts = listOf(floatText)
            )

            delay(1000)

            _state.value = _state.value.copy(
                isAnimating = false,
                floatingTexts = emptyList(),
                isModalVisible = true,
                modalType = ModalCardType.ACTION_RESULT,
                modalTitle = "BREATH RESTORATION COMPLETE",
                modalMessage = step.resultText,
                modalHintChant = null,
                modalSpellToCast = null
            )
            narrate(step.resultText, DialogueSpeaker.NARRATOR)
        }
    }

    private fun executeEnemyAttackDemo(step: TutorialBattleStep.EnemyAttackDemo) {
        actionJob?.cancel()
        actionJob = scope.launch {
            _state.value = _state.value.copy(
                isModalVisible = false,
                isAnimating = true
            )

            // Dummy charges up
            delay(400)
            val updatedEnemies = _state.value.enemies.map {
                if (it.id == "dummy_alpha") it.copy(isDamagedFlash = true) else it
            }
            _state.value = _state.value.copy(enemies = updatedEnemies)

            delay(500)
            sfxManager.playHitImpact()

            // Damage first hero in party
            val targetHeroId = _state.value.party.first().id

            val enemyDmg = 35
            val updatedParty = _state.value.party.map { hero ->
                if (hero.id == targetHeroId) {
                    hero.copy(
                        currentHp = (hero.currentHp - enemyDmg).coerceAtLeast(1),
                        stance = CharacterStance.DAMAGED
                    )
                } else hero
            }

            val floatText = TutorialFloatingText(
                text = "-$enemyDmg",
                color = Color(0xFFEF5350),
                targetId = targetHeroId
            )

            _state.value = _state.value.copy(
                party = updatedParty,
                floatingTexts = listOf(floatText),
                screenShakeOffsetX = 8f,
                screenShakeOffsetY = -6f
            )

            delay(150)
            _state.value = _state.value.copy(
                screenShakeOffsetX = 0f,
                screenShakeOffsetY = 0f
            )

            delay(900)

            val resetParty = _state.value.party.map { it.copy(stance = CharacterStance.READY) }
            val resetEnemies = _state.value.enemies.map { it.copy(isDamagedFlash = false) }

            _state.value = _state.value.copy(
                isAnimating = false,
                party = resetParty,
                enemies = resetEnemies,
                floatingTexts = emptyList(),
                isModalVisible = true,
                modalType = ModalCardType.ENEMY_ATTACK_RESULT,
                modalTitle = "COMBAT STRATEGY",
                modalMessage = step.resultText,
                modalHintChant = null,
                modalSpellToCast = null
            )
            narrate(step.resultText, DialogueSpeaker.NARRATOR)
        }
    }

    fun handleVoiceInput(utterance: String) {
        val lower = utterance.trim().lowercase()
        if (lower.isBlank()) return

        _state.value = _state.value.copy(liveTranscript = utterance)

        // Navigation keywords
        val advanceKeywords = listOf("next", "continue", "proceed", "forward", "okay", "ok", "got it", "ready", "understand")
        if (advanceKeywords.any { lower.contains(it) } && _state.value.isModalVisible && _state.value.modalType != ModalCardType.PRACTICE_CAST_PROMPT) {
            advanceModal()
            return
        }

        // Spell casting in practice cast
        val curStep = _state.value.currentStep
        if (curStep is TutorialBattleStep.PracticeCast) {
            val spellNameLower = curStep.spellName.lowercase()
            val charDef = TutorialBattleContent.getCharacter(curStep.characterId)
            val spellDef = charDef?.spells?.firstOrNull { it.name.equals(curStep.spellName, ignoreCase = true) }
            val aliases = spellDef?.aliases ?: emptyList()

            val isMatch = lower.contains(spellNameLower) || aliases.any { lower.contains(it.lowercase()) }
            if (isMatch || curStep.isResonanceDemo) {
                castSpell(utterance)
            }
        }
    }

    fun startListening() {
        if (_state.value.isListening) return
        _state.value = _state.value.copy(isListening = true)
        speechManager.startListening(
            onResult = { utterance ->
                _state.value = _state.value.copy(isListening = false)
                handleVoiceInput(utterance)
            }
        )
    }

    fun stopListening() {
        speechManager.stopListening()
        _state.value = _state.value.copy(isListening = false)
    }

    override fun onCleared() {
        super.onCleared()
        actionJob?.cancel()
        speechManager.cancel()
        combatNarrator.stop()
    }
}
