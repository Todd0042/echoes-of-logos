package com.voicerpg.engine.ui.sprites

import androidx.compose.ui.geometry.Offset
import com.voicerpg.engine.audio.SpeechManager
import com.voicerpg.engine.combat.BattleCue
import com.voicerpg.engine.combat.CombatTiming
import com.voicerpg.engine.content.GameContent
import com.voicerpg.engine.engine.NoveltyCache
import com.voicerpg.engine.engine.ResonanceEngine
import com.voicerpg.engine.engine.SpellChantPresets
import com.voicerpg.engine.model.BattleEnvironment
import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.testing.AsepriteFixture
import com.voicerpg.engine.testing.AsepriteFixture.Tag
import com.voicerpg.engine.testing.TemplateFixture
import com.voicerpg.engine.ui.fx.BattleFxDirector
import com.voicerpg.engine.ui.fx.CombatantLook
import com.voicerpg.engine.viewmodel.CombatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Collections

/**
 * Sprite overhaul S1-S2: a game ships Aseprite sheets (PNG + JSON export, tags = animations, a `feet` slice,
 * event frames in tag user data) next to its legacy text grids; both play through one animation path that the
 * battle cues drive on the effects clock.
 *
 * Played through a real battle: Aethel (a 64 px sheet) chants at a brute that still uses a text grid. Her ready
 * stance, the charge loop while she speaks, the cast held on the frame before the release until the spell
 * leaves, the brute's hurt as it lands (frozen by the hit-stop), her victory pose; a wolf sheet with only the
 * enemy minimum falls back down the chain; and a boss's second phase swaps to its `p2_` animations.
 */
class SpriteAnimationTest {

    private lateinit var pack: File
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val viewModels = mutableListOf<CombatViewModel>()

