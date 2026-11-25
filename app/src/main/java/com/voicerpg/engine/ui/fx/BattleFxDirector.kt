package com.voicerpg.engine.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.voicerpg.engine.combat.BattleCue
import com.voicerpg.engine.content.PresentationConfig
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.ui.vfx.ParticleEmitter
import com.voicerpg.engine.ui.vfx.SpellVfxEngine
import com.voicerpg.engine.ui.vfx.drawParticles
import com.voicerpg.engine.ui.vfx.drawProjectiles
import kotlin.math.PI
import kotlin.math.sin

/**
 * Turns [BattleCue]s into what the player sees: projectiles, particle bursts, the arena shake, hit-stop,
 * per-combatant flashes and knockback, and damage numbers, all on one [FxClock]. It never changes game
 * state; combat logic never draws. One director per battle screen.
 *
 * [positionOf] resolves a combatant id to its live sprite position in arena pixels.
 */
class BattleFxDirector(
    private val positionOf: (String) -> Offset,
    /** What a combatant looks like (sprite grid, facing, hit reaction); null when unknown. */
    private val lookOf: (String) -> CombatantLook? = { null }
) {

    val clock = FxClock()
    val projectiles = SpellVfxEngine()
    val particles = ParticleEmitter()
    val shake = ArenaShake()

    /** The game's `combat/presentation.json`. */
    var presentation: PresentationConfig = PresentationConfig()

    /** Options → Reduce motion: no shake or zoom, hit-stop kept short. */
    var reduceMotion: Boolean = false

    /** Options → Reduce flashing: no full-screen white flash. */
    var reduceFlashing: Boolean = false

    /** Advances once per drawn frame, so the canvas redraws while anything moves. */
    var frameTick by mutableLongStateOf(0L)
        private set

    /** Director time: real seconds since the first frame. */
    var time = 0f
        private set

    /** Arena zoom (the Transcendental punch: 1.0 → 1.06 → 1.0). */
    var zoom by mutableFloatStateOf(1f)
        private set

    /** 0..1 full-screen white. */
    var screenFlash by mutableFloatStateOf(0f)
        private set

    /** Damage and heal numbers in flight. */
    val numbers = mutableStateListOf<DamageNumber>()

    /** The cut-in panel on screen, or null. */
    var cutIn by mutableStateOf<ShownCutIn?>(null)
        private set

    /** A running resonance streak (badge), or null. */
    var streak by mutableStateOf<ShownStreak?>(null)
        private set

    /** The move name on screen, in the game's title-card style. */
    var titleCard by mutableStateOf<ShownTitleCard?>(null)
        private set

    /** 0..0.3: the arena backdrop darkens while a chant charges. */
    var dim by mutableFloatStateOf(0f)
        private set

    /** The charging caster (null when nobody charges) and its smoothed voice level 0..1. */
    var chargingId: String? = null
        private set
    var chargeLevel = 0f
        private set
    private var chargeTarget = 0f
    private var chargeTyped = false
    private var moteDebt = 0f

    private val combatants = HashMap<String, CombatantFx>()
    private val spriteBounds = HashMap<String, androidx.compose.ui.geometry.Rect>()

    /** Where a combatant's sprite is drawn, in arena pixels (reported by its view). */
    fun registerSpriteBounds(id: String, bounds: androidx.compose.ui.geometry.Rect) {
        spriteBounds[id] = bounds
    }
    private val pending = mutableListOf<Pair<Float, () -> Unit>>()
    private var zoomStart = -1f
    private val flashTimes = ArrayDeque<Float>()
    private var nextNumberId = 0L
    private val shardRandom = kotlin.random.Random(7)

    /** Live effects of one combatant (created on first use). */
    fun fxOf(id: String): CombatantFx = combatants.getOrPut(id) { CombatantFx() }

    fun play(cue: BattleCue) {
        when (cue) {
            is BattleCue.BattleStart -> clear()
            is BattleCue.ChargeStart -> {
                chargingId = cue.casterId
                chargeTyped = cue.typed
                chargeLevel = 0f
                chargeTarget = if (cue.typed) TYPED_CHARGE_LEVEL else 0f
                fxOf(cue.casterId).glow = CHARGE_GLOW
            }
            is BattleCue.ChargeLevel -> if (cue.casterId == chargingId) chargeTarget = cue.level.coerceIn(0f, 1f)
            is BattleCue.ChargeEnd -> {
                if (cue.casterId == chargingId) chargingId = null
                fxOf(cue.casterId).let { it.glow = null; it.glowStrength = 0f }
            }
            is BattleCue.ResonanceReveal -> when {
                cue.resonance.bonusPercent >= SUPER_BONUS ->
                    kick(if (cue.resonance.tier == ResonanceTier.TRANSCENDENTAL) 30f else 18f, direction = 0f)
                cue.resonance.bonusPercent >= BANNER_BONUS -> kick(10f, direction = 0f)
            }
            is BattleCue.Telegraph -> if (presentation.telegraph.showIntentIcons) fxOf(cue.enemyId).intent = cue.icon
            is BattleCue.UltimateCharge -> fxOf(cue.enemyId).let {
                it.ultimateCharge = 1f - cue.turnsLeft.toFloat() / (cue.totalTurns + 1)
                it.glow = ULTIMATE_GLOW
                it.glowStrength = 0.9f
                kick(6f, direction = 0f)
            }
            is BattleCue.EnemyMove -> enemyMove(cue)
            is BattleCue.TitleCard -> titleCard = ShownTitleCard(cue.text, cue.fromParty, time)
            is BattleCue.CutIn -> {
                cutIn = ShownCutIn(cue, time)
                clock.slowMotion(scale = 0.3f, durationMs = (ShownCutIn.LIFETIME * 1000).toLong())
            }
            is BattleCue.Release -> release(cue)
            is BattleCue.Impact -> impact(cue)
            is BattleCue.Reinforcements -> kick(12f, direction = 0f)
            is BattleCue.Stagger -> stagger(cue)
            is BattleCue.Streak -> streak = if (cue.count >= 2) ShownStreak(cue.count, time) else null
            is BattleCue.Death -> death(cue)
            is BattleCue.Taunt -> fxOf(cue.enemyId).let { fx ->
                fx.speech = cue.line
                after(TAUNT_SECONDS) { if (fx.speech == cue.line) fx.speech = null }
            }
            is BattleCue.MusicIntensity,
            is BattleCue.PhaseShift,
            is BattleCue.Guard,
            is BattleCue.TurnEnd,
            is BattleCue.BattleEnd -> Unit
        }
    }

    /** Advance everything to the frame at [frameNanos]. */
    fun frame(frameNanos: Long) {
        val step = clock.tick(frameNanos)
        time += step.realSeconds
        runPending()
        projectiles.update(step.fxSeconds)
        particles.update(step.fxSeconds)
        shake.step(step.realSeconds)
        for (c in combatants.values) c.step(step.realSeconds, step.fxSeconds)
        if (numbers.isNotEmpty()) numbers.removeAll { time - it.bornAt > DamageNumber.LIFETIME }
        stepCharge(step.realSeconds)
        titleCard?.let { if (time - it.bornAt > ShownTitleCard.LIFETIME) titleCard = null }
        cutIn?.let { if (time - it.bornAt > ShownCutIn.LIFETIME) cutIn = null }
        if (screenFlash > 0f) screenFlash = (screenFlash - step.realSeconds / SCREEN_FLASH_SECONDS).coerceAtLeast(0f)
        if (zoomStart >= 0f) {
            val t = (time - zoomStart) / ZOOM_SECONDS
            if (t >= 1f) { zoom = 1f; zoomStart = -1f } else zoom = 1f + ZOOM_PEAK * sin(PI.toFloat() * t)
        }
        frameTick = frameNanos
    }

    private fun death(cue: BattleCue.Death) {
        val fx = fxOf(cue.combatantId)
        fx.intent = null
        fx.ultimateCharge = null
        fx.glow = null
        fx.leanTarget = 0f
        fx.speech = null
        val look = lookOf(cue.combatantId)
        if (!cue.isEnemy || look == null || !look.isEnemy) return // heroes fall as before (they can be revived)
        when (presentation.deathEffect) {
            PresentationConfig.DeathEffect.FADE -> fx.startFade()
            PresentationConfig.DeathEffect.SHATTER -> { shatter(cue.combatantId, look, rising = false); fx.hidden = true }
            PresentationConfig.DeathEffect.DISSOLVE -> { shatter(cue.combatantId, look, rising = true); fx.hidden = true }
        }
    }

    /**
     * Every opaque pixel of the sprite's death frame becomes a particle in its own colour, blown away from
     * the hit (enemies are struck from the party side) under gravity; [rising]: they drift up instead.
     */
    private fun shatter(id: String, look: CombatantLook, rising: Boolean) {
        val rows = look.matrix.size
        val cols = look.matrix.maxOfOrNull { it.length } ?: return
        if (rows == 0 || cols == 0) return
        val centre = positionOf(id)
        val bounds = spriteBounds[id] ?: androidx.compose.ui.geometry.Rect(centre.x - cols * 2f, centre.y - rows * 2f, centre.x + cols * 2f, centre.y + rows * 2f)
        val pxW = bounds.width / cols
        val pxH = bounds.height / rows
        val away = if (look.isEnemy) 1f else -1f
        val shards = ArrayList<com.voicerpg.engine.model.Particle>()
        for (r in 0 until rows) {
            val line = look.matrix[r]
            for (c in line.indices) {
                val ch = line[c]
                if (ch == '.') continue
                val color = look.palette[ch] ?: continue
                if (color.alpha == 0f) continue
                val col = if (look.flipped) cols - 1 - c else c
                val x = bounds.left + (col + 0.5f) * pxW
                val y = bounds.top + (r + 0.5f) * pxH
                val spread = (x - bounds.center.x) / bounds.width
                shards += com.voicerpg.engine.model.Particle(
                    x = x, y = y,
                    vx = if (rising) spread * 1.5f + away * 0.3f else away * (2f + shardRandom.nextFloat() * 4f) + spread * 3f,
                    vy = if (rising) -(0.6f + shardRandom.nextFloat() * 1.4f) else -(1.5f + shardRandom.nextFloat() * 3.5f),
                    size = maxOf(pxW, pxH), initialSize = maxOf(pxW, pxH),
                    decay = if (rising) 0.012f else 0.016f,
                    color = color,
                    gravity = if (rising) 0f else 0.28f,
                    drag = 0.985f
                )
            }
        }
        if (shards.size > MAX_SHARDS_PER_DEATH) shards.shuffle(shardRandom)
        particles.add(shards.take(MAX_SHARDS_PER_DEATH))
    }

    private fun stagger(cue: BattleCue.Stagger) {
        val fx = fxOf(cue.enemyId)
        fx.hit(direction = 1f, amplitudeDp = 18f * presentation.intensity.coerceAtLeast(0f) * (if (reduceMotion) 0.5f else 1f))
        if (cue.interruptedMove != null) {
            fx.intent = null
            fx.ultimateCharge = null
            fx.glow = null
            kick(16f, direction = 1f)
            clock.hitStop(if (reduceMotion) REDUCED_HIT_STOP_MS else 220L)
        }
        val at = positionOf(cue.enemyId)
        numbers += DamageNumber(
            id = nextNumberId++, targetId = cue.enemyId, text = if (cue.interruptedMove != null) "BROKEN!" else "STAGGER!",
            color = STAGGER_COLOR, label = null, labelColor = STAGGER_COLOR, x = at.x, y = at.y - 40f, drift = 0f, big = true, bornAt = time
        )
    }

    private fun enemyMove(cue: BattleCue.EnemyMove) {
        if (cue.isBossUltimate) kick(20f, direction = 0f)
        val fx = fxOf(cue.enemyId)
        fx.intent = null
        fx.ultimateCharge = null
        if (cue.windupMs > 0) {
            fx.leanTarget = 1f
            fx.glow = cue.school.themeColor
            fx.glowStrength = if (cue.isBossUltimate) 1f else 0.7f
            after(cue.windupMs / 1000f) {
                fx.leanTarget = 0f
                fx.glow = null
                fx.pulse(0.3f)
            }
        } else {
            fx.glow = null
        }
    }

    private fun stepCharge(dt: Float) {
        val caster = chargingId
        val dimTarget = if (caster != null) MAX_DIM else 0f
        dim += (dimTarget - dim) * (dt * 8f).coerceAtMost(1f)
        if (dim < 0.002f) dim = 0f
        if (caster == null) { chargeLevel = 0f; return }
        val rate = if (chargeTyped) 3f else 14f // typed: a steady ramp; voice: follows quickly
        chargeLevel += (chargeTarget - chargeLevel) * (dt * rate).coerceAtMost(1f)
        fxOf(caster).glowStrength = 0.25f + 0.75f * chargeLevel
        moteDebt += dt * MAX_MOTES_PER_SECOND * chargeLevel
        val motes = moteDebt.toInt()
        if (motes > 0) {
            moteDebt -= motes
            val at = positionOf(caster)
            particles.emit(com.voicerpg.engine.model.SpellSchool.HOLY, at.x, at.y, count = motes, isLogos = false)
        }
    }

    /** How long [cue] freezes the arena: by tier for the party's blows, short for enemies'. */
    fun hitStopMsFor(cue: BattleCue.Impact): Long {
        val base = if (cue.fromParty) HIT_STOP_MS.getValue(cue.tier) else if (cue.shake >= 18f) 140L else 70L
        val scaled = (base * presentation.intensity.coerceAtLeast(0f)).toLong()
        return if (reduceMotion) minOf(scaled, REDUCED_HIT_STOP_MS) else scaled
    }

    private fun impact(cue: BattleCue.Impact) {
        if (cue.shake > 0f) kick(cue.shake, direction = if (cue.fromParty) 1f else -1f)
        val damaging = !cue.isTick && cue.hits.any { it.kind == BattleCue.HitKind.DAMAGE && it.amount > 0 }
        if (damaging) clock.hitStop(hitStopMsFor(cue))
        if (damaging && cue.fromParty && cue.tier == ResonanceTier.TRANSCENDENTAL) {
            clock.slowMotion(scale = 0.3f, durationMs = 450)
            if (!reduceMotion) zoomStart = time
            flashScreen()
        }
        val push = if (cue.fromParty) 1f else -1f
        val amplitude = (KNOCK_MIN_DP + (KNOCK_MAX_DP - KNOCK_MIN_DP) * cue.tier.ordinal / (ResonanceTier.entries.size - 1)) *
            presentation.intensity.coerceAtLeast(0f)
        cue.hits.forEachIndexed { i, hit ->
            val land = {
                fxOf(hit.targetId).revealVitals()
                if (!cue.isTick && hit.kind == BattleCue.HitKind.DAMAGE && hit.amount > 0) {
                    val reaction = lookOf(hit.targetId)?.hitReaction ?: com.voicerpg.engine.model.HitReaction.FLINCH
                    fxOf(hit.targetId).hit(push, if (reduceMotion) amplitude * 0.5f else amplitude, reaction)
                }
                spawnNumber(hit, cue.tier, push)
            }
            if (i == 0) land() else after(i * AREA_STAGGER_SECONDS, land)
        }
    }

    private fun spawnNumber(hit: BattleCue.Hit, tier: ResonanceTier, push: Float) {
        val labels = presentation.labels
        val (text, color) = when (hit.kind) {
            BattleCue.HitKind.DAMAGE -> "-${hit.amount}" to when {
                hit.affinity >= WEAK_AFFINITY -> GOLD
                hit.affinity < RESIST_AFFINITY -> GREY
                tier >= ResonanceTier.MYTHIC -> AMBER
                else -> RED
            }
            BattleCue.HitKind.HEAL -> "+${hit.amount}" to GREEN
            BattleCue.HitKind.MANA -> "+${hit.amount} MP" to BLUE
        }
        val (label, labelColor) = when {
            hit.kind != BattleCue.HitKind.DAMAGE -> null to color
            hit.affinity >= WEAK_AFFINITY -> labels.weak to GOLD
            hit.affinity < RESIST_AFFINITY -> labels.resist to GREY
            tier >= ResonanceTier.MYTHIC -> labels.critFor(tier) to AMBER
            else -> null to color
        }
        val at = positionOf(hit.targetId)
        numbers += DamageNumber(
            id = nextNumberId++, targetId = hit.targetId, text = text, color = color, label = label, labelColor = labelColor,
            x = at.x, y = at.y, drift = push, big = label != null || tier >= ResonanceTier.MYTHIC, bornAt = time
        )
    }

    /** A full-screen white flash, at most three a second (WCAG 2.3.1); none with Reduce flashing. */
    private fun flashScreen() {
        if (reduceFlashing) return
        while (flashTimes.isNotEmpty() && time - flashTimes.first() >= 1f) flashTimes.removeFirst()
        if (flashTimes.size >= MAX_FLASHES_PER_SECOND) return
        flashTimes.addLast(time)
        screenFlash = 1f
    }

    private fun kick(magnitude: Float, direction: Float) {
        if (reduceMotion) return
        shake.kick(magnitude * presentation.intensity, direction)
    }

    private fun after(seconds: Float, action: () -> Unit) {
        pending += (time + seconds) to action
    }

    private fun runPending() {
        if (pending.isEmpty()) return
        val due = pending.filter { it.first <= time }
        pending.removeAll(due)
        due.forEach { it.second() }
    }

    private fun clear() {
        projectiles.projectiles.clear()
        particles.clear()
        shake.clear()
        clock.reset()
        numbers.clear()
        pending.clear()
        combatants.values.forEach { it.reset() }
        spriteBounds.clear()
        zoom = 1f
        zoomStart = -1f
        screenFlash = 0f
        chargingId = null
        chargeLevel = 0f
        dim = 0f
        titleCard = null
        streak = null
        cutIn = null
    }

    private fun release(cue: BattleCue.Release) {
        val caster = positionOf(cue.casterId)
        fxOf(cue.casterId).pulse()
        // A running streak makes the bursts bigger (looks only).
        val isLogos = cue.bonusPercent >= SUPER_BONUS || (cue.fromParty && (streak?.count ?: 0) >= 3)
        if (cue.style == BattleCue.ReleaseStyle.ATTUNE) {
            particles.emit(cue.school, caster.x, caster.y, count = ATTUNE_PARTICLES, isLogos = isLogos)
            return
        }
        val targets = cue.targetIds.map(positionOf).ifEmpty { listOf(FALLBACK_TARGET) }
        val perTarget = cue.graphics?.particleCount
            ?: if (targets.size == 1) cue.particleCount else (cue.particleCount / targets.size).coerceAtLeast(MIN_SPLIT_PARTICLES)
        for (target in targets) {
            projectiles.launch(
                startX = caster.x,
                startY = caster.y,
                targetX = target.x,
                targetY = target.y,
                school = cue.school,
                isHeal = cue.style == BattleCue.ReleaseStyle.HEAL,
                bonusPercent = cue.bonusPercent,
                tier = cue.tier,
                graphics = cue.graphics,
                durationMs = cue.travelMs,
                onImpact = { particles.emit(cue.school, target.x, target.y, count = perTarget, isLogos = isLogos) }
            )
        }
    }

    private companion object {
        /** Resonance bonus that earns the golden banner, and the "Logos" super burst. */
        const val BANNER_BONUS = 50
        const val SUPER_BONUS = 100
        const val ATTUNE_PARTICLES = 16
        const val MIN_SPLIT_PARTICLES = 8
        val HIT_STOP_MS = mapOf(
            ResonanceTier.BASIC to 60L, ResonanceTier.ADEPT to 80L, ResonanceTier.MASTER to 110L,
            ResonanceTier.MYTHIC to 150L, ResonanceTier.TRANSCENDENTAL to 180L
        )
        const val REDUCED_HIT_STOP_MS = 60L
        const val KNOCK_MIN_DP = 8f
        const val KNOCK_MAX_DP = 14f
        const val AREA_STAGGER_SECONDS = 0.06f
        const val WEAK_AFFINITY = 1.5f
        const val RESIST_AFFINITY = 0.95f
        const val ZOOM_PEAK = 0.06f
        const val ZOOM_SECONDS = 0.35f
        const val SCREEN_FLASH_SECONDS = 0.25f
        const val MAX_FLASHES_PER_SECOND = 3
        const val MAX_DIM = 0.3f
        const val TYPED_CHARGE_LEVEL = 0.8f
        const val MAX_MOTES_PER_SECOND = 45f
        val CHARGE_GLOW = Color(0xFFFFD54F)
        val ULTIMATE_GLOW = Color(0xFFFF1744)
        val STAGGER_COLOR = Color(0xFFFF6E40)
        const val TAUNT_SECONDS = 2.2f
        const val MAX_SHARDS_PER_DEATH = 600
        val GOLD = Color(0xFFFFD54F)
        val GREY = Color(0xFFB0BEC5)
        val AMBER = Color(0xFFFFAB00)
        val RED = Color(0xFFFF5252)
        val GREEN = Color(0xFF69F0AE)
        val BLUE = Color(0xFF4FC3F7)
        /** Where a release with no living target flies (right of centre, upper arena). */
        val FALLBACK_TARGET = Offset(800f, 350f)
    }
}

