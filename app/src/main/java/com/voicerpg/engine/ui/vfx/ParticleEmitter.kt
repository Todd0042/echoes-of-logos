package com.voicerpg.engine.ui.vfx

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.voicerpg.engine.model.Particle
import com.voicerpg.engine.model.SpellSchool
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Performance budget (docs/COMBAT_FEEL_PLAN.md §1.5): live particles at once. */
object ParticleCap {
    const val MAX_LIVE = 900
}

/**
 * Retro particle bursts for spell impacts. Advanced by the battle's FxClock and drawn by BattleFxCanvas.
 */
class ParticleEmitter {
    val particles = mutableStateListOf<Particle>()

    fun emit(
        school: SpellSchool,
        originX: Float,
        originY: Float,
        count: Int,
        isLogos: Boolean = false
    ) {
        val newParticles = ArrayList<Particle>(count)

        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2.0 * Math.PI
            val speed = if (isLogos) {
                Random.nextFloat() * 14.0f + 6.0f
            } else {
                Random.nextFloat() * 8.0f + 2.0f
            }

            val vx = (cos(angle) * speed).toFloat()
            val vy = (sin(angle) * speed).toFloat()

            val initialSize = if (isLogos) {
                Random.nextFloat() * 12.0f + 6.0f
            } else {
                Random.nextFloat() * 7.0f + 3.0f
            }

            val color = pickParticleColor(school, isLogos)
            val gravity = when (school) {
                SpellSchool.PYROMANCY -> -0.15f // embers float up
                SpellSchool.CRYOMANCY -> 0.18f  // hail/frost falls down
                SpellSchool.ELECTROMANCY -> 0.0f
                SpellSchool.NATURE -> 0.10f // leaves and briars drift down
                SpellSchool.HOLY -> -0.10f
                SpellSchool.SHADOW -> -0.05f
                SpellSchool.PHYSICAL -> 0.25f
            }

            newParticles.add(
                Particle(
                    x = originX + Random.nextFloat() * 30f - 15f,
                    y = originY + Random.nextFloat() * 30f - 15f,
                    vx = vx,
                    vy = vy,
                    size = initialSize,
                    initialSize = initialSize,
                    color = color,
                    gravity = gravity,
                    drag = if (school == SpellSchool.ELECTROMANCY) 0.92f else 0.96f
                )
            )
        }

        add(newParticles)
    }

    /** Adds [more], keeping at most [ParticleCap.MAX_LIVE] alive (the oldest go first). */
    fun add(more: List<Particle>) {
        particles.addAll(more)
        val over = particles.size - ParticleCap.MAX_LIVE
        if (over > 0) particles.removeRange(0, over)
    }

    /** Advance every particle by [dtSeconds] of arena time (see FxClock) and drop the dead ones. */
    fun update(dtSeconds: Float) {
        if (dtSeconds <= 0f || particles.isEmpty()) return
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.update(dtSeconds)
            if (p.isDead) {
                iterator.remove()
            }
        }
    }

    fun clear() {
        particles.clear()
    }

    private fun pickParticleColor(school: SpellSchool, isLogos: Boolean): Color {
        return when (school) {
            SpellSchool.PYROMANCY -> {
                val palette = listOf(
                    Color(0xFFFF3D00),
                    Color(0xFFFF9100),
                    Color(0xFFFFD600),
                    Color(0xFFFF6E40),
                    if (isLogos) Color(0xFFFFFFFF) else Color(0xFFFFAB91)
                )
                palette.random()
            }
            SpellSchool.CRYOMANCY -> {
                val palette = listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF18FFFF),
                    Color(0xFF80D8FF),
                    Color(0xFFE0F7FA),
                    Color(0xFFFFFFFF)
                )
                palette.random()
            }
            SpellSchool.ELECTROMANCY -> {
                val palette = listOf(
                    Color(0xFFE040FB),
                    Color(0xFFEA80FC),
                    Color(0xFF7C4DFF),
                    Color(0xFFB388FF),
                    Color(0xFFFFFFFF)
                )
                palette.random()
            }
            SpellSchool.NATURE -> {
                val palette = listOf(
                    Color(0xFF66BB6A),
                    Color(0xFFA5D6A7),
                    Color(0xFF2E7D32),
                    Color(0xFF9CCC65),
                    Color(0xFFE8F5E9)
                )
                palette.random()
            }
            SpellSchool.HOLY -> {
                val palette = listOf(
                    Color(0xFFFFD700),
                    Color(0xFFFFEE58),
                    Color(0xFFFFF59D),
                    Color(0xFFFFFDE7),
                    Color(0xFFFFFFFF)
                )
                palette.random()
            }
            SpellSchool.SHADOW -> {
                val palette = listOf(
                    Color(0xFF651FFF),
                    Color(0xFF7C4DFF),
                    Color(0xFF311B92),
                    Color(0xFFBA68C8),
                    Color(0xFF212121)
                )
                palette.random()
            }
            SpellSchool.PHYSICAL -> {
                val palette = listOf(
                    Color(0xFFECEFF1),
                    Color(0xFFCFD8DC),
                    Color(0xFFB0BEC5),
                    Color(0xFFFF5252)
                )
                palette.random()
            }
        }
    }
}

/** Draws the emitter's particles as retro square pixels. */
fun DrawScope.drawParticles(emitter: ParticleEmitter) {
    val list = emitter.particles
    for (i in list.indices) {
        val p = list.getOrNull(i) ?: continue
        drawRect(
            color = p.color.copy(alpha = p.alpha),
            topLeft = Offset(p.x - p.size / 2f, p.y - p.size / 2f),
            size = Size(p.size, p.size)
        )
    }
}
