package com.voicerpg.engine.engine

import androidx.compose.ui.graphics.Color
import com.voicerpg.engine.content.GameCharacter
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.DialogueSpeaker
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.SpellSchool

data class TutorialSpellDef(
    val name: String,
    val school: SpellSchool,
    val basePower: Int,
    val mpCost: Int,
    val isHeal: Boolean = false,
    val isGuard: Boolean = false,
    val hitsAll: Boolean = false,
    val manaRestorePct: Float = 0f,
    val description: String,
    val aliases: List<String> = emptyList()
) {
    val category: com.voicerpg.engine.model.SpellCategory
        get() = when {
            isHeal -> com.voicerpg.engine.model.SpellCategory.HEAL
            manaRestorePct > 0f -> com.voicerpg.engine.model.SpellCategory.MANA
            isGuard || (basePower == 0 && !hitsAll) -> com.voicerpg.engine.model.SpellCategory.DEFENSE
            hitsAll -> com.voicerpg.engine.model.SpellCategory.AOE_ATTACK
            else -> com.voicerpg.engine.model.SpellCategory.ATTACK
        }
}

data class TutorialCharacterDef(
    val id: String,
    val name: String,
    val title: String,
    val speaker: DialogueSpeaker,
    val loreClass: String,
    val maxHp: Int,
    val maxMp: Int,
    val speed: Int,
    val avatarTint: Color,
    val spells: List<TutorialSpellDef>
)

sealed class TutorialBattleStep {
    data class IntroPopup(
        val text: String,
        val speaker: DialogueSpeaker = GameContent.narrator
    ) : TutorialBattleStep()

    data class CharacterJoin(
        val characterDef: TutorialCharacterDef,
        val text: String
    ) : TutorialBattleStep()

    data class SpellOverview(
        val characterDef: TutorialCharacterDef,
        val text: String
    ) : TutorialBattleStep()

    data class PracticeCast(
        val characterId: String,
        val spellName: String,
        val school: SpellSchool,
        val promptText: String,
        val isResonanceDemo: Boolean = false,
        val resonanceDemoIndex: Int = 0
    ) : TutorialBattleStep()

    data class AutoAction(
        val characterId: String,
        val actionText: String,
        val resultText: String,
        val hintChant: String = "Attune"
    ) : TutorialBattleStep()

    data class EnemyAttackDemo(
        val introText: String,
        val resultText: String
    ) : TutorialBattleStep()

    data class CompletionPopup(
        val text: String
    ) : TutorialBattleStep()
}

/**
 * Tutorial content assembled at runtime from the active game's data files:
 * the template hero character (assets/game/characters/<name>.json), their spells
 * (assets/game/spells/spells.json), and the practice-dummy enemy template
 * (assets/game/enemies/enemies.json). No characters or spells are hardcoded.
 */
object TutorialBattleContent {

    val CHARACTERS: List<TutorialCharacterDef>
        get() {
            val hero = GameContent.heroCharacter?.takeIf { it.combat != null }
            val others = GameContent.characters.values
                .filter { it.combat != null && it.role != com.voicerpg.engine.content.CharacterRole.NARRATOR && it.id != hero?.id }
            return listOfNotNull(hero?.toTutorialDef()) + others.map { it.toTutorialDef() }
        }

    private fun GameCharacter.toTutorialDef(): TutorialCharacterDef {
        val combat = combat!!
        return TutorialCharacterDef(
            id = id,
            name = speaker.name,
            title = speaker.title,
            speaker = speaker,
            loreClass = loreClass,
            maxHp = combat.maxHp,
            maxMp = combat.maxMp,
            speed = combat.speed,
            avatarTint = combat.avatarTint,
            spells = combat.spells.map {
                TutorialSpellDef(
                    name = it.name,
                    school = it.school,
                    basePower = it.basePower,
                    mpCost = it.mpCost,
                    isHeal = it.isHeal,
                    isGuard = it.isGuard,
                    hitsAll = it.hitsAll,
                    manaRestorePct = it.manaRestorePct,
                    description = it.description,
                    aliases = it.aliases
                )
            }
        )
    }

    val ENEMIES: List<Enemy>
        get() {
            val dummy = GameContent.enemyTemplateById("practice_dummy")
            return listOf(
                dummyId("dummy_alpha", "Practice Dummy Alpha", dummy),
                dummyId("dummy_beta", "Practice Dummy Beta", dummy),
                dummyId("dummy_gamma", "Practice Dummy Gamma", dummy)
            )
        }

    private fun dummyId(id: String, name: String, template: com.voicerpg.engine.content.EnemyTemplate?): Enemy =
        template?.toEnemy(instanceId = id, nameOverride = name) ?: Enemy(
            id = id,
            name = name,
            subtitle = "Training Target",
            currentHp = 2000,
            maxHp = 2000,
            baseAttack = 0,
            speed = 30,
            spriteId = "practice_dummy",
            family = "CONSTRUCT"
        )

