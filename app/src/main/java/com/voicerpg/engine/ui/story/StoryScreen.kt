package com.voicerpg.engine.ui.story

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.content.res.Configuration
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.DialogueChoice
import com.voicerpg.engine.model.DialogueNode
import com.voicerpg.engine.model.DialogueSpeaker
import com.voicerpg.engine.model.SpeakerSide
import com.voicerpg.engine.engine.SaveManager
import com.voicerpg.engine.ui.combat.OptionsDialog
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.LogosGlow
import com.voicerpg.engine.ui.theme.RetroBlack
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroBorderGold
import com.voicerpg.engine.ui.theme.RetroPanel
import com.voicerpg.engine.viewmodel.CombatViewModel
import com.voicerpg.engine.viewmodel.StoryViewModel
import kotlin.math.sin

@Composable
fun StoryScreen(
    storyViewModel: StoryViewModel,
    combatViewModel: CombatViewModel,
    onOpenOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storyState by storyViewModel.state.collectAsState()
    val combatState by combatViewModel.state.collectAsState()
    val speechState by storyViewModel.speechManager.speechState.collectAsState()
    val isEyesFreeMode by combatViewModel.combatNarrator.isEyesFreeMode.collectAsState()
    val isAutoListen by storyViewModel.speechManager.isAutoListen.collectAsState()
    val isChimeMuted by storyViewModel.speechManager.isChimeMuted.collectAsState()

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Load orientation-aware background: portrait variant (_p.jpg) when in portrait mode,
    // falling back to the landscape image when no portrait variant asset is present.
    val backgroundBitmap = remember(storyState.currentScene.backgroundAsset, isLandscape) {
        StoryAssetLoader.loadBackground(
            context,
            storyState.currentScene.backgroundAsset,
            isPortrait = !isLandscape
        )
    }
    val manifest = GameContent.manifest
    val defaultHeroName = manifest.defaultHeroName.ifBlank { "Hero" }
    val defaultHeroTitle = manifest.defaultHeroTitle.ifBlank { "Adventurer" }
    val effectiveHeroName = storyState.player.name.ifBlank { defaultHeroName }
    val effectiveHeroTitle = storyState.player.title.ifBlank { defaultHeroTitle }

    val heroSpeaker = remember(effectiveHeroName, effectiveHeroTitle) {
        val base = GameContent.heroCharacter?.speaker ?: DialogueSpeaker(
            id = "hero",
            name = defaultHeroName,
            title = defaultHeroTitle,
            portraitAsset = manifest.defaultHeroPortrait ?: "portraits/template_hero.jpg",
            themeColor = Color(0xFF64B5F6)
        )
        base.copy(name = effectiveHeroName, title = effectiveHeroTitle)
    }
    val heroBitmap = remember(heroSpeaker.portraitAsset, storyState.currentSlot) {
        heroSpeaker.portraitAsset?.let { StoryAssetLoader.loadBitmap(context, it, storyState.currentSlot) }
    }

    val currentNode = storyState.currentNode
    val isNarratorSpeaking = currentNode.speaker.isNarrator ||
        currentNode.speaker.id == "narrator" ||
        currentNode.side == SpeakerSide.CENTER_NARRATOR

    val narratorAsset = remember(currentNode.id) {
        GameContent.narrator.effectivePortraitAsset() ?: DialogueSpeaker.NARRATOR.effectivePortraitAsset()
    }
    val narratorBitmap = remember(narratorAsset) {
        narratorAsset?.let { StoryAssetLoader.loadBitmap(context, it) }
    }

    val isHeroSpeaking = !isNarratorSpeaking && (
        currentNode.speaker.id == "hero" ||
        currentNode.speaker.id == "player" ||
        currentNode.speaker == heroSpeaker ||
        currentNode.speaker.name.equals(heroSpeaker.name, ignoreCase = true) ||
        (currentNode.speaker.portraitAsset != null && currentNode.speaker.portraitAsset == heroSpeaker.portraitAsset) ||
        currentNode.speaker.name.equals(defaultHeroName, ignoreCase = true) ||
        currentNode.speaker.name.equals(storyState.player.name, ignoreCase = true)
    )

    val leftSpeaker = when {
        isHeroSpeaking -> currentNode.speaker
        currentNode.side == SpeakerSide.LEFT -> currentNode.speaker
        else -> heroSpeaker
    }
    val leftSpeakerAsset = remember(leftSpeaker.id, currentNode.id) {
        leftSpeaker.effectivePortraitAsset(currentNode.id)
    }
    val leftBitmap = remember(leftSpeakerAsset, heroBitmap, narratorBitmap, storyState.currentSlot) {
        leftSpeakerAsset?.let { StoryAssetLoader.loadBitmap(context, it, storyState.currentSlot) }
    } ?: (heroBitmap ?: narratorBitmap)
    val isLeftSpeaking = isHeroSpeaking || (currentNode.side == SpeakerSide.LEFT && !isNarratorSpeaking)

    val rightSpeaker: DialogueSpeaker? = remember(currentNode.id, currentNode.speaker.id, isHeroSpeaking, isNarratorSpeaking) {
        when {
            isHeroSpeaking || (currentNode.side == SpeakerSide.LEFT && !isNarratorSpeaking) -> {
                null
            }
            isNarratorSpeaking -> {
                GameContent.narrator
            }
            else -> {
                currentNode.speaker
            }
        }
    }

    val rightSpeakerAsset = remember(rightSpeaker?.id, currentNode.id) {
        rightSpeaker?.effectivePortraitAsset(currentNode.id)
    }
    val rightBitmap = remember(rightSpeakerAsset, narratorBitmap, storyState.currentSlot) {
        rightSpeakerAsset?.let { StoryAssetLoader.loadBitmap(context, it, storyState.currentSlot) } ?: if (isNarratorSpeaking) narratorBitmap else null
    }

    val isRightSpeaking = isNarratorSpeaking || (rightSpeaker != null && (currentNode.speaker == rightSpeaker || currentNode.side == SpeakerSide.RIGHT))

    // Gentle breathing & bobbing animation for speaking busts
    val infiniteTransition = rememberInfiniteTransition(label = "BustBobbing")
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "BobSine"
    )
    val floatY = (sin(bobOffset) * 3f)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RetroBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(
                    if (storyState.isPureStoryMode) {
                        Modifier
                    } else if (storyState.isFastForwarding) {
                        Modifier.clickable { storyViewModel.stopFastForward() }
                    } else if (speechState is com.voicerpg.engine.audio.SpeechState.Standby) {
                        Modifier.clickable { storyViewModel.resumeVoiceListening() }
                    } else Modifier
                )
        ) {
            // 1. Scene Background — orientation-aware scaling strategy:
            //    • Landscape + 16:9 source  → ContentScale.Crop  (fills frame, minimal crop)
            //    • Portrait  + 9:16 _p.jpg  → ContentScale.FillBounds (fills frame perfectly)
            //    • Portrait  + 16:9 fallback → ContentScale.FillWidth  (pins width, avoids
            //      severe horizontal crop; lower gradient vignette masks the letterbox naturally)
            if (backgroundBitmap != null) {
                val bgScale = when {
                    isLandscape -> ContentScale.Crop
                    // If the bitmap is wider than tall it came from the landscape fallback;
                    // pin width so the full horizontal composition stays visible.
                    backgroundBitmap.width > backgroundBitmap.height -> ContentScale.FillWidth
                    // Native portrait asset: fill the frame perfectly.
                    else -> ContentScale.FillBounds
                }
                Image(
                    bitmap = backgroundBitmap,
                    contentDescription = storyState.currentScene.name,
                    contentScale = bgScale,
                    alignment = if (!isLandscape && backgroundBitmap.width > backgroundBitmap.height) {
                        // Align landscape-fallback image to the top so the most meaningful
                        // part of the scene (storefront, faces) stays in the upper 2/3.
                        Alignment.TopCenter
                    } else {
                        Alignment.Center
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF14141E))
                )
            }

            // Atmospheric dark vignette
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC08080C),
                                Color(0x2208080C),
                                Color(0x2208080C),
                                Color(0xE608080C)
                            )
                        )
                    )
            )

            // 2. Top Header: Chapter & Scene Location Banner on Left + Stacked Controls on Right
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = if (isLandscape) 4.dp else 8.dp)
            ) {
                if (storyState.isPureStoryMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF311B92).copy(alpha = 0.95f))
                            .border(1.dp, Color(0xFFBA68C8), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (storyState.isStoryAutoPlayPaused) "⏸ PAUSED" else "▶ STORY MODE",
                                color = Color(0xFFF3E5F5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF00695C))
                                        .clickable {
                                            combatViewModel.combatNarrator.setEyesFreeMode(true, lockGuard = true)
                                            if (!combatViewModel.combatNarrator.isSpeaking.value) {
                                                combatViewModel.combatNarrator.speak("Screen locked. Pocket mode active.", force = true)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "🔒 POCKET",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (storyState.isStoryAutoPlayPaused) Color(0xFF2E7D32) else Color(0xFFE65100))
                                        .clickable { storyViewModel.toggleStoryAutoPlayPause() }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = if (storyState.isStoryAutoPlayPaused) "RESUME" else "PAUSE",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFC62828))
                                        .clickable {
                                            if (storyState.currentSlot == com.voicerpg.engine.engine.SaveManager.STORY_MODE_SLOT) {
                                                storyViewModel.returnToTitle()
                                            } else {
                                                storyViewModel.setPureStoryMode(false)
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = if (storyState.currentSlot == com.voicerpg.engine.engine.SaveManager.STORY_MODE_SLOT) "EXIT" else "STOP",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                // Title Banner Card (with set flex width and clean multi-line wrapping)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = if (isLandscape) 44.dp else 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RetroPanel.copy(alpha = 0.92f))
                        .border(1.dp, RetroBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = if (isLandscape) 4.dp else 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = storyState.currentScene.chapterTitle.uppercase(),
                            color = LogosGold,
                            fontSize = if (isLandscape) 10.sp else 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            softWrap = true,
                            maxLines = if (isLandscape) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )
                        Text(
                            text = "📍 ${storyState.currentScene.name}",
                            color = Color.LightGray,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (GameContent.metrics.isNotEmpty() && storyState.dynamicMetrics.isNotEmpty()) {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                for (metric in GameContent.metrics.values) {
                                    val currVal = storyState.dynamicMetrics[metric.id] ?: metric.initialValue
                                    val chipColor = try {
                                        Color(android.graphics.Color.parseColor(metric.displayColor))
                                    } catch (_: Exception) {
                                        Color(0xFF4CAF50)
                                    }
                                    Text(
                                        text = "${metric.label}: ${currVal.toInt()}%",
                                        color = chipColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        softWrap = false,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Stacked Action Buttons (Outside the banner: 2x2 grid for Log, Options, Skip, Battle)
                Column(
                    modifier = Modifier.width(if (isLandscape) 144.dp else 134.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    val btnHeight = if (isLandscape) 24.dp else 28.dp

                    // Row 1: Backlog + Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Log / History Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(btnHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .background(RetroPanel.copy(alpha = 0.92f))
                                .border(1.dp, RetroBorderGold, RoundedCornerShape(6.dp))
                                .clickable { storyViewModel.openBacklog() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📜 LOG",
                                color = LogosGold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }

                        // Options / Pocket Mode Toggle Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(btnHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isEyesFreeMode) Color(0xFF1565C0) else RetroPanel.copy(alpha = 0.92f))
                                .border(1.dp, if (isEyesFreeMode) Color(0xFF64B5F6) else RetroBorder, RoundedCornerShape(6.dp))
                                .clickable { onOpenOptions() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEyesFreeMode) "🎧 POCKET" else "⚙️ OPT",
                                color = if (isEyesFreeMode) Color(0xFFE3F2FD) else LogosGold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }

                    // Row 2: Auto-Play Toggle + Skip/Fast-Forward
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Auto-Play Toggle Button (Available for all slots)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(btnHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (storyState.isPureStoryMode) Color(0xFF4A148C) else RetroPanel.copy(alpha = 0.92f))
                                .border(1.dp, if (storyState.isPureStoryMode) Color(0xFFCE93D8) else RetroBorder, RoundedCornerShape(6.dp))
                                .clickable { storyViewModel.toggleAutoPlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (storyState.isPureStoryMode) "🤖 AUTO: ON" else "🤖 AUTO",
                                color = if (storyState.isPureStoryMode) Color(0xFFF3E5F5) else LogosGold,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }

                        // Skip / Fast-Forward Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(btnHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (storyState.isFastForwarding) Color(0xFFD32F2F) else RetroPanel.copy(alpha = 0.92f))
                                .border(1.dp, if (storyState.isFastForwarding) Color(0xFFFF8A80) else RetroBorder, RoundedCornerShape(6.dp))
                                .clickable { storyViewModel.toggleFastForward() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (storyState.isFastForwarding) "⏹️ STOP" else "⏩ SKIP",
                                color = if (storyState.isFastForwarding) Color.White else LogosGold,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

            // 3. Characters & Dialogue Presentation (Responsive Portrait vs Landscape)
            if (!isLandscape) {
                // ==================== PORTRAIT ORIENTATION ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 80.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Character Portraits Row (Resting cleanly right on top of the speech bubble)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Left Character (Hero or Narrator)
                        CharacterPortraitBust(
                            bitmap = leftBitmap,
                            speaker = leftSpeaker,
                            isSpeaking = isLeftSpeaking,
                            sizeDp = 84.dp,
                            modifier = Modifier.offset(y = (if (isLeftSpeaking) floatY else 0f).dp)
                        )

                        // Right Character (Companion / NPC)
                        if (rightSpeaker != null) {
                            CharacterPortraitBust(
                                bitmap = rightBitmap,
                                speaker = rightSpeaker,
                                isSpeaking = isRightSpeaking,
                                sizeDp = 84.dp,
                                modifier = Modifier.offset(y = (if (isRightSpeaking) floatY else 0f).dp)
                            )
                        } else {
                            // Empty spacer to keep left character nicely aligned
                            Spacer(modifier = Modifier.width(84.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Retro Speech Bubble
                    RetroSpeechBubble(
                        node = currentNode,
                        onTapToAdvance = {
                            if (currentNode.choices.isEmpty()) {
                                if (currentNode.id == "epilogue_credits" || currentNode.setFlagOnEnter == "game_completed") {
                                    if (storyState.isPureStoryMode) {
                                        storyViewModel.startPureStoryMode(fresh = true)
                                    } else {
                                        storyViewModel.startNewGameFlow(storyState.currentSlot)
                                    }
                                } else {
                                    storyViewModel.advanceDialogue()
                                }
                            }
                        },
                        isEyesFreeMode = isEyesFreeMode,
                        speakerBitmapOverride = if (isNarratorSpeaking) narratorBitmap else null,
                        slot = storyState.currentSlot,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(Alignment.Top)
                    )

                    // Choice Buttons (if node has branching choices)
                    if (currentNode.choices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val choicesScrollState = rememberScrollState()
                        LaunchedEffect(currentNode.id) {
                            choicesScrollState.scrollTo(0)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(choicesScrollState),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                currentNode.choices.forEachIndexed { index, choice ->
                                    val isCompleted = choice.completionFlag != null && storyState.narrativeFlags[choice.completionFlag] == true
                                    val isLocked = storyViewModel.isChoiceLocked(choice)
                                    DialogueChoiceItem(
                                        index = index + 1,
                                        choice = choice,
                                        isCompleted = isCompleted,
                                        isLocked = isLocked,
                                        onSelect = { storyViewModel.selectChoice(choice) }
                                    )
                                }
                            }
                        }

                        if (choicesScrollState.canScrollForward) {
                            Text(
                                text = "▼ SCROLL FOR MORE OPTIONS ▼",
                                color = LogosGold.copy(alpha = 0.85f),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    // Battle Trigger Prompt if node triggers combat
                    if (currentNode.triggerBattleEncounterId != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { storyViewModel.advanceDialogue() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD32F2F),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚔️ COMMENCE BATTLE (TAP OR SAY 'FIREBALL')",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Mini-Game Trigger Prompt if node triggers a mini-game
                    if (currentNode.triggerMiniGameId != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { storyViewModel.advanceDialogue() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00897B),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🎮 START MINI-GAME (TAP OR SAY 'START')",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Ambient Audio & Voice Mic Prompt Indicator Bar
                    Spacer(modifier = Modifier.height(4.dp))
                    VoiceInputPromptBar(
                        hasChoices = currentNode.choices.isNotEmpty(),
                        speechState = speechState,
                        onStartListening = { storyViewModel.resumeVoiceListening() }
                    )
                }
            } else {
                // ==================== LANDSCAPE ORIENTATION (Full Widescreen JRPG Layout) ====================
                // Left Flank: Protagonist or Narrator Bust standing proudly on the left
                CharacterPortraitBust(
                    bitmap = leftBitmap,
                    speaker = leftSpeaker,
                    isSpeaking = isLeftSpeaking,
                    sizeDp = 110.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 8.dp)
                        .offset(y = (if (isLeftSpeaking) floatY else 0f).dp)
                )

                // Right Flank: Companion / NPC Bust standing on the right
                if (rightSpeaker != null) {
                    CharacterPortraitBust(
                        bitmap = rightBitmap,
                        speaker = rightSpeaker,
                        isSpeaking = isRightSpeaking,
                        sizeDp = 110.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 8.dp)
                            .offset(y = (if (isRightSpeaking) floatY else 0f).dp)
                    )
                }

                // Center Stage: Dialogue Bubble & Choices positioned neatly between characters
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 130.dp, end = if (rightSpeaker != null) 130.dp else 16.dp, top = 48.dp, bottom = 6.dp)
                        .widthIn(max = 600.dp)
                        .align(Alignment.BottomCenter),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Retro Speech Bubble
                    RetroSpeechBubble(
                        node = currentNode,
                        onTapToAdvance = {
                            if (currentNode.choices.isEmpty()) {
                                if (currentNode.id == "epilogue_credits" || currentNode.setFlagOnEnter == "game_completed") {
                                    if (storyState.isPureStoryMode) {
                                        storyViewModel.startPureStoryMode(fresh = true)
                                    } else {
                                        storyViewModel.startNewGameFlow(storyState.currentSlot)
                                    }
                                } else {
                                    storyViewModel.advanceDialogue()
                                }
                            }
                        },
                        isEyesFreeMode = isEyesFreeMode,
                        speakerBitmapOverride = if (isNarratorSpeaking) narratorBitmap else null,
                        slot = storyState.currentSlot,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(Alignment.Top)
                    )

                    // Choice Buttons in Landscape: Side-by-side pairs in a vertically scrollable container
                    if (currentNode.choices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val landscapeChoicesScrollState = rememberScrollState()
                        LaunchedEffect(currentNode.id) {
                            landscapeChoicesScrollState.scrollTo(0)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(landscapeChoicesScrollState),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                currentNode.choices.chunked(2).forEachIndexed { rowIndex, rowChoices ->
                                    if (rowChoices.size == 2) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowChoices.forEachIndexed { colIndex, choice ->
                                                val originalIndex = rowIndex * 2 + colIndex
                                                val isCompleted = choice.completionFlag != null && storyState.narrativeFlags[choice.completionFlag] == true
                                                val isLocked = storyViewModel.isChoiceLocked(choice)
                                                DialogueChoiceItem(
                                                    index = originalIndex + 1,
                                                    choice = choice,
                                                    isCompleted = isCompleted,
                                                    isLocked = isLocked,
                                                    onSelect = { storyViewModel.selectChoice(choice) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    } else {
                                        val choice = rowChoices.first()
                                        val originalIndex = rowIndex * 2
                                        val isCompleted = choice.completionFlag != null && storyState.narrativeFlags[choice.completionFlag] == true
                                        val isLocked = storyViewModel.isChoiceLocked(choice)
                                        DialogueChoiceItem(
                                            index = originalIndex + 1,
                                            choice = choice,
                                            isCompleted = isCompleted,
                                            isLocked = isLocked,
                                            onSelect = { storyViewModel.selectChoice(choice) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        if (landscapeChoicesScrollState.canScrollForward) {
                            Text(
                                text = "▼ SCROLL FOR MORE OPTIONS ▼",
                                color = LogosGold.copy(alpha = 0.85f),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }

                    // Battle Trigger Prompt if node triggers combat
                    if (currentNode.triggerBattleEncounterId != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { storyViewModel.advanceDialogue() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD32F2F),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚔️ COMMENCE BATTLE (TAP OR SAY 'FIREBALL')",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Mini-Game Trigger Prompt if node triggers a mini-game
                    if (currentNode.triggerMiniGameId != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { storyViewModel.advanceDialogue() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00897B),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🎮 START MINI-GAME (TAP OR SAY 'START')",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Ambient Audio & Voice Mic Prompt Indicator Bar
                    Spacer(modifier = Modifier.height(2.dp))
                    VoiceInputPromptBar(
                        hasChoices = currentNode.choices.isNotEmpty(),
                        speechState = speechState,
                        onStartListening = { storyViewModel.resumeVoiceListening() }
                    )
                }
            }

            // 4. Dialogue Backlog & Quest Recap Modal
            val questRecap = remember(
                storyState.currentScene.id,
                storyState.currentNode.id,
                storyState.narrativeFlags,
                storyState.partyStats
            ) {
                storyViewModel.getQuestRecap()
            }

            DialogueBacklogDialog(
                isOpen = storyState.isBacklogOpen,
                history = storyState.dialogueHistory,
                questRecap = questRecap,
                isRecapActive = storyState.isBacklogRecapActive,
                onTabSelect = { isRecap -> storyViewModel.setBacklogRecapTab(isRecap) },
                onClose = { storyViewModel.closeBacklog() },
                onReplay = { entry -> storyViewModel.replayDialogueEntry(entry) },
                onListenRecap = { spoken ->
                    combatViewModel.combatNarrator.stop()
                    combatViewModel.combatNarrator.speak(spoken, force = true)
                }
            )
        }
    }
}

/**
 * Character Bust Portrait on Left or Right of the screen with speaking bounce & dimming.
 */
@Composable
private fun CharacterPortraitBust(
    bitmap: ImageBitmap?,
    speaker: DialogueSpeaker,
    isSpeaking: Boolean,
    sizeDp: Dp = 100.dp,
    modifier: Modifier = Modifier
) {
    val scale = if (isSpeaking) 1.04f else 0.96f
    val alpha = if (isSpeaking) 1.0f else 0.70f
    val borderColor = if (isSpeaking) speaker.themeColor else RetroBorder

    Column(
        modifier = modifier
            .scale(scale)
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp)
                .shadow(if (isSpeaking) 12.dp else 4.dp, RoundedCornerShape(12.dp), spotColor = speaker.themeColor)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0F1A))
                .border(if (isSpeaking) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = speaker.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(speaker.themeColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = speaker.name.first().toString(),
                        color = speaker.themeColor,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Name Tag
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSpeaking) speaker.themeColor.copy(alpha = 0.9f) else Color(0xCC181824))
                .border(1.dp, if (isSpeaking) LogosGold else RetroBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = speaker.name,
                color = if (isSpeaking) RetroBlack else Color.LightGray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * 16-Bit Retro Speech Bubble anchored dynamically towards the speaking character.
 */
@Composable
private fun RetroSpeechBubble(
    node: DialogueNode,
    onTapToAdvance: () -> Unit,
    isEyesFreeMode: Boolean = false,
    speakerBitmapOverride: ImageBitmap? = null,
    slot: Int = SaveManager.DEFAULT_SLOT,
    modifier: Modifier = Modifier
) {
    val speakerColor = node.speaker.themeColor
    val context = LocalContext.current
    val speakerAsset = remember(node.id) { node.speaker.effectivePortraitAsset() }
    val speakerBitmap = remember(speakerAsset, speakerBitmapOverride, slot) {
        speakerBitmapOverride ?: speakerAsset?.let { StoryAssetLoader.loadBitmap(context, it, slot) }
    }

    // Subtle breathing pulse for advance arrow
    val infiniteTransition = rememberInfiniteTransition(label = "ArrowPulse")
    val arrowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ArrowAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF00D0D18))
            .border(2.dp, if (node.side == SpeakerSide.CENTER_NARRATOR) LogosGold else speakerColor.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .clickable { onTapToAdvance() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top),
            verticalArrangement = Arrangement.Top
        ) {
            // Speaker Name Badge Header with optional mini portrait circle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (speakerBitmap != null) {
                        Image(
                            bitmap = speakerBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(1.dp, speakerColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "💬 ${node.speaker.name.uppercase()}",
                        color = speakerColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (node.choices.isEmpty()) {
                    val promptText = when {
                        node.id == "epilogue_credits" -> "👑 PLAY AGAIN"
                        node.triggerBattleEncounterId != null -> "⚔️ TO BATTLE"
                        node.triggerMiniGameId != null -> "🎮 TO MINI-GAME"
                        isEyesFreeMode && node.nextNodeId != null -> "⏩ AUTO NEXT (1.5s)"
                        node.nextNodeId != null -> "▼ NEXT"
                        isEyesFreeMode -> "⏩ AUTO CAMP (1.5s)"
                        else -> "⭐ NEXT (CAMP)"
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = promptText,
                        color = LogosGold.copy(alpha = arrowAlpha),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }

            // Subtitle for title if present
            if (node.speaker.title.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "• ${node.speaker.title}",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Spoken Dialogue Content
            Text(
                text = node.text.trim(),
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

/**
 * Ambient Audio & Voice Mic Prompt Indicator Bar
 */
@Composable
private fun VoiceInputPromptBar(
    hasChoices: Boolean,
    speechState: com.voicerpg.engine.audio.SpeechState,
    onStartListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = speechState is com.voicerpg.engine.audio.SpeechState.Listening
    val isStandby = speechState is com.voicerpg.engine.audio.SpeechState.Standby

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                isStandby -> "⏸️ Standing by — tap screen to resume"
                !hasChoices -> "Tap or say 'Next' to continue"
                else -> "Tap choice or speak your decision"
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            color = if (isStandby) Color(0xFFFFD54F) else Color.Gray,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Hands-free Mic Quick Tap
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        isListening -> Color(0xFFEF5350)
                        isStandby -> Color(0xFF423810)
                        else -> RetroPanel
                    }
                )
                .border(1.dp, if (isStandby) Color(0xFFFFD54F) else LogosGold, RoundedCornerShape(16.dp))
                .clickable { onStartListening() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = when {
                    isListening -> "🎙️ LISTENING..."
                    isStandby -> "⏸️ STANDBY"
                    else -> "🎤 SPEAK"
                },
                color = if (isStandby) Color(0xFFFFD54F) else LogosGold,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Clickable and voice-triggerable dialogue choice button.
 */
@Composable
private fun DialogueChoiceItem(
    index: Int,
    choice: DialogueChoice,
    isCompleted: Boolean = false,
    isLocked: Boolean = false,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isCompleted -> Color(0xCC1A1C23)
        isLocked -> Color(0x6614161C)
        else -> RetroPanel.copy(alpha = 0.95f)
    }
    val borderColor = when {
        isCompleted -> Color(0xFF37474F)
        isLocked -> Color(0xFF263238)
        else -> RetroBorderGold
    }
    val textColor = when {
        isCompleted -> Color(0xFF78909C)
        isLocked -> Color(0xFF607D8B)
        else -> Color.White
    }
    val indexColor = when {
        isCompleted -> Color(0xFF546E7A)
        isLocked -> Color(0xFF455A64)
        else -> LogosGold
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .then(if (!isCompleted) Modifier.clickable { onSelect() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[$index]",
                    color = indexColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isLocked) "🔒 " + choice.text else choice.text,
                    color = textColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier.wrapContentWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x334CAF50))
                            .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "✓ DONE",
                            color = Color(0xFF81C784),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else if (isLocked) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33B0BEC5))
                            .border(1.dp, Color(0xFF546E7A), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🔒 LOCKED",
                            color = Color(0xFF90A4AE),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else {
                    // Spoken keyword cue
                    val keywordHint = choice.voiceKeywords.firstOrNull() ?: ""
                    if (keywordHint.isNotBlank()) {
                        Text(
                            text = "Say \"$keywordHint\"",
                            color = Color(0xFF80D8FF),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
