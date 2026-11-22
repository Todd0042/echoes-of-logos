package com.voicerpg.engine.engine

import com.voicerpg.engine.content.CharacterRole
import com.voicerpg.engine.content.GameContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class TutorialHeroLoadoutTest {

    @Before
    fun setUp() {
        val assetsDir = listOf(
            File("app/src/main/assets/game"),
            File("src/main/assets/game"),
            File("assets/game")
        ).firstOrNull { it.isDirectory }
        assertNotNull("Assets directory should be found", assetsDir)
        GameContent.reset()
        GameContent.initializeFromDirectory(assetsDir!!)
    }

    @Test
    fun testTutorialLoadsHeroCharacterFirst() {
        val heroChar = GameContent.heroCharacter
        assertNotNull("Game should have a hero character", heroChar)
        assertEquals(CharacterRole.HERO, heroChar!!.role)

        val tutorialChars = TutorialBattleContent.CHARACTERS
        assertTrue("Tutorial characters should not be empty", tutorialChars.isNotEmpty())

        val firstTutorialChar = tutorialChars.first()
        assertEquals(
            "The first tutorial character must match GameContent.heroCharacter",
            heroChar.id,
            firstTutorialChar.id
        )
        assertEquals(
            "The first tutorial character's name must match the hero",
            heroChar.speaker.name,
            firstTutorialChar.name
        )
    }

    @Test
    fun testTutorialStepsFeatureHero() {
        val heroChar = GameContent.heroCharacter!!
        val steps = TutorialBattleContent.STEPS
        assertTrue("Tutorial steps should not be empty", steps.isNotEmpty())

        // Find the CharacterJoin step
        val joinStep = steps.filterIsInstance<TutorialBattleStep.CharacterJoin>().firstOrNull()
        assertNotNull("Tutorial should have a CharacterJoin step", joinStep)
        assertEquals(heroChar.id, joinStep!!.characterDef.id)
        assertEquals(heroChar.speaker.name, joinStep.characterDef.name)

        // Find the SpellOverview step
        val overviewStep = steps.filterIsInstance<TutorialBattleStep.SpellOverview>().firstOrNull()
        assertNotNull("Tutorial should have a SpellOverview step", overviewStep)
        assertEquals(heroChar.id, overviewStep!!.characterDef.id)

        // Find PracticeCast steps - should cast hero's spells
        val castSteps = steps.filterIsInstance<TutorialBattleStep.PracticeCast>()
        assertTrue("Tutorial should have practice cast steps", castSteps.isNotEmpty())
        for (cast in castSteps) {
            assertEquals(heroChar.id, cast.characterId)
        }

        // AutoAction should be hero's breath spell
        val autoAction = steps.filterIsInstance<TutorialBattleStep.AutoAction>().firstOrNull()
        if (autoAction != null) {
            assertEquals(heroChar.id, autoAction.characterId)
            val breathSpell = heroChar.combat?.spells?.firstOrNull { it.manaRestorePct > 0f }
            if (breathSpell != null) {
                assertEquals(breathSpell.name, autoAction.hintChant)
            }
        }
    }
}
