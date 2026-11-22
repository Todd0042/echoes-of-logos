package com.voicerpg.engine.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.ui.theme.FrostCyan
import com.voicerpg.engine.ui.theme.HolyYellow
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.RetroBlack
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroBorderGold
import com.voicerpg.engine.ui.theme.RetroDeepSlate
import com.voicerpg.engine.ui.theme.RetroPanel

/**
 * Educational Pre-Permission Rationale Modal Dialog.
 * Explains VoiceRPG's hands-free voice gameplay, pocket mode, on-device audio privacy,
 * and seamless touch-only fallback before prompting for Android RECORD_AUDIO permission.
 */
@Composable
fun MicPermissionRationaleDialog(
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, RetroBorderGold, RoundedCornerShape(16.dp)),
            color = RetroBlack.copy(alpha = 0.98f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Crest / Icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(LogosGold.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                        .border(1.5.dp, RetroBorderGold, RoundedCornerShape(26.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = LogosGold,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = GameContent.manifest.voicePermissionTitle.ifBlank { "HANDS-FREE VOICE CONTROL" },
                    color = LogosGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = GameContent.manifest.voicePermissionSubtitle.ifBlank {
                        if (GameContent.manifest.gameTitle.isNotBlank()) "${GameContent.manifest.gameTitle} Hands-Free Voice Controls"
                        else "Hands-Free Voice Controls"
                    },
                    color = Color.LightGray.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Feature Pill 1: Hands-Free Voice Commands
                RationaleFeatureItem(
                    icon = Icons.Default.Mic,
                    iconTint = FrostCyan,
                    title = "Hands-Free & Pocket Mode",
                    description = "Cast spells, make dialogue choices, and explore the chronicle seamlessly while walking with your screen dimmed in your pocket."
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Feature Pill 2: Privacy
                RationaleFeatureItem(
                    icon = Icons.Default.Security,
                    iconTint = HolyYellow,
                    title = "Private & On-Device Processing",
                    description = "Speech recognition runs locally via Android speech services. The engine never records, stores, or uploads your voice to any remote servers."
                )


                Spacer(modifier = Modifier.height(8.dp))

                // Feature Pill 3: Touch Always Supported
                RationaleFeatureItem(
                    icon = Icons.Default.TouchApp,
                    iconTint = Color(0xFFA5D6A7),
                    title = "Tactile Controls Always Available",
                    description = "Prefer playing quietly? Every single action, battle decision, and story branch is always 100% playable via tactile screen taps."
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                if (isPermanentlyDenied) {
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, RetroBorderGold, RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3B2B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = HolyYellow,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "OPEN APP SETTINGS",
                                color = HolyYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, RetroBorderGold, RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3B2B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = HolyYellow,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ENABLE VOICE COMMANDS",
                                color = HolyYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, RetroBorder, RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = RetroDeepSlate),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "PLAY SILENTLY (TOUCH ONLY)",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun RationaleFeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RetroPanel.copy(alpha = 0.85f))
            .border(1.dp, RetroBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = Color.LightGray.copy(alpha = 0.8f),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
