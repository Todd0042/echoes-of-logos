package com.voicerpg.engine.ui.setup

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.audio.SpeechState
import com.voicerpg.engine.model.DialogueSpeaker
import com.voicerpg.engine.ui.theme.FrostCyan
import com.voicerpg.engine.ui.theme.HolyYellow
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.LogosGlow
import com.voicerpg.engine.ui.theme.RetroBlack
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroBorderGold
import com.voicerpg.engine.ui.theme.RetroPanel
import com.voicerpg.engine.ui.theme.ShadowPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Initial Audio & Voice Setup Screen.
 * Appears before Character Creation on first launch.
 * Explains companion voice roles, prompts optional installation of additional TTS voices,
 * and provides switches for accessibility, Screenless Pocket Mode, and auto-listen.
 */
@Composable
fun AudioSetupScreen(
    combatNarrator: CombatNarrator,
    speechManager: SpeechManager,
    onProceed: () -> Unit,
    isFromGame: Boolean = false,
    isNewGameFlow: Boolean = false,
    onBack: (() -> Unit)? = null,
    onVoiceChanged: () -> Unit = {},
    onOpenLanguageSelection: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val speechState by speechManager.speechState.collectAsState()
    val isAutoListen by speechManager.isAutoListen.collectAsState()
    val isChimeMuted by speechManager.isChimeMuted.collectAsState()

    val isEyesFreeMode by combatNarrator.isEyesFreeMode.collectAsState()
    val isNarrationEnabled by combatNarrator.isNarrationEnabled.collectAsState()
    val isReadChoicesEnabled by combatNarrator.isReadChoicesEnabled.collectAsState()
    val isCharacterPitchEnabled by combatNarrator.isCharacterPitchEnabled.collectAsState()
    val speechRate by combatNarrator.speechRate.collectAsState()
    val availableVoiceCount by combatNarrator.availableVoiceCount.collectAsState()
    val installedVoiceCount by combatNarrator.installedVoiceCount.collectAsState()
    var voiceUpdateKey by remember { mutableStateOf(0) }

    // Auto-refresh voices when returning from Android TTS settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                combatNarrator.refreshInstalledVoices()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        combatNarrator.refreshInstalledVoices()
    }

    fun handleVoiceInput(utterance: String) {
        val lower = utterance.lowercase().trim()
        if (lower.isBlank()) return

        // 1. Proceed to Character Creation / Return to Game
        val proceedKeywords = listOf(
            "proceed", "continue", "next", "character creation", "create character",
            "ready", "embark", "awaken", "start", "let's go", "lets go", "confirm", "forward",
            "return", "back", "return to game", "resume", "done", "close"
        )
        if (proceedKeywords.any { lower.contains(it) }) {
            speechManager.cancel()
            onProceed()
            return
        }

        // 2. Install voices
        if (lower.contains("install") || lower.contains("download voice") || lower.contains("more voice") || lower == "yes") {
            combatNarrator.openVoiceSettings(context)
            return
        }

        // 3. Refresh voices
        if (lower.contains("refresh") || lower.contains("rescan") || lower.contains("scan")) {
            combatNarrator.refreshInstalledVoices()
            combatNarrator.speak("Voices rescanned. $installedVoiceCount offline voices verified.", force = true)
            return
        }

        val registered = combatNarrator.getRegisteredSpeakers()
        // 4. Cycle / Switch companion voices
        if (lower.contains("cycle") || lower.contains("switch") || lower.contains("change voice") || lower.contains("next voice")) {
            val targetSpeaker = registered.firstOrNull { speaker ->
                lower.contains(speaker.name.lowercase()) || lower.contains(speaker.id.lowercase())
            }
            if (targetSpeaker != null) {
                combatNarrator.cycleSpeakerVoice(targetSpeaker)
                voiceUpdateKey++
                onVoiceChanged()
                return
            }
        }

        // 5. Preview / sample companion voices
        val previewMatch = registered.firstOrNull { speaker ->
            lower.contains(speaker.name.lowercase()) || lower.contains(speaker.id.lowercase())
        }
        if (previewMatch != null) {
            combatNarrator.previewSpeakerVoice(previewMatch)
            return
        }

        if (lower.contains("exit pocket mode") || lower.contains("disable pocket mode")) {
            combatNarrator.setEyesFreeMode(false)
            combatNarrator.speak("Pocket mode disabled.", force = true)
        } else if (lower.contains("pocket mode") || lower.contains("screenless")) {
            val enabled = combatNarrator.toggleEyesFreeMode()
            if (enabled) speechManager.setAutoListen(true)
            combatNarrator.speak(if (enabled) "Screenless Pocket Mode enabled. Screen locked." else "Pocket mode disabled.", force = true)
        } else if (lower.contains("auto listen") || lower.contains("hands free") || lower.contains("listening")) {
            speechManager.toggleAutoListen()
            val enabled = speechManager.isAutoListen.value
            combatNarrator.speak(if (enabled) "Auto listen enabled." else "Auto listen disabled.", force = true)
        } else if (lower.contains("narration") || lower.contains("read dialogue")) {
            val enabled = combatNarrator.toggleNarration()
            combatNarrator.speak(if (enabled) "Narration enabled." else "Narration disabled.", force = true)
        } else if (lower.contains("read choice") || lower.contains("choices")) {
            val enabled = combatNarrator.toggleReadChoices()
            combatNarrator.speak(if (enabled) "Read choices enabled." else "Read choices disabled.", force = true)
        } else if (lower.contains("character pitch") || lower.contains("pitch")) {
            val enabled = !isCharacterPitchEnabled
            combatNarrator.setCharacterPitchEnabled(enabled)
            combatNarrator.speak(if (enabled) "Character pitch enabled." else "Character pitch disabled.", force = true)
        } else if (lower.contains("chime")) {
            speechManager.toggleChimeMute()
            val muted = speechManager.isChimeMuted.value
            combatNarrator.speak(if (muted) "Listening chime muted." else "Listening chime unmuted.", force = true)
        }

        // Keep listening if auto-listen active
        if (speechManager.isAutoListen.value) {
            coroutineScope.launch {
                delay(300)
                speechManager.startListening { next -> handleVoiceInput(next) }
            }
        }
    }

    LaunchedEffect(isAutoListen) {
        if (isAutoListen) {
            delay(400)
            speechManager.startListening { utterance -> handleVoiceInput(utterance) }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RetroBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pinned Top Return Navigation (when opened from Options in-game)
            if (isFromGame || onBack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RetroPanel)
                            .border(1.dp, LogosGold, RoundedCornerShape(6.dp))
                            .clickable {
                                speechManager.cancel()
                                (onBack ?: onProceed).invoke()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isFromGame) "◀ RETURN TO GAME" else "◀ BACK TO TITLE",
                            color = LogosGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(RetroPanel)
                            .border(1.dp, RetroBorder, CircleShape)
                            .clickable {
                                speechManager.cancel()
                                (onBack ?: onProceed).invoke()
                            },
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
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = if (isFromGame) "🎧 COMPANION VOICES & RESONANCE" else "🎧 AURAL RESONANCE SETUP",
                    color = LogosGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.shadow(8.dp, spotColor = LogosGlow)
                )
            Text(
                text = if (isFromGame) "Live Voice Customization & Speech Settings" else "Configure Companion Voices & Voice Control",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(14.dp))

            // =============================================================
            // Language & Regional Dialect Selection
            // =============================================================
            if (onOpenLanguageSelection != null) {
                val currentLang = com.voicerpg.engine.localization.TranslationManager.currentLanguage.value
                val currentReg = com.voicerpg.engine.localization.TranslationManager.currentRegion.value
                val langOpt = com.voicerpg.engine.model.LanguageCatalog.getLanguage(currentLang)
                val dialectDesc = if (currentReg.isNotBlank()) " ($currentReg)" else ""

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RetroPanel.copy(alpha = 0.9f))
                        .border(1.dp, FrostCyan, RoundedCornerShape(12.dp))
                        .clickable { onOpenLanguageSelection() }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🌐 LANGUAGE & DIALECT",
                                color = FrostCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Current: ${langOpt.nativeName} - ${langOpt.displayName}$dialectDesc",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Tap to switch to any of 59 supported languages & regional accents.",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(FrostCyan)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "CHANGE ➔",
                                color = RetroBlack,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // =============================================================
            // Card 1: The Fellowship & Why Multiple Voices Matter
            // =============================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RetroPanel.copy(alpha = 0.9f))
                    .border(1.dp, RetroBorderGold, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "🎭 VOICES OF THE STORY",
                        color = LogosGlow,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Customize the distinctive voice engine model for each story character and adversary:",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val activeSpeakers = remember(installedVoiceCount) {
                        combatNarrator.getRegisteredSpeakers().ifEmpty {
                            listOf(DialogueSpeaker.NARRATOR)
                        }
                    }

                    activeSpeakers.forEach { speaker ->
                        val currentVoice = remember(voiceUpdateKey, installedVoiceCount) { combatNarrator.getVoiceForSpeaker(speaker) }
                        CompanionVoiceBadge(
                            speaker = speaker,
                            assignedVoiceName = currentVoice?.name,
                            voiceRegion = currentVoice?.let { CombatNarrator.getVoiceRegionCode(it) },
                            onPreview = { combatNarrator.previewSpeakerVoice(speaker) },
                            onCycle = {
                                combatNarrator.cycleSpeakerVoice(speaker)
                                voiceUpdateKey++
                                onVoiceChanged()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Why multiple voices? Android devices often provide only 1 active system voice by default. When extra voices are installed, each companion receives their own distinct voice actor model for full immersion!",
                        color = Color(0xFFB0BEC5),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "✦ Tap \"▶ SAMPLE\" to audition, or \"⇄ SWITCH\" to cycle through your device's installed voices for any character!",
                        color = LogosGold,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =============================================================
            // Card 2: Voice Installation Action
            // =============================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF161624))
                    .border(2.dp, if (installedVoiceCount > 1) Color(0xFF81C784) else LogosGold, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎙️ COMPANION VOICE PACKS",
                            color = LogosGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (installedVoiceCount > 1) Color(0xFF2E7D32).copy(alpha = 0.35f) else Color(0xFFFFB300).copy(alpha = 0.25f))
                                .border(1.dp, if (installedVoiceCount > 1) Color(0xFF81C784) else Color(0xFFFFD54F), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (installedVoiceCount > 1) {
                                    "✨ $installedVoiceCount OFFLINE VOICES VERIFIED"
                                } else {
                                    "⚡ $installedVoiceCount DEFAULT VOICE"
                                },
                                color = if (installedVoiceCount > 1) Color(0xFFA5D6A7) else Color(0xFFFFE082),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (installedVoiceCount > 1) {
                            "Your device has $installedVoiceCount verified offline voices. All companion roles have been populated with distinct physical voice models!"
                        } else {
                            "Would you like to install additional voices now? Tap below to open Android's Text-to-Speech Settings and install Google Voice data."
                        },
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "✦ Note: If Android settings opens without a download icon, all Google voice models are already downloaded on your device.",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Yes, Install Voices Button
                        Button(
                            onClick = { combatNarrator.openVoiceSettings(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (installedVoiceCount > 1) Color(0xFF388E3C) else LogosGold,
                                contentColor = RetroBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(42.dp)
                        ) {
                            Text(
                                text = if (installedVoiceCount > 1) "📥 ADD MORE VOICES" else "📥 YES, INSTALL VOICES",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }

                        // Refresh / Rescan Button
                        Button(
                            onClick = {
                                combatNarrator.refreshInstalledVoices()
                                coroutineScope.launch {
                                    combatNarrator.speak("Voices refreshed. $installedVoiceCount offline voices active.", force = true)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RetroPanel,
                                contentColor = LogosGold
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(42.dp)
                                .border(1.dp, RetroBorder, RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = "🔄 RE-SCAN",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =============================================================
            // Card 3: Voice & Accessibility Controls
            // =============================================================
            Text(
                text = "⚙️ ACCESSIBILITY & PLAYSTYLE TOGGLES",
                color = LogosGlow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Toggle 1: Screenless Pocket Mode
            AudioSetupToggleRow(
                title = "🎧 Screenless Pocket Mode",
                subtitle = "AMOLED true-black touch guard prevents pocket taps, keeps display awake, spoken combat narration, and autoplays non-branching dialogue.",
                voiceHint = "Voice: \"Pocket mode\"",
                checked = isEyesFreeMode,
                activeColor = Color(0xFF69F0AE),
                onCheckedChange = {
                    val enabled = combatNarrator.toggleEyesFreeMode()
                    if (enabled) speechManager.setAutoListen(true)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Toggle 2: Hands-Free Auto-Listen
            AudioSetupToggleRow(
                title = "👂 Hands-Free Auto-Listen",
                subtitle = "Automatically keeps microphone listening until dialogue choices, combat spells, or commands are spoken.",
                voiceHint = "Voice: \"Auto listen\"",
                checked = isAutoListen,
                activeColor = Color(0xFFFF80AB),
                onCheckedChange = { speechManager.toggleAutoListen() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Toggle 3: Read Dialogue Aloud
            AudioSetupToggleRow(
                title = "📖 Read Dialogue Aloud",
                subtitle = "Speaks narrator storytelling and character dialogue through device speakers.",
                voiceHint = "Voice: \"Narration\"",
                checked = isNarrationEnabled,
                activeColor = Color(0xFFFFD54F),
                onCheckedChange = { combatNarrator.toggleNarration() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Toggle 4: Read Choices Aloud
            AudioSetupToggleRow(
                title = "🔢 Read Dialogue Choices",
                subtitle = "Reads available numbered branching choices after dialogue line finishes.",
                voiceHint = "Voice: \"Read choices\"",
                checked = isReadChoicesEnabled,
                activeColor = Color(0xFF80D8FF),
                onCheckedChange = { combatNarrator.toggleReadChoices() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Toggle 5: Character Pitch Inflections
            AudioSetupToggleRow(
                title = "🎭 Character Pitch Modulation",
                subtitle = "Shapes deep baritone knight, soothing healer, and jaunty rogue pitches.",
                voiceHint = "Voice: \"Character pitch\"",
                checked = isCharacterPitchEnabled,
                activeColor = Color(0xFFCE93D8),
                onCheckedChange = { combatNarrator.setCharacterPitchEnabled(!isCharacterPitchEnabled) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Toggle 6: Speech Recognition Chimes
            AudioSetupToggleRow(
                title = "🔔 Microphone Chimes",
                subtitle = "Plays audio cue chime when voice recognition turns on and off.",
                voiceHint = "Voice: \"Toggle chime\"",
                checked = !isChimeMuted,
                activeColor = Color(0xFFFFE082),
                onCheckedChange = { speechManager.toggleChimeMute() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Speech Speed Selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RetroPanel.copy(alpha = 0.85f))
                    .border(1.dp, RetroBorder, RoundedCornerShape(8.dp))
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
                                        .clickable { combatNarrator.setSpeechRate(rate) }
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

            Spacer(modifier = Modifier.height(20.dp))

            // =============================================================
            // Proceed to Character Creation / Return to Game Button
            // =============================================================
            Button(
                onClick = {
                    speechManager.cancel()
                    onProceed()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LogosGold,
                    contentColor = RetroBlack
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = when {
                        isNewGameFlow -> "PROCEED TO CHARACTER CREATION ➔"
                        isFromGame -> "◀ RETURN TO GAME ➔"
                        else -> "◀ RETURN TO TITLE ➔"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Voice Prompt Bar for Hands-free Interaction
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isFromGame) "🎤 Say 'Return', 'Switch [Character]', or 'Install voices'" else "🎤 Say 'Proceed', 'Switch [Character]', or 'Install voices'",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (speechState is SpeechState.Listening) Color(0xFFEF5350) else RetroPanel)
                        .border(1.dp, LogosGold, CircleShape)
                        .clickable {
                            if (speechState is SpeechState.Listening) {
                                speechManager.stopListening()
                            } else {
                                speechManager.startListening { utterance -> handleVoiceInput(utterance) }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (speechState is SpeechState.Listening) "🎙️ LISTENING..." else "🎤 SPEAK",
                        color = LogosGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    }
}

@Composable
private fun CompanionVoiceBadge(
    speaker: DialogueSpeaker,
    assignedVoiceName: String?,
    voiceRegion: String? = null,
    onPreview: () -> Unit,
    onCycle: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val displayName = speaker.name
        val displayTitle = speaker.title
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    color = speaker.themeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "($displayTitle)",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
            val regionTag = when (voiceRegion) {
                "GB" -> "[UK] "
                null, "" -> ""
                else -> "[$voiceRegion] "
            }
            Text(
                text = if (assignedVoiceName != null) "Voice Model: $regionTag$assignedVoiceName" else "Default System Voice",
                color = Color(0xFF90CAF9),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(RetroPanel)
                    .border(1.dp, speaker.themeColor.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .clickable { onPreview() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "▶ SAMPLE",
                    color = speaker.themeColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (onCycle != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RetroPanel)
                        .border(1.dp, LogosGold.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .clickable { onCycle() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "⇄ SWITCH",
                        color = LogosGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioSetupToggleRow(
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
            .background(RetroPanel.copy(alpha = 0.85f))
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
