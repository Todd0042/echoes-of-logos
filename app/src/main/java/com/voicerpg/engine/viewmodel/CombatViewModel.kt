package com.voicerpg.engine.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicerpg.engine.combat.AffinityTable
import com.voicerpg.engine.combat.BattleCue
import com.voicerpg.engine.combat.CombatTiming
import com.voicerpg.engine.combat.DamageInputs
import com.voicerpg.engine.combat.DamageResolver
import com.voicerpg.engine.combat.EnemyBrain
import com.voicerpg.engine.combat.EnemyCodex
import com.voicerpg.engine.combat.EnemyFamily
import com.voicerpg.engine.combat.EnemyMove
import com.voicerpg.engine.combat.Intent
import com.voicerpg.engine.combat.Moveset
import com.voicerpg.engine.combat.MovesetTable
import com.voicerpg.engine.combat.Progression
import com.voicerpg.engine.combat.ProgressMember
import com.voicerpg.engine.combat.School
import com.voicerpg.engine.combat.StatusId
import com.voicerpg.engine.combat.StatusInstance
import com.voicerpg.engine.combat.StatusSystem
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SfxManager
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.IntentParser
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.CharacterStance
import com.voicerpg.engine.model.CombatantFaction
import com.voicerpg.engine.model.CombatPhase
import com.voicerpg.engine.model.EncounterDefinition
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.model.SavedCharacterStats
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.FloatingCombatText
import com.voicerpg.engine.model.MetaCommand
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.ResonanceResult
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.model.Spell
import com.voicerpg.engine.model.SpellSchool
import com.voicerpg.engine.model.TargetSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

data class CombatState(
    val phase: CombatPhase = CombatPhase.ATB_WAITING,
    val roundNumber: Int = 1,
    val party: List<PartyMember> = emptyList(),
    val enemies: List<Enemy> = emptyList(),
    val activePartyMemberId: String? = null,
    val lastResonance: ResonanceResult? = null,
    val isLogosBannerVisible: Boolean = false,
    val floatingTexts: List<FloatingCombatText> = emptyList(),
    val currentEnvironment: BattleEnvironment = BattleEnvironment.DUNGEON,
    val currentEnvironmentId: String = "",
    val isEyesFreeMode: Boolean = false,
    val isOptionsOpen: Boolean = false,
    val isPureStoryMode: Boolean = false
) {
    val activePartyMember: PartyMember?
        get() = party.firstOrNull { it.id == activePartyMemberId }
            ?: party.firstOrNull { it.isAlive && it.isTurnReady }
            ?: party.firstOrNull { it.isAlive }

    val activeSpells: List<Spell>
        get() = activePartyMember?.spells ?: emptyList()
}

