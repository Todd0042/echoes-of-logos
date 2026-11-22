package com.voicerpg.engine.ui.combat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.audio.SpeechState
import com.voicerpg.engine.engine.SpellChantPresets
import com.voicerpg.engine.model.AcousticProfile
import com.voicerpg.engine.model.CombatPhase
import com.voicerpg.engine.model.ResonanceResult
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.model.Spell
import com.voicerpg.engine.model.SpellSchool
import com.voicerpg.engine.ui.theme.FrostCyan
import com.voicerpg.engine.ui.theme.LightningViolet
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.LogosGlow
import com.voicerpg.engine.ui.theme.RetroBlack
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroBorderGold
import com.voicerpg.engine.ui.theme.RetroPanel

@Composable
fun ResonanceConsole(
    phase: CombatPhase,
    speechState: SpeechState,
    liveTranscript: String,
    lastResonance: ResonanceResult?,
    activePartyMember: com.voicerpg.engine.model.PartyMember?,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onSubmitChant: (String, AcousticProfile?) -> Unit,
    onCycleHero: () -> Unit = {},
    isChimeMuted: Boolean = true,
    onToggleChimeMute: () -> Unit = {},
    isAutoListen: Boolean = false,
    onToggleAutoListen: () -> Unit = {},
    rmsLevel: Float = 0f,
    showDeveloperTools: Boolean = false,
    modifier: Modifier = Modifier
) {
    var typedText by remember { mutableStateOf("") }
    var selectedTier by remember { mutableStateOf(ResonanceTier.TRANSCENDENTAL) }
    val isListening = speechState is SpeechState.Listening
    val isConclusionPhase = phase == CombatPhase.BATTLE_WON || phase == CombatPhase.BATTLE_LOST
    val isInputEnabled = phase == CombatPhase.PLAYER_INPUT || isConclusionPhase

    val pulseTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(RetroPanel)
            .border(1.dp, RetroBorderGold, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Active Character Turn Banner with Switch & Chime Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activePartyMember != null && isInputEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "▶ ACTING: ${activePartyMember.name.uppercase()}",
                        color = LogosGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF263238))
                            .border(1.dp, LogosGold, RoundedCornerShape(4.dp))
                            .clickable { onCycleHero() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "⟳ SWITCH",
                            color = LogosGold,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                Text(
                    text = "COMMAND CONSOLE",
                    color = Color(0xFF90A4AE),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Mode Toggle: Tap-To-Speak vs Auto-Listen
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isAutoListen) Color(0xFF1A237E) else Color(0xFF263238))
                        .border(1.dp, if (isAutoListen) Color(0xFF82B1FF) else Color(0xFF90A4AE), RoundedCornerShape(4.dp))
                        .clickable { onToggleAutoListen() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isAutoListen) "🎙️ AUTO" else "🎙️ TAP-TALK",
                        color = if (isAutoListen) Color(0xFF82B1FF) else Color(0xFFCFD8DC),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Chime Mute Toggle Badge (Top Right of Console)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isChimeMuted) Color(0xFF1B2A20) else Color(0xFF2E1C1D))
                        .border(1.dp, if (isChimeMuted) Color(0xFF69F0AE) else Color(0xFFFF5252), RoundedCornerShape(4.dp))
                        .clickable { onToggleChimeMute() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isChimeMuted) "🔇 CHIME OFF" else "🔔 CHIME ON",
                        color = if (isChimeMuted) Color(0xFF69F0AE) else Color(0xFFFF8A80),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Normal Battle Controls: Active Character Spell Buttons
        if (activePartyMember != null && isInputEnabled && activePartyMember.spells.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activePartyMember.spells.forEach { spell ->
                    val hasEnoughMp = activePartyMember.currentMp >= spell.mpCost
                    TacticalSpellButton(
                        spell = spell,
                        hasEnoughMp = hasEnoughMp,
                        enabled = isInputEnabled,
                        onClick = {
                            val rolledTier = rollWeightedResonanceTier()
                            val preset = SpellChantPresets.getPreset(spell, rolledTier)
                            onSubmitChant(preset.chantText, preset.acousticProfile)
                        }
                    )
                }
            }
        }

        // Resonance Meter Bar
        ResonanceMeterBar(lastResonance = lastResonance)

        Spacer(modifier = Modifier.height(8.dp))

        // Live Voice Transcription Box with Dynamic Equalizer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isListening) Color(0xFF0F1B29) else RetroBlack)
                .border(
                    if (isListening) 1.5.dp else 1.dp,
                    if (isListening) FrostCyan else RetroBorder,
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayPrompt = when {
                    isListening -> if (liveTranscript.isBlank()) "🔴 READY — CHANT INCANTATION NOW!" else liveTranscript
                    speechState is SpeechState.Standby -> "⏸️ STANDBY — TAP TO CHANT"
                    speechState is SpeechState.Processing -> "⚡ Evaluating Logos resonance..."
                    speechState is SpeechState.Error -> "Speech error: ${(speechState as SpeechState.Error).message}"
                    liveTranscript.isNotBlank() -> liveTranscript
                    lastResonance != null -> "\"${lastResonance.rawText}\""
                    isConclusionPhase -> "Say \"Commence Story\" or \"Restart Battle\"..."
                    isInputEnabled -> "Tap 🎙️ or select a spell chip to act..."
                    else -> "ATB time ticking... waiting for turn..."
                }

                val textColor = when {
                    isListening -> FrostCyan
                    speechState is SpeechState.Standby -> Color(0xFFFFD54F)
                    speechState is SpeechState.Error -> Color(0xFFFF8A80)
                    lastResonance != null && lastResonance.bonusPercent >= 100 -> LogosGold
                    lastResonance != null && lastResonance.bonusPercent >= 50 -> LightningViolet
                    else -> Color(0xFFB0BEC5)
                }

                Text(
                    text = displayPrompt,
                    color = textColor,
                    fontSize = if (isListening) 11.sp else 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isListening) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isListening) {
                    AudioEqualizerBars(
                        rmsLevel = rmsLevel,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- CHEATER HUD & QUICK-CAST CHIPS (debug/test only) ---
        if (showDeveloperTools) {
        // --- CHEATER HUD: RESONANCE INTENSITY SELECTOR ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141424))
                .border(1.dp, Color(0xFF373752), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHEATER HUD • INTENSITY:",
                    color = Color(0xFF90A4AE),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = when (selectedTier) {
                        ResonanceTier.TRANSCENDENTAL -> "👑 TRANSCENDENTAL (+200% / 3.0x)"
                        ResonanceTier.MYTHIC -> "🟡 MYTHIC (+125% / 2.25x)"
                        ResonanceTier.MASTER -> "🟣 MASTER (+80% / 1.8x)"
                        ResonanceTier.ADEPT -> "🟢 ADEPT (+40% / 1.4x)"
                        ResonanceTier.BASIC -> "⚪ BASIC (+10% / 1.1x)"
                    },
                    color = when (selectedTier) {
                        ResonanceTier.TRANSCENDENTAL -> Color(0xFFFFEE58)
                        ResonanceTier.MYTHIC -> LogosGold
                        ResonanceTier.MASTER -> LightningViolet
                        ResonanceTier.ADEPT -> FrostCyan
                        ResonanceTier.BASIC -> Color.LightGray
                    },
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Intensity Selector Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tiers = listOf(
                    Triple(ResonanceTier.BASIC, "⚪ 10%", Color.Gray),
                    Triple(ResonanceTier.ADEPT, "🟢 40%", FrostCyan),
                    Triple(ResonanceTier.MASTER, "🟣 80%", LightningViolet),
                    Triple(ResonanceTier.MYTHIC, "🟡 125%", LogosGold),
                    Triple(ResonanceTier.TRANSCENDENTAL, "👑 200% MAX", Color(0xFFFFEE58))
                )

                tiers.forEach { (tier, label, color) ->
                    val isSelected = selectedTier == tier
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) color.copy(alpha = 0.25f) else Color(0xFF1E1E30)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) color else Color(0xFF3A3A52),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable(enabled = isInputEnabled) {
                                selectedTier = tier
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) color else Color(0xFFB0BEC5),
                            fontSize = 8.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Action Chips: Active Hero's Spells + Defend + One-Tap Quick Casts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val spells = activePartyMember?.spells ?: emptyList()
            spells.forEach { spell ->
                val preset = SpellChantPresets.getPreset(spell, selectedTier)
                val icon = when (spell.school) {
                    SpellSchool.PYROMANCY -> "🔥"
                    SpellSchool.CRYOMANCY -> "❄️"
                    SpellSchool.ELECTROMANCY -> "⚡"
                    SpellSchool.NATURE -> "🌿"
                    SpellSchool.HOLY -> if (spell.isHeal) "💚" else "⚔️"
                    SpellSchool.SHADOW -> "🗡️"
                    SpellSchool.PHYSICAL -> "🛡️"
                }
                ChantChip(
                    title = "$icon ${spell.name} [${preset.label.take(4)}]",
                    chantText = preset.chantText,
                    acousticProfile = preset.acousticProfile,
                    enabled = isInputEnabled,
                    accentColor = spell.school.themeColor
                ) { text, acoustic -> onSubmitChant(text, acoustic) }
            }

            // Universal tactical action
            ChantChip(
                title = "🛡 Defend",
                chantText = "Defend",
                acousticProfile = null,
                enabled = isInputEnabled,
                accentColor = Color(0xFF90A4AE)
            ) { text, acoustic -> onSubmitChant(text, acoustic) }

            // One-Tap Quick Cast Chips for all 5 intensities on primary spell
            val primarySpell = spells.firstOrNull()
            if (primarySpell != null) {
                listOf(
                    Triple(ResonanceTier.TRANSCENDENTAL, "👑 Cast 200%", Color(0xFFFFEE58)),
                    Triple(ResonanceTier.MYTHIC, "🟡 Cast 125%", LogosGold),
                    Triple(ResonanceTier.MASTER, "🟣 Cast 80%", LightningViolet),
                    Triple(ResonanceTier.ADEPT, "🟢 Cast 40%", FrostCyan),
                    Triple(ResonanceTier.BASIC, "⚪ Cast 10%", Color.LightGray)
                ).forEach { (tier, label, color) ->
                    val quickPreset = SpellChantPresets.getPreset(primarySpell, tier)
                    ChantChip(
                        title = label,
                        chantText = quickPreset.chantText,
                        acousticProfile = quickPreset.acousticProfile,
                        enabled = isInputEnabled,
                        accentColor = color
                    ) { text, acoustic -> onSubmitChant(text, acoustic) }
                }
            }
        }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Controls: Mic Button + Text Input Field
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Push-to-Talk Mic Button
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) Brush.radialGradient(listOf(Color(0xFFFF1744), Color(0xFFD50000)))
                        else if (isInputEnabled) Brush.radialGradient(listOf(LogosGold, Color(0xFFC69214)))
                        else Brush.radialGradient(listOf(Color(0xFF455A64), Color(0xFF263238)))
                    )
                    .border(
                        if (isListening) 2.5.dp else 2.dp,
                        if (isListening) Color.White else if (isInputEnabled) LogosGlow else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        if (isListening) onStopListening() else onStartListening()
                    },
                    enabled = isInputEnabled,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop Recording" else "Tap to Speak",
                        tint = if (isListening) Color.White else if (isInputEnabled) RetroBlack else Color(0xFF90A4AE),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Text Input Field for quiet/simulator testing
            OutlinedTextField(
                value = typedText,
                onValueChange = { typedText = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (isConclusionPhase) "Type 'commence' or 'restart'..." else "Type custom chant...",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                },
                singleLine = true,
                enabled = isInputEnabled,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LogosGold,
                    unfocusedBorderColor = RetroBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (typedText.isNotBlank()) {
                        onSubmitChant(typedText, null)
                        typedText = ""
                    }
                }),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (typedText.isNotBlank()) {
                                onSubmitChant(typedText, null)
                                typedText = ""
                            }
                        },
                        enabled = isInputEnabled && typedText.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Chant", tint = LogosGold)
                    }
                }
            )
        }
    }
}

