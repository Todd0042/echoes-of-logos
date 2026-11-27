package com.voicerpg.engine.ui.combat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.ui.fx.CombatantFx
import com.voicerpg.engine.ui.theme.HpCritical
import com.voicerpg.engine.ui.theme.HpGreen
import com.voicerpg.engine.ui.theme.HpWarning
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.MpBlue
import com.voicerpg.engine.viewmodel.CombatState

/**
 * The battle HUD: gauges live off the arena so the combatants stand clear on their backdrop.
 *
 * - The party's HP, MP and turn gauges are chips in the console's top row ([PartyChips]).
 * - A boss's health and ultimate charge run across the top of the arena ([BossBars]), over the sky.
 * - A regular enemy shows a thin health sliver under its name only while targeted, or for a moment after a
 *   hit or heal ([EnemySliver]).
 */
object BattleHud {

    data class PartyChip(
        val id: String,
        val name: String,
        val hp: Int,
        val maxHp: Int,
        val mp: Int,
        val maxMp: Int,
        val hpRatio: Float,
        val mpRatio: Float,
        val atbRatio: Float,
        val isReady: Boolean,
        val isActive: Boolean,
        val isFallen: Boolean
    )

    data class BossBar(
        val id: String,
        val name: String,
        val subtitle: String,
        val hp: Int,
        val maxHp: Int,
        val hpRatio: Float,
        /** 0..1 while the boss charges its ultimate, else null. */
        val ultimateCharge: Float?
    )

    /** How the party chips share their row: [compact] chips for big parties, [scrolls] only if even those don't fit. */
    data class ChipLayout(val compact: Boolean, val chipWidthDp: Float, val spacingDp: Float, val scrolls: Boolean)

    /** Parties this big use compact chips (short name, gauges only). */
    const val COMPACT_FROM = 5
    const val FULL_CHIP_MIN_DP = 64f
    const val FULL_CHIP_MAX_DP = 92f
    const val FULL_CHIP_SPACING_DP = 4f
    const val COMPACT_CHIP_MIN_DP = 34f
    const val COMPACT_CHIP_MAX_DP = 64f
    const val COMPACT_CHIP_SPACING_DP = 3f
    const val COMPACT_CHIP_PADDING_DP = 3f
    const val COMPACT_NAME_SP = 7f
    /** A compact chip's name is at most this many characters. */
    const val SHORT_NAME_CHARS = 6

    /** Width math for [count] chips in a row [rowWidthDp] wide. */
    fun chipLayout(count: Int, rowWidthDp: Float): ChipLayout {
        val compact = count >= COMPACT_FROM
        val (min, max, gap) = if (compact) Triple(COMPACT_CHIP_MIN_DP, COMPACT_CHIP_MAX_DP, COMPACT_CHIP_SPACING_DP)
        else Triple(FULL_CHIP_MIN_DP, FULL_CHIP_MAX_DP, FULL_CHIP_SPACING_DP)
        val n = count.coerceAtLeast(1)
        val share = (rowWidthDp - (n - 1) * gap) / n
        return ChipLayout(compact, share.coerceIn(min, max), gap, scrolls = share < min)
    }

    /**
     * A name short enough for a compact chip: its first word of four or more letters (a title like "Sir" is
     * skipped), else its first word, cut to [SHORT_NAME_CHARS].
     */
    fun shortName(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val word = words.firstOrNull { it.length > 3 } ?: words.firstOrNull() ?: return name
        return word.take(SHORT_NAME_CHARS)
    }

    /** About how wide [text] is in a compact chip's monospace name (0.6 em per character). */
    fun compactNameWidthDp(text: String): Float = text.length * COMPACT_NAME_SP * 0.6f

    /** At most this many boss bars stack on top (a third boss shares the field's slivers instead). */
    const val MAX_BOSS_BARS = 2

    fun partyChips(state: CombatState): List<PartyChip> = partyChips(state.party, state.activePartyMember?.id)

    fun partyChips(party: List<com.voicerpg.engine.model.PartyMember>, activeId: String?): List<PartyChip> {
        return party.map { m ->
            PartyChip(
                id = m.id, name = m.name, hp = m.currentHp, maxHp = m.maxHp, mp = m.currentMp, maxMp = m.maxMp,
                hpRatio = m.hpRatio, mpRatio = m.mpRatio, atbRatio = m.atbRatio, isReady = m.isTurnReady,
                isActive = m.id == activeId && m.isAlive, isFallen = !m.isAlive
            )
        }
    }

    fun bossBars(state: CombatState, fxOf: (String) -> CombatantFx): List<BossBar> =
        bosses(state).map { e ->
            BossBar(e.id, e.name, e.subtitle, e.currentHp, e.maxHp, e.hpRatio, fxOf(e.id).ultimateCharge)
        }

    private fun bosses(state: CombatState): List<Enemy> = state.enemies.filter { it.isBoss && it.isAlive }.take(MAX_BOSS_BARS)

    /** Whether [enemy] has a bar on top (then it never gets a sliver). */
    fun hasBossBar(state: CombatState, enemy: Enemy): Boolean = bosses(state).any { it.id == enemy.id }

    /** 0..1 opacity of a regular enemy's health sliver: full while targeted, else what a recent hit left. */
    fun sliverAlpha(enemy: Enemy, fx: CombatantFx?, hasBossBar: Boolean = enemy.isBoss): Float = when {
        hasBossBar || !enemy.isAlive -> 0f
        enemy.isTargeted -> 1f
        else -> fx?.vitals ?: 0f
    }

    fun hpColor(ratio: Float): Color = when {
        ratio > 0.5f -> HpGreen
        ratio > 0.2f -> HpWarning
        else -> HpCritical
    }
}