    @Before
    fun setUp() {
        pack = Files.createTempDirectory("sprite-pack").toFile()
        TemplateFixture.dir().copyRecursively(pack)
        val sheets = File(pack, "sprites/sheets").apply { mkdirs() }
        File(sheets, "hero.json").writeText(heroSheet())
        File(sheets, "qqwolf.json").writeText(wolfSheet())
        File(sheets, "qqlord.json").writeText(lordSheet())
        File(pack, "sprites/sprites.json").writeText(
            """{ "sprites": { "qqemberwolf": { "base": "qqwolf", "palette": { "#3A3A3A": "#8A2B1A" }, "scale": 1.25 } } }"""
        )
        GameContent.reset()
        GameContent.initializeFromDirectory(pack)
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.cleanup() }
        collectorScope.cancel()
        vmScope.cancel()
        GameContent.reset()
        pack.deleteRecursively()
    }

    // ---- Aseprite exports, as Aseprite writes them (hash format, trimmed frames, tag user data, slices) ----

    private fun aseprite(name: String, cell: Int, tags: List<Tag>, feet: Pair<Int, Int>) = AsepriteFixture.export(name, cell, tags, feet)

    private fun heroSheet() = aseprite("hero", 64, listOf(
        Tag("idle", 0, 3),
        Tag("ready", 4, 5, dir = "pingpong"),
        Tag("charge", 6, 7),
        Tag("cast", 8, 12, data = "release=3"),
        Tag("attack", 13, 17, data = "impact=3"),
        Tag("hurt", 18, 19),
        Tag("ko", 20, 22),
        Tag("victory", 23, 26)
    ), feet = 32 to 60)

    private fun wolfSheet() = aseprite("qqwolf", 64, listOf(
        Tag("idle", 0, 3),
        Tag("attack", 4, 7, data = "impact=2"),
        Tag("hurt", 8, 9)
    ), feet = 30 to 58)

    private fun lordSheet() = aseprite("qqlord", 96, listOf(
        Tag("idle", 0, 3), Tag("windup", 4, 5), Tag("attack", 6, 9, data = "impact=3"), Tag("cast", 10, 13, data = "release=2"),
        Tag("hurt", 14, 15), Tag("charge_ultimate", 16, 17), Tag("ultimate", 18, 22, data = "release=4"),
        Tag("p2_idle", 23, 26), Tag("p2_attack", 27, 30, data = "impact=2")
    ), feet = 48 to 92)

    // ---- the battle ----

    private val frame = 1_000_000_000L / 60
    private var now = 1_000_000_000L

    private fun BattleFxDirector.runFor(seconds: Float) {
        frame(now)
        repeat((seconds * 60).toInt()) { now += frame; frame(now) }
    }

    private fun aethel() = PartyMember(id = "hero", name = "Aethel", loreClass = "Elementalist", currentHp = 300, maxHp = 300,
        currentMp = 200, maxMp = 200, spells = listOfNotNull(GameContent.spellById("flame_spark")), speed = 0)

    private fun brute() = Enemy(id = "qqbrute", name = "Qxbrute", subtitle = "Qz", currentHp = 9_000, maxHp = 9_000,
        baseAttack = 1, speed = 0, family = "CONSTRUCT", spriteId = "template_brute")

    @Test
    fun `sheets load from the Aseprite export, legacy grids share the path, and missing tags fall back`() {
        val hero = SpriteLibrary.source("hero")
        assertTrue("the hero's sheet: $hero", hero is SheetSprite)
        hero as SheetSprite
        assertEquals(64, hero.sheet.cellW)
        assertEquals(27, hero.sheet.frames.size)
        assertEquals("trimmed frames keep their place in the cell", 8, hero.sheet.frames[0].offX)
        assertEquals("feet: centre of the slice, its bottom edge", 32 to 60, hero.feet)
        val cast = hero.clip(AnimTag.CAST)
        assertEquals("cast", cast.name)
        assertEquals("release=3 is the third frame of the tag", 2, cast.events["release"])
        assertEquals("pingpong plays 4 5 then back", listOf(4, 5), hero.clip(AnimTag.READY).frames.take(2))

        val wolf = SpriteLibrary.source("qqwolf")
        assertEquals("no cast: the attack stands in", "attack", wolf.clip(AnimTag.CAST).name)
        assertEquals("no windup: ready, then idle", "idle", wolf.clip(AnimTag.WINDUP).name)
        assertEquals("no ko: hurt", "hurt", wolf.clip(AnimTag.KO).name)

        val ember = SpriteLibrary.source("qqemberwolf")
        assertTrue(ember is SheetSprite)
        ember as SheetSprite
        assertEquals("a variant is its base sheet, recoloured", "qqwolf", ember.sheet.id)
        assertEquals(0xFF8A2B1A.toInt(), ember.recolor[0xFF3A3A3A.toInt()])
        assertEquals(1.25f, ember.scale, 0f)

        val brute = SpriteLibrary.source("template_brute")
        assertTrue("the text grid still works: $brute", brute is GridSprite)
        assertEquals("its idle breathes on two frames", 2, brute.clip(AnimTag.IDLE).frames.size)
        assertEquals("its hurt is the damaged frame", GridSprite.DAMAGED, brute.clip(AnimTag.HURT).frames.single())
    }

    @Test
    fun `a chant plays ready, charge, cast held to the release, the foe's hurt frozen by the hit-stop, then victory`() {
        val vm = CombatViewModel(SpeechManager(), resonanceEngine = ResonanceEngine(NoveltyCache()), timing = CombatTiming.INSTANT,
            random = kotlin.random.Random(3), scopeOverride = vmScope).also { viewModels += it }
        val cues = Collections.synchronizedList(mutableListOf<BattleCue>())
        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.cues.collect { cues += it } }
        val director = BattleFxDirector(
            positionOf = { Offset.Zero },
            lookOf = { id -> CombatantLook(isEnemy = id != "hero", pixels = PixelGrid.EMPTY, flipped = id != "hero") },
            spriteOf = { id -> SpriteLibrary.source(if (id == "hero") "hero" else "template_brute") }
        )
        vm.startEncounter(listOf(aethel()), listOf(brute()), BattleEnvironment.FOREST)
        val hero = director.fxOf("hero").animator
        val foe = director.fxOf("qqbrute").animator

        // Her turn: the view sets the resting state from the combat state.
        vm.setPlayerInputPhaseForTesting("hero")
        val state = vm.state.value
        hero.base = SpriteBase.of(state.party.single(), isActive = true)
        foe.base = SpriteBase.of(state.enemies.single())
        director.runFor(0.2f)
        assertEquals("ready", hero.clipName)
        assertEquals("idle", foe.clipName)

        val preset = SpellChantPresets.getPreset(GameContent.spellById("flame_spark")!!, ResonanceTier.ADEPT)
        vm.submitTypedChant(preset.chantText, preset.acousticProfile)
        val end = System.currentTimeMillis() + 15_000
        while (synchronized(cues) { cues.none { it is BattleCue.TurnEnd } } && System.currentTimeMillis() < end) Thread.sleep(15)
        val turn = synchronized(cues) { cues.toList() }
        assertTrue("a full turn: $turn", turn.any { it is BattleCue.Release } && turn.any { it is BattleCue.Impact })

        var heldFrame = -1
        for (cue in turn) {
            director.play(cue)
            when (cue) {
                is BattleCue.ChargeStart -> {
                    director.runFor(0.3f)
                    assertEquals("speaking: the charge loop", "charge", hero.clipName)
                }
                is BattleCue.ChargeEnd -> {
                    director.runFor(1.0f)
                    assertEquals("the chant is done: she raises the cast", "cast", hero.clipName)
                    assertEquals("and holds on the frame before the release", 9, hero.frame) // cast = 8..12, release = 3rd
                    heldFrame = hero.frame
                }
                is BattleCue.Release -> {
                    assertEquals("the spell leaves on the release frame", 10, hero.frame)
                    assertTrue(heldFrame == 9)
                }
                is BattleCue.Impact -> {
                    assertEquals("the brute flinches as it lands", "hurt", foe.clipName)
                    assertEquals(GridSprite.DAMAGED, foe.frame)
                    val t = foe.elapsedMs
                    director.runFor(0.03f) // inside the hit-stop
                    assertEquals("frozen by the hit-stop", t, foe.elapsedMs, 0.001f)
                    director.runFor(1.5f)
                    assertEquals("then back to its stance", "idle", foe.clipName)
                }
                else -> director.runFor(0.05f)
            }
        }
        hero.base = SpriteBase.of(vm.state.value.party.single(), isActive = false)
        director.runFor(1.0f)
        assertEquals("after the cast she rests", "idle", hero.clipName)

        director.play(BattleCue.BattleEnd(victory = true))
        director.runFor(0.2f)
        assertEquals("won: the victory pose", "victory", hero.clipName)
        assertEquals("the foe does not celebrate", "idle", foe.clipName)
    }

    @Test
    fun `a boss winds up for the move's wind-up time, charges its ultimate, and changes animations with its phase`() {
        val director = BattleFxDirector(positionOf = { Offset.Zero }, spriteOf = { SpriteLibrary.source("qqlord") })
        val lord = director.fxOf("qqlord").animator
        director.runFor(0.1f)
        assertEquals("idle", lord.clipName)

        director.play(BattleCue.Telegraph("qqlord", "Qxcleave", com.voicerpg.engine.combat.Intent.ATTACK))
        director.runFor(0.2f)
        assertEquals("telegraphed: winding up", "windup", lord.clipName)

        director.play(BattleCue.EnemyMove("qqlord", "Qxcleave", kind = "ATTACK", isBossUltimate = false, windupMs = 600))
        director.runFor(0.3f)
        assertEquals("attack", lord.clipName)
        assertTrue("half-way through the wind-up, still before the blow: ${lord.frame}", lord.frame in 6..7)
        director.runFor(0.4f)
        assertEquals("held on the frame before the impact", 7, lord.frame)
        director.play(BattleCue.Release("qqlord", fromParty = false, targetIds = listOf("hero"),
            school = com.voicerpg.engine.model.SpellSchool.PHYSICAL, style = BattleCue.ReleaseStyle.PROJECTILE, particleCount = 10, travelMs = 300))
        assertEquals("the blow lands on the impact frame", 8, lord.frame)
        director.runFor(1.0f)
        assertEquals("idle", lord.clipName)

        director.play(BattleCue.UltimateCharge("qqlord", "Qxdoom", turnsLeft = 1, totalTurns = 2))
        director.runFor(0.2f)
        assertEquals("charge_ultimate", lord.clipName)

        director.play(BattleCue.PhaseShift("qqlord", "enraged"))
        director.play(BattleCue.EnemyMove("qqlord", "Qxrend", kind = "ATTACK", isBossUltimate = false, windupMs = 0))
        director.runFor(0.05f)
        assertEquals("second phase: its own attack", "p2_attack", lord.clipName)
        director.runFor(2.0f)
        assertEquals("and its own idle", "p2_idle", lord.clipName)
        assertNotNull(lord.source)
    }

    @Test
    fun `a boss sheet is drawn at its own size, while text grids keep the boss enlargement`() {
        val lordSheet = SpriteLibrary.source("qqlord")              // a 96 px boss sheet
        val grid = SpriteLibrary.source("template_brute")           // a 20x26 text grid
        val boss = brute().copy(isBoss = true, scale = Enemy.DEFAULT_BOSS_SCALE)
        assertEquals("the sheet is already boss-sized", 1f, SpriteSizing.enemyFactor(boss, lordSheet), 0f)
        assertEquals("the grid is enlarged as before", 2f, SpriteSizing.enemyFactor(boss, grid), 0f)
        assertEquals("content asking for a bigger boss still gets it", 1.25f,
            SpriteSizing.enemyFactor(boss.copy(scale = 2.5f), lordSheet), 0.001f)
        assertEquals("a regular enemy keeps its own scale", 1.2f, SpriteSizing.enemyFactor(brute().copy(scale = 1.2f), lordSheet), 0.001f)
    }
}
