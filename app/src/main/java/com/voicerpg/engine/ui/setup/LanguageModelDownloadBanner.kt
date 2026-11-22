package com.voicerpg.engine.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.localization.ModelDownloadStatus
import com.voicerpg.engine.ui.theme.FrostCyan
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.RetroBlack

/**
 * Smart top banner displaying on-device neural language model download progress and readiness.
 * Automatically adapts height so all underlying screen content scales to fit without clipping.
 * Auto-dismisses 5 seconds after completion.
 */
@Composable
fun LanguageModelDownloadBanner(
    status: ModelDownloadStatus,
    progress: Float,
    languageName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (status == ModelDownloadStatus.IDLE) return

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaPulse"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when (status) {
            ModelDownloadStatus.DOWNLOADING -> Color(0xF00D111A)
            ModelDownloadStatus.COMPLETED -> Color(0xF00B2213)
            ModelDownloadStatus.FAILED -> Color(0xF0241A0B)
            ModelDownloadStatus.IDLE -> RetroBlack
        },
        label = "bgColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when (status) {
            ModelDownloadStatus.DOWNLOADING -> FrostCyan.copy(alpha = 0.7f)
            ModelDownloadStatus.COMPLETED -> Color(0xFF00E676)
            ModelDownloadStatus.FAILED -> Color(0xFFFFB300)
            ModelDownloadStatus.IDLE -> Color.Transparent
        },
        label = "borderColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Slim linear progress indicator at the very top edge
            when (status) {
                ModelDownloadStatus.DOWNLOADING -> {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0.02f, 1.0f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = FrostCyan,
                        trackColor = Color(0x3300E5FF)
                    )
                }
                ModelDownloadStatus.COMPLETED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(Color(0xFF00E676))
                    )
                }
                ModelDownloadStatus.FAILED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(Color(0xFFFFB300))
                    )
                }
                ModelDownloadStatus.IDLE -> {}
            }

            // Main info row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (status) {
                        ModelDownloadStatus.DOWNLOADING -> {
                            Text(
                                text = "🌐",
                                fontSize = 11.sp,
                                modifier = Modifier.alpha(pulseAlpha)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val percent = (progress * 100).toInt().coerceIn(5, 99)
                            Text(
                                text = "AI LANGUAGE PACK [$languageName] $percent% • OFFLINE ACTIVE",
                                color = FrostCyan,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ModelDownloadStatus.COMPLETED -> {
                            Text(
                                text = "✓",
                                color = Color(0xFF00E676),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI NEURAL MODEL READY [$languageName] • ON-DEVICE TRANSLATION ACTIVE",
                                color = Color(0xFFB9F6CA),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ModelDownloadStatus.FAILED -> {
                            Text(
                                text = "⚠️",
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI PACK PAUSED • PLAYING VIA OFFLINE DICTIONARY",
                                color = Color(0xFFFFE082),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ModelDownloadStatus.IDLE -> {}
                    }
                }

                // Quick dismiss button
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = Color.LightGray.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
