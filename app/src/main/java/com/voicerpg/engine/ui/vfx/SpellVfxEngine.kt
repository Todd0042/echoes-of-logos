package com.voicerpg.engine.ui.vfx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.model.SpellGraphics
import com.voicerpg.engine.model.SpellSchool
import com.voicerpg.engine.content.parseHexColor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class ActiveSpellProjectile(
    val id: Long = System.nanoTime(),
    val startX: Float,
    val startY: Float,
    val targetX: Float,
    val targetY: Float,
    val school: SpellSchool,
    val isHeal: Boolean,
    val bonusPercent: Int = 0,
    val tier: ResonanceTier = ResonanceTier.BASIC,
    var progress: Float = 0f,
    val graphics: SpellGraphics? = null,
    val onImpact: () -> Unit
)

class SpellVfxEngine {
    val projectiles = mutableStateListOf<ActiveSpellProjectile>()

    fun launch(
        startX: Float,
        startY: Float,
        targetX: Float,
        targetY: Float,
        school: SpellSchool,
        isHeal: Boolean,
        bonusPercent: Int = 0,
        tier: ResonanceTier = ResonanceTier.BASIC,
        graphics: SpellGraphics? = null,
        onImpact: () -> Unit
    ) {
        projectiles.add(
            ActiveSpellProjectile(
                startX = startX,
                startY = startY,
                targetX = targetX,
                targetY = targetY,
                school = school,
                isHeal = isHeal,
                bonusPercent = bonusPercent,
                tier = tier,
                graphics = graphics,
                onImpact = onImpact
            )
        )
    }

    fun update(deltaProgress: Float = 0.045f) {
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.progress += deltaProgress
            if (p.progress >= 1.0f) {
                p.onImpact()
                iterator.remove()
            }
        }
    }
}

