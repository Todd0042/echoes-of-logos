package com.voicerpg.engine.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.voicerpg.engine.audio.CombatNarrator
import com.voicerpg.engine.localization.tr
import com.voicerpg.engine.model.LanguageCatalog
import com.voicerpg.engine.model.RegionOption
import com.voicerpg.engine.ui.theme.FrostCyan
import com.voicerpg.engine.ui.theme.HolyYellow
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.RetroBlack
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroBorderGold
import com.voicerpg.engine.ui.theme.RetroPanel

/**
 * Screen 2 of 2: Dialect / Regional Accent Selection.
 * Lets the player choose an accent/regional variant for the selected language
 * (e.g. English US vs UK vs Australia vs South Africa; Spanish Spain vs Mexico vs Argentina).
 * Includes an audio voice sample preview button and back button to return to Screen 1.
 */
@Composable
fun DialectSelectionScreen(
    languageCode: String,
    initialRegionCode: String = "",
    combatNarrator: CombatNarrator? = null,
    onBack: () -> Unit,
    onConfirm: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val langOption = remember(languageCode) {
        LanguageCatalog.getLanguage(languageCode)
    }

    val regionOptions = remember(langOption) {
        LanguageCatalog.getRegionsForLanguage(langOption.code)
    }

    var selectedRegionCode by remember {
        mutableStateOf(
            if (regionOptions.any { it.code.equals(initialRegionCode, ignoreCase = true) }) {
                initialRegionCode.uppercase()
            } else {
                regionOptions.firstOrNull()?.code ?: ""
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RetroBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            RetroBlack,
                            Color(0xFF0F1524),
                            RetroBlack
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row with Back button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RetroPanel)
                            .border(1.dp, RetroBorder, RoundedCornerShape(6.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tr("◀ LANGUAGES"),
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "🎙️ ${tr("SELECT DIALECT")}",
                        color = LogosGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.width(70.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Chosen Language Info Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RetroPanel.copy(alpha = 0.85f))
                        .border(1.dp, RetroBorderGold, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${langOption.nativeName} (${langOption.displayName})",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${tr("Code")}: [${langOption.code.uppercase()}]",
                                color = FrostCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Preview Voice Button
                        if (combatNarrator != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E3A5F))
                                    .border(1.dp, FrostCyan, RoundedCornerShape(6.dp))
                                    .clickable {
                                        val sampleText = when (langOption.code.lowercase()) {
                                            "es" -> "La aventura comienza ahora. Tu voz es tu poder."
                                            "fr" -> "L'aventure commence maintenant. Votre voix est votre pouvoir."
                                            "de" -> "Das Abenteuer beginnt jetzt. Deine Stimme ist deine Macht."
                                            "it" -> "L'avventura inizia ora. La tua voce è il tuo potere."
                                            "ja" -> "冒険が今始まる。あなたの声が力となる。"
                                            "ko" -> "모험이 지금 시작됩니다. 당신의 목소리가 힘입니다."
                                            "zh" -> "冒险现在开始。你的声音就是力量。"
                                            "ru" -> "Приключение начинается. Твой голос — твоя сила."
                                            "pt" -> "A aventura começa agora. Sua voz é seu poder."
                                            else -> "The journey begins now. Speak your command."
                                        }
                                        combatNarrator.speak(sampleText, force = true)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "🔊 ${tr("PREVIEW")}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Text(
                    text = tr("Choose your preferred regional accent for synthesis & speech recognition:"),
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
                )

                // List of dialects / regions
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(regionOptions, key = { it.code }) { region ->
                        val isSelected = region.code.equals(selectedRegionCode, ignoreCase = true)
                        DialectCard(
                            region = region,
                            isSelected = isSelected,
                            onClick = { selectedRegionCode = region.code }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Confirm and Continue Action Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(LogosGold, HolyYellow)
                            )
                        )
                        .clickable {
                            onConfirm(langOption.code, selectedRegionCode)
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${tr("CONFIRM & APPLY")} ➔",
                        color = RetroBlack,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun DialectCard(
    region: RegionOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF1B2E3D) else RetroPanel)
            .border(
                1.dp,
                if (isSelected) FrostCyan else RetroBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = region.displayName,
                    color = if (isSelected) FrostCyan else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (region.code.isNotBlank()) "${tr("Region code")}: ${region.code}" else tr("System default accent"),
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) FrostCyan else Color.Transparent)
                    .border(2.dp, if (isSelected) FrostCyan else RetroBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(RetroBlack)
                    )
                }
            }
        }
    }
}
