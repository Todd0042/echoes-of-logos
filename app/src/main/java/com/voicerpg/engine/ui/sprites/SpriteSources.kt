package com.voicerpg.engine.ui.sprites

import androidx.compose.ui.graphics.Color
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.content.SpriteDefinition
import com.voicerpg.engine.content.SpriteSheet
import com.voicerpg.engine.content.TagDirection
import com.voicerpg.engine.model.CharacterStance
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.PartyMember

/**
 * The battle animations every sprite can be asked for (docs/SPRITE_PLAN.md §3.2). A sprite that lacks one
 * plays the next name in [chain]; every chain ends at `idle`, so a missing tag never breaks a battle (the
 * sprite contract is what makes a game supply them).
 */
enum class AnimTag(val chain: List<String>, val loops: Boolean) {
    IDLE(listOf("idle"), loops = true),
    READY(listOf("ready", "idle"), loops = true),
    WEAK(listOf("weak", "idle"), loops = true),
    CHARGE(listOf("charge", "ready", "idle"), loops = true),
    CAST(listOf("cast", "attack", "idle"), loops = false),
    ATTACK(listOf("attack", "cast", "idle"), loops = false),
    HURT(listOf("hurt", "idle"), loops = false),
    KO(listOf("ko", "hurt", "idle"), loops = false),
    DEATH(listOf("death", "hurt", "idle"), loops = false),
    VICTORY(listOf("victory", "ready", "idle"), loops = true),
    GUARD(listOf("guard", "ready", "idle"), loops = true),
    STEP(listOf("step", "ready", "idle"), loops = false),
    REVIVE(listOf("revive", "idle"), loops = false),
    ITEM(listOf("item", "cast", "attack", "idle"), loops = false),
    WINDUP(listOf("windup", "ready", "idle"), loops = true),
    TAUNT(listOf("taunt", "idle"), loops = false),
    CHARGE_ULTIMATE(listOf("charge_ultimate", "windup", "ready", "idle"), loops = true),
    ULTIMATE(listOf("ultimate", "cast", "attack", "idle"), loops = false);

    val key: String get() = chain.first()

    companion object {
        const val PHASE_TWO_PREFIX = "p2_"
    }
}

/** A playable animation: sprite frame indices in play order, each with its duration; events index into [frames]. */
data class AnimClip(
    val name: String,
    val frames: List<Int>,
    val durationsMs: List<Int>,
    val loops: Boolean,
    val events: Map<String, Int> = emptyMap()
) {
    val totalMs: Int get() = durationsMs.sum()
}

/** Where a combatant's pictures come from: an Aseprite sheet, or a legacy text grid. One animation path for both. */
sealed interface SpriteSource {
    val id: String
    val cellW: Int
    val cellH: Int
    /** The point (in cell pixels) that stands on the floor. */
    val feet: Pair<Int, Int>
    /** Extra size from `sprites.json` (variants); 1 = the sheet's own size. */
    val scale: Float

    /** The animation names this sprite really has (no fallbacks). */
    val animationNames: Set<String>

    /** The clip with exactly this name, or null. */
    fun clipNamed(name: String, loops: Boolean): AnimClip?

    /** [tag], or the first animation down its fallback chain this sprite has; `p2_` versions first in phase two. */
    fun clip(tag: AnimTag, phase2: Boolean = false): AnimClip {
        for (name in tag.chain) {
            if (phase2) clipNamed(AnimTag.PHASE_TWO_PREFIX + name, tag.loops)?.let { return it }
            clipNamed(name, tag.loops)?.let { return it }
        }
        return clipNamed("idle", true) ?: AnimClip("idle", listOf(0), listOf(500), loops = true)
    }
}

class SheetSprite(
    override val id: String,
    val sheet: SpriteSheet,
    /** Palette swap (ARGB → ARGB) applied when the sheet's picture is loaded. */
    val recolor: Map<Int, Int> = emptyMap(),
    override val scale: Float = 1f
) : SpriteSource {
    override val cellW get() = sheet.cellW
    override val cellH get() = sheet.cellH
    override val feet get() = sheet.feet
    override val animationNames: Set<String> get() = sheet.tags.keys

    override fun clipNamed(name: String, loops: Boolean): AnimClip? {
        val tag = sheet.tags[name] ?: return null
        val last = sheet.frames.lastIndex
        val from = tag.from.coerceIn(0, last)
        val to = tag.to.coerceIn(from, last)
        val frames = when (tag.direction) {
            TagDirection.FORWARD -> (from..to).toList()
            TagDirection.REVERSE -> (to downTo from).toList()
            TagDirection.PINGPONG -> (from..to).toList() + ((to - 1) downTo (from + 1)).toList()
        }
        return AnimClip(name, frames, frames.map { sheet.frames[it].durationMs.coerceAtLeast(16) }, loops, tag.events)
    }

    override fun toString() = "SheetSprite($id: ${sheet.id} ${cellW}x$cellH, ${sheet.tags.keys})"
}

/**
 * A legacy text-grid sprite (a character file's `sprite`, `enemies/enemy_sprites.json`): its frames become
 * `idle` (upright, crouch), `charge` and `cast` (the cast pose), and when present `attack`, `hurt` and `death`.
 */
class GridSprite(override val id: String, val definition: SpriteDefinition) : SpriteSource {

