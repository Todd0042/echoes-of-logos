package com.voicerpg.engine.ui.story

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.voicerpg.engine.engine.QuestMilestone
import com.voicerpg.engine.engine.QuestRecap
import com.voicerpg.engine.model.DialogueLogEntry
import com.voicerpg.engine.ui.theme.FrostCyan
import com.voicerpg.engine.ui.theme.HolyYellow
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.RetroBlack
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroBorderGold
import com.voicerpg.engine.ui.theme.RetroPanel

/**
 * Fullscreen retro dialog offering:
 * 1. Pokémon FireRed / LeafGreen style "Previously on your quest..." narrative story recap.
 * 2. Verbatim line-by-line dialogue history backlog with individual speech replay.
 */
@Composable
fun DialogueBacklogDialog(
    isOpen: Boolean,
    history: List<DialogueLogEntry>,
    questRecap: QuestRecap,
    isRecapActive: Boolean,
    onTabSelect: (Boolean) -> Unit,
    onClose: () -> Unit,
    onReplay: (DialogueLogEntry) -> Unit,
    onListenRecap: (String) -> Unit
) {
    if (!isOpen) return

    val dialogueListState = rememberLazyListState()
    val recapListState = rememberLazyListState()

    // Auto-scroll dialogue to the bottom (most recent lines) on open / update
    LaunchedEffect(isOpen, isRecapActive, history.size) {
        if (!isRecapActive && history.isNotEmpty()) {
            dialogueListState.animateScrollToItem(history.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(14.dp))
                .background(RetroBlack.copy(alpha = 0.97f))
                .border(2.dp, RetroBorderGold, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📜 CHRONICLE & BACKLOG",
                        color = LogosGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )

                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(RetroPanel)
                            .border(1.dp, RetroBorder, CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dual Tab Switcher: [📖 STORY RECAP] vs [💬 DIALOGUE LOG]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tab 1: Story Recap
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isRecapActive) LogosGold.copy(alpha = 0.22f) else RetroPanel)
                            .border(1.dp, if (isRecapActive) LogosGold else RetroBorder, RoundedCornerShape(6.dp))
                            .clickable { onTabSelect(true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📖 STORY RECAP",
                            color = if (isRecapActive) LogosGold else Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Tab 2: Dialogue Log
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isRecapActive) LogosGold.copy(alpha = 0.22f) else RetroPanel)
                            .border(1.dp, if (!isRecapActive) LogosGold else RetroBorder, RoundedCornerShape(6.dp))
                            .clickable { onTabSelect(false) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "💬 DIALOGUE LOG (${history.size})",
                            color = if (!isRecapActive) LogosGold else Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Body Content Based on Active Tab
                if (isRecapActive) {
                    // ================= TAB 1: STORY RECAP (Pokémon FRLG Style) =================
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Current Situation Overview Card (Compact, clean badges, no ballooning)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC1A1C29))
                                .border(1.dp, HolyYellow.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Row 1: Act Tag + Chapter Title (flexible width) + Spoken Recap Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Act pill badge (e.g. "ACT IV", "ACT I", "PROLOGUE")
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(LogosGold.copy(alpha = 0.22f))
                                                .border(1.dp, LogosGold, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = parseActBadge(questRecap.currentActTitle),
                                                color = LogosGold,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1
                                            )
                                        }

                                        Text(
                                            text = questRecap.currentChapterTitle.uppercase(),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Spoken Recap Narration Button
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0x44FFD700))
                                            .border(1.dp, LogosGold, RoundedCornerShape(4.dp))
                                            .clickable { onListenRecap(questRecap.spokenRecap) }
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "🔊 READ RECAP",
                                            color = LogosGold,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Text(
                                    text = "📍 Location: ${questRecap.currentSceneName}",
                                    color = Color.LightGray,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = "🛡️ Fellowship: " + questRecap.fellowshipRoster.joinToString(" • "),
                                    color = FrostCyan,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 12.sp
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x333F51B5))
                                        .border(1.dp, Color(0xFF5C6BC0), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "🎯 OBJECTIVE: ${questRecap.activeObjective}",
                                        color = Color(0xFFC5CAE9),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 12.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "PREVIOUSLY ON YOUR QUEST:",
                            color = Color.Gray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Scrollable Milestone Timeline
                        LazyColumn(
                            state = recapListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(questRecap.milestones) { milestone ->
                                QuestMilestoneItem(milestone = milestone)
                            }
                        }
                    }
                } else {
                    // ================= TAB 2: DIALOGUE LOG (Verbatim) =================
                    if (history.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No dialogue recorded yet in this chronicle.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        LazyColumn(
                            state = dialogueListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(history) { entry ->
                                DialogueBacklogItem(
                                    entry = entry,
                                    onReplay = { onReplay(entry) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRecapActive) "💡 Say 'Recap' to hear quest summary" else "💡 Tap 🔊 to replay speech • Say 'Close' hands-free",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF37474F))
                            .border(1.dp, Color(0xFF78909C), RoundedCornerShape(6.dp))
                            .clickable { onClose() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "CLOSE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

/**
 * Episodic recap card representing one completed or active chapter milestone.
 */
@Composable
private fun QuestMilestoneItem(
    milestone: QuestMilestone
) {
    val borderColor = if (milestone.isCurrent) LogosGold else RetroBorder
    val backgroundColor = if (milestone.isCurrent) Color(0xCC201C12) else RetroPanel.copy(alpha = 0.90f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Milestone Header: Chapter Title & Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = milestone.icon, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = milestone.title,
                        color = if (milestone.isCurrent) LogosGold else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (milestone.isCurrent) Color(0x33FFD700) else Color(0x334CAF50))
                        .border(1.dp, if (milestone.isCurrent) LogosGold else Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (milestone.isCurrent) "⏳ CURRENT" else "✓ COMPLETED",
                        color = if (milestone.isCurrent) LogosGold else Color(0xFF81C784),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }

            Text(
                text = "📍 ${milestone.location}",
                color = Color.Gray,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = milestone.summary,
                color = Color.LightGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun DialogueBacklogItem(
    entry: DialogueLogEntry,
    onReplay: () -> Unit
) {
    val context = LocalContext.current
    val speakerThemeColor = entry.speakerThemeColor()
    val portraitAsset = entry.speakerPortraitAsset()
    val portraitBitmap = remember(portraitAsset) {
        portraitAsset?.let { StoryAssetLoader.loadBitmap(context, it) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RetroPanel.copy(alpha = 0.90f))
            .border(1.dp, RetroBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Speaker Header + Location + Replay Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (portraitBitmap != null) {
                        Image(
                            bitmap = portraitBitmap,
                            contentDescription = entry.speakerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(1.dp, speakerThemeColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = entry.speakerName.uppercase(),
                        color = speakerThemeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "• ${entry.sceneName}",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Voice Replay Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x33FFD700))
                        .border(1.dp, LogosGold.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .clickable { onReplay() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔊 REPLAY",
                        color = LogosGold,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Dialogue Text
            Text(
                text = entry.text,
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp
            )
        }
    }
}

/**
 * Extracts a concise badge label from an act title (e.g. "Act IV: The Primordial Syllable" -> "ACT IV").
 */
private fun parseActBadge(actTitle: String): String {
    val trimmed = actTitle.trim()
    val colonIdx = trimmed.indexOf(':')
    return if (colonIdx > 0) {
        trimmed.substring(0, colonIdx).trim().uppercase()
    } else {
        trimmed.uppercase()
    }
}