class CombatViewModel(
    val speechManager: SpeechManager,
    val sfxManager: SfxManager = SfxManager(),
    val resonanceEngine: ResonanceEngine = ResonanceEngine(),
    val combatNarrator: CombatNarrator = CombatNarrator(),
    val musicManager: com.voicerpg.engine.audio.MusicManager? = null,
    /** Every wait in a turn; [CombatTiming.INSTANT] in tests that don't measure time. */
    val timing: CombatTiming = CombatTiming.DEFAULT,
    /** Enemy move choice and damage rolls; seed it in tests for repeatable battles. */
    private val random: Random = Random.Default,
    private val scopeOverride: CoroutineScope? = null
) : ViewModel() {

    private val activeScope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow(createInitialState())
    val state: StateFlow<CombatState> = _state.asStateFlow()

    internal fun setCombatStateForTesting(testState: CombatState) {
        _state.value = testState
    }

    // ---- Battle cues: what happened, in order, for the presentation layer (ui/fx) to play -----------
    // Never blocks logic: with no screen collecting (pocket mode, tests) cues are simply dropped.
    private val _cues = MutableSharedFlow<BattleCue>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val cues: SharedFlow<BattleCue> = _cues.asSharedFlow()

    private fun cue(c: BattleCue) {
        _cues.tryEmit(c)
    }

    /** Waits the [timing] table's hold for [c]: the only way combat logic pauses between beats. */
    private suspend fun hold(c: BattleCue) {
        val ms = timing.holdMs(c, pocket = combatNarrator.isEyesFreeMode.value)
        if (ms > 0L) delay(ms)
    }

    private suspend fun beat(c: BattleCue) {
        cue(c)
        hold(c)
    }

    private fun aliveIds(): Set<String> =
        (_state.value.party.filter { it.isAlive }.map { it.id } + _state.value.enemies.filter { it.isAlive }.map { it.id }).toSet()

    // ---- Voice charge-up: while the active hero is heard, the charge follows the microphone -------------
    private var chargeJob: Job? = null
    private var chargingId: String? = null

    private fun startChargeWatcher() {
        chargeJob?.cancel()
        chargeJob = activeScope.launch {
            combine(
                speechManager.speechState,
                _state.map { it.phase to it.activePartyMemberId }.distinctUntilChanged()
            ) { speech, (phase, activeId) ->
                if (speech == com.voicerpg.engine.audio.SpeechState.Listening && phase == CombatPhase.PLAYER_INPUT) activeId else null
            }.distinctUntilChanged().collectLatest { casterId ->
                endCharge()
                if (casterId == null) return@collectLatest
                chargingId = casterId
                cue(BattleCue.ChargeStart(casterId, typed = false))
                sfxManager.startChargeHum()
                var lastSent = -1L
                var lastLevel = -1f
                speechManager.rmsLevel.collect { level ->
                    val now = System.currentTimeMillis()
                    // Small wobbles at most every 50 ms; a real jump (a shout) goes through at once.
                    if (now - lastSent >= CHARGE_LEVEL_INTERVAL_MS || kotlin.math.abs(level - lastLevel) >= CHARGE_LEVEL_JUMP) {
                        lastSent = now
                        lastLevel = level
                        cue(BattleCue.ChargeLevel(casterId, level))
                    }
                    sfxManager.setChargeLevel(level)
                }
            }
        }
    }

    private fun endCharge() {
        val id = chargingId ?: return
        chargingId = null
        sfxManager.stopChargeHum()
        cue(BattleCue.ChargeEnd(id))
    }

    // ---- Music intensity (M8): the intensity layer follows party health and boss phases -------------
    private var intensityJob: Job? = null
    private var intensityOn = false
    private var bossPhaseShifted = false

    private fun partyHpRatio(party: List<PartyMember>): Float {
        val mortal = party.filter { !it.isGhost }
        val max = mortal.sumOf { it.maxHp }
        return if (max <= 0) 1f else mortal.sumOf { it.currentHp.coerceAtLeast(0) }.toFloat() / max
    }

    private fun startIntensityWatcher(intensityTrack: String?) {
        intensityJob?.cancel()
        intensityOn = false
        bossPhaseShifted = false
        musicManager?.setIntensityTrack(intensityTrack)
        intensityJob = activeScope.launch {
            _state.map { partyHpRatio(it.party) }.distinctUntilChanged().collect { evaluateIntensity(it) }
        }
    }

    private fun evaluateIntensity(ratio: Float = partyHpRatio(_state.value.party)) = synchronized(this) {
        val next = com.voicerpg.engine.combat.MusicIntensityRule.next(
            current = intensityOn, partyHpRatio = ratio,
            threshold = GameContent.presentation.music.lowHpThreshold, bossPhaseShifted = bossPhaseShifted
        )
        if (next != intensityOn) {
            intensityOn = next
            cue(BattleCue.MusicIntensity(next))
            musicManager?.setIntensity(next)
        }
    }

    /** Announces a [BattleCue.Death] for everyone alive in [before] who has fallen since. */
    private fun announceFallen(before: Set<String>) {
        val enemyIds = _state.value.enemies.map { it.id }.toSet()
        for (id in before - aliveIds()) {
            val isEnemy = id in enemyIds
            cue(BattleCue.Death(id, isEnemy = isEnemy))
            if (isEnemy && GameContent.presentation.deathEffect != com.voicerpg.engine.content.PresentationConfig.DeathEffect.FADE) {
                sfxManager.playShatter()
            }
            if (!isEnemy) endStreak() // a fallen hero ends the streak
        }
    }

    // ---- Reward bold voices (M5) ---------------------------------------------------------------------
    private var streak = 0

    /** Consecutive casts at or above the game's streak tier. Changes looks and sound only, never damage. */
    private fun updateStreak(tier: ResonanceTier) {
        if (tier >= GameContent.presentation.streak.minTier) {
            streak++
            if (streak >= 2) cue(BattleCue.Streak(streak))
        } else {
            endStreak()
        }
    }

    private fun endStreak() {
        if (streak == 0) return
        streak = 0
        cue(BattleCue.Streak(0))
    }

    // ---- Cut-ins (M7) ----------------------------------------------------------------------------------
    private fun heroCutIn(member: PartyMember, tier: ResonanceTier): BattleCue.CutIn? {
        if (tier < GameContent.presentation.cutIn.minTier) return null
        val character = GameContent.characterById(member.id)
        val spec = character?.combat?.cutIn
        val portrait = spec?.portrait ?: character?.speaker?.portraitAsset
            ?: if (member.id == "hero") GameContent.manifest.defaultHeroPortrait else null
        val color = spec?.color?.let { com.voicerpg.engine.content.parseHexColor(it, member.avatarTint) }
            ?: character?.speaker?.themeColor ?: member.avatarTint
        return BattleCue.CutIn(member.id, member.name, tier, portrait, color, spec?.quote, isEnemy = false)
    }

    private fun enemyCutIn(enemy: Enemy): BattleCue.CutIn? {
        val spec = enemy.cutIn ?: return null
        return BattleCue.CutIn(
            characterId = enemy.id, name = enemy.name, tier = ResonanceTier.TRANSCENDENTAL, portrait = spec.portrait,
            color = spec.color?.let { com.voicerpg.engine.content.parseHexColor(it, enemy.spriteTint) } ?: enemy.spriteTint,
            quote = spec.quote, isEnemy = true
        )
    }

    /** Plays a cut-in; in pocket mode its quote is spoken instead of shown. */
    private suspend fun playCutIn(cutIn: BattleCue.CutIn) {
        cue(cutIn)
        if (combatNarrator.isEyesFreeMode.value && !cutIn.quote.isNullOrBlank()) {
            combatNarrator.speak("${cutIn.name}: ${cutIn.quote}", force = false)
        }
        hold(cutIn)
    }

    /** Is [enemy] a boss holding a planned ultimate (charging, or about to fire)? */
    private fun holdsUltimate(enemy: Enemy): Boolean {
        val planned = enemy.plannedMove ?: return false
        if (!enemy.isBoss) return false
        return enemy.ultimateChargeLeft > 0 ||
            movesetOf(enemy).let { set -> (set.moves + set.phases.flatMap { it.addMoves }).any { it.name == planned && Intent.isUltimate(true, it) } }
    }

    /**
     * After a hero's blow: a MASTER+ chant on a weakness pushes the target's gauge back; a MYTHIC+ hit on a
     * boss holding its ultimate breaks it. At most one per enemy per round.
     */
    private fun applyBoldVoice(hits: List<BattleCue.Hit>, tier: ResonanceTier) {
        val round = _state.value.roundNumber
        val staggers = mutableListOf<BattleCue.Stagger>()
        _state.update { prev ->
            prev.copy(enemies = prev.enemies.map { e ->
                val hit = hits.firstOrNull { it.targetId == e.id && it.kind == BattleCue.HitKind.DAMAGE && it.amount > 0 }
                if (hit == null || !e.isAlive || e.staggeredRound == round) return@map e
                val pushedBack = (e.atbGauge - STAGGER_GAUGE).coerceAtLeast(0f)
                when {
                    tier >= ResonanceTier.MYTHIC && holdsUltimate(e) -> {
                        staggers += BattleCue.Stagger(e.id, interruptedMove = e.plannedMove)
                        e.copy(atbGauge = pushedBack, plannedMove = null, ultimateChargeLeft = 0, staggeredRound = round)
                    }
                    tier >= ResonanceTier.MASTER && hit.affinity >= WEAKNESS_AFFINITY -> {
                        staggers += BattleCue.Stagger(e.id, interruptedMove = null)
                        e.copy(atbGauge = pushedBack, staggeredRound = round)
                    }
                    else -> e
                }
            })
        }
        for (s in staggers) {
            cue(s)
            if (s.interruptedMove != null) {
                cue(BattleCue.TitleCard("BROKEN!", fromParty = true))
                val name = _state.value.enemies.firstOrNull { it.id == s.enemyId }?.name ?: "The foe"
                combatNarrator.speak("$name's ${s.interruptedMove} is broken!", force = true)
                sfxManager.playStaggerStinger()
            } else {
                sfxManager.playStaggerStinger()
            }
        }
    }

    // Debug-only developer tools visibility. Kept OUT of CombatState so that
    // encounter/restart state reconstructions can never reset it.
    private val _isDeveloperToolsEnabled = MutableStateFlow(false)
    val isDeveloperToolsEnabled: StateFlow<Boolean> = _isDeveloperToolsEnabled.asStateFlow()

    fun toggleDeveloperTools() {
        _isDeveloperToolsEnabled.value = !_isDeveloperToolsEnabled.value
    }

    var currentEncounterId: String? = null
        internal set

    var onContinueStory: (() -> Unit)? = null

    // Persisted progression (level/xp + damaged HP/MP carry) applied when encounters start.
    private var importedStats: List<SavedCharacterStats> = emptyList()
    fun applySavedStats(stats: List<SavedCharacterStats>) {
        importedStats = stats
    }

    private var lastVictoryOutcomes: List<com.voicerpg.engine.combat.ProgressOutcome> = emptyList()
    val recentLevelUps: List<com.voicerpg.engine.combat.ProgressOutcome> get() = lastVictoryOutcomes.filter { it.levelsGained > 0 }

    /** Lore-faithful combat doctrine from EnemyCodex: family, self-element, defense, xp, moveset. */
    private fun decorateEnemy(enemy: Enemy): Enemy {
        val codex = EnemyCodex.entryOf(enemy.name)
        return enemy.copy(
            family = codex?.family ?: enemy.family,
            selfElement = codex?.selfElement?.ifEmpty { null } ?: enemy.selfElement.ifEmpty { null } ?: "",
            defense = if (codex != null) maxOf(codex.defense, enemy.defense) else enemy.defense,
            xpReward = codex?.baseXp ?: enemy.xpReward,
            movesetId = codex?.moveset?.ifEmpty { null } ?: enemy.movesetId.ifEmpty { null } ?: ""
        )
    }

    private fun schoolOf(school: SpellSchool): School = School.fromNameOrNull(school.name) ?: School.PHYSICAL
    private fun familyOf(enemy: Enemy): EnemyFamily = EnemyFamily.fromNameOrNull(enemy.family) ?: EnemyFamily.FLESH

    var arenaWidth: Float = 1080f
        private set
    var arenaHeight: Float = 1400f
        private set

    private val _combatantPositions = java.util.concurrent.ConcurrentHashMap<String, Pair<Float, Float>>()

    fun updateArenaDimensions(width: Float, height: Float) {
        if (width > 0f && height > 0f) {
            arenaWidth = width
            arenaHeight = height
        }
    }

    fun updateCombatantPosition(id: String, x: Float, y: Float) {
        if (x > 0f && y > 0f) {
            _combatantPositions[id] = x to y
        }
    }

    /** Live sprite center or proportional centered fallback. */
    fun enemyFloatSlot(enemyId: String): Pair<Float, Float> {
        _combatantPositions[enemyId]?.let { return it }
        val enemies = _state.value.enemies
        val idx = enemies.indexOfFirst { it.id == enemyId }.coerceAtLeast(0)
        return if (enemies.size <= 3) {
            val total = enemies.size.coerceAtLeast(1)
            val spacing = (arenaHeight * 0.40f) / total
            val startY = (arenaHeight * 0.50f) - (total - 1) * spacing * 0.5f
            (arenaWidth * 0.78f) to (startY + idx * spacing)
        } else {
            val x = if (idx % 2 == 0) arenaWidth * 0.68f else arenaWidth * 0.84f
            val rows = (enemies.size + 1) / 2
            val rowIdx = idx / 2
            val spacing = (arenaHeight * 0.45f) / rows.coerceAtLeast(1)
            val startY = (arenaHeight * 0.50f) - (rows - 1) * spacing * 0.5f
            x to (startY + rowIdx * spacing)
        }
    }

    /** Live sprite centre of any combatant, party or enemy. */
    fun combatantSlot(id: String): Pair<Float, Float> =
        if (_state.value.party.any { it.id == id }) partyFloatSlot(id) else enemyFloatSlot(id)

    fun partyFloatSlot(memberId: String): Pair<Float, Float> {
        _combatantPositions[memberId]?.let { return it }
        val party = _state.value.party
        val idx = party.indexOfFirst { it.id == memberId }.coerceAtLeast(0)
        val total = party.size.coerceAtLeast(1)
        val spacing = (arenaHeight * 0.40f) / total
        val startY = (arenaHeight * 0.50f) - (total - 1) * spacing * 0.5f
        return (arenaWidth * 0.22f) to (startY + idx * spacing)
    }

    fun setPlayerInputPhaseForTesting(heroId: String = "hero") {
        val hero = _state.value.party.firstOrNull { it.id == heroId } ?: _state.value.party.firstOrNull()
        _state.value = _state.value.copy(
            phase = CombatPhase.PLAYER_INPUT,
            activePartyMemberId = hero?.id,
            party = _state.value.party.map {
                if (it.id == hero?.id) it.copy(atbGauge = 1.0f) else it
            }
        )
    }

    fun setPartyMemberAtbForTesting(memberId: String, gauge: Float) {
        _state.value = _state.value.copy(
            party = _state.value.party.map {
                if (it.id == memberId) it.copy(atbGauge = gauge) else it
            }
        )
    }

    fun setPartyForTesting(party: List<PartyMember>) {
        _state.value = _state.value.copy(party = party)
    }

    private var atbJob: Job? = null
    private var conclusionTransitionJob: Job? = null
    private var conclusionWatchdogJob: Job? = null
    private var lastActedHeroId: String? = null
    var lastActedFaction: CombatantFaction = CombatantFaction.NONE
        internal set
    private var turnsTakenInRound: Int = 0

    internal fun registerTurnCompleted() {
        turnsTakenInRound++
        val aliveCount = _state.value.party.count { it.isAlive } + _state.value.enemies.count { it.isAlive }
        if (aliveCount > 0 && turnsTakenInRound >= aliveCount) {
            turnsTakenInRound = 0
            _state.value = _state.value.copy(roundNumber = _state.value.roundNumber + 1)
        }
    }

    private fun createInitialState(): CombatState {
        return CombatState(
            phase = CombatPhase.INACTIVE,
            party = GameContent.createDuoParty(),
            enemies = emptyList()
        )
    }

    private fun startAtbLoop() {
        atbJob?.cancel()
        atbJob = activeScope.launch {
            while (true) {
                delay(40)
                if (_state.value.phase == CombatPhase.ATB_WAITING) {
                    if (combatNarrator.isEyesFreeMode.value && combatNarrator.isSpeaking.value) {
                        continue
                    }
                    tickAtb()
                }
            }
        }
    }

    internal fun tickAtb() {
        if (_state.value.phase != CombatPhase.ATB_WAITING || _state.value.enemies.isEmpty()) {
            return
        }
        // 1. Advance gauges strictly for ALIVE combatants (atomic recompute-from-current:
        // mid-tick summons/attacks can never be lost to a stale snapshot write).
        // Statuses modulate ATB speed (chill slows, root nearly halts, overload crawls, bless quickens).
        val livingHeroes = _state.value.party.count { it.isAlive }
        val enemyPartyScale = when (livingHeroes) {
            4 -> 1.30f
            3 -> 1.15f
            else -> 1.0f
        }

        _state.update { prev ->
            prev.copy(
                party = prev.party.map { member ->
                    if (member.isAlive) {
                        val advance = (member.speed / 100f) * 0.035f * StatusSystem.speedMult(member.statuses)
                        member.copy(atbGauge = (member.atbGauge + advance).coerceAtMost(1.0f))
                    } else {
                        member.copy(atbGauge = 0f, currentMp = 0, stance = CharacterStance.DEAD)
                    }
                },
                enemies = prev.enemies.map { enemy ->
                    if (enemy.isAlive) {
                        val advance = (enemy.speed / 100f) * 0.035f * enemyPartyScale * StatusSystem.speedMult(enemy.statuses) * ENEMY_SPEED_PACING_MULT
                        enemy.copy(atbGauge = (enemy.atbGauge + advance).coerceAtMost(1.0f))
                    } else {
                        enemy.copy(atbGauge = 0f)
                    }
                }
            )
        }

        planEnemyMoves()

        val freshParty = _state.value.party
        val freshEnemies = _state.value.enemies

        // 2. Check if ANY combatant reached 100% turn readiness
        val readyHeroes = freshParty.filter { it.isAlive && it.isTurnReady }
        val readyEnemies = freshEnemies.filter { it.isAlive && it.isTurnReady }

        if (readyHeroes.isEmpty() && readyEnemies.isEmpty()) {
            return
        }

        // Compare highest readiness between heroes and enemies
        val topHero = readyHeroes.maxByOrNull { it.atbGauge }
        val topEnemy = readyEnemies.maxByOrNull { it.atbGauge }

        val isHeroTurn = when {
            topHero != null && topEnemy == null -> true
            topHero == null && topEnemy != null -> false
            topHero != null && topEnemy != null -> {
                // Interleave factions when both are ready to prevent starvation
                if (lastActedFaction == CombatantFaction.HERO) false
                else if (lastActedFaction == CombatantFaction.ENEMY) true
                else topHero.speed >= topEnemy.speed
            }
            else -> false
        }

        if (isHeroTurn && topHero != null) {
            lastActedFaction = CombatantFaction.HERO
            // HERO TURN: PAUSE EVERYTHING until player completes incantation
            val lastIdx = readyHeroes.indexOfFirst { it.id == lastActedHeroId }
            val readyHero = if (lastIdx != -1 && readyHeroes.size > 1) {
                readyHeroes[(lastIdx + 1) % readyHeroes.size]
            } else {
                topHero
            }

            // Begin-of-turn status processing (DoT ticks, stun skip). Returns true if the turn was consumed.
            if (beginPartyMemberTurn(readyHero.id)) return

            _state.value = _state.value.copy(
                phase = CombatPhase.PLAYER_INPUT,
                activePartyMemberId = readyHero.id,
                party = freshParty.map {
                    if (it.id == readyHero.id) it.copy(stance = CharacterStance.READY) else it
                }
            )
            if (_state.value.isPureStoryMode) {
                activeScope.launch {
                    delay(700)
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && _state.value.isPureStoryMode) {
                        triggerAutoHeroAction(readyHero)
                    }
                }
            }
            combatNarrator.narratePlayerTurn(readyHero, freshEnemies) {
                if (_state.value.isPureStoryMode) {
                    activeScope.launch {
                        delay(200)
                        if (_state.value.phase == CombatPhase.PLAYER_INPUT && _state.value.isPureStoryMode) {
                            triggerAutoHeroAction(readyHero)
                        }
                    }
                } else if (speechManager.isAutoListen.value) {
                    relistenAfter(100)
                }
            }
        } else if (topEnemy != null) {
            lastActedFaction = CombatantFaction.ENEMY
            // ENEMY TURN: PAUSE EVERYTHING until enemy executes attack
            executeSingleEnemyAttack(topEnemy)
        }
    }

    private fun movesetOf(enemy: Enemy): Moveset = MovesetTable.movesetFor(enemy.movesetId) ?: defaultMovesetFor(enemy)

    /**
     * Telegraphs (M4): an enemy whose gauge passes [PLAN_AT_GAUGE] picks its next move now and shows it.
     * A planned boss ultimate also starts its charge turns.
     */
    private fun planEnemyMoves() {
        val toPlan = _state.value.enemies.filter { it.isAlive && it.plannedMove == null && it.atbGauge >= PLAN_AT_GAUGE }
        if (toPlan.isEmpty()) return
        val chargeTurns = GameContent.presentation.telegraph.bossUltimateChargeTurns
        val plans = toPlan.associate { enemy ->
            enemy.id to EnemyBrain.pickMove(movesetOf(enemy), enemy.hpRatio, enemy.moveCooldowns, random)
        }
        _state.update { prev ->
            prev.copy(enemies = prev.enemies.map { e ->
                val move = plans[e.id] ?: return@map e
                e.copy(plannedMove = move.name, ultimateChargeLeft = if (Intent.isUltimate(e.isBoss, move)) chargeTurns else 0)
            })
        }
        for (enemy in toPlan) {
            val move = plans.getValue(enemy.id)
            val intent = Intent.of(move, Intent.isUltimate(enemy.isBoss, move))
            cue(BattleCue.Telegraph(enemy.id, move.name, intent, move.intentIcon ?: intent.icon))
            if (enemy.taunts.isNotEmpty() && random.nextFloat() < TAUNT_CHANCE) {
                val line = enemy.taunts[random.nextInt(enemy.taunts.size)]
                cue(BattleCue.Taunt(enemy.id, line))
                if (combatNarrator.isEyesFreeMode.value) combatNarrator.speak("${enemy.name}: $line", force = false)
            }
        }
    }

    /**
     * Begin-of-turn effects for a party member: DoT ticks, then stun skip.
     * @return true if this member's turn was consumed (skipped or they fell), false if they may act.
     */
    private fun beginPartyMemberTurn(heroId: String): Boolean {
        val member = _state.value.party.firstOrNull { it.id == heroId } ?: return false
        if (member.statuses.isEmpty()) return false

        val wasStunned = StatusSystem.isStunned(member.statuses)
        val (dot, updated) = StatusSystem.advanceTurn(member.statuses)
        var fell = false

        if (dot > 0) {
            val newHp = (member.currentHp - dot).coerceAtLeast(0)
            fell = newHp <= 0
            val aliveBefore = aliveIds()
            _state.value = _state.value.copy(
                party = _state.value.party.map {
                    if (it.id == heroId) it.copy(
                        currentHp = newHp,
                        statuses = updated,
                        stance = if (fell) CharacterStance.DEAD else it.stance,
                        atbGauge = if (fell) 0f else it.atbGauge
                    ) else it
                }
            )
            cue(BattleCue.Impact(heroId, fromParty = false, hits = listOf(BattleCue.Hit(heroId, dot)), isTick = true))
            announceFallen(aliveBefore)
            combatNarrator.speak("${member.name} suffers $dot damage from ongoing effects.", force = false)
        } else {
            _state.value = _state.value.copy(
                party = _state.value.party.map { if (it.id == heroId) it.copy(statuses = updated) else it }
            )
        }

        if (fell) {
            checkAndTransitionNextTurn()
            return true
        }
        if (wasStunned) {
            _state.value = _state.value.copy(
                party = _state.value.party.map { if (it.id == heroId) it.copy(atbGauge = 0f) else it },
                activePartyMemberId = null
            )
            val stunStatus = member.statuses.firstOrNull { it.status == StatusId.OVERLOAD || it.status == StatusId.FREEZE }
            val stunDesc = when (stunStatus?.status) {
                StatusId.FREEZE -> "is frozen solid"
                else -> "is stunned"
            }
            combatNarrator.speak("${member.name} $stunDesc and cannot act!", force = true)
            lastActedFaction = CombatantFaction.HERO
            lastActedHeroId = heroId
            registerTurnCompleted()
            checkAndTransitionNextTurn()
            return true
        }
        return false
    }

    private fun executeSingleEnemyAttack(enemy: Enemy) {
        activeScope.launch {
            // 1. Immediately pause everything in ENEMY_ACTIONS phase
            _state.value = _state.value.copy(
                phase = CombatPhase.ENEMY_ACTIONS,
                activePartyMemberId = null
            )
            speechManager.cancel()

            val mortalHeroes = _state.value.party.filter { it.isAlive && !it.isGhost }
            if (mortalHeroes.isEmpty()) {
                concludeDefeat()
                return@launch
            }
            val aliveHeroes = _state.value.party.filter { it.isAlive && !it.isGhost && !it.isUntargetable }.ifEmpty { mortalHeroes }

            // 2. Begin-of-enemy-turn status effects: DoT ticks and stun/bind skips.
            val livingSelf = _state.value.enemies.firstOrNull { it.id == enemy.id } ?: return@launch
            if (livingSelf.statuses.isNotEmpty()) {
                val wasStunned = StatusSystem.isStunned(livingSelf.statuses)
                val (dot, advanced) = StatusSystem.advanceTurn(livingSelf.statuses)
                val selfNewHp = (livingSelf.currentHp - dot).coerceAtLeast(0)
                val died = dot > 0 && selfNewHp <= 0
                if (dot > 0) {
                    val aliveBefore = aliveIds()
                    _state.value = _state.value.copy(
                        enemies = _state.value.enemies.map {
                            if (it.id == enemy.id) it.copy(currentHp = selfNewHp, atbGauge = if (died) 0f else it.atbGauge, statuses = advanced) else it
                        }
                    )
                    cue(BattleCue.Impact(enemy.id, fromParty = true, hits = listOf(BattleCue.Hit(enemy.id, dot)), isTick = true))
                    announceFallen(aliveBefore)
                    combatNarrator.speak("${enemy.name} suffers $dot damage from ongoing effects.", force = false)
                } else {
                    _state.value = _state.value.copy(
                        enemies = _state.value.enemies.map { if (it.id == enemy.id) it.copy(statuses = advanced) else it }
                    )
                }
                if (died) {
                    if (_state.value.enemies.none { it.isAlive }) {
                        concludeVictory()
                        return@launch
                    }
                    checkAndTransitionNextTurn()
                    return@launch
                }
                if (wasStunned) {
                    _state.value = _state.value.copy(
                        enemies = _state.value.enemies.map { if (it.id == enemy.id) it.copy(atbGauge = 0f) else it }
                    )
                    val stunStatus = livingSelf.statuses.firstOrNull { it.status == StatusId.OVERLOAD || it.status == StatusId.FREEZE }
                    val stunDesc = when (stunStatus?.status) {
                        StatusId.FREEZE -> "is frozen solid"
                        else -> "is stunned"
                    }
                    combatNarrator.speak("${enemy.name} $stunDesc and cannot act!", force = true)
                    lastActedFaction = CombatantFaction.ENEMY
                    registerTurnCompleted()
                    checkAndTransitionNextTurn()
                    return@launch
                }
            }

            // 3. Choose a move from the enemy's moveset (weighted, cooldown + boss-phase gated).
            val refreshedSelf = _state.value.enemies.firstOrNull { it.id == enemy.id } ?: return@launch
            val moveset = MovesetTable.movesetFor(refreshedSelf.movesetId) ?: defaultMovesetFor(refreshedSelf)
            val phase = EnemyBrain.phaseAnnouncement(moveset, enemy.hpRatio, refreshedSelf.hpRatio)
            if (phase != null && phase.note.isNotBlank()) {
                val (px, py) = enemyFloatSlot(enemy.id)
                val phaseFct = FloatingCombatText(text = phase.note, color = Color(0xFFFFD700), startX = px, startY = py, isCrit = true)
                _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts + phaseFct)
                activeScope.launch {
                    delay(1500)
                    _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filterNot { f -> f.id == phaseFct.id })
                }
                combatNarrator.speak("${enemy.name} changes tactics! ${phase.note}", force = true)
                if (enemy.isBoss) {
                    bossPhaseShifted = true
                    evaluateIntensity()
                }
                beat(BattleCue.PhaseShift(enemy.id, phase.note))
            }
            // The move it telegraphed at 75% (re-picked only if it can no longer be used).
            val planned = refreshedSelf.plannedMove?.let { name ->
                EnemyBrain.availableMoves(moveset, refreshedSelf.hpRatio, refreshedSelf.moveCooldowns).firstOrNull { it.name == name }
            }
            val move = planned ?: EnemyBrain.pickMove(moveset, refreshedSelf.hpRatio, refreshedSelf.moveCooldowns, random)
            val isBossUltimate = Intent.isUltimate(enemy.isBoss, move)

            // A planned boss ultimate spends its charge turns first: a red bar fills and the narrator warns.
            if (isBossUltimate && planned != null && refreshedSelf.ultimateChargeLeft > 0) {
                val left = refreshedSelf.ultimateChargeLeft - 1
                _state.update { prev ->
                    prev.copy(enemies = prev.enemies.map { if (it.id == enemy.id) it.copy(atbGauge = 0f, ultimateChargeLeft = left) else it })
                }
                cue(BattleCue.UltimateCharge(enemy.id, move.name, turnsLeft = left,
                    totalTurns = GameContent.presentation.telegraph.bossUltimateChargeTurns.coerceAtLeast(1)))
                combatNarrator.speak("${enemy.name} gathers power for ${move.name}!", force = true)
                waitForPocketNarration()
                finishEnemyTurn(enemy.id)
                return@launch
            }
            _state.update { prev ->
                prev.copy(enemies = prev.enemies.map { if (it.id == enemy.id) it.copy(plannedMove = null, ultimateChargeLeft = 0) else it })
            }

            val attacks = move.kind != "SUMMON" && move.kind != "BUFF"
            val moveCue = BattleCue.EnemyMove(
                enemyId = enemy.id,
                moveName = move.name,
                kind = move.kind,
                isBossUltimate = isBossUltimate,
                school = runCatching { SpellSchool.valueOf(move.school) }.getOrDefault(SpellSchool.PHYSICAL),
                windupMs = if (attacks) timing.windupMs(move.windupMs, isBossUltimate) else 0L
            )
            cue(moveCue)
            if (move.showsTitleCard) cue(BattleCue.TitleCard(move.name, fromParty = false))
            if (isBossUltimate) enemyCutIn(refreshedSelf)?.let { playCutIn(it) }

            // SUMMON moves spawn codex-true minions instead of attacking.
            if (move.kind == "SUMMON") {
                val updatedEnemies = _state.value.enemies.map {
                    if (it.id == enemy.id) it.copy(atbGauge = 0f, moveCooldowns = EnemyBrain.advanceCooldowns(it.moveCooldowns, move)) else it
                }
                _state.value = _state.value.copy(enemies = updatedEnemies)
                val minions = (0 until move.summonCount.coerceAtLeast(1)).map { i ->
                    GameContent.createMinion(
                        idSuffix = "${System.currentTimeMillis() % 1000}_$i",
                        name = move.summon ?: "Blighted Minion",
                        subtitle = "Minion",
                        hp = if (enemy.isBoss) 160 else 130
                    )
                }
                summonReinforcements(minions)
                waitForPocketNarration()
                hold(moveCue)
                finishEnemyTurn(enemy.id)
                return@launch
            }

            // 4. Self-buffs deal no damage.
            if (move.kind == "BUFF") {
                _state.value = _state.value.copy(
                    enemies = _state.value.enemies.map {
                        if (it.id == enemy.id) it.copy(
                            atbGauge = 0f,
                            moveCooldowns = EnemyBrain.advanceCooldowns(it.moveCooldowns, move),
                            statuses = StatusSystem.merge(it.statuses, StatusSystem.apply(StatusId.fromNameOrNull(move.applyStatus) ?: StatusId.BLESS, "ADEPT", familyOf(it), 0f))
                        ) else it
                    }
                )
                combatNarrator.speak("${enemy.name} uses ${move.name}!", force = false)
                hold(moveCue)
                finishEnemyTurn(enemy.id)
                return@launch
            }

            // 5. Resolve targets (taunt from guarding members overrides the move's rule).
            val guardTaunter = aliveHeroes.firstOrNull { it.isGuarding }
            val targetList: List<PartyMember> = when {
                guardTaunter != null -> listOf(guardTaunter)
                move.kind == "AOE" || move.targetRule == "PARTY_AOE" -> aliveHeroes
                move.targetRule == "SINGLE_LOWEST_HP" -> listOfNotNull(aliveHeroes.minByOrNull { it.hpRatio })
                else -> listOf(aliveHeroes.random())
            }

            if (isBossUltimate) {
                combatNarrator.speak("${enemy.name} unleashes their ultimate: ${move.name}!", force = true)
                val (ex, ey) = enemyFloatSlot(enemy.id)
                val ultFct = FloatingCombatText(
                    text = "BOSS ULTIMATE: ${move.name.uppercase()}",
                    color = Color(0xFFFF5252),
                    startX = ex - 40f,
                    startY = ey - 30f,
                    isCrit = true
                )
                _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts + ultFct)
                activeScope.launch {
                    delay(1800)
                    _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filterNot { it.id == ultFct.id })
                }
            }

            val weakenedMult = StatusSystem.attackDebuffMult(refreshedSelf.statuses)
            val moveSchool = runCatching { SpellSchool.valueOf(move.school) }.getOrDefault(SpellSchool.PHYSICAL)
            val enemyGraphics = move.graphics

            // 6. Wind-up, then the move flies (one projectile per targeted hero) and lands.
            hold(moveCue)
            beat(
                BattleCue.Release(
                    casterId = enemy.id,
                    fromParty = false,
                    targetIds = targetList.map { it.id },
                    school = moveSchool,
                    style = BattleCue.ReleaseStyle.PROJECTILE,
                    particleCount = ENEMY_IMPACT_PARTICLES,
                    graphics = enemyGraphics,
                    travelMs = timing.travelMs(fromParty = false, isBossUltimate = isBossUltimate)
                )
            )
            sfxManager.playImpact(moveSchool)
            sfxManager.playSoundBite(move.soundBites.ifEmpty { refreshedSelf.soundBites })
            val moveShake = enemyGraphics?.screenShake ?: 0f

            val moveStatusId = StatusId.fromNameOrNull(move.applyStatus)
            val hits = mutableListOf<BattleCue.Hit>()
            var totalDamage = 0
            var anyFallen = false
            var struckHeroName: String? = null

            val aoeStunTargetId = if (moveStatusId == StatusId.OVERLOAD || moveStatusId == StatusId.FREEZE) {
                targetList.filter { it.isAlive }.randomOrNull()?.id
            } else null

            val updatedParty = _state.value.party.map { hero ->
                if (targetList.any { it.id == hero.id } && hero.isAlive && !hero.isGhost && !hero.isUntargetable) {
                    val blessMitigation = 1f / StatusSystem.defenseMult(hero.statuses).coerceAtLeast(1f)
                    val damage = DamageResolver.resolveEnemyStrike(
                        enemyAttack = enemy.baseAttack,
                        movePowerMult = move.powerMult * weakenedMult,
                        defenderGuarding = hero.isGuarding,
                        defenderStatusMitigation = blessMitigation.coerceIn(0.5f, 1.25f),
                        random = random
                    )
                    totalDamage += damage
                    hits += BattleCue.Hit(hero.id, damage)
                    val newHp = (hero.currentHp - damage).coerceAtLeast(0)
                    val isFallen = newHp <= 0
                    anyFallen = anyFallen || isFallen
                    struckHeroName = hero.name
                    val landedStatus = if (moveStatusId != null) {
                        if (move.targetRule == "PARTY_AOE" && (moveStatusId == StatusId.OVERLOAD || moveStatusId == StatusId.FREEZE)) {
                            if (hero.id == aoeStunTargetId) {
                                StatusSystem.apply(moveStatusId, "ADEPT", EnemyFamily.FLESH, damage.toFloat())
                            } else null
                        } else {
                            StatusSystem.apply(moveStatusId, "ADEPT", EnemyFamily.FLESH, damage.toFloat())
                        }
                    } else null
                    hero.copy(
                        currentHp = newHp,
                        currentMp = if (isFallen) 0 else hero.currentMp,
                        atbGauge = if (isFallen) 0f else hero.atbGauge,
                        stance = if (isFallen) CharacterStance.DEAD else CharacterStance.DAMAGED,
                        isGuarding = false,
                        statuses = StatusSystem.merge(hero.statuses, landedStatus)
                    )
                } else hero
            }

            // DRAIN moves convert half the damage dealt into enemy healing.
            val healedHp = if (move.kind == "DRAIN") (totalDamage / 2).coerceAtMost(refreshedSelf.maxHp - refreshedSelf.currentHp) else 0

            val updatedEnemies = _state.value.enemies.map {
                if (it.id == enemy.id) it.copy(
                    atbGauge = 0f,
                    currentHp = (it.currentHp + healedHp).coerceAtMost(it.maxHp),
                    moveCooldowns = EnemyBrain.advanceCooldowns(it.moveCooldowns, move)
                ) else it
            }

            val aliveBefore = aliveIds()
            _state.value = _state.value.copy(
                enemies = updatedEnemies,
                party = updatedParty
            )
            val impact = BattleCue.Impact(
                sourceId = enemy.id,
                fromParty = false,
                hits = hits,
                shake = if (isBossUltimate) maxOf(18f, moveShake) else moveShake
            )
            cue(impact)
            announceFallen(aliveBefore)

            hold(impact)

            // Restore damaged hero to READY if still alive
            _state.value = _state.value.copy(
                party = _state.value.party.map {
                    if (it.isAlive && it.stance == CharacterStance.DAMAGED) it.copy(stance = CharacterStance.READY) else it
                }
            )

            // In Eyes-Free Pocket Mode, narrate enemy action and PAUSE until voice narration completes
            combatNarrator.narrateEnemyActionSuspend(
                enemyName = enemy.name,
                targetHeroName = struckHeroName ?: targetList.firstOrNull()?.name ?: "the fellowship",
                damage = totalDamage,
                isFallen = anyFallen,
                moveName = move.name,
                statusNote = moveStatusId?.let { StatusSystem.shortName(it) }
            )

            // Check defeat (excluding ethereal/ghost companions)
            if (_state.value.party.filter { !it.isGhost }.none { it.isAlive }) {
                concludeDefeat()
                return@launch
            }

            finishEnemyTurn(enemy.id)
        }
    }

    /** Ends an enemy's action: its turn-end beat, then the next combatant. */
    private suspend fun finishEnemyTurn(enemyId: String) {
        beat(BattleCue.TurnEnd(enemyId, fromParty = false))
        lastActedFaction = CombatantFaction.ENEMY
        registerTurnCompleted()
        checkAndTransitionNextTurn()
    }

    /** In pocket mode, let the narrator finish before the battle moves on (capped at 7 s). */
    private suspend fun waitForPocketNarration() {
        if (!combatNarrator.isEyesFreeMode.value) return
        val waitStart = System.currentTimeMillis()
        while (combatNarrator.isSpeaking.value && (System.currentTimeMillis() - waitStart < 7000L)) {
            delay(50)
        }
    }

    /** Family/role-derived fallback when an enemy has no codex moveset (never one-note). */
    private fun defaultMovesetFor(enemy: Enemy): Moveset {
        val roleWord = when {
            enemy.subtitle.contains("Sniper", true) || enemy.subtitle.contains("Assassin", true) -> "sniper"
            enemy.subtitle.contains("Occultist", true) || enemy.subtitle.contains("Caster", true) ||
                    enemy.subtitle.contains("Summoner", true) || enemy.subtitle.contains("Mystic", true) -> "caster"
            else -> "bruiser"
        }
        val key = "${enemy.family.lowercase()}_$roleWord"
        MovesetTable.movesetFor(key)?.let { return it }
        return Moveset(
            moves = listOf(
                MovesetTable.FALLBACK_BASIC.copy(name = "Attack"),
                MovesetTable.FALLBACK_BASIC.copy(name = "Heavy Blow", kind = "HEAVY", powerMult = 1.5f, targetRule = "SINGLE_LOWEST_HP", cooldownTurns = 3, weight = 2)
            )
        )
    }

    /** Shared victory conclusion: award XP, narrate, arm the continue/listen loop. */
    private fun concludeVictory() {
        conclusionTransitionJob?.cancel()
        conclusionWatchdogJob?.cancel()
        _state.value = _state.value.copy(phase = CombatPhase.BATTLE_WON)
        cue(BattleCue.BattleEnd(victory = true))
        val levelUpLines = awardVictoryXp()

        fun proceedToStory() {
            if (_state.value.phase == CombatPhase.BATTLE_WON && _state.value.isPureStoryMode) {
                pauseAtb()
                onContinueStory?.invoke()
            }
        }

        val fullVictoryText = if (!levelUpLines.isNullOrBlank()) {
            "$levelUpLines ${com.voicerpg.engine.content.GameContent.manifest.victoryLine}"
        } else {
            com.voicerpg.engine.content.GameContent.manifest.victoryLine
        }
        if (_state.value.isPureStoryMode) {
            val wordCount = fullVictoryText.split(Regex("\\s+")).count { it.isNotBlank() }
            val estimatedDurationMs = maxOf(4000L, ((wordCount / 2.0f) * 1000L).toLong() + 3000L)
            val watchdogTimeoutMs = estimatedDurationMs + 6000L
            conclusionWatchdogJob = activeScope.launch {
                delay(watchdogTimeoutMs)
                if (_state.value.phase == CombatPhase.BATTLE_WON && _state.value.isPureStoryMode) {
                    proceedToStory()
                }
            }
        }

        combatNarrator.narrateConclusion(
            isVictory = true,
            extraPrefix = levelUpLines,
            force = _state.value.isPureStoryMode
        ) {
            conclusionWatchdogJob?.cancel()
            conclusionWatchdogJob = null
            if (_state.value.isPureStoryMode) {
                conclusionTransitionJob?.cancel()
                conclusionTransitionJob = activeScope.launch {
                    delay(1500L)
                    proceedToStory()
                }
            } else if (speechManager.isAutoListen.value) {
                relistenAfter(150)
            }
        }
    }

    private fun concludeDefeat() {
        conclusionTransitionJob?.cancel()
        conclusionWatchdogJob?.cancel()
        _state.value = _state.value.copy(phase = CombatPhase.BATTLE_LOST)
        cue(BattleCue.BattleEnd(victory = false))

        fun proceedToStory() {
            if (_state.value.phase == CombatPhase.BATTLE_LOST && _state.value.isPureStoryMode) {
                pauseAtb()
                onContinueStory?.invoke()
            }
        }

        val fullDefeatText = com.voicerpg.engine.content.GameContent.manifest.defeatLine
        if (_state.value.isPureStoryMode) {
            val wordCount = fullDefeatText.split(Regex("\\s+")).count { it.isNotBlank() }
            val estimatedDurationMs = maxOf(4000L, ((wordCount / 2.0f) * 1000L).toLong() + 3000L)
            val watchdogTimeoutMs = estimatedDurationMs + 6000L
            conclusionWatchdogJob = activeScope.launch {
                delay(watchdogTimeoutMs)
                if (_state.value.phase == CombatPhase.BATTLE_LOST && _state.value.isPureStoryMode) {
                    proceedToStory()
                }
            }
        }

        combatNarrator.narrateConclusion(
            isVictory = false,
            force = _state.value.isPureStoryMode
        ) {
            conclusionWatchdogJob?.cancel()
            conclusionWatchdogJob = null
            if (_state.value.isPureStoryMode) {
                conclusionTransitionJob?.cancel()
                conclusionTransitionJob = activeScope.launch {
                    delay(1500L)
                    proceedToStory()
                }
            } else if (speechManager.isAutoListen.value) {
                relistenAfter(150)
            }
        }
    }

    private fun awardVictoryXp(): String? {
        val party = _state.value.party
        if (party.isEmpty()) return null
        val totalXp = _state.value.enemies.filterNot { it.isAlive }.sumOf { it.xpReward } +
                if (_state.value.enemies.any { it.isBoss }) 300 else 0
        val roster = party.map {
            ProgressMember(
                id = it.id,
                classKey = Progression.classKey(it.id, it.loreClass),
                level = it.level,
                xp = it.xp,
                alive = it.isAlive
            )
        }
        val outcomes = Progression.awardXp(roster, totalXp)
        lastVictoryOutcomes = outcomes
        if (outcomes.isEmpty()) return null
        _state.value = _state.value.copy(
            party = _state.value.party.map { hero ->
                val o = outcomes.firstOrNull { it.memberId == hero.id } ?: return@map hero
                if (o.levelsGained == 0) hero
                else hero.copy(
                    level = o.newLevel,
                    xp = o.newXp,
                    maxHp = hero.maxHp + o.dMaxHp,
                    currentHp = hero.currentHp + o.dMaxHp,
                    maxMp = hero.maxMp + o.dMaxMp,
                    currentMp = hero.currentMp + o.dMaxMp,
                    speed = hero.speed + o.dSpeed,
                    defense = hero.defense + o.dDefense
                )
            }
        )
        val levelUps = outcomes.filter { it.levelsGained > 0 }
        if (levelUps.isNotEmpty()) {
            val lines = levelUps.joinToString(" ") { "${it.memberId} ascends to level ${it.newLevel}!" }
            levelUps.forEach { o ->
                val hero = _state.value.party.firstOrNull { it.id == o.memberId }
                val (lx, ly) = partyFloatSlot(o.memberId)
                val fct = FloatingCombatText(text = "LEVEL ${hero?.level ?: o.newLevel}!", color = Color(0xFFFFD700), startX = lx, startY = ly, isCrit = true)
                _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts + fct)
                activeScope.launch {
                    delay(1600)
                    _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filterNot { f -> f.id == fct.id })
                }
            }
            return lines
        }
        return null
    }

    private fun checkAndTransitionNextTurn() {
        activeScope.launch {
            if (combatNarrator.isEyesFreeMode.value && combatNarrator.isSpeaking.value) {
                val waitStart = System.currentTimeMillis()
                while (combatNarrator.isSpeaking.value && (System.currentTimeMillis() - waitStart < 7000L)) {
                    delay(50)
                }
                delay(200)
            }
            performTurnTransition()
        }
    }

    private fun performTurnTransition() {
        proceedAfterTurnCheck(_state.value.party, _state.value.enemies)
    }

    private fun proceedAfterTurnCheck(currentParty: List<PartyMember>, currentEnemies: List<Enemy>) {
        val readyHeroes = currentParty.filter { it.isAlive && it.isTurnReady }
        val readyEnemies = currentEnemies.filter { it.isAlive && it.isTurnReady }

        val shouldEnemyAct = when {
            readyEnemies.isEmpty() -> false
            readyHeroes.isEmpty() -> true
            lastActedFaction == CombatantFaction.HERO -> true // Hero acted last; interleave to ready enemy!
            lastActedFaction == CombatantFaction.ENEMY -> false // Enemy acted last; interleave to ready hero!
            else -> {
                val maxHeroSpeed = readyHeroes.maxOfOrNull { it.speed } ?: 0
                val maxEnemySpeed = readyEnemies.maxOfOrNull { it.speed } ?: 0
                maxEnemySpeed > maxHeroSpeed
            }
        }

        if (shouldEnemyAct) {
            val nextEnemy = readyEnemies.maxByOrNull { it.atbGauge } ?: readyEnemies.first()
            lastActedFaction = CombatantFaction.ENEMY
            executeSingleEnemyAttack(nextEnemy)
        } else if (readyHeroes.isNotEmpty()) {
            val lastIdx = readyHeroes.indexOfFirst { it.id == lastActedHeroId }
            val nextHero = if (lastIdx != -1 && readyHeroes.size > 1) {
                readyHeroes[(lastIdx + 1) % readyHeroes.size]
            } else {
                readyHeroes.first()
            }
            lastActedFaction = CombatantFaction.HERO
            if (beginPartyMemberTurn(nextHero.id)) return
            val refreshedParty = _state.value.party
            _state.value = _state.value.copy(
                phase = CombatPhase.PLAYER_INPUT,
                activePartyMemberId = nextHero.id,
                party = refreshedParty.map {
                    if (it.id == nextHero.id) it.copy(stance = CharacterStance.READY) else it
                }
            )
            if (_state.value.isPureStoryMode) {
                activeScope.launch {
                    delay(700)
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && _state.value.isPureStoryMode) {
                        triggerAutoHeroAction(nextHero)
                    }
                }
            }
            combatNarrator.narratePlayerTurn(nextHero, currentEnemies) {
                if (_state.value.isPureStoryMode) {
                    activeScope.launch {
                        delay(200)
                        if (_state.value.phase == CombatPhase.PLAYER_INPUT && _state.value.isPureStoryMode) {
                            triggerAutoHeroAction(nextHero)
                        }
                    }
                } else if (speechManager.isAutoListen.value) {
                    relistenAfter(100)
                }
            }
        } else {
            // Nobody ready yet — resume advancing turn gauges
            _state.value = _state.value.copy(phase = CombatPhase.ATB_WAITING)
        }
    }

    fun selectEnemy(enemy: Enemy) {
        _state.value = _state.value.copy(
            enemies = _state.value.enemies.map { it.copy(isTargeted = it.id == enemy.id) }
        )
    }

    fun selectPartyMember(member: PartyMember) {
        if (!member.isAlive) return
        if (_state.value.phase != CombatPhase.PLAYER_INPUT) return

        if (member.isTurnReady) {
            _state.value = _state.value.copy(
                activePartyMemberId = member.id,
                party = _state.value.party.map {
                    if (it.id == member.id) it.copy(stance = CharacterStance.READY) else it
                }
            )
        } else {
            // Visual feedback showing current ATB charge level
            val pct = (member.atbRatio * 100).toInt()
            val fct = FloatingCombatText(
                text = "${member.name} charging: $pct%",
                color = Color(0xFF80D8FF),
                startX = 260f,
                startY = 420f
            )
            _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts + fct)
            activeScope.launch {
                delay(1000)
                _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filter { it.id != fct.id })
            }
        }
    }

    fun cycleNextPartyMember() {
        if (_state.value.phase != CombatPhase.PLAYER_INPUT) return
        val readyHeroes = _state.value.party.filter { it.isAlive && it.isTurnReady }
        if (readyHeroes.size <= 1) {
            val fct = FloatingCombatText(
                text = "No other heroes ready!",
                color = Color(0xFFFFD54F),
                startX = 260f,
                startY = 420f
            )
            _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts + fct)
            activeScope.launch {
                delay(900)
                _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filter { it.id != fct.id })
            }
            return
        }
        val currentId = _state.value.activePartyMemberId
        val currentIndex = readyHeroes.indexOfFirst { it.id == currentId }
        val nextHero = if (currentIndex != -1) {
            readyHeroes[(currentIndex + 1) % readyHeroes.size]
        } else {
            readyHeroes.first()
        }
        _state.value = _state.value.copy(
            activePartyMemberId = nextHero.id,
            party = _state.value.party.map {
                if (it.id == nextHero.id) it.copy(stance = CharacterStance.READY) else it
            }
        )
    }

    fun defendActivePartyMember() {
        val activeHero = _state.value.activePartyMember ?: return
        if (_state.value.phase != CombatPhase.PLAYER_INPUT) return

        activeScope.launch {
            speechManager.cancel()
            val (dx, dy) = partyFloatSlot(activeHero.id)
            val fct = FloatingCombatText(
                text = "${activeHero.name} Guards! (-50% incoming)",
                color = Color(0xFF64B5F6),
                startX = dx,
                startY = dy
            )
            lastActedHeroId = activeHero.id
            _state.value = _state.value.copy(
                party = _state.value.party.map {
                    if (it.id == activeHero.id) it.copy(atbGauge = 0.35f, stance = CharacterStance.READY, isGuarding = true) else it
                },
                activePartyMemberId = null,
                floatingTexts = _state.value.floatingTexts + fct
            )
            activeScope.launch {
                delay(1000)
                _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filter { it.id != fct.id })
            }
            cue(BattleCue.Guard(activeHero.id))
            if (combatNarrator.isEyesFreeMode.value) {
                combatNarrator.speakSuspend("${activeHero.name} braces and defends!", force = true)
            }
            hold(BattleCue.Guard(activeHero.id))
            beat(BattleCue.TurnEnd(activeHero.id, fromParty = true))
            lastActedFaction = CombatantFaction.HERO
            registerTurnCompleted()
            checkAndTransitionNextTurn()
        }
    }

    fun startVoiceListening() {
        speechManager.startListening(
            onStandby = {
                if (_state.value.phase == CombatPhase.PLAYER_INPUT) {
                    if (combatNarrator.isEyesFreeMode.value) {
                        combatNarrator.speak(
                            "Standing by. Tap the screen when you are ready to command.",
                            force = true
                        )
                    }
                }
            },
            onResult = { utterance ->
                processIncantation(utterance)
            }
        )
    }

    fun resumeVoiceListening() {
        if (_state.value.phase == CombatPhase.PLAYER_INPUT) {
            if (combatNarrator.isEyesFreeMode.value) {
                val heroName = _state.value.activePartyMember?.name ?: "Hero"
                combatNarrator.speak("$heroName is ready. Speak your command.", force = true) {
                    relistenAfter(100)
                }
            } else {
                startVoiceListening()
            }
        } else {
            startVoiceListening()
        }
    }

    /** Re-arm the microphone after [ms] (lets TTS release the audio route first). */
    private fun relistenAfter(ms: Long) {
        activeScope.launch {
            delay(ms)
            startVoiceListening()
        }
    }

    fun stopVoiceListening() {
        speechManager.stopListening()
    }

    fun submitTypedChant(text: String, forcedAcoustic: com.voicerpg.engine.model.AcousticProfile? = null) {
        speechManager.cancel()
        processIncantation(text, forcedAcoustic, typed = true)
    }

    fun toggleEyesFreeMode(): Boolean {
        val enabled = combatNarrator.toggleEyesFreeMode()
        _state.value = _state.value.copy(isEyesFreeMode = enabled)
        if (enabled) {
            speechManager.setAutoListen(true)
        }
        val text = if (enabled) "Eyes-free pocket mode enabled. Screen locked." else "Eyes-free pocket mode disabled."
        combatNarrator.speak(text, force = true) {
            if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                relistenAfter(100)
            }
        }
        return enabled
    }

    fun setEyesFreeMode(enabled: Boolean) {
        combatNarrator.setEyesFreeMode(enabled)
        _state.value = _state.value.copy(isEyesFreeMode = enabled)
    }

    fun setPureStoryMode(enabled: Boolean) {
        _state.value = _state.value.copy(isPureStoryMode = enabled)
    }

    fun triggerAutoHeroAction(hero: PartyMember): Spell? {
        if (_state.value.phase != CombatPhase.PLAYER_INPUT) return null
        val aliveEnemies = _state.value.enemies.filter { it.isAlive }

        val isSupportOnly = hero.isGhost || !hero.canAttack || hero.spells.none { !it.isHeal && !it.cleansesDebuffs && it.basePower > 0 }
        val nonHealSpells = hero.spells.filter { !it.isHeal && !it.cleansesDebuffs }
        val affordableSpells = nonHealSpells.filter { it.mpCost <= hero.currentMp }

        val chosenSpell = if (isSupportOnly) {
            val affordable = hero.spells.filter { it.mpCost <= hero.currentMp }
            val pool = if (affordable.isNotEmpty()) affordable else hero.spells
            val lowestAlly = _state.value.party.filter { it.isAlive && !it.isGhost }.minByOrNull { it.hpRatio }
            val debuffedAlly = _state.value.party.filter { it.isAlive && !it.isGhost }.firstOrNull { it.statuses.any { s -> s.isDebuffOnTarget } }

            when {
                // 1. If any living ally has debuffs and hero has a cleanse spell, prioritize cleansing
                debuffedAlly != null && pool.any { it.cleansesDebuffs || it.isCleanse } -> {
                    pool.firstOrNull { it.cleansesDebuffs || it.isCleanse }
                }
                // 2. If any living ally is injured and hero has a heal spell, prioritize healing
                lowestAlly != null && lowestAlly.hpRatio < 0.85f && pool.any { it.isHeal } -> {
                    pool.firstOrNull { it.isHeal }
                }
                // 3. Otherwise prefer guard, mana restore, heal, cleanse, or any available ability
                else -> {
                    pool.firstOrNull { it.isGuard || it.manaRestorePct > 0f }
                        ?: pool.firstOrNull { it.isHeal }
                        ?: pool.firstOrNull { it.cleansesDebuffs || it.isCleanse }
                        ?: pool.firstOrNull()
                }
            }
        } else if (aliveEnemies.size > 1) {
            // 1. Always prefer AoE damage spells when there is more than 1 enemy
            val affordableAoe = affordableSpells.filter { it.hitsAll && it.manaRestorePct == 0f && !it.isGuard }
            if (affordableAoe.isNotEmpty()) {
                affordableAoe.maxByOrNull { it.basePower } ?: affordableAoe.first()
            } else {
                val zeroCostAoe = nonHealSpells.filter { it.mpCost == 0 && it.hitsAll && it.manaRestorePct == 0f && !it.isGuard }
                if (zeroCostAoe.isNotEmpty()) {
                    zeroCostAoe.maxByOrNull { it.basePower } ?: zeroCostAoe.first()
                } else {
                    val offensive = affordableSpells.filter { it.manaRestorePct == 0f && !it.isGuard }
                    offensive.maxByOrNull { it.basePower }
                        ?: affordableSpells.firstOrNull { it.manaRestorePct > 0f }
                        ?: nonHealSpells.firstOrNull { it.mpCost == 0 }
                        ?: nonHealSpells.firstOrNull()
                }
            }
        } else {
            // Single enemy on field: use strongest offensive spell
            val offensive = affordableSpells.filter { it.manaRestorePct == 0f && !it.isGuard }
            offensive.maxByOrNull { it.basePower }
                ?: affordableSpells.firstOrNull { it.manaRestorePct > 0f }
                ?: nonHealSpells.firstOrNull { it.mpCost == 0 }
                ?: nonHealSpells.firstOrNull()
        }

        if (chosenSpell != null) {
            val aliveEnemy = _state.value.enemies.firstOrNull { it.isAlive && it.isTargeted }
                ?: _state.value.enemies.firstOrNull { it.isAlive }
            val chant = when {
                chosenSpell.cleansesDebuffs || chosenSpell.isCleanse -> {
                    val debuffedAlly = _state.value.party.filter { it.isAlive && !it.isGhost }.firstOrNull { it.statuses.any { s -> s.isDebuffOnTarget } }
                    if (chosenSpell.hitsAll) "${chosenSpell.name} all"
                    else if (debuffedAlly != null) "${chosenSpell.name} ${debuffedAlly.name}"
                    else "${chosenSpell.name} all"
                }
                chosenSpell.isHeal || chosenSpell.isGuard -> {
                    val lowestAlly = _state.value.party.filter { it.isAlive && !it.isGhost }.minByOrNull { it.hpRatio }
                    if (chosenSpell.hitsAll) "${chosenSpell.name} all"
                    else if (lowestAlly != null) "${chosenSpell.name} ${lowestAlly.name}"
                    else chosenSpell.name
                }
                chosenSpell.manaRestorePct > 0f -> "attune"
                chosenSpell.hitsAll -> "${chosenSpell.name} all"
                aliveEnemy != null -> "${chosenSpell.name} ${aliveEnemy.name}"
                else -> chosenSpell.name
            }
            submitTypedChant(chant)
        }
        return chosenSpell
    }

    fun openOptions() {
        _state.value = _state.value.copy(isOptionsOpen = true)
        combatNarrator.speak("Options open. Say Pocket Mode, Auto Listen, Speaker Names, Help, or Close Options.", force = _state.value.isEyesFreeMode) {
            if (speechManager.isAutoListen.value) {
                relistenAfter(100)
            }
        }
    }

    fun closeOptions() {
        _state.value = _state.value.copy(isOptionsOpen = false)
        combatNarrator.speak("Resuming battle.", force = _state.value.isEyesFreeMode) {
            if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                relistenAfter(100)
            }
        }
    }

    fun handleMetaCommand(command: MetaCommand) {
        when (command) {
            MetaCommand.STATUS_REPORT -> {
                combatNarrator.narrateStatus(_state.value.party, _state.value.enemies) {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
            }
            MetaCommand.CHECK_ENEMIES -> {
                combatNarrator.narrateEnemies(_state.value.enemies) {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
            }
            MetaCommand.CHECK_PARTY -> {
                combatNarrator.narrateParty(_state.value.party) {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
            }
            MetaCommand.TOGGLE_EYES_FREE -> {
                val enabled = toggleEyesFreeMode()
                val fct = FloatingCombatText(
                    text = if (enabled) "Eyes-Free Mode: ON 🎧" else "Eyes-Free Mode: OFF 📱",
                    color = Color(0xFF64B5F6),
                    startX = 500f,
                    startY = 400f
                )
                _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts + fct)
                activeScope.launch {
                    delay(1500)
                    _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filter { it.id != fct.id })
                }
            }
            MetaCommand.ENABLE_EYES_FREE -> {
                setEyesFreeMode(true)
                speechManager.setAutoListen(true)
                val text = "Eyes-free pocket mode enabled. Screen locked."
                combatNarrator.speak(text, force = true) {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
            }
            MetaCommand.DISABLE_EYES_FREE -> {
                setEyesFreeMode(false)
                combatNarrator.speak("Eyes-free pocket mode disabled.", force = true)
            }
            MetaCommand.UNLOCK_SCREEN -> {
                combatNarrator.unlockPocketGuard()
                combatNarrator.speak("Screen unlocked.", force = true) {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
            }
            MetaCommand.LOCK_SCREEN -> {
                if (!combatNarrator.isEyesFreeMode.value) {
                    setEyesFreeMode(true)
                    speechManager.setAutoListen(true)
                }
                combatNarrator.lockPocketGuard()
                combatNarrator.speak("Screen locked. Pocket mode active.", force = true) {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
            }
            MetaCommand.TOGGLE_AUTO_LISTEN -> {
                speechManager.toggleAutoListen()
                val enabled = speechManager.isAutoListen.value
                val status = if (enabled) "Auto listen enabled." else "Auto listen disabled."
                combatNarrator.speak(status, force = true) {
                    if (enabled && _state.value.phase == CombatPhase.PLAYER_INPUT) {
                        relistenAfter(100)
                    }
                }
                val fct = FloatingCombatText(
                    text = if (enabled) "Auto-Listen: ON 👂" else "Auto-Listen: OFF 🔇",
                    color = Color(0xFF81C784),
                    startX = 500f,
                    startY = 400f
                )
                _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts + fct)
                activeScope.launch {
                    delay(1500)
                    _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filter { it.id != fct.id })
                }
            }
            MetaCommand.TOGGLE_NARRATION -> {
                val enabled = combatNarrator.toggleNarration()
                val status = if (enabled) "Story narration enabled." else "Story narration muted."
                combatNarrator.speak(status, force = true) {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
                val fct = FloatingCombatText(
                    text = if (enabled) "Narration: ON 📖" else "Narration: OFF 🔇",
                    color = Color(0xFFFFD54F),
                    startX = 500f,
                    startY = 400f
                )
                _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts + fct)
                activeScope.launch {
                    delay(1500)
                    _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filter { it.id != fct.id })
                }
            }
            MetaCommand.TOGGLE_READ_CHOICES -> {
                val enabled = combatNarrator.toggleReadChoices()
                val status = if (enabled) "Choice reading enabled." else "Choice reading disabled."
                combatNarrator.speak(status, force = true) {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
                val fct = FloatingCombatText(
                    text = if (enabled) "Read Choices: ON 🔢" else "Read Choices: OFF 🔇",
                    color = Color(0xFF80D8FF),
                    startX = 500f,
                    startY = 400f
                )
                _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts + fct)
                activeScope.launch {
                    delay(1500)
                    _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filter { it.id != fct.id })
                }
            }
            MetaCommand.TOGGLE_SPEAKER_ATTRIBUTION -> {
                val enabled = combatNarrator.toggleSpeakerAttribution()
                val status = if (enabled) "Speaker names will be announced." else "Speaker names hidden. Voices remain distinct."
                combatNarrator.speak(status, force = true) {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
                val fct = FloatingCombatText(
                    text = if (enabled) "Speaker Names: ON 🗣️" else "Speaker Names: OFF 🗣️",
                    color = Color(0xFFFFB74D),
                    startX = 500f,
                    startY = 400f
                )
                _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts + fct)
                activeScope.launch {
                    delay(1500)
                    _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filter { it.id != fct.id })
                }
            }
            MetaCommand.TOGGLE_MUSIC -> {
                val enabled = musicManager?.toggleMusic() ?: true
                val status = if (enabled) "Background music enabled." else "Background music muted."
                combatNarrator.speak(status, force = true) {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
                val fct = FloatingCombatText(
                    text = if (enabled) "Music: ON 🎵" else "Music: OFF 🎵",
                    color = Color(0xFFFFD54F),
                    startX = 500f,
                    startY = 400f
                )
                _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts + fct)
                activeScope.launch {
                    delay(1500)
                    _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filter { it.id != fct.id })
                }
            }
            MetaCommand.OPEN_OPTIONS -> {
                openOptions()
            }
            MetaCommand.CLOSE_OPTIONS -> {
                closeOptions()
            }
            MetaCommand.HELP -> {
                combatNarrator.narrateHelp {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
            }
            MetaCommand.SPEED_UP_NARRATION -> {
                val current = combatNarrator.speechRate.value
                val next = when {
                    current < 0.95f -> 1.05f
                    current < 1.15f -> 1.25f
                    else -> 1.50f
                }
                combatNarrator.setSpeechRate(next)
                combatNarrator.speak("Narration speed increased to %.2fx.".format(next), force = true) {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
            }
            MetaCommand.SLOW_DOWN_NARRATION -> {
                val current = combatNarrator.speechRate.value
                val next = when {
                    current > 1.35f -> 1.25f
                    current > 1.15f -> 1.05f
                    else -> 0.85f
                }
                combatNarrator.setSpeechRate(next)
                combatNarrator.speak("Narration speed decreased to %.2fx.".format(next), force = true) {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
            }
            MetaCommand.RESET_NARRATION_SPEED -> {
                combatNarrator.setSpeechRate(1.05f)
                combatNarrator.speak("Narration speed reset to normal 1.05x.", force = true) {
                    if (_state.value.phase == CombatPhase.PLAYER_INPUT && speechManager.isAutoListen.value) {
                        relistenAfter(100)
                    }
                }
            }
            MetaCommand.OPEN_BACKLOG,
            MetaCommand.CLOSE_BACKLOG,
            MetaCommand.FAST_FORWARD,
            MetaCommand.STOP_FAST_FORWARD,
            MetaCommand.STORY_RECAP,
            MetaCommand.NONE -> Unit
        }
    }

    /** [typed]: the chant was typed (or chosen automatically), so it gets a short simulated charge. */
    fun processIncantation(utterance: String, forcedAcoustic: com.voicerpg.engine.model.AcousticProfile? = null, typed: Boolean = false) {
        val lower = utterance.lowercase().trim()
        if (lower.isBlank()) return

        // 0. Intercept Meta Voice Commands (Accessibility, Screenless / Pocket Mode, Status, Options)
        val peek = IntentParser.parse(utterance, emptyList(), _state.value.enemies, _state.value.party)
        if (peek.metaCommand != MetaCommand.NONE) {
            handleMetaCommand(peek.metaCommand)
            return
        }

        // 0b. Intercept Battle Victory / Defeat Voice Commands
        if (_state.value.phase == CombatPhase.BATTLE_WON || _state.value.phase == CombatPhase.BATTLE_LOST) {
            val conclusionAction = com.voicerpg.engine.engine.StoryChoiceMatcher.parseBattleConclusionIntent(utterance)
            when (conclusionAction) {
                com.voicerpg.engine.engine.StoryChoiceMatcher.ConclusionAction.CONTINUE_STORY -> {
                    speechManager.cancel()
                    combatNarrator.stop()
                    onContinueStory?.invoke()
                }
                com.voicerpg.engine.engine.StoryChoiceMatcher.ConclusionAction.RESTART_BATTLE -> {
                    speechManager.cancel()
                    restartBattle()
                }
                com.voicerpg.engine.engine.StoryChoiceMatcher.ConclusionAction.NONE -> {
                    if (speechManager.isAutoListen.value) {
                        activeScope.launch {
                            delay(200)
                            if (_state.value.phase == CombatPhase.BATTLE_WON || _state.value.phase == CombatPhase.BATTLE_LOST) {
                                startVoiceListening()
                            }
                        }
                    }
                }
            }
            return
        }

        if (_state.value.phase != CombatPhase.PLAYER_INPUT) return

        val aliveParty = _state.value.party.filter { it.isAlive }
        val activeHero = _state.value.activePartyMember ?: return
        if (!activeHero.isTurnReady) return
        if (_state.value.activePartyMemberId != activeHero.id) {
            _state.value = _state.value.copy(activePartyMemberId = activeHero.id)
        }

        // 1. Voice Hero Switch Commands
        if (lower == "switch" || lower == "next hero" || lower == "cycle hero" || lower == "switch hero" || lower == "change hero" || lower == "next") {
            cycleNextPartyMember()
            return
        }

        val isExplicitSwitch = lower.startsWith("switch to ") || lower.startsWith("change to ") ||
                lower.startsWith("select ") || lower.startsWith("choose ")
        if (isExplicitSwitch) {
            val switchTarget = aliveParty.firstOrNull { member ->
                val idMatch = member.id.isNotBlank() && lower.contains(member.id.lowercase())
                val nameMatch = member.name.isNotBlank() && (
                    lower.contains(member.name.lowercase()) ||
                    member.name.lowercase().split(" ").any { part -> part.length > 2 && lower.contains(part) }
                )
                val classMatch = member.loreClass.isNotBlank() && lower.contains(member.loreClass.lowercase())
                // Content-agnostic alias lookup: characters declare their spoken aliases in JSON
                // (e.g. the hero's file lists the aliases they can be addressed by). Never hardcode
                // campaign-specific names/titles in the engine.
                val aliasMatch = GameContent.voiceAliasesFor(member.id).any { alias ->
                    alias.isNotBlank() && lower.contains(alias.lowercase())
                }
                idMatch || nameMatch || classMatch || aliasMatch
            }
            if (switchTarget != null) {
                if (switchTarget.id == activeHero.id) {
                    combatNarrator.speak("Already commanding ${activeHero.name}.", force = false)
                    return
                }
                if (switchTarget.isTurnReady) {
                    selectPartyMember(switchTarget)
                    combatNarrator.speak("Switched to ${switchTarget.name}. Ready to act.", force = true) {
                        if (speechManager.isAutoListen.value) {
                            relistenAfter(100)
                        }
                    }
                } else {
                    val pct = (switchTarget.atbRatio * 100).toInt()
                    val (sx, sy) = partyFloatSlot(switchTarget.id)
                    val fct = FloatingCombatText(
                        text = "${switchTarget.name} charging: $pct%",
                        color = Color(0xFF80D8FF),
                        startX = sx,
                        startY = sy
                    )
                    _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts + fct)
                    activeScope.launch {
                        delay(1200)
                        _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filter { it.id != fct.id })
                    }
                    combatNarrator.speak("${switchTarget.name} is not ready yet ($pct% charged). It is ${activeHero.name}'s turn.", force = true) {
                        if (speechManager.isAutoListen.value) {
                            relistenAfter(100)
                        }
                    }
                }
                return
            }
        }

        // 2. Tactical Defend / Guard
        if (lower == "defend" || lower == "guard" || lower == "pass" || lower.contains("defend")) {
            defendActivePartyMember()
            return
        }

        // 3. Strict Turn Enforcement: Only the active hero whose ATB turn is ready may act!
        val otherHeroes = aliveParty.filter { it.id != activeHero.id }

        // Spoken aliases are content-declared per character (assets/game/characters/*.json);
        // the engine only ever matches display names, lore titles, and those JSON aliases.
        fun memberInvokedByUtterance(member: PartyMember): Boolean {
            val idMatch = member.id.isNotBlank() && lower.contains(member.id.lowercase())
            val nameMatch = member.name.isNotBlank() && (
                lower.contains(member.name.lowercase()) ||
                member.name.lowercase().split(" ").any { part -> part.length > 2 && lower.contains(part) }
            )
            val classMatch = member.loreClass.isNotBlank() && lower.contains(member.loreClass.lowercase())
            val aliasMatch = GameContent.voiceAliasesFor(member.id).any { it.isNotBlank() && lower.contains(it.lowercase()) }
            return idMatch || nameMatch || classMatch || aliasMatch
        }

        // Check if an inactive hero is explicitly invoked to take an action
        val invokedOtherHero = otherHeroes.firstOrNull { memberInvokedByUtterance(it) }

        val hasHealSupportKeyword = lower.contains("heal") || lower.contains("mend") || lower.contains("restore") ||
                lower.contains("cure") || lower.contains("protect") || lower.contains("shield") ||
                lower.contains("lay on hands") || lower.contains("soothing rain") ||
                lower.contains("cleanse") || lower.contains("dispel") || lower.contains("purify")
        val isSupportSpell = activeHero.spells.any { it.isHeal || it.isGuard || it.cleansesDebuffs || it.isCleanse }
        val isHealSupportForOther = invokedOtherHero != null && isSupportSpell && (
            hasHealSupportKeyword || activeHero.spells.any { spell ->
                (spell.isHeal || spell.isGuard || spell.cleansesDebuffs || spell.isCleanse) &&
                (lower.contains(spell.name.lowercase()) || spell.aliases.any { a -> a.length >= 3 && lower.contains(a.lowercase()) })
            }
        )

        // Breath/recovery utterances are self-disciplines: they always belong to whoever's turn it is.
        val BREATH_KEYWORDS = setOf(
            "attune", "breathe", "breath", "steady", "concentrate", "center", "centre",
            "recover mana", "restore mana", "focus", "still", "hush", "deep root", "quiet lungs"
        )
        val isBreathUtterance = BREATH_KEYWORDS.any { lower.contains(it) }

        fun spellMatchesUtterance(spell: Spell): Boolean {
            if (spell.manaRestorePct != 0f) return false
            return lower.contains(spell.name.lowercase()) ||
                spell.aliases.any { alias -> alias.length >= 3 && lower.contains(alias.lowercase()) } ||
                spell.name.lowercase().split(" ").any { word -> word.length >= 4 && lower.contains(word) }
        }

        // Check if the utterance matches a spell of the active hero
        val activeHeroSpellMatch = isBreathUtterance || activeHero.spells.any { spellMatchesUtterance(it) }

        // Check if another hero owns a spell that matches this utterance
        val otherHeroWithSpell = if (isBreathUtterance || activeHeroSpellMatch) null else otherHeroes.firstOrNull { member ->
            member.spells.any { spellMatchesUtterance(it) }
        }

        // Check if the utterance evokes a school the active hero does NOT command while another
        // hero does. Vocabulary is generic engine-level school wording (never campaign-specific),
        // and any utterance that already matches the active hero's own kit is exempt.
        val activeSchools = activeHero.spells.map { it.school }.toSet()
        val genericSchoolHints = mapOf(
            SpellSchool.PYROMANCY to listOf("fire", "flame", "blaze", "scorch", "cinder", "inferno", "heat"),
            SpellSchool.CRYOMANCY to listOf("frost", "ice", "freeze", "chill", "glacier", "snow"),
            SpellSchool.ELECTROMANCY to listOf("lightning", "thunder", "voltage", "shock", "static", "storm"),
            SpellSchool.HOLY to listOf("smite", "aegis", "dawn", "radiant", "seraph", "consecrate"),
            SpellSchool.NATURE to listOf("briar", "entangle", "grove", "verdant", "thorn", "vine", "bloom"),
            SpellSchool.SHADOW to listOf("venom", "flurry", "dagger", "poison", "dread", "oblivion", "night")
        )
        val hintedSchools = genericSchoolHints
            .filterKeys { it !in activeSchools }
            .filterValues { hints -> hints.any { hint -> hint.length >= 4 && lower.contains(hint) } }
            .keys
        val otherHeroBySchool = if (isBreathUtterance || activeHeroSpellMatch || otherHeroWithSpell != null || hintedSchools.isEmpty()) {
            null
        } else {
            otherHeroes.firstOrNull { member -> member.spells.any { it.school in hintedSchools } }
        }

        val outOfTurnHero = when {
            invokedOtherHero != null && !isHealSupportForOther && !activeHeroSpellMatch -> invokedOtherHero
            otherHeroWithSpell != null -> otherHeroWithSpell
            otherHeroBySchool != null -> otherHeroBySchool
            else -> null
        }

        if (outOfTurnHero != null) {
            val (hx, hy) = partyFloatSlot(activeHero.id)
            val fct = FloatingCombatText(
                text = if (!outOfTurnHero.isTurnReady) {
                    val pct = (outOfTurnHero.atbRatio * 100).toInt()
                    "${outOfTurnHero.name} is charging ($pct%)! It is ${activeHero.name}'s turn!"
                } else {
                    "It is ${activeHero.name}'s turn! Switch heroes to command ${outOfTurnHero.name}."
                },
                color = Color(0xFFFF8A80),
                startX = hx,
                startY = hy
            )
            _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts + fct)
            activeScope.launch {
                delay(1400)
                _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filter { it.id != fct.id })
            }
            val feedbackSpeech = if (!outOfTurnHero.isTurnReady) {
                val pct = (outOfTurnHero.atbRatio * 100).toInt()
                "${outOfTurnHero.name} is not ready to act yet, charging at $pct percent. It is ${activeHero.name}'s turn."
            } else {
                "It is ${activeHero.name}'s turn. Switch heroes to command ${outOfTurnHero.name}."
            }
            val relisten: () -> Unit = {
                if (speechManager.isAutoListen.value && _state.value.phase == CombatPhase.PLAYER_INPUT) {
                    relistenAfter(150)
                }
            }
            // The active hero may answer in character (outOfTurnLine in their character file).
            val activeCharacter = com.voicerpg.engine.content.GameContent.characterById(activeHero.id)
            val inCharacterLine = activeCharacter?.outOfTurnLine?.takeIf { it.isNotBlank() }
                ?.replace("{owner}", outOfTurnHero.name)?.replace("{active}", activeHero.name)
            if (inCharacterLine != null) {
                combatNarrator.narrateDialogue(activeCharacter.speaker, inCharacterLine, force = true) { relisten() }
            } else {
                combatNarrator.speak(feedbackSpeech, force = true) { relisten() }
            }
            return
        }

        activeScope.launch {
            speechManager.cancel()

            // 0-pre. Parse FIRST (so we know the spell), then enforce MP: an unaffordable chant
            // fails softly WITHOUT burning the hero's turn.
            val parsed = IntentParser.parse(utterance, activeHero.spells, _state.value.enemies, _state.value.party)
            if (parsed.spell.mpCost > activeHero.currentMp) {
                val (mx, my) = partyFloatSlot(activeHero.id)
                val fct = FloatingCombatText(
                    text = if (activeHero.spells.any { it.manaRestorePct > 0f }) "Not enough mana! (say attune)" else "Not enough mana!",
                    color = Color(0xFF80D8FF),
                    startX = mx,
                    startY = my
                )
                _state.value = _state.value.copy(
                    phase = CombatPhase.PLAYER_INPUT,
                    activePartyMemberId = activeHero.id,
                    floatingTexts = _state.value.floatingTexts + fct
                )
                activeScope.launch {
                    delay(1400)
                    _state.value = _state.value.copy(floatingTexts = _state.value.floatingTexts.filterNot { it.id == fct.id })
                }
                combatNarrator.speak(
                    "Not enough mana. ${activeHero.name} needs ${parsed.spell.mpCost} but holds only ${activeHero.currentMp}." +
                            if (activeHero.spells.any { it.manaRestorePct > 0f }) " Say attune to steady your breath and recover." else "",
                    force = true
                ) {
                    if (speechManager.isAutoListen.value) {
                        relistenAfter(150)
                    }
                }
                return@launch
            }

            // 1. Enter resolving phase & update active hero
            _state.value = _state.value.copy(
                phase = CombatPhase.INCANTATION_RESOLVING,
                activePartyMemberId = activeHero.id
            )
            if (typed) {
                beat(BattleCue.ChargeStart(activeHero.id, typed = true))
                cue(BattleCue.ChargeEnd(activeHero.id))
            }

            val acoustic = forcedAcoustic ?: speechManager.getLatestAcousticProfile()
            val resonance = resonanceEngine.evaluate(
                utterance = utterance,
                school = parsed.spell.school,
                acousticProfile = acoustic,
                ignoreNoveltyDecay = forcedAcoustic != null
            )

            val isSuperLogos = resonance.bonusPercent >= 100
            val showBanner = resonance.bonusPercent >= 50

            _state.value = _state.value.copy(
                lastResonance = resonance,
                party = _state.value.party.map {
                    if (it.id == activeHero.id) it.copy(stance = CharacterStance.CASTING) else it
                }
            )

            // 2. The cast: the voice is scored now, but revealed when the spell lands (step 4).
            sfxManager.playSpellCast()
            sfxManager.playSoundBite(parsed.spell.soundBites.ifEmpty {
                com.voicerpg.engine.content.GameContent.characterById(activeHero.id)?.soundBites ?: emptyList()
            })

            // A MYTHIC+ voice earns a portrait cut-in before the spell flies.
            heroCutIn(activeHero, resonance.tier)?.let { playCutIn(it) }

            // 3. Release: the spell flies from the caster to its targets.
            _state.value = _state.value.copy(phase = CombatPhase.SPELL_VFX_PLAYING)
            val isAttune = parsed.spell.manaRestorePct > 0f
            val isHeal = parsed.spell.isHeal || parsed.spell.cleansesDebuffs || parsed.spell.isCleanse
            val (style, targetIds) = when {
                isAttune -> BattleCue.ReleaseStyle.ATTUNE to listOf(activeHero.id)
                isHeal -> BattleCue.ReleaseStyle.HEAL to
                    resolveHealTargets(activeHero, parsed.spell, parsed.target, parsed.targetHeroId).ifEmpty { listOf(activeHero) }.map { it.id }
                else -> BattleCue.ReleaseStyle.PROJECTILE to
                    resolveDamageTargets(parsed.spell, parsed.target, parsed.targetEnemyId).map { it.id }
            }
            beat(
                BattleCue.Release(
                    casterId = activeHero.id,
                    fromParty = true,
                    targetIds = targetIds,
                    school = parsed.spell.school,
                    style = style,
                    tier = resonance.tier,
                    bonusPercent = resonance.bonusPercent,
                    particleCount = resonance.particleCount,
                    graphics = parsed.spell.graphics,
                    travelMs = timing.travelMs(fromParty = true, isBossUltimate = false, tier = resonance.tier)
                )
            )

            // 4. Spend mana, then resolve through the combat doctrine:
            //    affinity scales the BASE; the resonance multiplier stays untouched by any matchup.
            _state.value = _state.value.copy(
                party = _state.value.party.map {
                    if (it.id == activeHero.id) it.copy(
                        currentMp = (it.currentMp - parsed.spell.mpCost).coerceAtLeast(0),
                        isGuarding = parsed.spell.isGuard
                    ) else it
                }
            )

            val aliveBefore = aliveIds()
            val hits = if (isAttune) {
                applyAttuneAction(activeHero, parsed.spell)
            } else if (isHeal) {
                applyHealAction(activeHero, parsed.spell, parsed.target, parsed.targetHeroId, resonance)
            } else {
                applyDamageToEnemies(activeHero, parsed.spell, parsed.target, parsed.targetEnemyId, resonance)
            }
            val impact = BattleCue.Impact(
                sourceId = activeHero.id,
                fromParty = true,
                hits = hits,
                tier = resonance.tier,
                shake = parsed.spell.graphics?.screenShake ?: 0f
            )
            // The resonance reveal lands with the blow: banner, fanfare and impact are one moment.
            _state.value = _state.value.copy(isLogosBannerVisible = showBanner)
            val streakBefore = streak
            if (isSuperLogos) sfxManager.playLogosFanfare(pitch = 1f + 0.06f * streakBefore.coerceIn(0, 6))
            cue(BattleCue.ResonanceReveal(activeHero.id, resonance, showBanner))
            cue(impact)
            announceFallen(aliveBefore)
            if (!isAttune && !isHeal) applyBoldVoice(hits, resonance.tier)
            updateStreak(resonance.tier)
            hold(impact)

            // 5. Reset active hero's ATB gauge to 0 and record lastActedHeroId for round-robin rotation
            lastActedHeroId = activeHero.id
            val updatedParty = _state.value.party.map {
                if (it.id == activeHero.id) {
                    it.copy(stance = CharacterStance.READY, atbGauge = 0f)
                } else if (!it.isAlive) {
                    it.copy(stance = CharacterStance.DEAD, atbGauge = 0f, currentMp = 0)
                } else it
            }

            _state.value = _state.value.copy(
                isLogosBannerVisible = false,
                party = updatedParty,
                activePartyMemberId = null
            )

            // Check victory
            if (_state.value.enemies.none { it.isAlive }) {
                concludeVictory()
                return@launch
            }

            // Strictly Turn-Based Progression: check if next combatant is ready or resume charging
            beat(BattleCue.TurnEnd(activeHero.id, fromParty = true))
            lastActedFaction = CombatantFaction.HERO
            registerTurnCompleted()
            checkAndTransitionNextTurn()
        }
    }

    /**
     * Attune: the hero's free breath discipline. Costs the turn, restores a flat
     * percentage of max MP. Deliberately resonance-INDEPENDENT: sustain can never
     * inflate the voice multiplier.
     */
    private fun applyAttuneAction(member: PartyMember, spell: Spell): List<BattleCue.Hit> {
        val restore = (member.maxMp * spell.manaRestorePct).roundToInt().coerceAtLeast(1)
        sfxManager.playSpellCast()
        _state.value = _state.value.copy(
            party = _state.value.party.map {
                if (it.id == member.id) it.copy(currentMp = (it.currentMp + restore).coerceAtMost(it.maxMp)) else it
            }
        )
        combatNarrator.speak("${member.name} breathes slow and steady; $restore mana returns.", force = false)
        return listOf(BattleCue.Hit(member.id, restore, kind = BattleCue.HitKind.MANA))
    }

    private fun resolveHealTargets(
        caster: PartyMember,
        spell: Spell,
        target: TargetSelection,
        targetHeroId: String?
    ): List<PartyMember> {
        val currentParty = _state.value.party
        return when {
            spell.hitsAll || target == TargetSelection.PARTY_LOWEST -> currentParty.filter { it.isAlive }
            targetHeroId != null -> {
                val matched = currentParty.filter { it.id == targetHeroId && it.isAlive }
                if (matched.isNotEmpty()) matched else listOfNotNull(currentParty.filter { it.isAlive }.minByOrNull { it.hpRatio })
            }
            target == TargetSelection.SELF -> listOf(caster)
            else -> {
                // Heal lowest HP percentage hero
                listOfNotNull(currentParty.filter { it.isAlive }.minByOrNull { it.hpRatio })
            }
        }
    }

    private fun resolveDamageTargets(
        spell: Spell,
        target: TargetSelection,
        targetEnemyId: String?
    ): List<Enemy> {
        val currentEnemies = _state.value.enemies
        val isAoe = spell.hitsAll || target == TargetSelection.ALL_ENEMIES
        return when {
            isAoe -> currentEnemies.filter { it.isAlive }
            targetEnemyId != null -> {
                val matched = currentEnemies.filter { it.id == targetEnemyId && it.isAlive }
                if (matched.isNotEmpty()) matched
                else {
                    val marked = currentEnemies.firstOrNull { it.isTargeted && it.isAlive }
                    if (marked != null) listOf(marked)
                    else listOfNotNull(currentEnemies.firstOrNull { it.isAlive })
                }
            }
            else -> {
                val marked = currentEnemies.firstOrNull { it.isTargeted && it.isAlive }
                if (marked != null) listOf(marked)
                else listOfNotNull(currentEnemies.firstOrNull { it.isAlive })
            }
        }
    }

    private suspend fun applyHealAction(
        caster: PartyMember,
        spell: Spell,
        target: TargetSelection,
        targetHeroId: String?,
        resonance: ResonanceResult
    ): List<BattleCue.Hit> {
        sfxManager.playLogosFanfare()

        val healStatus = StatusId.fromNameOrNull(spell.status)
        val currentParty = _state.value.party
        val targetsToHeal = resolveHealTargets(caster, spell, target, targetHeroId)

        val healAmount = if (spell.basePower > 0) {
            DamageResolver.resolveHeal(
                spellBasePower = spell.basePower.toFloat(),
                attackerLevel = caster.level,
                resonanceMultiplier = resonance.damageMultiplier,
                partyWide = targetsToHeal.size > 1,
                random = random
            )
        } else 0

        var anyDebuffsCleansed = false
        val updatedParty = currentParty.map { hero ->
            if (targetsToHeal.any { it.id == hero.id }) {
                val newHp = (hero.currentHp + healAmount).coerceAtMost(hero.maxHp)
                var currentStatuses = hero.statuses
                if (spell.cleansesDebuffs || spell.isCleanse) {
                    val hadDebuffs = currentStatuses.any { it.isDebuffOnTarget }
                    if (hadDebuffs) anyDebuffsCleansed = true
                    currentStatuses = currentStatuses.filterNot { it.isDebuffOnTarget }
                }
                val blessed = if (healStatus == StatusId.BLESS || healStatus == StatusId.GUARD)
                    StatusSystem.merge(currentStatuses, StatusSystem.apply(healStatus!!, "ADEPT", EnemyFamily.FLESH, 0f))
                else currentStatuses
                hero.copy(
                    currentHp = newHp,
                    statuses = blessed,
                    isGuarding = hero.isGuarding || healStatus == StatusId.GUARD,
                    stance = if (hero.stance == CharacterStance.DEAD && newHp > 0) CharacterStance.READY else hero.stance
                )
            } else hero
        }

        val cleansedAny = (spell.cleansesDebuffs || spell.isCleanse) && targetsToHeal.any { t -> t.statuses.any { it.isDebuffOnTarget } }
        val cleanseNote = if (cleansedAny) FloatingCombatText(text = "CLEANSED ✨", color = Color(0xFF80D8FF), startX = 0f, startY = 0f) else null
        val newFloatingTexts = listOfNotNull(cleanseNote)

        _state.value = _state.value.copy(
            party = updatedParty,
            floatingTexts = _state.value.floatingTexts + newFloatingTexts
        )

        val targetDesc = if (spell.hitsAll) "the fellowship" else targetsToHeal.joinToString(", ") { it.name }
        if (healAmount > 0) {
            combatNarrator.narrateSpellCastSuspend(
                heroName = caster.name,
                spellName = spell.name,
                targetName = targetDesc,
                amount = healAmount,
                isHeal = true,
                tierTitle = resonance.tier.title,
                defeatedNames = emptyList()
            )
        } else if (anyDebuffsCleansed) {
            combatNarrator.speak("${caster.name}'s ${spell.name} dispels all negative conditions from $targetDesc!", force = true)
        } else {
            combatNarrator.speak("${caster.name} channels ${spell.name} upon $targetDesc.", force = false)
        }

        activeScope.launch {
            delay(1200)
            val idsToRemove = newFloatingTexts.map { it.id }.toSet()
            _state.value = _state.value.copy(
                floatingTexts = _state.value.floatingTexts.filterNot { it.id in idsToRemove }
            )
        }
        return targetsToHeal.map { BattleCue.Hit(it.id, healAmount, kind = BattleCue.HitKind.HEAL) }
    }

    private suspend fun applyDamageToEnemies(
        caster: PartyMember,
        spell: Spell,
        target: TargetSelection,
        targetEnemyId: String?,
        resonance: ResonanceResult
    ): List<BattleCue.Hit> {
        val currentEnemies = _state.value.enemies
        val isAoe = spell.hitsAll || target == TargetSelection.ALL_ENEMIES
        val targetList = resolveDamageTargets(spell, target, targetEnemyId)

        sfxManager.playImpact(spell.school, resonance.tier)
        // A muffled whomp under the freeze of a strong blow (lengths follow the director's hit-stop table).
        when (resonance.tier) {
            ResonanceTier.MASTER -> sfxManager.playWhomp(110)
            ResonanceTier.MYTHIC -> sfxManager.playWhomp(150)
            ResonanceTier.TRANSCENDENTAL -> sfxManager.playWhomp(180)
            else -> Unit
        }

        // Per-enemy resolution: affinity lands on the base, voice multiplier untouched.
        val school = schoolOf(spell.school)
        val statusId = StatusId.fromNameOrNull(spell.status)
        val weakenedCaster = StatusSystem.isWeakened(caster.statuses)
        data class HitOutcome(val enemy: Enemy, val damage: Int, val affinity: Float, val landed: StatusInstance?)
        val hits = targetList.map { enemy ->
            val eff = AffinityTable.effectiveness(school, familyOf(enemy), School.fromNameOrNull(enemy.selfElement))
            val strike = DamageResolver.resolve(
                DamageInputs(
                    spellBasePower = spell.basePower.toFloat(),
                    attackerLevel = caster.level,
                    affinity = eff,
                    resonanceMultiplier = resonance.damageMultiplier,
                    targetDefense = enemy.defense,
                    defenseMultiplier = StatusSystem.defenseMult(enemy.statuses),
                    attackerWeakened = weakenedCaster
                ),
                random
            )
            val effectiveDamage = if (_state.value.isPureStoryMode) {
                (strike.damage * 2.5f).toInt().coerceAtLeast(100)
            } else {
                strike.damage
            }
            HitOutcome(enemy, effectiveDamage, strike.affinity, statusId?.let { StatusSystem.apply(it, resonance.tier.name, familyOf(enemy), effectiveDamage.toFloat()) })
        }
        val totalDamage = hits.sumOf { it.damage }

        val updatedEnemies = currentEnemies.map { enemy ->
            hits.firstOrNull { it.enemy.id == enemy.id }?.let { hit ->
                val newHp = (enemy.currentHp - hit.damage).coerceAtLeast(0)
                enemy.copy(
                    currentHp = newHp,
                    atbGauge = if (newHp <= 0) 0f else enemy.atbGauge,
                    isDamagedFlash = true,
                    statuses = StatusSystem.merge(enemy.statuses, hit.landed)
                )
            } ?: enemy
        }

        // Lifesteal spells convert half the damage dealt into healing for the caster.
        var lifestealHealed = 0
        if (spell.lifesteal && totalDamage > 0) {
            lifestealHealed = totalDamage / 2
            _state.value = _state.value.copy(
                party = _state.value.party.map {
                    if (it.id == caster.id) it.copy(currentHp = (it.currentHp + lifestealHealed).coerceAtMost(it.maxHp)) else it
                }
            )
        }

        val defeatedNames = updatedEnemies
            .filter { e -> targetList.any { it.id == e.id } && !e.isAlive }
            .map { it.name }
        val enemyTargetDesc = if (isAoe) "all foes" else targetList.joinToString(", ") { it.name }

        // Pocket-mode narration: pick the single most informative effect line among targets.
        val effectNote: String? = hits.firstNotNullOfOrNull { hit ->
            AffinityTable.narrationLine(school, hit.enemy.name, hit.affinity)
        } ?: hits.firstNotNullOfOrNull { hit ->
            hit.landed?.let { s -> "${hit.enemy.name} is ${StatusSystem.shortName(s.status)}!" }
        }
        val fullNote = listOfNotNull(
            effectNote,
            if (lifestealHealed > 0) "${caster.name} drains $lifestealHealed health!" else null
        ).joinToString(" ").ifBlank { null }

        combatNarrator.narrateSpellCastSuspend(
            heroName = caster.name,
            spellName = spell.name,
            targetName = enemyTargetDesc,
            amount = totalDamage,
            isHeal = false,
            tierTitle = resonance.tier.title,
            defeatedNames = defeatedNames,
            effectNote = fullNote
        )

        // If targeted enemy was defeated, auto-retarget the next living enemy
        val hasDeadTarget = updatedEnemies.any { it.isTargeted && !it.isAlive }
        val finalEnemies = if (hasDeadTarget) {
            val nextAlive = updatedEnemies.firstOrNull { it.isAlive }
            updatedEnemies.map { it.copy(isTargeted = it.id == nextAlive?.id) }
        } else updatedEnemies

        _state.value = _state.value.copy(enemies = finalEnemies)

        // Clear flash
        activeScope.launch {
            delay(200)
            _state.value = _state.value.copy(
                enemies = _state.value.enemies.map { it.copy(isDamagedFlash = false) }
            )
        }
        return hits.map { BattleCue.Hit(it.enemy.id, it.damage, it.affinity) }
    }

    fun setEnvironment(env: BattleEnvironment) {
        _state.value = _state.value.copy(currentEnvironment = env)
    }

    /**
     * Spawns new enemies onto the battlefield mid-fight (e.g. boss summon or reinforcements wave),
     * strictly enforcing the hard maximum cap of 6 enemies simultaneously on field.
     */
    fun summonReinforcements(newEnemies: List<Enemy>): Int {
        val currentAlive = _state.value.enemies.count { it.isAlive }
        val maxAllowed = (6 - currentAlive).coerceAtLeast(0)
        val toAdd = newEnemies.take(maxAllowed).map {
            decorateEnemy(it).copy(atbGauge = 0f, isDamagedFlash = false)
        }
        if (toAdd.isEmpty()) return 0

        val announcementText = if (toAdd.size == 1) {
            "${toAdd.first().name} Joined!"
        } else {
            "Reinforcements Arrived! (+${toAdd.size})"
        }

        val fct = FloatingCombatText(
            text = announcementText,
            color = Color(0xFFFFB74D),
            startX = 780f,
            startY = 380f,
            isCrit = true
        )

        sfxManager.playSpellCast()
        cue(BattleCue.Reinforcements(toAdd.map { it.id }))
        combatNarrator.narrateReinforcements(toAdd.size, toAdd.map { it.name })

        _state.update { prev ->
            prev.copy(
                enemies = prev.enemies + toAdd,
                floatingTexts = prev.floatingTexts + fct
            )
        }

        activeScope.launch {
            delay(1500)
            _state.update { prev ->
                prev.copy(floatingTexts = prev.floatingTexts.filter { it.id != fct.id })
            }
        }

        return toAdd.size
    }

    fun pauseAtb() {
        conclusionTransitionJob?.cancel()
        conclusionTransitionJob = null
        conclusionWatchdogJob?.cancel()
        conclusionWatchdogJob = null
        atbJob?.cancel()
        atbJob = null
        chargeJob?.cancel()
        chargeJob = null
        intensityJob?.cancel()
        intensityJob = null
        musicManager?.setIntensityTrack(null)
        endCharge()
        combatNarrator.setCombatActive(false)
        _state.update { it.copy(phase = CombatPhase.INACTIVE) }
    }

    fun spawnEnemy(enemy: Enemy): Boolean = summonReinforcements(listOf(enemy)) > 0

    var activeCustomization: PlayerCustomization = PlayerCustomization()
        private set

    fun applyPlayerCustomization(
        customization: PlayerCustomization,
        savedStats: List<SavedCharacterStats> = emptyList()
    ) {
        activeCustomization = customization
        val spells = GameContent.spellsForClass(customization.heroClass.id)
        val heroStats = savedStats.firstOrNull { it.id == "hero" }
        val hp = heroStats?.currentHp ?: customization.heroClass.startingHp
        val maxHp = heroStats?.maxHp ?: customization.heroClass.startingHp
        val mp = heroStats?.currentMp ?: customization.heroClass.startingMp
        val maxMp = heroStats?.maxMp ?: customization.heroClass.startingMp
        val tint = try {
            Color(android.graphics.Color.parseColor(customization.auraColor.hexColor))
        } catch (_: Exception) {
            Color(0xFF90CAF9)
        }

        val updatedHero = PartyMember(
            id = "hero",
            name = customization.name,
            loreClass = customization.heroClass.title,
            currentHp = hp,
            maxHp = maxHp,
            currentMp = mp,
            maxMp = maxMp,
            spells = spells,
            avatarTint = tint,
            speed = heroStats?.speed ?: customization.heroClass.startingSpeed,
            atbGauge = if (_state.value.phase == CombatPhase.INACTIVE) 0f else (_state.value.party.firstOrNull { it.id == "hero" }?.atbGauge ?: 0f),
            level = heroStats?.level ?: 1,
            xp = heroStats?.xp ?: 0
        )

        val updatedParty = _state.value.party.map {
            if (it.id == "hero") updatedHero else it
        }

        _state.value = _state.value.copy(party = updatedParty)
    }

    var lastEncounterDefinition: EncounterDefinition? = null
        internal set

    fun startEncounter(encounter: EncounterDefinition) {
        lastEncounterDefinition = encounter
        currentEncounterId = encounter.id
        _combatantPositions.clear()
        sfxManager.mute(false)
        musicManager?.playCombatMusic()

        val baseParty = encounter.initialParty ?: if (_state.value.party.isNotEmpty()) {
            _state.value.party.map {
                it.copy(
                    currentHp = it.maxHp,
                    currentMp = it.maxMp,
                    stance = CharacterStance.READY,
                    atbGauge = (Random.nextFloat() * 0.3f + 0.25f)
                )
            }
        } else {
            GameContent.createDuoParty()
        }

        // Apply active player customization (custom name, class, starter spells, aura color) to the hero
        val partyToUse = applyImportedProgression(baseParty.map { member ->
            if (member.id == "hero") {
                val spells = GameContent.spellsForClass(activeCustomization.heroClass.id)
                val tint = try {
                    Color(android.graphics.Color.parseColor(activeCustomization.auraColor.hexColor))
                } catch (_: Exception) {
                    member.avatarTint
                }
                member.copy(
                    name = activeCustomization.name,
                    loreClass = activeCustomization.heroClass.title,
                    spells = spells,
                    avatarTint = tint,
                    speed = activeCustomization.heroClass.startingSpeed
                )
            } else member
        })

        startEncounter(partyToUse, encounter.enemies.map { decorateEnemy(it) }, encounter.environment, encounter.environmentId)
    }

    /** Persisted level/xp + damaged HP/MP carry into the next fight; fresh battle with clean statuses. */
    internal fun applyImportedProgression(members: List<PartyMember>): List<PartyMember> {
        return members.map { member ->
            val saved = importedStats.firstOrNull { it.id == member.id } ?: return@map member
            val revived = member.currentHp <= 0 || saved.currentHp <= 0
            val currentHp = if (revived) (saved.maxHp / 2).coerceAtLeast(1) else saved.currentHp.coerceAtMost(saved.maxHp)

            // Resolve companion spells from saved stats if available
            val updatedSpells = if (member.id != "hero" && saved.spellIds.isNotEmpty()) {
                val companionSpells = saved.spellIds.mapNotNull { GameContent.spellById(it) }
                val breath = GameContent.breathSpellFor(member.id)
                if (companionSpells.isNotEmpty()) {
                    (companionSpells.filter { it.manaRestorePct == 0f } + listOfNotNull(breath)).distinctBy { it.id }
                } else member.spells
            } else {
                member.spells
            }

            member.copy(
                level = saved.level,
                xp = saved.xp,
                maxHp = saved.maxHp,
                maxMp = saved.maxMp,
                currentHp = currentHp,
                currentMp = saved.currentMp.coerceAtMost(saved.maxMp),
                speed = saved.speed,
                spells = updatedSpells,
                statuses = emptyList(),
                isGuarding = false
            )
        }
    }

    fun startEncounter(
        party: List<PartyMember>,
        enemies: List<Enemy>,
        environment: BattleEnvironment,
        environmentId: String = ""
    ) {
        conclusionTransitionJob?.cancel()
        conclusionTransitionJob = null
        conclusionWatchdogJob?.cancel()
        conclusionWatchdogJob = null
        atbJob?.cancel()
        speechManager.cancel()
        resonanceEngine.noveltyCache.clear()
        _combatantPositions.clear()
        turnsTakenInRound = 0
        lastActedHeroId = null
        lastActedFaction = CombatantFaction.NONE
        streak = 0
        musicManager?.playCombatMusic()
        combatNarrator.setCombatActive(true)

        _state.value = CombatState(
            phase = CombatPhase.ATB_WAITING,
            roundNumber = 1,
            party = party,
            enemies = enemies.map { decorateEnemy(it).let { e -> e.copy(atbGauge = e.atbGauge.coerceAtLeast(0.25f)) } }.take(6),
            currentEnvironment = environment,
            currentEnvironmentId = environmentId,
            isPureStoryMode = _state.value.isPureStoryMode
        )
        cue(BattleCue.BattleStart(currentEncounterId))
        startAtbLoop()
        startChargeWatcher()
        startIntensityWatcher(lastEncounterDefinition?.takeIf { it.id == currentEncounterId }?.intensityTrack
            ?: GameContent.presentation.music.intensityTrack)
    }

    fun restartBattle() {
        val lastDef = lastEncounterDefinition
        if (lastDef != null) {
            startEncounter(lastDef)
            return
        }
        val currentEnv = _state.value.currentEnvironment
        val currentEnvId = _state.value.currentEnvironmentId
        conclusionTransitionJob?.cancel()
        conclusionTransitionJob = null
        conclusionWatchdogJob?.cancel()
        conclusionWatchdogJob = null
        turnsTakenInRound = 0
        lastActedHeroId = null
        lastActedFaction = CombatantFaction.NONE
        streak = 0
        _combatantPositions.clear()
        resonanceEngine.noveltyCache.clear()
        val initialParty = GameContent.createDuoParty()
        val fallbackEnemies = GameContent.enemyTemplates.values.take(3)
            .mapIndexed { index, template ->
                template.toEnemy(
                    isTargeted = index == 1,
                    atbGauge = (0.30f + 0.15f * index).coerceAtMost(0.45f)
                )
            }
        combatNarrator.setCombatActive(true)
        _state.value = CombatState(
            phase = CombatPhase.ATB_WAITING,
            party = initialParty,
            enemies = fallbackEnemies,
            currentEnvironment = currentEnv,
            currentEnvironmentId = currentEnvId,
            isPureStoryMode = _state.value.isPureStoryMode
        )
        cue(BattleCue.BattleStart(currentEncounterId))
        startAtbLoop()
        startChargeWatcher()
        startIntensityWatcher(GameContent.presentation.music.intensityTrack)
    }

    fun cleanup() {
        conclusionTransitionJob?.cancel()
        conclusionTransitionJob = null
        conclusionWatchdogJob?.cancel()
        conclusionWatchdogJob = null
        atbJob?.cancel()
        chargeJob?.cancel()
        intensityJob?.cancel()
        endCharge()
        speechManager.destroy()
        combatNarrator.destroy()
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }

    companion object {
        /** Global enemy speed pacing multiplier: minor increment to grant enemies more turns. */
        const val ENEMY_SPEED_PACING_MULT = 1.15f

        /** Particle burst of an enemy move that declares none in its graphics. */
        const val ENEMY_IMPACT_PARTICLES = 15

        /** How far a stagger pushes an enemy's gauge back. */
        const val STAGGER_GAUGE = 0.35f
        const val WEAKNESS_AFFINITY = 1.5f

        /** Chance an enemy with taunts shouts one when it telegraphs. */
        const val TAUNT_CHANCE = 0.35f

        /** Gauge at which an enemy picks and telegraphs its next move. */
        const val PLAN_AT_GAUGE = 0.75f

        /** How often a charge passes the live voice level on (the hum follows every sample). */
        const val CHARGE_LEVEL_INTERVAL_MS = 50L
        const val CHARGE_LEVEL_JUMP = 0.15f
    }
}
