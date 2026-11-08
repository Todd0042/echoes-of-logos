package com.voicerpg.engine.ui.setup

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.localization.ModelDownloadStatus
import com.voicerpg.engine.localization.TranslationManager
import com.voicerpg.engine.localization.tr
import com.voicerpg.engine.model.LanguageCatalog
import com.voicerpg.engine.ui.theme.FrostCyan
import com.voicerpg.engine.ui.theme.HpCritical
import com.voicerpg.engine.ui.theme.HolyYellow
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.RetroBlack
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroBorderGold
import com.voicerpg.engine.ui.theme.RetroPanel

/**
 * Gatekeeper Screen: Language Download Standby Screen.
 * 
 * Strict Gatekeeper Contract:
 * When a requested language neural model / pack is not present on the phone, the game
 * will NOT fall back to English. It halts gracefully on this standby screen, displays live
 * download progress, and automatically resumes into the game the moment the download succeeds.
 * 
 * If the user does not want to wait, they can click [◀ CHOOSE A DIFFERENT LANGUAGE] to pick
 * a language that is already installed or has faster network availability.
 */
@Composable
fun LanguageDownloadStandbyScreen(
    languageCode: String,
    regionCode: String,
    onDownloadComplete: (String, String) -> Unit,
    onChangeLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloadStatus by TranslationManager.downloadStatus.collectAsState()
    val downloadProgress by TranslationManager.downloadProgress.collectAsState()
    val downloadTargetName by TranslationManager.downloadTargetName.collectAsState()

    val langOption = LanguageCatalog.getLanguage(languageCode)
    val displayName = downloadTargetName.ifBlank { langOption.displayName }

    // When download completes, automatically notify callback to resume game
    LaunchedEffect(downloadStatus) {
        if (downloadStatus == ModelDownloadStatus.COMPLETED || TranslationManager.isModelInstalled(languageCode)) {
            onDownloadComplete(languageCode, regionCode)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

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
                            Color(0xFF0F1826),
                            RetroBlack
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RetroPanel)
                    .border(1.dp, RetroBorderGold, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2D42))
                        .border(2.dp, FrostCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🌐",
                        fontSize = 30.sp,
                        modifier = Modifier.rotate(if (downloadStatus == ModelDownloadStatus.DOWNLOADING) rotation else 0f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Status Title
                Text(
                    text = when (downloadStatus) {
                        ModelDownloadStatus.DOWNLOADING -> "Downloading Language Pack"
                        ModelDownloadStatus.COMPLETED -> "Language Pack Ready!"
                        ModelDownloadStatus.FAILED -> "Download Interrupted"
                        ModelDownloadStatus.IDLE -> "Preparing Download..."
                    },
                    color = when (downloadStatus) {
                        ModelDownloadStatus.FAILED -> HpCritical
                        ModelDownloadStatus.COMPLETED -> FrostCyan
                        else -> LogosGold
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Primary Message text
                Text(
                    text = when (downloadStatus) {
                        ModelDownloadStatus.DOWNLOADING ->
                            "Currently downloading $displayName language pack. Please stand by..."
                        ModelDownloadStatus.COMPLETED ->
                            "$displayName voice & text models ready. Resuming story..."
                        ModelDownloadStatus.FAILED ->
                            "Network connection lost while downloading $displayName pack. Please check connection or retry."
                        ModelDownloadStatus.IDLE ->
                            "Connecting to Google ML Kit repository for $displayName..."
                    },
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Progress Bar or Status Graphic
                when (downloadStatus) {
                    ModelDownloadStatus.DOWNLOADING, ModelDownloadStatus.IDLE -> {
                        LinearProgressIndicator(
                            progress = { downloadProgress.coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = FrostCyan,
                            trackColor = Color(0xFF1B2838),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            color = FrostCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ModelDownloadStatus.COMPLETED -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = HolyYellow,
                            strokeWidth = 3.dp
                        )
                    }
                    ModelDownloadStatus.FAILED -> {
                        // Retry button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF381E1E))
                                .border(1.dp, HpCritical, RoundedCornerShape(6.dp))
                                .clickable {
                                    TranslationManager.setLanguageAndRegion(languageCode, regionCode)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "🔄 RETRY DOWNLOAD",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // System Text-To-Speech Voices option
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF141C2A))
                        .border(1.dp, RetroBorder, RoundedCornerShape(6.dp))
                        .clickable {
                            try {
                                val installIntent = Intent()
                                installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
                                installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(installIntent)
                            } catch (_: Throwable) {
                                val settingsIntent = Intent("com.android.settings.TTS_SETTINGS")
                                settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(settingsIntent)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔊 Android TTS Voice Data",
                                color = LogosGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Open system voice settings if your device requires TTS audio files.",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "OPEN ➔",
                            color = HolyYellow,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Change Language Option Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(RetroPanel)
                        .border(1.dp, RetroBorder, RoundedCornerShape(6.dp))
                        .clickable { onChangeLanguage() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "◀ CHOOSE A DIFFERENT LANGUAGE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