private val Track = Color(0xFF1E1E28)
private val UltimateRed = Color(0xFFFF1744)

@Composable
private fun Gauge(ratio: Float, color: Color, height: Dp, modifier: Modifier = Modifier, track: Color = Track) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color)
        )
    }
}

/**
 * One chip per hero, the acting hero outlined in gold; tapping a living hero selects them. Up to four: name, HP and
 * HP/MP/turn gauges, scrolling sideways if they don't fit. Five or six: compact chips sharing the row evenly (short
 * name, HP bar, MP bar for a hero with mana, turn gauge), so six fit a phone's row ([BattleHud.chipLayout]).
 */
@Composable
fun PartyChips(chips: List<BattleHud.PartyChip>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val layout = BattleHud.chipLayout(chips.size, maxWidth.value)
        val rowModifier = if (layout.scrolls || !layout.compact) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth()
        Row(modifier = rowModifier, horizontalArrangement = Arrangement.spacedBy(layout.spacingDp.dp)) {
            chips.forEach { chip ->
                val width = if (layout.compact && !layout.scrolls) Modifier.weight(1f).widthIn(max = BattleHud.COMPACT_CHIP_MAX_DP.dp)
                else if (layout.compact) Modifier.width(BattleHud.COMPACT_CHIP_MIN_DP.dp)
                else Modifier.widthIn(min = BattleHud.FULL_CHIP_MIN_DP.dp, max = BattleHud.FULL_CHIP_MAX_DP.dp)
                if (layout.compact) CompactChip(chip, onSelect, width) else FullChip(chip, onSelect, width)
            }
        }
    }
}

private fun chipBorder(chip: BattleHud.PartyChip): Color = when {
    chip.isActive -> LogosGold
    chip.isReady -> Color(0xFFFFB74D)
    else -> Color(0xFF37474F)
}

private fun Modifier.chipFrame(chip: BattleHud.PartyChip, onSelect: (String) -> Unit, hPad: Dp): Modifier = this
    .clip(RoundedCornerShape(5.dp))
    .background(if (chip.isActive) Color(0xFF2A2412) else Color(0xFF161A22))
    .border(1.dp, chipBorder(chip), RoundedCornerShape(5.dp))
    .clickable(enabled = !chip.isFallen) { onSelect(chip.id) }
    .padding(horizontal = hPad, vertical = 3.dp)
    .graphicsLayer { alpha = if (chip.isFallen) 0.55f else 1f }

@Composable
private fun ChipGauges(chip: BattleHud.PartyChip) {
    Spacer(Modifier.height(2.dp))
    Gauge(chip.hpRatio, BattleHud.hpColor(chip.hpRatio), 3.dp, Modifier.fillMaxWidth())
    if (chip.maxMp > 0) {
        Spacer(Modifier.height(1.dp))
        Gauge(chip.mpRatio, MpBlue, 2.dp, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(1.dp))
    Gauge(chip.atbRatio, if (chip.isReady) LogosGold else Color(0xFFFF9800), 2.dp, Modifier.fillMaxWidth())
}

@Composable
private fun FullChip(chip: BattleHud.PartyChip, onSelect: (String) -> Unit, modifier: Modifier) {
    Column(modifier = modifier.chipFrame(chip, onSelect, hPad = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (chip.isFallen) "💀 ${chip.name}" else chip.name,
                color = if (chip.isActive) LogosGold else Color(0xFFE0E0E0),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "${chip.hp}",
                color = BattleHud.hpColor(chip.hpRatio),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
        ChipGauges(chip)
    }
}

@Composable
private fun CompactChip(chip: BattleHud.PartyChip, onSelect: (String) -> Unit, modifier: Modifier) {
    Column(modifier = modifier.chipFrame(chip, onSelect, hPad = BattleHud.COMPACT_CHIP_PADDING_DP.dp)) {
        Text(
            text = BattleHud.shortName(chip.name),
            color = if (chip.isFallen) Color(0xFF90A4AE) else if (chip.isActive) LogosGold else Color(0xFFE0E0E0),
            fontSize = BattleHud.COMPACT_NAME_SP.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        ChipGauges(chip)
    }
}

/** Bosses' health and ultimate charge across the top of the arena (over the backdrop's sky). */
@Composable
fun BossBars(bars: List<BattleHud.BossBar>, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        bars.forEach { bar ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xB0000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bar.name.uppercase(),
                        color = Color(0xFFFFCDD2),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (bar.ultimateCharge != null) {
                        Text(
                            text = "☄ CHARGING",
                            color = UltimateRed,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = "${bar.hp}/${bar.maxHp}",
                        color = Color(0xFFD0D8DC),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(2.dp))
                Gauge(bar.hpRatio, BattleHud.hpColor(bar.hpRatio), 5.dp, Modifier.fillMaxWidth())
                if (bar.ultimateCharge != null) {
                    Spacer(Modifier.height(2.dp))
                    Gauge(bar.ultimateCharge, UltimateRed, 3.dp, Modifier.fillMaxWidth(), track = Color(0xFF3B0A0A))
                }
            }
        }
    }
}

/** A regular enemy's thin health sliver; [alpha] from [BattleHud.sliverAlpha]. Keeps its space when hidden. */
@Composable
fun EnemySliver(hpRatio: Float, alpha: Float, modifier: Modifier = Modifier) {
    Gauge(
        ratio = hpRatio,
        color = BattleHud.hpColor(hpRatio),
        height = 3.dp,
        modifier = modifier
            .width(40.dp)
            .graphicsLayer { this.alpha = alpha }
            .border(0.5.dp, Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.5.dp))
    )
}
