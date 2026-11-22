package com.voicerpg.engine.audio

import com.voicerpg.engine.content.GameContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class TtsNormalizationTest {

    @Test
    fun testStAugustineNormalization() {
        val assetsDir = listOf(
            File("app/src/main/assets/game"),
            File("src/main/assets/game"),
            File("assets/game")
        ).firstOrNull { it.isDirectory }
        assertNotNull("Assets directory should be found", assetsDir)

        GameContent.reset()
        GameContent.initializeFromDirectory(assetsDir!!)

        val narrator = CombatNarrator()

        val input1 = "Welcome to St. Augustine, Florida."
        val output1 = narrator.normalizeTtsText(input1)
        assertEquals("Welcome to Saint Augustine, Florida.", output1)

        val input2 = "The commute into St. Augustine was brutal."
        val output2 = narrator.normalizeTtsText(input2)
        assertEquals("The commute into Saint Augustine was brutal.", output2)

        val input3 = "We arrived in St Augustine without sleep."
        val output3 = narrator.normalizeTtsText(input3)
        assertEquals("We arrived in Saint Augustine without sleep.", output3)

        val input4 = "Drive past St. Johns river."
        val output4 = narrator.normalizeTtsText(input4)
        assertEquals("Drive past Saint Johns river.", output4)
    }
}
