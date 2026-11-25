package com.voicerpg.engine.content

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Battle sprite sheets (docs/SPRITE_PLAN.md): a PNG sheet plus its Aseprite JSON export, in
 * `game/sprites/sheets/<spriteId>.json` (+ the PNG named by `meta.image`). Tags are animations, the
 * `feet` slice is the point that stands on the floor, and a tag's user data carries its event frames
 * (`release=3`, `impact=4`: counted within the tag, from 1). Art is drawn facing right.
 */
data class SpriteSheet(
    val id: String,
    /** Asset path of the PNG, from the assets root (e.g. `game/sprites/sheets/hero.png`). */
    val imagePath: String,
    val cellW: Int,
    val cellH: Int,
    val frames: List<SheetFrame>,
    val tags: Map<String, SheetTag>,
    val slices: Map<String, SheetSlice>
) {
    /** The point that stands on the floor: the `feet` slice's centre-x and bottom edge (or its pivot). */
    val feet: Pair<Int, Int>
        get() = slices["feet"]?.let { it.pivotX to it.pivotY } ?: (cellW / 2 to cellH)
}

/** One frame: its rectangle in the sheet, and where that (possibly trimmed) rectangle sits in the cell. */
data class SheetFrame(val x: Int, val y: Int, val w: Int, val h: Int, val offX: Int, val offY: Int, val durationMs: Int)

enum class TagDirection { FORWARD, REVERSE, PINGPONG }

/** An animation: sheet frames [from]..[to]; [events] maps an event name to its frame within the tag (0-based). */
data class SheetTag(val name: String, val from: Int, val to: Int, val direction: TagDirection, val events: Map<String, Int>)

data class SheetSlice(val name: String, val x: Int, val y: Int, val w: Int, val h: Int, val pivotX: Int, val pivotY: Int)

/** `game/sprites/sprites.json`: per-sprite settings and palette-swap variants of another sheet. */
data class SpriteSetting(
    val id: String,
    /** A variant draws this sheet... */
    val base: String? = null,
    /** ...with these colours swapped (ARGB ints). */
    val recolor: Map<Int, Int> = emptyMap(),
    /** Extra size for this sprite (a big variant), on top of the whole-number pixel scale. */
    val scale: Float = 1f
)

object AsepriteParser {

    /** Parses an Aseprite JSON export (array or hash frames). [dir] is the asset folder the PNG sits in. */
    fun parse(id: String, json: String, dir: String): SpriteSheet? = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        val meta = root.getAsJsonObject("meta") ?: JsonObject()
        val frameElements: List<JsonObject> = when (val f = root.get("frames")) {
            null -> emptyList()
            else -> if (f.isJsonArray) f.asJsonArray.map { it.asJsonObject } else f.asJsonObject.entrySet().map { it.value.asJsonObject }
        }
        val frames = frameElements.map { fo ->
            val r = fo.getAsJsonObject("frame")
            val sss = fo.getAsJsonObject("spriteSourceSize")
            SheetFrame(
                x = r.int("x"), y = r.int("y"), w = r.int("w"), h = r.int("h"),
                offX = sss?.int("x") ?: 0, offY = sss?.int("y") ?: 0,
                durationMs = fo.get("duration")?.asInt ?: 100
            )
        }
        val source = frameElements.firstOrNull()?.getAsJsonObject("sourceSize")
        val cellW = source?.int("w") ?: frames.firstOrNull()?.w ?: 0
        val cellH = source?.int("h") ?: frames.firstOrNull()?.h ?: 0
        val tags = meta.getAsJsonArray("frameTags")?.map { it.asJsonObject }?.associate { t ->
            val name = t.get("name").asString
            name to SheetTag(
                name = name,
                from = t.int("from"),
                to = t.int("to"),
                direction = when (t.get("direction")?.asString?.lowercase()) {
                    "reverse" -> TagDirection.REVERSE
                    "pingpong", "pingpong_reverse" -> TagDirection.PINGPONG
                    else -> TagDirection.FORWARD
                },
                events = parseEvents(t.get("data")?.takeIf { it.isJsonPrimitive }?.asString)
            )
        } ?: emptyMap()
        val slices = meta.getAsJsonArray("slices")?.mapNotNull { el ->
            val s = el.asJsonObject
            val key = s.getAsJsonArray("keys")?.firstOrNull()?.asJsonObject ?: return@mapNotNull null
            val b = key.getAsJsonObject("bounds")
            val pivot = key.getAsJsonObject("pivot")
            val x = b.int("x"); val y = b.int("y"); val w = b.int("w"); val h = b.int("h")
            SheetSlice(
                name = s.get("name").asString, x = x, y = y, w = w, h = h,
                pivotX = if (pivot != null) x + pivot.int("x") else x + w / 2,
                pivotY = if (pivot != null) y + pivot.int("y") else y + h
            )
        }?.associateBy { it.name } ?: emptyMap()
        val image = meta.get("image")?.asString?.substringAfterLast('/') ?: "$id.png"
        SpriteSheet(id, "$dir/$image", cellW, cellH, frames, tags, slices)
    }.getOrNull()

    /** `release=3, impact=4` (frames within the tag, from 1) → 0-based indices. */
    internal fun parseEvents(data: String?): Map<String, Int> =
        data.orEmpty().split(',', ';', ' ', '\n')
            .mapNotNull { part ->
                val (k, v) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val n = v.trim().toIntOrNull() ?: return@mapNotNull null
                k.trim().lowercase() to (n - 1).coerceAtLeast(0)
            }.toMap()

    private fun JsonObject.int(key: String): Int = (get(key) as JsonElement?)?.asInt ?: 0
}

