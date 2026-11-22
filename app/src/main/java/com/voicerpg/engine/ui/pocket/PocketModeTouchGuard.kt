package com.voicerpg.engine.ui.pocket

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.audio.SpeechState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight representation of a party member's vital stats for the AMOLED Pocket Lock screen.
 */
data class PocketHeroVitals(
    val name: String,
    val loreClass: String,
    val currentHp: Int,
    val maxHp: Int,
    val currentMp: Int,
    val maxMp: Int
)

/**
 * True-black AMOLED full-screen touch guard overlay for Pocket Mode.
 *
 * Prevents accidental pocket touches, conserves battery by rendering on pure #000000 black,
 * displays glanceable vital stats and audio recognition waveforms, and supports double-tap,
 * swipe-up, and voice ("Unlock") gestures to dismiss.
 */
@Composable
fun PocketModeTouchGuard(
    party: List<PocketHeroVitals>,
    locationTitle: String,
    speechState: SpeechState,
    rmsLevel: Float,
    isTtsSpeaking: Boolean,
    onUnlock: () -> Unit,
    onExitPocketMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTimeString by remember {
        mutableStateOf(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()))
    }
    var showTapHint by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // Update time periodically
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000)
            currentTimeString = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        }
    }

    // Auto-dismiss single tap hint after 2 seconds
    LaunchedEffect(showTapHint) {
        if (showTapHint) {
            delay(2000)
            showTapHint = false
        }
    }

    // Smooth audio visualizer amplitude
    val smoothRms by animateFloatAsState(
        targetValue = rmsLevel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 80),
        label = "smoothRms"
    )

    // Pulsing animation for mic / speaking states
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black) // True AMOLED pure black
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onUnlock()
                    },
                    onTap = {
                        showTapHint = true
                    }
                )
            }
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // =================================================================
            // Top Bar: Clock, Mode Status & OLED Badge
            // =================================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentTimeString,
                        color = Color(0xFF888888),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🎧 POCKET MODE",
                            color = Color(0xFFFFD54F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF212121))
                                .border(1.dp, Color(0xFF424242), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🔒 LOCKED",
                                color = Color(0xFFB0BEC5),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Location / Encounter info
                Text(
                    text = locationTitle,
                    color = Color(0xFFE0E0E0),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Display Dimmed • Mic Active • Anti-Pocket Tap Guard",
                    color = Color(0xFF616161),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }

            // =================================================================
            // Center Section: Fellowship Vitals & Audio Visualizer
            // =================================================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Fellowship Vitals
                if (party.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF101010))
                            .border(1.dp, Color(0xFF262626), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        party.forEach { hero ->
                            val hpRatio = if (hero.maxHp > 0) hero.currentHp.toFloat() / hero.maxHp else 0f
                            val mpRatio = if (hero.maxMp > 0) hero.currentMp.toFloat() / hero.maxMp else 0f

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = hero.name,
                                        color = Color(0xFFFFD54F),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "HP ${hero.currentHp}/${hero.maxHp}  MP ${hero.currentMp}/${hero.maxMp}",
                                        color = Color(0xFF9E9E9E),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { hpRatio.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = Color(0xFF2E7D32),
                                        trackColor = Color(0xFF1B331B),
                                        strokeCap = StrokeCap.Round
                                    )
                                    LinearProgressIndicator(
                                        progress = { mpRatio.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = Color(0xFF1565C0),
                                        trackColor = Color(0xFF0D2545),
                                        strokeCap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Audio / Speech Visualizer Ring
                Box(
                    modifier = Modifier
                        .size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing animated canvas ring
                    val isListening = speechState is SpeechState.Listening
                    val ringColor = when {
                        isTtsSpeaking -> Color(0xFFFFB300)
                        isListening -> Color(0xFF69F0AE)
                        speechState is SpeechState.Processing -> Color(0xFF80D8FF)
                        else -> Color(0xFF424242)
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val baseRadius = size.minDimension / 2.6f
                        val reactiveRadius = baseRadius + (smoothRms * 18f)

                        // Outer glowing pulse ring
                        if (isListening || isTtsSpeaking) {
                            drawCircle(
                                color = ringColor.copy(alpha = 0.25f),
                                radius = reactiveRadius * pulseScale,
                                center = center,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        // Inner solid ring
                        drawCircle(
                            color = ringColor.copy(alpha = 0.7f),
                            radius = reactiveRadius,
                            center = center,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    // Center Icon / Glyph
                    Text(
                        text = when {
                            isTtsSpeaking -> "🔊"
                            speechState is SpeechState.Listening -> "🎤"
                            speechState is SpeechState.Processing -> "⚡"
                            else -> "🎙️"
                        },
                        fontSize = 32.sp
                    )
                }

                // Audio status text
                val statusText = when {
                    isTtsSpeaking -> "🔊 Narrator Speaking..."
                    speechState is SpeechState.Listening -> "🎤 Listening for command or incantation..."
                    speechState is SpeechState.Processing -> "⚡ Evaluating speech..."
                    speechState is SpeechState.Standby -> "🎙️ Microphone on Standby"
                    speechState is SpeechState.Error -> "⚠️ Speech Standby"
                    else -> "🎙️ Hands-free Auto-Listen Active"
                }

                Text(
                    text = statusText,
                    color = when {
                        isTtsSpeaking -> Color(0xFFFFB300)
                        speechState is SpeechState.Listening -> Color(0xFF69F0AE)
                        speechState is SpeechState.Processing -> Color(0xFF80D8FF)
                        else -> Color(0xFF757575)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                // Voice commands quick hint
                Text(
                    text = "Commands: \"Status\" • \"Enemies\" • \"Party\" • \"Unlock\"",
                    color = Color(0xFF555555),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }

            // =================================================================
            // Bottom Section: Swipe Up & Unlock Handle
            // =================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffsetY < -60f) {
                                    onUnlock()
                                }
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                dragOffsetY = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount
                                if (dragOffsetY < -100f) {
                                    onUnlock()
                                    dragOffsetY = 0f
                                }
                            }
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Animated single-tap feedback hint
                AnimatedVisibility(
                    visible = showTapHint,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF212121))
                            .border(1.dp, Color(0xFFFFD54F), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🔒 Screen locked. Double-tap or swipe up to unlock.",
                            color = Color(0xFFFFD54F),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Text(
                    text = "▲ SWIPE UP OR DOUBLE-TAP TO UNLOCK ▲",
                    color = Color(0xFF757575),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                // Interactive Unlock Button Bar
                Button(
                    onClick = onUnlock,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C1C1C),
                        contentColor = Color(0xFFFFE082)
                    ),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "🔓", fontSize = 14.sp)
                        Text(
                            text = "DOUBLE-TAP OR TAP TO UNLOCK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Subtle Exit Pocket Mode link
                Text(
                    text = "Exit Pocket Mode",
                    color = Color(0xFF555555),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onExitPocketMode() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Floating top banner shown when Pocket Mode is active but the touch guard screen is temporarily unlocked.
 * Allows instant re-locking or turning off pocket mode.
 */
@Composable
fun PocketModeUnlockedBanner(
    onLock: () -> Unit,
    onExitPocketMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xE610141C),
        border = BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.6f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🎧", fontSize = 16.sp)
                Column {
                    Text(
                        text = "POCKET MODE (UNLOCKED)",
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Say \"Lock\" before returning to pocket",
                        color = Color(0xFFB0BEC5),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onLock,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4AF37).copy(alpha = 0.25f),
                        contentColor = Color(0xFFFFE082)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "🔒 LOCK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = onExitPocketMode,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text(
                        text = "✖",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
