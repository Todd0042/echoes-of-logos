package com.voicerpg.engine.combat

// GENERATED from docs/combat-design/data/enemies.json - regenerate, do not hand-edit.
data class EnemyCodexEntry(
    val family: String,
    val selfElement: String,
    val defense: Int,
    val baseXp: Int,
    val moveset: String
)

/**
 * Optional lore-doctrine lookup keyed by normalized enemy display name. Enemy data
 * files (assets/game/enemies/enemies.json) carry family/element/defense/xp/moveset
 * directly; the codex only back-fills values for enemies spawned without them.
 * Ships with template entries only — regenerate for your game via
 * docs/combat-design/data/enemies.json.
 */
object EnemyCodex {
    private val VARIANTS = setOf("alpha","beta","gamma","delta","epsilon","prime","a","b","c","d","i","ii","iii","iv","v","1","2","3")

    private val TABLE: Map<String, EnemyCodexEntry> = mapOf(
        "training drone" to EnemyCodexEntry(family = "CONSTRUCT", selfElement = "CRYOMANCY", defense = 6, baseXp = 60, moveset = "construct_bruiser"),
        "template brute" to EnemyCodexEntry(family = "FLESH", selfElement = "PHYSICAL", defense = 6, baseXp = 60, moveset = "flesh_bruiser"),
        "template sniper" to EnemyCodexEntry(family = "FLESH", selfElement = "PHYSICAL", defense = 3, baseXp = 55, moveset = "flesh_sniper"),
        "template caster" to EnemyCodexEntry(family = "VOID", selfElement = "SHADOW", defense = 4, baseXp = 70, moveset = "void_caster"),
        "template boss" to EnemyCodexEntry(family = "CONSTRUCT", selfElement = "PHYSICAL", defense = 14, baseXp = 600, moveset = "boss_template"),
        "template minion" to EnemyCodexEntry(family = "CONSTRUCT", selfElement = "PHYSICAL", defense = 4, baseXp = 40, moveset = "construct_bruiser"),
        "practice dummy" to EnemyCodexEntry(family = "CONSTRUCT", selfElement = "PHYSICAL", defense = 0, baseXp = 0, moveset = ""),
        "proving guardian" to EnemyCodexEntry(family = "CONSTRUCT", selfElement = "PHYSICAL", defense = 14, baseXp = 600, moveset = "boss_template"),
        "guardian warden" to EnemyCodexEntry(family = "CONSTRUCT", selfElement = "PHYSICAL", defense = 4, baseXp = 40, moveset = "construct_bruiser"),
        "drone vanguard" to EnemyCodexEntry(family = "CONSTRUCT", selfElement = "CRYOMANCY", defense = 6, baseXp = 60, moveset = "construct_bruiser"),
        "drone artillery" to EnemyCodexEntry(family = "CONSTRUCT", selfElement = "PYROMANCY", defense = 4, baseXp = 60, moveset = "construct_sniper"),
        "drone scout" to EnemyCodexEntry(family = "CONSTRUCT", selfElement = "ELECTROMANCY", defense = 3, baseXp = 55, moveset = "construct_sniper"),
    )

    private fun normalize(name: String): String {
        val parts = name.lowercase().split(" ").toMutableList()
        while (parts.isNotEmpty() && parts.last() in VARIANTS) parts.removeAt(parts.lastIndex)
        return parts.joinToString(" ")
    }

    /** Lore family for display/targeting; unknown enemies default to FLESH (never a wall). */
    fun familyOf(name: String): EnemyFamily =
        EnemyFamily.fromNameOrNull(TABLE[normalize(name)]?.family) ?: EnemyFamily.FLESH

    fun entryOf(name: String): EnemyCodexEntry? = TABLE[normalize(name)]

    fun selfElementOf(name: String): School? = School.fromNameOrNull(TABLE[normalize(name)]?.selfElement?.ifEmpty { null })
    fun defenseOf(name: String): Int = TABLE[normalize(name)]?.defense ?: 0
    fun xpOf(name: String): Int = TABLE[normalize(name)]?.baseXp ?: 60
    fun movesetOf(name: String): String = TABLE[normalize(name)]?.moveset ?: ""
    fun codexForTemplate(name: String): EnemyCodexEntry? = TABLE.entries.firstOrNull { (k, _) ->
        val words = name.lowercase().split(" ")
        k.split(" ").all { it in words } || normalize(name) == k
    }?.value
}