/** DTOs for `game/sprites/sprites.json`. */
data class SpriteSettingDto(val base: String? = null, val palette: Map<String, String>? = null, val scale: Float? = null)
data class SpriteSettingsFileDto(val sprites: Map<String, SpriteSettingDto>? = null)

internal fun SpriteSettingDto.toSetting(id: String): SpriteSetting = SpriteSetting(
    id = id,
    base = base?.ifBlank { null },
    recolor = palette.orEmpty().mapNotNull { (from, to) ->
        val a = parseArgb(from) ?: return@mapNotNull null
        val b = parseArgb(to) ?: return@mapNotNull null
        a to b
    }.toMap(),
    scale = (scale ?: 1f).coerceIn(0.25f, 4f)
)

/** `#RRGGBB` or `#AARRGGBB` → ARGB int (opaque when no alpha is given). */
internal fun parseArgb(hex: String): Int? {
    val h = hex.trim().removePrefix("#")
    val v = h.toLongOrNull(16) ?: return null
    return when (h.length) {
        6 -> (0xFF000000L or v).toInt()
        8 -> v.toInt()
        else -> null
    }
}

/** A sheet as a combatant uses it: the sprite key it was found under, the sheet, and any variant settings. */
data class ResolvedSheet(val key: String, val sheet: SpriteSheet, val setting: SpriteSetting?)

/**
 * The sprite contract (docs/SPRITE_PLAN.md §3): what every game must ship before it can be released.
 * Enforced when the game opts in (`manifest.json` `"spriteContract": 1`) and always for a release build.
 */
object SpriteContract {

    enum class Role(val required: List<String>, val cells: Set<Int>) {
        PARTY(listOf("idle", "ready", "charge", "cast", "attack", "hurt", "ko", "victory"), setOf(64)),
        ENEMY(listOf("idle", "attack", "hurt"), setOf(64)),
        BOSS(listOf("idle", "windup", "attack", "cast", "hurt", "charge_ultimate", "ultimate"), setOf(64, 96, 128))
    }

    /** The event frame each of these animations must mark, when the role requires the animation. */
    val EVENTS = mapOf("cast" to "release", "attack" to "impact", "ultimate" to "release")

    data class Violation(val combatant: String, val sprite: String, val problem: String) {
        override fun toString() = "$combatant ($sprite): $problem"
    }

    data class Report(val violations: List<Violation>, val checked: Int) {
        val passes: Boolean get() = violations.isEmpty()

        fun format(): String = buildString {
            appendLine("Sprite contract: $checked combatants checked, ${violations.size} problem(s)")
            violations.groupBy { it.combatant }.forEach { (who, list) ->
                appendLine("  $who (${list.first().sprite})")
                list.forEach { appendLine("    - ${it.problem}") }
            }
        }
    }

    fun isEnforced(manifest: GameManifest, releaseBuild: Boolean): Boolean = releaseBuild || manifest.spriteContract >= 1

    fun check(pack: GameContentPack): Report {
        val violations = mutableListOf<Violation>()
        val fighters = pack.characters.values.filter { it.combat != null && it.role != CharacterRole.NARRATOR }
        fighters.sortedBy { it.id }.forEach { c -> violations += checkSprite(pack, c.id, c.id, Role.PARTY) }

        val enemies = pack.enemyTemplates.values.sortedBy { it.id }
        val keyOf = enemies.associate { it.id to it.spriteId.ifBlank { it.id } }
        enemies.forEach { e ->
            val key = keyOf.getValue(e.id)
            val role = if (e.isBoss) Role.BOSS else Role.ENEMY
            violations += checkSprite(pack, e.id, key, role)
            if (e.isBoss) {
                val sharers = enemies.filter { it.id != e.id && keyOf[it.id] == key }.map { it.id }
                if (sharers.isNotEmpty()) violations += Violation(e.id, key, "shares its sprite with ${sharers.joinToString()} (every boss needs its own)")
            }
        }
        return Report(violations, fighters.size + enemies.size)
    }

    private fun checkSprite(pack: GameContentPack, who: String, key: String, role: Role): List<Violation> {
        val resolved = pack.resolveSheet(key)
        val variantBase = pack.spriteSettings[key]?.base
        if (resolved == null) {
            val problem = if (variantBase != null) "variant of $variantBase, which has no sprite sheet" else "no sprite sheet"
            return listOf(Violation(who, key, problem))
        }
        val sheet = resolved.sheet
        val out = mutableListOf<Violation>()
        role.required.filter { it !in sheet.tags }.forEach { out += Violation(who, key, "missing animation $it") }
        val cellOk = sheet.cellW == sheet.cellH && sheet.cellW in role.cells
        if (!cellOk) {
            val must = if (role.cells.size == 1) "${role.cells.single()}x${role.cells.single()}" else role.cells.sorted().joinToString(", ", postfix = " px square")
            out += Violation(who, key, "frames are ${sheet.cellW}x${sheet.cellH}, must be $must")
        }
        if ("feet" !in sheet.slices) out += Violation(who, key, "no feet slice")
        EVENTS.forEach { (tag, event) ->
            if (tag in role.required && sheet.tags[tag]?.events?.containsKey(event) == false) out += Violation(who, key, "$tag has no $event event")
        }
        return out
    }
}
