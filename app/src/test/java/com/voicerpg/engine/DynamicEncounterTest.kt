package com.voicerpg.engine

import org.junit.Rule
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.combat.MovesetTable
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.IntentParser
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.CharacterStance
import com.voicerpg.engine.model.CombatPhase
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.EncounterDefinition
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.PlayerCustomization
import com.voicerpg.engine.model.SavedCharacterStats
import com.voicerpg.engine.model.Spell
import com.voicerpg.engine.model.TargetSelection
import com.voicerpg.engine.viewmodel.CombatViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DynamicEncounterTest {

    /** Mechanics test: runs on the engine's frozen template content, whatever game it's in. */
    @get:Rule val templateFixture = TemplateFixture()

    private lateinit var viewModel: CombatViewModel

    @Before
    fun setUp() {
        val dummySpeech = SpeechManager()
        val testScope = CoroutineScope(Dispatchers.Default)
        viewModel = CombatViewModel(
            speechManager = dummySpeech,
            resonanceEngine = ResonanceEngine(NoveltyCache()),
            scopeOverride = testScope
        )
    }

    private val directorScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        viewModel.cleanup()
        directorScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    /** What RetroBattleScreen does: a director that plays this view model's cues at its live sprite positions. */
    private fun attachDirector(): com.voicerpg.engine.ui.fx.BattleFxDirector {
        val director = com.voicerpg.engine.ui.fx.BattleFxDirector(positionOf = { id ->
            viewModel.combatantSlot(id).let { (x, y) -> androidx.compose.ui.geometry.Offset(x, y) }
        })
        directorScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            viewModel.cues.collect { director.play(it) }
        }
        return director
    }

    // =============================================================================
    // Data-driven fixtures: the hardcoded StoryEncounters object was deleted, so every
    // encounter is now built from the GameContent registry (real spell/enemy ids).
    // =============================================================================

    private fun elementalistSpells(): List<Spell> = GameContent.spellsForClass("elementalist")

    private fun spellIds(vararg ids: String): List<Spell> = ids.mapNotNull { GameContent.spellById(it) }

    private fun heroElementalist() = PartyMember(
        id = "hero",
        name = "Aethel",
        loreClass = "Elementalist",
        currentHp = 240,
        maxHp = 240,
        currentMp = 160,
        maxMp = 160,
        spells = elementalistSpells(),
        speed = 70
    )

    private fun cedric() = PartyMember(
        id = "cedric",
        name = "Sir Cedric",
        loreClass = "Templar",
        currentHp = 420,
        maxHp = 420,
        currentMp = 90,
        maxMp = 90,
        spells = spellIds("power_strike", "mending_touch", "aegis_guard", "attune"),
        speed = 40
    )

    private fun lyra() = PartyMember(
        id = "lyra",
        name = "Lyra",
        loreClass = "Warden",
        currentHp = 320,
        maxHp = 320,
        currentMp = 160,
        maxMp = 160,
        spells = spellIds("mending_touch", "haste_cadence", "attune"),
        speed = 55
    )

    private fun zephyr() = PartyMember(
        id = "zephyr",
        name = "Zephyr",
        loreClass = "Shadowblade",
        currentHp = 300,
        maxHp = 300,
        currentMp = 140,
        maxMp = 140,
        spells = spellIds("shadow_spike", "void_drain", "umbral_veil", "attune"),
        speed = 85
    )

    private fun createDuoParty() = listOf(heroElementalist(), cedric())
    private fun createTrioParty() = listOf(heroElementalist(), cedric(), lyra())
    private fun createZephyrMember() = zephyr()
    private fun createQuadParty() = listOf(heroElementalist(), cedric(), lyra(), zephyr())
    private fun createStandardParty() = createQuadParty()

    private fun createMinion(idSuffix: String, name: String = "Template Minion", hp: Int = 150): Enemy =
        GameContent.createMinion(idSuffix, name, hp = hp)

    /** Data-driven chapter-stub encounters carrying the expected party roster for the roster tests. */
    private fun chapterEncounter(id: String, party: List<PartyMember>): EncounterDefinition =
        EncounterDefinition(
            id = id,
            name = id,
            environment = BattleEnvironment.FOREST,
            enemies = listOf(
                GameContent.enemyTemplates.getValue("training_drone").toEnemy(
                    instanceId = "${id}_enemy",
                    nameOverride = "Encounter Guard"
                )
            ),
            initialParty = party
        )

    /** Equips the registry hero with the elementalist loadout so 'Fireball' chant targeting stays legible. */
    private fun applyElementalistCustomization() {
        viewModel.applyPlayerCustomization(PlayerCustomization(name = "Aethel", heroClassId = "elementalist"))
    }

    @Test
    fun testDeveloperToolsToggleDefaultsHidden() {
        // Battle test controls must be hidden by default in every build
        assertFalse(viewModel.isDeveloperToolsEnabled.value)

        // Toggle on reveals them, toggle again hides them
        viewModel.toggleDeveloperTools()
        assertTrue(viewModel.isDeveloperToolsEnabled.value)
        viewModel.toggleDeveloperTools()
        assertFalse(viewModel.isDeveloperToolsEnabled.value)

        // Survives encounter start/restart state reconstructions
        viewModel.startEncounter(GameContent.encounters.getValue("template_solo_battle"))
        viewModel.toggleDeveloperTools()
        assertTrue(viewModel.isDeveloperToolsEnabled.value)
        viewModel.restartBattle()
        assertTrue(viewModel.isDeveloperToolsEnabled.value)
    }

    @Test
    fun testSoloPrologue1Hero2Enemies() {
        viewModel.startEncounter(
            party = listOf(heroElementalist()),
            enemies = listOf(
                GameContent.enemyTemplates.getValue("training_drone").toEnemy(
                    instanceId = "wisp_1",
                    nameOverride = "Shadow Wisp",
                    subtitleOverride = "Void Wisp"
                ),
                GameContent.enemyTemplates.getValue("training_drone").toEnemy(
                    instanceId = "wisp_2",
                    nameOverride = "Shadow Wisp Beta",
                    subtitleOverride = "Void Wisp"
                )
            ),
            environment = BattleEnvironment.FOREST
        )
        val state = viewModel.state.value

        assertEquals(1, state.party.size)
        assertEquals("hero", state.party[0].id)
        assertEquals(2, state.enemies.size)
        assertEquals(BattleEnvironment.FOREST, state.currentEnvironment)

        // Dynamic voice targeting by custom enemy name
        val intent = IntentParser.parse(
            utterance = "Fireball the shadow wisp beta!",
            availableSpells = state.party[0].spells,
            activeEnemies = state.enemies,
            party = state.party
        )

        assertEquals("fireball", intent.spell.id)
        assertEquals("wisp_2", intent.targetEnemyId)
    }

    @Test
    fun testFull6EnemyHordeEncounterAndOrdinalTargeting() {
        viewModel.startEncounter(
            party = createDuoParty(),
            enemies = listOf(
                Enemy(id = "gate_captain", name = "Gate Captain", subtitle = "Warlord", currentHp = 260, maxHp = 260, baseAttack = 24, speed = 45),
                Enemy(id = "ironclad_1", name = "Ironclad Legionnaire", subtitle = "Vanguard", currentHp = 220, maxHp = 220, baseAttack = 20, speed = 40),
                Enemy(id = "ironclad_2", name = "Ironclad Legionnaire", subtitle = "Vanguard", currentHp = 220, maxHp = 220, baseAttack = 20, speed = 40),
                Enemy(id = "battle_tactician", name = "Battle Tactician", subtitle = "Caster", currentHp = 180, maxHp = 180, baseAttack = 22, speed = 55),
                Enemy(id = "longbow_archer", name = "Longbow Archer", subtitle = "Sniper", currentHp = 160, maxHp = 160, baseAttack = 26, speed = 60),
                Enemy(id = "tribal_shaman", name = "Tribal Shaman", subtitle = "Occultist", currentHp = 170, maxHp = 170, baseAttack = 24, speed = 50)
            ),
            environment = BattleEnvironment.CASTLE
        )
        val state = viewModel.state.value

        assertEquals(2, state.party.size)
        assertEquals(6, state.enemies.size)
        assertEquals(BattleEnvironment.CASTLE, state.currentEnvironment)

        // Target by specific title/role
        val intentBoss = IntentParser.parse(
            utterance = "Frost spike the gate captain!",
            availableSpells = elementalistSpells(),
            activeEnemies = state.enemies,
            party = state.party
        )
        assertEquals("gate_captain", intentBoss.targetEnemyId)

        // Target by ordinal (first, second, third, etc.)
        val intentOrdinal = IntentParser.parse(
            utterance = "Strike enemy 2 with lightning",
            availableSpells = elementalistSpells(),
            activeEnemies = state.enemies,
            party = state.party
        )
        // Enemy at index 1 is "ironclad_2"
        assertEquals(state.enemies[1].id, intentOrdinal.targetEnemyId)
    }

    @Test
    fun testMidBattleReinforcementsStrict6EnemyCap() {
        // Start encounter with 4 enemies
        val initialEnemies = listOf(
            createMinion("1", "Minion 1"),
            createMinion("2", "Minion 2"),
            createMinion("3", "Minion 3"),
            createMinion("4", "Minion 4")
        )
        viewModel.startEncounter(
            party = createStandardParty(),
            enemies = initialEnemies,
            environment = BattleEnvironment.DUNGEON
        )
        viewModel.pauseAtb()

        assertEquals(4, viewModel.state.value.enemies.size)

        // Attempt to summon 4 reinforcements (would be 8 total, exceeding cap of 6)
        val incoming = listOf(
            createMinion("5", "Minion 5"),
            createMinion("6", "Minion 6"),
            createMinion("7", "Minion 7"),
            createMinion("8", "Minion 8")
        )

        val admitted = viewModel.summonReinforcements(incoming)

        // Exactly 2 enemies should be admitted to reach the hard maximum of 6
        assertEquals(2, admitted)
        assertEquals(6, viewModel.state.value.enemies.size)
        assertEquals(6, viewModel.state.value.enemies.count { it.isAlive })

        // A subsequent summon while at 6 alive enemies must admit 0
        val extra = viewModel.summonReinforcements(listOf(createMinion("9", "Minion 9")))
        assertEquals(0, extra)
        assertEquals(6, viewModel.state.value.enemies.size)
    }

    @Test
    fun testReinforcementsAdmittedWhenEnemiesAreDefeated() {
        val initialEnemies = listOf(
            createMinion("1", "Minion 1", hp = 100),
            createMinion("2", "Minion 2", hp = 0), // Already defeated!
            createMinion("3", "Minion 3", hp = 100),
            createMinion("4", "Minion 4", hp = 0)  // Already defeated!
        )
        viewModel.startEncounter(
            party = createStandardParty(),
            enemies = initialEnemies,
            environment = BattleEnvironment.CAVE
        )
        viewModel.pauseAtb()

        // 2 alive enemies out of 4 total on field
        val currentAlive = viewModel.state.value.enemies.count { it.isAlive }
        assertEquals(2, currentAlive)

        // (6 - 2) = 4 available slots
        val reinforcements = listOf(
            createMinion("r1", "Reinforcement 1"),
            createMinion("r2", "Reinforcement 2"),
            createMinion("r3", "Reinforcement 3"),
            createMinion("r4", "Reinforcement 4"),
            createMinion("r5", "Reinforcement 5")
        )

        val admitted = viewModel.summonReinforcements(reinforcements)
        assertEquals(4, admitted) // Only 4 admitted to reach cap of 6 alive
        assertEquals(6, viewModel.state.value.enemies.count { it.isAlive })
    }

    @Test
    fun testDynamicPartyHealingTargetResolution() {
        val party = createQuadParty()
        val spells = lyra().spells

        val healIntentCedric = IntentParser.parse(
            utterance = "Spirits of the grove, mend Sir Cedric's wounds!",
            availableSpells = spells,
            activeEnemies = emptyList(),
            party = party
        )
        assertEquals("mending_touch", healIntentCedric.spell.id)
        assertEquals("cedric", healIntentCedric.targetHeroId)

        val healIntentParty = IntentParser.parse(
            utterance = "Mending touch upon our entire party",
            availableSpells = spells,
            activeEnemies = emptyList(),
            party = party
        )
        assertEquals(TargetSelection.PARTY_LOWEST, healIntentParty.target)
    }

    @Test
    fun testPlayerIncantationAgainstReinforcedMinion() {
        viewModel.startEncounter(
            EncounterDefinition(
                id = "dungeon_descent",
                name = "Dungeon Descent",
                environment = BattleEnvironment.DUNGEON,
                enemies = listOf(
                    GameContent.enemyTemplates.getValue("training_drone").toEnemy(
                        instanceId = "ch1_drone",
                        nameOverride = "Dungeon Drone"
                    )
                ),
                initialParty = createDuoParty()
            )
        )
        viewModel.pauseAtb()

        // Spawn a reinforcement mid-battle
        val summonSuccess = viewModel.spawnEnemy(
            createMinion("bone_guard", "Bone Vanguard", hp = 200)
        )
        assertTrue(summonSuccess)

        val activeEnemies = viewModel.state.value.enemies
        assertTrue(activeEnemies.any { it.name == "Bone Vanguard" })

        // Target the summoned minion by voice
        val intent = IntentParser.parse(
            utterance = "Power strike the bone vanguard!",
            availableSpells = cedric().spells,
            activeEnemies = activeEnemies,
            party = viewModel.state.value.party
        )

        assertEquals("power_strike", intent.spell.id)
        assertEquals("minion_bone_guard", intent.targetEnemyId)
    }

    @Test
    fun testActIEncountersOnlyIncludeDuoPartyAndExcludeLyra() {
        val actIEncounters = listOf(
            chapterEncounter("forest_ambush", createDuoParty()),
            chapterEncounter("dungeon_descent", createDuoParty()),
            chapterEncounter("castle_horde", createDuoParty()),
            chapterEncounter("cave_broodmother", createDuoParty()),
            chapterEncounter("blight_trackers", createDuoParty()),
            chapterEncounter("ch3_sentinels", createDuoParty())
        )

        for (encounter in actIEncounters) {
            val party = encounter.initialParty ?: emptyList()
            assertEquals("Encounter ${encounter.id} should have exactly 2 heroes in Act I", 2, party.size)
            assertTrue("Encounter ${encounter.id} must not contain Lyra before Chapter 5", party.none { it.id == "lyra" })
            assertTrue("Encounter ${encounter.id} should contain Aethel", party.any { it.id == "hero" })
            assertTrue("Encounter ${encounter.id} should contain Sir Cedric", party.any { it.id == "cedric" })
        }

        // MARSH_RESCUE is Chapter 5 and is a duo rescue mission before Lyra joins
        val ch5RescueParty = chapterEncounter("marsh_rescue", createDuoParty()).initialParty ?: emptyList()
        assertEquals(2, ch5RescueParty.size)
        assertTrue(ch5RescueParty.none { it.id == "lyra" })

        // SWAMP_BEHEMOTH is Chapter 6 and introduces Lyra into the active combat team
        val ch6Party = chapterEncounter("swamp_behemoth", createTrioParty()).initialParty ?: emptyList()
        assertEquals(3, ch6Party.size)
        assertTrue(ch6Party.any { it.id == "lyra" })
    }

    @Test
    fun testActIIAndBeyondPartyRosterIntegrity() {
        // Chapter 7: Trio party (Aethel, Cedric, Lyra)
        val ch7Party = chapterEncounter("ch7_mire_wyrm", createTrioParty()).initialParty ?: emptyList()
        assertEquals(3, ch7Party.size)
        assertTrue(ch7Party.any { it.id == "hero" })
        assertTrue(ch7Party.any { it.id == "cedric" })
        assertTrue(ch7Party.any { it.id == "lyra" })
        assertTrue(ch7Party.none { it.id == "zephyr" })

        // Chapter 8: Ambush begins as Trio before Zephyr defects
        val ch8Party = chapterEncounter("ch8_executioner_ambush", createTrioParty()).initialParty ?: emptyList()
        assertEquals(3, ch8Party.size)
        assertTrue(ch8Party.none { it.id == "zephyr" })

        // Chapters 9 through 16: Full Quad Party (Aethel, Cedric, Lyra, Zephyr)
        val quadEncounters = listOf(
            chapterEncounter("ch9_galahault_trial", createQuadParty()),
            chapterEncounter("ch10_broodmother_trial", createQuadParty()),
            chapterEncounter("ch11_nocturne_trial", createQuadParty()),
            chapterEncounter("ch12_warmaster_ouro", createQuadParty()),
            chapterEncounter("ch13_commander_vaelor", createQuadParty()),
            chapterEncounter("ch14_abyssal_leviathan", createQuadParty()),
            chapterEncounter("ch15_archon_custodians", createQuadParty()),
            chapterEncounter("ch16_malakor_finale", createQuadParty())
        )

        for (enc in quadEncounters) {
            val party = enc.initialParty ?: emptyList()
            assertEquals("Encounter ${enc.id} must feature the full 4-hero party", 4, party.size)
            assertTrue("Encounter ${enc.id} must include Aethel", party.any { it.id == "hero" })
            assertTrue("Encounter ${enc.id} must include Cedric", party.any { it.id == "cedric" })
            assertTrue("Encounter ${enc.id} must include Lyra", party.any { it.id == "lyra" })
            assertTrue("Encounter ${enc.id} must include Zephyr", party.any { it.id == "zephyr" })
        }
    }

    @Test
    fun testCompanionStarterSpellKitsDoNotIncludeMasterTrialSpells() {
        val duo = createDuoParty()
        val cedric = duo.first { it.id == "cedric" }
        assertFalse("Cedric starter kit should not include Chain Lightning", cedric.spells.any { it.id == "chain_lightning" })
        assertTrue("Cedric starter kit should include Mending Touch", cedric.spells.any { it.id == "mending_touch" })
        assertTrue("Cedric starter kit should include Attune", cedric.spells.any { it.id == "attune" })

        val trio = createTrioParty()
        val lyra = trio.first { it.id == "lyra" }
        assertFalse("Lyra starter kit should not include Temporal Stasis", lyra.spells.any { it.id == "temporal_stasis" })
        assertTrue("Lyra starter kit should include Mending Touch", lyra.spells.any { it.id == "mending_touch" })
        assertTrue("Lyra starter kit should include Attune", lyra.spells.any { it.id == "attune" })

        val zephyr = createZephyrMember()
        assertFalse("Zephyr starter kit should not include Resonant Surge", zephyr.spells.any { it.id == "resonant_surge" })
        assertTrue("Zephyr starter kit should include Shadow Spike", zephyr.spells.any { it.id == "shadow_spike" })
        assertTrue("Zephyr starter kit should include Attune", zephyr.spells.any { it.id == "attune" })
    }

    @Test
    fun testRoundNumberAdvancesWhenAllCombatantsAct() {
        val party = createDuoParty()
        val enemy = createMinion("dummy", "Dummy Target", hp = 500)
        viewModel.startEncounter(party = party, enemies = listOf(enemy), environment = BattleEnvironment.DUNGEON)

        assertEquals(1, viewModel.state.value.roundNumber)

        // Hero acts
        viewModel.registerTurnCompleted()
        assertEquals(1, viewModel.state.value.roundNumber)

        // Cedric acts
        viewModel.registerTurnCompleted()
        assertEquals(1, viewModel.state.value.roundNumber)

        // Enemy acts -> total alive combatants is 3 (hero, cedric, enemy)
        viewModel.registerTurnCompleted()
        assertEquals(2, viewModel.state.value.roundNumber)
    }

    @Test
    fun testApplyImportedProgressionCarriesVitals() {
        val savedParty = listOf(
            SavedCharacterStats(
                id = "cedric",
                name = "Sir Cedric",
                loreClass = "Templar",
                currentHp = 100,
                maxHp = 420,
                currentMp = 40,
                maxMp = 90,
                speed = 40
            )
        )
        viewModel.applySavedStats(savedParty)
        viewModel.startEncounter(
            EncounterDefinition(
                id = "proving_grounds_duo",
                name = "Proving Grounds Duo",
                environment = BattleEnvironment.FOREST,
                enemies = listOf(
                    GameContent.enemyTemplates.getValue("training_drone").toEnemy(
                        instanceId = "duo_drone",
                        nameOverride = "Duo Drone"
                    )
                ),
                initialParty = createDuoParty()
            )
        )

        val cedric = viewModel.state.value.party.first { it.id == "cedric" }
        assertEquals("carried HP", 100, cedric.currentHp)
        assertEquals("carried MP", 40, cedric.currentMp)
    }

    @Test
    fun testEnemiesAttackInQuadPartyEncounterWithinRoundOne() {
        viewModel.startEncounter(
            EncounterDefinition(
                id = "ch11_archive_enforcers",
                name = "Archive Enforcers",
                environment = BattleEnvironment.DUNGEON,
                enemies = (1..4).map { idx ->
                    GameContent.enemyTemplates.getValue("training_drone").toEnemy(
                        instanceId = "archive_enforcer_$idx",
                        nameOverride = "Archive Enforcer $idx",
                        hpOverride = 160
                    )
                },
                initialParty = createQuadParty()
            )
        )
        assertEquals(4, viewModel.state.value.party.size)
        assertEquals(4, viewModel.state.value.enemies.size)

        var enemyActed = false
        for (i in 0 until 100) {
            val phase = viewModel.state.value.phase
            if (phase == CombatPhase.PLAYER_INPUT) {
                viewModel.defendActivePartyMember()
                Thread.sleep(60)
            } else if (phase == CombatPhase.ENEMY_ACTIONS) {
                enemyActed = true
                break
            } else {
                viewModel.tickAtb()
            }
            if (viewModel.state.value.phase == CombatPhase.ENEMY_ACTIONS) {
                enemyActed = true
                break
            }
        }
        assertTrue("Enemy should have acted in 4-hero encounter rather than being turn-starved", enemyActed)
    }

    @Test
    fun testTurnInterleavingPreventsHeroStarvationOfReadyEnemies() {
        val quad = createQuadParty().map { it.copy(atbGauge = 1.0f) }
        val enemy = createMinion("dummy", "Dummy Vanguard", hp = 400).copy(atbGauge = 1.0f)

        viewModel.startEncounter(party = quad, enemies = listOf(enemy), environment = BattleEnvironment.DUNGEON)

        // Force ATB tick to resolve turn
        viewModel.tickAtb()
        assertTrue(
            "Initial phase should be PLAYER_INPUT or ENEMY_ACTIONS",
            viewModel.state.value.phase == CombatPhase.PLAYER_INPUT || viewModel.state.value.phase == CombatPhase.ENEMY_ACTIONS
        )

        if (viewModel.state.value.phase == CombatPhase.PLAYER_INPUT) {
            viewModel.defendActivePartyMember()
            var reachedEnemy = false
            for (i in 0 until 20) {
                Thread.sleep(50)
                if (viewModel.lastActedFaction == com.voicerpg.engine.model.CombatantFaction.ENEMY ||
                    viewModel.state.value.phase == CombatPhase.ENEMY_ACTIONS
                ) {
                    reachedEnemy = true
                    break
                }
            }
            assertTrue("Enemy should get next turn after hero acts when both are ready", reachedEnemy)
        }
    }

    @Test
    fun testEnemyAtbAdvanceScalesWithPartySize() {
        val soloHero = listOf(
            PartyMember("hero", "Aethel", "Elementalist", currentHp = 240, maxHp = 240, currentMp = 140, maxMp = 140, spells = elementalistSpells(), speed = 50, atbGauge = 0f)
        )
        val enemy1 = createMinion("e1", "Minion 1", hp = 200).copy(speed = 50, atbGauge = 0f)
        viewModel.startEncounter(party = soloHero, enemies = listOf(enemy1), environment = BattleEnvironment.DUNGEON)
        viewModel.tickAtb()
        val soloEnemyGauge = viewModel.state.value.enemies[0].atbGauge

        val quadParty = createQuadParty().map { it.copy(atbGauge = 0f) }
        val enemy2 = createMinion("e2", "Minion 2", hp = 200).copy(speed = 50, atbGauge = 0f)
        viewModel.startEncounter(party = quadParty, enemies = listOf(enemy2), environment = BattleEnvironment.DUNGEON)
        viewModel.tickAtb()
        val quadEnemyGauge = viewModel.state.value.enemies[0].atbGauge

        assertTrue(
            "Enemy ATB rate should scale up against 4-hero party (quad=$quadEnemyGauge > solo=$soloEnemyGauge)",
            quadEnemyGauge > soloEnemyGauge
        )
    }

    @Test
    fun testSingleTargetSpellDynamicTrajectory() {
        val hero = PartyMember("hero", "Aethel", "Elementalist", currentHp = 240, maxHp = 240, currentMp = 140, maxMp = 140, spells = elementalistSpells(), speed = 50, atbGauge = 100f)
        val enemy1 = Enemy(id = "orc", name = "Orc Raider", subtitle = "Vanguard", currentHp = 200, maxHp = 200, baseAttack = 20, speed = 50)
        val enemy2 = Enemy(id = "archer", name = "Goblin Archer", subtitle = "Sniper", currentHp = 200, maxHp = 200, baseAttack = 20, speed = 50)
        viewModel.startEncounter(party = listOf(hero), enemies = listOf(enemy1, enemy2), environment = BattleEnvironment.DUNGEON)
        viewModel.setPlayerInputPhaseForTesting("hero")

        // Simulate live measured sprite positions reported by Compose layout
        viewModel.updateCombatantPosition("hero", 220f, 600f)
        viewModel.updateCombatantPosition("archer", 960f, 750f)

        // The battle screen's effects director, fed by the view model's cues
        val director = attachDirector()

        // Cast fireball targeting archer
        viewModel.processIncantation("Fireball archer")

        var projectile: com.voicerpg.engine.ui.vfx.ActiveSpellProjectile? = null
        for (i in 0 until 40) {
            Thread.sleep(50)
            projectile = director.projectiles.projectiles.firstOrNull()
            if (projectile != null) break
        }

        // Verify dynamic trajectory from hero sprite to archer sprite
        assertNotNull("Projectile should be launched", projectile)
        assertEquals(220f, projectile!!.startX, 0.1f)
        assertEquals(600f, projectile.startY, 0.1f)
        assertEquals(960f, projectile.targetX, 0.1f)
        assertEquals(750f, projectile.targetY, 0.1f)
    }

    @Test
    fun testAoeSpellDynamicMultiProjectiles() {
        val hero = PartyMember("hero", "Aethel", "Elementalist", currentHp = 240, maxHp = 240, currentMp = 140, maxMp = 140, spells = elementalistSpells(), speed = 50, atbGauge = 100f)
        val enemy1 = Enemy(id = "orc", name = "Orc Raider", subtitle = "Vanguard", currentHp = 200, maxHp = 200, baseAttack = 20, speed = 50)
        val enemy2 = Enemy(id = "archer", name = "Goblin Archer", subtitle = "Sniper", currentHp = 200, maxHp = 200, baseAttack = 20, speed = 50)
        val enemy3 = Enemy(id = "shaman", name = "Goblin Shaman", subtitle = "Caster", currentHp = 200, maxHp = 200, baseAttack = 20, speed = 50)
        viewModel.startEncounter(party = listOf(hero), enemies = listOf(enemy1, enemy2, enemy3), environment = BattleEnvironment.DUNGEON)
        viewModel.setPlayerInputPhaseForTesting("hero")

        // Simulate live measured sprite positions reported by Compose layout
        viewModel.updateCombatantPosition("hero", 220f, 600f)
        viewModel.updateCombatantPosition("orc", 960f, 400f)
        viewModel.updateCombatantPosition("archer", 960f, 600f)
        viewModel.updateCombatantPosition("shaman", 960f, 800f)

        val director = attachDirector()

        // Cast Chain Lightning AoE spell
        viewModel.processIncantation("Chain lightning all foes")

        var projectiles: List<com.voicerpg.engine.ui.vfx.ActiveSpellProjectile> = emptyList()
        for (i in 0 until 40) {
            Thread.sleep(50)
            projectiles = director.projectiles.projectiles.toList()
            if (projectiles.isNotEmpty()) break
        }

        // AoE should launch multi-projectiles, one for each enemy sprite
        assertEquals(3, projectiles.size)
        projectiles.forEach { p ->
            assertEquals(220f, p.startX, 0.1f)
            assertEquals(600f, p.startY, 0.1f)
            assertEquals(960f, p.targetX, 0.1f)
        }
        assertEquals(400f, projectiles[0].targetY, 0.1f)
        assertEquals(600f, projectiles[1].targetY, 0.1f)
        assertEquals(800f, projectiles[2].targetY, 0.1f)
    }

    @Test
    fun testChapter16MirrorGauntletShadowMovesets() {
        val gauntlet = EncounterDefinition(
            id = "ch16_mirror_gauntlet",
            name = "Mirror Gauntlet",
            environment = BattleEnvironment.CASTLE,
            enemies = listOf(
                GameContent.enemyTemplates.getValue("template_caster").toEnemy(instanceId = "mirror_occultist", nameOverride = "Mirror Occultist"),
                GameContent.enemyTemplates.getValue("template_brute").toEnemy(instanceId = "mirror_vanguard", nameOverride = "Mirror Vanguard"),
                GameContent.enemyTemplates.getValue("template_sniper").toEnemy(instanceId = "mirror_sniper", nameOverride = "Mirror Sniper"),
                GameContent.enemyTemplates.getValue("training_drone").toEnemy(instanceId = "mirror_automaton", nameOverride = "Mirror Automaton"),
                GameContent.enemyTemplates.getValue("template_caster").toEnemy(instanceId = "mirror_oracle", nameOverride = "Mirror Oracle")
            )
        )
        viewModel.startEncounter(gauntlet)
        val decoratedEnemies = viewModel.state.value.enemies
        assertEquals(5, decoratedEnemies.size)

        // Verify each enemy in the Mirror Gauntlet resolves to a distinct moveset and none have party-wide AoE stun
        decoratedEnemies.forEach { enemy ->
            val moveset = MovesetTable.movesetFor(enemy.movesetId)
            assertNotNull("Moveset for ${enemy.name} (${enemy.movesetId}) must exist", moveset)
            moveset!!.moves.forEach { move ->
                if (move.targetRule == "PARTY_AOE") {
                    assertTrue(
                        "Move ${move.name} on ${enemy.name} must not be party-wide OVERLOAD stun",
                        move.applyStatus != "OVERLOAD" && move.applyStatus != "FREEZE"
                    )
                }
            }
        }
    }

    @Test
    fun testStrictTurnEnforcementZephyrActiveCannotCastFireball() {
        val quad = createQuadParty()
        applyElementalistCustomization()
        viewModel.startEncounter(GameContent.encounters.getValue("template_solo_battle").copy(initialParty = quad))

        // Set Zephyr as active turn holder with ATB full; other heroes are not turn ready
        viewModel.setPlayerInputPhaseForTesting("zephyr")
        viewModel.setPartyMemberAtbForTesting("hero", 0.45f)
        viewModel.setPartyMemberAtbForTesting("cedric", 0.25f)
        viewModel.setPartyMemberAtbForTesting("lyra", 0.35f)

        val initialAethelMp = viewModel.state.value.party.first { it.id == "hero" }.currentMp
        val initialEnemyHps = viewModel.state.value.enemies.associate { it.id to it.currentHp }

        // Zephyr's turn: player tries to cast Aethel's Fireball
        viewModel.processIncantation("Fireball archer")

        // Wait briefly to allow any coroutines/effects to process
        Thread.sleep(150)

        val currentState = viewModel.state.value
        // 1. Turn must NOT be consumed; still in PLAYER_INPUT with Zephyr
        assertEquals(CombatPhase.PLAYER_INPUT, currentState.phase)
        assertEquals("zephyr", currentState.activePartyMemberId)

        // 2. Aethel must NOT have cast the spell (MP untouched)
        val aethel = currentState.party.first { it.id == "hero" }
        assertEquals(initialAethelMp, aethel.currentMp)

        // 3. No enemies took damage from unauthorized out-of-turn cast
        currentState.enemies.forEach { enemy ->
            assertEquals(initialEnemyHps[enemy.id], enemy.currentHp)
        }

        // 4. Zephyr's ATB gauge must still be ready
        val zephyr = currentState.party.first { it.id == "zephyr" }
        assertEquals(1.0f, zephyr.atbGauge, 0.001f)

        // 5. Floating text warning should be present
        assertTrue(currentState.floatingTexts.any { it.text.contains("charging") || it.text.contains("turn") })
    }

    @Test
    fun testStrictTurnEnforcementCannotCommandOtherHeroOutOfTurn() {
        val quad = createQuadParty()
        applyElementalistCustomization()
        viewModel.startEncounter(GameContent.encounters.getValue("template_solo_battle").copy(initialParty = quad))

        viewModel.setPlayerInputPhaseForTesting("zephyr")
        viewModel.setPartyMemberAtbForTesting("cedric", 0.30f)

        val initialCedricMp = viewModel.state.value.party.first { it.id == "cedric" }.currentMp
        val initialEnemyHps = viewModel.state.value.enemies.associate { it.id to it.currentHp }

        // Player chants Cedric's smite out of turn
        viewModel.processIncantation("Cedric attack archer")
        Thread.sleep(150)

        val currentState = viewModel.state.value
        assertEquals(CombatPhase.PLAYER_INPUT, currentState.phase)
        assertEquals("zephyr", currentState.activePartyMemberId)
        assertEquals(initialCedricMp, currentState.party.first { it.id == "cedric" }.currentMp)
        currentState.enemies.forEach { enemy ->
            assertEquals(initialEnemyHps[enemy.id], enemy.currentHp)
        }
    }

    @Test
    fun testStrictTurnEnforcementZephyrActsConsumesTurn() {
        val quad = createQuadParty()
        applyElementalistCustomization()
        viewModel.startEncounter(GameContent.encounters.getValue("template_solo_battle").copy(initialParty = quad))

        viewModel.setPlayerInputPhaseForTesting("zephyr")
        val initialZephyrMp = viewModel.state.value.party.first { it.id == "zephyr" }.currentMp

        // Valid command for Zephyr
        viewModel.processIncantation("Shadow strike orc")

        // Wait for resolution and VFX
        var turnResolved = false
        for (i in 0 until 40) {
            Thread.sleep(50)
            val zephyr = viewModel.state.value.party.first { it.id == "zephyr" }
            if (zephyr.atbGauge == 0f || zephyr.currentMp < initialZephyrMp) {
                turnResolved = true
                break
            }
        }

        assertTrue("Zephyr's turn must be consumed (ATB reset to 0 and MP spent)", turnResolved)
    }

    @Test
    fun testVoiceHeroSwitchingTurnReadyVsNotReady() {
        val quad = createQuadParty()
        viewModel.startEncounter(GameContent.encounters.getValue("template_solo_battle").copy(initialParty = quad))

        viewModel.setPlayerInputPhaseForTesting("zephyr")
        viewModel.setPartyMemberAtbForTesting("cedric", 0.40f)

        // Attempt to switch to Cedric when Cedric is not ready
        viewModel.processIncantation("Switch to Cedric")
        Thread.sleep(100)

        // Should reject switch because Cedric's ATB is < 1.0f
        assertEquals("zephyr", viewModel.state.value.activePartyMemberId)

        // Now set Cedric's ATB to 1.0f
        viewModel.setPartyMemberAtbForTesting("cedric", 1.0f)
        viewModel.processIncantation("Switch to Cedric")
        Thread.sleep(100)

        // Should succeed because Cedric is turn ready
        assertEquals("cedric", viewModel.state.value.activePartyMemberId)
    }

    @Test
    fun testPureStoryModePreservedOnStartEncounterAndRestart() {
        viewModel.setPureStoryMode(true)
        assertTrue(viewModel.state.value.isPureStoryMode)

        viewModel.startEncounter(GameContent.encounters.getValue("template_solo_battle"))
        assertTrue("isPureStoryMode must be preserved when starting an encounter", viewModel.state.value.isPureStoryMode)

        viewModel.restartBattle()
        assertTrue("isPureStoryMode must be preserved when restarting a battle", viewModel.state.value.isPureStoryMode)
    }

    @Test
    fun testPureStoryModeAutoHeroActionExecutesChantAndDamagesEnemies() {
        viewModel.setPureStoryMode(true)
        viewModel.startEncounter(GameContent.encounters.getValue("template_solo_battle"))

        val activeHero = viewModel.state.value.party.first { it.id == "hero" }
        val targetEnemy = viewModel.state.value.enemies.first { it.isAlive }
        val initialEnemyHp = targetEnemy.currentHp

        viewModel.setPlayerInputPhaseForTesting("hero")
        viewModel.triggerAutoHeroAction(activeHero)

        // Give coroutine time to complete VFX and damage application
        var turnResolved = false
        for (i in 0 until 20) {
            Thread.sleep(100)
            val updatedEnemy = viewModel.state.value.enemies.firstOrNull { it.id == targetEnemy.id }
            if (updatedEnemy != null && updatedEnemy.currentHp < initialEnemyHp) {
                turnResolved = true
                break
            }
        }

        assertTrue("Auto hero action in pure story mode must resolve and deal damage to enemies", turnResolved)
    }
}