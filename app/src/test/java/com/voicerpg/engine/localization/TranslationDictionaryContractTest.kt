package com.voicerpg.engine.localization

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files

/**
 * Offline phrase dictionaries come from two places:
 *  - `assets/game/localization/strings_<lang>.json`: the engine's own UI phrases. Engine-owned, synced
 *    byte-identical into every game, so it must never carry one game's story text.
 *  - `assets/game/translations/strings_<lang>.json`: the game's story and content phrases. Game-owned,
 *    never synced. Its entries win over the engine's for the same phrase.
 *
 * Contract tests: they run on whatever content pack is in app/src/main/assets/game (the engine's
 * template, or a game's content via scripts/test_content_packs.sh).
 */
class TranslationDictionaryContractTest {

    private val mapType = object : TypeToken<Map<String, String>>() {}.type

    private fun firstDir(vararg candidates: String): File? =
        candidates.map(::File).firstOrNull { it.isDirectory }

    private val assetsDir: File by lazy {
        assertNotNull("assets/game not found", firstDir("app/src/main/assets/game", "src/main/assets/game"))
        firstDir("app/src/main/assets/game", "src/main/assets/game")!!
    }

    private fun readDictionary(file: File): Map<String, String> =
        file.reader(Charsets.UTF_8).use { Gson().fromJson<Map<String, String>>(it, mapType) } ?: emptyMap()

    private fun dictionaryFiles(subdir: String): List<File> =
        File(assetsDir, subdir).listFiles { f -> f.name.matches(Regex("strings_[a-z]{2}\\.json")) }
            ?.sortedBy { it.name }.orEmpty()

    @After
    fun tearDown() = TranslationManager.clearAssetDictionaries()

    @Test
    fun engineDictionaryHoldsOnlyPhrasesTheEngineShows() {
        val engineSources = firstDir(
            "app/src/main/java/com/voicerpg/engine", "src/main/java/com/voicerpg/engine"
        )
        assertNotNull("engine sources not found", engineSources)
        val corpus = buildString {
            engineSources!!.walkTopDown().filter { it.extension == "kt" }.forEach { append(it.readText()).append('\n') }
            File(assetsDir, "commands").walkTopDown().filter { it.extension == "json" }
                .forEach { append(it.readText()).append('\n') }
        }.lowercase()
        // Headings like "4. HONORIFIC TITLE" are built at runtime from a number and a literal.
        fun shownByEngine(phrase: String): Boolean {
            val p = phrase.lowercase()
            val escaped = Gson().toJson(p).trim('"')
            val unnumbered = p.replace(Regex("^\\d+\\.\\s+"), "")
            return p in corpus || escaped in corpus || unnumbered in corpus
        }

        val files = dictionaryFiles("localization")
        assertTrue("the engine ships offline UI dictionaries", files.isNotEmpty())
        for (file in files) {
            val strays = readDictionary(file).keys.filterNot(::shownByEngine)
            assertTrue(
                "localization/${file.name} has ${strays.size} phrases the engine never shows; " +
                    "story and content phrases belong in the game's translations/${file.name}. " +
                    "First few: ${strays.take(5)}",
                strays.isEmpty()
            )
        }
    }

    @Test
    fun gameTranslationsAreFlatPhraseMapsForEngineLanguages() {
        val engineLanguages = dictionaryFiles("localization").map { it.name }.toSet()
        val gameFiles = File(assetsDir, "translations").listFiles()?.toList().orEmpty()
        for (file in gameFiles) {
            assertTrue(
                "translations/${file.name}: expected strings_<lang>.json for a language the engine has " +
                    "($engineLanguages)",
                file.name in engineLanguages
            )
            val dictionary = readDictionary(file)
            assertTrue("translations/${file.name} is empty", dictionary.isNotEmpty())
            dictionary.forEach { (phrase, translated) ->
                assertTrue("translations/${file.name}: blank translation for '$phrase'", translated.isNotBlank())
            }
        }
    }

    @Test
    fun gamePhrasesAndEngineUiPhrasesBothTranslateFromThisPack() {
        for (engineFile in dictionaryFiles("localization")) {
            val lang = engineFile.name.removePrefix("strings_").removeSuffix(".json")
            val engine = readDictionary(engineFile)
            val game = File(assetsDir, "translations/${engineFile.name}")
                .takeIf { it.isFile }?.let(::readDictionary).orEmpty()

            TranslationManager.clearAssetDictionaries()
            TranslationManager.loadAssetDictionary(lang) { path -> FileInputStream(File(assetsDir.parentFile, path)) }

            game.entries.take(25).forEach { (phrase, translated) ->
                assertEquals("[$lang] game phrase '$phrase'", translated, TranslationManager.lookupDictionary(phrase, lang))
            }
            engine.entries.filter { it.key !in game }.take(25).forEach { (phrase, translated) ->
                assertEquals("[$lang] engine phrase '$phrase'", translated, TranslationManager.lookupDictionary(phrase, lang))
            }
        }
    }

    @Test
    fun gameTranslationOverridesEngineWordingAndWorksWithoutEither() {
        val root = Files.createTempDirectory("dict-pack").toFile()
        try {
            File(root, "game/localization").mkdirs()
            File(root, "game/translations").mkdirs()
            File(root, "game/localization/strings_de.json")
                .writeText("""{"BACK": "ZURÜCK", "Options": "Optionen"}""")
            File(root, "game/translations/strings_de.json")
                .writeText("""{"Options": "Wahlkampf-Optionen", "Golden Escalator": "Goldene Rolltreppe"}""")
            val open = { path: String -> FileInputStream(File(root, path)) }

            TranslationManager.loadAssetDictionary("de", open)
            assertEquals("ZURÜCK", TranslationManager.lookupDictionary("BACK", "de"))
            assertEquals("Wahlkampf-Optionen", TranslationManager.lookupDictionary("Options", "de"))
            assertEquals("Goldene Rolltreppe", TranslationManager.lookupDictionary("Golden Escalator", "de"))

            // A language with no files at all is not an error; lookups fall through to later tiers.
            TranslationManager.loadAssetDictionary("it", open)
            assertEquals(null, TranslationManager.lookupDictionary("Golden Escalator", "it"))

            // A game with no translations folder still gets the engine's UI phrases.
            File(root, "game/translations/strings_de.json").delete()
            TranslationManager.clearAssetDictionaries()
            TranslationManager.loadAssetDictionary("de", open)
            assertEquals("Optionen", TranslationManager.lookupDictionary("Options", "de"))
        } finally {
            root.deleteRecursively()
        }
    }
}
