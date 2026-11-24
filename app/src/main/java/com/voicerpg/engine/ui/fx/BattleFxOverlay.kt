package com.voicerpg.engine.ui.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.content.PresentationConfig.DamageNumberStyle
import kotlin.math.roundToInt

/** Damage and heal numbers over the arena, in the game's [DamageNumberStyle]. */
@Composable
fun DamageNumbersLayer(director: BattleFxDirector, modifier: Modifier = Modifier) {
    director.frameTick // recompose every frame while numbers fly
    val style = director.presentation.damageNumberStyle
    Box(modifier = modifier.fillMaxSize()) {
        for (n in director.numbers) {
            val age = director.time - n.bornAt
            val (dx, dy) = n.riseAt(age)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset { IntOffset((n.x - 36f + dx.dp.toPx()).roundToInt(), (n.y - 52f + dy.dp.toPx()).roundToInt()) }
                    .graphicsLayer {
                        val s = n.scaleAt(age)
                        scaleX = s
                        scaleY = s
                        alpha = n.alphaAt(age)
                    }
            ) {
                if (n.label != null) {
                    Text(
                        text = n.label,
                        style = numberStyle(style, n.labelColor, if (n.big) 13 else 11),
                        modifier = labelBackground(style)
                    )
                }
                Text(text = n.text, style = numberStyle(style, n.color, if (n.big) 24 else 19), modifier = numberBackground(style))
            }
        }
    }
}

private fun numberStyle(style: DamageNumberStyle, color: Color, sizeSp: Int): TextStyle = when (style) {
    DamageNumberStyle.POP -> TextStyle(
        color = color, fontSize = sizeSp.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
        shadow = Shadow(Color.Black, blurRadius = 4f)
    )
    DamageNumberStyle.RECEIPT -> TextStyle(
        color = Color(0xFF212121), fontSize = (sizeSp - 3).sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
    )
    DamageNumberStyle.COMIC -> TextStyle(
        color = color, fontSize = (sizeSp + 2).sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
        shadow = Shadow(Color.Black, offset = androidx.compose.ui.geometry.Offset(3f, 3f), blurRadius = 0f)
    )
}

private fun numberBackground(style: DamageNumberStyle): Modifier = when (style) {
    DamageNumberStyle.RECEIPT -> Modifier
        .background(Color(0xFFFAFAF0), RoundedCornerShape(1.dp))
        .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(1.dp))
        .padding(horizontal = 6.dp, vertical = 1.dp)
    else -> Modifier
}

private fun labelBackground(style: DamageNumberStyle): Modifier = when (style) {
    DamageNumberStyle.COMIC -> Modifier
        .background(Color(0xFFFFEB3B), RoundedCornerShape(3.dp))
        .border(2.dp, Color.Black, RoundedCornerShape(3.dp))
        .padding(horizontal = 5.dp)
    DamageNumberStyle.RECEIPT -> Modifier.background(Color(0xFFFAFAF0)).padding(horizontal = 4.dp)
    DamageNumberStyle.POP -> Modifier
}

/** The full-screen white flash (Transcendental hits), already limited by the director. */
@Composable
fun ScreenFlashLayer(director: BattleFxDirector, modifier: Modifier = Modifier) {
    val a = director.screenFlash
    if (a > 0f) Box(modifier = modifier.fillMaxSize().background(Color.White.copy(alpha = 0.85f * a)))
}

/** The current move name, in the game's title-card style; slides in from the acting side. */
@Composable
fun TitleCardLayer(director: BattleFxDirector, modifier: Modifier = Modifier) {
    val card = director.titleCard ?: return
    director.frameTick
    val age = director.time - card.bornAt
    val slideIn = (age / 0.16f).coerceIn(0f, 1f)
    val fadeOut = ((ShownTitleCard.LIFETIME - age) / 0.25f).coerceIn(0f, 1f)
    val fromSide = if (card.fromParty) -1f else 1f
    Box(modifier = modifier.fillMaxSize().padding(top = 36.dp), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier.graphicsLayer {
                translationX = fromSide * (1f - slideIn) * size.width * 0.8f
                alpha = fadeOut
            }
        ) {
            TitleCardBody(card.text, director.presentation.titleCardStyle)
        }
    }
}

@Composable
private fun TitleCardBody(text: String, style: com.voicerpg.engine.content.PresentationConfig.TitleCardStyle) {
    when (style) {
        com.voicerpg.engine.content.PresentationConfig.TitleCardStyle.BANNER -> Text(
            text = text.uppercase(),
            color = Color(0xFFFFE082),
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, Color(0xE6101018), Color(0xE6101018), Color.Transparent)))
                .padding(horizontal = 36.dp, vertical = 6.dp)
        )
        com.voicerpg.engine.content.PresentationConfig.TitleCardStyle.HEADLINE -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color(0xFFF5F1E6), RoundedCornerShape(2.dp))
                .border(2.dp, Color(0xFF111111), RoundedCornerShape(2.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text("— BREAKING —", color = Color(0xFFB71C1C), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
            Text(text.uppercase(), color = Color(0xFF111111), fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif)
        }
        com.voicerpg.engine.content.PresentationConfig.TitleCardStyle.PA_ANNOUNCEMENT -> Text(
            text = "📢  ${text.uppercase()}",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .background(Color(0xFF1565C0), RoundedCornerShape(6.dp))
                .border(2.dp, Color(0xFFFFEB3B), RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
        com.voicerpg.engine.content.PresentationConfig.TitleCardStyle.SCROLL -> Text(
            text = "❦  $text  ❦",
            color = Color(0xFF4E342E),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            fontFamily = FontFamily.Serif,
            modifier = Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFE8D5A3), Color(0xFFF6E7C1), Color(0xFFE0C98F))),
                    RoundedCornerShape(10.dp)
                )
                .border(2.dp, Color(0xFF8D6E63), RoundedCornerShape(10.dp))
                .padding(horizontal = 20.dp, vertical = 6.dp)
        )
    }
}

