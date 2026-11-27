package com.voicerpg.engine.content

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract test (the game's own content): a game's content matches its `combatResource`.
 *
 *  - A game without mana (`"none"`) has no mana left in its files: no `mpCost`, `manaRestorePct`, `maxMp` or
 *    `startingMp` above 0. The engine would ignore them, but leftovers mislead whoever edits the game next.
 *  - A game with mana gives every fighter (characters with combat stats, and every class kit) a way to restore
 *    their own mana (attune), so nobody is ever stranded without it.
 */
class CombatResourceContractTest {

    private val gameDir: File = listOf(File("src/main/assets/game"), File("app/src/main/assets/game")).first { it.isDirectory }

    private fun json(f: File): JsonElement = JsonParser.parseString(f.readText())

    /** Every (file, path, value) for numeric fields named [keys] anywhere in [el]. */
    private fun numbers(el: JsonElement, keys: Set<String>, path: String, out: MutableList<Pair<String, Double>>) {
        when {
            el.isJsonObject -> el.asJsonObject.entrySet().forEach { (k, v) ->
                if (k in keys && v.isJsonPrimitive && v.asJsonPrimitive.isNumber) out += "$path.$k" to v.asDouble
                numbers(v, keys, "$path.$k", out)
            }
            el.isJsonArray -> el.asJsonArray.forEachIndexed { i, v -> numbers(v, keys, "$path[$i]", out) }
        }
    }

    private fun contentFiles(): List<File> =
        listOf("spells", "characters", "classes").flatMap { d -> File(gameDir, d).listFiles { f -> f.extension == "json" }?.toList().orEmpty() }

    @Test
    fun `content matches the game's combat resource`() {
        GameContent.reset()
        GameContent.initializeFromDirectory(gameDir)
        if (!GameContent.usesMana) {
            val found = mutableListOf<Pair<String, Double>>()
            contentFiles().forEach { f -> numbers(json(f), setOf("mpCost", "manaRestorePct", "maxMp", "startingMp"), f.name, found) }
            val leftovers = found.filter { it.second > 0.0 }
            assertTrue("A game without mana still has mana in its content:\n  " + leftovers.joinToString("\n  ") { "${it.first} = ${it.second}" },
                leftovers.isEmpty())
        } else {
            val stranded = GameContent.characters.values.filter { it.combat != null && it.combat.spells.none { s -> s.manaRestorePct > 0f } }.map { it.id } +
                GameContent.classes.filter { c -> GameContent.spellsForClass(c.id).none { it.manaRestorePct > 0f } }.map { "class ${it.id}" }
            assertTrue("Fighters with no way to restore their mana: $stranded", stranded.isEmpty())
        }
    }
}
