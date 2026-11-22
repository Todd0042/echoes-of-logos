package com.voicerpg.engine.ui.minigame

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.voicerpg.engine.engine.MiniGameSimulator
import com.voicerpg.engine.model.MiniGameConfig
import com.voicerpg.engine.model.MiniGameOutcome
import com.voicerpg.engine.model.MiniGameOutcomeTier
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Generic Interactive Mini-Game Container & Runner.
 * Provides hands-free / touch reflex timing gameplay, active score and quota tracking,
 * timer countdown, skip simulation, and outcome reporting for any game built on the engine.
 */
@Composable
fun MiniGameContainerScreen(
    config: MiniGameConfig,
    onFinish: (outcome: MiniGameOutcome) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var timeRemainingSeconds by remember { mutableIntStateOf(config.durationSeconds.coerceAtLeast(15)) }
    var currentScore by remember { mutableFloatStateOf(0f) }
    var currentQuotaDone by remember { mutableIntStateOf(0) }
    var feedbackText by remember { mutableStateOf("") }
    var feedbackColor by remember { mutableStateOf(Color.White) }
    var finalOutcome by remember { mutableStateOf<MiniGameOutcome?>(null) }

    // Countdown Timer Loop
    LaunchedEffect(finalOutcome) {
        if (finalOutcome != null) return@LaunchedEffect
        while (timeRemainingSeconds > 0 && finalOutcome == null) {
            delay(1000)
            timeRemainingSeconds--
            if (timeRemainingSeconds <= 0 && finalOutcome == null) {
                finalOutcome = MiniGameSimulator.outcomeFromScore(config, currentScore)
            }
        }
    }

    val minutes = timeRemainingSeconds / 60
    val seconds = timeRemainingSeconds % 60
    val timeFormatted = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)

    // Needle animation for timing challenge (0.0f to 1.0f and back)
    val cycleDurationMs = (1600 / config.targetSpeed.coerceAtLeast(0.5f)).toInt().coerceIn(600, 3000)
    val infiniteTransition = rememberInfiniteTransition(label = "needle_transition")
    val needleProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cycleDurationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "needle_pos"
    )

    fun onAttemptAction() {
        if (finalOutcome != null) return
        val distFromCenter = abs(needleProgress - 0.5f)
        val scoreDelta: Float
        if (distFromCenter < 0.10f) {
            // Perfect sweet spot
            scoreDelta = 15f
            currentQuotaDone = (currentQuotaDone + 1).coerceAtMost(config.quota)
            feedbackText = "PERFECT! +15"
            feedbackColor = Color(0xFF69F0AE)
        } else if (distFromCenter < 0.22f) {
            // Good hit
            scoreDelta = 8f
            currentQuotaDone = (currentQuotaDone + 1).coerceAtMost(config.quota)
            feedbackText = "GOOD! +8"
            feedbackColor = Color(0xFFFFD54F)
        } else {
            // Miss
            scoreDelta = -config.errorPenalty.coerceAtLeast(4f)
            feedbackText = "OFF TARGET!"
            feedbackColor = Color(0xFFFF5252)
        }
        currentScore = (currentScore + scoreDelta).coerceIn(0f, 100f)

        if (currentQuotaDone >= config.quota && finalOutcome == null) {
            val bonus = 10f
            currentScore = (currentScore + bonus).coerceIn(0f, 100f)
            finalOutcome = MiniGameSimulator.outcomeFromScore(config, currentScore)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = config.title.uppercase(),
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (config.department.isNotBlank()) {
                            Text(
                                text = config.department,
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Timer Indicator
                        Surface(
                            color = if (timeRemainingSeconds <= 10) Color(0xFFB71C1C) else Color(0xFF0F172A),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "⏱️ $timeFormatted",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Skip / Auto-Play Button
                        Button(
                            onClick = {
                                if (finalOutcome == null) {
                                    finalOutcome = MiniGameSimulator.simulate(config)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF374151),
                                contentColor = Color(0xFFFFD54F)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⏩ SKIP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Stats Sub-header Row (Quota and Score)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QUOTA PROGRESS: $currentQuotaDone / ${config.quota}",
                    color = Color(0xFF81D4FA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "SCORE: ${currentScore.toInt()} / 100",
                    color = Color(0xFFA5D6A7),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Interactive Reflex Challenge Play Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (config.description.isNotBlank()) {
                        Text(
                            text = config.description,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                    }

                    // Reflex timing gauge bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                            .border(2.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // Draw Target Sweet Spot Zone (center 20%)
                            val targetWidth = w * 0.20f
                            val targetLeft = (w - targetWidth) / 2f
                            drawRoundRect(
                                color = Color(0xFF2E7D32).copy(alpha = 0.45f),
                                topLeft = Offset(targetLeft, 0f),
                                size = Size(targetWidth, h),
                                cornerRadius = CornerRadius(8f, 8f)
                            )
                            // Draw inner bullseye (center 8%)
                            val bullseyeWidth = w * 0.08f
                            val bullseyeLeft = (w - bullseyeWidth) / 2f
                            drawRoundRect(
                                color = Color(0xFF4CAF50).copy(alpha = 0.70f),
                                topLeft = Offset(bullseyeLeft, 0f),
                                size = Size(bullseyeWidth, h),
                                cornerRadius = CornerRadius(6f, 6f)
                            )

                            // Draw moving needle / orb
                            val needleX = w * needleProgress
                            drawCircle(
                                color = Color(0xFFFFD54F),
                                radius = 14f,
                                center = Offset(needleX, h / 2f)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 6f,
                                center = Offset(needleX, h / 2f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Feedback Pill
                    Text(
                        text = feedbackText.ifBlank { "TIME YOUR TAP IN THE GREEN ZONE" },
                        color = if (feedbackText.isBlank()) Color.Gray else feedbackColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Big Action Button
                    Button(
                        onClick = { onAttemptAction() },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(60.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(2.dp, Color(0xFFFFD54F), RoundedCornerShape(14.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE65100),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⚡ STRIKE (TAP HERE)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }

        // Outcome Summary Modal Dialog
        finalOutcome?.let { outcome ->
            Dialog(onDismissRequest = { /* Must click continue */ }) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val tierColor = when (outcome.tier) {
                            MiniGameOutcomeTier.FLAWLESS -> Color(0xFF69F0AE)
                            MiniGameOutcomeTier.SOLID -> Color(0xFF81D4FA)
                            MiniGameOutcomeTier.CLUMSY -> Color(0xFFFFB74D)
                            MiniGameOutcomeTier.DISASTER -> Color(0xFFEF5350)
                        }

                        Text(
                            text = outcome.tier.name.uppercase(),
                            color = tierColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "FINAL SCORE: ${outcome.score.toInt()}/100",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (outcome.narrativeBlurb.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = outcome.narrativeBlurb,
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (outcome.metricDeltas.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            outcome.metricDeltas.forEach { (metricId, delta) ->
                                val sign = if (delta >= 0f) "+" else ""
                                Text(
                                    text = "$metricId: $sign${delta.toInt()}%",
                                    color = if (delta >= 0f) Color(0xFF81C784) else Color(0xFFE57373),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { onFinish(outcome) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "CONTINUE ➔",
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
