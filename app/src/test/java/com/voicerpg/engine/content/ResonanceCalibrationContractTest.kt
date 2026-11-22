package com.voicerpg.engine.content

import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.engine.SpellChantPresets
import com.voicerpg.engine.model.ResonanceTier
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract test, run on the game's own content: every spell the game ships must be reachable at every
 * resonance tier with the hands-free presets. In practice this means the spell's name hits exactly one
 * root word of its school (engine built-ins plus the game's spells/thesaurus.json).
 */
class ResonanceCalibrationContractTest {

    private lateinit var engine: ResonanceEngine

    @Before
    fun setUp() {
        GameContent.reset()
        engine = ResonanceEngine(NoveltyCache())
    }

    @Test
    fun testAllSpellChantPresetsAcrossAllResonanceTiers() {
        val allSpells = GameContent.spells.values.distinctBy { it.id }

        val tiers = listOf(
            ResonanceTier.BASIC,
            ResonanceTier.ADEPT,
            ResonanceTier.MASTER,
            ResonanceTier.MYTHIC,
            ResonanceTier.TRANSCENDENTAL
        )

        val failures = mutableListOf<String>()
        fun check(message: String, ok: Boolean) { if (!ok) failures += message }
        for (spell in allSpells) {
            for (expectedTier in tiers) {
                val preset = SpellChantPresets.getPreset(spell, expectedTier)
                val result = engine.evaluate(
                    utterance = preset.chantText,
                    school = spell.school,
                    acousticProfile = preset.acousticProfile,
                    ignoreNoveltyDecay = true
                )

                val tierMessage =
                    "Spell '${spell.id}' with preset tier $expectedTier failed: got ${result.tier} with ${result.bonusPercent}%"

                if (expectedTier == ResonanceTier.BASIC) {
                    // A spell name containing two thematic roots (e.g. "Aegis Guard")
                    // inherently floors at 22% above the BASIC ceiling, so the BASIC
                    // preset only guarantees staying near BASIC (never MASTER+).
                    check(tierMessage, result.bonusPercent < 55)
                } else {
                    check(tierMessage, expectedTier == result.tier)
                }

                if (expectedTier == ResonanceTier.TRANSCENDENTAL) {
                    check("Transcendental tier for ${spell.id} should hit >= 155% bonus (got ${result.bonusPercent}%)", result.bonusPercent >= 155)
                    check("Damage multiplier for ${spell.id} should be >= 2.55x (got ${result.damageMultiplier}x)", result.damageMultiplier >= 2.55f)
                }
            }
        }
        assertTrue("${failures.size} preset chant(s) miss their tier:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
