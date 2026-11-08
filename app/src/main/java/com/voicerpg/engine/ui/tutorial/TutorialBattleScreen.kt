package com.voicerpg.engine.ui.tutorial

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.audio.MusicManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.TutorialBattleContent
import com.voicerpg.engine.engine.TutorialBattleStep
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.CharacterStance
import com.voicerpg.engine.model.DialogueSpeaker
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.model.SpellSchool
import com.voicerpg.engine.ui.combat.EnemyFlank
import com.voicerpg.engine.ui.combat.PartyFlank
import com.voicerpg.engine.ui.environment.BattleEnvironmentCanvas
import com.voicerpg.engine.ui.story.StoryAssetLoader
import com.voicerpg.engine.ui.theme.FrostCyan
import com.voicerpg.engine.ui.theme.HolyYellow
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.LogosGlow
import com.voicerpg.engine.ui.theme.MpBlue
import com.voicerpg.engine.ui.theme.RetroBlack
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroBorderGold
import com.voicerpg.engine.ui.theme.RetroDeepSlate
import com.voicerpg.engine.ui.theme.RetroPanel
import com.voicerpg.engine.viewmodel.ModalCardType
import com.voicerpg.engine.viewmodel.TutorialBattleUiState
import com.voicerpg.engine.viewmodel.TutorialBattleViewModel
import com.voicerpg.engine.viewmodel.TutorialFloatingText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TutorialBattleScreen(
    viewModel: TutorialBattleViewModel,
    onBack: () -> Unit,
    onStartNewGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Start battle BGM on enter
    LaunchedEffect(Unit) {
        viewModel.musicManager?.playTrack(MusicManager.TRACK_COMBAT)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RetroBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .offset(x = state.screenShakeOffsetX.dp, y = state.screenShakeOffsetY.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header: Navigation & Tutorial Progress
                TutorialTopBar(
                    stepIndex = state.stepIndex,
                    totalSteps = state.totalSteps,
                    isListening = state.isListening,
                    onBack = {
                        viewModel.musicManager?.playTrack(MusicManager.TRACK_TITLE)
                        onBack()
                    }
                )

                // Middle: 32-bit Tactical Battle Arena (Left: Fellowship, Right: Dummies)
                var arenaCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, RetroBorder, RoundedCornerShape(8.dp))
                        .onGloballyPositioned { arenaCoordinates = it }
                ) {
                    // Living Environmental Background Canvas
                    BattleEnvironmentCanvas(
                        environment = GameContent.defaultEncounter?.environment ?: BattleEnvironment.CASTLE,
                        environmentId = GameContent.defaultEncounter?.environmentId,
                        isPortrait = !isLandscape,
                        partyCount = state.party.size,
                        enemyCount = state.enemies.size,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 25% black contrast overlay between background and sprites
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )

                    // Battle Grid Flanks
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Flank (Fellowship Heroes)
                        PartyFlank(
                            party = state.party,
                            activePartyMemberId = state.activePartyMemberId,
                            onSelectHero = { /* Active hero managed by tutorial progression */ },
                            arenaCoordinates = arenaCoordinates,
                            modifier = Modifier.weight(1f)
                        )

                        // Right Flank (Practice Dummies)
                        EnemyFlank(
                            enemies = state.enemies,
                            onSelectEnemy = { enemy -> viewModel.selectEnemy(enemy.id) },
                            arenaCoordinates = arenaCoordinates,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Floating Damage & Healing Numbers
                    state.floatingTexts.forEach { ft ->
                        TutorialFloatingNumberItem(ft = ft)
                    }

                    // Spell Casting Banner Overlay during spell execution
                    if (state.isAnimating && state.activeSpellName != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xEE1A1A2E))
                                .border(1.dp, LogosGold, RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "⚡ CASTING: ${state.activeSpellName?.uppercase()}",
                                color = LogosGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Bottom: Command Console & Spells Dock
                TutorialCommandDock(
                    state = state,
                    onCastSpell = { spellName -> viewModel.castSpell(spellName) },
                    onStartListening = { viewModel.startListening() },
                    onStopListening = { viewModel.stopListening() }
                )
            }

            // Centered Modal Dialogue Card Overlay
            if (state.isModalVisible) {
                TutorialModalOverlay(
                    state = state,
                    onAdvance = { viewModel.advanceModal() },
                    onCastSpell = { viewModel.castSpell() },
                    onStartListening = {
                        viewModel.advanceModal() // will start listening for PRACTICE_CAST_PROMPT
                    },
                    onBack = {
                        viewModel.musicManager?.playTrack(MusicManager.TRACK_TITLE)
                        onBack()
                    },
                    onStartNewGame = onStartNewGame
                )
            }
        }
    }
}

