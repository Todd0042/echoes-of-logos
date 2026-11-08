package com.voicerpg.engine.engine

import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.SpellSchool

object SpellThesaurus {

    private val PYROMANCY_ROOTS = setOf(
        "cinder", "inferno", "ash", "blaze", "blazing", "ignite", "scorching",
        "incandescent", "solar", "phoenix", "wrath", "embers", "consume",
        "flame", "flames", "fire", "fireball", "burn", "combustion",
        "conflagration", "pyre", "hellfire", "searing", "flare", "magma",
        "resonant",
        // Generic labor lexicon (genre-neutral additions for workplace-flavored games)
        "heat"
    )

    private val CRYOMANCY_ROOTS = setOf(
        "glacial", "glacier", "permafrost", "frostbite", "frost", "blizzard",
        "crystalline", "crystal", "absolute zero", "tundra", "shards", "bitter",
        "freeze", "frozen", "ice", "icicle", "hail", "sleet", "avalanche",
        "rime", "chill", "chilling", "cold", "zero"
    )

    private val ELECTROMANCY_ROOTS = setOf(
        "tempest", "thunderclap", "galvanic", "arc", "storm", "fulgur",
        "lightning", "volt", "voltage", "flash", "strike", "spark", "sparks",
        "thunder", "thunderbolt", "shock", "discharge", "surge", "electrify",
        "static", "plasma", "stasis", "debate", "dismantle",
        // Generic labor lexicon (genre-neutral additions for workplace-flavored games)
        "charge"
    )

    private val HOLY_ROOTS = setOf(
        "radiance", "radiant", "divine", "seraph", "celestial", "dawn",
        "sanctify", "blessing", "blessed", "mend", "mending", "aegis",
        "purity", "pure", "heal", "healing", "grace", "light", "halo", "cadence",
        "restorative", "prayer", "cure", "sanctuary", "smite", "soothing",
        "deal", "art", "negotiate", "patriots", "rally", "inspire", "freedom", "golden", "liberty",
        "executive", "action", "order", "decree", "water", "sip", "hydrate", "fortitude", "clearance", "purge", "winning", "remedy",
        // Generic care/labor lexicon (genre-neutral additions for workplace-flavored games)
        "steady", "calm", "soothe", "listen",
        "backrub", "inspiring", "lunch", "groceries", "breath", "remember"
    )

    private val NATURE_ROOTS = setOf(
        "grove", "briar", "thorns", "thorn", "vines", "vine", "rain", "nature",
        "rejuvenate", "verdant", "root", "roots", "canopy", "bloom", "petal",
        "moss", "fern", "blossom", "undergrowth", "bark", "seed", "sprout",
        "wildwood", "pollen", "bramble", "nettle", "willow", "oaken", "earthen",
        "oil", "oils", "milk", "raw", "colostrum", "electrolyte", "electrolytes", "spring",
        // Generic growth lexicon (genre-neutral additions for workplace-flavored games)
        "grow"
    )

    private val SHADOW_ROOTS = setOf(
        "umbra", "abyss", "abyssal", "venom", "venomous", "phantom", "whisper",
        "shroud", "eclipse", "silent", "silence", "strike", "hollow", "dark",
        "darkness", "shade", "shadow", "void", "backstab", "stealth", "dread",
        "nocturnal", "creeping", "siphon", "mend", "drain",
        "obama", "receipts", "hypocrisy", "dates", "2014", "archive", "exposed",
        "sanction", "sanctions", "embargo", "declassify", "memo", "binder", "fisa", "leak", "leaks", "plug", "officer", "squeal",
        // Generic labor lexicon (genre-neutral additions for workplace-flavored games)
        "night"
    )

    private val MARTIAL_ROOTS = setOf(
        "blade", "cleave", "rend", "slash", "steel", "shatter", "strike",
        "vanguard", "shield", "smash", "pulverize", "bash", "crush", "charge",
        "tremendous", "wall", "border", "patriots", "america", "victory", "winning", "greatest", "historic", "huge", "bigly", "barrier", "fortify",
        "veto", "filibuster", "tariff", "tariffs", "slam", "appalachian", "rustbelt", "common", "sense", "truth", "order", "kennedy", "english", "exam", "grill", "liner", "punchline",
        "aegis", "barrier", "attune",
        // Generic labor lexicon (genre-neutral additions for workplace-flavored games):
        // the line, the dock, the freight, the grind.
        "breathe", "push", "hold", "haul", "clear", "grind", "line"
    )

    fun getKeywordsForSchool(school: SpellSchool): Set<String> {
        val builtIn = when (school) {
            SpellSchool.PYROMANCY -> PYROMANCY_ROOTS
            SpellSchool.CRYOMANCY -> CRYOMANCY_ROOTS
            SpellSchool.ELECTROMANCY -> ELECTROMANCY_ROOTS
            SpellSchool.NATURE -> NATURE_ROOTS
            SpellSchool.HOLY -> HOLY_ROOTS
            SpellSchool.SHADOW -> SHADOW_ROOTS
            SpellSchool.PHYSICAL -> MARTIAL_ROOTS
        }
        val loaded = GameContent.thesaurusRoots[school]
        return if (!loaded.isNullOrEmpty()) {
            builtIn + loaded
        } else {
            builtIn
        }
    }

    /**
     * Identifies all thematic root keywords present in the utterance.
     */
    fun findMatches(text: String, school: SpellSchool): List<String> {
        val words = text.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").split("\\s+".toRegex())
        val dictionary = getKeywordsForSchool(school)
        return words.filter { word ->
            dictionary.contains(word) || dictionary.any { root -> word.contains(root) || root.contains(word) && word.length >= 4 }
        }.distinct()
    }
}
