package com.voicerpg.engine.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.IncidentManager
import com.voicerpg.engine.engine.IntentParser
import com.voicerpg.engine.engine.SaveManager
import com.voicerpg.engine.engine.QuestRecap
import com.voicerpg.engine.engine.StoryChoiceMatcher
import com.voicerpg.engine.engine.StoryRecapEngine
import com.voicerpg.engine.engine.StoryScript
import com.voicerpg.engine.model.DialogueChoice
import com.voicerpg.engine.model.DialogueLogEntry
import com.voicerpg.engine.model.DialogueNode
import com.voicerpg.engine.model.EncounterDefinition
import com.voicerpg.engine.model.GameSaveData
import com.voicerpg.engine.model.GameScreen
import com.voicerpg.engine.model.MetaCommand
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.ui.story.StoryAssetLoader
import com.voicerpg.engine.model.SaveSlotInfo
import com.voicerpg.engine.model.SaveSummary
import com.voicerpg.engine.model.SavedCharacterStats
import com.voicerpg.engine.model.StoryScene
import com.voicerpg.engine.model.MiniGameConfig
import com.voicerpg.engine.model.MiniGameOutcome
import com.voicerpg.engine.engine.MiniGameSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StoryState(
    val currentScene: StoryScene = StoryScript.INITIAL_SCENE,
    val currentNode: DialogueNode = StoryScript.INITIAL_NODE,
    val gameScreen: GameScreen = GameScreen.TITLE,
    val previousScreen: GameScreen? = null,
    val activeEncounter: EncounterDefinition? = null,
    val player: PlayerCustomization = PlayerCustomization(),
    val decisionsMade: List<String> = emptyList(),
    val narrativeFlags: Map<String, Boolean> = emptyMap(),
    val visitedNodeIds: Set<String> = emptySet(),
    val defeatedEncounters: List<String> = emptyList(),
    val achievements: List<String> = emptyList(),
    val partyStats: List<SavedCharacterStats> = emptyList(),
    val isNarratorSpeaking: Boolean = false,
    val isTypingComplete: Boolean = true,
    val hasExistingSave: Boolean = false,
    val saveSummary: SaveSummary? = null,
    val currentSlot: Int = 1,
    val saveSlots: List<SaveSlotInfo> = emptyList(),
    val dialogueHistory: List<DialogueLogEntry> = emptyList(),
    val isBacklogOpen: Boolean = false,
    val isFastForwarding: Boolean = false,
    val isBacklogRecapActive: Boolean = true,
    val isNewGameFlow: Boolean = false,
    val isPureStoryMode: Boolean = false,
    val isStoryAutoPlayPaused: Boolean = false,
    val dynamicMetrics: Map<String, Float> = emptyMap(),
    val activeHubNodeId: String? = null,
    val activeMiniGameConfig: MiniGameConfig? = null,
    val isMiniGameTestMenuOpen: Boolean = false,
    val returnToOptionsOnConfirm: Boolean = false,
    val pendingSelectedLanguage: String = "en",
    val pendingSelectedRegion: String = "",
    val selectedLanguage: String = "en",
    val selectedRegion: String = "",
    val showFirstLaunchLanguagePrompt: Boolean = false
)

