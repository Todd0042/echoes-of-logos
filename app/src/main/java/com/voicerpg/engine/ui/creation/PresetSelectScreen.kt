package com.voicerpg.engine.ui.creation

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.audio.SfxManager
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.content.parseHexColor
import com.voicerpg.engine.model.CampaignTrackDef
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.ui.story.StoryAssetLoader
import com.voicerpg.engine.ui.theme.RetroBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Character creation for perspective games (manifest creationMode "preset_select"): the player picks one
 * of the campaign tracks' preset heroes, arcade "choose your fighter" style. Picking a hero sets the
 * player's name, title, class, portrait and campaign track together.
 *
 * Voice: say a hero's name, alias or one of the track's voiceKeywords to pick them; "start", "lock in",
 * "select", "begin", "play" or "ready" to confirm; "back" to leave.
 */
@Composable
fun PresetSelectScreen(
    initialCustomization: PlayerCustomization,
    speechManager: SpeechManager? = null,
    combatNarrator: CombatNarrator? = null,
    onBack: (() -> Unit)? = null,
    onConfirmCharacter: (PlayerCustomization) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manifest = GameContent.manifest
    val tracks = remember { manifest.campaignTracks.filter { it.hero != null } }
    val sfx = remember { SfxManager(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    val isAutoListen = speechManager?.isAutoListen?.collectAsState()?.value ?: false

    var selected by remember {
        mutableStateOf(tracks.firstOrNull { it.id == initialCustomization.campaignTrackId } ?: tracks.first())
    }

    fun pick(track: CampaignTrackDef, announce: Boolean = true) {
        selected = track
        val hero = track.hero ?: return
        sfx.playSoundBite(hero.soundBites)
        if (announce && hero.soundBites.isEmpty()) {
            combatNarrator?.speak("${hero.name.ifBlank { track.displayName }}. ${track.displayName}.", force = true)
        }
    }

    fun confirm() {
        speechManager?.cancel()
        val hero = selected.hero ?: return
        onConfirmCharacter(hero.toCustomization(selected, initialCustomization))
    }

    fun handleVoice(utterance: String) {
        val lower = utterance.lowercase().trim()
        if (lower.isBlank()) return
        val confirmWords = (manifest.creationVoiceKeywords + listOf("start", "lock in", "lock", "select", "begin", "play", "ready", "confirm")).distinct()
        val picked = tracks.firstOrNull { t ->
            val h = t.hero
            listOfNotNull(h?.name, h?.alias, t.displayName).any { it.isNotBlank() && lower.contains(it.lowercase()) } ||
                t.voiceKeywords.any { it.isNotBlank() && lower.contains(it.lowercase()) }
        }
        when {
            picked != null -> pick(picked)
            confirmWords.any { lower.contains(it) } -> { confirm(); return }
            lower == "back" || lower.contains("go back") || lower == "return" -> { speechManager?.cancel(); onBack?.invoke(); return }
        }
        if (speechManager != null && speechManager.isAutoListen.value) {
            coroutineScope.launch {
                delay(200)
                speechManager.startListening { next -> handleVoice(next) }
            }
        }
    }

    LaunchedEffect(isAutoListen) {
        if (isAutoListen) {
            delay(300)
            speechManager?.startListening { utterance -> handleVoice(utterance) }
        }
    }

    val pulse by rememberInfiniteTransition(label = "preset_pulse").animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "preset_pulse_alpha"
    )

    Column(
        modifier = modifier.fillMaxSize().background(RetroBlack).verticalScroll(rememberScrollState()).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = manifest.creationStatusSubtitle ?: "CHOOSE YOUR CHAMPION",
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (track in tracks) {
                PresetCard(
                    track = track,
                    isSelected = track.id == selected.id,
                    pulseAlpha = if (track.id == selected.id) pulse else 1f,
                    onClick = { pick(track) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val accent = parseHexColor(selected.hero?.accentColor ?: "#FFD700", Color(0xFFFFD700))
        Button(
            onClick = { confirm() },
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = RetroBlack),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "LOCK IN ${selected.hero?.name?.ifBlank { null }?.uppercase() ?: selected.displayName.uppercase()} ➔",
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black
            )
        }
        if (onBack != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "◀ BACK", color = Color.LightGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                modifier = Modifier.clickable { speechManager?.cancel(); onBack() }.padding(6.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "🎤 Say a name to choose, then \"Lock in\"",
            color = accent.copy(alpha = 0.9f), fontSize = 11.sp, fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PresetCard(
    track: CampaignTrackDef,
    isSelected: Boolean,
    pulseAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hero = track.hero ?: return
    val context = LocalContext.current
    val accent = parseHexColor(hero.accentColor, Color(0xFFFFD700))
    val secondary = parseHexColor(hero.secondaryColor, Color(0xFFB71C1C))
    val portrait = remember(hero.portraitAsset) { hero.portraitAsset?.let { StoryAssetLoader.loadRawAssetBitmap(context, it) } }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0E0E16))
            .border(if (isSelected) 3.dp else 1.dp, accent.copy(alpha = if (isSelected) pulseAlpha else 0.35f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(6.dp)).background(secondary.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            if (portrait != null) {
                Image(bitmap = portrait, contentDescription = hero.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(hero.name.take(1).uppercase(), color = accent, fontSize = 48.sp, fontWeight = FontWeight.Black)
            }
            if (track.badgeText.isNotBlank()) {
                Text(
                    text = track.badgeText, color = RetroBlack, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp).background(accent, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(hero.name.uppercase(), color = accent, fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        if (hero.alias.isNotBlank()) {
            Text(hero.alias, color = secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        }
        if (hero.title.isNotBlank()) {
            Text(hero.title, color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(4.dp))
        Text(track.displayName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        if (hero.slogan.isNotBlank()) {
            Text("“${hero.slogan}”", color = accent.copy(alpha = 0.85f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        }
        if (hero.playstyle.isNotBlank()) {
            Text(hero.playstyle, color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        }
        if (track.rosterDescription.isNotBlank()) {
            Text(track.rosterDescription, color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        }
        if (hero.featuredMoves.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            for (move in hero.featuredMoves.take(4)) {
                Text("▸ $move", color = Color(0xFFE0E0E0), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
        if (hero.soundBiteLabel.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("🔊 ${hero.soundBiteLabel}", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
