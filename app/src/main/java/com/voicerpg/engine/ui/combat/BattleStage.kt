package com.voicerpg.engine.ui.combat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The battle arena's stage: one floor shared by the backdrop and the combatants.
 *
 * The backdrop draws its floor from [HORIZON] (a fraction of the arena height) to the bottom edge;
 * every combatant is placed by its feet inside that band, the back of a formation just below the horizon
 * and the front at the bottom. The arena's height changes with the console below it (developer tools and
 * preset chants make it much shorter), so positions are fractions of the band, never fixed spacing:
 * whatever the height, nobody stands in the sky.
 */
object BattleStage {
    /** The horizon: where the backdrop's scenery meets the floor plane. */
    const val HORIZON = 0.43f

    /** How far into the floor the back row stands, as a fraction of the band. */
    private const val BACK_ROW = 0.22f

    /** A lone combatant stands toward the front, not on the edge. */
    private const val SOLO = 0.78f

    val FLOOR_INSET: Dp = 12.dp

    fun horizonY(arenaHeight: Float): Float = arenaHeight * HORIZON

    fun floorBottomY(arenaHeight: Float, insetPx: Float): Float = arenaHeight - insetPx

    /**
     * The y of the feet of combatant [index] of [count] (0 = back of the formation), in arena pixels.
     * [depth] narrows the column to part of the floor: a horde's back column stands deeper (0..0.75),
     * its front column nearer (0.25..1).
     */
    fun feetY(index: Int, count: Int, arenaHeight: Float, insetPx: Float, depth: ClosedFloatingPointRange<Float> = 0f..1f): Float {
        val top = horizonY(arenaHeight)
        val bottom = floorBottomY(arenaHeight, insetPx).coerceAtLeast(top + 1f)
        val within = if (count <= 1) SOLO else index.toFloat() / (count - 1)
        val d = depth.start + (depth.endInclusive - depth.start) * within
        val t = BACK_ROW + (1f - BACK_ROW) * d
        return top + (bottom - top) * t
    }

    val BACK_COLUMN = 0f..0.75f
    val FRONT_COLUMN = 0.25f..1f
}

/**
 * Lays its children out as one depth column of the formation: child i stands with its bottom edge (its
 * feet) at [BattleStage.feetY], horizontally aligned by [horizontalAlignment]. Later children draw in front.
 * Fills the arena's height; children may overlap like rows of a real formation.
 */
@Composable
fun StageColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    depth: ClosedFloatingPointRange<Float> = 0f..1f,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        // As wide as the widest combatant (a parent that forces a width, like a weighted flank, still wins).
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeables.sumOf { it.height }
        val inset = BattleStage.FLOOR_INSET.toPx()
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                val feet = BattleStage.feetY(i, placeables.size, height.toFloat(), inset, depth)
                val x = horizontalAlignment.align(p.width, width, layoutDirection)
                p.place(x, (feet - p.height).roundToInt())
            }
        }
    }
}
