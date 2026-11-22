package com.voicerpg.engine.template

import androidx.compose.ui.graphics.Color
import com.voicerpg.engine.model.Enemy

/**
 * ========================================================================
 * CREATOR TEMPLATE: Spawneable Enemy & Squad Factory
 * ========================================================================
 * Use this factory to spawn 1 or multiple instances of an enemy in combat.
 *
 * Spawning multiple enemies simply requires giving each instance a unique ID:
 * e.g., createTemplateEnemy("drone_1", "Drone Alpha"), createTemplateEnemy("drone_2", "Drone Beta")
 */
object TemplateEnemy {

    /**
     * Creates a single enemy instance.
     *
     * @param id Unique identifier in the encounter (e.g., "enemy_1", "goblin_a").
     * @param name Display name rendered in the UI.
     * @param subtitle Flavor/archetype tag (e.g., "Automaton Target", "Forest Scamp").
     * @param hp Starting and maximum health.
     * @param attack Base attack power used in damage calculation.
     * @param defense Armor rating reducing incoming physical damage.
     * @param speed Turn frequency (governs ATB tick speed; 50 is average).
     * @param isBoss Whether this unit is rendered with boss UI accents.
     * @param spriteTint Color used for sprite canvas silhouette rendering.
     */
    fun createTemplateEnemy(
        id: String,
        name: String = "Training Drone",
        subtitle: String = "Automaton Target",
        hp: Int = 100,
        attack: Int = 16,
        defense: Int = 6,
        speed: Int = 45,
        isBoss: Boolean = false,
        spriteTint: Color = Color(0xFFE57373)
    ): Enemy {
        return Enemy(
            id = id,
            name = name,
            subtitle = subtitle,
            currentHp = hp,
            maxHp = hp,
            baseAttack = attack,
            defense = defense,
            speed = speed,
            isBoss = isBoss,
            spriteTint = spriteTint,
            family = "CONSTRUCT",
            selfElement = "ICE",
            movesetId = "template_drone_moves"
        )
    }

    /**
     * Spawns a group or squad of template enemies with unique IDs and staggered stats.
     * Demonstrates multi-enemy combat setup with up to 6 combatants.
     */
    fun spawnSquad(count: Int, baseName: String = "Drone"): List<Enemy> {
        val clampedCount = count.coerceIn(1, 6)
        return (1..clampedCount).map { index ->
            val letter = ('A' + index - 1)
            createTemplateEnemy(
                id = "template_drone_$index",
                name = "$baseName $letter",
                subtitle = "Unit #0$index",
                hp = 75 + (index * 15),
                attack = 14 + (index * 2),
                speed = 38 + (index * 4),
                spriteTint = when (index % 3) {
                    1 -> Color(0xFFEF5350)
                    2 -> Color(0xFFFFA726)
                    else -> Color(0xFFAB47BC)
                }
            )
        }
    }
}