// =========================================================================
// TOP BAR
// =========================================================================

@Composable
private fun TutorialTopBar(
    stepIndex: Int,
    totalSteps: Int,
    isListening: Boolean,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RetroDeepSlate)
            .border(1.dp, RetroBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Exit Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF263238))
                    .border(1.dp, Color(0xFF546E7A), RoundedCornerShape(6.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◀ EXIT",
                    color = Color(0xFFCFD8DC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Step Information
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TRAINING ARENA",
                    color = LogosGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "STEP ${stepIndex + 1} OF $totalSteps",
                    color = Color(0xFFB0BEC5),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Status Indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isListening) Color(0xFFB71C1C) else Color(0xFF1B5E20))
                    .border(1.dp, if (isListening) Color(0xFFEF5350) else Color(0xFF66BB6A), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isListening) "🎙️ LISTENING" else "⚔️ TUTORIAL",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Progress Bar
        LinearProgressIndicator(
            progress = { (stepIndex + 1).toFloat() / totalSteps.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = LogosGold,
            trackColor = Color(0xFF212130)
        )
    }
}

// =========================================================================
// BOTTOM COMMAND DOCK
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TutorialCommandDock(
    state: TutorialBattleUiState,
    onCastSpell: (String) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
) {
    val activeMember = state.party.firstOrNull { it.id == state.activePartyMemberId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(RetroPanel)
            .border(1.dp, RetroBorderGold, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Active Hero Banner & MP bar
        if (activeMember != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(activeMember.avatarTint)
                    )
                    Text(
                        text = "${activeMember.name.uppercase()} — ${activeMember.loreClass.uppercase()}",
                        color = LogosGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "MP ${activeMember.currentMp}/${activeMember.maxMp}",
                    color = MpBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Hero Spells Bar (Clickable)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                activeMember.spells.forEach { spell ->
                    val isTargetSpell = (state.currentStep as? TutorialBattleStep.PracticeCast)?.let {
                        it.spellName.equals(spell.name, ignoreCase = true)
                    } ?: false
                    val category = spell.category

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isTargetSpell) Color(0xFF3E2723) else RetroDeepSlate)
                            .border(
                                width = if (isTargetSpell) 1.5.dp else 1.dp,
                                color = if (isTargetSpell) LogosGold else spell.school.themeColor.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable(enabled = !state.isAnimating) {
                                onCastSpell(spell.name)
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Category Badge: Icon + Label (e.g. ⚔️ ATK, 💥 AOE, 💚 HEAL, 🛡️ DEF, ✨ MANA)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(category.tagBg)
                                    .border(0.5.dp, category.tagColor.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = category.icon,
                                        fontSize = 8.sp
                                    )
                                    Text(
                                        text = category.label,
                                        color = category.tagColor,
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Text(
                                text = spell.name,
                                color = if (isTargetSpell) LogosGold else Color.White,
                                fontSize = 10.sp,
                                fontWeight = if (isTargetSpell) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                            if (spell.mpCost > 0) {
                                Text(
                                    text = "${spell.mpCost}m",
                                    color = MpBlue,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Text(
                                    text = "FREE",
                                    color = Color(0xFF81C784),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // Voice Command Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF101018))
                    .border(1.dp, RetroBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when {
                        state.isListening -> "🎙️ Speak: \"${state.modalHintChant ?: "Fireball"}\"..."
                        state.liveTranscript.isNotBlank() -> "Heard: \"${state.liveTranscript}\""
                        else -> "Tip: Speak spell names aloud or tap buttons"
                    },
                    color = if (state.isListening) LogosGlow else Color(0xFF90A4AE),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (state.isListening) Color(0xFFB71C1C) else Color(0xFF0D47A1))
                    .border(1.dp, if (state.isListening) Color(0xFFEF5350) else Color(0xFF42A5F5), RoundedCornerShape(6.dp))
                    .clickable {
                        if (state.isListening) onStopListening() else onStartListening()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.isListening) "STOP" else "🎤 MIC",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// =========================================================================
// CENTERED MODAL DIALOGUE CARD OVERLAY
// =========================================================================

@Composable
private fun TutorialModalOverlay(
    state: TutorialBattleUiState,
    onAdvance: () -> Unit,
    onCastSpell: () -> Unit,
    onStartListening: () -> Unit,
    onBack: () -> Unit,
    onStartNewGame: () -> Unit
) {
    val context = LocalContext.current
    val speaker = state.modalSpeaker
    val portraitBitmap = remember(speaker.portraitAsset) {
        speaker.portraitAsset?.let { StoryAssetLoader.loadBitmap(context, it) }
    }

    // Translucent dark scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        // Centered Card Container
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(RetroPanel)
                .border(2.dp, RetroBorderGold, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Modal Header: Speaker Portrait, Name, and Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A2E))
                            .border(2.dp, speaker.themeColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (portraitBitmap != null) {
                            Image(
                                bitmap = portraitBitmap,
                                contentDescription = speaker.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = speaker.name.take(1),
                                color = speaker.themeColor,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = speaker.name.uppercase(),
                            color = speaker.themeColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = speaker.title,
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "[ ${state.modalTitle} ]",
                            color = LogosGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Divider line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(RetroBorder)
                )

                // Message Body
                Text(
                    text = state.modalMessage,
                    color = Color(0xFFECEFF1),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = FontFamily.SansSerif
                )

                // Card Type Specific Context Cards
                when (state.modalType) {
                    ModalCardType.CHARACTER_JOIN -> {
                        val charDef = (state.currentStep as? TutorialBattleStep.CharacterJoin)?.characterDef
                        if (charDef != null) {
                            CharacterJoinPreviewCard(charDef)
                        }
                    }
                    ModalCardType.SPELL_OVERVIEW -> {
                        val charDef = (state.currentStep as? TutorialBattleStep.SpellOverview)?.characterDef
                        if (charDef != null) {
                            SpellOverviewPreviewCard(charDef)
                        }
                    }
                    ModalCardType.PRACTICE_CAST_PROMPT -> {
                        val curStep = state.currentStep as? TutorialBattleStep.PracticeCast
                        PracticeCastPromptCard(
                            step = curStep,
                            spellDef = state.modalSpellToCast,
                            hintChant = state.modalHintChant
                        )
                    }
                    ModalCardType.CAST_RESULT -> {
                        val record = state.lastCastRecord
                        if (record != null) {
                            CastResultCard(record)
                        }
                    }
                    ModalCardType.COMPLETION -> {
                        CompletionBannerCard()
                    }
                    else -> { /* NARRATOR_INTRO, ACTION_RESULT, ENEMY_ATTACK_INTRO, ENEMY_ATTACK_RESULT */ }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Footer Action Buttons
                when (state.modalType) {
                    ModalCardType.PRACTICE_CAST_PROMPT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onStartListening,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0D47A1)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "🎙️ SPEAK COMMAND",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Button(
                                onClick = onCastSpell,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFB71C1C)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "⚡ TAP TO CAST",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    ModalCardType.COMPLETION -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onStartNewGame,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFB71C1C)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "⚔️ BEGIN YOUR JOURNEY",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            OutlinedButton(
                                onClick = onBack,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "🏠 RETURN TO TITLE",
                                    color = LogosGold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    else -> {
                        Button(
                            onClick = onAdvance,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1B5E20)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "CONTINUE ➔",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SUB-CARD PREVIEWS
// =========================================================================

@Composable
private fun CharacterJoinPreviewCard(charDef: com.voicerpg.engine.engine.TutorialCharacterDef) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF10101C))
            .border(1.dp, charDef.avatarTint.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "NEW COMPANION RECRUITED",
                color = charDef.avatarTint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${charDef.name} (${charDef.loreClass})",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "HP: ${charDef.maxHp}", color = Color(0xFF81C784), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(text = "MP: ${charDef.maxMp}", color = MpBlue, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(text = "Speed: ${charDef.speed}", color = LogosGold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun SpellOverviewPreviewCard(charDef: com.voicerpg.engine.engine.TutorialCharacterDef) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        charDef.spells.forEach { spell ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF10101C))
                    .border(1.dp, spell.school.themeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = spell.name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "[${spell.school.displayName}]",
                                color = spell.school.themeColor,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = spell.description,
                            color = Color(0xFF90A4AE),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = if (spell.mpCost > 0) "${spell.mpCost} MP" else "FREE",
                        color = if (spell.mpCost > 0) MpBlue else Color(0xFF81C784),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeCastPromptCard(
    step: TutorialBattleStep.PracticeCast?,
    spellDef: com.voicerpg.engine.engine.TutorialSpellDef?,
    hintChant: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF161628))
            .border(1.dp, LogosGold, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "VOICE COMMAND TARGET",
                color = LogosGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "“${hintChant ?: spellDef?.name ?: "Spell"}”",
                    color = FrostCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                if (spellDef != null) {
                    Text(
                        text = "${spellDef.school.displayName} (${spellDef.mpCost} MP)",
                        color = spellDef.school.themeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (step?.isResonanceDemo == true) {
                Text(
                    text = if (step.resonanceDemoIndex == 0) {
                        "Resonance Test Part 1: Say ONLY the spell name ('Fireball') to see baseline power."
                    } else {
                        "Resonance Test Part 2: Now unleash elaborate words (e.g. 'Searing cosmic blaze, consume my enemy!') to earn FORTISSIMO +60% bonus!"
                    },
                    color = HolyYellow,
                    fontSize = 11.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun CastResultCard(record: com.voicerpg.engine.viewmodel.TutorialCastRecord) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF10101C))
            .border(1.dp, LogosGold, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RESONANCE: ${record.resonanceTier.title.uppercase()}",
                    color = when (record.resonanceTier) {
                        ResonanceTier.TRANSCENDENTAL -> Color(0xFFE040FB)
                        ResonanceTier.MYTHIC -> Color(0xFFFF1744)
                        ResonanceTier.MASTER -> Color(0xFFFFD700)
                        ResonanceTier.ADEPT -> Color(0xFF64B5F6)
                        ResonanceTier.BASIC -> Color(0xFFB0BEC5)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "${record.damage} DMG",
                    color = Color(0xFFEF5350),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "Spoken: \"${record.utterance}\"",
                color = Color(0xFFECEFF1),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            if (record.bonusPercent > 0) {
                Text(
                    text = "+${record.bonusPercent}% Power Bonus from Vivid Incantation!",
                    color = HolyYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun CompletionBannerCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A00))
            .border(1.dp, LogosGold, RoundedCornerShape(8.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "👑 MASTERY ACHIEVED 👑",
                color = LogosGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            val manifest = com.voicerpg.engine.content.GameContent.manifest
            val completionMsg = manifest.tutorialCompletionText.ifBlank {
                "All companions assembled. Resonance mechanics understood. You are prepared to enter the world of ${manifest.worldName}."
            }
            Text(
                text = completionMsg,
                color = Color(0xFFFFF9C4),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// =========================================================================
// FLOATING NUMBER ITEM
// =========================================================================

@Composable
private fun TutorialFloatingNumberItem(ft: TutorialFloatingText) {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(ft.id) {
        launch {
            offsetY.animateTo(-50f, animationSpec = tween(950))
        }
        launch {
            delay(550)
            alpha.animateTo(0f, animationSpec = tween(400))
        }
    }

    Box(
        modifier = Modifier
            .offset {
                // If it's damage to enemy dummy, place it on the right side; if heal/damage to hero, place it on left
                val isHeroTarget = !ft.targetId.startsWith("dummy") && !ft.targetId.startsWith("enemy")
                val baseX = if (isHeroTarget) 120 else 600
                val baseY = if (ft.targetId.contains("beta")) 380 else if (ft.targetId.contains("gamma")) 460 else 300
                IntOffset(
                    x = baseX,
                    y = (baseY + offsetY.value).roundToInt()
                )
            }
    ) {
        Text(
            text = ft.text,
            color = ft.color.copy(alpha = alpha.value),
            fontSize = if (ft.isCrit) 18.sp else 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}
