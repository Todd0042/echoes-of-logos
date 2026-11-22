package com.voicerpg.engine.combat

import com.voicerpg.engine.content.GameContent

/**
 * Shallow progression (SPEC §8): levels grow HP/MP/speed/defense + a 3-4.5%/level
 * potency multiplier on base power. Never a dominant ATK stat — resonance stays king.
 * Growth curves are data-driven: hero classes define their growth in
 * assets/game/classes/<name>.json and companion characters in assets/game/characters/<name>.json.
 */
data class StatGrowth(
    val maxHp: Int,
    val maxMp: Int,
    val speed: Int,
    val defense: Int,
    val potencyPct: Float
)

data class ProgressMember(
    val id: String,
    val classKey: String,
    val level: Int,
    val xp: Int,
    val alive: Boolean
)

data class ProgressOutcome(
    val memberId: String,
    val levelsGained: Int,
    val newLevel: Int,
    val newXp: Int,
    val dMaxHp: Int,
    val dMaxMp: Int,
    val dSpeed: Int,
    val dDefense: Int
)

object Progression {
    const val MAX_LEVEL = 30
    const val FALLEN_SHARE = 0.5f

    val DEFAULT_GROWTH = StatGrowth(18, 8, 1, 1, 0.03f)

    fun xpToNext(level: Int): Int = 80 + 60 * level

    /**
     * Growth keys:
     * - "hero/<classId>" resolves to the class growth curve from class data files.
     * - "<characterId>" resolves to a companion character's growth from its character file.
     * - anything else falls back to [DEFAULT_GROWTH].
     */
    fun growthFor(classKey: String): StatGrowth = when {
        classKey.startsWith("hero/") -> {
            val classId = classKey.removePrefix("hero/")
            GameContent.classes.firstOrNull { it.id == classId }?.growth ?: DEFAULT_GROWTH
        }
        else -> GameContent.characterById(classKey)?.combat?.growth ?: DEFAULT_GROWTH
    }

    fun classKey(memberId: String, heroClassId: String?): String =
        if (memberId == "hero") "hero/" + (heroClassId ?: "") else memberId

    /**
     * Even split among alive story-legal members; fallen members still gain at 50% share.
     * Multiple level-ups carry over. Returns an outcome per member (0 levels if none).
     */
    fun awardXp(members: List<ProgressMember>, totalXp: Int): List<ProgressOutcome> {
        if (members.isEmpty() || totalXp <= 0) return emptyList()
        val weightSum = members.sumOf { if (it.alive) 1.0 else FALLEN_SHARE.toDouble() }
        val unit = if (weightSum > 0) totalXp.toDouble() / weightSum else 0.0

        return members.map { m ->
            val share = (if (m.alive) 1.0 else FALLEN_SHARE.toDouble()) * unit
            var level = m.level
            var xp = m.xp + share.toInt()
            var gained = 0
            var dHp = 0
            var dMp = 0
            var dSp = 0
            var dDef = 0
            while (level < MAX_LEVEL && xp >= xpToNext(level)) {
                xp -= xpToNext(level)
                level++
                gained++
                val g = growthFor(m.classKey)
                dHp += g.maxHp; dMp += g.maxMp; dSp += g.speed; dDef += g.defense
            }
            ProgressOutcome(m.id, gained, level, xp, dHp, dMp, dSp, dDef)
        }
    }
}