    companion object {
        const val UPRIGHT = 0
        const val CROUCH = 1
        const val CAST = 2
        const val DAMAGED = 3
        const val ATTACK = 4
        const val HURT = 5
        const val DEATH = 6
    }

    fun rows(frame: Int): List<String> = when (frame) {
        UPRIGHT -> definition.idleUpright
        CROUCH -> definition.idleCrouch
        CAST -> definition.actionCast
        DAMAGED -> definition.damaged
        ATTACK -> definition.attack ?: definition.actionCast
        HURT -> definition.hurt ?: definition.damaged
        DEATH -> definition.death ?: definition.damaged
        else -> definition.idleUpright
    }

    val palette: Map<Char, Color> get() = definition.palette

    override val cellW: Int get() = definition.idleUpright.maxOfOrNull { it.length } ?: 20
    override val cellH: Int get() = definition.idleUpright.size
    override val feet: Pair<Int, Int>
        get() {
            val lowest = definition.idleUpright.indexOfLast { row -> row.any { it != '.' } }
            return cellW / 2 to (if (lowest < 0) cellH else lowest + 1)
        }
    override val scale: Float get() = 1f

    override val animationNames: Set<String>
        get() = buildSet {
            add("idle"); add("charge"); add("cast"); add("hurt")
            if (definition.attack != null) add("attack")
            if (definition.death != null) add("death")
        }

    override fun clipNamed(name: String, loops: Boolean): AnimClip? = when (name) {
        "idle" -> AnimClip(name, listOf(UPRIGHT, CROUCH), listOf(650, 650), loops)
        "charge" -> AnimClip(name, listOf(CAST), listOf(450), loops) // the cast pose while the chant is spoken
        "cast" -> AnimClip(name, listOf(CAST), listOf(450), loops, mapOf("release" to 0))
        "attack" -> definition.attack?.let { AnimClip(name, listOf(ATTACK), listOf(400), loops, mapOf("impact" to 0)) }
        "hurt" -> AnimClip(name, listOf(if (definition.hurt != null) HURT else DAMAGED), listOf(350), loops)
        "death" -> definition.death?.let { AnimClip(name, listOf(DEATH), listOf(500), loops) }
        else -> null
    }

    override fun toString() = "GridSprite($id)"
}

/** Resolves a sprite key (a character id, an enemy's `spriteId`, a sheet or variant id) to its source. */
object SpriteLibrary {

    fun source(key: String): SpriteSource {
        GameContent.pack.resolveSheet(key)?.let { r ->
            return SheetSprite(key, r.sheet, r.setting?.recolor ?: emptyMap(), r.setting?.scale ?: 1f)
        }
        return GridSprite(key, PixelSpriteData.definitionFor(key))
    }

    /** The sprite key of a party member (its character id) and of an enemy (its `spriteId`, else its id). */
    fun keyFor(member: PartyMember): String = member.spriteKey.ifBlank { member.id }
    fun keyFor(enemy: Enemy): String = enemy.spriteId.ifBlank { enemy.id }

    /** Whether [key] has a real sheet (or a variant of one), not just a legacy grid. */
    fun hasSheet(key: String): Boolean = source(key) is SheetSprite

}

/** How big an enemy's sprite is drawn, relative to its art. */
object SpriteSizing {
    /**
     * [enemy]'s `scale` for [source]: legacy grids take it as is (bosses default to 2x the small grid); a sheet is
     * drawn at its own size, so a boss's default enlargement doesn't apply to it, only what the content asks
     * for beyond that default.
     */
    fun enemyFactor(enemy: Enemy, source: SpriteSource): Float = when (source) {
        is GridSprite -> enemy.scale
        is SheetSprite -> if (enemy.isBoss) enemy.scale / Enemy.DEFAULT_BOSS_SCALE else enemy.scale
    }
}

/** The resting animation a combatant returns to between one-shots, from its combat state. */
object SpriteBase {
    fun of(member: PartyMember, isActive: Boolean): AnimTag = when {
        !member.isAlive -> AnimTag.KO
        member.stance == CharacterStance.VICTORY -> AnimTag.VICTORY
        member.stance == CharacterStance.CASTING -> AnimTag.CHARGE
        !member.isGhost && member.hpRatio < WEAK_BELOW -> AnimTag.WEAK
        isActive || member.isTurnReady -> AnimTag.READY
        else -> AnimTag.IDLE
    }

    fun of(enemy: Enemy): AnimTag = if (enemy.isAlive) AnimTag.IDLE else AnimTag.DEATH

    const val WEAK_BELOW = 0.25f
}

/** A sprite frame as pixels (what shatters on death): [width] x [height], [colorAt] null where transparent. */
class PixelGrid(val width: Int, val height: Int, private val pixels: Array<Color?>) {
    fun colorAt(x: Int, y: Int): Color? = if (x in 0 until width && y in 0 until height) pixels[y * width + x] else null

    companion object {
        val EMPTY = PixelGrid(0, 0, emptyArray())

        fun fromRows(rows: List<String>, palette: Map<Char, Color>): PixelGrid {
            val w = rows.maxOfOrNull { it.length } ?: 0
            val h = rows.size
            val px = arrayOfNulls<Color>(w * h)
            rows.forEachIndexed { y, line ->
                line.forEachIndexed { x, ch ->
                    if (ch != '.') palette[ch]?.takeIf { it.alpha > 0f }?.let { px[y * w + x] = it }
                }
            }
            return PixelGrid(w, h, px)
        }
    }
}
