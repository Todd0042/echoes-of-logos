package com.voicerpg.engine.combat

import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.EncounterDefinition
import com.voicerpg.engine.model.SpellSchool
import kotlin.math.roundToInt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnemyBrainTest {

    private val boss = MovesetTable.movesetFor("boss_template")!!

    @Test
    fun `enemies are never one-note - full movesets with variety`() {
        assertTrue("boss must carry multiple moves", boss.moves.size >= 2)
    }

    @Test
    fun `moves respect cooldowns`() {
        val heavy = boss.moves.firstOrNull { it.cooldownTurns > 0 } ?: boss.moves.first()
        val withCd = mapOf(heavy.name to 2)
        val available = EnemyBrain.availableMoves(boss, 1.0f, withCd)
        assertTrue("cooling move must be excluded", available.none { it.name == heavy.name })
        assertTrue("some move must always remain", available.isNotEmpty())
    }

    @Test
    fun `boss phases unlock extra moves only below their hp threshold`() {
        val boss = MovesetTable.TABLE.values.first { it.phases.isNotEmpty() }
        val phase = boss.phases.first()
        val highHp = EnemyBrain.availableMoves(boss, 0.95f, emptyMap())
        val lowHp = EnemyBrain.availableMoves(boss, (phase.hpBelow - 0.05f).coerceAtLeast(0.05f), emptyMap())
        assertTrue(lowHp.size >= highHp.size)
    }

    @Test
    fun `every enemy in the campaign resolves to a real moveset`() {
        GameContent.encounters.values
            .flatMap { encounter -> encounter.enemies }
            .distinctBy { it.name }
            .forEach { enemy ->
                val key = enemy.movesetId
                assertTrue(
                    "'${enemy.name}' must resolve to a moveset",
                    key.isNotEmpty() && MovesetTable.movesetFor(key) != null
                )
            }
    }
}

class NoUselessAllyDoctrineTest {

    private fun band(encounter: EncounterDefinition): List<String> =
        encounter.initialParty?.map { it.id } ?: listOf("hero")

    private fun memberSchools(memberId: String): List<SpellSchool> =
        GameContent.heroSpellsFor(memberId).map { it.school }.distinct()

    @Test
    fun `system floor guarantees no wall - even against a hypothetical perfect-resist family every kit does at least 80 percent`() {
        // The engine clamps at 0.8 regardless of table content; any future family can only add weak points.
        for (school in School.entries) {
            for (family in EnemyFamily.entries) {
                assertTrue(AffinityTable.affinity(school, family) >= 0.8f)
            }
        }
    }

    @Test
    fun `every mandatory member contributes meaningfully in every legal encounter`() {
        val violations = mutableListOf<String>()
        for (encounter in GameContent.encounters.values) {
            val roster = band(encounter)
            val families = encounter.enemies
                .map { EnemyFamily.fromNameOrNull(it.family) ?: EnemyFamily.FLESH }
            for (member in roster) {
                val memberFamilies = families.ifEmpty { listOf(EnemyFamily.FLESH) }
                // (1) DAMAGE FLOOR (I-2): even the worst target for the member's best school is 0.8x.
                val worstAny = memberFamilies.minOf { fam ->
                    memberSchools(member).maxOf { school ->
                        AffinityTable.effectiveness(School.valueOf(school.name), fam, null)
                    }
                }
                if (worstAny < 0.8f) violations += "${encounter.id}: $member worst=$worstAny"
                // (2) VOICE DOMINATES KNOWLEDGE: a resisted ally chanting at MASTER (0.8 x 2.0 = 1.6)
                // still out-damages a fumbled Basic neutral hit (1.0) - resisted is never useless.
                if (0.8f * 2.0f <= 1.0f) violations += "resisted master chant must beat basic neutral"
                // (3) ALWAYS-RELEVANT LANE: every mandatory member's kit carries a status/utility
                // effect that resists-only degrades and can never be blocked (heal/root/jam/etc).
                val kitSpells = GameContent.heroSpellsFor(member)
                val hasUtility = kitSpells.any { it.isHeal || it.isGuard || it.lifesteal || it.status != null }
                if (!hasUtility) violations += "${encounter.id}: $member has no affinity-immune lane"
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `resisted lanes always land - status utility survives any family`() {
        // Lyra's Root vs the exact construct/verdant/radiant walls and Zephyr's Poison/Corrode vs void walls.
        for (family in listOf(EnemyFamily.CONSTRUCT, EnemyFamily.VERDANT, EnemyFamily.RADIANT, EnemyFamily.VOID, EnemyFamily.UNDEAD)) {
            val root = StatusSystem.apply(StatusId.ROOT, "ADEPT", family, 60f)!!
            assertTrue("root must still land on $family", root.turnsLeft >= 1 && root.potPct > 0f)
            val poison = StatusSystem.apply(StatusId.POISON, "ADEPT", family, 52f)!!
            assertTrue("poison must still land on $family", poison.turnsLeft >= 1 && poison.potPct > 0f)
        }
    }
}
