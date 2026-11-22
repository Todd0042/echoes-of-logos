package com.voicerpg.engine.ui.combat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.EncounterDefinition
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.LogosGlow
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroPanel

@Composable
fun EnvironmentSwitcherBar(
    currentEnvironment: BattleEnvironment,
    onSelectEnvironment: (BattleEnvironment) -> Unit,
    onSelectEncounter: ((EncounterDefinition) -> Unit)? = null,
    onSummonMinion: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Environments
        BattleEnvironment.entries.forEach { env ->
            val isSelected = env == currentEnvironment
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) LogosGold.copy(alpha = 0.2f) else RetroPanel.copy(alpha = 0.85f)
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) LogosGold else RetroBorder,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelectEnvironment(env) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = env.icon,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = env.displayName.uppercase(),
                        color = if (isSelected) LogosGlow else Color(0xFFB0BEC5),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 2. Story Encounter Switchers (loaded data-driven from the active game's content)
        if (onSelectEncounter != null) {
            val encounterPresets = remember { GameContent.encounters.values.toList().take(4) }
            val presetLabels = listOf("👤 Solo", "👥 Duo", "⚔️ Squad", "👑 Boss")
            val presetTints = listOf(Color(0xFF81D4FA), Color(0xFFA5D6A7), Color(0xFFFFB74D), Color(0xFFFF8A80))
            encounterPresets.forEachIndexed { index, enc ->
                val label = presetLabels.getOrElse(index) { "⚔️ Encounter ${index + 1}" }
                val tint = presetTints[index % presetTints.size]
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E2430))
                        .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable { onSelectEncounter(enc) }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        color = tint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 3. Mid-Fight Reinforcement / Summon button
        if (onSummonMinion != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF332014))
                    .border(1.dp, Color(0xFFFF9800), RoundedCornerShape(6.dp))
                    .clickable { onSummonMinion() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "➕ Summon Minion",
                    color = Color(0xFFFFB74D),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
