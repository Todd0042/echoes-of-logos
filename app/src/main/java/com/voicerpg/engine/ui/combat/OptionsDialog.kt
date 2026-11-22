package com.voicerpg.engine.ui.combat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.LogosGlow
import com.voicerpg.engine.ui.theme.RetroBlack
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroBorderGold
import com.voicerpg.engine.ui.theme.RetroPanel

@Composable
fun OptionsDialog(
    isOpen: Boolean,
    isEyesFreeMode: Boolean,
    isNarrationEnabled: Boolean,
    isAutoPlayEnabled: Boolean = false,
    isCombatNarrationEnabled: Boolean = true,
    isPocketGuardEnabled: Boolean = true,
    isReadChoicesEnabled: Boolean,
    isAutoReadHubChoices: Boolean = true,
    isCharacterPitchEnabled: Boolean,
    isSpeakerAttributionEnabled: Boolean,
    onToggleSpeakerAttribution: () -> Unit,
    speechRate: Float,
    isAutoListen: Boolean,
    isChimeMuted: Boolean,
    isMusicEnabled: Boolean = true,
    musicVolume: Float = 0.55f,
    onToggleEyesFreeMode: () -> Unit,
    onToggleNarration: () -> Unit,
    onToggleAutoPlay: () -> Unit = {},
    onToggleCombatNarration: () -> Unit = {},
    onTogglePocketGuard: () -> Unit = {},
    onToggleReadChoices: () -> Unit,
    onToggleAutoReadHubChoices: () -> Unit = {},
    onToggleCharacterPitch: () -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onToggleAutoListen: () -> Unit,
    onToggleChimeMute: () -> Unit,
    onToggleMusic: () -> Unit = {},
    onMusicVolumeChange: (Float) -> Unit = {},
    onOpenVoiceSettings: (() -> Unit)? = null,
    onOpenVoiceAssignment: (() -> Unit)? = null,
    onOpenLanguageSelection: (() -> Unit)? = null,
    onApplyPreset: (PlaystylePreset) -> Unit = {},
    onTriggerCheaterDialogue: () -> Unit = {},
    isDebugWarpEnabled: Boolean = true,
    onOpenDebugWarp: (() -> Unit)? = null,
    onReturnToTitle: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    if (!isOpen) return

    val activePreset = when {
        isEyesFreeMode && isPocketGuardEnabled && isCombatNarrationEnabled && isAutoListen -> PlaystylePreset.POCKET_WALK
        isEyesFreeMode && !isPocketGuardEnabled && isCombatNarrationEnabled && isAutoListen -> PlaystylePreset.STORYBOOK
        !isEyesFreeMode && !isCombatNarrationEnabled && !isAutoListen -> PlaystylePreset.CLASSIC_TACTICAL
        else -> PlaystylePreset.CUSTOM
    }

    var isAdvancedOpen by remember { mutableStateOf(false) }
    var showCheaterDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RetroBlack.copy(alpha = 0.96f))
                .border(2.dp, RetroBorderGold, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "⚙️ GAME & AUDIO OPTIONS",
                            color = LogosGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Voice-Controlled • Narration • Pocket Mode",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Close icon button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(RetroPanel)
                            .border(1.dp, RetroBorder, CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✖",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // =============================================================
                // Playstyle Presets (1-Tap Profiles)
                // =============================================================
                Text(
                    text = "🎯 CHOOSE YOUR PLAYSTYLE",
                    color = LogosGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        PlaystylePreset.POCKET_WALK to Color(0xFF64B5F6),
                        PlaystylePreset.STORYBOOK to Color(0xFFFFD54F),
                        PlaystylePreset.CLASSIC_TACTICAL to Color(0xFF81C784)
                    ).forEach { (preset, accent) ->
                        val isSelected = activePreset == preset
                        PlaystylePresetCard(
                            preset = preset,
                            isSelected = isSelected,
                            accentColor = accent,
                            onClick = { onApplyPreset(preset) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // =============================================================
                // Collapsible Advanced Settings Accordion
                // =============================================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141923))
                        .border(1.dp, if (isAdvancedOpen) LogosGold else RetroBorder, RoundedCornerShape(8.dp))
                        .clickable { isAdvancedOpen = !isAdvancedOpen }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⚙️ DETAILED SETTINGS",
                                    color = if (isAdvancedOpen) LogosGold else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (activePreset == PlaystylePreset.CUSTOM) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFFFFB74D))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "CUSTOMIZED",
                                            color = RetroBlack,
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                            if (!isAdvancedOpen) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Narration speed, voice pitch, volumes, & companion voices",
                                    color = Color.Gray,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Text(
                            text = if (isAdvancedOpen) "▲ HIDE" else "▼ EXPAND",
                            color = if (isAdvancedOpen) LogosGold else Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (isAdvancedOpen) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // =============================================================
                    // Section 1: Story & Dialogue Narration
                    // =============================================================
                    Text(
                        text = "📖 STORY & DIALOGUE NARRATION",
                        color = LogosGlow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                // Toggle: Read Dialogue Aloud
                OptionToggleRow(
                    title = "📖 Read Dialogue Aloud",
                    subtitle = "Phone reads story narration and character voices aloud (screen on or off).",
                    voiceHint = "Voice command: \"Toggle narration\" or \"Narration\"",
                    checked = isNarrationEnabled,
                    activeColor = Color(0xFFFFD54F),
                    onCheckedChange = { onToggleNarration() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle: Auto-Play / Story Mode
                OptionToggleRow(
                    title = "🤖 Auto-Play / Story Mode",
                    subtitle = "Phone automatically reads story, picks choices hands-free, and executes combat turns across any save slot.",
                    voiceHint = "Voice command: \"Auto Play\" or \"Story Mode\"",
                    checked = isAutoPlayEnabled,
                    activeColor = Color(0xFFBA68C8),
                    onCheckedChange = { onToggleAutoPlay() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle: Read Combat Actions Aloud
                OptionToggleRow(
                    title = "⚔️ Read Combat Actions Aloud",
                    subtitle = "Phone speaks player turns, spell damage, and enemy attacks aloud (screen on or off).",
                    voiceHint = "Voice command: \"Combat narration\" or \"Read combat\"",
                    checked = isCombatNarrationEnabled,
                    activeColor = Color(0xFFFF7043),
                    onCheckedChange = { onToggleCombatNarration() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle: Read Dialogue Choices
                OptionToggleRow(
                    title = "🔢 Read Dialogue Choices",
                    subtitle = "Speaks available response options after the dialogue line finishes.",
                    voiceHint = "Voice command: \"Read choices\" or \"Toggle choices\"",
                    checked = isReadChoicesEnabled,
                    activeColor = Color(0xFF80D8FF),
                    onCheckedChange = { onToggleReadChoices() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle: Character Voice Pitch Modulation
                OptionToggleRow(
                    title = "🎭 Character Voice Pitch",
                    subtitle = "Modulates pitch per character to create distinctive vocal profiles.",
                    voiceHint = "Dynamic pitch shifting",
                    checked = isCharacterPitchEnabled,
                    activeColor = Color(0xFFCE93D8),
                    onCheckedChange = { onToggleCharacterPitch() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle: Speaker Name Attribution
                OptionToggleRow(
                    title = "🗣️ Speaker Names Aloud",
                    subtitle = "Narrator announces who is talking aloud. Off keeps each character's distinct voice, just without the name.",
                    voiceHint = "Voice command: \"Speaker names\" or \"Who is speaking\"",
                    checked = isSpeakerAttributionEnabled,
                    activeColor = Color(0xFFFFB74D),
                    onCheckedChange = { onToggleSpeakerAttribution() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Narration Playback Speed (TTS) Selector
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RetroPanel.copy(alpha = 0.8f))
                        .border(1.dp, RetroBorderGold.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🎙️ Narration Playback Speed (TTS)",
                                    color = LogosGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Pace for story reading, character dialogue, & battle announcements.",
                                    color = Color.LightGray,
                                    fontSize = 9.5.sp,
                                    lineHeight = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "%.2fx".format(speechRate),
                                color = LogosGold,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                0.85f to "0.85x Slow",
                                1.05f to "1.05x Normal",
                                1.25f to "1.25x Fast",
                                1.50f to "1.50x Rapid"
                            ).forEach { (rate, label) ->
                                val isSelected = kotlin.math.abs(speechRate - rate) < 0.08f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) LogosGold else RetroBlack)
                                        .border(
                                            1.dp,
                                            if (isSelected) LogosGold else RetroBorder,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onSpeechRateChange(rate) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) RetroBlack else Color.LightGray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                if (onOpenLanguageSelection != null) {
                    val currentLangCode = com.voicerpg.engine.localization.TranslationManager.currentLanguage.value
                    val currentRegCode = com.voicerpg.engine.localization.TranslationManager.currentRegion.value
                    val langOpt = com.voicerpg.engine.model.LanguageCatalog.getLanguage(currentLangCode)
                    val dialectDesc = if (currentRegCode.isNotBlank()) " ($currentRegCode)" else ""

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(RetroPanel.copy(alpha = 0.8f))
                            .border(1.dp, LogosGold, RoundedCornerShape(8.dp))
                            .clickable { onOpenLanguageSelection() }
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🌐 Language & Dialect",
                                    color = LogosGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Current: ${langOpt.nativeName} - ${langOpt.displayName}$dialectDesc",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CHANGE ➔",
                                color = LogosGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                if (onOpenVoiceSettings != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(RetroPanel.copy(alpha = 0.8f))
                            .border(1.dp, RetroBorderGold, RoundedCornerShape(8.dp))
                            .clickable { onOpenVoiceSettings() }
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🎙️ Install Character Voices",
                                    color = LogosGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Open system Text-to-Speech settings to download additional English voices for your story characters and adversaries.",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "OPEN ➔",
                                color = LogosGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                if (onOpenVoiceAssignment != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(RetroPanel.copy(alpha = 0.8f))
                            .border(1.dp, Color(0xFF64B5F6), RoundedCornerShape(8.dp))
                            .clickable { onOpenVoiceAssignment() }
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🎭 Assign & Customize Character Voices",
                                    color = Color(0xFF90CAF9),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Audition character quotes, cycle voice models for characters and adversaries without restarting your game.",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ASSIGN ➔",
                                color = Color(0xFF90CAF9),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // =============================================================
                // Section 2: Ambient Music & Sound
                // =============================================================
                Text(
                    text = "🎵 AMBIENT MUSIC & SOUND",
                    color = LogosGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle: Background Music
                OptionToggleRow(
                    title = "🎵 Background Music (BGM)",
                    subtitle = "Continuous ambient soundtrack per Act & battle arena. Automatically ducks during narration.",
                    voiceHint = "Voice command: \"Toggle music\" or \"Music on/off\"",
                    checked = isMusicEnabled,
                    activeColor = Color(0xFFFFD54F),
                    onCheckedChange = { onToggleMusic() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Music Volume Selector
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RetroPanel.copy(alpha = 0.8f))
                        .border(1.dp, RetroBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔊 Music Volume",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${(musicVolume * 100).toInt()}%",
                                color = LogosGold,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                0.25f to "25% Soft",
                                0.55f to "55% Balanced",
                                0.80f to "80% Epic",
                                1.00f to "100% Max"
                            ).forEach { (vol, label) ->
                                val isSelected = kotlin.math.abs(musicVolume - vol) < 0.12f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) LogosGold else RetroBlack)
                                        .border(
                                            1.dp,
                                            if (isSelected) LogosGold else RetroBorder,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onMusicVolumeChange(vol) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) RetroBlack else Color.LightGray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // =============================================================
                // Section 3: Pocket & Hands-Free Accessibility
                // =============================================================
                Text(
                    text = "🎧 POCKET & ACCESSIBILITY",
                    color = LogosGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle: Screenless / Pocket Mode
                OptionToggleRow(
                    title = "🎧 Screenless Pocket Mode",
                    subtitle = "Hands-free audio play: automatically speaks choices, advances dialogue, and opens mic without touching the screen.",
                    voiceHint = "Voice command: \"Pocket mode\" or \"Eyes free\"",
                    checked = isEyesFreeMode,
                    activeColor = Color(0xFF64B5F6),
                    onCheckedChange = { onToggleEyesFreeMode() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle: AMOLED Pocket Touch Guard (Screen Blanking & Lock)
                OptionToggleRow(
                    title = "🔒 AMOLED Screen Guard / Lock",
                    subtitle = "When Pocket Mode is active, blanks display with true-black touch guard to prevent pocket taps. Turn OFF to keep screen visible while audio plays.",
                    voiceHint = "Voice command: \"Screen guard\" or \"Touch lock\"",
                    checked = isPocketGuardEnabled,
                    activeColor = Color(0xFF81D4FA),
                    onCheckedChange = { onTogglePocketGuard() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle: Hands-Free Auto Listen
                OptionToggleRow(
                    title = "👂 Hands-Free Auto-Listen",
                    subtitle = "Automatically opens microphone after narrator finishes speaking. Zero touch needed.",
                    voiceHint = "Voice command: \"Auto listen\" or \"Hands free\"",
                    checked = isAutoListen,
                    activeColor = Color(0xFF81C784),
                    onCheckedChange = { onToggleAutoListen() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle: Chime Mute
                OptionToggleRow(
                    title = "🔔 Microphone Chime Mute",
                    subtitle = "Silences system mic beeps for stealthy, uninterrupted chanting.",
                    voiceHint = "Default: Muted",
                    checked = isChimeMuted,
                    activeColor = LogosGold,
                    onCheckedChange = { onToggleChimeMute() }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // =============================================================
                // Section 3: Voice Command Cheat Sheet
                // =============================================================
                Text(
                    text = "VOICE COMMAND CHEAT SHEET",
                    color = LogosGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RetroPanel.copy(alpha = 0.9f))
                        .border(1.dp, RetroBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        VoiceCommandItem(command = "📖 \"Narration\"", desc = "Toggles dialogue reading on/off")
                        VoiceCommandItem(command = "🔢 \"Read Choices\"", desc = "Toggles reading choices on/off")
                        VoiceCommandItem(command = "⏭️ \"Next\" / \"Continue\"", desc = "Advances story dialogue")
                        VoiceCommandItem(command = "📢 \"Status\" / \"Report\"", desc = "Announces HP of party & living enemies")
                        VoiceCommandItem(command = "👁️ \"Enemies\" / \"Targets\"", desc = "Scans foes and states targeted enemy")
                        VoiceCommandItem(command = "🛡️ \"Party\" / \"Allies\"", desc = "Checks health & state of fellowship")
                        VoiceCommandItem(command = "🎧 \"Pocket Mode\"", desc = "Toggles audio-guided combat")
                        VoiceCommandItem(command = "👂 \"Auto Listen\"", desc = "Toggles hands-free turn mic on/off")
                        VoiceCommandItem(command = "🎭 \"Assign Voices\"", desc = "Opens character voice customization screen")
                        VoiceCommandItem(command = "🗣️ \"Speaker Names\"", desc = "Toggles spoken speaker name announcements")
                        VoiceCommandItem(command = "🎵 \"Music\" / \"Toggle Music\"", desc = "Toggles ambient background music on/off")
                        VoiceCommandItem(command = "🏠 \"Title Screen\" / \"Main Menu\"", desc = "Saves and returns to Title Screen")
                        VoiceCommandItem(command = "⚙️ \"Options\" / \"Close\"", desc = "Opens or closes this settings screen")
                        VoiceCommandItem(command = "❓ \"Help\"", desc = "Spoken audio overview of voice commands")
                    }
                }
                } // End if (isAdvancedOpen)

                Spacer(modifier = Modifier.height(16.dp))

                // Close / Resume Button
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LogosGold,
                        contentColor = RetroBlack
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "RESUME GAME",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }

                if (onReturnToTitle != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onClose()
                            onReturnToTitle()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, RetroBorder, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "💾 SAVE & RETURN TO TITLE",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }

                // =============================================================
                // Toggle: Auto-Read Hub Choices in Screenless Pocket Mode
                // =============================================================
                Spacer(modifier = Modifier.height(10.dp))
                OptionToggleRow(
                    title = "🎧 Pocket Mode Auto-Read Choices",
                    subtitle = "When Screenless Pocket Mode is active, automatically reads all available story choices upon visiting story hubs.",
                    voiceHint = "Pocket Mode default: ON",
                    checked = isAutoReadHubChoices,
                    activeColor = Color(0xFF64B5F6),
                    onCheckedChange = onToggleAutoReadHubChoices
                )

                // =============================================================
                // Universal Cheat Code: up up down down left right left right b a start
                // =============================================================
                if (onOpenDebugWarp != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF160E1E))
                            .border(1.dp, Color(0xFFAB47BC).copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                            .clickable {
                                showCheaterDialog = true
                                onTriggerCheaterDialogue()
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎮 up up down down left right left right b a start",
                                color = Color(0xFFE1BEE7),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "➔",
                                color = Color(0xFFCE93D8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    // Read-aloud Cheater Acknowledgment Modal Dialog
    if (showCheaterDialog) {
        Dialog(onDismissRequest = { showCheaterDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RetroBlack.copy(alpha = 0.98f))
                    .border(2.dp, Color(0xFFE040FB), RoundedCornerShape(12.dp))
                    .padding(18.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🚨 CHEATER DETECTED 🚨",
                        color = Color(0xFFE040FB),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "The Narrator catches you red-handed...",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1428))
                            .border(1.dp, Color(0xFF7B1FA2), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "\"Cheater detected! You have invoked the ancient Konami incantation to bend time, space, and narrative destiny. Do you boldly embrace your dishonor and enter the Debug Sanctum?\"",
                            color = Color(0xFFF3E5F5),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showCheaterDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF263238),
                                contentColor = Color.LightGray
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "REPENT",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = {
                                showCheaterDialog = false
                                onOpenDebugWarp?.invoke()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8E24AA),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "WARP ➔",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionToggleRow(
    title: String,
    subtitle: String,
    voiceHint: String,
    checked: Boolean,
    activeColor: Color,
    onCheckedChange: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RetroPanel.copy(alpha = 0.8f))
            .border(
                1.dp,
                if (checked) activeColor.copy(alpha = 0.8f) else RetroBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable { onCheckedChange() }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (checked) activeColor else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = voiceHint,
                    color = Color(0xFF80D8FF),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = activeColor,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF1E1E28)
                )
            )
        }
    }
}

@Composable
private fun VoiceCommandItem(
    command: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = command,
            color = LogosGold,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = desc,
            color = Color.LightGray,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

enum class PlaystylePreset(val title: String, val badge: String, val description: String) {
    POCKET_WALK(
        "🎧 POCKET WALK",
        "SCREENLESS",
        "Phone in pocket: AMOLED screen locked & dark, auto-listen mic, full combat & story narration aloud."
    ),
    STORYBOOK(
        "📖 STORYBOOK",
        "AUDIO DRAMA",
        "Hands-free listening: screen stays on & visible, reads all dialogue & combat events aloud, auto-advances."
    ),
    CLASSIC_TACTICAL(
        "🎮 CLASSIC TACTICAL",
        "VISUAL PLAY",
        "Traditional RPG: screen visible, visual combat text, manual tap-to-talk mic, zero battle voice clutter."
    ),
    CUSTOM(
        "⚙️ CUSTOM",
        "CUSTOM",
        "Fine-tuned custom balance of narration, speech speed, and accessibility controls."
    )
}

@Composable
private fun PlaystylePresetCard(
    preset: PlaystylePreset,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.15f) else RetroPanel.copy(alpha = 0.8f))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accentColor else RetroBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = preset.title,
                    color = if (isSelected) accentColor else Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) accentColor else Color(0xFF263238))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isSelected) "✓ ACTIVE" else preset.badge,
                        color = if (isSelected) RetroBlack else Color.LightGray,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = preset.description,
                color = Color.LightGray,
                fontSize = 9.5.sp,
                lineHeight = 12.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
