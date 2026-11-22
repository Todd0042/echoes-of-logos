package com.voicerpg.engine.ui.setup

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.voicerpg.engine.model.LanguageCatalog
import com.voicerpg.engine.ui.theme.FrostCyan
import com.voicerpg.engine.ui.theme.HolyYellow
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.LogosGlow
import com.voicerpg.engine.ui.theme.RetroBlack
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroBorderGold
import com.voicerpg.engine.ui.theme.RetroPanel

/**
 * First-launch and settings dialog prompting the player to choose their
 * preferred language and regional voice accent.
 * Fully content-neutral: presents generic language and regional accent metadata.
 */
@Composable
fun LanguageRegionDialog(
    isOpen: Boolean,
    initialLanguage: String = "en",
    initialRegion: String = "",
    onConfirm: (language: String, region: String) -> Unit,
    onDismiss: () -> Unit = {}
) {
    if (!isOpen) return

    var selectedLang by remember(initialLanguage) {
        mutableStateOf(if (initialLanguage.isNotBlank()) initialLanguage.lowercase() else "en")
    }
    var selectedReg by remember(initialRegion) {
        mutableStateOf(initialRegion.uppercase())
    }

    val currentRegions = LanguageCatalog.getRegionsForLanguage(selectedLang)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RetroBlack.copy(alpha = 0.97f))
                .border(2.dp, RetroBorderGold, RoundedCornerShape(12.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "🌐 LANGUAGE & VOICE REGION",
                    color = LogosGold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Select your language and preferred accent",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                )

                // 1. Language Selection Section
                Text(
                    text = "LANGUAGE",
                    color = HolyYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )

                // Grid of 6 languages (2 rows of 3)
                val languages = LanguageCatalog.SUPPORTED_LANGUAGES
                val chunkedLanguages = languages.chunked(3)
                chunkedLanguages.forEach { rowLanguages ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowLanguages.forEach { langOpt ->
                            val isSelected = selectedLang.equals(langOpt.code, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) LogosGold else Color(0xFF1B2230))
                                    .border(
                                        1.dp,
                                        if (isSelected) LogosGlow else RetroBorder,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        selectedLang = langOpt.code
                                        // Reset region if not in the new language's options
                                        val newRegions = LanguageCatalog.getRegionsForLanguage(langOpt.code)
                                        if (newRegions.none { it.code == selectedReg }) {
                                            selectedReg = ""
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = langOpt.nativeName,
                                        color = if (isSelected) RetroBlack else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "[${langOpt.code.uppercase()}]",
                                        color = if (isSelected) RetroBlack.copy(alpha = 0.8f) else Color.Gray,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Region / Accent Selection Section
                Text(
                    text = "REGIONAL ACCENT / PREFERENCE",
                    color = HolyYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )

                // Grid of Regions (rows of 2)
                val chunkedRegions = currentRegions.chunked(2)
                chunkedRegions.forEach { rowRegions ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowRegions.forEach { regOpt ->
                            val isSelected = selectedReg.equals(regOpt.code, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) FrostCyan else RetroPanel)
                                    .border(
                                        1.dp,
                                        if (isSelected) Color.White else RetroBorder,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedReg = regOpt.code }
                                    .padding(vertical = 7.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = regOpt.displayName,
                                    color = if (isSelected) RetroBlack else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        if (rowRegions.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Explanation note
                Text(
                    text = "Voices installed on your device matching this language and region will be prioritized for characters and narration. You can change this anytime in Options.",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Confirm Button
                Button(
                    onClick = { onConfirm(selectedLang, selectedReg) },
                    colors = ButtonDefaults.buttonColors(containerColor = LogosGold),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = "CONFIRM & PROCEED ▶",
                        color = RetroBlack,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
