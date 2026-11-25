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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.model.CharacterStance
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.LogosGlow

@Composable
fun PixelCharacterView(
    characterId: String,
    name: String,
    subtitle: String,
    hpRatio: Float,
    currentHp: Int,
    isTurnReady: Boolean,
    isActiveTurn: Boolean,
    isTargeted: Boolean = false,
    stance: CharacterStance = CharacterStance.READY,
    isFlippedHorizontally: Boolean = false,
    isGhost: Boolean = false,
    spriteAlpha: Float? = null,
    pixelSize: Dp = 2.2.dp,
    depthScale: Float = 1f,
    /**
     * Opacity of the thin health sliver under the name (regular enemies: targeted or just hit), or null for
     * none. Gauges otherwise live in the HUD (BattleHud), not on the field.
     */
    sliverAlpha: Float? = null,
    /** The sprite to draw (a sheet or a legacy grid); null looks it up from [characterId]. */
    sprite: SpriteSource? = null,
    /** The resting animation from the combat state (SpriteBase); the battle's cues play over it. */
    baseAnim: AnimTag = AnimTag.IDLE,
    /** Live battle effects (flash, knockback, squash, glow, intent, charge bar), from the FX director. */
    fx: com.voicerpg.engine.ui.fx.CombatantFx? = null,
    /** Reports where the sprite itself is drawn (for effects such as the death shatter). */
    onSpriteLayout: ((androidx.compose.ui.layout.LayoutCoordinates) -> Unit)? = null,
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

    // Sprite and animation: the director's animator in battle, a local one elsewhere (tutorial, gallery).
    val source = sprite ?: androidx.compose.runtime.remember(characterId) { SpriteLibrary.source(characterId) }
    val animator = fx?.animator ?: androidx.compose.runtime.remember(characterId) { SpriteAnimator() }
    androidx.compose.runtime.SideEffect {
        animator.source = source
        animator.base = baseAnim
    }
    if (fx == null) {
        androidx.compose.runtime.LaunchedEffect(animator) {
            var last = 0L
            while (true) {
                androidx.compose.runtime.withFrameNanos { now ->
                    if (last != 0L) animator.step((now - last) / 1_000_000_000f)
                    last = now
                }
            }
        }
    }
    // A sprite with its own fall animation lies down by itself; others are tipped over as before.
    val hasOwnFall = (if (isFlippedHorizontally) "death" else "ko") in source.animationNames && source is SheetSprite

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

    val facing = if (isFlippedHorizontally) -1f else 1f
    Column(
        modifier = modifier
            .offset(x = stepForwardOffset)
            .graphicsLayer {
                // Knockback, squash/stretch and the attack lean are read here (layer-only: no relayout).
                val squash = fx?.squash ?: 0f
                translationX = ((fx?.knockback ?: 0f) + facing * 10f * (fx?.lean ?: 0f)).dp.toPx()
                scaleX = depthScale * (1f + 0.18f * squash)
                scaleY = depthScale * (1f - 0.18f * squash)
                rotationZ = facing * 6f * (fx?.lean ?: 0f)
                // A small idle hop on the breathing cycle (still when fallen)
                translationY = if (isDead || source is SheetSprite) 0f else -(1.5f * cycleProgress).dp.toPx() // sheets breathe in their own idle
                transformOrigin = TransformOrigin(0.5f, 1.0f)
                alpha = if (fx?.hidden == true) 0f else (fx?.fade ?: 1f)
            }
            .clickable(enabled = !isDead && onClick != null) { onClick?.invoke() }
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A taunt in a speech bubble
        val speech = fx?.speech
        if (speech != null && !isDead) {
            Text(
                text = speech,
                color = Color(0xFF111111),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                modifier = Modifier
                    .padding(bottom = 2.dp)
                    .width(110.dp)
                    .background(Color(0xFFFFFDE7), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF424242), RoundedCornerShape(8.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }

        // Telegraphed intent (what this enemy is about to do)
        val intent = fx?.intent
        if (intent != null && !isDead) {
            Text(
                text = intent,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 1.dp)
            )
        }

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

        if (sliverAlpha != null && !isDead) {
            com.voicerpg.engine.ui.combat.EnemySliver(hpRatio = hpRatio, alpha = sliverAlpha, modifier = Modifier.padding(top = 1.dp))
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Pixel Art Canvas anchored to a perspective ground contact shadow
        val density = androidx.compose.ui.platform.LocalDensity.current
        val artPx = when (source) {
            is SheetSprite -> sheetArtPx(sizeFactor = (pixelSize / 2.2.dp) * source.scale)
            is GridSprite -> with(density) { pixelSize.toPx() }
        }
        val spriteWidth = with(density) { (source.cellW * artPx).toDp() }
        val spriteHeight = with(density) { (source.feet.second.coerceAtLeast(1) * artPx).toDp() }
        val tipOver = isDead && !hasOwnFall

        Box(
            modifier = Modifier
                .padding(top = if (tipOver) 16.dp else 0.dp)
                .graphicsLayer {
                    rotationZ = fx?.spin ?: 0f
                    if (tipOver) {
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

            // Wind-up / charge aura behind the sprite
            val glow = fx?.glow
            if (glow != null && !isDead) {
                Canvas(modifier = Modifier.size(width = spriteWidth + 28.dp, height = spriteHeight + 16.dp)) {
                    val strength = (fx.glowStrength).coerceIn(0f, 1f)
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(glow.copy(alpha = 0.55f * strength), glow.copy(alpha = 0.18f * strength), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.55f),
                            radius = size.maxDimension * (0.45f + 0.15f * strength)
                        ),
                        topLeft = Offset.Zero,
                        size = size
                    )
                }
            }

            SpriteCanvas(
                source = source,
                frame = animator.frame,
                artPx = artPx,
                flipped = isFlippedHorizontally,
                flash = fx?.flash ?: 0f,
                hurtTint = stance == CharacterStance.DAMAGED && source is GridSprite,
                modifier = if (onSpriteLayout != null) Modifier.onGloballyPositioned(onSpriteLayout) else Modifier
            )
        }
    }
}
