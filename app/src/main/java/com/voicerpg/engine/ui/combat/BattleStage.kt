package com.voicerpg.engine.ui.combat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
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
     * The y of the feet of combatant [index] of [count] in a diagonal column (0 = back of the formation), in arena
     * pixels: the column spreads from just below the horizon to the bottom; a lone combatant stands toward the front.
     */
    fun feetY(index: Int, count: Int, arenaHeight: Float, insetPx: Float): Float {
        val top = horizonY(arenaHeight)
        val bottom = floorBottomY(arenaHeight, insetPx).coerceAtLeast(top + 1f)
        val within = if (count <= 1) SOLO else index.toFloat() / (count - 1)
        val t = BACK_ROW + (1f - BACK_ROW) * within
        return top + (bottom - top) * t
    }

    /** Where painted battle art usually puts its horizon, for environments that don't say. */
    const val DEFAULT_ART_HORIZON = 0.55f

    /** The part of a backdrop picture to draw over the whole arena, in picture pixels. */
    data class Crop(val left: Float, val top: Float, val width: Float, val height: Float)

    /**
     * Crops a [srcW]x[srcH] picture whose painted horizon is at [artHorizon] (fraction of its height) to fill an
     * [arenaW]x[arenaH] arena with that horizon drawn on the stage [HORIZON]. The picture covers the arena (no
     * letterbox, no stretching), zooming in further when needed so both the sky above and the floor below the
     * horizon reach the arena's edges; it stays centered horizontally.
     */
    fun backdropCrop(srcW: Int, srcH: Int, artHorizon: Float, arenaW: Float, arenaH: Float): Crop {
        val hz = artHorizon.coerceIn(0.05f, 0.95f)
        val scale = maxOf(
            arenaW / srcW,
            arenaH / srcH,
            HORIZON * arenaH / (hz * srcH),                // enough sky above the horizon
            (1f - HORIZON) * arenaH / ((1f - hz) * srcH)   // enough floor below it
        )
        val width = arenaW / scale
        val height = arenaH / scale
        val top = (hz * srcH - HORIZON * height).coerceIn(0f, srcH - height)
        return Crop(left = (srcW - width) / 2f, top = top, width = width, height = height)
    }
}

/**
 * Lays one side's fighters out in formation ([BattleFormation]) over the whole flank. [fighter] draws fighter i
 * with its feet at the bottom centre of what it draws; it is placed there and scaled about its feet. [hitTarget]
 * is fighter i's tap target, laid over its body only, so a fighter partly behind another is still tapped where it
 * shows. Both are drawn in the formation's order: back rows first, the active fighter last.
 */
@Composable
fun StageFormation(
    figures: List<BattleFormation.Figure>,
    facingRight: Boolean,
    activeId: String?,
    frontOrder: List<String>?,
    modifier: Modifier = Modifier,
    fighter: @Composable (index: Int) -> Unit,
    hitTarget: @Composable (index: Int) -> Unit
) {
    val cache = remember { FormationCache() }
    val content = @Composable {
        figures.indices.forEach { i ->
            key(figures[i].id) {
                fighter(i)
                hitTarget(i)
            }
        }
    }
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val stage = BattleFormation.Stage.of(width.toFloat(), height.toFloat(), density, facingRight)
        val formation = cache.get(figures, stage, activeId, frontOrder)
        layout(width, height) {
            figures.indices.forEach { i ->
                val p = formation.placements[i]
                val f = figures[i]
                val view = measurables.getOrNull(2 * i)?.measure(Constraints())
                val hit = measurables.getOrNull(2 * i + 1)
                view?.placeWithLayer((p.x - view.width / 2f).roundToInt(), (p.feetY - view.height).roundToInt(), zIndex = 2f * p.z) {
                    scaleX = p.scale
                    scaleY = p.scale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                if (hit != null) {
                    val body = p.body(f)
                    val box = hit.measure(Constraints.fixed(body.width.roundToInt().coerceAtLeast(1), body.height.roundToInt().coerceAtLeast(1)))
                    box.place(body.left.roundToInt(), body.top.roundToInt(), zIndex = 2f * p.z + 1f)
                }
            }
        }
    }
}

/** Keeps the last formation, so it is only worked out again when the fighters, the flank or the active one change. */
private class FormationCache {
    private var key: List<Any?>? = null
    private var value: BattleFormation.Formation? = null

    fun get(figures: List<BattleFormation.Figure>, stage: BattleFormation.Stage, activeId: String?, frontOrder: List<String>?): BattleFormation.Formation {
        val k = listOf(figures, stage, activeId, frontOrder)
        value?.let { if (k == key) return it }
        return BattleFormation.place(figures, stage, activeId, frontOrder).also { key = k; value = it }
    }
}