@Composable
fun SpellVfxCanvas(
    engine: SpellVfxEngine,
    modifier: Modifier = Modifier
) {
    var frameTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(engine) {
        while (true) {
            withFrameNanos { nanos ->
                engine.update()
                frameTick = nanos
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val _tick = frameTick // Observes frame tick so Canvas reliably redraws every frame while projectiles exist
        if (engine.projectiles.isNotEmpty()) {
            for (p in engine.projectiles) {
                renderProjectile(p)
            }
        }
    }
}

private fun DrawScope.renderProjectile(p: ActiveSpellProjectile) {
    val t = p.progress.coerceIn(0f, 1f)
    val isSuper = p.bonusPercent >= 100
    val isTranscendental = p.bonusPercent >= 155
    val sizeScale = 1.0f + 1.5f * (p.bonusPercent / 200.0f)

    val customPrimary = p.graphics?.primaryColor?.let { parseHexColor(it) }
    val customSecondary = p.graphics?.secondaryColor?.let { parseHexColor(it) }
    val effectType = p.graphics?.effectType?.uppercase() ?: if (p.isHeal) "PILLAR" else "PROJECTILE"

    if (p.isHeal || effectType == "PILLAR") {
        val primary = customPrimary ?: Color(0x44FFD700)
        val secondary = customSecondary ?: Color(0xAA69F0AE)
        val pillarWidth = if (isTranscendental) 150f else if (isSuper) 110f else 70f
        val pillarHeight = size.height * t

        // Outer radiance
        drawRect(
            color = primary.copy(alpha = 0.4f),
            topLeft = Offset(p.targetX - pillarWidth / 2f, 0f),
            size = Size(pillarWidth, pillarHeight)
        )
        // Inner shaft
        drawRect(
            color = secondary.copy(alpha = 0.75f),
            topLeft = Offset(p.targetX - pillarWidth / 4f, 0f),
            size = Size(pillarWidth / 2f, pillarHeight)
        )

        // Descending Golden Halos for high resonance heals
        if (isSuper) {
            val haloY = pillarHeight * 0.85f
            drawOval(
                color = primary,
                topLeft = Offset(p.targetX - pillarWidth * 0.45f, haloY - 12f),
                size = Size(pillarWidth * 0.9f, 24f),
                style = Stroke(width = 4f)
            )
        }
        return
    }

    // Projectile coordinates along trajectory
    val curX = p.startX + (p.targetX - p.startX) * t
    val arcHeight = if (p.school == SpellSchool.PYROMANCY || effectType == "WAVE") -120f * sin(t * Math.PI.toFloat()) else 0f
    val curY = p.startY + (p.targetY - p.startY) * t + arcHeight

    val dx = p.targetX - p.startX
    val dy = p.targetY - p.startY
    val angleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()

    if (effectType == "SLASH") {
        val slashColor = customPrimary ?: Color(0xFFECEFF1)
        val accentColor = customSecondary ?: Color(0xFFFF5252)
        val length = 32f * sizeScale
        val height = 8f * sizeScale
        rotate(degrees = angleDeg + 45f * sin(t * Math.PI.toFloat()), pivot = Offset(curX, curY)) {
            drawRect(color = slashColor, topLeft = Offset(curX - length / 2f, curY - height / 2f), size = Size(length, height))
            drawRect(color = accentColor, topLeft = Offset(curX - length / 4f, curY - height / 4f), size = Size(length / 2f, height / 2f))
        }
        for (i in 1..4) {
            val trailT = (t - i * 0.04f).coerceAtLeast(0f)
            val trailX = p.startX + (p.targetX - p.startX) * trailT
            val trailY = p.startY + (p.targetY - p.startY) * trailT
            drawCircle(slashColor.copy(alpha = (0.7f - i * 0.15f).coerceAtLeast(0.1f)), radius = 7f * sizeScale - i, center = Offset(trailX, trailY))
        }
        return
    }

    if (effectType == "BURST") {
        val burstColor = customPrimary ?: p.school.themeColor
        val coreColor = customSecondary ?: Color.White
        val radius = (18f + 25f * sin(t * Math.PI.toFloat())) * sizeScale
        drawCircle(color = burstColor.copy(alpha = (1f - t * 0.5f).coerceIn(0.2f, 0.85f)), radius = radius, center = Offset(curX, curY))
        drawCircle(color = coreColor, radius = radius * 0.5f, center = Offset(curX, curY))
        drawCircle(color = Color.White, radius = radius * 0.25f, center = Offset(curX, curY))
        return
    }

    if (effectType == "WAVE") {
        val waveColor = customPrimary ?: p.school.themeColor
        val waveWidth = 36f * sizeScale
        val waveHeight = 16f * sizeScale
        rotate(degrees = angleDeg, pivot = Offset(curX, curY)) {
            drawOval(
                color = waveColor.copy(alpha = 0.85f),
                topLeft = Offset(curX - waveWidth / 2f, curY - waveHeight / 2f),
                size = Size(waveWidth, waveHeight),
                style = Stroke(width = 3.5f * sizeScale)
            )
        }
        return
    }

    when (p.school) {
        SpellSchool.PYROMANCY -> {
            val baseRadius = 14f * sizeScale

            // 1. Rotating Arcane Rune Rings for Mythic/Transcendental
            if (isSuper) {
                val orbitAngle = (t * 12f).toDouble()
                val orbitRadius = baseRadius * 1.6f
                drawCircle(
                    color = Color(0x88FFD54F),
                    radius = orbitRadius,
                    center = Offset(curX, curY),
                    style = Stroke(width = 2.5f)
                )
                // Orbiting celestial plasma motes
                for (k in 0..2) {
                    val moteAngle = orbitAngle + (k * 2.0 * Math.PI / 3.0)
                    val mx = curX + (cos(moteAngle) * orbitRadius).toFloat()
                    val my = curY + (sin(moteAngle) * orbitRadius).toFloat()
                    drawCircle(Color(0xFFFFD54F), radius = 5f, center = Offset(mx, my))
                }
            }

            // 2. Multi-Stage Solar Core
            if (isTranscendental) {
                // Outer heat distortion aura
                drawCircle(Color(0x33E040FB), radius = baseRadius * 2.1f, center = Offset(curX, curY))
            }
            drawCircle(Color(0xFFFF3D00).copy(alpha = 0.85f), radius = baseRadius * 1.3f, center = Offset(curX, curY))
            drawCircle(Color(0xFFFFD54F), radius = baseRadius * 0.9f, center = Offset(curX, curY))
            drawCircle(Color.White, radius = baseRadius * 0.5f, center = Offset(curX, curY))

            // 3. Dense Comet Tail
            val trailCount = if (isTranscendental) 8 else if (isSuper) 6 else 4
            for (i in 1..trailCount) {
                val trailT = (t - i * 0.035f).coerceAtLeast(0f)
                val trailX = p.startX + (p.targetX - p.startX) * trailT
                val trailY = p.startY + (p.targetY - p.startY) * trailT - 120f * sin(trailT * Math.PI.toFloat())
                val alpha = (1f - i.toFloat() / trailCount).coerceIn(0.1f, 1f)
                val rad = (baseRadius * 0.7f) - (i * 1.5f).coerceAtLeast(2f)
                drawCircle(Color(0xFFFF6E40).copy(alpha = alpha), radius = rad, center = Offset(trailX, trailY))
            }
        }

        SpellSchool.CRYOMANCY -> {
            val length = 26f * sizeScale
            val height = 10f * sizeScale

            rotate(degrees = angleDeg, pivot = Offset(curX, curY)) {
                // Glacial Lance
                drawRect(color = Color(0xFF00E5FF), topLeft = Offset(curX - length / 2f, curY - height / 2f), size = Size(length, height))
                drawRect(color = Color.White, topLeft = Offset(curX - length / 4f, curY - height / 4f), size = Size(length / 2f, height / 2f))

                if (isSuper) {
                    // Frost nova rings expanding along path
                    drawOval(
                        color = Color(0x6680DEEA),
                        topLeft = Offset(curX - length, curY - height * 1.5f),
                        size = Size(length * 2f, height * 3f),
                        style = Stroke(width = 2f)
                    )
                }
            }
        }

        SpellSchool.ELECTROMANCY -> {
            // Multi-branching Lightning Tempest
            val branchCount = if (isSuper) 4 else 2
            for (b in 0 until branchCount) {
                val path = Path().apply {
                    moveTo(p.startX, p.startY)
                    for (step in 1..5) {
                        val st = step / 5f
                        val jitter = if (isSuper) 45f else 25f
                        val nx = p.startX + (p.targetX - p.startX) * st + (Random.nextFloat() * jitter * 2 - jitter)
                        val ny = p.startY + (p.targetY - p.startY) * st + (Random.nextFloat() * jitter * 2 - jitter)
                        lineTo(nx, ny)
                    }
                    lineTo(p.targetX, p.targetY)
                }
                drawPath(path, color = if (b == 0) Color(0xFFE040FB) else Color(0xFF00E5FF), style = Stroke(width = if (isSuper) 4f else 2.5f))
                drawPath(path, color = Color.White, style = Stroke(width = 1.5f))
            }
        }

        SpellSchool.NATURE -> {
            val radius = 12f * sizeScale
            // Verdant brier orb
            drawCircle(color = Color(0xFF66BB6A), radius = radius, center = Offset(curX, curY))
            drawCircle(color = Color(0xFFA5D6A7), radius = radius * 0.6f, center = Offset(curX, curY))
            drawCircle(Color.White, radius = radius * 0.3f, center = Offset(curX, curY))
            // Orbiting emerald leaves
            val orbitCount = if (isSuper) 4 else 2
            for (k in 0 until orbitCount) {
                val leafAngle = (t * 10f + k * (2 * Math.PI / orbitCount)).toDouble()
                val lx = curX + (cos(leafAngle) * radius * 1.5f).toFloat()
                val ly = curY + (sin(leafAngle) * radius * 1.5f).toFloat()
                drawCircle(Color(0xFF2E7D32), radius = 4f * sizeScale, center = Offset(lx, ly))
            }
        }

        SpellSchool.HOLY -> {
            val radius = 14f * sizeScale
            drawCircle(color = Color(0x66FFD700), radius = radius * 1.6f, center = Offset(curX, curY))
            drawCircle(color = Color(0xFFFFD54F), radius = radius, center = Offset(curX, curY))
            drawCircle(Color.White, radius = radius * 0.5f, center = Offset(curX, curY))
            val rayLen = 20f * sizeScale
            drawLine(
                color = Color(0xFFFFEE58),
                start = Offset(curX - rayLen, curY),
                end = Offset(curX + rayLen, curY),
                strokeWidth = 3f * sizeScale
            )
            drawLine(
                color = Color(0xFFFFEE58),
                start = Offset(curX, curY - rayLen),
                end = Offset(curX, curY + rayLen),
                strokeWidth = 3f * sizeScale
            )
        }

        SpellSchool.SHADOW -> {
            val radius = 14f * sizeScale
            drawCircle(color = Color(0xFF7C4DFF).copy(alpha = 0.85f), radius = radius, center = Offset(curX, curY))
            drawCircle(color = Color(0xFF311B92), radius = radius * 0.6f, center = Offset(curX, curY))
            // Cross slash lines
            val slashLen = 25f * sizeScale
            drawLine(
                color = Color(0xFF00E676),
                start = Offset(curX - slashLen, curY - slashLen),
                end = Offset(curX + slashLen, curY + slashLen),
                strokeWidth = 3f * sizeScale
            )
            drawLine(
                color = Color(0xFFE040FB),
                start = Offset(curX - slashLen, curY + slashLen),
                end = Offset(curX + slashLen, curY - slashLen),
                strokeWidth = 3f * sizeScale
            )
        }

        SpellSchool.PHYSICAL -> {
            val length = 22f * sizeScale
            val height = 6f * sizeScale
            rotate(degrees = angleDeg, pivot = Offset(curX, curY)) {
                // Sharp Steel Slash / Martial Arrow Strike
                drawRect(color = Color(0xFFECEFF1), topLeft = Offset(curX - length / 2f, curY - height / 2f), size = Size(length, height))
                drawRect(color = Color(0xFFFF5252), topLeft = Offset(curX - length / 6f, curY - height / 4f), size = Size(length / 3f, height / 2f))
            }
            // Steel trail
            val trailCount = 3
            for (i in 1..trailCount) {
                val trailT = (t - i * 0.04f).coerceAtLeast(0f)
                val trailX = p.startX + (p.targetX - p.startX) * trailT
                val trailY = p.startY + (p.targetY - p.startY) * trailT
                drawCircle(Color(0xFFB0BEC5).copy(alpha = 0.6f - i * 0.15f), radius = 6f * sizeScale, center = Offset(trailX, trailY))
            }
        }
    }
}
