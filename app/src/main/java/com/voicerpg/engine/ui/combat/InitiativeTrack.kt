package com.voicerpg.engine.ui.combat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.CombatPhase
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroPanel

@Composable
fun InitiativeTrack(
    phase: CombatPhase,
    roundNumber: Int,
    party: List<com.voicerpg.engine.model.PartyMember> = emptyList(),
    activePartyMemberId: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RetroPanel.copy(alpha = 0.9f))
            .border(1.dp, RetroBorder, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Round Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ROUND $roundNumber",
                    color = LogosGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (phase) {
                        CombatPhase.INACTIVE -> "⏸ COMBAT STANDBY"
                        CombatPhase.ATB_WAITING -> "⏳ TURN GAUGE CHARGING"
                        CombatPhase.PLAYER_INPUT -> "▶ PLAYER TURN (CHANT NOW)"
                        CombatPhase.INCANTATION_RESOLVING -> "⚡ RESOLVING LOGOS..."
                        CombatPhase.SPELL_VFX_PLAYING -> "🔥 UNLEASHING ACTION"
                        CombatPhase.ENEMY_ACTIONS -> "⚠️ ENEMY TURN (STRIKING)"
                        CombatPhase.BATTLE_WON -> "🏆 VICTORY ACHIEVED"
                        CombatPhase.BATTLE_LOST -> "💀 DEFEAT"
                    },
                    color = when (phase) {
                        CombatPhase.ATB_WAITING -> Color(0xFFFFD54F)
                        CombatPhase.PLAYER_INPUT -> Color(0xFF80D8FF)
                        CombatPhase.INCANTATION_RESOLVING -> LogosGold
                        CombatPhase.ENEMY_ACTIONS -> Color(0xFFFF5252)
                        CombatPhase.BATTLE_WON -> Color(0xFF69F0AE)
                        else -> Color.White
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Timeline turn icons with live active hero highlight and death status
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (party.isEmpty()) {
                    GameContent.allSpeakers.filter { !it.isNarrator }.take(4).forEach { speaker ->
                        TimelineIcon(
                            label = speaker.name.take(1).uppercase(),
                            isCurrent = false,
                            isAlive = true,
                            color = speaker.themeColor
                        )
                    }
                } else {
                    party.forEach { member ->
                        val isAlive = member.isAlive
                        val isCurrent = isAlive && phase == CombatPhase.PLAYER_INPUT && activePartyMemberId == member.id
                        val label = if (isAlive) member.name.take(1).uppercase() else "💀"
                        TimelineIcon(
                            label = label,
                            isCurrent = isCurrent,
                            isAlive = isAlive,
                            color = if (isAlive) member.avatarTint else Color(0xFF37474F)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineIcon(
    label: String,
    isCurrent: Boolean,
    isAlive: Boolean,
    color: Color
) {
    Box(
        modifier = Modifier
            .size(if (isCurrent) 22.dp else 18.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                if (isCurrent) 2.dp else 1.dp,
                if (isCurrent) LogosGold else if (!isAlive) Color(0xFFD32F2F) else Color.Black,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isAlive) Color.White else Color(0xFFEF5350),
            fontSize = if (label == "💀") 8.sp else if (isCurrent) 10.sp else 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
