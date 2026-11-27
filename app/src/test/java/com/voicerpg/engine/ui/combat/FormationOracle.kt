package com.voicerpg.engine.ui.combat

import androidx.compose.ui.geometry.Rect

/**
 * An independent check of a battle formation (what BattleFormation promises), written from the rules rather
 * than from its code: feet on the floor band, nobody off the flank, every head fully visible (no fighter drawn
 * later reaches it, even stepping forward on its turn or flinching back), most of every body visible, the active
 * fighter drawn last, nobody shrunk below the floor.
 */
object FormationOracle {

    /** Arena sizes in dp: phone portrait (normal console, expanded console), landscape, tablet. */
    val ARENAS = listOf(360f to 300f, 360f to 220f, 780f to 260f, 1000f to 500f)

    fun stage(arenaWDp: Float, arenaHDp: Float, density: Float, facingRight: Boolean) =
        BattleFormation.Stage.of(widthPx = arenaWDp / 2f * density, heightPx = arenaHDp * density, density = density, facingRight = facingRight)

    /** Where [f] can be drawn at [p]: its body, widened by the turn steps (forward more when active). */
    fun reach(f: BattleFormation.Figure, p: BattleFormation.Placement, stage: BattleFormation.Stage, active: Boolean): Rect {
        val body = p.body(f)
        val fwd = if (active) stage.activeStep else stage.readyStep
        val back = stage.readyStep
        return if (stage.facingRight) Rect(body.left - back, body.top, body.right + fwd, body.bottom)
        else Rect(body.left - fwd, body.top, body.right + back, body.bottom)
    }

    private fun overlapArea(a: Rect, b: Rect): Float {
        val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return if (w > 0f && h > 0f) w * h else 0f
    }

    private fun visibleFraction(r: Rect, covers: List<Rect>, n: Int = 24): Float {
        if (r.width <= 0f || r.height <= 0f) return 1f
        var seen = 0
        for (i in 0 until n) for (j in 0 until n) {
            val x = r.left + (i + 0.5f) * r.width / n
            val y = r.top + (j + 0.5f) * r.height / n
            if (covers.none { x > it.left && x < it.right && y > it.top && y < it.bottom }) seen++
        }
        return seen.toFloat() / (n * n)
    }

    /** Every rule broken by [formation] (empty = good), each with its numbers. */
    fun violations(
        figures: List<BattleFormation.Figure>,
        formation: BattleFormation.Formation,
        stage: BattleFormation.Stage,
        activeId: String?
    ): List<String> {
        val out = mutableListOf<String>()
        val ps = formation.placements
        if (ps.size != figures.size) return listOf("placed ${ps.size} of ${figures.size}")
        val eps = 0.5f
        val activeIndex = if (activeId == null) -1 else figures.indexOfFirst { it.id == activeId }
        figures.forEachIndexed { i, f ->
            val p = ps[i]
            if (p.scale < BattleFormation.MIN_SCALE * BattleFormation.BACK_ROW_SCALE - 0.001f) out += "${f.id} scale ${p.scale} below the floor"
            if (p.feetY < stage.horizon - eps || p.feetY > stage.floorBottom + eps)
                out += "${f.id} feet ${p.feetY} off the floor band ${stage.horizon}..${stage.floorBottom}"
            val body = p.body(f)
            if (body.left < -eps || body.right > stage.width + eps || body.top < -eps || body.bottom > stage.height + eps)
                out += "${f.id} body $body off the flank ${stage.width}x${stage.height}"
        }
        if (activeIndex >= 0 && ps[activeIndex].z != ps.maxOf { it.z }) out += "active $activeId not drawn last"
        if (ps.map { it.z }.toSet().size != ps.size) out += "draw order not a strict order: ${ps.map { it.z }}"
        figures.forEachIndexed { i, f ->
            val head = ps[i].head(f)
            val later = figures.indices.filter { ps[it].z > ps[i].z }
            for (j in later) {
                val r = reach(figures[j], ps[j], stage, active = j == activeIndex)
                val a = overlapArea(head, r)
                if (a > 0.5f) out += "${f.id}'s head $head covered by ${figures[j].id} ($r): ${"%.1f".format(a)} px²"
            }
            val seen = visibleFraction(ps[i].body(f), later.map { ps[it].body(figures[it]) })
            if (seen < BattleFormation.MIN_BODY_VISIBLE - 0.02f) out += "${f.id} only ${"%.0f".format(seen * 100)}% of the body visible"
        }
        return out
    }
}
