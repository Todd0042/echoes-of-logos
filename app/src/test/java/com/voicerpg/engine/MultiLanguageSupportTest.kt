package com.voicerpg.engine

import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.IntentParser
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.engine.SpellChantPresets
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.MetaCommand
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.model.Spell
import com.voicerpg.engine.model.SpellSchool
import com.voicerpg.engine.model.TargetSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MultiLanguageSupportTest {

    private lateinit var resonanceEngine: ResonanceEngine

    @Before
    fun setUp() {
        resonanceEngine = ResonanceEngine(NoveltyCache())
    }

    @Test
    fun testAllSixLanguageCatalogsLoaded() {
        val loadedCatalogs = GameContent.commandCatalogs
        val expectedLocales = listOf("en", "es", "de", "fr", "pt", "it")
        for (locale in expectedLocales) {
            assertTrue("Catalog for locale '$locale' must be loaded", loadedCatalogs.containsKey(locale))
            val catalog = loadedCatalogs.getValue(locale)
            assertTrue("Catalog '$locale' must contain meta commands", catalog.metaCommands.isNotEmpty())
            assertTrue("Catalog '$locale' must contain ordinals", catalog.ordinals.isNotEmpty())
            assertTrue("Catalog '$locale' must contain actions", catalog.actions.isNotEmpty())
            assertTrue("Catalog '$locale' must contain groups", catalog.groups.isNotEmpty())
        }
    }

    @Test
    fun testSpanishMetaCommandsAndEnglishFallback() {
        // Native Spanish commands
        assertEquals(MetaCommand.STATUS_REPORT, IntentParser.parse("informe de estado", locale = "es").metaCommand)
        assertEquals(MetaCommand.CHECK_ENEMIES, IntentParser.parse("enemigos", locale = "es").metaCommand)
        assertEquals(MetaCommand.CHECK_PARTY, IntentParser.parse("grupo", locale = "es").metaCommand)
        assertEquals(MetaCommand.OPEN_OPTIONS, IntentParser.parse("opciones", locale = "es").metaCommand)
        assertEquals(MetaCommand.TOGGLE_EYES_FREE, IntentParser.parse("modo bolsillo", locale = "es").metaCommand)
        assertEquals(MetaCommand.HELP, IntentParser.parse("ayuda", locale = "es").metaCommand)

        // English fallback while in Spanish mode
        assertEquals(MetaCommand.STATUS_REPORT, IntentParser.parse("status report", locale = "es").metaCommand)
        assertEquals(MetaCommand.OPEN_OPTIONS, IntentParser.parse("open options", locale = "es").metaCommand)
        assertEquals(MetaCommand.CHECK_ENEMIES, IntentParser.parse("check enemies", locale = "es").metaCommand)
    }

    @Test
    fun testGermanMetaCommandsAndEnglishFallback() {
        // Native German commands
        assertEquals(MetaCommand.STATUS_REPORT, IntentParser.parse("statusbericht", locale = "de").metaCommand)
        assertEquals(MetaCommand.CHECK_ENEMIES, IntentParser.parse("gegner", locale = "de").metaCommand)
        assertEquals(MetaCommand.CHECK_PARTY, IntentParser.parse("gruppe", locale = "de").metaCommand)
        assertEquals(MetaCommand.OPEN_OPTIONS, IntentParser.parse("optionen", locale = "de").metaCommand)
        assertEquals(MetaCommand.TOGGLE_EYES_FREE, IntentParser.parse("taschenmodus", locale = "de").metaCommand)
        assertEquals(MetaCommand.HELP, IntentParser.parse("hilfe", locale = "de").metaCommand)

        // English fallback while in German mode
        assertEquals(MetaCommand.OPEN_OPTIONS, IntentParser.parse("options", locale = "de").metaCommand)
        assertEquals(MetaCommand.STATUS_REPORT, IntentParser.parse("health", locale = "de").metaCommand)
    }

    @Test
    fun testFrenchMetaCommandsAndEnglishFallback() {
        // Native French commands
        assertEquals(MetaCommand.STATUS_REPORT, IntentParser.parse("statut", locale = "fr").metaCommand)
        assertEquals(MetaCommand.CHECK_ENEMIES, IntentParser.parse("ennemis", locale = "fr").metaCommand)
        assertEquals(MetaCommand.OPEN_OPTIONS, IntentParser.parse("options", locale = "fr").metaCommand)
        assertEquals(MetaCommand.TOGGLE_EYES_FREE, IntentParser.parse("mode poche", locale = "fr").metaCommand)
        assertEquals(MetaCommand.HELP, IntentParser.parse("aide", locale = "fr").metaCommand)

        // English fallback while in French mode
        assertEquals(MetaCommand.STATUS_REPORT, IntentParser.parse("status", locale = "fr").metaCommand)
    }

    @Test
    fun testPortugueseMetaCommandsAndEnglishFallback() {
        // Native Portuguese commands
        assertEquals(MetaCommand.STATUS_REPORT, IntentParser.parse("relatório de estado", locale = "pt").metaCommand)
        assertEquals(MetaCommand.CHECK_ENEMIES, IntentParser.parse("inimigos", locale = "pt").metaCommand)
        assertEquals(MetaCommand.OPEN_OPTIONS, IntentParser.parse("opções", locale = "pt").metaCommand)
        assertEquals(MetaCommand.TOGGLE_EYES_FREE, IntentParser.parse("modo bolso", locale = "pt").metaCommand)
        assertEquals(MetaCommand.HELP, IntentParser.parse("ajuda", locale = "pt").metaCommand)

        // English fallback while in Portuguese mode
        assertEquals(MetaCommand.CHECK_ENEMIES, IntentParser.parse("targets", locale = "pt").metaCommand)
    }

    @Test
    fun testItalianMetaCommandsAndEnglishFallback() {
        // Native Italian commands
        assertEquals(MetaCommand.STATUS_REPORT, IntentParser.parse("rapporto di stato", locale = "it").metaCommand)
        assertEquals(MetaCommand.CHECK_ENEMIES, IntentParser.parse("nemici", locale = "it").metaCommand)
        assertEquals(MetaCommand.OPEN_OPTIONS, IntentParser.parse("opzioni", locale = "it").metaCommand)
        assertEquals(MetaCommand.TOGGLE_EYES_FREE, IntentParser.parse("modalità tasca", locale = "it").metaCommand)
        assertEquals(MetaCommand.HELP, IntentParser.parse("aiuto", locale = "it").metaCommand)

        // English fallback while in Italian mode
        assertEquals(MetaCommand.OPEN_OPTIONS, IntentParser.parse("settings", locale = "it").metaCommand)
    }

    @Test
    fun testLocalizedOrdinalTargetingAcrossAllLanguages() {
        val enemies = listOf(
            Enemy(id = "e0", name = "Target Zero", subtitle = "Alpha", currentHp = 100, maxHp = 100, baseAttack = 10),
            Enemy(id = "e1", name = "Target One", subtitle = "Beta", currentHp = 100, maxHp = 100, baseAttack = 10),
            Enemy(id = "e2", name = "Target Two", subtitle = "Gamma", currentHp = 100, maxHp = 100, baseAttack = 10)
        )
        val fireball = Spell(id = "fireball", name = "Fireball", school = SpellSchool.PYROMANCY, basePower = 50, mpCost = 10, description = "A test fireball", exampleChant = "Fireball")

        // Spanish: segundo -> e1
        val intentEs = IntentParser.parse("Lanza Fireball al segundo enemigo", listOf(fireball), enemies, locale = "es")
        assertEquals("e1", intentEs.targetEnemyId)

        // German: dritter -> e2
        val intentDe = IntentParser.parse("Fireball auf den dritten Gegner", listOf(fireball), enemies, locale = "de")
        assertEquals("e2", intentDe.targetEnemyId)

        // French: premier -> e0
        val intentFr = IntentParser.parse("Fireball sur le premier ennemi", listOf(fireball), enemies, locale = "fr")
        assertEquals("e0", intentFr.targetEnemyId)

        // Portuguese: segundo -> e1
        val intentPt = IntentParser.parse("Fireball no segundo inimigo", listOf(fireball), enemies, locale = "pt")
        assertEquals("e1", intentPt.targetEnemyId)

        // Italian: terzo -> e2
        val intentIt = IntentParser.parse("Fireball sul terzo nemico", listOf(fireball), enemies, locale = "it")
        assertEquals("e2", intentIt.targetEnemyId)
    }

    @Test
    fun testLocalizedResonanceChantScoringAcrossAllTiers() {
        val testSpells = listOf(
            Spell("fireball", "Fireball", SpellSchool.PYROMANCY, 55, 15, description = "Test", exampleChant = "Fireball"),
            Spell("frost_spike", "Frost Spike", SpellSchool.CRYOMANCY, 50, 12, description = "Test", exampleChant = "Frost Spike"),
            Spell("chain_lightning", "Chain Lightning", SpellSchool.ELECTROMANCY, 45, 18, description = "Test", exampleChant = "Chain Lightning"),
            Spell("holy_smite", "Holy Smite", SpellSchool.HOLY, 60, 14, description = "Test", exampleChant = "Holy Smite"),
            Spell("briar_entangle", "Briar Entangle", SpellSchool.NATURE, 50, 12, description = "Test", exampleChant = "Briar Entangle"),
            Spell("shadow_strike", "Shadow Strike", SpellSchool.SHADOW, 70, 12, description = "Test", exampleChant = "Shadow Strike"),
            Spell("shield_wall", "Shield Wall", SpellSchool.PHYSICAL, 40, 10, description = "Test", exampleChant = "Shield Wall")
        )

        val languages = listOf("en", "es", "de", "fr", "pt", "it")
        val tiers = listOf(
            ResonanceTier.BASIC,
            ResonanceTier.ADEPT,
            ResonanceTier.MASTER,
            ResonanceTier.MYTHIC,
            ResonanceTier.TRANSCENDENTAL
        )

        for (lang in languages) {
            for (spell in testSpells) {
                for (expectedTier in tiers) {
                    val preset = SpellChantPresets.getPreset(spell, expectedTier, locale = lang)
                    val result = resonanceEngine.evaluate(
                        utterance = preset.chantText,
                        school = spell.school,
                        acousticProfile = preset.acousticProfile,
                        ignoreNoveltyDecay = true
                    )

                    val msg = "Lang '$lang' Spell '${spell.id}' Tier '$expectedTier' failed with result ${result.tier} (${result.bonusPercent}%) chant='${preset.chantText}'"

                    if (expectedTier == ResonanceTier.BASIC) {
                        assertTrue(msg, result.bonusPercent < 55)
                        assertTrue(msg, result.damageMultiplier in 1.0f..1.55f)
                    } else {
                        assertEquals(msg, expectedTier, result.tier)
                    }

                    if (expectedTier == ResonanceTier.TRANSCENDENTAL) {
                        assertTrue("Transcendental should achieve >= 155% ($msg)", result.bonusPercent >= 155)
                        assertTrue("Transcendental damage multiplier should be >= 2.55x ($msg)", result.damageMultiplier >= 2.55f)
                    }
                }
            }
        }
    }

    @Test
    fun testLanguageCatalogDeclaredLanguagesAndRegions() {
        val languages = com.voicerpg.engine.model.LanguageCatalog.SUPPORTED_LANGUAGES
        assertTrue(languages.size >= 6)

        val expectedCodes = setOf("en", "es", "de", "fr", "pt", "it")
        assertTrue(languages.map { it.code }.containsAll(expectedCodes))

        // Verify each language declares native names and regional options
        for (lang in languages) {
            assertTrue("Native name for ${lang.code} should not be blank", lang.nativeName.isNotBlank())
            assertTrue("Regions for ${lang.code} should not be empty", lang.regions.isNotEmpty())
            assertTrue("First region for ${lang.code} should be Any/Default ('')", lang.regions.first().code.isEmpty())
        }

        // Verify English includes previously excluded and global regions (India, Nigeria, etc.)
        val enRegions = com.voicerpg.engine.model.LanguageCatalog.getRegionsForLanguage("en").map { it.code }
        assertTrue(enRegions.containsAll(listOf("US", "GB", "CA", "AU", "IE", "IN", "NG", "ZA", "NZ")))

        // Verify Spanish regions
        val esRegions = com.voicerpg.engine.model.LanguageCatalog.getRegionsForLanguage("es").map { it.code }
        assertTrue(esRegions.containsAll(listOf("ES", "MX", "US", "AR", "CO")))

        // Verify Portuguese regions
        val ptRegions = com.voicerpg.engine.model.LanguageCatalog.getRegionsForLanguage("pt").map { it.code }
        assertTrue(ptRegions.containsAll(listOf("BR", "PT")))
    }

    @Test
    fun testCombatNarratorGetVoiceRegionCodeExtraction() {
        assertEquals("", com.voicerpg.engine.audio.CombatNarrator.getVoiceRegionCode(null))

        // Note: android.speech.tts.Voice is an Android framework class; we verify
        // region extraction via reflection / mock or via null safety & fallback guarantee.
        assertTrue(com.voicerpg.engine.audio.CombatNarrator.DEFAULT_ALLOWED_VOICE_REGIONS.contains("US"))
    }

    @Test
    fun testGameSaveDataLanguageAndRegionPersistence() {
        val saveManager = com.voicerpg.engine.engine.SaveManager()
        val custom = com.voicerpg.engine.model.PlayerCustomization(name = "Tester", title = "The Mage")

        val initialSave = saveManager.createInitialSave(
            customization = custom,
            slot = 1,
            selectedLanguage = "es",
            selectedRegion = "MX"
        )

        assertEquals("es", initialSave.selectedLanguage)
        assertEquals("MX", initialSave.selectedRegion)

        val loaded = saveManager.load(1)
        org.junit.Assert.assertNotNull(loaded)
        assertEquals("es", loaded?.selectedLanguage)
        assertEquals("MX", loaded?.selectedRegion)
    }

    @Test
    fun testStoryViewModelFirstLaunchPromptAndSelection() {
        val saveManager = com.voicerpg.engine.engine.SaveManager()
        // Simulate fresh install: language setup not yet completed
        saveManager.saveGlobalSettings(com.voicerpg.engine.engine.SaveManager.GlobalGameSettings(isLanguageSetupCompleted = false))
        val speechManager = com.voicerpg.engine.audio.SpeechManager(null)
        val combatNarrator = com.voicerpg.engine.audio.CombatNarrator(null)

        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val viewModel = com.voicerpg.engine.viewmodel.StoryViewModel(
            speechManager = speechManager,
            combatNarrator = combatNarrator,
            saveManager = saveManager,
            musicManager = null,
            scopeOverride = testScope
        )

        // On brand new launch (no save file), language selection screen must be the very first screen
        assertEquals(com.voicerpg.engine.model.GameScreen.LANGUAGE_SELECTION, viewModel.state.value.gameScreen)

        // Player confirms language and region
        viewModel.completeLanguageSetup("es", "MX")

        assertEquals(com.voicerpg.engine.model.GameScreen.TITLE, viewModel.state.value.gameScreen)
        assertEquals("es", viewModel.state.value.selectedLanguage)
        assertEquals("MX", viewModel.state.value.selectedRegion)
        org.junit.Assert.assertFalse(viewModel.state.value.showFirstLaunchLanguagePrompt)
        assertEquals("es", speechManager.selectedLocale.value)
        assertEquals("MX", speechManager.selectedRegion.value)
        assertEquals("es", combatNarrator.selectedLanguage.value)
        assertEquals("MX", combatNarrator.selectedRegion.value)
        assertEquals("es", com.voicerpg.engine.engine.IntentParser.currentLocale)
        assertTrue(saveManager.loadGlobalSettings().isLanguageSetupCompleted)
    }
}
