package com.voicerpg.engine.ui.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.voicerpg.engine.content.CharacterRole
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.content.GameContentPack
import com.voicerpg.engine.content.SpriteContract
import com.voicerpg.engine.ui.environment.BattleEnvironmentCanvas
import com.voicerpg.engine.ui.sprites.AnimClip
import com.voicerpg.engine.ui.sprites.AnimTag
import com.voicerpg.engine.ui.sprites.SheetSprite
import com.voicerpg.engine.ui.sprites.SpriteCanvas
import com.voicerpg.engine.ui.sprites.SpriteLibrary
import com.voicerpg.engine.ui.sprites.SpriteSource
import com.voicerpg.engine.ui.sprites.sheetArtPx
import com.voicerpg.engine.ui.theme.LogosGold
import com.voicerpg.engine.ui.theme.RetroBorder
import com.voicerpg.engine.ui.theme.RetroDeepSlate

/**
 * The sprite lineup (gallery → SPRITES, docs/SPRITE_PLAN.md S4): every battle sprite the game uses, once each,
 * side by side on one floor at one pixel scale, all playing the same animation in step. It's where sprites are
 * judged for consistency (height, outline, palette, proportions, timing) before they ship.
 */
object SpriteLineup {

    enum class Group(val label: String) { PARTY("Party"), BOSS("Bosses"), ENEMY("Enemies") }

    data class Entry(
        val key: String,
        val label: String,
        val group: Group,
        /** Ids of the characters or enemy templates that use this sprite. */
        val usedBy: List<String>,
        val hasSheet: Boolean,
        /** What the sprite contract still wants (empty = meets it). */
        val problems: List<String>
    )

    /** The animations the lineup can play (party and boss contracts, then the optional ones). */
    val ANIMATIONS = listOf(
        AnimTag.IDLE, AnimTag.READY, AnimTag.CHARGE, AnimTag.CAST, AnimTag.ATTACK, AnimTag.HURT, AnimTag.KO,
        AnimTag.VICTORY, AnimTag.WINDUP, AnimTag.CHARGE_ULTIMATE, AnimTag.ULTIMATE, AnimTag.DEATH, AnimTag.WEAK,
        AnimTag.GUARD, AnimTag.TAUNT
    )

    fun entries(pack: GameContentPack): List<Entry> {
        val problems = SpriteContract.check(pack).violations.groupBy { it.sprite }
        fun problemsOf(key: String) = problems[key].orEmpty().map { it.problem }.distinct()

        val party = pack.characters.values
            .filter { it.combat != null && it.role != CharacterRole.NARRATOR }
            .sortedBy { it.id }
            .map { c -> Entry(c.id, c.speaker.name, Group.PARTY, listOf(c.id), drawn(pack, c.id), problemsOf(c.id)) }

        val enemies = pack.enemyTemplates.values
            .groupBy { it.spriteId.ifBlank { it.id } }
            .map { (key, users) ->
                val sorted = users.sortedBy { it.id }
                val boss = sorted.firstOrNull { it.isBoss }
                Entry(
                    key = key,
                    label = (boss ?: sorted.first()).name,
                    group = if (boss != null) Group.BOSS else Group.ENEMY,
                    usedBy = sorted.map { it.id },
                    hasSheet = drawn(pack, key),
                    problems = problemsOf(key)
                )
            }
        return party + enemies.filter { it.group == Group.BOSS }.sortedBy { it.label } +
            enemies.filter { it.group == Group.ENEMY }.sortedBy { it.label }
    }

    /** A real sheet (or a variant of one), not a placeholder standing in for art on the way. */
    private fun drawn(pack: GameContentPack, key: String) = pack.resolveSheet(key)?.isPlaceholder == false

    fun filter(entries: List<Entry>, group: Group?): List<Entry> = if (group == null) entries else entries.filter { it.group == group }

    /** The frame of [clip] at [ms] on the lineup's shared clock; every clip loops, so one-shots repeat. */
    fun frameAt(clip: AnimClip, ms: Float): Int {
        val total = clip.totalMs
        if (total <= 0 || clip.frames.isEmpty()) return clip.frames.firstOrNull() ?: 0
        var t = ms % total
        for (i in clip.frames.indices) {
            t -= clip.durationsMs[i]
            if (t < 0f) return clip.frames[i]
        }
        return clip.frames.last()
    }
}

private val Chip = Color(0xFF1C2230)

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) LogosGold else Color(0xFFCFD8DC),
        fontSize = 10.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) LogosGold.copy(alpha = 0.15f) else Chip)
            .border(1.dp, if (selected) LogosGold else RetroBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

/** A shared animation clock in milliseconds (every sprite in the lineup reads the same one, so they play in step). */
@Composable
private fun lineupClock(restartKey: Any): Float {
    val ms by produceState(0f, restartKey) {
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now
                value = (now - start) / 1_000_000f
            }
        }
    }
    return ms
}