/** The resonance streak badge ("RESONANCE x3"), top right of the arena; it pulses when it grows. */
@Composable
fun StreakBadge(director: BattleFxDirector, modifier: Modifier = Modifier) {
    val streak = director.streak ?: return
    director.frameTick
    val age = director.time - streak.bornAt
    val pop = 1f + 0.35f * kotlin.math.exp(-age / 0.12f)
    val label = director.presentation.streak.label.replace("{n}", streak.count.toString())
    Box(modifier = modifier.fillMaxSize().padding(top = 8.dp, end = 10.dp), contentAlignment = Alignment.TopEnd) {
        Text(
            text = label,
            color = Color(0xFFFFF59D),
            fontSize = (11 + streak.count.coerceAtMost(6)).sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .graphicsLayer { scaleX = pop; scaleY = pop }
                .background(Color(0xCC4A148C), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFFFFD54F), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * The cut-in: a diagonal panel in the character's colour slides across with their portrait (or their
 * pixel sprite, zoomed), name, quote and speed lines.
 */
@Composable
fun CutInLayer(director: BattleFxDirector, modifier: Modifier = Modifier) {
    val shown = director.cutIn ?: return
    director.frameTick
    val cue = shown.cue
    val context = androidx.compose.ui.platform.LocalContext.current
    val portrait = androidx.compose.runtime.remember(cue.portrait) {
        cue.portrait?.let { com.voicerpg.engine.ui.story.StoryAssetLoader.loadBitmap(context, it) }
    }
    val age = director.time - shown.bornAt
    val life = ShownCutIn.LIFETIME
    val slide = when {
        age < 0.12f -> 1f - age / 0.12f            // sliding in
        age > life - 0.12f -> -(age - (life - 0.12f)) / 0.12f // sliding out the other way
        else -> 0f
    }
    val side = if (cue.isEnemy) 1f else -1f
    val color = cue.color ?: Color(0xFFFFD54F)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = -10f * side
                    translationX = side * slide * size.width
                }
                .fillMaxSize(fraction = 1f)
                .padding(vertical = 0.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 70.dp)
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(color.copy(alpha = 0.95f), Color(0xF0101018), color.copy(alpha = 0.95f))))
                    .border(2.dp, Color.White.copy(alpha = 0.8f))
            ) {
                // Speed lines
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val shift = (age * 2400f) % size.width
                    for (i in 0 until 14) {
                        val y = size.height * ((i * 37 % 100) / 100f)
                        val x = (i * 173f + shift * side) % size.width
                        drawLine(Color.White.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(x, y),
                            androidx.compose.ui.geometry.Offset(x + 90f, y), strokeWidth = 2f)
                    }
                }
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (portrait != null) {
                        androidx.compose.foundation.Image(
                            bitmap = portrait,
                            contentDescription = cue.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(fraction = 0.38f).border(2.dp, Color.White)
                        )
                    } else {
                        CutInSprite(cue.characterId, flipped = cue.isEnemy, modifier = Modifier.fillMaxSize(fraction = 0.3f))
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(cue.name.uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace, style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 6f)))
                        if (!cue.quote.isNullOrBlank()) {
                            Text("“${cue.quote}”", color = Color(0xFFFFF8E1), fontSize = 14.sp, fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Bold, style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 6f)))
                        }
                    }
                }
            }
        }
    }
}

/** Fallback portrait: the character's pixel sprite, zoomed. */
@Composable
private fun CutInSprite(id: String, flipped: Boolean, modifier: Modifier = Modifier) {
    val (matrix, palette) = androidx.compose.runtime.remember(id) {
        com.voicerpg.engine.ui.sprites.PixelSpriteData.getPixelMatrix(id, com.voicerpg.engine.ui.sprites.SpriteFrame.ACTION_CAST)
    }
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cols = matrix.maxOfOrNull { it.length } ?: return@Canvas
        val px = minOf(size.width / cols, size.height / matrix.size)
        for (r in matrix.indices) for (c in matrix[r].indices) {
            val ch = matrix[r][c]
            if (ch == '.') continue
            val color = palette[ch] ?: continue
            val col = if (flipped) cols - 1 - c else c
            drawRect(color, androidx.compose.ui.geometry.Offset(col * px, r * px), androidx.compose.ui.geometry.Size(px, px))
        }
    }
}
