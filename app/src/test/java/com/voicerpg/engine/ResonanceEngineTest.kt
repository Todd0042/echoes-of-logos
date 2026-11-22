package com.voicerpg.engine

import org.junit.Rule
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.engine.IntentParser
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.engine.SpellChantPresets
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.model.AcousticProfile
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.model.Spell
import com.voicerpg.engine.model.SpellSchool
import com.voicerpg.engine.model.TargetSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResonanceEngineTest {

    /** Mechanics test: runs on the engine's frozen template content, whatever game it's in. */
    @get:Rule val templateFixture = TemplateFixture()

    private lateinit var engine: ResonanceEngine
    private lateinit var testSpells: List<Spell>

    @Before
    fun setUp() {
        engine = ResonanceEngine(NoveltyCache())
        testSpells = listOf(
            Spell("fireball", "Fireball", SpellSchool.PYROMANCY, 55, 15, false, false, "Fireball", "Fireball archer"),
            Spell("frost_spike", "Frost Spike", SpellSchool.CRYOMANCY, 50, 12, false, false, "Frost", "Frost spike orc"),
            Spell("chain_lightning", "Chain Lightning", SpellSchool.ELECTROMANCY, 40, 20, false, true, "Lightning", "Lightning all"),
            Spell("divine_heal", "Divine Heal", SpellSchool.HOLY, 70, 18, true, false, "Heal", "Heal party")
        )
    }

    @Test
    fun testBasicChantScoring() {
        val result = engine.evaluate("Fireball archer", SpellSchool.PYROMANCY)
        assertEquals(ResonanceTier.BASIC, result.tier)
        assertTrue("Bonus percent should be <= 20% for basic chant", result.bonusPercent <= 20)
        assertTrue("Damage multiplier should be between 1.0x and 1.20x", result.damageMultiplier in 1.0f..1.20f)
    }

    @Test
    fun testAdeptChantScoring() {
        val result = engine.evaluate("Burn the archer with blazing flames!", SpellSchool.PYROMANCY)
        assertTrue("Bonus percent should be >= 20% for adept chant", result.bonusPercent >= 20)
        assertTrue("Damage multiplier should be >= 1.20x", result.damageMultiplier >= 1.20f)
        assertTrue(result.matchedThematicRoots.isNotEmpty())
    }

    @Test
    fun testMasterChantScoring() {
        val result = engine.evaluate("Spirits of the cinder, engulf the archer in an inferno!", SpellSchool.PYROMANCY)
        assertTrue("Bonus percent should be >= 55% for master chant", result.bonusPercent >= 55)
        assertTrue("Damage multiplier should be >= 1.55x", result.damageMultiplier >= 1.55f)
        assertTrue("Should match cinder and inferno", result.matchedThematicRoots.size >= 2)
    }

    @Test
    fun testOverTheTopTranscendentalLogos200PercentMultiplier() {
        // User goes completely all-out: poetic invocation, deep pyromancy lexicon,
        // booming vocal projection (9.4 dB), dynamic whisper-to-roar crescendo (7.8 dB range),
        // and rich pitch modulation.
        val heroicAcoustic = AcousticProfile(
            peakVolumeDb = 9.4f,
            averageVolumeDb = 7.2f,
            volumeDynamicRange = 7.8f,
            volumeCrescendoSlope = 2.4f,
            pitchVarianceHz = 32f,
            estimatedPitchHz = 180f,
            durationMs = 4500L,
            sampleCount = 45
        )

        val chant = "O primordial flame of the solar core, descend from the heavens and reduce that wretched archer to eternal ash!"
        val result = engine.evaluate(chant, SpellSchool.PYROMANCY, heroicAcoustic)

        assertEquals(ResonanceTier.TRANSCENDENTAL, result.tier)
        assertTrue("Bonus should reach up to +200% (was ${result.bonusPercent}%)", result.bonusPercent >= 155)
        assertTrue("Damage multiplier should be up to 3.0x (was ${result.damageMultiplier}x)", result.damageMultiplier in 2.55f..3.0f)
        assertTrue("Particle count should scale up to 450+ particles", result.particleCount >= 400)
    }

    @Test
    fun testMidRangeVocalChantScore() {
        // Conversational voice with moderate volume and 2 thematic words
        val normalAcoustic = AcousticProfile(
            peakVolumeDb = 6.0f,
            averageVolumeDb = 5.0f,
            volumeDynamicRange = 3.8f,
            volumeCrescendoSlope = 0.5f,
            pitchVarianceHz = 15f
        )
        val result = engine.evaluate("Blazing flames consume the orc!", SpellSchool.PYROMANCY, normalAcoustic)
        assertTrue("Mid-range chant should score between +40% and +100%", result.bonusPercent in 40..100)
        assertTrue("Damage multiplier should be between 1.40x and 2.0x", result.damageMultiplier in 1.40f..2.0f)
    }

    @Test
    fun testNoveltyCacheDecay() {
        val chant = "Engulf the archer in an inferno of blazing embers!"

        val first = engine.evaluate(chant, SpellSchool.PYROMANCY)
        assertEquals(1.0f, first.repetitionDecayApplied, 0.01f)
        assertTrue(first.isNovel)

        val second = engine.evaluate(chant, SpellSchool.PYROMANCY)
        assertEquals(0.5f, second.repetitionDecayApplied, 0.01f)
        assertFalse(second.isNovel)
        assertTrue(second.bonusPercent < first.bonusPercent)

        val third = engine.evaluate(chant, SpellSchool.PYROMANCY)
        assertEquals(0.25f, third.repetitionDecayApplied, 0.01f)
        assertTrue(third.bonusPercent < second.bonusPercent)
    }

    @Test
    fun testIntentParserTargetAndSpell() {
        val orc = Enemy(
            id = "orc",
            name = "Orc",
            subtitle = "Vanguard",
            currentHp = 100,
            maxHp = 340,
            baseAttack = 22
        )
        val intent1 = IntentParser.parse("Frost spike the orc", testSpells, activeEnemies = listOf(orc))
        assertEquals("frost_spike", intent1.spell.id)
        assertEquals(TargetSelection.SPECIFIC_ENEMY, intent1.target)
        assertEquals("orc", intent1.targetEnemyId)

        val intent2 = IntentParser.parse("Chain lightning all enemies", testSpells)
        assertEquals("chain_lightning", intent2.spell.id)
        assertEquals(TargetSelection.ALL_ENEMIES, intent2.target)

        val intent3 = IntentParser.parse("Celestial radiance and blessing, heal the party", testSpells)
        assertEquals("divine_heal", intent3.spell.id)
        assertEquals(TargetSelection.PARTY_LOWEST, intent3.target)
    }

    @Test
    fun testCedricLayOnHandsAndLyraSoothingRain() {
        val cedricSpells = listOf(
            Spell("holy_smite", "Holy Smite", SpellSchool.HOLY, 62, 14, false, false, "Smite", "Smite heretic"),
            Spell("lay_on_hands", "Lay on Hands", SpellSchool.HOLY, 75, 16, true, false, "Heal", "Mend Cedric")
        )
        val cedricParty = listOf(
            PartyMember(
                id = "cedric",
                name = "Cedric",
                loreClass = "Templar",
                currentHp = 100,
                maxHp = 320,
                currentMp = 110,
                maxMp = 110,
                spells = cedricSpells
            )
        )
        val intentCedric = IntentParser.parse("Sacred radiance mend Cedric's wounds", cedricSpells, party = cedricParty)
        assertEquals("lay_on_hands", intentCedric.spell.id)
        assertEquals(TargetSelection.SPECIFIC_HERO, intentCedric.target)
        assertEquals("cedric", intentCedric.targetHeroId)
        assertTrue(intentCedric.spell.isHeal)

        val lyraSpells = listOf(
            Spell("soothing_rain", "Soothing Rain", SpellSchool.NATURE, 55, 18, true, true, "Heal", "Soothing rain party"),
            Spell("briar_entangle", "Briar Entangle", SpellSchool.NATURE, 52, 12, false, false, "Snare", "Briar archer")
        )
        val intentLyra = IntentParser.parse("Spirits of the grove grant soothing rain upon our party", lyraSpells)
        assertEquals("soothing_rain", intentLyra.spell.id)
        assertEquals(TargetSelection.PARTY_LOWEST, intentLyra.target)
        assertTrue(intentLyra.spell.isHeal)
    }

    @Test
    fun testAethelChainLightningAndZephyrShadowStrike() {
        val aethelSpells = listOf(
            Spell("fireball", "Fireball", SpellSchool.PYROMANCY, 65, 15, false, false, "Fireball", "Fireball archer"),
            Spell("chain_lightning", "Chain Lightning", SpellSchool.ELECTROMANCY, 48, 20, false, true, "Lightning", "Tempest lightning strike all enemies!")
        )
        val intentAethel = IntentParser.parse("Tempest lightning strike all enemies!", aethelSpells)
        assertEquals("chain_lightning", intentAethel.spell.id)
        assertEquals(TargetSelection.ALL_ENEMIES, intentAethel.target)

        val zephyrSpells = listOf(
            Spell("shadow_strike", "Shadow Strike", SpellSchool.SHADOW, 75, 12, false, false, "Backstab", "Strike from umbra"),
            Spell("venom_flurry", "Venom Flurry", SpellSchool.SHADOW, 50, 15, false, true, "Poison", "Abyssal venom coat my blades")
        )
        val shaman = Enemy(
            id = "shaman",
            name = "Orc Shaman",
            subtitle = "Hexcaller",
            currentHp = 80,
            maxHp = 200,
            baseAttack = 18
        )
        val intentZephyr = IntentParser.parse("From the silent umbra, strike the shaman's throat!", zephyrSpells, activeEnemies = listOf(shaman))
        assertEquals("shadow_strike", intentZephyr.spell.id)
        assertEquals(TargetSelection.SPECIFIC_ENEMY, intentZephyr.target)
        assertEquals("shaman", intentZephyr.targetEnemyId)
    }

    @Test
    fun testZephyrVenomFlurryAndUmbralSiphonPresetsResolveToTheirOwnSpells() {
        val zephyrSpells = listOf(
            Spell("shadow_strike", "Shadow Strike", SpellSchool.SHADOW, 75, 12, false, false, "Backstab", "From the silent umbra, strike the shaman's throat!"),
            Spell("venom_flurry", "Venom Flurry", SpellSchool.SHADOW, 50, 15, false, true, "Poison", "Abyssal venom coat my blades"),
            Spell("umbral_siphon", "Umbral Siphon", SpellSchool.SHADOW, 60, 14, true, false, "Siphon", "Abyssal siphon mend Zephyr's wounds!")
        )

        ResonanceTier.entries.forEach { tier ->
            val venomPreset = SpellChantPresets.getPreset(zephyrSpells[1], tier)
            val venomIntent = IntentParser.parse(venomPreset.chantText, zephyrSpells)
            assertEquals("venom_flurry resolved to ${venomIntent.spell.id}", "venom_flurry", venomIntent.spell.id)

            val siphonPreset = SpellChantPresets.getPreset(zephyrSpells[2], tier)
            val siphonIntent = IntentParser.parse(siphonPreset.chantText, zephyrSpells)
            assertEquals("umbral_siphon resolved to ${siphonIntent.spell.id}", "umbral_siphon", siphonIntent.spell.id)
            assertTrue("Umbral Siphon must be a heal for tier $tier", siphonIntent.spell.isHeal)
        }
    }

    @Test
    fun testDeadCombatantsZeroResources() {
        val deadHero = PartyMember(
            id = "hero",
            name = "Aethel",
            loreClass = "Elementalist",
            currentHp = 0,
            maxHp = 240,
            currentMp = 140,
            maxMp = 140,
            spells = emptyList(),
            atbGauge = 0.95f
        )
        assertFalse(deadHero.isAlive)
        assertEquals(0f, deadHero.hpRatio)
        assertEquals(0f, deadHero.mpRatio)
        assertEquals(0f, deadHero.atbRatio)
        assertFalse(deadHero.isTurnReady)

        val deadEnemy = Enemy(
            id = "orc",
            name = "Orc",
            subtitle = "Vanguard",
            currentHp = 0,
            maxHp = 340,
            baseAttack = 22,
            atbGauge = 0.85f
        )
        assertFalse(deadEnemy.isAlive)
        assertEquals(0f, deadEnemy.hpRatio)
        assertEquals(0f, deadEnemy.atbRatio)
        assertFalse(deadEnemy.isTurnReady)
    }

    @Test
    fun testCheaterHudRepeatTapsDoNotDecay() {
        val spell = Spell("fireball", "Fireball", SpellSchool.PYROMANCY, 65, 15, false, false, "Flame", "Fireball")
        val transcendentalPreset = SpellChantPresets.getPreset(spell, ResonanceTier.TRANSCENDENTAL)

        val first = engine.evaluate(
            utterance = transcendentalPreset.chantText,
            school = spell.school,
            acousticProfile = transcendentalPreset.acousticProfile,
            ignoreNoveltyDecay = true
        )
        assertEquals(ResonanceTier.TRANSCENDENTAL, first.tier)
        assertTrue(first.bonusPercent >= 155)

        // Repeat tap from Cheater HUD with ignoreNoveltyDecay = true
        val second = engine.evaluate(
            utterance = transcendentalPreset.chantText,
            school = spell.school,
            acousticProfile = transcendentalPreset.acousticProfile,
            ignoreNoveltyDecay = true
        )
        assertEquals("Repeat tap with ignoreNoveltyDecay should maintain Transcendental tier", ResonanceTier.TRANSCENDENTAL, second.tier)
        assertEquals(first.bonusPercent, second.bonusPercent)
    }
}