@Composable
fun SpriteLineupTab(modifier: Modifier = Modifier) {
    val entries = remember { SpriteLineup.entries(GameContent.pack) }
    var group by remember { mutableStateOf<SpriteLineup.Group?>(null) }
    var tag by remember { mutableStateOf(AnimTag.IDLE) }
    var backdrop by remember { mutableStateOf(false) }
    var guides by remember { mutableStateOf(true) }
    var asInBattle by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(1) }
    var inspect by remember { mutableStateOf<SpriteLineup.Entry?>(null) }
    val shown = SpriteLineup.filter(entries, group)
    val clock = lineupClock(tag)
    val drawn = entries.count { it.hasSheet }
    val passing = entries.count { it.problems.isEmpty() }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "${entries.size} sprites · $drawn drawn · $passing meet the contract",
            color = if (passing == entries.size) Color(0xFF69F0AE) else Color(0xFFFFB74D),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceChip("All", group == null) { group = null }
            SpriteLineup.Group.entries.forEach { g -> ChoiceChip(g.label, group == g) { group = g } }
            Spacer(Modifier.width(8.dp))
            ChoiceChip(if (backdrop) "Backdrop" else "Plain", backdrop) { backdrop = !backdrop }
            ChoiceChip("Guides", guides) { guides = !guides }
            ChoiceChip(if (asInBattle) "Facing foes" else "All face right", asInBattle) { asInBattle = !asInBattle }
            ChoiceChip("x$zoom", zoom > 1) { zoom = if (zoom >= 3) 1 else zoom + 1 }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SpriteLineup.ANIMATIONS.forEach { t -> ChoiceChip(t.key, tag == t) { tag = t } }
        }
        Spacer(Modifier.height(8.dp))

        val artPx = sheetArtPx(sizeFactor = zoom.toFloat())
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, RetroBorder, RoundedCornerShape(8.dp))
                .background(Color(0xFF5A5F66))
        ) {
            if (backdrop) {
                val env = GameContent.environments.keys.firstOrNull()
                BattleEnvironmentCanvas(environmentId = env, modifier = Modifier.fillMaxSize())
            }
            val floorFromBottom = 46.dp
            if (guides) {
                // Height guides every 16 art pixels above the floor, so heights compare at a glance.
                Canvas(Modifier.fillMaxSize()) {
                    val floorY = size.height - floorFromBottom.toPx()
                    drawLine(Color(0xCCFFFFFF), Offset(0f, floorY), Offset(size.width, floorY), strokeWidth = 2f)
                    var h = 16
                    while (floorY - h * artPx > 0f) {
                        val y = floorY - h * artPx
                        drawLine(
                            Color(if (h % 64 == 0) 0x99FFD700.toInt() else 0x55FFFFFF), Offset(0f, y), Offset(size.width, y),
                            strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                        h += 16
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                shown.forEach { e ->
                    val source = remember(e.key) { SpriteLibrary.source(e.key) }
                    val clip = remember(e.key, tag) { source.clip(tag) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { inspect = e }
                    ) {
                        SpriteCanvas(
                            source = source,
                            frame = SpriteLineup.frameAt(clip, clock),
                            artPx = artPx,
                            flipped = asInBattle && e.group != SpriteLineup.Group.PARTY
                        )
                        // Exactly the floor's distance from the bottom, so every pair of feet is on the line.
                        Column(
                            modifier = Modifier.height(floorFromBottom).padding(top = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                        Text(
                            text = e.label,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 90.dp)
                        )
                        Text(
                            text = when {
                                e.problems.isEmpty() -> "✓ " + clip.name
                                !e.hasSheet && e.problems.any { it.startsWith("no sprite sheet yet") } -> "… art on the way"
                                !e.hasSheet -> "✗ text grid"
                                else -> "✗ ${e.problems.size} · ${clip.name}"
                            },
                            color = if (e.problems.isEmpty()) Color(0xFF69F0AE) else Color(0xFFFF8A80),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (e.usedBy.size > 1) "${e.usedBy.size} users" else " ",
                            color = Color(0xFFB0BEC5),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        }
                    }
                }
            }
        }
    }

    inspect?.let { e -> SpriteInspectDialog(e, onDismiss = { inspect = null }) }
}

/** One sprite up close: every animation it really has, its cell and feet, who uses it, and what the contract wants. */
@Composable
private fun SpriteInspectDialog(entry: SpriteLineup.Entry, onDismiss: () -> Unit) {
    val source: SpriteSource = remember(entry.key) { SpriteLibrary.source(entry.key) }
    val names = source.animationNames.sorted()
    var playing by remember { mutableStateOf(names.firstOrNull { it == "idle" } ?: names.firstOrNull() ?: "idle") }
    val clip = remember(playing) { source.clipNamed(playing, loops = true) ?: source.clip(AnimTag.IDLE) }
    val clock = lineupClock(playing)
    val artPx = sheetArtPx(sizeFactor = 3f)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(RetroDeepSlate)
                .border(1.dp, LogosGold, RoundedCornerShape(12.dp))
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(entry.label, color = LogosGold, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(
                text = "${entry.key} · ${source.cellW}x${source.cellH}" + (if (source is SheetSprite) " sheet" else " text grid") +
                    " · feet ${source.feet.first},${source.feet.second}",
                color = Color(0xFFB0BEC5), fontSize = 9.sp, fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF5A5F66)).padding(12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                SpriteCanvas(source = source, frame = SpriteLineup.frameAt(clip, clock), artPx = artPx, flipped = false)
                // The feet point: what stands on the floor line in battle.
                Canvas(Modifier.width(12.dp).height(12.dp)) {
                    drawLine(Color.Red, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 3f)
                    drawLine(Color.Red, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 3f)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                names.forEach { n -> ChoiceChip(n, n == playing) { playing = n } }
            }
            val events = clip.events.entries.joinToString { "${it.key}=${it.value + 1}" }
            Text(
                text = "${clip.name}: ${clip.frames.size} frames, ${clip.totalMs} ms" + (if (events.isNotEmpty()) " · $events" else ""),
                color = Color(0xFFCFD8DC), fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = "Used by: " + entry.usedBy.joinToString(),
                color = Color(0xFFB0BEC5), fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (entry.problems.isNotEmpty()) {
                Text(
                    text = "Contract:\n" + entry.problems.joinToString("\n") { "✗ $it" },
                    color = Color(0xFFFF8A80), fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                Text("✓ Meets the sprite contract", color = Color(0xFF69F0AE), fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
