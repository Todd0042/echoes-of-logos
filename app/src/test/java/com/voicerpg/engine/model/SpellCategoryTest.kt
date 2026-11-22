package com.voicerpg.engine.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SpellCategoryTest {

    @Test
    fun testSingleTargetAttack() {
        val spell = Spell(
            id = "test_attack",
            name = "Test Attack",
            school = SpellSchool.PHYSICAL,
            basePower = 40,
            mpCost = 0,
            description = "Basic attack",
            exampleChant = "attack!"
        )
        assertEquals(SpellCategory.ATTACK, spell.category)
        assertEquals("ATK", spell.category.label)
        assertEquals("⚔️", spell.category.icon)
    }

    @Test
    fun testAoeAttack() {
        val spell = Spell(
            id = "test_aoe",
            name = "Test AoE",
            school = SpellSchool.PYROMANCY,
            basePower = 55,
            mpCost = 12,
            hitsAll = true,
            description = "AoE attack",
            exampleChant = "burn all!"
        )
        assertEquals(SpellCategory.AOE_ATTACK, spell.category)
        assertEquals("AOE", spell.category.label)
        assertEquals("💥", spell.category.icon)
    }

    @Test
    fun testHeal() {
        val spell = Spell(
            id = "test_heal",
            name = "Test Heal",
            school = SpellSchool.HOLY,
            basePower = 50,
            mpCost = 15,
            isHeal = true,
            description = "Restore health",
            exampleChant = "heal!"
        )
        assertEquals(SpellCategory.HEAL, spell.category)
        assertEquals("HEAL", spell.category.label)
        assertEquals("💚", spell.category.icon)
    }

    @Test
    fun testDefensiveGuard() {
        val spell = Spell(
            id = "test_guard",
            name = "Test Guard",
            school = SpellSchool.PHYSICAL,
            basePower = 0,
            mpCost = 10,
            isGuard = true,
            description = "Guard stance",
            exampleChant = "guard!"
        )
        assertEquals(SpellCategory.DEFENSE, spell.category)
        assertEquals("DEF", spell.category.label)
        assertEquals("🛡️", spell.category.icon)
    }

    @Test
    fun testManaRestore() {
        val spell = Spell(
            id = "test_breathe",
            name = "Breathe",
            school = SpellSchool.PHYSICAL,
            basePower = 0,
            mpCost = 0,
            manaRestorePct = 0.35f,
            description = "Restore mana",
            exampleChant = "breathe"
        )
        assertEquals(SpellCategory.MANA, spell.category)
        assertEquals("MANA", spell.category.label)
        assertEquals("✨", spell.category.icon)
    }
}