    /** Tutorial script generated from the template character's actual spell loadout. */
    val STEPS: List<TutorialBattleStep>
        get() = buildSteps()

    private fun buildSteps(): List<TutorialBattleStep> {
        val hero = CHARACTERS.firstOrNull()
        val narrator = GameContent.narrator
        if (hero == null) {
            return listOf(
                TutorialBattleStep.IntroPopup(
                    text = "No playable characters are defined. Add a character file under assets/game/characters/ to enable the tutorial.",
                    speaker = narrator
                ),
                TutorialBattleStep.CompletionPopup(text = "Tutorial unavailable until character content is added.")
            )
        }

        val heroName = hero.name
        val offensiveSpells = hero.spells.filter { !it.isHeal && !it.isGuard && it.manaRestorePct == 0f }
        val breathSpell = hero.spells.firstOrNull { it.manaRestorePct > 0f }
        val demoSpell = offensiveSpells.firstOrNull() ?: hero.spells.firstOrNull()

        val steps = mutableListOf<TutorialBattleStep>()

        steps += TutorialBattleStep.IntroPopup(
            text = "Welcome to the Training Arena. Here, practice dummies stand ready for you to test your incantations. You cannot be harmed. Speak your commands freely.",
            speaker = narrator
        )
        steps += TutorialBattleStep.IntroPopup(
            text = "In real combat, you will speak voice commands to cast spells. The more vivid and dramatic your words, the more powerful your magic becomes. Let us begin.",
            speaker = narrator
        )
        steps += TutorialBattleStep.CharacterJoin(
            characterDef = hero,
            text = "Meet $heroName, the ${loreTitle(hero)}. Every hero in your game is defined by a character data file: name, voice, portrait, stats, and spells all live together in one place."
        )
        steps += TutorialBattleStep.SpellOverview(
            characterDef = hero,
            text = "Here are ${heroName}'s spells."
        )

        for (spell in offensiveSpells.take(3)) {
            steps += TutorialBattleStep.PracticeCast(
                characterId = hero.id,
                spellName = spell.name,
                school = spell.school,
                promptText = buildCastPrompt(spell.name, spell.hitsAll)
            )
        }

        if (breathSpell != null) {
            steps += TutorialBattleStep.AutoAction(
                characterId = hero.id,
                actionText = "Every hero has a free breath ability. Say '${breathSpell.name}' to restore mana. It costs nothing and lets you keep casting.",
                resultText = "${heroName}'s mana has been restored. Breath abilities are essential for sustaining long battles.",
                hintChant = breathSpell.name
            )
        }

        if (demoSpell != null) {
            steps += TutorialBattleStep.IntroPopup(
                text = "Now witness the Resonance System. Your spells grow stronger with more vivid incantation. First, say just '${demoSpell.name}' -- nothing more.",
                speaker = narrator
            )
            steps += TutorialBattleStep.PracticeCast(
                characterId = hero.id,
                spellName = demoSpell.name,
                school = demoSpell.school,
                promptText = "Say '${demoSpell.name}' now. Keep it simple.",
                isResonanceDemo = true,
                resonanceDemoIndex = 0
            )
            steps += TutorialBattleStep.PracticeCast(
                characterId = hero.id,
                spellName = demoSpell.name,
                school = demoSpell.school,
                promptText = "Now cast ${demoSpell.name} again, but this time use elaborate, dramatic language. Describe the energy, the impact, the spectacle. Be creative.",
                isResonanceDemo = true,
                resonanceDemoIndex = 1
            )
            steps += TutorialBattleStep.IntroPopup(
                text = "See the difference? Elaborate, vivid incantations unleash far more power than simple spell names. The resonance engine rewards those who speak with conviction.",
                speaker = narrator
            )
        }

        steps += TutorialBattleStep.EnemyAttackDemo(
            introText = "Now observe -- enemies fight back too! The dummies will take a turn. Don't worry, your party can withstand it.",
            resultText = "In real combat, enemies will strike back on their own turn. Use healing spells, guards, and smart strategy to keep your party alive."
        )
        steps += TutorialBattleStep.CompletionPopup(
            text = "Well done. You now know the basics of commanding your party. Every spell grows stronger with vivid, dramatic incantation. Your journey awaits."
        )
        return steps
    }

    private fun loreTitle(hero: TutorialCharacterDef): String =
        hero.loreClass.ifBlank { hero.title.ifBlank { "Adventurer" } }

    private fun buildCastPrompt(spellName: String, hitsAll: Boolean): String =
        if (hitsAll) {
            "$spellName strikes all enemies at once. Say the spell name."
        } else {
            "Try casting $spellName. Say the spell name to strike the training dummy."
        }

    fun getCharacter(id: String): TutorialCharacterDef? =
        CHARACTERS.firstOrNull { it.id == id }

    val totalSteps: Int get() = STEPS.size
}