@Composable
private fun ResonanceMeterBar(lastResonance: ResonanceResult?) {
    val score = lastResonance?.score ?: 0f
    val tier = lastResonance?.tier ?: ResonanceTier.BASIC

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bonus = lastResonance?.bonusPercent ?: 0
            Text(
                text = "RESONANCE: ${tier.title.uppercase()} (+${bonus}%)",
                color = when (tier) {
                    ResonanceTier.TRANSCENDENTAL -> Color(0xFFFFEE58)
                    ResonanceTier.MYTHIC -> LogosGold
                    ResonanceTier.MASTER -> LightningViolet
                    ResonanceTier.ADEPT -> FrostCyan
                    ResonanceTier.BASIC -> Color.Gray
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "MULT: ${String.format("%.2f", lastResonance?.damageMultiplier ?: 1.0f)}x | ${lastResonance?.particleCount ?: 40} VFX",
                color = LogosGlow,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Multi-tier gradient progress track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1A28))
                .border(1.dp, RetroBorder, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(score.coerceIn(0.04f, 1f))
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF546E7A),
                                FrostCyan,
                                LightningViolet,
                                LogosGold
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun ChantChip(
    title: String,
    chantText: String,
    acousticProfile: AcousticProfile?,
    enabled: Boolean,
    accentColor: Color = LogosGlow,
    onClick: (String, AcousticProfile?) -> Unit
) {
    Surface(
        onClick = { onClick(chantText, acousticProfile) },
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF26263B),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) accentColor.copy(alpha = 0.7f) else Color(0xFF4A4A6A)
        ),
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 9.sp,
                color = if (enabled) accentColor else Color.Gray,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun AudioEqualizerBars(
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq_bars")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_pulse"
    )

    val clampedRms = rmsLevel.coerceIn(0f, 1f)
    val heights = listOf(
        ((clampedRms * 0.9f) + pulse * 0.3f).coerceIn(0.15f, 1f),
        ((clampedRms * 1.3f) + pulse * 0.4f).coerceIn(0.2f, 1f),
        ((clampedRms * 0.7f) + pulse * 0.2f).coerceIn(0.15f, 1f),
        ((clampedRms * 1.1f) + pulse * 0.35f).coerceIn(0.18f, 1f)
    )

    Row(
        modifier = modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { factor ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((20 * factor).dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                FrostCyan,
                                Color(0xFF00E676)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Weighted random resonance tier generator:
 * - Basic (+10%): 50% chance
 * - Adept (+40%): 30% chance
 * - Master (+80%): 14% chance
 * - Mythic (+125%): 5% chance
 * - Transcendental (+200% MAX): 1% rare chance
 */
private fun rollWeightedResonanceTier(): ResonanceTier {
    val roll = kotlin.random.Random.nextFloat()
    return when {
        roll < 0.50f -> ResonanceTier.BASIC
        roll < 0.80f -> ResonanceTier.ADEPT
        roll < 0.94f -> ResonanceTier.MASTER
        roll < 0.99f -> ResonanceTier.MYTHIC
        else -> ResonanceTier.TRANSCENDENTAL
    }
}

@Composable
private fun TacticalSpellButton(
    spell: Spell,
    hasEnoughMp: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val category = spell.category
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled && hasEnoughMp) Color(0xFF1E2638) else Color(0xFF181822))
            .border(
                width = 1.dp,
                color = if (enabled && hasEnoughMp) spell.school.themeColor.copy(alpha = 0.85f) else Color(0xFF373752),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
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
                color = if (enabled && hasEnoughMp) Color.White else Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (spell.mpCost > 0) {
                Text(
                    text = "${spell.mpCost}MP",
                    color = if (!hasEnoughMp) Color(0xFFFF5252) else if (enabled) FrostCyan else Color.DarkGray,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = "FREE",
                    color = if (enabled) Color(0xFF81C784) else Color.DarkGray,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
