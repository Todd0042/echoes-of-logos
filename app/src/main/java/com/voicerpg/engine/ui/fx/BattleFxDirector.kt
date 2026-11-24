package com.voicerpg.engine.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.voicerpg.engine.combat.BattleCue
import com.voicerpg.engine.content.PresentationConfig
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.ui.vfx.ParticleEmitter
import com.voicerpg.engine.ui.vfx.SpellVfxEngine
import com.voicerpg.engine.ui.vfx.drawParticles
import com.voicerpg.engine.ui.vfx.drawProjectiles

/**
 * Turns [BattleCue]s into what the player sees: projectiles, particle bursts and the arena shake, all on
 * one [FxClock]. It never changes game state; combat logic never draws. One director per battle screen.
 *
 * [positionOf] resolves a combatant id to its live sprite position in arena pixels.
 */
class BattleFxDirector(private val positionOf: (String) -> Offset) {

    val clock = FxClock()
    val projectiles = SpellVfxEngine()
    val particles = ParticleEmitter()
    val shake = ArenaShake()

    /** The game's `combat/presentation.json`. */
    var presentation: PresentationConfig = PresentationConfig()

    /** Options → Reduce motion: no shake. */
    var reduceMotion: Boolean = false

    /** Advances once per drawn frame, so the canvas redraws while anything moves. */
    var frameTick by mutableLongStateOf(0L)
        private set

    fun play(cue: BattleCue) {
        when (cue) {
            is BattleCue.BattleStart -> {
                projectiles.projectiles.clear()
                particles.clear()
                shake.clear()
                clock.reset()
            }
            is BattleCue.ResonanceReveal -> when {
                cue.resonance.bonusPercent >= SUPER_BONUS ->
                    kick(if (cue.resonance.tier == ResonanceTier.TRANSCENDENTAL) 30f else 18f, direction = 0f)
                cue.resonance.bonusPercent >= BANNER_BONUS -> kick(10f, direction = 0f)
            }
            is BattleCue.EnemyMove -> if (cue.isBossUltimate) kick(20f, direction = 0f)
            is BattleCue.Release -> release(cue)
            is BattleCue.Impact -> if (cue.shake > 0f) kick(cue.shake, direction = if (cue.fromParty) 1f else -1f)
            is BattleCue.Reinforcements -> kick(12f, direction = 0f)
            is BattleCue.PhaseShift,
            is BattleCue.Death,
            is BattleCue.Guard,
            is BattleCue.TurnEnd,
            is BattleCue.BattleEnd -> Unit
        }
    }

    /** Advance everything to the frame at [frameNanos]. */
    fun frame(frameNanos: Long) {
        val step = clock.tick(frameNanos)
        projectiles.update(step.fxSeconds)
        particles.update(step.fxSeconds)
        shake.step(step.realSeconds)
        frameTick = frameNanos
    }

    private fun kick(magnitude: Float, direction: Float) {
        if (reduceMotion) return
        shake.kick(magnitude * presentation.intensity, direction)
    }

    private fun release(cue: BattleCue.Release) {
        val caster = positionOf(cue.casterId)
        val isLogos = cue.bonusPercent >= SUPER_BONUS
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
        /** Where a release with no living target flies (right of centre, upper arena). */
        val FALLBACK_TARGET = Offset(800f, 350f)
    }
}

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