class StoryViewModel(
    val speechManager: SpeechManager,
    val combatNarrator: CombatNarrator,
    val saveManager: SaveManager = SaveManager(),
    val musicManager: com.voicerpg.engine.audio.MusicManager? = null,
    private val scopeOverride: CoroutineScope? = null
) : ViewModel() {

    data class DebugChapterTarget(
        val label: String,
        val introNodeId: String,
        val chapterIndex: Int
    )

    companion object {
        /** Engine convention: a node setting this flag marks the playthrough complete. */
        const val GAME_COMPLETED_FLAG = "game_completed"

        /**
         * Chapter warp targets derived from scene data files (chapterIndex + entry node).
         * No chapters are hardcoded in the engine.
         */
        val DEBUG_CHAPTER_TARGETS: List<DebugChapterTarget>
            get() = GameContent.scenes.values
                .distinctBy { it.chapterIndex }
                .sortedBy { it.chapterIndex }
                .map { DebugChapterTarget(it.chapterTitle, it.initialNodeId, it.chapterIndex) }
    }

    private val activeScope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow(StoryState())
    val state: StateFlow<StoryState> = _state.asStateFlow()

    private var pendingAutoAdvanceJob: Job? = null

    fun cancelPendingAutoAdvance() {
        pendingAutoAdvanceJob?.cancel()
        pendingAutoAdvanceJob = null
    }

    fun isChoiceLocked(choice: DialogueChoice): Boolean {
        val node = _state.value.currentNode
        return isChoiceLocked(choice, node, _state.value.narrativeFlags)
    }

    fun isChoiceCompleted(
        choice: DialogueChoice,
        flags: Map<String, Boolean> = _state.value.narrativeFlags,
        defeated: List<String> = _state.value.defeatedEncounters
    ): Boolean {
        if (choice.completionFlag != null && flags[choice.completionFlag] == true) {
            return true
        }
        val target = StoryScript.ALL_NODES[choice.nextNodeId]
        if (target?.triggerBattleEncounterId != null && defeated.contains(target.triggerBattleEncounterId)) {
            return true
        }
        return false
    }

    fun isChoiceLocked(choice: DialogueChoice, node: DialogueNode, flags: Map<String, Boolean>): Boolean {
        // 1. Explicit choice required flags
        if (choice.requiredFlags.isNotEmpty() && !choice.requiredFlags.all { flags[it] == true }) {
            return true
        }
        // 2. Hub gating: In a hub node (declaring hubCompletion), all dialogue options
        // (choices with completionFlag that do not trigger battle) must be completed
        // before advancing choices (e.g. battles, scene transitions) are unlocked.
        val completion = node.hubCompletion
        val targetNode = StoryScript.ALL_NODES[choice.nextNodeId]
        val isBattleOrAdvance = choice.completionFlag == null ||
            targetNode?.triggerBattleEncounterId != null ||
            targetNode?.changeSceneId != null

        if (completion != null && isBattleOrAdvance) {
            val dialogueChoices = node.choices.filter { other ->
                val otherTarget = StoryScript.ALL_NODES[other.nextNodeId]
                other.completionFlag != null && otherTarget?.triggerBattleEncounterId == null
            }
            if (dialogueChoices.isNotEmpty()) {
                val allDialogueDone = dialogueChoices.all { flags[it.completionFlag] == true }
                if (!allDialogueDone) {
                    return true
                }
            }
        }
        return false
    }

    fun formatStoryText(
        rawText: String,
        playerName: String,
        playerTitle: String = "",
        metrics: Map<String, Float> = emptyMap()
    ): String {
        if (rawText.isBlank()) return rawText
        val defaultName = GameContent.manifest.defaultHeroName.ifBlank { "Hero" }
        val effectiveName = playerName.ifBlank { defaultName }
        val effectiveTitle = playerTitle.ifBlank { GameContent.manifest.defaultHeroTitle.ifBlank { "Adventurer" } }

        var result = rawText
            .replace("{hero}", effectiveName)
            .replace("{heroName}", effectiveName)
            .replace("{player}", effectiveName)
            .replace("{playerName}", effectiveName)
            .replace("{heroTitle}", effectiveTitle)
            .replace("{title}", effectiveTitle)

        // Possessive placeholders
        result = result
            .replace("{hero}'s", "$effectiveName's")
            .replace("{heroName}'s", "$effectiveName's")
            .replace("{player}'s", "$effectiveName's")

        // Dynamic metric token replacement if metrics are present
        if (metrics.isNotEmpty() || GameContent.metrics.isNotEmpty()) {
            for ((metricId, metricVal) in metrics) {
                if (result.contains("{$metricId}")) {
                    result = result.replace("{$metricId}", "${metricVal.toInt()}%")
                }
            }
            for (metric in GameContent.metrics.values) {
                if (result.contains("{${metric.id}}")) {
                    val valNum = metrics[metric.id] ?: metric.initialValue
                    result = result.replace("{${metric.id}}", "${valNum.toInt()}%")
                }
            }
        }

        // If player has a custom name different from defaultName, replace any leftover defaultName references
        if (!effectiveName.equals(defaultName, ignoreCase = true) && !defaultName.contains("{")) {
            result = result.replace(Regex("\\b${Regex.escape(defaultName)}'s\\b", RegexOption.IGNORE_CASE), "$effectiveName's")
            result = result.replace(Regex("\\b${Regex.escape(defaultName)}'\\b", RegexOption.IGNORE_CASE), "$effectiveName'")
            result = result.replace(Regex("\\b${Regex.escape(defaultName)}\\b", RegexOption.IGNORE_CASE), effectiveName)
        }

        return result
    }

    fun formatNodeForPlayer(
        node: DialogueNode,
        player: PlayerCustomization,
        metrics: Map<String, Float> = emptyMap()
    ): DialogueNode {
        val defaultHeroName = GameContent.manifest.defaultHeroName.ifBlank { "Hero" }
        val effectiveName = player.name.ifBlank { defaultHeroName }
        val effectiveTitle = player.title.ifBlank { GameContent.manifest.defaultHeroTitle.ifBlank { "Adventurer" } }

        val isHero = node.speaker.id in setOf("hero", "player") ||
                node.speaker == GameContent.heroCharacter?.speaker ||
                node.speaker.name.equals(defaultHeroName, ignoreCase = true)

        val formattedSpeaker = if (isHero) {
            node.speaker.copy(
                name = effectiveName,
                title = if (player.title.isNotBlank()) player.title else (if (node.speaker.title.isBlank()) effectiveTitle else node.speaker.title)
            )
        } else {
            node.speaker
        }

        val formattedText = formatStoryText(node.text, effectiveName, effectiveTitle, metrics)
        val formattedRevisit = node.revisitText?.let { formatStoryText(it, effectiveName, effectiveTitle, metrics) }
        val formattedChoices = node.choices.map { choice ->
            choice.copy(
                text = formatStoryText(choice.text, effectiveName, effectiveTitle, metrics),
                voiceKeywords = choice.voiceKeywords.map { formatStoryText(it, effectiveName, effectiveTitle, metrics) }
            )
        }

        return node.copy(
            speaker = formattedSpeaker,
            text = formattedText,
            revisitText = formattedRevisit,
            choices = formattedChoices
        )
    }

    private fun eligibleChoices(node: DialogueNode): List<DialogueChoice> =
        node.choices.filter { choice ->
            val notCompleted = !isChoiceCompleted(choice, _state.value.narrativeFlags, _state.value.defeatedEncounters)
            val notLocked = !isChoiceLocked(choice, node, _state.value.narrativeFlags)
            notCompleted && notLocked
        }

    private fun effectiveDialogueChoices(node: DialogueNode): List<DialogueChoice> {
        val eligible = eligibleChoices(node)
        return if (node.choices.isNotEmpty() && eligible.size <= 1) emptyList() else node.choices
    }

    fun canAdvanceDialogue(): Boolean {
        val node = _state.value.currentNode
        if (effectiveDialogueChoices(node).isNotEmpty()) return false
        if (node.triggerBattleEncounterId != null) return false
        if (node.triggerMiniGameId != null) return true
        if (node.choices.isNotEmpty()) return true
        if (node.nextNodeId != null) return StoryScript.ALL_NODES.containsKey(node.nextNodeId)
        // Terminal node (no choices, no next): the playthrough is complete or a dead end.
        return false
    }

    fun schedulePocketModeAutoAdvance(node: DialogueNode) {
        cancelPendingAutoAdvance()
        if (!combatNarrator.isEyesFreeMode.value || !canAdvanceDialogue()) return
        pendingAutoAdvanceJob = activeScope.launch {
            delay(1500)
            if (combatNarrator.isEyesFreeMode.value &&
                _state.value.currentNode.id == node.id &&
                _state.value.gameScreen == GameScreen.STORY_EXPLORATION &&
                canAdvanceDialogue()
            ) {
                advanceDialogue()
            }
        }
    }

    private var pendingStoryModeJob: Job? = null
    private var watchdogStoryModeJob: Job? = null

    fun cancelPendingStoryMode() {
        pendingStoryModeJob?.cancel()
        pendingStoryModeJob = null
        watchdogStoryModeJob?.cancel()
        watchdogStoryModeJob = null
    }

    fun isBossEncounter(encounterId: String): Boolean {
        val enc = GameContent.encounterById(encounterId) ?: return false
        return enc.enemies.any { it.isBoss }
    }

    fun pickStoryModeChoice(node: DialogueNode): DialogueChoice? {
        val eligible = eligibleChoices(node)
        if (eligible.isEmpty()) return null

        // Uncompleted sub-story choices in hubs and dialogue trees (explore all narrative paths randomly)
        val uncompleted = eligible.filter { choice ->
            choice.completionFlag != null &&
            _state.value.narrativeFlags[choice.completionFlag] != true
        }
        if (uncompleted.isNotEmpty()) {
            return uncompleted.random()
        }

        // Randomly select among all eligible choices
        return eligible.random()
    }

    fun setPureStoryMode(enabled: Boolean) {
        _state.value = _state.value.copy(
            isPureStoryMode = enabled,
            isStoryAutoPlayPaused = false
        )
        if (enabled) {
            combatNarrator.setNarrationEnabled(true)
            if (_state.value.gameScreen == GameScreen.STORY_EXPLORATION && !combatNarrator.isSpeaking.value) {
                scheduleStoryModeNextStep(_state.value.currentNode, delayMs = 600L)
            }
        } else {
            cancelPendingStoryMode()
        }
        persistCurrentState()
    }

    fun toggleAutoPlay(): Boolean {
        val willEnable = !_state.value.isPureStoryMode
        setPureStoryMode(willEnable)
        val msg = if (willEnable) "Auto play enabled." else "Auto play disabled."
        combatNarrator.speak(msg, force = true)
        return willEnable
    }

    fun toggleStoryAutoPlayPause(): Boolean {
        val newPaused = !_state.value.isStoryAutoPlayPaused
        _state.value = _state.value.copy(isStoryAutoPlayPaused = newPaused)
        if (newPaused) {
            cancelPendingStoryMode()
            combatNarrator.speak("Story mode paused.", force = true)
        } else {
            combatNarrator.speak("Story mode resumed.", force = true)
            if (_state.value.gameScreen == GameScreen.STORY_EXPLORATION && !combatNarrator.isSpeaking.value) {
                scheduleStoryModeNextStep(_state.value.currentNode, delayMs = 600L)
            }
        }
        return newPaused
    }

    fun startPureStoryMode(fresh: Boolean = false, campaignTrackId: String? = null) {
        cancelPendingAutoAdvance()
        cancelPendingStoryMode()
        speechManager.cancel()
        speechManager.stopListening()
        combatNarrator.stop()
        combatNarrator.setNarrationEnabled(true)

        val storySlot = SaveManager.STORY_MODE_SLOT
        val hasStorySave = saveManager.hasSave(storySlot)
        val existingStorySave = if (hasStorySave) saveManager.load(storySlot) else null
        val isCompleted = existingStorySave?.narrativeFlags?.get(GAME_COMPLETED_FLAG) == true

        _state.value = _state.value.copy(
            isPureStoryMode = true,
            isStoryAutoPlayPaused = false
        )

        if (!fresh && hasStorySave && existingStorySave != null && !isCompleted) {
            continueGame(storySlot)
            _state.value = _state.value.copy(
                isPureStoryMode = true,
                isStoryAutoPlayPaused = false
            )
            scheduleStoryModeNextStep(_state.value.currentNode, delayMs = 1200L)
        } else {
            saveManager.deleteSave(storySlot)
            val manifest = GameContent.manifest
            val defaultName = manifest.defaultHeroName.ifBlank { "Hero" }
            val defaultTitle = manifest.defaultHeroTitle.ifBlank { "The Awakened Hero" }
            val resolvedTrackId = campaignTrackId ?: manifest.campaignTracks.firstOrNull()?.id ?: "track_a"
            val defaultPlayer = PlayerCustomization(
                name = defaultName,
                title = defaultTitle,
                campaignTrackId = resolvedTrackId
            )
            saveManager.createInitialSave(defaultPlayer, storySlot)
            saveManager.currentSlot = storySlot
            StoryAssetLoader.activeSlot = storySlot
            _state.value = _state.value.copy(
                gameScreen = GameScreen.CHARACTER_CREATION,
                previousScreen = GameScreen.TITLE,
                isNewGameFlow = true,
                currentSlot = storySlot,
                isPureStoryMode = true,
                isStoryAutoPlayPaused = false,
                player = defaultPlayer
            )
        }
    }

    fun scheduleStoryModeNextStep(node: DialogueNode, delayMs: Long = 1200L) {
        cancelPendingStoryMode()
        if (!_state.value.isPureStoryMode || _state.value.isStoryAutoPlayPaused) return
        if (_state.value.gameScreen != GameScreen.STORY_EXPLORATION) return

        pendingStoryModeJob = activeScope.launch {
            delay(delayMs)
            if (!_state.value.isPureStoryMode || _state.value.isStoryAutoPlayPaused) return@launch
            if (_state.value.currentNode.id != node.id || _state.value.gameScreen != GameScreen.STORY_EXPLORATION) return@launch

            val current = _state.value.currentNode
            if (current.triggerBattleEncounterId != null) {
                triggerEncounter(current.triggerBattleEncounterId)
                return@launch
            }

            val effective = effectiveDialogueChoices(current)
            if (effective.isNotEmpty()) {
                val chosen = pickStoryModeChoice(current)
                if (chosen != null) {
                    selectChoice(chosen)
                }
            } else {
                advanceDialogueInternal(suppressNarration = false)
            }
        }
    }

    init {
        // Observe Screenless Pocket Mode toggles: cancel or start auto-advance reactively
        activeScope.launch {
            combatNarrator.isEyesFreeMode.collect { isEyesFree ->
                if (!isEyesFree) {
                    cancelPendingAutoAdvance()
                } else if (_state.value.gameScreen == GameScreen.STORY_EXPLORATION && canAdvanceDialogue() && !combatNarrator.isSpeaking.value) {
                    schedulePocketModeAutoAdvance(_state.value.currentNode)
                }
            }
        }

        // Load persistent game save on boot with multi-slot support
        val allSlots = saveManager.getAllSlotInfos()
        val initialSlot = allSlots
            .filter { !it.isEmpty }
            .maxByOrNull { it.summary?.timestamp ?: 0L }
            ?.slotIndex ?: SaveManager.DEFAULT_SLOT
        saveManager.currentSlot = initialSlot
        val existingSave = saveManager.load(initialSlot)
        val globalSettings = saveManager.loadGlobalSettings()
        val lang = if (globalSettings.isLanguageSetupCompleted) {
            globalSettings.selectedLanguage.ifBlank { "en" }
        } else {
            existingSave?.selectedLanguage?.ifBlank { "en" } ?: "en"
        }
        val reg = if (globalSettings.isLanguageSetupCompleted) {
            globalSettings.selectedRegion
        } else {
            existingSave?.selectedRegion ?: ""
        }
        com.voicerpg.engine.localization.TranslationManager.setLanguageAndRegion(lang, reg)
        speechManager.setSelectedLocale(lang)
        speechManager.setSelectedRegion(reg)
        combatNarrator.setSelectedLanguageAndRegion(lang, reg)
        com.voicerpg.engine.engine.IntentParser.currentLocale = lang

        val initialGameScreen = if (!globalSettings.isLanguageSetupCompleted) {
            GameScreen.LANGUAGE_SELECTION
        } else {
            GameScreen.TITLE
        }

        if (existingSave != null) {
            val restoredScene = StoryScript.ALL_SCENES[existingSave.currentSceneId] ?: StoryScript.INITIAL_SCENE
            val rawRestoredNode = StoryScript.ALL_NODES[existingSave.currentNodeId] ?: StoryScript.INITIAL_NODE
            val restoredNode = resolveEffectiveHubNode(rawRestoredNode, existingSave.narrativeFlags)

            val summary = saveManager.getSlotSummary(initialSlot)

            val restoredHistory = if (existingSave.recentDialogueLog.isNotEmpty()) {
                existingSave.recentDialogueLog
            } else {
                listOf(
                    DialogueLogEntry(
                        speakerId = restoredNode.speaker.id,
                        speakerName = restoredNode.speaker.name,
                        speakerTitle = restoredNode.speaker.title,
                        text = restoredNode.text,
                        sceneName = restoredScene.name,
                        isNarrator = restoredNode.speaker.isNarrator
                    )
                )
            }

            _state.value = StoryState(
                currentScene = restoredScene,
                currentNode = formatNodeForPlayer(restoredNode, existingSave.player),
                gameScreen = initialGameScreen,
                player = existingSave.player,
                decisionsMade = existingSave.decisionsMade,
                narrativeFlags = existingSave.narrativeFlags,
                defeatedEncounters = existingSave.defeatedEncounters,
                achievements = existingSave.achievements,
                partyStats = existingSave.partyStats,
                hasExistingSave = true,
                saveSummary = summary,
                currentSlot = initialSlot,
                saveSlots = allSlots,
                dialogueHistory = restoredHistory,
                selectedLanguage = lang,
                selectedRegion = reg,
                showFirstLaunchLanguagePrompt = !globalSettings.isLanguageSetupCompleted
            )
            combatNarrator.setEyesFreeMode(existingSave.isEyesFreeMode, lockGuard = false)
            combatNarrator.setPocketGuardEnabled(existingSave.isPocketGuardEnabled)
            combatNarrator.setCombatNarrationEnabled(existingSave.isCombatNarrationEnabled)
            combatNarrator.setNarrationEnabled(existingSave.isNarrationEnabled)
            combatNarrator.setReadChoicesEnabled(existingSave.isReadChoicesEnabled)
            combatNarrator.setAutoReadHubChoices(existingSave.isAutoReadHubChoices)
            combatNarrator.setSpeechRate(existingSave.speechRate)
            combatNarrator.setCharacterPitchEnabled(existingSave.isCharacterPitchEnabled)
            combatNarrator.setSpeakerAttributionEnabled(existingSave.isSpeakerAttributionEnabled)
            combatNarrator.setVoiceAssignments(existingSave.voiceAssignments)
            speechManager.setAutoListen(existingSave.isAutoListen)
            speechManager.setChimeMuted(existingSave.isChimeMuted)
            musicManager?.setMusicEnabled(existingSave.isMusicEnabled)
            musicManager?.setVolume(existingSave.musicVolume)
            musicManager?.playTrack(com.voicerpg.engine.audio.MusicManager.TRACK_EXPLORATION)
        } else {
            // First time player: start at Language Selection or Title Screen
            musicManager?.playTrack(com.voicerpg.engine.audio.MusicManager.TRACK_EXPLORATION)
            _state.value = StoryState(
                gameScreen = initialGameScreen,
                hasExistingSave = false,
                saveSummary = null,
                currentSlot = initialSlot,
                saveSlots = allSlots,
                selectedLanguage = lang,
                selectedRegion = reg,
                showFirstLaunchLanguagePrompt = !globalSettings.isLanguageSetupCompleted
            )
        }
    }

    /**
     * Resumes a game save from the Title Screen. Defaults to the active slot.
     */
    fun continueGame(slot: Int? = null, startInAutoPlay: Boolean = false) {
        val targetSlot = if (slot == SaveManager.STORY_MODE_SLOT) SaveManager.STORY_MODE_SLOT else (slot ?: _state.value.currentSlot).coerceIn(1, SaveManager.MAX_SLOTS)
        saveManager.currentSlot = targetSlot
        StoryAssetLoader.activeSlot = targetSlot
        val existingSave = saveManager.load(targetSlot) ?: return
        if (startInAutoPlay && !existingSave.isAutoPlayEnabled) {
            saveManager.save(existingSave.copy(isAutoPlayEnabled = true), targetSlot)
        }
        val restoredScene = StoryScript.ALL_SCENES[existingSave.currentSceneId] ?: StoryScript.INITIAL_SCENE
        val rawRestoredNode = StoryScript.ALL_NODES[existingSave.currentNodeId] ?: StoryScript.INITIAL_NODE
        val baseRestoredNode = resolveEffectiveHubNode(rawRestoredNode, existingSave.narrativeFlags)
        val restoredVisited = existingSave.visitedNodeIds.toSet() + baseRestoredNode.id
        val isRestoredRevisit = baseRestoredNode.id in existingSave.visitedNodeIds
        val restoredActiveText = if (isRestoredRevisit) {
            baseRestoredNode.revisitText?.ifBlank { null }
                ?: if (baseRestoredNode.choices.size > 1) "What will you do next?" else baseRestoredNode.text
        } else {
            baseRestoredNode.text
        }
        val restoredNode = if (restoredActiveText != baseRestoredNode.text) {
            baseRestoredNode.copy(text = restoredActiveText)
        } else {
            baseRestoredNode
        }

        val restoredHistory = if (existingSave.recentDialogueLog.isNotEmpty()) {
            existingSave.recentDialogueLog
        } else if (_state.value.dialogueHistory.isNotEmpty()) {
            _state.value.dialogueHistory
        } else {
            listOf(
                DialogueLogEntry(
                    speakerId = restoredNode.speaker.id,
                    speakerName = restoredNode.speaker.name,
                    speakerTitle = restoredNode.speaker.title,
                    text = restoredNode.text,
                    sceneName = restoredScene.name,
                    isNarrator = restoredNode.speaker.isNarrator
                )
            )
        }

        val isStoryMode = startInAutoPlay || existingSave.isAutoPlayEnabled || (targetSlot == SaveManager.STORY_MODE_SLOT)
        _state.value = _state.value.copy(
            currentScene = restoredScene,
            currentNode = formatNodeForPlayer(restoredNode, existingSave.player, existingSave.dynamicMetrics),
            gameScreen = GameScreen.STORY_EXPLORATION,
            previousScreen = null,
            player = existingSave.player,
            decisionsMade = existingSave.decisionsMade,
            narrativeFlags = existingSave.narrativeFlags,
            visitedNodeIds = restoredVisited,
            defeatedEncounters = existingSave.defeatedEncounters,
            achievements = existingSave.achievements,
            partyStats = existingSave.partyStats,
            currentSlot = targetSlot,
            hasExistingSave = true,
            saveSummary = saveManager.getSlotSummary(targetSlot),
            saveSlots = saveManager.getAllSlotInfos(),
            dialogueHistory = restoredHistory,
            isPureStoryMode = isStoryMode,
            isStoryAutoPlayPaused = false,
            dynamicMetrics = existingSave.dynamicMetrics
        )
        combatNarrator.setEyesFreeMode(existingSave.isEyesFreeMode, lockGuard = false)
        combatNarrator.setPocketGuardEnabled(existingSave.isPocketGuardEnabled)
        combatNarrator.setCombatNarrationEnabled(existingSave.isCombatNarrationEnabled)
        combatNarrator.setNarrationEnabled(existingSave.isNarrationEnabled)
        combatNarrator.setReadChoicesEnabled(existingSave.isReadChoicesEnabled)
        combatNarrator.setAutoReadHubChoices(existingSave.isAutoReadHubChoices)
        combatNarrator.setSpeechRate(existingSave.speechRate)
        combatNarrator.setCharacterPitchEnabled(existingSave.isCharacterPitchEnabled)
        combatNarrator.setSpeakerAttributionEnabled(existingSave.isSpeakerAttributionEnabled)
        combatNarrator.setVoiceAssignments(existingSave.voiceAssignments)
        speechManager.setAutoListen(existingSave.isAutoListen)
        speechManager.setChimeMuted(existingSave.isChimeMuted)
        musicManager?.setMusicEnabled(existingSave.isMusicEnabled)
        musicManager?.setVolume(existingSave.musicVolume)
        musicManager?.playTrack(restoredScene.musicAsset)
        narrateCurrentNode()
        if (isStoryMode) {
            scheduleStoryModeNextStep(_state.value.currentNode, delayMs = 1200L)
        }
    }

    /**
     * Selects an active save slot and refreshes the preview summary.
     */
    fun selectSlot(slot: Int) {
        val validSlot = saveManager.sanitizeSlot(slot)
        saveManager.currentSlot = validSlot
        StoryAssetLoader.activeSlot = validSlot
        val summary = saveManager.getSlotSummary(validSlot)
        _state.value = _state.value.copy(
            currentSlot = validSlot,
            hasExistingSave = summary != null,
            saveSummary = summary,
            saveSlots = saveManager.getAllSlotInfos()
        )
    }

    /**
     * Deletes the specified save slot and updates slot listings.
     */
    fun deleteSlot(slot: Int) {
        val validSlot = saveManager.sanitizeSlot(slot)
        saveManager.deleteSave(validSlot)
        StoryAssetLoader.clearSlot(validSlot)
        val allSlots = saveManager.getAllSlotInfos()
        val activeSlot = _state.value.currentSlot
        val summary = saveManager.getSlotSummary(activeSlot)
        _state.value = _state.value.copy(
            saveSlots = allSlots,
            hasExistingSave = summary != null,
            saveSummary = summary
        )
    }

    /**
     * Refreshes save slots metadata from disk.
     */
    fun refreshSaveSlots() {
        val allSlots = saveManager.getAllSlotInfos()
        val activeSlot = _state.value.currentSlot
        val summary = saveManager.getSlotSummary(activeSlot)
        _state.value = _state.value.copy(
            saveSlots = allSlots,
            hasExistingSave = summary != null,
            saveSummary = summary
        )
    }

    /**
     * Starts the new game wizard flow from the Title Screen, optionally targeting a slot.
     */
    fun startNewGameFlow(slot: Int? = null) {
        val targetSlot = if (slot != null) saveManager.sanitizeSlot(slot) else _state.value.currentSlot
        selectSlot(targetSlot)
        cancelPendingAutoAdvance()
        combatNarrator.stop()
        speechManager.cancel()
        val manifest = GameContent.manifest
        val defaultName = manifest.defaultHeroName.ifBlank { "Hero" }
        val defaultTitle = manifest.defaultHeroTitle.ifBlank { "The Awakened Hero" }
        StoryAssetLoader.activeSlot = targetSlot
        _state.value = _state.value.copy(
            gameScreen = GameScreen.AUDIO_SETUP,
            previousScreen = GameScreen.TITLE,
            isNewGameFlow = true,
            currentSlot = targetSlot,
            isPureStoryMode = (targetSlot == SaveManager.STORY_MODE_SLOT),
            player = PlayerCustomization(name = defaultName, title = defaultTitle)
        )
    }

    /**
     * Opens Screen 1: Language Selection (e.g. from Options, Audio Setup, or first boot).
     */
    fun openLanguageSelection(fromOptions: Boolean = false) {
        val prev = _state.value.gameScreen
        _state.value = _state.value.copy(
            gameScreen = GameScreen.LANGUAGE_SELECTION,
            previousScreen = prev,
            returnToOptionsOnConfirm = fromOptions,
            pendingSelectedLanguage = com.voicerpg.engine.localization.TranslationManager.currentLanguage.value,
            pendingSelectedRegion = com.voicerpg.engine.localization.TranslationManager.currentRegion.value
        )
    }

    /**
     * Advances from Screen 1 (Language selected) to Screen 2 (Dialect / Accent selection).
     */
    fun onLanguageChosen(languageCode: String) {
        val clean = languageCode.lowercase().trim().take(2)
        _state.value = _state.value.copy(
            gameScreen = GameScreen.DIALECT_SELECTION,
            pendingSelectedLanguage = clean,
            pendingSelectedRegion = ""
        )
    }

    /**
     * Returns from Screen 2 (Dialect) back to Screen 1 (Language).
     */
    fun returnToLanguageSelection() {
        _state.value = _state.value.copy(
            gameScreen = GameScreen.LANGUAGE_SELECTION
        )
    }

    /**
     * Cancels language selection and returns to previous screen without applying changes.
     */
    fun cancelLanguageSelection() {
        val dest = _state.value.previousScreen ?: GameScreen.TITLE
        _state.value = _state.value.copy(
            gameScreen = dest,
            previousScreen = null,
            returnToOptionsOnConfirm = false
        )
    }

    /**
     * Called when user confirms dialect on Screen 2. Checks if model is downloaded;
     * if not, routes to LANGUAGE_DOWNLOAD_STANDBY gatekeeper screen.
     */
    fun onDialectConfirmed(languageCode: String, regionCode: String) {
        val cleanLang = languageCode.lowercase().trim().take(2)
        val cleanReg = regionCode.uppercase().trim()
        _state.value = _state.value.copy(
            pendingSelectedLanguage = cleanLang,
            pendingSelectedRegion = cleanReg
        )

        viewModelScope.launch {
            val isInstalled = com.voicerpg.engine.localization.TranslationManager.isModelInstalled(cleanLang)
            if (isInstalled) {
                applyLanguageAndFinish(cleanLang, cleanReg)
            } else {
                _state.value = _state.value.copy(
                    gameScreen = GameScreen.LANGUAGE_DOWNLOAD_STANDBY
                )
                com.voicerpg.engine.localization.TranslationManager.setLanguageAndRegion(cleanLang, cleanReg)
            }
        }
    }

    fun applyLanguageAndFinish(languageCode: String, regionCode: String) {
        com.voicerpg.engine.localization.TranslationManager.setLanguageAndRegion(languageCode, regionCode)
        combatNarrator.setSelectedLanguageAndRegion(languageCode, regionCode)
        speechManager.setSelectedLocale(languageCode)
        speechManager.setSelectedRegion(regionCode)

        val dest = _state.value.previousScreen ?: GameScreen.TITLE
        _state.value = _state.value.copy(
            gameScreen = dest,
            previousScreen = null
        )
    }

    /**
     * Saves current campaign progress and returns to the Title Screen.
     */
    fun returnToTitle() {
        cancelPendingAutoAdvance()
        cancelPendingStoryMode()
        combatNarrator.stop()
        speechManager.cancel()
        persistCurrentState()

        // If returning from Story Mode, restore active slot to latest campaign slot (1..3)
        val allSlots = saveManager.getAllSlotInfos()
        val latestCampaignSlot = allSlots
            .filter { !it.isEmpty }
            .maxByOrNull { it.summary?.timestamp ?: 0L }
            ?.slotIndex ?: SaveManager.DEFAULT_SLOT

        val activeSlot = if (_state.value.currentSlot == SaveManager.STORY_MODE_SLOT) {
            latestCampaignSlot
        } else {
            _state.value.currentSlot
        }
        saveManager.currentSlot = activeSlot
        val summary = saveManager.getSlotSummary(activeSlot)
        _state.value = _state.value.copy(
            gameScreen = GameScreen.TITLE,
            previousScreen = null,
            hasExistingSave = summary != null,
            saveSummary = summary,
            currentSlot = activeSlot,
            saveSlots = allSlots,
            isPureStoryMode = false,
            isStoryAutoPlayPaused = false
        )
        musicManager?.playTrack(com.voicerpg.engine.audio.MusicManager.TRACK_EXPLORATION)
    }

    fun getStoryModeSaveSummary(): SaveSummary? = saveManager.getSlotSummary(SaveManager.STORY_MODE_SLOT)

    /**
     * Proceeds from initial Audio Setup to Character Creation.
     */
    fun proceedToCharacterCreation() {
        _state.value = _state.value.copy(
            gameScreen = GameScreen.CHARACTER_CREATION,
            previousScreen = GameScreen.AUDIO_SETUP
        )
    }

    /**
     * Initializes a fresh game from Character Creation.
     */
    fun startNewGame(customization: PlayerCustomization, slot: Int? = null) {
        cancelPendingAutoAdvance()
        val targetSlot = if (slot == SaveManager.STORY_MODE_SLOT) SaveManager.STORY_MODE_SLOT else (slot ?: _state.value.currentSlot).coerceIn(1, SaveManager.MAX_SLOTS)
        saveManager.currentSlot = targetSlot
        StoryAssetLoader.activeSlot = targetSlot
        val initialSave = saveManager.createInitialSave(customization, targetSlot)
        val initialNode = formatNodeForPlayer(StoryScript.INITIAL_NODE, customization)
        val initialScene = StoryScript.INITIAL_SCENE
        val initialEntry = DialogueLogEntry(
            speakerId = initialNode.speaker.id,
            speakerName = initialNode.speaker.name,
            speakerTitle = initialNode.speaker.title,
            text = initialNode.text,
            sceneName = initialScene.name,
            isNarrator = initialNode.speaker.isNarrator
        )
        val isStoryMode = targetSlot == SaveManager.STORY_MODE_SLOT
        _state.value = StoryState(
            currentScene = initialScene,
            currentNode = initialNode,
            gameScreen = GameScreen.STORY_EXPLORATION,
            player = customization,
            decisionsMade = emptyList(),
            narrativeFlags = emptyMap(),
            visitedNodeIds = setOf(initialNode.id),
            defeatedEncounters = emptyList(),
            achievements = initialSave.achievements,
            partyStats = initialSave.partyStats,
            hasExistingSave = true,
            saveSummary = saveManager.getSlotSummary(targetSlot),
            currentSlot = targetSlot,
            saveSlots = saveManager.getAllSlotInfos(),
            dialogueHistory = listOf(initialEntry),
            isPureStoryMode = isStoryMode,
            isStoryAutoPlayPaused = false,
            dynamicMetrics = GameContent.metrics.values.associate { it.id to it.initialValue }
        )
        musicManager?.playTrack(initialScene.musicAsset)
        persistCurrentState()
        narrateCurrentNode()
    }

    fun resetGame(slot: Int? = null) {
        cancelPendingAutoAdvance()
        val targetSlot = if (slot != null) saveManager.sanitizeSlot(slot) else _state.value.currentSlot
        saveManager.deleteSave(targetSlot)
        StoryAssetLoader.clearSlot(targetSlot)
        val allSlots = saveManager.getAllSlotInfos()
        val summary = saveManager.getSlotSummary(targetSlot)
        _state.value = _state.value.copy(
            gameScreen = GameScreen.TITLE,
            hasExistingSave = summary != null,
            saveSummary = summary,
            saveSlots = allSlots
        )
    }

    /**
     * Debug-only helper: instantly jumps to a chapter's intro node. Chapter targets are
     * derived from scene data files. Only exposed through the debug-build Options dialog
     * (see BuildConfig.DEBUG_WARP_MENU).
     */
    fun debugWarpToChapter(introNodeId: String) {
        val introNode = StoryScript.ALL_NODES[introNodeId] ?: return
        cancelPendingAutoAdvance()
        combatNarrator.stop()
        speechManager.cancel()

        val introScene = StoryScript.ALL_SCENES.values.firstOrNull { it.initialNodeId == introNodeId }
            ?: _state.value.currentScene

        _state.value = _state.value.copy(
            currentScene = introScene,
            currentNode = introNode,
            gameScreen = GameScreen.STORY_EXPLORATION,
            activeEncounter = null
        )
        musicManager?.playTrack(introScene.musicAsset)
        applyNodeTransition(introNode)
    }

    fun advanceDialogue() {
        if (_state.value.isFastForwarding) {
            stopFastForward()
            return
        }
        advanceDialogueInternal(suppressNarration = false)
    }

    private fun advanceDialogueInternal(suppressNarration: Boolean = false) {
        cancelPendingAutoAdvance()
        cancelPendingStoryMode()
        if (!suppressNarration) {
            combatNarrator.stop()
            speechManager.cancel()
        }
        val node = _state.value.currentNode
        if (effectiveDialogueChoices(node).isNotEmpty()) {
            return
        }

        if (node.choices.isNotEmpty()) {
            val sole = eligibleChoices(node).firstOrNull()
            if (sole != null) {
                val nextNode = StoryScript.ALL_NODES[sole.nextNodeId]
                if (nextNode != null) {
                    val updatedDecisions = _state.value.decisionsMade + sole.id
                    _state.value = _state.value.copy(decisionsMade = updatedDecisions)
                    applyNodeTransition(nextNode, suppressNarration = suppressNarration)
                }
            }
            return
        }

        if (node.triggerBattleEncounterId != null) {
            triggerEncounter(node.triggerBattleEncounterId)
            return
        }

        if (node.triggerMiniGameId != null) {
            triggerMiniGame(node.triggerMiniGameId)
            return
        }

        val nextId = node.nextNodeId
        if (nextId != null) {
            val nextNode = StoryScript.ALL_NODES[nextId]
            if (nextNode != null) {
                applyNodeTransition(nextNode, suppressNarration = suppressNarration)
            }
        } else if (node.choices.isEmpty()) {
            // Terminal node: the engine-convention "game_completed" flag ends the playthrough.
            if (node.setFlagOnEnter == GAME_COMPLETED_FLAG) {
                resetGame()
                return
            }
        }
    }

    fun selectChoice(choice: DialogueChoice) {
        if (_state.value.isFastForwarding) {
            stopFastForward()
        }
        cancelPendingAutoAdvance()
        cancelPendingStoryMode()
        if (isChoiceCompleted(choice, _state.value.narrativeFlags, _state.value.defeatedEncounters)) {
            combatNarrator.speak("That objective has already been completed. Please select a remaining task.", force = true)
            return
        }
        if (isChoiceLocked(choice, _state.value.currentNode, _state.value.narrativeFlags)) {
            combatNarrator.speak("That option is locked. Please explore the remaining dialogue options first.", force = true)
            return
        }

        combatNarrator.stop()
        speechManager.cancel()
        val nextNode = StoryScript.ALL_NODES[choice.nextNodeId]
        if (nextNode != null) {
            val updatedDecisions = _state.value.decisionsMade + choice.id
            var currentMetrics = _state.value.dynamicMetrics
            if (choice.metricDeltas.isNotEmpty() && GameContent.metrics.isNotEmpty()) {
                val updated = currentMetrics.toMutableMap()
                for ((metricId, delta) in choice.metricDeltas) {
                    val def = GameContent.metrics[metricId]
                    val currVal = updated[metricId] ?: def?.initialValue ?: 0f
                    val clamped = if (def != null) (currVal + delta).coerceIn(def.minValue, def.maxValue) else currVal + delta
                    updated[metricId] = clamped
                }
                currentMetrics = updated
            }
            if (choice.completionFlag != null && _state.value.narrativeFlags[choice.completionFlag] != true) {
                _state.value = _state.value.copy(
                    narrativeFlags = _state.value.narrativeFlags + (choice.completionFlag to true),
                    decisionsMade = updatedDecisions,
                    dynamicMetrics = currentMetrics
                )
            } else {
                _state.value = _state.value.copy(decisionsMade = updatedDecisions, dynamicMetrics = currentMetrics)
            }
            if (!choice.assignHeroClassId.isNullOrBlank()) {
                assignHeroClass(choice.assignHeroClassId)
            }
            applyNodeTransition(nextNode)
        }
    }

    fun assignHeroClass(classId: String) {
        val heroClass = GameContent.classes.firstOrNull { it.id.equals(classId, ignoreCase = true) } ?: return
        val starterSpells = GameContent.spellsForClass(heroClass.id)

        val updatedPlayer = _state.value.player.copy(
            heroClassId = classId,
            title = heroClass.title.ifBlank { _state.value.player.title },
            voiceAffinityBonusSchool = heroClass.preferredSchool,
            customPortraitAsset = if (_state.value.player.customPortraitAsset?.contains("custom") == true) {
                _state.value.player.customPortraitAsset
            } else {
                heroClass.portraitAsset ?: _state.value.player.customPortraitAsset
            }
        )
        val updatedPartyStats = _state.value.partyStats.map { stat ->
            if (stat.id == "hero") {
                stat.copy(
                    loreClass = heroClass.title,
                    currentHp = heroClass.startingHp,
                    maxHp = heroClass.startingHp,
                    currentMp = heroClass.startingMp,
                    maxMp = heroClass.startingMp,
                    speed = heroClass.startingSpeed,
                    spellIds = starterSpells.map { it.id }
                )
            } else stat
        }
        _state.value = _state.value.copy(
            player = updatedPlayer,
            partyStats = updatedPartyStats
        )
        persistCurrentState()
    }

    private fun applyNodeTransition(newNode: DialogueNode, suppressNarration: Boolean = false) {
        var updatedFlags = if (newNode.setFlagOnEnter != null) {
            _state.value.narrativeFlags + (newNode.setFlagOnEnter to true)
        } else {
            _state.value.narrativeFlags
        }

        var effectiveNode = resolveEffectiveHubNode(newNode, updatedFlags)
        if (effectiveNode.setFlagOnEnter != null && updatedFlags[effectiveNode.setFlagOnEnter] != true) {
            updatedFlags = updatedFlags + (effectiveNode.setFlagOnEnter to true)
        }

        // Procedural incident check on hub return
        if (GameContent.incidents.isNotEmpty()) {
            val isHubNode = effectiveNode.id.endsWith("_hub") || effectiveNode.hubCompletion != null
            val isReturningToHub = isHubNode && (effectiveNode.id in _state.value.visitedNodeIds)

            if (isReturningToHub) {
                if (updatedFlags["incident_just_happened"] == true) {
                    updatedFlags = updatedFlags + ("incident_just_happened" to false)
                } else if (effectiveNode.allowIncidents) {
                    if (kotlin.random.Random.nextFloat() < 0.45f) {
                        val candidateState = _state.value.copy(narrativeFlags = updatedFlags)
                        val chosen = IncidentManager.selectRandomIncident(
                            state = candidateState,
                            availableIncidents = GameContent.incidents.values
                        )
                        if (chosen?.triggerNodeId != null) {
                            val triggerNode = StoryScript.ALL_NODES[chosen.triggerNodeId]
                            if (triggerNode != null && triggerNode.id !in _state.value.visitedNodeIds) {
                                updatedFlags = updatedFlags + ("flag_incident_${chosen.id}_done" to true) + ("incident_just_happened" to true)
                                effectiveNode = triggerNode
                            }
                        }
                    }
                }
            }
        }

        if (!effectiveNode.assignHeroClassId.isNullOrBlank()) {
            assignHeroClass(effectiveNode.assignHeroClassId)
        }

        var updatedPartyStats = _state.value.partyStats

        // Data-driven party rest: nodes declare healPartyOnEnter = "half" | "full".
        when (effectiveNode.healPartyOnEnter?.lowercase()) {
            "full" -> {
                updatedPartyStats = updatedPartyStats.map { member ->
                    if (member.currentHp <= 0) {
                        member.copy(currentHp = member.maxHp / 2, currentMp = member.maxMp / 2)
                    } else {
                        member.copy(currentHp = member.maxHp, currentMp = member.maxMp)
                    }
                }
            }
            "half" -> {
                // Ordinary rest: recover HALF of missing HP/MP (attrition keeps healing
                // and caution meaningful); a fallen comrade is revived at half vitals.
                updatedPartyStats = updatedPartyStats.map { member ->
                    val missingHp = (member.maxHp - member.currentHp).coerceAtLeast(0)
                    val missingMp = (member.maxMp - member.currentMp).coerceAtLeast(0)
                    member.copy(
                        currentHp = if (member.currentHp <= 0) member.maxHp / 2 else (member.currentHp + missingHp / 2).coerceAtMost(member.maxHp),
                        currentMp = if (member.currentMp <= 0) member.maxMp / 2 else (member.currentMp + missingMp / 2).coerceAtMost(member.maxMp)
                    )
                }
            }
        }

        // Data-driven companion recruitment: any character file declaring joinWhenFlag
        // joins the party automatically once that narrative flag is set.
        for (character in GameContent.characters.values) {
            val joinFlag = character.joinWhenFlag
            if (joinFlag != null && updatedFlags[joinFlag] == true &&
                character.combat != null && updatedPartyStats.none { it.id == character.id }
            ) {
                character.toSavedStats()?.let { updatedPartyStats = updatedPartyStats + it }
            }
        }

        // Data-driven spell grants: character files may award spells when flags are set.
        for (character in GameContent.characters.values) {
            for (grant in character.spellGrants) {
                if (grant.flag.isNotBlank() && updatedFlags[grant.flag] == true) {
                    updatedPartyStats = updatedPartyStats.map { member ->
                        if (member.id == character.id) {
                            val missing = grant.spellIds.filterNot { it in member.spellIds }
                            if (missing.isEmpty()) member else member.copy(spellIds = member.spellIds + missing)
                        } else member
                    }
                }
            }
        }

        val sceneIdToUse = effectiveNode.changeSceneId ?: _state.value.currentScene.id
        val targetScene = StoryScript.ALL_SCENES[sceneIdToUse] ?: _state.value.currentScene

        val isRevisit = effectiveNode.id in _state.value.visitedNodeIds
        val activeText = if (isRevisit) {
            effectiveNode.revisitText?.ifBlank { null }
                ?: if (effectiveNode.choices.size > 1) "What will you do next?" else effectiveNode.text
        } else {
            effectiveNode.text
        }
        var currentMetrics = _state.value.dynamicMetrics
        if (GameContent.metrics.isNotEmpty()) {
            val updated = currentMetrics.toMutableMap()
            for ((metricId, overrideVal) in effectiveNode.metricOverrides) {
                val def = GameContent.metrics[metricId]
                val clamped = if (def != null) overrideVal.coerceIn(def.minValue, def.maxValue) else overrideVal
                updated[metricId] = clamped
            }
            for ((metricId, delta) in effectiveNode.metricDeltas) {
                val def = GameContent.metrics[metricId]
                val currVal = updated[metricId] ?: def?.initialValue ?: 0f
                val clamped = if (def != null) (currVal + delta).coerceIn(def.minValue, def.maxValue) else currVal + delta
                updated[metricId] = clamped
            }
            currentMetrics = updated
        }
        val baseNode = if (activeText != effectiveNode.text) {
            effectiveNode.copy(text = activeText)
        } else {
            effectiveNode
        }
        val displayNode = formatNodeForPlayer(baseNode, _state.value.player, currentMetrics)

        val updatedVisited = _state.value.visitedNodeIds + newNode.id + effectiveNode.id

        val entry = DialogueLogEntry(
            speakerId = displayNode.speaker.id,
            speakerName = displayNode.speaker.name,
            speakerTitle = displayNode.speaker.title,
            text = displayNode.text,
            sceneName = targetScene.name,
            isNarrator = displayNode.speaker.isNarrator
        )
        val updatedHistory = (_state.value.dialogueHistory + entry).takeLast(100)

        _state.value = _state.value.copy(
            currentScene = targetScene,
            currentNode = displayNode,
            narrativeFlags = updatedFlags,
            partyStats = updatedPartyStats,
            dialogueHistory = updatedHistory,
            visitedNodeIds = updatedVisited,
            dynamicMetrics = currentMetrics
        )

        musicManager?.playTrack(targetScene.musicAsset)
        persistCurrentState()
        if (!suppressNarration) {
            narrateCurrentNode()
        }
    }

    private fun resolveEffectiveHubNode(node: DialogueNode, flags: Map<String, Boolean>): DialogueNode {
        // Data-driven hub auto-redirect: when every required flag is set, a hub with a
        // hubCompletion block reroutes to the declared completion node (choice-elimination
        // endgame). See StoryScene/story JSON files for the schema.
        var effective = node
        var guard = 0
        while (guard < 16) {
            val completion = effective.hubCompletion
            if (completion == null || completion.requiredFlags.isEmpty()) break
            if (completion.requiredFlags.all { flags[it] == true }) {
                // If the hub still has an eligible choice that is not yet completed (e.g. an unlocked battle),
                // do not redirect immediately; let the choice be presented and chosen.
                val hasPendingChoice = effective.choices.any { choice ->
                    val notCompleted = !isChoiceCompleted(choice, flags, _state.value.defeatedEncounters)
                    val notLocked = !isChoiceLocked(choice, effective, flags)
                    notCompleted && notLocked && choice.nextNodeId != completion.redirectToNodeId
                }
                if (hasPendingChoice) {
                    break
                }
                val redirect = StoryScript.ALL_NODES[completion.redirectToNodeId]
                if (redirect == null || redirect.id == effective.id) break
                effective = redirect
            } else {
                break
            }
            guard++
        }
        return effective
    }

    var onOpenOptions: (() -> Unit)? = null
    var onCloseOptions: (() -> Unit)? = null

    fun triggerEncounter(encounterId: String) {
        cancelPendingAutoAdvance()
        cancelPendingStoryMode()
        // Data-driven encounter dispatch: the engine only knows encounter IDs as declared
        // in the game's encounter data files, never hardcoded story battles.
        val encounter = GameContent.encounterById(encounterId) ?: return
        speechManager.cancel()
        combatNarrator.stop()

        _state.value = _state.value.copy(
            gameScreen = GameScreen.COMBAT_ARENA,
            activeEncounter = encounter
        )
    }

    fun onCombatVictory() {
        cancelPendingAutoAdvance()
        cancelPendingStoryMode()
        val lastNode = _state.value.currentNode
        val encounterId = lastNode.triggerBattleEncounterId ?: _state.value.activeEncounter?.id ?: "unknown"

        val updatedDefeated = if (encounterId !in _state.value.defeatedEncounters) {
            _state.value.defeatedEncounters + encounterId
        } else {
            _state.value.defeatedEncounters
        }

        // Generic post-battle routing: return to the encounter-triggering node's declared
        // next node. A game may instead define victory nodes purely via the node graph.
        val defaultTarget = StoryScript.ALL_NODES[lastNode.nextNodeId ?: lastNode.id]
        val targetNode = defaultTarget ?: lastNode

        _state.value = _state.value.copy(
            gameScreen = GameScreen.STORY_EXPLORATION,
            activeEncounter = null,
            defeatedEncounters = updatedDefeated
        )

        applyNodeTransition(targetNode)
    }

    fun updatePartyStatsFromCombat(updatedParty: List<PartyMember>) {
        val mappedStats = updatedParty.map { member ->
            val existing = _state.value.partyStats.firstOrNull { it.id == member.id }
            val mergedSpells = (member.spells.map { it.id } + (existing?.spellIds ?: emptyList())).distinct()
            SavedCharacterStats(
                id = member.id,
                name = member.name,
                loreClass = member.loreClass,
                currentHp = member.currentHp,
                maxHp = member.maxHp,
                currentMp = member.currentMp,
                maxMp = member.maxMp,
                speed = member.speed,
                level = member.level,
                xp = member.xp,
                spellIds = mergedSpells
            )
        }
        _state.value = _state.value.copy(partyStats = mappedStats)
        persistCurrentState()
    }

    fun persistCurrentState() {
        val s = _state.value
        val slot = s.currentSlot
        val currentSave = saveManager.load(slot) ?: GameSaveData()
        val updatedSave = currentSave.copy(
            player = s.player,
            currentSceneId = s.currentScene.id,
            currentNodeId = s.currentNode.id,
            decisionsMade = s.decisionsMade,
            narrativeFlags = s.narrativeFlags,
            visitedNodeIds = s.visitedNodeIds.toList(),
            partyStats = s.partyStats,
            defeatedEncounters = s.defeatedEncounters,
            achievements = s.achievements,
            recentDialogueLog = s.dialogueHistory.takeLast(60),
            isEyesFreeMode = combatNarrator.isEyesFreeMode.value,
            isPocketGuardEnabled = combatNarrator.isPocketGuardEnabled.value,
            isCombatNarrationEnabled = combatNarrator.isCombatNarrationEnabled.value,
            isAutoListen = speechManager.isAutoListen.value,
            isAutoPlayEnabled = s.isPureStoryMode,
            isChimeMuted = speechManager.isChimeMuted.value,
            isNarrationEnabled = combatNarrator.isNarrationEnabled.value,
            isReadChoicesEnabled = combatNarrator.isReadChoicesEnabled.value,
            isAutoReadHubChoices = combatNarrator.isAutoReadHubChoices.value,
            speechRate = combatNarrator.speechRate.value,
            isCharacterPitchEnabled = combatNarrator.isCharacterPitchEnabled.value,
            isSpeakerAttributionEnabled = combatNarrator.isSpeakerAttributionEnabled.value,
            isMusicEnabled = musicManager?.isMusicEnabled?.value ?: currentSave.isMusicEnabled,
            musicVolume = musicManager?.musicVolume?.value ?: currentSave.musicVolume,
            voiceAssignments = combatNarrator.getVoiceAssignments(),
            dynamicMetrics = s.dynamicMetrics
        )
        saveManager.save(updatedSave, slot)
    }

    fun switchToCombat() {
        combatNarrator.stop()
        musicManager?.playCombatMusic()
        _state.value = _state.value.copy(gameScreen = GameScreen.COMBAT_ARENA)
    }

    fun switchToStory() {
        _state.value = _state.value.copy(gameScreen = GameScreen.STORY_EXPLORATION)
        musicManager?.playTrack(_state.value.currentScene.musicAsset)
        narrateCurrentNode()
    }

    fun openTutorial() {
        cancelPendingAutoAdvance()
        combatNarrator.stop()
        speechManager.cancel()
        _state.value = _state.value.copy(
            previousScreen = _state.value.gameScreen,
            gameScreen = GameScreen.TUTORIAL
        )
    }

    fun returnFromTutorial() {
        combatNarrator.stop()
        speechManager.cancel()
        _state.value = _state.value.copy(
            previousScreen = null,
            gameScreen = GameScreen.TITLE
        )
    }

    fun openAudioSetup() {
        cancelPendingAutoAdvance()
        combatNarrator.stop()
        speechManager.cancel()
        _state.value = _state.value.copy(
            previousScreen = _state.value.gameScreen,
            gameScreen = GameScreen.AUDIO_SETUP,
            isNewGameFlow = false
        )
    }

    fun returnFromAudioSetup() {
        val rawTarget = _state.value.previousScreen
        val returnTarget = if (rawTarget == null || rawTarget == GameScreen.AUDIO_SETUP) {
            GameScreen.TITLE
        } else {
            rawTarget
        }
        _state.value = _state.value.copy(
            previousScreen = null,
            gameScreen = returnTarget,
            isNewGameFlow = false
        )
        persistCurrentState()
        if (returnTarget == GameScreen.STORY_EXPLORATION) {
            musicManager?.playTrack(_state.value.currentScene.musicAsset)
            narrateCurrentNode()
        } else if (returnTarget == GameScreen.TITLE) {
            musicManager?.playTrack(com.voicerpg.engine.audio.MusicManager.TRACK_TITLE)
        } else {
            musicManager?.playTrack(com.voicerpg.engine.audio.MusicManager.TRACK_EXPLORATION)
        }
    }

    fun openAssetViewer() {
        cancelPendingAutoAdvance()
        cancelPendingStoryMode()
        combatNarrator.stop()
        speechManager.cancel()
        _state.value = _state.value.copy(
            previousScreen = _state.value.gameScreen,
            gameScreen = GameScreen.ASSET_VIEWER
        )
    }

    fun returnFromAssetViewer() {
        val rawTarget = _state.value.previousScreen
        val returnTarget = if (rawTarget == null || rawTarget == GameScreen.ASSET_VIEWER) {
            GameScreen.TITLE
        } else {
            rawTarget
        }
        _state.value = _state.value.copy(
            previousScreen = null,
            gameScreen = returnTarget
        )
        if (returnTarget == GameScreen.TITLE) {
            musicManager?.playTrack(com.voicerpg.engine.audio.MusicManager.TRACK_TITLE)
        } else if (returnTarget == GameScreen.STORY_EXPLORATION) {
            musicManager?.playTrack(_state.value.currentScene.musicAsset)
        }
    }

    fun openMiniGameTestMenu() {
        _state.value = _state.value.copy(isMiniGameTestMenuOpen = true)
    }

    fun closeMiniGameTestMenu() {
        _state.value = _state.value.copy(isMiniGameTestMenuOpen = false)
    }

    fun triggerMiniGame(miniGameId: String) {
        cancelPendingAutoAdvance()
        cancelPendingStoryMode()
        val config = GameContent.minigameById(miniGameId) ?: MiniGameConfig(
            id = miniGameId,
            title = miniGameId.replace('_', ' ').replaceFirstChar { it.uppercase() }
        )
        if (_state.value.isPureStoryMode) {
            val outcome = MiniGameSimulator.simulate(
                config = config,
                skillModifier = 1.0f,
                fatigueModifier = 1.0f
            )
            onMiniGameFinished(outcome)
        } else {
            speechManager.cancel()
            combatNarrator.stop()
            val prev = _state.value.gameScreen
            _state.value = _state.value.copy(
                gameScreen = GameScreen.MINI_GAME,
                previousScreen = prev,
                activeMiniGameConfig = config,
                isMiniGameTestMenuOpen = false
            )
        }
    }

    fun onSkipMiniGame() {
        val config = _state.value.activeMiniGameConfig ?: MiniGameConfig(id = "minigame", title = "Mini-Game")
        val outcome = MiniGameSimulator.simulate(
            config = config,
            skillModifier = 1.0f,
            fatigueModifier = 1.0f
        )
        onMiniGameFinished(outcome)
    }

    fun onMiniGameFinished(outcome: MiniGameOutcome) {
        cancelPendingAutoAdvance()
        cancelPendingStoryMode()

        if (_state.value.previousScreen == GameScreen.TITLE) {
            _state.value = _state.value.copy(
                gameScreen = GameScreen.TITLE,
                previousScreen = null,
                activeMiniGameConfig = null,
                isMiniGameTestMenuOpen = false
            )
            return
        }

        val lastNode = _state.value.currentNode
        val updatedMetrics = _state.value.dynamicMetrics.toMutableMap()
        outcome.metricDeltas.forEach { (k, v) ->
            val cur = updatedMetrics[k] ?: 50f
            updatedMetrics[k] = (cur + v).coerceIn(0f, 100f)
        }

        _state.value = _state.value.copy(
            gameScreen = GameScreen.STORY_EXPLORATION,
            previousScreen = null,
            activeMiniGameConfig = null,
            dynamicMetrics = updatedMetrics
        )

        val nextNodeId = lastNode.nextNodeId
        if (!nextNodeId.isNullOrBlank()) {
            val nextNode = GameContent.nodes[nextNodeId]
            if (nextNode != null) {
                applyNodeTransition(nextNode)
            }
        }
    }

    private var fastForwardJob: Job? = null

    fun startFastForward() {
        if (_state.value.isFastForwarding) return
        cancelPendingAutoAdvance()
        combatNarrator.stop()
        speechManager.cancel()
        _state.value = _state.value.copy(isFastForwarding = true)

        fastForwardJob = activeScope.launch {
            while (_state.value.isFastForwarding) {
                if (!canAdvanceDialogue()) {
                    stopFastForward()
                    break
                }
                advanceDialogueInternal(suppressNarration = true)
                delay(120)
            }
        }
    }

    fun stopFastForward() {
        fastForwardJob?.cancel()
        fastForwardJob = null
        if (_state.value.isFastForwarding) {
            _state.value = _state.value.copy(isFastForwarding = false)
            narrateCurrentNode()
        }
    }

    fun toggleFastForward() {
        if (_state.value.isFastForwarding) {
            stopFastForward()
        } else {
            startFastForward()
        }
    }

    fun getQuestRecap(): QuestRecap {
        val s = _state.value
        return StoryRecapEngine.buildRecap(
            scene = s.currentScene,
            node = s.currentNode,
            flags = s.narrativeFlags,
            partyStats = s.partyStats
        )
    }

    fun playStoryRecap() {
        cancelPendingAutoAdvance()
        if (_state.value.isFastForwarding) {
            stopFastForward()
        }
        val recap = getQuestRecap()
        _state.value = _state.value.copy(
            isBacklogOpen = true,
            isBacklogRecapActive = true
        )
        combatNarrator.stop()
        combatNarrator.speak(recap.spokenRecap, force = true)
    }

    fun setBacklogRecapTab(isRecap: Boolean) {
        _state.value = _state.value.copy(isBacklogRecapActive = isRecap)
    }

    fun openBacklog(showRecapFirst: Boolean = true) {
        cancelPendingAutoAdvance()
        if (_state.value.isFastForwarding) {
            stopFastForward()
        }
        _state.value = _state.value.copy(
            isBacklogOpen = true,
            isBacklogRecapActive = showRecapFirst
        )
        if (combatNarrator.isEyesFreeMode.value) {
            val recap = getQuestRecap()
            combatNarrator.speak(recap.spokenRecap, force = true)
        }
    }

    fun closeBacklog() {
        combatNarrator.stop()
        _state.value = _state.value.copy(isBacklogOpen = false)
        if (combatNarrator.isEyesFreeMode.value) {
            combatNarrator.speak("Resuming story.", force = true) {
                narrateCurrentNode()
            }
        }
    }

    fun toggleBacklog() {
        if (_state.value.isBacklogOpen) {
            closeBacklog()
        } else {
            openBacklog()
        }
    }

    fun replayDialogueEntry(entry: DialogueLogEntry) {
        val speaker = GameContent.speakerById(entry.speakerId) ?: GameContent.narrator
        combatNarrator.stop()
        combatNarrator.narrateDialogue(
            speaker = speaker,
            text = entry.text,
            choices = emptyList()
        )
    }

    fun handleStoryVoiceInput(utterance: String) {
        val lower = utterance.lowercase().trim()
        val node = _state.value.currentNode

        // Intercept backlog dismissal when backlog is open
        if (_state.value.isBacklogOpen) {
            if (lower.contains("close") || lower == "back" || lower == "return" || lower == "dismiss" || lower.contains("exit")) {
                closeBacklog()
                return
            }
        }

        // Intercept fast-forward stop
        if (_state.value.isFastForwarding) {
            if (lower == "stop" || lower == "halt" || lower == "pause" || lower.contains("stop skip")) {
                stopFastForward()
                return
            }
        }

        // Handle voice navigation on Title Screen
        if (_state.value.gameScreen == GameScreen.TITLE) {
            when {
                lower.contains("slot 1") || lower.contains("slot one") -> {
                    selectSlot(1)
                    if (lower.contains("load") || lower.contains("continue") || lower.contains("resume") || lower.contains("play")) {
                        if (_state.value.hasExistingSave) continueGame(1)
                    }
                    return
                }
                lower.contains("slot 2") || lower.contains("slot two") -> {
                    selectSlot(2)
                    if (lower.contains("load") || lower.contains("continue") || lower.contains("resume") || lower.contains("play")) {
                        if (_state.value.hasExistingSave) continueGame(2)
                    }
                    return
                }
                lower.contains("slot 3") || lower.contains("slot three") -> {
                    selectSlot(3)
                    if (lower.contains("load") || lower.contains("continue") || lower.contains("resume") || lower.contains("play")) {
                        if (_state.value.hasExistingSave) continueGame(3)
                    }
                    return
                }
                lower.contains("continue") || lower.contains("resume") || lower.contains("load") -> {
                    if (_state.value.hasExistingSave) {
                        continueGame()
                    }
                    return
                }
                lower.contains("new game") || lower.contains("start game") || lower.contains("start new") || lower.contains("begin") -> {
                    startNewGameFlow()
                    return
                }
                lower.contains("audio setup") || lower.contains("voices") || lower.contains("calibrate") -> {
                    openAudioSetup()
                    return
                }
                lower.contains("tutorial") || lower.contains("guide") || lower.contains("how to play") || lower.contains("help me") -> {
                    openTutorial()
                    return
                }
                lower.contains("story mode") || lower.contains("auto play") || lower.contains("pure story") -> {
                    startPureStoryMode()
                    return
                }
                lower.contains("options") || lower.contains("settings") -> {
                    onOpenOptions?.invoke()
                    return
                }
                lower.contains("asset viewer") || lower.contains("gallery") || lower.contains("jukebox") || lower.contains("music player") || lower.contains("view assets") || lower.contains("soundtrack") -> {
                    openAssetViewer()
                    return
                }
                lower.contains("test minigame") || lower.contains("test minigames") || lower.contains("arcade") || lower.contains("minigames") || lower.contains("mini game") -> {
                    if (GameContent.minigames.isNotEmpty()) {
                        openMiniGameTestMenu()
                    }
                    return
                }
                _state.value.isMiniGameTestMenuOpen && (lower.contains("close") || lower.contains("cancel") || lower.contains("back")) -> {
                    closeMiniGameTestMenu()
                    return
                }
                else -> {
                    if (_state.value.isMiniGameTestMenuOpen && GameContent.minigames.isNotEmpty()) {
                        for (mg in GameContent.minigames.values) {
                            if (lower.contains(mg.id.lowercase()) || lower.contains(mg.title.lowercase())) {
                                triggerMiniGame(mg.id)
                                return
                            }
                        }
                    }
                }
            }
            return
        }

        // Story Mode Pause / Resume / Toggle voice commands
        if (lower.contains("pause story") || lower.contains("pause auto play")) {
            if (_state.value.isPureStoryMode && !_state.value.isStoryAutoPlayPaused) {
                toggleStoryAutoPlayPause()
                return
            }
        }
        if (lower.contains("resume story") || lower.contains("play story") || lower.contains("resume auto play")) {
            if (_state.value.isPureStoryMode && _state.value.isStoryAutoPlayPaused) {
                toggleStoryAutoPlayPause()
                return
            }
        }
        if (lower == "story mode" || lower == "toggle story mode" || lower == "pure story mode") {
            val willEnable = !_state.value.isPureStoryMode
            setPureStoryMode(willEnable)
            val msg = if (willEnable) "Pure story mode enabled." else "Pure story mode disabled."
            combatNarrator.speak(msg, force = true)
            return
        }

        // In-game return to title voice command
        if (lower.contains("title screen") || lower.contains("main menu") || lower.contains("return to title")) {
            returnToTitle()
            return
        }

        // Intercept direct companion voice assignment voice command
        if (lower.contains("assign voice") || lower.contains("companion voice") || lower.contains("customize voice")) {
            openAudioSetup()
            return
        }

        // 0. Intercept Meta Voice Commands (Options, Narration, Choice Reading, Pocket Mode, Backlog, Fast-Forward)
        val peek = IntentParser.parse(utterance, emptyList(), emptyList(), emptyList())
        when (peek.metaCommand) {
            MetaCommand.OPEN_OPTIONS -> {
                onOpenOptions?.invoke()
                return
            }
            MetaCommand.CLOSE_OPTIONS -> {
                onCloseOptions?.invoke()
                return
            }
            MetaCommand.OPEN_BACKLOG -> {
                openBacklog()
                return
            }
            MetaCommand.CLOSE_BACKLOG -> {
                closeBacklog()
                return
            }
            MetaCommand.FAST_FORWARD -> {
                startFastForward()
                return
            }
            MetaCommand.STOP_FAST_FORWARD -> {
                if (_state.value.isFastForwarding) {
                    stopFastForward()
                    return
                }
            }
            MetaCommand.STORY_RECAP -> {
                playStoryRecap()
                return
            }
            MetaCommand.TOGGLE_NARRATION -> {
                val enabled = combatNarrator.toggleNarration()
                val status = if (enabled) "Story dialogue narration enabled." else "Story dialogue narration muted."
                combatNarrator.speak(status, force = true)
                persistCurrentState()
                return
            }
            MetaCommand.TOGGLE_READ_CHOICES -> {
                val enabled = combatNarrator.toggleReadChoices()
                val status = if (enabled) "Choice reading enabled." else "Choice reading disabled."
                combatNarrator.speak(status, force = true)
                persistCurrentState()
                return
            }
            MetaCommand.TOGGLE_EYES_FREE -> {
                val enabled = combatNarrator.toggleEyesFreeMode()
                if (enabled) {
                    speechManager.setAutoListen(true)
                }
                val status = if (enabled) "Screenless pocket mode enabled. Screen locked." else "Pocket mode disabled."
                combatNarrator.speak(status, force = true)
                persistCurrentState()
                return
            }
            MetaCommand.ENABLE_EYES_FREE -> {
                combatNarrator.setEyesFreeMode(true)
                speechManager.setAutoListen(true)
                combatNarrator.speak("Screenless pocket mode enabled. Screen locked.", force = true)
                persistCurrentState()
                return
            }
            MetaCommand.DISABLE_EYES_FREE -> {
                combatNarrator.setEyesFreeMode(false)
                combatNarrator.speak("Pocket mode disabled.", force = true)
                persistCurrentState()
                return
            }
            MetaCommand.UNLOCK_SCREEN -> {
                combatNarrator.unlockPocketGuard()
                combatNarrator.speak("Screen unlocked.", force = true)
                return
            }
            MetaCommand.LOCK_SCREEN -> {
                if (!combatNarrator.isEyesFreeMode.value) {
                    combatNarrator.setEyesFreeMode(true)
                    speechManager.setAutoListen(true)
                    persistCurrentState()
                }
                combatNarrator.lockPocketGuard()
                combatNarrator.speak("Screen locked. Pocket mode active.", force = true)
                return
            }
            MetaCommand.TOGGLE_AUTO_LISTEN -> {
                speechManager.toggleAutoListen()
                val enabled = speechManager.isAutoListen.value
                val status = if (enabled) "Hands free auto listen enabled." else "Auto listen disabled."
                combatNarrator.speak(status, force = true)
                persistCurrentState()
                return
            }
            MetaCommand.TOGGLE_MUSIC -> {
                val enabled = musicManager?.toggleMusic() ?: true
                val status = if (enabled) "Background music enabled." else "Background music muted."
                combatNarrator.speak(status, force = true)
                persistCurrentState()
                return
            }
            MetaCommand.HELP -> {
                combatNarrator.speak("Say 'Next' to advance dialogue. Say a choice keyword to select it. Say 'Faster narration' or 'Slower narration' to change speech speed. Say 'Recap' to hear your quest summary. Say 'Log' or 'History' for dialogue history. Say 'Skip' to fast-forward. Say 'Options' for settings.", force = true)
                return
            }
            MetaCommand.SPEED_UP_NARRATION -> {
                val current = combatNarrator.speechRate.value
                val next = when {
                    current < 0.95f -> 1.05f
                    current < 1.15f -> 1.25f
                    else -> 1.50f
                }
                combatNarrator.setSpeechRate(next)
                persistCurrentState()
                combatNarrator.speak("Narration speed increased to %.2fx.".format(next), force = true)
                return
            }
            MetaCommand.SLOW_DOWN_NARRATION -> {
                val current = combatNarrator.speechRate.value
                val next = when {
                    current > 1.35f -> 1.25f
                    current > 1.15f -> 1.05f
                    else -> 0.85f
                }
                combatNarrator.setSpeechRate(next)
                persistCurrentState()
                combatNarrator.speak("Narration speed decreased to %.2fx.".format(next), force = true)
                return
            }
            MetaCommand.RESET_NARRATION_SPEED -> {
                combatNarrator.setSpeechRate(1.05f)
                persistCurrentState()
                combatNarrator.speak("Narration speed reset to normal 1.05x.", force = true)
                return
            }
            else -> Unit
        }

        // 1. Epilogue restart command
        if (node.id == "epilogue_credits" || node.setFlagOnEnter == "game_completed") {
            val restartKeywords = listOf("play again", "new game", "start over", "restart", "awaken", "embark", "begin again")
            if (restartKeywords.any { lower.contains(it) }) {
                if (_state.value.isPureStoryMode) {
                    startPureStoryMode(fresh = true)
                } else {
                    startNewGameFlow(_state.value.currentSlot)
                }
                return
            }
        }

        // 2. Battle trigger command
        if (node.triggerBattleEncounterId != null) {
            val combatKeywords = listOf("fight", "battle", "attack", "fireball", "commence", "strike", "charge", "to battle", "engage", "slay", "ready", "start", "draw blade")
            if (combatKeywords.any { lower.contains(it) }) {
                advanceDialogue()
                return
            }
        }

        // 2b. Mini-game trigger command
        if (node.triggerMiniGameId != null) {
            val miniGameKeywords = listOf("play", "start", "begin", "ready", "mini game", "game", "go", "action")
            if (miniGameKeywords.any { lower.contains(it) }) {
                advanceDialogue()
                return
            }
        }

        // 3. Flexible choice selection with loud & nerdy roleplay matching
        val effective = effectiveDialogueChoices(node)
        if (effective.isNotEmpty()) {
            val matchedChoice = StoryChoiceMatcher.matchChoice(
                utterance = utterance,
                choices = effective,
                completedFlags = _state.value.narrativeFlags
            )
            if (matchedChoice != null) {
                if (isChoiceLocked(matchedChoice, node, _state.value.narrativeFlags)) {
                    combatNarrator.speak("That option is locked. Please explore the remaining dialogue options first.", force = true)
                    return
                }
                if (isChoiceCompleted(matchedChoice, _state.value.narrativeFlags, _state.value.defeatedEncounters)) {
                    combatNarrator.speak("That objective has already been completed. Please select a remaining task.", force = true)
                    return
                }
                selectChoice(matchedChoice)
                return
            }
        }

        // 4. Non-branching progression command (natural & nerdy progression phrases)
        if (effective.isEmpty() && StoryChoiceMatcher.isProgressionUtterance(utterance)) {
            advanceDialogue()
            return
        }

        // If in hands-free auto-listen mode and no action triggered, keep listening!
        if (speechManager.isAutoListen.value && _state.value.gameScreen == GameScreen.STORY_EXPLORATION) {
            activeScope.launch {
                delay(200)
                if (speechManager.isAutoListen.value && _state.value.gameScreen == GameScreen.STORY_EXPLORATION) {
                    speechManager.startListening(
                        onStandby = {
                            if (combatNarrator.isEyesFreeMode.value) {
                                combatNarrator.speak("Standing by. Tap the screen when you are ready to continue.", force = true)
                            }
                        },
                        onResult = { nextUtterance ->
                            handleStoryVoiceInput(nextUtterance)
                        }
                    )
                }
            }
        }
    }

    fun resumeVoiceListening() {
        if (combatNarrator.isEyesFreeMode.value) {
            combatNarrator.speak("Ready. What is your decision?", force = true) {
                activeScope.launch {
                    delay(100)
                    speechManager.startListening(
                        onStandby = {
                            if (combatNarrator.isEyesFreeMode.value) {
                                combatNarrator.speak("Standing by. Tap the screen when you are ready to continue.", force = true)
                            }
                        },
                        onResult = { utterance ->
                            handleStoryVoiceInput(utterance)
                        }
                    )
                }
            }
        } else {
            speechManager.startListening(
                onStandby = {
                    if (combatNarrator.isEyesFreeMode.value) {
                        combatNarrator.speak("Standing by. Tap the screen when you are ready to continue.", force = true)
                    }
                },
                onResult = { utterance ->
                    handleStoryVoiceInput(utterance)
                }
            )
        }
    }

    private fun narrateCurrentNode() {
        cancelPendingAutoAdvance()
        cancelPendingStoryMode()
        if (_state.value.gameScreen != GameScreen.STORY_EXPLORATION) return
        val node = _state.value.currentNode
        val effective = effectiveDialogueChoices(node)
        val narrationText = if (effective.isEmpty() && node.choices.isNotEmpty()) {
            val sole = eligibleChoices(node).firstOrNull()
            if (sole != null) node.text + "\n\nOne path remains. " + sole.text else node.text
        } else {
            node.text
        }
        val isStoryMode = _state.value.isPureStoryMode
        val isPaused = _state.value.isStoryAutoPlayPaused

        // In Pure Story Mode, do not read choices aloud with "What is your command?" — let narrator speak the story
        val choicesToRead = if (isStoryMode || effective.isEmpty()) emptyList() else eligibleChoices(node)

        combatNarrator.narrateDialogue(
            speaker = node.speaker,
            text = narrationText,
            choices = choicesToRead
        ) {
            val isPocketMode = combatNarrator.isEyesFreeMode.value
            val canAuto = canAdvanceDialogue()

            if (_state.value.isPureStoryMode && !_state.value.isStoryAutoPlayPaused) {
                scheduleStoryModeNextStep(node, delayMs = 1200L)
            } else if (isPocketMode && canAuto) {
                schedulePocketModeAutoAdvance(node)
            } else if (speechManager.isAutoListen.value) {
                activeScope.launch {
                    delay(120)
                    speechManager.startListening(
                        onStandby = {
                            if (combatNarrator.isEyesFreeMode.value) {
                                combatNarrator.speak("Standing by. Tap the screen when you are ready to continue.", force = true)
                            }
                        },
                        onResult = { utterance ->
                            handleStoryVoiceInput(utterance)
                        }
                    )
                }
            }
        }

        // Safety watchdog: ensure Pure Story Mode auto-advance is scheduled even if TTS onDone is never invoked
        if (isStoryMode && !isPaused) {
            val wordCount = narrationText.split(Regex("\\s+")).count { it.isNotBlank() }
            val estimatedDurationMs = maxOf(4000L, ((wordCount / 2.0f) * 1000L).toLong() + 3000L)
            val watchdogTimeoutMs = estimatedDurationMs + 6000L
            watchdogStoryModeJob = activeScope.launch {
                delay(watchdogTimeoutMs)
                if (_state.value.isPureStoryMode && !_state.value.isStoryAutoPlayPaused &&
                    _state.value.currentNode.id == node.id && _state.value.gameScreen == GameScreen.STORY_EXPLORATION
                ) {
                    scheduleStoryModeNextStep(node, delayMs = 200L)
                }
            }
        }
    }

    fun completeLanguageSetup(lang: String, region: String) {
        val cleanLang = lang.lowercase().trim()
        val cleanRegion = region.uppercase().trim()
        saveManager.saveGlobalSettings(
            SaveManager.GlobalGameSettings(
                isLanguageSetupCompleted = true,
                selectedLanguage = cleanLang,
                selectedRegion = cleanRegion
            )
        )
        com.voicerpg.engine.localization.TranslationManager.setLanguageAndRegion(cleanLang, cleanRegion)
        speechManager.setSelectedLocale(cleanLang)
        speechManager.setSelectedRegion(cleanRegion)
        combatNarrator.setSelectedLanguageAndRegion(cleanLang, cleanRegion)
        com.voicerpg.engine.engine.IntentParser.currentLocale = cleanLang
        _state.value = _state.value.copy(
            selectedLanguage = cleanLang,
            selectedRegion = cleanRegion,
            gameScreen = GameScreen.TITLE,
            showFirstLaunchLanguagePrompt = false
        )
        persistCurrentState()
    }

    fun setSelectedLanguage(lang: String) {
        val clean = lang.lowercase().trim()
        val currentReg = _state.value.selectedRegion
        saveManager.saveGlobalSettings(
            SaveManager.GlobalGameSettings(
                isLanguageSetupCompleted = true,
                selectedLanguage = clean,
                selectedRegion = currentReg
            )
        )
        com.voicerpg.engine.localization.TranslationManager.setLanguageAndRegion(clean, currentReg)
        _state.value = _state.value.copy(selectedLanguage = clean)
        speechManager.setSelectedLocale(clean)
        combatNarrator.setSelectedLanguage(clean)
        com.voicerpg.engine.engine.IntentParser.currentLocale = clean
        persistCurrentState()
    }

    fun setSelectedRegion(region: String) {
        val clean = region.uppercase().trim()
        val currentLang = _state.value.selectedLanguage
        saveManager.saveGlobalSettings(
            SaveManager.GlobalGameSettings(
                isLanguageSetupCompleted = true,
                selectedLanguage = currentLang,
                selectedRegion = clean
            )
        )
        com.voicerpg.engine.localization.TranslationManager.setLanguageAndRegion(currentLang, clean)
        _state.value = _state.value.copy(selectedRegion = clean)
        speechManager.setSelectedRegion(clean)
        combatNarrator.setSelectedRegion(clean)
        persistCurrentState()
    }

    fun setLanguageAndRegion(lang: String, region: String) {
        completeLanguageSetup(lang, region)
    }

    fun dismissFirstLaunchLanguagePrompt() {
        _state.value = _state.value.copy(showFirstLaunchLanguagePrompt = false)
    }

    override fun onCleared() {
        super.onCleared()
        stopFastForward()
        cancelPendingAutoAdvance()
        cancelPendingStoryMode()
    }
}
