package com.voicerpg.engine.ui.combat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.voicerpg.engine.BuildConfig
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.CombatPhase
import com.voicerpg.engine.model.FloatingCombatText
import com.voicerpg.engine.ui.environment.BattleEnvironmentCanvas
import com.voicerpg.engine.ui.environment.BattleForegroundOverlay
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.LogosGlow
import com.voicerpg.engine.ui.theme.RetroBlack
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroDeepSlate
import com.voicerpg.engine.ui.theme.RetroPanel
import androidx.compose.ui.geometry.Offset
import com.voicerpg.engine.engine.MotionSettings
import com.voicerpg.engine.ui.fx.BattleFxCanvas
import com.voicerpg.engine.ui.fx.BattleFxDirector
import com.voicerpg.engine.ui.fx.FxBenchBar
import com.voicerpg.engine.ui.fx.DamageNumbersLayer
import com.voicerpg.engine.ui.fx.ScreenFlashLayer
import com.voicerpg.engine.ui.fx.TitleCardLayer
import com.voicerpg.engine.ui.fx.StreakBadge
import com.voicerpg.engine.ui.fx.CutInLayer
import androidx.compose.ui.graphics.graphicsLayer
import com.voicerpg.engine.viewmodel.CombatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RetroBattleScreen(
    viewModel: CombatViewModel,
    motionSettings: MotionSettings,
    onReturnToStory: (() -> Unit)? = null,
    onContinueStory: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val reduceMotion by motionSettings.reduceMotion.collectAsState()
    val reduceFlashing by motionSettings.reduceFlashing.collectAsState()

    // Battle effects: the view model announces cues, the director plays them (ui/fx).
    val director = remember(viewModel) {
        BattleFxDirector(
            positionOf = { id -> viewModel.combatantSlot(id).let { (x, y) -> Offset(x, y) } },
            lookOf = { id -> combatantLook(viewModel.state.value, id) }
        )
    }
    director.presentation = GameContent.presentation
    director.reduceMotion = reduceMotion
    director.reduceFlashing = reduceFlashing
    LaunchedEffect(viewModel, director) {
        viewModel.cues.collect { director.play(it) }
    }
    val speechState by viewModel.speechManager.speechState.collectAsState()
    val liveTranscript by viewModel.speechManager.liveTranscript.collectAsState()
    val isChimeMuted by viewModel.speechManager.isChimeMuted.collectAsState()
    val isAutoListen by viewModel.speechManager.isAutoListen.collectAsState()
    val rmsLevel by viewModel.speechManager.rmsLevel.collectAsState()
    val showDeveloperTools by viewModel.isDeveloperToolsEnabled.collectAsState()
    val configuration = LocalConfiguration.current
    val bgAspect = com.voicerpg.engine.ui.story.BackgroundAspect.fromConfiguration(configuration)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RetroBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(
                    if (speechState is com.voicerpg.engine.audio.SpeechState.Standby && state.phase == CombatPhase.PLAYER_INPUT) {
                        Modifier.clickable { viewModel.resumeVoiceListening() }
                    } else Modifier
                )
        ) {
            // Main Battle Arena Layout
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: Initiative & Round Status with Quick Options / Pocket Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InitiativeTrack(
                        phase = state.phase,
                        roundNumber = state.roundNumber,
                        party = state.party,
                        activePartyMemberId = state.activePartyMemberId,
                        modifier = Modifier.weight(1f)
                    )

                    // Return to Story Mode button (debug/test only)
                    if (onReturnToStory != null && showDeveloperTools) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1B5E20))
                                .border(1.dp, Color(0xFF66BB6A), RoundedCornerShape(8.dp))
                                .clickable { onReturnToStory() }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📖 STORY",
                                color = Color(0xFFE8F5E9),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Quick Settings Button (Options modal & Pocket Mode status)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (state.isEyesFreeMode) Color(0xFF1565C0) else RetroPanel.copy(alpha = 0.9f))
                            .border(1.dp, if (state.isEyesFreeMode) Color(0xFF64B5F6) else RetroBorder, RoundedCornerShape(8.dp))
                            .clickable { viewModel.openOptions() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (state.isEyesFreeMode) "🎧 POCKET" else "⚙️ OPTIONS",
                            color = if (state.isEyesFreeMode) Color(0xFFE3F2FD) else LogosGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Environment & Story Encounter Switcher Bar (debug/test only)
                if (showDeveloperTools) {
                    EnvironmentSwitcherBar(
                        currentEnvironment = state.currentEnvironment,
                        onSelectEnvironment = { viewModel.setEnvironment(it) },
                        onSelectEncounter = { viewModel.startEncounter(it) },
                        onSummonMinion = {
                            val minionNum = (state.enemies.size + 1)
                            val minion = GameContent.createMinion(
                                idSuffix = "$minionNum",
                                name = "Template Minion $minionNum",
                                hp = 180
                            )
                            viewModel.summonReinforcements(listOf(minion))
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    FxBenchBar(
                        director = director,
                        partyIds = state.party.filter { it.isAlive }.map { it.id },
                        enemyIds = state.enemies.filter { it.isAlive }.map { it.id },
                        bossId = state.enemies.firstOrNull { it.isAlive && it.isBoss }?.id
                    )
                }

                // Middle: 32-bit Tactical Battle Arena (Left: Party, Right: Monsters)
                var arenaCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        // Only the arena shakes; the status bar and console stay readable.
                        .offset {
                            val shake = director.shake.offset
                            IntOffset(shake.x.dp.roundToPx(), shake.y.dp.roundToPx())
                        }
                        .graphicsLayer {
                            scaleX = director.zoom
                            scaleY = director.zoom
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, RetroBorder, RoundedCornerShape(8.dp))
                        .onSizeChanged { size ->
                            viewModel.updateArenaDimensions(size.width.toFloat(), size.height.toFloat())
                        }
                        .onGloballyPositioned { coords ->
                            arenaCoordinates = coords
                        }
                ) {
                    // SNES BG3/BG2 Layer: Backdrop + Horizon Mist + Mode-7 Perspective Floor
                    BattleEnvironmentCanvas(
                        environment = state.currentEnvironment,
                        environmentId = state.currentEnvironmentId,
                        bgAspect = bgAspect,
                        partyCount = state.party.size,
                        enemyCount = state.enemies.size,
                        modifier = Modifier.fillMaxSize()
                    )

                    // The backdrop darkens while a chant charges (the combatants stay lit)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind { drawRect(Color.Black.copy(alpha = director.dim)) }
                    )

                    // Bosses' health and ultimate charge run across the top (BattleHud); their sprites carry no gauges.
                    val bossBars = BattleHud.bossBars(state, director::fxOf)

                    // SNES OBJ Layer: Combatants anchored onto the floor stage
                    // Both flanks fill the arena, so they share the backdrop's floor (BattleStage).
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Flank (Heroes / Party)
                        PartyFlank(
                            party = state.party,
                            activePartyMemberId = state.activePartyMemberId,
                            onSelectHero = { viewModel.selectPartyMember(it) },
                            onPositionHero = { id, offset ->
                                viewModel.updateCombatantPosition(id, offset.x, offset.y)
                            },
                            arenaCoordinates = arenaCoordinates,
                            fxOf = director::fxOf,
                            modifier = Modifier.weight(1f)
                        )

                        // Right Flank (Enemies / Monsters)
                        EnemyFlank(
                            enemies = state.enemies,
                            onSelectEnemy = { viewModel.selectEnemy(it) },
                            onPositionEnemy = { id, offset ->
                                viewModel.updateCombatantPosition(id, offset.x, offset.y)
                            },
                            arenaCoordinates = arenaCoordinates,
                            fxOf = director::fxOf,
                            onSpriteBounds = director::registerSpriteBounds,
                            bossBarIds = bossBars.map { it.id }.toSet(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // SNES BG1 + Color Math Foreground Layer (silhouettes, light shafts & vignette)
                    BattleForegroundOverlay(
                        environment = state.currentEnvironment,
                        environmentId = state.currentEnvironmentId,
                        modifier = Modifier.fillMaxSize()
                    )

                    BossBars(
                        bars = bossBars,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    // Spell projectiles and particle bursts
                    BattleFxCanvas(
                        director = director,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Floating Damage & Healing Numbers
                    DamageNumbersLayer(director = director)
                    TitleCardLayer(director = director)
                    StreakBadge(director = director)
                    CutInLayer(director = director)
                    ScreenFlashLayer(director = director)

                    // Logos Golden Banner Overlay (Top-Center Notification Style)
                    LogosBanner(
                        resonance = state.lastResonance,
                        visible = state.isLogosBannerVisible,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp + 30.dp * bossBars.size, start = 12.dp, end = 12.dp)
                    )

                    // Battle Won / Lost Overlay
                    if (state.phase == CombatPhase.BATTLE_WON || state.phase == CombatPhase.BATTLE_LOST) {
                        Box(modifier = Modifier.align(Alignment.Center)) {
                            BattleConclusionOverlay(
                                isVictory = state.phase == CombatPhase.BATTLE_WON,
                                onRestart = { viewModel.restartBattle() },
                                onContinueStory = onContinueStory
                            )
                        }
                    }
                }

                // Status line: turn, mana, charging and option messages (kept out of the arena)
                BattleStatusLine(messages = state.floatingTexts)

                // Bottom: Command Console with Mic & Resonance Meter
                ResonanceConsole(
                    phase = state.phase,
                    speechState = speechState,
                    liveTranscript = liveTranscript,
                    lastResonance = state.lastResonance,
                    activePartyMember = state.activePartyMember,
                    onStartListening = { viewModel.resumeVoiceListening() },
                    onStopListening = { viewModel.stopVoiceListening() },
                    onSubmitChant = { chant, acoustic -> viewModel.submitTypedChant(chant, acoustic) },
                    partyChips = BattleHud.partyChips(state),
                    onSelectHero = { id -> state.party.firstOrNull { it.id == id }?.let(viewModel::selectPartyMember) },
                    isChimeMuted = isChimeMuted,
                    onToggleChimeMute = { viewModel.speechManager.toggleChimeMute() },
                    isAutoListen = isAutoListen,
                    onToggleAutoListen = { viewModel.speechManager.toggleAutoListen() },
                    rmsLevel = rmsLevel,
                    showDeveloperTools = showDeveloperTools,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BattleConclusionOverlay(
    isVictory: Boolean,
    onRestart: () -> Unit,
    onContinueStory: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE60A0A10))
            .border(2.dp, if (isVictory) LogosGold else Color.Red, RoundedCornerShape(12.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val manifest = GameContent.manifest
            Text(
                text = if (isVictory) manifest.victoryTitle else manifest.defeatTitle,
                color = if (isVictory) LogosGlow else Color(0xFFFF5252),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            val conclusionText = if (isVictory) manifest.victoryLine else manifest.defeatLine

            Text(
                text = conclusionText,
                color = Color.LightGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Win or lose, the story can continue (labels from the manifest).
            if (onContinueStory != null) {
                Button(
                    onClick = onContinueStory,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isVictory) LogosGold else Color(0xFFFF9800),
                        contentColor = RetroBlack
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isVictory) manifest.victoryContinueLabel else manifest.defeatContinueLabel,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isVictory) Color(0xFF37474F) else Color(0xFFC62828),
                    contentColor = if (isVictory) Color.White else RetroBlack
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "RESTART BATTLE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isVictory) {
                    "🎤 Say \"Commence Story\" or \"Restart Battle\""
                } else {
                    "🎤 Say \"Press On\" / \"Continue Story\" or \"Restart Battle\""
                },
                color = LogosGold.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** The newest battle message (charging, not enough mana, guards, toggles), one line above the console. */
@Composable
private fun BattleStatusLine(messages: List<FloatingCombatText>) {
    val latest = messages.lastOrNull()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (latest != null) {
            Text(
                text = latest.text,
                color = latest.color,
                fontSize = if (latest.isCrit) 12.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

/** What the effects director needs about a combatant's sprite: its death frame, facing and hit reaction. */
private fun combatantLook(state: com.voicerpg.engine.viewmodel.CombatState, id: String): com.voicerpg.engine.ui.fx.CombatantLook? {
    val enemy = state.enemies.firstOrNull { it.id == id }
    val spriteId = enemy?.spriteId?.ifBlank { enemy.id } ?: state.party.firstOrNull { it.id == id }?.id ?: return null
    val (matrix, palette) = com.voicerpg.engine.ui.sprites.PixelSpriteData.getPixelMatrix(spriteId, com.voicerpg.engine.ui.sprites.SpriteFrame.DEATH)
    return com.voicerpg.engine.ui.fx.CombatantLook(
        isEnemy = enemy != null,
        matrix = matrix,
        palette = palette,
        flipped = enemy != null,
        hitReaction = enemy?.hitReaction ?: com.voicerpg.engine.model.HitReaction.FLINCH
    )
}
