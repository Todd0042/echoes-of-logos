package com.voicerpg.engine.template

import androidx.compose.ui.graphics.Color
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.Spell
import com.voicerpg.engine.model.SpellSchool

/**
 * ========================================================================
 * CREATOR TEMPLATE: Playable Heroes and Starter Spells
 * ========================================================================
 * Use this template to create playable heroes, companions, and custom spell kits in Kotlin.
 */
object TemplateHero {

    val POWER_STRIKE = Spell(
        id = "template_strike",
        name = "Power Strike",
        school = SpellSchool.PHYSICAL,
        basePower = 55,
        mpCost = 0,
        description = "A swift physical blow that requires no mana.",
        exampleChant = "Power strike!",
        aliases = listOf("strike", "power strike", "attack", "slash", "hit")
    )

    val FLAME_SPARK = Spell(
        id = "template_flame_spark",
        name = "Flame Spark",
        school = SpellSchool.PYROMANCY,
        basePower = 70,
        mpCost = 15,
        description = "Unleashes searing magical flames against a single enemy.",
        exampleChant = "Flame spark!",
        aliases = listOf("flame", "spark", "flame spark", "fire", "burn")
    )

    val BLIZZARD_BURST = Spell(
        id = "template_blizzard_burst",
        name = "Blizzard Burst",
        school = SpellSchool.CRYOMANCY,
        basePower = 45,
        mpCost = 25,
        hitsAll = true,
        description = "Sweeps the entire enemy flank with freezing frost.",
        exampleChant = "Blizzard burst!",
        aliases = listOf("blizzard", "burst", "blizzard burst", "freeze all", "ice storm")
    )

    val MEND = Spell(
        id = "template_mend",
        name = "Mend",
        school = SpellSchool.HOLY,
        basePower = 60,
        mpCost = 20,
        isHeal = true,
        description = "Restores health to an injured party member.",
        exampleChant = "Mend wounds!",
        aliases = listOf("mend", "heal", "cure", "recover", "restore")
    )

    val INNER_FOCUS = Spell(
        id = "template_inner_focus",
        name = "Inner Focus",
        school = SpellSchool.ELECTROMANCY,
        basePower = 0,
        mpCost = 0,
        manaRestorePct = 0.35f,
        description = "Focuses breathing and incantation clarity to restore 35% MP.",
        exampleChant = "Inner focus!",
        aliases = listOf("focus", "inner focus", "meditate", "mana restore", "clarity")
    )

    val SHIELD_BASH = Spell(
        id = "template_shield_bash",
        name = "Shield Bash",
        school = SpellSchool.PHYSICAL,
        basePower = 40,
        mpCost = 10,
        status = "STUN",
        description = "Slams the enemy with a heavy shield, stunning them.",
        exampleChant = "Shield bash!",
        aliases = listOf("shield bash", "bash", "stun", "slam")
    )

    val STANDARD_SPELLS = listOf(
        POWER_STRIKE,
        FLAME_SPARK,
        BLIZZARD_BURST,
        MEND,
        INNER_FOCUS
    )

    fun createTemplateAdventurer(
        id: String = "hero",
        name: String = "Adventurer",
        currentHp: Int = 120,
        maxHp: Int = 120,
        currentMp: Int = 50,
        maxMp: Int = 50
    ): PartyMember {
        return PartyMember(
            id = id,
            name = name,
            loreClass = "Apprentice Invocator",
            currentHp = currentHp,
            maxHp = maxHp,
            currentMp = currentMp,
            maxMp = maxMp,
            speed = 55,
            defense = 12,
            avatarTint = Color(0xFFFFD700),
            spells = STANDARD_SPELLS
        )
    }

    fun createTemplateMage(
        id: String = "mage",
        name: String = "Mage Companion",
        currentHp: Int = 90,
        maxHp: Int = 90,
        currentMp: Int = 90,
        maxMp: Int = 90
    ): PartyMember {
        return PartyMember(
            id = id,
            name = name,
            loreClass = "Elementalist",
            currentHp = currentHp,
            maxHp = maxHp,
            currentMp = currentMp,
            maxMp = maxMp,
            speed = 50,
            defense = 8,
            avatarTint = Color(0xFF00E5FF),
            spells = listOf(FLAME_SPARK, BLIZZARD_BURST, INNER_FOCUS)
        )
    }

    fun createTemplateCleric(
        id: String = "cleric",
        name: String = "Cleric Companion",
        currentHp: Int = 100,
        maxHp: Int = 100,
        currentMp: Int = 70,
        maxMp: Int = 70
    ): PartyMember {
        return PartyMember(
            id = id,
            name = name,
            loreClass = "Priest",
            currentHp = currentHp,
            maxHp = maxHp,
            currentMp = currentMp,
            maxMp = maxMp,
            speed = 48,
            defense = 14,
            avatarTint = Color(0xFF76FF03),
            spells = listOf(MEND, POWER_STRIKE, INNER_FOCUS)
        )
    }
}