/** A move name on screen since [bornAt] (director time). */
data class ShownTitleCard(val text: String, val fromParty: Boolean, val bornAt: Float) {
    companion object {
        const val LIFETIME = 1.15f
    }
}

/** What the director needs to know about a combatant's sprite. */
data class CombatantLook(
    val isEnemy: Boolean,
    /** The frame that shatters (its death frame), as the sprite grid rows. */
    val matrix: List<String>,
    val palette: Map<Char, Color>,
    /** Drawn mirrored (enemies face the party). */
    val flipped: Boolean,
    val hitReaction: com.voicerpg.engine.model.HitReaction = com.voicerpg.engine.model.HitReaction.FLINCH
)

/** A cut-in panel shown since [bornAt] (director time). */
data class ShownCutIn(val cue: BattleCue.CutIn, val bornAt: Float) {
    companion object {
        const val LIFETIME = 0.65f
    }
}

/** A resonance streak badge: [count] casts in a row, shown since [bornAt]. */
data class ShownStreak(val count: Int, val bornAt: Float)

/** Runs the director's frame loop and draws its projectiles and particles over the arena. */
@Composable
fun BattleFxCanvas(director: BattleFxDirector, modifier: Modifier = Modifier) {
    LaunchedEffect(director) {
        while (true) {
            withFrameNanos { director.frame(it) }
        }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        director.frameTick // read so the canvas redraws every frame
        drawProjectiles(director.projectiles)
        drawParticles(director.particles)
    }
}
