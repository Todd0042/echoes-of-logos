package com.voicerpg.engine.testing

/**
 * Builds Aseprite JSON exports the way Aseprite writes them (hash format, trimmed frames, tags with
 * direction and user data, slices), for tests that ship sprite sheets in a content pack.
 */
object AsepriteFixture {

    data class Tag(val name: String, val from: Int, val to: Int, val dir: String = "forward", val data: String? = null)

    fun export(name: String, cell: Int, tags: List<Tag>, feet: Pair<Int, Int>? = cell / 2 to cell - 4, durations: (Int) -> Int = { 100 }): String {
        val count = tags.maxOf { it.to } + 1
        val frames = (0 until count).joinToString(",\n") { i ->
            val x = (i % 8) * cell; val y = (i / 8) * cell
            """"$name $i.aseprite": { "frame": {"x":$x,"y":$y,"w":${cell - 16},"h":${cell - 8}}, "rotated": false, "trimmed": true,
               "spriteSourceSize": {"x":8,"y":6,"w":${cell - 16},"h":${cell - 8}}, "sourceSize": {"w":$cell,"h":$cell}, "duration": ${durations(i)} }"""
        }
        val tagJson = tags.joinToString(",") { t ->
            """{ "name": "${t.name}", "from": ${t.from}, "to": ${t.to}, "direction": "${t.dir}", "color": "#000000ff"${t.data?.let { ", \"data\": \"$it\"" } ?: ""} }"""
        }
        val feetSlice = feet?.let {
            """{ "name": "feet", "color": "#0000ffff", "keys": [ { "frame": 0, "bounds": {"x": ${it.first - 2}, "y": ${it.second - 2}, "w": 4, "h": 2 } } ] },"""
        } ?: ""
        return """{ "frames": { $frames },
          "meta": { "app": "https://www.aseprite.org/", "version": "1.3.7", "image": "$name.png", "format": "RGBA8888",
            "size": {"w": ${8 * cell}, "h": ${((count + 7) / 8) * cell}}, "scale": "1",
            "frameTags": [ $tagJson ],
            "layers": [ { "name": "Layer 1", "opacity": 255, "blendMode": "normal" } ],
            "slices": [ $feetSlice
                        { "name": "cast", "color": "#ff0000ff", "keys": [ { "frame": 0, "bounds": {"x": 44, "y": 24, "w": 2, "h": 2 } } ] } ] } }"""
    }

    /** Tags laid end to end, [frames] frames each; [events] adds user data to the named tags. */
    fun tags(names: List<String>, frames: Int = 2, events: Map<String, String> = emptyMap()): List<Tag> =
        names.mapIndexed { i, n -> Tag(n, i * frames, i * frames + frames - 1, data = events[n]) }

    val PARTY = listOf("idle", "ready", "charge", "cast", "attack", "hurt", "ko", "victory")
    val ENEMY = listOf("idle", "attack", "hurt")
    val BOSS = listOf("idle", "windup", "attack", "cast", "hurt", "charge_ultimate", "ultimate")
    val EVENTS = mapOf("cast" to "release=2", "attack" to "impact=2", "ultimate" to "release=2")
}
