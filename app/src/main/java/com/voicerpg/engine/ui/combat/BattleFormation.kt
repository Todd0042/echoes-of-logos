package com.voicerpg.engine.ui.combat

import androidx.compose.ui.geometry.Rect
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.ui.sprites.GridSprite
import com.voicerpg.engine.ui.sprites.SheetSprite
import com.voicerpg.engine.ui.sprites.SpriteLibrary
import com.voicerpg.engine.ui.sprites.SpriteSizing
import com.voicerpg.engine.ui.sprites.SpriteSource
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where each fighter of one side stands on the battle stage (docs/COMBAT_PROGRESSION_PLAN.md §3.7). A pure
 * function of the fighters' drawn shapes and the flank's size, so it is tested on every game's real sprites.
 *
 * - **No shrinking:** sprites keep the size a lone fighter has; deeper rows are only [BACK_ROW_SCALE] smaller
 *   (perspective). Only when the arena is too small for the rules below does everyone shrink, in steps, never
 *   below [MIN_SCALE] ([Formation.fits] is false if even that isn't enough).
 * - **Depth rows** ([rowSizes]): 1-3 fighters stand in a diagonal (one per row, the classic look); 4 = 2+2,
 *   5 = 2+2+1, 6 = 2+2+2. Each row stands half a slot further back than the one in front, so the heads behind
 *   show between the fighters in front. Rows spread across the flank's width; feet stay on the floor band.
 * - **Never hidden:** every fighter's head is clear of every fighter drawn after it, even while that one steps
 *   forward on its turn ([Stage.activeStep], [Stage.readyStep]) or flinches back; at least [MIN_BODY_VISIBLE] of
 *   each body shows.
 * - **Drawn back to front**, the active fighter last (on top). Its step forward is the sprite view's own.
 *
 * Coordinates are the flank's pixels; a [Figure] is relative to its feet (x right, y down, feet at 0,0).
 */
object BattleFormation {

    /** A party sprite's size: dp per art pixel of a legacy grid (sheets scale from it, see [artPx]). */
    const val PARTY_PIXEL_DP = 2.0f

    /** An enemy's, before its own `scale`. */
    const val ENEMY_PIXEL_DP = 2.2f

    /** The deepest row's size relative to the front row (slight perspective). */
    const val BACK_ROW_SCALE = 0.92f

    /** The last-resort shrink never goes below this (1.4 dp per art pixel for the party). */
    const val MIN_SCALE = 0.7f

    /** At least this share of each body is not covered by fighters drawn in front of it. */
    const val MIN_BODY_VISIBLE = 0.5f

    /** Without a `head` slice, the head is this top share of the body. */
    private const val HEAD_SHARE = 0.3f

    private val SCALES = listOf(1f, 0.95f, 0.9f, 0.85f, 0.8f, 0.75f, MIN_SCALE)

    /** A fighter's drawn body and head at full size, relative to its feet, as seen on screen (mirrored if flipped). */
    data class Figure(val id: String, val body: Rect, val head: Rect)

    /** One flank of the arena: its size, the floor band, and the distances fighters move on their own. */
    data class Stage(
        val width: Float,
        val height: Float,
        val floorInset: Float,
        /** Room kept between the formation and the flank's outer edge, and its inner edge. */
        val edgePad: Float,
        /** How far a ready (or flinching) fighter's sprite moves sideways by itself. */
        val readyStep: Float,
        /** How far the active fighter's sprite moves forward by itself (casting). */
        val activeStep: Float,
        /** The diagonal's sideways step between fighters for 1-3 (today's look). */
        val diagonalStep: Float,
        /** The side faces right (the party) or left (enemies): "forward" is toward the other side. */
        val facingRight: Boolean
    ) {
        val horizon: Float get() = BattleStage.horizonY(height)
        val floorBottom: Float get() = BattleStage.floorBottomY(height, floorInset).coerceAtLeast(horizon + 1f)

        companion object {
            /** The stage for a flank of [widthPx] x [heightPx], with the sprite view's step distances. */
            fun of(widthPx: Float, heightPx: Float, density: Float, facingRight: Boolean) = Stage(
                width = widthPx,
                height = heightPx,
                floorInset = BattleStage.FLOOR_INSET.value * density,
                edgePad = 10f * density,
                readyStep = 8f * density,
                activeStep = 16f * density,
                diagonalStep = 10f * density,
                facingRight = facingRight
            )
        }
    }

    /**
     * Where one fighter stands: its feet at ([x], [feetY]), drawn at [scale] (about the feet), in depth [row]
     * (0 = front), [z] its place in the drawing order (higher draws later, on top).
     */
    data class Placement(val id: String, val x: Float, val feetY: Float, val scale: Float, val row: Int, val z: Int) {
        fun body(f: Figure): Rect = at(f.body)
        fun head(f: Figure): Rect = at(f.head)
        private fun at(r: Rect) = Rect(x + r.left * scale, feetY + r.top * scale, x + r.right * scale, feetY + r.bottom * scale)
    }

    /** Placements in the order the fighters were given; [scale] the overall (last-resort) shrink. */
    data class Formation(val placements: List<Placement>, val scale: Float, val fits: Boolean) {
        fun of(id: String): Placement? = placements.firstOrNull { it.id == id }
    }

    /** Fighters per depth row, front row first. */
    fun rowSizes(count: Int): List<Int> {
        if (count <= 0) return emptyList()
        if (count <= 3) return List(count) { 1 }
        val rows = if (count == 4) 2 else 3
        return List(rows) { r -> count / rows + if (r < count % rows) 1 else 0 }
    }

    // ---- Default front rows (today's look until the caller supplies positions) ----

    /** The party's front-first order: 1-3 keep the diagonal's order (the first at the back), 4+ the first in front. */
    fun partyFrontOrder(party: List<PartyMember>): List<String> =
        (if (party.size <= 3) party.reversed() else party).map { it.id }

    /** Enemies' front-first order, as the party's; bosses stand at the back (they would hide a whole row). */
    fun enemyFrontOrder(enemies: List<Enemy>): List<String> {
        val ordered = if (enemies.size <= 3) enemies.reversed() else enemies
        return (ordered.filterNot { it.isBoss } + ordered.filter { it.isBoss }).map { it.id }
    }

    // ---- Figures from sprites ----

    /**
     * Device pixels per art pixel, as the sprite view draws it (PixelCharacterView / sheetArtPx): a sheet at about
     * 1.25 dp per pixel for [pixelSizeDp] 2.2, rounded to whole pixels; a grid at [pixelSizeDp].
     */
    fun artPx(source: SpriteSource, pixelSizeDp: Float, density: Float): Float = when (source) {
        is SheetSprite -> (1.25f * density * (pixelSizeDp / 2.2f) * source.scale).roundToInt().coerceAtLeast(1).toFloat()
        is GridSprite -> pixelSizeDp * density
    }

    fun figureOf(member: PartyMember, density: Float): Figure =
        figureOf(member.id, SpriteLibrary.source(SpriteLibrary.keyFor(member)), PARTY_PIXEL_DP, density, flipped = false)

    fun figureOf(enemy: Enemy, density: Float): Figure {
        val source = SpriteLibrary.source(SpriteLibrary.keyFor(enemy))
        return figureOf(enemy.id, source, ENEMY_PIXEL_DP * SpriteSizing.enemyFactor(enemy, source), density, flipped = true)
    }

    /**
     * The body and head of [source] drawn at [pixelSizeDp]. A sheet's body spans its `head` and `feet` slices (the
     * middle half of the cell without them) from the head's top to the feet; its head is the `head` slice, else the
     * top [HEAD_SHARE] of the body. A grid's body is its upright frame's painted area.
     */
    fun figureOf(id: String, source: SpriteSource, pixelSizeDp: Float, density: Float, flipped: Boolean): Figure {
        val a = artPx(source, pixelSizeDp, density)
        val (fx, fy) = source.feet
        var body: Rect
        var head: Rect? = null
        when (source) {
            is SheetSprite -> {
                val slices = source.sheet.slices
                val h = slices["head"]
                val feet = slices["feet"]
                val xs = listOfNotNull(h?.let { it.x to it.x + it.w }, feet?.let { it.x to it.x + it.w })
                val left = xs.minOfOrNull { it.first }?.toFloat() ?: source.cellW * 0.25f
                val right = xs.maxOfOrNull { it.second }?.toFloat() ?: source.cellW * 0.75f
                body = Rect(left, (h?.y ?: 0).toFloat(), right, fy.toFloat())
                if (h != null) head = Rect(h.x.toFloat(), h.y.toFloat(), (h.x + h.w).toFloat(), (h.y + h.h).toFloat())
            }
            is GridSprite -> {
                val rows = source.definition.idleUpright
                val painted = rows.flatMapIndexed { y, line -> line.mapIndexedNotNull { x, c -> if (c != '.') x to y else null } }
                body = if (painted.isEmpty()) Rect(0f, 0f, source.cellW.toFloat(), fy.toFloat())
                else Rect(painted.minOf { it.first }.toFloat(), painted.minOf { it.second }.toFloat(), painted.maxOf { it.first } + 1f, fy.toFloat())
            }
        }
        if (body.bottom <= body.top) body = Rect(body.left, body.bottom - 1f, body.right, body.bottom)
        val headRect = head ?: Rect(body.left, body.top, body.right, body.top + body.height * HEAD_SHARE)
        fun toFeet(r: Rect): Rect {
            val rel = Rect((r.left - fx) * a, (r.top - fy) * a, (r.right - fx) * a, (r.bottom - fy) * a)
            return if (flipped) Rect(-rel.right, rel.top, -rel.left, rel.bottom) else rel
        }
        return Figure(id, toFeet(body), toFeet(headRect))
    }

    // ---- Placement ----

    /**
     * Places [figures] on [stage]. [frontOrder] lists fighter ids front first (those not listed follow in the given
     * order); by default the first fighters stand in front. [activeId] is drawn last.
     */
    fun place(figures: List<Figure>, stage: Stage, activeId: String? = null, frontOrder: List<String>? = null): Formation {
        if (figures.isEmpty()) return Formation(emptyList(), 1f, fits = true)
        // Work facing right ("forward" = +x); mirror back at the end.
        val fwd = if (stage.facingRight) figures else figures.map { it.copy(body = mirror(it.body), head = mirror(it.head)) }
        val rows = assignRows(figures, frontOrder)
        val active = activeId?.let { id -> figures.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        val order = drawOrder(rows, active)

        var best: Pair<Int, Layout>? = null
        for (scale in SCALES) {
            for (layout in candidates(fwd, rows, stage, scale)) {
                val faults = faults(fwd, layout, order, active, stage)
                if (faults == 0) return layout.toFormation(figures, rows, order, stage, scale, fits = true)
                if (scale == MIN_SCALE && (best == null || faults < best.first)) best = faults to layout
            }
        }
        return best!!.second.toFormation(figures, rows, order, stage, MIN_SCALE, fits = false)
    }

    private fun mirror(r: Rect) = Rect(-r.right, r.top, -r.left, r.bottom)

    /** Fighter indices per row, front row first; within a row the first stands most forward. */
    private fun assignRows(figures: List<Figure>, frontOrder: List<String>?): List<List<Int>> {
        val priority = mutableListOf<Int>()
        frontOrder.orEmpty().forEach { id ->
            figures.indices.firstOrNull { figures[it].id == id && it !in priority }?.let { priority += it }
        }
        figures.indices.forEach { if (it !in priority) priority += it }
        var next = 0
        return rowSizes(figures.size).map { size -> priority.subList(next, next + size).toList().also { next += size } }
    }

    /** Back row first, within a row the rearmost first; the active fighter last. */
    private fun drawOrder(rows: List<List<Int>>, active: Int?): List<Int> {
        val order = rows.indices.reversed().flatMap { r -> rows[r].reversed() }
        return if (active == null) order else order - active + active
    }

    /** A candidate: feet positions in forward coordinates, scales per fighter. */
    private class Layout(val x: FloatArray, val feetY: FloatArray, val scale: FloatArray) {
        fun body(f: Figure, i: Int) = Rect(x[i] + f.body.left * scale[i], feetY[i] + f.body.top * scale[i], x[i] + f.body.right * scale[i], feetY[i] + f.body.bottom * scale[i])
        fun head(f: Figure, i: Int) = Rect(x[i] + f.head.left * scale[i], feetY[i] + f.head.top * scale[i], x[i] + f.head.right * scale[i], feetY[i] + f.head.bottom * scale[i])

        fun toFormation(figures: List<Figure>, rows: List<List<Int>>, order: List<Int>, stage: Stage, overall: Float, fits: Boolean): Formation {
            val rowOf = IntArray(figures.size).also { a -> rows.forEachIndexed { r, members -> members.forEach { a[it] = r } } }
            val zOf = IntArray(figures.size).also { a -> order.forEachIndexed { z, i -> a[i] = z } }
            return Formation(figures.indices.map { i ->
                Placement(figures[i].id, if (stage.facingRight) x[i] else stage.width - x[i], feetY[i], scale[i], rowOf[i], zOf[i])
            }, overall, fits)
        }
    }

    /**
     * Candidate layouts at [scale], most preferred first. 1-3: today's diagonal (rows spread over the floor, a
     * small sideways step), widening the step, then using more of the floor, when heads would be covered. 4+: the
     * widest in-row spacing that fits the flank, rows as close as the heads allow (about half a body apart).
     */
    private fun candidates(figures: List<Figure>, rows: List<List<Int>>, stage: Stage, scale: Float): Sequence<Layout> = sequence {
        val n = figures.size
        val rowCount = rows.size
        val top = stage.horizon
        val bottom = stage.floorBottom
        val band = bottom - top
        val rowScale = FloatArray(rowCount) { r -> scale * if (rowCount <= 1) 1f else 1f - (1f - BACK_ROW_SCALE) * r / (rowCount - 1) }
        val wMax = figures.maxOf { it.body.width } * scale
        val hMax = figures.maxOf { it.body.height } * scale

        fun build(rowGap: Float, rowShift: Float, slot: Float, solo: Boolean): Layout {
            val x = FloatArray(n)
            val y = FloatArray(n)
            val s = FloatArray(n)
            rows.forEachIndexed { r, members ->
                val feet = if (solo) BattleStage.feetY(0, 1, stage.height, stage.floorInset) else bottom - r * rowGap
                members.forEachIndexed { j, i ->
                    x[i] = -(r * rowShift + j * slot)
                    y[i] = feet
                    s[i] = rowScale[r]
                }
            }
            // The back edge (with room to flinch back) on the flank's outer edge.
            val l = Layout(x, y, s)
            val shift = stage.edgePad + stage.readyStep - figures.indices.minOf { l.body(figures[it], it).left }
            for (i in 0 until n) x[i] += shift
            return l
        }

        if (n <= 3) {
            if (rowCount == 1) { yield(build(0f, 0f, 0f, solo = true)); return@sequence }
            val today = (bottom - BattleStage.feetY(0, rowCount, stage.height, stage.floorInset)) / (rowCount - 1)
            val most = band / (rowCount - 1)
            val gaps = (listOf(today) + steps(today, most, 6) + steps(today, today * 0.4f, 6)).distinct()
            val shifts = listOf(stage.diagonalStep) + (1..24).map { stage.diagonalStep + it * 0.1f * wMax }
            for (shift in shifts) for (gap in gaps) yield(build(gap, shift, 0f, solo = false))
            return@sequence
        }

        val most = band / (rowCount - 1)
        val hi = min(most, 1.25f * hMax)
        val lo = min(0.25f * hMax, hi)
        val pref = (0.5f * hMax).coerceIn(lo, hi)
        val gaps = (listOf(pref) + steps(pref, hi, 12) + steps(pref, lo, 6)).distinct()
        for (k in 0..24) {
            val slot = wMax * (1.7f - k * 0.05f)
            for (gap in gaps) yield(build(gap, slot / 2f, slot, solo = false))
        }
    }

    /** [count] evenly spaced values from just past [from] to [to]. */
    private fun steps(from: Float, to: Float, count: Int): List<Float> = (1..count).map { from + (to - from) * it / count }

    /** How badly [l] breaks the rules (0 = not at all): off the stage, heads covered, bodies hidden. */
    private fun faults(figures: List<Figure>, l: Layout, order: List<Int>, active: Int?, stage: Stage): Int {
        var faults = 0
        val bodies = figures.indices.map { l.body(figures[it], it) }
        for (i in figures.indices) {
            val b = bodies[i]
            if (b.top < 0f) faults += 1000
            if (l.feetY[i] < stage.horizon - 0.5f) faults += 1000
            val fwd = if (i == active) stage.activeStep else stage.readyStep
            if (b.right + fwd > stage.width - stage.edgePad + 0.01f) faults += 1000
        }
        for ((zi, i) in order.withIndex()) {
            val head = l.head(figures[i], i)
            for (zj in zi + 1 until order.size) {
                val j = order[zj]
                val b = bodies[j]
                val fwd = if (j == active) stage.activeStep else stage.readyStep
                val reach = Rect(b.left - stage.readyStep, b.top, b.right + fwd, b.bottom)
                if (overlap(head, reach) > 0.25f) faults += 100
            }
        }
        if (faults > 0) return faults
        for ((zi, i) in order.withIndex()) {
            val covers = order.subList(zi + 1, order.size).map { bodies[it] }
            if (visibleShare(bodies[i], covers) < MIN_BODY_VISIBLE) faults += 10
        }
        return faults
    }

    private fun overlap(a: Rect, b: Rect): Float {
        val w = min(a.right, b.right) - max(a.left, b.left)
        val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        return if (w > 0f && h > 0f) w * h else 0f
    }

    /** The share of [r] not under any of [covers] (exact, over the grid their edges make). */
    private fun visibleShare(r: Rect, covers: List<Rect>): Float {
        val area = r.width * r.height
        if (area <= 0f) return 1f
        val clipped = covers.mapNotNull { c -> r.intersect(c).takeIf { it.width > 0f && it.height > 0f } }
        if (clipped.isEmpty()) return 1f
        val xs = (clipped.flatMap { listOf(it.left, it.right) }).distinct().sorted()
        val ys = (clipped.flatMap { listOf(it.top, it.bottom) }).distinct().sorted()
        var covered = 0f
        for (a in 0 until xs.size - 1) for (b in 0 until ys.size - 1) {
            val cx = (xs[a] + xs[a + 1]) / 2f
            val cy = (ys[b] + ys[b + 1]) / 2f
            if (clipped.any { cx > it.left && cx < it.right && cy > it.top && cy < it.bottom }) covered += (xs[a + 1] - xs[a]) * (ys[b + 1] - ys[b])
        }
        return 1f - covered / area
    }
}
