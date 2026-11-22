package com.voicerpg.engine.template

import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.EncounterDefinition

/**
 * ========================================================================
 * CREATOR TEMPLATE: Combat Encounters (Solo & Multi-Enemy)
 * ========================================================================
 * Use this file to organize battle encounters for your game.
 *
 * Each encounter defines:
 * - Environment backdrop (Cave, Dungeon, Forest, Castle, Swamp)
 * - List of enemies spawned on the enemy flank
 * - Optional fixed starting party (defaults to standard active fellowship)
 */
object TemplateEncounters {

    const val ENCOUNTER_SOLO_ID = "template_solo_battle"
    const val ENCOUNTER_TRIO_ID = "template_trio_battle"

    /**
     * 1. Solo Battle Encounter:
     * 1 Template Hero vs. 1 Training Drone Alpha.
     * Perfect for initial tutorials, 1v1 duels, and mechanics testing.
     */
    val TEMPLATE_SOLO_BATTLE = EncounterDefinition(
        id = ENCOUNTER_SOLO_ID,
        name = "Initiate Proving Trial",
        description = "A single combat drone awakens to test vocal incantations and basic resonance.",
        environment = BattleEnvironment.CAVE,
        enemies = listOf(
            TemplateEnemy.createTemplateEnemy(
                id = "drone_solo",
                name = "Training Drone Alpha",
                subtitle = "Automaton Target",
                hp = 120,
                attack = 16,
                speed = 45
            )
        ),
        initialParty = listOf(TemplateHero.createTemplateAdventurer())
    )

    /**
     * 2. Multi-Enemy Squad Battle:
     * 1 Template Hero vs. 3 Drones (Vanguard, Artillery, and Scout).
     * Demonstrates multi-target combat, threat distribution, and target selection voice commands.
     */
    val TEMPLATE_TRIO_BATTLE = EncounterDefinition(
        id = ENCOUNTER_TRIO_ID,
        name = "Squad Defense Exercise",
        description = "Three combat automatons deploy in staggered formation to test crowd control and targeting.",
        environment = BattleEnvironment.DUNGEON,
        enemies = listOf(
            TemplateEnemy.createTemplateEnemy(
                id = "drone_vanguard",
                name = "Drone Vanguard",
                subtitle = "Frontline Brawler",
                hp = 110,
                attack = 18,
                speed = 42
            ),
            TemplateEnemy.createTemplateEnemy(
                id = "drone_artillery",
                name = "Drone Artillery",
                subtitle = "Backline Blaster",
                hp = 85,
                attack = 22,
                speed = 52
            ),
            TemplateEnemy.createTemplateEnemy(
                id = "drone_scout",
                name = "Drone Scout",
                subtitle = "Evasive Disruptor",
                hp = 70,
                attack = 12,
                speed = 65
            )
        ),
        initialParty = listOf(TemplateHero.createTemplateAdventurer())
    )
}
