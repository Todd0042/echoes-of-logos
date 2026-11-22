package com.voicerpg.engine.ui.combat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.model.ResonanceResult
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.LogosGlow
import com.voicerpg.engine.ui.theme.RetroBlack

@Composable
fun LogosBanner(
    resonance: ResonanceResult?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "banner_shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )

    AnimatedVisibility(
        visible = visible && resonance != null && resonance.bonusPercent >= 50,
        enter = scaleIn(tween(250)) + fadeIn(),
        exit = scaleOut(tween(200)) + fadeOut(),
        modifier = modifier
    ) {
        val tier = resonance?.tier ?: ResonanceTier.BASIC
        val bonus = resonance?.bonusPercent ?: 0

        val (headerTitle, headerColor, bgGradient) = when (tier) {
            ResonanceTier.TRANSCENDENTAL -> Triple(
                "👑 TRANSCENDENTAL LOGOS (+${bonus}% MAX) 👑",
                Color(0xFFFFEE58),
                listOf(Color(0xFFE040FB), Color(0xFFFFD54F), Color(0xFF0D0D1A))
            )
            ResonanceTier.MYTHIC -> Triple(
                "🌟 MYTHIC LOGOS RESONANCE (+${bonus}%) 🌟",
                LogosGlow,
                listOf(LogosGold, Color(0xFF1B1429), Color(0xFF0D0D1A))
            )
            ResonanceTier.MASTER -> Triple(
                "✦ MASTER INCANTATION (+${bonus}%) ✦",
                Color(0xFFFFD54F),
                listOf(Color(0xFFFF9800), Color(0xFF1B1429), Color(0xFF0D0D1A))
            )
            else -> Triple(
                "⚡ ADEPT RESONANCE (+${bonus}%) ⚡",
                Color(0xFF80DEEA),
                listOf(Color(0xFF00ACC1), Color(0xFF141A29), Color(0xFF0D0D1A))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(colors = bgGradient))
                .border(2.dp, headerColor.copy(alpha = shimmerAlpha), RoundedCornerShape(10.dp))
                .padding(vertical = 10.dp, horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = headerTitle,
                    color = headerColor,
                    fontSize = if (tier == ResonanceTier.TRANSCENDENTAL) 16.sp else 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "MULTIPLIER: ${String.format("%.2f", resonance?.damageMultiplier ?: 1.0f)}x DAMAGE | VFX: ${resonance?.particleCount ?: 40} PARTICLES",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Breakdown of vocal and lexical scoring attributes
                val acoustic = resonance?.acousticProfile
                val volumeText = "Vol: ${String.format("%.1f", acoustic?.peakVolumeDb ?: 0f)}dB"
                val inflectionText = if ((acoustic?.volumeDynamicRange ?: 0f) >= 4f) "Crescendo: +${(acoustic?.inflectionScore ?: 0f) * 100}%" else "Inflection: OK"
                val rootsText = if (!resonance?.matchedThematicRoots.isNullOrEmpty()) "Roots: ${resonance?.matchedThematicRoots?.joinToString("/")}" else "Lexicon: Standard"

                Row {
                    Text(
                        text = "$volumeText • $inflectionText • $rootsText",
                        color = Color(0xFFFFF9C4),
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
