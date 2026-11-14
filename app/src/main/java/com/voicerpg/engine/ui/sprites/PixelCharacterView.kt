package com.voicerpg.engine.ui.sprites

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.model.CharacterStance
import com.voicerpg.engine.ui.theme.HpCritical
import com.voicerpg.engine.ui.theme.HpGreen
import com.voicerpg.engine.ui.theme.HpWarning
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.LogosGlow
import com.voicerpg.engine.ui.theme.MpBlue

@Composable
fun PixelCharacterView(
    characterId: String,
    name: String,
    subtitle: String,
    hpRatio: Float,
    currentHp: Int,
    maxHp: Int,
    mpRatio: Float? = null,
    atbRatio: Float,
    isTurnReady: Boolean,
    isActiveTurn: Boolean,
    isTargeted: Boolean = false,
    stance: CharacterStance = CharacterStance.READY,
    isFlippedHorizontally: Boolean = false,
    isGhost: Boolean = false,
    spriteAlpha: Float? = null,
    pixelSize: Dp = 2.2.dp,
    depthScale: Float = 1f,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDead = currentHp <= 0 || stance == CharacterStance.DEAD

    // 2-Frame Idle Breathing & Knee Buckle Animation (stopped when dead)
    val infiniteTransition = rememberInfiniteTransition(label = "knee_buckle_$characterId")
    val cycleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crouch_cycle_$characterId"
    )

    val isEthereal = isGhost || spriteAlpha != null
    val baseAlpha = spriteAlpha ?: if (isGhost) 0.45f else 1.0f
    val etherealPulse by infiniteTransition.animateFloat(
        initialValue = -0.12f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ethereal_pulse_$characterId"
    )
    val spriteFinalAlpha = if (isEthereal) {
        (baseAlpha + etherealPulse).coerceIn(0.15f, 0.95f)
    } else {
        baseAlpha
    }

    val currentFrame = when {
        isDead -> SpriteFrame.DAMAGED
        stance == CharacterStance.DAMAGED -> SpriteFrame.DAMAGED
        stance == CharacterStance.CASTING -> SpriteFrame.ACTION_CAST
        cycleProgress > 0.5f -> SpriteFrame.IDLE_CROUCH
        else -> SpriteFrame.IDLE_UPRIGHT
    }

    val (matrix, palette) = PixelSpriteData.getPixelMatrix(characterId, currentFrame)

    val targetStepOffset = when {
        isDead -> 0.dp
        stance == CharacterStance.CASTING -> if (isFlippedHorizontally) -16.dp else 16.dp
        isActiveTurn || isTurnReady -> if (isFlippedHorizontally) -8.dp else 8.dp
        stance == CharacterStance.DAMAGED -> if (isFlippedHorizontally) 8.dp else -8.dp
        else -> 0.dp
    }
    val stepForwardOffset by animateDpAsState(
        targetValue = targetStepOffset,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "step_offset_$characterId"
    )

    Column(
        modifier = modifier
            .offset(x = stepForwardOffset)
            .graphicsLayer {
                scaleX = depthScale
                scaleY = depthScale
                transformOrigin = TransformOrigin(0.5f, 1.0f)
            }
            .clickable(enabled = !isDead && onClick != null) { onClick?.invoke() }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Targeted Indicator / Floating Name
        if (isTargeted && !isDead) {
            Text(
                text = "▼ TARGET",
                color = LogosGold,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 1.dp)
            )
        }
        Text(
            text = if (isDead) "💀 $name" else name,
            color = if (isDead) Color(0xFF90A4AE) else if (isActiveTurn || isTargeted) LogosGold else Color.White,
            fontSize = if (name.length > 12) 9.sp else 10.sp,
            fontWeight = if (isActiveTurn || isTargeted) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )

        if (isDead) {
            // Defeated / Fallen Badge — zero resources available
            Box(
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF2B1113))
                    .border(1.dp, Color(0xFFEF5350), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFlippedHorizontally) "DEFEATED" else "FALLEN",
                    color = Color(0xFFFF8A80),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            // Mini HP Bar
            val hpColor = when {
                hpRatio > 0.5f -> HpGreen
                hpRatio > 0.2f -> HpWarning
                else -> HpCritical
            }

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF1E1E28))
                    .border(0.5.dp, Color.Black, RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(hpRatio.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(hpColor)
                )
            }

            // Optional MP Bar
            if (mpRatio != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0xFF1E1E28))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(mpRatio.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(MpBlue)
                    )
                }
            }

            // ATB Bar
            Spacer(modifier = Modifier.height(1.dp))
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color(0xFF1E1E28))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(atbRatio.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(if (isTurnReady) LogosGold else Color(0xFFFF9800))
                )
            }

            Text(
                text = "$currentHp/$maxHp",
                color = Color(0xFFD0D8DC),
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 1.dp)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Pixel Art Canvas anchored to a perspective ground contact shadow
        val spriteWidth = pixelSize * (matrix.firstOrNull()?.length ?: 12)
        val spriteHeight = pixelSize * matrix.size

        Box(
            modifier = Modifier
                .padding(top = if (isDead) 16.dp else 0.dp)
                .graphicsLayer {
                    if (isDead) {
                        rotationZ = if (isFlippedHorizontally) 90f else -90f
                        transformOrigin = TransformOrigin(0.5f, 0.85f)
                        alpha = if (isEthereal) (spriteFinalAlpha * 0.55f) else 0.55f
                    } else if (isEthereal) {
                        alpha = spriteFinalAlpha
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Ground contact shadow + turn readiness floor ring locked directly under the soles
            Canvas(
                modifier = Modifier
                    .width(spriteWidth + 18.dp)
                    .height(14.dp)
                    .offset(y = 4.dp)
            ) {
                val shadowW = size.width
                val shadowH = size.height
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xB8000000),
                            Color(0x66000000),
                            Color.Transparent
                        ),
                        center = Offset(shadowW / 2f, shadowH / 2f),
                        radius = shadowW * 0.5f
                    ),
                    topLeft = Offset.Zero,
                    size = Size(shadowW, shadowH)
                )
                if (!isDead && (isActiveTurn || isTurnReady)) {
                    val ringColor = if (isActiveTurn) LogosGold else Color(0xFFFFB74D)
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ringColor.copy(alpha = 0.45f),
                                ringColor.copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            center = Offset(shadowW / 2f, shadowH / 2f),
                            radius = shadowW * 0.52f
                        ),
                        topLeft = Offset(-2f, -1f),
                        size = Size(shadowW + 4f, shadowH + 2f)
                    )
                }
            }

            Canvas(
                modifier = Modifier.size(
                    width = spriteWidth,
                    height = spriteHeight
                )
            ) {
                val pPx = pixelSize.toPx()
                val numCols = matrix.firstOrNull()?.length ?: 12

                for (row in matrix.indices) {
                    val line = matrix[row]
                    for (col in line.indices) {
                        val char = line[col]
                        if (char != '.') {
                            val color = palette[char] ?: Color.Magenta
                            val targetCol = if (isFlippedHorizontally) (numCols - 1 - col) else col
                            drawRect(
                                color = if (stance == CharacterStance.DAMAGED) Color(0xFFFF5252) else color,
                                topLeft = Offset(targetCol * pPx, row * pPx),
                                size = Size(pPx, pPx)
                            )
                        }
                    }
                }
            }
        }
    }
}
